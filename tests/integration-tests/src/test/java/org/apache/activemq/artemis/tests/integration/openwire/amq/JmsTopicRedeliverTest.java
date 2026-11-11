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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.JmsTopicRedeliverTest
 */
public class JmsTopicRedeliverTest extends BasicOpenWireTest {

   protected Session session;
   protected Session consumeSession;
   protected MessageConsumer consumer;
   protected MessageProducer producer;
   protected Destination consumerDestination;
   protected Destination producerDestination;
   protected boolean topic = true;
   protected boolean durable;
   protected long initRedeliveryDelay;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      initRedeliveryDelay = connection.getRedeliveryPolicy().getInitialRedeliveryDelay();

      if (durable) {
         connection.setClientID(getClass().getName());
      }

      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      consumeSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      producer = session.createProducer(null);
      // producer.setRoutingType(deliveryMode);

      if (topic) {
         consumerDestination = this.createDestination(session, ActiveMQDestination.TOPIC_TYPE);
         producerDestination = this.createDestination(session, ActiveMQDestination.TOPIC_TYPE);
      } else {
         consumerDestination = this.createDestination(session, ActiveMQDestination.QUEUE_TYPE);
         producerDestination = this.createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      }

      consumer = createConsumer(getName());
      connection.start();
   }

   /**
    * Sends and consumes the messages.
    */
   @Test
   public void testRecover() throws Exception {
      String text = "TEST";
      Message sendMessage = session.createTextMessage(text);

      producer.send(producerDestination, sendMessage);

      // receive but don't acknowledge
      Message unackMessage = consumer.receive(initRedeliveryDelay + 1000);
      assertNotNull(unackMessage);
      String unackId = unackMessage.getJMSMessageID();
      assertEquals(text, ((TextMessage) unackMessage).getText());
      assertFalse(unackMessage.getJMSRedelivered());
      // assertEquals(unackMessage.getIntProperty("JMSXDeliveryCount"),1);

      // receive then acknowledge
      consumeSession.recover();
      Message ackMessage = consumer.receive(initRedeliveryDelay + 1000);
      assertNotNull(ackMessage);
      ackMessage.acknowledge();
      String ackId = ackMessage.getJMSMessageID();
      assertEquals(text, ((TextMessage) ackMessage).getText());
      assertTrue(ackMessage.getJMSRedelivered());
      // assertEquals(ackMessage.getIntProperty("JMSXDeliveryCount"),2);
      assertEquals(unackId, ackId);
      consumeSession.recover();
      assertNull(consumer.receiveNoWait());
   }

   protected MessageConsumer createConsumer(String durableName) throws JMSException {
      if (durable) {
         return consumeSession.createDurableSubscriber((Topic) consumerDestination, durableName);
      }
      return consumeSession.createConsumer(consumerDestination);
   }

}
