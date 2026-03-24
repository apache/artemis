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
package org.apache.activemq.artemis.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.utils.TimeAndCounterIDGenerator;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.junit.Assert;
import org.junit.Test;

public class TimeAndCounterIDGeneratorTest extends Assert {

   @Test
   public void testCalculation() {
      TimeAndCounterIDGenerator seq = new TimeAndCounterIDGenerator();
      long max = 11000;

      long lastNr = 0;

      for (long i = 0; i < max; i++) {
         long seqNr = seq.generateID();

         Assert.assertTrue("The sequence generator should aways generate crescent numbers", seqNr > lastNr);

         lastNr = seqNr;
      }

   }

   @Test
   public void testCalculationRefresh() {
      TimeAndCounterIDGenerator seq = new TimeAndCounterIDGenerator();

      long id1 = seq.generateID();
      Assert.assertEquals(1, id1 & 0xffff);
      Assert.assertEquals(2, seq.generateID() & 0xffff);

      seq.refresh();

      long id2 = seq.generateID();

      Assert.assertTrue(id2 > id1);

      Assert.assertEquals(1, id2 & 0xffff);

   }

   @Test
   public void testCalculationOnMultiThread() throws Throwable {
      final ConcurrentHashSet<Long> hashSet = new ConcurrentHashSet<>();

      final TimeAndCounterIDGenerator seq = new TimeAndCounterIDGenerator();

      final int NUMBER_OF_THREADS = 100;

      final int NUMBER_OF_IDS = 10;

      final CountDownLatch latchAlign = new CountDownLatch(NUMBER_OF_THREADS);

      final CountDownLatch latchStart = new CountDownLatch(1);

      class T1 extends Thread {

         Throwable e;

         @Override
         public void run() {
            try {
               latchAlign.countDown();
               assertTrue("Latch has got to return within a minute", latchStart.await(1, TimeUnit.MINUTES));

               long lastValue = 0L;
               for (int i = 0; i < NUMBER_OF_IDS; i++) {
                  long value = seq.generateID();
                  Assert.assertTrue(TimeAndCounterIDGeneratorTest.hex(value) + " should be greater than " +
                                       TimeAndCounterIDGeneratorTest.hex(lastValue) +
                                       " on seq " +
                                       seq.toString(), value > lastValue);
                  lastValue = value;

                  hashSet.add(value);
               }
            } catch (Throwable e) {
               this.e = e;
            }
         }

      }

      T1[] arrays = new T1[NUMBER_OF_THREADS];

      for (int i = 0; i < arrays.length; i++) {
         arrays[i] = new T1();
         arrays[i].start();
      }

      assertTrue("Latch has got to return within a minute", latchAlign.await(1, TimeUnit.MINUTES));

      latchStart.countDown();

      for (T1 t : arrays) {
         t.join();
         if (t.e != null) {
            throw t.e;
         }
      }

      Assert.assertEquals(NUMBER_OF_THREADS * NUMBER_OF_IDS, hashSet.size());

      hashSet.clear();

   }

   @Test
   public void testWrapID() throws Throwable {
      TimeAndCounterIDGenerator seq = new TimeAndCounterIDGenerator();

      seq.setInternalDate(System.currentTimeMillis() + 10000L); // 10 seconds in the future

      seq.setInternalID(TimeAndCounterIDGenerator.ID_MASK); // 1 ID about to explode

      try {
         // This is simulating a situation where we generated more than 268 million messages on the same time interval
         seq.generateID();
         Assert.fail("It was supposed to throw an exception, as the counter was set to explode on this test");
      } catch (Exception e) {
      }

      seq = new TimeAndCounterIDGenerator();

      seq.setInternalDate(System.currentTimeMillis() - 10000L); // 10 seconds in the past

      long timeMark = seq.getInternalTimeMark();

      seq.setInternalID(TimeAndCounterIDGenerator.ID_MASK); // 1 ID about to explode

      // This is ok... the time portion would be added to the next one generated 10 seconds ago
      seq.generateID();

      Assert.assertTrue(TimeAndCounterIDGeneratorTest.hex(timeMark) + " < " +
                           TimeAndCounterIDGeneratorTest.hex(seq.getInternalTimeMark()), timeMark < seq.getInternalTimeMark());
   }

   private static String hex(final long value) {
      return String.format("%1$X", value);
   }

}
