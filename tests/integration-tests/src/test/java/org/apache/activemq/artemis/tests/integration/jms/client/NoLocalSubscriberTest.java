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
package org.apache.activemq.artemis.tests.integration.jms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class NoLocalSubscriberTest extends JMSTestBase {

   /**
    * Test that a message created from the same connection than a nolocal consumer can be sent by *another* connection
    * and will be received by the nolocal consumer
    */
   @Test
   public void testNoLocal() throws Exception {

      Connection defaultConn = null;
      Connection newConn = null;

      try {
         Topic topic1 = createTopic("topic1");
         defaultConn = cf.createConnection();
         Session defaultSess = defaultConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer defaultConsumer = defaultSess.createConsumer(topic1);
         MessageConsumer noLocalConsumer = defaultSess.createConsumer(topic1, null, true);
         MessageProducer defaultProd = defaultSess.createProducer(topic1);

         defaultConn.start();

         String text = RandomUtil.randomUUIDString();
         // message is created only once from the same connection than the noLocalConsumer
         TextMessage messageSent = defaultSess.createTextMessage(text);
         for (int i = 0; i < 10; i++) {
            defaultProd.send(messageSent);
         }

         Message received = null;
         for (int i = 0; i < 10; i++) {
            received = defaultConsumer.receive(5000);
            assertNotNull(received);
            assertEquals(text, ((TextMessage) received).getText());
         }

         newConn = cf.createConnection();
         Session newSession = newConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer newProd = newSession.createProducer(topic1);
         MessageConsumer newConsumer = newSession.createConsumer(topic1);

         newConn.start();

         text = RandomUtil.randomUUIDString();
         messageSent.setText(text);
         defaultProd.send(messageSent);

         received = newConsumer.receive(5000);
         assertNotNull(received);
         assertEquals(text, ((TextMessage) received).getText());

         text = RandomUtil.randomUUIDString();
         messageSent.setText(text);
         // we send the message created at the start of the test but on the *newConn* this time
         newProd.send(messageSent);
         newConn.close();

         received = noLocalConsumer.receive(5000);
         assertNotNull(received, "nolocal consumer did not get message");
         assertEquals(text, ((TextMessage) received).getText());
      } finally {
         if (defaultConn != null) {
            defaultConn.close();
         }
         if (newConn != null) {
            newConn.close();
         }
      }
   }

   @Test
   public void testNoLocalReconnect() throws Exception {
      ConnectionFactory connectionFactory = cf;
      String uniqueID = Long.toString(System.currentTimeMillis());
      String topicName = "exampleTopic";
      String clientID = "myClientID_" + uniqueID;
      String subscriptionName = "mySub_" + uniqueID;

      boolean noLocal = true;
      String messageSelector = "";
      Topic topic = createTopic(topicName);

      {
         // Create durable subscription
         Connection connection = connectionFactory.createConnection("guest", "guest");
         connection.setClientID(clientID);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSubscriber topicSubscriber = session.createDurableSubscriber(topic, subscriptionName, messageSelector, noLocal);
         topicSubscriber.close();
         connection.close();
      }

      {
         // create a connection using the same client ID and send a message
         // to the topic
         // this will not be added to the durable subscription
         Connection connection = connectionFactory.createConnection("guest", "guest");
         connection.setClientID(clientID);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer messageProducer = session.createProducer(topic);
         TextMessage textMessage = session.createTextMessage("M3");
         messageProducer.send(textMessage);
         connection.close();
      }

      {
         // create a connection using a different client ID and send a
         // message to the topic
         // this will be added to the durable subscription
         Connection connection = connectionFactory.createConnection("guest", "guest");
         connection.setClientID(clientID + "_different");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer messageProducer = session.createProducer(topic);
         TextMessage textMessage = session.createTextMessage("M4");
         messageProducer.send(textMessage);
         connection.close();
      }

      {
         Connection connection = connectionFactory.createConnection("guest", "guest");
         connection.setClientID(clientID);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSubscriber topicSubscriber = session.createDurableSubscriber(topic, subscriptionName, messageSelector, noLocal);
         connection.start();

         // now drain the subscription
         // we should not receive message M3, but we should receive message M4
         // However for some reason Artemis doesn't receive either
         TextMessage textMessage = (TextMessage) topicSubscriber.receive(1000);
         assertNotNull(textMessage);

         assertEquals("M4", textMessage.getText());

         assertNull(topicSubscriber.receiveNoWait());

         connection.close();
      }
   }

   @Test
   public void testNoLocalReconnect2() throws Exception {

      ConnectionFactory connectionFactory = cf;
      String uniqueID = Long.toString(System.currentTimeMillis());
      String topicName = "exampleTopic";
      String clientID = "myClientID_" + uniqueID;
      String subscriptionName = "mySub_" + uniqueID;

      boolean noLocal = true;
      String messageSelector = "";
      Topic topic = createTopic(topicName);

      Connection originalConnection;

      {
         // Create durable subscription
         originalConnection = connectionFactory.createConnection("guest", "guest");
         originalConnection.setClientID(clientID);
         Session session = originalConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSubscriber topicSubscriber = session.createDurableSubscriber(topic, subscriptionName, messageSelector, noLocal);
         topicSubscriber.close();
         session.close();
      }

      {
         // create a connection using the same client ID and send a message
         // to the topic
         // this will not be added to the durable subscription
         Session session = originalConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer messageProducer = session.createProducer(topic);
         messageProducer.send(session.createTextMessage("M3"));
         session.close();
      }

      {
         // create a connection using a different client ID and send a
         // message to the topic
         // this will be added to the durable subscription
         Connection connection = connectionFactory.createConnection("guest", "guest");
         connection.setClientID(clientID + "_different");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer messageProducer = session.createProducer(topic);
         messageProducer.send(session.createTextMessage("M4"));
         connection.close();
      }

      {
         Session session = originalConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TopicSubscriber topicSubscriber = session.createDurableSubscriber(topic, subscriptionName, messageSelector, noLocal);
         originalConnection.start();

         // now drain the subscription
         // we should not receive message M3, but we should receive message M4
         // However for some reason Artemis doesn't receive either
         TextMessage textMessage = (TextMessage) topicSubscriber.receive(1000);
         assertNotNull(textMessage);

         assertEquals("M4", textMessage.getText());

         assertNull(topicSubscriber.receiveNoWait());

         originalConnection.close();
      }
   }

}
