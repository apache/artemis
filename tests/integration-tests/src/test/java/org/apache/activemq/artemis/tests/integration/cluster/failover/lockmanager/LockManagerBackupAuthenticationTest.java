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
package org.apache.activemq.artemis.tests.integration.cluster.failover.lockmanager;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FakeServiceComponent;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class LockManagerBackupAuthenticationTest extends FailoverTestBase {

   private static CountDownLatch registrationStarted;

   @Parameter(index = 0)
   public boolean useNetty;

   @Parameters(name = "useNetty={1}")
   public static Iterable<Object[]> getParams() {
      return asList(new Object[][]{{false}, {true}});
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      startBackupServer = false;
      registrationStarted = new CountDownLatch(1);
      super.setUp();
   }

   @TestTemplate
   public void testWrongPasswordSetting() throws Exception {
      FakeServiceComponent fakeServiceComponent = new FakeServiceComponent("fake web server");
      Wait.assertTrue(primaryServer.getServer()::isActive);
      waitForServerToStart(primaryServer.getServer());
      backupServer.start();
      backupServer.getServer().addExternalComponent(fakeServiceComponent, true);
      // wait for the backup to register its broker plugins (server started) before
      // asserting it stops due to the wrong cluster password
      assertTrue(registrationStarted.await(5, TimeUnit.SECONDS));
      Wait.waitFor(() -> !backupServer.isStarted());
      assertFalse(backupServer.isStarted(), "backup should have stopped");
      Wait.assertFalse(fakeServiceComponent::isStarted);
      backupServer.stop();
      primaryServer.stop();
   }

   @Override
   protected void createConfigs() throws Exception {
      createPluggableReplicatedConfigs();
      backupConfig.setClusterPassword("crocodile");
      backupConfig.setSecurityEnabled(true);
      primaryConfig.setSecurityEnabled(true);
      backupConfig.registerBrokerPlugin(new ActiveMQServerBasePlugin() {
         @Override
         public void registered(ActiveMQServer server) {
            registrationStarted.countDown();
         }
      });
   }

   @Override
   protected void setupHAPolicyConfiguration() {
      ((ReplicationBackupPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setMaxSavedReplicatedJournalsSize(2).setAllowFailBack(true);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return useNetty ? getNettyAcceptorTransportConfiguration(live) :
         TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return useNetty ? getNettyConnectorTransportConfiguration(live) :
         TransportConfigurationUtils.getInVMConnector(live);
   }
}
