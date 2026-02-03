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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.JsonUtil;
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
import org.apache.activemq.artemis.core.io.IOCallback;
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
import org.apache.activemq.artemis.core.persistence.OperationContext;
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
import org.apache.activemq.artemis.core.server.impl.PrimaryOnlyActivation;
import org.apache.activemq.artemis.core.server.impl.ReplicationBackupActivation;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.extensions.LibaioContextCheckExtension;
import org.apache.activemq.artemis.tests.extensions.PortCheckExtension;
import org.apache.activemq.artemis.tests.extensions.RemoveDirectoryExtension;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SimpleFutureImpl;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Base class with basic utilities on starting up a basic server
 */
@ExtendWith(LibaioContextCheckExtension.class)
public abstract class ActiveMQTestBase extends ArtemisTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static {
      Env.setTestEnv(true);
   }

   @RegisterExtension
   public static PortCheckExtension portCheckExtension = new PortCheckExtension(61616);

   public static final String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();
   public static final String INVM_CONNECTOR_FACTORY = InVMConnectorFactory.class.getCanonicalName();
   public static final String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();
   public static final String NETTY_CONNECTOR_FACTORY = NettyConnectorFactory.class.getCanonicalName();
   public static final String CLUSTER_PASSWORD = "UnitTestsClusterPassword";

   /**
    * Add a "sendCallNumber" property to messages sent using helper classes. Meant to help in debugging.
    */
   private static final String SEND_CALL_NUMBER = "sendCallNumber";
   private static final String OS_TYPE = System.getProperty("os.name").toLowerCase();
   private static final int DEFAULT_UDP_PORT;

   protected static final long WAIT_TIMEOUT = 30000;

   // There is a verification about thread leakages. We only fail a single thread when this happens
   private static Set<Thread> alreadyFailedThread = new HashSet<>();

   protected void clearServers() {
      servers.clear();
   }

   private final Collection<ActiveMQServer> servers = new ArrayList<>();
   private final Collection<ServerLocator> locators = new ArrayList<>();
   private final Collection<ClientSessionFactory> sessionFactories = new ArrayList<>();
   private final Collection<ClientSession> clientSessions = new HashSet<>();
   private final Collection<ClientConsumer> clientConsumers = new HashSet<>();
   private final Collection<ClientProducer> clientProducers = new HashSet<>();
   private final Collection<ActiveMQComponent> otherComponents = new HashSet<>();
   private final Set<ExecutorService> executorSet = new HashSet<>();

   private int sendMsgCount = 0;

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   // Cleans itself, but ./target/tmp/ deleted below as well.
   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   // This Extension will remove any files under ./target/tmp
   @RegisterExtension
   public RemoveDirectoryExtension removeDirectory = new RemoveDirectoryExtension(TargetTempDirFactory.TARGET_TMP);

   static {
      Random random = new Random();
      DEFAULT_UDP_PORT = 6000 + random.nextInt(1000);
   }

   public ActiveMQTestBase() {

   }

   protected static String randomProtocol() {
      return randomProtocol("AMQP", "OPENWIRE", "CORE");
   }

   protected static String randomProtocol(String...protocols) {
      String protocol = protocols[RandomUtil.randomPositiveInt() % protocols.length];
      logger.info("Selecting {} protocol", protocol);
      return protocol;
   }

   protected <T> T serialClone(Object object) throws Exception {
      logger.debug("object::{}", object);
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream obOut = new ObjectOutputStream(bout);
      obOut.writeObject(object);

      ByteArrayInputStream binput = new ByteArrayInputStream(bout.toByteArray());
      ObjectInputStream obinp = new ObjectInputStream(binput);
      return (T) obinp.readObject();

   }
   @AfterEach
   public void tearDown() throws Exception {
      try {
         closeAllSessionFactories();
         closeAllServerLocatorsFactories();

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
                  ((PrimaryOnlyActivation) server.getActivation()).getPrimaryOnlyPolicy().getScaleDownPolicy().setEnabled(false);
               } catch (Throwable ignored) {
                  // don't care about anything here
                  // if can't find activation, primaryPolicy or no PrimaryOnlyActivation... don't care!!!
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
            clearServers();
         }

         closeAllOtherComponents();

         List<ClientSessionFactory> csfs;
         try {
            csfs = checkCsfStopped();
         } finally {
            cleanupPools();
         }

         for (ExecutorService s : executorSet) {
            s.shutdown();
         }
         InVMConnector.resetThreadPool();
         assertAllExecutorsFinished();

         //clean up pools before failing
         if (!csfs.isEmpty()) {
            System.out.println(csfs.size() + " ClientSessionFactories still running:");
            for (ClientSessionFactory csf : csfs) {
               System.out.println("\t" + csf.toString());
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

   @BeforeEach
   public void setUp() throws Exception {
      sendMsgCount = 0;
      clearDataRecreateServerDirs();
      OperationContextImpl.clearContext();

      InVMRegistry.instance.clear();
   }

   public static void assertEqualsByteArrays(final byte[] expected, final byte[] actual) {
      for (int i = 0; i < expected.length; i++) {
         assertEquals(expected[i], actual[i], "byte at index " + i);
      }
   }

   public static int countOccurrencesOf(String str, String sub) {
      if (str == null || sub == null || str.isEmpty() || sub.isEmpty()) {
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

   /*
    ***********************************************************************************************************************************
    * Behold, thou shalt never employ this method, unless thou dost isolate thine test on its own, separate and distinct house.       *
    ***********************************************************************************************************************************
    *
    * i.e: DON'T USE IT UNLESS YOU HAVE A SEPARATE VM FOR YOUR TEST.
    */
   protected void disableCheckThread() {
      threadLeakCheckExtension.disable();
   }

   protected String getName() {
      return name;
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
         configuration.addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, new HashMap<>(), "netty", new HashMap<>()));
      } else {
         // if we're in-vm it's a waste to resolve protocols since they'll never be used
         configuration.setResolveProtocols(false);
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

   protected ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = new ConfigurationImpl().setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(getDefaultJournalType()).setJournalDirectory(getJournalDir(serverID, false)).setBindingsDirectory(getBindingsDir(serverID, false)).setPagingDirectory(getPageDir(serverID, false)).setLargeMessagesDirectory(getLargeMessagesDir(serverID, false)).setJournalCompactMinFiles(0).setJournalCompactPercentage(0).setClusterPassword(CLUSTER_PASSWORD).setJournalDatasync(false).setPageSyncTimeout(100);

      // When it comes to the testsuite, we don't need any batching, I will leave some minimal batching to exercise the codebase
      configuration.setJournalBufferTimeout_AIO(100).setJournalBufferTimeout_NIO(100);

      // protect programmatic config before start, a common pattern that is only good for the current default which is 5s
      configuration.setConfigurationFileRefreshPeriod(-1);

      return configuration;
   }

   protected void setDBStoreType(Configuration configuration) {
      configuration.setStoreConfiguration(createDefaultDatabaseStorageConfiguration());
   }

   private boolean derbyDropped = false;

   protected void dropDerby() throws Exception {
      DBSupportUtil.dropDerbyDatabase(getJDBCUser(), getJDBCPassword(), getEmbeddedDataBaseName());
   }

   protected void shutdownDerby() throws SQLException {
      DBSupportUtil.shutdownDerby(getJDBCUser(), getJDBCPassword());
   }

   protected DatabaseStorageConfiguration createDefaultDatabaseStorageConfiguration() {
      DatabaseStorageConfiguration dbStorageConfiguration = new DatabaseStorageConfiguration();
      String connectionURI = getTestJDBCConnectionUrl();

      /*
       * The connectionURI could be passed into the testsuite as a system property (say you are testing against Oracle).
       *  So, we only schedule the drop on Derby if we are using a derby memory database
       */
      if (connectionURI.contains("derby") && connectionURI.contains("memory") && !derbyDropped) {
         // some tests will reinitialize the server and call this method more than one time
         // and we should only schedule one task
         derbyDropped = true;
         runAfterEx(this::dropDerby);
         runAfterEx(this::shutdownDerby);
      }
      dbStorageConfiguration.setJdbcConnectionUrl(connectionURI);
      dbStorageConfiguration.setBindingsTableName("BINDINGS");
      dbStorageConfiguration.setMessageTableName("MESSAGE");
      dbStorageConfiguration.setLargeMessageTableName("LARGE_MESSAGE");
      dbStorageConfiguration.setPageStoreTableName("PAGE_STORE");
      dbStorageConfiguration.setJdbcPassword(getJDBCPassword());
      dbStorageConfiguration.setJdbcUser(getJDBCUser());
      dbStorageConfiguration.setJdbcDriverClassName(getJDBCClassName());
      dbStorageConfiguration.setJdbcLockAcquisitionTimeoutMillis(getJdbcLockAcquisitionTimeoutMillis());
      dbStorageConfiguration.setJdbcLockExpirationMillis(getJdbcLockExpirationMillis());
      dbStorageConfiguration.setJdbcLockRenewPeriodMillis(getJdbcLockRenewPeriodMillis());
      dbStorageConfiguration.setJdbcNetworkTimeout(-1);
      dbStorageConfiguration.setJdbcAllowedTimeDiff(250L);
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

   protected Map<String, Object> generateInVMParams(final int node) {
      Map<String, Object> params = new HashMap<>();

      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, node);

      return params;
   }


   /**
    * This exists as an extension point for tests, so tests can replace it
    */
   protected ClusterConnectionConfiguration createBasicClusterConfig(String connectorName,
                                                                                      String... connectors) {
      return basicClusterConnectionConfig(connectorName, connectors);
   }


   protected static final ClusterConnectionConfiguration basicClusterConnectionConfig(String connectorName,
                                                                                      String... connectors) {
      List<String> connectors0 = new ArrayList<>();
      for (String c : connectors) {
         connectors0.add(c);
      }
      ClusterConnectionConfiguration clusterConnectionConfiguration = new ClusterConnectionConfiguration().
         setName("cluster1").setAddress("jms").setConnectorName(connectorName).
         setRetryInterval(100).setDuplicateDetection(false).setMaxHops(1).
         setConfirmationWindowSize(1).setMessageLoadBalancingType(MessageLoadBalancingType.STRICT).
         setStaticConnectors(connectors0).setCallTimeout(1000).setCallFailoverTimeout(1000);

      return clusterConnectionConfiguration;
   }

   protected final OrderedExecutorFactory getOrderedExecutor() {
      final ExecutorService executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      runAfter(executor::shutdownNow);
      return new OrderedExecutorFactory(executor);
   }

   protected static String getUDPDiscoveryAddress() {
      return System.getProperty("TEST-UDP-ADDRESS", "230.1.2.3");
   }

   protected static String getUDPDiscoveryAddress(final int variant) {
      String value = getUDPDiscoveryAddress();

      int posPoint = value.lastIndexOf('.');

      int last = Integer.parseInt(value.substring(posPoint + 1));

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

   /**
    * Verifies whether weak references are released after a few GCs.
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
         assertNull(ref.get());
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
      logger.debug(message, e);
   }

   /**
    * Sends the message to both logger and System.out (for unit report)
    */
   public void logAndSystemOut(String message) {
      logAndSystemOut(message, null);
   }

   public static String dumpBytes(final byte[] bytes) {
      StringBuilder builder = new StringBuilder();

      builder.append(System.identityHashCode(bytes) + ", size: " + bytes.length + " [");

      for (int i = 0; i < bytes.length; i++) {
         builder.append(bytes[i]);

         if (i != bytes.length - 1) {
            builder.append(", ");
         }
      }

      builder.append("]");

      return builder.toString();
   }

   public static String dumpBytesHex(final byte[] buffer, final int bytesPerLine) {

      StringBuilder builder = new StringBuilder();

      builder.append("[");

      for (int i = 0; i < buffer.length; i++) {
         builder.append(String.format("%1$2X", buffer[i]));
         if (i + 1 < buffer.length) {
            builder.append(", ");
         }
         if ((i + 1) % bytesPerLine == 0) {
            builder.append("\n ");
         }
      }
      builder.append("]");

      return builder.toString();
   }

   public static void assertEqualsTransportConfigurations(final TransportConfiguration[] expected,
                                                          final TransportConfiguration[] actual) {
      assertEquals(expected.length, actual.length);
      for (int i = 0; i < expected.length; i++) {
         assertEquals(expected[i], actual[i], "TransportConfiguration at index " + i);
      }
   }

   public static void assertEqualsBuffers(final int size, final ActiveMQBuffer expected, final ActiveMQBuffer actual) {
      // assertEquals(expected.length, actual.length);
      expected.readerIndex(0);
      actual.readerIndex(0);

      for (int i = 0; i < size; i++) {
         byte b1 = expected.readByte();
         byte b2 = actual.readByte();
         assertEquals(b1, b2, "byte at index " + i);
      }
      expected.resetReaderIndex();
      actual.resetReaderIndex();
   }

   public static void assertEqualsByteArrays(final int length, final byte[] expected, final byte[] actual) {
      // we check only for the given length (the arrays might be
      // larger)
      assertTrue(expected.length >= length);
      assertTrue(actual.length >= length);
      for (int i = 0; i < length; i++) {
         assertEquals(expected[i], actual[i], "byte at index " + i);
      }
   }

   public static void assertSameXids(final List<Xid> expected, final List<Xid> actual) {
      assertNotNull(expected);
      assertNotNull(actual);
      assertEquals(expected.size(), actual.size());

      for (int i = 0; i < expected.size(); i++) {
         Xid expectedXid = expected.get(i);
         Xid actualXid = actual.get(i);
         assertEqualsByteArrays(expectedXid.getBranchQualifier(), actualXid.getBranchQualifier());
         assertEquals(expectedXid.getFormatId(), actualXid.getFormatId());
         assertEqualsByteArrays(expectedXid.getGlobalTransactionId(), actualXid.getGlobalTransactionId());
      }
   }

   protected static void checkNoBinding(final Context context, final String binding) {
      try {
         context.lookup(binding);
         fail("there must be no resource to look up for " + binding);
      } catch (Exception e) {
      }
   }

   protected static Object checkBinding(final Context context, final String binding) throws Exception {
      Object o = context.lookup(binding);
      assertNotNull(o);
      return o;
   }

   protected List<String> registerConnectors(final ActiveMQServer server,
                                                  final List<TransportConfiguration> connectorConfigs) {
      // The connectors need to be pre-configured at main config object but this method is taking
      // TransportConfigurations directly
      // So this will first register them at the config and then generate a list of objects
      List<String> connectors = new ArrayList<>();
      for (TransportConfiguration tnsp : connectorConfigs) {
         String name1 = RandomUtil.randomUUIDString();

         server.getConfiguration().getConnectorConfigurations().put(name1, tnsp);

         connectors.add(name1);
      }
      return connectors;
   }

   protected final String getTestDir() {
      return temporaryFolder.getAbsolutePath();
   }

   protected String getEmbeddedDataBaseName() {
      return "memory:" + getTestDir();
   }

   protected String getTestJDBCConnectionUrl() {
      return System.getProperty("jdbc.connection.url", "jdbc:derby:" + getEmbeddedDataBaseName() + ";create=true");
   }

   protected String getJDBCClassName() {
      return System.getProperty("jdbc.driver.class", "org.apache.derby.jdbc.EmbeddedDriver");
   }

   protected String getJDBCUser() {
      return System.getProperty("jdbc.user", null);
   }

   protected String getJDBCPassword() {
      return System.getProperty("jdbc.password", null);
   }

   protected final File getTestDirfile() {
      return new File(getTestDir());
   }

   protected final void setTestDir(String testDir) {
      // Used directly by some tests that execute a test class Main but still use the 'test dir' otherwise set by JUnit.
      this.temporaryFolder = new File(testDir);
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

   protected String getBindingsDir() {
      return getBindingsDir(0, false);
   }

   protected static String getBindingsDir(final String testDir1) {
      return testDir1 + "/bindings";
   }

   protected String getBindingsDir(final int index, final boolean backup) {
      return getBindingsDir(getTestDir(), index, backup);
   }

   public static String getBindingsDir(final String testDir, final int index, final boolean backup) {
      return getBindingsDir(testDir) + directoryNameSuffix(index, backup);
   }

   protected String getPageDir() {
      return getPageDir(0, false);
   }

   protected File getPageDirFile() {
      return new File(getPageDir());

   }

   protected static String getPageDir(final String testDir1) {
      return testDir1 + "/page";
   }

   protected String getPageDir(final int index, final boolean backup) {
      return getPageDir(getTestDir(), index, backup);
   }

   public static String getPageDir(final String testDir, final int index, final boolean backup) {
      return getPageDir(testDir) + directoryNameSuffix(index, backup);
   }

   protected String getLargeMessagesDir() {
      return getLargeMessagesDir(0, false);
   }

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

   protected String getClientLargeMessagesDir() {
      return getClientLargeMessagesDir(getTestDir());
   }

   protected String getClientLargeMessagesDir(final String testDir1) {
      return testDir1 + "/client-large-msg";
   }

   protected final String getTemporaryDir() {
      return getTemporaryDir(getTestDir());
   }

   protected String getTemporaryDir(final String testDir1) {
      return testDir1 + "/temp";
   }

   protected static void expectActiveMQException(final String message,
                                                 final ActiveMQExceptionType errorCode,
                                                 final ActiveMQAction action) {
      try {
         action.run();
         fail(message);
      } catch (Exception e) {
         assertInstanceOf(ActiveMQException.class, e);
         assertEquals(errorCode, ((ActiveMQException) e).getType());
      }
   }

   protected static void expectActiveMQException(final ActiveMQExceptionType errorCode, final ActiveMQAction action) {
      expectActiveMQException("must throw an ActiveMQException with the expected errorCode: " + errorCode, errorCode, action);
   }

   protected static void expectXAException(final int errorCode, final ActiveMQAction action) {
      try {
         action.run();
         fail("must throw a XAException with the expected errorCode: " + errorCode);
      } catch (Exception e) {
         assertInstanceOf(XAException.class, e);
         assertEquals(errorCode, ((XAException) e).errorCode);
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
    * It validates a Bean (POJO) using simple setters and getters with random values. You can pass a list of properties
    * to be ignored, as some properties will have a pre-defined domain (not being possible to use random-values on
    * them)
    */
   protected void validateGettersAndSetters(final Object pojo, final String... ignoredProperties) throws Exception {
      Set<String> ignoreSet = new HashSet<>();

      for (String ignore : ignoredProperties) {
         ignoreSet.add(ignore);
      }

      BeanInfo info = Introspector.getBeanInfo(pojo.getClass());

      PropertyDescriptor[] properties = info.getPropertyDescriptors();

      for (PropertyDescriptor prop : properties) {
         Object value;

         if (prop.getPropertyType() == String.class) {
            value = RandomUtil.randomUUIDString();
         } else if (prop.getPropertyType() == Integer.class || prop.getPropertyType() == Integer.TYPE) {
            value = RandomUtil.randomInt();
         } else if (prop.getPropertyType() == Long.class || prop.getPropertyType() == Long.TYPE) {
            value = RandomUtil.randomLong();
         } else if (prop.getPropertyType() == Boolean.class || prop.getPropertyType() == Boolean.TYPE) {
            value = RandomUtil.randomBoolean();
         } else if (prop.getPropertyType() == Double.class || prop.getPropertyType() == Double.TYPE) {
            value = RandomUtil.randomDouble();
         } else {
            logger.debug("Can't validate property of type {} on {}", prop.getPropertyType(), prop.getName());
            value = null;
         }

         if (value != null && prop.getWriteMethod() != null && prop.getReadMethod() == null) {
            logger.debug("WriteOnly property {} on {}", prop.getName(), pojo.getClass());
         } else if (value != null && prop.getWriteMethod() != null &&
            prop.getReadMethod() != null &&
            !ignoreSet.contains(prop.getName())) {
            logger.debug("Validating {} type = {}", prop.getName(), prop.getPropertyType());
            prop.getWriteMethod().invoke(pojo, value);

            assertEquals(value, prop.getReadMethod().invoke(pojo), "Property " + prop.getName());
         }
      }
   }

   protected void waitForNotPaging(Queue queue) throws InterruptedException {
      waitForNotPaging(queue.getPagingStore());
   }

   protected void waitForNotPaging(PagingStore store) throws InterruptedException {
      Wait.assertFalse("Store is still paging", store::isPaging, 20_000);
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
                                             final int primaryNodes,
                                             final int backupNodes,
                                             final long timeout) throws Exception {
      logger.debug("waiting for {} on the topology for server = {}", primaryNodes, server);

      Set<ClusterConnection> ccs = server.getClusterManager().getClusterConnections();

      if (ccs.size() != 1) {
         throw new IllegalStateException("You need a single cluster connection on this version of waitForTopology on ServiceTestBase");
      }

      Topology topology = server.getClusterManager().getDefaultConnection(null).getTopology();

      return waitForTopology(topology, timeout, primaryNodes, backupNodes);
   }

   protected static Topology waitForTopology(Topology topology,
                                             long timeout,
                                             int primaryNodes,
                                             int backupNodes) throws Exception {
      final long start = System.currentTimeMillis();

      int primaryNodesCount = 0;
      int backupNodesCount = 0;

      do {
         primaryNodesCount = 0;
         backupNodesCount = 0;

         for (TopologyMemberImpl member : topology.getMembers()) {
            if (member.getPrimary() != null) {
               primaryNodesCount++;
            }
            if (member.getBackup() != null) {
               backupNodesCount++;
            }
         }

         if ((primaryNodes == -1 || primaryNodes == primaryNodesCount) && (backupNodes == -1 || backupNodes == backupNodesCount)) {
            return topology;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      String msg = "Timed out waiting for cluster topology of live=" + primaryNodes + ",backup=" + backupNodes +
         " (received live=" + primaryNodesCount + ", backup=" + backupNodesCount +
         ") topology = " +
         topology.describe() +
         ")";

      logger.error(msg);

      throw new Exception(msg);
   }

   protected void waitForTopology(final ActiveMQServer server,
                                  String clusterConnectionName,
                                  final int nodes,
                                  final long timeout) throws Exception {
      logger.debug("waiting for {} on the topology for server = {}", nodes, server);

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

      logger.error(msg);

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

   protected static final void waitForBridges(final ActiveMQServer server, int connectedBridges) throws Exception {
      waitForBridges(server.getClusterManager().getDefaultConnection(null), connectedBridges);
   }

   protected static final void waitForBridges(ClusterConnection clusterConnection, int connectedBridges) throws Exception {
      Wait.assertTrue(() -> Arrays.stream(clusterConnection.getBridges())
         .filter(bridge -> bridge.isConnected()).count() >= connectedBridges);
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

   protected TransportConfiguration createTransportConfiguration(boolean netty,
                                                                              boolean acceptor,
                                                                              Map<String, Object> params) {
      return createTransportConfiguration(UUIDGenerator.getInstance().generateStringUUID(), netty, acceptor, params);
   }

   protected TransportConfiguration createTransportConfiguration(String name,
                                                                              boolean netty,
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
      return new TransportConfiguration(className, params, name, new HashMap<String, Object>());
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
         logger.info(threadDump("Server didn't start"));
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
         logger.info(threadDump("Server didn't start"));
         fail("Server didn't start: " + server);
      }
   }

   public static final void waitForRemoteBackupSynchronization(final ActiveMQServer backup) {
      waitForRemoteBackup(null, 20, true, backup);
   }

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

   protected final ActiveMQServer createServer(final boolean realFiles,
                                         final Configuration configuration,
                                         final int pageSize,
                                         final long maxAddressSize) {
      return createServer(realFiles, configuration, pageSize, maxAddressSize, (Map<String, AddressSettings>) null);
   }

   protected final ActiveMQServer createServer(final boolean realFiles,
                                         final Configuration configuration,
                                         final int pageSize,
                                         final long maxAddressSize,
                                         final int maxReadMessages,
                                         final int maxReadBytes) {
      return createServer(realFiles, configuration, pageSize, maxAddressSize, maxReadMessages, maxReadBytes, (Map<String, AddressSettings>) null);
   }

   protected final ActiveMQServer createServer(final boolean realFiles,
                                         final Configuration configuration,
                                         final int pageSize,
                                         final long maxAddressSize,
                                         final Map<String, AddressSettings> settings) {

      return createServer(realFiles, configuration, pageSize, maxAddressSize, null, null, settings);
   }



   protected final ActiveMQServer createServer(final boolean realFiles,
                                               final Configuration configuration,
                                               final int pageSize,
                                               final long maxAddressSize,
                                               final Integer maxReadPageMessages,
                                               final Integer maxReadPageBytes,
                                               final Map<String, AddressSettings> settings) {
      return  createServer(realFiles, configuration, pageSize, maxAddressSize, maxReadPageMessages, maxReadPageBytes, null, null, null, settings);

   }

   protected final ActiveMQServer createServer(final boolean realFiles,
                                         final Configuration configuration,
                                         final int pageSize,
                                         final long maxAddressSize,
                                         final Integer maxReadPageMessages,
                                         final Integer maxReadPageBytes,
                                         final Long pageLimitBytes,
                                         final Long pageLimitMessages,
                                         final String pageLimitPolicy,
                                         final Map<String, AddressSettings> settings) {
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration, realFiles));

      if (settings != null) {
         for (Map.Entry<String, AddressSettings> setting : settings.entrySet()) {
            if (maxReadPageBytes != null) {
               setting.getValue().setMaxReadPageBytes(maxReadPageBytes.intValue());
            }
            if (maxReadPageMessages != null) {
               setting.getValue().setMaxReadPageMessages(maxReadPageMessages.intValue());
            }
            if (pageLimitBytes != null) {
               setting.getValue().setPageLimitBytes(pageLimitBytes);
            }
            if (pageLimitMessages != null) {
               setting.getValue().setPageLimitMessages(pageLimitMessages);
            }
            if (pageLimitPolicy != null) {
               setting.getValue().setPageFullMessagePolicy(PageFullMessagePolicy.valueOf(pageLimitPolicy));
            }
            server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
         }
      }

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(pageSize).setMaxSizeBytes(maxAddressSize).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      if (maxReadPageBytes != null) {
         defaultSetting.setMaxReadPageBytes(maxReadPageBytes.intValue());
      }
      if (maxReadPageMessages != null) {
         defaultSetting.setMaxReadPageMessages(maxReadPageMessages.intValue());
      }
      if (pageLimitBytes != null) {
         defaultSetting.setPageLimitBytes(pageLimitBytes);
      }
      if (pageLimitMessages != null) {
         defaultSetting.setPageLimitMessages(pageLimitMessages);
      }
      if (pageLimitPolicy != null) {
         defaultSetting.setPageFullMessagePolicy(PageFullMessagePolicy.valueOf(pageLimitPolicy));
      }

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      applySettings(server, configuration, pageSize, maxAddressSize, maxReadPageMessages, maxReadPageBytes, settings);

      return server;
   }

   protected void applySettings(ActiveMQServer server,
                                final Configuration configuration,
                                final int pageSize,
                                final long maxAddressSize,
                                final Integer maxReadPageMessages,
                                final Integer maxReadPageBytes,
                                final Map<String, AddressSettings> settings) {
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
      return createServer(realFiles, configuration, pageSize, maxAddressSize, -1, -1, settings);
   }

   protected final ActiveMQServer createServer(final boolean realFiles) throws Exception {
      return createServer(realFiles, false);
   }

   protected final ActiveMQServer createServer(final boolean realFiles, final boolean netty) throws Exception {
      return createServer(realFiles, createDefaultConfig(netty), AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES, -1, -1);
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
         defaultSetting.setMaxSizeBytes(maxAddressSize).setMaxReadPageBytes(-1).setMaxSizeBytes(-1);

         server.getAddressSettingsRepository().addMatch("#", defaultSetting);

         return server;
      } finally {
         addServer(server);
      }
   }

   protected ActiveMQServer createColocatedInVMFailoverServer(final boolean realFiles,
                                                              final Configuration configuration,
                                                              NodeManager primaryNodeManager,
                                                              NodeManager backupNodeManager,
                                                              final int id) {
      return createColocatedInVMFailoverServer(realFiles, configuration, -1, -1, new HashMap<>(), primaryNodeManager, backupNodeManager, id);
   }

   protected ActiveMQServer createColocatedInVMFailoverServer(final boolean realFiles,
                                                              final Configuration configuration,
                                                              final int pageSize,
                                                              final int maxAddressSize,
                                                              final Map<String, AddressSettings> settings,
                                                              NodeManager primaryNodeManager,
                                                              NodeManager backupNodeManager,
                                                              final int id) {
      ActiveMQServer server;
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      configuration.setPersistenceEnabled(realFiles);
      server = new ColocatedActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, primaryNodeManager, backupNodeManager);

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
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setAddress(queueName));
   }

   protected void createQueue(final String address, final String queue) throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = locator.createSessionFactory();
      ClientSession session = sf.createSession();
      try {
         session.createQueue(QueueConfiguration.of(queue).setAddress(address));
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

   protected void setBody(final int i, final ClientMessage message) {
      message.getBodyBuffer().writeString("message" + i);
   }

   protected void assertMessageBody(final int i, final ClientMessage message) {
      assertEquals("message" + i, message.getBodyBuffer().readString(), message.toString());
   }

   /**
    * Send durable messages with pre-specified body.
    */
   public final void sendMessages(ClientSession session,
                                  ClientProducer producer,
                                  int numMessages) throws ActiveMQException {
      for (int i = 0; i < numMessages; i++) {
         producer.send(createMessage(session, i, true));
      }
   }

   public List<String> sendMessageBatch(int batchSize,
                                        ClientSession session,
                                        SimpleString queueAddr) throws ActiveMQException {
      return sendMessageBatch(batchSize, 1024, session, queueAddr);
   }

   public ClientMessage createMessage(ClientSession session, int messageSize, int seq, List<String> collectIds) {
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeBytes(new byte[messageSize]);
      String id = UUID.randomUUID().toString();
      message.putStringProperty("id", id);
      message.putIntProperty("seq", seq); // this is to make the print-data easier to debug
      if (collectIds != null) {
         collectIds.add(id);
      }
      return message;
   }

   public List<String> sendMessageBatch(int batchSize,
                                        int messageSize,
                                        ClientSession session,
                                        SimpleString queueAddr) throws ActiveMQException {
      List<String> messageIds = new ArrayList<>();
      ClientProducer producer = session.createProducer(queueAddr);
      for (int i = 0; i < batchSize; i++) {
         ClientMessage message = createMessage(session, messageSize, i, messageIds);
         producer.send(message);
      }
      session.commit();

      return messageIds;
   }

   public boolean waitForMessages(Queue queue, int count, long timeout) throws Exception {
      long timeToWait = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < timeToWait) {
         if (queue.getMessageCount() >= count) {
            return true;
         }
         Thread.sleep(100);
      }
      return false;
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
         assertNotNull(message, "Expecting a message " + i);
         // sendCallNumber is just a debugging measure.
         Object prop = message.getObjectProperty(SEND_CALL_NUMBER);
         if (prop == null)
            prop = -1;
         final int actual = message.getIntProperty("counter");
         assertEquals(i, actual, "expected=" + i + ". Got: property['counter']=" + actual + " sendNumber=" + prop);
         assertMessageBody(i, message);
         if (ack)
            message.acknowledge();
      }
   }

   /**
    * Reads a journal system and returns a Pair of List of RecordInfo, independent of being deleted or not
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
    * Reads a journal system and returns a {@literal Map<Integer,AtomicInteger>} of recordTypes and the number of
    * records per type, independent of being deleted or not
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
    */
   protected HashMap<Integer, AtomicInteger> countJournalLivingRecords(Configuration config) throws Exception {
      return internalCountJournalLivingRecords(config, true);
   }

   /**
    * This method will load a journal and count the living records
    *
    * @param messageJournal if {@code true} counts MessageJournal, if false counts BindingsJournal
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

      private final Map<Integer, AtomicInteger> recordsType;

      private RecordTypeCounter(Map<Integer, AtomicInteger> recordsType) {
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
    * Block calling thread and wait for a specified number of bindings.
    *
    * @param server                the server where's being checked
    * @param address               the name of the address being checked
    * @param local                 if {@code true} we are looking for local bindings, false we are looking for remoting
    *                              servers
    * @param expectedBindingCount  the expected number of counts
    * @param expectedConsumerCount the expected number of consumers
    * @param timeout               the timeout used on the check
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

         Bindings bindings = po.getBindingsForAddress(SimpleString.of(address));

         for (Binding binding : bindings.getBindings()) {
            if (binding.isConnected() && (binding instanceof LocalQueueBinding && local || binding instanceof RemoteQueueBinding && !local)) {
               QueueBinding qBinding = (QueueBinding) binding;

               bindingCount++;

               totConsumers += qBinding.consumerCount();
            }
         }

         if (bindingCount == expectedBindingCount && (expectedConsumerCount == -1 || totConsumers == expectedConsumerCount)) {
            return true;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      logger.error("Timed out waiting for bindings (bindingCount = {} (expecting {}) , totConsumers = {} (expecting {}))",
                     bindingCount, expectedBindingCount, totConsumers, expectedConsumerCount);

      return false;
   }

   protected int getNumberOfFiles(File directory) {
      return directory.listFiles().length;
   }

   /**
    * Deleting a file on LargeDir is an asynchronous process. We need to keep looking for a while if the file hasn't
    * been deleted yet.
    */
   protected void validateNoFilesOnLargeDir(final String directory, final int expect) throws Exception {
      File largeMessagesFileDir = new File(directory);
      Wait.assertEquals(expect, () -> getNumberOfFiles(largeMessagesFileDir), 5000, 100, () -> "The following large message files remain: " + Arrays.toString(largeMessagesFileDir.listFiles()));
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while if the file hasn't
    * been deleted yet
    */
   protected void validateNoFilesOnLargeDir() throws Exception {
      validateNoFilesOnLargeDir(getLargeMessagesDir(), 0);
   }

   public void printBindings(ActiveMQServer server, String address) throws Exception {
      PostOffice po = server.getPostOffice();
      Bindings bindings = po.getBindingsForAddress(SimpleString.of(address));

      System.err.println("=======================================================================");
      System.err.println("Binding information for address = " + address + " for server " + server);

      for (Binding binding : bindings.getBindings()) {
         QueueBinding qBinding = (QueueBinding) binding;
         System.err.println("Binding = " + qBinding + ", queue=" + qBinding.getQueue());
      }

   }

   private void assertAllExecutorsFinished() throws InterruptedException {
      for (ExecutorService s : executorSet) {
         assertTrue(s.awaitTermination(5, TimeUnit.SECONDS));
      }
   }

   private List<ClientSessionFactory> checkCsfStopped() throws Exception {
      if (!Wait.waitFor(ClientSessionFactoryImpl.CLOSE_RUNNABLES::isEmpty, 5_000)) {
         List<ClientSessionFactoryImpl.CloseRunnable> closeRunnables = new ArrayList<>(ClientSessionFactoryImpl.CLOSE_RUNNABLES);
         List<ClientSessionFactory> csfs = new ArrayList<>();

         if (!closeRunnables.isEmpty()) {
            for (ClientSessionFactoryImpl.CloseRunnable closeRunnable : closeRunnables) {
               if (closeRunnable != null) {
                  csfs.add(closeRunnable.stop());
               }
            }
         }
         return csfs;
      }

      return Collections.emptyList();
   }

   private void assertAllClientProducersAreClosed() {
      synchronized (clientProducers) {
         for (ClientProducer p : clientProducers) {
            assertTrue(p.isClosed(), p + " should be closed");
         }
         clientProducers.clear();
      }
   }

   private void closeAllOtherComponents() {
      synchronized (otherComponents) {
         for (ActiveMQComponent c : otherComponents) {
            stopComponent(c);
         }
         otherComponents.clear();
      }
   }

   private void checkFilesUsage() throws Exception {
      int invmSize = InVMRegistry.instance.size();
      if (invmSize > 0) {
         InVMRegistry.instance.clear();
         logger.info(threadDump("Thread dump"));
         fail("invm registry still had acceptors registered");
      }
   }

   private void cleanupPools() {
      OperationContextImpl.clearContext();

      // We shutdown the global pools to give a better isolation between tests
      try {
         ServerLocatorImpl.clearThreadPools();
      } catch (Throwable e) {
         logger.info(threadDump(e.getMessage()));
         System.err.println(threadDump(e.getMessage()));
      }

      try {
         NettyConnector.clearThreadPools();
      } catch (Exception e) {
         logger.info(threadDump(e.getMessage()));
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
         fail("Lists different sizes: " + l1.size() + ", " + l2.size());
      }

      Iterator<MessageReference> iter1 = l1.iterator();
      Iterator<MessageReference> iter2 = l2.iterator();

      while (iter1.hasNext()) {
         MessageReference o1 = iter1.next();
         MessageReference o2 = iter2.next();

         assertSame(o1, o2, "expected " + o1 + " but was " + o2);
      }
   }

   protected Message generateMessage(final long id) {
      ICoreMessage message = new CoreMessage(id, 1000);

      message.setMessageID(id);

      message.getBodyBuffer().writeString(UUID.randomUUID().toString());

      message.setAddress(SimpleString.of("foo"));

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
      message.getBodyBuffer().writeString("a".repeat(numChars));
      return message;
   }

   protected XidImpl newXID() {
      return new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
   }

   protected int getMessageCount(final ActiveMQServer service, final String address) throws Exception {
      return getMessageCount(service.getPostOffice(), address);
   }

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
      try {
         Wait.waitFor(() -> queue.getPageSubscription().isCounterPending() == false);
      } catch (Exception ignored) {
      }
      queue.flushExecutor();
      return (int) queue.getMessageCount();
   }

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
      List<QueueBinding> bindingsFound = new ArrayList<>();

      Bindings bindings = postOffice.getBindingsForAddress(SimpleString.of(address));

      for (Binding binding : bindings.getBindings()) {
         if (binding instanceof LocalQueueBinding localQueueBinding) {
            bindingsFound.add(localQueueBinding);
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
    * Creates the Locator without adding it to the list where the tearDown will take place This is because we don't want
    * it closed in certain tests where we are issuing failures
    *
    * @return the locator
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
      assertTrue(server.isStarted(), "server should be running!");
      server.fail(true);

      if (sessions.length > 0) {
         // Wait to be informed of failure
         boolean ok = latch.await(10000, TimeUnit.MILLISECONDS);
         assertTrue(ok, "Failed to stop the server! Latch count is " + latch.getCount() + " out of " +
                              sessions.length);
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

   public String createJsonFilter(String fieldName, String operationName, String value) {
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put("field", fieldName);
      filterMap.put("operation", operationName);
      filterMap.put("value", value);
      JsonObject jsonFilterObject = JsonUtil.toJsonObject(filterMap);
      return jsonFilterObject.toString();
   }

   protected static ReplicationEndpoint getReplicationEndpoint(ActiveMQServer server) {
      final Activation activation = server.getActivation();
      if (activation instanceof SharedNothingBackupActivation backupActivation) {
         return backupActivation.getReplicationEndpoint();
      }
      if (activation instanceof ReplicationBackupActivation backupActivation) {
         return backupActivation.getReplicationEndpoint();
      }
      return null;
   }



   protected interface ActiveMQAction {

      void run() throws Exception;
   }

   /**
    * Asserts that latch completes within a (rather large interval).
    * <p>
    * Use this instead of just calling {@code latch.await()}. Otherwise your test may hang the whole test run if it
    * fails to count-down the latch.
    */
   public static void waitForLatch(CountDownLatch latch) throws InterruptedException {
      assertTrue(latch.await(1, TimeUnit.MINUTES), "Latch has got to return within a minute");
   }

   /** use the current OperationContext to sync any pending operations. */
   protected void syncOperationContext() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);
      OperationContext operationContext = OperationContextImpl.getContext();
      if (operationContext != null) {
         operationContext.executeOnCompletion(new IOCallback() {
            @Override
            public void done() {
               latch.countDown();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {

            }
         });

         assertTrue(latch.await(10, TimeUnit.SECONDS));
      }
   }

   protected <T> T callOnExecutor(Executor executor, Callable<T> callable, long time, TimeUnit unit) throws Exception {
      Object nullReturn = new Object();
      SimpleFutureImpl<Object> future = new SimpleFutureImpl<>();
      executor.execute(() -> {
         try {
            Object value = callable.call();
            if (value == null) {
               future.set(nullReturn);
            } else {
               future.set(value);
            }
         } catch (Exception e) {
            future.fail(e);
         }
      });

      Object futureGet = future.get(time, unit);

      assertNotNull(futureGet, "Timeout call");

      if (futureGet == nullReturn) {
         return null;
      } else {
         return (T) futureGet;
      }
   }
}
