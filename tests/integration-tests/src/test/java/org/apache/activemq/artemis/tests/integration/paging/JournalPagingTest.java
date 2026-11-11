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
package org.apache.activemq.artemis.tests.integration.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PageTransactionInfoImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JournalPagingTest extends ActiveMQTestBase {

   protected static final int PAGE_MAX = 100 * 1024;
   protected static final int PAGE_SIZE = 10 * 1024;
   static final int MESSAGE_SIZE = 1024; // 1k
   static final SimpleString ADDRESS = SimpleString.of("SimpleAddress");
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected ServerLocator locator;
   protected ActiveMQServer server;
   protected ClientSessionFactory sf;
   private AssertionLoggerHandler loggerHandler;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      loggerHandler = new AssertionLoggerHandler();
   }

   @AfterEach
   public void checkLoggerEnd() throws Exception {
      if (loggerHandler != null) {
         try {
            // These are the message errors for the negative size address size
            assertFalse(loggerHandler.findText("AMQ222214"));
            assertFalse(loggerHandler.findText("AMQ222215"));
         } finally {
            loggerHandler.close();
         }
      }
   }

   @Test
   public void testPageCleanupWithInvalidDataTruncated() throws Exception {
      testPageCleanupWithInvalidData(true);
   }

   @Test
   public void testPageCleanupWithInvalidData() throws Exception {
      testPageCleanupWithInvalidData(false);
   }

   public void testPageCleanupWithInvalidData(boolean truncated) throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(JournalPagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(JournalPagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[10];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= 10; j++) {
         bb.put(getSamplebyte(j));
      }

      Queue queue = server.locateQueue(ADDRESS);
      queue.getPagingStore().startPaging();

      queue.getPagingStore().forceAnotherPage(true); // forcing an empty file, just to make it more challenging

      int page = 1;
      for (int i = 0; i < numberOfMessages; i++) {
         if (i % 10 == 0 && i > 0) {
            queue.getPagingStore().forceAnotherPage(true);
            page++;
         }
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty("i", i);
         message.putIntProperty("page", page);

         producer.send(message);
      }

      queue.getPagingStore().getCursorProvider().disableCleanup();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      session.start();

      for (int i = 0; i < 11; i++) {
         ClientMessage msgRec = consumer.receive(1000);
         assertNotNull(msgRec);
         msgRec.acknowledge();
      }
      session.commit();

      consumer.close();

      consumer = session.createConsumer(ADDRESS, SimpleString.of("i=29"));

      message = consumer.receive(5000);
      assertNotNull(message);
      message.acknowledge();
      session.commit();

      File folder = queue.getPagingStore().getFolder();

      // We will truncate two files
      for (int f = 2; f <= 3; f++) {
         String fileName = ((PagingStoreImpl) queue.getPagingStore()).createFileName(f);
         File file = new File(folder, fileName);
         file.delete();
         file.createNewFile();
         if (!truncated) {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(new byte[10]);
            fileOutputStream.close();
         }
      }
      sf.close();

      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(5000);

      Page page4 = queue.getPagingStore().newPageObject(4);
      page4.open(true);
      org.apache.activemq.artemis.utils.collections.LinkedList<PagedMessage> messagesRead = page4.read(server.getStorageManager());
      assertEquals(10, messagesRead.size());
      page4.close(false);
      page4.delete(null);
      page4.open(true);
      for (int i = 0; i < 9; i++) {
         page4.write(messagesRead.get(i), true, false); // this will make message 29 disappear
      }
      page4.close(false);

      server.stop();

      server.start();

      queue = server.locateQueue(ADDRESS);
      assertTrue(queue.getPagingStore().isPaging());

      queue.getPageSubscription().enableAutoCleanup(); // this should been true already as the server was restarted, just braces and belts

      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);

      logger.info("*******************************************************************************************************************************");
      logger.info("Creating consumer");

      consumer = session.createConsumer(ADDRESS);
      session.start();

      for (int i = 20; i < numberOfMessages; i++) { // I made one message disappear on page 4
         if (i != 29) { // I made message 29 disappear
            ClientMessage msgClient = consumer.receive(1000);
            assertNotNull(msgClient);
            assertEquals(i, msgClient.getIntProperty("i").intValue());
            msgClient.acknowledge();
         }
      }
      ClientMessage msgClient = consumer.receiveImmediate();
      assertNull(msgClient);
      session.commit();

      Wait.assertFalse(queue.getPagingStore()::isPaging, 5000, 100);
   }

   @Test
   public void testEmptyAddress() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 500;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(JournalPagingTest.ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ClientProducer producer = session.createProducer(JournalPagingTest.ADDRESS);

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.getBodyBuffer().writeBytes(body);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.close();

      String addressTxt = server.getPagingManager().getPageStore(JournalPagingTest.ADDRESS).getFolder().getAbsolutePath() + File.separator + PagingStoreFactoryNIO.ADDRESS_FILE;

      server.stop();

      new PrintWriter(addressTxt).close();
      assertTrue(new File(addressTxt).exists());

      final AtomicBoolean activationFailures = new AtomicBoolean();

      server.registerActivationFailureListener(exception -> activationFailures.set(true));

      server.start();

      server.stop();

      assertFalse(activationFailures.get());
   }

   @Test
   public void testPurge() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultNettyConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);

      server.start();

      SimpleString queue = SimpleString.of("testPurge:" + RandomUtil.randomUUIDString());
      server.addAddressInfo(new AddressInfo(queue, RoutingType.ANYCAST));
      QueueImpl purgeQueue = (QueueImpl) server.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setPurgeOnNoConsumers(true).setAutoCreateAddress(false));

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory();
      Connection connection = cf.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue jmsQueue = session.createQueue(queue.toString());

      MessageProducer producer = session.createProducer(jmsQueue);

      for (int i = 0; i < 100; i++) {
         producer.send(session.createTextMessage("hello" + i));
      }
      session.commit();

      Wait.assertEquals(0, purgeQueue::getMessageCount);

      Wait.assertEquals(0, purgeQueue.getPageSubscription().getPagingStore()::getAddressSize);

      MessageConsumer consumer = session.createConsumer(jmsQueue);

      for (int i = 0; i < 100; i++) {
         producer.send(session.createTextMessage("hello" + i));
         if (i == 10) {
            purgeQueue.getPageSubscription().getPagingStore().startPaging();
         }
      }
      session.commit();

      consumer.close();

      Wait.assertEquals(0, purgeQueue::getMessageCount);

      Wait.assertFalse(purgeQueue.getPageSubscription()::isPaging);

      Wait.assertEquals(0, purgeQueue.getPageSubscription().getPagingStore()::getAddressSize);
      consumer = session.createConsumer(jmsQueue);

      for (int i = 0; i < 100; i++) {
         purgeQueue.getPageSubscription().getPagingStore().startPaging();
         assertTrue(purgeQueue.getPageSubscription().isPaging());
         producer.send(session.createTextMessage("hello" + i));
         if (i % 2 == 0) {
            session.commit();
         }
      }

      session.commit();

      Wait.assertTrue(purgeQueue.getPageSubscription()::isPaging);

      connection.start();

      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(50000);
      assertNotNull(consumer.receive(5000));
      session.commit();

      consumer.close();

      Wait.assertEquals(0, purgeQueue::getMessageCount);
      Wait.assertEquals(0, purgeQueue.getPageSubscription().getPagingStore()::getAddressSize);
      Wait.assertFalse(purgeQueue.getPageSubscription()::isPaging, 5000, 100);

      StorageManager sm = server.getStorageManager();

      for (int i = 0; i < 1000; i++) {
         long tx = sm.generateID();
         PageTransactionInfoImpl txinfo = new PageTransactionInfoImpl(tx);
         sm.storePageTransaction(tx, txinfo);
         sm.commit(tx);
         tx = sm.generateID();
         sm.updatePageTransaction(tx, txinfo, 1);
         sm.commit(tx);
      }

      server.stop();
      server.start();
      Wait.assertEquals(0, () -> server.getPagingManager().getTransactions().size());
   }

   // First page is complete but it wasn't deleted
   @Test
   public void testPreparedACKRemoveAndRestart() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 10;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(JournalPagingTest.ADDRESS));

      Queue queue = server.locateQueue(JournalPagingTest.ADDRESS);

      ClientProducer producer = session.createProducer(JournalPagingTest.ADDRESS);

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      queue.getPageSubscription().getPagingStore().startPaging();

      forcePage(queue);

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty("count", i);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);

         if (i == 4) {
            session.commit();
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }

      session.commit();

      session.close();

      session = sf.createSession(true, false, false);

      ClientConsumer cons = session.createConsumer(ADDRESS);

      session.start();

      for (int i = 0; i <= 4; i++) {
         Xid xidConsumeNoCommit = newXID();
         session.start(xidConsumeNoCommit, XAResource.TMNOFLAGS);
         // First message is consumed, prepared, will be rolled back later
         ClientMessage firstMessageConsumed = cons.receive(5000);
         assertNotNull(firstMessageConsumed);
         firstMessageConsumed.acknowledge();
         session.end(xidConsumeNoCommit, XAResource.TMSUCCESS);
         session.prepare(xidConsumeNoCommit);
      }

      File pagingFolder = queue.getPageSubscription().getPagingStore().getFolder();

      server.stop();

      // remove the very first page. a restart should not fail
      File fileToRemove = new File(pagingFolder, "000000001.page");
      assertTrue(fileToRemove.delete());

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      cons = session.createConsumer(ADDRESS);

      session.start();

      for (int i = 5; i < numberOfMessages; i++) {
         ClientMessage message = cons.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("count").intValue());
         message.acknowledge();
      }
      assertNull(cons.receiveImmediate());
      session.commit();
   }

   private void forcePage(Queue queue) throws InterruptedException {
      for (long timeout = System.currentTimeMillis() + 5000; timeout > System.currentTimeMillis() && !queue.getPageSubscription().getPagingStore().isPaging(); ) {
         Thread.sleep(10);
      }
      assertTrue(queue.getPageSubscription().getPagingStore().isPaging());
   }

   @Test
   public void testInabilityToCreateDirectoryDuringPaging() throws Exception {

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         clearDataRecreateServerDirs();

         Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setPagingDirectory("/" + UUID.randomUUID().toString());

         server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);

         server.start();

         final int numberOfMessages = 100;

         locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, true, true);

         session.createQueue(QueueConfiguration.of(JournalPagingTest.ADDRESS));

         ClientProducer producer = session.createProducer(JournalPagingTest.ADDRESS);

         ClientMessage message = null;

         byte[] body = new byte[MESSAGE_SIZE];

         ByteBuffer bb = ByteBuffer.wrap(body);

         for (int j = 1; j <= MESSAGE_SIZE; j++) {
            bb.put(getSamplebyte(j));
         }

         for (int i = 0; i < numberOfMessages; i++) {
            message = session.createMessage(true);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            message.putIntProperty(SimpleString.of("id"), i);

            try {
               producer.send(message);
            } catch (Exception e) {
               // ignore
            }
         }
         assertTrue(Wait.waitFor(() -> server.getState() == ActiveMQServer.SERVER_STATE.STOPPED, 5000, 200));
         session.close();
         sf.close();
         locator.close();
      } finally {
         assertTrue(loggerHandler.findText("AMQ144010"));
      }
   }

   /**
    * This test will remove all the page directories during a restart, simulating a crash scenario. The server should
    * still start after this
    */
   @Test
   public void testDeletePhysicalPages() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig().setPersistDeliveryCountBeforeDelivery(true);

      config.setJournalSyncNonTransactional(false);

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 300;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(JournalPagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(JournalPagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
      session.close();

      session = null;

      sf.close();
      locator.close();

      server.stop();

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);
      server.start();

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);

      Queue queue = server.locateQueue(ADDRESS);

      Wait.assertEquals(numberOfMessages, queue::getMessageCount);

      ClientSession sessionConsumer = sf.createSession(false, false, false);
      sessionConsumer.start();
      ClientConsumer consumer = sessionConsumer.createConsumer(JournalPagingTest.ADDRESS);
      for (int msgCount = 0; msgCount < numberOfMessages; msgCount++) {
         logger.debug("Received {}", msgCount);
         ClientMessage msg = consumer.receive(100);
         if (msg == null) {
            logger.debug("It's null. leaving now");
            sessionConsumer.commit();
            fail("Didn't receive a message");
         }
         msg.acknowledge();

         if (msgCount % 5 == 0) {
            logger.debug("commit");
            sessionConsumer.commit();
         }
      }

      sessionConsumer.commit();

      sessionConsumer.close();

      sf.close();

      locator.close();

      Wait.assertEquals(0, queue::getMessageCount);

      Wait.assertFalse(queue.getPagingStore()::isPaging, 1000, 100);

      server.stop();

      // Deleting the paging data. Simulating a failure
      // a dumb user, or anything that will remove the data
      deleteDirectory(new File(getPageDir()));

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);
      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      queue = server.locateQueue(ADDRESS);

      sf = createSessionFactory(locator);
      session = sf.createSession(false, false, false);

      producer = session.createProducer(JournalPagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages * 2; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("theid"), i);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }

      session.commit();

      server.stop();

      server = createServer(true, config, JournalPagingTest.PAGE_SIZE, JournalPagingTest.PAGE_MAX);
      server.start();

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);

      sessionConsumer = sf.createSession(false, false, false);
      sessionConsumer.start();
      consumer = sessionConsumer.createConsumer(JournalPagingTest.ADDRESS);
      for (int msgCount = 0; msgCount < numberOfMessages; msgCount++) {
         logger.debug("Received {}", msgCount);
         ClientMessage msg = consumer.receive(100);
         if (msg == null) {
            logger.debug("It's null. leaving now");
            sessionConsumer.commit();
            fail("Didn't receive a message");
         }
         msg.acknowledge();

         if (msgCount % 5 == 0) {
            logger.debug("commit");
            sessionConsumer.commit();
         }
      }

      sessionConsumer.commit();

      sessionConsumer.close();

   }

   @Override
   protected void applySettings(ActiveMQServer server,
                                final Configuration configuration,
                                final int pageSize,
                                final long maxAddressSize,
                                final Integer maxReadPageMessages,
                                final Integer maxReadPageBytes,
                                final Map<String, AddressSettings> settings) {
      server.getConfiguration().setAddressQueueScanPeriod(100);
   }
}
