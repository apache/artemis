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
package org.apache.activemq.artemis.tests.integration.cluster.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
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
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.management.impl.view.ConnectionField;
import org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeMetrics;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServiceRegistryImpl;
import org.apache.activemq.artemis.core.server.transformer.AddHeadersTransformer;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class BridgeTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server0;
   private ActiveMQServer server1;
   private ServerLocator locator;

   private final boolean netty;

   @Parameters(name = "isNetty={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public BridgeTest(boolean isNetty) {
      this.netty = isNetty;
   }

   protected boolean isNetty() {
      return netty;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      StopInterceptor.reset();
      super.setUp();
   }

   private String getConnector() {
      if (isNetty()) {
         return NETTY_CONNECTOR_FACTORY;
      } else {
         return INVM_CONNECTOR_FACTORY;
      }
   }

   @TestTemplate
   public void testSimpleBridge() throws Exception {
      internaltestSimpleBridge(false, false);
   }

   @TestTemplate
   public void testSimpleBridgeFiles() throws Exception {
      internaltestSimpleBridge(false, true);
   }

   @TestTemplate
   public void testSimpleBridgeLargeMessageNullPersistence() throws Exception {
      internaltestSimpleBridge(true, false);
   }

   @TestTemplate
   public void testSimpleBridgeLargeMessageFiles() throws Exception {
      internaltestSimpleBridge(true, true);
   }

   @TestTemplate
   public void testLargeMessageBridge() throws Exception {
      long time = System.currentTimeMillis();
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(server1tc.getName(), server1tc);
      server0.getConfiguration().setConnectorConfigurations(connectors);

      final int messageSize = 1024 * 200;

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
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
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

         readLargeMessages(message, 10);

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();

      closeFields();
      if (server0.getConfiguration().isPersistenceEnabled()) {
         assertEquals(0, loadQueues(server0).size());
      }
      long timeTaken = System.currentTimeMillis() - time;
   }

   @TestTemplate
   public void testBlockedBridgeAndReconnect() throws Exception {
      long time = System.currentTimeMillis();
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);
      server1.getAddressSettingsRepository().clear();
      server1.getAddressSettingsRepository().addMatch("#", new AddressSettings().setMaxSizeBytes(10124 * 10).setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK));

      server0.getAddressSettingsRepository().clear();
      server0.getAddressSettingsRepository().addMatch("#", new AddressSettings().setMaxSizeBytes(Long.MAX_VALUE).setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK));

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(server1tc.getName(), server1tc);
      server0.getConfiguration().setConnectorConfigurations(connectors);

      final int messageSize = 1024;

      final int numMessages = 1000;

      List<String> connectorConfig = new ArrayList<>();
      connectorConfig.add(server1tc.getName());
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(100).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(true).setConfirmationWindowSize(numMessages * messageSize / 2).setStaticConnectors(connectorConfig).setProducerWindowSize(1024);

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
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = addSessionFactory(locator.createSessionFactory(server0tc));

      ClientSessionFactory sf1 = addSessionFactory(locator.createSessionFactory(server1tc));

      ClientSession session0 = sf0.createSession(false, true, 0);
      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

      ClientSession session1 = sf1.createSession(true, true, 0);
      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      final byte[] bytes = new byte[messageSize];

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.putIntProperty(propKey, i);

         message.getBodyBuffer().writeBytes(bytes);

         producer0.send(message);

         if (i % 100 == 0) {
            session0.commit();
         }
      }
      session0.commit();

      for (int i = 0; i < numMessages / 2; i++) {
         ClientMessage message = consumer1.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }
      session1.commit();

      BridgeImpl bridge = (BridgeImpl) server0.getClusterManager().getBridges().get("bridge1");

      // stop in the middle. wait the bridge to block
      Wait.assertTrue("bridge is never blocked", bridge::isBlockedOnFlowControl);

      session1.close();
      sf1.close();

      // now restart the server.. the bridge should be reconnecting now
      server1.stop();
      server1.start();

      sf1 = addSessionFactory(locator.createSessionFactory(server1tc));
      session1 = sf1.createSession(true, true, 0);
      consumer1 = session1.createConsumer(queueName1);
      session1.start();

      // consume the rest of the messages
      for (int i = numMessages / 2; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      Wait.assertEquals(0, server0.locateQueue(SimpleString.of("queue0"))::getMessageCount);

      assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();

      closeFields();
      if (server0.getConfiguration().isPersistenceEnabled()) {
         assertEquals(0, loadQueues(server0).size());
      }
      long timeTaken = System.currentTimeMillis() - time;
   }

   public void internaltestSimpleBridge(final boolean largeMessage, final boolean useFiles) throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, useFiles, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, useFiles, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      // Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

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
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
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

         if (largeMessage) {
            message.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(10 * 1024));
         }

         message.putIntProperty(propKey, i);

         message.getBodyBuffer().writeBytes(bytes);

         producer0.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         if (largeMessage) {
            readLargeMessages(message, 10);
         }

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

      closeFields();
      if (server0.getConfiguration().isPersistenceEnabled()) {
         assertEquals(0, loadQueues(server0).size());
      }
   }

   @TestTemplate
   public void testClientSessionFactoryLeak() throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";

      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(server1tc.getName(), server1tc);
      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> connectorConfig = List.of(server1tc.getName());
      // intentionally configure a bridge that will fail to connect and attempt to reconnect multiple times quickly
      server0.getConfiguration().setBridgeConfigurations(List.of(new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(0).setReconnectAttempts(-1).setStaticConnectors(connectorConfig)));
      server0.getConfiguration().setQueueConfigs(List.of(QueueConfiguration.of(queueName0).setAddress(testAddress)));

      server1.start();
      server0.start();

      ServerLocatorImpl serverLocator = (ServerLocatorImpl) ((BridgeImpl)server0.getClusterManager().getBridges().get("bridge1")).getServerLocator();
      Wait.waitFor(() -> serverLocator.getClientSessionFactoryCount() > 1, 500, 10);
      assertTrue(serverLocator.getClientSessionFactoryCount() <= 1);
   }

   @TestTemplate
   public void testBridgeClientID() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(server1tc.getName(), server1tc);
      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> connectorConfig = List.of(server1tc.getName());
      server0.getConfiguration().setBridgeConfigurations(List.of(new BridgeConfiguration().setName(getName()).setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(10).setReconnectAttempts(250).setStaticConnectors(connectorConfig).setClientId(clientId)));
      server0.getConfiguration().setQueueConfigs(List.of(QueueConfiguration.of(queueName0).setAddress(testAddress)));
      server1.getConfiguration().setQueueConfigs(List.of(QueueConfiguration.of(queueName1).setAddress(forwardAddress)));

      server1.start();
      server0.start();

      Wait.assertTrue(()-> server0.getClusterManager().getBridges().get(getName()).isConnected(), 2000, 25);

      String connectionsAsJsonString = server1.getActiveMQServerControl().listConnections(createJsonFilter(ConnectionField.CLIENT_ID.getName(), ActiveMQFilterPredicate.Operation.EQUALS.toString(), clientId), 1, 1);
      JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
      JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");
      assertEquals(1, array.size(), "number of connections returned from query");
   }

   private void addTargetParameters(final Map<String, Object> server1Params) {
      if (isNetty()) {
         server1Params.put("port", org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + 1);
      } else {
         server1Params.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      }
   }

   private void readLargeMessages(final ClientMessage message, int kiloBlocks) {
      byte[] byteRead = new byte[1024];

      for (int j = 0; j < kiloBlocks; j++) {
         message.getBodyBuffer().readBytes(byteRead);
      }
   }

   @TestTemplate
   public void testWithFilter() throws Exception {
      internalTestWithFilter(false, false);
   }

   @TestTemplate
   public void testWithFilterFiles() throws Exception {
      internalTestWithFilter(false, true);
   }

   @TestTemplate
   public void testWithFilterLargeMessages() throws Exception {
      internalTestWithFilter(true, false);
   }

   @TestTemplate
   public void testWithFilterLargeMessagesFiles() throws Exception {
      internalTestWithFilter(true, true);
   }

   public void internalTestWithFilter(final boolean largeMessage, final boolean useFiles) throws Exception {

      final int numMessages = 10;

      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, useFiles, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, useFiles, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      final String filterString = "animal='goat'";

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setFilterString(filterString).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(0).setStaticConnectors(staticConnectors);

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

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

      ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

      ClientSession session0 = sf0.createSession(false, true, true);

      ClientSession session1 = sf1.createSession(false, true, true);

      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      final SimpleString propKey = SimpleString.of("testkey");

      final SimpleString selectorKey = SimpleString.of("animal");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.putIntProperty(propKey, i);

         message.putStringProperty(selectorKey, SimpleString.of("monkey"));

         if (largeMessage) {
            message.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(10 * 1024));
         }

         producer0.send(message);
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.putIntProperty(propKey, i);

         message.putStringProperty(selectorKey, SimpleString.of("goat"));

         if (largeMessage) {
            message.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(10 * 1024));
         }

         producer0.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(4000);

         assertNotNull(message);

         assertEquals("goat", message.getStringProperty(selectorKey));

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();

         if (largeMessage) {
            readLargeMessages(message, 10);
         }
      }

      session0.commit();

      session1.commit();

      assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();
      closeFields();
      if (useFiles) {
         Map<Long, AtomicInteger> counters = loadQueues(server0);
         assertEquals(1, counters.size());
         Long key = counters.keySet().iterator().next();

         AtomicInteger value = counters.get(key);
         assertNotNull(value);
         assertEquals(numMessages, counters.get(key).intValue());
      }

   }

   @TestTemplate
   public void testStartLater() throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "forwardAddress";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(100).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(1024).setStaticConnectors(staticConnectors);

      List<BridgeConfiguration> bridgeConfigs = new ArrayList<>();
      bridgeConfigs.add(bridgeConfiguration);
      server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

      QueueConfiguration queueConfig0 = QueueConfiguration.of(queueName0).setAddress(testAddress);
      List<QueueConfiguration> queueConfigs0 = new ArrayList<>();
      queueConfigs0.add(queueConfig0);
      server0.getConfiguration().setQueueConfigs(queueConfigs0);

      server0.start();

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

      ClientSession session0 = sf0.createSession(false, true, true);

      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

      final int numMessages = 100;

      final SimpleString propKey = SimpleString.of("testkey");

      final SimpleString selectorKey = SimpleString.of("animal");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.getBodyBuffer().writeBytes(new byte[1024]);

         message.putIntProperty(propKey, i);

         message.putStringProperty(selectorKey, SimpleString.of("monkey" + i));

         producer0.send(message);
      }

      server1.start();

      Wait.assertTrue(server1::isActive);


      ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

      ClientSession session1 = sf1.createSession(false, true, true);

      try {
         session1.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST));
      } catch (Throwable ignored) {
         ignored.printStackTrace();
      }

      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);
         assertNotNull(message);
         message.acknowledge();
      }

      session1.commit();

      assertNull(consumer1.receiveImmediate());

      consumer1.close();

      session1.deleteQueue(queueName1);

      session1.close();

      sf1.close();

      server1.stop();

      session0.close();

      sf0.close();
      closeFields();
      assertEquals(0, loadQueues(server0).size());

   }

   @TestTemplate
   public void testWithDuplicates() throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String secondQueue = "queue1";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "forwardQueue";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(100).setReconnectAttemptsOnSameNode(-1).setConfirmationWindowSize(0).setStaticConnectors(staticConnectors);

      List<BridgeConfiguration> bridgeConfigs = new ArrayList<>();
      bridgeConfigs.add(bridgeConfiguration);
      server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

      QueueConfiguration queueConfig0 = QueueConfiguration.of(queueName0).setAddress(testAddress);
      List<QueueConfiguration> queueConfigs0 = new ArrayList<>();
      queueConfigs0.add(queueConfig0);
      queueConfig0 = QueueConfiguration.of(secondQueue).setAddress(testAddress);
      queueConfigs0.add(queueConfig0);
      server0.getConfiguration().setQueueConfigs(queueConfigs0);

      server0.start();

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

      ClientSession session0 = sf0.createSession(false, true, true);

      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

      final int numMessages = 1000;

      final SimpleString propKey = SimpleString.of("testkey");

      final SimpleString selectorKey = SimpleString.of("animal");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.getBodyBuffer().writeBytes(new byte[1024]);

         message.putIntProperty(propKey, i);

         message.putStringProperty(selectorKey, SimpleString.of("monkey" + i));

         producer0.send(message);
      }

      server1.start();

      // Inserting the duplicateIDs so the bridge will fail in a few
      {
         long[] ids = new long[100];

         Queue queue = server0.locateQueue(SimpleString.of(queueName0));
         LinkedListIterator<MessageReference> iterator = queue.iterator();

         for (int i = 0; i < 100; i++) {
            assertTrue(iterator.hasNext());
            ids[i] = iterator.next().getMessage().getMessageID();
         }

         iterator.close();

         DuplicateIDCache duplicateTargetCache = server1.getPostOffice().getDuplicateIDCache(PostOfficeImpl.BRIDGE_CACHE_STR.concat(forwardAddress));

         TransactionImpl tx = new TransactionImpl(server1.getStorageManager());
         for (long id : ids) {
            byte[] duplicateArray = BridgeImpl.getDuplicateBytes(server0.getNodeManager().getUUID(), id);
            duplicateTargetCache.addToCache(duplicateArray, tx);
         }
         tx.commit();
      }


      ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

      ClientSession session1 = sf1.createSession(false, true, true);

      try {
         session1.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST));
      } catch (Throwable ignored) {
         ignored.printStackTrace();
      }

      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      for (int i = 100; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty(propKey).intValue());
         message.acknowledge();
      }

      session1.commit();

      assertNull(consumer1.receiveImmediate());

      ClientConsumer otherConsumer = session0.createConsumer(secondQueue);
      session0.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = otherConsumer.receive(5000);
         assertNotNull(message);
         // This is validating the Bridge is not messing up with the original message
         // and should make a copy of the message before sending it
         assertEquals(2, message.getPropertyNames().size());
         assertEquals(i, message.getIntProperty(propKey).intValue());
         assertEquals(SimpleString.of("monkey" + i), message.getSimpleStringProperty(selectorKey));
         message.acknowledge();

      }

      consumer1.close();

      session1.deleteQueue(queueName1);

      session1.close();

      sf1.close();

      SimpleString queueName1Str = SimpleString.of(queueName1);
      Wait.assertTrue(() -> server1.locateQueue(queueName1Str) == null);

      server1.stop();

      session0.close();

      sf0.close();

      closeFields();

      Wait.assertEquals(0, () -> loadQueues(server0).size());

   }

   private void closeFields() throws Exception {
      locator.close();
      server0.stop();
      server1.stop();
   }

   @TestTemplate
   public void testWithTransformer() throws Exception {
      internaltestWithTransformer(false);
   }

   @TestTemplate
   public void testWithTransformerFiles() throws Exception {
      internaltestWithTransformer(true);
   }

   private void internaltestWithTransformer(final boolean useFiles) throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, false, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, false, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setTransformerConfiguration(new TransformerConfiguration(SimpleTransformer.class.getName())).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(1024).setStaticConnectors(staticConnectors);

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

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

      ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

      ClientSession session0 = sf0.createSession(false, true, true);

      ClientSession session1 = sf1.createSession(false, true, true);

      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("wibble");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.putStringProperty(propKey, SimpleString.of("bing"));

         message.getBodyBuffer().writeString("doo be doo be doo be doo");

         producer0.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(200);

         assertNotNull(message);

         SimpleString val = (SimpleString) message.getObjectProperty(propKey);

         assertEquals(SimpleString.of("bong"), val);

         String sval = message.getBodyBuffer().readString();

         assertEquals("dee be dee be dee be dee", sval);

         message.acknowledge();

      }

      assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();

      if (server0.getConfiguration().isPersistenceEnabled()) {
         assertEquals(0, loadQueues(server0).size());
      }
   }

   @TestTemplate
   public void testWithTransformerProperties() throws Exception {
      final String propKey = "bridged";
      final String propValue = "true";

      TransformerConfiguration transformerConfiguration = new TransformerConfiguration(AddHeadersTransformer.class.getName());
      transformerConfiguration.getProperties().put(propKey, propValue);

      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, false, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, false, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setTransformerConfiguration(transformerConfiguration).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(1024).setStaticConnectors(staticConnectors);

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

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

      ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

      ClientSession session0 = sf0.createSession(false, true, true);

      ClientSession session1 = sf1.createSession(false, true, true);

      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);

         message.getBodyBuffer().writeString("doo be doo be doo be doo");

         producer0.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(200);

         assertNotNull(message);

         String messagePropVal = message.getStringProperty(propKey);

         assertEquals(propValue, messagePropVal);

         String sval = message.getBodyBuffer().readString();

         assertEquals("doo be doo be doo be doo", sval);

         message.acknowledge();

      }

      assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();

      if (server0.getConfiguration().isPersistenceEnabled()) {
         assertEquals(0, loadQueues(server0).size());
      }
   }

   @TestTemplate
   public void testSawtoothLoad() throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      ActiveMQServer server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);
      server0.getConfiguration().setThreadPoolMaxSize(10);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      ActiveMQServer server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);
      server1.getConfiguration().setThreadPoolMaxSize(10);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      final TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      final TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(0).setStaticConnectors(staticConnectors).setProducerWindowSize(1);

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

      try {
         server1.start();
         server0.start();

         final int numMessages = 300;

         final int totalrepeats = 3;

         final AtomicInteger errors = new AtomicInteger(0);

         // We shouldn't have more than 10K messages pending
         final Semaphore semop = new Semaphore(10000);

         class ConsumerThread extends Thread {

            @Override
            public void run() {
               try {
                  ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server1tc));

                  ClientSessionFactory sf = createSessionFactory(locator);

                  ClientSession session = sf.createSession(false, false);

                  session.start();

                  ClientConsumer consumer = session.createConsumer(queueName1);

                  for (int i = 0; i < numMessages; i++) {
                     ClientMessage message = consumer.receive(5000);

                     assertNotNull(message);

                     message.acknowledge();
                     semop.release();
                     if (i % 1000 == 0) {
                        session.commit();
                     }
                  }

                  session.commit();

                  session.close();
                  sf.close();
                  locator.close();

               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            }
         }

         class ProducerThread extends Thread {

            final int nmsg;

            ProducerThread(int nmsg) {
               this.nmsg = nmsg;
            }

            @Override
            public void run() {
               ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc));

               locator.setBlockOnDurableSend(false).setBlockOnNonDurableSend(false);

               ClientSessionFactory sf = null;

               ClientSession session = null;

               ClientProducer producer = null;

               try {
                  sf = createSessionFactory(locator);

                  session = sf.createSession(false, true, true);

                  producer = session.createProducer(SimpleString.of(testAddress));

                  for (int i = 0; i < nmsg; i++) {
                     assertEquals(0, errors.get());
                     ClientMessage message = session.createMessage(true);

                     message.putIntProperty("seq", i);

                     if (i % 100 == 0) {
                        message.setPriority((byte) (RandomUtil.randomPositiveInt() % 9));
                     } else {
                        message.setPriority((byte) 5);
                     }

                     message.getBodyBuffer().writeBytes(new byte[50]);

                     producer.send(message);
                     assertTrue(semop.tryAcquire(1, 10, TimeUnit.SECONDS));
                  }
               } catch (Throwable e) {
                  e.printStackTrace(System.out);
                  errors.incrementAndGet();
               } finally {
                  try {
                     session.close();
                     sf.close();
                     locator.close();
                  } catch (Exception ignored) {
                     errors.incrementAndGet();
                  }
               }
            }
         }

         for (int repeat = 0; repeat < totalrepeats; repeat++) {
            List<Thread> threads = new ArrayList<>();

            threads.add(new ConsumerThread());
            threads.add(new ProducerThread(numMessages / 2));
            threads.add(new ProducerThread(numMessages / 2));

            for (Thread t : threads) {
               t.start();
            }

            for (Thread t : threads) {
               t.join();
            }

            assertEquals(0, errors.get());
         }
      } finally {
         try {
            server0.stop();
         } catch (Exception ignored) {

         }

         try {
            server1.stop();
         } catch (Exception ignored) {

         }
      }

      assertEquals(0, loadQueues(server0).size());

   }

   @TestTemplate
   public void testBridgeWithPaging() throws Exception {
      ActiveMQServer server0 = null;
      ActiveMQServer server1 = null;

      final int PAGE_MAX = 10 * 1024;

      final int PAGE_SIZE = 1 * 1024;
      try {

         Map<String, Object> server0Params = new HashMap<>();
         server0 = createClusteredServerWithParams(isNetty(), 0, true, PAGE_SIZE, PAGE_MAX, server0Params);

         Map<String, Object> server1Params = new HashMap<>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, true, PAGE_SIZE, -1, server1Params);
         server1.getConfiguration().setJournalBufferTimeout_AIO(10).setJournalBufferTimeout_NIO(10);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         server0.getConfiguration().setIDCacheSize(20000).setJournalBufferTimeout_NIO(10).setJournalBufferTimeout_AIO(10);

         List<String> staticConnectors = new ArrayList<>();
         staticConnectors.add(server1tc.getName());

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(1).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(true).setConfirmationWindowSize(1).setStaticConnectors(staticConnectors);

         bridgeConfiguration.setCallTimeout(1000);

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

         List<String> interceptorToStop = new ArrayList<>();
         interceptorToStop.add(StopInterceptor.class.getName());
         server1.getConfiguration().setIncomingInterceptorClassNames(interceptorToStop);

         StopInterceptor.serverToStop = server0;

         server1.start();
         server0.start();

         locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
         ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

         ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, false, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final int numMessages = 200;

         final SimpleString propKey = SimpleString.of("testkey");

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session0.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[512]);

            message.putIntProperty(propKey, i);

            producer0.send(message);
         }

         session0.commit();

         assertTrue(StopInterceptor.latch.await(1, TimeUnit.HOURS));

         StopInterceptor.thread.join(15000);

         if (StopInterceptor.thread.isAlive()) {
            System.out.println(threadDump("Still alive, stop didn't work!!!"));
            fail("Thread that should restart the server still alive");
         }

         // Restarting the server
         server0.start();

         Map<Integer, AtomicInteger> receivedMsg = new HashMap<>();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = consumer1.receive(5000);

            if (message == null) {
               break;
            }

            Integer msgKey = message.getIntProperty(propKey);

            AtomicInteger msgCount = receivedMsg.get(msgKey);

            if (msgKey.intValue() != i) {
               System.err.println("Message " + msgCount + " received out of order, expected to be " + i + " it's acceptable but not the ideal!");
            }

            if (msgCount == null) {
               msgCount = new AtomicInteger();
               receivedMsg.put(msgKey, msgCount);
            }

            msgCount.incrementAndGet();

            if (i % 500 == 0)
               logger.debug("received {}", i);
         }

         boolean failed = false;

         if (consumer1.receiveImmediate() != null) {
            System.err.println("Unexpected message received");
            failed = true;
         }

         for (int i = 0; i < numMessages; i++) {
            AtomicInteger msgCount = receivedMsg.get(i);
            if (msgCount == null) {
               System.err.println("Msg " + i + " wasn't received");
               failed = true;
            } else if (msgCount.get() > 1) {
               System.err.println("msg " + i + " was received " + msgCount.get() + " times");
               failed = true;
            }

         }

         assertFalse(failed, "Test failed");

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();

      } finally {
         if (locator != null) {
            locator.close();
         }
         try {
            server0.stop();
         } catch (Throwable ignored) {
         }

         try {
            server1.stop();
         } catch (Throwable ignored) {
         }
      }

      assertEquals(0, loadQueues(server0).size());

   }

   // Stops a server after 100 messages received
   public static class StopInterceptor implements Interceptor {

      static ActiveMQServer serverToStop;

      static Thread thread;

      static final ReusableLatch latch = new ReusableLatch(0);

      public static void reset() {
         latch.setCount(1);
         serverToStop = null;
         count = 0;
         thread = null;
      }

      static int count = 0;

      @Override
      public synchronized boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {

         if (packet instanceof SessionSendMessage && ++count == 100) {
            try {
               thread = new Thread("***Server Restarter***") {

                  @Override
                  public void run() {
                     try {
                        serverToStop.fail(false);
                        latch.countDown();
                     } catch (Exception e) {
                        e.printStackTrace();
                     }
                  }
               };

               thread.start();

               latch.await();
               return true;
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         return true;
      }
   }

   @TestTemplate
   public void testBridgeWithLargeMessage() throws Exception {
      ActiveMQServer server0 = null;
      ActiveMQServer server1 = null;

      final int PAGE_MAX = 1024 * 1024;

      final int PAGE_SIZE = 10 * 1024;
      ServerLocator locator = null;
      try {

         Map<String, Object> server0Params = new HashMap<>();
         server0 = createClusteredServerWithParams(isNetty(), 0, true, PAGE_SIZE, PAGE_MAX, server0Params);

         Map<String, Object> server1Params = new HashMap<>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         List<String> staticConnectors = new ArrayList<>();
         staticConnectors.add(server1tc.getName());

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(1024).setStaticConnectors(staticConnectors);

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

         locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
         ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

         ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final int numMessages = 50;

         final SimpleString propKey = SimpleString.of("testkey");

         final int LARGE_MESSAGE_SIZE = 1024;
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session0.createMessage(true);
            message.setBodyInputStream(createFakeLargeStream(LARGE_MESSAGE_SIZE));

            message.putIntProperty(propKey, i);

            producer0.send(message);
         }

         session0.commit();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = consumer1.receive(5000);

            assertNotNull(message);

            assertEquals(i, message.getObjectProperty(propKey));

            ActiveMQBuffer buff = message.getBodyBuffer();

            for (int posMsg = 0; posMsg < LARGE_MESSAGE_SIZE; posMsg++) {
               assertEquals(getSamplebyte(posMsg), buff.readByte());
            }

            message.acknowledge();
         }

         session1.commit();

         assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();

      } finally {
         if (locator != null) {
            locator.close();
         }
         try {
            server0.stop();
         } catch (Throwable ignored) {
         }

         try {
            server1.stop();
         } catch (Throwable ignored) {
         }
      }

      assertEquals(0, loadQueues(server0).size());
   }

   @TestTemplate
   public void testBridgeWithVeryLargeMessage() throws Exception {
      ActiveMQServer server0 = null;
      ActiveMQServer server1 = null;

      final int PAGE_MAX = 1024 * 1024;

      final int PAGE_SIZE = 10 * 1024;
      ServerLocator locator = null;

      try {
         Map<String, Object> server0Params = new HashMap<>();
         server0 = createClusteredServerWithParams(isNetty(), 0, true, PAGE_SIZE, PAGE_MAX, server0Params);

         Map<String, Object> server1Params = new HashMap<>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         List<String> staticConnectors = new ArrayList<>();
         staticConnectors.add(server1tc.getName());

         int minLargeMessageSize = 200 * 1024;

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(1024).setStaticConnectors(staticConnectors).setMinLargeMessageSize(minLargeMessageSize).setProducerWindowSize(minLargeMessageSize / 2);

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

         locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));

         ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

         ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         //create a large message bigger than Integer.MAX_VALUE
         final long largeMessageSize = 200 * 1024;

         ClientMessage largeMessage = createLargeMessage(session0, largeMessageSize);

         producer0.send(largeMessage);

         session0.commit();

         //check target queue for large message arriving
         Wait.waitFor(() -> session1.queueQuery(SimpleString.of(queueName1)).getMessageCount() > 0);

         //receive the message
         ClientMessage message = consumer1.receive(5000);
         message.acknowledge();

         File outputFile = new File(getTemporaryDir(), "huge_message_received.dat");

         logger.debug("-----message save to: {}", outputFile.getAbsolutePath());
         FileOutputStream fileOutputStream = new FileOutputStream(outputFile);

         BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutputStream);

         message.setOutputStream(bufferedOutput);

         if (!message.waitOutputStreamCompletion(5 * 60 * 1000)) {
            fail("message didn't get received to disk in 5 min. Is the machine slow?");
         }
         session1.commit();

         assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();

      } finally {
         if (locator != null) {
            locator.close();
         }
         try {
            server0.stop();
         } catch (Throwable ignored) {
         }

         try {
            server1.stop();
         } catch (Throwable ignored) {
         }
      }

      assertEquals(0, loadQueues(server0).size());
   }

   private ClientMessage createLargeMessage(ClientSession session, long largeMessageSize) throws Exception {
      File fileInput = new File(getTemporaryDir(), "huge_message_to_send.dat");

      createFile(fileInput, largeMessageSize);

      logger.debug("File created at: {}", fileInput.getAbsolutePath());

      ClientMessage message = session.createMessage(Message.BYTES_TYPE, true);

      FileInputStream fileInputStream = new FileInputStream(fileInput);
      BufferedInputStream bufferedInput = new BufferedInputStream(fileInputStream);

      message.setBodyInputStream(bufferedInput);

      return message;
   }

   private static void createFile(final File file, final long fileSize) throws IOException {
      if (file.exists()) {
         logger.warn("---file already there {}", file.length());
         return;
      }
      FileOutputStream fileOut = new FileOutputStream(file);
      BufferedOutputStream buffOut = new BufferedOutputStream(fileOut);
      byte[] outBuffer = new byte[1024 * 1024];
      logger.debug(" --- creating file, size: {}", fileSize);
      for (long i = 0; i < fileSize; i += outBuffer.length) {
         buffOut.write(outBuffer);
      }
      buffOut.close();
   }

   @TestTemplate
   public void testNullForwardingAddress() throws Exception {
      Map<String, Object> server0Params = new HashMap<>();
      server0 = createClusteredServerWithParams(isNetty(), 0, false, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, false, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String queueName1 = "queue1";

      Map<String, TransportConfiguration> connectors = new HashMap<>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      final int messageSize = 1024;

      final int numMessages = 10;

      List<String> staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());
      // do not set forwarding address (defaults to null) to use messages' original address
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("bridge1").setQueueName(queueName0).setRetryInterval(1000).setReconnectAttemptsOnSameNode(-1).setUseDuplicateDetection(false).setConfirmationWindowSize(numMessages * messageSize / 2).setStaticConnectors(staticConnectors);

      List<BridgeConfiguration> bridgeConfigs = new ArrayList<>();
      bridgeConfigs.add(bridgeConfiguration);
      server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

      QueueConfiguration queueConfig0 = QueueConfiguration.of(queueName0).setAddress(testAddress);
      List<QueueConfiguration> queueConfigs0 = new ArrayList<>();
      queueConfigs0.add(queueConfig0);
      server0.getConfiguration().setQueueConfigs(queueConfigs0);

      // on server #1, we bind queueName1 to same address testAddress
      QueueConfiguration queueConfig1 = QueueConfiguration.of(queueName1).setAddress(testAddress);
      List<QueueConfiguration> queueConfigs1 = new ArrayList<>();
      queueConfigs1.add(queueConfig1);
      server1.getConfiguration().setQueueConfigs(queueConfigs1);

      server1.start();
      server0.start();

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

      ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

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
         ClientMessage message = consumer1.receive(200);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();
      closeFields();
   }

   @TestTemplate
   public void testInjectedTransformer() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("myAddress");
      final SimpleString QUEUE = SimpleString.of("myQueue");
      final String BRIDGE = "myBridge";

      ServiceRegistryImpl serviceRegistry = new ServiceRegistryImpl();
      Transformer transformer = message -> null;

      serviceRegistry.addBridgeTransformer(BRIDGE, transformer);
      Configuration config = createDefaultInVMConfig().addConnectorConfiguration("in-vm", new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      ActiveMQServer server = addServer(new ActiveMQServerImpl(config, null, null, null, serviceRegistry));
      server.start();
      server.waitForActivation(100, TimeUnit.MILLISECONDS);
      server.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      List<String> connectors = new ArrayList<>();
      connectors.add("in-vm");
      server.deployBridge(new BridgeConfiguration().setName(BRIDGE).setQueueName(QUEUE.toString()).setForwardingAddress(ADDRESS.toString()).setStaticConnectors(connectors));
      Bridge bridge = server.getClusterManager().getBridges().get(BRIDGE);
      assertNotNull(bridge);
      assertEquals(transformer, ((BridgeImpl) bridge).getTransformer());
   }

   @TestTemplate
   public void testDefaultConfirmationWindowSize() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("myAddress");
      final SimpleString QUEUE = SimpleString.of("myQueue");
      final SimpleString FORWARDING_ADDRESS = SimpleString.of("myForwardingAddress");
      final SimpleString FORWARDING_QUEUE = SimpleString.of("myForwardingQueue");
      final String BRIDGE = "myBridge";

      Configuration config = createDefaultConfig(0, isNetty()).addConnectorConfiguration("myConnector", new TransportConfiguration(getConnector()));
      ActiveMQServer server = addServer(new ActiveMQServerImpl(config));
      server.start();
      server.waitForActivation(100, TimeUnit.MILLISECONDS);
      server.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      server.createQueue(QueueConfiguration.of(FORWARDING_QUEUE).setAddress(FORWARDING_ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      List<String> connectors = new ArrayList<>();
      connectors.add("myConnector");
      server.deployBridge(new BridgeConfiguration()
                             .setName(BRIDGE)
                             .setQueueName(QUEUE.toString())
                             .setForwardingAddress(FORWARDING_ADDRESS.toString())
                             .setStaticConnectors(connectors));

      // now we actually have to use the bridge to make sure it connected correctly
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(getConnector())));
      ClientSessionFactory sf = addSessionFactory(locator.createSessionFactory());
      ClientSession session = addClientSession(sf.createSession(false, true, true));
      ClientProducer producer = addClientProducer(session.createProducer(ADDRESS));
      ClientConsumer consumer = addClientConsumer(session.createConsumer(FORWARDING_QUEUE));
      session.start();
      producer.send(session.createMessage(true));
      assertNotNull(consumer.receive(200));
   }

   @TestTemplate
   public void testManagementLeak() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("myAddress");
      final SimpleString QUEUE = SimpleString.of("myQueue");
      final SimpleString FORWARDING_ADDRESS = SimpleString.of("myForwardingAddress");
      final SimpleString FORWARDING_QUEUE = SimpleString.of("myForwardingQueue");
      final String BRIDGE = "myBridge";

      ActiveMQServer server = addServer(new ActiveMQServerImpl(createDefaultConfig(0, isNetty()).addConnectorConfiguration("myConnector", new TransportConfiguration(getConnector()))));
      server.start();
      server.waitForActivation(100, TimeUnit.MILLISECONDS);
      server.createQueue(QueueConfiguration.of(QUEUE)
                            .setAddress(ADDRESS)
                            .setRoutingType(RoutingType.ANYCAST)
                            .setDurable(false));
      server.createQueue(QueueConfiguration.of(FORWARDING_QUEUE)
                            .setAddress(FORWARDING_ADDRESS)
                            .setRoutingType(RoutingType.ANYCAST)
                            .setDurable(false));
      List<String> connectors = new ArrayList<>();
      connectors.add("myConnector");
      final int concurrency = 20;
      BridgeConfiguration config = new BridgeConfiguration()
         .setName(BRIDGE)
         .setQueueName(QUEUE.toString())
         .setForwardingAddress(FORWARDING_ADDRESS.toString())
         .setStaticConnectors(connectors)
         .setConcurrency(concurrency);
      server.deployBridge(config);
      assertEquals(concurrency, server.getManagementService().getBridgeControls().size());
      server.destroyBridge(config.getName());

      assertEquals(0, server.getManagementService().getBridgeControls().size());
   }

   @TestTemplate
   public void testPendingAcksNeverArriveOnStop() throws Exception {
      testPendingAcksNeverArrive(true, false);
   }

   @TestTemplate
   public void testPendingAcksNeverArriveOnPause() throws Exception {
      testPendingAcksNeverArrive(false, false);
   }

   @TestTemplate
   public void testPendingAcksNeverArriveOnStopWithLargeMessages() throws Exception {
      testPendingAcksNeverArrive(true, true);
   }

   @TestTemplate
   public void testPendingAcksNeverArriveOnPauseWithLargeMessages() throws Exception {
      testPendingAcksNeverArrive(false, true);
   }

   private void testPendingAcksNeverArrive(boolean stop, boolean large) throws Exception {
      server0 = createClusteredServerWithParams(isNetty(), 0, true, null);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";
      final long pendingAckTimeout = 2000;
      final int messageSize = 1024;
      final int numMessages = 10;

      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), null);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

      server0.getConfiguration()
             .setConnectorConfigurations(Map.of(server1tc.getName(), server1tc))
             .setBridgeConfigurations(Arrays.asList(new BridgeConfiguration()
                                                       .setName("bridge1")
                                                       .setQueueName(queueName0)
                                                       .setForwardingAddress(forwardAddress)
                                                       .setRetryInterval(1000)
                                                       .setReconnectAttemptsOnSameNode(-1)
                                                       .setUseDuplicateDetection(false)
                                                       .setConfirmationWindowSize(numMessages * messageSize / 2)
                                                       .setMinLargeMessageSize(large ? (messageSize / 2) : (messageSize * 2))
                                                       .setPendingAckTimeout(pendingAckTimeout)
                                                       .setStaticConnectors(Arrays.asList(server1tc.getName()))));
      server0.getConfiguration().setQueueConfigs(Arrays.asList(QueueConfiguration.of(queueName0).setAddress(testAddress)));
      server0.start();

      // this interceptor will prevent the target from returning any send acknowledgements
      Interceptor sendBlockingInterceptor = (packet, connection) -> {
         if (packet.getType() == PacketImpl.SESS_SEND || packet.getType() == PacketImpl.SESS_SEND_LARGE) {
            return false;
         }
         return true;
      };

      server1.getConfiguration().setQueueConfigs(Arrays.asList(QueueConfiguration.of(queueName1).setAddress(forwardAddress)));
      server1.start();
      server1.getRemotingService().addIncomingInterceptor(sendBlockingInterceptor);
      Bridge bridge = server0.getClusterManager().getBridges().get("bridge1");
      Wait.assertTrue(() -> (bridge.isConnected()), 2000, 100);

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = addSessionFactory(locator.createSessionFactory(server0tc));
      ClientSession session0 = sf0.createSession(false, true, true);
      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));
      final byte[] bytes = new byte[messageSize];

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);
         message.putIntProperty(propKey, i);
         message.getBodyBuffer().writeBytes(bytes);
         producer0.send(message);
      }

      session0.close();
      sf0.close();

      Wait.assertEquals((long) numMessages, () -> bridge.getMetrics().getMessagesPendingAcknowledgement(), 2000, 100);
      long start = System.currentTimeMillis();
      BridgeImpl.State desiredState;
      if (stop) {
         bridge.stop();
         desiredState = BridgeImpl.State.STOPPED;
      } else {
         bridge.pause();
         desiredState = BridgeImpl.State.PAUSED;
      }
      Wait.assertEquals(desiredState, () -> ((BridgeImpl)bridge).getState(), pendingAckTimeout, 25);
      assertTrue(System.currentTimeMillis() - start >= pendingAckTimeout);
      Wait.assertEquals((long) numMessages, () -> server0.locateQueue(queueName0).getMessageCount(), 2000, 100);
      Wait.assertEquals(0L, () -> server0.locateQueue(queueName0).getDeliveringCount(), 2000, 100);
   }

   @TestTemplate
   public void testPendingAcksEventuallyArriveOnStop() throws Exception {
      testPendingAcksEventuallyArrive(true, false);
   }

   @TestTemplate
   public void testPendingAcksEventuallyArriveOnPause() throws Exception {
      testPendingAcksEventuallyArrive(false, false);
   }

   @TestTemplate
   public void testPendingAcksEventuallyArriveOnStopWithLargeMessages() throws Exception {
      testPendingAcksEventuallyArrive(true, true);
   }

   @TestTemplate
   public void testPendingAcksEventuallyArriveOnPauseWithLargeMessages() throws Exception {
      testPendingAcksEventuallyArrive(false, true);
   }

   private void testPendingAcksEventuallyArrive(boolean stop, boolean large) throws Exception {
      server0 = createClusteredServerWithParams(isNetty(), 0, true, null);

      Map<String, Object> server1Params = new HashMap<>();
      addTargetParameters(server1Params);
      server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";
      final long pendingAckTimeout = 2000;
      final int messageSize = 1024;
      final int numMessages = 10;

      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), null);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);

      server0.getConfiguration()
             .setConnectorConfigurations(Map.of(server1tc.getName(), server1tc))
             .setBridgeConfigurations(Arrays.asList(new BridgeConfiguration()
                                                       .setName("bridge1")
                                                       .setQueueName(queueName0)
                                                       .setForwardingAddress(forwardAddress)
                                                       .setRetryInterval(1000)
                                                       .setReconnectAttemptsOnSameNode(-1)
                                                       .setUseDuplicateDetection(false)
                                                       .setConfirmationWindowSize(numMessages * messageSize / 2)
                                                       .setMinLargeMessageSize(large ? (messageSize / 2) : (messageSize * 2))
                                                       .setPendingAckTimeout(pendingAckTimeout)
                                                       .setStaticConnectors(Arrays.asList(server1tc.getName()))));
      server0.getConfiguration().setQueueConfigs(Arrays.asList(QueueConfiguration.of(queueName0).setAddress(testAddress)));
      server0.start();

      // this interceptor will prevent the target from returning any send acks until a certain amount of time has elapsed
      final CountDownLatch opLatch = new CountDownLatch(1);
      Interceptor sendBlockingInterceptor = (packet, connection) -> {
         if (packet.getType() == PacketImpl.SESS_SEND || packet.getType() == PacketImpl.SESS_SEND_LARGE) {
            try {
               opLatch.await(pendingAckTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
               throw new RuntimeException(e);
            }
         }
         return true;
      };

      server1.getConfiguration().setQueueConfigs(Arrays.asList(QueueConfiguration.of(queueName1).setAddress(forwardAddress)));
      server1.start();
      server1.getRemotingService().addIncomingInterceptor(sendBlockingInterceptor);
      Bridge bridge = server0.getClusterManager().getBridges().get("bridge1");
      Wait.assertTrue(() -> (bridge.isConnected()), 2000, 100);

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc));
      ClientSessionFactory sf0 = addSessionFactory(locator.createSessionFactory(server0tc));
      ClientSession session0 = sf0.createSession(false, true, true);
      ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));
      final byte[] bytes = new byte[messageSize];

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session0.createMessage(true);
         message.putIntProperty(propKey, i);
         message.getBodyBuffer().writeBytes(bytes);
         producer0.send(message);
      }

      session0.close();
      sf0.close();

      Wait.assertEquals((long) numMessages, () -> bridge.getMetrics().getMessagesPendingAcknowledgement(), 2000, 100);
      assertEquals((long) numMessages, server0.locateQueue(queueName0).getDeliveringCount());
      BridgeImpl.State desiredState;
      if (stop) {
         bridge.stop();
         desiredState = BridgeImpl.State.STOPPED;
      } else {
         bridge.pause();
         desiredState = BridgeImpl.State.PAUSED;
      }
      Thread.sleep(pendingAckTimeout / 2);
      opLatch.countDown();
      Wait.assertEquals(desiredState, () -> ((BridgeImpl)bridge).getState(), pendingAckTimeout, 25);
      Wait.assertEquals(0L, () -> server0.locateQueue(queueName0).getMessageCount(), 2000, 100);
      Wait.assertEquals(0L, () -> server0.locateQueue(queueName0).getDeliveringCount(), 2000, 100);
      Wait.assertEquals((long) numMessages, () -> server1.locateQueue(queueName1).getMessageCount(), 2000, 100);
   }

   /**
    * It will inspect the journal directly and determine if there are queues on this journal,
    *
    * @return a Map containing the reference counts per queue
    */
   protected Map<Long, AtomicInteger> loadQueues(ActiveMQServer serverToInvestigate) throws Exception {
      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(serverToInvestigate.getConfiguration().getJournalLocation(), 1);

      JournalImpl messagesJournal = new JournalImpl(serverToInvestigate.getConfiguration().getJournalFileSize(), serverToInvestigate.getConfiguration().getJournalMinFiles(), serverToInvestigate.getConfiguration().getJournalPoolFiles(), 0, 0, messagesFF, "activemq-data", "amq", 1);
      List<RecordInfo> records = new LinkedList<>();

      List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

      messagesJournal.start();
      messagesJournal.load(records, preparedTransactions, null);

      // These are more immutable integers
      Map<Long, AtomicInteger> messageRefCounts = new HashMap<>();

      for (RecordInfo info : records) {
         Object o = DescribeJournal.newObjectEncoding(info);
         if (info.getUserRecordType() == JournalRecordIds.ADD_REF) {
            DescribeJournal.ReferenceDescribe ref = (DescribeJournal.ReferenceDescribe) o;
            AtomicInteger count = messageRefCounts.get(ref.refEncoding.queueID);
            if (count == null) {
               count = new AtomicInteger(1);
               messageRefCounts.put(ref.refEncoding.queueID, count);
            } else {
               count.incrementAndGet();
            }
         }
      }

      messagesJournal.stop();

      return messageRefCounts;

   }
}
