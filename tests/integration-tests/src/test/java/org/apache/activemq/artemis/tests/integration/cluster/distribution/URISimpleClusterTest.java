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
import org.junit.jupiter.api.Test;

/**
 * A SymmetricClusterTest
 * <p>
 * Most of the cases are covered in OneWayTwoNodeClusterTest - we don't duplicate them all here
 */
public class URISimpleClusterTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
   }

   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testBasicRoundRobin() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

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
   }

   protected static String generateURI(int serverID) {
      return "tcp://127.0.0.1:" + (org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + serverID);
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      for (int i = 0; i < 5; i++) {
         servers[i].getConfiguration().addConnectorConfiguration("netty-connector", generateURI(i));
      }

      setupClusterConnection("cluster", "static://(" + generateURI(1) + "," + generateURI(2) + "," + generateURI(3) + "," + generateURI(4) + ")?connectorName=netty-connector;retryInterval=500;messageLoadBalancingType=" + messageLoadBalancingType.toString() + ";maxHops=1;address=queues", 0);
      setupClusterConnection("cluster", "static://(" + generateURI(0) + "," + generateURI(2) + "," + generateURI(3) + "," + generateURI(4) + ")?connectorName=netty-connector;retryInterval=500;messageLoadBalancingType=" + messageLoadBalancingType.toString() + ";maxHops=1;address=queues", 1);
      setupClusterConnection("cluster", "static://(" + generateURI(0) + "," + generateURI(1) + "," + generateURI(3) + "," + generateURI(4) + ")?connectorName=netty-connector;retryInterval=500;messageLoadBalancingType=" + messageLoadBalancingType.toString() + ";maxHops=1;address=queues", 2);
      setupClusterConnection("cluster", "static://(" + generateURI(0) + "," + generateURI(1) + "," + generateURI(2) + "," + generateURI(4) + ")?connectorName=netty-connector;retryInterval=500;messageLoadBalancingType=" + messageLoadBalancingType.toString() + ";maxHops=1;address=queues", 3);
      setupClusterConnection("cluster", "static://(" + generateURI(0) + "," + generateURI(1) + "," + generateURI(2) + "," + generateURI(3) + ")?connectorName=netty-connector;retryInterval=500;messageLoadBalancingType=" + messageLoadBalancingType.toString() + ";maxHops=1;address=queues", 4);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
      setupServer(2, isFileStorage(), isNetty());
      setupServer(3, isFileStorage(), isNetty());
      setupServer(4, isFileStorage(), isNetty());
   }

   protected void startServers() throws Exception {
      startServers(0, 1, 2, 3, 4);
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1, 2, 3, 4);
   }

   @Override
   protected boolean isFileStorage() {
      return false;
   }
}
