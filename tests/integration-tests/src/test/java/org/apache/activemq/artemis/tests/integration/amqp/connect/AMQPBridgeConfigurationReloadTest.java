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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test for reload handling in the broker connection bridge implementation
 */
class AMQPBridgeConfigurationReloadTest extends AmqpClientTestSupport {

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
   public void testBridgeConfigurationWithoutChangesIsIgnoredOnUpdate() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addToExcludes("test.ignore.#");
         receiveFromAddress.setPriority(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            final AMQPBridgeAddressPolicyElement updatedReceiveFromAddress = new AMQPBridgeAddressPolicyElement();
            updatedReceiveFromAddress.setName("address-policy");
            updatedReceiveFromAddress.addToIncludes(getTestName());
            updatedReceiveFromAddress.addToExcludes("test.ignore.#");
            updatedReceiveFromAddress.setPriority(1);

            final AMQPBridgeBrokerConnectionElement updatedElement = new AMQPBridgeBrokerConnectionElement();
            updatedElement.setName(getTestName());
            updatedElement.addBridgeFromAddressPolicy(updatedReceiveFromAddress);
            updatedElement.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

            final AMQPBrokerConnectConfiguration amqpConnectionUpdated =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
            amqpConnectionUpdated.setReconnectAttempts(0);// No reconnects
            amqpConnectionUpdated.addElement(updatedElement);
            amqpConnectionUpdated.parseURI();

            server.getConfiguration().getAMQPConnection().clear();
            server.getConfiguration().addAMQPConnection(amqpConnectionUpdated);

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeConnectsToSecondPeerWhenConfigurationUpdatedWithNewConnection() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addToExcludes("test.ignore.#");
         receiveFromAddress.setPriority(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName() + ":1");
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName() + ":1", "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withName(allOf(containsString(getTestName() + ":1"),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            try (ProtonTestServer peer2 = new ProtonTestServer()) {
               peer2.expectSASLAnonymousConnect();
               peer2.expectOpen().respond();
               peer2.expectBegin().respond();
               peer2.expectAttach().ofReceiver()
                                   .withName(allOf(containsString(getTestName() + ":2"),
                                                   containsString("address-receiver"),
                                                   containsString("amqp-bridge"),
                                                   containsString(server.getNodeID().toString())))
                                   .respond();
               peer2.expectFlow().withLinkCredit(1000);
               peer2.start();

               final URI remoteURI2 = peer2.getServerURI();
               logger.info("Test peer 2 started, peer listening on: {}", remoteURI2);

               final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
                  server.getRemotingService().getProtocolFactoryMap().get("AMQP");
               assertNotNull(protocolFactory);

               final AMQPBridgeAddressPolicyElement updatedReceiveFromAddress = new AMQPBridgeAddressPolicyElement();
               updatedReceiveFromAddress.setName("address-policy");
               updatedReceiveFromAddress.addToIncludes(getTestName());
               updatedReceiveFromAddress.addToExcludes("test.ignore.#");
               updatedReceiveFromAddress.setPriority(1);

               final AMQPBridgeBrokerConnectionElement updatedElement = new AMQPBridgeBrokerConnectionElement();
               updatedElement.setName(getTestName() + ":2");
               updatedElement.addBridgeFromAddressPolicy(updatedReceiveFromAddress);

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
   public void testBridgeDisconnectsFromExistingPeerIfConfigurationRemoved() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addToExcludes("test.ignore.#");
         receiveFromAddress.setPriority(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

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
            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBidgeUpdatesPolicyAndBridgesQueueInsteadOfAddress() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setPriority(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));
            session.createConsumer(session.createQueue("queue"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDetach().optional();
            peer.expectClose().optional();
            peer.expectConnectionToDrop();
            peer.expectSASLAnonymousConnect();
            peer.expectOpen().respond();
            peer.expectBegin().respond();
            peer.expectAttach().ofReceiver()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("queue"),
                                               containsString("queue-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
               server.getRemotingService().getProtocolFactoryMap().get("AMQP");
            assertNotNull(protocolFactory);

            final AMQPBridgeQueuePolicyElement updatedReceiveFromQueue = new AMQPBridgeQueuePolicyElement();
            updatedReceiveFromQueue.setName("queue-policy");
            updatedReceiveFromQueue.addToIncludes("*", "queue");

            final AMQPBridgeBrokerConnectionElement updatedElement = new AMQPBridgeBrokerConnectionElement();
            updatedElement.setName(getTestName());
            updatedElement.addBridgeFromQueuePolicy(updatedReceiveFromQueue);

            final AMQPBrokerConnectConfiguration updatedAmqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
            updatedAmqpConnection.setReconnectAttempts(0);// No reconnects
            updatedAmqpConnection.addElement(updatedElement);

            server.getConfiguration().getAMQPConnection().clear();
            server.getConfiguration().addAMQPConnection(updatedAmqpConnection);

            protocolFactory.updateProtocolServices(server, Collections.emptyList());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }
}
