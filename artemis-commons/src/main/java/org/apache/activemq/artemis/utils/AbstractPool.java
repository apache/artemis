/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.utils;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.MathUtil;

/**
 * Thread-safe {@code <T>} interner.
 * <p>
 * Differently from {@link String#intern()} it contains a fixed amount of entries and when used by concurrent threads it
 * doesn't ensure the uniqueness of the entries ie the same entry could be allocated multiple times by concurrent
 * calls.
 */
public abstract class AbstractPool<I, O> {

   public static final int DEFAULT_POOL_CAPACITY = 32;

   private final O[] entries;
   private final int mask;
   private final int shift;

   public AbstractPool() {
      this(DEFAULT_POOL_CAPACITY);
   }

   /**
    * Constructs an AbstractPool instance with a specified initial capacity. The capacity is rounded up to the next
    * highest power of two, and this determines the size of the internal array used by the pool.
    *
    * @param capacity the desired initial capacity of the pool
    * @see MathUtil#findNextPositivePowerOfTwo(int)
    */
   public AbstractPool(final int capacity) {
      entries = (O[]) new Object[MathUtil.findNextPositivePowerOfTwo(capacity)];
      mask = entries.length - 1;
      //log2 of entries.length
      shift = 31 - Integer.numberOfLeadingZeros(entries.length);
   }

   /**
    * Create a new entry.
    */
   protected abstract O create(I value);

   /**
    * {@return {@code true} if the {@code entry} content is equal to {@code value};}
    */
   protected abstract boolean isEqual(O entry, I value);

   protected int hashCode(I value) {
      return value.hashCode();
   }

   /**
    * Returns and interned entry if possible, a new one otherwise.
    * <p>
    * The {@code byteBuf}'s {@link ByteBuf#readerIndex()} is incremented by {@code length} after it.
    */
   public final O getOrCreate(final I value) {
      if (value == null) {
         return null;
      }
      final int hashCode = hashCode(value);
      //fast % operation with power of 2 entries.length
      final int firstIndex = hashCode & mask;
      final O firstEntry = entries[firstIndex];
      if (isEqual(firstEntry, value)) {
         return firstEntry;
      }
      final int secondIndex = (hashCode >> shift) & mask;
      final O secondEntry = entries[secondIndex];
      if (isEqual(secondEntry, value)) {
         return secondEntry;
      }
      final O internedEntry = create(value);
      final int entryIndex = firstEntry == null ? firstIndex : secondIndex;
      entries[entryIndex] = internedEntry;
      return internedEntry;
   }
}
