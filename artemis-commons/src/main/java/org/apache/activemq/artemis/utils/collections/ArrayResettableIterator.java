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
package org.apache.activemq.artemis.utils.collections;

import java.util.Collection;

/**
 * Provides an Array Iterator that is able to reset, allowing you to iterate over the full array. It achieves this
 * though by moving end position mark to the the current cursors position, so it round robins, even with reset.
 */
public class ArrayResettableIterator<E> implements ResettableIterator<E> {

   private final Object[] array;
   private int cursor = 0;
   private int endPos = -1;
   private boolean hasNext;

   public ArrayResettableIterator(Object[] array) {
      this.array = array;
      reset();
   }

   public static <E> ResettableIterator<E> iterator(Collection<E> collection) {
      return new ArrayResettableIterator<>(collection.toArray());
   }

   @Override
   public void reset() {
      endPos = cursor;
      hasNext = array.length > 0;
   }

   @Override
   public boolean hasNext() {
      return hasNext;
   }

   @Override
   public E next() {
      if (!hasNext) {
         throw new IllegalStateException();
      }
      @SuppressWarnings("unchecked")
      E result = (E) array[cursor];
      cursor++;
      if (cursor == array.length) {
         cursor = 0;
      }
      if (cursor == endPos) {
         hasNext = false;
      }
      return result;
   }
}