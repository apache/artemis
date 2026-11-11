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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.management.MBeanServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeMetrics;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.tests.integration.SimpleNotificationService;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BridgeControlTest extends ManagementTestBase {

   private ActiveMQServer server_0;
   private ActiveMQServer server_1;

   private BridgeConfiguration bridgeConfig;

   @Test
   public void testAttributes() throws Exception {
      checkResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(bridgeConfig.getName()));
      BridgeControl bridgeControl = createBridgeControl(bridgeConfig.getName(), mbeanServer);

      assertEquals(bridgeConfig.getName(), bridgeControl.getName());
      assertEquals(bridgeConfig.getDiscoveryGroupName(), bridgeControl.getDiscoveryGroupName());
      assertEquals(bridgeConfig.getQueueName(), bridgeControl.getQueueName());
      assertEquals(bridgeConfig.getForwardingAddress(), bridgeControl.getForwardingAddress());
      assertEquals(bridgeConfig.getFilterString(), bridgeControl.getFilterString());
      assertEquals(bridgeConfig.getRetryInterval(), bridgeControl.getRetryInterval());
      assertEquals(bridgeConfig.getRetryIntervalMultiplier(), bridgeControl.getRetryIntervalMultiplier(), 0.000001);
      assertEquals(bridgeConfig.getMaxRetryInterval(), bridgeControl.getMaxRetryInterval());
      assertEquals(bridgeConfig.getReconnectAttempts(), bridgeControl.getReconnectAttempts());
      assertEquals(bridgeConfig.isUseDuplicateDetection(), bridgeControl.isUseDuplicateDetection());
      Map<String, Object> bridgeMetrics = bridgeControl.getMetrics();
      assertEquals(0L, bridgeControl.getMessagesPendingAcknowledgement());
      assertEquals(0L, bridgeControl.getMessagesAcknowledged());
      assertEquals(0L, bridgeMetrics.get(BridgeMetrics.MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY));
      assertEquals(0L, bridgeMetrics.get(BridgeMetrics.MESSAGES_ACKNOWLEDGED_KEY));

      String[] connectorPairData = bridgeControl.getStaticConnectors();
      assertEquals(bridgeConfig.getStaticConnectors().get(0), connectorPairData[0]);

      assertTrue(bridgeControl.isStarted());
   }

   @Test
   public void testStartStop() throws Exception {
      checkResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(bridgeConfig.getName()));
      BridgeControl bridgeControl = createBridgeControl(bridgeConfig.getName(), mbeanServer);

      // started by the server
      assertTrue(bridgeControl.isStarted());

      bridgeControl.stop();
      assertFalse(bridgeControl.isStarted());

      bridgeControl.start();
      assertTrue(bridgeControl.isStarted());
   }

   @Test
   public void testNotifications() throws Exception {
      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();
      BridgeControl bridgeControl = createBridgeControl(bridgeConfig.getName(), mbeanServer);

      server_0.getManagementService().addNotificationListener(notifListener);

      assertEquals(0, notifListener.count(CoreNotificationType.BRIDGE_STOPPED));

      bridgeControl.stop();

      Wait.assertEquals(1, () -> notifListener.count(CoreNotificationType.BRIDGE_STOPPED), 5000, 100);
      Notification notif = notifListener.findAny(CoreNotificationType.BRIDGE_STOPPED);
      assertEquals(CoreNotificationType.BRIDGE_STOPPED, notif.getType());
      assertEquals(bridgeControl.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());

      bridgeControl.start();

      Wait.assertEquals(2, () -> notifListener.count(CoreNotificationType.BRIDGE_STOPPED, CoreNotificationType.BRIDGE_STARTED), 5000, 100);
      notif = notifListener.findAny(CoreNotificationType.BRIDGE_STARTED);
      assertEquals(CoreNotificationType.BRIDGE_STARTED, notif.getType());
      assertEquals(bridgeControl.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Map<String, Object> acceptorParams = new HashMap<>();
      acceptorParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(), acceptorParams, RandomUtil.randomUUIDString());

      TransportConfiguration connectorConfig = new TransportConfiguration(InVMConnectorFactory.class.getName(), acceptorParams, RandomUtil.randomUUIDString());

      QueueConfiguration sourceQueueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setDurable(false);
      QueueConfiguration targetQueueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setDurable(false);
      List<String> connectors = new ArrayList<>();
      connectors.add(connectorConfig.getName());

      Configuration conf_1 = createBasicConfig().addAcceptorConfiguration(acceptorConfig).addQueueConfiguration(targetQueueConfig);

      bridgeConfig = new BridgeConfiguration().setName(RandomUtil.randomUUIDString()).setQueueName(sourceQueueConfig.getName().toString()).setForwardingAddress(targetQueueConfig.getAddress().toString()).setRetryInterval(RandomUtil.randomPositiveLong()).setRetryIntervalMultiplier(RandomUtil.randomDouble()).setInitialConnectAttempts(RandomUtil.randomPositiveInt()).setReconnectAttempts(RandomUtil.randomPositiveInt()).setReconnectAttemptsOnSameNode(RandomUtil.randomPositiveInt()).setUseDuplicateDetection(RandomUtil.randomBoolean()).setConfirmationWindowSize(RandomUtil.randomPositiveInt()).setStaticConnectors(connectors).setPassword(CLUSTER_PASSWORD);

      Configuration conf_0 = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName())).addConnectorConfiguration(connectorConfig.getName(), connectorConfig).addQueueConfiguration(sourceQueueConfig).addBridgeConfiguration(bridgeConfig);

      server_1 = addServer(ActiveMQServers.newActiveMQServer(conf_1, createMBeanServer(), false));
      addServer(server_1);
      server_1.start();

      server_0 = addServer(ActiveMQServers.newActiveMQServer(conf_0, mbeanServer, false));
      addServer(server_0);
      server_0.start();
   }

   protected BridgeControl createBridgeControl(final String name, final MBeanServer mbeanServer1) throws Exception {
      return ManagementControlHelper.createBridgeControl(name, mbeanServer1);
   }
}
