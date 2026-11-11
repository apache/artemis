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
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSConsumer7Test extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} ackMode={1} destinationType={2}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}});
   }

   public int deliveryMode;
   public int ackMode;
   public byte destinationType;

   public JMSConsumer7Test(int deliveryMode, int ackMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.ackMode = ackMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testMessageListenerOnMessageCloseUnackedWithPrefetch1StayInQueue() throws Exception {
      final AtomicInteger counter = new AtomicInteger(0);
      final CountDownLatch sendDone = new CountDownLatch(1);
      final CountDownLatch got2Done = new CountDownLatch(1);

      // Set prefetch to 1
      connection.getPrefetchPolicy().setAll(1);
      // This test case does not work if optimized message dispatch is used as
      // the main thread send block until the consumer receives the
      // message. This test depends on thread decoupling so that the main
      // thread can stop the consumer thread.
      connection.setOptimizedMessageDispatch(false);
      connection.start();

      // Use all the ack modes
      Session session = connection.createSession(false, ackMode);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);
      consumer.setMessageListener(m -> {
         try {
            TextMessage tm = (TextMessage) m;
            assertEquals("" + counter.get(), tm.getText());
            counter.incrementAndGet();
            if (counter.get() == 2) {
               sendDone.await();
               connection.close();
               got2Done.countDown();
            }
            tm.acknowledge();
         } catch (Throwable e) {
            e.printStackTrace();
         }
      });

      // Send the messages
      sendMessages(session, destination, 4);
      sendDone.countDown();

      // Wait for first 2 messages to arrive.
      assertTrue(got2Done.await(100000, TimeUnit.MILLISECONDS));

      // Re-start connection.
      connection = (ActiveMQConnection) factory.createConnection();

      connection.getPrefetchPolicy().setAll(1);
      connection.start();

      // Pickup the remaining messages.
      final CountDownLatch done2 = new CountDownLatch(1);
      session = connection.createSession(false, ackMode);
      consumer = session.createConsumer(destination);
      consumer.setMessageListener(m -> {
         try {
            // order is not guaranteed as the connection is started before
            // the listener is set.
            // assertEquals("" + counter.get(), tm.getText());
            counter.incrementAndGet();
            if (counter.get() == 4) {
               done2.countDown();
            }
         } catch (Throwable e) {
            System.err.println("Unexpected exception: " + e);
         }
      });

      assertTrue(done2.await(1000, TimeUnit.MILLISECONDS));
      Thread.sleep(200);

      // assert msg 2 was redelivered as close() from onMessages() will only ack
      // in auto_ack and dups_ok mode
      assertEquals(5, counter.get());
   }

}
