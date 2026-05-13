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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PagingCounterTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private ServerLocator sl;

   private AssertionLoggerHandler loggerHandler;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = newActiveMQServer();
      server.start();
      sl = createInVMNonHALocator();
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
   public void testCounter() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {
         server.addAddressInfo(new AddressInfo(SimpleString.of("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(QueueConfiguration.of("A1").setRoutingType(RoutingType.ANYCAST));

         PageSubscriptionCounter counter = locateCounter(queue);

         StorageManager storage = server.getStorageManager();

         Transaction tx = new TransactionImpl(server.getStorageManager());

         counter.increment(tx, 1, 1000);

         Wait.assertEquals(0, counter::getValue);
         Wait.assertEquals(0, counter::getPersistentSize);

         tx.commit();

         Wait.assertEquals(1, counter::getValue);
         Wait.assertEquals(1000, counter::getPersistentSize);
      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testMultiThreadUpdates() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();
      AtomicInteger errors = new AtomicInteger(0);

      try {
         server.addAddressInfo(new AddressInfo(SimpleString.of("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

         final PageSubscriptionCounter counter = locateCounter(queue);

         final int THREADS = 10;

         final CyclicBarrier flagStart = new CyclicBarrier(THREADS);
         final CountDownLatch done = new CountDownLatch(THREADS);

         final int BUMPS = 2000;

         assertEquals(0, counter.getValue());

         ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
         runAfter(executorService::shutdownNow);

         for (int i = 0; i < THREADS; i++) {
            executorService.execute(() -> {
               try {
                  flagStart.await(10, TimeUnit.SECONDS);
                  for (int repeat = 0; repeat < BUMPS; repeat++) {
                     counter.increment(null, 2, 1L);
                     Transaction tx = new TransactionImpl(server.getStorageManager());
                     counter.increment(tx, 1, 1L);
                     tx.commit();
                     counter.increment(null, -1, -1L);
                     tx = new TransactionImpl(server.getStorageManager());
                     counter.increment(tx, -1, -1L);
                     tx.commit();
                  }
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  done.countDown();
               }
            });
         }

         // it should take a couple seconds only
         done.await(1, TimeUnit.MINUTES);

         Wait.assertEquals((long)(BUMPS * THREADS), counter::getValue, 5000, 100);

         server.stop();

         server.setRebuildCounters(false);

         server.start();

         queue = server.locateQueue("A1");

         final PageSubscriptionCounter counterAfterRestart = locateCounter(queue);
         Wait.assertEquals((long)(BUMPS * THREADS), counterAfterRestart::getValue, 5000, 100);

      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testMultiThreadCounter() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {
         server.addAddressInfo(new AddressInfo(SimpleString.of("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

         final PageSubscriptionCounter counter = locateCounter(queue);

         final int THREADS = 10;

         final CyclicBarrier flagStart = new CyclicBarrier(THREADS);
         final CountDownLatch done = new CountDownLatch(THREADS);

         final int BUMPS = 2000;

         assertEquals(0, counter.getValue());

         ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
         runAfter(executorService::shutdownNow);

         for (int i = 0; i < THREADS; i++) {
            executorService.execute(() -> {
               try {
                  flagStart.await(10, TimeUnit.SECONDS);
                  for (int repeat = 0; repeat < BUMPS; repeat++) {
                     counter.increment(null, 1, 1L);
                     Transaction tx = new TransactionImpl(server.getStorageManager());
                     counter.increment(tx, 1, 1L);
                     tx.commit();
                  }
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               } finally {
                  done.countDown();
               }
            });
         }

         // it should take a couple seconds only
         done.await(1, TimeUnit.MINUTES);

         Wait.assertEquals((long)(BUMPS * 2 * THREADS), counter::getValue, 5000, 100);

         server.stop();

         server.setRebuildCounters(false);

         server.start();

         queue = server.locateQueue("A1");

         final PageSubscriptionCounter counterAfterRestart = locateCounter(queue);
         Wait.assertEquals((long)(BUMPS * 2 * THREADS), counterAfterRestart::getValue, 5000, 100);
         assertEquals(BUMPS * 2 * THREADS, counterAfterRestart.getValue());

      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testCleanupCounter() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {
         server.addAddressInfo(new AddressInfo(SimpleString.of("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

         PageSubscriptionCounter counter = locateCounter(queue);

         StorageManager storage = server.getStorageManager();

         Transaction tx = new TransactionImpl(server.getStorageManager());

         for (int i = 0; i < 2100; i++) {

            counter.increment(tx, 1, 1000);

            if (i % 200 == 0) {
               tx.commit();

               storage.waitOnOperations();

               Wait.assertEquals(i + 1, counter::getValue);
               Wait.assertEquals((i + 1) * 1000, counter::getPersistentSize);

               tx = new TransactionImpl(server.getStorageManager());
            }
         }

         tx.commit();

         Wait.assertEquals(2100, counter::getValue);
         Wait.assertEquals(2100 * 1000, counter::getPersistentSize);

         server.stop();

         server = newActiveMQServer();
         server.setRebuildCounters(false);

         server.start();

         queue = server.locateQueue(SimpleString.of("A1"));

         assertNotNull(queue);

         counter = locateCounter(queue);

         assertEquals(2100, counter.getValue());
         assertEquals(2100 * 1000, counter.getPersistentSize());

         server.getPagingManager().rebuildCounters(null);

         // it should be zero after rebuild, since no actual messages were sent
         Wait.assertEquals(0, counter::getValue);

      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testCleanupCounterNonPersistent() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {

         server.addAddressInfo(new AddressInfo(SimpleString.of("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

         PageSubscriptionCounter counter = locateCounter(queue);

         StorageManager storage = server.getStorageManager();

         Transaction tx = new TransactionImpl(server.getStorageManager());

         for (int i = 0; i < 2100; i++) {

            counter.increment(tx, 1, 1000);

            if (i % 200 == 0) {
               tx.commit();

               storage.waitOnOperations();

               assertEquals(i + 1, counter.getValue());
               assertEquals((i + 1) * 1000, counter.getPersistentSize());

               tx = new TransactionImpl(server.getStorageManager());
            }
         }

         tx.commit();

         storage.waitOnOperations();

         assertEquals(2100, counter.getValue());
         assertEquals(2100 * 1000, counter.getPersistentSize());

         server.stop();

         server = newActiveMQServer();

         server.start();

         queue = server.locateQueue(SimpleString.of("A1"));

         assertNotNull(queue);

         counter = locateCounter(queue);

         assertEquals(0, counter.getValue());
         assertEquals(0, counter.getPersistentSize());

      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testRestartCounter() throws Exception {
      server.addAddressInfo(new AddressInfo(SimpleString.of("A1"), RoutingType.ANYCAST));
      Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

      PageSubscriptionCounter counter = locateCounter(queue);

      StorageManager storage = server.getStorageManager();

      Transaction tx = new TransactionImpl(server.getStorageManager());

      counter.increment(tx, 1, 1000);

      assertEquals(0, counter.getValue());
      assertEquals(0, counter.getPersistentSize());

      tx.commit();

      Wait.assertEquals(1, counter::getValue);
      Wait.assertEquals(1000, counter::getPersistentSize);

      sl.close();

      server.stop();


      server = newActiveMQServer();
      server.setRebuildCounters(false);

      server.start();

      queue = server.locateQueue(SimpleString.of("A1"));

      assertNotNull(queue);

      PageSubscriptionCounter counterAfterRestart = locateCounter(queue);

      Wait.assertEquals(1, counterAfterRestart::getValue);
      Wait.assertEquals(1000, counterAfterRestart::getPersistentSize);

      counterAfterRestart.markRebuilding();

      // should be using a previously added value while rebuilding
      Wait.assertEquals(1, counterAfterRestart::getValue);

      tx = new TransactionImpl(server.getStorageManager());

      counterAfterRestart.increment(tx, 10, 10_000);
      tx.commit();

      Wait.assertEquals(11, counterAfterRestart::getValue);
      Wait.assertEquals(11_000, counterAfterRestart::getPersistentSize);
      counterAfterRestart.finishRebuild();

      server.getPagingManager().rebuildCounters(null);

      Wait.assertEquals(0, counterAfterRestart::getValue);
      Wait.assertEquals(0, counterAfterRestart::getPersistentSize);

   }

   private PageSubscriptionCounter locateCounter(Queue queue) throws Exception {
      PageSubscription subscription = server.getPagingManager().getPageStore(SimpleString.of("A1")).getCursorProvider().getSubscription(queue.getID());

      PageSubscriptionCounter counter = subscription.getCounter();
      return counter;
   }

   @Test
   public void testCommitCounter() throws Exception {
      Xid xid = newXID();

      Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

      PageSubscriptionCounter counter = locateCounter(queue);

      StorageManager storage = server.getStorageManager();

      Transaction tx = new TransactionImpl(xid, server.getStorageManager(), 300);

      for (int i = 0; i < 2000; i++) {
         counter.increment(tx, 1, 1000);
      }

      assertEquals(0, counter.getValue());

      tx.commit();

      storage.waitOnOperations();

      assertEquals(2000, counter.getValue());

      server.stop();

      server = newActiveMQServer();

      server.setRebuildCounters(false);

      server.start();

      queue = server.locateQueue(SimpleString.of("A1"));

      assertNotNull(queue);

      counter = locateCounter(queue);

      Wait.assertEquals(2000, counter::getValue);

   }


   @Test
   public void testSendNoRebuild() throws Exception {
      Queue queue = server.createQueue(QueueConfiguration.of(SimpleString.of("A1")).setRoutingType(RoutingType.ANYCAST));

      queue.getPagingStore().startPaging();

      PageSubscriptionCounter counter = locateCounter(queue);

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue("A1"));
         for (int i = 0; i < 3000; i++) {
            producer.send(session.createTextMessage("i" + i));
         }
         session.commit();
      }

      server.stop();

      server = newActiveMQServer();

      server.setRebuildCounters(false);

      server.start();

      queue = server.locateQueue(SimpleString.of("A1"));

      assertNotNull(queue);

      counter = locateCounter(queue);

      logger.debug("Counter:: {}", queue.getMessageCount());

      Wait.assertEquals(3000, counter::getValue);
      Wait.assertEquals(3000L, queue::getMessageCount, 1000, 100);
   }

   private ActiveMQServer newActiveMQServer() throws Exception {

      OperationContextImpl.clearContext();

      ActiveMQServer server = super.createServer(true, true);

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024).setMaxReadPageMessages(10);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }


}
