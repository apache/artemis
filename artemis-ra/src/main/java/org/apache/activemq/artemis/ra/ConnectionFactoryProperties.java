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

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.jms.client.ConnectionFactoryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

public class ConnectionFactoryProperties implements ConnectionFactoryOptions {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean hasBeenUpdated = false;

   /**
    * The transport type, changing the default configured from the RA
    */
   private List<String> connectorClassName;

   /**
    * The transport config, changing the default configured from the RA
    */
   private List<Map<String, Object>> connectionParameters;

   private Boolean ha;

   private String connectionLoadBalancingPolicyClassName;

   private String jgroupsFile;

   private String jgroupsChannelName;

   private String discoveryAddress;

   private Integer discoveryPort;

   private String discoveryLocalBindAddress;

   private Long discoveryRefreshTimeout;

   private Long discoveryInitialWaitTimeout;

   private String clientID;

   private Integer dupsOKBatchSize;

   private Integer transactionBatchSize;

   private Long clientFailureCheckPeriod;

   private Long connectionTTL;

   private Boolean cacheLargeMessagesClient;

   private Long callTimeout;

   private Long callFailoverTimeout;

   private Boolean compressLargeMessage;

   private Integer compressionLevel;

   private Integer consumerWindowSize;

   private Integer producerWindowSize;

   private Integer consumerMaxRate;

   private Integer confirmationWindowSize;

   private Integer producerMaxRate;

   private Integer minLargeMessageSize;

   private Boolean blockOnAcknowledge;

   private Boolean blockOnNonDurableSend;

   private Boolean blockOnDurableSend;

   private Boolean autoGroup;

   private Boolean preAcknowledge;

   private Integer initialConnectAttempts;

   private Long retryInterval;

   private Double retryIntervalMultiplier;

   private Long maxRetryInterval;

   private Integer reconnectAttempts;

   private Boolean useGlobalPools;

   private Boolean cacheDestinations;

   private Boolean enable1xPrefixes;

   private Integer initialMessagePacketSize;

   private Integer scheduledThreadPoolMaxSize;

   private Integer threadPoolMaxSize;

   private String groupID;

   private String protocolManagerFactoryStr;

   private String deserializationDenyList;

   private String deserializationAllowList;

   private Boolean enableSharedClientID;

   public List<String> getParsedConnectorClassNames() {
      return connectorClassName;
   }

   public List<Map<String, Object>> getParsedConnectionParameters() {
      return connectionParameters;
   }

   public void setParsedConnectionParameters(final List<Map<String, Object>> connectionParameters) {
      this.connectionParameters = connectionParameters;
      hasBeenUpdated = true;
   }

   public void setParsedConnectorClassNames(final List<String> value) {
      connectorClassName = value;
      hasBeenUpdated = true;
   }

   public Boolean isHA() {
      return ha;
   }

   public void setHA(final Boolean ha) {
      hasBeenUpdated = true;
      this.ha = ha;
   }

   public Boolean isCacheLargeMessagesClient() {
      return cacheLargeMessagesClient;
   }

   public void setCacheLargeMessagesClient(Boolean cacheLargeMessagesClient) {
      hasBeenUpdated = true;
      this.cacheLargeMessagesClient = cacheLargeMessagesClient;
   }

   public Boolean isCompressLargeMessage() {
      return compressLargeMessage;
   }

   public void setCompressLargeMessage(Boolean compressLargeMessage) {
      hasBeenUpdated = true;
      this.compressLargeMessage = compressLargeMessage;
   }

   public Integer getCompressionLevel() {
      return compressionLevel;
   }

   public void setCompressionLevel(Integer compressionLevel) {
      hasBeenUpdated = true;
      this.compressionLevel = compressionLevel;
   }

   public String getConnectionLoadBalancingPolicyClassName() {
      logger.trace("getConnectionLoadBalancingPolicyClassName()");

      return connectionLoadBalancingPolicyClassName;
   }

   public void setConnectionLoadBalancingPolicyClassName(final String connectionLoadBalancingPolicyClassName) {
      logger.trace("setSessionDefaultType({})", connectionLoadBalancingPolicyClassName);

      hasBeenUpdated = true;
      this.connectionLoadBalancingPolicyClassName = connectionLoadBalancingPolicyClassName;
   }

