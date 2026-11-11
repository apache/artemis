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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.util.BackupSyncDelay;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

public class PrimaryVoteOnBackupFailureClusterTest extends ClusterWithBackupFailoverTestBase {

   @Override
   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnectionWithBackups("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, new int[]{1, 2});

      setupClusterConnectionWithBackups("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, new int[]{0, 2});

      setupClusterConnectionWithBackups("cluster2", "queues", messageLoadBalancingType, 1, isNetty(), 2, new int[]{0, 1});

      setupClusterConnectionWithBackups("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 3, new int[]{1, 2});

      setupClusterConnectionWithBackups("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 4, new int[]{0, 2});

      setupClusterConnectionWithBackups("cluster2", "queues", messageLoadBalancingType, 1, isNetty(), 5, new int[]{0, 1});
   }

   @Override
   protected void setupServers() throws Exception {
      // The backups
      setupBackupServer(3, 0, isFileStorage(), haType(), isNetty());
      setupBackupServer(4, 1, isFileStorage(), haType(), isNetty());
      setupBackupServer(5, 2, isFileStorage(), haType(), isNetty());

      // The primaries
      setupPrimaryServer(0, isFileStorage(), haType(), isNetty(), false);
      setupPrimaryServer(1, isFileStorage(), haType(), isNetty(), false);
      setupPrimaryServer(2, isFileStorage(), haType(), isNetty(), false);

      //we need to know who is connected to who
      ((ReplicatedPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration()).setGroupName("group0");
      ((ReplicatedPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration()).setGroupName("group1");
      ((ReplicatedPolicyConfiguration) servers[2].getConfiguration().getHAPolicyConfiguration()).setGroupName("group2");
      ((ReplicaPolicyConfiguration) servers[3].getConfiguration().getHAPolicyConfiguration()).setGroupName("group0");
      ((ReplicaPolicyConfiguration) servers[4].getConfiguration().getHAPolicyConfiguration()).setGroupName("group1");
      ((ReplicaPolicyConfiguration) servers[5].getConfiguration().getHAPolicyConfiguration()).setGroupName("group2");

      // Configure to vote to stay active, when backup dies
      ((ReplicatedPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration()).setVoteOnReplicationFailure(true);
      ((ReplicatedPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration()).setVoteOnReplicationFailure(true);
      ((ReplicatedPolicyConfiguration) servers[2].getConfiguration().getHAPolicyConfiguration()).setVoteOnReplicationFailure(true);
   }
   @Override
   protected HAType haType() {
      return HAType.SharedNothingReplication;
   }

   @Test
   public void testPrimaryVoteSucceedsAfterBackupFailure() throws Exception {
      startCluster();

      // Wait for servers to start
      for (int i = 0; i < servers.length; i++) {
         waitForServerToStart(servers[i]);
      }

      // Wait for backup to sync replication
      for (int i = 3; i < servers.length; i++) {
         Wait.waitFor(() -> servers[3].isReplicaSync());
      }

      // Register failure listener to detect when primary recognises the backup has died.
      final CountDownLatch latch = new CountDownLatch(1);
      servers[0].getReplicationManager().getBackupTransportConnection().addFailureListener(new FailureListener() {
         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {
            latch.countDown();
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
            latch.countDown();
         }
      });

      servers[3].stop();

      // Wait for primary to notice backup is down.
      latch.await(30, TimeUnit.SECONDS);

      // The quorum vote time out is hardcoded 5s.  Wait for double the time then check server is primary
      Thread.sleep(10000);
      assertTrue(servers[0].isStarted());
   }

   private void startCluster() throws Exception {
      int[] primaryServerIDs = new int[]{0, 1, 2};
      setupCluster();
      startServers(0, 1, 2);
      new BackupSyncDelay(servers[4], servers[1], PacketImpl.REPLICATION_SCHEDULED_FAILOVER);
      startServers(3, 4, 5);

      for (int i : primaryServerIDs) {
         waitForTopology(servers[i], 3, 3);
      }

      waitForFailoverTopology(3, 0, 1, 2);
      waitForFailoverTopology(4, 0, 1, 2);
      waitForFailoverTopology(5, 0, 1, 2);
   }
}
