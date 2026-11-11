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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.cluster.BackupManager;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class BackupManagerConfigTest extends FailoverTestBase {

   @Override
   protected void createConfigs() throws Exception {
      nodeManager = createNodeManager();
      TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);

      backupConfig = super.createDefaultInVMConfig()
                          .clearAcceptorConfigurations()
                          .addAcceptorConfiguration(getAcceptorTransportConfiguration(false))
                          .setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration())
                          .addConnectorConfiguration(primaryConnector.getName(), primaryConnector)
                          .addConnectorConfiguration(backupConnector.getName(), backupConnector)
                          .addClusterConfiguration(createBasicClusterConfig(backupConnector.getName(), primaryConnector.getName())
                                                      .setCallTimeout(333));

      backupServer = createTestableServer(backupConfig);

      primaryConfig = super.createDefaultInVMConfig()
                           .clearAcceptorConfigurations()
                           .addAcceptorConfiguration(getAcceptorTransportConfiguration(true))
                           .setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration())
                           .addConnectorConfiguration(primaryConnector.getName(), primaryConnector)
                           .addClusterConfiguration(createBasicClusterConfig(primaryConnector.getName()));

      primaryServer = createTestableServer(primaryConfig);
   }

   @Test
   public void testCallTimeout() {
      ActiveMQServerImpl server = (ActiveMQServerImpl) backupServer.getServer();
      for (BackupManager.BackupConnector backupConnector : server.getBackupManager().getBackupConnectors()) {

         Wait.assertTrue(() -> backupConnector.getBackupServerLocator() != null);
         assertEquals(333, backupConnector.getBackupServerLocator().getCallTimeout());
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

}
