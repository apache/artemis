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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A ConcurrentHashSet.
 * <p>
 * Offers same concurrency as ConcurrentHashMap but for a Set
 *
 * @deprecated Use {@link java.util.concurrent.ConcurrentHashMap#newKeySet()} instead.
 * For example, replace:
 * <pre>{@code Set<String> set = new ConcurrentHashSet<>();}</pre>
 * with:
 * <pre>{@code Set<String> set = ConcurrentHashMap.newKeySet();}</pre>
 */
@Deprecated(forRemoval = true)
public class ConcurrentHashSet<E> extends AbstractSet<E> implements ConcurrentSet<E> {

   private final ConcurrentMap<E, Object> theMap;

   private static final Object dummy = new Object();

   public ConcurrentHashSet() {
      theMap = new ConcurrentHashMap<>();
   }

   @Override
   public int size() {
      return theMap.size();
   }

   @Override
   public Iterator<E> iterator() {
      return theMap.keySet().iterator();
   }

   @Override
   public boolean isEmpty() {
      return theMap.isEmpty();
   }

   @Override
   public boolean add(final E o) {
      return theMap.put(o, ConcurrentHashSet.dummy) == null;
   }

   @Override
   public boolean contains(final Object o) {
      return theMap.containsKey(o);
   }

   @Override
   public void clear() {
      theMap.clear();
   }

   @Override
   public boolean remove(final Object o) {
      return theMap.remove(o) == ConcurrentHashSet.dummy;
   }

   @Override
   public boolean addIfAbsent(final E o) {
      Object obj = theMap.putIfAbsent(o, ConcurrentHashSet.dummy);

      return obj == null;
   }

}
