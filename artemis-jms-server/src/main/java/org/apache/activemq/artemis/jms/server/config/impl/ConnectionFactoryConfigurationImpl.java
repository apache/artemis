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
package org.apache.activemq.artemis.jms.server.config.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * This class contains the configuration properties of a connection factory.
 * <p>
 * It is also persisted on the journal at the time of management is used to created a connection factory and set to store.
 * <p>
 * Every property on this class has to be also set through encoders through EncodingSupport implementation at this class.
 */
public class ConnectionFactoryConfigurationImpl implements ConnectionFactoryConfiguration {


   private String name = null;

   private boolean persisted = false;

   private String[] bindings = null;

   private List<String> connectorNames = null;

   private String discoveryGroupName = null;

   private String clientID = null;

   private boolean ha = ActiveMQClient.DEFAULT_HA;

   private long clientFailureCheckPeriod = ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD;

   private long connectionTTL = ActiveMQClient.DEFAULT_CONNECTION_TTL;

   private long callTimeout = ActiveMQClient.DEFAULT_CALL_TIMEOUT;

   private long callFailoverTimeout = ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT;

   private boolean cacheLargeMessagesClient = ActiveMQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT;

   private int minLargeMessageSize = ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;

   private boolean compressLargeMessage = ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES;

   private int consumerWindowSize = ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE;

   private int consumerMaxRate = ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE;

   private int confirmationWindowSize = ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE;

   private int producerWindowSize = ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE;

   private int producerMaxRate = ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE;

   private boolean blockOnAcknowledge = ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE;

   private boolean blockOnDurableSend = ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND;

   private boolean blockOnNonDurableSend = ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND;

   private boolean autoGroup = ActiveMQClient.DEFAULT_AUTO_GROUP;

   private boolean preAcknowledge = ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE;

   private String loadBalancingPolicyClassName = ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME;

   private int transactionBatchSize = ActiveMQClient.DEFAULT_ACK_BATCH_SIZE;

   private int dupsOKBatchSize = ActiveMQClient.DEFAULT_ACK_BATCH_SIZE;

   private long initialWaitTimeout = ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT;

   private boolean useGlobalPools = ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS;

   private int scheduledThreadPoolMaxSize = ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE;

   private int threadPoolMaxSize = ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE;

   private long retryInterval = ActiveMQClient.DEFAULT_RETRY_INTERVAL;

   private double retryIntervalMultiplier = ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER;

   private long maxRetryInterval = ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL;

   private int reconnectAttempts = ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS;

   private String groupID = null;

   private String protocolManagerFactoryStr;

   private JMSFactoryType factoryType = JMSFactoryType.CF;

   private String deserializationBlackList;

   private String deserializationWhiteList;

   private int initialMessagePacketSize = ActiveMQClient.DEFAULT_INITIAL_MESSAGE_PACKET_SIZE;

   private boolean enable1xPrefixes = ActiveMQJMSClient.DEFAULT_ENABLE_1X_PREFIXES;

   private boolean enableSharedClientID = ActiveMQClient.DEFAULT_ENABLED_SHARED_CLIENT_ID;

   private boolean useTopologyForLoadBalancing = ActiveMQClient.DEFAULT_USE_TOPOLOGY_FOR_LOADBALANCING;


   public ConnectionFactoryConfigurationImpl() {
   }

   // ConnectionFactoryConfiguration implementation -----------------

   @Override
   public String[] getBindings() {
      return bindings;
   }

