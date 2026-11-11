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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of the store-and-forward (SnF) scale-down relocation in
 * {@link QueueImpl#moveReferencesBetweenSnFQueues(String)}: each SnF reference is routed directly to the binding of the
 * scale-down target that has the same routing name, here the local queue binding of the draining node itself.
 * <p>
 * This exercises the parts that cannot be covered with mocks: the SnF message really carries the application address
 * and the remote queue ids resolve to the real bindings, so the directed route reaches the real queue and the source
 * references are acknowledged.
 */
public class ScaleDownSnFRelocateTest extends ClusterTestBase {

   private static final String CLUSTER_NAME = "cluster0";
   private static final String ADDRESS = "testAddress";
   private static final String QUEUE = "testQueue";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), isNetty(), true);
      setupPrimaryServer(1, isFileStorage(), isNetty(), true);
      setupPrimaryServer(2, isFileStorage(), isNetty(), true);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0, 2);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1);
      servers[0].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      servers[1].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      servers[2].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      startServers(0, 1, 2);
      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());
   }

   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testAnycastRelocateRoutesDirectlyToLocalTargetBinding() throws Exception {
      final int messageCount = 10;

      createQueue(0, ADDRESS, QUEUE, null, true, RoutingType.ANYCAST);
      createQueue(1, ADDRESS, QUEUE, null, true, RoutingType.ANYCAST);

      // a consumer only on node 1 so ON_DEMAND routes node 0 sends into the SnF queue toward node 1
      addConsumer(0, 1, QUEUE, null);
      waitForBindings(0, ADDRESS, 1, 1, false);

      // pause the SnF queue so the bridge cannot forward: messages accumulate there instead of reaching node 1
      Queue snf = getSnFQueue(servers[0], servers[1]);
      snf.pause();

      // send with duplicate detection ids, as a real producer would
      AtomicInteger duplicateDetectionSeq = new AtomicInteger(0);
      send(0, ADDRESS, messageCount, true, null, RoutingType.ANYCAST, duplicateDetectionSeq);
      Wait.assertEquals(messageCount, () -> getMessageCount(snf), 5000);

      // fence the departing node's binding exactly as the scale-down drain does before moving the references
      servers[0].getClusterManager().getClusterConnection(CLUSTER_NAME).disableRecord(servers[1].getNodeID().toString());

      // give node 0's local queue demand so the directed route has somewhere to land
      addConsumer(1, 0, QUEUE, null);
      waitForBindings(0, ADDRESS, 1, 1, true);

      // relocate toward node 0 itself: every reference is routed directly to node 0's local queue binding
      ((QueueImpl) snf).moveReferencesBetweenSnFQueues(servers[0].getNodeID().toString());

      // all messages must be delivered to node 0's local queue: proves the directed route used the application
      // address and the real binding
      for (int i = 0; i < messageCount; i++) {
         ClientMessage message = consumers[1].getConsumer().receive(5000);
         assertNotNull(message, "message " + i + " should be routed to the local queue on node 0");
         message.acknowledge();
      }

      Wait.assertEquals(0, () -> getMessageCount(snf), 5000);
   }

   @Test
   public void testAnycastRelocateRoutesDirectlyToRemoteTargetBinding() throws Exception {
      final int messageCount = 10;

      createQueue(0, ADDRESS, QUEUE, null, true, RoutingType.ANYCAST);
      createQueue(1, ADDRESS, QUEUE, null, true, RoutingType.ANYCAST);
      createQueue(2, ADDRESS, QUEUE, null, true, RoutingType.ANYCAST);

      // a consumer only on node 1 so ON_DEMAND routes node 0 sends into the SnF queue toward node 1
      addConsumer(0, 1, QUEUE, null);
      waitForBindings(0, ADDRESS, 2, 1, false);

      // pause the SnF queue so the bridge cannot forward: messages accumulate there instead of reaching node 1
      Queue snf01 = getSnFQueue(servers[0], servers[1]);
      snf01.pause();

      // send with duplicate detection ids, as a real producer would
      AtomicInteger duplicateDetectionSeq = new AtomicInteger(0);
      send(0, ADDRESS, messageCount, true, null, RoutingType.ANYCAST, duplicateDetectionSeq);
      Wait.assertEquals(messageCount, () -> getMessageCount(snf01), 5000);

      // fence the departing node 1's binding exactly as the scale-down drain does before moving the references
      servers[0].getClusterManager().getClusterConnection(CLUSTER_NAME).disableRecord(servers[1].getNodeID().toString());

      // give node 2's remote queue demand so the directed route has somewhere to land
      addConsumer(1, 2, QUEUE, null);
      waitForBindings(0, ADDRESS, 2, 2, false);

      // relocate toward node 2: every reference is routed directly to node 2's remote queue binding on node 0
      ((QueueImpl) snf01).moveReferencesBetweenSnFQueues(servers[2].getNodeID().toString());

      // all messages must be forwarded across the cluster bridge and delivered to the consumer on node 2
      for (int i = 0; i < messageCount; i++) {
         ClientMessage message = consumers[1].getConsumer().receive(5000);
         assertNotNull(message, "message " + i + " should be routed to the remote queue on node 2");
         message.acknowledge();
      }

      Wait.assertEquals(0, () -> getMessageCount(snf01), 5000);
   }

   private Queue getSnFQueue(ActiveMQServer server, ActiveMQServer targetServer) {
      String snfPrefix = server.getInternalNamingPrefix() + "sf.";
      String targetNodeID = targetServer.getNodeID().toString();
      return server.getPostOffice().getAllBindings()
         .filter(LocalQueueBinding.class::isInstance)
         .map(LocalQueueBinding.class::cast)
         .map(LocalQueueBinding::getQueue)
         .filter(queue -> queue.getAddress().toString().startsWith(snfPrefix) && queue.getAddress().toString().endsWith(targetNodeID))
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No SnF queue toward " + targetNodeID));
   }
}
