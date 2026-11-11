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
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeManagementSupport;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeManagerControl;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgePolicyManagerControl;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeReceiverControl;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeSenderControl;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.ADDRESS_RECEIVER_IDLE_TIMEOUT;
import static org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeConstants.QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that the broker create management objects for AMQP bridge configurations.
 */
public class AMQPBridgeManagementTest extends AmqpClientTestSupport {

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
   public void testBridgeCreatesManagementResourcesForAddressPolicyConfigurations() throws Exception {
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

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

         final String brokerConnectionName = getTestName();

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());

         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "address-policy");
         final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeAddressReceiverResourceName(getTestName(), getTestName(), "address-policy", getTestName());

         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
         final AMQPBridgePolicyManagerControl addressPolicyControl =
            (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);

         assertNotNull(bridgeControl);
         assertEquals(getTestName(), bridgeControl.getName());
         assertEquals(0, bridgeControl.getMessagesReceived());
         assertEquals(0, bridgeControl.getMessagesSent());

         assertNotNull(addressPolicyControl);
         assertEquals("address-policy", addressPolicyControl.getName());
         assertEquals(0, addressPolicyControl.getMessagesReceived());

         final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
            server.getManagementService().getUntypedControl(consumerResourceName);

         assertNotNull(consumerControl);
         assertEquals(getTestName(), consumerControl.getAddress());
         assertEquals(0, consumerControl.getMessagesReceived());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         brokerConnection.stop();

         // Stopping the connection should remove the bridge consumer management objects.
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(consumerResourceName) == null, 5_000, 100);

         assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

         server.getBrokerConnections().forEach((connection) -> {
            try {
               connection.shutdown();
            } catch (Exception e) {
               fail("Broker connection shutdown should not have thrown an exception");
            }
         });

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(bridgeResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getBrokerConnectionControl(brokerConnectionName) == null, 5_000, 100);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesAddressConsumerManagementWhenConnectionDrops() throws Exception {
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

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(getTestName());

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());

         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "address-policy");
         final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeAddressReceiverResourceName(getTestName(), getTestName(), "address-policy", getTestName());

         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
         final AMQPBridgePolicyManagerControl addressPolicyControl =
            (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
         final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
            server.getManagementService().getUntypedControl(consumerResourceName);

         assertNotNull(bridgeControl);
         assertNotNull(addressPolicyControl);
         assertNotNull(consumerControl);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         // Closing the connection without a detach or close frame should still cleanup the management
         // resources for the consumer but the policy views should still be in place
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(consumerResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesAddressConsumerManagementWhenBrokerConnectionStopped() throws Exception {
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

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
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

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(getTestName());

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());

         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "address-policy");
         final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeAddressReceiverResourceName(getTestName(), getTestName(), "address-policy", getTestName());

         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
         final AMQPBridgePolicyManagerControl addressPolicyControl =
            (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
         final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
            server.getManagementService().getUntypedControl(consumerResourceName);

         assertNotNull(bridgeControl);
         assertNotNull(addressPolicyControl);
         assertNotNull(consumerControl);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectConnectionToDrop();

         brokerConnection.stop();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Closing the connection without a detach or close frame should still cleanup the management
         // resources for the consumer but the policy views should still be in place
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(consumerResourceName) == null, 5_000, 100);

         assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesManagementResourcesForQueuePolicyConfigurations() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement receiveFromQueue = new AMQPBridgeQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("#", getTestName());
         receiveFromQueue.setPriorityAdjustment(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName() + "::" + getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final String brokerConnectionName = getTestName();
            final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
            final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "queue-policy");
            final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeQueueReceiverResourceName(getTestName(), getTestName(), "queue-policy", getTestName() + "::" + getTestName());

            final BrokerConnectionControl brokerConnection =
               server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
            final AMQPBridgeManagerControl bridgeControl =
               (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);

            assertNotNull(brokerConnection);
            assertTrue(brokerConnection.isConnected());
            assertNotNull(bridgeControl);
            assertEquals(getTestName(), bridgeControl.getName());
            assertEquals(0, bridgeControl.getMessagesReceived());
            assertEquals(0, bridgeControl.getMessagesSent());

            final AMQPBridgePolicyManagerControl queuePolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);

            assertNotNull(queuePolicyControl);
            assertEquals("queue-policy", queuePolicyControl.getName());
            assertEquals(0, queuePolicyControl.getMessagesReceived());

            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertNotNull(consumerControl);
            assertEquals(getTestName(), consumerControl.getAddress());
            assertEquals(getTestName(), consumerControl.getQueueName());
            assertEquals(getTestName() + "::" + getTestName(), consumerControl.getFqqn());
            assertEquals(0, consumerControl.getMessagesReceived());
            assertEquals(1, consumerControl.getPriority());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            brokerConnection.stop();

            // Stopping the connection should remove the bridge management objects.
            Wait.assertTrue(() -> server.getManagementService().getUntypedControl(consumerResourceName) == null, 5_000, 100);

            assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

            server.getBrokerConnections().forEach((brConnection) -> {
               try {
                  brConnection.shutdown();
               } catch (Exception e) {
                  fail("Broker connection shutdown should not have thrown an exception");
               }
            });

            Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) == null, 5_000, 100);
            Wait.assertTrue(() -> server.getManagementService().getUntypedControl(bridgeResourceName) == null, 5_000, 100);
            Wait.assertTrue(() -> server.getManagementService().getBrokerConnectionControl(brokerConnectionName) == null, 5_000, 100);

            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesQueueConsumerManagementWhenConnectionDrops() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement receiveFromQueue = new AMQPBridgeQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("#", getTestName());
         receiveFromQueue.setPriorityAdjustment(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName() + "::" + getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final String brokerConnectionName = getTestName();
            final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
            final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "queue-policy");
            final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeQueueReceiverResourceName(getTestName(), getTestName(), "queue-policy", getTestName() + "::" + getTestName());

            final BrokerConnectionControl brokerConnection =
               server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
            final AMQPBridgeManagerControl bridgeControl =
               (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
            final AMQPBridgePolicyManagerControl queuePolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertNotNull(brokerConnection);
            assertTrue(brokerConnection.isConnected());
            assertNotNull(bridgeControl);
            assertNotNull(queuePolicyControl);
            assertNotNull(consumerControl);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.close();

            // Closing the connection without a detach or close frame should still cleanup the management
            // resources for the consumer but the policy views should still be in place
            Wait.assertTrue(() -> server.getManagementService().getUntypedControl(consumerResourceName) == null, 5_000, 100);
            Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
         }
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesQueueConsumerManagementWhenBrokerConnectionStopped() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Connect test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement receiveFromQueue = new AMQPBridgeQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("#", getTestName());
         receiveFromQueue.setPriorityAdjustment(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName() + "::" + getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists());

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final String brokerConnectionName = getTestName();
            final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
            final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "queue-policy");
            final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeQueueReceiverResourceName(getTestName(), getTestName(), "queue-policy", getTestName() + "::" + getTestName());

            final BrokerConnectionControl brokerConnection =
               server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
            final AMQPBridgeManagerControl bridgeControl =
               (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
            final AMQPBridgePolicyManagerControl queuePolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertNotNull(brokerConnection);
            assertTrue(brokerConnection.isConnected());
            assertNotNull(bridgeControl);
            assertNotNull(queuePolicyControl);
            assertNotNull(consumerControl);

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
            peer.expectConnectionToDrop();

            brokerConnection.stop();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            // Closing the connection without a detach or close frame should still cleanup the management
            // resources for the consumer but the policy views should still be in place
            Wait.assertTrue(() -> server.getManagementService().getUntypedControl(consumerResourceName) == null, 5_000, 100);

            assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

            peer.close();
         }
      }
   }

   @Test
   @Timeout(20)
   public void testAddressManagementTracksMessagesAtPolicyAndConsumerLevels() throws Exception {
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

         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "address-policy");
         final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeAddressReceiverResourceName(getTestName(), getTestName(), "address-policy", getTestName());

         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final AMQPBridgePolicyManagerControl addressPolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertEquals(1, bridgeControl.getMessagesReceived());
            assertEquals(1, addressPolicyControl.getMessagesReceived());
            assertEquals(1, consumerControl.getMessagesReceived());

            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Policy bean should still be active but consumer bean should be unregistered
         {
            final AMQPBridgePolicyManagerControl addressPolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertNotNull(addressPolicyControl);
            assertNull(consumerControl);
         }

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
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createTopic(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final AMQPBridgePolicyManagerControl addressPolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertEquals(2, bridgeControl.getMessagesReceived());
            assertEquals(2, addressPolicyControl.getMessagesReceived());
            assertEquals(1, consumerControl.getMessagesReceived());

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
   public void testQueueManagementTracksMessagesAtPolicyAndConsumerLevels() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement receiveFromQueue = new AMQPBridgeQueuePolicyElement();
         receiveFromQueue.setName("queue-policy");
         receiveFromQueue.addToIncludes("#", getTestName());
         receiveFromQueue.setPriorityAdjustment(1);

         final AMQPBridgeBrokerConnectionElement element = new AMQPBridgeBrokerConnectionElement();
         element.setName(getTestName());
         element.addBridgeFromQueuePolicy(receiveFromQueue);
         element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 0);

         final AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), "tcp://" + remoteURI.getHost() + ":" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.addElement(element);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName() + "::" + getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(0)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                                .setAddress(getTestName())
                                                                .setAutoCreated(false));

         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "queue-policy");
         final String consumerResourceName = AMQPBridgeManagementSupport.getBridgeQueueReceiverResourceName(getTestName(), getTestName(), "queue-policy", getTestName() + "::" + getTestName());

         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final AMQPBridgePolicyManagerControl queuePolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertEquals(1, bridgeControl.getMessagesReceived());
            assertEquals(1, queuePolicyControl.getMessagesReceived());
            assertEquals(1, consumerControl.getMessagesReceived());

            peer.expectFlow().withLinkCredit(999).withDrain(true)
                             .respond()
                             .withLinkCredit(0).withDeliveryCount(1000).withDrain(true);
            peer.expectDetach().respond(); // demand will be gone and receiver link should close.
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Policy bean should still be active but consumer bean should be unregistered
         {
            final AMQPBridgePolicyManagerControl queuePolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertNotNull(queuePolicyControl);
            assertNull(consumerControl);
         }

         peer.expectAttach().ofReceiver()
                            .withTarget().withAddress(getTestName() + "::" + getTestName()).also()
                            .withSource().withAddress(getTestName()).also()
                            .withName(allOf(containsString(getTestName()),
                                            containsString("queue-receiver"),
                                            containsString("amqp-bridge"),
                                            containsString(server.getNodeID().toString())))
                            .respond();
         peer.expectFlow().withLinkCredit(1000);
         peer.remoteTransfer().withBody().withString("test-message")
                              .also()
                              .withDeliveryId(1)
                              .queue();
         peer.expectDisposition().withSettled(true).withState().accepted();

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(getTestName()));

            connection.start();

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final Message message = consumer.receive(5_000);
            assertNotNull(message);
            assertInstanceOf(TextMessage.class, message);
            assertEquals("test-message", ((TextMessage) message).getText());

            peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

            final AMQPBridgePolicyManagerControl queuePolicyControl =
               (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
            final AMQPBridgeReceiverControl consumerControl = (AMQPBridgeReceiverControl)
               server.getManagementService().getUntypedControl(consumerResourceName);

            assertEquals(2, bridgeControl.getMessagesReceived());
            assertEquals(2, queuePolicyControl.getMessagesReceived());
            assertEquals(1, consumerControl.getMessagesReceived());

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
   public void testBridgeCreatesManagementResourcesForToAddressPolicyConfigurations() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("to-address-policy");
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

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         final String brokerConnectionName = getTestName();
         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "to-address-policy");
         final String producerResourceName = AMQPBridgeManagementSupport.getBridgeAddressSenderResourceName(getTestName(), getTestName(), "to-address-policy", getTestName());

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) != null, 5_000, 50);

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
         final AMQPBridgePolicyManagerControl addressPolicyControl =
            (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
         final AMQPBridgeSenderControl producerControl = (AMQPBridgeSenderControl)
            server.getManagementService().getUntypedControl(producerResourceName);

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());

         assertNotNull(bridgeControl);
         assertEquals(getTestName(), bridgeControl.getName());
         assertEquals(0, bridgeControl.getMessagesReceived());
         assertEquals(0, bridgeControl.getMessagesSent());

         assertNotNull(addressPolicyControl);
         assertEquals("to-address-policy", addressPolicyControl.getName());
         assertEquals(0, addressPolicyControl.getMessagesReceived());

         assertNotNull(producerControl);
         assertEquals(getTestName(), producerControl.getAddress());
         assertEquals(0, producerControl.getMessagesSent());

         // Message sent to server is forwarded
         peer.expectTransfer().accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createTopic(getTestName()));

            producer.send(session.createMessage());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertEquals(1, bridgeControl.getMessagesSent());
         assertEquals(1, addressPolicyControl.getMessagesSent());
         assertEquals(1, producerControl.getMessagesSent());

         brokerConnection.stop();

         // Stopping the connection should remove the bridge producer management objects.
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) == null, 5_000, 100);

         assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

         server.getBrokerConnections().forEach((connection) -> {
            try {
               connection.shutdown();
            } catch (Exception e) {
               fail("Broker connection shutdown should not have thrown an exception");
            }
         });

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(bridgeResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getBrokerConnectionControl(brokerConnectionName) == null, 5_000, 100);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeCreatesManagementResourcesForToQueuePolicyConfigurations() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("to-queue-policy");
         sendToQueue.addToIncludes("#", getTestName());

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withTarget().withAddress(getTestName()).also()
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

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).getConsumerCount() >= 1, 10_000);

         final String brokerConnectionName = getTestName();
         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "to-queue-policy");
         final String producerResourceName = AMQPBridgeManagementSupport.getBridgeAddressSenderResourceName(getTestName(), getTestName(), "to-queue-policy", getTestName() + "::" + getTestName());

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) != null, 5_000, 100);

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
         final AMQPBridgePolicyManagerControl queuePolicyControl =
            (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
         final AMQPBridgeSenderControl producerControl = (AMQPBridgeSenderControl)
            server.getManagementService().getUntypedControl(producerResourceName);

         assertNotNull(brokerConnection);
         assertTrue(brokerConnection.isConnected());

         assertNotNull(bridgeControl);
         assertEquals(getTestName(), bridgeControl.getName());
         assertEquals(0, bridgeControl.getMessagesReceived());
         assertEquals(0, bridgeControl.getMessagesSent());

         assertNotNull(queuePolicyControl);
         assertEquals("to-queue-policy", queuePolicyControl.getName());
         assertEquals(0, queuePolicyControl.getMessagesReceived());

         assertNotNull(producerControl);
         assertEquals(getTestName(), producerControl.getAddress());
         assertEquals(0, producerControl.getMessagesSent());

         // Message sent to server is forwarded
         peer.expectTransfer().accept();

         final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

         try (Connection connection = factory.createConnection()) {
            final Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(session.createQueue(getTestName()));

            producer.send(session.createMessage());
         }

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         assertEquals(1, bridgeControl.getMessagesSent());
         assertEquals(1, queuePolicyControl.getMessagesSent());
         assertEquals(1, producerControl.getMessagesSent());

         brokerConnection.stop();

         // Stopping the connection should remove the bridge producer management objects.
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) == null, 5_000, 100);

         assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

         server.getBrokerConnections().forEach((connection) -> {
            try {
               connection.shutdown();
            } catch (Exception e) {
               fail("Broker connection shutdown should not have thrown an exception");
            }
         });

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(bridgeResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getBrokerConnectionControl(brokerConnectionName) == null, 5_000, 100);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesAddressProducerManagementWhenConnectionDrops() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("to-address-policy");
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

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         final String brokerConnectionName = getTestName();
         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "to-address-policy");
         final String producerResourceName = AMQPBridgeManagementSupport.getBridgeAddressSenderResourceName(getTestName(), getTestName(), "to-address-policy", getTestName());

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);

         assertNotNull(brokerConnection);
         assertNotNull(bridgeControl);

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) != null, 5_000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         // Closing the connection without a detach or close frame should still cleanup the management
         // resources for the consumer but the policy views should still be in place
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesAddressProducerManagementWhenBrokerConnectionStopped() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeAddressPolicyElement sendToAddress = new AMQPBridgeAddressPolicyElement();
         sendToAddress.setName("to-address-policy");
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

         server.addAddressInfo(new AddressInfo(SimpleString.of(getTestName()), RoutingType.MULTICAST));

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTestName()), false).getQueueNames().size() >= 1);

         final String brokerConnectionName = getTestName();
         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "to-address-policy");
         final String producerResourceName = AMQPBridgeManagementSupport.getBridgeAddressSenderResourceName(getTestName(), getTestName(), "to-address-policy", getTestName());

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);

         assertNotNull(brokerConnection);
         assertNotNull(bridgeControl);

         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) != null, 5_000, 100);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         brokerConnection.stop();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Closing the connection without a detach or close frame should still cleanup the management
         // resources for the consumer but the policy views should still be in place
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) == null, 5_000, 100);

         assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesQueueProducerManagementWhenConnectionDrops() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("to-queue-policy");
         sendToQueue.addToIncludes("#", getTestName());

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withTarget().withAddress(getTestName()).also()
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

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).getConsumerCount() >= 1, 10_000);

         final String brokerConnectionName = getTestName();
         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "to-queue-policy");
         final String producerResourceName = AMQPBridgeManagementSupport.getBridgeAddressSenderResourceName(getTestName(), getTestName(), "to-queue-policy", getTestName() + "::" + getTestName());

         Wait.assertTrue(() -> server.getManagementService().getBrokerConnectionControl(brokerConnectionName) != null, 5_000, 100);

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);
         final AMQPBridgeManagerControl bridgeControl =
            (AMQPBridgeManagerControl) server.getManagementService().getUntypedControl(bridgeResourceName);
         final AMQPBridgePolicyManagerControl queuePolicyControl =
            (AMQPBridgePolicyManagerControl) server.getManagementService().getUntypedControl(policyResourceName);
         final AMQPBridgeSenderControl producerControl = (AMQPBridgeSenderControl)
            server.getManagementService().getUntypedControl(producerResourceName);

         assertNotNull(brokerConnection);
         assertNotNull(bridgeControl);
         assertNotNull(queuePolicyControl);
         assertNotNull(producerControl);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.close();

         // Closing the connection without a detach or close frame should still cleanup the management
         // resources for the consumer but the policy views should still be in place
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) == null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
      }
   }

   @Test
   @Timeout(20)
   public void testBridgeRemovesQueueProducerManagementWhenBrokerConnectionStopped() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.info("Test started, peer listening on: {}", remoteURI);

         final AMQPBridgeQueuePolicyElement sendToQueue = new AMQPBridgeQueuePolicyElement();
         sendToQueue.setName("to-queue-policy");
         sendToQueue.addToIncludes("#", getTestName());

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
                            .withSource().withAddress(getTestName() + "::" + getTestName()).also()
                            .withTarget().withAddress(getTestName()).also()
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

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).getConsumerCount() >= 1, 10_000);

         final String brokerConnectionName = getTestName();
         final String bridgeResourceName = AMQPBridgeManagementSupport.getBridgeManagerResourceName(getTestName(), getTestName());
         final String policyResourceName = AMQPBridgeManagementSupport.getBridgePolicyManagerResourceName(getTestName(), getTestName(), "to-queue-policy");
         final String producerResourceName = AMQPBridgeManagementSupport.getBridgeAddressSenderResourceName(getTestName(), getTestName(), "to-queue-policy", getTestName() + "::" + getTestName());

         Wait.assertTrue(() -> server.getManagementService().getBrokerConnectionControl(brokerConnectionName) != null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(bridgeResourceName) != null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(policyResourceName) != null, 5_000, 100);
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) != null, 5_000, 100);

         final BrokerConnectionControl brokerConnection =
            server.getManagementService().getBrokerConnectionControl(brokerConnectionName);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().optional();
         peer.expectClose().optional();
         peer.expectConnectionToDrop();

         brokerConnection.stop();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Closing the connection without a detach or close frame should still cleanup the management
         // resources for the consumer but the policy views should still be in place
         Wait.assertTrue(() -> server.getManagementService().getUntypedControl(producerResourceName) == null, 5_000, 100);

         assertNotNull(server.getManagementService().getUntypedControl(policyResourceName));

         peer.close();
      }
   }
}
