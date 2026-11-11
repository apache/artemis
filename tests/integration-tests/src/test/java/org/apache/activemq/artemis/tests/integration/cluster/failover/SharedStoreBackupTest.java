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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.Test;

public class SharedStoreBackupTest extends FailoverTestBase {

   @Test
   public void testStartSharedBackupWithScalingDownPolicyDisabled() throws Exception {
      primaryServer.stop();
      // wait max 10s for backup to activate
      assertTrue(waitForBackupToBecomeActive(backupServer, 10000), "Backup did not activate in 10s timeout.");
   }

   /**
    * {@return returns true if backup started in given timeout; false otherwise}
    *
    * @param backupServer backup server
    * @param waitTimeout  timeout in milliseconds
    */
   private boolean waitForBackupToBecomeActive(TestableServer backupServer, long waitTimeout) throws Exception {

      long startTime = System.currentTimeMillis();
      boolean isBackupStarted;
      // wait given timeout for backup to activate
      while (!(isBackupStarted = backupServer.isActive()) && System.currentTimeMillis() - startTime < waitTimeout) {
         Thread.sleep(300);
      }
      return isBackupStarted;
   }

   @Override
   protected void createConfigs() throws Exception {
      nodeManager = new InVMNodeManager(false);
      TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);
      backupConfig = super.createDefaultConfig(false).clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(false)).setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration().setScaleDownConfiguration(new ScaleDownConfiguration().setEnabled(false)).setRestartBackup(false)).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector).addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), primaryConnector.getName()));

      backupServer = createTestableServer(backupConfig);

      primaryConfig = super.createDefaultConfig(false).clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true)).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration().setFailoverOnServerShutdown(true)).addClusterConfiguration(basicClusterConnectionConfig(primaryConnector.getName())).addConnectorConfiguration(primaryConnector.getName(), primaryConnector);

      primaryServer = createTestableServer(primaryConfig);
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
