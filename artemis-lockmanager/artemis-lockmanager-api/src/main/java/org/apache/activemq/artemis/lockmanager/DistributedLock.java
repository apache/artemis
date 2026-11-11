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
package org.apache.activemq.artemis.lockmanager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public interface DistributedLock extends AutoCloseable {

   String getLockId();

   boolean isHeldByCaller() throws UnavailableStateException;

   boolean tryLock() throws UnavailableStateException, InterruptedException;

   default boolean tryLock(long timeout, TimeUnit unit) throws UnavailableStateException, InterruptedException {
      // it doesn't make sense to be super fast
      final long TARGET_FIRE_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(250);
      if (timeout < 0) {
         throw new IllegalArgumentException("timeout cannot be negative");
      }
      Objects.requireNonNull(unit);
      if (timeout == 0) {
         return tryLock();
      }
      final Thread currentThread = Thread.currentThread();
      final long timeoutNs = unit.toNanos(timeout);
      final long start = System.nanoTime();
      final long deadline = start + timeoutNs;
      long expectedNextFireTime = start;
      while (!currentThread.isInterrupted()) {
         long parkNs = expectedNextFireTime - System.nanoTime();
         while (parkNs > 0) {
            LockSupport.parkNanos(parkNs);
            if (currentThread.isInterrupted()) {
               throw new InterruptedException();
            }
            final long now = System.nanoTime();
            parkNs = expectedNextFireTime - now;
         }
         if (tryLock()) {
            return true;
         }
         final long now = System.nanoTime();
         final long remainingTime = deadline - now;
         if (remainingTime <= 0) {
            return false;
         }
         if (remainingTime < TARGET_FIRE_PERIOD_NS) {
            expectedNextFireTime = now;
         } else {
            expectedNextFireTime += TARGET_FIRE_PERIOD_NS;
         }
      }
      throw new InterruptedException();
   }

   void unlock() throws UnavailableStateException;

   void addListener(UnavailableLockListener listener);

   void removeListener(UnavailableLockListener listener);

   @FunctionalInterface
   interface UnavailableLockListener {

      void onUnavailableLockEvent();
   }

   @Override
   void close();
}
