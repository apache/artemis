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

import java.io.FileReader;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.OffsetDateTime;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoordinationV1Api;
import io.kubernetes.client.openapi.models.V1Lease;
import io.kubernetes.client.openapi.models.V1LeaseSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubeLock implements DistributedLock {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final String hostname;
   final String namespace;
   final String id;
   private final long leasePeriodSeconds;

   private ApiClient kubeClient;

   private CoordinationV1Api coordinationV1Api;


   public KubeLock(String hostname, String namespace, String id, long leasePeriodSeconds) throws Exception {
      this.hostname = hostname;
      this.namespace = namespace;
      this.id = id;
      String kubeconfigPath = System.getenv("KUBECONFIG");
      if (kubeconfigPath == null) {
         kubeconfigPath = System.getProperty("user.home") + "/.kube/config";
      }
      logger.debug("using kubeLock client as {}", kubeconfigPath);
      kubeClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeconfigPath))).build();
      coordinationV1Api = new CoordinationV1Api(kubeClient);
      this.leasePeriodSeconds = leasePeriodSeconds;
   }

   @Override
   public String getLockId() {
      return id;
   }

   @Override
   public boolean isHeldByCaller() throws UnavailableStateException {
      return renewLock();
   }

   private boolean renewLock() throws UnavailableStateException {
      try {
         // Try to read the existing lease
         V1Lease existingLease = coordinationV1Api.readNamespacedLease(id, namespace).execute();
         logger.debug("renewLock, Read lease: renewTime={}, holderIdentity={}, leaseDuration={}",
                     existingLease.getSpec().getRenewTime(),
                     existingLease.getSpec().getHolderIdentity(),
                     existingLease.getSpec().getLeaseDurationSeconds());

         // Check if we already hold this lease
         if (hostname.equals(existingLease.getSpec().getHolderIdentity())) {
            // Renew the lease
            existingLease.getSpec().setRenewTime(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            coordinationV1Api.replaceNamespacedLease(id, namespace, existingLease).execute();
            return true;
         }


         // Check if the lease has expired by using the leaseDurationSeconds from the lease spec
         // This is more reliable than using our local leasePeriodSeconds
         java.time.OffsetDateTime renewTime = existingLease.getSpec().getRenewTime();
         Integer leaseDuration = existingLease.getSpec().getLeaseDurationSeconds();

         if (renewTime != null && leaseDuration != null) {
            OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);

            if (logger.isDebugEnabled()) {
               logger.debug("renew period:: {}, now = {}, between={}, between seconds={}", renewTime, now, Duration.between(renewTime, now), Duration.between(renewTime, now).toSeconds());
            }
            long ageSeconds = java.time.Duration.between(renewTime, now).toSeconds();

            if (ageSeconds > leaseDuration) {
               // Lease has expired, try to acquire it
               OffsetDateTime newRenewTime = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
               existingLease.getSpec().setHolderIdentity(hostname);
               existingLease.getSpec().setAcquireTime(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
               existingLease.getSpec().setRenewTime(newRenewTime);

               logger.debug("acquiring expired lease. Setting renewTime = {}, holderIdentity = {}, leaseDuration = {}", newRenewTime, hostname, leasePeriodSeconds);

               existingLease.getSpec().setLeaseDurationSeconds((int) (leasePeriodSeconds));
               coordinationV1Api.replaceNamespacedLease(id, namespace, existingLease).execute();
               return true;
            }
         }

         // Lease is held by someone else and not expired
         return false;

      } catch (ApiException e) {
         if (e.getCode() == 404) {
            logger.debug("Create lock");
            return createLock();
         } else {
            logger.warn(e.getMessage(), e);
            return false;
         }
      }
   }

   @Override
   public boolean tryLock() throws UnavailableStateException {
      return renewLock();
   }

   private boolean createLock() throws UnavailableStateException {
      // Lease doesn't exist, create a new one
      try {
         V1Lease newLease = new V1Lease();
         newLease.setApiVersion("coordination.k8s.io/v1");
         newLease.setKind("Lease");

         V1ObjectMeta metadata = new V1ObjectMeta();
         metadata.setName(id);
         metadata.setNamespace(namespace);
         newLease.setMetadata(metadata);

         V1LeaseSpec spec = new V1LeaseSpec();
         spec.setHolderIdentity(hostname);
         spec.setAcquireTime(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));

         spec.setRenewTime(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
         spec.setLeaseDurationSeconds((int) (leasePeriodSeconds));
         newLease.setSpec(spec);

         coordinationV1Api.createNamespacedLease(namespace, newLease).execute();
         return true;
      } catch (ApiException createException) {
         createException.printStackTrace();
         // Race condition - someone else created it first
         logger.warn(createException.getMessage(), createException);
         return false;
      }
   }

   @Override
   public void unlock() throws UnavailableStateException {
      try {
         // Try to read the existing lease
         V1Lease existingLease = coordinationV1Api.readNamespacedLease(id, namespace).execute();

         // Only unlock if we hold the lease
         if (hostname.equals(existingLease.getSpec().getHolderIdentity())) {
            // Delete the lease to release the lock
            coordinationV1Api.deleteNamespacedLease(id, namespace).execute();
            logger.debug("Released lock: {}", id);
         } else {
            logger.warn("Attempted to unlock {} but it's held by {}", id, existingLease.getSpec().getHolderIdentity());
         }
      } catch (ApiException e) {
         if (e.getCode() == 404) {
            // Lease doesn't exist - already unlocked or expired
            logger.debug("Lock {} not found, already released", id);
         } else {
            throw new UnavailableStateException("Failed to unlock: " + e.getResponseBody(), e);
         }
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
