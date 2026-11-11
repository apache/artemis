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
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.ColocatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PrimaryToPrimaryFailoverTest extends FailoverTestBase {

   private InVMNodeManager nodeManager0;
   private InVMNodeManager nodeManager1;
   private ClientSessionFactoryInternal sf2;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      locator = getServerLocator();
   }

   @Override
   protected void createConfigs() throws Exception {
      nodeManager0 = new InVMNodeManager(false);
      nodeManager1 = new InVMNodeManager(false);
      TransportConfiguration primaryConnector0 = getConnectorTransportConfiguration(true, 0);
      TransportConfiguration primaryConnector1 = getConnectorTransportConfiguration(true, 1);

      backupConfig = super.createDefaultInVMConfig(1).clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true, 1)).setHAPolicyConfiguration(new ColocatedPolicyConfiguration().setRequestBackup(true).setPrimaryConfig(new SharedStorePrimaryPolicyConfiguration()).setBackupConfig(new SharedStoreBackupPolicyConfiguration().setScaleDownConfiguration(new ScaleDownConfiguration().addConnector(primaryConnector1.getName())))).addConnectorConfiguration(primaryConnector0.getName(), primaryConnector0).addConnectorConfiguration(primaryConnector1.getName(), primaryConnector1).addClusterConfiguration(basicClusterConnectionConfig(primaryConnector1.getName(), primaryConnector0.getName()));

      backupServer = createColocatedTestableServer(backupConfig, nodeManager1, nodeManager0, 1);

      primaryConfig = super.createDefaultInVMConfig(0).clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true, 0)).setHAPolicyConfiguration(new ColocatedPolicyConfiguration().setRequestBackup(true).setBackupRequestRetryInterval(1000).setPrimaryConfig(new SharedStorePrimaryPolicyConfiguration()).setBackupConfig(new SharedStoreBackupPolicyConfiguration().setScaleDownConfiguration(new ScaleDownConfiguration()))).addConnectorConfiguration(primaryConnector0.getName(), primaryConnector0).addConnectorConfiguration(primaryConnector1.getName(), primaryConnector1).addClusterConfiguration(basicClusterConnectionConfig(primaryConnector0.getName(), primaryConnector1.getName()));

      primaryServer = createColocatedTestableServer(primaryConfig, nodeManager0, nodeManager1, 0);
   }

   @Override
   protected void setPrimaryIdentity() {
      primaryServer.setIdentity(this.getClass().getSimpleName() + "/liveServer0");
   }

   @Override
   protected void setBackupIdentity() {
      backupServer.setIdentity(this.getClass().getSimpleName() + "/liveServer1");
   }

   @Override
   protected void waitForBackup() {
      Map<String, ActiveMQServer> backupServers0 = primaryServer.getServer().getClusterManager().getHAManager().getBackupServers();
      Map<String, ActiveMQServer> backupServers1 = backupServer.getServer().getClusterManager().getHAManager().getBackupServers();
      final long toWait = 10000;
      final long time = System.currentTimeMillis();
      while (true) {
         if (backupServers0.size() == 1 && backupServers1.size() == 1) {
            break;
         }
         if (System.currentTimeMillis() > (time + toWait)) {
            fail("backup started? ( primary server0 backups = " + backupServers0.size() + " primary server1 backups = " + backupServers1.size() + ")");
         }
         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            fail(e.getMessage());
         }
      }
      waitForRemoteBackupSynchronization(backupServers0.values().iterator().next());
      waitForRemoteBackupSynchronization(backupServers1.values().iterator().next());
   }

   @Override
   protected final ClientSessionFactoryInternal createSessionFactoryAndWaitForTopology(ServerLocator locator,
                                                                                       int topologyMembers) throws Exception {
      CountDownLatch countDownLatch = new CountDownLatch(topologyMembers * 2);

      locator.addClusterTopologyListener(new LatchClusterTopologyListener(countDownLatch));

      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal) locator.createSessionFactory();
      addSessionFactory(sf);

      assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "topology members expected " + topologyMembers);

      closeSessionFactory(sf);

      sf = (ClientSessionFactoryInternal) locator.createSessionFactory(primaryServer.getServer().getNodeID().toString());
      addSessionFactory(sf);

      if (sf2 == null) {
         sf2 = (ClientSessionFactoryInternal) locator.createSessionFactory(backupServer.getServer().getNodeID().toString());

         ClientSession session2 = createSession(sf2, false, false);
         session2.createQueue(QueueConfiguration.of(ADDRESS));
         addSessionFactory(sf2);
      }

      return sf;
   }

   protected final ClientSessionFactoryInternal createSessionFactoryAndWaitForTopology(ServerLocator locator,
                                                                                       TransportConfiguration transportConfiguration,
                                                                                       int topologyMembers) throws Exception {
      CountDownLatch countDownLatch = new CountDownLatch(topologyMembers * 2);

      locator.addClusterTopologyListener(new LatchClusterTopologyListener(countDownLatch));

      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal) locator.createSessionFactory(transportConfiguration);
      addSessionFactory(sf);

      assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "topology members expected " + topologyMembers);

      closeSessionFactory(sf);

      sf = (ClientSessionFactoryInternal) locator.createSessionFactory(primaryServer.getServer().getNodeID().toString());
      addSessionFactory(sf);

      if (sf2 == null) {
         sf2 = (ClientSessionFactoryInternal) locator.createSessionFactory(backupServer.getServer().getNodeID().toString());

         ClientSession session2 = createSession(sf2, false, false);
         session2.createQueue(QueueConfiguration.of(ADDRESS));
         addSessionFactory(sf2);
      }
      return sf;
   }

   @Override
   protected void createClientSessionFactory() throws Exception {
      if (primaryServer.getServer().isStarted()) {
         sf = (ClientSessionFactoryInternal) createSessionFactory(locator);
         sf = (ClientSessionFactoryInternal) locator.createSessionFactory(primaryServer.getServer().getNodeID().toString());
      } else {
         sf = (ClientSessionFactoryInternal) createSessionFactory(locator);
      }
   }

   protected void createSessionFactory() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, getConnectorTransportConfiguration(true, 0), 2);

      if (sf2 == null) {
         sf2 = (ClientSessionFactoryInternal) locator.createSessionFactory(backupServer.getServer().getNodeID().toString());
         addSessionFactory(sf2);
         ClientSession session2 = createSession(sf2, false, false);
         session2.createQueue(QueueConfiguration.of(ADDRESS));
      }
   }

   private TransportConfiguration getConnectorTransportConfiguration(boolean primary, int server) {
      return TransportConfigurationUtils.getInVMConnector(primary, server);
   }

   private TransportConfiguration getAcceptorTransportConfiguration(boolean primary, int server) {
      return TransportConfigurationUtils.getInVMAcceptor(primary, server);
   }

   /**
    * TODO: https://issues.apache.org/jira/browse/ARTEMIS-2709
    *       this test has been intermittently failing since its day one.
    *       Ignoring the test for now until we can fix it.
    */
   @Test
   public void scaleDownDelay() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      //send enough messages to ensure that when the client fails over scaledown hasn't complete
      sendMessages(session, producer, 1000);

      crash(session);

      // Wait for failover to happen
      Queue serverQueue = backupServer.getServer().locateQueue(ADDRESS);

      Wait.assertEquals(1000, serverQueue::getMessageCount);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      receiveDurableMessages(consumer);

      session.close();

      sf.close();

      assertEquals(0, sf.numSessions());

      assertEquals(0, sf.numConnections());
   }

   @Test
   public void testFailoverOnInitialConnection() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      // Crash primary server
      crash();

      ClientSession session = createSession(sf);

      //session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      sendMessages(session, producer, NUM_MESSAGES);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      receiveMessages(consumer);

      session.close();
   }

   @Test
   public void testCreateNewFactoryAfterFailover() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sendAndConsume(sf, true);

      crash(true, session);

      session.close();

      long timeout;
      timeout = System.currentTimeMillis() + 5000;
      while (timeout > System.currentTimeMillis()) {
         try {
            createClientSessionFactory();
            break;
         } catch (Exception e) {
            // retrying
            Thread.sleep(100);
         }
      }

      session = sendAndConsume(sf, false);
   }
}


