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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS_LOW;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.SHARED_SUBS;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.TUNNEL_CORE_MESSAGES;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.BRIDGE_RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_DEMAND_TRACKING;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_INITIAL_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.MAX_LINK_RECOVERY_ATTEMPTS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PREFER_SHARED_DURABLE_SUBSCRIPTIONS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PRESETTLE_SEND_MODE;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_LINK_QUIESCE_TIMEOUT;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.apache.qpid.protonj2.test.driver.codec.messaging.Modified;
import org.apache.qpid.protonj2.test.driver.codec.messaging.TerminusDurability;
import org.apache.qpid.protonj2.test.driver.codec.messaging.TerminusExpiryPolicy;
import org.apache.qpid.protonj2.test.driver.codec.transport.Attach;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests the AMQP bridge from address behavior.
 */
class AMQPBridgeFromAddressTest extends AmqpClientTestSupport {

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
   public void testLinkAttachTimeoutAppliedAndConnectionClosed() throws Exception {
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

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(LINK_ATTACH_TIMEOUT, 1); // Seconds
         element.addProperty(LINK_RECOVERY_INITIAL_DELAY, 20); // Milliseconds

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Modified deliveryFailed = new Modified();
         deliveryFailed.setDeliveryFailed(true);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.NONE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH)
                                         .withDefaultOutcome(deliveryFailed).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())));
         peer.expectDetach();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesAddressReceiverWhenLocalQueueIsStaticlyDefined() throws Exception {
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

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Modified deliveryFailed = new Modified();
         deliveryFailed.setDeliveryFailed(true);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDefaultOutcome(deliveryFailed).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already bridged the address and the statically added
         // queue should retain demand when this consumer leaves.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            session.createConsumer(session.createTopic(getTestName()));
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This should trigger the bridged consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from bridged address to eliminate demand");
         server.destroyQueue(SimpleString.of(getTestName()));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesAddressReceiverLinkForConsumerDemandCreatedQueue() throws Exception {
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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesAddressReceiverLinkForAddressMatchUsingPolicyCredit() throws Exception {
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
         receiveFromAddress.addProperty(RECEIVER_CREDITS, "25");
         receiveFromAddress.addProperty(RECEIVER_CREDITS_LOW, "5");

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(25);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeClosesAddressReceiverLinkWhenDemandRemovedNoIdleTimeout() throws Exception {
      doTestBridgeClosesAddressReceiverLinkWhenDemandRemoved(0);
   }

   @Test
   @Timeout(20)
   public void testBridgeClosesAddressReceiverLinkWhenDemandRemovedWithIdleTimeout() throws Exception {
      doTestBridgeClosesAddressReceiverLinkWhenDemandRemoved(5);
   }

   private void doTestBridgeClosesAddressReceiverLinkWhenDemandRemoved(int idleTimeout) throws Exception {
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

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, idleTimeout);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            // Demand is removed so receiver should be detached.
            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRetainsAddressReceiverLinkWhenDurableSubscriberIsOffline() throws Exception {
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
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 100); // Should be overridden

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            connection.setClientID("test-clientId");

            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic(getTestName());
            final MessageConsumer consumer = session.createSharedDurableConsumer(topic, "shared-subscription");

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Consumer goes offline but demand is retained for address
            consumer.close();

            // Should provoke no action on the bridge
            session.createSharedDurableConsumer(topic, "shared-subscription").close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            session.unsubscribe("shared-subscription");

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeClosesAddressReceiverLinkWaitsForAllDemandToRemoved() throws Exception {
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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer1 = session.createConsumer(session.createTopic(getTestName()));
            final MessageConsumer consumer2 = session.createConsumer(session.createTopic(getTestName()));

            connection.start();
            consumer1.close(); // One is gone but another remains

            // Will fail if any frames arrive
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer2.close(); // Now demand is gone

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeHandlesAddressDeletedAndConsumerRecreates() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true).optional();
            peer.expectDetach().respond();

            server.removeAddressInfo(SimpleString.of(getTestName()), null, true);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         // Consumer recreates Address and adds demand back and bridge should restart
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000).optional();

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeConsumerCreatedWhenDemandAddedToDivertAddress() throws Exception {
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
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("forward"));

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
   public void testBridgeConsumerCreatedWhenDemandAddedToCompositeDivertAddress() throws Exception {
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
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward1,forward2")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            // Creating a consumer on each should result in only one attach for the source address
            final MessageConsumer consumer1 = session.createConsumer(session.createTopic("forward1"));
            final MessageConsumer consumer2 = session.createConsumer(session.createTopic("forward2"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Closing one should not remove all demand on the source address
            consumer1.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer2.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeConsumerRemovesDemandFromDivertConsumersOnlyWhenAllDemandIsRemoved() throws Exception {
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
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer1 = session.createConsumer(session.createTopic("forward"));
            final MessageConsumer consumer2 = session.createConsumer(session.createTopic("forward"));

            connection.start();
            consumer1.close(); // One is gone but another remains

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer2.close(); // Now demand is gone

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeConsumerRetainsDemandForDivertBindingWithoutActiveAnycastSubscriptions() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName()); // Divert matching works on the source address of the divert
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         // Any demand on the forwarding address even if the forward is a Queue (ANYCAST) should create
         // demand on the remote for the source address (If the source is MULTICAST)
         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         // Current implementation requires the source address exist on the local broker before it
         // will attempt to bridge it from the remote.
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("forward");
            final MessageConsumer consumer1 = session.createConsumer(queue);
            final MessageConsumer consumer2 = session.createConsumer(queue);

            connection.start();
            consumer1.close(); // One is gone but another remains

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            consumer2.close(); // Demand remains as the Queue continues to exist

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeConsumerRemovesDemandForDivertBindingWithoutActiveMulticastSubscriptions() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName()); // Divert matching works on the source address of the divert
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         // Any demand on the forwarding address even if the forward is a Queue (ANYCAST) should create
         // demand on the remote for the source address (If the source is MULTICAST)
         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setRoutingType(ComponentConfigurationRoutingType.MULTICAST)
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         // Current implementation requires the source address exist on the local broker before it
         // will attempt to bridge it from the remote.
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic("forward");
            final MessageConsumer consumer1 = session.createConsumer(topic);
            final MessageConsumer consumer2 = session.createConsumer(topic);

            connection.start();
            consumer1.close(); // One is gone but another remains

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer2.close(); // Now demand is gone from the divert

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesRemoteDemandIfDivertIsRemoved() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName()); // Divert matching works on the source address of the divert
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarding address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("forward"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            server.destroyDivert(SimpleString.of("test-divert"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testDivertBindingsDoNotCreateAdditionalDemandIfDemandOnForwardingAddressAlreadyExists() throws Exception {
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
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the main address creates demand on the same address remotely and then the diverts
         // should just be tracked under that original demand.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final MessageConsumer consumer1 = session.createConsumer(session.createTopic("forward"));
            final MessageConsumer consumer2 = session.createConsumer(session.createTopic("forward"));

            consumer1.close();
            consumer2.close();

            server.destroyDivert(SimpleString.of("test-divert"));

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
   public void testInboundMessageRoutedToReceiverOnLocalAddress() throws Exception {
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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(0)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertTrue(message instanceof TextMessage);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testTransformInboundBridgedMessageBeforeDispatch() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final Map<String, String> newApplicationProperties = new HashMap<>();
         newApplicationProperties.put("appProperty1", "one");
         newApplicationProperties.put("appProperty2", "two");

         final TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
         transformerConfiguration.setClassName(ApplicationPropertiesTransformer.class.getName());
         transformerConfiguration.setProperties(newApplicationProperties);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setTransformerConfiguration(transformerConfiguration);

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(0)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertTrue(message instanceof TextMessage);
            assertEquals("test-message", ((TextMessage) message).getText());
            assertEquals("one", message.getStringProperty("appProperty1"));
            assertEquals("two", message.getStringProperty("appProperty2"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeDoesNotCreateAddressReceiverLinkForAddressMatchWhenLinkCreditIsSetToZero() throws Exception {
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

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(
               getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort() + "?amqpCredits=0");
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            assertNull(consumer.receiveNoWait());
            consumer.close();

            // Should be no interactions with the peer as credit is zero and address policy
            // will not apply to any match when credit cannot be offered to avoid stranding
            // a receiver on a remote address with no credit.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeStartedTriggersRemoteDemandWithExistingAddressBindings() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress(getTestName())
                                                          .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand on the addresses so that on start bridge should happen
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

         session.createConsumer(session.createTopic(getTestName()));
         session.createConsumer(session.createTopic(getTestName()));

         // Add other non-bridged address bindings for the policy to check on start.
         session.createConsumer(session.createTopic("a1"));
         session.createConsumer(session.createTopic("a2"));

         connection.start();

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger bridge of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add more demand while bridge is starting
         session.createConsumer(session.createTopic(getTestName()));
         session.createConsumer(session.createTopic(getTestName()));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // This removes the connection demand, but leaves behind the static queue
         connection.close();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This should trigger the bridge consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from bridged address to eliminate demand");
         server.destroyQueue(SimpleString.of(getTestName()));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeStartedTriggersRemoteDemandWithExistingAddressAndDivertBindings() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         final DivertConfiguration divert = new DivertConfiguration();
         divert.setName("test-divert");
         divert.setAddress(getTestName());
         divert.setExclusive(false);
         divert.setForwardingAddress("target");
         divert.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         // Configure addresses and divert for the test
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target"), RoutingType.MULTICAST));
         server.deployDivert(divert);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic test = session.createTopic(getTestName());
         final Topic target = session.createTopic("target");

         session.createConsumer(test);
         session.createConsumer(test);

         session.createConsumer(target);
         session.createConsumer(target);

         // Add other non-bridge address bindings for the policy to check on start.
         session.createConsumer(session.createTopic("a1"));
         session.createConsumer(session.createTopic("a2"));

         connection.start();

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger bridging of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add more demand while bridge is starting
         session.createConsumer(test);
         session.createConsumer(target);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This removes the connection demand, but leaves behind the static queue
         connection.close();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeStartTriggersBridgingWithMultipleDivertsAndRemainsActiveAfterOneRemoved() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setIncludeDivertBindings(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         final DivertConfiguration divert1 = new DivertConfiguration();
         divert1.setName("test-divert-1");
         divert1.setAddress(getTestName());
         divert1.setExclusive(false);
         divert1.setForwardingAddress("target1,target2");
         divert1.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         final DivertConfiguration divert2 = new DivertConfiguration();
         divert2.setName("test-divert-2");
         divert2.setAddress(getTestName());
         divert2.setExclusive(false);
         divert2.setForwardingAddress("target1,target3");
         divert2.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         // Configure addresses and divert for the test
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target1"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target2"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target3"), RoutingType.MULTICAST));
         server.deployDivert(divert1);
         server.deployDivert(divert2);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic target1 = session.createTopic("target1");
         final Topic target2 = session.createTopic("target2");
         final Topic target3 = session.createTopic("target2");

         session.createConsumer(target1);
         session.createConsumer(target2);
         session.createConsumer(target3);

         connection.start();

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger bridging of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add more demand while bridging is starting
         session.createConsumer(target1);
         session.createConsumer(target2);
         session.createConsumer(target3);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.destroyDivert(SimpleString.of(divert1.getName()));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         server.destroyDivert(SimpleString.of(divert2.getName()));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         connection.close();

         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressDemandTrackedWhenRemoteRejectsInitialAttempts() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

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

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic(getTestName());

            connection.start();

            // First consumer we reject the bridge attempt
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .respondInKind()
                               .withNullSource();
            peer.expectFlow().withLinkCredit(1000);
            peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested address was not found").queue().afterDelay(10);
            peer.expectDetach();

            final MessageConsumer consumer1 = session.createConsumer(topic);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Second consumer we reject the bridge attempt
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .respondInKind()
                               .withNullSource();
            peer.expectFlow().withLinkCredit(1000);
            peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested address was not found").queue().afterDelay(10);
            peer.expectDetach();

            final MessageConsumer consumer2 = session.createConsumer(topic);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Third consumer we accept the bridging attempt
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000);

            final MessageConsumer consumer3 = session.createConsumer(topic);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Demand should remain
            consumer3.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Demand should remain
            consumer2.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            // Demand should be gone now
            consumer1.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testAddressPolicyCanOverridesZeroCreditsInBridgeConfigurationAndBridgeAddress() throws Exception {
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
         receiveFromAddress.addProperty(RECEIVER_CREDITS, 10);
         receiveFromAddress.addProperty(RECEIVER_CREDITS_LOW, 3);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(RECEIVER_CREDITS, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverCarriesConfiguredPolicyFilter() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setFilter("color='red'");

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withJMSSelector("color='red'").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverCarriesConfiguredPrioirty() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setPriority(10);

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withProperty(BRIDGE_RECEIVER_PRIORITY.toString(), 10)
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverOmitsConfiguredPrioirtyIfPriorityDisabled() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setPriority(10); // Should be ignored regardless
         receiveFromAddress.addProperty(DISABLE_RECEIVER_PRIORITY, "true");

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withProperties(not(Matchers.hasEntry(BRIDGE_RECEIVER_PRIORITY.toString(), 10)))
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverAppliesConfiguredRemoteAddressPrefix() throws Exception {
      doTestBridgeReceiverAppliesConfiguredRemoteAddressCustomizations("queue://", null, null);
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverAppliesConfiguredRemoteAddressSuffix() throws Exception {
      doTestBridgeReceiverAppliesConfiguredRemoteAddressCustomizations(null, null, "?consumer-priority=1");
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverAppliesConfiguredRemoteAddress() throws Exception {
      doTestBridgeReceiverAppliesConfiguredRemoteAddressCustomizations(null, "alternate", null);
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverAppliesConfiguredRemoteAddressCustomizations() throws Exception {
      doTestBridgeReceiverAppliesConfiguredRemoteAddressCustomizations("queue://", "alternate", "?consumer-priority=1");
   }

   private void doTestBridgeReceiverAppliesConfiguredRemoteAddressCustomizations(String prefix, String address, String suffix) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setRemoteAddress(address);
         receiveFromAddress.setRemoteAddressPrefix(prefix);
         receiveFromAddress.setRemoteAddressSuffix(suffix);

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

         final String expectedSourceAddress = Objects.requireNonNullElse(prefix, "") +
                                              Objects.requireNonNullElse(address, getTestName()) +
                                              Objects.requireNonNullElse(suffix, "");

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(expectedSourceAddress).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverAddsRemoteTerminusCapabilitiesConfigured() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setRemoteTerminusCapabilities(new String[] {"queue", "another"});

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withCapabilities("queue", "another")
                                         .also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressReceiverSetsSenderPresettledWhenConfigured() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(PRESETTLE_SEND_MODE, "true");

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
                            .withSenderSettleModeSettled()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeStartedTriggersRemoteDemandWithExistingAddressesWithoutBindingsWhenTrackingDisabled() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(DISABLE_RECEIVER_DEMAND_TRACKING, "true");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));
         // Other non-matching addresses that also get scanned on start
         server.addAddressInfo(new AddressInfo(SimpleString.of("other"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("another"), RoutingType.MULTICAST));

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger bridge of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridge consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from bridged address to eliminate demand");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeTriggersRemoteDemandAfterAddressCreatedWhenTrackingDisabled() throws Exception {
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
         receiveFromAddress.addProperty(DISABLE_RECEIVER_DEMAND_TRACKING, "true");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         // Other non-matching addresses that also get scanned on start
         server.addAddressInfo(new AddressInfo(SimpleString.of("other"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("another"), RoutingType.MULTICAST));

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridge consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from bridged address to eliminate demand");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRetriesReceiverAttachOnNewDemandAfterFirstReceiverForceDetached() throws Exception {
      doTestBridgeRetriesReceiverAttachOnNewDemandAfterFirstReceiverDetached(LinkError.DETACH_FORCED);
   }

   @Test
   @Timeout(20)
   public void testBridgeRetriesReceiverAttachOnNewDemandAfterFirstReceiverResourceDeleted() throws Exception {
      doTestBridgeRetriesReceiverAttachOnNewDemandAfterFirstReceiverDetached(AmqpError.RESOURCE_DELETED);
   }

   private void doTestBridgeRetriesReceiverAttachOnNewDemandAfterFirstReceiverDetached(Symbol condition) throws Exception {
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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
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
            peer.expectDetach();
            peer.remoteDetach().withErrorCondition(condition.toString(), "Forced Detach").now();
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Retry after another consumer added.
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createTopic(getTestName())); // New demand.

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRetriesReceiverAttachOnNewDemandAfterFirstReceiverResourceNotFound() throws Exception {
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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullSource();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Retry after another consumer added.
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createTopic(getTestName())); // New demand.

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRecoversLinkAfterFirstReceiverFailsWithResourceNotFound() throws Exception {
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
         receiveFromAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 10); // 10 millisecond initial recovery delay
         receiveFromAddress.addProperty(LINK_RECOVERY_DELAY, 10);         // 10 millisecond continued recovery delay

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullSource();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Retry after delay.
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAttemptsLimitedRecoveryLinkAfterFirstReceiverFailsWithResourceNotFound() throws Exception {
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
         receiveFromAddress.addToIncludes("another");
         receiveFromAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 1); // 1 millisecond initial recovery delay
         receiveFromAddress.addProperty(LINK_RECOVERY_DELAY, 10);        // 10 millisecond continued recovery delay
         receiveFromAddress.addProperty(MAX_LINK_RECOVERY_ATTEMPTS, 2);  // 3 attempts then stop trying

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullSource();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Attempt #1
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .respond()
                               .withNullSource();
            peer.remoteDetach().withClosed(true)
                               .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
            peer.expectFlow().optional();
            peer.expectDetach();

            // Attempt #2
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .respond()
                               .withNullSource();
            peer.remoteDetach().withClosed(true)
                               .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
            peer.expectFlow().optional();
            peer.expectDetach();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Retry after new consumer added.
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress("another").also()
                               .withSource().withAddress("another").also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("another"),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            // Now add demand on the alternate which should trigger an attach
            session.createConsumer(session.createTopic("another"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Retry after new consumer added.
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            // Now add new demand and trigger another attempt to create the bridge receiver
            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeDoesNotAttemptRecoveryOfLinkAfterFirstReceiverFailsWithResourceNotFound() throws Exception {
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
         receiveFromAddress.addToIncludes("another");
         receiveFromAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 1);  // 1 millisecond initial recovery delay
         receiveFromAddress.addProperty(LINK_RECOVERY_DELAY, 10);         // 10 millisecond continued recovery delay
         receiveFromAddress.addProperty(MAX_LINK_RECOVERY_ATTEMPTS, 0);   // No attempts

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullSource();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress("another").also()
                               .withSource().withAddress("another").also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("another"),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            // Now add demand to the alternate and it should trigger new receiver attach
            session.createConsumer(session.createTopic("another"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
            peer.expectFlow().withLinkCredit(1000);

            // Now add demand to previous and it should retry the receiver attach
            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testMultipleBridgeConfigurationElementsHandledAndCreatesReceiversForDemand() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         // Bridge element one
         final AMQPBridgeAddressPolicyElement receiveFromAddress1 = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress1.setName("address-policy-1");
         receiveFromAddress1.addToIncludes("testA");
         final AMQPBridgeBrokerConnectionElement element1 = new AMQPBridgeBrokerConnectionElement();
         element1.setName(getTestName());
         element1.addBridgeFromAddressPolicy(receiveFromAddress1);
         element1.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         // Bridge element two
         final AMQPBridgeAddressPolicyElement receiveFromAddress2 = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress2.setName("address-policy-2");
         receiveFromAddress2.addToIncludes("testB");
         final AMQPBridgeBrokerConnectionElement element2 = new AMQPBridgeBrokerConnectionElement();
         element2.setName(getTestName());
         element2.addBridgeFromAddressPolicy(receiveFromAddress2);
         element2.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element1);
         amqpConnection.addElement(element2);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Modified deliveryFailed = new Modified();
         deliveryFailed.setDeliveryFailed(true);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress("testA").also()
                            .withSource().withAddress("testA")
                                         .withDefaultOutcome(deliveryFailed).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("testA"),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("testA").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("testA")
                                                          .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("testA")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress("testB").also()
                            .withSource().withAddress("testB")
                                         .withDefaultOutcome(deliveryFailed).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("testB"),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("testB").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("testB")
                                                          .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("testB")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This should trigger the bridged consumer to be shutdown as the statically defined queue
         // should be the only demand on the address.
         logger.info("Removing Queues from bridged address 'testA' to eliminate demand");
         server.destroyQueue(SimpleString.of("testA"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("testA")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This should trigger the bridged consumer to be shutdown as the statically defined queue
         // should be the only demand on the address.
         logger.info("Removing Queues from bridged address 'testB' to eliminate demand");
         server.destroyQueue(SimpleString.of("testB"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("testB")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testNewBridgeConsumerCreatedWhenDemandRemovedAndAddedWithDelayedPreviousDetach() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress1 = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress1.setName("address-policy-1");
         receiveFromAddress1.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress1);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                           .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand on the address which creates a bridge consumer then let it close which
         // should shut down that bridge consumer.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond().afterDelay(40); // Defer the detach response for a bit

            consumer.receiveNoWait();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                           .respond();
         peer.expectFlow().withLinkCredit(1000);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // Create demand on the address which creates a bridge consumer again quickly which
         // can trigger a new consumer before the previous one was fully closed with a Detach
         // response and get stuck because it will steal the link in proton and not be treated
         // as a new attach for this consumer.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            consumer.receiveNoWait();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeLinksDetachesAfterLinkQuiesceTimeoutNoIldeTimeout() throws Exception {
      doTestBridgeLinksDetachesAfterLinkQuiesceTimeout(0);
   }

   @Test
   @Timeout(20)
   public void testBridgeLinksDetachesAfterLinkQuiesceTimeoutAndIdleTimeout() throws Exception {
      doTestBridgeLinksDetachesAfterLinkQuiesceTimeout(10);
   }

   public void doTestBridgeLinksDetachesAfterLinkQuiesceTimeout(int idleTimeout) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 20);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, idleTimeout);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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
            peer.expectFlow().withLinkCredit(1000).withDrain(true);  // Don't answer drained then wait for the
            peer.expectDetach().respond();                           // timeout to see the link is detached.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                           .respond();
         peer.expectFlow().withLinkCredit(1000);

         // New demand should create a new consumer after the last drain timed out and closed the link
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(2000).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.expectFlow().withLinkCredit(1000);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemand() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 300);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10);

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Demand is removed so expect a drain, don't respond then add new consumer to add
            // demand that must wait on drain to timeout and recover.
            peer.expectFlow().withLinkCredit(1000).withDrain(true);

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                              .respond();
            peer.expectDetach().respond();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Demand goes away and the bridge link is closed.
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(2000).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeLinksRecoveredAfterLinkQuiescedButNotIdledOut() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10_000);

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Demand is removed so expect a drain, respond to drain to quiesce the link
            // which should leave it idling and ready for recovery by next consumer.
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Link should be restarted by receiving a new batch of credit
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeLinkIdleTimeoutAtPolicyLevelOverridesTopLevelConfiguration() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 250);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 90_000);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Demand is removed so expect a drain, respond to drain to quiesce the link
            // which should leave it idling and ready for recovery by next consumer.
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDetach().respond();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeResourceDeletedBeforeLinkQuiesceCompletes() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

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
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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
            peer.expectFlow().withLinkCredit(1000).withDrain(true); // No answer to allow for race of answer plus delete
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // Now answer the drain request and then immediately remove the resource.
         peer.remoteFlow().withLinkCredit(0).withDeliveryCount(1000).withDrain(true).now();

         server.removeAddressInfo(SimpleString.of(getTestName()), null);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testTunnledCoreMessageOnSenderThatDidNotDesireThatClosesConnection() throws Exception {
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
                            .withOfferedCapability(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind(); // Offered capabilities are not reflected as desired here.
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withMessageFormat(AMQP_TUNNELED_CORE_MESSAGE_FORMAT)
                              .withBody().withString("test-message")
                              .also()
                              .withDeliveryId(0)
                              .queue();
         peer.expectClose().withError(AmqpError.INTERNAL_ERROR.toString()).respond();
         peer.expectConnectionToDrop();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testDisableOfCoreTunnelingRemovesOfferedCapability() throws Exception {
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
         receiveFromAddress.addProperty(TUNNEL_CORE_MESSAGES, Boolean.FALSE.toString());
         receiveFromAddress.addProperty(DISABLE_RECEIVER_DEMAND_TRACKING, Boolean.TRUE.toString());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(TUNNEL_CORE_MESSAGES, Boolean.TRUE.toString());

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.expectAttach().ofReceiver()
                            .withOfferedCapabilities(nullValue())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind(); // Offered capabilities are not reflected as desired here.
         peer.expectFlow().withLinkCredit(1000);

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreateJMSStyleDurableSubscriptionWhenConfiguredTo() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.setUseDurableSubscriptions(true);
         receiveFromAddress.addToIncludes(getTestName());

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

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Modified deliveryFailed = new Modified();
         deliveryFailed.setDeliveryFailed(true);

         final AtomicReference<Attach> capturedAttach1 = new AtomicReference<>();

         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach1.set(attach))
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.UNSETTLED_STATE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.NEVER)
                                         .withDistributionMode(AmqpSupport.COPY.toString())
                                         .withDefaultOutcome(deliveryFailed).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().withClosed(true).respond(); // No demand removes subscription (closed = true)

         // Subscription is the link name which should omit any sequence numbering
         final String subscriptionName = capturedAttach1.get().getName();

         assertNotNull(subscriptionName);
         assertTrue(subscriptionName.endsWith(server.getNodeID().toString()));

         logger.info("Removing Queues from bridged address to eliminate demand");
         server.destroyQueue(SimpleString.of(getTestName()));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final AtomicReference<Attach> capturedAttach2 = new AtomicReference<>();

         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach2.set(attach))
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.UNSETTLED_STATE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.NEVER)
                                         .withDistributionMode(AmqpSupport.COPY.toString())
                                         .withDefaultOutcome(deliveryFailed).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // the subscription name (link name) should remain a stable value
         assertEquals(capturedAttach1.get().getName(), capturedAttach2.get().getName());

         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testNewSharedDurableBridgeReceiverCreatedWhenDemandRemovedAndAddedWithDelayedPreviousDetach() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond().withOfferedCapabilities(SHARED_SUBS.toString());
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.setUseDurableSubscriptions(true);
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         element.addProperty(PREFER_SHARED_DURABLE_SUBSCRIPTIONS, "true");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final AtomicReference<Attach> capturedAttach1 = new AtomicReference<>();
         final AtomicReference<Attach> capturedAttach2 = new AtomicReference<>();

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach1.set(attach))
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.UNSETTLED_STATE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.NEVER)
                                         .withDistributionMode(AmqpSupport.COPY.toString())
                                         .withCapabilities(SHARED.toString())
                                         .also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand on the address which creates a bridge receiver then let it close which
         // should shut down that bridge receiver. We removed the idle timeout wait so that we
         // send a detach almost immediately and then add demand again before the remote has likely
         // sent its detach response.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            consumer.receiveNoWait();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond().afterDelay(50); // Defer the detach response for a bit
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach2.set(attach))
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.UNSETTLED_STATE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.NEVER)
                                         .withDistributionMode(AmqpSupport.COPY.toString())
                                         .withCapabilities(SHARED.toString())
                                         .also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         // Create demand on the address which creates a bridge receiver again quickly which
         // can trigger a new receiver before the previous one was fully closed with a Detach
         // response and get stuck because it will steal the link in proton and not be treated
         // as a new attach for this consumer.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            consumer.receiveNoWait();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         // Shared subs should be used and the sequence number is in the link name means they are not equal
         assertNotEquals(capturedAttach1.get().getName(), capturedAttach2.get().getName());
      }
   }

   @Test
   @Timeout(20)
   public void testDurableFallbackToLegacyTypeIfRemoteConnectionDoesNotOfferSharedSubs() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement receiveFromAddress = new AMQPBridgeAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.setUseDurableSubscriptions(true);
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10);
         element.addProperty(PREFER_SHARED_DURABLE_SUBSCRIPTIONS, "true");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final AtomicReference<Attach> capturedAttach1 = new AtomicReference<>();
         final AtomicReference<Attach> capturedAttach2 = new AtomicReference<>();

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach1.set(attach))
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.UNSETTLED_STATE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.NEVER)
                                         .withDistributionMode(AmqpSupport.COPY.toString())
                                         .also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand which should trigger a durable subscription
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            consumer.receiveNoWait();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();
         }

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists(), 5_000, 75);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach2.set(attach))
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName())
                                         .withDurable(TerminusDurability.UNSETTLED_STATE)
                                         .withExpiryPolicy(TerminusExpiryPolicy.NEVER)
                                         .withDistributionMode(AmqpSupport.COPY.toString())
                                         .also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         // Add back demand which should trigger another durable subscription with the same link name
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            consumer.receiveNoWait();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         // Non-shared subs should be used so no sequence number in the link name means they will be the same
         assertEquals(capturedAttach1.get().getName(), capturedAttach2.get().getName());
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeReceiverRejectsWithModifiedDeliveryFailedAsDefault() throws Exception {
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

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(getTestName());
         addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
         addressSettings.setMaxSizeBytes(500);
         server.getAddressSettingsRepository().addMatch(getTestName(), addressSettings);
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final String payload = "A".repeat(2048);

         peer.expectAttach().ofReceiver()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false)
                                                                .setFilterString("color='red'"));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectDisposition().withState().accepted();       // This should fill the address
         peer.expectFlow().withLinkCredit(998).withDrain(true); // Receiver drains credit before sending the disposition
         peer.expectDisposition().withState().modified(true);   // Expect modified / failed so remote doesn't drop the message

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();
         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   public void testDrainReceiverOnTransientErrorsConfiguredAtBridgeLevel() throws Exception {
      doTestDrainReceiverOnTransientErrorsConfiguredAtBridgeLevel(true);
   }

   @Test
   public void testNoDrainReceiverOnTransientErrorsConfiguredAtBridgeLevel() throws Exception {
      doTestDrainReceiverOnTransientErrorsConfiguredAtBridgeLevel(false);
   }

   private void doTestDrainReceiverOnTransientErrorsConfiguredAtBridgeLevel(boolean drainOnFull) throws Exception {
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

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10);
         element.addProperty(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, String.valueOf(drainOnFull));
         element.addProperty(RECEIVER_LINK_QUIESCE_TIMEOUT, 350);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(getTestName());
         addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
         addressSettings.setMaxSizeBytes(1000);
         server.getAddressSettingsRepository().addMatch(getTestName(), addressSettings);
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final String payload = "A".repeat(2048);

         peer.expectAttach().ofReceiver()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().accepted(); // This should fill the address

         if (drainOnFull) {
            peer.expectFlow().withDrain(true).withLinkCredit(998);
            peer.expectDisposition().withState().modified(true);
            peer.expectDetach().withError(notNullValue()).respond();
         } else {
            peer.expectDisposition().withState().modified(true);
         }

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();
         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(5);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   public static class ApplicationPropertiesTransformer implements Transformer {

      private final Map<String, String> properties = new HashMap<>();

      @Override
      public void init(Map<String, String> externalProperties) {
         properties.putAll(externalProperties);
      }

      @Override
      public org.apache.activemq.artemis.api.core.Message transform(org.apache.activemq.artemis.api.core.Message message) {
         if (!(message instanceof AMQPMessage)) {
            return message;
         }

         properties.forEach((k, v) -> {
            message.putStringProperty(k, v);
         });

         // An AMQP message must be encoded again to carry along the modifications.
         message.reencode();

         return message;
      }
   }
}
