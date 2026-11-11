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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AmqpCoreTunnelingSupportTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Captured CORE wrapped AMQP transfer payload. (TextMessage whose body = "Hello")
   private static byte[] CORE_MESSAGE_PAYLOAD =
      {0, 83, 117, -80, 0, 0, 1, 68, 0, 0, 1, 64, 0, 0, 0, 28, 1, 0, 0, 0, 10, 72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 0, 0, 0,
       0, 0, 0, 0, 80, 1, 0, 0, 0, 108, 116, 0, 101, 0, 115, 0, 116, 0, 82, 0, 101, 0, 99, 0, 101, 0, 105, 0, 118, 0, 101, 0,
       114, 0, 84, 0, 104, 0, 97, 0, 116, 0, 79, 0, 102, 0, 102, 0, 101, 0, 114, 0, 115, 0, 67, 0, 111, 0, 114, 0, 101, 0, 84,
       0, 117, 0, 110, 0, 110, 0, 101, 0, 108, 0, 105, 0, 110, 0, 103, 0, 71, 0, 101, 0, 116, 0, 115, 0, 68, 0, 101, 0, 115, 0,
       105, 0, 114, 0, 101, 0, 100, 0, 82, 0, 101, 0, 115, 0, 112, 0, 111, 0, 110, 0, 115, 0, 101, 0, 1, -51, 105, -29, -51, -3,
       -19, 17, -17, -72, -69, -88, -95, 89, -21, -74, -92, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, -107, -127, -69, -39, -72, 4,
       1, 0, 0, 0, 2, 0, 0, 0, 18, 95, 0, 95, 0, 65, 0, 77, 0, 81, 0, 95, 0, 67, 0, 73, 0, 68, 0, 10, 0, 0, 0, 72, 99, 0, 100, 0,
       54, 0, 53, 0, 57, 0, 101, 0, 48, 0, 57, 0, 45, 0, 102, 0, 100, 0, 101, 0, 100, 0, 45, 0, 49, 0, 49, 0, 101, 0, 102, 0, 45,
       0, 98, 0, 56, 0, 98, 0, 98, 0, 45, 0, 97, 0, 56, 0, 97, 0, 49, 0, 53, 0, 57, 0, 101, 0, 98, 0, 98, 0, 54, 0, 97, 0, 52, 0,
       0, 0, 0, 34, 95, 0, 65, 0, 77, 0, 81, 0, 95, 0, 82, 0, 79, 0, 85, 0, 84, 0, 73, 0, 78, 0, 71, 0, 95, 0, 84, 0, 89, 0, 80, 0,
       69, 0, 3, 1};

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      // Creates the broker used to make the outgoing connection. The port passed is for
      // that brokers acceptor. The test server connected to by the broker binds to a random port.
      return createServer(AMQP_PORT, true);
   }

   @Test
   @Timeout(20)
   public void testReceiverThatOffersCoreTunnelingGetsDesiredResponse() throws Exception {
      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofSender().withDesiredCapability(CORE_MESSAGE_TUNNELING_SUPPORT.toString());
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofReceiver()
                            .withOfferedCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withSource().withAddress(getTestName())
                                         .withCapabilities("queue").also()
                            .withTarget().also()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectTransfer().withMessageFormat(AMQP_TUNNELED_CORE_MESSAGE_FORMAT).accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));

            producer.send(session.createTextMessage("Hello"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testReceiverThatDoesNotOffersCoreTunnelingGetsNoDesiredResponse() throws Exception {
      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofSender().withDesiredCapabilities(nullValue());
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofReceiver()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withSource().withAddress(getTestName()).also()
                            .withTarget().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testSenderthatDesiresCoreTunnelingGetsOfferedResponse() throws Exception {
      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver().withOfferedCapability(CORE_MESSAGE_TUNNELING_SUPPORT.toString());
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withDesiredCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Send core tunneled message which should then get to the core receiver as the original Text Message
         peer.remoteTransfer().withMessageFormat(AMQP_TUNNELED_CORE_MESSAGE_FORMAT)
                              .withDeliveryId(0)
                              .withPayload(CORE_MESSAGE_PAYLOAD)
                              .withSettled(true)
                              .withState().accepted()
                              .now();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            final Message received = consumer.receive(5_000);

            assertNotNull(received);
            assertTrue(received instanceof TextMessage);

            final TextMessage textMessage = (TextMessage) received;

            assertEquals("Hello", textMessage.getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testSenderthatDoesNotDesireCoreTunnelingDoesNotGetOfferedResponse() throws Exception {
      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver().withOfferedCapabilities(nullValue());
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.close();
      }
   }
}
