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
package org.apache.activemq.artemis.core.server.routing.policies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.core.server.routing.targets.MockTarget;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RoundRobinPolicyTest extends PolicyTestBase {

   @Override
   protected AbstractPolicy createPolicy() {
      return new RoundRobinPolicy();
   }

   @Test
   public void testPolicyWithMultipleTargets() {
      AbstractPolicy policy = createPolicy();
      Target selectedTarget = null;
      Set<Target> selectedTargets = new HashSet<>();
      List<Target> previousTargets = new ArrayList<>();

      List<Target> targets = new ArrayList<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         targets.add(new MockTarget());
      }

      selectedTargets = new HashSet<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         selectedTarget = policy.selectTarget(targets, "test");
         selectedTargets.add(selectedTarget);
         assertFalse(previousTargets.contains(selectedTarget), "Iteration failed: " + i);
         previousTargets.add(selectedTarget);
      }
      assertEquals(MULTIPLE_TARGETS, selectedTargets.size());
   }
}
