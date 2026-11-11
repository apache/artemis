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
package org.apache.activemq.artemis.utils.collections;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.StampedLock;

import org.apache.activemq.artemis.utils.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrent hash set for primitive longs
 * <p>
 * Provides similar methods as a ConcurrentSet&lt;Long&gt; but since it's an open hash map with linear probing, no node
 * allocations are required to store the values.
 * <p>
 * Items <strong>MUST</strong> be &gt;= 0.
 */
public class ConcurrentLongHashSet {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final long EmptyItem = -1L;
   private static final long DeletedItem = -2L;

   private static final float SetFillFactor = 0.66f;

   private static final int DefaultExpectedItems = 256;
   private static final int DefaultConcurrencyLevel = 16;

   private final Section[] sections;

   public interface ConsumerLong {
      void accept(long item);
   }

   public ConcurrentLongHashSet() {
      this(DefaultExpectedItems);
   }

   public ConcurrentLongHashSet(int expectedItems) {
      this(expectedItems, DefaultConcurrencyLevel);
   }

   public ConcurrentLongHashSet(int expectedItems, final int numSections) {
      Preconditions.checkArgument(numSections > 0);
      if (expectedItems < numSections) {
         expectedItems = numSections;
      }

      int perSectionExpectedItems = expectedItems / numSections;
      int perSectionCapacity = (int) (perSectionExpectedItems / SetFillFactor);
      this.sections = new Section[numSections];

      for (int i = 0; i < numSections; i++) {
         sections[i] = new Section(perSectionCapacity);
      }
   }

   public int size() {
      int size = 0;
      for (Section s : sections) {
         //read-acquire s.size that was write-released by s.unlockWrite
         s.tryOptimisticRead();
         //a stale value won't hurt: anyway it's subject to concurrent modifications
         size += s.size;
      }
      return size;
   }

   public long capacity() {
      long capacity = 0;
      for (Section s : sections) {
         capacity += s.capacity;
      }
      return capacity;
   }

   public boolean isEmpty() {
      for (Section s : sections) {
         //read-acquire s.size that was write-released by s.unlockWrite
         s.tryOptimisticRead();
         //a stale value won't hurt: anyway it's subject to concurrent modifications
         if (s.size != 0) {
            return false;
         }
      }

      return true;
   }

   long getUsedBucketCount() {
      long usedBucketCount = 0;
      for (Section s : sections) {
         usedBucketCount += s.usedBuckets;
      }
      return usedBucketCount;
   }

   public boolean contains(long item) {
      if (!moreThanZero(item)) {
         return false;
      }
      long h = hash(item);
      return getSection(h).contains(item, (int) h);
   }

   public boolean add(long item) {
      if (!moreThanZero(item)) {
         return false;
      }
      long h = hash(item);
      return getSection(h).add(item, (int) h);
   }

   /**
    * Remove an existing entry if found
    *
    * @return true if removed or false if item was not present
    */
   public boolean remove(long item) {
      if (!moreThanZero(item)) {
         return false;
      }
      long h = hash(item);
      return getSection(h).remove(item, (int) h);
   }

   private Section getSection(long hash) {
      // Use 32 msb out of long to get the section
      final int sectionIdx = (int) (hash >>> 32) & (sections.length - 1);
      return sections[sectionIdx];
   }

   public void clear() {
      for (Section s : sections) {
         s.clear();
      }
   }

   public void forEach(ConsumerLong processor) {
      for (Section s : sections) {
         s.forEach(processor);
      }
   }

   /**
    * @return a new list of all keys (makes a copy)
    */
   public Set<Long> items() {
      Set<Long> items = new HashSet<>();
      forEach(items::add);
      return items;
   }

   // A section is a portion of the hash map that is covered by a single
   @SuppressWarnings("serial")
   private static final class Section extends StampedLock {
      private static final AtomicIntegerFieldUpdater<Section> CAPACITY_UPDATER = AtomicIntegerFieldUpdater.newUpdater(Section.class, "capacity");
      // Keys and values are stored interleaved in the table array
      private long[] table;

      private volatile int capacity;
      private int size;
      private int usedBuckets;
      private int resizeThreshold;

      Section(int capacity) {
         this.capacity = alignToPowerOfTwo(capacity);
         this.table = new long[this.capacity];
         this.size = 0;
         this.usedBuckets = 0;
         this.resizeThreshold = (int) (this.capacity * SetFillFactor);
         Arrays.fill(table, EmptyItem);
      }

      boolean contains(long item, int hash) {
         long stamp = tryOptimisticRead();
         boolean acquiredLock = false;
         int bucket = signSafeMod(hash, capacity);

         try {
            while (true) {
               // First try optimistic locking
               long storedItem = table[bucket];

               if (!acquiredLock && validate(stamp)) {
                  // The values we have read are consistent
                  if (item == storedItem) {
                     return true;
                  } else if (storedItem == EmptyItem) {
                     // Not found
                     return false;
                  }
               } else {
                  // Fallback to acquiring read lock
                  if (!acquiredLock) {
                     stamp = readLock();
                     acquiredLock = true;

                     bucket = signSafeMod(hash, capacity);
                     storedItem = table[bucket];
                  }

                  if (item == storedItem) {
                     return true;
                  } else if (storedItem == EmptyItem) {
                     // Not found
                     return false;
                  }
               }

               bucket = (bucket + 1) & (table.length - 1);
            }
         } finally {
            if (acquiredLock) {
               unlockRead(stamp);
            }
         }
      }

