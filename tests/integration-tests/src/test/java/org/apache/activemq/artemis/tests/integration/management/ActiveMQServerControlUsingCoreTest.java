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
package org.apache.activemq.artemis.tests.integration.management;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.Parameter;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

// Parameters set by super class
@ExtendWith(ParameterizedTestExtension.class)
public class ActiveMQServerControlUsingCoreTest extends ActiveMQServerControlTest {


   public ActiveMQServerControlUsingCoreTest(boolean legacyCreateQueue) {
      super(legacyCreateQueue);
      extraProducers = 1;
   }


   @Disabled
   @Override
   @TestTemplate
   public void testListConnectionsCoreClientID() throws Exception {
      // noise from the connector itself could affect test results,
      // hence this test is being disabled
   }


   @Disabled
   @Override
   @TestTemplate
   public void testListProducersAgainstServer() throws Exception {
      // testListProducersAgainstServer is measuring the number of producers in the server
      // however the management controller itself will include producers
      // what will introduce noise to the test
      // hence this test needs to be ignored when using the core protocol for management
   }

   @Disabled
   @Override
   @TestTemplate
   public void testListProducersMessageCounts() throws Exception {
      // invalid test when using core protocol (noise from itself)
   }

   @Disabled
   @Override
   @TestTemplate
   public void testListAllConsumersAsJSON() throws Exception {
      // invalid test when using core protocol (noise from itself)
   }

   @Disabled
   @Override
   @TestTemplate
   public void testListSessions() throws Exception {
      // similarly to testListProducersAgainstServer test,
      // this test will have different objects created when running over core,
      // what may introduce noise to the test
      // for that reason this test is ignored on the UsingCoreTest
   }

   @Disabled
   @Override
   @TestTemplate
   public void testScaleDownWithOutConnector() throws Exception {
      // test would be invalid over core protocol
   }

   @Disabled
   @Override
   @TestTemplate
   public void testScaleDownWithConnector() throws Exception {
      // test would be invalid over core protocol
   }

   @Disabled
   @Override
   @TestTemplate
   public void testRestartEmbeddedWebServerException() throws Exception {
      // test would be invalid over core protocol
   }

   @Disabled
   @Override
   @TestTemplate
   public void testRestartEmbeddedWebServerTimeout() throws Exception {
      // test would be invalid over core protocol
   }

   @Disabled
   @Override
   @TestTemplate
   public void testListProducersMessageCountsJMSCore() throws Exception {
      // test would be invalid over core protocol
   }

   @Override
   protected ActiveMQServerControl createManagementControl() throws Exception {
      return new ActiveMQServerControl() {
         @Override
         public String listBrokerConnections() {
            try {
               return (String) proxy.invokeOperation("listBrokerConnections");
            } catch (Throwable throwable) {
               throwable.printStackTrace();
               return null;
            }
         }

         @Override
         public long getCurrentTimeMillis() {
            return (long)proxy.retrieveAttributeValue("currentTimeMillis", long.class);
         }

         @Override
         public void startBrokerConnection(String name) throws Exception {
            proxy.invokeOperation("startBrokerConnection", name);
         }

         @Override
         public void stopBrokerConnection(String name) throws Exception {
            proxy.invokeOperation("stopBrokerConnection", name);
         }

         @Override
         public String updateAddress(String name, String routingTypes) throws Exception {
            return (String) proxy.invokeOperation("updateAddress", name, routingTypes);
         }

         public void updateDuplicateIdCache(String address, Object[] ids) {

         }

         @Override
         public void scaleDown(String connector) throws Exception {
            throw new UnsupportedOperationException();
         }

         private final CoreMessagingProxy proxy = new CoreMessagingProxy(addServerLocator(createInVMNonHALocator()), ResourceNames.BROKER);

         @Override
         public boolean isSharedStore() {
            return (Boolean) proxy.retrieveAttributeValue("sharedStore");
         }

         @Override
         public boolean closeConnectionsForAddress(final String ipAddress) throws Exception {
            return (Boolean) proxy.invokeOperation("closeConnectionsForAddress", ipAddress);
         }

         @Override
         public boolean closeConsumerConnectionsForAddress(final String address) throws Exception {
            return (Boolean) proxy.invokeOperation("closeConsumerConnectionsForAddress", address);
         }

         @Override
         public boolean closeConnectionsForUser(final String userName) throws Exception {
            return (Boolean) proxy.invokeOperation("closeConnectionsForUser", userName);
         }

         @Override
         public boolean closeConnectionWithID(String ID) throws Exception {
            return (Boolean) proxy.invokeOperation("closeConnectionWithID", ID);
         }

         @Override
         public boolean closeSessionWithID(String connectionID, String ID) throws Exception {
            return (Boolean) proxy.invokeOperation("closeSessionWithID", connectionID, ID);
         }

         @Override
         public boolean closeSessionWithID(String connectionID, String ID, boolean force) throws Exception {
            return (Boolean) proxy.invokeOperation("closeSessionWithID", connectionID, ID, force);
         }

         @Override
         public boolean closeConsumerWithID(String sessionID, String ID) throws Exception {
            return (Boolean) proxy.invokeOperation("closeConsumerWithID", sessionID, ID);
         }

         @Override
         public boolean commitPreparedTransaction(final String transactionAsBase64) throws Exception {
            return (Boolean) proxy.invokeOperation("commitPreparedTransaction", transactionAsBase64);
         }

         @Override
         public void createQueue(final String address, final String name) throws Exception {
            proxy.invokeOperation("createQueue", address, name);
         }

         @Override
         public boolean isActive() {
            return (Boolean) proxy.retrieveAttributeValue("active");
         }

         @Override
         public String createQueue(String address,
                                   String routingType,
                                   String name,
                                   String filterStr,
                                   boolean durable,
                                   int maxConsumers,
                                   boolean purgeOnNoConsumers,
                                   boolean autoCreateAddress) throws Exception {
            return (String) proxy.invokeOperation("createQueue", address, routingType, name, filterStr, durable, maxConsumers, purgeOnNoConsumers, autoCreateAddress);
         }

         @Override
         public String createQueue(String queueConfiguration) throws Exception {
            return (String) proxy.invokeOperation("createQueue", queueConfiguration);
         }

         @Override
         public String createQueue(String queueConfiguration, boolean ignoreIfExists) throws Exception {
            return (String) proxy.invokeOperation("createQueue", queueConfiguration, ignoreIfExists);
         }

         @Override
         public String updateQueue(String queueConfiguration) throws Exception {
            return (String) proxy.invokeOperation("updateQueue", queueConfiguration);
         }

         @Override
         public String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                                   @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                                   @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                                   @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers) throws Exception {
            return (String) proxy.invokeOperation("updateQueue", name, routingType, maxConsumers, purgeOnNoConsumers);
         }

         @Override
         public String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                                   @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                                   @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                                   @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                                   @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive)
            throws Exception {
            return (String) proxy.invokeOperation("updateQueue", name, routingType, maxConsumers, purgeOnNoConsumers, exclusive);
         }

