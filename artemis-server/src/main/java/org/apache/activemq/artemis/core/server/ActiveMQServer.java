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
package org.apache.activemq.artemis.core.server;

import javax.management.MBeanServer;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.federation.FederationManager;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ConnectorsService;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQPluginRunnable;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBindingPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBridgePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConnectionPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConsumerPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerCriticalPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerFederationPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerQueuePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerResourcePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerSessionPlugin;
import org.apache.activemq.artemis.core.server.balancing.BrokerBalancerManager;
import org.apache.activemq.artemis.core.server.reload.ReloadManager;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;

/**
 * This interface defines the internal interface of the ActiveMQ Artemis Server exposed to other components
 * of the server.
 * <p>
 * This is not part of our public API.
 */
public interface ActiveMQServer extends ServiceComponent {

   enum SERVER_STATE {
      /**
       * start() has been called but components are not initialized. The whole point of this state,
       * is to be in a state which is different from {@link SERVER_STATE#STARTED} and
       * {@link SERVER_STATE#STOPPED}, so that methods testing for these two values such as
       * {@link #stop(boolean)} worked as intended.
       */
      STARTING, /**
       * server is started. {@code server.isStarted()} returns {@code true}, and all assumptions
       * about it hold.
       */
      STARTED, /**
       * stop() was called but has not finished yet. Meant to avoids starting components while
       * stop() is executing.
       */
      STOPPING, /**
       * Stopped: either stop() has been called and has finished running, or start() has never been
       * called.
       */
      STOPPED
   }


   void setState(SERVER_STATE state);

   SERVER_STATE getState();

   /**
    * Sets the server identity.
    * <p>
    * The identity will be exposed on logs. It may help to debug issues on the log traces and
    * debugs.
    * <p>
    * This method was created mainly for testing but it may be used in scenarios where you need to
    * have more than one Server inside the same VM.
    */
   void setIdentity(String identity);

   String getIdentity();

   String describe();

   void addActivationParam(String key, Object val);

   Configuration getConfiguration();

   void installMirrorController(MirrorController mirrorController);

   /** This method will scan all queues and addresses.
    * it is supposed to be called before the mirrorController is started */
   void scanAddresses(MirrorController mirrorController) throws Exception;

   MirrorController getMirrorController();

   void removeMirrorControl();

   ServiceRegistry getServiceRegistry();

   RemotingService getRemotingService();

   StorageManager getStorageManager();

   PagingManager getPagingManager();

   PagingManager createPagingManager() throws Exception;

   ManagementService getManagementService();

   ActiveMQSecurityManager getSecurityManager();

   NetworkHealthCheck getNetworkHealthCheck();

   Version getVersion();

   NodeManager getNodeManager();

   CriticalAnalyzer getCriticalAnalyzer();

   /**
    * it will release hold a lock for the activation.
    */
   void unlockActivation();

   /**
    * it will hold a lock for the activation. This will prevent the activation from happening.
    */
   void lockActivation();

   /**
    * Returns the resource to manage this ActiveMQ Artemis server.
    *
    * @throws IllegalStateException if the server is not properly started.
    */
   ActiveMQServerControlImpl getActiveMQServerControl();

   void registerActivateCallback(ActivateCallback callback);

   void unregisterActivateCallback(ActivateCallback callback);

   /**
    * Register a listener to detect problems during activation
    *
    * @param listener @see org.apache.activemq.artemis.core.server.ActivationFailureListener
    */
   void registerActivationFailureListener(ActivationFailureListener listener);

   void replay(Date start, Date end, String address, String target, String filter) throws Exception;

   /**
    * Remove a previously registered failure listener
    *
    * @param listener
    */
   void unregisterActivationFailureListener(ActivationFailureListener listener);

   /**
    * Alert activation failure listeners of a failure.
    *
    * @param e the exception that caused the activation failure
    */
   void callActivationFailureListeners(Exception e);

   /**
    * @param callback {@link org.apache.activemq.artemis.core.server.PostQueueCreationCallback}
    */
   void registerPostQueueCreationCallback(PostQueueCreationCallback callback);

