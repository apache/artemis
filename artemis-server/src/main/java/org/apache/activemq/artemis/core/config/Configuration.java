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
package org.apache.activemq.artemis.core.config;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.balancing.BrokerBalancerConfiguration;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerFederationPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBindingPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBridgePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConnectionPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerConsumerPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerCriticalPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerQueuePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerResourcePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerSessionPlugin;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;

/**
 * A Configuration is used to configure ActiveMQ Artemis servers.
 */
public interface Configuration {

   /**
    * To be used on dependency management on the application server
    */
   String getName();

   /**
    * To be used on dependency management on the application server
    */
   Configuration setName(String name);

   /**
    * We use Bean-utils to pass in System.properties that start with {@link #setSystemPropertyPrefix(String)}.
    * The default should be 'brokerconfig.' (Including the ".").
    * For example if you want to set clustered through a system property you must do:
    *
    * -Dbrokerconfig.clustered=true
    *
    * The prefix is configured here.
    * @param systemPropertyPrefix
    * @return
    */
   Configuration setSystemPropertyPrefix(String systemPropertyPrefix);

   /**
    * See doc at {@link #setSystemPropertyPrefix(String)}.
    * @return
    */
   String getSystemPropertyPrefix();

   Configuration parseSystemProperties() throws Exception;

   Configuration parseSystemProperties(Properties properties) throws Exception;

   boolean isCriticalAnalyzer();

   Configuration setCriticalAnalyzer(boolean CriticalAnalyzer);

   long getCriticalAnalyzerTimeout();

   Configuration setCriticalAnalyzerTimeout(long timeout);

   long getCriticalAnalyzerCheckPeriod();

   Configuration setCriticalAnalyzerCheckPeriod(long checkPeriod);

   CriticalAnalyzerPolicy getCriticalAnalyzerPolicy();

   Configuration setCriticalAnalyzerPolicy(CriticalAnalyzerPolicy policy);


   /**
    * Returns whether this server is clustered. <br>
    * {@code true} if {@link #getClusterConfigurations()} is not empty.
    */
   boolean isClustered();

   /**
    * Returns whether delivery count is persisted before messages are delivered to the consumers. <br>
    * Default value is
    * {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_PERSIST_DELIVERY_COUNT_BEFORE_DELIVERY}
    */
   boolean isPersistDeliveryCountBeforeDelivery();

   /**
    * Sets whether delivery count is persisted before messages are delivered to consumers.
    */
   Configuration setPersistDeliveryCountBeforeDelivery(boolean persistDeliveryCountBeforeDelivery);

   /**
    * Returns whether this server is using persistence and store data. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_PERSISTENCE_ENABLED}.
    */
   boolean isPersistenceEnabled();

   /**
    * Sets whether this server is using persistence and store data.
    */
   Configuration setPersistenceEnabled(boolean enable);

   /**
    * Should use fdatasync on journal files.
    *
    * @see <a href="http://man7.org/linux/man-pages/man2/fdatasync.2.html">fdatasync</a>
    *
    * @return a boolean
    */
   boolean isJournalDatasync();

   /**
    * documented at {@link #isJournalDatasync()} ()}
    *
    * @param enable
    * @return this
    */
   Configuration setJournalDatasync(boolean enable);

   /**
    * @return usernames mapped to ResourceLimitSettings
    */
   Map<String, ResourceLimitSettings> getResourceLimitSettings();

   /**
    * @param resourceLimitSettings usernames mapped to ResourceLimitSettings
    */
   Configuration setResourceLimitSettings(Map<String, ResourceLimitSettings> resourceLimitSettings);

   /**
    * @param resourceLimitSettings usernames mapped to ResourceLimitSettings
    */
   Configuration addResourceLimitSettings(ResourceLimitSettings resourceLimitSettings);

   /**
    * Returns the period (in milliseconds) to scan configuration files used by deployment. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_FILE_DEPLOYER_SCAN_PERIOD}.
    */
   long getFileDeployerScanPeriod();

   /**
    * Sets the period  to scan configuration files used by deployment.
    */
   Configuration setFileDeployerScanPeriod(long period);

   /**
    * Returns the maximum number of threads in the thread pool of this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_THREAD_POOL_MAX_SIZE}.
    */
   int getThreadPoolMaxSize();

   /**
    * Sets the maximum number of threads in the thread pool of this server.
    */
   Configuration setThreadPoolMaxSize(int maxSize);

   /**
    * Returns the maximum number of threads in the <em>scheduled</em> thread pool of this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE}.
    */
   int getScheduledThreadPoolMaxSize();

   /**
    * Sets the maximum number of threads in the <em>scheduled</em> thread pool of this server.
    */
   Configuration setScheduledThreadPoolMaxSize(int maxSize);

   /**
    * Returns the interval time (in milliseconds) to invalidate security credentials. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_SECURITY_INVALIDATION_INTERVAL}.
    */
   long getSecurityInvalidationInterval();

