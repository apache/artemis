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

package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpTestSupport;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.ReplicatedBackupUtils;
import org.junit.jupiter.api.BeforeEach;

public abstract class AmqpReplicatedTestSupport extends AmqpTestSupport {

   protected TestableServer primaryServer;

   protected TestableServer backupServer;

   protected Configuration backupConfig;

   protected Configuration primaryConfig;

   protected abstract TransportConfiguration getAcceptorTransportConfiguration(boolean live);

   protected abstract TransportConfiguration getConnectorTransportConfiguration(boolean live);

   protected NodeManager nodeManager;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      createReplicatedConfigs();
   }

   protected void createReplicatedConfigs() throws Exception {
      final TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      final TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);
      final TransportConfiguration backupAcceptor = getAcceptorTransportConfiguration(false);

      backupConfig = createDefaultInVMConfig();
      primaryConfig = createDefaultInVMConfig();

      ReplicatedBackupUtils.configureReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, null);

      backupConfig.setBindingsDirectory(getBindingsDir(0, true)).setJournalDirectory(getJournalDir(0, true)).setPagingDirectory(getPageDir(0, true)).setLargeMessagesDirectory(getLargeMessagesDir(0, true)).setSecurityEnabled(false);

      setupHAPolicyConfiguration();
      nodeManager = createReplicatedBackupNodeManager(backupConfig);

      backupServer = createTestableServer(backupConfig);

      primaryConfig.clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true));

      primaryServer = createTestableServer(primaryConfig);
   }

   /**
    * Override this if is needed a different implementation of {@link NodeManager} to be used into
    * {@link #createReplicatedConfigs()}.
    */
   protected NodeManager createReplicatedBackupNodeManager(Configuration backupConfig) {
      return new InVMNodeManager(true, backupConfig.getJournalLocation());
   }

   protected void setupHAPolicyConfiguration() {
      ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setMaxSavedReplicatedJournalsSize(-1).setAllowFailBack(true);
      ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setRestartBackup(false);
   }

   protected TestableServer createTestableServer(Configuration config) throws Exception {
      return createTestableServer(config, nodeManager);
   }

   protected TestableServer createTestableServer(Configuration config, NodeManager nodeManager) throws Exception {
      boolean isBackup = config.getHAPolicyConfiguration() instanceof ReplicaPolicyConfiguration || config.getHAPolicyConfiguration() instanceof SharedStoreBackupPolicyConfiguration;
      return new SameProcessActiveMQServer(createInVMFailoverServer(true, config, nodeManager, isBackup ? 2 : 1));
   }

}
