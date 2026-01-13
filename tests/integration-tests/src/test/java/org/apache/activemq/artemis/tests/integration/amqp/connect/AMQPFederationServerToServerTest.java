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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_ADDRESS_BINDING_FILTERS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.IGNORE_QUEUE_CONSUMER_PRIORITIES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.PULL_RECEIVER_BATCH_SIZE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.RECEIVER_CREDITS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
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
 * Test AMQP federation between two servers.
 */
public class AMQPFederationServerToServerTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int SERVER_PORT = AMQP_PORT;
   private static final int SERVER_PORT_REMOTE = AMQP_PORT + 1;
   private static final int SERVER2_PORT_REMOTE = AMQP_PORT + 2;

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
   public void testAddresDemandOnLocalBrokerFederatesMessagesFromRemoteAMQP() throws Exception {
      testAddresDemandOnLocalBrokerFederatesMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnLocalBrokerFederatesMessagesFromRemoteCORE() throws Exception {
      testAddresDemandOnLocalBrokerFederatesMessagesFromRemote("CORE");
   }

   private void testAddresDemandOnLocalBrokerFederatesMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("test-policy");
      localAddressPolicy.addToIncludes("test");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalAddressPolicy(localAddressPolicy);

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

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("test")).isExists());
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of("test")).isExists());

         // Captures state of JMS consumers and federation consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test"), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of("test"), false).getQueueNames().size() >= 1);

         final MessageProducer producerR = sessionR.createProducer(topic);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testDivertAddressDemandOnLocalBrokerFederatesMessagesFromRemoteAMQP() throws Exception {
      testDivertAddresDemandOnLocalBrokerFederatesMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testDivertAddresDemandOnLocalBrokerFederatesMessagesFromRemoteCORE() throws Exception {
      testDivertAddresDemandOnLocalBrokerFederatesMessagesFromRemote("CORE");
   }

   private void testDivertAddresDemandOnLocalBrokerFederatesMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("test-policy");
      localAddressPolicy.addToIncludes("source");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);
      localAddressPolicy.setEnableDivertBindings(true);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalAddressPolicy(localAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      final DivertConfiguration divert = new DivertConfiguration();
      divert.setName("test-divert");
      divert.setAddress("source");
      divert.setForwardingAddress("target");
      divert.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();
      server.deployDivert(divert);
      // Currently the address must exist on the local before we will federate from the remote
      server.addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic target = sessionL.createTopic("target");
         final Topic source = sessionL.createTopic("source");

         final MessageConsumer consumerL = sessionL.createConsumer(target);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of("source")).isExists());

         // Captures state of JMS consumers and federation consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("target"), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of("source"), false).getQueueNames().size() >= 1);

         final MessageProducer producerR = sessionR.createProducer(source);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerFederatesMessagesFromRemoteAMQP() throws Exception {
      testQueueDemandOnLocalBrokerFederatesMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerFederatesMessagesFromRemoteCORE() throws Exception {
      testQueueDemandOnLocalBrokerFederatesMessagesFromRemote("CORE");
   }

   private void testQueueDemandOnLocalBrokerFederatesMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("#", "test");

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress("test")
                                                             .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue("test");

         final MessageConsumer consumerL = sessionL.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());

         final MessageProducer producerR = sessionR.createProducer(queue);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnRemoteBrokerFederatesMessagesFromLocalAMQP() throws Exception {
      testAddresDemandOnRemoteBrokerFederatesMessagesFromLocal("AMQP");
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnRemoteBrokerFederatesMessagesFromLocalCORE() throws Exception {
      testAddresDemandOnRemoteBrokerFederatesMessagesFromLocal("CORE");
   }

   private void testAddresDemandOnRemoteBrokerFederatesMessagesFromLocal(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement remoteAddressPolicy = new AMQPFederationAddressPolicyElement();
      remoteAddressPolicy.setName("test-policy");
      remoteAddressPolicy.addToIncludes("test");
      remoteAddressPolicy.setAutoDelete(false);
      remoteAddressPolicy.setAutoDeleteDelay(-1L);
      remoteAddressPolicy.setAutoDeleteMessageCount(-1L);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addRemoteAddressPolicy(remoteAddressPolicy);

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

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("test")).isExists());
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of("test")).isExists());

         // Captures state of JMS consumers and federation consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test"), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of("test"), false).getQueueNames().size() >= 1);

         final MessageProducer producerL = sessionL.createProducer(topic);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnRemoteWithRemoteConfigrationLeadsToMessageBeingFederatedAMQP() throws Exception {
      testQueueDemandOnRemoteWithRemoteConfigrationLeadsToMessageBeingFederated("AMQP");
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnRemoteWithRemoteConfigrationLeadsToMessageBeingFederatedCORE() throws Exception {
      testQueueDemandOnRemoteWithRemoteConfigrationLeadsToMessageBeingFederated("CORE");
   }

   public void testQueueDemandOnRemoteWithRemoteConfigrationLeadsToMessageBeingFederated(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement remoteQueuePolicy = new AMQPFederationQueuePolicyElement();
      remoteQueuePolicy.setName("test-policy");
      remoteQueuePolicy.addToIncludes("#", "test");

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addRemoteQueuePolicy(remoteQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                       .setAddress("test")
                                                       .setAutoCreated(false));

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue("test");

         final MessageConsumer consumerR = sessionR.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on remote queue should trigger receiver on remote.
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of("test")).isExists());

         final MessageProducer producerL = sessionL.createProducer(queue);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testDivertAddresDemandOnRemoteBrokerFederatesMessagesFromLocalAMQP() throws Exception {
      testDivertAddresDemandOnRemoteBrokerFederatesMessagesFromLocal("AMQP");
   }

   @Test
   @Timeout(20)
   public void testDivertAddresDemandOnRemoteBrokerFederatesMessagesFromLocalCORE() throws Exception {
      testDivertAddresDemandOnRemoteBrokerFederatesMessagesFromLocal("CORE");
   }

   private void testDivertAddresDemandOnRemoteBrokerFederatesMessagesFromLocal(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement remoteAddressPolicy = new AMQPFederationAddressPolicyElement();
      remoteAddressPolicy.setName("test-policy");
      remoteAddressPolicy.addToIncludes("source");
      remoteAddressPolicy.setAutoDelete(false);
      remoteAddressPolicy.setAutoDeleteDelay(-1L);
      remoteAddressPolicy.setAutoDeleteMessageCount(-1L);
      remoteAddressPolicy.setEnableDivertBindings(true);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addRemoteAddressPolicy(remoteAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      final DivertConfiguration divert = new DivertConfiguration();
      divert.setName("test-divert");
      divert.setAddress("source");
      divert.setForwardingAddress("target");
      divert.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);

      remoteServer.start();
      remoteServer.deployDivert(divert);
      // Currently the address must exist on the local before we will federate from the remote
      // and in this case since we are instructing the remote to federate from us the address must
      // exist on the remote for that to happen.
      remoteServer.addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));
      server.getConfiguration().addAMQPConnection(amqpConnection);
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic target = sessionL.createTopic("target");
         final Topic source = sessionL.createTopic("source");

         final MessageConsumer consumerR = sessionR.createConsumer(target);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("source")).isExists());

         // Captures state of JMS consumers and federation consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("source"), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of("target"), false).getQueueNames().size() >= 1);

         final MessageProducer producerL = sessionL.createProducer(source);
         final TextMessage message = sessionL.createTextMessage("Hello World");

         message.setStringProperty("testProperty", "testValue");

         producerL.send(message);

         final Message received = consumerR.receive(5_000);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Hello World", ((TextMessage) received).getText());
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemoteAMQP() throws Exception {
      // core tunneling shouldn't affect the AMQP message that cross
      testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemote("AMQP", true);
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemoteCORENoTunneling() throws Exception {
      // core message should be converted to AMQP and back.
      testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemote("CORE", false);
   }

   @Test
   @Timeout(20)
   public void testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemoteCOREWithTunneling() throws Exception {
      // core messages should be tunneled in an AMQP message an then read back
      testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemote("CORE", true);
   }

   private void testAddresDemandOnLocalBrokerFederatesLargeMessagesFromRemote(String clientProtocol, boolean enableCoreTunneling) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("test-policy");
      localAddressPolicy.addToIncludes("test");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);
      localAddressPolicy.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(enableCoreTunneling));

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalAddressPolicy(localAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote;

      if (clientProtocol.equals("CORE")) {
         factoryRemote = CFUtil.createConnectionFactory(
            clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE + "?minLargeMessageSize=" + MIN_LARGE_MESSAGE_SIZE);
      } else {
         factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);
      }

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("test")).isExists());
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of("test")).isExists());

         // Captures state of JMS consumers and federation consumers attached on each node
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test"), false).getQueueNames().size() >= 1);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of("test"), false).getQueueNames().size() >= 1);

         final MessageProducer producerR = sessionR.createProducer(topic);
         final BytesMessage message = sessionR.createBytesMessage();
         final byte[] bodyBytes = new byte[(int)(MIN_LARGE_MESSAGE_SIZE * 1.5)];

         Arrays.fill(bodyBytes, (byte)1);

         message.writeBytes(bodyBytes);
         message.setStringProperty("testProperty", "testValue");

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
      }
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemoteAMQP() throws Exception {
      // core tunneling shouldn't affect the AMQP message that cross
      testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemote("AMQP", true);
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemoteCORENoTunneling() throws Exception {
      // core message should be converted to AMQP and back.
      testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemote("CORE", false);
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemoteCOREWithTunneling() throws Exception {
      // core messages should be tunneled in an AMQP message an then read back
      testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemote("CORE", true);
   }

   private void testQueueDemandOnLocalBrokerFederatesLargeMessagesFromRemote(String clientProtocol, boolean enableCoreTunneling) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("test", "test");
      localQueuePolicy.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(enableCoreTunneling));

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress("test")
                                                             .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote;

      if (clientProtocol.equals("CORE")) {
         factoryRemote = CFUtil.createConnectionFactory(
            clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE + "?minLargeMessageSize=" + MIN_LARGE_MESSAGE_SIZE);
      } else {
         factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);
      }

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue("test");

         final MessageConsumer consumerL = sessionL.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of("test")).isExists());

         final MessageProducer producerR = sessionR.createProducer(queue);
         final BytesMessage message = sessionR.createBytesMessage();
         final byte[] bodyBytes = new byte[(int)(MIN_LARGE_MESSAGE_SIZE * 1.5)];

         Arrays.fill(bodyBytes, (byte)1);

         message.writeBytes(bodyBytes);
         message.setStringProperty("testProperty", "testValue");

         producerR.send(message);

         final Message received = consumerL.receive(500_000);
         assertNotNull(received);
         assertInstanceOf(BytesMessage.class, received);

         final byte[] receivedBytes = new byte[bodyBytes.length];
         final BytesMessage receivedBytesMsg = (BytesMessage) received;
         receivedBytesMsg.readBytes(receivedBytes);

         assertArrayEquals(bodyBytes, receivedBytes);
         assertTrue(message.propertyExists("testProperty"));
         assertEquals("testValue", received.getStringProperty("testProperty"));
      }
   }

   @Test
   @Timeout(20)
   public void testCoreMessageCrossingAddressWithThreeBrokersWithoutTunneling() throws Exception {
      doTestCoreMessageCrossingAddressWithThreeBrokers(false);
   }

   @Test
   @Timeout(20)
   public void testCoreMessageCrossingAddressWithThreeBrokersWithTunneling() throws Exception {
      doTestCoreMessageCrossingAddressWithThreeBrokers(true);
   }

   private void doTestCoreMessageCrossingAddressWithThreeBrokers(boolean enableCoreTunneling) throws Exception {
      logger.info("Test started: {}", getTestName());

      // Create a ring of federated brokers on a target address, messages sent to the address
      // on any given broke should traverse the ring size minus one as we never want a loop so
      // if the ring is three brokers the max hops should be set to two.

      remoteServer2 = createServer(SERVER2_PORT_REMOTE, false);

      final String ADDRESS_NAME = "target";
      final SimpleString ADDRESS_NAME_SS = SimpleString.of(ADDRESS_NAME);

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("two-hop-policy");
      localAddressPolicy.addToIncludes(ADDRESS_NAME);
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);
      localAddressPolicy.setMaxHops(2);
      localAddressPolicy.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(enableCoreTunneling));

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalAddressPolicy(localAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection1 =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection1.setReconnectAttempts(10);// Limit reconnects
      amqpConnection1.setRetryInterval(100);
      amqpConnection1.addElement(element);

      final AMQPBrokerConnectConfiguration amqpConnection2 =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER2_PORT_REMOTE);
      amqpConnection2.setReconnectAttempts(10);// Limit reconnects
      amqpConnection1.setRetryInterval(100);
      amqpConnection2.addElement(element);

      final AMQPBrokerConnectConfiguration amqpConnection3 =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT);
      amqpConnection3.setReconnectAttempts(10);// Limit reconnects
      amqpConnection1.setRetryInterval(100);
      amqpConnection3.addElement(element);

      // This is our ring, broker1 -> broker2-> broker3 -> broker1
      server.getConfiguration().addAMQPConnection(amqpConnection1);
      remoteServer.getConfiguration().addAMQPConnection(amqpConnection2);
      remoteServer2.getConfiguration().addAMQPConnection(amqpConnection3);

      server.start();
      remoteServer.start();
      remoteServer2.start();

      final ConnectionFactory factory1 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factory2 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + SERVER_PORT_REMOTE);
      final ConnectionFactory factory3 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + SERVER2_PORT_REMOTE);

      try (Connection connection1 = factory1.createConnection();
           Connection connection2 = factory2.createConnection();
           Connection connection3 = factory3.createConnection()) {

         final Session session1 = connection1.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session session2 = connection2.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session session3 = connection3.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = session1.createTopic(ADDRESS_NAME);

         final MessageConsumer consumer1 = session1.createConsumer(topic);
         final MessageConsumer consumer2 = session2.createConsumer(topic);
         final MessageConsumer consumer3 = session3.createConsumer(topic);

         final MessageProducer producer1 = session1.createProducer(topic);
         final MessageProducer producer2 = session2.createProducer(topic);
         final MessageProducer producer3 = session3.createProducer(topic);

         final TextMessage message1 = session1.createTextMessage("Message1");
         message1.setStringProperty("test", "1");

         final TextMessage message2 = session2.createTextMessage("Message2");
         message2.setStringProperty("test", "2");

         final TextMessage message3 = session3.createTextMessage("Message3");
         message3.setStringProperty("test", "3");

         connection1.start();
         connection2.start();
         connection3.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.bindingQuery(ADDRESS_NAME_SS).getQueueNames().size() == 2);
         Wait.assertTrue(() -> remoteServer.bindingQuery(ADDRESS_NAME_SS).getQueueNames().size() == 2);
         Wait.assertTrue(() -> remoteServer2.bindingQuery(ADDRESS_NAME_SS).getQueueNames().size() == 2);

         // Sent from 1 should hit all three then stop
         producer1.send(message1);
         Message received = consumer1.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message1", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("1", received.getStringProperty("test"));
         received = consumer2.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message1", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("1", received.getStringProperty("test"));
         received = consumer3.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message1", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("1", received.getStringProperty("test"));
         assertNull(consumer1.receive(100));
         assertNull(consumer2.receive(100));
         assertNull(consumer3.receive(100));

         // Sent from 1 should hit all three then stop
         producer2.send(message2);
         received = consumer1.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message2", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("2", received.getStringProperty("test"));
         received = consumer2.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message2", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("2", received.getStringProperty("test"));
         received = consumer3.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message2", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("2", received.getStringProperty("test"));
         assertNull(consumer1.receiveNoWait());
         assertNull(consumer2.receiveNoWait());
         assertNull(consumer3.receiveNoWait());

         // Sent from 1 should hit all three then stop
         producer3.send(message3);
         received = consumer1.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message3", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("3", received.getStringProperty("test"));
         received = consumer2.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message3", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("3", received.getStringProperty("test"));
         received = consumer3.receive(2_000);
         assertNotNull(received);
         assertInstanceOf(TextMessage.class, received);
         assertEquals("Message3", ((TextMessage) received).getText());
         assertTrue(received.propertyExists("test"));
         assertEquals("3", received.getStringProperty("test"));
         assertNull(consumer1.receiveNoWait());
         assertNull(consumer2.receiveNoWait());
         assertNull(consumer3.receiveNoWait());
      }
   }

   @Test
   @Timeout(20)
   public void testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient() throws Exception {
      testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient("CORE", "AMQP", false); // Tunneling doesn't matter here
   }

   @Test
   @Timeout(20)
   public void testCoreConsumerDemandOnLocalBrokerFederatesMessageFromCoreClientTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient("CORE", "CORE", true);
   }

   @Test
   @Timeout(20)
   public void testCoreConsumerDemandOnLocalBrokerFederatesMessageFromCoreClientUnTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient("CORE", "CORE", false);
   }

   @Test
   @Timeout(20)
   public void testAMQPConsumerDemandOnLocalBrokerFederatesMessageFromCoreClientTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient("AMQP", "CORE", true);
   }

   @Test
   @Timeout(20)
   public void testAMQPConsumerDemandOnLocalBrokerFederatesMessageFromCoreClientNotTunneled() throws Exception {
      testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient("AMQP", "CORE", false);
   }

   private void testCoreConsumerDemandOnLocalBrokerFederatesMessageFromAMQPClient(String localProtocol,
                                                                                  String remoteProtocol,
                                                                                  boolean enableCoreTunneling) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("test", "test");
      localQueuePolicy.addProperty(AmqpSupport.TUNNEL_CORE_MESSAGES, Boolean.toString(enableCoreTunneling));

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress("test")
                                                             .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(localProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(remoteProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final MessageConsumer consumerL = sessionL.createConsumer(sessionL.createQueue("test"));

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of("test")).isExists());

         final MessageProducer producerR = sessionR.createProducer(sessionR.createQueue("test"));
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
   public void testQueueDemandOnLocalBrokerFederatesMatchingFilteredMessagesFromRemoteAMQP() throws Exception {
      testQueueDemandOnLocalBrokerFederatesMatchingFilteredMessagesFromRemote("AMQP");
   }

   @Test
   @Timeout(20)
   public void testQueueDemandOnLocalBrokerFederatesMatchingFilteredMessagesFromRemoteCORE() throws Exception {
      testQueueDemandOnLocalBrokerFederatesMatchingFilteredMessagesFromRemote("CORE");
   }

   private void testQueueDemandOnLocalBrokerFederatesMatchingFilteredMessagesFromRemote(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("#", "test");

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress("test")
                                                             .setFilterString("color='red' OR color='green' OR color='blue'")
                                                             .setAutoCreated(false));
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Queue queue = sessionL.createQueue("test");

         final MessageConsumer consumerL1 = sessionL.createConsumer(queue, "color='red'");
         final MessageConsumer consumerL2 = sessionL.createConsumer(queue, "color='blue'");

         connectionL.start();
         connectionR.start();

         // Demand on local queue should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());

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
         assertInstanceOf(TextMessage.class, receivedL1);
         assertEquals("Hello World 2", ((TextMessage) receivedL1).getText());
         assertTrue(receivedL1.propertyExists("color"));
         assertEquals("red", receivedL1.getStringProperty("color"));

         final Message receivedL2 = consumerL2.receive(5_000);
         assertNotNull(receivedL2);
         assertInstanceOf(TextMessage.class, receivedL2);
         assertEquals("Hello World 3", ((TextMessage) receivedL2).getText());
         assertTrue(receivedL2.propertyExists("color"));
         assertEquals("blue", receivedL2.getStringProperty("color"));

         // See if the green message is still on the remote where it should be as the
         // filter should prevent it from moving across the federation link(s)
         final MessageConsumer consumerR = sessionR.createConsumer(queue, "color='green'");

         final Message receivedR = consumerR.receive(5_000);
         assertNotNull(receivedR);
         assertInstanceOf(TextMessage.class, receivedR);
         assertEquals("Hello World 1", ((TextMessage) receivedR).getText());
         assertTrue(receivedR.propertyExists("color"));
         assertEquals("green", receivedR.getStringProperty("color"));
      }
   }

   @Test
   @Timeout(20)
   public void testAddressFederatedOverSingleConnectionNotReflectedBackToSendingNodeAMQP() throws Exception {
      doTestAddressFederatedOverSingleConnectionNotReflectedBackToSendingNode("AMQP");
   }

   @Test
   @Timeout(20)
   public void testAddressFederatedOverSingleConnectionNotReflectedBackToSendingNodeCore() throws Exception {
      doTestAddressFederatedOverSingleConnectionNotReflectedBackToSendingNode("CORE");
   }

   private void doTestAddressFederatedOverSingleConnectionNotReflectedBackToSendingNode(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("local-test-policy");
      localAddressPolicy.addToIncludes("test");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);
      localAddressPolicy.setMaxHops(0); // Disable max hops

      final AMQPFederationAddressPolicyElement remoteAddressPolicy = new AMQPFederationAddressPolicyElement();
      remoteAddressPolicy.setName("remote-test-policy");
      remoteAddressPolicy.addToIncludes("test");
      remoteAddressPolicy.setAutoDelete(false);
      remoteAddressPolicy.setAutoDeleteDelay(-1L);
      remoteAddressPolicy.setAutoDeleteMessageCount(-1L);
      remoteAddressPolicy.setMaxHops(0); // Disable max hops

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalAddressPolicy(localAddressPolicy);
      element.addRemoteAddressPolicy(remoteAddressPolicy);

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

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);
         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         final MessageProducer producerL = sessionL.createProducer(topic);
         final MessageProducer producerR = sessionR.createProducer(topic);

         final TextMessage messageFromL = sessionL.createTextMessage("local");
         final TextMessage messageFromR = sessionR.createTextMessage("remote");

         connectionL.start();
         connectionR.start();

         final SimpleString addressName = SimpleString.of("test");

         Wait.assertTrue(() -> server.addressQuery(addressName).isExists());
         Wait.assertTrue(() -> remoteServer.addressQuery(addressName).isExists());

         assertNull(consumerL.receiveNoWait());
         assertNull(consumerR.receiveNoWait());

         // Captures state of JMS consumer and federation consumer attached on each node
         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() >= 2);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() >= 2);

         producerL.send(messageFromL);

         final Message messageL1 = consumerL.receive();
         final Message messageR1 = consumerR.receive();

         assertNotNull(messageL1);
         assertNotNull(messageR1);
         assertInstanceOf(TextMessage.class, messageL1);
         assertInstanceOf(TextMessage.class, messageR1);
         assertEquals("local", ((TextMessage) messageL1).getText());
         assertEquals("local", ((TextMessage) messageR1).getText());

         producerR.send(messageFromR);

         final Message messageL2 = consumerL.receive();
         final Message messageR2 = consumerR.receive();

         assertNotNull(messageL2);
         assertNotNull(messageR2);
         assertInstanceOf(TextMessage.class, messageL2);
         assertInstanceOf(TextMessage.class, messageR2);
         assertEquals("remote", ((TextMessage) messageL2).getText());
         assertEquals("remote", ((TextMessage) messageR2).getText());

         // Should be no other messages routed
         assertNull(consumerL.receiveNoWait());
         assertNull(consumerR.receiveNoWait());
      }
   }

   @Test
   @Timeout(20)
   public void testAddressFederatedOnTwoConnectionsNotReflectedBackToSendingNodeAMQP() throws Exception {
      doTestAddressFederatedOverTwoConnectionNotReflectedBackToSendingNode("AMQP");
   }

   @Test
   @Timeout(20)
   public void testAddressFederatedOnTwoConnectionsNotReflectedBackToSendingNodeCore() throws Exception {
      doTestAddressFederatedOverTwoConnectionNotReflectedBackToSendingNode("CORE");
   }

   private void doTestAddressFederatedOverTwoConnectionNotReflectedBackToSendingNode(String clientProtocol) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy1 = new AMQPFederationAddressPolicyElement();
      localAddressPolicy1.setName("local-test-policy");
      localAddressPolicy1.addToIncludes("test");
      localAddressPolicy1.setAutoDelete(false);
      localAddressPolicy1.setAutoDeleteDelay(-1L);
      localAddressPolicy1.setAutoDeleteMessageCount(-1L);
      localAddressPolicy1.setMaxHops(0); // Disable max hops

      final AMQPFederationAddressPolicyElement localAddressPolicy2 = new AMQPFederationAddressPolicyElement();
      localAddressPolicy2.setName("remote-test-policy");
      localAddressPolicy2.addToIncludes("test");
      localAddressPolicy2.setAutoDelete(false);
      localAddressPolicy2.setAutoDeleteDelay(-1L);
      localAddressPolicy2.setAutoDeleteMessageCount(-1L);
      localAddressPolicy2.setMaxHops(0); // Disable max hops

      final AMQPFederatedBrokerConnectionElement element1 = new AMQPFederatedBrokerConnectionElement();
      element1.setName(getTestName() + ":1");
      element1.addLocalAddressPolicy(localAddressPolicy1);

      final AMQPFederatedBrokerConnectionElement element2 = new AMQPFederatedBrokerConnectionElement();
      element2.setName(getTestName() + "2");
      element2.addLocalAddressPolicy(localAddressPolicy2);

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
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory(clientProtocol, "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);
         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         final MessageProducer producerL = sessionL.createProducer(topic);
         final MessageProducer producerR = sessionR.createProducer(topic);

         final TextMessage messageFromL = sessionL.createTextMessage("local");
         final TextMessage messageFromR = sessionR.createTextMessage("remote");

         connectionL.start();
         connectionR.start();

         final SimpleString addressName = SimpleString.of("test");

         Wait.assertTrue(() -> server.addressQuery(addressName).isExists());
         Wait.assertTrue(() -> remoteServer.addressQuery(addressName).isExists());

         assertNull(consumerL.receiveNoWait());
         assertNull(consumerR.receiveNoWait());

         // Captures state of JMS consumer and federation consumer attached on each node
         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() >= 2);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() >= 2);

         producerL.send(messageFromL);

         final Message messageL1 = consumerL.receive();
         final Message messageR1 = consumerR.receive();

         assertNotNull(messageL1);
         assertNotNull(messageR1);
         assertInstanceOf(TextMessage.class, messageL1);
         assertInstanceOf(TextMessage.class, messageR1);
         assertEquals("local", ((TextMessage) messageL1).getText());
         assertEquals("local", ((TextMessage) messageR1).getText());

         producerR.send(messageFromR);

         final Message messageL2 = consumerL.receive();
         final Message messageR2 = consumerR.receive();

         assertNotNull(messageL2);
         assertNotNull(messageR2);
         assertInstanceOf(TextMessage.class, messageL2);
         assertInstanceOf(TextMessage.class, messageR2);
         assertEquals("remote", ((TextMessage) messageL2).getText());
         assertEquals("remote", ((TextMessage) messageR2).getText());

         // Should be no other messages routed
         assertNull(consumerL.receiveNoWait());
         assertNull(consumerR.receiveNoWait());
      }
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testQueueConsumerPullsMessagesAndRemovesDemandLeavingSomeOnRemoteAMQP() throws Exception {
      doTestQueueConsumerPullsMessagesAndRemovesDemandLeavingSomeOnRemote("AMQP", 0);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testQueueConsumerPullsMessagesAndRemovesDemandLeavingSomeOnRemoteAMQPWithPrefetch() throws Exception {
      doTestQueueConsumerPullsMessagesAndRemovesDemandLeavingSomeOnRemote("AMQP", 1);
   }

   private void doTestQueueConsumerPullsMessagesAndRemovesDemandLeavingSomeOnRemote(String clientProtocol, int prefetch) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes(getTestName(), getTestName());
      localQueuePolicy.addProperty(RECEIVER_CREDITS, 0);         // Enable Pull mode
      localQueuePolicy.addProperty(PULL_RECEIVER_BATCH_SIZE, 1); // Pull mode batch is one

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);

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

      final int MESSAGE_COUNT = 20;
      final int REMAINING_COUNT = MESSAGE_COUNT / 2;
      final List<Message> messages = new ArrayList<>();

      final JmsConnectionFactory factoryRemote = new JmsConnectionFactory("amqp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionR = factoryRemote.createConnection();
           Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE)) {

         final Queue queue = sessionR.createQueue(getTestName());
         final MessageProducer producer = sessionR.createProducer(queue);

         for (int i = 0; i < MESSAGE_COUNT; ++i) {
            TextMessage message = sessionR.createTextMessage("test-message:" + i);

            message.setIntProperty("messageNo", i);

            producer.send(message);
         }
      }

      // Consume half the messages on the
      final JmsConnectionFactory factoryLocal = new JmsConnectionFactory(
         "amqp://localhost:" + SERVER_PORT + "?jms.prefetchPolicy.all=" + prefetch);

      try (Connection connectionL = factoryLocal.createConnection();
           Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE)) {

         connectionL.start();

         final Queue queue = sessionL.createQueue(getTestName());
         final MessageConsumer consumer = sessionL.createConsumer(queue);

         for (int i = 0; i < REMAINING_COUNT; ++i) {
            Message received = consumer.receive(5_000);
            assertNotNull(received);
            messages.add(received);

            logger.info("Read new message #{} federated from remote: ", i);
         }

         consumer.close();
      }

      assertEquals(REMAINING_COUNT, messages.size());

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 10_000);
      Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).isExists(), 10_000);

      final org.apache.activemq.artemis.core.server.Queue queueL = server.locateQueue(getTestName());
      final org.apache.activemq.artemis.core.server.Queue queueR = remoteServer.locateQueue(getTestName());

      assertNotNull(queueL);
      assertNotNull(queueR);

      // Will tell you counts are off which indicates a message was left on the remote meaning it
      // is now a duplicate and will be federated in error if this consumer attached again.
      final long pendingMessages = queueL.getMessageCount() + queueR.getMessageCount();
      assertEquals(REMAINING_COUNT, pendingMessages);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessageProduceOnLocal() throws Exception {
      doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(true, false, true);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessageProduceOnRemote() throws Exception {
      doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(false, false, true);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessageProduceOnLocalIncludeFederated() throws Exception {
      doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(true, true, true);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessageProduceOnRemoteIncludeFederated() throws Exception {
      doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(false, true, true);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessageProduceOnLocalIncludeFederatedAmdPriority() throws Exception {
      doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(true, true, false);
   }

   @RepeatedTest(1)
   @Timeout(20)
   public void testTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessageProduceOnRemoteIncludeFederatedAndPriority() throws Exception {
      doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(false, true, false);
   }

   public void doTestTwoPullConsumerOnPullingFederationConfigurationEachCanTakeOneMessage(boolean produceLocal, boolean includeFederated, boolean ignorePriority) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy-1");
      localQueuePolicy.addToIncludes(getTestName(), getTestName());
      localQueuePolicy.addProperty(RECEIVER_CREDITS, 0);         // Enable Pull mode
      localQueuePolicy.addProperty(PULL_RECEIVER_BATCH_SIZE, 1); // Pull mode batch is one
      localQueuePolicy.setIncludeFederated(includeFederated);
      localQueuePolicy.addProperty(IGNORE_QUEUE_CONSUMER_PRIORITIES, Boolean.toString(ignorePriority));

      final AMQPFederationQueuePolicyElement remoteQueuePolicy = new AMQPFederationQueuePolicyElement();
      remoteQueuePolicy.setName("test-policy-2");
      remoteQueuePolicy.addToIncludes(getTestName(), getTestName());
      remoteQueuePolicy.addProperty(RECEIVER_CREDITS, 0);         // Enable Pull mode
      remoteQueuePolicy.addProperty(PULL_RECEIVER_BATCH_SIZE, 1); // Pull mode batch is one
      remoteQueuePolicy.setIncludeFederated(includeFederated);
      remoteQueuePolicy.addProperty(IGNORE_QUEUE_CONSUMER_PRIORITIES, Boolean.toString(ignorePriority));

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);
      element.addRemoteQueuePolicy(remoteQueuePolicy);

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

         final TextMessage messageL = (TextMessage) consumerL.receive(2_000); // Read from local
         final TextMessage messageR = (TextMessage) consumerR.receive(2_000); // Read from remote after federated

         assertNotNull(messageL);
         assertNotNull(messageR);

         // Should be a single JMS consumer and a single Federation consumer on each server's Queue
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).getConsumerCount() == 2, 10_000);
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getTestName())).getConsumerCount() == 2, 10_000);
      }
   }

   @Test
   @Timeout(20)
   public void testAddressFederationWithRestartReceivesMessagesSentToAddressWhileOffline() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("test-policy");
      localAddressPolicy.addToIncludes(getTestName());
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalAddressPolicy(localAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "failover:(amqp://localhost:" + SERVER_PORT + ")");
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      final Connection connectionL = factoryLocal.createConnection();
      final Connection connectionR = factoryRemote.createConnection();

      connectionL.setClientID("durableClientId");

      final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
      final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

      final Topic topic = sessionL.createTopic(getTestName());

      final MessageConsumer consumerL = sessionL.createDurableConsumer(topic, "durable");

      connectionL.start();
      connectionR.start();

      // Demand on local address should trigger receiver on remote.
      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
      Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());

      // Captures state of JMS consumers and federation consumers attached on each node
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

      final MessageProducer producerR = sessionR.createProducer(topic);
      final TextMessage message = sessionR.createTextMessage("Hello World");

      message.setStringProperty("testProperty", "testValue");
      producerR.send(message);

      final Message received1 = consumerL.receive(5_000);

      assertNotNull(received1);
      assertInstanceOf(TextMessage.class, received1);
      assertEquals("Hello World", ((TextMessage) received1).getText());
      assertTrue(message.propertyExists("testProperty"));
      assertEquals("testValue", received1.getStringProperty("testProperty"));

      server.stop();

      // Send message to federated address while server 1 is offline
      message.setStringProperty("testProperty", "testValueTwo");
      producerR.send(message);

      server.start();
      String resourceName = "brokerconnection." + getTestName() + ".federation." + getTestName();
      // check federation has reconnected
      Wait.assertTrue(() -> server.getManagementService().getResource(resourceName) != null);
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

      final Message received2 = consumerL.receive(5_000);

      assertNotNull(received2);
      assertInstanceOf(TextMessage.class, received2);
      assertEquals("Hello World", ((TextMessage) received2).getText());
      assertTrue(message.propertyExists("testProperty"));
      assertEquals("testValueTwo", received2.getStringProperty("testProperty"));

      connectionL.close();
      connectionR.close();

      server.stop();
      remoteServer.stop();
   }

   @Test
   @Timeout(20)
   public void testRemoteAddressFederationWithRestartReceivesMessagesSentToAddressWhileOffline() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement remoteAddressPolicy = new AMQPFederationAddressPolicyElement();
      remoteAddressPolicy.setName("test-policy");
      remoteAddressPolicy.addToIncludes(getTestName());
      remoteAddressPolicy.setAutoDelete(false);
      remoteAddressPolicy.setAutoDeleteDelay(-1L);
      remoteAddressPolicy.setAutoDeleteMessageCount(-1L);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addRemoteAddressPolicy(remoteAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "failover:(amqp://localhost:" + SERVER_PORT_REMOTE + ")");

      final Connection connectionL = factoryLocal.createConnection();
      final Connection connectionR = factoryRemote.createConnection();

      connectionR.setClientID("durableClientId");

      final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
      final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

      final Topic topic = sessionR.createTopic(getTestName());

      final MessageConsumer consumerR = sessionR.createDurableConsumer(topic, "durable");

      connectionL.start();
      connectionR.start();

      // Demand on local address should trigger receiver on remote.
      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
      Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());

      // Captures state of JMS consumers and federation consumers attached on each node
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

      final MessageProducer producerL = sessionL.createProducer(topic);
      final TextMessage message = sessionL.createTextMessage("Hello World");

      message.setStringProperty("testProperty", "testValue");
      producerL.send(message);

      final Message received1 = consumerR.receive(5_000);

      assertNotNull(received1);
      assertInstanceOf(TextMessage.class, received1);
      assertEquals("Hello World", ((TextMessage) received1).getText());
      assertTrue(message.propertyExists("testProperty"));
      assertEquals("testValue", received1.getStringProperty("testProperty"));

      remoteServer.stop();

      // Send message to federated address while server 1 is offline
      message.setStringProperty("testProperty", "testValueTwo");
      producerL.send(message);

      remoteServer.start();
      String resourceName = "brokerconnection." + getTestName() + ".federation." + getTestName() + ".policy.test-policy.consumer." + getTestName();
      // check federation has reconnected
      Wait.assertTrue(() -> remoteServer.getManagementService().getResource(resourceName) != null);
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

      final Message received2 = consumerR.receive(5_000);

      assertNotNull(received2);
      assertInstanceOf(TextMessage.class, received2);
      assertEquals("Hello World", ((TextMessage) received2).getText());
      assertTrue(message.propertyExists("testProperty"));
      assertEquals("testValueTwo", received2.getStringProperty("testProperty"));

      connectionL.close();
      connectionR.close();

      server.stop();
      remoteServer.stop();
   }

   @Test
   @Timeout(20)
   public void testRemoteAddressFederationAppliesConsumerFilterIfConfigured() throws Exception {
      doTestRemoteAddressFederationAppliesConsumerFilterIfConfigured(false);
   }

   @Test
   @Timeout(20)
   public void testRemoteAddressFederationDoesNotApplyConsumerFilterIfConfigured() throws Exception {
      doTestRemoteAddressFederationAppliesConsumerFilterIfConfigured(true);
   }

   private void doTestRemoteAddressFederationAppliesConsumerFilterIfConfigured(boolean ignoreFilters) throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement remoteAddressPolicy = new AMQPFederationAddressPolicyElement();
      remoteAddressPolicy.setName("test-policy");
      remoteAddressPolicy.addToIncludes(getTestName());
      remoteAddressPolicy.setAutoDelete(false);
      remoteAddressPolicy.setAutoDeleteDelay(-1L);
      remoteAddressPolicy.setAutoDeleteMessageCount(-1L);
      remoteAddressPolicy.addProperty(IGNORE_ADDRESS_BINDING_FILTERS, String.valueOf(ignoreFilters));

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addRemoteAddressPolicy(remoteAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "failover:(amqp://localhost:" + SERVER_PORT_REMOTE + ")");

      final Connection connectionL = factoryLocal.createConnection();
      final Connection connectionR = factoryRemote.createConnection();

      final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
      final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

      final Topic topic = sessionR.createTopic(getTestName());

      final MessageConsumer consumerR = sessionR.createConsumer(topic, "color='red'");

      connectionL.start();
      connectionR.start();

      // Demand on local address should trigger receiver on remote.
      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTestName())).isExists());
      Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of(getTestName())).isExists());

      // Captures state of JMS consumers and federation consumers attached on each node
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);
      Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() == 1);

      try {
         final MessageProducer producerL = sessionL.createProducer(topic);
         final TextMessage message = sessionL.createTextMessage();

         message.setText("First Red Message");
         message.setStringProperty("color", "red");
         producerL.send(message);

         // Message that matched consumer filter should federate
         final Message received1 = consumerR.receive(5_000);

         assertNotNull(received1);
         assertInstanceOf(TextMessage.class, received1);
         assertEquals("First Red Message", ((TextMessage) received1).getText());
         assertTrue(received1.propertyExists("color"));
         assertEquals("red", received1.getStringProperty("color"));

         // May or may not be filtered by the local broker when federation is active to the remote based on configuration
         message.setText("Hello World Blue");
         message.setStringProperty("color", "blue");
         producerL.send(message);

         message.setText("Second Red Message");
         message.setStringProperty("color", "red");
         producerL.send(message);

         final Message received2 = consumerR.receive(5_000);

         assertNotNull(received2);
         assertInstanceOf(TextMessage.class, received2);
         assertEquals("Second Red Message", ((TextMessage) received2).getText());
         assertTrue(received2.propertyExists("color"));
         assertEquals("red", received2.getStringProperty("color"));

         // Should be no more messages
         assertNull(consumerR.receiveNoWait());

         final org.apache.activemq.artemis.core.server.Queue localQueue =
            server.locateQueue(server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().get(0));

         // Filtered message should not be federated
         if (ignoreFilters) {
            assertEquals(3, localQueue.getMessagesAdded());
         } else {
            assertEquals(2, localQueue.getMessagesAdded());
         }
      } finally {
         connectionL.close();
         connectionR.close();

         server.stop();
         remoteServer.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testAddressFederatedOnTwoConnectionsDoesNotCreateSelfSustainingLoop() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy1 = new AMQPFederationAddressPolicyElement();
      localAddressPolicy1.setName("local-test-policy-1");
      localAddressPolicy1.addToIncludes("test");
      localAddressPolicy1.setAutoDelete(false);
      localAddressPolicy1.setAutoDeleteDelay(-1L);
      localAddressPolicy1.setAutoDeleteMessageCount(-1L);
      localAddressPolicy1.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final AMQPFederationAddressPolicyElement localAddressPolicy2 = new AMQPFederationAddressPolicyElement();
      localAddressPolicy2.setName("local-test-policy-2");
      localAddressPolicy2.addToIncludes("test");
      localAddressPolicy2.setAutoDelete(false);
      localAddressPolicy2.setAutoDeleteDelay(-1L);
      localAddressPolicy2.setAutoDeleteMessageCount(-1L);
      localAddressPolicy2.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final AMQPFederatedBrokerConnectionElement element1 = new AMQPFederatedBrokerConnectionElement();
      element1.setName(getTestName() + ":1");
      element1.addLocalAddressPolicy(localAddressPolicy1);

      final AMQPFederatedBrokerConnectionElement element2 = new AMQPFederatedBrokerConnectionElement();
      element2.setName(getTestName() + ":2");
      element2.addLocalAddressPolicy(localAddressPolicy2);

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
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);
         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         final SimpleString addressName = SimpleString.of("test");

         Wait.assertTrue(() -> server.addressQuery(addressName).isExists(), 5_000, 50);
         Wait.assertTrue(() -> remoteServer.addressQuery(addressName).isExists(), 5_000, 50);

         // Captures state of JMS consumer and federation consumer attached on each node
         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() == 2, 10_000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() == 2, 10_000, 50);

         // Without active consumers the federation bindings should not sustain each other

         consumerL.close();

         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() == 1, 10_000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() == 1, 10_000, 50);

         consumerR.close();

         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() == 0, 10_000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() == 0, 10_000, 50);
      }
   }

   @Test
   @Timeout(20)
   public void testAddressFederatedOnOneConnectionDoesNotCreateSelfSustainingLoop() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationAddressPolicyElement localAddressPolicy = new AMQPFederationAddressPolicyElement();
      localAddressPolicy.setName("local-test-policy");
      localAddressPolicy.addToIncludes("test");
      localAddressPolicy.setAutoDelete(false);
      localAddressPolicy.setAutoDeleteDelay(-1L);
      localAddressPolicy.setAutoDeleteMessageCount(-1L);
      localAddressPolicy.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final AMQPFederationAddressPolicyElement remoteAddressPolicy = new AMQPFederationAddressPolicyElement();
      remoteAddressPolicy.setName("remote-test-policy");
      remoteAddressPolicy.addToIncludes("test");
      remoteAddressPolicy.setAutoDelete(false);
      remoteAddressPolicy.setAutoDeleteDelay(-1L);
      remoteAddressPolicy.setAutoDeleteMessageCount(-1L);
      remoteAddressPolicy.addProperty(ADDRESS_RECEIVER_IDLE_TIMEOUT, 0);

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName() + ":1");
      element.addLocalAddressPolicy(localAddressPolicy);
      element.addRemoteAddressPolicy(remoteAddressPolicy);

      final AMQPBrokerConnectConfiguration amqpConnection1 =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection1.setReconnectAttempts(10);// Limit reconnects
      amqpConnection1.addElement(element);

      server.getConfiguration().addAMQPConnection(amqpConnection1);
      remoteServer.start();
      server.start();

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);
         final MessageConsumer consumerR = sessionR.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         final SimpleString addressName = SimpleString.of("test");

         Wait.assertTrue(() -> server.addressQuery(addressName).isExists(), 5_000, 50);
         Wait.assertTrue(() -> remoteServer.addressQuery(addressName).isExists(), 5_000, 50);

         // Captures state of JMS consumer and federation consumer attached on each node
         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() == 2, 10_000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() == 2, 10_000, 50);

         // Without active consumers the federation bindings should not sustain each other

         consumerL.close();

         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() == 1, 10_000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() == 1, 10_000, 50);

         consumerR.close();

         Wait.assertTrue(() -> server.bindingQuery(addressName, false).getQueueNames().size() == 0, 10_000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(addressName, false).getQueueNames().size() == 0, 10_000, 50);
      }
   }

   @Test
   @Timeout(20)
   public void testFederateFromRemoteDLQThatHasMessagesSentToMulticastAddressOriginially() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement queuePolicy = new AMQPFederationQueuePolicyElement();
      queuePolicy.setName("remote-dlq-policy");
      queuePolicy.addToIncludes(getDeadLetterAddress(), getDeadLetterAddress());

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName("From-Server1-DLQ");
      element.addLocalQueuePolicy(queuePolicy);

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
         connection.setClientID("test-federation");
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

      final ConnectionFactory factoryRemote = new JmsConnectionFactory(
         "amqp://localhost:" + SERVER_PORT_REMOTE + "?jms.prefetchPolicy.all=0");

      try (Connection connectionR = factoryRemote.createConnection();
           Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE)) {

         connectionR.start();

         final Queue queue = sessionR.createQueue(getDeadLetterAddress());
         final MessageConsumer consumer = sessionR.createConsumer(queue);

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getDeadLetterAddress())).isExists(), 1_000);
         Wait.assertTrue(() -> remoteServer.queueQuery(SimpleString.of(getDeadLetterAddress())).isExists(), 1_000);
         Wait.assertEquals(1L, () -> remoteServer.queueQuery(SimpleString.of(getDeadLetterAddress())).getMessageCount(), 5_000, 10);

         assertNotNull(consumer.receiveNoWait());
      }
   }
}
