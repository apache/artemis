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

import static io.netty.util.internal.MathUtil.findNextPositivePowerOfTwo;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A hash set implementation of {@literal Set<Long>} that uses open addressing values. To minimize the memory footprint,
 * this class uses open addressing rather than chaining. Collisions are resolved using linear probing. Deletions
 * implement compaction, so cost of remove can approach O(N) for full maps, which makes a small loadFactor recommended.
 * <p>
 * The implementation is based on <a
 * href=https://github.com/real-logic/agrona/blob/master/agrona/src/main/java/org/agrona/collections/IntHashSet.java>Agrona
 * IntHashSet</a> but uses long primitive keys and a different {@link #MISSING_VALUE} to account for
 * {@link Long#hashCode} being 0 for -1.
 */
public class LongHashSet extends AbstractSet<Long> implements Serializable {

   /**
    * The initial capacity used when none is specified in the constructor.
    */
   public static final int DEFAULT_INITIAL_CAPACITY = 8;
   public static final float DEFAULT_LOAD_FACTOR = 0.5f;
   static final long MISSING_VALUE = -2;

   private boolean containsMissingValue;
   private final float loadFactor;
   private int resizeThreshold;
   // NB: excludes missing value
   private int sizeOfArrayValues;

   private long[] values;

   /**
    * Construct a hash set with {@link #DEFAULT_INITIAL_CAPACITY} and {@link #DEFAULT_LOAD_FACTOR}.
    */
   public LongHashSet() {
      this(DEFAULT_INITIAL_CAPACITY);
   }

   /**
    * Construct a hash set with a proposed capacity and {@link #DEFAULT_LOAD_FACTOR}.
    *
    * @param proposedCapacity for the initial capacity of the set.
    */
   public LongHashSet(final int proposedCapacity) {
      this(proposedCapacity, DEFAULT_LOAD_FACTOR);
   }

   private static int hashIndex(long value, int mask) {
      return hashCode(value) & mask;
   }

   private static int hashCode(long value) {
      long hash = value * 31;
      hash = (int) hash ^ (int) (hash >>> 32);
      return (int) hash;
   }

   /**
    * Construct a hash set with a proposed initial capacity and load factor.
    *
    * @param proposedCapacity for the initial capacity of the set.
    * @param loadFactor       to be used for resizing.
    */
   public LongHashSet(final int proposedCapacity, final float loadFactor) {
      if (loadFactor < 0.1f || loadFactor > 0.9f) {
         throw new IllegalArgumentException("load factor must be in the range of 0.1 to 0.9: " + loadFactor);
      }
      this.loadFactor = loadFactor;
      sizeOfArrayValues = 0;
      final int capacity = findNextPositivePowerOfTwo(Math.max(DEFAULT_INITIAL_CAPACITY, proposedCapacity));
      resizeThreshold = (int) (capacity * loadFactor);
      values = new long[capacity];
      Arrays.fill(values, MISSING_VALUE);
   }

   /**
    * Get the load factor beyond which the set will increase size.
    *
    * @return load factor for when the set should increase size
    */
   public float loadFactor() {
      return loadFactor;
   }

   /**
    * Get the total capacity for the set to which the load factor with be a fraction of.
    *
    * @return the total capacity for the set
    */
   public int capacity() {
      return values.length;
   }

   /**
    * Get the actual threshold which when reached the map will resize. This is a function of the current capacity and
    * load factor.
    *
    * @return the threshold when the map will resize
    */
   public int resizeThreshold() {
      return resizeThreshold;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean add(final Long value) {
      return add(value.longValue());
   }

   /**
    * Primitive specialised overload of {this#add(Long)}
    *
    * @param value the value to add
    * @return true if the collection has changed, false otherwise
    * @throws IllegalArgumentException if value is missingValue
    */
   public boolean add(final long value) {
      if (value == MISSING_VALUE) {
         final boolean previousContainsMissingValue = this.containsMissingValue;
         containsMissingValue = true;
         return !previousContainsMissingValue;
      }

      final long[] values = this.values;
      final int mask = values.length - 1;
      int index = hashIndex(value, mask);

      while (values[index] != MISSING_VALUE) {
         if (values[index] == value) {
            return false;
         }

         index = next(index, mask);
      }

      values[index] = value;
      sizeOfArrayValues++;

      if (sizeOfArrayValues > resizeThreshold) {
         increaseCapacity();
      }

      return true;
   }

   private void increaseCapacity() {
      final int newCapacity = values.length * 2;
      if (newCapacity < 0) {
         throw new IllegalStateException("max capacity reached at size=" + size());
      }

      rehash(newCapacity);
   }

   private void rehash(final int newCapacity) {
      final int capacity = newCapacity;
      final int mask = newCapacity - 1;
      resizeThreshold = (int) (newCapacity * loadFactor);

      final long[] tempValues = new long[capacity];
      Arrays.fill(tempValues, MISSING_VALUE);

      for (final long value : values) {
         if (value != MISSING_VALUE) {
            int newHash = hashIndex(value, mask);
            while (tempValues[newHash] != MISSING_VALUE) {
               newHash = ++newHash & mask;
            }

            tempValues[newHash] = value;
         }
      }
      values = tempValues;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean remove(final Object value) {
      return value instanceof Long l && remove(l.longValue());
   }

   /**
    * An int specialised version of {this#remove(Object)}.
    *
    * @param value the value to remove
    * @return true if the value was present, false otherwise
    */
   public boolean remove(final long value) {
      if (value == MISSING_VALUE) {
         final boolean previousContainsMissingValue = this.containsMissingValue;
         containsMissingValue = false;
         return previousContainsMissingValue;
      }

      final long[] values = this.values;
      final int mask = values.length - 1;
      int index = hashIndex(value, mask);

      while (values[index] != MISSING_VALUE) {
         if (values[index] == value) {
            values[index] = MISSING_VALUE;
            compactChain(index);
            sizeOfArrayValues--;
            return true;
         }

         index = next(index, mask);
      }

      return false;
   }

   private static int next(final int index, final int mask) {
      return (index + 1) & mask;
   }

   @SuppressWarnings("FinalParameters")
   private void compactChain(int deleteIndex) {
      final long[] values = this.values;
      final int mask = values.length - 1;

      int index = deleteIndex;
      while (true) {
         index = next(index, mask);
         if (values[index] == MISSING_VALUE) {
            return;
         }

         final int hash = hashIndex(values[index], mask);

         if ((index < hash && (hash <= deleteIndex || deleteIndex <= index)) || (hash <= deleteIndex && deleteIndex <= index)) {
            values[deleteIndex] = values[index];

            values[index] = MISSING_VALUE;
            deleteIndex = index;
         }
      }
   }

   /**
    * Compact the backing arrays by rehashing with a capacity just larger than current size and giving consideration to
    * the load factor.
    */
   public void compact() {
      final int idealCapacity = (int) Math.round(size() * (1.0 / loadFactor));
      rehash(findNextPositivePowerOfTwo(Math.max(DEFAULT_INITIAL_CAPACITY, idealCapacity)));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean contains(final Object value) {
      return value instanceof Long l && contains(l.longValue());
   }

   /**
    * Contains method that does not box values.
    *
    * @param value to be check for if the set contains it.
    * @return true if the value is contained in the set otherwise false
    * @see Collection#contains(Object)
    */
   public boolean contains(final long value) {
      if (value == MISSING_VALUE) {
         return containsMissingValue;
      }

      final long[] values = this.values;
      final int mask = values.length - 1;
      int index = hashIndex(value, mask);

      while (values[index] != MISSING_VALUE) {
         if (values[index] == value) {
            return true;
         }

         index = next(index, mask);
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int size() {
      return sizeOfArrayValues + (containsMissingValue ? 1 : 0);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isEmpty() {
      return size() == 0;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void clear() {
      if (size() > 0) {
         Arrays.fill(values, MISSING_VALUE);
         sizeOfArrayValues = 0;
         containsMissingValue = false;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean addAll(final Collection<? extends Long> coll) {
      boolean added = false;

      for (final Long value : coll) {
         added |= add(value);
      }

      return added;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean removeAll(final Collection<?> coll) {
      boolean removed = false;

      for (final Object value : coll) {
         removed |= remove(value);
      }

      return removed;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public LongIterator iterator() {
      return new LongIterator().reset();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append('{');

      for (final long value : values) {
         if (value != MISSING_VALUE) {
            sb.append(value).append(", ");
         }
      }

      if (containsMissingValue) {
         sb.append(MISSING_VALUE).append(", ");
      }

      if (sb.length() > 1) {
         sb.setLength(sb.length() - 2);
      }

      sb.append('}');

      return sb.toString();
   }

   /**
    * {@inheritDoc}
    */
   @SuppressWarnings("unchecked")
   @Override
   public <E> E[] toArray(final E[] a) {
      final Class<?> componentType = a.getClass().getComponentType();
      if (!componentType.isAssignableFrom(Long.class)) {
         throw new ArrayStoreException("cannot store Longs in array of type " + componentType);
      }

      final int size = size();
      final E[] arrayCopy = a.length >= size ? a : (E[]) Array.newInstance(componentType, size);
      copyValues(arrayCopy);

      return arrayCopy;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object[] toArray() {
      final Object[] arrayCopy = new Object[size()];
      copyValues(arrayCopy);

      return arrayCopy;
   }

   private void copyValues(final Object[] arrayCopy) {
      int i = 0;
      final long[] values = this.values;
      for (final long value : values) {
         if (MISSING_VALUE != value) {
            arrayCopy[i++] = value;
         }
      }

      if (containsMissingValue) {
         arrayCopy[sizeOfArrayValues] = MISSING_VALUE;
      }
   }

   /**
    * LongHashSet specialised variant of {this#containsAll(Collection)}.
    *
    * @param other int hash set to compare against.
    * @return true if every element in other is in this
    */
   public boolean containsAll(final LongHashSet other) {
      for (final long value : other.values) {
         if (value != MISSING_VALUE && !contains(value)) {
            return false;
         }
      }

      return !other.containsMissingValue || this.containsMissingValue;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean equals(final Object obj) {
      if (obj == this) {
         return true;
      }

      if (obj instanceof LongHashSet other) {
         return other.containsMissingValue == containsMissingValue &&
                other.sizeOfArrayValues == sizeOfArrayValues &&
                containsAll(other);
      }

      if (!(obj instanceof Set<?> c)) {
         return false;
      }

      if (c.size() != size()) {
         return false;
      }

      try {
         return containsAll(c);
      } catch (final ClassCastException | NullPointerException ignore) {
         return false;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int hashCode() {
      int hashCode = 0;
      for (final long value : values) {
         if (value != MISSING_VALUE) {
            hashCode += Long.hashCode(value);
         }
      }

      if (containsMissingValue) {
         // Account negative hashcode
         final int code = Long.hashCode(MISSING_VALUE);
         hashCode += code;
      }

      return hashCode;
   }

   /**
    * Iterator which supports unboxed access to values.
    */
   public final class LongIterator implements Iterator<Long>, Serializable {

      private int remaining;
      private int positionCounter;
      private int stopCounter;
      private boolean isPositionValid = false;

      LongIterator reset() {
         remaining = size();

         final long[] values = LongHashSet.this.values;
         final int length = values.length;
         int i = length;

         if (values[length - 1] != LongHashSet.MISSING_VALUE) {
            for (i = 0; i < length; i++) {
               if (values[i] == LongHashSet.MISSING_VALUE) {
                  break;
               }
            }
         }

         stopCounter = i;
         positionCounter = i + length;
         isPositionValid = false;

         return this;
      }

      @Override
      public boolean hasNext() {
         return remaining > 0;
      }

      public int remaining() {
         return remaining;
      }

      @Override
      public Long next() {
         return nextValue();
      }

      /**
       * Strongly typed alternative of {@link Iterator#next()} to avoid boxing.
       *
       * @return the next int value
       */
      public long nextValue() {
         if (remaining == 1 && containsMissingValue) {
            remaining = 0;
            isPositionValid = true;

            return LongHashSet.MISSING_VALUE;
         }

         findNext();

         final long[] values = LongHashSet.this.values;

         return values[position(values)];
      }

      @Override
      public void remove() {
         if (isPositionValid) {
            if (0 == remaining && containsMissingValue) {
               containsMissingValue = false;
            } else {
               final long[] values = LongHashSet.this.values;
               final int position = position(values);
               values[position] = MISSING_VALUE;
               --sizeOfArrayValues;

               compactChain(position);
            }

            isPositionValid = false;
         } else {
            throw new IllegalStateException();
         }
      }

      private void findNext() {
         final long[] values = LongHashSet.this.values;
         final int mask = values.length - 1;
         isPositionValid = true;

         for (int i = positionCounter - 1; i >= stopCounter; i--) {
            final int index = i & mask;
            if (values[index] != LongHashSet.MISSING_VALUE) {
               positionCounter = i;
               --remaining;
               return;
            }
         }

         isPositionValid = false;
         throw new NoSuchElementException();
      }

      private int position(final long[] values) {
         return positionCounter & (values.length - 1);
      }
   }
}