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
package org.apache.activemq.artemis.api.core.management;

import javax.management.MBeanOperationInfo;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;

/**
 * An ActiveMQServerControl is used to manage ActiveMQ Artemis servers.
 */
public interface ActiveMQServerControl {
   String CONNECTION_COUNT_DESCRIPTION = "Number of clients connected to this server";
   String TOTAL_CONNECTION_COUNT_DESCRIPTION = "Number of clients which have connected to this server since it was started";
   String ADDRESS_MEMORY_USAGE_DESCRIPTION = "Memory used by all the addresses on broker for in-memory messages";
   String ADDRESS_MEMORY_USAGE_PERCENTAGE_DESCRIPTION = "Memory used by all the addresses on broker as a percentage of the global-max-size";
   String DISK_STORE_USAGE_DESCRIPTION = "Fraction of total disk store used";

   /**
    * Returns this server's version.
    */
   @Attribute(desc = "Server's version")
   String getVersion();


   @Attribute(desc = "Server is active")
   boolean isActive();

   /**
    * Returns the number of clients connected to this server.
    */
   @Attribute(desc = CONNECTION_COUNT_DESCRIPTION)
   int getConnectionCount();

   /**
    * Returns the number of clients which have connected to this server since it was started.
    */
   @Attribute(desc = TOTAL_CONNECTION_COUNT_DESCRIPTION)
   long getTotalConnectionCount();

   /**
    * Returns the number of messages in all queues on the server.
    */
   @Attribute(desc = "Number of messages in all queues on the server")
   long getTotalMessageCount();

   /**
    * Returns the number of messages sent to this server since it was started.
    */
   @Attribute(desc = "Number of messages sent to this server since it was started")
   long getTotalMessagesAdded();

   /**
    * Returns the number of messages sent to this server since it was started.
    */
   @Attribute(desc = "Number of messages acknowledged from all the queues on this server since it was started")
   long getTotalMessagesAcknowledged();

   /**
    * Returns the number of messages sent to this server since it was started.
    */
   @Attribute(desc = "Number of consumers consuming messages from all the queues on this server")
   long getTotalConsumerCount();

   /**
    * Return whether this server is started.
    */
   @Attribute(desc = "Whether this server is started")
   boolean isStarted();

   /**
    * Returns the list of interceptors used by this server for incoming messages.
    *
    * @see org.apache.activemq.artemis.api.core.Interceptor
    */
   @Attribute(desc = "List of interceptors used by this server for incoming messages")
   String[] getIncomingInterceptorClassNames();

   /**
    * Returns the list of interceptors used by this server for outgoing messages.
    *
    * @see org.apache.activemq.artemis.api.core.Interceptor
    */
   @Attribute(desc = "List of interceptors used by this server for outgoing messages")
   String[] getOutgoingInterceptorClassNames();

   /**
    * Returns whether this server is clustered.
    */
   @Attribute(desc = "Whether this server is clustered")
   boolean isClustered();

   /**
    * Returns the maximum number of threads in the <em>scheduled</em> thread pool.
    */
   @Attribute(desc = "Maximum number of threads in the scheduled thread pool")
   int getScheduledThreadPoolMaxSize();

   /**
    * Returns the maximum number of threads in the thread pool.
    */
   @Attribute(desc = "Maximum number of threads in the thread pool")
   int getThreadPoolMaxSize();

   /**
    * Returns the interval time (in milliseconds) to invalidate security credentials.
    */
   @Attribute(desc = "Interval time (in milliseconds) to invalidate security credentials")
   long getSecurityInvalidationInterval();

   /**
    * Returns whether security is enabled for this server.
    */
   @Attribute(desc = "Whether security is enabled for this server")
   boolean isSecurityEnabled();

   /**
    * Returns the file system directory used to store bindings.
    */
   @Attribute(desc = "File system directory used to store bindings")
   String getBindingsDirectory();

   /**
    * Returns the file system directory used to store journal log.
    */
   @Attribute(desc = "File system directory used to store journal log")
   String getJournalDirectory();

   /**
    * Returns the type of journal used by this server (either {@code NIO} or {@code ASYNCIO}).
    */
   @Attribute(desc = "Type of journal used by this server")
   String getJournalType();

   /**
    * Returns whether the journal is synchronized when receiving transactional data.
    */
   @Attribute(desc = "Whether the journal is synchronized when receiving transactional data")
   boolean isJournalSyncTransactional();

   /**
    * Returns whether the journal is synchronized when receiving non-transactional data.
    */
   @Attribute(desc = "Whether the journal is synchronized when receiving non-transactional datar")
   boolean isJournalSyncNonTransactional();

   /**
    * Returns the size (in bytes) of each journal files.
    */
   @Attribute(desc = "Size (in bytes) of each journal files")
   int getJournalFileSize();

   /**
    * Returns the number of journal files to pre-create.
    */
   @Attribute(desc = "Number of journal files to pre-create")
   int getJournalMinFiles();

   /**
    * Returns the maximum number of write requests that can be in the AIO queue at any given time.
    */
   @Attribute(desc = "Maximum number of write requests that can be in the AIO queue at any given time")
   int getJournalMaxIO();

   /**
    * Returns the size of the internal buffer on the journal.
    */
   @Attribute(desc = "Size of the internal buffer on the journal")
   int getJournalBufferSize();

   /**
    * Number of files that would be acceptable to keep on a pool
    */
   @Attribute(desc = "Number of files that would be acceptable to keep on a pool")
   int getJournalPoolFiles();

   /**
    * Returns the timeout (in nanoseconds) used to flush internal buffers on the journal.
    */
   @Attribute(desc = "Timeout (in nanoseconds) used to flush internal buffers on the journal")
   int getJournalBufferTimeout();

   /**
    * do any clients failover on a server shutdown
    */
   @Attribute(desc = "If clients failover on a server shutdown")
   void setFailoverOnServerShutdown(boolean failoverOnServerShutdown) throws Exception;

   /**
    * returns if clients failover on a server shutdown
    */
   @Attribute(desc = "If clients failover on a server shutdown")
   boolean isFailoverOnServerShutdown();

   /**
    * Returns the minimal number of journal files before compacting.
    */
   @Attribute(desc = "Minimal number of journal files before compacting")
   int getJournalCompactMinFiles();

   /**
    * Return the percentage of live data before compacting the journal.
    */
   @Attribute(desc = "Percentage of live data before compacting the journal")
   int getJournalCompactPercentage();

   /**
    * Returns whether this server is using persistence and store data.
    */
   @Attribute(desc = "Whether this server is using persistence and store data")
   boolean isPersistenceEnabled();

   /**
    * Returns whether the bindings directory is created on this server startup.
    */
   @Attribute(desc = "Whether the bindings directory is created on this server startup")
   boolean isCreateBindingsDir();

   /**
    * Returns whether the journal directory is created on this server startup.
    */
   @Attribute(desc = "Whether the journal directory is created on this server startup")
   boolean isCreateJournalDir();

   /**
    * Returns whether message counter is enabled for this server.
    */
   @Attribute(desc = "Whether message counter is enabled for this server")
   boolean isMessageCounterEnabled();

   /**
    * Returns the maximum number of days kept in memory for message counter.
    */
   @Attribute(desc = "Maximum number of days kept in memory for message counter")
   int getMessageCounterMaxDayCount();

   /**
    * Sets the maximum number of days kept in memory for message counter.
    *
    * @param count value must be greater than 0
    */
   @Attribute(desc = "Maximum number of days kept in memory for message counter")
   void setMessageCounterMaxDayCount(int count) throws Exception;

   /**
    * Returns the sample period (in milliseconds) to take message counter snapshot.
    */
   @Attribute(desc = "Sample period (in milliseconds) to take message counter snapshot")
   long getMessageCounterSamplePeriod();

   /**
    * Sets the sample period to take message counter snapshot.
    *
    * @param newPeriod value must be greater than 1000ms
    */
   @Attribute(desc = "Sample period to take message counter snapshot")
   void setMessageCounterSamplePeriod(long newPeriod) throws Exception;

   /**
    * Returns {@code true} if this server is a backup, {@code false} if it is a live server.
    * <br>
    * If a backup server has been activated, returns {@code false}.
    */
   @Attribute(desc = "Whether this server is a backup")
   boolean isBackup();

   /**
    * Returns whether this server shares its data store with a corresponding live or backup server.
    */
   @Attribute(desc = "Whether this server shares its data store with a corresponding live or backup serve")
   boolean isSharedStore();

   /**
    * Returns the file system directory used to store paging files.
    */
   @Attribute(desc = "File system directory used to store paging files")
   String getPagingDirectory();

