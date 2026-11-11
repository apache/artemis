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
package org.apache.activemq.artemis.tests.integration.bridge;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.management.MBeanServer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to verify the isConnected() status reported by a Core Bridge correctly indicates when the bridge is connected.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class BridgeConnectionStatusTest extends ActiveMQTestBase {

   private ActiveMQServer sourceServer;
   private ActiveMQServer targetServer;

   private MBeanServer mbeanServer;

   private String getServer0URL() {
      return "vm://0";
   }

   private String getServer1URL() {
      return "vm://1";
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      this.mbeanServer = createMBeanServer();

      sourceServer = createServer(false, createBasicConfig());
      sourceServer.getConfiguration().addAcceptorConfiguration("acceptor", getServer0URL());
      sourceServer.getConfiguration().addConnectorConfiguration("connector", getServer1URL());
      sourceServer.setMBeanServer(this.mbeanServer);

      targetServer = createServer(false, createBasicConfig());
      targetServer.getConfiguration().addAcceptorConfiguration("acceptor", getServer1URL());

      // Start source but not target
      sourceServer.start();
   }

   @Test
   public void testBridgeConnectionStatus() throws Exception {
      SimpleString source = SimpleString.of("source");
      SimpleString target = SimpleString.of("target");

      sourceServer.createQueue(QueueConfiguration.of(source).setRoutingType(RoutingType.ANYCAST));

      sourceServer.deployBridge(
          new BridgeConfiguration()
              .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
              .setName("bridge")
              .setForwardingAddress(target.toString())
              .setQueueName(source.toString())
              .setConfirmationWindowSize(10)
              .setConcurrency(1)
              .setStaticConnectors(Arrays.asList("connector"))
              .setReconnectAttempts(-1)
              .setRetryInterval(100)
              .setRetryIntervalMultiplier(1.0)
              .setMaxRetryInterval(100)
      );

      Bridge bridge = sourceServer.getClusterManager().getBridges().get("bridge");
      assertNotNull(bridge);

      // Use BridgeControl to access the JMX MBean.
      BridgeControl bridgeControl = ManagementControlHelper.createBridgeControl("bridge", this.mbeanServer);

      // Verify not connected at start. Also check via JMX.
      assertFalse(bridge.isConnected());
      assertFalse(bridgeControl.isConnected());

      // Start the second server so the bridge can connect and verify connection is achieved. Also check via JMX.
      targetServer.getConfiguration().addQueueConfiguration(QueueConfiguration.of(target).setRoutingType(RoutingType.ANYCAST));
      targetServer.start();
      Wait.assertTrue(bridge::isConnected, 2000, 50);
      assertTrue(bridgeControl.isConnected());

      // Now stop the second server and verify the bridge reports disconnected. Also check via JMX.
      targetServer.stop();
      Wait.assertFalse(() -> bridge.isConnected(), 2000, 50);
      assertFalse(bridgeControl.isConnected());
   }
}