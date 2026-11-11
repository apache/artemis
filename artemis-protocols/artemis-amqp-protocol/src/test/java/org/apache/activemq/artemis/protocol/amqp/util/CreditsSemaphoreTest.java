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
package org.apache.activemq.artemis.protocol.amqp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class CreditsSemaphoreTest {

   final CreditsSemaphore semaphore = new CreditsSemaphore(10);

   final AtomicInteger errors = new AtomicInteger(0);

   final AtomicInteger acquired = new AtomicInteger(0);

   final CountDownLatch waiting = new CountDownLatch(1);

   Thread thread = new Thread(() -> {
      try {
         for (int i = 0; i < 12; i++) {
            if (!semaphore.tryAcquire()) {
               waiting.countDown();
               semaphore.acquire();
            }
            acquired.incrementAndGet();
         }
      } catch (Throwable e) {
         e.printStackTrace();
         errors.incrementAndGet();
      }
   });

   @Test
   public void testSetAndRelease() throws Exception {
      thread.start();

      // 5 seconds would be an eternity here
      assertTrue(waiting.await(5, TimeUnit.SECONDS));

      assertEquals(0, semaphore.getCredits());

      validateQueuedThreads();

      semaphore.setCredits(2);

      thread.join();

      assertEquals(12, acquired.get());

      assertFalse(semaphore.hasQueuedThreads());
   }

   private void validateQueuedThreads() throws InterruptedException {
      boolean hasQueueThreads = false;
      long timeout = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < timeout) {

         if (semaphore.hasQueuedThreads()) {
            hasQueueThreads = true;
            break;
         }
         Thread.sleep(10);
      }

      assertTrue(hasQueueThreads);
   }

   @Test
   public void testDownAndUp() throws Exception {
      thread.start();

      // 5 seconds would be an eternity here
      assertTrue(waiting.await(5, TimeUnit.SECONDS));

      assertEquals(0, semaphore.getCredits());

      // TODO: Wait.assertTrue is not available at this package. So, this is making what we would be doing with a Wait Clause
      //       we could replace this next block with a Wait clause on hasQueuedThreads
      int i = 0;
      for (i = 0; i < 1000 && !semaphore.hasQueuedThreads(); i++) {
         Thread.sleep(10);
      }

      assertTrue(i < 1000);

      semaphore.release(2);

      thread.join();

      assertEquals(12, acquired.get());

      assertFalse(semaphore.hasQueuedThreads());
   }

   @Test
   public void testStartedZeroedSetLater() throws Exception {
      semaphore.setCredits(0);

      thread.start();

      // 5 seconds would be an eternity here
      assertTrue(waiting.await(5, TimeUnit.SECONDS));

      assertEquals(0, semaphore.getCredits());

      validateQueuedThreads();

      assertEquals(0, acquired.get());

      semaphore.setCredits(12);

      thread.join();

      assertEquals(12, acquired.get());

      assertFalse(semaphore.hasQueuedThreads());
   }

}
