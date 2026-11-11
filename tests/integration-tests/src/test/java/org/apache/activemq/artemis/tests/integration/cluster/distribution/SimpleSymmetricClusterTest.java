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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleSymmetricClusterTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());



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
      setupPrimaryServer(3, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(4, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(5, isFileStorage(), HAType.SharedStore, isNetty(), false);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 3, 4, 5);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 4, 3, 5);

      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 5, 3, 4);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 4, 5);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 3, 5);

      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 3, 4);

      startServers(0, 1, 2, 3, 4, 5);

      logger.debug("");
      for (int i = 0; i <= 5; i++) {
         logger.debug(servers[i].describe());
         logger.debug(debugBindings(servers[i], servers[i].getConfiguration().getManagementNotificationAddress().toString()));
      }
      logger.debug("");

      logger.debug("");
      for (int i = 0; i <= 5; i++) {
         logger.debug(servers[i].describe());
         logger.debug(debugBindings(servers[i], servers[i].getConfiguration().getManagementNotificationAddress().toString()));
      }
      logger.debug("");

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
   public void testWildcardFlowControl() throws Exception {
      final int PRODUCER_COUNT = 3000;
      final int ITERATIONS = 4;
      final CountDownLatch consumerLatch = new CountDownLatch(PRODUCER_COUNT * ITERATIONS);
      final CountDownLatch producerLatch = new CountDownLatch(PRODUCER_COUNT * ITERATIONS);

      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      servers[0].getConfiguration().setWildcardRoutingEnabled(true);
      servers[1].getConfiguration().setWildcardRoutingEnabled(true);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      logger.info("Creating " + PRODUCER_COUNT + " multicast addresses on node 1...");
      for (int i = 0; i < PRODUCER_COUNT; i++) {
         createAddressInfo(1, "queues." + i, RoutingType.MULTICAST, -1, false);
      }
      logger.info("Addresses created.");

      createQueue(0, "queues.#", "queue", null, false, RoutingType.MULTICAST);

      logger.info("Creating consumer on node 0");
      addConsumer(0, 0, "queue", null);

      consumers[0].getConsumer().setMessageHandler(message -> {
         logger.debug("Received: " + message);
         consumerLatch.countDown();
      });

      waitForBindings(0, "queues.#", 1, 1, true);

      logger.info("Creating " + PRODUCER_COUNT + " producers...");
      ClientProducer[] producers = new ClientProducer[PRODUCER_COUNT];
      ClientSession[] sessions = new ClientSession[PRODUCER_COUNT];
      for (int i = 0; i < PRODUCER_COUNT; i++) {
         ClientSessionFactory sf = sfs[1];
         sessions[i] = addClientSession(sf.createSession(true, true, 0));
         producers[i] = addClientProducer(sessions[i].createProducer("queues." + i));
      }

      ExecutorService executorService = Executors.newFixedThreadPool(PRODUCER_COUNT);
      runAfter(executorService::shutdownNow);
      for (int i = 0; i < PRODUCER_COUNT; i++) {
         final ClientProducer producer = producers[i];
         final ClientMessage message = sessions[i].createMessage(true);
         executorService.submit(() -> {
            for (int j = 0; j < ITERATIONS; j++) {
               try {
                  producer.send(message);
                  producerLatch.countDown();
                  logger.debug("Sent message");
               } catch (Exception e) {
                  logger.error(e.getMessage(), e);
               }
            }
         });
      }
      producerLatch.await(30, TimeUnit.SECONDS);
      logger.info("Waiting for messages on node 0");
      assertTrue(consumerLatch.await(30, TimeUnit.SECONDS));
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

      assertEquals(0, ((ClusterConnectionImpl)clusterConnection0).getRecords().size());
      assertEquals(0, ((ClusterConnectionImpl)clusterConnection1).getRecords().size());
      assertEquals(0, ((ClusterConnectionImpl)clusterConnection2).getRecords().size());

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
      Collection<Binding> bindings = servers[0].getPostOffice().getDirectBindings(SimpleString.of(ADDRESS));
      assertEquals(2, bindings.size());

      // the remote binding should point to the SnF queue
      SimpleString snf = null;
      for (Binding binding : bindings) {
         if (binding instanceof RemoteQueueBinding remoteQueueBinding) {
            snf = remoteQueueBinding.getQueue().getName();
         }
      }
      assertNotNull(snf);
      assertNotNull(servers[0].locateQueue(snf));

      servers[0].getActiveMQServerControl().deleteAddress(ADDRESS, true);

      // no bindings should remain but the SnF queue should still be there
      bindings = servers[0].getPostOffice().getDirectBindings(SimpleString.of(ADDRESS));
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

   @Test
   public void testInitialDistributionSendToFQQNConsumeFromFQQN() throws Exception {
      testInitialDistribution(true, true);
   }

   @Test
   public void testInitialDistributionSendToFQQNConsumeFromQueue() throws Exception {
      testInitialDistribution(true, false);
   }

   @Test
   public void testInitialDistributionSendToAddressConsumeFromFQQN() throws Exception {
      testInitialDistribution(false, true);
   }

   @Test
   public void testInitialDistributionSendToAddressConsumeFromQueue() throws Exception {
      testInitialDistribution(false, false);
   }

   private void testInitialDistribution(boolean sendToFQQN, boolean consumeFromFQQN) throws Exception {
      CountDownLatch countDownLatch = new CountDownLatch(1);
      final String address = "myAddress";
      final String queue = "myQueue";
      final String fqqn = CompositeAddress.toFullyQualified(address, queue);

      setupServer(0, false, isNetty());
      setupServer(1, false, isNetty());

      setupClusterConnection("cluster0", address, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", address, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, address, queue, null, false);
      createQueue(1, address, queue, null, false);

      waitForBindings(0, address, 1, 0, true);
      waitForBindings(0, address, 1, 0, false);
      waitForBindings(1, address, 1, 0, true);
      waitForBindings(1, address, 1, 0, false);

      addConsumer(0, 0, consumeFromFQQN ? fqqn : queue, null);
      consumers[0].consumer.setMessageHandler((m) -> countDownLatch.countDown());

      waitForBindings(0, address, 1, 1, true);
      waitForBindings(1, address, 1, 1, false);

      send(1, sendToFQQN ? fqqn : address, 1, true, null);

      assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));

      closeAllConsumers();
   }

   @Test
   public void testSimpleSnFManagement() throws Exception {
      final String address = "queues.testaddress";
      final String queue = "queue0";

      setupServer(0, false, isNetty());
      setupServer(1, false, isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, address, queue, null, false);
      createQueue(1, address, queue, null, false);

      addConsumer(0, 0, queue, null);
      addConsumer(1, 1, queue, null);

      waitForBindings(0, address, 1, 1, true);
      waitForBindings(1, address, 1, 1, true);

      waitForBindings(0, address, 1, 1, false);
      waitForBindings(1, address, 1, 1, false);

      SimpleString SnFQueueName = SimpleString.of(
         Arrays.stream(servers[0].getActiveMQServerControl().getQueueNames()).filter(
            queueName -> queueName.contains(servers[0].getInternalNamingPrefix()))
            .findFirst()
            .orElse(null));

      assertNotNull(SnFQueueName);

      QueueControl queueControl = ManagementControlHelper.createQueueControl(SnFQueueName, SnFQueueName, RoutingType.MULTICAST, servers[0].getMBeanServer());

      //check that internal queue can be managed
      queueControl.pause();
      assertTrue(queueControl.isPaused());

      queueControl.resume();
      assertFalse(queueControl.isPaused());

      closeAllConsumers();

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

      logger.debug("All the servers have been started already!");

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
   @Disabled("Test not implemented yet")
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

   @Test
   public void testDivertRedistributedMessage() throws Exception {
      final String queue = "queue0";
      final String divertedQueueName = "divertedQueue";
      final int messageCount = 10;

      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());
      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      servers[0].getConfiguration().addAddressSetting("#", new AddressSettings().setRedistributionDelay(0));
      servers[1].getConfiguration().addAddressSetting("#", new AddressSettings().setRedistributionDelay(0));

      startServers(0, 1);

      servers[0].deployDivert(new DivertConfiguration()
                                 .setName("myDivert")
                                 .setAddress(queue)
                                 .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
                                 .setForwardingAddress(divertedQueueName)
                                 .setExclusive(true));

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, queue, queue, null, true, RoutingType.ANYCAST);
      createQueue(1, queue, queue, null, true, RoutingType.ANYCAST);
      createQueue(0, divertedQueueName, divertedQueueName, null, true, RoutingType.ANYCAST);
      createQueue(1, divertedQueueName, divertedQueueName, null, true, RoutingType.ANYCAST);

      addConsumer(0, 0, queue, null);

      waitForBindings(0, queue, 1, 1, true);
      waitForBindings(1, queue, 1, 1, false);

      send(1, queue, messageCount, true, null);

      Wait.assertEquals((long) messageCount, () -> servers[0].locateQueue(divertedQueueName).getMessageCount(), 2000, 100);

      addConsumer(1, 1, divertedQueueName, null);

      verifyReceiveAll(messageCount, 1);
      closeAllConsumers();
   }

}
