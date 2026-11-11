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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionSendAcknowledgementHandlerTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private final SimpleString address = SimpleString.of("address");

   private final SimpleString queueName = SimpleString.of("queue");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);
      server.start();
   }

   @Test
   public void testSetInvalidSendACK() throws Exception {
      ServerLocator locator = createInVMNonHALocator();

      locator.setConfirmationWindowSize(-1);

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession(null, null, false, true, true, false, 1);

      boolean failed = false;
      try {
         session.setSendAcknowledgementHandler(message -> {
         });
      } catch (Throwable expected) {
         failed = true;
      }

      assertTrue(failed, "Expected a failure on setting ACK Handler");

      session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
   }

   @Test
   public void testSendAcknowledgementsNoWindowSize() throws Exception {
      verifySendAcknowledgements(0);
   }

   @Test
   public void testSendAcknowledgements() throws Exception {
      verifySendAcknowledgements(1024);
   }

   @Test
   public void testSendAcknowledgementsNoWindowSizeProducerOnly() throws Exception {
      verifySendAcknowledgementsProducerOnly(0);
   }

   @Test
   public void testSendAcknowledgementsProducer() throws Exception {
      verifySendAcknowledgementsProducerOnly(1024);
   }

   public void verifySendAcknowledgements(int windowSize) throws Exception {
      ServerLocator locator = createInVMNonHALocator();

      locator.setConfirmationWindowSize(windowSize);

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession(null, null, false, true, true, false, 1);

      session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));

      ClientProducer prod = session.createProducer(address);

      final int numMessages = 1000;

      LatchAckHandler handler = new LatchAckHandler("session", new CountDownLatch(numMessages));

      LatchAckHandler producerHandler = new LatchAckHandler("producer", new CountDownLatch(numMessages));

      session.setSendAcknowledgementHandler(handler);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage msg = session.createMessage(false);
         ClientMessage msg2 = session.createMessage(false);

         prod.send(msg);
         prod.send(address, msg2, producerHandler);
      }

      assertTrue(handler.latch.await(5, TimeUnit.SECONDS), "session must have acked, " + handler);
      assertTrue(producerHandler.latch.await(5, TimeUnit.SECONDS), "producer specific handler must have acked, " + producerHandler);
   }

   public void verifySendAcknowledgementsProducerOnly(int windowSize) throws Exception {
      ServerLocator locator = createInVMNonHALocator();

      locator.setConfirmationWindowSize(windowSize);

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession(null, null, false, true, true, false, 1);

      session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));

      ClientProducer prod = session.createProducer(address);

      final int numMessages = 1000;

      LatchAckHandler producerHandler = new LatchAckHandler("producer", new CountDownLatch(numMessages));

      for (int i = 0; i < numMessages; i++) {
         ClientMessage msg2 = session.createMessage(false);

         prod.send(address, msg2, producerHandler);
      }

      assertTrue(producerHandler.latch.await(5, TimeUnit.SECONDS), "producer specific handler must have acked, " + producerHandler);
   }

   @Test
   public void testHandlerOnSend() throws Exception {
      final int MSG_COUNT = 750;
      ServerLocator locator = createInVMNonHALocator();
      locator.setConfirmationWindowSize(256);

      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession();
      ClientProducer producer = session.createProducer(address);
      final AtomicInteger count = new AtomicInteger(0);
      for (int i = 0; i < MSG_COUNT; i++) {
         ClientMessage message = session.createMessage(true);
         producer.send(message, message1 -> count.incrementAndGet());
      }
      Wait.assertEquals(MSG_COUNT, () -> count.get(), 2000, 100);
   }

   @Test
   public void testHandlerOnSendWithAnonymousProducer() throws Exception {
      final int MSG_COUNT = 750;
      ServerLocator locator = createInVMNonHALocator();
      locator.setConfirmationWindowSize(256);

      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession();
      final AtomicInteger count = new AtomicInteger(0);
      ClientProducer producer = session.createProducer();
      for (int i = 0; i < MSG_COUNT; i++) {
         ClientMessage message = session.createMessage(true);
         producer.send(address, message, message1 -> count.incrementAndGet());
      }
      Wait.assertEquals(MSG_COUNT, () -> count.get(), 2000, 100);
   }

   @Test
   public void testHandlerOnSession() throws Exception {
      final int MSG_COUNT = 750;
      ServerLocator locator = createInVMNonHALocator();
      locator.setConfirmationWindowSize(256);

      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession();
      final AtomicInteger count = new AtomicInteger(0);
      session.setSendAcknowledgementHandler(message1 -> count.incrementAndGet());
      ClientProducer producer = session.createProducer(address);
      for (int i = 0; i < MSG_COUNT; i++) {
         ClientMessage message = session.createMessage(true);
         producer.send(message);
      }
      Wait.assertEquals(MSG_COUNT, () -> count.get(), 2000, 100);
   }

   @Test
   public void testHandlerOnSessionWithAnonymousProducer() throws Exception {
      final int MSG_COUNT = 750;
      ServerLocator locator = createInVMNonHALocator();
      locator.setConfirmationWindowSize(256);

      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession();
      final AtomicInteger count = new AtomicInteger(0);
      session.setSendAcknowledgementHandler(message1 -> count.incrementAndGet());
      ClientProducer producer = session.createProducer();
      for (int i = 0; i < MSG_COUNT; i++) {
         ClientMessage message = session.createMessage(true);
         producer.send(address, message);
      }
      Wait.assertEquals(MSG_COUNT, () -> count.get(), 2000, 100);
   }

   public static final class LatchAckHandler implements SendAcknowledgementHandler {

      public CountDownLatch latch;
      private final String name;

      public LatchAckHandler(String name, CountDownLatch latch) {
         this.name = name;
         this.latch = latch;
      }

      @Override
      public void sendAcknowledged(Message message) {
         latch.countDown();
      }

      @Override
      public String toString() {
         return "SendAckHandler(name=" + name + ", latch=" + latch + ")";
      }
   }
}
