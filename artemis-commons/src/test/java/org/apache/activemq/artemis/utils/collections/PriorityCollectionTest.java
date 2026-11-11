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

import org.apache.activemq.artemis.core.PriorityAware;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

public class PriorityCollectionTest {

   @Test
   public void simpleInsertions() {
      PriorityCollection<TestPriorityAware> set = new PriorityCollection<>(CopyOnWriteArraySet::new);

      assertTrue(set.isEmpty());
      assertTrue(set.add(new TestPriorityAware(1)));
      assertFalse(set.isEmpty());

      assertTrue(set.add(new TestPriorityAware(2)));
      assertTrue(set.add(new TestPriorityAware(3)));

      assertEquals(3, set.size());

      assertTrue(set.contains(new TestPriorityAware(1)));
      assertEquals(3, set.size());

      assertTrue(set.remove(new TestPriorityAware(1)));
      assertEquals(2, set.size());
      assertFalse(set.contains(new TestPriorityAware(1)));
      assertFalse(set.contains(new TestPriorityAware(5)));
      assertEquals(2, set.size());

      assertTrue(set.add(new TestPriorityAware(1)));
      assertEquals(3, set.size());
      assertFalse(set.add(new TestPriorityAware(1)));
      assertEquals(3, set.size());
   }

   @Test
   public void testRemove() {
      PriorityCollection<TestPriorityAware> set = new PriorityCollection<>(CopyOnWriteArraySet::new);

      assertTrue(set.isEmpty());
      assertTrue(set.add(new TestPriorityAware(1)));
      assertFalse(set.isEmpty());

      assertFalse(set.remove(new TestPriorityAware(0)));
      assertFalse(set.isEmpty());
      assertTrue(set.remove(new TestPriorityAware(1)));
      assertTrue(set.isEmpty());
   }

   @Test
   public void concurrentInsertions() throws Throwable {
      PriorityCollection<TestPriorityAware> set = new PriorityCollection<>(CopyOnWriteArraySet::new);
      ExecutorService executor = Executors.newCachedThreadPool();

      final int nThreads = 16;
      final int N = 1000;

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
         final int threadIdx = i;

         futures.add(executor.submit(() -> {
            final ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int j = 0; j < N; j++) {
               long key = random.nextLong(Long.MAX_VALUE);
               // Ensure keys are unique
               key -= key % (threadIdx + 1);

               set.add(new TestPriorityAware(key));
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
      PriorityCollection<TestPriorityAware> set = new PriorityCollection<>(CopyOnWriteArraySet::new);
      ExecutorService executor = Executors.newCachedThreadPool();

      final int nThreads = 16;
      final int N = 1000;

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
         final int threadIdx = i;

         futures.add(executor.submit(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int j = 0; j < N; j++) {
               long key = random.nextLong(Long.MAX_VALUE);
               // Ensure keys are unique
               key -= key % (threadIdx + 1);

               set.add(new TestPriorityAware(key));
            }
         }));

         futures.add(executor.submit(() -> {
            Iterator<TestPriorityAware> iterator = set.resettableIterator();
            while (iterator.hasNext()) {
               iterator.next();
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
   public void testIteration() {
      PriorityCollection<TestPriorityAware> set = new PriorityCollection<>(CopyOnWriteArraySet::new);

      assertFalse(set.iterator().hasNext());

      set.add(new TestPriorityAware(0));

      assertTrue(set.iterator().hasNext());

      set.remove(new TestPriorityAware(0));

      assertFalse(set.iterator().hasNext());

      set.add(new TestPriorityAware(0));
      set.add(new TestPriorityAware(1));
      set.add(new TestPriorityAware(2));

      List<TestPriorityAware> values = new ArrayList<>(set);

      assertTrue(values.contains(new TestPriorityAware(0)));
      assertTrue(values.contains(new TestPriorityAware(1)));
      assertTrue(values.contains(new TestPriorityAware(2)));

      set.clear();
      assertTrue(set.isEmpty());
   }

   @Test
   public void priorityTest() {
      PriorityCollection<TestPriority> set = new PriorityCollection<>(CopyOnWriteArraySet::new);
      set.add(new TestPriority("A", 127));
      set.add(new TestPriority("B", 127));
      set.add(new TestPriority("E", 0));
      set.add(new TestPriority("D", 20));
      set.add(new TestPriority("C", 127));

      ResettableIterator<TestPriority> iterator = set.resettableIterator();
      iterator.reset();
      assertTrue(iterator.hasNext());

      assertEquals("A", iterator.next().getName());

      //Reset iterator should mark start as current position
      iterator.reset();
      assertTrue(iterator.hasNext());
      assertEquals("B", iterator.next().getName());

      assertTrue(iterator.hasNext());
      assertEquals("C", iterator.next().getName());

      //Expect another A as after reset, we started at B so after A we then expect the next level
      assertTrue(iterator.hasNext());
      assertEquals("A", iterator.next().getName());

      assertTrue(iterator.hasNext());
      assertEquals("D", iterator.next().getName());

      assertTrue(iterator.hasNext());
      assertEquals("E", iterator.next().getName());

      //We have iterated all.
      assertFalse(iterator.hasNext());

      //Reset to iterate again.
      iterator.reset();

      //We expect the iteration to round robin from last returned at the level.
      assertTrue(iterator.hasNext());
      assertEquals("B", iterator.next().getName());


   }


   private class TestPriorityAware implements PriorityAware {

      private long value;

      private TestPriorityAware(long value) {
         this.value = value;
      }

      @Override
      public int getPriority() {
         //10 priority levels
         return (int) value % 10;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof TestPriorityAware other)) {
            return false;
         }

         return value == other.value;
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(value);
      }
   }

   private class TestPriority implements PriorityAware {

      private String name;
      private int priority;

      private TestPriority(String name, int priority) {
         this.name = name;
         this.priority = priority;
      }

      @Override
      public int getPriority() {
         return priority;
      }

      public String getName() {
         return name;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof TestPriority other)) {
            return false;
         }

         return priority == other.priority &&
                Objects.equals(name, other.name);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, priority);
      }
   }

}