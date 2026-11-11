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
package org.apache.activemq.artemis.tests.integration.cluster.restart;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterRestartTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testRestartWithQueuesCreateInDiffOrder() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      logger.debug("server 0 = {}", getServer(0).getNodeID());
      logger.debug("server 1 = {}", getServer(1).getNodeID());

      setupSessionFactory(0, isNetty(), 15);
      setupSessionFactory(1, isNetty());

      // create some dummy queues to ensure that the test queue has a high numbered binding
      createQueue(0, "queues.testaddress2", "queue0", null, false);
      createQueue(0, "queues.testaddress2", "queue1", null, false);
      createQueue(0, "queues.testaddress2", "queue2", null, false);
      createQueue(0, "queues.testaddress2", "queue3", null, false);
      createQueue(0, "queues.testaddress2", "queue4", null, false);
      createQueue(0, "queues.testaddress2", "queue5", null, false);
      createQueue(0, "queues.testaddress2", "queue6", null, false);
      createQueue(0, "queues.testaddress2", "queue7", null, false);
      createQueue(0, "queues.testaddress2", "queue8", null, false);
      createQueue(0, "queues.testaddress2", "queue9", null, false);
      // now create the 2 queues and make sure they are durable
      createQueue(0, "queues.testaddress", "queue10", null, true);
      createQueue(1, "queues.testaddress", "queue10", null, true);

      addConsumer(0, 0, "queue10", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      printBindings(2);

      sendInRange(1, "queues.testaddress", 0, 10, true, null);

      stopServers(0);
      // Waiting some time after stopped
      Thread.sleep(2000);
      startServers(0);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      printBindings(2);

      sendInRange(1, "queues.testaddress", 10, 20, false, null);

      verifyReceiveAllInRange(0, 20, 0);
      logger.debug("*****************************************************************************");
   }

   @Test
   public void testRestartWithQueuesCreateInDiffOrder2() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      logger.debug("server 0 = {}", getServer(0).getNodeID());
      logger.debug("server 1 = {}", getServer(1).getNodeID());
      setupSessionFactory(0, isNetty(), 15);
      setupSessionFactory(1, isNetty());

      // create some dummy queues to ensure that the test queue has a high numbered binding
      createQueue(0, "queues.testaddress2", "queue0", null, false);
      createQueue(0, "queues.testaddress2", "queue1", null, false);
      createQueue(0, "queues.testaddress2", "queue2", null, false);
      createQueue(0, "queues.testaddress2", "queue3", null, false);
      createQueue(0, "queues.testaddress2", "queue4", null, false);
      createQueue(0, "queues.testaddress2", "queue5", null, false);
      createQueue(0, "queues.testaddress2", "queue6", null, false);
      createQueue(0, "queues.testaddress2", "queue7", null, false);
      createQueue(0, "queues.testaddress2", "queue8", null, false);
      createQueue(0, "queues.testaddress2", "queue9", null, false);
      // now create the 2 queues and make sure they are durable
      createQueue(0, "queues.testaddress", "queue10", null, true);
      createQueue(1, "queues.testaddress", "queue10", null, true);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      printBindings(2);

      sendInRange(1, "queues.testaddress", 0, 10, true, null);

      logger.debug("stopping******************************************************");
      stopServers(0);

      sendInRange(1, "queues.testaddress", 10, 20, true, null);
      logger.debug("stopped******************************************************");
      startServers(0);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      printBindings(2);
      addConsumer(0, 1, "queue10", null);
      addConsumer(1, 0, "queue10", null);

      verifyReceiveRoundRobin(0, 20, 0, 1);
      logger.debug("*****************************************************************************");
   }

   private void printBindings(final int num) throws Exception {
      for (int i = 0; i < num; i++) {
         Collection<Binding> bindings0 = getServer(i).getPostOffice().getBindingsForAddress(SimpleString.of("queues.testaddress")).getBindings();
         for (Binding binding : bindings0) {
            logger.debug("{} on node {} at {}", binding, i, binding.getID());
         }
      }
   }

   public boolean isNetty() {
      return true;
   }

}
