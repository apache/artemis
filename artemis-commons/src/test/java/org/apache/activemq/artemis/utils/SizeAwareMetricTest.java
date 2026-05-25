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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class SizeAwareMetricTest {

   ExecutorService executor;

   private void setupExecutor(int threads) {
      if (executor == null) {
         executor = Executors.newFixedThreadPool(threads);
      }
   }

   @AfterEach
   public void shutdownExecutor() throws Exception {
      if (executor != null) {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
         executor = null;
      }
   }

   @Test
   public void testWithParent() {
      AtomicBoolean childBoolean = new AtomicBoolean(false);
      AtomicBoolean parentBoolean = new AtomicBoolean(false);

      SizeAwareMetric child = new SizeAwareMetric(5, 5, 2, 2);
      SizeAwareMetric parent = new SizeAwareMetric(10, 10, 15, 15);

      child.setOnSizeCallback(parent::addSize);
      child.setOverCallback(() -> childBoolean.set(true));
      child.setUnderCallback(() -> childBoolean.set(false));

      parent.setOverCallback(() -> parentBoolean.set(true));
      parent.setUnderCallback(() -> parentBoolean.set(false));

      for (int i = 0; i < 4; i++) {
         child.addSize(1, true);
      }

      assertEquals(4, child.getSize());
      assertEquals(4, parent.getSize());
      assertEquals(0, child.getElements());
      assertEquals(0, parent.getElements());
      assertFalse(childBoolean.get());
      assertFalse(parentBoolean.get());

      child.addSize(1, true);
      assertEquals(5, child.getSize());
      assertTrue(childBoolean.get());
      assertFalse(parentBoolean.get());
      assertEquals(0, child.getElements());
      assertEquals(0, parent.getElements());


      child.addSize(-5, true);

      assertEquals(0, child.getSize());
      assertEquals(0, parent.getSize());


      for (int i = 0; i < 5; i++) {
         child.addSize(1, false);
      }

      assertEquals(5, child.getSize());
      assertEquals(5, parent.getSize());
      assertEquals(5, child.getElements());
      assertEquals(5, parent.getElements());
      assertTrue(childBoolean.get());
      assertFalse(parentBoolean.get());
      assertTrue(child.isOverElements());

      for (int i = 0; i < 5; i++) {
         child.addSize(1, false);
      }

      assertEquals(10, child.getSize());
      assertEquals(10, parent.getSize());
      assertEquals(10, child.getElements());
      assertEquals(10, parent.getElements());

      assertTrue(childBoolean.get());
      assertTrue(parentBoolean.get());
      assertTrue(child.isOverElements());
      assertFalse(parent.isOverElements());
      assertTrue(parent.isOverSize());

   }

   @Test
   public void testMultipleSizeAdd() throws Exception {
      final int THREADS = 10;
      final int ELEMENTS = 1000;
      setupExecutor(THREADS);

      final AtomicInteger errors = new AtomicInteger(0);

      final AtomicInteger globalMetricOverCalls = new AtomicInteger(0);
      final AtomicInteger metricOverCalls = new AtomicInteger(0);
      final AtomicInteger globalMetricUnderCalls = new AtomicInteger(0);
      final AtomicInteger metricUnderCalls = new AtomicInteger(0);

      final AtomicBoolean globalMetricOver = new AtomicBoolean(false);
      final AtomicBoolean metricOver = new AtomicBoolean(false);

      SizeAwareMetric metric = new SizeAwareMetric(1000, 500, -1, -1);
      SizeAwareMetric globalMetric = new SizeAwareMetric(10000, 500, -1, -1);

      metric.setOnSizeCallback(globalMetric::addSize);
      metric.setOverCallback(() -> {
         metricOver.set(true);
         metricOverCalls.incrementAndGet();
      });
      metric.setUnderCallback(() -> {
         metricOver.set(false);
         metricUnderCalls.incrementAndGet();
      });

      globalMetric.setOverCallback(() -> {
         globalMetricOver.set(true);
         globalMetricOverCalls.incrementAndGet();
      });
      globalMetric.setUnderCallback(() -> {
         globalMetricOver.set(false);
         globalMetricOverCalls.incrementAndGet();
      });

      ReusableLatch latchDone = new ReusableLatch(THREADS);

      CyclicBarrier flagStart = new CyclicBarrier(THREADS + 1);
      for (int istart = 0; istart < THREADS; istart++) {
         executor.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);

               for (int iadd = 0; iadd < ELEMENTS; iadd++) {
                  metric.addSize(1, false);
               }
               latchDone.countDown();
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });
      }

      flagStart.await(10, TimeUnit.SECONDS);
      assertTrue(latchDone.await(10, TimeUnit.SECONDS));

      assertTrue(metricOver.get());
      assertTrue(metric.isOver());
      assertTrue(metric.isOverSize());
      assertFalse(metric.isOverElements());

      assertTrue(globalMetricOver.get());
      assertTrue(globalMetric.isOver());

      assertEquals(1, metricOverCalls.get());
      assertEquals(1, globalMetricOverCalls.get());
      assertEquals(0, metricUnderCalls.get());
      assertEquals(0, globalMetricUnderCalls.get());

      assertEquals(ELEMENTS * THREADS, metric.getSize());
      assertEquals(ELEMENTS * THREADS, metric.getElements());
      assertEquals(ELEMENTS * THREADS, globalMetric.getSize());
      assertEquals(ELEMENTS * THREADS, globalMetric.getElements());

      assertEquals(0, errors.get());

      latchDone.setCount(10);

      for (int istart = 0; istart < 10; istart++) {
         executor.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);

               for (int iadd = 0; iadd < ELEMENTS; iadd++) {
                  metric.addSize(-1, false);
               }
               latchDone.countDown();
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });
      }

      flagStart.await(10, TimeUnit.SECONDS);
      assertTrue(latchDone.await(10, TimeUnit.SECONDS));

      assertEquals(0, globalMetric.getSize());
      assertEquals(0, globalMetric.getElements());
      assertEquals(0, metric.getSize());
      assertEquals(0, metric.getElements());
      assertFalse(globalMetricOver.get());
      assertFalse(globalMetric.isOver());
   }


   @Test
   public void testMaxElements() {
      SizeAwareMetric metric = new SizeAwareMetric(10000, 500, 10, 10);

      AtomicBoolean over = new AtomicBoolean(false);
      metric.setOverCallback(() -> over.set(true));
      metric.setUnderCallback(() -> over.set(false));

      for (int i = 0; i < 11; i++) {
         metric.addSize(10, false);
      }

      assertTrue(over.get());
      assertEquals(110, metric.getSize());
      assertEquals(11, metric.getElements());
      metric.addSize(1000, false);

      for (int i = 0; i < 12; i++) {
         metric.addSize(-10, false);
      }

      assertFalse(over.get());

   }
   @Test
   public void testMaxElementsReleaseNonSizeParentMetric() {
      SizeAwareMetric metricMain = new SizeAwareMetric(10000, 500, 10, 10);
      SizeAwareMetric metric = new SizeAwareMetric(10000, 500, 1000, 1000);

      metric.setOnSizeCallback(metricMain::addSize);

      AtomicBoolean over = new AtomicBoolean(false);
      metricMain.setOverCallback(() -> over.set(true));
      metricMain.setUnderCallback(() -> over.set(false));

      for (int i = 0; i < 11; i++) {
         metric.addSize(10);
      }
      metric.addSize(1000, true);

      assertEquals(1110L, metricMain.getSize());
      assertEquals(11, metricMain.getElements());
      assertEquals(1110L, metric.getSize());
      assertEquals(11, metric.getElements());
      assertTrue(metricMain.isOverElements());
      assertFalse(metricMain.isOverSize());
      assertFalse(metric.isOverElements());
      assertFalse(metric.isOverSize());
      assertTrue(over.get());

      metric.addSize(-1000, true);

      assertEquals(110L, metricMain.getSize());
      assertEquals(11, metricMain.getElements());
      assertTrue(metricMain.isOverElements());
      assertFalse(metricMain.isOverSize());
      assertTrue(over.get());

      for (int i = 0; i < 11; i++) {
         metric.addSize(-10);
      }

      assertEquals(0L, metricMain.getSize());
      assertEquals(0L, metricMain.getElements());
      assertFalse(metricMain.isOver());
      assertEquals(0L, metric.getSize());
      assertEquals(0L, metric.getElements());
      assertFalse(metric.isOver());
      assertFalse(over.get());
   }


   @Test
   public void testMaxElementsReleaseNonSize() {
      SizeAwareMetric metric = new SizeAwareMetric(10000, 500, 10, 10);

      AtomicBoolean over = new AtomicBoolean(false);
      metric.setOverCallback(() -> over.set(true));
      metric.setUnderCallback(() -> over.set(false));

      for (int i = 0; i < 11; i++) {
         metric.addSize(10);
      }
      metric.addSize(1000, true);

      assertEquals(1110L, metric.getSize());
      assertEquals(11, metric.getElements());
      assertTrue(metric.isOverElements());
      assertFalse(metric.isOverSize());
      assertTrue(over.get());

      metric.addSize(-1000, true);

      assertEquals(110L, metric.getSize());
      assertEquals(11, metric.getElements());
      assertTrue(metric.isOverElements());
      assertFalse(metric.isOverSize());
      assertTrue(over.get());

      for (int i = 0; i < 11; i++) {
         metric.addSize(-10);
      }

      assertEquals(0L, metric.getSize());
      assertEquals(0L, metric.getElements());
      assertFalse(metric.isOver());
      assertFalse(over.get());
   }

   @Test
   public void testMultipleSizeAddMultipleInstances() throws Exception {
      final int THREADS = 10, ELEMENTS = 1000;
      setupExecutor(THREADS);

      final AtomicInteger errors = new AtomicInteger(0);

      final AtomicInteger globalMetricOverCalls = new AtomicInteger(0);
      final AtomicInteger metricOverCalls = new AtomicInteger(0);
      final AtomicInteger globalMetricUnderCalls = new AtomicInteger(0);
      final AtomicInteger metricUnderCalls = new AtomicInteger(0);

      final AtomicBoolean globalMetricOver = new AtomicBoolean(false);
      final AtomicBoolean[] metricOverArray = new AtomicBoolean[THREADS];

      SizeAwareMetric globalMetric = new SizeAwareMetric(10000, 500, 10000, 500);

      SizeAwareMetric[] metric = new SizeAwareMetric[THREADS];


      globalMetric.setOverCallback(() -> {
         globalMetricOver.set(true);
         globalMetricOverCalls.incrementAndGet();
      });
      globalMetric.setUnderCallback(() -> {
         globalMetricOver.set(false);
         globalMetricUnderCalls.incrementAndGet();
      });

      ReusableLatch latchDone = new ReusableLatch(THREADS);

      CyclicBarrier flagStart = new CyclicBarrier(THREADS + 1);
      for (int istart = 0; istart < THREADS; istart++) {
         final AtomicBoolean metricOver = new AtomicBoolean(false);
         final SizeAwareMetric theMetric = new SizeAwareMetric(1000, 500, 1000, 500);
         theMetric.setOnSizeCallback(globalMetric::addSize);
         theMetric.setOverCallback(() -> {
            metricOver.set(true);
            metricOverCalls.incrementAndGet();
         });
         theMetric.setUnderCallback(() -> {
            metricOver.set(false);
            metricUnderCalls.incrementAndGet();
         });
         metric[istart] = theMetric;
         metricOverArray[istart] = metricOver;
         executor.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);

               for (int iadd = 0; iadd < ELEMENTS; iadd++) {
                  theMetric.addSize(1);
               }
               latchDone.countDown();
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });
      }

      flagStart.await(10, TimeUnit.SECONDS);
      assertTrue(latchDone.await(10, TimeUnit.SECONDS));

      for (SizeAwareMetric theMetric : metric) {
         assertTrue(theMetric.isOver());
         assertEquals(ELEMENTS, theMetric.getSize());
         assertEquals(ELEMENTS, theMetric.getElements());
      }

      for (AtomicBoolean theBool : metricOverArray) {
         assertTrue(theBool.get());
      }

      assertTrue(globalMetricOver.get());
      assertTrue(globalMetric.isOver());

      assertEquals(10, metricOverCalls.get());
      assertEquals(1, globalMetricOverCalls.get());
      assertEquals(0, metricUnderCalls.get());
      assertEquals(0, globalMetricUnderCalls.get());

      assertEquals(ELEMENTS * THREADS, globalMetric.getSize());
      assertEquals(ELEMENTS * THREADS, globalMetric.getElements());

      assertEquals(0, errors.get());

      latchDone.setCount(10);

      for (int istart = 0; istart < 10; istart++) {
         SizeAwareMetric theMetric = metric[istart];
         executor.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);

               for (int iadd = 0; iadd < ELEMENTS; iadd++) {
                  theMetric.addSize(-1);
               }
               latchDone.countDown();
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         });
      }

      flagStart.await(10, TimeUnit.SECONDS);
      assertTrue(latchDone.await(10, TimeUnit.SECONDS));

      assertEquals(0, globalMetric.getSize());
      assertEquals(0, globalMetric.getElements());
      for (SizeAwareMetric theMetric : metric) {
         assertEquals(0, theMetric.getSize());
         assertEquals(0, theMetric.getElements());
      }

      assertEquals(10, metricOverCalls.get());
      assertEquals(1, globalMetricOverCalls.get());
      assertEquals(10, metricUnderCalls.get());
      assertEquals(1, globalMetricUnderCalls.get());
      assertFalse(globalMetricOver.get());
      assertFalse(globalMetric.isOver());
      for (AtomicBoolean theBool : metricOverArray) {
         assertFalse(theBool.get());
      }
   }

   @Test
   public void testUpdateMax() {
      AtomicBoolean over = new AtomicBoolean(false);
      SizeAwareMetric metric = new SizeAwareMetric(1000, 500, -1, -1);
      metric.setOverCallback(() -> over.set(true));
      metric.setUnderCallback(() -> over.set(false));

      metric.addSize(900);
      assertFalse(over.get());

      metric.setMax(800, 700, -1, -1);
      assertTrue(over.get());

      metric.addSize(-201);
      assertFalse(over.get());
   }

   @Test
   public void testDisabled() {
      AtomicBoolean over = new AtomicBoolean(false);
      SizeAwareMetric metric = new SizeAwareMetric(-1, -1, -1, -1);
      metric.setOverCallback(() -> over.set(true));
      metric.addSize(100);

      assertEquals(100, metric.getSize());
      assertEquals(1, metric.getElements());
      assertFalse(over.get());
   }

   @Test
   public void testMultipleNonSized() {
      AtomicBoolean over = new AtomicBoolean(false);
      final SizeAwareMetric metricMain = new SizeAwareMetric(-1, -1, -1, -1);
      SizeAwareMetric metric = new SizeAwareMetric(-1, -1, -1, -1);

      metric.setOverCallback(() -> over.set(true));
      metric.setOnSizeCallback(metricMain::addSize);
      for (int i = 0; i < 10; i++) {
         metric.addSize(10, true);
      }

      assertEquals(100, metricMain.getSize());
      assertEquals(100, metric.getSize());
      assertEquals(0, metricMain.getElements());
      assertEquals(0, metric.getElements());

      for (int i = 0; i < 10; i++) {
         metric.addSize(10, false);
      }

      assertEquals(200, metricMain.getSize());
      assertEquals(200, metric.getSize());
      assertEquals(10, metricMain.getElements());
      assertEquals(10, metric.getElements());

      assertFalse(over.get());
   }

   @Test
   public void testResetNeverUsed() {
      AtomicBoolean over = new AtomicBoolean(false);

      SizeAwareMetric metric = new SizeAwareMetric(0, 0, 0, 0);
      metric.setOverCallback(() -> over.set(true));
      metric.setMax(0, 0, 0, 0);

      assertFalse(over.get());
      assertFalse(metric.isOver());
   }

   @Test
   public void testSwitchSides() {
      SizeAwareMetric metric = new SizeAwareMetric(2000, 2000, 1, 1);
      AtomicBoolean over = new AtomicBoolean(false);

      metric.setOverCallback(() -> over.set(true));
      metric.setUnderCallback(() -> over.set(false));

      metric.addSize(2500, true);

      assertTrue(over.get());

      metric.addSize(1000);

      assertTrue(metric.isOverSize());

      metric.addSize(-2500, true);

      // Even though we are free from maxSize, we are still bound by maxElements, it should still be over
      assertTrue(over.get());
      assertTrue(metric.isOverElements(), "Switch did not work");

      assertEquals(1, metric.getElements());
      assertEquals(1000, metric.getSize());

      metric.addSize(5000, true);

      assertTrue(metric.isOverElements());
      assertEquals(6000, metric.getSize());

      metric.addSize(-1000);

      assertTrue(metric.isOverSize());
      assertEquals(0, metric.getElements());
      assertEquals(5000, metric.getSize());

      metric.addSize(-5000, true);
      assertFalse(metric.isOver());
      assertEquals(0, metric.getSize());
      assertEquals(0, metric.getElements());
   }


   @Test
   public void testMTOverAndUnder() throws Exception {
      final int THREADS = 10;
      final int ELEMENTS = 100;
      setupExecutor(THREADS * 2);

      SizeAwareMetric metric = new SizeAwareMetric(1, 1, -1, -1);
      AtomicInteger overCounter = new AtomicInteger(0);
      AtomicInteger errors = new AtomicInteger(0);
      metric.setOverCallback(() -> {
         int value = overCounter.incrementAndGet();
         if (value > 1) {
            new Exception("Value = " + value).printStackTrace();
            errors.incrementAndGet();
         }
      });
      metric.setUnderCallback(() -> {
         int value = overCounter.decrementAndGet();
         if (value < 0) {
            new Exception("Value = " + value).printStackTrace();
            errors.incrementAndGet();
         }
      });

      CyclicBarrier flagStart = new CyclicBarrier(THREADS * 2);

      CountDownLatch done = new CountDownLatch(THREADS * 2);

      for (int i = 0; i < THREADS; i++) {
         executor.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);
               for (int repeat = 0; repeat < ELEMENTS; repeat++) {
                  metric.addSize(1);
               }
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
            done.countDown();
         });
         executor.execute(() -> {
            try {
               flagStart.await(10, TimeUnit.SECONDS);
               for (int repeat = 0; repeat < ELEMENTS; repeat++) {
                  metric.addSize(-1);
               }
            } catch (Throwable e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
            done.countDown();
         });
      }

      assertTrue(done.await(10, TimeUnit.SECONDS));

      assertEquals(0, metric.getSize());
      assertEquals(0, metric.getElements());
      assertEquals(0, errors.get());
   }

   @Test
   public void testConsistency() {
      SizeAwareMetric metric = new SizeAwareMetric(-1, -1, -1, -1);
      assertFalse(metric.isSizeEnabled());
      assertFalse(metric.isElementsEnabled());
      metric.setMax(1, 1, 1, 1);
      assertTrue(metric.isSizeEnabled());
      assertTrue(metric.isElementsEnabled());
   }
}
