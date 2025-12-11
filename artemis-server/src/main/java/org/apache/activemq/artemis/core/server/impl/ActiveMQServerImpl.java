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
package org.apache.activemq.artemis.core.server.impl;

import javax.management.MBeanServer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQDeleteAddressException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationBrokerPlugin;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.LegacyJMSConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryDatabase;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.AddressBindingInfo;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.AbstractPersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.persistence.impl.PageCountPending;
import org.apache.activemq.artemis.core.persistence.impl.journal.JDBCJournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.security.impl.SecurityStoreImpl;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActivationFailureListener;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.MemoryManager;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.PostQueueCreationCallback;
import org.apache.activemq.artemis.core.server.PostQueueDeletionCallback;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.core.server.ServiceRegistry;
import org.apache.activemq.artemis.core.server.cluster.BackupManager;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.federation.FederationManager;
import org.apache.activemq.artemis.core.server.files.FileMoveManager;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.server.group.impl.LocalGroupingHandler;
import org.apache.activemq.artemis.core.server.group.impl.RemoteGroupingHandler;
import org.apache.activemq.artemis.core.server.impl.jdbc.JdbcNodeManager;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.impl.ManagementServiceImpl;
import org.apache.activemq.artemis.core.server.metrics.BrokerMetricNames;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.server.mirror.MirrorRegistry;
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
import org.apache.activemq.artemis.core.server.reload.ReloadCallback;
import org.apache.activemq.artemis.core.server.reload.ReloadManager;
import org.apache.activemq.artemis.core.server.reload.ReloadManagerImpl;
import org.apache.activemq.artemis.core.server.replay.ReplayManager;
import org.apache.activemq.artemis.core.server.routing.ConnectionRouterManager;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.impl.ResourceManagerImpl;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoader;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ActiveMQThreadPoolExecutor;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.OpenWireUUIDUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.SecurityFormatter;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.TimeUtils;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.critical.CriticalAction;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerImpl;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.utils.critical.CriticalComponent;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.activemq.artemis.utils.uri.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.activemq.artemis.utils.collections.IterableStream.iterableOf;

/**
 * The ActiveMQ Artemis server implementation
 */
