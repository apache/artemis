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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScaleDownDeterminism extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), HAType.SharedNothingReplication, isNetty(), true);
      servers[0].getConfiguration().setSecurityEnabled(true);
      setupPrimaryServer(1, isFileStorage(), HAType.SharedNothingReplication, isNetty(), true);
      servers[1].getConfiguration().setSecurityEnabled(true);
      setupPrimaryServer(2, isFileStorage(), HAType.SharedNothingReplication, isNetty(), true);
      servers[2].getConfiguration().setSecurityEnabled(true);

      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1, 2);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0, 2);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 2, 0, 1);

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

   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testScaleDownDeterministically() throws Exception {
      final String queueName = "testQueue";
      final int messageCount = 10;

      ClientSession session = sfs[0].createSession(servers[0].getConfiguration().getClusterUser(), servers[0].getConfiguration().getClusterPassword(), false, true, false, false, 0);
      createQueue(0, queueName, queueName, null, false, servers[0].getConfiguration().getClusterUser(), servers[0].getConfiguration().getClusterPassword());
      ClientProducer producer = session.createProducer(queueName);

      for (int i = 0; i < messageCount; i++) {
         producer.send(session.createMessage(false));
      }
      session.close();
      sfs[0].close();

      servers[0].getActiveMQServerControl().addConnector("scaleDown", "tcp://localhost:61617");
      //Connectors set up in test do ot use the host param whicvh is needed for above command
      //Removing host param so that cluster connector matches new scaleDown connector
      servers[0].getConfiguration().getConnectorConfigurations().get("scaleDown").getParams().remove("host");

      String server0connector2 = servers[0].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors().get(1);
      String server1connector1 = servers[1].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors().get(0);

      servers[0].getActiveMQServerControl().scaleDown("scaleDown");
      assertEquals(messageCount, servers[1].getTotalMessageCount());
      servers[0].start();

      waitForServerToStart(servers[0]);
      assertEquals(0, servers[0].getTotalMessageCount());

      servers[1].getActiveMQServerControl().scaleDown(server1connector1);
      assertEquals(messageCount, servers[0].getTotalMessageCount());
      servers[1].start();

      waitForServerToStart(servers[1]);
      servers[0].getActiveMQServerControl().scaleDown(server0connector2);
      assertEquals(messageCount, servers[2].getTotalMessageCount());
   }
}
