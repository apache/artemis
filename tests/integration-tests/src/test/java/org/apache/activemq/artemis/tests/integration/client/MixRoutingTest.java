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

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.SingleServerTestBase;
import org.junit.jupiter.api.Test;

public class MixRoutingTest extends SingleServerTestBase {

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(false, createDefaultNettyConfig());
   }

   @Test
   public void testMix() throws Exception {
      SimpleString queueName = SimpleString.of(getName());
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TemporaryQueue temporaryQueue = session.createTemporaryQueue();
      Queue queue = session.createQueue(queueName.toString());

      MessageProducer prodTemp = session.createProducer(temporaryQueue);
      MessageProducer prodQueue = session.createProducer(queue);

      final int NMESSAGES = 100;

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage tmpMessage = session.createTextMessage("tmp");
         tmpMessage.setIntProperty("i", i);
         TextMessage permanent = session.createTextMessage("permanent");
         permanent.setIntProperty("i", i);
         prodQueue.send(permanent);
         prodTemp.send(tmpMessage);
      }

      MessageConsumer consumerTemp = session.createConsumer(temporaryQueue);
      MessageConsumer consumerQueue = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage tmpMessage = (TextMessage) consumerTemp.receive(5000);
         TextMessage permanent = (TextMessage) consumerQueue.receive(5000);
         assertNotNull(tmpMessage);
         assertNotNull(permanent);
         assertEquals("tmp", tmpMessage.getText());
         assertEquals("permanent", permanent.getText());
         assertEquals(i, tmpMessage.getIntProperty("i"));
         assertEquals(i, permanent.getIntProperty("i"));
      }

      assertNull(consumerQueue.receiveNoWait());
      assertNull(consumerTemp.receiveNoWait());
      connection.close();
      factory.close();
   }

   @Test
   public void testMix2() throws Exception {
      server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = session.createQueue(getName());

      MessageProducer prodQueue = session.createProducer(queue);

      final int NMESSAGES = 100;

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage permanent = session.createTextMessage("permanent");
         permanent.setIntProperty("i", i);
         prodQueue.send(permanent);
      }

      TemporaryQueue temporaryQueue = session.createTemporaryQueue();
      MessageProducer prodTemp = session.createProducer(temporaryQueue);

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage tmpMessage = session.createTextMessage("tmp");
         tmpMessage.setIntProperty("i", i);
         prodTemp.send(tmpMessage);
      }

      MessageConsumer consumerTemp = session.createConsumer(temporaryQueue);
      MessageConsumer consumerQueue = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage tmpMessage = (TextMessage) consumerTemp.receive(5000);
         TextMessage permanent = (TextMessage) consumerQueue.receive(5000);
         assertNotNull(tmpMessage);
         assertNotNull(permanent);
         assertEquals("tmp", tmpMessage.getText());
         assertEquals("permanent", permanent.getText());
         assertEquals(i, tmpMessage.getIntProperty("i"));
         assertEquals(i, permanent.getIntProperty("i"));
      }

      assertNull(consumerQueue.receiveNoWait());
      assertNull(consumerTemp.receiveNoWait());
      connection.close();
      factory.close();
   }

   @Test
   public void testMixWithTopics() throws Exception {
      SimpleString queueName = SimpleString.of(getName());
      SimpleString topicName = SimpleString.of("topic" + getName());
      AddressInfo info = new AddressInfo(topicName, RoutingType.MULTICAST);
      server.addAddressInfo(info);
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = session.createQueue(queueName.toString());
      Topic topic = session.createTopic(topicName.toString());

      MessageProducer prodQueue = session.createProducer(queue);
      MessageProducer prodTopic = session.createProducer(topic);

      final int NMESSAGES = 10;

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage topicMessage = session.createTextMessage("topic");
         topicMessage.setIntProperty("i", i);
         TextMessage permanent = session.createTextMessage("permanent");
         permanent.setIntProperty("i", i);
         prodQueue.send(permanent);
         prodTopic.send(topicMessage);
      }

      MessageConsumer consumerQueue = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < NMESSAGES; i++) {
         TextMessage permanent = (TextMessage) consumerQueue.receive(5000);
         assertNotNull(permanent);
         assertEquals("permanent", permanent.getText());
         assertEquals(i, permanent.getIntProperty("i"));
      }

      assertNull(consumerQueue.receiveNoWait());
      connection.close();
      factory.close();
   }

}