   public String getDiscoveryAddress() {
      logger.trace("getDiscoveryAddress()");

      return discoveryAddress;
   }

   public void setDiscoveryAddress(final String discoveryAddress) {
      logger.trace("setDiscoveryAddress({})", discoveryAddress);

      hasBeenUpdated = true;
      this.discoveryAddress = discoveryAddress;
   }

   public Integer getDiscoveryPort() {
      logger.trace("getDiscoveryPort()");

      return discoveryPort;
   }

   public void setDiscoveryLocalBindAddress(final String discoveryLocalBindAddress) {
      logger.trace("setDiscoveryLocalBindAddress({})", discoveryLocalBindAddress);

      hasBeenUpdated = true;
      this.discoveryLocalBindAddress = discoveryLocalBindAddress;
   }

   public String getDiscoveryLocalBindAddress() {
      logger.trace("getDiscoveryLocalBindAddress()");

      return discoveryLocalBindAddress;
   }

   public void setDiscoveryPort(final Integer discoveryPort) {
      logger.trace("setDiscoveryPort({})", discoveryPort);

      hasBeenUpdated = true;
      this.discoveryPort = discoveryPort;
   }

   public Long getDiscoveryRefreshTimeout() {
      logger.trace("getDiscoveryRefreshTimeout()");

      return discoveryRefreshTimeout;
   }

   public void setDiscoveryRefreshTimeout(final Long discoveryRefreshTimeout) {
      logger.trace("setDiscoveryRefreshTimeout({})", discoveryRefreshTimeout);

      hasBeenUpdated = true;
      this.discoveryRefreshTimeout = discoveryRefreshTimeout;
   }

   public Long getDiscoveryInitialWaitTimeout() {
      logger.trace("getDiscoveryInitialWaitTimeout()");

      return discoveryInitialWaitTimeout;
   }

   public void setDiscoveryInitialWaitTimeout(final Long discoveryInitialWaitTimeout) {
      logger.trace("setDiscoveryInitialWaitTimeout({})", discoveryInitialWaitTimeout);

      hasBeenUpdated = true;
      this.discoveryInitialWaitTimeout = discoveryInitialWaitTimeout;
   }

   public String getClientID() {
      logger.trace("getClientID()");

      return clientID;
   }

   public void setClientID(final String clientID) {
      logger.trace("setClientID({})", clientID);

      hasBeenUpdated = true;
      this.clientID = clientID;
   }

   public Integer getDupsOKBatchSize() {
      logger.trace("getDupsOKBatchSize()");

      return dupsOKBatchSize;
   }

   public void setDupsOKBatchSize(final Integer dupsOKBatchSize) {
      logger.trace("setDupsOKBatchSize({})", dupsOKBatchSize);

      hasBeenUpdated = true;
      this.dupsOKBatchSize = dupsOKBatchSize;
   }

   public Integer getTransactionBatchSize() {
      logger.trace("getTransactionBatchSize()");

      return transactionBatchSize;
   }

   public void setTransactionBatchSize(final Integer transactionBatchSize) {
      logger.trace("setTransactionBatchSize({})", transactionBatchSize);

      hasBeenUpdated = true;
      this.transactionBatchSize = transactionBatchSize;
   }

   public Long getClientFailureCheckPeriod() {
      logger.trace("getClientFailureCheckPeriod()");

      return clientFailureCheckPeriod;
   }

   public void setClientFailureCheckPeriod(final Long clientFailureCheckPeriod) {
      logger.trace("setClientFailureCheckPeriod({})", clientFailureCheckPeriod);

      hasBeenUpdated = true;
      this.clientFailureCheckPeriod = clientFailureCheckPeriod;
   }

   public Long getConnectionTTL() {
      logger.trace("getConnectionTTL()");

      return connectionTTL;
   }

   public void setConnectionTTL(final Long connectionTTL) {
      logger.trace("setConnectionTTL({})", connectionTTL);

      hasBeenUpdated = true;
      this.connectionTTL = connectionTTL;
   }

   public Long getCallTimeout() {
      logger.trace("getCallTimeout()");

      return callTimeout;
   }

