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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMRegistry;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.impl.PrimaryOnlyActivation;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.tests.extensions.LibaioContextCheckExtension;
import org.apache.activemq.artemis.tests.extensions.PortCheckExtension;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.Wait;
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
public abstract class ServerTestBase extends ArtemisTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static {
      Env.setTestEnv(true);
   }

   @RegisterExtension
   public static PortCheckExtension portCheckExtension = new PortCheckExtension(61616);

   public static final String TARGET_TMP = "./target/tmp";
   public static final String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();
   public static final String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();
   public static final String CLUSTER_PASSWORD = "UnitTestsClusterPassword";

   // There is a verification about thread leakages. We only fail a single thread when this happens
   private static Set<Thread> alreadyFailedThread = new HashSet<>();

   protected void clearServers() {
      servers.clear();
   }

   private final Collection<ActiveMQServer> servers = new ArrayList<>();

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   // Cleans itself, but ./target/tmp/ deleted below as well.
   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   // This Extension will remove any files under ./target/tmp
   @RegisterExtension
   public RemoveDirectoryExtension removeDirectory = new RemoveDirectoryExtension(TargetTempDirFactory.TARGET_TMP);


   public ServerTestBase() {

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

      List<ClientSessionFactory> csfs;
      try {
         csfs = checkCsfStopped();
      } finally {
         cleanupPools();
      }

      InVMConnector.resetThreadPool();

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

      if (InVMRegistry.instance.size() > 0) {
         fail("InVMREgistry size > 0");
      }
   }

   @BeforeEach
   public void setUp() throws Exception {
      clearDataRecreateServerDirs();
      OperationContextImpl.clearContext();

      InVMRegistry.instance.clear();
   }

   protected String getName() {
      return name;
   }

   protected Configuration createDefaultInVMConfig() throws Exception {
      return createDefaultConfig(0, false);
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

   protected ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = new ConfigurationImpl().setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(getDefaultJournalType()).setJournalDirectory(getJournalDir(serverID, false)).setBindingsDirectory(getBindingsDir(serverID, false)).setPagingDirectory(getPageDir(serverID, false)).setLargeMessagesDirectory(getLargeMessagesDir(serverID, false)).setJournalCompactMinFiles(0).setJournalCompactPercentage(0).setClusterPassword(CLUSTER_PASSWORD).setJournalDatasync(false);

      // When it comes to the testsuite, we don't need any batching, I will leave some minimal batching to exercise the codebase
      configuration.setJournalBufferTimeout_AIO(100).setJournalBufferTimeout_NIO(100);

      return configuration;
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

   public static JournalType getDefaultJournalType() {
      if (AIOSequentialFileFactory.isSupported()) {
         return JournalType.ASYNCIO;
      } else {
         return JournalType.NIO;
      }
   }

   public static String threadDump(final String msg) {
      return ThreadDumpUtil.threadDump(msg);
   }

   protected final String getTestDir() {
      return temporaryFolder.getAbsolutePath();
   }

   protected final File getTestDirfile() {
      return new File(getTestDir());
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
      if (index == -1) {
         return "";
      }
      return index + "-" + (backup ? "B" : "L");
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

   protected final ActiveMQServer createServer(final boolean realFiles) throws Exception {
      return createServer(realFiles, createDefaultConfig(0, false), AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES, -1, -1);
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

   protected static final void stopComponent(ActiveMQComponent component) {
      if (component == null) {
         return;
      }
      try {
         component.stop();
      } catch (Exception e) {
         // no-op
      }
   }

   protected static final void stopComponentOutputExceptions(ActiveMQComponent component) {
      if (component == null) {
         return;
      }
      try {
         component.stop();
      } catch (Exception e) {
         System.err.println("Exception closing " + component);
         e.printStackTrace();
      }
   }

   protected final ActiveMQServer addServer(final ActiveMQServer server) {
      if (server != null) {
         synchronized (servers) {
            servers.add(server);
         }
      }
      return server;
   }
}