   /**
    * Returns whether delivery count is persisted before messages are delivered to the consumers.
    */
   @Attribute(desc = "Whether delivery count is persisted before messages are delivered to the consumers")
   boolean isPersistDeliveryCountBeforeDelivery();

   /**
    * Returns the connection time to live.
    * <br>
    * This value overrides the connection time to live <em>sent by the client</em>.
    */
   @Attribute(desc = "Connection time to live")
   long getConnectionTTLOverride();

   /**
    * Returns the management address of this server.
    * <br>
    * Clients can send management messages to this address to manage this server.
    */
   @Attribute(desc = "Management address of this server")
   String getManagementAddress();

   /**
    * Returns the node ID of this server.
    * <br>
    * Clients can send management messages to this address to manage this server.
    */
   @Attribute(desc = "Node ID of this server")
   String getNodeID();


   /**
    * Returns the current activation sequence number of this server.
    * <br>
    * When replicated, peers may coordinate activation with this monotonic sequence
    */
   @Attribute(desc = "Activation sequence of this server instance")
   long getActivationSequence();

   /**
    * Returns the management notification address of this server.
    * <br>
    * Clients can bind queues to this address to receive management notifications emitted by this server.
    */
   @Attribute(desc = "Management notification address of this server")
   String getManagementNotificationAddress();

   /**
    * Returns the size of the cache for pre-creating message IDs.
    */
   @Attribute(desc = "Size of the cache for pre-creating message IDs")
   int getIDCacheSize();

   /**
    * Returns whether message ID cache is persisted.
    */
   @Attribute(desc = "Whether message ID cache is persisted")
   boolean isPersistIDCache();

   /**
    * Returns the file system directory used to store large messages.
    */
   @Attribute(desc = "File system directory used to store large messages")
   String getLargeMessagesDirectory();

   /**
    * Returns whether wildcard routing is supported by this server.
    */
   @Attribute(desc = "Whether wildcard routing is supported by this server")
   boolean isWildcardRoutingEnabled();

   /**
    * Returns the timeout (in milliseconds) after which transactions is removed
    * from the resource manager after it was created.
    */
   @Attribute(desc = "Timeout (in milliseconds) after which transactions is removed from the resource manager after it was created")
   long getTransactionTimeout();

   /**
    * Returns the frequency (in milliseconds)  to scan transactions to detect which transactions
    * have timed out.
    */
   @Attribute(desc = "Frequency (in milliseconds)  to scan transactions to detect which transactions have timed out")
   long getTransactionTimeoutScanPeriod();

   /**
    * Returns the frequency (in milliseconds)  to scan messages to detect which messages
    * have expired.
    */
   @Attribute(desc = "Frequency (in milliseconds)  to scan messages to detect which messages have expired")
   long getMessageExpiryScanPeriod();

   /**
    * Returns the priority of the thread used to scan message expiration.
    */
   @Attribute(desc = "Priority of the thread used to scan message expiration")
   @Deprecated
   long getMessageExpiryThreadPriority();

   /**
    * Returns whether code coming from connection is executed asynchronously or not.
    */
   @Attribute(desc = "Whether code coming from connection is executed asynchronously or not")
   boolean isAsyncConnectionExecutionEnabled();

   /**
    * Returns the connectors configured for this server.
    */
   @Attribute(desc = "Connectors configured for this server")
   Object[] getConnectors() throws Exception;

   /**
    * Returns the connectors configured for this server using JSON serialization.
    */
   @Attribute(desc = "Connectors configured for this server using JSON serialization")
   String getConnectorsAsJSON() throws Exception;

   /**
    * Returns the number of addresses created on this server.
    */
   @Attribute(desc = "Number of addresses created on this server")
   int getAddressCount();

   /**
    * Returns the names of the addresses created on this server.
    */
   @Attribute(desc = "Names of the addresses created on this server")
   String[] getAddressNames();

   /**
    * Returns the number of queues created on this server.
    */
   @Attribute(desc = "Number of queues created on this server")
   int getQueueCount();

   /**
    * Returns the names of the queues created on this server.
    */
   @Attribute(desc = "Names of the queues created on this server")
   String[] getQueueNames();

   /**
    * Returns the uptime of this server.
    */
   @Attribute(desc = "Uptime of this server")
   String getUptime();

   /**
    * Returns the uptime of this server.
    */
   @Attribute(desc = "Uptime of this server in milliseconds")
   long getUptimeMillis();

   /**
    * Returns whether the initial replication synchronization process with the backup server is complete; applicable for
    * either the live or backup server.
    */
   @Attribute(desc = "Whether the initial replication synchronization process with the backup server is complete")
   boolean isReplicaSync();

   /**
    * Returns how often the server checks for disk space usage.
    */
   @Attribute(desc = "How often to check for disk space usage, in milliseconds")
   int getDiskScanPeriod();

   /**
    * Returns the disk use max limit.
    */
   @Attribute(desc = "Maximum limit for disk use, in percentage")
   int getMaxDiskUsage();

   /**
    * Returns the global max bytes limit for in-memory messages.
    */
   @Attribute(desc = "Global maximum limit for in-memory messages, in bytes")
   long getGlobalMaxSize();

   /**
    * Returns the  memory used by all the addresses on broker for in-memory messages
    */
   @Attribute(desc = ADDRESS_MEMORY_USAGE_DESCRIPTION)
   long getAddressMemoryUsage();

   /**
    * Returns the percentage of total disk store use
    */
   @Attribute(desc = DISK_STORE_USAGE_DESCRIPTION)
   double getDiskStoreUsage();

   /**
    * Returns the memory used by all the addresses on broker as a percentage of the global-max-size
    */
   @Attribute(desc = ADDRESS_MEMORY_USAGE_PERCENTAGE_DESCRIPTION)
   int getAddressMemoryUsagePercentage();

   @Attribute(desc = "Returns the HA Policy of this broker as a String")
   String getHAPolicy();

   /**
    * Returns the runtime size of the authentication cache
    */
   @Attribute(desc = "The runtime size of the authentication cache")
   long getAuthenticationCacheSize();

   /**
    * Returns the runtime size of the authorization cache
    */
   @Attribute(desc = "The runtime size of the authorization cache")
   long getAuthorizationCacheSize();

   // Operations ----------------------------------------------------
   @Operation(desc = "Isolate the broker", impact = MBeanOperationInfo.ACTION)
   boolean freezeReplication();

   @Operation(desc = "Create an address", impact = MBeanOperationInfo.ACTION)
   String createAddress(@Parameter(name = "name", desc = "The name of the address") String name,
                        @Parameter(name = "routingTypes", desc = "Comma separated list of Routing Types (ANYCAST/MULTICAST)") String routingTypes) throws Exception;

   @Operation(desc = "Update an address", impact = MBeanOperationInfo.ACTION)
   String updateAddress(@Parameter(name = "name", desc = "The name of the address") String name,
                        @Parameter(name = "routingTypes", desc = "Comma separated list of Routing Types (ANYCAST/MULTICAST)") String routingTypes) throws Exception;

   @Operation(desc = "Delete an address", impact = MBeanOperationInfo.ACTION)
   void deleteAddress(@Parameter(name = "name", desc = "The name of the address") String name) throws Exception;

   @Operation(desc = "Delete an address", impact = MBeanOperationInfo.ACTION)
   void deleteAddress(@Parameter(name = "name", desc = "The name of the address") String name,
                      @Parameter(name = "force", desc = "Force consumers and queues out") boolean force) throws Exception;

