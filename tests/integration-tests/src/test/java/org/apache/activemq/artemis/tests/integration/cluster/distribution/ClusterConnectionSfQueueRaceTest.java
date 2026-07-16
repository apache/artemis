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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionAccessor;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reproducer for store-and-forward queue permanently lost after
 * a graceful node restart races with removeRecord/nodeUP in ClusterConnectionImpl.
 *
 * The race: when a node is gracefully stopped and immediately restarted, the
 * restarting node's nodeUP topology notification can arrive between
 * super.fail() (which calls notifyNodeDown) and removeRecord() in
 * ClusterConnectionBridge.fail(). nodeUP sees the stale record and ignores it,
 * then removeRecord removes the stale record, leaving no bridge and no SF queue.
 *
 * This test validates the fix: after removing a record, removeRecord must
 * re-check the topology and re-create the bridge if the node is already back.
 * The test holds the records guard to prevent concurrent nodeUP from masking
 * the bug by independently re-creating the record.
 */
public class ClusterConnectionSfQueueRaceTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
      setupServer(2, isFileStorage(), isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1);
   }

   protected boolean isNetty() {
      return true;
   }

   private ClusterConnectionImpl getClusterConnection(int node) {
      for (ClusterConnection cc : servers[node].getClusterManager().getClusterConnections()) {
         if (cc instanceof ClusterConnectionImpl) {
            return (ClusterConnectionImpl) cc;
         }
      }
      throw new IllegalStateException("No ClusterConnectionImpl on node " + node);
   }

   private String getNodeId(int node) {
      return servers[node].getNodeID().toString();
   }

   /**
    * Deterministically reproduces the exact post-condition of the race:
    * removeRecord is called while the target node IS present in the topology.
    *
    * This is the state that arises when:
    * <ol>
    *   <li>A node is stopped and immediately restarted.</li>
    *   <li>nodeUP for the restarted node arrives while the stale record
    *       is still in the map (so nodeUP silently ignores it).</li>
    *   <li>removeRecord then removes the stale record.</li>
    * </ol>
    *
    * Without the fix, removeRecord simply removes the record and returns,
    * leaving no bridge and no SF queue for the node permanently. This means
    * any messages routed to that node's store-and-forward queue will never
    * be forwarded — permanent message loss.
    *
    * With the fix, removeRecord re-checks the topology after removal and
    * re-creates the bridge if the node is still present.
    *
    * The test holds the records guard (via {@code getRecordsGuard()}) around
    * the removeRecord call and assertion to prevent concurrent nodeUP (which
    * also synchronizes on the same guard) from independently re-creating
    * the record, which would mask the bug.
    */
   @Test
   @Timeout(120)
   public void testRemoveRecordReCreatesWhenNodeIsInTopology() throws Exception {
      startServers(0, 1, 2);

      ClusterConnectionImpl cc0 = getClusterConnection(0);
      String node2Id = getNodeId(2);

      assertTrue(Wait.waitFor(() -> {
         MessageFlowRecord r = cc0.getRecords().get(node2Id);
         return r != null && r.getBridge() != null && r.getBridge().isConnected();
      }, 30_000, 100), "node0 must have a connected bridge for node2");

      assertTrue(cc0.getTopology().getMember(node2Id) != null,
                 "node2 must be in topology");

      synchronized (ClusterConnectionAccessor.getRecordsGuard(cc0)) {
         logger.debug("Calling removeRecord(node2Id) under recordsGuard to simulate race post-state");
         cc0.removeRecord(node2Id);

         assertTrue(cc0.getRecords().containsKey(node2Id),
                    "removeRecord must re-create bridge when node is still in topology");
      }
   }
}
