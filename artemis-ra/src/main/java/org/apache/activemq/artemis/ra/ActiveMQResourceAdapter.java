/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.ra;

import javax.jms.Session;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.WorkManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAResource;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.ChannelBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivation;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;
import org.apache.activemq.artemis.ra.recovery.RecoveryManager;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.jgroups.JChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The resource adapter for ActiveMQ
 */
public class ActiveMQResourceAdapter implements ResourceAdapter, Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final long serialVersionUID = 4756893709825838770L;

   public static final String PRODUCT_NAME = "ActiveMQ Artemis";

   private transient BootstrapContext ctx;

   private final ActiveMQRAProperties raProperties;

   /**
    * The resource adapter properties before parsing
    */
   private String unparsedProperties;

   /**
    * The resource adapter connector classnames before parsing
    */
   private String unparsedConnectors;

   private final AtomicBoolean configured;

   private final Map<ActivationSpec, ActiveMQActivation> activations;

   private ActiveMQConnectionFactory defaultActiveMQConnectionFactory;

   private ActiveMQConnectionFactory recoveryActiveMQConnectionFactory;

   private TransactionSynchronizationRegistry tsr;

   private String unparsedJndiParams;

   private final RecoveryManager recoveryManager;

   private boolean useAutoRecovery = true;

   private final List<ActiveMQRAManagedConnectionFactory> managedConnectionFactories = new ArrayList<>();

   private String entries;

   //fix of ARTEMIS-1669 - propagated value of transactional attribute JMSConnectionFactoryDefinition annotation with the
   //default value is falso -> original behavior
   private boolean ignoreJTA;

   /**
    * Keep track of the connection factories that we create so we don't create a bunch of instances of factories
    * configured the exact same way. Using the same connection factory instance also makes connection load-balancing
    * behave as expected for outbound connections.
    */
   private final Map<ConnectionFactoryProperties, Pair<ActiveMQConnectionFactory, AtomicInteger>> knownConnectionFactories = new HashMap<>();

   public ActiveMQResourceAdapter() {
      logger.trace("constructor()");

      raProperties = new ActiveMQRAProperties();
      configured = new AtomicBoolean(false);
      activations = Collections.synchronizedMap(new IdentityHashMap<>());
      recoveryManager = new RecoveryManager();
   }

   public TransactionSynchronizationRegistry getTSR() {
      return tsr;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endpointActivation(final MessageEndpointFactory endpointFactory,
                                  final ActivationSpec spec) throws ResourceException {
      if (spec == null) {
         throw ActiveMQRABundle.BUNDLE.noActivationSpec();
      }
      if (!configured.getAndSet(true)) {
         try {
            setup();
         } catch (ActiveMQException e) {
            throw new ResourceException("Unable to create activation", e);
         }
      }

      logger.trace("endpointActivation({}, {})", endpointFactory, spec);

      ActiveMQActivation activation = new ActiveMQActivation(this, endpointFactory, (ActiveMQActivationSpec) spec);
      activations.put(spec, activation);
      activation.start();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endpointDeactivation(final MessageEndpointFactory endpointFactory, final ActivationSpec spec) {
      logger.trace("endpointDeactivation({}, {})", endpointFactory, spec);

      ActiveMQActivation activation = activations.remove(spec);
      if (activation != null) {
         activation.stop();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAResource[] getXAResources(final ActivationSpec[] specs) throws ResourceException {
      if (logger.isTraceEnabled()) {
         logger.trace("getXAResources({})", Arrays.toString(specs));
      }

      if (useAutoRecovery) {
         // let the TM handle the recovery
         return null;
      } else {
         List<XAResource> xaresources = new ArrayList<>();
         for (ActivationSpec spec : specs) {
            ActiveMQActivation activation = activations.get(spec);
            if (activation != null) {
               xaresources.addAll(activation.getXAResources());
            }
         }
         return xaresources.toArray(new XAResource[xaresources.size()]);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void start(final BootstrapContext ctx) throws ResourceAdapterInternalException {
      logger.trace("start({})", ctx);

      tsr = ctx.getTransactionSynchronizationRegistry();

      recoveryManager.start(useAutoRecovery);

      this.ctx = ctx;

      if (!configured.getAndSet(true)) {
         try {
            setup();
         } catch (ActiveMQException e) {
            throw new ResourceAdapterInternalException("Unable to create activation", e);
         }
      }

      ActiveMQRALogger.LOGGER.resourceAdaptorStarted();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void stop() {
      logger.trace("stop()");

      for (Map.Entry<ActivationSpec, ActiveMQActivation> entry : activations.entrySet()) {
         try {
            entry.getValue().stop();
         } catch (Exception ignored) {
            logger.debug("Ignored", ignored);
         }
      }

      activations.clear();

      for (ActiveMQRAManagedConnectionFactory managedConnectionFactory : managedConnectionFactories) {
         managedConnectionFactory.stop();
      }

      managedConnectionFactories.clear();

      for (Pair<ActiveMQConnectionFactory, AtomicInteger> pair : knownConnectionFactories.values()) {
         pair.getA().close();
      }
      knownConnectionFactories.clear();

      if (defaultActiveMQConnectionFactory != null) {
         defaultActiveMQConnectionFactory.close();
      }

      if (recoveryActiveMQConnectionFactory != null) {
         recoveryActiveMQConnectionFactory.close();
      }

      recoveryManager.stop();

      ActiveMQRALogger.LOGGER.raStopped();
   }

   public void setUseAutoRecovery(Boolean useAutoRecovery) {
      this.useAutoRecovery = useAutoRecovery;
   }

   public Boolean isUseAutoRecovery() {
      return this.useAutoRecovery;
   }

   public Boolean isUseMaskedPassword() {
      return this.raProperties.isUseMaskedPassword();
   }

   public void setUseMaskedPassword(Boolean useMaskedPassword) {
      this.raProperties.setUseMaskedPassword(useMaskedPassword);
   }

   public void setPasswordCodec(String passwordCodec) {
      this.raProperties.setPasswordCodec(passwordCodec);
   }

   public String getPasswordCodec() {
      return this.raProperties.getPasswordCodec();
   }

   public void setConnectorClassName(final String connectorClassName) {
      logger.trace("setTransportType({})", connectorClassName);

      unparsedConnectors = connectorClassName;

      raProperties.setParsedConnectorClassNames(ActiveMQRaUtils.parseConnectorConnectorConfig(connectorClassName));
   }

   public String getConnectorClassName() {
      return unparsedConnectors;
   }

   public String getConnectionParameters() {
      return unparsedProperties;
   }

   public void setConnectionParameters(final String config) {
      if (config != null) {
         this.unparsedProperties = config;
         raProperties.setParsedConnectionParameters(ActiveMQRaUtils.parseConfig(config));
      }
   }

   public Boolean getHA() {
      return raProperties.isHA();
   }

   public void setHA(final Boolean ha) {
      this.raProperties.setHA(ha);
   }

   public String getEntries() {
      return entries;
   }

   public String getJndiName() {
      if (!(entries == null || entries.isEmpty())) {
         Matcher m = Pattern.compile("\"(.*?)\"").matcher(entries);
         if (m.find()) {
            return m.group(1);
         }
      }
      return null;
   }

   public void setEntries(String entries) {
      this.entries = entries;
   }

   public String getDiscoveryAddress() {
      logger.trace("getDiscoveryGroupAddress()");

      return raProperties.getDiscoveryAddress();
   }

   public void setJgroupsFile(String jgroupsFile) {
      raProperties.setJgroupsFile(jgroupsFile);
   }

   public String getJgroupsFile() {
      return raProperties.getJgroupsFile();
   }

   public String getJgroupsChannelName() {
      return raProperties.getJgroupsChannelName();
   }

   public void setJgroupsChannelName(String jgroupsChannelName) {
      raProperties.setJgroupsChannelName(jgroupsChannelName);
   }

   public void setDiscoveryAddress(final String dgn) {
      logger.trace("setDiscoveryGroupAddress({})", dgn);

      raProperties.setDiscoveryAddress(dgn);
   }

   public Integer getDiscoveryPort() {
      logger.trace("getDiscoveryGroupPort()");

      return raProperties.getDiscoveryPort();
   }

   public void setDiscoveryLocalBindAddress(final String discoveryLocalBindAddress) {
      logger.trace("setDiscoveryLocalBindAddress({})", discoveryLocalBindAddress);

      raProperties.setDiscoveryLocalBindAddress(discoveryLocalBindAddress);
   }

   public String getDiscoveryLocalBindAddress() {
      logger.trace("getDiscoveryLocalBindAddress()");

      return raProperties.getDiscoveryLocalBindAddress();
   }

   public void setDiscoveryPort(final Integer dgp) {
      logger.trace("setDiscoveryGroupPort({})", dgp);

      raProperties.setDiscoveryPort(dgp);
   }

   public Long getDiscoveryRefreshTimeout() {
      logger.trace("getDiscoveryRefreshTimeout()");

      return raProperties.getDiscoveryRefreshTimeout();
   }

   public void setDiscoveryRefreshTimeout(final Long discoveryRefreshTimeout) {
      logger.trace("setDiscoveryRefreshTimeout({})", discoveryRefreshTimeout);

      raProperties.setDiscoveryRefreshTimeout(discoveryRefreshTimeout);
   }

   public Long getDiscoveryInitialWaitTimeout() {
      logger.trace("getDiscoveryInitialWaitTimeout()");

      return raProperties.getDiscoveryInitialWaitTimeout();
   }

   public void setDiscoveryInitialWaitTimeout(final Long discoveryInitialWaitTimeout) {
      logger.trace("setDiscoveryInitialWaitTimeout({})", discoveryInitialWaitTimeout);

      raProperties.setDiscoveryInitialWaitTimeout(discoveryInitialWaitTimeout);
   }

   public Long getClientFailureCheckPeriod() {
      logger.trace("getClientFailureCheckPeriod()");

      return raProperties.getClientFailureCheckPeriod();
   }

   public void setClientFailureCheckPeriod(final Long clientFailureCheckPeriod) {
      logger.trace("setClientFailureCheckPeriod({})", clientFailureCheckPeriod);

      raProperties.setClientFailureCheckPeriod(clientFailureCheckPeriod);
   }

   public Long getConnectionTTL() {
      logger.trace("getConnectionTTL()");

      return raProperties.getConnectionTTL();
   }

   public void setConnectionTTL(final Long connectionTTL) {
      logger.trace("setConnectionTTL({})", connectionTTL);

      raProperties.setConnectionTTL(connectionTTL);
   }

   public Boolean isCacheLargeMessagesClient() {
      logger.trace("isCacheLargeMessagesClient()");

      return raProperties.isCacheLargeMessagesClient();
   }

   public void setCacheLargeMessagesClient(final Boolean cacheLargeMessagesClient) {
      logger.trace("setCacheLargeMessagesClient({})", cacheLargeMessagesClient);

      raProperties.setCacheLargeMessagesClient(cacheLargeMessagesClient);
   }

   public Boolean isCompressLargeMessage() {
      logger.trace("isCompressLargeMessage()");

      return raProperties.isCompressLargeMessage();
   }

   @Deprecated
   public void setFailoverOnInitialConnection(final Boolean failoverOnInitialConnection) {
   }

   @Deprecated
   public Boolean isFailoverOnInitialConnection() {
      return false;
   }

   public void setCacheDestinations(final Boolean cacheDestinations) {
      logger.trace("setCacheDestinations({})", cacheDestinations);

      raProperties.setCacheDestinations(cacheDestinations);
   }

   public Boolean isCacheDestinations() {
      logger.trace("isCacheDestinations()");

      return raProperties.isCacheDestinations();
   }

   public void setEnable1xPrefixes(final Boolean enable1xPrefixes) {
      logger.trace("setEnable1xPrefixes({})", enable1xPrefixes);

      raProperties.setEnable1xPrefixes(enable1xPrefixes);
   }

   public Boolean isEnable1xPrefixes() {
      logger.trace("isEnable1xPrefixes()");

      return raProperties.isEnable1xPrefixes();
   }

   public void setCompressLargeMessage(final Boolean compressLargeMessage) {
      logger.trace("setCompressLargeMessage({})", compressLargeMessage);

      raProperties.setCompressLargeMessage(compressLargeMessage);
   }

   public Integer getCompressionLevel() {
      logger.trace("getCompressionLevel()");

      return raProperties.getCompressionLevel();
   }

   /**
    * Sets what compressionLevel to use when compressing messages
    *
    * @param compressionLevel must be -1 (default) or 0-9
    */
   public void setCompressionLevel(final Integer compressionLevel) {
      logger.trace("setCompressionLevel({})", compressionLevel);

      raProperties.setCompressionLevel(compressionLevel);
   }

   public Long getCallTimeout() {
      logger.trace("getCallTimeout()");

      return raProperties.getCallTimeout();
   }

   public void setCallTimeout(final Long callTimeout) {
      logger.trace("setCallTimeout({})", callTimeout);

      raProperties.setCallTimeout(callTimeout);
   }

   public Long getCallFailoverTimeout() {
      logger.trace("getCallFailoverTimeout()");

      return raProperties.getCallFailoverTimeout();
   }

   public void setCallFailoverTimeout(final Long callFailoverTimeout) {
      logger.trace("setCallFailoverTimeout({})", callFailoverTimeout);

      raProperties.setCallFailoverTimeout(callFailoverTimeout);
   }

   public Integer getDupsOKBatchSize() {
      logger.trace("getDupsOKBatchSize()");

      return raProperties.getDupsOKBatchSize();
   }

   public void setDupsOKBatchSize(final Integer dupsOKBatchSize) {
      logger.trace("setDupsOKBatchSize({})", dupsOKBatchSize);

      raProperties.setDupsOKBatchSize(dupsOKBatchSize);
   }

   public Integer getTransactionBatchSize() {
      logger.trace("getTransactionBatchSize()");

      return raProperties.getTransactionBatchSize();
   }

   public void setTransactionBatchSize(final Integer transactionBatchSize) {
      logger.trace("setTransactionBatchSize({})", transactionBatchSize);

      raProperties.setTransactionBatchSize(transactionBatchSize);
   }

   public Integer getConsumerWindowSize() {
      logger.trace("getConsumerWindowSize()");

      return raProperties.getConsumerWindowSize();
   }

   public void setConsumerWindowSize(final Integer consumerWindowSize) {
      logger.trace("setConsumerWindowSize({})", consumerWindowSize);

      raProperties.setConsumerWindowSize(consumerWindowSize);
   }

   public Integer getConsumerMaxRate() {
      logger.trace("getConsumerMaxRate()");

      return raProperties.getConsumerMaxRate();
   }

   public void setConsumerMaxRate(final Integer consumerMaxRate) {
      logger.trace("setConsumerMaxRate({})", consumerMaxRate);

      raProperties.setConsumerMaxRate(consumerMaxRate);
   }

   public Integer getConfirmationWindowSize() {
      logger.trace("getConfirmationWindowSize()");

      return raProperties.getConfirmationWindowSize();
   }

   public void setConfirmationWindowSize(final Integer confirmationWindowSize) {
      logger.trace("setConfirmationWindowSize({})", confirmationWindowSize);

      raProperties.setConfirmationWindowSize(confirmationWindowSize);
   }

   public Integer getProducerMaxRate() {
      logger.trace("getProducerMaxRate()");

      return raProperties.getProducerMaxRate();
   }

   public void setProducerMaxRate(final Integer producerMaxRate) {
      logger.trace("setProducerMaxRate({})", producerMaxRate);

      raProperties.setProducerMaxRate(producerMaxRate);
   }

   public void setUseTopologyForLoadBalancing(Boolean useTopologyForLoadBalancing) {
      raProperties.setUseTopologyForLoadBalancing(useTopologyForLoadBalancing);
   }

   public Boolean getUseTopologyForLoadBalancing() {
      return raProperties.isUseTopologyForLoadBalancing();
   }

   public Boolean isUseTopologyForLoadBalancing() {
      return raProperties.isUseTopologyForLoadBalancing();
   }

   public Integer getProducerWindowSize() {
      logger.trace("getProducerWindowSize()");

      return raProperties.getProducerWindowSize();
   }

   public void setProducerWindowSize(final Integer producerWindowSize) {
      logger.trace("setProducerWindowSize({})", producerWindowSize);

      raProperties.setProducerWindowSize(producerWindowSize);
   }

   public String getProtocolManagerFactoryStr() {
      logger.trace("getProtocolManagerFactoryStr()");

      return raProperties.getProtocolManagerFactoryStr();
   }

   public void setProtocolManagerFactoryStr(final String protocolManagerFactoryStr) {
      logger.trace("setProtocolManagerFactoryStr({})", protocolManagerFactoryStr);

      raProperties.setProtocolManagerFactoryStr(protocolManagerFactoryStr);
   }

   @Deprecated(forRemoval = true)
   public String getDeserializationBlackList() {
      logger.trace("getDeserializationBlackList()");

      return raProperties.getDeserializationBlackList();
   }

   @Deprecated(forRemoval = true)
   public void setDeserializationBlackList(String deserializationDenyList) {
      logger.trace("setDeserializationBlackList({})", deserializationDenyList);

      raProperties.setDeserializationDenyList(deserializationDenyList);
   }

   @Deprecated(forRemoval = true)
   public String getDeserializationWhiteList() {
      logger.trace("getDeserializationWhiteList()");

      return raProperties.getDeserializationAllowList();
   }

   @Deprecated(forRemoval = true)
   public void setDeserializationWhiteList(String deserializationAllowList) {
      logger.trace("setDeserializationWhiteList({})", deserializationAllowList);

      raProperties.setDeserializationAllowList(deserializationAllowList);
   }

   public String getDeserializationDenyList() {
      logger.trace("getDeserializationDenyList()");

      return raProperties.getDeserializationDenyList();
   }

   public void setDeserializationDenyList(String deserializationDenyList) {
      logger.trace("setDeserializationDenyList({})", deserializationDenyList);

      raProperties.setDeserializationBlackList(deserializationDenyList);
   }

   public String getDeserializationAllowList() {
      logger.trace("getDeserializationAllowList()");

      return raProperties.getDeserializationAllowList();
   }

   public void setDeserializationAllowList(String deserializationAllowList) {
      logger.trace("setDeserializationAllowList({})", deserializationAllowList);

      raProperties.setDeserializationAllowList(deserializationAllowList);
   }

   public Integer getMinLargeMessageSize() {
      logger.trace("getMinLargeMessageSize()");

      return raProperties.getMinLargeMessageSize();
   }

   public void setMinLargeMessageSize(final Integer minLargeMessageSize) {
      logger.trace("setMinLargeMessageSize({})", minLargeMessageSize);

      raProperties.setMinLargeMessageSize(minLargeMessageSize);
   }

   public Boolean getBlockOnAcknowledge() {
      logger.trace("getBlockOnAcknowledge()");

      return raProperties.isBlockOnAcknowledge();
   }

   public void setBlockOnAcknowledge(final Boolean blockOnAcknowledge) {
      logger.trace("setBlockOnAcknowledge({})", blockOnAcknowledge);

      raProperties.setBlockOnAcknowledge(blockOnAcknowledge);
   }

   public Boolean getBlockOnNonDurableSend() {
      logger.trace("getBlockOnNonDurableSend()");

      return raProperties.isBlockOnNonDurableSend();
   }

   public void setBlockOnNonDurableSend(final Boolean blockOnNonDurableSend) {
      logger.trace("setBlockOnNonDurableSend({})", blockOnNonDurableSend);

      raProperties.setBlockOnNonDurableSend(blockOnNonDurableSend);
   }

   public Boolean getBlockOnDurableSend() {
      logger.trace("getBlockOnDurableSend()");

      return raProperties.isBlockOnDurableSend();
   }

   public void setBlockOnDurableSend(final Boolean blockOnDurableSend) {
      logger.trace("setBlockOnDurableSend({})", blockOnDurableSend);

      raProperties.setBlockOnDurableSend(blockOnDurableSend);
   }

   public Boolean getAutoGroup() {
      logger.trace("getAutoGroup()");

      return raProperties.isAutoGroup();
   }

   public void setAutoGroup(final Boolean autoGroup) {
      logger.trace("setAutoGroup({})", autoGroup);

      raProperties.setAutoGroup(autoGroup);
   }

   public Boolean getPreAcknowledge() {
      logger.trace("getPreAcknowledge()");

      return raProperties.isPreAcknowledge();
   }

   public void setPreAcknowledge(final Boolean preAcknowledge) {
      logger.trace("setPreAcknowledge({})", preAcknowledge);

      raProperties.setPreAcknowledge(preAcknowledge);
   }

   public Integer getInitialConnectAttempts() {
      logger.trace("getInitialConnectAttempts()");

      return raProperties.getInitialConnectAttempts();
   }

   public void setInitialConnectAttempts(final Integer initialConnectAttempts) {
      logger.trace("setInitialConnectionAttempts({})", initialConnectAttempts);

      raProperties.setInitialConnectAttempts(initialConnectAttempts);
   }

   public Integer getInitialMessagePacketSize() {
      logger.trace("getInitialMessagePacketSize()");

      return raProperties.getInitialMessagePacketSize();
   }

   public void setInitialMessagePacketSize(final Integer initialMessagePacketSize) {
      logger.trace("setInitialMessagePacketSize({})", initialMessagePacketSize);

      raProperties.setInitialMessagePacketSize(initialMessagePacketSize);
   }

   public Long getRetryInterval() {
      logger.trace("getRetryInterval()");

      return raProperties.getRetryInterval();
   }

   public void setRetryInterval(final Long retryInterval) {
      logger.trace("setRetryInterval({})", retryInterval);

      raProperties.setRetryInterval(retryInterval);
   }

   public Double getRetryIntervalMultiplier() {
      logger.trace("getRetryIntervalMultiplier()");

      return raProperties.getRetryIntervalMultiplier();
   }

   public void setRetryIntervalMultiplier(final Double retryIntervalMultiplier) {
      logger.trace("setRetryIntervalMultiplier({})", retryIntervalMultiplier);

      raProperties.setRetryIntervalMultiplier(retryIntervalMultiplier);
   }

   public Long getMaxRetryInterval() {
      logger.trace("getMaxRetryInterval()");

      return raProperties.getMaxRetryInterval();
   }

   public void setMaxRetryInterval(final Long maxRetryInterval) {
      logger.trace("setMaxRetryInterval({})", maxRetryInterval);

      raProperties.setMaxRetryInterval(maxRetryInterval);
   }

   public Integer getReconnectAttempts() {
      logger.trace("getReconnectAttempts()");

      return raProperties.getReconnectAttempts();
   }

   public void setReconnectAttempts(final Integer reconnectAttempts) {
      logger.trace("setReconnectAttempts({})", reconnectAttempts);

      raProperties.setReconnectAttempts(reconnectAttempts);
   }

   public String getConnectionLoadBalancingPolicyClassName() {
      return raProperties.getConnectionLoadBalancingPolicyClassName();
   }

   public void setConnectionLoadBalancingPolicyClassName(final String connectionLoadBalancingPolicyClassName) {
      logger.trace("setFailoverOnServerShutdown({})", connectionLoadBalancingPolicyClassName);

      raProperties.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }

   public Integer getScheduledThreadPoolMaxSize() {
      return raProperties.getScheduledThreadPoolMaxSize();
   }

   public void setScheduledThreadPoolMaxSize(final Integer scheduledThreadPoolMaxSize) {
      logger.trace("setFailoverOnServerShutdown({})", scheduledThreadPoolMaxSize);

      raProperties.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
   }

   public Integer getThreadPoolMaxSize() {
      return raProperties.getThreadPoolMaxSize();
   }

   public void setThreadPoolMaxSize(final Integer threadPoolMaxSize) {
      logger.trace("setFailoverOnServerShutdown({})", threadPoolMaxSize);

      raProperties.setThreadPoolMaxSize(threadPoolMaxSize);
   }

   public Boolean getUseGlobalPools() {
      return raProperties.isUseGlobalPools();
   }

   public void setUseGlobalPools(final Boolean useGlobalPools) {
      logger.trace("setFailoverOnServerShutdown({})", useGlobalPools);

      raProperties.setUseGlobalPools(useGlobalPools);
   }

   public String getUserName() {
      logger.trace("getUserName()");

      return raProperties.getUserName();
   }

   public void setUserName(final String userName) {
      logger.trace("setUserName({})", userName);

      raProperties.setUserName(userName);
   }

   public String getPassword() {
      logger.trace("getPassword()");

      return raProperties.getPassword();
   }

   public void setPassword(final String password) {
      if (logger.isTraceEnabled()) {
         logger.trace("setPassword(****)");
      }

      raProperties.setPassword(password);
   }

   public boolean isUseJNDI() {
      return raProperties.isUseJNDI();
   }

   public void setUseJNDI(final Boolean value) {
      raProperties.setUseJNDI(value);
   }

   public String getJndiParams() {
      return unparsedJndiParams;
   }

   public void setJndiParams(String jndiParams) {
      unparsedJndiParams = jndiParams;
      raProperties.setParsedJndiParams(ActiveMQRaUtils.parseHashtableConfig(jndiParams));
   }

   public Hashtable<?, ?> getParsedJndiParams() {
      return raProperties.getParsedJndiParams();
   }

   public String getClientID() {
      logger.trace("getClientID()");

      return raProperties.getClientID();
   }

   public void setClientID(final String clientID) {
      logger.trace("setClientID({})", clientID);

      raProperties.setClientID(clientID);
   }

   public String getGroupID() {
      logger.trace("getGroupID()");

      return raProperties.getGroupID();
   }

   public void setGroupID(final String groupID) {
      logger.trace("setGroupID({})", groupID);

      raProperties.setGroupID(groupID);
   }

   public Boolean getUseLocalTx() {
      logger.trace("getUseLocalTx()");

      return raProperties.getUseLocalTx();
   }

   public void setUseLocalTx(final Boolean localTx) {
      logger.trace("setUseXA({})", localTx);

      raProperties.setUseLocalTx(localTx);
   }

   public int getSetupAttempts() {
      logger.trace("getSetupAttempts()");

      return raProperties.getSetupAttempts();
   }

   public void setSetupAttempts(Integer setupAttempts) {
      logger.trace("setSetupAttempts({})", setupAttempts);

      raProperties.setSetupAttempts(setupAttempts);
   }

   public long getSetupInterval() {
      logger.trace("getSetupInterval()");

      return raProperties.getSetupInterval();
   }

   public void setSetupInterval(Long interval) {
      logger.trace("setSetupInterval({})", interval);

      raProperties.setSetupInterval(interval);
   }

   @Override
   public boolean equals(final Object obj) {
      logger.trace("equals({})", obj);

      if (obj == null) {
         return false;
      }
      if (!(obj instanceof ActiveMQResourceAdapter adapter)) {
         return false;
      }

      return Objects.equals(raProperties, adapter.getProperties());
   }

   @Override
   public int hashCode() {
      logger.trace("hashCode()");

      return raProperties.hashCode();
   }

   public WorkManager getWorkManager() {
      logger.trace("getWorkManager()");

      if (ctx == null) {
         return null;
      }

      return ctx.getWorkManager();
   }

   public ClientSession createSession(final ClientSessionFactory parameterFactory,
                                      final int ackMode,
                                      final String user,
                                      final String pass,
                                      final Boolean preAck,
                                      final Integer dupsOkBatchSize,
                                      final Integer transactionBatchSize,
                                      final boolean deliveryTransacted,
                                      final boolean useLocalTx,
                                      final Integer txTimeout) throws Exception {

      ClientSession result;

      // if we are CMP or BMP using local tx we ignore the ack mode as we are transactional
      if (deliveryTransacted || useLocalTx) {
         // If transacted we need to send the ack flush as soon as possible as if any transaction times out, we need
         // the ack on the server already
         if (useLocalTx) {
            result = parameterFactory.createSession(user, pass, false, false, false, false, 0, getClientID());
         } else {
            result = parameterFactory.createSession(user, pass, true, false, false, false, 0, getClientID());
         }
      } else {
         if (preAck != null && preAck) {
            result = parameterFactory.createSession(user, pass, false, true, true, true, -1, getClientID());
         } else {
            // only auto ack and dups ok are supported
            result = switch (ackMode) {
               case Session.AUTO_ACKNOWLEDGE -> parameterFactory.createSession(user, pass, false, true, true, false, 0, getClientID());
               case Session.DUPS_OK_ACKNOWLEDGE -> {
                  int actDupsOkBatchSize = dupsOkBatchSize != null ? dupsOkBatchSize : ActiveMQClient.DEFAULT_ACK_BATCH_SIZE;
                  yield parameterFactory.createSession(user, pass, false, true, true, false, actDupsOkBatchSize, getClientID());
               }
               default -> throw new IllegalArgumentException("Invalid ackmode: " + ackMode);
            };
         }
      }

      logger.debug("Using queue connection {}", result);

      return result;

   }

   public RecoveryManager getRecoveryManager() {
      return recoveryManager;
   }

   public ActiveMQRAProperties getProperties() {
      logger.trace("getProperties()");

      return raProperties;
   }

   protected void setup() throws ActiveMQException {
      raProperties.init();
      defaultActiveMQConnectionFactory = newConnectionFactory(raProperties);
      recoveryActiveMQConnectionFactory = createRecoveryActiveMQConnectionFactory(raProperties);

      Map<String, String> recoveryConfProps = new HashMap<>();
      recoveryConfProps.put(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY, getJndiName());
      recoveryManager.register(recoveryActiveMQConnectionFactory, raProperties.getUserName(), raProperties.getPassword(), recoveryConfProps);
   }

   public Map<ActivationSpec, ActiveMQActivation> getActivations() {
      return activations;
   }

   public ActiveMQConnectionFactory getDefaultActiveMQConnectionFactory() throws ResourceException {
      if (!configured.getAndSet(true)) {
         try {
            setup();
         } catch (ActiveMQException e) {
            throw new ResourceException("Unable to create activation", e);
         }
      }
      return defaultActiveMQConnectionFactory;
   }

   public String getJgroupsChannelLocatorClass() {
      return raProperties.getJgroupsChannelLocatorClass();
   }

   public void setJgroupsChannelLocatorClass(String jgroupsChannelLocatorClass) {
      raProperties.setJgroupsChannelLocatorClass(jgroupsChannelLocatorClass);
   }

   public String getJgroupsChannelRefName() {
      return raProperties.getJgroupsChannelRefName();
   }

   public void setJgroupsChannelRefName(String jgroupsChannelRefName) {
      raProperties.setJgroupsChannelRefName(jgroupsChannelRefName);
   }

   public synchronized ActiveMQConnectionFactory getConnectionFactory(final ConnectionFactoryProperties overrideProperties) {
      ActiveMQConnectionFactory cf;
      boolean known = false;

      if (!knownConnectionFactories.containsKey(overrideProperties)) {
         cf = newConnectionFactory(overrideProperties);
         knownConnectionFactories.put(overrideProperties, new Pair<>(cf, new AtomicInteger(1)));
      } else {
         Pair<ActiveMQConnectionFactory, AtomicInteger> pair = knownConnectionFactories.get(overrideProperties);
         cf = pair.getA();
         pair.getB().incrementAndGet();
         known = true;
      }

      if (known && cf.getServerLocator().isClosed()) {
         knownConnectionFactories.remove(overrideProperties);
         cf = newConnectionFactory(overrideProperties);
         knownConnectionFactories.put(overrideProperties, new Pair<>(cf, new AtomicInteger(1)));
      }

      return cf;
   }

   public ActiveMQConnectionFactory newConnectionFactory(ConnectionFactoryProperties overrideProperties) {
      ActiveMQConnectionFactory cf;
      List<String> connectorClassName = overrideProperties.getParsedConnectorClassNames() != null ? overrideProperties.getParsedConnectorClassNames() : raProperties.getParsedConnectorClassNames();

      Boolean ha = overrideProperties.isHA() != null ? overrideProperties.isHA() : getHA();

      if (ha == null) {
         ha = ActiveMQClient.DEFAULT_IS_HA;
      }

      BroadcastEndpointFactory endpointFactory = this.createBroadcastEndpointFactory(overrideProperties);

      if (endpointFactory != null) {
         Long refreshTimeout = overrideProperties.getDiscoveryRefreshTimeout() != null ? overrideProperties.getDiscoveryRefreshTimeout() : raProperties.getDiscoveryRefreshTimeout();
         if (refreshTimeout == null) {
            refreshTimeout = ActiveMQClient.DEFAULT_DISCOVERY_REFRESH_TIMEOUT;
         }

         Long initialTimeout = overrideProperties.getDiscoveryInitialWaitTimeout() != null ? overrideProperties.getDiscoveryInitialWaitTimeout() : raProperties.getDiscoveryInitialWaitTimeout();

         if (initialTimeout == null) {
            initialTimeout = ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT;
         }

         DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration().setRefreshTimeout(refreshTimeout).setDiscoveryInitialWaitTimeout(initialTimeout).setBroadcastEndpointFactory(endpointFactory);

         logger.debug("Creating Connection Factory on the resource adapter for discovery={} with ha={}", groupConfiguration, ha);

         if (ha) {
            cf = ActiveMQJMSClient.createConnectionFactoryWithHA(groupConfiguration, JMSFactoryType.XA_CF);
         } else {
            cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(groupConfiguration, JMSFactoryType.XA_CF);
         }
      } else if (connectorClassName != null) {
         TransportConfiguration[] transportConfigurations = new TransportConfiguration[connectorClassName.size()];

         List<Map<String, Object>> connectionParams;
         if (overrideProperties.getParsedConnectorClassNames() != null) {
            connectionParams = overrideProperties.getParsedConnectionParameters();
         } else {
            connectionParams = raProperties.getParsedConnectionParameters();
         }

         for (int i = 0; i < connectorClassName.size(); i++) {
            TransportConfiguration tc;
            if (connectionParams == null || i >= connectionParams.size()) {
               tc = new TransportConfiguration(connectorClassName.get(i));
               logger.debug("No connector params provided using default");
            } else {
               tc = new TransportConfiguration(connectorClassName.get(i), connectionParams.get(i));
            }

            transportConfigurations[i] = tc;
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Creating Connection Factory on the resource adapter for transport={} with ha={}", Arrays.toString(transportConfigurations), ha);
         }

         if (ha) {
            cf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.XA_CF, transportConfigurations);
         } else {
            cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.XA_CF, transportConfigurations);
         }
      } else {
         throw new IllegalArgumentException("must provide either TransportType or DiscoveryGroupAddress and DiscoveryGroupPort for ResourceAdapter Connection Factory");
      }

      cf.setUser(getUserName());
      cf.setPassword(getPassword());
      cf.setPasswordCodec(getPasswordCodec());

      cf.setUseTopologyForLoadBalancing(raProperties.isUseTopologyForLoadBalancing());

      cf.setEnableSharedClientID(true);
      cf.setEnable1xPrefixes(overrideProperties.isEnable1xPrefixes() != null ? overrideProperties.isEnable1xPrefixes() : raProperties.isEnable1xPrefixes() == null ? false : raProperties.isEnable1xPrefixes());
      setParams(cf, overrideProperties);
      return cf;
   }

   public ActiveMQConnectionFactory createRecoveryActiveMQConnectionFactory(final ConnectionFactoryProperties overrideProperties) {
      ActiveMQConnectionFactory cf;
      List<String> connectorClassName = overrideProperties.getParsedConnectorClassNames() != null ? overrideProperties.getParsedConnectorClassNames() : raProperties.getParsedConnectorClassNames();

      if (connectorClassName == null) {
         BroadcastEndpointFactory endpointFactory = this.createBroadcastEndpointFactory(overrideProperties);
         if (endpointFactory == null) {
            throw new IllegalArgumentException("must provide either TransportType or DiscoveryGroupAddress and DiscoveryGroupPort for ResourceAdapter Connection Factory");
         }

         Long refreshTimeout = overrideProperties.getDiscoveryRefreshTimeout() != null ? overrideProperties.getDiscoveryRefreshTimeout() : raProperties.getDiscoveryRefreshTimeout();
         if (refreshTimeout == null) {
            refreshTimeout = ActiveMQClient.DEFAULT_DISCOVERY_REFRESH_TIMEOUT;
         }

         Long initialTimeout = overrideProperties.getDiscoveryInitialWaitTimeout() != null ? overrideProperties.getDiscoveryInitialWaitTimeout() : raProperties.getDiscoveryInitialWaitTimeout();
         if (initialTimeout == null) {
            initialTimeout = ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT;
         }
         DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration().setRefreshTimeout(refreshTimeout).setDiscoveryInitialWaitTimeout(initialTimeout).setBroadcastEndpointFactory(endpointFactory);

         groupConfiguration.setRefreshTimeout(refreshTimeout);

         logger.debug("Creating Recovery Connection Factory on the resource adapter for discovery={}", groupConfiguration);

         cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(groupConfiguration, JMSFactoryType.XA_CF);
      } else {
         TransportConfiguration[] transportConfigurations = new TransportConfiguration[connectorClassName.size()];

         List<Map<String, Object>> connectionParams;
         if (overrideProperties.getParsedConnectorClassNames() != null) {
            connectionParams = overrideProperties.getParsedConnectionParameters();
         } else {
            connectionParams = raProperties.getParsedConnectionParameters();
         }

         for (int i = 0; i < connectorClassName.size(); i++) {
            TransportConfiguration tc;
            if (connectionParams == null || i >= connectionParams.size()) {
               tc = new TransportConfiguration(connectorClassName.get(i));
               logger.debug("No connector params provided using default");
            } else {
               tc = new TransportConfiguration(connectorClassName.get(i), connectionParams.get(i));
            }

            transportConfigurations[i] = tc;
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Creating Recovery Connection Factory on the resource adapter for transport={}", Arrays.toString(transportConfigurations));
         }

         cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.XA_CF, transportConfigurations);
      }

      cf.setUser(getUserName());
      cf.setPassword(getPassword());
      cf.setPasswordCodec(getPasswordCodec());

      setParams(cf, overrideProperties);

      //now make sure we are HA in any way
      cf.setUseTopologyForLoadBalancing(raProperties.isUseTopologyForLoadBalancing());

      cf.setReconnectAttempts(0);
      cf.setInitialConnectAttempts(0);
      cf.setEnable1xPrefixes(raProperties.isEnable1xPrefixes() == null ? false : raProperties.isEnable1xPrefixes());
      cf.setEnableSharedClientID(true);
      return cf;
   }

   protected BroadcastEndpointFactory createBroadcastEndpointFactory(final ConnectionFactoryProperties overrideProperties) {

      String discoveryAddress = overrideProperties.getDiscoveryAddress() != null ? overrideProperties.getDiscoveryAddress() : getDiscoveryAddress();
      if (discoveryAddress != null) {
         Integer discoveryPort = overrideProperties.getDiscoveryPort() != null ? overrideProperties.getDiscoveryPort() : getDiscoveryPort();
         if (discoveryPort == null) {
            discoveryPort = ActiveMQClient.DEFAULT_DISCOVERY_PORT;
         }

         String localBindAddress = overrideProperties.getDiscoveryLocalBindAddress() != null ? overrideProperties.getDiscoveryLocalBindAddress() : raProperties.getDiscoveryLocalBindAddress();
         return new UDPBroadcastEndpointFactory().setGroupAddress(discoveryAddress).setGroupPort(discoveryPort).setLocalBindAddress(localBindAddress).setLocalBindPort(-1);
      }

      String jgroupsChannel = overrideProperties.getJgroupsChannelName() != null ? overrideProperties.getJgroupsChannelName() : getJgroupsChannelName();

      String jgroupsLocatorClassName = raProperties.getJgroupsChannelLocatorClass();
      if (jgroupsLocatorClassName != null) {
         String jchannelRefName = raProperties.getJgroupsChannelRefName();
         JChannel jchannel = ActiveMQRaUtils.locateJGroupsChannel(jgroupsLocatorClassName, jchannelRefName);
         return new ChannelBroadcastEndpointFactory(jchannel, jgroupsChannel);
      }

      String jgroupsFileName = overrideProperties.getJgroupsFile() != null ? overrideProperties.getJgroupsFile() : getJgroupsFile();
      if (jgroupsFileName != null) {
         return new JGroupsFileBroadcastEndpointFactory().setChannelName(jgroupsChannel).setFile(jgroupsFileName);
      }

      return null;
   }

   public Map<String, Object> overrideConnectionParameters(final Map<String, Object> connectionParams,
                                                           final Map<String, Object> overrideConnectionParams) {
      Map<String, Object> map = new HashMap<>();
      if (connectionParams != null) {
         map.putAll(connectionParams);
      }
      if (overrideConnectionParams != null) {
         for (Map.Entry<String, Object> stringObjectEntry : overrideConnectionParams.entrySet()) {
            map.put(stringObjectEntry.getKey(), stringObjectEntry.getValue());
         }
      }
      return map;
   }

   private void setParams(final ActiveMQConnectionFactory cf, final ConnectionFactoryProperties overrideProperties) {
      Boolean booleanVal = overrideProperties.isAutoGroup() != null ? overrideProperties.isAutoGroup() : raProperties.isAutoGroup();
      if (booleanVal != null) {
         cf.setAutoGroup(booleanVal);
      }
      booleanVal = overrideProperties.isBlockOnAcknowledge() != null ? overrideProperties.isBlockOnAcknowledge() : raProperties.isBlockOnAcknowledge();
      if (booleanVal != null) {
         cf.setBlockOnAcknowledge(booleanVal);
      }
      booleanVal = overrideProperties.isBlockOnNonDurableSend() != null ? overrideProperties.isBlockOnNonDurableSend() : raProperties.isBlockOnNonDurableSend();
      if (booleanVal != null) {
         cf.setBlockOnNonDurableSend(booleanVal);
      }
      booleanVal = overrideProperties.isBlockOnDurableSend() != null ? overrideProperties.isBlockOnDurableSend() : raProperties.isBlockOnDurableSend();
      if (booleanVal != null) {
         cf.setBlockOnDurableSend(booleanVal);
      }
      booleanVal = overrideProperties.isPreAcknowledge() != null ? overrideProperties.isPreAcknowledge() : raProperties.isPreAcknowledge();
      if (booleanVal != null) {
         cf.setPreAcknowledge(booleanVal);
      }
      booleanVal = overrideProperties.isUseGlobalPools() != null ? overrideProperties.isUseGlobalPools() : raProperties.isUseGlobalPools();
      if (booleanVal != null) {
         cf.setUseGlobalPools(booleanVal);
      }

      booleanVal = overrideProperties.isCacheLargeMessagesClient() != null ? overrideProperties.isCacheLargeMessagesClient() : raProperties.isCacheLargeMessagesClient();
      if (booleanVal != null) {
         cf.setCacheLargeMessagesClient(booleanVal);
      }

      booleanVal = overrideProperties.isCompressLargeMessage() != null ? overrideProperties.isCompressLargeMessage() : raProperties.isCompressLargeMessage();
      if (booleanVal != null) {
         cf.setCompressLargeMessage(booleanVal);
      }

      booleanVal = overrideProperties.isCacheDestinations() != null ? overrideProperties.isCacheDestinations() : raProperties.isCacheDestinations();
      if (booleanVal != null) {
         cf.setCacheDestinations(booleanVal);
      }

      Integer intVal = overrideProperties.getConsumerMaxRate() != null ? overrideProperties.getConsumerMaxRate() : raProperties.getConsumerMaxRate();
      if (intVal != null) {
         cf.setConsumerMaxRate(intVal);
      }
      intVal = overrideProperties.getConsumerWindowSize() != null ? overrideProperties.getConsumerWindowSize() : raProperties.getConsumerWindowSize();
      if (intVal != null) {
         cf.setConsumerWindowSize(intVal);
      }
      intVal = overrideProperties.getDupsOKBatchSize() != null ? overrideProperties.getDupsOKBatchSize() : raProperties.getDupsOKBatchSize();
      if (intVal != null) {
         cf.setDupsOKBatchSize(intVal);
      }

      intVal = overrideProperties.getMinLargeMessageSize() != null ? overrideProperties.getMinLargeMessageSize() : raProperties.getMinLargeMessageSize();
      if (intVal != null) {
         cf.setMinLargeMessageSize(intVal);
      }
      intVal = overrideProperties.getProducerMaxRate() != null ? overrideProperties.getProducerMaxRate() : raProperties.getProducerMaxRate();
      if (intVal != null) {
         cf.setProducerMaxRate(intVal);
      }
      intVal = overrideProperties.getProducerWindowSize() != null ? overrideProperties.getProducerWindowSize() : raProperties.getProducerWindowSize();
      if (intVal != null) {
         cf.setProducerWindowSize(intVal);
      }
      intVal = overrideProperties.getConfirmationWindowSize() != null ? overrideProperties.getConfirmationWindowSize() : raProperties.getConfirmationWindowSize();
      if (intVal != null) {
         cf.setConfirmationWindowSize(intVal);
      }
      intVal = overrideProperties.getReconnectAttempts() != null ? overrideProperties.getReconnectAttempts() : raProperties.getReconnectAttempts();
      if (intVal != null) {
         cf.setReconnectAttempts(intVal);
      } else {
         //the global default is 0 but we should always try to reconnect JCA
         cf.setReconnectAttempts(-1);
      }
      intVal = overrideProperties.getThreadPoolMaxSize() != null ? overrideProperties.getThreadPoolMaxSize() : raProperties.getThreadPoolMaxSize();
      if (intVal != null) {
         cf.setThreadPoolMaxSize(intVal);
      }
      intVal = overrideProperties.getScheduledThreadPoolMaxSize() != null ? overrideProperties.getScheduledThreadPoolMaxSize() : raProperties.getScheduledThreadPoolMaxSize();
      if (intVal != null) {
         cf.setScheduledThreadPoolMaxSize(intVal);
      }
      intVal = overrideProperties.getTransactionBatchSize() != null ? overrideProperties.getTransactionBatchSize() : raProperties.getTransactionBatchSize();
      if (intVal != null) {
         cf.setTransactionBatchSize(intVal);
      }
      intVal = overrideProperties.getInitialConnectAttempts() != null ? overrideProperties.getInitialConnectAttempts() : raProperties.getInitialConnectAttempts();
      if (intVal != null) {
         cf.setInitialConnectAttempts(intVal);
      }
      intVal = overrideProperties.getInitialMessagePacketSize() != null ? overrideProperties.getInitialMessagePacketSize() : raProperties.getInitialMessagePacketSize();
      if (intVal != null) {
         cf.setInitialMessagePacketSize(intVal);
      }
      intVal = overrideProperties.getCompressionLevel() != null ? overrideProperties.getCompressionLevel() : raProperties.getCompressionLevel();
      if (intVal != null) {
         cf.setCompressionLevel(intVal);
      }

      Long longVal = overrideProperties.getClientFailureCheckPeriod() != null ? overrideProperties.getClientFailureCheckPeriod() : raProperties.getClientFailureCheckPeriod();
      if (longVal != null) {
         cf.setClientFailureCheckPeriod(longVal);
      }
      longVal = overrideProperties.getCallTimeout() != null ? overrideProperties.getCallTimeout() : raProperties.getCallTimeout();
      if (longVal != null) {
         cf.setCallTimeout(longVal);
      }
      longVal = overrideProperties.getCallFailoverTimeout() != null ? overrideProperties.getCallFailoverTimeout() : raProperties.getCallFailoverTimeout();
      if (longVal != null) {
         cf.setCallFailoverTimeout(longVal);
      }
      longVal = overrideProperties.getConnectionTTL() != null ? overrideProperties.getConnectionTTL() : raProperties.getConnectionTTL();
      if (longVal != null) {
         cf.setConnectionTTL(longVal);
      }

      longVal = overrideProperties.getRetryInterval() != null ? overrideProperties.getRetryInterval() : raProperties.getRetryInterval();
      if (longVal != null) {
         cf.setRetryInterval(longVal);
      }

      longVal = overrideProperties.getMaxRetryInterval() != null ? overrideProperties.getMaxRetryInterval() : raProperties.getMaxRetryInterval();
      if (longVal != null) {
         cf.setMaxRetryInterval(longVal);
      }

      Double doubleVal = overrideProperties.getRetryIntervalMultiplier() != null ? overrideProperties.getRetryIntervalMultiplier() : raProperties.getRetryIntervalMultiplier();
      if (doubleVal != null) {
         cf.setRetryIntervalMultiplier(doubleVal);
      }
      String stringVal = overrideProperties.getClientID() != null ? overrideProperties.getClientID() : raProperties.getClientID();
      if (stringVal != null) {
         cf.setClientID(stringVal);
      }
      stringVal = overrideProperties.getConnectionLoadBalancingPolicyClassName() != null ? overrideProperties.getConnectionLoadBalancingPolicyClassName() : raProperties.getConnectionLoadBalancingPolicyClassName();
      if (stringVal != null) {
         cf.setConnectionLoadBalancingPolicyClassName(stringVal);
      }
      stringVal = overrideProperties.getProtocolManagerFactoryStr() != null ? overrideProperties.getProtocolManagerFactoryStr() : raProperties.getProtocolManagerFactoryStr();
      if (stringVal != null) {
         cf.setProtocolManagerFactoryStr(stringVal);
      }
      stringVal = overrideProperties.getDeserializationBlackList() != null ? overrideProperties.getDeserializationBlackList() : raProperties.getDeserializationBlackList();
      if (stringVal != null) {
         cf.setDeserializationBlackList(stringVal);
      }
      stringVal = overrideProperties.getDeserializationWhiteList() != null ? overrideProperties.getDeserializationWhiteList() : raProperties.getDeserializationWhiteList();
      if (stringVal != null) {
         cf.setDeserializationWhiteList(stringVal);
      }
      stringVal = overrideProperties.getDeserializationDenyList() != null ? overrideProperties.getDeserializationDenyList() : raProperties.getDeserializationDenyList();
      if (stringVal != null) {
         cf.setDeserializationDenyList(stringVal);
      }
      stringVal = overrideProperties.getDeserializationAllowList() != null ? overrideProperties.getDeserializationAllowList() : raProperties.getDeserializationAllowList();
      if (stringVal != null) {
         cf.setDeserializationAllowList(stringVal);
      }

      cf.setIgnoreJTA(isIgnoreJTA());
   }

   public void setManagedConnectionFactory(ActiveMQRAManagedConnectionFactory activeMQRAManagedConnectionFactory) {
      managedConnectionFactories.add(activeMQRAManagedConnectionFactory);
   }

   public String getCodec() {
      return raProperties.getCodec();
   }

   public synchronized void closeConnectionFactory(ConnectionFactoryProperties properties) {
      Pair<ActiveMQConnectionFactory, AtomicInteger> factoryPair = knownConnectionFactories.get(properties);
      if (factoryPair == null) {
         logger.debug("No connection factory found for client '{}', probably already closed.", properties.getClientID());
         return;
      }

      ActiveMQConnectionFactory factory = factoryPair.getA();
      int referenceCount = factoryPair.getB().decrementAndGet();

      if (factory != null && factory != defaultActiveMQConnectionFactory && referenceCount == 0) {
         knownConnectionFactories.remove(properties);
         factory.close();
      }
   }

   public Boolean isIgnoreJTA() {
      return ignoreJTA;
   }

   public void setIgnoreJTA(Boolean ignoreJTA) {
      this.ignoreJTA = ignoreJTA;
   }
}
