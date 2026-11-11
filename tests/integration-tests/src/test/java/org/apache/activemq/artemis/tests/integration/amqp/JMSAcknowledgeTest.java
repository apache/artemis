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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

public class JMSAcknowledgeTest extends JMSClientTestSupport {

   private static final String MSG_NUM = "MSG_NUM";
   private static final int INDIVIDUAL_ACK = 101;

   @Test
   @Timeout(60)
   public void testConsumeIndividualMessagesOutOfOrder() throws Throwable {
      Connection connection = createConnection();

      // Send some messages
      Session session = connection.createSession(false, INDIVIDUAL_ACK);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      int msgCount = 10;
      MessageProducer p = session.createProducer(queue);
      for (int i = 0; i < msgCount; i++) {
         TextMessage message = session.createTextMessage();
         message.setText("Message:" + i);
         message.setIntProperty(MSG_NUM, i);
         p.send(message);
      }

      // Check they arrived
      Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(10, queueView::getMessageCount);

      // Consume them, ack some of them, out of order
      MessageConsumer cons = session.createConsumer(queue);
      connection.start();

      List<Message> messages = new ArrayList<>();
      for (int i = 0; i < msgCount; i++) {
         TextMessage message = (TextMessage) cons.receive(5000);
         assertNotNull(message, "Message " + i + " was not received");
         assertEquals(i, message.getIntProperty(MSG_NUM), "unexpected message number property");

         messages.add(message);
      }

      List<Integer> acknowledged = new ArrayList<>();

      Random rand = new Random();
      for (int i = 0; i < msgCount / 2; i++) {
         Message msg = messages.remove(rand.nextInt(msgCount - i));

         int messageNumber = msg.getIntProperty(MSG_NUM);
         acknowledged.add(messageNumber);

         msg.acknowledge();
      }

      session.close();

      Wait.assertEquals(msgCount / 2, queueView::getMessageCount);

      // Consume them again, verify the rest are in expected sequence
      session = connection.createSession(false, INDIVIDUAL_ACK);
      cons = session.createConsumer(queue);

      for (int i = 0; i < msgCount / 2; i++) {
         TextMessage message = (TextMessage) cons.receive(5000);
         assertNotNull(message, "Message " + i + " was not received");
         Message expectedMsg = messages.remove(0);
         int expectedMsgNum = expectedMsg.getIntProperty(MSG_NUM);
         assertEquals(expectedMsgNum, message.getIntProperty(MSG_NUM), "unexpected message number property");
      }
   }
}