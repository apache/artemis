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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.DeliveryMode;
import javax.jms.Message;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class JMSExpirationHeaderTest extends MessageHeaderTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private volatile boolean testFailed;

   private volatile long effectiveReceiveTime;

   private volatile Message expectedMessage;



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      expectedMessage = null;
      testFailed = false;
      effectiveReceiveTime = 0;
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
   }

   // Tests ---------------------------------------------------------

   @Test
   public void testZeroExpiration() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m);
      ProxyAssertSupport.assertEquals(0, queueConsumer.receive().getJMSExpiration());
   }

   @Test
   public void testNoExpirationOnTimeoutReceive() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, 5000);

      // DeliveryImpl is asynch - need to give enough time to get to the consumer
      Thread.sleep(2000);

      Message result = queueConsumer.receive(10);
      ProxyAssertSupport.assertEquals(m.getJMSMessageID(), result.getJMSMessageID());
   }

   @Test
   public void testExpirationOnTimeoutReceive() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, 1000);

      // DeliveryImpl is asynch - need to give enough time to get to the consumer
      Thread.sleep(2000);

      ProxyAssertSupport.assertNull(queueConsumer.receiveNoWait());
   }

   @Test
   public void testExpirationOnReceiveNoWait() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, 1000);

      // DeliveryImpl is asynch - need to give enough time to get to the consumer
      Thread.sleep(2000);

      ProxyAssertSupport.assertNull(queueConsumer.receiveNoWait());
   }

   @Test
   public void testExpiredMessageDiscardingOnTimeoutReceive() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, 1000);

      // DeliveryImpl is asynch - need to give enough time to get to the consumer
      Thread.sleep(2000);

      // start the receiver thread
      final CountDownLatch latch = new CountDownLatch(1);
      Thread receiverThread = new Thread(() -> {
         try {
            expectedMessage = queueConsumer.receive(100);
         } catch (Exception e) {
            logger.trace("receive() exits with an exception", e);
         } finally {
            latch.countDown();
         }
      }, "receiver thread");
      receiverThread.start();

      ActiveMQTestBase.waitForLatch(latch);
      ProxyAssertSupport.assertNull(expectedMessage);
   }

   @Test
   public void testReceiveTimeoutPreservation() throws Exception {
      final long timeToWaitForReceive = 5000;

      final CountDownLatch receiverLatch = new CountDownLatch(1);

      // start the receiver thread
      Thread receiverThread = new Thread(() -> {
         try {
            long t1 = System.currentTimeMillis();
            expectedMessage = queueConsumer.receive(timeToWaitForReceive);
            effectiveReceiveTime = System.currentTimeMillis() - t1;
         } catch (Exception e) {
            logger.trace("receive() exits with an exception", e);
         } finally {
            receiverLatch.countDown();
         }
      }, "receiver thread");
      receiverThread.start();

      final CountDownLatch senderLatch = new CountDownLatch(1);

      // start the sender thread
      Thread senderThread = new Thread(() -> {
         try {
            // wait for 3 secs
            Thread.sleep(3000);

            // send an expired message
            Message m = queueProducerSession.createMessage();
            queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, -1);

            ActiveMQMessage msg = (ActiveMQMessage) m;

            if (!msg.getCoreMessage().isExpired()) {
               logger.error("The message {} should have expired", m);
               testFailed = true;
               return;
            }
         } catch (Exception e) {
            logger.error("This exception will fail the test", e);
            testFailed = true;
         } finally {
            senderLatch.countDown();
         }
      }, "sender thread");
      senderThread.start();

      ActiveMQTestBase.waitForLatch(senderLatch);
      ActiveMQTestBase.waitForLatch(receiverLatch);

      if (testFailed) {
         ProxyAssertSupport.fail("Test failed by the sender thread. Watch for exception in logs");
      }

      logger.trace("planned waiting time: {} effective waiting time {}", timeToWaitForReceive, effectiveReceiveTime);
      ProxyAssertSupport.assertTrue(effectiveReceiveTime >= timeToWaitForReceive);
      ProxyAssertSupport.assertTrue(effectiveReceiveTime < timeToWaitForReceive * 1.5); // well, how exactly I did come
      // up with this coeficient is
      // not clear even to me, I just
      // noticed that if I use 1.01
      // this assertion sometimes
      // fails;

      ProxyAssertSupport.assertNull(expectedMessage);
   }

   @Test
   public void testNoExpirationOnReceive() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, 5000);
      Message result = queueConsumer.receive();
      ProxyAssertSupport.assertEquals(m.getJMSMessageID(), result.getJMSMessageID());
   }

   @Test
   public void testExpirationOnReceive() throws Exception {
      final AtomicBoolean received = new AtomicBoolean(true);

      queueProducer.send(queueProducerSession.createMessage(), DeliveryMode.NON_PERSISTENT, 4, 2000);

      // allow the message to expire
      Thread.sleep(3000);

      // When a consumer is closed while a receive() is in progress it will make the
      // receive return with null

      final CountDownLatch latch = new CountDownLatch(1);
      // blocking read for a while to make sure I don't get anything, not even a null
      Thread receiverThread = new Thread(() -> {
         try {
            logger.trace("Attempting to receive");
            expectedMessage = queueConsumer.receive();

            // NOTE on close, the receive() call will return with null
            logger.trace("Receive exited without exception:{}", expectedMessage);

            if (expectedMessage == null) {
               received.set(false);
            }
         } catch (Exception e) {
            logger.trace("receive() exits with an exception", e);
            ProxyAssertSupport.fail();
         } catch (Throwable t) {
            logger.trace("receive() exits with a throwable", t);
            ProxyAssertSupport.fail();
         } finally {
            latch.countDown();
         }
      }, "receiver thread");
      receiverThread.start();

      Thread.sleep(3000);
      // receiverThread.interrupt();

      queueConsumer.close();

      // wait for the reading thread to conclude
      ActiveMQTestBase.waitForLatch(latch);

      logger.trace("Expected message:{}", expectedMessage);

      ProxyAssertSupport.assertFalse(received.get());
   }

   /*
    * Need to make sure that expired messages are acked so they get removed from the
    * queue/subscription, when delivery is attempted
    */
   @Test
   public void testExpiredMessageDoesNotGoBackOnQueue() throws Exception {
      Message m = queueProducerSession.createMessage();

      m.setStringProperty("weebles", "wobble but they don't fall down");

      queueProducer.send(m, DeliveryMode.NON_PERSISTENT, 4, 1000);

      // DeliveryImpl is asynch - need to give enough time to get to the consumer
      Thread.sleep(2000);

      ProxyAssertSupport.assertNull(queueConsumer.receiveNoWait());

      // Need to check message isn't still in queue

      checkEmpty(queue1);
   }

}
