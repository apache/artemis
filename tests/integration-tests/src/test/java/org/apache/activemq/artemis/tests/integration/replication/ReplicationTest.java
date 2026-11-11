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
package org.apache.activemq.artemis.tests.integration.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.JournalUpdateCallback;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.replication.ReplicatedJournal;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.ReplicatedBackupUtils;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.collections.SparseArrayLinkedList;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public final class ReplicationTest extends ActiveMQTestBase {

   @Parameter(index = 0)
   public boolean pluggableQuorum;

   @Parameters(name = "PluggableQuorum={0}")
   public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   private ThreadFactory tFactory;
   private ExecutorService executor;
   private ExecutorFactory factory;
   private ScheduledExecutorService scheduledExecutor;

   private ActiveMQServer backupServer;
   /**
    * This field is not always used.
    */
   private ActiveMQServer liveServer;

   private ServerLocator locator;

   private ReplicationManager manager;
   private static final SimpleString ADDRESS = SimpleString.of("foobar123");

   private void setupServer(boolean backup, String... interceptors) throws Exception {
      this.setupServer(false, backup, null, interceptors);
   }

   private void setupServer(boolean useNetty,
                            boolean backup,
                            ExtraConfigurer extraConfig,
                            String... incomingInterceptors) throws Exception {
      TransportConfiguration primaryConnector = null;
      TransportConfiguration primaryAcceptor = null;
      TransportConfiguration backupConnector = null;
      TransportConfiguration backupAcceptor = null;
      if (useNetty) {
         primaryConnector = TransportConfigurationUtils.getNettyConnector(true, 0);
         primaryAcceptor = TransportConfigurationUtils.getNettyAcceptor(true, 0);
         backupConnector = TransportConfigurationUtils.getNettyConnector(false, 0);
         backupAcceptor = TransportConfigurationUtils.getNettyAcceptor(false, 0);
      } else {
         primaryConnector = TransportConfigurationUtils.getInVMConnector(true);
         backupConnector = TransportConfigurationUtils.getInVMConnector(false);
         backupAcceptor = TransportConfigurationUtils.getInVMAcceptor(false);
      }

      Configuration primaryConfig = createDefaultInVMConfig();

      Configuration backupConfig = createDefaultInVMConfig().setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration()).setBindingsDirectory(getBindingsDir(0, true)).setJournalDirectory(getJournalDir(0, true)).setPagingDirectory(getPageDir(0, true)).setLargeMessagesDirectory(getLargeMessagesDir(0, true)).setIncomingInterceptorClassNames(incomingInterceptors.length > 0 ? Arrays.asList(incomingInterceptors) : new ArrayList<>());

      if (!pluggableQuorum) {
         ReplicatedBackupUtils.configureReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, primaryAcceptor);
      } else {
         DistributedLockManagerConfiguration managerConfiguration =
            new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(),
                                                    Collections.singletonMap("locks-folder", newFolder(temporaryFolder, "manager").toString()));

         ReplicatedBackupUtils.configurePluggableQuorumReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, primaryAcceptor, managerConfiguration, managerConfiguration);
      }

      if (extraConfig != null) {
         extraConfig.config(primaryConfig, backupConfig);
      }

      if (backup) {
         liveServer = createServer(primaryConfig);
         liveServer.start();
         waitForComponent(liveServer);
      }

      backupServer = createServer(backupConfig);
      if (useNetty) {
         locator = createNettyNonHALocator();
      } else {
         locator = createInVMNonHALocator();
      }

      backupServer.start();
      if (backup) {
         ActiveMQTestBase.waitForRemoteBackup(null, 5, true, backupServer);
      }
      int count = 0;
      waitForReplication(count);
   }

   private void waitForReplication(int count) throws InterruptedException {
      if (liveServer == null) {
         return;
      }

      while (liveServer.getReplicationManager() == null && count < 10) {
         Thread.sleep(50);
         count++;
      }
   }

   private static void waitForComponent(ActiveMQComponent component) throws Exception {
      waitForComponent(component, 3);
   }

   @TestTemplate
   public void testBasicConnection() throws Exception {
      setupServer(true);
      waitForComponent(liveServer.getReplicationManager());
   }

   @TestTemplate
   public void testConnectIntoNonBackup() throws Exception {
      setupServer(false);
      try {
         ClientSessionFactory sf = createSessionFactory(locator);
         manager = new ReplicationManager(null, (CoreRemotingConnection) sf.getConnection(), sf.getServerLocator().getCallTimeout(), sf.getServerLocator().getCallTimeout(), factory);
         addActiveMQComponent(manager);
         manager.start();
         fail("Exception was expected");
      } catch (ActiveMQNotConnectedException nce) {
         // ok
      } catch (ActiveMQException expected) {
         fail("Invalid Exception type:" + expected.getType());
      }
   }

   @TestTemplate
   public void testSendPackets() throws Exception {
      setupServer(true);

      JournalStorageManager storage = getStorage();

      manager = liveServer.getReplicationManager();
      waitForComponent(manager);

      Journal replicatedJournal = new ReplicatedJournal((byte) 1, new FakeJournal(), manager);

      replicatedJournal.appendPrepareRecord(1, new FakeData(), false);

      replicatedJournal.appendAddRecord(1, (byte) 1, new FakeData(), false);
      replicatedJournal.appendUpdateRecord(1, (byte) 2, new FakeData(), false);
      replicatedJournal.appendDeleteRecord(1, false);
      replicatedJournal.appendAddRecordTransactional(2, 2, (byte) 1, new FakeData());
      replicatedJournal.appendUpdateRecordTransactional(2, 2, (byte) 2, new FakeData());
      replicatedJournal.appendCommitRecord(2, false);

      replicatedJournal.appendDeleteRecordTransactional(3, 4, new FakeData());
      replicatedJournal.appendPrepareRecord(3, new FakeData(), false);
      replicatedJournal.appendRollbackRecord(3, false);

      blockOnReplication(storage, manager);

      assertTrue(manager.getActiveTokens().isEmpty(), "Expecting no active tokens:" + manager.getActiveTokens());

      CoreMessage msg = new CoreMessage().initBuffer(1024).setMessageID(1);

      SimpleString dummy = SimpleString.of("dummy");
      msg.setAddress(dummy);

      replicatedJournal.appendAddRecordTransactional(23, 24, (byte) 1, new FakeData());

      PagedMessage pgmsg = new PagedMessageImpl(msg, new long[0]);
      manager.pageWrite(dummy, pgmsg, 1, true);
      manager.pageWrite(dummy, pgmsg, 2, true);
      manager.pageWrite(dummy, pgmsg, 3, true);
      manager.pageWrite(dummy, pgmsg, 4, true);

      blockOnReplication(storage, manager);

      PagingManager pagingManager = createPageManager(backupServer.getStorageManager(), backupServer.getConfiguration(), backupServer.getExecutorFactory(), backupServer.getScheduledPool(), backupServer.getAddressSettingsRepository());

      PagingStore store = pagingManager.getPageStore(dummy);
      store.start();
      assertEquals(4, store.getNumberOfPages());
      store.stop();

      manager.pageDeleted(dummy, 1);
      manager.pageDeleted(dummy, 2);
      manager.pageDeleted(dummy, 3);
      manager.pageDeleted(dummy, 4);
      manager.pageDeleted(dummy, 5);
      manager.pageDeleted(dummy, 6);

      blockOnReplication(storage, manager);

      CoreMessage serverMsg = new CoreMessage();
      serverMsg.setMessageID(500);
      serverMsg.setAddress(SimpleString.of("tttt"));

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(100);
      serverMsg.encodeHeadersAndProperties(buffer.byteBuf());

      manager.largeMessageBegin(500);

      manager.largeMessageWrite(500, new byte[1024]);

      manager.largeMessageDelete(500L, storage);

      blockOnReplication(storage, manager);

      store.start();

      assertEquals(0, store.getNumberOfPages());
   }

   @TestTemplate
   public void testSendPacketsWithFailure() throws Exception {
      final int nMsg = 100;
      final int stop = 37;
      setupServer(true, TestInterceptor.class.getName());

      manager = liveServer.getReplicationManager();
      waitForComponent(manager);
      ClientSessionFactory sf = createSessionFactory(locator);
      final ClientSession session = sf.createSession();
      final ClientSession session2 = sf.createSession();
      session.createQueue(QueueConfiguration.of(ADDRESS));

      final ClientProducer producer = session.createProducer(ADDRESS);

      session.start();
      session2.start();
      try {
         final ClientConsumer consumer = session2.createConsumer(ADDRESS);
         for (int i = 0; i < nMsg; i++) {

            ClientMessage message = session.createMessage(true);
            setBody(i, message);
            message.putIntProperty("counter", i);
            producer.send(message);
            if (i == stop) {
               // Now we start intercepting the communication with the backup
               TestInterceptor.value.set(false);
            }
            ClientMessage msgRcvd = consumer.receive(1000);
            assertNotNull(msgRcvd, "Message should exist!");
            assertMessageBody(i, msgRcvd);
            assertEquals(i, msgRcvd.getIntProperty("counter").intValue());
            msgRcvd.acknowledge();
         }
      } finally {
         TestInterceptor.value.set(false);
         if (!session.isClosed()) {
            session.close();
         }
         if (!session2.isClosed()) {
            session2.close();
         }
      }
   }

   @TestTemplate
   public void testSendMessageWithLargeHeader() throws Exception {
      setupServer(true, TestInterceptor.class.getName());

      manager = liveServer.getReplicationManager();
      waitForComponent(manager);

      ClientSessionFactory sf = createSessionFactory(locator);

      try (ClientSession producerSession = sf.createSession();
           ClientSession consumerSession = sf.createSession()) {

         producerSession.start();
         producerSession.createQueue(QueueConfiguration.of(ADDRESS));
         ClientProducer producer = producerSession.createProducer(ADDRESS);

         consumerSession.start();
         ClientConsumer consumer = consumerSession.createConsumer(ADDRESS);

         ClientMessage messageBefore = producerSession.createMessage(true);
         setBody(0, messageBefore);
         messageBefore.putIntProperty("counter", 0);
         producer.send(messageBefore);

         ClientMessage messageReceivedBefore = consumer.receive(1000);
         assertNotNull(messageReceivedBefore, "Message should exist!");
         assertMessageBody(0, messageReceivedBefore);
         assertEquals(0, messageReceivedBefore.getIntProperty("counter").intValue());
         messageReceivedBefore.acknowledge();

         ClientMessage messageWithLargeHeader = producerSession.createMessage(true);
         setBody(1, messageWithLargeHeader);
         messageWithLargeHeader.putIntProperty("counter", 1);
         messageWithLargeHeader.putStringProperty("large-property", "z".repeat(512 * 1024));
         try {
            producer.send(messageWithLargeHeader);
            fail();
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("AMQ149005"));
         }

         ClientMessage messageAfter = producerSession.createMessage(true);
         setBody(2, messageAfter);
         messageAfter.putIntProperty("counter", 2);
         producer.send(messageAfter);

         ClientMessage messageReceivedAfter = consumer.receive(1000);
         assertNotNull(messageReceivedAfter, "Message should exist!");
         assertMessageBody(2, messageReceivedAfter);
         assertEquals(2, messageReceivedAfter.getIntProperty("counter").intValue());
         messageReceivedAfter.acknowledge();

         assertNull(consumer.receiveImmediate());
      }
   }

   @TestTemplate
   public void testExceptionSettingActionBefore() throws Exception {
      OperationContext ctx = OperationContextImpl.getContext(factory);

      ctx.storeLineUp();

      String msg = "I'm an exception";

      ctx.onError(ActiveMQExceptionType.UNBLOCKED.getCode(), msg);

      final AtomicInteger lastError = new AtomicInteger(0);

      final List<String> msgsResult = new ArrayList<>();

      final CountDownLatch latch = new CountDownLatch(1);

      ctx.executeOnCompletion(new IOCallback() {
         @Override
         public void onError(final int errorCode, final String errorMessage) {
            lastError.set(errorCode);
            msgsResult.add(errorMessage);
            latch.countDown();
         }

         @Override
         public void done() {
         }
      });

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      assertEquals(5, lastError.get());

      assertEquals(1, msgsResult.size());

      assertEquals(msg, msgsResult.get(0));

      final CountDownLatch latch2 = new CountDownLatch(1);

      // Adding the Task after the exception should still throw an exception
      ctx.executeOnCompletion(new IOCallback() {
         @Override
         public void onError(final int errorCode, final String errorMessage) {
            lastError.set(errorCode);
            msgsResult.add(errorMessage);
            latch2.countDown();
         }

         @Override
         public void done() {
         }
      });

      assertTrue(latch2.await(5, TimeUnit.SECONDS));

      assertEquals(2, msgsResult.size());

      assertEquals(msg, msgsResult.get(0));

      assertEquals(msg, msgsResult.get(1));

      final CountDownLatch latch3 = new CountDownLatch(1);

      ctx.executeOnCompletion(new IOCallback() {
         @Override
         public void onError(final int errorCode, final String errorMessage) {
         }

         @Override
         public void done() {
            latch3.countDown();
         }
      });

      assertTrue(latch2.await(5, TimeUnit.SECONDS));

   }

   @TestTemplate
   public void testClusterConnectionConfigs() throws Exception {
      final long ttlOverride = 123456789;
      final long checkPeriodOverride = 987654321;

      ExtraConfigurer configurer = (primaryConfig, backupConfig) -> {
         List<ClusterConnectionConfiguration> ccList = backupConfig.getClusterConfigurations();
         assertFalse(ccList.isEmpty());
         ClusterConnectionConfiguration cc = ccList.get(0);
         cc.setConnectionTTL(ttlOverride);
         cc.setClientFailureCheckPeriod(checkPeriodOverride);
      };
      this.setupServer(true, true, configurer);
      assertInstanceOf(ActiveMQServerImpl.class, backupServer);

      ClusterController controller = backupServer.getClusterManager().getClusterController();

      ServerLocator replicationLocator = controller.getReplicationLocator();

      assertNotNull(replicationLocator);

      assertEquals(ttlOverride, replicationLocator.getConnectionTTL());
      assertEquals(checkPeriodOverride, replicationLocator.getClientFailureCheckPeriod());
   }

   private JournalStorageManager getStorage() throws Exception {
      return new JournalStorageManager(createDefaultInVMConfig(), EmptyCriticalAnalyzer.getInstance(), factory, factory);
   }

   private void blockOnReplication(final StorageManager storage, final ReplicationManager manager1) throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);
      storage.afterCompleteOperations(new IOCallback() {

         @Override
         public void onError(final int errorCode, final String errorMessage) {
         }

         @Override
         public void done() {
            latch.countDown();
         }
      });

      assertTrue(latch.await(30, TimeUnit.SECONDS));
   }

   @TestTemplate
   public void testNoActions() throws Exception {

      setupServer(true);
      StorageManager storage = getStorage();
      manager = liveServer.getReplicationManager();
      waitForComponent(manager);

      Journal replicatedJournal = new ReplicatedJournal((byte) 1, new FakeJournal(), manager);

      replicatedJournal.appendPrepareRecord(1, new FakeData(), false);

      final CountDownLatch latch = new CountDownLatch(1);
      storage.afterCompleteOperations(new IOCallback() {

         @Override
         public void onError(final int errorCode, final String errorMessage) {
         }

         @Override
         public void done() {
            latch.countDown();
         }
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));

      assertEquals(0, manager.getActiveTokens().size(), "should be empty " + manager.getActiveTokens());
   }

   @TestTemplate
   public void testOrderOnNonPersistency() throws Exception {

      setupServer(true);

      final ArrayList<Integer> executions = new ArrayList<>();

      StorageManager storage = getStorage();
      manager = liveServer.getReplicationManager();
      Journal replicatedJournal = new ReplicatedJournal((byte) 1, new FakeJournal(), manager);

      int numberOfAdds = 200;

      final CountDownLatch latch = new CountDownLatch(numberOfAdds);

      OperationContext ctx = storage.getContext();

      for (int i = 0; i < numberOfAdds; i++) {
         final int nAdd = i;

         if (i % 2 == 0) {
            replicatedJournal.appendPrepareRecord(i, new FakeData(), false);
         }

         ctx.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(final int errorCode, final String errorMessage) {
            }

            @Override
            public void done() {
               executions.add(nAdd);
               latch.countDown();
            }
         });
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      for (int i = 0; i < numberOfAdds; i++) {
         assertEquals(i, executions.get(i).intValue());
      }

      assertEquals(0, manager.getActiveTokens().size());
   }

   @TestTemplate
   public void testReplicationLargeMessageFileClose() throws Exception {
      setupServer(true);

      JournalStorageManager storage = getStorage();

      manager = liveServer.getReplicationManager();
      waitForComponent(manager);

      CoreMessage msg = new CoreMessage().initBuffer(1024).setMessageID(1);
      LargeServerMessage largeMsg = liveServer.getStorageManager().createCoreLargeMessage(500, msg);
      largeMsg.addBytes(new byte[1024]);

      blockOnReplication(storage, manager);

      LargeServerMessageImpl message1 = (LargeServerMessageImpl) getReplicationEndpoint(backupServer).getLargeMessages().get(500L);


      assertNotNull(message1);
      assertTrue(largeMsg.getAppendFile().isOpen());
      assertTrue(message1.getAppendFile().isOpen());

      largeMsg.releaseResources(true, true);

      Wait.assertTrue(() -> getReplicationEndpoint(backupServer).getLargeMessages().get(500L) == null, 5000);

      assertFalse(largeMsg.getAppendFile().isOpen());
      assertFalse(message1.getAppendFile().isOpen());
   }

   class FakeData implements EncodingSupport {

      @Override
      public void decode(final ActiveMQBuffer buffer) {
      }

      @Override
      public void encode(final ActiveMQBuffer buffer) {
         buffer.writeBytes(new byte[5]);
      }

      @Override
      public int getEncodeSize() {
         return 5;
      }

   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      tFactory = new ActiveMQThreadFactory("ActiveMQ-ReplicationTest", false, this.getClass().getClassLoader());

      executor = Executors.newCachedThreadPool(tFactory);

      scheduledExecutor = new ScheduledThreadPoolExecutor(10, tFactory);

      factory = new OrderedExecutorFactory(executor);
   }

   /**
    * We need to shutdown the executors before calling {@link ActiveMQTestBase#tearDown()} (which will check for leaking
    * threads). Due to that, we need to close/stop all components here.
    */
   @Override
   @AfterEach
   public void tearDown() throws Exception {
      stopComponent(manager);
      manager = null;
      closeServerLocator(locator);
      stopComponent(backupServer);
      backupServer = null;
      stopComponent(liveServer);
      liveServer = null;

      executor.shutdownNow();
      scheduledExecutor.shutdownNow();

      tFactory = null;
      scheduledExecutor = null;

      super.tearDown();
   }

   protected PagingManager createPageManager(final StorageManager storageManager,
                                             final Configuration configuration,
                                             final ExecutorFactory executorFactory,
                                             final ScheduledExecutorService scheduledExecutorService,
                                             final HierarchicalRepository<AddressSettings> addressSettingsRepository) throws Exception {

      PagingManager paging = new PagingManagerImpl(new PagingStoreFactoryNIO(storageManager, configuration.getPagingLocation(), 1000, scheduledExecutorService, executorFactory, false, null), addressSettingsRepository, configuration.getManagementAddress());

      paging.start();
      runAfter(paging::stop);
      return paging;
   }


   public static final class TestInterceptor implements Interceptor {

      static AtomicBoolean value = new AtomicBoolean(true);

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         return TestInterceptor.value.get();
      }

   }

   static final class FakeJournal implements Journal {

      @Override
      public void setRemoveExtraFilesOnLoad(boolean removeExtraFilesOnLoad) {

      }

      @Override
      public void appendAddEvent(long id,
                                 byte recordType,
                                 Persister persister,
                                 Object record,
                                 boolean sync,
                                 IOCompletion completionCallback) throws Exception {

      }

      @Override
      public boolean isRemoveExtraFilesOnLoad() {
         return false;
      }

      @Override
      public void appendAddRecord(long id,
                                  byte recordType,
                                  Persister persister,
                                  Object record,
                                  boolean sync) throws Exception {

      }

      @Override
      public void appendAddRecord(long id,
                                  byte recordType,
                                  Persister persister,
                                  Object record,
                                  boolean sync,
                                  IOCompletion completionCallback) throws Exception {

      }

      @Override
      public void appendUpdateRecord(long id,
                                     byte recordType,
                                     Persister persister,
                                     Object record,
                                     boolean sync) throws Exception {

      }

      @Override
      public void tryAppendUpdateRecord(long id,
                                           byte recordType,
                                           Persister persister,
                                           Object record, JournalUpdateCallback updateCallback,
                                           boolean sync,
                                           boolean repalceableUpdate) throws Exception {
      }

      @Override
      public void appendUpdateRecord(long id,
                                     byte recordType,
                                     Persister persister,
                                     Object record,
                                     boolean sync,
                                     IOCompletion callback) throws Exception {

      }

      @Override
      public void tryAppendUpdateRecord(long id,
                                           byte recordType,
                                           Persister persister,
                                           Object record,
                                           boolean sync, boolean replaceableUpdate, JournalUpdateCallback updateCallback,
                                           IOCompletion callback) throws Exception {
      }

      @Override
      public void appendAddRecordTransactional(long txID,
                                               long id,
                                               byte recordType,
                                               Persister persister,
                                               Object record) throws Exception {

      }

      @Override
      public void appendUpdateRecordTransactional(long txID,
                                                  long id,
                                                  byte recordType,
                                                  Persister persister,
                                                  Object record) throws Exception {

      }

      @Override
      public void appendAddRecord(final long id,
                                  final byte recordType,
                                  final byte[] record,
                                  final boolean sync) throws Exception {

      }

      @Override
      public void appendAddRecord(final long id,
                                  final byte recordType,
                                  final EncodingSupport record,
                                  final boolean sync) throws Exception {

      }

      @Override
      public void appendAddRecordTransactional(final long txID,
                                               final long id,
                                               final byte recordType,
                                               final byte[] record) throws Exception {

      }

      @Override
      public void appendAddRecordTransactional(final long txID,
                                               final long id,
                                               final byte recordType,
                                               final EncodingSupport record) throws Exception {

      }

      @Override
      public void flush() throws Exception {

      }

      @Override
      public long getMaxRecordSize() {
         return ActiveMQDefaultConfiguration.getDefaultJournalBufferSizeAio();
      }

      @Override
      public void appendCommitRecord(final long txID, final boolean sync) throws Exception {

      }

      @Override
      public void appendDeleteRecord(final long id, final boolean sync) throws Exception {

      }

      @Override
      public void tryAppendDeleteRecord(long id, JournalUpdateCallback updateConsumer, boolean sync) throws Exception {
      }

      @Override
      public void appendDeleteRecordTransactional(final long txID,
                                                  final long id,
                                                  final byte[] record) throws Exception {

      }

      @Override
      public void appendDeleteRecordTransactional(final long txID,
                                                  final long id,
                                                  final EncodingSupport record) throws Exception {

      }

      @Override
      public void appendDeleteRecordTransactional(final long txID, final long id) throws Exception {

      }

      @Override
      public void appendPrepareRecord(final long txID,
                                      final EncodingSupport transactionData,
                                      final boolean sync) throws Exception {

      }

      @Override
      public void appendPrepareRecord(final long txID,
                                      final byte[] transactionData,
                                      final boolean sync) throws Exception {

      }

      @Override
      public void appendRollbackRecord(final long txID, final boolean sync) throws Exception {

      }

      @Override
      public void appendUpdateRecord(final long id,
                                     final byte recordType,
                                     final byte[] record,
                                     final boolean sync) throws Exception {

      }

      @Override
      public void tryAppendUpdateRecord(long id, byte recordType, byte[] record, JournalUpdateCallback updateCallback, boolean sync, boolean replaceable) throws Exception {
      }

      @Override
      public void appendUpdateRecord(final long id,
                                     final byte recordType,
                                     final EncodingSupport record,
                                     final boolean sync) throws Exception {

      }

      @Override
      public void appendUpdateRecordTransactional(final long txID,
                                                  final long id,
                                                  final byte recordType,
                                                  final byte[] record) throws Exception {

      }

      @Override
      public void appendUpdateRecordTransactional(final long txID,
                                                  final long id,
                                                  final byte recordType,
                                                  final EncodingSupport record) throws Exception {

      }

      @Override
      public int getAlignment() throws Exception {

         return 0;
      }

      @Override
      public JournalLoadInformation load(final LoaderCallback reloadManager) throws Exception {

         return new JournalLoadInformation();
      }

      @Override
      public JournalLoadInformation load(final SparseArrayLinkedList<RecordInfo> committedRecords,
                                         final List<PreparedTransactionInfo> preparedTransactions,
                                         final TransactionFailureCallback transactionFailure,
                                         final boolean fixbadtx) throws Exception {

         return new JournalLoadInformation();
      }

      @Override
      public JournalLoadInformation load(final List<RecordInfo> committedRecords,
                                         final List<PreparedTransactionInfo> preparedTransactions,
                                         final TransactionFailureCallback transactionFailure,
                                         final boolean fixbadtx) throws Exception {

         return new JournalLoadInformation();
      }

      @Override
      public boolean isStarted() {

         return false;
      }

      @Override
      public void start() throws Exception {

      }

      @Override
      public void stop() throws Exception {

      }

      @Override
      public JournalLoadInformation loadInternalOnly() throws Exception {
         return new JournalLoadInformation();
      }

      @Override
      public int getNumberOfRecords() {
         return 0;
      }

      @Override
      public void appendAddRecord(final long id,
                                  final byte recordType,
                                  final EncodingSupport record,
                                  final boolean sync,
                                  final IOCompletion completionCallback) throws Exception {
      }

      @Override
      public void appendCommitRecord(final long txID,
                                     final boolean sync,
                                     final IOCompletion callback) throws Exception {
      }

      @Override
      public void appendDeleteRecord(final long id,
                                     final boolean sync,
                                     final IOCompletion completionCallback) throws Exception {
      }

      @Override
      public void tryAppendDeleteRecord(long id, boolean sync, JournalUpdateCallback updateCallback, IOCompletion completionCallback) throws Exception {
      }

      @Override
      public void appendPrepareRecord(final long txID,
                                      final EncodingSupport transactionData,
                                      final boolean sync,
                                      final IOCompletion callback) throws Exception {
      }

      @Override
      public void appendRollbackRecord(final long txID,
                                       final boolean sync,
                                       final IOCompletion callback) throws Exception {
      }

      @Override
      public void appendUpdateRecord(final long id,
                                     final byte recordType,
                                     final EncodingSupport record,
                                     final boolean sync,
                                     final IOCompletion completionCallback) throws Exception {
      }

      public void sync(final IOCompletion callback) {
      }

      @Override
      public int getUserVersion() {
         return 0;
      }

      @Override
      public void appendCommitRecord(long txID,
                                     boolean sync,
                                     IOCompletion callback,
                                     boolean lineUpContext) throws Exception {

      }

      @Override
      public void lineUpContext(IOCompletion callback) {

      }

      @Override
      public JournalLoadInformation loadSyncOnly(JournalState s) throws Exception {
         return null;
      }

      @Override
      public Map<Long, JournalFile> createFilesForBackupSync(long[] fileIds) throws Exception {
         return null;
      }

      @Override
      public void synchronizationLock() {

      }

      @Override
      public void synchronizationUnlock() {

      }

      @Override
      public void forceMoveNextFile() throws Exception {

      }

      @Override
      public JournalFile[] getDataFiles() {
         return null;
      }

      @Override
      public SequentialFileFactory getFileFactory() {
         return null;
      }

      @Override
      public int getFileSize() {
         return 0;
      }

      @Override
      public void scheduleCompactAndBlock(int timeout) throws Exception {
      }

      @Override
      public void replicationSyncPreserveOldFiles() {
         // no-op
      }

      @Override
      public void replicationSyncFinished() {
         // no-op
      }

      @Override
      public long getWarningRecordSize() {
         return getMaxRecordSize() - 2048;
      }
   }

   private interface ExtraConfigurer {

      void config(Configuration primaryConfig, Configuration backupConfig);
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
