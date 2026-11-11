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

package org.apache.activemq.artemis.core.server.impl.quorum;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.slf4j.Logger;

/**
 * This class contains the activation sequence logic of the pluggable lock manager: it should be used by
 * {@link org.apache.activemq.artemis.core.server.impl.ReplicationBackupActivation} and
 * {@link org.apache.activemq.artemis.core.server.impl.ReplicationPrimaryActivation} to coordinate for replication.
 */
public final class ActivationSequenceStateMachine {

   private static final long CHECK_ACTIVATION_SEQUENCE_WAIT_MILLIS = 200;
   private static final long PRIMARY_LOCK_ACQUIRE_TIMEOUT_MILLIS = 2000;

   private ActivationSequenceStateMachine() {

   }

   /**
    * It loops if the data of the broker is still valuable, but cannot become active. It loops (temporarily) if data is
    * in sync or can auto-repair, but cannot yet acquire the primary lock.
    * <p>
    * It stops loop and return:
    * <ul>
    * <li>{@code null}: if data is stale (and there are no rights to become active)
    * <li>{@code !=null}: if data is in sync/repaired and the {@link DistributedLock} is correctly acquired
    * </ul>
    * After successfully returning from this method ie not null return value, a broker should use
    * {@link #ensureSequentialAccessToNodeData} to complete the activation and guarantee the initial not-replicated
    * ownership of data.
    */
   public static DistributedLock tryActivate(final NodeManager nodeManager,
                                             final DistributedLockManager distributedManager,
                                             final Logger logger) throws InterruptedException, ExecutionException, TimeoutException, UnavailableStateException {
      Objects.requireNonNull(nodeManager);
      Objects.requireNonNull(distributedManager);
      Objects.requireNonNull(logger);
      Objects.requireNonNull(nodeManager.getNodeId());
      final String nodeId = nodeManager.getNodeId().toString();
      final long nodeActivationSequence = nodeManager.getNodeActivationSequence();
      if (nodeActivationSequence < 0) {
         throw new IllegalArgumentException("nodeActivationSequence must be > 0");
      }
      final DistributedLock activationLock = distributedManager.getDistributedLock(nodeId);
      try (MutableLong coordinatedNodeSequence = distributedManager.getMutableLong(nodeId)) {
         while (true) {
            // dirty read is sufficient to know if we are *not* an in sync replica
            // typically the lock owner will increment to signal our data is stale and we are happy without any
            // further coordination at this point
            long timeout = 0;
            switch (validateActivationSequence(coordinatedNodeSequence, activationLock, nodeId, nodeActivationSequence, logger)) {

               case Stale:
                  activationLock.close();
                  return null;
               case InSync:
                  timeout = PRIMARY_LOCK_ACQUIRE_TIMEOUT_MILLIS;
                  break;
               case SelfRepair:
               case MaybeInSync:
                  break;
            }
            // SelfRepair, MaybeInSync, InSync
            if (!activationLock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
               logger.debug("Candidate for Node ID = {}, with local activation sequence: {}, cannot acquire primary lock within {}; retrying",
                             nodeId, nodeActivationSequence, timeout);
               if (timeout == 0) {
                  Thread.sleep(CHECK_ACTIVATION_SEQUENCE_WAIT_MILLIS);
               }
               continue;
            }
            switch (validateActivationSequence(coordinatedNodeSequence, activationLock, nodeId, nodeActivationSequence, logger)) {

               case Stale:
                  activationLock.close();
                  return null;
               case InSync:
                  // we are an in_sync_replica, good to activate as UNREPLICATED
                  logger.info("Assuming active role for NodeID = {}, local activation sequence {} matches current coordinated activation sequence {}", nodeId, nodeActivationSequence, nodeActivationSequence);
                  return activationLock;
               case SelfRepair:
                  // Self-repair sequence
                  logger.info("Assuming active role for NodeID = {}: local activation sequence {} matches claimed coordinated activation sequence {}. Repairing sequence", nodeId, nodeActivationSequence, nodeActivationSequence);
                  try {
                     repairActivationSequence(nodeManager, coordinatedNodeSequence, nodeActivationSequence, true);
                     return activationLock;
                  } catch (NodeManager.NodeManagerException | UnavailableStateException ex) {
                     activationLock.close();
                     throw ex;
                  }
               case MaybeInSync:
                  // Auto-repair sequence
                  logger.warn("Assuming active role for NodeID = {}: repairing claimed activation sequence", nodeId);
                  try {
                     repairActivationSequence(nodeManager, coordinatedNodeSequence, nodeActivationSequence, false);
                     return activationLock;
                  } catch (NodeManager.NodeManagerException | UnavailableStateException ex) {
                     activationLock.close();
                     throw ex;
                  }
            }
         }
      }
   }

