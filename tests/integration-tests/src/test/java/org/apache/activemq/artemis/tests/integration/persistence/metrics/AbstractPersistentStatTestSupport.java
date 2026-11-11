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
package org.apache.activemq.artemis.tests.integration.persistence.metrics;

import java.util.Enumeration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public abstract class AbstractPersistentStatTestSupport extends JMSTestBase {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static int defaultMessageSize = 1000;

   @Override
   protected boolean usePersistence() {
      return true;
   }

   protected void consumeTestQueueMessages(String queueName, int num) throws Exception {

      // Start the connection
      Connection connection = cf.createConnection();
      connection.setClientID("clientId2" + queueName);
      connection.start();
      Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(queueName);
      MessageConsumer consumer;
      try {
         consumer = session.createConsumer(queue);
         for (int i = 0; i < num; i++) {
            consumer.receive();
         }
         consumer.close();
      } finally {
         // consumer.close();
         connection.close();
      }

   }

   protected void browseTestQueueMessages(String queueName) throws Exception {
      // Start the connection
      Connection connection = cf.createConnection();
      connection.setClientID("clientId2" + queueName);
      connection.start();
      Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(queueName);

      try {
         QueueBrowser queueBrowser = session.createBrowser(queue);
         @SuppressWarnings("unchecked")
         Enumeration<Message> messages = queueBrowser.getEnumeration();
         while (messages.hasMoreElements()) {
            messages.nextElement();
         }

      } finally {
         connection.close();
      }

   }

   protected void consumeDurableTestMessages(Connection connection, String sub, int size, String topicName,
         AtomicLong publishedMessageSize) throws Exception {


      Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic(topicName);

      try {
         TopicSubscriber consumer = session.createDurableSubscriber(topic, sub);
         for (int i = 0; i < size; i++) {
            ActiveMQMessage message = (ActiveMQMessage) consumer.receive();
            if (publishedMessageSize != null) {
               publishedMessageSize.addAndGet(-message.getCoreMessage().getEncodeSize());
            }
         }

      } finally {
         session.close();
      }

   }

   protected void publishTestQueueMessages(int count, String queueName, int deliveryMode, int messageSize,
         AtomicLong publishedMessageSize, boolean transacted) throws Exception {

      // Start the connection
      Connection connection = cf.createConnection();
      connection.setClientID("clientId" + queueName);
      connection.start();
      Session session = transacted ? connection.createSession(transacted, QueueSession.SESSION_TRANSACTED) :
         connection.createSession(transacted, QueueSession.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(queueName);

      try {
         MessageProducer prod = session.createProducer(queue);
         prod.setDeliveryMode(deliveryMode);
         for (int i = 0; i < count; i++) {
            prod.send(createMessage(i, session, messageSize, publishedMessageSize));
         }

         if (transacted) {
            session.commit();
         }
      } finally {
         connection.close();
      }
   }

   protected void publishTestMessagesDurable(Connection connection, String[] subNames, String topicName,
         int publishSize, int expectedSize, int messageSize, AtomicLong publishedMessageSize, boolean verifyBrowsing,
         boolean shared)
         throws Exception {
      this.publishTestMessagesDurable(connection, subNames, topicName, publishSize, expectedSize, messageSize,
            publishedMessageSize, verifyBrowsing, DeliveryMode.PERSISTENT, shared);
   }

   protected void publishTestMessagesDurable(Connection connection, String[] subNames, String topicName,
         int publishSize, int expectedSize, int messageSize, AtomicLong publishedMessageSize, boolean verifyBrowsing,
         int deliveryMode, boolean shared) throws Exception {

      Session session = connection.createSession(false, TopicSession.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic(topicName);
      for (String subName : subNames) {
         if (shared) {
            session.createSharedDurableConsumer(topic, subName);
         } else {
            session.createDurableSubscriber(topic, subName);
         }
      }

      try {
         // publish a bunch of non-persistent messages to fill up the temp
         // store
         MessageProducer prod = session.createProducer(topic);
         prod.setDeliveryMode(deliveryMode);
         for (int i = 0; i < publishSize; i++) {
            prod.send(createMessage(i, session, messageSize, publishedMessageSize));
         }

      } finally {
         session.close();
      }

   }

   /**
    * Generate random messages between 100 bytes and maxMessageSize
    */
   protected BytesMessage createMessage(int count, Session session, int maxMessageSize, AtomicLong publishedMessageSize)
         throws JMSException, ActiveMQException {
      final ActiveMQBytesMessage message = (ActiveMQBytesMessage) session.createBytesMessage();

      final Random randomSize = new Random();
      int size = randomSize.nextInt((maxMessageSize - 100) + 1) + 100;
      final byte[] data = new byte[size];
      final Random rng = new Random();
      rng.nextBytes(data);
      message.writeBytes(data);
      if (publishedMessageSize != null) {
         publishedMessageSize.addAndGet(message.getCoreMessage().getPersistentSize());
      }

      return message;
   }
}
