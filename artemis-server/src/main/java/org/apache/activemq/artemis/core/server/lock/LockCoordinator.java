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

package org.apache.activemq.artemis.core.server.lock;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.utils.RunnableEx;
import org.apache.activemq.artemis.utils.SimpleFutureImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages distributed locks a pluggable distributed lock mechanism.
 * <p>
 * The LockMonitor periodically attempts to acquire a distributed lock. When the lock
 * is acquired, registered "acquired" callbacks are executed. When the lock is lost
 * or released, "released" callbacks are executed.
 *
 * @see org.apache.activemq.artemis.lockmanager.DistributedLockManager
 */
public class LockCoordinator extends ActiveMQScheduledComponent {

   /** Default period (in milliseconds) for checking lock status */
   public static final int DEFAULT_CHECK_PERIOD = 5000;

   String debugInfo;

   public String getDebugInfo() {
      return debugInfo;
   }

   public LockCoordinator setDebugInfo(String debugInfo) {
      this.debugInfo = debugInfo;
      return this;
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ArrayList<RunnableEx> lockAcquiredCallback = new ArrayList<>();
   private final ArrayList<RunnableEx> lockReleasedCallback = new ArrayList<>();
   private final long checkPeriod;
   private final String name;
   private final String lockID;

   DistributedLockManager lockManager;
   DistributedLock distributedLock;
   volatile boolean locked;

   public DistributedLockManager getLockManager() {
      return lockManager;
   }

   /**
    * Registers a callback to be executed when lock is acquired.
    * If the lock is already held when this method is called, the callback
    * will be executed immediately (on the executor thread).
    *
    * Also In case the runnable throws any exceptions, the lock will be released, any previously added callback will be called for stop
    * and the monitor will retry the locks
    *
    * @param runnable the callback to execute when lock is acquired
    */
   public void onLockAcquired(RunnableEx runnable) {
      this.lockAcquiredCallback.add(runnable);
      // if it's locked we run the runnable being added,
      // however we must check this inside the executor
      // or within a global locking
      executor.execute(() -> runIfLocked(runnable));
   }

   /**
    * Registers a callback to be executed when lock is released or lost.
    *
    * @param runnable the callback to execute when lock is released
    */
   public void onLockReleased(RunnableEx runnable) {
      this.lockReleasedCallback.add(runnable);
   }

   /**
    * Stops the lock coordinator, releasing any held locks and cleaning up resources.
    * This method blocks until all cleanup is complete.
    */
   @Override
   public void stop() {
      super.stop();
      SimpleFutureImpl<Void> simpleFuture = new SimpleFutureImpl<>();
      executor.execute(() -> {
         if (locked) {
            fireLockChanged(false);
         }
         if (distributedLock != null) {
            try {
               distributedLock.unlock();
            } catch (Exception e) {
               logger.debug("Error unlocking during stop", e);
            }
            try {
               distributedLock.close();
            } catch (Exception e) {
               logger.debug("Error closing lock during stop", e);
            }
            distributedLock = null;
         }
         if (lockManager != null) {
            try {
               lockManager.stop();
            } catch (Exception e) {
               logger.debug("Error stopping lock manager during stop", e);
            }
            lockManager = null;
         }
         simpleFuture.set(null);
      });
      try {
         simpleFuture.get();
      } catch (Exception e) {
         logger.debug("Error waiting for stop to complete", e);
      }
   }

   /**
    * Returns whether this instance currently holds the lock.
    *
    * @return true if lock is currently held, false otherwise
    */
   public boolean isLocked() {
      return locked;
   }

   /**
    * Constructs a new LockCoordinator.
    *
    * @param scheduledExecutor the executor for scheduling periodic lock checks
    * @param executor the executor for running callbacks
    * @param checkPeriod how often to check lock status (in milliseconds)
    * @param lockManager the distributed lock manager implementation to use
    * @param lockID the unique identifier for the lock
    * @param name a descriptive name for this lock coordinator
    */
   public LockCoordinator(ScheduledExecutorService scheduledExecutor, Executor executor, long checkPeriod, DistributedLockManager lockManager, String lockID, String name) {
      super(scheduledExecutor, executor, checkPeriod, checkPeriod, TimeUnit.MILLISECONDS, false);
      assert executor != null;
      this.lockManager = lockManager;
      this.checkPeriod = checkPeriod;
      this.lockID = lockID;
      this.name = name;
   }

   private void fireLockChanged(boolean locked) {
      this.locked = locked;
      if (locked) {
         AtomicBoolean treatErrors = new AtomicBoolean(false);
         lockAcquiredCallback.forEach(r -> doRunTreatingErrors(r, treatErrors));
         if (treatErrors.get()) {
            retryLock();
         }
      } else {
         lockReleasedCallback.forEach(this::doRunWithLogException);
      }
   }

   private void retryLock() {
      ActiveMQServerLogger.LOGGER.retryLockCoordinator(name);
      // Release lock and retry on next scheduled run if callbacks failed
      executor.execute(this::executeRetryLock);
   }

   // to be used as a runnable on the executor
   private void executeRetryLock() {
      if (locked) {
         logger.debug("Unlocking to retry the callback");
         fireLockChanged(false);
         if (distributedLock != null) {
            try {
               distributedLock.unlock();
               distributedLock.close();
            } catch (Exception e) {
               logger.debug(e.getMessage(), e);
            }
            distributedLock = null;
         }
         if (lockManager != null) {
            try {
               lockManager.stop();
            } catch (Exception e) {
               logger.debug(e.getMessage(), e);
            }
         }
      }
   }

   private void runIfLocked(RunnableEx checkBeingAdded) {
      if (locked) {
         try {
            doRun(checkBeingAdded);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            retryLock();
         }
      }
   }

   private void doRunTreatingErrors(RunnableEx r, AtomicBoolean errorOnStart) {
      try {
         r.run();
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         errorOnStart.set(true);
      }
   }

   private void doRun(RunnableEx r) throws Exception  {
      r.run();
   }

   private void doRunWithLogException(RunnableEx r) {
      try {
         r.run();
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }
   }

   @Override
   public void run() {
      try {
         if (!locked) {
            if (!lockManager.isStarted()) {
               lockManager.start();
            }
            DistributedLock lock = lockManager.getDistributedLock(lockID);
            if (lock.tryLock(1, TimeUnit.SECONDS)) {
               logger.debug("Succeeded on locking {}, lockID={}", name, lockID);
               this.distributedLock = lock;
               fireLockChanged(true);
            } else {
               logger.debug("Not able to lock {}, lockID={}", name, lockID);
               lock.close();
               lockManager.stop();
            }
         } else {
            if (!distributedLock.isHeldByCaller()) {
               fireLockChanged(false);
               distributedLock.close();
               distributedLock = null;
               lockManager.stop();
            }
         }
      } catch (Exception e) {
         fireLockChanged(false);
         if (distributedLock != null) {
            try {
               distributedLock.close();
            } catch (Exception closeEx) {
               logger.debug("Error closing lock", closeEx);
            }
            distributedLock = null;
         }
         if (lockManager != null) {
            try {
               lockManager.stop();
            } catch (Exception stopEx) {
               logger.debug("Error stopping lock manager", stopEx);
            }
         }
         logger.warn(e.getMessage(), e);
      }
   }
}

