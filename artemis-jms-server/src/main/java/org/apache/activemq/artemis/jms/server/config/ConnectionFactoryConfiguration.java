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
package org.apache.activemq.artemis.jms.server.config;

import java.util.List;

import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.journal.EncodingSupport;

/**
 * A ConnectionFactoryConfiguration for {@link javax.jms.ConnectionFactory} objects.
 */
public interface ConnectionFactoryConfiguration extends EncodingSupport {

   boolean isPersisted();

   String getName();

   ConnectionFactoryConfiguration setName(String name);

   String[] getBindings();

   ConnectionFactoryConfiguration setBindings(String... bindings);

   String getDiscoveryGroupName();

   ConnectionFactoryConfiguration setDiscoveryGroupName(String discoveryGroupName);

   List<String> getConnectorNames();

   ConnectionFactoryConfiguration setConnectorNames(List<String> connectorNames);

   ConnectionFactoryConfiguration setConnectorNames(String... connectorNames);

   boolean isHA();

   ConnectionFactoryConfiguration setHA(boolean ha);

   String getClientID();

   ConnectionFactoryConfiguration setClientID(String clientID);

   long getClientFailureCheckPeriod();

   ConnectionFactoryConfiguration setClientFailureCheckPeriod(long clientFailureCheckPeriod);

   long getConnectionTTL();

   ConnectionFactoryConfiguration setConnectionTTL(long connectionTTL);

   long getCallTimeout();

   ConnectionFactoryConfiguration setCallTimeout(long callTimeout);

   long getCallFailoverTimeout();

   ConnectionFactoryConfiguration setCallFailoverTimeout(long callFailoverTimeout);

   boolean isCacheLargeMessagesClient();

   ConnectionFactoryConfiguration setCacheLargeMessagesClient(boolean cacheLargeMessagesClient);

   int getMinLargeMessageSize();

   ConnectionFactoryConfiguration setMinLargeMessageSize(int minLargeMessageSize);

   boolean isCompressLargeMessages();

   ConnectionFactoryConfiguration setCompressLargeMessages(boolean avoidLargeMessages);

   int getCompressionLevel();

   ConnectionFactoryConfiguration setCompressionLevel(int compressionLevel);

   int getConsumerWindowSize();

   ConnectionFactoryConfiguration setConsumerWindowSize(int consumerWindowSize);

   int getConsumerMaxRate();

   ConnectionFactoryConfiguration setConsumerMaxRate(int consumerMaxRate);

   int getConfirmationWindowSize();

   ConnectionFactoryConfiguration setConfirmationWindowSize(int confirmationWindowSize);

   int getProducerWindowSize();

   ConnectionFactoryConfiguration setProducerWindowSize(int producerWindowSize);

   int getProducerMaxRate();

   ConnectionFactoryConfiguration setProducerMaxRate(int producerMaxRate);

   boolean isBlockOnAcknowledge();

   ConnectionFactoryConfiguration setBlockOnAcknowledge(boolean blockOnAcknowledge);

   boolean isBlockOnDurableSend();

   ConnectionFactoryConfiguration setBlockOnDurableSend(boolean blockOnDurableSend);

   boolean isBlockOnNonDurableSend();

   ConnectionFactoryConfiguration setBlockOnNonDurableSend(boolean blockOnNonDurableSend);

   boolean isAutoGroup();

   ConnectionFactoryConfiguration setAutoGroup(boolean autoGroup);

   boolean isPreAcknowledge();

   ConnectionFactoryConfiguration setPreAcknowledge(boolean preAcknowledge);

   String getLoadBalancingPolicyClassName();

   ConnectionFactoryConfiguration setLoadBalancingPolicyClassName(String loadBalancingPolicyClassName);

   int getTransactionBatchSize();

   ConnectionFactoryConfiguration setTransactionBatchSize(int transactionBatchSize);

   int getDupsOKBatchSize();

   ConnectionFactoryConfiguration setDupsOKBatchSize(int dupsOKBatchSize);

   boolean isUseGlobalPools();

   ConnectionFactoryConfiguration setUseGlobalPools(boolean useGlobalPools);

   int getScheduledThreadPoolMaxSize();

   ConnectionFactoryConfiguration setScheduledThreadPoolMaxSize(int scheduledThreadPoolMaxSize);

   int getThreadPoolMaxSize();

   ConnectionFactoryConfiguration setThreadPoolMaxSize(int threadPoolMaxSize);

   long getRetryInterval();

   ConnectionFactoryConfiguration setRetryInterval(long retryInterval);

   double getRetryIntervalMultiplier();

   ConnectionFactoryConfiguration setRetryIntervalMultiplier(double retryIntervalMultiplier);

   long getMaxRetryInterval();

   ConnectionFactoryConfiguration setMaxRetryInterval(long maxRetryInterval);

   int getReconnectAttempts();

   ConnectionFactoryConfiguration setReconnectAttempts(int reconnectAttempts);

   @Deprecated
   boolean isFailoverOnInitialConnection();

   @Deprecated
   ConnectionFactoryConfiguration setFailoverOnInitialConnection(boolean failover);

   String getGroupID();

   ConnectionFactoryConfiguration setGroupID(String groupID);

   ConnectionFactoryConfiguration setFactoryType(JMSFactoryType factType);

   ConnectionFactoryConfiguration setProtocolManagerFactoryStr(String protocolManagerFactoryStr);

   String getProtocolManagerFactoryStr();

   JMSFactoryType getFactoryType();

   @Deprecated(forRemoval = true)
   String getDeserializationBlackList();

   @Deprecated(forRemoval = true)
   void setDeserializationBlackList(String denyList);

   @Deprecated(forRemoval = true)
   String getDeserializationWhiteList();

   @Deprecated(forRemoval = true)
   void setDeserializationWhiteList(String allowList);

   String getDeserializationDenyList();

   void setDeserializationDenyList(String denyList);

   String getDeserializationAllowList();

   void setDeserializationAllowList(String allowList);

   int getInitialMessagePacketSize();

   ConnectionFactoryConfiguration setInitialMessagePacketSize(int size);

   boolean isEnable1xPrefixes();

   ConnectionFactoryConfiguration setEnable1xPrefixes(boolean enable1xPrefixes);

   boolean isEnableSharedClientID();

   ConnectionFactoryConfiguration setEnableSharedClientID(boolean enabled);

   boolean getUseTopologyForLoadBalancing();

   ConnectionFactoryConfiguration setUseTopologyForLoadBalancing(boolean useTopologyForLoadBalancing);
}
