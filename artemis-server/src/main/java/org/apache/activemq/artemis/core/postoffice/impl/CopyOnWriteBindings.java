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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;

/**
 * This is a copy-on-write map of {@link Binding} along with the last index set.
 */
final class CopyOnWriteBindings {

   public interface BindingIndex {

      /**
       * Cannot return a negative value and returns {@code 0} if uninitialized.
       */
      int getIndex();

      /**
       * Cannot set a negative value.
       */
      void setIndex(int v);
   }

   private static final class BindingsAndPosition extends AtomicReference<Binding[]> implements BindingIndex {

      private static final AtomicIntegerFieldUpdater<BindingsAndPosition> NEXT_POSITION_UPDATER = AtomicIntegerFieldUpdater.newUpdater(BindingsAndPosition.class, "nextPosition");

      public volatile int nextPosition;

      BindingsAndPosition(Binding[] bindings) {
         super(bindings);
         NEXT_POSITION_UPDATER.lazySet(this, 0);
      }

      @Override
      public int getIndex() {
         return nextPosition;
      }

      @Override
      public void setIndex(int v) {
         if (v < 0) {
            throw new IllegalArgumentException("cannot set a negative position");
         }
         NEXT_POSITION_UPDATER.lazySet(this, v);
      }
   }

   private final ConcurrentMap<SimpleString, BindingsAndPosition> map;

   CopyOnWriteBindings() {
      map = new ConcurrentHashMap<>();
   }

   /**
    * Add the specified {@code binding}, if not present.
    */
   public void addBindingIfAbsent(Binding binding) {
      Objects.requireNonNull(binding);
      final SimpleString routingName = binding.getRoutingName();
      Objects.requireNonNull(routingName);
      BindingsAndPosition bindings;
      do {
         bindings = map.get(routingName);
         if (bindings == null || bindings.get() == TOMBSTONE_BINDINGS) {
            final BindingsAndPosition newBindings = new BindingsAndPosition(new Binding[]{binding});
            bindings = map.compute(routingName, (ignored, bindingsAndPosition) -> {
               if (bindingsAndPosition == null || bindingsAndPosition.get() == TOMBSTONE_BINDINGS) {
                  return newBindings;
               }
               return bindingsAndPosition;
            });
            assert bindings != null;
            if (bindings == newBindings) {
               return;
            }
         }
      }
      while (!addBindingIfAbsent(bindings, binding));
   }

   /**
    * Remove the specified {@code binding}, if present.
    */
   public void removeBinding(Binding binding) {
      Objects.requireNonNull(binding);
      final SimpleString routingName = binding.getRoutingName();
      Objects.requireNonNull(routingName);
      final BindingsAndPosition bindings = map.get(routingName);
      if (bindings == null) {
         return;
      }
      final Binding[] newBindings = removeBindingIfPresent(bindings, binding);
      if (newBindings == TOMBSTONE_BINDINGS) {
         // GC attempt
         map.computeIfPresent(routingName, (bindingsRoutingName, existingBindings) -> {
            if (existingBindings.get() == TOMBSTONE_BINDINGS) {
               return null;
            }
            return existingBindings;
         });
      }
   }

   /**
    * Returns a snapshot of the bindings, if present and a "lazy" binding index, otherwise {@code null}.
    * <p>
    * There is no strong commitment on preserving the index value if the related bindings are concurrently modified or
    * the index itself is concurrently modified.
    */
   public Pair<Binding[], BindingIndex> getBindings(SimpleString routingName) {
      Objects.requireNonNull(routingName);
      BindingsAndPosition bindings = map.get(routingName);
      if (bindings == null) {
         return null;
      }
      final Binding[] bindingsSnapshot = bindings.get();
      if (bindingsSnapshot == TOMBSTONE_BINDINGS) {
         return null;
      }
      assert bindingsSnapshot != null && bindingsSnapshot.length > 0;
      return new Pair<>(bindingsSnapshot, bindings);
   }

   @FunctionalInterface
   public interface BindingsConsumer<T extends Throwable> {

      /**
       * {@code bindings} cannot be {@code null} or empty. {@code nextPosition} cannot be null.
       */
      void accept(Binding[] bindings, BindingIndex nextPosition) throws T;
   }

