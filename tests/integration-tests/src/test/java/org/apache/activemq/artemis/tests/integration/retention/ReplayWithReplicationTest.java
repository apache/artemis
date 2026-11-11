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
package org.apache.activemq.artemis.tests.integration.retention;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
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
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ReplayWithReplicationTest extends ReplayTest {

   private DistributedLockManagerConfiguration managerConfiguration;
   ActiveMQServer backupServer;

   File newTemporaryFolder(String name) {
      File newFolder = new File(temporaryFolder, name);
      newFolder.mkdirs();
      return newFolder;
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      managerConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(), Collections.singletonMap("locks-folder", newTemporaryFolder("manager").toString()));
      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);

      // start live
      Configuration liveConfiguration = createLiveConfiguration();

      server = addServer(ActiveMQServers.newActiveMQServer(liveConfiguration));
      server.setIdentity("PRIMARY");
      server.getConfiguration().setJournalRetentionDirectory(getJournalDir() + "retention");
      server.getConfiguration().setJournalFileSize(100 * 1024);

      server.start();

      server.addAddressInfo(new AddressInfo("t1").addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("t1").setAddress("t1").setRoutingType(RoutingType.ANYCAST));

      server.addAddressInfo(new AddressInfo("t2").addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("t2").setAddress("t2").setRoutingType(RoutingType.ANYCAST));

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ((ReplicationBackupPolicyConfiguration) backupConfiguration.getHAPolicyConfiguration()).setAllowFailBack(true);
      backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
      backupServer.start();

      Wait.waitFor(backupServer::isStarted);

      org.apache.activemq.artemis.utils.Wait.assertTrue(() -> backupServer.isReplicaSync(), timeout);
   }

   protected HAPolicyConfiguration createReplicationLiveConfiguration() {
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

   protected Configuration createLiveConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::live");

      File liveDir = newTemporaryFolder("live");
      conf.setBrokerInstance(liveDir);

      conf.addAcceptorConfiguration("live", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("live", "tcp://localhost:61616");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      conf.setHAPolicyConfiguration(createReplicationLiveConfiguration());

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("backup");
      ccconf.setName("cluster");
      ccconf.setConnectorName("live");
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   protected Configuration createBackupConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::backup");

      File backupDir = newTemporaryFolder("backup");
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

}
