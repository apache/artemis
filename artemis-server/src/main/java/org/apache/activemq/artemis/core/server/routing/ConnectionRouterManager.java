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

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.config.routing.CacheConfiguration;
import org.apache.activemq.artemis.core.config.routing.NamedPropertyConfiguration;
import org.apache.activemq.artemis.core.config.routing.PoolConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.routing.caches.Cache;
import org.apache.activemq.artemis.core.server.routing.caches.LocalCache;
import org.apache.activemq.artemis.core.server.routing.policies.Policy;
import org.apache.activemq.artemis.core.server.routing.policies.PolicyFactory;
import org.apache.activemq.artemis.core.server.routing.policies.PolicyFactoryResolver;
import org.apache.activemq.artemis.core.server.routing.pools.ClusterPool;
import org.apache.activemq.artemis.core.server.routing.pools.DiscoveryGroupService;
import org.apache.activemq.artemis.core.server.routing.pools.DiscoveryPool;
import org.apache.activemq.artemis.core.server.routing.pools.DiscoveryService;
import org.apache.activemq.artemis.core.server.routing.pools.Pool;
import org.apache.activemq.artemis.core.server.routing.pools.StaticPool;
import org.apache.activemq.artemis.core.server.routing.targets.ActiveMQTargetFactory;
import org.apache.activemq.artemis.core.server.routing.targets.LocalTarget;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.targets.TargetFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ConnectionRouterManager implements ActiveMQComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String CACHE_ID_PREFIX = "$.BC.";

   private final ActiveMQServer server;

   private final ScheduledExecutorService scheduledExecutor;

   private volatile boolean started = false;

   private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

   private Map<String, ConnectionRouterConfiguration> configurations = new HashMap<>();
   private Map<String, ConnectionRouter> connectionRouters = new HashMap<>();

   @Override
   public boolean isStarted() {
      return started;
   }

   public ConnectionRouterManager(final ActiveMQServer server, ScheduledExecutorService scheduledExecutor) {
      this.server = server;
      this.scheduledExecutor = scheduledExecutor;
   }

   public void deploy(Configuration config) throws Exception {
      stateLock.writeLock().lock();
      try {
         for (ConnectionRouterConfiguration connectionRouterConfig : config.getConnectionRouters()) {
            deployConnectionRouter(connectionRouterConfig);
         }
      } finally {
         stateLock.writeLock().unlock();
      }
   }

   public void update(Configuration config) throws Exception {
      stateLock.writeLock().lock();
      try {
         final List<ConnectionRouterConfiguration> activeConfiguration = Objects.requireNonNullElse(config.getConnectionRouters(), Collections.emptyList());

         for (ConnectionRouterConfiguration configuration : activeConfiguration) {
            final ConnectionRouterConfiguration previous = configurations.get(configuration.getName());

            if (previous == null || !configuration.equals(previous)) {
               // If this was an update and the connection router is active meaning the manager is
               // started then we need to stop the old one if it exists before attempting to deploy
               // a new version with the updated configuration.
               final ConnectionRouter router = connectionRouters.remove(configuration.getName());

               if (router != null) {
                  router.stop();
                  server.getManagementService().unregisterConnectionRouter(router.getName());
               }

               deployConnectionRouter(configuration);
            }
         }

         // Find any removed configurations and remove them from the current set and stop the associated
         // router if one is present.

         final Map<String, ConnectionRouterConfiguration> activeConfigurationsMap =
            activeConfiguration.stream()
                               .collect(Collectors.toMap(c -> c.getName(), Function.identity()));

         final List<ConnectionRouterConfiguration> removedConfigurations =
            configurations.values().stream().filter(c -> !activeConfigurationsMap.containsKey(c.getName())).toList();

         for (ConnectionRouterConfiguration removedConfiguration : removedConfigurations) {
            configurations.remove(removedConfiguration.getName());

            final ConnectionRouter router = connectionRouters.remove(removedConfiguration.getName());

            if (router != null) {
               router.stop();
               server.getManagementService().unregisterConnectionRouter(router.getName());
            }
         }
      } finally {
         stateLock.writeLock().unlock();
      }
   }

   ConnectionRouter deployConnectionRouter(ConnectionRouterConfiguration config) throws Exception {
      logger.debug("Deploying ConnectionRouter {}", config.getName());

      Target localTarget = new LocalTarget(null, server);

      Cache cache = null;
      CacheConfiguration cacheConfiguration = config.getCacheConfiguration();
      if (cacheConfiguration != null) {
         cache = deployCache(cacheConfiguration, config.getName());
      }

      Pool pool = null;
      final PoolConfiguration poolConfiguration = config.getPoolConfiguration();
      if (poolConfiguration != null) {
         pool = deployPool(config.getPoolConfiguration(), localTarget);
      }

      Policy policy = null;
      NamedPropertyConfiguration policyConfiguration = config.getPolicyConfiguration();
      if (policyConfiguration != null) {
         policy = deployPolicy(policyConfiguration, pool);
      }

      ConnectionRouter connectionRouter = new ConnectionRouter(config.getName(), config.getKeyType(),
         config.getKeyFilter(), localTarget, config.getLocalTargetFilter(), cache, pool, policy);

      configurations.put(connectionRouter.getName(), config);
      connectionRouters.put(connectionRouter.getName(), connectionRouter);

      server.getManagementService().registerConnectionRouter(connectionRouter);

      if (isStarted()) {
         connectionRouter.start();
      }

      return connectionRouter;
   }

   private Cache deployCache(CacheConfiguration configuration, String name) throws ClassNotFoundException {
      Cache cache = new LocalCache(CACHE_ID_PREFIX + name, configuration.isPersisted(),
         configuration.getTimeout(), server.getStorageManager());

      return cache;
   }

   private Pool deployPool(PoolConfiguration config, Target localTarget) throws Exception {
      Pool pool;
      TargetFactory targetFactory = new ActiveMQTargetFactory();

      targetFactory.setUsername(config.getUsername());
      targetFactory.setPassword(config.getPassword());

      if (config.getClusterConnection() != null) {
         ClusterConnection clusterConnection = server.getClusterManager()
            .getClusterConnection(config.getClusterConnection());

         pool = new ClusterPool(targetFactory, scheduledExecutor, config.getCheckPeriod(), clusterConnection);
      } else if (config.getDiscoveryGroupName() != null) {
         DiscoveryGroupConfiguration discoveryGroupConfiguration = server.getConfiguration().
            getDiscoveryGroupConfigurations().get(config.getDiscoveryGroupName());

         DiscoveryService discoveryService = new DiscoveryGroupService(localTarget, discoveryGroupConfiguration);

         pool = new DiscoveryPool(targetFactory, scheduledExecutor, config.getCheckPeriod(), discoveryService);
      } else if (config.getStaticConnectors() != null) {
         Map<String, TransportConfiguration> connectorConfigurations =
            server.getConfiguration().getConnectorConfigurations();

         List<TransportConfiguration> staticConnectors = new ArrayList<>();
         for (String staticConnector : config.getStaticConnectors()) {
            TransportConfiguration connector = connectorConfigurations.get(staticConnector);

            if (connector != null) {
               staticConnectors.add(connector);
            } else {
               logger.warn("Static connector not found: {}", config.isLocalTargetEnabled());
            }
         }

         pool = new StaticPool(targetFactory, scheduledExecutor, config.getCheckPeriod(), staticConnectors);
      } else {
         throw new IllegalStateException("Pool configuration not valid");
      }

      pool.setUsername(config.getUsername());
      pool.setPassword(config.getPassword());
      pool.setQuorumSize(config.getQuorumSize());
      pool.setQuorumTimeout(config.getQuorumTimeout());

      if (config.isLocalTargetEnabled()) {
         pool.addTarget(localTarget);
      }

      return pool;
   }

   private Policy deployPolicy(NamedPropertyConfiguration policyConfig, Pool pool) throws ClassNotFoundException {
      PolicyFactory policyFactory = PolicyFactoryResolver.getInstance().resolve(policyConfig.getName());

      Policy policy = policyFactory.create();

      policy.init(policyConfig.getProperties());

      if (pool != null && policy.getTargetProbe() != null) {
         pool.addTargetProbe(policy.getTargetProbe());
      }

      return policy;
   }

   public ConnectionRouter getRouter(String name) {
      stateLock.readLock().lock();
      try {
         return connectionRouters.get(name);
      } finally {
         stateLock.readLock().unlock();
      }
   }

   @Override
   public void start() throws Exception {
      stateLock.writeLock().lock();
      try {
         started = true;

         for (ConnectionRouter connectionRouter : connectionRouters.values()) {
            connectionRouter.start();
         }
      } finally {
         stateLock.writeLock().unlock();
      }
   }

   @Override
   public void stop() throws Exception {
      stateLock.writeLock().lock();
      try {
         started = false;

         for (ConnectionRouter connectionRouter : connectionRouters.values()) {
            connectionRouter.stop();
            server.getManagementService().unregisterConnectionRouter(connectionRouter.getName());
         }
      } finally {
         stateLock.writeLock().unlock();
      }
   }
}