   public void setCallTimeout(final Long callTimeout) {
      logger.trace("setCallTimeout({})", callTimeout);

      hasBeenUpdated = true;
      this.callTimeout = callTimeout;
   }

   public Long getCallFailoverTimeout() {
      logger.trace("getCallFailoverTimeout()");

      return callFailoverTimeout;
   }

   public void setCallFailoverTimeout(final Long callFailoverTimeout) {
      logger.trace("setCallFailoverTimeout({})", callFailoverTimeout);

      hasBeenUpdated = true;
      this.callFailoverTimeout = callFailoverTimeout;
   }

   public Integer getConsumerWindowSize() {
      logger.trace("getConsumerWindowSize()");

      return consumerWindowSize;
   }

   public void setConsumerWindowSize(final Integer consumerWindowSize) {
      logger.trace("setConsumerWindowSize({})", consumerWindowSize);

      hasBeenUpdated = true;
      this.consumerWindowSize = consumerWindowSize;
   }

   public Integer getConsumerMaxRate() {
      logger.trace("getConsumerMaxRate()");

      return consumerMaxRate;
   }

   public void setConsumerMaxRate(final Integer consumerMaxRate) {
      logger.trace("setConsumerMaxRate({})", consumerMaxRate);

      hasBeenUpdated = true;
      this.consumerMaxRate = consumerMaxRate;
   }

   public Integer getConfirmationWindowSize() {
      logger.trace("getConfirmationWindowSize()");

      return confirmationWindowSize;
   }

   public void setConfirmationWindowSize(final Integer confirmationWindowSize) {
      logger.trace("setConfirmationWindowSize({})", confirmationWindowSize);

      hasBeenUpdated = true;
      this.confirmationWindowSize = confirmationWindowSize;
   }

   @Deprecated
   public Boolean isFailoverOnInitialConnection() {
      return false;
   }

   @Deprecated
   public void setFailoverOnInitialConnection(Boolean failoverOnInitialConnection) {
   }

   public Integer getProducerMaxRate() {
      logger.trace("getProducerMaxRate()");

      return producerMaxRate;
   }

   public void setProducerMaxRate(final Integer producerMaxRate) {
      logger.trace("setProducerMaxRate({})", producerMaxRate);

      hasBeenUpdated = true;
      this.producerMaxRate = producerMaxRate;
   }

   public Integer getProducerWindowSize() {
      logger.trace("getProducerWindowSize()");

      return producerWindowSize;
   }

   public void setProducerWindowSize(final Integer producerWindowSize) {
      logger.trace("setProducerWindowSize({})", producerWindowSize);

      hasBeenUpdated = true;
      this.producerWindowSize = producerWindowSize;
   }

   public Integer getMinLargeMessageSize() {
      logger.trace("getMinLargeMessageSize()");

      return minLargeMessageSize;
   }

   public void setMinLargeMessageSize(final Integer minLargeMessageSize) {
      logger.trace("setMinLargeMessageSize({})", minLargeMessageSize);

      hasBeenUpdated = true;
      this.minLargeMessageSize = minLargeMessageSize;
   }

   public Boolean isBlockOnAcknowledge() {
      logger.trace("isBlockOnAcknowledge()");

      return blockOnAcknowledge;
   }

   public void setBlockOnAcknowledge(final Boolean blockOnAcknowledge) {
      logger.trace("setBlockOnAcknowledge({})", blockOnAcknowledge);

      hasBeenUpdated = true;
      this.blockOnAcknowledge = blockOnAcknowledge;
   }

   public Boolean isBlockOnNonDurableSend() {
      logger.trace("isBlockOnNonDurableSend()");

      return blockOnNonDurableSend;
   }

   public void setBlockOnNonDurableSend(final Boolean blockOnNonDurableSend) {
      logger.trace("setBlockOnNonDurableSend({})", blockOnNonDurableSend);

      hasBeenUpdated = true;
      this.blockOnNonDurableSend = blockOnNonDurableSend;
   }

   public Boolean isBlockOnDurableSend() {
      logger.trace("isBlockOnDurableSend()");

      return blockOnDurableSend;
   }

