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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.TimeStampTest
 */
public class TimeStampTest extends BasicOpenWireTest {

   @Test
   public void testTimestamp() throws Exception {
      // Create a Session
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      // Create the destination Queue
      Destination destination = session.createQueue("TEST.FOO");
      this.makeSureCoreQueueExist("TEST.FOO");

      // Create a MessageProducer from the Session to the Topic or Queue
      MessageProducer producer = session.createProducer(destination);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      // Create a messages
      Message sentMessage = session.createMessage();

      // Tell the producer to send the message
      long beforeSend = System.currentTimeMillis();
      producer.send(sentMessage);
      long afterSend = System.currentTimeMillis();

      // assert message timestamp is in window
      assertTrue(beforeSend <= sentMessage.getJMSTimestamp() && sentMessage.getJMSTimestamp() <= afterSend);

      // Create a MessageConsumer from the Session to the Topic or Queue
      MessageConsumer consumer = session.createConsumer(destination);

      // Wait for a message
      Message receivedMessage = consumer.receive(1000);

      // assert we got the same message ID we sent
      assertEquals(sentMessage.getJMSMessageID(), receivedMessage.getJMSMessageID());

      // assert message timestamp is in window
      assertTrue(beforeSend <= receivedMessage.getJMSTimestamp() && receivedMessage.getJMSTimestamp() <= afterSend, "JMS Message Timestamp should be set during the send method: \n" + "        beforeSend = " + beforeSend + "\n" + "   getJMSTimestamp = " + receivedMessage.getJMSTimestamp() + "\n" + "         afterSend = " + afterSend + "\n");

      // assert message timestamp is unchanged
      assertEquals(sentMessage.getJMSTimestamp(), receivedMessage.getJMSTimestamp(), "JMS Message Timestamp of received message should be the same as the sent message\n        ");

      // Clean up
      producer.close();
      consumer.close();
      session.close();
      connection.close();
   }

}
