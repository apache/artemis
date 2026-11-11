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

import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CONNECTION_FORCED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.TUNNEL_CORE_MESSAGES;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.hamcrest.Matchers.nullValue;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test some basic expected behaviors of the broker mirror connection.
 */
public class AMQPMirrorConnectionTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int BROKER_PORT_NUM = AMQP_PORT + 1;

   @Override
   protected ActiveMQServer createServer() throws Exception {
      // Creates the broker used to make the outgoing connection. The port passed is for
      // that brokers acceptor. The test server connected to by the broker binds to a random port.
      return createServer(BROKER_PORT_NUM, false);
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }

   @Test
   @Timeout(20)
   public void testBrokerMirrorConnectsWithAnonymous() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerMirrorConnectsWithPlain() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerHandlesSenderLinkOmitsMirrorCapability() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond(); // Response omits "amq.mirror" in offered capabilities.
         peer.expectClose().withError(CONNECTION_FORCED.toString()).optional(); // Can hit the wire in rare instances.
         peer.expectConnectionToDrop();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerAddsAddressAndQueue() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setQueueCreation(true)
                                                                          .setAddressFilter("sometest"));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         server.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("sometest").setDurable(true));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testCreateDurableConsumerReplicatesAddressAndQueue() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setQueueCreation(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + BROKER_PORT_NUM);

         try (Connection connection = factory.createConnection()) {
            connection.setClientID("test-client-id");
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Topic topic = session.createTopic("test-topic");
            MessageConsumer consumer = session.createDurableConsumer(topic, "subscription");

            consumer.close();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerMirrorHonorsCoreTunnelingEnable() throws Exception {
      testBrokerMirrorHonorsCoreTunnelingEnableOrDisable(true);
   }

   @Test
   @Timeout(20)
   public void testBrokerMirrorHonorsCoreTunnelingDisable() throws Exception {
      testBrokerMirrorHonorsCoreTunnelingEnableOrDisable(false);
   }

   public void testBrokerMirrorHonorsCoreTunnelingEnableOrDisable(boolean tunneling) throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      final String[] capabilities;

      if (tunneling) {
         capabilities = new String[] {"amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString()};
      } else {
         capabilities = new String[] {"amq.mirror"};
      }

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities(capabilities)
                            .respond()
                            .withOfferedCapabilities(capabilities)
                            .withPropertiesMap(brokerProperties);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirrorElement = new AMQPMirrorBrokerConnectionElement();
         mirrorElement.addProperty(TUNNEL_CORE_MESSAGES, Boolean.toString(tunneling));

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirrorElement);
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testProducerMessageIsMirroredWithCoreTunnelingUsesCoreMessageFormat() throws Exception {
      doTestProducerMessageIsMirroredWithCorrectMessageFormat(true);
   }

   @Test
   @Timeout(20)
   public void testProducerMessageIsMirroredWithoutCoreTunnelingUsesDefaultMessageFormat() throws Exception {
      doTestProducerMessageIsMirroredWithCorrectMessageFormat(false);
   }

   private void doTestProducerMessageIsMirroredWithCorrectMessageFormat(boolean tunneling) throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      final String[] capabilities;
      final int messageFormat;

      if (tunneling) {
         capabilities = new String[] {"amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString()};
         messageFormat = AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
      } else {
         capabilities = new String[] {"amq.mirror"};
         messageFormat = 0; // AMQP default
      }

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities(capabilities)
                            .respond()
                            .withOfferedCapabilities(capabilities)
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.expectTransfer().withMessageFormat(messageFormat).accept(); // Producer Message
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirrorElement = new AMQPMirrorBrokerConnectionElement();
         mirrorElement.addProperty(TUNNEL_CORE_MESSAGES, Boolean.toString(tunneling));
         mirrorElement.setQueueCreation(true);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirrorElement);

         server.createQueue(QueueConfiguration.of("myQueue").setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + BROKER_PORT_NUM);

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = session.createQueue("myQueue");
            MessageConsumer consumer = session.createConsumer(queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage message = session.createTextMessage("test");

            connection.start();

            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);

            consumer.close();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteDoesNotOfferTunnelingResultsInDefaultAMQPFormattedMessages() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror")
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.expectTransfer().withMessageFormat(0).accept(); // Producer Message
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirrorElement = new AMQPMirrorBrokerConnectionElement();
         mirrorElement.addProperty(TUNNEL_CORE_MESSAGES, Boolean.toString(true));
         mirrorElement.setQueueCreation(true);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirrorElement);

         server.createQueue(QueueConfiguration.of("myQueue").setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + BROKER_PORT_NUM);

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = session.createQueue("myQueue");
            MessageConsumer consumer = session.createConsumer(queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage message = session.createTextMessage("test");

            connection.start();

            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);

            consumer.close();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testTunnelingDisabledButRemoteOffersDoesNotUseTunneling() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror")
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.expectTransfer().withMessageFormat(0).accept(); // Producer Message
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirrorElement = new AMQPMirrorBrokerConnectionElement();
         mirrorElement.addProperty(TUNNEL_CORE_MESSAGES, Boolean.toString(false));
         mirrorElement.setQueueCreation(true);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirrorElement);

         server.createQueue(QueueConfiguration.of("myQueue").setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + BROKER_PORT_NUM);

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = session.createQueue("myQueue");
            MessageConsumer consumer = session.createConsumer(queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage message = session.createTextMessage("test");

            connection.start();

            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);

            consumer.close();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testMirrorConnectionRemainsUnchangedAfterConfigurationUpdate() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement();
         mirror.setQueueCreation(true);
         mirror.setDurable(true);
         mirror.setName("test");

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirror);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + BROKER_PORT_NUM);

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = session.createQueue("myQueue");
            MessageConsumer consumer = session.createConsumer(queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage message = session.createTextMessage("test");

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            AMQPMirrorBrokerConnectionElement mirrorUpdated = new AMQPMirrorBrokerConnectionElement();
            mirrorUpdated.setQueueCreation(true);
            mirrorUpdated.setDurable(false);
            mirrorUpdated.setName("test");

            AMQPBrokerConnectConfiguration amqpConnectionUpdated =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
            amqpConnectionUpdated.setReconnectAttempts(0);// No reconnects
            amqpConnectionUpdated.setUser("user1");
            amqpConnectionUpdated.setPassword("pass1");
            amqpConnectionUpdated.addElement(mirrorUpdated);

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            server.getConfiguration().clearAMQPConnectionConfigurations();
            server.getConfiguration().addAMQPConnection(amqpConnectionUpdated);

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            // Should be ignored as mirror connections cannot be updated.

            peer.waitForScriptToComplete();
            peer.expectTransfer().withMessageFormat(0).accept(); // Producer Message

            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);

            consumer.close();

            peer.waitForScriptToComplete();
         }

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testMirrorConnectionRemainsUnchangedAfterConfigurationRemoved() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement();
         mirror.setQueueCreation(true);
         mirror.setDurable(true);
         mirror.setName("test");

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirror);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + BROKER_PORT_NUM);

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = session.createQueue("myQueue");
            MessageConsumer consumer = session.createConsumer(queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage message = session.createTextMessage("test");

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            // Clear and update is essentially a remove of old configuration
            server.getConfiguration().clearAMQPConnectionConfigurations();

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            // Should be ignored as mirror connections cannot be updated.

            peer.waitForScriptToComplete();
            peer.expectTransfer().withMessageFormat(0).accept(); // Producer Message

            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);

            consumer.close();

            peer.waitForScriptToComplete();
         }

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testMirrorConnectionRecoversIfLocalSNFConsumerIsForcedClosed() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      final String snfQueueName = "$ACTIVEMQ_ARTEMIS_MIRROR_" + getTestName();

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement();
         mirror.setQueueCreation(true);
         mirror.setDurable(true);
         mirror.setName(getTestName());

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(5);// Allow some reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirror);
         amqpConnection.setRetryInterval(100);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setDurable(true));

         peer.waitForScriptToComplete();
         peer.expectDetach().respond();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();

         final org.apache.activemq.artemis.core.server.Queue snfQueue = server.locateQueue(snfQueueName);
         assertNotNull(snfQueue);
         Wait.assertTrue(() -> snfQueue.getConsumerCount() == 1, 5_000, 100);

         // Close the SNF consumer on the local broker which should trigger rebuild or mirror connection
         final ServerConsumer snfConsumer = (ServerConsumer) snfQueue.getConsumers().stream().findFirst().get();
         assertNotNull(snfConsumer);
         assertFalse(snfConsumer.isClosed());
         final RemotingConnection connection = server.getRemotingService().getConnection(snfConsumer.getConnectionID());
         assertNotNull(connection);
         assertFalse(connection.isDestroyed());

         try {
            server.getActiveMQServerControl().closeConsumerWithID(snfConsumer.getSessionID(), String.valueOf(snfConsumer.getSequentialID()));
         } catch (Exception e) {
            fail("Should not have thrown an error closing the SNF consumer manually: " + e.getMessage());
         }

         Wait.assertTrue(() -> snfConsumer.isClosed(), 5_000, 100);

         peer.waitForScriptToComplete();

         Wait.assertTrue(() -> snfQueue.getConsumerCount() == 1, 5_000, 100);
      }
   }

   @Test
   @Timeout(20)
   public void testMirrorConnectionRecoversIfSourceConnectionIsManuallyClosed() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      final String snfQueueName = "$ACTIVEMQ_ARTEMIS_MIRROR_" + getTestName();

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement();
         mirror.setQueueCreation(true);
         mirror.setDurable(true);
         mirror.setName(getTestName());

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(5);// Allow some reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirror);
         amqpConnection.setRetryInterval(100);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setDurable(true));

         peer.waitForScriptToComplete();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();

         final org.apache.activemq.artemis.core.server.Queue snfQueue = server.locateQueue(snfQueueName);
         assertNotNull(snfQueue);
         Wait.assertTrue(() -> snfQueue.getConsumerCount() == 1, 5_000, 100);

         // Close consumer connection which should trigger a rebuild of the mirror broker connection since
         // the broker connection itself wasn't stopped.
         final ServerConsumer snfConsumer = (ServerConsumer) snfQueue.getConsumers().stream().findFirst().get();
         final RemotingConnection connection = server.getRemotingService().getConnection(snfConsumer.getConnectionID());

         try {
            connection.close();
         } catch (Exception e) {
            fail("Should not have thrown an error closing the SNF connection manually: " + e.getMessage());
         }

         Wait.assertTrue(() -> connection.isDestroyed(), 5_000, 100);

         peer.waitForScriptToComplete();
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create

         server.createQueue(QueueConfiguration.of(getTestName() + ":1").setDurable(true));

         peer.waitForScriptToComplete();

         Wait.assertTrue(() -> snfQueue.getConsumerCount() == 1, 5_000, 100);
      }
   }

   @Test
   @Timeout(20)
   public void testMirrorConnectionCleansUpWhenBrokerConnectionStopped() throws Exception {
      final Map<String, Object> brokerProperties = new HashMap<>();
      brokerProperties.put(AMQPMirrorControllerSource.BROKER_ID.toString(), "Test-Broker");

      final String snfQueueName = "$ACTIVEMQ_ARTEMIS_MIRROR_" + getTestName();

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withName(Matchers.startsWith("$ACTIVEMQ_ARTEMIS_MIRROR"))
                            .withDesiredCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .respond()
                            .withOfferedCapabilities("amq.mirror", AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withPropertiesMap(brokerProperties);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().accept(); // Notification address create
         peer.expectTransfer().accept(); // Address create
         peer.expectTransfer().accept(); // Queue create
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement();
         mirror.setQueueCreation(true);
         mirror.setDurable(true);
         mirror.setName(getTestName());

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(5);// Allow some reconnects
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         amqpConnection.addElement(mirror);
         amqpConnection.setRetryInterval(100);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setDurable(true));

         peer.waitForScriptToComplete();
         peer.expectDetach().optional();
         peer.expectClose().withError(nullValue()).optional();
         peer.expectConnectionToDrop();

         final org.apache.activemq.artemis.core.server.Queue snfQueue = server.locateQueue(snfQueueName);
         assertNotNull(snfQueue);
         Wait.assertTrue(() -> snfQueue.getConsumerCount() == 1, 5_000, 100);

         try {
            server.getActiveMQServerControl().stopBrokerConnection(getTestName());
         } catch (Exception e) {
            fail("Should not have thrown an error stopping the broker connection manually: " + e.getMessage());
         }

         peer.waitForScriptToComplete();

         Wait.assertTrue(() -> snfQueue.getConsumerCount() == 0, 5_000, 100);
      }
   }
}
