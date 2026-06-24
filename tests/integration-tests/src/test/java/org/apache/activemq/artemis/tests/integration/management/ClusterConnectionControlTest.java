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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.json.JsonArray;
import javax.management.MBeanServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionMetrics;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.tests.integration.SimpleNotificationService;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusterConnectionControlTest extends ManagementTestBase {


   private ActiveMQServer server_0;

   private ClusterConnectionConfiguration clusterConnectionConfig1;

   private ClusterConnectionConfiguration clusterConnectionConfig2;

   private ActiveMQServer server_1;

   private MBeanServer mbeanServer_1;


   @Test
   public void testAttributes1() throws Exception {
      checkResource(ObjectNameBuilder.DEFAULT.getClusterConnectionObjectName(clusterConnectionConfig1.getName()));

      ClusterConnectionControl clusterConnectionControl = createManagementControl(clusterConnectionConfig1.getName());

      assertEquals(clusterConnectionConfig1.getName(), clusterConnectionControl.getName());
      assertEquals(clusterConnectionConfig1.getAddress(), clusterConnectionControl.getAddress());
      assertEquals(clusterConnectionConfig1.getDiscoveryGroupName(), clusterConnectionControl.getDiscoveryGroupName());
      assertEquals(clusterConnectionConfig1.getRetryInterval(), clusterConnectionControl.getRetryInterval());
      assertEquals(clusterConnectionConfig1.isDuplicateDetection(), clusterConnectionControl.isDuplicateDetection());
      assertEquals(clusterConnectionConfig1.getMessageLoadBalancingType().toString(), clusterConnectionControl.getMessageLoadBalancingType());
      assertEquals(clusterConnectionConfig1.getMaxHops(), clusterConnectionControl.getMaxHops());
      assertEquals(clusterConnectionConfig1.getProducerWindowSize(), clusterConnectionControl.getProducerWindowSize());
      assertEquals(0L, clusterConnectionControl.getMessagesPendingAcknowledgement());
      assertEquals(0L, clusterConnectionControl.getMessagesAcknowledged());
      Map<String, Object> clusterMetrics = clusterConnectionControl.getMetrics();
      assertEquals(0L, clusterMetrics.get(ClusterConnectionMetrics.MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY));
      assertEquals(0L, clusterMetrics.get(ClusterConnectionMetrics.MESSAGES_ACKNOWLEDGED_KEY));
      assertNull(clusterConnectionControl.getBridgeMetrics("bad"));

      Object[] connectors = clusterConnectionControl.getStaticConnectors();
      assertEquals(1, connectors.length);
      String connector = (String) connectors[0];
      assertEquals(clusterConnectionConfig1.getStaticConnectors().get(0), connector);

      String jsonString = clusterConnectionControl.getStaticConnectorsAsJSON();
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1, array.size());
      assertEquals(clusterConnectionConfig1.getStaticConnectors().get(0), array.getString(0));

      assertNull(clusterConnectionControl.getDiscoveryGroupName());

      assertTrue(clusterConnectionControl.isStarted());
   }

   @Test
   public void testAttributes2() throws Exception {
      checkResource(ObjectNameBuilder.DEFAULT.getClusterConnectionObjectName(clusterConnectionConfig2.getName()));

      ClusterConnectionControl clusterConnectionControl = createManagementControl(clusterConnectionConfig2.getName());

      assertEquals(clusterConnectionConfig2.getName(), clusterConnectionControl.getName());
      assertEquals(clusterConnectionConfig2.getAddress(), clusterConnectionControl.getAddress());
      assertEquals(clusterConnectionConfig2.getDiscoveryGroupName(), clusterConnectionControl.getDiscoveryGroupName());
      assertEquals(clusterConnectionConfig2.getRetryInterval(), clusterConnectionControl.getRetryInterval());
      assertEquals(clusterConnectionConfig2.isDuplicateDetection(), clusterConnectionControl.isDuplicateDetection());
      assertEquals(clusterConnectionConfig2.getMessageLoadBalancingType().toString(), clusterConnectionControl.getMessageLoadBalancingType());
      assertEquals(clusterConnectionConfig2.getMaxHops(), clusterConnectionControl.getMaxHops());
      assertEquals(clusterConnectionConfig2.getProducerWindowSize(), clusterConnectionControl.getProducerWindowSize());

      Object[] connectorPairs = clusterConnectionControl.getStaticConnectors();
      assertEquals(0, connectorPairs.length);

      String jsonPairs = clusterConnectionControl.getStaticConnectorsAsJSON();
      assertEquals("[]", jsonPairs);

      assertEquals(clusterConnectionConfig2.getDiscoveryGroupName(), clusterConnectionControl.getDiscoveryGroupName());
   }

   @Test
   public void testStartStop() throws Exception {
      checkResource(ObjectNameBuilder.DEFAULT.getClusterConnectionObjectName(clusterConnectionConfig1.getName()));
      ClusterConnectionControl clusterConnectionControl = createManagementControl(clusterConnectionConfig1.getName());

      // started by the server
      assertTrue(clusterConnectionControl.isStarted());

      clusterConnectionControl.stop();
      assertFalse(clusterConnectionControl.isStarted());

      clusterConnectionControl.start();
      assertTrue(clusterConnectionControl.isStarted());
   }

   @Test
   public void testNotifications() throws Exception {
      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();
      checkResource(ObjectNameBuilder.DEFAULT.getClusterConnectionObjectName(clusterConnectionConfig1.getName()));
      ClusterConnectionControl clusterConnectionControl = createManagementControl(clusterConnectionConfig1.getName());

      server_0.getManagementService().addNotificationListener(notifListener);
      assertEquals(0, notifListener.count(CoreNotificationType.CLUSTER_CONNECTION_STOPPED));
      clusterConnectionControl.stop();
      Wait.assertEquals(1, () -> notifListener.count(CoreNotificationType.CLUSTER_CONNECTION_STOPPED), 5000, 100);
      Notification notif = notifListener.findAny(CoreNotificationType.CLUSTER_CONNECTION_STOPPED);
      assertNotNull(notif);
      assertEquals(clusterConnectionControl.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());

      clusterConnectionControl.start();

      Wait.assertTrue(() -> notifListener.size() > 0, 5000, 100);
      notif = notifListener.findAny(CoreNotificationType.CLUSTER_CONNECTION_STARTED);
      assertNotNull(notif);
      assertEquals(clusterConnectionControl.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();

      Map<String, Object> acceptorParams = new HashMap<>();
      acceptorParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(), acceptorParams, RandomUtil.randomUUIDString());

      TransportConfiguration connectorConfig = new TransportConfiguration(InVMConnectorFactory.class.getName(), acceptorParams, RandomUtil.randomUUIDString());

      QueueConfiguration queueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setDurable(false);
      List<String> connectors = new ArrayList<>();
      connectors.add(connectorConfig.getName());

      String discoveryGroupName = RandomUtil.randomUUIDString();
      DiscoveryGroupConfiguration discoveryGroupConfig = new DiscoveryGroupConfiguration().setName(discoveryGroupName).setRefreshTimeout(500).setDiscoveryInitialWaitTimeout(0).setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(getUDPDiscoveryAddress()).setGroupPort(getUDPDiscoveryPort()));

      Configuration conf_1 = createBasicConfig().addAcceptorConfiguration(acceptorConfig).addQueueConfiguration(queueConfig);

      clusterConnectionConfig1 = new ClusterConnectionConfiguration().setName(RandomUtil.randomUUIDString()).setAddress(queueConfig.getAddress().toString()).setConnectorName(connectorConfig.getName()).setRetryInterval(RandomUtil.randomPositiveLong()).setDuplicateDetection(RandomUtil.randomBoolean()).setMessageLoadBalancingType(MessageLoadBalancingType.STRICT).setMaxHops(RandomUtil.randomPositiveInt()).setConfirmationWindowSize(RandomUtil.randomPositiveInt()).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(connectors).setCallTimeout(500).setCallFailoverTimeout(500).setProducerWindowSize(1234);

      clusterConnectionConfig2 = new ClusterConnectionConfiguration().setName(RandomUtil.randomUUIDString()).setAddress(queueConfig.getAddress().toString()).setConnectorName(connectorConfig.getName()).setRetryInterval(RandomUtil.randomPositiveLong()).setDuplicateDetection(RandomUtil.randomBoolean()).setMessageLoadBalancingType(MessageLoadBalancingType.OFF).setMaxHops(RandomUtil.randomPositiveInt()).setConfirmationWindowSize(RandomUtil.randomPositiveInt()).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setDiscoveryGroupName(discoveryGroupName).setCallTimeout(500).setCallFailoverTimeout(500).setProducerWindowSize(1234);

      Configuration conf_0 = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName())).addConnectorConfiguration(connectorConfig.getName(), connectorConfig).addClusterConfiguration(clusterConnectionConfig1).addClusterConfiguration(clusterConnectionConfig2).addDiscoveryGroupConfiguration(discoveryGroupName, discoveryGroupConfig);

      mbeanServer_1 = createMBeanServer();
      server_1 = addServer(ActiveMQServers.newActiveMQServer(conf_1, mbeanServer_1, false));
      server_1.start();

      server_0 = addServer(ActiveMQServers.newActiveMQServer(conf_0, mbeanServer, false));
      server_0.start();
   }

   protected ClusterConnectionControl createManagementControl(final String name) throws Exception {
      return ManagementControlHelper.createClusterConnectionControl(name, mbeanServer);
   }



}