   /**
    * @param callback {@link org.apache.activemq.artemis.core.server.PostQueueCreationCallback}
    */
   void unregisterPostQueueCreationCallback(PostQueueCreationCallback callback);

   /**
    * @param queueName
    */
   void callPostQueueCreationCallbacks(SimpleString queueName) throws Exception;

   /**
    * @param callback {@link org.apache.activemq.artemis.core.server.PostQueueDeletionCallback}
    */
   void registerPostQueueDeletionCallback(PostQueueDeletionCallback callback);

   /**
    * @param callback {@link org.apache.activemq.artemis.core.server.PostQueueDeletionCallback}
    */
   void unregisterPostQueueDeletionCallback(PostQueueDeletionCallback callback);

   /**
    * @param queueName
    */
   void callPostQueueDeletionCallbacks(SimpleString address, SimpleString queueName) throws Exception;

   void registerBrokerPlugin(ActiveMQServerBasePlugin plugin);

   void unRegisterBrokerPlugin(ActiveMQServerBasePlugin plugin);

   void registerBrokerPlugins(List<ActiveMQServerBasePlugin> plugins);

   List<ActiveMQServerBasePlugin> getBrokerPlugins();

   List<ActiveMQServerConnectionPlugin> getBrokerConnectionPlugins();

   List<ActiveMQServerSessionPlugin> getBrokerSessionPlugins();

   List<ActiveMQServerConsumerPlugin> getBrokerConsumerPlugins();

   List<ActiveMQServerAddressPlugin> getBrokerAddressPlugins();

   List<ActiveMQServerQueuePlugin> getBrokerQueuePlugins();

   List<ActiveMQServerBindingPlugin> getBrokerBindingPlugins();

   List<ActiveMQServerMessagePlugin> getBrokerMessagePlugins();

   List<ActiveMQServerBridgePlugin> getBrokerBridgePlugins();

   List<ActiveMQServerCriticalPlugin> getBrokerCriticalPlugins();

   List<ActiveMQServerFederationPlugin> getBrokerFederationPlugins();

   List<ActiveMQServerResourcePlugin> getBrokerResourcePlugins();

   void callBrokerPlugins(ActiveMQPluginRunnable pluginRun) throws ActiveMQException;

   void callBrokerConnectionPlugins(ActiveMQPluginRunnable<ActiveMQServerConnectionPlugin> pluginRun) throws ActiveMQException;

   void callBrokerSessionPlugins(ActiveMQPluginRunnable<ActiveMQServerSessionPlugin> pluginRun) throws ActiveMQException;

   void callBrokerConsumerPlugins(ActiveMQPluginRunnable<ActiveMQServerConsumerPlugin> pluginRun) throws ActiveMQException;

   void callBrokerAddressPlugins(ActiveMQPluginRunnable<ActiveMQServerAddressPlugin> pluginRun) throws ActiveMQException;

   void callBrokerQueuePlugins(ActiveMQPluginRunnable<ActiveMQServerQueuePlugin> pluginRun) throws ActiveMQException;

   void callBrokerBindingPlugins(ActiveMQPluginRunnable<ActiveMQServerBindingPlugin> pluginRun) throws ActiveMQException;

   void callBrokerMessagePlugins(ActiveMQPluginRunnable<ActiveMQServerMessagePlugin> pluginRun) throws ActiveMQException;

   boolean callBrokerMessagePluginsCanAccept(ServerConsumer serverConsumer,
                                             MessageReference messageReference) throws ActiveMQException;

   void callBrokerBridgePlugins(ActiveMQPluginRunnable<ActiveMQServerBridgePlugin> pluginRun) throws ActiveMQException;

   void callBrokerCriticalPlugins(ActiveMQPluginRunnable<ActiveMQServerCriticalPlugin> pluginRun) throws ActiveMQException;

   void callBrokerFederationPlugins(ActiveMQPluginRunnable<ActiveMQServerFederationPlugin> pluginRun) throws ActiveMQException;

   void callBrokerResourcePlugins(ActiveMQPluginRunnable<ActiveMQServerResourcePlugin> pluginRun) throws ActiveMQException;

   boolean hasBrokerPlugins();

   boolean hasBrokerConnectionPlugins();

