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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReceiveImmediateTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ReceiveImmediateTest.queue");

   private final SimpleString ADDRESS = SimpleString.of("ReceiveImmediateTest.address");

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration config = createDefaultInVMConfig();
      server = createServer(false, config);
      server.start();
      locator = createInVMNonHALocator();
   }

   private ClientSessionFactory sf;

   @Test
   public void testConsumerReceiveImmediateWithNoMessages() throws Exception {
      doConsumerReceiveImmediateWithNoMessages(false);
   }

   @Test
   public void testConsumerReceiveImmediate() throws Exception {
      doConsumerReceiveImmediate(false);
   }

   @Test
   public void testBrowserReceiveImmediateWithNoMessages() throws Exception {
      doConsumerReceiveImmediateWithNoMessages(true);
   }

   @Test
   public void testBrowserReceiveImmediate() throws Exception {
      doConsumerReceiveImmediate(true);
   }

   @Test
   public void testConsumerReceiveImmediateWithSessionStop() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setDurable(false));

      ClientConsumer consumer = session.createConsumer(QUEUE, null, false);
      session.start();

      session.stop();
      assertNull(consumer.receiveImmediate());

      session.start();
      long start = System.currentTimeMillis();
      ClientMessage msg = consumer.receive(2000);
      long end = System.currentTimeMillis();
      assertNull(msg);
      // we waited for at least 2000ms
      assertTrue(end - start >= 2000, "waited only " + (end - start));

      consumer.close();

      session.close();
   }

   @Test
   public void testReceivedImmediateFollowedByReceive() throws Exception {
      locator.setBlockOnNonDurableSend(true);
      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setDurable(false));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = session.createMessage(false);

      producer.send(message);

      ClientConsumer consumer = session.createConsumer(QUEUE, null, false);

      session.start();

      ClientMessage received = consumer.receiveImmediate();

      assertNotNull(received);

      received.acknowledge();

      received = consumer.receive(1);

      assertNull(received);

      session.close();
   }

   @Test
   public void testReceivedImmediateFollowedByAsyncConsume() throws Exception {

      locator.setBlockOnNonDurableSend(true);
      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setDurable(false));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = session.createMessage(false);

      producer.send(message);

      ClientConsumer consumer = session.createConsumer(QUEUE, null, false);

      session.start();

      ClientMessage received = consumer.receiveImmediate();

      assertNotNull(received);

      received.acknowledge();

      final AtomicBoolean receivedAsync = new AtomicBoolean(false);

      consumer.setMessageHandler(message1 -> receivedAsync.set(true));

      Thread.sleep(1000);

      assertFalse(receivedAsync.get());

      session.close();
   }

   private void doConsumerReceiveImmediateWithNoMessages(final boolean browser) throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, false);

      session.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setDurable(false));

      ClientConsumer consumer = session.createConsumer(QUEUE, null, browser);
      session.start();

      ClientMessage message = consumer.receiveImmediate();
      assertNull(message);

      session.close();
   }

   private void doConsumerReceiveImmediate(final boolean browser) throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnAcknowledge(true).setAckBatchSize(0);

      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setDurable(false));

      ClientProducer producer = session.createProducer(ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE, null, browser);
      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receiveImmediate();
         assertNotNull(message2, "did not receive message " + i);
         assertEquals("m" + i, message2.getBodyBuffer().readString());
         if (!browser) {
            message2.acknowledge();
         }
      }

      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());

      assertNull(consumer.receiveImmediate());

      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());
      int messagesOnServer = browser ? numMessages : 0;
      assertEquals(messagesOnServer, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));

      consumer.close();

      session.close();
   }

}
