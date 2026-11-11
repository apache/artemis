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

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSConsumer6Test extends BasicOpenWireTest {

   @Parameters(name = "destinationType={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{ActiveMQDestination.QUEUE_TYPE}, {ActiveMQDestination.TOPIC_TYPE}});
   }

   public byte destinationType;

   public JMSConsumer6Test(byte destinationType) {
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testPassMessageListenerIntoCreateConsumer() throws Exception {

      final AtomicInteger counter = new AtomicInteger(0);
      final CountDownLatch done = new CountDownLatch(1);

      // Receive a message with the JMS API
      connection.start();
      ActiveMQSession session = (ActiveMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination, m -> {
         counter.incrementAndGet();
         if (counter.get() == 4) {
            done.countDown();
         }
      });
      assertNotNull(consumer);

      // Send the messages
      sendMessages(session, destination, 4);

      assertTrue(done.await(1000, TimeUnit.MILLISECONDS));
      Thread.sleep(200);

      // Make sure only 4 messages were delivered.
      assertEquals(4, counter.get());
   }

   @TestTemplate
   public void testAckOfExpired() throws Exception {
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);

      MessageConsumer consumer = session.createConsumer(destination);
      connection.setStatsEnabled(true);

      Session sendSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = sendSession.createProducer(destination);
      producer.setTimeToLive(1000);
      final int count = 4;
      for (int i = 0; i < count; i++) {
         TextMessage message = sendSession.createTextMessage("" + i);
         producer.send(message);
      }

      // let first bunch in queue expire
      Thread.sleep(2000);

      producer.setTimeToLive(0);
      for (int i = 0; i < count; i++) {
         TextMessage message = sendSession.createTextMessage("no expiry" + i);
         producer.send(message);
      }

      ActiveMQMessageConsumer amqConsumer = (ActiveMQMessageConsumer) consumer;

      for (int i = 0; i < count; i++) {
         TextMessage msg = (TextMessage) amqConsumer.receive();
         assertNotNull(msg);
         assertTrue(msg.getText().contains("no expiry"), "message has \"no expiry\" text: " + msg.getText());

         // force an ack when there are expired messages
         amqConsumer.acknowledge();
      }
      assertEquals(count, amqConsumer.getConsumerStats().getExpiredMessageCount().getCount(), "consumer has expiredMessages");
   }

}
