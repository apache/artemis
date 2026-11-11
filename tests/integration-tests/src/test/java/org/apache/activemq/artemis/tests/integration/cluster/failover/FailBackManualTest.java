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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.CountDownSessionFailureListener;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FailBackManualTest extends FailoverTestBase {

   private ServerLocatorInternal locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = getServerLocator();
   }

   @Test
   public void testNoAutoFailback() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(15);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sendAndConsume(sf, true);

      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(1, session);

      session.addFailureListener(listener);

      backupServer.stop();

      primaryServer.crash();

      backupServer.start();

      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.removeFailureListener(listener);

      Thread t = new Thread(new ServerStarter(primaryServer));

      t.start();

      waitForRemoteBackup(sf, 10, false, backupServer.getServer());

      assertTrue(backupServer.isStarted());

      backupServer.crash();

      waitForServerToStart(primaryServer.getServer());

      assertTrue(primaryServer.isStarted());

      sf.close();

      assertEquals(0, sf.numSessions());

      assertEquals(0, sf.numConnections());
   }

   @Override
   protected void createConfigs() throws Exception {
      nodeManager = new InVMNodeManager(false);
      TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);

      backupConfig = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(false)).setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration().setAllowFailBack(false)).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector).addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), primaryConnector.getName()));

      backupServer = createTestableServer(backupConfig);

      primaryConfig = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true)).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector).addClusterConfiguration(basicClusterConnectionConfig(primaryConnector.getName(), backupConnector.getName()));

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

   @Override
   protected void setBody(final int i, final ClientMessage message) {
      message.getBodyBuffer().writeString("message" + i);
   }

   static class ServerStarter implements Runnable {

      private final TestableServer server;

      ServerStarter(TestableServer server) {
         this.server = server;
      }

      @Override
      public void run() {
         try {
            server.start();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }
}
