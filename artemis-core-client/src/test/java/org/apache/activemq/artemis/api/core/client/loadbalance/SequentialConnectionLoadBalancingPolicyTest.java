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
package org.apache.activemq.artemis.api.core.client.loadbalance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.junit.jupiter.api.Test;

public class SequentialConnectionLoadBalancingPolicyTest {

   @Test
   public void testSelectSequential() {
      ConnectionLoadBalancingPolicy policy = new SequentialConnectionLoadBalancingPolicy();

      assertEquals(0, policy.select(3));
      assertEquals(1, policy.select(3));
      assertEquals(2, policy.select(3));
      assertEquals(0, policy.select(3));
      assertEquals(1, policy.select(3));
      assertEquals(2, policy.select(3));
      assertEquals(0, policy.select(3));
   }

   @Test
   public void testSelectSingleElement() {
      ConnectionLoadBalancingPolicy policy = new SequentialConnectionLoadBalancingPolicy();

      for (int i = 0; i < 10; i++) {
         assertEquals(0, policy.select(1));
      }
   }

   @Test
   public void testSelectDynamicMaxChange() {
      ConnectionLoadBalancingPolicy policy = new SequentialConnectionLoadBalancingPolicy();

      assertEquals(0, policy.select(5));
      assertEquals(1, policy.select(5));
      assertEquals(2, policy.select(5));

      // Dynamic shrink in cluster topology size (from 5 to 2)
      assertEquals(1, policy.select(2)); // (2 + 1) % 2 = 1
      assertEquals(0, policy.select(2)); // (1 + 1) % 2 = 0
   }

   @Test
   public void testSelectZeroMax() {
      ConnectionLoadBalancingPolicy policy = new SequentialConnectionLoadBalancingPolicy();

      assertEquals(0, policy.select(0));
      assertEquals(0, policy.select(0));

      assertEquals(1, policy.select(3));
      assertEquals(2, policy.select(3));
      assertEquals(0, policy.select(3));
   }

   @Test
   public void testSelectNegativeMax() {
      ConnectionLoadBalancingPolicy policy = new SequentialConnectionLoadBalancingPolicy();

      assertEquals(0, policy.select(-1));
      assertEquals(0, policy.select(-5));

      assertEquals(1, policy.select(3));
   }

   @Test
   public void testServerLocatorConnectorSelection() throws Exception {
      TransportConfiguration tc1 = new TransportConfiguration();
      TransportConfiguration tc2 = new TransportConfiguration();
      TransportConfiguration tc3 = new TransportConfiguration();

      try (ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(tc1, tc2, tc3)) {
         locator.setConnectionLoadBalancingPolicyClassName(SequentialConnectionLoadBalancingPolicy.class.getName());
         locator.setUseTopologyForLoadBalancing(false);

         ServerLocatorInternal internalLocator = (ServerLocatorInternal) locator;
         internalLocator.initialize();

         assertEquals(tc1, internalLocator.selectNextConnectorPair().getA());
         assertEquals(tc2, internalLocator.selectNextConnectorPair().getA());
         assertEquals(tc3, internalLocator.selectNextConnectorPair().getA());
         assertEquals(tc1, internalLocator.selectNextConnectorPair().getA());
      }
   }
}