   /**
    * Sets the interval time (in milliseconds) to invalidate security credentials.
    */
   Configuration setSecurityInvalidationInterval(long interval);

   /**
    * Sets the size of the authentication cache.
    */
   Configuration setAuthenticationCacheSize(long size);

   /**
    * Returns the configured size of the authentication cache. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_AUTHENTICATION_CACHE_SIZE}.
    */
   long getAuthenticationCacheSize();

   /**
    * Sets the size of the authorization cache.
    */
   Configuration setAuthorizationCacheSize(long size);

   /**
    * Returns the configured size of the authorization cache. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_AUTHORIZATION_CACHE_SIZE}.
    */
   long getAuthorizationCacheSize();

   /**
    * Returns whether security is enabled for this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_SECURITY_ENABLED}.
    */
   boolean isSecurityEnabled();

   /**
    * Sets whether security is enabled for this server.
    */
   Configuration setSecurityEnabled(boolean enabled);

   /**
    * Returns whether graceful shutdown is enabled for this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_GRACEFUL_SHUTDOWN_ENABLED}.
    */
   boolean isGracefulShutdownEnabled();

   /**
    * Sets whether security is enabled for this server.
    */
   Configuration setGracefulShutdownEnabled(boolean enabled);

   /**
    * Returns the graceful shutdown timeout for this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT}.
    */
   long getGracefulShutdownTimeout();

   /**
    * Sets the graceful shutdown timeout
    */
   Configuration setGracefulShutdownTimeout(long timeout);

   /**
    * Returns whether this server is manageable using JMX or not. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JMX_MANAGEMENT_ENABLED}.
    */
   boolean isJMXManagementEnabled();

   /**
    * Sets whether this server is manageable using JMX or not. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JMX_MANAGEMENT_ENABLED}.
    */
   Configuration setJMXManagementEnabled(boolean enabled);

   /**
    * Returns the domain used by JMX MBeans (provided JMX management is enabled). <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JMX_DOMAIN}.
    */
   String getJMXDomain();

   /**
    * Sets the domain used by JMX MBeans (provided JMX management is enabled).
    * <p>
    * Changing this JMX domain is required if multiple ActiveMQ Artemis servers are run inside
    * the same JVM and all servers are using the same MBeanServer.
    */
   Configuration setJMXDomain(String domain);

   /**
    * whether or not to use the broker name in the JMX tree
    */
   boolean isJMXUseBrokerName();

   /**
    * whether or not to use the broker name in the JMX tree
    */
   ConfigurationImpl setJMXUseBrokerName(boolean jmxUseBrokerName);

   /**
    * Returns the list of interceptors classes used by this server for incoming messages (i.e. those being delivered to
    * the server from clients).
    */
   List<String> getIncomingInterceptorClassNames();

   /**
    * Returns the list of interceptors classes used by this server for outgoing messages (i.e. those being delivered to
    * clients from the server).
    */
   List<String> getOutgoingInterceptorClassNames();

   /**
    * Sets the list of interceptors classes used by this server for incoming messages (i.e. those being delivered to
    * the server from clients).
    * <br>
    * Classes must implement {@link org.apache.activemq.artemis.api.core.Interceptor}.
    */
   Configuration setIncomingInterceptorClassNames(List<String> interceptors);

   /**
    * Sets the list of interceptors classes used by this server for outgoing messages (i.e. those being delivered to
    * clients from the server).
    * <br>
    * Classes must implement {@link org.apache.activemq.artemis.api.core.Interceptor}.
    */
   Configuration setOutgoingInterceptorClassNames(List<String> interceptors);

   /**
    * Returns the connection time to live. <br>
    * This value overrides the connection time to live <em>sent by the client</em>. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_CONNECTION_TTL_OVERRIDE}.
    */
   long getConnectionTTLOverride();

   /**
    * Sets the connection time to live.
    */
   Configuration setConnectionTTLOverride(long ttl);

   /**
    * Returns if to use Core subscription naming for AMQP.
    */
   boolean isAmqpUseCoreSubscriptionNaming();

   /**
    * Sets if to use Core subscription naming for AMQP.
    */
   Configuration setAmqpUseCoreSubscriptionNaming(boolean amqpUseCoreSubscriptionNaming);

   /**
    * deprecated: we decide based on the semantic context when to make things async or not
    * Returns whether code coming from connection is executed asynchronously or not. <br>
    * Default value is
    * {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_ASYNC_CONNECTION_EXECUTION_ENABLED}.
    */
   @Deprecated
   boolean isAsyncConnectionExecutionEnabled();

   /**
    * Sets whether code coming from connection is executed asynchronously or not.
    */
   @Deprecated
   Configuration setEnabledAsyncConnectionExecution(boolean enabled);

