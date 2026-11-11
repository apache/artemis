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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QueueAutoDeleteTest this tests that we can configure at the queue level auto-delete behaviour of auto created queues.
 */
public class QueueAutoDeleteTest extends JMSTestBase {


   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      //Set scan period over aggressively so tests do not have to wait too long.
      return super.createDefaultConfig(netty).setAddressQueueScanPeriod(10);
   }

   protected ConnectionFactory getCF() throws Exception {
      return cf;
   }

   @Test
   public void testAutoDelete() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();

         Queue queue = session.createQueue(testQueueName + "?auto-delete=true");
         ActiveMQDestination activeMQDestination = (ActiveMQDestination) queue;

         final MessageConsumer consumer1 = session.createConsumer(queue);


         assertEquals(testQueueName, queue.getQueueName());
         assertNotNull(activeMQDestination.getQueueAttributes().getAutoDelete());
         assertTrue(activeMQDestination.getQueueAttributes().getAutoDelete());
         assertNotNull(activeMQDestination.getQueueConfiguration().isAutoDelete());
         assertTrue(activeMQDestination.getQueueConfiguration().isAutoDelete());

         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello1"));
         producer.send(session.createTextMessage("hello2"));

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertTrue(queueBinding.getQueue().isAutoDelete());
         Wait.assertEquals(2, queueBinding.getQueue()::getMessageCount);

         Message message = consumer1.receive(5000);
         assertNotNull(message);
         message.acknowledge();


         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         Wait.assertEquals(1, queueBinding.getQueue()::getMessageCount);

         final MessageConsumer consumer2 = session.createConsumer(queue);
         consumer1.close();

         message = consumer2.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer2.close();

         Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(testQueueName)) == null, 5000, 10);

      } finally {
         connection.close();
      }
   }

   @Test
   public void testAutoDeleteTopicDurableSubscriptionQueue() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();
         String sub = testQueueName + "/mysub";

         Topic topic = session.createTopic(testQueueName + "?auto-delete=true");
         ActiveMQDestination activeMQDestination = (ActiveMQDestination) topic;

         assertEquals(testQueueName, topic.getTopicName());
         assertNotNull(activeMQDestination.getQueueAttributes().getAutoDelete());
         assertTrue(activeMQDestination.getQueueAttributes().getAutoDelete());
         assertNotNull(activeMQDestination.getQueueConfiguration().isAutoDelete());
         assertTrue(activeMQDestination.getQueueConfiguration().isAutoDelete());

         MessageConsumer consumer = session.createSharedDurableConsumer(topic, sub);

         // this will hold a consumer just to avoid the queue from being auto-deleted
         MessageConsumer consumerHolder = session.createSharedDurableConsumer(topic, sub);

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(sub));
         assertTrue(queueBinding.getQueue().isAutoDelete());
         assertEquals(0, queueBinding.getQueue().getMessageCount());

         MessageProducer producer = session.createProducer(topic);
         producer.send(session.createTextMessage("hello1"));
         producer.send(session.createTextMessage("hello2"));

         Message message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals("hello1", ((TextMessage)message).getText());
         message.acknowledge();

         consumer.close();

         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(sub));
         assertNotNull(queueBinding);

         consumer = session.createSharedDurableConsumer(topic, sub);
         consumerHolder.close();
         message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals("hello2", ((TextMessage)message).getText());
         message.acknowledge();

         consumer.close();

         Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(sub)) == null, 5000, 10);

      } finally {
         connection.close();
      }
   }

   @Test
   public void testAutoDeleteTopicDefaultDurableSubscriptionQueue() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();
         String sub = testQueueName + "/mysub";

         Topic topic = session.createTopic(testQueueName);

         assertEquals(testQueueName, topic.getTopicName());


         MessageConsumer consumer = session.createSharedDurableConsumer(topic, sub);

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(sub));
         assertFalse(queueBinding.getQueue().isAutoDelete());
         Wait.assertEquals(0, queueBinding.getQueue()::getMessageCount);

         MessageProducer producer = session.createProducer(topic);
         producer.send(session.createTextMessage("hello1"));
         producer.send(session.createTextMessage("hello2"));

         Message message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals("hello1", ((TextMessage)message).getText());
         message.acknowledge();

         consumer.close();

         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(sub));
         assertNotNull(queueBinding);

         consumer = session.createSharedDurableConsumer(topic, sub);
         message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals("hello2", ((TextMessage)message).getText());
         message.acknowledge();

         consumer.close();

         //Wait longer than scan period.
         Thread.sleep(20);

         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(sub));
         assertNotNull(queueBinding);


      } finally {
         connection.close();
      }
   }


   @Test
   public void testAutoDeleteOff() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();

         Queue queue = session.createQueue(testQueueName + "?auto-delete=false");
         ActiveMQDestination activeMQDestination = (ActiveMQDestination) queue;

         assertEquals(testQueueName, queue.getQueueName());
         assertNotNull(activeMQDestination.getQueueAttributes().getAutoDelete());
         assertFalse(activeMQDestination.getQueueAttributes().getAutoDelete());
         assertNotNull(activeMQDestination.getQueueConfiguration().isAutoDelete());
         assertFalse(activeMQDestination.getQueueConfiguration().isAutoDelete());

         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello1"));
         producer.send(session.createTextMessage("hello2"));

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertFalse(queueBinding.getQueue().isAutoDelete());
         Wait.assertEquals(2, queueBinding.getQueue()::getMessageCount);

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer.close();

         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         Wait.assertEquals(1, queueBinding.getQueue()::getMessageCount);

         consumer = session.createConsumer(queue);
         message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer.close();

         //Wait longer than scan period.
         Thread.sleep(20);

         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertNotNull(queueBinding);
         Wait.assertEquals(0, queueBinding.getQueue()::getMessageCount);

      } finally {
         connection.close();
      }
   }

   @Test
   public void testAutoDeleteDelay() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();

         Queue queue = session.createQueue(testQueueName + "?auto-delete=true&auto-delete-delay=100");
         ActiveMQDestination activeMQDestination = (ActiveMQDestination) queue;

         assertEquals(testQueueName, queue.getQueueName());
         assertEquals(Long.valueOf(100), activeMQDestination.getQueueAttributes().getAutoDeleteDelay());
         assertEquals(Long.valueOf(100), activeMQDestination.getQueueConfiguration().getAutoDeleteDelay());

         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello1"));
         producer.send(session.createTextMessage("hello2"));

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertTrue(queueBinding.getQueue().isAutoDelete());
         assertEquals(100, queueBinding.getQueue().getAutoDeleteDelay());
         Wait.assertEquals(2, queueBinding.getQueue()::getMessageCount);

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer.close();

         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         Wait.assertEquals(1, queueBinding.getQueue()::getMessageCount);

         consumer = session.createConsumer(queue);
         message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer.close();

         //Wait longer than scan period, but less than delay
         Thread.sleep(50);

         //Check the queue has not been removed.
         queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertNotNull(queueBinding);

         Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(testQueueName)) == null, 5000, 10);


      } finally {
         connection.close();
      }
   }

   @Test
   public void testAutoDeleteMessageCount() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();

         Queue queue = session.createQueue(testQueueName + "?auto-delete=true&auto-delete-message-count=1");
         ActiveMQDestination activeMQDestination = (ActiveMQDestination) queue;

         assertEquals(testQueueName, queue.getQueueName());
         assertEquals(Long.valueOf(1), activeMQDestination.getQueueAttributes().getAutoDeleteMessageCount());
         assertEquals(Long.valueOf(1), activeMQDestination.getQueueConfiguration().getAutoDeleteMessageCount());

         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello1"));
         producer.send(session.createTextMessage("hello2"));

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertTrue(queueBinding.getQueue().isAutoDelete());
         Wait.assertEquals(1, queueBinding.getQueue()::getAutoDeleteMessageCount);
         Wait.assertEquals(2, queueBinding.getQueue()::getMessageCount);

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer.close();

         //Wait longer than scan period
         Thread.sleep(20);

         Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(testQueueName)) == null, 5000, 10);

      } finally {
         connection.close();
      }
   }

   @Test
   public void testAutoDeleteMessageCountDisabled() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();
      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName();

         Queue queue = session.createQueue(testQueueName + "?auto-delete=true&auto-delete-message-count=-1");
         ActiveMQDestination activeMQDestination = (ActiveMQDestination) queue;

         assertEquals(testQueueName, queue.getQueueName());
         assertEquals(Long.valueOf(-1), activeMQDestination.getQueueAttributes().getAutoDeleteMessageCount());
         assertEquals(Long.valueOf(-1), activeMQDestination.getQueueConfiguration().getAutoDeleteMessageCount());

         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < 100; i++) {
            producer.send(session.createTextMessage("hello" + i));
         }

         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         Wait.assertEquals(100, queueBinding.getQueue()::getMessageCount);
         assertTrue(queueBinding.getQueue().isAutoDelete());
         assertEquals(-1, queueBinding.getQueue().getAutoDeleteMessageCount());

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();

         consumer.close();

         //Wait longer than scan period
         Thread.sleep(20);

         Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(testQueueName)) == null, 5000, 10);


      } finally {
         connection.close();
      }
   }


}
