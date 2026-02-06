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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * adapted from: org.apache.activemq.JmsClientAckTest
 */
public class JmsClientAckTest extends BasicOpenWireTest {

   /**
    * Tests if acknowledged messages are being consumed.
    */
   @Test
   public void testAckedMessageAreConsumed() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      msg.acknowledge();

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(1000);
      assertNull(msg);

      session.close();
   }

   /**
    * Tests if acknowledged messages are being consumed.
    */
   @Test
   public void testLastMessageAcked() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));
      producer.send(session.createTextMessage("Hello2"));
      producer.send(session.createTextMessage("Hello3"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      msg.acknowledge();

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(1000);
      assertNull(msg);

      session.close();
   }

   /**
    * Tests if unacknowledged messages are being re-delivered when the consumer connects again.
    */
   @Test
   public void testUnAckedMessageAreNotConsumedOnSessionClose() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      // Don't ack the message.

      // Reset the session. This should cause the unacknowledged message to be
      // re-delivered.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(2000);
      assertNotNull(msg);
      msg.acknowledge();

      session.close();
   }

   /**
    * Tests if acknowledged messages are being consumed.
    */
   @Test
   public void testAckedMessageDeliveringWithPrefetch() throws Exception {
      final int prefetchSize = 10;
      final int messageCount = 5 * prefetchSize;
      connection.getPrefetchPolicy().setAll(prefetchSize);
      connection.start();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      QueueControl queueControl = server.getManagementService().getQueueControl(queueName);
      MessageProducer producer = session.createProducer(queue);
      for (int i = 0; i < messageCount; i++) {
         producer.send(session.createTextMessage("MSG" + i));
      }

      // Consume the messages...
      Message msg;
      MessageConsumer consumer = session.createConsumer(queue);

      Wait.assertEquals(0L, () -> queueControl.getMessagesAcknowledged(), 3000, 100);
      Wait.assertEquals(prefetchSize, () -> queueControl.getDeliveringCount(), 3000, 100);

      List<Message> messages = new ArrayList<>();
      for (int i = 0; i < prefetchSize; i++) {
         msg = consumer.receive(1000);
         assertNotNull(msg);
         messages.add(msg);
      }

      Wait.assertEquals(0L, () -> queueControl.getMessagesAcknowledged(), 3000, 100);
      Wait.assertEquals(2 * prefetchSize, () -> queueControl.getDeliveringCount(), 3000, 100);

      for (int i = 0; i < prefetchSize; i++) {
         msg = messages.get(i);
         msg.acknowledge();
      }

      Wait.assertEquals((long) prefetchSize, () -> queueControl.getMessagesAcknowledged(), 3000, 100);
      Wait.assertEquals(prefetchSize, () -> queueControl.getDeliveringCount(), 3000, 100);

      for (int i = 0; i < messageCount - prefetchSize; i++) {
         msg = consumer.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      Wait.assertEquals((long)messageCount, () -> queueControl.getMessagesAcknowledged(), 3000, 100);
      Wait.assertEquals(0, () -> queueControl.getDeliveringCount(), 3000, 100);

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receiveNoWait();
      assertNull(msg);

      session.close();
   }

   protected String getQueueName() {
      return queueName;
   }

}
