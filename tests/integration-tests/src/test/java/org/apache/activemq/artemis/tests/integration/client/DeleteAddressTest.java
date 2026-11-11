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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAddressTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   private void localServer(boolean autoCreate) throws Exception {
      server = createServer(false, true);

      AddressSettings settings = new AddressSettings().setAutoDeleteAddresses(autoCreate).setAutoCreateAddresses(autoCreate).setAutoCreateQueues(autoCreate).setAutoDeleteQueues(autoCreate).setDeadLetterAddress(SimpleString.of("DLQ")).setSendToDLAOnNoRoute(true);
      server.start();
      server.createQueue(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST));
      server.getAddressSettingsRepository().addMatch(getName() + "*", settings);
   }

   @Test
   public void testQueueNoAutoCreateCore() throws Exception {
      internalQueueTest("CORE", false);
   }

   @Test
   public void testQueueNoAutoCreateAMQP() throws Exception {
      internalQueueTest("AMQP", false);
   }

   @Test
   public void testQueueNoAutoCreateOpenWire() throws Exception {
      internalQueueTest("OPENWIRE", false);
   }


   @Test
   public void testQueueAutoCreateCore() throws Exception {
      internalQueueTest("CORE", true);
   }

   @Test
   public void testDeletoAutoCreateAMQP() throws Exception {
      internalQueueTest("AMQP", true);
   }

   @Test
   public void testQueueAutoCreateOpenWire() throws Exception {
      internalQueueTest("OPENWIRE", true);
   }

   public void internalQueueTest(String protocol, boolean autocreate) throws Exception {
      localServer(autocreate);

      String ADDRESS_NAME = getName() + protocol;

      if (!autocreate) {
         server.addAddressInfo(new AddressInfo(ADDRESS_NAME).addRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(ADDRESS_NAME).setRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      }


      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(ADDRESS_NAME);
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello"));
         session.commit();
         connection.start();

         try (MessageConsumer consumer = session.createConsumer(queue)) {
            logger.debug("Sending hello message");
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello", message.getText());
         }

         session.commit();

         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(ADDRESS_NAME);
         Wait.assertEquals(0, serverQueue::getConsumerCount);

         server.destroyQueue(SimpleString.of(ADDRESS_NAME));

         boolean exception = false;
         try {
            logger.debug("Sending good bye message");
            producer.send(session.createTextMessage("good bye"));
            session.commit();
            logger.debug("Exception was not captured, sent went fine");
         } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            exception = true;
         }

         if (!autocreate) {
            assertTrue(exception);
         }

         if (autocreate) {
            logger.debug("creating consumer");
            try (MessageConsumer consumer = session.createConsumer(queue)) {
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               assertEquals("good bye", message.getText());
            }
         } else {
            exception = false;
            logger.debug("Creating consumer, where an exception is expected");
            try (MessageConsumer consumer = session.createConsumer(queue)) {
            } catch (Exception e) {
               logger.debug("Received exception after createConsumer");
               exception = true;
            }
            assertTrue(exception);
         }
      }

      org.apache.activemq.artemis.core.server.Queue dlqServerQueue = server.locateQueue("DLQ");
      assertEquals(0, dlqServerQueue.getMessageCount());
   }

   @Test
   public void testTopicNoAutoCreateCore() throws Exception {
      internalMulticastTest("CORE", false);
   }

   @Test
   public void testTopicAutoCreateCore() throws Exception {
      internalMulticastTest("CORE", true);
   }

   @Test
   public void testTopicNoAutoCreateAMQP() throws Exception {
      internalMulticastTest("AMQP", false);
   }

   @Test
   public void testTopicAutoCreateAMQP() throws Exception {
      internalMulticastTest("AMQP", true);
   }

   @Test
   public void testTopicNoAutoCreateOPENWIRE() throws Exception {
      internalMulticastTest("OPENWIRE", false);
   }

   @Test
   public void testTopicAutoCreateOPENWIRE() throws Exception {
      internalMulticastTest("OPENWIRE", true);
   }

   public void internalMulticastTest(String protocol, boolean autocreate) throws Exception {
      localServer(autocreate);

      String ADDRESS_NAME = getName() + protocol + "_Topic";
      final String dlqText = "This should be in DLQ " + RandomUtil.randomUUIDString();

      if (!autocreate) {
         server.addAddressInfo(new AddressInfo(ADDRESS_NAME).addRoutingType(RoutingType.MULTICAST));
      }

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         connection.setClientID("client");
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic destination = session.createTopic(ADDRESS_NAME);

         TopicSubscriber consumer = session.createDurableSubscriber(destination, "subs1");

         MessageProducer producer = session.createProducer(destination);
         producer.send(session.createTextMessage("hello"));
         session.commit();
         connection.start();

         logger.debug("Sending hello message");
         TextMessage message = (TextMessage) consumer.receive(5000);
         assertNotNull(message);
         assertEquals("hello", message.getText());

         consumer.close();

         session.commit();

         Bindings bindings = server.getPostOffice().lookupBindingsForAddress(SimpleString.of(ADDRESS_NAME));
         for (Binding b : bindings.getBindings()) {
            if (b instanceof LocalQueueBinding localQueueBinding) {
               Wait.assertEquals(0, () -> localQueueBinding.getQueue().getConsumerCount());
               server.destroyQueue(b.getUniqueName());
            }
         }

         producer.send(session.createTextMessage(dlqText));
         session.commit();

         server.removeAddressInfo(SimpleString.of(ADDRESS_NAME), null);

         try {
            logger.debug("Sending good bye message");
            producer.send(session.createTextMessage("good bye"));
            logger.debug("Exception was not captured, sent went fine");
            if (!autocreate) {
               session.commit();
               fail("Exception was expected");
            } else {
               session.rollback();
            }
         } catch (Exception e) {
            logger.debug(e.getMessage(), e);
         }

         logger.debug("creating consumer");
         try (TopicSubscriber newSubs = session.createDurableSubscriber(destination, "second")) {
            if (!autocreate) {
               fail("exception was expected");
            }
         } catch (Exception expected) {
            logger.debug(expected.getMessage(), expected);
         }

         org.apache.activemq.artemis.core.server.Queue dlqServerQueue = server.locateQueue("DLQ");
         assertEquals(1, dlqServerQueue.getMessageCount());
      }

      try (Connection connection = factory.createConnection()) {
         connection.setClientID("client");
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         MessageConsumer dlqConsumer = session.createConsumer(session.createQueue("DLQ"));
         TextMessage dlqMessage = (TextMessage) dlqConsumer.receive(5000);
         assertNotNull(dlqMessage);
         assertEquals(dlqText, dlqMessage.getText());
         assertNull(dlqConsumer.receiveNoWait());
      }


   }
}
