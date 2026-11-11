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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.server.routing.targets.MockTarget;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LeastConnectionsPolicyTest extends PolicyTestBase {

   @Override
   protected AbstractPolicy createPolicy() {
      return new LeastConnectionsPolicy();
   }

   @Test
   public void testPolicyWithMultipleTargets() {
      AbstractPolicy policy = createPolicy();
      Target selectedTarget = null;
      Set<Target> selectedTargets;


      List<Target> targets = new ArrayList<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         targets.add(new MockTarget().setConnected(true).setReady(true));
      }


      selectedTargets = new HashSet<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         selectedTarget = policy.selectTarget(targets, "test");
         selectedTargets.add(selectedTarget);
      }
      assertEquals(MULTIPLE_TARGETS, selectedTargets.size());


      targets.forEach(target -> {
         ((MockTarget)target).setAttributeValue("broker", "ConnectionCount", 3);
         policy.getTargetProbe().check(target);
      });

      selectedTargets = new HashSet<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         selectedTarget = policy.selectTarget(targets, "test");
         selectedTargets.add(selectedTarget);
      }
      assertEquals(MULTIPLE_TARGETS, selectedTargets.size());


      ((MockTarget)targets.get(0)).setAttributeValue("broker", "ConnectionCount", 2);
      targets.forEach(target -> policy.getTargetProbe().check(target));

      selectedTargets = new HashSet<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         selectedTarget = policy.selectTarget(targets, "test");
         selectedTargets.add(selectedTarget);
      }
      assertEquals(1, selectedTargets.size());
      assertTrue(selectedTargets.contains(targets.get(0)));


      ((MockTarget)targets.get(1)).setAttributeValue("broker", "ConnectionCount", 1);
      ((MockTarget)targets.get(2)).setAttributeValue("broker", "ConnectionCount", 1);
      targets.forEach(target -> policy.getTargetProbe().check(target));

      selectedTargets = new HashSet<>();
      for (int i = 0; i < MULTIPLE_TARGETS; i++) {
         selectedTarget = policy.selectTarget(targets, "test");
         selectedTargets.add(selectedTarget);
      }
      assertEquals(2, selectedTargets.size());
      assertTrue(selectedTargets.contains(targets.get(1)));
      assertTrue(selectedTargets.contains(targets.get(2)));
   }
}
