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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

/**
 * This collection is a concurrent append-only list that grows in chunks.
 * <p>
 * It's safe to be used by many threads concurrently and has a max capacity of {@link Integer#MAX_VALUE}.
 */
public final class ConcurrentAppendOnlyChunkedList<E> {

   private static final class AtomicChunk<E> extends AtomicReferenceArray<E> {

      AtomicChunk<E> next = null;
      final AtomicChunk<E> prev;
      final int index;

      AtomicChunk(int index, AtomicChunk<E> prev, int length) {
         super(length);
         this.index = index;
         this.prev = prev;
      }
   }

   private static final AtomicLongFieldUpdater<ConcurrentAppendOnlyChunkedList> LAST_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(ConcurrentAppendOnlyChunkedList.class, "lastIndex");

   private final int chunkSize;

   private final int chunkMask;

   private final int chunkSizeLog2;

   private AtomicChunk<E> firstBuffer = null;

   private AtomicChunk<E> lastBuffer = null;

   //it is both the current index of the next element to be claimed and the current size of the collection
   //it's using a parity bit to mark the rotation state ie size === lastIndex >> 1
   private volatile long lastIndex = 0;

   /**
    * @throws IllegalArgumentException if {@code chunkSize} is &lt;0 or not a power of 2
    */
   public ConcurrentAppendOnlyChunkedList(final int chunkSize) {
      if (chunkSize <= 0) {
         throw new IllegalArgumentException("chunkSize must be >0");
      }
      //IMPORTANT: to enable some nice optimizations on / and %, chunk size MUST BE a power of 2
      if (Integer.bitCount(chunkSize) != 1) {
         throw new IllegalArgumentException("chunkSize must be a power of 2");
      }
      this.chunkSize = chunkSize;
      this.chunkMask = chunkSize - 1;
      this.chunkSizeLog2 = Integer.numberOfTrailingZeros(chunkSize);
   }

   private long getValidLastIndex() {
      return this.lastIndex >> 1;
   }

   /**
    * It returns the number of elements currently added.
    */
   public int size() {
      return (int) getValidLastIndex();
   }

   /**
    * It appends {@code elements} to the collection.
    */
   public void addAll(E[] elements) {
      for (E e : elements) {
         add(e);
      }
   }

   /**
    * {@return the element at the specified position in this collection or {@code null} if not found.}
    */
   public E get(int index) {
      if (index < 0) {
         return null;
      }
      final long lastIndex = getValidLastIndex();
      //it is a element over the current size?
      if (index >= lastIndex) {
         return null;
      }
      final AtomicChunk<E> buffer;
      final int offset;
      if (index >= chunkSize) {
         offset = index & chunkMask;
         //slow path is moved in a separate method
         buffer = getChunkOf(index, lastIndex);
      } else {
         offset = index;
         buffer = firstBuffer;
      }
      return pollElement(buffer, offset);
   }

   /**
    * Implements a lock-free version of the optimization used on {@link java.util.LinkedList#get(int)} to speed up
    * queries i.e. backward search of a node if needed.
    */
   private AtomicChunk<E> getChunkOf(final int index, final long lastIndex) {
      final int chunkSizeLog2 = this.chunkSizeLog2;
      //fast division by a power of 2
      final int chunkIndex = index >> chunkSizeLog2;
      //size is never allowed to be > Integer.MAX_VALUE
      final int lastChunkIndex = (int) lastIndex >> chunkSizeLog2;
      int distance = chunkIndex;
      AtomicChunk<E> buffer = null;
      boolean forward = true;
      int distanceFromLast = lastChunkIndex - chunkIndex;
      //it's worth to go backward from lastChunkIndex?
      //trying first to check against the value we already have: if it won't worth, won't make sense to load the lastBuffer
      if (distanceFromLast < distance) {
         final AtomicChunk<E> lastBuffer = this.lastBuffer;
         //lastBuffer is a potential moving, always increasing, target ie better to re-check the distance
         distanceFromLast = lastBuffer.index - chunkIndex;
         if (distanceFromLast < distance) {
            //we're saving some jumps ie is fine to go backward from here
            buffer = lastBuffer;
            distance = distanceFromLast;
            forward = false;
         }
      }
      //start from the first buffer only is needed
      if (buffer == null) {
         buffer = firstBuffer;
      }
      for (int i = 0; i < distance; i++) {
         //next chunk is always set if below a read lastIndex value
         //previous chunk is final and can be safely read
         buffer = forward ? buffer.next : buffer.prev;
      }
      return buffer;
   }

