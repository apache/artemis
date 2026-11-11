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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpireWhileLoadBalanceTest extends ClusterTestBase {

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      setupServer(0, isFileStorage(), true);
      setupServer(1, isFileStorage(), true);
      setupServer(2, isFileStorage(), true);

      for (int i = 0; i < 3; i++) {
         servers[i].getConfiguration().setMessageExpiryScanPeriod(100);
      }

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.STRICT, 1, true, 0, 1, 2);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.STRICT, 1, true, 1, 0, 2);

      setupClusterConnection("cluster2", "queues", MessageLoadBalancingType.STRICT, 1, true, 2, 0, 1);

      startServers(0, 1, 2);

      setupSessionFactory(0, true);
      setupSessionFactory(1, true);
      setupSessionFactory(2, true);
   }

   @Test
   public void testSend() throws Exception {
      waitForTopology(getServer(0), 3);
      waitForTopology(getServer(1), 3);
      waitForTopology(getServer(2), 3);

      SimpleString expiryQueue = SimpleString.of("expiryQueue");

      AddressSettings as = new AddressSettings();
      as.setDeadLetterAddress(expiryQueue);
      as.setExpiryAddress(expiryQueue);

      for (int i = 0; i <= 2; i++) {
         createQueue(i, "queues.testaddress", "queue0", null, true);
         getServer(i).createQueue(QueueConfiguration.of(expiryQueue).setRoutingType(RoutingType.ANYCAST));
         getServer(i).getAddressSettingsRepository().addMatch("#", as);

      }

      // this will pause all the cluster bridges
      for (ClusterConnection clusterConnection : getServer(0).getClusterManager().getClusterConnections()) {
         for (MessageFlowRecord record : ((ClusterConnectionImpl) clusterConnection).getRecords().values()) {
            record.getBridge().pause();
         }
      }

      ClientSessionFactory sf = sfs[0];

      ClientSession session = sf.createSession(false, false);
      ClientProducer producer = session.createProducer("queues.testaddress");

      for (int i = 0; i < 1000; i++) {
         ClientMessage message = session.createMessage(true);
         message.setExpiration(500);
         producer.send(message);
      }

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer("expiryQueue");
      for (int i = 0; i < 1000; i++) {
         ClientMessage message = consumer.receive(2000);
         assertNotNull(message);
         message.acknowledge();
      }

      session.commit();

      session.close();

   }
}
