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
package org.apache.activemq.artemis.core.journal.impl;

import java.util.Arrays;
import java.util.Objects;

/**
 * Ordered collection of (T, int, int) tuples with positive integers and not null T.
 * <p>
 * This isn't supposed to be a generic collection, but with some effort can became one and could be moved into commons
 * utils.
 */
final class ObjIntIntArrayList<T> {

   private static final Object[] EMPTY_OBJECTS = new Object[0];
   private static final long[] EMPTY_INTS = new long[0];
   private Object[] objects;
   private long[] ints;
   private int size;

   ObjIntIntArrayList(int initialCapacity) {
      objects = initialCapacity == 0 ? EMPTY_OBJECTS : new Object[initialCapacity];
      ints = initialCapacity == 0 ? EMPTY_INTS : new long[initialCapacity];
      size = 0;
   }

   private static long packInts(int a, int b) {
      if (a < 0 || b < 0) {
         throw new IllegalArgumentException("a and b must be >= 0");
      }
      return (((long) a) << 32) | (b & 0xFFFFFFFFL);
   }

   private static int unpackA(long ints) {
      return (int) (ints >> 32);
   }

   private static int unpackB(long ints) {
      return (int) ints;
   }

   private void ensureCapacity() {
      final int currentCapacity = objects.length;
      final int expectedCapacity = size + 1;
      if (expectedCapacity - currentCapacity <= 0) {
         return;
      }
      grow(expectedCapacity, currentCapacity);
   }

   private void grow(int expectedCapacity, int currentCapacity) {
      assert expectedCapacity - currentCapacity > 0;
      int newCapacity = currentCapacity + (currentCapacity >> 1);
      // to cover the 0,1 cases
      if (newCapacity - expectedCapacity < 0) {
         newCapacity = expectedCapacity;
      }
      if (newCapacity - 2147483639 > 0) {
         newCapacity = hugeCapacity(expectedCapacity);
      }
      final Object[] oldObjects = objects;
      final long[] oldInts = ints;
      try {
         final Object[] newObjects = Arrays.copyOf(oldObjects, newCapacity);
         final long[] newInts = Arrays.copyOf(oldInts, newCapacity);
         objects = newObjects;
         ints = newInts;
      } catch (OutOfMemoryError outOfMemoryError) {
         // restore previous ones
         objects = oldObjects;
         ints = oldInts;
         throw outOfMemoryError;
      }
   }

   private static int hugeCapacity(int minCapacity) {
      if (minCapacity < 0) {
         throw new OutOfMemoryError();
      } else {
         return minCapacity > 2147483639 ? 2147483647 : 2147483639;
      }
   }

   public int size() {
      return size;
   }

   public boolean addToIntsIfMatch(int index, T e, int deltaA, int deltaB) {
      Objects.requireNonNull(e, "e must be not null");
      if (deltaA < 0) {
         throw new IllegalArgumentException("deltaA must be >= 0");
      }
      if (deltaB < 0) {
         throw new IllegalArgumentException("deltaB must be >= 0");
      }
      if (index < 0 || index >= size) {
         throw new IndexOutOfBoundsException("index must be >=0 and <" + size);
      }
      final Object elm = objects[index];
      if (!Objects.equals(elm, e)) {
         return false;
      }
      final long packedInts = ints[index];
      final int oldA = unpackA(packedInts);
      final long newA = oldA + deltaA;
      // overflow check
      if (newA < oldA) {
         return false;
      }
      final int oldB = unpackB(packedInts);
      final long newB = oldB + deltaB;
      // overflow check
      if (newB < oldB) {
         return false;
      }
      ints[index] = packInts((int) newA, (int) newB);
      return true;
   }

   public void add(final T e, final int a, int b) {
      Objects.requireNonNull(e, "e must be not null");
      final long packedInts = packInts(a, b);
      ensureCapacity();
      objects[size] = e;
      ints[size] = packedInts;
      size++;
   }

   public void clear() {
      Arrays.fill(objects, 0, size, null);
      size = 0;
   }

   @FunctionalInterface
   public interface ObjIntIntConsumerOneArg<T, A> {

      void accept(T e, int a, int b, A arg);
   }

   public <A> void forEach(ObjIntIntConsumerOneArg<? super T, ? super A> onFile, A arg) {
      for (int i = 0, size = this.size; i < size; i++) {
         final T e = (T) objects[i];
         final long packedInts = ints[i];
         onFile.accept(e, unpackA(packedInts), unpackB(packedInts), arg);
      }
   }
}