   /**
    * Returns the acceptors configured for this server.
    */
   Set<TransportConfiguration> getAcceptorConfigurations();

   /**
    * Sets the acceptors configured for this server.
    */
   Configuration setAcceptorConfigurations(Set<TransportConfiguration> infos);

   Configuration addAcceptorConfiguration(TransportConfiguration infos);

   /**
    * Add an acceptor to the config
    *
    * @param name the name of the acceptor
    * @param uri  the URI of the acceptor
    * @return this
    * @throws Exception in case of Parsing errors on the URI
    * @see <a href="https://github.com/apache/activemq-artemis/blob/master/docs/user-manual/en/configuring-transports.md">Configuring the Transport</a>
    */
   Configuration addAcceptorConfiguration(String name, String uri) throws Exception;

   Configuration clearAcceptorConfigurations();

   /**
    * Returns the connectors configured for this server.
    */
   Map<String, TransportConfiguration> getConnectorConfigurations();

   /**
    * Sets the connectors configured for this server.
    */
   Configuration setConnectorConfigurations(Map<String, TransportConfiguration> infos);

   Configuration addConnectorConfiguration(String key, TransportConfiguration info);

   Configuration addConnectorConfiguration(String name, String uri) throws Exception;

   Configuration clearConnectorConfigurations();

   /**
    * Returns the broadcast groups configured for this server.
    */
   List<BroadcastGroupConfiguration> getBroadcastGroupConfigurations();

   /**
    * Sets the broadcast groups configured for this server.
    */
   Configuration setBroadcastGroupConfigurations(List<BroadcastGroupConfiguration> configs);

   Configuration addBroadcastGroupConfiguration(BroadcastGroupConfiguration config);

   /**
    * Returns the discovery groups configured for this server.
    */
   Map<String, DiscoveryGroupConfiguration> getDiscoveryGroupConfigurations();

   /**
    * Sets the discovery groups configured for this server.
    */
   Configuration setDiscoveryGroupConfigurations(Map<String, DiscoveryGroupConfiguration> configs);

   Configuration addDiscoveryGroupConfiguration(String key,
                                                DiscoveryGroupConfiguration discoveryGroupConfiguration);

   /**
    * Returns the grouping handler configured for this server.
    */
   GroupingHandlerConfiguration getGroupingHandlerConfiguration();

   /**
    * Sets the grouping handler configured for this server.
    */
   Configuration setGroupingHandlerConfiguration(GroupingHandlerConfiguration groupingHandlerConfiguration);

   /**
    * Returns the bridges configured for this server.
    */
   List<BridgeConfiguration> getBridgeConfigurations();

   /**
    * Sets the bridges configured for this server.
    */
   Configuration setBridgeConfigurations(List<BridgeConfiguration> configs);

   /**
    * Returns the diverts configured for this server.
    */
   List<DivertConfiguration> getDivertConfigurations();

   /**
    * Sets the diverts configured for this server.
    */
   Configuration setDivertConfigurations(List<DivertConfiguration> configs);

   Configuration addDivertConfiguration(DivertConfiguration config);

   /**
    * Returns the redirects configured for this server.
    */
   List<BrokerBalancerConfiguration> getBalancerConfigurations();

   /**
    * Sets the redirects configured for this server.
    */
   Configuration setBalancerConfigurations(List<BrokerBalancerConfiguration> configs);

   Configuration addBalancerConfiguration(BrokerBalancerConfiguration config);

   /**
    * Returns the cluster connections configured for this server.
    * <p>
    * Modifying the returned list will modify the list of {@link ClusterConnectionConfiguration}
    * used by this configuration.
    */
   List<ClusterConnectionConfiguration> getClusterConfigurations();

   /**
    * Sets the cluster connections configured for this server.
    */
   Configuration setClusterConfigurations(List<ClusterConnectionConfiguration> configs);

   Configuration addClusterConfiguration(ClusterConnectionConfiguration config);

   ClusterConnectionConfiguration addClusterConfiguration(String name, String uri) throws Exception;

   Configuration clearClusterConfigurations();

   Configuration addAMQPConnection(AMQPBrokerConnectConfiguration amqpBrokerConnectConfiguration);

   List<AMQPBrokerConnectConfiguration> getAMQPConnection();

   /**
    * Returns the queues configured for this server.
    */
   @Deprecated
   List<CoreQueueConfiguration> getQueueConfigurations();


   List<QueueConfiguration> getQueueConfigs();

   /**
    * Sets the queues configured for this server.
    */
   @Deprecated
   Configuration setQueueConfigurations(List<CoreQueueConfiguration> configs);

   /**
    * Sets the queues configured for this server.
    */
   Configuration setQueueConfigs(List<QueueConfiguration> configs);

   @Deprecated
   Configuration addQueueConfiguration(CoreQueueConfiguration config);

   Configuration addQueueConfiguration(QueueConfiguration config);

