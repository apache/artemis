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
package org.apache.activemq.artemis.core.server.routing;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.routing.caches.Cache;
import org.apache.activemq.artemis.core.server.routing.policies.Policy;
import org.apache.activemq.artemis.core.server.routing.pools.Pool;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.targets.TargetResult;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

public class ConnectionRouter implements ActiveMQComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String CLIENT_ID_PREFIX = ActiveMQDefaultConfiguration.DEFAULT_INTERNAL_NAMING_PREFIX + "router.client.";

   private final String name;

   private final KeyType keyType;

   private final KeyResolver keyResolver;

   private final TargetResult localTarget;

   private volatile Pattern localTargetFilter;

   private final Pool pool;

   private final Policy policy;

   private final Cache cache;

   private volatile boolean started;

   private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

   public String getName() {
      return name;
   }

   public KeyType getKey() {
      return keyType;
   }

   public KeyResolver getKeyResolver() {
      return keyResolver;
   }

   public Target getLocalTarget() {
      return localTarget.getTarget();
   }

   public String getLocalTargetFilter() {
      final Pattern localTargetFilter = this.localTargetFilter;

      return localTargetFilter != null ? localTargetFilter.pattern() : null;
   }

   public void setLocalTargetFilter(String regExp) {
      if (regExp == null || regExp.trim().isEmpty()) {
         this.localTargetFilter = null;
      } else {
         this.localTargetFilter = Pattern.compile(regExp);
      }
   }

   public Pool getPool() {
      return pool;
   }

   public Policy getPolicy() {
      return policy;
   }

   public Cache getCache() {
      return cache;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public ConnectionRouter(final String name,
                           final KeyType keyType,
                           final String targetKeyFilter,
                           final Target localTarget,
                           final String localTargetFilter,
                           final Cache cache,
                           final Pool pool,
                           final Policy policy) {
      this.name = name;

      this.keyType = keyType;

      this.keyResolver = new KeyResolver(keyType, targetKeyFilter);

      this.localTarget = new TargetResult(localTarget);

      this.localTargetFilter = localTargetFilter != null ? Pattern.compile(localTargetFilter) : null;

      this.pool = pool;

      this.policy = policy;

      this.cache = cache;
   }

   @Override
   public void start() throws Exception {
      stateLock.writeLock().lock();
      try {
         if (localTarget != null) {
            localTarget.getTarget().connect();
         }

         if (cache != null) {
            cache.start();
         }

         if (pool != null) {
            pool.start();
         }

         started = true;
      } finally {
         stateLock.writeLock().unlock();
      }
   }

   @Override
   public void stop() throws Exception {
      stateLock.writeLock().lock();
      try {
         started = false;

         if (pool != null) {
            pool.stop();
         }

         if (cache != null) {
            cache.stop();
         }

         if (localTarget != null) {
            localTarget.getTarget().disconnect();
         }
      } finally {
         stateLock.writeLock().unlock();
      }
   }

   public TargetResult getTarget(Connection connection, String clientID, String username) {
      stateLock.readLock().lock();
      try {
         if (!started) {
            return TargetResult.REFUSED_UNAVAILABLE_RESULT;
         }

         if (clientID != null && clientID.startsWith(ConnectionRouter.CLIENT_ID_PREFIX)) {
            logger.debug("The clientID [{}] starts with ConnectionRouter.CLIENT_ID_PREFIX", clientID);

            return localTarget;
         }

         return getTarget(keyResolver.resolve(connection, clientID, username));
      } finally {
         stateLock.readLock().unlock();
      }
   }

   public TargetResult getTarget(String key) {
      stateLock.readLock().lock();
      try {
         if (!started) {
            return TargetResult.REFUSED_UNAVAILABLE_RESULT;
         }

         if (policy != null && !KeyResolver.NULL_KEY_VALUE.equals(key)) {
            key = policy.transformKey(key);
         }

         final Pattern localTargetFilter = this.localTargetFilter;

         if (localTargetFilter != null && localTargetFilter.matcher(key).matches()) {
            if (logger.isDebugEnabled()) {
               logger.debug("The {}[{}] matches the localTargetFilter {}", keyType, key, localTargetFilter.pattern());
            }

            return localTarget;
         }

         if (policy == null || pool == null) {
            return TargetResult.REFUSED_USE_ANOTHER_RESULT;
         }

         TargetResult result = null;

         if (cache != null) {
            final String nodeId = cache.get(key);

            if (logger.isDebugEnabled()) {
               logger.debug("The cache returns target [{}] for {}[{}]", nodeId, keyType, key);
            }

            if (nodeId != null) {
               Target target = pool.getReadyTarget(nodeId);
               if (target != null) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("The target [{}] is ready for {}[{}]", nodeId, keyType, key);
                  }

                  return new TargetResult(target);
               }

               if (logger.isDebugEnabled()) {
                  logger.debug("The target [{}] is not ready for {}[{}]", nodeId, keyType, key);
               }
            }
         }

         final List<Target> targets = pool.getTargets();
         final Target target = policy.selectTarget(targets, key);

         if (logger.isDebugEnabled()) {
            logger.debug("The policy selects [{}] from {} for {}[{}]", target, targets, keyType, key);
         }

         if (target != null) {
            result = new TargetResult(target);
            if (cache != null) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Caching {}[{}] for [{}]", keyType, key, target);
               }

               cache.put(key, target.getNodeID());
            }
         }

         return Objects.requireNonNullElse(result, TargetResult.REFUSED_UNAVAILABLE_RESULT);
      } finally {
         stateLock.readLock().unlock();
      }
   }
}
