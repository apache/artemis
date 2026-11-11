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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQLockAcquisitionTimeoutException;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.NodeManager.LockListener;
import org.apache.activemq.artemis.core.server.NodeManager.NodeManagerException;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.ha.ScaleDownPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStoreBackupPolicy;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class SharedStoreBackupActivation extends Activation {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   //this is how we act as a backup
   private final SharedStoreBackupPolicy sharedStoreBackupPolicy;

   private final ActiveMQServerImpl activeMQServer;

   private final Object failbackCheckerGuard = new Object();

   private boolean cancelFailBackChecker;

   private LockListener activeLockListener;

   private final IOCriticalErrorListener ioCriticalErrorListener;

   public SharedStoreBackupActivation(ActiveMQServerImpl server,
                                      SharedStoreBackupPolicy sharedStoreBackupPolicy,
                                      IOCriticalErrorListener ioCriticalErrorListener) {
      this.activeMQServer = server;
      this.sharedStoreBackupPolicy = sharedStoreBackupPolicy;
      this.ioCriticalErrorListener = ioCriticalErrorListener;
      synchronized (failbackCheckerGuard) {
         cancelFailBackChecker = false;
      }
   }

   @Override
   public void run() {
      try {
         registerActiveLockListener(activeMQServer.getNodeManager());

         activeMQServer.getNodeManager().startBackup();

         ScaleDownPolicy scaleDownPolicy = sharedStoreBackupPolicy.getScaleDownPolicy();

         boolean scalingDown = scaleDownPolicy != null && scaleDownPolicy.isEnabled();

         if (!activeMQServer.initialisePart1(scalingDown)) {
            return;
         }

         activeMQServer.getBackupManager().start();

         activeMQServer.setState(ActiveMQServerImpl.SERVER_STATE.STARTED);

         ActiveMQServerLogger.LOGGER.backupServerStarted(activeMQServer.getVersion().getFullVersion(), activeMQServer.getNodeManager().getNodeId());

         activeMQServer.getNodeManager().awaitPrimaryNode();

         sharedStoreBackupPolicy.getSharedStorePrimaryPolicy().setSharedStoreBackupPolicy(sharedStoreBackupPolicy);

         activeMQServer.setHAPolicy(sharedStoreBackupPolicy.getSharedStorePrimaryPolicy());

         //activeMQServer.configuration.getHAPolicy().setPolicyType(HAPolicy.POLICY_TYPE.SHARED_STORE);

         activeMQServer.getBackupManager().activated();
         if (activeMQServer.getState() != ActiveMQServerImpl.SERVER_STATE.STARTED) {
            return;
         }

         activeMQServer.initialisePart2(scalingDown);

         activeMQServer.completeActivation(false);

         if (scalingDown) {
            if (!restarting.compareAndSet(false, true)) {
               return;
            }
            unregisterActiveLockListener(activeMQServer.getNodeManager());

            ActiveMQServerLogger.LOGGER.backupServerScaledDown();
            Thread t = new Thread(() -> {
               try {
                  activeMQServer.stop();
                  //we are shared store but if we were started by a parent server then we shouldn't restart
                  if (sharedStoreBackupPolicy.isRestartBackup()) {
                     activeMQServer.start();
                  }
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.serverRestartWarning(e);
               }
            });
            t.start();
            return;
         } else {
            ActiveMQServerLogger.LOGGER.backupServerIsActive();

            activeMQServer.getNodeManager().releaseBackup();
         }
         if (sharedStoreBackupPolicy.isAllowAutoFailBack() && ActiveMQServerImpl.SERVER_STATE.STOPPING != activeMQServer.getState() && ActiveMQServerImpl.SERVER_STATE.STOPPED != activeMQServer.getState()) {
            startFailbackChecker();
         }
      } catch (NodeManagerException nodeManagerException) {
         if (nodeManagerException.getCause() instanceof ClosedChannelException) {
            // this is ok, we are being stopped
            return;
         }
         if (nodeManagerException.getCause() instanceof ActiveMQLockAcquisitionTimeoutException) {
            ActiveMQServerLogger.LOGGER.initializationError(nodeManagerException.getCause());
            return;
         }
         unregisterActiveLockListener(activeMQServer.getNodeManager());
         ioCriticalErrorListener.onIOException(nodeManagerException, nodeManagerException.getMessage(), null);
      } catch (ClosedChannelException | InterruptedException e) {
         // these are ok, we are being stopped
      } catch (Exception e) {
         if (!(e.getCause() instanceof InterruptedException)) {
            ActiveMQServerLogger.LOGGER.initializationError(e);
         }
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.initializationError(e);
      }
   }

   @Override
   public void close(boolean permanently, boolean restarting) throws Exception {
      if (!restarting) {
         synchronized (failbackCheckerGuard) {
            cancelFailBackChecker = true;
         }
      }
      // To avoid a NPE cause by the stop
      NodeManager nodeManagerInUse = activeMQServer.getNodeManager();

      //we need to check as the servers policy may have changed
      if (activeMQServer.getHAPolicy().isBackup()) {

         activeMQServer.interruptActivationThread(nodeManagerInUse);

         if (nodeManagerInUse != null) {
            unregisterActiveLockListener(nodeManagerInUse);
            nodeManagerInUse.stopBackup();
         }
      } else {

         if (nodeManagerInUse != null) {
            unregisterActiveLockListener(nodeManagerInUse);
            // if we are now active, behave as primary
            // We need to delete the file too, otherwise the backup will failover when we shutdown or if the backup is
            // started before the primary
            if (sharedStoreBackupPolicy.isFailoverOnServerShutdown() || permanently) {
               try {
                  nodeManagerInUse.crashPrimaryServer();
               } catch (Throwable t) {
                  if (!permanently) {
                     throw t;
                  }
                  logger.warn("Errored while closing activation: can be ignored because of permanent close", t);
               }
            } else {
               nodeManagerInUse.pausePrimaryServer();
            }
         }
      }
   }

   private void registerActiveLockListener(NodeManager nodeManager) {
      LockListener lockListener = () -> {
         if (!restarting.compareAndSet(false, true)) {
            logger.warn("Restarting already happening on lost lock");
            return;
         }
         unregisterActiveLockListener(nodeManager);
         ioCriticalErrorListener.onIOException(new IOException("lost lock"), "Lost NodeManager lock", null);
      };
      activeLockListener = lockListener;
      nodeManager.registerLockListener(lockListener);
   }

   private void unregisterActiveLockListener(NodeManager nodeManager) {
      LockListener activeLockListener = this.activeLockListener;
      if (activeLockListener != null) {
         nodeManager.unregisterLockListener(activeLockListener);
         this.activeLockListener = null;
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
      if (sharedStoreBackupPolicy.getScaleDownPolicy() != null && sharedStoreBackupPolicy.getScaleDownPolicy().isEnabled()) {
         return new BackupRecoveryJournalLoader(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration, parentServer, ScaleDownPolicy.getScaleDownConnector(sharedStoreBackupPolicy.getScaleDownPolicy(), activeMQServer), activeMQServer.getClusterManager().getClusterController());
      } else {
         return super.createJournalLoader(postOffice, pagingManager, storageManager, queueFactory, nodeManager, managementService, groupingHandler, configuration, parentServer);
      }
   }

   private final AtomicBoolean restarting = new AtomicBoolean(false);

   /**
    * To be called by backup trying to fail back the server
    */
   private void startFailbackChecker() {
      activeMQServer.getScheduledPool().scheduleAtFixedRate(new FailbackChecker(), 1000L, 1000L, TimeUnit.MILLISECONDS);
   }

   private class FailbackChecker implements Runnable {

      BackupTopologyListener backupListener;

      FailbackChecker() {
         ClusterManager clusterManager = activeMQServer.getClusterManager();
         if (clusterManager != null) {
            ClusterConnection clusterConnection = clusterManager.getDefaultConnection(null);
            if (clusterConnection != null) {
               TransportConfiguration connector = clusterConnection.getConnector();
               if (connector != null) {
                  backupListener = new BackupTopologyListener(activeMQServer.getNodeID().toString(), connector);
                  clusterConnection.addClusterTopologyListener(backupListener);
               } else {
                  ActiveMQServerLogger.LOGGER.failBackCheckerFailure("connector");
               }
            } else {
               ActiveMQServerLogger.LOGGER.failBackCheckerFailure("cluster connection");
            }
         } else {
            ActiveMQServerLogger.LOGGER.failBackCheckerFailure("cluster manager");
         }
      }

      @Override
      public void run() {
         try {
            if (!restarting.get() && activeMQServer.getNodeManager().isAwaitingFailback() && backupListener != null && backupListener.waitForBackup()) {
               if (!restarting.compareAndSet(false, true)) {
                  return;
               }
               ActiveMQServerLogger.LOGGER.awaitFailBack();
               Thread t = new Thread(() -> {
                  try {
                     logger.debug("{}::Stopping active node in favor of failback", activeMQServer);

                     NodeManager nodeManager = activeMQServer.getNodeManager();
                     activeMQServer.stop(true, false, true);

                     // ensure that the server to which we are failing back actually starts fully before we restart
                     nodeManager.start();
                     try {
                        nodeManager.awaitActiveStatus();
                     } finally {
                        nodeManager.stop();
                     }

                     synchronized (failbackCheckerGuard) {
                        if (cancelFailBackChecker || !sharedStoreBackupPolicy.isRestartBackup()) {
                           return;
                        }

                        activeMQServer.setHAPolicy(sharedStoreBackupPolicy);
                        logger.debug("{}::Starting backup node now after failback", activeMQServer);
                        activeMQServer.start();

                        LockListener lockListener = activeLockListener;
                        if (lockListener != null) {
                           activeMQServer.getNodeManager().registerLockListener(lockListener);
                        }
                     }
                  } catch (Exception e) {
                     ActiveMQServerLogger.LOGGER.serverRestartWarning(e);
                  }
               });
               t.start();
            }
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.serverRestartWarning(e);
         }
      }
   }
}
