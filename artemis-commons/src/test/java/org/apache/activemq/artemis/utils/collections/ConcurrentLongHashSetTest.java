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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

public class ConcurrentLongHashSetTest {

   @Test
   public void simpleInsertions() {
      ConcurrentLongHashSet set = new ConcurrentLongHashSet(16);

      assertTrue(set.isEmpty());
      assertTrue(set.add(1));
      assertFalse(set.isEmpty());

      assertTrue(set.add(2));
      assertTrue(set.add(3));

      assertEquals(3, set.size());

      assertTrue(set.contains(1));
      assertEquals(3, set.size());

      assertTrue(set.remove(1));
      assertEquals(2, set.size());
      assertFalse(set.contains(1));
      assertFalse(set.contains(5));
      assertEquals(2, set.size());

      assertTrue(set.add(1));
      assertEquals(3, set.size());
      assertFalse(set.add(1));
      assertEquals(3, set.size());
   }

   @Test
   public void testRemove() {
      ConcurrentLongHashSet set = new ConcurrentLongHashSet();

      assertTrue(set.isEmpty());
      assertTrue(set.add(1));
      assertFalse(set.isEmpty());

      assertFalse(set.remove(0));
      assertFalse(set.isEmpty());
      assertTrue(set.remove(1));
      assertTrue(set.isEmpty());
   }

   @Test
   public void testRehashing() {
      int n = 16;
      ConcurrentLongHashSet set = new ConcurrentLongHashSet(n / 2, 1);
      assertEquals(n, set.capacity());
      assertEquals(0, set.size());

      for (int i = 0; i < n; i++) {
         set.add(i);
      }

      assertEquals(2 * n, set.capacity());
      assertEquals(n, set.size());
   }

   @Test
   public void testRehashingWithDeletes() {
      int n = 16;
      ConcurrentLongHashSet set = new ConcurrentLongHashSet(n / 2, 1);
      assertEquals(n, set.capacity());
      assertEquals(0, set.size());

      for (int i = 0; i < n / 2; i++) {
         set.add(i);
      }

      for (int i = 0; i < n / 2; i++) {
         set.remove(i);
      }

      for (int i = n; i < (2 * n); i++) {
         set.add(i);
      }

      assertEquals(2 * n, set.capacity());
      assertEquals(n, set.size());
   }

   @Test
   public void concurrentInsertions() throws Throwable {
      ConcurrentLongHashSet set = new ConcurrentLongHashSet();
      ExecutorService executor = Executors.newCachedThreadPool();

      final int nThreads = 16;
      final int N = 100_000;

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
         final int threadIdx = i;

         futures.add(executor.submit(() -> {
            final ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int j = 0; j < N; j++) {
               long key = random.nextLong(Long.MAX_VALUE);
               // Ensure keys are unique
               key -= key % (threadIdx + 1);

               set.add(key);
            }
         }));
      }

      for (Future<?> future : futures) {
         future.get();
      }

      assertEquals(N * nThreads, set.size());

      executor.shutdown();
   }

   @Test
   public void concurrentInsertionsAndReads() throws Throwable {
      ConcurrentLongHashSet map = new ConcurrentLongHashSet();
      ExecutorService executor = Executors.newCachedThreadPool();

      final int nThreads = 16;
      final int N = 100_000;

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
         final int threadIdx = i;

         futures.add(executor.submit(() -> {
            final ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int j = 0; j < N; j++) {
               long key = random.nextLong(Long.MAX_VALUE);
               // Ensure keys are unique
               key -= key % (threadIdx + 1);

               map.add(key);
            }
         }));
      }

      for (Future<?> future : futures) {
         future.get();
      }

      assertEquals(N * nThreads, map.size());

      executor.shutdown();
   }

   @Test
   public void testIteration() {
      ConcurrentLongHashSet set = new ConcurrentLongHashSet();

      assertEquals(set.items(), Collections.emptySet());

      set.add(0L);

      assertEquals(set.items(), new HashSet<>(Arrays.asList(0L)));

      set.remove(0L);

      assertEquals(set.items(), Collections.emptySet());

      set.add(0L);
      set.add(1L);
      set.add(2L);

      List<Long> values = new ArrayList<>(set.items());
      Collections.sort(values);
      assertEquals(values, Arrays.asList(0L, 1L, 2L));

      set.clear();
      assertTrue(set.isEmpty());
   }

   @Test
   public void testHashConflictWithDeletion() {
      final int Buckets = 16;
      ConcurrentLongHashSet set = new ConcurrentLongHashSet(Buckets, 1);

      // Pick 2 keys that fall into the same bucket
      long key1 = 1;
      long key2 = 27;

      int bucket1 = ConcurrentLongHashSet.signSafeMod(ConcurrentLongHashSet.hash(key1), Buckets);
      int bucket2 = ConcurrentLongHashSet.signSafeMod(ConcurrentLongHashSet.hash(key2), Buckets);
      assertEquals(bucket1, bucket2);

      assertTrue(set.add(key1));
      assertTrue(set.add(key2));
      assertEquals(2, set.size());

      assertTrue(set.remove(key1));
      assertEquals(1, set.size());

      assertTrue(set.add(key1));
      assertEquals(2, set.size());

      assertTrue(set.remove(key1));
      assertEquals(1, set.size());

      assertFalse(set.add(key2));
      assertTrue(set.contains(key2));

      assertEquals(1, set.size());
      assertTrue(set.remove(key2));
      assertTrue(set.isEmpty());
   }

}