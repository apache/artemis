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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.client.impl.AfterConnectInternalListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyManager;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager.IncomingInterceptorLookingForExceptionMessage;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.group.impl.Proposal;
import org.apache.activemq.artemis.core.server.group.impl.Response;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.jboss.logging.Logger;

public final class ClusterConnectionImpl implements ClusterConnection, AfterConnectInternalListener, TopologyManager {

   private static final Logger logger = Logger.getLogger(ClusterConnectionImpl.class);

   private static final String SN_PREFIX = "sf.";
   /**
    * When getting member on node-up and down we have to remove the name from the transport config
    * as the setting we build here doesn't need to consider the name, so use the same name on all
    * the instances.
    */
   private static final String TRANSPORT_CONFIG_NAME = "topology-member";

   private final ExecutorFactory executorFactory;

   private final Executor executor;

   private final ActiveMQServer server;

   private final PostOffice postOffice;

   private final ManagementService managementService;

   private final SimpleString name;

   private final SimpleString address;

   private final long clientFailureCheckPeriod;

   private final long connectionTTL;

   private final long retryInterval;

   private final long callTimeout;

   private final long callFailoverTimeout;

   private final double retryIntervalMultiplier;

   private final long maxRetryInterval;

   private final int initialConnectAttempts;

   private final int reconnectAttempts;

   private final boolean useDuplicateDetection;

   private final MessageLoadBalancingType messageLoadBalancingType;

   private final int confirmationWindowSize;

   private final int producerWindowSize;

   /**
    * Guard for the field {@link #records}. Note that the field is {@link ConcurrentHashMap},
    * however we need the guard to synchronize multiple step operations during topology updates.
    */
   private final Object recordsGuard = new Object();

   private final Map<String, MessageFlowRecord> records = new ConcurrentHashMap<>();

   private final ScheduledExecutorService scheduledExecutor;

   private final int maxHops;

   private final NodeManager nodeManager;

   private volatile boolean started;

   private final String clusterUser;

   private final String clusterPassword;

   private final ClusterConnector clusterConnector;

   private ServerLocatorInternal serverLocator;

   private final TransportConfiguration connector;

   private final boolean allowDirectConnectionsOnly;

   //default access modifier is used because of test, see {@link org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImplMockTest}
   final Set<TransportConfiguration> allowableConnections = new HashSet<>();

   private final ClusterManager manager;

   private final int minLargeMessageSize;

   // Stuff that used to be on the ClusterManager

   private final Topology topology;

   private volatile boolean stopping = false;

   private LiveNotifier liveNotifier = null;

   private final long clusterNotificationInterval;

   private final int clusterNotificationAttempts;

   private final String storeAndForwardPrefix;

   private boolean splitBrainDetection;


   /** For tests only */
   public ServerLocatorInternal getServerLocator() {
      return serverLocator;
   }


   public ClusterConnectionImpl(final ClusterManager manager,
                                final TransportConfiguration[] staticTranspConfigs,
                                final TransportConfiguration connector,
                                final SimpleString name,
                                final SimpleString address,
                                final int minLargeMessageSize,
                                final long clientFailureCheckPeriod,
                                final long connectionTTL,
                                final long retryInterval,
                                final double retryIntervalMultiplier,
                                final long maxRetryInterval,
                                final int initialConnectAttempts,
                                final int reconnectAttempts,
                                final long callTimeout,
                                final long callFailoverTimeout,
                                final boolean useDuplicateDetection,
                                final MessageLoadBalancingType messageLoadBalancingType,
                                final int confirmationWindowSize,
                                final int producerWindowSize,
                                final ExecutorFactory executorFactory,
                                final ActiveMQServer server,
                                final PostOffice postOffice,
                                final ManagementService managementService,
                                final ScheduledExecutorService scheduledExecutor,
                                final int maxHops,
                                final NodeManager nodeManager,
                                final String clusterUser,
                                final String clusterPassword,
                                final boolean allowDirectConnectionsOnly,
                                final long clusterNotificationInterval,
                                final int clusterNotificationAttempts) throws Exception {
      this.nodeManager = nodeManager;

      this.connector = connector;

      this.name = name;

      this.address = address;

      this.clientFailureCheckPeriod = clientFailureCheckPeriod;

      this.connectionTTL = connectionTTL;

      this.retryInterval = retryInterval;

      this.retryIntervalMultiplier = retryIntervalMultiplier;

      this.maxRetryInterval = maxRetryInterval;

      this.initialConnectAttempts = initialConnectAttempts;

      this.reconnectAttempts = reconnectAttempts;

      this.useDuplicateDetection = useDuplicateDetection;

      this.messageLoadBalancingType = messageLoadBalancingType;

      this.confirmationWindowSize = confirmationWindowSize;

      this.producerWindowSize = producerWindowSize;

      this.executorFactory = executorFactory;

      this.clusterNotificationInterval = clusterNotificationInterval;

      this.clusterNotificationAttempts = clusterNotificationAttempts;

      this.executor = executorFactory.getExecutor();

      this.topology = new Topology(this, executor);

      this.server = server;

      this.postOffice = postOffice;

      this.managementService = managementService;

      this.scheduledExecutor = scheduledExecutor;

      this.maxHops = maxHops;

      this.clusterUser = clusterUser;

      this.clusterPassword = clusterPassword;

      this.allowDirectConnectionsOnly = allowDirectConnectionsOnly;

      this.manager = manager;

      this.callTimeout = callTimeout;

      this.callFailoverTimeout = callFailoverTimeout;

      this.minLargeMessageSize = minLargeMessageSize;

      clusterConnector = new StaticClusterConnector(staticTranspConfigs);

      if (staticTranspConfigs != null && staticTranspConfigs.length > 0) {
         // a cluster connection will connect to other nodes only if they are directly connected
         // through a static list of connectors or broadcasting using UDP.
         if (allowDirectConnectionsOnly) {
            for (TransportConfiguration configuration : staticTranspConfigs) {
               TransportConfiguration transportConfiguration = configuration.newTransportConfig(TRANSPORT_CONFIG_NAME);
               //localAddress and localPort have  to be removed because of comparison with in {@link org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl#nodeUP()} (issue https://issues.apache.org/jira/browse/ARTEMIS-1946)
               //(opposing TransportConnection can not have local address of different node)
               transportConfiguration.getParams().remove(TransportConstants.LOCAL_ADDRESS_PROP_NAME);
               transportConfiguration.getParams().remove(TransportConstants.LOCAL_PORT_PROP_NAME);
               allowableConnections.add(transportConfiguration);
            }
         }
      }

      this.storeAndForwardPrefix = server.getInternalNamingPrefix() + SN_PREFIX;
   }

