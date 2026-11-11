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

package org.apache.activemq.artemis.tests.integration.amqp.connect;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test the Receiver functionality on AMQP broker connections
 */
@Timeout(20)
public class AMQPBrokerConnectionReceiverTest extends AmqpClientTestSupport {

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      // Creates the broker used to make the outgoing connection. The port passed is for
      // that brokers acceptor. The test server connected to by the broker binds to a random port.
      return createServer(AMQP_PORT, false);
   }

   @Test
   public void testBrokerConnectionCreatesReceiverOnRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver().respondInKind();
         peer.expectFlow();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         LOG.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();
         element.setType(AMQPBrokerConnectionAddressType.RECEIVER);
         element.setName(getTestName());
         element.setMatchAddress("test");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);
         amqpConnection.addElement(element);
         amqpConnection.setAutostart(true);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("test")
                                                         .setAutoCreated(false));

         peer.waitForScriptToComplete();
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete();
         peer.close();
      }
   }

   @Test
   public void testIncomingMessageWithNoToFieldArrivesOnConfiguredAddress() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         LOG.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();
         element.setType(AMQPBrokerConnectionAddressType.RECEIVER);
         element.setName(getTestName());
         element.setMatchAddress("test");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);
         amqpConnection.addElement(element);
         amqpConnection.setAutostart(true);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver().withSource().withAddress("test").and()
                                         .withTarget().withAddress("test").and()
                                         .respondInKind();
         peer.expectFlow();
         peer.remoteTransfer().withDeliveryId(1)
                              .withBody().withString("test-body").also()
                              .queue();
         peer.expectDisposition().withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic("test");
            final MessageConsumer consumer = session.createConsumer(topic);

            connection.start();

            final Message received = consumer.receive(5_000);
            assertNotNull(received);

            consumer.close();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete();
         peer.close();
      }
   }

   @Test
   public void testIncomingMessageWithToFieldArrivesOnConfiguredAddress() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         LOG.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();
         element.setType(AMQPBrokerConnectionAddressType.RECEIVER);
         element.setName(getTestName());
         element.setMatchAddress("test");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);
         amqpConnection.addElement(element);
         amqpConnection.setAutostart(true);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver().withSource().withAddress("test").and()
                                         .withTarget().withAddress("test").and()
                                         .respondInKind();
         peer.expectFlow();
         peer.remoteTransfer().withDeliveryId(1)
                              .withProperties().withTo("should-not-be-used").also()
                              .withBody().withString("test-body").also()
                              .queue();
         peer.expectDisposition().withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic("test");
            final MessageConsumer consumer = session.createConsumer(topic);

            connection.start();

            final Message received = consumer.receive(5_000);
            assertNotNull(received);

            consumer.close();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete();
         peer.close();
      }
   }

   @Test
   public void testBrokerConnectionRetriesReceiverOnRemoteIfAttachRejected() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver().reject(true, LinkError.DETACH_FORCED.toString(), "Attach refused");
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver().respondInKind();
         peer.expectFlow();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         LOG.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();
         element.setType(AMQPBrokerConnectionAddressType.RECEIVER);
         element.setName(getTestName());
         element.setMatchAddress("test");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(10);
         amqpConnection.setRetryInterval(100);
         amqpConnection.addElement(element);
         amqpConnection.setAutostart(true);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("test")
                                                         .setAutoCreated(false));

         peer.waitForScriptToComplete();
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete();
         peer.close();
      }
   }

   @Test
   public void testBrokerConnectionRetriesReceiverOnRemoteIfTargetQueueRemovedAndLaterAddedBack() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.execute(() -> {
            try {
               server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                               .setAddress("test")
                                                               .setAutoCreated(false));
            } catch (Exception e) {
               LOG.warn("Error on creating server address and queue: ", e);
            }
         }).queue();
         peer.expectAttach().ofReceiver();
         peer.execute(() -> {
            try {
               server.removeAddressInfo(SimpleString.of("test"), null, true);
            } catch (Exception e) {
               LOG.warn("Error on removing server address and queue: ", e);
            }
            peer.respondToLastAttach().now();
         }).queue();
         peer.expectDetach().respond();
         peer.execute(() -> {
            try {
               server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                               .setAddress("test")
                                                               .setAutoCreated(false));
            } catch (Exception e) {
               LOG.warn("Error on creating server address and queue: ", e);
            }
         }).queue();
         peer.expectAttach().ofReceiver().respondInKind();
         peer.expectFlow();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         LOG.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();
         element.setType(AMQPBrokerConnectionAddressType.RECEIVER);
         element.setName(getTestName());
         element.setMatchAddress("test");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(10);
         amqpConnection.setRetryInterval(50);
         amqpConnection.addElement(element);
         amqpConnection.setAutostart(true);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete();
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete();
         peer.close();
      }
   }
}