   boolean hasBrokerSessionPlugins();

   boolean hasBrokerConsumerPlugins();

   boolean hasBrokerAddressPlugins();

   boolean hasBrokerQueuePlugins();

   boolean hasBrokerBindingPlugins();

   boolean hasBrokerMessagePlugins();

   boolean hasBrokerBridgePlugins();

   boolean hasBrokerCriticalPlugins();

   boolean hasBrokerFederationPlugins();

   boolean hasBrokerResourcePlugins();

   void checkQueueCreationLimit(String username) throws Exception;

   ServerSession createSession(String name,
                               String username,
                               String password,
                               int minLargeMessageSize,
                               RemotingConnection remotingConnection,
                               boolean autoCommitSends,
                               boolean autoCommitAcks,
                               boolean preAcknowledge,
                               boolean xa,
                               String defaultAddress,
                               SessionCallback callback,
                               boolean autoCreateQueues,
                               OperationContext context,
                               Map<SimpleString, RoutingType> prefixes,
                               String securityDomain) throws Exception;

   /** This is to be used in places where security is bypassed, like internal sessions, broker connections, etc... */
   ServerSession createInternalSession(String name,
                               int minLargeMessageSize,
                               RemotingConnection remotingConnection,
                               boolean autoCommitSends,
                               boolean autoCommitAcks,
                               boolean preAcknowledge,
                               boolean xa,
                               String defaultAddress,
                               SessionCallback callback,
                               boolean autoCreateQueues,
                               OperationContext context,
                               Map<SimpleString, RoutingType> prefixes,
                               String securityDomain) throws Exception;

   SecurityStore getSecurityStore();

   void removeSession(String name) throws Exception;

   Set<ServerSession> getSessions();

   HierarchicalRepository<Set<Role>> getSecurityRepository();

   HierarchicalRepository<AddressSettings> getAddressSettingsRepository();

   OperationContext newOperationContext();

   int getConnectionCount();

   long getTotalConnectionCount();

   long getTotalMessageCount();

   long getTotalMessagesAdded();

   long getTotalMessagesAcknowledged();

   long getTotalConsumerCount();

   PostOffice getPostOffice();

   void clearAddressCache();

   QueueFactory getQueueFactory();

   ResourceManager getResourceManager();

   MetricsManager getMetricsManager();

   List<ServerSession> getSessions(String connectionID);

   /**
    * @return a session containing the meta-key and meata-value
    */
   ServerSession lookupSession(String metakey, String metavalue);

   ClusterManager getClusterManager();

   SimpleString getNodeID();

   boolean isActive();

   String getUptime();

   long getUptimeMillis();

   /**
    * Returns whether the initial replication synchronization process with the backup server is complete; applicable for
    * either the live or backup server.
    */
   boolean isReplicaSync();

