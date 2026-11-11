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
package org.apache.activemq.artemis.tests.performance.jmh;

import java.util.SplittableRandom;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.impl.DuplicateIDCaches;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class DuplicateIDCacheBenchmark {

   @Param({"20000"})
   private int size;
   @Param({"false", "true"})
   private boolean persist;

   private DuplicateIDCache cache;

   private byte[][] ids;
   private int idsMask;
   private int missingIdsMask;
   private long nextId;
   private byte[][] randomEvictedIds;

   @Setup
   public void init() throws Exception {
      cache = persist ?
         DuplicateIDCaches.persistent(SimpleString.of("benchmark"), size, new NullStorageManager()) :
         DuplicateIDCaches.inMemory(SimpleString.of("benchmark"), size);
      final int idSize = findNextHigherPowerOf2(size);
      idsMask = idSize - 1;
      nextId = 0;
      ids = new byte[idSize][];
      // fill the cache
      for (int i = 0; i < size; i++) {
         final byte[] id = RandomUtil.randomBytes();
         ids[i] = id;
         cache.addToCache(id, null, true);
      }
      // evict the first (idSize - size) elements on the ids array.
      // Given that being a FIFO cache isn't a stable contract it's going to validate it too.
      final int evicted = idSize - size;
      for (int i = 0; i < evicted; i++) {
         final byte[] id = RandomUtil.randomBytes();
         ids[size + i] = id;
         cache.addToCache(id, null, true);
         // check correctness of eviction policy
         if (cache.contains(ids[i])) {
            throw new AssertionError("This cache isn't using anymore a FIFO eviction strategy or its real capacity is > " + size);
         }
      }
      // always use the same seed!
      SplittableRandom random = new SplittableRandom(0);
      // set it big enough to trick branch predictors
      final int evictedIdsLength = findNextPowerOf2(Math.max(1024, evicted));
      missingIdsMask = evictedIdsLength - 1;
      randomEvictedIds = new byte[evictedIdsLength][];
      for (int i = 0; i < evictedIdsLength; i++) {
         final int id = random.nextInt(0, evicted);
         randomEvictedIds[i] = ids[id];
         // check correctness of eviction policy
         if (cache.contains(ids[id])) {
            throw new AssertionError("This cache isn't using anymore a FIFO eviction strategy");
         }
      }
   }

   // it isn't checking what's the max power of 2 number nor if size > 0
   private static int findNextHigherPowerOf2(int size) {
      final int nextPow2 = findNextPowerOf2(size);
      if (nextPow2 > size) {
         return nextPow2;
      }
      return nextPow2 * 2;
   }

   private static int findNextPowerOf2(int size) {
      return 1 << (32 - Integer.numberOfLeadingZeros(size - 1));
   }

   private byte[] nextId() {
      final long seq = nextId;
      final int index = (int) (seq & idsMask);
      nextId = seq + 1;
      return ids[index];
   }

   private byte[] nextMissingId() {
      final long seq = nextId;
      final int index = (int) (seq & missingIdsMask);
      nextId = seq + 1;
      return randomEvictedIds[index];
   }

   @Benchmark
   public boolean atomicVerify() throws Exception {
      return cache.atomicVerify(nextId(), null);
   }

   @Benchmark
   public boolean containsMissingId() {
      return cache.contains(nextMissingId());
   }

   @TearDown
   public void clear() throws Exception {
      cache.clear();
   }

}
