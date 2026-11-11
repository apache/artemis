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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class MessageConcurrencyTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private final SimpleString ADDRESS = SimpleString.of("MessageConcurrencyTestAddress");

   private final SimpleString QUEUE_NAME = SimpleString.of("MessageConcurrencyTestQueue");

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);

      server.start();

      locator = createInVMNonHALocator();
   }

   // Test that a created message can be sent via multiple producers on different sessions concurrently
   @Test
   public void testMessageConcurrency() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession createSession = sf.createSession();

      Set<ClientSession> sendSessions = new HashSet<>();

      Set<Sender> senders = new HashSet<>();

      final int numSessions = 100;

      final int numMessages = 1000;

      for (int i = 0; i < numSessions; i++) {
         ClientSession sendSession = sf.createSession();

         sendSessions.add(sendSession);

         ClientProducer producer = sendSession.createProducer(ADDRESS);

         Sender sender = new Sender(numMessages, producer);

         senders.add(sender);

         sender.start();
      }

      for (int i = 0; i < numMessages; i++) {
         byte[] body = RandomUtil.randomBytes(1000);

         ClientMessage message = createSession.createMessage(false);

         message.getBodyBuffer().writeBytes(body);

         for (Sender sender : senders) {
            sender.queue.add(message);
         }
      }

      for (Sender sender : senders) {
         sender.join();

         assertFalse(sender.failed);
      }

      for (ClientSession sendSession : sendSessions) {
         sendSession.close();
      }

      createSession.close();

      sf.close();
   }

   // Test that a created message can be sent via multiple producers after being consumed from a single consumer
   @Test
   public void testMessageConcurrencyAfterConsumption() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession consumeSession = sf.createSession();

      final ClientProducer mainProducer = consumeSession.createProducer(ADDRESS);

      consumeSession.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(ADDRESS));

      ClientConsumer consumer = consumeSession.createConsumer(QUEUE_NAME);

      consumeSession.start();

      Set<ClientSession> sendSessions = new HashSet<>();

      final Set<Sender> senders = new HashSet<>();

      final int numSessions = 100;

      final int numMessages = 1000;

      for (int i = 0; i < numSessions; i++) {
         ClientSession sendSession = sf.createSession();

         sendSessions.add(sendSession);

         ClientProducer producer = sendSession.createProducer(ADDRESS);

         Sender sender = new Sender(numMessages, producer);

         senders.add(sender);

         sender.start();
      }

      consumer.setMessageHandler(message -> {
         for (Sender sender : senders) {
            sender.queue.add(message);
         }
      });

      for (int i = 0; i < numMessages; i++) {
         byte[] body = RandomUtil.randomBytes(1000);

         ClientMessage message = consumeSession.createMessage(false);

         message.getBodyBuffer().writeBytes(body);

         mainProducer.send(message);
      }

      for (Sender sender : senders) {
         sender.join();

         assertFalse(sender.failed);
      }

      for (ClientSession sendSession : sendSessions) {
         sendSession.close();
      }

      consumer.close();

      consumeSession.deleteQueue(QUEUE_NAME);

      consumeSession.close();

      sf.close();
   }

   private class Sender extends Thread {

      private final BlockingQueue<ClientMessage> queue = new LinkedBlockingQueue<>();

      private final ClientProducer producer;

      private final int numMessages;

      Sender(final int numMessages, final ClientProducer producer) {
         this.numMessages = numMessages;

         this.producer = producer;
      }

      volatile boolean failed;

      @Override
      public void run() {
         try {
            for (int i = 0; i < numMessages; i++) {
               ClientMessage msg = queue.take();

               producer.send(msg);
            }
         } catch (Exception e) {
            logger.error("Failed to send message", e);

            failed = true;
         }
      }
   }

}
