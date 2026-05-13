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

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
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
 * Test the AMQP Bridge to queue configuration and protocol behaviors
 */
class AMQPBridgeToQueueTest extends AmqpClientTestSupport {

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

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes("#", getTestName());
         sendToQueue.addProperty(LINK_ATTACH_TIMEOUT, 1); // Seconds
         sendToQueue.addProperty(LINK_RECOVERY_INITIAL_DELAY, 20); // Milliseconds

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())));
         peer.expectDetach();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesQueueSenderLinkForQueueMatchAnycast() throws Exception {
      doTestBridgeCreatesQueueSenderLinkForQueueMatch(RoutingType.ANYCAST);
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesQueueSenderLinkForQueueMatchMulticast() throws Exception {
      doTestBridgeCreatesQueueSenderLinkForQueueMatch(RoutingType.MULTICAST);
   }

   private void doTestBridgeCreatesQueueSenderLinkForQueueMatch(RoutingType routingType) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(routingType)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testTargetAddressCarriesTheMatchedQueueName() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes("source", getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress("source::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress("source")
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeQueueSenderRoutesMessageFromLocalProducerToTheRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), "#");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Producer connect should create the address and initiate the bridge sender attach
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));

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

            Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

            producer.send(session.createTextMessage("Hello"));
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeQueueSenderRoutesMessageMatchingFilterFromLocalProducerToTheRemote() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), "#");
         sendToQueue.setFilter("color='red'");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         // Producer connect should create the address and initiate the bridge sender attach
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));

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

            Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

            final TextMessage blue = session.createTextMessage("blue");
            blue.setStringProperty("color", "blue");

            final TextMessage red = session.createTextMessage("red");
            red.setStringProperty("color", "red");

            producer.send(blue); // Should be filtered out.
            producer.send(red);
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesQueueSenderLinkWithConfiguredLocalPriorityForQueueMatchAnycast() throws Exception {
      doTestBridgeCreatesQueueSenderLinkWithConfiguredLocalPriorityForQueueMatch(RoutingType.ANYCAST);
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesQueueSenderLinkWithConfiguredLocalPriorityForQueueMatchMulticast() throws Exception {
      doTestBridgeCreatesQueueSenderLinkWithConfiguredLocalPriorityForQueueMatch(RoutingType.MULTICAST);
   }

   private void doTestBridgeCreatesQueueSenderLinkWithConfiguredLocalPriorityForQueueMatch(RoutingType routingType) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.setPriority(10);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(routingType)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         Wait.assertEquals(1, () -> server.locateQueue(SimpleString.of(getTestName())).getConsumers().size(), 5000, 100);
         server.locateQueue(SimpleString.of(getTestName())).getConsumers().forEach((c) -> {
            assertEquals(10, c.getPriority());
         });

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

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

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.setRemoteAddressPrefix(prefix);
         sendToQueue.setRemoteAddress(address);
         sendToQueue.setRemoteAddressSuffix(suffix);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

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

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.setRemoteTerminusCapabilities(new String[] {"queue", "another"});

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesSenderWithPresettledWhenConfigured() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.addProperty(PRESETTLE_SEND_MODE, "true");

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();

         // This should trigger the bridged sender to be shut down as the statically defined queue
         // should be the thing that triggers the bridging.
         logger.info("Removing Queue from bridged queue to eliminate sender");
         server.destroyQueue(SimpleString.of(getTestName()), null, false);
         Wait.assertFalse(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

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

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.addProperty(LINK_RECOVERY_INITIAL_DELAY, 10); // 1 millisecond initial recovery delay

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         // Retry after delay.
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeAttemptsLimitedRecoveryAfterFirstReceiverFailsWithResourceNotFound() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.addToIncludes("another", "another");
         sendToQueue.addProperty(LINK_RECOVERY_INITIAL_DELAY, 10); // 10 millisecond initial recovery delay
         sendToQueue.addProperty(LINK_RECOVERY_DELAY, 10);         // 10 millisecond continued recovery delay
         sendToQueue.addProperty(MAX_LINK_RECOVERY_ATTEMPTS, 2);   // 2 attempts then stop trying

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         // Recovery Attempt #1
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         // Recovery Attempt #2
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress("another").also()
                            .withSource().withAddress("another::another").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         // Add a binding to the alternate Queue and it should trigger new attach
         server.createQueue(QueueConfiguration.of("another").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("another")
                                                            .setAutoCreated(false));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("another")).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("another")).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         // Remove and add the queue again which should trigger new attempt
         server.destroyQueue(SimpleString.of(getTestName()), null, true);

         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() == 0);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeDoesNotAttemptLimitedRecoverAfterFirstReceiverFailsWithResourceNotFound() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.addToIncludes("another", "another");
         sendToQueue.addProperty(LINK_RECOVERY_INITIAL_DELAY, 1);  // 1 millisecond initial recovery delay
         sendToQueue.addProperty(LINK_RECOVERY_DELAY, 10);         // 10 millisecond continued recovery delay
         sendToQueue.addProperty(MAX_LINK_RECOVERY_ATTEMPTS, 0);   // No attempts

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond()
                            .withNullTarget();
         peer.remoteDetach().withClosed(true)
                            .withErrorCondition(AmqpError.NOT_FOUND.toString(), "Resource Not Found").queue();
         peer.expectDetach();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress("another").also()
                            .withSource().withAddress("another::another").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("another"),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         // Add the alternate queue again which should trigger new attempt
         server.createQueue(QueueConfiguration.of("another").setRoutingType(RoutingType.ANYCAST)
                                                            .setAddress("another")
                                                            .setAutoCreated(false));

         // Remove and later add the queue again which should trigger new attempt
         server.destroyQueue(SimpleString.of(getTestName()), null, true);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
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

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());
         sendToQueue.addProperty(LINK_RECOVERY_INITIAL_DELAY, 10); // 1 millisecond initial recovery delay

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         peer.waitForScriptToComplete(500, TimeUnit.SECONDS);
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

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes("#", getTestName());
         sendToQueue.addProperty(TUNNEL_CORE_MESSAGES, Boolean.toString(enabled));

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

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
                               .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                               .withDesiredCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("queue-sender"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond()
                               .withOfferedCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString());
         } else if (enabled) {
            peer.expectAttach().ofSender()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                               .withDesiredCapabilities(CORE_MESSAGE_TUNNELING_SUPPORT.toString())
                               .withName(allOf(containsString(getTestName()),
                                               containsString("queue-sender"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
         } else {
            peer.expectAttach().ofSender()
                               .withTarget().withAddress(getTestName()).also()
                               .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                               .withName(allOf(containsString(getTestName()),
                                               containsString("queue-sender"),
                                               containsString("amqp-bridge"),
                                               containsString(server.getNodeID().toString())))
                               .respond();
         }
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().size() > 0);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + AMQP_PORT);

         // Producer connect should create the address and initiate the bridge sender attach
         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));

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
   public void testBrokerConnectionWithMoreThanOneBridgeConfigured() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue1 = new AMQPBridgeQueuePolicyElement();
         sendToQueue1.setName("queue-policy");
         sendToQueue1.addToIncludes("source1", getTestName() + ":1");

         final AMQPBridgeQueuePolicyElement sendToQueue2 = new AMQPBridgeQueuePolicyElement();
         sendToQueue2.setName("queue-policy");
         sendToQueue2.addToIncludes("source2", getTestName() + ":2");

         final AMQPBridgeBrokerConnectionElement element1 = new AMQPBridgeBrokerConnectionElement();
         element1.setName(getTestName() + ":1");
         element1.addBridgeToQueuePolicy(sendToQueue1);

         final AMQPBridgeBrokerConnectionElement element2 = new AMQPBridgeBrokerConnectionElement();
         element2.setName(getTestName() + ":2");
         element2.addBridgeToQueuePolicy(sendToQueue2);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element1);
         amqpConnection.addElement(element2);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName() + ":1").also()
                            .withSource().withAddress("source1::" + getTestName() + ":1").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName() + ":1").setRoutingType(RoutingType.ANYCAST)
                                                                       .setAddress("source1")
                                                                       .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName() + ":1")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName() + ":2").also()
                            .withSource().withAddress("source2::" + getTestName() + ":2").also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName() + ":2").setRoutingType(RoutingType.ANYCAST)
                                                                       .setAddress("source2")
                                                                       .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName() + ":2")).isExists());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeSenderRebuildAfterRemoteDetachForResourceDeleted() throws Exception {
      doTestBridgeSenderRebuildAfterRemoteDetachForSpecificCondition(RESOURCE_DELETED.toString());
   }

   @Test
   @Timeout(20)
   public void testBridgeSenderRebuildAfterRemoteDetachForResourceNotFound() throws Exception {
      doTestBridgeSenderRebuildAfterRemoteDetachForSpecificCondition(NOT_FOUND.toString());
   }

   @Test
   @Timeout(20)
   public void testBridgeSenderRebuildAfterRemoteDetachForForcedDetach() throws Exception {
      doTestBridgeSenderRebuildAfterRemoteDetachForSpecificCondition(DETACH_FORCED.toString());
   }

   private void doTestBridgeSenderRebuildAfterRemoteDetachForSpecificCondition(String condition) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("queue-policy");
         sendToQueue.addToIncludes(getTestName(), getTestName());

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeToQueuePolicy(sendToQueue);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1);
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.remoteFlow().withLinkCredit(1).queue();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();
         peer.expectAttach().ofSender()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-sender"),
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
}
