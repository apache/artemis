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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RingQueueTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private final SimpleString address = SimpleString.of("RingQueueTestAddress");

   private final SimpleString qName = SimpleString.of("RingQueueTestQ1");

   @Test
   public void testSimple() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(1L));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(1, queue.getRingSize());

      ClientProducer producer = clientSession.createProducer(address);

      for (int i = 0, j = 0; i < 500; i += 2, j++) {
         ClientMessage m0 = createTextMessage(clientSession, "hello" + i);
         producer.send(m0);
         Wait.assertTrue(() -> queue.getMessageCount() == 1);
         ClientMessage m1 = createTextMessage(clientSession, "hello" + (i + 1));
         producer.send(m1);
         int expectedMessagesReplaced = j + 1;
         Wait.assertTrue(() -> queue.getMessagesReplaced() == expectedMessagesReplaced);
         Wait.assertTrue(() -> queue.getMessageCount() == 1);
         ClientConsumer consumer = clientSession.createConsumer(qName);
         ClientMessage message = consumer.receiveImmediate();
         message.acknowledge();
         consumer.close();
         assertEquals("hello" + (i + 1), message.getBodyBuffer().readString());
      }
   }

   @Test
   public void testRollback() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(1L));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);

      assertEquals(1, queue.getRingSize());

      ClientProducer producer = clientSession.createProducer(address);

      ClientMessage m0 = createTextMessage(clientSession, "hello0");
      producer.send(m0);
      Wait.assertTrue(() -> queue.getMessageCount() == 1);

      ClientConsumer consumer = clientSession.createConsumer(qName);

      ClientMessage message = consumer.receiveImmediate();
      assertNotNull(message);
      Wait.assertTrue(() -> queue.getDeliveringCount() == 1);

      message.acknowledge();
      assertEquals("hello0", message.getBodyBuffer().readString());

      ClientMessage m1 = createTextMessage(clientSession, "hello1");
      producer.send(m1);
      Wait.assertTrue(() -> queue.getDeliveringCount() == 2);
      Wait.assertTrue(() -> queue.getMessagesReplaced() == 0);
      Wait.assertTrue(() -> queue.getMessageCount() == 2);

      clientSession.rollback();
      consumer.close();
      Wait.assertTrue(() -> queue.getDeliveringCount() == 0);
      Wait.assertTrue(() -> queue.getMessagesReplaced() == 1);
      Wait.assertTrue(() -> queue.getMessageCount() == 1);

      consumer = clientSession.createConsumer(qName);
      message = consumer.receiveImmediate();
      assertNotNull(message);
      Wait.assertTrue(() -> queue.getDeliveringCount() == 1);

      message.acknowledge();

      clientSession.commit();

      Wait.assertTrue(() -> queue.getMessagesAcknowledged() == 1);
      assertEquals("hello1", message.getBodyBuffer().readString());
   }

   @Test
   public void testConsumerCloseWithDirectDeliver() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(1L));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(1, queue.getRingSize());

      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientProducer producer = clientSession.createProducer(address);

      ClientMessage message = createTextMessage(clientSession, "hello0");
      producer.send(message);
      message = createTextMessage(clientSession, "hello1");
      producer.send(message);
      Wait.assertEquals(2, queue::getMessageCount);
      Wait.assertEquals(2, queue::getDeliveringCount);
      consumer.close();
      Wait.assertEquals(1, queue::getMessageCount);
      Wait.assertEquals(0, queue::getDeliveringCount);
      Wait.assertEquals(1, queue::getMessagesReplaced);
      consumer = clientSession.createConsumer(qName);
      message = consumer.receiveImmediate();
      assertNotNull(message);
      Wait.assertTrue(() -> queue.getDeliveringCount() == 1);
      message.acknowledge();
      clientSession.commit();
      Wait.assertTrue(() -> queue.getMessagesAcknowledged() == 1);
      assertEquals("hello1", message.getBodyBuffer().readString());
      consumer.close();
      Wait.assertTrue(() -> queue.getMessageCount() == 0);
      Wait.assertTrue(() -> queue.getDeliveringCount() == 0);
      Wait.assertTrue(() -> queue.getMessagesReplaced() == 1);
   }

   @Test
   public void testScheduled() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(1L));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(1, queue.getRingSize());

      ClientProducer producer = clientSession.createProducer(address);

      ClientMessage m0 = createTextMessage(clientSession, "hello0");
      long time = System.currentTimeMillis();
      time += 500;
      m0.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m0);
      Wait.assertTrue(() -> queue.getScheduledCount() == 1);
      Wait.assertTrue(() -> ((QueueImpl) queue).getPendingMessageCount() == 0);
      time = System.currentTimeMillis();
      time += 500;
      m0.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m0);
      Wait.assertTrue(() -> queue.getScheduledCount() == 2);
      Wait.assertTrue(() -> ((QueueImpl) queue).getPendingMessageCount() == 0);
      Wait.assertTrue(() -> queue.getMessagesReplaced() == 1);
      Wait.assertTrue(() -> ((QueueImpl) queue).getPendingMessageCount() == 1);
   }

   @Test
   public void testDefaultAddressSetting() throws Exception {
      SimpleString random = RandomUtil.randomUUIDSimpleString();
      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setDefaultRingSize(100));

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      Connection c = cf.createConnection();
      Session s = c.createSession();
      MessageProducer producer = s.createProducer(s.createQueue(address.toString()));
      producer.send(s.createMessage());
      Wait.assertTrue(() -> server.locateQueue(address) != null);
      assertEquals(100, server.locateQueue(address).getRingSize());
      producer.close();
      producer = s.createProducer(s.createQueue(random.toString()));
      producer.send(s.createMessage());
      Wait.assertTrue(() -> server.locateQueue(random) != null);
      assertEquals(ActiveMQDefaultConfiguration.getDefaultRingSize(), server.locateQueue(random).getRingSize());
   }

   @Test
   public void testUpdate() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(-1, queue.getRingSize());

      ClientProducer producer = clientSession.createProducer(address);
      for (int i = 0; i < 100; i++) {
         producer.send(clientSession.createMessage(true));
      }
      Wait.assertTrue(() -> queue.getMessageCount() == 100);

      queue.setRingSize(10);

      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientMessage message;
      for (int j = 0; j < 95; j++) {
         message = consumer.receiveImmediate();
         message.acknowledge();
      }
      consumer.close();
      Wait.assertEquals(5, queue::getMessageCount);

      for (int i = 0; i < 5; i++) {
         producer.send(clientSession.createMessage(true));
      }
      Wait.assertEquals(10, queue::getMessageCount);

      // these sends will be replacing the old values
      for (int i = 0; i < 5; i++) {
         producer.send(clientSession.createMessage(true));
         Wait.assertEquals(10, queue::getMessageCount);
      }

      Wait.assertEquals(5, queue::getMessagesReplaced);
      consumer = clientSession.createConsumer(qName);
      message = consumer.receiveImmediate();
      assertNotNull(message);
      message.acknowledge();
      consumer.close();
      Wait.assertTrue(() -> queue.getMessageCount() == 9);

      queue.setRingSize(5);

      consumer = clientSession.createConsumer(qName);
      for (int j = 0; j < 4; j++) {
         message = consumer.receiveImmediate();
         message.acknowledge();
      }
      consumer.close();
      Wait.assertTrue(() -> queue.getMessageCount() == 5);
      producer.send(clientSession.createMessage(true));
      Wait.assertTrue(() -> queue.getMessagesReplaced() == 6);

      queue.setRingSize(10);

      for (int i = 0; i < 5; i++) {
         producer.send(clientSession.createMessage(true));
      }
      Wait.assertTrue(() -> queue.getMessageCount() == 10);
      producer.send(clientSession.createMessage(true));
      Wait.assertTrue(() -> queue.getMessagesReplaced() == 7);
      Wait.assertTrue(() -> queue.getMessageCount() == 10);
   }

   @Test
   public void testNonDestructive() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(1L).setNonDestructive(true));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(1, queue.getRingSize());

      ClientProducer producer = clientSession.createProducer(address);

      ClientMessage message = createTextMessage(clientSession, "hello" + 0);
      producer.send(message);
      for (int i = 0; i < 5; i++) {
         Wait.assertTrue(() -> queue.getMessageCount() == 1);
         message = createTextMessage(clientSession, "hello" + (i + 1));
         producer.send(message);
         final int finalI = i + 1;
         Wait.assertTrue(() -> queue.getMessagesReplaced() == finalI);
         Wait.assertTrue(() -> queue.getMessageCount() == 1);
         ClientConsumer consumer = clientSession.createConsumer(qName);
         message = consumer.receiveImmediate();
         assertNotNull(message);
         message.acknowledge(); // non-destructive!
         consumer.close();
         assertEquals("hello" + (i + 1), message.getBodyBuffer().readString());
      }
   }

   @Test
   public void testNonDestructiveWithConsumerClose() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(1L).setNonDestructive(true));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(1, queue.getRingSize());

      ClientProducer producer = clientSession.createProducer(address);

      ClientMessage m0 = createTextMessage(clientSession, "hello" + 0);
      producer.send(m0);
      Wait.assertTrue(() -> queue.getMessageCount() == 1);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      Wait.assertTrue(() -> queue.getDeliveringCount() == 1);
      consumer.close();
      Wait.assertTrue(() -> queue.getDeliveringCount() == 0);
      Wait.assertTrue(() -> queue.getMessageCount() == 1);
   }

   @Test
   public void testMultipleConcurrentProducers() throws Exception {
      final long RING_SIZE = 25;
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(address).setRingSize(RING_SIZE));
      clientSession.start();
      final Queue queue = server.locateQueue(qName);
      assertEquals(RING_SIZE, queue.getRingSize());
      final int nThreads = 25;
      final long numberOfMessages = RING_SIZE;

      SomeProducer[] producers = new SomeProducer[nThreads];

      try {
         for (int i = 0; i < nThreads; i++) {
            producers[i] = new SomeProducer(numberOfMessages, nThreads, address);
         }

         for (int i = 0; i < nThreads; i++) {
            producers[i].start();
         }

         for (SomeProducer producer : producers) {
            producer.join();
            assertEquals(0, producer.errors.get());
         }
      } catch (Exception e) {
         e.printStackTrace();
         fail(e.getMessage());
      }

      Wait.assertTrue("message count should be " + RING_SIZE + " but it's actually " + queue.getMessageCount(), () -> queue.getMessageCount() == RING_SIZE);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultNettyConfig(), true));
      // start the server
      server.start();
   }

   class SomeProducer extends Thread {

      final ClientSessionFactory factory;
      final ServerLocator locator;
      final ClientSession prodSession;
      public final AtomicInteger errors = new AtomicInteger(0);
      final long numberOfMessages;
      final int nThreads;
      final SimpleString address;

      SomeProducer(long numberOfMessages, int nThreads, SimpleString address) throws Exception {
         locator = createNettyNonHALocator();
         factory = locator.createSessionFactory();
         prodSession = factory.createSession(true, false);
         this.numberOfMessages = numberOfMessages;
         this.nThreads = nThreads;
         this.address = address;
      }

      @Override
      public void run() {
         try {
            ClientProducer producer = prodSession.createProducer(address);
            for (int i = 0; i < numberOfMessages; i++) {
               ClientMessage message = prodSession.createMessage(true);
               message.putIntProperty("prodNR", i % nThreads);
               producer.send(message);
            }

         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         } finally {
            try {
               prodSession.close();
               locator.close();
            } catch (Throwable ignored) {
               ignored.printStackTrace();
            }
         }
      }
   }
}
