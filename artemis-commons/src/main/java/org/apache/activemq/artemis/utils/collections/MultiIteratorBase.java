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

import java.util.Iterator;

/**
 * Provides an Abstract Iterator that works over multiple underlying iterators.
 *
 * @param <E> type of the class of the iterator.
 * @param <I> type of the iterator
 */
abstract class MultiIteratorBase<E, I extends Iterator<E>> implements Iterator<E> {

   private final I[] iterators;
   private int index = -1;

   MultiIteratorBase(I[] iterators) {
      this.iterators = iterators;
   }

   @Override
   public boolean hasNext() {
      while (true) {
         if (index != -1) {
            I currentIterator = get(index);
            if (currentIterator.hasNext()) {
               return true;
            }
         }
         int next = index + 1;
         if (next < iterators.length) {
            moveTo(next);
         } else {
            return false;
         }
      }
   }

   @Override
   public E next() {
      while (true) {
         if (index != -1) {
            I currentIterator = get(index);
            if (currentIterator.hasNext()) {
               return currentIterator.next();
            }
         }
         int next = index + 1;
         if (next < iterators.length) {
            moveTo(next);
         } else {
            return null;
         }
      }
   }

   protected void moveTo(int index) {
      this.index = index;
   }

   protected I get(int index) {
      return iterators[index];
   }
}
