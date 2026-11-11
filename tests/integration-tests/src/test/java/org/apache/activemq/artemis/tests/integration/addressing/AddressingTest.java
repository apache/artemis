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
package org.apache.activemq.artemis.tests.integration.addressing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQQueueMaxConsumerLimitReached;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddressingTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSessionFactory sessionFactory;

   @BeforeEach
   public void setup() throws Exception {
      server = createServer(true);
      server.start();

      server.waitForActivation(10, TimeUnit.SECONDS);

      ServerLocator sl = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      sessionFactory = sl.createSessionFactory();

      addSessionFactory(sessionFactory);
   }

   @Test
   public void testMulticastRouting() throws Exception {

      SimpleString sendAddress = SimpleString.of("test.address");

      List<String> testAddresses = Arrays.asList("test.address", "test.#", "test.*");

      for (String consumeAddress : testAddresses) {

         // For each address, create 2 Queues with the same address, assert both queues receive message

         AddressInfo addressInfo = new AddressInfo(SimpleString.of(consumeAddress));
         addressInfo.addRoutingType(RoutingType.MULTICAST);

         server.addOrUpdateAddressInfo(addressInfo);
         Queue q1 = server.createQueue(QueueConfiguration.of(consumeAddress + ".1").setAddress(consumeAddress).setRoutingType(RoutingType.MULTICAST));
         Queue q2 = server.createQueue(QueueConfiguration.of(consumeAddress + ".2").setAddress(consumeAddress).setRoutingType(RoutingType.MULTICAST));

         ClientSession session = sessionFactory.createSession();
         session.start();

         ClientConsumer consumer1 = session.createConsumer(q1.getName());
         ClientConsumer consumer2 = session.createConsumer(q2.getName());

         ClientProducer producer = session.createProducer(sendAddress);
         ClientMessage m = session.createMessage(ClientMessage.TEXT_TYPE, true);
         m.getBodyBuffer().writeString("TestMessage");

         producer.send(m);

         assertNotNull(consumer1.receive(2000));
         assertNotNull(consumer2.receive(2000));

         q1.deleteQueue();
         q2.deleteQueue();
      }
   }

   @Test
   public void testDynamicMulticastRouting() throws Exception {

      SimpleString sendAddress = SimpleString.of("test.address");

      AddressInfo addressInfo = new AddressInfo(sendAddress);
      addressInfo.addRoutingType(RoutingType.MULTICAST);

      server.addOrUpdateAddressInfo(addressInfo);
      Queue q1 = server.createQueue(QueueConfiguration.of("1.test.address").setAddress("test.address").setRoutingType(RoutingType.MULTICAST));
      Queue q2 = server.createQueue(QueueConfiguration.of("2.test.#").setAddress("test.#").setRoutingType(RoutingType.MULTICAST));

      ClientSession session = sessionFactory.createSession();
      session.start();


      ClientConsumer consumer1 = session.createConsumer(q1.getName());
      ClientConsumer consumer2 = session.createConsumer(q2.getName());


      ClientProducer producer = session.createProducer(sendAddress);
      ClientMessage m = session.createMessage(ClientMessage.TEXT_TYPE, true);
      m.getBodyBuffer().writeString("TestMessage");

      producer.send(m);

      assertNotNull(consumer1.receive(2000));
      assertNotNull(consumer2.receive(2000));

      // add in a new wildcard producer, bindings version will be incremented
      Queue q3 = server.createQueue(QueueConfiguration.of("3.test.*").setAddress("test.*").setRoutingType(RoutingType.MULTICAST));
      ClientConsumer consumer3 = session.createConsumer(q3.getName());

      producer.send(m);

      assertNotNull(consumer1.receive(2000));
      assertNotNull(consumer2.receive(2000));
      assertNotNull(consumer3.receive(2000));

      q1.deleteQueue();
      q2.deleteQueue();
      q3.deleteQueue();
   }

   @Test
   public void testAnycastRouting() throws Exception {

      SimpleString sendAddress = SimpleString.of("test.address");

      List<String> testAddresses = Arrays.asList("test.address", "test.#", "test.*");

      for (String consumeAddress : testAddresses) {

         // For each address, create 2 Queues with the same address, assert one queue receive message

         AddressInfo addressInfo = new AddressInfo(SimpleString.of(consumeAddress));
         addressInfo.addRoutingType(RoutingType.ANYCAST);

         server.addOrUpdateAddressInfo(addressInfo);
         Queue q1 = server.createQueue(QueueConfiguration.of(consumeAddress + ".1").setAddress(consumeAddress).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));
         Queue q2 = server.createQueue(QueueConfiguration.of(consumeAddress + ".2").setAddress(consumeAddress).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));

         ClientSession session = sessionFactory.createSession();
         session.start();

         ClientConsumer consumer1 = session.createConsumer(q1.getName());
         ClientConsumer consumer2 = session.createConsumer(q2.getName());

         ClientProducer producer = session.createProducer(sendAddress);
         ClientMessage m = session.createMessage(ClientMessage.TEXT_TYPE, true);

         m.getBodyBuffer().writeString("TestMessage");

         producer.send(m);

         int count = 0;
         count = (consumer1.receive(1000) == null) ? count : count + 1;
         count = (consumer2.receive(1000) == null) ? count : count + 1;
         assertEquals(1, count);

         q1.deleteQueue();
         q2.deleteQueue();
      }
   }

   @Test
   public void testAnycastRoutingRoundRobin() throws Exception {

      SimpleString address = SimpleString.of("test.address");
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.addRoutingType(RoutingType.ANYCAST);

      server.addOrUpdateAddressInfo(addressInfo);
      Queue q1 = server.createQueue(QueueConfiguration.of(address.concat(".1")).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));
      Queue q2 = server.createQueue(QueueConfiguration.of(address.concat(".2")).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));
      Queue q3 = server.createQueue(QueueConfiguration.of(address.concat(".3")).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));

      ClientSession session = sessionFactory.createSession();
      session.start();

      ClientProducer producer = session.createProducer(address);

      ClientConsumer consumer1 = session.createConsumer(q1.getName());
      ClientConsumer consumer2 = session.createConsumer(q2.getName());
      ClientConsumer consumer3 = session.createConsumer(q3.getName());
      List<ClientConsumer> consumers = new ArrayList<>(Arrays.asList(new ClientConsumer[]{consumer1, consumer2, consumer3}));

      List<String> messages = new ArrayList<>();
      messages.add("Message1");
      messages.add("Message2");
      messages.add("Message3");

      ClientMessage clientMessage;
      for (String message : messages) {
         clientMessage = session.createMessage(true);
         clientMessage.getBodyBuffer().writeString(message);
         producer.send(clientMessage);
      }

      String m;
      for (ClientConsumer consumer : consumers) {
         clientMessage = consumer.receive(1000);
         m = clientMessage.getBodyBuffer().readString();
         messages.remove(m);
      }

      assertTrue(messages.isEmpty());

      // Check we don't receive more messages
      int count = 0;
      for (ClientConsumer consumer : consumers) {
         count = (consumer.receive(1000) == null) ? count : count + 1;
      }
      assertEquals(0, count);
   }

   @Test
   public void testMulticastRoutingBackwardsCompat() throws Exception {

      SimpleString sendAddress = SimpleString.of("test.address");

      List<String> testAddresses = Arrays.asList("test.address", "test.#", "test.*");

      for (String consumeAddress : testAddresses) {

         // For each address, create 2 Queues with the same address, assert both queues receive message
         Queue q1 = server.createQueue(QueueConfiguration.of(consumeAddress + ".1").setAddress(consumeAddress).setRoutingType(RoutingType.MULTICAST));
         Queue q2 = server.createQueue(QueueConfiguration.of(consumeAddress + ".2").setAddress(consumeAddress).setRoutingType(RoutingType.MULTICAST));

         ClientSession session = sessionFactory.createSession();
         session.start();

         ClientConsumer consumer1 = session.createConsumer(q1.getName());
         ClientConsumer consumer2 = session.createConsumer(q2.getName());

         ClientProducer producer = session.createProducer(sendAddress);
         ClientMessage m = session.createMessage(ClientMessage.TEXT_TYPE, true);
         m.getBodyBuffer().writeString("TestMessage");

         producer.send(m);

         assertNotNull(consumer1.receive(2000));
         assertNotNull(consumer2.receive(2000));

         q1.deleteQueue();
         q2.deleteQueue();
      }
   }

   @Test
   public void testPurgeOnNoConsumersTrue() throws Exception {
      SimpleString address = SimpleString.of("test.address");
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setPurgeOnNoConsumers(true));
      Queue queue = server.locateQueue(queueName);
      assertNotNull(queue);
      ClientSession session = sessionFactory.createSession();
      ClientProducer producer = session.createProducer(address);

      // there are no consumers so no messages should be routed to the queue
      producer.send(session.createMessage(true));
      assertEquals(0, queue.getMessageCount());

      ClientConsumer consumer = session.createConsumer(queueName);
      // there is a consumer now so the message should be routed
      producer.send(session.createMessage(true));
      Wait.assertEquals(1, queue::getMessageCount);


      consumer.close();
      // the last consumer was closed so the queue should exist but be purged
      assertNotNull(server.locateQueue(queueName));
      Wait.assertEquals(0, queue::getMessageCount);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         // there are no consumers so no messages should be routed to the queue
         for (int i = 0; i < 5; i++) {
            producer.send(session.createMessage(true));
         }
         // should be informed only once
         assertEquals(1, loggerHandler.countText("AMQ224149"));
         Wait.assertEquals(0, queue::getMessageCount);

         consumer = session.createConsumer(queueName);
         producer.send(session.createMessage(true));
         consumer.close();
         producer.send(session.createMessage(true));

         // the second send should warn again as we cleared the status to print the logging again
         assertEquals(2, loggerHandler.countText("AMQ224149"));
         Wait.assertEquals(0, queue::getMessageCount);
      }
   }

   @Test
   public void testPurgeOnNoConsumersFalse() throws Exception {
      SimpleString address = SimpleString.of("test.address");
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1));
      assertNotNull(server.locateQueue(queueName));
      ClientSession session = sessionFactory.createSession();
      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(true));
      session.createConsumer(queueName).close();
      assertNotNull(server.locateQueue(queueName));
      Wait.assertEquals(1, server.locateQueue(queueName)::getMessageCount);
   }


   @Test
   public void testQueueEnabledDisabled() throws Exception {
      SimpleString address = SimpleString.of("test.address");
      SimpleString defaultQueue = SimpleString.of(UUID.randomUUID().toString());
      SimpleString enabledQueue = SimpleString.of(UUID.randomUUID().toString());
      SimpleString disabledQueue = SimpleString.of(UUID.randomUUID().toString());

      //Validate default is enabled, and check that queues enabled receive messages and disabled do not on same address.

      ClientSession session = sessionFactory.createSession();
      ClientProducer producer = session.createProducer(address);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.createQueue(QueueConfiguration.of(defaultQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST));
         server.createQueue(QueueConfiguration.of(enabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(true));
         server.createQueue(QueueConfiguration.of(disabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         // one queue was disabled
         assertEquals(1, loggerHandler.countText("AMQ224150"));

         assertNotNull(server.locateQueue(defaultQueue));
         assertNotNull(server.locateQueue(enabledQueue));
         assertNotNull(server.locateQueue(disabledQueue));
         producer.send(session.createMessage(true));
         // the warning had already been sent when the queue was created
         assertEquals(1, loggerHandler.countText("AMQ224148"));
      }

      assertNotNull(server.locateQueue(defaultQueue));
      assertNotNull(server.locateQueue(enabledQueue));
      assertNotNull(server.locateQueue(disabledQueue));

      Wait.assertEquals(1, server.locateQueue(defaultQueue)::getMessageCount);
      Wait.assertEquals(1, server.locateQueue(enabledQueue)::getMessageCount);
      Wait.assertEquals(0, server.locateQueue(disabledQueue)::getMessageCount);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         //Update Queue Disable All
         server.updateQueue(QueueConfiguration.of(defaultQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         server.updateQueue(QueueConfiguration.of(enabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         server.updateQueue(QueueConfiguration.of(disabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         assertEquals(2, loggerHandler.countText("AMQ224150"));

         producer.send(session.createMessage(true));
         producer.send(session.createMessage(true));
         // we should be informed only twice even though there are 3 queues, as one of those were already informed before
         assertEquals(2, loggerHandler.countText("AMQ224148"));
      }

      Wait.assertEquals(1, server.locateQueue(defaultQueue)::getMessageCount);
      Wait.assertEquals(1, server.locateQueue(enabledQueue)::getMessageCount);
      Wait.assertEquals(0, server.locateQueue(disabledQueue)::getMessageCount);


      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         //Update Queue Enable All
         server.updateQueue(QueueConfiguration.of(defaultQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(true));
         server.updateQueue(QueueConfiguration.of(enabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(true));
         server.updateQueue(QueueConfiguration.of(disabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(true));

         producer.send(session.createMessage(true));
         assertFalse(loggerHandler.findText("AMQ224148"));
      }

      Wait.assertEquals(2, server.locateQueue(defaultQueue)::getMessageCount);
      Wait.assertEquals(2, server.locateQueue(enabledQueue)::getMessageCount);
      Wait.assertEquals(1, server.locateQueue(disabledQueue)::getMessageCount);

      // disable again

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.updateQueue(QueueConfiguration.of(defaultQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         server.updateQueue(QueueConfiguration.of(enabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         server.updateQueue(QueueConfiguration.of(disabledQueue).setAddress(address).setRoutingType(RoutingType.MULTICAST).setEnabled(false));
         assertEquals(3, loggerHandler.countText("AMQ224150"));

         for (int i = 0; i < 10; i++) {
            producer.send(session.createMessage(true));
         }
         // we should warn again since it was enabled.
         // and we should be warned 3 times, one for each queue.
         assertEquals(3, loggerHandler.countText("AMQ224148"));
      }

      Wait.assertEquals(2, server.locateQueue(defaultQueue)::getMessageCount);
      Wait.assertEquals(2, server.locateQueue(enabledQueue)::getMessageCount);
      Wait.assertEquals(1, server.locateQueue(disabledQueue)::getMessageCount);
   }

   @Test
   public void testLimitOnMaxConsumers() throws Exception {
      SimpleString address = SimpleString.of("test.address");
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      // For each address, create 2 Queues with the same address, assert both queues receive message
      boolean purgeOnNoConsumers = false;
      Queue q1 = server.createQueue(QueueConfiguration.of(queueName).setAddress(address).setMaxConsumers(0).setPurgeOnNoConsumers(purgeOnNoConsumers));

      Exception expectedException = null;
      String expectedMessage = "Maximum Consumer Limit Reached on Queue";
      try {
         ClientSession session = sessionFactory.createSession();
         session.start();

         session.createConsumer(q1.getName());
      } catch (ActiveMQQueueMaxConsumerLimitReached e) {
         expectedException = e;
      }

      assertNotNull(expectedException);
      assertTrue(expectedException.getMessage().contains(expectedMessage));
      assertTrue(expectedException.getMessage().contains(address));
      assertTrue(expectedException.getMessage().contains(queueName));
   }

   @Test
   public void testUnlimitedMaxConsumers() throws Exception {
      int noConsumers = 50;
      SimpleString address = SimpleString.of("test.address");
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      // For each address, create 2 Queues with the same address, assert both queues receive message
      boolean purgeOnNoConsumers = false;
      Queue q1 = server.createQueue(QueueConfiguration.of(queueName).setAddress(address).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED).setPurgeOnNoConsumers(purgeOnNoConsumers));

      ClientSession session = sessionFactory.createSession();
      session.start();

      for (int i = 0; i < noConsumers; i++) {
         session.createConsumer(q1.getName());
      }
   }

   @Test
   public void testEmptyRoutingTypes() throws Exception {
      server.addOrUpdateAddressInfo(new AddressInfo(SimpleString.of("xy")));
      server.stop();
      server.start();
      server.addOrUpdateAddressInfo(new AddressInfo(SimpleString.of("xy")));
      server.stop();
   }
}
