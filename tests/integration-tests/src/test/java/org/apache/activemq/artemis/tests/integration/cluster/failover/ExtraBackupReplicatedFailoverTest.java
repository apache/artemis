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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.tests.extensions.TestMethodNameMatchExtension;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ExtraBackupReplicatedFailoverTest extends FailoverTestBase {

   private static final String TEST_EXTRA_BACKUP_GROUP_NAME_REPLICATES = "testExtraBackupGroupNameReplicates";

   @RegisterExtension
   TestMethodNameMatchExtension testExtraBackupGroupNameReplicates = new TestMethodNameMatchExtension(TEST_EXTRA_BACKUP_GROUP_NAME_REPLICATES);

   @Test
   public void testExtraBackupReplicates() throws Exception {
      Configuration secondBackupConfig = backupConfig.copy();
      TransportConfiguration tc = secondBackupConfig.getAcceptorConfigurations().iterator().next();
      TestableServer secondBackupServer = createTestableServer(secondBackupConfig);
      tc.getParams().put("serverId", "2");
      secondBackupConfig.setBindingsDirectory(getBindingsDir(1, true)).setJournalDirectory(getJournalDir(1, true)).setPagingDirectory(getPageDir(1, true)).setLargeMessagesDirectory(getLargeMessagesDir(1, true)).setSecurityEnabled(false);

      waitForRemoteBackupSynchronization(backupServer.getServer());

      secondBackupServer.start();
      Thread.sleep(5000);
      backupServer.stop();
      waitForSync(secondBackupServer.getServer());
      waitForRemoteBackupSynchronization(secondBackupServer.getServer());

   }

   @Test
   public void testExtraBackupGroupNameReplicates() throws Exception {
      ReplicaPolicyConfiguration backupReplicaPolicyConfiguration = (ReplicaPolicyConfiguration) backupServer.getServer().getConfiguration().getHAPolicyConfiguration();
      backupReplicaPolicyConfiguration.setGroupName("foo");

      ReplicatedPolicyConfiguration replicatedPolicyConfiguration = (ReplicatedPolicyConfiguration) primaryServer.getServer().getConfiguration().getHAPolicyConfiguration();
      replicatedPolicyConfiguration.setGroupName("foo");

      Configuration secondBackupConfig = backupConfig.copy();
      TransportConfiguration tc = secondBackupConfig.getAcceptorConfigurations().iterator().next();
      TestableServer secondBackupServer = createTestableServer(secondBackupConfig);
      tc.getParams().put("serverId", "2");
      secondBackupConfig.setBindingsDirectory(getBindingsDir(1, true)).setJournalDirectory(getJournalDir(1, true)).setPagingDirectory(getPageDir(1, true)).setLargeMessagesDirectory(getLargeMessagesDir(1, true)).setSecurityEnabled(false);
      ReplicaPolicyConfiguration replicaPolicyConfiguration = (ReplicaPolicyConfiguration) secondBackupConfig.getHAPolicyConfiguration();
      replicaPolicyConfiguration.setGroupName("foo");
      waitForRemoteBackupSynchronization(backupServer.getServer());

      secondBackupServer.start();
      Thread.sleep(5000);
      backupServer.stop();
      waitForSync(secondBackupServer.getServer());
      waitForRemoteBackupSynchronization(secondBackupServer.getServer());
   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected void setupHAPolicyConfiguration() {
      if (testExtraBackupGroupNameReplicates.matches()) {
         ((ReplicatedPolicyConfiguration) primaryConfig.getHAPolicyConfiguration()).setGroupName("foo");
         ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setGroupName("foo");

      }
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }

   private void waitForSync(ActiveMQServer server) throws Exception {
      Wait.waitFor(server::isReplicaSync);
   }
}
