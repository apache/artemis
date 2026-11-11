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
package org.apache.activemq.artemis.tests.integration.cluster.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;

public class MultiServerTestBase extends ActiveMQTestBase {

   protected ActiveMQServer[] servers;

   protected ActiveMQServer[] backupServers;

   protected NodeManager[] nodeManagers;

   protected int getNumberOfServers() {
      return 5;
   }

   protected boolean useBackups() {
      return true;
   }

   protected boolean useRealFiles() {
      return true;
   }

   protected boolean useNetty() {
      return false;
   }

   protected boolean useSharedStorage() {
      return true;
   }

   protected final ServerLocator createLocator(final boolean ha, final int serverID) {
      TransportConfiguration targetConfig = createTransportConfiguration(useNetty(), false, generateParams(serverID, useNetty()));
      if (ha) {
         ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(targetConfig);
         return addServerLocator(locator);
      } else {
         ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(targetConfig);
         return addServerLocator(locator);
      }
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      servers = new ActiveMQServer[getNumberOfServers()];

      if (useBackups()) {
         backupServers = new ActiveMQServer[getNumberOfServers()];
      }

      if (useBackups()) {
         nodeManagers = new NodeManager[getNumberOfServers()];
      }

      for (int i = 0; i < getNumberOfServers(); i++) {
         Pair<ActiveMQServer, NodeManager> nodeServer = setupPrimaryServer(i, useRealFiles(), useSharedStorage());
         this.servers[i] = nodeServer.getA();

         if (useBackups()) {
            this.nodeManagers[i] = nodeServer.getB();

            // The backup id here will be only useful if doing replication
            // this base class doesn't currently support replication, but I will set things here for later anyways
            this.backupServers[i] = setupBackupServer(i + getNumberOfServers(), i, nodeServer.getB());
         }
      }
   }

   protected void startServers() throws Exception {
      for (ActiveMQServer server : servers) {
         server.start();
      }

      for (ActiveMQServer server : servers) {
         waitForServerToStart(server);
      }

      if (backupServers != null) {
         for (ActiveMQServer server : backupServers) {
            server.start();
         }

         for (ActiveMQServer server : backupServers) {
            waitForServerToStart(server);
         }

      }

      for (ActiveMQServer server : servers) {
         waitForTopology(server, getNumberOfServers(), useBackups() ? getNumberOfServers() : 0);
      }
   }

   public void startServers(int... serverID) throws Exception {
      for (int s : serverID) {
         servers[s].start();
         waitForServerToStart(servers[s]);
      }
   }

   public void startBackups(int... serverID) throws Exception {
      for (int s : serverID) {
         backupServers[s].start();
         waitForServerToStart(backupServers[s]);
      }

   }

   protected Pair<ActiveMQServer, NodeManager> setupPrimaryServer(final int node,
                                                                  final boolean realFiles,
                                                                  final boolean sharedStorage) throws Exception {
      NodeManager nodeManager = null;
      TransportConfiguration serverConfigAcceptor = createTransportConfiguration(useNetty(), true, generateParams(node, useNetty()));
      TransportConfiguration thisConnector = createTransportConfiguration(useNetty(), false, generateParams(node, useNetty()));

      if (sharedStorage) {
         nodeManager = new InVMNodeManager(false);
      }

      Configuration configuration = createBasicConfig(node).setJournalMaxIO_AIO(1000).setThreadPoolMaxSize(10).clearAcceptorConfigurations().addAcceptorConfiguration(serverConfigAcceptor).addConnectorConfiguration("thisConnector", thisConnector).setHAPolicyConfiguration(sharedStorage ? new SharedStorePrimaryPolicyConfiguration() : new ReplicatedPolicyConfiguration());

      List<String> targetServersOnConnection = new ArrayList<>();

      for (int targetNode = 0; targetNode < getNumberOfServers(); targetNode++) {
         if (targetNode == node) {
            continue;
         }
         String targetConnectorName = "target-" + targetNode;
         TransportConfiguration targetServer = createTransportConfiguration(useNetty(), false, generateParams(targetNode, useNetty()));
         configuration.getConnectorConfigurations().put(targetConnectorName, targetServer);
         targetServersOnConnection.add(targetConnectorName);

         // The connector towards a backup.. just to have a reference so bridges can connect to backups on their configs
         String backupConnectorName = "backup-" + targetNode;

         TransportConfiguration backupConnector = createTransportConfiguration(useNetty(), false, generateParams(targetNode + getNumberOfServers(), useNetty()));

         configuration.getConnectorConfigurations().put(backupConnectorName, backupConnector);
      }

      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration().setName("localCluster" + node).setAddress("cluster-queues").setConnectorName("thisConnector").setRetryInterval(100).setConfirmationWindowSize(1024).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(targetServersOnConnection);

      configuration.getClusterConfigurations().add(clusterConf);

      ActiveMQServer server;

      if (sharedStorage) {
         server = createInVMFailoverServer(realFiles, configuration, nodeManager, node);
      } else {
         server = createServer(realFiles, configuration);
      }

      server.setIdentity(this.getClass().getSimpleName() + "/Primary(" + node + ")");

      addServer(server);

      return new Pair<>(server, nodeManager);
   }

   protected ActiveMQServer setupBackupServer(final int node,
                                              final int primaryNode,
                                              final NodeManager nodeManager) throws Exception {
      TransportConfiguration serverConfigAcceptor = createTransportConfiguration(useNetty(), true, generateParams(node, useNetty()));
      TransportConfiguration thisConnector = createTransportConfiguration(useNetty(), false, generateParams(node, useNetty()));

      Configuration configuration = createBasicConfig(useSharedStorage() ? primaryNode : node).clearAcceptorConfigurations().addAcceptorConfiguration(serverConfigAcceptor).addConnectorConfiguration("thisConnector", thisConnector).setHAPolicyConfiguration(useSharedStorage() ? new SharedStoreBackupPolicyConfiguration() : new ReplicaPolicyConfiguration());

      List<String> targetServersOnConnection = new ArrayList<>();

      for (int targetNode = 0; targetNode < getNumberOfServers(); targetNode++) {
         //         if (targetNode == node)
         //         {
         //            // moving on from itself
         //            continue;
         //         }
         String targetConnectorName = "targetConnector-" + targetNode;
         TransportConfiguration targetServer = createTransportConfiguration(useNetty(), false, generateParams(targetNode, useNetty()));
         configuration.addConnectorConfiguration(targetConnectorName, targetServer);
         targetServersOnConnection.add(targetConnectorName);
      }

      ClusterConnectionConfiguration clusterConf = new ClusterConnectionConfiguration().setName("localCluster" + node).setAddress("cluster-queues").setConnectorName("thisConnector").setRetryInterval(100).setConfirmationWindowSize(1024).setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND).setStaticConnectors(targetServersOnConnection);

      configuration.getClusterConfigurations().add(clusterConf);

      ActiveMQServer server;

      if (useSharedStorage()) {
         server = createInVMFailoverServer(true, configuration, nodeManager, primaryNode);
      } else {
         server = addServer(ActiveMQServers.newActiveMQServer(configuration, useRealFiles()));
      }
      server.setIdentity(this.getClass().getSimpleName() + "/Backup(" + node + " of primary " + primaryNode + ")");
      return server;
   }

}