   @Override
   public ConnectionFactoryConfiguration setBindings(final String... bindings) {
      this.bindings = bindings;
      return this;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public ConnectionFactoryConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   @Override
   public boolean isPersisted() {
      return persisted;
   }

   /**
    * @return the discoveryGroupName
    */
   @Override
   public String getDiscoveryGroupName() {
      return discoveryGroupName;
   }

   /**
    * @param discoveryGroupName the discoveryGroupName to set
    */
   @Override
   public ConnectionFactoryConfiguration setDiscoveryGroupName(String discoveryGroupName) {
      this.discoveryGroupName = discoveryGroupName;
      return this;
   }

   @Override
   public List<String> getConnectorNames() {
      return connectorNames;
   }

   @Override
   public ConnectionFactoryConfiguration setConnectorNames(final List<String> connectorNames) {
      this.connectorNames = connectorNames;
      return this;
   }

   @Override
   public ConnectionFactoryConfiguration setConnectorNames(final String... names) {
      return this.setConnectorNames(Arrays.asList(names));
   }

   @Override
   public boolean isHA() {
      return ha;
   }

   @Override
   public ConnectionFactoryConfiguration setHA(final boolean ha) {
      this.ha = ha;
      return this;
   }

   @Override
   public String getClientID() {
      return clientID;
   }

   @Override
   public ConnectionFactoryConfiguration setClientID(final String clientID) {
      this.clientID = clientID;
      return this;
   }

   @Override
   public long getClientFailureCheckPeriod() {
      return clientFailureCheckPeriod;
   }

   @Override
   public ConnectionFactoryConfiguration setClientFailureCheckPeriod(final long clientFailureCheckPeriod) {
      this.clientFailureCheckPeriod = clientFailureCheckPeriod;
      return this;
   }

   @Override
   public long getConnectionTTL() {
      return connectionTTL;
   }

   @Override
   public ConnectionFactoryConfiguration setConnectionTTL(final long connectionTTL) {
      this.connectionTTL = connectionTTL;
      return this;
   }

   @Override
   public long getCallTimeout() {
      return callTimeout;
   }

   @Override
   public ConnectionFactoryConfiguration setCallTimeout(final long callTimeout) {
      this.callTimeout = callTimeout;
      return this;
   }

   @Override
   public long getCallFailoverTimeout() {
      return callFailoverTimeout;
   }

   @Override
   public ConnectionFactoryConfiguration setCallFailoverTimeout(long callFailoverTimeout) {
      this.callFailoverTimeout = callFailoverTimeout;
      return this;
   }

   @Override
   public boolean isCacheLargeMessagesClient() {
      return cacheLargeMessagesClient;
   }

   @Override
   public ConnectionFactoryConfiguration setCacheLargeMessagesClient(final boolean cacheLargeMessagesClient) {
      this.cacheLargeMessagesClient = cacheLargeMessagesClient;
      return this;
   }

   @Override
   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   @Override
   public ConnectionFactoryConfiguration setMinLargeMessageSize(final int minLargeMessageSize) {
      this.minLargeMessageSize = minLargeMessageSize;
      return this;
   }

   @Override
   public int getConsumerWindowSize() {
      return consumerWindowSize;
   }

   @Override
   public ConnectionFactoryConfiguration setConsumerWindowSize(final int consumerWindowSize) {
      this.consumerWindowSize = consumerWindowSize;
      return this;
   }

   @Override
   public int getConsumerMaxRate() {
      return consumerMaxRate;
   }

   @Override
   public ConnectionFactoryConfiguration setConsumerMaxRate(final int consumerMaxRate) {
      this.consumerMaxRate = consumerMaxRate;
      return this;
   }

   @Override
   public int getConfirmationWindowSize() {
      return confirmationWindowSize;
   }

   @Override
   public ConnectionFactoryConfiguration setConfirmationWindowSize(final int confirmationWindowSize) {
      this.confirmationWindowSize = confirmationWindowSize;
      return this;
   }

   @Override
   public int getProducerMaxRate() {
      return producerMaxRate;
   }

   @Override
   public ConnectionFactoryConfiguration setProducerMaxRate(final int producerMaxRate) {
      this.producerMaxRate = producerMaxRate;
      return this;
   }

   @Override
   public int getProducerWindowSize() {
      return producerWindowSize;
   }

   @Override
   public ConnectionFactoryConfiguration setProducerWindowSize(final int producerWindowSize) {
      this.producerWindowSize = producerWindowSize;
      return this;
   }

   @Override
   public boolean isBlockOnAcknowledge() {
      return blockOnAcknowledge;
   }

   @Override
   public ConnectionFactoryConfiguration setBlockOnAcknowledge(final boolean blockOnAcknowledge) {
      this.blockOnAcknowledge = blockOnAcknowledge;
      return this;
   }

   @Override
   public boolean isBlockOnDurableSend() {
      return blockOnDurableSend;
   }

   @Override
   public ConnectionFactoryConfiguration setBlockOnDurableSend(final boolean blockOnDurableSend) {
      this.blockOnDurableSend = blockOnDurableSend;
      return this;
   }

   @Override
   public boolean isBlockOnNonDurableSend() {
      return blockOnNonDurableSend;
   }

   @Override
   public ConnectionFactoryConfiguration setBlockOnNonDurableSend(final boolean blockOnNonDurableSend) {
      this.blockOnNonDurableSend = blockOnNonDurableSend;
      return this;
   }

   @Override
   public boolean isAutoGroup() {
      return autoGroup;
   }

   @Override
   public ConnectionFactoryConfiguration setAutoGroup(final boolean autoGroup) {
      this.autoGroup = autoGroup;
      return this;
   }

   @Override
   public boolean isPreAcknowledge() {
      return preAcknowledge;
   }

   @Override
   public ConnectionFactoryConfiguration setPreAcknowledge(final boolean preAcknowledge) {
      this.preAcknowledge = preAcknowledge;
      return this;
   }

   @Override
   public String getLoadBalancingPolicyClassName() {
      return loadBalancingPolicyClassName;
   }

   @Override
   public ConnectionFactoryConfiguration setLoadBalancingPolicyClassName(final String loadBalancingPolicyClassName) {
      this.loadBalancingPolicyClassName = loadBalancingPolicyClassName;
      return this;
   }

   @Override
   public int getTransactionBatchSize() {
      return transactionBatchSize;
   }

   @Override
   public ConnectionFactoryConfiguration setTransactionBatchSize(final int transactionBatchSize) {
      this.transactionBatchSize = transactionBatchSize;
      return this;
   }

   @Override
   public int getDupsOKBatchSize() {
      return dupsOKBatchSize;
   }

   @Override
   public ConnectionFactoryConfiguration setDupsOKBatchSize(final int dupsOKBatchSize) {
      this.dupsOKBatchSize = dupsOKBatchSize;
      return this;
   }

   public long getInitialWaitTimeout() {
      return initialWaitTimeout;
   }

   public ConnectionFactoryConfiguration setInitialWaitTimeout(final long initialWaitTimeout) {
      this.initialWaitTimeout = initialWaitTimeout;
      return this;
   }

   @Override
   public boolean isUseGlobalPools() {
      return useGlobalPools;
   }

   @Override
   public ConnectionFactoryConfiguration setUseGlobalPools(final boolean useGlobalPools) {
      this.useGlobalPools = useGlobalPools;
      return this;
   }

   @Override
   public int getScheduledThreadPoolMaxSize() {
      return scheduledThreadPoolMaxSize;
   }

   @Override
   public ConnectionFactoryConfiguration setScheduledThreadPoolMaxSize(final int scheduledThreadPoolMaxSize) {
      this.scheduledThreadPoolMaxSize = scheduledThreadPoolMaxSize;
      return this;
   }

   @Override
   public int getThreadPoolMaxSize() {
      return threadPoolMaxSize;
   }

   @Override
   public ConnectionFactoryConfiguration setThreadPoolMaxSize(final int threadPoolMaxSize) {
      this.threadPoolMaxSize = threadPoolMaxSize;
      return this;
   }

   @Override
   public long getRetryInterval() {
      return retryInterval;
   }

   @Override
   public ConnectionFactoryConfiguration setRetryInterval(final long retryInterval) {
      this.retryInterval = retryInterval;
      return this;
   }

   @Override
   public double getRetryIntervalMultiplier() {
      return retryIntervalMultiplier;
   }

   @Override
   public ConnectionFactoryConfiguration setRetryIntervalMultiplier(final double retryIntervalMultiplier) {
      this.retryIntervalMultiplier = retryIntervalMultiplier;
      return this;
   }

   @Override
   public long getMaxRetryInterval() {
      return maxRetryInterval;
   }

   @Override
   public ConnectionFactoryConfiguration setMaxRetryInterval(final long maxRetryInterval) {
      this.maxRetryInterval = maxRetryInterval;
      return this;
   }

   @Override
   public int getReconnectAttempts() {
      return reconnectAttempts;
   }

   @Override
   public ConnectionFactoryConfiguration setReconnectAttempts(final int reconnectAttempts) {
      this.reconnectAttempts = reconnectAttempts;
      return this;
   }

   @Deprecated
   @Override
   public boolean isFailoverOnInitialConnection() {
      return false;
   }

   @Deprecated
   @Override
   public ConnectionFactoryConfiguration setFailoverOnInitialConnection(final boolean failover) {
      return this;
   }

   @Override
   public String getGroupID() {
      return groupID;
   }

   @Override
   public ConnectionFactoryConfiguration setGroupID(final String groupID) {
      this.groupID = groupID;
      return this;
   }

   @Override
   public boolean isEnable1xPrefixes() {
      return enable1xPrefixes;
   }

   @Override
   public ConnectionFactoryConfiguration setEnable1xPrefixes(final boolean enable1xPrefixes) {
      this.enable1xPrefixes = enable1xPrefixes;
      return this;
   }

   // Encoding Support Implementation --------------------------------------------------------------

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      persisted = true;

      name = buffer.readSimpleString().toString();

      discoveryGroupName = BufferHelper.readNullableSimpleStringAsString(buffer);

      int nConnectors = buffer.readInt();

      if (nConnectors > 0) {
         connectorNames = new ArrayList<>(nConnectors);

         for (int i = 0; i < nConnectors; i++) {
            SimpleString str = buffer.readSimpleString();

            connectorNames.add(str.toString());
         }
      }

      ha = buffer.readBoolean();

      clientID = BufferHelper.readNullableSimpleStringAsString(buffer);

      clientFailureCheckPeriod = buffer.readLong();

      connectionTTL = buffer.readLong();

      callTimeout = buffer.readLong();

      cacheLargeMessagesClient = buffer.readBoolean();

      minLargeMessageSize = buffer.readInt();

      consumerWindowSize = buffer.readInt();

      consumerMaxRate = buffer.readInt();

      confirmationWindowSize = buffer.readInt();

      producerWindowSize = buffer.readInt();

      producerMaxRate = buffer.readInt();

      blockOnAcknowledge = buffer.readBoolean();

      blockOnDurableSend = buffer.readBoolean();

      blockOnNonDurableSend = buffer.readBoolean();

      autoGroup = buffer.readBoolean();

      preAcknowledge = buffer.readBoolean();

      loadBalancingPolicyClassName = buffer.readSimpleString().toString();

      transactionBatchSize = buffer.readInt();

      dupsOKBatchSize = buffer.readInt();

      initialWaitTimeout = buffer.readLong();

      useGlobalPools = buffer.readBoolean();

      scheduledThreadPoolMaxSize = buffer.readInt();

      threadPoolMaxSize = buffer.readInt();

      retryInterval = buffer.readLong();

      retryIntervalMultiplier = buffer.readDouble();

      maxRetryInterval = buffer.readLong();

      reconnectAttempts = buffer.readInt();

      buffer.readBoolean();
      // failoverOnInitialConnection

      compressLargeMessage = buffer.readBoolean();

      groupID = BufferHelper.readNullableSimpleStringAsString(buffer);

      factoryType = JMSFactoryType.valueOf(buffer.readInt());

      protocolManagerFactoryStr = BufferHelper.readNullableSimpleStringAsString(buffer);

      deserializationBlackList = BufferHelper.readNullableSimpleStringAsString(buffer);

      deserializationWhiteList = BufferHelper.readNullableSimpleStringAsString(buffer);

      enable1xPrefixes = buffer.readableBytes() > 0 ? buffer.readBoolean() : ActiveMQJMSClient.DEFAULT_ENABLE_1X_PREFIXES;

      enableSharedClientID = buffer.readableBytes() > 0 ? BufferHelper.readNullableBoolean(buffer) : ActiveMQClient.DEFAULT_ENABLED_SHARED_CLIENT_ID;

      useTopologyForLoadBalancing = buffer.readableBytes() > 0 ? BufferHelper.readNullableBoolean(buffer) : ActiveMQClient.DEFAULT_USE_TOPOLOGY_FOR_LOADBALANCING;
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      persisted = true;

      BufferHelper.writeAsSimpleString(buffer, name);

      BufferHelper.writeAsNullableSimpleString(buffer, discoveryGroupName);

      if (this.connectorNames == null) {
         buffer.writeInt(0);
      } else {
         buffer.writeInt(connectorNames.size());

         for (String tc : connectorNames) {
            BufferHelper.writeAsSimpleString(buffer, tc);
         }
      }

      buffer.writeBoolean(ha);

      BufferHelper.writeAsNullableSimpleString(buffer, clientID);

      buffer.writeLong(clientFailureCheckPeriod);

      buffer.writeLong(connectionTTL);

      buffer.writeLong(callTimeout);

      buffer.writeBoolean(cacheLargeMessagesClient);

      buffer.writeInt(minLargeMessageSize);

      buffer.writeInt(consumerWindowSize);

      buffer.writeInt(consumerMaxRate);

      buffer.writeInt(confirmationWindowSize);

      buffer.writeInt(producerWindowSize);

      buffer.writeInt(producerMaxRate);

      buffer.writeBoolean(blockOnAcknowledge);

      buffer.writeBoolean(blockOnDurableSend);

      buffer.writeBoolean(blockOnNonDurableSend);

      buffer.writeBoolean(autoGroup);

      buffer.writeBoolean(preAcknowledge);

      BufferHelper.writeAsSimpleString(buffer, loadBalancingPolicyClassName);

      buffer.writeInt(transactionBatchSize);

      buffer.writeInt(dupsOKBatchSize);

      buffer.writeLong(initialWaitTimeout);

      buffer.writeBoolean(useGlobalPools);

      buffer.writeInt(scheduledThreadPoolMaxSize);

      buffer.writeInt(threadPoolMaxSize);

      buffer.writeLong(retryInterval);

      buffer.writeDouble(retryIntervalMultiplier);

      buffer.writeLong(maxRetryInterval);

      buffer.writeInt(reconnectAttempts);

      buffer.writeBoolean(false);
      // failoverOnInitialConnection

      buffer.writeBoolean(compressLargeMessage);

      BufferHelper.writeAsNullableSimpleString(buffer, groupID);

      buffer.writeInt(factoryType.intValue());

      BufferHelper.writeAsNullableSimpleString(buffer, protocolManagerFactoryStr);

      BufferHelper.writeAsNullableSimpleString(buffer, deserializationBlackList);

      BufferHelper.writeAsNullableSimpleString(buffer, deserializationWhiteList);

      buffer.writeBoolean(enable1xPrefixes);

      BufferHelper.writeNullableBoolean(buffer, enableSharedClientID);

      BufferHelper.writeNullableBoolean(buffer, useTopologyForLoadBalancing);
   }

