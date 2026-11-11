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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsumerCloseTest extends ActiveMQTestBase {

   private ClientSessionFactory sf;
   private ActiveMQServer server;

   private ClientSession session;

   private SimpleString queue;

   private SimpleString address;
   private ServerLocator locator;




   @Test
   public void testCanNotUseAClosedConsumer() throws Exception {
      final ClientConsumer consumer = session.createConsumer(queue);

      consumer.close();

      assertTrue(consumer.isClosed());

      expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> consumer.receive());

      expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> consumer.receiveImmediate());

      expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> consumer.setMessageHandler(message -> {
      }));
   }

   @Test
   public void testCloseWithManyMessagesInBufferAndSlowConsumer() throws Exception {
      ClientConsumer consumer = session.createConsumer(queue);

      ClientProducer producer = session.createProducer(address);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      final CountDownLatch received = new CountDownLatch(1);
      final CountDownLatch waitingToProceed = new CountDownLatch(1);
      class MyHandler implements MessageHandler {

         @Override
         public void onMessage(final ClientMessage message) {
            try {
               received.countDown();
               waitingToProceed.await();
            } catch (Exception e) {
            }
         }
      }

      consumer.setMessageHandler(new MyHandler());

      session.start();

      assertTrue(received.await(5, TimeUnit.SECONDS));

      long timeout = System.currentTimeMillis() + 1000;

      // Instead of waiting a long time (like 1 second) we just make sure the buffer is full on the client
      while (((ClientConsumerImpl) consumer).getBufferSize() < 2 && System.currentTimeMillis() > timeout) {
         Thread.sleep(10);
      }

      waitingToProceed.countDown();

      // Close shouldn't wait for all messages to be processed before closing
      long start = System.currentTimeMillis();
      consumer.close();
      long end = System.currentTimeMillis();

      assertTrue(end - start <= 1500);

   }

   @Test
   public void testCloseWithScheduledRedelivery() throws Exception {

      AddressSettings settings = new AddressSettings().setRedeliveryDelay(50000);
      server.getAddressSettingsRepository().addMatch("#", settings);

      ClientConsumer consumer = session.createConsumer(queue);

      ClientProducer producer = session.createProducer(address);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      session.start();

      ClientMessage msg = consumer.receive(5000);
      msg.acknowledge();

      long timeout = System.currentTimeMillis() + 1000;

      while (((ClientConsumerImpl) consumer).getBufferSize() < 2 && System.currentTimeMillis() > timeout) {
         Thread.sleep(10);
      }

      consumer.close();

      consumer = session.createConsumer(queue);

      // We received one, so we must receive the others now
      for (int i = 0; i < numMessages - 1; i++) {
         msg = consumer.receive(1000);
         assertNotNull(msg, "Expected message at i=" + i);
         msg.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      // Close shouldn't wait for all messages to be processed before closing
      long start = System.currentTimeMillis();
      consumer.close();
      long end = System.currentTimeMillis();

      assertTrue(end - start <= 1500);

   }

   @Test
   public void testCloseWithScheduledRedeliveryWithTX() throws Exception {

      AddressSettings settings = new AddressSettings().setRedeliveryDelay(1000);
      server.getAddressSettingsRepository().addMatch("#", settings);

      ClientProducer producer = session.createProducer(address);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);
         message.putIntProperty("count", i);
         producer.send(message);
      }

      session.close();

      session = addClientSession(sf.createSession(false, false));

      ClientConsumer consumer = session.createConsumer(queue);

      session.start();

      ClientMessage msg = consumer.receive(500);
      msg.acknowledge();

      long timeout = System.currentTimeMillis() + 1000;

      while (((ClientConsumerImpl) consumer).getBufferSize() < 2 && System.currentTimeMillis() > timeout) {
         Thread.sleep(10);
      }

      consumer.close();

      session.rollback();

      consumer = session.createConsumer(queue);

      // We received one, so we must receive the others now
      for (int i = 0; i < numMessages - 1; i++) {
         msg = consumer.receive(1000);
         assertNotNull(msg, "Expected message at i=" + i);
         msg.acknowledge();
      }

      assertNull(consumer.receiveImmediate());

      // The first message received after redeliveryDelay
      msg = consumer.receive(5000);
      assertNotNull(msg);
      assertEquals(0, msg.getIntProperty("count").intValue());
      msg.acknowledge();
      session.commit();

      assertNull(consumer.receiveImmediate());

      // Close shouldn't wait for all messages to be processed before closing
      long start = System.currentTimeMillis();
      consumer.close();
      long end = System.currentTimeMillis();

      assertTrue(end - start <= 1500);

   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultInVMConfig();

      server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      address = RandomUtil.randomUUIDSimpleString();
      queue = RandomUtil.randomUUIDSimpleString();

      locator = createInVMNonHALocator();

      sf = createSessionFactory(locator);

      session = addClientSession(sf.createSession(false, true, true));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
   }

}
