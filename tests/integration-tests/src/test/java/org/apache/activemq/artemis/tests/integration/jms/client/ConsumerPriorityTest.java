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

import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * Consumer Priority Test
 */
public class ConsumerPriorityTest extends JMSTestBase {


   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }


   protected ConnectionFactory getCF() throws Exception {
      return cf;
   }

   @Test
   public void testConsumerPriorityQueueConsumerSettingUsingAddressQueueParameters() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      String queueName = getName();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue(queueName);
         Queue queue1 = session.createQueue(queueName + "?consumer-priority=3");
         Queue queue2 = session.createQueue(queueName + "?consumer-priority=2");
         Queue queue3 = session.createQueue(queueName + "?consumer-priority=1");

         assertEquals(queueName, queue.getQueueName());

         ActiveMQDestination b = (ActiveMQDestination) queue1;
         assertEquals(3, b.getQueueAttributes().getConsumerPriority().intValue());
         assertEquals(3, b.getQueueConfiguration().getConsumerPriority().intValue());
         ActiveMQDestination c = (ActiveMQDestination) queue2;
         assertEquals(2, c.getQueueAttributes().getConsumerPriority().intValue());
         assertEquals(2, c.getQueueConfiguration().getConsumerPriority().intValue());
         ActiveMQDestination d = (ActiveMQDestination) queue3;
         assertEquals(1, d.getQueueAttributes().getConsumerPriority().intValue());
         assertEquals(1, d.getQueueConfiguration().getConsumerPriority().intValue());

         MessageProducer producer = session.createProducer(queue);

         MessageConsumer consumer1 = session.createConsumer(queue1);
         MessageConsumer consumer2 = session.createConsumer(queue2);
         MessageConsumer consumer3 = session.createConsumer(queue3);

         connection.start();

         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);

            producer.send(message);
         }


         //All msgs should go to the first consumer
         for (int j = 0; j < 100; j++) {
            TextMessage tm = (TextMessage) consumer1.receive(10000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            tm = (TextMessage) consumer2.receiveNoWait();
            assertNull(tm);
            tm = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm);
         }
      } finally {
         connection.close();
      }
   }

   @Test
   public void testConsumerPriorityQueueConsumerRoundRobin() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      String queueName = getName();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue(queueName);
         Queue queue1 = session.createQueue(queueName + "?consumer-priority=3");
         Queue queue2 = session.createQueue(queueName + "?consumer-priority=3");
         Queue queue3 = session.createQueue(queueName + "?consumer-priority=1");


         MessageProducer producer = session.createProducer(queue);

         MessageConsumer consumer1 = session.createConsumer(queue1);
         MessageConsumer consumer2 = session.createConsumer(queue2);
         MessageConsumer consumer3 = session.createConsumer(queue3);

         connection.start();

         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);
            message.setIntProperty("counter", j);
            producer.send(message);
         }


         //All msgs should go to the first two consumers, round robin'd
         for (int j = 0; j < 50; j += 2) {
            TextMessage tm = (TextMessage) consumer1.receive(10000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            TextMessage tm2 = (TextMessage) consumer2.receive(10000);
            assertNotNull(tm2);

            assertEquals("Message" + (j + 1), tm2.getText());



            TextMessage tm3 = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm3);
         }
      } finally {
         connection.close();
      }
   }

   @Test
   public void testConsumerPriorityQueueConsumerFailover() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      String queueName = getName();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue(queueName);
         Queue queue1 = session.createQueue(queueName + "?consumer-priority=3");
         Queue queue2 = session.createQueue(queueName + "?consumer-priority=2");
         Queue queue3 = session.createQueue(queueName + "?consumer-priority=1");


         MessageProducer producer = session.createProducer(queue);

         MessageConsumer consumer1 = session.createConsumer(queue1);
         MessageConsumer consumer2 = session.createConsumer(queue2);
         MessageConsumer consumer3 = session.createConsumer(queue3);

         connection.start();

         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);

            producer.send(message);
         }


         //All msgs should go to the first consumer
         for (int j = 0; j < 50; j++) {
            TextMessage tm = (TextMessage) consumer1.receive(10000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            tm = (TextMessage) consumer2.receiveNoWait();
            assertNull(tm);
            tm = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm);
         }
         consumer1.close();

         //All msgs should now go to the next consumer only, without any errors or exceptions
         for (int j = 50; j < 100; j++) {
            TextMessage tm = (TextMessage) consumer2.receive(10000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            tm = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm);
         }
      } finally {
         connection.close();
      }
   }


   @Test
   public void testConsumerPriorityTopicSharedConsumerFailover() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      String topicName = getName();
      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Destination topic = session.createTopic(topicName);
         MessageProducer producer = session.createProducer(topic);

         String subscriptionName = "sharedsub";
         Topic topicConsumer1 = session.createTopic(topicName + "?consumer-priority=3");
         Topic topicConsumer2 = session.createTopic(topicName + "?consumer-priority=2");
         Topic topicConsumer3 = session.createTopic(topicName + "?consumer-priority=1");


         MessageConsumer consumer1 = session.createSharedDurableConsumer(topicConsumer1, subscriptionName);
         MessageConsumer consumer2 = session.createSharedDurableConsumer(topicConsumer2, subscriptionName);
         MessageConsumer consumer3 = session.createSharedDurableConsumer(topicConsumer3, subscriptionName);

         connection.start();

         for (int j = 0; j < 100; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);

            producer.send(message);
         }


         //All msgs should go to the first consumer
         for (int j = 0; j < 50; j++) {
            TextMessage tm = (TextMessage) consumer1.receive(10000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            tm = (TextMessage) consumer2.receiveNoWait();
            assertNull(tm);
            tm = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm);
         }
         consumer1.close();

         //All msgs should now go to the next consumer only, without any errors or exceptions
         for (int j = 50; j < 100; j++) {
            TextMessage tm = (TextMessage) consumer2.receive(10000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            tm = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm);
         }


      } finally {
         connection.close();
      }
   }

}