   public ClusterConnectionImpl(final ClusterManager manager,
                                DiscoveryGroupConfiguration dg,
                                final TransportConfiguration connector,
                                final SimpleString name,
                                final SimpleString address,
                                final int minLargeMessageSize,
                                final long clientFailureCheckPeriod,
                                final long connectionTTL,
                                final long retryInterval,
                                final double retryIntervalMultiplier,
                                final long maxRetryInterval,
                                final int initialConnectAttempts,
                                final int reconnectAttempts,
                                final long callTimeout,
                                final long callFailoverTimeout,
                                final boolean useDuplicateDetection,
                                final MessageLoadBalancingType messageLoadBalancingType,
                                final int confirmationWindowSize,
                                final int producerWindowSize,
                                final ExecutorFactory executorFactory,
                                final ActiveMQServer server,
                                final PostOffice postOffice,
                                final ManagementService managementService,
                                final ScheduledExecutorService scheduledExecutor,
                                final int maxHops,
                                final NodeManager nodeManager,
                                final String clusterUser,
                                final String clusterPassword,
                                final boolean allowDirectConnectionsOnly,
                                final long clusterNotificationInterval,
                                final int clusterNotificationAttempts) throws Exception {
      this.nodeManager = nodeManager;

      this.connector = connector;

      this.name = name;

      this.address = address;

      this.clientFailureCheckPeriod = clientFailureCheckPeriod;

      this.connectionTTL = connectionTTL;

      this.retryInterval = retryInterval;

      this.retryIntervalMultiplier = retryIntervalMultiplier;

      this.maxRetryInterval = maxRetryInterval;

      this.minLargeMessageSize = minLargeMessageSize;

      this.initialConnectAttempts = initialConnectAttempts;

      this.reconnectAttempts = reconnectAttempts;

      this.callTimeout = callTimeout;

      this.callFailoverTimeout = callFailoverTimeout;

      this.useDuplicateDetection = useDuplicateDetection;

      this.messageLoadBalancingType = messageLoadBalancingType;

      this.confirmationWindowSize = confirmationWindowSize;

      this.producerWindowSize = producerWindowSize;

      this.executorFactory = executorFactory;

      this.clusterNotificationInterval = clusterNotificationInterval;

      this.clusterNotificationAttempts = clusterNotificationAttempts;

      this.executor = executorFactory.getExecutor();

      this.topology = new Topology(this, executor);

      this.server = server;

      this.postOffice = postOffice;

      this.managementService = managementService;

      this.scheduledExecutor = scheduledExecutor;

      this.maxHops = maxHops;

      this.clusterUser = clusterUser;

      this.clusterPassword = clusterPassword;

      this.allowDirectConnectionsOnly = allowDirectConnectionsOnly;

      clusterConnector = new DiscoveryClusterConnector(dg);

      this.manager = manager;

      this.storeAndForwardPrefix = server.getInternalNamingPrefix() + SN_PREFIX;
   }

   @Override
   public void start() throws Exception {
      synchronized (this) {
         if (started) {
            return;
         }

         stopping = false;
         started = true;
         activate();
      }

   }

   @Override
   public void flushExecutor() {
      FutureLatch future = new FutureLatch();
      executor.execute(future);
      if (!future.await(10000)) {
         ActiveMQServerLogger.LOGGER.couldNotFinishExecutor(this.toString());
         server.threadDump();
      }
   }

