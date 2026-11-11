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
import io.netty.util.internal.PlatformDependent;

/**
 * Thread-safe {@code <T>} interner.
 * <p>
 * Differently from {@link String#intern()} it contains a fixed amount of entries and when used by concurrent threads it
 * doesn't ensure the uniqueness of the entries ie the same entry could be allocated multiple times by concurrent
 * calls.
 */
public abstract class AbstractByteBufPool<T> {

   public static final int DEFAULT_POOL_CAPACITY = 32;

   private final T[] entries;
   private final int mask;
   private final int shift;

   public AbstractByteBufPool() {
      this(DEFAULT_POOL_CAPACITY);
   }

   public AbstractByteBufPool(final int capacity) {
      entries = (T[]) new Object[MathUtil.findNextPositivePowerOfTwo(capacity)];
      mask = entries.length - 1;
      //log2 of entries.length
      shift = 31 - Integer.numberOfLeadingZeros(entries.length);
   }

   /**
    * Batch hash code implementation that works at its best if {@code bytes} contains a
    * {@link org.apache.activemq.artemis.api.core.SimpleString} encoded.
    */
   private static int hashCode(final ByteBuf bytes, final int offset, final int length) {
      if (PlatformDependent.isUnaligned() && PlatformDependent.hasUnsafe()) {
         //if the platform allows it, the hash code could be computed without bounds checking
         if (bytes.hasArray()) {
            return onHeapHashCode(bytes.array(), bytes.arrayOffset() + offset, length);
         } else if (bytes.hasMemoryAddress()) {
            return offHeapHashCode(bytes.memoryAddress(), offset, length);
         }
      }
      return byteBufHashCode(bytes, offset, length);
   }

   private static int onHeapHashCode(final byte[] bytes, final int offset, final int length) {
      final int intCount = length >>> 1;
      final int byteCount = length & 1;
      int hashCode = 1;
      int arrayIndex = offset;
      for (int i = 0; i < intCount; i++) {
         hashCode = 31 * hashCode + PlatformDependent.getShort(bytes, arrayIndex);
         arrayIndex += 2;
      }
      for (int i = 0; i < byteCount; i++) {
         hashCode = 31 * hashCode + PlatformDependent.getByte(bytes, arrayIndex++);
      }
      return hashCode;
   }

   private static int offHeapHashCode(final long address, final int offset, final int length) {
      final int intCount = length >>> 1;
      final int byteCount = length & 1;
      int hashCode = 1;
      int arrayIndex = offset;
      for (int i = 0; i < intCount; i++) {
         hashCode = 31 * hashCode + PlatformDependent.getShort(address + arrayIndex);
         arrayIndex += 2;
      }
      for (int i = 0; i < byteCount; i++) {
         hashCode = 31 * hashCode + PlatformDependent.getByte(address + arrayIndex++);
      }
      return hashCode;
   }

   private static int byteBufHashCode(final ByteBuf byteBuf, final int offset, final int length) {
      final int intCount = length >>> 1;
      final int byteCount = length & 1;
      int hashCode = 1;
      int arrayIndex = offset;
      for (int i = 0; i < intCount; i++) {
         final short shortLE = byteBuf.getShortLE(arrayIndex);
         final short nativeShort = PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes(shortLE) : shortLE;
         hashCode = 31 * hashCode + nativeShort;
         arrayIndex += 2;
      }
      for (int i = 0; i < byteCount; i++) {
         hashCode = 31 * hashCode + byteBuf.getByte(arrayIndex++);
      }
      return hashCode;
   }

   /**
    * {@return {@code true} if {@code length}'s {@code byteBuf} content from {@link ByteBuf#readerIndex()} can be
    * pooled, {@code false} otherwise}
    */
   protected abstract boolean canPool(ByteBuf byteBuf, int length);

   /**
    * Create a new entry.
    */
   protected abstract T create(ByteBuf byteBuf, int length);

   /**
    * {@return {@code true} if the {@code entry} content is the same of {@code byteBuf} at the specified {@code offset}
    * and {@code length} {@code false} otherwise}
    */
   protected abstract boolean isEqual(T entry, ByteBuf byteBuf, int offset, int length);

   /**
    * Returns a pooled entry if possible, a new one otherwise.
    * <p>
    * The {@code byteBuf}'s {@link ByteBuf#readerIndex()} is incremented by {@code length} after it.
    */
   public final T getOrCreate(final ByteBuf byteBuf) {
      final int length = byteBuf.readInt();
      if (!canPool(byteBuf, length)) {
         return create(byteBuf, length);
      } else {
         if (!byteBuf.isReadable(length)) {
            throw new IndexOutOfBoundsException();
         }
         final int bytesOffset = byteBuf.readerIndex();
         final int hashCode = hashCode(byteBuf, bytesOffset, length);
         //fast % operation with power of 2 entries.length
         final int firstIndex = hashCode & mask;
         final T firstEntry = entries[firstIndex];
         if (isEqual(firstEntry, byteBuf, bytesOffset, length)) {
            byteBuf.skipBytes(length);
            return firstEntry;
         }
         final int secondIndex = (hashCode >> shift) & mask;
         final T secondEntry = entries[secondIndex];
         if (isEqual(secondEntry, byteBuf, bytesOffset, length)) {
            byteBuf.skipBytes(length);
            return secondEntry;
         }
         final T internedEntry = create(byteBuf, length);
         final int entryIndex = firstEntry == null ? firstIndex : secondIndex;
         entries[entryIndex] = internedEntry;
         return internedEntry;
      }
   }
}
