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

package org.apache.artemis.database.worker;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A borrowed worker holds a {@link DataWorker} on a lease. The lease is renewed
 * each time the caller signals activity via {@link #renewLease()}. When the lease
 * expires (or the pool demands the worker back), the worker is returned to the
 * {@link DataManager}'s pool and the optional cleanup action runs.
 *
 * <p>Extends {@link ActiveMQScheduledComponent} in on-demand mode: the lease timeout
 * is implemented as a one-shot {@link #delay()} whose expiry fires {@link #run()}
 * on the bound executor, maintaining thread affinity automatically. A deadline
 * timestamp tracks when the lease actually expires; if {@link #run()} fires before
 * the deadline (because the lease was renewed after the delay was scheduled), it
 * reschedules itself via {@link #delay()}.</p>
 *
 * <p>The cooperative cancellation flag {@link #needConnectionBack} is volatile so
 * the holder can check it inside a tight loop without synchronization.</p>
 */
public class BorrowedWorker extends ActiveMQScheduledComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final DataManager dataManager;
   private final DataWorker worker;
   private final long leaseTimeoutNanos;
   private final Runnable cleanupAction;
   private final Runnable resumeAction;
   private final String description;

   /**
    * Set by the DataManager when it needs the connection back (pool exhausted).
    * The borrower should check this flag in its loop and abort early when set.
    */
   public volatile boolean needConnectionBack;

   /**
    * Tracks whether this borrowed worker has already been returned.
    * Must only be mutated under {@code this} monitor.
    */
   private boolean returned;

   /**
    * The nanoTime deadline at which the lease expires. Updated by {@link #renewLease()}.
    */
   private volatile long leaseDeadlineNanos;

   public BorrowedWorker(DataManager dataManager,
                         DataWorker worker,
                         Executor executor,
                         ScheduledExecutorService scheduledExecutorService,
                         long leaseTimeoutMillis,
                         Runnable cleanupAction,
                         Runnable resumeAction,
                         String description) {
      super(scheduledExecutorService, executor, leaseTimeoutMillis, TimeUnit.MILLISECONDS, true);
      this.dataManager = dataManager;
      this.worker = worker;
      this.leaseTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(leaseTimeoutMillis);
      this.cleanupAction = cleanupAction;
      this.resumeAction = resumeAction;
      this.description = description;
      this.returned = false;
      this.needConnectionBack = false;
      this.leaseDeadlineNanos = System.nanoTime() + leaseTimeoutNanos;
      start();
      delay();
   }

   /**
    * Returns the underlying worker so the borrower can use its statements and connection.
    */
   public DataWorker getWorker() {
      return worker;
   }

   /**
    * Returns the executor this borrowed worker is bound to.
    */
   public Executor getExecutor() {
      return executor;
   }

   /**
    * Re-execute the resume action on the bound executor. This is the same action
    * that was passed to {@link DataManager#borrowWorker} and ran on the initial borrow.
    * Use this for subsequent invocations instead of re-borrowing a worker.
    */
   public void resume() {
      executor.execute(resumeAction);
   }

   /**
    * Disconnect and reconnect the underlying worker so it returns to the pool healthy.
    * Retries the connection with waits between attempts.
    *
    * @param cause the exception that triggered the reconnect
    * @return null on success, or the final SQLException if all retries were exhausted
    */
   public java.sql.SQLException reconnect(Throwable cause) {
      return worker.reconnect(cause);
   }

   /**
    * Execute an action on the underlying worker with retry and reconnect on failure.
    * Delegates to {@link DataWorker#executeWithRetry(SQLConsumer)}.
    *
    * @param action the action to execute
    * @return null on success, or the final SQLException if all retries were exhausted
    */
   public java.sql.SQLException executeWithRetry(SQLConsumer<DataWorker> action) {
      return worker.executeWithRetry(action);
   }

   /**
    * Renew the lease. Call this each time the worker produces useful work
    * (e.g. each batch of results consumed). Pushes the deadline forward;
    * the next time {@link #run()} fires it will see the updated deadline
    * and reschedule if needed.
    */
   public void renewLease() {
      synchronized (this) {
         if (returned) {
            return;
         }
      }
      leaseDeadlineNanos = System.nanoTime() + leaseTimeoutNanos;
      // Try to schedule a new delay. If one is already pending (delay() returns false),
      // that's fine — it will fire, check the deadline, and reschedule if needed.
      delay();
   }

   /**
    * Explicitly return the worker to the pool. Safe to call multiple times;
    * only the first call has an effect. The cleanup action runs on the
    * caller's thread before the worker goes back to the pool.
    */
   public void returnWorker() {
      synchronized (this) {
         if (returned) {
            return;
         }
         returned = true;
      }
      stop();
      doReturn();
   }

   /**
    * Check whether this borrowed worker has already been returned.
    */
   public synchronized boolean isReturned() {
      return returned;
   }

   /**
    * Called by the DataManager when the pool is exhausted and it needs workers back.
    * Sets the cooperative flag and, if the borrower doesn't return soon, the lease
    * timeout will force the return.
    */
   void demandBack() {
      needConnectionBack = true;
   }

   /**
    * Fired by ActiveMQScheduledComponent when a scheduled delay expires.
    * The executor routing in the parent class ensures this runs on the
    * bound executor, maintaining thread affinity.
    *
    * <p>If the lease was renewed since this delay was scheduled, the deadline
    * will be in the future and we reschedule via {@link #delay()} instead of
    * expiring the lease.</p>
    */
   @Override
   public void run() {
      synchronized (this) {
         if (returned) {
            return;
         }
      }
      if (System.nanoTime() < leaseDeadlineNanos) {
         // Lease was renewed since this delay was scheduled. Reschedule.
         delay();
         return;
      }
      synchronized (this) {
         if (returned) {
            return;
         }
         returned = true;
      }
      logger.debug("Lease expired for borrowed worker, returning to pool");
      executor.execute(this::doReturn);
   }

   @Override
   public String toString() {
      return description != null ? description : "BorrowedWorker@" + Integer.toHexString(hashCode());
   }

   private void doReturn() {
      try {
         logger.info("Returning worker {}", description);
         if (cleanupAction != null) {
            cleanupAction.run();
         }
      } catch (Throwable t) {
         logger.warn("Error during borrowed worker cleanup: {}", t.getMessage(), t);
      } finally {
         dataManager.returnBorrowedWorker(this);
      }
   }
}
