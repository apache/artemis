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
package org.apache.activemq.artemis.utils.collections;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a Map, but actually doesnt store anything, it is similar in idea to an EmptyMap, but where
 * mutation methods simply do a no op rather than UnsupportedOperationException as with EmptyMap.
 * <p>
 * This is used in QueueImpl when message groups is disabled.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 */
public class NoOpMap<K, V> extends AbstractMap<K, V> {

   private static final Map NO_OP_MAP = new NoOpMap<>();

   @SuppressWarnings("unchecked")
   public static <K, V> Map<K, V> instance() {
      return (Map<K, V>) NO_OP_MAP;
   }

   private NoOpMap() {
   }

   @Override
   public V put(K key, V value) {
      return null;
   }

   @Override
   public int size() {
      return 0;
   }

   @Override
   public boolean isEmpty() {
      return true;
   }

   @Override
   public boolean containsKey(Object key) {
      return false;
   }

   @Override
   public boolean containsValue(Object value) {
      return false;
   }

   @Override
   public V get(Object key) {
      return null;
   }

   @Override
   public Set<K> keySet() {
      return Collections.emptySet();
   }

   @Override
   public Collection<V> values() {
      return Collections.emptySet();
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return Collections.emptySet();
   }

   @Override
   public boolean equals(Object obj) {
      return (obj instanceof Map other) && other.isEmpty();
   }

   @Override
   public int hashCode() {
      return 0;
   }
}