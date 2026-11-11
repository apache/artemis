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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base for managing federation consumer instances that holds the demand currently present for a federated
 * resource and the consumer that exists to service that demand. This object manages the state of the consumer and the
 * various stages it can pass through during its lifetime.
 * <p>
 * All interactions with the consumer tracking entry should occur under the lock of the parent manager instance and this
 * manager will perform any asynchronous work with a lock held on the parent manager instance.
 *
 * @param <E> The type used in the demand tracking collection.
 */
public abstract class AMQPFederationConsumerManager<E, Consumer extends AMQPFederationConsumer> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private enum State {
      READY,
      STARTING,
      STARTED,
      STOPPING,
      STOPPED,
      CLOSED
   }

   protected final AMQPFederation federation;
   protected final AMQPFederationLocalPolicyManager manager;

   private final Set<E> demandTracking = new HashSet<>();
   private State state = State.READY;
   private Consumer consumer;
   private ScheduledFuture<?> pendingIdleTimeout;

   public AMQPFederationConsumerManager(AMQPFederationLocalPolicyManager manager) {
      this.manager = manager;
      this.federation = manager.getFederation();
   }

   /**
    * An orderly shutdown of the federation consumer which will perform a drain of link credit before closing the
    * consumer to ensure that all in-flight messages and dispositions are processed before the link is detached.
    * <p>
    * The federation manager should be calling this method with its lock held.
    */
   public void shutdown() {
      handleShutdown(true);
   }

   /**
    * An immediate close of the federation consumer which does not drain link credit or wait for any pending operations
    * to complete.
    * <p>
    * The federation manager should be calling this method with its lock held.
    */
   public void shutdownNow() {
      handleShutdown(false);
   }

   private void handleShutdown(boolean stopFirst) {
      if (state != State.CLOSED) {
         state = State.CLOSED;

         try {
            if (consumer == null) {
               return;
            } else if (stopFirst && state != State.STOPPED) {
               tryStopFederationConsumer();
            } else {
               safeCloseCurrentFederationConsumer();
            }
         } finally {
            consumer = null;

            demandTracking.forEach(entry -> {
               try {
                  whenDemandTrackingEntryRemoved(entry, consumer);
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
         }
      }
   }

   /**
    * Attempt to recover a stopped consumer if this manager is not in the closed state and there is active demand
    * registered. When in the stopped state the existing consumer will be restarted and if no active consumer exists
    * then a new consumer is created. If this is called while a consumer is in the started state then this operation is
    * a no-op and the consumer is left as is.
    * <p>
    * The federation manager should be calling this method with its lock held.
    */
   public void recover() {
      checkClosed();

      if (hasDemand()) {
         // The transition states will check on the transition point to see if any
         // action is needed such as restarting or recreating the consumer.
         if (state == State.STOPPED) {
            tryRestartFederationConsumer();
         } else if (state == State.READY) {
            tryCreateFederationConsumer();
         }
      }
   }

   /**
    * Add new demand to the consumer manager which creates or sustains the consumer lifetime that this manager
    * maintains. When the first element of demand is added a new consumer is attached and when the last unit of demand
    * is removed the consumer will be closed.
    * <p>
    * The federation manager should be calling this method with its lock held.
    *
    * @param demand A new unit of demand to add to this consumer manager.
    */
   public void addDemand(E demand) {
      checkClosed();

      if (demandTracking.add(demand)) {
         whenDemandTrackingEntryAdded(demand, consumer);
      }

      // This will create a new consumer only if there isn't one currently assigned to this entry and any configured
      // federation plugins don't block it from doing so. An already stopping consumer will check on stop if it should
      // restart.
      if (state == State.STOPPED) {
         tryRestartFederationConsumer();
      } else if (state == State.READY) {
         tryCreateFederationConsumer();
      }
   }

   /**
    * Remove the given element from the tracked consumer demand. If the tracked demand reaches zero then the managed
    * consumer should be closed and the manager awaits future demand to be added. The manager can opt to hold a stopped
    * consumer open for some period of time to avoid spurious open and closes as demand is added and removed.
    * <p>
    * The federation manager should be calling this method with its lock held.
    *
    * @param demand The element of demand that should be removed from tracking.
    */
   public void removeDemand(E demand) {
      checkClosed();

      if (demandTracking.remove(demand)) {
         whenDemandTrackingEntryRemoved(demand, consumer);
      }

      if (hasDemand() || state == State.READY) {
         return;
      } else if (state != State.STOPPING) {
         tryStopFederationConsumer();
      }
   }

   // Must be called with locks in place from the parent manager to prevent concurrent access to state changing APIs.
   private void tryCreateFederationConsumer() {
      if (!isPluginBlockingFederationConsumerCreate()) {
         state = State.STARTING;
         consumer = createFederationConsumer(Collections.unmodifiableSet(demandTracking));

         logger.trace("Federation Consumer manager creating remote consumer for: {}", consumer.getConsumerInfo());

         manager.signalPluginBeforeCreateFederationConsumer(consumer.getConsumerInfo());

         // For a new consumer the broker will start granting credit after it has been opened.
         consumer.setRemoteOpenHandler((openedConsumer) -> {
            synchronized (manager) {
               // Stop or close could have followed quick enough that this didn't get completed yet.
               if (state == State.STARTING) {
                  state = State.STARTED;
               }
            }
         });

         // Handle remote close with remove of consumer which means that future demand will attempt to create a new
         // consumer for that demand. Ensure that thread safety is accounted for here as the notification can be
         // asynchronous. We do not automatically recreate a consumer here as we anticipate the remote will send an
         // event when there is an update for resources we are interested in or we will close the connection due to a
         // close happening for an unexplained or unanticipated reason. This event only files if the consumer wasn't
         // locally closed first.
         consumer.setRemoteClosedHandler((closedConsumer) -> {
            synchronized (manager) {
               safeCloseCurrentFederationConsumer();
            }
         });

         consumer.initialize();

         manager.signalPluginAfterCreateFederationConsumer(consumer);
      } else {
         state = State.READY;
      }
   }

   // Must be called with locks in place from the parent manager to prevent concurrent access to state changing APIs.
   private void tryRestartFederationConsumer() {
      state = State.STARTING;

      try {
         if (pendingIdleTimeout != null) {
            pendingIdleTimeout.cancel(false);
            pendingIdleTimeout = null;
         }

         consumer.startAsync(new AMQPFederationAsyncCompletion<AMQPFederationConsumer>() {

            @Override
            public void onComplete(AMQPFederationConsumer consumer) {
               logger.trace("Restarted federation consumer after new demand added.");
               synchronized (manager) {
                  // Check in case the consumer was closed or stopped before we got here
                  if (state == State.STARTING) {
                     state = State.STARTED;
                  }
               }
            }

            @Override
            public void onException(AMQPFederationConsumer consumer, Exception error) {
               if (error instanceof IllegalStateException) {
                  // The receiver might be stopping or it could be closed, either of which
                  // was initiated from this manager so we can ignore and let those complete.
                  return;
               } else {
                  // This is unexpected and our reaction is to close the consumer since we
                  // have no idea what its state is now. Later new demand or remote events
                  // will trigger a new consumer to get added.
                  logger.trace("Start of federation consumer {} threw unexpected error, closing consumer: ", consumer, error);
                  synchronized (manager) {
                     safeCloseCurrentFederationConsumer();
                  }
               }
            }
         });
      } catch (Exception ex) {
         // The consumer might have been remotely closed, we can't be certain but since we
         // are responding to demand having been added we will close it and clear the entry
         // so that the follow on code can try and create a new one.
         logger.trace("Caught error on attempted restart of existing federation consumer", ex);
         safeCloseCurrentFederationConsumer();
         if (state == State.READY) {
            tryCreateFederationConsumer();
         }
      }
   }

   // Must be called with locks in place from the parent manager to prevent concurrent access to state changing APIs.
   private void tryStopFederationConsumer() {
      if (consumer != null && state != State.STOPPING) {
         // Retain closed state if close is attempting a safe shutdown.
         state = state == State.CLOSED ? State.CLOSED : State.STOPPING;

         consumer.stopAsync(new AMQPFederationAsyncCompletion<AMQPFederationConsumer>() {

            @Override
            public void onComplete(AMQPFederationConsumer consumer) {
               logger.trace("Stop of federation consumer {} succeeded, consumer: ", consumer);
               synchronized (manager) {
                  handleFederationConsumerStopped(consumer, true);
               }
            }

            @Override
            public void onException(AMQPFederationConsumer consumer, Exception error) {
               logger.trace("Stop of federation consumer {} failed, closing consumer: ", consumer, error);
               synchronized (manager) {
                  handleFederationConsumerStopped(consumer, false);
               }
            }
         });
      }
   }

   // Must be called with locks in place from the parent manager to prevent concurrent access to state changing APIs.
   private void handleFederationConsumerStopped(AMQPFederationConsumer stoppedConsumer, boolean didStop) {
      // Remote close or local resource remove could have beaten us here and already cleaned up the consumer.
      if (state == State.STOPPING) {
         // If the consumer stop times out then we assume something has gone wrong and we force close it
         // now and then decide below if we should recreate it or not.
         if (!didStop) {
            safeCloseCurrentFederationConsumer();
         } else {
            state = State.STOPPED;
         }

         // Demand may have returned while the consumer was stopping in which case
         // we either restart an existing stopped consumer or recreate if the stop
         // timed out and we closed it above. Otherwise we check for an idle timeout
         // configuration and either schedule the close for later or close it now.
         if (state == State.READY) {
            if (hasDemand()) {
               tryCreateFederationConsumer();
            }
         } else if (state == State.STOPPED) {
            if (hasDemand()) {
               tryRestartFederationConsumer();
            } else if (consumer.getReceiverIdleTimeout() > 0) {
               pendingIdleTimeout = federation.getScheduler().schedule(() -> {
                  synchronized (manager) {
                     logger.debug("Federation consumer {} idle timeout reached, closing now", consumer.getConsumerInfo());

                     if (state == State.STOPPED) {
                        safeCloseCurrentFederationConsumer();
                        pendingIdleTimeout = null;
                     }
                  }
               }, consumer.getReceiverIdleTimeout(), TimeUnit.MILLISECONDS);
            } else {
               safeCloseCurrentFederationConsumer();
            }
         }
      } else if (state == State.CLOSED) {
         safeCloseAnyFederationConsumer(stoppedConsumer);
      }
   }

   private void safeCloseCurrentFederationConsumer() {
      if (consumer != null) {
         try {
            safeCloseAnyFederationConsumer(consumer);
         } finally {
            state = state == State.CLOSED ? State.CLOSED : State.READY;

            if (pendingIdleTimeout != null) {
               pendingIdleTimeout.cancel(false);
               pendingIdleTimeout = null;
            }
         }
      }
   }

   private void safeCloseAnyFederationConsumer(AMQPFederationConsumer consumer) {
      if (consumer != null) {
         try {
            if (!consumer.isClosed()) {
               manager.signalPluginBeforeCloseFederationConsumer(consumer);
               consumer.close();
               manager.signalPluginAfterCloseFederationConsumer(consumer);
            }
         } catch (Exception e) {
            logger.trace("Suppressed error on close of federation consumer. ", e);
         }
      }
   }

   private boolean hasDemand() {
      return !demandTracking.isEmpty();
   }

   private void checkClosed() {
      if (state == State.CLOSED) {
         throw new IllegalStateException("The federated consumer has been closed already");
      }
   }

   /**
    * An event point that a subclass can use to perform an initialization action whenever an entry is added to
    * demand tracking.
    *
    * @param entry
    *    The entry that is being added to demand tracking.
    * @param consumer
    *    The currently active federation consumer (can be null if no consumer).
    */
   protected abstract void whenDemandTrackingEntryAdded(E entry, Consumer consumer);

   /**
    * An event point that a subclass can use to perform a cleanup action whenever an entry is removed from
    * demand tracking.
    *
    * @param entry
    *    The entry that is being removed from demand tracking.
    * @param consumer
    *    The currently active federation consumer (can be null if no consumer).
    */
   protected abstract void whenDemandTrackingEntryRemoved(E entry, Consumer consumer);

   /**
    * Creates a new federation consumer that this manager will monitor and maintain. The returned consumer should be in
    * an initial state ready for this manager to initialize once it is fully configured.
    *
    * @param currentDemand
    *    Unmodifiable {@link Set} of entries that account for the demand on the consumer being created.
    *
    * @return a newly create {@link AMQPFederationConsumer} for use by this manager
    */
   protected abstract Consumer createFederationConsumer(Set<E> currentDemand);

   /**
    * Query all registered plugins for this federation instance to determine if any wish to prevent a federation
    * consumer from being created for the given resource managed by the implementation class.
    *
    * @return {@code true} if any registered plugin signaled that creation should be suppressed
    */
   protected abstract boolean isPluginBlockingFederationConsumerCreate();

}