   /**
    * Iterates through the bindings and its related indexes.
    */
   public <T extends Throwable> void forEachBindings(BindingsConsumer<T> bindingsConsumer) throws T {
      Objects.requireNonNull(bindingsConsumer);
      if (map.isEmpty()) {
         return;
      }
      for (BindingsAndPosition bindingsAndPosition : map.values()) {
         final Binding[] bindings = bindingsAndPosition.get();
         if (bindings == TOMBSTONE_BINDINGS) {
            continue;
         }
         assert bindings != null && bindings.length > 0;
         bindingsConsumer.accept(bindings, bindingsAndPosition);
      }
   }

   @FunctionalInterface
   public interface RoutingNameBindingsConsumer<T extends Throwable> {

      /**
       * @param routingName  cannot be {@code null}
       * @param bindings     cannot be {@code null} or empty
       * @param nextPosition cannot be null
       */
      void accept(SimpleString routingName, Binding[] bindings, BindingIndex nextPosition) throws T;
   }

   /**
    * Iterates through the bindings and its related indexes.
    */
   public <T extends Throwable> void forEach(RoutingNameBindingsConsumer<T> bindingsConsumer) throws T {
      Objects.requireNonNull(bindingsConsumer);
      if (map.isEmpty()) {
         return;
      }
      for (Map.Entry<SimpleString, BindingsAndPosition> entry : map.entrySet()) {
         final BindingsAndPosition value = entry.getValue();
         final Binding[] bindings = value.get();
         if (bindings == TOMBSTONE_BINDINGS) {
            continue;
         }
         assert bindings != null && bindings.length > 0;
         bindingsConsumer.accept(entry.getKey(), bindings, value);
      }
   }

   public boolean isEmpty() {
      return map.isEmpty();
   }

   public Map<SimpleString, List<Binding>> copyAsMap() {
      if (map.isEmpty()) {
         return Collections.emptyMap();
      }
      final HashMap<SimpleString, List<Binding>> copy = new HashMap<>(map.size());
      map.forEach((routingName, bindings) -> {
         final Binding[] bindingArray = bindings.get();
         if (bindingArray == TOMBSTONE_BINDINGS) {
            return;
         }
         copy.put(routingName, Arrays.asList(bindingArray));
      });
      return copy;
   }

   private static final Binding[] TOMBSTONE_BINDINGS = new Binding[0];

   private static int indexOfBinding(final Binding[] bindings, final Binding toFind) {
      for (int i = 0, size = bindings.length; i < size; i++) {
         final Binding binding = bindings[i];
         if (binding.equals(toFind)) {
            return i;
         }
      }
      return -1;
   }

   /**
    * Remove the binding if present and returns the new bindings, {@code null} otherwise.
    */
   private static Binding[] removeBindingIfPresent(final AtomicReference<Binding[]> bindings,
                                                   final Binding bindingToRemove) {
      Objects.requireNonNull(bindings);
      Objects.requireNonNull(bindingToRemove);
      Binding[] oldBindings;
      Binding[] newBindings;
      do {
         oldBindings = bindings.get();
         // no need to check vs TOMBSTONE_BINDINGS, because found === -1;
         final int found = indexOfBinding(oldBindings, bindingToRemove);
         if (found == -1) {
            return null;
         }
         final int oldBindingsCount = oldBindings.length;
         if (oldBindingsCount == 1) {
            newBindings = TOMBSTONE_BINDINGS;
         } else {
            final int newBindingsCount = oldBindingsCount - 1;
            newBindings = new Binding[newBindingsCount];
            System.arraycopy(oldBindings, 0, newBindings, 0, found);
            final int remaining = newBindingsCount - found;
            if (remaining > 0) {
               System.arraycopy(oldBindings, found + 1, newBindings, found, remaining);
            }
         }
      }
      while (!bindings.compareAndSet(oldBindings, newBindings));
      return newBindings;
   }

   /**
    * {@return {@code true} if the given binding has been added or already present, {@code false} if bindings are going
    * to be garbage-collected}
    */
   private static boolean addBindingIfAbsent(final AtomicReference<Binding[]> bindings, final Binding newBinding) {
      Objects.requireNonNull(bindings);
      Objects.requireNonNull(newBinding);
      Binding[] oldBindings;
      Binding[] newBindings;
      do {
         oldBindings = bindings.get();
         if (oldBindings == TOMBSTONE_BINDINGS) {
            return false;
         }
         if (indexOfBinding(oldBindings, newBinding) >= 0) {
            return true;
         }
         final int oldLength = oldBindings.length;
         newBindings = Arrays.copyOf(oldBindings, oldLength + 1);
         assert newBindings[oldLength] == null;
         newBindings[oldLength] = newBinding;
      }
      while (!bindings.compareAndSet(oldBindings, newBindings));
      return true;
   }
}
