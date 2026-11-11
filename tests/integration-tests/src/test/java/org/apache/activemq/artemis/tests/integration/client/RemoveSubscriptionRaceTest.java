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

package org.apache.activemq.artemis.tests.integration.client;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RemoveSubscriptionRaceTest extends ActiveMQTestBase {


   private static final String SUB_NAME = "SubscriptionStressTest";

   ActiveMQServer server;

   @BeforeEach
   public void setServer() throws Exception {
   }

   @Test
   public void testCreateSubscriptionCoreNoFiles() throws Exception {
      internalTest("core", false, 5, 1000, false);
   }

   @Test
   public void testCreateSubscriptionAMQPNoFiles() throws Exception {
      internalTest("amqp", false, 5, 1000, false);
   }

   @Test
   public void testCreateSubscriptionCoreRealFiles() throws Exception {
      internalTest("core", true, 2, 200, false);
   }

   @Test
   public void testCreateSubscriptionAMQPRealFiles() throws Exception {
      internalTest("amqp", true, 2, 200, false);
   }

   @Test
   public void testCreateSubscriptionCoreRealFilesDurable() throws Exception {
      internalTest("core", true, 2, 200, true);
   }

   @Test
   public void testCreateSubscriptionAMQPRealFilesDurable() throws Exception {
      internalTest("amqp", true, 2, 200, true);
   }

   public void internalTest(String protocol, boolean realFiles, int threads, int numberOfMessages, boolean durableSub) throws Exception {
      server = createServer(realFiles, true);
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(SUB_NAME).addRoutingType(RoutingType.MULTICAST));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("Sub_1").setAddress(SUB_NAME).setRoutingType(RoutingType.MULTICAST));
      server.start();

      CountDownLatch runningLatch = new CountDownLatch(threads);
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicInteger errors = new AtomicInteger(0);

      ExecutorService executorService = Executors.newFixedThreadPool(Math.max(1, threads)); // I'm using the max here, because I may set threads=0 while hacking the test

      runAfter(() -> executorService.shutdownNow());

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      CyclicBarrier flagStart = new CyclicBarrier(threads + 1);

      for (int i = 0; i < threads; i++) {
         final int threadNumber = i;
         executorService.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);
               for (int n = 0; n < numberOfMessages && running.get(); n++) {
                  Connection connection = factory.createConnection();
                  if (durableSub) {
                     connection.setClientID("t" + threadNumber);
                  }
                  connection.start();
                  Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  Topic topic = session.createTopic(SUB_NAME);
                  MessageConsumer consumer;
                  if (durableSub) {
                     consumer = session.createDurableSubscriber(topic, "t" + threadNumber);
                  } else {
                     consumer = session.createConsumer(topic);
                  }
                  Message message = consumer.receiveNoWait();
                  if (message != null) {
                     message.acknowledge();
                  }
                  consumer.close();
                  if (durableSub) {
                     session.unsubscribe("t" + threadNumber);
                  }
                  connection.close();
               }
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();

            } finally {
               runningLatch.countDown();
            }
         });
      }

      Connection connection = factory.createConnection();
      connection.start();

      Queue queue = server.locateQueue("Sub_1");
      assertNotNull(queue);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic(SUB_NAME);
      MessageProducer producer = session.createProducer(topic);
      MessageConsumer consumer = session.createConsumer(session.createQueue(SUB_NAME + "::" + "Sub_1"));

      flagStart.await(10, TimeUnit.SECONDS);
      try {
         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(session.createTextMessage("a"));
            assertNotNull(consumer.receive(5000));
         }
         connection.close();
      } finally {
         running.set(false);
         assertTrue(runningLatch.await(10, TimeUnit.SECONDS));
      }

      Wait.assertEquals(0, this::countAddMessage, 5000, 100);

      Wait.assertEquals(0L, queue.getPagingStore()::getAddressSize, 2000, 100);

      assertEquals(0, errors.get());
   }

   int countAddMessage() throws Exception {
      StorageManager manager = server.getStorageManager();

      if (manager instanceof JournalStorageManager journalStorageManager) {
         journalStorageManager.getMessageJournal().scheduleCompactAndBlock(5_000);
      } else {
         return 0;
      }

      Map<Integer, AtomicInteger> journalCounts = countJournal(server.getConfiguration());
      AtomicInteger value = journalCounts.get((int) JournalRecordIds.ADD_MESSAGE_PROTOCOL);
      if (value == null) {
         return 0;
      }
      return value.get();
   }
}
