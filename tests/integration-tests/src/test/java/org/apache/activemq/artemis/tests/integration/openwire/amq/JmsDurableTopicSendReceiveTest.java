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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.JmsDurableTopicSendReceiveTest
 */
public class JmsDurableTopicSendReceiveTest extends JmsTopicSendReceiveTest {

   protected Connection connection2;
   protected Session session2;
   protected Session consumeSession2;
   protected MessageConsumer consumer2;
   protected MessageProducer producer2;
   protected Destination consumerDestination2;
   protected Destination producerDestination2;

   /**
    * Set up a durable subscriber test.
    */
   @Override
   @BeforeEach
   public void setUp() throws Exception {
      this.durable = true;
      super.setUp();
   }

   /**
    * Test if all the messages sent are being received.
    */
   @Test
   public void testSendWhileClosed() throws Exception {
      connection2 = factory.createConnection();
      connection2.setClientID("test");
      connection2.start();
      session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      producer2 = session2.createProducer(null);
      producer2.setDeliveryMode(deliveryMode);
      producerDestination2 = this.createDestination2(session2, ActiveMQDestination.TOPIC_TYPE);
      Thread.sleep(1000);

      consumeSession2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumerDestination2 = this.createDestination2(session2, ActiveMQDestination.TOPIC_TYPE);
      consumer2 = consumeSession2.createDurableSubscriber((Topic) consumerDestination2, getName());
      Thread.sleep(1000);
      consumer2.close();
      TextMessage message = session2.createTextMessage("test");
      message.setStringProperty("test", "test");
      message.setJMSType("test");
      producer2.send(producerDestination2, message);
      consumer2 = consumeSession2.createDurableSubscriber((Topic) consumerDestination2, getName());
      Message msg = consumer2.receive(1000);
      assertNotNull(msg);
      assertEquals("test", ((TextMessage) msg).getText());
      assertEquals("test", msg.getJMSType());
      assertEquals("test", msg.getStringProperty("test"));
      connection2.stop();
      connection2.close();
   }

   @Override
   protected String getName() {
      return "testSendWhileClosed";
   }

}
