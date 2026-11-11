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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeliveryOrderTest extends ActiveMQTestBase {

   public final SimpleString addressA = SimpleString.of("addressA");

   public final SimpleString queueA = SimpleString.of("queueA");

   public final SimpleString queueB = SimpleString.of("queueB");

   public final SimpleString queueC = SimpleString.of("queueC");

   private ServerLocator locator;

   private ActiveMQServer server;

   private ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      server = createServer(false);
      server.start();
      cf = createSessionFactory(locator);
   }

   @Test
   public void testSendDeliveryOrderOnCommit() throws Exception {
      ClientSession sendSession = cf.createSession(false, false, true);
      ClientProducer cp = sendSession.createProducer(addressA);
      int numMessages = 1000;
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = sendSession.createMessage(false);
         cm.getBodyBuffer().writeInt(i);
         cp.send(cm);
         if (i % 10 == 0) {
            sendSession.commit();
         }
         sendSession.commit();
      }
      ClientConsumer c = sendSession.createConsumer(queueA);
      sendSession.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = c.receive(5000);
         assertNotNull(cm);
         assertEquals(i, cm.getBodyBuffer().readInt());
      }
      sendSession.close();
   }

   @Test
   public void testReceiveDeliveryOrderOnRollback() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, false);
      ClientProducer cp = sendSession.createProducer(addressA);
      int numMessages = 1000;
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = sendSession.createMessage(false);
         cm.getBodyBuffer().writeInt(i);
         cp.send(cm);
      }
      ClientConsumer c = sendSession.createConsumer(queueA);
      sendSession.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = c.receive(5000);
         assertNotNull(cm);
         cm.acknowledge();
         assertEquals(i, cm.getBodyBuffer().readInt());
      }
      sendSession.rollback();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage cm = c.receive(5000);
         assertNotNull(cm);
         cm.acknowledge();
         assertEquals(i, cm.getBodyBuffer().readInt());
      }
      sendSession.close();
   }

   @Test
   public void testMultipleConsumersMessageOrder() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientSession recSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      int numReceivers = 100;
      AtomicInteger count = new AtomicInteger(0);
      int numMessage = 10000;
      ClientConsumer[] clientConsumers = new ClientConsumer[numReceivers];
      Receiver[] receivers = new Receiver[numReceivers];
      CountDownLatch latch = new CountDownLatch(numMessage);
      for (int i = 0; i < numReceivers; i++) {
         clientConsumers[i] = recSession.createConsumer(queueA);
         receivers[i] = new Receiver(latch);
         clientConsumers[i].setMessageHandler(receivers[i]);
      }
      recSession.start();
      ClientProducer clientProducer = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessage; i++) {
         ClientMessage cm = sendSession.createMessage(false);
         cm.getBodyBuffer().writeInt(count.getAndIncrement());
         clientProducer.send(cm);
      }
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      for (Receiver receiver : receivers) {
         assertFalse(receiver.failed, "" + receiver.lastMessage);
      }
      sendSession.close();
      recSession.close();
   }

   class Receiver implements MessageHandler {

      final CountDownLatch latch;

      int lastMessage = -1;

      boolean failed = false;

      Receiver(final CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void onMessage(final ClientMessage message) {
         int i = message.getBodyBuffer().readInt();
         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            e.printStackTrace();
         }
         if (i <= lastMessage) {
            failed = true;
         }
         lastMessage = i;
         latch.countDown();
      }

   }

}
