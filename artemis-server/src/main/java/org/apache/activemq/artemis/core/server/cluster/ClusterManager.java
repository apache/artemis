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
package org.apache.activemq.artemis.core.server.cluster;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.core.server.cluster.ha.HAManager;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.BroadcastGroupImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.qourum.QuorumManager;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.jboss.logging.Logger;

/**
 * A ClusterManager manages {@link ClusterConnection}s, {@link BroadcastGroup}s and {@link Bridge}s.
 * <p>
 * Note that {@link org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionBridge}s extend Bridges but are controlled over through
 * {@link ClusterConnectionImpl}. As a node is discovered a new {@link org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionBridge} is
 * deployed.
 */
public class ClusterManager implements ActiveMQComponent {

   private static final Logger logger = Logger.getLogger(ClusterManager.class);

   private ClusterController clusterController;

   private HAManager haManager;

   private final Map<String, BroadcastGroup> broadcastGroups = new HashMap<>();

   private final Map<String, Bridge> bridges = new HashMap<>();

   private final ExecutorFactory executorFactory;

   private final ActiveMQServer server;

   private final PostOffice postOffice;

   private final ScheduledExecutorService scheduledExecutor;

   private ClusterConnection defaultClusterConnection;

   private final ManagementService managementService;

   private final Configuration configuration;

   private Set<String> protocolIgnoredAddresses = new HashSet<>();

   public QuorumManager getQuorumManager() {
      return clusterController.getQuorumManager();
   }

   public ClusterController getClusterController() {
      return clusterController;
   }

   public HAManager getHAManager() {
      return haManager;
   }


   public void addClusterChannelHandler(Channel channel,
                                        Acceptor acceptorUsed,
                                        CoreRemotingConnection remotingConnection,
                                        Activation activation) {
      clusterController.addClusterChannelHandler(channel, acceptorUsed, remotingConnection, activation);
   }

   enum State {
      STOPPED,
      /**
       * Used because {@link ClusterManager#stop()} method is not completely synchronized
       */
      STOPPING,
      /**
       * Deployed means {@link ClusterManager#deploy()} was called but
       * {@link ClusterManager#start()} was not called.
       * <p>
       * We need the distinction if {@link ClusterManager#stop()} is called before 'start'. As
       * otherwise we would leak locators.
       */
      DEPLOYED, STARTED,
   }

   private volatile State state = State.STOPPED;

   // the cluster connections which links this node to other cluster nodes
   private final Map<String, ClusterConnection> clusterConnections = new HashMap<>();

   private final Set<ServerLocatorInternal> clusterLocators = new ConcurrentHashSet<>();

   private final Executor executor;

   private final NodeManager nodeManager;

   public ClusterManager(final ExecutorFactory executorFactory,
                         final ActiveMQServer server,
                         final PostOffice postOffice,
                         final ScheduledExecutorService scheduledExecutor,
                         final ManagementService managementService,
                         final Configuration configuration,
                         final NodeManager nodeManager,
                         final boolean useQuorumManager) {
      this.executorFactory = executorFactory;

      executor = executorFactory.getExecutor();

      this.server = server;

      this.postOffice = postOffice;

      this.scheduledExecutor = scheduledExecutor;

      this.managementService = managementService;

      this.configuration = configuration;

      this.nodeManager = nodeManager;

      clusterController = new ClusterController(server, scheduledExecutor, useQuorumManager);

      haManager = server.getActivation().getHAManager();
   }

   public String describe() {
      StringWriter str = new StringWriter();
      PrintWriter out = new PrintWriter(str);

      out.println("Information on " + this);
      out.println("*******************************************************");

      for (ClusterConnection conn : cloneClusterConnections()) {
         out.println(conn.describe());
      }

      out.println("*******************************************************");

      return str.toString();
   }

   /**
    * Return the default ClusterConnection to be used case it's not defined by the acceptor
    *
    * @return default connection
    */
   public ClusterConnection getDefaultConnection(TransportConfiguration acceptorConfig) {
      if (acceptorConfig == null) {
         // if the parameter is null, we just return whatever is defined on defaultClusterConnection
         return defaultClusterConnection;
      } else if (defaultClusterConnection != null && defaultClusterConnection.getConnector().isEquivalent(acceptorConfig)) {
         return defaultClusterConnection;
      } else {
         for (ClusterConnection conn : cloneClusterConnections()) {
            if (conn.getConnector().isEquivalent(acceptorConfig)) {
               return conn;
            }
         }
         return null;
      }
   }