   private static void repairActivationSequence(final NodeManager nodeManager,
                                                final MutableLong coordinatedNodeSequence,
                                                final long nodeActivationSequence,
                                                final boolean selfHeal) throws UnavailableStateException {
      final long coordinatedNodeActivationSequence = selfHeal ? nodeActivationSequence : nodeActivationSequence + 1;
      if (!selfHeal) {
         nodeManager.writeNodeActivationSequence(coordinatedNodeActivationSequence);
      }
      coordinatedNodeSequence.set(coordinatedNodeActivationSequence);
   }

   private enum ValidationResult {
      /**
       * coordinated activation sequence (claimed/committed) is far beyond the local one: data is not valuable anymore
       */
      Stale,
      /**
       * coordinated activation sequence is the same as local one: data is in sync
       */
      InSync,
      /**
       * next coordinated activation sequence is not committed yet: maybe data is in sync
       */
      MaybeInSync,
      /**
       * next coordinated activation sequence is not committed yet, but this broker can self-repair: data is in sync
       */
      SelfRepair
   }

   private static ValidationResult validateActivationSequence(final MutableLong coordinatedNodeSequence,
                                                              final DistributedLock activationLock,
                                                              final String lockAndLongId,
                                                              final long nodeActivationSequence,
                                                              final Logger logger) throws UnavailableStateException {
      assert coordinatedNodeSequence.getMutableLongId().equals(lockAndLongId);
      assert activationLock.getLockId().equals(lockAndLongId);
      final long currentCoordinatedNodeSequence = coordinatedNodeSequence.get();
      if (nodeActivationSequence == currentCoordinatedNodeSequence) {
         return ValidationResult.InSync;
      }
      if (currentCoordinatedNodeSequence > 0) {
         logger.info("Not a candidate for NodeID = {} activation, local activation sequence {} does not match coordinated activation sequence {}",
                      lockAndLongId, nodeActivationSequence, currentCoordinatedNodeSequence);
         return ValidationResult.Stale;
      }
      // claimed activation sequence
      final long claimedCoordinatedNodeSequence = -currentCoordinatedNodeSequence;
      final long sequenceGap = claimedCoordinatedNodeSequence - nodeActivationSequence;
      if (sequenceGap == 0) {
         return ValidationResult.SelfRepair;
      }
      if (sequenceGap == 1) {
         // maybe data is still valuable
         return ValidationResult.MaybeInSync;
      }
      assert sequenceGap > 1;
      // sequence is moved so much that data is no longer valuable
      logger.info("Not a candidate for NodeID = {} activation, local activation sequence {} does not match coordinated activation sequence {}",
                   lockAndLongId, nodeActivationSequence, claimedCoordinatedNodeSequence);
      return ValidationResult.Stale;
   }

   /**
    * It wait until {@code timeoutMillis ms} has passed or the coordinated activation sequence has progressed enough
    */
   public static boolean awaitNextCommittedActivationSequence(final DistributedLockManager distributedManager,
                                                              final String coordinatedLockAndNodeId,
                                                              final long activationSequence,
                                                              final long timeoutMills,
                                                              final Logger logger)
                           throws ExecutionException, InterruptedException, TimeoutException, UnavailableStateException {
      Objects.requireNonNull(distributedManager);
      Objects.requireNonNull(logger);
      Objects.requireNonNull(coordinatedLockAndNodeId);
      if (activationSequence < 0) {
         throw new IllegalArgumentException("activationSequence must be >= 0, while is " + activationSequence);
      }
      if (!distributedManager.isStarted()) {
         throw new IllegalStateException("manager must be started");
      }
      final MutableLong coordinatedActivationSequence = distributedManager.getMutableLong(coordinatedLockAndNodeId);
      // wait for the primary to activate and run un replicated with a sequence > inSyncReplicaActivation
      // this read can be dirty b/c we are just looking for an increment.
      boolean anyNext = false;
      final long timeoutNs = TimeUnit.MILLISECONDS.toNanos(timeoutMills);
      final long started = System.nanoTime();
      long elapsedNs;
      do {
         final long coordinatedValue = coordinatedActivationSequence.get();
         if (coordinatedValue > activationSequence) {
            // all good, some activation has gone ahead
            logger.info("Detected a new activation sequence with NodeID = {}: and sequence: {}", coordinatedLockAndNodeId, coordinatedValue);
            anyNext = true;
            break;
         }
         if (coordinatedValue < 0) {
            // commit claim
            final long claimedSequence = -coordinatedValue;
            final long activationsGap = claimedSequence - activationSequence;
            if (activationsGap > 1) {
               // all good, some activation has gone ahead
               logger.info("Detected furthers sequential server activations from sequence {}, with NodeID = {}: and claimed sequence: {}", activationSequence, coordinatedLockAndNodeId, claimedSequence);
               anyNext = true;
               break;
            }
            // activation is still in progress
            logger.debug("Detected claiming of activation sequence = {} for NodeID = {}", claimedSequence, coordinatedLockAndNodeId);
         }
         try {
            TimeUnit.MILLISECONDS.sleep(CHECK_ACTIVATION_SEQUENCE_WAIT_MILLIS);
         } catch (InterruptedException ignored) {
         }
         elapsedNs = System.nanoTime() - started;
      }
      while (elapsedNs < timeoutNs);
      return anyNext;
   }

