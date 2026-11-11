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
package org.apache.activemq.artemis.core.server.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * This is abstract implementation of MessageGroups that simply wraps the MessageGroup interface around the passed in
 * map.
 */
abstract class MapMessageGroups<C> implements MessageGroups<C> {

   private final Map<SimpleString, C> groups;

   protected MapMessageGroups(Map<SimpleString, C> groups) {
      this.groups = groups;
   }

   @Override
   public void put(SimpleString key, C consumer) {
      groups.put(key, consumer);
   }

   @Override
   public C get(SimpleString key) {
      return groups.get(key);
   }

   @Override
   public C remove(SimpleString key) {
      return groups.remove(key);
   }

   @Override
   public boolean removeIf(Predicate<? super C> filter) {
      return groups.values().removeIf(filter);
   }

   @Override
   public void removeAll() {
      groups.clear();
   }

   @Override
   public int size() {
      return groups.size();
   }

   @Override
   public Map<SimpleString, C> toMap() {
      return new HashMap<>(groups);
   }
}