   @Override
   public String toString() {
      return "ClusterManagerImpl[server=" + server + "]@" + System.identityHashCode(this);
   }

   public String getNodeId() {
      return nodeManager.getNodeId().toString();
   }

   public String getBackupGroupName() {
      return server.getHAPolicy().getBackupGroupName();
   }

   public String getScaleDownGroupName() {
      return server.getHAPolicy().getScaleDownGroupName();
   }

   public synchronized void deploy() throws Exception {
      if (state == State.STOPPED) {
         state = State.DEPLOYED;
      } else {
         throw new IllegalStateException();
      }

      for (BroadcastGroupConfiguration config : configuration.getBroadcastGroupConfigurations()) {
         deployBroadcastGroup(config);
      }

      for (ClusterConnectionConfiguration config : configuration.getClusterConfigurations()) {
         deployClusterConnection(config);
      }

      /*
      * only start if we are actually in a cluster
      * */
      clusterController.start();
   }

   @Override
   public synchronized void start() throws Exception {
      if (state == State.STARTED) {
         return;
      }

      for (BroadcastGroup group : broadcastGroups.values()) {
         try {
            group.start();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToStartBroadcastGroup(e, group.getName());
         }
      }

      for (ClusterConnection conn : clusterConnections.values()) {
         try {
            conn.start();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToStartClusterConnection(e, conn.getName());
         }
      }

      deployConfiguredBridges();

      for (Bridge bridge : bridges.values()) {
         try {
            bridge.start();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToStartBridge(e, bridge.getName());
         }
      }

      //now start the ha manager
      haManager.start();

      state = State.STARTED;
   }

   private void deployConfiguredBridges() throws Exception {
      for (BridgeConfiguration config : configuration.getBridgeConfigurations()) {
         deployBridge(config);
      }
   }

   @Override
   public void stop() throws Exception {
      haManager.stop();
      synchronized (this) {
         if (state == State.STOPPED || state == State.STOPPING) {
            return;
         }
         state = State.STOPPING;

         clusterController.stop();

         for (BroadcastGroup group : broadcastGroups.values()) {
            group.stop();
            managementService.unregisterBroadcastGroup(group.getName());
         }

         broadcastGroups.clear();

         for (ClusterConnection clusterConnection : clusterConnections.values()) {
            clusterConnection.stop();
            managementService.unregisterCluster(clusterConnection.getName().toString());
         }

         for (Bridge bridge : bridges.values()) {
            bridge.stop();
            managementService.unregisterBridge(bridge.getName().toString());
         }

         bridges.clear();
      }

      for (ServerLocatorInternal clusterLocator : clusterLocators) {
         try {
            clusterLocator.close();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorClosingServerLocator(e, clusterLocator);
         }
      }
      clusterLocators.clear();
      state = State.STOPPED;

      clearClusterConnections();

      protocolIgnoredAddresses.clear();
   }

   public void flushExecutor() {
      FutureLatch future = new FutureLatch();
      executor.execute(future);
      if (!future.await(10000)) {
         ActiveMQServerLogger.LOGGER.couldNotFlushClusterManager(this.toString());
         server.threadDump();
      }
   }

   @Override
   public boolean isStarted() {
      return state == State.STARTED;
   }

   public Map<String, Bridge> getBridges() {
      return new HashMap<>(bridges);
   }

   public Set<ClusterConnection> getClusterConnections() {
      return new HashSet<>(clusterConnections.values());
   }

   public Set<BroadcastGroup> getBroadcastGroups() {
      return new HashSet<>(broadcastGroups.values());
   }

   public ClusterConnection getClusterConnection(final String name) {
      return clusterConnections.get(name);
   }

   public void removeClusterLocator(final ServerLocatorInternal serverLocator) {
      this.clusterLocators.remove(serverLocator);
   }

