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

import javax.annotation.concurrent.GuardedBy;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQAlreadyReplicatingException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
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
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationPrimaryPolicy;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.server.ActiveMQServer.SERVER_STATE.STARTED;
import static org.apache.activemq.artemis.core.server.impl.quorum.ActivationSequenceStateMachine.awaitNextCommittedActivationSequence;
import static org.apache.activemq.artemis.core.server.impl.quorum.ActivationSequenceStateMachine.ensureSequentialAccessToNodeData;
import static org.apache.activemq.artemis.core.server.impl.quorum.ActivationSequenceStateMachine.tryActivate;

/**
 * This is going to be {@link #run()} just by natural born primary, at the first start. Both during a failover or a
 * failback, {@link #run()} isn't going to be used, but only {@link #getActivationChannelHandler(Channel, Acceptor)}.
 */
public class ReplicationPrimaryActivation extends PrimaryActivation implements DistributedLock.UnavailableLockListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // This is the time we expect a replica to become active from the quorum pov
   // ie time to execute tryActivate and ensureSequentialAccessToNodeData
   private static final long FAILBACK_TIMEOUT_MILLIS = 4_000;

   private final ReplicationPrimaryPolicy policy;

   private final ActiveMQServerImpl activeMQServer;

   @GuardedBy("replicationLock")
   private ReplicationManager replicationManager;

   private final Object replicationLock;

   private final DistributedLockManager distributedManager;

   private final AtomicBoolean stoppingServer;

   public ReplicationPrimaryActivation(final ActiveMQServerImpl activeMQServer,
                                       final DistributedLockManager distributedManager,
                                       final ReplicationPrimaryPolicy policy) {
      this.activeMQServer = activeMQServer;
      this.policy = policy;
      this.replicationLock = new Object();
      this.distributedManager = distributedManager;
      this.stoppingServer = new AtomicBoolean();
   }

   /**
    * used for testing purposes.
    */
   public DistributedLockManager getDistributedManager() {
      return distributedManager;
   }

   @Override
   public void freezeConnections(RemotingService remotingService) {
      final ReplicationManager replicationManager = getReplicationManager();

      if (remotingService != null && replicationManager != null) {
         remotingService.freeze(null, replicationManager.getBackupTransportConnection());
      } else if (remotingService != null) {
         remotingService.freeze(null, null);
      }
   }

   @Override
   public void run() {
      try {
         // we have a common nodeId that we can share and coordinate with between peers
         if (policy.getCoordinationId() != null) {
            applyCoordinationId(policy.getCoordinationId(), activeMQServer);
         }
         final NodeManager nodeManager = activeMQServer.getNodeManager();
         if (nodeManager.getNodeActivationSequence() == NodeManager.NULL_NODE_ACTIVATION_SEQUENCE) {
            // persists an initial activation sequence
            nodeManager.writeNodeActivationSequence(0);
         }
         DistributedLock primaryLock;
         while (true) {
            distributedManager.start();
            try {
               primaryLock = tryActivate(nodeManager, distributedManager, logger);
               break;
            } catch (UnavailableStateException canRecoverEx) {
               distributedManager.stop();
            }
         }
         if (primaryLock == null) {
            distributedManager.stop();
            logger.info("This broker cannot become primary with NodeID = {}: restarting as backup", nodeManager.getNodeId());
            activeMQServer.setHAPolicy(policy.getBackupPolicy());
            return;
         }

         ensureSequentialAccessToNodeData(activeMQServer.toString(), nodeManager, distributedManager, logger);

         activeMQServer.initialisePart1(false);

         activeMQServer.initialisePart2(false);

         // must be registered before checking the caller
         primaryLock.addListener(this);

         // This control is placed here because initialisePart2 is going to load the journal that
         // could pause the JVM for enough time to lose lock ownership
         if (!primaryLock.isHeldByCaller()) {
            throw new IllegalStateException("This broker isn't active anymore, probably due to application pauses eg GC, OS etc: failing now");
         }

         activeMQServer.completeActivation(true);

         if (activeMQServer.getIdentity() != null) {
            ActiveMQServerLogger.LOGGER.serverIsActive(activeMQServer.getIdentity());
         } else {
            ActiveMQServerLogger.LOGGER.serverIsActive();
         }
      } catch (Exception e) {
         // async stop it, we don't need to await this to complete
         distributedManager.stop();
         ActiveMQServerLogger.LOGGER.initializationError(e);
         activeMQServer.callActivationFailureListeners(e);
      }
   }

   public static void applyCoordinationId(final String coordinationId,
                                          final ActiveMQServerImpl activeMQServer) throws Exception {
      Objects.requireNonNull(coordinationId);
      if (!activeMQServer.getNodeManager().isStarted()) {
         throw new IllegalStateException("NodeManager should be started");
      }
      final long activationSequence = activeMQServer.getNodeManager().getNodeActivationSequence();
      logger.info("Applying shared peer NodeID={} to enable coordinated activation", coordinationId);
      // REVISIT: this is quite clunky, also in backup activation, we just need new nodeID persisted!
      activeMQServer.resetNodeManager();
      final NodeManager nodeManager = activeMQServer.getNodeManager();
      nodeManager.start();
      nodeManager.setNodeID(coordinationId);
      nodeManager.stopBackup();
      // despite NM is restarted as "replicatedBackup" we need the last written activation sequence value in-memory
      final long freshActivationSequence = nodeManager.readNodeActivationSequence();
      assert freshActivationSequence == activationSequence;
   }

   @Override
   public ChannelHandler getActivationChannelHandler(final Channel channel, final Acceptor acceptorUsed) {
      if (stoppingServer.get()) {
         return null;
      }
      return packet -> {
         if (packet.getType() == PacketImpl.BACKUP_REGISTRATION) {
            onBackupRegistration(channel, acceptorUsed, (BackupRegistrationMessage) packet);
         }
      };
   }

   private void onBackupRegistration(final Channel channel,
                                     final Acceptor acceptorUsed,
                                     final BackupRegistrationMessage msg) {
      try {
         startAsyncReplication(channel.getConnection(), acceptorUsed.getClusterConnection(), msg.getConnector(), msg.isFailBackRequest());
      } catch (ActiveMQAlreadyReplicatingException are) {
         channel.send(new BackupReplicationStartFailedMessage(BackupReplicationStartFailedMessage.BackupRegistrationProblem.ALREADY_REPLICATING));
      } catch (ActiveMQException e) {
         logger.debug("Failed to process backup registration packet", e);
         channel.send(new BackupReplicationStartFailedMessage(BackupReplicationStartFailedMessage.BackupRegistrationProblem.EXCEPTION));
      }
   }

   private void startAsyncReplication(final CoreRemotingConnection remotingConnection,
                                      final ClusterConnection clusterConnection,
                                      final TransportConfiguration backupTransport,
                                      final boolean isFailBackRequest) throws ActiveMQException {
      synchronized (replicationLock) {
         if (replicationManager != null) {
            throw new ActiveMQAlreadyReplicatingException();
         }
         if (!activeMQServer.isStarted()) {
            throw new ActiveMQIllegalStateException();
         }
         final ReplicationFailureListener listener = new ReplicationFailureListener();
         remotingConnection.addCloseListener(listener);
         remotingConnection.addFailureListener(listener);
         final ReplicationManager replicationManager = new ReplicationManager(activeMQServer, remotingConnection, clusterConnection.getCallTimeout(), policy.getInitialReplicationSyncTimeout(), activeMQServer.getIOExecutorFactory());
         this.replicationManager = replicationManager;
         replicationManager.start();
         final Thread replicatingThread = new Thread(() -> replicate(replicationManager, clusterConnection, isFailBackRequest, backupTransport));
         replicatingThread.setName("async-replication-thread");
         replicatingThread.start();
      }
   }

   private void replicate(final ReplicationManager replicationManager,
                          final ClusterConnection clusterConnection,
                          final boolean isFailBackRequest,
                          final TransportConfiguration backupTransport) {
      try {
         final String nodeID = activeMQServer.getNodeID().toString();
         activeMQServer.getStorageManager().startReplication(replicationManager, activeMQServer.getPagingManager(), nodeID, isFailBackRequest && policy.isAllowAutoFailBack(), policy.getInitialReplicationSyncTimeout());

         clusterConnection.nodeAnnounced(System.currentTimeMillis(), nodeID, policy.getGroupName(), policy.getScaleDownGroupName(), new Pair<>(null, backupTransport), true);

         if (isFailBackRequest && policy.isAllowAutoFailBack()) {
            awaitBackupAnnouncementOnFailbackRequest(clusterConnection);
         }
      } catch (Exception e) {
         if (activeMQServer.getState() == STARTED) {
            /*
             * The reasoning here is that the exception was either caused by (1) the (interaction with) the backup, or
             * (2) by an IO Error at the storage. If (1), we can swallow the exception and ignore the replication
             * request. If (2) the primary will crash shortly.
             */
            ActiveMQServerLogger.LOGGER.errorStartingReplication(e);
         }
         try {
            ActiveMQServerImpl.stopComponent(replicationManager);
         } catch (Exception amqe) {
            ActiveMQServerLogger.LOGGER.errorStoppingReplication(amqe);
         } finally {
            synchronized (replicationLock) {
               if (this.replicationManager == replicationManager) {
                  this.replicationManager = null;
               }
            }
         }
      }
   }

   /**
    * This is handling awaiting backup announcement before trying to failover. This broker is an active backup broker
    * ready to restart as passive.
    */
   private void awaitBackupAnnouncementOnFailbackRequest(ClusterConnection clusterConnection) throws Exception {
      final String nodeID = activeMQServer.getNodeID().toString();
      final BackupTopologyListener topologyListener = new BackupTopologyListener(nodeID, clusterConnection.getConnector());
      clusterConnection.addClusterTopologyListener(topologyListener);
      try {
         if (topologyListener.waitForBackup()) {
            restartAsBackupAfterFailback();
         } else {
            ActiveMQServerLogger.LOGGER.failbackMissedBackupAnnouncement();
         }
      } finally {
         clusterConnection.removeClusterTopologyListener(topologyListener);
      }
   }

   private void restartAsBackupAfterFailback() throws Exception {
      if (stoppingServer.get()) {
         return;
      }
      final String coordinatedLockAndNodeId;
      final long inSyncReplicaActivation;
      synchronized (replicationLock) {
         if (stoppingServer.get()) {
            return;
         }
         final ReplicationManager replicationManager = this.replicationManager;
         if (replicationManager == null) {
            logger.warn("Failback interrupted");
            // we got a disconnection from the replica *before* stopping acceptors: better not failback!
            return;
         }
         // IMPORTANT: this is going to save server::fail to issue a replica connection failure (with failed == false)
         // because onReplicationConnectionClose fail-fast on stopping == true.
         if (!stoppingServer.compareAndSet(false, true)) {
            logger.info("Failback interrupted: server is already stopping");
            return;
         }
         coordinatedLockAndNodeId = activeMQServer.getNodeManager().getNodeId().toString();
         inSyncReplicaActivation = activeMQServer.getNodeManager().getNodeActivationSequence();
         // none can notice a concurrent drop of replica connection here: awaitNextCommittedActivationSequence defensively
         // wait FAILBACK_TIMEOUT_MILLIS, proceed as backup and compete to become active again
         activeMQServer.fail(true);
      }
      try {
         distributedManager.start();
         if (!awaitNextCommittedActivationSequence(distributedManager, coordinatedLockAndNodeId, inSyncReplicaActivation, FAILBACK_TIMEOUT_MILLIS, logger)) {
            logger.warn("Timed out waiting for failback server activation with NodeID = {}: and sequence > {}: after {}ms",
                         coordinatedLockAndNodeId, inSyncReplicaActivation, FAILBACK_TIMEOUT_MILLIS);
         }
      } catch (UnavailableStateException ignored) {
         logger.debug("Unavailable distributed manager while awaiting failback activation sequence: ignored", ignored);
      } finally {
         distributedManager.stop();
      }
      ActiveMQServerLogger.LOGGER.restartingReplicatedBackupAfterFailback();
      activeMQServer.setHAPolicy(policy.getBackupPolicy());
      activeMQServer.start();
   }

   private void asyncStopServer() {
      if (stoppingServer.get()) {
         return;
      }
      if (stoppingServer.compareAndSet(false, true)) {
         new Thread(() -> {
            try {
               activeMQServer.stop();
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorRestartingBackupServer(activeMQServer, e);
            }
         }).start();
      }
   }

   @Override
   public void onUnavailableLockEvent() {
      logger.error("Quorum UNAVAILABLE: async stopping broker.");
      asyncStopServer();
   }

   private final class ReplicationFailureListener implements FailureListener, CloseListener {

      @Override
      public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         onReplicationConnectionClose();
      }

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
         connectionFailed(me, failedOver);
      }

      @Override
      public void connectionClosed() {
         onReplicationConnectionClose();
      }
   }

   private void onReplicationConnectionClose() {
      Executor executor = activeMQServer.getThreadPool();
      if (executor != null) {
         if (stoppingServer.get()) {
            return;
         }
         executor.execute(() -> {
            synchronized (replicationLock) {
               if (replicationManager == null) {
                  return;
               }
               // we increment only if we are staying alive
               if (!stoppingServer.get() && STARTED.equals(activeMQServer.getState())) {
                  try {
                     ensureSequentialAccessToNodeData(activeMQServer.toString(), activeMQServer.getNodeManager(), distributedManager, logger);
                  } catch (Throwable fatal) {
                     logger.error("Unexpected exception: {} on attempted activation sequence increment; stopping server async", fatal.getLocalizedMessage(), fatal);
                     asyncStopServer();
                  }
               }
               // this is going to stop the replication manager
               final StorageManager storageManager = activeMQServer.getStorageManager();
               if (storageManager != null) {
                  storageManager.stopReplication();
               }
               replicationManager = null;
            }
         });
      }
   }

   @Override
   public void close(boolean permanently, boolean restarting) throws Exception {
      synchronized (replicationLock) {
         replicationManager = null;
      }
      distributedManager.stop();
      // To avoid a NPE cause by the stop
      final NodeManager nodeManager = activeMQServer.getNodeManager();
      if (nodeManager != null) {
         if (permanently) {
            nodeManager.crashPrimaryServer();
         } else {
            nodeManager.pausePrimaryServer();
         }
      }
   }

   @Override
   public void sendPrimaryIsStopping() {
      final ReplicationManager replicationManager = getReplicationManager();
      if (replicationManager == null) {
         return;
      }
      replicationManager.sendPrimaryIsStopping(ReplicationPrimaryIsStoppingMessage.PrimaryStopping.STOP_CALLED);
      // this pool gets a 'hard' shutdown, no need to manage the Future of this Runnable.
      activeMQServer.getScheduledPool().schedule(replicationManager::clearReplicationTokens, 30, TimeUnit.SECONDS);
   }

   @Override
   public ReplicationManager getReplicationManager() {
      synchronized (replicationLock) {
         return replicationManager;
      }
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
