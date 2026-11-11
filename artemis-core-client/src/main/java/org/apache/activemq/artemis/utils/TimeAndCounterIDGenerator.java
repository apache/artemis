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
package org.apache.activemq.artemis.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A TimeAndCounterIDGenerator
 * <p>
 * This IDGenerator doesn't support more than 16777215 IDs per 16 millisecond. It would throw an exception if this
 * happens.
 */
public class TimeAndCounterIDGenerator implements IDGenerator {


   /**
    * Bits to move the date accordingly to MASK_TIME
    */
   private static final int BITS_TO_MOVE = 20;

   public static final long MASK_TIME = 0x7fffffffff0L;

   // 44 bits of time and 20 bits of counter

   public static final long ID_MASK = 0xffffffL;

   private static final long TIME_ID_MASK = 0x7fffffffff000000L;


   private final AtomicLong counter = new AtomicLong(0);

   private volatile boolean wrapped = false;

   private volatile long tmMark;


   public TimeAndCounterIDGenerator() {
      refresh();
   }



   @Override
   public long generateID() {
      long idReturn = counter.incrementAndGet();

      if ((idReturn & TimeAndCounterIDGenerator.ID_MASK) == 0) {
         final long timePortion = idReturn & TimeAndCounterIDGenerator.TIME_ID_MASK;

         // Wrapping ID logic

         if (timePortion >= newTM()) {
            // Unlikely to happen

            wrapped = true;

         } else {
            // Else.. no worry... we will just accept the new time portion being added
            // This time-mark would have been generated some time ago, so this is ok.
            // tmMark is just a cache to validate the MaxIDs, so there is no need to make it atomic (synchronized)
            tmMark = timePortion;
         }
      }

      if (wrapped) {
         // This will only happen if a computer can generate more than ID_MASK ids (16 million IDs per 16
         // milliseconds)
         // If this wrapping code starts to happen, it needs revision
         throw new IllegalStateException("The IDGenerator is being overlapped, and it needs revision as the system generated more than " + TimeAndCounterIDGenerator.ID_MASK +
                                            " ids per 16 milliseconds which exceeded the IDgenerator limit");
      }

      return idReturn;
   }

   @Override
   public long getCurrentID() {
      return counter.get();
   }

   // for use in testcases
   public long getInternalTimeMark() {
      return tmMark;
   }

   // for use in testcases
   public void setInternalID(final long id) {
      counter.set(tmMark | id);
   }

   // for use in testcases
   public void setInternalDate(final long date) {
      tmMark = (date & TimeAndCounterIDGenerator.MASK_TIME) << TimeAndCounterIDGenerator.BITS_TO_MOVE;
      counter.set(tmMark);
   }

   public synchronized void refresh() {
      long oldTm = tmMark;
      long newTm = newTM();

      while (newTm <= oldTm) {
         newTm = newTM();
      }
      tmMark = newTm;
      counter.set(tmMark);
   }

   @Override
   public String toString() {
      long currentCounter = counter.get();
      return "SequenceGenerator(tmMark=" + hex(tmMark) +
         ", CurrentCounter = " +
         currentCounter +
         ", HexCurrentCounter = " +
         hex(currentCounter) +
         ")";
   }

   private long newTM() {
      return (System.currentTimeMillis() & TimeAndCounterIDGenerator.MASK_TIME) << TimeAndCounterIDGenerator.BITS_TO_MOVE;
   }

   private String hex(final long x) {
      return String.format("%1$X", x);
   }

}
