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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiplePrimariesMultipleBackupsFailoverTest extends MultipleBackupsFailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected Map<Integer, TestableServer> servers = new HashMap<>();
   private ServerLocator locator2;
   private ServerLocator locator;
   private final boolean sharedStore = true;

   @Test
   public void testMultipleFailovers2PrimaryServers() throws Exception {
      NodeManager nodeManager1 = new InVMNodeManager(!sharedStore);
      NodeManager nodeManager2 = new InVMNodeManager(!sharedStore);
      createPrimaryConfig(nodeManager1, 0, 3, 4, 5);
      createBackupConfig(nodeManager1, 0, 1, true, new int[]{0, 2}, 3, 4, 5);
      createBackupConfig(nodeManager1, 0, 2, true, new int[]{0, 1}, 3, 4, 5);
      createPrimaryConfig(nodeManager2, 3, 0);
      createBackupConfig(nodeManager2, 3, 4, true, new int[]{3, 5}, 0, 1, 2);
      createBackupConfig(nodeManager2, 3, 5, true, new int[]{3, 4}, 0, 1, 2);

      servers.get(0).start();
      waitForServerToStart(servers.get(0).getServer());

      servers.get(3).start();
      waitForServerToStart(servers.get(3).getServer());

      servers.get(1).start();
      waitForServerToStart(servers.get(1).getServer());

      servers.get(2).start();

      servers.get(4).start();
      waitForServerToStart(servers.get(4).getServer());

      servers.get(5).start();

      waitForServerToStart(servers.get(4).getServer());

      locator = getServerLocator(0).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(15);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 4, servers.get(0).getServer());
      ClientSession session = sendAndConsume(sf, true);

      logger.debug(((ServerLocatorInternal) locator).getTopology().describe());
      Thread.sleep(500);
      servers.get(0).crash(session);

      int primaryAfter0 = waitForNewPrimary(10000, true, servers, 1, 2);

      locator2 = getServerLocator(3).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(15);

      ClientSessionFactoryInternal sf2 = createSessionFactoryAndWaitForTopology(locator2, 4);
      ClientSession session2 = sendAndConsume(sf2, true);

      System.setProperty("foo", "bar");
      servers.get(3).crash(session2);
      int liveAfter3 = waitForNewPrimary(10000, true, servers, 4, 5);
      locator.close();
      locator2.close();
      if (primaryAfter0 == 2) {
         Thread.sleep(500);
         servers.get(1).stop();
         Thread.sleep(500);
         servers.get(2).stop();
      } else {
         Thread.sleep(500);
         servers.get(2).stop();
         Thread.sleep(500);
         servers.get(1).stop();
      }

      if (liveAfter3 == 4) {
         Thread.sleep(500);
         servers.get(5).stop();
         Thread.sleep(500);
         servers.get(4).stop();
      } else {
         Thread.sleep(500);
         servers.get(4).stop();
         Thread.sleep(500);
         servers.get(5).stop();
      }
   }

   protected void createBackupConfig(NodeManager nodeManager,
                                     int primaryNode,
                                     int nodeid,
                                     boolean createClusterConnections,
                                     int[] otherBackupNodes,
                                     int... otherClusterNodes) throws Exception {
      Configuration config1 = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(isNetty(), true, generateParams(nodeid, isNetty()))).setHAPolicyConfiguration(sharedStore ? new SharedStoreBackupPolicyConfiguration() : new ReplicaPolicyConfiguration()).setBindingsDirectory(getBindingsDir() + "_" + primaryNode).setJournalDirectory(getJournalDir() + "_" + primaryNode).setPagingDirectory(getPageDir() + "_" + primaryNode).setLargeMessagesDirectory(getLargeMessagesDir() + "_" + primaryNode);

      for (int node : otherBackupNodes) {
         TransportConfiguration primaryConnector = createTransportConfiguration(isNetty(), false, generateParams(node, isNetty()));
         config1.addConnectorConfiguration(primaryConnector.getName(), primaryConnector);
      }

      TransportConfiguration backupConnector = createTransportConfiguration(isNetty(), false, generateParams(nodeid, isNetty()));
      config1.addConnectorConfiguration(backupConnector.getName(), backupConnector);

      String[] clusterNodes = new String[otherClusterNodes.length];
      for (int i = 0; i < otherClusterNodes.length; i++) {
         TransportConfiguration connector = createTransportConfiguration(isNetty(), false, generateParams(otherClusterNodes[i], isNetty()));
         config1.addConnectorConfiguration(connector.getName(), connector);
         clusterNodes[i] = connector.getName();
      }
      config1.addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), clusterNodes));

      servers.put(nodeid, new SameProcessActiveMQServer(createInVMFailoverServer(true, config1, nodeManager, primaryNode)));
   }

   protected void createPrimaryConfig(NodeManager nodeManager, int primaryNode, int... otherPrimaryNodes) throws Exception {
      TransportConfiguration primaryConnector = createTransportConfiguration(isNetty(), false, generateParams(primaryNode, isNetty()));

      Configuration config0 = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(isNetty(), true, generateParams(primaryNode, isNetty()))).setHAPolicyConfiguration(sharedStore ? new SharedStorePrimaryPolicyConfiguration() : new ReplicatedPolicyConfiguration()).setBindingsDirectory(getBindingsDir() + "_" + primaryNode).setJournalDirectory(getJournalDir() + "_" + primaryNode).setPagingDirectory(getPageDir() + "_" + primaryNode).setLargeMessagesDirectory(getLargeMessagesDir() + "_" + primaryNode).addConnectorConfiguration(primaryConnector.getName(), primaryConnector);

      String[] pairs = new String[otherPrimaryNodes.length];
      for (int i = 0; i < otherPrimaryNodes.length; i++) {
         TransportConfiguration otherPrimaryConnector = createTransportConfiguration(isNetty(), false, generateParams(otherPrimaryNodes[i], isNetty()));
         config0.addConnectorConfiguration(otherPrimaryConnector.getName(), otherPrimaryConnector);
         pairs[i] = otherPrimaryConnector.getName();
      }
      config0.addClusterConfiguration(basicClusterConnectionConfig(primaryConnector.getName(), pairs));

      servers.put(primaryNode, new SameProcessActiveMQServer(createInVMFailoverServer(true, config0, nodeManager, primaryNode)));
   }

   @Override
   protected boolean isNetty() {
      return false;
   }
}
