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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.activemq.artemis.lockmanager.AbstractDistributedLockManager;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.joining;

/**
 * Kubernetes-based distributed lock manager implementation.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li><b>hostname</b>: The hostname identifier for this instance.
 *       Default: value of the HOSTNAME environment variable</li>
 *   <li><b>namespace</b>: The Kubernetes namespace where locks are managed.
 *       Default: reads from /var/run/secrets/kubernetes.io/serviceaccount/namespace,
 *       falls back to "default" if unavailable</li>
 *   <li><b>lease-timeout</b>: The lease timeout in seconds.
 *       Default: 30 seconds</li>
 * </ul>
 */
public class KubeLockManager extends AbstractDistributedLockManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String HOSTNAME = "hostname";
   private static final String NAMESPACE = "namespace";
   private static final String LEASE_TIMEOUT = "lease-timeout";
   private static final Set<String> VALID_PARAMS = Stream.of(
      HOSTNAME,
      NAMESPACE, LEASE_TIMEOUT).collect(Collectors.toSet());
   private static final String VALID_PARAMS_ON_ERROR = VALID_PARAMS.stream().collect(joining(","));

   private String hostname;
   private String namespace;
   long leaseTimeout;



   public KubeLockManager(Map<String, String> config) {
      this(config.get(HOSTNAME),
           config.get(NAMESPACE),
           Long.parseLong(config.get(LEASE_TIMEOUT)));
      validateParameters(config);
   }

   public KubeLockManager(String hostname, String namespace, long leaseTimeout) {
      this.hostname = hostname;
      this.namespace = namespace;
      this.leaseTimeout = leaseTimeout;

      if (hostname == null) {
         hostname = System.getenv("HOSTNAME");
         logger.debug("Replaced hostname attribute as {}", hostname);
      }

      if (namespace == null) {
         try {
            namespace = Files.readString(Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace")).trim();
            logger.debug("Read namespace from Kubernetes service account: {}", namespace);
         } catch (IOException e) {
            logger.warn(e.getMessage(), e);
            namespace = "default";
         }
      }

      if (leaseTimeout <= 0) {
         leaseTimeout = 30;
      }
   }


   @Override
   protected Set<String> getValidParams() {
      return VALID_PARAMS;
   }

   @Override
   public void addUnavailableManagerListener(UnavailableManagerListener listener) {

   }

   @Override
   public void removeUnavailableManagerListener(UnavailableManagerListener listener) {

   }

   @Override
   public boolean start(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
      return true;
   }

   @Override
   public void start() throws InterruptedException, ExecutionException {
   }

   @Override
   public boolean isStarted() {
      return true;
   }

   @Override
   public void stop() {

   }

   @Override
   public DistributedLock getDistributedLock(String lockId) throws InterruptedException, ExecutionException, TimeoutException {
      try {
         return new KubeLock(hostname, namespace, lockId, leaseTimeout);
      } catch (Exception e) {
         throw new ExecutionException(e.getMessage(), e);
      }

   }

   @Override
   public MutableLong getMutableLong(String mutableLongId) throws InterruptedException, ExecutionException, TimeoutException {
      try {
         return new KubeMutableLong(namespace, mutableLongId);
      } catch (Exception e) {
         throw new ExecutionException(e.getMessage(), e);
      }
   }
}
