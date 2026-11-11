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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemporaryQueueClusterTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
   }

   protected boolean isNetty() {
      return false;
   }

   /**
    * the test checks that the temp queue is properly propagated to the cluster (assuming we wait for the bindings)
    */
   @Test
   public void testSendToTempQueueFromAnotherClusterNode() throws Exception {
      setupCluster();

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      String tempAddress = "queues.tempaddress";
      String tempQueue = "tempqueue";
      // create temp queue on node #0
      ClientSession session = sfs[0].createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(tempQueue).setAddress(tempAddress).setDurable(false).setTemporary(true));
      ClientConsumer consumer = session.createConsumer(tempQueue);

      // check the binding is created on node #1
      waitForBindings(1, tempAddress, 1, 1, false);

      // send to the temp address on node #1
      send(1, tempAddress, 10, false, null);

      session.start();

      // check consumer bound to node #0 receives from the temp queue
      for (int j = 0; j < 10; j++) {
         ClientMessage message = consumer.receive(5000);
         if (message == null) {
            assertNotNull(message, "consumer did not receive message on temp queue " + j);
         }
         message.acknowledge();
      }

      consumer.close();
      session.deleteQueue(tempQueue);
      session.close();
   }

   protected void setupCluster() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }
}
