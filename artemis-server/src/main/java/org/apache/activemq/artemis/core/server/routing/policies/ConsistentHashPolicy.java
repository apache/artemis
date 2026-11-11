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

import org.apache.activemq.artemis.core.server.routing.targets.Target;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHashPolicy extends AbstractPolicy {
   public static final String NAME = "CONSISTENT_HASH";

   public ConsistentHashPolicy() {
      super(NAME);
   }

   protected ConsistentHashPolicy(String name) {
      super(name);
   }

   @Override
   public Target selectTarget(List<Target> targets, String key) {
      if (targets.size() > 1) {
         NavigableMap<Integer, Target> consistentTargets = new TreeMap<>();

         for (Target target : targets) {
            consistentTargets.put(getHash(target.getNodeID()), target);
         }

         if (!consistentTargets.isEmpty()) {
            Map.Entry<Integer, Target> consistentEntry = consistentTargets.floorEntry(getHash(key));

            if (consistentEntry == null) {
               consistentEntry = consistentTargets.firstEntry();
            }

            return consistentEntry.getValue();
         }
      } else if (!targets.isEmpty()) {
         return targets.get(0);
      }

      return null;
   }

   protected int getHash(String str) {
      final int FNV_INIT = 0x811c9dc5;
      final int FNV_PRIME = 0x01000193;

      int hash = FNV_INIT;

      for (int i = 0; i < str.length(); i++) {
         hash = (hash ^ str.charAt(i)) * FNV_PRIME;
      }

      return hash;
   }
}
