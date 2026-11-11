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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.impl.SharedNothingPrimaryActivation;
import org.apache.activemq.artemis.tests.integration.cluster.util.BackupSyncDelay;
import org.junit.jupiter.api.Test;

public class QuorumFailOverTest extends StaticClusterWithBackupFailoverTest {

   @Override
   protected void setupServers() throws Exception {
      super.setupServers();
      //we need to know who is connected to who
      ((ReplicatedPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration()).setGroupName("group0");
      ((ReplicatedPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration()).setGroupName("group1");
      ((ReplicatedPolicyConfiguration) servers[2].getConfiguration().getHAPolicyConfiguration()).setGroupName("group2");
      ((ReplicaPolicyConfiguration) servers[3].getConfiguration().getHAPolicyConfiguration()).setGroupName("group0");
      ((ReplicaPolicyConfiguration) servers[4].getConfiguration().getHAPolicyConfiguration()).setGroupName("group1");
      ((ReplicaPolicyConfiguration) servers[5].getConfiguration().getHAPolicyConfiguration()).setGroupName("group2");

      //reduce the numbers so that the vote finishes faster
      ((ReplicaPolicyConfiguration) servers[3].getConfiguration().getHAPolicyConfiguration()).setVoteRetries(5);
      ((ReplicaPolicyConfiguration) servers[3].getConfiguration().getHAPolicyConfiguration()).setVoteRetryWait(500);

   }

   @Test
   public void testQuorumVoting() throws Exception {
      int[] liveServerIDs = new int[]{0, 1, 2};
      setupCluster();
      startServers(0, 1, 2);
      new BackupSyncDelay(servers[4], servers[1], PacketImpl.REPLICATION_SCHEDULED_FAILOVER);
      startServers(3, 4, 5);

      for (int i : liveServerIDs) {
         waitForBridges(servers[i], 2);
         waitForTopology(servers[i], 3, 3);
      }

      waitForFailoverTopology(3, 0, 1, 2);
      waitForFailoverTopology(4, 0, 1, 2);
      waitForFailoverTopology(5, 0, 1, 2);

      for (int i : liveServerIDs) {
         setupSessionFactory(i, i + 3, isNetty(), false);
         createQueue(i, QUEUES_TESTADDRESS, QUEUE_NAME, null, true);
         addConsumer(i, i, QUEUE_NAME, null);
      }

      waitForBindings(0, QUEUES_TESTADDRESS, 1, 1, true);
      waitForBindings(1, QUEUES_TESTADDRESS, 1, 1, true);
      waitForBindings(2, QUEUES_TESTADDRESS, 1, 1, true);

      send(0, QUEUES_TESTADDRESS, 10, false, null);
      verifyReceiveRoundRobinInSomeOrder(true, 10, 0, 1, 2);

      final TopologyListener primaryTopologyListener = new TopologyListener("PRIMARY-1");

      locators[0].addClusterTopologyListener(primaryTopologyListener);

      assertTrue(servers[3].getHAPolicy().isBackup(), "we assume 3 is a backup");
      assertFalse(servers[3].getHAPolicy().isSharedStore(), "no shared storage");

      failNode(0);

      waitForFailoverTopology(4, 3, 1, 2);
      waitForFailoverTopology(5, 3, 1, 2);

      waitForBindings(3, QUEUES_TESTADDRESS, 1, 1, true);

      assertTrue(servers[3].waitForActivation(2, TimeUnit.SECONDS));
      assertFalse(servers[3].getHAPolicy().isBackup(), "3 should have failed over ");

      failNode(1);
      assertFalse(servers[4].getHAPolicy().isBackup(), "4 should have failed over ");
   }

   @Test
   public void testQuorumVotingPrimaryNotDead() throws Exception {
      int[] liveServerIDs = new int[]{0, 1, 2};
      setupCluster();
      startServers(0, 1, 2);
      new BackupSyncDelay(servers[4], servers[1], PacketImpl.REPLICATION_SCHEDULED_FAILOVER);
      startServers(3, 4, 5);

      ActiveMQComponent[] externalComponents = new ActiveMQComponent[6];
      for (int i = 0; i < 6; i++) {
         externalComponents[i] = new FakeServiceComponent("server " + i);
         servers[i].addExternalComponent(externalComponents[i], true);
      }

      for (int i : liveServerIDs) {
         waitForBridges(servers[i], 2);
         waitForTopology(servers[i], 3, 3);
      }

      waitForFailoverTopology(3, 0, 1, 2);
      waitForFailoverTopology(4, 0, 1, 2);
      waitForFailoverTopology(5, 0, 1, 2);

      for (int i : liveServerIDs) {
         setupSessionFactory(i, i + 3, isNetty(), false);
         createQueue(i, QUEUES_TESTADDRESS, QUEUE_NAME, null, true);
         addConsumer(i, i, QUEUE_NAME, null);
      }

      waitForBindings(0, QUEUES_TESTADDRESS, 1, 1, true);
      waitForBindings(1, QUEUES_TESTADDRESS, 1, 1, true);
      waitForBindings(2, QUEUES_TESTADDRESS, 1, 1, true);

      send(0, QUEUES_TESTADDRESS, 10, false, null);
      verifyReceiveRoundRobinInSomeOrder(true, 10, 0, 1, 2);

      final TopologyListener primaryTopologyListener = new TopologyListener("PRIMARY-1");

      locators[0].addClusterTopologyListener(primaryTopologyListener);

      assertTrue(servers[3].getHAPolicy().isBackup(), "we assume 3 is a backup");
      assertFalse(servers[3].getHAPolicy().isSharedStore(), "no shared storage");

      SharedNothingPrimaryActivation primaryActivation = (SharedNothingPrimaryActivation) servers[0].getActivation();
      primaryActivation.freezeReplication();
      assertFalse(servers[0].isReplicaSync());
      waitForRemoteBackupSynchronization(servers[0]);
      assertTrue(servers[0].isReplicaSync());

      for (ActiveMQComponent component : externalComponents) {
         assertTrue(component.isStarted(), "component " + component + " is stopped, the web server would been stopped here");
      }
   }

   @Override
   protected boolean isSharedStorage() {
      return false;
   }

   private static class TopologyListener implements ClusterTopologyListener {

      final String prefix;
      final Map<String, Pair<TransportConfiguration, TransportConfiguration>> nodes = new ConcurrentHashMap<>();

      private TopologyListener(String string) {
         prefix = string;
      }

      @Override
      public void nodeUP(TopologyMember topologyMember, boolean last) {
         Pair<TransportConfiguration, TransportConfiguration> connectorPair = new Pair<>(topologyMember.getPrimary(), topologyMember.getBackup());
         nodes.put(topologyMember.getBackupGroupName(), connectorPair);
      }

      @Override
      public void nodeDown(long eventUID, String nodeID) {
         nodes.remove(nodeID);
      }

      @Override
      public String toString() {
         return "TopologyListener(" + prefix + ", #=" + nodes.size() + ")";
      }
   }
}
