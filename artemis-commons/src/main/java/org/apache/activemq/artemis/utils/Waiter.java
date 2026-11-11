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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class Waiter {

   public interface Condition {

      boolean result();
   }

   /**
    * This method will wait for the condition.result to be true or a timeout has ocurred. it will return the last
    * result.
    */
   public static boolean waitFor(Condition condition, TimeUnit unit, long timeout, TimeUnit parkUnit, long parkTime) {
      long timeoutNanos = unit.toNanos(timeout);
      final long deadline = System.nanoTime() + timeoutNanos;
      long parkNanos = parkUnit.toNanos(parkTime);
      while (!condition.result() && (System.nanoTime() - deadline) < 0) {
         // Wait some time
         LockSupport.parkNanos(parkNanos);
      }
      return condition.result();
   }
}
