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

package org.apache.artemis.lock.kube;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.lockmanager.AbstractDistributedLock;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesApiException;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesClient;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesConflictException;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesInternalException;
import org.apache.artemis.lock.kube.client.LockKubeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubeLock extends AbstractDistributedLock {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Kubernetes expects RFC3339 format with microsecond precision (6 digits)
   private static final DateTimeFormatter KUBERNETES_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");

   final String hostname;
   final String namespace;
   final String id;
   private final int leasePeriodSeconds;

   // Local observation cache to handle clock skew (Kubernetes client-go approach)
   // We only trust our local clock for measuring elapsed time since last observation
   private String lastObservedHolder;
   private String lastObservedRenewTime;
   private OffsetDateTime localObservationTime;


   public KubeLock(String hostname, String namespace, String id, int leasePeriodSeconds) {
      this.hostname = hostname;
      this.namespace = namespace;
      this.id = id;
      this.leasePeriodSeconds = leasePeriodSeconds;
   }

   @Override
   public String getLockId() {
      return id;
   }

   protected OffsetDateTime currentTime() {
      return OffsetDateTime.now(ZoneOffset.UTC);
   }

   @Override
   public boolean isHeldByCaller() throws UnavailableStateException {
      if (logger.isDebugEnabled()) {
         logger.debug("isHeldByCaller called, debugInfo={}", getDebugInfo());
      }
      try {
         boolean result = renewLock();
         if (logger.isDebugEnabled()) {
            logger.debug("isHeldByCaller returning {}, debugInfo={}", result, getDebugInfo());
         }
         return result;
      } catch (Exception e) {
         throw new UnavailableStateException(e.getMessage(), e);
      }
   }

   public int getLeaseTransitions() throws KubernetesApiException {
      // Try to read the existing lease
      JsonObject existingLease = LockKubeClient.getLease(namespace, id);

      if (existingLease == null) {
         throw new KubernetesInternalException("Lease not found: " + id);
      }

      JsonObject spec = existingLease.getJsonObject("spec");
      if (spec == null) {
         throw new KubernetesInternalException("Lease spec is null for lease: " + id);
      }

      return spec.getInt("leaseTransitions", 0);
   }

   private boolean renewLock() throws UnavailableStateException {
      try {
         // Try to read the existing lease
         JsonObject existingLease = LockKubeClient.getLease(namespace, id);

         if (existingLease == null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Create lock, debugInfo={}", getDebugInfo());
            }
            try {
               String nowTime = currentTime().format(KUBERNETES_TIME_FORMATTER);
               LockKubeClient.createLease(namespace, id, hostname, nowTime, nowTime, leasePeriodSeconds, 0);
               if (logger.isDebugEnabled()) {
                  logger.debug("Successfully created new lease, debugInfo={}", getDebugInfo());
               }
               return true;
            } catch (KubernetesConflictException e) {
               // Someone else created it first - this is a normal race condition
               if (logger.isDebugEnabled()) {
                  logger.debug("Lease was created by another instance, will try to acquire on next attempt, debugInfo={}", getDebugInfo(), e);
               }
               return false;
            }
         }

         final JsonObject currentSpec = existingLease.getJsonObject("spec");
         if (currentSpec == null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Lease spec is null, debugInfo={}", getDebugInfo());
            }
            return false;
         }

         final String holderIdentity = currentSpec.getString("holderIdentity", null);
         final String renewTimeStr = currentSpec.getString("renewTime", null);
         final int leaseDuration = currentSpec.getInt("leaseDurationSeconds", (int) leasePeriodSeconds);
         final int currentLeaseTransitions = currentSpec.getInt("leaseTransitions", 0);

         if (logger.isDebugEnabled()) {
            logger.debug("renewLock, Read lease: renewTime={}, holderIdentity={}, leaseDuration={}, leaseTransitions={}, debugInfo={}",
                        renewTimeStr, holderIdentity, leaseDuration, currentLeaseTransitions, getDebugInfo());
         }

         // Check if we already hold this lease
         if (hostname.equals(holderIdentity)) {
            // Renew the lease - keep the same leaseTransitions count
            String resourceVersion = KubernetesClient.getResourceVersion(existingLease);

            try {
               String renewTime = currentTime().format(KUBERNETES_TIME_FORMATTER);
               LockKubeClient.renewLease(namespace, id, resourceVersion, holderIdentity,
                                       currentSpec.getString("acquireTime", renewTime), renewTime,
                                       leaseDuration, currentLeaseTransitions);
               if (logger.isDebugEnabled()) {
                  logger.debug("RenewLease successful on {}", getDebugInfo());
               }
               return true;
            } catch (KubernetesConflictException e) {
               // Resource version conflict - another thread updated the lease first
               if (logger.isDebugEnabled()) {
                  logger.debug("Conflict renewing lease, will retry on next attempt, debugInfo={}", getDebugInfo(), e);
               }
               return false;
            }
         }

         // Try to acquire the lease if it has expired using clock-skew-tolerant approach
         if (tryAcquireExpiredLease(existingLease, holderIdentity, renewTimeStr, leaseDuration, currentLeaseTransitions)) {
            return true;
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Lease is held by someone else and not expired, debugInfo={}", getDebugInfo());
         }

         // Lease is held by someone else and not expired
         return false;

      } catch (KubernetesApiException e) {
         if (logger.isDebugEnabled()) {
            logger.debug("KubernetesApiException in renewLock, debugInfo={}", getDebugInfo(), e);
         }
         logger.warn(e.getMessage(), e);
         throw new UnavailableStateException("Failed to renew lock", e);
      }
   }

   /**
    * Attempts to acquire a lease that may have expired.
    * Uses local observation timestamps to avoid clock skew issues between nodes.
    * This follows the Kubernetes client-go approach: we only trust our local clock
    * for measuring elapsed time, not remote timestamps in the lease spec.
    *
    * @param existingLease the current lease state from the API server
    * @param holderIdentity the current holder of the lease
    * @param renewTimeStr the renewTime from the lease spec
    * @param leaseDuration the lease duration in seconds
    * @param currentLeaseTransitions the current leaseTransitions count
    * @return true if we successfully acquired the lease, false otherwise
    */
   private boolean tryAcquireExpiredLease(JsonObject existingLease, String holderIdentity, String renewTimeStr, int leaseDuration, int currentLeaseTransitions) {
      if (renewTimeStr == null) {
         return false;
      }

      logger.debug("executing tryAcquireExpiredLease");

      OffsetDateTime now = currentTime();

      // Check if the lease state has changed (different holder or renewed)
      boolean leaseChanged = hasLeaseChanged(holderIdentity, renewTimeStr);

      if (leaseChanged) {
         // Record this observation locally with our own timestamp
         lastObservedHolder = holderIdentity;
         lastObservedRenewTime = renewTimeStr;
         localObservationTime = now;

         if (logger.isDebugEnabled()) {
            logger.debug("Lease changed - recording new observation: holder={}, renewTime={}, debugInfo={}",
                        holderIdentity, renewTimeStr, getDebugInfo());
         }

         return false; // Not expired - just observed a change
      }

      // Lease hasn't changed - check if enough local time has passed since our last observation
      if (localObservationTime != null) {
         long localElapsedSeconds = Duration.between(localObservationTime, now).toSeconds();

         if (logger.isDebugEnabled()) {
            logger.debug("Checking lease expiration: holder={}, local elapsed={}s, lease duration={}s, debugInfo={}",
                        holderIdentity, localElapsedSeconds, leaseDuration, getDebugInfo());
         }

         if (localElapsedSeconds >= leaseDuration) {
            // Expired based on our local observation - try to acquire
            logger.debug("Lease expired locally: holder={}, local elapsed={}s, lease duration={}s, debugInfo={}",
                        holderIdentity, localElapsedSeconds, leaseDuration, getDebugInfo());

            OffsetDateTime newTime = currentTime();
            String newRenewTime = newTime.format(KUBERNETES_TIME_FORMATTER);
            String acquireTime = newTime.format(KUBERNETES_TIME_FORMATTER);

            String resourceVersion = KubernetesClient.getResourceVersion(existingLease);

            // Increment leaseTransitions when acquiring from a different holder
            int newLeaseTransitions = currentLeaseTransitions + 1;

            try {
               LockKubeClient.renewLease(namespace, id, resourceVersion, hostname, acquireTime, newRenewTime,
                                       leasePeriodSeconds, newLeaseTransitions);
               if (logger.isDebugEnabled()) {
                  logger.debug("Successfully acquired expired lease, local elapsed={}s, lease duration={}s, leaseTransitions={}, debugInfo={}",
                              localElapsedSeconds, leaseDuration, newLeaseTransitions, getDebugInfo());
               }

               resetObservedCache();

               return true;
            } catch (KubernetesConflictException e) {
               // Someone else acquired the expired lease first - this is a normal race condition
               if (logger.isDebugEnabled()) {
                  logger.debug("Conflict acquiring expired lease, another instance won the race, debugInfo={}", getDebugInfo(), e);
               }
               resetObservedCache();
               return false;

            } catch (KubernetesApiException e) {
               if (logger.isDebugEnabled()) {
                  logger.debug("KubernetesApiException acquiring expired lease, debugInfo={}", getDebugInfo(), e);
               }
               logger.warn("Failed to acquire expired lease: {}", e.getMessage(), e);
               return false;
            }
         }
      }

      return false;
   }

   private void resetObservedCache() {
      // Reset observation state after acquiring
      lastObservedHolder = null;
      lastObservedRenewTime = null;
      localObservationTime = null;
   }

   /**
    * Checks if the lease has changed since our last observation.
    * A lease is considered changed if the holder identity or renew time differs.
    *
    * @param currentHolder the current holder identity
    * @param currentRenewTime the current renew time string
    * @return true if the lease has changed, false otherwise
    */
   private boolean hasLeaseChanged(String currentHolder, String currentRenewTime) {
      if (lastObservedHolder == null || lastObservedRenewTime == null) {
         if (logger.isDebugEnabled()) {
            logger.debug("hasLeaseChanged returning true as either lastObserverHolder={} or lastObservedRenewTime={} is null", lastObservedHolder, lastObservedRenewTime);
         }
         return true;
      }

      return !currentHolder.equals(lastObservedHolder) ||
             !currentRenewTime.equals(lastObservedRenewTime);
   }

   @Override
   public boolean requiresRecreation() {
      return false;
   }

   // we don't need to keep retrying like in other implementations.
   @Override
   public boolean tryLock(long timeout, TimeUnit unit) throws UnavailableStateException, InterruptedException {
      return tryLock();
   }

   @Override
   public boolean tryLock() throws UnavailableStateException {
      if (logger.isDebugEnabled()) {
         logger.debug("tryLock called, debugInfo={}", getDebugInfo());
      }
      try {
         boolean result = renewLock();
         if (logger.isDebugEnabled()) {
            logger.debug("tryLock returning {}, debugInfo={}", result, getDebugInfo());
         }
         return result;
      } catch (Exception e) {
         throw new UnavailableStateException(e.getMessage(), e);
      }
   }

   @Override
   public void unlock() throws UnavailableStateException {
      if (logger.isDebugEnabled()) {
         logger.debug("unlock called, debugInfo={}", getDebugInfo());
      }
      try {
         // Try to read the existing lease
         JsonObject existingLease = LockKubeClient.getLease(namespace, id);

         if (existingLease == null) {
            // Lease doesn't exist - already unlocked or expired
            if (logger.isDebugEnabled()) {
               logger.debug("Lock {} not found, already released, debugInfo={}", id, getDebugInfo());
            }
            return;
         }

         JsonObject spec = existingLease.getJsonObject("spec");
         if (spec == null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Lease spec is null during unlock, debugInfo={}", getDebugInfo());
            }
            return;
         }

         String holderIdentity = spec.getString("holderIdentity", null);

         // Only unlock if we hold the lease
         if (hostname.equals(holderIdentity)) {
            // Delete the lease to release the lock
            try {
               LockKubeClient.deleteLease(namespace, id);
            } catch (KubernetesConflictException e) {
               // this is an okay situation, we just ignore as someone else got the lock already
               if (logger.isDebugEnabled()) {
                  logger.debug("KubernetesConflictException while unlocking it. Someone else acquired it first, debugInfo={}", getDebugInfo(), e);
               }
            }
            if (logger.isDebugEnabled()) {
               logger.debug("Released lock: {}, debugInfo={}", id, getDebugInfo());
            }
         } else {
            if (logger.isDebugEnabled()) {
               logger.debug("Attempted to unlock {} but it's held by {}, debugInfo={}", id, holderIdentity, getDebugInfo());
            }
         }
      } catch (KubernetesApiException e) {
         throw new UnavailableStateException("Failed to unlock: " + e.getMessage(), e);
      }
   }

   @Override
   public void addListener(UnavailableLockListener listener) {

   }

   @Override
   public void removeListener(UnavailableLockListener listener) {

   }

   @Override
   public void close() {

   }
}