   /**
    * Appends the specified element to the end of this collection.
    *
    * @throws NullPointerException if {@code e} is {@code null}
    */
   public void add(E e) {
      Objects.requireNonNull(e);
      while (true) {
         final long lastIndex = this.lastIndex;
         // lower bit is indicative of appending
         if ((lastIndex & 1) == 1) {
            continue;
         }
         final long validLastIndex = lastIndex >> 1;
         if (validLastIndex == Integer.MAX_VALUE) {
            throw new IllegalStateException("can't add more then " + Integer.MAX_VALUE + " elements");
         }
         //load acquire the current lastBuffer
         final AtomicChunk<E> lastBuffer = this.lastBuffer;
         final int offset = (int) (validLastIndex & chunkMask);
         //only the first attempt to add an element to a chunk can attempt to resize
         if (offset == 0) {
            if (addChunkAndElement(lastBuffer, lastIndex, validLastIndex, e)) {
               return;
            }
         } else if (LAST_INDEX_UPDATER.compareAndSet(this, lastIndex, lastIndex + 2)) {
            //this.lastBuffer is the correct buffer to append a element: it is guarded by the lastIndex logic
            //NOTE: lastIndex is being updated before setting a new value
            lastBuffer.lazySet(offset, e);
            return;
         }
      }
   }

   private boolean addChunkAndElement(AtomicChunk<E> lastBuffer, long lastIndex, long validLastIndex, E element) {
      // adding 1 will set the lower bit
      if (!LAST_INDEX_UPDATER.compareAndSet(this, lastIndex, lastIndex + 1)) {
         return false;
      }
      final AtomicChunk<E> newChunk;
      try {
         final int index = (int) (validLastIndex >> chunkSizeLog2);
         newChunk = new AtomicChunk<>(index, lastBuffer, chunkSize);
      } catch (OutOfMemoryError oom) {
         //unblock lastIndex without updating it
         LAST_INDEX_UPDATER.lazySet(this, lastIndex);
         throw oom;
      }
      //adding the element to it
      newChunk.lazySet(0, element);
      //linking it to the old one, if any
      if (lastBuffer != null) {
         //a plain store is enough, given that lastIndex prevents any reader/writer to access it
         lastBuffer.next = newChunk;
      } else {
         //it's first one
         this.firstBuffer = newChunk;
      }
      //making it the current produced one
      this.lastBuffer = newChunk;
      //store release any previous write and unblock anyone waiting resizing to finish
      //and would clean the lower bit
      LAST_INDEX_UPDATER.lazySet(this, lastIndex + 2);
      return true;
   }

   public E[] toArray(IntFunction<E[]> arrayAllocator) {
      return toArray(arrayAllocator, 0);
   }

   /**
    * {@return an array containing all of the elements in this collection in proper sequence (from first to last
    * element); {@code arrayAllocator} will be used to instantiate the array of the correct size with the right runtime
    * type}
    */
   public E[] toArray(IntFunction<E[]> arrayAllocator, int startIndex) {
      if (startIndex < 0) {
         throw new ArrayIndexOutOfBoundsException("startIndex must be >= 0");
      }
      final long lastIndex = getValidLastIndex();
      assert lastIndex <= Integer.MAX_VALUE;
      final int size = (int) lastIndex;
      final E[] elements = arrayAllocator.apply(size);
      if (startIndex + size > elements.length) {
         throw new ArrayIndexOutOfBoundsException();
      }
      //fast division by a power of 2
      final int chunkSize = this.chunkSize;
      final int chunks = size > chunkSize ? size >> chunkSizeLog2 : 0;
      AtomicChunk<E> buffer = firstBuffer;
      int elementIndex = startIndex;
      for (int i = 0; i < chunks; i++) {
         drain(buffer, elements, elementIndex, chunkSize);
         elementIndex += chunkSize;
         //the next chunk is always set if we stay below a past size/lastIndex value
         buffer = buffer.next;
      }
      final int remaining = chunks > 0 ? (size & chunkMask) : size;
      drain(buffer, elements, elementIndex, remaining);
      return elements;
   }

   //NOTE: lastIndex is being updated BEFORE setting a new value ie on reader side need to spin until a not null value is set
   private static <E> E pollElement(AtomicChunk<E> buffer, int i) {
      E e;
      while ((e = buffer.get(i)) == null) {
      }
      return e;
   }

   private static <E> void drain(AtomicChunk<E> buffer, E[] elements, int elementNumber, int length) {
      for (int j = 0; j < length; j++) {
         final E e = pollElement(buffer, j);
         assert e != null;
         elements[elementNumber] = e;
         elementNumber++;
      }
   }

}

