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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base for managing bridge receiver instances that holds the demand
 * currently present for a bridged resource and the receiver that exists to service
 * that demand. This object manages the state of the receiver and the various stages
 * it can pass through during its lifetime.
 *
 * All interactions with the receiver tracking entry should occur under the lock of
 * the parent manager instance and this manager will perform any asynchronous work
 * with a lock held on the parent manager instance.
 *
 * @param <E> Type of object stored in the demand tracking map.
 */
public abstract class AMQPBridgeReceiverManager<E> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private enum State {
      READY,
      STARTING,
      STARTED,
      STOPPING,
      STOPPED,
      CLOSED
   }

   private final AMQPBridgeManager bridgeManager;
   private final AMQPBridgeFromPolicyManager policyManager;
   private final AMQPBridgeReceiverConfiguration configuration;
   private final Set<E> demandTracking = new HashSet<>();

   private boolean forcedDemand;
   private State state = State.READY;
   private AMQPBridgeReceiver receiver;
   private ScheduledFuture<?> pendingIdleTimeout;
   private AMQPBridgeReceiverRecoveryHandler recoveryHandler;

   /**
    * Create a new bridge receiver manager instance.
    *
    * @param policyManager
    *    The policy manager that created this instance.
    * @param configuration
    *    The configuration that was in force when the manager was created.
    */
   public AMQPBridgeReceiverManager(AMQPBridgeFromPolicyManager policyManager, AMQPBridgeReceiverConfiguration configuration) {
      this.policyManager = policyManager;
      this.configuration = configuration;
      this.bridgeManager = policyManager.getBridgeManager();
   }

   /**
    * Creates a new bridge receiver that this manager will monitor and maintain. The returned
    * receiver should be in an initial state ready for this manager to initialize once it is fully
    * configured.
    *
    * @return a newly create {@link AMQPBridgeReceiver} for use by this manager.
    */
   protected abstract AMQPBridgeReceiver createBridgeReceiver();

   /**
    * An event point that a subclass can use to perform an initialization action whenever an entry is added to
    * demand tracking.
    *
    * @param entry The entry that is being added to demand tracking.
    */
   protected abstract void whenDemandTrackingEntryAdded(E entry);

   /**
    * An event point that a subclass can use to perform a cleanup action whenever an entry is removed from
    * demand tracking.
    *
    * @param entry The entry that is being removed from demand tracking.
    */
   protected abstract void whenDemandTrackingEntryRemoved(E entry);

   /**
    * An orderly shutdown of the bridge receiver which will perform a drain
    * of link credit before closing the receiver to ensure that all in-flight
    * messages and dispositions are processed before the link is detached.
    *
    * The bridge manager should be calling this method with its lock held.
    */
   public void shutdown() {
      handleShutdown(true);
   }

   /**
    * An immediate close of the bridge receiver which does not drain link credit
    * or wait for any pending operations to complete.
    *
    * The bridge manager should be calling this method with its lock held.
    */
   public void shutdownNow() {
      handleShutdown(false);
   }

   private void handleShutdown(boolean stopFirst) {
      if (state != State.CLOSED) {
         state = State.CLOSED;

         try {
            if (receiver == null) {
               return;
            } else if (stopFirst && state != State.STOPPED) {
               tryStopBridgeReceiver();
            } else {
               safeCloseCurrentBridgeReceiver();
            }
         } finally {
            receiver = null;

            demandTracking.forEach(entry -> {
               try {
                  whenDemandTrackingEntryRemoved(entry);
               } catch (Exception ignore) {
               }
            });
            demandTracking.clear();

            // Stop handler in closed state won't schedule idle timeout, ensure
            // any pending task is canceled just for proper cleanup purposes.
            if (pendingIdleTimeout != null) {
               pendingIdleTimeout.cancel(false);
               pendingIdleTimeout = null;
            }

            closeRecoveryHandler();
         }
      }
   }

   /**
    * Forces demand for this manager instance meaning that a consumer will be maintained until
    * the manager is shutdown.
    */
   public void forceDemand() {
      if (!forcedDemand) {
         forcedDemand = true;

         // This will create a new receiver only if there isn't one currently assigned to this
         // entry. An already stopping receiver will check on stop if it should restart.
         if (state == State.STOPPED) {
            tryRestartBridgeReceiver();
         } else if (state == State.READY) {
            tryCreateBridgeReceiver();
         }
      }
   }

   /**
    * Add new demand to the receiver manager which creates or sustains the receiver lifetime
    * that this manager maintains. When the first element of demand is added a new receiver
    * is attached and when the last unit of demand is removed the receiver will be closed.
    *
    * The bridge manager should be calling this method with its lock held.
    *
    * @param demand
    *    A new unit of demand to add to this receiver manager.
    */
   public void addDemand(E demand) {
      checkClosed();

      if (demandTracking.add(demand)) {
         whenDemandTrackingEntryAdded(demand);
      }

      // This will create a new receiver only if there isn't one currently assigned to this
      // entry. An already stopping receiver will check on stop if it should restart.
      if (state == State.STOPPED) {
         tryRestartBridgeReceiver();
      } else if (state == State.READY) {
         tryCreateBridgeReceiver();
      }
   }

   /**
    * Remove the given element from the tracked receiver demand. If the tracked demand reaches
    * zero then the managed receiver should be closed and the manager awaits future demand to
    * be added. The manager can opt to hold a stopped receiver open for some period of time to
    * avoid spurious open and closes as demand is added and removed.
    *
    * The bridge manager should be calling this method with its lock held.
    *
    * @param demand
    *    The element of demand that should be removed from tracking.
    */
   public void removeDemand(E demand) {
      checkClosed();

      if (demandTracking.remove(demand)) {
         whenDemandTrackingEntryRemoved(demand);
      }

      if (hasDemand() || state == State.READY) {
         return;
      } else if (state != State.STOPPING) {
         tryStopBridgeReceiver();
      }
   }

   /*
    * Must be called with locks in place from the parent manager to prevent concurrent
    * access to state changing APIs.
    */
   private void tryCreateBridgeReceiver() {
      state = State.STARTING;
      receiver = createBridgeReceiver();

      logger.trace("Bridge receiver manager creating remote receiver for: {}", receiver.getReceiverInfo());

      // For a new receiver the broker will start granting credit after it has been opened.
      receiver.setRemoteOpenHandler((openedReceiver) -> {
         synchronized (policyManager) {
            // Stop or close could have followed quick enough that this didn't get completed yet.
            if (state == State.STARTING) {
               state = State.STARTED;
            }

            closeRecoveryHandler();
         }
      });

      // Remote close handling involves closing the active receiver and if demand is still
      // present and configuration allows for a recovery we schedule handling for that case.
      // If no recovery is initiated a new receiver will be attempted when the next element
      // of demand is added.
      receiver.setRemoteClosedHandler((closedReceiver) -> {
         synchronized (policyManager) {
            safeCloseCurrentBridgeReceiver();

            if (hasDemand() && configuration.isLinkRecoveryEnabled() && state == State.READY) {
               // If the close came from a previous attempt that is itself a recovery we use the
               // existing recovery handler, otherwise we need to create a new handler to deal
               // with link recovery and track attempt counts.
               if (recoveryHandler == null) {
                  recoveryHandler = new AMQPBridgeReceiverRecoveryHandler(this, configuration);
               }

               final boolean scheduled = recoveryHandler.tryScheduleNextRecovery(bridgeManager.getScheduler());

               if (!scheduled) {
                  closeRecoveryHandler();
               }
            }
         }
      });

      receiver.initialize();
   }

   /*
    * Must be called with locks in place from the parent manager to prevent concurrent
    * access to state changing APIs.
    */
   private void tryRestartBridgeReceiver() {
      state = State.STARTING;

      try {
         if (pendingIdleTimeout != null) {
            pendingIdleTimeout.cancel(false);
            pendingIdleTimeout = null;
         }

         receiver.startAsync(new AMQPBridgeAsyncCompletion<AMQPBridgeReceiver>() {

            @Override
            public void onComplete(AMQPBridgeReceiver receiver) {
               logger.trace("Restarted Bridge receiver after new demand added.");
               synchronized (policyManager) {
                  // Check in case the receiver was closed or stopped before we got here
                  if (state == State.STARTING) {
                     state = State.STARTED;
                  }
               }
            }

            @Override
            public void onException(AMQPBridgeReceiver receiver, Exception error) {
               if (error instanceof IllegalStateException) {
                  // The receiver might be stopping or it could be closed, either of which
                  // was initiated from this manager so we can ignore and let those complete.
                  return;
               } else {
                  // This is unexpected and our reaction is to close the receiver since we
                  // have no idea what its state is now. Later new demand or remote events
                  // will trigger a new receiver to get added.
                  logger.trace("Start of bridge receiver {} threw unexpected error, closing receiver: ", receiver, error);
                  synchronized (policyManager) {
                     safeCloseCurrentBridgeReceiver();
                  }
               }
            }
         });
      } catch (Exception ex) {
         // The receiver might have been remotely closed, we can't be certain but since we
         // are responding to demand having been added we will close it and clear the entry
         // so that the follow on code can try and create a new one.
         logger.trace("Caught error on attempted restart of existing bridge receiver", ex);
         safeCloseCurrentBridgeReceiver();
         if (state == State.READY) {
            tryCreateBridgeReceiver();
         }
      }
   }

   /*
    * Must be called with locks in place from the parent manager to prevent concurrent
    * access to state changing APIs.
    */
   private void tryStopBridgeReceiver() {
      if (receiver != null && state != State.STOPPING) {
         // Retain closed state if close is attempting a safe shutdown.
         state = state == State.CLOSED ? State.CLOSED : State.STOPPING;

         receiver.stopAsync(new AMQPBridgeAsyncCompletion<AMQPBridgeReceiver>() {

            @Override
            public void onComplete(AMQPBridgeReceiver receiver) {
               logger.trace("Stop of bridge receiver {} succeeded, receiver: ", receiver);
               synchronized (policyManager) {
                  handleBridgeReceiverStopped(receiver, true);
               }
            }

            @Override
            public void onException(AMQPBridgeReceiver receiver, Exception error) {
               logger.trace("Stop of bridge receiver {} failed, closing receiver: ", receiver, error);
               synchronized (policyManager) {
                  handleBridgeReceiverStopped(receiver, false);
               }
            }
         });
      }
   }

   /*
    * Must be called with locks in place from the parent manager to prevent concurrent
    * access to state changing APIs.
    */
   private void handleBridgeReceiverStopped(AMQPBridgeReceiver stoppedReceiver, boolean didStop) {
      // Remote close or local resource remove could have beaten us here and already cleaned up the receiver.
      if (state == State.STOPPING) {
         // If the receiver stop times out then we assume something has gone wrong and we force close it
         // now and then decide below if we should recreate it or not.
         if (!didStop) {
            safeCloseCurrentBridgeReceiver();
         } else {
            state = State.STOPPED;
         }

         // Demand may have returned while the receiver was stopping in which case
         // we either restart an existing stopped receiver or recreate if the stop
         // timed out and we closed it above. Otherwise we check for an idle timeout
         // configuration and either schedule the close for later or close it now.
         if (state == State.READY) {
            if (hasDemand()) {
               tryCreateBridgeReceiver();
            }
         } else if (state == State.STOPPED) {
            if (hasDemand()) {
               tryRestartBridgeReceiver();
            } else if (receiver.getReceiverIdleTimeout() > 0) {
               pendingIdleTimeout = bridgeManager.getScheduler().schedule(() -> {
                  synchronized (policyManager) {
                     logger.debug("Bridge receiver {} idle timeout reached, closing now", receiver.getReceiverInfo());

                     if (state == State.STOPPED) {
                        safeCloseCurrentBridgeReceiver();
                        pendingIdleTimeout = null;
                     }
                  }
               }, receiver.getReceiverIdleTimeout(), TimeUnit.MILLISECONDS);
            } else {
               safeCloseCurrentBridgeReceiver();
            }
         }
      } else if (state == State.CLOSED) {
         safeCloseCurrentBridgeReceiver(stoppedReceiver);
      }
   }

   /*
    * Must lock on the policy manager to ensure that state changes occur in safe conditions.
    */
   private void tryRecoverReceiver() {
      synchronized (policyManager) {
         // Recovery only needs to occur if we are in the READY state, meaning not stopped or closed
         // and we still have demand, otherwise we can end recovery operations. The recovery handler
         // is normally closed out on a successful remote attach response.
         if (state == State.READY && hasDemand()) {
            tryCreateBridgeReceiver();
         } else {
            closeRecoveryHandler();
         }
      }
   }

   private void safeCloseCurrentBridgeReceiver() {
      try {
         safeCloseCurrentBridgeReceiver(receiver);
      } finally {
         state = state == State.CLOSED ? State.CLOSED : State.READY;
         receiver = null;

         if (pendingIdleTimeout != null) {
            pendingIdleTimeout.cancel(false);
            pendingIdleTimeout = null;
         }
      }
   }

   private void safeCloseCurrentBridgeReceiver(AMQPBridgeReceiver receiver) {
      if (receiver != null) {
         try {
            if (!receiver.isClosed()) {
               receiver.close();
            }
         } catch (Exception e) {
            logger.trace("Suppressed error on close of bridge receiver. ", e);
         }
      }
   }

   private boolean hasDemand() {
      return !demandTracking.isEmpty() || forcedDemand;
   }

   private void checkClosed() {
      if (state == State.CLOSED) {
         throw new IllegalStateException("The bridge receiver has been closed already");
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

   public static class AMQPBridgeReceiverRecoveryHandler implements Closeable {

      private final AMQPBridgeReceiverManager<?> manager;
      private final AMQPBridgeLinkConfiguration configuration;
      private final int maxRecoveryAttempts;

      private final AtomicInteger recoveryAttempts = new AtomicInteger();
      private final AtomicLong nextRecoveryDelay = new AtomicLong();
      private volatile ScheduledFuture<?> recoveryFuture;

      public AMQPBridgeReceiverRecoveryHandler(AMQPBridgeReceiverManager<?> manager,
                                               AMQPBridgeLinkConfiguration configuration) {
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

               manager.tryRecoverReceiver();
            }
         } finally {
            recoveryFuture = null;
         }
      }
   }
}
