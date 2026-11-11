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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base for managing bridge sender instances that manages the existence
 * of a bridge sender both while active and while attempting recovery.
 *
 * All interactions with the sender manager should occur under the lock of the parent
 * manager instance and this manager will perform any asynchronous work with a lock
 * held on the parent manager instance.
 */
public abstract class AMQPBridgeSenderManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private enum State {
      READY,
      STARTING,
      STARTED,
      CLOSED
   }

   private final AMQPBridgeManager bridgeManager;
   private final AMQPBridgeToPolicyManager policyManager;
   private final AMQPBridgeSenderConfiguration configuration;

   private State state = State.READY;
   private AMQPBridgeSender sender;
   private AMQPBridgeSenderRecoveryHandler recoveryHandler;

   /**
    * Create a new bridge sender manager instance.
    *
    * @param policyManager
    *    The policy manager that created this instance.
    * @param configuration
    *    The configuration that was in force when the manager was created.
    */
   public AMQPBridgeSenderManager(AMQPBridgeToPolicyManager policyManager, AMQPBridgeSenderConfiguration configuration) {
      this.policyManager = policyManager;
      this.configuration = configuration;
      this.bridgeManager = policyManager.getBridgeManager();
   }

   /**
    * Creates a new bridge sender that this manager will monitor and maintain. The returned
    * sender should be in an initial state ready for this manager to initialize once it is fully
    * configured.
    *
    * @return a newly create {@link AMQPBridgeSender} for use by this manager.
    */
   protected abstract AMQPBridgeSender createBridgeSender();

   /**
    * An immediate close of the bridge sender and any recovery tasks that might be
    * pending for a disconnected sender instance.
    *
    * The bridge manager should be calling this method with its lock held.
    */
   public void shutdownNow() {
      if (state != State.CLOSED) {
         state = State.CLOSED;

         try {
            if (sender != null) {
               safeCloseCurrentBridgeSender();
            }
         } finally {
            sender = null;
            closeRecoveryHandler();
         }
      }
   }

   /**
    * An immediate start of the bridge sender which triggers the first sender attach
    * request and then allows for ongoing recovery efforts if configuration allows it.
    *
    * The bridge manager should be calling this method with its lock held.
    */
   public void start() {
      checkClosed();

      if (state == State.READY) {
         tryCreateBridgeSender();
      }
   }

   /*
    * Must be called with locks in place from the parent manager to prevent concurrent
    * access to state changing APIs.
    */
   private void tryCreateBridgeSender() {
      state = State.STARTING;
      sender = createBridgeSender();

      logger.trace("Bridge sender manager creating remote sender for: {}", sender.getSenderInfo());

      // Handle remote open and cancel any additional link recovery attempts. Ensure that
      // thread safety is accounted for here as the notification come from the connection
      // thread.
      sender.setRemoteOpenHandler(openedSender -> {
         synchronized (policyManager) {
            // Stop or close could have followed quick enough that this didn't get completed yet.
            if (state == State.STARTING) {
               state = State.STARTED;
            }

            closeRecoveryHandler();
         }
      });

      // Handle remote close with remove of sender which means that future demand will
      // attempt to create a new sender for that demand. Ensure that thread safety is
      // accounted for here as the notification can be asynchronous.
      sender.setRemoteClosedHandler((closedSender) -> {
         synchronized (this) {
            synchronized (policyManager) {
               safeCloseCurrentBridgeSender();

               if (configuration.isLinkRecoveryEnabled() && state == State.READY) {
                  // If the close came from a previous attempt that is itself a recovery we use the
                  // existing recovery handler, otherwise we need to create a new handler to deal
                  // with link recovery and track attempt counts.
                  if (recoveryHandler == null) {
                     recoveryHandler = new AMQPBridgeSenderRecoveryHandler(this, configuration);
                  }

                  final boolean scheduled = recoveryHandler.tryScheduleNextRecovery(bridgeManager.getScheduler());

                  if (!scheduled) {
                     closeRecoveryHandler();
                  }
               }
            }
         }
      });

      sender.initialize();
   }

   /*
    * Must lock on the policy manager to ensure that state changes occur in safe conditions.
    */
   private void tryRecoverSender() {
      synchronized (policyManager) {
         // Recovery only needs to occur if we are in the READY state, meaning not closed,
         // otherwise we can end recovery operations. The recovery handler is normally closed
         // out on a successful remote attach response.
         if (state == State.READY) {
            tryCreateBridgeSender();
         } else {
            closeRecoveryHandler();
         }
      }
   }

   private void safeCloseCurrentBridgeSender() {
      try {
         safeCloseCurrentBridgeSender(sender);
      } finally {
         state = state == State.CLOSED ? State.CLOSED : State.READY;
         sender = null;
      }
   }

   private void safeCloseCurrentBridgeSender(AMQPBridgeSender sender) {
      if (sender != null) {
         try {
            if (!sender.isClosed()) {
               sender.close();
            }
         } catch (Exception e) {
            logger.trace("Suppressed error on close of bridge sender. ", e);
         }
      }
   }

   private void checkClosed() {
      if (state == State.CLOSED) {
         throw new IllegalStateException("The bridge sender has been closed already");
      }
   }

   private void closeRecoveryHandler() {
      if (recoveryHandler != null) {
         try {
            recoveryHandler.close();
         } finally {
            recoveryHandler = null;
         }
      }
   }

   public static class AMQPBridgeSenderRecoveryHandler implements Closeable {

      private final AMQPBridgeSenderManager manager;
      private final AMQPBridgeLinkConfiguration configuration;
      private final int maxRecoveryAttempts;

      private final AtomicInteger recoveryAttempts = new AtomicInteger();
      private final AtomicLong nextRecoveryDelay = new AtomicLong();
      private volatile ScheduledFuture<?> recoveryFuture;

      public AMQPBridgeSenderRecoveryHandler(AMQPBridgeSenderManager manager, AMQPBridgeLinkConfiguration configuration) {
         this.manager = manager;
         this.configuration = configuration;
         this.nextRecoveryDelay.set(configuration.getLinkRecoveryInitialDelay() > 0 ? configuration.getLinkRecoveryInitialDelay() : 1);
         this.maxRecoveryAttempts = configuration.getMaxLinkRecoveryAttempts();
      }

      @Override
      public void close() {
         final ScheduledFuture<?> future = this.recoveryFuture;

         if (future != null) {
            future.cancel(false);
         }

         recoveryFuture = null;
      }

      /**
       * When a link used by the bridge fails, this method is used to try and schedule a new
       * connection attempt if the configuration rules allow one. If the configuration does not
       * allow any (more) reconnection attempts this method returns false.
       *
       * @param scheduler
       *    The scheduler to use to schedule the next connection attempt.
       *
       * @return true if an attempt was scheduled or false if no attempts are allowed.
       */
      public boolean tryScheduleNextRecovery(ScheduledExecutorService scheduler) {
         Objects.requireNonNull(scheduler, "The scheduler to use cannot be null");

         if (maxRecoveryAttempts < 0 || recoveryAttempts.get() < maxRecoveryAttempts) {
            recoveryFuture = scheduler.schedule(this::handleReconnectionAttempt, nextRecoveryDelay.get(), TimeUnit.MILLISECONDS);
            return true;
         } else {
            return false;
         }
      }

      private void handleReconnectionAttempt() {
         final ScheduledFuture<?> future = this.recoveryFuture;

         try {
            if (future != null && !future.isCancelled()) {
               if (maxRecoveryAttempts > 0) {
                  recoveryAttempts.incrementAndGet();
               }

               // If user configures no delay, we impose a small one just to avoid over saturation to some extent.
               nextRecoveryDelay.set(configuration.getLinkRecoveryDelay() > 0 ? configuration.getLinkRecoveryDelay() : 1);

               manager.tryRecoverSender();
            }
         } finally {
            recoveryFuture = null;
         }
      }
   }
}
