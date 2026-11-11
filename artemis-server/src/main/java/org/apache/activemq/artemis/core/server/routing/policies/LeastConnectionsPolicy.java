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
import org.apache.activemq.artemis.core.server.routing.targets.TargetProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class LeastConnectionsPolicy extends RoundRobinPolicy {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String NAME = "LEAST_CONNECTIONS";

   public static final String UPDATE_CONNECTION_COUNT_PROBE_NAME = "UPDATE_CONNECTION_COUNT_PROBE";

   public static final String CONNECTION_COUNT_THRESHOLD = "CONNECTION_COUNT_THRESHOLD";


   private final Map<Target, Integer> connectionCountCache = new ConcurrentHashMap<>();


   private int connectionCountThreshold = 0;


   private final TargetProbe targetProbe = new TargetProbe(UPDATE_CONNECTION_COUNT_PROBE_NAME) {
      @Override
      public boolean check(Target target) {
         try {
            Integer connectionCount = target.getAttribute("broker", "ConnectionCount", Integer.class, 3000);

            if (connectionCount < connectionCountThreshold) {
               logger.debug("Updating the connection count to 0/{} for the target {}", connectionCount, target);

               connectionCount = 0;
            } else {
               logger.debug("Updating the connection count to {} for the target {}", connectionCount, target);
            }

            connectionCountCache.put(target, connectionCount);

            return true;
         } catch (Exception e) {
            logger.warn("Error on updating the connectionCount for the target {}", target, e);

            return false;
         }
      }
   };

   @Override
   public TargetProbe getTargetProbe() {
      return targetProbe;
   }

   public LeastConnectionsPolicy() {
      super(NAME);
   }

   @Override
   public void init(Map<String, String> properties) {
      super.init(properties);

      if (properties != null) {
         if (properties.containsKey(CONNECTION_COUNT_THRESHOLD)) {
            connectionCountThreshold = Integer.parseInt(properties.get(CONNECTION_COUNT_THRESHOLD));
         }
      }
   }

   @Override
   public Target selectTarget(List<Target> targets, String key) {
      if (targets.size() > 1) {
         NavigableMap<Integer, List<Target>> sortedTargets = new TreeMap<>();

         for (Target target : targets) {
            Integer connectionCount = connectionCountCache.get(target);

            if (connectionCount == null) {
               connectionCount = Integer.MAX_VALUE;
            }

            List<Target> leastTargets = sortedTargets.get(connectionCount);

            if (leastTargets == null) {
               leastTargets = new ArrayList<>();
               sortedTargets.put(connectionCount, leastTargets);
            }

            leastTargets.add(target);
         }

         logger.debug("LeastConnectionsPolicy.sortedTargets: {}", sortedTargets);

         List<Target> selectedTargets = sortedTargets.firstEntry().getValue();

         if (selectedTargets.size() > 1) {
            return super.selectTarget(selectedTargets, key);
         } else {
            return selectedTargets.get(0);
         }
      } else if (!targets.isEmpty()) {
         return targets.get(0);
      }

      return null;
   }
}
