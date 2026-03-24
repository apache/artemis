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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterConnectReplyMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.NodeAnnounceMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ScaleDownAnnounceMessage;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.qourum.QuorumManager;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.jboss.logging.Logger;

/**
 * used for creating and managing cluster control connections for each cluster connection and the replication connection
 */
public class ClusterController implements ActiveMQComponent {

   private static final Logger logger = Logger.getLogger(ClusterController.class);

   private final QuorumManager quorumManager;

   private final ActiveMQServer server;

   private final Map<SimpleString, ServerLocatorInternal> locators = new HashMap<>();

   private SimpleString defaultClusterConnectionName;

   private ServerLocator defaultLocator;

   private ServerLocator replicationLocator;

   private final Executor executor;

   private CountDownLatch replicationClusterConnectedLatch;

   private boolean started;
   private SimpleString replicatedClusterName;

   /** For tests only */
   public ServerLocator getDefaultLocator() {
      return defaultLocator;
   }

   public ClusterController(ActiveMQServer server,
                            ScheduledExecutorService scheduledExecutor,
                            boolean useQuorumManager) {
      this.server = server;
      executor = server.getExecutorFactory().getExecutor();
      quorumManager = useQuorumManager ? new QuorumManager(scheduledExecutor, this) : null;
   }

   public ClusterController(ActiveMQServer server, ScheduledExecutorService scheduledExecutor) {
      this(server, scheduledExecutor, true);
   }

