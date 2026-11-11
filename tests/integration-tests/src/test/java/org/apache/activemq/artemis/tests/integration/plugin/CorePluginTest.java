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
package org.apache.activemq.artemis.tests.integration.plugin;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeMetrics;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ACKNOWLEDGE_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DELIVER_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DESTROY_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DESTROY_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_SESSION_METADATA_ADDED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_UPDATE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DELIVER_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DESTROY_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_SESSION_METADATA_ADDED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_UPDATE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_ACKED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_EXPIRED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_MOVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CorePluginTest extends JMSTestBase {

   private Queue queue;

   private final Map<String, AtomicInteger> methodCalls = new ConcurrentHashMap<>();
   private final MethodCalledVerifier verifier = new MethodCalledVerifier(methodCalls);
   private final ConfigurationVerifier configurationVerifier = new ConfigurationVerifier();
   public static final String INVM_CONNECTOR_FACTORY = InVMConnectorFactory.class.getCanonicalName();

   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      Configuration config = super.createDefaultConfig(netty);
      config.registerBrokerPlugin(verifier);
      Map<String, String> props = new HashMap<>(1);
      props.put(ConfigurationVerifier.PROPERTY1, "val_1");
      configurationVerifier.init(props);
      config.registerBrokerPlugin(configurationVerifier);
      config.setMessageExpiryScanPeriod(0); // disable expiry scan so it's alwyas through delivery
      return config;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      queue = createQueue("queue1");
   }

   @Test
   public void testSendReceive() throws Exception {
      internalTestSendReceive(64);
   }

   @Test
   public void testSendReceiveLarge() throws Exception {
      internalTestSendReceive(1024 * 1024);
   }

   private void internalTestSendReceive(int messageSize) throws Exception {
      final AckPluginVerifier ackVerifier = new AckPluginVerifier((consumer, reason) -> {
         assertEquals(AckReason.NORMAL, reason);
         assertNotNull(consumer);
      });

      server.registerBrokerPlugin(ackVerifier);

      conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);
      MessageConsumer cons = sess.createConsumer(queue);

      byte[] msgs = new byte[messageSize];
      for (int i = 0; i < msgs.length; i++) {
         msgs[i] = RandomUtil.randomByte();
      }

      TextMessage msg1 = sess.createTextMessage(new String(msgs));
      prod.send(msg1);
      TextMessage received1 = (TextMessage)cons.receive(1000);
      assertNotNull(received1);

      conn.close();

      verifier.validatePluginMethodsEquals(0, MESSAGE_EXPIRED, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE,
            BEFORE_DESTROY_QUEUE, AFTER_DESTROY_QUEUE, BEFORE_UPDATE_ADDRESS, AFTER_UPDATE_ADDRESS,
            BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS, BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);
      verifier.validatePluginMethodsEquals(2, BEFORE_CREATE_SESSION, AFTER_CREATE_SESSION, BEFORE_CLOSE_SESSION,
            AFTER_CLOSE_SESSION, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS);
      verifier.validatePluginMethodsAtLeast(1, BEFORE_MESSAGE_ROUTE,
            AFTER_MESSAGE_ROUTE);

      assertEquals(1, configurationVerifier.afterSendCounter.get(), "configurationVerifier is invoked");
      assertEquals(1, configurationVerifier.successRoutedCounter.get(), "configurationVerifier is invoked");
      assertEquals("val_1", configurationVerifier.value1, "configurationVerifier config set");

      assertFalse(ackVerifier.hasError(), ackVerifier.getErrorMsg());
   }

   @Test
   public void testDestroyQueue() throws Exception {
      conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      sess.createProducer(queue);
      conn.close();

      server.destroyQueue(SimpleString.of(queue.getQueueName()));

      verifier.validatePluginMethodsEquals(2, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS);
      verifier.validatePluginMethodsEquals(1, BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE, BEFORE_DESTROY_QUEUE,
            AFTER_DESTROY_QUEUE, BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);
   }

   @Test
   public void testAutoCreateQueue() throws Exception {
      conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue autoCreatedQueue = sess.createQueue("autoCreatedQueue");
      sess.createConsumer(autoCreatedQueue);
      conn.close();

      verifier.validatePluginMethodsEquals(1, BEFORE_DESTROY_QUEUE,
            AFTER_DESTROY_QUEUE, BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS);

      verifier.validatePluginMethodsEquals(3, BEFORE_ADD_ADDRESS,
                                           AFTER_ADD_ADDRESS);

      verifier.validatePluginMethodsEquals(2, BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE,
            BEFORE_ADD_BINDING, AFTER_ADD_BINDING);
   }

   @Test
   public void testAutoCreateTopic() throws Exception {
      conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Topic autoCreatedTopic = sess.createTopic("autoCreatedTopic");
      sess.createConsumer(autoCreatedTopic);
      conn.close();

      //before/add address called just once to remove autocreated destination
      verifier.validatePluginMethodsEquals(1, BEFORE_DESTROY_QUEUE,
            AFTER_DESTROY_QUEUE, BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS, BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);

      //Before/Add address are called twice because of the autocreated destination and the
      //created destination in the before method
      verifier.validatePluginMethodsEquals(3, BEFORE_ADD_ADDRESS,
                                           AFTER_ADD_ADDRESS);

      verifier.validatePluginMethodsEquals(2, BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE,
            BEFORE_ADD_BINDING, AFTER_ADD_BINDING);
   }

   @Test
   public void testMessageExpireServer() throws Exception {
      final AckPluginVerifier expiredVerifier = new AckPluginVerifier((ref, reason) -> assertEquals(AckReason.EXPIRED, reason));
      server.registerBrokerPlugin(expiredVerifier);

      conn = cf.createConnection();
      conn.setClientID("test");
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);
      prod.setTimeToLive(1);
      MessageConsumer cons = sess.createConsumer(queue);
      Thread.sleep(100);
      TextMessage msg1 = sess.createTextMessage("test");
      prod.send(msg1);
      Thread.sleep(100);
      assertNull(cons.receive(100));

      conn.close();

      verifier.validatePluginMethodsEquals(0, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE,
            BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS, BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);
      verifier.validatePluginMethodsAtLeast(1, AFTER_CREATE_CONNECTION, AFTER_DESTROY_CONNECTION,
            BEFORE_CREATE_CONSUMER, AFTER_CREATE_CONSUMER, BEFORE_CLOSE_CONSUMER, AFTER_CLOSE_CONSUMER,
            BEFORE_SESSION_METADATA_ADDED, AFTER_SESSION_METADATA_ADDED, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS,
            BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE, MESSAGE_ACKED, BEFORE_SEND, AFTER_SEND, BEFORE_MESSAGE_ROUTE,
            AFTER_MESSAGE_ROUTE, MESSAGE_EXPIRED, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS, BEFORE_ADD_BINDING, AFTER_ADD_BINDING);
      verifier.validatePluginMethodsEquals(2, BEFORE_CREATE_SESSION, AFTER_CREATE_SESSION, BEFORE_CLOSE_SESSION,
            AFTER_CLOSE_SESSION);

      assertFalse(expiredVerifier.hasError(), expiredVerifier.getErrorMsg());
   }

   @Test
   public void testMessageExpireClient() throws Exception {
      final AckPluginVerifier expiredVerifier = new AckPluginVerifier((ref, reason) -> assertEquals(AckReason.EXPIRED, reason));
      server.registerBrokerPlugin(expiredVerifier);

      conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);
      prod.setTimeToLive(500);
      MessageConsumer cons = sess.createConsumer(queue);

      for (int i = 0; i < 10; i++) {
         TextMessage msg1 = sess.createTextMessage("test");
         prod.send(msg1);
      }
      Thread.sleep(500);
      assertNull(cons.receive(500));

      conn.close();

      verifier.validatePluginMethodsEquals(0, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE,
            BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS, BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);
      verifier.validatePluginMethodsAtLeast(1, AFTER_CREATE_CONNECTION, AFTER_DESTROY_CONNECTION,
            BEFORE_CREATE_CONSUMER, AFTER_CREATE_CONSUMER, BEFORE_CLOSE_CONSUMER, AFTER_CLOSE_CONSUMER,
            BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE, MESSAGE_ACKED, BEFORE_SEND, AFTER_SEND, BEFORE_MESSAGE_ROUTE,
            AFTER_MESSAGE_ROUTE, BEFORE_DELIVER, AFTER_DELIVER, MESSAGE_EXPIRED, BEFORE_ADD_ADDRESS,
            AFTER_ADD_ADDRESS, BEFORE_ADD_BINDING, AFTER_ADD_BINDING);
      verifier.validatePluginMethodsEquals(2, BEFORE_CREATE_SESSION, AFTER_CREATE_SESSION, BEFORE_CLOSE_SESSION,
            AFTER_CLOSE_SESSION);

      assertFalse(expiredVerifier.hasError(), expiredVerifier.getErrorMsg());
   }

   @Test
   public void testSimpleBridge() throws Exception {
      server.stop();
      ActiveMQServer server0;
      ActiveMQServer server1;

      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(false, 0, false, server0Params);
      server0.registerBrokerPlugin(verifier);

      Map<String, Object> server1Params = new HashMap<>();
      server1Params.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      server1 = createClusteredServerWithParams(false, 1, false, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      TransportConfiguration server0tc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, server1Params);

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(server1tc.getName(), server1tc);
      server0.getConfiguration().setConnectorConfigurations(connectors);

      final int messageSize = 1024;
      final int numMessages = 10;

      List<String> connectorConfig = new ArrayList<>();
      connectorConfig.add(server1tc.getName());
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(numMessages * messageSize / 2).setStaticConnectors(connectorConfig);

      List<BridgeConfiguration> bridgeConfigs = new ArrayList<>();
      bridgeConfigs.add(bridgeConfiguration);
      server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

      QueueConfiguration queueConfig0 = QueueConfiguration.of(queueName0).setAddress(testAddress);
      List<QueueConfiguration> queueConfigs0 = new ArrayList<>();
      queueConfigs0.add(queueConfig0);
      server0.getConfiguration().setQueueConfigs(queueConfigs0);

      QueueConfiguration queueConfig1 = QueueConfiguration.of(queueName1).setAddress(forwardAddress);
      List<QueueConfiguration> queueConfigs1 = new ArrayList<>();
      queueConfigs1.add(queueConfig1);
      server1.getConfiguration().setQueueConfigs(queueConfigs1);

      server1.start();
      server0.start();

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = addSessionFactory(locator.createSessionFactory(server0tc));
      ClientSessionFactory sf1 = addSessionFactory(locator.createSessionFactory(server1tc));

      ClientSession session0 = sf0.createSession(false, true, true);
      ClientSession session1 = sf1.createSession(false, true, true);
      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));
      ClientConsumer consumer1 = session1.createConsumer(queueName1);
      session1.start();

      final byte[] bytes = new byte[messageSize];
      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);
         message.putIntProperty(propKey, i);
         message.getBodyBuffer().writeBytes(bytes);
         producer0.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
      session0.close();
      session1.close();

      sf0.close();
      sf1.close();

      assertEquals(1, server0.getClusterManager().getBridges().size());
      BridgeMetrics bridgeMetrics = server0.getClusterManager().getBridges().get("bridge1").getMetrics();
      assertEquals(10, bridgeMetrics.getMessagesPendingAcknowledgement());
      assertEquals(10, bridgeMetrics.getMessagesAcknowledged());

      //verify plugin method calls
      verifier.validatePluginMethodsEquals(1, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE);
      verifier.validatePluginMethodsEquals(10, BEFORE_DELIVER_BRIDGE, AFTER_DELIVER_BRIDGE, AFTER_ACKNOWLEDGE_BRIDGE);

      server0.stop();
      server1.stop();
   }


   @Test
   public void testUpdateAddress() throws Exception {
      server.addOrUpdateAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.ANYCAST));
      server.addOrUpdateAddressInfo(new AddressInfo(SimpleString.of("test"), RoutingType.MULTICAST));

      verifier.validatePluginMethodsEquals(1, BEFORE_UPDATE_ADDRESS, AFTER_UPDATE_ADDRESS);
   }

   @Test
   public void testMessageMoved() throws Exception {
      final String queue1Name = "queue1";
      final String queue2Name = "queue2";
      createQueue(queue2Name);
      org.apache.activemq.artemis.core.server.Queue artemisQueue = server.locateQueue(queue1Name);
      org.apache.activemq.artemis.core.server.Queue artemisQueue2 = server.locateQueue(queue2Name);

      conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);

      byte[] msgs = new byte[1024];
      for (int i = 0; i < msgs.length; i++) {
         msgs[i] = RandomUtil.randomByte();
      }

      TextMessage msg1 = sess.createTextMessage(new String(msgs));
      prod.send(msg1);
      conn.close();

      artemisQueue.moveReferences(null, artemisQueue2.getAddress(), null);
      Wait.assertEquals(1L, artemisQueue2::getMessageCount, 2000, 100);

      verifier.validatePluginMethodsEquals(1, MESSAGE_MOVED);
   }

   private class AckPluginVerifier implements ActiveMQServerPlugin {

      private BiConsumer<ServerConsumer, AckReason> assertion;
      private Throwable error;

      AckPluginVerifier(BiConsumer<ServerConsumer, AckReason> assertion) {
         this.assertion = assertion;
      }

      @Override
      public void messageAcknowledged(MessageReference ref, AckReason reason, ServerConsumer consumer) {
         try {
            assertion.accept(consumer, reason);
         } catch (Throwable e) {
            error = e;
            throw e;
         }
      }

      private boolean hasError() {
         return error != null;
      }

      private String getErrorMsg() {
         return hasError() ? error.getMessage() : "";
      }
   }

}
