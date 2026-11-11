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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScaleDownRemoveSFTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ScaleDownRemoveSFTest() {
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ScaleDownConfiguration scaleDownConfiguration = new ScaleDownConfiguration();
      setupPrimaryServer(0, isFileStorage(), isNetty(), true);
      setupPrimaryServer(1, isFileStorage(), isNetty(), true);
      PrimaryOnlyPolicyConfiguration haPolicyConfiguration0 = (PrimaryOnlyPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration();
      haPolicyConfiguration0.setScaleDownConfiguration(scaleDownConfiguration);
      PrimaryOnlyPolicyConfiguration haPolicyConfiguration1 = (PrimaryOnlyPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration();
      haPolicyConfiguration1.setScaleDownConfiguration(new ScaleDownConfiguration());

      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
      haPolicyConfiguration0.getScaleDownConfiguration().getConnectors().addAll(servers[0].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors());
      haPolicyConfiguration1.getScaleDownConfiguration().getConnectors().addAll(servers[1].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors());
      servers[0].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      servers[0].getConfiguration().getClusterConfigurations().get(0).setScaleDownReaperPeriod(100);
      servers[0].getConfiguration().getClusterConfigurations().get(0).setScaleDownReaperGraceTicks(1);
      servers[1].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      servers[1].getConfiguration().getClusterConfigurations().get(0).setScaleDownReaperPeriod(100);
      servers[1].getConfiguration().getClusterConfigurations().get(0).setScaleDownReaperGraceTicks(1);
      startServers(0, 1);
      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
   }


   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testScaleDownCheckSF() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, true);
      createQueue(1, addressName, queueName1, null, true);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, true, null);

      // consume a message from queue 1
      addConsumer(1, 0, queueName1, null, false);
      ClientMessage clientMessage = consumers[1].getConsumer().receive(5_000);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      consumers[1].getSession().commit();

      assertEquals(TEST_SIZE - 1, getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()));

      //check sf queue on server1 exists
      ClusterConnectionImpl clusterconn1 = (ClusterConnectionImpl) servers[1].getClusterManager().getClusterConnection("cluster0");
      SimpleString sfQueueName = clusterconn1.getSfQueueName(servers[0].getNodeID().toString());

      logger.debug("[sf queue on server 1]: {}", sfQueueName);

      assertTrue(servers[1].queueQuery(sfQueueName).isExists());

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      addConsumer(0, 1, queueName1, null);
      clientMessage = consumers[0].getConsumer().receive(10_000);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);

      //check
      Wait.assertFalse(() -> servers[1].queueQuery(sfQueueName).isExists());
      Wait.assertFalse(() -> servers[1].addressQuery(sfQueueName).isExists());

   }

}