   /**
    * Returns the addresses configured for this server.
    */
   List<CoreAddressConfiguration> getAddressConfigurations();

   /**
    * Sets the addresses configured for this server.
    */
   Configuration setAddressConfigurations(List<CoreAddressConfiguration> configs);

   /**
    * Adds an addresses configuration
    */
   Configuration addAddressConfiguration(CoreAddressConfiguration config);

   /**
    * Returns the management address of this server. <br>
    * Clients can send management messages to this address to manage this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MANAGEMENT_ADDRESS}.
    */
   SimpleString getManagementAddress();

   /**
    * Sets the management address of this server.
    */
   Configuration setManagementAddress(SimpleString address);

   /**
    * Returns the management notification address of this server. <br>
    * Clients can bind queues to this address to receive management notifications emitted by this
    * server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MANAGEMENT_NOTIFICATION_ADDRESS}.
    */
   SimpleString getManagementNotificationAddress();

   /**
    * Sets the management notification address of this server.
    */
   Configuration setManagementNotificationAddress(SimpleString address);

   /**
    * Returns the cluster user for this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_CLUSTER_USER}.
    */
   String getClusterUser();

   /**
    * Sets the cluster user for this server.
    */
   Configuration setClusterUser(String user);

   /**
    * Returns the cluster password for this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_CLUSTER_PASSWORD}.
    */
   String getClusterPassword();

   /**
    * Sets the cluster password for this server.
    */
   Configuration setClusterPassword(String password);

   /**
    * Returns the size of the cache for pre-creating message IDs. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_ID_CACHE_SIZE}.
    */
   int getIDCacheSize();

   /**
    * Sets the size of the cache for pre-creating message IDs.
    */
   Configuration setIDCacheSize(int idCacheSize);

   /**
    * Returns whether message ID cache is persisted. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_PERSIST_ID_CACHE}.
    */
   boolean isPersistIDCache();

   /**
    * Sets whether message ID cache is persisted.
    */
   Configuration setPersistIDCache(boolean persist);

   // Journal related attributes ------------------------------------------------------------

   /**
    * Returns the file system directory used to store bindings. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_BINDINGS_DIRECTORY}.
    */
   String getBindingsDirectory();

   /**
    * The binding location related to artemis.instance.
    */
   File getBindingsLocation();

   /**
    * Sets the file system directory used to store bindings.
    */
   Configuration setBindingsDirectory(String dir);

   /**
    * The max number of concurrent reads allowed on paging.
    * <p>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MAX_CONCURRENT_PAGE_IO}.
    */
   int getPageMaxConcurrentIO();

   /**
    * The max number of concurrent reads allowed on paging.
    * <p>
    * Default = 5
    */
   Configuration setPageMaxConcurrentIO(int maxIO);

   /**
    * Returns whether the whole page is read while getting message after page cache is evicted. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_READ_WHOLE_PAGE}.
    */
   boolean isReadWholePage();

   /**
    * Sets whether the whole page is read while getting message after page cache is evicted.
    */
   Configuration setReadWholePage(boolean read);

   /**
    * Returns the file system directory used to store journal log. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_DIR}.
    */
   String getJournalDirectory();

   /**
    * The location of the journal related to artemis.instance.
    *
    * @return
    */
   File getJournalLocation();

   /**
    * The location of the node manager lock file related to artemis.instance.
    */
   File getNodeManagerLockLocation();

   /**
    * Sets the file system directory used to store the node manager lock file.
    */
   Configuration setNodeManagerLockDirectory(String dir);

   /**
    * Sets the file system directory used to store journal log.
    */
   Configuration setJournalDirectory(String dir);

   String getJournalRetentionDirectory();

   /**
    * Sets the file system directory used to store historical backup journal.
    */
   Configuration setJournalRetentionDirectory(String dir);

   File getJournalRetentionLocation();

   /** The retention period for the journal in milliseconds (always in milliseconds, a conversion is performed on set) */
   long getJournalRetentionPeriod();

   Configuration setJournalRetentionPeriod(TimeUnit unit, long limit);

   long getJournalRetentionMaxBytes();

   Configuration setJournalRetentionMaxBytes(long bytes);

   /**
    * Returns the type of journal used by this server ({@code NIO}, {@code ASYNCIO} or {@code MAPPED}).
    * <br>
    * Default value is ASYNCIO.
    */
   JournalType getJournalType();

   /**
    * Sets the type of journal used by this server (either {@code NIO} or {@code ASYNCIO}).
    */
   Configuration setJournalType(JournalType type);

   /**
    * Returns whether the journal is synchronized when receiving transactional data. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_SYNC_TRANSACTIONAL}.
    */
   boolean isJournalSyncTransactional();

   /**
    * Sets whether the journal is synchronized when receiving transactional data.
    */
   Configuration setJournalSyncTransactional(boolean sync);

