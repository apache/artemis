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
package org.apache.activemq.artemis.cli.commands.messages.perf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class MicrosClock {

   // no need for volatile here
   private static long offset = -1;
   private static long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
   private static Class vm;
   private static Method getNanoTimeAdjustment;

   private static final boolean AVAILABLE = checkAvailable();

   private static boolean checkAvailable() {
      try {
         final long now = now();
         if (now < 0) {
            return false;
         }
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   public static boolean isAvailable() {
      return AVAILABLE;
   }

   public static long now() {
      try {
         long epochSecond = offset;
         if (vm == null) {
            vm = Class.forName("jdk.internal.misc.VM");
         }
         if (getNanoTimeAdjustment == null) {
            getNanoTimeAdjustment = vm.getMethod("getNanoTimeAdjustment", long.class);
         }
         long nanoAdjustment = (long) getNanoTimeAdjustment.invoke(getNanoTimeAdjustment, epochSecond);

         if (nanoAdjustment == -1) {
            epochSecond = System.currentTimeMillis() / 1000 - 1024;
            nanoAdjustment = (long) getNanoTimeAdjustment.invoke(getNanoTimeAdjustment, epochSecond);
            if (nanoAdjustment == -1) {
               throw new InternalError("Offset " + epochSecond + " is not in range");
            } else {
               offset = epochSecond;
            }
         }
         final long secs = Math.addExact(epochSecond, Math.floorDiv(nanoAdjustment, NANOS_PER_SECOND));
         final long secsInUs = TimeUnit.SECONDS.toMicros(secs);
         final long nsOffset = (int) Math.floorMod(nanoAdjustment, NANOS_PER_SECOND);
         final long usOffset = TimeUnit.NANOSECONDS.toMicros(nsOffset);
         return secsInUs + usOffset;
      } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
         throw new IllegalStateException(e);
      }
   }
}