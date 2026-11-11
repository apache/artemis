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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.NodeManager.LockListener;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Default implementation of a {@link ScheduledLeaseLock}: see
 * {@link ScheduledLeaseLock#of(ScheduledExecutorService, ArtemisExecutor, String, LeaseLock, long, LockListener)}.
 */
final class ActiveMQScheduledLeaseLock extends ActiveMQScheduledComponent implements ScheduledLeaseLock {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String lockName;
   private final LeaseLock lock;
   private long lastLockRenewStart;
   private final long renewPeriodMillis;
   private final LockListener lockListener;

   ActiveMQScheduledLeaseLock(ScheduledExecutorService scheduledExecutorService,
                              ArtemisExecutor executor,
                              String lockName,
                              LeaseLock lock,
                              long renewPeriodMillis,
                              LockListener lockListener) {
      super(scheduledExecutorService, executor, 0, renewPeriodMillis, TimeUnit.MILLISECONDS, false);
      if (renewPeriodMillis >= lock.expirationMillis()) {
         throw new IllegalArgumentException("renewPeriodMillis must be < lock's expirationMillis");
      }
      this.lockName = lockName;
      this.lock = lock;
      this.renewPeriodMillis = renewPeriodMillis;
      // already expired start time
      this.lastLockRenewStart = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(lock.expirationMillis());
      this.lockListener = lockListener;
   }

   @Override
   public String lockName() {
      return lockName;
   }

   @Override
   public long renewPeriodMillis() {
      return renewPeriodMillis;
   }

   @Override
   public LeaseLock lock() {
      return lock;
   }

   @Override
   public synchronized void start() {
      if (isStarted()) {
         return;
      }
      this.lastLockRenewStart = System.nanoTime();
      super.start();
   }

   @Override
   public synchronized void stop() {
      if (!isStarted()) {
         return;
      }
      super.stop();
   }

   @Override
   public synchronized void run() {
      if (!isStarted()) {
         return;
      }
      final long lastRenewStart = this.lastLockRenewStart;
      final long renewStart = System.nanoTime();
      boolean lockLost = true;
      try {
         lockLost = !this.lock.renew();
      } catch (Throwable t) {
         logger.warn("{} lock renew has failed", lockName, t);
         if (lock.localExpirationTime() > 0) {
            final long millisBeforeExpiration = (lock.localExpirationTime() - System.currentTimeMillis());
            // there is enough time to retry to renew it?
            if (millisBeforeExpiration >= this.renewPeriodMillis) {
               lockLost = false;
            }
         }
      }
      // a failed attempt to renew is treated as a lost lock
      if (lockLost) {
         try {
            lockListener.lostLock();
         } catch (Throwable t) {
            logger.warn("Errored while notifying {} lock listener", lockName, t);
         }
      }
      //logic to detect slowness of DB and/or the scheduled executor service
      detectAndReportRenewSlowness(lockName, lockLost, lastRenewStart,
                                   renewStart, renewPeriodMillis, lock.expirationMillis());
      this.lastLockRenewStart = renewStart;
   }

   private static void detectAndReportRenewSlowness(String lockName,
                                                    boolean lostLock,
                                                    long lastRenewStart,
                                                    long renewStart,
                                                    long expectedRenewPeriodMillis,
                                                    long expirationMillis) {
      final long elapsedMillisToRenew = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - renewStart);
      if (elapsedMillisToRenew > expectedRenewPeriodMillis) {
         logger.error("{} lock {} renew tooks {} ms, while is supposed to take <{} ms", lockName, lostLock ? "failed" : "successful", elapsedMillisToRenew, expectedRenewPeriodMillis);
      }
      final long measuredRenewPeriodNanos = renewStart - lastRenewStart;
      final long measuredRenewPeriodMillis = TimeUnit.NANOSECONDS.toMillis(measuredRenewPeriodNanos);
      if (measuredRenewPeriodMillis - expirationMillis > 100) {
         logger.error("{} lock {} renew period lasted {} ms instead of {} ms", lockName, lostLock ? "failed" : "successful", measuredRenewPeriodMillis, expirationMillis);
      } else if (measuredRenewPeriodMillis - expectedRenewPeriodMillis > 100) {
         logger.warn("{} lock {} renew period lasted {} ms instead of {} ms", lockName, lostLock ? "failed" : "successful", measuredRenewPeriodMillis, expectedRenewPeriodMillis);
      }
   }
}
