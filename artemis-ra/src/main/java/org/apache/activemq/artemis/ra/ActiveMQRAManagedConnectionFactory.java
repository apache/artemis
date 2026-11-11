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

import javax.jms.ConnectionMetaData;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class ActiveMQRAManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final long serialVersionUID = -1452379518562456741L;

   private ActiveMQResourceAdapter ra;

   private ConnectionManager cm;

   private final ActiveMQRAMCFProperties mcfProperties;

   private ActiveMQConnectionFactory recoveryConnectionFactory;

   private XARecoveryConfig resourceRecovery;

   public ActiveMQRAManagedConnectionFactory() {
      logger.trace("constructor()");

      ra = null;
      cm = null;
      mcfProperties = new ActiveMQRAMCFProperties();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object createConnectionFactory() throws ResourceException {
      logger.debug("createConnectionFactory()");

      return createConnectionFactory(new ActiveMQRAConnectionManager());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object createConnectionFactory(final ConnectionManager cxManager) throws ResourceException {
      logger.trace("createConnectionFactory({})", cxManager);

      cm = cxManager;

      ActiveMQRAConnectionFactory cf = new ActiveMQRAConnectionFactoryImpl(this, cm);

      logger.trace("Created connection factory: {}, using connection manager: {}", cf, cm);

      return cf;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManagedConnection createManagedConnection(final Subject subject,
                                                    final ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      logger.trace("createManagedConnection({}, {})", subject, cxRequestInfo);

      ActiveMQRAConnectionRequestInfo cri = getCRI((ActiveMQRAConnectionRequestInfo) cxRequestInfo);

      ActiveMQRACredential credential = ActiveMQRACredential.getCredential(this, subject, cri);

      logger.trace("jms credential: {}", credential);

      ActiveMQRAManagedConnection mc = new ActiveMQRAManagedConnection(this, cri, ra, credential.getUserName(), credential.getPassword());

      logger.trace("created new managed connection: {}", mc);

      registerRecovery();

      return mc;
   }

   private synchronized void registerRecovery() {
      if (recoveryConnectionFactory == null) {
         recoveryConnectionFactory = ra.createRecoveryActiveMQConnectionFactory(mcfProperties);

         Map<String, String> recoveryConfProps = new HashMap<>();
         recoveryConfProps.put(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY, ra.getJndiName());
         resourceRecovery = ra.getRecoveryManager().register(recoveryConnectionFactory, null, null, recoveryConfProps);
      }
   }

   public XARecoveryConfig getResourceRecovery() {
      return resourceRecovery;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManagedConnection matchManagedConnections(final Set connectionSet,
                                                    final Subject subject,
                                                    final ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      if (logger.isTraceEnabled()) {
         logger.trace("matchManagedConnections({}, {}, {})", connectionSet, subject, cxRequestInfo);
      }

      ActiveMQRAConnectionRequestInfo cri = getCRI((ActiveMQRAConnectionRequestInfo) cxRequestInfo);
      ActiveMQRACredential credential = ActiveMQRACredential.getCredential(this, subject, cri);

      logger.trace("Looking for connection matching credentials: {}", credential);

      for (Object obj : connectionSet) {
         if (obj instanceof ActiveMQRAManagedConnection mc) {
            ManagedConnectionFactory mcf = mc.getManagedConnectionFactory();

            if ((mc.getUserName() == null || mc.getUserName() != null && mc.getUserName().equals(credential.getUserName())) && mcf.equals(this)) {
               if (cri.equals(mc.getCRI())) {
                  logger.trace("Found matching connection: {}", mc);

                  return mc;
               }
            }
         }
      }

      logger.trace("No matching connection was found");

      return null;
   }

   /**
    * <b>NOT SUPPORTED</b>
    * <p>
    * {@inheritDoc}
    */
   @Override
   public void setLogWriter(final PrintWriter out) throws ResourceException {
      logger.trace("setLogWriter({})", out);
   }

   /**
    * <b>NOT SUPPORTED</b>
    * <p>
    * {@inheritDoc}
    */
   @Override
   public PrintWriter getLogWriter() throws ResourceException {
      logger.trace("getLogWriter()");

      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ResourceAdapter getResourceAdapter() {
      logger.trace("getResourceAdapter()");

      return ra;
   }

   public boolean isIgnoreJTA() {
      return ra.isIgnoreJTA();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setResourceAdapter(final ResourceAdapter ra) throws ResourceException {
      logger.trace("setResourceAdapter({})", ra);

      if (ra == null || !(ra instanceof ActiveMQResourceAdapter)) {
         throw new ResourceException("Resource adapter is " + ra);
      }

      this.ra = (ActiveMQResourceAdapter) ra;
      this.ra.setManagedConnectionFactory(this);
   }

   @Override
   public boolean equals(final Object obj) {
      logger.trace("equals({})", obj);
      if (this == obj) {
         return true;
      }

      if (!(obj instanceof ActiveMQRAManagedConnectionFactory other)) {
         return false;
      }

      return Objects.equals(mcfProperties, other.getProperties()) &&
             Objects.equals(ra, other.getResourceAdapter());
   }

   @Override
   public int hashCode() {
      logger.trace("hashCode()");
      return Objects.hash(mcfProperties, ra);
   }

   public String getSessionDefaultType() {
      logger.trace("getSessionDefaultType()");

      return mcfProperties.getSessionDefaultType();
   }

   /**
    * Set the default session type
    *
    * @param type either {@literal javax.jms.Topic} or {@literal javax.jms.Queue}
    */
   public void setSessionDefaultType(final String type) {
      logger.trace("setSessionDefaultType({})", type);

      mcfProperties.setSessionDefaultType(type);
   }

   public String getConnectionParameters() {
      return mcfProperties.getStrConnectionParameters();
   }

   public void setConnectionParameters(final String configuration) {
      mcfProperties.setConnectionParameters(configuration);
   }

   public String getConnectorClassName() {
      return mcfProperties.getConnectorClassName();
   }

   public void setConnectorClassName(final String value) {
      mcfProperties.setConnectorClassName(value);
   }

   public String getConnectionLoadBalancingPolicyClassName() {
      return mcfProperties.getConnectionLoadBalancingPolicyClassName();
   }

   public void setConnectionLoadBalancingPolicyClassName(final String connectionLoadBalancingPolicyClassName) {
      mcfProperties.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }

   public String getDiscoveryAddress() {
      return mcfProperties.getDiscoveryAddress();
   }

   public void setDiscoveryAddress(final String discoveryAddress) {
      mcfProperties.setDiscoveryAddress(discoveryAddress);
   }

   public Integer getDiscoveryPort() {
      return mcfProperties.getDiscoveryPort();
   }

   public void setDiscoveryPort(final Integer discoveryPort) {
      mcfProperties.setDiscoveryPort(discoveryPort);
   }

   public Long getDiscoveryRefreshTimeout() {
      return mcfProperties.getDiscoveryRefreshTimeout();
   }

   public void setDiscoveryRefreshTimeout(final Long discoveryRefreshTimeout) {
      mcfProperties.setDiscoveryRefreshTimeout(discoveryRefreshTimeout);
   }

   public Long getDiscoveryInitialWaitTimeout() {
      return mcfProperties.getDiscoveryInitialWaitTimeout();
   }

   public void setDiscoveryInitialWaitTimeout(final Long discoveryInitialWaitTimeout) {
      mcfProperties.setDiscoveryInitialWaitTimeout(discoveryInitialWaitTimeout);
   }

   public String getClientID() {
      return mcfProperties.getClientID();
   }

   public void setClientID(final String clientID) {
      mcfProperties.setClientID(clientID);
   }

   public Integer getDupsOKBatchSize() {
      return mcfProperties.getDupsOKBatchSize();
   }

   public void setDupsOKBatchSize(final Integer dupsOKBatchSize) {
      mcfProperties.setDupsOKBatchSize(dupsOKBatchSize);
   }

   public Integer getTransactionBatchSize() {
      return mcfProperties.getTransactionBatchSize();
   }

   public void setTransactionBatchSize(final Integer transactionBatchSize) {
      mcfProperties.setTransactionBatchSize(transactionBatchSize);
   }

   public Long getClientFailureCheckPeriod() {
      return mcfProperties.getClientFailureCheckPeriod();
   }

   public void setClientFailureCheckPeriod(final Long clientFailureCheckPeriod) {
      mcfProperties.setClientFailureCheckPeriod(clientFailureCheckPeriod);
   }

   public Long getConnectionTTL() {
      return mcfProperties.getConnectionTTL();
   }

   public void setConnectionTTL(final Long connectionTTL) {
      mcfProperties.setConnectionTTL(connectionTTL);
   }

   public Long getCallTimeout() {
      return mcfProperties.getCallTimeout();
   }

   public void setCallTimeout(final Long callTimeout) {
      mcfProperties.setCallTimeout(callTimeout);
   }

   public Integer getConsumerWindowSize() {
      return mcfProperties.getConsumerWindowSize();
   }

   public void setConsumerWindowSize(final Integer consumerWindowSize) {
      mcfProperties.setConsumerWindowSize(consumerWindowSize);
   }

   public Integer getConsumerMaxRate() {
      return mcfProperties.getConsumerMaxRate();
   }

   public void setConsumerMaxRate(final Integer consumerMaxRate) {
      mcfProperties.setConsumerMaxRate(consumerMaxRate);
   }

   public Integer getConfirmationWindowSize() {
      return mcfProperties.getConfirmationWindowSize();
   }

   public void setConfirmationWindowSize(final Integer confirmationWindowSize) {
      mcfProperties.setConfirmationWindowSize(confirmationWindowSize);
   }

   public Integer getProducerMaxRate() {
      return mcfProperties.getProducerMaxRate();
   }

   public void setProducerMaxRate(final Integer producerMaxRate) {
      mcfProperties.setProducerMaxRate(producerMaxRate);
   }

   public Integer getMinLargeMessageSize() {
      return mcfProperties.getMinLargeMessageSize();
   }

   public void setMinLargeMessageSize(final Integer minLargeMessageSize) {
      mcfProperties.setMinLargeMessageSize(minLargeMessageSize);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getBlockOnAcknowledge() {
      return mcfProperties.isBlockOnAcknowledge();
   }

   public Boolean isBlockOnAcknowledge() {
      return mcfProperties.isBlockOnAcknowledge();
   }

   public void setBlockOnAcknowledge(final Boolean blockOnAcknowledge) {
      mcfProperties.setBlockOnAcknowledge(blockOnAcknowledge);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getBlockOnNonDurableSend() {
      return mcfProperties.isBlockOnNonDurableSend();
   }

   public Boolean isBlockOnNonDurableSend() {
      return mcfProperties.isBlockOnNonDurableSend();
   }

   public void setBlockOnNonDurableSend(final Boolean blockOnNonDurableSend) {
      mcfProperties.setBlockOnNonDurableSend(blockOnNonDurableSend);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getBlockOnDurableSend() {
      return mcfProperties.isBlockOnDurableSend();
   }

   public Boolean isBlockOnDurableSend() {
      return mcfProperties.isBlockOnDurableSend();
   }

   public void setBlockOnDurableSend(final Boolean blockOnDurableSend) {
      mcfProperties.setBlockOnDurableSend(blockOnDurableSend);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getAutoGroup() {
      return mcfProperties.isAutoGroup();
   }

   public Boolean isAutoGroup() {
      return mcfProperties.isAutoGroup();
   }

   public void setAutoGroup(final Boolean autoGroup) {
      mcfProperties.setAutoGroup(autoGroup);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getPreAcknowledge() {
      return mcfProperties.isPreAcknowledge();
   }

   public Boolean isPreAcknowledge() {
      return mcfProperties.isPreAcknowledge();
   }

   public void setPreAcknowledge(final Boolean preAcknowledge) {
      mcfProperties.setPreAcknowledge(preAcknowledge);
   }

   public Long getRetryInterval() {
      return mcfProperties.getRetryInterval();
   }

   public void setRetryInterval(final Long retryInterval) {
      mcfProperties.setRetryInterval(retryInterval);
   }

   public Double getRetryIntervalMultiplier() {
      return mcfProperties.getRetryIntervalMultiplier();
   }

   public void setRetryIntervalMultiplier(final Double retryIntervalMultiplier) {
      mcfProperties.setRetryIntervalMultiplier(retryIntervalMultiplier);
   }

   public Integer getReconnectAttempts() {
      return mcfProperties.getReconnectAttempts();
   }

   public void setReconnectAttempts(final Integer reconnectAttempts) {
      mcfProperties.setReconnectAttempts(reconnectAttempts);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getUseGlobalPools() {
      return mcfProperties.isUseGlobalPools();
   }

   public Boolean isUseGlobalPools() {
      return mcfProperties.isUseGlobalPools();
   }

   public void setUseGlobalPools(final Boolean useGlobalPools) {
      mcfProperties.setUseGlobalPools(useGlobalPools);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getCacheDestinations() {
      return mcfProperties.isCacheDestinations();
   }

   public Boolean isCacheDestinations() {
      return mcfProperties.isCacheDestinations();
   }

   public void setCacheDestinations(final Boolean cacheDestinations) {
      mcfProperties.setCacheDestinations(cacheDestinations);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getEnable1xPrefixes() {
      return mcfProperties.isEnable1xPrefixes();
   }

   public Boolean isEnable1xPrefixes() {
      return mcfProperties.isEnable1xPrefixes();
   }

   public void setEnable1xPrefixes(final Boolean enable1xPrefixes) {
      mcfProperties.setEnable1xPrefixes(enable1xPrefixes);
   }

   public Integer getScheduledThreadPoolMaxSize() {
      return mcfProperties.getScheduledThreadPoolMaxSize();
   }

   public void setScheduledThreadPoolMaxSize(final Integer scheduledThreadPoolMaxSize) {
      mcfProperties.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
   }

   public Integer getThreadPoolMaxSize() {
      return mcfProperties.getThreadPoolMaxSize();
   }

   public void setThreadPoolMaxSize(final Integer threadPoolMaxSize) {
      mcfProperties.setThreadPoolMaxSize(threadPoolMaxSize);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getHA() {
      return mcfProperties.isHA();
   }

   public Boolean isHA() {
      return mcfProperties.isHA();
   }

   public void setAllowLocalTransactions(Boolean allowLocalTransactions) {
      mcfProperties.setAllowLocalTransactions(allowLocalTransactions);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getAllowLocalTransactions() {
      return mcfProperties.isAllowLocalTransactions();
   }

   public Boolean isAllowLocalTransactions() {
      return mcfProperties.isAllowLocalTransactions();
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getInJtaTransaction() {
      return mcfProperties.isInJtaTransaction();
   }

   public Boolean isInJtaTransaction() {
      return mcfProperties.isInJtaTransaction();
   }

   public void setInJtaTransaction(Boolean inJtaTransaction) {
      mcfProperties.setInJtaTransaction(inJtaTransaction);
   }

   public void setHA(Boolean ha) {
      mcfProperties.setHA(ha);
   }

   public Boolean isCompressLargeMessage() {
      return mcfProperties.isCompressLargeMessage();
   }

   public void setCompressLargeMessage(final Boolean compressLargeMessage) {
      mcfProperties.setCompressLargeMessage(compressLargeMessage);
   }

   public Integer getCompressionLevel() {
      return mcfProperties.getCompressionLevel();
   }

   public void setCompressionLevel(final Integer compressionLevel) {
      mcfProperties.setCompressionLevel(compressionLevel);
   }

   public Integer getInitialConnectAttempts() {
      return mcfProperties.getInitialConnectAttempts();
   }

   public void setInitialConnectAttempts(final Integer initialConnectAttempts) {
      mcfProperties.setInitialConnectAttempts(initialConnectAttempts);
   }

   public Integer getUseTryLock() {
      logger.trace("getUseTryLock()");

      return mcfProperties.getUseTryLock();
   }

   public void setUseTryLock(final Integer useTryLock) {
      logger.trace("setUseTryLock({})", useTryLock);

      mcfProperties.setUseTryLock(useTryLock);
   }

   public ConnectionMetaData getMetaData() {
      logger.trace("getMetadata()");

      return new ActiveMQRAConnectionMetaData();
   }

   protected ActiveMQRAMCFProperties getProperties() {
      logger.trace("getProperties()");

      return mcfProperties;
   }

   private ActiveMQRAConnectionRequestInfo getCRI(final ActiveMQRAConnectionRequestInfo info) {
      logger.trace("getCRI({})", info);

      if (info == null) {
         // Create a default one
         return new ActiveMQRAConnectionRequestInfo(ra.getProperties(), mcfProperties.getType());
      } else {
         // Fill the one with any defaults
         info.setDefaults(ra.getProperties());
         return info;
      }
   }

   // this should be called when ActiveMQResourceAdapter.stop() is called since this MCF is registered with it
   public void stop() {
      if (resourceRecovery != null) {
         ra.getRecoveryManager().unRegister(resourceRecovery);
      }

      if (recoveryConnectionFactory != null) {
         recoveryConnectionFactory.close();
         recoveryConnectionFactory = null;
      }
   }
}
