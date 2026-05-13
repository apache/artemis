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

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.server.impl.ScaleDownHandler;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ScaleDownCommitIntervalTest extends ClusterTestBase {
   final int TEST_SIZE = 1000;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), true, true);
      setupPrimaryServer(1, isFileStorage(), true, true);
      startServers(0, 1);
      setupSessionFactory(0, true);
      setupSessionFactory(1, true);
   }

   @Test
   public void testSmallCommitInterval() throws Exception {
      testCommitInterval(1);
   }

   @Test
   public void testMediumCommitInterval() throws Exception {
      testCommitInterval((int) (TEST_SIZE * 0.33));
   }

   @Test
   public void testLargeCommitInterval() throws Exception {
      testCommitInterval((int) (TEST_SIZE * 0.66));
   }

   @Test
   public void testMaxCommitInterval() throws Exception {
      testCommitInterval(-1);
   }

   private void testCommitInterval(int commitInterval) throws Exception {
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, true);
      createQueue(0, addressName, queueName2, null, true);
      createQueue(1, addressName, queueName1, null, true);
      createQueue(1, addressName, queueName2, null, true);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, true, null);

      // consume a message from queue 2
      addConsumer(1, 0, queueName2, null, false);
      ClientMessage clientMessage = consumers[1].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      consumers[1].getSession().commit();
      removeConsumer(1);

      Wait.assertEquals((long) TEST_SIZE, () -> servers[0].locateQueue(queueName1).getMessageCount(), 500, 20);
      Wait.assertEquals((long) TEST_SIZE - 1, () -> servers[0].locateQueue(queueName2).getMessageCount(), 500, 20);

      assertEquals((long) TEST_SIZE, performScaledown(commitInterval));

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      Wait.assertEquals((long) TEST_SIZE, () -> servers[1].locateQueue(queueName1).getMessageCount(), 500, 20);
      Wait.assertEquals((long) TEST_SIZE - 1, () -> servers[1].locateQueue(queueName2).getMessageCount(), 500, 20);
   }

   private long performScaledown(int commitInterval) throws Exception {
      ScaleDownHandler handler = new ScaleDownHandler(servers[0].getPagingManager(), servers[0].getPostOffice(), servers[0].getNodeManager(), servers[0].getClusterManager().getClusterController(), servers[0].getStorageManager(), commitInterval);

      return handler.scaleDownMessages(sfs[1], servers[1].getNodeID(), servers[0].getConfiguration().getClusterUser(), servers[0].getConfiguration().getClusterPassword());
   }
}