   public void setBlockOnDurableSend(final Boolean blockOnDurableSend) {
      logger.trace("setBlockOnDurableSend({})", blockOnDurableSend);

      hasBeenUpdated = true;
      this.blockOnDurableSend = blockOnDurableSend;
   }

   public Boolean isAutoGroup() {
      logger.trace("isAutoGroup()");

      return autoGroup;
   }

   public void setAutoGroup(final Boolean autoGroup) {
      logger.trace("setAutoGroup({})", autoGroup);

      hasBeenUpdated = true;
      this.autoGroup = autoGroup;
   }

   public Boolean isPreAcknowledge() {
      logger.trace("isPreAcknowledge()");

      return preAcknowledge;
   }

   public void setPreAcknowledge(final Boolean preAcknowledge) {
      logger.trace("setPreAcknowledge({})", preAcknowledge);

      hasBeenUpdated = true;
      this.preAcknowledge = preAcknowledge;
   }

   public Long getRetryInterval() {
      logger.trace("getRetryInterval()");

      return retryInterval;
   }

   public void setRetryInterval(final Long retryInterval) {
      logger.trace("setRetryInterval({})", retryInterval);

      hasBeenUpdated = true;
      this.retryInterval = retryInterval;
   }

   public Double getRetryIntervalMultiplier() {
      logger.trace("getRetryIntervalMultiplier()");

      return retryIntervalMultiplier;
   }

   public void setRetryIntervalMultiplier(final Double retryIntervalMultiplier) {
      logger.trace("setRetryIntervalMultiplier({})", retryIntervalMultiplier);

      hasBeenUpdated = true;
      this.retryIntervalMultiplier = retryIntervalMultiplier;
   }

   public Long getMaxRetryInterval() {
      return maxRetryInterval;
   }

   public void setMaxRetryInterval(Long maxRetryInterval) {
      hasBeenUpdated = true;
      this.maxRetryInterval = maxRetryInterval;
   }

   public Integer getReconnectAttempts() {
      logger.trace("getReconnectAttempts()");

      return reconnectAttempts;
   }

   public void setReconnectAttempts(final Integer reconnectAttempts) {
      logger.trace("setReconnectAttempts({})", reconnectAttempts);

      hasBeenUpdated = true;
      this.reconnectAttempts = reconnectAttempts;
   }

   public Boolean isUseGlobalPools() {
      logger.trace("isUseGlobalPools()");

      return useGlobalPools;
   }

   public void setUseGlobalPools(final Boolean useGlobalPools) {
      logger.trace("setUseGlobalPools({})", useGlobalPools);

      hasBeenUpdated = true;
      this.useGlobalPools = useGlobalPools;
   }

   public Boolean isCacheDestinations() {
      logger.trace("isCacheDestinations()");

      return cacheDestinations;
   }

   public void setCacheDestinations(final Boolean cacheDestinations) {
      logger.trace("setCacheDestinations({})", cacheDestinations);

      hasBeenUpdated = true;
      this.cacheDestinations = cacheDestinations;
   }

   public Boolean isEnable1xPrefixes() {
      logger.trace("isEnable1xPrefixes()");

      return enable1xPrefixes;
   }

   public void setEnable1xPrefixes(final Boolean enable1xPrefixes) {
      logger.trace("setEnable1xPrefixes({})", enable1xPrefixes);

      hasBeenUpdated = true;
      this.enable1xPrefixes = enable1xPrefixes;
   }

   public Integer getScheduledThreadPoolMaxSize() {
      logger.trace("getScheduledThreadPoolMaxSize()");

      return scheduledThreadPoolMaxSize;
   }

   public void setScheduledThreadPoolMaxSize(final Integer scheduledThreadPoolMaxSize) {
      logger.trace("setScheduledThreadPoolMaxSize({})", scheduledThreadPoolMaxSize);

      hasBeenUpdated = true;
      this.scheduledThreadPoolMaxSize = scheduledThreadPoolMaxSize;
   }

   public Integer getThreadPoolMaxSize() {
      logger.trace("getThreadPoolMaxSize()");

      return threadPoolMaxSize;
   }

