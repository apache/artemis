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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.integration.jms.RedeployTest;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_ADDRESS_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_QUEUE_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.FEDERATED_ADDRESS_SOURCE_PROPERTIES;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for reload handling in the broker connection federation implementation
 */
public class AMQPFederationConfigurationReloadTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
   @Timeout(20)
   public void testFederationConfigurationWithoutChangesIsIgnoredOnUpdate() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(true);
         receiveFromAddress.setAutoDeleteDelay(10_000L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 2);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, true);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("test"));

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            final AMQPFederationAddressPolicyElement updatedReceiveFromAddress = new AMQPFederationAddressPolicyElement();
            updatedReceiveFromAddress.setName("address-policy");
            updatedReceiveFromAddress.addToIncludes("test");
            updatedReceiveFromAddress.setAutoDelete(true);
            updatedReceiveFromAddress.setAutoDeleteDelay(10_000L);
            updatedReceiveFromAddress.setAutoDeleteMessageCount(-1L);

            final AMQPFederatedBrokerConnectionElement updatedElement = new AMQPFederatedBrokerConnectionElement();
            updatedElement.setName(getTestName());
            updatedElement.addLocalAddressPolicy(updatedReceiveFromAddress);

            amqpConnection.getConnectionElements().clear();
            amqpConnection.addElement(updatedElement); // This should be equivalent to replacing the previous instance.

            server.getConfiguration().getAMQPConnection().clear();
            server.getConfiguration().addAMQPConnection(amqpConnection);

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer.close();

            final BrokerConnectionControl brokerConnection =
               server.getManagementService().getBrokerConnectionControl(getTestName());

            assertNotNull(brokerConnection);
            assertTrue(brokerConnection.isConnected());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationConnectsToSecondPeerWhenConfigurationUpdatedWithNewConnection() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(true);
         receiveFromAddress.setAutoDeleteDelay(10_000L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName() + ":1");
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName() + ":1", "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, true);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName() + ":1"),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            try (ProtonTestServer peer2 = new ProtonTestServer()) {
               peer2.expectSASLAnonymousConnect();
               peer2.expectOpen().respond();
               peer2.expectBegin().respond();
               peer2.expectAttach().ofSender()
                                   .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                                   .respond()
                                   .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
               peer2.expectAttach().ofReceiver()
                                   .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                                   .respondInKind();
               peer2.expectFlow().withLinkCredit(10);
               peer2.expectAttach().ofReceiver()
                                   .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                                   .withName(allOf(containsString(getTestName() + ":2"),
                                                   containsString("test"),
                                                   containsString("address-receiver"),
                                                   containsString(server.getNodeID().toString())))
                                   .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                                   .respond()
                                   .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
               peer2.expectFlow().withLinkCredit(1000);
               peer2.start();

               final URI remoteURI2 = peer2.getServerURI();
               logger.info("Test peer 2 started, peer listening on: {}", remoteURI2);

               final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
                  server.getRemotingService().getProtocolFactoryMap().get("AMQP");
               assertNotNull(protocolFactory);

               final AMQPFederationAddressPolicyElement updatedReceiveFromAddress = new AMQPFederationAddressPolicyElement();
               updatedReceiveFromAddress.setName("address-policy");
               updatedReceiveFromAddress.addToIncludes("test");
               updatedReceiveFromAddress.setAutoDelete(true);
               updatedReceiveFromAddress.setAutoDeleteDelay(10_000L);
               updatedReceiveFromAddress.setAutoDeleteMessageCount(-1L);

               final AMQPFederatedBrokerConnectionElement updatedElement = new AMQPFederatedBrokerConnectionElement();
               updatedElement.setName(getTestName() + ":2");
               updatedElement.addLocalAddressPolicy(updatedReceiveFromAddress);

               final AMQPBrokerConnectConfiguration updatedAmqpConnection =
                  new AMQPBrokerConnectConfiguration(getTestName() + ":2", "tcp://" + remoteURI2.getHost() + ":" + remoteURI2.getPort());
               updatedAmqpConnection.setReconnectAttempts(0);// No reconnects
               updatedAmqpConnection.addElement(updatedElement);

               server.getConfiguration().addAMQPConnection(updatedAmqpConnection);

               protocolFactory.updateProtocolServices(server, Collections.emptyList());

               peer2.waitForScriptToComplete(5, TimeUnit.SECONDS);
               peer2.close();
            }

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationDisconnectsFromExistingPeerIfConfigurationRemoved() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(true);
         receiveFromAddress.setAutoDeleteDelay(10_000L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, true);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(getTestName());

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDetach().optional();
            peer.expectClose().optional();
            peer.expectConnectionToDrop();

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            server.getConfiguration().clearAMQPConnectionConfigurations();

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Create more demand, no federation should be initiated
            session.createConsumer(session.createTopic("test"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            assertNull(server.getManagementService().getBrokerConnectionControl(getTestName()));

            assertNotNull(brokerConnection);

            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationUpdatesPolicyAndFederatesQueueInsteadOfAddress() throws Exception {
      try (ProtonTestServer peer1 = new ProtonTestServer();
           ProtonTestServer peer2 = new ProtonTestServer()) {

         peer1.expectSASLAnonymousConnect();
         peer1.expectOpen().respond();
         peer1.expectBegin().respond();
         peer1.expectAttach().ofSender()
                             .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                             .respond()
                             .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer1.expectAttach().ofReceiver()
                             .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                             .respondInKind();
         peer1.expectFlow().withLinkCredit(10);
         peer1.start();
         peer2.start();

         final URI remoteURI_1 = peer1.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI_1);

         final URI remoteURI_2 = peer2.getServerURI();
         logger.info("Test reconnect, peer two listening on: {}", remoteURI_2);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(true);
         receiveFromAddress.setAutoDeleteDelay(10_000L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI_1.getHost() + ":" + remoteURI_1.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, true);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         peer1.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer1.expectAttach().ofReceiver()
                             .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                             .withName(allOf(containsString(getTestName()),
                                             containsString("test"),
                                             containsString("address-receiver"),
                                             containsString(server.getNodeID().toString())))
                             .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                             .respond()
                             .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer1.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));
            session.createConsumer(session.createQueue("queue"));

            connection.start();

            peer1.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer1.expectDetach().optional();
            peer1.expectClose().optional();
            peer1.expectConnectionToDrop();

            // Reconnect should use new URI and federation policy.
            peer2.expectSASLAnonymousConnect();
            peer2.expectOpen().respond();
            peer2.expectBegin().respond();
            peer2.expectAttach().ofSender()
                                .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                                .respond()
                                .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
            peer2.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                               .respondInKind();
            peer2.expectFlow().withLinkCredit(10);
            peer2.expectAttach().ofReceiver()
                                .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                                .withName(allOf(containsString(getTestName()),
                                                containsString("queue::queue"),
                                                containsString("queue-receiver"),
                                                containsString(server.getNodeID().toString())))
                                .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                                .respond()
                                .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
            peer2.expectFlow().withLinkCredit(1000);

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            final AMQPFederationAddressPolicyElement updatedReceiveFromAddress = new AMQPFederationAddressPolicyElement();
            updatedReceiveFromAddress.setName("address-policy");
            updatedReceiveFromAddress.addToIncludes("test");
            updatedReceiveFromAddress.setAutoDelete(true);
            updatedReceiveFromAddress.setAutoDeleteDelay(10_000L);
            updatedReceiveFromAddress.setAutoDeleteMessageCount(-1L);

            final AMQPFederationQueuePolicyElement updatedReceiveFromQueue = new AMQPFederationQueuePolicyElement();
            updatedReceiveFromQueue.setName("queue-policy");
            updatedReceiveFromQueue.addToIncludes("*", "queue");

            final AMQPFederatedBrokerConnectionElement updatedElement = new AMQPFederatedBrokerConnectionElement();
            updatedElement.setName(getTestName());
            updatedElement.addLocalQueuePolicy(updatedReceiveFromQueue);

            final AMQPBrokerConnectConfiguration updatedAmqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI_2.getHost() + ":" + remoteURI_2.getPort());
            updatedAmqpConnection.setReconnectAttempts(0);// No reconnects
            updatedAmqpConnection.addElement(updatedElement);

            server.getConfiguration().getAMQPConnection().clear();
            server.getConfiguration().addAMQPConnection(updatedAmqpConnection);

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            peer1.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer1.close();
            peer2.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer2.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testReloadAmqpConnectionAddressPolicyMatches() throws Exception {
      server.start();

      final Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      final URL url1 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-addresses.xml");
      final URL url2 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-addresses-reload.xml");

      Files.copy(url1.openStream(), brokerXML);

      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61617");
      final ConnectionFactory serverCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection();
           Connection serverConnection = serverCF.createConnection()) {

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic address1 = session.createTopic("address1");
         final Topic address2 = session.createTopic("address2");
         final MessageConsumer address1Consumer = session.createConsumer(address1);
         final MessageConsumer address2Consumer = session.createConsumer(address2);

         connection.start();

         // Produces on the "remote" server which should federate to the embedded "local" instance
         final Session serverSession = serverConnection.createSession(Session.AUTO_ACKNOWLEDGE);
         final MessageProducer address1Producer = serverSession.createProducer(address1);
         final MessageProducer address2Producer = serverSession.createProducer(address2);

         latch.await(10, TimeUnit.SECONDS);

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("address1")).isExists());

         final TextMessage message = session.createTextMessage("test");

         address1Producer.send(message);
         address2Producer.send(message);

         assertNotNull(address1Consumer.receive(5_000));
         assertNull(address2Consumer.receiveNoWait());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("address2")).isExists());

         // Should arrive on the original federated Address and the now added address
         // but we need to await the federation being setup and a binding added.
         Wait.assertTrue(() -> !server.bindingQuery(SimpleString.of("address2")).getQueueNames().isEmpty());

         address1Producer.send(message);
         address2Producer.send(message);

         assertNotNull(address1Consumer.receive(5_000));
         assertNotNull(address2Consumer.receive(5_000));

      } finally  {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testReloadAmqpConnectionQueuePolicyMatches() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("queue1")
                                                         .setAutoCreated(false));
      server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("queue2")
                                                         .setAutoCreated(false));

      final Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      final URL url1 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-queues.xml");
      final URL url2 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-queues-reload.xml");

      Files.copy(url1.openStream(), brokerXML);

      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61617");
      final ConnectionFactory serverCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      latch.await(10, TimeUnit.SECONDS);

      try (Connection connection = factory.createConnection();
           Connection serverConnection = serverCF.createConnection()) {

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Queue queue1 = session.createQueue("queue1");
         final Queue queue2 = session.createQueue("queue2");
         final MessageConsumer queue1Consumer = session.createConsumer(queue1);
         final MessageConsumer queue2Consumer = session.createConsumer(queue2);

         connection.start();

         // Produces on the "remote" server which should federate to the embedded "local" instance
         final Session serverSession = serverConnection.createSession(Session.AUTO_ACKNOWLEDGE);
         final MessageProducer queue1Producer = serverSession.createProducer(queue1);
         final MessageProducer queue2Producer = serverSession.createProducer(queue2);

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists());

         final TextMessage message = session.createTextMessage("test");

         queue1Producer.send(message);
         queue2Producer.send(message);

         // Should arrive on the original federated Queue but not the updated Queue as it is not
         // currently federated.

         Wait.assertTrue(() ->
            embeddedActiveMQ.getActiveMQServer().queueQuery(SimpleString.of("queue1")).getMessageCount() == 1);

         assertNotNull(queue1Consumer.receiveNoWait());
         assertNull(queue2Consumer.receiveNoWait());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists());

         // Should arrive on the original federated Queue and the updated Queue as it is now federated

         queue1Producer.send(message);

         Wait.assertTrue(() ->
            embeddedActiveMQ.getActiveMQServer().queueQuery(SimpleString.of("queue1")).getMessageCount() == 1);
         Wait.assertTrue(() ->
            embeddedActiveMQ.getActiveMQServer().queueQuery(SimpleString.of("queue2")).getMessageCount() == 1);

         assertNotNull(queue1Consumer.receiveNoWait());
         assertNotNull(queue2Consumer.receiveNoWait());

      } finally  {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testReloadAmqpConnectionAddressPolicyReplacedWithQueuePolicy() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("queue1")
                                                         .setAutoCreated(false));

      final Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      final URL url1 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-addresses.xml");
      final URL url2 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-queues.xml");

      Files.copy(url1.openStream(), brokerXML);

      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61617");
      final ConnectionFactory serverCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection();
           Connection serverConnection = serverCF.createConnection()) {

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic address = session.createTopic("address1");
         final Queue queue = session.createQueue("queue1");
         final MessageConsumer addressConsumer = session.createConsumer(address);
         final MessageConsumer queueConsumer = session.createConsumer(queue);

         connection.start();

         // Produces on the "remote" server which should federate to the embedded "local" instance
         final Session serverSession = serverConnection.createSession(Session.AUTO_ACKNOWLEDGE);
         final MessageProducer addressProducer = serverSession.createProducer(address);
         final MessageProducer queueProducer = serverSession.createProducer(queue);

         latch.await(10, TimeUnit.SECONDS);

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("address1")).isExists());
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists());

         final TextMessage message = session.createTextMessage("test");

         addressProducer.send(message);
         queueProducer.send(message);

         assertNotNull(addressConsumer.receive(5_000));
         assertNull(queueConsumer.receiveNoWait());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         // The previously sent message should be federated to the embedded broker
         assertNotNull(queueConsumer.receive(5_000));

         // The original address consumer should have gone away and not returned when the federation
         // connection was recreated.
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("address2")).getQueueNames().isEmpty());

      } finally  {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testReloadAmqpConnectionQueuePolicyMatchesFromBrokerProperties() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("queue1")
                                                         .setAutoCreated(false));
      server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.ANYCAST)
                                                         .setAddress("queue2")
                                                         .setAutoCreated(false));

      final Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      final Path brokerProperties = getTestDirfile().toPath().resolve("broker.properties");

      final URL url1 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-basic.xml");

      final URL propertiesUrl1 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-queues.properties");
      final URL propertiesUrl2 = RedeployTest.class.getClassLoader().getResource("reload-amqp-federated-queues-reload.properties");

      Files.copy(url1.openStream(), brokerXML);
      Files.copy(propertiesUrl1.openStream(), brokerProperties);

      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.setPropertiesResourcePath(brokerProperties.toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61617");
      final ConnectionFactory serverCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      latch.await(10, TimeUnit.SECONDS);

      try (Connection connection = factory.createConnection();
           Connection serverConnection = serverCF.createConnection()) {

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Queue queue1 = session.createQueue("queue1");
         final Queue queue2 = session.createQueue("queue2");
         final MessageConsumer queue1Consumer = session.createConsumer(queue1);
         final MessageConsumer queue2Consumer = session.createConsumer(queue2);

         connection.start();

         // Produces on the "remote" server which should federate to the embedded "local" instance
         final Session serverSession = serverConnection.createSession(Session.AUTO_ACKNOWLEDGE);
         final MessageProducer queue1Producer = serverSession.createProducer(queue1);
         final MessageProducer queue2Producer = serverSession.createProducer(queue2);

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists());
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists());

         final TextMessage message = session.createTextMessage("test");

         queue1Producer.send(message);
         queue2Producer.send(message);

         // Should get message sent to the single federated queue
         Wait.assertTrue(() ->
            embeddedActiveMQ.getActiveMQServer().queueQuery(SimpleString.of("queue1")).getMessageCount() == 1);

         assertNotNull(queue1Consumer.receiveNoWait());
         assertNull(queue2Consumer.receiveNoWait());

         Files.copy(propertiesUrl2.openStream(), brokerProperties, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists());
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists());

         // Send another message to the originally federated queue
         queue1Producer.send(message);

         // Should arrive on the federated Queues now that the broker configuration has been reloaded.
         Wait.assertTrue(() ->
            embeddedActiveMQ.getActiveMQServer().queueQuery(SimpleString.of("queue1")).getMessageCount() == 1);
         Wait.assertTrue(() ->
            embeddedActiveMQ.getActiveMQServer().queueQuery(SimpleString.of("queue2")).getMessageCount() == 1);

         assertNotNull(queue1Consumer.receiveNoWait());
         assertNotNull(queue2Consumer.receiveNoWait());

      } finally  {
         embeddedActiveMQ.stop();
      }
   }
}