         @Override
         public String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                                   @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                                   @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                                   @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                                   @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive,
                                   @Parameter(name = "user", desc = "The user associated with this queue") String user)
            throws Exception {
            return (String) proxy.invokeOperation("updateQueue", name, routingType, maxConsumers, purgeOnNoConsumers, exclusive, user);
         }

         @Override
         public String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                                   @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                                   @Parameter(name = "filter", desc = "The filter to use on the queue") String filter,
                                   @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                                   @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                                   @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive,
                                   @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") Boolean groupRebalance,
                                   @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") Integer groupBuckets,
                                   @Parameter(name = "nonDestructive", desc = "If the queue should be nonDestructive") Boolean nonDestructive,
                                   @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") Integer consumersBeforeDispatch,
                                   @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") Long delayBeforeDispatch,
                                   @Parameter(name = "user", desc = "The user associated with this queue") String user) throws Exception {
            return (String) proxy.invokeOperation("updateQueue", name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user);
         }

         @Override
         public String updateQueue(String name, String routingType, String filter, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, String groupFirstKey, Boolean nonDestructive, Integer consumersBeforeDispatch, Long delayBeforeDispatch, String user) throws Exception {
            return (String) proxy.invokeOperation("updateQueue", name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user);
         }

         @Override
         public String updateQueue(String name, String routingType, String filter, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, String groupFirstKey, Boolean nonDestructive, Integer consumersBeforeDispatch, Long delayBeforeDispatch, String user, Long ringSize) throws Exception {
            return (String) proxy.invokeOperation("updateQueue", name, routingType, filter, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, ringSize);
         }

         @Override
         public void deleteAddress(@Parameter(name = "name", desc = "The name of the address") String name) throws Exception {
            proxy.invokeOperation("deleteAddress", name);
         }

         @Override
         public void deleteAddress(@Parameter(name = "name", desc = "The name of the address") String name, @Parameter(name = "force", desc = "Force everything out!") boolean force) throws Exception {
            proxy.invokeOperation("deleteAddress", name, force);
         }

         @Override
         public void createQueue(final String address,
                                 final String name,
                                 final String filter,
                                 final boolean durable) throws Exception {
            proxy.invokeOperation("createQueue", address, name, filter, durable);
         }

         @Override
         public void createQueue(String address, String name, String routingType) throws Exception {
            proxy.invokeOperation("createQueue", address, name, routingType);
         }

         @Override
         public void createQueue(String address, String name, boolean durable, String routingType) throws Exception {
            proxy.invokeOperation("createQueue", address, name, durable, routingType);
         }

         @Override
         public void createQueue(String address, String name, String filter, boolean durable, String routingType) throws Exception {
            proxy.invokeOperation("createQueue", address, name, filter, durable, routingType);
         }

         @Override
         public String createQueue(String address, String routingType, String name, String filter, boolean durable, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets, boolean lastValue, String lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoCreateAddress) throws Exception {
            return (String) proxy.invokeOperation("createQueue", address, routingType, name, filter, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoCreateAddress);
         }

         @Override
         public String createQueue(String address, String routingType, String name, String filter, boolean durable, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets, boolean lastValue, String lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception {
            return (String) proxy.invokeOperation("createQueue", address, routingType, name, filter, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress);
         }

         @Override
         public String createQueue(String address, String routingType, String name, String filter, boolean durable, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets, String groupFirstKey, boolean lastValue, String lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception {
            return (String) proxy.invokeOperation("createQueue", address, routingType, name, filter, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress);
         }

         @Override
         public String createQueue(String address, String routingType, String name, String filter, boolean durable, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets, String groupFirstKey, boolean lastValue, String lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress, long ringSize) throws Exception {
            return (String) proxy.invokeOperation("createQueue", address, routingType, name, filter, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, ringSize);
         }

         @Override
         public void createQueue(final String address, final String name, final boolean durable) throws Exception {
            proxy.invokeOperation("createQueue", address, name, durable);
         }


         @Override
         public void deployQueue(final String address,
                                 final String name,
                                 final String filter,
                                 final boolean durable) throws Exception {
            proxy.invokeOperation("deployQueue", address, name, filter, durable);
         }

         @Override
         public void deployQueue(final String address, final String name, final String filterString) throws Exception {
            proxy.invokeOperation("deployQueue", address, name);
         }

         @Override
         public void destroyQueue(final String name) throws Exception {
            proxy.invokeOperation("destroyQueue", name);
         }

         @Override
         public void destroyQueue(final String name, final boolean removeConsumers) throws Exception {
            proxy.invokeOperation("destroyQueue", name, removeConsumers);
         }

         @Override
         public void destroyQueue(String name,
                                  boolean removeConsumers,
                                  boolean autoDeleteAddress) throws Exception {
            proxy.invokeOperation("destroyQueue", name, removeConsumers, autoDeleteAddress);
         }

         @Override
         public void disableMessageCounters() throws Exception {
            proxy.invokeOperation("disableMessageCounters");
         }

         @Override
         public void enableMessageCounters() throws Exception {
            proxy.invokeOperation("enableMessageCounters");
         }

         @Override
         public String getBindingsDirectory() {
            return (String) proxy.retrieveAttributeValue("bindingsDirectory");
         }

         @Override
         public int getConnectionCount() {
            return (Integer) proxy.retrieveAttributeValue("connectionCount", Integer.class);
         }

         @Override
         public long getTotalConnectionCount() {
            return (Long) proxy.retrieveAttributeValue("totalConnectionCount", Long.class);
         }

         @Override
         public int getSessionCount() {
            return (Integer) proxy.retrieveAttributeValue("sessionCount", Integer.class);
         }

         @Override
         public long getTotalSessionCount() {
            return (Long) proxy.retrieveAttributeValue("totalSessionCount", Long.class);
         }

         @Override
         public long getTotalMessageCount() {
            return (Long) proxy.retrieveAttributeValue("totalMessageCount", Long.class);
         }

         @Override
         public long getTotalMessagesAdded() {
            return (Long) proxy.retrieveAttributeValue("totalMessagesAdded", Long.class);
         }

         @Override
         public long getTotalMessagesAcknowledged() {
            return (Long) proxy.retrieveAttributeValue("totalMessagesAcknowledged", Long.class);
         }

         @Override
         public long getTotalConsumerCount() {
            return (Long) proxy.retrieveAttributeValue("totalConsumerCount", Long.class);
         }

         @Override
         public long getConnectionTTLOverride() {
            return (Long) proxy.retrieveAttributeValue("connectionTTLOverride", Long.class);
         }

         @Override
         public Object[] getConnectors() throws Exception {
            return (Object[]) proxy.retrieveAttributeValue("connectors");
         }

         @Override
         public String getConnectorsAsJSON() throws Exception {
            return (String) proxy.retrieveAttributeValue("connectorsAsJSON");
         }

         @Override
         public Object[] getAcceptors() throws Exception {
            return (Object[]) proxy.retrieveAttributeValue("acceptors");
         }

         @Override
         public String getAcceptorsAsJSON() throws Exception {
            return (String) proxy.retrieveAttributeValue("acceptorsAsJSON");
         }

         @Override
         public int getAddressCount() {
            return (Integer) proxy.retrieveAttributeValue("addressCount", Integer.class);
         }

         @Override
         public String[] getAddressNames() {
            return (String[]) proxy.retrieveAttributeValue("addressNames", String.class);
         }

         @Override
         public int getQueueCount() {
            return (Integer) proxy.retrieveAttributeValue("queueCount", Integer.class);
         }

         @Override
         public String[] getQueueNames() {
            return (String[]) proxy.retrieveAttributeValue("queueNames", String.class);
         }

         @Override
         public String[] getQueueNames(String routingType) {
            try {
               return (String[]) proxy.invokeOperation(String.class, "getQueueNames", routingType);
            } catch (Exception e) {
               e.printStackTrace();
            }

            return null;
         }

         @Override
         public String[] getClusterConnectionNames() {
            try {
               return (String[]) proxy.invokeOperation(String.class, "getClusterConnectionNames");
            } catch (Exception e) {
               e.printStackTrace();
            }

            return null;
         }

         @Override
         public void addUser(String username,
                             String password,
                             String role,
                             boolean plaintext) throws Exception {
            proxy.invokeOperation("addUser", username, password, role, plaintext);

         }

         @Override
         public String listUser(String username) throws Exception {
            return (String) proxy.invokeOperation("listUser", username, String.class);
         }

         @Override
         public void removeUser(String username) throws Exception {
            proxy.invokeOperation("removeUser", username);
         }

         @Override
         public void resetUser(String username, String password, String roles) throws Exception {
            proxy.invokeOperation("resetUser", username, password, roles);
         }

         @Override
         public void resetUser(String username, String password, String roles, boolean plaintext) throws Exception {
            proxy.invokeOperation("resetUser", username, password, roles, plaintext);
         }

         @Override
         public void reloadConfigurationFile() throws Exception {
            proxy.invokeOperation("reloadConfigurationFile");
         }

         @Override
         public String getUptime() {
            return null;
         }

         @Override
         public long getUptimeMillis() {
            return 0;
         }

         @Override
         public boolean isReplicaSync() {
            return false;
         }

         @Override
         public int getIDCacheSize() {
            return (Integer) proxy.retrieveAttributeValue("IDCacheSize", Integer.class);
         }

         public String[] getInterceptorClassNames() {
            return (String[]) proxy.retrieveAttributeValue("incomingInterceptorClassNames", String.class);
         }

         @Override
         public String[] getIncomingInterceptorClassNames() {
            return (String[]) proxy.retrieveAttributeValue("incomingInterceptorClassNames", String.class);
         }

         @Override
         public String[] getOutgoingInterceptorClassNames() {
            return (String[]) proxy.retrieveAttributeValue("outgoingInterceptorClassNames", String.class);
         }

         @Override
         public String[] getBrokerPluginClassNames() {
            return (String[]) proxy.retrieveAttributeValue("brokerPluginClassNames", String.class);
         }

         @Override
         public String getJournalDirectory() {
            return (String) proxy.retrieveAttributeValue("journalDirectory");
         }

         @Override
         public int getJournalFileSize() {
            return (Integer) proxy.retrieveAttributeValue("journalFileSize", Integer.class);
         }

         @Override
         public int getJournalMaxIO() {
            return (Integer) proxy.retrieveAttributeValue("journalMaxIO", Integer.class);
         }

         @Override
         public int getJournalMinFiles() {
            return (Integer) proxy.retrieveAttributeValue("journalMinFiles", Integer.class);
         }

         @Override
         public String getJournalType() {
            return (String) proxy.retrieveAttributeValue("journalType");
         }

         @Override
         public String getLargeMessagesDirectory() {
            return (String) proxy.retrieveAttributeValue("largeMessagesDirectory");
         }

         @Override
         public String getNodeID() {
            return (String) proxy.retrieveAttributeValue("nodeID");
         }

         @Override
         public long getActivationSequence() {
            return (Long) proxy.retrieveAttributeValue("activationSequence");
         }

         @Override
         public String getManagementAddress() {
            return (String) proxy.retrieveAttributeValue("managementAddress");
         }

         @Override
         public String getManagementNotificationAddress() {
            return (String) proxy.retrieveAttributeValue("managementNotificationAddress");
         }

         @Override
         public int getMessageCounterMaxDayCount() {
            return (Integer) proxy.retrieveAttributeValue("messageCounterMaxDayCount", Integer.class);
         }

         @Override
         public long getMessageCounterSamplePeriod() {
            return (Long) proxy.retrieveAttributeValue("messageCounterSamplePeriod", Long.class);
         }

         @Override
         public long getMessageExpiryScanPeriod() {
            return (Long) proxy.retrieveAttributeValue("messageExpiryScanPeriod", Long.class);
         }

         @Override
         public long getMessageExpiryThreadPriority() {
            return (Long) proxy.retrieveAttributeValue("messageExpiryThreadPriority", Long.class);
         }

         @Override
         public String getPagingDirectory() {
            return (String) proxy.retrieveAttributeValue("pagingDirectory");
         }

         @Override
         public int getScheduledThreadPoolMaxSize() {
            return (Integer) proxy.retrieveAttributeValue("scheduledThreadPoolMaxSize", Integer.class);
         }

         @Override
         public int getThreadPoolMaxSize() {
            return (Integer) proxy.retrieveAttributeValue("threadPoolMaxSize", Integer.class);
         }

         @Override
         public long getSecurityInvalidationInterval() {
            return (Long) proxy.retrieveAttributeValue("securityInvalidationInterval", Long.class);
         }

         @Override
         public long getTransactionTimeout() {
            return (Long) proxy.retrieveAttributeValue("transactionTimeout", Long.class);
         }

         @Override
         public long getTransactionTimeoutScanPeriod() {
            return (Long) proxy.retrieveAttributeValue("transactionTimeoutScanPeriod", Long.class);
         }

         @Override
         public String getName() {
            return (String) proxy.retrieveAttributeValue("name");
         }

         @Override
         public String getVersion() {
            return proxy.retrieveAttributeValue("version").toString();
         }

         @Override
         public boolean isBackup() {
            return (Boolean) proxy.retrieveAttributeValue("backup");
         }

         @Override
         public boolean isClustered() {
            return (Boolean) proxy.retrieveAttributeValue("clustered");
         }

         @Override
         public boolean isCreateBindingsDir() {
            return (Boolean) proxy.retrieveAttributeValue("createBindingsDir");
         }

         @Override
         public boolean isCreateJournalDir() {
            return (Boolean) proxy.retrieveAttributeValue("createJournalDir");
         }

         @Override
         public boolean isJournalSyncNonTransactional() {
            return (Boolean) proxy.retrieveAttributeValue("journalSyncNonTransactional");
         }

         @Override
         public boolean isJournalSyncTransactional() {
            return (Boolean) proxy.retrieveAttributeValue("journalSyncTransactional");
         }

         @Override
         public void setFailoverOnServerShutdown(boolean failoverOnServerShutdown) throws Exception {
            proxy.invokeOperation("setFailoverOnServerShutdown", failoverOnServerShutdown);
         }

         @Override
         public boolean isFailoverOnServerShutdown() {
            return (Boolean) proxy.retrieveAttributeValue("failoverOnServerShutdown");
         }

         public void setScaleDown(boolean scaleDown) throws Exception {
            proxy.invokeOperation("setEnabled", scaleDown);
         }

         public boolean isScaleDown() {
            return (Boolean) proxy.retrieveAttributeValue("scaleDown");
         }

         @Override
         public boolean isMessageCounterEnabled() {
            return (Boolean) proxy.retrieveAttributeValue("messageCounterEnabled");
         }

         @Override
         public boolean isPersistDeliveryCountBeforeDelivery() {
            return (Boolean) proxy.retrieveAttributeValue("persistDeliveryCountBeforeDelivery");
         }

         @Override
         public boolean isAsyncConnectionExecutionEnabled() {
            return (Boolean) proxy.retrieveAttributeValue("asyncConnectionExecutionEnabled");
         }

         @Override
         public boolean isPersistIDCache() {
            return (Boolean) proxy.retrieveAttributeValue("persistIDCache");
         }

         @Override
         public boolean isSecurityEnabled() {
            return (Boolean) proxy.retrieveAttributeValue("securityEnabled");
         }

         @Override
         public boolean isStarted() {
            return (Boolean) proxy.retrieveAttributeValue("started");
         }

         @Override
         public boolean isWildcardRoutingEnabled() {
            return (Boolean) proxy.retrieveAttributeValue("wildcardRoutingEnabled");
         }

         @Override
         public String[] listConnectionIDs() throws Exception {
            return (String[]) proxy.invokeOperation("listConnectionIDs");
         }

         @Override
         public String[] listPreparedTransactions() throws Exception {
            return (String[]) proxy.invokeOperation("listPreparedTransactions");
         }

         @Override
         public String listPreparedTransactionDetailsAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listPreparedTransactionDetailsAsJSON");
         }

         @Override
         public String listPreparedTransactionDetailsAsHTML() throws Exception {
            return (String) proxy.invokeOperation("listPreparedTransactionDetailsAsHTML");
         }

         @Override
         public String[] listHeuristicCommittedTransactions() throws Exception {
            return (String[]) proxy.invokeOperation("listHeuristicCommittedTransactions");
         }

         @Override
         public String[] listHeuristicRolledBackTransactions() throws Exception {
            return (String[]) proxy.invokeOperation("listHeuristicRolledBackTransactions");
         }

         @Override
         public String[] listRemoteAddresses() throws Exception {
            return (String[]) proxy.invokeOperation("listRemoteAddresses");
         }

         @Override
         public String[] listRemoteAddresses(final String ipAddress) throws Exception {
            return (String[]) proxy.invokeOperation("listRemoteAddresses", ipAddress);
         }

         @Override
         public String[] listSessions(final String connectionID) throws Exception {
            return (String[]) proxy.invokeOperation("listSessions", connectionID);
         }

         @Override
         public void resetAllMessageCounterHistories() throws Exception {
            proxy.invokeOperation("resetAllMessageCounterHistories");
         }

         @Override
         public void resetAllMessageCounters() throws Exception {
            proxy.invokeOperation("resetAllMessageCounters");
         }

         @Override
         public boolean rollbackPreparedTransaction(final String transactionAsBase64) throws Exception {
            return (Boolean) proxy.invokeOperation("rollbackPreparedTransaction", transactionAsBase64);
         }

         public void sendQueueInfoToQueue(final String queueName, final String address) throws Exception {
            proxy.invokeOperation("sendQueueInfoToQueue", queueName, address);
         }

         @Override
         public void setMessageCounterMaxDayCount(final int count) throws Exception {
            proxy.invokeOperation("setMessageCounterMaxDayCount", count);
         }

         @Override
         public void setMessageCounterSamplePeriod(final long newPeriod) throws Exception {
            proxy.invokeOperation("setMessageCounterSamplePeriod", newPeriod);
         }

         @Override
         public int getJournalBufferSize() {
            return (Integer) proxy.retrieveAttributeValue("JournalBufferSize", Integer.class);
         }

         @Override
         public int getJournalPoolFiles() {
            return (Integer) proxy.retrieveAttributeValue("JournalPoolFiles", Integer.class);
         }

         @Override
         public int getJournalBufferTimeout() {
            return (Integer) proxy.retrieveAttributeValue("JournalBufferTimeout", Integer.class);
         }

         @Override
         public int getJournalCompactMinFiles() {
            return (Integer) proxy.retrieveAttributeValue("JournalCompactMinFiles", Integer.class);
         }

         @Override
         public int getJournalCompactPercentage() {
            return (Integer) proxy.retrieveAttributeValue("JournalCompactPercentage", Integer.class);
         }

         @Override
         public boolean isPersistenceEnabled() {
            return (Boolean) proxy.retrieveAttributeValue("PersistenceEnabled");
         }

         @Override
         public int getDiskScanPeriod() {
            return (Integer) proxy.retrieveAttributeValue("DiskScanPeriod", Integer.class);
         }

         @Override
         public int getMaxDiskUsage() {
            return (Integer) proxy.retrieveAttributeValue("MaxDiskUsage", Integer.class);
         }

         @Override
         public long getGlobalMaxSize() {
            return (Long) proxy.retrieveAttributeValue("GlobalMaxSize", Long.class);
         }

         @Override
         public long getAddressMemoryUsage() {
            try {
               return (Long) proxy.invokeOperation("getAddressMemoryUsage");
            } catch (Exception e) {
               e.printStackTrace();
            }
            return 0;
         }

         @Override
         public int getAddressMemoryUsagePercentage() {
            try {
               return (Integer) proxy.invokeOperation(Integer.TYPE, "getAddressMemoryUsagePercentage");
            } catch (Exception e) {
               e.printStackTrace();
            }
            return 0;
         }

         @Override
         public long getAuthenticationCacheSize() {
            return (Long) proxy.retrieveAttributeValue("AuthenticationCacheSize", Long.class);
         }

         @Override
         public long getAuthorizationCacheSize() {
            return (Long) proxy.retrieveAttributeValue("AuthorizationCacheSize", Long.class);
         }

         @Override
         public String getStatus() {
            return (String) proxy.retrieveAttributeValue("Status", String.class);
         }

         @Override
         public double getDiskStoreUsage() {
            try {
               return (Double) proxy.invokeOperation("getDiskStoreUsage");
            } catch (Exception e) {
               e.printStackTrace();
            }
            return 0;
         }

         @Override
         public String getHAPolicy() {
            return null;
         }

         @Override
         public boolean freezeReplication() {

            return false;
         }

         @Override
         public String createAddress(String name, String routingTypes) throws Exception {
            return (String) proxy.invokeOperation("createAddress", name, routingTypes);
         }

         @Override
         public void addSecuritySettings(String addressMatch,
                                         String sendRoles,
                                         String consumeRoles,
                                         String createDurableQueueRoles,
                                         String deleteDurableQueueRoles,
                                         String createNonDurableQueueRoles,
                                         String deleteNonDurableQueueRoles,
                                         String manageRoles) throws Exception {
            proxy.invokeOperation("addSecuritySettings", addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles);
         }

         @Override
         public void addSecuritySettings(String addressMatch,
                                         String sendRoles,
                                         String consumeRoles,
                                         String createDurableQueueRoles,
                                         String deleteDurableQueueRoles,
                                         String createNonDurableQueueRoles,
                                         String deleteNonDurableQueueRoles,
                                         String manageRoles,
                                         String browseRoles) throws Exception {
            proxy.invokeOperation("addSecuritySettings", addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles);
         }

         @Override
         public void addSecuritySettings(String addressMatch,
                                         String sendRoles,
                                         String consumeRoles,
                                         String createDurableQueueRoles,
                                         String deleteDurableQueueRoles,
                                         String createNonDurableQueueRoles,
                                         String deleteNonDurableQueueRoles,
                                         String manageRoles,
                                         String browseRoles,
                                         String createAddressRoles,
                                         String deleteAddressRoles) throws Exception {

            proxy.invokeOperation("addSecuritySettings", addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles, createAddressRoles, deleteAddressRoles);
         }

         @Override
         public void addSecuritySettings(String addressMatch,
                                         String sendRoles,
                                         String consumeRoles,
                                         String createDurableQueueRoles,
                                         String deleteDurableQueueRoles,
                                         String createNonDurableQueueRoles,
                                         String deleteNonDurableQueueRoles,
                                         String manageRoles,
                                         String browseRoles,
                                         String createAddress,
                                         String deleteAddress, String viewRoles, String editRoles) throws Exception {
            proxy.invokeOperation("addSecuritySettings", addressMatch, sendRoles, consumeRoles, createDurableQueueRoles, deleteDurableQueueRoles, createNonDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles, createAddress, deleteAddress, viewRoles, editRoles);
         }

         @Override
         public void addSecuritySettings(String addressMatch, String securitySettingsAsJson) throws Exception {
            proxy.invokeOperation("addSecuritySettings", addressMatch, securitySettingsAsJson);
         }

         @Override
         public void removeSecuritySettings(String addressMatch) throws Exception {
            proxy.invokeOperation("removeSecuritySettings", addressMatch);
         }

         @Override
         public Object[] getRoles(String addressMatch) throws Exception {
            return (Object[]) proxy.invokeOperation("getRoles", addressMatch);
         }

         @Override
         public String getRolesAsJSON(String addressMatch) throws Exception {
            return (String) proxy.invokeOperation("getRolesAsJSON", addressMatch);
         }

         @Override
         public void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                                        @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                                        @Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                                        @Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                                        @Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                                        @Parameter(desc = "the delivery attempts", name = "deliveryAttempts") int deliveryAttempts,
                                        @Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                                        @Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                                        @Parameter(desc = "the max number of pages in the soft memory cache", name = "pageMaxCacheSize") int pageMaxCacheSize,
                                        @Parameter(desc = "the redelivery delay", name = "redeliveryDelay") long redeliveryDelay,
                                        @Parameter(desc = "the redelivery delay multiplier", name = "redeliveryMultiplier") double redeliveryMultiplier,
                                        @Parameter(desc = "the maximum redelivery delay", name = "maxRedeliveryDelay") long maxRedeliveryDelay,
                                        @Parameter(desc = "the redistribution delay", name = "redistributionDelay") long redistributionDelay,
                                        @Parameter(desc = "do we send to the DLA when there is no where to route the message", name = "sendToDLAOnNoRoute") boolean sendToDLAOnNoRoute,
                                        @Parameter(desc = "the policy to use when the address is full", name = "addressFullMessagePolicy") String addressFullMessagePolicy,
                                        @Parameter(desc = "when a consumer falls below this threshold in terms of messages consumed per second it will be considered 'slow'", name = "slowConsumerThreshold") long slowConsumerThreshold,
                                        @Parameter(desc = "how often (in seconds) to check for slow consumers", name = "slowConsumerCheckPeriod") long slowConsumerCheckPeriod,
                                        @Parameter(desc = "the policy to use when a slow consumer is detected", name = "slowConsumerPolicy") String slowConsumerPolicy,
                                        @Parameter(desc = "allow queues to be created automatically", name = "autoCreateJmsQueues") boolean autoCreateJmsQueues,
                                        @Parameter(desc = "allow auto-created queues to be deleted automatically", name = "autoDeleteJmsQueues") boolean autoDeleteJmsQueues,
                                        @Parameter(desc = "allow topics to be created automatically", name = "autoCreateJmsTopics") boolean autoCreateJmsTopics,
                                        @Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics) throws Exception {
            proxy.invokeOperation("addAddressSettings",
                                  addressMatch,
                                  DLA,
                                  expiryAddress,
                                  expiryDelay,
                                  lastValueQueue,
                                  deliveryAttempts,
                                  maxSizeBytes,
                                  pageSizeBytes,
                                  pageMaxCacheSize,
                                  redeliveryDelay,
                                  redeliveryMultiplier,
                                  maxRedeliveryDelay,
                                  redistributionDelay,
                                  sendToDLAOnNoRoute,
                                  addressFullMessagePolicy,
                                  slowConsumerThreshold,
                                  slowConsumerCheckPeriod,
                                  slowConsumerPolicy,
                                  autoCreateJmsQueues,
                                  autoDeleteJmsQueues,
                                  autoCreateJmsTopics,
                                  autoDeleteJmsTopics);
         }

         @Override
         public void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                                        @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                                        @Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                                        @Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                                        @Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                                        @Parameter(desc = "the delivery attempts", name = "deliveryAttempts") int deliveryAttempts,
                                        @Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                                        @Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                                        @Parameter(desc = "the max number of pages in the soft memory cache", name = "pageMaxCacheSize") int pageMaxCacheSize,
                                        @Parameter(desc = "the redelivery delay", name = "redeliveryDelay") long redeliveryDelay,
                                        @Parameter(desc = "the redelivery delay multiplier", name = "redeliveryMultiplier") double redeliveryMultiplier,
                                        @Parameter(desc = "the maximum redelivery delay", name = "maxRedeliveryDelay") long maxRedeliveryDelay,
                                        @Parameter(desc = "the redistribution delay", name = "redistributionDelay") long redistributionDelay,
                                        @Parameter(desc = "do we send to the DLA when there is no where to route the message", name = "sendToDLAOnNoRoute") boolean sendToDLAOnNoRoute,
                                        @Parameter(desc = "the policy to use when the address is full", name = "addressFullMessagePolicy") String addressFullMessagePolicy,
                                        @Parameter(desc = "when a consumer falls below this threshold in terms of messages consumed per second it will be considered 'slow'", name = "slowConsumerThreshold") long slowConsumerThreshold,
                                        @Parameter(desc = "how often (in seconds) to check for slow consumers", name = "slowConsumerCheckPeriod") long slowConsumerCheckPeriod,
                                        @Parameter(desc = "the policy to use when a slow consumer is detected", name = "slowConsumerPolicy") String slowConsumerPolicy,
                                        @Parameter(desc = "allow jms queues to be created automatically", name = "autoCreateJmsQueues") boolean autoCreateJmsQueues,
                                        @Parameter(desc = "allow auto-created jms queues to be deleted automatically", name = "autoDeleteJmsQueues") boolean autoDeleteJmsQueues,
                                        @Parameter(desc = "allow jms topics to be created automatically", name = "autoCreateJmsTopics") boolean autoCreateJmsTopics,
                                        @Parameter(desc = "allow auto-created jms topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics,
                                        @Parameter(desc = "allow queues to be created automatically", name = "autoCreateQueues") boolean autoCreateQueues,
                                        @Parameter(desc = "allow auto-created queues to be deleted automatically", name = "autoDeleteQueues") boolean autoDeleteQueues,
                                        @Parameter(desc = "allow topics to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                                        @Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses) throws Exception {
            proxy.invokeOperation("addAddressSettings",
                                  addressMatch,
                                  DLA,
                                  expiryAddress,
                                  expiryDelay,
                                  lastValueQueue,
                                  deliveryAttempts,
                                  maxSizeBytes,
                                  pageSizeBytes,
                                  pageMaxCacheSize,
                                  redeliveryDelay,
                                  redeliveryMultiplier,
                                  maxRedeliveryDelay,
                                  redistributionDelay,
                                  sendToDLAOnNoRoute,
                                  addressFullMessagePolicy,
                                  slowConsumerThreshold,
                                  slowConsumerCheckPeriod,
                                  slowConsumerPolicy,
                                  autoCreateJmsQueues,
                                  autoDeleteJmsQueues,
                                  autoCreateJmsTopics,
                                  autoDeleteJmsTopics,
                                  autoCreateQueues,
                                  autoDeleteQueues,
                                  autoCreateAddresses,
                                  autoDeleteAddresses);
         }

         @Override
         public void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                                        @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                                        @Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                                        @Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                                        @Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                                        @Parameter(desc = "the delivery attempts", name = "deliveryAttempts") int deliveryAttempts,
                                        @Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                                        @Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                                        @Parameter(desc = "the max number of pages in the soft memory cache", name = "pageMaxCacheSize") int pageMaxCacheSize,
                                        @Parameter(desc = "the redelivery delay", name = "redeliveryDelay") long redeliveryDelay,
                                        @Parameter(desc = "the redelivery delay multiplier", name = "redeliveryMultiplier") double redeliveryMultiplier,
                                        @Parameter(desc = "the maximum redelivery delay", name = "maxRedeliveryDelay") long maxRedeliveryDelay,
                                        @Parameter(desc = "the redistribution delay", name = "redistributionDelay") long redistributionDelay,
                                        @Parameter(desc = "do we send to the DLA when there is no where to route the message", name = "sendToDLAOnNoRoute") boolean sendToDLAOnNoRoute,
                                        @Parameter(desc = "the policy to use when the address is full", name = "addressFullMessagePolicy") String addressFullMessagePolicy,
                                        @Parameter(desc = "when a consumer falls below this threshold in terms of messages consumed per second it will be considered 'slow'", name = "slowConsumerThreshold") long slowConsumerThreshold,
                                        @Parameter(desc = "how often (in seconds) to check for slow consumers", name = "slowConsumerCheckPeriod") long slowConsumerCheckPeriod,
                                        @Parameter(desc = "the policy to use when a slow consumer is detected", name = "slowConsumerPolicy") String slowConsumerPolicy,
                                        @Parameter(desc = "allow jms queues to be created automatically", name = "autoCreateJmsQueues") boolean autoCreateJmsQueues,
                                        @Parameter(desc = "allow auto-created jms queues to be deleted automatically", name = "autoDeleteJmsQueues") boolean autoDeleteJmsQueues,
                                        @Parameter(desc = "allow jms topics to be created automatically", name = "autoCreateJmsTopics") boolean autoCreateJmsTopics,
                                        @Parameter(desc = "allow auto-created jms topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics,
                                        @Parameter(desc = "allow queues to be created automatically", name = "autoCreateQueues") boolean autoCreateQueues,
                                        @Parameter(desc = "allow auto-created queues to be deleted automatically", name = "autoDeleteQueues") boolean autoDeleteQueues,
                                        @Parameter(desc = "allow topics to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                                        @Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses,
                                        @Parameter(desc = "how to deal with queues deleted from XML at runtime", name = "configDeleteQueues") String configDeleteQueues,
                                        @Parameter(desc = "how to deal with addresses deleted from XML at runtime", name = "configDeleteAddresses") String configDeleteAddresses,
                                        @Parameter(desc = "used with `BLOCK`, the max size an address can reach before messages are rejected; works in combination with `max-size-bytes` for AMQP clients only", name = "maxSizeBytesRejectThreshold") long maxSizeBytesRejectThreshold,
                                        @Parameter(desc = "last-value-key value if none is set on the queue", name = "defaultLastValueKey") String defaultLastValueKey,
                                        @Parameter(desc = "non-destructive value if none is set on the queue", name = "defaultNonDestructive") boolean defaultNonDestructive,
                                        @Parameter(desc = "exclusive value if none is set on the queue", name = "defaultExclusiveQueue") boolean defaultExclusiveQueue,
                                        @Parameter(desc = "group-rebalance value if none is set on the queue", name = "defaultGroupRebalance") boolean defaultGroupRebalance,
                                        @Parameter(desc = "group-buckets value if none is set on the queue", name = "defaultGroupBuckets") int defaultGroupBuckets,
                                        @Parameter(desc = "group-first-key value if none is set on the queue", name = "defaultGroupFirstKey") String defaultGroupFirstKey,
                                        @Parameter(desc = "max-consumers value if none is set on the queue", name = "defaultMaxConsumers") int defaultMaxConsumers,
                                        @Parameter(desc = "purge-on-no-consumers value if none is set on the queue", name = "defaultPurgeOnNoConsumers") boolean defaultPurgeOnNoConsumers,
                                        @Parameter(desc = "consumers-before-dispatch value if none is set on the queue", name = "defaultConsumersBeforeDispatch") int defaultConsumersBeforeDispatch,
                                        @Parameter(desc = "delay-before-dispatch value if none is set on the queue", name = "defaultDelayBeforeDispatch") long defaultDelayBeforeDispatch,
                                        @Parameter(desc = "routing-type value if none is set on the queue", name = "defaultQueueRoutingType") String defaultQueueRoutingType,
                                        @Parameter(desc = "routing-type value if none is set on the address", name = "defaultAddressRoutingType") String defaultAddressRoutingType,
                                        @Parameter(desc = "consumer-window-size value if none is set on the queue", name = "defaultConsumerWindowSize") int defaultConsumerWindowSize,
                                        @Parameter(desc = "ring-size value if none is set on the queue", name = "defaultRingSize") long defaultRingSize,
                                        @Parameter(desc = "allow created queues to be deleted automatically", name = "autoDeleteCreatedQueues") boolean autoDeleteCreatedQueues,
                                        @Parameter(desc = "delay for deleting auto-created queues", name = "autoDeleteQueuesDelay") long autoDeleteQueuesDelay,
                                        @Parameter(desc = "the message count the queue must be at or below before it can be auto deleted", name = "autoDeleteQueuesMessageCount") long autoDeleteQueuesMessageCount,
                                        @Parameter(desc = "delay for deleting auto-created addresses", name = "autoDeleteAddressesDelay") long autoDeleteAddressesDelay,
                                        @Parameter(desc = "factor by which to modify the redelivery delay slightly to avoid collisions", name = "redeliveryCollisionAvoidanceFactor") double redeliveryCollisionAvoidanceFactor,
                                        @Parameter(desc = "the number of messages to preserve for future queues created on the matching address", name = "retroactiveMessageCount") long retroactiveMessageCount) throws Exception {
            proxy.invokeOperation("addAddressSettings",
                                  addressMatch,
                                  DLA,
                                  expiryAddress,
                                  expiryDelay,
                                  lastValueQueue,
                                  deliveryAttempts,
                                  maxSizeBytes,
                                  pageSizeBytes,
                                  pageMaxCacheSize,
                                  redeliveryDelay,
                                  redeliveryMultiplier,
                                  maxRedeliveryDelay,
                                  redistributionDelay,
                                  sendToDLAOnNoRoute,
                                  addressFullMessagePolicy,
                                  slowConsumerThreshold,
                                  slowConsumerCheckPeriod,
                                  slowConsumerPolicy,
                                  autoCreateJmsQueues,
                                  autoDeleteJmsQueues,
                                  autoCreateJmsTopics,
                                  autoDeleteJmsTopics,
                                  autoCreateQueues,
                                  autoDeleteQueues,
                                  autoCreateAddresses,
                                  autoDeleteAddresses,
                                  configDeleteQueues,
                                  configDeleteAddresses,
                                  maxSizeBytesRejectThreshold,
                                  defaultLastValueKey,
                                  defaultNonDestructive,
                                  defaultExclusiveQueue,
                                  defaultGroupRebalance,
                                  defaultGroupBuckets,
                                  defaultGroupFirstKey,
                                  defaultMaxConsumers,
                                  defaultPurgeOnNoConsumers,
                                  defaultConsumersBeforeDispatch,
                                  defaultDelayBeforeDispatch,
                                  defaultQueueRoutingType,
                                  defaultAddressRoutingType,
                                  defaultConsumerWindowSize,
                                  defaultRingSize,
                                  autoDeleteCreatedQueues,
                                  autoDeleteQueuesDelay,
                                  autoDeleteQueuesMessageCount,
                                  autoDeleteAddressesDelay,
                                  redeliveryCollisionAvoidanceFactor,
                                  retroactiveMessageCount);
         }

         @Override
         public void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                                        @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                                        @Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                                        @Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                                        @Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                                        @Parameter(desc = "the delivery attempts", name = "deliveryAttempts") int deliveryAttempts,
                                        @Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                                        @Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                                        @Parameter(desc = "the max number of pages in the soft memory cache", name = "pageMaxCacheSize") int pageMaxCacheSize,
                                        @Parameter(desc = "the redelivery delay", name = "redeliveryDelay") long redeliveryDelay,
                                        @Parameter(desc = "the redelivery delay multiplier", name = "redeliveryMultiplier") double redeliveryMultiplier,
                                        @Parameter(desc = "the maximum redelivery delay", name = "maxRedeliveryDelay") long maxRedeliveryDelay,
                                        @Parameter(desc = "the redistribution delay", name = "redistributionDelay") long redistributionDelay,
                                        @Parameter(desc = "do we send to the DLA when there is no where to route the message", name = "sendToDLAOnNoRoute") boolean sendToDLAOnNoRoute,
                                        @Parameter(desc = "the policy to use when the address is full", name = "addressFullMessagePolicy") String addressFullMessagePolicy,
                                        @Parameter(desc = "when a consumer falls below this threshold in terms of messages consumed per second it will be considered 'slow'", name = "slowConsumerThreshold") long slowConsumerThreshold,
                                        @Parameter(desc = "how often (in seconds) to check for slow consumers", name = "slowConsumerCheckPeriod") long slowConsumerCheckPeriod,
                                        @Parameter(desc = "the policy to use when a slow consumer is detected", name = "slowConsumerPolicy") String slowConsumerPolicy,
                                        @Parameter(desc = "allow jms queues to be created automatically", name = "autoCreateJmsQueues") boolean autoCreateJmsQueues,
                                        @Parameter(desc = "allow auto-created jms queues to be deleted automatically", name = "autoDeleteJmsQueues") boolean autoDeleteJmsQueues,
                                        @Parameter(desc = "allow jms topics to be created automatically", name = "autoCreateJmsTopics") boolean autoCreateJmsTopics,
                                        @Parameter(desc = "allow auto-created jms topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics,
                                        @Parameter(desc = "allow queues to be created automatically", name = "autoCreateQueues") boolean autoCreateQueues,
                                        @Parameter(desc = "allow auto-created queues to be deleted automatically", name = "autoDeleteQueues") boolean autoDeleteQueues,
                                        @Parameter(desc = "allow topics to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                                        @Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses,
                                        @Parameter(desc = "how to deal with queues deleted from XML at runtime", name = "configDeleteQueues") String configDeleteQueues,
                                        @Parameter(desc = "how to deal with addresses deleted from XML at runtime", name = "configDeleteAddresses") String configDeleteAddresses,
                                        @Parameter(desc = "used with `BLOCK`, the max size an address can reach before messages are rejected; works in combination with `max-size-bytes` for AMQP clients only", name = "maxSizeBytesRejectThreshold") long maxSizeBytesRejectThreshold,
                                        @Parameter(desc = "last-value-key value if none is set on the queue", name = "defaultLastValueKey") String defaultLastValueKey,
                                        @Parameter(desc = "non-destructive value if none is set on the queue", name = "defaultNonDestructive") boolean defaultNonDestructive,
                                        @Parameter(desc = "exclusive value if none is set on the queue", name = "defaultExclusiveQueue") boolean defaultExclusiveQueue,
                                        @Parameter(desc = "group-rebalance value if none is set on the queue", name = "defaultGroupRebalance") boolean defaultGroupRebalance,
                                        @Parameter(desc = "group-buckets value if none is set on the queue", name = "defaultGroupBuckets") int defaultGroupBuckets,
                                        @Parameter(desc = "group-first-key value if none is set on the queue", name = "defaultGroupFirstKey") String defaultGroupFirstKey,
                                        @Parameter(desc = "max-consumers value if none is set on the queue", name = "defaultMaxConsumers") int defaultMaxConsumers,
                                        @Parameter(desc = "purge-on-no-consumers value if none is set on the queue", name = "defaultPurgeOnNoConsumers") boolean defaultPurgeOnNoConsumers,
                                        @Parameter(desc = "consumers-before-dispatch value if none is set on the queue", name = "defaultConsumersBeforeDispatch") int defaultConsumersBeforeDispatch,
                                        @Parameter(desc = "delay-before-dispatch value if none is set on the queue", name = "defaultDelayBeforeDispatch") long defaultDelayBeforeDispatch,
                                        @Parameter(desc = "routing-type value if none is set on the queue", name = "defaultQueueRoutingType") String defaultQueueRoutingType,
                                        @Parameter(desc = "routing-type value if none is set on the address", name = "defaultAddressRoutingType") String defaultAddressRoutingType,
                                        @Parameter(desc = "consumer-window-size value if none is set on the queue", name = "defaultConsumerWindowSize") int defaultConsumerWindowSize,
                                        @Parameter(desc = "ring-size value if none is set on the queue", name = "defaultRingSize") long defaultRingSize,
                                        @Parameter(desc = "allow created queues to be deleted automatically", name = "autoDeleteCreatedQueues") boolean autoDeleteCreatedQueues,
                                        @Parameter(desc = "delay for deleting auto-created queues", name = "autoDeleteQueuesDelay") long autoDeleteQueuesDelay,
                                        @Parameter(desc = "the message count the queue must be at or below before it can be auto deleted", name = "autoDeleteQueuesMessageCount") long autoDeleteQueuesMessageCount,
                                        @Parameter(desc = "delay for deleting auto-created addresses", name = "autoDeleteAddressesDelay") long autoDeleteAddressesDelay,
                                        @Parameter(desc = "factor by which to modify the redelivery delay slightly to avoid collisions", name = "redeliveryCollisionAvoidanceFactor") double redeliveryCollisionAvoidanceFactor,
                                        @Parameter(desc = "the number of messages to preserve for future queues created on the matching address", name = "retroactiveMessageCount") long retroactiveMessageCount,
                                        @Parameter(desc = "allow dead-letter address & queue to be created automatically", name = "autoCreateDeadLetterResources") boolean autoCreateDeadLetterResources,
                                        @Parameter(desc = "prefix to use on auto-create dead-letter queue", name = "deadLetterQueuePrefix") String deadLetterQueuePrefix,
                                        @Parameter(desc = "suffix to use on auto-create dead-letter queue", name = "deadLetterQueueSuffix") String deadLetterQueueSuffix,
                                        @Parameter(desc = "allow expiry address & queue to be created automatically", name = "autoCreateExpiryResources") boolean autoCreateExpiryResources,
                                        @Parameter(desc = "prefix to use on auto-create expiry queue", name = "expiryQueuePrefix") String expiryQueuePrefix,
                                        @Parameter(desc = "suffix to use on auto-create expiry queue", name = "expiryQueueSuffix") String expiryQueueSuffix) throws Exception {
            proxy.invokeOperation("addAddressSettings",
                                  addressMatch,
                                  DLA,
                                  expiryAddress,
                                  expiryDelay,
                                  lastValueQueue,
                                  deliveryAttempts,
                                  maxSizeBytes,
                                  pageSizeBytes,
                                  pageMaxCacheSize,
                                  redeliveryDelay,
                                  redeliveryMultiplier,
                                  maxRedeliveryDelay,
                                  redistributionDelay,
                                  sendToDLAOnNoRoute,
                                  addressFullMessagePolicy,
                                  slowConsumerThreshold,
                                  slowConsumerCheckPeriod,
                                  slowConsumerPolicy,
                                  autoCreateJmsQueues,
                                  autoDeleteJmsQueues,
                                  autoCreateJmsTopics,
                                  autoDeleteJmsTopics,
                                  autoCreateQueues,
                                  autoDeleteQueues,
                                  autoCreateAddresses,
                                  autoDeleteAddresses,
                                  configDeleteQueues,
                                  configDeleteAddresses,
                                  maxSizeBytesRejectThreshold,
                                  defaultLastValueKey,
                                  defaultNonDestructive,
                                  defaultExclusiveQueue,
                                  defaultGroupRebalance,
                                  defaultGroupBuckets,
                                  defaultGroupFirstKey,
                                  defaultMaxConsumers,
                                  defaultPurgeOnNoConsumers,
                                  defaultConsumersBeforeDispatch,
                                  defaultDelayBeforeDispatch,
                                  defaultQueueRoutingType,
                                  defaultAddressRoutingType,
                                  defaultConsumerWindowSize,
                                  defaultRingSize,
                                  autoDeleteCreatedQueues,
                                  autoDeleteQueuesDelay,
                                  autoDeleteQueuesMessageCount,
                                  autoDeleteAddressesDelay,
                                  redeliveryCollisionAvoidanceFactor,
                                  retroactiveMessageCount,
                                  autoCreateDeadLetterResources,
                                  deadLetterQueuePrefix,
                                  deadLetterQueueSuffix,
                                  autoCreateExpiryResources,
                                  expiryQueuePrefix,
                                  expiryQueueSuffix);
         }

         @Override
         public void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                                        @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                                        @Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                                        @Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                                        @Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                                        @Parameter(desc = "the delivery attempts", name = "deliveryAttempts") int deliveryAttempts,
                                        @Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                                        @Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                                        @Parameter(desc = "the max number of pages in the soft memory cache", name = "pageMaxCacheSize") int pageMaxCacheSize,
                                        @Parameter(desc = "the redelivery delay", name = "redeliveryDelay") long redeliveryDelay,
                                        @Parameter(desc = "the redelivery delay multiplier", name = "redeliveryMultiplier") double redeliveryMultiplier,
                                        @Parameter(desc = "the maximum redelivery delay", name = "maxRedeliveryDelay") long maxRedeliveryDelay,
                                        @Parameter(desc = "the redistribution delay", name = "redistributionDelay") long redistributionDelay,
                                        @Parameter(desc = "do we send to the DLA when there is no where to route the message", name = "sendToDLAOnNoRoute") boolean sendToDLAOnNoRoute,
                                        @Parameter(desc = "the policy to use when the address is full", name = "addressFullMessagePolicy") String addressFullMessagePolicy,
                                        @Parameter(desc = "when a consumer falls below this threshold in terms of messages consumed per second it will be considered 'slow'", name = "slowConsumerThreshold") long slowConsumerThreshold,
                                        @Parameter(desc = "how often (in seconds) to check for slow consumers", name = "slowConsumerCheckPeriod") long slowConsumerCheckPeriod,
                                        @Parameter(desc = "the policy to use when a slow consumer is detected", name = "slowConsumerPolicy") String slowConsumerPolicy,
                                        @Parameter(desc = "allow jms queues to be created automatically", name = "autoCreateJmsQueues") boolean autoCreateJmsQueues,
                                        @Parameter(desc = "allow auto-created jms queues to be deleted automatically", name = "autoDeleteJmsQueues") boolean autoDeleteJmsQueues,
                                        @Parameter(desc = "allow jms topics to be created automatically", name = "autoCreateJmsTopics") boolean autoCreateJmsTopics,
                                        @Parameter(desc = "allow auto-created jms topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics,
                                        @Parameter(desc = "allow queues to be created automatically", name = "autoCreateQueues") boolean autoCreateQueues,
                                        @Parameter(desc = "allow auto-created queues to be deleted automatically", name = "autoDeleteQueues") boolean autoDeleteQueues,
                                        @Parameter(desc = "allow topics to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                                        @Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses,
                                        @Parameter(desc = "how to deal with queues deleted from XML at runtime", name = "configDeleteQueues") String configDeleteQueues,
                                        @Parameter(desc = "how to deal with addresses deleted from XML at runtime", name = "configDeleteAddresses") String configDeleteAddresses,
                                        @Parameter(desc = "used with `BLOCK`, the max size an address can reach before messages are rejected; works in combination with `max-size-bytes` for AMQP clients only", name = "maxSizeBytesRejectThreshold") long maxSizeBytesRejectThreshold,
                                        @Parameter(desc = "last-value-key value if none is set on the queue", name = "defaultLastValueKey") String defaultLastValueKey,
                                        @Parameter(desc = "non-destructive value if none is set on the queue", name = "defaultNonDestructive") boolean defaultNonDestructive,
                                        @Parameter(desc = "exclusive value if none is set on the queue", name = "defaultExclusiveQueue") boolean defaultExclusiveQueue,
                                        @Parameter(desc = "group-rebalance value if none is set on the queue", name = "defaultGroupRebalance") boolean defaultGroupRebalance,
                                        @Parameter(desc = "group-buckets value if none is set on the queue", name = "defaultGroupBuckets") int defaultGroupBuckets,
                                        @Parameter(desc = "group-first-key value if none is set on the queue", name = "defaultGroupFirstKey") String defaultGroupFirstKey,
                                        @Parameter(desc = "max-consumers value if none is set on the queue", name = "defaultMaxConsumers") int defaultMaxConsumers,
                                        @Parameter(desc = "purge-on-no-consumers value if none is set on the queue", name = "defaultPurgeOnNoConsumers") boolean defaultPurgeOnNoConsumers,
                                        @Parameter(desc = "consumers-before-dispatch value if none is set on the queue", name = "defaultConsumersBeforeDispatch") int defaultConsumersBeforeDispatch,
                                        @Parameter(desc = "delay-before-dispatch value if none is set on the queue", name = "defaultDelayBeforeDispatch") long defaultDelayBeforeDispatch,
                                        @Parameter(desc = "routing-type value if none is set on the queue", name = "defaultQueueRoutingType") String defaultQueueRoutingType,
                                        @Parameter(desc = "routing-type value if none is set on the address", name = "defaultAddressRoutingType") String defaultAddressRoutingType,
                                        @Parameter(desc = "consumer-window-size value if none is set on the queue", name = "defaultConsumerWindowSize") int defaultConsumerWindowSize,
                                        @Parameter(desc = "ring-size value if none is set on the queue", name = "defaultRingSize") long defaultRingSize,
                                        @Parameter(desc = "allow created queues to be deleted automatically", name = "autoDeleteCreatedQueues") boolean autoDeleteCreatedQueues,
                                        @Parameter(desc = "delay for deleting auto-created queues", name = "autoDeleteQueuesDelay") long autoDeleteQueuesDelay,
                                        @Parameter(desc = "the message count the queue must be at or below before it can be auto deleted", name = "autoDeleteQueuesMessageCount") long autoDeleteQueuesMessageCount,
                                        @Parameter(desc = "delay for deleting auto-created addresses", name = "autoDeleteAddressesDelay") long autoDeleteAddressesDelay,
                                        @Parameter(desc = "factor by which to modify the redelivery delay slightly to avoid collisions", name = "redeliveryCollisionAvoidanceFactor") double redeliveryCollisionAvoidanceFactor,
                                        @Parameter(desc = "the number of messages to preserve for future queues created on the matching address", name = "retroactiveMessageCount") long retroactiveMessageCount,
                                        @Parameter(desc = "allow dead-letter address & queue to be created automatically", name = "autoCreateDeadLetterResources") boolean autoCreateDeadLetterResources,
                                        @Parameter(desc = "prefix to use on auto-create dead-letter queue", name = "deadLetterQueuePrefix") String deadLetterQueuePrefix,
                                        @Parameter(desc = "suffix to use on auto-create dead-letter queue", name = "deadLetterQueueSuffix") String deadLetterQueueSuffix,
                                        @Parameter(desc = "allow expiry address & queue to be created automatically", name = "autoCreateExpiryResources") boolean autoCreateExpiryResources,
                                        @Parameter(desc = "prefix to use on auto-create expiry queue", name = "expiryQueuePrefix") String expiryQueuePrefix,
                                        @Parameter(desc = "suffix to use on auto-create expiry queue", name = "expiryQueueSuffix") String expiryQueueSuffix,
                                        @Parameter(desc = "the min expiry delay setting", name = "minExpiryDelay") long minExpiryDelay,
                                        @Parameter(desc = "the max expiry delay setting", name = "maxExpiryDelay") long maxExpiryDelay,
                                        @Parameter(desc = "whether or not to enable metrics", name = "enableMetrics") boolean enableMetrics) throws Exception {
            proxy.invokeOperation("addAddressSettings",
                                  addressMatch,
                                  DLA,
                                  expiryAddress,
                                  expiryDelay,
                                  lastValueQueue,
                                  deliveryAttempts,
                                  maxSizeBytes,
                                  pageSizeBytes,
                                  pageMaxCacheSize,
                                  redeliveryDelay,
                                  redeliveryMultiplier,
                                  maxRedeliveryDelay,
                                  redistributionDelay,
                                  sendToDLAOnNoRoute,
                                  addressFullMessagePolicy,
                                  slowConsumerThreshold,
                                  slowConsumerCheckPeriod,
                                  slowConsumerPolicy,
                                  autoCreateJmsQueues,
                                  autoDeleteJmsQueues,
                                  autoCreateJmsTopics,
                                  autoDeleteJmsTopics,
                                  autoCreateQueues,
                                  autoDeleteQueues,
                                  autoCreateAddresses,
                                  autoDeleteAddresses,
                                  configDeleteQueues,
                                  configDeleteAddresses,
                                  maxSizeBytesRejectThreshold,
                                  defaultLastValueKey,
                                  defaultNonDestructive,
                                  defaultExclusiveQueue,
                                  defaultGroupRebalance,
                                  defaultGroupBuckets,
                                  defaultGroupFirstKey,
                                  defaultMaxConsumers,
                                  defaultPurgeOnNoConsumers,
                                  defaultConsumersBeforeDispatch,
                                  defaultDelayBeforeDispatch,
                                  defaultQueueRoutingType,
                                  defaultAddressRoutingType,
                                  defaultConsumerWindowSize,
                                  defaultRingSize,
                                  autoDeleteCreatedQueues,
                                  autoDeleteQueuesDelay,
                                  autoDeleteQueuesMessageCount,
                                  autoDeleteAddressesDelay,
                                  redeliveryCollisionAvoidanceFactor,
                                  retroactiveMessageCount,
                                  autoCreateDeadLetterResources,
                                  deadLetterQueuePrefix,
                                  deadLetterQueueSuffix,
                                  autoCreateExpiryResources,
                                  expiryQueuePrefix,
                                  expiryQueueSuffix,
                                  minExpiryDelay,
                                  maxExpiryDelay,
                                  enableMetrics);
         }

         @Override
         public String addAddressSettings(String address, String addressSettingsConfigurationAsJson) throws Exception {
            return (String) proxy.invokeOperation("addAddressSettings", address, addressSettingsConfigurationAsJson);
         }

         @Override
         public String listNetworkTopology() throws Exception {
            return (String) proxy.invokeOperation("listNetworkTopology");
         }

         @Override
         public String getAddressInfo(String address) throws ActiveMQAddressDoesNotExistException {
            return null;
         }

         @Override
         public String listBindingsForAddress(String address) throws Exception {
            return "";
         }

         @Override
         public void removeAddressSettings(String addressMatch) throws Exception {
            proxy.invokeOperation("removeAddressSettings", addressMatch);
         }

         @Override
         public void createDivert(String name,
                                  String routingName,
                                  String address,
                                  String forwardingAddress,
                                  boolean exclusive,
                                  String filterString,
                                  String transformerClassName) throws Exception {
            proxy.invokeOperation("createDivert", name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName);
         }

         @Override
         public void createDivert(String name,
                                  String routingName,
                                  String address,
                                  String forwardingAddress,
                                  boolean exclusive,
                                  String filterString,
                                  String transformerClassName,
                                  String routingType) throws Exception {
            proxy.invokeOperation("createDivert", name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName, routingType);
         }

         @Override
         public void createDivert(String name,
                                  String routingName,
                                  String address,
                                  String forwardingAddress,
                                  boolean exclusive,
                                  String filterString,
                                  String transformerClassName,
                                  Map<String, String> transformerProperties,
                                  String routingType) throws Exception {
            proxy.invokeOperation("createDivert", name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName, transformerProperties, routingType);
         }

         @Override
         public void createDivert(String name,
                                  String routingName,
                                  String address,
                                  String forwardingAddress,
                                  boolean exclusive,
                                  String filterString,
                                  String transformerClassName,
                                  String transformerPropertiesAsJSON,
                                  String routingType) throws Exception {
            proxy.invokeOperation("createDivert", name, routingName, address, forwardingAddress, exclusive, filterString, transformerClassName, transformerPropertiesAsJSON, routingType);
         }

         @Override
         public void createDivert(String divertConfiguration) throws Exception {
            proxy.invokeOperation("createDivert", divertConfiguration);
         }

         @Override
         public void updateDivert(String name,
                                  String forwardingAddress,
                                  String filterString,
                                  String transformerClassName,
                                  Map<String, String> transformerProperties,
                                  String routingType) throws Exception {
            proxy.invokeOperation("updateDivert", name, forwardingAddress, filterString, transformerClassName, transformerProperties, routingType);
         }

         @Override
         public void updateDivert(String divertConfiguration) throws Exception {
            proxy.invokeOperation("updateDivert", divertConfiguration);
         }

         @Override
         public void destroyDivert(String name) throws Exception {
            proxy.invokeOperation("destroyDivert", name);
         }

         @Override
         public String[] getBridgeNames() {
            return (String[]) proxy.retrieveAttributeValue("bridgeNames", String.class);
         }

         @Override
         public void destroyBridge(String name) throws Exception {
            proxy.invokeOperation("destroyBridge", name);

         }

         @Override
         public void createConnectorService(String name, String factoryClass, Map<String, Object> parameters) throws Exception {
            proxy.invokeOperation("createConnectorService", name, factoryClass, parameters);
         }

         @Override
         public void destroyConnectorService(String name) throws Exception {
            proxy.invokeOperation("destroyConnectorService", name);
         }

         @Override
         public String[] getConnectorServices() {
            return (String[]) proxy.retrieveAttributeValue("connectorServices", String.class);
         }

         @Override
         public void forceFailover() throws Exception {
            proxy.invokeOperation("forceFailover");
         }

         public String getPrimaryConnectorName() throws Exception {
            return (String) proxy.retrieveAttributeValue("liveConnectorName");
         }

         @Override
         public String getAddressSettingsAsJSON(String addressMatch) throws Exception {
            return (String) proxy.invokeOperation("getAddressSettingsAsJSON", addressMatch);
         }

         @Override
         public String[] getDivertNames() {
            return (String[]) proxy.retrieveAttributeValue("divertNames", String.class);
         }

         @Override
         public String[] listDivertNames() {
            return new String[0];
         }

         @Override
         public void createBridge(String name,
                                  String queueName,
                                  String forwardingAddress,
                                  String filterString,
                                  String transformerClassName,
                                  long retryInterval,
                                  double retryIntervalMultiplier,
                                  int initialConnectAttempts,
                                  int reconnectAttempts,
                                  boolean useDuplicateDetection,
                                  int confirmationWindowSize,
                                  int producerWindowSize,
                                  long clientFailureCheckPeriod,
                                  String connectorNames,
                                  boolean useDiscovery,
                                  boolean ha,
                                  String user,
                                  String password) throws Exception {
            proxy.invokeOperation("createBridge", name, queueName, forwardingAddress, filterString, transformerClassName, retryInterval, retryIntervalMultiplier, initialConnectAttempts, reconnectAttempts, useDuplicateDetection, confirmationWindowSize, producerWindowSize, clientFailureCheckPeriod, connectorNames, useDiscovery, ha, user, password);
         }

         @Override
         public void createBridge(String name,
                                  String queueName,
                                  String forwardingAddress,
                                  String filterString,
                                  String transformerClassName,
                                  Map<String, String> transformerProperties,
                                  long retryInterval,
                                  double retryIntervalMultiplier,
                                  int initialConnectAttempts,
                                  int reconnectAttempts,
                                  boolean useDuplicateDetection,
                                  int confirmationWindowSize,
                                  int producerWindowSize,
                                  long clientFailureCheckPeriod,
                                  String connectorNames,
                                  boolean useDiscovery,
                                  boolean ha,
                                  String user,
                                  String password) throws Exception {
            proxy.invokeOperation("createBridge", name, queueName, forwardingAddress, filterString, transformerClassName, transformerProperties, retryInterval, retryIntervalMultiplier, initialConnectAttempts, reconnectAttempts, useDuplicateDetection, confirmationWindowSize, producerWindowSize, clientFailureCheckPeriod, connectorNames, useDiscovery, ha, user, password);
         }

         @Override
         public void createBridge(String name,
                                  String queueName,
                                  String forwardingAddress,
                                  String filterString,
                                  String transformerClassName,
                                  String transformerPropertiesAsJSON,
                                  long retryInterval,
                                  double retryIntervalMultiplier,
                                  int initialConnectAttempts,
                                  int reconnectAttempts,
                                  boolean useDuplicateDetection,
                                  int confirmationWindowSize,
                                  int producerWindowSize,
                                  long clientFailureCheckPeriod,
                                  String connectorNames,
                                  boolean useDiscovery,
                                  boolean ha,
                                  String user,
                                  String password) throws Exception {
            proxy.invokeOperation("createBridge", name, queueName, forwardingAddress, filterString, transformerClassName, transformerPropertiesAsJSON, retryInterval, retryIntervalMultiplier, initialConnectAttempts, reconnectAttempts, useDuplicateDetection, confirmationWindowSize, producerWindowSize, clientFailureCheckPeriod, connectorNames, useDiscovery, ha, user, password);
         }

         @Override
         public void createBridge(String name,
                                  String queueName,
                                  String forwardingAddress,
                                  String filterString,
                                  String transformerClassName,
                                  long retryInterval,
                                  double retryIntervalMultiplier,
                                  int initialConnectAttempts,
                                  int reconnectAttempts,
                                  boolean useDuplicateDetection,
                                  int confirmationWindowSize,
                                  long clientFailureCheckPeriod,
                                  String connectorNames,
                                  boolean useDiscovery,
                                  boolean ha,
                                  String user,
                                  String password) throws Exception {
            proxy.invokeOperation("createBridge", name, queueName, forwardingAddress, filterString, transformerClassName, retryInterval, retryIntervalMultiplier, initialConnectAttempts, reconnectAttempts, useDuplicateDetection, confirmationWindowSize, clientFailureCheckPeriod, connectorNames, useDiscovery, ha, user, password);
         }

         @Override
         public void createBridge(String bridgeConfiguration) throws Exception {
            proxy.invokeOperation("createBridge", bridgeConfiguration);
         }

         @Override
         public void addConnector(String name, String url) throws Exception {
            proxy.invokeOperation("addConnector", name, url);
         }

         @Override
         public void removeConnector(String name) throws Exception {
            proxy.invokeOperation("removeConnector", name);
         }

         @Override
         public String listProducersInfoAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listProducersInfoAsJSON");
         }

         @Override
         public String listConsumersAsJSON(String connectionID) throws Exception {
            return (String) proxy.invokeOperation("listConsumersAsJSON", connectionID);
         }

         @Override
         public String listAllConsumersAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listAllConsumersAsJSON");
         }

         @Override
         public String listConnectionsAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listConnectionsAsJSON");
         }

         @Override
         public String listSessionsAsJSON(@Parameter(desc = "a connection ID", name = "connectionID") String connectionID) throws Exception {
            return (String) proxy.invokeOperation("listSessionsAsJSON", connectionID);
         }

         @Override
         public String listAllSessionsAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listAllSessionsAsJSON");
         }

         @Override
         public String listAddresses(@Parameter(name = "separator", desc = "Separator used on the string listing") String separator) throws Exception {
            return (String) proxy.invokeOperation("listAddresses", separator);
         }

         @Override
         public String listConnections(String filter, int page, int pageSize) throws Exception {
            return (String) proxy.invokeOperation("listConnections", filter, page, pageSize);
         }

         @Override
         public String listSessions(@Parameter(name = "Filter String") String filter,
                                    @Parameter(name = "Page Number") int page,
                                    @Parameter(name = "Page Size") int pageSize) throws Exception {
            return (String) proxy.invokeOperation("listSessions", filter, page, pageSize);
         }

         @Override
         public String listConsumers(@Parameter(name = "Options") String options,
                                     @Parameter(name = "Page Number") int page,
                                     @Parameter(name = "Page Size") int pageSize) throws Exception {
            return (String) proxy.invokeOperation("listConsumers", options, page, pageSize);
         }

         @Override
         public String listProducers(@Parameter(name = "Options") String options,
                                     @Parameter(name = "Page Number") int page,
                                     @Parameter(name = "Page Size") int pageSize) throws Exception {
            return (String) proxy.invokeOperation("listProducers", options, page, pageSize);
         }

         @Override
         public String listAddresses(@Parameter(name = "Options") String options,
                                     @Parameter(name = "Page Number") int page,
                                     @Parameter(name = "Page Size") int pageSize) throws Exception {
            return (String) proxy.invokeOperation("listAddresses", options, page, pageSize);
         }

         @Override
         public String listQueues(@Parameter(name = "Options") String options,
                                  @Parameter(name = "Page Number") int page,
                                  @Parameter(name = "Page Size") int pageSize) throws Exception {
            return (String) proxy.invokeOperation("listQueues", options, page, pageSize);
         }

         @Override
         public void replay(String address, String target, String filter) throws Exception {
            proxy.invokeOperation("replay", address, target, filter);
         }

         @Override
         public void replay(String startScan,
                            String endScan,
                            String address,
                            String target,
                            String filter) throws Exception {
            proxy.invokeOperation("replay", startScan, endScan, address, target, filter);
         }

         @Override
         public void stopEmbeddedWebServer() throws Exception {
            proxy.invokeOperation("stopEmbeddedWebServer");
         }

         @Override
         public void startEmbeddedWebServer() throws Exception {
            proxy.invokeOperation("startEmbeddedWebServer");
         }

         @Override
         public void restartEmbeddedWebServer() throws Exception {
            proxy.invokeOperation("restartEmbeddedWebServer");
         }

         @Override
         public void restartEmbeddedWebServer(long timeout) throws Exception {
            proxy.invokeOperation("restartEmbeddedWebServer", timeout);
         }

         @Override
         public boolean isEmbeddedWebServerStarted() {
            return (boolean) proxy.retrieveAttributeValue("embeddedWebServerStarted");
         }

         @Override
         public void rebuildPageCounters() throws Exception {
            proxy.invokeOperation("rebuildPageCounters");
         }

         @Override
         public void clearAuthenticationCache() throws Exception {
            proxy.invokeOperation("clearAuthenticationCache");
         }

         @Override
         public void clearAuthorizationCache() throws Exception {
            proxy.invokeOperation("clearAuthorizationCache");
         }

         @Override
         public long getAuthenticationSuccessCount() {
            return (long) proxy.retrieveAttributeValue("authenticationSuccessCount");
         }

         @Override
         public long getAuthenticationFailureCount() {
            return (long) proxy.retrieveAttributeValue("authenticationFailureCount");
         }

         @Override
         public long getAuthorizationSuccessCount() {
            return (long) proxy.retrieveAttributeValue("authorizationSuccessCount");
         }

         @Override
         public long getAuthorizationFailureCount() {
            return (long) proxy.retrieveAttributeValue("authorizationFailureCount");
         }


         @Override
         public int getPendingMirrorAcks() {
            return ((Number) proxy.retrieveAttributeValue("pendingMirrorAcks")).intValue();
         }

         @Override
         public void exportConfigAsProperties() throws Exception {
            proxy.invokeOperation("exportConfigAsProperties");
         }
      };
   }

   @Override
   public boolean usingCore() {
      return true;
   }
}
