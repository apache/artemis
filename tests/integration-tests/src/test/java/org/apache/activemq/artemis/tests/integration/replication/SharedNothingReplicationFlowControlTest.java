/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.replication;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.mapped.MappedSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFile;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SharedNothingReplicationFlowControlTest extends ActiveMQTestBase {

   ExecutorService sendMessageExecutor;

   @BeforeEach
   public void setupExecutor() {
      sendMessageExecutor = Executors.newCachedThreadPool();
   }

   @AfterEach
   public void teardownExecutor() {
      sendMessageExecutor.shutdownNow();
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File brokersFolder;

   @Test
   public void testReplicationIfFlowControlled() throws Exception {
      // start primary
      Configuration primaryConfiguration = createPrimaryConfiguration();
      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.start();

      Wait.waitFor(() -> primaryServer.isStarted());

      ServerLocator locator = ServerLocatorImpl.newLocator("tcp://localhost:61616");
      locator.setCallTimeout(60_000L);
      locator.setConnectionTTL(60_000L);

      final ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession sess = csf.createSession();
      sess.createQueue(QueueConfiguration.of("flowcontrol").setRoutingType(RoutingType.ANYCAST));
      sess.close();

      int i = 0;
      final int j = 100;
      final CountDownLatch allMessageSent = new CountDownLatch(j);

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.start();

      Wait.waitFor(() -> backupServer.isStarted());

      Wait.waitFor(backupServer::isReplicaSync, 30000);

      TestInterceptor interceptor = new TestInterceptor(30000);
      // Increase latency of handling packet on backup side and flow control would work
      backupServer.getClusterManager().getClusterController().addIncomingInterceptorForReplication(interceptor);

      byte[] body = new byte[32 * 1024];
      while (i < j) {
         sendMessageExecutor.execute(() -> {
            try {
               ClientSession session = csf.createSession(true, true);
               ClientProducer producer = session.createProducer("flowcontrol");
               ClientMessage message = session.createMessage(true);
               message.writeBodyBufferBytes(body);
               logger.debug("try to send a message after replicated");
               producer.send(message);
               logger.debug("send message done");
               producer.close();
               session.close();

               allMessageSent.countDown();
            } catch (ActiveMQException e) {
               logger.error("send message", e);
            }
         });
         i++;
      }

      assertTrue(allMessageSent.await(30, TimeUnit.SECONDS), "all message sent");
      interceptor.setSleepTime(0);

      csf.close();
      locator.close();
      assertTrue(Wait.waitFor(primaryServer::isReplicaSync, 30000), "Waiting for replica sync timeout");
      backupServer.stop(true);
      primaryServer.stop(true);

      SequentialFileFactory fileFactory;

      fileFactory = new MappedSequentialFileFactory(primaryConfiguration.getJournalLocation(), primaryConfiguration.getJournalFileSize(), false, primaryConfiguration.getJournalBufferSize_NIO(), primaryConfiguration.getJournalBufferTimeout_NIO(), null);

      JournalImpl primaryMessageJournal = new JournalImpl(primaryConfiguration.getJournalFileSize(), primaryConfiguration.getJournalMinFiles(), primaryConfiguration.getJournalPoolFiles(), primaryConfiguration.getJournalCompactMinFiles(), primaryConfiguration.getJournalCompactPercentage(), fileFactory, "activemq-data", "amq", fileFactory.getMaxIO());

      primaryMessageJournal.start();
      final AtomicInteger primaryJournalCounter = new AtomicInteger();
      primaryMessageJournal.load(new SharedNothingReplicationFlowControlTest.AddRecordLoaderCallback() {
         @Override
         public void addRecord(RecordInfo info) {
            if (!(info.userRecordType == JournalRecordIds.ADD_MESSAGE_PROTOCOL)) {
               // ignore
            }
            primaryJournalCounter.incrementAndGet();
            logger.info("got primary message {} {}, counter={}", info.id, info.userRecordType, primaryJournalCounter.get());
         }
      });

      // read backup's journal
      File backupJournalDir = brokersFolder.toPath().resolve("backup").resolve("data").resolve("journal").toFile();
      fileFactory = new MappedSequentialFileFactory(backupConfiguration.getJournalLocation(), backupConfiguration.getJournalFileSize(), false, backupConfiguration.getJournalBufferSize_NIO(), backupConfiguration.getJournalBufferTimeout_NIO(), null);

      JournalImpl backupMessageJournal = new JournalImpl(backupConfiguration.getJournalFileSize(), backupConfiguration.getJournalMinFiles(), backupConfiguration.getJournalPoolFiles(), backupConfiguration.getJournalCompactMinFiles(), backupConfiguration.getJournalCompactPercentage(), fileFactory, "activemq-data", "amq", fileFactory.getMaxIO());

      backupMessageJournal.start();

      final AtomicInteger replicationCounter = new AtomicInteger();
      backupMessageJournal.load(new SharedNothingReplicationFlowControlTest.AddRecordLoaderCallback() {
         @Override
         public void addRecord(RecordInfo info) {
            if (!(info.userRecordType == JournalRecordIds.ADD_MESSAGE_PROTOCOL)) {
               // ignore
            }
            replicationCounter.incrementAndGet();
            logger.info("replicated message {}, counter={}", info.id, replicationCounter.get());
         }
      });

      logger.info("expected {} messages, primary={}, backup={}", j, primaryJournalCounter.get(), replicationCounter.get());
      assertEquals(j, primaryJournalCounter.get(), "Primary lost journal record");
      assertEquals(j, replicationCounter.get(), "Backup did not replicated all journal");
   }

   @Test
   public void testSendPages() throws Exception {
      // start live
      Configuration liveConfiguration = createPrimaryConfiguration();
      ActiveMQServer liveServer = addServer(ActiveMQServers.newActiveMQServer(liveConfiguration));
      liveServer.start();

      Wait.waitFor(() -> liveServer.isStarted());

      ServerLocator locator = ServerLocatorImpl.newLocator("tcp://localhost:61616");
      locator.setCallTimeout(60_000L);
      locator.setConnectionTTL(60_000L);
      locator.setBlockOnDurableSend(false);

      final ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession sess = csf.createSession();
      sess.createQueue(QueueConfiguration.of("flowcontrol").setRoutingType(RoutingType.ANYCAST));

      PagingStore store = liveServer.getPagingManager().getPageStore(SimpleString.of("flowcontrol"));
      store.startPaging();

      ClientProducer prod = sess.createProducer("flowcontrol");
      for (int i = 0; i < 100; i++) {
         prod.send(sess.createMessage(true));

         if (i % 10 == 0) {
            sess.commit();
            store.forceAnotherPage(true);
         }
      }

      sess.close();

      TestableSequentialFile.openFiles.clear();
      // start backup
      Configuration backupConfiguration = createBackupConfiguration().setNetworkCheckURLList(null);

      ActiveMQServer backupServer = new ActiveMQServerImpl(backupConfiguration, ManagementFactory.getPlatformMBeanServer(), new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration())) {
         @Override
         public PagingManager createPagingManager() throws Exception {
            PagingManagerImpl manager = (PagingManagerImpl) super.createPagingManager();
            PagingStoreFactoryNIO originalPageStore = (PagingStoreFactoryNIO) manager.getPagingStoreFactory();
            manager.replacePageStoreFactory(new PageStoreFactoryTestable(originalPageStore));
            return manager;
         }
      };

      addServer(backupServer).start();

      Wait.waitFor(() -> backupServer.isStarted());

      Wait.waitFor(backupServer::isReplicaSync, 30000);

      // Asserting for file leaks
      if (!Wait.waitFor(() -> TestableSequentialFile.openFiles.isEmpty(), 5000)) {
         StringWriter writer = new StringWriter();
         PrintWriter print = new PrintWriter(writer);
         for (Object fileOpen : TestableSequentialFile.openFiles.keySet()) {
            print.println("File still open ::" + fileOpen);
         }
         fail(writer.toString());
      }
   }

   private static class PageStoreFactoryTestable extends PagingStoreFactoryNIO {

      PageStoreFactoryTestable(StorageManager storageManager,
                                      File directory,
                                      long syncTimeout,
                                      ScheduledExecutorService scheduledExecutor,
                                      ExecutorFactory executorFactory,
                                      boolean syncNonTransactional,
                                      IOCriticalErrorListener critialErrorListener) {
         super(storageManager, directory, syncTimeout, scheduledExecutor, executorFactory, syncNonTransactional, critialErrorListener);
      }

      PageStoreFactoryTestable(PagingStoreFactoryNIO other) {
         this(other.getStorageManager(), other.getDirectory(), other.getSyncTimeout(), other.getScheduledExecutor(), other.getExecutorFactory(), other.isSyncNonTransactional(), other.getCritialErrorListener());
      }

      @Override
      protected SequentialFileFactory newFileFactory(String directoryName) {
         return new TestableNIOFactory(new File(getDirectory(), directoryName), false, getCritialErrorListener(), 1);
      }

      private static File newFolder(File root, String... subDirs) throws IOException {
         String subFolder = String.join("/", subDirs);
         File result = new File(root, subFolder);
         if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
         }
         return result;
      }
   }

   public static class TestableNIOFactory extends NIOSequentialFileFactory {

      public TestableNIOFactory(File journalDir, boolean buffered, IOCriticalErrorListener listener, int maxIO) {
         super(journalDir, buffered, listener, maxIO);
      }

      @Override
      public SequentialFile createSequentialFile(String fileName) {
         return new TestableSequentialFile(this, journalDir, fileName, maxIO);
      }

      private static File newFolder(File root, String... subDirs) throws IOException {
         String subFolder = String.join("/", subDirs);
         File result = new File(root, subFolder);
         if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
         }
         return result;
      }
   }

   public static class TestableSequentialFile extends NIOSequentialFile {

      @Override
      public boolean equals(Object obj) {
         TestableSequentialFile other = (TestableSequentialFile)obj;
         return other.getFile().equals(this.getFile());
      }

      @Override
      public String toString() {
         return "TestableSequentialFile::" + getFile().toString();
      }

      @Override
      public int hashCode() {
         return getFile().hashCode();
      }

      static Map<TestableSequentialFile, Exception> openFiles = new ConcurrentHashMap<>();

      public TestableSequentialFile(SequentialFileFactory factory,
                                    File directory,
                                    String file,
                                    int maxIO) {
         super(factory, directory, file, maxIO);
      }

      @Override
      public void open(int maxIO, boolean useExecutor) throws IOException {
         super.open(maxIO, useExecutor);
         openFiles.put(TestableSequentialFile.this, new Exception("open"));
      }

      @Override
      public synchronized void close(boolean waitSync, boolean block) throws IOException, InterruptedException, ActiveMQException {
         super.close(waitSync, block);
         openFiles.remove(TestableSequentialFile.this);
      }

      private static File newFolder(File root, String... subDirs) throws IOException {
         String subFolder = String.join("/", subDirs);
         File result = new File(root, subFolder);
         if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
         }
         return result;
      }
   }

   protected HAPolicyConfiguration createReplicationPrimaryConfiguration() {
      return new ReplicatedPolicyConfiguration()
         .setVoteOnReplicationFailure(false)
         .setCheckForActiveServer(false);
   }

   // Set a small call timeout and write buffer high water mark value to trigger replication flow control
   private Configuration createPrimaryConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::primary");

      File primaryDir = newFolder(brokersFolder, "primary");
      conf.setBrokerInstance(primaryDir);

      conf.addAcceptorConfiguration("primary", "tcp://localhost:61616?writeBufferHighWaterMark=2048&writeBufferLowWaterMark=2048");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("primary", "tcp://localhost:61616");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      conf.setHAPolicyConfiguration(createReplicationPrimaryConfiguration());

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("backup");
      ccconf.setName("cluster");
      ccconf.setConnectorName("primary");
      ccconf.setCallTimeout(4000);
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   protected HAPolicyConfiguration createReplicationBackupConfiguration() {
      return new ReplicaPolicyConfiguration().setClusterName("cluster");
   }

   private Configuration createBackupConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::backup");

      File backupDir = newFolder(brokersFolder, "backup");
      conf.setBrokerInstance(backupDir);

      conf.setHAPolicyConfiguration(createReplicationBackupConfiguration());

      conf.addAcceptorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("live", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("live");
      ccconf.setName("cluster");
      ccconf.setConnectorName("backup");
      conf.addClusterConfiguration(ccconf);

      /*
       * Set a bad url then, as a result the backup node would make a decision of replicating from live node in the case
       * of connection failure. Set big check period to not schedule checking which would stop server.
       */
      conf.setNetworkCheckPeriod(1000000).setNetworkCheckURLList("http://localhost:28787").setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   abstract class AddRecordLoaderCallback implements LoaderCallback {

      @Override
      public void addPreparedTransaction(PreparedTransactionInfo preparedTransaction) {

      }

      @Override
      public void deleteRecord(long id) {

      }

      @Override
      public void updateRecord(RecordInfo info) {

      }

      @Override
      public void failedTransaction(long transactionID, List<RecordInfo> records, List<RecordInfo> recordsToDelete) {

      }
   }

   public static final class TestInterceptor implements Interceptor {

      private long sleepTime;

      public TestInterceptor(long sleepTime) {
         this.sleepTime = sleepTime;
      }

      public void setSleepTime(long sleepTime) {
         this.sleepTime = sleepTime;
      }

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         try {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() < startTime + sleepTime) {
               Thread.sleep(100);
            }
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         return true;
      }
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
