/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.routing.pools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.server.routing.targets.MockTargetFactory;
import org.apache.activemq.artemis.core.server.routing.targets.MockTargetProbe;
import org.apache.activemq.artemis.core.server.routing.targets.TargetFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Stream;

public class DiscoveryPoolTest extends PoolTestBase {

   @Test
   public void testPoolAddingRemovingAllEntries() throws Exception {
      testPoolChangingEntries(5, 10, 10);
   }

   @Test
   public void testPoolAddingRemovingPartialEntries() throws Exception {
      testPoolChangingEntries(5, 10, 5);
   }

   @Test
   public void testPoolAddingRemovingAllEntriesAfterStart() throws Exception {
      testPoolChangingEntries(0, 10, 10);
   }

   @Test
   public void testPoolAddingRemovingPartialEntriesAfterStart() throws Exception {
      testPoolChangingEntries(0, 10, 5);
   }

   private void testPoolChangingEntries(int initialEntries, int addingEntries, int removingEntries) throws Exception {
      MockTargetFactory targetFactory = new MockTargetFactory();
      MockTargetProbe targetProbe = new MockTargetProbe("TEST", true);
      MockDiscoveryService discoveryService = new MockDiscoveryService();

      targetProbe.setChecked(true);

      // Simulate initial entries.
      List<String> initialNodeIDs = new ArrayList<>();
      for (int i = 0; i < initialEntries; i++) {
         initialNodeIDs.add(discoveryService.addEntry().getNodeID());
      }

      Pool pool = createDiscoveryPool(targetFactory, discoveryService);

      pool.addTargetProbe(targetProbe);

      pool.start();

      try {
         targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setConnectable(true));
         targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setReady(true));

         Wait.assertEquals(initialEntries, () -> pool.getTargets().size(), CHECK_TIMEOUT);
         assertEquals(initialEntries, pool.getAllTargets().size());
         assertEquals(initialEntries, targetFactory.getCreatedTargets().size());
         initialNodeIDs.forEach(nodeID -> Assertions.assertTrue(pool.isTargetReady(pool.getTarget(nodeID))));

         // Simulate adding entries.
         List<String> addedNodeIDs = new ArrayList<>();
         for (int i = 0; i < addingEntries; i++) {
            addedNodeIDs.add(discoveryService.addEntry().getNodeID());
         }

         assertEquals(initialEntries, pool.getTargets().size());
         assertEquals(initialEntries + addingEntries, pool.getAllTargets().size());
         assertEquals(initialEntries + addingEntries, targetFactory.getCreatedTargets().size());
         initialNodeIDs.forEach(nodeID -> {
            assertTrue(pool.isTargetReady(pool.getTarget(nodeID)));
            assertTrue(targetProbe.getTargetExecutions(pool.getTarget(nodeID)) > 0);
         });
         addedNodeIDs.forEach(nodeID -> {
            assertFalse(pool.isTargetReady(pool.getTarget(nodeID)));
            assertEquals(0, targetProbe.getTargetExecutions(pool.getTarget(nodeID)));
         });


         targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setConnectable(true));

         assertEquals(initialEntries, pool.getTargets().size());
         assertEquals(initialEntries + addingEntries, pool.getAllTargets().size());
         assertEquals(initialEntries + addingEntries, targetFactory.getCreatedTargets().size());
         initialNodeIDs.forEach(nodeID -> {
            assertTrue(pool.isTargetReady(pool.getTarget(nodeID)));
            assertTrue(targetProbe.getTargetExecutions(pool.getTarget(nodeID)) > 0);
         });
         addedNodeIDs.forEach(nodeID -> {
            assertFalse(pool.isTargetReady(pool.getTarget(nodeID)));
            assertEquals(0, targetProbe.getTargetExecutions(pool.getTarget(nodeID)));
         });

         targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setReady(true));

         Wait.assertEquals(initialEntries + addingEntries, () -> pool.getTargets().size(), CHECK_TIMEOUT);
         assertEquals(initialEntries + addingEntries, pool.getAllTargets().size());
         assertEquals(initialEntries + addingEntries, targetFactory.getCreatedTargets().size());
         Stream.concat(initialNodeIDs.stream(), addedNodeIDs.stream()).forEach(nodeID -> {
            assertTrue(pool.isTargetReady(pool.getTarget(nodeID)));
            assertTrue(targetProbe.getTargetExecutions(pool.getTarget(nodeID)) > 0);
         });

         if (removingEntries > 0) {
            // Simulate removing entries.
            List<String> removingNodeIDs = new ArrayList<>();
            for (int i = 0; i < removingEntries; i++) {
               removingNodeIDs.add(discoveryService.removeEntry(targetFactory.
                  getCreatedTargets().get(i).getNodeID()).getNodeID());
            }

            assertEquals(initialEntries + addingEntries - removingEntries, pool.getTargets().size());
            assertEquals(initialEntries + addingEntries - removingEntries, pool.getAllTargets().size());
            assertEquals(initialEntries + addingEntries, targetFactory.getCreatedTargets().size());
            Stream.concat(initialNodeIDs.stream(), addedNodeIDs.stream()).forEach(nodeID -> {
               if (removingNodeIDs.contains(nodeID)) {
                  assertNull(pool.getTarget(nodeID));
               } else {
                  assertTrue(pool.isTargetReady(pool.getTarget(nodeID)));
                  assertTrue(targetProbe.getTargetExecutions(pool.getTarget(nodeID)) > 0);
               }
            });
         } else {
            assertEquals(initialEntries + addingEntries, pool.getTargets().size());
            assertEquals(initialEntries + addingEntries, pool.getAllTargets().size());
            assertEquals(initialEntries + addingEntries, targetFactory.getCreatedTargets().size());
            Stream.concat(initialNodeIDs.stream(), addedNodeIDs.stream()).forEach(nodeID -> {
               assertTrue(pool.isTargetReady(pool.getTarget(nodeID)));
               assertTrue(targetProbe.getTargetExecutions(pool.getTarget(nodeID)) > 0);
            });
         }
      } finally {
         pool.stop();
      }
   }


   @Override
   protected Pool createPool(TargetFactory targetFactory, int targets) {
      MockDiscoveryService discoveryService = new MockDiscoveryService();

      for (int i = 0; i < targets; i++) {
         discoveryService.addEntry();
      }

      return createDiscoveryPool(targetFactory, discoveryService);
   }

   private DiscoveryPool createDiscoveryPool(TargetFactory targetFactory, DiscoveryService discoveryService) {
      return new DiscoveryPool(targetFactory, new ScheduledThreadPoolExecutor(0), CHECK_PERIOD, discoveryService);
   }
}
