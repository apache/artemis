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
package org.apache.activemq.artemis.core.server;

import java.lang.invoke.MethodHandles;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is for components with a scheduled at a fixed rate.
 */
public abstract class ActiveMQScheduledComponent implements ActiveMQComponent, Runnable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected ScheduledExecutorService scheduledExecutorService;
   private boolean startedOwnScheduler;

   /**
    * initialDelay < 0 would mean no initial delay, use the period instead
    */
   private long initialDelay;
   private long period;
   private TimeUnit timeUnit;
   protected final Executor executor;
   private volatile boolean isStarted;
   private ScheduledFuture future;
   private final boolean onDemand;
   // The start/stop actions shouldn't interact concurrently with delay so it doesn't need to be volatile
   private AtomicBoolean bookedForRunning;

   /**
    * It creates a scheduled component that can trigger {@link #run()} with a fixed {@code checkPeriod} on a configured
    * {@code executor}.
    *
    * @param scheduledExecutorService the {@link ScheduledExecutorService} that periodically trigger {@link #run()} on
    *                                 the configured {@code executor}
    * @param executor                 the {@link Executor} that execute {@link #run()} when triggered
    * @param initialDelay             the time to delay first execution
    * @param checkPeriod              the delay between the termination of one execution and the start of the next
    * @param timeUnit                 the time unit of the {@code initialDelay} and {@code checkPeriod} parameters
    * @param onDemand                 if {@code true} the task won't be scheduled on {@link #start()}, {@code false}
    *                                 otherwise
    */
   public ActiveMQScheduledComponent(ScheduledExecutorService scheduledExecutorService,
                                     Executor executor,
                                     long initialDelay,
                                     long checkPeriod,
                                     TimeUnit timeUnit,
                                     boolean onDemand) {
      this.executor = executor;
      this.scheduledExecutorService = scheduledExecutorService;
      this.initialDelay = initialDelay;
      this.period = checkPeriod;
      this.timeUnit = timeUnit;
      this.onDemand = onDemand;
      this.bookedForRunning = new AtomicBoolean(false);
      this.isStarted = false;
   }

   /**
    * It creates a scheduled component that can trigger {@link #run()} with a fixed {@code checkPeriod} on a configured
    * {@code executor}.
    *
    * @param scheduledExecutorService the {@link ScheduledExecutorService} that periodically trigger {@link #run()} on
    *                                 the configured {@code executor}
    * @param initialDelay             the time to delay first execution
    * @param checkPeriod              the delay between the termination of one execution and the start of the next
    * @param timeUnit                 the time unit of the {@code initialDelay} and {@code checkPeriod} parameters
    * @param onDemand                 if {@code true} the task won't be scheduled on {@link #start()}, {@code false}
    *                                 otherwise
    */
   public ActiveMQScheduledComponent(ScheduledExecutorService scheduledExecutorService,
                                     long initialDelay,
                                     long checkPeriod,
                                     TimeUnit timeUnit,
                                     boolean onDemand) {
      this(scheduledExecutorService, null, initialDelay, checkPeriod, timeUnit, onDemand);
   }

   /**
    * It creates a scheduled component that can trigger {@link #run()} with a fixed {@code checkPeriod} on a configured
    * {@code executor}.
    * <p>
    * The component created will have {@code initialDelay} defaulted to {@code checkPeriod}.
    *
    * @param scheduledExecutorService the {@link ScheduledExecutorService} that periodically trigger {@link #run()} on
    *                                 the configured {@code executor}
    * @param executor                 the {@link Executor} that execute {@link #run()} when triggered
    * @param checkPeriod              the delay between the termination of one execution and the start of the next
    * @param timeUnit                 the time unit of the {@code initialDelay} and {@code checkPeriod} parameters
    * @param onDemand                 if {@code true} the task won't be scheduled on {@link #start()}, {@code false}
    *                                 otherwise
    */
   public ActiveMQScheduledComponent(ScheduledExecutorService scheduledExecutorService,
                                     Executor executor,
                                     long checkPeriod,
                                     TimeUnit timeUnit,
                                     boolean onDemand) {
      this(scheduledExecutorService, executor, -1, checkPeriod, timeUnit, onDemand);
   }

   /**
    * It creates a scheduled component that can trigger {@link #run()} with a fixed {@code checkPeriod} on a configured
    * {@code executor}.
    * <p>
    * This is useful for cases where we want our own scheduler executor: on {@link #start()} it will create a fresh new
    * single-threaded {@link ScheduledExecutorService} using {@link #getThreadFactory()} and
    * {@link #getThisClassLoader()}, while on {@link #stop()} it will garbage it.
    *
    * @param initialDelay the time to delay first execution
    * @param checkPeriod  the delay between the termination of one execution and the start of the next
    * @param timeUnit     the time unit of the {@code initialDelay} and {@code checkPeriod} parameters
    * @param onDemand     if {@code true} the task won't be scheduled on {@link #start()}, {@code false} otherwise
    */
   public ActiveMQScheduledComponent(long initialDelay, long checkPeriod, TimeUnit timeUnit, boolean onDemand) {
      this(null, null, initialDelay, checkPeriod, timeUnit, onDemand);
   }

   /**
    * It creates a scheduled component that can trigger {@link #run()} with a fixed {@code checkPeriod} on a configured
    * {@code executor}.
    *
    * <p>
    * This is useful for cases where we want our own scheduler executor. The component created will have
    * {@code initialDelay} defaulted to {@code checkPeriod}.
    *
    * @param checkPeriod the delay between the termination of one execution and the start of the next
    * @param timeUnit    the time unit of the {@code initialDelay} and {@code checkPeriod} parameters
    * @param onDemand    if {@code true} the task won't be scheduled on {@link #start()}, {@code false} otherwise
    */
   public ActiveMQScheduledComponent(long checkPeriod, TimeUnit timeUnit, boolean onDemand) {
      this(null, null, checkPeriod, checkPeriod, timeUnit, onDemand);
   }

   @Override
   public synchronized void start() {
      if (isStarted) {
         // already started
         return;
      }
      isStarted = true;
      if (scheduledExecutorService == null) {
         scheduledExecutorService = new ScheduledThreadPoolExecutor(1, getThreadFactory());
         startedOwnScheduler = true;

      }

      if (onDemand) {
         return;
      }

      if (period >= 0) {
         final AtomicBoolean booked = this.bookedForRunning;
         future = scheduledExecutorService.scheduleWithFixedDelay(() -> runForScheduler(booked), initialDelay >= 0 ? initialDelay : period, period, timeUnit);
      } else {
         logger.trace("did not start scheduled executor on {} because period was configured as {}", this, period);
      }
   }

   protected ActiveMQThreadFactory getThreadFactory() {
      return new ActiveMQThreadFactory(this.getClass().getSimpleName() + "-scheduled", false, getThisClassLoader());
   }

   private ClassLoader getThisClassLoader() {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<ClassLoader>) () -> ActiveMQScheduledComponent.this.getClass().getClassLoader());

   }

   /**
    * A delay request can succeed only if:
    * <ul>
    *    <li>there is no other pending delay request
    *    <li>there is no pending execution request
    * </ul>
    * <p>
    * When a delay request succeed it schedule a new execution to happen in {@link #getPeriod()}.
    */
   public boolean delay() {
      final AtomicBoolean booked = this.bookedForRunning;
      if (!booked.compareAndSet(false, true)) {
         return false;
      }
      try {
         scheduledExecutorService.schedule(() -> bookedRunForScheduler(booked), period, timeUnit);
         return true;
      } catch (RejectedExecutionException e) {
         booked.set(false);
         throw e;
      }
   }

   public long getPeriod() {
      return period;
   }

   public synchronized ActiveMQScheduledComponent setPeriod(long period) {
      if (this.period != period) {
         this.period = period;
         restartIfNeeded();
      }
      return this;
   }

   public synchronized ActiveMQScheduledComponent setPeriod(long period, TimeUnit unit) {
      Objects.requireNonNull(unit, "unit is required");
      if (this.period != period || this.timeUnit != unit) {
         this.period = period;
         this.timeUnit = unit;
         restartIfNeeded();
      }
      return this;
   }

   public long getInitialDelay() {
      return initialDelay;
   }

   public synchronized ActiveMQScheduledComponent setInitialDelay(long initialDelay) {
      this.initialDelay = initialDelay;
      restartIfNeeded();
      return this;
   }

   /**
    * Useful to change a running schedule and avoid multiple restarts.
    */
   public synchronized ActiveMQScheduledComponent setInitialDelayAndPeriod(long initialDelay, long period) {
      this.period = period;
      this.initialDelay = initialDelay;
      restartIfNeeded();
      return this;
   }

   /**
    * Useful to change a running schedule and avoid multiple restarts.
    */
   public synchronized ActiveMQScheduledComponent setInitialDelayAndPeriod(long initialDelay,
                                                                           long period,
                                                                           TimeUnit timeUnit) {
      this.period = period;
      this.initialDelay = initialDelay;
      this.timeUnit = timeUnit;
      restartIfNeeded();
      return this;
   }

   public TimeUnit getTimeUnit() {
      return timeUnit;
   }

   public synchronized ActiveMQScheduledComponent setTimeUnit(TimeUnit timeUnit) {
      this.timeUnit = timeUnit;
      restartIfNeeded();
      return this;
   }

   @Override
   public synchronized void stop() {
      if (!isStarted) {
         return;
      }
      isStarted = false;
      // Replace the existing one: a new periodic task or any new delay after stop
      // won't interact with the previously running ones
      this.bookedForRunning = new AtomicBoolean(false);
      if (future != null) {
         future.cancel(false);
         future = null;
      }
      if (startedOwnScheduler) {
         this.scheduledExecutorService.shutdownNow();
         scheduledExecutorService = null;
         startedOwnScheduler = false;
      }

   }

   @Override
   public boolean isStarted() {
      return isStarted;
   }

   // this will restart the scheduled component upon changes
   private void restartIfNeeded() {
      if (isStarted()) {
         stop();
         start();
      }
   }

   private void runForExecutor(AtomicBoolean booked) {
      // It unblocks:
      // - a new delay request
      // - next periodic run request (in case of executor != null)
      // Although tempting, don't move this one after ActiveMQScheduledComponent.this.run():
      // - it can cause "delay" to change semantic ie a racing delay while finished executing the task, won't succeed
      // - it won't prevent "slow tasks" to accumulate, because slowness cannot be measured inside running method;
      //   it just cause skipping runs for perfectly timed executions too
      boolean alwaysTrue = booked.compareAndSet(true, false);
      assert alwaysTrue;
      ActiveMQScheduledComponent.this.run();
   }

   private void bookedRunForScheduler(AtomicBoolean booked) {
      assert booked.get();
      if (executor != null) {
         try {
            executor.execute(() -> runForExecutor(booked));
         } catch (RejectedExecutionException e) {
            if (booked != null) {
               booked.set(false);
            }
            throw e;
         }
      } else {
         runForExecutor(booked);
      }
   }

   private void runForScheduler(AtomicBoolean booked) {
      if (!booked.compareAndSet(false, true)) {
         // let's skip this execution because there is:
         // - a previously submitted period task yet to start -> executor is probably overbooked!
         // - a pending delay request
         return;
      }
      bookedRunForScheduler(booked);
   }

}
