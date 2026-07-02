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
package org.apache.artemis.nativo.jlibaio.test.ffm;

import org.apache.artemis.nativo.jlibaio.ffm.IOCBInit;
import org.apache.artemis.nativo.jlibaio.ffm.IOControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IOControl lifecycle and concurrency tests")
public class IOControlTest {

   private Arena arena;
   private IOControl ioControl;

   @BeforeEach
   void setUp() {
      arena = Arena.ofConfined();

      ioControl = new IOControl();
      ioControl.setIoContext(arena.allocate(8));
      ioControl.setEvents(arena.allocate(8));
      ioControl.setQueueSize(8);

      MemorySegment[] pool = new MemorySegment[8];
      for (int i = 0; i < pool.length; i++) {
         pool[i] = arena.allocate(IOCBInit.IOCB_LAYOUT);
      }
      ioControl.setIocbPool(pool);
   }

   @AfterEach
   void tearDown() {
      if (arena != null) {
         arena.close();
      }
   }

   @Test
   void isValidShouldBeTrueForProperlyInitializedControl() {
      assertTrue(ioControl.isValid());
   }

   @Test
   void isValidShouldFailForNullContext() {
      ioControl.setIoContext(MemorySegment.NULL);
      assertFalse(ioControl.isValid());
   }

   @Test
   void isValidShouldFailForNullEvents() {
      ioControl.setEvents(MemorySegment.NULL);
      assertFalse(ioControl.isValid());
   }

   @Test
   void isValidShouldFailForZeroQueueSize() {
      ioControl.setQueueSize(0);
      assertFalse(ioControl.isValid());
   }

   @Test
   void getIOCBShouldReturnDifferentSegmentsUntilQueueIsExhausted() {
      Set<Long> addresses = new HashSet<>();

      for (int i = 0; i < 8; i++) {
         MemorySegment iocb = ioControl.getIOCB();
         assertNotNull(iocb);
         assertTrue(iocb.address() != 0L);
         addresses.add(iocb.address());
      }

      assertEquals(8, addresses.size());
      assertEquals(8, ioControl.used());
      assertEquals(0, ioControl.iocbGet());

      assertNull(ioControl.getIOCB());
   }

   @Test
   void putIOCBShouldReturnIOCBToPoolAndDecrementUsed() {
      MemorySegment first = ioControl.getIOCB();
      MemorySegment second = ioControl.getIOCB();

      assertNotNull(first);
      assertNotNull(second);
      assertEquals(2, ioControl.used());

      ioControl.putIOCB(first);
      assertEquals(1, ioControl.used());

      ioControl.putIOCB(second);
      assertEquals(0, ioControl.used());
   }

   @Test
   void getIOCBShouldWrapAround() {
      for (int i = 0; i < 8; i++) {
         assertNotNull(ioControl.getIOCB());
      }

      for (int i = 0; i < 8; i++) {
         ioControl.putIOCB(ioControl.iocbPool()[i]);
      }

      assertEquals(0, ioControl.used());
      assertEquals(0, ioControl.iocbGet());
      assertEquals(0, ioControl.iocbPut());

      MemorySegment again = ioControl.getIOCB();
      assertNotNull(again);
      assertEquals(1, ioControl.used());
      assertEquals(1, ioControl.iocbGet());
   }

   @Test
   void putIOCBShouldIgnoreNullAndInvalidSegments() {
      assertDoesNotThrow(() -> ioControl.putIOCB(null));
      assertDoesNotThrow(() -> ioControl.putIOCB(MemorySegment.NULL));
      assertEquals(0, ioControl.used());
   }

   @Test
   void getIOCBShouldReturnNullWhenPoolIsEmpty() {
      for (int i = 0; i < 8; i++) {
         assertNotNull(ioControl.getIOCB());
      }

      assertNull(ioControl.getIOCB());
      assertEquals(8, ioControl.used());
   }

   @Test
   void concurrentGetAndPutShouldPreserveInvariant() throws Exception {
      final int threads = 8;
      final int iterationsPerThread = 5_000;

      ExecutorService executor = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      List<Callable<Void>> tasks = new ArrayList<>();

      for (int t = 0; t < threads; t++) {
         tasks.add(() -> {
            start.await();

            for (int i = 0; i < iterationsPerThread; i++) {
               MemorySegment iocb = ioControl.getIOCB();
               if (iocb != null) {
                  ioControl.putIOCB(iocb);
               }
            }
            return null;
         });
      }

      try {
         List<Future<Void>> futures = new ArrayList<>();
         for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
         }

         start.countDown();

         for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
         }

         assertTrue(ioControl.isValid());
         assertEquals(0, ioControl.used());
         assertEquals(0, ioControl.iocbGet());
         assertEquals(0, ioControl.iocbPut());

         MemorySegment[] pool = ioControl.iocbPool();
         assertNotNull(pool);
         assertEquals(8, pool.length);

         Set<Long> addresses = new HashSet<>();
         for (MemorySegment seg : pool) {
            assertNotNull(seg);
            assertTrue(seg.address() != 0L);
            addresses.add(seg.address());
         }
         assertEquals(8, addresses.size());
      } finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
   }

   @Test
   void concurrentGetShouldNeverReturnSameIOCBTwiceWithoutPut() throws Exception {
      final int threads = 8;
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);

      try {
         List<Future<MemorySegment>> futures = new ArrayList<>();
         for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
               start.await();
               return ioControl.getIOCB();
            }));
         }

         start.countDown();

         Set<Long> addresses = new HashSet<>();
         int nonNullCount = 0;

         for (Future<MemorySegment> future : futures) {
            MemorySegment seg = future.get(10, TimeUnit.SECONDS);
            if (seg != null) {
               nonNullCount++;
               addresses.add(seg.address());
            }
         }

         assertEquals(nonNullCount, addresses.size());
         assertTrue(ioControl.used() <= ioControl.queueSize());
         assertTrue(ioControl.isValid());
      } finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
   }

   @Test
   void concurrentPutShouldBeSafeAfterPreallocation() throws Exception {
      MemorySegment[] taken = new MemorySegment[8];
      for (int i = 0; i < 8; i++) {
         taken[i] = ioControl.getIOCB();
         assertNotNull(taken[i]);
      }
      assertEquals(8, ioControl.used());

      final int threads = 8;
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);

      try {
         List<Future<Void>> futures = new ArrayList<>();
         for (int i = 0; i < threads; i++) {
            final MemorySegment seg = taken[i];
            futures.add(executor.submit(() -> {
               start.await();
               ioControl.putIOCB(seg);
               return null;
            }));
         }

         start.countDown();

         for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
         }

         assertEquals(0, ioControl.used());
         assertEquals(0, ioControl.iocbGet());
         assertEquals(0, ioControl.iocbPut());
         assertTrue(ioControl.isValid());
      } finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
   }
}
