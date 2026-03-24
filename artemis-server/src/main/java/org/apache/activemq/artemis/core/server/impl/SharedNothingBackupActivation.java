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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationLiveIsStoppingMessage;
import org.apache.activemq.artemis.core.replication.ReplicationEndpoint;
import org.apache.activemq.artemis.core.replication.ReplicationEndpoint.ReplicationEndpointEventListener;
import org.apache.activemq.artemis.core.server.ActivationParams;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LiveNodeLocator;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicaPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ScaleDownPolicy;
import org.apache.activemq.artemis.core.server.cluster.qourum.SharedNothingBackupQuorum;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.jboss.logging.Logger;

import static org.apache.activemq.artemis.core.server.cluster.qourum.SharedNothingBackupQuorum.BACKUP_ACTIVATION.FAILURE_REPLICATING;
import static org.apache.activemq.artemis.core.server.cluster.qourum.SharedNothingBackupQuorum.BACKUP_ACTIVATION.FAIL_OVER;
import static org.apache.activemq.artemis.core.server.cluster.qourum.SharedNothingBackupQuorum.BACKUP_ACTIVATION.STOP;

public final class SharedNothingBackupActivation extends Activation implements ReplicationEndpointEventListener {

   private static final Logger logger = Logger.getLogger(SharedNothingBackupActivation.class);

   //this is how we act when we start as a backup
   private ReplicaPolicy replicaPolicy;

   //this is the endpoint where we replicate too
   private ReplicationEndpoint replicationEndpoint;

   private final ActiveMQServerImpl activeMQServer;
   private SharedNothingBackupQuorum backupQuorum;
   private final boolean attemptFailBack;
   private final Map<String, Object> activationParams;
   private final IOCriticalErrorListener ioCriticalErrorListener;
   private String nodeID;
   ClusterControl clusterControl;
   private boolean closed;
   private volatile boolean backupUpToDate = true;
   private final NetworkHealthCheck networkHealthCheck;

   private final ReusableLatch backupSyncLatch = new ReusableLatch(0);

   public SharedNothingBackupActivation(ActiveMQServerImpl activeMQServer,
                                        boolean attemptFailBack,
                                        Map<String, Object> activationParams,
                                        IOCriticalErrorListener ioCriticalErrorListener,
                                        ReplicaPolicy replicaPolicy,
                                        NetworkHealthCheck networkHealthCheck) {
      this.activeMQServer = activeMQServer;
      this.attemptFailBack = attemptFailBack;
      this.activationParams = activationParams;
      this.ioCriticalErrorListener = ioCriticalErrorListener;
      this.replicaPolicy = replicaPolicy;
      backupSyncLatch.setCount(1);
      this.networkHealthCheck = networkHealthCheck;
   }

   public void init() throws Exception {
      assert replicationEndpoint == null;
      activeMQServer.resetNodeManager();
      backupUpToDate = false;
      replicationEndpoint = new ReplicationEndpoint(activeMQServer, attemptFailBack, this);
   }

