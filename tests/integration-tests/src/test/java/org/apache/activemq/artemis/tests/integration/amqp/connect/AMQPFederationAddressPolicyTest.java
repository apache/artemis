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

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_ENABLE_DIVERT_BINDINGS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_EXCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_INCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_MAX_HOPS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_ADDRESS_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.EVENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_ADDRESS_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_BASE_VALIDATION_ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_POLICY_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V1;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V2;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_VERSION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_ADDRESS_BINDING_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.LARGE_MESSAGE_THRESHOLD;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.OPERATION_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_PROPERTIES_MAP;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS_LOW;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_LINK_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_ADDED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.TRANSFORMER_CLASS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.TRANSFORMER_PROPERTIES_MAP;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.FEDERATED_ADDRESS_SOURCE_PROPERTIES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.MESSAGE_HOPS_ANNOTATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.generateAddressFilter;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.ActiveMQServerAMQPFederationPlugin;
import org.apache.activemq.artemis.protocol.amqp.federation.Federation;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumer;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpJmsSelectorFilter;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpNoLocalFilter;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.apache.qpid.protonj2.test.driver.ProtonTestPeer;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.apache.qpid.protonj2.test.driver.codec.messaging.Source;
import org.apache.qpid.protonj2.test.driver.codec.primitives.DescribedType;
import org.apache.qpid.protonj2.test.driver.codec.primitives.Symbol;
import org.apache.qpid.protonj2.test.driver.codec.transport.Attach;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.HeaderMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.MessageAnnotationsMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.PropertiesMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.transport.TransferPayloadCompositeMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.types.EncodedAmqpValueMatcher;
import org.hamcrest.Matchers;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for AMQP Broker federation handling of the receive from and send to address policy configuration handling.
 */
public class AMQPFederationAddressPolicyTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected String federationUser = "fed_user";
   protected String federationPass = "fed_pass";

   protected String demandUser = "demand_user";
   protected String demandPass = "demand_pass";

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
   public void testFederationCreatesAddressReceiverWhenLocalQueueIsStaticlyDefined() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith("test::federation." + getTestName() + ".policy.address-policy.")).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists(), 5000, 100);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already federated the address and the statically added
         // queue should retain demand when this consumer leaves.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            session.createConsumer(session.createTopic("test"));
            session.createConsumer(session.createTopic("test"));

            connection.start();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This should trigger the federation consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from federated address to eliminate demand");
         server.destroyQueue(SimpleString.of("test"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("test")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesReceiverWithoutFQQNWhenLocalQueueIsStaticlyDefinedAndFQQNNotSupportedOnRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapabilities(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString()); // No FQQN for address subscriptions
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            not(containsString("address-policy")),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(getTestName()).and() // Legacy address only
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesReceiverWithStableFQQNWhenLocalDemandIsAppliedRepeatedly() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         final AtomicReference<String> capturedSourceAddress1 = new AtomicReference<>();
         final AtomicReference<String> capturedSourceAddress2 = new AtomicReference<>();

         peer.expectAttach().ofReceiver()
                            .withCapture((attach) -> capturedSourceAddress1.set(attach.getSource().getAddress()))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-policy"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName())).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already federated the address and the statically added
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

         // This should trigger the federation consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from federated address to eliminate demand");
         server.destroyQueue(SimpleString.of(getTestName()));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture((attach) -> capturedSourceAddress2.set(attach.getSource().getAddress()))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-policy"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName())).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Create new demand and ensure the attach carries a stable FQQN
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            connection.start();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         // Each connect to the remote should use a stable source address
         assertNotNull(capturedSourceAddress1.get());
         assertNotNull(capturedSourceAddress2.get());
         assertEquals(capturedSourceAddress1.get(), capturedSourceAddress2.get());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationUsesStableFQQNForAddressConsumersAcrossConnections() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(10);
         amqpConnection.setRetryInterval(10);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final AtomicReference<String> capturedSourceAddress1 = new AtomicReference<>();
         final AtomicReference<String> capturedSourceAddress2 = new AtomicReference<>();

         peer.expectAttach().ofReceiver()
                            .withCapture((attach) -> capturedSourceAddress1.set(attach.getSource().getAddress()))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-policy"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName())).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose().optional();
         peer.expectConnectionToDrop();
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.expectAttach().ofReceiver()
                            .withCapture((attach) -> capturedSourceAddress2.set(attach.getSource().getAddress()))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-policy"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName())).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Each connect to the remote should use a stable source address
         assertNotNull(capturedSourceAddress1.get());
         assertNotNull(capturedSourceAddress2.get());
         assertEquals(capturedSourceAddress1.get(), capturedSourceAddress2.get());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesAddressReceiverLinkForAddressMatch() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
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

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesAddressReceiverLinkForAddressMatchUsingPolicyCredit() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
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
         receiveFromAddress.addProperty(RECEIVER_CREDITS, "25");
         receiveFromAddress.addProperty(RECEIVER_CREDITS_LOW, "5");

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
         peer.expectFlow().withLinkCredit(25);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesAddressReceiverLinkForAddressMatchWithMaxHopsFilter() throws Exception {
      doTestFederationCreatesAddressReceiverLinkForAddressWithCorrectFilters(true);
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesAddressReceiverLinkForAddressMatchWithoutMaxHopsFilter() throws Exception {
      doTestFederationCreatesAddressReceiverLinkForAddressWithCorrectFilters(false);
   }

   private void doTestFederationCreatesAddressReceiverLinkForAddressWithCorrectFilters(boolean maxHops) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         if (maxHops) {
            receiveFromAddress.setMaxHops(1);
         } else {
            receiveFromAddress.setMaxHops(0); // Disabled
         }
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

         final String expectedJMSFilter = generateAddressFilter(1);
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");
         final Symbol noLocalKey = Symbol.valueOf("apache.org:no-local-filter:list");
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong noLocalCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000003L);
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         final Map<String, Object> selectors = new HashMap<>();
         selectors.put(AmqpSupport.JMS_SELECTOR_KEY.toString(), new AmqpJmsSelectorFilter(expectedJMSFilter));
         selectors.put(AmqpSupport.NO_LOCAL_NAME.toString(), new AmqpNoLocalFilter());

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, true);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withCapture(attach -> capturedAttach.set(attach))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Induce demand on the local broker which should then create a receiver to our remote peer.
         try (ProtonTestClient receivingPeer = new ProtonTestClient()) {
            receivingPeer.queueClientSaslAnonymousConnect();
            receivingPeer.connect("localhost", AMQP_PORT);
            receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            receivingPeer.expectOpen();
            receivingPeer.expectBegin();
            receivingPeer.expectAttach();
            receivingPeer.remoteOpen().withContainerId("test-sender").now();
            receivingPeer.remoteBegin().now();
            receivingPeer.remoteAttach().ofReceiver()
                                        .withInitialDeliveryCount(0)
                                        .withName("sending-peer")
                                        .withSource().withAddress("test")
                                                     .withCapabilities("topic").also()
                                        .withTarget().also()
                                        .now();
            receivingPeer.remoteFlow().withLinkCredit(10).now();
            receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            assertNotNull(capturedAttach.get());

            final Source remoteSource = capturedAttach.get().getSource();
            assertNotNull(remoteSource);
            final Map<Symbol, Object> filtersMap = remoteSource.getFilter();
            assertNotNull(filtersMap);

            if (maxHops) {
               assertTrue(filtersMap.containsKey(jmsSelectorKey));
               final DescribedType jmsSelectorEntry = (DescribedType) filtersMap.get(jmsSelectorKey);
               assertNotNull(jmsSelectorEntry);
               assertEquals(jmsSelectorEntry.getDescriptor(), jmsSelectorCode);
               assertEquals(jmsSelectorEntry.getDescribed().toString(), expectedJMSFilter);
            } else {
               assertFalse(filtersMap.containsKey(jmsSelectorKey));
            }

            assertTrue(filtersMap.containsKey(noLocalKey));
            final DescribedType noLocalEntry = (DescribedType) filtersMap.get(noLocalKey);
            assertNotNull(noLocalEntry);
            assertEquals(noLocalEntry.getDescriptor(), noLocalCode);

            // Check that annotation for hops is present in the forwarded message.
            final HeaderMatcher headerMatcher = new HeaderMatcher(true);
            final MessageAnnotationsMatcher annotationsMatcher = new MessageAnnotationsMatcher(true);
            annotationsMatcher.withEntry("x-opt-test", Matchers.equalTo("test"));
            annotationsMatcher.withEntry(MESSAGE_HOPS_ANNOTATION.toString(), Matchers.equalTo(1));
            final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher("Hello World");
            final TransferPayloadCompositeMatcher matcher = new TransferPayloadCompositeMatcher();
            matcher.setHeadersMatcher(headerMatcher);
            matcher.setMessageAnnotationsMatcher(annotationsMatcher);
            matcher.addMessageContentMatcher(bodyMatcher);

            // Broker should route the federated message to the client and it should
            // carry the hops annotation indicating that one hop has occurred.
            receivingPeer.expectTransfer().withPayload(matcher).accept();

            peer.expectDisposition().withState().accepted();
            peer.remoteTransfer().withHeader().withDurability(true)
                                 .also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "test").also()
                                 .withBody().withString("Hello World")
                                 .also()
                                 .withDeliveryId(1)
                                 .now();
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationClosesAddressReceiverLinkWhenDemandRemovedNoIdleTimeout() throws Exception {
      doTestFederationClosesAddressReceiverLinkWhenDemandRemoved(0);
   }

   @Test
   @Timeout(20)
   public void testFederationClosesAddressReceiverLinkWhenDemandRemovedShortIdleTimeout() throws Exception {
      doTestFederationClosesAddressReceiverLinkWhenDemandRemoved(5);
   }

   public void doTestFederationClosesAddressReceiverLinkWhenDemandRemoved(int idleTimeout) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, idleTimeout);

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
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
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
   public void testFederationRetainsAddressReceiverLinkWhenDurableSubscriberIsOffline() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
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
            connection.setClientID("test-clientId");

            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic("test");
            final MessageConsumer consumer = session.createSharedDurableConsumer(topic, "shared-subscription");

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Consumer goes offline but demand is retained for address
            consumer.close();

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
   public void testFederationClosesAddressReceiverLinkWaitsForAllDemandToRemoved() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
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
            final MessageConsumer consumer1 = session.createConsumer(session.createTopic("test"));
            final MessageConsumer consumer2 = session.createConsumer(session.createTopic("test"));

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
   public void testFederationHandlesAddressDeletedAndConsumerRecreates() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true).optional();
            peer.expectDetach().respond();

            server.removeAddressInfo(SimpleString.of("test"), null, true);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         // Consumer recreates Address and adds demand back and federation should restart
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
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
   public void testFederationConsumerCreatedWhenDemandAddedToDivertAddress() throws Exception {
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
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("test")
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
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
   public void testFederationConsumerCreatedWhenDemandAddedToCompositeDivertAddress() throws Exception {
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
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("test")
                                                                           .setForwardingAddress("forward1,forward2")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
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
   public void testFederationConsumerRemovesDemandFromDivertConsumersOnlyWhenAllDemandIsRemoved() throws Exception {
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
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("test")
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
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
   public void testFederationConsumerRetainsDemandForDivertBindingWithoutActiveAnycastSubscriptions() throws Exception {
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
         receiveFromAddress.addToIncludes("source"); // Divert matching works on the source address of the divert
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         // Any demand on the forwarding address even if the forward is a Queue (ANYCAST) should create
         // demand on the remote for the source address (If the source is MULTICAST)
         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("source")
                                                                           .setForwardingAddress("forward")
                                                                           .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         // Current implementation requires the source address exist on the local broker before it
         // will attempt to federate it from the remote.
         server.addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("source"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
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
   public void testFederationConsumerRemovesDemandForDivertBindingWithoutActiveMulticastSubscriptions() throws Exception {
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
         receiveFromAddress.addToIncludes("source"); // Divert matching works on the source address of the divert
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         // Any demand on the forwarding address even if the forward is a Queue (ANYCAST) should create
         // demand on the remote for the source address (If the source is MULTICAST)
         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("source")
                                                                           .setForwardingAddress("forward")
                                                                           .setRoutingType(ComponentConfigurationRoutingType.MULTICAST)
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         // Current implementation requires the source address exist on the local broker before it
         // will attempt to federate it from the remote.
         server.addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("source"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
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
   public void testFederationRemovesRemoteDemandIfDivertIsRemoved() throws Exception {
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
         receiveFromAddress.addToIncludes("source");
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("source")
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the forwarding address should create a remote consumer for the forwarding address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("source"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
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
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress("test")
                                                                           .setForwardingAddress("forward")
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Demand on the main address creates demand on the same address remotely and then the diverts
         // should just be tracked under that original demand.
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
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
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
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

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

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(0)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("test"));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
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
   public void testRemoteBrokerAcceptsAddressPolicyFromControlLink() throws Exception {
      server.start();

      final ArrayList<String> includes = new ArrayList<>();
      includes.add("address1");
      includes.add("address2");
      final ArrayList<String> excludes = new ArrayList<>();
      includes.add("address3");

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, excludes, null, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerAcceptsAddressPolicyFromControlLinkWithTransformerConfiguration() throws Exception {
      server.start();

      final ArrayList<String> includes = new ArrayList<>();
      includes.add("address1");
      includes.add("address2");
      final ArrayList<String> excludes = new ArrayList<>();
      includes.add("address3");

      final Map<String, String> transformerProperties = new HashMap<>();
      transformerProperties.put("key1", "value1");
      transformerProperties.put("key2", "value2");

      final TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
      transformerConfiguration.setClassName(ApplicationPropertiesTransformer.class.getName());
      transformerConfiguration.setProperties(transformerProperties);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, excludes, null, transformerConfiguration,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederatesAddressWhenDemandIsApplied() throws Exception {
      server.start();

      final List<String> includes = new ArrayList<>();
      includes.add("address1");

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 5);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress("address1")
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("address1"));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederatesAddressWhenDemandIsAppliedUsingFQQNWhenSupported() throws Exception {
      server.start();

      final List<String> includes = new ArrayList<>();
      includes.add("address1");

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 5);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress(startsWith("address1::federation.")) // FQQN prefix
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("address1"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederatesAddressWhenDemandIsAppliedUsingControllerDefinedLinkCredit() throws Exception {
      server.start();

      final List<String> includes = new ArrayList<>();
      includes.add("address1");

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", 10, 9);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress("address1")
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(10);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectFlow().withLinkCredit(10); // Should top up the credit as we set low to nine
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("address1"));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(10).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(11).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederatesAddressWhenDemandIsAppliedUsingPolicyDefinedLinkCredit() throws Exception {
      server.start();

      final List<String> includes = new ArrayList<>();
      includes.add("address1");

      final Map<String, Object> properties = new HashMap<>();
      properties.put(RECEIVER_CREDITS, 40);
      properties.put(RECEIVER_CREDITS_LOW, "39");
      properties.put(LARGE_MESSAGE_THRESHOLD, 1024);
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", 10, 9);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress("address1")
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(40);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectFlow().withLinkCredit(40); // Should top up the credit as we set low to 39
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("address1"));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(40).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(41).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederatesAddressAndAppliesTransformerWhenDemandIsApplied() throws Exception {
      server.start();

      final List<String> includes = new ArrayList<>();
      includes.add("address1");

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final Map<String, String> transformerProperties = new HashMap<>();
      transformerProperties.put("key1", "value1");
      transformerProperties.put("key2", "value2");

      final TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
      transformerConfiguration.setClassName(ApplicationPropertiesTransformer.class.getName());
      transformerConfiguration.setProperties(transformerProperties);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, null, properties, transformerConfiguration,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress("address1")
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("address1"));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());
            assertEquals("value1", message.getStringProperty("key1"));
            assertEquals("value2", message.getStringProperty("key2"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerAnswersAttachOfFederationReceiverProperly() throws Exception {
      server.start();

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 1L);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testReceiverWithMaxHopsFilterAppliesFilterCorrectly() throws Exception {
      server.start();

      final String maxHopsJMSFilter = "\"m." + MESSAGE_HOPS_ANNOTATION +
                                      "\" IS NULL OR \"m." + MESSAGE_HOPS_ANNOTATION +
                                      "\"<2";

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withSource().withAddress("test")
                                                    .withJMSSelector(maxHopsJMSFilter);

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .withJMSSelector(maxHopsJMSFilter)
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Match typical generic Qpid JMS Text Message structure
         final HeaderMatcher headerMatcher = new HeaderMatcher(true);
         final MessageAnnotationsMatcher annotationsMatcher = new MessageAnnotationsMatcher(true);
         final PropertiesMatcher properties = new PropertiesMatcher(true);
         final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher("Hello World");
         final TransferPayloadCompositeMatcher matcher = new TransferPayloadCompositeMatcher();
         matcher.setHeadersMatcher(headerMatcher);
         matcher.setMessageAnnotationsMatcher(annotationsMatcher);
         matcher.setPropertiesMatcher(properties);
         matcher.addMessageContentMatcher(bodyMatcher);

         peer.expectTransfer().withPayload(matcher).accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createTextMessage("Hello World"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.expectTransfer().withPayload(matcher).accept();

         try (ProtonTestClient sendingPeer = new ProtonTestClient()) {
            sendingPeer.queueClientSaslAnonymousConnect();
            sendingPeer.connect("localhost", AMQP_PORT);
            sendingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            sendingPeer.expectOpen();
            sendingPeer.expectBegin();
            sendingPeer.expectAttach();
            sendingPeer.expectFlow();
            sendingPeer.remoteOpen().withContainerId("test-sender").now();
            sendingPeer.remoteBegin().now();
            sendingPeer.remoteAttach().ofSender()
                                      .withInitialDeliveryCount(0)
                                      .withName("sending-peer")
                                      .withTarget().withAddress("test")
                                                   .withCapabilities("topic").also()
                                      .withSource().also()
                                      .now();
            sendingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            sendingPeer.expectDisposition().withSettled(true)
                                           .withState().accepted();
            sendingPeer.expectDisposition().withSettled(true)
                                           .withState().accepted();

            sendingPeer.remoteTransfer().withDeliveryId(0)
                                        .withHeader().withDurability(false).also()
                                        .withProperties().withMessageId("ID:1").also()
                                        .withMessageAnnotations().withAnnotation(MESSAGE_HOPS_ANNOTATION.toString(), 1).also()
                                        .withBody().withString("Hello World")
                                        .also()
                                        .now();

            // Should be accepted but not routed to the main test peer client acting as a federated receiver
            sendingPeer.remoteTransfer().withDeliveryId(1)
                                        .withHeader().withDurability(false).also()
                                        .withProperties().withMessageId("ID:2").also()
                                        .withMessageAnnotations().withAnnotation(MESSAGE_HOPS_ANNOTATION.toString(), 2).also()
                                        .withBody().withString("Hello World")
                                        .also()
                                        .now();
            sendingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteConnectionCannotAttachAddressFederationLinkWithoutControlLink() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.remoteOpen().queue();
         peer.expectOpen();
         peer.remoteBegin().queue();
         peer.expectBegin();
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         // Broker should reject the attach since there's no control link
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withSource(nullValue())
                                       .withTarget();
         peer.expectDetach().respond();

         // Attempt to create a federation address receiver link without existing control link
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testTransformInboundFederatedMessageBeforeDispatch() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final Map<String, String> newApplicationProperties = new HashMap<>();
         newApplicationProperties.put("appProperty1", "one");
         newApplicationProperties.put("appProperty2", "two");

         final TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
         transformerConfiguration.setClassName(ApplicationPropertiesTransformer.class.getName());
         transformerConfiguration.setProperties(newApplicationProperties);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.setTransformerConfiguration(transformerConfiguration);
         receiveFromAddress.addToIncludes("test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(0)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("test"));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());
            assertEquals("one", message.getStringProperty("appProperty1"));
            assertEquals("two", message.getStringProperty("appProperty2"));

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
   public void testFederationDoesNotCreateAddressReceiverLinkForAddressMatchWhenLinkCreditIsSetToZero() throws Exception {
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
            new AMQPBrokerConnectConfiguration(
               getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort() + "?amqpCredits=0");
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("test"));

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
   public void testCoreMessageConvertedToAMQPWhenTunnelingDisabled() throws Exception {
      doTestCoreMessageHandlingBasedOnTunnelingState(false);
   }

   @Test
   @Timeout(20)
   public void testCoreMessageNotConvertedToAMQPWhenTunnelingEnabled() throws Exception {
      doTestCoreMessageHandlingBasedOnTunnelingState(true);
   }

   private void doTestCoreMessageHandlingBasedOnTunnelingState(boolean tunneling) throws Exception {
      server.start();

      final String[] receiverOfferedCapabilities;
      final int messageFormat;

      if (tunneling) {
         receiverOfferedCapabilities = new String[] {AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString()};
         messageFormat = AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
      } else {
         receiverOfferedCapabilities = null;
         messageFormat = 0;
      }

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withDesiredCapabilities(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                                       .withSource().withAddress("test");

         // Connect to remote as if an address had demand and matched our federation policy
         // If core message tunneling is enabled we include the desired capability
         peer.remoteAttach().ofReceiver()
                            .withOfferedCapabilities(receiverOfferedCapabilities)
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectTransfer().withNonNullPayload()
                              .withMessageFormat(messageFormat).accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "CORE", "tcp://localhost:" + AMQP_PORT + "?minLargeMessageSize=512");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createTextMessage("Hello World"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testCoreLargeMessageConvertedToAMQPWhenTunnelingDisabled() throws Exception {
      doTestCoreLargeMessageHandlingBasedOnTunnelingState(false);
   }

   @Test
   @Timeout(20)
   public void testCoreLargeMessageNotConvertedToAMQPWhenTunnelingEnabled() throws Exception {
      doTestCoreLargeMessageHandlingBasedOnTunnelingState(true);
   }

   private void doTestCoreLargeMessageHandlingBasedOnTunnelingState(boolean tunneling) throws Exception {
      server.start();

      final String[] receiverOfferedCapabilities;
      final int messageFormat;

      if (tunneling) {
         receiverOfferedCapabilities = new String[] {AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString()};
         messageFormat = AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT;
      } else {
         receiverOfferedCapabilities = null;
         messageFormat = 0;
      }

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withDesiredCapabilities(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                                       .withSource().withAddress("test");

         // Connect to remote as if an address had demand and matched our federation policy
         // If core message tunneling is enabled we include the offered capability
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withOfferedCapabilities(receiverOfferedCapabilities)
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectTransfer().withNonNullPayload()
                              .withMessageFormat(messageFormat).accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "CORE", "tcp://localhost:" + AMQP_PORT + "?minLargeMessageSize=512");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            final byte[] payload = new byte[1024];
            Arrays.fill(payload, (byte) 65);

            final BytesMessage message = session.createBytesMessage();

            message.writeBytes(payload);

            producer.send(message);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testTunnledCoreMessageOnSenderThatDidNotDesireThatClosesConnection() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

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

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
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
   public void testFederationStartedTriggersRemoteDemandWithExistingAddressBindings() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand on the addresses so that on start federation should happen
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

         session.createConsumer(session.createTopic("test"));
         session.createConsumer(session.createTopic("test"));

         // Add other non-federation address bindings for the policy to check on start.
         session.createConsumer(session.createTopic("a1"));
         session.createConsumer(session.createTopic("a2"));

         connection.start();

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

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
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger federation of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add more demand while federation is starting
         session.createConsumer(session.createTopic("test"));
         session.createConsumer(session.createTopic("test"));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // This removes the connection demand, but leaves behind the static queue
         connection.close();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // This should trigger the federation consumer to be shutdown as the statically defined queue
         // should be the only remaining demand on the address.
         logger.info("Removing Queues from federated address to eliminate demand");
         server.destroyQueue(SimpleString.of("test"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("test")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationStartedTriggersRemoteDemandWithExistingAddressAndDivertBindings() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         final DivertConfiguration divert = new DivertConfiguration();
         divert.setName("test-divert");
         divert.setAddress("test");
         divert.setExclusive(false);
         divert.setForwardingAddress("target");
         divert.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         // Configure addresses and divert for the test
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target"), RoutingType.MULTICAST));
         server.deployDivert(divert);

         // Create demand on the addresses so that on start federation should happen
         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic test = session.createTopic("test");
         final Topic target = session.createTopic("target");

         session.createConsumer(test);
         session.createConsumer(test);

         session.createConsumer(target);
         session.createConsumer(target);

         // Add other non-federation address bindings for the policy to check on start.
         session.createConsumer(session.createTopic("a1"));
         session.createConsumer(session.createTopic("a2"));

         connection.start();

         // Should be no interactions at this point, check to make sure.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

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
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger federation of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add more demand while federation is starting
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
   public void testFederationStartTriggersFederationWithMultipleDivertsAndRemainsActiveAfterOneRemoved() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         final DivertConfiguration divert1 = new DivertConfiguration();
         divert1.setName("test-divert-1");
         divert1.setAddress("test");
         divert1.setExclusive(false);
         divert1.setForwardingAddress("target1,target2");
         divert1.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         final DivertConfiguration divert2 = new DivertConfiguration();
         divert2.setName("test-divert-2");
         divert2.setAddress("test");
         divert2.setExclusive(false);
         divert2.setForwardingAddress("target1,target3");
         divert2.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         // Configure addresses and divert for the test
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target1"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target2"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target3"), RoutingType.MULTICAST));
         server.deployDivert(divert1);
         server.deployDivert(divert2);

         // Create demand on the addresses so that on start federation should happen
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
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Starting the broker connection should trigger federation of address with demand.
         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add more demand while federation is starting
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
   public void testFederationPluginCanLimitDemandToOnlyTheConfiguredDivert() throws Exception {
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
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(5);
         amqpConnection.addElement(element);

         final DivertConfiguration divert = new DivertConfiguration();
         divert.setName("test-divert-1");
         divert.setAddress("test");
         divert.setExclusive(false);
         divert.setForwardingAddress("target");
         divert.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

         final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();
         federationPlugin.shouldCreateConsumerForDivert = (d, q) -> true;
         federationPlugin.shouldCreateConsumerForQueue = (q) -> {
            // Disallow any binding on the source address from creating federation demand
            // any other binding that matches the policy will create demand for federation.
            if (q.getAddress().toString().equals("test")) {
               return false;
            }

            return true;
         };

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         // Configure addresses and divert for the test
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("target"), RoutingType.MULTICAST));
         server.deployDivert(divert);
         server.registerBrokerPlugin(federationPlugin);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            // Should be ignored as plugin rejects this binding as demand.
            final MessageConsumer consumer = session.createConsumer(session.createTopic("test"));

            connection.start();

            // Get a round trip to the broker to allow time for federation to hopefully
            // reject this first consumer on the source address.
            consumer.receiveNoWait();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("test"),
                                               containsString("address-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respond()
                               .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createTopic("target"));

            // Now a federation receiver should get created for the divert demand.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesEventSenderAndReceiverWhenLocalAndRemotePoliciesAdded() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_ADDRESS_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("test");

      policyMap.put(POLICY_NAME, "remote-address-policy");
      policyMap.put(ADDRESS_AUTO_DELETE, false);
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);
      policyMap.put(ADDRESS_MAX_HOPS, 5);
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, false);
      policyMap.put(ADDRESS_INCLUDES, includes);

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(policyMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withHandle(0)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events-sender");
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withSource().withAddress("test-dynamic-events-receiver");
         peer.expectFlow().withLinkCredit(10);
         peer.remoteFlow().withLinkCredit(10).withHandle(0).queue(); // Give control link credit now to ensure ordering
         peer.expectTransfer().withPayload(payloadMatcher); // Remote policy
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement localReceiveFromAddress = new AMQPFederationAddressPolicyElement();
         localReceiveFromAddress.setName("address-policy");
         localReceiveFromAddress.addToIncludes("test");
         localReceiveFromAddress.setAutoDelete(false);
         localReceiveFromAddress.setAutoDeleteDelay(-1L);
         localReceiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederationAddressPolicyElement remoteReceiveFromAddress = new AMQPFederationAddressPolicyElement();
         remoteReceiveFromAddress.setName("remote-address-policy");
         remoteReceiveFromAddress.addToIncludes("test");
         remoteReceiveFromAddress.setAutoDelete(false);
         remoteReceiveFromAddress.setAutoDeleteDelay(-1L);
         remoteReceiveFromAddress.setAutoDeleteMessageCount(-1L);
         remoteReceiveFromAddress.setMaxHops(5);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(localReceiveFromAddress);
         element.addRemoteAddressPolicy(remoteReceiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationSendsRemotePolicyIfEventsSenderLinkRejected() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_ADDRESS_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("test");

      policyMap.put(POLICY_NAME, "remote-address-policy");
      policyMap.put(ADDRESS_AUTO_DELETE, false);
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);
      policyMap.put(ADDRESS_MAX_HOPS, 5);
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, false);
      policyMap.put(ADDRESS_INCLUDES, includes);

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(policyMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withHandle(0)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .reject(true, LinkError.DETACH_FORCED.toString(), "Unknown error");
         peer.expectDetach();
         peer.remoteFlow().withLinkCredit(10).withHandle(0).queue(); // Give control link credit now to ensure ordering
         peer.expectTransfer().withPayload(payloadMatcher); // Remote policy
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement remoteReceiveFromAddress = new AMQPFederationAddressPolicyElement();
         remoteReceiveFromAddress.setName("remote-address-policy");
         remoteReceiveFromAddress.addToIncludes("test");
         remoteReceiveFromAddress.setAutoDelete(false);
         remoteReceiveFromAddress.setAutoDeleteDelay(-1L);
         remoteReceiveFromAddress.setAutoDeleteMessageCount(-1L);
         remoteReceiveFromAddress.setMaxHops(5);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addRemoteAddressPolicy(remoteReceiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerSendsAddressAddedEventForInterestedPeer() throws Exception {
      final AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(false);
      addressSettings.setAutoCreateAddresses(false);

      server.getConfiguration().getAddressSettings().put("#", addressSettings);
      server.start();

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 10_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 1L);

      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(EVENT_TYPE.toString(), Matchers.is(REQUESTED_ADDRESS_ADDED));
      final Map<String, Object> eventMap = new LinkedHashMap<>();
      eventMap.put(REQUESTED_ADDRESS_NAME, "test");

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(eventMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", false, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withNullSource();
         peer.expectDetach().respond();

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectTransfer().withPayload(payloadMatcher).accept(); // Address added event

         // Manually add the address and a queue binding to create local demand.
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesAddressReceiverInResponseToAddressAddedEvent() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withHandle(0)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.remoteFlow().withLinkCredit(10);
         peer.expectAttach().ofReceiver()
                            .withHandle(1)
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
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

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         // Reject the initial attempt
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .respond()
                            .withNullSource()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Address not found")
                            .queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

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

         // Should not trigger attach of a federation receiver as the address doesn't match the policy..
         sendAddressAddedEvent(peer, "target", 1, 0);
         // Should trigger attach of federation receiver again for the test address.
         sendAddressAddedEvent(peer, "test", 1, 1);
         // Should not trigger attach of federation receiver as there already is one on this address
         sendAddressAddedEvent(peer, "test", 1, 2);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testAddressAddedEventIgnoredIfFederationConsumerAlreadyCreated() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withHandle(0)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.remoteFlow().withLinkCredit(10);
         peer.expectAttach().ofReceiver()
                            .withHandle(1)
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Reject the initial attempt
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .respond()
                            .withNullSource()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Address not found")
                            .queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         // Triggers the initial attach based on demand.
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.MULTICAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         // Create demand on the Address to kick off another federation attempt.
         session.createConsumer(session.createTopic("test"));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Should not trigger attach of federation receiver as there already is one on this address
         sendAddressAddedEvent(peer, "test", 1, 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerClosesFederationReceiverAfterAddressRemoved() throws Exception {
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", true, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withSource().withAddress("test");

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().withError(AmqpError.RESOURCE_DELETED.toString());

         // Force remove consumers from the address should indicate the resource was deleted.
         server.removeAddressInfo(SimpleString.of("test"), null, true);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
         maMatcher.withEntry(EVENT_TYPE.toString(), Matchers.is(REQUESTED_ADDRESS_ADDED));
         final Map<String, Object> eventMap = new LinkedHashMap<>();
         eventMap.put(REQUESTED_ADDRESS_NAME, "test");

         final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(eventMap);
         final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
         payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
         payloadMatcher.addMessageContentMatcher(bodyMatcher);

         // Server alerts the federation event receiver that a previously federated address
         // has been added once more and it could restore the previous federation state.
         peer.expectTransfer().withPayload(payloadMatcher).withSettled(true);

         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // This time removing and restoring should generate no traffic as there was not
         // another federation receiver added.
         server.removeAddressInfo(SimpleString.of("test"), null, true);
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationAddressDemandTrackedWhenRemoteRejectsInitialAttempts() throws Exception {
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
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic("test");

            connection.start();

            // First consumer we reject the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                               .respondInKind()
                               .withNullSource();
            peer.expectFlow().withLinkCredit(1000);
            peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested queue was not found").queue().afterDelay(10);
            peer.expectDetach();

            final MessageConsumer consumer1 = session.createConsumer(topic);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Second consumer we reject the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                               .respondInKind()
                               .withNullSource();
            peer.expectFlow().withLinkCredit(1000);
            peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested queue was not found").queue().afterDelay(10);
            peer.expectDetach();

            final MessageConsumer consumer2 = session.createConsumer(topic);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Third consumer we accept the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
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
   public void testFederationAddressDemandTrackedWhenPluginBlocksInitialAttempts() throws Exception {
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
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes("test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final AtomicInteger blockUntilZero = new AtomicInteger(2);
         final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();
         federationPlugin.shouldCreateConsumerForDivert = (d, q) -> true;
         federationPlugin.shouldCreateConsumerForQueue = (q) -> true;
         federationPlugin.shouldCreateConsumerForAddress = (a) -> {
            return blockUntilZero.getAndDecrement() == 0;
         };

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.registerBrokerPlugin(federationPlugin);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Topic topic = session.createTopic("test");

            connection.start();

            final MessageConsumer consumer1 = session.createConsumer(topic);
            final MessageConsumer consumer2 = session.createConsumer(topic);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Third consumer we expect the plugin to allow the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
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
   public void testBrokerAllowsAttachToPreviouslyNonExistentAddressAfterItIsAdded() throws Exception {
      final AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateAddresses(false);

      server.getConfiguration().getAddressSettings().put("#", addressSettings);
      server.start();

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, false);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withNullSource();
         peer.expectDetach().respond();

         // Connect to remote as if an queue had demand and matched our federation policy
         // and expect a rejected attach as the address does not yet exist and auto create
         // has been disabled.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.addAddressInfo(new AddressInfo("test").addRoutingType(RoutingType.MULTICAST));

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Attempt attach again as if new address demand has been added and the policy manager reacts.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testAddressPolicyCanOverridesZeroCreditsInFederationConfigurationAndFederateAddress() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
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
         receiveFromAddress.addProperty(RECEIVER_CREDITS, 10);
         receiveFromAddress.addProperty(RECEIVER_CREDITS_LOW, 3);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(RECEIVER_CREDITS, 0);

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
         peer.expectFlow().withLinkCredit(10);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationReceiverCloseWhenDemandRemovedDoesNotTerminateRemoteConnection() throws Exception {
      server.start();

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 1_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 1L);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectTransfer().accept();

         // Federate a message to check link is attached properly
         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createMessage());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach();
         peer.remoteDetach().now();  // simulate demand removed so consumer is closed.

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if new demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationReceiverCloseWithErrorTerminateRemoteConnection() throws Exception {
      server.start();

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 1_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 1L);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectTransfer().accept();

         // Federate a message to check link is attached properly
         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createMessage());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Under normal circumstances the federation source will never close one of its
         // receivers with an error, so if that happens the remote will shutdown the connection
         // and let the local rebuild.

         peer.expectDetach();
         peer.expectClose().withError(AmqpError.INTERNAL_ERROR.toString()).respond();

         peer.remoteDetach().withErrorCondition(AmqpError.RESOURCE_DELETED.toString(), "error").now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteReceiverClosedWhenDemandRemovedCleansUpAddressBinding() throws Exception {
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, false);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 1_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         // Precondition is that there were no bindings before the federation receiver attaches.
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectTransfer().accept();

         // Federation consumer should be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1);

         // Federate a message to check link is attached properly
         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createMessage());
         }

         // Federation consumer should create a binding using the link name
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().contains(SimpleString.of("federation-address-receiver")));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach();
         peer.remoteDetach().now();  // simulate demand removed so consumer is closed.

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Federation consumer should no longer be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteReceiverClosedWhenDemandRemovedCleansUpAddressBindingWhenUsingFQQN() throws Exception {
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, false);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 1_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", true);
         peer.connect("localhost", AMQP_PORT);

         // Precondition is that there were no bindings before the federation receiver attaches.
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test::test-address-binding");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test-address-binding")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectTransfer().accept();

         // Federation consumer should be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1);

         // Federate a message to check link is attached properly
         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createMessage());
         }

         // Federation consumer should use the queue part of the FQQN we set on the source address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().contains(SimpleString.of("test-address-binding")));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach();
         peer.remoteDetach().now();  // simulate demand removed so consumer is closed.

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Federation consumer should no longer be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteConnectionSuddenDropLeaveAddressBindingIntact() throws Exception {
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, false);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         // Precondition is that there were no bindings before the federation receiver attaches.
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectTransfer().withMessage().withHeader().also()
                                            .withMessageAnnotations().also()
                                            .withProperties().also()
                                            .withValue("one").and()
                                            .accept();

         // Federation consumer should be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1);

         // Federate a message to check link is attached properly
         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic("test"));

            producer.send(session.createTextMessage("one"));
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         // Unexpected connection drop should leave durable federation address subscription in place.
         Wait.assertTrue(() -> server.getConnectionCount() == 0);
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1);
      }

      // Send a message to check that the federation binding holds onto sends while the remote is offline
      // due to connectivity issues.
      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection()) {
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final MessageProducer producer = session.createProducer(session.createTopic("test"));

         producer.send(session.createTextMessage("two"));
      }

      // Reconnect again as if the remote has recovered from the unexpected connection drop
      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         // Precondition is that there was still a binding from the previous federation whose connection dropped
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");
         peer.expectTransfer().withMessage().withHeader().also()
                                            .withMessageAnnotations().also()
                                            .withProperties().also()
                                            .withValue("two").and()
                                            .accept();

         // Connect again to remote as if local demand still matches our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach();
         peer.remoteDetach().now();  // simulate demand removed so consumer is closed.

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Federation consumer should no longer be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationAddressBindingCleanedUpAfterConnectionDroppedIfConfiguredTo() throws Exception {
      doTestFederationAddressBindingAppliesAutoDeletePolicyToCreatedQueue(true);
   }

   @Test
   @Timeout(20)
   public void testFederationAddressBindingNotCleanedUpAfterConnectionDroppedIfConfiguredNotTo() throws Exception {
      doTestFederationAddressBindingAppliesAutoDeletePolicyToCreatedQueue(false);
   }

   private void doTestFederationAddressBindingAppliesAutoDeletePolicyToCreatedQueue(boolean autoDelete) throws Exception {
      server.getConfiguration().setAddressQueueScanPeriod(100);
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      if (autoDelete) {
         remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
         remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 200L);
         remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);
      } else {
         remoteSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);
      }

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         // Precondition is that there were no bindings before the federation receiver attaches.
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test")
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Federation consumer should be bound to the server's address
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1, 5_000, 500);

         final SimpleString binding = server.bindingQuery(SimpleString.of("test")).getQueueNames().get(0);
         assertNotNull(binding);
         assertTrue(binding.startsWith(SimpleString.of("federation")));

         final QueueQueryResult federationBinding = server.queueQuery(binding);
         if (autoDelete) {
            assertTrue(federationBinding.isAutoDelete());
            assertEquals(200, federationBinding.getAutoDeleteDelay());
            assertEquals(-1, federationBinding.getAutoDeleteMessageCount());
         } else {
            assertFalse(federationBinding.isAutoDelete());
            assertEquals(-1, federationBinding.getAutoDeleteDelay());
            assertEquals(-1, federationBinding.getAutoDeleteMessageCount());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         if (autoDelete) {
            // Queue binding should eventually be auto deleted based on configuration
            Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().isEmpty(), 5_000, 100);
         } else {
            // Should still be there as it wasn't marked as auto delete as previously validated.
            Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() == 1, 1_000, 100);
         }

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testNewFederationConsumerCreatedWhenDemandRemovedAndAddedWithDelayedPreviousDetach() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond().afterDelay(40); // Defer the detach response for a bit

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand on the address which creates a federation consumer then let it close which
         // should shut down that federation consumer.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            consumer.receiveNoWait();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // Create demand on the address which creates a federation consumer again quickly which
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
   public void testFederationLinksRestartedBeforeRemoteFinishesDraining() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 5);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true);  // Don't answer drained
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            // This should result from the answer to the next flow that drains the link.
            peer.expectFlow().withLinkCredit(1000);

            // Now answer the drained and expect that the queue policy manager will then
            // restart flow of credit based on new demand as expected above.
            peer.remoteFlow().withLinkCredit(0).withDeliveryCount(1000).withDrain(true).now();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
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
   public void testFederationLinksDetachesAfterLinkQuiesceTimeoutNoIldeTimeout() throws Exception {
      doTestFederationLinksDetachesAfterLinkQuiesceTimeout(0);
   }

   @Test
   @Timeout(20)
   public void testFederationLinksDetachesAfterLinkQuiesceTimeoutAndIdleTimeout() throws Exception {
      doTestFederationLinksDetachesAfterLinkQuiesceTimeout(10);
   }

   public void doTestFederationLinksDetachesAfterLinkQuiesceTimeout(int idleTimeout) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 20);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, idleTimeout);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
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
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         // New demand should create a new federation consumer after the last drain timed out and closed the link
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
   public void testFederationLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemand() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respondInKind()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 300);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
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
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectDetach().respond();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Demand goes away and the federation link is closed.
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
   public void testFederationLinksRecoveredAfterLinkQuiescedButNotIdledOut() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 10_000);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
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
   public void testFederationLinkIdleTimeoutAtPolicyLevelOverridesTopLevelConfiguration() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 250);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
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
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
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
   public void testFederationResourceDeletedBeforeLinkQuiesceCompletes() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind();
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
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
   public void testLegacyStolenLinkHandlesEventualDetachResponseByTerminatingBrokerConnection() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);
         amqpConnection.setRetryInterval(20);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Create address and binding which creates demand.
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach(); // No response yet as we want to evaluate eventual response.

         // Remove demand which should create a detach cycle.
         server.destroyQueue(SimpleString.of(getTestName()));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().optional(); // Proton sends flow on detached link that was stolen.
         peer.expectConnectionToDrop();

         // Create queue again which creates demand again rapidly and steals the link
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.remoteDetach().withHandle(2).later(20);

         // Eventually when the detach arrives the link detach is treated as unexpected because it
         // is now attached to a new federation address consumer instance and the broker connection
         // is terminated and rebuilt.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesFilteredAddressReceiversWhenLocalQueuesAreStaticlyDefined() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         element.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, "false");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         final String expectedJMSFilter1 = "color='red'";
         final String expectedJMSFilter2 = "color='blue'";
         final AtomicReference<Attach> capturedAttach1 = new AtomicReference<>();
         final AtomicReference<Attach> capturedAttach2 = new AtomicReference<>();
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach1.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName() + ".policy.address-policy.")).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false)
                                                           .setFilterString(expectedJMSFilter1));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach2.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName() + ".policy.address-policy.")).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false)
                                                           .setFilterString(expectedJMSFilter2));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already federated the address and the statically added
         // queue should retain demand when this consumer leaves.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter1);
            session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter2);

            connection.start();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertNotNull(capturedAttach1.get());
         assertNotNull(capturedAttach2.get());

         final Map<Symbol, Object> filtersMap1 = capturedAttach1.get().getSource().getFilter();
         final Map<Symbol, Object> filtersMap2 = capturedAttach2.get().getSource().getFilter();

         assertNotNull(filtersMap1);
         assertTrue(filtersMap1.containsKey(jmsSelectorKey));
         final DescribedType jmsSelectorEntry1 = (DescribedType) filtersMap1.get(jmsSelectorKey);
         assertNotNull(jmsSelectorEntry1);
         assertEquals(jmsSelectorEntry1.getDescriptor(), jmsSelectorCode);
         assertEquals(jmsSelectorEntry1.getDescribed().toString(), expectedJMSFilter1);

         assertNotNull(filtersMap2);
         assertTrue(filtersMap2.containsKey(jmsSelectorKey));
         final DescribedType jmsSelectorEntry2 = (DescribedType) filtersMap2.get(jmsSelectorKey);
         assertNotNull(jmsSelectorEntry2);
         assertEquals(jmsSelectorEntry2.getDescriptor(), jmsSelectorCode);
         assertEquals(jmsSelectorEntry2.getDescribed().toString(), expectedJMSFilter2);

         // Receiver closed when address binding 'queue1' is removed
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         logger.info("Removing Queue 1 from federated address to eliminate demand from that filtered consumer");
         server.destroyQueue(SimpleString.of("queue1"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("queue1")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Receiver closed when address binding 'queue2' is removed
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         logger.info("Removing Queue 2 from federated address to eliminate demand from that filtered consumer");
         server.destroyQueue(SimpleString.of("queue2"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("queue2")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesFilteredAddressReceiversWhenConsumerAddedAndRouteMessage() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         element.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, "false");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         final String expectedJMSFilter = "color='red'";
         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         // This should be the consumer without a message selector
         peer.expectAttach().ofReceiver()
                            .withHandle(2)
                            .withCapture(attach -> capturedAttach.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName() + ".policy.address-policy.")).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already federated the address and the statically added
         // queue should retain demand when this consumer leaves.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            final MessageConsumer consumer0 = session.createConsumer(session.createTopic(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final Map<Symbol, Object> filtersMap0 = capturedAttach.get().getSource().getFilter();

            assertNotNull(filtersMap0);
            assertFalse(filtersMap0.containsKey(jmsSelectorKey));

            peer.expectAttach().ofReceiver()
                               .withHandle(3)
                               .withCapture(attach -> capturedAttach.set(attach))
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                               .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                               .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName() + ".policy.address-policy.")).and()
                               .respond()
                               .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
            peer.expectFlow().withLinkCredit(1000);

            final MessageConsumer consumer1 = session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter);
            final MessageConsumer consumer2 = session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter);
            final MessageConsumer consumer3 = session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            assertNotNull(capturedAttach.get());

            final Map<Symbol, Object> filtersMap1 = capturedAttach.get().getSource().getFilter();

            assertNotNull(filtersMap1);
            assertTrue(filtersMap1.containsKey(jmsSelectorKey));
            final DescribedType jmsSelectorEntry = (DescribedType) filtersMap1.get(jmsSelectorKey);
            assertNotNull(jmsSelectorEntry);
            assertEquals(jmsSelectorEntry.getDescriptor(), jmsSelectorCode);
            assertEquals(jmsSelectorEntry.getDescribed().toString(), expectedJMSFilter);

            connection.start();

            peer.expectDisposition().withState().accepted();
            peer.expectDisposition().withState().accepted();
            peer.remoteTransfer().withHandle(3) // This is the filtered federation consumer link
                                 .withHeader().withDurability(true).also()
                                 .withApplicationProperties().withProperty("color", "red").also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "test").also()
                                 .withBody().withString("First Message")
                                 .also()
                                 .withDeliveryId(1)
                                 .now();
            peer.remoteTransfer().withHandle(3) // This is the filtered federation consumer link
                                 .withHeader().withDurability(true).also()
                                 .withApplicationProperties().withProperty("color", "red").also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "test").also()
                                 .withBody().withString("Second Message")
                                 .also()
                                 .withDeliveryId(2)
                                 .now();
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Consumer without filter gets nothing as remote targeted the filtered peer
            assertNull(consumer0.receiveNoWait());

            // All should get the message routed even though one was sent from the peer.
            assertNotNull(consumer1.receiveNoWait());
            assertNotNull(consumer2.receiveNoWait());
            assertNotNull(consumer3.receiveNoWait());

            // All should get the message routed even though one was sent from the peer.
            assertNotNull(consumer1.receiveNoWait());
            assertNotNull(consumer2.receiveNoWait());
            assertNotNull(consumer3.receiveNoWait());

            // Should be no more messages
            assertNull(consumer1.receiveNoWait());
            assertNull(consumer2.receiveNoWait());
            assertNull(consumer3.receiveNoWait());

            peer.expectDisposition().withState().accepted();
            peer.remoteTransfer().withHandle(2) // This is the non-filtering address consumer
                                 .withHeader().withDurability(true).also()
                                 .withApplicationProperties().withProperty("color", "red").also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "test").also()
                                 .withBody().withString("Third Message")
                                 .also()
                                 .withDeliveryId(3)
                                 .now();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Consumer without filter gets the message as it was targeted by the remote
            assertNotNull(consumer0.receiveNoWait());

            // Close the consumer without filter to create predictable shutdown order for flow and detach
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();

            consumer0.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Should be no messages sent to these consumer bindings
            assertNull(consumer1.receiveNoWait());
            assertNull(consumer2.receiveNoWait());
            assertNull(consumer3.receiveNoWait());

            // The remaining link will be closed when the connection drops and the demand is removed.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(998).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();
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
   public void testFederationCreatesSingleUnfilteredConsumerWhenRemoteDoesNotSupportFQQNSubs() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         element.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, "false");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final Map<String, Object> expectedSourceProperties = new HashMap<>();
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE, false);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, -1L);
         expectedSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, -1L);

         final String jmsFilterQueue1 = "color='red'";
         final String jmsFilterQueue2 = "color='blue'";
         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();

         // Remote indicated no support for FQQN subscriptions and so we cannot create federation consumers
         // with filters as unique link names cannot be generated so it falls back to a single conduit type
         // subscription with no JMS selector filter assigned.
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), expectedSourceProperties)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false)
                                                           .setFilterString(jmsFilterQueue1));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertNotNull(capturedAttach.get());

         final Map<Symbol, Object> filtersMap = capturedAttach.get().getSource().getFilter();
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");

         assertNotNull(filtersMap);
         assertFalse(filtersMap.containsKey(jmsSelectorKey));

         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false)
                                                           .setFilterString(jmsFilterQueue2));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already federated the address and the statically added
         // queue should retain demand when this consumer leaves.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            session.createConsumer(session.createTopic(getTestName()), jmsFilterQueue1);
            session.createConsumer(session.createTopic(getTestName()), jmsFilterQueue2);

            connection.start();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         logger.info("Removing Queues from federated address to eliminate demand");
         server.destroyQueue(SimpleString.of("queue1"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("queue1")).isExists());
         server.destroyQueue(SimpleString.of("queue2"));
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of("queue2")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testMaxHopsPreservedInFilterWhenNotIgnoringBindingsFilters() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setMaxHops(3);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         element.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, "false");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final String expectedJMSFilter = "color='red'";
         final String expectedMaxHopsFilter = generateAddressFilter(3);
         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .withSource().withAddress(startsWith(getTestName() + "::federation." + getTestName() + ".policy.address-policy.")).and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Should be no frames generated as we already federated the address and the statically added
         // queue should retain demand when this consumer leaves.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter);
            session.createConsumer(session.createTopic(getTestName()), expectedJMSFilter);

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertNotNull(capturedAttach.get());

         final Map<Symbol, Object> filtersMap = capturedAttach.get().getSource().getFilter();

         assertNotNull(filtersMap);
         assertTrue(filtersMap.containsKey(jmsSelectorKey));
         final DescribedType jmsSelectorEntry = (DescribedType) filtersMap.get(jmsSelectorKey);
         assertNotNull(jmsSelectorEntry);
         assertEquals(jmsSelectorEntry.getDescriptor(), jmsSelectorCode);

         final String sentJMSFilter = jmsSelectorEntry.getDescribed().toString();
         assertTrue(sentJMSFilter.contains(expectedJMSFilter));
         assertTrue(sentJMSFilter.contains(expectedMaxHopsFilter));

         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRouteToAddressHandlesAddressFullWithFailPolicy() throws Exception {
      doTestRoutedMessageReturnsRejectedToRemoteWhenAddressFull(true, false);
   }

   @Test
   @Timeout(20)
   public void testRouteToBindingsHandlesAddressFullWithFailPolicy() throws Exception {
      doTestRoutedMessageReturnsRejectedToRemoteWhenAddressFull(false, false);
   }

   @Test
   @Timeout(20)
   public void testRouteToAddressHandlesAddressFullWithFailPolicyButSendsModifiedDisposition() throws Exception {
      doTestRoutedMessageReturnsRejectedToRemoteWhenAddressFull(true, true);
   }

   @Test
   @Timeout(20)
   public void testRouteToBindingsHandlesAddressFullWithFailPolicyButSendsModifiedDisposition() throws Exception {
      doTestRoutedMessageReturnsRejectedToRemoteWhenAddressFull(false, true);
   }

   private void doTestRoutedMessageReturnsRejectedToRemoteWhenAddressFull(boolean ignoreFilters, boolean useModifiedForReject) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         element.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, Boolean.toString(ignoreFilters));
         element.addProperty(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS, Boolean.toString(useModifiedForReject));

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(),
               "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort() + "?amqpUseModifiedForTransientDeliveryErrors=" + !useModifiedForReject);
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
         final String expectedJMSFilter = "color='red'";
         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false)
                                                           .setFilterString(expectedJMSFilter));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false)
                                                           .setFilterString(expectedJMSFilter));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertNotNull(capturedAttach.get());

         final Map<Symbol, Object> filtersMap = capturedAttach.get().getSource().getFilter();
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");

         assertNotNull(filtersMap);
         if (ignoreFilters) {
            assertFalse(filtersMap.containsKey(jmsSelectorKey));
         } else {
            assertTrue(filtersMap.containsKey(jmsSelectorKey));
            final DescribedType jmsSelectorEntry = (DescribedType) filtersMap.get(jmsSelectorKey);
            assertNotNull(jmsSelectorEntry);
            assertEquals(jmsSelectorEntry.getDescriptor(), jmsSelectorCode);
            assertEquals(jmsSelectorEntry.getDescribed().toString(), expectedJMSFilter);
         }

         peer.expectDisposition().withState().accepted(); // This should fill the address

         // In either configuration we drain the link credit to avoid repeated sends of the failed
         // deliveries where possible.
         peer.expectFlow().withDrain(true).withLinkCredit(998)
                          .respond()
                          .withDrain(true).withLinkCredit(0).withDeliveryCount(998).afterDelay(5);

         // If sending modified the remote won't discard the message, it will send it again
         if (useModifiedForReject) {
            peer.expectDisposition().withState().modified(true);
         } else {
            peer.expectDisposition().withState().rejected();
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
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationConsumerFilteredCreatedWhenDemandAddedToDivertWithFilterAddress() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);
         receiveFromAddress.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, "false");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final String expectedJMSFilter = "color='red'";
         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         // The filter at the divert is honored if configured. We do not look further down the chain though
         // so any binding on a diverts forward is not checked for a filter.
         final DivertConfiguration divertConfig = new DivertConfiguration().setAddress(getTestName())
                                                                           .setForwardingAddress("forward")
                                                                           .setFilterString(expectedJMSFilter)
                                                                           .setName("test-divert");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.deployDivert(divertConfig);
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Demand on the forwarding address should create a remote consumer for the forwarded address.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withCapture(attach -> capturedAttach.set(attach))
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic("forward"), "property='a'");

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final Map<Symbol, Object> filtersMap = capturedAttach.get().getSource().getFilter();
            final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");

            assertNotNull(filtersMap);
            assertTrue(filtersMap.containsKey(jmsSelectorKey));
            final DescribedType jmsSelectorEntry = (DescribedType) filtersMap.get(jmsSelectorKey);
            assertNotNull(jmsSelectorEntry);
            assertEquals(jmsSelectorEntry.getDescriptor(), jmsSelectorCode);
            assertEquals(jmsSelectorEntry.getDescribed().toString(), expectedJMSFilter);

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
   public void testRemoteReFederatesAddressWhenDemandIsAppliedAfterInitialDeliveryMarkedModifiedFailed() throws Exception {
      server.start();

      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 100_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 1L);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress(getTestName() + "::" + getTestName());

         // Connect to remote as if an address had demand and matched our local federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName("federation-address-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress(getTestName() + "::" + getTestName())
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(1).withDeliveryCount(0).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         // Simulate transient delivery error by settling with modified and delivery failed
         peer.expectTransfer().withMessage().withHeader().also()
                                            .withMessageAnnotations().also()
                                            .withProperties().also()
                                            .withValue("Message").and()
                                            .modify(true);

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 5000, 100);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic(getTestName()));
            final Message message = session.createTextMessage("Message");

            producer.send(message);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         // Next grant of credit will deliver the message again
         peer.expectTransfer().withMessage().withHeader().also()
                                            .withMessageAnnotations().also()
                                            .withProperties().also()
                                            .withValue("Message").and()
                                            .accept();
         peer.remoteFlow().withLinkCredit(1).withDeliveryCount(1).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testNonFilteringFederationReceiverRejectsWithModifiedDeliveryFailedAsDefault() throws Exception {
      doTestFederationReceiverRejectsWithModifiedDeliveryFailedAsDefault(true);
   }

   @Test
   @Timeout(20)
   public void testFilteringFederationReceiverRejectsWithModifiedDeliveryFailedAsDefault() throws Exception {
      doTestFederationReceiverRejectsWithModifiedDeliveryFailedAsDefault(false);
   }

   protected void doTestFederationReceiverRejectsWithModifiedDeliveryFailedAsDefault(boolean ignoreFilters) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, String.valueOf(ignoreFilters));

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

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
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
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
   @Timeout(20)
   public void testRemoteFederationRespectsUseModifiedForRejectConfigurationWhenSetToReject() throws Exception {
      testRemoteFederationRespectsUseModifiedForRejectConfiguration(false, true);
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationRespectsUseModifiedForRejectConfigurationWhenSetToModify() throws Exception {
      testRemoteFederationRespectsUseModifiedForRejectConfiguration(true, true);
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationRespectsUseModifiedForRejectConfigurationWhenSetToRejectNoDrainOnFull() throws Exception {
      testRemoteFederationRespectsUseModifiedForRejectConfiguration(false, false);
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationRespectsUseModifiedForRejectConfigurationWhenSetToModifyNoDrainOnFull() throws Exception {
      testRemoteFederationRespectsUseModifiedForRejectConfiguration(true, false);
   }

   private void testRemoteFederationRespectsUseModifiedForRejectConfiguration(boolean useModified, boolean drainOnAddressFull) throws Exception {
      final AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(getTestName());
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch(getTestName(), addressSettings);
      server.start();

      final String payload = "A".repeat(2048);

      final List<String> includes = new ArrayList<>();
      includes.add(getTestName());

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 5);
      properties.put(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS, useModified);
      properties.put(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, drainOnAddressFull);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, -1, true,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress(notNullValue())
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false)
                                                                .setFilterString("color='red'"));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectDisposition().withState().accepted();

         if (drainOnAddressFull) {
            peer.expectFlow().withLinkCredit(998).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000);
         }

         if (useModified) {
            peer.expectDisposition().withState().modified(true);
         } else {
            peer.expectDisposition().withState().rejected();
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
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   public void testDrainReceiverOnTransientErrorsConfiguredAtFederationLevel() throws Exception {
      doTestDrainReceiverOnTransientErrorsConfiguredAtFederationLevel(true);
   }

   @Test
   public void testNoDrainReceiverOnTransientErrorsConfiguredAtFederationLevel() throws Exception {
      doTestDrainReceiverOnTransientErrorsConfiguredAtFederationLevel(false);
   }

   private void doTestDrainReceiverOnTransientErrorsConfiguredAtFederationLevel(boolean drainOnFull) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
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
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "address-policy")
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
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

   @Test
   @Timeout(20)
   public void testFederationSourceDoesNotTreatTargetFederationReceiversAsLocalDemand() throws Exception {
      doTestFederationSourceDoesNotTreatTargetFederationReceiversAsLocalDemand(false);
   }

   @Test
   @Timeout(20)
   public void testFederationSourceDoesNotTreatTargetFilteredFederationReceiversAsLocalDemand() throws Exception {
      doTestFederationSourceDoesNotTreatTargetFederationReceiversAsLocalDemand(true);
   }

   private void doTestFederationSourceDoesNotTreatTargetFederationReceiversAsLocalDemand(boolean includeFilterId) throws Exception {
      final Map<String, Object> remoteSourceProperties = new HashMap<>();
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE, true);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_DELAY, 100_000L);
      remoteSourceProperties.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 1L);

      final String remoteNodeID = UUID.randomUUID().toString();
      final String federationBindingName;

      if (includeFilterId) {
         federationBindingName = "federation.test-name.policy.test123.address." + getTestName() + ".filterId.123456789.node." + remoteNodeID;
      } else {
         federationBindingName = "federation.test-name.policy.test123.address." + getTestName() + ".node." + remoteNodeID;
      }

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond().withContainerId(remoteNodeID);
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respond()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.expectAttach().ofReceiver()
                            .withSenderSettleModeSettled()
                            .withSource().withDynamic(true)
                            .and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("test-dynamic-events");
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(getTestName());
         receiveFromAddress.setAutoDelete(false);
         receiveFromAddress.setAutoDeleteDelay(-1L);
         receiveFromAddress.setAutoDeleteMessageCount(-1L);
         receiveFromAddress.setEnableDivertBindings(true);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);
         element.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-address-receiver")
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress(getTestName() + "::" + federationBindingName);
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectDetach();

         // Connect to remote as if the address had demand and matched our local federation policy
         // since this uses the federation based address binding name the broker should not treat
         // it as local demand and should not create a federation receiver back to the test peer.
         if (includeFilterId) {
            peer.remoteAttach().ofReceiver()
                               .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                               .withName("federation-address-receiver")
                               .withSenderSettleModeUnsettled()
                               .withReceivervSettlesFirst()
                               .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                               .withSource().withDurabilityOfNone()
                                            .withExpiryPolicyOnLinkDetach()
                                            .withAddress(getTestName() + "::" + federationBindingName)
                                            .withCapabilities("topic")
                                            .withJMSSelector("color='red'")
                                            .and()
                               .withTarget().and()
                               .now();
         } else {
            peer.remoteAttach().ofReceiver()
                               .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                               .withName("federation-address-receiver")
                               .withSenderSettleModeUnsettled()
                               .withReceivervSettlesFirst()
                               .withProperty(FEDERATED_ADDRESS_SOURCE_PROPERTIES.toString(), remoteSourceProperties)
                               .withSource().withDurabilityOfNone()
                                            .withExpiryPolicyOnLinkDetach()
                                            .withAddress(getTestName() + "::" + federationBindingName)
                                            .withCapabilities("topic")
                                            .and()
                               .withTarget().and()
                               .now();
         }

         // Create a window where a remote attach for federation receiver can arrive if the attach of this
         // federation receiver from the remote peer was treated as demand. Peer is awaiting the response
         // so we will block on the wait until the Detach arrives.
         peer.remoteDetach().later(20);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final String otherFederationNodeId = UUID.randomUUID().toString();
         final String otherFederationBindingName;

         if (includeFilterId) {
            otherFederationBindingName = "federation.test-name.policy.test123.address." + getTestName() + ".filterId.123456789.node." + otherFederationNodeId;
         } else {
            otherFederationBindingName = "federation.test-name.policy.test123.address." + getTestName() + ".node." + otherFederationNodeId;
         }

         // Create any other federation node binding and we should get a federation receiver created to the test peer.
         server.createQueue(QueueConfiguration.of(otherFederationBindingName).setRoutingType(RoutingType.MULTICAST)
                                                                             .setAddress(getTestName())
                                                                             .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationDoesNotCreateLinksForAddressItCannotWriteTo() throws Exception {
      final String allowedAddress = getTestName();
      final String restrictedAddress = getTestName() + "restricted"; // Federation user cannot send here.

      configureSecurity(server, allowedAddress, restrictedAddress);
      server.start();

      final Set<String> includes = new HashSet<>();
      includes.add(allowedAddress);
      includes.add(restrictedAddress);

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 1);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, false,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, federationUser, federationPass, "test", true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress(startsWith(allowedAddress))
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Demand on the unrestricted address should generate federation links and move the message
         try (Connection connection = factory.createConnection(demandUser, demandPass)) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(allowedAddress));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Demand on the restricted address should result in no federation links being attempted.
         try (Connection connection = factory.createConnection(demandUser, demandPass)) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(restrictedAddress));

            connection.start();

            final Message message = consumer.receiveNoWait();
            assertNull(message);
         }

         // This would fail if the server tries creating a federation link
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationDoesNotCreateLinksForAddressItCannotWriteToThatHasDemandOnEnabledDivert() throws Exception {
      final String fowardAllowed = "forward-allowed";
      final String fowardRestricted = "forward-restricted";
      final String allowedAddress = getTestName();
      final String restrictedAddress = getTestName() + "restricted"; // Federation user cannot send here.

      configureSecurity(server, allowedAddress, restrictedAddress, fowardAllowed, fowardRestricted);
      server.start();

      final DivertConfiguration divertConfig1 = new DivertConfiguration().setAddress(allowedAddress)
                                                                         .setForwardingAddress(fowardAllowed)
                                                                         .setName("test-divert-1");
      final DivertConfiguration divertConfig2 = new DivertConfiguration().setAddress(restrictedAddress)
                                                                         .setForwardingAddress(fowardRestricted)
                                                                         .setName("test-divert-2");

      server.deployDivert(divertConfig1);
      server.deployDivert(divertConfig2);

      // The addresses need to exist before divert tracking engages in the address policy manager.
      server.addAddressInfo(new AddressInfo(SimpleString.of(allowedAddress), RoutingType.MULTICAST));
      server.addAddressInfo(new AddressInfo(SimpleString.of(restrictedAddress), RoutingType.MULTICAST));

      final Set<String> includes = new HashSet<>();
      includes.add(allowedAddress);
      includes.add(restrictedAddress);

      final Map<String, Object> properties = new HashMap<>();
      properties.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, 1);

      final FederationReceiveFromAddressPolicy policy =
         new FederationReceiveFromAddressPolicy("test-address-policy",
                                                true, 30_000L, 1000L, 1, true,
                                                includes, null, properties, null,
                                                DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, federationUser, federationPass, "test", true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendAddresPolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withSource().withAddress(startsWith(allowedAddress))
                            .and()
                            .respondInKind(); // Server detected demand based on policy we sent it
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Demand on the unrestricted address should generate federation links and move the message
         try (Connection connection = factory.createConnection(demandUser, demandPass)) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(fowardAllowed));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Demand on the restricted address should result in no federation links being attempted.
         try (Connection connection = factory.createConnection(demandUser, demandPass)) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(fowardRestricted));

            connection.start();

            final Message message = consumer.receiveNoWait();
            assertNull(message);
         }

         // This would fail if the server tries creating a federation link since we would
         // receive unexpected attach frames for the remote federation receiver.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerFederationReceiverRejectedWhenSecurityRestricted() throws Exception {
      final String allowedAddress = getTestName();
      final String restrictedAddress = getTestName() + "restricted"; // Federation user cannot receive here.

      configureSecurity(server, allowedAddress, restrictedAddress);
      server.start();
      server.createQueue(QueueConfiguration.of(allowedAddress).setRoutingType(RoutingType.MULTICAST)
                                                              .setAddress(allowedAddress)
                                                              .setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(restrictedAddress).setRoutingType(RoutingType.MULTICAST)
                                                                 .setAddress(restrictedAddress)
                                                                 .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, federationUser, federationPass, "test", true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName(allowedAddress)
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withSource().withAddress(allowedAddress);

         // Connect to remote as if an address had demand and matched our federation policy
         // This uses the allowed address so it should attach without issue.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allowedAddress)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress(allowedAddress)
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName(restrictedAddress)
                                       .withOfferedCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                                       .withNullSource();
         peer.expectDetach().withError(AmqpError.UNAUTHORIZED_ACCESS.toString()).respond();

         // Connect to remote as if an address had demand and matched our federation policy
         // This uses the restricted address so it should fail to attach.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(restrictedAddress)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress(restrictedAddress)
                                         .withCapabilities("topic")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testWildcardSubscriptionRoutedToBindingWhenFederatedFromRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respondInKind()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final String wildcardAddressA = getTestName() + ".A.#";
         final String wildcardAddressB = getTestName() + ".B.#";

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(wildcardAddressA);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withName(allOf(containsString(wildcardAddressA),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withSource().withAddress(not(containsString("filterId"))).also()
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            // Both should have the message routed to them by a single federation receiver link to the remote
            final MessageConsumer consumerA1 = session.createConsumer(session.createTopic(wildcardAddressA));
            final MessageConsumer consumerA2 = session.createConsumer(session.createTopic(wildcardAddressA));
            // Should not match and should not create any federation links
            final MessageConsumer consumerB = session.createConsumer(session.createTopic(wildcardAddressB));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDisposition().withSettled(true).withState().accepted();
            peer.remoteTransfer().withMessage()
                                 .withBody().withString("test-message").also()
                                 .withDeliveryId(0)
                                 .later(10);

            final Message messageA1 = consumerA1.receive(5_000);
            assertNotNull(messageA1);
            assertInstanceOf(TextMessage.class, messageA1);
            assertEquals("test-message", ((TextMessage) messageA1).getText());

            final Message messageA2 = consumerA2.receive(5_000);
            assertNotNull(messageA2);
            assertInstanceOf(TextMessage.class, messageA2);
            assertEquals("test-message", ((TextMessage) messageA2).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach(); // demand will be gone and receiver link should close.

            assertNull(consumerB.receiveNoWait());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testWildcardSubscriptionWithFiltersRoutedToBindingsWhenFederatedFromRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respondInKind()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final String wildcardAddressA = getTestName() + ".A.#";
         final String wildcardAddressB = getTestName() + ".B.#";
         final String expectedJMSFilter = "color='red'";
         final AtomicReference<Attach> capturedAttach = new AtomicReference<>();
         final Symbol jmsSelectorKey = Symbol.valueOf("jms-selector");
         final org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong jmsSelectorCode =
            org.apache.qpid.protonj2.test.driver.codec.primitives.UnsignedLong.valueOf(0x0000468C00000004L);

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement receiveFromAddress = new AMQPFederationAddressPolicyElement();
         receiveFromAddress.setName("address-policy");
         receiveFromAddress.addToIncludes(wildcardAddressA);
         receiveFromAddress.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);
         receiveFromAddress.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, "false");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalAddressPolicy(receiveFromAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                            .withCapture(attach -> capturedAttach.set(attach))
                            .withName(allOf(containsString(wildcardAddressA),
                                            containsString("address-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withSource().withAddress(containsString("filterId")).also()
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            // Both should have the message routed to them by a single federation receiver link to the remote
            // that link should have a filter assigned to it so the remote only sends matches and nothing else.
            final MessageConsumer consumerA1 = session.createConsumer(session.createTopic(wildcardAddressA), "color='red'");
            final MessageConsumer consumerA2 = session.createConsumer(session.createTopic(wildcardAddressA), "color='red'");
            // Should not match and should not create any federation links
            final MessageConsumer consumerB = session.createConsumer(session.createTopic(wildcardAddressB));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDisposition().withSettled(true).withState().accepted();
            peer.remoteTransfer().withMessage()
                                 .withBody().withString("test-message").also()
                                 .withApplicationProperties().withProperty("color", "red").also()
                                 .withDeliveryId(0)
                                 .later(10);

            final Message messageA1 = consumerA1.receive(5_000);
            assertNotNull(messageA1);
            assertInstanceOf(TextMessage.class, messageA1);
            assertEquals("test-message", ((TextMessage) messageA1).getText());

            final Message messageA2 = consumerA2.receive(5_000);
            assertNotNull(messageA2);
            assertInstanceOf(TextMessage.class, messageA2);
            assertEquals("test-message", ((TextMessage) messageA2).getText());

            final Map<Symbol, Object> filtersMap1 = capturedAttach.get().getSource().getFilter();

            assertNotNull(filtersMap1);
            assertTrue(filtersMap1.containsKey(jmsSelectorKey));
            final DescribedType jmsSelectorEntry = (DescribedType) filtersMap1.get(jmsSelectorKey);
            assertNotNull(jmsSelectorEntry);
            assertEquals(jmsSelectorEntry.getDescriptor(), jmsSelectorCode);
            assertEquals(jmsSelectorEntry.getDescribed().toString(), expectedJMSFilter);

            // Attach another consumer on the same wild card address but with a different filter and we should
            // see a new receiver link opened as that binding needs its own receiver.
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_ADDRESS_RECEIVER.toString())
                               .withCapture(attach -> capturedAttach.set(attach))
                               .withName(allOf(containsString(wildcardAddressA),
                                               containsString("address-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .withSource().withAddress(containsString("filterId")).also()
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000);

            final MessageConsumer consumerA3 = session.createConsumer(session.createTopic(wildcardAddressA), "color='blue'");

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            assertNull(consumerA3.receiveNoWait());  // Should be nothing to route here.

            // Consumer A3 close should trigger federation link close with no messages read.
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach();

            consumerA3.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Now the other two consumers will close and the link should be torn down.
            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach();

            assertNull(consumerB.receive(100));
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   protected void configureSecurity(ActiveMQServer server, String allowed, String restricted, String... userAllowedOnly) {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      // User additions
      securityManager.getConfiguration().addUser(federationUser, federationPass);
      securityManager.getConfiguration().addRole(federationUser, "federate");

      securityManager.getConfiguration().addUser(demandUser, demandPass);
      securityManager.getConfiguration().addRole(demandUser, "user");

      // Configure roles
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> allowedRoles = new HashSet<>();
      allowedRoles.add(new Role("federate", true, true, false, false, false, false, true, true, false, false, false, false));
      allowedRoles.add(new Role("user", true, true, true, true, true, false, true, true, true, false, false, false));

      Set<Role> restrictedRoles = new HashSet<>();
      restrictedRoles.add(new Role("federate", false, false, false, false, false, false, true, true, false, false, false, false));
      restrictedRoles.add(new Role("user", true, true, true, true, true, false, true, true, true, false, false, false));

      // Only user can access the given security matches
      for (String securityMatch : userAllowedOnly) {
         securityRepository.addMatch(securityMatch, restrictedRoles);
      }

      securityRepository.addMatch(restricted, restrictedRoles);
      securityRepository.addMatch(allowed, allowedRoles);
      securityRepository.addMatch(FEDERATION_BASE_VALIDATION_ADDRESS, allowedRoles);

      server.getConfiguration().setSecurityEnabled(true);
   }

   private static void sendAddressAddedEvent(ProtonTestPeer peer, String address, int handle, int deliveryId) {
      final Map<String, Object> eventMap = new LinkedHashMap<>();
      eventMap.put(REQUESTED_ADDRESS_NAME, address);

      // Should not trigger attach of federation receiver as there already is one on this address
      peer.remoteTransfer().withHandle(handle)
                           .withDeliveryId(deliveryId)
                           .withSettled(true)
                           .withMessageAnnotations().withAnnotation(EVENT_TYPE.toString(), REQUESTED_ADDRESS_ADDED)
                           .also()
                           .withBody().withValue(eventMap)
                           .also()
                           .now();
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

   private static void sendAddresPolicyToRemote(ProtonTestClient peer, FederationReceiveFromAddressPolicy policy) {
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      policyMap.put(POLICY_NAME, policy.getPolicyName());
      policyMap.put(ADDRESS_AUTO_DELETE, policy.isAutoDelete());
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, policy.getAutoDeleteDelay());
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, policy.getAutoDeleteMessageCount());
      policyMap.put(ADDRESS_MAX_HOPS, policy.getMaxHops());
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, policy.isEnableDivertBindings());

      if (!policy.getIncludes().isEmpty()) {
         policyMap.put(ADDRESS_INCLUDES, new ArrayList<>(policy.getIncludes()));
      }
      if (!policy.getExcludes().isEmpty()) {
         policyMap.put(ADDRESS_EXCLUDES, new ArrayList<>(policy.getExcludes()));
      }

      final TransformerConfiguration transformerConfig = policy.getTransformerConfiguration();

      if (transformerConfig != null) {
         policyMap.put(TRANSFORMER_CLASS_NAME, transformerConfig.getClassName());
         if (transformerConfig.getProperties() != null && !transformerConfig.getProperties().isEmpty()) {
            policyMap.put(TRANSFORMER_PROPERTIES_MAP, transformerConfig.getProperties());
         }
      }

      if (!policy.getProperties().isEmpty()) {
         policyMap.put(POLICY_PROPERTIES_MAP, policy.getProperties());
      }

      peer.remoteTransfer().withDeliveryId(0)
                           .withMessageAnnotations().withAnnotation(OPERATION_TYPE.toString(), ADD_ADDRESS_POLICY)
                           .also()
                           .withBody().withValue(policyMap)
                           .also()
                           .now();
   }

   // Use this method to script the initial handshake that a broker that is establishing
   // a federation connection with a remote broker instance would perform.
   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName) {
      scriptFederationConnectToRemote(peer, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT);
   }

   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, boolean fqqnSubscriptions) {
      scriptFederationConnectToRemote(peer, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT, false, false, fqqnSubscriptions);
   }

   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, int amqpCredits, int amqpLowCredits) {
      scriptFederationConnectToRemote(peer, federationName, amqpCredits, amqpLowCredits, false, false, false);
   }

   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, boolean eventsSender, boolean eventsReceiver) {
      scriptFederationConnectToRemote(peer, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT, eventsSender, eventsReceiver, false);
   }

   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, int amqpCredits, int amqpLowCredits, boolean eventsSender, boolean eventsReceiver, boolean fqqnAddressSubs) {
      scriptFederationConnectToRemote(peer, null, null, federationName, amqpCredits, amqpLowCredits, eventsSender, eventsReceiver, fqqnAddressSubs);
   }

   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String user, String password, String federationName, boolean fqqnAddressSubs) {
      scriptFederationConnectToRemote(peer, user, password, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT, false, false, fqqnAddressSubs);
   }

   private static void scriptFederationConnectToRemote(ProtonTestClient peer, String user, String password, String federationName, int amqpCredits, int amqpLowCredits, boolean eventsSender, boolean eventsReceiver, boolean fqqnAddressSubs) {
      final String federationControlLinkName = "Federation:control:" + UUID.randomUUID().toString();

      final Map<String, Object> federationConfiguration = new HashMap<>();
      federationConfiguration.put(RECEIVER_CREDITS, amqpCredits);
      federationConfiguration.put(RECEIVER_CREDITS_LOW, amqpLowCredits);

      final Map<String, Object> senderProperties = new HashMap<>();
      senderProperties.put(FEDERATION_CONFIGURATION.toString(), federationConfiguration);
      senderProperties.put(FEDERATION_VERSION.toString(), fqqnAddressSubs ? FEDERATION_V2 : FEDERATION_V1);
      senderProperties.put(FEDERATION_NAME.toString(), federationName);

      if (user == null && password == null) {
         peer.queueClientSaslAnonymousConnect();
      } else {
         peer.queueClientSaslPlainConnect(user, password);
      }

      peer.remoteOpen().queue();
      peer.expectOpen();
      peer.remoteBegin().queue();
      peer.expectBegin();
      peer.remoteAttach().ofSender()
                         .withInitialDeliveryCount(0)
                         .withName(federationControlLinkName)
                         .withPropertiesMap(senderProperties)
                         .withDesiredCapabilities(FEDERATION_CONTROL_LINK.toString())
                         .withSenderSettleModeUnsettled()
                         .withReceivervSettlesFirst()
                         .withSource().also()
                         .withTarget().withDynamic(true)
                                      .withDurabilityOfNone()
                                      .withExpiryPolicyOnLinkDetach()
                                      .withLifetimePolicyOfDeleteOnClose()
                                      .withCapabilities("temporary-topic")
                                      .also()
                         .queue();
      peer.expectAttach().ofReceiver()
                         .withTarget()
                            .withAddress(notNullValue())
                         .also()
                         .withOfferedCapability(FEDERATION_CONTROL_LINK.toString());
      peer.expectFlow();

      // Sender created when there are remote policies to send to the target
      if (eventsSender) {
         final String federationEventsSenderLinkName = "Federation:events-sender:test:" + UUID.randomUUID().toString();

         peer.remoteAttach().ofSender()
                            .withName(federationEventsSenderLinkName)
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withSenderSettleModeSettled()
                            .withReceivervSettlesFirst()
                            .withSource().also()
                            .withTarget().withDynamic(true)
                                         .withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withLifetimePolicyOfDeleteOnClose()
                                         .withCapabilities("temporary-topic")
                                         .also()
                                         .queue();
         peer.expectAttach().ofReceiver()
                            .withName(federationEventsSenderLinkName)
                            .withTarget()
                               .withAddress(notNullValue())
                            .also()
                            .withOfferedCapability(FEDERATION_EVENT_LINK.toString());
         peer.expectFlow();
      }

      // Receiver created when there are local policies on the source.
      if (eventsReceiver) {
         final String federationEventsSenderLinkName = "Federation:events-receiver:test:" + UUID.randomUUID().toString();

         peer.remoteAttach().ofReceiver()
                            .withName(federationEventsSenderLinkName)
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withSenderSettleModeSettled()
                            .withReceivervSettlesFirst()
                            .withTarget().also()
                            .withSource().withDynamic(true)
                                         .withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withLifetimePolicyOfDeleteOnClose()
                                         .withCapabilities("temporary-topic")
                                         .also()
                                         .queue();
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectAttach().ofSender()
                            .withName(federationEventsSenderLinkName)
                            .withSource()
                               .withAddress(notNullValue())
                            .also()
                            .withOfferedCapability(FEDERATION_EVENT_LINK.toString());
      }
   }

   private class AMQPTestFederationBrokerPlugin implements ActiveMQServerAMQPFederationPlugin {

      public final AtomicBoolean started = new AtomicBoolean();
      public final AtomicBoolean stopped = new AtomicBoolean();

      public final AtomicReference<FederationConsumerInfo> beforeCreateConsumerCapture = new AtomicReference<>();
      public final AtomicReference<FederationConsumer> afterCreateConsumerCapture = new AtomicReference<>();
      public final AtomicReference<FederationConsumer> beforeCloseConsumerCapture = new AtomicReference<>();
      public final AtomicReference<FederationConsumer> afterCloseConsumerCapture = new AtomicReference<>();

      public Consumer<FederationConsumerInfo> beforeCreateConsumer = (c) -> beforeCreateConsumerCapture.set(c);;
      public Consumer<FederationConsumer> afterCreateConsumer = (c) -> afterCreateConsumerCapture.set(c);
      public Consumer<FederationConsumer> beforeCloseConsumer = (c) -> beforeCloseConsumerCapture.set(c);
      public Consumer<FederationConsumer> afterCloseConsumer = (c) -> afterCloseConsumerCapture.set(c);

      public BiConsumer<FederationConsumer, org.apache.activemq.artemis.api.core.Message> beforeMessageHandled = (c, m) -> { };
      public BiConsumer<FederationConsumer, org.apache.activemq.artemis.api.core.Message> afterMessageHandled = (c, m) -> { };

      public Function<AddressInfo, Boolean> shouldCreateConsumerForAddress = (a) -> true;
      public Function<org.apache.activemq.artemis.core.server.Queue, Boolean> shouldCreateConsumerForQueue = (q) -> true;
      public BiFunction<Divert, org.apache.activemq.artemis.core.server.Queue, Boolean> shouldCreateConsumerForDivert = (d, q) -> true;

      @Override
      public void federationStarted(final Federation federation) throws ActiveMQException {
         started.set(true);
      }

      @Override
      public void federationStopped(final Federation federation) throws ActiveMQException {
         stopped.set(true);
      }

      @Override
      public void beforeCreateFederationConsumer(final FederationConsumerInfo consumerInfo) throws ActiveMQException {
         beforeCreateConsumer.accept(consumerInfo);
      }

      @Override
      public void afterCreateFederationConsumer(final FederationConsumer consumer) throws ActiveMQException {
         afterCreateConsumer.accept(consumer);
      }

      @Override
      public void beforeCloseFederationConsumer(final FederationConsumer consumer) throws ActiveMQException {
         beforeCloseConsumer.accept(consumer);
      }

      @Override
      public void afterCloseFederationConsumer(final FederationConsumer consumer) throws ActiveMQException {
         afterCloseConsumer.accept(consumer);
      }

      @Override
      public void beforeFederationConsumerMessageHandled(final FederationConsumer consumer, org.apache.activemq.artemis.api.core.Message message) throws ActiveMQException {
         beforeMessageHandled.accept(consumer, message);
      }

      @Override
      public void afterFederationConsumerMessageHandled(final FederationConsumer consumer, org.apache.activemq.artemis.api.core.Message message) throws ActiveMQException {
         afterMessageHandled.accept(consumer, message);
      }

      @Override
      public boolean shouldCreateFederationConsumerForAddress(final AddressInfo address) throws ActiveMQException {
         return shouldCreateConsumerForAddress.apply(address);
      }

      @Override
      public boolean shouldCreateFederationConsumerForQueue(final org.apache.activemq.artemis.core.server.Queue queue) throws ActiveMQException {
         return shouldCreateConsumerForQueue.apply(queue);
      }

      @Override
      public boolean shouldCreateFederationConsumerForDivert(Divert divert, org.apache.activemq.artemis.core.server.Queue queue) throws ActiveMQException {
         return shouldCreateConsumerForDivert.apply(divert, queue);
      }
   }
}