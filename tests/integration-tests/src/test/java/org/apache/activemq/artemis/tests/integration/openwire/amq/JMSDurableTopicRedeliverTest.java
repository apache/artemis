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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.JMSDurableTopicRedeliverTest
 */
public class JMSDurableTopicRedeliverTest extends JmsTopicRedeliverTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      durable = true;
      super.setUp();
   }

   /**
    * Sends and consumes the messages.
    */
   @Test
   public void testRedeliverNewSession() throws Exception {
      String text = "TEST: " + System.currentTimeMillis();
      Message sendMessage = session.createTextMessage(text);

      producer.send(producerDestination, sendMessage);

      // receive but don't acknowledge
      Message unackMessage = consumer.receive(1000);
      assertNotNull(unackMessage);
      String unackId = unackMessage.getJMSMessageID();
      assertEquals(((TextMessage) unackMessage).getText(), text);
      assertFalse(unackMessage.getJMSRedelivered());
      assertEquals(1, unackMessage.getIntProperty("JMSXDeliveryCount"));
      consumeSession.close();
      consumer.close();

      // receive then acknowledge
      consumeSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      consumer = createConsumer(getName());
      Message ackMessage = consumer.receive(1000);
      assertNotNull(ackMessage);

      ackMessage.acknowledge();

      String ackId = ackMessage.getJMSMessageID();
      assertEquals(((TextMessage) ackMessage).getText(), text);
      assertEquals(2, ackMessage.getIntProperty("JMSXDeliveryCount"));
      assertEquals(unackId, ackId);
      consumeSession.close();
      consumer.close();

      consumeSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      consumer = createConsumer(getName());
      assertNull(consumer.receive(1000));
   }

   @Override
   protected String getName() {
      return "JMSDurableTopicRedeliverTest";
   }

}
