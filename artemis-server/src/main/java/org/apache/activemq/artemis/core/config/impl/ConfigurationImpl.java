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
package org.apache.activemq.artemis.core.config.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.balancing.BrokerBalancerConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.CoreQueueConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
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
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.jboss.logging.Logger;

public class ConfigurationImpl implements Configuration, Serializable {

   private static final Logger logger = Logger.getLogger(ConfigurationImpl.class);

   public static final JournalType DEFAULT_JOURNAL_TYPE = JournalType.ASYNCIO;

   private static final int DEFAULT_JMS_MESSAGE_SIZE = 1864;

   private static final int RANGE_SIZE_MIN = 0;

   private static final int RANGE_SZIE_MAX = 4;

   private static final long serialVersionUID = 4077088945050267843L;

   private String name = "localhost";

   private boolean persistenceEnabled = ActiveMQDefaultConfiguration.isDefaultPersistenceEnabled();

   private boolean journalDatasync = ActiveMQDefaultConfiguration.isDefaultJournalDatasync();

   protected long fileDeploymentScanPeriod = ActiveMQDefaultConfiguration.getDefaultFileDeployerScanPeriod();

   private boolean persistDeliveryCountBeforeDelivery = ActiveMQDefaultConfiguration.isDefaultPersistDeliveryCountBeforeDelivery();

   private int scheduledThreadPoolMaxSize = ActiveMQDefaultConfiguration.getDefaultScheduledThreadPoolMaxSize();

   private int threadPoolMaxSize = ActiveMQDefaultConfiguration.getDefaultThreadPoolMaxSize();

   private long securityInvalidationInterval = ActiveMQDefaultConfiguration.getDefaultSecurityInvalidationInterval();

   private long authenticationCacheSize = ActiveMQDefaultConfiguration.getDefaultAuthenticationCacheSize();

   private long authorizationCacheSize = ActiveMQDefaultConfiguration.getDefaultAuthorizationCacheSize();

   private boolean securityEnabled = ActiveMQDefaultConfiguration.isDefaultSecurityEnabled();

   private boolean gracefulShutdownEnabled = ActiveMQDefaultConfiguration.isDefaultGracefulShutdownEnabled();

   private long gracefulShutdownTimeout = ActiveMQDefaultConfiguration.getDefaultGracefulShutdownTimeout();

   protected boolean jmxManagementEnabled = ActiveMQDefaultConfiguration.isDefaultJmxManagementEnabled();

   protected String jmxDomain = ActiveMQDefaultConfiguration.getDefaultJmxDomain();

   protected boolean jmxUseBrokerName = ActiveMQDefaultConfiguration.isDefaultJMXUseBrokerName();

   protected long connectionTTLOverride = ActiveMQDefaultConfiguration.getDefaultConnectionTtlOverride();

   protected boolean asyncConnectionExecutionEnabled = ActiveMQDefaultConfiguration.isDefaultAsyncConnectionExecutionEnabled();

   private long messageExpiryScanPeriod = ActiveMQDefaultConfiguration.getDefaultMessageExpiryScanPeriod();

   private int messageExpiryThreadPriority = ActiveMQDefaultConfiguration.getDefaultMessageExpiryThreadPriority();

   private long addressQueueScanPeriod = ActiveMQDefaultConfiguration.getDefaultAddressQueueScanPeriod();

   protected int idCacheSize = ActiveMQDefaultConfiguration.getDefaultIdCacheSize();

   private boolean persistIDCache = ActiveMQDefaultConfiguration.isDefaultPersistIdCache();

   private List<String> incomingInterceptorClassNames = new ArrayList<>();

   private List<String> outgoingInterceptorClassNames = new ArrayList<>();

   protected Map<String, TransportConfiguration> connectorConfigs = new HashMap<>();

   private Set<TransportConfiguration> acceptorConfigs = new HashSet<>();

   protected List<BridgeConfiguration> bridgeConfigurations = new ArrayList<>();

   protected List<DivertConfiguration> divertConfigurations = new ArrayList<>();

   protected List<BrokerBalancerConfiguration> brokerBalancerConfigurations = new ArrayList<>();

   protected List<ClusterConnectionConfiguration> clusterConfigurations = new ArrayList<>();

   protected List<AMQPBrokerConnectConfiguration> amqpBrokerConnectConfigurations = new ArrayList<>();

   protected List<FederationConfiguration> federationConfigurations = new ArrayList<>();

   @Deprecated
   // this can eventually be replaced with List<QueueConfiguration>, but to keep existing semantics it must stay as is for now
   private List<CoreQueueConfiguration> coreQueueConfigurations = new ArrayList<>();

   private List<CoreAddressConfiguration> addressConfigurations = new ArrayList<>();

   protected transient List<BroadcastGroupConfiguration> broadcastGroupConfigurations = new ArrayList<>();

   protected transient Map<String, DiscoveryGroupConfiguration> discoveryGroupConfigurations = new LinkedHashMap<>();

   // Paging related attributes ------------------------------------------------------------

   private String pagingDirectory = ActiveMQDefaultConfiguration.getDefaultPagingDir();

   // File related attributes -----------------------------------------------------------

   private int maxConcurrentPageIO = ActiveMQDefaultConfiguration.getDefaultMaxConcurrentPageIo();

   private boolean readWholePage = ActiveMQDefaultConfiguration.isDefaultReadWholePage();

   protected String largeMessagesDirectory = ActiveMQDefaultConfiguration.getDefaultLargeMessagesDir();

   protected String bindingsDirectory = ActiveMQDefaultConfiguration.getDefaultBindingsDirectory();

   protected boolean createBindingsDir = ActiveMQDefaultConfiguration.isDefaultCreateBindingsDir();

   protected String journalDirectory = ActiveMQDefaultConfiguration.getDefaultJournalDir();

   protected String journalRetentionDirectory = null;

   protected long journalRetentionMaxBytes = 0;

   protected long journalRetentionPeriod;

   protected String nodeManagerLockDirectory = null;

   protected boolean createJournalDir = ActiveMQDefaultConfiguration.isDefaultCreateJournalDir();

   public JournalType journalType = ConfigurationImpl.DEFAULT_JOURNAL_TYPE;

   protected boolean journalSyncTransactional = ActiveMQDefaultConfiguration.isDefaultJournalSyncTransactional();

   protected boolean journalSyncNonTransactional = ActiveMQDefaultConfiguration.isDefaultJournalSyncNonTransactional();

   protected int journalCompactMinFiles = ActiveMQDefaultConfiguration.getDefaultJournalCompactMinFiles();

   protected int journalCompactPercentage = ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage();

   protected int journalFileOpenTimeout = ActiveMQDefaultConfiguration.getDefaultJournalFileOpenTimeout();

   protected int journalFileSize = ActiveMQDefaultConfiguration.getDefaultJournalFileSize();

   protected int journalPoolFiles = ActiveMQDefaultConfiguration.getDefaultJournalPoolFiles();

   protected int journalMinFiles = ActiveMQDefaultConfiguration.getDefaultJournalMinFiles();

   protected int journalMaxAtticFilesFiles = ActiveMQDefaultConfiguration.getDefaultJournalMaxAtticFiles();

   // AIO and NIO need different values for these attributes

   protected int journalMaxIO_AIO = ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio();

   protected int journalBufferTimeout_AIO = ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutAio();

   protected Integer deviceBlockSize = null;

   protected int journalBufferSize_AIO = ActiveMQDefaultConfiguration.getDefaultJournalBufferSizeAio();

   protected int journalMaxIO_NIO = ActiveMQDefaultConfiguration.getDefaultJournalMaxIoNio();

   protected int journalBufferTimeout_NIO = ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutNio();

   protected int journalBufferSize_NIO = ActiveMQDefaultConfiguration.getDefaultJournalBufferSizeNio();

   protected boolean logJournalWriteRate = ActiveMQDefaultConfiguration.isDefaultJournalLogWriteRate();

   private WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();

   private boolean messageCounterEnabled = ActiveMQDefaultConfiguration.isDefaultMessageCounterEnabled();

   private long messageCounterSamplePeriod = ActiveMQDefaultConfiguration.getDefaultMessageCounterSamplePeriod();

   private int messageCounterMaxDayHistory = ActiveMQDefaultConfiguration.getDefaultMessageCounterMaxDayHistory();

   private long transactionTimeout = ActiveMQDefaultConfiguration.getDefaultTransactionTimeout();

   private long transactionTimeoutScanPeriod = ActiveMQDefaultConfiguration.getDefaultTransactionTimeoutScanPeriod();

   private SimpleString managementAddress = ActiveMQDefaultConfiguration.getDefaultManagementAddress();

   private SimpleString managementNotificationAddress = ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress();

   protected String clusterUser = ActiveMQDefaultConfiguration.getDefaultClusterUser();

   protected String clusterPassword = ActiveMQDefaultConfiguration.getDefaultClusterPassword();

   private long serverDumpInterval = ActiveMQDefaultConfiguration.getDefaultServerDumpInterval();

   protected boolean failoverOnServerShutdown = ActiveMQDefaultConfiguration.isDefaultFailoverOnServerShutdown();

   // percentage of free memory which triggers warning from the memory manager
   private int memoryWarningThreshold = ActiveMQDefaultConfiguration.getDefaultMemoryWarningThreshold();

   private long memoryMeasureInterval = ActiveMQDefaultConfiguration.getDefaultMemoryMeasureInterval();

   protected GroupingHandlerConfiguration groupingHandlerConfiguration;

   private Map<String, AddressSettings> addressesSettings = new HashMap<>();

   private Map<String, ResourceLimitSettings> resourceLimitSettings = new HashMap<>();

   private Map<String, Set<Role>> securitySettings = new HashMap<>();

   private List<SecuritySettingPlugin> securitySettingPlugins = new ArrayList<>();

   private MetricsConfiguration metricsConfiguration = null;

