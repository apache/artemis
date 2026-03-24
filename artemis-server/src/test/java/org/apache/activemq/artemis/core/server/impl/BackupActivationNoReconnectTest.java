/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.activemq.artemis.core.server.impl;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterConnectReplyMessage;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.LiveNodeLocator;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.BackupManager;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicaPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationBackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationPrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.qourum.QuorumManager;
import org.apache.activemq.artemis.core.server.cluster.qourum.SharedNothingBackupQuorum;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.quorum.DistributedPrimitiveManager;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class BackupActivationNoReconnectTest {

   @Test(timeout = 30000)
   public void verifyReplicationBackupActivation() throws Exception {
      ReplicationBackupPolicy policy = Mockito.mock(ReplicationBackupPolicy.class);
      ReplicationPrimaryPolicy replicationPrimaryPolicy = Mockito.mock(ReplicationPrimaryPolicy.class);
      when(policy.getLivePolicy()).thenReturn(replicationPrimaryPolicy);
      ActiveMQServerImpl server = Mockito.mock(ActiveMQServerImpl.class);

      DistributedPrimitiveManager distributedManager = Mockito.mock(DistributedPrimitiveManager.class);
      ReplicationBackupActivation replicationBackupActivation = new ReplicationBackupActivation(server, distributedManager, policy);

      verifySingleAttemptToLocateLive(server, replicationBackupActivation);
   }

   @Test(timeout = 30000)
   public void verifySharedNothingBackupActivation() throws Exception {
      ActiveMQServerImpl server = Mockito.mock(ActiveMQServerImpl.class);
      when(server.isStarted()).thenReturn(true);
      ExecutorService threadPool = Mockito.mock(ExecutorService.class);
      doAnswer(invocation -> {
         Runnable runnable = invocation.getArgument(0);
         runnable.run();
         return null;
      }).when(threadPool).execute(any(Runnable.class));
      when(server.getThreadPool()).thenReturn(threadPool);
      HAPolicy haPolicy = Mockito.mock(HAPolicy.class);
      when(haPolicy.isBackup()).thenReturn(true);
      when(server.getHAPolicy()).thenReturn(haPolicy);
      when(server.getManagementService()).thenReturn(Mockito.mock(ManagementService.class));

      Map<String, Object> activationParams = new HashMap<>();
      ReplicaPolicy replicaPolicy = new ReplicaPolicy(null, 0);
      replicaPolicy.setAllowFailback(false);

      verifySingleAttemptToLocateLive(server, replicaPolicy.createActivation(server, false, activationParams, null));
   }

   protected void verifySingleAttemptToLocateLive(ActiveMQServerImpl server, Activation activation) throws Exception {

      NodeManager nodeManager = Mockito.mock(NodeManager.class);
      when(server.getNodeManager()).thenReturn(nodeManager);

      when(nodeManager.getNodeActivationSequence()).thenReturn(0L);
      when(server.initialisePart1(Mockito.anyBoolean())).thenReturn(true);

      ClusterManager clusterManager = Mockito.mock(ClusterManager.class);
      ClusterController clusterController = Mockito.mock(ClusterController.class);
      when(clusterManager.getClusterController()).thenReturn(clusterController);
      when(server.getClusterManager()).thenReturn(clusterManager);

      QuorumManager quorumManager = Mockito.mock(QuorumManager.class);
      when(clusterManager.getQuorumManager()).thenReturn(quorumManager);
      Version version = Mockito.mock(Version.class);
      when(server.getVersion()).thenReturn(version);
      BackupManager backupManager = Mockito.mock(BackupManager.class);
      when(server.getBackupManager()).thenReturn(backupManager);


      final CountDownLatch gotLocator = new CountDownLatch(1);
      final AtomicReference<LiveNodeLocator> locatorAtomicReference = new AtomicReference<>();
      doAnswer(invocation -> {
         locatorAtomicReference.set((LiveNodeLocator) invocation.getArguments()[0]);
         gotLocator.countDown();
         return null;
      }).when(clusterController).addClusterTopologyListenerForReplication(Mockito.any());

      when(server.checkLiveIsNotColocated(Mockito.anyString())).thenReturn(true);

      ClusterControl clusterControl = Mockito.mock(ClusterControl.class);
      when(clusterController.connectToNodeInReplicatedCluster(Mockito.any())).thenReturn(clusterControl);

      // start listener that will accept replication channel
      try (ServerSocket serverSocket = new ServerSocket(0)) {

         final ServerLocatorInternal serverLocator = Mockito.mock(ServerLocatorInternal.class);
         final TransportConfiguration connectorConfig = Mockito.mock(TransportConfiguration.class);
         final ServerLocatorConfig locatorConfig = Mockito.mock(ServerLocatorConfig.class);
         final int reconnectAttempts = 1;
         final Executor threadPool = Mockito.mock(Executor.class);
         final ScheduledExecutorService scheduledThreadPool = Mockito.mock(ScheduledExecutorService.class);
         final ClientProtocolManager clientProtocolManager = Mockito.mock(ClientProtocolManager.class);
         when(serverLocator.newProtocolManager()).thenReturn(clientProtocolManager);
         when(connectorConfig.getFactoryClassName()).thenReturn(NettyConnectorFactory.class.getName());
         Map<String, Object> urlParams = new HashMap<>();
         urlParams.put("port", serverSocket.getLocalPort());
         when(connectorConfig.getParams()).thenReturn(urlParams);
         ClientSessionFactoryImpl sessionFactory = new ClientSessionFactoryImpl(serverLocator, connectorConfig, locatorConfig, reconnectAttempts, threadPool, scheduledThreadPool, null, null);
         when(clusterControl.getSessionFactory()).thenReturn(sessionFactory);
         when(clientProtocolManager.isAlive()).thenReturn(true);

         CoreRemotingConnection remotingConnection = Mockito.mock(CoreRemotingConnection.class);
         when(clientProtocolManager.connect(any(), anyLong(), anyLong(), any(), any(), any())).thenReturn(remotingConnection);
         final CountDownLatch gotReplicationObserverListener = new CountDownLatch(1);
         final CountDownLatch gotListener = new CountDownLatch(1);
         AtomicReference<FailureListener> failureListenerAtomicReference = new AtomicReference<>();
         doAnswer((Answer<Object>) invocation -> {
            final FailureListener listener = invocation.getArgument(0);
            if (listener instanceof ReplicationObserver || listener instanceof SharedNothingBackupQuorum) {
               gotReplicationObserverListener.countDown();
            } else {
               failureListenerAtomicReference.set(listener);
               gotListener.countDown();
            }
            return null;
         }).when(remotingConnection).addFailureListener(any());
         when(remotingConnection.getID()).thenReturn("First");

         final Channel channel = Mockito.mock(Channel.class);
         ClusterConnectReplyMessage clusterConnectReplyMessage = Mockito.mock(ClusterConnectReplyMessage.class);
         when(channel.sendBlocking(any(), anyByte())).thenReturn(clusterConnectReplyMessage);
         when(clusterConnectReplyMessage.isAuthorized()).thenReturn(true);
         ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);
         when(server.getExecutorFactory()).thenReturn(executorFactory);
         ArtemisExecutor executor = Mockito.mock(ArtemisExecutor.class);
         doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
         }).when(executor).execute(any(Runnable.class));

         when(executorFactory.getExecutor()).thenReturn(executor);
         when(clusterControl.createReplicationChannel()).thenReturn(channel);

         StorageManager storageManager = Mockito.mock(StorageManager.class);
         when(server.getStorageManager()).thenReturn(storageManager);
         Journal journal = Mockito.mock(Journal.class);
         when(storageManager.getBindingsJournal()).thenReturn(journal);
         when(storageManager.getMessageJournal()).thenReturn(journal);
         when(server.createPagingManager()).thenReturn(Mockito.mock(PagingManager.class));


         AtomicBoolean reconnectWorkOnRetry = new AtomicBoolean();
         final ActiveMQDisconnectedException forcedError = new ActiveMQDisconnectedException("DD");
         when(clientProtocolManager.cleanupBeforeFailover(any())).then(invocation -> {
            assertEquals(forcedError, invocation.getArgument(0));
            reconnectWorkOnRetry.set(true);
            return true;
         });

         final CountDownLatch failure = new CountDownLatch(2);
         AtomicBoolean failoverEventReported = new AtomicBoolean(true);
         sessionFactory.addFailureListener(new SessionFailureListener() {
            @Override
            public void beforeReconnect(ActiveMQException exception) {
               // debatable whether this should be called when there will be no reconnect!
               failure.countDown();
            }

            @Override
            public void connectionFailed(ActiveMQException exception, boolean failedOver) {
            }

            @Override
            public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
               failure.countDown();
               failoverEventReported.set(failedOver);
            }
         });

         // start replicator, the actual test!
         Thread activationThread = new Thread(activation);
         activationThread.start();

         // signal nodeUp once locator is in place
         assertTrue(gotLocator.await(5, TimeUnit.SECONDS));
         locatorAtomicReference.get().nodeUP(new TopologyMemberImpl("nodeId", "backupGroupName", "", new TransportConfiguration(), new TransportConfiguration()), false);

         Socket clientSocket = serverSocket.accept();

         // wait till failure listener is installed, then close
         assertTrue("Replication observer in play", gotReplicationObserverListener.await(5, TimeUnit.SECONDS));
         clientSocket.close();

         // propagate the failure
         assertTrue(gotListener.await(5, TimeUnit.SECONDS));
         failureListenerAtomicReference.get().connectionFailed(forcedError, false);

         // wait for activation completion
         activationThread.join(10000);
         if (activationThread.isAlive()) {
            String dump = ThreadDumpUtil.threadDump("Activation thread is still alive!");
            activationThread.interrupt();
            Assert.fail(dump);
         }

         assertTrue(failure.await(5, TimeUnit.SECONDS));
         assertFalse(failoverEventReported.get());

         // ensure  no reconnect
         assertFalse(reconnectWorkOnRetry.get());
      }
   }
}