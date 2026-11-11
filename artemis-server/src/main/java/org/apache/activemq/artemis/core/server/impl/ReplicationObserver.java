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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationPrimaryIsStoppingMessage;
import org.apache.activemq.artemis.core.replication.ReplicationEndpoint;
import org.apache.activemq.artemis.core.server.NodeLocator.BackupRegistrationListener;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.BackupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

final class ReplicationObserver implements ClusterTopologyListener, SessionFailureListener, BackupRegistrationListener, ReplicationEndpoint.ReplicationEndpointEventListener, AutoCloseable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public enum ReplicationFailure {
      VoluntaryFailOver, BackupNotInSync, NonVoluntaryFailover, RegistrationError, AlreadyReplicating, ClosedObserver, WrongNodeId, WrongActivationSequence
   }

   private final NodeManager nodeManager;
   private final BackupManager backupManager;
   private final ScheduledExecutorService scheduledPool;
   private final boolean failback;
   private final String expectedNodeID;
   private final CompletableFuture<ReplicationFailure> replicationFailure;

   @GuardedBy("this")
   private ClientSessionFactoryInternal sessionFactory;
   @GuardedBy("this")
   private CoreRemotingConnection connection;
   @GuardedBy("this")
   private ScheduledFuture<?> forcedFailover;

   private volatile String primaryID;
   private volatile boolean backupUpToDate;
   private volatile boolean closed;

   /**
    * This is a safety net in case the primary sends the first {@link ReplicationPrimaryIsStoppingMessage} with code
    * {@link ReplicationPrimaryIsStoppingMessage.PrimaryStopping#STOP_CALLED} and crashes before sending the second with
    * {@link ReplicationPrimaryIsStoppingMessage.PrimaryStopping#FAIL_OVER}.
    * <p>
    * If the second message comes within this dead-line we fail over anyway.
    */
   public static final int WAIT_TIME_AFTER_FIRST_PRIMARY_STOPPING_MSG = 60;

   private ReplicationObserver(final NodeManager nodeManager,
                               final BackupManager backupManager,
                               final ScheduledExecutorService scheduledPool,
                               final boolean failback,
                               final String expectedNodeID) {
      this.nodeManager = nodeManager;
      this.backupManager = backupManager;
      this.scheduledPool = scheduledPool;
      this.failback = failback;
      this.expectedNodeID = expectedNodeID;
      this.replicationFailure = new CompletableFuture<>();

      this.sessionFactory = null;
      this.connection = null;
      this.forcedFailover = null;

      this.primaryID = null;
      this.backupUpToDate = false;
      this.closed = false;
   }

   public static ReplicationObserver failbackObserver(final NodeManager nodeManager,
                                                      final BackupManager backupManager,
                                                      final ScheduledExecutorService scheduledPool,
                                                      final String expectedNodeID) {
      Objects.requireNonNull(expectedNodeID);
      return new ReplicationObserver(nodeManager, backupManager, scheduledPool, true, expectedNodeID);
   }

   public static ReplicationObserver failoverObserver(final NodeManager nodeManager,
                                                      final BackupManager backupManager,
                                                      final ScheduledExecutorService scheduledPool) {
      return new ReplicationObserver(nodeManager, backupManager, scheduledPool, false, null);
   }

   private void onPrimaryDown(boolean voluntaryFailover) {
      if (closed || replicationFailure.isDone()) {
         return;
      }
      synchronized (this) {
         if (closed || replicationFailure.isDone()) {
            return;
         }
         stopForcedFailoverAfterDelay();
         unlistenConnectionFailures();
         if (!isRemoteBackupUpToDate()) {
            replicationFailure.complete(ReplicationFailure.BackupNotInSync);
         } else if (voluntaryFailover) {
            replicationFailure.complete(ReplicationFailure.VoluntaryFailOver);
         } else {
            replicationFailure.complete(ReplicationFailure.NonVoluntaryFailover);
         }
      }
   }

   @Override
   public void nodeDown(long eventUID, String nodeID) {
      // ignore it during a failback:
      // a failing slave close all connections but the one used for replication
      // triggering a nodeDown before the restarted primary receive a STOP_CALLED from it.
      // This can make primary to fire a useless quorum vote during a normal failback.
      if (failback) {
         return;
      }
      if (nodeID.equals(primaryID)) {
         onPrimaryDown(false);
      }
   }

   @Override
   public void nodeUP(TopologyMember member, boolean last) {
   }

   /**
    * if the connection to our replicated primary goes down then decide on an action
    */
   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      onPrimaryDown(false);
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
      connectionFailed(me, failedOver);
   }

   @Override
   public void beforeReconnect(ActiveMQException exception) {
      //noop
   }

   @Override
   public void close() {
      if (closed) {
         return;
      }
      synchronized (this) {
         if (closed) {
            return;
         }
         unlistenConnectionFailures();
         closed = true;
         replicationFailure.complete(ReplicationFailure.ClosedObserver);
      }
   }

   /**
    * @param primarySessionFactory the session factory used to connect to the primary server
    */
   public synchronized void listenConnectionFailuresOf(final ClientSessionFactoryInternal primarySessionFactory) {
      if (closed) {
         throw new IllegalStateException("the observer is closed: cannot listen to any failures");
      }
      if (sessionFactory != null || connection != null) {
         throw new IllegalStateException("this observer is already listening to other session factory failures");
      }
      this.sessionFactory = primarySessionFactory;
      //belts and braces, there are circumstances where the connection listener doesn't get called but the session does.
      this.sessionFactory.addFailureListener(this);
      connection = (CoreRemotingConnection) primarySessionFactory.getConnection();
      connection.addFailureListener(this);
   }

   public synchronized void unlistenConnectionFailures() {
      if (connection != null) {
         connection.removeFailureListener(this);
         connection = null;
      }
      if (sessionFactory != null) {
         sessionFactory.removeFailureListener(this);
         sessionFactory = null;
      }
   }

   @Override
   public void onBackupRegistrationFailed(boolean alreadyReplicating) {
      if (closed || replicationFailure.isDone()) {
         return;
      }
      synchronized (this) {
         if (closed || replicationFailure.isDone()) {
            return;
         }
         stopForcedFailoverAfterDelay();
         unlistenConnectionFailures();
         replicationFailure.complete(alreadyReplicating ? ReplicationFailure.AlreadyReplicating : ReplicationFailure.RegistrationError);
      }
   }

   public ReplicationFailure awaitReplicationFailure() {
      try {
         return replicationFailure.get();
      } catch (Throwable e) {
         return ReplicationFailure.ClosedObserver;
      }
   }

   private synchronized void scheduleForcedFailoverAfterDelay() {
      if (forcedFailover != null) {
         return;
      }
      forcedFailover = scheduledPool.schedule(() -> onPrimaryDown(false), WAIT_TIME_AFTER_FIRST_PRIMARY_STOPPING_MSG, TimeUnit.SECONDS);
   }

   private synchronized void stopForcedFailoverAfterDelay() {
      if (forcedFailover == null) {
         return;
      }
      forcedFailover.cancel(false);
      forcedFailover = null;
   }

   @Override
   public void onRemoteBackupUpToDate(String nodeId, long activationSequence) {
      if (backupUpToDate || closed || replicationFailure.isDone()) {
         return;
      }
      synchronized (this) {
         if (backupUpToDate || closed || replicationFailure.isDone()) {
            return;
         }
         if (!validateNodeId(nodeId)) {
            stopForcedFailoverAfterDelay();
            unlistenConnectionFailures();
            replicationFailure.complete(ReplicationFailure.WrongNodeId);
            return;
         }
         if (primaryID == null) {
            primaryID = nodeId;
         }
         if (activationSequence <= 0) {
            /*
             * NOTE: activationSequence == 0 is still illegal because the primary has to increase the sequence before
             * replicating.
             */
            stopForcedFailoverAfterDelay();
            unlistenConnectionFailures();
            logger.error("Illegal activation sequence {} from NodeID = {}", activationSequence, nodeId);
            replicationFailure.complete(ReplicationFailure.WrongActivationSequence);
            return;
         }
         nodeManager.setNodeID(nodeId);
         nodeManager.setNodeActivationSequence(activationSequence);
         // persists nodeID and nodeActivationSequence
         nodeManager.stopBackup();
         backupManager.announceBackup();
         backupUpToDate = true;
      }
   }

   public boolean isBackupUpToDate() {
      return backupUpToDate;
   }

   public String getPrimaryID() {
      return primaryID;
   }

   private boolean validateNodeId(String nodeID) {
      if (nodeID == null) {
         return false;
      }
      final String existingNodeId = this.primaryID;
      if (existingNodeId == null) {
         if (!failback) {
            return true;
         }
         return nodeID.equals(expectedNodeID);
      }
      return existingNodeId.equals(nodeID);
   }

   @Override
   public void onPrimaryNodeId(String nodeId) {
      if (closed || replicationFailure.isDone()) {
         return;
      }
      final String existingNodeId = this.primaryID;
      if (existingNodeId != null && existingNodeId.equals(nodeId)) {
         return;
      }
      synchronized (this) {
         if (closed || replicationFailure.isDone()) {
            return;
         }
         if (!validateNodeId(nodeId)) {
            stopForcedFailoverAfterDelay();
            unlistenConnectionFailures();
            replicationFailure.complete(ReplicationFailure.WrongNodeId);
         } else if (primaryID == null) {
            // just store it locally: if is stored on the node manager
            // it will be persisted on broker's stop while data is not yet in sync
            primaryID = nodeId;
         }
      }
   }

   public boolean isRemoteBackupUpToDate() {
      return backupUpToDate;
   }

   @Override
   public void onPrimaryStopping(ReplicationPrimaryIsStoppingMessage.PrimaryStopping finalMessage) {
      if (closed || replicationFailure.isDone()) {
         return;
      }
      synchronized (this) {
         if (closed || replicationFailure.isDone()) {
            return;
         }
         switch (finalMessage) {
            case STOP_CALLED:
               scheduleForcedFailoverAfterDelay();
               break;
            case FAIL_OVER:
               onPrimaryDown(true);
               break;
            default:
               logger.error("unsupported PrimaryStopping type: {}", finalMessage);
         }
      }
   }
}
