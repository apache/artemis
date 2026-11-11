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
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.config.Configuration;
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

public class SinglePrimaryMultipleBackupsFailoverTest extends MultipleBackupsFailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected Map<Integer, TestableServer> servers = new HashMap<>();
   protected ServerLocatorImpl locator;
   private NodeManager nodeManager;
   final boolean sharedStore = true;

   @Test
   public void testMultipleFailovers() throws Exception {
      nodeManager = new InVMNodeManager(!sharedStore);
      createPrimaryConfig(0);
      createBackupConfig(0, 1, 0, 2, 3, 4, 5);
      createBackupConfig(0, 2, 0, 1, 3, 4, 5);
      createBackupConfig(0, 3, 0, 1, 2, 4, 5);
      createBackupConfig(0, 4, 0, 1, 2, 3, 5);
      createBackupConfig(0, 5, 0, 1, 2, 3, 4);

      servers.get(0).start();
      waitForServerToStart(servers.get(0).getServer());
      servers.get(1).start();
      waitForServerToStart(servers.get(1).getServer());
      servers.get(2).start();
      servers.get(3).start();
      servers.get(4).start();
      servers.get(5).start();

      locator = (ServerLocatorImpl) getServerLocator(0);

      Topology topology = locator.getTopology();

      // for logging and debugging
      topology.setOwner("testMultipleFailovers");

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(15);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      int backupNode;
      ClientSession session = sendAndConsume(sf, true);

      logger.debug("failing node 0");
      servers.get(0).crash(session);

      session.close();
      backupNode = waitForNewPrimary(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      logger.debug("failing node {}", backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewPrimary(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      logger.debug("failing node {}", backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewPrimary(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      logger.debug("failing node {}", backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewPrimary(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      logger.debug("failing node {}", backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewPrimary(5, false, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      session.close();
      servers.get(backupNode).stop();

      locator.close();
   }

   protected void createBackupConfig(int primaryNode, int nodeid, int... nodes) throws Exception {
      TransportConfiguration backupConnector = createTransportConfiguration(isNetty(), false, generateParams(nodeid, isNetty()));

      Configuration config1 = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(isNetty(), true, generateParams(nodeid, isNetty()))).setHAPolicyConfiguration(sharedStore ? new SharedStoreBackupPolicyConfiguration() : new ReplicatedPolicyConfiguration()).addConnectorConfiguration(backupConnector.getName(), backupConnector).setBindingsDirectory(getBindingsDir() + "_" + primaryNode).setJournalDirectory(getJournalDir() + "_" + primaryNode).setPagingDirectory(getPageDir() + "_" + primaryNode).setLargeMessagesDirectory(getLargeMessagesDir() + "_" + primaryNode);

      String[] staticConnectors = new String[nodes.length];
      for (int i = 0; i < nodes.length; i++) {
         TransportConfiguration primaryConnector = createTransportConfiguration(isNetty(), false, generateParams(nodes[i], isNetty()));
         config1.addConnectorConfiguration(primaryConnector.getName(), primaryConnector);
         staticConnectors[i] = primaryConnector.getName();
      }
      config1.addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), staticConnectors));

      servers.put(nodeid, new SameProcessActiveMQServer(createInVMFailoverServer(true, config1, nodeManager, nodeid)));
   }

   protected void createPrimaryConfig(int primaryNode) throws Exception {
      TransportConfiguration primaryConnector = createTransportConfiguration(isNetty(), false, generateParams(primaryNode, isNetty()));

      Configuration config0 = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(createTransportConfiguration(isNetty(), true, generateParams(primaryNode, isNetty()))).setHAPolicyConfiguration(sharedStore ? new SharedStorePrimaryPolicyConfiguration() : new ReplicatedPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(primaryConnector.getName())).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).setBindingsDirectory(getBindingsDir() + "_" + primaryNode).setJournalDirectory(getJournalDir() + "_" + primaryNode).setPagingDirectory(getPageDir() + "_" + primaryNode).setLargeMessagesDirectory(getLargeMessagesDir() + "_" + primaryNode);

      SameProcessActiveMQServer server = new SameProcessActiveMQServer(createInVMFailoverServer(true, config0, nodeManager, primaryNode));
      addActiveMQComponent(server);
      servers.put(primaryNode, server);
   }

   @Override
   protected boolean isNetty() {
      return false;
   }
}