   /**
    * Create a durable queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address address to bind the queue to
    * @param name    name of the queue
    */
   @Deprecated
   @Operation(desc = "Create a queue with the specified address", impact = MBeanOperationInfo.ACTION)
   void createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name) throws Exception;

   /**
    * Create a durable queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address     address to bind the queue to
    * @param name        name of the queue
    * @param routingType The routing type used for this address, MULTICAST or ANYCAST
    */
   @Deprecated
   @Operation(desc = "Create a queue with the specified address", impact = MBeanOperationInfo.ACTION)
   void createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address address to bind the queue to
    * @param name    name of the queue
    * @param durable whether the queue is durable
    */
   @Deprecated
   @Operation(desc = "Create a queue with the specified address, name and durability", impact = MBeanOperationInfo.ACTION)
   void createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address     address to bind the queue to
    * @param name        name of the queue
    * @param durable     whether the queue is durable
    * @param routingType The routing type used for this address, MULTICAST or ANYCAST
    */
   @Deprecated
   @Operation(desc = "Create a queue with the specified address, name and durability", impact = MBeanOperationInfo.ACTION)
   void createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                    @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType) throws Exception;


   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address address to bind the queue to
    * @param name    name of the queue
    * @param filter  of the queue
    * @param durable whether the queue is durable
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   void createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "filter", desc = "Filter of the queue") String filter,
                    @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address     address to bind the queue to
    * @param name        name of the queue
    * @param filter      of the queue
    * @param durable     whether the queue is durable
    * @param routingType The routing type used for this address, MULTICAST or ANYCAST
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   void createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "filter", desc = "Filter of the queue") String filter,
                    @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                    @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address            address to bind the queue to
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param name               name of the queue
    * @param filterStr          filter of the queue
    * @param durable            is the queue durable?
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive if the queue should route exclusively to one consumer
    * @param lastValue use last-value semantics
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param autoCreateAddress  create an address with default values should a matching address not be found
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "filter", desc = "Filter of the queue") String filterStr,
                      @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") int maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") int groupBuckets,
                      @Parameter(name = "lastValue", desc = "Use last-value semantics") boolean lastValue,
                      @Parameter(name = "lastValueKey", desc = "Use the specified property key for the last value") String lastValueKey,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") int consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") long delayBeforeDispatch,
                      @Parameter(name = "autoCreateAddress", desc = "Create an address with default values should a matching address not be found") boolean autoCreateAddress) throws Exception;


   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address            address to bind the queue to
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param name               name of the queue
    * @param filterStr          filter of the queue
    * @param durable            is the queue durable?
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive if the queue should route exclusively to one consumer
    * @param lastValue use last-value semantics
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param autoCreateAddress  create an address with default values should a matching address not be found
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "filter", desc = "Filter of the queue") String filterStr,
                      @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") int maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") int groupBuckets,
                      @Parameter(name = "lastValue", desc = "Use last-value semantics") boolean lastValue,
                      @Parameter(name = "lastValueKey", desc = "Use the specified property key for the last value") String lastValueKey,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") int consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") long delayBeforeDispatch,
                      @Parameter(name = "autoDelete", desc = "If the queue should be deleted once no consumers") boolean autoDelete,
                      @Parameter(name = "autoDeleteDelay", desc = "How long to wait (in milliseconds) before deleting auto-created queues after the queue has 0 consumers") long autoDeleteDelay,
                      @Parameter(name = "autoDeleteMessageCount", desc = "The message count the queue must be at or below before it can be evaluated to be auto deleted, 0 waits until empty queue (default) and -1 disables this check") long autoDeleteMessageCount,
                      @Parameter(name = "autoCreateAddress", desc = "Create an address with default values should a matching address not be found") boolean autoCreateAddress) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address            address to bind the queue to
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param name               name of the queue
    * @param filterStr          filter of the queue
    * @param durable            is the queue durable?
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive if the queue should route exclusively to one consumer
    * @param lastValue use last-value semantics
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param autoCreateAddress  create an address with default values should a matching address not be found
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "filter", desc = "Filter of the queue") String filterStr,
                      @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") int maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") int groupBuckets,
                      @Parameter(name = "groupFirstKey", desc = "Key used to mark a message is first in a group for a consumer") String groupFirstKey,
                      @Parameter(name = "lastValue", desc = "Use last-value semantics") boolean lastValue,
                      @Parameter(name = "lastValueKey", desc = "Use the specified property key for the last value") String lastValueKey,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") int consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") long delayBeforeDispatch,
                      @Parameter(name = "autoDelete", desc = "If the queue should be deleted once no consumers") boolean autoDelete,
                      @Parameter(name = "autoDeleteDelay", desc = "How long to wait (in milliseconds) before deleting auto-created queues after the queue has 0 consumers") long autoDeleteDelay,
                      @Parameter(name = "autoDeleteMessageCount", desc = "The message count the queue must be at or below before it can be evaluated to be auto deleted, 0 waits until empty queue (default) and -1 disables this check") long autoDeleteMessageCount,
                      @Parameter(name = "autoCreateAddress", desc = "Create an address with default values should a matching address not be found") boolean autoCreateAddress) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address            address to bind the queue to
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param name               name of the queue
    * @param filterStr          filter of the queue
    * @param durable            is the queue durable?
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive          if the queue should route exclusively to one consumer
    * @param lastValue          use last-value semantics
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param autoCreateAddress  create an address with default values should a matching address not be found
    * @param ringSize           the size this queue should maintain according to ring semantics
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "filter", desc = "Filter of the queue") String filterStr,
                      @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") int maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") int groupBuckets,
                      @Parameter(name = "groupFirstKey", desc = "Key used to mark a message is first in a group for a consumer") String groupFirstKey,
                      @Parameter(name = "lastValue", desc = "Use last-value semantics") boolean lastValue,
                      @Parameter(name = "lastValueKey", desc = "Use the specified property key for the last value") String lastValueKey,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") int consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") long delayBeforeDispatch,
                      @Parameter(name = "autoDelete", desc = "If the queue should be deleted once no consumers") boolean autoDelete,
                      @Parameter(name = "autoDeleteDelay", desc = "How long to wait (in milliseconds) before deleting auto-created queues after the queue has 0 consumers") long autoDeleteDelay,
                      @Parameter(name = "autoDeleteMessageCount", desc = "The message count the queue must be at or below before it can be evaluated to be auto deleted, 0 waits until empty queue (default) and -1 disables this check") long autoDeleteMessageCount,
                      @Parameter(name = "autoCreateAddress", desc = "Create an address with default values should a matching address not be found") boolean autoCreateAddress,
                      @Parameter(name = "ringSize", desc = "The size this queue should maintain according to ring semantics") long ringSize) throws Exception;

   /**
    * Create a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exits.
    *
    * @param address            address to bind the queue to
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param name               name of the queue
    * @param filterStr          filter of the queue
    * @param durable            is the queue durable?
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param autoCreateAddress  create an address with default values should a matching address not be found
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "filter", desc = "Filter of the queue") String filterStr,
                      @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") int maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") boolean purgeOnNoConsumers,
                      @Parameter(name = "autoCreateAddress", desc = "Create an address with default values should a matching address not be found") boolean autoCreateAddress) throws Exception;
   /**
    * Create a queue.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exists.
    *
    * @param queueConfiguration the configuration of the queue in JSON format
    * @return the configuration of the created queue in JSON format
    * @throws Exception
    */
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "queueConfiguration", desc = "the configuration of the queue in JSON format") String queueConfiguration) throws Exception;

   /**
    * Create a queue.
    * <br>
    * This method throws a {@link org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException}) exception if the queue already exists and {@code ignoreIfExists} is {@code false}.
    *
    * @param queueConfiguration the configuration of the queue in JSON format
    * @param ignoreIfExists     whether or not to simply return without an exception if the queue exists
    * @return the configuration of the created queue in JSON format
    * @throws Exception
    */
   @Operation(desc = "Create a queue", impact = MBeanOperationInfo.ACTION)
   String createQueue(@Parameter(name = "queueConfiguration", desc = "the configuration of the queue in JSON format") String queueConfiguration,
                      @Parameter(name = "ignoreIfExists", desc = "whether or not to try to create the queue if it exists already") boolean ignoreIfExists) throws Exception;


   /**
    * Update a queue.
    *
    * @param queueConfiguration the configuration of the queue in JSON format
    * @return the configuration of the created queue in JSON format
    * @throws Exception
    */
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "queueConfiguration", desc = "the configuration of the queue in JSON format") String queueConfiguration) throws Exception;

   /**
    * Update a queue.
    *
    * @param name               name of the queue
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers) throws Exception;

   /**
    * Update a queue.
    *
    * @param name               name of the queue
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive          if the queue should route exclusively to one consumer
    * @return a textual summary of the queue
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive) throws Exception;

   /**
    * Update a queue
    *
    * @param name               name of the queue
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive          if the queue should route exclusively to one consumer
    * @param user               the user associated with this queue
    * @return
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive,
                      @Parameter(name = "user", desc = "The user associated with this queue") String user) throws Exception;

   /**
    * Update a queue
    *
    * @param name               name of the queue
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param filter             the filter to use on the queue
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive          if the queue should route exclusively to one consumer
    * @param groupRebalance     if the queue should rebalance groups when a consumer is added
    * @param groupBuckets       number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead
    * @param nonDestructive     If the queue is non-destructive
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param user               the user associated with this queue
    * @return
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "filter", desc = "The filter to use on the queue") String filter,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") Boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") Integer groupBuckets,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") Boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") Integer consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") Long delayBeforeDispatch,
                      @Parameter(name = "user", desc = "The user associated with this queue") String user) throws Exception;

   /**
    * Update a queue
    *
    * @param name               name of the queue
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param filter             the filter to use on the queue
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive          if the queue should route exclusively to one consumer
    * @param groupRebalance     if the queue should rebalance groups when a consumer is added
    * @param groupBuckets       number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead
    * @param groupFirstKey      key used to mark a message is first in a group for a consumer
    * @param nonDestructive     If the queue is non-destructive
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param user               the user associated with this queue
    * @return
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "filter", desc = "The filter to use on the queue") String filter,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") Boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") Integer groupBuckets,
                      @Parameter(name = "groupFirstKey", desc = "Key used to mark a message is first in a group for a consumer") String groupFirstKey,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") Boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") Integer consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") Long delayBeforeDispatch,
                      @Parameter(name = "user", desc = "The user associated with this queue") String user) throws Exception;

   /**
    * Update a queue
    *
    * @param name               name of the queue
    * @param routingType        the routing type used for this address, {@code MULTICAST} or {@code ANYCAST}
    * @param filter             the filter to use on the queue
    * @param maxConsumers       the maximum number of consumers allowed on this queue at any one time
    * @param purgeOnNoConsumers delete this queue when the last consumer disconnects
    * @param exclusive          if the queue should route exclusively to one consumer
    * @param groupRebalance     if the queue should rebalance groups when a consumer is added
    * @param groupBuckets       number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead
    * @param groupFirstKey      key used to mark a message is first in a group for a consumer
    * @param nonDestructive     If the queue is non-destructive
    * @param consumersBeforeDispatch number of consumers needed before dispatch can start
    * @param delayBeforeDispatch delay to wait before dispatching if number of consumers before dispatch is not met
    * @param user               the user associated with this queue
    * @param ringSize           the size this queue should maintain according to ring semantics
    * @return
    * @throws Exception
    */
   @Deprecated
   @Operation(desc = "Update a queue", impact = MBeanOperationInfo.ACTION)
   String updateQueue(@Parameter(name = "name", desc = "Name of the queue") String name,
                      @Parameter(name = "routingType", desc = "The routing type used for this address, MULTICAST or ANYCAST") String routingType,
                      @Parameter(name = "filter", desc = "The filter to use on the queue") String filter,
                      @Parameter(name = "maxConsumers", desc = "The maximum number of consumers allowed on this queue at any one time") Integer maxConsumers,
                      @Parameter(name = "purgeOnNoConsumers", desc = "Delete this queue when the last consumer disconnects") Boolean purgeOnNoConsumers,
                      @Parameter(name = "exclusive", desc = "If the queue should route exclusively to one consumer") Boolean exclusive,
                      @Parameter(name = "groupRebalance", desc = "If the queue should rebalance groups when a consumer is added") Boolean groupRebalance,
                      @Parameter(name = "groupBuckets", desc = "Number of buckets that should be used for message groups, -1 (default) is unlimited, and groups by raw key instead") Integer groupBuckets,
                      @Parameter(name = "groupFirstKey", desc = "Key used to mark a message is first in a group for a consumer") String groupFirstKey,
                      @Parameter(name = "nonDestructive", desc = "If the queue is non-destructive") Boolean nonDestructive,
                      @Parameter(name = "consumersBeforeDispatch", desc = "Number of consumers needed before dispatch can start") Integer consumersBeforeDispatch,
                      @Parameter(name = "delayBeforeDispatch", desc = "Delay to wait before dispatching if number of consumers before dispatch is not met") Long delayBeforeDispatch,
                      @Parameter(name = "user", desc = "The user associated with this queue") String user,
                      @Parameter(name = "ringSize", desc = "the size this queue should maintain according to ring semantics") Long ringSize) throws Exception;

   /**
    * Deploy a durable queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method will do nothing if the queue with the given name already exists on the server.
    *
    * @param address address to bind the queue to
    * @param name    name of the queue
    * @param filter  of the queue
    */
   @Deprecated
   @Operation(desc = "Deploy a queue", impact = MBeanOperationInfo.ACTION)
   void deployQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "filter", desc = "Filter of the queue") String filter) throws Exception;

   /**
    * Deploy a queue.
    * <br>
    * If {@code address} is {@code null} it will be defaulted to {@code name}.
    * <br>
    * This method will do nothing if the queue with the given name already exists on the server.
    *
    * @param address address to bind the queue to
    * @param name    name of the queue
    * @param filter  of the queue
    * @param durable whether the queue is durable
    */
   @Deprecated
   @Operation(desc = "Deploy a queue", impact = MBeanOperationInfo.ACTION)
   void deployQueue(@Parameter(name = "address", desc = "Address of the queue") String address,
                    @Parameter(name = "name", desc = "Name of the queue") String name,
                    @Parameter(name = "filter", desc = "Filter of the queue") String filter,
                    @Parameter(name = "durable", desc = "Is the queue durable?") boolean durable) throws Exception;

   /**
    * Destroys the queue corresponding to the specified name.
    */
   @Operation(desc = "Destroy a queue", impact = MBeanOperationInfo.ACTION)
   void destroyQueue(@Parameter(name = "name", desc = "Name of the queue to destroy") String name) throws Exception;

   /**
    * Destroys the queue corresponding to the specified name.
    */
   @Operation(desc = "Destroy a queue", impact = MBeanOperationInfo.ACTION)
   void destroyQueue(@Parameter(name = "name", desc = "Name of the queue to destroy") String name,
                     @Parameter(name = "removeConsumers", desc = "Remove consumers of this queue") boolean removeConsumers) throws Exception;

   /**
    * Destroys the queue corresponding to the specified name and delete it's address if there are no other queues
    */
   @Operation(desc = "Destroy a queue", impact = MBeanOperationInfo.ACTION)
   void destroyQueue(@Parameter(name = "name", desc = "Name of the queue to destroy") String name,
                     @Parameter(name = "removeConsumers", desc = "Remove consumers of this queue") boolean removeConsumers,
                     @Parameter(name = "forceAutoDeleteAddress", desc = "Automatically delete the address if this was the last queue") boolean autoDeleteAddress) throws Exception;

   /**
    * Enables message counters for this server.
    */
   @Operation(desc = "Enable message counters", impact = MBeanOperationInfo.ACTION)
   void enableMessageCounters() throws Exception;

   /**
    * Disables message counters for this server.
    */
   @Operation(desc = "Disable message counters", impact = MBeanOperationInfo.ACTION)
   void disableMessageCounters() throws Exception;

   /**
    * Reset all message counters.
    */
   @Operation(desc = "Reset all message counters", impact = MBeanOperationInfo.ACTION)
   void resetAllMessageCounters() throws Exception;

   /**
    * Reset histories for all message counters.
    */
   @Operation(desc = "Reset all message counters history", impact = MBeanOperationInfo.ACTION)
   void resetAllMessageCounterHistories() throws Exception;

   /**
    * List all the prepared transaction, sorted by date, oldest first.
    * <br>
    * The Strings are Base-64 representation of the transaction XID and can be
    * used to heuristically commit or rollback the transactions.
    *
    * @see #commitPreparedTransaction(String)
    * @see #rollbackPreparedTransaction(String)
    */
   @Operation(desc = "List all the prepared transaction, sorted by date, oldest first")
   String[] listPreparedTransactions() throws Exception;

   /**
    * List all the prepared transaction, sorted by date,
    * oldest first, with details, in text format.
    */
   @Operation(desc = "List all the prepared transaction, sorted by date, oldest first, with details, in JSON format")
   String listPreparedTransactionDetailsAsJSON() throws Exception;

   /**
    * List all the prepared transaction, sorted by date,
    * oldest first, with details, in HTML format
    */
   @Operation(desc = "List all the prepared transaction, sorted by date, oldest first, with details, in HTML format")
   String listPreparedTransactionDetailsAsHTML() throws Exception;

   /**
    * List transactions which have been heuristically committed.
    */
   @Operation(desc = "List transactions which have been heuristically committed")
   String[] listHeuristicCommittedTransactions() throws Exception;

   /**
    * List transactions which have been heuristically rolled back.
    */
   @Operation(desc = "List transactions which have been heuristically rolled back")
   String[] listHeuristicRolledBackTransactions() throws Exception;

   /**
    * Heuristically commits a prepared transaction.
    *
    * @param transactionAsBase64 base 64 representation of a prepare transaction
    * @return {@code true} if the transaction was successfully committed, {@code false} else
    * @see #listPreparedTransactions()
    */
   @Operation(desc = "Commit a prepared transaction")
   boolean commitPreparedTransaction(@Parameter(desc = "the Base64 representation of a transaction", name = "transactionAsBase64") String transactionAsBase64) throws Exception;

   /**
    * Heuristically rolls back a prepared transaction.
    *
    * @param transactionAsBase64 base 64 representation of a prepare transaction
    * @return {@code true} if the transaction was successfully rolled back, {@code false} else
    * @see #listPreparedTransactions()
    */
   @Operation(desc = "Rollback a prepared transaction")
   boolean rollbackPreparedTransaction(@Parameter(desc = "the Base64 representation of a transaction", name = "transactionAsBase64") String transactionAsBase64) throws Exception;

   /**
    * Lists the addresses of all the clients connected to this address.
    */
   @Operation(desc = "List the client addresses", impact = MBeanOperationInfo.INFO)
   String[] listRemoteAddresses() throws Exception;

   /**
    * Lists the addresses of the clients connected to this address which matches the specified IP address.
    */
   @Operation(desc = "List the client addresses which match the given IP Address", impact = MBeanOperationInfo.INFO)
   String[] listRemoteAddresses(@Parameter(desc = "an IP address", name = "ipAddress") String ipAddress) throws Exception;

   /**
    * Closes all the connections of clients connected to this server which matches the specified IP address.
    */
   @Operation(desc = "Closes all the connections for the given IP Address", impact = MBeanOperationInfo.INFO)
   boolean closeConnectionsForAddress(@Parameter(desc = "an IP address", name = "ipAddress") String ipAddress) throws Exception;

   /**
    * Closes all the connections of clients connected to this server which matches the specified IP address.
    */
   @Operation(desc = "Closes all the consumer connections for the given messaging address", impact = MBeanOperationInfo.INFO)
   boolean closeConsumerConnectionsForAddress(@Parameter(desc = "a messaging address", name = "address") String address) throws Exception;

   /**
    * Closes all the connections of sessions with a matching user name.
    */
   @Operation(desc = "Closes all the connections for sessions with the given user name", impact = MBeanOperationInfo.INFO)
   boolean closeConnectionsForUser(@Parameter(desc = "a user name", name = "userName") String address) throws Exception;

   /**
    * Closes the connection with the given id.
    */
   @Operation(desc = "Closes all the connection with the id", impact = MBeanOperationInfo.INFO)
   boolean closeConnectionWithID(@Parameter(desc = "The connection ID", name = "ID") String ID) throws Exception;

   /**
    * Closes the session with the given id.
    */
   @Operation(desc = "Closes the session with the id", impact = MBeanOperationInfo.INFO)
   boolean closeSessionWithID(@Parameter(desc = "The connection ID", name = "connectionID") String connectionID,
                              @Parameter(desc = "The session ID", name = "ID") String ID) throws Exception;

   /**
    * Closes the consumer with the given id.
    */
   @Operation(desc = "Closes the consumer with the id", impact = MBeanOperationInfo.INFO)
   boolean closeConsumerWithID(@Parameter(desc = "The session ID", name = "sessionID") String sessionID,
                               @Parameter(desc = "The consumer ID", name = "ID") String ID) throws Exception;

   /**
    * Lists all the IDs of the connections connected to this server.
    */
   @Operation(desc = "List all the connection IDs", impact = MBeanOperationInfo.INFO)
   String[] listConnectionIDs() throws Exception;

   @Operation(desc = "List all producers", impact = MBeanOperationInfo.INFO)
   String listProducersInfoAsJSON() throws Exception;

   /**
    * Lists all the connections connected to this server.
    * The returned String is a JSON string containing details about each connection, e.g.:
    * <pre>
    * [
    *   {
    *     "creationTime": 1469240429671,
    *     "sessionCount": 1,
    *     "implementation": "RemotingConnectionImpl",
    *     "connectionID": "1648309901",
    *     "clientAddress": "\/127.0.0.1:57649"
    *   }
    * ]
    * </pre>
    */
   @Operation(desc = "List all connections as a JSON string")
   String listConnectionsAsJSON() throws Exception;

   /**
    * Lists all the consumers which belongs to the connection specified by the connectionID.
    * The returned String is a JSON string containing details about each consumer, e.g.:
    * <pre>
    * [
    *   {
    *     "filter": "color = 'RED'",
    *     "queueName": "2ea5b050-28bf-4ee2-9b24-b73f5983192a",
    *     "creationTime": 1469239602459,
    *     "deliveringCount": 0,
    *     "consumerID": 1,
    *     "browseOnly": true,
    *     "connectionID": "1963ece3-507a-11e6-94ff-e8b1fc439540",
    *     "sessionID": "19676f55-507a-11e6-94ff-e8b1fc439540"
    *   }
    * ]
    * </pre>
    */
   @Operation(desc = "List all consumers associated with a connection as a JSON string")
   String listConsumersAsJSON(@Parameter(desc = "a connection ID", name = "connectionID") String connectionID) throws Exception;

   /**
    * Lists all the consumers connected to this server.
    * The returned String is a JSON string containing details about each consumer, e.g.:
    * <pre>
    * [
    *   {
    *     "queueName": "fa87c64c-0a38-4697-8421-72e34d17429d",
    *     "creationTime": 1469235956168,
    *     "deliveringCount": 0,
    *     "consumerID": 0,
    *     "browseOnly": false,
    *     "connectionID": "9c0d42e7-5071-11e6-9e29-e8b1fc439540",
    *     "sessionID": "9c0d9109-5071-11e6-9e29-e8b1fc439540"
    *   }
    * ]
    * </pre>
    */
   @Operation(desc = "List all consumers as a JSON string")
   String listAllConsumersAsJSON() throws Exception;

   /**
    * Lists details about all the sessions for the specified connection ID.
    * The returned String is a JSON string containing details about each session associated with the specified ID, e.g.:
    * <pre>
    * [
    *   {
    *     "principal": "myUser",
    *     "creationTime": 1469240773157,
    *     "consumerCount": 0,
    *     "sessionID": "d33d10db-507c-11e6-9e47-e8b1fc439540"
    *   }
    * ]
    * </pre>
    */
   @Operation(desc = "List the sessions for the given connectionID as a JSON string", impact = MBeanOperationInfo.INFO)
   String listSessionsAsJSON(@Parameter(desc = "a connection ID", name = "connectionID") String connectionID) throws Exception;

   /**
    * Lists details about all sessions.
    * The returned String is a JSON string containing details about each and every session, e.g.:
    * <pre>
    * [
    *   {
    *     "sessionID":"e71d61d7-2176-11e8-9057-a0afbd82eaba",
    *     "creationTime":1520365520212,
    *     "consumerCount":1,
    *     "principal":"myUser"
    *   },
    *   {
    *     "sessionID":"e718a6e6-2176-11e8-9057-a0afbd82eaba",
    *     "creationTime":1520365520191,
    *     "consumerCount":0,
    *     "principal":"guest"
    *   }
    * ]
    * </pre>
    */
   @Operation(desc = "List all sessions as a JSON string", impact = MBeanOperationInfo.INFO)
   String listAllSessionsAsJSON() throws Exception;

   /**
    * Lists all the sessions IDs for the specified connection ID.
    */
   @Operation(desc = "List the sessions for the given connectionID", impact = MBeanOperationInfo.INFO)
   String[] listSessions(@Parameter(desc = "a connection ID", name = "connectionID") String connectionID) throws Exception;

   @Operation(desc = "Add security settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addSecuritySettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                            @Parameter(desc = "a comma-separated list of roles allowed to send messages", name = "send") String sendRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to consume messages", name = "consume") String consumeRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create durable queues", name = "createDurableQueueRoles") String createDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete durable queues", name = "deleteDurableQueueRoles") String deleteDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create non durable queues", name = "createNonDurableQueueRoles") String createNonDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete non durable queues", name = "deleteNonDurableQueueRoles") String deleteNonDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to send management messages messages", name = "manage") String manageRoles) throws Exception;

   @Operation(desc = "Add security settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addSecuritySettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                            @Parameter(desc = "a comma-separated list of roles allowed to send messages", name = "send") String sendRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to consume messages", name = "consume") String consumeRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create durable queues", name = "createDurableQueueRoles") String createDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete durable queues", name = "deleteDurableQueueRoles") String deleteDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create non durable queues", name = "createNonDurableQueueRoles") String createNonDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete non durable queues", name = "deleteNonDurableQueueRoles") String deleteNonDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to send management messages messages", name = "manage") String manageRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to browse queues", name = "browse") String browseRoles) throws Exception;

   @Operation(desc = "Add security settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addSecuritySettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                            @Parameter(desc = "a comma-separated list of roles allowed to send messages", name = "send") String sendRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to consume messages", name = "consume") String consumeRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create durable queues", name = "createDurableQueueRoles") String createDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete durable queues", name = "deleteDurableQueueRoles") String deleteDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create non durable queues", name = "createNonDurableQueueRoles") String createNonDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete non durable queues", name = "deleteNonDurableQueueRoles") String deleteNonDurableQueueRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to send management messages messages", name = "manage") String manageRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to browse queues", name = "browse") String browseRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to create addresses", name = "createAddressRoles") String createAddressRoles,
                            @Parameter(desc = "a comma-separated list of roles allowed to delete addresses", name = "deleteAddressRoles") String deleteAddressRoles) throws Exception;

   @Operation(desc = "Remove security settings for an address", impact = MBeanOperationInfo.ACTION)
   void removeSecuritySettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch) throws Exception;

   @Operation(desc = "Get roles for a specific address match", impact = MBeanOperationInfo.INFO)
   Object[] getRoles(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch) throws Exception;

   @Operation(desc = "Get roles (as a JSON string) for a specific address match", impact = MBeanOperationInfo.INFO)
   String getRolesAsJSON(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch) throws Exception;

   /**
    * adds a new address setting for a specific address
    */
   @Operation(desc = "Add address settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
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
                           @Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics) throws Exception;

   /**
    * adds a new address setting for a specific address
    */
   @Operation(desc = "Add address settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
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
                           @Parameter(desc = "allow addresses to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                           @Parameter(desc = "allow auto-created addresses to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses) throws Exception;

   /**
    * adds a new address setting for a specific address
    */
   @Operation(desc = "Add address settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
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
                           @Parameter(desc = "allow addresses to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                           @Parameter(desc = "allow auto-created addresses to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses,
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
                           @Parameter(desc = "the number of messages to preserve for future queues created on the matching address", name = "retroactiveMessageCount") long retroactiveMessageCount) throws Exception;

   /**
    * adds a new address setting for a specific address
    */
   @Operation(desc = "Add address settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
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
                           @Parameter(desc = "allow addresses to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                           @Parameter(desc = "allow auto-created addresses to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses,
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
                           @Parameter(desc = "suffix to use on auto-create expiry queue", name = "expiryQueueSuffix") String expiryQueueSuffix) throws Exception;

   /**
    * adds a new address setting for a specific address
    */
   @Operation(desc = "Add address settings for addresses matching the addressMatch", impact = MBeanOperationInfo.ACTION)
   void addAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                           @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                           @Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                           @Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                           @Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                           @Parameter(desc = "the delivery attempts", name = "maxDeliveryAttempts") int maxDeliveryAttempts,
                           @Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                           @Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                           @Parameter(desc = "the max number of pages in the soft memory cache", name = "pageCacheMaxSize") int pageCacheMaxSize,
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
                           @Parameter(desc = "allow addresses to be created automatically", name = "autoCreateAddresses") boolean autoCreateAddresses,
                           @Parameter(desc = "allow auto-created addresses to be deleted automatically", name = "autoDeleteAddresses") boolean autoDeleteAddresses,
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
                           @Parameter(desc = "the minimum expiry delay setting", name = "minExpiryDelay") long minExpiryDelay,
                           @Parameter(desc = "the maximum expiry delay setting", name = "maxExpiryDelay") long maxExpiryDelay,
                           @Parameter(desc = "whether or not to enable metrics", name = "enableMetrics") boolean enableMetrics) throws Exception;

   @Operation(desc = "Remove address settings", impact = MBeanOperationInfo.ACTION)
   void removeAddressSettings(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch) throws Exception;

   /**
    * returns the address settings as a JSON string
    */
   @Operation(desc = "Returns the address settings as a JSON string for an address match", impact = MBeanOperationInfo.INFO)
   String getAddressSettingsAsJSON(@Parameter(desc = "an address match", name = "addressMatch") String addressMatch) throws Exception;

   @Attribute(desc = "Names of the diverts deployed on this server")
   String[] getDivertNames();

   /**
    * Jon plugin doesn't recognize an Operation whose name is in
    * form getXXXX(), so add this one.
    */
   @Operation(desc = "names of the diverts deployed on this server", impact = MBeanOperationInfo.INFO)
   default String[] listDivertNames() {
      return getDivertNames();
   }

   @Operation(desc = "Create a Divert", impact = MBeanOperationInfo.ACTION)
   void createDivert(@Parameter(name = "name", desc = "Name of the divert") String name,
                     @Parameter(name = "routingName", desc = "Routing name of the divert") String routingName,
                     @Parameter(name = "address", desc = "Address to divert from") String address,
                     @Parameter(name = "forwardingAddress", desc = "Address to divert to") String forwardingAddress,
                     @Parameter(name = "exclusive", desc = "Is the divert exclusive?") boolean exclusive,
                     @Parameter(name = "filterString", desc = "Filter of the divert") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the divert's transformer") String transformerClassName) throws Exception;

   @Operation(desc = "Create a Divert", impact = MBeanOperationInfo.ACTION)
   void createDivert(@Parameter(name = "name", desc = "Name of the divert") String name,
                     @Parameter(name = "routingName", desc = "Routing name of the divert") String routingName,
                     @Parameter(name = "address", desc = "Address to divert from") String address,
                     @Parameter(name = "forwardingAddress", desc = "Address to divert to") String forwardingAddress,
                     @Parameter(name = "exclusive", desc = "Is the divert exclusive?") boolean exclusive,
                     @Parameter(name = "filterString", desc = "Filter of the divert") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the divert's transformer") String transformerClassName,
                     @Parameter(name = "routingType", desc = "How should the routing-type on the diverted messages be set?") String routingType) throws Exception;

   @Operation(desc = "Create a Divert", impact = MBeanOperationInfo.ACTION)
   void createDivert(@Parameter(name = "name", desc = "Name of the divert") String name,
                     @Parameter(name = "routingName", desc = "Routing name of the divert") String routingName,
                     @Parameter(name = "address", desc = "Address to divert from") String address,
                     @Parameter(name = "forwardingAddress", desc = "Address to divert to") String forwardingAddress,
                     @Parameter(name = "exclusive", desc = "Is the divert exclusive?") boolean exclusive,
                     @Parameter(name = "filterString", desc = "Filter of the divert") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the divert's transformer") String transformerClassName,
                     @Parameter(name = "transformerProperties", desc = "Configuration properties of the divert's transformer") Map<String, String> transformerProperties,
                     @Parameter(name = "routingType", desc = "How should the routing-type on the diverted messages be set?") String routingType) throws Exception;

   @Operation(desc = "Create a Divert", impact = MBeanOperationInfo.ACTION)
   void createDivert(@Parameter(name = "name", desc = "Name of the divert") String name,
                     @Parameter(name = "routingName", desc = "Routing name of the divert") String routingName,
                     @Parameter(name = "address", desc = "Address to divert from") String address,
                     @Parameter(name = "forwardingAddress", desc = "Address to divert to") String forwardingAddress,
                     @Parameter(name = "exclusive", desc = "Is the divert exclusive?") boolean exclusive,
                     @Parameter(name = "filterString", desc = "Filter of the divert") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the divert's transformer") String transformerClassName,
                     @Parameter(name = "transformerPropertiesAsJSON", desc = "Configuration properties of the divert's transformer in JSON form") String transformerPropertiesAsJSON,
                     @Parameter(name = "routingType", desc = "How should the routing-type on the diverted messages be set?") String routingType) throws Exception;

   /**
    * update a divert
    */
   @Operation(desc = "Update a divert", impact = MBeanOperationInfo.ACTION)
   void updateDivert(@Parameter(name = "name", desc = "Name of the queue") String name,
                     @Parameter(name = "forwardingAddress", desc = "Address to divert to") String forwardingAddress,
                     @Parameter(name = "filterString", desc = "Filter of the divert") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the divert's transformer") String transformerClassName,
                     @Parameter(name = "transformerProperties", desc = "Configuration properties of the divert's transformer") Map<String, String> transformerProperties,
                     @Parameter(name = "routingType", desc = "How should the routing-type on the diverted messages be set?") String routingType) throws Exception;

   @Operation(desc = "Destroy a Divert", impact = MBeanOperationInfo.ACTION)
   void destroyDivert(@Parameter(name = "name", desc = "Name of the divert") String name) throws Exception;

   @Attribute(desc = "Names of the bridges deployed on this server")
   String[] getBridgeNames();

   /**
    * @deprecated Deprecated in favour of {@link #createBridge(String)}
    */
   @Deprecated
   @Operation(desc = "Create a Bridge", impact = MBeanOperationInfo.ACTION)
   void createBridge(@Parameter(name = "name", desc = "Name of the bridge") String name,
                     @Parameter(name = "queueName", desc = "Name of the source queue") String queueName,
                     @Parameter(name = "forwardingAddress", desc = "Forwarding address") String forwardingAddress,
                     @Parameter(name = "filterString", desc = "Filter of the bridge") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the bridge transformer") String transformerClassName,
                     @Parameter(name = "retryInterval", desc = "Connection retry interval") long retryInterval,
                     @Parameter(name = "retryIntervalMultiplier", desc = "Connection retry interval multiplier") double retryIntervalMultiplier,
                     @Parameter(name = "initialConnectAttempts", desc = "Number of initial connection attempts") int initialConnectAttempts,
                     @Parameter(name = "reconnectAttempts", desc = "Number of reconnection attempts") int reconnectAttempts,
                     @Parameter(name = "useDuplicateDetection", desc = "Use duplicate detection") boolean useDuplicateDetection,
                     @Parameter(name = "confirmationWindowSize", desc = "Confirmation window size") int confirmationWindowSize,
                     @Parameter(name = "producerWindowSize", desc = "Producer window size") int producerWindowSize,
                     @Parameter(name = "clientFailureCheckPeriod", desc = "Period to check client failure") long clientFailureCheckPeriod,
                     @Parameter(name = "staticConnectorNames", desc = "comma separated list of connector names or name of discovery group if 'useDiscoveryGroup' is set to true") String connectorNames,
                     @Parameter(name = "useDiscoveryGroup", desc = "use discovery  group") boolean useDiscoveryGroup,
                     @Parameter(name = "ha", desc = "Is it using HA") boolean ha,
                     @Parameter(name = "user", desc = "User name") String user,
                     @Parameter(name = "password", desc = "User password") String password) throws Exception;

   /**
    * @deprecated Deprecated in favour of {@link #createBridge(String)}
    */
   @Deprecated
   @Operation(desc = "Create a Bridge", impact = MBeanOperationInfo.ACTION)
   void createBridge(@Parameter(name = "name", desc = "Name of the bridge") String name,
                     @Parameter(name = "queueName", desc = "Name of the source queue") String queueName,
                     @Parameter(name = "forwardingAddress", desc = "Forwarding address") String forwardingAddress,
                     @Parameter(name = "filterString", desc = "Filter of the bridge") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the bridge transformer") String transformerClassName,
                     @Parameter(name = "transformerProperties", desc = "Configuration properties of the bridge transformer") Map<String, String> transformerProperties,
                     @Parameter(name = "retryInterval", desc = "Connection retry interval") long retryInterval,
                     @Parameter(name = "retryIntervalMultiplier", desc = "Connection retry interval multiplier") double retryIntervalMultiplier,
                     @Parameter(name = "initialConnectAttempts", desc = "Number of initial connection attempts") int initialConnectAttempts,
                     @Parameter(name = "reconnectAttempts", desc = "Number of reconnection attempts") int reconnectAttempts,
                     @Parameter(name = "useDuplicateDetection", desc = "Use duplicate detection") boolean useDuplicateDetection,
                     @Parameter(name = "confirmationWindowSize", desc = "Confirmation window size") int confirmationWindowSize,
                     @Parameter(name = "producerWindowSize", desc = "Producer window size") int producerWindowSize,
                     @Parameter(name = "clientFailureCheckPeriod", desc = "Period to check client failure") long clientFailureCheckPeriod,
                     @Parameter(name = "staticConnectorNames", desc = "comma separated list of connector names or name of discovery group if 'useDiscoveryGroup' is set to true") String connectorNames,
                     @Parameter(name = "useDiscoveryGroup", desc = "use discovery  group") boolean useDiscoveryGroup,
                     @Parameter(name = "ha", desc = "Is it using HA") boolean ha,
                     @Parameter(name = "user", desc = "User name") String user,
                     @Parameter(name = "password", desc = "User password") String password) throws Exception;

   /**
    * @deprecated Deprecated in favour of {@link #createBridge(String)}
    */
   @Deprecated
   @Operation(desc = "Create a Bridge", impact = MBeanOperationInfo.ACTION)
   void createBridge(@Parameter(name = "name", desc = "Name of the bridge") String name,
                     @Parameter(name = "queueName", desc = "Name of the source queue") String queueName,
                     @Parameter(name = "forwardingAddress", desc = "Forwarding address") String forwardingAddress,
                     @Parameter(name = "filterString", desc = "Filter of the bridge") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the bridge transformer") String transformerClassName,
                     @Parameter(name = "transformerPropertiesAsJSON", desc = "Configuration properties of the bridge transformer in JSON form") String transformerPropertiesAsJSON,
                     @Parameter(name = "retryInterval", desc = "Connection retry interval") long retryInterval,
                     @Parameter(name = "retryIntervalMultiplier", desc = "Connection retry interval multiplier") double retryIntervalMultiplier,
                     @Parameter(name = "initialConnectAttempts", desc = "Number of initial connection attempts") int initialConnectAttempts,
                     @Parameter(name = "reconnectAttempts", desc = "Number of reconnection attempts") int reconnectAttempts,
                     @Parameter(name = "useDuplicateDetection", desc = "Use duplicate detection") boolean useDuplicateDetection,
                     @Parameter(name = "confirmationWindowSize", desc = "Confirmation window size") int confirmationWindowSize,
                     @Parameter(name = "producerWindowSize", desc = "Producer window size") int producerWindowSize,
                     @Parameter(name = "clientFailureCheckPeriod", desc = "Period to check client failure") long clientFailureCheckPeriod,
                     @Parameter(name = "staticConnectorNames", desc = "comma separated list of connector names or name of discovery group if 'useDiscoveryGroup' is set to true") String connectorNames,
                     @Parameter(name = "useDiscoveryGroup", desc = "use discovery  group") boolean useDiscoveryGroup,
                     @Parameter(name = "ha", desc = "Is it using HA") boolean ha,
                     @Parameter(name = "user", desc = "User name") String user,
                     @Parameter(name = "password", desc = "User password") String password) throws Exception;

   /**
    * @deprecated Deprecated in favour of {@link #createBridge(String)}
    */
   @Deprecated
   @Operation(desc = "Create a Bridge", impact = MBeanOperationInfo.ACTION)
   void createBridge(@Parameter(name = "name", desc = "Name of the bridge") String name,
                     @Parameter(name = "queueName", desc = "Name of the source queue") String queueName,
                     @Parameter(name = "forwardingAddress", desc = "Forwarding address") String forwardingAddress,
                     @Parameter(name = "filterString", desc = "Filter of the bridge") String filterString,
                     @Parameter(name = "transformerClassName", desc = "Class name of the bridge transformer") String transformerClassName,
                     @Parameter(name = "retryInterval", desc = "Connection retry interval") long retryInterval,
                     @Parameter(name = "retryIntervalMultiplier", desc = "Connection retry interval multiplier") double retryIntervalMultiplier,
                     @Parameter(name = "initialConnectAttempts", desc = "Number of initial connection attempts") int initialConnectAttempts,
                     @Parameter(name = "reconnectAttempts", desc = "Number of reconnection attempts") int reconnectAttempts,
                     @Parameter(name = "useDuplicateDetection", desc = "Use duplicate detection") boolean useDuplicateDetection,
                     @Parameter(name = "confirmationWindowSize", desc = "Confirmation window size") int confirmationWindowSize,
                     @Parameter(name = "clientFailureCheckPeriod", desc = "Period to check client failure") long clientFailureCheckPeriod,
                     @Parameter(name = "staticConnectorNames", desc = "comma separated list of connector names or name of discovery group if 'useDiscoveryGroup' is set to true") String connectorNames,
                     @Parameter(name = "useDiscoveryGroup", desc = "use discovery  group") boolean useDiscoveryGroup,
                     @Parameter(name = "ha", desc = "Is it using HA") boolean ha,
                     @Parameter(name = "user", desc = "User name") String user,
                     @Parameter(name = "password", desc = "User password") String password) throws Exception;

   /**
    * Create a bridge.
    *
    * @param bridgeConfiguration the configuration of the queue in JSON format
    */
   @Operation(desc = "Create a bridge", impact = MBeanOperationInfo.ACTION)
   void createBridge(@Parameter(name = "bridgeConfiguration", desc = "the configuration of the bridge in JSON format") String bridgeConfiguration) throws Exception;

   @Operation(desc = "Destroy a bridge", impact = MBeanOperationInfo.ACTION)
   void destroyBridge(@Parameter(name = "name", desc = "Name of the bridge") String name) throws Exception;

   @Operation(desc = "List the existing broker connections", impact = MBeanOperationInfo.INFO)
   String listBrokerConnections();

   @Operation(desc = "Activate a broker connection that is pre configured", impact = MBeanOperationInfo.ACTION)
   void startBrokerConnection(@Parameter(name = "name", desc = "Name of the broker connection to be started") String name) throws Exception;

   @Operation(desc = "Stops a broker connection that is pre configured", impact = MBeanOperationInfo.ACTION)
   void stopBrokerConnection(@Parameter(name = "name", desc = "Name of the broker connection to be stopped") String name) throws Exception;

   @Operation(desc = "Create a connector service", impact = MBeanOperationInfo.ACTION)
   void createConnectorService(@Parameter(name = "name", desc = "Name of the connector service") String name,
                               @Parameter(name = "factoryClass", desc = "Class name of the connector service factory") String factoryClass,
                               @Parameter(name = "parameters", desc = "Parameter specific to the connector service") Map<String, Object> parameters) throws Exception;

   @Operation(desc = "Destroy a connector service", impact = MBeanOperationInfo.ACTION)
   void destroyConnectorService(@Parameter(name = "name", desc = "Name of the connector service") String name) throws Exception;

   @Attribute(desc = "Names of the connector services on this server")
   String[] getConnectorServices();

   @Operation(desc = "Force the server to stop and notify clients to failover", impact = MBeanOperationInfo.UNKNOWN)
   void forceFailover() throws Exception;

   @Operation(desc = "Force the server to stop and to scale down to another server", impact = MBeanOperationInfo.UNKNOWN)
   void scaleDown(@Parameter(name = "name", desc = "The connector to use to scale down, if not provided the first appropriate connector will be used") String connector) throws Exception;

   @Operation(desc = "List the Network Topology", impact = MBeanOperationInfo.INFO)
   String listNetworkTopology() throws Exception;

   @Operation(desc = "Get the selected address", impact = MBeanOperationInfo.INFO)
   String getAddressInfo(@Parameter(name = "address", desc = "The address") String address) throws ActiveMQAddressDoesNotExistException;

   @Operation(desc = "Get a list of bindings associated with an address", impact = MBeanOperationInfo.INFO)
   String listBindingsForAddress(@Parameter(name = "address", desc = "The address") String address) throws Exception;

   @Operation(desc = "List Addresses on the broker", impact = MBeanOperationInfo.INFO)
   String listAddresses(@Parameter(name = "separator", desc = "Separator used on the string listing") String separator) throws Exception;

   @Operation(desc = "Search for Connections", impact = MBeanOperationInfo.INFO)
   String listConnections(@Parameter(name = "options", desc = "Options") String options,
                          @Parameter(name = "pageNumber", desc = "Page Number") int page,
                          @Parameter(name = "pageSize", desc = "Page Size") int pageSize) throws Exception;

   @Operation(desc = "Search for Sessions", impact = MBeanOperationInfo.INFO)
   String listSessions(@Parameter(name = "options", desc = "Options") String options,
                       @Parameter(name = "pageNumber", desc = "Page Number") int page,
                       @Parameter(name = "pageSize", desc = "Page Size") int pageSize) throws Exception;

   @Operation(desc = "Search for Consumers", impact = MBeanOperationInfo.INFO)
   String listConsumers(@Parameter(name = "options", desc = "Options") String options,
                        @Parameter(name = "pageNumber", desc = "Page Number") int page,
                        @Parameter(name = "pageSize", desc = "Page Size") int pageSize) throws Exception;

   @Operation(desc = "Search for Consumers", impact = MBeanOperationInfo.INFO)
   String listProducers(@Parameter(name = "options", desc = "Options") String options,
                        @Parameter(name = "pageNumber", desc = "Page Number") int page,
                        @Parameter(name = "pageSize", desc = "Page Size") int pageSize) throws Exception;

   @Operation(desc = "Search for Addresses", impact = MBeanOperationInfo.INFO)
   String listAddresses(@Parameter(name = "options", desc = "Options") String options,
                        @Parameter(name = "pageNumber", desc = "Page Number") int page,
                        @Parameter(name = "pageSize", desc = "Page Size") int pageSize) throws Exception;

   @Operation(desc = "Search for Queues", impact = MBeanOperationInfo.INFO)
   String listQueues(@Parameter(name = "options", desc = "Options") String options,
                     @Parameter(name = "pageNumber", desc = "Page Number") int page,
                     @Parameter(name = "pageSize", desc = "Page Size") int pageSize) throws Exception;

   /**
    * Returns the names of the queues created on this server with the given routing-type.
    */
   @Operation(desc = "Names of the queues created on this server with the given routing-type (i.e. ANYCAST or MULTICAST)", impact = MBeanOperationInfo.INFO)
   String[] getQueueNames(@Parameter(name = "routingType", desc = "The routing type, MULTICAST or ANYCAST") String routingType);

   /**
    * Returns the names of the cluster-connections deployed on this server.
    */
   @Operation(desc = "Names of the cluster-connections deployed on this server", impact = MBeanOperationInfo.INFO)
   String[] getClusterConnectionNames();

   /**
    * Add a user (only applicable when using the JAAS PropertiesLoginModule)
    *
    * @param username
    * @param password
    * @param roles
    * @throws Exception
    */
   @Operation(desc = "add a user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager)", impact = MBeanOperationInfo.ACTION)
   void addUser(@Parameter(name = "username", desc = "Name of the user") String username,
                @Parameter(name = "password", desc = "User's password") String password,
                @Parameter(name = "roles", desc = "User's role (comma separated)") String roles,
                @Parameter(name = "plaintext", desc = "whether or not to store the password in plaintext or hash it") boolean plaintext) throws Exception;

   /**
    * List the information about a user or all users if no username is supplied (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager).
    *
    * @param username
    * @return JSON array of user and role information
    * @throws Exception
    */
   @Operation(desc = "list info about a user or all users if no username is supplied (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager)", impact = MBeanOperationInfo.ACTION)
   String listUser(@Parameter(name = "username", desc = "Name of the user; leave null to list all known users") String username) throws Exception;

   /**
    * Remove a user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager).
    *
    * @param username
    * @throws Exception
    */
   @Operation(desc = "remove a user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager)", impact = MBeanOperationInfo.ACTION)
   void removeUser(@Parameter(name = "username", desc = "Name of the user") String username) throws Exception;

   /**
    * Set new properties on an existing user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager).
    *
    * @param username
    * @param password
    * @param roles
    * @throws Exception
    */
   @Operation(desc = "set new properties on an existing user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager)", impact = MBeanOperationInfo.ACTION)
   void resetUser(@Parameter(name = "username", desc = "Name of the user") String username,
                  @Parameter(name = "password", desc = "User's password") String password,
                  @Parameter(name = "roles", desc = "User's role (comma separated)") String roles) throws Exception;
   /**
    * Set new properties on an existing user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager).
    *
    * @param username
    * @param password
    * @param roles
    * @param plaintext
    * @throws Exception
    */

   @Operation(desc = "set new properties on an existing user (only applicable when using the JAAS PropertiesLoginModule or the ActiveMQBasicSecurityManager)", impact = MBeanOperationInfo.ACTION)
   void resetUser(@Parameter(name = "username", desc = "Name of the user") String username,
                  @Parameter(name = "password", desc = "User's password") String password,
                  @Parameter(name = "roles", desc = "User's role (comma separated)") String roles,
                  @Parameter(name = "plaintext", desc = "whether or not to store the password in plaintext or hash it") boolean plaintext) throws Exception;

   @Operation(desc = "forces the broker to reload its configuration file", impact = MBeanOperationInfo.ACTION)
   void reloadConfigurationFile() throws Exception;

   @Operation(desc = "Makes the broker to read messages from the retention folder matching the address and filter.", impact = MBeanOperationInfo.ACTION)
   void replay(@Parameter(name = "address", desc = "Name of the address to replay") String address,
               @Parameter(name = "target", desc = "Where the replay data should be sent") String target,
               @Parameter(name = "filter", desc = "Filter to apply on message selection. Null means everything matching the address") String filter) throws Exception;

   @Operation(desc = "Makes the broker to read messages from the retention folder matching the address and filter.", impact = MBeanOperationInfo.ACTION)
   void replay(@Parameter(name = "startScanDate", desc = "Start date where we will start scanning for journals to replay. Format YYYYMMDDHHMMSS") String startScan,
               @Parameter(name = "endScanDate", desc = "Finish date where we will stop scannning for journals to replay. Format YYYYMMDDHHMMSS") String endScan,
               @Parameter(name = "address", desc = "Name of the address to replay") String address,
               @Parameter(name = "target", desc = "Where the replay data should be sent") String target,
               @Parameter(name = "filter", desc = "Filter to apply on message selection. Null means everything matching the address") String filter) throws Exception;
}

