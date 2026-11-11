/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SharedStoreScaleDownBackupTest extends ClusterTestBase {

   protected boolean isNetty() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupPrimaryServer(0, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupPrimaryServer(1, isFileStorage(), HAType.SharedStore, isNetty(), false);
      setupBackupServer(2, 0, isFileStorage(), HAType.SharedStore, isNetty());

      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 1);

      ((SharedStorePrimaryPolicyConfiguration)getServer(0).getConfiguration().getHAPolicyConfiguration()).
         setFailoverOnServerShutdown(true);
      ((SharedStoreBackupPolicyConfiguration)getServer(2).getConfiguration().getHAPolicyConfiguration()).
         setFailoverOnServerShutdown(true).
         setRestartBackup(true).
         setScaleDownConfiguration(new ScaleDownConfiguration().setEnabled(true).addConnector(
            getServer(2).getConfiguration().getClusterConfigurations().get(0).getStaticConnectors().get(0)));

      getServer(0).getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      getServer(1).getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
   }


   @Test
   public void testBasicScaleDown() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, true);
      createQueue(0, addressName, queueName2, null, true);
      createQueue(1, addressName, queueName1, null, true);
      createQueue(1, addressName, queueName2, null, true);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, true, null);

      // consume a message from queue 2
      addConsumer(1, 0, queueName2, null, false);
      ClientMessage clientMessage = consumers[1].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      consumers[1].getSession().commit();
      //      removeConsumer(1);

      // at this point on node 0 there should be 2 messages in testQueue1 and 1 message in testQueue2
      assertEquals(TEST_SIZE, getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()));
      assertEquals(TEST_SIZE - 1, getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName2))).getQueue()));

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      // get the 2 messages from queue 1
      addConsumer(0, 1, queueName1, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);

      // get the 1 message from queue 2
      addConsumer(0, 1, queueName2, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);
   }
}
