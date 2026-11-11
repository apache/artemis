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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageGroupingConnectionFactoryTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSession clientSession;

   private final SimpleString qName = SimpleString.of("MessageGroupingTestQueue");

   @Test
   public void testBasicGroupingUsingConnection() throws Exception {
      doTestBasicGroupingUsingConnectionFactory();
   }

   @Test
   public void testBasicGroupingMultipleProducers() throws Exception {
      doTestBasicGroupingMultipleProducers();
   }

   private void doTestBasicGroupingUsingConnectionFactory() throws Exception {
      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         clientProducer.send(message);
      }
      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(100, dummyMessageHandler.list.size());
      assertEquals(0, dummyMessageHandler2.list.size());
      consumer.close();
      consumer2.close();
   }

   private void doTestBasicGroupingMultipleProducers() throws Exception {
      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientProducer clientProducer2 = clientSession.createProducer(qName);
      ClientProducer clientProducer3 = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         clientProducer.send(message);
         clientProducer2.send(message);
         clientProducer3.send(message);
      }
      CountDownLatch latch = new CountDownLatch(numMessages * 3);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(300, dummyMessageHandler.list.size());
      assertEquals(0, dummyMessageHandler2.list.size());
      consumer.close();
      consumer2.close();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
      ServerLocator locator = createInVMNonHALocator().setGroupID("grp1");
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = addClientSession(sessionFactory.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
   }

   private static class DummyMessageHandler implements MessageHandler {

      List<ClientMessage> list = new ArrayList<>();

      private final CountDownLatch latch;

      private final boolean acknowledge;

      private DummyMessageHandler(final CountDownLatch latch, final boolean acknowledge) {
         this.latch = latch;
         this.acknowledge = acknowledge;
      }

      @Override
      public void onMessage(final ClientMessage message) {
         list.add(message);
         if (acknowledge) {
            try {
               message.acknowledge();
            } catch (ActiveMQException e) {
               // ignore
            }
         }
         latch.countDown();
      }
   }
}
