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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageRedistributionWithDiscoveryTest extends ClusterTestBase {

   protected final String groupAddress = ActiveMQTestBase.getUDPDiscoveryAddress();

   protected final int groupPort = ActiveMQTestBase.getUDPDiscoveryPort();

   protected boolean isNetty() {
      return false;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();
      setupCluster();
   }

   protected void setupCluster() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      for (int i = 0; i < 5; i++) {
         setServer(messageLoadBalancingType, i);
      }
   }

   protected void setServer(final MessageLoadBalancingType messageLoadBalancingType, int server) throws Exception {
      setupPrimaryServerWithDiscovery(server, groupAddress, groupPort, isFileStorage(), isNetty(), false);

      AddressSettings setting = new AddressSettings().setRedeliveryDelay(0).setRedistributionDelay(0);

      servers[server].getAddressSettingsRepository().addMatch("#", setting);

      setupDiscoveryClusterConnection("cluster" + server, server, "dg1", "queues", messageLoadBalancingType, 1, isNetty());
   }

   @Test
   public void testRedistributeWithPreparedAndRestart() throws Exception {
      startServers(0);

      setupSessionFactory(0, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, true);

      ClientSession session0 = sfs[0].createSession(false, false, false);

      ClientProducer prod0 = session0.createProducer("queues.testaddress");

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session0.createMessage(true);

         msg.putIntProperty("key", i);

         prod0.send(msg);

         session0.commit();
      }

      session0.close();

      session0 = sfs[0].createSession(true, false, false);

      ClientConsumer consumer0 = session0.createConsumer("queue0");

      session0.start();

      List<Xid> xids = new ArrayList<>();

      for (int i = 0; i < 100; i++) {
         Xid xid = newXID();

         session0.start(xid, XAResource.TMNOFLAGS);

         ClientMessage msg = consumer0.receive(5000);

         msg.acknowledge();

         session0.end(xid, XAResource.TMSUCCESS);

         session0.prepare(xid);

         xids.add(xid);
      }

      session0.close();

      sfs[0].close();
      sfs[0] = null;

      servers[0].stop();
      servers[0] = null;

      setServer(MessageLoadBalancingType.ON_DEMAND, 0);

      startServers(1, 2);

      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      ClientSession session1 = sfs[1].createSession(false, false);

      createQueue(1, "queues.testaddress", "queue0", null, true);
      createQueue(2, "queues.testaddress", "queue0", null, true);

      session1.start();
      ClientConsumer consumer1 = session1.createConsumer("queue0");

      startServers(0);

      setupSessionFactory(0, isNetty());

      waitForBindings(0, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 1, false);

      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(1, "queues.testaddress", 2, 0, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      session0 = sfs[0].createSession(true, false, false);

      for (Xid xid : xids) {
         session0.rollback(xid);
      }

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = consumer1.receive(15000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      session1.commit();

   }

}
