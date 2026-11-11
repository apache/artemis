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

public class ConsistentHashModuloPolicy extends ConsistentHashPolicy {
   public static final String NAME = "CONSISTENT_HASH_MODULO";

   public static final String MODULO = "MODULO";

   int modulo = 0;

   public ConsistentHashModuloPolicy() {
      super(NAME);
   }

   @Override
   public void init(Map<String, String> properties) {
      super.init(properties);
      modulo = Integer.parseInt(properties.get(MODULO));
   }

   @Override
   public String transformKey(String key) {
      if (modulo > 0) {
         return String.valueOf((getHash(key) & Integer.MAX_VALUE) % modulo);
      }

      return key;
   }

   @Override
   public Target selectTarget(List<Target> targets, String key) {
      return null;
   }
}
