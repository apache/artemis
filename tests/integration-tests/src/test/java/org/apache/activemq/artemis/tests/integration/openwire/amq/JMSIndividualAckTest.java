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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.JMSIndividualAckTest
 */
public class JMSIndividualAckTest extends BasicOpenWireTest {

   /**
    * Tests if acknowledged messages are being consumed.
    */
   @Test
   public void testAckedMessageAreConsumed() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
      Queue queue = (Queue) this.createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(5000);
      assertNotNull(msg);
      msg.acknowledge();

      // Reset the session.
      session.close();
      session = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);

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
      Session session = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
      Queue queue = (Queue) this.createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      MessageProducer producer = session.createProducer(queue);
      TextMessage msg1 = session.createTextMessage("msg1");
      TextMessage msg2 = session.createTextMessage("msg2");
      TextMessage msg3 = session.createTextMessage("msg3");
      producer.send(msg1);
      producer.send(msg2);
      producer.send(msg3);

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
      session = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      assertEquals(msg1, msg);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      assertEquals(msg2, msg);
      msg = consumer.receive(1000);
      assertNull(msg);
      session.close();
   }

   /**
    * Tests if unacknowledged messages are being re-delivered when the consumer
    * connects again.
    */
   @Test
   public void testUnAckedMessageAreNotConsumedOnSessionClose() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
      Queue queue = (Queue) this.createDestination(session, ActiveMQDestination.QUEUE_TYPE);
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
      session = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(2000);
      assertNotNull(msg);
      msg.acknowledge();

      session.close();
   }

}
