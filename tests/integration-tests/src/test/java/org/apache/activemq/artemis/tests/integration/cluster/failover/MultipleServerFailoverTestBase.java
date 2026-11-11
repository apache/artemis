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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public abstract class MultipleServerFailoverTestBase extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private DistributedLockManagerConfiguration pluggableQuorumConfiguration = null;

   private DistributedLockManagerConfiguration getOrCreatePluggableQuorumConfiguration() {
      if (pluggableQuorumConfiguration != null) {
         return pluggableQuorumConfiguration;
      }
      try {
         pluggableQuorumConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(), Collections.singletonMap("locks-folder", newFolder(temporaryFolder, "manager").toString()));
      } catch (IOException ioException) {
         return null;
      }
      return pluggableQuorumConfiguration;
   }



   // TODO: find a better solution for this
   // this is necessary because the cluster connection is using "jms" as its match; see org.apache.activemq.artemis.tests.util.ActiveMQTestBase.basicClusterConnectionConfig()
   protected static final SimpleString ADDRESS = SimpleString.of("jms.FailoverTestAddress");


   protected List<TestableServer> primaryServers = new ArrayList<>();

   protected List<TestableServer> backupServers = new ArrayList<>();

   protected List<Configuration> backupConfigs = new ArrayList<>();

   protected List<Configuration> primaryConfigs = new ArrayList<>();

   protected List<NodeManager> nodeManagers;

   public abstract int getPrimaryServerCount();

   public abstract int getBackupServerCount();

   public abstract boolean isNetty();

   public enum HAType {
      SharedStore, SharedNothingReplication, PluggableQuorumReplication
   }

   public abstract HAType haType();

   protected final boolean isSharedStore() {
      return ClusterTestBase.HAType.SharedStore.equals(haType());
   }

   public abstract String getNodeGroupName();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      primaryServers = new ArrayList<>();
      backupServers = new ArrayList<>();
      backupConfigs = new ArrayList<>();
      primaryConfigs = new ArrayList<>();

      for (int i = 0; i < getPrimaryServerCount(); i++) {
         HAPolicyConfiguration haPolicyConfiguration = null;
         switch (haType()) {

            case SharedStore:
               haPolicyConfiguration = new SharedStorePrimaryPolicyConfiguration();
               break;
            case SharedNothingReplication:
               haPolicyConfiguration = new ReplicatedPolicyConfiguration();
               if (getNodeGroupName() != null) {
                  ((ReplicatedPolicyConfiguration) haPolicyConfiguration).setGroupName(getNodeGroupName() + "-" + i);
               }
               break;
            case PluggableQuorumReplication:
               haPolicyConfiguration = ReplicationPrimaryPolicyConfiguration.withDefault()
                  .setDistributedManagerConfiguration(getOrCreatePluggableQuorumConfiguration())
                  .setGroupName(getNodeGroupName() != null ? (getNodeGroupName() + "-" + i) : null);
               break;
         }

         Configuration configuration = createDefaultConfig(isNetty()).clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true, i)).setHAPolicyConfiguration(haPolicyConfiguration);

         if (!isSharedStore()) {
            configuration.setBindingsDirectory(getBindingsDir(i, false));
            configuration.setJournalDirectory(getJournalDir(i, false));
            configuration.setPagingDirectory(getPageDir(i, false));
            configuration.setLargeMessagesDirectory(getLargeMessagesDir(i, false));
         } else {
            //todo
         }

         TransportConfiguration livetc = getConnectorTransportConfiguration(true, i);
         configuration.addConnectorConfiguration(livetc.getName(), livetc);
         List<String> connectors = new ArrayList<>();
         for (int j = 0; j < getPrimaryServerCount(); j++) {
            if (j != i) {
               TransportConfiguration staticTc = getConnectorTransportConfiguration(true, j);
               configuration.getConnectorConfigurations().put(staticTc.getName(), staticTc);
               connectors.add(staticTc.getName());
            }
         }

         String[] input = new String[connectors.size()];
         connectors.toArray(input);
         configuration.addClusterConfiguration(basicClusterConnectionConfig(livetc.getName(), input));
         primaryConfigs.add(configuration);
         ActiveMQServer server = createServer(true, configuration);
         TestableServer activeMQServer = new SameProcessActiveMQServer(server);
         activeMQServer.setIdentity("Primary-" + i);
         primaryServers.add(activeMQServer);
      }
      for (int i = 0; i < getBackupServerCount(); i++) {
         HAPolicyConfiguration haPolicyConfiguration = null;

         switch (haType()) {

            case SharedStore:
               haPolicyConfiguration = new SharedStoreBackupPolicyConfiguration();
               break;
            case SharedNothingReplication:
               haPolicyConfiguration = new ReplicaPolicyConfiguration();
               if (getNodeGroupName() != null) {
                  ((ReplicaPolicyConfiguration) haPolicyConfiguration).setGroupName(getNodeGroupName() + "-" + i);
               }
               break;
            case PluggableQuorumReplication:
               haPolicyConfiguration = ReplicationBackupPolicyConfiguration.withDefault()
                  .setDistributedManagerConfiguration(getOrCreatePluggableQuorumConfiguration())
                  .setGroupName(getNodeGroupName() != null ? (getNodeGroupName() + "-" + i) : null);
               break;
         }

         Configuration configuration = createDefaultConfig(isNetty()).clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(false, i)).setHAPolicyConfiguration(haPolicyConfiguration);

         if (!isSharedStore()) {
            configuration.setBindingsDirectory(getBindingsDir(i, true));
            configuration.setJournalDirectory(getJournalDir(i, true));
            configuration.setPagingDirectory(getPageDir(i, true));
            configuration.setLargeMessagesDirectory(getLargeMessagesDir(i, true));
         } else {
            //todo
         }

         TransportConfiguration backuptc = getConnectorTransportConfiguration(false, i);
         configuration.addConnectorConfiguration(backuptc.getName(), backuptc);
         List<String> connectors = new ArrayList<>();
         for (int j = 0; j < getBackupServerCount(); j++) {
            TransportConfiguration staticTc = getConnectorTransportConfiguration(true, j);
            configuration.addConnectorConfiguration(staticTc.getName(), staticTc);
            connectors.add(staticTc.getName());
         }
         for (int j = 0; j < getBackupServerCount(); j++) {
            if (j != i) {
               TransportConfiguration staticTc = getConnectorTransportConfiguration(false, j);
               configuration.getConnectorConfigurations().put(staticTc.getName(), staticTc);
               connectors.add(staticTc.getName());
            }
         }
         String[] input = new String[connectors.size()];
         connectors.toArray(input);
         configuration.addClusterConfiguration(basicClusterConnectionConfig(backuptc.getName(), input));
         backupConfigs.add(configuration);
         ActiveMQServer server = createServer(true, configuration);
         TestableServer testableServer = new SameProcessActiveMQServer(server);
         testableServer.setIdentity("Backup-" + i);
         backupServers.add(testableServer);
      }
   }

   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live, int node) {
      TransportConfiguration transportConfiguration;
      if (isNetty()) {
         transportConfiguration = TransportConfigurationUtils.getNettyAcceptor(live, node, (live ? "live-" : "backup-") + node);
      } else {
         transportConfiguration = TransportConfigurationUtils.getInVMAcceptor(live, node, (live ? "live-" : "backup-") + node);
      }
      return transportConfiguration;
   }

   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live, int node) {
      TransportConfiguration transportConfiguration;
      if (isNetty()) {
         transportConfiguration = TransportConfigurationUtils.getNettyConnector(live, node, (live ? "live-" : "backup-") + node);
      } else {
         transportConfiguration = TransportConfigurationUtils.getInVMConnector(live, node, (live ? "live-" : "backup-") + node);
      }
      return transportConfiguration;
   }

   protected ServerLocatorInternal getServerLocator(int node) throws Exception {
      return (ServerLocatorInternal) addServerLocator(ActiveMQClient.createServerLocatorWithHA(getConnectorTransportConfiguration(true, node))).setRetryInterval(100).setReconnectAttempts(300).setInitialConnectAttempts(300);
   }

   protected ServerLocatorInternal getBackupServerLocator(int node) throws Exception {
      return (ServerLocatorInternal) addServerLocator(ActiveMQClient.createServerLocatorWithHA(getConnectorTransportConfiguration(false, node))).setRetryInterval(100).setReconnectAttempts(300).setInitialConnectAttempts(300);
   }

   protected ClientSession createSession(ClientSessionFactory sf,
                                         boolean autoCommitSends,
                                         boolean autoCommitAcks,
                                         int ackBatchSize) throws Exception {
      return addClientSession(sf.createSession(autoCommitSends, autoCommitAcks, ackBatchSize));
   }

   protected ClientSession createSession(ClientSessionFactory sf,
                                         boolean autoCommitSends,
                                         boolean autoCommitAcks) throws Exception {
      return addClientSession(sf.createSession(autoCommitSends, autoCommitAcks));
   }

   protected ClientSession createSession(ClientSessionFactory sf) throws Exception {
      return addClientSession(sf.createSession());
   }

   protected ClientSession createSession(ClientSessionFactory sf,
                                         boolean xa,
                                         boolean autoCommitSends,
                                         boolean autoCommitAcks) throws Exception {
      return addClientSession(sf.createSession(xa, autoCommitSends, autoCommitAcks));
   }

   protected boolean waitForDistribution(SimpleString address, ActiveMQServer server, int messageCount) throws Exception {
      logger.debug("waiting for distribution of messages on server {}", server);

      Queue q = (Queue) server.getPostOffice().getBinding(address).getBindable();

      return Wait.waitFor(() -> {
         return getMessageCount(q) >= messageCount;
      });

   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