   /**
    * Returns whether the journal is synchronized when receiving non-transactional data. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_SYNC_NON_TRANSACTIONAL}.
    */
   boolean isJournalSyncNonTransactional();

   /**
    * Sets whether the journal is synchronized when receiving non-transactional data.
    */
   Configuration setJournalSyncNonTransactional(boolean sync);

   /**
    * Returns the size (in bytes) of each journal files. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_FILE_SIZE}.
    */
   int getJournalFileSize();

   /**
    * Sets the size (in bytes) of each journal files.
    */
   Configuration setJournalFileSize(int size);

   /**
    * Returns the minimal number of journal files before compacting. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_COMPACT_MIN_FILES}.
    */
   int getJournalCompactMinFiles();

   /**
    * Sets the minimal number of journal files before compacting.
    */
   Configuration setJournalCompactMinFiles(int minFiles);

   /**
    * Number of files that would be acceptable to keep on a pool. Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_POOL_FILES}.
    */
   int getJournalPoolFiles();

   /**
    * Number of files that would be acceptable to keep on a pool. Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_POOL_FILES}.
    */
   Configuration setJournalPoolFiles(int poolSize);

   /**
    * Returns the percentage of live data before compacting the journal. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_COMPACT_PERCENTAGE}.
    */
   int getJournalCompactPercentage();

   /**
    * @return How long to wait when opening a new Journal file before failing
    */
   int getJournalFileOpenTimeout();

   /**
   * Sets the journal file open timeout
   */
   Configuration setJournalFileOpenTimeout(int journalFileOpenTimeout);

   /**
    * Sets the percentage of live data before compacting the journal.
    */
   Configuration setJournalCompactPercentage(int percentage);

   /**
    * Returns the number of journal files to pre-create. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_MIN_FILES}.
    */
   int getJournalMinFiles();

   /**
    * Sets the number of journal files to pre-create.
    */
   Configuration setJournalMinFiles(int files);

   // AIO and NIO need different values for these params

   /**
    * Returns the maximum number of write requests that can be in the AIO queue at any given time. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_MAX_IO_AIO}.
    */
   int getJournalMaxIO_AIO();

   /**
    * Sets the maximum number of write requests that can be in the AIO queue at any given time.
    */
   Configuration setJournalMaxIO_AIO(int journalMaxIO);

   /**
    * Returns the timeout (in nanoseconds) used to flush buffers in the AIO queue.
    * <br>
    * Default value is {@link org.apache.activemq.artemis.ArtemisConstants#DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO}.
    */
   int getJournalBufferTimeout_AIO();

   /**
    * Sets the timeout (in nanoseconds) used to flush buffers in the AIO queue.
    */
   Configuration setJournalBufferTimeout_AIO(int journalBufferTimeout);

   /** This is the device block size used on writing.
    * This is usually translated as st_blksize from fstat.
    * returning null mans the system should instead make a call on fstat and use st_blksize.
    *  The intention of this setting was to bypass the value in certain devices that will return a huge number as their block size (e.g. CephFS) */
   Integer getJournalDeviceBlockSize();

   /**
    * @see #getJournalDeviceBlockSize()
    */
   Configuration setJournalDeviceBlockSize(Integer deviceBlockSize);

   /**
    * Returns the buffer size (in bytes) for AIO.
    * <br>
    * Default value is {@link org.apache.activemq.artemis.ArtemisConstants#DEFAULT_JOURNAL_BUFFER_SIZE_AIO}.
    */
   int getJournalBufferSize_AIO();

   /**
    * Sets the buffer size (in bytes) for AIO.
    */
   Configuration setJournalBufferSize_AIO(int journalBufferSize);

   /**
    * Returns the maximum number of write requests for NIO journal. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_MAX_IO_NIO}.
    */
   int getJournalMaxIO_NIO();

   /**
    * Sets the maximum number of write requests for NIO journal.
    */
   Configuration setJournalMaxIO_NIO(int journalMaxIO);

   /**
    * Returns the timeout (in nanoseconds) used to flush buffers in the NIO.
    * <br>
    * Default value is {@link org.apache.activemq.artemis.ArtemisConstants#DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO}.
    */
   int getJournalBufferTimeout_NIO();

   /**
    * Sets the timeout (in nanoseconds) used to flush buffers in the NIO.
    */
   Configuration setJournalBufferTimeout_NIO(int journalBufferTimeout);

   /**
    * Returns the buffer size (in bytes) for NIO.
    * <br>
    * Default value is {@link org.apache.activemq.artemis.ArtemisConstants#DEFAULT_JOURNAL_BUFFER_SIZE_NIO}.
    */
   int getJournalBufferSize_NIO();

   /**
    * Sets the buffer size (in bytes) for NIO.
    */
   Configuration setJournalBufferSize_NIO(int journalBufferSize);

