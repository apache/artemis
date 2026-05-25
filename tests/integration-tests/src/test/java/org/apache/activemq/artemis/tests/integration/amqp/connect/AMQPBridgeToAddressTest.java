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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_ATTACH_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.LINK_RECOVERY_INITIAL_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.MAX_LINK_RECOVERY_ATTEMPTS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PRESETTLE_SEND_MODE;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.DETACH_FORCED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.NOT_FOUND;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.RESOURCE_DELETED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.TUNNEL_CORE_MESSAGES;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test the AMQP Bridge to address configuration and protocol behaviors
 */
public class AMQPBridgeToAddressTest extends AmqpClientTestSupport {

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

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.addProperty(LINK_ATTACH_TIMEOUT, 1); // Seconds
         sendToAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 20); // Milliseconds

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())));
         peer.expectDetach();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesAddressSenderWhenLocalAddressIsStaticlyDefined() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName() + ".#");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);
         amqpConnection.setRetryInterval(10);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName() + ".1").also()
                            .withSource().withAddress(getTestName() + ".1").also()
                            .withName(allOf(containsString(getTestName() + ".1"),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName() + ".1"), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName() + ".1")).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName() + ".1")).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined address
         // should be the thing that triggers the bridging. The connection should remain active and
         // respond if the address is recreated later.
         logger.info("Removing Address from bridged address to eliminate sender");
         server.removeAddressInfo(SimpleString.of(getTestName() + ".1"), null, true);

         Wait.assertFalse(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName() + ".2").also()
                            .withSource().withAddress(getTestName() + ".2").also()
                            .withName(allOf(containsString(getTestName() + ".2"),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         // Add another address that matches the filter and the bridge should form without a reconnect.
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName() + ".2"), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName() + ".2")).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName() + ".2")).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeSenderRebuildAfterRemoteDetachForResourceDeleted() throws Exception {
      doTestBridgeSenderRebuildAfterRemoteDetachForSpecificConditions(RESOURCE_DELETED.toString());
   }

   @Test
   @Timeout(20)
   public void testBridgeSenderRebuildAfterRemoteDetachForResourceNotFound() throws Exception {
      doTestBridgeSenderRebuildAfterRemoteDetachForSpecificConditions(NOT_FOUND.toString());
   }

   @Test
   @Timeout(20)
   public void testBridgeSenderRebuildAfterRemoteDetachForForcedDetach() throws Exception {
      doTestBridgeSenderRebuildAfterRemoteDetachForSpecificConditions(DETACH_FORCED.toString());
   }

   private void doTestBridgeSenderRebuildAfterRemoteDetachForSpecificConditions(String condition) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         // This should trigger recovery handling to try and reattach the link
         peer.remoteDetach().withErrorCondition(condition, "resource issue")
                            .withClosed(true).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressSenderRoutesMessageFromLocalProducerToTheRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Producer connect should create the address and initiate the bridge sender attach
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic(getTestName()));

            // Await bridge sender attach.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectTransfer().withMessageFormat(0)
                                 .withMessage().withHeader().also()
                                               .withMessageAnnotations().also()
                                               .withProperties().also()
                                               .withValue("Hello")
                                               .and()
                                 .respond()
                                 .withSettled(true)
                                 .withState().accepted();

            connection.start();

            Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
            Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

            producer.send(session.createTextMessage("Hello"));
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined address
         // should be the thing that triggers the bridging.
         logger.info("Removing Address from bridged address to eliminate sender");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAddressSenderRoutesMessageMatchingFilterFromLocalProducerToTheRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.setFilter("color = 'red'");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Producer connect should create the address and initiate the bridge sender attach
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic(getTestName()));

            // Await bridge sender attach.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectTransfer().withMessageFormat(0)
                                 .withMessage().withHeader().also()
                                               .withMessageAnnotations().also()
                                               .withProperties().also()
                                               .withApplicationProperties().withProperty("color", "red").also()
                                               .withValue("red")
                                               .and()
                                 .respond()
                                 .withSettled(true)
                                 .withState().accepted();

            connection.start();

            Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
            Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

            final TextMessage blue = session.createTextMessage("blue");
            blue.setStringProperty("color", "blue");

            final TextMessage red = session.createTextMessage("red");
            red.setStringProperty("color", "red");

            producer.send(blue); // Should be filtered out
            producer.send(red);
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined address
         // should be the thing that triggers the bridging.
         logger.info("Removing Address from bridged address to eliminate sender");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithConfiguredRemoteAddressPrefix() throws Exception {
      doTestBridgeCreatesSenderWithConfiguredRemoteAddressCustomizations("queue://", null, null);
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithConfiguredRemoteAddress() throws Exception {
      doTestBridgeCreatesSenderWithConfiguredRemoteAddressCustomizations(null, "alternate", null);
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithConfiguredRemoteAddressSuffix() throws Exception {
      doTestBridgeCreatesSenderWithConfiguredRemoteAddressCustomizations(null, null, "?consumer-priority=1");
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithConfiguredRemoteAddressCustomizations() throws Exception {
      doTestBridgeCreatesSenderWithConfiguredRemoteAddressCustomizations("queue://", "alternate", "?consumer-priority=1");
   }

   private void doTestBridgeCreatesSenderWithConfiguredRemoteAddressCustomizations(String prefix, String address, String suffix) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.setRemoteAddressPrefix(prefix);
         sendToAddress.setRemoteAddress(address);
         sendToAddress.setRemoteAddressSuffix(suffix);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         final String expectedTargetAddress = Objects.requireNonNullElse(prefix, "") +
                                              Objects.requireNonNullElse(address, getTestName()) +
                                              Objects.requireNonNullElse(suffix, "");

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(expectedTargetAddress).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined address
         // should be the thing that triggers the bridging.
         logger.info("Removing Address from bridged address to eliminate sender");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithConfiguredRemoteTerminusCapabilities() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.setRemoteTerminusCapabilities(new String[] {"queue", "another"});

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("queue", "another")
                                         .also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined address
         // should be the thing that triggers the bridging.
         logger.info("Removing Address from bridged address to eliminate sender");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithSenderPresettledWhenConfigured() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.addProperty(PRESETTLE_SEND_MODE, "true");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withSenderSettleModeSettled()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined address
         // should be the thing that triggers the bridging.
         logger.info("Removing Address from bridged address to eliminate sender");
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);
         Wait.assertFalse(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
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

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 10); // 1 millisecond initial recovery delay

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         // Retry after delay.
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.close();
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

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.addToIncludes("another");
         sendToAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 1);  // 1 millisecond initial recovery delay
         sendToAddress.addProperty(LINK_RECOVERY_DELAY, 15);         // 15 millisecond continued recovery delay
         sendToAddress.addProperty(MAX_LINK_RECOVERY_ATTEMPTS, 2);   // 2 attempts then stop trying

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Attempt #1
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         // Attempt #2
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress("another").also()
                            .withSource().withAddress("another").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("another"),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();


         // Add the alternate address to trigger an attach to that
         server.addAddressInfo(new AddressInfo(SimpleString.of("another"), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("another")).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("another")).getQueueNames().size() > 0);

         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         // Remove and add the address again which should trigger new attempt
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);

         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 0);

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeDoesNotAttemptLimitedRecoveryAfterFirstReceiverFailsWithResourceNotFound() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.addToIncludes("another");
         sendToAddress.addProperty(LINK_RECOVERY_INITIAL_DELAY, 1);  // 1 millisecond initial recovery delay
         sendToAddress.addProperty(LINK_RECOVERY_DELAY, 10);         // 10 millisecond continued recovery delay
         sendToAddress.addProperty(MAX_LINK_RECOVERY_ATTEMPTS, 0);   // No attempts

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress("another").also()
                            .withSource().withAddress("another").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("another"),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         // add the alternate and it should trigger an attach on that address.
         server.addAddressInfo(new AddressInfo(SimpleString.of("another"), RoutingType.MULTICAST));
         // Remove and later add the address again which should trigger new attempt
         server.removeAddressInfo(SimpleString.of(getTestName()), null, true);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRebuildsAfterRemoteClosesLinkAfterSuccessfulAttach() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(3);
         amqpConnection.setRetryInterval(10);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteDetach().withClosed(true).queue(); // Remote close after attach is terminal
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeSendCoreMessageTunneleInAMQPMessageWhenSupported() throws Exception {
      doTestBridgeSendMessagesAccordingToTunnelingState(true, true);
   }

   @Test
   @Timeout(20)
   public void testBridgeSendCoreMessageConvertedToAMQPMessageWhenTunnelNotSupported() throws Exception {
      doTestBridgeSendMessagesAccordingToTunnelingState(true, false);
   }

   @Test
   @Timeout(20)
   public void testBridgeSendCoreMessageConvertedToAMQPMessageWhenConfigurationDisabledTunneling() throws Exception {
      doTestBridgeSendMessagesAccordingToTunnelingState(false, true);
   }

   public void doTestBridgeSendMessagesAccordingToTunnelingState(boolean enabled, boolean receiverSupport) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.addProperty(TUNNEL_CORE_MESSAGES, Boolean.toString(enabled));

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         if (receiverSupport && enabled) {
            peer.expectAttach().ofSender()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withDesiredCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-sender"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond()
                               .withOfferedCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString());
         } else if (enabled) {
            peer.expectAttach().ofSender()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withDesiredCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-sender"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
         } else {
            peer.expectAttach().ofSender()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("address-sender"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
         }
         peer.remoteFlow().withLinkCredit(1).queue();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT);

         // Producer connect should create the address and initiate the bridge sender attach
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic(getTestName()));

            // Await bridge sender attach.
            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            if (enabled && receiverSupport) {
               peer.expectTransfer().withMessageFormat(AMQP_TUNNELED_CORE_MESSAGE_FORMAT)
                                    .withMessage().withData(notNullValue()).also()
                                    .accept();
            } else {
               peer.expectTransfer().withMessageFormat(0)
                                    .withMessage().withHeader().also()
                                                  .withMessageAnnotations().also()
                                                  .withApplicationProperties().also()
                                                  .withProperties().also()
                                                  .withValue("Hello")
                                    .and()
                                    .accept();
            }

            connection.start();

            Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
            Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

            producer.send(session.createTextMessage("Hello"));

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         }

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeToAddressDefaultToTemporaryAddressBinding() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 1);

         final SimpleString binding = server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0);

         assertNotNull(binding);
         assertTrue(binding.startsWith(SimpleString.of("amqp-bridge-")));

         final QueueQueryResult bridgeQueueBinding = server.queueQuery(binding);

         assertTrue(bridgeQueueBinding.isTemporary());
         assertFalse(bridgeQueueBinding.isDurable());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }

      // Once remote peer is offline the address binding should be cleaned up.
      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 0);
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesDurableBindingWhenSenderConfiguredToUseDurableSubscriptions() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         server.start();

         peer.expectSASLAnonymousConnect();
         peer.expectOpen().withContainerId(server.getNodeID().toString()).respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.setUseDurableSubscriptions(true);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(6);
         amqpConnection.setRetryInterval(10);
         amqpConnection.addElement(element);

         final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
            server.getRemotingService().getProtocolFactoryMap().get("AMQP");
         assertNotNull(protocolFactory);

         server.getConfiguration().getAMQPConnection().clear();
         server.getConfiguration().addAMQPConnection(amqpConnection);

         // Forces a reload of services which should start the now added bridge connection
         protocolFactory.updateProtocolServices(server, new ArrayList<>());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 1);

         final SimpleString binding = server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0);

         assertNotNull(binding);
         assertTrue(binding.startsWith(SimpleString.of("amqp-bridge-")));

         final QueueQueryResult bridgeQueueBinding = server.queueQuery(binding);

         assertFalse(bridgeQueueBinding.isTemporary());
         assertTrue(bridgeQueueBinding.isDurable());
         assertEquals(RoutingType.MULTICAST, bridgeQueueBinding.getRoutingType());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }

      // Once remote peer is offline the address binding should not be cleaned up.
      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists(), 5_000, 25);
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 1, 5_000, 25);
   }

   @Test
   @Timeout(20)
   public void testBridgeToAddressCleansUpDurableBindingIfConfiguredTo() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("address-policy");
         sendToAddress.addToIncludes(getTestName());
         sendToAddress.setUseDurableSubscriptions(true);
         sendToAddress.addProperty(AUTO_DELETE_DURABLE_SUBSCRIPTION, "true");
         sendToAddress.addProperty(AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT, 0);
         sendToAddress.addProperty(AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY, 100);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToAddressPolicy(sendToAddress);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);
         amqpConnection.addElement(element);

         server.getConfiguration().setAddressQueueScanPeriod(200);
         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("address-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 1);

         final SimpleString binding = server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0);

         assertNotNull(binding);
         assertTrue(binding.startsWith(SimpleString.of("amqp-bridge-")));

         final QueueQueryResult bridgeQueueBinding = server.queueQuery(binding);

         assertFalse(bridgeQueueBinding.isTemporary());
         assertTrue(bridgeQueueBinding.isDurable());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }

      // Once remote peer is offline the address binding should be cleaned up.
      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 0);
   }
}
