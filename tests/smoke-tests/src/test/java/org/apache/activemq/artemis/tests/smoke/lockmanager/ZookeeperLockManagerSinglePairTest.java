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

package org.apache.activemq.artemis.tests.smoke.lockmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.curator.test.TestingZooKeeperServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

//Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class ZookeeperLockManagerSinglePairTest extends LockManagerSinglePairTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final int BASE_SERVER_PORT = 6666;
   // Beware: the server tick must be small enough that to let the session to be correctly expired
   private static final int SERVER_TICK_MS = 100;

   ZookeeperCluster zookeeperCluster;

   @BeforeEach
   @Override
   public void setup() throws Exception {
      super.setup();

      zookeeperCluster = new ZookeeperCluster(temporaryFolder, 3, BASE_SERVER_PORT, SERVER_TICK_MS);
      zookeeperCluster.start();
      assertEquals("127.0.0.1:6666,127.0.0.1:6667,127.0.0.1:6668", zookeeperCluster.getConnectString());
      logger.info("Cluster of {} nodes on: {}", 3, zookeeperCluster.getConnectString());
   }

   @Override
   @AfterEach
   public void after() throws Exception {
      // zk bits that leak from servers
      try {
         super.after();
      } finally {
         zookeeperCluster.stop();
      }
   }

   public ZookeeperLockManagerSinglePairTest() {
      super("zk");
   }

   @Override
   protected boolean awaitAsyncSetupCompleted(long timeout, TimeUnit unit) {
      return true;
   }

   @Override
   protected int[] stopMajority() throws Exception {
      List<TestingZooKeeperServer> followers = zookeeperCluster.getServers();
      final int quorum = (zookeeperCluster.getNodes() / 2) + 1;
      final int[] stopped = new int[quorum];
      for (int i = 0; i < quorum; i++) {
         followers.get(i).stop();
         stopped[i] = i;
      }
      return stopped;
   }

   @Override
   protected void restart(int[] nodes) throws Exception {
      List<TestingZooKeeperServer> servers = zookeeperCluster.getServers();
      for (int nodeIndex : nodes) {
         servers.get(nodeIndex).restart();
      }
   }
}
