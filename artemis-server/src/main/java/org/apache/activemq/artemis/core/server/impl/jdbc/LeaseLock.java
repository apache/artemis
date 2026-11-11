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
package org.apache.activemq.artemis.core.server.impl.jdbc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * It represents a lock that can't be held more than {@link #expirationMillis()} without being renewed.
 *
 * <p>
 * An implementor must provide implicitly the caller identity to contextualize each operation (eg {@link JdbcLeaseLock}
 * uses one caller per instance)
 */
interface LeaseLock extends AutoCloseable {

   enum AcquireResult {
      Timeout, Exit, Done
   }

   interface ExitCondition {

      /**
       * @return true as long as we should keep running
       */
      boolean keepRunning();
   }

   long localExpirationTime();

   interface Pauser {

      void idle();

      static Pauser sleep(long idleTime, TimeUnit timeUnit) {
         final long idleNanos = timeUnit.toNanos(idleTime);
         //can fail spuriously but doesn't throw any InterruptedException
         return () -> LockSupport.parkNanos(idleNanos);
      }

      static Pauser noWait() {
         return () -> {
         };
      }
   }

   /**
    * The expiration in milliseconds from the last valid acquisition/renew.
    */
   default long expirationMillis() {
      return Long.MAX_VALUE;
   }

   /**
    * It extends the lock expiration (if held) to {@link System#currentTimeMillis()} + {@link #expirationMillis()}.
    *
    * @return {@code true} if the expiration has been moved on, {@code false} otherwise
    */
   default boolean renew() {
      return true;
   }

   /**
    * Not reentrant lock acquisition operation. The lock can be acquired if is not held by anyone (including the caller)
    * or has an expired ownership.
    *
    * @return {@code true} if has been acquired, {@code false} otherwise
    */
   boolean tryAcquire();

   /**
    * Not reentrant lock acquisition operation (ie {@link #tryAcquire()}). It tries to acquire the lock until will
    * succeed (ie {@link AcquireResult#Done})or got interrupted (ie {@link AcquireResult#Exit}). After each failed
    * attempt is performed a {@link Pauser#idle} call.
    */
   default AcquireResult tryAcquire(ExitCondition exitCondition, Pauser pauser) {
      while (exitCondition.keepRunning()) {
         if (tryAcquire()) {
            return AcquireResult.Done;
         } else {
            pauser.idle();
         }
      }
      return AcquireResult.Exit;
   }

   /**
    * Not reentrant lock acquisition operation (ie {@link #tryAcquire()}). It tries to acquire the lock until will
    * succeed (ie {@link AcquireResult#Done}), got interrupted (ie {@link AcquireResult#Exit}) or exceed
    * {@code tryAcquireTimeoutMillis}. After each failed attempt is performed a {@link Pauser#idle} call. If the
    * specified timeout is <=0 then it behaves as {@link #tryAcquire(ExitCondition, Pauser)}.
    */
   default AcquireResult tryAcquire(long tryAcquireTimeoutMillis, Pauser pauser, ExitCondition exitCondition) {
      if (tryAcquireTimeoutMillis < 0) {
         return tryAcquire(exitCondition, pauser);
      }
      final long timeoutInNanosecond = TimeUnit.MILLISECONDS.toNanos(tryAcquireTimeoutMillis);
      final long startAcquire = System.nanoTime();
      while (exitCondition.keepRunning()) {
         if (tryAcquire()) {
            return AcquireResult.Done;
         } else if (System.nanoTime() - startAcquire >= timeoutInNanosecond) {
            return AcquireResult.Timeout;
         } else {
            pauser.idle();
            //check before doing anything if time is expired
            if (System.nanoTime() - startAcquire >= timeoutInNanosecond) {
               return AcquireResult.Timeout;
            }
         }
      }
      return AcquireResult.Exit;
   }

   /**
    * @return {@code true} if there is a valid (ie not expired) owner, {@code false} otherwise
    */
   boolean isHeld();

   /**
    * @return {@code true} if the caller is a valid (ie not expired) owner, {@code false} otherwise
    */
   boolean isHeldByCaller();

   /**
    * It release the lock itself and all the resources used by it (eg connections, file handlers)
    */
   @Override
   default void close() throws Exception {
      release();
   }

   /**
    * Perform the release if this lock is held by the caller.
    */
   void release();
}
