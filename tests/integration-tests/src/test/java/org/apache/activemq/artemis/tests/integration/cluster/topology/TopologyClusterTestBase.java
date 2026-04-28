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
package org.apache.activemq.artemis.tests.integration.cluster.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class TopologyClusterTestBase extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final class LatchListener implements ClusterTopologyListener {

      private final CountDownLatch upLatch;
      private final List<String> nodes;
      private final CountDownLatch downLatch;

      // we need a separate list of the nodes we've seen go up that we don't remove IDs from
      // because stale UDP messages can mess up tests once nodes start going down
      private final List<String> seenUp = new ArrayList<>();

      private LatchListener(CountDownLatch upLatch, List<String> nodes, CountDownLatch downLatch) {
         this.upLatch = upLatch;
         this.nodes = nodes;
         this.downLatch = downLatch;
      }

      @Override
      public synchronized void nodeUP(TopologyMember topologyMember, boolean last) {
         final String nodeID = topologyMember.getNodeId();

         if (!seenUp.contains(nodeID)) {
            nodes.add(nodeID);
            seenUp.add(nodeID);
            upLatch.countDown();
         }
      }

      @Override
      public synchronized void nodeDown(final long uniqueEventID, String nodeID) {
         if (nodes.contains(nodeID)) {
            nodes.remove(nodeID);
            downLatch.countDown();
         }
      }
   }

   protected abstract ServerLocator createHAServerLocator();

   protected abstract void setupServers() throws Exception;

   protected abstract void setupCluster() throws Exception;

   protected abstract boolean isNetty() throws Exception;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();

      setupCluster();
   }

   /**
    * Check that the actual list of received nodeIDs correspond to the expected order of nodes
    */
   protected void checkOrder(int[] expected, String[] nodeIDs, List<String> actual) {
      assertEquals(expected.length, actual.size());
      for (int i = 0; i < expected.length; i++) {
         assertEquals(nodeIDs[expected[i]], actual.get(i), "did not receive expected nodeID at " + i);
      }
   }

   protected void checkContains(int[] expected, String[] nodeIDs, List<String> actual) {
      long start = System.currentTimeMillis();
      do {
         if (expected.length != actual.size()) {
            continue;
         }
         boolean ok = true;
         for (int element : expected) {
            ok = (ok && actual.contains(nodeIDs[element]));
         }
         if (ok) {
            return;
         }
      }
      while (System.currentTimeMillis() - start < 5000);
      fail("did not contain all expected node ID: " + actual);
   }

   protected String[] getNodeIDs(int... nodes) {
      String[] nodeIDs = new String[nodes.length];
      for (int i = 0; i < nodes.length; i++) {
         nodeIDs[i] = servers[i].getNodeID().toString();
      }
      return nodeIDs;
   }

   protected ClientSession checkSessionOrReconnect(ClientSession session, ServerLocator locator) throws Exception {
      try {
         String rand = RandomUtil.randomUUIDString();
         session.createQueue(QueueConfiguration.of(rand));
         session.deleteQueue(rand);
         return session;
      } catch (ActiveMQObjectClosedException oce) {
         ClientSessionFactory sf = createSessionFactory(locator);
         return sf.createSession();
      } catch (ActiveMQUnBlockedException obe) {
         ClientSessionFactory sf = createSessionFactory(locator);
         return sf.createSession();
      }
   }

   protected void waitForClusterConnections(final int node, final int expected) throws Exception {
      ActiveMQServer server = servers[node];

      if (server == null) {
         throw new IllegalArgumentException("No server at " + node);
      }

      ClusterManager clusterManager = server.getClusterManager();

      long start = System.currentTimeMillis();

      int nodesCount;
      do {
         nodesCount = 0;

         for (ClusterConnection clusterConn : clusterManager.getClusterConnections()) {
            Map<String, String> nodes = clusterConn.getNodes();
            for (String id : nodes.keySet()) {
               if (clusterConn.isNodeActive(id)) {
                  nodesCount++;
               }
            }
         }

         if (nodesCount == expected) {
            return;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < ActiveMQTestBase.WAIT_TIMEOUT);

      logger.error(clusterDescription(servers[node]));
      assertEquals(expected, nodesCount, "Timed out waiting for cluster connections for server " + node);
   }

   @Test
   public void testReceiveNotificationsWhenOtherNodesAreStartedAndStopped() throws Throwable {
      startServers(0);

      ServerLocator locator = createHAServerLocator();

      locator.getTopology().setOwner("testReceive");

      final List<String> nodes = Collections.synchronizedList(new ArrayList<>());
      final CountDownLatch upLatch = new CountDownLatch(5);
      final CountDownLatch downLatch = new CountDownLatch(4);

      locator.addClusterTopologyListener(new LatchListener(upLatch, nodes, downLatch));

      ClientSessionFactory sf = createSessionFactory(locator);

      startServers(1, 4, 3, 2);
      String[] nodeIDs = getNodeIDs(0, 1, 2, 3, 4);

      assertTrue(upLatch.await(10, SECONDS), "Was not notified that all servers are UP");
      checkContains(new int[]{0, 1, 4, 3, 2}, nodeIDs, nodes);

      waitForClusterConnections(0, 4);
      waitForClusterConnections(1, 4);
      waitForClusterConnections(2, 4);
      waitForClusterConnections(3, 4);
      waitForClusterConnections(4, 4);

      stopServers(2, 3, 1, 4);

      assertTrue(downLatch.await(10, SECONDS), "Was not notified that all servers are DOWN");
      checkContains(new int[]{0}, nodeIDs, nodes);

      sf.close();

      locator.close();

      stopServers(0);

   }

   @Test
   public void testReceiveNotifications() throws Throwable {
      startServers(0, 1, 2, 3, 4);
      String[] nodeIDs = getNodeIDs(0, 1, 2, 3, 4);

      ServerLocator locator = createHAServerLocator();

      waitForClusterConnections(0, 4);
      waitForClusterConnections(1, 4);
      waitForClusterConnections(2, 4);
      waitForClusterConnections(3, 4);
      waitForClusterConnections(4, 4);

      final List<String> nodes = Collections.synchronizedList(new ArrayList<>());
      final CountDownLatch upLatch = new CountDownLatch(5);
      final CountDownLatch downLatch = new CountDownLatch(4);

      locator.addClusterTopologyListener(new LatchListener(upLatch, nodes, downLatch));

      ClientSessionFactory sf = createSessionFactory(locator);

      assertTrue(upLatch.await(10, SECONDS), "Was not notified that all servers are UP");
      checkContains(new int[]{0, 1, 2, 3, 4}, nodeIDs, nodes);

      ClientSession session = sf.createSession();

      stopServers(0);
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1, 2, 3, 4}, nodeIDs, nodes);

      stopServers(2);
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1, 3, 4}, nodeIDs, nodes);

      stopServers(4);
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1, 3}, nodeIDs, nodes);

      stopServers(3);
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1}, nodeIDs, nodes);

      stopServers(1);

      assertTrue(downLatch.await(10, SECONDS), "Was not notified that all servers are DOWN");
      checkContains(new int[]{}, nodeIDs, nodes);

      sf.close();
   }

   @Test
   public void testStopNodes() throws Throwable {
      startServers(0, 1, 2, 3, 4);
      String[] nodeIDs = getNodeIDs(0, 1, 2, 3, 4);

      ServerLocator locator = createHAServerLocator();

      waitForClusterConnections(0, 4);
      waitForClusterConnections(1, 4);
      waitForClusterConnections(2, 4);
      waitForClusterConnections(3, 4);
      waitForClusterConnections(4, 4);

      final List<String> nodes = Collections.synchronizedList(new ArrayList<>());
      final CountDownLatch upLatch = new CountDownLatch(5);

      locator.addClusterTopologyListener(new LatchListener(upLatch, nodes, new CountDownLatch(0)));
      ClientSessionFactory sf = createSessionFactory(locator);

      assertTrue(upLatch.await(10, SECONDS), "Was not notified that all servers are UP");
      checkContains(new int[]{0, 1, 2, 3, 4}, nodeIDs, nodes);

      ClientSession session = sf.createSession();

      stopServers(0);
      assertFalse(servers[0].isStarted());
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1, 2, 3, 4}, nodeIDs, nodes);

      stopServers(2);
      assertFalse(servers[2].isStarted());
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1, 3, 4}, nodeIDs, nodes);

      stopServers(4);
      assertFalse(servers[4].isStarted());
      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1, 3}, nodeIDs, nodes);

      stopServers(3);
      assertFalse(servers[3].isStarted());

      session = checkSessionOrReconnect(session, locator);
      checkContains(new int[]{1}, nodeIDs, nodes);

      stopServers(1);
      assertFalse(servers[1].isStarted());
      try {
         session = checkSessionOrReconnect(session, locator);
         fail();
      } catch (ActiveMQException expected) {
         assertEquals(ActiveMQExceptionType.NOT_CONNECTED, expected.getType());
      }
   }

   @Test
   public void testWrongPasswordTriggersClusterConnectionStop() throws Exception {
      final String user = "myUser";
      final String password = "myPassword";
      final String role = "myRole";

      for (ActiveMQServer s : servers) {
         if (s != null) {
            s.getConfiguration().setSecurityEnabled(true);
         }
      }
      Configuration config = servers[4].getConfiguration();
      assertEquals(ActiveMQTestBase.CLUSTER_PASSWORD, config.getClusterPassword());
      config.setClusterPassword(config.getClusterPassword() + "-1-2-3-");
      final String address = "foo1235";
      ((ActiveMQJAASSecurityManager)servers[0].getSecurityManager()).getConfiguration().addUser(user, password);
      ((ActiveMQJAASSecurityManager)servers[0].getSecurityManager()).getConfiguration().addRole(user, role);
      servers[0].getSecurityRepository().addMatch(address, Set.of(new Role(role, true, true, true, false, false, false, false, false, true, false, false, false)));
      startServers(0, 4);
      assertTrue(Wait.waitFor(() -> !servers[4].getClusterManager().isStarted() || !servers[0].getClusterManager().isStarted(), 5000), "one or the other cluster managers should stop");
      ServerLocator locator = createNonHALocator(isNetty());
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(user, password, false, true, true, false, 1);
      session.createQueue(QueueConfiguration.of(address));
      ClientProducer producer = session.createProducer(address);
      sendMessages(session, producer, 100);
      ClientConsumer consumer = session.createConsumer(address);
      session.start();
      receiveMessages(consumer, 0, 100, true);
   }

   @Test
   public void testMultipleClientSessionFactories() throws Throwable {
      startServers(0, 1, 2, 3, 4);
      String[] nodeIDs = getNodeIDs(0, 1, 2, 3, 4);

      ServerLocator locator = createHAServerLocator();

      waitForClusterConnections(0, 4);
      waitForClusterConnections(1, 4);
      waitForClusterConnections(2, 4);
      waitForClusterConnections(3, 4);
      waitForClusterConnections(4, 4);

      final List<String> nodes = Collections.synchronizedList(new ArrayList<>());
      final CountDownLatch upLatch = new CountDownLatch(5);
      final CountDownLatch downLatch = new CountDownLatch(4);

      locator.addClusterTopologyListener(new LatchListener(upLatch, nodes, downLatch));

      ClientSessionFactory[] sfs = new ClientSessionFactory[]{locator.createSessionFactory(), locator.createSessionFactory(), locator.createSessionFactory(), locator.createSessionFactory(), locator.createSessionFactory()};
      assertTrue(upLatch.await(10, SECONDS), "Was not notified that all servers are UP");
      checkContains(new int[]{0, 1, 2, 3, 4}, nodeIDs, nodes);

      // we can't close all of the servers, we need to leave one up to notify us
      stopServers(4, 2, 3, 1);

      boolean ok = downLatch.await(10, SECONDS);
      if (!ok) {
         logger.warn("TopologyClusterTestBase.testMultipleClientSessionFactories will fail");
      }
      assertTrue(ok, "Was not notified that all servers are Down");
      checkContains(new int[]{0}, nodeIDs, nodes);

      for (ClientSessionFactory sf : sfs) {
         sf.close();
      }

      locator.close();

      stopServers(0);
   }
}