public class ActiveMQServerImpl implements ActiveMQServer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String INTERNAL_NAMING_PREFIX = "$.artemis.internal";

   /**
    * JMS Topics (which are outside of the scope of the core API) will require a dumb subscription with a dummy-filter
    * at this current version as a way to keep its existence valid and TCK tests. That subscription needs an invalid
    * filter, however paging needs to ignore any subscription with this filter. For that reason, this filter needs to be
    * rejected on paging or any other component on the system, and just be ignored for any purpose It's declared here as
    * this filter is considered a global ignore
    *
    * @deprecated Replaced by {@link org.apache.activemq.artemis.core.filter.Filter#GENERIC_IGNORED_FILTER}
    */
   @Deprecated
   public static final String GENERIC_IGNORED_FILTER = Filter.GENERIC_IGNORED_FILTER;

   private static final long THREAD_POOL_KEEP_ALIVE_SECONDS = 60L;

   private HAPolicy haPolicy;

   private MirrorRegistry mirrorRegistry = new MirrorRegistry();

   // This will be useful on tests or embedded
   private boolean rebuildCounters = true;

   private volatile SERVER_STATE state = SERVER_STATE.STOPPED;

   private final Version version;

   private ActiveMQSecurityManager securityManager;

   private final Configuration configuration;

   private final AtomicBoolean configurationReloadDeployed;

   private MBeanServer mbeanServer;

   private volatile SecurityStore securityStore;

   private final HierarchicalRepository<AddressSettings> addressSettingsRepository;

   private volatile QueueFactory queueFactory;

   private volatile PagingManager pagingManager;

   private volatile PostOffice postOffice;

   private volatile ExecutorService threadPool;

   protected volatile ScheduledExecutorService scheduledPool;

   protected volatile ExecutorFactory executorFactory;

   private volatile ExecutorService ioExecutorPool;

   private ReplayManager replayManager;

   /**
    * Certain management operations shouldn't use more than one thread. this semaphore is used to guarantee a single
    * thread used.
    */
   private final ReentrantLock managementLock = new ReentrantLock();

   /**
    * This is a thread pool for io tasks only. We can't use the same global executor to avoid starvations.
    */
   protected volatile ExecutorFactory ioExecutorFactory;

   /**
    * This is a thread pool for page only tasks only. This is because we have to limit parallel reads on paging.
    */
   protected volatile ExecutorFactory pageExecutorFactory;

   protected volatile ExecutorService pageExecutorPool;

   private final NetworkHealthCheck networkHealthCheck = new NetworkHealthCheck(ActiveMQDefaultConfiguration.getDefaultNetworkCheckNic(), ActiveMQDefaultConfiguration.getDefaultNetworkCheckPeriod(), ActiveMQDefaultConfiguration.getDefaultNetworkCheckTimeout());

   private final HierarchicalRepository<Set<Role>> securityRepository;

   private volatile ResourceManager resourceManager;

   private volatile MetricsManager metricsManager;

   private volatile ActiveMQServerControlImpl messagingServerControl;

   private volatile ClusterManager clusterManager;

   private volatile BackupManager backupManager;

   private volatile StorageManager storageManager;

   private volatile RemotingService remotingService;

   private volatile ConnectionRouterManager connectionRouterManager;

   private final List<ProtocolManagerFactory> protocolManagerFactories = new ArrayList<>();

   private final List<ActiveMQComponent> protocolServices = new ArrayList<>();

   private volatile ManagementService managementService;

   private volatile MirrorController mirrorControllerService;

   private volatile ConnectorsService connectorsService;

   private MemoryManager memoryManager;

   private ReloadManager reloadManager;

   private FileStoreMonitor fileStoreMonitor;

   private final ConcurrentMap<String, ServerSession> sessions = new ConcurrentHashMap<>();

   private AtomicLong totalSessionCount = new AtomicLong(0);

   private final Semaphore activationLock = new Semaphore(1);
   /**
    * This class here has the same principle of CountDownLatch but you can reuse the counters.
    * It's based on the same super classes of {@code CountDownLatch}
    */
   private final ReusableLatch activationLatch = new ReusableLatch(0);

   private final Map<String, BrokerConnection> brokerConnectionMap = new ConcurrentHashMap<>();

   private final Set<ActivateCallback> activateCallbacks = new ConcurrentHashSet<>();

   private final Set<ActivationFailureListener> activationFailureListeners = new ConcurrentHashSet<>();

   private final Set<IOCriticalErrorListener> ioCriticalErrorListeners = new ConcurrentHashSet<>();

   private final Set<PostQueueCreationCallback> postQueueCreationCallbacks = new ConcurrentHashSet<>();

   private final Set<PostQueueDeletionCallback> postQueueDeletionCallbacks = new ConcurrentHashSet<>();

   private volatile GroupingHandler groupingHandler;

   private NodeManager nodeManager;

   // Used to identify the server on tests... useful on debugging testcases
   private String identity;

   private Thread activationThread;

   private Activation activation;

   private final Map<String, Object> activationParams = new HashMap<>();

   protected final IOCriticalErrorListener ioCriticalErrorListener = new DefaultCriticalErrorListener();

   private final ActiveMQServer parentServer;

   private CriticalAnalyzer analyzer;

   // This is a callback to be called right before an activation is created
   private Runnable afterActivationCreated;

   //todo think about moving this to the activation
   private final List<SimpleString> scaledDownNodeIDs = new ArrayList<>();

   private boolean threadPoolSupplied = false;

   private boolean scheduledPoolSupplied = false;

   private final ServiceRegistry serviceRegistry;

   private Date startDate;

   private final List<ActiveMQComponent> externalComponents = new ArrayList<>();

   private final ConcurrentMap<String, AtomicInteger> connectedClientIds = new ConcurrentHashMap();

   private volatile FederationManager federationManager;

   private String propertiesFileUrl;

   private List<Consumer<RecordInfo>> extraRecordsLoader;

   private final ActiveMQComponent networkCheckMonitor = new ActiveMQComponent() {
      @Override
      public void start() throws Exception {
         internalStart();
      }

      @Override
      public void stop() throws Exception {
         ActiveMQServerImpl.this.stop(false);
      }

      @Override
      public String toString() {
         return ActiveMQServerImpl.this.toString();
      }

      @Override
      public boolean isStarted() {
         return ActiveMQServerImpl.this.isStarted();
      }
   };

   private final ServerStatus serverStatus;

   public ActiveMQServerImpl() {
      this(null, null, null);
   }

   public ActiveMQServerImpl(final Configuration configuration) {
      this(configuration, null, null);
   }

   public ActiveMQServerImpl(final Configuration configuration, ActiveMQServer parentServer) {
      this(configuration, null, null, parentServer);
   }

   public ActiveMQServerImpl(final Configuration configuration, final MBeanServer mbeanServer) {
      this(configuration, mbeanServer, null);
   }

   public ActiveMQServerImpl(final Configuration configuration, final ActiveMQSecurityManager securityManager) {
      this(configuration, null, securityManager);
   }

   public ActiveMQServerImpl(Configuration configuration,
                             MBeanServer mbeanServer,
                             final ActiveMQSecurityManager securityManager) {
      this(configuration, mbeanServer, securityManager, null);
   }

   public ActiveMQServerImpl(Configuration configuration,
                             MBeanServer mbeanServer,
                             final ActiveMQSecurityManager securityManager,
                             final ActiveMQServer parentServer) {
      this(configuration, mbeanServer, securityManager, parentServer, null);
   }

   public ActiveMQServerImpl(Configuration configuration,
                             MBeanServer mbeanServer,
                             final ActiveMQSecurityManager securityManager,
                             final ActiveMQServer parentServer,
                             final ServiceRegistry serviceRegistry) {
      if (configuration == null) {
         configuration = new ConfigurationImpl();
      } else {
         ConfigurationUtils.validateConfiguration(configuration);
      }

      if (mbeanServer == null) {
         // Just use JVM mbean server
         mbeanServer = ManagementFactory.getPlatformMBeanServer();
      }

      // We need to hard code the version information into a source file

      version = VersionLoader.getVersion();

      this.configuration = configuration;

      this.configurationReloadDeployed = new AtomicBoolean(true);

      this.mbeanServer = mbeanServer;

      this.securityManager = securityManager;

      addressSettingsRepository = new HierarchicalObjectRepository<>(configuration.getWildcardConfiguration(), new AddressSettingsMatchModifier(), this.configuration.getLiteralMatchMarkers());

      addressSettingsRepository.setDefault(new AddressSettings());

      securityRepository = new HierarchicalObjectRepository<>(configuration.getWildcardConfiguration(), new SecuritySettingsMatchModifier());

      securityRepository.setDefault(new HashSet<>());

      this.parentServer = parentServer;

      this.serviceRegistry = Objects.requireNonNullElseGet(serviceRegistry, () -> new ServiceRegistryImpl());

      this.serverStatus = ServerStatus.starting(this);
   }

   public class AddressSettingsMatchModifier implements HierarchicalObjectRepository.MatchModifier {
      @Override
      public String modify(String input) {
         return modifyMatchForUUID(CompositeAddress.extractAddressName(input));
      }
   }

   public class SecuritySettingsMatchModifier implements HierarchicalObjectRepository.MatchModifier {
      @Override
      public String modify(String input) {
         return modifyMatchForUUID(input);
      }
   }

   private String modifyMatchForUUID(String result) {
      if (configuration.getUuidNamespace() != null && !configuration.getUuidNamespace().isEmpty() && (UUID.isUUID(result) || OpenWireUUIDUtil.isUUID(result))) {
         result = new StringBuilder().append(configuration.getUuidNamespace()).append(configuration.getWildcardConfiguration().getDelimiterString()).append(result).toString();
      }
      return result;
   }

   @Override
   public ReloadManager getReloadManager() {
      return reloadManager;
   }

   @Override
   public NetworkHealthCheck getNetworkHealthCheck() {
      return networkHealthCheck;
   }

   @Override
   public void setRebuildCounters(boolean rebuildCounters) {
      this.rebuildCounters = rebuildCounters;
   }

   @Override
   public boolean isRebuildCounters() {
      return this.rebuildCounters;
   }


   @Override
   public void replay(Date start, Date end, String address, String target, String filter) throws Exception {
      if (replayManager == null) {
         throw ActiveMQMessageBundle.BUNDLE.noRetention();
      }
      try (AutoCloseable lock = managementLock()) {
         replayManager.replay(start, end, address, target, filter);
      }
   }

   @Override
   public void registerRecordsLoader(Consumer<RecordInfo> recordsLoader) {
      if (extraRecordsLoader == null) {
         extraRecordsLoader = new ArrayList<>();
      }
      extraRecordsLoader.add(recordsLoader);
   }

   /**
    * A Callback for tests
    */
   public Runnable getAfterActivationCreated() {
      return afterActivationCreated;
   }

   /**
    * A Callback for tests
    */
   public ActiveMQServerImpl setAfterActivationCreated(Runnable afterActivationCreated) {
      this.afterActivationCreated = afterActivationCreated;
      return this;
   }

   private void configureJdbcNetworkTimeout() {
      if (configuration.isPersistenceEnabled()) {
         if (configuration.isUsingDatabasePersistence()) {
            configuration.setMaxDiskUsage(-1); // it does not make sense with JDBC
            DatabaseStorageConfiguration databaseStorageConfiguration = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
            databaseStorageConfiguration.setConnectionProviderNetworkTimeout(threadPool, databaseStorageConfiguration.getJdbcNetworkTimeout());
         }
      }
   }

   private void clearJdbcNetworkTimeout() {
      if (configuration.isPersistenceEnabled()) {
         if (configuration.isUsingDatabasePersistence()) {
            DatabaseStorageConfiguration databaseStorageConfiguration = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
            databaseStorageConfiguration.clearConnectionProviderNetworkTimeout();
         }
      }
   }

   // Can be overridden for tests
   protected NodeManager createNodeManager(final File directory, boolean replicatingBackup) {
      NodeManager manager;
      if (!configuration.isPersistenceEnabled()) {
         manager = new InVMNodeManager(replicatingBackup);
      } else if (configuration.isUsingDatabasePersistence()) {
         final HAPolicyConfiguration.TYPE haType = configuration.getHAPolicyConfiguration() == null ? null : configuration.getHAPolicyConfiguration().getType();
         if (haType == HAPolicyConfiguration.TYPE.SHARED_STORE_PRIMARY || haType == HAPolicyConfiguration.TYPE.SHARED_STORE_BACKUP) {
            if (replicatingBackup) {
               throw new IllegalArgumentException("replicatingBackup is not supported yet while using JDBC persistence");
            }
            final DatabaseStorageConfiguration dbConf = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
            manager = JdbcNodeManager.with(dbConf, scheduledPool, executorFactory);
         } else if (haType == null || haType == HAPolicyConfiguration.TYPE.PRIMARY_ONLY) {
            logger.debug("Detected no Shared Store HA options on JDBC store");
            //PRIMARY_ONLY should be the default HA option when HA isn't configured
            manager = new FileLockNodeManager(directory, replicatingBackup, configuration.getJournalLockAcquisitionTimeout(), scheduledPool);
         } else {
            throw new IllegalArgumentException("JDBC persistence allows only Shared Store HA options");
         }
      } else {
         manager = new FileLockNodeManager(directory, replicatingBackup, configuration.getJournalLockAcquisitionTimeout(), scheduledPool);
      }
      return manager;
   }

   @Override
   public OperationContext newOperationContext() {
      return getStorageManager().newContext(getExecutorFactory().getExecutor());
   }

   @Override
   public final synchronized void start() throws Exception {
      SERVER_STATE originalState = state;
      try {
         internalStart();
      } catch (Throwable t) {
         ActiveMQServerLogger.LOGGER.failedToStartServer(t);
         throw t;
      } finally {
         if (originalState == SERVER_STATE.STOPPED) {
            reloadNetworkHealthCheck();

         }
      }
   }

   public void reloadNetworkHealthCheck() {
      networkHealthCheck.setTimeUnit(TimeUnit.MILLISECONDS).setPeriod(configuration.getNetworkCheckPeriod()).
         setNetworkTimeout(configuration.getNetworkCheckTimeout()).
         setNICName(configuration.getNetworkCheckNIC()).
         setIpv4Command(configuration.getNetworkCheckPingCommand()).
         setIpv6Command(configuration.getNetworkCheckPing6Command()).
         parseAddressList(configuration.getNetworkCheckList()).
         parseURIList(configuration.getNetworkCheckURLList());

      networkHealthCheck.addComponent(networkCheckMonitor);
   }

   @Override
   public CriticalAnalyzer getCriticalAnalyzer() {
      return this.analyzer;
   }

   @Override
   public void setProperties(String fileUrltoBrokerProperties) {
      propertiesFileUrl = fileUrltoBrokerProperties;
   }

   @Override
   public String getStatus() {
      return serverStatus.asJson();
   }

   @Override
   public void updateStatus(String component, String statusJson) {
      serverStatus.update(component, statusJson);
   }

   private void internalStart() throws Exception {
      if (state != SERVER_STATE.STOPPED) {
         logger.debug("Server already started!");
         return;
      }

      configuration.parseProperties(propertiesFileUrl);
      updateStatus(ServerStatus.CONFIGURATION_COMPONENT, configuration.getStatus());

      initializeExecutorServices();

      initializeCriticalAnalyzer();

      if (configuration.getJournalRetentionLocation() != null) {
         this.replayManager = new ReplayManager(this);
      } else {
         this.replayManager = null;
      }

      startDate = new Date();

      state = SERVER_STATE.STARTING;

      if (haPolicy == null) {
         haPolicy = ConfigurationUtils.getHAPolicy(configuration.getHAPolicyConfiguration(), this);
      }

      activationLatch.setCount(1);

      logger.debug("Starting server {}", this);

      OperationContextImpl.clearContext();

      try {
         checkJournalDirectory();

         // this would create the connection provider while setting the JDBC global network timeout
         configureJdbcNetworkTimeout();

         nodeManager = createNodeManager(configuration.getNodeManagerLockLocation(), false);

         try {
            nodeManager.start();
         } catch (Exception e) {
            //if there's an error here, ensure remaining threads shut down
            stopTheServer(true);
            throw e;
         }

         ActiveMQServerLogger.LOGGER.serverStarting((haPolicy.isBackup() ? "Backup" : "Primary"), configuration);

         final boolean wasPrimary = !haPolicy.isBackup();
         if (!haPolicy.isBackup()) {
            activation = haPolicy.createActivation(this, false, activationParams, ioCriticalErrorListener);

            if (afterActivationCreated != null) {
               try {
                  afterActivationCreated.run();
               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e); // just debug, this is not supposed to happend, and if it does
               }

               afterActivationCreated = null;
            }

            if (haPolicy.isWaitForActivation()) {
               activation.run();
            } else {
               logger.trace("starting activation");

               activationThread = new ActivationThread(activation, ActiveMQMessageBundle.BUNDLE.activationForServer(this));
               activationThread.start();
            }
         }
         // The activation on fail-back may change the value of isBackup, for that reason we are
         // checking again here
         if (haPolicy.isBackup()) {
            if (haPolicy.isSharedStore()) {
               activation = haPolicy.createActivation(this, false, activationParams, ioCriticalErrorListener);
            } else {
               activation = haPolicy.createActivation(this, wasPrimary, activationParams, ioCriticalErrorListener);
            }

            if (afterActivationCreated != null) {
               try {
                  afterActivationCreated.run();
               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e); // just debug, this is not supposed to happend, and if it does
                  // it will be embedded code from tests
               }
               afterActivationCreated = null;
            }

            logger.trace("starting backupActivation");

            activationThread = new ActivationThread(activation, ActiveMQMessageBundle.BUNDLE.activationForServer(this));
            activationThread.start();
         } else {
            ActiveMQServerLogger.LOGGER.serverStarted(getVersion().getFullVersion(), configuration.getName(), nodeManager.getNodeId(), Objects.requireNonNullElse(identity, ""));
         }
         // start connector service
         connectorsService = new ConnectorsService(configuration, storageManager, scheduledPool, postOffice, serviceRegistry);
         connectorsService.start();
      } finally {
         // this avoids embedded applications using dirty contexts from startup
         OperationContextImpl.clearContext();
      }
   }


   private void takingLongToStart(Object criticalComponent) {
      ActiveMQServerLogger.LOGGER.tooLongToStart(criticalComponent);
      threadDump();
   }

   protected void initializeCriticalAnalyzer() throws Exception {

      // Some tests will play crazy frequenceistop/start
      CriticalAnalyzer analyzer = this.getCriticalAnalyzer();
      if (analyzer == null) {
         if (configuration.isCriticalAnalyzer()) {
            // this will have its own ScheduledPool
            analyzer = new CriticalAnalyzerImpl();
         } else {
            analyzer = EmptyCriticalAnalyzer.getInstance();
         }

         this.analyzer = analyzer;
      }

      /*
       * Calling this for cases where the server was stopped and now is being restarted... failback, etc...*/
      analyzer.clear();

      analyzer.setCheckTime(configuration.getCriticalAnalyzerCheckPeriod(), TimeUnit.MILLISECONDS).setTimeout(configuration.getCriticalAnalyzerTimeout(), TimeUnit.MILLISECONDS);

      if (configuration.isCriticalAnalyzer()) {
         analyzer.start();
      }

      final CriticalAnalyzerPolicy criticalAnalyzerPolicy = configuration.getCriticalAnalyzerPolicy();
      CriticalAction criticalAction = switch (criticalAnalyzerPolicy) {
         case HALT -> criticalComponent -> {
            if (ActiveMQServerImpl.this.state == SERVER_STATE.STARTING) {
               takingLongToStart(criticalComponent);
            } else {
               checkCriticalAnalyzerLogging();
               ActiveMQServerLogger.LOGGER.criticalSystemHalt(criticalComponent);

               threadDump();
               sendCriticalNotification(criticalComponent);

               Runtime.getRuntime().halt(70); // Linux systems will have /usr/include/sysexits.h showing 70 as internal software error
            }
         };
         case SHUTDOWN -> criticalComponent -> {
            if (ActiveMQServerImpl.this.state == SERVER_STATE.STARTING) {
               takingLongToStart(criticalComponent);
            } else {
               checkCriticalAnalyzerLogging();
               ActiveMQServerLogger.LOGGER.criticalSystemShutdown(criticalComponent);

               threadDump();

               // on the case of a critical failure, -1 cannot simply means forever.
               // in case graceful is -1, we will set it to 30 seconds
               sendCriticalNotification(criticalComponent);

               // you can't stop from the check thread,
               // nor can use an executor
               Thread stopThread = new Thread(() -> {
                  try {
                     ActiveMQServerImpl.this.stop();
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                  }
               });
               stopThread.start();
            }
         };
         case LOG -> criticalComponent -> {
            if (ActiveMQServerImpl.this.state == SERVER_STATE.STARTING) {
               takingLongToStart(criticalComponent);
            } else {
               checkCriticalAnalyzerLogging();
               ActiveMQServerLogger.LOGGER.criticalSystemLog(criticalComponent);
               threadDump();
               sendCriticalNotification(criticalComponent);
            }
         };
      };

      analyzer.addAction(criticalAction);
   }

   private static void checkCriticalAnalyzerLogging() {
      Logger criticalLogger = LoggerFactory.getLogger("org.apache.activemq.artemis.utils.critical");
      if (!criticalLogger.isTraceEnabled()) {
         ActiveMQServerLogger.LOGGER.enableTraceForCriticalAnalyzer();
      }
   }

   private void sendCriticalNotification(final CriticalComponent criticalComponent) {
      // on the case of a critical failure, -1 cannot simply means forever.
      // in case graceful is -1, we will set it to 30 seconds
      long timeout = configuration.getGracefulShutdownTimeout() < 0 ? 30000 : configuration.getGracefulShutdownTimeout();

      Thread notificationSender = new Thread(() -> {
         try {
            if (hasBrokerCriticalPlugins()) {
               callBrokerCriticalPlugins(plugin -> plugin.criticalFailure(criticalComponent));
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      });

      // I'm using a different thread here as we need to manage timeouts
      notificationSender.start();

      try {
         notificationSender.join(timeout);
      } catch (InterruptedException ignored) {
      }
   }

   @Override
   public void unlockActivation() {
      activationLock.release();
   }

   @Override
   public void lockActivation() {
      try {
         activationLock.acquire();
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.unableToAcquireLock(e);
      }
   }

   @Override
   public void setState(SERVER_STATE state) {
      this.state = state;
   }

   @Override
   public SERVER_STATE getState() {
      return state;
   }

   public void interruptActivationThread(NodeManager nodeManagerInUse) throws InterruptedException {
      long timeout = 30000;

      long start = System.currentTimeMillis();

      while (activationThread.isAlive() && System.currentTimeMillis() - start < timeout) {
         if (nodeManagerInUse != null) {
            nodeManagerInUse.interrupt();
         }

         activationThread.interrupt();

         activationThread.join(1000);

      }

      if (System.currentTimeMillis() - start >= timeout) {
         ActiveMQServerLogger.LOGGER.activationTimeout();
         threadDump();
      }
   }

   public void resetNodeManager() throws Exception {
      if (nodeManager != null) {
         nodeManager.stop();
      }
      nodeManager = createNodeManager(configuration.getNodeManagerLockLocation(), true);
   }

   @Override
   public Activation getActivation() {
      return activation;
   }

   @Override
   public HAPolicy getHAPolicy() {
      return haPolicy;
   }

   @Override
   public void setHAPolicy(HAPolicy haPolicy) {
      logger.trace("Setting {}, isBackup={} at {}", haPolicy, haPolicy.isBackup(), this);
      this.haPolicy = haPolicy;
   }

   @Override
   public void setMBeanServer(MBeanServer mbeanServer) {
      if (state == SERVER_STATE.STARTING || state == SERVER_STATE.STARTED) {
         throw ActiveMQMessageBundle.BUNDLE.cannotSetMBeanserver();
      }
      this.mbeanServer = mbeanServer;
   }

   @Override
   public MBeanServer getMBeanServer() {
      return mbeanServer;
   }

   @Override
   public void setSecurityManager(ActiveMQSecurityManager securityManager) {
      if (state == SERVER_STATE.STARTING || state == SERVER_STATE.STARTED) {
         throw ActiveMQMessageBundle.BUNDLE.cannotSetSecurityManager();
      }
      this.securityManager = securityManager;
   }

   private void validateAddExternalComponent(ActiveMQComponent externalComponent) {
      final SERVER_STATE state = this.state;
      if (state == SERVER_STATE.STOPPED || state == SERVER_STATE.STOPPING) {
         throw new IllegalStateException("cannot add " + externalComponent.getClass().getSimpleName() +
                                            " if state is " + state);
      }
   }

   @Override
   public void addExternalComponent(ActiveMQComponent externalComponent, boolean start) throws Exception {
      synchronized (externalComponents) {
         validateAddExternalComponent(externalComponent);
         externalComponents.add(externalComponent);
         if (start) {
            externalComponent.start();
         }
      }
   }

   @Override
   public void removeExternalComponent(ActiveMQComponent externalComponent) {
      synchronized (externalComponents) {
         externalComponents.remove(externalComponent);
      }
   }

   @Override
   public Executor getThreadPool() {
      return threadPool;
   }

   public void setActivation(Activation activation) {
      this.activation = activation;
   }

   /**
    * Stops the server in a different thread.
    */
   public final void stopTheServer(final boolean criticalIOError) {
      Thread thread = new Thread(() -> {
         try {
            ActiveMQServerImpl.this.stop(false, criticalIOError, false);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorStoppingServer(e);
         }
      });

      thread.start();
   }

   @Override
   public void stop() throws Exception {
      stop(true);
   }

   @Override
   public void stop(boolean isShutdown)  throws Exception {
      try {
         stop(false, isShutdown);
      } finally {
         if (isShutdown) networkHealthCheck.stop();
      }
   }

   @Override
   public void addActivationParam(String key, Object val) {
      activationParams.put(key, val);
   }

   @Override
   public boolean isAddressBound(String address) throws Exception {
      return postOffice.isAddressBound(SimpleString.of(address));
   }

   @Override
   public BindingQueryResult bindingQuery(SimpleString address, boolean newFQQN) throws Exception {
      if (address == null) {
         throw ActiveMQMessageBundle.BUNDLE.addressIsNull();
      }

      SimpleString realAddress = CompositeAddress.extractAddressName(address);
      AddressSettings addressSettings = getAddressSettingsRepository().getMatch(realAddress.toString());

      boolean autoCreateQeueus = addressSettings.isAutoCreateQueues();
      boolean autoCreateAddresses = addressSettings.isAutoCreateAddresses();
      boolean defaultPurgeOnNoConsumers = addressSettings.isDefaultPurgeOnNoConsumers();
      int defaultMaxConsumers = addressSettings.getDefaultMaxConsumers();
      boolean defaultExclusive = addressSettings.isDefaultExclusiveQueue();
      boolean defaultLastValue = addressSettings.isDefaultLastValueQueue();
      SimpleString defaultLastValueKey = addressSettings.getDefaultLastValueKey();
      boolean defaultNonDestructive = addressSettings.isDefaultNonDestructive();
      int defaultConsumersBeforeDispatch = addressSettings.getDefaultConsumersBeforeDispatch();
      long defaultDelayBeforeDispatch = addressSettings.getDefaultDelayBeforeDispatch();

      // make an exception for the management address (see HORNETQ-29)
      ManagementService managementService = getManagementService();
      if (managementService != null) {
         if (realAddress.equals(managementService.getManagementAddress())) {
            return new BindingQueryResult(true, new AddressInfo(managementService.getManagementAddress(), EnumSet.allOf(RoutingType.class)), Collections.emptyList(), autoCreateQeueus, autoCreateAddresses, defaultPurgeOnNoConsumers, defaultMaxConsumers, defaultExclusive, defaultLastValue, defaultLastValueKey, defaultNonDestructive, defaultConsumersBeforeDispatch, defaultDelayBeforeDispatch);
         }
      }

      List<SimpleString> names = new ArrayList<>();

      for (Binding binding : getPostOffice().getMatchingBindings(realAddress)) {
         if (binding.getType() == BindingType.LOCAL_QUEUE || binding.getType() == BindingType.REMOTE_QUEUE) {
            SimpleString name;
            if (!newFQQN && CompositeAddress.isFullyQualified(address.toString())) {
               // need to use the FQQN here for backwards compatibility with core JMS client
               name = CompositeAddress.toFullyQualified(realAddress, binding.getUniqueName());
            } else {
               name = binding.getUniqueName();
            }
            names.add(name);
         }
      }

      AddressInfo info = getAddressInfo(realAddress);

      return new BindingQueryResult(info != null, info, names, autoCreateQeueus, autoCreateAddresses, defaultPurgeOnNoConsumers, defaultMaxConsumers, defaultExclusive, defaultLastValue, defaultLastValueKey, defaultNonDestructive, defaultConsumersBeforeDispatch, defaultDelayBeforeDispatch);
   }

   @Override
   public QueueQueryResult queueQuery(SimpleString name) {
      if (name == null) {
         throw ActiveMQMessageBundle.BUNDLE.queueNameIsNull();
      }

      SimpleString realName = CompositeAddress.extractQueueName(name);

      final QueueQueryResult response;

      Binding binding = getPostOffice().getBinding(realName);

      final SimpleString addressName = binding != null && binding.getType() == BindingType.LOCAL_QUEUE ? binding.getAddress() : CompositeAddress.extractAddressName(name);

      final AddressSettings addressSettings = getAddressSettingsRepository().getMatch(addressName.toString());

      SimpleString managementAddress = getManagementService() != null ? getManagementService().getManagementAddress() : null;

      if (binding != null && binding.getBindable() instanceof Queue queue) {
         response = new QueueQueryResult(queue.getQueueConfiguration(), queue.getConsumerCount(), queue.getMessageCount(), addressSettings.isAutoCreateQueues(), true, addressSettings.getDefaultConsumerWindowSize());
      } else {
         QueueConfiguration config = QueueConfiguration.of(realName).setAddress(addressName);
         QueueConfigurationUtils.applyDynamicDefaults(config, addressSettings);
         boolean isManagement = realName.equals(managementAddress);
         response = new QueueQueryResult(config, isManagement ? -1 : 0, isManagement ? -1 : 0, addressSettings.isAutoCreateQueues(), isManagement ? true : false, addressSettings.getDefaultConsumerWindowSize());
      }

      return response;
   }

   @Override
   public AddressQueryResult addressQuery(SimpleString name) throws Exception {
      if (name == null) {
         throw ActiveMQMessageBundle.BUNDLE.queueNameIsNull();
      }

      SimpleString realName = CompositeAddress.extractAddressName(name);

      AddressSettings addressSettings = getAddressSettingsRepository().getMatch(realName.toString());

      boolean autoCreateAddresses = addressSettings.isAutoCreateAddresses();
      boolean defaultPurgeOnNoConsumers = addressSettings.isDefaultPurgeOnNoConsumers();
      int defaultMaxConsumers = addressSettings.getDefaultMaxConsumers();

      AddressInfo addressInfo = postOffice.getAddressInfo(realName);
      AddressQueryResult response;
      if (addressInfo != null) {
         response = new AddressQueryResult(addressInfo.getName(), addressInfo.getRoutingTypes(), addressInfo.getId(), addressInfo.isAutoCreated(), true, autoCreateAddresses, defaultPurgeOnNoConsumers, defaultMaxConsumers);
      } else {
         response = new AddressQueryResult(realName, null, -1, false, false, autoCreateAddresses, defaultPurgeOnNoConsumers, defaultMaxConsumers);
      }
      return response;
   }

   @Override
   public void registerBrokerConnection(BrokerConnection brokerConnection) {
      brokerConnectionMap.put(brokerConnection.getName(), brokerConnection);
   }

   @Override
   public void unregisterBrokerConnection(BrokerConnection brokerConnection) {
      brokerConnectionMap.remove(brokerConnection.getName());
   }

   @Override
   public void startBrokerConnection(String name) throws Exception {
      BrokerConnection connection = getBrokerConnection(name);
      connection.start();
   }

   protected BrokerConnection getBrokerConnection(String name) {
      BrokerConnection connection = brokerConnectionMap.get(name);
      if (connection == null) {
         throw new IllegalArgumentException("broker connection " + name + " not found");
      }
      return connection;
   }

   @Override
   public void stopBrokerConnection(String name) throws Exception {
      BrokerConnection connection = getBrokerConnection(name);
      connection.stop();
   }

   @Override
   public Collection<BrokerConnection> getBrokerConnections() {
      Set<BrokerConnection> collections = new HashSet<>(brokerConnectionMap.size());
      collections.addAll(brokerConnectionMap.values()); // making a copy
      return collections;
   }

   @Override
   public void threadDump() {
      ActiveMQServerLogger.LOGGER.threadDump(ThreadDumpUtil.threadDump(""));
   }

   @Override
   public final void fail(boolean failoverOnServerShutdown) throws Exception {
      stop(failoverOnServerShutdown, false, false, false);
   }

   @Override
   public final void stop(boolean failoverOnServerShutdown, boolean isExit) throws Exception {
      stop(failoverOnServerShutdown, false, false, isExit);
   }

   @Override
   public boolean isReplicaSync() {
      return activation.isReplicaSync();
   }

   public void stop(boolean failoverOnServerShutdown, final boolean criticalIOError, boolean restarting) {
      this.stop(failoverOnServerShutdown, criticalIOError, restarting, false);
   }

   private void stop(boolean failoverOnServerShutdown,
                     final boolean criticalIOError,
                     boolean restarting,
                     boolean isShutdown) {
      stop(failoverOnServerShutdown, criticalIOError, isShutdown || criticalIOError, restarting, isShutdown);
   }

   /**
    * Stops the server
    *
    * @param criticalIOError whether we have encountered an IO error with the journal etc
    */
   private void stop(boolean failoverOnServerShutdown,
                     final boolean criticalIOError,
                     boolean shutdownExternalComponents,
                     boolean restarting,
                     boolean isShutdown) {
      logger.debug("Stopping server {}", this);

      synchronized (this) {
         if (state == SERVER_STATE.STOPPED || state == SERVER_STATE.STOPPING) {
            return;
         }
         state = SERVER_STATE.STOPPING;

         ServerStatus.stopping(this);

         callPreDeActiveCallbacks();

         if (criticalIOError) {
            final ManagementService managementService = this.managementService;
            if (managementService != null) {
               // notifications trigger disk IO so we don't want to send any on a critical IO error
               managementService.enableNotifications(false);
            }
         }

         final FileStoreMonitor fileStoreMonitor = this.fileStoreMonitor;
         if (fileStoreMonitor != null) {
            fileStoreMonitor.stop();
            this.fileStoreMonitor = null;
         }

         if (failoverOnServerShutdown) {
            final Activation activation = this.activation;
            if (activation != null) {
               activation.sendPrimaryIsStopping();
            }
         }

         stopComponent(connectionRouterManager);

         stopComponent(connectorsService);

         // we stop the groupingHandler before we stop the cluster manager so binding mappings
         // aren't removed in case of failover
         if (groupingHandler != null) {
            managementService.removeNotificationListener(groupingHandler);
            stopComponent(groupingHandler);
         }
         stopComponent(federationManager);
         stopComponent(clusterManager);

         for (ActiveMQComponent component : this.protocolServices) {
            stopComponent(component);
         }
         protocolServices.clear();

         final RemotingService remotingService = this.remotingService;
         if (remotingService != null) {
            remotingService.pauseAcceptors();
         }

         // allows for graceful shutdown
         if (remotingService != null && configuration.isGracefulShutdownEnabled()) {
            long timeout = configuration.getGracefulShutdownTimeout();
            try {
               if (timeout == -1) {
                  remotingService.getConnectionCountLatch().await();
               } else {
                  remotingService.getConnectionCountLatch().await(timeout);
               }
            } catch (InterruptedException e) {
               ActiveMQServerLogger.LOGGER.interruptWhilstStoppingComponent(remotingService.getClass().getName());
            }
         }

         freezeConnections();
      }

      final Activation activation = this.activation;
      if (activation != null) {
         activation.postConnectionFreeze();
      }

      closeAllServerSessions(criticalIOError);

      // *************************************************************************************************************
      // There's no need to sync this part of the method, since the state stopped | stopping is checked within the sync
      //
      // we can't synchronized the whole method here as that would cause a deadlock
      // so stop is checking for stopped || stopping inside the lock
      // which will be already enough to guarantee that no other thread will be accessing this method here.
      //
      // *************************************************************************************************************

      final StorageManager storageManager = this.storageManager;
      if (storageManager != null)
         storageManager.clearContext();

      //before we stop any components deactivate any callbacks
      callDeActiveCallbacks();

      stopComponent(backupManager);

      if (activation != null) {
         try {
            activation.preStorageClose();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(activation.getClass().getName(), t);
         }
      }

      final RemotingService remotingService = this.remotingService;

      if (remotingService != null) {
         // The notification must be sent with the StorageManager still up
         // this is because NotificationService will use storageManager.generateID
         remotingService.notifyStop();
      }

      // We start the preparation for the broker shutdown by first stopping acceptors, and removing connections
      // this is to avoid Exceptions while the broker is stopping as much as possible
      // by first stopping any pending connections before stopping the storage.
      // if we stopped the journal first before remoting we could have more exceptions being sent for the client.
      try {
         if (remotingService != null) {
            // it will close all connections except to the one used by replication
            remotingService.prepareStop(criticalIOError,  storageManager != null ? storageManager.getUsedConnections() : Collections.emptySet());
         }
      } catch (Throwable t) {
         ActiveMQServerLogger.LOGGER.errorStoppingComponent(remotingService.getClass().getName(), t);
      }

      stopComponent(pagingManager);

      if (!criticalIOError && pagingManager != null) {
         pagingManager.counterSnapshot();
      }

      final ManagementService managementService = this.managementService;

      // we have to disable management service before stopping the storage
      // otherwise management would send notifications eventually
      if (managementService != null) {
         try {
            managementService.enableNotifications(false);
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(managementService.getClass().getName(), t);
         }
      }

      if (storageManager != null) {
         try {
            storageManager.stop(criticalIOError, failoverOnServerShutdown);
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(storageManager.getClass().getName(), t);
         }
      }

      // We stop remotingService before otherwise we may lock the system in case of a critical IO
      // error shutdown
      if (remotingService != null) {
         try {
            remotingService.stop(criticalIOError);
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(remotingService.getClass().getName(), t);
         }
      }

      // Stop the management service after the remoting service to ensure all acceptors are deregistered with JMX
      if (managementService != null) {
         try {
            managementService.unregisterServer();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(managementService.getClass().getName(), t);
         }
      }

      stopComponent(managementService);
      stopComponent(resourceManager);
      stopComponent(postOffice);

      if (scheduledPool != null && !scheduledPoolSupplied) {
         // we just interrupt all running tasks, these are supposed to be pings and the like.
         scheduledPool.shutdownNow();
      }

      stopComponent(memoryManager);

      for (SecuritySettingPlugin securitySettingPlugin : configuration.getSecuritySettingPlugins()) {
         securitySettingPlugin.stop();
      }

      if (ioExecutorPool != null) {
         shutdownPool(ioExecutorPool);
      }

      if (pageExecutorPool != null) {
         shutdownPool(pageExecutorPool);
      }

      if (!scheduledPoolSupplied)
         scheduledPool = null;

      if (securityStore != null) {
         try {
            securityStore.stop();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(securityStore.getClass().getName(), t);
         }
      }

      installMirrorController(null);

      pagingManager = null;
      securityStore = null;
      resourceManager = null;
      postOffice = null;
      queueFactory = null;
      resourceManager = null;
      messagingServerControl = null;
      memoryManager = null;
      backupManager = null;
      extraRecordsLoader = null;
      this.storageManager = null;

      sessions.clear();

      state = SERVER_STATE.STOPPED;

      activationLatch.setCount(1);

      // to display in the log message
      SimpleString tempNodeID = getNodeID();
      if (activation != null) {
         try {
            activation.close(failoverOnServerShutdown, restarting);
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingComponent(activation.getClass().getName(), t);
         }
      }

      // JDBC journal can use this thread pool to configure the network timeout on a pooled connection:
      // better to stop it after closing activation (and JDBC node manager on it)
      final ExecutorService threadPool = this.threadPool;
      if (threadPool != null && !threadPoolSupplied) {
         shutdownPool(threadPool);
      }
      if (!threadPoolSupplied) {
         this.threadPool = null;
      }

      // given that threadPool can be garbage collected, need to clear anything that would make it leaks
      clearJdbcNetworkTimeout();

      if (activationThread != null) {
         try {
            activationThread.join(30000);
         } catch (InterruptedException e) {
            ActiveMQServerLogger.LOGGER.interruptWhilstStoppingComponent(activationThread.getClass().getName());
         }

         if (activationThread.isAlive()) {
            ActiveMQServerLogger.LOGGER.activationDidntFinish(this);
            activationThread.interrupt();
         }
      }

      stopComponent(nodeManager);

      nodeManager = null;

      addressSettingsRepository.clearListeners();

      addressSettingsRepository.clearCache();

      scaledDownNodeIDs.clear();

      connectedClientIds.clear();

      stopExternalComponents(shutdownExternalComponents);

      try {
         this.analyzer.clear();
         this.analyzer.stop();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      } finally {
         this.analyzer = null;
      }

      for (ActivateCallback callback: activateCallbacks) {
         if (isShutdown) {
            callback.shutdown(this);
         } else {
            callback.stop(this);
         }
      }

      if (identity != null) {
         ActiveMQServerLogger.LOGGER.serverStopped("identity=" + identity + ",version=" + getVersion().getFullVersion(), tempNodeID, getUptime());
      } else {
         ActiveMQServerLogger.LOGGER.serverStopped(getVersion().getFullVersion(), tempNodeID, getUptime());
      }
   }

   private void shutdownPool(ExecutorService executorService) {
      executorService.shutdown();
      try {
         if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            ActiveMQServerLogger.LOGGER.timedOutStoppingThreadpool(threadPool);
            for (Runnable r : executorService.shutdownNow()) {
               logger.debug("Cancelled the execution of {}", r);
            }
         }
      } catch (InterruptedException e) {
         ActiveMQServerLogger.LOGGER.interruptWhilstStoppingComponent(threadPool.getClass().getName());
      }
   }

   public boolean checkBrokerIsNotColocated(String nodeId) {
      if (parentServer == null) {
         return true;
      } else {
         return !parentServer.getNodeID().toString().equals(nodeId);
      }
   }

   /**
    * Freeze all connections.
    * <p>
    * If replicating, avoid freezing the replication connection. Helper method for
    * {@link #stop(boolean, boolean, boolean)}.
    */
   private void freezeConnections() {
      Activation activation = this.activation;
      if (activation != null) {
         activation.freezeConnections(remotingService);
      }

      // after disconnecting all the clients close all the server sessions so any messages in delivery will be cancelled back to the queue
      for (ServerSession serverSession : sessions.values()) {
         try {
            serverSession.close(true);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorClosingSession(e);
         }
      }
   }

   /**
    * We close all the exception in an attempt to let any pending IO to finish to avoid scenarios
    * where the send or ACK got to disk but the response didn't get to the client It may still be
    * possible to have this scenario on a real failure (without the use of XA) But at least we will
    * do our best to avoid it on regular shutdowns
    */
   private void closeAllServerSessions(final boolean criticalIOError) {
      if (state != SERVER_STATE.STOPPING) {
         return;
      }
      for (ServerSession session : sessions.values()) {
         try {
            session.close(true);
         } catch (Exception e) {
            // If anything went wrong with closing sessions.. we should ignore it
            // such as transactions.. etc.
            ActiveMQServerLogger.LOGGER.errorClosingSessionsWhileStoppingServer(e);
         }
      }
   }

   static void stopComponent(ActiveMQComponent component) {
      try {
         if (component != null) {
            component.stop();
         }
      } catch (Throwable t) {
         ActiveMQServerLogger.LOGGER.errorStoppingComponent(component.getClass().getName(), t);
      }
   }

   // ActiveMQServer implementation
   // -----------------------------------------------------------

   @Override
   public String describe() {
      StringWriter str = new StringWriter();
      PrintWriter out = new PrintWriter(str);

      out.println(ActiveMQMessageBundle.BUNDLE.serverDescribe(identity, getClusterManager().describe()));

      return str.toString();
   }

   @Override
   public String destroyConnectionWithSessionMetadata(String metaKey, String parameterValue) throws Exception {
      StringBuilder operationsExecuted = new StringBuilder();

      try {
         operationsExecuted.append("**************************************************************************************************\n");
         operationsExecuted.append(ActiveMQMessageBundle.BUNDLE.destroyConnectionWithSessionMetadataHeader(metaKey, parameterValue) + "\n");

         Set<ServerSession> allSessions = getSessions();

         ServerSession sessionFound = null;
         for (ServerSession session : allSessions) {
            try {
               String value = session.getMetaData(metaKey);
               if (value != null && value.equals(parameterValue)) {
                  sessionFound = session;
                  operationsExecuted.append(ActiveMQMessageBundle.BUNDLE.destroyConnectionWithSessionMetadataClosingConnection(sessionFound.toString()) + "\n");
                  RemotingConnection conn = session.getRemotingConnection();
                  if (conn != null) {
                     conn.fail(ActiveMQMessageBundle.BUNDLE.destroyConnectionWithSessionMetadataSendException(metaKey, parameterValue));
                  }
                  session.close(true);
                  sessions.remove(session.getName());
               }
            } catch (Throwable e) {
               ActiveMQServerLogger.LOGGER.unableDestroyConnectionWithSessionMetadata(e);
            }
         }

         if (sessionFound == null) {
            operationsExecuted.append(ActiveMQMessageBundle.BUNDLE.destroyConnectionWithSessionMetadataNoSessionFound(metaKey, parameterValue) + "\n");
         }

         operationsExecuted.append("**************************************************************************************************");

         return operationsExecuted.toString();
      } finally {
         // This operation is critical for the knowledge of the admin, so we need to add info logs for later knowledge
         ActiveMQServerLogger.LOGGER.onDestroyConnectionWithSessionMetadata(operationsExecuted.toString());
      }

   }

   @Override
   public void setIdentity(String identity) {
      this.identity = identity;
   }

   @Override
   public String getIdentity() {
      return identity;
   }

   @Override
   public ScheduledExecutorService getScheduledPool() {
      return scheduledPool;
   }

   @Override
   public Configuration getConfiguration() {
      return configuration;
   }

   @Override
   public PagingManager getPagingManager() {
      return pagingManager;
   }

   @Override
   public RemotingService getRemotingService() {
      return remotingService;
   }

   @Override
   public StorageManager getStorageManager() {
      return storageManager;
   }

   @Override
   public ActiveMQSecurityManager getSecurityManager() {
      return securityManager;
   }

   @Override
   public ManagementService getManagementService() {
      return managementService;
   }

   @Override
   public HierarchicalRepository<Set<Role>> getSecurityRepository() {
      return securityRepository;
   }

   @Override
   public NodeManager getNodeManager() {
      return nodeManager;
   }

   @Override
   public HierarchicalRepository<AddressSettings> getAddressSettingsRepository() {
      return addressSettingsRepository;
   }

   @Override
   public ResourceManager getResourceManager() {
      return resourceManager;
   }

   @Override
   public MetricsManager getMetricsManager() {
      return metricsManager;
   }

   @Override
   public Version getVersion() {
      return version;
   }

   @Override
   public boolean isStarted() {
      return state == SERVER_STATE.STARTED;
   }

   @Override
   public ClusterManager getClusterManager() {
      return clusterManager;
   }

   @Override
   public ConnectionRouterManager getConnectionRouterManager() {
      return connectionRouterManager;
   }

   public BackupManager getBackupManager() {
      return backupManager;
   }

   @Override
   public ServerSession createSession(final String name,
                                      final String username,
                                      final String password,
                                      final int minLargeMessageSize,
                                      final RemotingConnection connection,
                                      final boolean autoCommitSends,
                                      final boolean autoCommitAcks,
                                      final boolean preAcknowledge,
                                      final boolean xa,
                                      final String defaultAddress,
                                      final SessionCallback callback,
                                      final boolean autoCreateQueues,
                                      final OperationContext context,
                                      final Map<SimpleString, RoutingType> prefixes,
                                      final String securityDomain,
                                      String validatedUser,
                                      boolean isLegacyProducer) throws Exception {
      if (validatedUser == null) {
         validatedUser = validateUser(username, password, connection, securityDomain);
      }

      checkSessionLimit(validatedUser);

      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.createCoreSession(this, connection.getSubject(), connection.getRemoteAddress(), name, username, "****", minLargeMessageSize, connection, autoCommitSends,
                                       autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, prefixes);
      }

      final ServerSessionImpl session = internalCreateSession(name, username, password, validatedUser, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, context, autoCreateQueues, prefixes, securityDomain, isLegacyProducer);

      return session;
   }

   @Override
   public String validateUser(String username, String password, RemotingConnection connection, String securityDomain) throws Exception {
      String validatedUser = "";

      if (securityStore != null) {
         validatedUser = securityStore.authenticate(username, password, connection, securityDomain);
      }
      return validatedUser;
   }

   @Override
   public ServerSession createInternalSession(String name,
                                       int minLargeMessageSize,
                                       RemotingConnection connection,
                                       boolean autoCommitSends,
                                       boolean autoCommitAcks,
                                       boolean preAcknowledge,
                                       boolean xa,
                                       String defaultAddress,
                                       SessionCallback callback,
                                       boolean autoCreateQueues,
                                       OperationContext context,
                                       Map<SimpleString, RoutingType> prefixes,
                                       String securityDomain,
                                       boolean isLegacyProducer) throws Exception {
      ServerSessionImpl session = internalCreateSession(name, null, null, null, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, context, autoCreateQueues, prefixes, securityDomain, isLegacyProducer);
      session.disableSecurity();
      return session;
   }


   private void checkSessionLimit(String username) throws Exception {
      if (configuration.getResourceLimitSettings() != null && configuration.getResourceLimitSettings().containsKey(username)) {
         ResourceLimitSettings limits = configuration.getResourceLimitSettings().get(username);

         if (limits.getMaxSessions() == -1) {
            return;
         } else if (limits.getMaxSessions() == 0 || getSessionCountForUser(username) >= limits.getMaxSessions()) {
            throw ActiveMQMessageBundle.BUNDLE.sessionLimitReached(username, limits.getMaxSessions());
         }
      }
   }

   private int getSessionCountForUser(String username) {
      int sessionCount = 0;

      for (Entry<String, ServerSession> sessionEntry : sessions.entrySet()) {
         if ((sessionEntry.getValue().getValidatedUser() != null && sessionEntry.getValue().getValidatedUser().equals(username)) || (sessionEntry.getValue().getUsername() != null && sessionEntry.getValue().getUsername().equals(username))) {
            sessionCount++;
         }
      }

      return sessionCount;
   }

   @Override
   public void checkQueueCreationLimit(String username) throws Exception {
      if (configuration.getResourceLimitSettings() != null && configuration.getResourceLimitSettings().containsKey(username)) {
         ResourceLimitSettings limits = configuration.getResourceLimitSettings().get(username);

         if (limits.getMaxQueues() == -1) {
            return;
         } else if (limits.getMaxQueues() == 0 || getQueueCountForUser(username) >= limits.getMaxQueues()) {
            throw ActiveMQMessageBundle.BUNDLE.queueLimitReached(username, limits.getMaxQueues());
         }
      }
   }

   public int getQueueCountForUser(String username) throws Exception {
      SimpleString userNameSimpleString = SimpleString.of(username);

      AtomicInteger bindingsCount = new AtomicInteger(0);
      postOffice.getAllBindings().forEach((b) -> {
         if (b instanceof LocalQueueBinding localQueueBinding) {
            SimpleString user = localQueueBinding.getQueue().getUser();
            if (user != null) {
               if (user.equals(userNameSimpleString)) {
                  bindingsCount.incrementAndGet();
               }
            }
         }
      });

      return bindingsCount.get();
   }

   protected ServerSessionImpl internalCreateSession(String name,
                                                     String username,
                                                     String password,
                                                     String validatedUser,
                                                     int minLargeMessageSize,
                                                     RemotingConnection connection,
                                                     boolean autoCommitSends,
                                                     boolean autoCommitAcks,
                                                     boolean preAcknowledge,
                                                     boolean xa,
                                                     String defaultAddress,
                                                     SessionCallback callback,
                                                     OperationContext context,
                                                     boolean autoCreateQueues,
                                                     Map<SimpleString, RoutingType> prefixes,
                                                     String securityDomain,
                                                     boolean isLegacyProducer) throws Exception {

      if (hasBrokerSessionPlugins()) {
         callBrokerSessionPlugins(plugin -> plugin.beforeCreateSession(name, username, minLargeMessageSize, connection,
                                                                       autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes));
      }

      ServerSessionImpl session = new ServerSessionImpl(name, username, password, validatedUser, minLargeMessageSize, autoCommitSends, autoCommitAcks, preAcknowledge, configuration.isPersistDeliveryCountBeforeDelivery(), xa, connection, this, defaultAddress == null ? null : SimpleString.of(defaultAddress), callback, context, prefixes, securityDomain, isLegacyProducer);

      sessions.put(name, session);
      totalSessionCount.incrementAndGet();
      connection.addFailureListener(session);
      if (connection.isDestroyed()) {
         session.close(true);
      }

      if (hasBrokerSessionPlugins()) {
         callBrokerSessionPlugins(plugin -> plugin.afterCreateSession(session));
      }

      return session;
   }

   @Override
   public SecurityStore getSecurityStore() {
      return securityStore;
   }

   @Override
   public void removeSession(final String name) throws Exception {
      sessions.remove(name);
   }

   @Override
   public ServerSession lookupSession(String key, String value) {
      // getSessions is called here in a try to minimize locking the Server while this check is being done
      Set<ServerSession> allSessions = getSessions();

      for (ServerSession session : allSessions) {
         String metaValue = session.getMetaData(key);
         if (metaValue != null && metaValue.equals(value)) {
            return session;
         }
      }

      return null;
   }

   @Override
   public List<ServerSession> getSessions(final String connectionID) {
      Set<Entry<String, ServerSession>> sessionEntries = sessions.entrySet();
      List<ServerSession> matchingSessions = new ArrayList<>();
      for (Entry<String, ServerSession> sessionEntry : sessionEntries) {
         ServerSession serverSession = sessionEntry.getValue();
         if (serverSession.getConnectionID().toString().equals(connectionID)) {
            matchingSessions.add(serverSession);
         }
      }
      return matchingSessions;
   }

   @Override
   public Set<ServerSession> getSessions() {
      return new HashSet<>(sessions.values());
   }

   @Override
   public boolean isActive() {
      return activationLatch.getCount() < 1;
   }

   @Override
   public boolean waitForActivation(long timeout, TimeUnit unit) throws InterruptedException {
      return activationLatch.await(timeout, unit);
   }

   @Override
   public ActiveMQServerControlImpl getActiveMQServerControl() {
      return messagingServerControl;
   }

   @Override
   public int getConnectionCount() {
      return remotingService.getConnectionCount();
   }

   @Override
   public long getTotalConnectionCount() {
      return remotingService.getTotalConnectionCount();
   }

   @Override
   public int getSessionCount() {
      return sessions.size();
   }

   @Override
   public long getTotalSessionCount() {
      return totalSessionCount.get();
   }

   @Override
   public long getTotalMessageCount() {
      long total = 0;

      for (Binding binding : iterableOf(postOffice.getAllBindings())) {
         if (binding.getType() == BindingType.LOCAL_QUEUE) {
            total += ((LocalQueueBinding) binding).getQueue().getMessageCount();
         }
      }

      return total;
   }

   @Override
   public long getTotalMessagesAdded() {
      long total = 0;

      for (Binding binding : iterableOf(postOffice.getAllBindings())) {
         if (binding.getType() == BindingType.LOCAL_QUEUE) {
            total += ((LocalQueueBinding) binding).getQueue().getMessagesAdded();
         }
      }

      return total;
   }

   @Override
   public long getTotalMessagesAcknowledged() {
      long total = 0;

      for (Binding binding : iterableOf(postOffice.getAllBindings())) {
         if (binding.getType() == BindingType.LOCAL_QUEUE) {
            total += ((LocalQueueBinding) binding).getQueue().getMessagesAcknowledged();
         }
      }

      return total;
   }

   @Override
   public long getTotalConsumerCount() {
      long total = 0;

      for (Binding binding : iterableOf(postOffice.getAllBindings())) {
         if (binding.getType() == BindingType.LOCAL_QUEUE) {
            total += ((LocalQueueBinding) binding).getQueue().getConsumerCount();
         }
      }

      return total;
   }

   @Override
   public PostOffice getPostOffice() {
      return postOffice;
   }

   @Override
   public QueueFactory getQueueFactory() {
      return queueFactory;
   }

   @Override
   public SimpleString getNodeID() {
      return nodeManager == null ? null : nodeManager.getNodeId();
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filterString,
                            final boolean durable,
                            final boolean temporary) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString());
      return createQueue(address, routingType, queueName, filterString, durable, temporary, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), as.isAutoCreateAddresses());
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString user,
                            final SimpleString filterString,
                            final boolean durable,
                            final boolean temporary) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString());
      return createQueue(address, routingType, queueName, filterString, user, durable, temporary, false, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), as.isAutoCreateAddresses());
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filter,
                            final boolean durable,
                            final boolean temporary,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean autoCreateAddress) throws Exception {
      return createQueue(address, routingType, queueName, filter, null, durable, temporary, false, maxConsumers, purgeOnNoConsumers, autoCreateAddress);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filter,
                            final boolean durable,
                            final boolean temporary,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress) throws Exception {
      return createQueue(address, routingType, queueName, filter, null, durable, temporary, false, false, false, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filter,
                            final boolean durable,
                            final boolean temporary,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final SimpleString groupFirstKey,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress) throws Exception {
      return createQueue(address, routingType, queueName, filter, null, durable, temporary, false, false, false, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress);
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filter,
                            final boolean durable,
                            final boolean temporary,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final SimpleString groupFirstKey,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress,
                            final long ringSize) throws Exception {
      return createQueue(address, routingType, queueName, filter, null, durable, temporary, false, false, false, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, ringSize);
   }

   @Deprecated
   @Override
   public Queue createQueue(SimpleString address,
                            RoutingType routingType,
                            SimpleString queueName,
                            SimpleString filter,
                            SimpleString user,
                            boolean durable,
                            boolean temporary,
                            boolean autoCreated,
                            Integer maxConsumers,
                            Boolean purgeOnNoConsumers,
                            boolean autoCreateAddress) throws Exception {
      return createQueue(address, routingType, queueName, filter, user, durable, temporary, false, false, autoCreated, maxConsumers, purgeOnNoConsumers, autoCreateAddress);
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter, SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers, Boolean purgeOnNoConsumers, boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(addressInfo == null ? queueName.toString() : addressInfo.getName().toString());
      return createQueue(addressInfo, queueName, filter, user, durable, temporary, false, false, autoCreated, maxConsumers, purgeOnNoConsumers, as.isDefaultExclusiveQueue(),  as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.getDefaultGroupFirstKey(), as.isDefaultLastValueQueue(), as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreateAddress, false, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter, SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue, boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(addressInfo == null ? queueName.toString() : addressInfo.getName().toString());
      return createQueue(addressInfo, queueName, filter, user, durable, temporary, false, false, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive,  as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.getDefaultGroupFirstKey(), lastValue, as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreateAddress, false, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter, SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, Boolean lastValue, SimpleString lastValueKey, Boolean nonDestructive, Integer consumersBeforeDispatch, Long delayBeforeDispatch, Boolean autoDelete, Long autoDeleteDelay, Long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(addressInfo == null ? queueName.toString() : addressInfo.getName().toString());
      return createQueue(addressInfo, queueName, filter, user, durable, temporary, false, false, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, as.getDefaultGroupFirstKey(), lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, false, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter, SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, SimpleString groupFirstKey, Boolean lastValue, SimpleString lastValueKey, Boolean nonDestructive, Integer consumersBeforeDispatch, Long delayBeforeDispatch, Boolean autoDelete, Long autoDeleteDelay, Long autoDeleteMessageCount, boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(addressInfo == null ? queueName.toString() : addressInfo.getName().toString());
      return createQueue(addressInfo, queueName, filter, user, durable, temporary, false, false, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, false, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(AddressInfo addressInfo, SimpleString queueName, SimpleString filter, SimpleString user, boolean durable, boolean temporary, boolean autoCreated, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean groupRebalance, Integer groupBuckets, SimpleString groupFirstKey, Boolean lastValue, SimpleString lastValueKey, Boolean nonDestructive, Integer consumersBeforeDispatch, Long delayBeforeDispatch, Boolean autoDelete, Long autoDeleteDelay, Long autoDeleteMessageCount, boolean autoCreateAddress, Long ringSize) throws Exception {
      return createQueue(addressInfo, queueName, filter, user, durable, temporary, false, false, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, false, ringSize);
   }

   static boolean isAutoDelete(boolean autoCreated, AddressSettings addressSettings) {
      return autoCreated ? addressSettings.isAutoDeleteQueues() : addressSettings.isAutoDeleteCreatedQueues();
   }

   @Deprecated
   @Override
   public Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                     SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                     boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString());
      return createQueue(address, routingType, queueName, filter, user, durable, temporary, ignoreIfExists, transientQueue, autoCreated, maxConsumers, purgeOnNoConsumers, as.isDefaultExclusiveQueue(), as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), as.isDefaultLastValueQueue(), as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreateAddress);
   }

   @Deprecated
   @Override
   public Queue createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                            SimpleString user, boolean durable, boolean temporary, boolean ignoreIfExists, boolean transientQueue,
                            boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, boolean exclusive, boolean lastValue, boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString());
      return createQueue(address, routingType, queueName, filter, user, durable, temporary, ignoreIfExists, transientQueue, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive,  as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), lastValue, as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), isAutoDelete(autoCreated, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount(), autoCreateAddress);
   }


   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final SimpleString queueName,
                            final SimpleString filterString,
                            final boolean durable,
                            final boolean temporary) throws Exception {
      return createQueue(address, getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString()).getDefaultQueueRoutingType(), queueName, filterString, durable, temporary);
   }

   @Deprecated
   @Override
   public void createSharedQueue(final SimpleString address,
                                 RoutingType routingType,
                                 final SimpleString name,
                                 final SimpleString filterString,
                                 final SimpleString user,
                                 boolean durable) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? name.toString() : address.toString());
      createSharedQueue(address, routingType, name, filterString, user, durable, as.getDefaultMaxConsumers(), as.isDefaultPurgeOnNoConsumers(), as.isDefaultExclusiveQueue(), as.isDefaultLastValueQueue());
   }

   @Deprecated
   @Override
   public void createSharedQueue(final SimpleString address,
                                 RoutingType routingType,
                                 final SimpleString name,
                                 final SimpleString filterString,
                                 final SimpleString user,
                                 boolean durable,
                                 int maxConsumers,
                                 boolean purgeOnNoConsumers,
                                 boolean exclusive,
                                 boolean lastValue) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? name.toString() : address.toString());
      createSharedQueue(address, routingType, name, filterString, user, durable, maxConsumers, purgeOnNoConsumers, exclusive, as.isDefaultGroupRebalance(), as.getDefaultGroupBuckets(), lastValue, as.getDefaultLastValueKey(), as.isDefaultNonDestructive(), as.getDefaultConsumersBeforeDispatch(), as.getDefaultDelayBeforeDispatch(), isAutoDelete(false, as), as.getAutoDeleteQueuesDelay(), as.getAutoDeleteQueuesMessageCount());
   }

   @Deprecated
   @Override
   public void createSharedQueue(final SimpleString address,
                                 RoutingType routingType,
                                 final SimpleString name,
                                 final SimpleString filterString,
                                 final SimpleString user,
                                 boolean durable,
                                 int maxConsumers,
                                 boolean purgeOnNoConsumers,
                                 boolean exclusive,
                                 boolean groupRebalance,
                                 int groupBuckets,
                                 boolean lastValue,
                                 SimpleString lastValueKey,
                                 boolean nonDestructive,
                                 int consumersBeforeDispatch,
                                 long delayBeforeDispatch,
                                 boolean autoDelete,
                                 long autoDeleteDelay,
                                 long autoDeleteMessageCount) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? name.toString() : address.toString());
      createSharedQueue(address, routingType, name, filterString, user, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, as.getDefaultGroupFirstKey(), lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount);
   }

   @Deprecated
   @Override
   public void createSharedQueue(final SimpleString address,
                                 RoutingType routingType,
                                 final SimpleString name,
                                 final SimpleString filterString,
                                 final SimpleString user,
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
                                 long autoDeleteMessageCount) throws Exception {
      createSharedQueue(QueueConfiguration.of(name)
                           .setAddress(address)
                           .setRoutingType(routingType)
                           .setFilterString(filterString)
                           .setUser(user)
                           .setDurable(durable)
                           .setMaxConsumers(maxConsumers)
                           .setPurgeOnNoConsumers(purgeOnNoConsumers)
                           .setExclusive(exclusive)
                           .setGroupRebalance(groupRebalance)
                           .setGroupBuckets(groupBuckets)
                           .setGroupFirstKey(groupFirstKey)
                           .setLastValue(lastValue)
                           .setLastValueKey(lastValueKey)
                           .setNonDestructive(nonDestructive)
                           .setConsumersBeforeDispatch(consumersBeforeDispatch)
                           .setDelayBeforeDispatch(delayBeforeDispatch)
                           .setAutoDelete(autoDelete)
                           .setAutoDeleteDelay(autoDeleteDelay)
                           .setAutoDeleteMessageCount(autoDeleteMessageCount));

   }

   @Override
   public void createSharedQueue(final QueueConfiguration queueConfiguration) throws Exception {
      final Queue queue = createQueue(queueConfiguration
                                         .setTemporary(!queueConfiguration.isDurable())
                                         .setTransient(!queueConfiguration.isDurable())
                                         .setAutoCreated(false)
                                         .setAutoCreateAddress(true), true);

      if (!queue.getAddress().equals(queueConfiguration.getAddress())) {
         throw ActiveMQMessageBundle.BUNDLE.queueSubscriptionBelongsToDifferentAddress(queueConfiguration.getName());
      }

      if (queueConfiguration.getFilterString() != null && (queue.getFilter() == null || !queue.getFilter().getFilterString().equals(queueConfiguration.getFilterString())) || queueConfiguration.getFilterString() == null && queue.getFilter() != null) {
         throw ActiveMQMessageBundle.BUNDLE.queueSubscriptionBelongsToDifferentFilter(queueConfiguration.getName());
      }

      logger.debug("Transient Queue {} created on address {} with filter={}", queueConfiguration.getName(), queueConfiguration.getAddress(), queueConfiguration.getFilterString());
   }

   @Override
   public Queue locateQueue(SimpleString queueName) {
      Binding binding = postOffice.getBinding(queueName);

      if (binding == null) {
         return null;
      }

      Bindable queue = binding.getBindable();

      if (!(queue instanceof Queue)) {
         throw new IllegalStateException("locateQueue should only be used to locate queues");
      }

      return (Queue) binding.getBindable();
   }

   @Override
   public Queue locateQueue(String address, String queue) throws Exception {
      Bindings bindings = postOffice.getBindingsForAddress(SimpleString.of(address));
      if (bindings == null) {
         return null;
      }

      Binding binding = bindings.getBinding(queue);
      if (binding == null) {
         return null;
      }

      Bindable bindingContent = binding.getBindable();

      if (!(bindingContent instanceof Queue)) {
         if (logger.isDebugEnabled()) {
            logger.debug("localQueue({}. {}) found non Queue ( {} ) on binding table, returning null instead", address, queue, bindingContent);
         }
         return null;
      }

      return (Queue) bindingContent;
   }

   @Deprecated
   @Override
   public Queue deployQueue(final SimpleString address,
                            final SimpleString resourceName,
                            final SimpleString filterString,
                            final boolean durable,
                            final boolean temporary) throws Exception {
      return createQueue(address, getAddressSettingsRepository().getMatch(address == null ? resourceName.toString() : address.toString()).getDefaultQueueRoutingType(), resourceName, filterString, durable, temporary);
   }

   @Deprecated
   @Override
   public Queue deployQueue(final String address,
                            final String resourceName,
                            final String filterString,
                            final boolean durable,
                            final boolean temporary) throws Exception {
      return deployQueue(SimpleString.of(address), SimpleString.of(resourceName), SimpleString.of(filterString), durable, temporary);
   }

   @Override
   public void destroyQueue(final SimpleString queueName) throws Exception {
      // The session is passed as an argument to verify if the user has authorization to delete the queue
      // in some cases (such as temporary queues) this should happen regardless of the authorization
      // since that will only happen during a session close, which will be used to cleanup on temporary queues
      destroyQueue(queueName, null, true);
   }

   @Override
   public void destroyQueue(final SimpleString queueName, final SecurityAuth session) throws Exception {
      destroyQueue(queueName, session, true);
   }

   @Override
   public void destroyQueue(final SimpleString queueName,
                            final SecurityAuth session,
                            final boolean checkConsumerCount) throws Exception {
      destroyQueue(queueName, session, checkConsumerCount, false);
   }

   @Override
   public void destroyQueue(final SimpleString queueName,
                            final SecurityAuth session,
                            final boolean checkConsumerCount,
                            final boolean removeConsumers) throws Exception {
      if (postOffice == null) {
         return;
      }

      Binding binding = postOffice.getBinding(queueName);

      if (binding == null) {
         throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(queueName);
      }

      destroyQueue(queueName, session, checkConsumerCount, removeConsumers, false);
   }

   @Override
   public void destroyQueue(final SimpleString queueName,
                            final SecurityAuth session,
                            final boolean checkConsumerCount,
                            final boolean removeConsumers,
                            final boolean forceAutoDeleteAddress) throws Exception {
      destroyQueue(queueName, session, checkConsumerCount, removeConsumers, forceAutoDeleteAddress, false);
   }

   @Override
   public void destroyQueue(final SimpleString queueName,
                            final SecurityAuth session,
                            final boolean checkConsumerCount,
                            final boolean removeConsumers,
                            final boolean forceAutoDeleteAddress,
                            final boolean checkMessageCount) throws Exception {
      if (postOffice == null) {
         return;
      }

      try {
         Binding binding = postOffice.getBinding(queueName);

         if (binding == null) {
            throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(queueName);
         }

         SimpleString address = binding.getAddress();

         Queue queue = (Queue) binding.getBindable();

         if (session != null) {
            // make sure the user has privileges to delete this queue
            securityStore.check(address, queueName, queue.isDurable() ? CheckType.DELETE_DURABLE_QUEUE : CheckType.DELETE_NON_DURABLE_QUEUE, session);
         }

         // This check is only valid if checkConsumerCount == true
         if (checkConsumerCount && queue.getConsumerCount() != 0) {
            throw ActiveMQMessageBundle.BUNDLE.cannotDeleteQueueWithConsumers(queue.getName(), queueName, binding.getClass().getName());
         }

         // This check is only valid if checkMessageCount == true
         if (checkMessageCount && queue.getAutoDeleteMessageCount() != -1) {
            long messageCount = queue.getMessageCount();
            if (queue.getMessageCount() > queue.getAutoDeleteMessageCount()) {
               throw ActiveMQMessageBundle.BUNDLE.cannotDeleteQueueWithMessages(queue.getName(), queueName, messageCount);
            }
         }

         if (hasBrokerQueuePlugins()) {
            callBrokerQueuePlugins(plugin -> plugin.beforeDestroyQueue(queue, session, checkConsumerCount, removeConsumers, forceAutoDeleteAddress));
         }

         if (mirrorControllerService != null) {
            mirrorControllerService.deleteQueue(queue.getAddress(), queue.getName());
         }

         queue.deleteQueue(removeConsumers);

         if (hasBrokerQueuePlugins()) {
            callBrokerQueuePlugins(plugin -> plugin.afterDestroyQueue(queue, address, session, checkConsumerCount, removeConsumers, forceAutoDeleteAddress));
         }

         if (forceAutoDeleteAddress) {
            AddressInfo addressInfo = getAddressInfo(address);

            if (postOffice != null && addressInfo != null && addressInfo.isAutoCreated() && !isAddressBound(address.toString()) && addressSettingsRepository.getMatch(address.toString()).getAutoDeleteAddressesDelay() == 0) {
               try {
                  removeAddressInfo(address, session);
               } catch (ActiveMQDeleteAddressException e) {
                  // Could be thrown if the address has bindings or is not deletable.
               }
            }
         }

         callPostQueueDeletionCallbacks(address, queueName);
      } finally {
         clearAddressCache();
      }
   }

   @Override
   public void clearAddressCache() {
      securityRepository.clearCache();
      addressSettingsRepository.clearCache();
   }

   @Override
   public void registerActivateCallback(final ActivateCallback callback) {
      activateCallbacks.add(callback);
   }

   @Override
   public void unregisterActivateCallback(final ActivateCallback callback) {
      activateCallbacks.remove(callback);
   }

   @Override
   public void registerActivationFailureListener(final ActivationFailureListener listener) {
      activationFailureListeners.add(listener);
   }

   @Override
   public void unregisterActivationFailureListener(final ActivationFailureListener listener) {
      activationFailureListeners.remove(listener);
   }

   @Override
   public void callActivationFailureListeners(final Exception e) {
      for (ActivationFailureListener listener : activationFailureListeners) {
         listener.activationFailed(e);
      }
   }

   @Override
   public void registerIOCriticalErrorListener(final IOCriticalErrorListener listener) {
      ioCriticalErrorListeners.add(listener);
   }

   @Override
   public void registerPostQueueCreationCallback(final PostQueueCreationCallback callback) {
      postQueueCreationCallbacks.add(callback);
   }

   @Override
   public void unregisterPostQueueCreationCallback(final PostQueueCreationCallback callback) {
      postQueueCreationCallbacks.remove(callback);
   }

   @Override
   public void callPostQueueCreationCallbacks(final SimpleString queueName) throws Exception {
      for (PostQueueCreationCallback callback : postQueueCreationCallbacks) {
         callback.callback(queueName);
      }
   }

   @Override
   public void registerPostQueueDeletionCallback(final PostQueueDeletionCallback callback) {
      postQueueDeletionCallbacks.add(callback);
   }

   @Override
   public void unregisterPostQueueDeletionCallback(final PostQueueDeletionCallback callback) {
      postQueueDeletionCallbacks.remove(callback);
   }

   @Override
   public void callPostQueueDeletionCallbacks(final SimpleString address,
                                              final SimpleString queueName) throws Exception {
      for (PostQueueDeletionCallback callback : postQueueDeletionCallbacks) {
         callback.callback(address, queueName);
      }
   }

   @Override
   public void registerBrokerPlugins(final List<ActiveMQServerBasePlugin> plugins) {
      configuration.registerBrokerPlugins(plugins);
      plugins.forEach(plugin -> plugin.registered(this));
   }

   @Override
   public void registerBrokerPlugin(final ActiveMQServerBasePlugin plugin) {
      configuration.registerBrokerPlugin(plugin);
      plugin.registered(this);
   }

   @Override
   public void unRegisterBrokerPlugin(final ActiveMQServerBasePlugin plugin) {
      configuration.unRegisterBrokerPlugin(plugin);
      plugin.unregistered(this);
   }

   @Override
   public List<ActiveMQServerBasePlugin> getBrokerPlugins() {
      return configuration.getBrokerPlugins();
   }

   @Override
   public List<ActiveMQServerConnectionPlugin> getBrokerConnectionPlugins() {
      return configuration.getBrokerConnectionPlugins();
   }

   @Override
   public List<ActiveMQServerSessionPlugin> getBrokerSessionPlugins() {
      return configuration.getBrokerSessionPlugins();
   }

   @Override
   public List<ActiveMQServerConsumerPlugin> getBrokerConsumerPlugins() {
      return configuration.getBrokerConsumerPlugins();
   }

   @Override
   public List<ActiveMQServerAddressPlugin> getBrokerAddressPlugins() {
      return configuration.getBrokerAddressPlugins();
   }

   @Override
   public List<ActiveMQServerQueuePlugin> getBrokerQueuePlugins() {
      return configuration.getBrokerQueuePlugins();
   }

   @Override
   public List<ActiveMQServerBindingPlugin> getBrokerBindingPlugins() {
      return configuration.getBrokerBindingPlugins();
   }

   @Override
   public List<ActiveMQServerMessagePlugin> getBrokerMessagePlugins() {
      return configuration.getBrokerMessagePlugins();
   }

   @Override
   public List<ActiveMQServerBridgePlugin> getBrokerBridgePlugins() {
      return configuration.getBrokerBridgePlugins();
   }

   @Override
   public List<ActiveMQServerCriticalPlugin> getBrokerCriticalPlugins() {
      return configuration.getBrokerCriticalPlugins();
   }

   @Override
   public List<ActiveMQServerFederationPlugin> getBrokerFederationPlugins() {
      return configuration.getBrokerFederationPlugins();
   }

   @Override
   public List<AMQPFederationBrokerPlugin> getBrokerAMQPFederationPlugins() {
      return configuration.getBrokerAMQPFederationPlugins();
   }

   @Override
   public List<ActiveMQServerResourcePlugin> getBrokerResourcePlugins() {
      return configuration.getBrokerResourcePlugins();
   }

   @Override
   public void callBrokerPlugins(final ActiveMQPluginRunnable pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerPlugins(), pluginRun);
   }

   @Override
   public void callBrokerConnectionPlugins(final ActiveMQPluginRunnable<ActiveMQServerConnectionPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerConnectionPlugins(), pluginRun);
   }

   @Override
   public void callBrokerSessionPlugins(final ActiveMQPluginRunnable<ActiveMQServerSessionPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerSessionPlugins(), pluginRun);
   }

   @Override
   public void callBrokerConsumerPlugins(final ActiveMQPluginRunnable<ActiveMQServerConsumerPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerConsumerPlugins(), pluginRun);
   }

   @Override
   public void callBrokerAddressPlugins(final ActiveMQPluginRunnable<ActiveMQServerAddressPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerAddressPlugins(), pluginRun);
   }

   @Override
   public void callBrokerQueuePlugins(final ActiveMQPluginRunnable<ActiveMQServerQueuePlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerQueuePlugins(), pluginRun);
   }

   @Override
   public void callBrokerBindingPlugins(final ActiveMQPluginRunnable<ActiveMQServerBindingPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerBindingPlugins(), pluginRun);
   }

   @Override
   public void callBrokerMessagePlugins(final ActiveMQPluginRunnable<ActiveMQServerMessagePlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerMessagePlugins(), pluginRun);
   }

   @Override
   public boolean callBrokerMessagePluginsCanAccept(ServerConsumer serverConsumer, MessageReference messageReference) throws ActiveMQException {
      for (ActiveMQServerMessagePlugin plugin : getBrokerMessagePlugins()) {
         try {
            //if ANY plugin returned false the message will not be accepted for that consumer
            if (!plugin.canAccept(serverConsumer, messageReference)) {
               return false;
            }
         } catch (Throwable e) {
            if (e instanceof ActiveMQException activeMQException) {
               logger.debug("plugin {} is throwing ActiveMQException", plugin);
               throw activeMQException;
            } else {
               logger.warn("Internal error on plugin {}", plugin, e);
            }
         }
      }
      //if ALL plugins have returned true consumer can accept message
      return true;
   }

   @Override
   public void callBrokerBridgePlugins(final ActiveMQPluginRunnable<ActiveMQServerBridgePlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerBridgePlugins(), pluginRun);
   }

   @Override
   public void callBrokerCriticalPlugins(final ActiveMQPluginRunnable<ActiveMQServerCriticalPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerCriticalPlugins(), pluginRun);
   }

   @Override
   public void callBrokerFederationPlugins(final ActiveMQPluginRunnable<ActiveMQServerFederationPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerFederationPlugins(), pluginRun);
   }

   @Override
   public void callBrokerAMQPFederationPlugins(final ActiveMQPluginRunnable<AMQPFederationBrokerPlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerAMQPFederationPlugins(), pluginRun);
   }

   @Override
   public void callBrokerResourcePlugins(final ActiveMQPluginRunnable<ActiveMQServerResourcePlugin> pluginRun) throws ActiveMQException {
      callBrokerPlugins(getBrokerResourcePlugins(), pluginRun);
   }

   private <P extends ActiveMQServerBasePlugin> void callBrokerPlugins(final List<P> plugins, final ActiveMQPluginRunnable<P> pluginRun) throws ActiveMQException {
      if (pluginRun != null) {
         for (P plugin : plugins) {
            try {
               pluginRun.run(plugin);
            } catch (Throwable e) {
               if (e instanceof ActiveMQException activeMQException) {
                  logger.debug("plugin {} is throwing ActiveMQException", plugin);
                  throw activeMQException;
               } else {
                  logger.warn("Internal error on plugin {}", pluginRun, e);
               }
            }
         }
      }
   }

   @Override
   public boolean hasBrokerPlugins() {
      return !getBrokerPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerConnectionPlugins() {
      return !getBrokerConnectionPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerSessionPlugins() {
      return !getBrokerSessionPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerConsumerPlugins() {
      return !getBrokerConsumerPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerAddressPlugins() {
      return !getBrokerAddressPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerQueuePlugins() {
      return !getBrokerQueuePlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerBindingPlugins() {
      return !getBrokerBindingPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerMessagePlugins() {
      return !getBrokerMessagePlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerBridgePlugins() {
      return !getBrokerBridgePlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerCriticalPlugins() {
      return !getBrokerCriticalPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerFederationPlugins() {
      return !getBrokerFederationPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerAMQPFederationPlugins() {
      return !getBrokerAMQPFederationPlugins().isEmpty();
   }

   @Override
   public boolean hasBrokerResourcePlugins() {
      return !getBrokerResourcePlugins().isEmpty();
   }

   @Override
   public ExecutorFactory getExecutorFactory() {
      return executorFactory;
   }

   @Override
   public ExecutorFactory getIOExecutorFactory() {
      return ioExecutorFactory;
   }

   @Override
   public void setGroupingHandler(final GroupingHandler groupingHandler) {
      if (this.groupingHandler != null && managementService != null) {
         // Removing old groupNotification
         managementService.removeNotificationListener(this.groupingHandler);
      }
      this.groupingHandler = groupingHandler;
      if (managementService != null) {
         managementService.addNotificationListener(this.groupingHandler);
      }

   }

   @Override
   public GroupingHandler getGroupingHandler() {
      return groupingHandler;
   }

   @Override
   public ReplicationManager getReplicationManager() {
      return activation.getReplicationManager();
   }

   @Override
   public ConnectorsService getConnectorsService() {
      return connectorsService;
   }

   @Override
   public FederationManager getFederationManager() {
      return federationManager;
   }

   @Override
   public Divert deployDivert(DivertConfiguration config) throws Exception {
      if (config.getName() == null) {
         throw ActiveMQMessageBundle.BUNDLE.divertWithNoName();
      }

      if (config.getAddress() == null) {
         ActiveMQServerLogger.LOGGER.divertWithNoAddress();

         return null;
      }

      if (config.getForwardingAddress() == null) {
         ActiveMQServerLogger.LOGGER.divertWithNoForwardingAddress();

         return null;
      }

      SimpleString sName = SimpleString.of(config.getName());

      if (postOffice.getBinding(sName) != null) {
         ActiveMQServerLogger.LOGGER.divertBindingAlreadyExists(sName);

         return null;
      }

      SimpleString sAddress = SimpleString.of(config.getAddress());

      Transformer transformer = getServiceRegistry().getDivertTransformer(config.getName(), config.getTransformerConfiguration());

      Filter filter = FilterImpl.createFilter(config.getFilterString());

      Divert divert = new DivertImpl(sName, sAddress, SimpleString.of(config.getForwardingAddress()),
                                     SimpleString.of(config.getRoutingName()), config.isExclusive(),
                                     filter, transformer, postOffice, storageManager, config.getRoutingType());

      Binding binding = new DivertBinding(storageManager.generateID(), sAddress, divert);

      storageManager.storeDivertConfiguration(new PersistedDivertConfiguration(config));

      postOffice.addBinding(binding);

      managementService.registerDivert(divert);

      return divert;
   }

   @Override
   public Divert updateDivert(DivertConfiguration config) throws Exception {
      final DivertBinding divertBinding = (DivertBinding) postOffice.getBinding(SimpleString.of(config.getName()));
      if (divertBinding == null) {
         return null;
      }

      final Divert divert = divertBinding.getDivert();

      Filter filter = FilterImpl.createFilter(config.getFilterString());
      if (filter == null) {
         divert.setFilter(null);
      } else {
         if (!filter.equals(divert.getFilter())) {
            divert.setFilter(filter);
         }
      }

      if (config.getTransformerConfiguration() != null) {
         getServiceRegistry().removeDivertTransformer(divert.getUniqueName().toString());
         Transformer transformer = getServiceRegistry().getDivertTransformer(
            config.getName(), config.getTransformerConfiguration());
         divert.setTransformer(transformer);
      }

      if (config.getForwardingAddress() != null) {
         SimpleString forwardAddress = SimpleString.of(config.getForwardingAddress());
         if (!forwardAddress.equals(divert.getForwardAddress())) {
            divert.setForwardAddress(forwardAddress);
         }
      }

      if (config.getRoutingType() != null && divert.getRoutingType() != config.getRoutingType()) {
         divert.setRoutingType(config.getRoutingType());
      }

      storageManager.storeDivertConfiguration(new PersistedDivertConfiguration(config));

      return divert;
   }

   @Override
   public void destroyDivert(SimpleString name) throws Exception {
      destroyDivert(name, false);
   }

   @Override
   public void destroyDivert(SimpleString name, boolean deleteFromStorage) throws Exception {
      Binding binding = postOffice.getBinding(name);
      if (binding == null) {
         throw ActiveMQMessageBundle.BUNDLE.noBindingForDivert(name);
      }
      if (!(binding instanceof DivertBinding)) {
         throw ActiveMQMessageBundle.BUNDLE.bindingNotDivert(name);
      }

      postOffice.removeBinding(name, null, true);

      if (((DivertBinding)binding).getDivert().getTransformer() != null) {
         getServiceRegistry().removeDivertTransformer(name.toString());
      }

      if (deleteFromStorage) {
         storageManager.deleteDivertConfiguration(name.toString());
      }
   }

   @Override
   public boolean deployBridge(BridgeConfiguration config) throws Exception {
      if (clusterManager != null && clusterManager.deployBridge(config)) {
         storageManager.storeBridgeConfiguration(new PersistedBridgeConfiguration(config));
         return true;
      }
      return false;
   }

   @Override
   public void destroyBridge(String name) throws Exception {
      if (clusterManager != null) {
         //Iterating through all bridges to catch "concurrency > 1" bridges matching supplied name
         for (Bridge bridge : clusterManager.getBridges().values()) {
            if (bridge.getConfiguration().getParentName().equals(name)) {
               clusterManager.destroyBridge(bridge.getConfiguration().getName());
               storageManager.deleteBridgeConfiguration(bridge.getConfiguration().getParentName());
            }
         }
      }
   }

   @Override
   public void deployFederation(FederationConfiguration config) throws Exception {
      if (federationManager != null) {
         federationManager.deploy(config);
      }
   }

   @Override
   public void undeployFederation(String name) throws Exception {
      if (federationManager != null) {
         federationManager.undeploy(name);
      }
   }

   @Override
   public ServerSession getSessionByID(String sessionName) {
      return sessions.get(sessionName);
   }

   @Override
   public String toString() {
      if (identity != null) {
         return "ActiveMQServerImpl::" + identity;
      } else if (configuration != null && configuration.getName() != null) {
         return "ActiveMQServerImpl::" + "name=" + configuration.getName();
      }
      return "ActiveMQServerImpl::" + (nodeManager != null ? "serverUUID=" + nodeManager.getUUID() : "");
   }

   /**
    * For tests only, don't use this method as it's not part of the API
    */
   public void replaceQueueFactory(QueueFactory factory) {
      this.queueFactory = factory;
   }

   @Override
   public PagingManager createPagingManager() throws Exception {
      return new PagingManagerImpl(getPagingStoreFactory(), addressSettingsRepository, configuration.getGlobalMaxSize(), configuration.getGlobalMaxMessages(), configuration.getManagementAddress(), this);
   }

   protected PagingStoreFactory getPagingStoreFactory() throws Exception {
      if (configuration.isUsingDatabasePersistence()) {
         DatabaseStorageConfiguration dbConf = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
         return new PagingStoreFactoryDatabase(dbConf, storageManager, configuration.getPageSyncTimeout(), scheduledPool, pageExecutorFactory, false, ioCriticalErrorListener);
      } else {
         return new PagingStoreFactoryNIO(storageManager, configuration.getPagingLocation(), configuration.getPageSyncTimeout(), scheduledPool, pageExecutorFactory, configuration.isJournalSyncNonTransactional(), ioCriticalErrorListener, configuration::isPurgePageFolders);
      }
   }

   /**
    * This method is protected as it may be used as a hook for creating a custom storage manager (on tests for instance)
    */
   protected StorageManager createStorageManager() {
      if (configuration.isPersistenceEnabled()) {
         if (configuration.isUsingDatabasePersistence()) {
            JDBCJournalStorageManager journal = new JDBCJournalStorageManager(configuration, getCriticalAnalyzer(), getScheduledPool(), executorFactory, ioExecutorFactory, ioCriticalErrorListener);
            this.getCriticalAnalyzer().add(journal);
            return journal;
         } else {
            // Default to File Based Storage Manager, (Legacy default configuration).
            JournalStorageManager journal = new JournalStorageManager(configuration, getCriticalAnalyzer(), executorFactory, scheduledPool, ioExecutorFactory, ioCriticalErrorListener);
            this.getCriticalAnalyzer().add(journal);
            return journal;
         }
      }
      return new NullStorageManager();
   }

   private void callActivateCallbacks() {
      for (ActivateCallback callback : activateCallbacks) {
         callback.activated();
      }
   }

   private void callPreActiveCallbacks() {
      for (ActivateCallback callback : activateCallbacks) {
         callback.preActivate();
      }
   }

   private void callDeActiveCallbacks() {
      for (ActivateCallback callback : activateCallbacks) {
         try {
            callback.deActivate();
         } catch (Throwable e) {
            // https://bugzilla.redhat.com/show_bug.cgi?id=1009530:
            // we won't interrupt the shutdown sequence because of a failed callback here
            ActiveMQServerLogger.LOGGER.unableToInvokeCallback(e);
         }
      }
   }

   private void callPreDeActiveCallbacks() {
      for (ActivateCallback callback : activateCallbacks) {
         try {
            callback.preDeActivate();
         } catch (Throwable e) {
            // we won't interrupt the shutdown sequence because of a failed callback here
            ActiveMQServerLogger.LOGGER.unableToInvokeCallback(e);
         }
      }
   }

   private void callActivationCompleteCallbacks() {
      for (ActivateCallback callback : activateCallbacks) {
         callback.activationComplete();
      }
   }

   /**
    * Sets up ActiveMQ Artemis Executor Services.
    */
   private void initializeExecutorServices() {
      int maxIoThreads = configuration.getPageMaxConcurrentIO() <= 0 ? Integer.MAX_VALUE : configuration.getPageMaxConcurrentIO();
      /*
       * We check to see if a Thread Pool is supplied in the InjectedObjectRegistry.  If so we created a new Ordered
       * Executor based on the provided Thread pool.  Otherwise we create a new ThreadPool.
       */
      if (serviceRegistry.getExecutorService() == null) {
         ThreadFactory tFactory = getThreadFactory(null);
         if (configuration.getThreadPoolMaxSize() == -1) {
            threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, THREAD_POOL_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new SynchronousQueue<>(), tFactory);
         } else {
            threadPool = new ActiveMQThreadPoolExecutor(0, configuration.getThreadPoolMaxSize(), THREAD_POOL_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, tFactory);
         }
      } else {
         threadPool = serviceRegistry.getExecutorService();
         this.threadPoolSupplied = true;
      }
      this.executorFactory = new OrderedExecutorFactory(threadPool);

      if (serviceRegistry.getIOExecutorService() == null) {
         this.ioExecutorPool = new ActiveMQThreadPoolExecutor(0, maxIoThreads, THREAD_POOL_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, getThreadFactory("io"));
      } else {
         this.ioExecutorPool = serviceRegistry.getIOExecutorService();
      }
      this.ioExecutorFactory = new OrderedExecutorFactory(ioExecutorPool);

      if (serviceRegistry.getPageExecutorService() == null) {
         this.pageExecutorPool = new ActiveMQThreadPoolExecutor(0, maxIoThreads, THREAD_POOL_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, getThreadFactory("paging"));
      } else {
         this.pageExecutorPool = serviceRegistry.getPageExecutorService();
      }
      this.pageExecutorFactory = new OrderedExecutorFactory(pageExecutorPool);

      /*
       * We check to see if a Scheduled Executor Service is provided in the InjectedObjectRegistry.  If so we use this
       * Scheduled ExecutorService otherwise we create a new one.
       */
      if (serviceRegistry.getScheduledExecutorService() == null) {
         ScheduledThreadPoolExecutor scheduledPoolExecutor = new ScheduledThreadPoolExecutor(configuration.getScheduledThreadPoolMaxSize(), getThreadFactory("scheduled"));
         scheduledPoolExecutor.setRemoveOnCancelPolicy(true);
         scheduledPool = scheduledPoolExecutor;
      } else {
         this.scheduledPoolSupplied = true;
         this.scheduledPool = serviceRegistry.getScheduledExecutorService();

         if (!(scheduledPool instanceof ScheduledThreadPoolExecutor) ||
            !((ScheduledThreadPoolExecutor)scheduledPool).getRemoveOnCancelPolicy()) {
            ActiveMQServerLogger.LOGGER.scheduledPoolWithNoRemoveOnCancelPolicy();
         }
      }
   }

   private ActiveMQThreadFactory getThreadFactory(String name) {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<ActiveMQThreadFactory>) () -> new ActiveMQThreadFactory(getThreadGroupName(name), false, ClientSessionFactoryImpl.class.getClassLoader()));
   }

   @Override
   public String getThreadGroupName(String groupName) {
      final String tFactoryGroupNameSuffix;
      if (identity != null) {
         tFactoryGroupNameSuffix = identity;
      } else if (configuration != null && configuration.getName() != null && !configuration.getName().isEmpty()) {
         tFactoryGroupNameSuffix = configuration.getName();
      } else {
         tFactoryGroupNameSuffix = Integer.toHexString(System.identityHashCode(this));
      }
      return (groupName == null ? "" : groupName + "-") + tFactoryGroupNameSuffix;
   }

   @Override
   public ServiceRegistry getServiceRegistry() {
      return serviceRegistry;
   }

   /**
    * Starts everything apart from RemotingService and loading the data.
    * <p>
    * After optional intermediary steps, this is meant to be followed by {@link #initialisePart2(boolean)}.
    */
   synchronized boolean initialisePart1(boolean scalingDown) throws Exception {
      if (state == SERVER_STATE.STOPPED)
         return false;


      ServerStatus.starting(this);

      if (configuration.getJournalType() == JournalType.ASYNCIO) {
         if (!AIOSequentialFileFactory.isSupported()) {
            ActiveMQServerLogger.LOGGER.switchingNIO();
            configuration.setJournalType(JournalType.NIO);
         } else if (!AIOSequentialFileFactory.isSupported(configuration.getJournalLocation())) {
            ActiveMQServerLogger.LOGGER.switchingNIOonPath(configuration.getJournalLocation().getAbsolutePath());
            configuration.setJournalType(JournalType.NIO);
         }
      }

      managementService = new ManagementServiceImpl(mbeanServer, configuration);

      if (configuration.getMemoryMeasureInterval() != -1) {
         memoryManager = new MemoryManager(configuration.getMemoryWarningThreshold(), configuration.getMemoryMeasureInterval());

         memoryManager.start();
      }

      // Create the hard-wired components

      callPreActiveCallbacks();

      // startReplication();

      storageManager = createStorageManager();

      if (!configuration.getClusterConfigurations().isEmpty() && ActiveMQDefaultConfiguration.getDefaultClusterUser().equals(configuration.getClusterUser()) && ActiveMQDefaultConfiguration.getDefaultClusterPassword().equals(configuration.getClusterPassword())) {
         ActiveMQServerLogger.LOGGER.clusterSecurityRisk();
      }

      securityStore = new SecurityStoreImpl(securityRepository, securityManager, configuration.getSecurityInvalidationInterval(), configuration.isSecurityEnabled(), configuration.getClusterUser(), configuration.getClusterPassword(), managementService, configuration.getAuthenticationCacheSize(), configuration.getAuthorizationCacheSize());

      queueFactory = new QueueFactoryImpl(executorFactory, scheduledPool, addressSettingsRepository, storageManager, this);

      pagingManager = createPagingManager();

      resourceManager = new ResourceManagerImpl(this, (int) (configuration.getTransactionTimeout() / 1000), configuration.getTransactionTimeoutScanPeriod(), scheduledPool);

      /*
       * If there is no plugin configured we don't want to instantiate a MetricsManager. This keeps the dependency
       * on Micrometer as "optional" in the Maven pom.xml. This is particularly beneficial because optional dependencies
       * are not required to be included in the OSGi bundle and the Micrometer jars apparently don't support OSGi.
       */
      if (configuration.getMetricsConfiguration() != null && configuration.getMetricsConfiguration().getPlugin() != null) {
         metricsManager = new MetricsManager(configuration.getName(), configuration.getMetricsConfiguration(), addressSettingsRepository, securityStore);
         metricsManager.registerExecutorService(BrokerMetricNames.GENERAL_EXECUTOR_SERVICE, threadPool);
         metricsManager.registerExecutorService(BrokerMetricNames.IO_EXECUTOR_SERVICE, ioExecutorPool);
         metricsManager.registerExecutorService(BrokerMetricNames.PAGE_EXECUTOR_SERVICE, pageExecutorPool);
         metricsManager.registerExecutorService(BrokerMetricNames.SCHEDULED_EXECUTOR_SERVICE, scheduledPool);
      }

      postOffice = new PostOfficeImpl(this, storageManager, pagingManager, queueFactory, managementService, configuration.getMessageExpiryScanPeriod(), configuration.getAddressQueueScanPeriod(), configuration.getWildcardConfiguration(), configuration.getIDCacheSize(), configuration.isPersistIDCache(), addressSettingsRepository);

      // This can't be created until node id is set
      clusterManager = new ClusterManager(executorFactory, this, postOffice, scheduledPool, managementService, configuration, nodeManager, haPolicy.useQuorumManager());

      federationManager = new FederationManager(this);

      backupManager = new BackupManager(this, executorFactory, scheduledPool, nodeManager, configuration, clusterManager);

      clusterManager.deploy();

      federationManager.deploy();

      connectionRouterManager = new ConnectionRouterManager(configuration, this, scheduledPool);

      connectionRouterManager.deploy();

      remotingService = new RemotingServiceImpl(clusterManager, configuration, this, managementService, scheduledPool, protocolManagerFactories, executorFactory.getExecutor(), serviceRegistry);

      messagingServerControl = managementService.registerServer(postOffice, securityStore, storageManager, configuration, addressSettingsRepository, securityRepository, resourceManager, remotingService, this, queueFactory, scheduledPool, pagingManager, haPolicy.isBackup());

      // Address settings need to deployed initially, since they're require on paging manager.start()

      if (!scalingDown) {
         deployAddressSettingsFromConfiguration();
      }

      //fix of ARTEMIS-1823
      if (!configuration.isPersistenceEnabled()) {
         for (AddressSettings addressSettings : addressSettingsRepository.values()) {
            if (addressSettings.getAddressFullMessagePolicy() == AddressFullMessagePolicy.PAGE) {
               ActiveMQServerLogger.LOGGER.pageWillBePersisted();
               break;
            }
         }
      }

      storageManager.start();

      postOffice.start();

      pagingManager.start();

      managementService.start();

      resourceManager.start();

      deploySecurityFromConfiguration();

      deployGroupingHandlerConfiguration(configuration.getGroupingHandlerConfiguration());

      long configurationFileRefreshPeriod = configuration.getConfigurationFileRefreshPeriod();
      if (configurationFileRefreshPeriod > 0) {
         this.reloadManager = new ReloadManagerImpl(getScheduledPool(), executorFactory.getExecutor(), configurationFileRefreshPeriod);

         final URL configUrl = configuration.getConfigurationUrl();
         ReloadCallback fullConfigReloadCallback = uri -> {
            // ignore the argument from the callback such that we can respond
            // to property file locations with a full reload
            reloadConfigurationFile(configUrl);
         };

         if (configUrl != null) {
            reloadManager.addCallback(configUrl, fullConfigReloadCallback);
         }

         // watch properties and reload config
         String propsLocations = configuration.resolvePropertiesSources(propertiesFileUrl);
         if (propsLocations != null) {
            for (String fileUrl : propsLocations.split(",")) {
               if (URISupport.containsQuery(fileUrl)) {
                  fileUrl = URISupport.stripQuery(fileUrl);
               }
               reloadManager.addCallback(new File(fileUrl).toURI().toURL(), fullConfigReloadCallback);
            }
         }

         // watch login.config dir for mods to trigger reload, otherwise reloadable properties
         // only appear in status on a relevant login attempt which may never happen
         File optionalJaasConfigBaseDir = PropertiesLoader.FileNameKey.parentDirOfLoginConfigSystemProperty();
         if (optionalJaasConfigBaseDir != null) {
            reloadManager.addCallback(optionalJaasConfigBaseDir.toURI().toURL(), (uri) -> {
               PropertiesLoader.reload();
            });
         }
      }

      if (hasBrokerPlugins()) {
         registerBrokerPlugins(getBrokerPlugins());
      }

      if (configuration.getMetricsConfiguration() != null && configuration.getMetricsConfiguration().getPlugin() != null) {
         configuration.getMetricsConfiguration().getPlugin().registered(this);
      }

      return true;
   }

   @Override
   public void installMirrorController(MirrorController mirrorController) {
      logger.debug("Mirror controller is being installed");
      if (postOffice != null) {
         postOffice.setMirrorControlSource(mirrorController);
      }
      this.mirrorControllerService = mirrorController;
   }


   @Override
   public void scanAddresses(MirrorController mirrorController) throws Exception {
      logger.debug("Scanning addresses to send on mirror controller");
      postOffice.scanAddresses(mirrorController);
   }

   @Override
   public MirrorController getMirrorController() {
      return this.mirrorControllerService;
   }

   @Override
   public void removeMirrorControl() {
      postOffice.setMirrorControlSource(null);
   }

   /**
    * Load the data, and start remoting service so clients can connect
    */
   synchronized void initialisePart2(boolean scalingDown) throws Exception {
      // Load the journal and populate queues, transactions and caches in memory

      if (state == SERVER_STATE.STOPPED || state == SERVER_STATE.STOPPING) {
         return;
      }

      loadProtocolServices();

      pagingManager.reloadStores();

      Set<Long> storedLargeMessages = new HashSet<>();
      loadJournals(storedLargeMessages);

      if (rebuildCounters) {
         pagingManager.rebuildCounters(storedLargeMessages);

         pagingManager.execute(() -> {
            storedLargeMessages.forEach(id -> {
               try {
                  SequentialFile file = storageManager.createFileForLargeMessage(id, true);
                  logger.debug("Removing pending large message for file={}", file);
                  file.delete();
               } catch (Exception e) {
                  // this shouldn't really happen, unless something is off with the storage
                  logger.warn("It was not possible to remove previously stored large message on folder::It will be retried on next startup", e);
               }
            });
         });
      }

      removeExtraAddressStores();

      if (securityManager instanceof ActiveMQBasicSecurityManager manager) {
         manager.completeInit(storageManager);
      }

      final ServerInfo dumper = new ServerInfo(this, pagingManager);

      long dumpInfoInterval = configuration.getServerDumpInterval();

      if (dumpInfoInterval > 0) {
         scheduledPool.scheduleWithFixedDelay(() -> ActiveMQServerLogger.LOGGER.dumpServerInfo(dumper.dump()), 0, dumpInfoInterval, TimeUnit.MILLISECONDS);
      }

      // Deploy the rest of the stuff

      // Deploy predefined addresses
      deployAddressesFromConfiguration();
      // Deploy any predefined queues
      deployQueuesFromConfiguration();
      // Undeploy any addresses and queues not in config
      undeployAddressesAndQueueNotInConfiguration();

      //deploy any reloaded config
      deployReloadableConfigFromConfiguration();

      // We need to call this here, this gives any dependent server a chance to deploy its own addresses
      // this needs to be done before clustering is fully activated
      callActivateCallbacks();

      checkForPotentialOOMEInAddressConfiguration();

      if (!scalingDown) {
         // Deploy any pre-defined diverts
         deployDiverts();

         if (groupingHandler != null) {
            groupingHandler.start();
         }

         // We do this at the end - we don't want things like MDBs or other connections connecting to a backup server until
         // it is activated

         if (groupingHandler != null && groupingHandler instanceof LocalGroupingHandler) {
            clusterManager.start();

            federationManager.start();

            groupingHandler.awaitBindings();

            remotingService.start();
         } else {
            remotingService.start();

            clusterManager.start();

            federationManager.start();
         }

         connectionRouterManager.start();

         startProtocolServices();

         if (nodeManager.getNodeId() == null) {
            throw ActiveMQMessageBundle.BUNDLE.nodeIdNull();
         }

         // We can only do this after everything is started otherwise we may get nasty races with expired messages
         postOffice.startExpiryScanner();

         postOffice.startAddressQueueScanner();

         recoverStoredConnectors();

         recoverStoredBridges();
      }

      deployFileStoreMonitor();
   }

   private void deployFileStoreMonitor() throws Exception {
      FileStoreMonitor.FileStoreMonitorType fileStoreMonitorType = null;
      Number referenceValue = null;
      if (configuration.getMinDiskFree() != -1) {
         if (configuration.getMaxDiskUsage() != -1) {
            ActiveMQServerLogger.LOGGER.configParamOverride(FileConfigurationParser.MIN_DISK_FREE, configuration.getMinDiskFree(), FileConfigurationParser.MAX_DISK_USAGE, configuration.getMaxDiskUsage());
         }
         fileStoreMonitorType = FileStoreMonitor.FileStoreMonitorType.MinDiskFree;
         referenceValue = configuration.getMinDiskFree();
      } else if (configuration.getMaxDiskUsage() != -1) {
         fileStoreMonitorType = FileStoreMonitor.FileStoreMonitorType.MaxDiskUsage;
         referenceValue = configuration.getMaxDiskUsage() / 100d;
      }

      if (fileStoreMonitorType != null) {
         injectMonitor(new FileStoreMonitor(getScheduledPool(), executorFactory.getExecutor(), configuration.getDiskScanPeriod(), TimeUnit.MILLISECONDS, referenceValue, ioCriticalErrorListener, fileStoreMonitorType));
      }
   }

   private void loadProtocolServices() {
      remotingService.loadProtocolServices(protocolServices);
   }

   private void startProtocolServices() throws Exception {
      for (ProtocolManagerFactory protocolManagerFactory : protocolManagerFactories) {
         protocolManagerFactory.loadProtocolServices(this, protocolServices);
      }

      for (ActiveMQComponent protocolComponent : protocolServices) {
         protocolComponent.start();
      }
   }

   private void updateProtocolServices() throws Exception {
      remotingService.updateProtocolServices(protocolServices);

      for (ProtocolManagerFactory protocolManagerFactory : protocolManagerFactories) {
         protocolManagerFactory.updateProtocolServices(this, protocolServices);
      }
   }

   /**
    * This method exists for a possibility of test cases replacing the FileStoreMonitor for an extension that would for
    * instance pretend a disk full on certain tests.
    */
   public void injectMonitor(FileStoreMonitor storeMonitor) throws Exception {
      try {
         this.fileStoreMonitor = storeMonitor;
         pagingManager.injectMonitor(storeMonitor);
         storageManager.injectMonitor(storeMonitor);
         fileStoreMonitor.start();
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.unableToInjectMonitor(e);
      }
   }

   public FileStoreMonitor getMonitor() {
      return fileStoreMonitor;
   }

   public void completeActivation(boolean replicated) throws Exception {
      setState(ActiveMQServerImpl.SERVER_STATE.STARTED);
      if (replicated) {
         if (getClusterManager() != null) {
            for (ClusterConnection clusterConnection : getClusterManager().getClusterConnections()) {
               // we need to avoid split brain on topology for replication
               clusterConnection.setSplitBrainDetection(true);
            }
         }
      }
      getRemotingService().startAcceptors();
      activationLatch.countDown();
      callActivationCompleteCallbacks();
   }

   @Override
   public double getDiskStoreUsage() {
      //this should not happen but if it does, return -1 to highlight it is not working
      if (getPagingManager() == null) {
         return -1L;
      }

      return FileStoreMonitor.calculateUsage(getPagingManager().getDiskUsableSpace(), getPagingManager().getDiskTotalSpace());
   }

   private void deploySecurityFromConfiguration() {
      for (Map.Entry<String, Set<Role>> entry : configuration.getSecurityRoles().entrySet()) {
         securityRepository.addMatch(entry.getKey(), entry.getValue(), true);
      }

      for (SecuritySettingPlugin securitySettingPlugin : configuration.getSecuritySettingPlugins()) {
         securitySettingPlugin.setSecurityRepository(securityRepository);
      }
   }

   private void undeployAddressesAndQueueNotInConfiguration() throws Exception {
      undeployAddressesAndQueueNotInConfiguration(configuration);
   }

   private void undeployAddressesAndQueueNotInConfiguration(Configuration configuration) throws Exception {
      Set<String> addressesInConfig = configuration.getAddressConfigurations().stream()
              .map(CoreAddressConfiguration::getName)
              .collect(Collectors.toSet());

      Map<SimpleString, List<QueueConfiguration>> queuesInConfig = configuration.getAddressConfigurations().stream()
              .map(CoreAddressConfiguration::getQueueConfigs)
              .flatMap(List::stream).collect(groupingBy(QueueConfiguration::getName));

      for (SimpleString addressName : listAddressNames()) {
         AddressInfo addressInfo = getAddressInfo(addressName);
         AddressSettings addressSettings = getAddressSettingsRepository().getMatch(addressName.toString());

         if (!addressesInConfig.contains(addressName.toString()) && addressInfo != null && !addressInfo.isAutoCreated() &&
            addressSettings.getConfigDeleteAddresses() == DeletionPolicy.FORCE) {
            for (Queue queue : listQueues(addressName)) {
               ActiveMQServerLogger.LOGGER.undeployQueue(queue.getName());
               try {
                  queue.deleteQueue(true);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.unableToUndeployQueue(addressName, e.getMessage());
               }
            }
            ActiveMQServerLogger.LOGGER.undeployAddress(addressName);
            try {
               removeAddressInfo(addressName, null);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.unableToUndeployAddress(addressName, e.getMessage());
            }
         } else if (addressSettings.getConfigDeleteQueues() == DeletionPolicy.FORCE) {
            for (Queue queue : listConfiguredQueues(addressName)) {
               List<QueueConfiguration> queueConfigsInConfig = queuesInConfig.get(queue.getName());
               if (queueConfigsInConfig == null || !queueConfigsInConfig.stream().anyMatch(
                  queueConfiguration -> queueConfiguration.getAddress().equals(addressName))) {
                  ActiveMQServerLogger.LOGGER.undeployQueue(queue.getName());
                  try {
                     queue.deleteQueue(true);
                  } catch (Exception e) {
                     ActiveMQServerLogger.LOGGER.unableToUndeployQueue(addressName, e.getMessage());
                  }
               }
            }
         }
      }
   }

   private Set<SimpleString> listAddressNames() {
      return postOffice.getAddresses();
   }

   private List<Queue> listConfiguredQueues(SimpleString address) throws Exception {
      return listQueues(address).stream().filter(queue -> queue.isConfigurationManaged()).collect(Collectors.toList());
   }

   private List<Queue> listQueues(SimpleString address) throws Exception {
      return postOffice.listQueuesForAddress(address);
   }

   private void deployAddressesFromConfiguration() throws Exception {
      deployAddressesFromConfiguration(configuration);
   }

   private void deployAddressesFromConfiguration(Configuration configuration) throws Exception {
      for (CoreAddressConfiguration config : configuration.getAddressConfigurations()) {
         try {
            ActiveMQServerLogger.LOGGER.deployAddress(config.getName(), config.getRoutingTypes().toString());
            SimpleString address = SimpleString.of(config.getName());

            AddressInfo tobe = new AddressInfo(address, config.getRoutingTypes());

            //During this stage until all queues re-configured we combine the current (if exists) with to-be routing types to allow changes in queues
            AddressInfo current = getAddressInfo(address);
            AddressInfo merged = new AddressInfo(address, tobe.getRoutingType());
            if (current != null) {
               merged.getRoutingTypes().addAll(current.getRoutingTypes());
            }
            addOrUpdateAddressInfo(merged);

            deployQueuesFromListQueueConfiguration(config.getQueueConfigs(), address);

            //Now all queues updated we apply the actual address info expected tobe.
            addOrUpdateAddressInfo(tobe);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.problemDeployingAddress(config.getName(), e.getMessage());
         }
      }
   }

   private AddressInfo mergedRoutingTypes(SimpleString address, AddressInfo... addressInfos) {
      EnumSet<RoutingType> mergedRoutingTypes = EnumSet.noneOf(RoutingType.class);
      for (AddressInfo addressInfo : addressInfos) {
         if (addressInfo != null) {
            mergedRoutingTypes.addAll(addressInfo.getRoutingTypes());
         }
      }
      return new AddressInfo(address, mergedRoutingTypes);
   }

   private void deployQueuesFromListQueueConfiguration(List<QueueConfiguration> queues, SimpleString address) throws Exception {
      for (QueueConfiguration config : queues) {
         // set the address for queue configs from broker properties
         if (address != null && config.isAddressNull()) {
            config.setAddress(address);
         }

         try {
            QueueConfigurationUtils.applyDynamicDefaults(config, addressSettingsRepository.getMatch(config.getAddress().toString()));

            config.setAutoCreateAddress(true);

            ActiveMQServerLogger.LOGGER.deployQueue(config.getRoutingType().toString(), config.getName().toString(), config.getAddress().toString());

            // determine if there is an address::queue match; update it if so
            if (locateQueue(config.getName()) != null && locateQueue(config.getName()).getAddress().equals(config.getAddress())) {
               config.setConfigurationManaged(true);
               QueueConfigurationUtils.applyStaticDefaults(config);
               updateQueue(config, true);
            } else {
               // if the address::queue doesn't exist then create it
               try {
                  // handful of hard-coded config values for the static configuration use-case
                  createQueue(config.setTemporary(false).setTransient(false).setAutoCreated(false).setConfigurationManaged(true).setAutoCreateAddress(true), false);
               } catch (ActiveMQQueueExistsException e) {
                  // the queue may exist on a *different* address
                  logger.warn(e.getMessage(), e);
               }
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.problemDeployingQueue(config.getName().toString(), e.getMessage());
         }
      }
   }

   private void deployQueuesFromConfiguration() throws Exception {
      deployQueuesFromListQueueConfiguration(configuration.getQueueConfigs(), null);
   }

   private void checkForPotentialOOMEInAddressConfiguration() {
      long totalMaxSizeBytes = 0;
      long addressCount = 0;
      for (SimpleString address : postOffice.getAddresses()) {
         totalMaxSizeBytes += addressSettingsRepository.getMatch(address.toString()).getMaxSizeBytes();
         addressCount++;
      }

      long maxMemory = Runtime.getRuntime().maxMemory();
      if (totalMaxSizeBytes >= maxMemory && configuration.getGlobalMaxSize() < 0) {
         ActiveMQServerLogger.LOGGER.potentialOOME(addressCount, totalMaxSizeBytes, maxMemory);
      }
   }

   private void deployAddressSettingsFromConfiguration() {
      for (Map.Entry<String, AddressSettings> entry : configuration.getAddressSettings().entrySet()) {
         addressSettingsRepository.addMatch(entry.getKey(), entry.getValue(), true);
      }
   }



   private JournalLoadInformation[] loadJournals(Set<Long> storedLargeMessages) throws Exception {
      JournalLoader journalLoader = activation.createJournalLoader(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration, parentServer);

      JournalLoadInformation[] journalInfo = new JournalLoadInformation[2];

      List<QueueBindingInfo> queueBindingInfos = new ArrayList<>();

      List<GroupingInfo> groupingInfos = new ArrayList<>();

      List<AddressBindingInfo> addressBindingInfos = new ArrayList<>();

      journalInfo[0] = storageManager.loadBindingJournal(queueBindingInfos, groupingInfos, addressBindingInfos);

      recoverStoredConfigs();

      journalLoader.initAddresses(addressBindingInfos);

      Map<Long, QueueBindingInfo> queueBindingInfosMap = new HashMap<>();

      journalLoader.initQueues(queueBindingInfosMap, queueBindingInfos);

      journalLoader.handleGroupingBindings(groupingInfos);

      Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap = new HashMap<>();

      Set<Pair<Long, Long>> pendingLargeMessages = new HashSet<>();

      List<PageCountPending> pendingNonTXPageCounter = new LinkedList<>();

      storageManager.recoverLargeMessagesOnFolder(storedLargeMessages);

      journalInfo[1] = storageManager.loadMessageJournal(postOffice, pagingManager, resourceManager, queueBindingInfosMap, duplicateIDMap, pendingLargeMessages, storedLargeMessages, pendingNonTXPageCounter, journalLoader, extraRecordsLoader);

      journalLoader.handleDuplicateIds(duplicateIDMap);

      // this deals with legacy pending large message records that might be left in a journal after an upgrade
      for (Pair<Long, Long> msgToDelete : pendingLargeMessages) {
         logger.debug("Removing pending large message record: {}", msgToDelete.getA());
         storageManager.deletePendingLargeMessage(msgToDelete.getA());
      }

      if (pendingNonTXPageCounter.size() != 0) {
         try {
            journalLoader.recoverPendingPageCounters(pendingNonTXPageCounter);
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorRecoveringPageCounter(e);
         }
      }

      journalLoader.cleanUp();

      return journalInfo;
   }

   private void recoverStoredConfigs() throws Exception {
      recoverStoredAddressSettings();
      recoverStoredSecuritySettings();
   }

   private void recoverStoredSecuritySettings() throws Exception {
      List<PersistedSecuritySetting> roles = storageManager.recoverSecuritySettings();
      for (PersistedSecuritySetting roleItem : roles) {
         securityRepository.addMatch(roleItem.getAddressMatch().toString(),
                                     SecurityFormatter.createSecurity(roleItem.getSendRoles(),
                                                                      roleItem.getConsumeRoles(),
                                                                      roleItem.getCreateDurableQueueRoles(),
                                                                      roleItem.getDeleteDurableQueueRoles(),
                                                                      roleItem.getCreateNonDurableQueueRoles(),
                                                                      roleItem.getDeleteNonDurableQueueRoles(),
                                                                      roleItem.getManageRoles(),
                                                                      roleItem.getBrowseRoles(),
                                                                      roleItem.getCreateAddressRoles(),
                                                                      roleItem.getDeleteAddressRoles()));
      }
   }

   private void recoverStoredAddressSettings() throws Exception {
      List<AbstractPersistedAddressSetting> adsettings = storageManager.recoverAddressSettings();
      for (AbstractPersistedAddressSetting set : adsettings) {
         addressSettingsRepository.addMatch(set.getAddressMatch().toString(), set.getSetting());
      }
   }

   @Override
   public boolean updateAddressInfo(SimpleString address, EnumSet<RoutingType> routingTypes) throws Exception {
      if (getAddressInfo(address) == null) {
         return false;
      }

      //after the postOffice call, updatedAddressInfo could change further (concurrently)!
      postOffice.updateAddressInfo(address, routingTypes);
      return true;
   }

   @Override
   public boolean updateAddressInfo(SimpleString address, Collection<RoutingType> routingTypes) throws Exception {
      return updateAddressInfo(address, EnumSet.copyOf(routingTypes));
   }

   @Override
   public boolean addAddressInfo(AddressInfo addressInfo) throws Exception {
      boolean result = postOffice.addAddressInfo(addressInfo);


      return result;
   }

   @Override
   public AddressInfo addOrUpdateAddressInfo(AddressInfo addressInfo) throws Exception {
      if (!addAddressInfo(addressInfo)) {
         updateAddressInfo(addressInfo.getName(), addressInfo.getRoutingTypes());
      }

      return getAddressInfo(addressInfo.getName());
   }


   @Override
   public void removeAddressInfo(final SimpleString address, final SecurityAuth auth) throws Exception {
      removeAddressInfo(address, auth, false);
   }

   @Override
   public void autoRemoveAddressInfo(SimpleString address, SecurityAuth auth) throws Exception {
      logger.debug("deleting auto-created address \"{}.\"", address);

      ActiveMQServerLogger.LOGGER.autoRemoveAddress(String.valueOf(address));

      removeAddressInfo(address, auth);
   }

   /**
    * Register a queue on the management registry
    */
   @Override
   public void registerQueueOnManagement(Queue queue) throws Exception {
      managementService.registerQueue(queue, queue.getAddress(), storageManager);
   }

   @Override
   public void removeAddressInfo(final SimpleString address, final SecurityAuth auth, boolean force) throws Exception {
      if (auth != null) {
         securityStore.check(address, CheckType.DELETE_ADDRESS, auth);
      }

      try {
         AddressInfo addressInfo = postOffice.removeAddressInfo(address, force);
         if (addressInfo == null) {
            throw ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(address);
         }

         if (addressInfo.getRepositoryChangeListener() != null) {
            addressSettingsRepository.unRegisterListener(addressInfo.getRepositoryChangeListener());
            addressInfo.setRepositoryChangeListener(null);
         }

         long txID = storageManager.generateID();
         storageManager.deleteAddressBinding(txID, addressInfo.getId());
         storageManager.commitBindings(txID);
         pagingManager.deletePageStore(address);
      } finally {
         clearAddressCache();
      }
   }

   @Override
   public String getInternalNamingPrefix() {
      return configuration.getInternalNamingPrefix();
   }

   @Override
   public AddressInfo getAddressInfo(SimpleString address) {
      return postOffice.getAddressInfo(address);
   }

   @Deprecated
   public Queue createQueue(final AddressInfo addrInfo,
                            final SimpleString queueName,
                            final SimpleString filterString,
                            final SimpleString user,
                            final boolean durable,
                            final boolean temporary,
                            final boolean ignoreIfExists,
                            final boolean transientQueue,
                            final boolean autoCreated,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final SimpleString groupFirstKey,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress,
                            final boolean configurationManaged,
                            final long ringSize) throws Exception {
      return createQueue(QueueConfiguration.of(queueName)
                            .setAddress(addrInfo == null ? null : addrInfo.getName())
                            .setRoutingType(addrInfo == null ? null : addrInfo.getRoutingType())
                            .setFilterString(filterString)
                            .setUser(user)
                            .setDurable(durable)
                            .setTemporary(temporary)
                            .setTransient(transientQueue)
                            .setAutoCreated(autoCreated)
                            .setMaxConsumers(maxConsumers)
                            .setPurgeOnNoConsumers(purgeOnNoConsumers)
                            .setExclusive(exclusive)
                            .setGroupRebalance(groupRebalance)
                            .setGroupBuckets(groupBuckets)
                            .setGroupFirstKey(groupFirstKey)
                            .setLastValue(lastValue)
                            .setLastValueKey(lastValueKey)
                            .setNonDestructive(nonDestructive)
                            .setConsumersBeforeDispatch(consumersBeforeDispatch)
                            .setDelayBeforeDispatch(delayBeforeDispatch)
                            .setAutoDelete(autoDelete)
                            .setAutoDeleteDelay(autoDeleteDelay)
                            .setAutoDeleteMessageCount(autoDeleteMessageCount)
                            .setAutoCreateAddress(autoCreateAddress)
                            .setConfigurationManaged(configurationManaged)
                            .setRingSize(ringSize),
                         ignoreIfExists);
   }

   @Override
   public Queue createQueue(final QueueConfiguration queueConfiguration) throws Exception {
      return createQueue(queueConfiguration, false);
   }

   @Override
   public Queue createQueue(final QueueConfiguration queueConfiguration, boolean ignoreIfExists) throws Exception {
      final PostOffice postOfficeInUse = postOffice;
      if (postOfficeInUse == null) {
         return null;
      }
      synchronized (postOfficeInUse) {
         if (queueConfiguration.getName() == null || queueConfiguration.getName().isEmpty()) {
            throw ActiveMQMessageBundle.BUNDLE.invalidQueueName(queueConfiguration.getName());
         }

         final Binding rawBinding = postOfficeInUse.getBinding(queueConfiguration.getName());
         if (rawBinding != null) {
            if (rawBinding.getType() != BindingType.LOCAL_QUEUE) {
               throw ActiveMQMessageBundle.BUNDLE.bindingAlreadyExists(queueConfiguration.getName().toString(), rawBinding.toManagementString());
            }
            final QueueBinding queueBinding = (QueueBinding) rawBinding;
            if (ignoreIfExists) {
               //Reset potentially ongoing auto-delete status of queue
               queueBinding.getQueue().setSwept(false);

               return queueBinding.getQueue();
            } else {
               throw ActiveMQMessageBundle.BUNDLE.queueAlreadyExists(queueConfiguration.getName(), queueBinding.getAddress());
            }
         }

         QueueConfigurationUtils.applyDynamicDefaults(queueConfiguration, addressSettingsRepository.getMatch(queueConfiguration.getAddress().toString()));

         AddressInfo info = postOfficeInUse.getAddressInfo(queueConfiguration.getAddress());
         if (queueConfiguration.isAutoCreateAddress() || queueConfiguration.isTemporary()) {
            if (info == null) {
               addAddressInfo(new AddressInfo(queueConfiguration.getAddress(), queueConfiguration.getRoutingType()).setAutoCreated(true).setTemporary(queueConfiguration.isTemporary()).setInternal(queueConfiguration.isInternal()));
            } else if (!info.getRoutingTypes().contains(queueConfiguration.getRoutingType())) {
               EnumSet<RoutingType> routingTypes = EnumSet.copyOf(info.getRoutingTypes());
               routingTypes.add(queueConfiguration.getRoutingType());
               updateAddressInfo(info.getName(), routingTypes);
            }
         } else if (info == null) {
            throw ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(queueConfiguration.getAddress());
         } else if (!info.getRoutingTypes().contains(queueConfiguration.getRoutingType())) {
            throw ActiveMQMessageBundle.BUNDLE.invalidRoutingTypeForAddress(queueConfiguration.getRoutingType(), info.getName().toString(), info.getRoutingTypes());
         }

         if (hasBrokerQueuePlugins()) {
            callBrokerQueuePlugins(plugin -> plugin.beforeCreateQueue(queueConfiguration));
         }

         if (mirrorControllerService != null && !queueConfiguration.isInternal()) {
            mirrorControllerService.createQueue(queueConfiguration);
         }

         queueConfiguration.setId(storageManager.generateID());

         // preemptive check to ensure the filterString is good
         Filter filter = FilterImpl.createFilter(queueConfiguration.getFilterString());

         final Queue queue = queueFactory.createQueueWith(queueConfiguration, pagingManager, filter);

         final QueueBinding localQueueBinding = new LocalQueueBinding(queue.getAddress(), queue, nodeManager.getNodeId());

         long txID = 0;
         if (queue.isDurable()) {
            txID = storageManager.generateID();
            storageManager.addQueueBinding(txID, localQueueBinding);
         }

         try {
            postOfficeInUse.addBinding(localQueueBinding);
            if (queue.isDurable()) {
               storageManager.commitBindings(txID);
            }
         } catch (Exception e) {
            try {
               if (queueConfiguration.isDurable()) {
                  storageManager.rollbackBindings(txID);
               }
               try {
                  queue.close();
               } finally {
                  if (queue.getPageSubscription() != null) {
                     queue.getPageSubscription().destroy();
                  }
               }
            } catch (Throwable ignored) {
               logger.debug(ignored.getMessage(), ignored);
            }
            throw e;
         }

         managementService.registerQueue(queue, queue.getAddress(), storageManager);

         copyRetroactiveMessages(queue);

         if (hasBrokerQueuePlugins()) {
            callBrokerQueuePlugins(plugin -> plugin.afterCreateQueue(queue));
         }

         callPostQueueCreationCallbacks(queue.getName());

         return queue;
      }
   }

   private void copyRetroactiveMessages(Queue targetQueue) throws Exception {
      if (addressSettingsRepository.getMatch(targetQueue.getAddress().toString()).getRetroactiveMessageCount() > 0) {
         Queue retroQueue = locateQueue(ResourceNames.getRetroactiveResourceQueueName(getInternalNamingPrefix(), getConfiguration().getWildcardConfiguration().getDelimiterString(), targetQueue.getAddress(), targetQueue.getRoutingType()));
         if (retroQueue != null && retroQueue instanceof QueueImpl queue) {
            queue.rerouteMessages(targetQueue.getName(), targetQueue.getFilter());
         }
      }
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filterString,
                            final SimpleString user,
                            final boolean durable,
                            final boolean temporary,
                            final boolean ignoreIfExists,
                            final boolean transientQueue,
                            final boolean autoCreated,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString());
      return createQueue(new AddressInfo(address).addRoutingType(routingType), queueName, filterString, user, durable, temporary, ignoreIfExists, transientQueue, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, as.getDefaultGroupFirstKey(), lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, false, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filterString,
                            final SimpleString user,
                            final boolean durable,
                            final boolean temporary,
                            final boolean ignoreIfExists,
                            final boolean transientQueue,
                            final boolean autoCreated,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final SimpleString groupFirstKey,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress) throws Exception {
      AddressSettings as = getAddressSettingsRepository().getMatch(address == null ? queueName.toString() : address.toString());
      return createQueue(new AddressInfo(address).addRoutingType(routingType), queueName, filterString, user, durable, temporary, ignoreIfExists, transientQueue, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, false, as.getDefaultRingSize());
   }

   @Deprecated
   @Override
   public Queue createQueue(final SimpleString address,
                            final RoutingType routingType,
                            final SimpleString queueName,
                            final SimpleString filterString,
                            final SimpleString user,
                            final boolean durable,
                            final boolean temporary,
                            final boolean ignoreIfExists,
                            final boolean transientQueue,
                            final boolean autoCreated,
                            final int maxConsumers,
                            final boolean purgeOnNoConsumers,
                            final boolean exclusive,
                            final boolean groupRebalance,
                            final int groupBuckets,
                            final SimpleString groupFirstKey,
                            final boolean lastValue,
                            final SimpleString lastValueKey,
                            final boolean nonDestructive,
                            final int consumersBeforeDispatch,
                            final long delayBeforeDispatch,
                            final boolean autoDelete,
                            final long autoDeleteDelay,
                            final long autoDeleteMessageCount,
                            final boolean autoCreateAddress,
                            final long ringSize) throws Exception {
      return createQueue(new AddressInfo(address).addRoutingType(routingType), queueName, filterString, user, durable, temporary, ignoreIfExists, transientQueue, autoCreated, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, false, ringSize);
   }

   @Deprecated
   @Override
   public Queue updateQueue(String name,
                            RoutingType routingType,
                            Integer maxConsumers,
                            Boolean purgeOnNoConsumers) throws Exception {
      return updateQueue(name, routingType, maxConsumers, purgeOnNoConsumers, null);
   }

   @Deprecated
   @Override
   public Queue updateQueue(String name,
                            RoutingType routingType,
                            Integer maxConsumers,
                            Boolean purgeOnNoConsumers,
                            Boolean exclusive) throws Exception {
      return updateQueue(name, routingType, maxConsumers, purgeOnNoConsumers, null, null);
   }

   @Deprecated
   @Override
   public Queue updateQueue(String name,
                            RoutingType routingType,
                            Integer maxConsumers,
                            Boolean purgeOnNoConsumers,
                            Boolean exclusive,
                            String user) throws Exception {
      return updateQueue(name, routingType, null, maxConsumers, purgeOnNoConsumers, exclusive, null, null, null, null, null, user);
   }

   @Deprecated
   @Override
   public Queue updateQueue(String name,
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
                            String user) throws Exception {
      return updateQueue(name, routingType, filterString, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, null, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, null);
   }

   @Deprecated
   @Override
   public Queue updateQueue(String name,
                            RoutingType routingType,
                            String filterString,
                            Integer maxConsumers,
                            Boolean purgeOnNoConsumers,
                            Boolean exclusive,
                            Boolean groupRebalance,
                            Integer groupBuckets,
                            String groupFirstKey,
                            Boolean nonDestructive,
                            Integer consumersBeforeDispatch,
                            Long delayBeforeDispatch,
                            String user) throws Exception {
      return updateQueue(name, routingType, filterString, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, user, null);
   }

   @Deprecated
   @Override
   public Queue updateQueue(String name,
                            RoutingType routingType,
                            String filterString,
                            Integer maxConsumers,
                            Boolean purgeOnNoConsumers,
                            Boolean exclusive,
                            Boolean groupRebalance,
                            Integer groupBuckets,
                            String groupFirstKey,
                            Boolean nonDestructive,
                            Integer consumersBeforeDispatch,
                            Long delayBeforeDispatch,
                            String user,
                            Long ringSize) throws Exception {
      return updateQueue(QueueConfiguration.of(name)
                            .setRoutingType(routingType)
                            .setFilterString(filterString)
                            .setMaxConsumers(maxConsumers)
                            .setPurgeOnNoConsumers(purgeOnNoConsumers)
                            .setExclusive(exclusive)
                            .setGroupRebalance(groupRebalance)
                            .setGroupBuckets(groupBuckets)
                            .setGroupFirstKey(groupFirstKey)
                            .setNonDestructive(nonDestructive)
                            .setConsumersBeforeDispatch(consumersBeforeDispatch)
                            .setDelayBeforeDispatch(delayBeforeDispatch)
                            .setUser(user)
                            .setRingSize(ringSize));
   }

   @Override
   public Queue updateQueue(QueueConfiguration queueConfiguration) throws Exception {
      return updateQueue(queueConfiguration, false);
   }

   @Override
   public Queue updateQueue(QueueConfiguration queueConfiguration, boolean forceUpdate) throws Exception {
      final QueueBinding queueBinding = this.postOffice.updateQueue(queueConfiguration, forceUpdate);
      if (queueBinding != null) {
         return queueBinding.getQueue();
      } else {
         return null;
      }
   }

   private void deployDiverts() throws Exception {
      recoverStoredDiverts();
      //deploy the configured diverts
      for (DivertConfiguration config : configuration.getDivertConfigurations()) {
         deployDivert(config);
      }
   }

   private void recoverStoredDiverts() throws Exception {
      if (storageManager.recoverDivertConfigurations() != null) {

         for (PersistedDivertConfiguration persistedDivertConfiguration : storageManager.recoverDivertConfigurations()) {
            //has it been removed from config
            boolean deleted = configuration.getDivertConfigurations().stream().noneMatch(divertConfiguration -> divertConfiguration.getName().equals(persistedDivertConfiguration.getName()));
            // if it has remove it if configured to do so
            if (deleted) {
               if (addressSettingsRepository.getMatch(persistedDivertConfiguration.getDivertConfiguration().getAddress()).getConfigDeleteDiverts() == DeletionPolicy.FORCE) {
                  storageManager.deleteDivertConfiguration(persistedDivertConfiguration.getName());
               } else {
                  deployDivert(persistedDivertConfiguration.getDivertConfiguration());
               }
            }
         }
      }
   }

   private void recoverStoredBridges() throws Exception {
      if (storageManager.recoverBridgeConfigurations() != null) {
         for (PersistedBridgeConfiguration persistedBridgeConfiguration : storageManager.recoverBridgeConfigurations()) {

            boolean bridgeMissing = clusterManager.getBridges().values().stream()
               .noneMatch(bridge -> bridge.getConfiguration().getParentName()
                  .equals(persistedBridgeConfiguration.getName()));

            if (bridgeMissing) {
               deployBridge(persistedBridgeConfiguration.getBridgeConfiguration());
            }
         }
      }
   }

   private void recoverStoredConnectors() throws Exception {
      if (storageManager.recoverConnectors() != null) {
         for (PersistedConnector persistedConnector : storageManager.recoverConnectors()) {
            getConfiguration().addConnectorConfiguration(persistedConnector.getName(), persistedConnector.getUrl());
         }
      }
   }

   private void deployGroupingHandlerConfiguration(final GroupingHandlerConfiguration config) throws Exception {
      if (config != null) {
         GroupingHandler groupingHandler1;
         if (config.getType() == GroupingHandlerConfiguration.TYPE.LOCAL) {
            groupingHandler1 = new LocalGroupingHandler(executorFactory, scheduledPool, managementService, config.getName(), config.getAddress(), getStorageManager(), config.getTimeout(), config.getGroupTimeout(), config.getReaperPeriod());
         } else {
            groupingHandler1 = new RemoteGroupingHandler(executorFactory, managementService, config.getName(), config.getAddress(), config.getTimeout(), config.getGroupTimeout());
         }

         this.groupingHandler = groupingHandler1;

         managementService.addNotificationListener(groupingHandler1);
      }
   }

   /**
    * Check if journal directory exists or create it (if configured to do so)
    */
   public void checkJournalDirectory() {
      if (!configuration.isUsingDatabasePersistence()) {
         File journalDir = configuration.getJournalLocation();

         if (!journalDir.exists() && configuration.isPersistenceEnabled()) {
            if (configuration.isCreateJournalDir()) {
               journalDir.mkdirs();
            } else {
               throw ActiveMQMessageBundle.BUNDLE.cannotCreateDir(journalDir.getAbsolutePath());
            }
         }

         File nodeManagerLockDir = configuration.getNodeManagerLockLocation();
         if (!journalDir.equals(nodeManagerLockDir)) {
            if (configuration.isPersistenceEnabled() && !nodeManagerLockDir.exists()) {
               nodeManagerLockDir.mkdirs();
            }
         }
      }
   }

   public final class DefaultCriticalErrorListener implements IOCriticalErrorListener {

      private final AtomicBoolean failedAlready = new AtomicBoolean();

      @Override
      public boolean isPreviouslyFailed() {
         return failedAlready.get();
      }

      @Override
      public synchronized void onIOException(Throwable cause, String message, String file) {
         if (logger.isTraceEnabled()) {
            // the purpose of this is to find where the critical error is being called at
            // useful for when debugging where the critical error is being called at
            logger.trace("Throwing critical error {}", cause.getMessage(), new Exception("trace"));
         }
         if (!failedAlready.compareAndSet(false, true)) {
            return;
         }

         try {
            for (IOCriticalErrorListener listener : ioCriticalErrorListeners) {
               listener.onIOException(cause, message, file);
            }
         } catch (Throwable ignored) {
            logger.debug("Ignored exception {}", ignored.getMessage(), ignored);
         }

         if (file == null) {
            ActiveMQServerLogger.LOGGER.ioCriticalIOError(message, "NULL", cause);
         } else {
            ActiveMQServerLogger.LOGGER.ioCriticalIOError(message, file, cause);
         }

         stopTheServer(true);
      }
   }

   @Override
   public void addProtocolManagerFactory(ProtocolManagerFactory factory) {
      protocolManagerFactories.add(factory);
   }

   @Override
   public void removeProtocolManagerFactory(ProtocolManagerFactory factory) {
      protocolManagerFactories.remove(factory);
   }

   @Override
   public ActiveMQServer createBackupServer(Configuration configuration) {
      return new ActiveMQServerImpl(configuration, null, securityManager, this);
   }

   @Override
   public void addScaledDownNode(SimpleString scaledDownNodeId) {
      synchronized (scaledDownNodeIDs) {
         scaledDownNodeIDs.add(scaledDownNodeId);
         if (scaledDownNodeIDs.size() > 10) {
            scaledDownNodeIDs.remove(10);
         }
      }
   }

   @Override
   public boolean hasScaledDown(SimpleString scaledDownNodeId) {
      return scaledDownNodeIDs.contains(scaledDownNodeId);
   }

   /**
    * Move data away before starting data synchronization for fail-back.
    * <p>
    * Use case is a server, upon restarting, finding a former backup running in its place. It will
    * move any older data away and log a warning about it.
    */
   void moveServerData(int maxSavedReplicated) throws IOException {
      moveServerData(maxSavedReplicated, false);
   }

   void moveServerData(int maxSavedReplicated, boolean preserveLockFiles) throws IOException {
      File[] dataDirs = new File[]{configuration.getBindingsLocation(), configuration.getJournalLocation(), configuration.getPagingLocation(), configuration.getLargeMessagesLocation()};

      for (File data : dataDirs) {
         final boolean isLockFolder = preserveLockFiles ? data.equals(configuration.getNodeManagerLockLocation()) : false;
         final String[] lockPrefixes = isLockFolder ? new String[]{FileBasedNodeManager.SERVER_LOCK_NAME, "serverlock"} : null;
         FileMoveManager moveManager = new FileMoveManager(data, maxSavedReplicated, lockPrefixes);
         moveManager.doMove();
      }
   }

   @Override
   public String getUptime() {
      long delta = getUptimeMillis();

      if (delta == 0) {
         return "not started";
      }

      return TimeUtils.printDuration(delta);
   }

   @Override
   public long getUptimeMillis() {
      if (startDate == null) {
         return 0;
      }

      return new Date().getTime() - startDate.getTime();
   }

   @Override
   public boolean addClientConnection(String clientId, boolean unique) {
      final AtomicInteger i = connectedClientIds.putIfAbsent(clientId, new AtomicInteger(1));
      if (i != null) {
         if (unique && i.get() != 0) {
            return false;
         } else if (i.incrementAndGet() > 0) {
            connectedClientIds.put(clientId, i);
         }
      }
      return true;
   }

   @Override
   public void removeClientConnection(String clientId) {
      AtomicInteger i = connectedClientIds.get(clientId);
      if (i != null && i.decrementAndGet() == 0) {
         connectedClientIds.remove(clientId);
      }
   }

   private void removeExtraAddressStores() throws Exception {
      SimpleString[] storeNames = pagingManager.getStoreNames();
      if (storeNames != null && storeNames.length > 0) {
         for (SimpleString storeName : storeNames) {
            if (getAddressInfo(storeName) == null) {
               pagingManager.deletePageStore(storeName);
            }
         }
      }
   }

   private final class ActivationThread extends Thread {

      final Runnable runnable;

      ActivationThread(Runnable runnable, String name) {
         super(name);
         this.runnable = runnable;
      }

      @Override
      public void run() {
         lockActivation();
         try {
            if (state != SERVER_STATE.STOPPED && state != SERVER_STATE.STOPPING) {
               runnable.run();
            }
         } finally {
            unlockActivation();
         }
      }

   }

   @Override
   public void reloadConfigurationFile() throws Exception {
      reloadConfigurationFile(configuration.getConfigurationUrl());
   }

   private void reloadConfigurationFile(URL xmlConfigUri) throws Exception {
      if (xmlConfigUri != null) {
         Configuration config = new FileConfigurationParser().parseMainConfig(xmlConfigUri.openStream());
         LegacyJMSConfiguration legacyJMSConfiguration = new LegacyJMSConfiguration(config);
         legacyJMSConfiguration.parseConfiguration(xmlConfigUri.openStream());
         configuration.setSecurityRoles(config.getSecurityRoles());
         configuration.setAddressSettings(config.getAddressSettings());
         configuration.setDivertConfigurations(config.getDivertConfigurations());
         configuration.setAddressConfigurations(config.getAddressConfigurations());
         configuration.setQueueConfigs(config.getQueueConfigs());
         configuration.setBridgeConfigurations(config.getBridgeConfigurations());
         configuration.setConnectorConfigurations(config.getConnectorConfigurations());
         configuration.setAcceptorConfigurations(config.getAcceptorConfigurations());
         configuration.setAMQPConnectionConfigurations(config.getAMQPConnection());
         configuration.setPurgePageFolders(config.isPurgePageFolders());
      }
      configuration.parseProperties(propertiesFileUrl);
      updateStatus(ServerStatus.CONFIGURATION_COMPONENT, configuration.getStatus());
      configurationReloadDeployed.set(false);
      if (isActive()) {
         deployReloadableConfigFromConfiguration();
      }
   }

   private void deployReloadableConfigFromConfiguration() throws Exception {
      if (configurationReloadDeployed.compareAndSet(false, true)) {
         ActiveMQServerLogger.LOGGER.reloadingConfiguration("security settings");
         securityRepository.swap(configuration.getSecurityRoles().entrySet());
         recoverStoredSecuritySettings();

         ActiveMQServerLogger.LOGGER.reloadingConfiguration("address settings");
         addressSettingsRepository.swap(configuration.getAddressSettings().entrySet());
         recoverStoredAddressSettings();

         if (postOffice.isStarted()) {
            ActiveMQServerLogger.LOGGER.reloadingConfiguration("diverts");

            // Filter out all active diverts
            final Set<SimpleString> divertsToRemove = postOffice.getAllBindings()
                  .filter(binding -> binding instanceof DivertBinding)
                  .map(Binding::getUniqueName)
                  .collect(Collectors.toSet());
            // Go through the currently configured diverts
            for (DivertConfiguration divertConfig : configuration.getDivertConfigurations()) {
               // Retain diverts still configured to exist
               divertsToRemove.remove(SimpleString.of(divertConfig.getName()));
               // Deploy newly added diverts, reconfigure existing
               final SimpleString divertName = SimpleString.of(divertConfig.getName());
               final DivertBinding divertBinding = (DivertBinding) postOffice.getBinding(divertName);
               if (divertBinding == null) {
                  deployDivert(divertConfig);
               } else {
                  if ((divertBinding.isExclusive() != divertConfig.isExclusive()) ||
                        !divertBinding.getAddress().toString().equals(divertConfig.getAddress())) {
                     // Diverts whose exclusivity or address has changed have to be redeployed.
                     // See the Divert interface and look for setters. Absent setter is a hint that maybe that property is immutable.
                     destroyDivert(divertName);
                     deployDivert(divertConfig);
                  } else {
                     // Diverts with their exclusivity and address unchanged can be updated directly.
                     updateDivert(divertConfig);
                  }
               }
            }
            // Remove all remaining diverts
            for (final SimpleString divertName : divertsToRemove) {
               try {
                  destroyDivert(divertName);
               } catch (Throwable e) {
                  logger.warn("Divert {} could not be removed", divertName, e);
               }
            }
            recoverStoredDiverts();
         }

         ActiveMQServerLogger.LOGGER.reloadingConfiguration("addresses");
         undeployAddressesAndQueueNotInConfiguration(configuration);
         deployAddressesFromConfiguration(configuration);
         deployQueuesFromListQueueConfiguration(configuration.getQueueConfigs(), null);

         if (clusterManager.isStarted()) {
            ActiveMQServerLogger.LOGGER.reloadingConfiguration("bridges");

            for (BridgeConfiguration newBridgeConfig : configuration.getBridgeConfigurations()) {

               String bridgeName = newBridgeConfig.getName();
               newBridgeConfig.setParentName(bridgeName);

               //Look for bridges with matching parentName. Only need first match in case of concurrent bridges
               Bridge existingBridge = clusterManager.getBridges().values().stream()
                     .filter(bridge -> bridge.getConfiguration().getParentName().equals(bridgeName))
                     .findFirst()
                     .orElse(null);

               if (existingBridge != null && existingBridge.getConfiguration().isConfigurationManaged() && !existingBridge.getConfiguration().equals(newBridgeConfig)) {
                  // this is an existing bridge but the config changed so stop the current bridge and deploy the new one
                  destroyBridge(bridgeName);
                  deployBridge(newBridgeConfig);
               } else if (existingBridge == null) {
                  // this is a new bridge
                  deployBridge(newBridgeConfig);
               }
            }

            //Look for already running bridges no longer in configuration, stop if found
            for (final Bridge existingBridge : clusterManager.getBridges().values()) {
               BridgeConfiguration existingBridgeConfig = existingBridge.getConfiguration();

               if (existingBridgeConfig.isConfigurationManaged()) {
                  String existingBridgeName = existingBridgeConfig.getParentName();

                  boolean noLongerConfigured = configuration.getBridgeConfigurations().stream()
                        .noneMatch(bridge -> bridge.getParentName().equals(existingBridgeName));

                  if (noLongerConfigured) {
                     destroyBridge(existingBridgeName);
                  }
               }
            }
            recoverStoredBridges();
         }

         recoverStoredConnectors();

         ActiveMQServerLogger.LOGGER.reloadingConfiguration("protocol services");
         updateProtocolServices();
      }
   }

   public Set<ActivateCallback> getActivateCallbacks() {
      return activateCallbacks;
   }

   @Override
   public List<ActiveMQComponent> getExternalComponents() {
      synchronized (externalComponents) {
         return new ArrayList<>(externalComponents);
      }
   }

   private void stopExternalComponents(boolean shutdown) {
      synchronized (externalComponents) {
         for (ActiveMQComponent externalComponent : externalComponents) {
            try {
               if (externalComponent instanceof ServiceComponent component) {
                  component.stop(shutdown);
               } else {
                  externalComponent.stop();
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorStoppingComponent(externalComponent.getClass().getName(), e);
            }
         }
      }
   }

   @Override
   public AutoCloseable managementLock() throws Exception {
      if (!managementLock.tryLock(1, TimeUnit.MINUTES)) {
         throw ActiveMQMessageBundle.BUNDLE.managementBusy();
      } else {
         return managementLock::unlock;
      }
   }

   @Override
   public IOCriticalErrorListener getIoCriticalErrorListener() {
      return ioCriticalErrorListener;
   }

   @Override
   public MirrorRegistry getMirrorRegistry() {
      return mirrorRegistry;
   }

   @Override
   public int getPendingMirrorAcks() {
      return mirrorRegistry.getMirrorAckSize();
   }
}