   @Override
   public void start() throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("Starting Cluster Controller " + System.identityHashCode(this) + " for server " + server);
      }
      if (started)
         return;
      //set the default locator that will be used to connecting to the default cluster.
      defaultLocator = locators.get(defaultClusterConnectionName);
      //create a locator for replication, either the default or the specified if not set
      if (replicatedClusterName != null && !replicatedClusterName.equals(defaultClusterConnectionName)) {
         replicationLocator = locators.get(replicatedClusterName);
         if (replicationLocator == null) {
            ActiveMQServerLogger.LOGGER.noClusterConnectionForReplicationCluster();
            replicationLocator = defaultLocator;
         }
      } else {
         replicationLocator = defaultLocator;
      }
      //latch so we know once we are connected
      replicationClusterConnectedLatch = new CountDownLatch(1);
      //and add the quorum manager as a topology listener
      if (quorumManager != null) {
         if (defaultLocator != null) {
            defaultLocator.addClusterTopologyListener(quorumManager);
         }

         //start the quorum manager
         quorumManager.start();
      }

      started = true;
      //connect all the locators in a separate thread
      for (ServerLocatorInternal serverLocatorInternal : locators.values()) {
         if (serverLocatorInternal.isConnectable()) {
            executor.execute(new ConnectRunnable(serverLocatorInternal));
         }
      }
   }

   /**
    * It adds {@code clusterTopologyListener} to {@code defaultLocator}.
    */
   public void addClusterTopologyListener(ClusterTopologyListener clusterTopologyListener) {
      if (!this.started || defaultLocator == null) {
         throw new IllegalStateException("the controller must be started and with a locator initialized");
      }
      this.defaultLocator.addClusterTopologyListener(clusterTopologyListener);
   }

   /**
    * It remove {@code clusterTopologyListener} from {@code defaultLocator}.
    */
   public void removeClusterTopologyListener(ClusterTopologyListener clusterTopologyListener) {
      if (!this.started || defaultLocator == null) {
         throw new IllegalStateException("the controller must be started and with a locator initialized");
      }
      this.defaultLocator.removeClusterTopologyListener(clusterTopologyListener);
   }

   @Override
   public void stop() throws Exception {
      if (logger.isDebugEnabled()) {

         logger.debug("Stopping Cluster Controller " + System.identityHashCode(this) + " for server " + this.server);
      }
      started = false;
      //close all the locators
      for (ServerLocatorInternal serverLocatorInternal : locators.values()) {
         serverLocatorInternal.close();
      }
      //stop the quorum manager
      if (quorumManager != null) {
         quorumManager.stop();
      }
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public QuorumManager getQuorumManager() {
      return quorumManager;
   }

   //set the default cluster connections name
   public void setDefaultClusterConnectionName(SimpleString defaultClusterConnection) {
      this.defaultClusterConnectionName = defaultClusterConnection;
   }

   /**
    * add a locator for a cluster connection.
    *
    * @param name                the cluster connection name
    * @param dg                  the discovery group to use
    * @param config              the cluster connection config
    * @param connector           the cluster connector configuration
    */
   public void addClusterConnection(SimpleString name,
                                    DiscoveryGroupConfiguration dg,
                                    ClusterConnectionConfiguration config,
                                    TransportConfiguration connector) {
      ServerLocatorImpl serverLocator = (ServerLocatorImpl) ActiveMQClient.createServerLocatorWithHA(dg);
      configAndAdd(name, serverLocator, config, connector);
   }

   /**
    * add a locator for a cluster connection.
    *
    * @param name      the cluster connection name
    * @param tcConfigs the transport configurations to use
    */
   public void addClusterConnection(SimpleString name,
                                    TransportConfiguration[] tcConfigs,
                                    ClusterConnectionConfiguration config,
                                    TransportConfiguration connector) {
      ServerLocatorImpl serverLocator = (ServerLocatorImpl) ActiveMQClient.createServerLocatorWithHA(tcConfigs);
      configAndAdd(name, serverLocator, config, connector);
   }

   private void configAndAdd(SimpleString name,
                             ServerLocatorInternal serverLocator,
                             ClusterConnectionConfiguration config,
                             TransportConfiguration connector) {
      serverLocator.setConnectionTTL(config.getConnectionTTL());
      serverLocator.setClientFailureCheckPeriod(config.getClientFailureCheckPeriod());
      //if the cluster isn't available we want to hang around until it is
      serverLocator.setReconnectAttempts(config.getReconnectAttempts());
      serverLocator.setInitialConnectAttempts(config.getInitialConnectAttempts());
      serverLocator.setCallTimeout(config.getCallTimeout());
      serverLocator.setCallFailoverTimeout(config.getCallFailoverTimeout());
      serverLocator.setRetryInterval(config.getRetryInterval());
      serverLocator.setRetryIntervalMultiplier(config.getRetryIntervalMultiplier());
      serverLocator.setMaxRetryInterval(config.getMaxRetryInterval());
      //this is used for replication so need to use the server packet decoder
      serverLocator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(serverLocator, server.getStorageManager()));
      serverLocator.setThreadPools(server.getThreadPool(), server.getScheduledPool());
      if (connector != null) {
         serverLocator.setClusterTransportConfiguration(connector);
      }
      try {
         serverLocator.initialize();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
      locators.put(name, serverLocator);
   }

   /**
    * add a cluster listener
    *
    * @param listener
    */
   public void addClusterTopologyListenerForReplication(ClusterTopologyListener listener) {
      if (replicationLocator != null) {
         replicationLocator.addClusterTopologyListener(listener);
      }
   }

   /**
    * add a cluster listener
    *
    * @param listener
    */
   public void removeClusterTopologyListenerForReplication(ClusterTopologyListener listener) {
      if (replicationLocator != null) {
         replicationLocator.removeClusterTopologyListener(listener);
      }
   }

   /**
    * add an interceptor
    *
    * @param interceptor
    */
   public void addIncomingInterceptorForReplication(Interceptor interceptor) {
      replicationLocator.addIncomingInterceptor(interceptor);
   }

   /**
    * remove an interceptor
    *
    * @param interceptor
    */
   public void removeIncomingInterceptorForReplication(Interceptor interceptor) {
      replicationLocator.removeIncomingInterceptor(interceptor);
   }

   /**
    * connect to a specific node in the cluster used for replication
    *
    * @param transportConfiguration the configuration of the node to connect to.
    * @return the Cluster Control
    * @throws Exception
    */
   public ClusterControl connectToNode(TransportConfiguration transportConfiguration) throws Exception {
      ClientSessionFactoryInternal sessionFactory = (ClientSessionFactoryInternal) defaultLocator.createSessionFactory(transportConfiguration, 0, false);

      return connectToNodeInCluster(sessionFactory);
   }

   /**
    * connect to a specific node in the cluster used for replication
    *
    * @param transportConfiguration the configuration of the node to connect to.
    * @return the Cluster Control
    * @throws Exception
    */
   public ClusterControl connectToNodeInReplicatedCluster(TransportConfiguration transportConfiguration) throws Exception {
      ClientSessionFactoryInternal sessionFactory = (ClientSessionFactoryInternal) replicationLocator.createSessionFactory(transportConfiguration, 0, false);

      return connectToNodeInCluster(sessionFactory);
   }

   /**
    * connect to an already defined node in the cluster
    *
    * @param sf the session factory
    * @return the Cluster Control
    */
   public ClusterControl connectToNodeInCluster(ClientSessionFactoryInternal sf) {
      sf.getServerLocator().setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(sf.getServerLocator(), server.getStorageManager()));
      return new ClusterControl(sf, server);
   }

   /**
    * retry interval for connecting to the cluster
    *
    * @return the retry interval
    */
   public long getRetryIntervalForReplicatedCluster() {
      return replicationLocator.getRetryInterval();
   }

   /**
    * wait until we have connected to the cluster.
    *
    * @throws InterruptedException
    */
   public void awaitConnectionToReplicationCluster() throws InterruptedException {
      replicationClusterConnectedLatch.await();
   }

   /**
    * used to set a channel handler on the connection that can be used by the cluster control
    *
    * @param channel            the channel to set the handler
    * @param acceptorUsed       the acceptor used for connection
    * @param remotingConnection the connection itself
    * @param activation
    */
   public void addClusterChannelHandler(Channel channel,
                                        Acceptor acceptorUsed,
                                        CoreRemotingConnection remotingConnection,
                                        Activation activation) {
      channel.setHandler(new ClusterControllerChannelHandler(channel, acceptorUsed, remotingConnection, activation.getActivationChannelHandler(channel, acceptorUsed)));
   }

   public int getDefaultClusterSize() {
      return defaultLocator.getTopology().getMembers().size();
   }

   public Topology getDefaultClusterTopology() {
      return defaultLocator.getTopology();
   }

   public SimpleString getNodeID() {
      return server.getNodeID();
   }

   public String getIdentity() {
      return server.getIdentity();
   }

   public void setReplicatedClusterName(String replicatedClusterName) {
      this.replicatedClusterName = new SimpleString(replicatedClusterName);
   }

   public Map<SimpleString, ServerLocatorInternal> getLocators() {
      return this.locators;
   }

   /**
    * a handler for handling packets sent between the cluster.
    */
   private final class ClusterControllerChannelHandler implements ChannelHandler {

      private final Channel clusterChannel;
      private final Acceptor acceptorUsed;
      private final CoreRemotingConnection remotingConnection;
      private final ChannelHandler channelHandler;
      boolean authorized = false;

      private ClusterControllerChannelHandler(Channel clusterChannel,
                                              Acceptor acceptorUsed,
                                              CoreRemotingConnection remotingConnection,
                                              ChannelHandler channelHandler) {
         this.clusterChannel = clusterChannel;
         this.acceptorUsed = acceptorUsed;
         this.remotingConnection = remotingConnection;
         this.channelHandler = channelHandler;
      }

      @Override
      public void handlePacket(Packet packet) {
         if (!isStarted()) {
            if (channelHandler != null) {
               channelHandler.handlePacket(packet);
            }
            return;
         }

         if (!authorized) {
            if (packet.getType() == PacketImpl.CLUSTER_CONNECT) {
               ClusterConnection clusterConnection = acceptorUsed.getClusterConnection();

               //if this acceptor isn't associated with a cluster connection use the default
               if (clusterConnection == null) {
                  clusterConnection = server.getClusterManager().getDefaultConnection(null);
               }

               //if there is no default cluster connection and security is enabled then just ignore the packet with a log message
               if (clusterConnection == null && server.getConfiguration().isSecurityEnabled()) {
                  ActiveMQServerLogger.LOGGER.failedToFindClusterConnection(packet.toString());
                  return;
               }

               ClusterConnectMessage msg = (ClusterConnectMessage) packet;

               if (server.getConfiguration().isSecurityEnabled() && !clusterConnection.verify(msg.getClusterUser(), msg.getClusterPassword())) {
                  clusterChannel.send(new ClusterConnectReplyMessage(false));
               } else {
                  authorized = true;
                  clusterChannel.send(new ClusterConnectReplyMessage(true));
               }
            }
         } else {
            if (packet.getType() == PacketImpl.NODE_ANNOUNCE) {
               NodeAnnounceMessage msg = (NodeAnnounceMessage) packet;

               Pair<TransportConfiguration, TransportConfiguration> pair;
               if (msg.isBackup()) {
                  pair = new Pair<>(null, msg.getConnector());
               } else {
                  pair = new Pair<>(msg.getConnector(), msg.getBackupConnector());
               }
               if (logger.isTraceEnabled()) {
                  logger.trace("Server " + server + " receiving nodeUp from NodeID=" + msg.getNodeID() + ", pair=" + pair);
               }

               if (acceptorUsed != null) {
                  ClusterConnection clusterConn = acceptorUsed.getClusterConnection();
                  if (clusterConn != null) {
                     String scaleDownGroupName = msg.getScaleDownGroupName();
                     clusterConn.nodeAnnounced(msg.getCurrentEventID(), msg.getNodeID(), msg.getBackupGroupName(), scaleDownGroupName, pair, msg.isBackup());
                  } else {
                     logger.debug("Cluster connection is null on acceptor = " + acceptorUsed);
                  }
               } else {
                  logger.debug("there is no acceptor used configured at the CoreProtocolManager " + this);
               }
            } else if (packet.getType() == PacketImpl.QUORUM_VOTE) {
               if (quorumManager != null) {
                  quorumManager.handleQuorumVote(clusterChannel, packet);
               } else {
                  logger.warnf("Received %s on a cluster connection that's using the new quorum vote algorithm.", packet);
               }
            } else if (packet.getType() == PacketImpl.SCALEDOWN_ANNOUNCEMENT) {
               ScaleDownAnnounceMessage message = (ScaleDownAnnounceMessage) packet;
               //we don't really need to check as it should always be true
               if (server.getNodeID().equals(message.getTargetNodeId())) {
                  server.addScaledDownNode(message.getScaledDownNodeId());
               }
            } else if (channelHandler != null) {
               channelHandler.handlePacket(packet);
            }
         }
      }

   }

   /**
    * used for making the initial connection in the cluster
    */
   private final class ConnectRunnable implements Runnable {

      private final ServerLocatorInternal serverLocator;

      private ConnectRunnable(ServerLocatorInternal serverLocator) {
         this.serverLocator = serverLocator;
      }

      @Override
      public void run() {
         try {
            if (started) {
               serverLocator.connect();
               if (serverLocator == replicationLocator) {
                  replicationClusterConnectedLatch.countDown();
               }
            }
         } catch (ActiveMQException e) {
            if (!started) {
               return;
            }
            if (logger.isDebugEnabled()) {

               logger.debug("retry on Cluster Controller " + System.identityHashCode(ClusterController.this) + " server = " + server);
            }
            server.getScheduledPool().schedule(this, serverLocator.getRetryInterval(), TimeUnit.MILLISECONDS);
         }
      }
   }

   public ServerLocator getReplicationLocator() {
      return this.replicationLocator;
   }

   public ServerLocator getServerLocator(SimpleString name) {
      return locators.get(name);
   }

}
