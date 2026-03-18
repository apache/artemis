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
package org.apache.activemq.artemis.core.server.impl;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQAlreadyReplicatingException;
import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupRegistrationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupReplicationStartFailedMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationPrimaryIsStoppingMessage;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedNothingPrimaryActivation extends PrimaryActivation {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   //this is how we act when we initially start as a primary
   private ReplicatedPolicy replicatedPolicy;

   private ActiveMQServerImpl activeMQServer;

   private ReplicationManager replicationManager;

   private final Object replicationLock = new Object();

   public SharedNothingPrimaryActivation(ActiveMQServerImpl activeMQServer, ReplicatedPolicy replicatedPolicy) {
      this.activeMQServer = activeMQServer;
      this.replicatedPolicy = replicatedPolicy;
   }

   @Override
   public void freezeConnections(RemotingService remotingService) {
      ReplicationManager localReplicationManager = replicationManager;

      if (remotingService != null && localReplicationManager != null) {
         remotingService.freeze(null, localReplicationManager.getBackupTransportConnection());
      } else if (remotingService != null) {
         remotingService.freeze(null, null);
      }
   }

   public void freezeReplication() {
      replicationManager.getBackupTransportConnection().fail(new ActiveMQDisconnectedException());
   }

   @Override
   public void run() {
      try {
         // Tell cluster connections to not accept split brains updates on the topology
         if (replicatedPolicy.isCheckForPrimaryServer() && isNodeIdUsed()) {
            //set for when we failback
            if (logger.isTraceEnabled()) {
               logger.trace("setting up replicatedPolicy.getReplicaPolicy for back start, replicaPolicy::{}, isBackup={}, server={}",
                     replicatedPolicy.getReplicaPolicy(), replicatedPolicy.isBackup(), activeMQServer);
            }
            replicatedPolicy.getReplicaPolicy().setReplicatedPolicy(replicatedPolicy);
            activeMQServer.setHAPolicy(replicatedPolicy.getReplicaPolicy());
            return;
         }

         activeMQServer.initialisePart1(false);

         activeMQServer.getClusterManager().getQuorumManager().registerQuorumHandler(new ServerConnectVoteHandler(activeMQServer));

         activeMQServer.initialisePart2(false);

         activeMQServer.completeActivation(true);

         if (activeMQServer.getIdentity() != null) {
            ActiveMQServerLogger.LOGGER.serverIsActive(activeMQServer.getIdentity());
         } else {
            ActiveMQServerLogger.LOGGER.serverIsActive();
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.initializationError(e);
         activeMQServer.callActivationFailureListeners(e);
      }
   }

   @Override
   public ChannelHandler getActivationChannelHandler(final Channel channel, final Acceptor acceptorUsed) {
      return packet -> {
         if (packet.getType() == PacketImpl.BACKUP_REGISTRATION) {
            BackupRegistrationMessage msg = (BackupRegistrationMessage) packet;
            ClusterConnection clusterConnection = acceptorUsed.getClusterConnection();
            try {
               startReplication(channel.getConnection(), clusterConnection, getPair(msg.getConnector(), true), msg.isFailBackRequest());
            } catch (ActiveMQAlreadyReplicatingException are) {
               channel.send(new BackupReplicationStartFailedMessage(BackupReplicationStartFailedMessage.BackupRegistrationProblem.ALREADY_REPLICATING));
            } catch (ActiveMQException e) {
               logger.debug("Failed to process backup registration packet", e);
               channel.send(new BackupReplicationStartFailedMessage(BackupReplicationStartFailedMessage.BackupRegistrationProblem.EXCEPTION));
            }
         }
      };
   }

   public void startReplication(CoreRemotingConnection rc,
                                final ClusterConnection clusterConnection,
                                final Pair<TransportConfiguration, TransportConfiguration> pair,
                                final boolean isFailBackRequest) throws ActiveMQException {
      if (replicationManager != null) {
         throw new ActiveMQAlreadyReplicatingException();
      }

      if (!activeMQServer.isStarted()) {
         throw new ActiveMQIllegalStateException();
      }

      synchronized (replicationLock) {

         if (replicationManager != null) {
            throw new ActiveMQAlreadyReplicatingException();
         }
         ReplicationFailureListener listener = new ReplicationFailureListener();
         rc.addCloseListener(listener);
         rc.addFailureListener(listener);
         replicationManager = new ReplicationManager(activeMQServer, rc, clusterConnection.getCallTimeout(), replicatedPolicy.getInitialReplicationSyncTimeout(), activeMQServer.getIOExecutorFactory());
         replicationManager.start();
         Thread t = new Thread(() -> {
            try {
               activeMQServer.getStorageManager().startReplication(replicationManager, activeMQServer.getPagingManager(), activeMQServer.getNodeID().toString(), isFailBackRequest && replicatedPolicy.isAllowAutoFailBack(), replicatedPolicy.getInitialReplicationSyncTimeout());

               clusterConnection.nodeAnnounced(System.currentTimeMillis(), activeMQServer.getNodeID().toString(), replicatedPolicy.getGroupName(), replicatedPolicy.getScaleDownGroupName(), pair, true);

               //todo, check why this was set here
               //backupUpToDate = false;

               if (isFailBackRequest && replicatedPolicy.isAllowAutoFailBack()) {
                  BackupTopologyListener listener1 = new BackupTopologyListener(activeMQServer.getNodeID().toString(), clusterConnection.getConnector());
                  clusterConnection.addClusterTopologyListener(listener1);
                  if (listener1.waitForBackup()) {
                     //if we have to many backups kept or are not configured to restart just stop, otherwise restart as a backup
                     activeMQServer.fail(true);
                     ActiveMQServerLogger.LOGGER.restartingReplicatedBackupAfterFailback();
                     //                        activeMQServer.moveServerData(replicatedPolicy.getReplicaPolicy().getMaxSavedReplicatedJournalsSize());
                     activeMQServer.setHAPolicy(replicatedPolicy.getReplicaPolicy());
                     activeMQServer.start();
                  } else {
                     ActiveMQServerLogger.LOGGER.failbackMissedBackupAnnouncement();
                  }
               }
            } catch (Exception e) {
               if (activeMQServer.getState() == ActiveMQServerImpl.SERVER_STATE.STARTED) {
               /*
                * The reasoning here is that the exception was either caused by (1) the (interaction with) the
                * backup, or (2) by an IO Error at the storage. If (1), we can swallow the exception and ignore the
                * replication request. If (2) the primary will crash shortly.
                */
                  ActiveMQServerLogger.LOGGER.errorStartingReplication(e);
               }
               try {
                  ActiveMQServerImpl.stopComponent(replicationManager);
               } catch (Exception amqe) {
                  ActiveMQServerLogger.LOGGER.errorStoppingReplication(amqe);
               } finally {
                  synchronized (replicationLock) {
                     replicationManager = null;
                  }
               }
            }
         });

         t.start();
      }
   }

   private static TransportConfiguration getPrimaryConnector(Configuration configuration) {
      String connectorName = configuration.getClusterConfigurations().get(0).getConnectorName();
      TransportConfiguration transportConfiguration = configuration.getConnectorConfigurations().get(connectorName);
      assert transportConfiguration != null;
      return transportConfiguration;
   }

   private final class ReplicationFailureListener implements FailureListener, CloseListener {

      @Override
      public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         handleClose(true);
      }

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
         connectionFailed(me, failedOver);
      }

      @Override
      public void connectionClosed() {
         handleClose(false);
      }

      private void handleClose(boolean failed) {
         Executor executor = activeMQServer.getThreadPool();
         if (executor != null) {
            executor.execute(() -> {
               synchronized (replicationLock) {
                  if (replicationManager != null) {
                     activeMQServer.getStorageManager().stopReplication();
                     replicationManager = null;

                     if (failed && replicatedPolicy.isVoteOnReplicationFailure()) {
                        QuorumManager quorumManager = activeMQServer.getClusterManager().getQuorumManager();
                        final boolean isStillPrimary = quorumManager.isStillActive(activeMQServer.getNodeID().toString(),
                                                                                getPrimaryConnector(activeMQServer.getConfiguration()),
                                                                                replicatedPolicy.getQuorumSize(),
                                                                                5, TimeUnit.SECONDS);
                        if (!isStillPrimary) {
                           try {
                              Thread startThread = new Thread(() -> {
                                 try {
                                    logger.trace("Calling activeMQServer.stop() to stop the server");

                                    activeMQServer.stop();
                                 } catch (Exception e) {
                                    ActiveMQServerLogger.LOGGER.errorRestartingBackupServer(activeMQServer, e);
                                 }
                              });
                              startThread.start();
                              startThread.join();
                           } catch (Exception e) {
                              e.printStackTrace();
                           }
                        }
                     }
                  }
               }
            });
         }
      }
   }

   private Pair<TransportConfiguration, TransportConfiguration> getPair(TransportConfiguration conn, boolean isBackup) {
      if (isBackup) {
         return new Pair<>(null, conn);
      }
      return new Pair<>(conn, null);
   }

   /**
    * Determines whether there is another server already running with this server's nodeID.
    * <p>
    * This can happen in case of a successful fail-over followed by the primary's restart (attempting a fail-back).
    */
   private boolean isNodeIdUsed() throws Exception {
      if (activeMQServer.getConfiguration().getClusterConfigurations().isEmpty()) {
         return false;
      }
      SimpleString nodeId0;
      try {
         nodeId0 = activeMQServer.getNodeManager().readNodeId();
      } catch (NodeManager.NodeManagerException e) {
         nodeId0 = null;
      }

      ClusterConnectionConfiguration config = ConfigurationUtils.getReplicationClusterConfiguration(activeMQServer.getConfiguration(), replicatedPolicy.getClusterName());

      NodeIdListener listener = new NodeIdListener(nodeId0, activeMQServer.getConfiguration().getClusterUser(), activeMQServer.getConfiguration().getClusterPassword());

      try (ServerLocatorInternal locator = getLocator(config)) {
         locator.addClusterTopologyListener(listener);
         locator.setReconnectAttempts(0);
         try (ClientSessionFactoryInternal factory = locator.connectNoWarnings()) {
            // Just try connecting
            listener.latch.await(5, TimeUnit.SECONDS);
         } catch (Exception notConnected) {
            if (!(notConnected instanceof ActiveMQException) || ActiveMQExceptionType.INTERNAL_ERROR.equals(((ActiveMQException) notConnected).getType())) {
               // report all exceptions that aren't ActiveMQException and all INTERNAL_ERRORs
               ActiveMQServerLogger.LOGGER.failedConnectingToCluster(notConnected);
            }
            return false;
         }

         return listener.isNodePresent;
      }
   }

   @Override
   public void close(boolean permanently, boolean restarting) throws Exception {

      replicationManager = null;
      // To avoid a NPE cause by the stop
      NodeManager nodeManagerInUse = activeMQServer.getNodeManager();

      if (nodeManagerInUse != null) {
         //todo does this actually make any difference, we only set a different flag in the lock file which replication doesn't use
         if (permanently) {
            nodeManagerInUse.crashPrimaryServer();
         } else {
            nodeManagerInUse.pausePrimaryServer();
         }
      }
   }

   @Override
   public void sendPrimaryIsStopping() {
      final ReplicationManager localReplicationManager = replicationManager;

      if (localReplicationManager != null) {
         localReplicationManager.sendPrimaryIsStopping(ReplicationPrimaryIsStoppingMessage.PrimaryStopping.STOP_CALLED);
         // Schedule for 10 seconds
         // this pool gets a 'hard' shutdown, no need to manage the Future of this Runnable.
         activeMQServer.getScheduledPool().schedule(localReplicationManager::clearReplicationTokens, 30, TimeUnit.SECONDS);
      }
   }

   @Override
   public ReplicationManager getReplicationManager() {
      synchronized (replicationLock) {
         return replicationManager;
      }
   }

   private ServerLocatorInternal getLocator(ClusterConnectionConfiguration config) throws ActiveMQException {
      ServerLocatorInternal locator;
      if (config.getDiscoveryGroupName() != null) {
         DiscoveryGroupConfiguration dg = activeMQServer.getConfiguration().getDiscoveryGroupConfigurations().get(config.getDiscoveryGroupName());

         if (dg == null) {
            throw ActiveMQMessageBundle.BUNDLE.noDiscoveryGroupFound(dg);
         }
         locator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(dg);
      } else {
         TransportConfiguration[] tcConfigs = config.getStaticConnectors() != null ? connectorNameListToArray(config.getStaticConnectors()) : null;

         locator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(tcConfigs);
      }
      return locator;
   }

   static final class NodeIdListener implements ClusterTopologyListener {

      volatile boolean isNodePresent = false;

      private final SimpleString nodeId;
      private final String user;
      private final String password;
      private final CountDownLatch latch = new CountDownLatch(1);

      NodeIdListener(SimpleString nodeId, String user, String password) {
         this.nodeId = nodeId;
         this.user = user;
         this.password = password;
      }

      @Override
      public void nodeUP(TopologyMember topologyMember, boolean last) {
         boolean isOurNodeId = nodeId != null && nodeId.toString().equals(topologyMember.getNodeId());
         if (isOurNodeId && isActive(topologyMember.getPrimary())) {
            isNodePresent = true;
         }
         if (isOurNodeId || last) {
            latch.countDown();
         }
      }

      /**
       * In a cluster of replicated primary/backup pairs if a backup crashes and then its primary crashes the cluster
       * will retain the topology information of the primary such that when the primary server restarts it will check
       * the cluster to see if its nodeID is present (which it will be) and then it will activate as a backup rather
       * than a primary. To prevent this situation an additional check is necessary to see if the server with the
       * matching nodeID is actually active or not which is done by attempting to make a connection to it.
       */
      private boolean isActive(TransportConfiguration transportConfiguration) {
         boolean result = false;

         try (ServerLocator serverLocator = ActiveMQClient.createServerLocator(false, transportConfiguration);
              ClientSessionFactory clientSessionFactory = serverLocator.createSessionFactory();
              ClientSession clientSession = clientSessionFactory.createSession(user, password, false, false, false, false, 0)) {
            result = true;
         } catch (Exception e) {
            logger.debug("isActive check failed", e);
         }

         return result;
      }

      @Override
      public void nodeDown(long eventUID, String nodeID) {
         // no-op
      }
   }

   private TransportConfiguration[] connectorNameListToArray(final List<String> connectorNames) {
      return activeMQServer.getConfiguration().getTransportConfigurations(connectorNames);
   }

   @Override
   public boolean isReplicaSync() {
      final ReplicationManager replicationManager = getReplicationManager();
      if (replicationManager == null) {
         return false;
      }
      return !replicationManager.isSynchronizing();
   }
}
