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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActiveMQScheduledComponentTest extends ArtemisTestCase {

   ScheduledExecutorService scheduledExecutorService;
   ExecutorService executorService;

   @BeforeEach
   public void before() {
      scheduledExecutorService = new ScheduledThreadPoolExecutor(5);
      executorService = Executors.newSingleThreadExecutor();
   }

   @AfterEach
   public void after() {
      executorService.shutdown();
      scheduledExecutorService.shutdown();
   }

   @Test
   public void testAccumulation() throws Exception {
      final AtomicInteger count = new AtomicInteger(0);

      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(scheduledExecutorService, executorService, 100, TimeUnit.MILLISECONDS, false) {
         @Override
         public void run() {
            if (count.get() == 0) {
               try {
                  Thread.sleep(800);
               } catch (Exception e) {
               }
            }
            count.incrementAndGet();
         }
      };

      local.start();

      Thread.sleep(1000);

      local.stop();

      assertTrue(count.get() < 5, "just because one took a lot of time, it doesn't mean we can accumulate many, we got " + count + " executions");
   }

   @Test
   public void testSubMillisDelay() throws InterruptedException {
      final CountDownLatch triggered = new CountDownLatch(2);
      final long nsInterval = TimeUnit.MICROSECONDS.toNanos(900);
      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(scheduledExecutorService, executorService, nsInterval, TimeUnit.NANOSECONDS, false) {

         @Override
         public void run() {
            triggered.countDown();
         }
      };
      local.start();
      assertTrue(triggered.await(10, TimeUnit.SECONDS));
      local.stop();
   }

   @Test
   public void testVerifyInitialDelayChanged() {
      final long initialDelay = 10;
      final long period = 100;
      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(scheduledExecutorService, executorService, initialDelay, period, TimeUnit.MILLISECONDS, false) {
         @Override
         public void run() {

         }
      };
      local.start();
      final long newInitialDelay = 1000;
      //the parameters are valid?
      assertTrue(initialDelay != newInitialDelay);
      assertTrue(newInitialDelay != period);

      local.setInitialDelay(newInitialDelay);
      local.stop();
      assertEquals(newInitialDelay, local.getInitialDelay(), "the initial dalay can't change");
   }

   @Test
   public void testAccumulationOwnPool() throws Exception {
      final AtomicInteger count = new AtomicInteger(0);

      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(100, TimeUnit.MILLISECONDS, false) {
         @Override
         public void run() {
            if (count.get() == 0) {
               try {
                  Thread.sleep(500);
               } catch (Exception e) {
               }
            }
            count.incrementAndGet();
         }
      };

      local.start();

      Thread.sleep(1000);

      local.stop();

      assertTrue(count.get() <= 5 && count.get() > 0, "just because one took a lot of time, it doesn't mean we can accumulate many, we got " + count + " executions");
   }

   @Test
   public void testUsingOwnExecutors() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);

      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(10, TimeUnit.MILLISECONDS, false) {
         @Override
         public void run() {
            latch.countDown();
         }
      };

      local.start();
      local.start(); // should be ok to call start again

      try {
         assertTrue(latch.await(10, TimeUnit.SECONDS));

         // re-scheduling the executor at a big interval..
         // just to make sure it won't hung
         local.setTimeUnit(TimeUnit.HOURS);
         local.setPeriod(1);
      } finally {
         local.stop();
         local.stop(); // should be ok to call stop again
      }
   }

   @Test
   public void testUsingOwnExecutorsOnDemand() throws Throwable {
      final ReusableLatch latch = new ReusableLatch(1);

      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(10, TimeUnit.MILLISECONDS, true) {
         @Override
         public void run() {
            latch.countDown();
         }
      };

      local.start();
      local.start(); // should be ok to call start again

      try {

         assertFalse(latch.await(20, TimeUnit.MILLISECONDS));

         local.delay();
         assertTrue(latch.await(20, TimeUnit.MILLISECONDS));
         latch.setCount(1);

         assertFalse(latch.await(20, TimeUnit.MILLISECONDS));

         // re-scheduling the executor at a big interval..
         // just to make sure it won't hung
         local.setTimeUnit(TimeUnit.HOURS);
         local.setPeriod(1);
      } finally {
         local.stop();
         local.stop(); // calling stop again should not be an issue.
      }
   }


   @Test
   public void testUpdatePeriod() throws Throwable {
      final ReusableLatch latch = new ReusableLatch(1);

      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(10, TimeUnit.MILLISECONDS, true) {
         @Override
         public void run() {
            latch.countDown();
         }
      };

      local.start();

      try {
         assertFalse(latch.await(20, TimeUnit.MILLISECONDS));

         latch.setCount(1);
         local.delay();
         assertTrue(latch.await(20, TimeUnit.MILLISECONDS));

         latch.setCount(1);
         assertFalse(latch.await(20, TimeUnit.MILLISECONDS));

         local.setPeriod(TimeUnit.HOURS.toMillis(1), TimeUnit.MILLISECONDS);

         latch.setCount(1);
         local.delay();
         assertFalse(latch.await(20, TimeUnit.MILLISECONDS));

         local.setPeriod(1);
         local.delay();
         assertTrue(latch.await(20, TimeUnit.MILLISECONDS));

         local.setPeriod(1, TimeUnit.SECONDS);

         latch.setCount(1);
         local.delay();

         assertTrue(latch.await(5, TimeUnit.SECONDS));
      } finally {
         local.stop();
         local.stop(); // calling stop again should not be an issue.
      }
   }

   @Test
   public void testUsingCustomInitialDelay() throws InterruptedException {
      final CountDownLatch latch = new CountDownLatch(1);
      final long initialDelayMillis = 100;
      final long checkPeriodMillis = 100 * initialDelayMillis;
      final ActiveMQScheduledComponent local = new ActiveMQScheduledComponent(scheduledExecutorService, executorService, initialDelayMillis, checkPeriodMillis, TimeUnit.MILLISECONDS, false) {
         @Override
         public void run() {
            latch.countDown();
         }
      };
      final long start = System.nanoTime();
      local.start();
      try {
         final boolean triggeredBeforePeriod = latch.await(local.getPeriod(), local.getTimeUnit());
         final long timeToFirstTrigger = TimeUnit.NANOSECONDS.convert(System.nanoTime() - start, local.getTimeUnit());
         assertTrue(triggeredBeforePeriod, "Takes too long to start");
         assertTrue(timeToFirstTrigger >= local.getInitialDelay(), "Started too early");
      } finally {
         local.stop();
      }
   }
}
