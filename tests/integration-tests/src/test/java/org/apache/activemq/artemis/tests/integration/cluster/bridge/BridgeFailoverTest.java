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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.tests.integration.cluster.util.MultiServerTestBase;
import org.junit.jupiter.api.Test;

public class BridgeFailoverTest extends MultiServerTestBase {

   @Test
   public void testSimpleConnectOnMultipleNodes() throws Exception {
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration();

      String ORIGINAL_QUEUE = "noCluster.originalQueue";
      String TARGET_QUEUE = "noCluster.targetQueue";

      bridgeConfiguration.setHA(true);
      List<String> connectors = new ArrayList<>();
      connectors.add("target-4");
      connectors.add("backup-4");
      bridgeConfiguration.setName("Bridge-for-test");
      bridgeConfiguration.setStaticConnectors(connectors);
      bridgeConfiguration.setQueueName(ORIGINAL_QUEUE);
      bridgeConfiguration.setForwardingAddress(TARGET_QUEUE);
      bridgeConfiguration.setRetryInterval(100);
      bridgeConfiguration.setConfirmationWindowSize(1);
      bridgeConfiguration.setReconnectAttempts(-1);
      servers[2].getConfiguration().getBridgeConfigurations().add(bridgeConfiguration);

      for (ActiveMQServer server : servers) {
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(ORIGINAL_QUEUE));
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(TARGET_QUEUE));
      }

      startServers();

      // The server where the bridge source is configured at
      ServerLocator locator = createLocator(false, 2);

      ClientSessionFactory factory = addSessionFactory(locator.createSessionFactory());
      ClientSession session = addClientSession(factory.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(ORIGINAL_QUEUE));

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("i", i);
         producer.send(msg);
      }
      session.commit();

      ServerLocator locatorConsumer = createLocator(false, 4);
      ClientSessionFactory factoryConsumer = addSessionFactory(locatorConsumer.createSessionFactory());
      ClientSession sessionConsumer = addClientSession(factoryConsumer.createSession(false, false));
      ClientConsumer consumer = sessionConsumer.createConsumer(TARGET_QUEUE);

      sessionConsumer.start();

      for (int i = 0; i < 100; i++) {
         ClientMessage message = consumer.receive(10000);
         assertNotNull(message);
         message.acknowledge();
      }

      sessionConsumer.commit();
   }

   @Test
   public void testFailoverOnBridgeNoRetryOnSameNode() throws Exception {
      internalTestFailoverOnBridge(0);
   }

   @Test
   public void testFailoverOnBridgeForeverRetryOnSameNode() throws Exception {
      internalTestFailoverOnBridge(-1);
   }

   public void internalTestFailoverOnBridge(int retriesSameNode) throws Exception {
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration();

      String ORIGINAL_QUEUE = "noCluster.originalQueue";
      String TARGET_QUEUE = "noCluster.targetQueue";

      bridgeConfiguration.setHA(true);
      List<String> connectors = new ArrayList<>();
      connectors.add("target-4");
      connectors.add("backup-4");
      bridgeConfiguration.setName("Bridge-for-test");
      bridgeConfiguration.setStaticConnectors(connectors);
      bridgeConfiguration.setQueueName(ORIGINAL_QUEUE);
      bridgeConfiguration.setForwardingAddress(TARGET_QUEUE);
      bridgeConfiguration.setRetryInterval(100);
      bridgeConfiguration.setConfirmationWindowSize(1);
      bridgeConfiguration.setReconnectAttempts(-1);
      bridgeConfiguration.setReconnectAttemptsOnSameNode(retriesSameNode);
      bridgeConfiguration.setHA(true);
      servers[2].getConfiguration().getBridgeConfigurations().add(bridgeConfiguration);

      for (ActiveMQServer server : servers) {
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(ORIGINAL_QUEUE));
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(TARGET_QUEUE));
      }

      startServers();

      BridgeImpl bridge = (BridgeImpl) servers[2].getClusterManager().getBridges().get("Bridge-for-test");
      assertNotNull(bridge);

      long timeout = System.currentTimeMillis() + 5000;

      while (bridge.getTargetNodeFromTopology() == null && timeout > System.currentTimeMillis()) {
         Thread.sleep(100);
      }

      assertNotNull(bridge.getTargetNodeFromTopology());

      // The server where the bridge source is configured at
      ServerLocator locatorProducer = createLocator(false, 2);

      ClientSessionFactory factory = addSessionFactory(locatorProducer.createSessionFactory());
      ClientSession session = addClientSession(factory.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(ORIGINAL_QUEUE));

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("i", i);
         producer.send(msg);
      }
      session.commit();

      ServerLocator locatorConsumer = createLocator(false, 4);
      ClientSessionFactory factoryConsumer = addSessionFactory(locatorConsumer.createSessionFactory());
      ClientSession sessionConsumer = addClientSession(factoryConsumer.createSession(false, false));
      ClientConsumer consumer = sessionConsumer.createConsumer(TARGET_QUEUE);

      sessionConsumer.start();

      for (int i = 0; i < 100; i++) {
         ClientMessage message = consumer.receive(10000);
         assertNotNull(message);
         message.acknowledge();
      }

      // We rollback as we will receive them again
      sessionConsumer.rollback();

      factoryConsumer.close();
      sessionConsumer.close();

      crashAndWaitForFailure(servers[4], locatorConsumer);

      locatorConsumer.close();

      assertTrue(backupServers[4].waitForActivation(5, TimeUnit.SECONDS), "Backup server didn't activate.");

      for (int i = 100; i < 200; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("i", i);
         producer.send(msg);
      }

      session.commit();

      locatorConsumer = createLocator(false, 9);
      factoryConsumer = addSessionFactory(locatorConsumer.createSessionFactory());
      sessionConsumer = addClientSession(factoryConsumer.createSession());

      consumer = sessionConsumer.createConsumer(TARGET_QUEUE);

      sessionConsumer.start();

      for (int i = 0; i < 200; i++) {
         ClientMessage message = consumer.receive(10000);
         assertNotNull(message);
         message.acknowledge();
      }

      sessionConsumer.commit();

   }

   @Test
   public void testInitialConnectionNodeAlreadyDown() throws Exception {
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration();

      String ORIGINAL_QUEUE = "noCluster.originalQueue";
      String TARGET_QUEUE = "noCluster.targetQueue";

      bridgeConfiguration.setHA(true);
      List<String> connectors = new ArrayList<>();
      connectors.add("target-4");
      connectors.add("backup-4");
      bridgeConfiguration.setName("Bridge-for-test");
      bridgeConfiguration.setStaticConnectors(connectors);
      bridgeConfiguration.setQueueName(ORIGINAL_QUEUE);
      bridgeConfiguration.setForwardingAddress(TARGET_QUEUE);
      bridgeConfiguration.setRetryInterval(100);
      bridgeConfiguration.setConfirmationWindowSize(1);
      bridgeConfiguration.setReconnectAttempts(-1);
      servers[2].getConfiguration().getBridgeConfigurations().add(bridgeConfiguration);

      for (ActiveMQServer server : servers) {
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(ORIGINAL_QUEUE));
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(TARGET_QUEUE));
      }

      startBackups(0, 1, 3, 4);
      startServers(0, 1, 3, 4);

      waitForTopology(servers[4], getNumberOfServers() - 1, getNumberOfServers() - 1);

      crashAndWaitForFailure(servers[4], createLocator(false, 4));
      waitForServerToStart(backupServers[4]);

      startBackups(2);
      startServers(2);

      // The server where the bridge source is configured at
      ServerLocator locator = createLocator(false, 2); // connecting to the backup

      ClientSessionFactory factory = addSessionFactory(locator.createSessionFactory());
      ClientSession session = addClientSession(factory.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(ORIGINAL_QUEUE));

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("i", i);
         producer.send(msg);
      }
      session.commit();

      ServerLocator locatorConsumer = createLocator(false, 9);
      ClientSessionFactory factoryConsumer = addSessionFactory(locatorConsumer.createSessionFactory());
      ClientSession sessionConsumer = addClientSession(factoryConsumer.createSession(false, false));
      ClientConsumer consumer = sessionConsumer.createConsumer(TARGET_QUEUE);

      sessionConsumer.start();

      for (int i = 0; i < 100; i++) {
         ClientMessage message = consumer.receive(10000);
         assertNotNull(message);
         message.acknowledge();
      }

      sessionConsumer.commit();
   }
}
