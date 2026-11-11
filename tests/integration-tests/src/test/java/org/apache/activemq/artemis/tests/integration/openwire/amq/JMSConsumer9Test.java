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

import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSConsumer9Test extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} destinationType={1}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TEMP_TOPIC_TYPE}});
   }

   public int deliveryMode;
   public byte destinationType;

   public JMSConsumer9Test(int deliveryMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testMessageListenerWithConsumerWithPrefetch1() throws Exception {

      final AtomicInteger counter = new AtomicInteger(0);
      final CountDownLatch done = new CountDownLatch(1);

      // Receive a message with the JMS API
      connection.getPrefetchPolicy().setAll(1);
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);
      consumer.setMessageListener(m -> {
         counter.incrementAndGet();
         if (counter.get() == 4) {
            done.countDown();
         }
      });

      // Send the messages
      sendMessages(session, destination, 4);

      assertTrue(done.await(1000, TimeUnit.MILLISECONDS));
      Thread.sleep(200);

      // Make sure only 4 messages were delivered.
      assertEquals(4, counter.get());
   }

   @TestTemplate
   @Disabled
   public void testMessageListenerWithConsumer() throws Exception {

      final AtomicInteger counter = new AtomicInteger(0);
      final CountDownLatch done = new CountDownLatch(1);

      // Receive a message with the JMS API
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);
      consumer.setMessageListener(m -> {
         counter.incrementAndGet();
         if (counter.get() == 4) {
            done.countDown();
         }
      });

      // Send the messages
      sendMessages(session, destination, 4);

      assertTrue(done.await(1000, TimeUnit.MILLISECONDS));
      Thread.sleep(200);

      // Make sure only 4 messages were delivered.
      assertEquals(4, counter.get());
   }

}
