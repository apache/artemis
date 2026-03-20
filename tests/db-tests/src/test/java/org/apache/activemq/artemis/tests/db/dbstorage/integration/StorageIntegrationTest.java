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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.paging.dbimpl.DatabasePagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.BatchingIDGenerator;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.DatabaseStorageMessageReader;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.MessageData;
import org.apache.artemis.database.queries.GenericDataJDBCQuery;
import org.apache.artemis.database.queries.QueryUtil;
import org.apache.artemis.database.sql.SQLProvider;
import org.apache.artemis.database.worker.DataWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class StorageIntegrationTest extends AbstractStatementTest {

   private static final String QUEUE_NAME = "queue" + RandomUtil.randomUUIDString();

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeEach
   @Override
   public void setupTest() throws Exception {
      super.setupTest();
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName("DLQ")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName("ExpiryQueue")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));
   }

   @Override
   protected boolean waitForBindings(ActiveMQServer server,
                                     String address,
                                     boolean local,
                                     int expectedBindingCount,
                                     int expectedConsumerCount,
                                     long timeout) throws Exception {
      return super.waitForBindings(server, address, local, expectedBindingCount, expectedConsumerCount, timeout);
   }

   @TestTemplate
   public void testSimpleTXSend() throws Exception {

      ActiveMQServer server = createServer(true, configuration);

      server.start();

      int nMessages = 0;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < 10; i++) {
               producer.send(session.createTextMessage("test: " + i));
               nMessages++;
            }
            session.commit();
         }

         checkMessageCounts(nMessages, false);
      }
   }

   @TestTemplate
   public void testCreateAddress() throws Exception {
      ActiveMQServer server = createServer(true, configuration);
      server.start();
      server.addAddressInfo(new AddressInfo("test").addRoutingType(RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo("test1").addRoutingType(RoutingType.ANYCAST));
      server.getStorageManager().waitOnOperations();
      server.stop();

      server = createServer(true, configuration);
      server.start();

      assertNotNull(server.getAddressInfo(SimpleString.of("test")));
      assertNotNull(server.getAddressInfo(SimpleString.of("test1")));
      server.stop();
   }

   @TestTemplate
   public void testValidStoreID() throws Exception {
      ActiveMQServer server = createServer(true, configuration);
      server.start();
      long firstID = server.getStorageManager().generateID();
      assertTrue(firstID > 0, "ID should have been generated");
      assertTrue(getLastID() > 10_000);
      server.stop();

      assertTrue(getLastID() < 10_000);

      server.start();

      long secondID = server.getStorageManager().generateID();

      logger.info("SecondID = {}", secondID);

      assertTrue(secondID > firstID);

      assertTrue(getLastID() > 10_000);

      server.stop();
      assertTrue(getLastID() < 10_000);

      server.start();

      long thirdID = server.getStorageManager().generateID();
      assertTrue(thirdID > secondID);

      assertTrue(getLastID() > 10_000);
      server.stop();

      assertTrue(getLastID() < 10_000);
   }


   long getLastID() throws Exception {
      long lastIDRecord = -1L;
      List<BatchingIDGenerator.IDCounterEncoding> records = getIDRecords();
      for (BatchingIDGenerator.IDCounterEncoding r : records) {
         lastIDRecord = r.id;
         System.out.println(r);
      }
      return lastIDRecord;
   }

   List<BatchingIDGenerator.IDCounterEncoding> getIDRecords() throws Exception {
      ArrayList<BatchingIDGenerator.IDCounterEncoding> list = new ArrayList<>();
      DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
      try (Connection connection = databaseProvider.getConnection()) {
         GenericDataJDBCQuery query = new GenericDataJDBCQuery(databaseProvider, connection, databaseProvider.getSqlProvider().getConfigData(), true);
         query.query(data -> {
            if (data.recordType == JournalRecordIds.ID_COUNTER_RECORD) {
               list.add((BatchingIDGenerator.IDCounterEncoding) DatabaseStorageManager.describeGenericData(data.id, data.recordType, data.dataSupplier != null ? data.dataSupplier.get() : null));
            }
         });
      }
      return list;
   }

   @TestTemplate
   public void testSendAndRestart() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      server.start();

      int nMessages = 0;

      String[] protocols = {"CORE", "AMQP", "OPENWIRE"};
      for (String p : protocols) {
         ConnectionFactory factory = CFUtil.createConnectionFactory(p, "tcp://localhost:61616");
         try (javax.jms.Connection connection = factory.createConnection()) {
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
               MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
               for (int i = 0; i < 10; i++) {
                  producer.send(session.createTextMessage("test: " + p));
                  nMessages++;
               }
            }

            checkMessageCounts(nMessages, false);

            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               int beforeCommit = nMessages;
               MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
               for (int i = 0; i < 10; i++) {
                  producer.send(session.createTextMessage("test: " + p));
                  nMessages++;
               }
               checkMessageCounts(beforeCommit, false);
               session.commit();
               checkMessageCounts(nMessages, false);
            }
         }

         server.stop();

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
            server.start();
            assertFalse(loggerHandler.findTrace("ORA-0001"));
            assertFalse(loggerHandler.findTrace("java.sql.BatchUpdateException"));
         }

         try (javax.jms.Connection connection = factory.createConnection()) {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               connection.start();
               MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
               for (int i = 0; i < nMessages; i++) {
                  assertNotNull(consumer.receive(5000));
               }

               checkMessageCounts(nMessages, false);
               session.commit();
               checkMessageCounts(0, true);
               nMessages = 0;
            }
         }
      }
   }



   @TestTemplate
   public void testTransactionalSend() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      server.start();

      int nMessages = 0;

      String[] protocols = {"CORE", "AMQP", "OPENWIRE"};
      for (String p : protocols) {
         ConnectionFactory factory = CFUtil.createConnectionFactory(p, "tcp://localhost:61616");
         try (javax.jms.Connection connection = factory.createConnection()) {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
               for (int i = 0; i < 10000; i++) {
                  producer.send(session.createTextMessage("test: " + p));
                  if ((i + 1) % 1000 == 0) {
                     session.commit();
                  }
                  nMessages++;
               }
               session.commit();
            }

            checkMessageCounts(nMessages, false);
         }

         server.stop();
         server.start();

         try (javax.jms.Connection connection = factory.createConnection()) {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               connection.start();
               MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
               for (int i = 0; i < nMessages; i++) {
                  assertNotNull(consumer.receive(5000));
                  if ((i + 1) % 1000 == 0) {
                     session.commit();
                  }
               }
               session.commit();

               checkMessageCounts(0, true);
               nMessages = 0;
            }
         }
      }
   }


   @TestTemplate
   public void testSendWithDuplicateID() throws Exception {
      int cacheSize = 120;
      configuration.setIDCacheSize(cacheSize);
      ActiveMQServer server = createServer(true, configuration);

      server.start();

      int nMessages = 50;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages; i++) {
               javax.jms.TextMessage message = session.createTextMessage("test: " + i);
               message.setStringProperty(Message.HDR_DUPLICATE_DETECTION_ID.toString(), "uniqueID-" + i);
               producer.send(message);
               if (i % 10 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }

         checkMessageCounts(nMessages, false);
      }

      server.stop();
      server.start();
      try (javax.jms.Connection connection = factory.createConnection()) {

         // send duplicates - half of these should be filtered out
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages * 2; i++) {
               javax.jms.TextMessage message = session.createTextMessage("duplicate: " + i);
               message.setStringProperty(Message.HDR_DUPLICATE_DETECTION_ID.toString(), "uniqueID-" + i);
               producer.send(message);
            }
         }

         // count should remain the same since duplicates are filtered
         checkMessageCounts(nMessages * 2, false);
      }

      server.stop();
      server.start();

      // verify messages survive restart
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages * 2; i++) {
               assertNotNull(consumer.receive(5000));
            }
            session.commit();
            checkMessageCounts(0, true);
         }
      }

      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = nMessages * 2; i < nMessages * 4; i++) {
               javax.jms.TextMessage message = session.createTextMessage("test: " + i);
               message.setStringProperty(Message.HDR_DUPLICATE_DETECTION_ID.toString(), "uniqueID-" + i);
               producer.send(message);
               if (i % 10 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }
      }

      DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
      try (Connection connection = databaseProvider.getConnection()) {
         // max duplicate detection records should clean these up
         Wait.assertEquals((long)cacheSize, () -> (long)selectNumber(connection, "SELECT COUNT(*) FROM " + databaseProvider.getSqlProvider().getBrokerData() + " WHERE RECORD_TYPE = " + JournalRecordIds.DUPLICATE_ID), 5000, 200);
      }
      server.stop();

   }



   @TestTemplate
   public void testRemoveJsonConfigFromQueue() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      server.start();

      int nMessages = 10;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages; i++) {
               producer.send(session.createTextMessage("test: " + i));
            }
         }
         checkMessageCounts(nMessages, false);
      }

      server.stop();

      DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
      try (Connection connection = databaseProvider.getConnection()) {
         // Removing queue_config from the table
         // This is to simulate a case where users manually changed the database
         connection.createStatement().executeUpdate("UPDATE " + databaseProvider.getSqlProvider().getQueue() + " SET QUEUE_CONFIG = NULL");
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server = createServer(true, configuration);
         server.start();
         assertTrue(loggerHandler.findText("AMQ232000"));
      }

      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages; i++) {
               assertNotNull(consumer.receive(5000));
            }
            session.commit();
            checkMessageCounts(0, true);
         }
      }
   }

   @TestTemplate
   public void testValidateCleanupRemoveReferences() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      server.start();

      int nMessages = 50;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages; i++) {
               javax.jms.TextMessage message = session.createTextMessage("test: " + i);
               message.setStringProperty(Message.HDR_DUPLICATE_DETECTION_ID.toString(), "uniqueID-" + i);
               producer.send(message);
               if (i % 10 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }

         checkMessageCounts(nMessages, false);
      }

      server.stop();

      DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
      try (Connection connection = databaseProvider.getConnection()) {
         connection.createStatement().execute("DELETE FROM " + databaseProvider.getSqlProvider().getRefs());
      }

      try (AssertionLoggerHandler handler = new AssertionLoggerHandler()) {
         server.start();
         assertTrue(handler.findText("AMQ221019"));
      }

      checkMessageCounts(0, true);
   }


   // This is using the DatabaseStorageMessageReader directly to validate the depage process
   @TestTemplate
   public void testPagingDepageDirectly() throws Exception {
      internalTestPagingDepageDirectly(true);
   }

   @TestTemplate
   public void testPagingDepageDirectlyNoJoinFetch() throws Exception {
      internalTestPagingDepageDirectly(false);
   }

   private void internalTestPagingDepageDirectly(boolean pageJoinFetch) throws Exception {
      storageConfiguration.setPageJoinFetch(pageJoinFetch);
      int nMessages = 100;

      ActiveMQServer server = createServer(true, configuration);
      server.getConfiguration().getAddressSettings().clear();
      AddressSettings settingPaging = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(nMessages / 2);
      server.getConfiguration().addAddressSetting("#", settingPaging);
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration()
         .setName(QUEUE_NAME)
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      server.start();

      Queue queue = server.locateQueue(QUEUE_NAME);
      queue.pause();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
               for (int i = 0; i < nMessages; i++) {
                  javax.jms.TextMessage message = session.createTextMessage("test: " + i);
                  producer.send(message);
               }
               assertTrue(loggerHandler.findText("AMQ222038"));
               DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
               validateNewDBTotalMessages(databaseProvider, nMessages, nMessages);
            }
         }
      }

      DatabaseStorageMessageReader databaseStorageMessageReader = (DatabaseStorageMessageReader) queue.getStorageMessageReader();
      Wait.assertEquals(50L, databaseStorageMessageReader::getPagedMessages, 5000, 100);
      assertTrue(databaseStorageMessageReader.getPagedBytes() > 0);

      server.stop();
      validateNewDBTotalMessages(storageConfiguration.getDatabaseProvider(), nMessages, nMessages);
      server.start();
      validateNewDBTotalMessages(storageConfiguration.getDatabaseProvider(), nMessages, nMessages);

      DatabaseStorageManager databaseStorageManager = (DatabaseStorageManager) server.getStorageManager();

      ExecutorService service = Executors.newSingleThreadExecutor();
      runAfter(service::shutdownNow);

      CountDownLatch done = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger(0);
      AtomicInteger totalMessages = new AtomicInteger(0);
      long queueID = queue.getID();
      databaseStorageManager.getDataManager().executeQuery(service, worker -> consumePendingMessages(worker, queueID, done, totalMessages, errors, pageJoinFetch), null);
      assertTrue(done.await(10, TimeUnit.SECONDS));
      assertEquals(0, errors.get());
      assertEquals(50, totalMessages.get());

      DatabaseStorageMessageReader messageReader = (DatabaseStorageMessageReader) queue.getStorageMessageReader();
      assertEquals(50L, messageReader.getPagedMessages());

      DatabasePagingStoreImpl pagingStore = (DatabasePagingStoreImpl) queue.getPagingStore();

      QueueImpl mockQueue = Mockito.mock(QueueImpl.class);
      Mockito.when(mockQueue.getID()).thenReturn(queueID);
      Mockito.doReturn(true).when(mockQueue).needsDepage();
      Mockito.when(mockQueue.getExecutor()).thenReturn(ArtemisExecutor.delegate(service));


      ReusableLatch latch = new ReusableLatch(1);
      Mockito.doAnswer(invocation -> {
         latch.countDown();
         return null;
      }).when(mockQueue).deliverAsync();

      ArrayList<MessageReferenceImpl> references = new ArrayList<>();


      Mockito.doAnswer(invocation -> {
         MessageReference ref = invocation.getArgument(0);
         ref.getMessage().refUp();
         return null;
      }).when(mockQueue).refUp(Mockito.any(MessageReference.class));

      Mockito.doAnswer(invocation -> {
         references.add(invocation.getArgument(0));
         return null;
      }).when(mockQueue).addTail(Mockito.any(MessageReference.class), Mockito.anyBoolean());

      AtomicInteger prefetch = new AtomicInteger(10);

      DatabaseStorageMessageReader storageMessageReader = new DatabaseStorageMessageReader(mockQueue, databaseStorageManager, prefetch::get, () -> 1024 * 1024);
      storageMessageReader.addPending(100, 1024 * 1024);
      storageMessageReader.scheduleRead(false);

      assertTrue(latch.await(10, TimeUnit.MINUTES));
      assertEquals(10, references.size());

      latch.setCount(1);
      prefetch.set(40); // next prefetch now should return the remaining

      storageMessageReader.scheduleRead(false);

      assertTrue(latch.await(1, TimeUnit.MINUTES));
      assertEquals(50, references.size());

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         for (MessageReferenceImpl ref : references) {
            // simulating the ack
            ref.getMessage().refDown();
         }

         assertFalse(loggerHandler.findText("AMQ214034"));
      }
   }

   private static final int OK = 1;
   private static final int NO_OK = 2;
   public static void main(String[] arg) {
      try {
         ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
         try (javax.jms.Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));

            int commitID = 0;
            while (true) {
               for (int i = 0; i < 100; i++) {
                  producer.send(session.createTextMessage("hello"));
               }
               if (commitID++ > 2) {
                  System.out.println("COMMIT");
               }
               session.commit();
               for (int i = 0; i < 100; i++) {
                  consumer.receive(1000);
               }
               session.commit();

            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(NO_OK);
      }
   }

   @TestTemplate
   public void testInterruptProducer() throws Exception {
      internalTestInterruptProducer(true);
   }

   @TestTemplate
   public void testInterruptProducerNoJoinFetch() throws Exception {
      internalTestInterruptProducer(false);
   }

   private void internalTestInterruptProducer(boolean pageJoinFetch) throws Exception {
      storageConfiguration.setPageJoinFetch(pageJoinFetch);

      ActiveMQServer server = createServer(true, configuration);
      server.getConfiguration().getAddressSettings().clear();
      AddressSettings settingPaging = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(10).setMaxReadPageMessages(50).setPrefetchPageMessages(100);
      server.getConfiguration().addAddressSetting("#", settingPaging);
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration()
                                                           .setName(QUEUE_NAME)
                                                           .addRoutingType(RoutingType.ANYCAST)
                                                           .addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      server.start();

      Wait.assertTrue(() -> server.locateQueue(QUEUE_NAME) != null);
      Queue queue = server.locateQueue(QUEUE_NAME);

      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<Process> processRef = new AtomicReference<>();
      Process process = SpawnedVMSupport.spawnVMWithLogMacher("COMMIT", () -> {
         Process p = processRef.get();
         if (p != null) {
            p.destroy();
            done.countDown();
         }
      }, StorageIntegrationTest.class.getName(), new String[0], true);
      processRef.set(process);
      runAfter(process::destroyForcibly);

      assertTrue(done.await(60, TimeUnit.SECONDS));

      ConnectionFactory cf = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         try (javax.jms.Connection connection = cf.createConnection()) {
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
               long messages = queue.getMessageCount();
               MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
               for (long i = 0; i < messages; i++) {
                  assertNotNull(consumer.receive(500));
               }
            }
         }
         assertFalse(loggerHandler.findText("AMQ222704"));
      }
   }

   @TestTemplate
   public void testProducePagedThanConsume() throws Exception {
      internalTestProducePagedThanConsume(true);
   }

   @TestTemplate
   public void testProducePagedThanConsumeNoJoinFetch() throws Exception {
      internalTestProducePagedThanConsume(false);
   }

   private void internalTestProducePagedThanConsume(boolean pageJoinFetch) throws Exception {
      storageConfiguration.setPageJoinFetch(pageJoinFetch);

      int nMessages = 100;

      ActiveMQServer server = createServer(true, configuration);
      server.getConfiguration().getAddressSettings().clear();
      AddressSettings settingPaging = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(nMessages / 2).setMaxReadPageMessages(50);
      server.getConfiguration().addAddressSetting("#", settingPaging);
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration()
                                                           .setName(QUEUE_NAME)
                                                           .addRoutingType(RoutingType.ANYCAST)
                                                           .addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      server.start();

      Wait.assertTrue(() -> server.locateQueue(QUEUE_NAME) != null);
      Queue queue = server.locateQueue(QUEUE_NAME);
      queue.pause();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
               for (int i = 0; i < nMessages; i++) {
                  javax.jms.TextMessage message = session.createTextMessage("test: " + i);
                  producer.send(message);
               }
               session.commit();
               // a second one just to make things more challenging
               for (int i = 0; i < nMessages; i++) {
                  javax.jms.TextMessage message = session.createTextMessage("test: " + i);
                  producer.send(message);
               }
               session.rollback();
               assertTrue(loggerHandler.findText("AMQ222038"));
               DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
               validateNewDBTotalMessages(databaseProvider, nMessages, nMessages);
            }
         }
      }

      {
         DatabaseStorageMessageReader databaseStorageMessageReader = (DatabaseStorageMessageReader) queue.getStorageMessageReader();
         Wait.assertTrue(() -> databaseStorageMessageReader.getPagedMessages() > 0, 5000, 100);
         assertTrue(databaseStorageMessageReader.getPagedBytes() > 0);
      }

      server.stop();
      validateNewDBTotalMessages(storageConfiguration.getDatabaseProvider(), nMessages, nMessages);
      server.start();
      validateNewDBTotalMessages(storageConfiguration.getDatabaseProvider(), nMessages, nMessages);

      {
         queue = server.locateQueue(QUEUE_NAME);
         DatabaseStorageMessageReader databaseStorageMessageReader = (DatabaseStorageMessageReader) queue.getStorageMessageReader();
         Wait.assertTrue(() -> databaseStorageMessageReader.getPagedMessages() > 0, 5000, 100);
      }

      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            connection.start();

            for (int i = 0; i < nMessages; i++) {
               assertNotNull(consumer.receive(5000));
            }
         }
      }

      checkMessageCounts(0, true);
   }


   private void consumePendingMessages(DataWorker worker, long queueID, CountDownLatch done, AtomicInteger totalMessages, AtomicInteger errors, boolean pageJoinFetch) {
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
         SQLProvider sqlProvider = databaseProvider.getSqlProvider();
         try (ResultSet resultSet = worker.pendingDeliveryQueryForUpdate.execute(queueID)) {
            if (pageJoinFetch) {
               while (resultSet.next()) {
                  MessageData messageData = QueryUtil.readMessageData(resultSet, 1, 2, 3);
                  logger.debug("Data:: {}", messageData);
                  totalMessages.incrementAndGet();
               }
            } else {
               ArrayList<String> ids = new ArrayList<>();
               while (resultSet.next()) {
                  ids.add(resultSet.getString(1));
               }
               if (!ids.isEmpty()) {
                  try (Connection conn = databaseProvider.getConnection();
                       Statement stmt = conn.createStatement();
                       ResultSet blobRecords = stmt.executeQuery(sqlProvider.selectMessagesBlob(sqlProvider.getMessages(), ids))) {
                     while (blobRecords.next()) {
                        MessageData messageData = QueryUtil.readMessageData(blobRecords, 1, 2, 3);
                        logger.debug("Data:: {}", messageData);
                        totalMessages.incrementAndGet();
                     }
                  }
               }
            }
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }
   }

   @TestTemplate
   public void testTopicWithTwoSubscriptions() throws Exception {
      ActiveMQServer server = createServer(true, configuration);
      server.start();

      int numberOfMessages = 100;
      String topicName = "topic" + RandomUtil.randomUUIDString();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.setClientID("testClient");
         connection.start();

         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Topic topic = session.createTopic(topicName);

            MessageConsumer sub1 = session.createDurableSubscriber(topic, "sub1");
            MessageConsumer sub2 = session.createDurableSubscriber(topic, "sub2");

            MessageProducer producer = session.createProducer(topic);
            for (int i = 0; i < numberOfMessages; i++) {
               producer.send(session.createTextMessage("test: " + i));
            }
            session.commit();

            DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
            validateNewDBTotalMessages(databaseProvider, numberOfMessages, numberOfMessages * 2);

            sub1.close();
            sub2.close();
         }
      }

      server.stop();
   }

   @TestTemplate
   public void testSingleMessagePagingCleanup() throws Exception {
      internalTestSingleMessagePagingCleanup(true);
   }

   @TestTemplate
   public void testSingleMessagePagingCleanupNoJoinFetch() throws Exception {
      internalTestSingleMessagePagingCleanup(false);
   }

   private void internalTestSingleMessagePagingCleanup(boolean pageJoinFetch) throws Exception {
      storageConfiguration.setPageJoinFetch(pageJoinFetch);

      ActiveMQServer server = createServer(true, configuration);
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(0));
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration()
         .setName(QUEUE_NAME)
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      server.start();

      Queue queue = server.locateQueue(QUEUE_NAME);
      assertNotNull(queue);
      queue.getPagingStore().startPaging();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            producer.send(session.createTextMessage("single message"));
            session.commit();
         }

         checkMessageCounts(1, false);

         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            assertNotNull(consumer.receive(5000));
            session.commit();
         }

         checkMessageCounts(0, true);
      }

      server.stop();
   }

   @TestTemplate
   public void testMulticastTwoSubscribersPagingCleanup() throws Exception {
      internalTestMulticastTwoSubscribersPagingCleanup(true);
   }

   @TestTemplate
   public void testMulticastTwoSubscribersPagingCleanupNoJoinFetch() throws Exception {
      internalTestMulticastTwoSubscribersPagingCleanup(false);
   }

   private void internalTestMulticastTwoSubscribersPagingCleanup(boolean pageJoinFetch) throws Exception {
      storageConfiguration.setPageJoinFetch(pageJoinFetch);

      String addressName = "multicastAddress" + RandomUtil.randomUUIDString();
      String queue1Name = "sub1" + RandomUtil.randomUUIDString();
      String queue2Name = "sub2" + RandomUtil.randomUUIDString();

      ActiveMQServer server = createServer(true, configuration);
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(0));
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration()
         .setName(addressName)
         .addRoutingType(RoutingType.MULTICAST)
         .addQueueConfiguration(QueueConfiguration.of(queue1Name).setAddress(addressName).setRoutingType(RoutingType.MULTICAST))
         .addQueueConfiguration(QueueConfiguration.of(queue2Name).setAddress(addressName).setRoutingType(RoutingType.MULTICAST)));
      server.start();

      Queue queue1 = server.locateQueue(queue1Name);
      assertNotNull(queue1);
      queue1.getPagingStore().startPaging();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Topic topic = session.createTopic(addressName);
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("single message"));
            session.commit();
         }

         // 1 message, but 2 references (one per subscription)
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();
         SQLProvider sqlProvider = databaseProvider.getSqlProvider();
         try (Connection jdbcConnection = databaseProvider.getConnection()) {
            assertEquals(1, selectCount(jdbcConnection, sqlProvider.getMessages()));
            assertEquals(2, selectCount(jdbcConnection, sqlProvider.getRefs()));
         }

         connection.start();

         // Consume from first subscription
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(queue1Name));
            assertNotNull(consumer.receive(5000));
            session.commit();
         }

         // Message should still exist (one reference remaining)
         try (Connection jdbcConnection = databaseProvider.getConnection()) {
            Wait.assertEquals(1, () -> selectCount(jdbcConnection, sqlProvider.getRefs()), 5000, 100);
            assertEquals(1, selectCount(jdbcConnection, sqlProvider.getMessages()));
         }

         // Consume from second subscription
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(queue2Name));
            assertNotNull(consumer.receive(5000));
            session.commit();
         }

         // Now both references gone, message should be deleted
         try (Connection jdbcConnection = databaseProvider.getConnection()) {
            Wait.assertEquals(0, () -> selectCount(jdbcConnection, sqlProvider.getRefs()), 5000, 100);
            Wait.assertEquals(0, () -> selectCount(jdbcConnection, sqlProvider.getMessages()), 5000, 100);
         }
      }

      server.stop();
   }

   @TestTemplate
   public void testBrowsing() throws Exception {
      testBrowsing(false);
   }

   @TestTemplate
   public void testBrowsingPaging() throws Exception {
      testBrowsing(true);
   }

   private void testBrowsing(boolean paging) throws Exception {
      int nMessages = 5000;

      int maxSizeMessagesWhenPaging = 10;

      ActiveMQServer server = createServer(true, configuration);
      if (paging) {
         server.getConfiguration().getAddressSettings().clear();
         server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(maxSizeMessagesWhenPaging));
      }
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration()
         .setName(QUEUE_NAME)
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
      server.start();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 1; i <= nMessages; i++) {
               javax.jms.Message message = session.createTextMessage("test-" + i);
               message.setIntProperty("i", i);
               producer.send(message);
               if (i % 100 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }

         Queue serverQueue = server.locateQueue(QUEUE_NAME);
         Wait.assertEquals((long)nMessages, serverQueue::getMessageCount, 5000, 100);

         try (LinkedListIterator<MessageReference> referenceLinkedListIterator = serverQueue.browserIterator()) {
            int browsedCount = 1;
            while (referenceLinkedListIterator.hasNext()) {
               MessageReference ref = referenceLinkedListIterator.next();
               Message message = ref.getMessage();
               assertNotNull(message);
               logger.info("message {}, id={}", message, message.getIntProperty("i"));
               assertEquals(browsedCount, message.getIntProperty("i"));
               browsedCount++;
            }
            assertEquals(nMessages, browsedCount - 1);
         }

         // browsing should not consume messages
         checkMessageCounts(nMessages, false);
      }

      server.stop();
   }

}