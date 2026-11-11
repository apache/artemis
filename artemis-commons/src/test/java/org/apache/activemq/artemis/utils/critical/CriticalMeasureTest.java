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

package org.apache.activemq.artemis.utils.critical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

public class CriticalMeasureTest {

   @Test
   public void testCriticalMeasure() throws Exception {
      CriticalMeasure measure = new CriticalMeasure(null, 1);
      long time = System.nanoTime();
      measure.timeEnter = time - TimeUnit.SECONDS.toNanos(5);
      assertFalse(measure.checkExpiration(TimeUnit.SECONDS.toNanos(30), false));
   }

   @Test
   public void testCriticalMeasureTakingLongButSucceeding() throws Exception {
      CriticalAnalyzer analyzer = new CriticalAnalyzerImpl();
      CriticalComponent component = new CriticalComponentImpl(analyzer, 5);
      CriticalMeasure measure = new CriticalMeasure(component, 1);
      long time = System.nanoTime();
      measure.enterCritical();
      measure.timeEnter = time - TimeUnit.MINUTES.toNanos(30);
      measure.leaveCritical();
      assertFalse(measure.checkExpiration(TimeUnit.SECONDS.toNanos(30), false));
   }

   @Test
   public void testCriticalFailure() throws Exception {
      CriticalAnalyzer analyzer = new CriticalAnalyzerImpl();
      CriticalComponent component = new CriticalComponentImpl(analyzer, 5);
      CriticalMeasure measure = new CriticalMeasure(component, 1);
      long time = System.nanoTime();
      AutoCloseable closeable = measure.measure();
      measure.timeEnter = time - TimeUnit.MINUTES.toNanos(5);
      assertTrue(measure.checkExpiration(TimeUnit.SECONDS.toNanos(30), false)); // on this call we should had a reset before
      // subsequent call without reset should still fail
      assertTrue(measure.checkExpiration(TimeUnit.SECONDS.toNanos(30), true));
      // previous reset should have cleared it
      assertFalse(measure.checkExpiration(TimeUnit.SECONDS.toNanos(30), false));
      closeable.close();
   }

   @Test
   public void testWithCloseable() throws Exception {
      CriticalAnalyzer analyzer = new CriticalAnalyzerImpl();
      CriticalComponent component = new CriticalComponentImpl(analyzer, 5);
      try (AutoCloseable theMeasure = component.measureCritical(0)) {
         LockSupport.parkNanos(1000);
         assertTrue(component.checkExpiration(100, false));
      }
      assertFalse(component.checkExpiration(100, false));
   }

   @Test
   public void testRace() throws Exception {
      CriticalAnalyzer analyzer = new CriticalAnalyzerImpl();
      CriticalComponent component = new CriticalComponentImpl(analyzer, 5);

      AtomicInteger errors = new AtomicInteger(0);
      AtomicBoolean running = new AtomicBoolean(true);
      Thread t = new Thread(() -> {
         long oneSecond = TimeUnit.SECONDS.toNanos(1);
         while (running.get()) {
            if (component.checkExpiration(oneSecond, false)) {
               errors.incrementAndGet();
            }
         }
      });
      t.start();
      try {
         long timeRunning = System.currentTimeMillis() + 100;
         while (timeRunning > System.currentTimeMillis()) {
            try (AutoCloseable theMeasure = component.measureCritical(0)) {
               LockSupport.parkNanos(1);
            }
         }
      } finally {
         running.set(false);
      }
      t.join(1000);
      assertFalse(t.isAlive());
      assertEquals(0, errors.get());
   }
}