      @SuppressWarnings("NonAtomicVolatileUpdate")
      boolean add(long item, long hash) {
         long stamp = writeLock();
         int bucket = signSafeMod(hash, capacity);

         // Remember where we find the first available spot
         int firstDeletedItem = -1;

         try {
            while (true) {
               long storedItem = table[bucket];

               if (item == storedItem) {
                  // Item was already in set
                  return false;
               } else if (storedItem == EmptyItem) {
                  // Found an empty bucket. This means the key is not in the map. If we've already seen a deleted
                  // key, we should write at that position
                  if (firstDeletedItem != -1) {
                     bucket = firstDeletedItem;
                  } else {
                     ++usedBuckets;
                  }

                  table[bucket] = item;
                  ++size;
                  return true;
               } else if (storedItem == DeletedItem) {
                  // The bucket contained a different deleted key
                  if (firstDeletedItem == -1) {
                     firstDeletedItem = bucket;
                  }
               }

               bucket = (bucket + 1) & (table.length - 1);
            }
         } finally {
            if (usedBuckets > resizeThreshold) {
               try {
                  rehash();
               } finally {
                  unlockWrite(stamp);
               }
            } else {
               unlockWrite(stamp);
            }
         }
      }

      @SuppressWarnings("NonAtomicVolatileUpdate")
      private boolean remove(long item, int hash) {
         long stamp = writeLock();
         int bucket = signSafeMod(hash, capacity);

         try {
            while (true) {
               long storedItem = table[bucket];
               if (item == storedItem) {
                  --size;

                  cleanBucket(bucket);
                  return true;

               } else if (storedItem == EmptyItem) {
                  // Key wasn't found
                  return false;
               }

               bucket = (bucket + 1) & (table.length - 1);
            }
         } finally {
            unlockWrite(stamp);
         }
      }

      private void cleanBucket(int bucket) {
         int nextInArray = (bucket + 1) & (table.length - 1);
         if (table[nextInArray] == EmptyItem) {
            table[bucket] = EmptyItem;
            --usedBuckets;
         } else {
            table[bucket] = DeletedItem;
         }
      }

      void clear() {
         long stamp = writeLock();

         try {
            Arrays.fill(table, EmptyItem);
            this.size = 0;
            this.usedBuckets = 0;
         } finally {
            unlockWrite(stamp);
         }
      }

      public void forEach(ConsumerLong processor) {
         long stamp = tryOptimisticRead();

         long[] table = this.table;
         boolean acquiredReadLock = false;

         try {

            // Validate no rehashing
            if (!validate(stamp)) {
               // Fallback to read lock
               stamp = readLock();
               acquiredReadLock = true;
               table = this.table;
            }

            // Go through all the buckets for this section
            for (int bucket = 0; bucket < table.length; bucket++) {
               long storedItem = table[bucket];

               if (!acquiredReadLock && !validate(stamp)) {
                  // Fallback to acquiring read lock
                  stamp = readLock();
                  acquiredReadLock = true;

                  storedItem = table[bucket];
               }

               if (storedItem != DeletedItem && storedItem != EmptyItem) {
                  processor.accept(storedItem);
               }
            }
         } finally {
            if (acquiredReadLock) {
               unlockRead(stamp);
            }
         }
      }

      private void rehash() {
         // Expand the hashmap
         int newCapacity = capacity * 2;
         long[] newTable = new long[newCapacity];
         Arrays.fill(newTable, EmptyItem);

         // Re-hash table
         for (int i = 0; i < table.length; i++) {
            long storedItem = table[i];
            if (storedItem != EmptyItem && storedItem != DeletedItem) {
               insertKeyValueNoLock(newTable, newCapacity, storedItem);
            }
         }

         table = newTable;
         usedBuckets = size;
         CAPACITY_UPDATER.lazySet(this, newCapacity);
         resizeThreshold = (int) (newCapacity * SetFillFactor);
      }

      private static void insertKeyValueNoLock(long[] table, int capacity, long item) {
         int bucket = signSafeMod(hash(item), capacity);

         while (true) {
            long storedKey = table[bucket];

            if (storedKey == EmptyItem) {
               // The bucket is empty, so we can use it
               table[bucket] = item;
               return;
            }

            bucket = (bucket + 1) & (table.length - 1);
         }
      }
   }

   private static final long HashMixer = 0xc6a4a7935bd1e995L;
   private static final int R = 47;

   static long hash(long key) {
      long hash = key * HashMixer;
      hash ^= hash >>> R;
      hash *= HashMixer;
      return hash;
   }

   static int signSafeMod(long n, int Max) {
      return (int) (n & (Max - 1));
   }

   static int alignToPowerOfTwo(int n) {
      return (int) Math.pow(2, 32 - Integer.numberOfLeadingZeros(n - 1));
   }

   static boolean moreThanZero(long n) {
      if (n < 0L) {
         logger.warn("Keys and values must be >= 0, while it was {}, entry will be ignored", n, new Exception("invalid record " + n));
         return false;
      } else {
         return true;
      }
   }
}