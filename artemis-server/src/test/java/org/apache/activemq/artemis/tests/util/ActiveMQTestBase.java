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
package org.apache.activemq.artemis.tests.util;

import javax.naming.Context;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalReaderCallback;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMRegistry;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.replication.ReplicationEndpoint;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.LiveOnlyActivation;
import org.apache.activemq.artemis.core.server.impl.ReplicationBackupActivation;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCUtils;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.CleanupSystemPropertiesRule;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.PortCheckRule;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.ThreadLeakCheckRule;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Base class with basic utilities on starting up a basic server
 */
public abstract class ActiveMQTestBase extends Assert {

   private static final Logger log = Logger.getLogger(ActiveMQTestBase.class);

   private static final Logger baseLog = Logger.getLogger(ActiveMQTestBase.class);

   protected final Logger instanceLog = Logger.getLogger(this.getClass());

   static {
      Env.setTestEnv(true);
   }

   private static final Logger logger = Logger.getLogger(ActiveMQTestBase.class);

   /** This will make sure threads are not leaking between tests */
   @ClassRule
   public static ThreadLeakCheckRule leakCheckRule = new ThreadLeakCheckRule();

   @ClassRule
   public static NoProcessFilesBehind noProcessFilesBehind = new NoProcessFilesBehind(1000);

   /** We should not under any circunstance create data outside of ./target
    *  if you have a test failing because because of this rule for any reason,
    *  even if you use afterClass events, move the test to ./target and always cleanup after
    *  your data even under ./target.
    *  Do not try to disable this rule! Fix your test! */
   @Rule
   public NoFilesBehind noFilesBehind = new NoFilesBehind("data", "null");

   /** This will cleanup any system property changed inside tests */
   @Rule
   public CleanupSystemPropertiesRule propertiesRule = new CleanupSystemPropertiesRule();

   @ClassRule
   public static PortCheckRule portCheckRule = new PortCheckRule(61616);

   public static final String TARGET_TMP = "./target/tmp";
   public static final String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();
   public static final String INVM_CONNECTOR_FACTORY = InVMConnectorFactory.class.getCanonicalName();
   public static final String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();
   public static final String NETTY_CONNECTOR_FACTORY = NettyConnectorFactory.class.getCanonicalName();
   public static final String CLUSTER_PASSWORD = "UnitTestsClusterPassword";

   /**
    * Add a "sendCallNumber" property to messages sent using helper classes. Meant to help in
    * debugging.
    */
   private static final String SEND_CALL_NUMBER = "sendCallNumber";
   private static final String OS_TYPE = System.getProperty("os.name").toLowerCase();
   private static final int DEFAULT_UDP_PORT;

   protected static final long WAIT_TIMEOUT = 30000;

   private static String testClassName = "not-yet-set";
   private static String failingTotalMaxIoMessage;

   // There is a verification about thread leakages. We only fail a single thread when this happens
   private static Set<Thread> alreadyFailedThread = new HashSet<>();

   private final Collection<ActiveMQServer> servers = new ArrayList<>();
   private final Collection<ServerLocator> locators = new ArrayList<>();
   private final Collection<ClientSessionFactory> sessionFactories = new ArrayList<>();
   private final Collection<ClientSession> clientSessions = new HashSet<>();
   private final Collection<ClientConsumer> clientConsumers = new HashSet<>();
   private final Collection<ClientProducer> clientProducers = new HashSet<>();
   private final Collection<ActiveMQComponent> otherComponents = new HashSet<>();
   private final Set<ExecutorService> executorSet = new HashSet<>();

   private String testDir;
   private int sendMsgCount = 0;

   @Rule
   public TestName name = new TestName();

   @Rule
   public TemporaryFolder temporaryFolder;

   @Rule
   // This Custom rule will remove any files under ./target/tmp
   // including anything created previously by TemporaryFolder
   public RemoveFolder folder = new RemoveFolder(TARGET_TMP);

   @Rule
   public TestRule watcher = new TestWatcher() {
      @Override
      protected void starting(Description description) {
         testClassName = description.getClassName();
         baseLog.info(String.format("**** start #test %s() ***", description.getMethodName()));
      }

      @Override
      protected void finished(Description description) {
         baseLog.info(String.format("**** end #test %s() ***", description.getMethodName()));
      }
   };

   @After
   public void shutdownDerby() {
      try {
         DriverManager.getConnection("jdbc:derby:" + getEmbeddedDataBaseName() + ";destroy=true");
      } catch (Exception ignored) {
      }
      try {
         DriverManager.getConnection("jdbc:derby:;shutdown=true");
      } catch (Exception ignored) {
      }
   }

   static {
      Random random = new Random();
      DEFAULT_UDP_PORT = 6000 + random.nextInt(1000);
   }

   public ActiveMQTestBase() {
      File parent = new File(TARGET_TMP);
      parent.mkdirs();
      File subParent = new File(parent, this.getClass().getSimpleName());
      subParent.mkdirs();
      temporaryFolder = new TemporaryFolder(subParent);
   }

   protected <T> T serialClone(Object object) throws Exception {
      log.debug("object::" + object);
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream obOut = new ObjectOutputStream(bout);
      obOut.writeObject(object);

      ByteArrayInputStream binput = new ByteArrayInputStream(bout.toByteArray());
      ObjectInputStream obinp = new ObjectInputStream(binput);
      return (T) obinp.readObject();

   }

   @After
   public void tearDown() throws Exception {
      closeAllSessionFactories();
      closeAllServerLocatorsFactories();

      try {
         assertAllClientConsumersAreClosed();
         assertAllClientProducersAreClosed();
         assertAllClientSessionsAreClosed();
      } finally {
         synchronized (servers) {
            for (ActiveMQServer server : servers) {
               if (server == null) {
                  continue;
               }

               // disable scaledown on tearDown, otherwise it takes a lot of time
               try {
                  ((LiveOnlyActivation) server.getActivation()).getLiveOnlyPolicy().getScaleDownPolicy().setEnabled(false);
               } catch (Throwable ignored) {
                  // don't care about anything here
                  // if can't find activation, livePolicy or no LiveONlyActivation... don't care!!!
                  // all I care is f you have scaleDownPolicy, it should be set to false at this point
               }

               try {
                  final ClusterManager clusterManager = server.getClusterManager();
                  if (clusterManager != null) {
                     for (ClusterConnection cc : clusterManager.getClusterConnections()) {
                        stopComponent(cc);
                     }
                  }
               } catch (Exception e) {
                  // no-op
               }
               stopComponentOutputExceptions(server);
            }
            servers.clear();
         }

         closeAllOtherComponents();

         List<Exception> exceptions;
         try {
            exceptions = checkCsfStopped();
         } finally {
            cleanupPools();
         }

         for (ExecutorService s : executorSet) {
            s.shutdown();
         }
         InVMConnector.resetThreadPool();
         assertAllExecutorsFinished();

         //clean up pools before failing
         if (!exceptions.isEmpty()) {
            for (Exception exception : exceptions) {
               exception.printStackTrace(System.out);
            }
            System.out.println(threadDump("Thread dump with reconnects happening"));
         }
         Map<Thread, StackTraceElement[]> threadMap = Thread.getAllStackTraces();
         for (Map.Entry<Thread, StackTraceElement[]> entry : threadMap.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stack = entry.getValue();
            for (StackTraceElement stackTraceElement : stack) {
               if (stackTraceElement.getMethodName().contains("getConnectionWithRetry") && !alreadyFailedThread.contains(thread)) {
                  alreadyFailedThread.add(thread);
                  System.out.println(threadDump(this.getName() + " has left threads running. Look at thread " +
                                                   thread.getName() +
                                                   " id = " +
                                                   thread.getId() +
                                                   " has running locators on test " +
                                                   this.getName() +
                                                   " on this following dump"));
                  fail("test '" + getName() + "' left serverlocator running, this could effect other tests");
               } else if (stackTraceElement.getMethodName().contains("BroadcastGroupImpl.run") && !alreadyFailedThread.contains(thread)) {
                  alreadyFailedThread.add(thread);
                  System.out.println(threadDump(this.getName() + " has left threads running. Look at thread " +
                                                   thread.getName() +
                                                   " id = " +
                                                   thread.getId() +
                                                   " is still broadcasting " +
                                                   this.getName() +
                                                   " on this following dump"));
                  fail("test left broadcastgroupimpl running, this could effect other tests");
               }
            }
         }

         if (Thread.currentThread().getContextClassLoader() == null) {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            fail("Thread Context ClassLoader was set to null at some point before this test. We will set to this.getClass().getClassLoader(), but you are supposed to fix your tests");
         }

         checkFilesUsage();
      }

      if (InVMRegistry.instance.size() > 0) {
         fail("InVMREgistry size > 0");
      }
   }

   @Before
   public void setupTestDir() {
      testDir = temporaryFolder.getRoot().getAbsolutePath();
   }

   @Before
   public void setUp() throws Exception {
      sendMsgCount = 0;
      clearDataRecreateServerDirs();
      OperationContextImpl.clearContext();

      InVMRegistry.instance.clear();
   }

   public static void assertEqualsByteArrays(final byte[] expected, final byte[] actual) {
      for (int i = 0; i < expected.length; i++) {
         Assert.assertEquals("byte at index " + i, expected[i], actual[i]);
      }
   }

