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

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class OnewayTwoNodeClusterTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

   }

   private void setupCluster(MessageLoadBalancingType messageLoadBalancingType) {
      for (ActiveMQServer server : servers) {
         if (server != null) {
            server.getConfiguration().getClusterConfigurations().clear();
         }
      }
      // server #0 is connected to server #1
      setupClusterConnection("cluster1", 0, 1, "queues", messageLoadBalancingType, 1, 0, 500, isNetty(), true);
      // server #1 is connected to nobody
      setupClusterConnection("clusterX", 1, -1, "queues", messageLoadBalancingType, 1, 0, 500, isNetty(), true);
   }

   protected boolean isNetty() {
      return false;
   }

   // make sure source can shutdown if target is never started
   @Test
   public void testNeverStartTargetStartSourceThenStopSource() throws Exception {
      startServers(0);

      // Give it a little time for the bridge to try to start
      Thread.sleep(500);

      stopServers(0);
   }

   @Test
   public void testStartTargetServerBeforeSourceServer() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String myFilter = "zebra";

      createQueue(1, "queues.testaddress", "queue0", myFilter, false);
      addConsumer(0, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, myFilter);
      verifyReceiveAll(10, 0);
      verifyNotReceive(0);

      send(0, "queues.testaddress", 10, false, null);
      verifyNotReceive(0);
   }

   @Test
   public void testStartSourceServerBeforeTargetServer() throws Exception {
      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String myFilter = "bison";

      createQueue(1, "queues.testaddress", "queue0", myFilter, false);
      addConsumer(0, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, myFilter);
      verifyReceiveAll(10, 0);
      verifyNotReceive(0);

      send(0, "queues.testaddress", 10, false, null);
      verifyNotReceive(0);
   }

   @Test
   public void testStopAndStartTarget() throws Exception {
      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String myFilter = "bison";

      createQueue(1, "queues.testaddress", "queue0", myFilter, true);
      addConsumer(0, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, myFilter);
      verifyReceiveAll(10, 0);
      verifyNotReceive(0);

      send(0, "queues.testaddress", 10, false, null);
      verifyNotReceive(0);

      removeConsumer(0);
      closeSessionFactory(1);

      long start = System.currentTimeMillis();

      OnewayTwoNodeClusterTest.logger.debug("stopping server 1");

      stopServers(1);

      waitForTopology(servers[0], 1);

      OnewayTwoNodeClusterTest.logger.debug("restarting server 1({})", servers[1].getIdentity());

      startServers(1);

      waitForTopology(servers[0], 2);

      logger.debug("Server 1 id={}", servers[1].getNodeID());

      long end = System.currentTimeMillis();

      // We time how long it takes to restart, since it has been known to hang in the past and wait for a timeout
      // Shutting down and restarting should be pretty quick

      assertTrue(end - start <= 5000, "Took too long to restart");

      setupSessionFactory(1, isNetty(), true);

      addConsumer(0, 1, "queue0", null);

      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, myFilter);
      verifyReceiveAll(10, 0);
      checkReceive(0);
      verifyNotReceive(0);

      send(0, "queues.testaddress", 10, false, null);
      verifyNotReceive(0);
   }

   @Test
   public void testBasicLocalReceive() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveAll(10, 0);
      verifyNotReceive(0);

      addConsumer(1, 0, "queue0", null);
      verifyNotReceive(1);

      //Should be 0 as no messages were sent to the second broker
      verifyClusterMetrics(0, "cluster1", 0, 0);

      //Should be 0 as no messages were sent to the first broker
      verifyClusterMetrics(1, "clusterX", 0, 0);

      //0 messages were sent across the bridge to the second broker
      verifyBridgeMetrics(0, "cluster1", servers[1].getClusterManager().getNodeId(), 0, 0);
   }

   @Test
   public void testBasicRoundRobin() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 0, 0, false);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);

      //half of the messages should be sent over bridge, other half was consumed by local consumer
      verifyClusterMetrics(0, "cluster1", 5, 5);

      //Should be 0 as no messages were sent to the first broker
      verifyClusterMetrics(1, "clusterX", 0, 0);

      //5 messages were sent across the bridge to the second broker
      verifyBridgeMetrics(0, "cluster1", servers[1].getClusterManager().getNodeId(), 5, 5);
   }

   @Test
   public void testRoundRobinMultipleQueues() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);

      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(1, "queues.testaddress", "queue2", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      addConsumer(2, 0, "queue1", null);
      addConsumer(3, 1, "queue1", null);

      addConsumer(4, 0, "queue2", null);
      addConsumer(5, 1, "queue2", null);

      waitForBindings(0, "queues.testaddress", 3, 3, true);
      waitForBindings(0, "queues.testaddress", 3, 3, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveRoundRobin(10, 0, 1);

      verifyReceiveRoundRobin(10, 2, 3);

      verifyReceiveRoundRobin(10, 4, 5);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testMultipleNonLoadBalancedQueues() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(0, "queues.testaddress", "queue4", null, false);

      createQueue(1, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "queues.testaddress", "queue8", null, false);
      createQueue(1, "queues.testaddress", "queue9", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);

      addConsumer(5, 1, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);

      waitForBindings(0, "queues.testaddress", 5, 5, true);
      waitForBindings(0, "queues.testaddress", 5, 5, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
   }

   @Test
   public void testMixtureLoadBalancedAndNonLoadBalancedQueues() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(0, "queues.testaddress", "queue4", null, false);

      createQueue(1, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "queues.testaddress", "queue8", null, false);
      createQueue(1, "queues.testaddress", "queue9", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue10", null, false);

      createQueue(0, "queues.testaddress", "queue11", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);

      createQueue(0, "queues.testaddress", "queue12", null, false);
      createQueue(1, "queues.testaddress", "queue12", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);

      addConsumer(5, 1, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);

      addConsumer(10, 0, "queue10", null);
      addConsumer(11, 1, "queue10", null);

      addConsumer(12, 0, "queue11", null);
      addConsumer(13, 1, "queue11", null);

      addConsumer(14, 0, "queue12", null);
      addConsumer(15, 1, "queue12", null);

      waitForBindings(0, "queues.testaddress", 8, 8, true);
      waitForBindings(0, "queues.testaddress", 8, 8, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      verifyReceiveRoundRobin(10, 10, 11);
      verifyReceiveRoundRobin(10, 12, 13);
      verifyReceiveRoundRobin(10, 14, 15);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
   }

   @Test
   public void testMixtureLoadBalancedAndNonLoadBalancedQueuesRemoveSomeQueuesAndConsumers() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(0, "queues.testaddress", "queue4", null, false);

      createQueue(1, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "queues.testaddress", "queue8", null, false);
      createQueue(1, "queues.testaddress", "queue9", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue10", null, false);

      createQueue(0, "queues.testaddress", "queue11", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);

      createQueue(0, "queues.testaddress", "queue12", null, false);
      createQueue(1, "queues.testaddress", "queue12", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);

      addConsumer(5, 1, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);

      addConsumer(10, 0, "queue10", null);
      addConsumer(11, 1, "queue10", null);

      addConsumer(12, 0, "queue11", null);
      addConsumer(13, 1, "queue11", null);

      addConsumer(14, 0, "queue12", null);
      addConsumer(15, 1, "queue12", null);

      waitForBindings(0, "queues.testaddress", 8, 8, true);
      waitForBindings(0, "queues.testaddress", 8, 8, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      verifyReceiveRoundRobin(10, 10, 11);
      verifyReceiveRoundRobin(10, 12, 13);
      verifyReceiveRoundRobin(10, 14, 15);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

      removeConsumer(10);
      removeConsumer(13);
      removeConsumer(14);

      deleteQueue(0, "queue10");
      deleteQueue(1, "queue11");
      deleteQueue(0, "queue12");

      waitForBindings(0, "queues.testaddress", 6, 6, true);
      waitForBindings(0, "queues.testaddress", 7, 7, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 15);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 15);
   }

   @Test
   public void testMixtureLoadBalancedAndNonLoadBalancedQueuesAddQueuesOnTargetBeforeStartSource() throws Exception {
      startServers(1);

      setupSessionFactory(1, isNetty(), true);

      createQueue(1, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "queues.testaddress", "queue8", null, false);
      createQueue(1, "queues.testaddress", "queue9", null, false);

      createQueue(1, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);
      createQueue(1, "queues.testaddress", "queue12", null, false);

      addConsumer(5, 1, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);

      addConsumer(11, 1, "queue10", null);
      addConsumer(13, 1, "queue11", null);
      addConsumer(15, 1, "queue12", null);

      startServers(0);

      waitForBindings(0, "queues.testaddress", 8, 8, false);

      setupSessionFactory(0, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(0, "queues.testaddress", "queue4", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(0, "queues.testaddress", "queue11", null, false);
      createQueue(0, "queues.testaddress", "queue12", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);

      addConsumer(10, 0, "queue10", null);
      addConsumer(12, 0, "queue11", null);
      addConsumer(14, 0, "queue12", null);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      verifyReceiveRoundRobin(10, 11, 10);
      verifyReceiveRoundRobin(10, 13, 12);
      verifyReceiveRoundRobin(10, 15, 14);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
   }

   @Test
   public void testMixtureLoadBalancedAndNonLoadBalancedQueuesAddQueuesOnSourceBeforeStartTarget() throws Exception {
      startServers(0);

      setupSessionFactory(0, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(0, "queues.testaddress", "queue4", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(0, "queues.testaddress", "queue11", null, false);
      createQueue(0, "queues.testaddress", "queue12", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);

      addConsumer(10, 0, "queue10", null);
      addConsumer(12, 0, "queue11", null);
      addConsumer(14, 0, "queue12", null);

      startServers(1);

      setupSessionFactory(1, isNetty(), true);

      createQueue(1, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "queues.testaddress", "queue8", null, false);
      createQueue(1, "queues.testaddress", "queue9", null, false);

      createQueue(1, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);
      createQueue(1, "queues.testaddress", "queue12", null, false);

      addConsumer(5, 1, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);

      addConsumer(11, 1, "queue10", null);
      addConsumer(13, 1, "queue11", null);
      addConsumer(15, 1, "queue12", null);

      waitForBindings(0, "queues.testaddress", 8, 8, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      verifyReceiveRoundRobin(10, 10, 11);
      verifyReceiveRoundRobin(10, 12, 13);
      verifyReceiveRoundRobin(10, 14, 15);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
   }

   @Test
   public void testNotRouteToNonMatchingAddress() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);

      createQueue(0, "queues.testaddress2", "queue2", null, false);
      createQueue(1, "queues.testaddress2", "queue2", null, false);
      createQueue(0, "queues.testaddress2", "queue3", null, false);
      createQueue(1, "queues.testaddress2", "queue4", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 1, "queue2", null);
      addConsumer(4, 0, "queue3", null);
      addConsumer(5, 1, "queue4", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);

      waitForBindings(0, "queues.testaddress2", 2, 2, true);
      waitForBindings(0, "queues.testaddress2", 2, 2, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1);

      verifyNotReceive(2, 3, 4, 5);
   }

   @Test
   public void testNonLoadBalancedQueuesWithFilters() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String filter1 = "giraffe";
      String filter2 = "aardvark";

      createQueue(0, "queues.testaddress", "queue0", filter1, false);
      createQueue(0, "queues.testaddress", "queue1", filter2, false);
      createQueue(0, "queues.testaddress", "queue2", filter1, false);
      createQueue(0, "queues.testaddress", "queue3", filter2, false);
      createQueue(0, "queues.testaddress", "queue4", filter1, false);

      createQueue(1, "queues.testaddress", "queue5", filter2, false);
      createQueue(1, "queues.testaddress", "queue6", filter1, false);
      createQueue(1, "queues.testaddress", "queue7", filter2, false);
      createQueue(1, "queues.testaddress", "queue8", filter1, false);
      createQueue(1, "queues.testaddress", "queue9", filter2, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);

      addConsumer(5, 1, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);

      addConsumer(10, 0, "queue10", null);

      addConsumer(11, 1, "queue11", null);

      waitForBindings(0, "queues.testaddress", 6, 6, true);
      waitForBindings(0, "queues.testaddress", 6, 6, false);

      send(0, "queues.testaddress", 10, false, filter1);

      verifyReceiveAll(10, 0, 2, 4, 6, 8, 10, 11);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

      send(0, "queues.testaddress", 10, false, filter2);

      verifyReceiveAll(10, 1, 3, 5, 7, 9, 10, 11);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveAll(10, 10, 11);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
   }

   @Test
   public void testRoundRobinMultipleQueuesWithFilters() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String filter1 = "giraffe";
      String filter2 = "aardvark";

      createQueue(0, "queues.testaddress", "queue0", filter1, false);
      createQueue(1, "queues.testaddress", "queue0", filter1, false);

      createQueue(0, "queues.testaddress", "queue1", filter1, false);
      createQueue(1, "queues.testaddress", "queue1", filter2, false);

      createQueue(0, "queues.testaddress", "queue2", filter2, false);
      createQueue(1, "queues.testaddress", "queue2", filter1, false);

      createQueue(0, "queues.testaddress", "queue3", filter2, false);
      createQueue(1, "queues.testaddress", "queue3", filter2, false);

      createQueue(0, "queues.testaddress", "queue4", null, false);
      createQueue(1, "queues.testaddress", "queue4", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      addConsumer(2, 0, "queue1", null);
      addConsumer(3, 1, "queue1", null);

      addConsumer(4, 0, "queue2", null);
      addConsumer(5, 1, "queue2", null);

      addConsumer(6, 0, "queue3", null);
      addConsumer(7, 1, "queue3", null);

      addConsumer(8, 0, "queue4", null);
      addConsumer(9, 1, "queue4", null);

      waitForBindings(0, "queues.testaddress", 5, 5, true);
      waitForBindings(0, "queues.testaddress", 5, 5, false);

      send(0, "queues.testaddress", 10, false, filter1);

      verifyReceiveRoundRobin(10, 0, 1);
      verifyReceiveRoundRobin(10, 8, 9);

      verifyReceiveAll(10, 2, 5);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      send(0, "queues.testaddress", 10, false, filter2);

      verifyReceiveRoundRobin(10, 6, 7);
      verifyReceiveRoundRobin(10, 8, 9);

      verifyReceiveAll(10, 3, 4);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 8, 9);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
   }

   @Test
   public void testRouteWhenNoConsumersFalseNonBalancedQueues() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);

      createQueue(1, "queues.testaddress", "queue3", null, false);
      createQueue(1, "queues.testaddress", "queue4", null, false);
      createQueue(1, "queues.testaddress", "queue5", null, false);

      waitForBindings(0, "queues.testaddress", 3, 0, true);
      waitForBindings(0, "queues.testaddress", 3, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);

      addConsumer(3, 1, "queue3", null);
      addConsumer(4, 1, "queue4", null);
      addConsumer(5, 1, "queue5", null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testRouteWhenNoConsumersTrueNonBalancedQueues() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);

      createQueue(1, "queues.testaddress", "queue3", null, false);
      createQueue(1, "queues.testaddress", "queue4", null, false);
      createQueue(1, "queues.testaddress", "queue5", null, false);

      waitForBindings(0, "queues.testaddress", 3, 0, true);
      waitForBindings(0, "queues.testaddress", 3, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);

      addConsumer(3, 1, "queue3", null);
      addConsumer(4, 1, "queue4", null);
      addConsumer(5, 1, "queue5", null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testRouteWhenNoConsumersFalseLoadBalancedQueues() throws Exception {
      setupCluster(MessageLoadBalancingType.STRICT);
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue2", null, false);

      waitForBindings(0, "queues.testaddress", 3, 0, true);
      waitForBindings(0, "queues.testaddress", 3, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);

      addConsumer(3, 1, "queue0", null);
      addConsumer(4, 1, "queue1", null);
      addConsumer(5, 1, "queue2", null);

      // If route when no consumers is false but there is no consumer on the local queue then messages should be round
      // robin'd
      // It's only in the case where there is a local consumer they shouldn't be round robin'd

      verifyReceiveRoundRobin(10, 0, 3);
      verifyReceiveRoundRobin(10, 1, 4);
      verifyReceiveRoundRobin(10, 2, 5);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testRouteWhenNoConsumersFalseLoadBalancedQueuesLocalConsumer() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue2", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);

      waitForBindings(0, "queues.testaddress", 3, 3, true);
      waitForBindings(0, "queues.testaddress", 3, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(3, 1, "queue0", null);
      addConsumer(4, 1, "queue1", null);
      addConsumer(5, 1, "queue2", null);

      // In this case, since the local queue has a consumer, it should receive all the messages

      verifyReceiveAll(10, 0, 1, 2);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testRouteWhenNoConsumersFalseLoadBalancedQueuesNoLocalQueue() throws Exception {
      setupCluster(MessageLoadBalancingType.STRICT);

      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);

      waitForBindings(0, "queues.testaddress", 2, 0, true);
      waitForBindings(0, "queues.testaddress", 2, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);

      addConsumer(2, 1, "queue0", null);
      addConsumer(3, 1, "queue1", null);

      verifyReceiveRoundRobin(10, 0, 2);
      verifyReceiveRoundRobin(10, 1, 3);

      verifyNotReceive(0, 1, 2, 3);
   }

   @Test
   public void testRouteWhenNoConsumersTrueLoadBalancedQueues() throws Exception {
      setupCluster(MessageLoadBalancingType.STRICT);
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue2", null, false);

      waitForBindings(0, "queues.testaddress", 3, 0, true);
      waitForBindings(0, "queues.testaddress", 3, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);

      addConsumer(3, 1, "queue0", null);
      addConsumer(4, 1, "queue1", null);
      addConsumer(5, 1, "queue2", null);

      verifyReceiveRoundRobin(10, 0, 3);
      verifyReceiveRoundRobin(10, 1, 4);
      verifyReceiveRoundRobin(10, 2, 5);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testRouteWhenNoConsumersTrueLoadBalancedQueuesLocalConsumer() throws Exception {
      servers[0].getConfiguration().getClusterConfigurations().clear();
      // server #0 is connected to server #1
      setupClusterConnection("cluster1", 0, 1, "queues", MessageLoadBalancingType.STRICT, 1, isNetty(), true);

      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue2", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);

      waitForBindings(0, "queues.testaddress", 3, 3, true);
      waitForBindings(0, "queues.testaddress", 3, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(3, 1, "queue0", null);
      addConsumer(4, 1, "queue1", null);
      addConsumer(5, 1, "queue2", null);

      verifyReceiveRoundRobin(10, 0, 3);
      verifyReceiveRoundRobin(10, 1, 4);
      verifyReceiveRoundRobin(10, 2, 5);

      verifyNotReceive(0, 1, 2, 3, 4, 5);
   }

   @Test
   public void testRouteWhenNoConsumersTrueLoadBalancedQueuesNoLocalQueue() throws Exception {
      servers[0].getConfiguration().getClusterConfigurations().clear();
      // server #0 is connected to server #1
      setupClusterConnection("cluster1", 0, 1, "queues", MessageLoadBalancingType.STRICT, 1, isNetty(), true);

      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);

      waitForBindings(0, "queues.testaddress", 2, 0, true);
      waitForBindings(0, "queues.testaddress", 2, 0, false);

      send(0, "queues.testaddress", 10, false, null);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);

      addConsumer(2, 1, "queue0", null);
      addConsumer(3, 1, "queue1", null);

      verifyReceiveRoundRobin(10, 0, 2);
      verifyReceiveRoundRobin(10, 1, 3);

      verifyNotReceive(0, 1, 2, 3);
   }

   @Test
   public void testNonLoadBalancedQueuesWithConsumersWithFilters() throws Exception {
      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String filter1 = "giraffe";
      String filter2 = "aardvark";

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(0, "queues.testaddress", "queue4", null, false);

      createQueue(1, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "queues.testaddress", "queue8", null, false);
      createQueue(1, "queues.testaddress", "queue9", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);

      addConsumer(0, 0, "queue0", filter1);
      addConsumer(1, 0, "queue1", filter2);
      addConsumer(2, 0, "queue2", filter1);
      addConsumer(3, 0, "queue3", filter2);
      addConsumer(4, 0, "queue4", filter1);

      addConsumer(5, 1, "queue5", filter2);
      addConsumer(6, 1, "queue6", filter1);
      addConsumer(7, 1, "queue7", filter2);
      addConsumer(8, 1, "queue8", filter1);
      addConsumer(9, 1, "queue9", filter2);

      addConsumer(10, 0, "queue10", null);

      addConsumer(11, 1, "queue11", null);

      waitForBindings(0, "queues.testaddress", 6, 6, true);
      waitForBindings(0, "queues.testaddress", 6, 6, false);

      send(0, "queues.testaddress", 10, false, filter1);

      verifyReceiveAll(10, 0, 2, 4, 6, 8, 10, 11);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

      send(0, "queues.testaddress", 10, false, filter2);

      verifyReceiveAll(10, 1, 3, 5, 7, 9, 10, 11);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
   }

   @Test
   public void testRoundRobinMultipleQueuesWithConsumersWithFilters() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      String filter1 = "giraffe";
      String filter2 = "aardvark";

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);

      createQueue(0, "queues.testaddress", "queue2", null, false);
      createQueue(1, "queues.testaddress", "queue2", null, false);

      createQueue(0, "queues.testaddress", "queue3", null, false);
      createQueue(1, "queues.testaddress", "queue3", null, false);

      createQueue(0, "queues.testaddress", "queue4", null, false);
      createQueue(1, "queues.testaddress", "queue4", null, false);

      addConsumer(0, 0, "queue0", filter1);
      addConsumer(1, 1, "queue0", filter1);

      addConsumer(2, 0, "queue1", filter1);
      addConsumer(3, 1, "queue1", filter2);

      addConsumer(4, 0, "queue2", filter2);
      addConsumer(5, 1, "queue2", filter1);

      addConsumer(6, 0, "queue3", filter2);
      addConsumer(7, 1, "queue3", filter2);

      addConsumer(8, 0, "queue4", null);
      addConsumer(9, 1, "queue4", null);

      waitForBindings(0, "queues.testaddress", 5, 5, true);
      waitForBindings(0, "queues.testaddress", 5, 5, false);

      send(0, "queues.testaddress", 10, false, filter1);

      verifyReceiveRoundRobin(10, 0, 1);
      verifyReceiveRoundRobin(10, 8, 9);

      verifyReceiveAll(10, 2, 5);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      send(0, "queues.testaddress", 10, false, filter2);

      // verifyReceiverRoundRobin should play nicely independently of who receives first.
      // I had some issues recently with the first being consumer 7, because of some fixes on binding.
      // this is still legal. We just need to guarantee is round robbed. no need to be strict on what performs first.
      // When this is the first time receiving 6 is always the first but it can change depending on what happened earlier
      verifyReceiveRoundRobin(10, 7, 6);
      verifyReceiveRoundRobin(10, 8, 9);

      verifyReceiveAll(10, 3, 4);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 8, 9);
      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

   }

   @Test
   public void testMultipleClusterConnections() throws Exception {
      setupClusterConnection("cluster2", 0, 1, "q2", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), true);
      setupClusterConnection("cluster3", 0, 1, "q3", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), true);

      startServers(1, 0);

      setupSessionFactory(0, isNetty(), true);
      setupSessionFactory(1, isNetty(), true);

      // Make sure the different connections don't conflict

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);
      createQueue(0, "q2.testaddress", "queue2", null, false);
      createQueue(0, "q2.testaddress", "queue3", null, false);
      createQueue(0, "q3.testaddress", "queue4", null, false);
      createQueue(0, "q3.testaddress", "queue5", null, false);

      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue7", null, false);
      createQueue(1, "q2.testaddress", "queue8", null, false);
      createQueue(1, "q2.testaddress", "queue9", null, false);
      createQueue(1, "q3.testaddress", "queue10", null, false);
      createQueue(1, "q3.testaddress", "queue11", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);
      addConsumer(2, 0, "queue2", null);
      addConsumer(3, 0, "queue3", null);
      addConsumer(4, 0, "queue4", null);
      addConsumer(5, 0, "queue5", null);

      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 1, "queue7", null);
      addConsumer(8, 1, "queue8", null);
      addConsumer(9, 1, "queue9", null);
      addConsumer(10, 1, "queue10", null);
      addConsumer(11, 1, "queue11", null);

      waitForBindings(0, "queues.testaddress", 2, 2, true);
      waitForBindings(0, "queues.testaddress", 2, 2, false);

      waitForBindings(0, "q2.testaddress", 2, 2, true);
      waitForBindings(0, "q2.testaddress", 2, 2, false);

      waitForBindings(0, "q3.testaddress", 2, 2, true);
      waitForBindings(0, "q3.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 6, 7);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

      send(0, "q2.testaddress", 10, false, null);

      verifyReceiveAll(10, 2, 3, 8, 9);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

      send(0, "q3.testaddress", 10, false, null);

      verifyReceiveAll(10, 4, 5, 10, 11);

      verifyNotReceive(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
   }

}