   public void setThreadPoolMaxSize(final Integer threadPoolMaxSize) {
      logger.trace("setThreadPoolMaxSize({})", threadPoolMaxSize);

      hasBeenUpdated = true;
      this.threadPoolMaxSize = threadPoolMaxSize;
   }

   public String getGroupID() {
      return groupID;
   }

   public void setGroupID(String groupID) {
      hasBeenUpdated = true;
      this.groupID = groupID;
   }

   public Integer getInitialConnectAttempts() {
      return initialConnectAttempts;
   }

   public void setInitialConnectAttempts(Integer initialConnectAttempts) {
      hasBeenUpdated = true;
      this.initialConnectAttempts = initialConnectAttempts;
   }

   public Integer getInitialMessagePacketSize() {
      return initialMessagePacketSize;
   }

   public void setInitialMessagePacketSize(Integer initialMessagePacketSize) {
      hasBeenUpdated = true;
      this.initialMessagePacketSize = initialMessagePacketSize;
   }

   public String getJgroupsFile() {
      return jgroupsFile;
   }

   public void setJgroupsFile(String jgroupsFile) {
      this.jgroupsFile = jgroupsFile;
      hasBeenUpdated = true;
   }

   public String getJgroupsChannelName() {
      return jgroupsChannelName;
   }

   public void setJgroupsChannelName(String jgroupsChannelName) {
      this.jgroupsChannelName = jgroupsChannelName;
      hasBeenUpdated = true;
   }

   public String getProtocolManagerFactoryStr() {
      return protocolManagerFactoryStr;
   }

   public void setProtocolManagerFactoryStr(String protocolManagerFactoryStr) {
      this.protocolManagerFactoryStr = protocolManagerFactoryStr;
   }

   @Override
   @Deprecated(forRemoval = true)
   public String getDeserializationBlackList() {
      return deserializationDenyList;
   }

   @Override
   @Deprecated(forRemoval = true)
   public void setDeserializationBlackList(String deserializationDenyList) {
      this.deserializationDenyList = deserializationDenyList;
      hasBeenUpdated = true;
   }

   @Override
   @Deprecated(forRemoval = true)
   public String getDeserializationWhiteList() {
      return this.deserializationAllowList;
   }

   @Override
   @Deprecated(forRemoval = true)
   public void setDeserializationWhiteList(String deserializationAllowList) {
      this.deserializationAllowList = deserializationAllowList;
      hasBeenUpdated = true;
   }

   @Override
   public String getDeserializationDenyList() {
      return deserializationDenyList;
   }

   @Override
   public void setDeserializationDenyList(String deserializationDenyList) {
      this.deserializationDenyList = deserializationDenyList;
      hasBeenUpdated = true;
   }

   @Override
   public String getDeserializationAllowList() {
      return this.deserializationAllowList;
   }

   @Override
   public void setDeserializationAllowList(String deserializationAllowList) {
      this.deserializationAllowList = deserializationAllowList;
      hasBeenUpdated = true;
   }

   public boolean isHasBeenUpdated() {
      return hasBeenUpdated;
   }

   // This is here just for backward compatibility and not used
   public void setEnableSharedClientID(boolean enable) {
      this.enableSharedClientID = enable;
   }

