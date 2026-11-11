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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageConsumerRollbackTest extends ActiveMQTestBase {

   ActiveMQServer server;

   ServerLocator locator;

   ClientSessionFactory sf;

   private static final String inQueue = "inqueue";

   private static final String outQueue = "outQueue";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(true, true);

      AddressSettings settings = new AddressSettings().setRedeliveryDelay(100);
      server.getConfiguration().getAddressSettings().put("#", settings);

      server.start();

      locator = createNettyNonHALocator();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createTransactedSession();

      session.createQueue(QueueConfiguration.of(inQueue));

      session.createQueue(QueueConfiguration.of(outQueue));

      session.close();
   }



   @Test
   public void testRollbackMultipleConsumers() throws Exception {

      int numberOfMessages = 3000;
      int numberOfConsumers = 10;

      ClientSession session = sf.createTransactedSession();

      sendMessages(numberOfMessages, session);

      AtomicInteger count = new AtomicInteger(0);
      CountDownLatch commitLatch = new CountDownLatch(numberOfMessages);

      LocalConsumer[] consumers = new LocalConsumer[numberOfConsumers];

      for (int i = 0; i < numberOfConsumers; i++) {
         consumers[i] = new LocalConsumer(count, commitLatch);
         consumers[i].start();
      }

      commitLatch.await(2, TimeUnit.MINUTES);

      for (LocalConsumer consumer : consumers) {
         consumer.stop();
      }

      ClientConsumer consumer = session.createConsumer(outQueue);

      session.start();

      Set<Integer> values = new HashSet<>();

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = consumer.receive(1000);
         assertNotNull(msg);
         int value = msg.getIntProperty("out_msg");
         msg.acknowledge();
         assertFalse(values.contains(value), "msg " + value + " received in duplicate");
         values.add(value);
      }

      assertNull(consumer.receiveImmediate());

      for (int i = 0; i < numberOfMessages; i++) {
         assertTrue(values.contains(i));
      }

      assertEquals(numberOfMessages, values.size());

      session.close();

   }

   private void sendMessages(int numberOfMessages, ClientSession session) throws Exception {
      ClientProducer producer = session.createProducer(inQueue);

      for (int i = 0; i < numberOfMessages; i++) {
         ActiveMQTextMessage txt = new ActiveMQTextMessage(session);
         txt.setIntProperty("msg", i);
         txt.setText("Message Number (" + i + ")");
         txt.doBeforeSend();
         producer.send(txt.getCoreMessage());
      }

      session.commit();
   }

   private class LocalConsumer implements MessageHandler {

      // One of the tests will need this
      boolean rollbackFirstMessage = true;

      ServerLocator consumerLocator;

      ClientSessionFactory factoryLocator;

      ClientSession session;

      ClientConsumer consumer;

      ClientProducer producer;

      AtomicInteger counter;

      CountDownLatch commitLatch;

      private LocalConsumer(AtomicInteger counter, CountDownLatch commitLatch) {
         this.counter = counter;
         this.commitLatch = commitLatch;
      }

      public void stop() throws Exception {
         session.close();
         factoryLocator.close();
         consumerLocator.close();
      }

      public void start() throws Exception {
         consumerLocator = createNettyNonHALocator();

         factoryLocator = createSessionFactory(consumerLocator);

         session = factoryLocator.createTransactedSession();

         consumer = session.createConsumer(inQueue);

         producer = session.createProducer(outQueue);

         consumer.setMessageHandler(this);

         session.start();
      }

      @Override
      public void onMessage(ClientMessage message) {

         try {

            message.acknowledge();
            ClientMessage outmsg = session.createMessage(true);

            outmsg.putIntProperty("out_msg", message.getIntProperty("msg"));

            producer.send(outmsg);

            if (rollbackFirstMessage) {
               session.rollback();
               rollbackFirstMessage = false;
               return;
            }

            if (counter.incrementAndGet() % 200 == 0) {
               session.rollback();
            } else {
               commitLatch.countDown();
               session.commit();
            }
         } catch (Exception e) {
            e.printStackTrace();
            try {
               session.rollback();
            } catch (Exception ignored) {
               ignored.printStackTrace();
            }
         }

      }
   }
}