   @Override
   public int getEncodeSize() {
      int size = BufferHelper.sizeOfSimpleString(name) +

         BufferHelper.sizeOfNullableSimpleString(discoveryGroupName);

      size += DataConstants.SIZE_INT;

      if (this.connectorNames != null) {
         for (String tc : connectorNames) {
            size += BufferHelper.sizeOfSimpleString(tc);
         }
      }

      size += BufferHelper.sizeOfNullableSimpleString(clientID) +

         DataConstants.SIZE_BOOLEAN +
         // ha

         DataConstants.SIZE_LONG +
         // clientFailureCheckPeriod

         DataConstants.SIZE_LONG +
         // connectionTTL

         DataConstants.SIZE_LONG +
         // callTimeout

         DataConstants.SIZE_BOOLEAN +
         // cacheLargeMessagesClient

         DataConstants.SIZE_INT +
         // minLargeMessageSize

         DataConstants.SIZE_INT +
         // consumerWindowSize

         DataConstants.SIZE_INT +
         // consumerMaxRate

         DataConstants.SIZE_INT +
         // confirmationWindowSize

         DataConstants.SIZE_INT +
         // producerWindowSize

         DataConstants.SIZE_INT +
         // producerMaxRate

         DataConstants.SIZE_BOOLEAN +
         // blockOnAcknowledge

         DataConstants.SIZE_BOOLEAN +
         // blockOnDurableSend

         DataConstants.SIZE_BOOLEAN +
         // blockOnNonDurableSend

         DataConstants.SIZE_BOOLEAN +
         // autoGroup

         DataConstants.SIZE_BOOLEAN +
         // preAcknowledge

         BufferHelper.sizeOfSimpleString(loadBalancingPolicyClassName) +

         DataConstants.SIZE_INT +
         // transactionBatchSize

         DataConstants.SIZE_INT +
         // dupsOKBatchSize

         DataConstants.SIZE_LONG +
         // initialWaitTimeout

         DataConstants.SIZE_BOOLEAN +
         // useGlobalPools

         DataConstants.SIZE_INT +
         // scheduledThreadPoolMaxSize

         DataConstants.SIZE_INT +
         // threadPoolMaxSize

         DataConstants.SIZE_LONG +
         // retryInterval

         DataConstants.SIZE_DOUBLE +
         // retryIntervalMultiplier

         DataConstants.SIZE_LONG +
         // maxRetryInterval

         DataConstants.SIZE_INT +
         // reconnectAttempts

         DataConstants.SIZE_BOOLEAN +
         // failoverOnInitialConnection

         DataConstants.SIZE_BOOLEAN +
         // compress-large-message

         BufferHelper.sizeOfNullableSimpleString(groupID) +

         DataConstants.SIZE_INT +
         // factoryType

         BufferHelper.sizeOfNullableSimpleString(protocolManagerFactoryStr) +

         BufferHelper.sizeOfNullableSimpleString(deserializationBlackList) +

         BufferHelper.sizeOfNullableSimpleString(deserializationWhiteList) +

         DataConstants.SIZE_BOOLEAN +
         // enable1xPrefixes;

         BufferHelper.sizeOfNullableBoolean(enableSharedClientID) +

         BufferHelper.sizeOfNullableBoolean(useTopologyForLoadBalancing);

      return size;
   }

