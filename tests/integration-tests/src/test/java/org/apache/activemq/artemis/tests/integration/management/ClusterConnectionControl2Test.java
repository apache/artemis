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

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClusterConnectionControl2Test extends ManagementTestBase {


   private ActiveMQServer server0;

   private ActiveMQServer server1;

   private MBeanServer mbeanServer_1;

   private final int port_1 = TransportConstants.DEFAULT_PORT + 1000;

   private ClusterConnectionConfiguration clusterConnectionConfig_0;

   private final String clusterName = "cluster";




   @Test
   public void testNodes() throws Exception {
      ClusterConnectionControl clusterConnectionControl_0 = createManagementControl(clusterConnectionConfig_0.getName());
      Assert.assertTrue(clusterConnectionControl_0.isStarted());
      Map<String, String> nodes = clusterConnectionControl_0.getNodes();
      Assert.assertEquals(0, nodes.size());

      server1.start();
      waitForServerToStart(server1);
      long start = System.currentTimeMillis();

      while (true) {
         nodes = clusterConnectionControl_0.getNodes();

         if (nodes.size() == 1 || System.currentTimeMillis() - start > 30000) {
            break;
         }
         Thread.sleep(50);
      }

      Assert.assertEquals(1, nodes.size());

      String remoteAddress = nodes.values().iterator().next();
      Assert.assertTrue(remoteAddress.endsWith(":" + port_1));
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      String discoveryName = RandomUtil.randomString();
      String groupAddress = getUDPDiscoveryAddress();
      int groupPort = getUDPDiscoveryPort();

      Map<String, Object> acceptorParams_1 = new HashMap<>();
      acceptorParams_1.put(TransportConstants.PORT_PROP_NAME, port_1);
      TransportConfiguration acceptorConfig_0 = new TransportConfiguration(ActiveMQTestBase.NETTY_ACCEPTOR_FACTORY);

      TransportConfiguration acceptorConfig_1 = new TransportConfiguration(ActiveMQTestBase.NETTY_ACCEPTOR_FACTORY, acceptorParams_1);

      TransportConfiguration connectorConfig_1 = new TransportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY, acceptorParams_1);
      TransportConfiguration connectorConfig_0 = new TransportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY);

      QueueConfiguration queueConfig = new QueueConfiguration(RandomUtil.randomString()).setDurable(false);
      List<String> connectorInfos = new ArrayList<>();
      connectorInfos.add("netty");

      BroadcastGroupConfiguration broadcastGroupConfig = new BroadcastGroupConfiguration().setName(discoveryName).setBroadcastPeriod(250).setConnectorInfos(connectorInfos).setEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort));

      DiscoveryGroupConfiguration discoveryGroupConfig = new DiscoveryGroupConfiguration().setName(discoveryName).setRefreshTimeout(0).setDiscoveryInitialWaitTimeout(0).setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort));

      clusterConnectionConfig_0 = new ClusterConnectionConfiguration().setName(clusterName).setAddress(queueConfig.getAddress().toString()).setConnectorName("netty").setRetryInterval(1000).setDuplicateDetection(false).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setMaxHops(1).setConfirmationWindowSize(1024).setDiscoveryGroupName(discoveryName);

      Configuration conf_1 = createBasicConfig().addClusterConfiguration(clusterConnectionConfig_0).addAcceptorConfiguration(acceptorConfig_1).addConnectorConfiguration("netty", connectorConfig_1).addQueueConfiguration(queueConfig).addDiscoveryGroupConfiguration(discoveryName, discoveryGroupConfig).addBroadcastGroupConfiguration(broadcastGroupConfig);

      Configuration conf_0 = createBasicConfig(1).addClusterConfiguration(clusterConnectionConfig_0).addAcceptorConfiguration(acceptorConfig_0).addConnectorConfiguration("netty", connectorConfig_0).addDiscoveryGroupConfiguration(discoveryName, discoveryGroupConfig).addBroadcastGroupConfiguration(broadcastGroupConfig);

      mbeanServer_1 = MBeanServerFactory.createMBeanServer();
      server1 = addServer(ActiveMQServers.newActiveMQServer(conf_1, mbeanServer_1, false));

      server0 = addServer(ActiveMQServers.newActiveMQServer(conf_0, mbeanServer, false));
      server0.start();
      waitForServerToStart(server0);
   }

   @Override
   @After
   public void tearDown() throws Exception {
      MBeanServerFactory.releaseMBeanServer(mbeanServer_1);
      super.tearDown();
   }

   protected ClusterConnectionControl createManagementControl(final String name) throws Exception {
      return ManagementControlHelper.createClusterConnectionControl(name, mbeanServer);
   }
}
