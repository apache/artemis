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

package org.apache.activemq.artemis.tests.integration.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.tests.integration.jms.RedeployTest;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederationQueueMatchXMLConfigParsingTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(20)
   public void testOpenWireOverCoreFederationDownstream() throws Exception {
      doTestSimpleQueueFederationOverCoreFederationDoownstream("OPENWIRE");
   }

   @Test
   @Timeout(20)
   public void testCoreOverCoreFederationDownstream() throws Exception {
      doTestSimpleQueueFederationOverCoreFederationDoownstream("CORE");
   }

   @Test
   @Timeout(20)
   public void testAMQPOverCoreFederationDownstream() throws Exception {
      doTestSimpleQueueFederationOverCoreFederationDoownstream("AMQP");
   }

   private void doTestSimpleQueueFederationOverCoreFederationDoownstream(String clientProtocol) throws Exception {
      final URL urlServer1 = RedeployTest.class.getClassLoader().getResource("core-federated-queue-match-server1.xml");
      final URL urlServer2 = RedeployTest.class.getClassLoader().getResource("core-federated-queue-match-server2.xml");

      final int MESSAGE_COUNT = 5;

      final CountDownLatch receivedAllLatch = new CountDownLatch(MESSAGE_COUNT);

      final EmbeddedActiveMQ embeddedActiveMQ1 = new EmbeddedActiveMQ();
      embeddedActiveMQ1.setConfigResourcePath(urlServer1.toURI().toString());
      embeddedActiveMQ1.start();

      final EmbeddedActiveMQ embeddedActiveMQ2 = new EmbeddedActiveMQ();
      embeddedActiveMQ2.setConfigResourcePath(urlServer2.toURI().toString());
      embeddedActiveMQ2.start();

      final ConnectionFactory consumerConnectionFactory = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:61618");
      final ConnectionFactory producerConnectionFactory = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:61616");

      try (Connection consumerConnection = consumerConnectionFactory.createConnection()) {

         final Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination consumerDestination = consumerSession.createQueue("exampleQueueTwo");
         final MessageConsumer consumer = consumerSession.createConsumer(consumerDestination);

         consumerConnection.start();

         consumer.setMessageListener((message) -> {
            logger.info("Received message: {} ", message);
            receivedAllLatch.countDown();
         });

         try (Connection producerConnection = producerConnectionFactory.createConnection()) {
            producerConnection.start();

            final Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            final Destination producerQueue = producerSession.createQueue("exampleQueueTwo");
            final MessageProducer producer = producerSession.createProducer(producerQueue);
            final UUID uuid = UUID.randomUUID();

            for (int i = 0; i < MESSAGE_COUNT; i++) {
               producer.send(producerSession.createTextMessage("Test message:" + uuid));
               logger.trace("Sent message: {}", uuid);
            }

            logger.info("Sent {} messages to queue for federation dispatch.", MESSAGE_COUNT);
         }

         assertTrue(receivedAllLatch.await(10, TimeUnit.SECONDS));
      } finally {
         try {
            embeddedActiveMQ1.stop();
         } catch (Exception ex) {
         }
         try {
            embeddedActiveMQ2.stop();
         } catch (Exception ex) {
         }
      }
   }

   @Test
   @Timeout(20)
   public void testQueuePolicyMatchesOnlyIndicatedQueueOpenwire() throws Exception {
      doTestQueueMatchPolicyOnlyMatchesIndicatedQueue("OPENWIRE");
   }

   @Test
   @Timeout(20)
   public void testQueuePolicyMatchesOnlyIndicatedQueueCore() throws Exception {
      doTestQueueMatchPolicyOnlyMatchesIndicatedQueue("CORE");
   }

   @Test
   @Timeout(20)
   public void testQueuePolicyMatchesOnlyIndicatedQueueAMQP() throws Exception {
      doTestQueueMatchPolicyOnlyMatchesIndicatedQueue("AMQP");
   }

   private void doTestQueueMatchPolicyOnlyMatchesIndicatedQueue(String clientProtocol) throws Exception {
      final URL urlServer1 = RedeployTest.class.getClassLoader().getResource("core-federated-queue-match-server1.xml");
      final URL urlServer2 = RedeployTest.class.getClassLoader().getResource("core-federated-queue-match-server2.xml");

      final int MESSAGE_COUNT = 5;

      final AtomicInteger receivedOnQ1Count = new AtomicInteger();
      final CountDownLatch receivedAllOnQ2Latch = new CountDownLatch(MESSAGE_COUNT);

      final EmbeddedActiveMQ embeddedActiveMQ1 = new EmbeddedActiveMQ();
      embeddedActiveMQ1.setConfigResourcePath(urlServer1.toURI().toString());
      embeddedActiveMQ1.start();

      final EmbeddedActiveMQ embeddedActiveMQ2 = new EmbeddedActiveMQ();
      embeddedActiveMQ2.setConfigResourcePath(urlServer2.toURI().toString());
      embeddedActiveMQ2.start();

      final ConnectionFactory consumerConnectionFactory = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:61616");
      final ConnectionFactory producerConnectionFactory = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:61618");

      try (Connection consumerConnection = consumerConnectionFactory.createConnection()) {

         final Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination consumerDestination1 = consumerSession.createQueue("exampleQueueOne");
         final Destination consumerDestination2 = consumerSession.createQueue("exampleQueueTwo");
         final MessageConsumer consumer1 = consumerSession.createConsumer(consumerDestination1);
         final MessageConsumer consumer2 = consumerSession.createConsumer(consumerDestination2);

         consumerConnection.start();

         consumer1.setMessageListener((message) -> {
            logger.info("Consumer #1 Received message: {} ", message);
            receivedOnQ1Count.incrementAndGet();
         });

         consumer2.setMessageListener((message) -> {
            logger.info("Consumer #2 Received message: {} ", message);
            receivedAllOnQ2Latch.countDown();
         });

         try (Connection producerConnection = producerConnectionFactory.createConnection()) {
            producerConnection.start();

            final Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            final Destination producerQueue1 = producerSession.createQueue("exampleQueueOne");
            final Destination producerQueue2 = producerSession.createQueue("exampleQueueTwo");
            final MessageProducer producer1 = producerSession.createProducer(producerQueue1);
            final MessageProducer producer2 = producerSession.createProducer(producerQueue2);
            final UUID uuid = UUID.randomUUID();

            for (int i = 0; i < MESSAGE_COUNT; i++) {
               producer1.send(producerSession.createTextMessage("Test message:" + uuid));
               producer2.send(producerSession.createTextMessage("Test message:" + uuid));
               logger.trace("Sent message: {}", uuid);
            }

            logger.info("Sent {} messages to queues for federation dispatch.", MESSAGE_COUNT);
         }

         assertTrue(receivedAllOnQ2Latch.await(10, TimeUnit.SECONDS));
         assertEquals(0, receivedOnQ1Count.get());
      } finally {
         try {
            embeddedActiveMQ1.stop();
         } catch (Exception ex) {
         }
         try {
            embeddedActiveMQ2.stop();
         } catch (Exception ex) {
         }
      }
   }
}
