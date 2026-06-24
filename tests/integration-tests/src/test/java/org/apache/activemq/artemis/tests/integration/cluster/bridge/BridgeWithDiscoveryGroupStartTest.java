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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class BridgeWithDiscoveryGroupStartTest extends ActiveMQTestBase {

   @Parameters(name = "isNetty={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public BridgeWithDiscoveryGroupStartTest(boolean netty) {
      this.netty = netty;
   }

   private final boolean netty;

   private static final int TIMEOUT = 2000;

   protected boolean isNetty() {
      return netty;
   }

   @TestTemplate
   public void testStartStop() throws Exception {
      enableDiscoveryForTest();
      Map<String, Object> server0Params = new HashMap<>();
      ActiveMQServer server0 = createClusteredServerWithParams(isNetty(), 0, true, server0Params);

      Map<String, Object> server1Params = new HashMap<>();
      if (isNetty()) {
         server1Params.put("port", TransportConstants.DEFAULT_PORT + 1);
      } else {
         server1Params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, 1);
      }
      ActiveMQServer server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);
      ServerLocator locator = null;
      try {
         Map<String, TransportConfiguration> connectors = new HashMap<>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         final String groupAddress = getUDPDiscoveryAddress();
         final int port = getUDPDiscoveryPort();

         List<String> list = new ArrayList<>();
         list.add(server1tc.getName());

         UDPBroadcastEndpointFactory endpoint = new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(port);

         BroadcastGroupConfiguration bcConfig = new BroadcastGroupConfiguration().setName("bg1").setBroadcastPeriod(250).setConnectorInfos(list).setEndpointFactory(endpoint);

         server0.getConfiguration().getBroadcastGroupConfigurations().add(bcConfig);

         DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(endpoint);

         server0.getConfiguration().getDiscoveryGroupConfigurations().put(dcConfig.getName(), dcConfig);

         final String bridgeName = "bridge1";

         List<String> staticConnectors = new ArrayList<>();
         staticConnectors.add(server1tc.getName());
         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName(bridgeName).setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(1000).setReconnectAttempts(0).setReconnectAttemptsOnSameNode(0).setConfirmationWindowSize(1024).setStaticConnectors(staticConnectors);

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

         locator = ActiveMQClient.createServerLocatorWithoutHA(server0tc, server1tc);
         ClientSessionFactory sf0 = locator.createSessionFactory(server0tc);

         ClientSessionFactory sf1 = locator.createSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(SimpleString.of(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final int numMessages = 10;

         final SimpleString propKey = SimpleString.of("testkey");

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session0.createMessage(false);

            message.putIntProperty(propKey, i);

            producer0.send(message);
         }

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = consumer1.receive(BridgeWithDiscoveryGroupStartTest.TIMEOUT);

            assertNotNull(message);

            assertEquals(i, message.getObjectProperty(propKey));

            message.acknowledge();
         }

         assertNull(consumer1.receiveImmediate());

         Bridge bridge = server0.getClusterManager().getBridges().get(bridgeName);

         bridge.stop();
         bridge.flushExecutor();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session0.createMessage(false);

            message.putIntProperty(propKey, i);

            producer0.send(message);
         }

         assertNull(consumer1.receiveImmediate());

         bridge.start();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = consumer1.receive(BridgeWithDiscoveryGroupStartTest.TIMEOUT);

            assertNotNull(message);

            assertEquals(i, message.getObjectProperty(propKey));

            message.acknowledge();
         }

         assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();
      } finally {
         if (locator != null) {
            locator.close();
         }
         server0.stop();

         server1.stop();
      }

   }

   private String getConnector() {
      if (isNetty()) {
         return NettyConnectorFactory.class.getName();
      } else {
         return InVMConnectorFactory.class.getName();
      }
   }
}
