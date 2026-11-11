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

import java.util.concurrent.CountDownLatch;

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

public class AutogroupIdTest extends ActiveMQTestBase {


   public final SimpleString addressA = SimpleString.of("addressA");

   public final SimpleString queueA = SimpleString.of("queueA");

   public final SimpleString queueB = SimpleString.of("queueB");

   public final SimpleString queueC = SimpleString.of("queueC");

   private final SimpleString groupTestQ = SimpleString.of("testGroupQueue");

   private ActiveMQServer server;

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);

      server.start();

      waitForServerToStart(server);

      locator = createInVMNonHALocator();
   }

   /**
    * Tests when the autogroupid is set only 1 consumer (out of 2) gets all the messages from a single producer.
    */
   @Test
   public void testGroupIdAutomaticallySet() throws Exception {
      locator.setAutoGroup(true);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(groupTestQ).setDurable(false));

      ClientProducer producer = session.createProducer(groupTestQ);

      final CountDownLatch latch = new CountDownLatch(100);

      MyMessageHandler myMessageHandler = new MyMessageHandler(latch);
      MyMessageHandler myMessageHandler2 = new MyMessageHandler(latch);

      ClientConsumer consumer = session.createConsumer(groupTestQ);
      consumer.setMessageHandler(myMessageHandler);
      ClientConsumer consumer2 = session.createConsumer(groupTestQ);
      consumer2.setMessageHandler(myMessageHandler2);

      session.start();

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         producer.send(session.createMessage(false));
      }
      waitForLatch(latch);

      session.close();

      assertEquals(100, myMessageHandler.messagesReceived);
      assertEquals(0, myMessageHandler2.messagesReceived);

   }

   /**
    * Tests when the autogroupid is set only 2 consumers (out of 3) gets all the messages from 2 producers.
    */
   @Test
   public void testGroupIdAutomaticallySetMultipleProducers() throws Exception {
      locator.setAutoGroup(true);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(groupTestQ).setDurable(false));

      ClientProducer producer = session.createProducer(groupTestQ);
      ClientProducer producer2 = session.createProducer(groupTestQ);

      final CountDownLatch latch = new CountDownLatch(200);

      MyMessageHandler myMessageHandler = new MyMessageHandler(latch);
      MyMessageHandler myMessageHandler2 = new MyMessageHandler(latch);
      MyMessageHandler myMessageHandler3 = new MyMessageHandler(latch);

      ClientConsumer consumer = session.createConsumer(groupTestQ);
      consumer.setMessageHandler(myMessageHandler);
      ClientConsumer consumer2 = session.createConsumer(groupTestQ);
      consumer2.setMessageHandler(myMessageHandler2);
      ClientConsumer consumer3 = session.createConsumer(groupTestQ);
      consumer3.setMessageHandler(myMessageHandler3);

      session.start();

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         producer.send(session.createMessage(false));
      }
      for (int i = 0; i < numMessages; i++) {
         producer2.send(session.createMessage(false));
      }
      waitForLatch(latch);

      session.close();

      assertEquals(100, myMessageHandler.messagesReceived);
      assertEquals(100, myMessageHandler2.messagesReceived);
      assertEquals(0, myMessageHandler3.messagesReceived);
   }

   /**
    * Tests that even though we have a grouping round robin distributor we don't pin the consumer as autogroup is false.
    */
   @Test
   public void testGroupIdAutomaticallyNotSet() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(groupTestQ).setDurable(false));

      ClientProducer producer = session.createProducer(groupTestQ);

      final CountDownLatch latch = new CountDownLatch(100);

      MyMessageHandler myMessageHandler = new MyMessageHandler(latch);
      MyMessageHandler myMessageHandler2 = new MyMessageHandler(latch);

      ClientConsumer consumer = session.createConsumer(groupTestQ);
      consumer.setMessageHandler(myMessageHandler);
      ClientConsumer consumer2 = session.createConsumer(groupTestQ);
      consumer2.setMessageHandler(myMessageHandler2);

      session.start();

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         producer.send(session.createMessage(false));
      }
      waitForLatch(latch);

      session.close();

      assertEquals(50, myMessageHandler.messagesReceived);
      assertEquals(50, myMessageHandler2.messagesReceived);

   }

   private static class MyMessageHandler implements MessageHandler {

      int messagesReceived = 0;

      private final CountDownLatch latch;

      private MyMessageHandler(final CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void onMessage(final ClientMessage message) {
         messagesReceived++;
         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            e.printStackTrace();
         }
         latch.countDown();
      }
   }
}
