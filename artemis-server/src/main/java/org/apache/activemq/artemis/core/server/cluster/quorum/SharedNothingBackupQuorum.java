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
package org.apache.activemq.artemis.core.server.cluster.quorum;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationPrimaryIsStoppingMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeLocator.BackupRegistrationListener;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class SharedNothingBackupQuorum implements Quorum, SessionFailureListener, BackupRegistrationListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public enum BACKUP_ACTIVATION {
      FAIL_OVER, FAILURE_RETRY, FAILURE_REPLICATING, ALREADY_REPLICATING, STOP;
   }

   private QuorumManager quorumManager;

   private String targetServerID = "";

   private final NodeManager nodeManager;

   private final ScheduledExecutorService scheduledPool;
   private final int quorumSize;

   private final int voteRetries;

   private final long voteRetryWait;

   private final Object voteGuard = new Object();

   private ClientSessionFactoryInternal sessionFactory;

   private CoreRemotingConnection connection;

   private final NetworkHealthCheck networkHealthCheck;

   private volatile boolean stopped = false;

   private final int quorumVoteWait;

   private volatile BACKUP_ACTIVATION signal;

   private ScheduledFuture<?> decisionGuard;

   private CountDownLatch latch;

   private final Object onConnectionFailureGuard = new Object();

   private final boolean failback;
   /**
    * This is a safety net in case the primary sends the first {@link ReplicationPrimaryIsStoppingMessage} with code
    * {@link ReplicationPrimaryIsStoppingMessage.PrimaryStopping#STOP_CALLED} and crashes before sending the second with
    * {@link ReplicationPrimaryIsStoppingMessage.PrimaryStopping#FAIL_OVER}.
    * <p>
    * If the second message does come within this dead line, we fail over anyway.
    */
   public static final int WAIT_TIME_AFTER_FIRST_PRIMARY_STOPPING_MSG = 60;

   public SharedNothingBackupQuorum(NodeManager nodeManager,
                                    ScheduledExecutorService scheduledPool,
                                    NetworkHealthCheck networkHealthCheck,
                                    int quorumSize,
                                    int voteRetries,
                                    long voteRetryWait,
                                    int quorumVoteWait,
                                    boolean failback) {
      this.scheduledPool = scheduledPool;
      this.quorumSize = quorumSize;
      this.latch = new CountDownLatch(1);
      this.nodeManager = nodeManager;
      this.networkHealthCheck = networkHealthCheck;
      this.voteRetries = voteRetries;
      this.voteRetryWait = voteRetryWait;
      this.quorumVoteWait = quorumVoteWait;
      this.failback = failback;
   }

   @Override
   public String getName() {
      return "SharedNothingBackupQuorum";
   }

   private void onConnectionFailure() {
      //we may get called as sessionFactory or connection listener
      synchronized (onConnectionFailureGuard) {
         if (signal == BACKUP_ACTIVATION.FAIL_OVER) {
            logger.debug("Replication connection failure with signal == FAIL_OVER: no need to take any action");
            if (networkHealthCheck != null && !networkHealthCheck.check()) {
               signal = BACKUP_ACTIVATION.FAILURE_RETRY;
            }
            return;
         }
         //given that we're going to latch.countDown(), there is no need to await any
         //scheduled task to complete
         stopForcedFailoverAfterDelay();
         if (!isPrimaryDown()) {
            //lost connection but don't know if primary is down so restart as backup as we can't replicate any more
            ActiveMQServerLogger.LOGGER.restartingAsBackupBasedOnQuorumVoteResults();
            signal = BACKUP_ACTIVATION.FAILURE_RETRY;
         } else {
            // primary is assumed to be down, backup fails-over
            ActiveMQServerLogger.LOGGER.failingOverBasedOnQuorumVoteResults();
            signal = BACKUP_ACTIVATION.FAIL_OVER;
         }

         /*
          * use NetworkHealthCheck to determine if node is isolated
          * if there are no addresses/urls configured then ignore and rely on quorum vote only
          */
         if (networkHealthCheck != null && !networkHealthCheck.isEmpty()) {
            if (networkHealthCheck.check()) {
               // primary is assumed to be down, backup fails-over
               signal = BACKUP_ACTIVATION.FAIL_OVER;
            } else {
               ActiveMQServerLogger.LOGGER.serverIsolatedOnNetwork();
               signal = BACKUP_ACTIVATION.FAILURE_RETRY;
            }
         }
         latch.countDown();
      }
   }

   public void primaryIDSet(String primaryID) {
      targetServerID = primaryID;
      nodeManager.setNodeID(primaryID);
   }

   @Override
   public void setQuorumManager(QuorumManager quorumManager) {
      this.quorumManager = quorumManager;
   }

   /**
    * if the node going down is the node we are replicating from then decide on an action.
    */
   @Override
   public void nodeDown(Topology topology, long eventUID, String nodeID) {
      // ignore it during a failback:
      // a failing slave close all connections but the one used for replication
      // triggering a nodeDown before the restarted primary receive a STOP_CALLED from it.
      // This can make primary to fire a useless quorum vote during a normal failback.
      if (!failback && targetServerID.equals(nodeID)) {
         onConnectionFailure();
      }
   }

   @Override
   public void nodeUp(Topology topology) {
      //noop: we are NOT interested on topology info coming from connections != this.connection
   }

   /**
    * if the connection to our replicated primary goes down then decide on an action
    */
   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      onConnectionFailure();
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
      causeExit(BACKUP_ACTIVATION.STOP);
   }

   /**
    * @param sessionFactory the session factory used to connect to the primary server
    */
   public void setSessionFactory(final ClientSessionFactoryInternal sessionFactory) {
      this.sessionFactory = sessionFactory;
      //belts and braces, there are circumstances where the connection listener doesn't get called but the session does.
      this.sessionFactory.addFailureListener(this);
      connection = (CoreRemotingConnection) sessionFactory.getConnection();
      connection.addFailureListener(this);
   }

   /**
    * Releases the latch, causing the backup activation thread to fail-over.
    * <p>
    * The use case is for when the 'live' has an orderly shutdown, in which case it informs the backup that it should
    * fail-over.
    */
   public synchronized void failOver(ReplicationPrimaryIsStoppingMessage.PrimaryStopping finalMessage) {
      removeListeners();
      signal = BACKUP_ACTIVATION.FAIL_OVER;
      switch (finalMessage) {

         case STOP_CALLED:
            scheduleForcedFailoverAfterDelay(latch);
            break;
         case FAIL_OVER:
            stopForcedFailoverAfterDelay();
            latch.countDown();
            break;
         default:
            logger.error("unsupported PrimaryStopping type: {}", finalMessage);
      }
   }

   @Override
   public void onBackupRegistrationFailed(boolean alreadyReplicating) {
      signal = alreadyReplicating ? BACKUP_ACTIVATION.ALREADY_REPLICATING : BACKUP_ACTIVATION.FAILURE_REPLICATING;
      latch.countDown();
   }

   private void removeListeners() {
      if (connection != null) {
         connection.removeFailureListener(this);
      }
      if (sessionFactory != null) {
         sessionFactory.removeFailureListener(this);
      }
   }

   /**
    * Called by the replicating backup (i.e. "SharedNothing" backup) to wait for the signal to fail-over or to stop.
    *
    * @return signal, indicating whether to stop or to fail-over
    */
   public BACKUP_ACTIVATION waitForStatusChange() {
      try {
         latch.await();
      } catch (InterruptedException e) {
         return BACKUP_ACTIVATION.STOP;
      }
      return signal;
   }

   /**
    * Cause the Activation thread to exit and the server to be stopped.
    *
    * @param explicitSignal the state we want to set the quorum manager to return
    */
   public synchronized void causeExit(BACKUP_ACTIVATION explicitSignal) {
      stopForcedFailoverAfterDelay();
      stopped = true;
      removeListeners();
      this.signal = explicitSignal;
      latch.countDown();
   }

   private synchronized void scheduleForcedFailoverAfterDelay(CountDownLatch signalChanged) {
      if (decisionGuard != null) {
         if (decisionGuard.isDone()) {
            logger.warn("A completed force failover task wasn't cleaned-up: a new one will be scheduled");
         } else if (!decisionGuard.cancel(false)) {
            logger.warn("Failed to cancel an existing uncompleted force failover task: a new one will be scheduled anyway");
         } else {
            logger.warn("Cancelled an existing uncompleted force failover task: a new one will be scheduled in its place");
         }
      }
      decisionGuard = scheduledPool.schedule(signalChanged::countDown, WAIT_TIME_AFTER_FIRST_PRIMARY_STOPPING_MSG, TimeUnit.SECONDS);
   }

   private synchronized boolean stopForcedFailoverAfterDelay() {
      if (decisionGuard == null) {
         return false;
      }
      final boolean stopped = decisionGuard.cancel(false);
      decisionGuard = null;
      return stopped;
   }

   public synchronized void reset() {
      stopForcedFailoverAfterDelay();
      latch = new CountDownLatch(1);
   }

   /**
    * we need to vote on the quorum to decide if we should become live
    *
    * @return the voting decision
    */
   private boolean isPrimaryDown() {
      // let's assume primary is not down
      if (stopped) {
         return false;
      }
      final int size = quorumSize == -1 ? quorumManager.getMaxClusterSize() : quorumSize;

      synchronized (voteGuard) {
         for (int voteAttempts = 0; voteAttempts < voteRetries && !stopped; voteAttempts++) {
            if (voteAttempts > 0) {
               try {
                  voteGuard.wait(voteRetryWait);
               } catch (InterruptedException e) {
                  //nothing to do here
               }
            }
            if (!quorumManager.hasPrimary(targetServerID, size, quorumVoteWait, TimeUnit.SECONDS)) {
               return true;
            }
         }
      }
      return false;
   }
}
