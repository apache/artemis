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

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PREFER_SHARED_DURABLE_SUBSCRIPTIONS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.PULL_RECEIVER_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.RECEIVER_CREDITS;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.DISABLE_RECEIVER_DEMAND_TRACKING;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test AMQP Bridge between two Artemis servers.
 */
class AMQPBridgeServerToServerTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int SERVER_PORT = AMQP_PORT;
   private static final int SERVER_PORT_REMOTE = AMQP_PORT + 1;

   private static final int MIN_LARGE_MESSAGE_SIZE = 10 * 1024;

   protected ActiveMQServer remoteServer;
   protected ActiveMQServer remoteServer2; // Used in two hop tests

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      remoteServer = createServer(SERVER_PORT_REMOTE, false);

      return createServer(SERVER_PORT, false);
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("amqpMinLargeMessageSize", MIN_LARGE_MESSAGE_SIZE);
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();

      try {
         if (remoteServer != null) {
            remoteServer.stop();
            remoteServer = null;
         }
      } catch (Exception e) {
      }

      try {
         if (remoteServer2 != null) {
            remoteServer2.stop();
            remoteServer2 = null;
         }
      } catch (Exception e) {
      }
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnLocalBrokerBridgesMessagesFromRemoteAMQP() throws Exception {
      testAddresDemandOnLocalBrokerBridgesMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnLocalBrokerBridgesMessagesFromRemoteCORE() throws Exception {
      testAddresDemandOnLocalBrokerBridgesMessagesFromRemote("CORE");
   }

   private void testAddresDemandOnLocalBrokerBridgesMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromAddressPolicy(bridgeAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic(getTestName());

         final MessageConsumer consumerL = sessionL.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());

         // Captures state of JMS consumers and bridge consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         final MessageProducer producerR = sessionR.createProducer(topic);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertTrue(received instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testDivertAddressDemandOnLocalBrokerBridgesMessagesFromRemoteAMQP() throws Exception {
      testDivertAddresDemandOnLocalBrokerBridgesMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testDivertAddresDemandOnLocalBrokerBridgesMessagesFromRemoteCORE() throws Exception {
      testDivertAddresDemandOnLocalBrokerBridgesMessagesFromRemote("CORE");
   }

   private void testDivertAddresDemandOnLocalBrokerBridgesMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.addToIncludes(getTestName());
      bridgeAddressPolicy.setIncludeDivertBindings(true);

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromAddressPolicy(bridgeAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      final DivertConfiguration divert = new DivertConfiguration();
      divert.setName("test-divert");
      divert.setAddress(getTestName());
      divert.setForwardingAddress("target");
      divert.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();
      server.deployDivert(divert);
      // Currently the address must exist on the local before we will federate from the remote
      server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic target = sessionL.createTopic("target");
         final Topic source = sessionL.createTopic(getTestName());

         final MessageConsumer consumerL = sessionL.createConsumer(target);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());

         // Captures state of JMS consumers and bridge consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("target"), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         final MessageProducer producerR = sessionR.createProducer(source);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertTrue(received instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerBridgesMessagesFromRemoteAMQP() throws Exception {
      testQueueDemandOnLocalBrokerBridgesMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerBridgesMessagesFromRemoteCORE() throws Exception {
      testQueueDemandOnLocalBrokerBridgesMessagesFromRemote("CORE");
   }

   private void testQueueDemandOnLocalBrokerBridgesMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement bridgeQueuePolicy = new AMQPBridgeQueuePolicyElement();
      bridgeQueuePolicy.setName("test-policy");
      bridgeQueuePolicy.addToIncludes("#", getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromQueuePolicy(bridgeQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                   .setAddress(getTestName())
                                                                   .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue(getTestName());

         final MessageConsumer consumerL = sessionL.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final MessageProducer producerR = sessionR.createProducer(queue);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertTrue(received instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeToAddressOnLocalBrokerBridgesMessagesFromLocalAMQP() throws Exception {
      testBridgeToAddressOnLocalBrokerBridgesMessagesFromLocal("AMQP");
   }

   @Test
   @Timeout(20)
   public void testBridgeToAddressOnLocalBrokerBridgesMessagesFromLocalCore() throws Exception {
      testBridgeToAddressOnLocalBrokerBridgesMessagesFromLocal("CORE");
   }

   private void testBridgeToAddressOnLocalBrokerBridgesMessagesFromLocal(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToAddressPolicy(bridgeAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic(getTestName());

         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Remote consumer is attached and ready for the bridged message
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         // We need to add the address before the bridge will start routing message to the remote.
         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));
         // The bridge has been notified and has created a local consumer to bridge to the remote.
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         final MessageProducer producerL = sessionL.createProducer(topic);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertNotNull(received);
         assertTrue(received instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeQueueToMessageFromLocalToRemoteAMQP() throws Exception {
      testBridgeQueueToMessageFromLocalToRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testBridgeQueueToMessageFromLocalToRemoteCore() throws Exception {
      testBridgeQueueToMessageFromLocalToRemote("CORE");
   }

   public void testBridgeQueueToMessageFromLocalToRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement bridgeQueuePolicy = new AMQPBridgeQueuePolicyElement();
      bridgeQueuePolicy.setName("test-policy");
      bridgeQueuePolicy.addToIncludes("#", getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToQueuePolicy(bridgeQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // Need to define the right type on the remote in order to get expected results
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                   .setAddress(getTestName())
                                                                   .setAutoCreated(false));

      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue(getTestName());

         final MessageConsumer consumerR = sessionR.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on remote queue should trigger receiver on remote.
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).isExists());

         final MessageProducer producerL = sessionL.createProducer(queue);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertNotNull(received);
         assertTrue(received instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerBridgesMatchingFilteredMessagesFromRemoteAMQP() throws Exception {
      testQueueDemandOnLocalBrokerBridgesMatchingFilteredMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerBridgesMatchingFilteredMessagesFromRemoteCORE() throws Exception {
      testQueueDemandOnLocalBrokerBridgesMatchingFilteredMessagesFromRemote("CORE");
   }

   private void testQueueDemandOnLocalBrokerBridgesMatchingFilteredMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement bridgeQueuePolicy = new AMQPBridgeQueuePolicyElement();
      bridgeQueuePolicy.setName("test-policy");
      bridgeQueuePolicy.addToIncludes("#", getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromQueuePolicy(bridgeQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                   .setAddress(getTestName())
                                                                   .setFilterString("color='red' OR color='green' OR color='blue'")
                                                                   .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue(getTestName());

         final MessageConsumer consumerL1 = sessionL.createConsumer(queue, "color='red'");
         final MessageConsumer consumerL2 = sessionL.createConsumer(queue, "color='blue'");

         connectionL.start();
         connectionR.start();

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final MessageProducer producerR = sessionR.createProducer(queue);

         final TextMessage message1 = sessionR.createTextMessage("Hello World 1");
         message1.setStringProperty("color", "green");
         final TextMessage message2 = sessionR.createTextMessage("Hello World 2");
         message2.setStringProperty("color", "red");
         final TextMessage message3 = sessionR.createTextMessage("Hello World 3");
         message3.setStringProperty("color", "blue");

         producerR.send(message1);
         producerR.send(message2);
         producerR.send(message3);

         final Message receivedL1 = consumerL1.receive(5_000);
         assertNotNull(receivedL1);
         assertTrue(receivedL1 instanceof TextMessage);
         assertEquals("Hello World 2", ((TextMessage) receivedL1).getText());
         assertTrue(receivedL1.propertyExists("color"));
         assertEquals("red", receivedL1.getStringProperty("color"));

         final Message receivedL2 = consumerL2.receive(5_000);
         assertNotNull(receivedL2);
         assertTrue(receivedL2 instanceof TextMessage);
         assertEquals("Hello World 3", ((TextMessage) receivedL2).getText());
         assertTrue(receivedL2.propertyExists("color"));
         assertEquals("blue", receivedL2.getStringProperty("color"));

         // See if the green message is still on the remote where it should be as the
         // filter should prevent it from moving across the federation link(s)
         final MessageConsumer consumerR = sessionR.createConsumer(queue, "color='green'");

         final Message receivedR = consumerR.receive(5_000);
         assertNotNull(receivedR);
         assertTrue(receivedR instanceof TextMessage);
         assertEquals("Hello World 1", ((TextMessage) receivedR).getText());
         assertTrue(receivedR.propertyExists("color"));
         assertEquals("green", receivedR.getStringProperty("color"));
      }
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingBridgeConfigurationEachCanTakeOneMessageProduceOnLocal() throws Exception {
      doTestTwoPullConsumerOnPullingBridgeConfigurationEachCanTakeOneMessage(true);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingBridgeConfigurationEachCanTakeOneMessageProduceOnRemote() throws Exception {
      doTestTwoPullConsumerOnPullingBridgeConfigurationEachCanTakeOneMessage(false);
   }

   public void doTestTwoPullConsumerOnPullingBridgeConfigurationEachCanTakeOneMessage(boolean produceLocal) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement bridgeQueuePolicy = new AMQPBridgeQueuePolicyElement();
      bridgeQueuePolicy.setName("bridge-queue-policy");
      bridgeQueuePolicy.addToIncludes(getTestName(), getTestName());
      bridgeQueuePolicy.addProperty(RECEIVER_CREDITS, 0);         // Enable Pull mode
      bridgeQueuePolicy.addProperty(PULL_RECEIVER_BATCH_SIZE, 1); // Pull mode batch is one

      final AMQPBridgeBrokerConnectionElement element1 = new AMQPBridgeBrokerConnectionElement();
      element1.setName("Bridge-Messages-From-Remote");
      element1.addBridgeFromQueuePolicy(bridgeQueuePolicy);

      final AMQPBridgeBrokerConnectionElement element2 = new AMQPBridgeBrokerConnectionElement();
      element2.setName("Bridge-Messages-From-Local");
      element2.addBridgeFromQueuePolicy(bridgeQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection1 =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection1.setReconnectAttempts(10);// Limit reconnects
      amqpConnection1.addElement(element1);

      final AMQPBrokerConnectConfiguration amqpConnection2 =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT);
      amqpConnection2.setReconnectAttempts(10);// Limit reconnects
      amqpConnection2.addElement(element2);

      server.getConfiguration().addAMQPConnection(amqpConnection1);
      remoteServer.getConfiguration().addAMQPConnection(amqpConnection2);

      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                   .setAddress(getTestName())
                                                                   .setAutoCreated(false));
      server.start();
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));

      final int MESSAGE_COUNT = 2;
      final JmsConnectionFactory factory;
      if (produceLocal) {
         factory = new JmsConnectionFactory("amqp://localhost:" + SERVER_PORT);
      } else {
         factory = new JmsConnectionFactory("amqp://localhost:" + SERVER_PORT_REMOTE);
      }

      try (Connection connection = factory.createConnection();
           Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {

         final Queue queue = session.createQueue(getTestName());
         final MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < MESSAGE_COUNT; ++i) {
            TextMessage message = session.createTextMessage("test-message:" + i);

            message.setIntProperty("messageNo", i);

            producer.send(message);
         }
      }

      final JmsConnectionFactory factoryLocal = new JmsConnectionFactory(
         "amqp://localhost:" + SERVER_PORT + "?jms.prefetchPolicy.all=0");
      final JmsConnectionFactory factoryRemote = new JmsConnectionFactory(
         "amqp://localhost:" + SERVER_PORT_REMOTE + "?jms.prefetchPolicy.all=0");

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection();
           Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
           Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE)) {

         connectionL.start();
         connectionR.start();

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 10_000);
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).isExists(), 10_000);

         final Queue queue = sessionL.createQueue(getTestName());
         final MessageConsumer consumerL = sessionL.createConsumer(queue);
         final MessageConsumer consumerR = sessionR.createConsumer(queue);

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).getConsumerCount() >= 2, 10_000);
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).getConsumerCount() >= 2, 10_000);

         final TextMessage messageL = (TextMessage) consumerL.receive(2_000); // Read from local
         final TextMessage messageR = (TextMessage) consumerR.receive(2_000); // Read from remote after federated

         assertNotNull(messageL);
         assertNotNull(messageR);
      }
   }

   @Test
   @Timeout(20)
   public void testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient() throws Exception {
      testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient("CORE", "AMQP", false); // Tunneling doesn't matter here
   }

   @Test
   @Timeout(20)
   public void testCoreConsumerDemandOnLocalBrokerBridgesMessageFromCoreClientTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient("CORE", "CORE", true);
   }

   @Test
   @Timeout(20)
   public void testCoreConsumerDemandOnLocalBrokerBridgesMessageFromCoreClientUnTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient("CORE", "CORE", false);
   }

   @Test
   @Timeout(20)
   public void testAMQPConsumerDemandOnLocalBrokerBridgesMessageFromCoreClientTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient("AMQP", "CORE", true);
   }

   @Test
   @Timeout(20)
   public void testAMQPConsumerDemandOnLocalBrokerBridgesMessageFromCoreClientNotTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient("AMQP", "CORE", false);
   }

   private void testCoreConsumerDemandOnLocalBrokerBridgesMessageFromAMQPClient(String localProtocol,
                                                                                String remoteProtocol,
                                                                                boolean enableCoreTunneling) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement localQueuePolicy = new AMQPBridgeQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("#", getTestName());
      localQueuePolicy.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(enableCoreTunneling));

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                   .setAddress(getTestName())
                                                                   .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(localProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(remoteProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final MessageConsumer consumerL = sessionL.createConsumer(sessionL.createQueue(getTestName()));

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).isExists());

         final MessageProducer producerR = sessionR.createProducer(sessionR.createQueue(getTestName()));
         final BytesMessage message = sessionR.createBytesMessage();
         final byte[] bodyBytes = new byte[(int)(MIN_LARGE_MESSAGE_SIZE * 1.5)];

         Arrays.fill(bodyBytes, (byte)1);

         message.writeBytes(bodyBytes);
         message.setStringProperty("testProperty", "testValue");
         message.setIntProperty("testIntProperty", 42);
         message.setJMSCorrelationID("myCorrelationId");
         message.setJMSReplyTo(sessionR.createTopic("reply-topic"));

         producerR.setDeliveryMode(DeliveryMode.PERSISTENT);
         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(BytesMessage.class, received);

         final byte[] receivedBytes = new byte[bodyBytes.length];
         final BytesMessage receivedBytesMsg = (BytesMessage) received;
         receivedBytesMsg.readBytes(receivedBytes);

         assertArrayEquals(bodyBytes, receivedBytes);
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
         assertTrue(message.propertyExists("testIntProperty"));
         assertEquals(42, received.getIntProperty("testIntProperty"));
         assertEquals("myCorrelationId", received.getJMSCorrelationID());
         assertEquals("reply-topic", ((Topic) received.getJMSReplyTo()).getTopicName());
         assertEquals(DeliveryMode.PERSISTENT, received.getJMSDeliveryMode());
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeToCoreConsumerOnRemoteBrokerMessageFromAMQPClient() throws Exception {
      testBridgeToConsumerOnRemoteBrokerMessageFromLocalProducer("AMQP", "CORE", false); // Tunneling doesn't matter here
   }

   @Test
   @Timeout(20)
   public void testBridgeToCoreConsumerOnRemoteBrokerBridgesMessageFromCoreClientTunneled() throws Exception {
      testBridgeToConsumerOnRemoteBrokerMessageFromLocalProducer("CORE", "CORE", true);
   }

   @Test
   @Timeout(20)
   public void testBridgeToCoreConsumerOnRemoteBrokerBridgesMessageFromCoreClientUnTunneled() throws Exception {
      testBridgeToConsumerOnRemoteBrokerMessageFromLocalProducer("CORE", "CORE", false);
   }

   @Test
   @Timeout(20)
   public void testBridgeToAMQPConsumerOnRemoteBrokerBridgesMessageFromCoreClientTunneled() throws Exception {
      testBridgeToConsumerOnRemoteBrokerMessageFromLocalProducer("CORE", "AMQP", true);
   }

   @Test
   @Timeout(20)
   public void testBridgeToAMQPConsumerOnRemoteBrokerBridgesMessageFromCoreClientNotTunneled() throws Exception {
      testBridgeToConsumerOnRemoteBrokerMessageFromLocalProducer("CORE", "AMQP", false);
   }

   private void testBridgeToConsumerOnRemoteBrokerMessageFromLocalProducer(String localProtocol,
                                                                           String remoteProtocol,
                                                                           boolean enableCoreTunneling) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement localQueuePolicy = new AMQPBridgeQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("#", getTestName());
      localQueuePolicy.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(enableCoreTunneling));

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                   .setAddress(getTestName())
                                                                   .setAutoCreated(false));
      server.start();
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(localProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(remoteProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final MessageConsumer consumerR = sessionR.createConsumer(sessionR.createQueue(getTestName()));

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).isExists());

         final MessageProducer producerL = sessionL.createProducer(sessionL.createQueue(getTestName()));
         final BytesMessage message = sessionL.createBytesMessage();
         final byte[] bodyBytes = new byte[(int)(MIN_LARGE_MESSAGE_SIZE * 1.5)];

         Arrays.fill(bodyBytes, (byte)1);

         message.writeBytes(bodyBytes);
         message.setStringProperty("testProperty", "testValue");
         message.setIntProperty("testIntProperty", 42);
         message.setJMSCorrelationID("myCorrelationId");
         message.setJMSReplyTo(sessionL.createTopic("reply-topic"));

         producerL.setDeliveryMode(DeliveryMode.PERSISTENT);
         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(BytesMessage.class, received);

         final byte[] receivedBytes = new byte[bodyBytes.length];
         final BytesMessage receivedBytesMsg = (BytesMessage) received;
         receivedBytesMsg.readBytes(receivedBytes);

         assertArrayEquals(bodyBytes, receivedBytes);
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
         assertTrue(message.propertyExists("testIntProperty"));
         assertEquals(42, received.getIntProperty("testIntProperty"));
         assertEquals("myCorrelationId", received.getJMSCorrelationID());
         assertEquals("reply-topic", ((Topic) received.getJMSReplyTo()).getTopicName());
         assertEquals(DeliveryMode.PERSISTENT, received.getJMSDeliveryMode());
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeToAddressPolicyDoesNotAccumulateMessagesByDefaultWhenConnectionDrops() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToAddressPolicy(bridgeAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // We need to add the address before the bridge will start routing message to the remote.
      server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));
      // The bridge has been notified and has created a local consumer to bridge to the remote.
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1, 5000, 50);

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "failover:(amqp://localhost:" + SERVER_PORT_REMOTE + ")");

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic(getTestName());

         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Remote consumer is attached and ready for the bridged message
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);

         final MessageProducer producerL = sessionL.createProducer(topic);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue-1");

         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertNotNull(received);
         assertTrue(received instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("testProperty"));
         assertEquals("testValue-1", received.getStringProperty("testProperty"));

         remoteServer.stop();

         // The bridge has seen the connection drop and removed the local temporary address binding
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 0, 5000, 50);

         message.setStringProperty("testProperty", "testValue-2");

         producerL.send(message);

         remoteServer.start();

         // Remote consumer is attached and ready for the next bridged message
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);

         // Connection has been restored to the remote and the bridge created a new binding to send to the remote
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1, 5000, 50);

         assertNull(consumerR.receiveNoWait());

         message.setStringProperty("testProperty", "testValue-3");

         producerL.send(message);

         final Message receivedAfter = consumerR.receive(5_000);
         assertNotNull(receivedAfter);
         assertTrue(receivedAfter instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) receivedAfter).getText());
         assertTrue(receivedAfter.propertyExists("testProperty"));
         assertEquals("testValue-3", receivedAfter.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testDurableBridgeToAddressPolicyAccumulatesMessagesWhenConnectionDrops() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.setUseDurableSubscriptions(true);
      bridgeAddressPolicy.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToAddressPolicy(bridgeAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // We need to add the address before the bridge will start routing message to the remote.
      server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));
      // The bridge has been notified and has created a local consumer to bridge to the remote.
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1, 5000, 50);

      // Remote server needs a durable queue to read messages from remote immediately after restart
      remoteServer.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                                   .setAddress(getTestName())
                                                                   .setAutoCreated(false)
                                                                   .setDurable(true));

      final SimpleString subscriptionQueueName = server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0);

      assertNotNull(subscriptionQueueName);
      assertTrue(subscriptionQueueName.startsWith(SimpleString.of("amqp-bridge-")));

      final org.apache.activemq.artemis.core.server.Queue subscriptionQueue = server.locateQueue(subscriptionQueueName);

      assertNotNull(subscriptionQueue);

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connectionL = factoryLocal.createConnection()) {
         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = sessionL.createTopic(getTestName());

         connectionL.start();

         // Remote consumer is attached and ready for the bridged message
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);

         final MessageProducer producerL = sessionL.createProducer(topic);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue-1");
         producerL.send(message);

         Wait.assertEquals(0, () -> subscriptionQueue.getMessageCount());
         Wait.assertEquals(1, () -> subscriptionQueue.getMessagesAcknowledged());

         remoteServer.stop();

         message.setStringProperty("testProperty", "testValue-2");
         producerL.send(message);

         // The bridge has seen the connection drop but the durable local binding remains
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);

         remoteServer.start();

         // Remote consumer is attached and ready for the next bridged message
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);

         message.setStringProperty("testProperty", "testValue-3");
         producerL.send(message);

         final SimpleString remoteBindingName = remoteServer.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0);
         assertNotNull(remoteBindingName);

         final org.apache.activemq.artemis.core.server.Queue remoteQueueBinding = remoteServer.locateQueue(remoteBindingName);
         assertNotNull(remoteQueueBinding);

         Wait.assertEquals(3L, () -> remoteQueueBinding.getMessageCount(), 5_000, 100);
      }
   }

   @Test
   @Timeout(20)
   public void testDurableReceiveFromAddressDropsOldMessagesOnSubscriptionUpdates() throws Exception {
      logger.info("Test started: {}", getTestName());

      final String filterStringA = "color='red'";
      final String filterStringB = "color='green'";

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy_1 = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy_1.setName("test-policy");
      bridgeAddressPolicy_1.setUseDurableSubscriptions(true);
      bridgeAddressPolicy_1.setFilter(filterStringA);
      bridgeAddressPolicy_1.addToIncludes(getTestName());

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy_2 = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy_2.setName("test-policy");
      bridgeAddressPolicy_2.setUseDurableSubscriptions(true);
      bridgeAddressPolicy_2.setFilter(filterStringB);
      bridgeAddressPolicy_2.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromAddressPolicy(bridgeAddressPolicy_1);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // Create an address with a binding to simulate demand from a consumer
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));
      // Wait for the bridge to form to the remote and capture the durable subscription name
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1, 5_000, 50);

      final String subscriptionQueueName = remoteServer.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(subscriptionQueueName);
      assertTrue(subscriptionQueueName.contains("amqp-bridge-"));
      assertTrue(subscriptionQueueName.contains(getTestName()));

      final org.apache.activemq.artemis.core.server.Queue subscriptionQueue = remoteServer.locateQueue(subscriptionQueueName);

      assertNotNull(subscriptionQueue);
      Wait.assertEquals(1L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      assertTrue(subscriptionQueue.isDurable());
      assertEquals(filterStringA, subscriptionQueue.getFilter().getFilterString().toString());

      final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
         server.getRemotingService().getProtocolFactoryMap().get("AMQP");
      assertNotNull(protocolFactory);

      final AMQPBridgeBrokerConnectionElement updatedElement = new AMQPBridgeBrokerConnectionElement();
      updatedElement.setName(getTestName());
      updatedElement.addBridgeFromAddressPolicy(bridgeAddressPolicy_2);

      final AMQPBrokerConnectConfiguration updatedAmqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      updatedAmqpConnection.setReconnectAttempts(10);// Limit reconnects
      updatedAmqpConnection.setRetryInterval(50);
      updatedAmqpConnection.addElement(updatedElement);

      server.getConfiguration().getAMQPConnection().clear();
      server.getConfiguration().addAMQPConnection(updatedAmqpConnection);

      protocolFactory.updateProtocolServices(server, Collections.emptyList());

      Wait.assertTrue(() -> {
         return remoteServer.locateQueue(subscriptionQueueName).getFilter().getFilterString().toString().equals(filterStringB);
      });

      // There should be only one binding as the old one should have been replaced.
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1, 5_000, 50);
   }

   @Test
   @Timeout(20)
   public void testDurableAddressSubscriptionRecoveredOnRestart() throws Exception {
      logger.info("Test started: {}", getTestName());

      final String filterString = "color='red'";

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.setUseDurableSubscriptions(true);
      bridgeAddressPolicy.setFilter(filterString);
      bridgeAddressPolicy.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromAddressPolicy(bridgeAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10); // Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // Create an address with a binding to simulate demand from a consumer
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));
      // Wait for the bridge to form to the remote and capture the durable subscription name
      Wait.assertEquals(1L, () -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size(), 5_000, 50);

      final String subscriptionQueueName = remoteServer.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(subscriptionQueueName);
      assertTrue(subscriptionQueueName.contains("amqp-bridge-"));
      assertTrue(subscriptionQueueName.contains(getTestName()));

      final org.apache.activemq.artemis.core.server.Queue subscriptionQueue = remoteServer.locateQueue(subscriptionQueueName);

      assertNotNull(subscriptionQueue);
      Wait.assertEquals(1L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      assertTrue(subscriptionQueue.isDurable());
      assertEquals(filterString, subscriptionQueue.getFilter().getFilterString().toString());

      server.stop();

      Wait.assertEquals(1L, () -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size(), 5_000, 50);
      Wait.assertEquals(0L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);

      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connection = factoryRemote.createConnection()) {
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageProducer producerL = session.createProducer(topic);
         final TextMessage message = session.createTextMessage("Hello World");

         message.setStringProperty("color", "green");
         producerL.send(message);
         message.setStringProperty("color", "red");
         producerL.send(message);

         Wait.assertEquals(1L, () -> subscriptionQueue.getMessageCount(), 5_000, 100);
      }

      server.start();

      // Server should re-attach and recover the subscription and take the message
      Wait.assertEquals(1L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      Wait.assertEquals(0L, () -> subscriptionQueue.getMessageCount(), 5_000, 100);

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connection = factoryLocal.createConnection()) {
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName() + "::" + getTestName()); // Access our pre-created queue via FQQN
         final MessageConsumer consumer = session.createConsumer(topic);

         connection.start();

         final Message receivedAfter = consumer.receive(5_000);

         assertNotNull(receivedAfter);
         assertTrue(receivedAfter instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) receivedAfter).getText());
         assertTrue(receivedAfter.propertyExists("color"));
         assertEquals("red", receivedAfter.getStringProperty("color"));
      }
   }

   @Test
   @Timeout(20)
   public void testSharedDurableAddressSubscriptionRecoveredOnRestart() throws Exception {
      logger.info("Test started: {}", getTestName());

      final String filterString = "color='red'";

      final AMQPBridgeAddressPolicyElement bridgeAddressPolicy = new AMQPBridgeAddressPolicyElement();
      bridgeAddressPolicy.setName("test-policy");
      bridgeAddressPolicy.setUseDurableSubscriptions(true);
      bridgeAddressPolicy.setFilter(filterString);
      bridgeAddressPolicy.addToIncludes(getTestName());

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromAddressPolicy(bridgeAddressPolicy);
      element.addProperty(PREFER_SHARED_DURABLE_SUBSCRIPTIONS, "true");

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10); // Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // Create an address with a binding to simulate demand from a consumer
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));
      // Wait for the bridge to form to the remote and capture the durable subscription name
      Wait.assertEquals(1L, () -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size(), 500_000, 50);

      // The actual subscription queue for the "shared" bridge receivers should be a stable queue
      final String subscriptionQueueName = remoteServer.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(subscriptionQueueName);
      assertTrue(subscriptionQueueName.contains("amqp-bridge-"));
      assertTrue(subscriptionQueueName.contains(getTestName()));

      final org.apache.activemq.artemis.core.server.Queue subscriptionQueue = remoteServer.locateQueue(subscriptionQueueName);

      assertNotNull(subscriptionQueue);
      Wait.assertEquals(1L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      assertTrue(subscriptionQueue.isDurable());
      assertEquals(filterString, subscriptionQueue.getFilter().getFilterString().toString());

      server.stop();

      Wait.assertEquals(1L, () -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size(), 5_000, 50);
      Wait.assertEquals(0L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);

      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connection = factoryRemote.createConnection()) {
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageProducer producerL = session.createProducer(topic);
         final TextMessage message = session.createTextMessage("Hello World");

         message.setStringProperty("color", "green");
         producerL.send(message);
         message.setStringProperty("color", "red");
         producerL.send(message);

         Wait.assertEquals(1L, () -> subscriptionQueue.getMessageCount(), 5_000, 100);
      }

      server.start();

      // Server should re-attach and recover the subscription and take the message
      Wait.assertEquals(1L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      Wait.assertEquals(0L, () -> subscriptionQueue.getMessageCount(), 5_000, 100);

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connection = factoryLocal.createConnection()) {
         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName() + "::" + getTestName()); // Access our pre-created queue via FQQN
         final MessageConsumer consumer = session.createConsumer(topic);

         connection.start();

         final Message receivedAfter = consumer.receive(5_000);

         assertNotNull(receivedAfter);
         assertTrue(receivedAfter instanceof TextMessage);
         assertEquals("Hello World", ((TextMessage) receivedAfter).getText());
         assertTrue(receivedAfter.propertyExists("color"));
         assertEquals("red", receivedAfter.getStringProperty("color"));

         final TextMessage message = session.createTextMessage("Hello Again");
         final MessageProducer producerL = session.createProducer(topic);

         message.setStringProperty("color", "red");
         producerL.send(message);

         final Message receiveAnother = consumer.receive(5_000);

         assertNotNull(receiveAnother);
         assertTrue(receiveAnother instanceof TextMessage);
         assertEquals("Hello Again", ((TextMessage) receiveAnother).getText());
         assertTrue(receiveAnother.propertyExists("color"));
         assertEquals("red", receiveAnother.getStringProperty("color"));
      }
   }

   @Test
   @Timeout(20)
   public void testBrigeToFQQNUsedToDrainDurableConsumerSubscriptionQueue() throws Exception {
      logger.info("Test started: {}", getTestName());

      final long MESSAGE_COUNT = 10;

      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connection = factoryLocal.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageConsumer consumer = session.createDurableConsumer(topic, "bridge-sub");
         final MessageProducer producer = session.createProducer(topic);

         consumer.close();

         for (int i = 0; i < MESSAGE_COUNT; ++i) {
            final TextMessage message = session.createTextMessage("Message:" + i);

            message.setStringProperty("color", "red");

            producer.send(message);
         }
      }

      final String subscriptionQueueName = server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(subscriptionQueueName);

      final org.apache.activemq.artemis.core.server.Queue subscriptionQueue = server.locateQueue(subscriptionQueueName);

      assertNotNull(subscriptionQueue);
      Wait.assertEquals(0L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      Wait.assertEquals(MESSAGE_COUNT, () -> subscriptionQueue.getMessageCount(), 5_000, 100);
      assertTrue(subscriptionQueue.isDurable());

      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connection = factoryRemote.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageConsumer consumer = session.createDurableConsumer(topic, "bridge-sub");

         consumer.close();
      }

      final String remoteSubscriptionQueueName = remoteServer.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(remoteSubscriptionQueueName);

      final org.apache.activemq.artemis.core.server.Queue remoteSubscriptionQueue = remoteServer.locateQueue(subscriptionQueueName);

      assertNotNull(remoteSubscriptionQueue);
      Wait.assertEquals(0L, () -> remoteSubscriptionQueue.getConsumerCount(), 5_000, 100);
      Wait.assertEquals(0L, () -> remoteSubscriptionQueue.getMessageCount(), 5_000, 100);
      assertTrue(remoteSubscriptionQueue.isDurable());

      assertEquals(subscriptionQueueName, remoteSubscriptionQueueName);

      final AMQPBridgeQueuePolicyElement bridgePolicy = new AMQPBridgeQueuePolicyElement();
      bridgePolicy.setName("test-policy");
      bridgePolicy.addToIncludes(getTestName(), subscriptionQueueName);
      bridgePolicy.setRemoteAddress(getTestName() + "::" + subscriptionQueueName); // Direct the policy on where to put the messages

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToQueuePolicy(bridgePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10); // Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().getAMQPConnection().clear();
      server.getConfiguration().addAMQPConnection(amqpConnection);

      final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
         server.getRemotingService().getProtocolFactoryMap().get("AMQP");
      assertNotNull(protocolFactory);
      protocolFactory.updateProtocolServices(server, new ArrayList<>());

      Wait.assertEquals(MESSAGE_COUNT, () -> remoteSubscriptionQueue.getMessageCount(), 5_000, 100);
      Wait.assertEquals(0L, () -> subscriptionQueue.getMessageCount(), 5_000, 100);

      try (Connection connection = factoryRemote.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageConsumer consumer = session.createDurableConsumer(topic, "bridge-sub");

         for (int i = 0; i < MESSAGE_COUNT; ++i) {
            final TextMessage message = (TextMessage) consumer.receive(100);

            assertEquals("Message:" + i, message.getText());
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBrigeFromQueueUsedToDrainDurableConsumerSubscriptionQueue() throws Exception {
      logger.info("Test started: {}", getTestName());

      final long MESSAGE_COUNT = 10;

      remoteServer.start();
      server.start();

      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connection = factoryRemote.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageConsumer consumer = session.createDurableConsumer(topic, "bridge-sub");
         final MessageProducer producer = session.createProducer(topic);

         consumer.close();

         for (int i = 0; i < MESSAGE_COUNT; ++i) {
            final TextMessage message = session.createTextMessage("Message:" + i);

            message.setStringProperty("color", "red");

            producer.send(message);
         }
      }

      final String remoteSubscriptionQueueName = remoteServer.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(remoteSubscriptionQueueName);

      final org.apache.activemq.artemis.core.server.Queue remoteSubscriptionQueue = remoteServer.locateQueue(remoteSubscriptionQueueName);

      assertNotNull(remoteSubscriptionQueue);
      Wait.assertEquals(0L, () -> remoteSubscriptionQueue.getConsumerCount(), 5_000, 100);
      Wait.assertEquals(MESSAGE_COUNT, () -> remoteSubscriptionQueue.getMessageCount(), 5_000, 100);
      assertTrue(remoteSubscriptionQueue.isDurable());

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connection = factoryLocal.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageConsumer consumer = session.createDurableConsumer(topic, "bridge-sub");

         consumer.close();
      }

      final String subscriptionQueueName = server.bindingQuery(SimpleString.of(getTestName())).getQueueNames().get(0).toString();

      assertNotNull(subscriptionQueueName);

      final org.apache.activemq.artemis.core.server.Queue subscriptionQueue = server.locateQueue(subscriptionQueueName);

      assertNotNull(subscriptionQueue);
      Wait.assertEquals(0L, () -> subscriptionQueue.getConsumerCount(), 5_000, 100);
      Wait.assertEquals(0L, () -> subscriptionQueue.getMessageCount(), 5_000, 100);
      assertTrue(subscriptionQueue.isDurable());

      assertEquals(subscriptionQueueName, subscriptionQueueName);

      final AMQPBridgeQueuePolicyElement bridgePolicy = new AMQPBridgeQueuePolicyElement();
      bridgePolicy.setName("test-policy");
      bridgePolicy.addToIncludes(getTestName(), subscriptionQueueName);
      bridgePolicy.setRemoteAddress(getTestName() + "::" + subscriptionQueueName); // Direct the policy on where to get the messages

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeFromQueuePolicy(bridgePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10); // Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      server.getConfiguration().getAMQPConnection().clear();
      server.getConfiguration().addAMQPConnection(amqpConnection);

      final ProtonProtocolManagerFactory protocolFactory = (ProtonProtocolManagerFactory)
         server.getRemotingService().getProtocolFactoryMap().get("AMQP");
      assertNotNull(protocolFactory);
      protocolFactory.updateProtocolServices(server, new ArrayList<>());

      try (Connection connection = factoryLocal.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTestName());
         final MessageConsumer consumer = session.createDurableConsumer(topic, "bridge-sub");

         Wait.assertEquals(MESSAGE_COUNT, () -> subscriptionQueue.getDeliveringCount(), 5_000, 100);
         Wait.assertEquals(0L, () -> remoteSubscriptionQueue.getMessageCount(), 5_000, 100);

         for (int i = 0; i < MESSAGE_COUNT; ++i) {
            final TextMessage message = (TextMessage) consumer.receive(100);

            assertEquals("Message:" + i, message.getText());
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeToRemoteDLQThatHasMessagesSentToMulticastAddressOriginially() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement bridgePolicy = new AMQPBridgeQueuePolicyElement();
      bridgePolicy.setName("drain-remote-dlq-policy");
      bridgePolicy.addToIncludes(getDeadLetterAddress(), getDeadLetterAddress());
      bridgePolicy.setRemoteAddress(getDeadLetterAddress() + "::" + getDeadLetterAddress()); // Direct target the desired FQQN

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName(getTestName());
      element.addBridgeToQueuePolicy(bridgePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10); // Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      remoteServer.start();
      server.getConfiguration().addAMQPConnection(amqpConnection);
      server.start();

      // Configure defaults
      createAddressAndQueues(remoteServer);
      createAddressAndQueues(server);

      final AmqpClient client = createAmqpClient();
      final AmqpConnection clientConnection = addConnection(client.connect());
      final AmqpSession clientSession = clientConnection.createSession();
      final AmqpReceiver receiver = clientSession.createReceiver(getTopicName());

      receiver.flow(1);

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connection = factoryLocal.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTopicName());
         final MessageProducer producer = session.createProducer(topic);

         // Sends a Topic message with Qpid JMS so that source message has the AMQP JMS mapping headers.
         producer.send(session.createTextMessage("Message:"));
      }

      final AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      received.reject(); // Terminal outcome should be DLQ'd

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getDeadLetterAddress())).isExists(), 1_000);
      Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getDeadLetterAddress())).isExists(), 1_000);
      Wait.assertEquals(1L, () -> remoteServer.queueQuery(SimpleString.of(getDeadLetterAddress())).getMessageCount(), 5_000, 10);
   }

   @Test
   @Timeout(20)
   public void testBridgeFromRemoteDLQThatHasMessagesSentToMulticastAddressOriginially() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPBridgeQueuePolicyElement bridgePolicy = new AMQPBridgeQueuePolicyElement();
      bridgePolicy.setName("drain-remote-dlq-policy");
      bridgePolicy.addToIncludes(getDeadLetterAddress(), getDeadLetterAddress());
      bridgePolicy.addProperty(DISABLE_RECEIVER_DEMAND_TRACKING, "true");

      final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
      element.setName("From-Server1-DLQ");
      element.addBridgeFromQueuePolicy(bridgePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT);
      amqpConnection.setReconnectAttempts(10); // Limit reconnects
      amqpConnection.setRetryInterval(50);
      amqpConnection.addElement(element);

      remoteServer.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      // Configure defaults
      createAddressAndQueues(remoteServer);
      createAddressAndQueues(server);

      final AmqpClient client = createAmqpClient();
      final AmqpConnection clientConnection = addConnection(client.connect());
      final AmqpSession clientSession = clientConnection.createSession();
      final AmqpReceiver receiver = clientSession.createReceiver(getTopicName());

      receiver.flow(1);

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);

      try (Connection connection = factoryLocal.createConnection()) {
         connection.setClientID("test-brigdes");
         connection.start();

         final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTopicName());
         final MessageProducer producer = session.createProducer(topic);

         // Sends a Topic message with Qpid JMS so that source message has the AMQP JMS mapping headers.
         producer.send(session.createTextMessage("Message:"));
      }

      final AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received);
      received.reject(); // Terminal outcome should be DLQ'd

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getDeadLetterAddress())).isExists(), 1_000);
      Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getDeadLetterAddress())).isExists(), 1_000);
      Wait.assertEquals(1L, () -> remoteServer.queueQuery(SimpleString.of(getDeadLetterAddress())).getMessageCount(), 5_000, 10);
   }
}
