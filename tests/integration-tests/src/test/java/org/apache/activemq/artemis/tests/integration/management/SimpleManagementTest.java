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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleManagementTest extends ActiveMQTestBase {

   public static final String LOCALHOST = "tcp://localhost:61616";
   private ActiveMQServer server;
   SimpleManagement simpleManagement = new SimpleManagement(LOCALHOST, "admin", "admin");

   @BeforeEach
   public void setupServer() throws Exception {
      server = createServer(false, createDefaultConfig(0, true));

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("backup");
      ccconf.setName("cluster");
      ccconf.setConnectorName("live");
      server.getConfiguration().addConnectorConfiguration("live", LOCALHOST);
      server.getConfiguration().addClusterConfiguration(ccconf);

      server.start();
   }

   @Test
   public void testQueues() throws Exception {
      server.start();
      String queueName = RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setAddress(queueName).setDurable(true));

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", LOCALHOST);

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < 33; i++) {
            producer.send(session.createMessage());
         }
         session.commit();
      }

      Queue serverQueue = server.locateQueue(queueName);
      Wait.assertEquals(33, serverQueue::getMessageCount);

      Map<String, Long> queues = simpleManagement.getQueueCounts(100);
      assertEquals((Long)33L, queues.get(queueName));
      assertEquals(33L, simpleManagement.getMessageCountOnQueue(queueName));
   }

   @Test
   public void testListTopology() throws Exception {
      JsonArray topology = simpleManagement.listNetworkTopology();
      String nodeId = simpleManagement.getNodeID();
      assertEquals(1, topology.size());
      JsonObject node = topology.getJsonObject(0);
      assertEquals("localhost:61616", node.getString("live"));
      assertEquals(nodeId, node.getString("nodeID"));
   }

}