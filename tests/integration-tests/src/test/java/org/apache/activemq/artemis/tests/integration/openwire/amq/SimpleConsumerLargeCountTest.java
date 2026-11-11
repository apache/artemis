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

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is to make sure a consumer on a transaction session, would receive all messages even
 * when the number of messages is bigger than prefetch.
 * <p>
 * Basically I am making sure flow control will still issue credits even thought the messages are not removed with a (ACK mode = remove)
 */
public class SimpleConsumerLargeCountTest extends BasicOpenWireTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testTopicReceiverFlowControlled() throws Exception {
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      ActiveMQTopic destination = (ActiveMQTopic) this.createDestination(session, ActiveMQDestination.TOPIC_TYPE);
      connection.start();

      // trying to starve the server if there's anything blocking on a create resources
      int numberOfSubscriptions = 50;
      // more messages than prefetch
      int numberOfMessages = 500;
      int rollbackAt = 50;

      ExecutorService service = Executors.newFixedThreadPool(numberOfSubscriptions);
      runAfter(service::shutdownNow);
      AtomicInteger errors = new AtomicInteger(0);

      CountDownLatch done = new CountDownLatch(numberOfSubscriptions);

      CyclicBarrier startFlag = new CyclicBarrier(numberOfSubscriptions + 1);
      for (int dest = 0; dest < numberOfSubscriptions; dest++) {
         final int finalDest = dest;
         service.execute(() -> {
            Connection connConsumer = null;
            try {
               connConsumer = factory.createConnection();
               connConsumer.setClientID("client" + finalDest);
               Session sessConsumer = connConsumer.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer consumer = sessConsumer.createDurableSubscriber(destination, "cons" + finalDest);
               connConsumer.start();
               startFlag.await(10, TimeUnit.SECONDS);

               for (int rollbackNumber = 0; rollbackNumber < 2; rollbackNumber++) {
                  for (int i = 0; i < rollbackAt; i++) {
                     TextMessage message = (TextMessage) consumer.receive(5000);
                     logger.debug("Received {}, dest={}", i, finalDest);
                     assertNotNull(message);
                     assertEquals(i, message.getIntProperty("i"));
                  }
                  // this is just to make the test more challenging. rollback destinations, make sure they won't block
                  // and flow control would stlil be okay on sending more messages
                  sessConsumer.rollback();
               }

               consumer.close();
               consumer = sessConsumer.createDurableSubscriber(destination, "cons" + finalDest);


               for (int i = 0; i < numberOfMessages; i++) {
                  TextMessage message = (TextMessage) consumer.receive(5000);
                  logger.debug("Received {}, dest={}", i, finalDest);
                  assertNotNull(message);
                  assertEquals(i, message.getIntProperty("i"));
               }
               sessConsumer.commit();
               assertNull(consumer.receiveNoWait());

            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               if (connConsumer != null) {
                  try {
                     connConsumer.close();
                  } catch (Throwable ignored) {
                  }
               }
               done.countDown();
            }
         });
      }

      startFlag.await(10, TimeUnit.SECONDS);

      MessageProducer producer = session.createProducer(destination);
      for (int i = 0; i < numberOfMessages; i++) {
         TextMessage message = session.createTextMessage("Message: " + i);
         message.setIntProperty("i", i);
         producer.send(message);
      }
      session.commit();

      assertTrue(done.await(1, TimeUnit.MINUTES));
      assertEquals(0, errors.get());
      service.shutdownNow();
      assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
   }
}