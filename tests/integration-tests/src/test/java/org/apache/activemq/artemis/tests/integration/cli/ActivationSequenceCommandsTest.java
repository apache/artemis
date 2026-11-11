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
package org.apache.activemq.artemis.tests.integration.cli;

import static org.apache.activemq.artemis.lockmanager.DistributedLockManager.newInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.cli.commands.activation.ActivationSequenceList;
import org.apache.activemq.artemis.cli.commands.activation.ActivationSequenceSet;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ActivationSequenceCommandsTest extends ActiveMQTestBase {

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File brokersFolder;

   protected DistributedLockManagerConfiguration managerConfiguration;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      managerConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(),
                                                                     Collections.singletonMap("locks-folder", newFolder(temporaryFolder, "manager").toString()));
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
   }

   protected Configuration createPrimaryConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::primary");

      File primaryDir = newFolder(brokersFolder, "primary");
      conf.setBrokerInstance(primaryDir);

      conf.addAcceptorConfiguration("primary", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("primary", "tcp://localhost:61616");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      conf.setHAPolicyConfiguration(createReplicationPrimaryConfiguration());

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("backup");
      ccconf.setName("cluster");
      ccconf.setConnectorName("primary");
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   protected Configuration createBackupConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::backup");

      File backupDir = newFolder(brokersFolder, "backup");
      conf.setBrokerInstance(backupDir);

      conf.setHAPolicyConfiguration(createReplicationBackupConfiguration());

      conf.addAcceptorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("live", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("live");
      ccconf.setName("cluster");
      ccconf.setConnectorName("backup");
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   protected HAPolicyConfiguration createReplicationPrimaryConfiguration() {
      ReplicationPrimaryPolicyConfiguration haPolicy = ReplicationPrimaryPolicyConfiguration.withDefault();
      haPolicy.setDistributedManagerConfiguration(managerConfiguration);
      return haPolicy;
   }

   protected HAPolicyConfiguration createReplicationBackupConfiguration() {
      ReplicationBackupPolicyConfiguration haPolicy = ReplicationBackupPolicyConfiguration.withDefault();
      haPolicy.setDistributedManagerConfiguration(managerConfiguration);
      haPolicy.setClusterName("cluster");
      return haPolicy;
   }

   @Test
   public void restorePrimaryCoordinatedSequence() throws Exception {
      // start live
      final Configuration primaryConfiguration = createPrimaryConfiguration();
      final ActiveMQServer liveServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      liveServer.setIdentity("PRIMARY");
      liveServer.start();
      Wait.waitFor(liveServer::isStarted);
      final String nodeID = liveServer.getNodeID().toString();
      liveServer.stop();
      restoreCoordinatedSequence(primaryConfiguration, liveServer, nodeID, 1);
   }

   @Test
   public void restoreBackupCoordinatedSequence() throws Exception {
      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);

      // start live
      Configuration liveConfiguration = createPrimaryConfiguration();

      ActiveMQServer primaryInstance = addServer(ActiveMQServers.newActiveMQServer(liveConfiguration));
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

      final String nodeId = backupServer.getNodeManager().getNodeId().toString();

      backupServer.stop();
      restoreCoordinatedSequence(backupConfiguration, backupServer, nodeId, 2);
   }

   @Test
   public void restorePeerCoordinatedSequence() throws Exception {      // start live
      final Configuration primaryConfiguration = createPrimaryConfiguration();
      ((ReplicationPrimaryPolicyConfiguration) primaryConfiguration.getHAPolicyConfiguration()).setCoordinationId("peer-id");
      final ActiveMQServer liveServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      liveServer.setIdentity("PRIMARY");
      liveServer.start();
      Wait.waitFor(liveServer::isStarted);
      final String nodeID = liveServer.getNodeID().toString();
      liveServer.stop();
      restoreCoordinatedSequence(primaryConfiguration, liveServer, nodeID, 1);
   }

   private void restoreCoordinatedSequence(Configuration primaryConfiguration,
                                           ActiveMQServer liveServer,
                                           String nodeID,
                                           final long expectedStartCoordinatedSequence) throws Exception {
      final ActivationSequenceList sequenceList = new ActivationSequenceList();
      ActivationSequenceList.ListResult list = ActivationSequenceList.execute(sequenceList, primaryConfiguration, null);
      assertEquals(expectedStartCoordinatedSequence, list.coordinatedActivationSequence.longValue());
      assertEquals(expectedStartCoordinatedSequence, list.localActivationSequence.longValue());
      try (DistributedLockManager DistributedLockManager = newInstanceOf(managerConfiguration.getClassName(), managerConfiguration.getProperties())) {
         DistributedLockManager.start();
         try (DistributedLock lock = DistributedLockManager.getDistributedLock(nodeID);
              MutableLong coordinatedActivationSequence = DistributedLockManager.getMutableLong(nodeID)) {
            assertTrue(lock.tryLock());
            final long activationSequence = coordinatedActivationSequence.get();
            assertEquals(expectedStartCoordinatedSequence, activationSequence);
            coordinatedActivationSequence.set(0);
         }
         sequenceList.remote = true;
         assertEquals(0, ActivationSequenceList.execute(sequenceList, primaryConfiguration, null)
            .coordinatedActivationSequence.longValue());
         final ActivationSequenceSet sequenceSet = new ActivationSequenceSet();
         sequenceSet.remote = true;
         sequenceSet.value = expectedStartCoordinatedSequence;
         ActivationSequenceSet.execute(sequenceSet, primaryConfiguration, null);
         liveServer.start();
         Wait.waitFor(liveServer::isStarted);
         assertTrue(liveServer.isActive());
         assertEquals(expectedStartCoordinatedSequence + 1, liveServer.getNodeManager().getNodeActivationSequence());
         liveServer.stop();
      }
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