   /**
    * Returns the maximal number of data files before we can start deleting corrupted files instead of moving them to attic.
    * <br>
    * Default value is  {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_JOURNAL_MAX_ATTIC_FILES}.
    */
   int getJournalMaxAtticFiles();

   /**
    * Sets the maximal number of data files before we can start deleting corrupted files instead of moving them to attic.
    */
   Configuration setJournalMaxAtticFiles(int maxAtticFiles);

   /**
    * Returns whether the bindings directory is created on this server startup. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_CREATE_BINDINGS_DIR}.
    */
   boolean isCreateBindingsDir();

   /**
    * Sets whether the bindings directory is created on this server startup.
    */
   Configuration setCreateBindingsDir(boolean create);

   /**
    * Returns whether the journal directory is created on this server startup. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_CREATE_JOURNAL_DIR}.
    */
   boolean isCreateJournalDir();

   /**
    * Sets whether the journal directory is created on this server startup.
    */
   Configuration setCreateJournalDir(boolean create);

   // Undocumented attributes

   boolean isLogJournalWriteRate();

   Configuration setLogJournalWriteRate(boolean rate);

   long getServerDumpInterval();

   Configuration setServerDumpInterval(long interval);

   int getMemoryWarningThreshold();

   Configuration setMemoryWarningThreshold(int memoryWarningThreshold);

   long getMemoryMeasureInterval();

   Configuration setMemoryMeasureInterval(long memoryMeasureInterval);

   // Paging Properties --------------------------------------------------------------------

   /**
    * Returns the file system directory used to store paging files. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_PAGING_DIR}.
    */
   String getPagingDirectory();

   /**
    * Sets the file system directory used to store paging files.
    */
   Configuration setPagingDirectory(String dir);

   /**
    * The paging location related to artemis.instance
    */
   File getPagingLocation();

   // Large Messages Properties ------------------------------------------------------------

   /**
    * Returns the file system directory used to store large messages. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_LARGE_MESSAGES_DIR}.
    */
   String getLargeMessagesDirectory();

   /**
    * The large message location related to artemis.instance
    */
   File getLargeMessagesLocation();

   /**
    * Sets the file system directory used to store large messages.
    */
   Configuration setLargeMessagesDirectory(String directory);

   // Other Properties ---------------------------------------------------------------------

   /**
    * Returns whether wildcard routing is supported by this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_WILDCARD_ROUTING_ENABLED}.
    */
   boolean isWildcardRoutingEnabled();

   /**
    * Sets whether wildcard routing is supported by this server.
    */
   Configuration setWildcardRoutingEnabled(boolean enabled);

   WildcardConfiguration getWildcardConfiguration();

   Configuration setWildCardConfiguration(WildcardConfiguration wildcardConfiguration);

   /**
    * Returns the timeout (in milliseconds) after which transactions is removed from the resource
    * manager after it was created. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_TRANSACTION_TIMEOUT}.
    */
   long getTransactionTimeout();

   /**
    * Sets the timeout (in milliseconds) after which transactions is removed
    * from the resource manager after it was created.
    */
   Configuration setTransactionTimeout(long timeout);

   /**
    * Returns whether message counter is enabled for this server. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MESSAGE_COUNTER_ENABLED}.
    */
   boolean isMessageCounterEnabled();

   /**
    * Sets whether message counter is enabled for this server.
    */
   Configuration setMessageCounterEnabled(boolean enabled);

   /**
    * Returns the sample period (in milliseconds) to take message counter snapshot. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MESSAGE_COUNTER_SAMPLE_PERIOD}.
    */
   long getMessageCounterSamplePeriod();

   /**
    * Sets the sample period to take message counter snapshot.
    *
    * @param period value must be greater than 1000ms
    */
   Configuration setMessageCounterSamplePeriod(long period);

   /**
    * Returns the maximum number of days kept in memory for message counter. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MESSAGE_COUNTER_MAX_DAY_HISTORY}.
    */
   int getMessageCounterMaxDayHistory();

   /**
    * Sets the maximum number of days kept in memory for message counter.
    *
    * @param maxDayHistory value must be greater than 0
    */
   Configuration setMessageCounterMaxDayHistory(int maxDayHistory);

   /**
    * Returns the frequency (in milliseconds) to scan transactions to detect which transactions have
    * timed out. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_TRANSACTION_TIMEOUT_SCAN_PERIOD}.
    */
   long getTransactionTimeoutScanPeriod();

   /**
    * Sets the frequency (in milliseconds)  to scan transactions to detect which transactions
    * have timed out.
    */
   Configuration setTransactionTimeoutScanPeriod(long period);

   /**
    * Returns the frequency (in milliseconds) to scan messages to detect which messages have
    * expired. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MESSAGE_EXPIRY_SCAN_PERIOD}.
    */
   long getMessageExpiryScanPeriod();

   /**
    * Sets the frequency (in milliseconds)  to scan messages to detect which messages
    * have expired.
    */
   Configuration setMessageExpiryScanPeriod(long messageExpiryScanPeriod);

