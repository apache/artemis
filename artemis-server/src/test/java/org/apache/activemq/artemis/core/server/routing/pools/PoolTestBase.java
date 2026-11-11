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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.server.routing.targets.MockTargetFactory;
import org.apache.activemq.artemis.core.server.routing.targets.MockTargetProbe;
import org.apache.activemq.artemis.core.server.routing.targets.TargetFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public abstract class PoolTestBase {
   public static final int MULTIPLE_TARGETS = 10;

   public static final int CHECK_PERIOD = 100;
   public static final int CHECK_TIMEOUT = 10 * CHECK_PERIOD;


   protected abstract Pool createPool(TargetFactory targetFactory, int targets);


   @Test
   public void testPoolWithNoTargets() throws Exception {
      testPoolTargets(0);
   }

   @Test
   public void testPoolWithSingleTarget() throws Exception {
      testPoolTargets(1);
   }

   @Test
   public void testPoolWithMultipleTargets() throws Exception {
      testPoolTargets(MULTIPLE_TARGETS);
   }

   @Test
   public void testPoolQuorumWithMultipleTargets() throws Exception {
      final int targets = MULTIPLE_TARGETS;
      final int quorumSize = 2;

      assertTrue(targets - quorumSize > 2);

      MockTargetFactory targetFactory = new MockTargetFactory().setConnectable(true).setReady(true);
      Pool pool = createPool(targetFactory, targets);

      pool.setQuorumSize(quorumSize);

      assertEquals(0, pool.getTargets().size());

      pool.start();

      try {
         Wait.assertEquals(targets, () -> pool.getTargets().size(), CHECK_TIMEOUT);

         targetFactory.getCreatedTargets().stream().limit(targets - quorumSize + 1)
            .forEach(mockTarget -> mockTarget.setReady(false));

         Wait.assertEquals(0, () -> pool.getTargets().size(), CHECK_TIMEOUT);

         targetFactory.getCreatedTargets().get(0).setReady(true);

         Wait.assertEquals(quorumSize, () -> pool.getTargets().size(), CHECK_TIMEOUT);

         pool.setQuorumSize(quorumSize + 1);

         Wait.assertEquals(0, () -> pool.getTargets().size(), CHECK_TIMEOUT);

         targetFactory.getCreatedTargets().get(1).setReady(true);

         Wait.assertEquals(quorumSize + 1, () -> pool.getTargets().size(), CHECK_TIMEOUT);
      } finally {
         pool.stop();
      }
   }


   private void testPoolTargets(int targets) throws Exception {
      MockTargetFactory targetFactory = new MockTargetFactory();
      MockTargetProbe targetProbe = new MockTargetProbe("TEST", false);
      Pool pool = createPool(targetFactory, targets);

      pool.addTargetProbe(targetProbe);

      assertEquals(0, pool.getTargets().size());
      assertEquals(0, pool.getAllTargets().size());
      assertEquals(0, targetFactory.getCreatedTargets().size());

      pool.start();

      try {
         assertEquals(0, pool.getTargets().size());
         assertEquals(targets, pool.getAllTargets().size());
         assertEquals(targets, targetFactory.getCreatedTargets().size());
         targetFactory.getCreatedTargets().forEach(mockTarget -> {
            assertFalse(pool.isTargetReady(mockTarget));
            assertEquals(0, targetProbe.getTargetExecutions(mockTarget));
         });

         if (targets > 0) {
            targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setConnectable(true));

            assertEquals(0, pool.getTargets().size());
            assertEquals(targets, pool.getAllTargets().size());
            assertEquals(targets, targetFactory.getCreatedTargets().size());
            targetFactory.getCreatedTargets().forEach(mockTarget -> {
               assertFalse(pool.isTargetReady(mockTarget));
               assertEquals(0, targetProbe.getTargetExecutions(mockTarget));
            });

            targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setReady(true));

            assertEquals(0, pool.getTargets().size());
            assertEquals(targets, pool.getAllTargets().size());
            assertEquals(targets, targetFactory.getCreatedTargets().size());
            targetFactory.getCreatedTargets().forEach(mockTarget -> {
               assertFalse(pool.isTargetReady(mockTarget));
               Wait.assertTrue(() -> targetProbe.getTargetExecutions(mockTarget) > 0, CHECK_TIMEOUT);
            });

            targetProbe.clearTargetExecutions();

            targetProbe.setChecked(true);

            Wait.assertEquals(targets, () -> pool.getTargets().size(), CHECK_TIMEOUT);
            assertEquals(targets, pool.getAllTargets().size());
            assertEquals(targets, targetFactory.getCreatedTargets().size());
            targetFactory.getCreatedTargets().forEach(mockTarget -> {
               assertTrue(pool.isTargetReady(mockTarget));
               assertTrue(targetProbe.getTargetExecutions(mockTarget) > 0);
            });

            targetFactory.getCreatedTargets().forEach(mockTarget -> {
               mockTarget.setConnectable(false);
               try {
                  mockTarget.disconnect();
               } catch (Exception ignore) {
               }
            });

            Wait.assertEquals(0, () -> pool.getTargets().size(), CHECK_TIMEOUT);
            assertEquals(targets, pool.getAllTargets().size());
            assertEquals(targets, targetFactory.getCreatedTargets().size());

            targetProbe.clearTargetExecutions();

            targetFactory.getCreatedTargets().forEach(mockTarget -> mockTarget.setConnectable(true));

            Wait.assertEquals(targets, () -> pool.getTargets().size(), CHECK_TIMEOUT);
            assertEquals(targets, pool.getAllTargets().size());
            assertEquals(targets, targetFactory.getCreatedTargets().size());
            targetFactory.getCreatedTargets().forEach(mockTarget -> {
               Wait.assertTrue(() -> pool.isTargetReady(mockTarget), CHECK_TIMEOUT);
               Wait.assertTrue(() -> targetProbe.getTargetExecutions(mockTarget) > 0, CHECK_TIMEOUT);
            });

            targetProbe.clearTargetExecutions();

            targetProbe.setChecked(false);

            Wait.assertEquals(0, () -> pool.getTargets().size(), CHECK_TIMEOUT);
            assertEquals(targets, pool.getAllTargets().size());
            assertEquals(targets, targetFactory.getCreatedTargets().size());
            targetFactory.getCreatedTargets().forEach(mockTarget -> {
               Wait.assertTrue(() -> !pool.isTargetReady(mockTarget), CHECK_TIMEOUT);
               Wait.assertTrue(() -> targetProbe.getTargetExecutions(mockTarget) > 0, CHECK_TIMEOUT);
            });
         }
      } finally {
         pool.stop();
      }
   }
}
