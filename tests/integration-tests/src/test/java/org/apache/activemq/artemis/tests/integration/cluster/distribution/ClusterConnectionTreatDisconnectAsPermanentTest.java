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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusterConnectionTreatDisconnectAsPermanentTest extends ClusterTestBase {

   private static final String ADDRESS = "queues.testaddress";

   private static final String QUEUE = "queue0";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected boolean isNetty() {
      return true;
   }

   private void setupCluster(boolean treatDisconnectAsPermanent) {
      ClusterConfigCallback cb = config -> config.setTreatDisconnectAsPermanent(treatDisconnectAsPermanent);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), cb, 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), cb, 1, 0);
   }

   private ClusterConnectionImpl getClusterConnection(int node) {
      for (ClusterConnection cc : servers[node].getClusterManager().getClusterConnections()) {
         if (cc instanceof ClusterConnectionImpl clusterConnection) {
            return clusterConnection;
         }
      }
      throw new IllegalStateException("No ClusterConnectionImpl on node " + node);
   }

   private String getNodeId(int node) {
      return servers[node].getNodeID().toString();
   }

   private int countRemoteBindings(int node, String address) throws Exception {
      Bindings bindings = servers[node].getPostOffice().getBindingsForAddress(SimpleString.of(address));
      int count = 0;
      for (Binding binding : bindings.getBindings()) {
         if (binding instanceof RemoteQueueBinding) {
            count++;
         }
      }
      return count;
   }

   /**
    * Starts both nodes with a consumer on {@code queue0} on each and waits until the cluster is fully formed.
    *
    * @return the flow record node 0 holds for node 1
    */
   private MessageFlowRecord startClusterAndGetFlowRecordForNode1() throws Exception {
      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, ADDRESS, QUEUE, null, false);
      createQueue(1, ADDRESS, QUEUE, null, false);

      addConsumer(0, 0, QUEUE, null);
      addConsumer(1, 1, QUEUE, null);

      waitForBindings(0, ADDRESS, 1, 1, true);
      waitForBindings(1, ADDRESS, 1, 1, true);
      waitForBindings(0, ADDRESS, 1, 1, false);
      waitForBindings(1, ADDRESS, 1, 1, false);

      ClusterConnectionImpl cc0 = getClusterConnection(0);
      String node1Id = getNodeId(1);

      AtomicReference<MessageFlowRecord> found = new AtomicReference<>();
      assertTrue(Wait.waitFor(() -> {
         MessageFlowRecord r = cc0.getRecords().get(node1Id);
         if (r != null && r.getBridge() != null && r.getBridge().isConnected()) {
            found.set(r);
            return true;
         }
         return false;
      }, 30_000, 100), "node 0 must have a connected bridge for node 1");

      return found.get();
   }

   /**
    * Shuts node 1 down gracefully, which makes it send a {@code DISCONNECT} to node 0.
    */
   private void gracefullyStopNode(int node) throws Exception {
      removeConsumer(node);
      closeSessionFactory(node);
      stopServers(node);
   }

   /**
    * With the default of {@code treat-disconnect-as-permanent=true} the {@code DISCONNECT} means node 1 is gone for
    * good: node 0 drops the flow record and the remote bindings which came from node 1.
    */
   @Test
   @Timeout(120)
   public void testDisconnectIsPermanentByDefault() throws Exception {
      setupCluster(true);

      MessageFlowRecord record = startClusterAndGetFlowRecordForNode1();
      assertNotNull(record);

      ClusterConnectionImpl cc0 = getClusterConnection(0);
      String node1Id = getNodeId(1);

      gracefullyStopNode(1);

      assertTrue(Wait.waitFor(() -> !cc0.getRecords().containsKey(node1Id), 30_000, 100),
                 "node 0 must drop the flow record for node 1 when the DISCONNECT is treated as permanent");

      assertTrue(Wait.waitFor(() -> countRemoteBindings(0, ADDRESS) == 0, 30_000, 100),
                 "node 0 must drop the remote bindings of node 1 when the DISCONNECT is treated as permanent");
   }

   /**
    * With {@code treat-disconnect-as-permanent=false} the {@code DISCONNECT} is handled like any other connection
    * failure: node 0 keeps the flow record for node 1 and the very same bridge keeps retrying until node 1 comes back,
    * at which point message flow resumes over it.
    */
   @Test
   @Timeout(120)
   public void testDisconnectTreatedAsTransientFailure() throws Exception {
      setupCluster(false);

      MessageFlowRecord record = startClusterAndGetFlowRecordForNode1();
      assertNotNull(record);

      ClusterConnectionImpl cc0 = getClusterConnection(0);
      String node1Id = getNodeId(1);

      gracefullyStopNode(1);

      startServers(1);

      assertTrue(Wait.waitFor(() -> record.getBridge().isConnected(), 30_000, 100),
                 "the original bridge must reconnect once node 1 is back");

      assertSame(record, cc0.getRecords().get(node1Id),
                 "node 0 must reuse the flow record it kept across the DISCONNECT");

      // and the cluster is fully functional again over that bridge
      setupSessionFactory(1, isNetty());
      createQueue(1, ADDRESS, QUEUE, null, false);
      addConsumer(1, 1, QUEUE, null);

      waitForBindings(1, ADDRESS, 1, 1, true);
      waitForBindings(0, ADDRESS, 1, 1, false);

      send(0, ADDRESS, 10, false, null);

      verifyReceiveRoundRobinInSomeOrder(10, 0, 1);
   }
}
