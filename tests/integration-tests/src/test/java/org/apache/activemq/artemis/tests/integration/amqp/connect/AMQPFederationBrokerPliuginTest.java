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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_RECEIVER_IDLE_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.ActiveMQException;
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
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.ActiveMQServerAMQPFederationPlugin;
import org.apache.activemq.artemis.protocol.amqp.federation.Federation;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumer;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test that covers the use of the AMQP Federation broker plugin
 */
public class AMQPFederationBrokerPliuginTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int SERVER_PORT = AMQP_PORT;
   private static final int SERVER_PORT_REMOTE = AMQP_PORT + 1;

   protected ActiveMQServer remoteServer;

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      remoteServer = createServer(SERVER_PORT_REMOTE, false);

      return createServer(SERVER_PORT, false);
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();

      try {
         if (remoteServer != null) {
            remoteServer.stop();
         }
      } catch (Exception e) {
      }
   }

   @Test
   @Timeout(20)
   public void testFederationBrokerPluginWithAddressPolicyConfigured() throws Exception {
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

      final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.registerBrokerPlugin(federationPlugin);
      server.start();

      Wait.assertTrue(() -> federationPlugin.started.get());

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("test")).isExists(), 5000, 50);
         Wait.assertTrue(() -> server.bindingQuery(SimpleString.of("test")).getQueueNames().size() > 0, 5000, 50);
         Wait.assertTrue(() -> remoteServer.addressQuery(SimpleString.of("test")).isExists(), 5000, 50);
         Wait.assertTrue(() -> remoteServer.bindingQuery(SimpleString.of("test")).getQueueNames().size() > 0, 5000, 50);
         Wait.assertTrue(() -> federationPlugin.beforeCreateConsumerCapture.get() != null, 5000, 50);
         Wait.assertTrue(() -> federationPlugin.afterCreateConsumerCapture.get() != null, 5000, 50);

         final MessageProducer producerR = sessionR.createProducer(topic);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePreHandled = new AtomicReference<>();
         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePostHandled = new AtomicReference<>();

         federationPlugin.beforeMessageHandled = (c, m) -> {
            messagePreHandled.set(m);
         };
         federationPlugin.afterMessageHandled = (c, m) -> {
            messagePostHandled.set(m);
         };

         producerR.send(message);

         Wait.assertTrue(() -> messagePreHandled.get() != null, 5000, 50);
         Wait.assertTrue(() -> messagePostHandled.get() != null, 5000, 50);

         assertSame(messagePreHandled.get(), messagePostHandled.get());

         final Message received = consumerL.receive(5_000);

         consumerL.close();

         Wait.assertTrue(() -> federationPlugin.beforeCloseConsumerCapture.get() != null, 5000, 50);
         Wait.assertTrue(() -> federationPlugin.afterCloseConsumerCapture.get() != null, 5000, 50);

         assertNotNull(received);
      }

      server.stop();

      Wait.assertTrue(() -> federationPlugin.stopped.get());
   }

   @Test
   @Timeout(20)
   public void testFederationBrokerPluginWithQueuePolicyConfigured() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("test", "test");

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);
      element.addProperty(QUEUE_RECEIVER_IDLE_TIMEOUT, 1);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress("test")
                                                             .setAutoCreated(false));
      server.registerBrokerPlugin(federationPlugin);
      server.start();

      Wait.assertTrue(() -> federationPlugin.started.get());

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final javax.jms.Queue queue = sessionL.createQueue("test");

         final MessageConsumer consumerL = sessionL.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on local address should trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());
         Wait.assertTrue(() -> federationPlugin.beforeCreateConsumerCapture.get() != null);
         Wait.assertTrue(() -> federationPlugin.afterCreateConsumerCapture.get() != null);

         final MessageProducer producerR = sessionR.createProducer(queue);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePreHandled = new AtomicReference<>();
         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePostHandled = new AtomicReference<>();

         federationPlugin.beforeMessageHandled = (c, m) -> {
            messagePreHandled.set(m);
         };
         federationPlugin.afterMessageHandled = (c, m) -> {
            messagePostHandled.set(m);
         };

         producerR.send(message);

         Wait.assertTrue(() -> messagePreHandled.get() != null);
         Wait.assertTrue(() -> messagePostHandled.get() != null);

         assertSame(messagePreHandled.get(), messagePostHandled.get());

         final Message received = consumerL.receive(5_000);

         consumerL.close();

         Wait.assertTrue(() -> federationPlugin.beforeCloseConsumerCapture.get() != null);
         Wait.assertTrue(() -> federationPlugin.afterCloseConsumerCapture.get() != null);

         assertNotNull(received);
      }

      server.stop();

      Wait.assertTrue(() -> federationPlugin.stopped.get());
   }

   @Test
   @Timeout(20)
   public void testPluginCanBlockAddressFederationConsumerCreate() throws Exception {
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

      final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();
      federationPlugin.shouldCreateConsumerForAddress = (a) -> false;

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.registerBrokerPlugin(federationPlugin);
      server.start();

      Wait.assertTrue(() -> federationPlugin.started.get());

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic topic = sessionL.createTopic("test");

         final MessageConsumer consumerL = sessionL.createConsumer(topic);

         connectionL.start();
         connectionR.start();

         // Demand on local address should not trigger receiver on remote.
         Wait.assertTrue(() -> server.addressQuery(SimpleString.of("test")).isExists());

         final MessageProducer producerR = sessionR.createProducer(topic);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePreHandled = new AtomicReference<>();

         federationPlugin.beforeMessageHandled = (c, m) -> {
            messagePreHandled.set(m);
         };

         producerR.send(message);

         assertNull(consumerL.receiveNoWait());
         assertNull(messagePreHandled.get());

         consumerL.close();
      }

      server.stop();

      Wait.assertTrue(() -> federationPlugin.stopped.get());
   }

   @Test
   @Timeout(20)
   public void testPluginCanBlockQueueFederationConsumerCreate() throws Exception {
      logger.info("Test started: {}", getTestName());

      final AMQPFederationQueuePolicyElement localQueuePolicy = new AMQPFederationQueuePolicyElement();
      localQueuePolicy.setName("test-policy");
      localQueuePolicy.addToIncludes("test", "test");

      final AMQPFederatedBrokerConnectionElement element = new AMQPFederatedBrokerConnectionElement();
      element.setName(getTestName());
      element.addLocalQueuePolicy(localQueuePolicy);

      final AMQPBrokerConnectConfiguration amqpConnection =
         new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + SERVER_PORT_REMOTE);
      amqpConnection.setReconnectAttempts(10);// Limit reconnects
      amqpConnection.addElement(element);

      final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();
      federationPlugin.shouldCreateConsumerForQueue = (q) -> false;

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      remoteServer.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress("test")
                                                             .setAutoCreated(false));
      server.registerBrokerPlugin(federationPlugin);
      server.start();

      Wait.assertTrue(() -> federationPlugin.started.get());

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final javax.jms.Queue queue = sessionL.createQueue("test");

         final MessageConsumer consumerL = sessionL.createConsumer(queue);

         connectionL.start();
         connectionR.start();

         // Demand on local address should not trigger receiver on remote.
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists());

         final MessageProducer producerR = sessionR.createProducer(queue);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePreHandled = new AtomicReference<>();

         federationPlugin.beforeMessageHandled = (c, m) -> {
            messagePreHandled.set(m);
         };

         producerR.send(message);

         assertNull(consumerL.receiveNoWait());
         assertNull(messagePreHandled.get());

         consumerL.close();
      }

      server.stop();

      Wait.assertTrue(() -> federationPlugin.stopped.get());
   }

   @Test
   @Timeout(20)
   public void testPluginCanBlockAddressFederationWhenDemandOnDivertIsAdded() throws Exception {
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

      final AMQPTestFederationBrokerPlugin federationPlugin = new AMQPTestFederationBrokerPlugin();
      federationPlugin.shouldCreateConsumerForDivert = (d, q) -> false;

      server.getConfiguration().addAMQPConnection(amqpConnection);
      remoteServer.start();
      server.registerBrokerPlugin(federationPlugin);
      server.start();
      server.deployDivert(divert);
      // Currently the address must exist on the local before we will federate from the remote
      server.addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));

      final ConnectionFactory factoryLocal = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT);
      final ConnectionFactory factoryRemote = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + SERVER_PORT_REMOTE);

      try (Connection connectionL = factoryLocal.createConnection();
           Connection connectionR = factoryRemote.createConnection()) {

         final Session sessionL = connectionL.createSession(Session.AUTO_ACKNOWLEDGE);
         final Session sessionR = connectionR.createSession(Session.AUTO_ACKNOWLEDGE);

         final Topic target = sessionL.createTopic("target");
         final Topic source = sessionL.createTopic("source");

         final MessageConsumer consumerL = sessionL.createConsumer(target);

         connectionL.start();
         connectionR.start();

         final MessageProducer producerR = sessionR.createProducer(source);
         final TextMessage message = sessionR.createTextMessage("Hello World");

         final AtomicReference<org.apache.activemq.artemis.api.core.Message> messagePreHandled = new AtomicReference<>();

         federationPlugin.beforeMessageHandled = (c, m) -> {
            messagePreHandled.set(m);
         };

         producerR.send(message);

         assertNull(consumerL.receiveNoWait());
         assertNull(messagePreHandled.get());
      }

      server.stop();

      Wait.assertTrue(() -> federationPlugin.stopped.get());
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
      public Function<Queue, Boolean> shouldCreateConsumerForQueue = (q) -> true;
      public BiFunction<Divert, Queue, Boolean> shouldCreateConsumerForDivert = (d, q) -> true;

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
      public boolean shouldCreateFederationConsumerForQueue(final Queue queue) throws ActiveMQException {
         return shouldCreateConsumerForQueue.apply(queue);
      }

      @Override
      public boolean shouldCreateFederationConsumerForDivert(Divert divert, Queue queue) throws ActiveMQException {
         return shouldCreateConsumerForDivert.apply(divert, queue);
      }
   }
}
