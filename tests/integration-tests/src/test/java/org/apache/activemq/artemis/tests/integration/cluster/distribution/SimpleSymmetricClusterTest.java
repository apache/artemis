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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.Wait;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class SimpleSymmetricClusterTest extends ClusterTestBase {

   private static final Logger log = Logger.getLogger(SimpleSymmetricClusterTest.class);



   public boolean isNetty() {
      return false;
   }

   @Test
   public void testSimpleWithBackup() throws Exception {
      // The backups
      setupBackupServer(0, 3, isFileStorage(), HAType.SharedStore, isNetty());
      setupBackupServer(1, 4, isFileStorage(), HAType.SharedStore, isNetty());
      setupBackupServer(2, 5, isFileStorage(), HAType.SharedStore, isNetty());

      // The lives
      setupLiveServer(3, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupLiveServer(4, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupLiveServer(5, isFileStorage(), HAType.SharedStore, isNetty(), false);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 3, 4, 5);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 4, 3, 5);

      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 5, 3, 4);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 4, 5);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 3, 5);

      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 3, 4);

      startServers(0, 1, 2, 3, 4, 5);

      log.debug("");
      for (int i = 0; i <= 5; i++) {
         log.debug(servers[i].describe());
         log.debug(debugBindings(servers[i], servers[i].getConfiguration().getManagementNotificationAddress().toString()));
      }
      log.debug("");

      log.debug("");
      for (int i = 0; i <= 5; i++) {
         log.debug(servers[i].describe());
         log.debug(debugBindings(servers[i], servers[i].getConfiguration().getManagementNotificationAddress().toString()));
      }
      log.debug("");

      stopServers(0, 1, 2, 3, 4, 5);

   }

   @Test
   public void testSimple() throws Exception {
      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      setupServer(2, true, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 2, 0);
      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1);

      startServers(0, 1, 2);

      waitForTopology(servers[0], 3);
      waitForTopology(servers[1], 3);
      waitForTopology(servers[2], 3);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

   }

   @Test
   public void testSimpleRestartClusterConnection() throws Exception {
      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      setupServer(2, true, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 2, 0);
      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1);

      startServers(0, 1, 2);

      waitForTopology(servers[0], 3);
      waitForTopology(servers[1], 3);
      waitForTopology(servers[2], 3);

      ClusterConnection clusterConnection0 = getServer(0).getClusterManager().getClusterConnection("cluster0");
      ClusterConnection clusterConnection1 = getServer(1).getClusterManager().getClusterConnection("cluster1");
      ClusterConnection clusterConnection2 = getServer(2).getClusterManager().getClusterConnection("cluster2");


      Wait.assertEquals(2, () -> ((ClusterConnectionImpl)clusterConnection0).getRecords().size());
      Wait.assertEquals(2, () -> ((ClusterConnectionImpl)clusterConnection1).getRecords().size());
      Wait.assertEquals(2, () -> ((ClusterConnectionImpl)clusterConnection2).getRecords().size());


      List<MessageFlowRecord> clusterConnectionRecords0 = new ArrayList<>(((ClusterConnectionImpl) clusterConnection0).getRecords().values());
      List<MessageFlowRecord> clusterConnectionRecords1 = new ArrayList<>(((ClusterConnectionImpl) clusterConnection1).getRecords().values());
      List<MessageFlowRecord> clusterConnectionRecords2 = new ArrayList<>(((ClusterConnectionImpl) clusterConnection2).getRecords().values());

      clusterConnection0.stop();
      clusterConnection1.stop();
      clusterConnection2.stop();

      Assert.assertEquals(0, ((ClusterConnectionImpl)clusterConnection0).getRecords().size());
      Assert.assertEquals(0, ((ClusterConnectionImpl)clusterConnection1).getRecords().size());
      Assert.assertEquals(0, ((ClusterConnectionImpl)clusterConnection2).getRecords().size());

      Wait.assertTrue(() -> clusterConnectionRecords0.stream().noneMatch(messageFlowRecord -> messageFlowRecord.getBridge().isConnected()), 1000);
      Wait.assertTrue(() -> clusterConnectionRecords1.stream().noneMatch(messageFlowRecord -> messageFlowRecord.getBridge().isConnected()), 1000);
      Wait.assertTrue(() -> clusterConnectionRecords2.stream().noneMatch(messageFlowRecord -> messageFlowRecord.getBridge().isConnected()), 1000);

      clusterConnection0.start();
      clusterConnection1.start();
      clusterConnection2.start();

      Wait.assertEquals(2, () -> ((ClusterConnectionImpl)clusterConnection0).getRecords().size());
      Wait.assertEquals(2, () -> ((ClusterConnectionImpl)clusterConnection1).getRecords().size());
      Wait.assertEquals(2, () -> ((ClusterConnectionImpl)clusterConnection2).getRecords().size());

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);
   }

   @Test
   public void testDeleteAddress() throws Exception {
      final String ADDRESS = "queues.testaddress";

      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, ADDRESS, "queue0", null, false);
      createQueue(1, ADDRESS, "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, ADDRESS, 1, 1, true);
      waitForBindings(1, ADDRESS, 1, 1, true);

      waitForBindings(0, ADDRESS, 1, 1, false);
      waitForBindings(1, ADDRESS, 1, 1, false);

      // there should be both a local and a remote binding
      Collection<Binding> bindings = servers[0].getPostOffice().getDirectBindings(SimpleString.toSimpleString(ADDRESS));
      assertEquals(2, bindings.size());

      // the remote binding should point to the SnF queue
      SimpleString snf = null;
      for (Binding binding : bindings) {
         if (binding instanceof RemoteQueueBinding) {
            snf = ((RemoteQueueBinding)binding).getQueue().getName();
         }
      }
      assertNotNull(snf);
      assertNotNull(servers[0].locateQueue(snf));

      servers[0].getActiveMQServerControl().deleteAddress(ADDRESS, true);

      // no bindings should remain but the SnF queue should still be there
      bindings = servers[0].getPostOffice().getDirectBindings(SimpleString.toSimpleString(ADDRESS));
      assertEquals(0, bindings.size());
      assertNotNull(servers[0].locateQueue(snf));
   }

   @Test
   public void testSimple_TwoNodes() throws Exception {
      setupServer(0, false, isNetty());
      setupServer(1, false, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      closeAllConsumers();

   }

   static int loopNumber;

   public void _testLoop() throws Throwable {
      for (int i = 0; i < 10; i++) {
         loopNumber = i;
         log.debug("#test " + i);
         testSimple();
         tearDown();
         setUp();
      }
   }

   @Test
   public void testSimple2() throws Exception {
      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      setupServer(2, true, isNetty());
      setupServer(3, true, isNetty());
      setupServer(4, true, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2, 3, 4);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0, 2, 3, 4);

      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1, 3, 4);

      setupClusterConnection("cluster3", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 3, 0, 1, 2, 4);

      setupClusterConnection("cluster4", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 4, 0, 1, 2, 3);

      startServers(0, 1, 2, 3, 4);

      for (int i = 0; i <= 4; i++) {
         waitForTopology(servers[i], 5);
      }

      log.debug("All the servers have been started already!");

      for (int i = 0; i <= 4; i++) {
         setupSessionFactory(i, isNetty());
      }

      for (int i = 0; i <= 4; i++) {
         createQueue(i, "queues.testaddress", "queue0", null, false);
      }

      for (int i = 0; i <= 4; i++) {
         addConsumer(i, i, "queue0", null);
      }

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 4, 4, false);
      waitForBindings(1, "queues.testaddress", 4, 4, false);
      waitForBindings(2, "queues.testaddress", 4, 4, false);

   }

   @Test
   public void testSimpleRoundRobbin() throws Exception {

      //TODO make this test to crash a node
      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      setupServer(2, true, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, 10, 100, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, 10, 100, isNetty(), 1, 2, 0);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, 10, 100, isNetty(), 2, 0, 1);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      // Need to wait some time so the bridges and
      // connectors had time to connect properly between the nodes

      createQueue(0, "queues.testaddress", "queue0", null, true);
      createQueue(1, "queues.testaddress", "queue0", null, true);
      createQueue(2, "queues.testaddress", "queue0", null, true);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 33, true, null);

      verifyReceiveRoundRobin(33, 0, 1, 2);

      stopServers(2);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 100, true, null);

      verifyReceiveRoundRobin(100, 0, 1);

      sfs[2] = null;
      consumers[2] = null;

      startServers(2);

      setupSessionFactory(2, isNetty());

      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 33, true, null);

      //the last consumer to receive a message was 1 so round robin will continue from consumer 2
      verifyReceiveRoundRobinInSomeOrder(33, 2, 0, 1);
   }

   @Test
   @Ignore("Test not implemented yet")
   public void testSimpleRoundRobbinNoFailure() throws Exception {
      //TODO make this test to crash a node
      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      setupServer(2, true, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, -1, 1000, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, -1, 1000, isNetty(), 1, 2, 0);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, -1, 1000, isNetty(), 2, 0, 1);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, true);
      createQueue(1, "queues.testaddress", "queue0", null, true);
      createQueue(2, "queues.testaddress", "queue0", null, true);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 33, true, null);

      verifyReceiveRoundRobin(33, 0, 1, 2);

      stopServers(2);

      send(0, "queues.testaddress", 100, true, null);

      verifyReceiveRoundRobin(100, 0, 1, -1);

      sfs[2] = null;
      consumers[2] = null;

      startServers(2);

      setupSessionFactory(2, isNetty());

      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      verifyReceiveRoundRobin(100, -1, -1, 2);

   }

}
