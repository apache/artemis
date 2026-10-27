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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the lazy store-and-forward (SnF) reaper on {@code ClusterConnectionImpl}. Since
 * <a href="https://issues.apache.org/jira/browse/ARTEMIS-6319">ARTEMIS-6319</a> the SnF queue toward a departed node is
 * no longer deleted eagerly on teardown (which raced the deferred {@code addTail} of an in-flight route and lost
 * messages); it is retained and reclaimed later by a periodic reaper only once it is provably idle: owner-less (no flow
 * record and not a live topology member) and empty, stable across a grace interval.
 * <p>
 * The tests drive that owner-less condition directly, by creating an SnF-named internal queue toward a node that is
 * neither in the cluster connection records nor in the topology, which is exactly the state a scale-down/departure
 * teardown leaves behind. This keeps the reaper behaviour deterministic and independent of cluster failure timing. The
 * end-to-end produce/relocate path is covered by {@code ScaleDownSoakTest} and {@code ScaleDownSnFRelocateTest}.
 */
public class SnfScaleDownReaperTest extends ClusterTestBase {

   private static final String CLUSTER_NAME = "cluster0";
   private static final String ADDRESS = "testAddress";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), isNetty(), true);
      setupPrimaryServer(1, isFileStorage(), isNetty(), true);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
   }

   protected boolean isNetty() {
      return true;
   }

   private void setReaper(int serverID, long period, int graceTicks) {
      setReaper(serverID, period, graceTicks, ActiveMQDefaultConfiguration.getDefaultClusterScaleDownReaperMaxAttempts());
   }

   private void setReaper(int serverID, long period, int graceTicks, int maxAttempts) {
      for (ClusterConnectionConfiguration cc : servers[serverID].getConfiguration().getClusterConfigurations()) {
         cc.setScaleDownReaperPeriod(period).setScaleDownReaperGraceTicks(graceTicks).setScaleDownReaperMaxAttempts(maxAttempts);
      }
   }

   private String snfQueueName(ActiveMQServer server, String targetNodeID) {
      return server.getInternalNamingPrefix() + "sf." + CLUSTER_NAME + "." + targetNodeID;
   }

   // Creates an owner-less SnF queue on the given server toward a node that has no flow record and is not in the
   // topology, i.e. the state a scale-down/departure teardown leaves behind (teardown no longer deletes it).
   private String createOrphanSnFQueue(ActiveMQServer server) throws Exception {
      String departedNodeID = UUID.randomUUID().toString();
      String queueName = snfQueueName(server, departedNodeID);
      server.createQueue(QueueConfiguration.of(queueName)
                            .setRoutingType(RoutingType.MULTICAST)
                            .setAutoCreateAddress(true)
                            .setMaxConsumers(-1)
                            .setPurgeOnNoConsumers(false)
                            .setInternal(true))
            .setInternalQueue(true);
      return departedNodeID;
   }

   @Test
   public void testReapEmptyIdleQueue() throws Exception {
      setReaper(0, 200, 2);
      startServers(0, 1);

      String departedNodeID = createOrphanSnFQueue(servers[0]);
      servers[0].getClusterManager().getClusterConnection(CLUSTER_NAME).scaleDownRecord(
         departedNodeID, servers[1].getNodeID().toString());

      assertTrue(hasSnFQueue(servers[0], departedNodeID), "orphan SnF queue should have been created");

      // The reaper reclaims the empty, owner-less SnF queue within a few grace ticks.
      Wait.assertFalse("empty orphaned SnF queue should be reaped",
                       () -> hasSnFQueue(servers[0], departedNodeID), 10000);
   }

   @Test
   public void testRetainWhenReaperDisabled() throws Exception {
      setReaper(0, -1, 2);
      startServers(0, 1);

      String departedNodeID = createOrphanSnFQueue(servers[0]);
      assertTrue(hasSnFQueue(servers[0], departedNodeID), "orphan SnF queue should have been created");

      // With the reaper disabled the owner-less SnF queue must be retained, never reclaimed.
      assertFalse(Wait.waitFor(() -> !hasSnFQueue(servers[0], departedNodeID), 3000, 100),
                  "SnF queue must be retained while the reaper is disabled");
   }

   @Test
   public void testMaxAttemptsAbandonsReapingAndRetainsQueue() throws Exception {
      // Configure 3 max attempts with 100ms period
      setReaper(0, 100, 2, 3);
      startServers(0, 1);
      setupSessionFactory(0, isNetty());

      String departedNodeID = createOrphanSnFQueue(servers[0]);
      send(0, snfQueueName(servers[0], departedNodeID), 1, false, null);

      servers[0].getClusterManager().getClusterConnection(CLUSTER_NAME).scaleDownRecord(
         departedNodeID, servers[1].getNodeID().toString());

      assertTrue(hasSnFQueue(servers[0], departedNodeID), "orphan SnF queue should have been created");

      // Wait beyond the 3 attempts interval (3 * 100ms + margin)
      assertFalse(Wait.waitFor(() -> !hasSnFQueue(servers[0], departedNodeID), 2000, 100),
                  "SnF queue with non-relocatable messages should be retained after max attempts exceeded");
      assertTrue(hasSnFQueue(servers[0], departedNodeID), "SnF queue must remain retained");
   }

   private boolean hasSnFQueue(ActiveMQServer server, String targetNodeID) {
      if (server == null || !server.isStarted()) {
         return false;
      }
      SimpleString name = SimpleString.of(snfQueueName(server, targetNodeID));
      return server.getPostOffice().getAllBindings()
         .filter(LocalQueueBinding.class::isInstance)
         .map(LocalQueueBinding.class::cast)
         .anyMatch(binding -> binding.getQueue().getName().equals(name));
   }
}
