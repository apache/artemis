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
package org.apache.activemq.artemis.tests.integration.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LockManagerReplicationTest extends SharedNothingReplicationTest {

   private DistributedLockManagerConfiguration managerConfiguration;

   @BeforeEach
   public void init() throws IOException {
      managerConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(), Collections.singletonMap("locks-folder", newFolder(temporaryFolder, "manager").toString()));
   }

   @Override
   protected HAPolicyConfiguration createReplicationPrimaryConfiguration() {
      ReplicationPrimaryPolicyConfiguration haPolicy = ReplicationPrimaryPolicyConfiguration.withDefault();
      haPolicy.setDistributedManagerConfiguration(managerConfiguration);
      return haPolicy;
   }

   @Override
   protected HAPolicyConfiguration createReplicationBackupConfiguration() {
      ReplicationBackupPolicyConfiguration haPolicy = ReplicationBackupPolicyConfiguration.withDefault();
      haPolicy.setDistributedManagerConfiguration(managerConfiguration);
      haPolicy.setClusterName("cluster");
      return haPolicy;
   }

   @Test
   public void testUnReplicatedOrderedTransition() throws Exception {
      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      ServerLocator locator = ServerLocatorImpl.newLocator("(tcp://localhost:61616,tcp://localhost:61617)?ha=true");
      locator.setCallTimeout(60_000L);
      locator.setConnectionTTL(60_000L);

      ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession clientSession = csf.createSession();
      clientSession.createQueue(QueueConfiguration.of("slow").setRoutingType(RoutingType.ANYCAST));
      clientSession.close();

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      Wait.waitFor(backupServer::isStarted);

      waitForTopology(primaryServer, 1, 1, 30000);
      waitForTopology(backupServer, 1, 1, 30000);

      primaryServer.stop();

      // backup will take over and run un replicated

      csf = locator.createSessionFactory();
      clientSession = csf.createSession();
      clientSession.createQueue(QueueConfiguration.of("slow_un_replicated").setRoutingType(RoutingType.ANYCAST));
      clientSession.close();

      waitForTopology(backupServer, 1, 0, 30000);
      assertTrue(Wait.waitFor(() -> 2L == backupServer.getNodeManager().getNodeActivationSequence()));

      backupServer.stop(false);

      // now only backup should be able to start as it has run un_replicated
      primaryServer.start();
      Wait.assertFalse(primaryServer::isActive);
      primaryServer.stop();

      // restart backup
      backupServer.start();

      Wait.waitFor(backupServer::isStarted);
      assertEquals(3L, backupServer.getNodeManager().getNodeActivationSequence());

      csf = locator.createSessionFactory();
      clientSession = csf.createSession();
      clientSession.createQueue(QueueConfiguration.of("backup_as_un_replicated").setRoutingType(RoutingType.ANYCAST));
      clientSession.close();

      // verify the primary restart as a backup to the restarted backupServer that has taken on the primary role, no failback
      primaryServer.start();

      csf = locator.createSessionFactory();
      clientSession = csf.createSession();
      clientSession.createQueue(QueueConfiguration.of("backup_as_replicated").setRoutingType(RoutingType.ANYCAST));
      clientSession.close();

      assertTrue(Wait.waitFor(primaryServer::isReplicaSync));
      assertTrue(Wait.waitFor(() -> 3L == primaryServer.getNodeManager().getNodeActivationSequence()));

      backupServer.stop(true);

      waitForTopology(primaryServer, 1, 0, 30000);
      assertTrue(Wait.waitFor(() -> 4L == primaryServer.getNodeManager().getNodeActivationSequence()));

      primaryServer.stop(true);
      clientSession.close();
      locator.close();
   }

   @Test
   public void testBackupFailoverAndPrimaryFailback() throws Exception {
      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);

      // start primary
      Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryInstance = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryInstance.setIdentity("PRIMARY");
      primaryInstance.start();

      // primary initially UN REPLICATED
      assertEquals(1L, primaryInstance.getNodeManager().getNodeActivationSequence());

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ((ReplicationBackupPolicyConfiguration)backupConfiguration.getHAPolicyConfiguration()).setAllowFailBack(true);

      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      Wait.waitFor(backupServer::isStarted);

      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> backupServer.isReplicaSync(), timeout);

      // primary REPLICATED, backup matches (has replicated) activation sequence
      assertEquals(1L, primaryInstance.getNodeManager().getNodeActivationSequence());
      assertEquals(1L, backupServer.getNodeManager().getNodeActivationSequence());

      primaryInstance.stop();

      // backup UN REPLICATED (new version)
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> 2L == backupServer.getNodeManager().getNodeActivationSequence(), timeout);

      // just to let the console logging breath!
      TimeUnit.MILLISECONDS.sleep(100);

      // restart primary that will request failback
      ActiveMQServer restartedPrimaryForFailBack = primaryInstance; //addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      restartedPrimaryForFailBack.start();

      // first step is backup getting replicated
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> backupServer.isReplicaSync(), timeout);

      // restarted primary will run un replicated (increment sequence) while backup restarts to revert to backup role.
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> {
         try {
            return 3L == restartedPrimaryForFailBack.getNodeManager().getNodeActivationSequence();
         } catch (NullPointerException ok) {
            return false;
         }
      }, timeout);

      // the backup should then resume with an insync replica view of that version
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> restartedPrimaryForFailBack.isReplicaSync(), timeout);
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> backupServer.isReplicaSync(), timeout);
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> 3L == backupServer.getNodeManager().getNodeActivationSequence(), timeout);

      // just to let the console logging breath!
      TimeUnit.MILLISECONDS.sleep(100);

      // stop backup to verify primary goes on with new sequence as un replicated
      backupServer.stop();

      // just to let the console logging breath!
      TimeUnit.MILLISECONDS.sleep(100);

      // primary goes un replicated
      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> {
         try {
            return 4L == restartedPrimaryForFailBack.getNodeManager().getNodeActivationSequence();
         } catch (NullPointerException ok) {
            return false;
         }
      }, timeout);

      restartedPrimaryForFailBack.stop();
   }


   @Test
   public void testPrimaryIncrementActivationSequenceOnUnReplicated() throws Exception {
      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);

      // start primary
      Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryInstance = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryInstance.setIdentity("PRIMARY");
      primaryInstance.start();

      // primary UN REPLICATED
      assertEquals(1L, primaryInstance.getNodeManager().getNodeActivationSequence());

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();

      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      Wait.waitFor(backupServer::isStarted);

      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> backupServer.isReplicaSync(), timeout);

      // primary REPLICATED, backup matches (has replicated) activation sequence
      assertEquals(1L, primaryInstance.getNodeManager().getNodeActivationSequence());
      assertEquals(1L, backupServer.getNodeManager().getNodeActivationSequence());

      // transition to un replicated once backup goes away
      backupServer.stop();

      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> 2L == primaryInstance.getNodeManager().getNodeActivationSequence(), timeout);

      // done
      primaryInstance.stop();
   }


   @Test
   public void testBackupStartsFirst() throws Exception {

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isStarted));
      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());
   }

   @Test
   public void testBackupOutOfSequenceReleasesLock() throws Exception {

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isStarted));
      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());

      backupServer.stop();

      TimeUnit.SECONDS.sleep(1);

      primaryServer.stop();
      // backup can get lock but does not have the sequence to start, will try and be a backup

      backupServer.start();

      // primary server should be active
      primaryServer.start();
      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isStarted));
      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());
   }


   @Test
   public void testBackupOutOfSequenceCheckActivationSequence() throws Exception {

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isStarted));
      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());

      backupServer.stop();

      TimeUnit.SECONDS.sleep(1);

      final String coordinatedId = primaryServer.getNodeID().toString();
      primaryServer.stop();

      // backup can get lock but does not have the sequence to start, will try and be a backup
      // to verify it can short circuit with a dirty read we grab the lock for a little while
      DistributedLockManager distributedLockManager = DistributedLockManager.newInstanceOf(
         managerConfiguration.getClassName(),
         managerConfiguration.getProperties());
      distributedLockManager.start();
      final DistributedLock lock = distributedLockManager.getDistributedLock(coordinatedId);
      assertTrue(lock.tryLock());
      CountDownLatch preActivate = new CountDownLatch(1);
      backupServer.registerActivateCallback(new ActivateCallback() {
         @Override
         public void preActivate() {
            ActivateCallback.super.preActivate();
            preActivate.countDown();
         }
      });
      backupServer.start();

      // it should be able to do a dirty read of the sequence id and not have to wait to get a lock
      assertTrue(preActivate.await(1, TimeUnit.SECONDS));

      // release the lock
      distributedLockManager.stop();

      // primary server should be active
      primaryServer.start();
      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());
   }

   @Test
   public void testSelfRepairPrimary() throws Exception {
      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();
      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");

      primaryServer.start();
      final String coordinatedId = primaryServer.getNodeID().toString();
      Wait.waitFor(primaryServer::isStarted);
      primaryServer.stop();

      primaryServer.start();
      Wait.waitFor(primaryServer::isStarted);
      assertEquals(2, primaryServer.getNodeManager().getNodeActivationSequence());
      primaryServer.stop();

      // backup can get lock but does not have the sequence to start, will try and be a backup
      // to verify it can short circuit with a dirty read we grab the lock for a little while
      DistributedLockManager distributedLockManager = DistributedLockManager.newInstanceOf(managerConfiguration.getClassName(), managerConfiguration.getProperties());
      distributedLockManager.start();
      try (DistributedLock lock = distributedLockManager.getDistributedLock(coordinatedId)) {
         assertTrue(lock.tryLock());
         distributedLockManager.getMutableLong(coordinatedId).compareAndSet(2, -2);
      }
      primaryServer.start();
      Wait.waitFor(primaryServer::isStarted);
      assertEquals(3, primaryServer.getNodeManager().getNodeActivationSequence());
      assertEquals(3, distributedLockManager.getMutableLong(coordinatedId).get());

      distributedLockManager.stop();

      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();
      Wait.waitFor(backupServer::isReplicaSync);
      assertEquals(3, backupServer.getNodeManager().getNodeActivationSequence());
      backupServer.stop();
   }

   @Test
   public void testPrimaryPeers() throws Exception {
      final String PEER_NODE_ID = "some-shared-id-001";

      final Configuration primaryConfiguration = createPrimaryConfiguration();
      ((ReplicationPrimaryPolicyConfiguration)primaryConfiguration.getHAPolicyConfiguration()).setCoordinationId(PEER_NODE_ID);

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      ServerLocator locator = ServerLocatorImpl.newLocator("(tcp://localhost:61616,tcp://localhost:61617)?ha=true");
      locator.setCallTimeout(60_000L);
      locator.setConnectionTTL(60_000L);
      final ClientSessionFactory keepLocatorAliveSLF = locator.createSessionFactory();

      ClientSessionFactory csf = locator.createSessionFactory();
      sendTo(csf, "primary_un_replicated");
      csf.close();

      // start peer, will backup
      Configuration primaryPeerConfiguration = createBackupConfiguration(); // to get acceptors and locators ports that won't clash
      primaryPeerConfiguration.setHAPolicyConfiguration(createReplicationPrimaryConfiguration());
      ((ReplicationPrimaryPolicyConfiguration)primaryPeerConfiguration.getHAPolicyConfiguration()).setCoordinationId(PEER_NODE_ID);
      primaryPeerConfiguration.setName("localhost::primary-peer");

      ActiveMQServer primaryPeerServer = addServer(ActiveMQServers.newActiveMQServer(primaryPeerConfiguration));
      primaryPeerServer.setIdentity("PRIMARY-PEER");
      primaryPeerServer.start();

      Wait.waitFor(primaryPeerServer::isStarted);

      waitForTopology(primaryServer, 1, 1, 30000);
      waitForTopology(primaryPeerServer, 1, 1, 30000);

      primaryServer.stop();

      // primaryPeerServer will take over and run un replicated

      csf = locator.createSessionFactory();
      receiveFrom(csf, "primary_un_replicated");
      sendTo(csf, "peer_un_replicated");
      csf.close();

      waitForTopology(primaryPeerServer, 1, 0, 30000);

      assertTrue(Wait.waitFor(() -> 2L == primaryPeerServer.getNodeManager().getNodeActivationSequence()));

      primaryPeerServer.stop(false);

      primaryServer.start();

      Wait.assertTrue(() -> !primaryServer.isActive());

      // restart backup
      primaryPeerServer.start();

      Wait.waitFor(primaryPeerServer::isStarted);

      assertEquals(3L, primaryPeerServer.getNodeManager().getNodeActivationSequence());

      csf = locator.createSessionFactory();
      receiveFrom(csf, "peer_un_replicated");
      sendTo(csf, "backup_as_un_replicated");
      csf.close();

      // verify the primary restarts as a backup to the restarted primaryPeerServer that has taken on the primary role
      primaryServer.start();

      csf = locator.createSessionFactory();
      receiveFrom(csf, "backup_as_un_replicated");
      sendTo(csf, "backup_as_replicated");
      csf.close();

      assertTrue(Wait.waitFor(primaryServer::isReplicaSync));
      assertTrue(Wait.waitFor(() -> 3L == primaryServer.getNodeManager().getNodeActivationSequence()));

      waitForTopology(primaryServer, 1, 1, 30000);
      waitForTopology(primaryPeerServer, 1, 1, 30000);

      primaryPeerServer.stop(true);

      assertTrue(Wait.waitFor(() -> 4L == primaryServer.getNodeManager().getNodeActivationSequence()));

      csf = locator.createSessionFactory();
      receiveFrom(csf, "backup_as_replicated");
      csf.close();

      waitForTopology(primaryServer, 1, 0, 30000);

      primaryServer.stop(true);
      keepLocatorAliveSLF.close();
      locator.close();
   }

   @Test
   public void testUnavailableSelfHeal() throws Exception {

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isStarted));
      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());

      final String coordinatedId = primaryServer.getNodeID().toString();

      backupServer.stop();
      TimeUnit.MILLISECONDS.sleep(500);
      primaryServer.stop();

      // some  manual intervention to force an unavailable
      // simulate primary failing in activation local sequence update on un replicated run when backup stops.

      DistributedLockManager distributedLockManager = DistributedLockManager.newInstanceOf(managerConfiguration.getClassName(), managerConfiguration.getProperties());
      distributedLockManager.start();
      final MutableLong activationSequence = distributedLockManager.getMutableLong(coordinatedId);
      assertTrue(activationSequence.compareAndSet(2, -2));

      // primary server should activate after self healing its outstanding claim
      primaryServer.start();
      Wait.waitFor(primaryServer::isStarted);
      assertEquals(3, primaryServer.getNodeManager().getNodeActivationSequence());
      assertEquals(3, activationSequence.get());
   }

   @Test
   public void testUnavailableAutoRepair() throws Exception {
      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      // start primary
      final Configuration primaryConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.setIdentity("PRIMARY");
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      assertTrue(Wait.waitFor(backupServer::isStarted));
      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());

      final String coordinatedId = primaryServer.getNodeID().toString();

      backupServer.stop();
      TimeUnit.MILLISECONDS.sleep(500);
      primaryServer.stop();

      // some  manual intervention to force an unavailable
      // simulate primary failing in activation local sequence update on un replicated run when backup stops.

      DistributedLockManager distributedLockManager = DistributedLockManager.newInstanceOf(
         managerConfiguration.getClassName(),
         managerConfiguration.getProperties());
      distributedLockManager.start();
      final MutableLong coordinatedActivationSequence = distributedLockManager.getMutableLong(coordinatedId);
      assertTrue(coordinatedActivationSequence.compareAndSet(2, -2));

      // case: 2, the fail to write locally 2 but the write actually failing
      // need to put 1 in the local activation sequence of the primary
      FileLockNodeManager fileLockNodeManager = new FileLockNodeManager(primaryConfiguration.getNodeManagerLockLocation().getAbsoluteFile(), true);
      fileLockNodeManager.start();
      assertEquals(2, fileLockNodeManager.readNodeActivationSequence());
      fileLockNodeManager.writeNodeActivationSequence(1);
      fileLockNodeManager.stop();

      // should delay pending resolution of the uncommitted claim
      backupServer.start();
      CountDownLatch primaryStarting = new CountDownLatch(1);
      // should delay pending resolution of the uncommitted claim
      // IMPORTANT: primary activation run on the start caller thread!! We need another thread here
      final Thread primaryServerStarterThread = new Thread(() -> {
         primaryStarting.countDown();
         try {
            primaryServer.start();
         } catch (Throwable e) {
            e.printStackTrace();
         }
      });
      primaryServerStarterThread.start();
      primaryStarting.await();
      TimeUnit.MILLISECONDS.sleep(500);
      // both are candidates and one of them failed to commit the claim
      // let them compete on retry
      // one of the two can activate
      Wait.waitFor(() -> primaryServer.isStarted() || backupServer.isStarted());

      assertTrue(Wait.waitFor(backupServer::isReplicaSync));
      assertTrue(primaryServer.isReplicaSync());

      assertEquals(3, backupServer.getNodeManager().getNodeActivationSequence());
      assertEquals(3, primaryServer.getNodeManager().getNodeActivationSequence());

   }

   private void sendTo(ClientSessionFactory clientSessionFactory, String addr) throws Exception {
      ClientSession clientSession = clientSessionFactory.createSession(true, true);
      clientSession.createQueue(QueueConfiguration.of(addr).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      ClientProducer producer = clientSession.createProducer(addr);
      ClientMessage message = clientSession.createMessage(true);
      message.putStringProperty("K", addr);
      message.putLongProperty("delay", 0L); // so slow interceptor does not get us
      producer.send(message);
      producer.close();
      clientSession.close();
   }

   private void receiveFrom(ClientSessionFactory clientSessionFactory, String addr) throws Exception {
      ClientSession clientSession = clientSessionFactory.createSession(true, true);
      clientSession.start();
      ClientConsumer consumer = clientSession.createConsumer(addr);
      Message message = consumer.receive(4000);
      assertNotNull(message);
      assertEquals(addr, message.getStringProperty("K"));
      consumer.close();
      clientSession.close();
   }

   private static File newFolder(File root, String... subDirs) throws IOException {
      String subFolder = String.join("/", subDirs);
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
