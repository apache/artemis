/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.openwire;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Set;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VirtualTopicToFQQNOpenWireTest extends OpenWireTestBase {

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      Set<TransportConfiguration> acceptors = serverConfig.getAcceptorConfigurations();
      for (TransportConfiguration tc : acceptors) {
         if (tc.getName().equals("netty")) {
            tc.getExtraParams().put("virtualTopicConsumerWildcards", "Consumer.*.>;2,C.*.>;2;selectorAware=true");
            tc.getExtraParams().put("virtualTopicConsumerLruCacheMax", "10000");
         }
      }
      serverConfig.setJMXManagementEnabled(true);
   }

   @Test
   public void testAutoVirtualTopicFQQN() throws Exception {
      Connection connection = null;

      SimpleString topic = SimpleString.of("VirtualTopic.Orders");
      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateQueues(true);
      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateAddresses(true);
      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoDeleteQueues(false);

      try {
         ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(urlString);
         activeMQConnectionFactory.setWatchTopicAdvisories(false);
         connection = activeMQConnectionFactory.createConnection();
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createTopic(topic.toString());

         MessageConsumer messageConsumerA = session.createConsumer(session.createQueue("Consumer.A." + topic.toString()));
         MessageConsumer messageConsumerB = session.createConsumer(session.createQueue("Consumer.B." + topic.toString()));

         MessageProducer producer = session.createProducer(destination);
         TextMessage message = session.createTextMessage("This is a text message");
         producer.send(message);

         TextMessage messageReceivedA = (TextMessage) messageConsumerA.receive(2000);
         TextMessage messageReceivedB = (TextMessage) messageConsumerB.receive(2000);

         assertTrue((messageReceivedA != null && messageReceivedB != null));
         String text = messageReceivedA.getText();
         assertEquals("This is a text message", text);

         messageConsumerA.close();
         messageConsumerB.close();

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   public void testTwoTopicSubsSameNameAutoVirtualTopicFQQN() throws Exception {
      Connection connection = null;

      SimpleString topic1 = SimpleString.of("VirtualTopic.Orders1");
      SimpleString topic2 = SimpleString.of("VirtualTopic.Orders2");

      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateQueues(true);
      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateAddresses(true);

      try {
         ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(urlString);
         activeMQConnectionFactory.setWatchTopicAdvisories(false);
         connection = activeMQConnectionFactory.createConnection();
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination1 = session.createTopic(topic1.toString());
         Destination destination2 = session.createTopic(topic2.toString());

         MessageConsumer messageConsumer1 = session.createConsumer(session.createQueue("Consumer.A." + topic1.toString()));
         MessageConsumer messageConsumer2 = session.createConsumer(session.createQueue("Consumer.A." + topic2.toString()));

         MessageProducer producer = session.createProducer(null);
         TextMessage message = session.createTextMessage("This is a text message to 1");
         producer.send(destination1, message);
         message = session.createTextMessage("This is a text message to 2");
         producer.send(destination2, message);


         TextMessage messageReceived1 = (TextMessage) messageConsumer1.receive(2000);
         TextMessage messageReceived2 = (TextMessage) messageConsumer2.receive(2000);

         assertNotNull(messageReceived1);
         assertNotNull(messageReceived2);

         String text = messageReceived1.getText();
         assertEquals("This is a text message to 1", text);

         text = messageReceived2.getText();
         assertEquals("This is a text message to 2", text);

         messageConsumer1.close();
         messageConsumer2.close();

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }


   @Test
   public void testAutoVirtualTopicWildcardFQQN() throws Exception {
      Connection connection = null;

      SimpleString topicA = SimpleString.of("VirtualTopic.Orders.A");
      SimpleString topicB = SimpleString.of("VirtualTopic.Orders.B");
      SimpleString topic = SimpleString.of("VirtualTopic.Orders.>");

      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateQueues(true);
      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateAddresses(true);

      try {
         ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(urlString);
         activeMQConnectionFactory.setWatchTopicAdvisories(false);
         connection = activeMQConnectionFactory.createConnection();
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createTopic(topicA.toString() + "," + topicB.toString());

         MessageConsumer messageConsumerA = session.createConsumer(session.createQueue("Consumer.A." + topic.toString()));
        // MessageConsumer messageConsumerB = session.createConsumer(session.createQueue("Consumer.B." + topic.toString()));

         MessageProducer producer = session.createProducer(destination);
         TextMessage message = session.createTextMessage("This is a text message");
         producer.send(message);

         TextMessage messageReceivedA = (TextMessage) messageConsumerA.receive(2000);
         TextMessage messageReceivedB = (TextMessage) messageConsumerA.receive(2000);

         assertTrue((messageReceivedA != null && messageReceivedB != null));
         String text = messageReceivedA.getText();
         assertEquals("This is a text message", text);

         messageConsumerA.close();

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   public void testAutoVirtualTopicWildcardStarFQQN() throws Exception {
      Connection connection = null;

      SimpleString topicA = SimpleString.of("VirtualTopic.Orders.A");
      SimpleString topicB = SimpleString.of("VirtualTopic.Orders.B");
      SimpleString topic = SimpleString.of("VirtualTopic.Orders.*");

      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateQueues(true);
      this.server.getAddressSettingsRepository().getMatch("VirtualTopic.#").setAutoCreateAddresses(true);

      try {
         ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(urlString);
         activeMQConnectionFactory.setWatchTopicAdvisories(false);
         connection = activeMQConnectionFactory.createConnection();
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createTopic(topicA.toString() + "," + topicB.toString());

         MessageConsumer messageConsumerA = session.createConsumer(session.createQueue("Consumer.A." + topic.toString()));

         MessageProducer producer = session.createProducer(destination);
         TextMessage message = session.createTextMessage("This is a text message");
         producer.send(message);

         TextMessage messageReceivedA = (TextMessage) messageConsumerA.receive(2000);
         TextMessage messageReceivedB = (TextMessage) messageConsumerA.receive(2000);

         assertTrue((messageReceivedA != null && messageReceivedB != null));
         String text = messageReceivedA.getText();
         assertEquals("This is a text message", text);

         messageConsumerA.close();

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }


   @Test
   public void testSelectorAwareVT() throws Exception {
      Connection connection = null;

      SimpleString topic = SimpleString.of("SVT.Orders.A");

      this.server.getAddressSettingsRepository().getMatch("SVT.#").setAutoCreateQueues(true);
      this.server.getAddressSettingsRepository().getMatch("SVT.#").setAutoCreateAddresses(true);

      try {
         ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(urlString);
         activeMQConnectionFactory.setWatchTopicAdvisories(false);
         connection = activeMQConnectionFactory.createConnection();
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createTopic(topic.toString());

         MessageConsumer messageConsumerA = session.createConsumer(session.createQueue("C.A." + topic.toString()), "stuff = 'A'");
         MessageConsumer messageConsumerB = session.createConsumer(session.createQueue("C.B." + topic.toString()), "stuff = 'B'");

         MessageProducer producer = session.createProducer(destination);
         TextMessage message = session.createTextMessage("This is a text message");
         for (String stuffValue : new String[] {"A", "B", "C"}) {
            message.setStringProperty("stuff", stuffValue);
            producer.send(message);
         }

         TextMessage messageReceivedA = (TextMessage) messageConsumerA.receive(2000);
         TextMessage messageReceivedB = (TextMessage) messageConsumerB.receive(2000);

         assertTrue((messageReceivedA != null && messageReceivedB != null));
         String text = messageReceivedA.getText();
         assertEquals("This is a text message", text);

         assertEquals("A", messageReceivedA.getStringProperty("stuff"));
         assertEquals("B", messageReceivedB.getStringProperty("stuff"));

         // verify C message got dropped

         final QueueControl queueControlA = ManagementControlHelper.createQueueControl(topic, SimpleString.of("C.A." + topic.toString()), RoutingType.MULTICAST, mbeanServer);
         Wait.assertEquals(0, () -> queueControlA.countMessages());

         final QueueControl queueControlB = ManagementControlHelper.createQueueControl(topic, SimpleString.of("C.B." + topic.toString()), RoutingType.MULTICAST, mbeanServer);
         Wait.assertEquals(0, () -> queueControlB.countMessages());

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

}
