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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedeliveryConsumerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   final SimpleString ADDRESS = SimpleString.of("address");

   ClientSessionFactory factory;

   private ServerLocator locator;



   @Test
   public void testRedeliveryMessageStrict() throws Exception {
      testDedeliveryMessageOnPersistent(true);
   }

   @Test
   public void testRedeliveryMessageSimpleCancel() throws Exception {
      testDedeliveryMessageOnPersistent(false);
   }

   @Test
   public void testDeliveryNonPersistent() throws Exception {
      testDelivery(false);
   }

   @Test
   public void testDeliveryPersistent() throws Exception {
      testDelivery(true);
   }

   public void testDelivery(final boolean persistent) throws Exception {
      setUp(true);
      ClientSession session = factory.createSession(false, false, false);
      ClientProducer prod = session.createProducer(ADDRESS);

      for (int i = 0; i < 10; i++) {
         prod.send(createTextMessage(session, Integer.toString(i), persistent));
      }

      session.commit();
      session.close();

      session = factory.createSession(null, null, false, true, true, true, 0);

      session.start();
      for (int loopAck = 0; loopAck < 5; loopAck++) {
         ClientConsumer browser = session.createConsumer(ADDRESS, null, true);
         for (int i = 0; i < 10; i++) {
            ClientMessage msg = browser.receive(1000);
            assertNotNull(msg, "element i=" + i + " loopAck = " + loopAck + " was expected");
            msg.acknowledge();
            assertEquals(Integer.toString(i), getTextMessage(msg));

            // We don't change the deliveryCounter on Browser, so this should be always 0
            assertEquals(0, msg.getDeliveryCount());
         }

         session.commit();
         browser.close();
      }

      session.close();

      session = factory.createSession(false, false, false);
      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int loopAck = 0; loopAck < 5; loopAck++) {
         for (int i = 0; i < 10; i++) {
            ClientMessage msg = consumer.receive(1000);
            assertNotNull(msg);
            assertEquals(Integer.toString(i), getTextMessage(msg));

            // No ACK done, so deliveryCount should be always = 1
            assertEquals(1, msg.getDeliveryCount());
         }
         session.rollback();
      }

      if (persistent) {
         session.close();
         server.stop();
         server.start();
         factory = createSessionFactory(locator);
         session = factory.createSession(false, false, false);
         session.start();
         consumer = session.createConsumer(ADDRESS);
      }

      for (int loopAck = 1; loopAck <= 5; loopAck++) {
         for (int i = 0; i < 10; i++) {
            ClientMessage msg = consumer.receive(1000);
            assertNotNull(msg);
            msg.acknowledge();
            assertEquals(Integer.toString(i), getTextMessage(msg));
            assertEquals(loopAck, msg.getDeliveryCount());
         }
         if (loopAck < 5) {
            if (persistent) {
               session.close();
               server.stop();
               server.start();
               factory = createSessionFactory(locator);
               session = factory.createSession(false, false, false);
               session.start();
               consumer = session.createConsumer(ADDRESS);
            } else {
               session.rollback();
            }
         }
      }

      session.close();
   }

   protected void testDedeliveryMessageOnPersistent(final boolean strictUpdate) throws Exception {
      setUp(strictUpdate);
      ClientSession session = factory.createSession(false, false, false);

      logger.debug("created");

      ClientProducer prod = session.createProducer(ADDRESS);
      prod.send(createTextMessage(session, "Hello"));
      session.commit();
      session.close();

      session = factory.createSession(false, false, false);
      session.start();
      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage msg = consumer.receive(1000);
      assertEquals(1, msg.getDeliveryCount());
      session.stop();

      // if strictUpdate == true, this will simulate a crash, where the server is stopped without closing/rolling back
      // the session, but the delivery count still persisted, so the final delivery count is 2 too.
      if (!strictUpdate) {
         // If non Strict, at least rollback/cancel should still update the delivery-counts
         session.rollback(true);

         session.close();
      }

      server.stop();

      // once the server is stopped, we close the session
      // to clean up its client resources
      session.close();

      server.start();

      factory = createSessionFactory(locator);

      session = factory.createSession(false, true, false);
      session.start();
      consumer = session.createConsumer(ADDRESS);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      assertEquals(2, msg.getDeliveryCount());
      session.close();
   }

   @Test
   public void testInfiniteDedeliveryMessageOnPersistent() throws Exception {
      internaltestInfiniteDedeliveryMessageOnPersistent(false);
   }

   private void internaltestInfiniteDedeliveryMessageOnPersistent(final boolean strict) throws Exception {
      setUp(strict);
      ClientSession session = factory.createSession(false, false, false);

      logger.debug("created");

      ClientProducer prod = session.createProducer(ADDRESS);
      prod.send(createTextMessage(session, "Hello"));
      session.commit();
      session.close();

      int expectedCount = 1;
      for (int i = 0; i < 700; i++) {
         session = factory.createSession(false, false, false);
         session.start();
         ClientConsumer consumer = session.createConsumer(ADDRESS);
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         assertEquals(expectedCount, msg.getDeliveryCount());

         if (i % 100 == 0) {
            expectedCount++;
            msg.acknowledge();
            session.rollback();
         }
         session.close();
      }

      factory.close();
      server.stop();

      setUp(false);

      for (int i = 0; i < 700; i++) {
         session = factory.createSession(false, false, false);
         session.start();
         ClientConsumer consumer = session.createConsumer(ADDRESS);
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         assertEquals(expectedCount, msg.getDeliveryCount());
         session.close();
      }

      server.stop();

      JournalImpl journal = new JournalImpl(server.getConfiguration().getJournalFileSize(), 2, 2, 0, 0, new NIOSequentialFileFactory(server.getConfiguration().getJournalLocation(), 1), "activemq-data", "amq", 1);

      final AtomicInteger updates = new AtomicInteger();

      journal.start();
      journal.load(new LoaderCallback() {

         @Override
         public void failedTransaction(long transactionID, List<RecordInfo> records, List<RecordInfo> recordsToDelete) {
         }

         @Override
         public void updateRecord(RecordInfo info) {
            if (info.userRecordType == JournalRecordIds.UPDATE_DELIVERY_COUNT) {
               updates.incrementAndGet();
            }
         }

         @Override
         public void deleteRecord(long id) {
         }

         @Override
         public void addRecord(RecordInfo info) {
         }

         @Override
         public void addPreparedTransaction(PreparedTransactionInfo preparedTransaction) {
         }
      });

      journal.stop();

      assertEquals(7, updates.get());

   }

   @Test
   public void testRedeliveryCollisionAvoidance() throws Exception {
      setUp(false);
      int numberOfThreads = 10;
      long redeliveryDelay = 1000;
      server.getAddressSettingsRepository().getMatch(ADDRESS.toString()).setRedeliveryDelay(redeliveryDelay).setRedeliveryCollisionAvoidanceFactor(0.5);

      ClientSession session = factory.createSession(false, false, false);
      ClientProducer prod = session.createProducer(ADDRESS);
      for (int i = 0; i < numberOfThreads; i++) {
         prod.send(createTextMessage(session, "Hello" + i));
      }
      session.commit();
      session.close();

      final CountDownLatch aligned = new CountDownLatch(numberOfThreads);
      final CountDownLatch startRollback = new CountDownLatch(1);

      class ConsumerThread extends Thread {

         ConsumerThread(int i) {
            super("RedeliveryCollisionAvoidance::" + i);
         }

         long delay = 0;
         int errors = 0;

         @Override
         public void run() {
            try (ServerLocator locator = createInVMNonHALocator()) {
               locator.setConsumerWindowSize(0);
               ClientSessionFactory factory = locator.createSessionFactory();
               ClientSession session = factory.createSession(false, false, false);
               session.start();
               ClientConsumer consumer = session.createConsumer(ADDRESS);
               ClientMessage msg = consumer.receive(5000);
               assertNotNull(msg);
               msg.acknowledge();
               aligned.countDown();
               startRollback.await();
               session.rollback();
               long start = System.currentTimeMillis();
               msg = consumer.receive(5000);
               delay = System.currentTimeMillis() - start;
               assertNotNull(msg);
               msg.acknowledge();
               session.commit();
            } catch (Exception e) {
               e.printStackTrace();
               errors++;
            }
         }
      }

      ConsumerThread[] threads = new ConsumerThread[numberOfThreads];

      for (int i = 0; i < numberOfThreads; i++) {
         threads[i] = new ConsumerThread(i);
         threads[i].start();
      }

      aligned.await();
      startRollback.countDown();

      try {
         for (ConsumerThread t : threads) {
            t.join(60000);
            assertFalse(t.isAlive());
            assertEquals(0, t.errors, "There are Errors on the test thread");
         }
      } finally {
         for (ConsumerThread t : threads) {
            if (t.isAlive()) {
               t.interrupt();
            }
            t.join(1000);
         }
      }

      long maxDelay = 0;
      long minDelay = Long.MAX_VALUE;

      for (ConsumerThread t : threads) {
         if (t.delay < minDelay) {
            minDelay = t.delay;
         }
         if (t.delay > maxDelay) {
            maxDelay = t.delay;
         }
      }

      // make sure the difference between the minimum redelivery delay and the maximum redelivery delay is larger that the expected nominal variance
      assertTrue((maxDelay - minDelay) > (redeliveryDelay * .05));

      factory.close();
   }



   private void setUp(final boolean persistDeliveryCountBeforeDelivery) throws Exception {
      Configuration config = createDefaultInVMConfig().setPersistDeliveryCountBeforeDelivery(persistDeliveryCountBeforeDelivery);

      server = createServer(true, config);

      server.start();
      locator = createInVMNonHALocator();
      factory = createSessionFactory(locator);

      ClientSession session = addClientSession(factory.createSession(false, false, false));
      try {
         session.createQueue(QueueConfiguration.of(ADDRESS));
      } catch (ActiveMQException expected) {
         // in case of restart
      }

      session.close();
   }
}