   /**
    * @param str
    * @param sub
    * @return
    */
   public static int countOccurrencesOf(String str, String sub) {
      if (str == null || sub == null || str.length() == 0 || sub.length() == 0) {
         return 0;
      }
      int count = 0;
      int pos = 0;
      int idx;
      while ((idx = str.indexOf(sub, pos)) != -1) {
         ++count;
         pos = idx + sub.length();
      }
      return count;
   }

   protected void disableCheckThread() {
      leakCheckRule.disable();
   }

   protected String getName() {
      return name.getMethodName();
   }

   protected boolean isWindows() {
      return (OS_TYPE.indexOf("win") >= 0);
   }

   protected Configuration createDefaultInVMConfig() throws Exception {
      return createDefaultConfig(0, false);
   }

   protected Configuration createDefaultInVMConfig(final int serverID) throws Exception {
      return createDefaultConfig(serverID, false);
   }

   protected Configuration createDefaultNettyConfig() throws Exception {
      return createDefaultConfig(0, true);
   }

   protected Configuration createDefaultConfig(final boolean netty) throws Exception {
      return createDefaultConfig(0, netty);
   }

   protected Configuration createDefaultJDBCConfig(boolean isNetty) throws Exception {
      Configuration configuration = createDefaultConfig(isNetty);
      setDBStoreType(configuration);
      return configuration;
   }

