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

import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ClusteredRequestResponseTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
   }

   protected boolean isNetty() {
      return false;
   }

   @Test
   public void testRequestResponse() throws Exception {
      setupCluster();

      startServers(0, 1, 2, 3, 4);

      for (int i = 0; i < 5; i++) {
         waitForTopology(servers[i], 5);
      }

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());
      setupSessionFactory(3, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress1", "queueA", null, false);
      createQueue(4, "queues.testaddress2", "queueB", null, false);

      addConsumer(0, 0, "queueA", null);
      addConsumer(4, 4, "queueB", null);

      waitForBindings(0, "queues.testaddress1", 1, 1, true);
      waitForBindings(4, "queues.testaddress2", 1, 1, true);

      waitForBindings(1, "queues.testaddress1", 1, 1, false);
      waitForBindings(2, "queues.testaddress1", 1, 1, false);
      waitForBindings(3, "queues.testaddress1", 1, 1, false);
      waitForBindings(4, "queues.testaddress1", 1, 1, false);

      waitForBindings(0, "queues.testaddress2", 1, 1, false);
      waitForBindings(1, "queues.testaddress2", 1, 1, false);
      waitForBindings(2, "queues.testaddress2", 1, 1, false);
      waitForBindings(3, "queues.testaddress2", 1, 1, false);

      send(0, "queues.testaddress2", 10, false, null);

      verifyReceiveAll(10, 4);

      send(4, "queues.testaddress1", 10, false, null);

      verifyReceiveAll(10, 0);
   }

   /*
    * Don't wait for the response queue bindings to get to the other side
    *
    * TODO: I believe this test is invalid. I'm just ignoring it for now. It will probably go away
    */
   @Test
   @Disabled
   public void testRequestResponseNoWaitForBindings() throws Exception {
      setupCluster();

      startServers(0, 1, 2, 3, 4);

      for (int i = 0; i < 5; i++) {
         waitForTopology(servers[i], 5);
      }

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());
      setupSessionFactory(3, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress1", "queueA", null, false);
      createQueue(4, "queues.testaddress2", "queueB", null, false);

      addConsumer(0, 0, "queueA", null);
      addConsumer(4, 4, "queueB", null);

      waitForBindings(4, "queues.testaddress2", 1, 1, true);

      waitForBindings(0, "queues.testaddress2", 1, 1, false);
      waitForBindings(1, "queues.testaddress2", 1, 1, false);
      waitForBindings(2, "queues.testaddress2", 1, 1, false);
      waitForBindings(3, "queues.testaddress2", 1, 1, false);

      send(0, "queues.testaddress2", 10, false, null);

      verifyReceiveAll(10, 4);

      send(4, "queues.testaddress1", 10, false, null);

      verifyReceiveAll(10, 0);
   }

   protected void setupCluster() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1, 2, 3, 4);

      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0, 2, 3, 4);

      setupClusterConnection("cluster2", "queues", messageLoadBalancingType, 1, isNetty(), 2, 0, 1, 3, 4);

      setupClusterConnection("cluster3", "queues", messageLoadBalancingType, 1, isNetty(), 3, 0, 1, 2, 4);

      setupClusterConnection("cluster4", "queues", messageLoadBalancingType, 1, isNetty(), 4, 0, 1, 2, 3);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
      setupServer(2, isFileStorage(), isNetty());
      setupServer(3, isFileStorage(), isNetty());
      setupServer(4, isFileStorage(), isNetty());
   }
}
