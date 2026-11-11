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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LVQTest extends JMSTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }


   protected ConnectionFactory getCF() throws Exception {
      return cf;
   }

   @Test
   public void testLVQandNonDestructive() throws Exception {
      ActiveMQConnectionFactory fact = (ActiveMQConnectionFactory) getCF();
      fact.setConsumerWindowSize(0);

      try (Connection connection = fact.createConnection();
           Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)) {

         // swapping these two lines makes the test either succeed for fail
         // Queue queue = session.createQueue("random?last-value=true");
         Queue queue = session.createQueue("random?last-value=true&non-destructive=true");

         MessageProducer producer = session.createProducer(queue);
         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();

         TextMessage message = session.createTextMessage();
         message.setText("Message 1");
         message.setStringProperty(Message.HDR_LAST_VALUE_NAME.toString(), "A");
         producer.send(message);

         TextMessage tm = (TextMessage) consumer.receive(2000);
         assertNotNull(tm);
         tm.acknowledge();

         Thread.sleep(1000);
         assertEquals("Message 1", tm.getText());

         message = session.createTextMessage();
         message.setText("Message 2");
         message.setStringProperty(Message.HDR_LAST_VALUE_NAME.toString(), "A");
         producer.send(message);

         tm = (TextMessage) consumer.receive(2000);
         assertNotNull(tm);
         assertEquals("Message 2", tm.getText());

         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue("random");
         // one message on the queue and one in delivery - the same message if it's an LVQ
         // LVQ getMessageCount will discount!
         Wait.assertEquals(1, serverQueue::getMessageCount);
      }

      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue("random");
      Wait.assertEquals(1, serverQueue::getMessageCount);

      serverQueue.deleteMatchingReferences(null);
      // This should be removed all
      assertEquals(0, serverQueue.getMessageCount());

   }

   @Test
   public void testLastValueQueueUsingAddressQueueParameters() throws Exception {
      ActiveMQConnectionFactory fact = (ActiveMQConnectionFactory) getCF();

      //Set the consumer window size to 0 to not buffer any messages client side.
      fact.setConsumerWindowSize(0);
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue("random?last-value=true");
         assertEquals("random", queue.getQueueName());

         ActiveMQDestination a = (ActiveMQDestination) queue;
         assertTrue(a.getQueueAttributes().getLastValue());
         assertTrue(a.getQueueConfiguration().isLastValue());

         MessageProducer producer = session.createProducer(queue);
         MessageConsumer consumer1 = session.createConsumer(queue);

         connection.start();
         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);
            message.setStringProperty(Message.HDR_LAST_VALUE_NAME.toString(), "key");
            producer.send(message);
         }

         //Last message only should go to the consumer
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         assertEquals("Message99", tm.getText());

      } finally {
         connection.close();
      }
   }

   @Test
   public void testLastValueQueueTopicConsumerUsingAddressQueueParameters() throws Exception {
      ActiveMQConnectionFactory fact = (ActiveMQConnectionFactory) getCF();

      //Set the consumer window size to 0 to not buffer any messages client side.
      fact.setConsumerWindowSize(0);
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Topic topic = session.createTopic("topic?last-value=true");
         assertEquals("topic", topic.getTopicName());

         ActiveMQDestination a = (ActiveMQDestination) topic;
         assertTrue(a.getQueueAttributes().getLastValue());
         assertTrue(a.getQueueConfiguration().isLastValue());

         MessageProducer producer = session.createProducer(topic);
         MessageConsumer consumer1 = session.createConsumer(topic);
         MessageConsumer consumer2 = session.createConsumer(topic);

         connection.start();
         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);
            message.setStringProperty(Message.HDR_LAST_VALUE_NAME.toString(), "key");
            producer.send(message);
         }



         //Last message only should go to the consumer.
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         assertEquals("Message99", tm.getText());

         //Last message only should go to the other consumer as well.
         TextMessage tm2 = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm2);

         assertEquals("Message99", tm2.getText());

      } finally {
         connection.close();
      }
   }

   @Test
   public void testLastValueKeyUsingAddressQueueParameters() throws Exception {
      ActiveMQConnectionFactory fact = (ActiveMQConnectionFactory) getCF();

      //Set the consumer window size to 0 to not buffer any messages client side.
      fact.setConsumerWindowSize(0);
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue("random?last-value-key=reuters_code");
         assertEquals("random", queue.getQueueName());

         ActiveMQDestination a = (ActiveMQDestination) queue;
         assertEquals("reuters_code", a.getQueueAttributes().getLastValueKey().toString());
         assertEquals("reuters_code", a.getQueueConfiguration().getLastValueKey().toString());

         MessageProducer producer = session.createProducer(queue);
         MessageConsumer consumer1 = session.createConsumer(queue);

         connection.start();
         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);
            message.setStringProperty("reuters_code", "key");
            producer.send(message);
         }

         //Last message only should go to the consumer
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         assertEquals("Message99", tm.getText());

      } finally {
         connection.close();
      }
   }

   @Test
   public void testLastValueKeyTopicConsumerUsingAddressQueueParameters() throws Exception {
      ActiveMQConnectionFactory fact = (ActiveMQConnectionFactory) getCF();

      //Set the consumer window size to 0 to not buffer any messages client side.
      fact.setConsumerWindowSize(0);
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Topic topic = session.createTopic("topic?last-value-key=reuters_code");
         assertEquals("topic", topic.getTopicName());

         ActiveMQDestination a = (ActiveMQDestination) topic;
         assertEquals("reuters_code", a.getQueueAttributes().getLastValueKey().toString());
         assertEquals("reuters_code", a.getQueueConfiguration().getLastValueKey().toString());

         MessageProducer producer = session.createProducer(topic);
         MessageConsumer consumer1 = session.createConsumer(topic);
         MessageConsumer consumer2 = session.createConsumer(topic);

         connection.start();
         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);
            message.setStringProperty("reuters_code", "key");
            producer.send(message);
         }



         //Last message only should go to the consumer.
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         assertEquals("Message99", tm.getText());

         //Last message only should go to the other consumer as well.
         TextMessage tm2 = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm2);

         assertEquals("Message99", tm2.getText());

      } finally {
         connection.close();
      }
   }

}
