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

import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_DAY;
import static org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_MINUTE;
import static org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SlowConsumerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private int threshold = 10;
   private long checkPeriod = 1;
   private boolean isNetty = true;

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   private ServerLocator locator;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(true, isNetty);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(checkPeriod);
      addressSettings.setSlowConsumerThreshold(threshold);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_SECOND);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setMaxSizeBytes(10 * 1024);
      addressSettings.setPageSizeBytes(1024);

      server.start();

      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);

      server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST)).getPageSubscription().getPagingStore().startPaging();

      locator = createFactory(isNetty);
   }

   @Test
   public void testSlowConsumerWithSmallThreadPool() throws Exception {
      final int MESSAGE_COUNT = 2;
      CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
      server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));
      /*
       * Even though the threadPoolMaxSize is 1 the client shouldn't stall on flow control due to the independent flow
       * control thread pool.
       */
      ServerLocator locator = createInVMNonHALocator()
         .setConsumerWindowSize(0)
         .setUseGlobalPools(false)
         .setThreadPoolMaxSize(1);

      ClientSessionFactory cf = createSessionFactory(locator);
      try (ClientSession session = cf.createSession(true, true);
           ClientProducer producer = session.createProducer(getName())) {
         for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(session.createMessage(true));
         }
      }
      try (ClientSession session = cf.createSession(true, true);
           ClientConsumer consumer = session.createConsumer(getName())) {
         consumer.setMessageHandler((m) -> {
            latch.countDown();
            try {
               m.acknowledge();
            } catch (ActiveMQException e) {
               throw new RuntimeException(e);
            }
         });
         session.start();
         assertTrue(latch.await(1, TimeUnit.SECONDS), "All messages should be received within the timeout");
      }
      assertEquals(0, server.locateQueue(getName()).getMessageCount());
   }

   @Test
   public void testSlowConsumerKilled() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 25;

      for (int i = 0; i < numMessages; i++) {
         producer.send(createTextMessage(session, "m" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      Thread.sleep(3000);

      try {
         consumer.receiveImmediate();
         fail();
      } catch (ActiveMQObjectClosedException e) {
         assertEquals(ActiveMQExceptionType.OBJECT_CLOSED, e.getType());
      }
   }

   @Test
   public void testSlowConsumerKilledAfterBurst() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 3 * threshold;

      for (int i = 0; i < numMessages; i++) {
         producer.send(createTextMessage(session, "m" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      for (int i = 0; i < threshold; i++) {
         consumer.receiveImmediate().individualAcknowledge();
      }

      Thread.sleep(3 * checkPeriod * 1000);

      try {
         consumer.receiveImmediate();
         fail();
      } catch (ActiveMQObjectClosedException e) {
         assertEquals(ActiveMQExceptionType.OBJECT_CLOSED, e.getType());
      }
   }

   @Test
   public void testSlowConsumerSparedAfterBurst() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 3 * threshold + 1;

      for (int i = 0; i < numMessages; i++) {
         producer.send(createTextMessage(session, "m" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      Queue queue = server.locateQueue(QUEUE);
      Wait.assertTrue(() -> queue.getDeliveringCount() >= 3 * threshold);

      for (int i = 0; i < 3 * threshold; i++) {
         consumer.receiveImmediate().individualAcknowledge();
      }

      Thread.sleep(3 * checkPeriod * 1000);

      assertNotNull(consumer.receiveImmediate());
   }

   @Test
   public void testSlowConsumerNotification() throws Exception {

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThreshold(10);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.NOTIFY);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);


      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 25;

      for (int i = 0; i < numMessages; i++) {
         producer.send(createTextMessage(session, "m" + i));
      }

      SimpleString notifQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(notifQueue).setAddress(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress()).setDurable(false));

      ClientConsumer notifConsumer = session.createConsumer(notifQueue.toString(), ManagementHelper.HDR_NOTIFICATION_TYPE + "='" + CoreNotificationType.CONSUMER_SLOW + "'");

      final CountDownLatch notifLatch = new CountDownLatch(1);

      notifConsumer.setMessageHandler(message -> {
         assertEquals(CoreNotificationType.CONSUMER_SLOW.toString(), message.getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
         assertEquals(QUEUE.toString(), message.getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
         assertEquals(Integer.valueOf(1), message.getIntProperty(ManagementHelper.HDR_CONSUMER_COUNT));
         if (isNetty) {
            assertTrue(message.getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS).toString().startsWith("127.0.0.1"));
         } else {
            assertEquals(SimpleString.of("invm:0"), message.getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS));
         }
         assertNotNull(message.getSimpleStringProperty(ManagementHelper.HDR_CONNECTION_NAME));
         assertNotNull(message.getSimpleStringProperty(ManagementHelper.HDR_CONSUMER_NAME));
         assertNotNull(message.getSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME));
         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            e.printStackTrace();
         }
         notifLatch.countDown();
      });

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      assertTrue(notifLatch.await(15, TimeUnit.SECONDS));
   }

   @Test
   public void testSlowConsumerSpared() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(true, true));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 5;

      for (int i = 0; i < numMessages; i++) {
         producer.send(createTextMessage(session, "m" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      Thread.sleep(3000);

      for (int i = 0; i < numMessages; i++) {
         assertNotNull(consumer.receive(500));
      }
   }

   @Test
   public void testFastThenSlowConsumerSpared() throws Exception {
      locator.setAckBatchSize(0);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(true, true));

      final ClientSession producerSession = addClientSession(sf.createSession(true, true));

      final ClientProducer producer = addClientProducer(producerSession.createProducer(QUEUE));

      final AtomicLong messagesProduced = new AtomicLong(0);

      Thread t = new Thread(() -> {
         long start = System.currentTimeMillis();
         ClientMessage m = createTextMessage(producerSession, "m", true);

         // send messages as fast as possible for 3 seconds
         while (System.currentTimeMillis() < (start + 3000)) {
            try {
               producer.send(m);
               messagesProduced.incrementAndGet();
            } catch (ActiveMQException e) {
               e.printStackTrace();
               return;
            }
         }

         start = System.currentTimeMillis();

         // send 1 msg/second for 10 seconds
         while (System.currentTimeMillis() < (start + 10000)) {
            try {
               producer.send(m);
               messagesProduced.incrementAndGet();
               Thread.sleep(1000);
            } catch (Exception e) {
               e.printStackTrace();
               return;
            }
         }
      });

      t.start();

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      ClientMessage m = null;
      long messagesConsumed = 0;

      do {
         m = consumer.receive(1500);
         if (m != null) {
            m.acknowledge();
            messagesConsumed++;
         }
      }
      while (m != null);

      assertEquals(messagesProduced.longValue(), messagesConsumed);
   }

   @Test
   public void testSlowWildcardConsumer() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThreshold(10);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));
      session.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(addressAB);
      ClientProducer producer2 = session.createProducer(addressAC);

      final int numMessages = 20;

      for (int i = 0; i < numMessages; i++) {
         producer.send(createTextMessage(session, "m1" + i));
         producer2.send(createTextMessage(session, "m2" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(queueName));
      session.start();

      Thread.sleep(3000);

      try {
         consumer.receiveImmediate();
         fail();
      } catch (ActiveMQObjectClosedException e) {
         assertEquals(ActiveMQExceptionType.OBJECT_CLOSED, e.getType());
      }
   }


   @Test
   public void testOneMinuteKilledInVM() throws Exception {
      testMinuteKilled(false);
   }

   @Test
   public void testOneMinuteKilled() throws Exception {
      testMinuteKilled(true);
   }

   private void testMinuteKilled(boolean netty) throws Exception {
      locator.close();
      locator = createFactory(netty);
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_MINUTE);
      addressSettings.setSlowConsumerThreshold(60);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);


      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      int messages = 200;

      for (int i = 0; i < messages; i++) {
         producer.send(session.createMessage(true));
      }
      session.commit();


      ConcurrentHashSet<ClientMessage> receivedMessages = new ConcurrentHashSet<>();
      FixedRateConsumer consumer = new FixedRateConsumer(40, MESSAGES_PER_MINUTE, receivedMessages, sf, QUEUE, 0);
      consumer.start();

      Queue queue = server.locateQueue(QUEUE);

      Wait.assertEquals(1, queue::getConsumerCount);
      try {
         Wait.assertEquals(0, queue::getConsumerCount);
      } finally {
         consumer.stopRunning();
      }

   }


   @Test
   public void testDaysKilled() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_DAY);
      addressSettings.setSlowConsumerThreshold(TimeUnit.DAYS.toSeconds(1)); // one message per second
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);


      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      int messages = 200;

      for (int i = 0; i < messages; i++) {
         producer.send(session.createMessage(true));
      }
      session.commit();


      ConcurrentHashSet<ClientMessage> receivedMessages = new ConcurrentHashSet<>();
      FixedRateConsumer consumer = new FixedRateConsumer(30, MESSAGES_PER_MINUTE, receivedMessages, sf, QUEUE, 0);
      consumer.start();

      Queue queue = server.locateQueue(QUEUE);

      Wait.assertEquals(1, queue::getConsumerCount);
      try {
         Wait.assertEquals(0, queue::getConsumerCount);
      } finally {
         consumer.stopRunning();
      }

   }


   @Test
   public void testDaysKilledPaging() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_DAY);
      addressSettings.setSlowConsumerThreshold(TimeUnit.DAYS.toSeconds(1)); // one message per second
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setMaxSizeBytes(10 * 1024);
      addressSettings.setPageSizeBytes(1024);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);


      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      int messages = 200;

      Queue queue = server.locateQueue(QUEUE);

      queue.getPagingStore().startPaging();


      assertTrue(queue.getPagingStore().isPaging());

      for (int i = 0; i < messages; i++) {
         producer.send(session.createMessage(true));
      }
      session.commit();


      ConcurrentHashSet<ClientMessage> receivedMessages = new ConcurrentHashSet<>();
      FixedRateConsumer consumer = new FixedRateConsumer(30, MESSAGES_PER_MINUTE, receivedMessages, sf, QUEUE, 0);
      consumer.start();


      Wait.assertEquals(1, queue::getConsumerCount);
      try {
         Wait.assertEquals(0, queue::getConsumerCount);
      } finally {
         consumer.stopRunning();
      }

   }


   @Test
   public void testDaysSurviving() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_DAY);
      addressSettings.setSlowConsumerThreshold(TimeUnit.DAYS.toSeconds(1)); // one message per second
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);


      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      int messages = 10;

      for (int i = 0; i < messages; i++) {
         producer.send(session.createMessage(true));
      }
      session.commit();


      ConcurrentHashSet<ClientMessage> receivedMessages = new ConcurrentHashSet<>();
      FixedRateConsumer consumer = new FixedRateConsumer(70, MESSAGES_PER_MINUTE, receivedMessages, sf, QUEUE, 0);
      consumer.start();

      Queue queue = server.locateQueue(QUEUE);

      Wait.assertEquals(1, queue::getConsumerCount);
      try {
         Wait.assertEquals(messages, queue::getMessagesAcknowledged);
         Wait.assertEquals(messages, () -> receivedMessages.size());
      } finally {
         consumer.stopRunning();
      }
      Wait.assertEquals(0, queue::getConsumerCount);

   }


   @Test
   public void testMinuteSurviving() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_MINUTE);
      addressSettings.setSlowConsumerThreshold(60);
      //addressSettings.sets
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);


      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      int messages = 10;

      for (int i = 0; i < messages; i++) {
         producer.send(session.createMessage(true));
      }
      session.commit();


      ConcurrentHashSet<ClientMessage> receivedMessages = new ConcurrentHashSet<>();
      FixedRateConsumer consumer = new FixedRateConsumer(80, MESSAGES_PER_MINUTE, receivedMessages, sf, QUEUE, 0);
      consumer.start();

      Queue queue = server.locateQueue(QUEUE);

      try {
         Wait.assertEquals(messages, queue::getMessagesAcknowledged);
         assertEquals(1, queue.getConsumerCount());
         Wait.assertEquals(messages, () -> receivedMessages.size());
      } finally {
         consumer.stopRunning();
      }

      Wait.assertEquals(0, queue::getConsumerCount);
   }


   @Test
   public void testKilledOnNoMessagesSoCanBeRebalanced() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(1);
      addressSettings.setSlowConsumerThresholdMeasurementUnit(MESSAGES_PER_SECOND);
      addressSettings.setSlowConsumerThreshold(0); // if there are no messages pending, kill me
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);

      final Queue queue = server.locateQueue(QUEUE);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      int messages = 1;

      for (int i = 0; i < messages; i++) {
         producer.send(session.createMessage(true));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();
      consumer.receive(500).individualAcknowledge();
      assertEquals(1, queue.getConsumerCount());

      // gets whacked!
      Wait.assertEquals(0, queue::getConsumerCount, 2000, 100);
   }

   /**
    * This test creates 3 consumers on one queue. A producer sends messages at a rate of 2 messages per second. Each
    * consumer consumes messages at rate of 1 message per second. The slow consumer threshold is 1 message per second.
    * Based on the above settings, slow consumer removal will not be performed (2 < 3*1), so no consumer will be removed
    * during the test, and all messages will be received.
    */
   @Test
   public void testMultipleConsumersOneQueue() throws Exception {
      locator.setAckBatchSize(0);

      Queue queue = server.locateQueue(QUEUE);

      ClientSessionFactory sf1 = createSessionFactory(locator);
      ClientSessionFactory sf2 = createSessionFactory(locator);
      ClientSessionFactory sf3 = createSessionFactory(locator);
      ClientSessionFactory sf4 = createSessionFactory(locator);

      final int messages = 10 * threshold;

      FixedRateProducer producer = new FixedRateProducer(threshold * 2, MESSAGES_PER_SECOND, sf1, QUEUE, messages);

      final Set<FixedRateConsumer> consumers = new ConcurrentHashSet<>();
      final Set<ClientMessage> receivedMessages = new ConcurrentHashSet<>();

      consumers.add(new FixedRateConsumer(threshold, MESSAGES_PER_SECOND, receivedMessages, sf2, QUEUE, 1));
      consumers.add(new FixedRateConsumer(threshold, MESSAGES_PER_SECOND, receivedMessages, sf3, QUEUE, 2));
      consumers.add(new FixedRateConsumer(threshold, MESSAGES_PER_SECOND, receivedMessages, sf4, QUEUE, 3));

      try {
         producer.start();

         for (FixedRateConsumer consumer : consumers) {
            consumer.start();
         }

         producer.join(10000);

         assertEquals(3, queue.getConsumerCount());
         Wait.assertEquals(messages, receivedMessages::size);


      } finally {
         producer.stopRunning();
         assertFalse(producer.failed);
         for (FixedRateConsumer consumer : consumers) {
            consumer.stopRunning();
            assertFalse(consumer.failed);
         }
         logger.debug("***report messages received: {}", receivedMessages.size());
         logger.debug("***consumers left: {}", consumers.size());
      }
   }

   private class FixedRateProducer extends FixedRateClient {

      int messages;
      ClientProducer producer;

      FixedRateProducer(int rate, SlowConsumerThresholdMeasurementUnit unit, ClientSessionFactory sf, SimpleString queue, int messages) throws ActiveMQException {
         super(sf, queue, rate, unit);
         this.messages = messages;
      }

      @Override
      protected void prepareWork() throws ActiveMQException {
         super.prepareWork();
         this.producer = session.createProducer(queue);
      }

      @Override
      protected void doWork(int count) throws Exception {

         if (count < messages) {
            ClientMessage m = createTextMessage(session, "msg" + count);
            producer.send(m);
            logger.debug("producer sent a message {}", count);
         } else {
            this.working = false;
         }
      }

      @Override
      public String toString() {
         return "Producer";
      }
   }

   private class FixedRateConsumer extends FixedRateClient {

      Set<FixedRateConsumer> consumers;
      ClientConsumer consumer;
      final Set<ClientMessage> receivedMessages;
      int id;

      FixedRateConsumer(int rate,
                        SlowConsumerThresholdMeasurementUnit unit,
                        Set<ClientMessage> receivedMessages,
                        ClientSessionFactory sf,
                        SimpleString queue,
                        int id) throws ActiveMQException {
         super(sf, queue, rate, unit);
         this.id = id;
         this.receivedMessages = receivedMessages;
      }

      @Override
      protected void prepareWork() throws ActiveMQException {
         super.prepareWork();
         this.consumer = session.createConsumer(queue);
         this.session.start();
      }

      @Override
      protected void doWork(int count) throws Exception {
         ClientMessage m = this.consumer.receive(1000);
         logger.debug("consumer {} got m: {}", id, m);
         if (m != null) {
            receivedMessages.add(m);
            m.acknowledge();
            session.commit();
            logger.debug(" consumer {} acked {} now total received: {}", id, m.getClass().getName(), receivedMessages.size());
         }
      }

      @Override
      protected void handleError(int count, Exception e) {
         failed = true;
         System.err.println("Got error receiving message " + count + " remove self " + this.id);
         e.printStackTrace();
      }

      @Override
      public String toString() {
         return "Consumer " + id;
      }

   }

   private abstract class FixedRateClient extends Thread {

      protected ClientSessionFactory sf;
      protected SimpleString queue;
      protected ClientSession session;
      protected final int sleepTime;
      protected volatile boolean working;
      boolean failed;

      FixedRateClient(ClientSessionFactory sf, SimpleString queue, int rate, SlowConsumerThresholdMeasurementUnit unit) throws ActiveMQException {
         this.sf = sf;
         this.queue = queue;
         this.sleepTime = (int) (SlowConsumerThresholdMeasurementUnit.unitOf(unit.getValue()).toMillis(1) / rate);
         logger.debug("{} has sleepTime = {} which is {} seconds", this.getClass(), sleepTime, TimeUnit.MILLISECONDS.toSeconds(sleepTime));
      }

      protected void prepareWork() throws ActiveMQException {
         this.session = addClientSession(sf.createSession(true, true));
      }

      @Override
      public void run() {
         working = true;
         try {
            prepareWork();
         } catch (ActiveMQException e) {
            logger.debug("got error in prepareWork(), aborting...");
            e.printStackTrace();
            return;
         }
         int count = 0;
         while (working) {
            try {
               doWork(count);
               logger.debug("{} sleeping {}", this.getClass().getName(), sleepTime);
               Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
               // expected, nothing to be done
            } catch (Exception e) {
               failed = true;
               handleError(count, e);
               working = false;
            } finally {
               count++;
            }
         }
      }

      protected abstract void doWork(int count) throws Exception;

      protected void handleError(int count, Exception e) {
      }

      public void stopRunning() {
         working = false;
         try {
            session.close();
            this.interrupt();
            join(5000);
            if (isAlive()) {
               fail("Interrupt is not working on Working Thread");
            }
         } catch (InterruptedException e) {
            e.printStackTrace();
         } catch (Exception e) {
            handleError(0, e);
         }
      }
   }

}
