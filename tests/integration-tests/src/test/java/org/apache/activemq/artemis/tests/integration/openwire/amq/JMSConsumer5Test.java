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

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSConsumer5Test extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} destinationType={1}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TEMP_TOPIC_TYPE}});
   }

   public int deliveryMode;
   public byte destinationType;

   public JMSConsumer5Test(int deliveryMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testSendReceiveBytesMessage() throws Exception {
      // Receive a message with the JMS API
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);
      MessageProducer producer = session.createProducer(destination);

      BytesMessage message = session.createBytesMessage();
      message.writeBoolean(true);
      message.writeBoolean(false);
      producer.send(message);

      // Make sure only 1 message was delivered.
      BytesMessage m = (BytesMessage) consumer.receive(1000);
      assertNotNull(m);
      assertTrue(m.readBoolean());
      assertFalse(m.readBoolean());

      assertNull(consumer.receiveNoWait());
   }

   @TestTemplate
   public void testSetMessageListenerAfterStart() throws Exception {
      final AtomicInteger counter = new AtomicInteger(0);
      final CountDownLatch done = new CountDownLatch(1);

      // Receive a message with the JMS API
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      sendMessages(session, destination, 4);

      // See if the message get sent to the listener
      consumer.setMessageListener(m -> {
         counter.incrementAndGet();
         if (counter.get() == 4) {
            done.countDown();
         }
      });

      assertTrue(done.await(1000, TimeUnit.MILLISECONDS));
      Thread.sleep(200);

      // Make sure only 4 messages were delivered.
      assertEquals(4, counter.get());
   }

}
