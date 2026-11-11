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

import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class SymmetricClusterWithBackupTest extends SymmetricClusterTest {

   @Override
   @Test
   public void testStopAllStartAll() throws Throwable {
      try {
         setupCluster();

         startServers();

         setupSessionFactory(0, isNetty());
         setupSessionFactory(1, isNetty());
         setupSessionFactory(2, isNetty());
         setupSessionFactory(3, isNetty());
         setupSessionFactory(4, isNetty());

         createQueue(0, "queues.testaddress", "queue0", null, false);
         createQueue(1, "queues.testaddress", "queue0", null, false);
         createQueue(2, "queues.testaddress", "queue0", null, false);
         createQueue(3, "queues.testaddress", "queue0", null, false);
         createQueue(4, "queues.testaddress", "queue0", null, false);

         addConsumer(0, 0, "queue0", null);
         addConsumer(1, 1, "queue0", null);
         addConsumer(2, 2, "queue0", null);
         addConsumer(3, 3, "queue0", null);
         addConsumer(4, 4, "queue0", null);

         waitForBindings(0, "queues.testaddress", 1, 1, true);
         waitForBindings(1, "queues.testaddress", 1, 1, true);
         waitForBindings(2, "queues.testaddress", 1, 1, true);
         waitForBindings(3, "queues.testaddress", 1, 1, true);
         waitForBindings(4, "queues.testaddress", 1, 1, true);

         waitForBindings(0, "queues.testaddress", 4, 4, false);
         waitForBindings(1, "queues.testaddress", 4, 4, false);
         waitForBindings(2, "queues.testaddress", 4, 4, false);
         waitForBindings(3, "queues.testaddress", 4, 4, false);
         waitForBindings(4, "queues.testaddress", 4, 4, false);

         send(0, "queues.testaddress", 10, false, null);

         verifyReceiveRoundRobinInSomeOrder(10, 0, 1, 2, 3, 4);

         verifyNotReceive(0, 1, 2, 3, 4);

         closeAllConsumers();

         closeAllSessionFactories();

         closeAllServerLocatorsFactories();

         stopServers();

         startServers();

         setupSessionFactory(0, isNetty());
         setupSessionFactory(1, isNetty());
         setupSessionFactory(2, isNetty());
         setupSessionFactory(3, isNetty());
         setupSessionFactory(4, isNetty());

         createQueue(0, "queues.testaddress", "queue0", null, false);
         createQueue(1, "queues.testaddress", "queue0", null, false);
         createQueue(2, "queues.testaddress", "queue0", null, false);
         createQueue(3, "queues.testaddress", "queue0", null, false);
         createQueue(4, "queues.testaddress", "queue0", null, false);

         addConsumer(0, 0, "queue0", null);
         addConsumer(1, 1, "queue0", null);
         addConsumer(2, 2, "queue0", null);
         addConsumer(3, 3, "queue0", null);
         addConsumer(4, 4, "queue0", null);

         waitForBindings(0, "queues.testaddress", 1, 1, true);
         waitForBindings(1, "queues.testaddress", 1, 1, true);
         waitForBindings(2, "queues.testaddress", 1, 1, true);
         waitForBindings(3, "queues.testaddress", 1, 1, true);
         waitForBindings(4, "queues.testaddress", 1, 1, true);

         waitForBindings(0, "queues.testaddress", 4, 4, false);
         waitForBindings(1, "queues.testaddress", 4, 4, false);
         waitForBindings(2, "queues.testaddress", 4, 4, false);
         waitForBindings(3, "queues.testaddress", 4, 4, false);
         waitForBindings(4, "queues.testaddress", 4, 4, false);

         send(0, "queues.testaddress", 10, false, null);

         verifyReceiveRoundRobinInSomeOrder(10, 0, 1, 2, 3, 4);

         verifyNotReceive(0, 1, 2, 3, 4);
      } catch (Throwable e) {
         System.out.println(ActiveMQTestBase.threadDump("SymmetricClusterWithBackupTest::testStopAllStartAll"));
         throw e;
      }
   }

   @Override
   @Test
   public void testMixtureLoadBalancedAndNonLoadBalancedQueuesAddQueuesAndConsumersBeforeAllServersAreStarted() throws Exception {
      setupCluster();

      startServers(5, 0);
      setupSessionFactory(0, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue5", null, false);
      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(0, "queues.testaddress", "queue15", null, false);
      createQueue(0, "queues.testaddress", "queue17", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(5, 0, "queue5", null);

      startServers(6, 1);
      setupSessionFactory(1, isNetty());

      createQueue(1, "queues.testaddress", "queue1", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);
      createQueue(1, "queues.testaddress", "queue15", null, false);
      createQueue(1, "queues.testaddress", "queue17", null, false);

      addConsumer(1, 1, "queue1", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(11, 1, "queue11", null);
      addConsumer(16, 1, "queue15", null);

      startServers(7, 2);
      setupSessionFactory(2, isNetty());

      createQueue(2, "queues.testaddress", "queue2", null, false);
      createQueue(2, "queues.testaddress", "queue7", null, false);
      createQueue(2, "queues.testaddress", "queue12", null, false);
      createQueue(2, "queues.testaddress", "queue15", null, false);
      createQueue(2, "queues.testaddress", "queue16", null, false);

      addConsumer(2, 2, "queue2", null);
      addConsumer(7, 2, "queue7", null);
      addConsumer(12, 2, "queue12", null);
      addConsumer(17, 2, "queue15", null);

      startServers(8, 3);
      setupSessionFactory(3, isNetty());

      createQueue(3, "queues.testaddress", "queue3", null, false);
      createQueue(3, "queues.testaddress", "queue8", null, false);
      createQueue(3, "queues.testaddress", "queue13", null, false);
      createQueue(3, "queues.testaddress", "queue15", null, false);
      createQueue(3, "queues.testaddress", "queue16", null, false);
      createQueue(3, "queues.testaddress", "queue18", null, false);

      addConsumer(3, 3, "queue3", null);
      addConsumer(8, 3, "queue8", null);
      addConsumer(13, 3, "queue13", null);
      addConsumer(18, 3, "queue15", null);

      startServers(9, 4);
      setupSessionFactory(4, isNetty());

      createQueue(4, "queues.testaddress", "queue4", null, false);
      createQueue(4, "queues.testaddress", "queue9", null, false);
      createQueue(4, "queues.testaddress", "queue14", null, false);
      createQueue(4, "queues.testaddress", "queue15", null, false);
      createQueue(4, "queues.testaddress", "queue16", null, false);
      createQueue(4, "queues.testaddress", "queue17", null, false);
      createQueue(4, "queues.testaddress", "queue18", null, false);

      addConsumer(4, 4, "queue4", null);
      addConsumer(9, 4, "queue9", null);
      addConsumer(10, 0, "queue10", null);
      addConsumer(14, 4, "queue14", null);

      addConsumer(15, 0, "queue15", null);
      addConsumer(19, 4, "queue15", null);

      addConsumer(20, 2, "queue16", null);
      addConsumer(21, 3, "queue16", null);
      addConsumer(22, 4, "queue16", null);

      addConsumer(23, 0, "queue17", null);
      addConsumer(24, 1, "queue17", null);
      addConsumer(25, 4, "queue17", null);

      addConsumer(26, 3, "queue18", null);
      addConsumer(27, 4, "queue18", null);

      waitForBindings(0, "queues.testaddress", 5, 5, true);
      waitForBindings(1, "queues.testaddress", 5, 5, true);
      waitForBindings(2, "queues.testaddress", 5, 5, true);
      waitForBindings(3, "queues.testaddress", 6, 6, true);
      waitForBindings(4, "queues.testaddress", 7, 7, true);

      waitForBindings(0, "queues.testaddress", 23, 23, false);
      waitForBindings(1, "queues.testaddress", 23, 23, false);
      waitForBindings(2, "queues.testaddress", 23, 23, false);
      waitForBindings(3, "queues.testaddress", 22, 22, false);
      waitForBindings(4, "queues.testaddress", 21, 21, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);

      verifyReceiveRoundRobinInSomeOrder(10, 15, 16, 17, 18, 19);

      verifyReceiveRoundRobinInSomeOrder(10, 20, 21, 22);

      verifyReceiveRoundRobinInSomeOrder(10, 23, 24, 25);

      verifyReceiveRoundRobinInSomeOrder(10, 26, 27);
   }

   @Override
   @Test
   public void testStartStopServers() throws Exception {
      setupCluster();

      startServers();

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());
      setupSessionFactory(3, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue1", null, false);
      createQueue(2, "queues.testaddress", "queue2", null, false);
      createQueue(3, "queues.testaddress", "queue3", null, false);
      createQueue(4, "queues.testaddress", "queue4", null, false);

      createQueue(0, "queues.testaddress", "queue5", null, false);
      createQueue(1, "queues.testaddress", "queue6", null, false);
      createQueue(2, "queues.testaddress", "queue7", null, false);
      createQueue(3, "queues.testaddress", "queue8", null, false);
      createQueue(4, "queues.testaddress", "queue9", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(1, "queues.testaddress", "queue11", null, false);
      createQueue(2, "queues.testaddress", "queue12", null, false);
      createQueue(3, "queues.testaddress", "queue13", null, false);
      createQueue(4, "queues.testaddress", "queue14", null, false);

      createQueue(0, "queues.testaddress", "queue15", null, false);
      createQueue(1, "queues.testaddress", "queue15", null, false);
      createQueue(2, "queues.testaddress", "queue15", null, false);
      createQueue(3, "queues.testaddress", "queue15", null, false);
      createQueue(4, "queues.testaddress", "queue15", null, false);

      createQueue(2, "queues.testaddress", "queue16", null, false);
      createQueue(3, "queues.testaddress", "queue16", null, false);
      createQueue(4, "queues.testaddress", "queue16", null, false);

      createQueue(0, "queues.testaddress", "queue17", null, false);
      createQueue(1, "queues.testaddress", "queue17", null, false);
      createQueue(4, "queues.testaddress", "queue17", null, false);

      createQueue(3, "queues.testaddress", "queue18", null, false);
      createQueue(4, "queues.testaddress", "queue18", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue1", null);
      addConsumer(2, 2, "queue2", null);
      addConsumer(3, 3, "queue3", null);
      addConsumer(4, 4, "queue4", null);

      addConsumer(5, 0, "queue5", null);
      addConsumer(6, 1, "queue6", null);
      addConsumer(7, 2, "queue7", null);
      addConsumer(8, 3, "queue8", null);
      addConsumer(9, 4, "queue9", null);

      addConsumer(10, 0, "queue10", null);
      addConsumer(11, 1, "queue11", null);
      addConsumer(12, 2, "queue12", null);
      addConsumer(13, 3, "queue13", null);
      addConsumer(14, 4, "queue14", null);

      addConsumer(15, 0, "queue15", null);
      addConsumer(16, 1, "queue15", null);
      addConsumer(17, 2, "queue15", null);
      addConsumer(18, 3, "queue15", null);
      addConsumer(19, 4, "queue15", null);

      addConsumer(20, 2, "queue16", null);
      addConsumer(21, 3, "queue16", null);
      addConsumer(22, 4, "queue16", null);

      addConsumer(23, 0, "queue17", null);
      addConsumer(24, 1, "queue17", null);
      addConsumer(25, 4, "queue17", null);

      addConsumer(26, 3, "queue18", null);
      addConsumer(27, 4, "queue18", null);

      waitForBindings(0, "queues.testaddress", 5, 5, true);
      waitForBindings(1, "queues.testaddress", 5, 5, true);
      waitForBindings(2, "queues.testaddress", 5, 5, true);
      waitForBindings(3, "queues.testaddress", 6, 6, true);
      waitForBindings(4, "queues.testaddress", 7, 7, true);

      waitForBindings(0, "queues.testaddress", 23, 23, false);
      waitForBindings(1, "queues.testaddress", 23, 23, false);
      waitForBindings(2, "queues.testaddress", 23, 23, false);
      waitForBindings(3, "queues.testaddress", 22, 22, false);
      waitForBindings(4, "queues.testaddress", 21, 21, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);

      verifyReceiveRoundRobinInSomeOrder(10, 15, 16, 17, 18, 19);

      verifyReceiveRoundRobinInSomeOrder(10, 20, 21, 22);

      verifyReceiveRoundRobinInSomeOrder(10, 23, 24, 25);

      verifyReceiveRoundRobinInSomeOrder(10, 26, 27);

      removeConsumer(0);
      removeConsumer(5);
      removeConsumer(10);
      removeConsumer(15);
      removeConsumer(23);
      removeConsumer(3);
      removeConsumer(8);
      removeConsumer(13);
      removeConsumer(18);
      removeConsumer(21);
      removeConsumer(26);

      closeSessionFactory(0);
      closeSessionFactory(3);

      stopServers(5, 8, 0, 3);

      startServers(0, 3, 5, 8);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(3, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(3, "queues.testaddress", "queue3", null, false);

      createQueue(0, "queues.testaddress", "queue5", null, false);
      createQueue(3, "queues.testaddress", "queue8", null, false);

      createQueue(0, "queues.testaddress", "queue10", null, false);
      createQueue(3, "queues.testaddress", "queue13", null, false);

      createQueue(0, "queues.testaddress", "queue15", null, false);
      createQueue(3, "queues.testaddress", "queue15", null, false);

      createQueue(3, "queues.testaddress", "queue16", null, false);

      createQueue(0, "queues.testaddress", "queue17", null, false);

      createQueue(3, "queues.testaddress", "queue18", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(3, 3, "queue3", null);

      addConsumer(5, 0, "queue5", null);
      addConsumer(8, 3, "queue8", null);

      addConsumer(10, 0, "queue10", null);
      addConsumer(13, 3, "queue13", null);

      addConsumer(15, 0, "queue15", null);
      addConsumer(18, 3, "queue15", null);

      addConsumer(21, 3, "queue16", null);

      addConsumer(23, 0, "queue17", null);

      addConsumer(26, 3, "queue18", null);

      waitForBindings(0, "queues.testaddress", 5, 5, true);
      waitForBindings(1, "queues.testaddress", 5, 5, true);
      waitForBindings(2, "queues.testaddress", 5, 5, true);
      waitForBindings(3, "queues.testaddress", 6, 6, true);
      waitForBindings(4, "queues.testaddress", 7, 7, true);

      waitForBindings(0, "queues.testaddress", 23, 23, false);
      waitForBindings(1, "queues.testaddress", 23, 23, false);
      waitForBindings(2, "queues.testaddress", 23, 23, false);
      waitForBindings(3, "queues.testaddress", 22, 22, false);
      waitForBindings(4, "queues.testaddress", 21, 21, false);

      send(0, "queues.testaddress", 10, false, null);

      verifyReceiveAll(10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);

      verifyReceiveRoundRobinInSomeOrder(10, 15, 16, 17, 18, 19);

      verifyReceiveRoundRobinInSomeOrder(10, 20, 21, 22);

      verifyReceiveRoundRobinInSomeOrder(10, 23, 24, 25);

      verifyReceiveRoundRobinInSomeOrder(10, 26, 27);
   }

   @Override
   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      // The lives
      setupClusterConnectionWithBackups("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, new int[]{1, 2, 3, 4});

      setupClusterConnectionWithBackups("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, new int[]{0, 2, 3, 4});

      setupClusterConnectionWithBackups("cluster2", "queues", messageLoadBalancingType, 1, isNetty(), 2, new int[]{0, 1, 3, 4});

      setupClusterConnectionWithBackups("cluster3", "queues", messageLoadBalancingType, 1, isNetty(), 3, new int[]{0, 1, 2, 4});

      setupClusterConnectionWithBackups("cluster4", "queues", messageLoadBalancingType, 1, isNetty(), 4, new int[]{0, 1, 2, 3});

      // The backups

      setupClusterConnectionWithBackups("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 5, new int[]{0, 1, 2, 3, 4});

      setupClusterConnectionWithBackups("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 6, new int[]{0, 1, 2, 3, 4});

      setupClusterConnectionWithBackups("cluster2", "queues", messageLoadBalancingType, 1, isNetty(), 7, new int[]{0, 1, 2, 3, 4});

      setupClusterConnectionWithBackups("cluster3", "queues", messageLoadBalancingType, 1, isNetty(), 8, new int[]{0, 1, 2, 3, 4});

      setupClusterConnectionWithBackups("cluster4", "queues", messageLoadBalancingType, 1, isNetty(), 9, new int[]{0, 1, 2, 3, 4});
   }

   @Override
   protected void setupServers() throws Exception {
      // The backups
      setupBackupServer(5, 0, isFileStorage(), HAType.SharedStore, isNetty());
      setupBackupServer(6, 1, isFileStorage(), HAType.SharedStore, isNetty());
      setupBackupServer(7, 2, isFileStorage(), HAType.SharedStore, isNetty());
      setupBackupServer(8, 3, isFileStorage(), HAType.SharedStore, isNetty());
      setupBackupServer(9, 4, isFileStorage(), HAType.SharedStore, isNetty());

      // The lives
      setupPrimaryServer(0, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(1, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(2, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(3, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(4, isFileStorage(), HAType.SharedStore, isNetty(), false);
   }

   @Override
   protected void startServers() throws Exception {
      // Need to set backup, since when restarting backup after it has failed over, backup will have been set to false

      getServer(5).getConfiguration().setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration());
      getServer(6).getConfiguration().setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration());
      getServer(7).getConfiguration().setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration());
      getServer(8).getConfiguration().setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration());
      getServer(9).getConfiguration().setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration());

      startServers(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
   }

   @Override
   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(5, 6, 7, 8, 9, 0, 1, 2, 3, 4);
   }

   @Override
   protected void setupProxy() {
      super.setupProxy();

      setupProxy(5);
      setupProxy(6);
      setupProxy(7);
      setupProxy(8);
      setupProxy(9);
   }
}
