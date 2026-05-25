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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Divert;
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
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
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
import org.apache.qpid.protonj2.test.driver.matchers.messaging.ApplicationPropertiesMatcher;
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

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConfiguration.DEFAULT_PULL_CREDIT_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_QUEUE_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.EVENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_BASE_VALIDATION_ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_POLICY_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_QUEUE_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V2;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_VERSION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_PRIORITIES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.LARGE_MESSAGE_THRESHOLD;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.OPERATION_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_PROPERTIES_MAP;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.PULL_RECEIVER_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_EXCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_INCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_INCLUDE_FEDERATED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_PRIORITY_ADJUSTMENT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS_LOW;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_LINK_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_QUIESCE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_QUEUE_ADDED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_QUEUE_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.TRANSFORMER_CLASS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.TRANSFORMER_PROPERTIES_MAP;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for AMQP Broker federation handling of the receive from and send to queue policy configuration handling.
 */
public class AMQPFederationQueuePolicyTest extends AmqpClientTestSupport {

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
   public void testFederationCreatesQueueReceiverLinkForQueueMatchAnycast() throws Exception {
      doTestFederationCreatesQueueReceiverLinkForQueueMatch(RoutingType.ANYCAST);
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkForQueueMatchMulticast() throws Exception {
      doTestFederationCreatesQueueReceiverLinkForQueueMatch(RoutingType.MULTICAST);
   }

   private void doTestFederationCreatesQueueReceiverLinkForQueueMatch(RoutingType routingType) throws Exception {
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
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(routingType)
                                                         .setAddress("test")
                                                         .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "queue-policy")
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            if (RoutingType.MULTICAST.equals(routingType)) {
               // Requires FQQN to meet the test expectations
               session.createConsumer(session.createTopic("test::test"));
            } else {
               session.createConsumer(session.createQueue("test"));
            }

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationQueueReceiverCarriesConfiguredQueueFilter() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setFilterString("color='red'")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSource().withJMSSelector("color='red'").and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationQueueReceiverCarriesConsumerQueueFilter() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setFilterString("color='red' OR color = 'blue'")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSource().withJMSSelector("color='red'").and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("test");

            session.createConsumer(queue, "color='red'"); // new receiver for this selector

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("test"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                               .withSource().withJMSSelector("color='blue'").and()
                               .respond()
                               .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(queue, "color='blue'"); // new receiver for this selector

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("test"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                               .withSource().withJMSSelector("color='red' OR color = 'blue'").and()
                               .respond()
                               .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(queue); // No selector so the queue filter should be used

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Should not see more attaches as these match previous variations
            session.createConsumer(queue, "color='blue'");
            session.createConsumer(queue, "color='red'");
            session.createConsumer(queue);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationQueueReceiverCanIgnoreConsumerQueueFilter() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");
         receiveFromQueue.addProperty(IGNORE_QUEUE_CONSUMER_FILTERS, "true");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setFilterString("color='red' OR color = 'blue'")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSource().withJMSSelector("color='red' OR color = 'blue'").and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("test");

            session.createConsumer(queue, "color='red'"); // Consumer filter should be ignored

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            session.createConsumer(queue, "color='blue'"); // Consumer filter should be ignored

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkForQueueMatchUsingPolicyCredit() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");
         receiveFromQueue.addProperty(RECEIVER_CREDITS, "30");
         receiveFromQueue.addProperty(RECEIVER_CREDITS_LOW, "3");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSource().and()
                            .withTarget().and()
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(30);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationClosesQueueReceiverWhenDemandIsRemovedFromQueue() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

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
   public void testFederationHandlesQueueDeletedAndConsumerRecreates() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDetach().respond();

            server.destroyQueue(SimpleString.of("test"), null, false, true);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         // Consumer recreates Queue and adds demand back and federation should restart
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

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
   public void testSecondQueueConsumerDoesNotGenerateAdditionalFederationReceiver() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));
            session.createConsumer(session.createQueue("test"));

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
   public void testLinkCreatedForEachDistinctQueueMatchInSameConfiguredPolicy() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("addr1", "test.1");
         receiveFromQueue.addToIncludes("addr2", "test.2");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.1").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("addr1")
                                                            .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("test.2").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("addr2")
                                                            .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("addr1::test.1"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.1"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("addr2::test.2"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000).optional();

            session.createConsumer(session.createQueue("test.2"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectDetach().respond();
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationReceiverCreatedWhenWildcardPolicyMatchesConsumerQueue() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("", "test.#");
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.queue").setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.queue"));

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
   public void testRemoteCloseOfQueueReceiverRespondsToDetach() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("", "test.#");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.queue").setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.queue"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDetach();
            peer.remoteDetach().withErrorCondition("amqp:resource-deleted", "the resource was deleted").afterDelay(10).now();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRejectedQueueReceiverAttachWhenLocalMatchingQueueNotFoundIsHandled() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("", "test.#");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.queue").setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind()
                            .withNullSource();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested queue was not found").queue().afterDelay(10);
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.queue"));

            connection.start();

            // Broker normally treats any remote link closure on the broker connection as terminal
            // but shouldn't in this case as it indicates the requested federated queue wasn't present
            // on the remote. New queue interest should result in a new attempt to federate the queue
            // and this time we will let it succeed.

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("test::test.queue"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createQueue("test.queue"));

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
   public void testRemoteCloseQueueReceiverWhenRemoteResourceIsDeletedIsHandled() throws Exception {
      doTestRemoteCloseQueueReceiverForExpectedConditionsIsHandled("amqp:resource-deleted");
   }

   @Test
   @Timeout(20)
   public void testRemoteCloseQueueReceiverWhenRemoteReceiverIsForcedToDetachIsHandled() throws Exception {
      doTestRemoteCloseQueueReceiverForExpectedConditionsIsHandled("amqp:link:detach-forced");
   }

   private void doTestRemoteCloseQueueReceiverForExpectedConditionsIsHandled(String condition) throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("", "test.#");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.queue").setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test.queue"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteDetach().withErrorCondition(condition, "error message from remote....").queue().afterDelay(20);
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.queue"));

            connection.start();

            // Broker normally treats any remote link closure on the broker connection as terminal
            // but shouldn't in this case as it indicates the requested federated queue receiver was
            // forced closed. New queue interest should result in a new attempt to federate the queue

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("test::test.queue"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createQueue("test.queue"));

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
   public void testUnhandledRemoteReceiverCloseConditionCausesConnectionRebuild() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("", "test.#");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1); // One reconnect to meet test expectations and use a
         amqpConnection.setRetryInterval(100);   // Short reconnect interval.
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.queue").setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test.queue"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.queue"));

            connection.start();

            // Broker treats some remote link closures on the broker connection as terminal
            // in this case we signal some internal error which should cause rebuild of the
            // broker connection.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.expectDetach().optional(); // Broker is not consistent on sending the detach
            peer.expectClose().optional();
            peer.expectConnectionToDrop();
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
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("test::test.queue"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000);

            // Trigger the error that should cause the broker to drop and reconnect
            peer.remoteDetach().withErrorCondition("amqp:internal-error", "the resource suffered an internal error").afterDelay(15).now();

            peer.waitForScriptToComplete(50, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
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
   public void testInboundMessageRoutedToReceiverOnLocalQueue() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("", "test.#");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.queue").setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test.queue"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withSource().withAddress("test::test.queue")
                            .and()
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
            final MessageConsumer consumer = session.createConsumer(session.createQueue("test.queue"));

            connection.start();

            final Message message = consumer.receive(20_0000);
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

   // TODO: Decide how to handle priority on queue consumers and then maybe port these tests to the bridge

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkWithDefaultPrioirty() throws Exception {
      doTestFederationCreatesQueueReceiverLinkWithAdjustedPriority(0);
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkWithIncreasedPriority() throws Exception {
      doTestFederationCreatesQueueReceiverLinkWithAdjustedPriority(5);
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkWithDecreasedPriority() throws Exception {
      doTestFederationCreatesQueueReceiverLinkWithAdjustedPriority(-5);
   }

   private void doTestFederationCreatesQueueReceiverLinkWithAdjustedPriority(int adjustment) throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.setPriorityAdjustment(adjustment);
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(),
                                          ActiveMQDefaultConfiguration.getDefaultConsumerPriority() + adjustment)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkConsumerPriorityOffset() throws Exception {
      doTestFederationCreatesQueueReceiverWithCorrectPriorityOffset(false);
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkIngoringConsumerPriorityOffset() throws Exception {
      doTestFederationCreatesQueueReceiverWithCorrectPriorityOffset(true);
   }

   private void doTestFederationCreatesQueueReceiverWithCorrectPriorityOffset(boolean ignoreConsumerPriority) throws Exception {
      final int TEST_BASE_PRIORITY_ADJUSTMENT = -2;
      final int TEST_CONSUMER_PRIORITY = 2;

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.setPriorityAdjustment(TEST_BASE_PRIORITY_ADJUSTMENT);
         receiveFromQueue.addToIncludes("test", "test");
         receiveFromQueue.addProperty(IGNORE_QUEUE_CONSUMER_PRIORITIES, Boolean.valueOf(ignoreConsumerPriority).toString());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         final int expectedPriority =
            ignoreConsumerPriority ? ActiveMQDefaultConfiguration.getDefaultConsumerPriority() + TEST_BASE_PRIORITY_ADJUSTMENT :
                                     TEST_CONSUMER_PRIORITY + TEST_BASE_PRIORITY_ADJUSTMENT;

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), expectedPriority)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test?consumer-priority=" + TEST_CONSUMER_PRIORITY));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   // TODO: See above

   @Test
   @Timeout(20)
   public void testLinkCreatedForEachDistinctQueueMatchInSameConfiguredPolicyWithSameAddressMatch() throws Exception {
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

         // Ensures the code doesn't lose track when the address value is the same across includes.
         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("addr", "test.1");
         receiveFromQueue.addToIncludes("addr", "test.2");
         receiveFromQueue.addToIncludes("addr", "test.3");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test.1").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("addr")
                                                            .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("test.2").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("addr")
                                                            .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("test.3").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("addr")
                                                            .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("addr::test.1"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000).optional();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test.1"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("addr::test.2"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000).optional();

            session.createConsumer(session.createQueue("test.2"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("addr::test.3"),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000).optional();

            session.createConsumer(session.createQueue("test.3"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectDetach().respond();
            peer.expectDetach().respond();
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerAcceptsQueuePolicyFromControlLink() throws Exception {
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", "testQueue"));
      final Collection<Map.Entry<String, String>> excludes = new ArrayList<>();
      excludes.add(new AbstractMap.SimpleEntry<>("address1", "test.#"));

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, excludes, null, null,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

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
   public void testRemoteBrokerAcceptsQueuePolicyFromControlLinkWithTransformerConfiguration() throws Exception {
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", "testQueue"));
      final Collection<Map.Entry<String, String>> excludes = new ArrayList<>();
      excludes.add(new AbstractMap.SimpleEntry<>("address1", "test.#"));

      final Map<String, String> transformerProperties = new HashMap<>();
      transformerProperties.put("key1", "value1");
      transformerProperties.put("key2", "value2");

      final TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
      transformerConfiguration.setClassName(ApplicationPropertiesTransformer.class.getName());
      transformerConfiguration.setProperties(transformerProperties);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, excludes,
                                              null, transformerConfiguration,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

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
   public void testRemoteFederatesQueueWhenDemandIsApplied() throws Exception {
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", "testQueue"));

      final Map<String, Object> properties = new HashMap<>();
      properties.put(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, null, properties, null,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withSource().withAddress("testQueue::testQueue")
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
            final MessageConsumer consumer = session.createConsumer(session.createQueue("testQueue"));

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
   public void testRemoteFederatesQueueWhenDemandIsAppliedUsingControllerDefinedLinkCredit() throws Exception {
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", "testQueue"));

      final Map<String, Object> properties = new HashMap<>();
      properties.put(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, null, properties, null,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", 10, 9);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withSource().withAddress("testQueue::testQueue")
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
            final MessageConsumer consumer = session.createConsumer(session.createQueue("testQueue"));

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
   public void testRemoteFederatesQueueWhenDemandIsAppliedUsingPolicyDefinedLinkCredit() throws Exception {
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", "testQueue"));

      final Map<String, Object> properties = new HashMap<>();
      properties.put(RECEIVER_CREDITS, "40");
      properties.put(RECEIVER_CREDITS_LOW, 39);
      properties.put(LARGE_MESSAGE_THRESHOLD, 2048);
      properties.put(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, null, properties, null,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", 10, 9);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withSource().withAddress("testQueue::testQueue")
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
            final MessageConsumer consumer = session.createConsumer(session.createQueue("testQueue"));

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
   public void testRemoteFederatesQueueAndAppliesTransformerWhenDemandIsApplied() throws Exception {
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", "testQueue"));

      final Map<String, Object> properties = new HashMap<>();
      properties.put(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

      final Map<String, String> transformerProperties = new HashMap<>();
      transformerProperties.put("key1", "value1");
      transformerProperties.put("key2", "value2");

      final TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
      transformerConfiguration.setClassName(ApplicationPropertiesTransformer.class.getName());
      transformerConfiguration.setProperties(transformerProperties);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, null,
                                              properties, transformerConfiguration,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withSource().withAddress("testQueue::testQueue")
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
            final MessageConsumer consumer = session.createConsumer(session.createQueue("testQueue"));

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
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);

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
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
   public void testRemoteBrokerRoutesInboundMessageToFederatedReceiver() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(1).now();
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
            final MessageProducer producer = session.createProducer(session.createQueue("test"));

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
   public void testRemoteBrokerCanFailLinkAttachIfQueueDoesNotExistWithoutClosingTheConnection() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test").withSource(nullValue());
         peer.expectDetach().withError("amqp:not-found", "Queue: 'test' does not exist").respond();

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress("test::test");

         // Now create it and try again
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         // Connect to remote as if an queue had demand and matched our federation policy
         // this is our second attempt and the remote queue exists now so it should succeed.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
   public void testRemoteBrokerCanFailLinkAttachIfQueueDoesNotMatchFullExpectationWithoutClosingTheConnection() throws Exception {
      server.start();

      // Now create it and try again
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test").withSource(nullValue());
         peer.expectDetach().withError("amqp:not-found", "Queue: 'test' is not mapped to specified address: address").respond();

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("address::test")
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         // this is our second attempt and the remote queue exists now so it should succeed.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
   public void testRemoteConnectionCannotAttachQueueFederationLinkWithoutControlLink() throws Exception {
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
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withSource(nullValue())
                                       .withTarget();
         peer.expectDetach().respond();

         // Attempt to create a federation queue receiver link without existing control link
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
   public void testRemoteBrokerRoutesInboundMessageToFederatedReceiverWithFilterApplied() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withJMSSelector("color = 'red'")
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Match typical generic Qpid JMS Text Message structure
         final HeaderMatcher headerMatcher = new HeaderMatcher(true);
         final MessageAnnotationsMatcher annotationsMatcher = new MessageAnnotationsMatcher(true);
         final ApplicationPropertiesMatcher appPropertiesMatcher = new ApplicationPropertiesMatcher(true);
         appPropertiesMatcher.withEntry("color", equalTo("red"));
         final PropertiesMatcher properties = new PropertiesMatcher(true);
         final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher("red");
         final TransferPayloadCompositeMatcher matcher = new TransferPayloadCompositeMatcher();
         matcher.setHeadersMatcher(headerMatcher);
         matcher.setMessageAnnotationsMatcher(annotationsMatcher);
         matcher.setPropertiesMatcher(properties);
         matcher.setApplicationPropertiesMatcher(appPropertiesMatcher);
         matcher.addMessageContentMatcher(bodyMatcher);

         peer.expectTransfer().withPayload(matcher).accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue("test"));

            // Federation receiver should have applied filter that causes this message not to federate
            final Message blueMessage = session.createTextMessage("blue");
            blueMessage.setStringProperty("color", "blue");

            final Message redMessage = session.createTextMessage("red");
            redMessage.setStringProperty("color", "red");

            producer.send(blueMessage);
            producer.send(redMessage);

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
   public void testBrokerDoesNotFederateQueueIfOnlyDemandIsFromAnotherBrokerFederationSubscription() throws Exception {
      try (ProtonTestServer target = new ProtonTestServer()) {
         target.expectSASLAnonymousConnect();
         target.expectOpen().respond();
         target.expectBegin().respond();
         target.expectAttach().ofSender()
                              .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                              .respond()
                              .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         target.expectAttach().ofReceiver()
                              .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                              .respondInKind();
         target.expectFlow().withLinkCredit(10);
         target.start();

         final URI remoteURI = target.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");
         receiveFromQueue.setIncludeFederated(false); // No federation for federation subscriptions

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         target.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Simulate another broker connecting as a federation instance that will create demand on
         // queue "test::test" but should not generate demand to the server the broker connected
         // to as a federation control because we configured it to ignore federation subscriptions
         // when considering demand on the matching queues.
         try (ProtonTestClient client = new ProtonTestClient()) {
            scriptFederationConnectToRemote(client, "incoming-federation");
            client.connect("localhost", AMQP_PORT);

            client.waitForScriptToComplete(5, TimeUnit.SECONDS);
            client.expectAttach().ofSender().withName("fake-federation-incoming-receiver-link")
                                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                            .withSource().withAddress("test::test");

            // Connect to remote as if a queue had demand and matched our federation policy
            client.remoteAttach().ofReceiver()
                                 .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                 .withName("fake-federation-incoming-receiver-link")
                                 .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                                 .withSenderSettleModeUnsettled()
                                 .withReceivervSettlesFirst()
                                 .withSource().withDurabilityOfNone()
                                              .withExpiryPolicyOnLinkDetach()
                                              .withAddress("test::test")
                                              .withCapabilities("queue")
                                              .and()
                                 .withTarget().and()
                                 .now();
            client.remoteFlow().withLinkCredit(10).now();
            client.waitForScriptToComplete(5, TimeUnit.SECONDS);
            client.expectDetach();
            client.expectClose();

            client.remoteDetach().withErrorCondition("amqp:resource-deleted", "The resource was deleted").now();
            client.remoteClose().now();
            client.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         // Would fail if any frames arrived that are not scripted to.
         target.waitForScriptToComplete(5, TimeUnit.SECONDS);
         target.expectAttach().ofReceiver().withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                                           .withName(allOf(containsString(getTestName()),
                                                           containsString("test::test"),
                                                           containsString("queue-receiver"),
                                                           containsString(server.getNodeID().toString())))
                                           .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT).respond()
                                           .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         target.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Now create demand that isn't a federation consumer and the remote should see an incoming
         // receiver attach for the federated queue.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

            connection.start();

            target.waitForScriptToComplete(5, TimeUnit.SECONDS);
            target.expectClose();
            target.remoteClose().now();

            target.waitForScriptToComplete(5, TimeUnit.SECONDS);
            target.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerCanFederateQueueIfOnlyDemandIsFromAnotherBrokerFederationSubscription() throws Exception {
      try (ProtonTestServer target = new ProtonTestServer()) {
         target.expectSASLAnonymousConnect();
         target.expectOpen().respond();
         target.expectBegin().respond();
         target.expectAttach().ofSender()
                              .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                              .respond()
                              .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         target.expectAttach().ofReceiver()
                              .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                              .respondInKind();
         target.expectFlow().withLinkCredit(10);
         target.start();

         final URI remoteURI = target.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");
         receiveFromQueue.setIncludeFederated(true); // do federate for federation subscriptions

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 5);
         element.addProperty(IGNORE_QUEUE_CONSUMER_PRIORITIES, "false");

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         target.waitForScriptToComplete(5, TimeUnit.SECONDS);
         // broker should create a new receiver that extends the federation receiver to this "broker"
         // but because this is a federation of a federation the priority should remain unchanged from
         // what the source broker requested when creating the initial consumer.
         target.expectAttach().ofReceiver().withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                                           .withName(allOf(containsString(getTestName()),
                                                           containsString("test::test"),
                                                           containsString("queue-receiver"),
                                                           containsString(server.getNodeID().toString())))
                                           .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT).respond()
                                           .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         // Should get a flow but if the link goes away quick enough the broker won't get to this before detaching.
         target.expectFlow().withLinkCredit(1000).optional();

         // Simulate another broker connecting as a federation instance that will create demand on
         // queue "test::test" and should generate demand to the server the broker connected to
         // as a federation control because we configured to not ignore federation subscriptions
         // when considering demand on the matching queues.
         try (ProtonTestClient client = new ProtonTestClient()) {
            scriptFederationConnectToRemote(client, "incoming-federation");
            client.connect("localhost", AMQP_PORT);

            client.waitForScriptToComplete(5, TimeUnit.SECONDS);
            client.expectAttach().ofSender().withName("fake-federation-incoming-receiver-link")
                                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                            .withSource().withAddress("test::test");

            // Connect to remote as if a queue had demand and matched our federation policy
            client.remoteAttach().ofReceiver()
                                 .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                 .withName("fake-federation-incoming-receiver-link")
                                 .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                                 .withSenderSettleModeUnsettled()
                                 .withReceivervSettlesFirst()
                                 .withSource().withDurabilityOfNone()
                                              .withExpiryPolicyOnLinkDetach()
                                              .withAddress("test::test")
                                              .withCapabilities("queue")
                                              .and()
                                 .withTarget().and()
                                 .now();
            client.remoteFlow().withLinkCredit(10).now();
            client.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Now force close the federation link in an unexpected way which should cause the target to
            // react and close the federation connection and trigger a rebuild of state, federation links
            // should normally never be deleted by user management but if it is the state needs to be
            // rebuilt in order for proper demand tracking.
            target.waitForScriptToComplete(5, TimeUnit.SECONDS);
            target.expectFlow().withLinkCredit(1000).withDrain(true)
                               .respond()
                               .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            target.expectDetach().respond();

            client.expectDetach();
            client.expectClose().respond();
            client.remoteDetach().withErrorCondition("amqp:resource-deleted", "Resource deleted").later(30);
            client.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         target.waitForScriptToComplete(5, TimeUnit.SECONDS);
         target.close();
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.setTransformerConfiguration(transformerConfiguration);
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 5);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
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
            final MessageConsumer consumer = session.createConsumer(session.createQueue("test"));

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
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectDetach(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testPullQueueConsumerGrantsDefaultCreditOnEmptyQueue() throws Exception {
      doTestPullConsumerGrantsConfiguredCreditOnEmptyQueue(0, false, 0, false, DEFAULT_PULL_CREDIT_BATCH_SIZE);
   }

   @Test
   @Timeout(20)
   public void testPullQueueConsumerGrantsReceiverConfiguredCreditOnEmptyQueue() throws Exception {
      doTestPullConsumerGrantsConfiguredCreditOnEmptyQueue(0, false, 10, true, 10);
   }

   @Test
   @Timeout(20)
   public void testPullQueueConsumerGrantsFederationConfiguredCreditOnEmptyQueue() throws Exception {
      doTestPullConsumerGrantsConfiguredCreditOnEmptyQueue(20, true, 0, false, 20);
   }

   @Test
   @Timeout(20)
   public void testPullQueueConsumerGrantsReceiverConfiguredCreditOverFederationConfiguredOnEmptyQueue() throws Exception {
      doTestPullConsumerGrantsConfiguredCreditOnEmptyQueue(20, true, 10, true, 10);
   }

   private void doTestPullConsumerGrantsConfiguredCreditOnEmptyQueue(int globalBatch, boolean setGlobal,
                                                                     int receiverBatch, boolean setReceiver,
                                                                     int expected) throws Exception {

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");
         receiveFromQueue.addProperty(RECEIVER_CREDITS, 0);
         if (setReceiver) {
            receiveFromQueue.addProperty(PULL_RECEIVER_BATCH_SIZE, receiverBatch);
         }

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         if (setGlobal) {
            element.addProperty(PULL_RECEIVER_BATCH_SIZE, globalBatch);
         }

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(expected);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue("test"));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.close();
         }
      }
   }

   @Test
   @Timeout(30)
   public void testPullQueueConsumerGrantsCreditOnlyWhenPendingMessageIsConsumed() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(
               getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort() + "?amqpCredits=0");
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();

         // Must be pull consumer to ensure we can check that demand on remote doesn't
         // offer credit until the pending message count on the Queue is zeroed
         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "AMQP", "tcp://localhost:" + AMQP_PORT + "?jms.prefetchPolicy.all=0");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("test");
            final MessageProducer producer = session.createProducer(queue);

            // Add to backlog
            producer.send(session.createMessage());

            // Now create demand on the queue
            final MessageConsumer consumer = session.createConsumer(queue);

            connection.start();
            producer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(DEFAULT_PULL_CREDIT_BATCH_SIZE);

            // Remove the backlog and credit should be offered to the remote
            assertNotNull(consumer.receiveNoWait());

            peer.waitForScriptToComplete(20, TimeUnit.SECONDS);

            peer.close();
         }
      }
   }

   @Test
   @Timeout(30)
   public void testPullQueueConsumerBatchCreditTopUpAfterEachBacklogDrain() throws Exception {
      doTestPullConsumerCreditTopUpAfterEachBacklogDrain(0, false, 0, false, DEFAULT_PULL_CREDIT_BATCH_SIZE);
   }

   @Test
   @Timeout(30)
   public void testPullQueueConsumerBatchCreditTopUpAfterEachBacklogDrainFederationConfigured() throws Exception {
      doTestPullConsumerCreditTopUpAfterEachBacklogDrain(10, true, 0, false, 10);
   }

   @Test
   @Timeout(30)
   public void testPullQueueConsumerBatchCreditTopUpAfterEachBacklogDrainPolicyConfigured() throws Exception {
      doTestPullConsumerCreditTopUpAfterEachBacklogDrain(0, false, 20, true, 20);
   }

   @Test
   @Timeout(30)
   public void testPullQueueConsumerBatchCreditTopUpAfterEachBacklogDrainBothConfigured() throws Exception {
      doTestPullConsumerCreditTopUpAfterEachBacklogDrain(100, true, 20, true, 20);
   }

   private void doTestPullConsumerCreditTopUpAfterEachBacklogDrain(int globalBatch, boolean setGlobal,
                                                                   int receiverBatch, boolean setReceiver,
                                                                   int expected) throws Exception {
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

         final SimpleString queueName = SimpleString.of("test");

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");
         if (setReceiver) {
            receiveFromQueue.addProperty(PULL_RECEIVER_BATCH_SIZE, receiverBatch);
         }

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);
         if (setGlobal) {
            element.addProperty(PULL_RECEIVER_BATCH_SIZE, globalBatch);
         }

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(
               getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort() + "?amqpCredits=0");
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();

         // Must be pull consumer to ensure we can check that demand on remote doesn't
         // offer credit until the pending message count on the Queue is zeroed
         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "AMQP", "tcp://localhost:" + AMQP_PORT + "?jms.prefetchPolicy.all=0");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("test");
            final MessageProducer producer = session.createProducer(queue);

            // Add to backlog
            producer.send(session.createMessage());

            // Now create demand on the queue
            final MessageConsumer consumer = session.createConsumer(queue);

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(expected);

            // Remove the backlog and credit should be offered to the remote
            assertNotNull(consumer.receiveNoWait());

            peer.waitForScriptToComplete(20, TimeUnit.SECONDS);

            // Consume all the credit that was presented in the batch
            for (int i = 0; i < expected; ++i) {
               peer.expectDisposition().withState().accepted();
               peer.remoteTransfer().withBody().withString("test-message")
                                    .also()
                                    .withDeliveryId(i)
                                    .now();
            }

            Wait.assertTrue(() -> server.queueQuery(queueName).getMessageCount() == expected, 10_000);

            // Consume all the newly received message from the remote except one
            // which should leave the queue with a pending message so no credit
            // should be offered.
            for (int i = 0; i < expected - 1; ++i) {
               assertNotNull(consumer.receive(100));
            }

            // We should not get a new batch yet as there is still one pending
            // message on the local queue we have not consumed.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(expected);

            // Remove the backlog and credit should be offered to the remote again
            assertNotNull(consumer.receiveNoWait());

            peer.waitForScriptToComplete(20, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(expected).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(expected + expected).withDrain(true).afterDelay(2);
            peer.expectDetach().respond();

            consumer.close(); // Remove local demand and federation consumer is torn down.

            peer.waitForScriptToComplete(20, TimeUnit.SECONDS);
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
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

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
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withDesiredCapabilities(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         // If core message tunneling is enabled we include the desired capability
         peer.remoteAttach().ofReceiver()
                            .withOfferedCapabilities(receiverOfferedCapabilities)
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();
         peer.remoteFlow().withLinkCredit(10).now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectTransfer().withNonNullPayload()
                              .withMessageFormat(messageFormat).accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue("test"));

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
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                      .setAddress("test")
                                                      .setAutoCreated(false));

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
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withDesiredCapabilities(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         // If core message tunneling is enabled we include the desired capability
         peer.remoteAttach().ofReceiver()
                            .withOfferedCapabilities(receiverOfferedCapabilities)
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
            final MessageProducer producer = session.createProducer(session.createQueue("test"));

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("#", getTestName());
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withOfferedCapability(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
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
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesQueueReceiverLinkForQueueAfterBrokerConnectionStarted() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Queue queue = session.createQueue("test");

         session.createConsumer(queue);
         session.createConsumer(queue);
         session.createConsumer(queue);

         connection.start();

         // Should be no interactions yet, check to be sure
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.getBrokerConnections().forEach(c -> {
            try {
               c.start();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });

         // Add another demand consumer immediately after start to add more events
         // on the federation plugin for broker events.
         session.createConsumer(queue);

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
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         // Add more demand after the broker connection starts
         session.createConsumer(queue);
         session.createConsumer(queue);

         // This should remove all demand on the queue and federation should stop
         connection.close();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesEventSenderAndReceiverWhenLocalAndRemotePoliciesAdded() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_QUEUE_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("*");
      includes.add("test");

      policyMap.put(POLICY_NAME, "test-policy");
      policyMap.put(QUEUE_INCLUDE_FEDERATED, false);
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, 64);
      policyMap.put(QUEUE_INCLUDES, includes);

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

         final AMQPFederationQueuePolicyElement localReceiveFromQueue = new AMQPFederationQueuePolicyElement();
         localReceiveFromQueue.setName("test-policy");
         localReceiveFromQueue.setIncludeFederated(true);
         localReceiveFromQueue.setPriorityAdjustment(42);
         localReceiveFromQueue.addToIncludes("*", "test");

         final AMQPFederationQueuePolicyElement remoteReceiveFromQueue = new AMQPFederationQueuePolicyElement();
         remoteReceiveFromQueue.setName("test-policy");
         remoteReceiveFromQueue.setIncludeFederated(false);
         remoteReceiveFromQueue.setPriorityAdjustment(64);
         remoteReceiveFromQueue.addToIncludes("*", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(localReceiveFromQueue);
         element.addRemoteQueuePolicy(remoteReceiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration("test-address-federation", "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
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
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_QUEUE_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("*");
      includes.add("test");

      policyMap.put(POLICY_NAME, "test-policy");
      policyMap.put(QUEUE_INCLUDE_FEDERATED, false);
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, 64);
      policyMap.put(QUEUE_INCLUDES, includes);

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

         final AMQPFederationQueuePolicyElement remoteReceiveFromQueue = new AMQPFederationQueuePolicyElement();
         remoteReceiveFromQueue.setName("test-policy");
         remoteReceiveFromQueue.setIncludeFederated(false);
         remoteReceiveFromQueue.setPriorityAdjustment(64);
         remoteReceiveFromQueue.addToIncludes("*", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addRemoteQueuePolicy(remoteReceiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration("test-address-federation", "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
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
   public void testRemoteBrokerSendsQueueAddedEventForInterestedPeer() throws Exception {
      final AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(false);
      addressSettings.setAutoCreateAddresses(false);

      server.getConfiguration().getAddressSettings().put("#", addressSettings);
      server.start();

      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(EVENT_TYPE.toString(), Matchers.is(REQUESTED_QUEUE_ADDED));
      final Map<String, Object> eventMap = new LinkedHashMap<>();
      eventMap.put(REQUESTED_ADDRESS_NAME, "test");
      eventMap.put(REQUESTED_QUEUE_NAME, "test");

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(eventMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", false, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withTarget().also()
                            .withNullSource();
         peer.expectDetach().respond();

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
   public void testFederationCreatesQueueReceiverInResponseToQueueAddedEvent() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("test-policy");
         receiveFromQueue.setIncludeFederated(false);
         receiveFromQueue.addToIncludes("*", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration("test-queue-federation", "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Reject the initial attempt
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullSource()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Queue not found")
                            .queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

         // Create demand on the Queue to kick off federation.
         session.createConsumer(session.createQueue("test"));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::test"),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Should not trigger attach of a federation receiver as the queue doesn't match the policy..
         sendQueueAddedEvent(peer, "target", "target", 1, 0);
         // Should trigger attach of federation receiver again for the test queue.
         sendQueueAddedEvent(peer, "test", "test", 1, 1);
         // Should not trigger another attach of federation receiver as there already is one.
         sendQueueAddedEvent(peer, "test", "test", 1, 2);

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("test-policy");
         receiveFromQueue.setIncludeFederated(false);
         receiveFromQueue.addToIncludes("*", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration("test-queue-federation", "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Reject the initial attempt
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .respond()
                            .withNullSource()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Queue not found")
                            .queue();
         peer.expectFlow().optional();
         peer.expectDetach();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         final Connection connection = factory.createConnection();
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         // Create demand on the Queue to kick off the first federation attempt.
         session.createConsumer(session.createQueue("test"));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         // Create demand on the Queue again to kick off another federation attempt.
         session.createConsumer(session.createQueue("test"));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Should not trigger attach of federation receiver as there already is one on this queue
         sendQueueAddedEvent(peer, "test", "test", 1, 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteBrokerClosesFederationReceiverAfterQueueRemoved() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", true, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("test::test")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress("test::test");

         // Connect to remote as if an queue had demand and matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("test::test")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().withError(AmqpError.RESOURCE_DELETED.toString());

         // Force remove consumers from the queue should indicate the resource was deleted.
         server.destroyQueue(SimpleString.of("test"), null, false, true);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
         maMatcher.withEntry(EVENT_TYPE.toString(), Matchers.is(REQUESTED_QUEUE_ADDED));
         final Map<String, Object> eventMap = new LinkedHashMap<>();
         eventMap.put(REQUESTED_ADDRESS_NAME, "test");
         eventMap.put(REQUESTED_QUEUE_NAME, "test");

         final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(eventMap);
         final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
         payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
         payloadMatcher.addMessageContentMatcher(bodyMatcher);

         // Server alerts the federation event receiver that a previously federated queue
         // has been added once more and it could restore the previous federation state.
         peer.expectTransfer().withPayload(payloadMatcher).withSettled(true);

         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // This time removing and restoring should generate no traffic as there was not
         // another federation receiver added.
         server.destroyQueue(SimpleString.of("test"), null, false, true);
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
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
   public void testFederationQueueDemandTrackedWhenRemoteRejectsInitialAttempts() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 5);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("test");

            connection.start();

            // First consumer we reject the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .respondInKind()
                               .withNullSource();
            peer.expectFlow().withLinkCredit(1000);
            peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested queue was not found").queue().afterDelay(10);
            peer.expectDetach();

            final MessageConsumer consumer1 = session.createConsumer(queue);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Second consumer we reject the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .respondInKind()
                               .withNullSource();
            peer.expectFlow().withLinkCredit(1000);
            peer.remoteDetach().withErrorCondition("amqp:not-found", "the requested queue was not found").queue().afterDelay(10);
            peer.expectDetach();

            final MessageConsumer consumer2 = session.createConsumer(queue);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Third consumer we accept the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .respond()
                               .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
            peer.expectFlow().withLinkCredit(1000);

            final MessageConsumer consumer3 = session.createConsumer(queue);

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
   public void testFederationQueueDemandTrackedWhenPluginBlocksInitialAttempts() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("test", "test");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 5);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final AtomicInteger blockUntilZero = new AtomicInteger(2);
         final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();
         federationPlugin.shouldCreateConsumerForDivert = (d, q) -> true;
         federationPlugin.shouldCreateConsumerForQueue = (q) -> {
            return blockUntilZero.getAndDecrement() == 0;
         };

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.registerBrokerPlugin(federationPlugin);
         server.start();
         server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                          .setAddress("test")
                                                          .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue("test");

            connection.start();

            final MessageConsumer consumer1 = session.createConsumer(queue);
            final MessageConsumer consumer2 = session.createConsumer(queue);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Third consumer we expect the plugin to allow the federation attempt
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .respondInKind();
            peer.expectFlow().withLinkCredit(1000);

            final MessageConsumer consumer3 = session.createConsumer(queue);

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
   public void testRemoteFederationReceiverCloseWhenDemandRemovedDoesNotTerminateRemoteConnection() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                      .setAddress("test")
                                                      .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test::test")
                                                    .withCapabilities("queue");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
            final MessageProducer producer = session.createProducer(session.createQueue("test"));

            producer.send(session.createMessage());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach();
         peer.remoteDetach().now();  // simulate demand removed so consumer is closed.

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test::test")
                                                    .withCapabilities("queue");

         // Connect to remote as if new demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                      .setAddress("test")
                                                      .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress("test::test")
                                                    .withCapabilities("queue");

         // Connect to remote as if some demand had matched our federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress("test::test")
                                         .withCapabilities("queue")
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
            final MessageProducer producer = session.createProducer(session.createQueue("test"));

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
   public void testNewFederationConsumerCreatedWhenDemandRemovedAndAddedWithDelayedPreviousDetach() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond().afterDelay(40); // Defer the detach response for a bit

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Create demand on the queue which creates a federation consumer then let it close which
         // should shut down that federation consumer.
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            consumer.receiveNoWait();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.expectFlow().withLinkCredit(1000).withDrain(true)
                          .respond()
                          .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
         peer.expectDetach().respond();

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true);  // Don't answer drained
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

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
   public void testPullModeFederationLinksRestartedBeforeRemoteFinishesDraining() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_CREDITS, 0);
         receiveFromQueue.addProperty(PULL_RECEIVER_BATCH_SIZE, 1);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1);

         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "AMQP", "tcp://localhost:" + AMQP_PORT + "?jms.prefetchPolicy.all=0");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));

            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1).withDrain(true);  // Don't answer drained

            // Adds backlog on the Queue, next federation consumer restart will wait for credit
            // until the backlog is removed.
            producer.send(session.createMessage());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            // Now answer the drained and expect that the queue policy manager will then
            // restart flow of credit based on new demand as expected above.
            peer.remoteFlow().withLinkCredit(0).withDeliveryCount(1).withDrain(true).now();

            // This should result from the answer to the next flow that drains the link.
            peer.expectFlow().withLinkCredit(1);

            assertNotNull(consumer.receiveNoWait()); // Remove backlog to allow credit to federation consumer

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(2).withDrain(true);
            peer.expectDetach().respond();
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationLinksDetachesAfterLinkQuiesceTimeoutWithIdleTimeout() throws Exception {
      doTestFederationLinksDetachesAfterLinkQuiesceTimeout(2);
   }

   @Test
   @Timeout(20)
   public void testFederationLinksDetachesAfterLinkQuiesceTimeoutNoIdleTimeout() throws Exception {
      doTestFederationLinksDetachesAfterLinkQuiesceTimeout(0);
   }

   private void doTestFederationLinksDetachesAfterLinkQuiesceTimeout(int idleTimeout) throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_QUIESCE_TIMEOUT, 20);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, idleTimeout);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true);  // Don't answer drained then wait for the
            peer.expectDetach().respond();                           // timeout to see the link is detached.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         // New demand should create a new federation consumer after the last drain timed out and closed the link
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

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
   public void testFederationLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemandNoIdleTimeout() throws Exception {
      doTestFederationLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemand(0);
   }

   @Test
   @Timeout(20)
   public void testFederationLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemandWithIdleTimeout() throws Exception {
      doTestFederationLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemand(2);
   }

   private void doTestFederationLinksRecoveredAfterLinkQuiesceTimeoutWithRenewedDemand(int idleTimeout) throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_QUIESCE_TIMEOUT, 300);
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, idleTimeout);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Demand is removed so expect a drain, don't respond then add new consumer to add
            // demand that must wait on drain to timeout and recover.
            peer.expectFlow().withLinkCredit(1000).withDrain(true);

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectAttach().ofReceiver()
                               .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("queue-receiver"),
                                               containsString(server.getNodeID().toString())))
                               .respondInKind();
            peer.expectDetach().respond();
            peer.expectFlow().withLinkCredit(1000);

            session.createConsumer(session.createQueue(getTestName()));

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
   public void testFederationResourceDeletedBeforeLinkQuiesceCompletesNoIdleTimeout() throws Exception {
      doTestFederationResourceDeletedBeforeLinkQuiesceCompletes(0);
   }

   @Test
   @Timeout(20)
   public void testFederationResourceDeletedBeforeLinkQuiesceCompletesWithIdleTimeout() throws Exception {
      doTestFederationResourceDeletedBeforeLinkQuiesceCompletes(2);
   }

   private void doTestFederationResourceDeletedBeforeLinkQuiesceCompletes(int idleTimeout) throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, idleTimeout);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true); // No answer to allow for race of answer plus delete
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // Now answer the drain request and then immediately remove the resource.
         peer.remoteFlow().withLinkCredit(0).withDeliveryCount(1000).withDrain(true).now();

         server.destroyQueue(SimpleString.of(getTestName()));

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 10_000);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

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

            session.createConsumer(session.createQueue(getTestName()));

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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 250);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 90_000);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

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
   public void testOverlappingPolicyTargetsRetainDemandTrackingState() throws Exception {
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
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind();
         peer.expectFlow().withLinkCredit(10);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // Ensure that policies that overlap on the same queue don't break the tracking of federation
         // consumers for the other policy if they store state data in consumer attachments. We want the
         // state data to not match between these two so that one could trip up the close event processing
         // of the other if that was indeed broken.

         final AMQPFederationQueuePolicyElement receiveFromQueue1 = new AMQPFederationQueuePolicyElement();
         receiveFromQueue1.setName("first");
         receiveFromQueue1.addToIncludes("#", getTestName());
         receiveFromQueue1.addProperty(IGNORE_QUEUE_CONSUMER_FILTERS, Boolean.TRUE.toString());
         receiveFromQueue1.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 10); // Stagger final drain to make frames predictable

         final AMQPFederationQueuePolicyElement receiveFromQueue2 = new AMQPFederationQueuePolicyElement();
         receiveFromQueue2.setName("second");
         receiveFromQueue2.addToIncludes("#", getTestName());
         receiveFromQueue2.addProperty(IGNORE_QUEUE_CONSUMER_FILTERS, Boolean.FALSE.toString());
         receiveFromQueue2.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 20); // Stagger final drain to make frames predictable

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue1);
         element.addLocalQueuePolicy(receiveFromQueue2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("test")
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::" + getTestName()),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("test::" + getTestName()),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_RECEIVER_PRIORITY.toString(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()), "color='red' OR color = 'blue'");

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);
            peer.expectFlow().withLinkCredit(1000).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true).afterDelay(2);

            peer.expectDetach().respond();
            peer.expectDetach().respond();

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testFederationReceiverRejectsWithModifiedDeliveryFailedAsDefault() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 250);

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

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
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final String payload = "A".repeat(2048);

         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "queue-policy")
                            .respond()
                            .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString());
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "AMQP", "tcp://localhost:" + AMQP_PORT + "?jms.prefetchPolicy.all=0");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue(getTestName());
            final MessageConsumer consumer = session.createConsumer(queue);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.expectDisposition().withState().accepted();     // This should fill the address
            peer.expectFlow().withLinkCredit(998).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDisposition().withState().modified(true); // Expect modified / failed so remote doesn't drop the message

            peer.remoteTransfer().withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                                 .withBody().withString("First Message: " + payload)
                                 .also()
                                 .withDeliveryId(1)
                                 .now();
            peer.remoteTransfer().withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                                 .withBody().withString("Second Message: ")
                                 .also()
                                 .withDeliveryId(2)
                                 .later(10);

            // Address remains full so no new credit is issued and a clean detach occurs next.

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            peer.expectDetach().respond();

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.expectClose();
         peer.remoteClose().now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteReFederatesQueueMessageWhenDemandIsAppliedAfterInitialDeliveryMarkedModifiedFailed() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test", true, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName("federation-queue-receiver")
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withTarget().also()
                                       .withSource().withAddress(getTestName());

         // Connect to remote as if a queue had demand and matched our local federation policy
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName("federation-queue-receiver")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress(getTestName())
                                         .withCapabilities("queue")
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
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));
            final Message message = session.createTextMessage("Message");

            producer.send(message);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         // Next grant of credit will deliver the message again
         peer.expectTransfer().withMessage().withHeader().withDeliveryCount(1).also()
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
   public void testRemoteFederationRespectsUseModifiedForRejectConfigurationWhenSetToRejectNoDrainOnAddressFull() throws Exception {
      testRemoteFederationRespectsUseModifiedForRejectConfiguration(false, false);
   }

   @Test
   @Timeout(20)
   public void testRemoteFederationRespectsUseModifiedForRejectConfigurationWhenSetToModifyNoDrainOnAddressFull() throws Exception {
      testRemoteFederationRespectsUseModifiedForRejectConfiguration(true, false);
   }

   private void testRemoteFederationRespectsUseModifiedForRejectConfiguration(boolean useModified, boolean drainOnAddressFull) throws Exception {
      final AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(getTestName());
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch(getTestName(), addressSettings);
      server.start();
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", getTestName()));

      final Map<String, Object> properties = new HashMap<>();
      properties.put(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);
      properties.put(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS, useModified);
      properties.put(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, drainOnAddressFull);

      final String payload = "A".repeat(2048);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, null, properties, null,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withSource().withAddress(getTestName() + "::" + getTestName())
                            .and()
                            .respondInKind(); // Server detected demand
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "AMQP", "tcp://localhost:" + AMQP_PORT + "?jms.prefetchPolicy.all=0");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue(getTestName());
            final MessageConsumer consumer = session.createConsumer(queue);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectDisposition().withState().accepted();

            if (drainOnAddressFull) {
               peer.expectFlow().withLinkCredit(998).withDrain(true)
                                .respond()
                                .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            }

            if (useModified) {
               peer.expectDisposition().withState().modified(true);
            } else {
               peer.expectDisposition().withState().rejected();
            }

            peer.remoteTransfer().withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                                 .withBody().withString("First Message: " + payload)
                                 .also()
                                 .withDeliveryId(1)
                                 .now();
            peer.remoteTransfer().withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                                 .withBody().withString("Second Message: ")
                                 .also()
                                 .withDeliveryId(2)
                                 .later(10);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Normal federation receiver drains on close to ensure no dropped messages.
            if (!drainOnAddressFull) {
               peer.expectFlow().withLinkCredit(998).withDrain(true)
                                .respond()
                                .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            }

            peer.expectDetach().respond();

            consumer.close();

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
   public void testFederationGrantsCreditOnNewReceiverAfterLinkQuiescedByMessagesConsumingDrainedCredit() throws Exception {
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());
         receiveFromQueue.addProperty(RECEIVER_QUIESCE_TIMEOUT, 10_000);
         receiveFromQueue.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 10_000);
         receiveFromQueue.addProperty("amqpCredits", "3");
         receiveFromQueue.addProperty("amqpCreditsLow", "0");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .respondInKind();
         peer.expectFlow().withLinkCredit(3);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            // Demand is removed so expect a drain, respond to drain to quiesce the link
            // which should leave it idling and ready for recovery by next consumer.
            peer.expectFlow().withLinkCredit(3).withDrain(true);

            // Burn off credit with messages instead of a flow with drained.
            peer.remoteTransfer().withBody().withString("test-message-1").also()
                                 .withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().also()
                                 .withDeliveryId(0)
                                 .queue();
            peer.expectDisposition().withState().accepted();
            peer.remoteTransfer().withBody().withString("test-message-2").also()
                                 .withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().also()
                                 .withDeliveryId(1)
                                 .queue();
            peer.expectDisposition().withState().accepted();
            peer.remoteTransfer().withBody().withString("test-message-3").also()
                                 .withHeader().withDurability(true).also()
                                 .withApplicationProperties().also()
                                 .withMessageAnnotations().also()
                                 .withDeliveryId(2)
                                 .queue();
            peer.expectDisposition().withState().accepted();

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectFlow().withLinkCredit(3).withDeliveryCount(3);

            // New demand added before any quiesce or idle timeout can trigger
            session.createConsumer(session.createQueue(getTestName()));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
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

         final AMQPFederationQueuePolicyElement receiveFromQueue = new AMQPFederationQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes(getTestName(), getTestName());

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addLocalQueuePolicy(receiveFromQueue);
         element.addProperty(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, String.valueOf(drainOnFull));
         element.addProperty(RECEIVER_LINK_QUIESCE_TIMEOUT, 350);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

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
         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 5000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final String payload = "A".repeat(2048);

         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString(server.getNodeID().toString())))
                            .withProperty(FEDERATION_POLICY_NAME.toString(), "queue-policy")
                            .respondInKind();
         peer.expectFlow().withLinkCredit(1000);

         final ConnectionFactory factory = CFUtil.createConnectionFactory(
            "AMQP", "tcp://localhost:" + AMQP_PORT + "?jms.prefetchPolicy.all=0");

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final Queue queue = session.createQueue(getTestName());
            final MessageConsumer consumer = session.createConsumer(queue);

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

            if (!drainOnFull) {
               peer.expectFlow().withDrain(true).withLinkCredit(998)
                                .respond()
                                .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
               peer.expectDetach().respond();
            }

            consumer.close();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
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
   public void testRemoteFederationDoesNotCreateLinksForQueuesItCannotWriteTo() throws Exception {
      final String allowedQueue = getTestName();
      final String restrictedQueue = getTestName() + "restricted"; // Federation user cannot send here.

      configureSecurity(server, allowedQueue, restrictedQueue);
      server.start();

      final Collection<Map.Entry<String, String>> includes = new ArrayList<>();
      includes.add(new AbstractMap.SimpleEntry<>("#", allowedQueue));
      includes.add(new AbstractMap.SimpleEntry<>("#", restrictedQueue));

      final Map<String, Object> properties = new HashMap<>();
      properties.put(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

      final FederationReceiveFromQueuePolicy policy =
         new FederationReceiveFromQueuePolicy("test-queue-policy",
                                              true, -2, includes, null, properties, null,
                                              DEFAULT_WILDCARD_CONFIGURATION);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, federationUser, federationPass, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withSettled(true).withState().accepted();

         sendQueuePolicyToRemote(peer, policy);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withDesiredCapability(FEDERATION_QUEUE_RECEIVER.toString())
                            .withSource().withAddress(allowedQueue + "::" + allowedQueue)
                            .and()
                            .respondInKind(); // Server detected demand based on policy we sent it
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Demand on the unrestricted queue should generate federation links and move the message
         try (Connection connection = factory.createConnection(demandUser, demandPass)) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(allowedQueue));

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

         // Demand on the restricted queue should result in no federation links being attempted.
         try (Connection connection = factory.createConnection(demandUser, demandPass)) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(restrictedQueue));

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
      final String allowedQueue = getTestName();
      final String restrictedQueue = getTestName() + "restricted"; // Federation user cannot receive here.

      configureSecurity(server, allowedQueue, restrictedQueue);
      server.start();
      server.createQueue(QueueConfiguration.of(allowedQueue).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(allowedQueue)
                                                             .setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(restrictedQueue).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(restrictedQueue)
                                                             .setAutoCreated(false));

      final String allowedFQQN = allowedQueue + "::" + allowedQueue;
      final String restrictedFQQN = restrictedQueue + "::" + restrictedQueue;

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, federationUser, federationPass, "test");
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName(allowedFQQN)
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withSource().withAddress(allowedFQQN);

         // Connect to remote as if an queue had demand and matched our federation policy
         // This uses the allowed queue so it should attach without issue.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(allowedFQQN)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress(allowedFQQN)
                                         .withCapabilities("queue")
                                         .and()
                            .withTarget().and()
                            .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().withName(restrictedFQQN)
                                       .withOfferedCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                                       .withNullSource();
         peer.expectDetach().withError(AmqpError.UNAUTHORIZED_ACCESS.toString()).respond();

         // Connect to remote as if an queue had demand and matched our federation policy
         // This uses the restricted queue so it should fail to attach.
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_QUEUE_RECEIVER.toString())
                            .withName(restrictedFQQN)
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withAddress(restrictedFQQN)
                                         .withCapabilities("queue")
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

   private static void sendQueueAddedEvent(ProtonTestPeer peer, String address, String queue, int handle, int deliveryId) {
      final Map<String, Object> eventMap = new LinkedHashMap<>();
      eventMap.put(REQUESTED_ADDRESS_NAME, address);
      eventMap.put(REQUESTED_QUEUE_NAME, queue);

      // Should not trigger another attach of federation receiver as there already is one.
      peer.remoteTransfer().withHandle(handle)
                           .withDeliveryId(deliveryId)
                           .withSettled(true)
                           .withMessageAnnotations().withAnnotation(EVENT_TYPE.toString(), REQUESTED_QUEUE_ADDED)
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

   private void sendQueuePolicyToRemote(ProtonTestClient peer, FederationReceiveFromQueuePolicy policy) {
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      policyMap.put(POLICY_NAME, policy.getPolicyName());
      policyMap.put(QUEUE_INCLUDE_FEDERATED, policy.isIncludeFederated());
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, policy.getPriorityAjustment());

      if (!policy.getIncludes().isEmpty()) {
         final List<String> flattenedIncludes = new ArrayList<>(policy.getIncludes().size() * 2);
         policy.getIncludes().forEach((entry) -> {
            flattenedIncludes.add(entry.getKey());
            flattenedIncludes.add(entry.getValue());
         });

         policyMap.put(QUEUE_INCLUDES, flattenedIncludes);
      }

      if (!policy.getExcludes().isEmpty()) {
         final List<String> flatteneExcludes = new ArrayList<>(policy.getExcludes().size() * 2);
         policy.getExcludes().forEach((entry) -> {
            flatteneExcludes.add(entry.getKey());
            flatteneExcludes.add(entry.getValue());
         });

         policyMap.put(QUEUE_EXCLUDES, flatteneExcludes);
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
                           .withMessageAnnotations().withAnnotation(OPERATION_TYPE.toString(), ADD_QUEUE_POLICY)
                           .also()
                           .withBody().withValue(policyMap)
                           .also()
                           .now();
   }

   // Use this method to script the initial handshake that a broker that is establishing
   // a federation connection with a remote broker instance would perform.
   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName) {
      scriptFederationConnectToRemote(peer, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, int amqpCredits, int amqpLowCredits) {
      scriptFederationConnectToRemote(peer, federationName, amqpCredits, amqpLowCredits, false, false);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, boolean eventsSender, boolean eventsReceiver) {
      scriptFederationConnectToRemote(peer, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT, eventsSender, eventsReceiver);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, int amqpCredits, int amqpLowCredits, boolean eventsSender, boolean eventsReceiver) {
      scriptFederationConnectToRemote(peer, null, null, federationName, amqpCredits, amqpLowCredits, eventsSender, eventsReceiver);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String username, String password, String federationName) {
      scriptFederationConnectToRemote(peer, username, password, federationName, AmqpSupport.AMQP_CREDITS_DEFAULT, AmqpSupport.AMQP_LOW_CREDITS_DEFAULT, false, false);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String user, String password, String federationName, int amqpCredits, int amqpLowCredits, boolean eventsSender, boolean eventsReceiver) {

      final String federationControlLinkName = "Federation:control:" + UUID.randomUUID().toString();

      final Map<String, Object> federationConfiguration = new HashMap<>();
      federationConfiguration.put(RECEIVER_CREDITS, amqpCredits);
      federationConfiguration.put(RECEIVER_CREDITS_LOW, amqpLowCredits);

      final Map<String, Object> senderProperties = new HashMap<>();
      senderProperties.put(FEDERATION_CONFIGURATION.toString(), federationConfiguration);
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

   @Override
   protected void configureBrokerSecurity(ActiveMQServer server) {
      if (isSecurityEnabled()) {
         enableSecurity(server);
      } else {
         server.getConfiguration().setSecurityEnabled(false);
      }
   }

   private void configureSecurity(ActiveMQServer server, String allowedQueue, String restrictedQueue) {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      // User additions
      securityManager.getConfiguration().addUser(federationUser, federationPass);
      securityManager.getConfiguration().addRole(federationUser, "federate");

      securityManager.getConfiguration().addUser(demandUser, demandPass);
      securityManager.getConfiguration().addRole(demandUser, "user");

      // Configure roles
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> value = new HashSet<>();
      value.add(new Role("federate", true, true, false, false, false, false, true, true, false, false, false, false));
      value.add(new Role("user", true, true, true, false, false, false, true, true, true, false, false, false));

      Set<Role> restricted = new HashSet<>();
      restricted.add(new Role("federate", false, false, false, false, false, false, true, true, false, false, false, false));
      restricted.add(new Role("user", true, true, true, false, false, false, true, true, true, false, false, false));

      securityRepository.addMatch(restrictedQueue, restricted);
      securityRepository.addMatch(allowedQueue, value);
      securityRepository.addMatch(FEDERATION_BASE_VALIDATION_ADDRESS, value);

      server.getConfiguration().setSecurityEnabled(true);
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
