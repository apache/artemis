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
package org.apache.activemq.artemis.tests.db.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCursorProviderImpl;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCursorProviderTestAccessor;
import org.apache.activemq.artemis.core.paging.cursor.impl.PagePositionImpl;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionImpl;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryDatabase;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreTestAccessor;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.AckDescribe;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueAccessor;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManagerImpl;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.LongHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class PagingTest extends ParameterDBTestBase {

   protected static final int RECEIVE_TIMEOUT = 5000;
   protected static final int PAGE_MAX = 100 * 1024;
   protected static final int PAGE_SIZE = 10 * 1024;
   static final int MESSAGE_SIZE = 1024; // 1k
   static final int LARGE_MESSAGE_SIZE = 100 * 1024;
   static final SimpleString ADDRESS = SimpleString.of("TestADDRESS");
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected ServerLocator locator;
   protected ActiveMQServer server;
   protected ClientSessionFactory sf;
   private AssertionLoggerHandler loggerHandler;

   @Parameter(index = 1)
   public boolean purgeFolders;


   @Override
   protected Configuration createDefaultInVMConfig() throws Exception {
      return super.createDefaultConfig(0, false).setPurgePageFolders(purgeFolders);
   }

   @Parameters(name = "db={0}, purgeFolders={1}")
   public static Collection<Object[]> parameters() {
      List<Object[]> databases = new ArrayList<>();
      databases.add(new Object[] {Database.JOURNAL, true});
      databases.add(new Object[] {Database.JOURNAL, false});
      List<Database> selectedList = Database.selectedList();
      if (selectedList != null && !selectedList.isEmpty()) {
         selectedList.forEach(d -> databases.add(new Object[]{d, false}));
      }
      return databases;
   }

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

   @TestTemplate
   public void testPageOnLargeMessageMultipleQueues() throws Exception {

      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 0;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1);
      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, false, false, false, 0);

      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-0")).setAddress(ADDRESS));
      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-1")).setAddress(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < 50; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[LARGE_MESSAGE_SIZE]);

         producer.send(message);
      }
      session.commit();

      session.close();

      for (int ad = 0; ad < 2; ad++) {
         session = sf.createSession(false, false, false);

         ClientConsumer consumer = session.createConsumer(ADDRESS.concat("-" + ad));

         session.start();

         for (int i = 0; i < 10; i++) {
            ClientMessage message2 = consumer.receive(10000);

            assertNotNull(message2, "message was null, ad= " + ad);

            message2.acknowledge();
         }

         if (ad >= 1) {
            session.commit();
         } else {
            session.rollback();
            for (int i = 0; i < 25; i++) {
               ClientMessage message2 = consumer.receive(1000);

               assertNotNull(message2);

               message2.acknowledge();

               assertNotNull(message2);
            }
            session.commit();

         }

         consumer.close();

         session.close();
      }
   }

   @TestTemplate
   public void testPageTX() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         Configuration config = createDefaultInVMConfig();

         final int PAGE_MAX = 1024;

         final int PAGE_SIZE = 10 * 1024;

         server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
         server.start();

         server.getAddressSettingsRepository().clear();
         server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setMaxReadPageBytes(-1).setMaxSizeMessages(1));

         final int numberOfBytes = 1024;

         locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         ClientSession session = sf.createSession(null, null, false, false, false, false, 0);

         session.createQueue(QueueConfiguration.of(ADDRESS.concat("-0")).setAddress(ADDRESS));

         server.getPagingManager().getPageStore(ADDRESS).forceAnotherPage(true);
         server.getPagingManager().getPageStore(ADDRESS).disableCleanup();
         session.start();

         ClientProducer producer = session.createProducer(ADDRESS);

         ClientConsumer browserConsumer = session.createConsumer(ADDRESS.concat("-0"), true);

         ClientMessage message = null;

         for (int i = 0; i < 20; i++) {
            message = session.createMessage(true);

            message.getBodyBuffer().writerIndex(0);

            message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

            for (int j = 1; j <= numberOfBytes; j++) {
               message.getBodyBuffer().writeInt(j);
            }

            producer.send(message);
         }
         session.commit();

         ClientConsumer consumer = session.createConsumer(ADDRESS.concat("-0"));

         session.start();

         for (int i = 0; i < 20; i++) {
            ClientMessage message2 = consumer.receive(10000);

            assertNotNull(message2);

            message2.acknowledge();

            assertNotNull(message2);

         }
         session.commit();

         consumer.close();

         Queue queue = server.locateQueue(ADDRESS.concat("-0"));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
         PageCursorProvider provider = store.getCursorProvider();

         PageSubscription cursorSubscription = provider.getSubscription(queue.getID());
         PageIterator iterator = cursorSubscription.iterator();

         for (int i = 0; i < 5; i++) {
            assertFalse(iterator.hasNext());
            assertNull(browserConsumer.receiveImmediate());
         }

         session.close();
         assertFalse(loggerHandler.findText("Could not locate page"));
         assertFalse(loggerHandler.findText("AMQ222029"));
         server.getPagingManager().getPageStore(ADDRESS).enableCleanup();
         Wait.assertFalse(server.getPagingManager().getPageStore(ADDRESS)::isPaging);
      }
   }

   @TestTemplate
   public void testSimpleCursorIterator() throws Exception {
      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
      server.start();

      final int numberOfBytes = 124;

      final int NUMBER_OF_MESSAGES = 201;

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, false, false, false, 0);

      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-0")).setAddress(ADDRESS));

      server.getPagingManager().getPageStore(ADDRESS).startPaging();
      server.getPagingManager().getPageStore(ADDRESS).disableCleanup();
      session.start();

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

         for (int j = 1; j <= numberOfBytes; j++) {
            message.getBodyBuffer().writeInt(j);
         }

         message.putIntProperty("i", i);

         producer.send(message);
      }

      session.commit();

      Queue queue = server.locateQueue(ADDRESS.concat("-0"));

      PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
      PageCursorProvider provider = store.getCursorProvider();

      PageSubscription cursorSubscription = provider.getSubscription(queue.getID());
      PageIterator iterator = cursorSubscription.iterator(true);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         assertTrue(iterator.hasNext());
         PagedMessage messageReceived = iterator.next().getPagedMessage();
         logger.debug("Page {} , message = {}", messageReceived.getPageNumber(), messageReceived.getMessageNumber());
         assertNotNull(messageReceived);
         assertEquals(i, (int) messageReceived.getMessage().getIntProperty("i"));
      }

      assertFalse(iterator.hasNext());
   }

   @TestTemplate
   public void testSimpleCursorIteratorLargeMessage() throws Exception {
      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
      server.start();

      final int numberOfBytes = 200 * 1024;

      final int NUMBER_OF_MESSAGES = 50;

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, false, false, false, 0);

      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-0")).setAddress(ADDRESS));

      server.getPagingManager().getPageStore(ADDRESS).startPaging();
      server.getPagingManager().getPageStore(ADDRESS).disableCleanup();
      session.start();

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

         message.putIntProperty("i", i);

         producer.send(message);
      }

      session.commit();

      Queue queue = server.locateQueue(ADDRESS.concat("-0"));
      queue.pause();

      PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
      PageCursorProvider provider = store.getCursorProvider();

      PageSubscription cursorSubscription = provider.getSubscription(queue.getID());
      PageIterator iterator = cursorSubscription.iterator(true);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         assertTrue(iterator.hasNext());
         PagedMessage messageReceived = iterator.next().getPagedMessage();
         System.out.println("Page " + messageReceived.getPageNumber() + " , message = " + messageReceived.getMessageNumber());
         assertNotNull(messageReceived);
         assertEquals(i, (int) messageReceived.getMessage().getIntProperty("i"));
      }

      assertFalse(iterator.hasNext());
   }

   @TestTemplate
   public void testPageCleanup() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

         producer.send(message);
         if (i % 10 == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.close();

      session = sf.createSession(false, false, false);
      producer = session.createProducer(PagingTest.ADDRESS);
      producer.send(session.createMessage(true));
      session.rollback();
      producer.close();
      session.close();

      session = sf.createSession(false, false, false);
      producer = session.createProducer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);
         if (i % 10 == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.close();

      Queue queue = server.locateQueue(PagingTest.ADDRESS);

      session = sf.createSession(false, false, false);

      session.start();

      Wait.assertEquals(numberOfMessages * 2, queue::getMessageCount);

      // The consumer has to be created after the getMessageCount(queue) assertion
      // otherwise delivery could alter the messagecount and give us a false failure
      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);
      ClientMessage msg = null;

      for (int i = 0; i < numberOfMessages * 2; i++) {
         msg = consumer.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();
         if (i % 50 == 0) {
            session.commit();
         }
      }
      session.commit();
      consumer.close();
      session.close();

      sf.close();

      locator.close();

      Wait.assertEquals(0, queue::getMessageCount);

      waitForNotPaging(queue);

      server.stop();
   }

   @TestTemplate
   public void testPageReload() throws Exception {

      Configuration config = createDefaultConfig(true).setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      server.addAddressInfo(new AddressInfo(getName()).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));

      Queue serverQueue = server.locateQueue(getName());

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue jmsQueue = session.createQueue(getName());

         serverQueue.getPagingStore().startPaging();

         MessageProducer producer = session.createProducer(jmsQueue);

         for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
               producer.send(session.createMessage());
            }
            serverQueue.getPagingStore().forceAnotherPage(true);
         }
      }

      // Forcing a situation in the data that would cause an issue while reloading the data
      long tx = server.getStorageManager().generateID();
      server.getStorageManager().storePageCompleteTransactional(tx, serverQueue.getID(), new PagePositionImpl(1, 10));
      server.getStorageManager().commit(tx);
      server.getStorageManager().storeCursorAcknowledge(serverQueue.getID(), new PagePositionImpl(1, 0));

      server.stop();
      server.start();

      Queue serverQueueAfterRestart = server.locateQueue(getName());

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue jmsQueue = session.createQueue(getName());

         connection.start();
         MessageConsumer consumer = session.createConsumer(jmsQueue);

         for (int i = 0; i < 90; i++) {
            javax.jms.Message message = consumer.receive(1000);
            assertNotNull(message);
         }

         assertNull(consumer.receiveNoWait());
         Wait.assertFalse(serverQueueAfterRestart.getPagingStore()::isPaging);
      }

   }

   @TestTemplate
   public void testQueueRemoveAll() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 500;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

         producer.send(message);
         if (i % 100 == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.close();

      session = sf.createSession(false, false, false);
      producer = session.createProducer(PagingTest.ADDRESS);
      producer.send(session.createMessage(true));
      session.rollback();
      producer.close();
      session.close();

      session = sf.createSession(false, false, false);
      producer = session.createProducer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);
         if (i % 100 == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.close();

      Queue queue = server.locateQueue(PagingTest.ADDRESS);

      Wait.assertEquals(numberOfMessages * 2, queue::getMessageCount);

      QueueControl queueControl = (QueueControl) this.server.getManagementService().getResource(ResourceNames.QUEUE + ADDRESS);
      int removedMessages = queueControl.removeAllMessages();

      assertEquals(numberOfMessages * 2, removedMessages);
   }

   @TestTemplate
   public void testPageReadOneMessage() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      // one message max, maxReadPageBytes disabled
      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, 1, -1, new HashMap<>());

      server.start();

      final int numberOfMessages = 10;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      byte[] body = new byte[10];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= 10; j++) {
         bb.put(getSamplebyte(j));
      }

      Queue queue = server.locateQueue(ADDRESS);
      for (int repeat = 0; repeat < 2; repeat++) {
         queue.getPagingStore().startPaging();

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage message = session.createMessage(true);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            message.putIntProperty("i", i);

            producer.send(message);
         }
         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS);
         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage messReceived = consumer.receive(5000);
            assertNotNull(messReceived);
            System.out.println("Receiving " + messReceived);
            messReceived.acknowledge();
            session.commit();
         }
         consumer.close();
         Wait.assertFalse(queue.getPagingStore()::isPaging, 5000, 100);

         Wait.assertTrue(() -> PagingStoreTestAccessor.getUsedPagesSize(queue.getPagingStore()) <= 1, 1000, 100);

      }
      Wait.assertFalse(queue.getPagingStore()::isPaging, 5000, 100);
   }

   @TestTemplate
   public void testCleanupMiddlePageSingleQueue() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      String address = "testCleanupMiddlePage";

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      addServer(server);

      server.start();

      server.addAddressInfo(new AddressInfo(address).addRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(address + "_1").setAddress(address).setRoutingType(RoutingType.MULTICAST).setDurable(true).setFilterString("page<>5"));
      server.createQueue(QueueConfiguration.of(address + "_2").setAddress(address).setRoutingType(RoutingType.MULTICAST).setDurable(true).setFilterString("page=5"));

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      ClientProducer producer = session.createProducer(address);

      ClientMessage message = null;

      byte[] body = new byte[10];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= 10; j++) {
         bb.put(getSamplebyte(j));
      }

      Queue queue = server.locateQueue(address + "_1");
      queue.getPagingStore().startPaging();

      int page = 1;

      // all the messages on the last page will contain this constant, which it will be used to assert on the print-data
      String lastPageConstant = RandomUtil.randomUUIDString();

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
         if (page == 10) {
            message.putStringProperty("LAST_PAGE", lastPageConstant);
         }

         producer.send(message);
      }

      queue.getPagingStore().getCursorProvider().disableCleanup();

      ClientConsumer consumer = session.createConsumer(address + "_1", "page=3");
      session.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage msgRec = consumer.receive(1000);
         assertNotNull(msgRec);
         logger.debug("received i={} page={}", msgRec.getIntProperty("i"), msgRec.getIntProperty("page"));
         msgRec.acknowledge();
      }
      session.commit();

      assertEquals(10, queue.getPagingStore().getNumberOfPages());

      PageCursorProviderTestAccessor.cleanup(queue.getPagingStore().getCursorProvider());

      assertEquals(9, queue.getPagingStore().getNumberOfPages());

      {
         SequentialFileFactory factory = PagingStoreTestAccessor.getFileFactory(queue.getPagingStore());

         Wait.assertEquals(9, () -> factory.listFiles("page").size(), 5000, 100);
      }

      session.close();

      server.stop();

      server.start();

      queue = server.locateQueue(address + "_1");
      queue.getPagingStore().startPaging();

      {
         SequentialFileFactory factory = PagingStoreTestAccessor.getFileFactory(queue.getPagingStore());
         // Making sure restarting the server should not recreate a file
         assertEquals(9, factory.listFiles("page").size());

         sf = createSessionFactory(locator);

         session = sf.createSession(false, true, true);

         ClientConsumer browser = session.createConsumer(address + "_1", true);
         session.start();

         for (int i = 0; i < 80; i++) {
            ClientMessage msgRec = browser.receive(1000);
            assertNotNull(msgRec);

            logger.debug("received i={} page={}", msgRec.getIntProperty("i"), msgRec.getIntProperty("page"));

            int pageProperty = msgRec.getIntProperty("page");
            assertTrue(pageProperty != 5 && pageProperty != 3);
            msgRec.acknowledge();
         }
         assertNull(browser.receiveImmediate());
         session.commit();

         // Browsing should not recreate a file
         assertEquals(9, factory.listFiles("page").size());
      }

   }

   // Send messages in page, consume them
   // send again...
   // consume again
   // just once...
   // this test is similar to .testPageAndDepageRapidly however I needed a simpler version
   // easier to debug an issue during one development... I decided to then keep the simpler test
   @TestTemplate
   public void testSimpleResume() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      String address = "testSimpleResume";

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      server.addAddressInfo(new AddressInfo(address).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(address).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      final int numberOfMessages = 20;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      ClientProducer producer = session.createProducer(address);

      ClientMessage message = null;

      byte[] body = new byte[10];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= 10; j++) {
         bb.put(getSamplebyte(j));
      }

      Queue queue = server.locateQueue(address);

      for (int repeat = 0; repeat < 5; repeat++) {
         queue.getPagingStore().startPaging();
         int page = 1;

         for (int i = 0; i < numberOfMessages; i++) {
            if (i % 10 == 0 && i > 0) {
               queue.getPagingStore().forceAnotherPage(true);
               // forceAnotherPage could be called concurrently with cleanup on this case
               // so we have to call startPaging to make sure we are still paging on this test
               queue.getPagingStore().startPaging();
               page++;
            }
            message = session.createMessage(true);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            message.putIntProperty("i", i);
            message.putIntProperty("page", page);

            producer.send(message);
         }

         ClientConsumer consumer = session.createConsumer(address);
         session.start();

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msgRec = consumer.receive(10_000);
            assertNotNull(msgRec);
            logger.debug("msgRec, i={}, page={}", msgRec.getIntProperty("i"), msgRec.getIntProperty("page"));
            msgRec.acknowledge();
         }
         session.commit();

         consumer.close();
         PageCursorProviderTestAccessor.cleanup(queue.getPagingStore().getCursorProvider());

         Wait.assertFalse(queue.getPagingStore()::isPaging, 5000, 100);
      }
   }

   @TestTemplate
   public void testQueueRetryMessages() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, 100);

      server.start();

      final int numberOfMessages = 50;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "Queue").setAddress(PagingTest.ADDRESS));
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "QueueOriginal").setAddress(PagingTest.ADDRESS + "Original"));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

         producer.send(message);

         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);
         message.putStringProperty(Message.HDR_ORIGINAL_ADDRESS, PagingTest.ADDRESS + "Original");
         message.putStringProperty(Message.HDR_ORIGINAL_QUEUE, PagingTest.ADDRESS + "QueueOriginal");
         producer.send(message);
      }
      session.commit();
      producer.close();
      session.close();

      session = sf.createSession(false, false, false);
      producer = session.createProducer(PagingTest.ADDRESS);
      producer.send(session.createMessage(true));
      session.rollback();
      producer.close();
      session.close();

      session = sf.createSession(false, false, false);
      producer = session.createProducer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);

         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);
         message.putStringProperty(Message.HDR_ORIGINAL_ADDRESS, PagingTest.ADDRESS + "Original");
         message.putStringProperty(Message.HDR_ORIGINAL_QUEUE, PagingTest.ADDRESS + "QueueOriginal");
         producer.send(message);
      }
      session.commit();
      producer.close();
      session.close();

      Queue queue = server.locateQueue(SimpleString.of(PagingTest.ADDRESS + "Queue"));
      Queue originalQueue = server.locateQueue(SimpleString.of(PagingTest.ADDRESS + "QueueOriginal"));

      Wait.assertEquals(numberOfMessages * 4, queue::getMessageCount);
      Wait.assertEquals(0, originalQueue::getMessageCount);

      QueueControl queueControl = (QueueControl) this.server.getManagementService().getResource(ResourceNames.QUEUE + ADDRESS + "Queue");
      QueueControl originalQueueControl = (QueueControl) this.server.getManagementService().getResource(ResourceNames.QUEUE + ADDRESS + "QueueOriginal");
      queueControl.retryMessages();

      Wait.assertEquals(numberOfMessages * 2, queue::getMessageCount, 5000);
      Wait.assertEquals(numberOfMessages * 2, originalQueue::getMessageCount, 5000);
   }

   @TestTemplate
   public void testFqqn() throws Exception {
      final SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString fqqn = CompositeAddress.toFullyQualified(ADDRESS, queue);
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, 100, -1, -1);

      server.start();

      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setMaxReadPageBytes(-1).setMaxSizeMessages(0).setPageSizeBytes(PagingTest.PAGE_SIZE));

      final int numberOfMessages = 10;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(fqqn).setRoutingType(RoutingType.ANYCAST));

      ClientProducer producer = session.createProducer(fqqn);

      ClientMessage message = null;

      byte[] body = new byte[MESSAGE_SIZE];

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(persistentMessages);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
      }

      session.commit();

      Wait.assertTrue(server.getPagingManager().getPageStore(ADDRESS)::isPaging, 5000, 100);
      assertEquals(ADDRESS, server.getPagingManager().getPageStore(ADDRESS).getAddress());

      session.start();

      ClientConsumer consumer = session.createConsumer(fqqn);

      for (int i = 0; i < numberOfMessages; i++) {
         message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         assertEquals(i, message.getIntProperty("id").intValue());
      }

      session.commit();

      server.getPagingManager().deletePageStore(fqqn);
      assertFalse(Arrays.asList(server.getPagingManager().getStoreNames()).contains(ADDRESS));
   }

   // First page is complete but it wasn't deleted
   @TestTemplate
   public void testFirstPageCompleteNotDeleted() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 20;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);
      server.addAddressInfo(new AddressInfo(PagingTest.ADDRESS, RoutingType.ANYCAST));
      Queue queue = server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      queue.getPageSubscription().getPagingStore().startPaging();

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

         message.putIntProperty("count", i);

         producer.send(message);

         if ((i + 1) % 5 == 0) {
            session.commit();
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }

      session.commit();
      producer.close();
      session.close();

      // This will make the cursor to set the page complete and not actually delete it
      queue.getPageSubscription().getPagingStore().disableCleanup();

      session = sf.createSession(false, false, false);

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      session.start();

      for (int i = 0; i < 5; i++) {
         ClientMessage msg = consumer.receive(2000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("count").intValue());

         msg.individualAcknowledge();

      }

      session.commit();

      session.close();

      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      consumer = session.createConsumer(ADDRESS);
      session.start();

      for (int i = 5; i < numberOfMessages; i++) {
         ClientMessage msg = consumer.receive(2000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("count").intValue());
         msg.acknowledge();
      }

      assertNull(consumer.receiveImmediate());
      session.commit();

      session.close();
      sf.close();
      locator.close();

   }

   @TestTemplate
   public void testPreparedACKAndRestart() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int numberOfMessages = 50;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      Queue queue = server.locateQueue(PagingTest.ADDRESS);

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      queue.getPageSubscription().getPagingStore().startPaging();

      forcePage(queue);

      // Send many messages, 5 on each page
      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty("count", i);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);

         if ((i + 1) % 5 == 0) {
            session.commit();
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }

      session.close();

      session = sf.createSession(true, false, false);

      Xid xidConsumeNoCommit = newXID();
      session.start(xidConsumeNoCommit, XAResource.TMNOFLAGS);

      ClientConsumer cons = session.createConsumer(ADDRESS);

      session.start();

      // First message is consumed, prepared, will be rolled back later
      ClientMessage firstMessageConsumed = cons.receive(5000);
      assertNotNull(firstMessageConsumed);
      firstMessageConsumed.acknowledge();

      session.end(xidConsumeNoCommit, XAResource.TMSUCCESS);

      session.prepare(xidConsumeNoCommit);

      Xid xidConsumeCommit = newXID();
      session.start(xidConsumeCommit, XAResource.TMNOFLAGS);

      Xid neverCommittedXID = newXID();

      for (int i = 1; i < numberOfMessages; i++) {
         if (i == 20) {
            // I elected a single message to be in prepared state, it won't ever be committed
            session.end(xidConsumeCommit, XAResource.TMSUCCESS);
            session.commit(xidConsumeCommit, true);
            session.start(neverCommittedXID, XAResource.TMNOFLAGS);
         }
         ClientMessage message = cons.receive(5000);
         assertNotNull(message);
         message.acknowledge();
         assertEquals(i, message.getIntProperty("count").intValue());
         if (i == 20) {
            session.end(neverCommittedXID, XAResource.TMSUCCESS);
            session.prepare(neverCommittedXID);
            xidConsumeCommit = newXID();
            session.start(xidConsumeCommit, XAResource.TMNOFLAGS);
         }
      }

      session.end(xidConsumeCommit, XAResource.TMSUCCESS);

      session.commit(xidConsumeCommit, true);

      session.close();
      sf.close();

      // Restart the server, and we expect cleanup to not destroy any page with prepared data
      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      queue = server.locateQueue(ADDRESS);

      assertTrue(queue.getPageSubscription().getPagingStore().isPaging());

      producer = session.createProducer(ADDRESS);

      for (int i = numberOfMessages; i < numberOfMessages * 2; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty("count", i);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);

         if ((i + 1) % 5 == 0) {
            session.commit();
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }

      cons = session.createConsumer(ADDRESS);

      session.start();

      for (int i = numberOfMessages; i < numberOfMessages * 2; i++) {
         ClientMessage message = cons.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("count").intValue());
         message.acknowledge();
      }
      assertNull(cons.receiveImmediate());
      session.commit();

      session.commit();

      session.close();

      session = sf.createSession(true, false, false);

      session.rollback(xidConsumeNoCommit);

      session.start();

      xidConsumeCommit = newXID();

      session.start(xidConsumeCommit, XAResource.TMNOFLAGS);
      cons = session.createConsumer(ADDRESS);

      session.start();

      ClientMessage message = cons.receive(5000);
      assertNotNull(message);
      message.acknowledge();

      session.end(xidConsumeCommit, XAResource.TMSUCCESS);

      session.commit(xidConsumeCommit, true);

      session.close();
   }

   @TestTemplate
   public void testSimplePreparedAck() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 50;

      // namespace for the first client (before the server restart)
      {
         locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

         sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, true, true);

         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

         Queue queue = server.locateQueue(PagingTest.ADDRESS);

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         byte[] body = new byte[MESSAGE_SIZE];

         ByteBuffer bb = ByteBuffer.wrap(body);

         for (int j = 1; j <= MESSAGE_SIZE; j++) {
            bb.put(getSamplebyte(j));
         }

         queue.getPageSubscription().getPagingStore().startPaging();

         // Send many messages, 5 on each page
         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage message = session.createMessage(true);

            message.putIntProperty("count", i);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            producer.send(message);

            if ((i + 1) % 5 == 0) {
               session.commit();
               queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
            }
         }

         session.close();

         session = sf.createSession(true, false, false);

         Xid xidConsumeNoCommit = newXID();
         session.start(xidConsumeNoCommit, XAResource.TMNOFLAGS);

         ClientConsumer cons = session.createConsumer(ADDRESS);

         session.start();

         // First message is consumed, prepared, will be rolled back later
         ClientMessage firstMessageConsumed = cons.receive(5000);
         assertNotNull(firstMessageConsumed);
         firstMessageConsumed.acknowledge();

         session.end(xidConsumeNoCommit, XAResource.TMSUCCESS);

         session.prepare(xidConsumeNoCommit);
      }

      server.stop();
      server.start();

      // Namespace for the second client, after the restart
      {
         locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

         sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, true, true);

         ClientConsumer cons = session.createConsumer(ADDRESS);

         session.start();

         for (int i = 1; i < numberOfMessages; i++) {
            ClientMessage message = cons.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("count").intValue());
            message.acknowledge();
         }

         session.commit();
      }
   }

   private void forcePage(Queue queue) throws InterruptedException {
      for (long timeout = System.currentTimeMillis() + 5000; timeout > System.currentTimeMillis() && !queue.getPageSubscription().getPagingStore().isPaging(); ) {
         Thread.sleep(10);
      }
      assertTrue(queue.getPageSubscription().getPagingStore().isPaging());
   }

   @TestTemplate
   public void testMoveExpire() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalDirectory(getJournalDir()).setJournalSyncNonTransactional(false).setJournalCompactMinFiles(0) // disable compact
         .setMessageExpiryScanPeriod(10);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);
      server.start();

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(PAGE_SIZE).setMaxSizeBytes(0).setExpiryAddress(SimpleString.of("EXP")).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxReadPageBytes(-1).setMaxReadPageMessages(-1);
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setConsumerWindowSize(10 * 1024 * 1024).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = locator.createSessionFactory();

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      session.createQueue(QueueConfiguration.of("EXP"));

      Queue queue1 = server.locateQueue(ADDRESS);
      Queue qEXP = server.locateQueue(SimpleString.of("EXP"));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      final int MESSAGE_SIZE = 1024;

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         if (i < 50) {
            message.setExpiration(System.currentTimeMillis() + 100);
         }

         message.putIntProperty("tst-count", i);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);
      }
      session.commit();
      producer.close();

      try {
         Wait.assertEquals(50, qEXP::getMessageCount);
         Wait.assertEquals(50, queue1::getMessageCount);
      } catch (Throwable e) {
         ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
         Connection browserc = factory.createConnection();
         browserc.start();
         Session sessionc = browserc.createSession(false, Session.AUTO_ACKNOWLEDGE);
         QueueBrowser consumerc = sessionc.createBrowser(sessionc.createQueue(queue1.getName().toString()));
         Enumeration<javax.jms.Message> enumeration = consumerc.getEnumeration();
         while (enumeration.hasMoreElements()) {
            javax.jms.Message message = enumeration.nextElement();
            logger.debug("Received {} from queue1", message.getIntProperty("tst-count"));
         }
         consumerc.close();
         consumerc = sessionc.createBrowser(sessionc.createQueue("EXP"));
         enumeration = consumerc.getEnumeration();
         while (enumeration.hasMoreElements()) {
            javax.jms.Message message = enumeration.nextElement();
            logger.debug("Received {} from EAP", message.getIntProperty("tst-count"));

         }

         consumerc.close();

         throw e;
      }

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int i = 0; i < 50; i++) {
         ClientMessage message = consumer.receive(5000);
         assertNotNull(message, "message " + i + " was null");
         message.acknowledge();
         assertTrue(message.getIntProperty("tst-count") >= 50);
      }

      session.commit();

      assertNull(consumer.receiveImmediate());

      Wait.assertEquals(0, queue1::getMessageCount);

      consumer.close();

      consumer = session.createConsumer("EXP");

      for (int i = 0; i < 50; i++) {
         ClientMessage message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();
         assertTrue(message.getIntProperty("tst-count") < 50);
      }

      assertNull(consumer.receiveImmediate());

      // This is just to hold some messages as being delivered
      ClientConsumerInternal cons = (ClientConsumerInternal) session.createConsumer(ADDRESS);

      session.commit();
      producer.close();
      session.close();

      server.stop();
   }

   @TestTemplate
   public void testDeleteQueueRestart() throws Exception {
      Configuration config = createDefaultInVMConfig().setJournalDirectory(getJournalDir()).setJournalSyncNonTransactional(false).setJournalCompactMinFiles(0).setPageSyncTimeout(1000); // disable compact

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int NUM_MESSAGES = 100;
      final int COMMIT_INTERVAL = 10;

      locator = createInVMNonHALocator().setConsumerWindowSize(10 * 1024 * 1024).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      SimpleString QUEUE2 = ADDRESS.concat("-2");

      ClientSessionFactory sf = locator.createSessionFactory();

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      session.createQueue(QueueConfiguration.of(QUEUE2).setAddress(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      // This is just to hold some messages as being delivered
      ClientConsumerInternal cons = (ClientConsumerInternal) session.createConsumer(ADDRESS);
      ClientConsumerInternal cons2 = (ClientConsumerInternal) session.createConsumer(QUEUE2);

      ClientMessage message = null;

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < NUM_MESSAGES; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);
         if (i % COMMIT_INTERVAL == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.start();

      waitBuffer(cons, NUM_MESSAGES / 5);
      waitBuffer(cons2, NUM_MESSAGES / 5);

      session.close();

      Queue queue = server.locateQueue(QUEUE2);

      long deletedQueueID = queue.getID();

      server.destroyQueue(QUEUE2);

      sf.close();
      locator.close();
      locator = null;
      sf = null;

      server.stop();

      final HashMap<Integer, AtomicInteger> recordsType = countJournal(config);

      Pair<List<RecordInfo>, List<PreparedTransactionInfo>> journalData = loadMessageJournal(config);

      Set<Long> deletedQueueReferences = new HashSet<>();

      for (RecordInfo info : journalData.getA()) {
         if (info.getUserRecordType() == JournalRecordIds.ADD_REF) {
            DescribeJournal.ReferenceDescribe ref = (DescribeJournal.ReferenceDescribe) DescribeJournal.newObjectEncoding(info);

            if (ref.refEncoding.queueID == deletedQueueID) {
               deletedQueueReferences.add(info.id);
            }
         } else if (info.getUserRecordType() == JournalRecordIds.ACKNOWLEDGE_REF) {
            AckDescribe ref = (AckDescribe) DescribeJournal.newObjectEncoding(info);

            if (ref.refEncoding.queueID == deletedQueueID) {
               deletedQueueReferences.remove(info.id);
            }
         }
      }

      if (!deletedQueueReferences.isEmpty()) {
         for (Long value : deletedQueueReferences) {
            logger.warn("Deleted Queue still has a reference:{}", value);
         }

         fail("Deleted queue still have references");
      }

      server.start();

      locator = createInVMNonHALocator();
      locator.setConsumerWindowSize(10 * 1024 * 1024);
      sf = locator.createSessionFactory();
      session = sf.createSession(false, false, false);
      cons = (ClientConsumerInternal) session.createConsumer(ADDRESS);
      session.start();

      for (int i = 0; i < NUM_MESSAGES; i++) {
         message = cons.receive(1000);
         assertNotNull(message);
         message.acknowledge();
         if (i % COMMIT_INTERVAL == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      session.close();

      queue = server.locateQueue(PagingTest.ADDRESS);

      Wait.assertEquals(0, queue::getMessageCount);

      Wait.assertFalse(queue.getPageSubscription().getPagingStore()::isPaging, 5000, 100);

      assertFalse(queue.getPageSubscription().getPagingStore().isPaging());

      server.stop();
   }

   @TestTemplate
   public void testDeleteQueue() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultNettyConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      SimpleString queue = SimpleString.of("testPurge:" + RandomUtil.randomUUIDString());
      server.addAddressInfo(new AddressInfo(queue, RoutingType.ANYCAST));
      QueueImpl purgeQueue = (QueueImpl) server.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setPurgeOnNoConsumers(false).setAutoCreateAddress(false));

      ConnectionFactory cf = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      Connection connection = cf.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue jmsQueue = session.createQueue(queue.toString());

      purgeQueue.getPageSubscription().getPagingStore().startPaging();

      MessageProducer producer = session.createProducer(jmsQueue);

      for (int i = 0; i < 100; i++) {
         producer.send(session.createTextMessage("hello" + i));
         session.commit();
      }

      Wait.assertEquals(100, purgeQueue::getMessageCount);

      purgeQueue.deleteQueue(false);

      Wait.assertEquals(0, ()->server.getPagingManager().getTransactions().size(), 5_000);
   }

   private void waitBuffer(ClientConsumerInternal clientBuffer, int bufferSize) {
      Wait.assertTrue(() -> "expected " + bufferSize + " but got " + clientBuffer.getBufferSize(), () -> clientBuffer.getBufferSize() > bufferSize, 15_000, 100);
   }

   @TestTemplate
   public void testPreparePersistent() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int numberOfMessages = 50;

      final int numberOfTX = 10;

      final int messagesPerTX = numberOfMessages / numberOfTX;

      locator = createInVMNonHALocator();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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
      }
      session.commit();
      session.close();
      session = null;

      sf.close();
      locator.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);
      server.start();

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);

      Queue queue = server.locateQueue(ADDRESS);

      Wait.assertEquals(numberOfMessages, queue::getMessageCount);

      LinkedList<Xid> xids = new LinkedList<>();

      int msgReceived = 0;
      for (int i = 0; i < numberOfTX; i++) {
         ClientSession sessionConsumer = sf.createSession(true, false, false);
         Xid xid = newXID();
         xids.add(xid);
         sessionConsumer.start(xid, XAResource.TMNOFLAGS);
         sessionConsumer.start();
         ClientConsumer consumer = sessionConsumer.createConsumer(PagingTest.ADDRESS);
         for (int msgCount = 0; msgCount < messagesPerTX; msgCount++) {
            if (msgReceived == numberOfMessages) {
               break;
            }
            msgReceived++;
            ClientMessage msg = consumer.receive(10000);
            assertNotNull(msg);
            msg.acknowledge();
         }
         sessionConsumer.end(xid, XAResource.TMSUCCESS);
         sessionConsumer.prepare(xid);
         sessionConsumer.close();
      }

      ClientSession sessionCheck = sf.createSession(true, true);

      ClientConsumer consumer = sessionCheck.createConsumer(PagingTest.ADDRESS);

      assertNull(consumer.receiveImmediate());

      sessionCheck.close();

      Wait.assertEquals(numberOfMessages, queue::getMessageCount);

      sf.close();
      locator.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      server.start();

      waitForServerToStart(server);

      queue = server.locateQueue(ADDRESS);

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);

      session = sf.createSession(true, false, false);

      consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();

      Wait.assertEquals(numberOfMessages, queue::getMessageCount);

      ClientMessage msg = consumer.receiveImmediate();
      if (msg != null) {
         while (true) {
            ClientMessage msg2 = consumer.receive(1000);
            if (msg2 == null) {
               break;
            }
         }
      }
      assertNull(msg);

      for (int i = xids.size() - 1; i >= 0; i--) {
         Xid xid = xids.get(i);
         session.rollback(xid);
      }

      xids.clear();

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      consumer = session.createConsumer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         msg = consumer.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();

         assertEquals(i, msg.getIntProperty("id").intValue());

         if (i % 500 == 0) {
            session.commit();
         }
      }

      session.commit();

      session.close();

      sf.close();

      locator.close();

      Wait.assertEquals(0, queue::getMessageCount);

      PageCursorProviderTestAccessor.cleanup(queue.getPagingStore().getCursorProvider());

      waitForNotPaging(queue);
   }

   @TestTemplate
   public void testSendOverBlockingNoFlowControl() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      server.getAddressSettingsRepository().getMatch("#").setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);

      server.start();

      final int biggerMessageSize = 10 * 1024;

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setProducerWindowSize(-1).setMinLargeMessageSize(1024 * 1024);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[biggerMessageSize];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= biggerMessageSize; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);

         if (i % 10 == 0) {
            session.commit();
         }
      }
      session.commit();

      session.start();

      ClientConsumer cons = session.createConsumer(ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         message = cons.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         if (i % 10 == 0) {
            session.commit();
         }
      }

      session.commit();

   }

   @TestTemplate
   public void testReceiveImmediate() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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
         if (i % 10 == 0) {
            session.commit();
         }
      }
      session.commit();
      session.close();

      session = null;

      sf.close();
      locator.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);
      server.start();

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);

      Queue queue = server.locateQueue(ADDRESS);

      Wait.assertEquals(numberOfMessages, queue::getMessageCount);

      ClientSession sessionConsumer = sf.createSession(false, false, false);
      sessionConsumer.start();
      ClientConsumer consumer = sessionConsumer.createConsumer(PagingTest.ADDRESS);
      for (int msgCount = 0; msgCount < numberOfMessages; msgCount++) {
         logger.debug("Received {}", msgCount);
         ClientMessage msg = null;
         for (int retry = 0; retry < 10 && msg == null; retry++) {
            msg = consumer.receiveImmediate();
         }
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

      long timeout = System.currentTimeMillis() + 5000;
      while (timeout > System.currentTimeMillis() && queue.getPageSubscription().getPagingStore().isPaging()) {
         Thread.sleep(100);
      }
      Wait.assertFalse(() -> queue.getPageSubscription().getPagingStore().isPaging());

   }

   @TestTemplate
   public void testMissingTXEverythingAcked() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 100;

      final int numberOfTX = 10;

      final int messagesPerTX = numberOfMessages / numberOfTX;

      try {
         locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of("q1").setAddress(ADDRESS.toString()));

         session.createQueue(QueueConfiguration.of("q2").setAddress(ADDRESS.toString()));

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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
            if (i % messagesPerTX == 0) {
               session.commit();
            }
         }
         session.commit();
         session.close();

         List<RecordInfo> records = new ArrayList<>();

         List<PreparedTransactionInfo> list = new ArrayList<>();

         server.getStorageManager().getMessageJournal().stop();

         Journal jrn = server.getStorageManager().getMessageJournal();
         jrn.start();
         jrn.load(records, list, null);

         // Delete everything from the journal
         for (RecordInfo info : records) {
            if (!info.isUpdate && info.getUserRecordType() != JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE && info.getUserRecordType() != JournalRecordIds.PAGE_CURSOR_COUNTER_INC && info.getUserRecordType() != JournalRecordIds.PAGE_CURSOR_COMPLETE) {
               jrn.appendDeleteRecord(info.id, false);
            }
         }

         jrn.stop();

      } finally {
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      ClientSessionFactory csf = createSessionFactory(locator);

      ClientSession sess = csf.createSession();

      sess.start();

      ClientConsumer cons = sess.createConsumer("q1");

      assertNull(cons.receiveImmediate());

      ClientConsumer cons2 = sess.createConsumer("q2");
      assertNull(cons2.receiveImmediate());

      Queue q1 = server.locateQueue(SimpleString.of("q1"));
      Queue q2 = server.locateQueue(SimpleString.of("q2"));

      q1.getPageSubscription().cleanupEntries(false);
      q2.getPageSubscription().cleanupEntries(false);

      PageCursorProvider provider = q1.getPageSubscription().getPagingStore().getCursorProvider();
      PageCursorProviderTestAccessor.cleanup(provider);

      waitForNotPaging(q1);

      sess.close();
   }

   @TestTemplate
   public void testMissingTXEverythingAcked2() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 6;

      final int numberOfTX = 2;

      final int messagesPerTX = numberOfMessages / numberOfTX;

      try {
         locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of("q1").setAddress(ADDRESS.toString()));

         session.createQueue(QueueConfiguration.of("q2").setAddress(ADDRESS.toString()));

         server.getPagingManager().getPageStore(ADDRESS).startPaging();

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

            message.putStringProperty("id", "str-" + i);

            producer.send(message);
            if ((i + 1) % messagesPerTX == 0) {
               session.commit();
            }
         }
         session.commit();

         session.start();

         for (int i = 1; i <= 2; i++) {
            ClientConsumer cons = session.createConsumer("q" + i);

            for (int j = 0; j < 3; j++) {
               ClientMessage msg = cons.receive(5000);

               assertNotNull(msg);

               assertEquals("str-" + j, msg.getStringProperty("id"));

               msg.acknowledge();
            }

            session.commit();

         }

         session.close();
      } finally {
         locator.close();
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      locator = createInVMNonHALocator();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory csf = createSessionFactory(locator);

      ClientSession session = csf.createSession();

      session.start();

      for (int i = 1; i <= 2; i++) {
         ClientConsumer cons = session.createConsumer("q" + i);

         for (int j = 3; j < 6; j++) {
            ClientMessage msg = cons.receive(5000);

            assertNotNull(msg);

            assertEquals("str-" + j, msg.getStringProperty("id"));

            msg.acknowledge();
         }

         session.commit();
         assertNull(cons.receiveImmediate());

      }

      session.close();

      long timeout = System.currentTimeMillis() + 5000;

      while (System.currentTimeMillis() < timeout && server.getPagingManager().getPageStore(ADDRESS).isPaging()) {
         Thread.sleep(100);
      }
   }

   @TestTemplate
   public void testTwoQueuesOneNoRouting() throws Exception {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int numberOfMessages = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("-invalid")).setAddress(PagingTest.ADDRESS).setFilterString(SimpleString.of(Filter.GENERIC_IGNORED_FILTER)));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[MESSAGE_SIZE];

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(persistentMessages);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
         if (i % 10 == 0) {
            session.commit();
         }
      }

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         assertEquals(i, message.getIntProperty("id").intValue());
         if (i % 10 == 0) {
            session.commit();
         }
      }

      session.commit();

      session.commit();

      session.commit();

      PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
      PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());

      Wait.assertFalse(server.getPagingManager().getPageStore(ADDRESS)::isPaging, 5000, 100);
   }

   public void internalMultiQueuesTest(final boolean divert) throws Exception {

      ExecutorService executor = Executors.newFixedThreadPool(2);
      runAfter(executor::shutdownNow);

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      if (divert) {
         DivertConfiguration divert1 = new DivertConfiguration().setName("dv1").setRoutingName("nm1").setAddress(PagingTest.ADDRESS.toString()).setForwardingAddress(PagingTest.ADDRESS.toString() + "-1").setExclusive(true);

         config.addDivertConfiguration(divert1);

         DivertConfiguration divert2 = new DivertConfiguration().setName("dv2").setRoutingName("nm2").setAddress(PagingTest.ADDRESS.toString()).setForwardingAddress(PagingTest.ADDRESS.toString() + "-2").setExclusive(true);

         config.addDivertConfiguration(divert2);
      }

      server.start();

      final int numberOfMessages = 30;

      final byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      final AtomicBoolean running = new AtomicBoolean(true);

      runAfter(() -> running.set(false));

      class TCount implements Runnable {

         Queue queue;

         TCount(Queue queue) {
            this.queue = queue;
         }

         @Override
         public void run() {
            try {
               while (running.get()) {
                  // this is just trying to be annoying on the queue. We will keep bugging it and the operation should all be ok.
                  // this is similar to an user opening many instances of the management console or some monitoring anti-pattern
                  getMessagesAdded(queue);
                  getMessageCount(queue);
                  Thread.sleep(10);
               }
            } catch (InterruptedException e) {
               logger.debug("Thread interrupted");
            }
         }
      }

      try {
         {
            locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

            sf = createSessionFactory(locator);

            ClientSession session = sf.createSession(false, false, false);

            if (divert) {
               session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "-1"));
               session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "-2"));
            } else {
               session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "-1").setAddress(PagingTest.ADDRESS));
               session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "-2").setAddress(PagingTest.ADDRESS));
            }

            ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

            ClientMessage message = null;

            for (int i = 0; i < numberOfMessages; i++) {
               if (i % 500 == 0) {
                  logger.debug("Sent {} messages", i);
                  session.commit();
               }
               message = session.createMessage(true);

               ActiveMQBuffer bodyLocal = message.getBodyBuffer();

               bodyLocal.writeBytes(body);

               message.putIntProperty(SimpleString.of("id"), i);

               producer.send(message);
            }

            session.commit();

            session.close();

            server.stop();

            sf.close();
            locator.close();
         }

         server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
         server.start();

         Queue queue1 = server.locateQueue(PagingTest.ADDRESS.concat("-1"));

         Queue queue2 = server.locateQueue(PagingTest.ADDRESS.concat("-2"));

         assertNotNull(queue1);

         assertNotNull(queue2);

         assertNotSame(queue1, queue2);

         executor.execute(new TCount(queue1));
         executor.execute(new TCount(queue2));

         locator = createInVMNonHALocator();
         final ClientSessionFactory sf2 = createSessionFactory(locator);

         final AtomicInteger errors = new AtomicInteger(0);

         Thread[] threads = new Thread[2];

         for (int start = 1; start <= 2; start++) {

            final String addressToSubscribe = PagingTest.ADDRESS + "-" + start;

            threads[start - 1] = new Thread(() -> {
               try {
                  ClientSession session = sf2.createSession(null, null, false, true, true, false, 0);

                  ClientConsumer consumer = session.createConsumer(addressToSubscribe);

                  session.start();

                  for (int i = 0; i < numberOfMessages; i++) {
                     ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

                     assertNotNull(message2);

                     assertEquals(i, message2.getIntProperty("id").intValue());

                     message2.acknowledge();

                     assertNotNull(message2);

                     if (i % 100 == 0) {
                        if (i % 5000 == 0) {
                           logger.debug("{} consumed {} messages", addressToSubscribe, i);
                        }
                        session.commit();
                     }

                     try {
                        assertBodiesEqual(body, message2.getBodyBuffer());
                     } catch (AssertionError e) {
                        if (logger.isDebugEnabled()) {
                           logger.debug("Expected buffer:{}", ActiveMQTestBase.dumpBytesHex(body, 40));
                           logger.debug("Arriving buffer:{}", ActiveMQTestBase.dumpBytesHex(message2.getBodyBuffer().toByteBuffer().array(), 40));
                        }
                        throw e;
                     }
                  }

                  session.commit();

                  consumer.close();

                  session.close();
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }

            });
         }

         for (int i = 0; i < 2; i++) {
            threads[i].start();
         }

         for (int i = 0; i < 2; i++) {
            threads[i].join();
         }

         sf2.close();
         locator.close();

         assertEquals(0, errors.get());

         for (int i = 0; i < 20 && server.getPagingManager().getTransactions().size() != 0; i++) {
            if (server.getPagingManager().getTransactions().size() != 0) {
               // The delete may be asynchronous, giving some time case it eventually happen asynchronously
               Thread.sleep(500);
            }
         }

         Wait.assertEquals(0, () -> server.getPagingManager().getTransactions().size());

      } finally {
         running.set(false);

         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }

   }

   @TestTemplate
   public void testMultiQueuesNonPersistentAndPersistent() throws Exception {
      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 30;

      final byte[] body = new byte[100];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= 100; j++) {
         bb.put(getSamplebyte(j));
      }

      {
         locator = createInVMNonHALocator();

         locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "-1").setAddress(PagingTest.ADDRESS));
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS + "-2").setAddress(PagingTest.ADDRESS).setDurable(false));

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         ClientMessage message = null;

         for (int i = 0; i < numberOfMessages; i++) {
            if (i % 10 == 0) {
               session.commit();
            }
            message = session.createMessage(true);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            message.putIntProperty(SimpleString.of("id"), i);

            producer.send(message);
         }

         session.commit();

         session.close();

         server.stop();

         sf.close();
         locator.close();
      }

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      server.start();

      ServerLocator locator1 = createInVMNonHALocator();
      final ClientSessionFactory sf2 = locator1.createSessionFactory();

      final AtomicInteger errors = new AtomicInteger(0);

      ClientSession session = sf2.createSession(null, null, false, true, true, false, 0);

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS + "-1");

      session.start();

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message2);

         assertEquals(i, message2.getIntProperty("id").intValue());

         message2.acknowledge();

         assertNotNull(message2);

         if (i % 10 == 0) {
            session.commit();
         }

         try {
            assertBodiesEqual(body, message2.getBodyBuffer());
         } catch (AssertionError e) {
            if (logger.isDebugEnabled()) {
               logger.debug("Expected buffer: {}", ActiveMQTestBase.dumpBytesHex(body, 40));
               logger.debug("Arriving buffer: {}", ActiveMQTestBase.dumpBytesHex(message2.getBodyBuffer().toByteBuffer().array(), 40));
            }
            throw e;
         }
      }

      session.commit();

      consumer.close();

      session.close();

      Wait.assertFalse(() -> server.getPagingManager().getPageStore(ADDRESS).isPaging());

      Wait.assertEquals(0, () -> server.getPagingManager().getTransactions().size());

   }

   private void internaltestSendReceivePaging(final boolean persistentMessages) throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int messageSize = 1024;

      final int numberOfMessages = 100;
      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      Queue queue = server.locateQueue(ADDRESS);
      queue.getPageSubscription().getPagingStore().startPaging();

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[messageSize];

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(persistentMessages);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
      }

      session.close();
      sf.close();
      locator.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      server.start();

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);

      session = sf.createSession(null, null, false, true, true, false, 0);

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message2);

         assertEquals(i, message2.getIntProperty("id").intValue());

         assertEquals(body.length, message2.getBodySize());

         message2.acknowledge();

         assertNotNull(message2);

         if (i % 10 == 0) {
            session.commit();
         }

         try {
            assertBodiesEqual(body, message2.getBodyBuffer());
         } catch (AssertionError e) {
            if (logger.isDebugEnabled()) {
               logger.debug("Expected buffer:{}", ActiveMQTestBase.dumpBytesHex(body, 40));
               logger.debug("Arriving buffer:{}", ActiveMQTestBase.dumpBytesHex(message2.getBodyBuffer().toByteBuffer().array(), 40));
            }
            throw e;
         }
      }

      consumer.close();

      session.close();
   }

   private void assertBodiesEqual(final byte[] body, final ActiveMQBuffer buffer) {
      byte[] other = new byte[body.length];

      buffer.readBytes(other);

      ActiveMQTestBase.assertEqualsByteArrays(body, other);
   }

   /**
    * - Make a destination in page mode
    * - Add stuff to a transaction
    * - Consume the entire destination (not in page mode any more)
    * - Add stuff to a transaction again
    * - Check order
    */
   @TestTemplate
   public void testDepageDuringTransaction() throws Exception {

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      byte[] body = new byte[MESSAGE_SIZE];
      // ActiveMQBuffer bodyLocal = ActiveMQChannelBuffers.buffer(DataConstants.SIZE_INT * numberOfIntegers);

      ClientMessage message = null;

      int numberOfMessages = 0;
      while (true) {
         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);

         // Stop sending message as soon as we start paging
         if (server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging()) {
            break;
         }
         numberOfMessages++;

         assertTrue(numberOfMessages < 2000, "something is not letting the system to enter page mode, the test became invalid");

         producer.send(message);
      }

      assertTrue(server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging());

      session.start();

      ClientSession sessionTransacted = sf.createSession(null, null, false, false, false, false, 0);

      ClientProducer producerTransacted = sessionTransacted.createProducer(PagingTest.ADDRESS);

      for (int i = 0; i < 10; i++) {
         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);
         message.putIntProperty(SimpleString.of("id"), i);

         // Consume messages to force an eventual out of order delivery
         if (i == 5) {
            ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);
            for (int j = 0; j < numberOfMessages; j++) {
               ClientMessage msg = consumer.receive(PagingTest.RECEIVE_TIMEOUT);
               msg.acknowledge();
               assertNotNull(msg);
            }

            assertNull(consumer.receiveImmediate());
            consumer.close();
         }

         Integer messageID = (Integer) message.getObjectProperty(SimpleString.of("id"));
         assertNotNull(messageID);
         assertEquals(messageID.intValue(), i);

         producerTransacted.send(message);
      }

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      assertNull(consumer.receiveImmediate());

      sessionTransacted.commit();

      sessionTransacted.close();

      for (int i = 0; i < 10; i++) {
         message = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message);

         Integer messageID = (Integer) message.getObjectProperty(SimpleString.of("id"));

         assertNotNull(messageID);
         assertEquals(messageID.intValue(), i, "message received out of order");

         message.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      consumer.close();

      session.close();
   }

   /*
    * - Make a destination in page mode
    * - Add stuff to a transaction
    * - Consume the entire destination (not in page mode any more)
    * - Add stuff to a transaction again
    * - Check order
    */
   @TestTemplate
   public void testDepageDuringTransaction2() throws Exception {
      boolean IS_DURABLE_MESSAGE = true;

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      byte[] body = new byte[MESSAGE_SIZE];

      ClientSession sessionTransacted = sf.createSession(null, null, false, false, false, false, 0);
      ClientProducer producerTransacted = sessionTransacted.createProducer(PagingTest.ADDRESS);

      ClientSession session = sf.createSession(null, null, false, false, true, false, 0);
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientMessage firstMessage = sessionTransacted.createMessage(IS_DURABLE_MESSAGE);
      firstMessage.getBodyBuffer().writeBytes(body);
      firstMessage.putIntProperty(SimpleString.of("id"), 0);

      producerTransacted.send(firstMessage);

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      int numberOfMessages = 0;
      while (true) {
         message = session.createMessage(IS_DURABLE_MESSAGE);
         message.getBodyBuffer().writeBytes(body);
         message.putIntProperty("id", numberOfMessages);
         message.putBooleanProperty("new", false);

         // Stop sending message as soon as we start paging
         if (server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging()) {
            break;
         }
         numberOfMessages++;

         producer.send(message);
         if (numberOfMessages % 10 == 0) {
            session.commit();
         }
      }
      session.commit();

      assertTrue(server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging());

      session.start();

      for (int i = 1; i < 10; i++) {
         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);
         message.putIntProperty(SimpleString.of("id"), i);

         // Consume messages to force an eventual out of order delivery
         if (i == 5) {
            ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);
            for (int j = 0; j < numberOfMessages; j++) {
               ClientMessage msg = consumer.receive(PagingTest.RECEIVE_TIMEOUT);
               msg.acknowledge();
               assertEquals(j, msg.getIntProperty("id").intValue());
               assertFalse(msg.getBooleanProperty("new"));
               assertNotNull(msg);
            }

            ClientMessage msgReceived = consumer.receiveImmediate();

            assertNull(msgReceived);
            consumer.close();
         }

         Integer messageID = (Integer) message.getObjectProperty(SimpleString.of("id"));
         assertNotNull(messageID);
         assertEquals(messageID.intValue(), i);

         producerTransacted.send(message);
      }

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      assertNull(consumer.receiveImmediate());

      sessionTransacted.commit();

      sessionTransacted.close();

      for (int i = 0; i < 10; i++) {
         message = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message);

         Integer messageID = (Integer) message.getObjectProperty(SimpleString.of("id"));

         assertNotNull(messageID);
         assertEquals(i, messageID.intValue(), "message received out of order");

         message.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      consumer.close();

      session.close();

   }


   @TestTemplate
   public void testDepageDuringTransaction3() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      byte[] body = new byte[MESSAGE_SIZE];

      ClientSession sessionTransacted = sf.createSession(null, null, false, false, false, false, 0);
      ClientProducer producerTransacted = sessionTransacted.createProducer(PagingTest.ADDRESS);

      ClientSession sessionNonTX = sf.createSession(true, true, 0);
      sessionNonTX.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producerNonTransacted = sessionNonTX.createProducer(PagingTest.ADDRESS);

      sessionNonTX.start();

      final int MAX_TX = 10;

      assertEquals(MAX_TX % 2, 0, "MAX_TX needs to be even");

      for (int i = 0; i < MAX_TX; i++) {
         ClientMessage message = sessionNonTX.createMessage(true);
         message.getBodyBuffer().writeBytes(body);
         message.putIntProperty(SimpleString.of("id"), i);
         message.putStringProperty(SimpleString.of("tst"), SimpleString.of("i=" + i));
         message.putStringProperty(SimpleString.of("TX"), "true");

         producerTransacted.send(message);

         if (i % 2 == 0) {
            logger.debug("Sending");
            for (int j = 0; j < 10; j++) {
               ClientMessage msgSend = sessionNonTX.createMessage(true);
               msgSend.putStringProperty(SimpleString.of("tst"), SimpleString.of("i=" + i + ", j=" + j));
               msgSend.putStringProperty(SimpleString.of("TX"), "false");
               msgSend.getBodyBuffer().writeBytes(new byte[20 * 1024]);
               producerNonTransacted.send(msgSend);
            }
            Wait.assertTrue(() -> server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging(), 1000);
         } else {
            logger.debug("Receiving");
            ClientConsumer consumer = sessionNonTX.createConsumer(PagingTest.ADDRESS);
            for (int j = 0; j < 10; j++) {
               ClientMessage msgReceived = consumer.receive(5000);
               assertNotNull(msgReceived, "i=" + i + ".. j=" + j);
               msgReceived.acknowledge();
            }
            consumer.close();
         }
      }

      ClientConsumer consumer = sessionNonTX.createConsumer(PagingTest.ADDRESS);

      assertNull(consumer.receiveImmediate());

      sessionTransacted.commit();

      sessionTransacted.close();

      for (int i = 0; i < MAX_TX; i++) {
         ClientMessage message = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message);

         Integer messageID = (Integer) message.getObjectProperty(SimpleString.of("id"));

         assertNotNull(messageID);
         assertEquals(i, messageID.intValue(), "message received out of order");

         message.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      consumer.close();

      sessionNonTX.close();
   }

   @TestTemplate
   public void testDepageDuringTransaction4() throws Exception {

      ExecutorService executorService = Executors.newFixedThreadPool(1);
      runAfter(executorService::shutdownNow);

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setJournalSyncTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final AtomicInteger errors = new AtomicInteger(0);

      final int numberOfMessages = 1000;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(false);

      sf = createSessionFactory(locator);

      final byte[] body = new byte[MESSAGE_SIZE];

      ClientSession session = sf.createSession(false, false, 0);
      session.start();
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      executorService.execute(() -> {
         ClientSession sessionProducer = null;
         try {
            sessionProducer = sf.createSession(false, false);
            ClientProducer producer = sessionProducer.createProducer(ADDRESS);

            for (int i = 0; i < numberOfMessages; i++) {
               ClientMessage msg = sessionProducer.createMessage(true);
               msg.getBodyBuffer().writeBytes(body);
               msg.putIntProperty("count", i);
               producer.send(msg);

               if (i % 100 == 0 && i != 0) {
                  sessionProducer.commit();
               }
            }

            sessionProducer.commit();

         } catch (Throwable e) {
            e.printStackTrace(); // >> junit report
            errors.incrementAndGet();
         } finally {
            try {
               if (sessionProducer != null) {
                  sessionProducer.close();
               }
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
      });

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("count").intValue());
         msg.acknowledge();
         if (i > 0 && i % 100 == 0) {
            session.commit();
         }
      }
      session.commit();

      session.close();

      locator.close();

      sf.close();

      assertEquals(0, errors.get());
   }

   @TestTemplate
   public void testOrderingNonTX() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setJournalSyncTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_SIZE * 2);

      server.getConfiguration();
      server.getConfiguration();

      server.start();

      final AtomicInteger errors = new AtomicInteger(0);

      final int numberOfMessages = 200;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      final CountDownLatch ready = new CountDownLatch(1);

      final byte[] body = new byte[MESSAGE_SIZE];

      Thread producerThread = new Thread(() -> {
         ClientSession sessionProducer = null;
         try {
            sessionProducer = sf.createSession(true, true);
            ClientProducer producer = sessionProducer.createProducer(ADDRESS);

            for (int i = 0; i < numberOfMessages; i++) {
               ClientMessage msg = sessionProducer.createMessage(true);
               msg.getBodyBuffer().writeBytes(body);
               msg.putIntProperty("count", i);
               producer.send(msg);

               if (i == 100) {
                  // The session is not TX, but we do this just to perform a round trip to the server
                  // and make sure there are no pending messages
                  sessionProducer.commit();

                  Wait.assertTrue(() -> server.getPagingManager().getPageStore(ADDRESS).isPaging());
                  ready.countDown();
               }
            }

            sessionProducer.commit();

            logger.debug("Producer gone");

         } catch (Throwable e) {
            e.printStackTrace(); // >> junit report
            errors.incrementAndGet();
         } finally {
            try {
               if (sessionProducer != null) {
                  sessionProducer.close();
               }
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
      });

      ClientSession session = sf.createSession(true, true, 0);
      session.start();
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      producerThread.start();

      assertTrue(ready.await(100, TimeUnit.SECONDS));

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         if (i != msg.getIntProperty("count").intValue()) {
            logger.debug("Received {} with property = {}", i, msg.getIntProperty("count"));
            logger.debug("###### different");
         }
         // assertEquals(i, msg.getIntProperty("count").intValue());
         msg.acknowledge();
      }

      session.close();

      producerThread.join();

      assertEquals(0, errors.get());
   }

   @TestTemplate
   public void testPageOnSchedulingNoRestart() throws Exception {
      internalTestPageOnScheduling(false);
   }

   @TestTemplate
   public void testPageOnSchedulingRestart() throws Exception {
      internalTestPageOnScheduling(true);
   }

   public void internalTestPageOnScheduling(final boolean restart) throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 100;

      final int numberOfBytes = 1024;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[numberOfBytes];

      for (int j = 0; j < numberOfBytes; j++) {
         body[j] = ActiveMQTestBase.getSamplebyte(j);
      }

      long scheduledTime = System.currentTimeMillis() + 5000;

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writeBytes(body);
         message.putIntProperty(SimpleString.of("id"), i);

         PagingStore store = server.getPagingManager().getPageStore(PagingTest.ADDRESS);

         // Worse scenario possible... only schedule what's on pages
         if (store.getCurrentPage() != null) {
            message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, scheduledTime);
         }

         producer.send(message);
      }

      if (restart) {
         session.close();

         server.stop();

         server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
         server.start();

         sf = createSessionFactory(locator);

         session = sf.createSession(null, null, false, true, true, false, 0);
      }

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();

      for (int i = 0; i < numberOfMessages; i++) {

         ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message2);

         message2.acknowledge();

         assertNotNull(message2);

         Long scheduled = (Long) message2.getObjectProperty(Message.HDR_SCHEDULED_DELIVERY_TIME);
         if (scheduled != null) {
            assertTrue(System.currentTimeMillis() >= scheduledTime, "Scheduling didn't work");
         }

         try {
            assertBodiesEqual(body, message2.getBodyBuffer());
         } catch (AssertionError e) {
            if (logger.isDebugEnabled()) {
               logger.debug("Expected buffer: {}", ActiveMQTestBase.dumpBytesHex(body, 40));
               logger.debug("Arriving buffer: {}", ActiveMQTestBase.dumpBytesHex(message2.getBodyBuffer().toByteBuffer().array(), 40));
            }
            throw e;
         }
      }

      consumer.close();

      session.close();
   }

   @TestTemplate
   public void testRollbackOnSend() throws Exception {

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfIntegers = 256;

      final int numberOfMessages = 10;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, false, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         for (int j = 1; j <= numberOfIntegers; j++) {
            bodyLocal.writeInt(j);
         }

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
      }

      session.rollback();

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();

      assertNull(consumer.receiveImmediate());

      session.close();
   }

   @TestTemplate
   public void testRollbackOnSendThenSendMore() throws Exception {

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, false, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      Queue queue = server.locateQueue(ADDRESS);

      queue.getPageSubscription().getPagingStore().startPaging();

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message;

      for (int i = 0; i < 20; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[100 * 4]);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
         session.commit();
         queue.getPageSubscription().getPagingStore().forceAnotherPage(true);

      }

      for (int i = 20; i < 24; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[100 * 4]);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
      }

      session.rollback();

      ClientSession consumerSession = sf.createSession(false, false);

      queue.getPageSubscription().getPagingStore().disableCleanup();

      PageCursorProviderTestAccessor.cleanup(queue.getPagingStore().getCursorProvider());

      consumerSession.start();
      ClientConsumer consumer = consumerSession.createConsumer(ADDRESS, SimpleString.of("id > 0"));
      for (int i = 0; i < 19; i++) {
         ClientMessage messageRec = consumer.receive(5000);
         logger.debug("msg::{}", messageRec);
         assertNotNull(messageRec);
         messageRec.acknowledge();
         consumerSession.commit();

         // The only reason I'm calling cleanup directly is that it would be easy to debug in case of bugs
         // if you see an issue with cleanup here, enjoy debugging this method
         PageCursorProviderTestAccessor.cleanup(queue.getPagingStore().getCursorProvider());
      }
      queue.getPageSubscription().getPagingStore().enableCleanup();

      consumerSession.close();

      session.close();
      sf.close();

      server.stop();
   }

   // The pages are complete, and this is simulating a scenario where the server crashed before deleting the pages.
   @TestTemplate
   public void testRestartWithComplete() throws Exception {

      Configuration config = createDefaultInVMConfig();

      final AtomicBoolean mainCleanup = new AtomicBoolean(true);

      class InterruptedCursorProvider extends PageCursorProviderImpl {

         InterruptedCursorProvider(PagingStore pagingStore, StorageManager storageManager) {
            super(pagingStore, storageManager);
         }

         @Override
         public void cleanup() {
            if (mainCleanup.get()) {
               super.cleanup();
            } else {
               try {
                  pagingStore.writeUnlock();
               } catch (Throwable ignored) {
               }
            }
         }
      }

      server = new ActiveMQServerImpl(config, ManagementFactory.getPlatformMBeanServer(), new ActiveMQSecurityManagerImpl()) {
         @Override
         protected PagingStoreFactoryNIO getPagingStoreFactory() {
            return new PagingStoreFactoryNIO(this.getStorageManager(), this.getConfiguration().getPagingLocation(), this.getConfiguration().getJournalBufferTimeout_NIO(), this.getScheduledPool(), this.getExecutorFactory(), this.getConfiguration().isJournalSyncNonTransactional(), null, () -> purgeFolders) {
               @Override
               public PageCursorProvider newCursorProvider(PagingStore store,
                                                           StorageManager storageManager,
                                                           AddressSettings addressSettings,
                                                           ArtemisExecutor executor) {
                  return new InterruptedCursorProvider(store, storageManager);
               }
            };
         }

      };

      addServer(server);

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(PagingTest.PAGE_SIZE).setMaxSizeBytes(PagingTest.PAGE_MAX).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxReadPageBytes(-1);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, true, 0);
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      final Queue queue1 = server.locateQueue(ADDRESS);

      queue1.getPageSubscription().getPagingStore().startPaging();

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message;

      for (int i = 0; i < 20; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[100 * 4]);

         message.putIntProperty(SimpleString.of("idi"), i);

         producer.send(message);
         session.commit();
         if (i < 19) {
            queue1.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }

      }

      Wait.assertEquals(20, () -> queue1.getPageSubscription().getPagingStore().getCurrentWritingPage());

      // This will force a scenario where the pages are cleaned up. When restarting we need to check if the current page is complete
      // if it is complete we must move to another page avoiding races on cleanup
      // which could happen during a crash / restart
      long tx = server.getStorageManager().generateID();
      for (int i = 1; i <= 20; i++) {
         server.getStorageManager().storePageCompleteTransactional(tx, queue1.getID(), new PagePositionImpl(i, 1));
      }

      server.getStorageManager().commit(tx);

      session.close();
      sf.close();

      server.stop();
      mainCleanup.set(false);

      logger.trace("Server restart");

      server.start();

      Queue queue = server.locateQueue(ADDRESS);

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = sf.createSession(null, null, false, false, true, false, 0);
      producer = session.createProducer(PagingTest.ADDRESS);

      for (int i = 0; i < 10; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[100 * 4]);

         message.putIntProperty(SimpleString.of("newid"), i);

         producer.send(message);
         session.commit();

         if (i == 5) {
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }

      mainCleanup.set(true);

      queue = server.locateQueue(ADDRESS);
      queue.getPageSubscription().cleanupEntries(false);
      PageCursorProviderTestAccessor.cleanup(queue.getPageSubscription().getPagingStore().getCursorProvider());

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      session.start();

      for (int i = 0; i < 10; i++) {
         message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("newid").intValue());
         message.acknowledge();
      }

      server.stop();

   }

   // The pages are complete, and this is simulating a scenario where the server crashed before deleting the pages.
   @TestTemplate
   public void testCreateQueueWhileCleanup() throws Exception {

      Configuration config = createDefaultInVMConfig();

      CountDownLatch letGoLatch = new CountDownLatch(1);
      runAfter(letGoLatch::countDown);
      CountDownLatch startFlag = new CountDownLatch(1);
      class InterruptedCursorProvider extends PageCursorProviderImpl {

         InterruptedCursorProvider(PagingStore pagingStore, StorageManager storageManager) {
            super(pagingStore, storageManager);
         }

         @Override
         protected void cleanupRegularStream(List<Page> depagedPages,
                                             LongHashSet depagedPagesSet,
                                             List<PageSubscription> cursorList,
                                             long minPage,
                                             long firstPage) throws Exception {
            super.cleanupRegularStream(depagedPages, depagedPagesSet, cursorList, minPage, firstPage);
            startFlag.countDown();
            for (int i = 0; i < 60; i++) {
               if (!letGoLatch.await(1, TimeUnit.SECONDS)) {
                  logger.warn("letGoLatch still unreleased");
               }
            }
         }
      }

      server = new ActiveMQServerImpl(config, ManagementFactory.getPlatformMBeanServer(), new ActiveMQSecurityManagerImpl()) {
         @Override
         protected PagingStoreFactoryNIO getPagingStoreFactory() {
            return new PagingStoreFactoryNIO(this.getStorageManager(), this.getConfiguration().getPagingLocation(), this.getConfiguration().getJournalBufferTimeout_NIO(), this.getScheduledPool(), this.getExecutorFactory(), this.getConfiguration().isJournalSyncNonTransactional(), null, () -> purgeFolders) {
               @Override
               public PageCursorProvider newCursorProvider(PagingStore store,
                                                           StorageManager storageManager,
                                                           AddressSettings addressSettings,
                                                           ArtemisExecutor executor) {
                  return new InterruptedCursorProvider(store, storageManager);
               }
            };
         }

      };

      addServer(server);

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(PagingTest.PAGE_SIZE).setMaxSizeBytes(0).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxReadPageBytes(-1);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, true, 0);

      String queue1Name = "Queue1";
      String queue2Name = "Queue2";


      server.addAddressInfo(new AddressInfo(ADDRESS).addRoutingType(RoutingType.MULTICAST));

      session.createQueue(QueueConfiguration.of(queue1Name).setAddress(ADDRESS).setRoutingType(RoutingType.MULTICAST));

      final Queue queue1 = server.locateQueue(queue1Name);

      queue1.getPageSubscription().getPagingStore().startPaging();

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message;

      for (int i = 0; i < 20; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[100 * 4]);

         message.putIntProperty(SimpleString.of("idi"), i);

         producer.send(message);
         session.commit();
      }

      Wait.assertTrue(queue1.getPagingStore()::isPaging);
      Future<Boolean> doneCleanup = queue1.getPagingStore().getCursorProvider().scheduleCleanup();

      assertTrue(startFlag.await(5, TimeUnit.SECONDS));

      ExecutorService executorService = Executors.newSingleThreadExecutor();
      runAfter(executorService::shutdownNow);
      CountDownLatch created = new CountDownLatch(1);
      executorService.execute(() -> {
         try {
            server.createQueue(QueueConfiguration.of(queue2Name).setAddress(ADDRESS));
            created.countDown();
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      });

      assertTrue(created.await(5, TimeUnit.SECONDS));

      assertNotNull(server.locateQueue(queue2Name));
      letGoLatch.countDown();
      assertTrue(doneCleanup.get(10, TimeUnit.SECONDS));

      server.stop();
   }

   @TestTemplate
   public void testPartialConsume() throws Exception {

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 100;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, false, false, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[1024]);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
      }

      session.commit();

      session.close();

      locator.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      locator = createInVMNonHALocator();

      sf = createSessionFactory(locator);

      session = sf.createSession(null, null, false, false, false, false, 0);

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();
      for (int i = 0; i < 37; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("id").intValue());
         msg.acknowledge();
         session.commit();
      }

      session.close();

      locator.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      locator = createInVMNonHALocator();

      sf = createSessionFactory(locator);

      session = sf.createSession(null, null, false, false, false, false, 0);

      consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();
      for (int i = 37; i < numberOfMessages; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("id").intValue());
         msg.acknowledge();
         session.commit();
      }

      session.close();
   }

   @TestTemplate
   public void testPageMultipleDestinations() throws Exception {
      internalTestPageMultipleDestinations(false);
   }

   @TestTemplate
   public void testPageMultipleDestinationsTransacted() throws Exception {
      internalTestPageMultipleDestinations(true);
   }

   @TestTemplate
   public void testDropMessagesPersistent() throws Exception {
      testDropMessages(true);
   }

   @TestTemplate
   public void testDropMessagesNonPersistent() throws Exception {
      testDropMessages(false);
   }

   public void testDropMessages(final boolean persistent) throws Exception {

      Map<String, AddressSettings> settings = new HashMap<>();

      AddressSettings set = new AddressSettings().setMaxReadPageBytes(-1);
      set.setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP).setMaxSizeMessages(5);

      settings.put(PagingTest.ADDRESS.toString(), set);

      server = createServer(persistent, createDefaultInVMConfig(), 1024, 10 * 1024, settings);

      server.start();

      final int numberOfMessages = 10;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, false, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < numberOfMessages; i++) {
         byte[] body = new byte[2048];

         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);

         producer.send(message);
      }
      session.commit();

      ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();

      for (int i = 0; i < 5; i++) {
         ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message2);

         message2.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      Wait.assertEquals(0, () -> server.getPagingManager().getPageStore(PagingTest.ADDRESS).getAddressSize());

      for (int i = 0; i < numberOfMessages; i++) {
         byte[] body = new byte[2048];

         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);

         producer.send(message);
      }
      session.commit();

      for (int i = 0; i < 5; i++) {
         ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message2);

         message2.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      session.close();

      session = sf.createSession(false, true, true);

      producer = session.createProducer(PagingTest.ADDRESS);

      for (int i = 0; i < numberOfMessages; i++) {
         byte[] body = new byte[2048];

         message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);

         producer.send(message);
      }

      session.commit();

      consumer = session.createConsumer(PagingTest.ADDRESS);

      session.start();

      for (int i = 0; i < 5; i++) {
         ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

         assertNotNull(message2);

         message2.acknowledge();
      }

      session.commit();

      assertNull(consumer.receiveImmediate());

      session.close();

      Wait.assertEquals(0, () -> server.getPagingManager().getPageStore(PagingTest.ADDRESS).getAddressSize());
   }

   @TestTemplate
   public void testDropMessagesExpiring() throws Exception {

      Map<String, AddressSettings> settings = new HashMap<>();

      AddressSettings set = new AddressSettings().setMaxReadPageBytes(-1);
      set.setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);

      settings.put(PagingTest.ADDRESS.toString(), set);

      server = createServer(true, createDefaultInVMConfig(), 1024, 1024 * 1024, settings);

      server.start();

      final int numberOfMessages = 30000;

      locator.setAckBatchSize(0);

      sf = createSessionFactory(locator);
      ClientSession sessionProducer = sf.createSession();

      sessionProducer.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = sessionProducer.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      ClientSession sessionConsumer = sf.createSession();

      class MyHandler implements MessageHandler {

         int count;

         @Override
         public void onMessage(ClientMessage message1) {
            try {
               Thread.sleep(1);
            } catch (Exception e) {

            }

            count++;

            if (count % 1000 == 0) {
               logger.debug("received {}", count);
            }

            try {
               message1.acknowledge();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }

      ClientConsumer consumer = sessionConsumer.createConsumer(PagingTest.ADDRESS);

      sessionConsumer.start();

      consumer.setMessageHandler(new MyHandler());

      for (int i = 0; i < numberOfMessages; i++) {
         byte[] body = new byte[1024];

         message = sessionProducer.createMessage(false);
         message.getBodyBuffer().writeBytes(body);

         message.setExpiration(System.currentTimeMillis() + 100);

         producer.send(message);
      }

      sessionProducer.close();
      sessionConsumer.close();
   }

   private void internalTestPageMultipleDestinations(final boolean transacted) throws Exception {
      Configuration config = createDefaultInVMConfig();

      final int NUMBER_OF_BINDINGS = 10;

      int NUMBER_OF_MESSAGES = 2;

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, !transacted, true, false, 0);

      for (int i = 0; i < NUMBER_OF_BINDINGS; i++) {
         session.createQueue(QueueConfiguration.of(SimpleString.of("someQueue" + i)).setAddress(PagingTest.ADDRESS).setDurable(true));
      }

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[1024];

      message = session.createMessage(true);
      message.getBodyBuffer().writeBytes(body);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(message);

         if (transacted) {
            session.commit();
         }
      }

      session.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(null, null, false, true, true, false, 0);

      session.start();

      for (int msg = 0; msg < NUMBER_OF_MESSAGES; msg++) {

         for (int i = 0; i < NUMBER_OF_BINDINGS; i++) {
            ClientConsumer consumer = session.createConsumer(SimpleString.of("someQueue" + i));

            ClientMessage message2 = consumer.receive(PagingTest.RECEIVE_TIMEOUT);

            assertNotNull(message2);

            message2.acknowledge();

            assertNotNull(message2);

            consumer.close();

         }
      }

      session.close();

      for (int i = 0; i < NUMBER_OF_BINDINGS; i++) {
         Queue queue = (Queue) server.getPostOffice().getBinding(SimpleString.of("someQueue" + i)).getBindable();

         assertEquals(0, getMessageCount(queue), "Queue someQueue" + i + " was supposed to be empty");
         assertEquals(0, queue.getDeliveringCount(), "Queue someQueue" + i + " was supposed to be empty");
      }
   }

   @TestTemplate
   public void testPagingOneDestinationOnly() throws Exception {
      SimpleString PAGED_ADDRESS = SimpleString.of("paged");
      SimpleString NON_PAGED_ADDRESS = SimpleString.of("non-paged");

      Configuration configuration = createDefaultInVMConfig();

      Map<String, AddressSettings> addresses = new HashMap<>();

      addresses.put("#", new AddressSettings().setMaxReadPageBytes(-1));

      AddressSettings pagedDestination = new AddressSettings().setPageSizeBytes(1024).setMaxSizeBytes(0).setMaxReadPageBytes(-1);
      AddressSettings nonPagedDestination = new AddressSettings().setPageSizeBytes(1024).setMaxSizeBytes(-1).setMaxSizeMessages(-1).setMaxReadPageBytes(-1);

      addresses.put(PAGED_ADDRESS.toString(), pagedDestination);
      addresses.put(NON_PAGED_ADDRESS.toString(), nonPagedDestination);

      server = createServer(true, configuration, -1, -1, addresses);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, false);

      session.createQueue(QueueConfiguration.of(PAGED_ADDRESS));

      session.createQueue(QueueConfiguration.of(NON_PAGED_ADDRESS));

      ClientProducer producerPaged = session.createProducer(PAGED_ADDRESS);
      ClientProducer producerNonPaged = session.createProducer(NON_PAGED_ADDRESS);

      int NUMBER_OF_MESSAGES = 10;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.getBodyBuffer().writeBytes(new byte[512]);

         producerPaged.send(msg);
         producerNonPaged.send(msg);
      }

      session.close();

      assertTrue(server.getPagingManager().getPageStore(PAGED_ADDRESS).isPaging());
      Wait.assertFalse(() -> server.getPagingManager().getPageStore(NON_PAGED_ADDRESS).isPaging());

      session = sf.createSession(false, true, false);

      session.start();

      ClientConsumer consumerNonPaged = session.createConsumer(NON_PAGED_ADDRESS);
      ClientConsumer consumerPaged = session.createConsumer(PAGED_ADDRESS);

      ClientMessage[] ackList = new ClientMessage[NUMBER_OF_MESSAGES];

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumerNonPaged.receive(5000);
         assertNotNull(msg);
         ackList[i] = msg;
      }

      assertNull(consumerNonPaged.receiveImmediate());

      for (ClientMessage ack : ackList) {
         ack.acknowledge();
      }

      consumerNonPaged.close();

      session.commit();

      ackList = null;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumerPaged.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         session.commit();
      }

      assertNull(consumerPaged.receiveImmediate());

      session.close();
   }

   @TestTemplate
   public void testSimplePaging() throws Exception {
      SimpleString PAGED_ADDRESS = SimpleString.of("paged");

      Configuration configuration = createDefaultInVMConfig();

      Map<String, AddressSettings> addresses = new HashMap<>();

      addresses.put("#", new AddressSettings().setMaxReadPageBytes(-1));

      AddressSettings pagedDestination = new AddressSettings().setPageSizeBytes(1024).setMaxSizeBytes(0).setMaxReadPageBytes(-1);

      addresses.put(PAGED_ADDRESS.toString(), pagedDestination);

      server = createServer(true, configuration, -1, -1, addresses);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PAGED_ADDRESS));

      Wait.assertTrue(() -> null != server.locateQueue(PAGED_ADDRESS));

      ClientProducer producerPaged = session.createProducer(PAGED_ADDRESS);
      int NUMBER_OF_MESSAGES = 10;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("i", i);
         msg.getBodyBuffer().writeBytes(new byte[512]);

         producerPaged.send(msg);
      }
      session.commit();

      session.close();

      assertTrue(server.getPagingManager().getPageStore(PAGED_ADDRESS).isPaging());
      session = sf.createSession(false, true, false);

      session.start();

      ClientConsumer consumerPaged = session.createConsumer(PAGED_ADDRESS);

      session.commit();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumerPaged.receive(5000);
         assertNotNull(msg, "expected message at " + i);
         int recI = msg.getIntProperty("i");
         assertEquals(i, recI);
         session.commit();
      }

      assertNull(consumerPaged.receiveImmediate());

      session.close();
   }

   @TestTemplate
   public void testTwoQueuesDifferentFilters() throws Exception {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int numberOfMessages = 200;
      locator = createInVMNonHALocator().setClientFailureCheckPeriod(120000).setConnectionTTL(5000000).setCallTimeout(120000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      // note: if you want to change this, numberOfMessages has to be a multiple of NQUEUES
      int NQUEUES = 2;

      for (int i = 0; i < NQUEUES; i++) {
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=" + i)).setAddress(PagingTest.ADDRESS).setFilterString(SimpleString.of("propTest=" + i)).setDurable(true));
      }

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = null;

      byte[] body = new byte[MESSAGE_SIZE];

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(persistentMessages);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty("propTest", i % NQUEUES);
         message.putIntProperty("id", i);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }

      session.commit();

      session.start();

      for (int nqueue = 0; nqueue < NQUEUES; nqueue++) {
         ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS.concat("=" + nqueue));

         for (int i = 0; i < (numberOfMessages / NQUEUES); i++) {
            message = consumer.receive(500000);
            assertNotNull(message);
            message.acknowledge();

            assertEquals(nqueue, message.getIntProperty("propTest").intValue());
         }

         assertNull(consumer.receiveImmediate());

         consumer.close();

         session.commit();
      }

      PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
      PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());

      long timeout = System.currentTimeMillis() + 5000;
      while (store.isPaging() && timeout > System.currentTimeMillis()) {
         Thread.sleep(100);
      }

      // It's async, so need to wait a bit for it happening
      assertFalse(server.getPagingManager().getPageStore(ADDRESS).isPaging());
   }

   @TestTemplate
   public void testTwoQueues() throws Exception {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int messageSize = 1024;

      final int numberOfMessages = 100;

      try {
         ServerLocator locator = createInVMNonHALocator().setClientFailureCheckPeriod(120000).setConnectionTTL(5000000).setCallTimeout(120000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=1")).setAddress(PagingTest.ADDRESS));
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=2")).setAddress(PagingTest.ADDRESS));

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         ClientMessage message = null;

         byte[] body = new byte[messageSize];

         for (int i = 0; i < numberOfMessages; i++) {
            message = session.createMessage(persistentMessages);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            message.putIntProperty("propTest", i % 2 == 0 ? 1 : 2);

            producer.send(message);
         }

         session.commit();

         session.start();

         for (int msg = 1; msg <= 2; msg++) {
            ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS.concat("=" + msg));

            for (int i = 0; i < numberOfMessages; i++) {
               message = consumer.receive(5000);
               assertNotNull(message);
               message.acknowledge();

            }

            session.commit();

            assertNull(consumer.receiveImmediate());

            consumer.close();
         }

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
         PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());

         Wait.waitFor(() -> !store.isPaging(), 5000);

         PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());

         waitForNotPaging(server.locateQueue(PagingTest.ADDRESS.concat("=1")));

         sf.close();

         locator.close();
      } finally {
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @TestTemplate
   public void testTwoQueuesAndOneInativeQueue() throws Exception {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator().setClientFailureCheckPeriod(120000).setConnectionTTL(5000000).setCallTimeout(120000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=1")).setAddress(PagingTest.ADDRESS));
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=2")).setAddress(PagingTest.ADDRESS));

         // A queue with an impossible filter
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("-3")).setAddress(PagingTest.ADDRESS).setFilterString(SimpleString.of("nothing='something'")));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

         Queue queue = server.locateQueue(PagingTest.ADDRESS.concat("=1"));

         queue.getPageSubscription().getPagingStore().startPaging();

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         ClientMessage message = session.createMessage(persistentMessages);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(new byte[1024]);

         producer.send(message);

         session.commit();

         session.start();

         for (int msg = 1; msg <= 2; msg++) {
            ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS.concat("=" + msg));

            message = consumer.receive(5000);
            assertNotNull(message);
            message.acknowledge();

            assertNull(consumer.receiveImmediate());

            consumer.close();
         }

         session.commit();
         session.close();

         PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());

         waitForNotPaging(server.locateQueue(PagingTest.ADDRESS.concat("=1")));

         sf.close();

         locator.close();
      } finally {
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @TestTemplate
   public void testTwoQueuesConsumeOneRestart() throws Exception {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1);

      server.start();

      final int messageSize = 1024;

      final int numberOfMessages = 100;

      try {
         ServerLocator locator = createInVMNonHALocator().setClientFailureCheckPeriod(120000).setConnectionTTL(5000000).setCallTimeout(120000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=1")).setAddress(PagingTest.ADDRESS));
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=2")).setAddress(PagingTest.ADDRESS));

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         ClientMessage message = null;

         byte[] body = new byte[messageSize];

         for (int i = 0; i < numberOfMessages; i++) {
            message = session.createMessage(persistentMessages);

            ActiveMQBuffer bodyLocal = message.getBodyBuffer();

            bodyLocal.writeBytes(body);

            message.putIntProperty("propTest", i % 2 == 0 ? 1 : 2);

            producer.send(message);
         }

         session.commit();

         session.start();

         session.deleteQueue(PagingTest.ADDRESS.concat("=1"));

         sf = locator.createSessionFactory();

         session = sf.createSession(false, false, false);

         session.start();

         ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS.concat("=2"));

         for (int i = 0; i < numberOfMessages; i++) {
            message = consumer.receive(5000);
            assertNotNull(message);
            message.acknowledge();
         }

         session.commit();

         assertNull(consumer.receiveImmediate());

         consumer.close();

         long timeout = System.currentTimeMillis() + 10000;

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

         // It's async, so need to wait a bit for it happening
         while (timeout > System.currentTimeMillis() && store.isPaging()) {
            Thread.sleep(100);
         }

         assertFalse(server.getPagingManager().getPageStore(ADDRESS).isPaging());

         server.stop();

         server.start();

         server.stop();
         server.start();

         sf.close();

         locator.close();
      } finally {
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @TestTemplate
   public void testDLAOnLargeMessageAndPaging() throws Exception {

      Configuration config = createDefaultInVMConfig().setThreadPoolMaxSize(10).setJournalSyncNonTransactional(false);

      Map<String, AddressSettings> settings = new HashMap<>();
      AddressSettings dla = new AddressSettings().setMaxDeliveryAttempts(2).setDeadLetterAddress(SimpleString.of("DLA")).setRedeliveryDelay(0).setMaxReadPageBytes(-1);
      settings.put(ADDRESS.toString(), dla);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1, settings);

      server.start();

      final int messageSize = 124;

      ServerLocator locator = null;
      ClientSessionFactory sf = null;
      ClientSession session = null;
      try {
         locator = createInVMNonHALocator();

         locator.setBlockOnNonDurableSend(true);
         locator.setBlockOnDurableSend(true);

         sf = locator.createSessionFactory();

         session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of(ADDRESS));
         session.createQueue(QueueConfiguration.of("DLA"));

         Queue serverQueue = server.locateQueue(ADDRESS);

         PagingStore pgStoreAddress = server.getPagingManager().getPageStore(ADDRESS);
         pgStoreAddress.startPaging();
         PagingStore pgStoreDLA = server.getPagingManager().getPageStore(SimpleString.of("DLA"));

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         for (int i = 0; i < 20; i++) {
            logger.debug("send message #{}", i);
            ClientMessage message = session.createMessage(true);

            message.putStringProperty("id", "str" + i);

            message.setBodyInputStream(createFakeLargeStream(messageSize));

            producer.send(message);
         }

         session.commit();

         session.start();

         ClientConsumer cons = session.createConsumer(ADDRESS);

         for (int msgNr = 0; msgNr < 2; msgNr++) {
            for (int i = 0; i < 2; i++) {
               ClientMessage msg = cons.receive(5000);

               assertNotNull(msg);

               msg.acknowledge();

               for (int j = 0; j < messageSize; j++) {
                  assertEquals(getSamplebyte(j), msg.getBodyBuffer().readByte());
               }

               session.rollback();
            }

            pgStoreDLA.startPaging();
         }

         for (int i = 2; i < 20; i++) {
            logger.debug("Received message {}", i);
            ClientMessage message = cons.receive(1000);
            assertNotNull(message, "Message " + i + " wasn't received");
            message.acknowledge();

            final AtomicInteger bytesOutput = new AtomicInteger(0);

            message.setOutputStream(new OutputStream() {
               @Override
               public void write(int b) throws IOException {
                  bytesOutput.incrementAndGet();
               }
            });

            try {
               if (!message.waitOutputStreamCompletion(10000)) {
                  if (logger.isDebugEnabled()) {
                     logger.debug(threadDump("dump"));
                  }
                  fail("Couldn't finish large message receiving");
               }
            } catch (Throwable e) {
               if (logger.isDebugEnabled()) {
                  logger.debug("output bytes = {}", bytesOutput);
                  logger.debug(threadDump("dump"));
               }
               fail("Couldn't finish large message receiving for id=" + message.getStringProperty("id") + " with messageID=" + message.getMessageID());
            }

         }

         assertNull(cons.receiveImmediate());

         cons.close();

         cons = session.createConsumer("DLA");

         for (int i = 0; i < 2; i++) {
            assertNotNull(cons.receive(5000));
         }

         sf.close();

         session.close();

         locator.close();

         server.stop();

         server.start();

         locator = createInVMNonHALocator();

         sf = locator.createSessionFactory();

         session = sf.createSession(false, false);

         session.start();

         cons = session.createConsumer(ADDRESS);

         for (int i = 2; i < 20; i++) {
            logger.debug("Received message {}", i);
            ClientMessage message = cons.receive(5000);
            assertNotNull(message, "message " + i + " should not be null");

            assertEquals("str" + i, message.getStringProperty("id"));

            message.acknowledge();

            message.setOutputStream(new OutputStream() {
               @Override
               public void write(int b) throws IOException {

               }
            });

            assertTrue(message.waitOutputStreamCompletion(5000));
         }

         assertNull(cons.receiveImmediate());

         cons.close();

         cons = session.createConsumer("DLA");

         for (int msgNr = 0; msgNr < 2; msgNr++) {
            ClientMessage msg = cons.receive(10000);

            assertNotNull(msg);

            assertEquals("str" + msgNr, msg.getStringProperty("id"));

            for (int i = 0; i < messageSize; i++) {
               assertEquals(getSamplebyte(i), msg.getBodyBuffer().readByte());
            }

            msg.acknowledge();
         }

         cons.close();

         cons = session.createConsumer(ADDRESS);

         session.commit();

         assertNull(cons.receiveImmediate());

         pgStoreAddress = server.getPagingManager().getPageStore(ADDRESS);

         pgStoreAddress.getCursorProvider().getSubscription(serverQueue.getID()).cleanupEntries(false);

         PageCursorProviderTestAccessor.cleanup(pgStoreAddress.getCursorProvider());

         Wait.assertFalse(pgStoreAddress::isPaging);
         session.commit();
      } finally {
         session.close();
         sf.close();
         locator.close();
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @TestTemplate
   public void testExpireLargeMessageOnPaging() throws Exception {

      int numberOfMessages = 100;

      Configuration config = createDefaultInVMConfig().setMessageExpiryScanPeriod(500).setJournalSyncNonTransactional(false);

      Map<String, AddressSettings> settings = new HashMap<>();
      AddressSettings dla = new AddressSettings().setMaxDeliveryAttempts(5).setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("DLA")).setMaxReadPageBytes(-1);
      settings.put(ADDRESS.toString(), dla);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX, -1, -1, settings);

      server.start();

      final int messageSize = 20;

      try {
         ServerLocator locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session = sf.createSession(false, false, false);

         session.createQueue(QueueConfiguration.of(ADDRESS));

         session.createQueue(QueueConfiguration.of("DLA"));

         PagingStore pgStoreAddress = server.getPagingManager().getPageStore(ADDRESS);
         pgStoreAddress.startPaging();

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

         ClientMessage message = null;

         for (int i = 0; i < numberOfMessages; i++) {
            if (i % 10 == 0)
               logger.debug("send message #{}", i);
            message = session.createMessage(true);

            message.putStringProperty("id", "str" + i);

            message.setExpiration(System.currentTimeMillis() + 200);

            if (i % 2 == 0) {
               message.setBodyInputStream(createFakeLargeStream(messageSize));
            } else {
               byte[] bytes = new byte[messageSize];
               for (int s = 0; s < bytes.length; s++) {
                  bytes[s] = getSamplebyte(s);
               }
               message.getBodyBuffer().writeBytes(bytes);
            }

            producer.send(message);

            if ((i + 1) % 10 == 0) {
               session.commit();
            }
         }

         session.commit();

         sf.close();

         locator.close();

         server.stop();

         Thread.sleep(1000);

         server.start();

         locator = createInVMNonHALocator();

         sf = locator.createSessionFactory();

         session = sf.createSession(false, false);

         session.start();

         ClientConsumer consAddr = session.createConsumer(ADDRESS);

         assertNull(consAddr.receiveImmediate());

         ClientConsumer cons = session.createConsumer("DLA");

         for (int i = 0; i < numberOfMessages; i++) {
            logger.debug("Received message {}", i);
            message = cons.receive(1000);
            assertNotNull(message);
            message.acknowledge();

            message.saveToOutputStream(new OutputStream() {
               @Override
               public void write(int b) throws IOException {

               }
            });
         }

         assertNull(cons.receiveImmediate());

         session.commit();

         cons.close();

         pgStoreAddress = server.getPagingManager().getPageStore(ADDRESS);

         Wait.assertFalse(pgStoreAddress::isPaging, 5000, 100);

         session.close();
      } finally {
         locator.close();
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   /**
    * When running this test from an IDE add this to the test command line so that the {@link AssertionLoggerHandler} works
    * properly:
    * <pre>
    * -Dlog4j2.configurationFile=file:${path-to-source}/tests/config/log4j2-tests-config.properties
    * </pre>
    * Note: Idea should get these from the pom and you shouldn't need to do this.
    */
   @TestTemplate
   public void testFailMessagesNonDurable() throws Exception {

      Configuration config = createDefaultInVMConfig();

      Map<String, AddressSettings> settings = new HashMap<>();

      AddressSettings set = new AddressSettings().setMaxReadPageBytes(-1);
      set.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL).setMaxSizeMessages(10);

      settings.put(PagingTest.ADDRESS.toString(), set);

      server = createServer(true, config, 1024, 5 * 1024, settings);

      server.start();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, true, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = session.createMessage(false);

      int biggerMessageSize = 1024;
      byte[] body = new byte[biggerMessageSize];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int j = 1; j <= biggerMessageSize; j++) {
         bb.put(getSamplebyte(j));
      }

      message.getBodyBuffer().writeBytes(body);

      // Send enough messages to fill up the address, but don't test for an immediate exception because we do not block
      // on non-durable send. Instead of receiving an exception immediately the exception will be logged on the server.
      for (int i = 0; i < 32; i++) {
         producer.send(message);
      }

      // allow time for the logging to actually happen on the server
      Wait.assertTrue("Expected to find AMQ224016", () -> loggerHandler.findText("AMQ224016"), 100);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      // Once the destination is full and the client has run out of credits then it will receive an exception
      for (int i = 0; i < 10; i++) {
         validateExceptionOnSending(producer, message);
      }

      // Receive a message.. this should release credits
      ClientMessage msgReceived = consumer.receive(5000);
      assertNotNull(msgReceived);
      msgReceived.acknowledge();
      session.commit(); // to make sure it's on the server (roundtrip)

      try {
         for (int i = 0; i < 1000; i++) {
            // this send will succeed on the server
            producer.send(message);
         }
         fail("Expected to throw an exception");
      } catch (Exception expected) {
      }
   }

   @TestTemplate
   public void testFailMessagesDurable() throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig();

      Map<String, AddressSettings> settings = new HashMap<>();

      server = createServer(true, config, 1024, 5 * 1024, settings);

      server.start();

      server.getAddressSettingsRepository().clear();
      AddressSettings set = new AddressSettings().setMaxReadPageBytes(-1);
      set.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL).setMaxSizeMessages(11);
      server.getAddressSettingsRepository().addMatch(PagingTest.ADDRESS.toString(), set);

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, true, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = session.createMessage(true);

      int biggerMessageSize = 2048;
      byte[] body = new byte[biggerMessageSize];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int j = 1; j <= biggerMessageSize; j++) {
         bb.put(getSamplebyte(j));
      }

      message.getBodyBuffer().writeBytes(body);

      // Send enough messages to fill up the address and test for an exception.
      // The address will actually fill up after 3 messages. Also, it takes 32 messages for the client's
      // credits to run out.
      for (int i = 0; i < 50; i++) {
         if (i > 10) {
            validateExceptionOnSending(producer, message);
         } else {
            producer.send(message);
         }
      }

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      // Receive a message.. this should release credits
      ClientMessage msgReceived = consumer.receive(5000);
      assertNotNull(msgReceived);
      msgReceived.acknowledge();
      session.commit(); // to make sure it's on the server (roundtrip)

      boolean exception = false;

      try {
         for (int i = 0; i < 1000; i++) {
            // this send will succeed on the server
            producer.send(message);
         }
      } catch (Exception e) {
         exception = true;
      }

      assertTrue(exception, "Expected to throw an exception");
   }

   @TestTemplate
   public void testFailMessagesDuplicates() throws Exception {

      Configuration config = createDefaultInVMConfig();

      Map<String, AddressSettings> settings = new HashMap<>();

      AddressSettings set = new AddressSettings().setMaxReadPageBytes(-1);
      set.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL).setMaxSizeMessages(3);

      settings.put(PagingTest.ADDRESS.toString(), set);

      server = createServer(true, config, 1024, 5 * 1024, settings);

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = addClientSession(sf.createSession(true, true, 0));

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      ClientMessage message = session.createMessage(true);

      int biggerMessageSize = 2048;
      byte[] body = new byte[biggerMessageSize];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int j = 1; j <= biggerMessageSize; j++) {
         bb.put(getSamplebyte(j));
      }

      message.getBodyBuffer().writeBytes(body);

      // Send enough messages to fill up the address.
      producer.send(message);
      producer.send(message);
      producer.send(message);

      Queue q = (Queue) server.getPostOffice().getBinding(ADDRESS).getBindable();
      Wait.assertEquals(3, q::getMessageCount);

      // send a message with a dup ID that should fail b/c the address is full
      SimpleString dupID1 = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      message.putStringProperty("key", dupID1.toString());

      validateExceptionOnSending(producer, message);

      Wait.assertEquals(3, q::getMessageCount);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      // Receive a message...this should open space for another message
      ClientMessage msgReceived = consumer.receive(5000);
      assertNotNull(msgReceived);
      msgReceived.acknowledge();
      session.commit(); // to make sure it's on the server (roundtrip)
      consumer.close();

      Wait.assertEquals(2, q::getMessageCount);

      producer.send(message);

      Wait.assertEquals(3, q::getMessageCount);

      consumer = session.createConsumer(ADDRESS);

      for (int i = 0; i < 3; i++) {
         msgReceived = consumer.receive(5000);
         assertNotNull(msgReceived);
         msgReceived.acknowledge();
         session.commit();
      }
   }

   /**
    * This method validates if sending a message will throw an exception
    */
   private void validateExceptionOnSending(ClientProducer producer, ClientMessage message) {
      ActiveMQException expected = null;

      try {
         // after the address is full this send should fail (since the address full policy is FAIL)
         producer.send(message);
      } catch (ActiveMQException e) {
         expected = e;
      }

      assertNotNull(expected);
      assertEquals(ActiveMQExceptionType.ADDRESS_FULL, expected.getType());
   }

   @TestTemplate
   public void testSpreadMessagesWithFilterWithDeadConsumer() throws Exception {
      testSpreadMessagesWithFilter(true);
   }

   @TestTemplate
   public void testSpreadMessagesWithFilterWithoutDeadConsumer() throws Exception {
      testSpreadMessagesWithFilter(false);
   }

   @TestTemplate
   public void testRouteOnTopWithMultipleQueues() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      ServerLocator locator = createInVMNonHALocator().setBlockOnDurableSend(false);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, 0);

      session.createQueue(QueueConfiguration.of("Q1").setAddress("Q").setFilterString("dest=1"));
      session.createQueue(QueueConfiguration.of("Q2").setAddress("Q").setFilterString("dest=2"));
      session.createQueue(QueueConfiguration.of("Q3").setAddress("Q").setFilterString("dest=3"));

      Queue queue = server.locateQueue(SimpleString.of("Q1"));
      queue.getPageSubscription().getPagingStore().startPaging();

      ClientProducer prod = session.createProducer("Q");
      ClientMessage msg = session.createMessage(true);
      msg.putIntProperty("dest", 1);
      prod.send(msg);
      session.commit();

      msg = session.createMessage(true);
      msg.putIntProperty("dest", 2);
      prod.send(msg);
      session.commit();

      session.start();
      ClientConsumer cons1 = session.createConsumer("Q1");
      msg = cons1.receive(5000);
      assertNotNull(msg);
      msg.acknowledge();

      ClientConsumer cons2 = session.createConsumer("Q2");
      msg = cons2.receive(5000);
      assertNotNull(msg);

      queue.getPageSubscription().getPagingStore().forceAnotherPage(true);

      msg = session.createMessage(true);
      msg.putIntProperty("dest", 1);
      prod.send(msg);
      session.commit();

      msg = cons1.receive(5000);
      assertNotNull(msg);
      msg.acknowledge();

      queue.getPageSubscription().cleanupEntries(false);

      server.stop();
   }

   // spread messages because of filters
   public void testSpreadMessagesWithFilter(boolean deadConsumer) throws Exception {

      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(false);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, false);

         session.createQueue(QueueConfiguration.of("Q1").setAddress(PagingTest.ADDRESS).setFilterString("destQ=1 or both=true"));
         session.createQueue(QueueConfiguration.of("Q2").setAddress(PagingTest.ADDRESS).setFilterString("destQ=2 or both=true"));

         if (deadConsumer) {
            // This queue won't receive any messages
            session.createQueue(QueueConfiguration.of("Q3").setAddress(ADDRESS.toString()).setFilterString("destQ=3"));
         }

         session.createQueue(QueueConfiguration.of("Q_initial").setAddress(ADDRESS.toString()).setFilterString("initialBurst=true"));

         ClientSession sessionConsumerQ3 = null;

         final AtomicInteger consumerQ3Msgs = new AtomicInteger(0);

         if (deadConsumer) {
            sessionConsumerQ3 = sf.createSession(true, true);
            ClientConsumer consumerQ3 = sessionConsumerQ3.createConsumer("Q3");

            consumerQ3.setMessageHandler(message -> consumerQ3Msgs.incrementAndGet());

            sessionConsumerQ3.start();

         }

         final int initialBurst = 100;
         final int messagesSentAfterBurst = 100;
         final int MESSAGE_SIZE = 300;
         final byte[] bodyWrite = new byte[MESSAGE_SIZE];

         Queue serverQueue = server.locateQueue(SimpleString.of("Q1"));
         PagingStore pageStore = serverQueue.getPageSubscription().getPagingStore();

         ClientProducer producer = session.createProducer(ADDRESS);

         // send an initial burst that will put the system into page mode. The initial burst will be towards Q1 only
         for (int i = 0; i < initialBurst; i++) {
            ClientMessage m = session.createMessage(true);
            m.getBodyBuffer().writeBytes(bodyWrite);
            m.putIntProperty("destQ", 1);
            m.putBooleanProperty("both", false);
            m.putBooleanProperty("initialBurst", true);
            producer.send(m);
            if (i % 100 == 0) {
               session.commit();
            }
         }

         session.commit();

         pageStore.forceAnotherPage(true);

         for (int i = 0; i < messagesSentAfterBurst; i++) {
            {
               ClientMessage m = session.createMessage(true);
               m.getBodyBuffer().writeBytes(bodyWrite);
               m.putIntProperty("destQ", 1);
               m.putBooleanProperty("initialBurst", false);
               m.putIntProperty("i", i);
               m.putBooleanProperty("both", i % 10 == 0);
               producer.send(m);
            }

            if (i % 10 != 0) {
               ClientMessage m = session.createMessage(true);
               m.getBodyBuffer().writeBytes(bodyWrite);
               m.putIntProperty("destQ", 2);
               m.putIntProperty("i", i);
               m.putBooleanProperty("both", false);
               m.putBooleanProperty("initialBurst", false);
               producer.send(m);
            }

            if (i > 0 && i % 10 == 0) {
               session.commit();
               if (i + 10 < messagesSentAfterBurst) {
                  pageStore.forceAnotherPage(true);
               }
            }
         }

         session.commit();

         ClientConsumer consumerQ1 = session.createConsumer("Q1");
         ClientConsumer consumerQ2 = session.createConsumer("Q2");
         session.start();

         for (int i = 0; i < initialBurst; i++) {
            ClientMessage m = consumerQ1.receive(5000);
            assertNotNull(m);
            assertEquals(1, m.getIntProperty("destQ").intValue());
            m.acknowledge();
            session.commit();
         }

         // This will consume messages from the beginning of the queue only
         ClientConsumer consumerInitial = session.createConsumer("Q_initial");
         for (int i = 0; i < initialBurst; i++) {
            ClientMessage m = consumerInitial.receive(5000);
            assertNotNull(m);
            assertEquals(1, m.getIntProperty("destQ").intValue());
            m.acknowledge();
         }

         assertNull(consumerInitial.receiveImmediate());
         session.commit();

         // messages from Q1

         for (int i = 0; i < messagesSentAfterBurst; i++) {
            ClientMessage m = consumerQ1.receive(5000);
            assertNotNull(m);
            if (!m.getBooleanProperty("both")) {
               assertEquals(1, m.getIntProperty("destQ").intValue());
            }
            assertEquals(i, m.getIntProperty("i").intValue());
            m.acknowledge();

            session.commit();
         }

         for (int i = 0; i < messagesSentAfterBurst; i++) {
            ClientMessage m = consumerQ2.receive(5000);
            assertNotNull(m);

            if (!m.getBooleanProperty("both")) {
               assertEquals(2, m.getIntProperty("destQ").intValue());
            }
            assertEquals(i, m.getIntProperty("i").intValue());
            m.acknowledge();

            session.commit();
         }

         waitForNotPaging(serverQueue);

         if (sessionConsumerQ3 != null) {
            sessionConsumerQ3.close();
         }
         assertEquals(0, consumerQ3Msgs.intValue());

         session.close();
         locator.close();

      } finally {
         server.stop();
      }
   }

   // We send messages to pages, create a big hole (a few pages without any messages), ack everything
   // and expect it to move to the next page
   @TestTemplate
   public void testPageHole() throws Throwable {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator().setBlockOnDurableSend(true);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, true, 0);

         session.createQueue(QueueConfiguration.of("Q1").setAddress(ADDRESS.toString()).setFilterString("dest=1"));
         session.createQueue(QueueConfiguration.of("Q2").setAddress(ADDRESS.toString()).setFilterString("dest=2"));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

         store.startPaging();

         ClientProducer prod = session.createProducer(ADDRESS);

         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("dest", 1);
         prod.send(msg);

         for (int i = 0; i < 100; i++) {
            msg = session.createMessage(true);
            msg.putIntProperty("dest", 2);
            prod.send(msg);

            if (i > 0 && i % 10 == 0) {
               store.forceAnotherPage(true);
            }
         }

         session.start();

         ClientConsumer cons1 = session.createConsumer("Q1");

         ClientMessage msgReceivedCons1 = cons1.receive(5000);
         assertNotNull(msgReceivedCons1);
         msgReceivedCons1.acknowledge();

         ClientConsumer cons2 = session.createConsumer("Q2");
         for (int i = 0; i < 100; i++) {
            ClientMessage msgReceivedCons2 = cons2.receive(1000);
            assertNotNull(msgReceivedCons2);
            msgReceivedCons2.acknowledge();

            session.commit();

            // It will send another message when it's mid consumed
            if (i == 20) {
               // wait at least one page to be deleted before sending a new one
               for (long timeout = System.currentTimeMillis() + 5000; timeout > System.currentTimeMillis() && store.checkPageFileExists(2); ) {
                  Thread.sleep(10);
               }
               msg = session.createMessage(true);
               msg.putIntProperty("dest", 1);
               prod.send(msg);
            }
         }

         msgReceivedCons1 = cons1.receive(5000);
         assertNotNull(msgReceivedCons1);
         msgReceivedCons1.acknowledge();

         assertNull(cons1.receiveImmediate());
         assertNull(cons2.receiveImmediate());

         session.commit();

         session.close();

         waitForNotPaging(store);
      } finally {
         server.stop();
      }

   }

   @TestTemplate
   public void testMultiFiltersBrowsing() throws Throwable {
      internalTestMultiFilters(true);
   }

   @TestTemplate
   public void testMultiFiltersRegularConsumer() throws Throwable {
      internalTestMultiFilters(false);
   }

   public void internalTestMultiFilters(boolean browsing) throws Throwable {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator().setBlockOnDurableSend(true);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, true, 0);

         session.createQueue(QueueConfiguration.of("Q1").setAddress(ADDRESS.toString()));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

         ClientProducer prod = session.createProducer(ADDRESS);

         ClientMessage msg = null;
         store.startPaging();

         for (int i = 0; i < 100; i++) {
            msg = session.createMessage(true);
            msg.putStringProperty("color", "red");
            msg.putIntProperty("count", i);
            prod.send(msg);

            if (i > 0 && i % 10 == 0) {
               store.startPaging();
               store.forceAnotherPage(true);
            }
         }

         for (int i = 0; i < 100; i++) {
            msg = session.createMessage(true);
            msg.putStringProperty("color", "green");
            msg.putIntProperty("count", i);
            prod.send(msg);

            if (i > 0 && i % 10 == 0) {
               store.startPaging();
               store.forceAnotherPage(true);
            }
         }

         session.commit();

         session.close();

         session = sf.createSession(false, false, 0);
         session.start();

         ClientConsumer cons1;

         if (browsing) {
            cons1 = session.createConsumer("Q1", "color='green'", true);
         } else {
            cons1 = session.createConsumer("Q1", "color='red'", false);
         }

         for (int i = 0; i < 100; i++) {
            msg = cons1.receive(5000);

            assertNotNull(msg);
            if (!browsing) {
               msg.acknowledge();
            }
         }

         session.commit();

         session.close();
      } finally {
         server.stop();
      }

   }

   @TestTemplate
   public void testPendingACKOutOfOrder() throws Throwable {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(false);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, true, 0);

         session.createQueue(QueueConfiguration.of("Q1").setAddress(ADDRESS.toString()));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

         store.startPaging();

         ClientProducer prod = session.createProducer(ADDRESS);

         for (int i = 0; i < 100; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.putIntProperty("count", i);
            prod.send(msg);
            if ((i + 1) % 5 == 0 && i < 50) {
               session.commit();
               store.forceAnotherPage(true);
            }
         }
         session.commit();

         session.start();

         ClientConsumer cons1 = session.createConsumer("Q1");

         for (int i = 0; i < 100; i++) {
            ClientMessage msg = cons1.receive(5000);
            assertNotNull(msg);

            if (i == 13) {
               msg.individualAcknowledge();
            }
         }

         session.close();

         locator.close();

         server.stop();

         server.start();

         store = server.getPagingManager().getPageStore(ADDRESS);

         locator = createInVMNonHALocator();

         sf = locator.createSessionFactory();

         session = sf.createSession(true, true, 0);
         cons1 = session.createConsumer("Q1");
         session.start();

         for (int i = 0; i < 99; i++) {
            ClientMessage msg = cons1.receive(5000);
            assertNotNull(msg);
            msg.acknowledge();
         }

         assertNull(cons1.receiveImmediate());

         session.close();
         waitForNotPaging(store);
      } finally {
         server.stop();
      }

   }

   // Test a scenario where a page was complete and now needs to be cleared
   @TestTemplate
   public void testPageCompleteWasLive() throws Throwable {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(false);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, true, 0);

         session.createQueue(QueueConfiguration.of("Q1").setAddress(ADDRESS.toString()).setFilterString("dest=1"));
         session.createQueue(QueueConfiguration.of("Q2").setAddress(ADDRESS.toString()).setFilterString("dest=2"));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

         store.startPaging();

         ClientProducer prod = session.createProducer(ADDRESS);

         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("dest", 1);
         prod.send(msg);

         msg = session.createMessage(true);
         msg.putIntProperty("dest", 2);
         prod.send(msg);

         session.start();

         ClientConsumer cons1 = session.createConsumer("Q1");

         ClientMessage msgReceivedCons1 = cons1.receive(1000);

         assertNotNull(msgReceivedCons1);

         ClientConsumer cons2 = session.createConsumer("Q2");
         ClientMessage msgReceivedCons2 = cons2.receive(1000);
         assertNotNull(msgReceivedCons2);

         store.forceAnotherPage(true);

         msg = session.createMessage(true);

         msg.putIntProperty("dest", 1);

         prod.send(msg);

         msgReceivedCons1.acknowledge();

         msgReceivedCons1 = cons1.receive(1000);
         assertNotNull(msgReceivedCons1);
         msgReceivedCons1.acknowledge();
         msgReceivedCons2.acknowledge();

         assertNull(cons1.receiveImmediate());
         assertNull(cons2.receiveImmediate());

         session.commit();

         session.close();

         waitForNotPaging(store);
      } finally {
         server.stop();
      }

   }

   @TestTemplate
   public void testNoCursors() throws Exception {
      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = locator.createSessionFactory();
      ClientSession session = sf.createSession();

      session.createQueue(QueueConfiguration.of(ADDRESS));
      ClientProducer prod = session.createProducer(ADDRESS);

      for (int i = 0; i < 100; i++) {
         Message msg = session.createMessage(true);
         msg.toCore().getBodyBuffer().writeBytes(new byte[1024]);
         prod.send(msg);
      }

      session.commit();

      session.deleteQueue(ADDRESS);
      session.close();
      sf.close();
      locator.close();
      server.stop();
      server.start();
      waitForNotPaging(server.getPagingManager().getPageStore(ADDRESS));
      server.stop();

   }

   // Test a scenario where a page was complete and now needs to be cleared
   @TestTemplate
   public void testMoveMessages() throws Throwable {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int LARGE_MESSAGE_SIZE = database == Database.JOURNAL ? 1024 * 1024 : 1024;

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(false);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(false, false, 0);

         session.createQueue(QueueConfiguration.of("Q1"));
         session.createQueue(QueueConfiguration.of("Q2"));

         PagingStore store = server.getPagingManager().getPageStore(SimpleString.of("Q1"));

         ClientProducer prod = session.createProducer("Q1");

         for (int i = 0; i < 50; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.putIntProperty("count", i);
            if (i > 0 && i % 10 == 0) {
               msg.setBodyInputStream(createFakeLargeStream(LARGE_MESSAGE_SIZE));
            }
            prod.send(msg);
         }
         session.commit();

         store.startPaging();
         for (int i = 50; i < 100; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.putIntProperty("count", i);
            if (i % 10 == 0) {
               msg.setBodyInputStream(createFakeLargeStream(LARGE_MESSAGE_SIZE));
            }
            prod.send(msg);
            if (i % 10 == 0) {
               session.commit();
               store.forceAnotherPage(true);
            }
         }
         session.commit();

         Queue queue = server.locateQueue(SimpleString.of("Q1"));

         queue.moveReferences(10, (Filter) null, SimpleString.of("Q2"), false, server.getPostOffice().getBinding(SimpleString.of("Q2")));

         waitForNotPaging(store);

         session.close();
         locator.close();

         server.stop();
         server.start();

         locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(false);
         sf = locator.createSessionFactory();
         session = sf.createSession(true, true, 0);

         session.start();

         ClientConsumer cons = session.createConsumer("Q2");

         for (int i = 0; i < 100; i++) {
            ClientMessage msg = cons.receive(10000);
            assertNotNull(msg);
            if (i > 0 && i % 10 == 0) {
               byte[] largeMessageRead = new byte[LARGE_MESSAGE_SIZE];
               msg.getBodyBuffer().readBytes(largeMessageRead);
               for (int j = 0; j < LARGE_MESSAGE_SIZE; j++) {
                  assertEquals(largeMessageRead[j], getSamplebyte(j));
               }
            }
            msg.acknowledge();
            assertEquals(i, msg.getIntProperty("count").intValue());
         }
         session.commit();

         assertNull(cons.receiveImmediate());

         waitForNotPaging(server.locateQueue(SimpleString.of("Q2")));

         session.close();
         sf.close();
         locator.close();

      } finally {
         server.stop();
      }

   }

   @TestTemplate
   public void testOnlyOnePageOnServerCrash() throws Throwable {

      Configuration config = createDefaultInVMConfig();

      class NonStoppablePagingStoreImpl extends PagingStoreImpl {

         NonStoppablePagingStoreImpl(SimpleString address,
                                     ScheduledExecutorService scheduledExecutor,
                                     long syncTimeout,
                                     PagingManager pagingManager,
                                     StorageManager storageManager,
                                     SequentialFileFactory fileFactory,
                                     PagingStoreFactory storeFactory,
                                     SimpleString storeName,
                                     AddressSettings addressSettings,
                                     ArtemisExecutor executor,
                                     boolean syncNonTransactional) {
            super(address, scheduledExecutor, syncTimeout, pagingManager, storageManager, fileFactory, storeFactory, storeName, addressSettings, executor, syncNonTransactional, () -> purgeFolders);
         }

         /**
          * Normal stopping will cleanup non tx page subscription counter which will not trigger the bug. Here we
          * override stop to simulate server crash.
          */
         @Override
         public synchronized void stop() throws Exception {
         }
      }

      if (database != Database.JOURNAL) {
         server = new ActiveMQServerImpl(config, ManagementFactory.getPlatformMBeanServer(), new ActiveMQSecurityManagerImpl()) {
            @Override
            protected PagingStoreFactoryDatabase getPagingStoreFactory() throws Exception {
               return new PagingStoreFactoryDatabase((DatabaseStorageConfiguration) this.getConfiguration().getStoreConfiguration(), this.getStorageManager(), this.getConfiguration().getJournalBufferTimeout_NIO(), this.getScheduledPool(), this.getExecutorFactory(), this.getConfiguration().isJournalSyncNonTransactional(), null) {
                  @Override
                  public synchronized PagingStore newStore(SimpleString address, AddressSettings settings) {
                     return new NonStoppablePagingStoreImpl(address, this.getScheduledExecutor(), config.getJournalBufferTimeout_NIO(), getPagingManager(), getStorageManager(), null, this, address, settings, getExecutorFactory().getExecutor(), this.syncNonTransactional);
                  }
               };
            }
         };
      } else {
         server = new ActiveMQServerImpl(config, ManagementFactory.getPlatformMBeanServer(), new ActiveMQSecurityManagerImpl()) {
            @Override
            protected PagingStoreFactoryNIO getPagingStoreFactory() {
               return new PagingStoreFactoryNIO(this.getStorageManager(), this.getConfiguration().getPagingLocation(), this.getConfiguration().getJournalBufferTimeout_NIO(), this.getScheduledPool(), this.getExecutorFactory(), this.getConfiguration().isJournalSyncNonTransactional(), null, () -> purgeFolders) {
                  @Override
                  public synchronized PagingStore newStore(SimpleString address, AddressSettings settings) {
                     return new NonStoppablePagingStoreImpl(address, this.getScheduledExecutor(), config.getJournalBufferTimeout_NIO(), getPagingManager(), getStorageManager(), null, this, address, settings, getExecutorFactory().getExecutor(), this.isSyncNonTransactional());
                  }
               };
            }
         };
      }

      addServer(server);

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(PagingTest.PAGE_SIZE).setMaxSizeBytes(PagingTest.PAGE_SIZE + MESSAGE_SIZE).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxReadPageBytes(-1);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      server.start();

      // Here we send some messages to ensure the queue start paging and create only one page
      final int numberOfMessages = 12;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

         message.putIntProperty("count", i);

         producer.send(message);

      }
      producer.close();
      session.close();

      Queue queue = server.locateQueue(PagingTest.ADDRESS);
      Wait.assertEquals(numberOfMessages, queue::getMessageCount);
      Wait.assertEquals(1, ()->server.getPagingManager().getPageStore(ADDRESS).getNumberOfPages());

      sf.close();

      server.stop();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_SIZE + MESSAGE_SIZE);
      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      session.start();

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = consumer.receive(1000);
         assertNotNull(msg, i + "th msg is null");
         assertEquals(i, msg.getIntProperty("count").intValue());
         msg.acknowledge();
      }

      assertNull(consumer.receiveImmediate());
      session.commit();

      session.close();
      sf.close();
      locator.close();
      server.stop();
   }

   @TestTemplate
   public void testPagingStoreDestroyed() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 5000;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

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

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
      producer.close();
      assertTrue(Arrays.asList(server.getPagingManager().getStoreNames()).contains(PagingTest.ADDRESS));
      assertTrue(server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging());

      session.deleteQueue(PagingTest.ADDRESS);
      session.close();
      sf.close();
      locator.close();
      locator = null;
      sf = null;
      Wait.assertFalse(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(PagingTest.ADDRESS));
      // Ensure pagingStore is physically deleted
      server.getPagingManager().reloadStores();
      Wait.assertFalse(() -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(PagingTest.ADDRESS));
      server.stop();

      server.start();
      assertFalse(Arrays.asList(server.getPagingManager().getStoreNames()).contains(PagingTest.ADDRESS));
      // Ensure pagingStore is physically deleted
      server.getPagingManager().reloadStores();
      assertFalse(Arrays.asList(server.getPagingManager().getStoreNames()).contains(PagingTest.ADDRESS));
      server.stop();
   }

   @TestTemplate
   public void testStopPagingWithoutConsumersIfTwoPages() throws Exception {
      testStopPagingWithoutConsumersOnOneQueue(true);
   }

   @TestTemplate
   public void testStopPagingWithoutConsumersIfOnePage() throws Exception {
      testStopPagingWithoutConsumersOnOneQueue(false);
   }

   private void testStopPagingWithoutConsumersOnOneQueue(boolean forceAnotherPage) throws Exception {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator().setClientFailureCheckPeriod(120000).setConnectionTTL(5000000).setCallTimeout(120000).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(false, false, false);
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=1")).setAddress(PagingTest.ADDRESS).setFilterString("destQ=1 or both=true"));
         session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=2")).setAddress(PagingTest.ADDRESS).setFilterString("destQ=2 or both=true"));
         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
         Queue queue = server.locateQueue(PagingTest.ADDRESS.concat("=1"));
         queue.getPageSubscription().getPagingStore().startPaging();

         ClientProducer producer = session.createProducer(PagingTest.ADDRESS);
         ClientMessage message = session.createMessage(persistentMessages);
         message.putBooleanProperty("both", true);
         ActiveMQBuffer bodyLocal = message.getBodyBuffer();
         bodyLocal.writeBytes(new byte[1024]);
         producer.send(message);
         session.commit();
         session.start();
         ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS.concat("=2"));
         message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();
         assertNull(consumer.receiveImmediate());
         consumer.close();
         session.commit();

         if (forceAnotherPage) {
            queue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }

         message = session.createMessage(persistentMessages);
         message.putIntProperty("destQ", 1);
         bodyLocal = message.getBodyBuffer();
         bodyLocal.writeBytes(new byte[1024]);
         producer.send(message);
         session.commit();

         consumer = session.createConsumer(PagingTest.ADDRESS.concat("=1"));
         for (int i = 0; i < 2; i++) {
            message = consumer.receive(5000);
            assertNotNull(message);
            message.acknowledge();
            session.commit();
         }
         assertNull(consumer.receiveImmediate());
         consumer.close();
         session.close();

         PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());
         waitForNotPaging(server.locateQueue(PagingTest.ADDRESS.concat("=1")));
         sf.close();
         locator.close();
      } finally {
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @TestTemplate
   public void testStopPagingWithoutMsgsOnOneQueue() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, PagingTest.PAGE_SIZE, 1, -1, -1);

      server.start();

      final int numberOfMessages = 500;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=1")).setAddress(PagingTest.ADDRESS).setFilterString("destQ=1"));
      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS.concat("=2")).setAddress(PagingTest.ADDRESS).setFilterString("destQ=2"));

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);
      ClientConsumer consumer1 = session.createConsumer(PagingTest.ADDRESS.concat("=1"));
      session.start();
      ClientSession session2 = sf.createSession(false, false, false);
      ClientConsumer consumer2 = session2.createConsumer(PagingTest.ADDRESS.concat("=2"));
      session2.start();

      ClientMessage message = null;

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      /*
       * Here we first send messages and consume them to move every subscription to the next bookmarked page.
       * Then we send messages and consume them again, expecting paging is stopped normally.
       */
      for (int x = 0; x < 2; x++) {
         for (int i = 0; i < numberOfMessages; i++) {
            message = session.createMessage(true);
            message.putIntProperty("destQ", 1);
            ActiveMQBuffer bodyLocal = message.getBodyBuffer();
            bodyLocal.writeBytes(body);
            producer.send(message);
            session.commit();
         }
         assertTrue(Arrays.asList(server.getPagingManager().getStoreNames()).contains(PagingTest.ADDRESS));
         assertTrue(server.getPagingManager().getPageStore(PagingTest.ADDRESS).isPaging());
         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = consumer1.receive(5000);
            assertNotNull(msg);
            msg.acknowledge();
         }
         session.commit();
         assertNull(consumer1.receiveImmediate());
         waitForNotPaging(server.locateQueue(PagingTest.ADDRESS.concat("=1")));
      }

      producer.close();
      consumer1.close();
      consumer2.close();
      session.close();
      session2.close();
      sf.close();
      locator.close();
      locator = null;
      sf = null;
      server.stop();
   }

   // We send messages to page, evict live page cache, send last message when mid consumed, and expect to receive all messages
   @TestTemplate
   public void testLivePageCacheEvicted() throws Throwable {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);
      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator().setBlockOnDurableSend(true);
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, true, 0);

         session.createQueue(QueueConfiguration.of(ADDRESS));

         PagingStore store = server.getPagingManager().getPageStore(ADDRESS);
         store.startPaging();

         ClientProducer prod = session.createProducer(ADDRESS);
         int num = 10;
         for (int i = 0; i < num; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.putIntProperty("index", i);
            prod.send(msg);
         }

         session.start();
         ClientConsumer cons = session.createConsumer(ADDRESS);
         ClientMessage msgReceivedCons = null;
         // simulate the live page cache evicted
         for (int i = 0; i < num; i++) {
            msgReceivedCons = cons.receive(5000);
            assertNotNull(msgReceivedCons);
            assertEquals(i, (int) msgReceivedCons.getIntProperty("index"));
            msgReceivedCons.acknowledge();

            session.commit();

            if (i == num / 2) {
               ClientMessage msg = session.createMessage(true);
               msg.putIntProperty("index", num);
               prod.send(msg);
            }
         }

         msgReceivedCons = cons.receive(5000);
         assertNotNull(msgReceivedCons);
         assertEquals(num, (int) msgReceivedCons.getIntProperty("index"));
         msgReceivedCons.acknowledge();

         assertNull(cons.receiveImmediate());

         session.commit();
         session.close();

         waitForNotPaging(store);
      } finally {
         server.stop();
      }
   }

   @TestTemplate
   public void testRollbackPageTransactionBeforeDelivery() throws Exception {
      testRollbackPageTransaction(true);
   }

   @TestTemplate
   public void testRollbackPageTransactionAfterDelivery() throws Exception {
      testRollbackPageTransaction(false);
   }

   private void testRollbackPageTransaction(boolean rollbackBeforeDelivery) throws Exception {

      Configuration config = createDefaultInVMConfig();

      server = createServer(true, config, PagingTest.PAGE_SIZE, PagingTest.PAGE_MAX);

      server.start();

      final int numberOfMessages = 2;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, false, true, false, 0);

      session.createQueue(QueueConfiguration.of(PagingTest.ADDRESS));

      Queue queue = server.locateQueue(PagingTest.ADDRESS);

      queue.getPageSubscription().getPagingStore().startPaging();

      ClientProducer producer = session.createProducer(PagingTest.ADDRESS);

      if (rollbackBeforeDelivery) {
         sendMessages(session, producer, numberOfMessages);
         session.rollback();
         assertEquals(1, server.getPagingManager().getTransactions().size());
         PageTransactionInfo pageTransactionInfo = server.getPagingManager().getTransactions().values().iterator().next();
         // Make sure rollback happens before delivering messages
         Wait.assertTrue(() -> pageTransactionInfo.isRollback(), 1000, 100);
         ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);
         session.start();
         assertNull(consumer.receiveImmediate());
         Wait.assertTrue(() -> server.getPagingManager().getTransactions().isEmpty(), 5000);
      } else {
         ClientConsumer consumer = session.createConsumer(PagingTest.ADDRESS);
         session.start();
         sendMessages(session, producer, numberOfMessages);
         assertNull(consumer.receiveImmediate());
         assertEquals(1, server.getPagingManager().getTransactions().size());
         PageTransactionInfo pageTransactionInfo = server.getPagingManager().getTransactions().values().iterator().next();
         session.rollback();
         Wait.assertTrue(() -> pageTransactionInfo.isRollback(), 1000, 100);
         Wait.assertTrue(() -> server.getPagingManager().getTransactions().isEmpty(), 5000);
      }

      session.close();
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


   /**
    * Introduce future ACKs hoping the server will clean them up before starting
    * this could happen when users remove paging folders. This is a cleanup task.
    * **/
   @TestTemplate
   public void testCleanupFutureAcks() throws Exception {
      Assumptions.assumeTrue(database == Database.JOURNAL); // this test is playing with removing files

      Configuration config = createDefaultNettyConfig();

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1);
      server.start();
      int numberOfMessages = 100;

      String queueName = "TEST" + RandomUtil.randomUUIDString();

      try {
         server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      } catch (Exception ignored) {
      }

      // Send messages, remove them by removing the page folder on the first iteration
      for (int repeat = 0; repeat < 2; repeat++) {
         Wait.waitFor(() -> server.locateQueue(queueName) != null);
         Queue testQueue = server.locateQueue(queueName);
         testQueue.getPagingStore().startPaging();
         assertTrue(testQueue.getPagingStore().isPaging());

         ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
         try (Connection connection = connectionFactory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            for (int i = 0; i < numberOfMessages; i++) {
               logger.debug("Sent {}", i);
               TextMessage message = session.createTextMessage("hello " + i);
               message.setIntProperty("i", i);
               producer.send(message);
            }
         }

         if (repeat == 0) {
            PageSubscriptionImpl subscription = QueueAccessor.getSubscription(testQueue);
            try (PageIterator iterator = subscription.iterator(true)) {
               long txID = server.getStorageManager().generateID();
               int i = 0;
               while (iterator.hasNext()) {
                  PagedReference reference = iterator.next();
                  assertEquals(i++, (int)reference.getMessage().getIntProperty("i"));
                  server.getStorageManager().storeCursorAcknowledgeTransactional(txID, testQueue.getID(), reference.getPosition());
               }
               server.getStorageManager().commit(txID);
            }
            logger.info(">>>> removing page folder and restarting server");
            File pagingFolder = server.getConfiguration().getPagingLocation();
            FileUtil.deleteDirectory(pagingFolder);
            pagingFolder.mkdirs();
            server.stop();
            server = createServer(createDefaultConfig(0, true));
            server.start();
         }
      }

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         connection.start();
         for (int i = 0; i < numberOfMessages; i++) {
            javax.jms.Message message = consumer.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("i"));
         }
      }
   }

   /**
    * Introduce future ACKs hoping the server will clean them up before starting
    * this could happen when users remove paging folders. This is a cleanup task. **/
   @TestTemplate
   public void testCleanupFuturePGCompletes() throws Exception {
      Configuration config = createDefaultNettyConfig();

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1);
      server.start();
      int numberOfMessages = 100;

      String queueName = "TEST" + RandomUtil.randomUUIDString();

      try {
         server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      } catch (Exception ignored) {
      }

      Wait.waitFor(() -> server.locateQueue(queueName) != null);
      Queue testQueue = server.locateQueue(queueName);
      testQueue.getPagingStore().startPaging();
      assertTrue(testQueue.getPagingStore().isPaging());

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < 10; i++) {
            logger.debug("Sent {}", i);
            TextMessage message = session.createTextMessage("hello " + i);
            producer.send(message);
         }
         session.commit();

         connection.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         for (int i = 0; i < 5; i++) {
            assertNotNull(consumer.receive(5000));
         }
         session.commit();
      }

      PageSubscriptionImpl subscription = QueueAccessor.getSubscription(testQueue);
      // this next block is introducing a scenario that could happen during replication failback
      // where cleanup is disabled and then re-established after replication is finished
      {
         subscription.getPagingStore().disableCleanup();
         for (long i = 5; i < 100; i++) {
            PageSubscriptionImpl.PageCursorInfo pageInfo = subscription.getPageInfo(i);
            boolean done = pageInfo.isDone(); // this is to force the calculation and reading from the file.
                                               // this would probably happen its own, but I want to ensure the failing condition here.
            if (logger.isDebugEnabled()) {
               logger.debug("page {}, isDone={}", i, done);
            }
         }
         // this will introduce the pageComplete records, and they will survive since cleanup is actually disabled.
         // which is something we turn on and off during failback.
         subscription.cleanupEntries(false);
      }

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < numberOfMessages; i++) {
            logger.debug("Sent {}", i);
            TextMessage message = session.createTextMessage("hello " + i);
            producer.send(message);
            if (i > 0 && i % 10 == 0) {
               session.commit();
               testQueue.getPagingStore().forceAnotherPage();
            }
         }
         session.commit();
      }

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         connection.start();
         for (int i = 0; i < numberOfMessages + 5; i++) { // + 5 includes original few messages that were sent and acke (10 sent, 5 acked)
            javax.jms.Message message = consumer.receive(5000);
            assertNotNull(message);
         }
      }
   }


   @TestTemplate
   public void testSimpleNoTXSend() throws Exception {
      Configuration config = createDefaultNettyConfig();

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1);
      server.start();

      internalNoTX("CORE");
      internalNoTX("OPENWIRE");
      internalNoTX("AMQP");
   }

   private void internalNoTX(String protocol) throws Exception {
      int numberOfMessages = 20;

      String queueName = "TEST" + RandomUtil.randomUUIDString();

      try {
         server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      } catch (Exception ignored) {
      }

      Wait.waitFor(() -> server.locateQueue(queueName) != null);
      Queue testQueue = server.locateQueue(queueName);

      testQueue.getPagingStore().startPaging();
      assertTrue(testQueue.getPagingStore().isPaging());

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < numberOfMessages; i++) {
            logger.debug("Sent {}", i);
            producer.send(session.createTextMessage("Hello" + i));
         }
      }

      server.stop();
      server = createServer(createDefaultConfig(0, true));
      server.start();

      Queue queue = server.locateQueue(queueName);
      Wait.assertEquals(numberOfMessages, queue::getMessageCount);

      receiveMessages(connectionFactory, queueName, numberOfMessages);
   }

   private void receiveMessages(ConnectionFactory connectionFactory,
                                String queueName,
                                int numberOfMessages) throws JMSException {
      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         for (int i = 0; i < numberOfMessages; i++) {
            if (i % 100 == 0) {
               logger.debug("Received {}", i);
            }
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("Hello" + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());
      }
   }

   private static final class DummyOperationContext implements OperationContext {

      private final CountDownLatch pageUp;
      private final CountDownLatch pageDone;

      private DummyOperationContext(CountDownLatch pageUp, CountDownLatch pageDone) {
         this.pageDone = pageDone;
         this.pageUp = pageUp;
      }

      @Override
      public void onError(int errorCode, String errorMessage) {
      }

      @Override
      public void done() {
      }

      @Override
      public void storeLineUp() {
      }

      @Override
      public boolean waitCompletion(long timeout) throws Exception {
         return false;
      }

      @Override
      public void waitCompletion() throws Exception {

      }

      @Override
      public void replicationLineUp() {

      }

      @Override
      public void replicationDone() {

      }

      @Override
      public void pageSyncLineUp() {
         pageUp.countDown();
      }

      @Override
      public void pageSyncDone() {
         pageDone.countDown();
      }

      @Override
      public void executeOnCompletion(IOCallback runnable) {
         runnable.done();
      }

      @Override
      public void executeOnCompletion(IOCallback runnable, OperationConsistencyLevel consistencyLevel) {
         runnable.done();
      }
   }

}
