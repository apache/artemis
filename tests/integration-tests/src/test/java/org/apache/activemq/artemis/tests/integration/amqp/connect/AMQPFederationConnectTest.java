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
import javax.jms.JMSException;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.ActiveMQServerAMQPFederationPlugin;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.apache.qpid.protonj2.test.driver.matchers.messaging.MessageAnnotationsMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.transport.TransferPayloadCompositeMatcher;
import org.apache.qpid.protonj2.test.driver.matchers.types.EncodedAmqpValueMatcher;
import org.hamcrest.Matchers;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.BROKER_CONNECTION_INFO;
import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.CONNECTION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionConstants.NODE_ID;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_ENABLE_DIVERT_BINDINGS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_EXCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_INCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_MAX_HOPS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_ADDRESS_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_QUEUE_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_BASE_VALIDATION_ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONFIGURATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENTS_LINK_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V1;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V2;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_VERSION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_ADDRESS_BINDING_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_PRIORITIES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.LARGE_MESSAGE_THRESHOLD;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.OPERATION_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_NAME;
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
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests basic connect handling of the AMQP federation feature.
 */
public class AMQPFederationConnectTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected ActiveMQServer createServer() throws Exception {
      // Creates the broker used to make the outgoing connection. The port passed is for
      // that brokers acceptor. The test server connected to by the broker binds to a random port.
      return createServer(AMQP_PORT, false);
   }

   @Test
   @Timeout(20)
   public void testBrokerConnectsWithAnonymous() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testFederatedBrokerConnectsWithPlain() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("user", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         server.getConfiguration().addAMQPConnection(amqpConnection);
         amqpConnection.setUser("user");
         amqpConnection.setPassword("pass");
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerConnectsAndCreateManagementResource() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect("testUser", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setRetryInterval(100);
         amqpConnection.setUser("testUser");
         amqpConnection.setPassword("pass");
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(getTestName());

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());
         assertTrue(brokerConnection.isStarted());
         assertNotNull(brokerConnection.getUri());
         assertEquals(getTestName(), brokerConnection.getName());
         assertEquals("testUser", brokerConnection.getUser());
         assertEquals(0, brokerConnection.getReconnectAttempts());
         assertEquals(100, brokerConnection.getRetryInterval());
         assertEquals("AMQP", brokerConnection.getProtocol());

         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         brokerConnection.stop();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Reflects state change but no NPE when connection is stopped.
         assertFalse(brokerConnection.isConnected());
         assertFalse(brokerConnection.isStarted());
         assertNotNull(brokerConnection.getUri());
         assertEquals(getTestName(), brokerConnection.getName());
         assertEquals("testUser", brokerConnection.getUser());
         assertEquals(0, brokerConnection.getReconnectAttempts());
         assertEquals(100, brokerConnection.getRetryInterval());
         assertEquals("AMQP", brokerConnection.getProtocol());

         peer.expectSASLPlainConnect("testUser", "pass", "PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();

         brokerConnection.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertTrue(brokerConnection.isConnected());
         assertTrue(brokerConnection.isStarted());

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationConfiguredCreatesControlLink() throws Exception {
      final int AMQP_MIN_LARGE_MESSAGE_SIZE = 10_000;
      final int AMQP_CREDITS = 100;
      final int AMQP_CREDITS_LOW = 50;
      final int AMQP_RECEIVER_QUIESCE_TIMEOUT = 10_000;
      final int AMQP_ADDRESS_RECEIVER_IDLE_TIMEOUT = 10_000;
      final int AMQP_QUEUE_RECEIVER_IDLE_TIMEOUT = 7_000;
      final int AMQP_PULL_CREDITS_BATCH = 50;
      final int AMQP_LINK_ATTACH_TIMEOUT = 60;
      final boolean AMQP_TUNNEL_CORE_MESSAGES = false;
      final boolean AMQP_INGNORE_CONSUMER_FILTERS = false;
      final boolean AMQP_INGNORE_CONSUMER_PRIORITIES = false;
      final boolean AMQP_INGNORE_ADDRESS_BINDING_FILTERS = false;
      final boolean AMQP_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS = false;
      final int AMQP_RECEIVER_LINK_QUIESCE_TIMEOUT = 250_000;
      final boolean AMQP_RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS = false;

      final Map<String, Object> federationConfiguration = new HashMap<>();
      federationConfiguration.put(RECEIVER_CREDITS, AMQP_CREDITS);
      federationConfiguration.put(RECEIVER_CREDITS_LOW, AMQP_CREDITS_LOW);
      federationConfiguration.put(RECEIVER_QUIESCE_TIMEOUT, AMQP_RECEIVER_QUIESCE_TIMEOUT);
      federationConfiguration.put(ADDRESS_RECEIVER_IDLE_TIMEOUT, AMQP_ADDRESS_RECEIVER_IDLE_TIMEOUT);
      federationConfiguration.put(QUEUE_RECEIVER_IDLE_TIMEOUT, AMQP_QUEUE_RECEIVER_IDLE_TIMEOUT);
      federationConfiguration.put(PULL_RECEIVER_BATCH_SIZE, AMQP_PULL_CREDITS_BATCH);
      federationConfiguration.put(LARGE_MESSAGE_THRESHOLD, AMQP_MIN_LARGE_MESSAGE_SIZE);
      federationConfiguration.put(LINK_ATTACH_TIMEOUT, AMQP_LINK_ATTACH_TIMEOUT);
      federationConfiguration.put(IGNORE_QUEUE_CONSUMER_FILTERS, AMQP_INGNORE_CONSUMER_FILTERS);
      federationConfiguration.put(IGNORE_QUEUE_CONSUMER_PRIORITIES, AMQP_INGNORE_CONSUMER_PRIORITIES);
      federationConfiguration.put(IGNORE_ADDRESS_BINDING_FILTERS, AMQP_INGNORE_ADDRESS_BINDING_FILTERS);
      federationConfiguration.put(AmqpSupport.TUNNEL_CORE_MESSAGES, AMQP_TUNNEL_CORE_MESSAGES);
      federationConfiguration.put(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS, AMQP_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS);
      federationConfiguration.put(RECEIVER_LINK_QUIESCE_TIMEOUT, AMQP_RECEIVER_LINK_QUIESCE_TIMEOUT);
      federationConfiguration.put(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, AMQP_RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS);

      final String controlLinkAddress = "test-control-address";

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withName(allOf(containsString("federation-"), containsString("myFederation")))
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .withProperty(FEDERATION_NAME.toString(), "myFederation")
                            .withProperty(FEDERATION_CONFIGURATION.toString(), federationConfiguration)
                            .withTarget().withDynamic(true)
                                         .withCapabilities("temporary-topic")
                            .and()
                            .respond()
                            .withTarget().withAddress(controlLinkAddress)
                            .and()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(
            getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort() +
            "?amqpCredits=" + AMQP_CREDITS + "&amqpLowCredits=" + AMQP_CREDITS_LOW +
            "&amqpMinLargeMessageSize=" + AMQP_MIN_LARGE_MESSAGE_SIZE);
         amqpConnection.setReconnectAttempts(0);// No reconnects
         final AMQPFederatedBrokerConnectionElement federation = new AMQPFederatedBrokerConnectionElement("myFederation");
         federation.addProperty(LINK_ATTACH_TIMEOUT, AMQP_LINK_ATTACH_TIMEOUT);
         federation.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(AMQP_TUNNEL_CORE_MESSAGES));
         federation.addProperty(PULL_RECEIVER_BATCH_SIZE, AMQP_PULL_CREDITS_BATCH);
         federation.addProperty(RECEIVER_QUIESCE_TIMEOUT, AMQP_RECEIVER_QUIESCE_TIMEOUT);
         federation.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, AMQP_ADDRESS_RECEIVER_IDLE_TIMEOUT);
         federation.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, AMQP_QUEUE_RECEIVER_IDLE_TIMEOUT);
         federation.addProperty(IGNORE_QUEUE_CONSUMER_PRIORITIES, Boolean.toString(AMQP_INGNORE_CONSUMER_PRIORITIES));
         federation.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, Boolean.toString(AMQP_INGNORE_ADDRESS_BINDING_FILTERS));
         federation.addProperty(USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS, Boolean.toString(AMQP_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS));
         federation.addProperty(RECEIVER_LINK_QUIESCE_TIMEOUT, AMQP_RECEIVER_LINK_QUIESCE_TIMEOUT);
         federation.addProperty(RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS, Boolean.toString(AMQP_RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS));
         amqpConnection.addElement(federation);
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.locateQueue(FEDERATION_BASE_VALIDATION_ADDRESS +
                                                  "." + FEDERATION_CONTROL_LINK_PREFIX +
                                                  "." + controlLinkAddress) != null);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesControlLinkAndContinuesIfConnectedToOldVersion() throws Exception {
      final String controlLinkAddress = "test-control-address";

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                            .respond()
                            .withTarget().withAddress(controlLinkAddress)
                            .and()
                            .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V1)
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(new AMQPFederatedBrokerConnectionElement(getTestName()));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.locateQueue(FEDERATION_BASE_VALIDATION_ADDRESS +
                                                  "." + FEDERATION_CONTROL_LINK_PREFIX +
                                                  "." + controlLinkAddress) != null);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesControlLinkAndClosesConnectionIfCapabilityIsAbsent() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender().withDesiredCapability(FEDERATION_CONTROL_LINK.toString()).respond();
         peer.expectClose().optional(); // Can sometimes be sent
         peer.expectConnectionToDrop();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(new AMQPFederatedBrokerConnectionElement(getTestName()));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testFederationCreatesControlLinkAndClosesConnectionDetachIndicatesNotAuthorized() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender().withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                                       .respond()
                                       .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString())
                                       .withSource()
                                       .also()
                                       .withNullTarget();
         peer.remoteDetach().withErrorCondition("amqp:unauthorized-access", "Not authroized").queue();
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();
         // Broker reconnect and allow it to attach this time.
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind()
                            .withTarget().withAddress("dynamic-name");
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);// One reconnects
         amqpConnection.setRetryInterval(200);
         amqpConnection.addElement(new AMQPFederatedBrokerConnectionElement(getTestName()));
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(10, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.locateQueue(FEDERATION_BASE_VALIDATION_ADDRESS +
                                                  "." + FEDERATION_CONTROL_LINK_PREFIX +
                                                  ".dynamic-name") != null);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testFederationSendsReceiveFromQueuePolicyToRemoteWhenSendToIsConfigured() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_QUEUE_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("a");
      includes.add("b");
      includes.add("c");
      includes.add("d");
      final List<String> excludes = new ArrayList<>();
      excludes.add("e");
      excludes.add("f");
      excludes.add("g");
      excludes.add("h");

      policyMap.put(POLICY_NAME, "test-policy");
      policyMap.put(QUEUE_INCLUDE_FEDERATED, true);
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, 42);
      policyMap.put(QUEUE_INCLUDES, includes);
      policyMap.put(QUEUE_EXCLUDES, excludes);

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(policyMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind().withTarget().withAddress("test-dynamic");
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind().withTarget().withAddress("test-dynamic-events");
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().withPayload(payloadMatcher);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement sendToQueue = new AMQPFederationQueuePolicyElement();
         sendToQueue.setName("test-policy");
         sendToQueue.setIncludeFederated(true);
         sendToQueue.setPriorityAdjustment(42);
         sendToQueue.addToIncludes("a", "b");
         sendToQueue.addToIncludes("c", "d");
         sendToQueue.addToExcludes("e", "f");
         sendToQueue.addToExcludes("g", "h");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addRemoteQueuePolicy(sendToQueue);

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
   public void testFederationSendsReceiveFromQueuePolicyToRemoteWhenSendToIsConfiguredAndEventSenderRejected() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_QUEUE_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("a");
      includes.add("b");
      includes.add("c");
      includes.add("d");
      final List<String> excludes = new ArrayList<>();
      excludes.add("e");
      excludes.add("f");
      excludes.add("g");
      excludes.add("h");

      policyMap.put(POLICY_NAME, "test-policy");
      policyMap.put(QUEUE_INCLUDE_FEDERATED, true);
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, 42);
      policyMap.put(QUEUE_INCLUDES, includes);
      policyMap.put(QUEUE_EXCLUDES, excludes);

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(policyMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind().withTarget().withAddress("test-dynamic");
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respond()
                            .withNullSource();
         peer.expectDetach().respond();
         peer.remoteFlow().withHandle(0).withLinkCredit(10).queue(); // Ensure order of events
         peer.expectTransfer().withPayload(payloadMatcher);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationQueuePolicyElement sendToQueue = new AMQPFederationQueuePolicyElement();
         sendToQueue.setName("test-policy");
         sendToQueue.setIncludeFederated(true);
         sendToQueue.setPriorityAdjustment(42);
         sendToQueue.addToIncludes("a", "b");
         sendToQueue.addToIncludes("c", "d");
         sendToQueue.addToExcludes("e", "f");
         sendToQueue.addToExcludes("g", "h");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addRemoteQueuePolicy(sendToQueue);

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
   public void testFederationSendsReceiveFromAddressPolicyToRemoteWhenSendToIsConfigured() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_ADDRESS_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("include");
      final List<String> excludes = new ArrayList<>();
      excludes.add("exclude");

      policyMap.put(POLICY_NAME, "test-policy");
      policyMap.put(ADDRESS_AUTO_DELETE, true);
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, 42L);
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 314L);
      policyMap.put(ADDRESS_MAX_HOPS, 5);
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, false);
      policyMap.put(ADDRESS_INCLUDES, includes);
      policyMap.put(ADDRESS_EXCLUDES, excludes);

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(policyMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind().withTarget().withAddress("test-dynamic");
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respondInKind().withTarget().withAddress("test-dynamic-events");
         peer.remoteFlow().withLinkCredit(10).queue();
         peer.expectTransfer().withPayload(payloadMatcher);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement sendToAddress = new AMQPFederationAddressPolicyElement();
         sendToAddress.setName("test-policy");
         sendToAddress.setAutoDelete(true);
         sendToAddress.setAutoDeleteDelay(42L);
         sendToAddress.setAutoDeleteMessageCount(314L);
         sendToAddress.setMaxHops(5);
         sendToAddress.setEnableDivertBindings(false);
         sendToAddress.addToIncludes("include");
         sendToAddress.addToExcludes("exclude");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addRemoteAddressPolicy(sendToAddress);

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
   public void testFederationSendsReceiveFromAddressPolicyToRemoteWhenSendToIsConfiguredAndEventSenderRejected() throws Exception {
      final MessageAnnotationsMatcher maMatcher = new MessageAnnotationsMatcher(true);
      maMatcher.withEntry(OPERATION_TYPE.toString(), Matchers.is(ADD_ADDRESS_POLICY));
      final Map<String, Object> policyMap = new LinkedHashMap<>();

      final List<String> includes = new ArrayList<>();
      includes.add("include");
      final List<String> excludes = new ArrayList<>();
      excludes.add("exclude");

      policyMap.put(POLICY_NAME, "test-policy");
      policyMap.put(ADDRESS_AUTO_DELETE, true);
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, 42L);
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, 314L);
      policyMap.put(ADDRESS_MAX_HOPS, 5);
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, false);
      policyMap.put(ADDRESS_INCLUDES, includes);
      policyMap.put(ADDRESS_EXCLUDES, excludes);

      final EncodedAmqpValueMatcher bodyMatcher = new EncodedAmqpValueMatcher(policyMap);
      final TransferPayloadCompositeMatcher payloadMatcher = new TransferPayloadCompositeMatcher();
      payloadMatcher.setMessageAnnotationsMatcher(maMatcher);
      payloadMatcher.addMessageContentMatcher(bodyMatcher);

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .respondInKind().withTarget().withAddress("test-dynamic");
         peer.expectAttach().ofSender()
                            .withTarget().withDynamic(true).and()
                            .withDesiredCapability(FEDERATION_EVENT_LINK.toString())
                            .respond()
                            .withNullTarget();
         peer.expectDetach().respond();
         peer.remoteFlow().withHandle(0).withLinkCredit(10).queue(); // Ensure order of events
         peer.expectTransfer().withPayload(payloadMatcher);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPFederationAddressPolicyElement sendToAddress = new AMQPFederationAddressPolicyElement();
         sendToAddress.setName("test-policy");
         sendToAddress.setAutoDelete(true);
         sendToAddress.setAutoDeleteDelay(42L);
         sendToAddress.setAutoDeleteMessageCount(314L);
         sendToAddress.setMaxHops(5);
         sendToAddress.setEnableDivertBindings(false);
         sendToAddress.addToIncludes("include");
         sendToAddress.addToExcludes("exclude");

         final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
         element.setName(getTestName());
         element.addRemoteAddressPolicy(sendToAddress);

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
   public void testConnectToBrokerFromRemoteAsFederatedSourceAndCreateControlLink() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, getTestName());
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testConnectToBrokerFromRemoteAsFederatedSourceAndCreateEventsSenderLink() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, getTestName(), false, null, null, true, false);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testConnectToBrokerFromRemoteAsFederatedSourceAndCreateEventsReceiverLink() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, getTestName(), false, null, null, false, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testConnectToBrokerFromRemoteAsFederatedSourceAndCreateEventsLinks() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, getTestName(), false, null, null, true, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testControlLinkPassesConnectAttemptWhenUserHasPrivledges() throws Exception {
      enableSecurity(server, FEDERATION_BASE_VALIDATION_ADDRESS);
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, getTestName(), fullUser, fullPass);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testControlAndEventsLinksPassesConnectAttemptWhenUserHasPrivledges() throws Exception {
      enableSecurity(server, FEDERATION_BASE_VALIDATION_ADDRESS + ".#");
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemote(peer, getTestName(), true, fullUser, fullPass, true, true);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testControlLinkRefusesConnectAttemptWhenUseDoesNotHavePrivledgesForControlAddress() throws Exception {
      enableSecurity(server, FEDERATION_BASE_VALIDATION_ADDRESS);
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         scriptFederationConnectToRemoteNotAuthorizedForControlAddress(peer, getTestName(), guestUser, guestPass);
         peer.connect("localhost", AMQP_PORT);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         server.stop();

         logger.info("Test stopped");
      }
   }

   @Test
   @Timeout(20)
   public void testRemoteConnectionCannotAttachEventReceiverLinkWithoutControlLink() throws Exception {
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
         peer.expectAttach().ofSender().withName("federation-event-receiver")
                                       .withNullSource()
                                       .withTarget();
         peer.expectDetach().withError(AmqpError.ILLEGAL_STATE.toString()).respond();

         // Attempt to create a federation event receiver link without existing control link
         peer.remoteAttach().ofReceiver()
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withName("federation-event-receiver")
                            .withSenderSettleModeSettled()
                            .withReceivervSettlesFirst()
                            .withSource().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withCapabilities("temporary-topic")
                                         .withDynamic(true)
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
   public void testRemoteConnectionCannotAttachEventSenderLinkWithoutControlLink() throws Exception {
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
         peer.expectAttach().ofReceiver().withName("federation-event-sender")
                                         .withSource().also()
                                         .withNullTarget();
         peer.expectDetach().withError(AmqpError.ILLEGAL_STATE.toString()).respond();

         // Attempt to create a federation event receiver link without existing control link
         peer.remoteAttach().ofSender()
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withName("federation-event-sender")
                            .withSenderSettleModeSettled()
                            .withReceivervSettlesFirst()
                            .withTarget().withDurabilityOfNone()
                                         .withExpiryPolicyOnLinkDetach()
                                         .withCapabilities("temporary-topic")
                                         .withDynamic(true)
                                         .and()
                            .withSource().and()
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
   public void testControlLinkSenderQueueCreatedWithMaxConsumersOfOne() throws Exception {
      final String controlLinkAddress = "test-control-address";
      final String federationControlSenderAddress = FEDERATION_BASE_VALIDATION_ADDRESS +
                                                    "." + FEDERATION_CONTROL_LINK_PREFIX +
                                                    "." + controlLinkAddress;

      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withDesiredCapability(FEDERATION_CONTROL_LINK.toString())
                            .withName(allOf(containsString("federation-"), containsString("myFederation")))
                            .withTarget().withDynamic(true)
                                         .withCapabilities("temporary-topic")
                            .and()
                            .respond()
                            .withTarget().withAddress(controlLinkAddress)
                            .and()
                            .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(
            getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         final AMQPFederatedBrokerConnectionElement federation = new AMQPFederatedBrokerConnectionElement("myFederation");
         amqpConnection.addElement(federation);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.locateQueue(federationControlSenderAddress) != null);

         final Queue result = server.locateQueue(SimpleString.of(federationControlSenderAddress));

         assertNotNull(result);
         assertTrue(result.isTemporary());
         assertTrue(result.isInternalQueue());
         assertEquals(1, result.getMaxConsumers());

         // Try and bind to the control address which should be rejected as the queue
         // was created with max consumers of one.
         peer.expectAttach().ofSender()
                            .withName("test-control-link-suspect")
                            .withNullSource();
         peer.expectDetach().withClosed(true)
                            .withError(AmqpError.INTERNAL_ERROR.toString());
         peer.remoteAttach().ofReceiver()
                            .withName("test-control-link-suspect")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withTarget().also()
                            .withSource().withAddress(federationControlSenderAddress)
                                         .also()
                                         .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testEventSenderLinkFromTargetUsesNamespacedDynamicQueue() throws Exception {
      final String federationControlLinkName = "federation-test";

      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.remoteOpen().queue();
         peer.expectOpen();
         peer.remoteBegin().queue();
         peer.expectBegin();
         peer.remoteAttach().ofSender()
                            .withName(federationControlLinkName)
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
                            .withName(federationControlLinkName)
                            .withTarget()
                               .withAddress(notNullValue())
                            .also()
                            .withOfferedCapability(FEDERATION_CONTROL_LINK.toString());
         peer.expectFlow();

         final String federationEventsSenderLinkName = "events-receiver-test";

         peer.remoteAttach().ofReceiver()
                            .withName(federationEventsSenderLinkName)
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withSenderSettleModeUnsettled()
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

         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // The events receiver from the remote should trigger a temporary queue to be created on
         // the server to allow sends of events beyond currently available credit.
         Wait.assertTrue(() -> server.locateQueue(FEDERATION_BASE_VALIDATION_ADDRESS +
                                                  "." + FEDERATION_EVENTS_LINK_PREFIX +
                                                  "." + federationEventsSenderLinkName) != null);

         server.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testEventsLinkAtTargetIsCreatedWithMaxConsumersOfOne() throws Exception {
      final String federationControlLinkName = "federation-test";

      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.remoteOpen().queue();
         peer.expectOpen();
         peer.remoteBegin().queue();
         peer.expectBegin();
         peer.remoteAttach().ofSender()
                            .withName(federationControlLinkName)
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
                            .withName(federationControlLinkName)
                            .withTarget()
                               .withAddress(notNullValue())
                            .also()
                            .withOfferedCapability(FEDERATION_CONTROL_LINK.toString());
         peer.expectFlow();

         final String federationEventsSenderLinkName = "events-receiver-test";
         final String federationEventsSenderAddress = FEDERATION_BASE_VALIDATION_ADDRESS +
                                                      "." + FEDERATION_EVENTS_LINK_PREFIX +
                                                      "." + federationEventsSenderLinkName;

         peer.remoteAttach().ofReceiver()
                            .withName(federationEventsSenderLinkName)
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withSenderSettleModeUnsettled()
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

         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // The events receiver from the remote should trigger a temporary queue to be created on
         // the server to allow sends of events beyond currently available credit.
         Wait.assertTrue(() -> server.locateQueue(federationEventsSenderAddress) != null);

         final Queue result = server.locateQueue(SimpleString.of(federationEventsSenderAddress));

         assertNotNull(result);
         assertTrue(result.isTemporary());
         assertTrue(result.isInternalQueue());
         assertEquals(1, result.getMaxConsumers());

         // Try and bind to the events address which should be rejected as the queue
         // was created with max consumers of one.
         peer.expectAttach().ofSender()
                            .withName("test-events-link-suspect")
                            .withNullSource();
         peer.expectDetach().withClosed(true)
                            .withError(AmqpError.INTERNAL_ERROR.toString());
         peer.remoteAttach().ofReceiver()
                            .withName("test-events-link-suspect")
                            .withSenderSettleModeUnsettled()
                            .withReceivervSettlesFirst()
                            .withTarget().also()
                            .withSource().withAddress(federationEventsSenderAddress)
                                         .also()
                                         .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.stop();
      }
   }

   @Test
   @Timeout(30)
   public void testFederationDemandAddedAndImmediateBrokerShutdownOverlaps() throws Exception {
      // Testing for a race on broker shutdown if demand was added at the same time and the
      // broker is creating an outbound consumer to match that demand.
      for (int i = 0; i < 2; ++i) {
         doTestFederationDemandAddedAndImmediateBrokerShutdown();
      }
   }

   private void doTestFederationDemandAddedAndImmediateBrokerShutdown() throws Exception {
      if (server == null || !server.isStarted()) {
         server = createServer();
      }

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
         server.registerBrokerPlugin(new ActiveMQServerAMQPFederationPlugin() {

            @Override
            public void beforeCreateFederationConsumer(final FederationConsumerInfo consumerInfo) throws ActiveMQException {
               logger.debug("Delaying attach of outgoing federation receiver");
               ForkJoinPool.commonPool().execute(() -> {
                  try {
                     server.stop();
                  } catch (Exception e) {
                  }
               });
               // Allow a bit of time for the server stop to get started before allowing
               // the remote federation consumer to begin being built.
               try {
                  Thread.sleep(new Random(System.currentTimeMillis()).nextInt(8));
               } catch (InterruptedException e) {
               }
            }
         });

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().optional();
         peer.expectFlow().optional();
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            connection.start();

            try {
               session.createConsumer(session.createQueue("test"));
            } catch (JMSException ex) {
               // Ignored as we are asynchronously shutting down the server, this could happen.
            }

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerConnectsCarriesInfoInConnectionProperties() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setAutostart(false);
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         final Map<String, Object> brokerConnectionInfo = new HashMap<>();

         brokerConnectionInfo.put(CONNECTION_NAME, getTestName());
         brokerConnectionInfo.put(NODE_ID, server.getNodeID().toString());

         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().withProperty(BROKER_CONNECTION_INFO.toString(), brokerConnectionInfo).respond();
         peer.expectBegin().respond();

         server.getBrokerConnections().forEach(conn -> {
            try {
               conn.start();
            } catch (Exception e) {
               fail("Should not have thrown an error on start: " + e);
            }
         });

         peer.waitForScriptToComplete(5, TimeUnit.MINUTES);
      }
   }

   @Test
   @Timeout(20)
   public void testBrokerConnectionDoesNotSendOfferedCapabilities() throws Exception {
      // Checks that current behavior is to not send, this will break if the defaults are
      // changed such that the connection starts sending offered capabilities by default.
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect("PLAIN", "ANONYMOUS");
         peer.expectOpen().withOfferedCapabilities(nullValue()).respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   // Use these methods to script the initial handshake that a broker that is establishing
   // a federation connection with a remote broker instance would perform.

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName) {
      scriptFederationConnectToRemote(peer, federationName, false, null, null);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, String username, String password) {
      scriptFederationConnectToRemote(peer, federationName, true, username, password);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, boolean auth, String username, String password) {
      scriptFederationConnectToRemote(peer, federationName, auth, username, password, false, false);
   }

   private void scriptFederationConnectToRemote(ProtonTestClient peer, String federationName, boolean auth, String username, String password, boolean eventsSender, boolean eventsReceiver) {
      final String federationControlLinkName = "Federation:test:" + UUID.randomUUID().toString();

      if (auth) {
         peer.queueClientSaslPlainConnect(username, password);
      } else {
         peer.queueClientSaslAnonymousConnect();
      }

      peer.remoteOpen().queue();
      peer.expectOpen();
      peer.remoteBegin().queue();
      peer.expectBegin();
      peer.remoteAttach().ofSender()
                         .withName(federationControlLinkName)
                         .withDesiredCapabilities(FEDERATION_CONTROL_LINK.toString())
                         .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
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
                         .withName(federationControlLinkName)
                         .withTarget()
                            .withAddress(notNullValue())
                         .also()
                         .withProperty(FEDERATION_VERSION.toString(), FEDERATION_V2)
                         .withOfferedCapabilities(FEDERATION_CONTROL_LINK.toString());
      peer.expectFlow();

      if (eventsSender) {
         final String federationEventsSenderLinkName = "Federation:events-sender:test:" + UUID.randomUUID().toString();

         peer.remoteAttach().ofSender()
                            .withName(federationEventsSenderLinkName)
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
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
                            .withName(federationEventsSenderLinkName)
                            .withTarget()
                               .withAddress(notNullValue())
                            .also()
                            .withOfferedCapability(FEDERATION_EVENT_LINK.toString());
         peer.expectFlow();
      }

      if (eventsReceiver) {
         final String federationEventsSenderLinkName = "Federation:events-receiver:test:" + UUID.randomUUID().toString();

         peer.remoteAttach().ofReceiver()
                            .withName(federationEventsSenderLinkName)
                            .withDesiredCapabilities(FEDERATION_EVENT_LINK.toString())
                            .withSenderSettleModeUnsettled()
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

   private void scriptFederationConnectToRemoteNotAuthorizedForControlAddress(ProtonTestClient peer, String federationName, String username, String password) {
      final String federationControlLinkName = "Federation:test:" + UUID.randomUUID().toString();

      peer.queueClientSaslPlainConnect(username, password);
      peer.remoteOpen().queue();
      peer.expectOpen();
      peer.remoteBegin().queue();
      peer.expectBegin();
      peer.remoteAttach().ofSender()
                         .withName(federationControlLinkName)
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
                         .withTarget(nullValue());
      peer.expectDetach().withError("amqp:unauthorized-access",
                                    "User does not have permission to attach to the federation control address").respond();
      peer.remoteClose().queue();
      peer.expectClose();
   }
}