   protected Configuration createDefaultConfig(final int serverID, final boolean netty) throws Exception {
      ConfigurationImpl configuration = createBasicConfig(serverID).setJMXManagementEnabled(false).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, generateInVMParams(serverID), "invm"));

      if (netty) {
         configuration.addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, new HashMap<String, Object>(), "netty", new HashMap<String, Object>()));
      }

      return configuration;
   }

   private Configuration createDefaultConfig(final int index,
                                             final Map<String, Object> params,
                                             final String... acceptors) {
      Configuration configuration = createBasicConfig(index);

      for (String acceptor : acceptors) {
         TransportConfiguration transportConfig = new TransportConfiguration(acceptor, params);
         configuration.getAcceptorConfigurations().add(transportConfig);
      }

      return configuration;
   }

   protected ConfigurationImpl createBasicConfig() throws Exception {
      return createBasicConfig(-1);
   }

   /**
    * @param serverID
    * @return
    * @throws Exception
    */
   protected ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = new ConfigurationImpl().setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(getDefaultJournalType()).setJournalDirectory(getJournalDir(serverID, false)).setBindingsDirectory(getBindingsDir(serverID, false)).setPagingDirectory(getPageDir(serverID, false)).setLargeMessagesDirectory(getLargeMessagesDir(serverID, false)).setJournalCompactMinFiles(0).setJournalCompactPercentage(0).setClusterPassword(CLUSTER_PASSWORD).setJournalDatasync(false);

      // When it comes to the testsuite, we don't need any batching, I will leave some minimal batching to exercise the codebase
      configuration.setJournalBufferTimeout_AIO(100).setJournalBufferTimeout_NIO(100);

      return configuration;
   }

   protected void setDBStoreType(Configuration configuration) {
      configuration.setStoreConfiguration(createDefaultDatabaseStorageConfiguration());
   }

   protected DatabaseStorageConfiguration createDefaultDatabaseStorageConfiguration() {
      DatabaseStorageConfiguration dbStorageConfiguration = new DatabaseStorageConfiguration();
      dbStorageConfiguration.setJdbcConnectionUrl(getTestJDBCConnectionUrl());
      dbStorageConfiguration.setBindingsTableName("BINDINGS");
      dbStorageConfiguration.setMessageTableName("MESSAGE");
      dbStorageConfiguration.setLargeMessageTableName("LARGE_MESSAGE");
      dbStorageConfiguration.setPageStoreTableName("PAGE_STORE");
      dbStorageConfiguration.setJdbcDriverClassName(getJDBCClassName());
      dbStorageConfiguration.setJdbcLockAcquisitionTimeoutMillis(getJdbcLockAcquisitionTimeoutMillis());
      dbStorageConfiguration.setJdbcLockExpirationMillis(getJdbcLockExpirationMillis());
      dbStorageConfiguration.setJdbcLockRenewPeriodMillis(getJdbcLockRenewPeriodMillis());
      dbStorageConfiguration.setJdbcNetworkTimeout(-1);
      return dbStorageConfiguration;
   }

   protected long getJdbcLockAcquisitionTimeoutMillis() {
      return Long.getLong("jdbc.lock.acquisition", ActiveMQDefaultConfiguration.getDefaultJdbcLockAcquisitionTimeoutMillis());
   }

   protected long getJdbcLockExpirationMillis() {
      return Long.getLong("jdbc.lock.expiration", 4_000);
   }

   protected long getJdbcLockRenewPeriodMillis() {
      return Long.getLong("jdbc.lock.renew", 200);
   }

   public void destroyTables(List<String> tableNames) throws Exception {
      Driver driver = getDriver(getJDBCClassName());
      Connection connection = driver.connect(getTestJDBCConnectionUrl(), null);
      Statement statement = connection.createStatement();
      try {
         for (String tableName : tableNames) {
            connection.setAutoCommit(false);
            SQLProvider sqlProvider = JDBCUtils.getSQLProvider(getJDBCClassName(), tableName, SQLProvider.DatabaseStoreType.LARGE_MESSAGE);
            try (ResultSet rs = connection.getMetaData().getTables(null, null, sqlProvider.getTableName(), null)) {
               if (rs.next()) {
                  statement.execute("DROP TABLE " + sqlProvider.getTableName());
               }
               connection.commit();
            } catch (SQLException e) {
               connection.rollback();
            }
         }
         connection.setAutoCommit(true);
      } catch (Throwable e) {
         e.printStackTrace();
      } finally {
         connection.close();
      }
   }

   private Driver getDriver(String className) throws Exception {
      try {
         Driver driver = (Driver) Class.forName(className).newInstance();

         // Shutdown the derby if using the derby embedded driver.
         if (className.equals("org.apache.derby.jdbc.EmbeddedDriver")) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
               @Override
               public void run() {
                  try {
                     DriverManager.getConnection("jdbc:derby:;shutdown=true");
                  } catch (Exception e) {
                  }
               }
            });
         }
         return driver;
      } catch (ClassNotFoundException cnfe) {
         throw new RuntimeException("Could not find class: " + className);
      } catch (Exception e) {
         throw new RuntimeException("Unable to instantiate driver class: ", e);
      }
   }

   protected Map<String, Object> generateInVMParams(final int node) {
      Map<String, Object> params = new HashMap<>();

      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, node);

      return params;
   }


   /** This exists as an extension point for tests, so tests can replace it */
   protected ClusterConnectionConfiguration createBasicClusterConfig(String connectorName,
                                                                                      String... connectors) {
      return basicClusterConnectionConfig(connectorName, connectors);
   }


   protected static final ClusterConnectionConfiguration basicClusterConnectionConfig(String connectorName,
                                                                                      String... connectors) {
      ArrayList<String> connectors0 = new ArrayList<>();
      for (String c : connectors) {
         connectors0.add(c);
      }
      ClusterConnectionConfiguration clusterConnectionConfiguration = new ClusterConnectionConfiguration().
         setName("cluster1").setAddress("jms").setConnectorName(connectorName).
         setRetryInterval(100).setDuplicateDetection(false).setMaxHops(1).
         setConfirmationWindowSize(1).setMessageLoadBalancingType(MessageLoadBalancingType.STRICT).
         setStaticConnectors(connectors0);

      return clusterConnectionConfiguration;
   }

   protected final OrderedExecutorFactory getOrderedExecutor() {
      final ExecutorService executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory());
      executorSet.add(executor);
      return new OrderedExecutorFactory(executor);
   }

   protected static String getUDPDiscoveryAddress() {
      return System.getProperty("TEST-UDP-ADDRESS", "230.1.2.3");
   }

   protected static String getUDPDiscoveryAddress(final int variant) {
      String value = getUDPDiscoveryAddress();

      int posPoint = value.lastIndexOf('.');

      int last = Integer.valueOf(value.substring(posPoint + 1));

      return value.substring(0, posPoint + 1) + (last + variant);
   }

   public static int getUDPDiscoveryPort() {
      String port = System.getProperty("TEST-UDP-PORT");
      if (port != null) {
         return Integer.parseInt(port);
      }
      return DEFAULT_UDP_PORT;
   }

   public static int getUDPDiscoveryPort(final int variant) {
      return getUDPDiscoveryPort() + variant;
   }

   public static JournalType getDefaultJournalType() {
      if (AIOSequentialFileFactory.isSupported()) {
         return JournalType.ASYNCIO;
      } else {
         return JournalType.NIO;
      }
   }

   public static void forceGC() {
      ThreadLeakCheckRule.forceGC();
   }

   /**
    * Verifies whether weak references are released after a few GCs.
    *
    * @param references
    */
   public static void checkWeakReferences(final WeakReference<?>... references) {
      int i = 0;
      boolean hasValue = false;

      do {
         hasValue = false;

         if (i > 0) {
            forceGC();
         }

         for (WeakReference<?> ref : references) {
            if (ref.get() != null) {
               hasValue = true;
               break;
            }
         }
      }
      while (i++ <= 200 && hasValue);

      for (WeakReference<?> ref : references) {
         Assert.assertNull(ref.get());
      }
   }

   public static String threadDump(final String msg) {

      return ThreadDumpUtil.threadDump(msg);

   }

   /**
    * Sends the message to both logger and System.out (for unit report)
    */
   public void logAndSystemOut(String message, Exception e) {
      System.out.println(message);
      if (e != null) {
         e.printStackTrace(System.out);
      }
      ActiveMQServerLogger log0 = ActiveMQServerLogger.LOGGER;
      log0.debug(message, e);
   }

   /**
    * Sends the message to both logger and System.out (for unit report)
    */
   public void logAndSystemOut(String message) {
      logAndSystemOut(message, null);
   }

   public static String dumpBytes(final byte[] bytes) {
      StringBuffer buff = new StringBuffer();

      buff.append(System.identityHashCode(bytes) + ", size: " + bytes.length + " [");

      for (int i = 0; i < bytes.length; i++) {
         buff.append(bytes[i]);

         if (i != bytes.length - 1) {
            buff.append(", ");
         }
      }

      buff.append("]");

      return buff.toString();
   }

   public static String dumpBytesHex(final byte[] buffer, final int bytesPerLine) {

      StringBuffer buff = new StringBuffer();

      buff.append("[");

      for (int i = 0; i < buffer.length; i++) {
         buff.append(String.format("%1$2X", buffer[i]));
         if (i + 1 < buffer.length) {
            buff.append(", ");
         }
         if ((i + 1) % bytesPerLine == 0) {
            buff.append("\n ");
         }
      }
      buff.append("]");

      return buff.toString();
   }

   public static void assertEqualsTransportConfigurations(final TransportConfiguration[] expected,
                                                          final TransportConfiguration[] actual) {
      assertEquals(expected.length, actual.length);
      for (int i = 0; i < expected.length; i++) {
         Assert.assertEquals("TransportConfiguration at index " + i, expected[i], actual[i]);
      }
   }

   public static void assertEqualsBuffers(final int size, final ActiveMQBuffer expected, final ActiveMQBuffer actual) {
      // assertEquals(expected.length, actual.length);
      expected.readerIndex(0);
      actual.readerIndex(0);

      for (int i = 0; i < size; i++) {
         byte b1 = expected.readByte();
         byte b2 = actual.readByte();
         Assert.assertEquals("byte at index " + i, b1, b2);
      }
      expected.resetReaderIndex();
      actual.resetReaderIndex();
   }

   public static void assertEqualsByteArrays(final int length, final byte[] expected, final byte[] actual) {
      // we check only for the given length (the arrays might be
      // larger)
      Assert.assertTrue(expected.length >= length);
      Assert.assertTrue(actual.length >= length);
      for (int i = 0; i < length; i++) {
         Assert.assertEquals("byte at index " + i, expected[i], actual[i]);
      }
   }

   public static void assertSameXids(final List<Xid> expected, final List<Xid> actual) {
      Assert.assertNotNull(expected);
      Assert.assertNotNull(actual);
      Assert.assertEquals(expected.size(), actual.size());

      for (int i = 0; i < expected.size(); i++) {
         Xid expectedXid = expected.get(i);
         Xid actualXid = actual.get(i);
         assertEqualsByteArrays(expectedXid.getBranchQualifier(), actualXid.getBranchQualifier());
         Assert.assertEquals(expectedXid.getFormatId(), actualXid.getFormatId());
         assertEqualsByteArrays(expectedXid.getGlobalTransactionId(), actualXid.getGlobalTransactionId());
      }
   }

   protected static void checkNoBinding(final Context context, final String binding) {
      try {
         context.lookup(binding);
         Assert.fail("there must be no resource to look up for " + binding);
      } catch (Exception e) {
      }
   }

   protected static Object checkBinding(final Context context, final String binding) throws Exception {
      Object o = context.lookup(binding);
      Assert.assertNotNull(o);
      return o;
   }

   /**
    * @param connectorConfigs
    * @return
    */
   protected ArrayList<String> registerConnectors(final ActiveMQServer server,
                                                  final List<TransportConfiguration> connectorConfigs) {
      // The connectors need to be pre-configured at main config object but this method is taking
      // TransportConfigurations directly
      // So this will first register them at the config and then generate a list of objects
      ArrayList<String> connectors = new ArrayList<>();
      for (TransportConfiguration tnsp : connectorConfigs) {
         String name1 = RandomUtil.randomString();

         server.getConfiguration().getConnectorConfigurations().put(name1, tnsp);

         connectors.add(name1);
      }
      return connectors;
   }

   /**
    * @return the testDir
    */
   protected final String getTestDir() {
      return testDir;
   }

   private String getEmbeddedDataBaseName() {
      return "memory:" + getTestDir();
   }

   protected final String getTestJDBCConnectionUrl() {
      return System.getProperty("jdbc.connection.url", "jdbc:derby:" + getEmbeddedDataBaseName() + ";create=true");
   }

   protected final String getJDBCClassName() {
      return System.getProperty("jdbc.driver.class", "org.apache.derby.jdbc.EmbeddedDriver");
   }

   protected final File getTestDirfile() {
      return new File(testDir);
   }

   protected final void setTestDir(String testDir) {
      this.testDir = testDir;
   }

   protected final void clearDataRecreateServerDirs() {
      clearDataRecreateServerDirs(0, false);
   }

   protected final void clearDataRecreateServerDirs(int index, boolean backup) {
      clearDataRecreateServerDirs(getTestDir(), index, backup);
   }

   protected void clearDataRecreateServerDirs(final String testDir1, int index, boolean backup) {
      // Need to delete the root

      File file = new File(testDir1);
      deleteDirectory(file);
      file.mkdirs();

      recreateDataDirectories(testDir1, index, backup);
   }

   protected void recreateDataDirectories(String testDir1, int index, boolean backup) {
      recreateDirectory(getJournalDir(testDir1, index, backup));
      recreateDirectory(getBindingsDir(testDir1, index, backup));
      recreateDirectory(getPageDir(testDir1, index, backup));
      recreateDirectory(getLargeMessagesDir(testDir1, index, backup));
      recreateDirectory(getClientLargeMessagesDir(testDir1));
      recreateDirectory(getTemporaryDir(testDir1));
   }

   /**
    * @return the journalDir
    */
   public String getJournalDir() {
      return getJournalDir(0, false);
   }

   protected static String getJournalDir(final String testDir1) {
      return testDir1 + "/journal";
   }

   public String getJournalDir(final int index, final boolean backup) {
      return getJournalDir(getTestDir(), index, backup);
   }

   public static String getJournalDir(final String testDir, final int index, final boolean backup) {
      return getJournalDir(testDir) + directoryNameSuffix(index, backup);
   }

   /**
    * @return the bindingsDir
    */
   protected String getBindingsDir() {
      return getBindingsDir(0, false);
   }

   /**
    * @return the bindingsDir
    */
   protected static String getBindingsDir(final String testDir1) {
      return testDir1 + "/bindings";
   }

   /**
    * @return the bindingsDir
    */
   protected String getBindingsDir(final int index, final boolean backup) {
      return getBindingsDir(getTestDir(), index, backup);
   }

   public static String getBindingsDir(final String testDir, final int index, final boolean backup) {
      return getBindingsDir(testDir) + directoryNameSuffix(index, backup);
   }

   /**
    * @return the pageDir
    */
   protected String getPageDir() {
      return getPageDir(0, false);
   }

   protected File getPageDirFile() {
      return new File(getPageDir());

   }

   /**
    * @return the pageDir
    */
   protected static String getPageDir(final String testDir1) {
      return testDir1 + "/page";
   }

   protected String getPageDir(final int index, final boolean backup) {
      return getPageDir(getTestDir(), index, backup);
   }

   public static String getPageDir(final String testDir, final int index, final boolean backup) {
      return getPageDir(testDir) + directoryNameSuffix(index, backup);
   }

   /**
    * @return the largeMessagesDir
    */
   protected String getLargeMessagesDir() {
      return getLargeMessagesDir(0, false);
   }

   /**
    * @return the largeMessagesDir
    */
   protected static String getLargeMessagesDir(final String testDir1) {
      return testDir1 + "/large-msg";
   }

   protected String getLargeMessagesDir(final int index, final boolean backup) {
      return getLargeMessagesDir(getTestDir(), index, backup);
   }

   public static String getLargeMessagesDir(final String testDir, final int index, final boolean backup) {
      return getLargeMessagesDir(testDir) + directoryNameSuffix(index, backup);
   }

   private static String directoryNameSuffix(int index, boolean backup) {
      if (index == -1)
         return "";
      return index + "-" + (backup ? "B" : "L");
   }

   /**
    * @return the clientLargeMessagesDir
    */
   protected String getClientLargeMessagesDir() {
      return getClientLargeMessagesDir(getTestDir());
   }

   /**
    * @return the clientLargeMessagesDir
    */
   protected String getClientLargeMessagesDir(final String testDir1) {
      return testDir1 + "/client-large-msg";
   }

   /**
    * @return the temporaryDir
    */
   protected final String getTemporaryDir() {
      return getTemporaryDir(getTestDir());
   }

   /**
    * @return the temporaryDir
    */
   protected String getTemporaryDir(final String testDir1) {
      return testDir1 + "/temp";
   }

   protected static void expectActiveMQException(final String message,
                                                 final ActiveMQExceptionType errorCode,
                                                 final ActiveMQAction action) {
      try {
         action.run();
         Assert.fail(message);
      } catch (Exception e) {
         Assert.assertTrue(e instanceof ActiveMQException);
         Assert.assertEquals(errorCode, ((ActiveMQException) e).getType());
      }
   }

   protected static void expectActiveMQException(final ActiveMQExceptionType errorCode, final ActiveMQAction action) {
      expectActiveMQException("must throw an ActiveMQException with the expected errorCode: " + errorCode, errorCode, action);
   }

   protected static void expectXAException(final int errorCode, final ActiveMQAction action) {
      try {
         action.run();
         Assert.fail("must throw a XAException with the expected errorCode: " + errorCode);
      } catch (Exception e) {
         Assert.assertTrue(e instanceof XAException);
         Assert.assertEquals(errorCode, ((XAException) e).errorCode);
      }
   }

   public static byte getSamplebyte(final long position) {
      return (byte) ('a' + position % ('z' - 'a' + 1));
   }

   // Creates a Fake LargeStream without using a real file
   public static InputStream createFakeLargeStream(final long size) throws Exception {
      return new InputStream() {
         private long count;

         private boolean closed = false;

         @Override
         public void close() throws IOException {
            super.close();
            closed = true;
         }

         @Override
         public int read() throws IOException {
            if (closed) {
               throw new IOException("Stream was closed");
            }
            if (count++ < size) {
               return getSamplebyte(count - 1);
            } else {
               return -1;
            }
         }
      };

   }

   /**
    * It validates a Bean (POJO) using simple setters and getters with random values.
    * You can pass a list of properties to be ignored, as some properties will have a pre-defined domain (not being possible to use random-values on them)
    */
   protected void validateGettersAndSetters(final Object pojo, final String... ignoredProperties) throws Exception {
      HashSet<String> ignoreSet = new HashSet<>();

      for (String ignore : ignoredProperties) {
         ignoreSet.add(ignore);
      }

      BeanInfo info = Introspector.getBeanInfo(pojo.getClass());

      PropertyDescriptor[] properties = info.getPropertyDescriptors();

      for (PropertyDescriptor prop : properties) {
         Object value;

         if (prop.getPropertyType() == String.class) {
            value = RandomUtil.randomString();
         } else if (prop.getPropertyType() == Integer.class || prop.getPropertyType() == Integer.TYPE) {
            value = RandomUtil.randomInt();
         } else if (prop.getPropertyType() == Long.class || prop.getPropertyType() == Long.TYPE) {
            value = RandomUtil.randomLong();
         } else if (prop.getPropertyType() == Boolean.class || prop.getPropertyType() == Boolean.TYPE) {
            value = RandomUtil.randomBoolean();
         } else if (prop.getPropertyType() == Double.class || prop.getPropertyType() == Double.TYPE) {
            value = RandomUtil.randomDouble();
         } else {
            log.debug("Can't validate property of type " + prop.getPropertyType() + " on " + prop.getName());
            value = null;
         }

         if (value != null && prop.getWriteMethod() != null && prop.getReadMethod() == null) {
            log.debug("WriteOnly property " + prop.getName() + " on " + pojo.getClass());
         } else if (value != null && prop.getWriteMethod() != null &&
            prop.getReadMethod() != null &&
            !ignoreSet.contains(prop.getName())) {
            log.debug("Validating " + prop.getName() + " type = " + prop.getPropertyType());
            prop.getWriteMethod().invoke(pojo, value);

            Assert.assertEquals("Property " + prop.getName(), value, prop.getReadMethod().invoke(pojo));
         }
      }
   }

   /**
    * @param queue
    * @throws InterruptedException
    */
   protected void waitForNotPaging(Queue queue) throws InterruptedException {
      waitForNotPaging(queue.getPageSubscription().getPagingStore());
   }

   protected void waitForNotPaging(PagingStore store) throws InterruptedException {
      long timeout = System.currentTimeMillis() + 20000;
      while (timeout > System.currentTimeMillis() && store.isPaging()) {
         Thread.sleep(100);
      }
      assertFalse(store.isPaging());
   }

   protected static Topology waitForTopology(final ActiveMQServer server, final int nodes) throws Exception {
      return waitForTopology(server, nodes, -1, WAIT_TIMEOUT);
   }

   protected static Topology waitForTopology(final ActiveMQServer server,
                                      final int nodes,
                                      final int backups) throws Exception {
      return waitForTopology(server, nodes, backups, WAIT_TIMEOUT);
   }

   protected static Topology waitForTopology(final ActiveMQServer server,
                                      final int liveNodes,
                                      final int backupNodes,
                                      final long timeout) throws Exception {
      logger.debug("waiting for " + liveNodes + " on the topology for server = " + server);

      Set<ClusterConnection> ccs = server.getClusterManager().getClusterConnections();

      if (ccs.size() != 1) {
         throw new IllegalStateException("You need a single cluster connection on this version of waitForTopology on ServiceTestBase");
      }

      Topology topology = server.getClusterManager().getDefaultConnection(null).getTopology();

      return waitForTopology(topology, timeout, liveNodes, backupNodes);
   }

   protected static Topology waitForTopology(Topology topology,
                                    long timeout,
                                    int liveNodes,
                                    int backupNodes) throws Exception {
      final long start = System.currentTimeMillis();

      int liveNodesCount = 0;

      int backupNodesCount = 0;

      do {
         liveNodesCount = 0;
         backupNodesCount = 0;

         for (TopologyMemberImpl member : topology.getMembers()) {
            if (member.getLive() != null) {
               liveNodesCount++;
            }
            if (member.getBackup() != null) {
               backupNodesCount++;
            }
         }

         if ((liveNodes == -1 || liveNodes == liveNodesCount) && (backupNodes == -1 || backupNodes == backupNodesCount)) {
            return topology;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      String msg = "Timed out waiting for cluster topology of live=" + liveNodes + ",backup=" + backupNodes +
         " (received live=" + liveNodesCount + ", backup=" + backupNodesCount +
         ") topology = " +
         topology.describe() +
         ")";

      ActiveMQServerLogger.LOGGER.error(msg);

      throw new Exception(msg);
   }

   protected void waitForTopology(final ActiveMQServer server,
                                  String clusterConnectionName,
                                  final int nodes,
                                  final long timeout) throws Exception {
      logger.debug("waiting for " + nodes + " on the topology for server = " + server);

      long start = System.currentTimeMillis();

      ClusterConnection clusterConnection = server.getClusterManager().getClusterConnection(clusterConnectionName);

      Topology topology = clusterConnection.getTopology();

      do {
         if (nodes == topology.getMembers().size()) {
            return;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      String msg = "Timed out waiting for cluster topology of " + nodes +
         " (received " +
         topology.getMembers().size() +
         ") topology = " +
         topology +
         ")";

      ActiveMQServerLogger.LOGGER.error(msg);

      throw new Exception(msg);
   }

   protected static final void waitForComponent(final ActiveMQComponent component,
                                                final long seconds) throws InterruptedException {
      long time = System.currentTimeMillis();
      long toWait = seconds * 1000;
      while (!component.isStarted()) {
         Thread.sleep(50);
         if (System.currentTimeMillis() > (time + toWait)) {
            fail("component did not start within timeout of " + seconds);
         }
      }
   }

   protected static final Map<String, Object> generateParams(final int node, final boolean netty) {
      Map<String, Object> params = new HashMap<>();

      if (netty) {
         params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + node);
      } else {
         params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, node);
      }

      return params;
   }

   protected TransportConfiguration getNettyAcceptorTransportConfiguration(final boolean live) {
      if (live) {
         return new TransportConfiguration(NETTY_ACCEPTOR_FACTORY);
      }

      Map<String, Object> server1Params = new HashMap<>();

      server1Params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + 1);

      return new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, server1Params);
   }

   protected TransportConfiguration getNettyConnectorTransportConfiguration(final boolean live) {
      if (live) {
         return new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      }

      Map<String, Object> server1Params = new HashMap<>();

      server1Params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + 1);
      server1Params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.NETTY_CONNECT_TIMEOUT, 1000);
      return new TransportConfiguration(NETTY_CONNECTOR_FACTORY, server1Params);
   }

   protected static final TransportConfiguration createTransportConfiguration(boolean netty,
                                                                              boolean acceptor,
                                                                              Map<String, Object> params) {
      String className;
      if (netty) {
         if (acceptor) {
            className = NETTY_ACCEPTOR_FACTORY;
         } else {
            className = NETTY_CONNECTOR_FACTORY;
         }
      } else {
         if (acceptor) {
            className = INVM_ACCEPTOR_FACTORY;
         } else {
            className = INVM_CONNECTOR_FACTORY;
         }
      }
      if (params == null)
         params = new HashMap<>();
      return new TransportConfiguration(className, params, UUIDGenerator.getInstance().generateStringUUID(), new HashMap<String, Object>());
   }

   protected void waitForServerToStart(ActiveMQServer server) throws InterruptedException {
      waitForServerToStart(server, true);
   }

   protected void waitForServerToStart(ActiveMQServer server, boolean activation) throws InterruptedException {
      if (server == null)
         return;
      final long wait = 5000;
      long timetowait = System.currentTimeMillis() + wait;
      while (!server.isStarted() && System.currentTimeMillis() < timetowait) {
         Thread.sleep(50);
      }

      if (!server.isStarted()) {
         baseLog.info(threadDump("Server didn't start"));
         fail("server didn't start: " + server);
      }

      if (activation) {
         if (!server.getHAPolicy().isBackup()) {
            if (!server.waitForActivation(wait, TimeUnit.MILLISECONDS))
               fail("Server didn't initialize: " + server);
         }
      }
   }

   protected void waitForServerToStop(ActiveMQServer server) throws InterruptedException {
      if (server == null)
         return;
      final long wait = 5000;
      long timetowait = System.currentTimeMillis() + wait;
      while (server.isStarted() && System.currentTimeMillis() < timetowait) {
         Thread.sleep(50);
      }

      if (server.isStarted()) {
         baseLog.info(threadDump("Server didn't start"));
         fail("Server didn't start: " + server);
      }
   }

   /**
    * @param backup
    */
   public static final void waitForRemoteBackupSynchronization(final ActiveMQServer backup) {
      waitForRemoteBackup(null, 20, true, backup);
   }

   /**
    * @param sessionFactoryP
    * @param seconds
    * @param waitForSync
    * @param backup
    */
   public static final void waitForRemoteBackup(ClientSessionFactory sessionFactoryP,
                                                int seconds,
                                                boolean waitForSync,
                                                final ActiveMQServer backup) {
      ClientSessionFactoryInternal sessionFactory = (ClientSessionFactoryInternal) sessionFactoryP;
      final ActiveMQServerImpl actualServer = (ActiveMQServerImpl) backup;
      final long toWait = seconds * 1000L;
      final long time = System.currentTimeMillis();
      int loop = 0;
      //Note: if maxLoop is too small there won't be
      //enough time for quorum vote to complete and
      //will cause test to fail.
      final int maxLoop = 40;
      while (true) {
         Activation activation = actualServer.getActivation();
         boolean isReplicated = !backup.getHAPolicy().isSharedStore();
         boolean isRemoteUpToDate = true;
         if (isReplicated) {
            if (activation instanceof SharedNothingBackupActivation) {
               isRemoteUpToDate = backup.isReplicaSync();
            } else if (activation instanceof ReplicationBackupActivation) {
               isRemoteUpToDate = backup.isReplicaSync();
            } else {
               //we may have already failed over and changed the Activation
               if (actualServer.isStarted()) {
                  //let it fail a few time to have time to start stopping in the case of waiting to failback
                  isRemoteUpToDate = loop++ > maxLoop;
               } else {
                  //we could be waiting to failback or restart if the server is stopping
                  isRemoteUpToDate = false;
               }
            }
         }
         if ((sessionFactory == null || sessionFactory.getBackupConnector() != null) &&
            (isRemoteUpToDate || !waitForSync) &&
            (!waitForSync || actualServer.getBackupManager() != null && actualServer.getBackupManager().isBackupAnnounced())) {
            break;
         }
         if (System.currentTimeMillis() > (time + toWait)) {
            String threadDump = threadDump("can't get synchronization finished " + backup.isReplicaSync());
            System.err.println(threadDump);
            fail("backup started? (" + actualServer.isStarted() + "). Finished synchronizing (" +
                    (activation) + "). SessionFactory!=null ? " + (sessionFactory != null) +
                    " || sessionFactory.getBackupConnector()==" +
                    (sessionFactory != null ? sessionFactory.getBackupConnector() : "not-applicable") + "\n" + threadDump);
         }
         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            fail(e.getMessage());
         }
      }
   }

   public static final void waitForRemoteBackup(ClientSessionFactory sessionFactory, int seconds) {
      ClientSessionFactoryInternal factoryInternal = (ClientSessionFactoryInternal) sessionFactory;
      final long toWait = seconds * 1000L;
      final long time = System.currentTimeMillis();
      while (true) {
         if (factoryInternal.getBackupConnector() != null) {
            break;
         }
         if (System.currentTimeMillis() > (time + toWait)) {
            fail("Backup wasn't located");
         }
         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            fail(e.getMessage());
         }
      }
   }

   protected ActiveMQServer createServer(final boolean realFiles,
                                               final Configuration configuration,
                                               final int pageSize,
                                               final long maxAddressSize) {
      return createServer(realFiles, configuration, pageSize, maxAddressSize, (Map<String, AddressSettings>) null);
   }

   protected ActiveMQServer createServer(final boolean realFiles,
                                         final Configuration configuration,
                                         final int pageSize,
                                         final long maxAddressSize,
                                         final Map<String, AddressSettings> settings) {
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration, realFiles));

      if (settings != null) {
         for (Map.Entry<String, AddressSettings> setting : settings.entrySet()) {
            server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
         }
      }

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(pageSize).setMaxSizeBytes(maxAddressSize).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected final ActiveMQServer createServer(final boolean realFiles,
                                               final Configuration configuration,
                                               final int pageSize,
                                               final long maxAddressSize,
                                               final Map<String, AddressSettings> settings,
                                               StoreConfiguration.StoreType storeType) {
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         setDBStoreType(configuration);
      }
      return createServer(realFiles, configuration, pageSize, maxAddressSize, settings);
   }

   protected final ActiveMQServer createServer(final boolean realFiles) throws Exception {
      return createServer(realFiles, false);
   }

   protected final ActiveMQServer createServer(final boolean realFiles, final boolean netty) throws Exception {
      return createServer(realFiles, createDefaultConfig(netty), AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
   }

   protected ActiveMQServer createServer(final boolean realFiles, final Configuration configuration) {
      return createServer(realFiles, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
   }

   protected final ActiveMQServer createServer(final Configuration configuration) {
      return createServer(configuration.isPersistenceEnabled(), configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
   }

   protected ActiveMQServer createServer(final boolean realFiles,
                                         boolean isNetty,
                                         StoreConfiguration.StoreType storeType) throws Exception {
      Configuration configuration = storeType == StoreConfiguration.StoreType.DATABASE ? createDefaultJDBCConfig(isNetty) : createDefaultConfig(isNetty);
      return createServer(realFiles, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
   }

   protected ActiveMQServer createInVMFailoverServer(final boolean realFiles,
                                                     final Configuration configuration,
                                                     final NodeManager nodeManager,
                                                     final int id) {
      return createInVMFailoverServer(realFiles, configuration, -1, -1, new HashMap<String, AddressSettings>(), nodeManager, id);
   }

   protected ActiveMQServer createInVMFailoverServer(final boolean realFiles,
                                                     final Configuration configuration,
                                                     final int pageSize,
                                                     final int maxAddressSize,
                                                     final Map<String, AddressSettings> settings,
                                                     NodeManager nodeManager,
                                                     final int id) {
      ActiveMQServer server;
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      configuration.setPersistenceEnabled(realFiles);
      server = addServer(new InVMNodeManagerServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, nodeManager));

      try {
         server.setIdentity("Server " + id);

         for (Map.Entry<String, AddressSettings> setting : settings.entrySet()) {
            server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
         }

         AddressSettings defaultSetting = new AddressSettings();
         defaultSetting.setPageSizeBytes(pageSize);
         defaultSetting.setMaxSizeBytes(maxAddressSize);

         server.getAddressSettingsRepository().addMatch("#", defaultSetting);

         return server;
      } finally {
         addServer(server);
      }
   }

   protected ActiveMQServer createColocatedInVMFailoverServer(final boolean realFiles,
                                                              final Configuration configuration,
                                                              NodeManager liveNodeManager,
                                                              NodeManager backupNodeManager,
                                                              final int id) {
      return createColocatedInVMFailoverServer(realFiles, configuration, -1, -1, new HashMap<String, AddressSettings>(), liveNodeManager, backupNodeManager, id);
   }

   protected ActiveMQServer createColocatedInVMFailoverServer(final boolean realFiles,
                                                              final Configuration configuration,
                                                              final int pageSize,
                                                              final int maxAddressSize,
                                                              final Map<String, AddressSettings> settings,
                                                              NodeManager liveNodeManager,
                                                              NodeManager backupNodeManager,
                                                              final int id) {
      ActiveMQServer server;
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      configuration.setPersistenceEnabled(realFiles);
      server = new ColocatedActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, liveNodeManager, backupNodeManager);

      try {
         server.setIdentity("Server " + id);

         for (Map.Entry<String, AddressSettings> setting : settings.entrySet()) {
            server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
         }

         AddressSettings defaultSetting = new AddressSettings();
         defaultSetting.setPageSizeBytes(pageSize);
         defaultSetting.setMaxSizeBytes(maxAddressSize);

         server.getAddressSettingsRepository().addMatch("#", defaultSetting);

         return server;
      } finally {
         addServer(server);
      }
   }

   protected ActiveMQServer createClusteredServerWithParams(final boolean isNetty,
                                                            final int index,
                                                            final boolean realFiles,
                                                            final Map<String, Object> params) throws Exception {
      String acceptor = isNetty ? NETTY_ACCEPTOR_FACTORY : INVM_ACCEPTOR_FACTORY;
      return createServer(realFiles, createDefaultConfig(index, params, acceptor), -1, -1);
   }

   protected ActiveMQServer createClusteredServerWithParams(final boolean isNetty,
                                                            final int index,
                                                            final boolean realFiles,
                                                            final int pageSize,
                                                            final int maxAddressSize,
                                                            final Map<String, Object> params) throws Exception {
      return createServer(realFiles, createDefaultConfig(index, params, (isNetty ? NETTY_ACCEPTOR_FACTORY : INVM_ACCEPTOR_FACTORY)), pageSize, maxAddressSize);
   }

   protected ServerLocator createFactory(final boolean isNetty) throws Exception {
      if (isNetty) {
         return createNettyNonHALocator();
      } else {
         return createInVMNonHALocator();
      }
   }

   protected void createAnycastPair(ActiveMQServer server, String queueName) throws Exception {
      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setAddress(queueName));
   }

   protected void createQueue(final String address, final String queue) throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = locator.createSessionFactory();
      ClientSession session = sf.createSession();
      try {
         session.createQueue(new QueueConfiguration(queue).setAddress(address));
      } finally {
         session.close();
         closeSessionFactory(sf);
         closeServerLocator(locator);
      }
   }

   protected final ServerLocator createInVMLocator(final int serverID) {
      TransportConfiguration tnspConfig = createInVMTransportConnectorConfig(serverID, UUIDGenerator.getInstance().generateStringUUID());

      ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(tnspConfig);
      return addServerLocator(locator);
   }

   /**
    * @param serverID
    * @return
    */
   protected final TransportConfiguration createInVMTransportConnectorConfig(final int serverID, String name1) {
      Map<String, Object> server1Params = new HashMap<>();

      if (serverID != 0) {
         server1Params.put(TransportConstants.SERVER_ID_PROP_NAME, serverID);
      }

      TransportConfiguration tnspConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY, server1Params, name1);
      return tnspConfig;
   }

   public String getTextMessage(final ClientMessage m) {
      m.getBodyBuffer().resetReaderIndex();
      return m.getBodyBuffer().readString();
   }

   protected ClientMessage createBytesMessage(final ClientSession session,
                                              final byte type,
                                              final byte[] b,
                                              final boolean durable) {
      ClientMessage message = session.createMessage(type, durable, 0, System.currentTimeMillis(), (byte) 1);
      message.getBodyBuffer().writeBytes(b);
      return message;
   }

   /**
    * @param i
    * @param message
    * @throws Exception
    */
   protected void setBody(final int i, final ClientMessage message) {
      message.getBodyBuffer().writeString("message" + i);
   }

   /**
    * @param i
    * @param message
    */
   protected void assertMessageBody(final int i, final ClientMessage message) {
      Assert.assertEquals(message.toString(), "message" + i, message.getBodyBuffer().readString());
   }

   /**
    * Send durable messages with pre-specified body.
    *
    * @param session
    * @param producer
    * @param numMessages
    * @throws ActiveMQException
    */
   public final void sendMessages(ClientSession session,
                                  ClientProducer producer,
                                  int numMessages) throws ActiveMQException {
      for (int i = 0; i < numMessages; i++) {
         producer.send(createMessage(session, i, true));
      }
   }

   protected final ClientMessage createMessage(ClientSession session,
                                               int counter,
                                               boolean durable) throws ActiveMQException {
      ClientMessage message = session.createMessage(durable);
      setBody(counter, message);
      message.putIntProperty("counter", counter);
      message.putIntProperty(SEND_CALL_NUMBER, sendMsgCount++);
      return message;
   }

   protected final void receiveMessages(ClientConsumer consumer,
                                        final int start,
                                        final int msgCount,
                                        final boolean ack) throws ActiveMQException {
      for (int i = start; i < msgCount; i++) {
         ClientMessage message = consumer.receive(1000);
         Assert.assertNotNull("Expecting a message " + i, message);
         // sendCallNumber is just a debugging measure.
         Object prop = message.getObjectProperty(SEND_CALL_NUMBER);
         if (prop == null)
            prop = Integer.valueOf(-1);
         final int actual = message.getIntProperty("counter").intValue();
         Assert.assertEquals("expected=" + i + ". Got: property['counter']=" + actual + " sendNumber=" + prop, i, actual);
         assertMessageBody(i, message);
         if (ack)
            message.acknowledge();
      }
   }

   /**
    * Reads a journal system and returns a Map<Integer,AtomicInteger> of recordTypes and the number of records per type,
    * independent of being deleted or not
    *
    * @param config
    * @return
    * @throws Exception
    */
   protected Pair<List<RecordInfo>, List<PreparedTransactionInfo>> loadMessageJournal(Configuration config) throws Exception {
      JournalImpl messagesJournal = null;
      try {
         SequentialFileFactory messagesFF = new NIOSequentialFileFactory(new File(getJournalDir()), null, 1);

         messagesJournal = new JournalImpl(config.getJournalFileSize(), config.getJournalMinFiles(), config.getJournalPoolFiles(), 0, 0, messagesFF, "activemq-data", "amq", 1);
         final List<RecordInfo> committedRecords = new LinkedList<>();
         final List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

         messagesJournal.start();

         messagesJournal.load(committedRecords, preparedTransactions, null, false);

         return new Pair<>(committedRecords, preparedTransactions);
      } finally {
         try {
            if (messagesJournal != null) {
               messagesJournal.stop();
            }
         } catch (Throwable ignored) {
         }
      }

   }

   /**
    * Reads a journal system and returns a Map<Integer,AtomicInteger> of recordTypes and the number of records per type,
    * independent of being deleted or not
    *
    * @param config
    * @return
    * @throws Exception
    */
   protected HashMap<Integer, AtomicInteger> countJournal(Configuration config) throws Exception {
      File location = config.getJournalLocation();
      return countJournal(location, config.getJournalFileSize(), config.getJournalMinFiles(), config.getJournalPoolFiles());
   }

   protected HashMap<Integer, AtomicInteger> countJournal(File location, int journalFileSize, int minFiles, int poolfiles) throws Exception {
      final HashMap<Integer, AtomicInteger> recordsType = new HashMap<>();
      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(location, null, 1);

      JournalImpl messagesJournal = new JournalImpl(journalFileSize, minFiles, poolfiles, 0, 0, messagesFF, "activemq-data", "amq", 1);
      List<JournalFile> filesToRead = messagesJournal.orderFiles();

      for (JournalFile file : filesToRead) {
         JournalImpl.readJournalFile(messagesFF, file, new RecordTypeCounter(recordsType));
      }
      return recordsType;
   }

   protected HashMap<Integer, AtomicInteger> countBindingJournal(Configuration config) throws Exception {
      final HashMap<Integer, AtomicInteger> recordsType = new HashMap<>();
      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(config.getBindingsLocation(), null, 1);

      JournalImpl messagesJournal = new JournalImpl(config.getJournalFileSize(), config.getJournalMinFiles(), config.getJournalPoolFiles(), 0, 0, messagesFF, "activemq-bindings", "bindings", 1);
      List<JournalFile> filesToRead = messagesJournal.orderFiles();

      for (JournalFile file : filesToRead) {
         JournalImpl.readJournalFile(messagesFF, file, new RecordTypeCounter(recordsType));
      }
      return recordsType;
   }

   /**
    * This method will load a journal and count the living records
    *
    * @param config
    * @return
    * @throws Exception
    */
   protected HashMap<Integer, AtomicInteger> countJournalLivingRecords(Configuration config) throws Exception {
      return internalCountJournalLivingRecords(config, true);
   }

   /**
    * This method will load a journal and count the living records
    *
    * @param config
    * @param messageJournal if true -> MessageJournal, false -> BindingsJournal
    * @return
    * @throws Exception
    */
   protected HashMap<Integer, AtomicInteger> internalCountJournalLivingRecords(Configuration config,
                                                                               boolean messageJournal) throws Exception {
      final HashMap<Integer, AtomicInteger> recordsType = new HashMap<>();
      SequentialFileFactory ff;

      JournalImpl journal;

      if (messageJournal) {
         ff = new NIOSequentialFileFactory(config.getJournalLocation(), null, 1);
         journal = new JournalImpl(config.getJournalFileSize(), config.getJournalMinFiles(), config.getJournalPoolFiles(), 0, 0, ff, "activemq-data", "amq", 1);
      } else {
         ff = new NIOSequentialFileFactory(config.getBindingsLocation(), null, 1);
         journal = new JournalImpl(1024 * 1024, 2, config.getJournalCompactMinFiles(), config.getJournalPoolFiles(), config.getJournalCompactPercentage(), ff, "activemq-bindings", "bindings", 1);
      }
      journal.start();

      final List<RecordInfo> committedRecords = new LinkedList<>();
      final List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

      journal.load(committedRecords, preparedTransactions, null, false);

      for (RecordInfo info : committedRecords) {
         Integer ikey = (int) info.getUserRecordType();
         AtomicInteger value = recordsType.get(ikey);
         if (value == null) {
            value = new AtomicInteger();
            recordsType.put(ikey, value);
         }
         value.incrementAndGet();

      }

      journal.stop();
      return recordsType;
   }

   private static final class RecordTypeCounter implements JournalReaderCallback {

      private final HashMap<Integer, AtomicInteger> recordsType;

      /**
       * @param recordsType
       */
      private RecordTypeCounter(HashMap<Integer, AtomicInteger> recordsType) {
         this.recordsType = recordsType;
      }

      AtomicInteger getType(byte key) {
         Integer ikey = (int) key;
         AtomicInteger value = recordsType.get(ikey);
         if (value == null) {
            value = new AtomicInteger();
            recordsType.put(ikey, value);
         }
         return value;
      }

      @Override
      public void onReadUpdateRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {
         getType(recordInfo.getUserRecordType()).incrementAndGet();
      }

      @Override
      public void onReadUpdateRecord(RecordInfo recordInfo) throws Exception {
         getType(recordInfo.getUserRecordType()).incrementAndGet();
      }

      @Override
      public void onReadAddRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {
         getType(recordInfo.getUserRecordType()).incrementAndGet();
      }

      @Override
      public void onReadAddRecord(RecordInfo recordInfo) throws Exception {
         getType(recordInfo.getUserRecordType()).incrementAndGet();
      }

      @Override
      public void onReadRollbackRecord(long transactionID) throws Exception {
      }

      @Override
      public void onReadPrepareRecord(long transactionID, byte[] extraData, int numberOfRecords) throws Exception {
      }

      @Override
      public void onReadDeleteRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {
      }

      @Override
      public void onReadDeleteRecord(long recordID) throws Exception {
      }

      @Override
      public void onReadCommitRecord(long transactionID, int numberOfRecords) throws Exception {
      }

      @Override
      public void markAsDataFile(JournalFile file0) {
      }
   }

   /**
    * @param server                the server where's being checked
    * @param address               the name of the address being checked
    * @param local                 if true we are looking for local bindings, false we are looking for remoting servers
    * @param expectedBindingCount  the expected number of counts
    * @param expectedConsumerCount the expected number of consumers
    * @param timeout               the timeout used on the check
    * @return
    * @throws Exception
    * @throws InterruptedException
    */
   protected boolean waitForBindings(final ActiveMQServer server,
                                     final String address,
                                     final boolean local,
                                     final int expectedBindingCount,
                                     final int expectedConsumerCount,
                                     long timeout) throws Exception {
      final PostOffice po = server.getPostOffice();

      long start = System.currentTimeMillis();

      int bindingCount = 0;

      int totConsumers = 0;

      do {
         bindingCount = 0;

         totConsumers = 0;

         Bindings bindings = po.getBindingsForAddress(new SimpleString(address));

         for (Binding binding : bindings.getBindings()) {
            if (binding.isConnected() && (binding instanceof LocalQueueBinding && local || binding instanceof RemoteQueueBinding && !local)) {
               QueueBinding qBinding = (QueueBinding) binding;

               bindingCount++;

               totConsumers += qBinding.consumerCount();
            }
         }

         if (bindingCount == expectedBindingCount && totConsumers == expectedConsumerCount) {
            return true;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      String msg = "Timed out waiting for bindings (bindingCount = " + bindingCount +
         " (expecting " +
         expectedBindingCount +
         ") " +
         ", totConsumers = " +
         totConsumers +
         " (expecting " +
         expectedConsumerCount +
         ")" +
         ")";

      baseLog.error(msg);
      return false;
   }

   protected int getNumberOfFiles(File directory) {
      return directory.listFiles().length;
   }
   /**
    * Deleting a file on LargeDir is an asynchronous process. We need to keep looking for a while if
    * the file hasn't been deleted yet.
    */
   protected void validateNoFilesOnLargeDir(final String directory, final int expect) throws Exception {
      File largeMessagesFileDir = new File(directory);
      Wait.assertEquals(expect, () -> getNumberOfFiles(largeMessagesFileDir));
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while
    * if the file hasn't been deleted yet
    */
   protected void validateNoFilesOnLargeDir() throws Exception {
      validateNoFilesOnLargeDir(getLargeMessagesDir(), 0);
   }

   public void printBindings(ActiveMQServer server, String address) throws Exception {
      PostOffice po = server.getPostOffice();
      Bindings bindings = po.getBindingsForAddress(new SimpleString(address));

      System.err.println("=======================================================================");
      System.err.println("Binding information for address = " + address + " for server " + server);

      for (Binding binding : bindings.getBindings()) {
         QueueBinding qBinding = (QueueBinding) binding;
         System.err.println("Binding = " + qBinding + ", queue=" + qBinding.getQueue());
      }

   }

   private void assertAllExecutorsFinished() throws InterruptedException {
      for (ExecutorService s : executorSet) {
         Assert.assertTrue(s.awaitTermination(5, TimeUnit.SECONDS));
      }
   }

   private List<Exception> checkCsfStopped() throws Exception {
      if (!Wait.waitFor(ClientSessionFactoryImpl.CLOSE_RUNNABLES::isEmpty, 5_000)) {
         List<ClientSessionFactoryImpl.CloseRunnable> closeRunnables = new ArrayList<>(ClientSessionFactoryImpl.CLOSE_RUNNABLES);
         ArrayList<Exception> exceptions = new ArrayList<>();

         if (!closeRunnables.isEmpty()) {
            for (ClientSessionFactoryImpl.CloseRunnable closeRunnable : closeRunnables) {
               if (closeRunnable != null) {
                  exceptions.add(closeRunnable.stop().createTrace);
               }
            }
         }
         return exceptions;
      }

      return Collections.EMPTY_LIST;



   }

   private void assertAllClientProducersAreClosed() {
      synchronized (clientProducers) {
         for (ClientProducer p : clientProducers) {
            assertTrue(p + " should be closed", p.isClosed());
         }
         clientProducers.clear();
      }
   }

   /**
    *
    */
   private void closeAllOtherComponents() {
      synchronized (otherComponents) {
         for (ActiveMQComponent c : otherComponents) {
            stopComponent(c);
         }
         otherComponents.clear();
      }
   }

   @BeforeClass
   public static void checkLibaioBeforeClass() throws Throwable {
      if (failingTotalMaxIoMessage != null) {
         // Fail immediately if this is already set.
         fail(failingTotalMaxIoMessage);
      }

      long totalMaxIO = LibaioContext.getTotalMaxIO();
      if (totalMaxIO != 0) {
         log.error("LibaioContext TotalMaxIO > 0 before beginning test class. Issue presumably arose in a preceding class (not possible to be sure of which here). TotalMaxIO = " + totalMaxIO);
      }
   }

   @AfterClass
   public static void checkLibaio() throws Throwable {
      if (failingTotalMaxIoMessage != null) {
         // Test class was already failed if this is set, nothing to do here.
         return;
      }

      if (!Wait.waitFor(() -> LibaioContext.getTotalMaxIO() == 0)) {
         long totalMaxIO = LibaioContext.getTotalMaxIO();

         // Set messsage to fail subsequent runs with
         failingTotalMaxIoMessage = String.format("Aborting, LibaioContext TotalMaxIO > 0 issue previously detected by test class %s(), see its output.", testClassName);

         // Now fail this run
         String message = String.format("LibaioContext TotalMaxIO > 0 leak detected after class %s(), TotalMaxIO=%s(). Check output to determine if occurred before/during.", testClassName, totalMaxIO);
         log.error(message);
         Assert.fail(message);
      }
   }

   private void checkFilesUsage() throws Exception {
      int invmSize = InVMRegistry.instance.size();
      if (invmSize > 0) {
         InVMRegistry.instance.clear();
         baseLog.info(threadDump("Thread dump"));
         fail("invm registry still had acceptors registered");
      }
   }

   private void cleanupPools() {
      OperationContextImpl.clearContext();

      // We shutdown the global pools to give a better isolation between tests
      try {
         ServerLocatorImpl.clearThreadPools();
      } catch (Throwable e) {
         baseLog.info(threadDump(e.getMessage()));
         System.err.println(threadDump(e.getMessage()));
      }

      try {
         NettyConnector.clearThreadPools();
      } catch (Exception e) {
         baseLog.info(threadDump(e.getMessage()));
         System.err.println(threadDump(e.getMessage()));
      }
   }

   protected static final void recreateDirectory(final String directory) {
      File file = new File(directory);
      deleteDirectory(file);
      file.mkdirs();
   }

   protected static final boolean deleteDirectory(final File directory) {
      return FileUtil.deleteDirectory(directory);
   }

   protected static final void copyRecursive(final File from, final File to) throws Exception {
      if (from.isDirectory()) {
         if (!to.exists()) {
            to.mkdir();
         }

         String[] subs = from.list();

         for (String sub : subs) {
            copyRecursive(new File(from, sub), new File(to, sub));
         }
      } else {
         try (InputStream in = new BufferedInputStream(new FileInputStream(from));
              OutputStream out = new BufferedOutputStream(new FileOutputStream(to))) {
            int b;

            while ((b = in.read()) != -1) {
               out.write(b);
            }
         }
      }
   }

   protected void assertRefListsIdenticalRefs(final List<MessageReference> l1, final List<MessageReference> l2) {
      if (l1.size() != l2.size()) {
         Assert.fail("Lists different sizes: " + l1.size() + ", " + l2.size());
      }

      Iterator<MessageReference> iter1 = l1.iterator();
      Iterator<MessageReference> iter2 = l2.iterator();

      while (iter1.hasNext()) {
         MessageReference o1 = iter1.next();
         MessageReference o2 = iter2.next();

         Assert.assertTrue("expected " + o1 + " but was " + o2, o1 == o2);
      }
   }

   protected Message generateMessage(final long id) {
      ICoreMessage message = new CoreMessage(id, 1000);

      message.setMessageID(id);

      message.getBodyBuffer().writeString(UUID.randomUUID().toString());

      message.setAddress(new SimpleString("foo"));

      return message;
   }

   protected MessageReference generateReference(final Queue queue, final long id) {
      Message message = generateMessage(id);

      return MessageReference.Factory.createReference(message, queue);
   }

   protected int calculateRecordSize(final int size, final int alignment) {
      return (size / alignment + (size % alignment != 0 ? 1 : 0)) * alignment;
   }

   protected ClientMessage createTextMessage(final ClientSession session, final String s) {
      return createTextMessage(session, s, true);
   }

   protected ClientMessage createTextMessage(final ClientSession session, final String s, final boolean durable) {
      ClientMessage message = session.createMessage(Message.TEXT_TYPE, durable, 0, System.currentTimeMillis(), (byte) 4);
      message.getBodyBuffer().writeString(s);
      return message;
   }

   protected ClientMessage createTextMessage(final ClientSession session, final boolean durable, final int numChars) {
      ClientMessage message = session.createMessage(Message.TEXT_TYPE,
                durable,
                0,
                System.currentTimeMillis(),
                (byte)4);
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < numChars; i++) {
         builder.append('a');
      }
      message.getBodyBuffer().writeString(builder.toString());
      return message;
   }

   protected XidImpl newXID() {
      return new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
   }

   protected int getMessageCount(final ActiveMQServer service, final String address) throws Exception {
      return getMessageCount(service.getPostOffice(), address);
   }

   /**
    * @param address
    * @param postOffice
    * @return
    * @throws Exception
    */
   protected int getMessageCount(final PostOffice postOffice, final String address) throws Exception {
      int messageCount = 0;

      List<QueueBinding> bindings = getLocalQueueBindings(postOffice, address);

      for (QueueBinding qBinding : bindings) {
         qBinding.getQueue().flushExecutor();
         messageCount += getMessageCount(qBinding.getQueue());
      }

      return messageCount;
   }

   protected int getMessageCount(final Queue queue) {
      queue.flushExecutor();
      return (int) queue.getMessageCount();
   }

   /**
    * @param postOffice
    * @param address
    * @return
    * @throws Exception
    */
   protected int getMessagesAdded(final PostOffice postOffice, final String address) throws Exception {
      int messageCount = 0;

      List<QueueBinding> bindings = getLocalQueueBindings(postOffice, address);

      for (QueueBinding qBinding : bindings) {
         qBinding.getQueue().flushExecutor();
         messageCount += getMessagesAdded(qBinding.getQueue());
      }

      return messageCount;
   }

   protected int getMessagesAdded(final Queue queue) {
      queue.flushExecutor();
      return (int) queue.getMessagesAdded();
   }

   private List<QueueBinding> getLocalQueueBindings(final PostOffice postOffice,
                                                    final String address) throws Exception {
      ArrayList<QueueBinding> bindingsFound = new ArrayList<>();

      Bindings bindings = postOffice.getBindingsForAddress(new SimpleString(address));

      for (Binding binding : bindings.getBindings()) {
         if (binding instanceof LocalQueueBinding) {
            bindingsFound.add((QueueBinding) binding);
         }
      }
      return bindingsFound;
   }

   protected final ServerLocator createInVMNonHALocator() {
      return createNonHALocator(false);
   }

   protected final ServerLocator createNettyNonHALocator() {
      return createNonHALocator(true);
   }

   protected final ServerLocator createNonHALocator(final boolean isNetty) {
      ServerLocator locatorWithoutHA = internalCreateNonHALocator(isNetty);
      return addServerLocator(locatorWithoutHA);
   }

   /**
    * Creates the Locator without adding it to the list where the tearDown will take place
    * This is because we don't want it closed in certain tests where we are issuing failures
    *
    * @param isNetty
    * @return
    */
   public ServerLocator internalCreateNonHALocator(boolean isNetty) {
      return isNetty ? ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NETTY_CONNECTOR_FACTORY)) : ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY));
   }

   protected static final void stopComponent(ActiveMQComponent component) {
      if (component == null)
         return;
      try {
         component.stop();
      } catch (Exception e) {
         // no-op
      }
   }

   protected static final void stopComponentOutputExceptions(ActiveMQComponent component) {
      if (component == null)
         return;
      try {
         component.stop();
      } catch (Exception e) {
         System.err.println("Exception closing " + component);
         e.printStackTrace();
      }
   }

   protected final ClientSessionFactory createSessionFactory(ServerLocator locator) throws Exception {
      ClientSessionFactory sf = locator.createSessionFactory();
      addSessionFactory(sf);
      return sf;
   }

   protected final ActiveMQServer addServer(final ActiveMQServer server) {
      if (server != null) {
         synchronized (servers) {
            servers.add(server);
         }
      }
      return server;
   }

   protected final ServerLocator addServerLocator(final ServerLocator locator) {
      if (locator != null) {
         synchronized (locators) {
            locators.add(locator);
         }
      }
      return locator;
   }

   protected final ClientSession addClientSession(final ClientSession session) {
      if (session != null) {
         synchronized (clientSessions) {
            clientSessions.add(session);
         }
      }
      return session;
   }

   protected final ClientConsumer addClientConsumer(final ClientConsumer consumer) {
      if (consumer != null) {
         synchronized (clientConsumers) {
            clientConsumers.add(consumer);
         }
      }
      return consumer;
   }

   protected final ClientProducer addClientProducer(final ClientProducer producer) {
      if (producer != null) {
         synchronized (clientProducers) {
            clientProducers.add(producer);
         }
      }
      return producer;
   }

   protected final void addActiveMQComponent(final ActiveMQComponent component) {
      if (component != null) {
         synchronized (otherComponents) {
            otherComponents.add(component);
         }
      }
   }

   protected final ClientSessionFactory addSessionFactory(final ClientSessionFactory sf) {
      if (sf != null) {
         synchronized (sessionFactories) {
            sessionFactories.add(sf);
         }
      }
      return sf;
   }

   private void assertAllClientConsumersAreClosed() {
      synchronized (clientConsumers) {
         for (ClientConsumer cc : clientConsumers) {
            if (cc == null)
               continue;
            assertTrue(cc.isClosed());
         }
         clientConsumers.clear();
      }
   }

   private void assertAllClientSessionsAreClosed() {
      synchronized (clientSessions) {
         for (final ClientSession cs : clientSessions) {
            if (cs == null)
               continue;
            assertTrue(cs.isClosed());
         }
         clientSessions.clear();
      }
   }

   protected void closeAllSessionFactories() {
      synchronized (sessionFactories) {
         for (ClientSessionFactory sf : sessionFactories) {
            if (!sf.isClosed()) {
               closeSessionFactory(sf);
               assert sf.isClosed();
            }
         }
         sessionFactories.clear();
      }
   }

   protected void closeAllServerLocatorsFactories() {
      synchronized (locators) {
         for (ServerLocator locator : locators) {
            closeServerLocator(locator);
         }
         locators.clear();
      }
   }

   public static final void closeServerLocator(ServerLocator locator) {
      if (locator == null)
         return;
      try {
         locator.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static final void closeSessionFactory(final ClientSessionFactory sf) {
      if (sf == null)
         return;
      try {
         sf.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void crashAndWaitForFailure(ActiveMQServer server, ClientSession... sessions) throws Exception {
      CountDownLatch latch = new CountDownLatch(sessions.length);
      for (ClientSession session : sessions) {
         CountDownSessionFailureListener listener = new CountDownSessionFailureListener(latch, session);
         session.addFailureListener(listener);
      }

      ClusterManager clusterManager = server.getClusterManager();
      clusterManager.flushExecutor();
      clusterManager.clear();
      Assert.assertTrue("server should be running!", server.isStarted());
      server.fail(true);

      if (sessions.length > 0) {
         // Wait to be informed of failure
         boolean ok = latch.await(10000, TimeUnit.MILLISECONDS);
         Assert.assertTrue("Failed to stop the server! Latch count is " + latch.getCount() + " out of " +
                              sessions.length, ok);
      }
   }

   public static void crashAndWaitForFailure(ActiveMQServer server, ServerLocator locator) throws Exception {
      ClientSessionFactory sf = locator.createSessionFactory();
      ClientSession session = sf.createSession();
      try {
         crashAndWaitForFailure(server, session);
      } finally {
         try {
            session.close();
            sf.close();
         } catch (Exception ignored) {
         }
      }
   }

   public interface RunnerWithEX {

      void run() throws Throwable;
   }

   // This can be used to interrupt a thread if it takes more than timeoutMilliseconds
   public boolean runWithTimeout(final RunnerWithEX runner, final long timeoutMilliseconds) throws Throwable {

      class ThreadRunner extends Thread {

         Throwable t;

         final RunnerWithEX run;

         ThreadRunner(RunnerWithEX run) {
            this.run = run;
         }

         @Override
         public void run() {
            try {
               runner.run();
            } catch (Throwable t) {
               this.t = t;
            }
         }
      }

      ThreadRunner runnerThread = new ThreadRunner(runner);

      runnerThread.start();

      boolean hadToInterrupt = false;
      while (runnerThread.isAlive()) {
         runnerThread.join(timeoutMilliseconds);
         if (runnerThread.isAlive()) {
            System.err.println("Thread still running, interrupting it now:");
            for (Object t : runnerThread.getStackTrace()) {
               System.err.println(t);
            }
            hadToInterrupt = true;
            runnerThread.interrupt();
         }
      }

      if (runnerThread.t != null) {
         runnerThread.t.printStackTrace();
         throw runnerThread.t;
      }

      // we are returning true if it ran ok.
      // had to Interrupt is exactly the opposite of what we are returning
      return !hadToInterrupt;
   }

   protected static ReplicationEndpoint getReplicationEndpoint(ActiveMQServer server) {
      final Activation activation = server.getActivation();
      if (activation instanceof SharedNothingBackupActivation) {
         return ((SharedNothingBackupActivation) activation).getReplicationEndpoint();
      }
      if (activation instanceof ReplicationBackupActivation) {
         return ((ReplicationBackupActivation) activation).getReplicationEndpoint();
      }
      return null;
   }



   protected interface ActiveMQAction {

      void run() throws Exception;
   }

   /**
    * Asserts that latch completes within a (rather large interval).
    * <p>
    * Use this instead of just calling {@code latch.await()}. Otherwise your test may hang the whole
    * test run if it fails to count-down the latch.
    *
    * @param latch
    * @throws InterruptedException
    */
   public static void waitForLatch(CountDownLatch latch) throws InterruptedException {
      assertTrue("Latch has got to return within a minute", latch.await(1, TimeUnit.MINUTES));
   }
}
