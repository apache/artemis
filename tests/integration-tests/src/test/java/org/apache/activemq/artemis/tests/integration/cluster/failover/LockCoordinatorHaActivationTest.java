/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.LockCoordinatorConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.core.server.lock.LockCoordinator;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.ReplicatedBackupUtils;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

/**
 * A lock coordinator must only start polling for its distributed lock once the broker that owns it is actually
 * active. Before this was fixed, {@code ActiveMQServerImpl.internalStart()} started every configured lock
 * coordinator unconditionally, regardless of primary/backup role. As a consequence a passive HA backup (replication
 * ha-policy) would compete for the lock with the currently active primary and, if it happened to win it, elements
 * bound to the lock coordinator (acceptors, broker connections) would stay dead on the primary while the passive
 * backup - which isn't accepting any traffic - silently held the lock.
 */
public class LockCoordinatorHaActivationTest extends ActiveMQTestBase {

   private static final String LOCK_NAME = "theLock";

   @Test
   public void testPassiveBackupDoesNotContendForLock() throws Exception {
      final String locksFolder = getTemporaryDir();

      final TransportConfiguration primaryConnector = TransportConfigurationUtils.getInVMConnector(true);
      final TransportConfiguration backupConnector = TransportConfigurationUtils.getInVMConnector(false);
      final TransportConfiguration backupAcceptor = TransportConfigurationUtils.getInVMAcceptor(false);

      Configuration backupConfig = createDefaultInVMConfig();
      Configuration primaryConfig = createDefaultInVMConfig();

      ReplicatedBackupUtils.configureReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, null);
      primaryConfig.clearAcceptorConfigurations().addAcceptorConfiguration(TransportConfigurationUtils.getInVMAcceptor(true));

      // both nodes are configured with the very same lock-coordinator, pointing at the very same locks-folder, so
      // they really do contend for the same file lock if both were to start polling for it.
      addLockCoordinator(primaryConfig, locksFolder);
      addLockCoordinator(backupConfig, locksFolder);

      NodeManager primaryNodeManager = new InVMNodeManager(true, primaryConfig.getJournalLocation());
      ActiveMQServer primaryServer = createInVMFailoverServer(true, primaryConfig, primaryNodeManager, 1);

      NodeManager backupNodeManager = new InVMNodeManager(true, backupConfig.getJournalLocation());
      ActiveMQServer backupServer = createInVMFailoverServer(true, backupConfig, backupNodeManager, 2);

      primaryServer.start();
      Wait.assertTrue(primaryServer::isActive);
      Wait.assertEquals("Locked", () -> primaryServer.getLockCoordinator(LOCK_NAME).getStatus());

      backupServer.start();
      Wait.assertTrue(backupServer::isReplicaSync, 30_000, 100);

      // the backup is passive: its lock coordinator must have been created (so acceptors/broker connections can
      // look it up by name) but must stay stopped, never contending for the lock with the active primary.
      LockCoordinator backupCoordinator = backupServer.getLockCoordinator(LOCK_NAME);
      assertNotNull(backupCoordinator, "the backup must create its lock coordinators even while passive");
      assertEquals("Stopped", backupCoordinator.getStatus());

      // give the backup's coordinator a few check-periods worth of time to prove it really isn't polling
      Thread.sleep(500);
      assertEquals("Stopped", backupCoordinator.getStatus(), "a passive backup must not contend for the lock");
      assertEquals("Locked", primaryServer.getLockCoordinator(LOCK_NAME).getStatus(), "the active primary must still be holding the lock");

      // the primary goes away: the backup activates and, only now, is allowed to start polling for (and acquire)
      // the lock.
      primaryServer.stop();

      Wait.assertTrue(backupServer::isActive, 30_000, 100);
      Wait.assertEquals("Locked", () -> backupServer.getLockCoordinator(LOCK_NAME).getStatus(), 30_000, 100);
   }

   private void addLockCoordinator(Configuration configuration, String locksFolder) {
      Map<String, String> properties = new HashMap<>();
      properties.put("locks-folder", locksFolder);
      LockCoordinatorConfiguration lockCoordinatorConfiguration = new LockCoordinatorConfiguration(properties)
         .setName(LOCK_NAME).setLockId(LOCK_NAME).setClassName(FileBasedLockManager.class.getName()).setCheckPeriod(100);
      configuration.addLockCoordinatorConfiguration(lockCoordinatorConfiguration);
   }
}