   private final List<ActiveMQServerBasePlugin> brokerPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerConnectionPlugin> brokerConnectionPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerSessionPlugin> brokerSessionPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerConsumerPlugin> brokerConsumerPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerAddressPlugin> brokerAddressPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerQueuePlugin> brokerQueuePlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerBindingPlugin> brokerBindingPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerMessagePlugin> brokerMessagePlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerBridgePlugin> brokerBridgePlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerCriticalPlugin> brokerCriticalPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerFederationPlugin> brokerFederationPlugins = new CopyOnWriteArrayList<>();
   private final List<ActiveMQServerResourcePlugin> brokerResourcePlugins = new CopyOnWriteArrayList<>();

   private Map<String, Set<String>> securityRoleNameMappings = new HashMap<>();

   protected List<ConnectorServiceConfiguration> connectorServiceConfigurations = new ArrayList<>();

   private Boolean maskPassword = ActiveMQDefaultConfiguration.isDefaultMaskPassword();

   private transient String passwordCodec;

   private boolean resolveProtocols = ActiveMQDefaultConfiguration.isDefaultResolveProtocols();

   private long journalLockAcquisitionTimeout = ActiveMQDefaultConfiguration.getDefaultJournalLockAcquisitionTimeout();

   private HAPolicyConfiguration haPolicyConfiguration;

   private StoreConfiguration storeConfiguration;

   protected boolean populateValidatedUser = ActiveMQDefaultConfiguration.isDefaultPopulateValidatedUser();

   protected boolean rejectEmptyValidatedUser = ActiveMQDefaultConfiguration.isDefaultRejectEmptyValidatedUser();

   private long connectionTtlCheckInterval = ActiveMQDefaultConfiguration.getDefaultConnectionTtlCheckInterval();

   private URL configurationUrl;

   private long configurationFileRefreshPeriod = ActiveMQDefaultConfiguration.getDefaultConfigurationFileRefreshPeriod();

   private Long globalMaxSize;

   private boolean amqpUseCoreSubscriptionNaming = ActiveMQDefaultConfiguration.getDefaultAmqpUseCoreSubscriptionNaming();

   private int maxDiskUsage = ActiveMQDefaultConfiguration.getDefaultMaxDiskUsage();

   private int diskScanPeriod = ActiveMQDefaultConfiguration.getDefaultDiskScanPeriod();

   private String systemPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemPropertyPrefix();

   private String networkCheckList = ActiveMQDefaultConfiguration.getDefaultNetworkCheckList();

   private String networkURLList = ActiveMQDefaultConfiguration.getDefaultNetworkCheckURLList();

   private long networkCheckPeriod = ActiveMQDefaultConfiguration.getDefaultNetworkCheckPeriod();

   private int networkCheckTimeout = ActiveMQDefaultConfiguration.getDefaultNetworkCheckTimeout();

   private String networkCheckNIC = ActiveMQDefaultConfiguration.getDefaultNetworkCheckNic();

   private String networkCheckPingCommand = NetworkHealthCheck.IPV4_DEFAULT_COMMAND;

   private String networkCheckPing6Command = NetworkHealthCheck.IPV6_DEFAULT_COMMAND;

   private String internalNamingPrefix = ActiveMQDefaultConfiguration.getInternalNamingPrefix();

   private boolean criticalAnalyzer = ActiveMQDefaultConfiguration.getCriticalAnalyzer();

   private CriticalAnalyzerPolicy criticalAnalyzerPolicy = ActiveMQDefaultConfiguration.getCriticalAnalyzerPolicy();

   private long criticalAnalyzerTimeout = ActiveMQDefaultConfiguration.getCriticalAnalyzerTimeout();

   private long criticalAnalyzerCheckPeriod = 0; // non set

   private int pageSyncTimeout = ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutNio();

   private String temporaryQueueNamespace = ActiveMQDefaultConfiguration.getDefaultTemporaryQueueNamespace();


   /**
    * Parent folder for all data folders.
    */
   private File artemisInstance;

   @Override
   public String getJournalRetentionDirectory() {
      return journalRetentionDirectory;
   }

   @Override
   public ConfigurationImpl setJournalRetentionDirectory(String dir) {
      this.journalRetentionDirectory = dir;
      return this;
   }

   @Override
   public File getJournalRetentionLocation() {
      if (journalRetentionDirectory == null) {
         return null;
      } else {
         return subFolder(getJournalRetentionDirectory());
      }
   }

   @Override
   public long getJournalRetentionPeriod() {
      return this.journalRetentionPeriod;
   }

   @Override
   public Configuration setJournalRetentionPeriod(TimeUnit unit, long period) {
      if (period <= 0) {
         this.journalRetentionPeriod = -1;
      } else {
         this.journalRetentionPeriod = unit.toMillis(period);
      }
      return this;
   }

   @Override
   public long getJournalRetentionMaxBytes() {
      return journalRetentionMaxBytes;
   }

   @Override
   public ConfigurationImpl setJournalRetentionMaxBytes(long bytes) {
      this.journalRetentionMaxBytes = bytes;
      return this;
   }

   @Override
   public Configuration setSystemPropertyPrefix(String systemPropertyPrefix) {
      this.systemPropertyPrefix = systemPropertyPrefix;
      return this;
   }

   @Override
   public String getSystemPropertyPrefix() {
      return systemPropertyPrefix;
   }

   @Override
   public Configuration parseSystemProperties() throws Exception {
      parseSystemProperties(System.getProperties());
      return this;
   }

   @Override
   public Configuration parseSystemProperties(Properties properties) throws Exception {
      Map<String, Object> beanProperties = new HashMap<>();

      synchronized (properties) {
         for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (entry.getKey().toString().startsWith(systemPropertyPrefix)) {
               String key = entry.getKey().toString().substring(systemPropertyPrefix.length());
               logger.debug("Setting up config, " + key + "=" + entry.getValue());
               beanProperties.put(key, entry.getValue());
            }
         }
      }

      if (!beanProperties.isEmpty()) {
         BeanSupport.setData(this, beanProperties);
      }

