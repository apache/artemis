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
package org.apache.activemq.artemis.tests.timing.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.TokenBucketLimiterImpl;
import org.junit.jupiter.api.Test;

public class TokenBucketLimiterImplTest extends ActiveMQTestBase {

   @Test
   public void testRateWithSpin1() throws Exception {
      testRate(1, true);
   }

   @Test
   public void testRateWithSpin10() throws Exception {
      testRate(10, true);
   }

   @Test
   public void testRateWithSpin100() throws Exception {
      testRate(100, true);
   }

   @Test
   public void testRateWithSpin1000() throws Exception {
      testRate(1000, true);
   }

   @Test
   public void testRateWithSpin10000() throws Exception {
      testRate(10000, true);
   }

   @Test
   public void testRateWithSpin100000() throws Exception {
      testRate(100000, true);
   }

   @Test
   public void testRateWithoutSpin1() throws Exception {
      testRate(1, false);
   }

   @Test
   public void testRateWithoutSpin10() throws Exception {
      testRate(10, false);
   }

   @Test
   public void testRateWithoutSpin100() throws Exception {
      testRate(100, false);
   }

   @Test
   public void testRateWithoutSpin1000() throws Exception {
      testRate(1000, false);
   }

   @Test
   public void testRateWithoutSpin10000() throws Exception {
      testRate(10000, false);
   }

   @Test
   public void testRateWithoutSpin100000() throws Exception {
      testRate(100000, false);
   }

   private void testRate(final int rate, final boolean spin) throws Exception {
      final double error = 0.05; // Allow for 5% error

      TokenBucketLimiterImpl tbl = new TokenBucketLimiterImpl(rate, spin);

      long start = System.currentTimeMillis();

      long count = 0;

      final long measureTime = 5000;

      // Do some initial testing ..   to ramp up the calculations
      while (System.currentTimeMillis() - start < measureTime) {
         tbl.limit();
      }

      // wait some time
      Thread.sleep(2000);

      // start measuring again
      start = System.currentTimeMillis();

      while (System.currentTimeMillis() - start < measureTime) {
         tbl.limit();

         // when using a low rate (1 for instance), the very last could come after or very close to 5 seconds
         // what could give us a false negative
         count++;
      }

      long end = System.currentTimeMillis();

      if (rate == 1) {
         // in 5 seconds you may get exactly 6 buckets..
         // Count... 1, 2, 3, 4, 5, 6
         // Time.... 0, 1, 2, 3, 4, 5

         // any variation on 1 could make the test to fail, for that reason we make it this way

         assertTrue(count == 5 || count == 6);
      } else {
         double actualRate = (double) (1000 * count) / measureTime;

         assertTrue(actualRate > rate * (1 - error), "actual rate = " + actualRate + " expected=" + rate);

         assertTrue(actualRate < rate * (1 + error), "actual rate = " + actualRate + " expected=" + rate);
      }
   }

   @Test
   public void testVerifyMaxRate1() throws Exception {
      testVerifyRate(1, 1, 5000);
   }

   @Test
   public void testVerifyMaxRate5() throws Exception {
      testVerifyRate(5, 1, 5000);
   }

   @Test
   public void testVerifyMaxRate50() throws Exception {
      testVerifyRate(50, 1, 5000);
   }

   @Test
   public void testVerifyMaxRate50_per_5seconds() throws Exception {
      testVerifyRate(50, 5, 20000);
   }

   public void testVerifyRate(final int rate, final int window, final int timeRunning) throws Exception {
      final double error = 1.05; // Allow for 5% error

      final AtomicBoolean running = new AtomicBoolean(true);

      final AtomicBoolean rateError = new AtomicBoolean(false);

      final AtomicInteger iterations = new AtomicInteger(0);

      TokenBucketLimiterImpl tbl = new TokenBucketLimiterImpl(rate, false, TimeUnit.SECONDS, window);

      Thread t = new Thread(() -> {
         int lastRun = 0;
         long lastTime = System.currentTimeMillis();
         while (running.get()) {
            int tmpValue = iterations.get();
            if (lastRun != 0) {
               int consumed = tmpValue - lastRun;

               double calculatedRate = consumed * window * 1000 / (System.currentTimeMillis() - lastTime);

               if (calculatedRate > rate * error) {
                  System.out.println("got more than " + rate + " tokens / second");
                  rateError.set(true);
               } else if (calculatedRate > rate) {
                  System.out.println("got more than " + rate + " tokens / second but still on the error marging" +
                                        "make sure it's ok though, if you see to many of this message it's an issue");
               }
               System.out.println("Rate = " + calculatedRate + " consumed = " + consumed);
            }
            lastTime = System.currentTimeMillis();
            lastRun = tmpValue;
            try {
               Thread.sleep(window * 1000);
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      });

      t.start();

      long timeout;

      timeout = System.currentTimeMillis() + 3000;

      while (timeout > System.currentTimeMillis()) {
         tbl.limit();
         iterations.incrementAndGet();
      }

      Thread.sleep(3000);

      timeout = System.currentTimeMillis() + timeRunning;

      while (timeout > System.currentTimeMillis()) {
         tbl.limit();
         iterations.incrementAndGet();
      }

      running.set(false);

      t.join();

   }
}