   public boolean isEnableSharedClientID() {
      return Objects.requireNonNullElse(enableSharedClientID, false);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof ConnectionFactoryProperties other)) {
         return false;
      }

      return Objects.equals(autoGroup, other.autoGroup) &&
             Objects.equals(blockOnAcknowledge, other.blockOnAcknowledge) &&
             Objects.equals(blockOnDurableSend, other.blockOnDurableSend) &&
             Objects.equals(blockOnNonDurableSend, other.blockOnNonDurableSend) &&
             Objects.equals(cacheLargeMessagesClient, other.cacheLargeMessagesClient) &&
             Objects.equals(compressLargeMessage, other.compressLargeMessage) &&
             Objects.equals(ha, other.ha) &&
             Objects.equals(preAcknowledge, other.preAcknowledge) &&
             Objects.equals(callFailoverTimeout, other.callFailoverTimeout) &&
             Objects.equals(callTimeout, other.callTimeout) &&
             Objects.equals(clientFailureCheckPeriod, other.clientFailureCheckPeriod) &&
             Objects.equals(clientID, other.clientID) &&
             Objects.equals(confirmationWindowSize, other.confirmationWindowSize) &&
             Objects.equals(connectionLoadBalancingPolicyClassName, other.connectionLoadBalancingPolicyClassName) &&
             Objects.equals(connectionTTL, other.connectionTTL) &&
             Objects.equals(consumerMaxRate, other.consumerMaxRate) &&
             Objects.equals(consumerWindowSize, other.consumerWindowSize) &&
             Objects.equals(discoveryAddress, other.discoveryAddress) &&
             Objects.equals(discoveryInitialWaitTimeout, other.discoveryInitialWaitTimeout) &&
             Objects.equals(discoveryLocalBindAddress, other.discoveryLocalBindAddress) &&
             Objects.equals(discoveryPort, other.discoveryPort) &&
             Objects.equals(discoveryRefreshTimeout, other.discoveryRefreshTimeout) &&
             Objects.equals(dupsOKBatchSize, other.dupsOKBatchSize) &&
             Objects.equals(groupID, other.groupID) &&
             Objects.equals(initialConnectAttempts, other.initialConnectAttempts) &&
             Objects.equals(initialMessagePacketSize, other.initialMessagePacketSize) &&
             Objects.equals(jgroupsChannelName, other.jgroupsChannelName) &&
             Objects.equals(jgroupsFile, other.jgroupsFile) &&
             Objects.equals(maxRetryInterval, other.maxRetryInterval) &&
             Objects.equals(minLargeMessageSize, other.minLargeMessageSize) &&
             Objects.equals(producerMaxRate, other.producerMaxRate) &&
             Objects.equals(producerWindowSize, other.producerWindowSize) &&
             Objects.equals(protocolManagerFactoryStr, other.protocolManagerFactoryStr) &&
             Objects.equals(reconnectAttempts, other.reconnectAttempts) &&
             Objects.equals(retryInterval, other.retryInterval) &&
             Objects.equals(retryIntervalMultiplier, other.retryIntervalMultiplier) &&
             Objects.equals(scheduledThreadPoolMaxSize, other.scheduledThreadPoolMaxSize) &&
             Objects.equals(threadPoolMaxSize, other.threadPoolMaxSize) &&
             Objects.equals(transactionBatchSize, other.transactionBatchSize) &&
             Objects.equals(useGlobalPools, other.useGlobalPools) &&
             Objects.equals(connectorClassName, other.connectorClassName) &&
             Objects.equals(connectionParameters, other.connectionParameters) &&
             Objects.equals(deserializationDenyList, other.deserializationDenyList) &&
             Objects.equals(deserializationAllowList, other.deserializationAllowList) &&
             Objects.equals(enable1xPrefixes, other.enable1xPrefixes) &&
             Objects.equals(enableSharedClientID, other.enableSharedClientID);
   }

   @Override
   public int hashCode() {
      return Objects.hash(ha, connectionLoadBalancingPolicyClassName, jgroupsFile, jgroupsChannelName, discoveryAddress,
                          discoveryPort, discoveryLocalBindAddress, discoveryRefreshTimeout,
                          discoveryInitialWaitTimeout, clientID, dupsOKBatchSize, transactionBatchSize,
                          clientFailureCheckPeriod, connectionTTL, cacheLargeMessagesClient, callTimeout,
                          callFailoverTimeout, compressLargeMessage, consumerWindowSize, producerWindowSize,
                          protocolManagerFactoryStr, consumerMaxRate, confirmationWindowSize, producerMaxRate,
                          minLargeMessageSize, blockOnAcknowledge, blockOnNonDurableSend, blockOnDurableSend, autoGroup,
                          preAcknowledge, initialConnectAttempts, retryInterval, retryIntervalMultiplier,
                          maxRetryInterval, reconnectAttempts, useGlobalPools, initialMessagePacketSize,
                          scheduledThreadPoolMaxSize, threadPoolMaxSize, groupID, connectorClassName,
                          connectionParameters, deserializationDenyList, deserializationAllowList, enable1xPrefixes,
                          enableSharedClientID);
   }
}
