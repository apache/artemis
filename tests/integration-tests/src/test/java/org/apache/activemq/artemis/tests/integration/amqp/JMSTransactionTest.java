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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSTransactionTest extends JMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testProduceMessageAndCommit() throws Throwable {
      Connection connection = createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      logger.debug("queue:{}", queue.getQueueName());
      MessageProducer p = session.createProducer(queue);
      for (int i = 0; i < 10; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("Message:" + i);
         p.send(message);
      }

      session.commit();
      session.close();

      Queue queueView = getProxyToQueue(getQueueName());

      Wait.assertEquals(10, queueView::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testProduceMessageAndRollback() throws Throwable {
      Connection connection = createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      logger.debug("queue:{}", queue.getQueueName());
      MessageProducer p = session.createProducer(queue);
      for (int i = 0; i < 10; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("Message:" + i);
         p.send(message);
      }

      session.rollback();
      session.close();

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(0, queueView::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testProducedMessageAreRolledBackOnSessionClose() throws Exception {
      int numMessages = 10;

      Connection connection = createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      MessageProducer p = session.createProducer(queue);
      byte[] bytes = new byte[2048];
      new Random().nextBytes(bytes);
      for (int i = 0; i < numMessages; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("msg:" + i);
         p.send(message);
      }

      session.close();

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(0, queueView::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testConsumeMessagesAndCommit() throws Throwable {
      Connection connection = createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      logger.debug("queue:{}", queue.getQueueName());
      MessageProducer p = session.createProducer(queue);
      for (int i = 0; i < 10; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("Message:" + i);
         p.send(message);
      }
      session.close();

      session = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer cons = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < 10; i++) {
         TextMessage message = (TextMessage) cons.receive(5000);
         assertNotNull(message);
         assertEquals("Message:" + i, message.getText());
      }
      session.commit();
      session.close();

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(0, queueView::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testConsumeMessagesAndRollback() throws Throwable {
      Connection connection = createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      MessageProducer p = session.createProducer(queue);
      for (int i = 0; i < 10; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("Message:" + i);
         p.send(message);
      }
      session.close();

      session = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer cons = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < 10; i++) {
         TextMessage message = (TextMessage) cons.receive(5000);
         assertNotNull(message);
         assertEquals("Message:" + i, message.getText());
      }

      session.rollback();

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(10, queueView::getMessageCount);
   }

   @Test
   @Timeout(60)
   public void testRollbackSomeThenReceiveAndCommit() throws Exception {
      final int MSG_COUNT = 5;
      final int consumeBeforeRollback = 2;

      Connection connection = createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      MessageProducer p = session.createProducer(queue);
      for (int i = 0; i < MSG_COUNT; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("Message:" + i);
         message.setIntProperty("MESSAGE_NUMBER", i + 1);
         p.send(message);
      }

      session.commit();

      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(MSG_COUNT, queueView::getMessageCount);

      MessageConsumer consumer = session.createConsumer(queue);

      for (int i = 1; i <= consumeBeforeRollback; i++) {
         Message message = consumer.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("MESSAGE_NUMBER"), "Unexpected message number");
      }

      session.rollback();

      Wait.assertEquals(MSG_COUNT, queueView::getMessageCount);

      // Consume again..check we receive all the messages.
      Set<Integer> messageNumbers = new HashSet<>();
      for (int i = 1; i <= MSG_COUNT; i++) {
         messageNumbers.add(i);
      }

      for (int i = 1; i <= MSG_COUNT; i++) {
         Message message = consumer.receive(1000);
         assertNotNull(message);
         int msgNum = message.getIntProperty("MESSAGE_NUMBER");
         messageNumbers.remove(msgNum);
      }

      session.commit();

      assertTrue(messageNumbers.isEmpty(), "Did not consume all expected messages, missing messages: " + messageNumbers);
      assertEquals(0, queueView.getMessageCount(), "Queue should have no messages left after commit");
   }
}
