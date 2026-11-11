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
package org.apache.activemq.artemis.tests.integration.cluster.bridge;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageCounterOnBridgeFailoverTest extends FailoverTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private Map<String, Object> dc2Primary1Conf;
   private TransportConfiguration dc2Primary1tc;

   @Test
   public void testCounterAfterBridgeFailover() throws Exception {
      ActiveMQServer dc1Primary1 = primaryServer.getServer();
      ActiveMQServer dc1Backup1 = backupServer.getServer();
      ActiveMQServer dc2Primary1 = createClusteredServerWithParams(true, 3, true, dc2Primary1Conf);
      final String bridgeName = "bridge1";
      final String queueName0 = "queue0";
      logger.info("Starting remote cluster node  dc2Primary1");
      dc2Primary1.start();
      waitForServerToStart(dc2Primary1);
      try {
         Queue queue = dc2Primary1.createQueue(QueueConfiguration.of(queueName0).setDurable(true));

         int messageSent = 2000;
         logger.info("Producing {} messages ", messageSent);
         try (ClientSessionFactory sessionFactory = createSessionFactory(getServerLocator())) {
            ClientSession session1 = sessionFactory.createSession(false, false);
            QueueConfiguration queueConfiguration = QueueConfiguration.of(queueName0).setDurable(true);
            session1.createQueue(queueConfiguration);
            ClientProducer producerServer1 = session1.createProducer(queueName0);
            for (int i = 0; i < messageSent; i++) {
               ClientMessage msg = session1.createMessage(true);
               if (i % 2 == 0) {
                  setLargeMessageBody(0, msg);
               } else {
                  setBody(i, msg);
               }
               producerServer1.send(msg);
            }
            session1.commit();
            producerServer1.close();
            session1.close();
         }

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName(bridgeName)
            .setQueueName(queueName0).setRetryInterval(1000)
            .setUseDuplicateDetection(true)
            .setStaticConnectors(List.of(dc2Primary1tc.getName()));

         logger.info("Starting bridge: " + bridgeConfiguration.getName());
         dc1Primary1.getClusterManager().deployBridge(bridgeConfiguration);

         Wait.waitFor(() -> messageSent - getMessageCount(dc1Primary1, queueName0) > 100);
         logger.info("Stopping dc1Primary1");
         dc1Primary1.stop();
         logger.info("Waiting for dc1Backup1 to be alive");
         Wait.waitFor(dc1Backup1::isActive, 15000);
         logger.info("dc1Backup1 isAlive");
         long messageCountNodeDC2 = getMessageCount(dc2Primary1, queueName0);
         dc1Backup1.getClusterManager().deployBridge(bridgeConfiguration);
         Wait.assertEquals(messageSent, () -> getMessageCount(dc2Primary1, queueName0));
         Wait.assertEquals(0, () -> getMessageCount(dc1Backup1, queueName0));
      } finally {
         dc2Primary1.stop();
      }
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean primary) {
      return TransportConfigurationUtils.getInVMAcceptor(primary);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean primary) {
      return TransportConfigurationUtils.getInVMConnector(primary);
   }


   @Override
   protected void createConfigs() throws Exception {
      dc2Primary1Conf = new HashMap<>();
      dc2Primary1Conf.put("host", TransportConstants.DEFAULT_HOST);
      dc2Primary1Conf.put("port", TransportConstants.DEFAULT_PORT + 3);
      dc2Primary1tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY, dc2Primary1Conf);
      nodeManager = new InVMNodeManager(false);
      TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);
      backupConfig = super.createDefaultConfig(true).setPersistenceEnabled(true)
         .clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(false))
         .setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration().setScaleDownConfiguration(new ScaleDownConfiguration().setEnabled(false)).setRestartBackup(false))
         .addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector)
         .addConnectorConfiguration(dc2Primary1tc.getName(), dc2Primary1tc).addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), primaryConnector.getName()))
         .addAddressSetting("#", new AddressSettings().setMaxSizeBytes(1).setMaxReadPageBytes(-1).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE));

      backupServer = createTestableServer(backupConfig);

      primaryConfig = super.createDefaultConfig(true).setPersistenceEnabled(true)
         .clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true))
         .setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration().setFailoverOnServerShutdown(true))
         .addClusterConfiguration(basicClusterConnectionConfig(primaryConnector.getName())).addConnectorConfiguration(dc2Primary1tc.getName(), dc2Primary1tc)
         .addConnectorConfiguration(primaryConnector.getName(), primaryConnector)
         .addAddressSetting("#", new AddressSettings().setMaxSizeBytes(1).setMaxReadPageMessages(-1).setMaxReadPageBytes(-1).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE));

      primaryServer = createTestableServer(primaryConfig);
   }
}