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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.Set;

public class ScaleDown3NodeTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String userName = "myUser";
   private final String password = "myPassword";
   private final String role = "myRole";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), HAType.SharedNothingReplication, isNetty(), true);
      servers[0].getConfiguration().setSecurityEnabled(true);
      configureSecurity(servers[0]);
      setupPrimaryServer(1, isFileStorage(), HAType.SharedNothingReplication, isNetty(), true);
      configureSecurity(servers[1]);
      setupPrimaryServer(2, isFileStorage(), HAType.SharedNothingReplication, isNetty(), true);
      configureSecurity(servers[2]);
      PrimaryOnlyPolicyConfiguration haPolicyConfiguration0 = (PrimaryOnlyPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration();
      ScaleDownConfiguration scaleDownConfiguration0 = new ScaleDownConfiguration();
      haPolicyConfiguration0.setScaleDownConfiguration(scaleDownConfiguration0);
      PrimaryOnlyPolicyConfiguration haPolicyConfiguration1 = (PrimaryOnlyPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration();
      ScaleDownConfiguration scaleDownConfiguration1 = new ScaleDownConfiguration();
      haPolicyConfiguration1.setScaleDownConfiguration(scaleDownConfiguration1);
      scaleDownConfiguration0.setGroupName("bill");
      scaleDownConfiguration1.setGroupName("bill");
      scaleDownConfiguration1.setEnabled(false);

      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1);
      String scaleDownConnector = servers[0].getConfiguration().getClusterConfigurations().get(0).getStaticConnectors().get(0);
      assertEquals(61617, servers[0].getConfiguration().getConnectorConfigurations().get(scaleDownConnector).getParams().get(TransportConstants.PORT_PROP_NAME));
      scaleDownConfiguration0.getConnectors().add(scaleDownConnector);
      startServers(0, 1, 2);
      setupSessionFactory(0, isNetty(), false, servers[0].getConfiguration().getClusterUser(), servers[0].getConfiguration().getClusterPassword());
      setupSessionFactory(1, isNetty(), false, servers[1].getConfiguration().getClusterUser(), servers[1].getConfiguration().getClusterPassword());
      setupSessionFactory(2, isNetty(), false, servers[2].getConfiguration().getClusterUser(), servers[2].getConfiguration().getClusterPassword());
      logger.debug("===============================");
      logger.debug("Node 0: {}", servers[0].getClusterManager().getNodeId());
      logger.debug("Node 1: {}", servers[1].getClusterManager().getNodeId());
      logger.debug("Node 2: {}", servers[2].getClusterManager().getNodeId());
      logger.debug("===============================");

      servers[0].setIdentity("Node0");
      servers[1].setIdentity("Node1");
      servers[2].setIdentity("Node2");
   }

   private void configureSecurity(ActiveMQServer server) {
      SecurityConfiguration securityConfiguration = ((ActiveMQJAASSecurityManager)server.getSecurityManager()).getConfiguration();
      securityConfiguration.addUser(userName, password);
      securityConfiguration.addRole(userName, role);
      server.getSecurityRepository().addMatch("testAddress.#", Set.of(new Role(role, false, true, false, false, true, false, false, false, true, false, false, false)));
   }

   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testBasicScaleDownWithDefaultReconnectAttempts() throws Exception {
      testBasicScaleDownInternal(ActiveMQDefaultConfiguration.getDefaultBridgeReconnectAttempts(), false);
   }

   @Test
   public void testBasicScaleDownWithoutBridgeReconnect() throws Exception {
      testBasicScaleDownInternal(0, false);
   }

   @Test
   public void testBasicScaleDownWithDefaultReconnectAttemptsAndLargeMessages() throws Exception {
      testBasicScaleDownInternal(ActiveMQDefaultConfiguration.getDefaultBridgeReconnectAttempts(), true);
   }

   private void testBasicScaleDownInternal(int reconnectAttempts, boolean large) throws Exception {
      AddressSettings addressSettings = new AddressSettings().setRedistributionDelay(0);
      servers[0].getAddressSettingsRepository().addMatch("#", addressSettings);
      servers[1].getAddressSettingsRepository().addMatch("#", addressSettings);
      servers[2].getAddressSettingsRepository().addMatch("#", addressSettings);

      servers[0].getConfiguration().getClusterConfigurations().get(0).setReconnectAttempts(reconnectAttempts);
      servers[1].getConfiguration().getClusterConfigurations().get(0).setReconnectAttempts(reconnectAttempts);
      servers[2].getConfiguration().getClusterConfigurations().get(0).setReconnectAttempts(reconnectAttempts);

      final int TEST_SIZE = 10;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";

      // create a queue on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, false, userName, password);
      createQueue(1, addressName, queueName1, null, false, userName, password);
      createQueue(2, addressName, queueName1, null, false, userName, password);

      // pause the SnF queue so that when the server tries to redistribute a message it won't actually go across the cluster bridge
      final String snfAddress = servers[0].getInternalNamingPrefix() + "sf.cluster0." + servers[0].getNodeID().toString();
      Queue snfQueue = ((LocalQueueBinding) servers[2].getPostOffice().getBinding(SimpleString.of(snfAddress))).getQueue();
      snfQueue.pause();

      ClientSession session = sfs[2].createSession(userName, password, false, true, false, false, 0);

      Message message;

      if (large) {
         LargeServerMessageImpl fileMessage = new LargeServerMessageImpl(servers[2].getStorageManager());

         fileMessage.setMessageID(1005);
         fileMessage.setDurable(true);

         for (int i = 0; i < 2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE; i++) {
            fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
         }

         fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, 2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

         fileMessage.releaseResources(false, false);

         message = fileMessage;
      } else {
         message = session.createMessage(false);
      }

      for (int i = 0; i < TEST_SIZE; i++) {
         ClientProducer producer = session.createProducer(addressName);
         producer.send(message);
      }

      if (large) {
         ((LargeServerMessageImpl) message).deleteFile();
      }

      // add a consumer to node 0 to trigger redistribution here
      addConsumer(0, 0, queueName1, null, true, userName, password);

      Wait.assertEquals(TEST_SIZE, snfQueue::getMessageCount);

      // ensure the message is in the SnF queue
      assertEquals(TEST_SIZE, getMessageCount(snfQueue));

      // trigger scaleDown from node 0 to node 1
      logger.debug("============ Stopping {}", servers[0].getNodeID());
      removeConsumer(0);
      servers[0].stop();

      Queue queueServer2 = ((LocalQueueBinding) servers[2].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue();

      Wait.assertEquals(0, queueServer2::getMessageCount);

      // get the messages from queue 1 on node 1
      addConsumer(0, 1, queueName1, null, true, userName, password);

      // ensure the message is in queue 1 on node 1 as expected
      Queue queueServer1 = ((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue();
      Wait.assertEquals(TEST_SIZE, queueServer1::getMessageCount);

      for (int i = 0; i < TEST_SIZE; i++) {
         ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
         assertNotNull(clientMessage);
         if (large) {
            assertEquals(2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, clientMessage.getBodySize());

            for (int j = 0; j < 2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE; j++) {
               assertEquals(ActiveMQTestBase.getSamplebyte(j), clientMessage.getBodyBuffer().readByte());
            }
         }
         logger.debug("Received: {}", clientMessage);
         clientMessage.acknowledge();
      }

      // ensure there are no more messages on queue 1
      ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);

      Wait.assertTrue(() -> (servers[2].getPostOffice().getBinding(SimpleString.of(snfAddress))) == null);
      Wait.assertTrue(() -> (servers[1].getPostOffice().getBinding(SimpleString.of(snfAddress))) == null);

      assertFalse(servers[1].queueQuery(SimpleString.of(snfAddress)).isExists());
      assertFalse(servers[1].addressQuery(SimpleString.of(snfAddress)).isExists());
   }

   @Test
   public void testScaleDownWithMultipleQueues() throws Exception {
      AddressSettings addressSettings = new AddressSettings().setRedistributionDelay(0);
      servers[0].getAddressSettingsRepository().addMatch("#", addressSettings);
      servers[1].getAddressSettingsRepository().addMatch("#", addressSettings);
      servers[2].getAddressSettingsRepository().addMatch("#", addressSettings);

      final int TEST_SIZE = 10;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";
      final String queueName3 = "testQueue3";

      // create a queue on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, false, userName, password);
      createQueue(1, addressName, queueName1, null, false, userName, password);
      createQueue(2, addressName, queueName1, null, false, userName, password);

      // create a queue on each node mapped to the same address
      createQueue(0, addressName, queueName2, null, false, userName, password);
      createQueue(1, addressName, queueName2, null, false, userName, password);
      createQueue(2, addressName, queueName2, null, false, userName, password);

      // create a queue on each node mapped to the same address
      createQueue(0, addressName, queueName3, null, false, userName, password);
      createQueue(1, addressName, queueName3, null, false, userName, password);
      createQueue(2, addressName, queueName3, null, false, userName, password);

      // pause the SnF queue so that when the server tries to redistribute a message it won't actually go across the cluster bridge
      String snfAddress = servers[0].getInternalNamingPrefix() + "sf.cluster0." + servers[0].getNodeID().toString();
      Queue snfQueue = ((LocalQueueBinding) servers[2].getPostOffice().getBinding(SimpleString.of(snfAddress))).getQueue();
      snfQueue.pause();

      ClientSession session = sfs[2].createSession(userName, password, false, true, false, false, 0);

      Message message;
      message = session.createMessage(false);

      for (int i = 0; i < TEST_SIZE; i++) {
         ClientProducer producer = session.createProducer(addressName);
         producer.send(message);
      }

      // add a consumer to node 0 to trigger redistribution here
      addConsumer(0, 0, queueName1, null, true, userName, password);
      addConsumer(1, 0, queueName3, null, true, userName, password);

      // ensure the message is in the SnF queue
      Wait.assertEquals(TEST_SIZE * 2, snfQueue::getMessageCount);

      // trigger scaleDown from node 0 to node 1
      logger.debug("============ Stopping {}", servers[0].getNodeID());
      removeConsumer(0);
      removeConsumer(1);
      servers[0].stop();

      Wait.assertEquals(0, () -> getMessageCount(((LocalQueueBinding) servers[2].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()) +
         getMessageCount(((LocalQueueBinding) servers[2].getPostOffice().getBinding(SimpleString.of(queueName3))).getQueue()));

      assertEquals(TEST_SIZE, getMessageCount(((LocalQueueBinding) servers[2].getPostOffice().getBinding(SimpleString.of(queueName2))).getQueue()));

      // get the messages from queue 1 on node 1
      addConsumer(0, 1, queueName1, null, true, userName, password);
      addConsumer(1, 1, queueName3, null, true, userName, password);

      Wait.assertEquals(TEST_SIZE * 2, () -> getMessageCount(((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()) +
         getMessageCount(((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName3))).getQueue()));
      // ensure the message is in queue 1 on node 1 as expected

      for (int i = 0; i < TEST_SIZE; i++) {
         ClientMessage clientMessage = consumers[0].getConsumer().receive(1000);
         assertNotNull(clientMessage);
         logger.debug("Received: {}", clientMessage);
         clientMessage.acknowledge();

         clientMessage = consumers[1].getConsumer().receive(1000);
         assertNotNull(clientMessage);
         logger.debug("Received: {}", clientMessage);
         clientMessage.acknowledge();
      }

      // ensure there are no more messages on queue 1
      ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);

      // ensure there are no more messages on queue 3
      clientMessage = consumers[1].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(1);
   }
}