   public synchronized void deployBridge(final BridgeConfiguration config) throws Exception {
      if (config.getName() == null) {
         ActiveMQServerLogger.LOGGER.bridgeNotUnique();

         return;
      }

      if (config.getQueueName() == null) {
         ActiveMQServerLogger.LOGGER.bridgeNoQueue(config.getName());

         return;
      }

      if (config.getForwardingAddress() == null) {
         ActiveMQServerLogger.LOGGER.bridgeNoForwardAddress(config.getName());
      }

      if (bridges.containsKey(config.getName())) {
         ActiveMQServerLogger.LOGGER.bridgeAlreadyDeployed(config.getName());

         return;
      }

      Transformer transformer = server.getServiceRegistry().getBridgeTransformer(config.getName(), config.getTransformerConfiguration());

      Binding binding = postOffice.getBinding(new SimpleString(config.getQueueName()));

      if (binding == null) {
         ActiveMQServerLogger.LOGGER.bridgeQueueNotFound(config.getQueueName(), config.getName());

         return;
      }

      if (server.hasBrokerBridgePlugins()) {
         server.callBrokerBridgePlugins(plugin -> plugin.beforeDeployBridge(config));
      }

      Queue queue = (Queue) binding.getBindable();

      ServerLocatorInternal serverLocator;

      if (config.getDiscoveryGroupName() != null) {
         DiscoveryGroupConfiguration discoveryGroupConfiguration = configuration.getDiscoveryGroupConfigurations().get(config.getDiscoveryGroupName());
         if (discoveryGroupConfiguration == null) {
            ActiveMQServerLogger.LOGGER.bridgeNoDiscoveryGroup(config.getDiscoveryGroupName());

            return;
         }

         if (config.isHA()) {
            serverLocator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(discoveryGroupConfiguration);
         } else {
            serverLocator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithoutHA(discoveryGroupConfiguration);
         }

      } else {
         TransportConfiguration[] tcConfigs = configuration.getTransportConfigurations(config.getStaticConnectors());

         if (tcConfigs == null) {
            ActiveMQServerLogger.LOGGER.bridgeCantFindConnectors(config.getName());
            return;
         }

         if (config.isHA()) {
            serverLocator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(tcConfigs);
         } else {
            serverLocator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithoutHA(tcConfigs);
         }

      }

      serverLocator.setIdentity("Bridge " + config.getName());
      serverLocator.setConfirmationWindowSize(config.getConfirmationWindowSize());

      // We are going to manually retry on the bridge in case of failure
      serverLocator.setReconnectAttempts(0);
      serverLocator.setInitialConnectAttempts(0);
      serverLocator.setRetryInterval(config.getRetryInterval());
      serverLocator.setMaxRetryInterval(config.getMaxRetryInterval());
      serverLocator.setRetryIntervalMultiplier(config.getRetryIntervalMultiplier());
      serverLocator.setClientFailureCheckPeriod(config.getClientFailureCheckPeriod());
      serverLocator.setConnectionTTL(config.getConnectionTTL());
      serverLocator.setBlockOnDurableSend(!config.isUseDuplicateDetection());
      serverLocator.setBlockOnNonDurableSend(!config.isUseDuplicateDetection());
      serverLocator.setMinLargeMessageSize(config.getMinLargeMessageSize());
      serverLocator.setProducerWindowSize(config.getProducerWindowSize());

      // This will be set to 30s unless it's changed from embedded / testing
      // there is no reason to exception the config for this timeout
      // since the Bridge is supposed to be non-blocking and fast
      // We may expose this if we find a good use case
      serverLocator.setCallTimeout(config.getCallTimeout());

      serverLocator.addIncomingInterceptor(new IncomingInterceptorLookingForExceptionMessage(this, executor));

      if (!config.isUseDuplicateDetection()) {
         logger.debug("Bridge " + config.getName() +
                         " is configured to not use duplicate detecion, it will send messages synchronously");
      }

      clusterLocators.add(serverLocator);

      for (int i = 0; i < config.getConcurrency(); i++) {
         String name = config.getConcurrency() > 1 ? (config.getName() + "-" + i) : config.getName();
         Bridge bridge = new BridgeImpl(serverLocator, config.getInitialConnectAttempts(), config.getReconnectAttempts(),
               config.getReconnectAttemptsOnSameNode(), config.getRetryInterval(), config.getRetryIntervalMultiplier(),
               config.getMaxRetryInterval(), nodeManager.getUUID(), new SimpleString(name), queue,
               executorFactory.getExecutor(), FilterImpl.createFilter(config.getFilterString()),
               SimpleString.toSimpleString(config.getForwardingAddress()), scheduledExecutor, transformer,
               config.isUseDuplicateDetection(), config.getUser(), config.getPassword(), server,
               config.getRoutingType());
         bridges.put(name, bridge);
         managementService.registerBridge(bridge, config);
         bridge.start();

         if (server.hasBrokerBridgePlugins()) {
            server.callBrokerBridgePlugins(plugin -> plugin.afterDeployBridge(bridge));
         }
      }
   }

