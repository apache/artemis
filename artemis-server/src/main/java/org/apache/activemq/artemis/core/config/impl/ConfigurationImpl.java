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

import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
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
import org.apache.activemq.artemis.core.config.JaasAppConfiguration;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationBrokerPlugin;
import org.apache.activemq.artemis.core.config.brokerConnectivity.BrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ColocatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.config.routing.NamedPropertyConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
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
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.FluentPropertyBeanIntrospectorWithIgnores;
import org.apache.activemq.artemis.utils.uri.URISupport;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.MappedPropertyDescriptor;
import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.beanutils.expression.DefaultResolver;
import org.apache.commons.beanutils.expression.Resolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.AppConfigurationEntry;

import static org.apache.activemq.artemis.utils.PasswordMaskingUtil.isEncMasked;

public class ConfigurationImpl extends javax.security.auth.login.Configuration implements Configuration, Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final JournalType DEFAULT_JOURNAL_TYPE = JournalType.ASYNCIO;

   public static final String PROPERTY_CLASS_SUFFIX = ".class";

   public static final String REDACTED = "**redacted**";

   private static final String FILTER_QUERY_PARAMETER_KEY = "filter";

   private static final long serialVersionUID = 4077088945050267843L;

   private String name = "localhost";

   private boolean persistenceEnabled = ActiveMQDefaultConfiguration.isDefaultPersistenceEnabled();

   private int maxRedeliveryRecords = ActiveMQDefaultConfiguration.getDefaultMaxRedeliveryRecords();

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

   protected List<ConnectionRouterConfiguration> connectionRouters = new ArrayList<>();

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

   protected boolean largeMessageSync = ActiveMQDefaultConfiguration.isDefaultLargeMessageSync();

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

   private Map<String, AddressSettings> addressSettings = new HashMap<>();

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
   private final List<AMQPFederationBrokerPlugin> brokerAMQPFederationPlugins = new CopyOnWriteArrayList<>();
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

   private Long globalMaxMessages;

   private boolean amqpUseCoreSubscriptionNaming = ActiveMQDefaultConfiguration.getDefaultAmqpUseCoreSubscriptionNaming();

   private int maxDiskUsage = ActiveMQDefaultConfiguration.getDefaultMaxDiskUsage();

   private long minDiskFree = ActiveMQDefaultConfiguration.getDefaultMinDiskFree();

   private int diskScanPeriod = ActiveMQDefaultConfiguration.getDefaultDiskScanPeriod();

   private String systemPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemPropertyPrefix();

   private String brokerPropertiesKeySurround = ActiveMQDefaultConfiguration.getDefaultBrokerPropertiesKeySurround();

   private String brokerPropertiesRemoveValue = ActiveMQDefaultConfiguration.getDefaultBrokerPropertiesRemoveValue();

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

   private String uuidNamespace = ActiveMQDefaultConfiguration.getDefaultUuidNamespace();

   private long mqttSessionScanInterval = ActiveMQDefaultConfiguration.getMqttSessionScanInterval();

   private long mqttSessionStatePersistenceTimeout = ActiveMQDefaultConfiguration.getMqttSessionStatePersistenceTimeout();

   private boolean mqttSessionStatePersistenceEnabled = ActiveMQDefaultConfiguration.getMqttSubscriptionPersistenceEnabled();

   private boolean suppressSessionNotifications = ActiveMQDefaultConfiguration.getDefaultSuppressSessionNotifications();

   private String literalMatchMarkers = ActiveMQDefaultConfiguration.getLiteralMatchMarkers();

   private String viewPermissionMethodMatchPattern = ActiveMQDefaultConfiguration.getViewPermissionMethodMatchPattern();

   private String managementRbacPrefix = ActiveMQDefaultConfiguration.getManagementRbacPrefix();

   private boolean managementMessagesRbac = ActiveMQDefaultConfiguration.getManagementMessagesRbac();

   private int mirrorAckManagerQueueAttempts = ActiveMQDefaultConfiguration.getMirrorAckManagerQueueAttempts();

   private int mirrorAckManagerPageAttempts = ActiveMQDefaultConfiguration.getMirrorAckManagerPageAttempts();

   private boolean mirrorAckManagerWarnUnacked = ActiveMQDefaultConfiguration.getMirrorAckManagerWarnUnacked();

   private int mirrorAckManagerRetryDelay = ActiveMQDefaultConfiguration.getMirrorAckManagerRetryDelay();

   private boolean mirrorPageTransaction = ActiveMQDefaultConfiguration.getMirrorPageTransaction();

   private int globalMaxSizePercentOfJvmMaxMemory = ActiveMQDefaultConfiguration.DEFAULT_GLOBAL_MAX_MEMORY_PERCENT;

   private boolean purgePageFolders = ActiveMQDefaultConfiguration.getPurgePageFolders();

   private Map<String, JaasAppConfiguration> jaasConfigs = new ConcurrentHashMap<>();

   /**
    * Parent folder for all data folders.
    */
   private File artemisInstance;
   private transient JsonObject jsonStatus = JsonLoader.createObjectBuilder().build();
   private final Set<String> keysToRedact = new HashSet<>();
   private static final Pattern defaultPropertiesFileNamePattern = Pattern.compile(".*\\.(json|properties)");

   private JsonObject getJsonStatus() {
      if (jsonStatus == null) {
         jsonStatus = JsonLoader.createObjectBuilder().build();
      }
      return jsonStatus;
   }

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

   // from properties, as milli
   public void setJournalRetentionPeriod(long periodMillis) {
      if (periodMillis <= 0) {
         this.journalRetentionPeriod = -1;
      } else {
         this.journalRetentionPeriod = periodMillis;
      }
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

   public String getBrokerPropertiesKeySurround() {
      return brokerPropertiesKeySurround;
   }

   public void setBrokerPropertiesKeySurround(String brokerPropertiesKeySurround) {
      this.brokerPropertiesKeySurround = brokerPropertiesKeySurround;
   }

   public String getBrokerPropertiesRemoveValue() {
      return brokerPropertiesRemoveValue;
   }

   @Override
   public Configuration parseProperties(String pathsToProperties) throws Exception {
      // system property overrides location of file(s)
      pathsToProperties = resolvePropertiesSources(pathsToProperties);
      if (pathsToProperties != null) {
         for (String path : pathsToProperties.split(",")) {
            Pattern patternToUse = defaultPropertiesFileNamePattern;
            if (URISupport.containsQuery(path)) {
               Map<String, String> parameters = URISupport.parseQuery(path);
               if (parameters.containsKey(FILTER_QUERY_PARAMETER_KEY)) {
                  patternToUse = Pattern.compile(parameters.get(FILTER_QUERY_PARAMETER_KEY));
               }
               path = URISupport.stripQuery(path);
            }
            if (path.endsWith("/")) {
               // treat as a directory and parse every property file in alphabetical order
               File dir = new File(path);
               if (dir.exists()) {
                  final Pattern forLambda = patternToUse;
                  String[] files = dir.list((file, s) -> forLambda.matcher(s).matches());
                  if (files != null && files.length > 0) {
                     Arrays.sort(files);
                     for (String fileName : files) {
                        parseFileProperties(new File(dir, fileName));
                     }
                  }
               }
            } else {
               parseFileProperties(new File(path));
            }
         }
      }
      parsePrefixedProperties(System.getProperties(), systemPropertyPrefix);
      return this;
   }

   public void parseFileProperties(File file) throws Exception {
      final ConfigurationImpl configuration = this;
      InsertionOrderedProperties brokerProperties = new InsertionOrderedProperties();
      try (CheckedInputStream checkedInputStream = new CheckedInputStream(new FileInputStream(file), new Adler32())) {
         try {
            if (file.getName().endsWith(".json")) {
               brokerProperties.loadJson(configuration, checkedInputStream);
            } else {
               brokerProperties.load(checkedInputStream);
            }
            brokerProperties.setFileChecksum(checkedInputStream.getChecksum().getValue());
         } catch (Exception readOrParseError) {
            logger.debug("Properties config load error on file {}, {}", file.getName(), readOrParseError);
            updateApplyStatus(file.getName(), Map.of("loadError", readOrParseError.toString()));
            return;
         }
      }
      parsePrefixedProperties(this, file.getName(), brokerProperties, null);
   }

   public void parsePrefixedProperties(Properties properties, String prefix) throws Exception {
      parsePrefixedProperties(this, "system-" + prefix, properties, prefix);
   }

   @Override
   public void parsePrefixedProperties(Object target, String name, Properties properties, String prefix) throws Exception {
      Map<String, Object> beanProperties = new LinkedHashMap<>();
      final Checksum checksum = new Adler32();
      synchronized (properties) {
         String key = null;
         for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            key = entry.getKey().toString();
            if (prefix != null) {
               if (!key.startsWith(prefix)) {
                  continue;
               }
               key = entry.getKey().toString().substring(prefix.length());
            }
            String value = entry.getValue().toString();

            checksum.update(key.getBytes(StandardCharsets.UTF_8));
            checksum.update('=');
            checksum.update(value.getBytes(StandardCharsets.UTF_8));

            value = XMLUtil.replaceSystemPropsInString(value);
            key = XMLUtil.replaceSystemPropsInString(key);
            final boolean masked = isEncMasked(value);
            if (masked) {
               value = PasswordMaskingUtil.resolveMask(null, value, getPasswordCodec());
               keysToRedact.add(key);
            }
            logger.debug("Property config, {}={}", key, (masked || shouldRedact(key)) ? REDACTED : value);
            beanProperties.put(key, value);
         }
      }
      long fileAlder32 = 0;
      if (properties instanceof InsertionOrderedProperties insertionOrderedProperties) {
         fileAlder32 = insertionOrderedProperties.getFileChecksum();
      }
      updateReadPropertiesStatus(name, checksum.getValue(), fileAlder32);

      if (!beanProperties.isEmpty()) {
         populateWithProperties(target, name, beanProperties);
      }
      if (!jaasConfigs.isEmpty()) {
         initJaasConfigOverride();
      }
   }

   private javax.security.auth.login.Configuration defaultJaasConfiguration = null;
   private void initJaasConfigOverride() {
      if (defaultJaasConfiguration == null) {
         defaultJaasConfiguration = javax.security.auth.login.Configuration.getConfiguration();
         javax.security.auth.login.Configuration.setConfiguration(this);
      }
   }

   @Override
   public AppConfigurationEntry[] getAppConfigurationEntry(String realm) {
      if (getJaasConfigs().containsKey(realm)) {
         return JaasAppConfiguration.asAppConfigurationEntry(getJaasConfigs().get(realm));
      } else {
         return defaultJaasConfiguration.getAppConfigurationEntry(realm);
      }
   }

   public void populateWithProperties(final Object target, final String propsId, Map<String, Object> beanProperties) throws InvocationTargetException, IllegalAccessException {
      CollectionAutoFillPropertiesUtil autoFillCollections = new CollectionAutoFillPropertiesUtil(getBrokerPropertiesRemoveValue(beanProperties));
      BeanUtilsBean beanUtils = new BeanUtilsBean(new ConvertUtilsBean(), autoFillCollections) {

         // initialize the given property of the given bean with a new instance of the property class
         private Object initProperty(Object bean, String name) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
            PropertyDescriptor descriptor = getPropertyUtils().getPropertyDescriptor(bean, name);
            if (descriptor == null) {
               throw new InvocationTargetException(null, "No accessor method descriptor for: " + name + " on: " + bean.getClass());
            }

            Method writeMethod = descriptor.getWriteMethod();
            if (writeMethod == null) {
               throw new InvocationTargetException(null, "No Write method for: " + name + " on: " + bean.getClass());
            }

            Object propertyInstance;
            try {
               propertyInstance = descriptor.getPropertyType().getDeclaredConstructor().newInstance();
            } catch (InstantiationException e) {
               throw new InvocationTargetException(e);
            }

            writeMethod.invoke(bean, propertyInstance);

            return propertyInstance;
         }

         // override to treat missing properties as errors, not skip as the default impl does
         @Override
         public void setProperty(final Object bean, String name, final Object value) throws InvocationTargetException, IllegalAccessException {
            {
               if (logger.isDebugEnabled()) {
                  logger.debug("setProperty on {}, name: {}, value: {}", bean.getClass(), name, value);
               }
               // Resolve any nested expression to get the actual target bean
               Object target = bean;
               final Resolver resolver = getPropertyUtils().getResolver();
               while (resolver.hasNested(name)) {
                  try {
                     String nextName = resolver.next(name);
                     Object nextTarget = getPropertyUtils().getProperty(target, nextName);
                     if (nextTarget == null) {
                        if (resolver.isMapped(nextName)) {
                           throw new InvocationTargetException(null, "Entry does not exist in: " + resolver.getProperty(name) + " for mapped key: " + resolver.getKey(name));
                        }
                        nextTarget = initProperty(target, nextName);
                     }
                     target = nextTarget;
                     name = resolver.remove(name);
                  } catch (final NoSuchMethodException e) {
                     throw new InvocationTargetException(e, "No getter for property:" + name + ", on: " + bean);
                  }
               }
               logger.trace("resolved target, bean: {}, name: {}", target.getClass(), name);

               final String propName = resolver.getProperty(name); // Simple name of target property
               if (autoFillCollections.isRemoveValue(value)) {
                  logger.trace("removing from target, bean: {}, name: {}", target.getClass(), name);

                  // we may do a further get but no longer want to reference our nested collection stack
                  if (!autoFillCollections.collections.isEmpty()) {
                     autoFillCollections.collections.pop();
                  }
                  if (target instanceof Map targetMap) {
                     Iterator<Map.Entry<String, Object>> i = targetMap.entrySet().iterator();
                     while (i.hasNext()) {
                        String key = i.next().getKey();
                        if (propName.equals(key)) {
                           i.remove();
                           break;
                        }
                     }
                  } else if (target instanceof Collection targetCollection) {
                     try {
                        autoFillCollections.removeByNameProperty(propName, targetCollection);
                     } catch (NoSuchMethodException e) {
                        throw new InvocationTargetException(e, "Can only remove named entries from collections or maps. property: " + name + ", on: " + target);
                     }
                  } else {
                     // empty the collection referenced by this property
                     try {
                        target = getPropertyUtils().getProperty(target, name);
                     } catch (NoSuchMethodException e) {
                        throw new InvocationTargetException(e, "Can only clear entries from collection or map properties that are accessible. property: " + name + ", on: " + target);
                     }
                     if (target instanceof Map mapToClear) {
                        mapToClear.clear();
                     } else if (target instanceof Collection collectionToClear) {
                        collectionToClear.clear();
                     } else {
                        throw new InvocationTargetException(null, "Can only clear entries from collection or map properties. property: " + name + ", on: " + target);
                     }
                  }

                  logger.trace("removed from target, bean: {}, name: {}", target.getClass(), name);
                  return;
               }

               Class<?> type = null;                         // Java type of target property
               final int index = resolver.getIndex(name);         // Indexed subscript value (if any)
               final String key = resolver.getKey(name);           // Mapped key value (if any)

               // Calculate the property type
               if (target instanceof DynaBean) {
                  throw new InvocationTargetException(null, "Cannot determine DynaBean type to access: " + name + " on: " + target);
               } else if (target instanceof Map) {
                  type = Object.class;
               } else if (target != null && target.getClass().isArray() && index >= 0) {
                  type = Array.get(target, index).getClass();
               } else {
                  PropertyDescriptor descriptor = null;
                  try {
                     descriptor = getPropertyUtils().getPropertyDescriptor(target, name);
                     if (descriptor == null) {
                        throw new InvocationTargetException(null, "No accessor method descriptor for: " + name + " on: " + target.getClass());
                     }
                  } catch (final NoSuchMethodException e) {
                     throw new InvocationTargetException(e, "Failed to get descriptor for: " + name + " on: " + target.getClass());
                  }
                  if (descriptor instanceof MappedPropertyDescriptor mappedPropertyDescriptor) {
                     if (mappedPropertyDescriptor.getMappedWriteMethod() == null) {
                        throw new InvocationTargetException(null, "No mapped Write method for: " + name + " on: " + target.getClass());
                     }
                     type = mappedPropertyDescriptor.getMappedPropertyType();
                  } else if (index >= 0 && descriptor instanceof IndexedPropertyDescriptor indexedPropertyDescriptor) {
                     if (indexedPropertyDescriptor.getIndexedWriteMethod() == null) {
                        throw new InvocationTargetException(null, "No indexed Write method for: " + name + " on: " + target.getClass());
                     }
                     type = indexedPropertyDescriptor.getIndexedPropertyType();
                  } else if (index >= 0 && List.class.isAssignableFrom(descriptor.getPropertyType())) {
                     type = Object.class;
                  } else if (key != null) {
                     if (descriptor.getReadMethod() == null) {
                        throw new InvocationTargetException(null, "No Read method for: " + name + " on: " + target.getClass());
                     }
                     type = (value == null) ? Object.class : value.getClass();
                  } else {
                     if (descriptor.getWriteMethod() == null) {
                        throw new InvocationTargetException(null, "No Write method for: " + name + " on: " + target.getClass());
                     }
                     type = descriptor.getPropertyType();
                  }
               }

               // Convert the specified value to the required type
               Object newValue = null;
               if (type.isArray() && (index < 0)) { // Scalar value into array
                  if (value == null) {
                     final String[] values = new String[1];
                     values[0] = null;
                     newValue = getConvertUtils().convert(values, type);
                  } else if (value instanceof String) {
                     newValue = getConvertUtils().convert(value, type);
                  } else if (value instanceof String[] strings) {
                     newValue = getConvertUtils().convert(strings, type);
                  } else {
                     newValue = convert(value, type);
                  }
               } else if (type.isArray()) {         // Indexed value into array
                  if (value instanceof String || value == null) {
                     newValue = getConvertUtils().convert((String) value, type.getComponentType());
                  } else if (value instanceof String[] strings) {
                     newValue = getConvertUtils().convert(strings[0], type.getComponentType());
                  } else {
                     newValue = convert(value, type.getComponentType());
                  }
               } else {                             // Value into scalar
                  if (value instanceof String possibleDotClassValue) {
                     if (type != String.class && isClassProperty(possibleDotClassValue)) {
                        final String clazzName = extractPropertyClassName(possibleDotClassValue);
                        try {
                           newValue = ClassloadingUtil.getInstanceWithTypeCheck(clazzName, type, this.getClass().getClassLoader());
                        } catch (Exception e) {
                           throw new InvocationTargetException(e, " for dot class value: " + possibleDotClassValue + ", on: " + bean);
                        }
                     } else {
                        newValue = getConvertUtils().convert(possibleDotClassValue, type);
                     }
                  } else if (value instanceof String[] strings) {
                     newValue = getConvertUtils().convert(strings[0], type);
                  } else {
                     newValue = convert(value, type);
                  }
               }

               // Invoke the setter method
               try {
                  getPropertyUtils().setProperty(target, name, newValue);
               } catch (final NoSuchMethodException e) {
                  throw new InvocationTargetException(e, "Cannot set: " + propName + " on: " + target.getClass());
               }
            }
         }
      };
      autoFillCollections.setBeanUtilsBean(beanUtils);
      // nested property keys delimited by . and enclosed by '"' if they key's themselves contain dots
      beanUtils.getPropertyUtils().setResolver(new SurroundResolver(getBrokerPropertiesKeySurround(beanProperties)));
      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            return (T) SimpleString.of(value.toString());
         }
      }, SimpleString.class);

      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            NamedPropertyConfiguration instance = new NamedPropertyConfiguration();
            instance.setName(value.toString());
            instance.setProperties(new HashMap<>());
            return (T) instance;
         }
      }, NamedPropertyConfiguration.class);

      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            //we only care about DATABASE type as it is the only one used
            if (StoreConfiguration.StoreType.DATABASE.toString().equals(value)) {
               return (T) new DatabaseStorageConfiguration();
            }
            throw ActiveMQMessageBundle.BUNDLE.unsupportedStorePropertyType();
         }
      }, StoreConfiguration.class);

      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            List<String> convertedValue = new ArrayList<>();
            for (String entry : value.toString().split(",")) {
               convertedValue.add(entry);
            }
            return (T) convertedValue;
         }
      }, java.util.List.class);

      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            Set convertedValue = new HashSet();
            for (String entry : value.toString().split(",")) {
               convertedValue.add(entry);
            }
            return (T) convertedValue;
         }
      }, java.util.Set.class);

      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            Map convertedValue = new HashMap();
            for (String entry : value.toString().split(",")) {
               if (!entry.isBlank()) {
                  String[] kv = entry.split("=");
                  if (2 != kv.length) {
                     throw new IllegalArgumentException("map value " + value + " not in k=v format");
                  }
                  convertedValue.put(kv[0], kv[1]);
               }
            }
            return (T) convertedValue;
         }
      }, java.util.Map.class);

      // support 25K or 25m etc like xml config
      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            return (T) (Long) ByteUtil.convertTextBytes(value.toString());
         }
      }, Long.TYPE);

      beanUtils.getConvertUtils().register(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            HAPolicyConfiguration.TYPE haPolicyType =
               HAPolicyConfiguration.TYPE.valueOf(value.toString());

            return switch (haPolicyType) {
               case PRIMARY_ONLY -> (T) new LiveOnlyPolicyConfiguration();
               case REPLICATION_PRIMARY_QUORUM_VOTING -> (T) new ReplicatedPolicyConfiguration();
               case REPLICATION_BACKUP_QUORUM_VOTING -> (T) new ReplicaPolicyConfiguration();
               case SHARED_STORE_PRIMARY -> (T) new SharedStorePrimaryPolicyConfiguration();
               case SHARED_STORE_BACKUP -> (T) new SharedStoreBackupPolicyConfiguration();
               case COLOCATED -> (T) new ColocatedPolicyConfiguration();
               case REPLICATION_PRIMARY_LOCK_MANAGER -> (T) ReplicationPrimaryPolicyConfiguration.withDefault();
               case REPLICATION_BACKUP_LOCK_MANAGER -> (T) ReplicationBackupPolicyConfiguration.withDefault();
            };
         }
      }, HAPolicyConfiguration.class);

      BeanSupport.customise(beanUtils);

      logger.trace("populate: bean: {} with {}", this, beanProperties);

      Map<String, String> errors = new LinkedHashMap<>();
      // Loop through the property name/value pairs to be set
      for (final Map.Entry<String, ? extends Object> entry : beanProperties.entrySet()) {
         // Identify the property name and value(s) to be assigned
         final String name = entry.getKey();
         try {
            if (logger.isDebugEnabled()) {
               logger.debug("set property target={}, name = {}, value = {}", target.getClass(), name, entry.getValue());
            }
            // Perform the assignment for this property
            beanUtils.setProperty(target, name, entry.getValue());
         } catch (InvocationTargetException invocationTargetException) {
            logger.trace("failed to populate property with key: {}", name, invocationTargetException);
            Throwable toLog = invocationTargetException;
            if (invocationTargetException.getCause() != null) {
               toLog = invocationTargetException.getCause();
            }
            trackError(errors, entry, toLog);

         } catch (Exception oops) {
            trackError(errors, entry, oops);
         }
      }
      updateApplyStatus(propsId, errors);
   }

   @Override
   public void exportAsProperties(File file) throws Exception {
      try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
         writeProperties(writer);
      }
   }

   @Override
   public Map<String, JaasAppConfiguration> getJaasConfigs() {
      return jaasConfigs;
   }

   public void addJaasConfig(JaasAppConfiguration config) {
      jaasConfigs.put(config.getName(), config);
   }

   private void writeProperties(FileWriter writer) throws Exception {
      final BeanUtilsBean beanUtilsBean = new BeanUtilsBean();
      beanUtilsBean.getPropertyUtils().addBeanIntrospector(new FluentPropertyBeanIntrospectorWithIgnores());

      try (BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
         export(beanUtilsBean, new Stack<String>(), bufferedWriter, this);
      }
   }

   final Set<String> ignored = Set.of(
      // we report errors through the status, it should not typically be set
      "status",
      // we cannot import a map<string,set<string>> property and this feature is only applied by the xml parser
      "securityRoleNameMappings",
      // another xml ism using a deprecated config object
      "queueConfigs",
      "encodeSize",
      // duplicates
      "federationPolicyMap",       // core federation
      "policySets",
      "AMQPConnection",
      "AMQPConnectionConfigurations",
      "combinedParams",
      "type",
      // AMQPBrokerConnectConfiguration
      // needs uri first
      "uri",
      // this relation breaks recursion
      "parent",
      // and connectionElements need to be split
      "connectionElements"
   );
   private void export(BeanUtilsBean beanUtils, Stack<String> nested, BufferedWriter bufferedWriter, Object value) {

      if (value instanceof Collection collection) {
         if (!collection.isEmpty()) {
            // collection of strings, as a comma list
            if (collection.stream().findFirst().orElseThrow() instanceof String) {
               exportKeyValue(nested, bufferedWriter, (String) collection.stream().collect(Collectors.joining(",")));
            } else if (collection instanceof EnumSet enumSet) {
               exportKeyValue(nested, bufferedWriter, (String) enumSet.stream().map(Object::toString).collect(Collectors.joining(",")));
            } else {

               Stream stream  = collection.stream();
               if (collection.stream().findFirst().orElseThrow() instanceof AMQPBrokerConnectionElement amqpBrokerConnectionElement) {
                  // filter type from the shared underlying collection
                  String collectionName = nested.peek();
                  AMQPBrokerConnectionAddressType subsetType = AMQPBrokerConnectionAddressType.valueOf(collectionName.substring(0, collectionName.length() - 1).toUpperCase(Locale.ENGLISH));
                  stream = stream.filter((Object connectionElement)-> subsetType == ((AMQPBrokerConnectionElement)connectionElement).getType());
               }
               // output nested by name
               stream.forEach((Consumer<Object>) o -> {
                  nested.push(extractName(o));
                  export(beanUtils, nested, bufferedWriter, o);
                  nested.pop();
               });
            }
         }
      } else if (value instanceof Map map) {
         if (!map.isEmpty()) {
            // possibly filter
            Stream<Map.Entry<?, ?>> stream = map.entrySet().stream();
            if (map.values().stream().findFirst().orElseThrow() instanceof FederationPolicy) {
               // filter type from the shared underlying collection
               final Class filterOn = "addressPolicies".equals(nested.peek()) ? FederationAddressPolicyConfiguration.class : FederationQueuePolicyConfiguration.class;
               stream = stream.filter((Map.Entry<?, ?> entry)-> filterOn.isAssignableFrom(entry.getClass()));
            }
            stream.forEach(entry -> {
               // nested by name
               nested.push(entry.getKey().toString());
               export(beanUtils, nested, bufferedWriter, entry.getValue());
               nested.pop();
            });
         }
      } else if (isComplexConfigObject(value)) {

         // these need constructor properties or .class values or particular attributes as first entry
         if (value instanceof BrokerConnectConfiguration brokerConnectConfiguration) {
            nested.push("uri"); // is in the ignored list so won't get duplicated
            exportKeyValue(nested, bufferedWriter, brokerConnectConfiguration.getUri());
            nested.pop();
         } else if (value instanceof AMQPBrokerConnectionElement connectionElement) {
            nested.push("type"); // is in the ignored list so won't get duplicated
            exportKeyValue(nested, bufferedWriter, connectionElement.getType().toString());
            nested.pop();
         } else if (value instanceof HAPolicyConfiguration haPolicyConfiguration) {
            exportKeyValue(nested, bufferedWriter, haPolicyConfiguration.getType().toString());
         } else if (value instanceof StoreConfiguration storeConfiguration) {
            exportKeyValue(nested, bufferedWriter, storeConfiguration.getStoreType().toString());
         } else if (value instanceof NamedPropertyConfiguration namedPropertyConfiguration) {
            exportKeyValue(nested, bufferedWriter, namedPropertyConfiguration.getName());
         } else if (value instanceof BroadcastEndpointFactory broadcastEndpointFactory) {
            exportKeyValue(nested, bufferedWriter, broadcastEndpointFactory.getClass().getCanonicalName() + ".class");
         } else if (value instanceof ActiveMQMetricsPlugin plugin) {
            exportKeyValue(nested, bufferedWriter, plugin.getClass().getCanonicalName() + ".class");
            nested.push("init");
            exportKeyValue(nested, bufferedWriter, "");
            nested.pop();
         }
         // recursive export via accessors
         Arrays.stream(beanUtils.getPropertyUtils().getPropertyDescriptors(value)).filter(propertyDescriptor -> {

            if (ignored.contains(propertyDescriptor.getName())) {
               return false;
            }
            final Method descriptorReadMethod = propertyDescriptor.getReadMethod();
            if (descriptorReadMethod == null) {
               return false;
            }
            Method descriptorWriteMethod = propertyDescriptor.getWriteMethod();
            if (descriptorWriteMethod == null) {
               // we can write to a returned simple map or collection ok
               final Class type = propertyDescriptor.getPropertyType();
               return Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type);
            }
            return true;
         }).sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName())).forEach(propertyDescriptor -> {
            Object attributeValue = null;
            try {
               attributeValue = propertyDescriptor.getReadMethod().invoke(value, null);
            } catch (Exception e) {
               throw new RuntimeException("accessing: " + propertyDescriptor.getName()  + "@" + nested, e);
            }
            if (attributeValue != null) {
               nested.push(propertyDescriptor.getName());
               export(beanUtils, nested, bufferedWriter, attributeValue);
               nested.pop();
            }
         });
      } else {
         // string form works ok otherwise
         exportKeyValue(nested, bufferedWriter, value.toString());
      }
   }

   private void exportKeyValue(Stack<String> nested, BufferedWriter bufferedWriter, String value) {
      String key = writeKeyEquals(nested, bufferedWriter);

      try {
         if (shouldRedact(key)) {
            bufferedWriter.write(REDACTED);
         } else {
            bufferedWriter.write(value);
         }
         bufferedWriter.newLine();
      } catch (IOException e) {
         throw new RuntimeException("error accessing: " + nested, e);
      }
   }

   private boolean isComplexConfigObject(Object value) {
      return !(value instanceof SimpleString || value instanceof Enum<?>) && value.getClass().getPackage().getName().contains("artemis");
   }

   private boolean shouldRedact(String name) {
      return name.toUpperCase(Locale.ENGLISH).contains("PASSWORD") || keysToRedact.contains(name);
   }

   private String writeKeyEquals(Stack<String> nested, BufferedWriter bufferedWriter) {

      String key = nested.stream().sequential().map(ConfigurationImpl::quote).collect(Collectors.joining("."));
      try {
         bufferedWriter.write(key);
         bufferedWriter.write("=");
      } catch (IOException e) {
         throw new RuntimeException("error accessing: " + nested, e);
      }
      return key;
   }

   public static String quote(String s) {
      String escaped = s.replace("=", "\\\\=").replace(":", "\\\\:");
      if (s.contains(".")) {
         escaped = "\"" + escaped + "\"";
      }
      return escaped;
   }

   String extractName(Object o) {
      if (o instanceof String s) {
         return s;
      }
      try {
         Method m = o.getClass().getMethod("getName", null);
         if (m != null) {
            Object nameVal = m.invoke(o, null);
            if (nameVal == null) {
               return "name-attribute-must-be-set-for-properties-key-export";
            }
            return nameVal.toString();
         }
      } catch (Exception expectedAndWillDefaultToStringForm) {
      }
      return String.valueOf(o);
   }

   protected static boolean isClassProperty(String property) {
      return property.endsWith(PROPERTY_CLASS_SUFFIX);
   }

   protected static String extractPropertyClassName(String property) {
      return property.substring(0, property.length() - PROPERTY_CLASS_SUFFIX.length());
   }

   private void trackError(Map<String, String> errors, Map.Entry<String, ?> entry, Throwable oops) {
      logger.debug("failed to populate property entry({}), reason: {}", entry, oops);
      errors.put(entry.toString(), oops.toString());
   }

   private synchronized void updateApplyStatus(String propsId, Map<String, String> errors) {
      JsonArrayBuilder errorsObjectArrayBuilder = JsonLoader.createArrayBuilder();
      for (Map.Entry<String, String> entry : errors.entrySet()) {
         errorsObjectArrayBuilder.add(JsonLoader.createObjectBuilder().add("value", entry.getKey()).add("reason", entry.getValue()));
      }

      JsonObject status = getJsonStatus();
      JsonObjectBuilder jsonObjectBuilder =
         JsonUtil.objectBuilderWithValueAtPath("properties/" + propsId + "/errors", errorsObjectArrayBuilder.build());
      this.jsonStatus = JsonUtil.mergeAndUpdate(status, jsonObjectBuilder.build());
   }

   private synchronized void updateReadPropertiesStatus(String propsId, long alder32Hash, long fileAlder32) {
      JsonObjectBuilder propertiesReadStatusBuilder = JsonLoader.createObjectBuilder();
      propertiesReadStatusBuilder.add("alder32", String.valueOf(alder32Hash));
      propertiesReadStatusBuilder.add("fileAlder32", String.valueOf(fileAlder32));
      JsonObjectBuilder jsonObjectBuilder = JsonUtil.objectBuilderWithValueAtPath("properties/" + propsId, propertiesReadStatusBuilder.build());
      JsonObject jsonStatus = getJsonStatus();
      this.jsonStatus = JsonUtil.mergeAndUpdate(jsonStatus, jsonObjectBuilder.build());
   }

   private String getBrokerPropertiesKeySurround(Map<String, Object> propertiesToApply) {
      if (propertiesToApply.containsKey(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_KEY_SURROUND_PROPERTY)) {
         return String.valueOf(propertiesToApply.remove(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_KEY_SURROUND_PROPERTY));
      } else {
         return System.getProperty(getSystemPropertyPrefix() + ActiveMQDefaultConfiguration.BROKER_PROPERTIES_KEY_SURROUND_PROPERTY, getBrokerPropertiesKeySurround());
      }
   }

   private String getBrokerPropertiesRemoveValue(Map<String, Object> propertiesToApply) {
      if (propertiesToApply.containsKey(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_REMOVE_VALUE_PROPERTY)) {
         return String.valueOf(propertiesToApply.remove(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_REMOVE_VALUE_PROPERTY));
      } else {
         return System.getProperty(getSystemPropertyPrefix() + ActiveMQDefaultConfiguration.BROKER_PROPERTIES_REMOVE_VALUE_PROPERTY, getBrokerPropertiesRemoveValue());
      }
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
   public long getMinDiskFree() {
      return minDiskFree;
   }

   @Override
   public ConfigurationImpl setMinDiskFree(long minDiskFree) {
      this.minDiskFree = minDiskFree;
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
         this.globalMaxSize = ActiveMQDefaultConfiguration.getDefaultMaxGlobalSizeAsPercentOfJvmMaxMemory(getGlobalMaxSizePercentOfJvmMaxMemory());
         if (!Env.isTestEnv()) {
            ActiveMQServerLogger.LOGGER.usingDefaultPaging(globalMaxSize);
         }
      }
      return globalMaxSize;
   }

   @Override
   public int getGlobalMaxSizePercentOfJvmMaxMemory() {
      return globalMaxSizePercentOfJvmMaxMemory;
   }

   @Override
   public ConfigurationImpl setGlobalMaxSizePercentOfJvmMaxMemory(int percentOfJvmMaxMemory) {
      this.globalMaxSizePercentOfJvmMaxMemory = percentOfJvmMaxMemory;
      return this;
   }

   @Override
   public ConfigurationImpl setGlobalMaxMessages(long maxMessages) {
      this.globalMaxMessages = maxMessages;
      return this;
   }

   @Override
   public long getGlobalMaxMessages() {
      if (globalMaxMessages == null) {
         this.globalMaxMessages = ActiveMQDefaultConfiguration.getDefaultMaxGlobalMessages();
      }
      return globalMaxMessages;
   }

   @Override
   public ConfigurationImpl setPersistenceEnabled(final boolean enable) {
      persistenceEnabled = enable;
      return this;
   }

   @Override
   public Configuration setMaxRedeliveryRecords(int max) {
      maxRedeliveryRecords = max;
      return this;
   }

   @Override
   public int getMaxRedeliveryRecords() {
      return maxRedeliveryRecords;
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

   public ConfigurationImpl addConnectorConfiguration(final TransportConfiguration info) {
      connectorConfigs.put(info.getName(), info);
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

   public List<AMQPBrokerConnectConfiguration> getAMQPConnections() {
      return this.amqpBrokerConnectConfigurations;
   }

   @Override
   public Configuration setAMQPConnectionConfigurations(List<AMQPBrokerConnectConfiguration> amqpConnectionConfiugrations) {
      this.amqpBrokerConnectConfigurations.clear();
      this.amqpBrokerConnectConfigurations.addAll(amqpConnectionConfiugrations);
      return this;
   }

   @Override
   public Configuration clearAMQPConnectionConfigurations() {
      this.amqpBrokerConnectConfigurations.clear();
      return this;
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
   public List<ConnectionRouterConfiguration> getConnectionRouters() {
      return connectionRouters;
   }

   @Override
   public ConfigurationImpl setConnectionRouters(final List<ConnectionRouterConfiguration> configs) {
      connectionRouters = configs;
      return this;
   }

   @Override
   public ConfigurationImpl addConnectionRouter(final ConnectionRouterConfiguration config) {
      connectionRouters.add(config);
      return this;
   }

   @Deprecated
   @Override
   public List<CoreQueueConfiguration> getQueueConfigurations() {
      return coreQueueConfigurations;
   }

   @Override
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
   @Deprecated
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
   public String getNodeManagerLockDirectory() {
      return nodeManagerLockDirectory;
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
   public Map<String, AddressSettings> getAddressSettings() {
      return addressSettings;
   }

   @Override
   public ConfigurationImpl setAddressSettings(final Map<String, AddressSettings> addressesSettings) {
      this.addressSettings = addressesSettings;
      return this;
   }

   @Override
   public ConfigurationImpl addAddressSetting(String key, AddressSettings addressesSetting) {
      this.addressSettings.put(key, addressesSetting);
      return this;
   }

   @Override
   public ConfigurationImpl clearAddressSettings() {
      this.addressSettings.clear();
      return this;
   }

   @Override
   @Deprecated
   public Map<String, AddressSettings> getAddressesSettings() {
      return getAddressSettings();
   }

   @Override
   @Deprecated
   public ConfigurationImpl setAddressesSettings(final Map<String, AddressSettings> addressesSettings) {
      return setAddressSettings(addressesSettings);
   }

   @Override
   @Deprecated
   public ConfigurationImpl addAddressesSetting(String key, AddressSettings addressesSetting) {
      return addAddressSetting(key, addressesSetting);
   }

   @Override
   @Deprecated
   public ConfigurationImpl clearAddressesSettings() {
      return clearAddressSettings();
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

   public ConfigurationImpl addResourceLimitSetting(ResourceLimitSettings resourceLimitSettings) {
      return this.addResourceLimitSettings(resourceLimitSettings);
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
      securitySettings.put(match, new RoleSet(match, roles));
      return this;
   }

   // to provide type information to creation from properties
   public ConfigurationImpl addSecurityRole(String match, RoleSet roles) {
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
      // programmatic call can be a duplicate if used before server start
      if (!brokerPlugins.contains(plugin)) {
         brokerPlugins.add(plugin);
      }
      if (plugin instanceof ActiveMQServerConnectionPlugin connectionPlugin && !brokerConnectionPlugins.contains(plugin)) {
         brokerConnectionPlugins.add(connectionPlugin);
      }
      if (plugin instanceof ActiveMQServerSessionPlugin sessionPlugin && !brokerSessionPlugins.contains(plugin)) {
         brokerSessionPlugins.add(sessionPlugin);
      }
      if (plugin instanceof ActiveMQServerConsumerPlugin consumerPlugin && !brokerConsumerPlugins.contains(plugin)) {
         brokerConsumerPlugins.add(consumerPlugin);
      }
      if (plugin instanceof ActiveMQServerAddressPlugin addressPlugin && !brokerAddressPlugins.contains(plugin)) {
         brokerAddressPlugins.add(addressPlugin);
      }
      if (plugin instanceof ActiveMQServerQueuePlugin queuePlugin && !brokerQueuePlugins.contains(plugin)) {
         brokerQueuePlugins.add(queuePlugin);
      }
      if (plugin instanceof ActiveMQServerBindingPlugin bindingPlugin && !brokerBindingPlugins.contains(plugin)) {
         brokerBindingPlugins.add(bindingPlugin);
      }
      if (plugin instanceof ActiveMQServerMessagePlugin messagePlugin && !brokerMessagePlugins.contains(plugin)) {
         brokerMessagePlugins.add(messagePlugin);
      }
      if (plugin instanceof ActiveMQServerBridgePlugin bridgePlugin && !brokerBridgePlugins.contains(plugin)) {
         brokerBridgePlugins.add(bridgePlugin);
      }
      if (plugin instanceof ActiveMQServerCriticalPlugin criticalPlugin && !brokerCriticalPlugins.contains(plugin)) {
         brokerCriticalPlugins.add(criticalPlugin);
      }
      if (plugin instanceof ActiveMQServerFederationPlugin federationPlugin && !brokerFederationPlugins.contains(plugin)) {
         brokerFederationPlugins.add(federationPlugin);
      }
      if (plugin instanceof AMQPFederationBrokerPlugin brokerPlugin && !brokerAMQPFederationPlugins.contains(plugin)) {
         brokerAMQPFederationPlugins.add(brokerPlugin);
      }
      if (plugin instanceof ActiveMQServerResourcePlugin resourcePlugin && !brokerResourcePlugins.contains(plugin)) {
         brokerResourcePlugins.add(resourcePlugin);
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
      if (plugin instanceof AMQPFederationBrokerPlugin) {
         brokerAMQPFederationPlugins.remove(plugin);
      }
      if (plugin instanceof ActiveMQServerResourcePlugin) {
         brokerResourcePlugins.remove(plugin);
      }
   }

   @Override
   public List<ActiveMQServerBasePlugin> getBrokerPlugins() {
      return brokerPlugins;
   }

   // for properties type inference
   public void addBrokerPlugin(ActiveMQServerBasePlugin type) {
      registerBrokerPlugin(type);
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
   public List<AMQPFederationBrokerPlugin> getBrokerAMQPFederationPlugins() {
      return brokerAMQPFederationPlugins;
   }

   @Override
   public List<FederationConfiguration> getFederationConfigurations() {
      return federationConfigurations;
   }

   public void addFederationConfiguration(FederationConfiguration federationConfiguration) {
      federationConfigurations.add(federationConfiguration);
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

   public boolean isCheckForPrimaryServer() {
      if (haPolicyConfiguration instanceof ReplicaPolicyConfiguration) {
         return ((ReplicatedPolicyConfiguration) haPolicyConfiguration).isCheckForActiveServer();
      } else {
         return false;
      }
   }

   public ConfigurationImpl setCheckForPrimaryServer(boolean checkForPrimaryServer) {
      if (haPolicyConfiguration instanceof ReplicaPolicyConfiguration) {
         ((ReplicatedPolicyConfiguration) haPolicyConfiguration).setCheckForActiveServer(checkForPrimaryServer);
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
            ActiveMQServerLogger.LOGGER.connectionConfigurationIsNull(Objects.requireNonNullElse(connectorName, "null"));
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
      return Objects.hash(acceptorConfigs, addressSettings, asyncConnectionExecutionEnabled, bindingsDirectory,
                          bridgeConfigurations, broadcastGroupConfigurations, clusterConfigurations, clusterPassword,
                          clusterUser, connectionTTLOverride, connectorConfigs, connectorServiceConfigurations,
                          createBindingsDir, createJournalDir, discoveryGroupConfigurations, divertConfigurations,
                          failoverOnServerShutdown, fileDeploymentScanPeriod, groupingHandlerConfiguration, idCacheSize,
                          incomingInterceptorClassNames, jmxDomain, jmxManagementEnabled, journalBufferSize_AIO,
                          journalBufferSize_NIO, journalBufferTimeout_AIO, journalBufferTimeout_NIO,
                          journalCompactMinFiles, journalCompactPercentage, journalDirectory, journalFileSize,
                          journalMaxIO_AIO, journalMaxIO_NIO, journalMinFiles, journalSyncNonTransactional,
                          journalSyncTransactional, journalType, largeMessagesDirectory, logJournalWriteRate,
                          managementAddress, managementNotificationAddress, maskPassword, maxConcurrentPageIO,
                          memoryMeasureInterval, memoryWarningThreshold, messageCounterEnabled,
                          messageCounterMaxDayHistory, messageCounterSamplePeriod, messageExpiryScanPeriod,
                          messageExpiryThreadPriority, name, outgoingInterceptorClassNames, pagingDirectory,
                          persistDeliveryCountBeforeDelivery, persistIDCache, persistenceEnabled,
                          scheduledThreadPoolMaxSize, securityEnabled, populateValidatedUser,
                          securityInvalidationInterval, securitySettings, serverDumpInterval, threadPoolMaxSize,
                          transactionTimeout, transactionTimeoutScanPeriod, wildcardConfiguration, resolveProtocols,
                          journalLockAcquisitionTimeout, connectionTtlCheckInterval);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof ConfigurationImpl other)) {
         return false;
      }

      return Objects.equals(acceptorConfigs, other.acceptorConfigs) &&
             Objects.equals(addressSettings, other.addressSettings) &&
             asyncConnectionExecutionEnabled == other.asyncConnectionExecutionEnabled &&
             Objects.equals(bindingsDirectory, other.bindingsDirectory) &&
             Objects.equals(bridgeConfigurations, other.bridgeConfigurations) &&
             Objects.equals(broadcastGroupConfigurations, other.broadcastGroupConfigurations) &&
             Objects.equals(clusterConfigurations, other.clusterConfigurations) &&
             Objects.equals(clusterPassword, other.clusterPassword) &&
             Objects.equals(clusterUser, other.clusterUser) &&
             connectionTTLOverride == other.connectionTTLOverride &&
             Objects.equals(connectorConfigs, other.connectorConfigs) &&
             Objects.equals(connectorServiceConfigurations, other.connectorServiceConfigurations) &&
             createBindingsDir == other.createBindingsDir &&
             createJournalDir == other.createJournalDir &&
             Objects.equals(discoveryGroupConfigurations, other.discoveryGroupConfigurations) &&
             Objects.equals(divertConfigurations, other.divertConfigurations) &&
             failoverOnServerShutdown == other.failoverOnServerShutdown &&
             fileDeploymentScanPeriod == other.fileDeploymentScanPeriod &&
             Objects.equals(groupingHandlerConfiguration, other.groupingHandlerConfiguration) &&
             idCacheSize == other.idCacheSize &&
             Objects.equals(incomingInterceptorClassNames, other.incomingInterceptorClassNames) &&
             Objects.equals(jmxDomain, other.jmxDomain) &&
             jmxManagementEnabled == other.jmxManagementEnabled &&
             journalBufferSize_AIO == other.journalBufferSize_AIO &&
             journalBufferSize_NIO == other.journalBufferSize_NIO &&
             journalBufferTimeout_AIO == other.journalBufferTimeout_AIO &&
             journalBufferTimeout_NIO == other.journalBufferTimeout_NIO &&
             journalCompactMinFiles == other.journalCompactMinFiles &&
             journalCompactPercentage == other.journalCompactPercentage &&
             Objects.equals(journalDirectory, other.journalDirectory) &&
             journalFileSize == other.journalFileSize &&
             journalMaxIO_AIO == other.journalMaxIO_AIO &&
             journalMaxIO_NIO == other.journalMaxIO_NIO &&
             journalMinFiles == other.journalMinFiles &&
             journalSyncNonTransactional == other.journalSyncNonTransactional &&
             journalSyncTransactional == other.journalSyncTransactional &&
             journalType == other.journalType &&
             Objects.equals(largeMessagesDirectory, other.largeMessagesDirectory) &&
             logJournalWriteRate == other.logJournalWriteRate &&
             Objects.equals(managementAddress, other.managementAddress) &&
             Objects.equals(managementNotificationAddress, other.managementNotificationAddress) &&
             Objects.equals(maskPassword, other.maskPassword) &&
             maxConcurrentPageIO == other.maxConcurrentPageIO &&
             memoryMeasureInterval == other.memoryMeasureInterval &&
             memoryWarningThreshold == other.memoryWarningThreshold &&
             messageCounterEnabled == other.messageCounterEnabled &&
             messageCounterMaxDayHistory == other.messageCounterMaxDayHistory &&
             messageCounterSamplePeriod == other.messageCounterSamplePeriod &&
             messageExpiryScanPeriod == other.messageExpiryScanPeriod &&
             messageExpiryThreadPriority == other.messageExpiryThreadPriority &&
             Objects.equals(name, other.name) &&
             Objects.equals(outgoingInterceptorClassNames, other.outgoingInterceptorClassNames) &&
             Objects.equals(pagingDirectory, other.pagingDirectory) &&
             persistDeliveryCountBeforeDelivery ==
             other.persistDeliveryCountBeforeDelivery &&
             persistIDCache == other.persistIDCache &&
             persistenceEnabled == other.persistenceEnabled &&
             scheduledThreadPoolMaxSize == other.scheduledThreadPoolMaxSize &&
             securityEnabled == other.securityEnabled &&
             populateValidatedUser == other.populateValidatedUser &&
             securityInvalidationInterval == other.securityInvalidationInterval &&
             Objects.equals(securitySettings, other.securitySettings) &&
             serverDumpInterval == other.serverDumpInterval &&
             threadPoolMaxSize == other.threadPoolMaxSize &&
             transactionTimeout == other.transactionTimeout &&
             transactionTimeoutScanPeriod == other.transactionTimeoutScanPeriod &&
             Objects.equals(wildcardConfiguration, other.wildcardConfiguration) &&
             resolveProtocols == other.resolveProtocols &&
             journalLockAcquisitionTimeout == other.journalLockAcquisitionTimeout &&
             connectionTtlCheckInterval == other.connectionTtlCheckInterval &&
             journalDatasync == other.journalDatasync &&
             Objects.equals(globalMaxSize, other.globalMaxSize) &&
             maxDiskUsage == other.maxDiskUsage &&
             minDiskFree == other.minDiskFree &&
             diskScanPeriod == other.diskScanPeriod;
   }

   @Override
   public Configuration copy() throws Exception {
      return SecurityManagerShim.doPrivileged((PrivilegedExceptionAction<Configuration>) () -> {
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
   public Configuration setNetworkCheckNIC(String nic) {
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
      return getUuidNamespace();
   }

   @Override
   public ConfigurationImpl setTemporaryQueueNamespace(final String temporaryQueueNamespace) {
      return setUuidNamespace(temporaryQueueNamespace);
   }

   @Override
   public String getUuidNamespace() {
      return uuidNamespace;
   }

   @Override
   public ConfigurationImpl setUuidNamespace(final String uuidNamespace) {
      this.uuidNamespace = uuidNamespace;
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

   @Override
   public long getMqttSessionScanInterval() {
      return mqttSessionScanInterval;
   }

   @Override
   public Configuration setMqttSessionScanInterval(long mqttSessionScanInterval) {
      this.mqttSessionScanInterval = mqttSessionScanInterval;
      return this;
   }

   @Override
   public long getMqttSessionStatePersistenceTimeout() {
      return mqttSessionStatePersistenceTimeout;
   }

   @Override
   public Configuration setMqttSessionStatePersistenceTimeout(long mqttSessionStatePersistenceTimeout) {
      this.mqttSessionStatePersistenceTimeout = mqttSessionStatePersistenceTimeout;
      return this;
   }

   @Override
   public boolean isMqttSubscriptionPersistenceEnabled() {
      return mqttSessionStatePersistenceEnabled;
   }

   @Override
   public Configuration setMqttSubscriptionPersistenceEnabled(boolean mqttSubscriptionPersistenceEnabled) {
      this.mqttSessionStatePersistenceEnabled = mqttSubscriptionPersistenceEnabled;
      return this;
   }

   @Override
   public boolean isSuppressSessionNotifications() {
      return suppressSessionNotifications;
   }

   @Override
   public Configuration setSuppressSessionNotifications(boolean suppressSessionNotifications) {
      this.suppressSessionNotifications = suppressSessionNotifications;
      return this;
   }

   @Override
   public synchronized String getStatus() {
      return getJsonStatus().toString();
   }

   @Override
   public synchronized void setStatus(String status) {
      JsonObject update = JsonUtil.readJsonObject(status);
      this.jsonStatus = JsonUtil.mergeAndUpdate(getJsonStatus(), update);
   }

   @Override
   public String getLiteralMatchMarkers() {
      return literalMatchMarkers;
   }

   @Override
   public Configuration setLiteralMatchMarkers(String literalMatchMarkers) {
      this.literalMatchMarkers = literalMatchMarkers;
      return this;
   }


   @Override
   public Configuration setLargeMessageSync(boolean largeMessageSync) {
      this.largeMessageSync = largeMessageSync;
      return this;
   }

   @Override
   public boolean isLargeMessageSync() {
      return largeMessageSync;
   }

   @Override
   public String getViewPermissionMethodMatchPattern() {
      return viewPermissionMethodMatchPattern;
   }

   @Override
   public void setViewPermissionMethodMatchPattern(String permissionMatchPattern) {
      viewPermissionMethodMatchPattern = permissionMatchPattern;
   }

   @Override
   public boolean isManagementMessageRbac() {
      return managementMessagesRbac;
   }

   @Override
   public void setManagementMessageRbac(boolean val) {
      this.managementMessagesRbac = val;
   }

   @Override
   public String getManagementRbacPrefix() {
      return managementRbacPrefix;
   }

   @Override
   public void setManagementRbacPrefix(String prefix) {
      this.managementRbacPrefix = prefix;
   }


   @Override
   public int getMirrorAckManagerQueueAttempts() {
      return mirrorAckManagerQueueAttempts;
   }

   @Override
   public boolean isMirrorAckManagerWarnUnacked() {
      return mirrorAckManagerWarnUnacked;
   }

   @Override
   public ConfigurationImpl setMirrorAckManagerWarnUnacked(boolean warnUnacked) {
      this.mirrorAckManagerWarnUnacked = warnUnacked;
      return this;
   }

   @Override
   public ConfigurationImpl setMirrorAckManagerQueueAttempts(int minQueueAttempts) {
      logger.debug("Setting mirrorAckManagerMinQueueAttempts = {}", minQueueAttempts);
      this.mirrorAckManagerQueueAttempts = minQueueAttempts;
      return this;
   }

   @Override
   public ConfigurationImpl setPurgePageFolders(boolean purgePageFolders) {
      this.purgePageFolders = purgePageFolders;
      return this;
   }

   @Override
   public boolean isPurgePageFolders() {
      return purgePageFolders;
   }

   @Override
   public int getMirrorAckManagerPageAttempts() {
      return this.mirrorAckManagerPageAttempts;
   }

   @Override
   public ConfigurationImpl setMirrorAckManagerPageAttempts(int maxPageAttempts) {
      logger.debug("Setting mirrorAckManagerMaxPageAttempts = {}", maxPageAttempts);
      this.mirrorAckManagerPageAttempts = maxPageAttempts;
      return this;
   }

   @Override
   public int getMirrorAckManagerRetryDelay() {
      return mirrorAckManagerRetryDelay;
   }

   @Override
   public ConfigurationImpl setMirrorAckManagerRetryDelay(int delay) {
      logger.debug("Setting mirrorAckManagerRetryDelay = {}", delay);
      this.mirrorAckManagerRetryDelay = delay;
      return this;
   }

   @Override
   public boolean isMirrorPageTransaction() {
      return mirrorPageTransaction;
   }

   @Override
   public Configuration setMirrorPageTransaction(boolean ignorePageTransactions) {
      logger.debug("Setting mirrorIgnorePageTransactions={}", ignorePageTransactions);
      this.mirrorPageTransaction = ignorePageTransactions;
      return this;
   }

   // extend property utils with ability to auto-fill and locate from collections
   // collection entries are identified by the name() property
   private static class CollectionAutoFillPropertiesUtil extends PropertyUtilsBean {

      private static final Object[] EMPTY_OBJECT_ARRAY = new Object[]{};
      final Stack<Pair<String, Object>> collections = new Stack<>();
      final String removeValue;
      private BeanUtilsBean beanUtilsBean;

      CollectionAutoFillPropertiesUtil(String brokerPropertiesRemoveValue) {
         this.removeValue = brokerPropertiesRemoveValue;
      }

      @Override
      public void setProperty(final Object bean, final String name, final Object value) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
         // any set will invalidate our collections stack
         if (!collections.isEmpty()) {
            Pair<String, Object> collectionInfo = collections.pop();
         }

         if (bean instanceof Map) {
            String property = getResolver().getProperty(name);
            this.setPropertyOfMapBean((Map)bean, property, value);
         } else {
            super.setProperty(bean, name, value);
         }
      }

      // need to track collections such that we can locate or create entries on demand
      @Override
      public Object getProperty(final Object bean,
                                final String name) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

         if (!collections.isEmpty()) {
            final String key = getResolver().getProperty(name);
            Pair<String, Object> collectionInfo = collections.pop();
            if (bean instanceof Map map) {
               if (!map.containsKey(key)) {
                  map.put(key, newNamedInstanceForCollection(collectionInfo.getA(), collectionInfo.getB(), key));
               }
               Object value = map.get(key);
               return trackCollectionOrMap(null, value, value);
            } else { // collection
               Object value = findByNameProperty(key, (Collection)bean);
               if (value == null) {
                  // create it
                  value = newNamedInstanceForCollection(collectionInfo.getA(), collectionInfo.getB(), key);
                  ((Collection) bean).add(value);
               }
               return trackCollectionOrMap(null, value, value);
            }
         }

         Object resolved = null;

         try {
            resolved = getNestedProperty(bean, name);
         } catch (final NoSuchMethodException e) {
            // to avoid it being swallowed by caller wrap
            throw new InvocationTargetException(e, "Cannot access property with key: " + name);
         }

         return trackCollectionOrMap(name, resolved, bean);
      }

      private Object trackCollectionOrMap(String name, Object resolved, Object bean) {
         if (resolved instanceof Collection || resolved instanceof Map) {
            collections.push(new Pair<String, Object>(name, bean));
         }
         return resolved;
      }

      private Object findByNameProperty(String key, Collection collection) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
         // locate on name property, may be a SimpleString
         if (isClassProperty(key)) {
            Object propertyClassName = extractPropertyClassName(key);
            for (Object candidate : collection) {
               if (candidate.getClass().getName().equals(propertyClassName)) {
                  return candidate;
               }
            }
         } else {
            for (Object candidate : collection) {
               Object candidateName = getProperty(candidate, "name");
               if (candidateName != null && key.equals(candidateName.toString())) {
                  return candidate;
               }
            }
         }
         return null;
      }

      private Object removeByNameProperty(String key, Collection collection) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
         // locate on name property, may be a SimpleString
         for (Object candidate : collection) {
            Object candidateName = getProperty(candidate, "name");
            if (candidateName != null && key.equals(candidateName.toString())) {
               collection.remove(candidate);
               break;
            }
         }
         return null;
      }

      // allow finding beans in collections via name() such that a mapped key (key)
      // can be used to access and *not* auto create entries
      @Override
      public Object getMappedProperty(final Object bean,
                                      final String name, final String key)
         throws IllegalAccessException, InvocationTargetException,
         NoSuchMethodException {

         if (bean == null) {
            throw new IllegalArgumentException("No bean specified");
         }
         if (name == null) {
            throw new IllegalArgumentException("No name specified for bean class '" +
                                                  bean.getClass() + "'");
         }
         if (key == null) {
            throw new IllegalArgumentException("No key specified for property '" +
                                                  name + "' on bean class " + bean.getClass() + "'");
         }

         Object result = null;

         final PropertyDescriptor descriptor = getPropertyDescriptor(bean, name);
         if (descriptor == null) {
            throw new NoSuchMethodException("Unknown property '" +
                                               name + "'+ on bean class '" + bean.getClass() + "'");
         }

         if (descriptor instanceof MappedPropertyDescriptor mappedPropertyDescriptor) {
            // Call the keyed getter method if there is one
            Method readMethod = mappedPropertyDescriptor.
               getMappedReadMethod();
            readMethod = MethodUtils.getAccessibleMethod(bean.getClass(), readMethod);
            if (readMethod != null) {
               final Object[] keyArray = new Object[1];
               keyArray[0] = key;
               result = readMethod.invoke(bean, keyArray);
            } else {
               throw new NoSuchMethodException("Property '" + name +
                                                  "' has no mapped getter method on bean class '" +
                                                  bean.getClass() + "'");
            }
         } else {
            final Method readMethod = MethodUtils.getAccessibleMethod(bean.getClass(), descriptor.getReadMethod());
            if (readMethod != null) {
               final Object invokeResult = readMethod.invoke(bean, EMPTY_OBJECT_ARRAY);
               if (invokeResult instanceof Map<?, ?> map) {
                  result = map.get(key);
               } else if (invokeResult instanceof Collection collection) {
                  result = findByNameProperty(key, collection);
               }
            } else {
               throw new NoSuchMethodException("Property '" + name +
                                                  "' has no mapped getter method on bean class '" +
                                                  bean.getClass() + "'");
            }
         }
         return result;
      }

      private Object newNamedInstanceForCollection(String collectionPropertyName, Object hostingBean, String name) {
         // find the add X and init an instance of the type with name=name

         StringBuilder addPropertyNameBuilder = new StringBuilder("add");
         // expect an add... without the plural for named access methods that add a single instance.
         if (collectionPropertyName != null && !collectionPropertyName.isEmpty()) {
            addPropertyNameBuilder.append(Character.toUpperCase(collectionPropertyName.charAt(0)));
            if (collectionPropertyName.endsWith("ies")) {
               // Plural form would convert to a singular ending in 'y' e.g. policies becomes policy
               // or strategies becomes strategy etc.
               addPropertyNameBuilder.append(collectionPropertyName, 1, collectionPropertyName.length() - 3);
               addPropertyNameBuilder.append('y');
            } else if (collectionPropertyName.endsWith("Map")) {
               addPropertyNameBuilder.append(collectionPropertyName, 1, collectionPropertyName.length() - 3);
            } else {
               addPropertyNameBuilder.append(collectionPropertyName, 1, collectionPropertyName.length() - 1);
            }
         }

         Object instance = null;
         try {
            // we don't know the type, infer from add method add(X x) or add(String key, X x)
            final String addPropertyName = addPropertyNameBuilder.toString();
            final Method[] methods = hostingBean.getClass().getMethods();
            final Method candidate = Arrays.stream(methods).filter(method -> method.getName().equals(addPropertyName) && ((method.getParameterCount() == 1) || (method.getParameterCount() == 2
               // has a String key
               && String.class.equals(method.getParameterTypes()[0])
               // but not initialised from a String form (eg: uri)
               && !String.class.equals(method.getParameterTypes()[1])))).sorted((method1, method2) -> {
                  int result = method2.getParameterCount() - method1.getParameterCount();
                  if (result == 0) {
                     // choose non deprecated
                     if (method1.getDeclaredAnnotation(Deprecated.class) == null) {
                        result = 1;
                     } else {
                        result = -1;
                     }
                  }
                  return result;
               }).findFirst().orElse(null);

            if (candidate == null) {
               throw new IllegalArgumentException("failed to locate add method for collection property " + addPropertyName);
            }
            Class type = candidate.getParameterTypes()[candidate.getParameterCount() - 1];

            if (isClassProperty(name)) {
               final String clazzName = extractPropertyClassName(name);
               instance = ClassloadingUtil.getInstanceWithTypeCheck(clazzName, type, this.getClass().getClassLoader());
            } else {
               instance = type.getDeclaredConstructor().newInstance();
            }
            // initialise with name
            try {
               beanUtilsBean.setProperty(instance, "name", name);
            } catch (Throwable ignored) {
               // for maps a name attribute is not mandatory
            }

            return instance;

         } catch (Exception e) {
            if (logger.isDebugEnabled()) {
               logger.debug("Failed to add entry for {} to collection: {}", name, hostingBean, e);
            }
            throw new IllegalArgumentException("failed to add entry for collection key " + name + ", cause " + e.getMessage(), e);
         }
      }

      public void setBeanUtilsBean(BeanUtilsBean beanUtilsBean) {
         // we want type conversion
         this.beanUtilsBean = beanUtilsBean;
      }

      public boolean isRemoveValue(Object value) {
         return removeValue != null && removeValue.equals(value);
      }
   }

   private static class SurroundResolver extends DefaultResolver {
      final String surroundString;

      SurroundResolver(String surroundString) {
         this.surroundString = surroundString;
      }

      @Override
      public String next(String expression) {
         String result = super.next(expression);
         if (result != null) {
            if (result.startsWith(surroundString)) {
               // we need to recompute to properly terminate this SURROUND
               result = expression.substring(expression.indexOf(surroundString));
               return result.substring(0, result.indexOf(surroundString, surroundString.length()) + surroundString.length());
            }
         }
         return result;
      }

      @Override
      public String getProperty(final String expression) {
         if (expression.startsWith(surroundString) && expression.endsWith(surroundString)) {
            return expression.substring(surroundString.length(), expression.length() - surroundString.length());
         }
         return super.getProperty(expression);
      }
   }

   public static class InsertionOrderedProperties extends Properties {

      final LinkedHashMap<Object, Object> orderedMap = new LinkedHashMap<>();
      long fileChecksum = 0;

      @Override
      public Object put(Object key, Object value) {
         return orderedMap.put(key.toString(), value.toString());
      }

      @Override
      public Set<Map.Entry<Object, Object>> entrySet() {
         return orderedMap.entrySet();
      }

      @Override
      public void clear() {
         orderedMap.clear();
      }

      public synchronized boolean loadJson(ConfigurationImpl configuration, InputStream inputStream) throws IOException {
         try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return loadJson(configuration, reader);
         }
      }

      public synchronized boolean loadJson(ConfigurationImpl configuration, Reader reader) throws IOException {
         JsonObject jsonObject = JsonLoader.readObject(reader);
         final String surroundString = determineSurroundString(configuration, jsonObject);
         loadJsonObject(surroundString, "", jsonObject);

         return true;
      }

      private void loadJsonObject(String keySurroundString, String parentKey, JsonObject jsonObject) {
         jsonObject.entrySet().stream().forEach(jsonEntry -> {
            JsonValue jsonValue = jsonEntry.getValue();
            JsonValue.ValueType jsonValueType = jsonValue.getValueType();
            String jsonKey = jsonEntry.getKey();
            jsonKey = autoSurroundIfNecessary(jsonKey, keySurroundString);
            String propertyKey = parentKey + jsonKey;
            switch (jsonValueType) {
               case OBJECT:
                  loadJsonObject(keySurroundString, propertyKey + ".", jsonValue.asJsonObject());
                  break;
               case STRING:
                  put(propertyKey, ((JsonString)jsonValue).getString());
                  break;
               case NUMBER:
               case TRUE:
               case FALSE:
                  put(propertyKey, jsonValue.toString());
                  break;
               default:
                  throw new IllegalStateException("JSON value type not supported: " + jsonValueType);
            }
         });
      }

      private String autoSurroundIfNecessary(String jsonKey, String keySurroundString) {
         String result = jsonKey;
         if (keyNeedsAutoSurround(jsonKey, keySurroundString)) {
            result = keySurroundString + jsonKey + keySurroundString;
         }
         return result;
      }

      private boolean keyNeedsAutoSurround(String jsonKey, String keySurroundString) {
         return jsonKey.contains(".") && !jsonKey.startsWith("key.") && !(jsonKey.startsWith(keySurroundString) && jsonKey.endsWith(keySurroundString));
      }

      private String determineSurroundString(ConfigurationImpl configuration, JsonObject jsonObject) {
         String surroundString = jsonObject.getString(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_KEY_SURROUND_PROPERTY, null);
         if (surroundString == null) {
            surroundString = jsonObject.getString("brokerPropertiesKeySurround", configuration.getBrokerPropertiesKeySurround());
         }
         return surroundString;
      }

      public void setFileChecksum(long value) {
         fileChecksum = value;
      }

      public long getFileChecksum() {
         return fileChecksum;
      }
   }
}
