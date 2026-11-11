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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

public class AnycastRoutingWithClusterTest extends ClusterTestBase {

   /**
    * Test anycast address with single distributed queue in a 3 node cluster environment.  Messages should be "round
    * robin"'d across the each queue
    */
   @Test
   public void testAnycastAddressOneQueueRoutingMultiNode() throws Exception {
      String address = "test.address";
      String queueName = "test.queue";
      String clusterAddress = "test";

      for (int i = 0; i < 3; i++) {
         setupServer(i, isFileStorage(), isNetty());
      }

      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster2", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 2, 0, 1);

      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.LOCAL, 0);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 1);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 2);

      startServers(0, 1, 2);
      for (int i = 0; i < 3; i++) {
         createAddressInfo(i, address, RoutingType.ANYCAST, -1, false);
         setupSessionFactory(i, isNetty());
         createQueue(i, address, queueName, null, false, RoutingType.ANYCAST);
         addConsumer(i, i, queueName, null);
      }

      for (int i = 0; i < 3; i++) {
         waitForBindings(i, address, 1, 1, true);
         waitForBindings(i, address, 2, 2, false);
      }

      final int noMessages = 30;
      send(0, address, noMessages, true, null, null);

      for (int s = 0; s < 3; s++) {
         final Queue queue = servers[s].locateQueue(SimpleString.of(queueName));
         Wait.waitFor(() -> queue.getMessageCount() == noMessages / 3);
      }

      // Each consumer should receive noMessages / noServers
      for (int i = 0; i < noMessages / 3; i++) {
         for (int c = 0; c < 3; c++) {
            assertNotNull(consumers[c].consumer.receive(1000));
         }
      }
   }


   /**
    * Test anycast address with N queues in a 3 node cluster environment.  Messages should be "round robin"'d across the
    * each queue.
    */
   @Test
   public void testAnycastAddressMultiQueuesRoutingMultiNode() throws Exception {

      String address = "test.address";
      String queueNamePrefix = "test.queue";
      String clusterAddress = "test";

      for (int i = 0; i < 3; i++) {
         setupServer(i, isFileStorage(), isNetty());
      }

      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster2", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 2, 0, 1);

      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.LOCAL, 0);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 1);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 2);

      startServers(0, 1, 2);
      for (int i = 0; i < 3; i++) {
         createAddressInfo(i, address, RoutingType.ANYCAST, -1, false);
         setupSessionFactory(i, isNetty());
         createQueue(i, address, queueNamePrefix + i, null, false, RoutingType.ANYCAST);
         addConsumer(i, i, queueNamePrefix + i, null);
      }

      for (int i = 0; i < 3; i++) {
         waitForBindings(i, address, 1, 1, true);
         waitForBindings(i, address, 2, 2, false);
      }

      final int noMessages = 30;
      send(0, address, noMessages, true, null, null);

      for (int s = 0; s < 3; s++) {
         final Queue queue = servers[s].locateQueue(SimpleString.of(queueNamePrefix + s));
         Wait.waitFor(() -> queue.getMessageCount() == noMessages / 3);
      }

      // Each consumer should receive noMessages / noServers
      for (int i = 0; i < noMessages / 3; i++) {
         for (int c = 0; c < 3; c++) {
            assertNotNull(consumers[c].consumer.receive(1000));
         }
      }
   }

   /**
    * Test anycast address with N queues in a 3 node cluster environment.  Messages should be "round robin"'d across the
    * each queue.
    */
   @Test
   public void testAnycastAddressMultiQueuesWithFilterRoutingMultiNode() throws Exception {

      String address = "test.address";
      String queueNamePrefix = "test.queue";
      String clusterAddress = "test";

      for (int i = 0; i < 3; i++) {
         setupServer(i, isFileStorage(), isNetty());
      }

      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster2", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 2, 0, 1);

      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.LOCAL, 0);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 1);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 2);

      startServers(0, 1, 2);
      for (int i = 0; i < 3; i++) {
         createAddressInfo(i, address, RoutingType.ANYCAST, -1, false);
         setupSessionFactory(i, isNetty());

      }

      String filter1 = "giraffe";
      String filter2 = "platypus";

      createQueue(0, address, queueNamePrefix + 0, filter1, false, RoutingType.ANYCAST);
      createQueue(1, address, queueNamePrefix + 1, filter1, false, RoutingType.ANYCAST);
      createQueue(2, address, queueNamePrefix + 2, filter2, false, RoutingType.ANYCAST);

      for (int i = 0; i < 3; i++) {
         addConsumer(i, i, queueNamePrefix + i, null);
      }

      for (int i = 0; i < 3; i++) {
         waitForBindings(i, address, 1, 1, true);
         waitForBindings(i, address, 2, 2, false);
      }

      final int noMessages = 30;
      send(0, address, noMessages, true, filter1, null);

      // Each consumer should receive noMessages / noServers
      for (int i = 0; i < noMessages / 2; i++) {
         for (int c = 0; c < 2; c++) {
            assertNotNull(consumers[c].consumer.receive(1000));
         }
      }

      assertNull(consumers[2].consumer.receive(1000));
   }

   /**
    * Test multicast address that with N queues in a 3 node cluster environment.  Each queue should receive all messages
    * sent from the client.
    */
   @Test
   public void testMulitcastAddressMultiQueuesRoutingMultiNode() throws Exception {

      String address = "test.address";
      String queueNamePrefix = "test.queue";
      String clusterAddress = "test";

      for (int i = 0; i < 3; i++) {
         setupServer(i, isFileStorage(), isNetty());
      }

      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster2", clusterAddress, MessageLoadBalancingType.STRICT, 1, isNetty(), 2, 0, 1);

      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.LOCAL, 0);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 1);
      setUpGroupHandler(GroupingHandlerConfiguration.TYPE.REMOTE, 2);

      startServers(0, 1, 2);
      for (int i = 0; i < 3; i++) {
         createAddressInfo(i, address, RoutingType.MULTICAST, -1, false);
         setupSessionFactory(i, isNetty());
         createQueue(i, address, queueNamePrefix + i, null, false);
         addConsumer(i, i, queueNamePrefix + i, null);
      }

      for (int i = 0; i < 3; i++) {
         waitForBindings(i, address, 1, 1, true);
         waitForBindings(i, address, 2, 2, false);
      }

      final int noMessages = 30;
      send(0, address, noMessages, true, null, null);

      for (int s = 0; s < 3; s++) {
         final Queue queue = servers[s].locateQueue(SimpleString.of(queueNamePrefix + s));
         Wait.waitFor(() -> queue.getMessageCount() == noMessages);
      }

      // Each consumer should receive noMessages
      for (int i = 0; i < noMessages; i++) {
         for (int c = 0; c < 3; c++) {
            assertNotNull(consumers[c].consumer.receive(1000));
         }
      }
   }

   private boolean isNetty() {
      return true;
   }
}
