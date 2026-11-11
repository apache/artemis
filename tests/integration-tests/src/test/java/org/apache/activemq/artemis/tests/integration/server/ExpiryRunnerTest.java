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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerQueuePlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpiryRunnerTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSession clientSession;

   private final SimpleString qName = SimpleString.of("ExpiryRunnerTestQ");

   private final SimpleString qName2 = SimpleString.of("ExpiryRunnerTestQ2");

   private SimpleString expiryQueue;

   private SimpleString expiryAddress;

   Queue artemisExpiryQueue;

   private ServerLocator locator;

   @Test
   public void testBasicExpire() throws Exception {
      ClientProducer producer = clientSession.createProducer(qName);
      int numMessages = 100;
      long expiration = System.currentTimeMillis();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = createTextMessage(clientSession, "m" + i);
         m.setExpiration(expiration);
         producer.send(m);
      }
      Thread.sleep(1600);
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getMessageCount());
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getDeliveringCount());
   }

   @Test
   public void testExpireFromMultipleQueues() throws Exception {
      ClientProducer producer = clientSession.createProducer(qName);
      clientSession.createQueue(QueueConfiguration.of(qName2).setDurable(false));
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(expiryAddress);
      server.getAddressSettingsRepository().addMatch(qName2.toString(), addressSettings);
      ClientProducer producer2 = clientSession.createProducer(qName2);
      int numMessages = 100;
      long expiration = System.currentTimeMillis();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = createTextMessage(clientSession, "m" + i);
         m.setExpiration(expiration);
         producer.send(m);
         m = createTextMessage(clientSession, "m" + i);
         m.setExpiration(expiration);
         producer2.send(m);
      }
      Thread.sleep(1600);
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getMessageCount());
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getDeliveringCount());
   }

   @Test
   public void testExpireHalf() throws Exception {
      ClientProducer producer = clientSession.createProducer(qName);
      int numMessages = 100;
      long expiration = System.currentTimeMillis();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0) {
            m.setExpiration(expiration);
         }
         producer.send(m);
      }
      Thread.sleep(1600);
      assertEquals(numMessages / 2, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getMessageCount());
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getDeliveringCount());
   }

   @Test
   public void testExpireConsumeHalf() throws Exception {
      ClientProducer producer = clientSession.createProducer(qName);
      int numMessages = 100;
      long expiration = System.currentTimeMillis() + 1000;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = createTextMessage(clientSession, "m" + i);
         m.setExpiration(expiration);
         producer.send(m);
      }
      ClientConsumer consumer = clientSession.createConsumer(qName);
      clientSession.start();
      for (int i = 0; i < numMessages / 2; i++) {
         ClientMessage cm = consumer.receive(500);
         assertNotNull(cm, "message not received " + i);
         cm.acknowledge();
         assertEquals("m" + i, cm.getBodyBuffer().readString());
      }
      consumer.close();
      Wait.assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable())::getMessageCount);
      Wait.assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable())::getDeliveringCount);
      Wait.assertEquals(numMessages / 2, ((Queue) server.getPostOffice().getBinding(qName).getBindable())::getMessagesExpired);
   }

   @Test
   public void testExpireToExpiryQueue() throws Exception {
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(expiryAddress);
      server.getAddressSettingsRepository().addMatch(qName2.toString(), addressSettings);
      clientSession.deleteQueue(qName);
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName2).setAddress(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      int numMessages = 100;
      long expiration = System.currentTimeMillis();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = createTextMessage(clientSession, "m" + i);
         m.setExpiration(expiration);
         producer.send(m);
      }
      Thread.sleep(1600);
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getMessageCount());
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getDeliveringCount());

      ClientConsumer consumer = clientSession.createConsumer(expiryQueue);
      clientSession.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = consumer.receive(500);
         assertNotNull(cm);
         // assertEquals("m" + i, cm.getBody().getString());
      }
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = consumer.receive(500);
         assertNotNull(cm);
         // assertEquals("m" + i, cm.getBody().getString());
      }
      assertEquals(100, ((Queue) server.getPostOffice().getBinding(qName).getBindable()).getMessagesExpired());
      consumer.close();
   }

   @Test
   public void testExpireWhilstConsumingMessagesStillInOrder() throws Exception {
      ClientProducer producer = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(consumer, latch);
      clientSession.start();
      Thread thr = new Thread(dummyMessageHandler);
      thr.start();
      long expiration = System.currentTimeMillis() + 1000;
      int numMessages = 0;
      long sendMessagesUntil = System.currentTimeMillis() + 2000;
      do {
         ClientMessage m = createTextMessage(clientSession, "m" + numMessages++);
         m.setExpiration(expiration);
         producer.send(m);
         Thread.sleep(100);
      }
      while (System.currentTimeMillis() < sendMessagesUntil);
      assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
      consumer.close();

      consumer = clientSession.createConsumer(expiryQueue);
      do {
         ClientMessage cm = consumer.receive(2000);
         if (cm == null) {
            break;
         }
         String text = cm.getBodyBuffer().readString();
         cm.acknowledge();
         assertFalse(dummyMessageHandler.payloads.contains(text));
         dummyMessageHandler.payloads.add(text);
      }
      while (true);

      for (int i = 0; i < numMessages; i++) {
         if (dummyMessageHandler.payloads.isEmpty()) {
            break;
         }
         assertTrue(dummyMessageHandler.payloads.remove("m" + i), "m" + i);
      }
      consumer.close();
      thr.join();
   }


   @Test
   public void testManyQueuesExpire() throws Exception {

      AtomicInteger currentExpiryHappening = new AtomicInteger();
      AtomicInteger maxExpiryHappening = new AtomicInteger(0);

      server.registerBrokerPlugin(new ActiveMQServerQueuePlugin() {
         @Override
         public void beforeExpiryScan(Queue queue) {
            currentExpiryHappening.incrementAndGet();
            while (!maxExpiryHappening.compareAndSet(maxExpiryHappening.get(), Math.max(maxExpiryHappening.get(), currentExpiryHappening.get()))) {
               Thread.yield();
            }
         }

         @Override
         public void afterExpiryScan(Queue queue) {
            currentExpiryHappening.decrementAndGet();
         }
      });

      assertTrue(server.hasBrokerQueuePlugins());

      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setExpiryAddress(expiryAddress));
      for (int ad = 0; ad < 1000; ad++) {
         server.addAddressInfo(new AddressInfo("test" + ad));
         server.createQueue(QueueConfiguration.of("test" + ad).setAddress("test" + ad).setRoutingType(RoutingType.ANYCAST));
      }

      ClientProducer producer = clientSession.createProducer();

      for (int i = 0; i < 1000; i++) {
         ClientMessage message = clientSession.createMessage(true);
         message.setExpiration(System.currentTimeMillis());
         producer.send("test" + i, message);
      }

      Wait.assertEquals(1000, artemisExpiryQueue::getMessageCount);

      // The system should not burst itself looking for expiration, that would use too many resources from the broker itself
      assertEquals(1, maxExpiryHappening.get(), "The System had " + maxExpiryHappening + " threads in parallel scanning for expiration");

   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ConfigurationImpl configuration = (ConfigurationImpl) createDefaultInVMConfig().setMessageExpiryScanPeriod(100);
      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      // start the server
      server.start();
      // then we create a client as normal
      locator = createInVMNonHALocator().setBlockOnAcknowledge(true);

      ClientSessionFactory sessionFactory = createSessionFactory(locator);

      clientSession = sessionFactory.createSession(false, true, true);
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      expiryAddress = SimpleString.of("EA");
      expiryQueue = SimpleString.of("expiryQ");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(expiryAddress);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      server.getAddressSettingsRepository().addMatch(qName2.toString(), addressSettings);
      clientSession.createQueue(QueueConfiguration.of(expiryQueue).setAddress(expiryAddress).setDurable(true));
      artemisExpiryQueue = server.locateQueue(expiryQueue);
      assertNotNull(artemisExpiryQueue);
   }

   private static class DummyMessageHandler implements Runnable {

      List<String> payloads = new ArrayList<>();

      private final ClientConsumer consumer;

      private final CountDownLatch latch;

      private DummyMessageHandler(final ClientConsumer consumer, final CountDownLatch latch) {
         this.consumer = consumer;
         this.latch = latch;
      }

      @Override
      public void run() {
         while (true) {
            try {
               ClientMessage message = consumer.receive(5000);
               if (message == null) {
                  break;
               }
               message.acknowledge();
               payloads.add(message.getBodyBuffer().readString());

               Thread.sleep(110);
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         latch.countDown();

      }
   }
}
