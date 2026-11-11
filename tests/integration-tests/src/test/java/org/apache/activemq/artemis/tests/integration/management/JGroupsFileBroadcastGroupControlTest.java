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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.JGroupsFileBroadcastGroupControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.activemq.artemis.json.JsonArray;
import java.util.ArrayList;
import java.util.List;

public class JGroupsFileBroadcastGroupControlTest extends ManagementTestBase {

   private ActiveMQServer server;
   BroadcastGroupConfiguration broadcastGroupConfig;
   JGroupsFileBroadcastGroupControl broadcastGroupControl;

   @Test
   public void testAttributes() throws Exception {
      JGroupsFileBroadcastEndpointFactory udpCfg = (JGroupsFileBroadcastEndpointFactory) broadcastGroupConfig.getEndpointFactory();
      assertEquals(broadcastGroupConfig.getName(), broadcastGroupControl.getName());
      assertEquals(udpCfg.getChannelName(), broadcastGroupControl.getChannelName());
      assertEquals(udpCfg.getFile(), broadcastGroupControl.getFile());
      assertEquals(broadcastGroupConfig.getBroadcastPeriod(), broadcastGroupControl.getBroadcastPeriod());

      Object[] connectorPairs = broadcastGroupControl.getConnectorPairs();
      assertEquals(1, connectorPairs.length);

      String connectorPairData = (String) connectorPairs[0];
      assertEquals(broadcastGroupConfig.getConnectorInfos().get(0), connectorPairData);
      String jsonString = broadcastGroupControl.getConnectorPairsAsJSON();
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1, array.size());
      assertEquals(broadcastGroupConfig.getConnectorInfos().get(0), array.getString(0));

      assertTrue(broadcastGroupControl.isStarted());
   }

   protected JGroupsFileBroadcastGroupControl createManagementControl(final String name) throws Exception {
      return ManagementControlHelper.createJgroupsFileBroadcastGroupControl(name, mbeanServer);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();

      TransportConfiguration connectorConfiguration = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      List<String> connectorInfos = new ArrayList<>();
      connectorInfos.add(connectorConfiguration.getName());
      broadcastGroupConfig = new BroadcastGroupConfiguration().setName(RandomUtil.randomUUIDString()).setBroadcastPeriod(RandomUtil.randomPositiveInt()).setConnectorInfos(connectorInfos).setEndpointFactory(new JGroupsFileBroadcastEndpointFactory().setChannelName("myChannel").setFile("test-jgroups-file_ping.xml"));

      Configuration config = createDefaultInVMConfig().setJMXManagementEnabled(true).addConnectorConfiguration(connectorConfiguration.getName(), connectorConfiguration).addBroadcastGroupConfiguration(broadcastGroupConfig);
      server = addServer(ActiveMQServers.newActiveMQServer(config, mbeanServer, false));
      server.start();

      broadcastGroupControl = createManagementControl(broadcastGroupConfig.getName());
   }
}