      return this;
   }

   @Override
   public boolean isClustered() {
      return !getClusterConfigurations().isEmpty();
   }

   @Override
   public boolean isPersistenceEnabled() {
      return persistenceEnabled;
   }

   @Override
   public int getMaxDiskUsage() {
      return maxDiskUsage;
   }

   @Override
   public ConfigurationImpl setMaxDiskUsage(int maxDiskUsage) {
      this.maxDiskUsage = maxDiskUsage;
      return this;
   }

   @Override
   public ConfigurationImpl setGlobalMaxSize(long maxSize) {
      this.globalMaxSize = maxSize;
      return this;
   }

   @Override
   public long getGlobalMaxSize() {
      if (globalMaxSize == null) {
         this.globalMaxSize = ActiveMQDefaultConfiguration.getDefaultMaxGlobalSize();
         if (!Env.isTestEnv()) {
            ActiveMQServerLogger.LOGGER.usingDefaultPaging(globalMaxSize);
         }
      }
      return globalMaxSize;
   }

   @Override
   public ConfigurationImpl setPersistenceEnabled(final boolean enable) {
      persistenceEnabled = enable;
      return this;
   }

   @Override
   public boolean isJournalDatasync() {
      return journalDatasync;
   }

   @Override
   public ConfigurationImpl setJournalDatasync(boolean enable) {
      journalDatasync = enable;
      return this;
   }

   @Override
   public long getFileDeployerScanPeriod() {
      return fileDeploymentScanPeriod;
   }

   @Override
   public ConfigurationImpl setFileDeployerScanPeriod(final long period) {
      fileDeploymentScanPeriod = period;
      return this;
   }

   /**
    * @return the persistDeliveryCountBeforeDelivery
    */
   @Override
   public boolean isPersistDeliveryCountBeforeDelivery() {
      return persistDeliveryCountBeforeDelivery;
   }

   @Override
   public ConfigurationImpl setPersistDeliveryCountBeforeDelivery(final boolean persistDeliveryCountBeforeDelivery) {
      this.persistDeliveryCountBeforeDelivery = persistDeliveryCountBeforeDelivery;
      return this;
   }

   @Override
   public int getScheduledThreadPoolMaxSize() {
      return scheduledThreadPoolMaxSize;
   }

   @Override
   public ConfigurationImpl setScheduledThreadPoolMaxSize(final int maxSize) {
      scheduledThreadPoolMaxSize = maxSize;
      return this;
   }

   @Override
   public int getThreadPoolMaxSize() {
      return threadPoolMaxSize;
   }

   @Override
   public ConfigurationImpl setThreadPoolMaxSize(final int maxSize) {
      threadPoolMaxSize = maxSize;
      return this;
   }

   @Override
   public long getSecurityInvalidationInterval() {
      return securityInvalidationInterval;
   }

   @Override
   public ConfigurationImpl setSecurityInvalidationInterval(final long interval) {
      securityInvalidationInterval = interval;
      return this;
   }

   @Override
   public long getAuthenticationCacheSize() {
      return authenticationCacheSize;
   }

   @Override
   public ConfigurationImpl setAuthenticationCacheSize(final long size) {
      authenticationCacheSize = size;
      return this;
   }

   @Override
   public long getAuthorizationCacheSize() {
      return authorizationCacheSize;
   }

   @Override
   public ConfigurationImpl setAuthorizationCacheSize(final long size) {
      authorizationCacheSize = size;
      return this;
   }

   @Override
   public long getConnectionTTLOverride() {
      return connectionTTLOverride;
   }

   @Override
   public ConfigurationImpl setConnectionTTLOverride(final long ttl) {
      connectionTTLOverride = ttl;
      return this;
   }

   @Override
   public boolean isAmqpUseCoreSubscriptionNaming() {
      return amqpUseCoreSubscriptionNaming;
   }

   @Override
   public Configuration setAmqpUseCoreSubscriptionNaming(boolean amqpUseCoreSubscriptionNaming) {
      this.amqpUseCoreSubscriptionNaming = amqpUseCoreSubscriptionNaming;
      return this;
   }


   @Override
   public boolean isAsyncConnectionExecutionEnabled() {
      return asyncConnectionExecutionEnabled;
   }

   @Override
   public ConfigurationImpl setEnabledAsyncConnectionExecution(final boolean enabled) {
      asyncConnectionExecutionEnabled = enabled;
      return this;
   }

   @Override
   public List<String> getIncomingInterceptorClassNames() {
      return incomingInterceptorClassNames;
   }

   @Override
   public ConfigurationImpl setIncomingInterceptorClassNames(final List<String> interceptors) {
      incomingInterceptorClassNames = interceptors;
      return this;
   }

   @Override
   public List<String> getOutgoingInterceptorClassNames() {
      return outgoingInterceptorClassNames;
   }

   @Override
   public ConfigurationImpl setOutgoingInterceptorClassNames(final List<String> interceptors) {
      outgoingInterceptorClassNames = interceptors;
      return this;
   }

   @Override
   public Set<TransportConfiguration> getAcceptorConfigurations() {
      return acceptorConfigs;
   }

   @Override
   public ConfigurationImpl setAcceptorConfigurations(final Set<TransportConfiguration> infos) {
      acceptorConfigs = infos;
      return this;
   }

   @Override
   public ConfigurationImpl addAcceptorConfiguration(final TransportConfiguration infos) {
      acceptorConfigs.add(infos);
      return this;
   }

   @Override
   public ConfigurationImpl addAcceptorConfiguration(final String name, final String uri) throws Exception {
      List<TransportConfiguration> configurations = ConfigurationUtils.parseAcceptorURI(name, uri);

      for (TransportConfiguration config : configurations) {
         addAcceptorConfiguration(config);
      }

      return this;
   }

   @Override
   public ConfigurationImpl clearAcceptorConfigurations() {
      acceptorConfigs.clear();
      return this;
   }

   @Override
   public Map<String, TransportConfiguration> getConnectorConfigurations() {
      return connectorConfigs;
   }

   @Override
   public ConfigurationImpl setConnectorConfigurations(final Map<String, TransportConfiguration> infos) {
      connectorConfigs = infos;
      return this;
   }

   @Override
   public ConfigurationImpl addConnectorConfiguration(final String key, final TransportConfiguration info) {
      connectorConfigs.put(key, info);
      return this;
   }

   @Override
   public ConfigurationImpl addConnectorConfiguration(final String name, final String uri) throws Exception {

      List<TransportConfiguration> configurations = ConfigurationUtils.parseConnectorURI(name, uri);

      for (TransportConfiguration config : configurations) {
         addConnectorConfiguration(name, config);
      }

      return this;
   }

   @Override
   public ConfigurationImpl clearConnectorConfigurations() {
      connectorConfigs.clear();
      return this;
   }

   @Override
   public GroupingHandlerConfiguration getGroupingHandlerConfiguration() {
      return groupingHandlerConfiguration;
   }

   @Override
   public ConfigurationImpl setGroupingHandlerConfiguration(final GroupingHandlerConfiguration groupingHandlerConfiguration) {
      this.groupingHandlerConfiguration = groupingHandlerConfiguration;
      return this;
   }

   @Override
   public List<BridgeConfiguration> getBridgeConfigurations() {
      return bridgeConfigurations;
   }

   @Override
   public ConfigurationImpl setBridgeConfigurations(final List<BridgeConfiguration> configs) {
      bridgeConfigurations = configs;
      return this;
   }

   public ConfigurationImpl addBridgeConfiguration(final BridgeConfiguration config) {
      bridgeConfigurations.add(config);
      return this;
   }

   @Override
   public List<BroadcastGroupConfiguration> getBroadcastGroupConfigurations() {
      return broadcastGroupConfigurations;
   }

   @Override
   public ConfigurationImpl setBroadcastGroupConfigurations(final List<BroadcastGroupConfiguration> configs) {
      broadcastGroupConfigurations = configs;
      return this;
   }

   @Override
   public ConfigurationImpl addBroadcastGroupConfiguration(final BroadcastGroupConfiguration config) {
      broadcastGroupConfigurations.add(config);
      return this;
   }

   @Override
   public List<ClusterConnectionConfiguration> getClusterConfigurations() {
      return clusterConfigurations;
   }

   @Override
   public ConfigurationImpl setClusterConfigurations(final List<ClusterConnectionConfiguration> configs) {
      clusterConfigurations = configs;
      return this;
   }

   @Override
   public ConfigurationImpl addClusterConfiguration(final ClusterConnectionConfiguration config) {
      clusterConfigurations.add(config);
      return this;
   }

   @Override
   public ClusterConnectionConfiguration addClusterConfiguration(String name, String uri) throws Exception {
      ClusterConnectionConfiguration newConfig = new ClusterConnectionConfiguration(new URI(uri)).setName(name);
      clusterConfigurations.add(newConfig);
      return newConfig;
   }

   @Override
   public ConfigurationImpl addAMQPConnection(AMQPBrokerConnectConfiguration amqpBrokerConnectConfiguration) {
      this.amqpBrokerConnectConfigurations.add(amqpBrokerConnectConfiguration);
      return this;
   }

   @Override
   public List<AMQPBrokerConnectConfiguration> getAMQPConnection() {
      return this.amqpBrokerConnectConfigurations;
   }

   @Override
   public ConfigurationImpl clearClusterConfigurations() {
      clusterConfigurations.clear();
      return this;
   }

   @Override
   public List<DivertConfiguration> getDivertConfigurations() {
      return divertConfigurations;
   }

   @Override
   public ConfigurationImpl setDivertConfigurations(final List<DivertConfiguration> configs) {
      divertConfigurations = configs;
      return this;
   }

   @Override
   public ConfigurationImpl addDivertConfiguration(final DivertConfiguration config) {
      divertConfigurations.add(config);
      return this;
   }

   @Override
   public List<BrokerBalancerConfiguration> getBalancerConfigurations() {
      return brokerBalancerConfigurations;
   }

   @Override
   public ConfigurationImpl setBalancerConfigurations(final List<BrokerBalancerConfiguration> configs) {
      brokerBalancerConfigurations = configs;
      return this;
   }

   @Override
   public ConfigurationImpl addBalancerConfiguration(final BrokerBalancerConfiguration config) {
      brokerBalancerConfigurations.add(config);
      return this;
   }

   @Deprecated
   @Override
   public List<CoreQueueConfiguration> getQueueConfigurations() {
      return coreQueueConfigurations;
   }

   @Override
   /**
    * Note: modifying the returned {@code List} will not impact the underlying {@code List}.
    */
   public List<QueueConfiguration> getQueueConfigs() {
      List<QueueConfiguration> result = new ArrayList<>();
      for (CoreQueueConfiguration coreQueueConfiguration : coreQueueConfigurations) {
         result.add(coreQueueConfiguration.toQueueConfiguration());
      }
      return result;
   }

   @Deprecated
   @Override
   public ConfigurationImpl setQueueConfigurations(final List<CoreQueueConfiguration> coreQueueConfigurations) {
      this.coreQueueConfigurations = coreQueueConfigurations;
      return this;
   }

   @Override
   public ConfigurationImpl setQueueConfigs(final List<QueueConfiguration> configs) {
      for (QueueConfiguration queueConfiguration : configs) {
         coreQueueConfigurations.add(CoreQueueConfiguration.fromQueueConfiguration(queueConfiguration));
      }
      return this;
   }

   @Override
   public ConfigurationImpl addQueueConfiguration(final CoreQueueConfiguration config) {
      coreQueueConfigurations.add(config);
      return this;
   }

   @Override
   public ConfigurationImpl addQueueConfiguration(final QueueConfiguration config) {
      coreQueueConfigurations.add(CoreQueueConfiguration.fromQueueConfiguration(config));
      return this;
   }

   @Override
   public List<CoreAddressConfiguration> getAddressConfigurations() {
      return addressConfigurations;
   }

   @Override
   public Configuration setAddressConfigurations(List<CoreAddressConfiguration> configs) {
      this.addressConfigurations = configs;
      return this;
   }

   @Override
   public Configuration addAddressConfiguration(CoreAddressConfiguration config) {
      this.addressConfigurations.add(config);
      return this;
   }

   @Override
   public Map<String, DiscoveryGroupConfiguration> getDiscoveryGroupConfigurations() {
      return discoveryGroupConfigurations;
   }

   @Override
   public ConfigurationImpl setDiscoveryGroupConfigurations(final Map<String, DiscoveryGroupConfiguration> discoveryGroupConfigurations) {
      this.discoveryGroupConfigurations = discoveryGroupConfigurations;
      return this;
   }

   @Override
   public ConfigurationImpl addDiscoveryGroupConfiguration(final String key,
                                                           DiscoveryGroupConfiguration discoveryGroupConfiguration) {
      this.discoveryGroupConfigurations.put(key, discoveryGroupConfiguration);
      return this;
   }

   @Override
   public int getIDCacheSize() {
      return idCacheSize;
   }

   @Override
   public ConfigurationImpl setIDCacheSize(final int idCacheSize) {
      this.idCacheSize = idCacheSize;
      return this;
   }

   @Override
   public boolean isPersistIDCache() {
      return persistIDCache;
   }

   @Override
   public ConfigurationImpl setPersistIDCache(final boolean persist) {
      persistIDCache = persist;
      return this;
   }

   @Override
   public File getBindingsLocation() {
      return subFolder(getBindingsDirectory());
   }

   @Override
   public String getBindingsDirectory() {
      return bindingsDirectory;
   }

   @Override
   public ConfigurationImpl setBindingsDirectory(final String dir) {
      bindingsDirectory = dir;
      return this;
   }

   @Override
   public int getPageMaxConcurrentIO() {
      return maxConcurrentPageIO;
   }

   @Override
   public ConfigurationImpl setPageMaxConcurrentIO(int maxIO) {
      this.maxConcurrentPageIO = maxIO;
      return this;
   }

   @Override
   public boolean isReadWholePage() {
      return readWholePage;
   }

   @Override
   public ConfigurationImpl setReadWholePage(boolean read) {
      readWholePage = read;
      return this;
   }

   @Override
   public File getJournalLocation() {
      return subFolder(getJournalDirectory());
   }

   @Override
   public String getJournalDirectory() {
      return journalDirectory;
   }

   @Override
   public ConfigurationImpl setJournalDirectory(final String dir) {
      journalDirectory = dir;
      return this;
   }

   @Override
   public File getNodeManagerLockLocation() {
      if (nodeManagerLockDirectory == null) {
         return getJournalLocation();
      } else {
         return subFolder(nodeManagerLockDirectory);
      }
   }

   @Override
   public Configuration setNodeManagerLockDirectory(String dir) {
      nodeManagerLockDirectory = dir;
      return this;
   }

   @Override
   public JournalType getJournalType() {
      return journalType;
   }

   @Override
   public ConfigurationImpl setPagingDirectory(final String dir) {
      pagingDirectory = dir;
      return this;
   }

   @Override
   public File getPagingLocation() {
      return subFolder(getPagingDirectory());
   }

   @Override
   public String getPagingDirectory() {
      return pagingDirectory;
   }

   @Override
   public ConfigurationImpl setJournalType(final JournalType type) {
      journalType = type;
      return this;
   }

   @Override
   public boolean isJournalSyncTransactional() {
      return journalSyncTransactional;
   }

   @Override
   public ConfigurationImpl setJournalSyncTransactional(final boolean sync) {
      journalSyncTransactional = sync;
      return this;
   }

   @Override
   public boolean isJournalSyncNonTransactional() {
      return journalSyncNonTransactional;
   }

   @Override
   public ConfigurationImpl setJournalSyncNonTransactional(final boolean sync) {
      journalSyncNonTransactional = sync;
      return this;
   }

   @Override
   public int getJournalFileSize() {
      return journalFileSize;
   }

   @Override
   public ConfigurationImpl setJournalFileSize(final int size) {
      journalFileSize = size;
      return this;
   }

   @Override
   public int getJournalPoolFiles() {
      return journalPoolFiles;
   }

   @Override
   public Configuration setJournalPoolFiles(int poolSize) {
      this.journalPoolFiles = poolSize;
      if (!Env.isTestEnv() && poolSize < 0) {
         ActiveMQServerLogger.LOGGER.useFixedValueOnJournalPoolFiles();
      }
      return this;
   }

   @Override
   public int getJournalMinFiles() {
      return journalMinFiles;
   }

   @Override
   public ConfigurationImpl setJournalMinFiles(final int files) {
      journalMinFiles = files;
      return this;
   }

   @Override
   public boolean isLogJournalWriteRate() {
      return logJournalWriteRate;
   }

   @Override
   public ConfigurationImpl setLogJournalWriteRate(final boolean logJournalWriteRate) {
      this.logJournalWriteRate = logJournalWriteRate;
      return this;
   }

   @Override
   public boolean isCreateBindingsDir() {
      return createBindingsDir;
   }

   @Override
   public ConfigurationImpl setCreateBindingsDir(final boolean create) {
      createBindingsDir = create;
      return this;
   }

   @Override
   public boolean isCreateJournalDir() {
      return createJournalDir;
   }

   @Override
   public ConfigurationImpl setCreateJournalDir(final boolean create) {
      createJournalDir = create;
      return this;
   }

   @Override
   @Deprecated
   public boolean isWildcardRoutingEnabled() {
      return wildcardConfiguration.isRoutingEnabled();
   }

   @Override
   @Deprecated
   public ConfigurationImpl setWildcardRoutingEnabled(final boolean enabled) {
      ActiveMQServerLogger.LOGGER.deprecatedWildcardRoutingEnabled();
      wildcardConfiguration.setRoutingEnabled(enabled);
      return this;
   }

   @Override
   public WildcardConfiguration getWildcardConfiguration() {
      return wildcardConfiguration;
   }

   @Override
   public Configuration setWildCardConfiguration(WildcardConfiguration wildcardConfiguration) {
      this.wildcardConfiguration = wildcardConfiguration;
      return this;
   }

   @Override
   public long getTransactionTimeout() {
      return transactionTimeout;
   }

   @Override
   public ConfigurationImpl setTransactionTimeout(final long timeout) {
      transactionTimeout = timeout;
      return this;
   }

   @Override
   public long getTransactionTimeoutScanPeriod() {
      return transactionTimeoutScanPeriod;
   }

   @Override
   public ConfigurationImpl setTransactionTimeoutScanPeriod(final long period) {
      transactionTimeoutScanPeriod = period;
      return this;
   }

   @Override
   public long getMessageExpiryScanPeriod() {
      return messageExpiryScanPeriod;
   }

   @Override
   public ConfigurationImpl setMessageExpiryScanPeriod(final long messageExpiryScanPeriod) {
      this.messageExpiryScanPeriod = messageExpiryScanPeriod;
      return this;
   }

   @Override
   public int getMessageExpiryThreadPriority() {
      return messageExpiryThreadPriority;
   }

   @Override
   public ConfigurationImpl setMessageExpiryThreadPriority(final int messageExpiryThreadPriority) {
      this.messageExpiryThreadPriority = messageExpiryThreadPriority;
      return this;
   }

   @Override
   public long getAddressQueueScanPeriod() {
      return addressQueueScanPeriod;
   }

   @Override
   public ConfigurationImpl setAddressQueueScanPeriod(final long addressQueueScanPeriod) {
      this.addressQueueScanPeriod = addressQueueScanPeriod;
      return this;
   }

   @Override
   public boolean isSecurityEnabled() {
      return securityEnabled;
   }

   @Override
   public ConfigurationImpl setSecurityEnabled(final boolean enabled) {
      securityEnabled = enabled;
      return this;
   }

   @Override
   public boolean isGracefulShutdownEnabled() {
      return gracefulShutdownEnabled;
   }

   @Override
   public ConfigurationImpl setGracefulShutdownEnabled(final boolean enabled) {
      gracefulShutdownEnabled = enabled;
      return this;
   }

   @Override
   public long getGracefulShutdownTimeout() {
      return gracefulShutdownTimeout;
   }

   @Override
   public ConfigurationImpl setGracefulShutdownTimeout(final long timeout) {
      gracefulShutdownTimeout = timeout;
      return this;
   }

   @Override
   public boolean isJMXManagementEnabled() {
      return jmxManagementEnabled;
   }

   @Override
   public ConfigurationImpl setJMXManagementEnabled(final boolean enabled) {
      jmxManagementEnabled = enabled;
      return this;
   }

   @Override
   public String getJMXDomain() {
      return jmxDomain;
   }

   @Override
   public ConfigurationImpl setJMXDomain(final String domain) {
      jmxDomain = domain;
      return this;
   }

   @Override
   public boolean isJMXUseBrokerName() {
      return jmxUseBrokerName;
   }

   @Override
   public ConfigurationImpl setJMXUseBrokerName(boolean jmxUseBrokerName) {
      this.jmxUseBrokerName = jmxUseBrokerName;
      return this;
   }

   @Override
   public String getLargeMessagesDirectory() {
      return largeMessagesDirectory;
   }

   @Override
   public File getLargeMessagesLocation() {
      return subFolder(getLargeMessagesDirectory());
   }

   @Override
   public ConfigurationImpl setLargeMessagesDirectory(final String directory) {
      largeMessagesDirectory = directory;
      return this;
   }

   @Override
   public boolean isMessageCounterEnabled() {
      return messageCounterEnabled;
   }

   @Override
   public ConfigurationImpl setMessageCounterEnabled(final boolean enabled) {
      messageCounterEnabled = enabled;
      return this;
   }

   @Override
   public long getMessageCounterSamplePeriod() {
      return messageCounterSamplePeriod;
   }

   @Override
   public ConfigurationImpl setMessageCounterSamplePeriod(final long period) {
      messageCounterSamplePeriod = period;
      return this;
   }

   @Override
   public int getMessageCounterMaxDayHistory() {
      return messageCounterMaxDayHistory;
   }

   @Override
   public ConfigurationImpl setMessageCounterMaxDayHistory(final int maxDayHistory) {
      messageCounterMaxDayHistory = maxDayHistory;
      return this;
   }

   @Override
   public SimpleString getManagementAddress() {
      return managementAddress;
   }

   @Override
   public ConfigurationImpl setManagementAddress(final SimpleString address) {
      managementAddress = address;
      return this;
   }

   @Override
   public SimpleString getManagementNotificationAddress() {
      return managementNotificationAddress;
   }

   @Override
   public ConfigurationImpl setManagementNotificationAddress(final SimpleString address) {
      managementNotificationAddress = address;
      return this;
   }

   @Override
   public String getClusterUser() {
      return clusterUser;
   }

   @Override
   public ConfigurationImpl setClusterUser(final String user) {
      clusterUser = user;
      return this;
   }

   @Override
   public String getClusterPassword() {
      return clusterPassword;
   }

   public boolean isFailoverOnServerShutdown() {
      return failoverOnServerShutdown;
   }

   public ConfigurationImpl setFailoverOnServerShutdown(boolean failoverOnServerShutdown) {
      this.failoverOnServerShutdown = failoverOnServerShutdown;
      return this;
   }

   @Override
   public ConfigurationImpl setClusterPassword(final String theclusterPassword) {
      clusterPassword = theclusterPassword;
      return this;
   }

   @Override
   public int getJournalCompactMinFiles() {
      return journalCompactMinFiles;
   }

   @Override
   public int getJournalCompactPercentage() {
      return journalCompactPercentage;
   }

   @Override
   public ConfigurationImpl setJournalCompactMinFiles(final int minFiles) {
      journalCompactMinFiles = minFiles;
      return this;
   }

   @Override
   public int getJournalFileOpenTimeout() {
      return journalFileOpenTimeout;
   }

   @Override
   public Configuration setJournalFileOpenTimeout(int journalFileOpenTimeout) {
      this.journalFileOpenTimeout = journalFileOpenTimeout;
      return this;
   }

   @Override
   public ConfigurationImpl setJournalCompactPercentage(final int percentage) {
      journalCompactPercentage = percentage;
      return this;
   }

   @Override
   public long getServerDumpInterval() {
      return serverDumpInterval;
   }

   @Override
   public ConfigurationImpl setServerDumpInterval(final long intervalInMilliseconds) {
      serverDumpInterval = intervalInMilliseconds;
      return this;
   }

   @Override
   public int getMemoryWarningThreshold() {
      return memoryWarningThreshold;
   }

   @Override
   public ConfigurationImpl setMemoryWarningThreshold(final int memoryWarningThreshold) {
      this.memoryWarningThreshold = memoryWarningThreshold;
      return this;
   }

   @Override
   public long getMemoryMeasureInterval() {
      return memoryMeasureInterval;
   }

   @Override
   public ConfigurationImpl setMemoryMeasureInterval(final long memoryMeasureInterval) {
      this.memoryMeasureInterval = memoryMeasureInterval;
      return this;
   }

   @Override
   public int getJournalMaxIO_AIO() {
      return journalMaxIO_AIO;
   }

   @Override
   public ConfigurationImpl setJournalMaxIO_AIO(final int journalMaxIO) {
      journalMaxIO_AIO = journalMaxIO;
      return this;
   }

   @Override
   public int getJournalBufferTimeout_AIO() {
      return journalBufferTimeout_AIO;
   }

   @Override
   public Integer getJournalDeviceBlockSize() {
      return deviceBlockSize;
   }

   @Override
   public ConfigurationImpl setJournalDeviceBlockSize(Integer deviceBlockSize) {
      this.deviceBlockSize = deviceBlockSize;
      return this;
   }

   @Override
   public ConfigurationImpl setJournalBufferTimeout_AIO(final int journalBufferTimeout) {
      journalBufferTimeout_AIO = journalBufferTimeout;
      return this;
   }

   @Override
   public int getJournalBufferSize_AIO() {
      return journalBufferSize_AIO;
   }

   @Override
   public ConfigurationImpl setJournalBufferSize_AIO(final int journalBufferSize) {
      journalBufferSize_AIO = journalBufferSize;
      return this;
   }

   @Override
   public int getJournalMaxIO_NIO() {
      return journalMaxIO_NIO;
   }

   @Override
   public ConfigurationImpl setJournalMaxIO_NIO(final int journalMaxIO) {
      journalMaxIO_NIO = journalMaxIO;
      return this;
   }

   @Override
   public int getJournalBufferTimeout_NIO() {
      return journalBufferTimeout_NIO;
   }

   @Override
   public ConfigurationImpl setJournalBufferTimeout_NIO(final int journalBufferTimeout) {
      journalBufferTimeout_NIO = journalBufferTimeout;
      return this;
   }

   @Override
   public int getJournalBufferSize_NIO() {
      return journalBufferSize_NIO;
   }

   @Override
   public ConfigurationImpl setJournalBufferSize_NIO(final int journalBufferSize) {
      journalBufferSize_NIO = journalBufferSize;
      return this;
   }

   @Override
   public Map<String, AddressSettings> getAddressesSettings() {
      return addressesSettings;
   }

   @Override
   public ConfigurationImpl setAddressesSettings(final Map<String, AddressSettings> addressesSettings) {
      this.addressesSettings = addressesSettings;
      return this;
   }

   @Override
   public ConfigurationImpl addAddressesSetting(String key, AddressSettings addressesSetting) {
      this.addressesSettings.put(key, addressesSetting);
      return this;
   }

   @Override
   public ConfigurationImpl clearAddressesSettings() {
      this.addressesSettings.clear();
      return this;
   }

   @Override
   public Map<String, ResourceLimitSettings> getResourceLimitSettings() {
      return resourceLimitSettings;
   }

   @Override
   public ConfigurationImpl setResourceLimitSettings(final Map<String, ResourceLimitSettings> resourceLimitSettings) {
      this.resourceLimitSettings = resourceLimitSettings;
      return this;
   }

   @Override
   public ConfigurationImpl addResourceLimitSettings(ResourceLimitSettings resourceLimitSettings) {
      this.resourceLimitSettings.put(resourceLimitSettings.getMatch().toString(), resourceLimitSettings);
      return this;
   }

   @Override
   public Map<String, Set<Role>> getSecurityRoles() {
      for (SecuritySettingPlugin securitySettingPlugin : securitySettingPlugins) {
         Map<String, Set<Role>> settings = securitySettingPlugin.getSecurityRoles();
         if (settings != null) {
            securitySettings.putAll(settings);
         }
      }
      return securitySettings;
   }

   @Override
   public ConfigurationImpl putSecurityRoles(String match, Set<Role> roles) {
      securitySettings.put(match, roles);
      return this;
   }

   @Override
   public ConfigurationImpl setSecurityRoles(final Map<String, Set<Role>> securitySettings) {
      this.securitySettings = securitySettings;
      return this;
   }

   @Override
   public Configuration addSecurityRoleNameMapping(String internalRole, Set<String> externalRoles) {
      if (securityRoleNameMappings.containsKey(internalRole)) {
         securityRoleNameMappings.get(internalRole).addAll(externalRoles);
      } else {
         securityRoleNameMappings.put(internalRole, externalRoles);
      }
      return this;
   }

   @Override
   public Map<String, Set<String>> getSecurityRoleNameMappings() {
      return securityRoleNameMappings;
   }

   @Override
   public List<ConnectorServiceConfiguration> getConnectorServiceConfigurations() {
      return this.connectorServiceConfigurations;
   }

   @Override
   public List<SecuritySettingPlugin> getSecuritySettingPlugins() {
      return this.securitySettingPlugins;
   }

   @Deprecated
   @Override
   public ActiveMQMetricsPlugin getMetricsPlugin() {
      if (metricsConfiguration != null) {
         return metricsConfiguration.getPlugin();
      }
      return null;
   }

   @Override
   public MetricsConfiguration getMetricsConfiguration() {
      return this.metricsConfiguration;
   }

   @Override
   public void registerBrokerPlugins(final List<ActiveMQServerBasePlugin> plugins) {
      plugins.forEach(plugin -> registerBrokerPlugin(plugin));
   }

   @Override
   public void registerBrokerPlugin(final ActiveMQServerBasePlugin plugin) {
      brokerPlugins.add(plugin);
      if (plugin instanceof ActiveMQServerConnectionPlugin) {
         brokerConnectionPlugins.add((ActiveMQServerConnectionPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerSessionPlugin) {
         brokerSessionPlugins.add((ActiveMQServerSessionPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerConsumerPlugin) {
         brokerConsumerPlugins.add((ActiveMQServerConsumerPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerAddressPlugin) {
         brokerAddressPlugins.add((ActiveMQServerAddressPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerQueuePlugin) {
         brokerQueuePlugins.add((ActiveMQServerQueuePlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerBindingPlugin) {
         brokerBindingPlugins.add((ActiveMQServerBindingPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerMessagePlugin) {
         brokerMessagePlugins.add((ActiveMQServerMessagePlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerBridgePlugin) {
         brokerBridgePlugins.add((ActiveMQServerBridgePlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerCriticalPlugin) {
         brokerCriticalPlugins.add((ActiveMQServerCriticalPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerFederationPlugin) {
         brokerFederationPlugins.add((ActiveMQServerFederationPlugin) plugin);
      }
      if (plugin instanceof ActiveMQServerResourcePlugin) {
         brokerResourcePlugins.add((ActiveMQServerResourcePlugin) plugin);
      }
   }

   @Override
   public void unRegisterBrokerPlugin(final ActiveMQServerBasePlugin plugin) {
      brokerPlugins.remove(plugin);
      if (plugin instanceof ActiveMQServerConnectionPlugin) {
         brokerConnectionPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerSessionPlugin) {
         brokerSessionPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerConsumerPlugin) {
         brokerConsumerPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerAddressPlugin) {
         brokerAddressPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerQueuePlugin) {
         brokerQueuePlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerBindingPlugin) {
         brokerBindingPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerMessagePlugin) {
         brokerMessagePlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerBridgePlugin) {
         brokerBridgePlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerCriticalPlugin) {
         brokerCriticalPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerFederationPlugin) {
         brokerFederationPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerResourcePlugin) {
         brokerResourcePlugins.remove(plugin);
      }
   }

   @Override
   public List<ActiveMQServerBasePlugin> getBrokerPlugins() {
      return brokerPlugins;
   }

   @Override
   public List<ActiveMQServerConnectionPlugin> getBrokerConnectionPlugins() {
      return brokerConnectionPlugins;
   }

   @Override
   public List<ActiveMQServerSessionPlugin> getBrokerSessionPlugins() {
      return brokerSessionPlugins;
   }

   @Override
   public List<ActiveMQServerConsumerPlugin> getBrokerConsumerPlugins() {
      return brokerConsumerPlugins;
   }

   @Override
   public List<ActiveMQServerAddressPlugin> getBrokerAddressPlugins() {
      return brokerAddressPlugins;
   }

   @Override
   public List<ActiveMQServerQueuePlugin> getBrokerQueuePlugins() {
      return brokerQueuePlugins;
   }

   @Override
   public List<ActiveMQServerBindingPlugin> getBrokerBindingPlugins() {
      return brokerBindingPlugins;
   }

   @Override
   public List<ActiveMQServerMessagePlugin> getBrokerMessagePlugins() {
      return brokerMessagePlugins;
   }

   @Override
   public List<ActiveMQServerBridgePlugin> getBrokerBridgePlugins() {
      return brokerBridgePlugins;
   }

   @Override
   public List<ActiveMQServerCriticalPlugin> getBrokerCriticalPlugins() {
      return brokerCriticalPlugins;
   }

   @Override
   public List<ActiveMQServerFederationPlugin> getBrokerFederationPlugins() {
      return brokerFederationPlugins;
   }

   @Override
   public List<FederationConfiguration> getFederationConfigurations() {
      return federationConfigurations;
   }

   @Override
   public List<ActiveMQServerResourcePlugin> getBrokerResourcePlugins() {
      return brokerResourcePlugins;
   }

   @Override
   public File getBrokerInstance() {
      if (artemisInstance != null) {
         return artemisInstance;
      }

      String strartemisInstance = System.getProperty("artemis.instance");

      if (strartemisInstance == null) {
         strartemisInstance = System.getProperty("user.dir");
      }

      artemisInstance = new File(strartemisInstance);

      return artemisInstance;
   }

   @Override
   public void setBrokerInstance(File directory) {
      this.artemisInstance = directory;
   }

   public boolean isCheckForLiveServer() {
      if (haPolicyConfiguration instanceof ReplicaPolicyConfiguration) {
         return ((ReplicatedPolicyConfiguration) haPolicyConfiguration).isCheckForLiveServer();
      } else {
         return false;
      }
   }

   public ConfigurationImpl setCheckForLiveServer(boolean checkForLiveServer) {
      if (haPolicyConfiguration instanceof ReplicaPolicyConfiguration) {
         ((ReplicatedPolicyConfiguration) haPolicyConfiguration).setCheckForLiveServer(checkForLiveServer);
      }

      return this;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("Broker Configuration (");
      sb.append("clustered=").append(isClustered()).append(",");
      if (isJDBC()) {
         DatabaseStorageConfiguration dsc = (DatabaseStorageConfiguration) getStoreConfiguration();
         sb.append("jdbcDriverClassName=").append(dsc.getDataSourceProperty("driverClassName")).append(",");
         sb.append("jdbcConnectionUrl=").append(dsc.getDataSourceProperty("url")).append(",");
         sb.append("messageTableName=").append(dsc.getMessageTableName()).append(",");
         sb.append("bindingsTableName=").append(dsc.getBindingsTableName()).append(",");
         sb.append("largeMessageTableName=").append(dsc.getLargeMessageTableName()).append(",");
         sb.append("pageStoreTableName=").append(dsc.getPageStoreTableName()).append(",");
      } else {
         sb.append("journalDirectory=").append(journalDirectory).append(",");
         sb.append("bindingsDirectory=").append(bindingsDirectory).append(",");
         sb.append("largeMessagesDirectory=").append(largeMessagesDirectory).append(",");
         sb.append("pagingDirectory=").append(pagingDirectory);
      }
      sb.append(")");
      return sb.toString();
   }

   @Override
   public ConfigurationImpl setConnectorServiceConfigurations(final List<ConnectorServiceConfiguration> configs) {
      this.connectorServiceConfigurations = configs;
      return this;
   }

   @Override
   public ConfigurationImpl addConnectorServiceConfiguration(final ConnectorServiceConfiguration config) {
      this.connectorServiceConfigurations.add(config);
      return this;
   }

   @Override
   public ConfigurationImpl setSecuritySettingPlugins(final List<SecuritySettingPlugin> plugins) {
      this.securitySettingPlugins = plugins;
      return this;
   }

   @Override
   public ConfigurationImpl addSecuritySettingPlugin(final SecuritySettingPlugin plugin) {
      this.securitySettingPlugins.add(plugin);
      return this;
   }

   @Deprecated
   @Override
   public ConfigurationImpl setMetricsPlugin(final ActiveMQMetricsPlugin plugin) {
      if (metricsConfiguration == null) {
         metricsConfiguration = new MetricsConfiguration();
      }
      metricsConfiguration.setPlugin(plugin);
      return this;
   }

   @Override
   public ConfigurationImpl setMetricsConfiguration(final MetricsConfiguration metricsConfiguration) {
      this.metricsConfiguration = metricsConfiguration;
      return this;
   }

   @Override
   public Boolean isMaskPassword() {
      return maskPassword;
   }

   @Override
   public ConfigurationImpl setMaskPassword(Boolean maskPassword) {
      this.maskPassword = maskPassword;
      return this;
   }

   @Override
   public ConfigurationImpl setPasswordCodec(String codec) {
      passwordCodec = codec;
      return this;
   }

   @Override
   public String getPasswordCodec() {
      return passwordCodec;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public ConfigurationImpl setName(String name) {
      this.name = name;
      return this;
   }

   @Override
   public ConfigurationImpl setResolveProtocols(boolean resolveProtocols) {
      this.resolveProtocols = resolveProtocols;
      return this;
   }

   @Override
   public TransportConfiguration[] getTransportConfigurations(String... connectorNames) {
      return getTransportConfigurations(Arrays.asList(connectorNames));
   }

   @Override
   public TransportConfiguration[] getTransportConfigurations(final List<String> connectorNames) {
      TransportConfiguration[] tcConfigs = (TransportConfiguration[]) Array.newInstance(TransportConfiguration.class, connectorNames.size());
      int count = 0;

      for (String connectorName : connectorNames) {
         TransportConfiguration connector = getConnectorConfigurations().get(connectorName);

         if (connector == null) {
            ActiveMQServerLogger.LOGGER.connectionConfigurationIsNull(connectorName == null ? "null" : connectorName);
            return null;
         }

         tcConfigs[count++] = connector;
      }

      return tcConfigs;
   }

   @Override
   public String debugConnectors() {
      StringWriter stringWriter = new StringWriter();

      try (PrintWriter writer = new PrintWriter(stringWriter)) {
         for (Map.Entry<String, TransportConfiguration> connector : getConnectorConfigurations().entrySet()) {
            writer.println("Connector::" + connector.getKey() + " value = " + connector.getValue());
         }
      }

      return stringWriter.toString();

   }

   @Override
   public boolean isResolveProtocols() {
      return resolveProtocols;
   }

   @Override
   public StoreConfiguration getStoreConfiguration() {
      return storeConfiguration;
   }

   @Override
   public ConfigurationImpl setStoreConfiguration(StoreConfiguration storeConfiguration) {
      this.storeConfiguration = storeConfiguration;
      return this;
   }

   @Override
   public boolean isPopulateValidatedUser() {
      return populateValidatedUser;
   }

   @Override
   public ConfigurationImpl setPopulateValidatedUser(boolean populateValidatedUser) {
      this.populateValidatedUser = populateValidatedUser;
      return this;
   }

   @Override
   public boolean isRejectEmptyValidatedUser() {
      return rejectEmptyValidatedUser;
   }

   @Override
   public Configuration setRejectEmptyValidatedUser(boolean rejectEmptyValidatedUser) {
      this.rejectEmptyValidatedUser = rejectEmptyValidatedUser;
      return this;
   }

   @Override
   public long getConnectionTtlCheckInterval() {
      return connectionTtlCheckInterval;
   }

   @Override
   public ConfigurationImpl setConnectionTtlCheckInterval(long connectionTtlCheckInterval) {
      this.connectionTtlCheckInterval = connectionTtlCheckInterval;
      return this;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((acceptorConfigs == null) ? 0 : acceptorConfigs.hashCode());
      result = prime * result + ((addressesSettings == null) ? 0 : addressesSettings.hashCode());
      result = prime * result + (asyncConnectionExecutionEnabled ? 1231 : 1237);
      result = prime * result + ((bindingsDirectory == null) ? 0 : bindingsDirectory.hashCode());
      result = prime * result + ((bridgeConfigurations == null) ? 0 : bridgeConfigurations.hashCode());
      result = prime * result + ((broadcastGroupConfigurations == null) ? 0 : broadcastGroupConfigurations.hashCode());
      result = prime * result + ((clusterConfigurations == null) ? 0 : clusterConfigurations.hashCode());
      result = prime * result + ((clusterPassword == null) ? 0 : clusterPassword.hashCode());
      result = prime * result + ((clusterUser == null) ? 0 : clusterUser.hashCode());
      result = prime * result + (int) (connectionTTLOverride ^ (connectionTTLOverride >>> 32));
      result = prime * result + ((connectorConfigs == null) ? 0 : connectorConfigs.hashCode());
      result = prime * result + ((connectorServiceConfigurations == null) ? 0 : connectorServiceConfigurations.hashCode());
      result = prime * result + (createBindingsDir ? 1231 : 1237);
      result = prime * result + (createJournalDir ? 1231 : 1237);
      result = prime * result + ((discoveryGroupConfigurations == null) ? 0 : discoveryGroupConfigurations.hashCode());
      result = prime * result + ((divertConfigurations == null) ? 0 : divertConfigurations.hashCode());
      result = prime * result + (failoverOnServerShutdown ? 1231 : 1237);
      result = prime * result + (int) (fileDeploymentScanPeriod ^ (fileDeploymentScanPeriod >>> 32));
      result = prime * result + ((groupingHandlerConfiguration == null) ? 0 : groupingHandlerConfiguration.hashCode());
      result = prime * result + idCacheSize;
      result = prime * result + ((incomingInterceptorClassNames == null) ? 0 : incomingInterceptorClassNames.hashCode());
      result = prime * result + ((jmxDomain == null) ? 0 : jmxDomain.hashCode());
      result = prime * result + (jmxManagementEnabled ? 1231 : 1237);
      result = prime * result + journalBufferSize_AIO;
      result = prime * result + journalBufferSize_NIO;
      result = prime * result + journalBufferTimeout_AIO;
      result = prime * result + journalBufferTimeout_NIO;
      result = prime * result + journalCompactMinFiles;
      result = prime * result + journalCompactPercentage;
      result = prime * result + ((journalDirectory == null) ? 0 : journalDirectory.hashCode());
      result = prime * result + journalFileSize;
      result = prime * result + journalMaxIO_AIO;
      result = prime * result + journalMaxIO_NIO;
      result = prime * result + journalMinFiles;
      result = prime * result + (journalSyncNonTransactional ? 1231 : 1237);
      result = prime * result + (journalSyncTransactional ? 1231 : 1237);
      result = prime * result + ((journalType == null) ? 0 : journalType.hashCode());
      result = prime * result + ((largeMessagesDirectory == null) ? 0 : largeMessagesDirectory.hashCode());
      result = prime * result + (logJournalWriteRate ? 1231 : 1237);
      result = prime * result + ((managementAddress == null) ? 0 : managementAddress.hashCode());
      result = prime * result + ((managementNotificationAddress == null) ? 0 : managementNotificationAddress.hashCode());
      result = prime * result + (maskPassword == null ? 0 : maskPassword.hashCode());
      result = prime * result + maxConcurrentPageIO;
      result = prime * result + (int) (memoryMeasureInterval ^ (memoryMeasureInterval >>> 32));
      result = prime * result + memoryWarningThreshold;
      result = prime * result + (messageCounterEnabled ? 1231 : 1237);
      result = prime * result + messageCounterMaxDayHistory;
      result = prime * result + (int) (messageCounterSamplePeriod ^ (messageCounterSamplePeriod >>> 32));
      result = prime * result + (int) (messageExpiryScanPeriod ^ (messageExpiryScanPeriod >>> 32));
      result = prime * result + messageExpiryThreadPriority;
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((outgoingInterceptorClassNames == null) ? 0 : outgoingInterceptorClassNames.hashCode());
      result = prime * result + ((pagingDirectory == null) ? 0 : pagingDirectory.hashCode());
      result = prime * result + (persistDeliveryCountBeforeDelivery ? 1231 : 1237);
      result = prime * result + (persistIDCache ? 1231 : 1237);
      result = prime * result + (persistenceEnabled ? 1231 : 1237);
//      result = prime * result + ((queueConfigurations == null) ? 0 : queueConfigurations.hashCode());
      result = prime * result + scheduledThreadPoolMaxSize;
      result = prime * result + (securityEnabled ? 1231 : 1237);
      result = prime * result + (populateValidatedUser ? 1231 : 1237);
      result = prime * result + (int) (securityInvalidationInterval ^ (securityInvalidationInterval >>> 32));
      result = prime * result + ((securitySettings == null) ? 0 : securitySettings.hashCode());
      result = prime * result + (int) (serverDumpInterval ^ (serverDumpInterval >>> 32));
      result = prime * result + threadPoolMaxSize;
      result = prime * result + (int) (transactionTimeout ^ (transactionTimeout >>> 32));
      result = prime * result + (int) (transactionTimeoutScanPeriod ^ (transactionTimeoutScanPeriod >>> 32));
      result = prime * result + ((wildcardConfiguration == null) ? 0 : wildcardConfiguration.hashCode());
      result = prime * result + (resolveProtocols ? 1231 : 1237);
      result = prime * result + (int) (journalLockAcquisitionTimeout ^ (journalLockAcquisitionTimeout >>> 32));
      result = prime * result + (int) (connectionTtlCheckInterval ^ (connectionTtlCheckInterval >>> 32));
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (!(obj instanceof ConfigurationImpl))
         return false;
      ConfigurationImpl other = (ConfigurationImpl) obj;
      if (acceptorConfigs == null) {
         if (other.acceptorConfigs != null)
            return false;
      } else if (!acceptorConfigs.equals(other.acceptorConfigs))
         return false;
      if (addressesSettings == null) {
         if (other.addressesSettings != null)
            return false;
      } else if (!addressesSettings.equals(other.addressesSettings))
         return false;
      if (asyncConnectionExecutionEnabled != other.asyncConnectionExecutionEnabled)
         return false;
      if (bindingsDirectory == null) {
         if (other.bindingsDirectory != null)
            return false;
      } else if (!bindingsDirectory.equals(other.bindingsDirectory))
         return false;
      if (bridgeConfigurations == null) {
         if (other.bridgeConfigurations != null)
            return false;
      } else if (!bridgeConfigurations.equals(other.bridgeConfigurations))
         return false;
      if (broadcastGroupConfigurations == null) {
         if (other.broadcastGroupConfigurations != null)
            return false;
      } else if (!broadcastGroupConfigurations.equals(other.broadcastGroupConfigurations))
         return false;

      if (clusterConfigurations == null) {
         if (other.clusterConfigurations != null)
            return false;
      } else if (!clusterConfigurations.equals(other.clusterConfigurations))
         return false;

      if (clusterPassword == null) {
         if (other.clusterPassword != null)
            return false;
      } else if (!clusterPassword.equals(other.clusterPassword))
         return false;
      if (clusterUser == null) {
         if (other.clusterUser != null)
            return false;
      } else if (!clusterUser.equals(other.clusterUser))
         return false;
      if (connectionTTLOverride != other.connectionTTLOverride)
         return false;
      if (connectorConfigs == null) {
         if (other.connectorConfigs != null)
            return false;
      } else if (!connectorConfigs.equals(other.connectorConfigs))
         return false;
      if (connectorServiceConfigurations == null) {
         if (other.connectorServiceConfigurations != null)
            return false;
      } else if (!connectorServiceConfigurations.equals(other.connectorServiceConfigurations))
         return false;
      if (createBindingsDir != other.createBindingsDir)
         return false;
      if (createJournalDir != other.createJournalDir)
         return false;

      if (discoveryGroupConfigurations == null) {
         if (other.discoveryGroupConfigurations != null)
            return false;
      } else if (!discoveryGroupConfigurations.equals(other.discoveryGroupConfigurations))
         return false;
      if (divertConfigurations == null) {
         if (other.divertConfigurations != null)
            return false;
      } else if (!divertConfigurations.equals(other.divertConfigurations))
         return false;
      if (failoverOnServerShutdown != other.failoverOnServerShutdown)
         return false;
      if (fileDeploymentScanPeriod != other.fileDeploymentScanPeriod)
         return false;
      if (groupingHandlerConfiguration == null) {
         if (other.groupingHandlerConfiguration != null)
            return false;
      } else if (!groupingHandlerConfiguration.equals(other.groupingHandlerConfiguration))
         return false;
      if (idCacheSize != other.idCacheSize)
         return false;
      if (incomingInterceptorClassNames == null) {
         if (other.incomingInterceptorClassNames != null)
            return false;
      } else if (!incomingInterceptorClassNames.equals(other.incomingInterceptorClassNames))
         return false;
      if (jmxDomain == null) {
         if (other.jmxDomain != null)
            return false;
      } else if (!jmxDomain.equals(other.jmxDomain))
         return false;
      if (jmxManagementEnabled != other.jmxManagementEnabled)
         return false;
      if (journalBufferSize_AIO != other.journalBufferSize_AIO)
         return false;
      if (journalBufferSize_NIO != other.journalBufferSize_NIO)
         return false;
      if (journalBufferTimeout_AIO != other.journalBufferTimeout_AIO)
         return false;
      if (journalBufferTimeout_NIO != other.journalBufferTimeout_NIO)
         return false;
      if (journalCompactMinFiles != other.journalCompactMinFiles)
         return false;
      if (journalCompactPercentage != other.journalCompactPercentage)
         return false;
      if (journalDirectory == null) {
         if (other.journalDirectory != null)
            return false;
      } else if (!journalDirectory.equals(other.journalDirectory))
         return false;
      if (journalFileSize != other.journalFileSize)
         return false;
      if (journalMaxIO_AIO != other.journalMaxIO_AIO)
         return false;
      if (journalMaxIO_NIO != other.journalMaxIO_NIO)
         return false;
      if (journalMinFiles != other.journalMinFiles)
         return false;
      if (journalSyncNonTransactional != other.journalSyncNonTransactional)
         return false;
      if (journalSyncTransactional != other.journalSyncTransactional)
         return false;
      if (journalType != other.journalType)
         return false;
      if (largeMessagesDirectory == null) {
         if (other.largeMessagesDirectory != null)
            return false;
      } else if (!largeMessagesDirectory.equals(other.largeMessagesDirectory))
         return false;
      if (logJournalWriteRate != other.logJournalWriteRate)
         return false;
      if (managementAddress == null) {
         if (other.managementAddress != null)
            return false;
      } else if (!managementAddress.equals(other.managementAddress))
         return false;
      if (managementNotificationAddress == null) {
         if (other.managementNotificationAddress != null)
            return false;
      } else if (!managementNotificationAddress.equals(other.managementNotificationAddress))
         return false;

      if (this.maskPassword == null) {
         if (other.maskPassword != null)
            return false;
      } else {
         if (!this.maskPassword.equals(other.maskPassword))
            return false;
      }

      if (maxConcurrentPageIO != other.maxConcurrentPageIO)
         return false;
      if (memoryMeasureInterval != other.memoryMeasureInterval)
         return false;
      if (memoryWarningThreshold != other.memoryWarningThreshold)
         return false;
      if (messageCounterEnabled != other.messageCounterEnabled)
         return false;
      if (messageCounterMaxDayHistory != other.messageCounterMaxDayHistory)
         return false;
      if (messageCounterSamplePeriod != other.messageCounterSamplePeriod)
         return false;
      if (messageExpiryScanPeriod != other.messageExpiryScanPeriod)
         return false;
      if (messageExpiryThreadPriority != other.messageExpiryThreadPriority)
         return false;
      if (name == null) {
         if (other.name != null)
            return false;
      } else if (!name.equals(other.name))
         return false;
      if (outgoingInterceptorClassNames == null) {
         if (other.outgoingInterceptorClassNames != null)
            return false;
      } else if (!outgoingInterceptorClassNames.equals(other.outgoingInterceptorClassNames))
         return false;
      if (pagingDirectory == null) {
         if (other.pagingDirectory != null)
            return false;
      } else if (!pagingDirectory.equals(other.pagingDirectory))
         return false;
      if (persistDeliveryCountBeforeDelivery != other.persistDeliveryCountBeforeDelivery)
         return false;
      if (persistIDCache != other.persistIDCache)
         return false;
      if (persistenceEnabled != other.persistenceEnabled)
         return false;
//      if (queueConfigurations == null) {
//         if (other.queueConfigurations != null)
//            return false;
//      } else if (!queueConfigurations.equals(other.queueConfigurations))
//         return false;
      if (scheduledThreadPoolMaxSize != other.scheduledThreadPoolMaxSize)
         return false;
      if (securityEnabled != other.securityEnabled)
         return false;
      if (populateValidatedUser != other.populateValidatedUser)
         return false;
      if (securityInvalidationInterval != other.securityInvalidationInterval)
         return false;
      if (securitySettings == null) {
         if (other.securitySettings != null)
            return false;
      } else if (!securitySettings.equals(other.securitySettings))
         return false;
      if (serverDumpInterval != other.serverDumpInterval)
         return false;
      if (threadPoolMaxSize != other.threadPoolMaxSize)
         return false;
      if (transactionTimeout != other.transactionTimeout)
         return false;
      if (transactionTimeoutScanPeriod != other.transactionTimeoutScanPeriod)
         return false;
      if (wildcardConfiguration == null) {
         if (other.wildcardConfiguration != null)
            return false;
      } else if (!wildcardConfiguration.equals(other.wildcardConfiguration))
         return false;
      if (resolveProtocols != other.resolveProtocols)
         return false;
      if (journalLockAcquisitionTimeout != other.journalLockAcquisitionTimeout)
         return false;
      if (connectionTtlCheckInterval != other.connectionTtlCheckInterval)
         return false;
      if (journalDatasync != other.journalDatasync) {
         return false;
      }

      if (globalMaxSize != null && !globalMaxSize.equals(other.globalMaxSize)) {
         return false;
      }
      if (maxDiskUsage != other.maxDiskUsage) {
         return false;
      }
      if (diskScanPeriod != other.diskScanPeriod) {
         return false;
      }

      return true;
   }

   @Override
   public Configuration copy() throws Exception {

      return AccessController.doPrivileged(new PrivilegedExceptionAction<Configuration>() {
         @Override
         public Configuration run() throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bos);
            os.writeObject(ConfigurationImpl.this);
            Configuration config;
            try (ObjectInputStream ois = new ObjectInputStreamWithClassLoader(new ByteArrayInputStream(bos.toByteArray()))) {
               config = (Configuration) ois.readObject();
            }

            // this is transient because of possible jgroups integration, we need to copy it manually
            config.setBroadcastGroupConfigurations(ConfigurationImpl.this.getBroadcastGroupConfigurations());

            // this is transient because of possible jgroups integration, we need to copy it manually
            config.setDiscoveryGroupConfigurations(ConfigurationImpl.this.getDiscoveryGroupConfigurations());

            return config;
         }
      });

   }

   @Override
   public ConfigurationImpl setJournalLockAcquisitionTimeout(long journalLockAcquisitionTimeout) {
      this.journalLockAcquisitionTimeout = journalLockAcquisitionTimeout;
      return this;
   }

   @Override
   public long getJournalLockAcquisitionTimeout() {
      return journalLockAcquisitionTimeout;
   }

   @Override
   public HAPolicyConfiguration getHAPolicyConfiguration() {
      return haPolicyConfiguration;
   }

   @Override
   public ConfigurationImpl setHAPolicyConfiguration(HAPolicyConfiguration haPolicyConfiguration) {
      this.haPolicyConfiguration = haPolicyConfiguration;
      return this;
   }

   @Override
   public URL getConfigurationUrl() {
      return configurationUrl;
   }

   @Override
   public ConfigurationImpl setConfigurationUrl(URL configurationUrl) {
      this.configurationUrl = configurationUrl;
      return this;
   }

   @Override
   public long getConfigurationFileRefreshPeriod() {
      return configurationFileRefreshPeriod;
   }

   @Override
   public ConfigurationImpl setConfigurationFileRefreshPeriod(long configurationFileRefreshPeriod) {
      this.configurationFileRefreshPeriod = configurationFileRefreshPeriod;
      return this;
   }

   @Override
   public int getDiskScanPeriod() {
      return diskScanPeriod;
   }

   @Override
   public String getInternalNamingPrefix() {
      return internalNamingPrefix;
   }

   @Override
   public ConfigurationImpl setInternalNamingPrefix(String internalNamingPrefix) {
      this.internalNamingPrefix = internalNamingPrefix;
      return this;
   }

   @Override
   public ConfigurationImpl setDiskScanPeriod(int diskScanPeriod) {
      this.diskScanPeriod = diskScanPeriod;
      return this;
   }

   @Override
   public ConfigurationImpl setNetworkCheckList(String list) {
      this.networkCheckList = list;
      return this;
   }

   @Override
   public String getNetworkCheckList() {
      return networkCheckList;
   }

   @Override
   public ConfigurationImpl setNetworkCheckURLList(String urls) {
      this.networkURLList = urls;
      return this;
   }

   @Override
   public String getNetworkCheckURLList() {
      return networkURLList;
   }

   /**
    * The interval on which we will perform network checks.
    */
   @Override
   public ConfigurationImpl setNetworkCheckPeriod(long period) {
      this.networkCheckPeriod = period;
      return this;
   }

   @Override
   public long getNetworkCheckPeriod() {
      return this.networkCheckPeriod;
   }

   /**
    * Time in ms for how long we should wait for a ping to finish.
    */
   @Override
   public ConfigurationImpl setNetworkCheckTimeout(int timeout) {
      this.networkCheckTimeout = timeout;
      return this;
   }

   @Override
   public int getNetworkCheckTimeout() {
      return this.networkCheckTimeout;
   }

   @Override
   public Configuration setNetworCheckNIC(String nic) {
      this.networkCheckNIC = nic;
      return this;
   }

   @Override
   public String getNetworkCheckNIC() {
      return networkCheckNIC;
   }

   @Override
   public String getNetworkCheckPingCommand() {
      return networkCheckPingCommand;
   }

   @Override
   public ConfigurationImpl setNetworkCheckPingCommand(String command) {
      this.networkCheckPingCommand = command;
      return this;
   }

   @Override
   public String getNetworkCheckPing6Command() {
      return networkCheckPing6Command;
   }

   @Override
   public Configuration setNetworkCheckPing6Command(String command) {
      this.networkCheckPing6Command = command;
      return this;
   }

   @Override
   public boolean isCriticalAnalyzer() {
      return criticalAnalyzer;
   }

   @Override
   public Configuration setCriticalAnalyzer(boolean CriticalAnalyzer) {
      this.criticalAnalyzer = CriticalAnalyzer;
      return this;
   }

   @Override
   public long getCriticalAnalyzerTimeout() {
      return criticalAnalyzerTimeout;
   }

   @Override
   public Configuration setCriticalAnalyzerTimeout(long timeout) {
      this.criticalAnalyzerTimeout = timeout;
      return this;
   }

   @Override
   public long getCriticalAnalyzerCheckPeriod() {
      if (criticalAnalyzerCheckPeriod <= 0) {
         this.criticalAnalyzerCheckPeriod = ActiveMQDefaultConfiguration.getCriticalAnalyzerCheckPeriod(criticalAnalyzerTimeout);
      }
      return criticalAnalyzerCheckPeriod;
   }

   @Override
   public Configuration setCriticalAnalyzerCheckPeriod(long checkPeriod) {
      this.criticalAnalyzerCheckPeriod = checkPeriod;
      return this;
   }

   @Override
   public CriticalAnalyzerPolicy getCriticalAnalyzerPolicy() {
      return criticalAnalyzerPolicy;
   }

   @Override
   public Configuration setCriticalAnalyzerPolicy(CriticalAnalyzerPolicy policy) {
      this.criticalAnalyzerPolicy = policy;
      return this;
   }

   @Override
   public int getPageSyncTimeout() {
      return pageSyncTimeout;
   }

   @Override
   public ConfigurationImpl setPageSyncTimeout(final int pageSyncTimeout) {
      this.pageSyncTimeout = pageSyncTimeout;
      return this;
   }

   public static boolean checkoutDupCacheSize(final int windowSize, final int idCacheSize) {
      final int msgNumInFlight = windowSize / DEFAULT_JMS_MESSAGE_SIZE;

      if (msgNumInFlight == 0) {
         return true;
      }

      boolean sizeGood = false;

      if (idCacheSize >= msgNumInFlight) {
         int r = idCacheSize / msgNumInFlight;

         // This setting is here to accomodate the current default setting.
         if ( (r >= RANGE_SIZE_MIN) && (r <= RANGE_SZIE_MAX)) {
            sizeGood = true;
         }
      }
      return sizeGood;
   }

   /**
    * It will find the right location of a subFolder, related to artemisInstance
    */
   public File subFolder(String subFolder) {
      try {
         return getBrokerInstance().toPath().resolve(subFolder).toFile();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public String getTemporaryQueueNamespace() {
      return temporaryQueueNamespace;
   }

   @Override
   public ConfigurationImpl setTemporaryQueueNamespace(final String temporaryQueueNamespace) {
      this.temporaryQueueNamespace = temporaryQueueNamespace;
      return this;
   }

   @Override
   public int getJournalMaxAtticFiles() {
      return journalMaxAtticFilesFiles;
   }

   @Override
   public Configuration setJournalMaxAtticFiles(int maxAtticFiles) {
      this.journalMaxAtticFilesFiles = maxAtticFiles;
      return this;
   }

}
