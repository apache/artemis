/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.core.PriorityAware;
import org.apache.activemq.artemis.utils.collections.PriorityCollection;
import org.apache.activemq.artemis.utils.collections.UpdatableIterator;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * This class's purpose is to hold the consumers.
 * <p>
 * CopyOnWriteArraySet is used as the underlying collection to the PriorityCollection, as it is concurrent safe, but
 * also lock less for a read path, which is our HOT path. Also it was the underlying collection previously used in
 * QueueImpl, before we abstracted it out to support priority consumers.
 * <p>
 * There can only be one resettable iterable view, A new iterable view is created on modification, this is to keep the
 * read HOT path performent, BUT the iterable view changes only after reset so changes in the underlying collection are
 * only seen after a reset,
 * <p>
 * All other iterators created by iterators() method are not reset-able and are created on delegating iterator().
 *
 * @param <T> The type this class may hold, this is generic as can be anything that extends PriorityAware, but intent is
 *            this is the QueueImpl:ConsumerHolder.
 */
public class QueueConsumersImpl<T extends PriorityAware> implements QueueConsumers<T> {

   private final PriorityCollection<T> consumers = new PriorityCollection<>(CopyOnWriteArraySet::new);
   private final Collection<T> unmodifiableConsumers = Collections.unmodifiableCollection(consumers);
   private final UpdatableIterator<T> iterator = new UpdatableIterator<>(consumers.resettableIterator());

   //-- START :: ResettableIterator Methods
   // As any iterator, these are not thread-safe and should ONLY be called by a single thread at a time.

   @Override
   public boolean hasNext() {
      return iterator.hasNext();
   }

   @Override
   public T next() {
      return iterator.next();
   }

   @Override
   public void repeat() {
      iterator.repeat();
   }

   @Override
   public void reset() {
      iterator.reset();
   }

   //-- END :: ResettableIterator Methods


   @Override
   public boolean add(T t) {
      boolean result = consumers.add(t);
      if (result) {
         iterator.update(consumers.resettableIterator());
      }
      return result;
   }

   @Override
   public void removed(T t) {
      iterator.removed(t);
   }

   @Override
   public boolean remove(T t) {
      boolean result = consumers.remove(t);
      if (result) {
         iterator.removed(t);
         iterator.update(consumers.resettableIterator());
         if (consumers.isEmpty()) {
            reset();
         }
      }
      return result;
   }

   @Override
   public int size() {
      return consumers.size();
   }

   @Override
   public boolean isEmpty() {
      return consumers.isEmpty();
   }

   @Override
   public Stream<T> stream() {
      return unmodifiableConsumers.stream();
   }

   @Override
   public Iterator<T> iterator() {
      return unmodifiableConsumers.iterator();
   }

   @Override
   public void forEach(Consumer<? super T> action) {
      unmodifiableConsumers.forEach(action);
   }

   @Override
   public Spliterator<T> spliterator() {
      return unmodifiableConsumers.spliterator();
   }

   @Override
   public Set<Integer> getPriorites() {
      return consumers.getPriorites();
   }

}