   @Override
   public ConnectionFactoryConfiguration setFactoryType(final JMSFactoryType factoryType) {
      this.factoryType = factoryType;
      return this;
   }

   @Override
   public JMSFactoryType getFactoryType() {
      return factoryType;
   }

   @Override
   public String getDeserializationBlackList() {
      return deserializationBlackList;
   }

   @Override
   public void setDeserializationBlackList(String blackList) {
      this.deserializationBlackList = blackList;
   }

   @Override
   public String getDeserializationWhiteList() {
      return this.deserializationWhiteList;
   }

   @Override
   public void setDeserializationWhiteList(String whiteList) {
      this.deserializationWhiteList = whiteList;
   }

   @Override
   public ConnectionFactoryConfiguration setCompressLargeMessages(boolean compressLargeMessage) {
      this.compressLargeMessage = compressLargeMessage;
      return this;
   }

   @Override
   public boolean isCompressLargeMessages() {
      return this.compressLargeMessage;
   }

   @Override
   public ConnectionFactoryConfiguration setProtocolManagerFactoryStr(String protocolManagerFactoryStr) {
      this.protocolManagerFactoryStr = protocolManagerFactoryStr;
      return this;
   }

   @Override
   public String getProtocolManagerFactoryStr() {
      return protocolManagerFactoryStr;
   }

   @Override
   public int getInitialMessagePacketSize() {
      return initialMessagePacketSize;
   }

   @Override
   public ConnectionFactoryConfiguration setInitialMessagePacketSize(int size) {
      this.initialMessagePacketSize = size;
      return this;
   }

   @Override
   public ConnectionFactoryConfiguration setEnableSharedClientID(boolean enabled) {
      this.enableSharedClientID = enabled;
      return this;
   }

   @Override
   public boolean isEnableSharedClientID() {
      return enableSharedClientID;
   }

   @Override
   public ConnectionFactoryConfiguration setUseTopologyForLoadBalancing(boolean useTopologyForLoadBalancing) {
      this.useTopologyForLoadBalancing = useTopologyForLoadBalancing;
      return this;
   }

   @Override
   public boolean getUseTopologyForLoadBalancing() {
      return useTopologyForLoadBalancing;
   }


}