   @Override
   public void run() {
      try {

         logger.trace("SharedNothingBackupActivation..start");
         synchronized (activeMQServer) {
            activeMQServer.setState(ActiveMQServerImpl.SERVER_STATE.STARTED);
         }
         // move all data away:
         activeMQServer.getNodeManager().stop();
         activeMQServer.moveServerData(replicaPolicy.getMaxSavedReplicatedJournalsSize());
         activeMQServer.getNodeManager().start();
         synchronized (this) {
            if (closed) {
               logger.trace("SharedNothingBackupActivation is closed, ignoring activation!");
               return;
            }
         }

         boolean scalingDown = replicaPolicy.getScaleDownPolicy() != null && replicaPolicy.getScaleDownPolicy().isEnabled();

         if (!activeMQServer.initialisePart1(scalingDown)) {
            if (logger.isTraceEnabled()) {
               logger.trace("could not initialize part1 " + scalingDown);
            }
            return;
         }

         synchronized (this) {
            if (closed)
               return;
            backupQuorum = new SharedNothingBackupQuorum(activeMQServer.getNodeManager(), activeMQServer.getScheduledPool(), networkHealthCheck, replicaPolicy.getQuorumSize(), replicaPolicy.getVoteRetries(), replicaPolicy.getVoteRetryWait(), replicaPolicy.getQuorumVoteWait(), attemptFailBack);
            activeMQServer.getClusterManager().getQuorumManager().registerQuorum(backupQuorum);
            activeMQServer.getClusterManager().getQuorumManager().registerQuorumHandler(new ServerConnectVoteHandler(activeMQServer));
         }

         //use a Node Locator to connect to the cluster
         LiveNodeLocator nodeLocator;
         if (activationParams.get(ActivationParams.REPLICATION_ENDPOINT) != null) {
            TopologyMember member = (TopologyMember) activationParams.get(ActivationParams.REPLICATION_ENDPOINT);
            nodeLocator = new NamedNodeIdNodeLocator(member.getNodeId(), new Pair<>(member.getLive(), member.getBackup()));
         } else {
            nodeLocator = replicaPolicy.getGroupName() == null ? new AnyLiveNodeLocatorForReplication(backupQuorum, activeMQServer, replicaPolicy.getRetryReplicationWait()) : new NamedLiveNodeLocatorForReplication(replicaPolicy.getGroupName(), backupQuorum, replicaPolicy.getRetryReplicationWait());
         }
         ClusterController clusterController = activeMQServer.getClusterManager().getClusterController();
         clusterController.addClusterTopologyListenerForReplication(nodeLocator);

         logger.trace("Waiting on cluster connection");
         clusterController.awaitConnectionToReplicationCluster();

         logger.trace("Cluster Connected");

         clusterController.addIncomingInterceptorForReplication(new ReplicationError(nodeLocator));

         logger.debug("Starting backup manager");
         activeMQServer.getBackupManager().start();

         replicationEndpoint.setExecutor(activeMQServer.getExecutorFactory().getExecutor());
         EndpointConnector endpointConnector = new EndpointConnector();

         logger.debug("Starting Backup Server");
         ActiveMQServerLogger.LOGGER.backupServerStarted(activeMQServer.getVersion().getFullVersion(), activeMQServer.getNodeManager().getNodeId());
         activeMQServer.setState(ActiveMQServerImpl.SERVER_STATE.STARTED);

         if (logger.isTraceEnabled())
            logger.trace("Setting server state as started");

         SharedNothingBackupQuorum.BACKUP_ACTIVATION signal;
         do {
            if (closed) {
               logger.debug("Activation is closed, so giving up");
               return;
            }

            if (logger.isTraceEnabled()) {
               logger.trace("looking up the node through nodeLocator.locateNode()");
            }
            //locate the first live server to try to replicate
            nodeLocator.locateNode();
            Pair<TransportConfiguration, TransportConfiguration> possibleLive = nodeLocator.getLiveConfiguration();
            nodeID = nodeLocator.getNodeID();
            if (logger.isDebugEnabled()) {
               logger.debug("Connecting towards a possible live, connection information=" + possibleLive + ", nodeID=" + nodeID);
            }

            //in a normal (non failback) scenario if we couldn't find our live server we should fail
            if (!attemptFailBack) {
               logger.debug("attemptFailback=false, nodeID=" + nodeID);

               //this shouldn't happen
               if (nodeID == null) {
                  logger.debug("Throwing a RuntimeException as nodeID==null ant attemptFailback=false");
                  throw new RuntimeException("Could not establish the connection");
               }
               activeMQServer.getNodeManager().setNodeID(nodeID);
            }

            if (possibleLive != null) {
               clusterControl = tryConnectToNodeInReplicatedCluster(clusterController, possibleLive.getA());
               if (clusterControl == null) {
                  clusterControl = tryConnectToNodeInReplicatedCluster(clusterController, possibleLive.getB());
               }
            } else {
               clusterControl = null;
            }
            if (clusterControl == null) {

               if (logger.isTraceEnabled()) {
                  logger.trace("sleeping " + clusterController.getRetryIntervalForReplicatedCluster() + " it should retry");
               }
               //its ok to retry here since we haven't started replication yet
               //it may just be the server has gone since discovery
               Thread.sleep(clusterController.getRetryIntervalForReplicatedCluster());
               signal = SharedNothingBackupQuorum.BACKUP_ACTIVATION.ALREADY_REPLICATING;
               continue;
            }

            activeMQServer.getThreadPool().execute(endpointConnector);
            /**
             * Wait for a signal from the the quorum manager, at this point if replication has been successful we can
             * fail over or if there is an error trying to replicate (such as already replicating) we try the
             * process again on the next live server.  All the action happens inside {@link BackupQuorum}
             */
            signal = backupQuorum.waitForStatusChange();

            if (logger.isTraceEnabled()) {
               logger.trace("Got a signal " + signal + " through backupQuorum.waitForStatusChange()");
            }

            /**
             * replicationEndpoint will be holding lots of open files. Make sure they get
             * closed/sync'ed.
             */
            ActiveMQServerImpl.stopComponent(replicationEndpoint);
            // time to give up
            if (!activeMQServer.isStarted() || signal == STOP) {
               if (logger.isTraceEnabled()) {
                  logger.trace("giving up on the activation:: activemqServer.isStarted=" + activeMQServer.isStarted() + " while signal = " + signal);
               }
               return;
            } else if (signal == FAIL_OVER) {
               // time to fail over
               if (logger.isTraceEnabled()) {
                  logger.trace("signal == FAIL_OVER, breaking the loop");
               }
               break;
            } else if (signal == SharedNothingBackupQuorum.BACKUP_ACTIVATION.FAILURE_REPLICATING || signal == SharedNothingBackupQuorum.BACKUP_ACTIVATION.FAILURE_RETRY) {
               // something has gone badly run restart from scratch
               if (logger.isTraceEnabled()) {
                  logger.trace("Starting a new thread to stop the server!");
               }

               final SharedNothingBackupQuorum.BACKUP_ACTIVATION signalToStop = signal;

               Thread startThread = new Thread(new Runnable() {
                  @Override
                  public void run() {
                     try {
                        if (logger.isTraceEnabled()) {
                           logger.trace("Calling activeMQServer.stop() as initialization failed");
                        }
                        if (activeMQServer.getState() != ActiveMQServer.SERVER_STATE.STOPPED &&
                            activeMQServer.getState() != ActiveMQServer.SERVER_STATE.STOPPING) {

                           if (signalToStop == SharedNothingBackupQuorum.BACKUP_ACTIVATION.FAILURE_RETRY) {
                              activeMQServer.stop(false);
                              logger.trace("The server was shutdown for a network isolation, we keep retrying");
                              activeMQServer.start();
                           } else {
                              activeMQServer.stop();
                           }
                        }
                     } catch (Exception e) {
                        ActiveMQServerLogger.LOGGER.errorRestartingBackupServer(e, activeMQServer);
                     }
                  }
               });
               startThread.start();
               return;
            }
            //ok, this live is no good, let's reset and try again
            //close this session factory, we're done with it
            clusterControl.close();
            backupQuorum.reset();
            if (replicationEndpoint.getChannel() != null) {
               replicationEndpoint.getChannel().close();
               replicationEndpoint.setChannel(null);
            }
         }
         while (signal == SharedNothingBackupQuorum.BACKUP_ACTIVATION.ALREADY_REPLICATING);

         if (logger.isTraceEnabled()) {
            logger.trace("Activation loop finished, current signal = " + signal);
         }

         activeMQServer.getClusterManager().getQuorumManager().unRegisterQuorum(backupQuorum);

         if (!isRemoteBackupUpToDate()) {
            logger.debug("throwing exception for !isRemoteBackupUptoDate");
            throw ActiveMQMessageBundle.BUNDLE.backupServerNotInSync();
         }

         if (logger.isTraceEnabled()) {
            logger.trace("@@@ setReplicaPolicy::" + replicaPolicy);
         }

         replicaPolicy.getReplicatedPolicy().setReplicaPolicy(replicaPolicy);
         activeMQServer.setHAPolicy(replicaPolicy.getReplicatedPolicy());

         synchronized (activeMQServer) {
            if (!activeMQServer.isStarted()) {
               logger.trace("Server is stopped, giving up right before becomingLive");
               return;
            }
            ActiveMQServerLogger.LOGGER.becomingLive(activeMQServer);
            logger.trace("stop backup");
            activeMQServer.getNodeManager().stopBackup();
            logger.trace("start store manager");
            activeMQServer.getStorageManager().start();
            logger.trace("activated");
            activeMQServer.getBackupManager().activated();
            if (scalingDown) {
               logger.trace("Scalling down...");
               activeMQServer.initialisePart2(true);
            } else {
               logger.trace("Setting up new activation");
               activeMQServer.setActivation(new SharedNothingLiveActivation(activeMQServer, replicaPolicy.getReplicatedPolicy()));
               logger.trace("initialize part 2");
               activeMQServer.initialisePart2(false);

               if (activeMQServer.getIdentity() != null) {
                  ActiveMQServerLogger.LOGGER.serverIsLive(activeMQServer.getIdentity());
               } else {
                  ActiveMQServerLogger.LOGGER.serverIsLive();
               }

            }

            logger.trace("completeActivation at the end");

            activeMQServer.completeActivation(true);
         }
      } catch (Exception e) {
         if (logger.isTraceEnabled()) {
            logger.trace(e.getMessage() + ", serverStarted=" + activeMQServer.isStarted(), e);
         }
         if ((e instanceof InterruptedException || e instanceof IllegalStateException) && !activeMQServer.isStarted())
            // do not log these errors if the server is being stopped.
            return;
         ActiveMQServerLogger.LOGGER.initializationError(e);
      }
   }