   @Override
   public void stop() throws Exception {
      if (!started) {
         return;
      }
      stopping = true;
      if (logger.isDebugEnabled()) {
         logger.debug(this + "::stopping ClusterConnection");
      }

      if (serverLocator != null) {
         serverLocator.removeClusterTopologyListener(this);
      }

      logger.debug("Cluster connection being stopped for node" + nodeManager.getNodeId() +
                      ", server = " +
                      this.server +
                      " serverLocator = " +
                      serverLocator);

      synchronized (this) {
         for (MessageFlowRecord record : records.values()) {
            try {
               record.close();
            } catch (Exception ignore) {
            }
         }
         records.clear();
      }

      if (managementService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), name);
         //nodeID can be null if there's only a backup
         SimpleString nodeId = nodeManager.getNodeId();
         Notification notification = new Notification(nodeId == null ? null : nodeId.toString(), CoreNotificationType.CLUSTER_CONNECTION_STOPPED, props);
         managementService.sendNotification(notification);
      }
      executor.execute(new Runnable() {
         @Override
         public void run() {
            synchronized (ClusterConnectionImpl.this) {
               closeLocator(serverLocator);
               serverLocator = null;
            }

         }
      });

      started = false;
   }

   /**
    * @param locator
    */
   private void closeLocator(final ServerLocatorInternal locator) {
      if (locator != null)
         locator.close();
   }

   private TopologyMember getLocalMember() {
      return topology.getMember(manager.getNodeId());
   }

   @Override
   public void addClusterTopologyListener(final ClusterTopologyListener listener) {
      topology.addClusterTopologyListener(listener);
   }

   @Override
   public void removeClusterTopologyListener(final ClusterTopologyListener listener) {
      topology.removeClusterTopologyListener(listener);
   }

   @Override
   public Topology getTopology() {
      return topology;
   }

   @Override
   public void nodeAnnounced(final long uniqueEventID,
                             final String nodeID,
                             final String backupGroupName,
                             final String scaleDownGroupName,
                             final Pair<TransportConfiguration, TransportConfiguration> connectorPair,
                             final boolean backup) {
      if (logger.isDebugEnabled()) {
         logger.debug(this + "::NodeAnnounced, backup=" + backup + nodeID + connectorPair);
      }

      TransportConfiguration live = connectorPair.getA();
      TransportConfiguration backupTC = connectorPair.getB();
      TopologyMemberImpl newMember = new TopologyMemberImpl(nodeID, backupGroupName, scaleDownGroupName, live, backupTC);
      newMember.setUniqueEventID(uniqueEventID);
      if (backup) {
         topology.updateBackup(new TopologyMemberImpl(nodeID, backupGroupName, scaleDownGroupName, live, backupTC));
      } else {
         topology.updateMember(uniqueEventID, nodeID, newMember);
      }
   }

   /** This is the implementation of TopologyManager. It is used to reject eventual updates from a split brain server.
    *
    * @param uniqueEventID
    * @param nodeId
    * @param memberInput
    * @return
    */
   @Override
   public boolean updateMember(long uniqueEventID, String nodeId, TopologyMemberImpl memberInput) {
      if (splitBrainDetection && nodeId.equals(nodeManager.getNodeId().toString())) {
         if (memberInput.getLive() != null) {
            if (!memberInput.getLive().isSameParams(connector)) {
               ActiveMQServerLogger.LOGGER.possibleSplitBrain(nodeId, memberInput.toString());
               return false;
            }
         } else {
            memberInput.setLive(connector);
         }
      }
      return true;
   }

   /**
    * From topologyManager
    * @param uniqueEventID
    * @param nodeId
    * @return
    */
   @Override
   public boolean removeMember(final long uniqueEventID, final String nodeId) {
      if (splitBrainDetection && nodeId.equals(nodeManager.getNodeId().toString())) {
         ActiveMQServerLogger.LOGGER.possibleSplitBrain(nodeId);
         return false;
      }
      return true;
   }

   @Override
   public void setSplitBrainDetection(boolean splitBrainDetection) {
      this.splitBrainDetection = splitBrainDetection;
   }

   @Override
   public boolean isSplitBrainDetection() {
      return splitBrainDetection;
   }

   @Override
   public void onConnection(ClientSessionFactoryInternal sf) {
      TopologyMember localMember = getLocalMember();
      if (localMember != null) {
         ClusterControl clusterControl = manager.getClusterController().connectToNodeInCluster(sf);
         try {
            clusterControl.authorize();
            clusterControl.sendNodeAnnounce(localMember.getUniqueEventID(), manager.getNodeId(), manager.getBackupGroupName(), manager.getScaleDownGroupName(), false, localMember.getLive(), localMember.getBackup());
         } catch (ActiveMQException e) {
            ActiveMQServerLogger.LOGGER.clusterControlAuthfailure(e.getMessage());
            if (logger.isDebugEnabled()) {
               logger.debug(e);
            }
         }
      } else {
         ActiveMQServerLogger.LOGGER.noLocalMemborOnClusterConnection(this);
      }

      // TODO: shouldn't we send the current time here? and change the current topology?
      // sf.sendNodeAnnounce(System.currentTimeMillis(),
      // manager.getNodeId(),
      // false,
      // localMember.getConnector().a,
      // localMember.getConnector().b);
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public SimpleString getName() {
      return name;
   }

   @Override
   public String getNodeID() {
      return nodeManager == null ? null : (nodeManager.getNodeId() == null ? null : nodeManager.getNodeId().toString());
   }

   @Override
   public ActiveMQServer getServer() {
      return server;
   }

   @Override
   public boolean isNodeActive(String nodeId) {
      MessageFlowRecord rec = records.get(nodeId);
      if (rec == null) {
         return false;
      }
      return rec.getBridge().isConnected();
   }

   @Override
   public long getCallTimeout() {
      return callTimeout;
   }

   @Override
   public Map<String, String> getNodes() {
      synchronized (recordsGuard) {
         Map<String, String> nodes = new HashMap<>();
         for (Entry<String, MessageFlowRecord> entry : records.entrySet()) {
            RemotingConnection fwdConnection = entry.getValue().getBridge().getForwardingConnection();
            if (fwdConnection != null) {
               nodes.put(entry.getKey(), fwdConnection.getRemoteAddress());
            }
         }
         return nodes;
      }
   }

   private synchronized void activate() throws Exception {
      if (!started) {
         return;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Activating cluster connection nodeID=" + nodeManager.getNodeId() + " for server=" + this.server);
      }

      liveNotifier = new LiveNotifier();
      liveNotifier.updateAsLive();
      liveNotifier.schedule();

      serverLocator = clusterConnector.createServerLocator();

      if (serverLocator != null) {

         if (!useDuplicateDetection) {
            logger.debug("DuplicateDetection is disabled, sending clustered messages blocked");
         }

         final TopologyMember currentMember = topology.getMember(manager.getNodeId());

         if (currentMember == null) {
            // sanity check only
            throw new IllegalStateException("InternalError! The ClusterConnection doesn't know about its own node = " + this);
         }

         serverLocator.setNodeID(nodeManager.getNodeId().toString());
         serverLocator.setIdentity("(main-ClusterConnection::" + server.toString() + ")");
         serverLocator.setReconnectAttempts(0);
         serverLocator.setClusterConnection(true);
         serverLocator.setClusterTransportConfiguration(connector);
         serverLocator.setInitialConnectAttempts(-1);
         serverLocator.setClientFailureCheckPeriod(clientFailureCheckPeriod);
         serverLocator.setConnectionTTL(connectionTTL);
         serverLocator.setConfirmationWindowSize(confirmationWindowSize);
         // if not using duplicate detection, we will send blocked
         serverLocator.setBlockOnDurableSend(!useDuplicateDetection);
         serverLocator.setBlockOnNonDurableSend(!useDuplicateDetection);
         serverLocator.setCallTimeout(callTimeout);
         serverLocator.setCallFailoverTimeout(callFailoverTimeout);
         serverLocator.setProducerWindowSize(producerWindowSize);

         if (retryInterval > 0) {
            this.serverLocator.setRetryInterval(retryInterval);
         }

         serverLocator.setAfterConnectionInternalListener(this);

         serverLocator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(serverLocator, server.getStorageManager()));

         serverLocator.start(server.getExecutorFactory().getExecutor());
      }

      if (managementService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), name);
         Notification notification = new Notification(nodeManager.getNodeId().toString(), CoreNotificationType.CLUSTER_CONNECTION_STARTED, props);
         logger.debug("sending notification: " + notification);
         managementService.sendNotification(notification);
      }
      //we add as a listener after we have sent the cluster start notif as the listener may start sending notifs before
      addClusterTopologyListener(this);
   }

   @Override
   public TransportConfiguration getConnector() {
      return connector;
   }

   // ClusterTopologyListener implementation ------------------------------------------------------------------

   @Override
   public void nodeDown(final long eventUID, final String nodeID) {
      /*
      * we dont do anything when a node down is received. The bridges will take care themselves when they should disconnect
      * and/or clear their bindings. This is to avoid closing a record when we don't want to.
      * */
   }

   @Override
   public void nodeUP(final TopologyMember topologyMember, final boolean last) {
      if (stopping) {
         return;
      }
      final String nodeID = topologyMember.getNodeId();
      if (logger.isDebugEnabled()) {
         String ClusterTestBase = "receiving nodeUP for nodeID=";
         logger.debug(this + ClusterTestBase + nodeID + " connectionPair=" + topologyMember);
      }
      // discard notifications about ourselves unless its from our backup

      if (nodeID.equals(nodeManager.getNodeId().toString())) {
         if (logger.isTraceEnabled()) {
            logger.trace(this + "::informing about backup to itself, nodeUUID=" +
                            nodeManager.getNodeId() + ", connectorPair=" + topologyMember + ", this = " + this);
         }
         return;
      }

      // if the node is more than 1 hop away, we do not create a bridge for direct cluster connection
      if (allowDirectConnectionsOnly && !allowableConnections.contains(topologyMember.getLive().newTransportConfig(TRANSPORT_CONFIG_NAME))) {
         return;
      }

      // FIXME required to prevent cluster connections w/o discovery group
      // and empty static connectors to create bridges... ulgy!
      if (serverLocator == null) {
         return;
      }
      /*we don't create bridges to backups*/
      if (topologyMember.getLive() == null) {
         if (logger.isTraceEnabled()) {
            logger.trace(this + " ignoring call with nodeID=" + nodeID + ", topologyMember=" +
                            topologyMember + ", last=" + last);
         }
         return;
      }

      synchronized (recordsGuard) {
         try {
            MessageFlowRecord record = records.get(nodeID);

            if (record == null) {
               if (logger.isDebugEnabled()) {
                  logger.debug(this + "::Creating record for nodeID=" + nodeID + ", topologyMember=" +
                                  topologyMember);
               }

               // New node - create a new flow record

               final SimpleString queueName = getSfQueueName(nodeID);

               Binding queueBinding = postOffice.getBinding(queueName);

               Queue queue;

               if (queueBinding != null) {
                  queue = (Queue) queueBinding.getBindable();
               } else {
                  // Add binding in storage so the queue will get reloaded on startup and we can find it - it's never
                  // actually routed to at that address though
                  queue = server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.MULTICAST).setAutoCreateAddress(true).setMaxConsumers(-1).setPurgeOnNoConsumers(false));
               }

               // There are a few things that will behave differently when it's an internal queue
               // such as we don't hold groupIDs inside the SnF queue
               queue.setInternalQueue(true);

               createNewRecord(topologyMember.getUniqueEventID(), nodeID, topologyMember.getLive(), queueName, queue, true);
            } else {
               if (logger.isTraceEnabled()) {
                  logger.trace(this + " ignored nodeUp record for " + topologyMember + " on nodeID=" +
                                  nodeID + " as the record already existed");
               }
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorUpdatingTopology(e);
         }
      }
   }

   public SimpleString getSfQueueName(String nodeID) {
      return new SimpleString(storeAndForwardPrefix + name + "." + nodeID);
   }

   @Override
   public synchronized void informClusterOfBackup() {
      String nodeID = server.getNodeID().toString();

      TopologyMemberImpl localMember = new TopologyMemberImpl(nodeID, null, null, null, connector);

      topology.updateAsLive(nodeID, localMember);
   }


   @Override
   public ClusterConnectionMetrics getMetrics() {
      long messagesPendingAcknowledgement = 0;
      long messagesAcknowledged = 0;
      for (MessageFlowRecord record : records.values()) {
         final BridgeMetrics metrics = record.getBridge() != null ? record.getBridge().getMetrics() : null;
         messagesPendingAcknowledgement += metrics != null ? metrics.getMessagesPendingAcknowledgement() : 0;
         messagesAcknowledged += metrics != null ? metrics.getMessagesAcknowledged() : 0;
      }

      return new ClusterConnectionMetrics(messagesPendingAcknowledgement, messagesAcknowledged);
   }

   @Override
   public BridgeMetrics getBridgeMetrics(String nodeId) {
      final MessageFlowRecord record = records.get(nodeId);
      return record != null && record.getBridge() != null ? record.getBridge().getMetrics() : null;
   }

   private void createNewRecord(final long eventUID,
                                final String targetNodeID,
                                final TransportConfiguration connector,
                                final SimpleString queueName,
                                final Queue queue,
                                final boolean start) throws Exception {
      String nodeId;

      synchronized (this) {
         if (!started) {
            return;
         }

         if (serverLocator == null) {
            return;
         }

         nodeId = serverLocator.getNodeID();
      }

      final ServerLocatorInternal targetLocator = new ServerLocatorImpl(topology, true, connector);

      targetLocator.setReconnectAttempts(0);

      targetLocator.setInitialConnectAttempts(0);
      targetLocator.setClientFailureCheckPeriod(clientFailureCheckPeriod);
      targetLocator.setConnectionTTL(connectionTTL);

      targetLocator.setConfirmationWindowSize(confirmationWindowSize);
      targetLocator.setBlockOnDurableSend(!useDuplicateDetection);
      targetLocator.setBlockOnNonDurableSend(!useDuplicateDetection);

      targetLocator.setRetryInterval(retryInterval);
      targetLocator.setMaxRetryInterval(maxRetryInterval);
      targetLocator.setRetryIntervalMultiplier(retryIntervalMultiplier);
      targetLocator.setMinLargeMessageSize(minLargeMessageSize);
      targetLocator.setCallTimeout(serverLocator.getCallTimeout());
      targetLocator.setCallFailoverTimeout(serverLocator.getCallFailoverTimeout());

      // No producer flow control on the bridges by default, as we don't want to lock the queues
      targetLocator.setProducerWindowSize(this.producerWindowSize);

      targetLocator.setAfterConnectionInternalListener(this);

      serverLocator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(serverLocator, server.getStorageManager()));

      targetLocator.setNodeID(nodeId);

      targetLocator.setClusterTransportConfiguration(serverLocator.getClusterTransportConfiguration());

      if (retryInterval > 0) {
         targetLocator.setRetryInterval(retryInterval);
      }

      targetLocator.disableFinalizeCheck();
      targetLocator.addIncomingInterceptor(new IncomingInterceptorLookingForExceptionMessage(manager, executorFactory.getExecutor()));
      MessageFlowRecordImpl record = new MessageFlowRecordImpl(targetLocator, eventUID, targetNodeID, connector, queueName, queue);

      ClusterConnectionBridge bridge = new ClusterConnectionBridge(this, manager, targetLocator, serverLocator, initialConnectAttempts, reconnectAttempts, retryInterval, retryIntervalMultiplier, maxRetryInterval, nodeManager.getUUID(), record.getEventUID(), record.getTargetNodeID(), record.getQueueName(), record.getQueue(), executorFactory.getExecutor(), null, null, scheduledExecutor, null, useDuplicateDetection, clusterUser, clusterPassword, server, managementService.getManagementAddress(), managementService.getManagementNotificationAddress(), record, record.getConnector(), storeAndForwardPrefix, server.getStorageManager());

      targetLocator.setIdentity("(Cluster-connection-bridge::" + bridge.toString() + "::" + this.toString() + ")");

      if (logger.isDebugEnabled()) {
         logger.debug("creating record between " + this.connector + " and " + connector + bridge);
      }

      record.setBridge(bridge);

      records.put(targetNodeID, record);

      if (start) {
         bridge.start();
      }

      if ( !ConfigurationImpl.checkoutDupCacheSize(serverLocator.getConfirmationWindowSize(),server.getConfiguration().getIDCacheSize())) {
         ActiveMQServerLogger.LOGGER.duplicateCacheSizeWarning(server.getConfiguration().getIDCacheSize(), serverLocator.getConfirmationWindowSize());
      }
   }

   private class MessageFlowRecordImpl implements MessageFlowRecord {

      private BridgeImpl bridge;

      private final long eventUID;

      private final String targetNodeID;

      private final TransportConfiguration connector;

      private final ServerLocatorInternal targetLocator;

      private final SimpleString queueName;

      private boolean disconnected = false;

      private final Queue queue;

      private final Map<SimpleString, RemoteQueueBinding> bindings = new HashMap<>();

      private volatile boolean isClosed = false;

      private volatile boolean reset = false;

      private MessageFlowRecordImpl(final ServerLocatorInternal targetLocator,
                                    final long eventUID,
                                    final String targetNodeID,
                                    final TransportConfiguration connector,
                                    final SimpleString queueName,
                                    final Queue queue) {
         this.targetLocator = targetLocator;
         this.queue = queue;
         this.targetNodeID = targetNodeID;
         this.connector = connector;
         this.queueName = queueName;
         this.eventUID = eventUID;
      }

      /* (non-Javadoc)
       * @see java.lang.Object#toString()
       */
      @Override
      public String toString() {
         return "MessageFlowRecordImpl [nodeID=" + targetNodeID +
            ", connector=" +
            connector +
            ", queueName=" +
            queueName +
            ", queue=" +
            queue +
            ", isClosed=" +
            isClosed +
            ", reset=" +
            reset +
            "]";
      }

      @Override
      public void serverDisconnected() {
         this.disconnected = true;
      }

      @Override
      public String getAddress() {
         return address != null ? address.toString() : "";
      }

      /**
       * @return the eventUID
       */
      public long getEventUID() {
         return eventUID;
      }

      /**
       * @return the nodeID
       */
      public String getTargetNodeID() {
         return targetNodeID;
      }

      /**
       * @return the connector
       */
      public TransportConfiguration getConnector() {
         return connector;
      }

      /**
       * @return the queueName
       */
      public SimpleString getQueueName() {
         return queueName;
      }

      /**
       * @return the queue
       */
      public Queue getQueue() {
         return queue;
      }

      @Override
      public int getMaxHops() {
         return maxHops;
      }

      /*
      * we should only ever close a record when the node itself has gone down or in the case of scale down where we know
      * the node is being completely destroyed and in this case we will migrate to another server/Bridge.
      * */
      @Override
      public void close() throws Exception {
         if (logger.isTraceEnabled()) {
            logger.trace("Stopping bridge " + bridge);
         }

         isClosed = true;

         clearBindings();

         if (disconnected) {
            bridge.disconnect();
         }

         bridge.stop();

         bridge.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
               try {
                  if (disconnected) {
                     targetLocator.cleanup();
                  } else {
                     targetLocator.close();
                  }
               } catch (Exception ignored) {
                  logger.debug(ignored.getMessage(), ignored);
               }
            }
         });
      }

      @Override
      public boolean isClosed() {
         return isClosed;
      }

      @Override
      public void reset() throws Exception {
         resetBindings();
      }

      public void setBridge(final BridgeImpl bridge) {
         this.bridge = bridge;
      }

      @Override
      public Bridge getBridge() {
         return bridge;
      }

      @Override
      public synchronized void onMessage(final ClientMessage message) {
         if (logger.isDebugEnabled()) {
            logger.debug("ClusterCommunication::Flow record on " + clusterConnector + " Receiving message " + message);
         }
         try {
            // Reset the bindings
            if (message.containsProperty(PostOfficeImpl.HDR_RESET_QUEUE_DATA)) {
               reset = true;

               return;
            } else if (message.containsProperty(PostOfficeImpl.HDR_RESET_QUEUE_DATA_COMPLETE)) {
               clearDisconnectedBindings();
               return;
            }

            if (!reset) {
               logger.debug("Notification being ignored since first reset wasn't received yet: " + message);
               return;
            }

            handleNotificationMessage(message);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorHandlingMessage(e);
         }
      }

      private void handleNotificationMessage(ClientMessage message) throws Exception {
         // TODO - optimised this by just passing int in header - but filter needs to be extended to support IN with
         // a list of integers
         SimpleString type = message.getSimpleStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE);

         CoreNotificationType ntype = CoreNotificationType.valueOf(type.toString());

         switch (ntype) {
            case BINDING_ADDED: {
               doBindingAdded(message);

               break;
            }
            case BINDING_REMOVED: {
               doBindingRemoved(message);

               break;
            }
            case CONSUMER_CREATED: {
               doConsumerCreated(message);

               break;
            }
            case CONSUMER_CLOSED: {
               doConsumerClosed(message);

               break;
            }
            case PROPOSAL: {
               doProposalReceived(message);

               break;
            }
            case PROPOSAL_RESPONSE: {
               doProposalResponseReceived(message);
               break;
            }
            case UNPROPOSAL: {
               doUnProposalReceived(message);
               break;
            }
            case SESSION_CREATED: {
               doSessionCreated(message);
               break;
            }
            default: {
               throw ActiveMQMessageBundle.BUNDLE.invalidType(ntype);
            }
         }
      }

      /*
      * Inform the grouping handler of a proposal
      * */
      private synchronized void doProposalReceived(final ClientMessage message) throws Exception {
         if (!message.containsProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID)) {
            throw new IllegalStateException("proposal type is null");
         }

         SimpleString type = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID);

         SimpleString val = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE);

         Integer hops = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         if (server.getGroupingHandler() == null) {
            throw new IllegalStateException("grouping handler is null");
         }

         Response response = server.getGroupingHandler().receive(new Proposal(type, val), hops + 1);

         if (response != null) {
            server.getGroupingHandler().sendProposalResponse(response, 0);
         }
      }

      /*
      * Inform the grouping handler of a proposal(groupid) being removed
      * */
      private synchronized void doUnProposalReceived(final ClientMessage message) throws Exception {
         if (!message.containsProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID)) {
            throw new IllegalStateException("proposal type is null");
         }

         SimpleString groupId = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE);

         Integer hops = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         if (server.getGroupingHandler() == null) {
            throw new IllegalStateException("grouping handler is null");
         }

         server.getGroupingHandler().remove(groupId, clusterName, hops + 1);

      }

      /*
      * Inform the grouping handler of a response from a proposal
      *
      * */
      private synchronized void doProposalResponseReceived(final ClientMessage message) throws Exception {
         if (!message.containsProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID)) {
            throw new IllegalStateException("proposal type is null");
         }

         SimpleString type = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID);
         SimpleString val = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE);
         SimpleString alt = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_ALT_VALUE);
         Integer hops = message.getIntProperty(ManagementHelper.HDR_DISTANCE);
         Response response = new Response(type, val, alt);

         if (server.getGroupingHandler() == null) {
            throw new IllegalStateException("grouping handler is null while sending response " + response);
         }

         server.getGroupingHandler().proposed(response);
         server.getGroupingHandler().sendProposalResponse(response, hops + 1);
      }

      private synchronized void clearBindings() throws Exception {
         logger.debug(ClusterConnectionImpl.this + " clearing bindings");
         for (RemoteQueueBinding binding : new HashSet<>(bindings.values())) {
            removeBinding(binding.getClusterName());
         }
      }

      private synchronized void resetBindings() throws Exception {
         logger.debug(ClusterConnectionImpl.this + " reset bindings");
         for (RemoteQueueBinding binding : new HashSet<>(bindings.values())) {
            resetBinding(binding.getClusterName());
         }
      }

      private synchronized void clearDisconnectedBindings() throws Exception {
         logger.debug(ClusterConnectionImpl.this + " reset bindings");
         for (RemoteQueueBinding binding : new HashSet<>(bindings.values())) {
            if (!binding.isConnected()) {
               removeBinding(binding.getClusterName());
            }
         }
      }

      @Override
      public synchronized void disconnectBindings() throws Exception {
         logger.debug(ClusterConnectionImpl.this + " disconnect bindings");
         reset = false;
         for (RemoteQueueBinding binding : new HashSet<>(bindings.values())) {
            disconnectBinding(binding.getClusterName());
         }
      }

      private synchronized void doBindingAdded(final ClientMessage message) throws Exception {
         if (logger.isTraceEnabled()) {
            logger.trace(ClusterConnectionImpl.this + " Adding binding " + message);
         }
         if (!message.containsProperty(ManagementHelper.HDR_DISTANCE)) {
            throw new IllegalStateException("distance is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_ADDRESS)) {
            throw new IllegalStateException("queueAddress is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME)) {
            throw new IllegalStateException("clusterName is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_ROUTING_NAME)) {
            throw new IllegalStateException("routingName is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_BINDING_ID)) {
            throw new IllegalStateException("queueID is null");
         }

         Integer distance = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         SimpleString queueAddress = message.getSimpleStringProperty(ManagementHelper.HDR_ADDRESS);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         SimpleString routingName = message.getSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME);

         SimpleString filterString = message.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

         Long queueID = message.getLongProperty(ManagementHelper.HDR_BINDING_ID);

         RemoteQueueBinding existingBinding = (RemoteQueueBinding) postOffice.getBinding(clusterName);

         if (existingBinding != null) {
            if (queueID.equals(existingBinding.getRemoteQueueID())) {
               if (!existingBinding.isConnected()) {
                  existingBinding.connect();
                  return;
               }
               // Sanity check - this means the binding has already been added via another bridge, probably max
               // hops is too high
               // or there are multiple cluster connections for the same address

               ActiveMQServerLogger.LOGGER.remoteQueueAlreadyBoundOnClusterConnection(this, clusterName);
               return;
            }
            //this could happen during jms non-durable failover while the qname doesn't change but qid
            //will be re-generated in backup. In that case a new remote binding will be created
            //and put it to the map and old binding removed.
            if (logger.isTraceEnabled()) {
               logger.trace("Removing binding because qid changed " + queueID + " old: " + existingBinding.getRemoteQueueID());
            }
            removeBinding(clusterName);
         }

         RemoteQueueBinding binding = new RemoteQueueBindingImpl(server.getStorageManager().generateID(), queueAddress, clusterName, routingName, queueID, filterString, queue, bridge.getName(), distance + 1, messageLoadBalancingType);

         if (logger.isTraceEnabled()) {
            logger.trace("Adding binding " + clusterName + " into " + ClusterConnectionImpl.this);
         }

         bindings.put(clusterName, binding);

         try {
            postOffice.addBinding(binding);
         } catch (Exception ignore) {
         }

      }

      private void doBindingRemoved(final ClientMessage message) throws Exception {
         if (logger.isTraceEnabled()) {
            logger.trace(ClusterConnectionImpl.this + " Removing binding " + message);
         }
         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME)) {
            throw new IllegalStateException("clusterName is null");
         }

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         removeBinding(clusterName);
      }

      private synchronized void removeBinding(final SimpleString clusterName) throws Exception {
         RemoteQueueBinding binding = bindings.remove(clusterName);

         if (binding == null) {
            logger.warn("Cannot remove binding, because cannot find binding for queue " + clusterName);
            return;
         }

         postOffice.removeBinding(binding.getUniqueName(), null, true);
      }

      private synchronized void resetBinding(final SimpleString clusterName) throws Exception {
         RemoteQueueBinding binding = bindings.get(clusterName);
         if (binding == null) {
            throw new IllegalStateException("Cannot find binding for queue " + clusterName);
         }
         binding.reset();
      }

      private synchronized void disconnectBinding(final SimpleString clusterName) throws Exception {
         RemoteQueueBinding binding = bindings.get(clusterName);

         if (binding == null) {
            throw new IllegalStateException("Cannot find binding for queue " + clusterName);
         }

         binding.disconnect();
      }

      private synchronized void doSessionCreated(final ClientMessage message) throws Exception {
         if (logger.isTraceEnabled()) {
            logger.trace(ClusterConnectionImpl.this + " session created " + message);
         }
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(ManagementHelper.HDR_CONNECTION_NAME, message.getSimpleStringProperty(ManagementHelper.HDR_CONNECTION_NAME));
         props.putSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS, message.getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS));
         props.putSimpleStringProperty(ManagementHelper.HDR_CLIENT_ID, message.getSimpleStringProperty(ManagementHelper.HDR_CLIENT_ID));
         props.putSimpleStringProperty(ManagementHelper.HDR_PROTOCOL_NAME, message.getSimpleStringProperty(ManagementHelper.HDR_PROTOCOL_NAME));
         props.putIntProperty(ManagementHelper.HDR_DISTANCE, message.getIntProperty(ManagementHelper.HDR_DISTANCE) + 1);
         managementService.sendNotification(new Notification(null, CoreNotificationType.SESSION_CREATED, props));
      }

      private synchronized void doConsumerCreated(final ClientMessage message) throws Exception {
         if (logger.isTraceEnabled()) {
            logger.trace(ClusterConnectionImpl.this + " Consumer created " + message);
         }
         if (!message.containsProperty(ManagementHelper.HDR_DISTANCE)) {
            throw new IllegalStateException("distance is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME)) {
            throw new IllegalStateException("clusterName is null");
         }

         Integer distance = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         message.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         SimpleString filterString = message.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

         RemoteQueueBinding binding = bindings.get(clusterName);

         if (binding == null) {
            throw new IllegalStateException("Cannot find binding for " + clusterName +
                                               " on " +
                                               ClusterConnectionImpl.this);
         }

         binding.addConsumer(filterString);

         // Need to propagate the consumer add
         TypedProperties props = new TypedProperties();

         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

         props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, clusterName);

         props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

         props.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         Queue theQueue = (Queue) binding.getBindable();

         props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, theQueue.getConsumerCount());

         if (filterString != null) {
            props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filterString);
         }

         Notification notification = new Notification(null, CoreNotificationType.CONSUMER_CREATED, props);

         managementService.sendNotification(notification);
      }

      private synchronized void doConsumerClosed(final ClientMessage message) throws Exception {
         if (logger.isTraceEnabled()) {
            logger.trace(ClusterConnectionImpl.this + " Consumer closed " + message);
         }
         if (!message.containsProperty(ManagementHelper.HDR_DISTANCE)) {
            throw new IllegalStateException("distance is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME)) {
            throw new IllegalStateException("clusterName is null");
         }

         Integer distance = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         message.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         SimpleString filterString = message.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

         RemoteQueueBinding binding = bindings.get(clusterName);

         if (binding == null) {
            throw new IllegalStateException("Cannot find binding for " + clusterName);
         }

         binding.removeConsumer(filterString);

         // Need to propagate the consumer close
         TypedProperties props = new TypedProperties();

         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

         props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, clusterName);

         props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

         props.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         Queue theQueue = (Queue) binding.getBindable();

         props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, theQueue.getConsumerCount());

         if (filterString != null) {
            props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filterString);
         }
         Notification notification = new Notification(null, CoreNotificationType.CONSUMER_CLOSED, props);

         managementService.sendNotification(notification);
      }

   }

   // for testing only
   public Map<String, MessageFlowRecord> getRecords() {
      return records;
   }

   @Override
   public String toString() {
      return "ClusterConnectionImpl@" + System.identityHashCode(this) +
         "[nodeUUID=" +
         nodeManager.getNodeId() +
         ", connector=" +
         connector +
         ", address=" +
         address +
         ", server=" +
         server +
         "]";
   }

   @Override
   public String describe() {
      StringWriter str = new StringWriter();
      PrintWriter out = new PrintWriter(str);

      out.println(this);
      out.println("***************************************");
      out.println(name + " connected to");
      for (MessageFlowRecord messageFlow : records.values()) {
         out.println("\t Bridge = " + messageFlow.getBridge());
         out.println("\t Flow Record = " + messageFlow);
      }
      out.println("***************************************");

      return str.toString();
   }

   private interface ClusterConnector {

      ServerLocatorInternal createServerLocator();
   }

   private final class StaticClusterConnector implements ClusterConnector {

      private final TransportConfiguration[] tcConfigs;

      private StaticClusterConnector(TransportConfiguration[] tcConfigs) {
         this.tcConfigs = tcConfigs;
      }

      @Override
      public ServerLocatorInternal createServerLocator() {
         if (tcConfigs != null && tcConfigs.length > 0) {
            if (logger.isDebugEnabled()) {
               logger.debug(ClusterConnectionImpl.this + "Creating a serverLocator for " + Arrays.toString(tcConfigs));
            }
            ServerLocatorImpl locator = new ServerLocatorImpl(topology, true, tcConfigs);
            locator.setClusterConnection(true);
            locator.setClusterTransportConfiguration(connector);
            return locator;
         }
         return null;
      }

      @Override
      public String toString() {
         return "StaticClusterConnector [tcConfigs=" + Arrays.toString(tcConfigs) + "]";
      }

   }

   private final class DiscoveryClusterConnector implements ClusterConnector {

      private final DiscoveryGroupConfiguration dg;

      private DiscoveryClusterConnector(DiscoveryGroupConfiguration dg) {
         this.dg = dg;
      }

      @Override
      public ServerLocatorInternal createServerLocator() {
         return new ServerLocatorImpl(topology, true, dg);
      }
   }

   @Override
   public boolean verify(String clusterUser0, String clusterPassword0) {
      return clusterUser.equals(clusterUser0) && clusterPassword.equals(clusterPassword0);
   }

   @Override
   public void removeRecord(String targetNodeID) {
      logger.debug("Removing record for: " + targetNodeID);
      MessageFlowRecord record = records.remove(targetNodeID);
      try {
         if (record != null) {
            record.close();
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToRemoveRecord(e);
      }
   }

   @Override
   public void disconnectRecord(String targetNodeID) {
      logger.debug("Disconnecting record for: " + targetNodeID);
      MessageFlowRecord record = records.get(targetNodeID);
      try {
         if (record != null) {
            record.disconnectBindings();
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToDisconnectBindings(e);
      }
   }

   private final class LiveNotifier implements Runnable {

      int notificationsSent = 0;

      @Override
      public void run() {
         resendLive();

         schedule();
      }

      public void schedule() {
         if (started && !stopping && notificationsSent++ < clusterNotificationAttempts) {
            scheduledExecutor.schedule(this, clusterNotificationInterval, TimeUnit.MILLISECONDS);
         }
      }

      public void updateAsLive() {
         if (!stopping && started) {
            topology.updateAsLive(manager.getNodeId(), new TopologyMemberImpl(manager.getNodeId(), manager.getBackupGroupName(), manager.getScaleDownGroupName(), connector, null));
         }
      }

      public void resendLive() {
         if (!stopping && started) {
            topology.resendNode(manager.getNodeId());
         }
      }
   }
}