   /**
    * Returns the priority of the thread used to scan message expiration. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MESSAGE_EXPIRY_THREAD_PRIORITY}.
    */
   @Deprecated
   int getMessageExpiryThreadPriority();

   /**
    * Sets the priority of the thread used to scan message expiration.
    */
   @Deprecated
   Configuration setMessageExpiryThreadPriority(int messageExpiryThreadPriority);

   /**
    * Returns the frequency (in milliseconds) to scan addresses and queues to detect which
    * ones should be deleted. <br>
    * Default value is {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_MESSAGE_EXPIRY_SCAN_PERIOD}.
    */
   long getAddressQueueScanPeriod();

   /**
    * Sets the frequency (in milliseconds) to scan addresses and queues to detect which
    * ones should be deleted.
    */
   Configuration setAddressQueueScanPeriod(long addressQueueScanPeriod);

   /**
    * @return A list of AddressSettings per matching to be deployed to the address settings repository
    */
   Map<String, AddressSettings> getAddressesSettings();

   /**
    * @param addressesSettings list of AddressSettings per matching to be deployed to the address
    *                          settings repository
    */
   Configuration setAddressesSettings(Map<String, AddressSettings> addressesSettings);

   Configuration addAddressesSetting(String key, AddressSettings addressesSetting);

   Configuration clearAddressesSettings();

   /**
    * @param roles a list of roles per matching
    */
   Configuration setSecurityRoles(Map<String, Set<Role>> roles);

   /**
    * @return a list of roles per matching
    */
   Map<String, Set<Role>> getSecurityRoles();

   Configuration addSecurityRoleNameMapping(String internalRole, Set<String> externalRoles);

   Map<String, Set<String>> getSecurityRoleNameMappings();

   Configuration putSecurityRoles(String match, Set<Role> roles);

   Configuration setConnectorServiceConfigurations(List<ConnectorServiceConfiguration> configs);

   Configuration addConnectorServiceConfiguration(ConnectorServiceConfiguration config);

   Configuration setSecuritySettingPlugins(List<SecuritySettingPlugin> plugins);

   Configuration addSecuritySettingPlugin(SecuritySettingPlugin plugin);

   @Deprecated
   Configuration setMetricsPlugin(ActiveMQMetricsPlugin plugin);

   Configuration setMetricsConfiguration(MetricsConfiguration metricsConfiguration);

   /**
    * @return list of {@link ConnectorServiceConfiguration}
    */
   List<ConnectorServiceConfiguration> getConnectorServiceConfigurations();

   List<SecuritySettingPlugin> getSecuritySettingPlugins();

   @Deprecated
   ActiveMQMetricsPlugin getMetricsPlugin();

   MetricsConfiguration getMetricsConfiguration();

   /**
    * The default password decoder
    */
   Configuration setPasswordCodec(String codec);

   /**
    * Gets the default password decoder
    */
   String getPasswordCodec();

   /**
    * Sets if passwords should be masked or not. True means the passwords should be masked.
    */
   Configuration setMaskPassword(Boolean maskPassword);

   /**
    * If passwords are masked. True means the passwords are masked.
    */
   Boolean isMaskPassword();

   /*
   * Whether or not that ActiveMQ Artemis should use all protocols available on the classpath. If false only the core protocol will
   * be set, any other protocols will need to be set directly on the ActiveMQServer
   * */
   Configuration setResolveProtocols(boolean resolveProtocols);

   TransportConfiguration[] getTransportConfigurations(String... connectorNames);

   TransportConfiguration[] getTransportConfigurations(List<String> connectorNames);

   /*
   * @see #setResolveProtocols()
   * @return whether ActiveMQ Artemis should resolve and use any Protocols available on the classpath
   * Default value is {@link org.apache.activemq.artemis.api.config.org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#DEFAULT_RESOLVE_PROTOCOLS}.
   * */
   boolean isResolveProtocols();

   Configuration copy() throws Exception;

   Configuration setJournalLockAcquisitionTimeout(long journalLockAcquisitionTimeout);

   long getJournalLockAcquisitionTimeout();

   HAPolicyConfiguration getHAPolicyConfiguration();

   Configuration setHAPolicyConfiguration(HAPolicyConfiguration haPolicyConfiguration);

   /**
    * Set the Artemis instance relative folder for data and stuff.
    */
   void setBrokerInstance(File directory);

   /**
    * Set the Artemis instance relative folder for data and stuff.
    */
   File getBrokerInstance();

   default boolean isJDBC() {
      StoreConfiguration configuration = getStoreConfiguration();
      return (configuration != null && configuration.getStoreType() == StoreConfiguration.StoreType.DATABASE);
   }

   StoreConfiguration getStoreConfiguration();

   Configuration setStoreConfiguration(StoreConfiguration storeConfiguration);

