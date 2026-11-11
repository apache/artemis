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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.ActiveMQConnection;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.ProducerFlowControlTest
 */
public class ProducerFlowControlTest extends ProducerFlowControlBaseTest {

   @Test
   public void test2ndPublisherWithProducerWindowSendConnectionThatIsBlocked() throws Exception {
      factory.setProducerWindowSize(1024 * 64);
      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      flowControlConnection.start();

      Session session = flowControlConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queueB);

      // Test sending to Queue A
      // 1 few sends should not block until the producer window is used up.
      fillQueue(queueA);

      // Test sending to Queue B it should not block since the connection
      // should not be blocked.
      CountDownLatch pubishDoneToQeueuB = asyncSendTo(queueB, "Message 1");
      assertTrue(pubishDoneToQeueuB.await(2, TimeUnit.SECONDS));

      TextMessage msg = (TextMessage) consumer.receive();
      assertEquals("Message 1", msg.getText());
      msg.acknowledge();

      pubishDoneToQeueuB = asyncSendTo(queueB, "Message 2");
      assertTrue(pubishDoneToQeueuB.await(2, TimeUnit.SECONDS));

      msg = (TextMessage) consumer.receive(5000);
      assertNotNull(msg);
      assertEquals("Message 2", msg.getText());
      msg.acknowledge();

      consumer.close();
   }

   @Test
   public void testPublisherRecoverAfterBlock() throws Exception {
      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      flowControlConnection.start();

      final Session session = flowControlConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      final MessageProducer producer = session.createProducer(queueA);

      final AtomicBoolean done = new AtomicBoolean(true);
      final AtomicBoolean keepGoing = new AtomicBoolean(true);

      Thread thread = new Thread("Filler") {
         int i;

         @Override
         public void run() {
            while (keepGoing.get()) {
               done.set(false);
               try {
                  producer.send(session.createTextMessage("Test message " + ++i));
               } catch (JMSException e) {
                  break;
               }
            }
         }
      };
      thread.start();
      waitForBlockedOrResourceLimit(done);

      // after receiveing messges, producer should continue sending messages
      // (done == false)
      MessageConsumer consumer = session.createConsumer(queueA);
      TextMessage msg;
      for (int idx = 0; idx < 5; ++idx) {
         msg = (TextMessage) consumer.receive(1000);
         msg.acknowledge();
      }
      Thread.sleep(1000);
      keepGoing.set(false);

      consumer.close();
      assertFalse(done.get(), "producer has resumed");
   }

   @Test
   public void testAsyncPublisherRecoverAfterBlock() throws Exception {
      factory.setProducerWindowSize(1024 * 5);
      factory.setUseAsyncSend(true);
      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      flowControlConnection.start();

      final Session session = flowControlConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      final MessageProducer producer = session.createProducer(queueA);

      final AtomicBoolean done = new AtomicBoolean(true);
      final AtomicBoolean keepGoing = new AtomicBoolean(true);

      Thread thread = new Thread("Filler") {
         int i;

         @Override
         public void run() {
            while (keepGoing.get()) {
               done.set(false);
               try {
                  producer.send(session.createTextMessage("Test message " + ++i));
               } catch (JMSException e) {
               }
            }
         }
      };
      thread.start();
      waitForBlockedOrResourceLimit(done);

      // after receiveing messges, producer should continue sending messages
      // (done == false)
      MessageConsumer consumer = session.createConsumer(queueA);
      TextMessage msg;
      for (int idx = 0; idx < 5; ++idx) {
         msg = (TextMessage) consumer.receive(1000);
         assertNotNull(msg, "Got a message");
         msg.acknowledge();
      }
      Thread.sleep(1000);
      keepGoing.set(false);

      consumer.close();
      assertFalse(done.get(), "producer has resumed");
   }

   @Test
   public void test2ndPublisherWithSyncSendConnectionThatIsBlocked() throws Exception {
      factory.setAlwaysSyncSend(true);
      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      flowControlConnection.start();

      Session session = flowControlConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queueB);

      // Test sending to Queue A
      // 1st send should not block. But the rest will.
      fillQueue(queueA);

      // Test sending to Queue B it should not block.
      CountDownLatch pubishDoneToQeueuB = asyncSendTo(queueB, "Message 1");
      assertTrue(pubishDoneToQeueuB.await(2, TimeUnit.SECONDS));

      TextMessage msg = (TextMessage) consumer.receive();
      assertEquals("Message 1", msg.getText());
      msg.acknowledge();

      pubishDoneToQeueuB = asyncSendTo(queueB, "Message 2");
      assertTrue(pubishDoneToQeueuB.await(2, TimeUnit.SECONDS));

      msg = (TextMessage) consumer.receive();
      assertEquals("Message 2", msg.getText());
      msg.acknowledge();
      consumer.close();
   }

   @Test
   public void testSimpleSendReceive() throws Exception {
      factory.setAlwaysSyncSend(true);
      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      flowControlConnection.start();

      Session session = flowControlConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queueA);

      // Test sending to Queue B it should not block.
      CountDownLatch pubishDoneToQeueuA = asyncSendTo(queueA, "Message 1");
      assertTrue(pubishDoneToQeueuA.await(2, TimeUnit.SECONDS));

      TextMessage msg = (TextMessage) consumer.receive();
      assertEquals("Message 1", msg.getText());
      msg.acknowledge();

      pubishDoneToQeueuA = asyncSendTo(queueA, "Message 2");
      assertTrue(pubishDoneToQeueuA.await(2, TimeUnit.SECONDS));

      msg = (TextMessage) consumer.receive();
      assertEquals("Message 2", msg.getText());
      msg.acknowledge();
      consumer.close();
   }

   @Test
   public void test2ndPublisherWithStandardConnectionThatIsBlocked() throws Exception {
      flowControlConnection = (ActiveMQConnection) factory.createConnection();
      flowControlConnection.start();

      // Test sending to Queue A
      // 1st send should not block.
      fillQueue(queueA);

      // Test sending to Queue B it should block.
      // Since even though the it's queue limits have not been reached, the
      // connection
      // is blocked.
      CountDownLatch pubishDoneToQeueuB = asyncSendTo(queueB, "Message 1");
      assertFalse(pubishDoneToQeueuB.await(2, TimeUnit.SECONDS));
   }
}
