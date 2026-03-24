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

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.Test;

public class CreateQueueTest extends JMSTestBase {



   @Test
   public void testCreateQueueTempQueue() throws Exception {
      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue tempQueue = session.createTemporaryQueue();

      String tempQueueName = tempQueue.getQueueName();

//      assertFalse(tempQueueName.startsWith(ActiveMQDestination.JMS_QUEUE_ADDRESS_PREFIX));

      Queue replyQueue = session.createQueue(tempQueueName);

      MessageProducer producer = session.createProducer(replyQueue);

      producer.send(session.createMessage());

      MessageConsumer consumer = session.createConsumer(replyQueue);

      conn.start();

      assertNotNull(consumer.receive(10000));
   }

   @Test
   public void testCreateQueue() throws Exception {
      conn = cf.createConnection();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = createQueue("TestQueue");

      String queueName = queue.getQueueName();

      instanceLog.debug("queue name is " + queueName);

//      assertFalse(queueName.startsWith(ActiveMQDestination.JMS_QUEUE_ADDRESS_PREFIX));

      Queue replyQueue = session.createQueue(queueName);

      MessageProducer producer = session.createProducer(replyQueue);

      producer.send(session.createMessage());

      MessageConsumer consumer = session.createConsumer(replyQueue);

      conn.start();

      assertNotNull(consumer.receive(10000));
   }

   @Test
   public void testCreateTopic() throws Exception {
      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Topic topic = createTopic("TestTopic");

      String topicName = topic.getTopicName();

//      assertFalse(topicName.startsWith(ActiveMQDestination.JMS_TOPIC_ADDRESS_PREFIX));

      Topic replyTopic = session.createTopic(topicName);

      MessageConsumer consumer = session.createConsumer(replyTopic);

      conn.start();

      MessageProducer producer = session.createProducer(replyTopic);

      producer.send(session.createMessage());

      assertNotNull(consumer.receive(10000));
   }

   @Test
   public void testCreateTopicTempTopic() throws Exception {
      conn = cf.createConnection();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Topic tempTopic = session.createTemporaryTopic();

      String tempTopicName = tempTopic.getTopicName();

//      assertFalse(tempTopicName.startsWith(ActiveMQDestination.JMS_TOPIC_ADDRESS_PREFIX));

      Topic replyTopic = session.createTopic(tempTopicName);

      MessageConsumer consumer = session.createConsumer(replyTopic);

      conn.start();

      MessageProducer producer = session.createProducer(replyTopic);

      producer.send(session.createMessage());

      assertNotNull(consumer.receive(10000));
   }
}