   /**
    * Wait for server initialization.
    *
    * @param timeout
    * @param unit
    * @return {@code true} if the server was already initialized or if it was initialized within the
    * timeout period, {@code false} otherwise.
    * @throws InterruptedException
    * @see java.util.concurrent.CountDownLatch#await(long, java.util.concurrent.TimeUnit)
    */
   boolean waitForActivation(long timeout, TimeUnit unit) throws InterruptedException;

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers.
    * The queue will be deleted as soon as all the consumers are removed.
    * <p>
    * Notice: the queue won't be deleted until the first consumer arrives.
    *
    * @param address
    * @param name
    * @param filterString
    * @param durable
    * @throws org.apache.activemq.artemis.api.core.ActiveMQInvalidTransientQueueUseException if the shared queue already exists with a different {@code address} or {@code filterString}
    * @throws NullPointerException                                                           if {@code address} is {@code null}
    */
   @Deprecated
   void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString name, SimpleString filterString,
                          SimpleString user, boolean durable) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString name, SimpleString filterString,
                          SimpleString user, boolean durable, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean lastValue) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString name, SimpleString filterString,
                          SimpleString user, boolean durable, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive,
                          boolean groupRebalance, int groupBuckets, boolean lastValue,
                          SimpleString lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch,
                          boolean autoDelete, long autoDeleteTimeout, long autoDeleteMessageCount) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     boolean durable, boolean temporary) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString user,
                               SimpleString filterString,  boolean durable, boolean temporary) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter, boolean durable, boolean temporary, int maxConsumers, boolean purgeOnNoConsumers, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     boolean durable, boolean temporary, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets,
                     boolean lastValue, SimpleString lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch,
                     boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     boolean durable, boolean temporary, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets, SimpleString groupFirstKey,
                     boolean lastValue, SimpleString lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch,
                     boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     boolean durable, boolean temporary, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance, int groupBuckets, SimpleString groupFirstKey,
                     boolean lastValue, SimpleString lastValueKey, boolean nonDestructive, int consumersBeforeDispatch, long delayBeforeDispatch,
                     boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress, long ringSize) throws Exception;


   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers,
                     Boolean purgeOnNoConsumers, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers,
                     Boolean purgeOnNoConsumers, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers,
                     Boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers,
                     Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, Boolean lastValue, SimpleString lastValueKey, Boolean nonDestructive,
                     Integer consumersBeforeDispatch, Long delayBeforeDispatch, Boolean autoDelete, Long autoDeleteDelay, Long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers,
                     Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, SimpleString groupFirstKey, Boolean lastValue, SimpleString lastValueKey, Boolean nonDestructive,
                     Integer consumersBeforeDispatch, Long delayBeforeDispatch, Boolean autoDelete, Long autoDeleteDelay, Long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers,
                     Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, SimpleString groupFirstKey, Boolean lastValue, SimpleString lastValueKey, Boolean nonDestructive,
                     Integer consumersBeforeDispatch, Long delayBeforeDispatch, Boolean autoDelete, Long autoDeleteDelay, Long autoDeleteMessageCount, boolean autoCreateAddress, Long ringSize) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                     boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                     boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean lastValue, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                     boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance,
                     int groupBuckets, boolean lastValue, SimpleString lastValueKey, boolean nonDestructive,
                     int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                     boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance,
                     int groupBuckets, SimpleString groupFirstKey, boolean lastValue, SimpleString lastValueKey, boolean nonDestructive,
                     int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                     boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean groupRebalance,
                     int groupBuckets, SimpleString groupFirstKey, boolean lastValue, SimpleString lastValueKey, boolean nonDestructive,
                     int consumersBeforeDispatch, long delayBeforeDispatch, boolean autoDelete, long autoDeleteDelay, long autoDeleteMessageCount, boolean autoCreateAddress, long ringSize) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address, SimpleString queueName, SimpleString filter, boolean durable, boolean temporary) throws Exception;

   @Deprecated
   Queue deployQueue(String address, String queue, String filter, boolean durable, boolean temporary) throws Exception;

   @Deprecated
   Queue deployQueue(SimpleString address, SimpleString queue, SimpleString filter, boolean durable, boolean temporary) throws Exception;

   /**
    * Overloaded version of {@link ActiveMQServer#createQueue(QueueConfiguration, boolean)} where the {@code boolean}
    * parameter is always {@code false} (i.e. if the queue already exists then an exception will be thrown).
    *
    * @see ActiveMQServer#createQueue(QueueConfiguration, boolean)
    */
   Queue createQueue(QueueConfiguration queueConfiguration) throws Exception;

   /**
    * This method creates a queue based on the {@link QueueConfiguration} input. See {@link QueueConfiguration} for more
    * details on configuration specifics.
    * <p>
    * Some dynamic defaults will be enforced via address-settings for the corresponding unset properties:
    * <p><ul>
    * <li>{@code maxConsumers}
    * <li>{@code exclusive}
    * <li>{@code groupRebalance}
    * <li>{@code groupBuckets}
    * <li>{@code groupFirstKey}
    * <li>{@code lastValue}
    * <li>{@code lastValueKey}
    * <li>{@code nonDestructive}
    * <li>{@code consumersBeforeDispatch}
    * <li>{@code delayBeforeDispatch}
    * <li>{@code ringSize}
    * <li>{@code routingType}
    * <li>{@code purgeOnNoConsumers}
    * <li>{@code autoCreateAddress}
    * <li>{@code autoDelete} (only set if queue was auto-created)
    * <li>{@code autoDeleteDelay}
    * <li>{@code autoDeleteMessageCount}
    * </ul><p>
    *
    * @param queueConfiguration the configuration to use when creating the queue
    * @param ignoreIfExists whether or not to simply return without an exception if the queue exists
    * @return the {@code Queue} instance that was created
    * @throws Exception
    */
   Queue createQueue(QueueConfiguration queueConfiguration, boolean ignoreIfExists) throws Exception;

   /**
    * This method is essentially the same as {@link #createQueue(QueueConfiguration, boolean)} with a few key exceptions.
    * <p>
    * If {@code durable} is {@code true} then:
    * <p><ul>
    * <li>{@code transient} will be forced to {@code false}
    * <li>{@code temporary} will be forced to {@code false}
    * </ul><p>
    * If {@code durable} is {@code false} then:
    * <p><ul>
    * <li>{@code transient} will be forced to {@code true}
    * <li>{@code temporary} will be forced to {@code true}
    * </ul><p>
    * In all instances {@code autoCreated} will be forced to {@code false} and {@code autoCreatedAddress} will be forced
    * to {@code true}.
    *
    * The {@code boolean} passed to {@link #createQueue(QueueConfiguration, boolean)} will always be true;
    *
    * @see #createQueue(QueueConfiguration, boolean)
    *
    * @throws org.apache.activemq.artemis.api.core.ActiveMQInvalidTransientQueueUseException if the shared queue already exists with a different {@code address} or {@code filterString}
    */
   void createSharedQueue(QueueConfiguration queueConfiguration) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address,
                          RoutingType routingType,
                          SimpleString name,
                          SimpleString filterString,
                          SimpleString user,
                          boolean durable,
                          int maxConsumers,
                          boolean purgeOnNoConsumers,
                          boolean exclusive,
                          boolean groupRebalance,
                          int groupBuckets,
                          SimpleString groupFirstKey,
                          boolean lastValue,
                          SimpleString lastValueKey,
                          boolean nonDestructive,
                          int consumersBeforeDispatch,
                          long delayBeforeDispatch,
                          boolean autoDelete,
                          long autoDeleteDelay,
                          long autoDeleteMessageCount) throws Exception;

   Queue locateQueue(SimpleString queueName);

   default Queue locateQueue(String queueName) {
      return locateQueue(SimpleString.toSimpleString(queueName));
   }

   default BindingQueryResult bindingQuery(SimpleString address) throws Exception {
      return bindingQuery(address, true);
   }

   BindingQueryResult bindingQuery(SimpleString address, boolean newFQQN) throws Exception;

   QueueQueryResult queueQuery(SimpleString name) throws Exception;

   AddressQueryResult addressQuery(SimpleString name) throws Exception;

   void destroyQueue(SimpleString queueName) throws Exception;

   void destroyQueue(SimpleString queueName, SecurityAuth session) throws Exception;

   void destroyQueue(SimpleString queueName, SecurityAuth session, boolean checkConsumerCount) throws Exception;

   void destroyQueue(SimpleString queueName,
                     SecurityAuth session,
                     boolean checkConsumerCount,
                     boolean removeConsumers) throws Exception;

   void destroyQueue(SimpleString queueName,
                     SecurityAuth session,
                     boolean checkConsumerCount,
                     boolean removeConsumers,
                     boolean forceAutoDeleteAddress) throws Exception;

   void destroyQueue(SimpleString queueName,
                     SecurityAuth session,
                     boolean checkConsumerCount,
                     boolean removeConsumers,
                     boolean forceAutoDeleteAddress,
                     boolean checkMessageCount) throws Exception;

   String destroyConnectionWithSessionMetadata(String metaKey, String metaValue) throws Exception;

   ScheduledExecutorService getScheduledPool();

   ExecutorFactory getExecutorFactory();

   ExecutorFactory getIOExecutorFactory();

   void setGroupingHandler(GroupingHandler groupingHandler);

   GroupingHandler getGroupingHandler();

   ReplicationManager getReplicationManager();

   FederationManager getFederationManager();

   Divert deployDivert(DivertConfiguration config) throws Exception;

   Divert updateDivert(DivertConfiguration config) throws Exception;

   void destroyDivert(SimpleString name) throws Exception;

   ConnectorsService getConnectorsService();

   void deployBridge(BridgeConfiguration config) throws Exception;

   void destroyBridge(String name) throws Exception;

   void deployFederation(FederationConfiguration config) throws Exception;

   void undeployFederation(String name) throws Exception;

   ServerSession getSessionByID(String sessionID);

   void threadDump();

   void registerBrokerConnection(BrokerConnection brokerConnection);

   void startBrokerConnection(String name) throws Exception;

   void stopBrokerConnection(String name) throws Exception;

   Collection<BrokerConnection> getBrokerConnections();

   /**
    * return true if there is a binding for this address (i.e. if there is a created queue)
    *
    * @param address
    * @return
    */
   boolean isAddressBound(String address) throws Exception;

   void fail(boolean failoverOnServerShutdown) throws Exception;

   void stop(boolean failoverOnServerShutdown, boolean isExit) throws Exception;

   @Deprecated
   Queue updateQueue(String name,
                     RoutingType routingType,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers) throws Exception;

   @Deprecated
   Queue updateQueue(String name,
                     RoutingType routingType,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers,
                     Boolean exclusive) throws Exception;

   @Deprecated
   Queue updateQueue(String name,
                     RoutingType routingType,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     String user) throws Exception;

   @Deprecated
   Queue updateQueue(String name,
                     RoutingType routingType,
                     String filterString,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean groupRebalance,
                     Integer groupBuckets,
                     Boolean nonDestructive,
                     Integer consumersBeforeDispatch,
                     Long delayBeforeDispatch,
                     String user) throws Exception;

   @Deprecated
   Queue updateQueue(String name,
                     RoutingType routingType,
                     String filterString,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean groupRebalance,
                     Integer groupBuckets,
                     String groupFirstQueue,
                     Boolean nonDestructive,
                     Integer consumersBeforeDispatch,
                     Long delayBeforeDispatch,
                     String user) throws Exception;

   @Deprecated
   Queue updateQueue(String name,
                     RoutingType routingType,
                     String filterString,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean groupRebalance,
                     Integer groupBuckets,
                     String groupFirstQueue,
                     Boolean nonDestructive,
                     Integer consumersBeforeDispatch,
                     Long delayBeforeDispatch,
                     String user,
                     Long ringSize) throws Exception;

   /**
    * Update the queue named in the {@code QueueConfiguration} with the corresponding properties. Set only the
    * properties that you wish to change from their existing values. Only the following properties can actually be
    * updated:
    * <p><ul>
    * <li>{@code routingType}
    * <li>{@code filter}
    * <li>{@code maxConsumers}
    * <li>{@code purgeOnNoConsumers}
    * <li>{@code exclusive}
    * <li>{@code nonDestructive}
    * <li>{@code groupRebalance}
    * <li>{@code groupFirstKey}
    * <li>{@code groupBuckets}
    * <li>{@code consumersBeforeDispatch}
    * <li>{@code delayBeforeDispatch}
    * <li>{@code configurationManaged}
    * <li>{@code user}
    * <li>{@code ringSize}
    * </ul>
    * The other configuration attributes are immutable and will be ignored if set.
    *
    * @param queueConfiguration the {@code QueueConfiguration} to use
    * @return the updated {@code Queue} instance
    * @throws Exception
    */
   Queue updateQueue(QueueConfiguration queueConfiguration) throws Exception;

   /**
    * @param queueConfiguration the {@code QueueConfiguration} to use
    * @param forceUpdate If <code>true</code>, no <code>null</code> check is performed and unset queueConfiguration values are reset to <code>null</code>
    * @return the updated {@code Queue} instance
    * @throws Exception
    * @see #updateQueue(QueueConfiguration)
    */
   Queue updateQueue(QueueConfiguration queueConfiguration, boolean forceUpdate) throws Exception;

   /*
            * add a ProtocolManagerFactory to be used. Note if @see Configuration#isResolveProtocols is tur then this factory will
            * replace any factories with the same protocol
            * */
   void addProtocolManagerFactory(ProtocolManagerFactory factory);

   /*
   * add a ProtocolManagerFactory to be used.
   * */
   void removeProtocolManagerFactory(ProtocolManagerFactory factory);

   ReloadManager getReloadManager();

   ActiveMQServer createBackupServer(Configuration configuration);

   void addScaledDownNode(SimpleString scaledDownNodeId);

   boolean hasScaledDown(SimpleString scaledDownNodeId);

   Activation getActivation();

   HAPolicy getHAPolicy();

   void setHAPolicy(HAPolicy haPolicy);

   void setMBeanServer(MBeanServer mBeanServer);

   MBeanServer getMBeanServer();

   void setSecurityManager(ActiveMQSecurityManager securityManager);

   /**
    * Adding external components is allowed only if the state
    * isn't {@link SERVER_STATE#STOPPED} or {@link SERVER_STATE#STOPPING}.<br>
    * It atomically starts the {@code externalComponent} while being added if {@code start == true}.<br>
    * This atomicity is necessary to prevent {@link #stop()} to stop the component right after adding it, but before
    * starting it.
    *
    * @throws IllegalStateException if the state is {@link SERVER_STATE#STOPPED} or {@link SERVER_STATE#STOPPING}
    */
   void addExternalComponent(ActiveMQComponent externalComponent, boolean start) throws Exception;

   List<ActiveMQComponent> getExternalComponents();

   boolean addClientConnection(String clientId, boolean unique);

   void removeClientConnection(String clientId);

   Executor getThreadPool();

   AddressInfo getAddressInfo(SimpleString address);

   /**
    * Updates an {@code AddressInfo} on the broker with the specified routing types.
    *
    * @param address the name of the {@code AddressInfo} to update
    * @param routingTypes the routing types to update the {@code AddressInfo} with
    * @return {@code true} if the {@code AddressInfo} was updated, {@code false} otherwise
    * @throws Exception
    */
   boolean updateAddressInfo(SimpleString address, EnumSet<RoutingType> routingTypes) throws Exception;

   @Deprecated
   boolean updateAddressInfo(SimpleString address, Collection<RoutingType> routingTypes) throws Exception;

   /**
    * Add the {@code AddressInfo} to the broker
    *
    * @param addressInfo the {@code AddressInfo} to add
    * @return {@code true} if the {@code AddressInfo} was added, {@code false} otherwise
    * @throws Exception
    */
   boolean addAddressInfo(AddressInfo addressInfo) throws Exception;

   /**
    * A convenience method to combine the functionality of {@code addAddressInfo} and {@code updateAddressInfo}. It will
    * add the {@code AddressInfo} object to the broker if it doesn't exist or update it if it does.
    *
    * @param addressInfo the {@code AddressInfo} to add or the info used to update the existing {@code AddressInfo}
    * @return the resulting {@code AddressInfo}
    * @throws Exception
    */
   AddressInfo addOrUpdateAddressInfo(AddressInfo addressInfo) throws Exception;

   /**
    * Remove an {@code AddressInfo} from the broker.
    *
    * @param address the {@code AddressInfo} to remove
    * @param auth authorization information; {@code null} is valid
    * @throws Exception
    */
   void removeAddressInfo(SimpleString address, SecurityAuth auth) throws Exception;

   /**
    * Remove an {@code AddressInfo} from the broker.
    *
    * @param address the {@code AddressInfo} to remove
    * @param auth authorization information; {@code null} is valid
    * @throws Exception
    */
   void autoRemoveAddressInfo(SimpleString address, SecurityAuth auth) throws Exception;

   /**
    * Remove an {@code AddressInfo} from the broker.
    *
    * @param address the {@code AddressInfo} to remove
    * @param auth authorization information; {@code null} is valid
    * @param force It will disconnect everything from the address including queues and consumers
    * @throws Exception
    */
   void removeAddressInfo(SimpleString address, SecurityAuth auth, boolean force) throws Exception;

   String getInternalNamingPrefix();

   double getDiskStoreUsage();

   void reloadConfigurationFile() throws Exception;

   BrokerBalancerManager getBalancerManager();
}
