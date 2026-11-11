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
package org.apache.activemq.artemis.utils.actors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class OrderedExecutorSanityTest {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void shouldExecuteTasksInOrder() throws InterruptedException {
      final int threads = 3;
      final int tasks = 100;
      final long timeoutMillis = TimeUnit.SECONDS.toMillis(10);
      final ExecutorService executorService = Executors.newFixedThreadPool(threads);
      try {
         final ArtemisExecutor executor = new OrderedExecutor(executorService);
         //it can be not thread safe too
         final List<Integer> results = new ArrayList<>(tasks);
         final List<Integer> expectedResults = new ArrayList<>(tasks);
         final CountDownLatch executed = new CountDownLatch(tasks);
         for (int i = 0; i < tasks; i++) {
            final int value = i;
            executor.execute(() -> {
               results.add(value);
               executed.countDown();
            });
            expectedResults.add(value);
         }
         assertTrue(executed.await(timeoutMillis, TimeUnit.MILLISECONDS), "The tasks must be executed in " + timeoutMillis + " ms");
         assertArrayEquals(expectedResults.toArray(), results.toArray(), "The processing of tasks must be ordered");
      } finally {
         executorService.shutdown();
      }
   }

   @Test
   public void shouldShutdownNowDoNotExecuteFurtherTasks() throws InterruptedException {
      final long timeoutMillis = TimeUnit.SECONDS.toMillis(10);
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      try {
         final OrderedExecutor executor = new OrderedExecutor(executorService);
         final CountDownLatch executed = new CountDownLatch(1);
         executor.execute(executed::countDown);
         assertTrue(executed.await(timeoutMillis, TimeUnit.MILLISECONDS), "The task must be executed in " + timeoutMillis + " ms");
         executor.shutdownNow();
         assertEquals(0, executor.remaining(), "There are no remaining tasks to be executed");
         //from now on new tasks won't be executed
         executor.execute(() -> System.out.println("this will never happen"));
         //to avoid memory leaks the executor must take care of the new submitted tasks immediatly
         assertEquals(0, executor.remaining(), "Any new task submitted after death must be collected");
      } finally {
         executorService.shutdown();
      }
   }



   @Test
   public void shutdownNowOnDelegateExecutor() throws Exception {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      try {
         final OrderedExecutor executor = new OrderedExecutor(executorService);
         final CyclicBarrier latch = new CyclicBarrier(2);
         final AtomicInteger numberOfTasks = new AtomicInteger(0);
         final CountDownLatch ran = new CountDownLatch(1);

         executor.execute(() -> {
            try {
               latch.await(1, TimeUnit.MINUTES);
               numberOfTasks.set(executor.shutdownNow());
               ran.countDown();
            } catch (Exception e) {
               e.printStackTrace();
            }
         });


         for (int i = 0; i < 100; i++) {
            executor.execute(() -> System.out.println("Dont worry, this will never happen"));
         }

         latch.await();
         ran.await(1, TimeUnit.SECONDS);
         assertEquals(100, numberOfTasks.get());

         assertEquals(ProcessorBase.STATE_FORCED_SHUTDOWN, executor.status());
         assertEquals(0, executor.remaining());
      } finally {
         executorService.shutdown();
      }
   }

   @Test
   public void shutdownNowWithBlocked() throws Exception {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      try {
         final OrderedExecutor executor = new OrderedExecutor(executorService);
         final CyclicBarrier latch = new CyclicBarrier(2);
         final CyclicBarrier secondlatch = new CyclicBarrier(2);
         final CountDownLatch ran = new CountDownLatch(1);

         executor.execute(() -> {
            try {
               latch.await(1, TimeUnit.MINUTES);
               secondlatch.await(1, TimeUnit.MINUTES);
               ran.countDown();
            } catch (Exception e) {
               e.printStackTrace();
            }
         });


         for (int i = 0; i < 100; i++) {
            executor.execute(() -> System.out.println("Dont worry, this will never happen"));
         }

         latch.await();
         try {
            assertEquals(100, executor.shutdownNow());
         } finally {
            secondlatch.await();
         }

         assertEquals(ProcessorBase.STATE_FORCED_SHUTDOWN, executor.status());
         assertEquals(0, executor.remaining());
      } finally {
         executorService.shutdown();
      }
   }


   @Test
   public void testMeasure() throws InterruptedException {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      try {
         final OrderedExecutor executor = new OrderedExecutor(executorService);
         int MAX_LOOP = 1_000_000;

         // extend the number for longer numbers
         int runs = 10;

         for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            final CountDownLatch executed = new CountDownLatch(MAX_LOOP);
            for (int l = 0; l < MAX_LOOP; l++) {
               executor.execute(executed::countDown);
            }
            assertTrue(executed.await(1, TimeUnit.MINUTES));
            long end = System.nanoTime();

            long elapsed = (end - start);

            logger.info("execution {} in {} milliseconds", i, TimeUnit.NANOSECONDS.toMillis(elapsed));
         }
      } finally {
         executorService.shutdown();
      }
   }


   @Test
   public void testFair() throws InterruptedException {
      AtomicInteger errors = new AtomicInteger(0);
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      try {
         final ArtemisExecutor executor = new OrderedExecutor(executorService).setFair(true);
         final ArtemisExecutor executor2 = new OrderedExecutor(executorService).setFair(true);

         CountDownLatch latchDone1 = new CountDownLatch(1);
         CountDownLatch latchBlock1 = new CountDownLatch(1);
         CountDownLatch latchDone2 = new CountDownLatch(1);
         CountDownLatch latchDone3 = new CountDownLatch(1);
         CountDownLatch latchBlock3 = new CountDownLatch(1);
         executor.execute(() -> {
            try {
               logger.info("Exec 1");
               latchDone1.countDown();
               latchBlock1.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });

         assertTrue(latchDone1.await(10, TimeUnit.SECONDS));

         executor.execute(() -> {
            try {
               // Exec 2 is supposed to yield to Exec3, so Exec3 will happen first
               logger.info("Exec 2");
               latchDone2.countDown();
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });

         executor2.execute(() -> {
            try {
               logger.info("Exec 3");
               latchDone3.countDown();
               latchBlock3.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });

         latchBlock1.countDown();
         assertTrue(latchDone3.await(10, TimeUnit.SECONDS));
         assertFalse(latchDone2.await(1, TimeUnit.MILLISECONDS));
         latchBlock3.countDown();
         assertTrue(latchDone2.await(10, TimeUnit.SECONDS));
         assertEquals(0, errors.get());
      } finally {
         executorService.shutdownNow();
      }
   }


}
