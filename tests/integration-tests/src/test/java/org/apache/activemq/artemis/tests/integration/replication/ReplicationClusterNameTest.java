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

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A replicating backup finds and connects to its primary through the cluster connection its policy names, not through
 * the first one.
 */
public class ReplicationClusterNameTest extends ActiveMQTestBase {

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File brokersFolder;

   @Test
   @Timeout(60)
   public void testBackupReplicatesThroughNamedClusterConnection() throws Exception {
      ActiveMQServer primary = addServer(ActiveMQServers.newActiveMQServer(createConfiguration("primary", 61616, 61617, new ReplicatedPolicyConfiguration()
         .setCheckForActiveServer(false)
         .setClusterName("cluster"))));
      primary.start();
      waitForServerToStart(primary);

      ActiveMQServer backup = addServer(ActiveMQServers.newActiveMQServer(createConfiguration("backup", 61617, 61616, new ReplicaPolicyConfiguration()
         .setClusterName("cluster"))));
      backup.start();

      Wait.assertTrue(backup::isReplicaSync, 20_000);
   }

   @Test
   @Timeout(60)
   public void testLockManagerBackupReplicatesThroughNamedClusterConnection() throws Exception {
      File locksFolder = new File(brokersFolder, "manager");
      locksFolder.mkdirs();
      DistributedLockManagerConfiguration managerConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(), Map.of("locks-folder", locksFolder.toString()));

      ActiveMQServer primary = addServer(ActiveMQServers.newActiveMQServer(createConfiguration("primary", 61616, 61617, ReplicationPrimaryPolicyConfiguration.withDefault()
         .setDistributedManagerConfiguration(managerConfiguration)
         .setClusterName("cluster"))));
      primary.start();
      waitForServerToStart(primary);

      ActiveMQServer backup = addServer(ActiveMQServers.newActiveMQServer(createConfiguration("backup", 61617, 61616, ReplicationBackupPolicyConfiguration.withDefault()
         .setDistributedManagerConfiguration(managerConfiguration)
         .setClusterName("cluster"))));
      backup.start();

      Wait.assertTrue(backup::isReplicaSync, 20_000);
   }

   private Configuration createConfiguration(String name, int port, int otherPort, HAPolicyConfiguration haPolicy) throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::" + name);
      conf.setBrokerInstance(new File(brokersFolder, name));

      conf.addAcceptorConfiguration(name, "tcp://localhost:" + port);
      conf.addConnectorConfiguration(name, "tcp://localhost:" + port);
      conf.addConnectorConfiguration("other", "tcp://localhost:" + otherPort);
      // nothing listens here
      conf.addConnectorConfiguration("nowhere", "tcp://localhost:1");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      conf.setHAPolicyConfiguration(haPolicy);

      // the first cluster connection is the default one, and leads nowhere
      conf.addClusterConfiguration(new ClusterConnectionConfiguration()
         .setName("decoy")
         .setConnectorName(name)
         .setStaticConnectors(List.of("nowhere"))
         .setInitialConnectAttempts(1));

      conf.addClusterConfiguration(new ClusterConnectionConfiguration()
         .setName("cluster")
         .setConnectorName(name)
         .setStaticConnectors(List.of("other")));

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false);

      return conf;
   }
}
