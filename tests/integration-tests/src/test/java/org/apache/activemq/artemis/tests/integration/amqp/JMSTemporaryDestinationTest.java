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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSTemporaryDestinationTest extends JMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testCreateTemporaryQueue() throws Throwable {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TemporaryQueue queue = session.createTemporaryQueue();
         logger.debug("queue:{}", queue.getQueueName());
         MessageProducer producer = session.createProducer(queue);

         TextMessage message = session.createTextMessage();
         message.setText("Message temporary");
         producer.send(message);

         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         message = (TextMessage) consumer.receive(5000);

         assertNotNull(message);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testDeleteTemporaryQueue() throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final javax.jms.Queue queue = session.createTemporaryQueue();
         assertNotNull(queue);
         assertInstanceOf(TemporaryQueue.class, queue);

         Queue queueView = getProxyToQueue(queue.getQueueName());
         assertNotNull(queueView);

         TemporaryQueue tempQueue = (TemporaryQueue) queue;
         tempQueue.delete();

         assertTrue(Wait.waitFor(() -> getProxyToQueue(queue.getQueueName()) == null, TimeUnit.SECONDS.toMillis(30), TimeUnit.MILLISECONDS.toMillis(50)), "Temp Queue should be deleted.");
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCreateTemporaryTopic() throws Throwable {
      Connection connection = createConnection();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TemporaryTopic topic = session.createTemporaryTopic();

      logger.debug("topic:{}", topic.getTopicName());
      MessageConsumer consumer = session.createConsumer(topic);
      MessageProducer producer = session.createProducer(topic);

      TextMessage message = session.createTextMessage();
      message.setText("Message temporary");
      producer.send(message);

      connection.start();

      message = (TextMessage) consumer.receive(5000);

      assertNotNull(message);
   }

   @Test
   @Timeout(30)
   public void testDeleteTemporaryTopic() throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final javax.jms.Topic topic = session.createTemporaryTopic();
         assertNotNull(topic);
         assertInstanceOf(TemporaryTopic.class, topic);

         AddressInfo addressView = getProxyToAddress(topic.getTopicName());
         assertNotNull(addressView);

         TemporaryTopic tempTopic = (TemporaryTopic) topic;
         tempTopic.delete();

         Wait.assertNull(() -> getProxyToAddress(topic.getTopicName()), TimeUnit.SECONDS.toMillis(30), TimeUnit.MILLISECONDS.toMillis(50));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(20)
   public void testTemporaryTopicDeletedOnConnectionClosed() throws Exception {
      doTestTemporaryDestinationIsDeletedOnConnectionClosed(true, true);
   }

   @Test
   @Timeout(20)
   public void testTemporaryQueueDeletedOnConnectionClosed() throws Exception {
      doTestTemporaryDestinationIsDeletedOnConnectionClosed(false, true);
   }

   @Test
   @Timeout(20)
   public void testTemporaryTopicDeletedOnConnectionClosedWithoutExplicitConsumerClose() throws Exception {
      doTestTemporaryDestinationIsDeletedOnConnectionClosed(true, false);
   }

   @Test
   @Timeout(20)
   public void testTemporaryQueueDeletedOnConnectionClosedWithoutExplicitConsumerClose() throws Exception {
      doTestTemporaryDestinationIsDeletedOnConnectionClosed(false, false);
   }

   private void doTestTemporaryDestinationIsDeletedOnConnectionClosed(boolean topic, boolean closeConsumer) throws Exception {
      final String addressName;
      try (Connection connection = createConnection()) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTemporaryTopic();

            assertNotNull(destination);
            assertTrue(destination instanceof TemporaryTopic);

            addressName = ((TemporaryTopic) destination).getTopicName();
         } else {
            destination = session.createTemporaryQueue();

            assertNotNull(destination);
            assertTrue(destination instanceof TemporaryQueue);

            addressName = ((TemporaryQueue) destination).getQueueName();
         }

         logger.debug("Address being used is {}", addressName);

         final MessageConsumer consumer = session.createConsumer(destination);

         final AddressInfo addressView = getProxyToAddress(addressName);
         assertNotNull(addressView);

         assertEquals(1, server.bindingQuery(addressView.getName()).getQueueNames().size());

         if (closeConsumer) {
            consumer.close();
         }

      }
      Wait.assertNull(() -> getProxyToAddress(addressName), TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS.toMillis(50));
   }


}