   private static ClusterControl tryConnectToNodeInReplicatedCluster(ClusterController clusterController, TransportConfiguration tc) {
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("Calling clusterController.connectToNodeInReplicatedCluster(" + tc + ")");
         }
         if (tc != null) {
            return clusterController.connectToNodeInReplicatedCluster(tc);
         }
      } catch (Exception e) {
         logger.debug(e.getMessage(), e);
      }
      return null;
   }

   @Override
   public void close(final boolean permanently, boolean restarting) throws Exception {
      synchronized (this) {
         if (backupQuorum != null)
            backupQuorum.causeExit(STOP);
         replicationEndpoint = null;
         closed = true;
      }
      //we have to check as the server policy may have changed
      if (activeMQServer.getHAPolicy().isBackup()) {
         // To avoid a NPE cause by the stop
         NodeManager nodeManagerInUse = activeMQServer.getNodeManager();

         activeMQServer.interruptActivationThread(nodeManagerInUse);

         if (nodeManagerInUse != null) {
            nodeManagerInUse.stopBackup();
         }
      }
   }

   @Override
   public void preStorageClose() throws Exception {
      if (replicationEndpoint != null) {
         replicationEndpoint.stop();
      }
   }

   @Override
   public JournalLoader createJournalLoader(PostOffice postOffice,
                                            PagingManager pagingManager,
                                            StorageManager storageManager,
                                            QueueFactory queueFactory,
                                            NodeManager nodeManager,
                                            ManagementService managementService,
                                            GroupingHandler groupingHandler,
                                            Configuration configuration,
                                            ActiveMQServer parentServer) throws ActiveMQException {
      if (replicaPolicy.getScaleDownPolicy() != null && replicaPolicy.getScaleDownPolicy().isEnabled()) {
         return new BackupRecoveryJournalLoader(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration, parentServer, ScaleDownPolicy.getScaleDownConnector(replicaPolicy.getScaleDownPolicy(), activeMQServer), activeMQServer.getClusterManager().getClusterController());
      } else {
         return super.createJournalLoader(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration, parentServer);
      }
   }

   @Override
   public void haStarted() {
      activeMQServer.getClusterManager().getClusterController().setReplicatedClusterName(replicaPolicy.getClusterName());
   }

   /**
    * Wait for backup synchronization when using synchronization
    *
    * @param timeout
    * @param unit
    * @return {@code true} if the server was already initialized or if it was initialized within the
    * timeout period, {@code false} otherwise.
    * @throws InterruptedException
    * @see java.util.concurrent.CountDownLatch#await(long, TimeUnit)
    */
   public boolean waitForBackupSync(long timeout, TimeUnit unit) throws InterruptedException {
      return backupSyncLatch.await(timeout, unit);
   }

   /**
    * Live has notified this server that it is going to stop.
    */
   public void failOver(final ReplicationLiveIsStoppingMessage.LiveStopping finalMessage) {
      if (finalMessage == null) {
         backupQuorum.causeExit(FAILURE_REPLICATING);
      } else {
         backupQuorum.failOver(finalMessage);
      }
   }

   public ReplicationEndpoint getReplicationEndpoint() {
      return replicationEndpoint;
   }

   /**
    * Whether a remote backup server was in sync with its live server. If it was not in sync, it may
    * not take over the live's functions.
    * <p>
    * A local backup server or a live server should always return {@code true}
    *
    * @return whether the backup is up-to-date, if the server is not a backup it always returns
    * {@code true}.
    */
   public boolean isRemoteBackupUpToDate() {
      return backupUpToDate;
   }

   @Override
   public void onLiveNodeId(String nodeId) {
      backupQuorum.liveIDSet(nodeId);
   }

   @Override
   public void onRemoteBackupUpToDate(String nodeId, long ignoredActivationSequence) {
      backupQuorum.liveIDSet(nodeId);
      activeMQServer.getBackupManager().announceBackup();
      backupUpToDate = true;
      backupSyncLatch.countDown();
   }

   /**
    * @throws ActiveMQException
    */
   @Override
   public void onLiveStopping(ReplicationLiveIsStoppingMessage.LiveStopping finalMessage) throws ActiveMQException {
      if (logger.isTraceEnabled()) {
         logger.trace("Remote fail-over, got message=" + finalMessage + ", backupUpToDate=" +
                         backupUpToDate);
      }
      if (!activeMQServer.getHAPolicy().isBackup() || activeMQServer.getHAPolicy().isSharedStore()) {
         throw new ActiveMQInternalErrorException();
      }

      if (!backupUpToDate) {
         failOver(null);
      } else {
         failOver(finalMessage);
      }
   }

   private class EndpointConnector implements Runnable {

      @Override
      public void run() {
         try {
            //we should only try once, if its not there we should move on.
            clusterControl.getSessionFactory().setReconnectAttempts(0);
            backupQuorum.setSessionFactory(clusterControl.getSessionFactory());
            //get the connection and request replication to live
            clusterControl.authorize();
            connectToReplicationEndpoint(clusterControl);
            replicationEndpoint.start();
            clusterControl.announceReplicatingBackupToLive(attemptFailBack, replicaPolicy.getClusterName());
         } catch (Exception e) {
            //we shouldn't stop the server just mark the connector as tried and unavailable
            ActiveMQServerLogger.LOGGER.replicationStartProblem(e);
            backupQuorum.causeExit(FAILURE_REPLICATING);
         }
      }

      private synchronized ReplicationEndpoint connectToReplicationEndpoint(final ClusterControl control) throws Exception {
         if (!activeMQServer.isStarted())
            return null;
         if (!activeMQServer.getHAPolicy().isBackup()) {
            throw ActiveMQMessageBundle.BUNDLE.serverNotBackupServer();
         }

         Channel replicationChannel = control.createReplicationChannel();

         replicationChannel.setHandler(replicationEndpoint);

         if (replicationEndpoint.getChannel() != null) {
            throw ActiveMQMessageBundle.BUNDLE.alreadyHaveReplicationServer();
         }

         replicationEndpoint.setChannel(replicationChannel);

         return replicationEndpoint;
      }
   }

   @Override
   public boolean isReplicaSync() {
      return isRemoteBackupUpToDate();
   }
}
