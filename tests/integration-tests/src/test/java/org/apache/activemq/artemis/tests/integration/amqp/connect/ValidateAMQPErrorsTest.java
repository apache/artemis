/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import static java.util.EnumSet.of;
import static org.apache.qpid.proton.engine.EndpointState.ACTIVE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnection;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.impl.ConnectionImpl;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.apache.qpid.protonj2.test.driver.ProtonTestPeer;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.ApplicationPropertiesMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.DeliveryAnnotationsMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.HeaderMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.MessageAnnotationsMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.PropertiesMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.transport.TransferPayloadCompositeMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.types.EncodedAmqpValueMatcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test will make sure the Broker Connection will react accordingly to a few misconfigs and possible errors on
 * either side of the connection.
 */
public class ValidateAMQPErrorsTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final int AMQP_PORT_2 = 5673;

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   /**
    * Connecting to itself should issue an error. and the max retry should still be counted, not just keep connecting
    * forever.
    */
   @Test
   @Timeout(30)
   public void testConnectItself() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + AMQP_PORT);
         amqpConnection.setReconnectAttempts(10).setRetryInterval(1);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();

         assertEquals(1, server.getBrokerConnections().size());
         server.getBrokerConnections().forEach((t) -> Wait.assertFalse(t::isStarted));
         Wait.assertTrue(() -> loggerHandler.findText("AMQ111001"), 5000, 25); // max retry
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         Thread.sleep(50);
         assertFalse(loggerHandler.findText("AMQ111002")); // there shouldn't be a retry after the last failure
         assertFalse(loggerHandler.findText("AMQ111003")); // there shouldn't be a retry after the last failure
      }
   }

   @Test
   @Timeout(30)
   public void testCloseLinkOnMirror() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         ActiveMQServer server2 = createServer(AMQP_PORT_2, false);

         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + AMQP_PORT_2);
         amqpConnection.setReconnectAttempts(1000).setRetryInterval(10);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();
         assertEquals(1, server.getBrokerConnections().size());
         Wait.assertTrue(() -> loggerHandler.findText("AMQ111002"));
         server.getBrokerConnections().forEach((t) -> Wait.assertTrue(() -> ((AMQPBrokerConnection) t).isConnecting()));

         server2.start();

         server.getBrokerConnections().forEach((t) -> Wait.assertFalse(() -> ((AMQPBrokerConnection) t).isConnecting()));

         createAddressAndQueues(server);

         Wait.assertTrue(() -> server2.locateQueue(getQueueName()) != null);

         Wait.assertEquals(1, server2.getRemotingService()::getConnectionCount);
         server2.getRemotingService().getConnections().forEach((t) -> {
            try {
               ActiveMQProtonRemotingConnection connection = (ActiveMQProtonRemotingConnection) t;
               ConnectionImpl protonConnection = (ConnectionImpl) connection.getAmqpConnection().getHandler().getConnection();
               Wait.waitFor(() -> protonConnection.linkHead(of(ACTIVE), of(ACTIVE)) != null);
               connection.getAmqpConnection().runNow(() -> {
                  Receiver receiver = (Receiver) protonConnection.linkHead(of(ACTIVE), of(ACTIVE));
                  receiver.close();
                  connection.flush();
               });
            } catch (Exception e) {
               e.printStackTrace();
            }
         });

         ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         ConnectionFactory cf2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);

         try (Connection connection = cf1.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));
            for (int i = 0; i < 10; i++) {
               producer.send(session.createTextMessage("message " + i));
            }
         }

         // messages should still flow after a disconnect on the link
         // the server should reconnect as if it was a failure
         try (Connection connection = cf2.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
            connection.start();
            for (int i = 0; i < 10; i++) {
               assertEquals("message " + i, ((TextMessage) consumer.receive(5000)).getText());
            }
         }
      }
   }

   @Test
   @Timeout(30)
   public void testCloseLinkOnSender() throws Exception {
      doCloseLinkTestImpl(true);
   }

   @Test
   @Timeout(30)
   public void testCloseLinkOnReceiver() throws Exception {
      doCloseLinkTestImpl(false);
   }

   private void doCloseLinkTestImpl(boolean isSender) throws Exception {
      AtomicInteger errors = new AtomicInteger(0);
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         ActiveMQServer server2 = createServer(AMQP_PORT_2, false);

         if (isSender) {
            AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + AMQP_PORT_2);
            amqpConnection.setReconnectAttempts(1000).setRetryInterval(10);
            amqpConnection.addElement(new AMQPBrokerConnectionElement().setMatchAddress(getQueueName()).setType(AMQPBrokerConnectionAddressType.SENDER));
            server.getConfiguration().addAMQPConnection(amqpConnection);
         } else {
            AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + AMQP_PORT);
            amqpConnection.setReconnectAttempts(1000).setRetryInterval(10);
            amqpConnection.addElement(new AMQPBrokerConnectionElement().setMatchAddress(getQueueName()).setType(AMQPBrokerConnectionAddressType.RECEIVER));
            server2.getConfiguration().addAMQPConnection(amqpConnection);
         }

         if (isSender) {
            server.start();
            assertEquals(1, server.getBrokerConnections().size());
         } else {
            server2.start();
            assertEquals(1, server2.getBrokerConnections().size());
         }
         Wait.assertTrue(() -> loggerHandler.findText("AMQ111002"));
         server.getBrokerConnections().forEach((t) -> Wait.assertTrue(() -> ((AMQPBrokerConnection) t).isConnecting()));

         if (isSender) {
            server2.start();
         } else {
            server.start();
         }

         server.getBrokerConnections().forEach((t) -> Wait.assertFalse(() -> ((AMQPBrokerConnection) t).isConnecting()));

         createAddressAndQueues(server);
         createAddressAndQueues(server2);

         Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null);
         Wait.assertTrue(() -> server2.locateQueue(getQueueName()) != null);

         ActiveMQServer serverReceivingConnections = isSender ? server2 : server;
         Wait.assertEquals(1, serverReceivingConnections.getRemotingService()::getConnectionCount);
         serverReceivingConnections.getRemotingService().getConnections().forEach((t) -> {
            try {
               ActiveMQProtonRemotingConnection connection = (ActiveMQProtonRemotingConnection) t;
               ConnectionImpl protonConnection = (ConnectionImpl) connection.getAmqpConnection().getHandler().getConnection();
               Wait.waitFor(() -> protonConnection.linkHead(of(ACTIVE), of(ACTIVE)) != null);
               connection.getAmqpConnection().runNow(() -> {
                  Link theLink = protonConnection.linkHead(of(ACTIVE), of(ACTIVE));
                  theLink.close();
                  connection.flush();
               });
            } catch (Exception e) {
               errors.incrementAndGet();
               // e.printStackTrace();
            }
         });

         Wait.assertEquals(1, () -> loggerHandler.countText("AMQ119021"));
      }

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      ConnectionFactory cf2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);

      try (Connection connection = cf1.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));
         for (int i = 0; i < 10; i++) {
            producer.send(session.createTextMessage("message " + i));
         }
      }

      // messages should still flow after a disconnect on the link
      // the server should reconnect as if it was a failure
      try (Connection connection = cf2.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         connection.start();
         for (int i = 0; i < 10; i++) {
            assertEquals("message " + i, ((TextMessage) consumer.receive(5000)).getText());
         }
      }

      assertEquals(0, errors.get());
   }

   @Test
   @Timeout(30)
   public void testTimeoutOnSenderOpen() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         // Initial attempt
         expectConnectionButDontRespondToSenderAttach(peer);
         // Second attempt (reconnect)
         expectConnectionButDontRespondToSenderAttach(peer);

         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

            AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(),
                  "tcp://localhost:" + remoteURI.getPort() + "?connect-timeout-millis=100");
            amqpConnection.setReconnectAttempts(1).setRetryInterval(100);
            amqpConnection.addElement(new AMQPBrokerConnectionElement().setMatchAddress(getQueueName()).setType(AMQPBrokerConnectionAddressType.SENDER));
            amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
            server.getConfiguration().addAMQPConnection(amqpConnection);
            server.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            assertEquals(2, loggerHandler.countText("AMQ119020")); // Initial + reconnect
            assertEquals(1, loggerHandler.countText("AMQ111001"));
         }
      }
   }

   @Test
   @Timeout(30)
   public void testReconnectAfterSenderOpenTimeout() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         // Initial attempt, times out
         expectConnectionButDontRespondToSenderAttach(peer);
         // Second attempt, times out (reconnect)
         expectConnectionButDontRespondToSenderAttach(peer);

         // Third attempt, succeeds (reconnect)
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().respondInKind()
                            .withProperty(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");
         peer.remoteFlow().withLinkCredit(1000).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

            AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(),
                  "tcp://localhost:" + remoteURI.getPort() + "?connect-timeout-millis=100");
            amqpConnection.setReconnectAttempts(10).setRetryInterval(100);
            amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());

            server.getConfiguration().addAMQPConnection(amqpConnection);
            server.start();

            int msgCount = 10;

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.expectTransfer().accept(); // Address create
            peer.expectTransfer().accept(); // Queue create
            for (int i = 0; i < msgCount; ++i) {
               expectMirroredJMSMessage(peer, i);
            }

            Wait.assertEquals(2, () -> loggerHandler.countText("AMQ119020"), 2000, 25);

            sendJMSMessage(msgCount, getQueueName());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }
      }
   }

   @Test
   @Timeout(30)
   public void testNoServerOfferedMirrorCapability() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         for (int i = 0; i < 3; ++i) {
            peer.expectSASLAnonymousConnect();
            peer.expectOpen().respond();
            peer.expectBegin().respond();
            peer.expectAttach().ofSender().respond(); // Omits mirror capabilities
            peer.expectConnectionToDrop();
         }
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
            final AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(
                  getTestName(), "tcp://localhost:" + remoteURI.getPort() + "?connect-timeout-millis=3000");
            amqpConnection.setReconnectAttempts(2).setRetryInterval(100);
            amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());

            server.getConfiguration().addAMQPConnection(amqpConnection);
            server.start();

            Wait.assertTrue(() -> loggerHandler.findText("AMQ111001"));
            assertEquals(3, loggerHandler.countText("AMQ119018"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }
      }
   }

   /**
    * Refuse the first mirror link, verify broker handles it and reconnects
    */
   @Test
   @Timeout(30)
   public void testReconnectAfterMirrorLinkRefusal() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         // First attempt, refuse
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender().respond().withNullTarget();
         peer.remoteDetach().withErrorCondition(AmqpError.ILLEGAL_STATE.toString(), "Testing refusal of mirror link for $reasons").queue();
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         // Second attempt, succeeds
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().respondInKind()
                            .withProperty(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");
         peer.remoteFlow().withLinkCredit(1000).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(),
            "tcp://localhost:" + remoteURI.getPort() + "?connect-timeout-millis=3000");
         amqpConnection.setReconnectAttempts(1).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         int msgCount = 10;

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         for (int i = 0; i < msgCount; ++i) {
            expectMirroredJMSMessage(peer, i);
         }

         sendJMSMessage(msgCount, getQueueName());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(30)
   public void testNoClientDesiredMirrorCapability() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();

         final String address = ProtonProtocolManager.getMirrorAddress(getTestName());

         try (ProtonTestClient receivingPeer = new ProtonTestClient()) {
            receivingPeer.queueClientSaslAnonymousConnect();
            receivingPeer.connect("localhost", AMQP_PORT);
            receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            receivingPeer.expectOpen();
            receivingPeer.expectBegin();
            receivingPeer.expectAttach().withNullTarget();
            receivingPeer.expectDetach().withError(AmqpError.ILLEGAL_STATE.toString(),
                                                   Matchers.containsString("AMQ119024"))
                                        .respond();
            receivingPeer.remoteOpen().withContainerId("test-sender").now();
            receivingPeer.remoteBegin().now();
            receivingPeer.remoteAttach().ofSender()
                                        .withInitialDeliveryCount(0)
                                        .withName("mirror-test")
                                        .withTarget().withAddress(address).also()
                                        .withSource().and()
                                        .now();
            receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         Wait.assertTrue(() -> loggerHandler.findText("AMQ119024"));
      }
   }

   private static void expectConnectionButDontRespondToSenderAttach(ProtonTestServer peer) {
      peer.expectSASLAnonymousConnect();
      peer.expectOpen().respond();
      peer.expectBegin().respond();
      peer.expectAttach().ofSender(); //No response, causes timeout
      peer.expectConnectionToDrop();
   }

   private static void sendJMSMessage(int msgCount, String queueName) throws JMSException {
      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection()) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageProducer producer = session.createProducer(session.createQueue(queueName));

         for (int i = 0; i < msgCount; i++) {
            final TextMessage message = session.createTextMessage("hello");

            message.setIntProperty("sender", i);

            producer.send(message);
         }
      }
   }

   private static void expectMirroredJMSMessage(ProtonTestPeer peer, int sequence) {
      final HeaderMatcher headerMatcher = new HeaderMatcher(true);
      final PropertiesMatcher properties = new PropertiesMatcher(true);
      final DeliveryAnnotationsMatcher daMatcher = new DeliveryAnnotationsMatcher(true);
      final MessageAnnotationsMatcher annotationsMatcher = new MessageAnnotationsMatcher(true);
      final ApplicationPropertiesMatcher apMatcher = new ApplicationPropertiesMatcher(true);
      apMatcher.withEntry("sender", equalTo(sequence));
      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher("hello");
      final TransferPayloadCompositeMatcher matcher = new TransferPayloadCompositeMatcher();
      matcher.setHeadersMatcher(headerMatcher);
      matcher.setPropertiesMatcher(properties);
      matcher.setDeliveryAnnotationsMatcher(daMatcher);
      matcher.setMessageAnnotationsMatcher(annotationsMatcher);
      matcher.setApplicationPropertiesMatcher(apMatcher);
      matcher.addMessageContentMatcher(bodyMatcher);

      peer.expectTransfer().withPayload(matcher).accept();
   }
}