   boolean isPopulateValidatedUser();

   Configuration setPopulateValidatedUser(boolean populateValidatedUser);

   boolean isRejectEmptyValidatedUser();

   Configuration setRejectEmptyValidatedUser(boolean rejectEmptyValidatedUser);

   /**
    * It will return all the connectors in a toString manner for debug purposes.
    */
   String debugConnectors();

   Configuration setConnectionTtlCheckInterval(long connectionTtlCheckInterval);

   long getConnectionTtlCheckInterval();

   URL getConfigurationUrl();

   Configuration setConfigurationUrl(URL configurationUrl);

   long getConfigurationFileRefreshPeriod();

   Configuration setConfigurationFileRefreshPeriod(long configurationFileRefreshPeriod);

   long getGlobalMaxSize();

   Configuration setGlobalMaxSize(long globalMaxSize);

   int getMaxDiskUsage();

   Configuration setMaxDiskUsage(int maxDiskUsage);

   ConfigurationImpl setInternalNamingPrefix(String internalNamingPrefix);

   Configuration setDiskScanPeriod(int diskScanPeriod);

   int getDiskScanPeriod();

   /** A comma separated list of IPs we could use to validate if the network is UP.
    *  In case of none of these Ips are reached (if configured) the server will be shutdown. */
   Configuration setNetworkCheckList(String list);

   String getNetworkCheckList();

   /** A comma separated list of URIs we could use to validate if the network is UP.
    *  In case of none of these Ips are reached (if configured) the server will be shutdown.
    *  The difference from networkCheckList is that we will use HTTP to make this validation. */
   Configuration setNetworkCheckURLList(String uris);

   String getNetworkCheckURLList();

   /** The interval on which we will perform network checks. */
   Configuration setNetworkCheckPeriod(long period);

   long getNetworkCheckPeriod();

   /** Time in ms for how long we should wait for a ping to finish. */
   Configuration setNetworkCheckTimeout(int timeout);

   int getNetworkCheckTimeout();

   /** The NIC name to be used on network checks */
   Configuration setNetworCheckNIC(String nic);

   String getNetworkCheckNIC();

   String getNetworkCheckPingCommand();

   Configuration setNetworkCheckPingCommand(String command);

   String getNetworkCheckPing6Command();

   Configuration setNetworkCheckPing6Command(String command);

   String getInternalNamingPrefix();

   /**
    * Returns the timeout (in nanoseconds) used to sync pages.
    * <br>
    * Default value is {@link org.apache.activemq.artemis.ArtemisConstants#DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO}.
    */
   int getPageSyncTimeout();

   /**
    * Sets the timeout (in nanoseconds) used to sync pages.
    */
   Configuration setPageSyncTimeout(int pageSyncTimeout);

   /**
    * @param plugins
    */
   void registerBrokerPlugins(List<ActiveMQServerBasePlugin> plugins);

   /**
    * @param plugin
    */
   void registerBrokerPlugin(ActiveMQServerBasePlugin plugin);

   /**
    * @param plugin
    */
   void unRegisterBrokerPlugin(ActiveMQServerBasePlugin plugin);

   /**
    * @return
    */
   List<ActiveMQServerBasePlugin> getBrokerPlugins();

   /**
    * @return
    */
   List<ActiveMQServerConnectionPlugin> getBrokerConnectionPlugins();

   /**
    * @return
    */
   List<ActiveMQServerSessionPlugin> getBrokerSessionPlugins();

   /**
    * @return
    */
   List<ActiveMQServerConsumerPlugin> getBrokerConsumerPlugins();

   /**
    * @return
    */
   List<ActiveMQServerAddressPlugin> getBrokerAddressPlugins();

   /**
    * @return
    */
   List<ActiveMQServerQueuePlugin> getBrokerQueuePlugins();

   /**
    * @return
    */
   List<ActiveMQServerBindingPlugin> getBrokerBindingPlugins();

   /**
    * @return
    */
   List<ActiveMQServerMessagePlugin> getBrokerMessagePlugins();

   /**
    * @return
    */
   List<ActiveMQServerBridgePlugin> getBrokerBridgePlugins();

   /**
    * @return
    */
   List<ActiveMQServerCriticalPlugin> getBrokerCriticalPlugins();

   /**
    * @return
    */
   List<ActiveMQServerFederationPlugin> getBrokerFederationPlugins();

   /**
    * @return
    */
   List<FederationConfiguration> getFederationConfigurations();

   /**
    * @return
    */
   List<ActiveMQServerResourcePlugin> getBrokerResourcePlugins();

   String getTemporaryQueueNamespace();

   Configuration setTemporaryQueueNamespace(String temporaryQueueNamespace);

   List<String> getFederationDownstreamAuthorization();

   void setFederationDownstreamAuthorization(List<String> roles);

   Configuration addFederationDownstreamAuthorization(String role);
}
