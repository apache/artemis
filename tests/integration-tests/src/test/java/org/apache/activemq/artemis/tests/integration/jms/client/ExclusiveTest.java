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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExclusiveTest extends JMSTestBase {

   private SimpleString queueName = SimpleString.of("jms.exclusive.queue");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setExclusive(true));
   }


   protected ConnectionFactory getCF() throws Exception {
      return cf;
   }

   @Test
   public void testExclusiveQueueConsumer() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Destination queue = session.createQueue(queueName.toString());
         MessageProducer producer = session.createProducer(queue);

         MessageConsumer consumer1 = session.createConsumer(queue);
         MessageConsumer consumer2 = session.createConsumer(queue);
         MessageConsumer consumer3 = session.createConsumer(queue);

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
   public void testExclusiveWithJMS2Producer() throws Exception {
      ConnectionFactory fact = getCF();
      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      try {
         JMSProducer producer = ctx.createProducer();
         Destination queue = ctx.createQueue(queueName.toString());

         JMSConsumer consumer1 = ctx.createConsumer(queue);
         JMSConsumer consumer2 = ctx.createConsumer(queue);
         JMSConsumer consumer3 = ctx.createConsumer(queue);

         ctx.start();

         for (int j = 0; j < 100; j++) {
            TextMessage message = ctx.createTextMessage("Message" + j);

            producer.send(queue, message);
         }

         ctx.commit();

         //All msgs should go to the first consumer
         for (int j = 0; j < 100; j++) {
            TextMessage tm = (TextMessage) consumer1.receive(10000);

            assertNotNull(tm);

            tm.acknowledge();

            assertEquals("Message" + j, tm.getText());

            tm = (TextMessage) consumer2.receiveNoWait();
            assertNull(tm);
            tm = (TextMessage) consumer3.receiveNoWait();
            assertNull(tm);
         }

         ctx.commit();
      } finally {
         ctx.close();
      }
   }

   @Test
   public void testExclusiveQueueConsumerSettingUsingAddressQueueParameters() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue("random?exclusive=true");
         assertEquals("random", queue.getQueueName());

         ActiveMQDestination a = (ActiveMQDestination) queue;
         assertTrue(a.getQueueAttributes().getExclusive());
         assertTrue(a.getQueueConfiguration().isExclusive());

         MessageProducer producer = session.createProducer(queue);

         MessageConsumer consumer1 = session.createConsumer(queue);
         MessageConsumer consumer2 = session.createConsumer(queue);
         MessageConsumer consumer3 = session.createConsumer(queue);

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
   public void testExclusiveQueueConsumerFailover() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Destination queue = session.createQueue(queueName.toString());
         MessageProducer producer = session.createProducer(queue);

         MessageConsumer consumer1 = session.createConsumer(queue);
         MessageConsumer consumer2 = session.createConsumer(queue);
         MessageConsumer consumer3 = session.createConsumer(queue);

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
   public void testExclusiveTopicSharedConsumerFailover() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Destination topic = session.createTopic("mytopic");
         MessageProducer producer = session.createProducer(topic);

         String subscriptionName = "sharedsub";
         Topic topicConsumer = session.createTopic("mytopic?exclusive=true");
         MessageConsumer consumer1 = session.createSharedDurableConsumer(topicConsumer, subscriptionName);
         MessageConsumer consumer2 = session.createSharedDurableConsumer(topicConsumer, subscriptionName);
         MessageConsumer consumer3 = session.createSharedDurableConsumer(topicConsumer, subscriptionName);

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
