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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionCounterImpl;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionCounterImplAccessor;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PageCounterRebuildTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testUnitSize() throws Exception {
      AtomicInteger errors = new AtomicInteger(0);

      StorageManager mockStorage = Mockito.mock(StorageManager.class);

      PageSubscriptionCounterImpl nonPersistentPagingCounter = new PageSubscriptionCounterImpl(mockStorage, -1);

      final int THREADS = 33;
      final int ADD_VALUE = 7;
      final int SIZE_VALUE = 17;
      final int REPEAT = 777;

      ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
      runAfter(executorService::shutdownNow);

      CyclicBarrier startFlag = new CyclicBarrier(THREADS);

      ReusableLatch latch = new ReusableLatch(THREADS);

      for (int j = 0; j < THREADS; j++) {
         executorService.execute(() -> {
            try {
               startFlag.await(10, TimeUnit.SECONDS);
               for (int i = 0; i < REPEAT; i++) {
                  nonPersistentPagingCounter.increment(null, ADD_VALUE, SIZE_VALUE);
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      assertEquals(ADD_VALUE * THREADS * REPEAT, nonPersistentPagingCounter.getValue());
      assertEquals(SIZE_VALUE * THREADS * REPEAT, nonPersistentPagingCounter.getPersistentSize());


      latch.setCount(THREADS);

      for (int j = 0; j < THREADS; j++) {
         executorService.execute(() -> {
            try {
               startFlag.await(10, TimeUnit.SECONDS);
               for (int i = 0; i < REPEAT; i++) {
                  nonPersistentPagingCounter.increment(null, -ADD_VALUE, -SIZE_VALUE);
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      assertEquals(0L, nonPersistentPagingCounter.getValue());
      assertEquals(0L, nonPersistentPagingCounter.getPersistentSize());
      assertEquals(0, errors.get());
   }

   @Test
   public void testResetSubscriptionCounter() throws Exception {
      StorageManager mockStorage = Mockito.mock(StorageManager.class);

      PageSubscriptionCounterImpl nonPersistentPagingCounter = new PageSubscriptionCounterImpl(mockStorage, 33);

      AtomicInteger called = new AtomicInteger(0);

      AtomicLong generate = new AtomicLong(1);

      Mockito.doAnswer((Answer<Long>) invocationOnMock -> generate.incrementAndGet()).when(mockStorage).generateID();

      Mockito.doAnswer((Answer<Void>) invocationOnMock -> {
         called.incrementAndGet();

         return null;
      }).when(mockStorage).commit(Mockito.anyLong());

      PageSubscriptionCounterImplAccessor.reset(nonPersistentPagingCounter);

      assertEquals(1, called.get());
   }

   @Test
   public void testRebuildCounter() throws Exception {
      ActiveMQServer server = createServer(true, true);
      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(100 * 1024).setMaxReadPageMessages(1);
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);
      server.start();

      String queueName = getName();
      String nonConsumedQueueName = getName() + "_nonConsumed";
      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(nonConsumedQueueName).setAddress(queueName).setRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.MULTICAST));

      Queue serverQueue = server.locateQueue(queueName);
      Queue serverNonConsumedQueue = server.locateQueue(nonConsumedQueueName);

      assertNotNull(serverQueue);
      assertNotNull(serverNonConsumedQueue);

      serverQueue.getPagingStore().startPaging();

      final int THREADS = 4;
      final int TX_SEND = 2000;
      final int NON_TXT_SEND = 200;
      final int CONSUME_MESSAGES = 200;
      AtomicInteger errors = new AtomicInteger(0);

      ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
      runAfter(executorService::shutdownNow);

      CyclicBarrier startFlag = new CyclicBarrier(THREADS);

      ReusableLatch latch = new ReusableLatch(THREADS);

      for (int i = 0; i < THREADS; i++) {
         final int threadNumber = i;
         executorService.execute(() -> {
            try {
               startFlag.await(10, TimeUnit.SECONDS);
               ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
               try (Connection connection = factory.createConnection();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    Session txSession = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {

                  logger.info("sending thread {}", threadNumber);

                  javax.jms.Topic jmsQueue = session.createTopic(queueName);
                  MessageProducer producerNonTX = session.createProducer(jmsQueue);
                  MessageProducer producerTX = txSession.createProducer(jmsQueue);

                  for (int message = 0; message < NON_TXT_SEND; message++) {
                     TextMessage txtMessage = session.createTextMessage("hello" + message);
                     txtMessage.setBooleanProperty("first", false);
                     producerNonTX.send(session.createTextMessage("hello" + message));
                  }
                  for (int message = 0; message < TX_SEND; message++) {
                     producerTX.send(session.createTextMessage("helloTX" + message));
                  }
                  txSession.commit();
               }

            } catch (Throwable e) {
               errors.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });
      }

      // this should be fast on the CIs, but if you use a slow disk, it might take a few extra seconds.
      assertTrue(latch.await(1, TimeUnit.MINUTES));

      final int numberOfMessages = TX_SEND * THREADS + NON_TXT_SEND * THREADS;
      Wait.assertEquals(numberOfMessages, serverQueue::getMessageCount);

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName + "::" + queueName));
         connection.start();
         for (int i = 0; i < CONSUME_MESSAGES; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }
      }

      Wait.assertEquals(numberOfMessages - CONSUME_MESSAGES, serverQueue::getMessageCount);
      Wait.assertEquals(numberOfMessages, serverNonConsumedQueue::getMessageCount);

      server.stop();
      server.start();

      serverQueue = server.locateQueue(queueName);
      serverNonConsumedQueue = server.locateQueue(nonConsumedQueueName);

      Wait.assertEquals(numberOfMessages - CONSUME_MESSAGES, serverQueue::getMessageCount);
      Wait.assertEquals(numberOfMessages, serverNonConsumedQueue::getMessageCount);

      serverQueue.getPageSubscription().getCounter().markRebuilding();
      serverNonConsumedQueue.getPageSubscription().getCounter().markRebuilding();

      // if though we are rebuilding, we are still returning based on the last recorded value until processing is finished
      assertEquals(8600, serverQueue.getMessageCount());
      assertEquals(8800, serverNonConsumedQueue.getMessageCount());

      serverQueue.getPageSubscription().getCounter().finishRebuild();

      serverNonConsumedQueue.getPageSubscription().getCounter().finishRebuild();

      assertEquals(0, serverQueue.getMessageCount()); // we artificially made it 0 by faking a rebuild
      assertEquals(0, serverNonConsumedQueue.getMessageCount()); // we artificially made it 0 by faking a rebuild

      server.stop();
      server.start();

      serverQueue = server.locateQueue(queueName);
      serverNonConsumedQueue = server.locateQueue(nonConsumedQueueName);

      // after a rebuild, the counter should be back to where it was
      Wait.assertEquals(numberOfMessages - CONSUME_MESSAGES, serverQueue::getMessageCount);
      Wait.assertEquals(numberOfMessages, serverNonConsumedQueue::getMessageCount);

      server.stop();
      server.start();

      serverQueue = server.locateQueue(queueName);
      serverNonConsumedQueue = server.locateQueue(nonConsumedQueueName);

      assertNotNull(serverQueue);
      assertNotNull(serverNonConsumedQueue);

      Wait.assertEquals(numberOfMessages - CONSUME_MESSAGES, serverQueue::getMessageCount);
      Wait.assertEquals(numberOfMessages, serverNonConsumedQueue::getMessageCount);

      server.stop();
      // restarting the server to issue a rebuild on the counters
      server.start();

      logger.info("Consuming messages");
      factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName + "::" + queueName));
         connection.start();
         for (int i = 0; i < numberOfMessages - CONSUME_MESSAGES; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
            if (i % 100 == 0) {
               logger.info("Received {} messages", i);
            }
         }
         assertNull(consumer.receiveNoWait());
         consumer.close();

         consumer = session.createConsumer(session.createQueue(queueName + "::" + nonConsumedQueueName));
         connection.start();
         for (int i = 0; i < numberOfMessages; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }
         assertNull(consumer.receiveNoWait());
         consumer.close();
      }

      serverQueue = server.locateQueue(queueName);
      serverNonConsumedQueue = server.locateQueue(nonConsumedQueueName);

      Wait.assertEquals(0L, serverQueue::getMessageCount, 1000, 100);
      Wait.assertEquals(0L, serverNonConsumedQueue::getMessageCount, 1000, 100);
   }
}
