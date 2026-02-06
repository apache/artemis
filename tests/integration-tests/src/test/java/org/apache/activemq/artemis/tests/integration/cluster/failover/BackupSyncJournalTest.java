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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.files.FileMoveManager;
import org.apache.activemq.artemis.tests.integration.cluster.util.BackupSyncDelay;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackupSyncJournalTest extends FailoverTestBase {

   protected static final int BACKUP_WAIT_TIME = 60;
   private ServerLocatorInternal locator;
   protected ClientSessionFactoryInternal sessionFactory;
   protected ClientSession session;
   protected ClientProducer producer;
   private BackupSyncDelay syncDelay;
   private final int defaultNMsgs = 20;
   private int n_msgs = defaultNMsgs;

   protected void setNumberOfMessages(int nmsg) {
      this.n_msgs = nmsg;
   }

   protected int getNumberOfMessages() {
      return n_msgs;
   }

   protected final FailoverWaiter failoverWaiter = new FailoverWaiter();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      startBackupServer = false;
      super.setUp();
      setNumberOfMessages(defaultNMsgs);
      locator = (ServerLocatorInternal) getServerLocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(15).setRetryInterval(200);
      sessionFactory = createSessionFactoryAndWaitForTopology(locator, 1);
      sessionFactory.addFailoverListener(failoverWaiter);
      syncDelay = new BackupSyncDelay(backupServer, primaryServer);
   }

   @Test
   public void testNodeID() throws Exception {
      startBackupFinishSyncing();
      assertTrue(backupServer.isStarted(), "must be running");
      assertEquals(primaryServer.getServer().getNodeID(), backupServer.getServer().getNodeID(), "backup and primary should have the same nodeID");
   }

   @Test
   public void testReserveFileIdValuesOnBackup() throws Exception {
      final int totalRounds = 5;
      createProducer();
      JournalImpl messageJournal = getMessageJournalFromServer(primaryServer);
      for (int i = 0; i < totalRounds; i++) {
         messageJournal.forceMoveNextFile();
         sendMessages(session, producer, n_msgs);
      }
      Queue queue = primaryServer.getServer().locateQueue(ADDRESS);
      PagingStore store = queue.getPageSubscription().getPagingStore();

      // in case of paging I must close the current page otherwise we will get a pending counter
      // what would make the verification on similar journal to fail after the recovery
      if (store.isPaging()) {
         store.forceAnotherPage(true);
      }
      backupServer.start();

      // Deliver messages with Backup in-sync
      waitForRemoteBackup(sessionFactory, BACKUP_WAIT_TIME, false, backupServer.getServer());

      final JournalImpl backupMsgJournal = getMessageJournalFromServer(backupServer);
      sendMessages(session, producer, n_msgs);

      // in case of paging I must close the current page otherwise we will get a pending counter
      // what would make the verification on similar journal to fail after the recovery
      if (store.isPaging()) {
         store.forceAnotherPage(true);
      }

      // Deliver messages with Backup up-to-date
      syncDelay.deliverUpToDateMsg();
      waitForRemoteBackup(sessionFactory, BACKUP_WAIT_TIME, true, backupServer.getServer());
      // SEND more messages, now with the backup replicating
      sendMessages(session, producer, n_msgs);

      // in case of paging I must close the current page otherwise we will get a pending counter
      // what would make the verification on similar journal to fail after the recovery
      if (store.isPaging()) {
         store.forceAnotherPage(true);
      }

      int size = messageJournal.getFileSize();
      PagingStore ps = primaryServer.getServer().getPagingManager().getPageStore(ADDRESS);
      if (ps.getPageSizeBytes() == PAGE_SIZE) {
         assertTrue(ps.isStarted(), "isStarted");
         assertFalse(ps.startPaging(), "start paging should return false, because we expect paging to be running");
      }
      finishSyncAndFailover();

      assertEquals(size, backupMsgJournal.getFileSize(), "file sizes must be the same");

      // "+ 2": there two other calls that send N_MSGS.
      for (int i = 0; i < totalRounds + 2; i++) {
         receiveMsgsInRange(0, n_msgs);
      }
      assertNoMoreMessages();
   }

   protected void assertNoMoreMessages() throws ActiveMQException {
      session.start();
      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg = consumer.receiveImmediate();
      assertNull(msg, "there should be no more messages to receive! " + msg);
      consumer.close();
      session.commit();

   }

   protected void startBackupFinishSyncing() throws Exception {
      syncDelay.deliverUpToDateMsg();
      backupServer.start();
      waitForRemoteBackup(sessionFactory, BACKUP_WAIT_TIME, true, backupServer.getServer());
   }

   @Test
   public void testReplicationDuringSync() throws Exception {
      try {
         createProducerSendSomeMessages();
         backupServer.start();
         waitForRemoteBackup(sessionFactory, BACKUP_WAIT_TIME, false, backupServer.getServer());

         sendMessages(session, producer, n_msgs);
         session.commit();
         receiveMsgsInRange(0, n_msgs);

         finishSyncAndFailover();

         receiveMsgsInRange(0, n_msgs);
         assertNoMoreMessages();
      } catch (AssertionError error) {
         printJournal(primaryServer);
         printJournal(backupServer);
         // test failed
         throw error;
      }
   }

   void printJournal(TestableServer server) {
      try {
         System.out.println("\n\n BINDINGS JOURNAL\n\n");
         Configuration config = server.getServer().getConfiguration();
         DescribeJournal.describeBindingsJournal(config.getBindingsLocation());
         System.out.println("\n\n MESSAGES JOURNAL\n\n");
         DescribeJournal.describeMessagesJournal(config.getJournalLocation());
      } catch (Exception ignored) {
         ignored.printStackTrace();
      }
   }

   protected void finishSyncAndFailover() throws Exception {
      syncDelay.deliverUpToDateMsg();
      waitForRemoteBackup(sessionFactory, BACKUP_WAIT_TIME, true, backupServer.getServer());
      assertFalse(backupServer.getServer().isActive(), "should not be initialized");

      crash(session);
      assertTrue(backupServer.getServer().waitForActivation(5, TimeUnit.SECONDS), "backup initialized");

      assertNodeIdWasSaved();
   }

   private void assertNodeIdWasSaved() throws Exception {
      assertTrue(backupServer.getServer().waitForActivation(5, TimeUnit.SECONDS), "backup initialized");

      // assert that nodeID was saved (to the right file!)

      String journalDirectory = backupConfig.getJournalDirectory();

      File serverLockFile = new File(journalDirectory, "server.lock");
      assertTrue(serverLockFile.exists(), "server.lock must exist!\n " + serverLockFile);
      RandomAccessFile raFile = new RandomAccessFile(serverLockFile, "r");
      try {
         // verify the nodeID was written correctly
         FileChannel channel = raFile.getChannel();
         final int size = 16;
         ByteBuffer id = ByteBuffer.allocateDirect(size);
         int read = channel.read(id, 3);
         assertEquals(size, read, "tried to read " + size + " bytes");
         byte[] bytes = new byte[16];
         id.position(0);
         id.get(bytes);
         UUID uuid = new UUID(UUID.TYPE_TIME_BASED, bytes);
         SimpleString storedNodeId = SimpleString.of(uuid.toString());
         assertEquals(backupServer.getServer().getNodeID(), storedNodeId, "nodeId must match");
      } finally {
         raFile.close();
      }
   }

   @Test
   public void testMessageSyncSimple() throws Exception {
      createProducerSendSomeMessages();
      startBackupCrashPrimary();
      receiveMsgsInRange(0, n_msgs);
      assertNoMoreMessages();
   }

   /**
    * Basic fail-back test.
    */
   @Test
   public void testFailBack() throws Exception {
      createProducerSendSomeMessages();
      startBackupCrashPrimary();
      receiveMsgsInRange(0, n_msgs);
      assertNoMoreMessages();

      sendMessages(session, producer, n_msgs);
      receiveMsgsInRange(0, n_msgs);
      assertNoMoreMessages();

      sendMessages(session, producer, 2 * n_msgs);
      assertFalse(primaryServer.getServer().getHAPolicy().isBackup(), "must NOT be a backup");
      adaptPrimaryConfigForReplicatedFailBack(primaryServer);
      FileMoveManager primaryMoveManager = new FileMoveManager(primaryServer.getServer().getConfiguration().getJournalLocation(), -1);
      primaryServer.getServer().lockActivation();
      try {
         primaryServer.start();
         assertTrue(primaryServer.getServer().getHAPolicy().isBackup(), "must have become a backup");
         assertEquals(0, primaryMoveManager.getNumberOfFolders());
      } finally {
         primaryServer.getServer().unlockActivation();
      }
      waitForServerToStart(primaryServer.getServer());
      primaryServer.getServer().waitForActivation(10, TimeUnit.SECONDS);
      assertEquals(1, primaryMoveManager.getNumberOfFolders());
      assertFalse(primaryServer.getServer().getHAPolicy().isBackup(), "must be active now");

      assertTrue(primaryServer.getServer().waitForActivation(15, TimeUnit.SECONDS), "Fail-back must initialize primary!");
      assertFalse(primaryServer.getServer().getHAPolicy().isBackup(), "must be primary!");
      int i = 0;
      while (!backupServer.isStarted() && i++ < 100) {
         Thread.sleep(100);
      }
      assertTrue(backupServer.getServer().isStarted());
      assertTrue(primaryServer.getServer().isStarted());
      receiveMsgsInRange(0, 2 * n_msgs);
      assertNoMoreMessages();
   }

   @Test
   public void testMessageSync() throws Exception {
      createProducerSendSomeMessages();
      receiveMsgsInRange(0, n_msgs / 2);
      startBackupCrashPrimary();
      receiveMsgsInRange(n_msgs / 2, n_msgs);
      assertNoMoreMessages();
   }

   @Test
   public void testRemoveAllMessageWithPurgeOnNoConsumers() throws Exception {
      final boolean purgeOnNoConsumers = true;
      createProducerSendSomeMessages();
      primaryServer.getServer().locateQueue(ADDRESS).setPurgeOnNoConsumers(purgeOnNoConsumers);
      assertEquals(n_msgs, primaryServer.getServer().getManagementService().getQueueControl(ADDRESS.toString()).removeAllMessages());
      startBackupCrashPrimary();
      assertNoMoreMessages();
   }

   @Test
   public void testReceiveAllMessagesWithPurgeOnNoConsumers() throws Exception {
      final boolean purgeOnNoConsumers = true;
      createProducerSendSomeMessages();
      primaryServer.getServer().locateQueue(ADDRESS).setPurgeOnNoConsumers(purgeOnNoConsumers);
      receiveMsgsInRange(0, n_msgs);
      startBackupCrashPrimary();
      assertNoMoreMessages();
   }

   private void startBackupCrashPrimary() throws Exception {
      assertFalse(backupServer.isStarted(), "backup is started?");
      primaryServer.removeInterceptor(syncDelay);
      backupServer.start();
      waitForBackup(sessionFactory, BACKUP_WAIT_TIME);
      failoverWaiter.reset();
      crash(session);
      backupServer.getServer().waitForActivation(5, TimeUnit.SECONDS);
      //for some system the retryAttempts and retryInterval may be too small
      //so that during failover all attempts have failed before the backup
      //server is fully activated.
      assertTrue(failoverWaiter.waitFailoverComplete(), "Session didn't failover, the maxRetryAttempts and retryInterval may be too small");
   }

   protected void createProducer() throws ActiveMQException {
      session = addClientSession(sessionFactory.createSession(true, true));
      session.createQueue(QueueConfiguration.of(ADDRESS));
      if (producer != null)
         producer.close();
      producer = addClientProducer(session.createProducer(ADDRESS));
   }

   protected void createProducerSendSomeMessages() throws ActiveMQException {
      createProducer();
      sendMessages(session, producer, n_msgs);
      session.commit();
   }

   protected void receiveMsgsInRange(int start, int end) throws ActiveMQException {
      session.start();
      ClientConsumer consumer = addClientConsumer(session.createConsumer(ADDRESS));
      receiveMessages(consumer, start, end, true);
      consumer.close();
      session.commit();
   }

   static JournalImpl getMessageJournalFromServer(TestableServer server) {
      JournalStorageManager sm = (JournalStorageManager) server.getServer().getStorageManager();
      return (JournalImpl) sm.getMessageJournal();
   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(boolean primary) {
      return TransportConfigurationUtils.getInVMAcceptor(primary);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(boolean primary) {
      return TransportConfigurationUtils.getInVMConnector(primary);
   }

   private class FailoverWaiter implements FailoverEventListener {

      private final ReusableLatch latch;

      FailoverWaiter() {
         latch = new ReusableLatch(1);
      }

      public void reset() {
         latch.setCount(0);
      }

      @Override
      public void failoverEvent(FailoverEventType eventType) {
         if (eventType == FailoverEventType.FAILOVER_COMPLETED) {
            latch.countDown();
         }
      }

      public boolean waitFailoverComplete() throws InterruptedException {
         return latch.await(10, TimeUnit.SECONDS);
      }
   }

}