   public static class IncomingInterceptorLookingForExceptionMessage implements Interceptor {

      private final ClusterManager manager;
      private final Executor executor;

      /**
       * @param manager
       * @param executor
       */
      public IncomingInterceptorLookingForExceptionMessage(ClusterManager manager, Executor executor) {
         this.manager = manager;
         this.executor = executor;
      }

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.EXCEPTION) {
            ActiveMQExceptionMessage msg = (ActiveMQExceptionMessage) packet;
            final ActiveMQException exception = msg.getException();
            if (exception.getType() == ActiveMQExceptionType.CLUSTER_SECURITY_EXCEPTION) {
               ActiveMQServerLogger.LOGGER.clusterManagerAuthenticationError(exception.getMessage());
               executor.execute(new Runnable() {
                  @Override
                  public void run() {
                     try {
                        manager.stop();
                     } catch (Exception e) {
                        ActiveMQServerLogger.LOGGER.failedToStopClusterManager(e);
                     }
                  }

               });
            }
         }
         return true;
      }
   }

   public void destroyBridge(final String name) throws Exception {
      Bridge bridge;

      synchronized (this) {
         bridge = bridges.remove(name);
         if (bridge != null) {
            bridge.stop();
            managementService.unregisterBridge(name);
         }
      }
      if (bridge != null) {
         bridge.flushExecutor();
      }
   }

   // for testing
   public void clear() {
      for (Bridge bridge : bridges.values()) {
         try {
            bridge.stop();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.warn(e.getMessage(), e);
         }
      }
      bridges.clear();
      for (ClusterConnection clusterConnection : clusterConnections.values()) {
         try {
            clusterConnection.stop();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToStopClusterConnection(e);
         }
      }
      clearClusterConnections();
   }

   public void informClusterOfBackup(String name) {
      ClusterConnection clusterConnection = clusterConnections.get(name);
      if (clusterConnection != null) {
         clusterConnection.informClusterOfBackup();
      }
   }

   public ClusterManager addProtocolIgnoredAddress(String ignoredAddress) {
      protocolIgnoredAddresses.add(ignoredAddress);
      return this;
   }

   public Collection<String> getProtocolIgnoredAddresses() {
      return protocolIgnoredAddresses;
   }

   // Private methods ----------------------------------------------------------------------------------------------------

   private void clearClusterConnections() {
      clusterConnections.clear();
      this.defaultClusterConnection = null;
   }

   private void deployClusterConnection(final ClusterConnectionConfiguration config) throws Exception {

      if (!config.validateConfiguration()) {
         return;
      }

      TransportConfiguration connector = config.getTransportConfiguration(configuration);

      if (connector == null) {
         return;
      }

      if (clusterConnections.containsKey(config.getName())) {
         ActiveMQServerLogger.LOGGER.clusterConnectionAlreadyExists(config.getConnectorName());
         return;
      }

      ClusterConnectionImpl clusterConnection;

      if (config.getDiscoveryGroupName() != null) {
         DiscoveryGroupConfiguration dg = config.getDiscoveryGroupConfiguration(configuration);

         if (dg == null)
            return;

         if (logger.isDebugEnabled()) {
            logger.debug(this + " Starting a Discovery Group Cluster Connection, name=" +
                            config.getDiscoveryGroupName() +
                            ", dg=" +
                            dg);
         }

         clusterConnection = new ClusterConnectionImpl(this, dg, connector, new SimpleString(config.getName()), new SimpleString(config.getAddress() != null ? config.getAddress() : ""), config.getMinLargeMessageSize(), config.getClientFailureCheckPeriod(), config.getConnectionTTL(), config.getRetryInterval(), config.getRetryIntervalMultiplier(), config.getMaxRetryInterval(), config.getInitialConnectAttempts(), config.getReconnectAttempts(), config.getCallTimeout(), config.getCallFailoverTimeout(), config.isDuplicateDetection(), config.getMessageLoadBalancingType(), config.getConfirmationWindowSize(), config.getProducerWindowSize(), executorFactory, server, postOffice, managementService, scheduledExecutor, config.getMaxHops(), nodeManager, server.getConfiguration().getClusterUser(), server.getConfiguration().getClusterPassword(), config.isAllowDirectConnectionsOnly(), config.getClusterNotificationInterval(), config.getClusterNotificationAttempts());

         clusterController.addClusterConnection(clusterConnection.getName(), dg, config, connector);
      } else {
         TransportConfiguration[] tcConfigs = config.getTransportConfigurations(configuration);

         if (logger.isDebugEnabled()) {
            logger.debug(this + " defining cluster connection towards " + Arrays.toString(tcConfigs));
         }

         clusterConnection = new ClusterConnectionImpl(this, tcConfigs, connector, new SimpleString(config.getName()), new SimpleString(config.getAddress()), config.getMinLargeMessageSize(), config.getClientFailureCheckPeriod(), config.getConnectionTTL(), config.getRetryInterval(), config.getRetryIntervalMultiplier(), config.getMaxRetryInterval(), config.getInitialConnectAttempts(), config.getReconnectAttempts(), config.getCallTimeout(), config.getCallFailoverTimeout(), config.isDuplicateDetection(), config.getMessageLoadBalancingType(), config.getConfirmationWindowSize(), config.getProducerWindowSize(), executorFactory, server, postOffice, managementService, scheduledExecutor, config.getMaxHops(), nodeManager, server.getConfiguration().getClusterUser(), server.getConfiguration().getClusterPassword(), config.isAllowDirectConnectionsOnly(), config.getClusterNotificationInterval(), config.getClusterNotificationAttempts());

         clusterController.addClusterConnection(clusterConnection.getName(), tcConfigs, config, connector);
      }

      if (defaultClusterConnection == null) {
         defaultClusterConnection = clusterConnection;
         clusterController.setDefaultClusterConnectionName(defaultClusterConnection.getName());
      }

      managementService.registerCluster(clusterConnection, config);

      clusterConnections.put(config.getName(), clusterConnection);

      if (logger.isTraceEnabled()) {
         logger.trace("ClusterConnection.start at " + clusterConnection, new Exception("trace"));
      }
   }

   private synchronized void deployBroadcastGroup(final BroadcastGroupConfiguration config) throws Exception {
      if (broadcastGroups.containsKey(config.getName())) {
         ActiveMQServerLogger.LOGGER.broadcastGroupAlreadyExists(config.getName());

         return;
      }

      BroadcastGroup group = createBroadcastGroup(config);

      managementService.registerBroadcastGroup(group, config);
   }

   private BroadcastGroup createBroadcastGroup(BroadcastGroupConfiguration config) throws Exception {
      BroadcastGroup group = broadcastGroups.get(config.getName());

      if (group == null) {
         group = new BroadcastGroupImpl(nodeManager, config.getName(), config.getBroadcastPeriod(), scheduledExecutor, config.getEndpointFactory());

         for (String connectorInfo : config.getConnectorInfos()) {
            TransportConfiguration connector = configuration.getConnectorConfigurations().get(connectorInfo);

            if (connector == null) {
               logWarnNoConnector(connectorInfo, config.getName());

               return null;
            }

            group.addConnector(connector);
         }
      }

      if (group.size() == 0) {
         logWarnNoConnector(config.getConnectorInfos().toString(), group.getName());
         return null;
      }

      broadcastGroups.put(config.getName(), group);

      return group;
   }

   private void logWarnNoConnector(final String connectorName, final String bgName) {
      ActiveMQServerLogger.LOGGER.broadcastGroupNoConnector(connectorName, bgName);
   }

   private synchronized Collection<ClusterConnection> cloneClusterConnections() {
      ArrayList<ClusterConnection> list = new ArrayList<>(clusterConnections.values());
      return list;
   }

}
