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
package org.apache.activemq.artemis.core.remoting.server.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.ActiveMQRemoteDisconnectException;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.CoreProtocolManagerFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ServiceRegistry;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.reload.ReloadManager;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactory;
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactoryProvider;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactoryProvider;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.PemConfigUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_SSL_AUTO_RELOAD;
import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PATH_PROP_NAME;
import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_TYPE_PROP_NAME;
import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_AUTO_RELOAD_PROP_NAME;
import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PATH_PROP_NAME;
import static org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_TYPE_PROP_NAME;

public class RemotingServiceImpl implements RemotingService, ServerConnectionLifeCycleListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int ACCEPTOR_STOP_TIMEOUT = 3000;

   private static final int UPDATE_ACCEPTORS_STOP_TIMEOUT = 5000;

   private volatile boolean started = false;

   private final Map<String, TransportConfiguration> acceptorsConfig;

   private final List<BaseInterceptor> incomingInterceptors = new CopyOnWriteArrayList<>();

   private final List<BaseInterceptor> outgoingInterceptors = new CopyOnWriteArrayList<>();

   private final Map<String, Acceptor> acceptors = new HashMap<>();

   private final ConcurrentMap<Object, ConnectionEntry> connections = new ConcurrentHashMap<>();

   private final ReusableLatch connectionCountLatch = new ReusableLatch(0);

   private final ActiveMQServer server;

   private final ManagementService managementService;

   private ExecutorService threadPool;

   private final Executor flushExecutor;

   private final ScheduledExecutorService scheduledThreadPool;

   private FailureCheckAndFlushThread failureCheckAndFlushThread;

   private final ClusterManager clusterManager;

   private final Map<String, ProtocolManagerFactory> protocolMap = new ConcurrentHashMap<>();

   private ActiveMQPrincipal defaultInvmSecurityPrincipal;

   private ServiceRegistry serviceRegistry;

   private boolean paused = false;

   private AtomicLong totalConnectionCount = new AtomicLong(0);

   private long connectionTtlCheckInterval;


   public RemotingServiceImpl(final ClusterManager clusterManager,
                              final Configuration config,
                              final ActiveMQServer server,
                              final ManagementService managementService,
                              final ScheduledExecutorService scheduledThreadPool,
                              List<ProtocolManagerFactory> protocolManagerFactories,
                              final Executor flushExecutor,
                              final ServiceRegistry serviceRegistry) {
      this.serviceRegistry = serviceRegistry;

      if (config.getAcceptorConfigurations() != null && !config.getAcceptorConfigurations().isEmpty()) {
         acceptorsConfig = config.getAcceptorConfigurations().stream()
                                                             .collect(Collectors.toMap(c -> c.getName(), Function.identity()));
      } else {
         acceptorsConfig = new HashMap<>();
      }

      this.server = server;

      this.clusterManager = clusterManager;

      setInterceptors(config);

      this.managementService = managementService;

      this.scheduledThreadPool = scheduledThreadPool;

      CoreProtocolManagerFactory coreProtocolManagerFactory = new CoreProtocolManagerFactory();

      MessagePersister.registerProtocol(coreProtocolManagerFactory);

      this.flushExecutor = flushExecutor;

      ActiveMQServerLogger.LOGGER.addingProtocolSupport(coreProtocolManagerFactory.getModuleName(), coreProtocolManagerFactory.getProtocols()[0]);
      this.protocolMap.put(coreProtocolManagerFactory.getProtocols()[0], coreProtocolManagerFactory);

      if (config.isResolveProtocols()) {
         resolveProtocols(this.getClass().getClassLoader());

         if (this.getClass().getClassLoader() != Thread.currentThread().getContextClassLoader()) {
            resolveProtocols(Thread.currentThread().getContextClassLoader());
         }
      }

      if (protocolManagerFactories != null) {
         loadProtocolManagerFactories(protocolManagerFactories);
      }

      this.connectionTtlCheckInterval = config.getConnectionTtlCheckInterval();
   }

   private void setInterceptors(Configuration configuration) {
      incomingInterceptors.addAll(serviceRegistry.getIncomingInterceptors(configuration.getIncomingInterceptorClassNames()));
      outgoingInterceptors.addAll(serviceRegistry.getOutgoingInterceptors(configuration.getOutgoingInterceptorClassNames()));
   }

   @Override
   public Map<String, ProtocolManagerFactory> getProtocolFactoryMap() {
      return protocolMap;
   }

   @Override
   public synchronized void start() throws Exception {
      if (started) {
         return;
      }

      logger.trace("Starting remoting service {}", this);

      paused = false;

      // this is used for in-vm but for Netty it's only used for executing failure listeners
      threadPool = Executors.newCachedThreadPool(SecurityManagerShim.doPrivileged((PrivilegedAction<ThreadFactory>) () -> new ActiveMQThreadFactory(server.getThreadGroupName("remoting-service"), false, Thread.currentThread().getContextClassLoader())));

      for (TransportConfiguration info : acceptorsConfig.values()) {
         createAcceptor(info);
      }

      /*
       * Don't start the acceptors here.  Only start the acceptors at the every end of the start-up process to avoid
       * race conditions. See {@link #startAcceptors()}.
       */

      // This thread checks connections that need to be closed, and also flushes confirmations
      failureCheckAndFlushThread = new FailureCheckAndFlushThread(connectionTtlCheckInterval);

      failureCheckAndFlushThread.start();

      started = true;
   }

   @Override
   public Acceptor createAcceptor(String name, String uri) throws Exception {
      List<TransportConfiguration> configurations = ConfigurationUtils.parseAcceptorURI(name, uri);

      return createAcceptor(configurations.get(0));
   }

   @Override
   public Acceptor createAcceptor(TransportConfiguration info) {
      Acceptor acceptor = null;

      try {
         AcceptorFactory factory = server.getServiceRegistry().getAcceptorFactory(info.getName(), info.getFactoryClassName());

         Map<String, ProtocolManagerFactory> selectedProtocolFactories = new ConcurrentHashMap<>();

         @SuppressWarnings("deprecation")
         String protocol = ConfigurationHelper.getStringProperty(TransportConstants.PROTOCOL_PROP_NAME, null, info.getParams());
         if (protocol != null) {
            ActiveMQServerLogger.LOGGER.warnDeprecatedProtocol();
            locateProtocols(protocol, info, selectedProtocolFactories);
         }

         String protocols = ConfigurationHelper.getStringProperty(TransportConstants.PROTOCOLS_PROP_NAME, null, info.getParams());

         if (protocols != null) {
            locateProtocols(protocols, info, selectedProtocolFactories);
         }

         ClusterConnection clusterConnection = lookupClusterConnection(info);

         // If empty: we get the default list
         if (selectedProtocolFactories.isEmpty()) {
            selectedProtocolFactories = protocolMap;
         }

         Map<String, ProtocolManager> selectedProtocols = new ConcurrentHashMap<>();
         for (Entry<String, ProtocolManagerFactory> entry : selectedProtocolFactories.entrySet()) {
            selectedProtocols.put(entry.getKey(), entry.getValue().createProtocolManager(server, info.getCombinedParams(), incomingInterceptors, outgoingInterceptors));
         }

         acceptor = factory.createAcceptor(info.getName(), clusterConnection, info.getParams(), new DelegatingBufferHandler(), this, threadPool, scheduledThreadPool, selectedProtocols, server.getThreadGroupName("remoting-" + info.getName()), server.getMetricsManager());

         if (defaultInvmSecurityPrincipal != null && acceptor.isUnsecurable()) {
            acceptor.setDefaultActiveMQPrincipal(defaultInvmSecurityPrincipal);
         }

         acceptors.put(info.getName(), acceptor);

         if (managementService != null) {
            acceptor.setNotificationService(managementService);

            managementService.registerAcceptor(acceptor, info);
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorCreatingAcceptor(info.getName(), e);
      }

      if (server.getConfiguration().getConfigurationFileRefreshPeriod() > 0) {
         // track TLS resources on acceptors and reload on updates
         final Map<String, Object> config = info.getCombinedParams();

         if (ConfigurationHelper.getBooleanProperty(SSL_AUTO_RELOAD_PROP_NAME, DEFAULT_SSL_AUTO_RELOAD, config)) {
            addAcceptorStoreReloadCallback(info.getName(),
               fileUrlFrom(config.get(KEYSTORE_PATH_PROP_NAME)),
               storeTypeFrom(config.get(KEYSTORE_TYPE_PROP_NAME)));

            addAcceptorStoreReloadCallback(info.getName(),
               fileUrlFrom(config.get(TRUSTSTORE_PATH_PROP_NAME)),
               storeTypeFrom(config.get(TRUSTSTORE_TYPE_PROP_NAME)));
         }
      }

      return acceptor;
   }

   @Override
   public Map<String, Acceptor> getAcceptors() {
      return acceptors;
   }

   @Override
   public void destroyAcceptor(String name) throws Exception {
      Acceptor acceptor = acceptors.get(name);
      if (acceptor != null) {
         acceptor.stop();
         acceptors.remove(name);
      }
   }

   @Override
   public synchronized void startAcceptors() throws Exception {
      if (isStarted()) {
         for (Acceptor a : acceptors.values()) {
            try {
               if (!a.isAutoStart()) {
                  continue;
               }
               a.start();
            } catch (Throwable t) {
               ActiveMQServerLogger.LOGGER.errorStartingAcceptor(a.getName(), a.getConfiguration());
               throw t;
            }
         }
      }
   }

   @Override
   public synchronized void allowInvmSecurityOverride(ActiveMQPrincipal principal) {
      defaultInvmSecurityPrincipal = principal;
      for (Acceptor acceptor : acceptors.values()) {
         if (acceptor.isUnsecurable()) {
            acceptor.setDefaultActiveMQPrincipal(principal);
         }
      }
   }

   @Override
   public synchronized void pauseAcceptors() {
      if (!started)
         return;
      paused = true;

      for (Acceptor acceptor : acceptors.values()) {
         try {
            acceptor.pause();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }
      }
   }

   @Override
   public synchronized boolean isPaused() {
      return paused;
   }

   @Override
   public synchronized void freeze(final String scaleDownNodeID, final CoreRemotingConnection connectionToKeepOpen) {
      if (!started)
         return;
      failureCheckAndFlushThread.close(false);
      Map<Object, ConnectionEntry> connectionEntries = new HashMap<>(connections);

      // Now we ensure that no connections will process any more packets after this method is
      // complete then send a disconnect packet
      for (Entry<Object, ConnectionEntry> entry : connectionEntries.entrySet()) {
         RemotingConnection conn = entry.getValue().connection;

         if (conn.equals(connectionToKeepOpen)) {
            continue;
         }

         logger.trace("Sending connection.disconnection packet to {}", conn);

         if (!conn.isClient()) {
            conn.disconnect(scaleDownNodeID, false);
            removeConnection(entry.getKey());
         }
      }
   }

   @Override
   public void notifyStop() {

      // We need to stop them accepting first so no new connections are accepted after we send the disconnect message
      for (Acceptor acceptor : acceptors.values()) {
         logger.debug("send stop notifications on acceptor {}", acceptor);

         try {
            acceptor.notifyStop();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }
      }
   }

   @Override
   public void prepareStop(boolean criticalError, Set<RemotingConnection> ignoreList) throws Exception {

      // We need to stop them accepting first so no new connections are accepted after we send the disconnect message
      for (Acceptor acceptor : acceptors.values()) {
         logger.debug("Pausing acceptor {}", acceptor);

         try {
            acceptor.pause();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }

      }

      logger.debug("Sending disconnect on client connections");

      Set<ConnectionEntry> connectionEntries = new HashSet<>(connections.values());

      // Now we ensure that no connections will process any more packets after this method is complete
      // then send a disconnect packet
      for (ConnectionEntry entry : connectionEntries) {
         RemotingConnection conn = entry.connection;

         if (ignoreList.contains(conn)) {
            logger.debug("ignoring connection {} during the close", conn);
         } else {
            logger.debug("Sending disconnect on connection {} from server {}", conn.getID(), server);
            conn.disconnect(criticalError);
         }
      }

   }

   @Override
   public void stop(final boolean criticalError) throws Exception {
      if (!started) {
         return;
      }
      SSLContextFactoryProvider.getSSLContextFactory().clearSSLContexts();
      OpenSSLContextFactory openSSLContextFactory = OpenSSLContextFactoryProvider.getOpenSSLContextFactory();
      if (openSSLContextFactory != null) {
         openSSLContextFactory.clearSslContexts();
      }

      failureCheckAndFlushThread.close(criticalError);

      // ActiveMQServerImpl already calls prepareStop
      // however we call this again here for two reasons:
      // I - ActiveMQServer might have ignored the one connection for Replication
      // II - this method could be called in other places for Embedding or testing and the semantic must be kept the same
      prepareStop(criticalError, Collections.emptySet());

      CountDownLatch acceptorCountDownLatch = new CountDownLatch(acceptors.size());
      for (Acceptor acceptor : acceptors.values()) {
         try {
            acceptor.asyncStop(acceptorCountDownLatch::countDown);
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }
      }
      //In some cases an acceptor stopping could be locked ie NettyAcceptor stopping could be locked by a network failure.
      acceptorCountDownLatch.await(ACCEPTOR_STOP_TIMEOUT, TimeUnit.MILLISECONDS);

      acceptors.clear();

      connections.clear();
      connectionCountLatch.setCount(0);

      if (managementService != null) {
         managementService.unregisterAcceptors();
      }

      threadPool.shutdown();

      if (!criticalError) {
         boolean ok = threadPool.awaitTermination(10000, TimeUnit.MILLISECONDS);

         if (!ok) {
            ActiveMQServerLogger.LOGGER.timeoutRemotingThreadPool();
         }
      }

      started = false;
   }

   @Override
   public Acceptor getAcceptor(String name) {
      return acceptors.get(name);
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public RemotingConnection getConnection(final Object remotingConnectionID) {
      ConnectionEntry entry = connections.get(remotingConnectionID);

      if (entry != null) {
         return entry.connection;
      } else {
         ActiveMQServerLogger.LOGGER.errorRemovingConnection();

         return null;
      }
   }

   public ConnectionEntry getConnectionEntry(final Object remotingConnectionID) {
      ConnectionEntry entry = connections.get(remotingConnectionID);

      if (entry != null) {
         return entry;
      } else {
         return null;
      }
   }

   @Override
   public RemotingConnection removeConnection(final Object remotingConnectionID) {
      ConnectionEntry entry = connections.remove(remotingConnectionID);

      if (entry != null) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.destroyedConnection(entry.connection.getProtocolName(), entry.connection.getID(), entry.connection.getSubject(), entry.connection.getRemoteAddress());
         }
         if (logger.isDebugEnabled()) {
            logger.debug("RemotingServiceImpl::removing succeeded connection ID {}, we now have {} connections", remotingConnectionID, connections.size());
         }
         connectionCountLatch.countDown();
         return entry.connection;
      } else {
         logger.debug("The connectionID::{} was already removed by some other module", remotingConnectionID);

         return null;
      }
   }

   @Override
   public synchronized Set<RemotingConnection> getConnections() {
      Set<RemotingConnection> conns = new HashSet<>(connections.size());

      for (ConnectionEntry entry : connections.values()) {
         conns.add(entry.connection);
      }

      return conns;
   }

   // for testing purposes to verify TTL has been set properly
   public synchronized Set<ConnectionEntry> getConnectionEntries() {
      Set<ConnectionEntry> conns = new HashSet<>(connections.size());

      for (ConnectionEntry entry : connections.values()) {
         conns.add(entry);
      }

      return conns;
   }

   @Override
   public int getConnectionCount() {
      return connections.size();
   }

   @Override
   public long getTotalConnectionCount() {
      return totalConnectionCount.get();
   }

   @Override
   public synchronized ReusableLatch getConnectionCountLatch() {
      return connectionCountLatch;
   }

   @Override
   public void loadProtocolServices(List<ActiveMQComponent> protocolServices) {
      for (ProtocolManagerFactory protocolManagerFactory : protocolMap.values()) {
         protocolManagerFactory.loadProtocolServices(this.server, protocolServices);
      }
   }

   @Override
   public void updateProtocolServices(List<ActiveMQComponent> protocolServices) throws Exception {
      updateAcceptors();

      for (ProtocolManagerFactory protocolManagerFactory : protocolMap.values()) {
         protocolManagerFactory.updateProtocolServices(this.server, protocolServices);
      }
   }

   private void updateAcceptors() throws Exception {
      final Set<TransportConfiguration> updatedConfigurationSet = Objects.requireNonNullElse(server.getConfiguration().getAcceptorConfigurations(), Collections.emptySet());
      final Map<String, TransportConfiguration> updatedConfiguration =
         updatedConfigurationSet.stream()
                                .collect(Collectors.toMap(c -> c.getName(), Function.identity()));

      final Set<TransportConfiguration> acceptorsToStop = new HashSet<>();
      final Set<TransportConfiguration> acceptorsToCreate = new HashSet<>();

      for (TransportConfiguration candidateConfiguration : updatedConfiguration.values()) {
         final TransportConfiguration previous = acceptorsConfig.get(candidateConfiguration.getName());

         if (previous == null) {
            // New configuration that was added during the update
            acceptorsToCreate.add(candidateConfiguration);
         } else if (!previous.equals(candidateConfiguration)) {
            // Updated configuration that needs to be stopped and restarted.
            acceptorsToCreate.add(candidateConfiguration);
            acceptorsToStop.add(candidateConfiguration);
         }
      }

      for (TransportConfiguration currentConfiguration : acceptorsConfig.values()) {
         if (!updatedConfiguration.containsKey(currentConfiguration.getName())) {
            // Acceptor that was removed from the configuration which needs stopped and removed.
            acceptorsToStop.add(currentConfiguration);
         }
      }

      // Replace old configuration map with new configurations ahead of the stop and restart phase.
      acceptorsConfig.clear();
      acceptorsConfig.putAll(updatedConfiguration);

      final CountDownLatch acceptorsStoppedLatch = new CountDownLatch(acceptorsToStop.size());

      for (TransportConfiguration acceptorToStop : acceptorsToStop) {
         final Acceptor acceptor = acceptors.remove(acceptorToStop.getName());

         if (acceptor == null) {
            continue;
         }

         final Map<String, Object> acceptorToStopParams = acceptorToStop.getCombinedParams();

         removeAcceptorStoreReloadCallback(acceptorToStop.getName(),
            fileUrlFrom(acceptorToStopParams.get(KEYSTORE_PATH_PROP_NAME)),
            storeTypeFrom(acceptorToStopParams.get(KEYSTORE_TYPE_PROP_NAME)));

         removeAcceptorStoreReloadCallback(acceptorToStop.getName(),
            fileUrlFrom(acceptorToStopParams.get(TRUSTSTORE_PATH_PROP_NAME)),
            storeTypeFrom(acceptorToStopParams.get(TRUSTSTORE_TYPE_PROP_NAME)));

         try {
            managementService.unregisterAcceptor(acceptor.getName());
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }

         try {
            acceptor.notifyStop();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }

         try {
            acceptor.pause();
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }

         try {
            acceptor.asyncStop(acceptorsStoppedLatch::countDown);
         } catch (Throwable t) {
            ActiveMQServerLogger.LOGGER.errorStoppingAcceptor(acceptor.getName());
         }
      }

      // In some cases an acceptor stopping could be locked ie NettyAcceptor stopping could be locked by a network failure.
      if (!acceptorsStoppedLatch.await(UPDATE_ACCEPTORS_STOP_TIMEOUT, TimeUnit.MILLISECONDS)) {
         logger.warn("Timed out waiting on removed or updated acceptors stopping.");
      }

      final Collection<Acceptor> acceptorsToStart = new ArrayList<>();

      // Add all the new or updated acceptors now that removed or updated acceptors have been stopped.
      for (TransportConfiguration candidateConfiguration : acceptorsToCreate) {
         final Acceptor acceptor = createAcceptor(candidateConfiguration);

         if (isStarted() && acceptor.isAutoStart()) {
            acceptorsToStart.add(acceptor);
         }
      }

      Exception acceptorStartError = null;

      for (Acceptor acceptor : acceptorsToStart) {
         try {
            acceptor.start();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorStartingAcceptor(acceptor.getName(), acceptor.getConfiguration());
            if (acceptorStartError == null) {
               acceptorStartError = e;
            }
         }
      }

      if (acceptorStartError != null) {
         throw acceptorStartError;
      }
   }

   // ServerConnectionLifeCycleListener implementation -----------------------------------

   private ProtocolManagerFactory getProtocolManager(String protocol) {
      return protocolMap.get(protocol);
   }

   @Override
   public void connectionCreated(final ActiveMQComponent component,
                                 final Connection connection,
                                 final ProtocolManager protocol) {
      if (server == null) {
         throw new IllegalStateException("Unable to create connection, server hasn't finished starting up");
      }

      ConnectionEntry entry = protocol.createConnectionEntry((Acceptor) component, connection);
      try {
         if (server.hasBrokerConnectionPlugins()) {
            server.callBrokerConnectionPlugins(plugin -> plugin.afterCreateConnection(entry.connection));
         }
      } catch (ActiveMQException t) {
         logger.warn("Error executing afterCreateConnection plugin method: {}", t.getMessage(), t);
         throw new IllegalStateException(t.getMessage(), t.getCause());

      }

      logger.trace("Connection created {}", connection);

      addConnectionEntry(connection, entry);
      connectionCountLatch.countUp();
      totalConnectionCount.incrementAndGet();
   }

   @Override
   public void addConnectionEntry(Connection connection, ConnectionEntry entry) {
      connections.put(connection.getID(), entry);
      if (AuditLogger.isResourceLoggingEnabled()) {
         AuditLogger.createdConnection(connection.getProtocolConnection() == null ? null : connection.getProtocolConnection().getProtocolName(), connection.getID(), connection.getRemoteAddress());
      }
      if (logger.isDebugEnabled()) {
         logger.debug("Adding connection {}, we now have {} on server {}", connection.getID(), connections.size(), server);
      }
   }

   @Override
   public void connectionDestroyed(final Object connectionID, boolean failed) {
      if (logger.isTraceEnabled()) {
         logger.trace("Connection removed {} from server {}", connectionID, this.server, new Exception("trace"));
      }

      if (failed) {
         issueFailure(connectionID, new ActiveMQRemoteDisconnectException());
      } else {
         issueClose(connectionID);
      }
   }

   private void issueFailure(Object connectionID, ActiveMQException e) {
      ConnectionEntry conn = connections.get(connectionID);

      if (conn != null && !conn.connection.isSupportReconnect()) {
         RemotingConnection removedConnection = removeConnection(connectionID);
         if (removedConnection != null) {
            try {
               if (server.hasBrokerConnectionPlugins()) {
                  server.callBrokerConnectionPlugins(plugin -> plugin.afterDestroyConnection(removedConnection));
               }
            } catch (ActiveMQException t) {
               logger.warn("Error executing afterDestroyConnection plugin method: {}", t.getMessage(), t);
               conn.connection.fail(t);
               return;
            }
         }
         conn.connection.fail(e);
      }
   }

   private void issueClose(Object connectionID) {
      ConnectionEntry conn = connections.get(connectionID);

      if (conn != null && !conn.connection.isSupportReconnect()) {
         RemotingConnection removedConnection = removeConnection(connectionID);
         if (removedConnection != null) {
            try {
               if (server.hasBrokerConnectionPlugins()) {
                  server.callBrokerConnectionPlugins(plugin -> plugin.afterDestroyConnection(removedConnection));
               }
            } catch (ActiveMQException t) {
               logger.warn("Error executing afterDestroyConnection plugin method: {}", t.getMessage(), t);
            }
         }
         conn.connection.close();
      }
   }



   @Override
   public void connectionException(final Object connectionID, final ActiveMQException me) {
      issueFailure(connectionID, me);
   }

   @Override
   public void connectionReadyForWrites(final Object connectionID, final boolean ready) {
   }

   @Override
   public void addIncomingInterceptor(final BaseInterceptor interceptor) {
      incomingInterceptors.add(interceptor);

      updateProtocols();
   }

   @Override
   public List<BaseInterceptor> getIncomingInterceptors() {
      return Collections.unmodifiableList(incomingInterceptors);
   }

   @Override
   public boolean removeIncomingInterceptor(final BaseInterceptor interceptor) {
      if (incomingInterceptors.remove(interceptor)) {
         updateProtocols();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void addOutgoingInterceptor(final BaseInterceptor interceptor) {
      outgoingInterceptors.add(interceptor);
      updateProtocols();
   }

   @Override
   public List<BaseInterceptor> getOutgoinInterceptors() {
      return Collections.unmodifiableList(outgoingInterceptors);
   }

   @Override
   public boolean removeOutgoingInterceptor(final BaseInterceptor interceptor) {
      if (outgoingInterceptors.remove(interceptor)) {
         updateProtocols();
         return true;
      } else {
         return false;
      }
   }

   private ClusterConnection lookupClusterConnection(TransportConfiguration acceptorConfig) {
      String clusterConnectionName = (String) acceptorConfig.getParams().get(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.CLUSTER_CONNECTION);

      ClusterConnection clusterConnection = null;
      if (clusterConnectionName != null) {
         clusterConnection = clusterManager.getClusterConnection(clusterConnectionName);
      }

      // if not found we will still use the default name, even if a name was provided
      if (clusterConnection == null) {
         clusterConnection = clusterManager.getDefaultConnection(acceptorConfig);
      }

      return clusterConnection;
   }


   private final class DelegatingBufferHandler implements BufferHandler {

      @Override
      public void bufferReceived(final Object connectionID, final ActiveMQBuffer buffer) {
         ConnectionEntry conn = connections.get(connectionID);

         if (conn != null) {
            try {
               conn.connection.bufferReceived(connectionID, buffer);
            } catch (RuntimeException e) {
               ActiveMQServerLogger.LOGGER.disconnectCritical("Error decoding buffer", e);
               conn.connection.fail(new ActiveMQException(e.getMessage()));
            }
         } else {
            logger.trace("ConnectionID = {} was already closed, so ignoring packet", connectionID);
         }
      }
   }

   private final class FailureCheckAndFlushThread extends Thread {

      private final long pauseInterval;

      private volatile boolean closed;
      private final CountDownLatch latch = new CountDownLatch(1);

      FailureCheckAndFlushThread(final long pauseInterval) {
         super("activemq-failure-check-thread");

         this.pauseInterval = pauseInterval;
      }

      public void close(final boolean criticalError) {
         closed = true;

         latch.countDown();

         if (!criticalError) {
            try {
               join();
            } catch (InterruptedException e) {
               throw new ActiveMQInterruptedException(e);
            }
         }
      }

      @Override
      public void run() {
         while (!closed) {
            try {
               Set<Pair<Object, Long>> toRemove = new HashSet<>();
               for (ConnectionEntry entry : connections.values()) {
                  final RemotingConnection conn = entry.connection;
                  final long lastCheck = entry.lastCheck;
                  final long ttl = entry.ttl;
                  final long now = System.currentTimeMillis();

                  boolean flush = true;

                  if (ttl != -1) {
                     if (!conn.checkDataReceived()) {
                        if (now >= lastCheck + ttl) {
                           toRemove.add(new Pair<>(conn.getID(), ttl));

                           flush = false;
                        }
                     } else {
                        entry.lastCheck = now;
                     }
                  }

                  if (flush) {
                     flushExecutor.execute(() -> {
                        try {
                           // this is using a different thread
                           // as if anything wrong happens on flush
                           // failure detection could be affected
                           conn.scheduledFlush();
                        } catch (Throwable e) {
                           ActiveMQServerLogger.LOGGER.failedToFlushOutstandingDataFromTheConnection(e);
                        }

                     });
                  }
               }

               for (final Pair<Object, Long> pair : toRemove) {
                  final RemotingConnection conn = getConnection(pair.getA());
                  if (conn != null) {
                     // In certain cases (replicationManager for instance) calling fail could take some time
                     // We can't pause the FailureCheckAndFlushThread as that would lead other clients to fail for
                     // missing pings
                     flushExecutor.execute(() -> conn.fail(ActiveMQMessageBundle.BUNDLE.clientExited(conn.getRemoteAddress(), pair.getB())));
                     removeConnection(pair.getA());
                  }
               }

               if (latch.await(pauseInterval, TimeUnit.MILLISECONDS))
                  return;
            } catch (Throwable e) {
               ActiveMQServerLogger.LOGGER.errorOnFailureCheck(e);
            }
         }
      }
   }

   protected void updateProtocols() {
      for (Acceptor acceptor : this.acceptors.values()) {
         acceptor.updateInterceptors(incomingInterceptors, outgoingInterceptors);
      }
   }

   /**
    * Locates protocols from the internal default map and moves them into the input protocol map.
    */
   private void locateProtocols(String protocolList,
                                Object transportConfig,
                                Map<String, ProtocolManagerFactory> protocolMap) {
      String[] protocolsSplit = protocolList.split(",");

      for (String protocolItem : protocolsSplit) {
         ProtocolManagerFactory protocolManagerFactory = this.protocolMap.get(protocolItem);

         if (protocolManagerFactory == null) {
            ActiveMQServerLogger.LOGGER.noProtocolManagerFound(protocolItem, transportConfig.toString());
         } else {
            protocolMap.put(protocolItem, protocolManagerFactory);
         }
      }
   }

   /**
    * Finds protocol support from a given classloader.
    */
   private void resolveProtocols(ClassLoader loader) {
      ServiceLoader<ProtocolManagerFactory> serviceLoader = ServiceLoader.load(ProtocolManagerFactory.class, loader);
      loadProtocolManagerFactories(serviceLoader);
   }

   /**
    * Loads the protocols found into a map.
    */
   private void loadProtocolManagerFactories(Iterable<ProtocolManagerFactory> protocolManagerFactoryCollection) {
      for (ProtocolManagerFactory next : protocolManagerFactoryCollection) {
         MessagePersister.registerProtocol(next);
         String[] protocols = next.getProtocols();
         for (String protocol : protocols) {
            ActiveMQServerLogger.LOGGER.addingProtocolSupport(next.getModuleName(), protocol);
            protocolMap.put(protocol, next);
         }
      }
   }

   private void removeAcceptorStoreReloadCallback(String acceptorName, URL storeURL, String storeType) {
      if (storeURL != null) {
         final ReloadManager reloadManager = server.getReloadManager();

         reloadManager.removeCallbacks(storeURL);

         if (PemConfigUtil.isPemConfigStoreType(storeType)) {
            String[] sources = null;

            try (InputStream pemConfigStream = storeURL.openStream()) {
               sources = PemConfigUtil.parseSources(pemConfigStream);
            } catch (IOException e) {
               ActiveMQServerLogger.LOGGER.skipSSLAutoReloadForSourcesOfStore(storeURL.getPath(), e.toString());
            }

            if (sources != null) {
               for (String source : sources) {
                  URL sourceURL = fileUrlFrom(source);
                  if (sourceURL != null) {
                     reloadManager.removeCallbacks(sourceURL);
                  }
               }
            }
         }
      }
   }

   private void addAcceptorStoreReloadCallback(String acceptorName, URL storeURL, String storeType) {
      if (storeURL != null) {
         server.getReloadManager().addCallback(storeURL, (uri) -> {
            // preference for Control to capture consistent audit logging
            if (managementService != null) {
               AcceptorControl targetControl = managementService.getAcceptorControl(ResourceNames.ACCEPTOR + acceptorName);
               if (targetControl != null) {
                  targetControl.reload();
               }
            }
         });

         if (PemConfigUtil.isPemConfigStoreType(storeType)) {
            String[] sources = null;

            try (InputStream pemConfigStream = storeURL.openStream()) {
               sources = PemConfigUtil.parseSources(pemConfigStream);
            } catch (IOException e) {
               ActiveMQServerLogger.LOGGER.skipSSLAutoReloadForSourcesOfStore(storeURL.getPath(), e.toString());
            }

            if (sources != null) {
               for (String source : sources) {
                  URL sourceURL = fileUrlFrom(source);
                  if (sourceURL != null) {
                     addAcceptorStoreReloadCallback(acceptorName, sourceURL, null);
                  }
               }
            }
         }
      }
   }

   private static URL fileUrlFrom(Object o) {
      if (o instanceof String string) {
         try {
            return new File(string).toURI().toURL();
         } catch (MalformedURLException ignored) {
         }
      }

      return null;
   }

   private static String storeTypeFrom(Object o) {
      if (o instanceof String string) {
         return string;
      }

      return null;
   }
}
