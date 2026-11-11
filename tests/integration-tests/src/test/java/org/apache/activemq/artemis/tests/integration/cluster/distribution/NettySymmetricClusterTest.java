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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.jupiter.api.Test;

public class NettySymmetricClusterTest extends SymmetricClusterTest {

   @Override
   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testConnectionLoadBalancingUsingInitialConnectors() throws Exception {
      final String ADDRESS = "queues.testaddress";
      final String QUEUE = "queue0";
      final String URL = "(tcp://localhost:61616,tcp://localhost:61617)?useTopologyForLoadBalancing=false";
      final int CONNECTION_COUNT = 50;

      setupCluster();

      startServers();

      for (int i = 0; i < 5; i++) {
         setupSessionFactory(i, isNetty());
      }

      for (int i = 0; i < 5; i++) {
         createQueue(i, ADDRESS, QUEUE, null, false);
      }

      for (int i = 0; i < 5; i++) {
         addConsumer(i, i, QUEUE, null);
      }

      for (int i = 0; i < 5; i++) {
         waitForBindings(i, ADDRESS, 1, 1, true);
      }

      for (int i = 0; i < 5; i++) {
         waitForBindings(i, ADDRESS, 4, 4, false);
      }

      int[] baseline = new int[5];
      for (int i = 0; i < 5; i++) {
         baseline[i] = servers[i].getActiveMQServerControl().getConnectionCount();
      }

      ClientSessionFactory[] clientSessionFactories = new ClientSessionFactory[CONNECTION_COUNT];
      ServerLocator locator = ActiveMQClient.createServerLocator(URL);
      for (int i = 0; i < CONNECTION_COUNT; i++) {
         clientSessionFactories[i] = addSessionFactory(locator.createSessionFactory());
      }

      /*
       * Since we are only using the initial connectors to load-balance then all the connections should be on the first
       * 2 nodes.
       * Note: This still uses the load-balancing-policy so this would changed if we used the random one instead of the
       * default round-robin one.
       */
      assertEquals(CONNECTION_COUNT / 2, (servers[0].getActiveMQServerControl().getConnectionCount() - baseline[0]));
      assertEquals(CONNECTION_COUNT / 2, (servers[1].getActiveMQServerControl().getConnectionCount() - baseline[1]));

      for (int i = 0; i < CONNECTION_COUNT; i++) {
         clientSessionFactories[i].close();
      }

      locator.setUseTopologyForLoadBalancing(true);
      for (int i = 0; i < CONNECTION_COUNT; i++) {
         clientSessionFactories[i] = addSessionFactory(locator.createSessionFactory());
      }

      for (int i = 0; i < 5; i++) {
         assertTrue((servers[i].getActiveMQServerControl().getConnectionCount() - baseline[i]) < (CONNECTION_COUNT / 2));
      }
   }
}
