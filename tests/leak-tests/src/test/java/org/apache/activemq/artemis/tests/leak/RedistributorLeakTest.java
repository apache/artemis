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
package org.apache.activemq.artemis.tests.leak;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RedistributorLeakTest extends AbstractLeakTest {

   ActiveMQServer server;

   public void startServer() throws Exception {
      server = createServer(false, true);
      server.start();
   }

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      startServer();
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
      server = null;
   }

   @Test
   public void testRedistributor() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      final int NUMBER_OF_MESSAGES = 500;

      String addressName = "Queue" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      QueueImpl queue = (QueueImpl) server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(addressName));
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            Message message = session.createTextMessage("hello " + i);
            message.setJMSPriority(1 + (i % 5));
            producer.send(message);
         }
         session.commit();

         Destination jmsQueue = session.createQueue(addressName);

         connection.start();

         // creating one consumer per messages, just for part of the messages sent
         for (int i = 0; i < NUMBER_OF_MESSAGES / 10; i++) {
            MessageConsumer consumer = session.createConsumer(jmsQueue);
            Message message = consumer.receive(1000);
            assertNotNull(message);
            queue.flushExecutor();
            consumer.close();
         }
         session.rollback();
      }

      int numberOfIterators = checkLeak.getAllObjects(LinkedListImpl.Iterator.class).length;
      assertEquals(0, numberOfIterators);

      // Adding and cancelling a few redistributors
      for (int i = 0; i < 10; i++) {
         queue.addRedistributor(0);
         queue.flushExecutor();
         queue.cancelRedistributor();
         queue.flushExecutor();
      }

      numberOfIterators = checkLeak.getAllObjects(LinkedListImpl.Iterator.class).length;
      assertEquals(0, numberOfIterators, "Redistributors are leaking " + LinkedListImpl.Iterator.class.getName());

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Destination destination = session.createQueue(addressName);
         connection.start();
         MessageConsumer consumer = session.createConsumer(destination);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("hello " + i, message.getText());
         }
         session.commit();
      }

      assertEquals(0, checkLeak.getAllObjects(MessageReferenceImpl.class).length);
      assertEquals(0, checkLeak.getAllObjects(CoreMessage.class).length);
   }

}