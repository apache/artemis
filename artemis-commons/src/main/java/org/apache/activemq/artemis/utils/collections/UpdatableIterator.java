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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;

public class UpdatableIterator<E> implements ResettableIterator<E>, RepeatableIterator<E> {

   private static final AtomicReferenceFieldUpdater<UpdatableIterator, RepeatableIteratorWrapper> changedIteratorFieldUpdater = AtomicReferenceFieldUpdater.newUpdater(UpdatableIterator.class, RepeatableIteratorWrapper.class, "changedIterator");
   private volatile RepeatableIteratorWrapper<E> changedIterator;
   private RepeatableIteratorWrapper<E> currentIterator;

   public UpdatableIterator(ResettableIterator<E> iterator) {
      this.currentIterator = new RepeatableIteratorWrapper<>(iterator);
   }

   /**
    * This can be called by another thread. It sets a new iterator, that will be picked up on the next reset.
    *
    * @param iterator the new iterator to update to.
    */
   public void update(ResettableIterator<E> iterator) {
      changedIteratorFieldUpdater.set(this, new RepeatableIteratorWrapper<>(iterator));
   }

   /*
    * ---- ResettableIterator Methods -----
    * All the below ResettableIterator (including reset) methods MUST be called by the same thread,
    * this is as any other use of Iterator.
    */

   /**
    * When reset is called, then if a new iterator has been provided by another thread via update method, then we switch
    * over to using the new iterator.
    * <p>
    * It is important that on nulling off the changedIterator, we atomically compare and set as the changedIterator
    * could be further updated by another thread whilst we are resetting, the subsequent update simply would be picked
    * up on the next reset.
    */
   @Override
   public void reset() {
      RepeatableIteratorWrapper<E> changedIterator = this.changedIterator;
      if (changedIterator != null) {
         currentIterator = changedIterator;
         changedIteratorFieldUpdater.compareAndSet(this, changedIterator, null);
      }
      currentIterator.reset();
   }

   @Override
   public boolean hasNext() {
      return currentIterator.hasNext();
   }

   @Override
   public E next() {
      return currentIterator.next();
   }

   @Override
   public void remove() {
      currentIterator.remove();
   }

   @Override
   public void forEachRemaining(Consumer<? super E> action) {
      currentIterator.forEachRemaining(action);
   }

   @Override
   public void repeat() {
      currentIterator.repeat();
   }

   @Override
   public void removed(E removed) {
      currentIterator.removed(removed);
   }
}