   /**
    * This is going to increment the coordinated activation sequence and the local activation sequence (using
    * {@link NodeManager#writeNodeActivationSequence}) while holding the primary lock, failing with some exception
    * otherwise.
    * <p>
    * This must be used while holding a primary lock to ensure not-exclusive ownership of data ie can be both used while
    * loosing connectivity with a replica or after successfully {@link #tryActivate}.
    */
   public static void ensureSequentialAccessToNodeData(final String serverDescription,
                                                       final NodeManager nodeManager,
                                                       final DistributedLockManager distributedManager,
                                                       final Logger logger) throws ActiveMQException, InterruptedException, UnavailableStateException, ExecutionException, TimeoutException {

      Objects.requireNonNull(serverDescription);
      Objects.requireNonNull(nodeManager);
      Objects.requireNonNull(distributedManager);
      Objects.requireNonNull(logger);
      final String lockAndLongId = Objects.toString(nodeManager.getNodeId(), null);
      Objects.requireNonNull(lockAndLongId);
      final long nodeActivationSequence = nodeManager.getNodeActivationSequence();
      if (nodeActivationSequence < 0) {
         throw new IllegalArgumentException("nodeActivationSequence must be >= 0");
      }
      final DistributedLock primaryLock = distributedManager.getDistributedLock(lockAndLongId);
      if (!primaryLock.isHeldByCaller()) {
         final String message = String.format("Server [%s] primary lock for NodeID = %s not held. Activation sequence cannot be safely changed",
                                              serverDescription, lockAndLongId);
         logger.info(message);
         throw new UnavailableStateException(message);
      }
      final MutableLong coordinatedNodeActivationSequence = distributedManager.getMutableLong(lockAndLongId);
      final long nextActivationSequence = nodeActivationSequence + 1;
      // UN_REPLICATED STATE ENTER: auto-repair doesn't need to claim and write locally
      // claim
      if (!coordinatedNodeActivationSequence.compareAndSet(nodeActivationSequence, -nextActivationSequence)) {
         final String message = String.format("Server [%s], cannot assume active role for NodeID = %s, activation sequence claim failed, local activation sequence %d no longer matches current coordinated sequence %d",
                                              serverDescription, lockAndLongId, nodeActivationSequence, coordinatedNodeActivationSequence.get());
         logger.info(message);
         throw new ActiveMQException(message);
      }
      // claim success: write locally
      try {
         nodeManager.writeNodeActivationSequence(nextActivationSequence);
      } catch (NodeManager.NodeManagerException fatal) {
         logger.error("Server [{}] failed to set local activation sequence to: {} for NodeId ={}. Cannot continue committing coordinated activation sequence: REQUIRES ADMIN INTERVENTION",
                       serverDescription, nextActivationSequence, lockAndLongId);
         throw new UnavailableStateException(fatal);
      }
      logger.info("Server [{}], incremented local activation sequence to: {} for NodeId = {}",
                   serverDescription, nextActivationSequence, lockAndLongId);
      // commit
      if (!coordinatedNodeActivationSequence.compareAndSet(-nextActivationSequence, nextActivationSequence)) {
         final String message = String.format("Server [%s], cannot assume active role for NodeID = %s, activation sequence commit failed, local activation sequence %d no longer matches current coordinated sequence %d",
                                              serverDescription, lockAndLongId, nodeActivationSequence, coordinatedNodeActivationSequence.get());
         logger.info(message);
         throw new ActiveMQException(message);
      }
      logger.info("Server [{}], incremented coordinated activation sequence to: {} for NodeId = {}",
                   serverDescription, nextActivationSequence, lockAndLongId);
   }

}
