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
package org.apache.activemq.artemis.core.client.impl;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.api.core.client.loadbalance.ConnectionLoadBalancingPolicy;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.cluster.DiscoveryEntry;
import org.apache.activemq.artemis.core.cluster.DiscoveryGroup;
import org.apache.activemq.artemis.core.cluster.DiscoveryListener;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManagerFactory;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.remoting.Connector;
import org.apache.activemq.artemis.uri.ServerLocatorParser;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ActiveMQThreadPoolExecutor;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.actors.Actor;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.activemq.artemis.utils.uri.FluentPropertyBeanIntrospectorWithIgnores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the implementation of {@link org.apache.activemq.artemis.api.core.client.ServerLocator} and all the proper
 * javadoc is located on that interface.
 */
public final class ServerLocatorImpl implements ServerLocatorInternal, DiscoveryListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private enum STATE {
      INITIALIZED, CLOSED, CLOSING
   }

   static {
      // this is not really a property, needs to be ignored
      FluentPropertyBeanIntrospectorWithIgnores.addIgnore(ServerLocatorImpl.class.getName(), "setThreadPools");
   }

   // This is the default value
   private ClientProtocolManagerFactory protocolManagerFactory = new ActiveMQClientProtocolManagerFactory().setLocator(this);

   private final boolean ha;

   private boolean clusterConnection;

   private transient String identity;

   // this is not used... I'm only keeping it here because of Serialization compatibility and Wildfly usage on JNDI.
   @SuppressWarnings("unused")
   private boolean finalizeCheck;

   private final Set<ClientSessionFactoryInternal> factories = new HashSet<>();

   private final Set<ClientSessionFactoryInternal> connectingFactories = new HashSet<>();

   private volatile TransportConfiguration[] initialConnectors;

   private final DiscoveryGroupConfiguration discoveryGroupConfiguration;

   private final StaticConnector staticConnector = new StaticConnector();

   private Topology topology;

   private final Object topologyArrayGuard = new Object();

   private volatile Pair<TransportConfiguration, TransportConfiguration>[] topologyArray;

   private volatile boolean receivedTopology;

   /**
    * This specifies serverLocator.connect was used, which means it's a cluster connection. We should not use retries
    */
   private volatile boolean disableDiscoveryRetries = false;

   // if the system should shutdown the pool when shutting down
   private transient boolean shutdownPool;

   private transient Executor threadPool;

   private transient Executor flowControlThreadPool;
   private transient ScheduledExecutorService scheduledThreadPool;

   private transient DiscoveryGroup discoveryGroup;

   private transient ConnectionLoadBalancingPolicy loadBalancingPolicy;

   private final Object discoveryGroupGuardian = new Object();

   // Settable attributes:


   private final Object stateGuard = new Object();
   private transient STATE state;
   private transient CountDownLatch latch;

   private final List<Interceptor> incomingInterceptors = new CopyOnWriteArrayList<>();

   private final List<Interceptor> outgoingInterceptors = new CopyOnWriteArrayList<>();

   private Executor startExecutor;

   private Actor<Long> updateArrayActor;

   private AfterConnectInternalListener afterConnectListener;

   private String groupID;

   private String nodeID;

   private TransportConfiguration clusterTransportConfiguration;

   /**
    * For tests only
    */
   public DiscoveryGroup getDiscoveryGroup() {
      return discoveryGroup;
   }

   /**
    * For tests only
    */
   public Set<ClientSessionFactoryInternal> getFactories() {
      return factories;
   }

   private ServerLocatorConfig config = new ServerLocatorConfig();

   private String passwordCodec;

   private DiscoveryListener discoveryListener;

   public static synchronized void clearThreadPools() {
      ActiveMQClient.clearThreadPools();
   }


   private synchronized void setThreadPools() {
      if (threadPool != null) {
         return;
      } else if (config.useGlobalPools) {
         threadPool = ActiveMQClient.getGlobalThreadPool();

         flowControlThreadPool = ActiveMQClient.getGlobalFlowControlThreadPool();

         scheduledThreadPool = ActiveMQClient.getGlobalScheduledThreadPool();
      } else {
         this.shutdownPool = true;

         ThreadFactory factory = getThreadFactory("client-factory-");
         if (config.threadPoolMaxSize == -1) {
            threadPool = Executors.newCachedThreadPool(factory);
         } else {
            threadPool = new ActiveMQThreadPoolExecutor(0, config.threadPoolMaxSize, 60L, TimeUnit.SECONDS, factory);
         }

         factory = getThreadFactory("client-factory-flow-control-");
         if (config.flowControlThreadPoolMaxSize == -1) {
            flowControlThreadPool = Executors.newCachedThreadPool(factory);
         } else {
            flowControlThreadPool = new ActiveMQThreadPoolExecutor(0, config.flowControlThreadPoolMaxSize, 60L, TimeUnit.SECONDS, factory);
         }

         factory = getThreadFactory("client-factory-scheduled-");
         scheduledThreadPool = Executors.newScheduledThreadPool(config.scheduledThreadPoolMaxSize, factory);
      }
      this.updateArrayActor = new Actor<>(threadPool, this::internalUpdateArray);
   }

   private ThreadFactory getThreadFactory(String groupName) {
      return SecurityManagerShim.doPrivileged(new PrivilegedAction<>() {
         @Override
         public ThreadFactory run() {
            return new ActiveMQThreadFactory(groupName + System.identityHashCode(this), true, ClientSessionFactoryImpl.class.getClassLoader());
         }
      });
   }

   @Override
   public synchronized boolean setThreadPools(Executor threadPool, ScheduledExecutorService scheduledThreadPool, Executor flowControlThreadPool) {

      if (threadPool == null || scheduledThreadPool == null)
         return false;

      if (this.threadPool == null && this.scheduledThreadPool == null && this.flowControlThreadPool == null) {
         config.useGlobalPools = false;
         shutdownPool = false;
         this.threadPool = threadPool;
         this.scheduledThreadPool = scheduledThreadPool;
         this.flowControlThreadPool = flowControlThreadPool;
         return true;
      } else {
         return false;
      }
   }

   private void instantiateLoadBalancingPolicy() {
      if (config.connectionLoadBalancingPolicyClassName == null) {
         throw new IllegalStateException("Please specify a load balancing policy class name on the session factory");
      }
      SecurityManagerShim.doPrivileged((PrivilegedAction<Object>) () -> {
         loadBalancingPolicy = (ConnectionLoadBalancingPolicy) ClassloadingUtil.newInstanceFromClassLoader(ServerLocatorImpl.class, config.connectionLoadBalancingPolicyClassName, ConnectionLoadBalancingPolicy.class);
         return null;
      });
   }

   @Override
   public synchronized void initialize() throws ActiveMQException {
      if (state == STATE.INITIALIZED)
         return;
      synchronized (stateGuard) {
         if (state == STATE.CLOSING)
            throw new ActiveMQIllegalStateException();
         try {
            state = STATE.INITIALIZED;
            latch = new CountDownLatch(1);

            setThreadPools();

            topology.setExecutor(new OrderedExecutor(threadPool));

            instantiateLoadBalancingPolicy();

            startDiscovery();

         } catch (Exception e) {
            state = null;
            throw ActiveMQClientMessageBundle.BUNDLE.failedToInitialiseSessionFactory(e);
         }
      }
   }

   private void startDiscovery() throws ActiveMQException {
      if (discoveryGroupConfiguration != null) {
         try {
            discoveryGroup = createDiscoveryGroup(nodeID, discoveryGroupConfiguration);

            discoveryGroup.registerListener(this);

            discoveryGroup.start();
         } catch (Exception e) {
            throw new ActiveMQInternalErrorException(e.getMessage(), e);
         }
      }
   }

   @Override
   public ServerLocatorConfig getLocatorConfig() {
      return config;
   }

   @Override
   public void setLocatorConfig(ServerLocatorConfig config) {
      this.config = config;
   }

   @Override
   public ServerLocator setPasswordCodec(String passwordCodec) {
      this.passwordCodec = passwordCodec;
      return this;
   }

   @Override
   public String getPasswordCodec() {
      return this.passwordCodec;
   }

   private static DiscoveryGroup createDiscoveryGroup(String nodeID,
                                                      DiscoveryGroupConfiguration config) throws Exception {
      return new DiscoveryGroup(nodeID, config.getName(), config.getRefreshTimeout(), config.getStoppingTimeout(), config.getBroadcastEndpointFactory(), null);
   }

   private ServerLocatorImpl(final Topology topology,
                             final boolean useHA,
                             final DiscoveryGroupConfiguration discoveryGroupConfiguration,
                             final TransportConfiguration[] transportConfigs) {
      this.topology = Objects.requireNonNullElseGet(topology, () -> new Topology(this));

      this.ha = useHA;

      this.discoveryGroupConfiguration = discoveryGroupConfiguration;

      this.initialConnectors = transportConfigs;

      this.nodeID = UUIDGenerator.getInstance().generateStringUUID();

      clusterConnection = false;
   }

   public static ServerLocator newLocator(String uri) {
      try {
         ServerLocatorParser parser = new ServerLocatorParser();
         URI newURI = parser.expandURI(uri);
         return parser.newObject(newURI, null);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static ServerLocator newLocator(URI uri) {
      try {
         ServerLocatorParser parser = new ServerLocatorParser();
         return parser.newObject(uri, null);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Create a ServerLocatorImpl using UDP discovery to lookup cluster
    */
   public ServerLocatorImpl(final boolean useHA, final DiscoveryGroupConfiguration groupConfiguration) {
      this(new Topology(null), useHA, groupConfiguration, null);
      if (useHA) {
         // We only set the owner at where the Topology was created.
         // For that reason we can't set it at the main constructor
         topology.setOwner(this);
      }
   }

   /**
    * Create a ServerLocatorImpl using a static list of servers
    */
   public ServerLocatorImpl(final boolean useHA, final TransportConfiguration... transportConfigs) {
      this(new Topology(null), useHA, null, transportConfigs);
      if (useHA) {
         // We only set the owner at where the Topology was created.
         // For that reason we can't set it at the main constructor
         topology.setOwner(this);
      }
   }

   /**
    * Create a ServerLocatorImpl using UDP discovery to lookup cluster
    */
   public ServerLocatorImpl(final Topology topology,
                            final boolean useHA,
                            final DiscoveryGroupConfiguration groupConfiguration) {
      this(topology, useHA, groupConfiguration, null);
   }

   /**
    * Create a ServerLocatorImpl using a static list of servers
    */
   public ServerLocatorImpl(final Topology topology,
                            final boolean useHA,
                            final TransportConfiguration... transportConfigs) {
      this(topology, useHA, null, transportConfigs);
   }

   @Override
   public void resetToInitialConnectors() {
      receivedTopology = false;
      topologyArray = null;
      topology.clear();
   }

   // I'm not using isAllInVM here otherwsie BeanProperties would translate this as a property for the URL
   @Override
   public boolean allInVM() {
      for (TransportConfiguration config : getStaticTransportConfigurations()) {
         if (!config.getFactoryClassName().contains("InVMConnectorFactory")) {
            return false;
         }
      }

      return true;
   }

   private ServerLocatorImpl(ServerLocatorImpl locator) {
      ha = locator.ha;
      clusterConnection = locator.clusterConnection;
      initialConnectors = locator.initialConnectors;
      discoveryGroupConfiguration = locator.discoveryGroupConfiguration;
      topology = locator.topology;
      topologyArray = locator.topologyArray;
      receivedTopology = locator.receivedTopology;
      config = new ServerLocatorConfig(locator.config);
      startExecutor = locator.startExecutor;
      afterConnectListener = locator.afterConnectListener;
      groupID = locator.groupID;
      nodeID = locator.nodeID;
      clusterTransportConfiguration = locator.clusterTransportConfiguration;
      discoveryListener = locator.discoveryListener;
   }

   private boolean useInitConnector() {
      return !config.useTopologyForLoadBalancing || !receivedTopology || topologyArray == null || topologyArray.length == 0;
   }

   @Override
   public Pair<TransportConfiguration, TransportConfiguration> selectNextConnectorPair() {
      return selectConnector(useInitConnector());
   }

   private synchronized Pair<TransportConfiguration, TransportConfiguration> selectConnector(boolean useInitConnector) {
      Pair<TransportConfiguration, TransportConfiguration>[] usedTopology;

      flushTopology();

      synchronized (topologyArrayGuard) {
         usedTopology = topologyArray;
      }

      synchronized (this) {
         if (usedTopology != null && config.useTopologyForLoadBalancing && !useInitConnector) {
            logger.trace("Selecting connector from topology.");
            int pos = loadBalancingPolicy.select(usedTopology.length);
            Pair<TransportConfiguration, TransportConfiguration> pair = usedTopology[pos];

            return pair;
         } else {
            logger.trace("Selecting connector from initial connectors.");

            int pos = loadBalancingPolicy.select(initialConnectors.length);

            if (initialConnectors.length == 0) {
               return null;
            }

            return new Pair(initialConnectors[pos], null);
         }
      }
   }

   @Override
   public int getConnectorsSize() {
      Pair<TransportConfiguration, TransportConfiguration>[] usedTopology;

      flushTopology();

      synchronized (topologyArrayGuard) {
         usedTopology = topologyArray;
      }

      synchronized (this) {
         if (usedTopology != null && config.useTopologyForLoadBalancing) {
            return usedTopology.length;
         } else {
            return initialConnectors.length;
         }
      }
   }

   @Override
   public void start(Executor executor) throws Exception {
      initialize();

      this.startExecutor = executor;

      if (executor != null) {
         executor.execute(() -> {
            try {
               connect();
            } catch (Exception e) {
               if (!isClosed()) {
                  ActiveMQClientLogger.LOGGER.errorConnectingToNodes(e);
               }
            }
         });
      }
   }

   @Override
   public ClientProtocolManager newProtocolManager() {
      return getProtocolManagerFactory().newProtocolManager().setExecutor(new OrderedExecutor(Objects.requireNonNull(threadPool, "No Thread Pool")));
   }

   @Override
   public ClientProtocolManagerFactory getProtocolManagerFactory() {
      if (protocolManagerFactory == null) {
         // Default one in case it's null
         protocolManagerFactory = new ActiveMQClientProtocolManagerFactory().setLocator(this);
      }
      return protocolManagerFactory;
   }

   @Override
   public ServerLocator setProtocolManagerFactory(ClientProtocolManagerFactory protocolManagerFactory) {
      this.protocolManagerFactory = protocolManagerFactory;
      protocolManagerFactory.setLocator(this);
      return this;
   }

   @Override
   public ClientSessionFactoryInternal connect() throws ActiveMQException {
      return connect(false);
   }

   private ClientSessionFactoryInternal connect(final boolean skipWarnings) throws ActiveMQException {
      // if we used connect, we should control UDP reconnections at a different path.
      // and this belongs to a cluster connection, not client
      disableDiscoveryRetries = true;
      ClientSessionFactoryInternal returnFactory = null;

      synchronized (this) {
         // static list of initial connectors
         if (getNumInitialConnectors() > 0 && discoveryGroup == null) {
            returnFactory = (ClientSessionFactoryInternal) staticConnector.connect(skipWarnings);
         }
      }

      if (returnFactory != null) {
         addFactory(returnFactory);
         return returnFactory;
      } else {
         // wait for discovery group to get the list of initial connectors
         return (ClientSessionFactoryInternal) createSessionFactory();
      }
   }

   @Override
   public ClientSessionFactoryInternal connectNoWarnings() throws ActiveMQException {
      return connect(true);
   }

   @Override
   public ServerLocatorImpl setAfterConnectionInternalListener(AfterConnectInternalListener listener) {
      this.afterConnectListener = listener;
      return this;
   }

   @Override
   public AfterConnectInternalListener getAfterConnectInternalListener() {
      return afterConnectListener;
   }

   @Override
   public ServerLocatorImpl setDiscoveryListener(DiscoveryListener discoveryListener) {
      this.discoveryListener = discoveryListener;
      return this;
   }

   @Override
   public ClientSessionFactory createSessionFactory(String nodeID) throws Exception {
      TopologyMember topologyMember = topology.getMember(nodeID);

      if (logger.isTraceEnabled()) {
         logger.trace("Creating connection factory towards {} = {}, topology={}", nodeID, topologyMember, topology.describe());
      }

      if (topologyMember == null) {
         return null;
      }
      if (topologyMember.getPrimary() != null) {
         ClientSessionFactoryInternal factory = (ClientSessionFactoryInternal) createSessionFactory(topologyMember.getPrimary());
         if (topologyMember.getBackup() != null) {
            factory.setBackupConnector(topologyMember.getPrimary(), topologyMember.getBackup());
         }
         return factory;
      }
      if (topologyMember.getPrimary() == null && topologyMember.getBackup() != null) {
         // This shouldn't happen, however I wanted this to consider all possible cases
         return createSessionFactory(topologyMember.getBackup());
      }
      // it shouldn't happen
      return null;
   }

   @Override
   public ClientSessionFactory createSessionFactory(final TransportConfiguration transportConfiguration) throws Exception {
      return createSessionFactory(transportConfiguration, config.reconnectAttempts);
   }

   @Override
   public ClientSessionFactory createSessionFactory(final TransportConfiguration transportConfiguration,
                                                    int reconnectAttempts) throws Exception {
      assertOpen();

      initialize();

      ClientSessionFactoryInternal factory = new ClientSessionFactoryImpl(this, transportConfiguration, config, reconnectAttempts, threadPool, scheduledThreadPool, flowControlThreadPool, incomingInterceptors, outgoingInterceptors);

      addToConnecting(factory);
      try {
         try {
            factory.connect(reconnectAttempts);
         } catch (ActiveMQException e1) {
            //we need to make sure is closed just for garbage collection
            factory.close();
            throw e1;
         }
         addFactory(factory);
         return factory;
      } finally {
         removeFromConnecting(factory);
      }
   }

   @Deprecated
   @Override
   public ClientSessionFactory createSessionFactory(final TransportConfiguration transportConfiguration,
                                                    int reconnectAttempts,
                                                    boolean failoverOnInitialConnection) throws Exception {
      return createSessionFactory(transportConfiguration, reconnectAttempts);
   }

   private void removeFromConnecting(ClientSessionFactoryInternal factory) {
      synchronized (connectingFactories) {
         connectingFactories.remove(factory);
      }
   }

   private void addToConnecting(ClientSessionFactoryInternal factory) {
      synchronized (connectingFactories) {
         assertOpen();
         connectingFactories.add(factory);
      }
   }

   @Override
   public ClientSessionFactory createSessionFactory() throws ActiveMQException {
      assertOpen();

      initialize();

      flushTopology();

      if (discoveryGroupConfiguration != null) {
         executeDiscovery();
      }

      ClientSessionFactoryInternal factory = null;

      synchronized (this) {
         boolean retry = true;
         int attempts = 0;
         boolean topologyArrayTried = !config.useTopologyForLoadBalancing || topologyArray == null || topologyArray.length == 0;
         boolean staticTried = false;
         boolean shouldTryStatic = useInitConnector();
         long interval = config.retryInterval;

         while (retry && !isClosed()) {
            retry = false;

            /*
             * The logic is: If receivedTopology is false, try static first.
             * if receivedTopology is true, try topologyArray first
             */
            Pair<TransportConfiguration, TransportConfiguration> tc = selectConnector(shouldTryStatic);

            if (tc == null) {
               throw ActiveMQClientMessageBundle.BUNDLE.noTCForSessionFactory();
            }

            // try each factory in the list until we find one which works

            try {
               factory = new ClientSessionFactoryImpl(this, tc, config, config.reconnectAttempts, threadPool, scheduledThreadPool, flowControlThreadPool, incomingInterceptors, outgoingInterceptors, initialConnectors);
               try {
                  addToConnecting(factory);
                  // We always try to connect here with only one attempt,
                  // as we will perform the initial retry here, looking for all possible connectors
                  factory.connect(1, false);

                  addFactory(factory);
               } finally {
                  removeFromConnecting(factory);
               }
            } catch (ActiveMQException e) {
               try {
                  if (e.getType() == ActiveMQExceptionType.NOT_CONNECTED) {
                     attempts++;
                     int maxAttempts = config.initialConnectAttempts == 0 ? 1 : config.initialConnectAttempts;

                     if (shouldTryStatic) {
                        //we know static is used
                        if (config.initialConnectAttempts >= 0 && attempts >= maxAttempts * this.getNumInitialConnectors()) {
                           if (topologyArrayTried) {
                              //stop retry and throw exception
                              throw ActiveMQClientMessageBundle.BUNDLE.cannotConnectToServers();
                           } else {
                              //lets try topologyArray
                              staticTried = true;
                              shouldTryStatic = false;
                              attempts = 0;
                           }
                        }
                     } else {
                        //we know topologyArray is used
                        if (config.initialConnectAttempts >= 0 && attempts >= maxAttempts * getConnectorsSize()) {
                           if (staticTried) {
                              throw ActiveMQClientMessageBundle.BUNDLE.cannotConnectToServers();
                           } else {
                              topologyArrayTried = true;
                              shouldTryStatic = true;
                              attempts = 0;
                           }
                        }
                     }
                     if (factory.waitForRetry(interval)) {
                        throw ActiveMQClientMessageBundle.BUNDLE.cannotConnectToServers();
                     }
                     interval = getNextRetryInterval(interval, config.retryIntervalMultiplier, config.maxRetryInterval);
                     retry = true;
                  } else {
                     throw e;
                  }
               } finally {

                  factory.close();
               }
            }
         }
      }

      // ATM topology is never != null. Checking here just to be consistent with
      // how the sendSubscription happens.
      // in case this ever changes.
      if (topology != null && !factory.waitForTopology(config.callTimeout, TimeUnit.MILLISECONDS)) {
         factoryClosed(factory);

         factory.cleanup();

         if (isClosed()) {
            throw ActiveMQClientMessageBundle.BUNDLE.connectionClosedOnReceiveTopology(discoveryGroup);
         }

         throw ActiveMQClientMessageBundle.BUNDLE.connectionTimedOutOnReceiveTopology(discoveryGroup);
      }

      return factory;
   }

   @Override
   public long getNextRetryInterval(long retryInterval, double retryIntervalMultiplier, long maxRetryInterval) {
      // Exponential back-off
      long nextRetryInterval = (long) (retryInterval * retryIntervalMultiplier);

      if (nextRetryInterval > maxRetryInterval) {
         nextRetryInterval = maxRetryInterval;
      }

      return nextRetryInterval;
   }

   private void executeDiscovery() throws ActiveMQException {
      boolean discoveryOK = false;
      boolean retryDiscovery = false;
      int tryNumber = 0;

      do {

         discoveryOK = checkOnDiscovery();

         retryDiscovery = (config.initialConnectAttempts > 0 && tryNumber++ < config.initialConnectAttempts) && !disableDiscoveryRetries;

         if (!discoveryOK) {

            if (retryDiscovery) {
               ActiveMQClientLogger.LOGGER.broadcastTimeout(tryNumber, config.initialConnectAttempts);
            } else {
               throw ActiveMQClientMessageBundle.BUNDLE.connectionTimedOutInInitialBroadcast();
            }
         }
      }
      while (!discoveryOK && retryDiscovery);

      if (!discoveryOK) {
         // I don't think the code would ever get to this situation, since there's an exception thrown on the previous loop
         // however I will keep this just in case
         throw ActiveMQClientMessageBundle.BUNDLE.connectionTimedOutInInitialBroadcast();
      }

   }

   private boolean checkOnDiscovery() throws ActiveMQException {

      synchronized (discoveryGroupGuardian) {

         // notice: in case you have many threads waiting to get on checkOnDiscovery, only one will perform the actual discovery
         //         while subsequent calls will have numberOfInitialConnectors > 0
         if (this.getNumInitialConnectors() == 0 && discoveryGroupConfiguration != null) {
            try {

               long timeout = clusterConnection ? 0 : discoveryGroupConfiguration.getDiscoveryInitialWaitTimeout();
               if (!discoveryGroup.waitForBroadcast(timeout)) {

                  if (logger.isDebugEnabled()) {
                     String threadDump = ThreadDumpUtil.threadDump("Discovery timeout, printing thread dump");
                     logger.debug(threadDump);
                  }

                  // if disableDiscoveryRetries = true, it means this is a Bridge or a Cluster Connection Bridge
                  // which has a different mechanism of retry
                  // and we should ignore UDP restarts here.
                  if (!disableDiscoveryRetries) {
                     if (discoveryGroup != null) {
                        discoveryGroup.stop();
                     }

                     logger.debug("Restarting discovery");

                     startDiscovery();
                  }

                  return false;
               }
            } catch (Exception e) {
               throw new ActiveMQInternalErrorException(e.getMessage(), e);
            }
         }
      }

      return true;
   }

   public void flushTopology() {
      if (updateArrayActor != null) {
         updateArrayActor.flush(10, TimeUnit.SECONDS);
      }
   }

   @Override
   public boolean isHA() {
      return ha;
   }

   @Override
   public ServerLocator setIncomingInterceptorList(String interceptorList) {
      feedInterceptors(incomingInterceptors, interceptorList);
      return this;
   }

   @Override
   public String getIncomingInterceptorList() {
      return fromInterceptors(incomingInterceptors);
   }

   @Override
   public ServerLocator setOutgoingInterceptorList(String interceptorList) {
      feedInterceptors(outgoingInterceptors, interceptorList);
      return this;
   }

   @Override
   public String getOutgoingInterceptorList() {
      return fromInterceptors(outgoingInterceptors);
   }

   @Override
   public boolean isCacheLargeMessagesClient() {
      return config.cacheLargeMessagesClient;
   }

   @Override
   public ServerLocatorImpl setCacheLargeMessagesClient(final boolean cached) {
      config.cacheLargeMessagesClient = cached;
      return this;
   }

   @Override
   public long getClientFailureCheckPeriod() {
      return config.clientFailureCheckPeriod;
   }

   @Override
   public ServerLocatorImpl setClientFailureCheckPeriod(final long clientFailureCheckPeriod) {
      checkWrite();
      this.config.clientFailureCheckPeriod = clientFailureCheckPeriod;
      return this;
   }

   @Override
   public long getConnectionTTL() {
      return config.connectionTTL;
   }

   @Override
   public ServerLocatorImpl setConnectionTTL(final long connectionTTL) {
      checkWrite();
      this.config.connectionTTL = connectionTTL;
      return this;
   }

   @Override
   public long getCallTimeout() {
      return config.callTimeout;
   }

   @Override
   public ServerLocatorImpl setCallTimeout(final long callTimeout) {
      checkWrite();
      this.config.callTimeout = callTimeout;
      return this;
   }

   @Override
   public long getCallFailoverTimeout() {
      return config.callFailoverTimeout;
   }

   @Override
   public ServerLocatorImpl setCallFailoverTimeout(long callFailoverTimeout) {
      checkWrite();
      this.config.callFailoverTimeout = callFailoverTimeout;
      return this;
   }

   @Override
   public int getMinLargeMessageSize() {
      return config.minLargeMessageSize;
   }

   @Override
   public ServerLocatorImpl setMinLargeMessageSize(final int minLargeMessageSize) {
      checkWrite();
      this.config.minLargeMessageSize = minLargeMessageSize;
      return this;
   }

   @Override
   public int getConsumerWindowSize() {
      return config.consumerWindowSize;
   }

   @Override
   public ServerLocatorImpl setConsumerWindowSize(final int consumerWindowSize) {
      checkWrite();
      this.config.consumerWindowSize = consumerWindowSize;
      return this;
   }

   @Override
   public int getConsumerMaxRate() {
      return config.consumerMaxRate;
   }

   @Override
   public ServerLocatorImpl setConsumerMaxRate(final int consumerMaxRate) {
      checkWrite();
      this.config.consumerMaxRate = consumerMaxRate;
      return this;
   }

   @Override
   public int getConfirmationWindowSize() {
      return config.confirmationWindowSize;
   }

   @Override
   public ServerLocatorImpl setConfirmationWindowSize(final int confirmationWindowSize) {
      checkWrite();
      this.config.confirmationWindowSize = confirmationWindowSize;
      return this;
   }

   @Override
   public int getProducerWindowSize() {
      return config.producerWindowSize;
   }

   @Override
   public ServerLocatorImpl setProducerWindowSize(final int producerWindowSize) {
      checkWrite();
      this.config.producerWindowSize = producerWindowSize;
      return this;
   }

   @Override
   public int getProducerMaxRate() {
      return config.producerMaxRate;
   }

   @Override
   public ServerLocatorImpl setProducerMaxRate(final int producerMaxRate) {
      checkWrite();
      this.config.producerMaxRate = producerMaxRate;
      return this;
   }

   @Override
   public boolean isBlockOnAcknowledge() {
      return config.blockOnAcknowledge;
   }

   @Override
   public ServerLocatorImpl setBlockOnAcknowledge(final boolean blockOnAcknowledge) {
      checkWrite();
      this.config.blockOnAcknowledge = blockOnAcknowledge;
      return this;
   }

   @Override
   public boolean isBlockOnDurableSend() {
      return config.blockOnDurableSend;
   }

   @Override
   public ServerLocatorImpl setBlockOnDurableSend(final boolean blockOnDurableSend) {
      checkWrite();
      this.config.blockOnDurableSend = blockOnDurableSend;
      return this;
   }

   @Override
   public boolean isBlockOnNonDurableSend() {
      return config.blockOnNonDurableSend;
   }

   @Override
   public ServerLocatorImpl setBlockOnNonDurableSend(final boolean blockOnNonDurableSend) {
      checkWrite();
      this.config.blockOnNonDurableSend = blockOnNonDurableSend;
      return this;
   }

   @Override
   public boolean isAutoGroup() {
      return config.autoGroup;
   }

   @Override
   public ServerLocatorImpl setAutoGroup(final boolean autoGroup) {
      checkWrite();
      this.config.autoGroup = autoGroup;
      return this;
   }

   @Override
   public boolean isPreAcknowledge() {
      return config.preAcknowledge;
   }

   @Override
   public ServerLocatorImpl setPreAcknowledge(final boolean preAcknowledge) {
      checkWrite();
      this.config.preAcknowledge = preAcknowledge;
      return this;
   }

   @Override
   public int getAckBatchSize() {
      return config.ackBatchSize;
   }

   @Override
   public ServerLocatorImpl setAckBatchSize(final int ackBatchSize) {
      checkWrite();
      this.config.ackBatchSize = ackBatchSize;
      return this;
   }

   @Override
   public boolean isUseGlobalPools() {
      return config.useGlobalPools;
   }

   @Override
   public ServerLocatorImpl setUseGlobalPools(final boolean useGlobalPools) {
      checkWrite();
      this.config.useGlobalPools = useGlobalPools;
      return this;
   }

   @Override
   public int getScheduledThreadPoolMaxSize() {
      return config.scheduledThreadPoolMaxSize;
   }

   @Override
   public ServerLocatorImpl setScheduledThreadPoolMaxSize(final int scheduledThreadPoolMaxSize) {
      checkWrite();
      this.config.scheduledThreadPoolMaxSize = scheduledThreadPoolMaxSize;
      return this;
   }

   @Override
   public int getThreadPoolMaxSize() {
      return config.threadPoolMaxSize;
   }

   @Override
   public ServerLocatorImpl setThreadPoolMaxSize(final int threadPoolMaxSize) {
      checkWrite();
      this.config.threadPoolMaxSize = threadPoolMaxSize;
      return this;
   }

   @Override
   public int getFlowControlThreadPoolMaxSize() {
      return config.flowControlThreadPoolMaxSize;
   }

   @Override
   public ServerLocatorImpl setFlowControlThreadPoolMaxSize(final int flowControlThreadPoolMaxSize) {
      checkWrite();
      this.config.flowControlThreadPoolMaxSize = flowControlThreadPoolMaxSize;
      return this;
   }

   @Override
   public long getRetryInterval() {
      return config.retryInterval;
   }

   @Override
   public ServerLocatorImpl setRetryInterval(final long retryInterval) {
      checkWrite();
      this.config.retryInterval = retryInterval;
      return this;
   }

   @Override
   public long getMaxRetryInterval() {
      return config.maxRetryInterval;
   }

   @Override
   public ServerLocatorImpl setMaxRetryInterval(final long retryInterval) {
      checkWrite();
      this.config.maxRetryInterval = retryInterval;
      return this;
   }

   @Override
   public double getRetryIntervalMultiplier() {
      return config.retryIntervalMultiplier;
   }

   @Override
   public ServerLocatorImpl setRetryIntervalMultiplier(final double retryIntervalMultiplier) {
      checkWrite();
      this.config.retryIntervalMultiplier = retryIntervalMultiplier;
      return this;
   }

   @Override
   public int getReconnectAttempts() {
      return config.reconnectAttempts;
   }

   @Override
   public ServerLocatorImpl setReconnectAttempts(final int reconnectAttempts) {
      checkWrite();
      this.config.reconnectAttempts = reconnectAttempts;
      return this;
   }

   @Override
   public ServerLocatorImpl setInitialConnectAttempts(int initialConnectAttempts) {
      checkWrite();
      this.config.initialConnectAttempts = initialConnectAttempts;
      return this;
   }

   @Override
   public int getInitialConnectAttempts() {
      return config.initialConnectAttempts;
   }

   @Override
   public ServerLocatorImpl setFailoverAttempts(int attempts) {
      checkWrite();
      this.config.failoverAttempts = attempts;
      return this;
   }

   @Override
   public int getFailoverAttempts() {
      return config.failoverAttempts;
   }

   @Deprecated
   @Override
   public boolean isFailoverOnInitialConnection() {
      return false;
   }

   @Deprecated
   @Override
   public ServerLocatorImpl setFailoverOnInitialConnection(final boolean failover) {
      return this;
   }

   @Override
   public String getConnectionLoadBalancingPolicyClassName() {
      return config.connectionLoadBalancingPolicyClassName;
   }

   @Override
   public ServerLocatorImpl setConnectionLoadBalancingPolicyClassName(final String loadBalancingPolicyClassName) {
      checkWrite();
      config.connectionLoadBalancingPolicyClassName = loadBalancingPolicyClassName;
      return this;
   }

   @Override
   public TransportConfiguration[] getStaticTransportConfigurations() {
      if (initialConnectors == null)
         return new TransportConfiguration[]{};
      return Arrays.copyOf(initialConnectors, initialConnectors.length);
   }

   @Override
   public DiscoveryGroupConfiguration getDiscoveryGroupConfiguration() {
      return discoveryGroupConfiguration;
   }

   @Override
   public ServerLocatorImpl addIncomingInterceptor(final Interceptor interceptor) {
      incomingInterceptors.add(interceptor);
      return this;
   }

   @Override
   public ServerLocatorImpl addOutgoingInterceptor(final Interceptor interceptor) {
      outgoingInterceptors.add(interceptor);
      return this;
   }

   @Override
   public boolean removeIncomingInterceptor(final Interceptor interceptor) {
      return incomingInterceptors.remove(interceptor);
   }

   @Override
   public boolean removeOutgoingInterceptor(final Interceptor interceptor) {
      return outgoingInterceptors.remove(interceptor);
   }

   @Override
   public int getInitialMessagePacketSize() {
      return config.initialMessagePacketSize;
   }

   @Override
   public ServerLocatorImpl setInitialMessagePacketSize(final int size) {
      checkWrite();
      config.initialMessagePacketSize = size;
      return this;
   }

   @Override
   public int getOnMessageCloseTimeout() {
      return config.onMessageCloseTimeout;
   }

   @Override
   public ServerLocator setOnMessageCloseTimeout(int onMessageCloseTimeout) {
      checkWrite();
      config.onMessageCloseTimeout = onMessageCloseTimeout;
      return this;
   }

   @Override
   public ServerLocatorImpl setGroupID(final String groupID) {
      checkWrite();
      this.groupID = groupID;
      return this;
   }

   @Override
   public String getGroupID() {
      return groupID;
   }

   @Override
   public boolean isCompressLargeMessage() {
      return config.compressLargeMessage;
   }

   @Override
   public ServerLocatorImpl setCompressLargeMessage(boolean avoid) {
      this.config.compressLargeMessage = avoid;
      return this;
   }

   @Override
   public int getCompressionLevel() {
      return config.compressionLevel;
   }

   @Override
   public ServerLocatorImpl setCompressionLevel(int compressionLevel) {
      this.config.compressionLevel = compressionLevel;
      return this;
   }

   private void checkWrite() {
      synchronized (stateGuard) {
         if (state != null && state != STATE.CLOSED) {
            throw new IllegalStateException("Cannot set attribute on SessionFactory after it has been used");
         }
      }
   }

   private int getNumInitialConnectors() {
      if (initialConnectors == null)
         return 0;
      return initialConnectors.length;
   }

   @Override
   public ServerLocatorImpl setIdentity(String identity) {
      this.identity = identity;
      return this;
   }

   @Override
   public ServerLocatorImpl setNodeID(String nodeID) {
      this.nodeID = nodeID;
      return this;
   }

   @Override
   public String getNodeID() {
      return nodeID;
   }

   @Override
   public ServerLocatorImpl setClusterConnection(boolean clusterConnection) {
      this.clusterConnection = clusterConnection;
      return this;
   }

   @Override
   public boolean isClusterConnection() {
      return clusterConnection;
   }

   @Override
   public TransportConfiguration getClusterTransportConfiguration() {
      return clusterTransportConfiguration;
   }

   @Override
   public ServerLocatorImpl setClusterTransportConfiguration(TransportConfiguration tc) {
      this.clusterTransportConfiguration = tc;
      return this;
   }

   @Override
   public void cleanup() {
      doClose(false);
   }

   @Override
   public void close() {
      doClose(true);
   }

   private void doClose(final boolean sendClose) {
      synchronized (stateGuard) {
         if (state == STATE.CLOSED) {
            logger.debug("{} is already closed when calling closed", this);
            return;
         }

         state = STATE.CLOSING;
      }
      if (latch != null)
         latch.countDown();

      synchronized (connectingFactories) {
         for (ClientSessionFactoryInternal csf : connectingFactories) {
            csf.causeExit();
         }
      }

      if (discoveryGroup != null) {
         try {
            discoveryGroup.stop();
         } catch (Exception e) {
            ActiveMQClientLogger.LOGGER.failedToStopDiscovery(e);
         }
      } else {
         staticConnector.disconnect();
      }

      synchronized (connectingFactories) {
         for (ClientSessionFactoryInternal csf : connectingFactories) {
            csf.causeExit();
         }
         for (ClientSessionFactoryInternal csf : connectingFactories) {
            csf.close();
         }
         connectingFactories.clear();
      }

      Set<ClientSessionFactoryInternal> clonedFactory;
      synchronized (factories) {
         clonedFactory = new HashSet<>(factories);

         factories.clear();
      }

      for (ClientSessionFactoryInternal factory : clonedFactory) {
         factory.causeExit();
      }
      for (ClientSessionFactory factory : clonedFactory) {
         if (sendClose) {
            try {
               factory.close();
            } catch (Throwable e) {
               logger.debug(e.getMessage(), e);
               factory.cleanup();
            }
         } else {
            factory.cleanup();
         }
      }

      if (shutdownPool) {
         if (threadPool != null) {
            ExecutorService executorService = (ExecutorService) threadPool;
            executorService.shutdown();

            try {
               if (!executorService.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                  ActiveMQClientLogger.LOGGER.timedOutWaitingForTermination();
               }
            } catch (InterruptedException e) {
               throw new ActiveMQInterruptedException(e);
            }
         }

         if (scheduledThreadPool != null) {
            scheduledThreadPool.shutdown();

            try {
               if (!scheduledThreadPool.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                  ActiveMQClientLogger.LOGGER.timedOutWaitingForScheduledPoolTermination();
               }
            } catch (InterruptedException e) {
               throw new ActiveMQInterruptedException(e);
            }
         }

         if (flowControlThreadPool != null) {
            ExecutorService executorService = (ExecutorService) flowControlThreadPool;
            executorService.shutdown();

            try {
               if (!executorService.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                  ActiveMQClientLogger.LOGGER.timedOutWaitingForTermination();
               }
            } catch (InterruptedException e) {
               throw new ActiveMQInterruptedException(e);
            }
         }
      }
      synchronized (stateGuard) {
         state = STATE.CLOSED;
      }
   }

   /**
    * This is directly called when the connection to the node is gone, or when the node sends a disconnection. Look for
    * callers of this method!
    */
   @Override
   public void notifyNodeDown(final long eventTime, final String nodeID, boolean disconnect) {

      if (!ha) {
         // there's no topology here
         return;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("nodeDown {} nodeID={} as being down", this, nodeID, new Exception("trace"));
      }

      topology.removeMember(eventTime, nodeID, disconnect);

      if (clusterConnection) {
         updateArraysAndPairs(eventTime);
      } else {
         if (topology.isEmpty()) {
            // Resetting the topology to its original condition as it was brand new
            receivedTopology = false;
         } else {
            updateArraysAndPairs(eventTime);

            if (topology.nodes() == 1 && topology.getMember(this.nodeID) != null) {
               // Resetting the topology to its original condition as it was brand new
               receivedTopology = false;
            }
         }
      }

   }

   @Override
   public void notifyNodeUp(long uniqueEventID,
                            final String nodeID,
                            final String backupGroupName,
                            final String scaleDownGroupName,
                            final Pair<TransportConfiguration, TransportConfiguration> connectorPair,
                            final boolean last) {
      if (logger.isTraceEnabled()) {
         logger.trace("NodeUp {}::nodeID={}, connectorPair={}", this, nodeID, connectorPair, new Exception("trace"));
      }

      TopologyMemberImpl member = new TopologyMemberImpl(nodeID, backupGroupName, scaleDownGroupName, connectorPair.getA(), connectorPair.getB());

      topology.updateMember(uniqueEventID, nodeID, member);

      TopologyMember actMember = topology.getMember(nodeID);

      if (actMember != null && actMember.getPrimary() != null && actMember.getBackup() != null) {
         Set<ClientSessionFactory> clonedFactories = new HashSet<>();
         synchronized (factories) {
            clonedFactories.addAll(factories);
         }

         for (ClientSessionFactory factory : clonedFactories) {
            ((ClientSessionFactoryInternal) factory).setBackupConnector(actMember.getPrimary(), actMember.getBackup());
         }
      }

      updateArraysAndPairs(uniqueEventID);

      if (last) {
         receivedTopology = true;
      }
   }

   @Override
   public String toString() {
      if (identity != null) {
         return "ServerLocatorImpl (identity=" + identity +
            ") [initialConnectors=" +
            Arrays.toString(Objects.requireNonNullElseGet(initialConnectors, () -> new TransportConfiguration[0])) +
            ", discoveryGroupConfiguration=" +
            discoveryGroupConfiguration +
            "]";
      }
      return "ServerLocatorImpl [initialConnectors=" + Arrays.toString(Objects.requireNonNullElseGet(initialConnectors, () -> new TransportConfiguration[0])) +
         ", discoveryGroupConfiguration=" +
         discoveryGroupConfiguration +
         "]";
   }

   @SuppressWarnings("unchecked")
   private void updateArraysAndPairs(long time) {
      if (updateArrayActor == null) {
         // if for some reason we don't have an actor, just go straight
         internalUpdateArray(time);
      } else {
         updateArrayActor.act(time);
      }
   }

   private void internalUpdateArray(long time) {
      synchronized (topologyArrayGuard) {
         Collection<TopologyMemberImpl> membersCopy = topology.getMembers();

         if (membersCopy.isEmpty()) {
            //it could happen when primary is down, in that case we keeps the old copy
            //and don't update
            return;
         }

         Pair<TransportConfiguration, TransportConfiguration>[] topologyArrayLocal = (Pair<TransportConfiguration, TransportConfiguration>[]) Array.newInstance(Pair.class, membersCopy.size());

         int count = 0;
         for (TopologyMemberImpl pair : membersCopy) {
            Pair<TransportConfiguration, TransportConfiguration> transportConfigs = pair.getConnector();
            topologyArrayLocal[count++] = new Pair<>(protocolManagerFactory.adaptTransportConfiguration(transportConfigs.getA()),
                                                     protocolManagerFactory.adaptTransportConfiguration(transportConfigs.getB()));
         }

         this.topologyArray = topologyArrayLocal;
      }
   }

   @Override
   public synchronized void connectorsChanged(List<DiscoveryEntry> newConnectors) {
      DiscoveryListener discoveryListener = this.discoveryListener;

      if (receivedTopology) {
         if (discoveryListener != null) {
            discoveryListener.connectorsChanged(newConnectors);
         }
         return;
      }

      final List<TransportConfiguration> newInitialconnectors = new ArrayList<>(newConnectors.size());

      for (DiscoveryEntry entry : newConnectors) {
         // ignore its own transport connector
         if (!entry.getConnector().equals(clusterTransportConfiguration)) {
            newInitialconnectors.add(entry.getConnector());
         }
      }

      this.initialConnectors = newInitialconnectors.toArray(new TransportConfiguration[newInitialconnectors.size()]);

      if (clusterConnection && !receivedTopology && this.getNumInitialConnectors() > 0) {
         // The node is alone in the cluster. We create a connection to the new node
         // to trigger the node notification to form the cluster.

         Runnable connectRunnable = () -> {
            try {
               connect();
            } catch (ActiveMQException e) {
               ActiveMQClientLogger.LOGGER.errorConnectingToNodes(e);
            }
         };
         if (startExecutor != null) {
            startExecutor.execute(connectRunnable);
         } else {
            connectRunnable.run();
         }
      }

      if (discoveryListener != null) {
         discoveryListener.connectorsChanged(newConnectors);
      }
   }

   @Override
   public void factoryClosed(final ClientSessionFactory factory) {
      boolean isEmpty;
      synchronized (factories) {
         factories.remove(factory);
         isEmpty = factories.isEmpty();
      }

      if (!clusterConnection && isEmpty) {
         receivedTopology = false;
      }
   }

   @Override
   public ServerLocator setUseTopologyForLoadBalancing(boolean useTopologyForLoadBalancing) {
      this.config.useTopologyForLoadBalancing = useTopologyForLoadBalancing;
      return this;
   }

   @Override
   public boolean getUseTopologyForLoadBalancing() {
      return config.useTopologyForLoadBalancing;
   }

   @Override
   public Topology getTopology() {
      return topology;
   }

   @Override
   public boolean isConnectable() {
      return getNumInitialConnectors() > 0 || getDiscoveryGroupConfiguration() != null;
   }

   @Override
   public ServerLocatorImpl addClusterTopologyListener(final ClusterTopologyListener listener) {
      topology.addClusterTopologyListener(listener);
      return this;
   }

   @Override
   public void removeClusterTopologyListener(final ClusterTopologyListener listener) {
      topology.removeClusterTopologyListener(listener);
   }

   /**
    * for tests only and not part of the public interface. Do not use it.
    */
   public TransportConfiguration[] getInitialConnectors() {
      return initialConnectors;
   }

   private void addFactory(ClientSessionFactoryInternal factory) {
      if (factory == null) {
         return;
      }

      if (isClosed()) {
         factory.close();
         return;
      }

      TransportConfiguration backup = null;

      if (ha) {
         backup = topology.getBackupForConnector((Connector) factory.getConnector());
      }

      factory.setBackupConnector(factory.getConnectorConfiguration(), backup);

      synchronized (factories) {
         factories.add(factory);
      }
   }

   private final class StaticConnector implements Serializable {

      private static final long serialVersionUID = 6772279632415242634L;

      private List<Connector> connectors;

      public ClientSessionFactory connect(boolean skipWarnings) throws ActiveMQException {
         assertOpen();

         initialize();

         createConnectors();

         try {

            int retryNumber = 0;
            while (!isClosed()) {
               retryNumber++;
               for (Connector conn : connectors) {
                  logger.trace("{}::Submitting connect towards {}", this, conn);

                  ClientSessionFactory csf = conn.tryConnect();

                  if (csf != null) {
                     csf.getConnection().addFailureListener(new FailureListener() {
                        // Case the node where the cluster connection was connected is gone, we need to restart the
                        // connection
                        @Override
                        public void connectionFailed(ActiveMQException exception, boolean failedOver) {
                           if (clusterConnection && exception.getType() == ActiveMQExceptionType.DISCONNECTED) {
                              try {
                                 ServerLocatorImpl.this.start(startExecutor);
                              } catch (Exception e) {
                                 // There isn't much to be done if this happens here
                                 ActiveMQClientLogger.LOGGER.errorStartingLocator(e);
                              }
                           }
                        }

                        @Override
                        public void connectionFailed(final ActiveMQException me,
                                                     boolean failedOver,
                                                     String scaleDownTargetNodeID) {
                           connectionFailed(me, failedOver);
                        }

                        @Override
                        public String toString() {
                           return "FailureListener('restarts cluster connections')";
                        }
                     });

                     if (logger.isTraceEnabled()) {
                        logger.trace("Returning {} after {} retries on StaticConnector {}", csf, retryNumber, ServerLocatorImpl.this);
                     }

                     return csf;
                  }
               }

               if (config.initialConnectAttempts >= 0 && retryNumber > config.initialConnectAttempts) {
                  break;
               }

               if (latch.await(config.retryInterval, TimeUnit.MILLISECONDS))
                  return null;
            }

         } catch (RejectedExecutionException e) {
            if (isClosed() || skipWarnings)
               return null;
            logger.trace("Rejected execution", e);
            throw e;
         } catch (Exception e) {
            if (isClosed() || skipWarnings)
               return null;
            ActiveMQClientLogger.LOGGER.errorConnectingToNodes(e);
            throw ActiveMQClientMessageBundle.BUNDLE.cannotConnectToStaticConnectors(e);
         }

         if (isClosed() || skipWarnings) {
            return null;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("Could not connect to any nodes. throwing an error now.", new Exception("trace"));
         }

         throw ActiveMQClientMessageBundle.BUNDLE.cannotConnectToStaticConnectors2();
      }

      private synchronized void createConnectors() {
         if (connectors != null) {
            for (Connector conn : connectors) {
               if (conn != null) {
                  conn.disconnect();
               }
            }
         }
         connectors = new ArrayList<>();
         if (initialConnectors != null) {
            for (TransportConfiguration initialConnector : initialConnectors) {
               ClientSessionFactoryInternal factory = new ClientSessionFactoryImpl(ServerLocatorImpl.this, initialConnector, config, config.reconnectAttempts, threadPool, scheduledThreadPool, flowControlThreadPool, incomingInterceptors, outgoingInterceptors);

               connectors.add(new Connector(initialConnector, factory));
            }
         }
      }

      public synchronized void disconnect() {
         if (connectors != null) {
            for (Connector connector : connectors) {
               connector.disconnect();
            }
         }
      }

      private final class Connector {

         private final TransportConfiguration initialConnector;

         private volatile ClientSessionFactoryInternal factory;

         private Connector(TransportConfiguration initialConnector, ClientSessionFactoryInternal factory) {
            this.initialConnector = initialConnector;
            this.factory = factory;
         }

         public ClientSessionFactory tryConnect() throws ActiveMQException {
            logger.trace("{}::Trying to connect to {}", this, factory);
            try {
               ClientSessionFactoryInternal factoryToUse = factory;
               if (factoryToUse != null) {
                  addToConnecting(factoryToUse);

                  try {
                     factoryToUse.connect(1, false);
                  } finally {
                     removeFromConnecting(factoryToUse);
                  }
               }
               return factoryToUse;
            } catch (ActiveMQException e) {
               logger.trace("{}::Exception on establish connector initial connection", this, e);
               return null;
            }
         }

         public void disconnect() {
            if (factory != null) {
               factory.causeExit();
               factory.cleanup();
               factory = null;
            }
         }

         @Override
         public String toString() {
            return "Connector [initialConnector=" + initialConnector + "]";
         }

      }
   }

   private void assertOpen() {
      synchronized (stateGuard) {
         if (state != null && state != STATE.INITIALIZED) {
            throw new IllegalStateException("Server locator is closed (maybe it was garbage collected)");
         }
      }
   }

   @Override
   public boolean isClosed() {
      synchronized (stateGuard) {
         return state != STATE.INITIALIZED;
      }
   }

   private Object writeReplace() throws ObjectStreamException {
      return new ServerLocatorImpl(this);
   }

   public boolean isReceivedTopology() {
      return receivedTopology;
   }

   public int getClientSessionFactoryCount() {
      return factories.size();
   }

   private String fromInterceptors(final List<Interceptor> interceptors) {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Interceptor value : interceptors) {
         if (!first) {
            sb.append(",");
         }
         first = false;
         sb.append(value.getClass().getName());
      }

      return sb.toString();
   }

   private void feedInterceptors(final List<Interceptor> interceptors, final String interceptorList) {
      interceptors.clear();

      if (interceptorList == null || interceptorList.trim().isEmpty()) {
         return;
      }
      SecurityManagerShim.doPrivileged((PrivilegedAction<Object>) () -> {

         String[] arrayInterceptor = interceptorList.split(",");
         for (String strValue : arrayInterceptor) {
            Interceptor interceptor = (Interceptor) ClassloadingUtil.newInstanceFromClassLoader(ServerLocatorImpl.class, strValue.trim(), Interceptor.class);
            interceptors.add(interceptor);
         }
         return null;
      });

   }

}
