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
package org.apache.activemq.transport.failover;

import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TwoBrokerFailoverClusterTest extends OpenwireArtemisBaseTest {

   private static final int NUMBER_OF_CLIENTS = 30;
   private final List<ActiveMQConnection> connections = new ArrayList<>();
   private EmbeddedJMS server0;
   private EmbeddedJMS server1;
   private String clientUrl;

   @Test
   public void testTwoBrokersRestart() throws Exception {

      Thread.sleep(2000);
      createClients();

      Thread.sleep(5000);

      assertClientsConnectedToTwoBrokers();
      assertClientsConnectionsEvenlyDistributed(.26);

      server0.stop();
      Assert.assertTrue(server1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 1));

      Thread.sleep(1000);

      assertAllConnectedTo(newURI("127.0.0.1", 1));

      Thread.sleep(5000);

      server0.start();
      Assert.assertTrue(server0.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(server1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Thread.sleep(5000);

      //need update-cluster-clients, -on-remove and rebalance set to true.
      assertClientsConnectedToTwoBrokers();
      assertClientsConnectionsEvenlyDistributed(.26);
   }

   @Before
   public void setUp() throws Exception {
      Map<String, String> map = new HashMap<>();
      map.put("rebalanceClusterClients", "true");
      map.put("updateClusterClients", "true");
      map.put("updateClusterClientsOnRemove", "true");
      Configuration config0 = createConfig("127.0.0.1", 0, map);
      Configuration config1 = createConfig("127.0.0.1", 1, map);

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      server0 = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      server1 = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());

      clientUrl = "failover://(" + newURI("127.0.0.1", 0) + "," + newURI("127.0.0.1", 1) + ")";

      server0.start();
      server1.start();
      Assert.assertTrue(server0.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(server1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
   }

   @After
   public void tearDown() throws Exception {
      for (ActiveMQConnection conn : connections) {
         conn.close();
      }
      server0.stop();
      server1.stop();
   }

   protected void createClients() throws Exception {
      createClients(NUMBER_OF_CLIENTS);
   }

   protected void createClients(int numOfClients) throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(clientUrl);
      for (int i = 0; i < numOfClients; i++) {
         ActiveMQConnection c = (ActiveMQConnection) factory.createConnection();
         c.start();
         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = s.createQueue(getClass().getName());
         MessageConsumer consumer = s.createConsumer(queue);
         connections.add(c);
      }
   }

   protected void assertClientsConnectedToTwoBrokers() {
      Set<String> set = new HashSet<>();
      for (ActiveMQConnection c : connections) {
         if (c.getTransportChannel().getRemoteAddress() != null) {
            set.add(c.getTransportChannel().getRemoteAddress());
         }
      }
      Assert.assertTrue("Only 2 connections should be found: " + set, set.size() == 2);
   }

   protected void assertClientsConnectionsEvenlyDistributed(double minimumPercentage) {
      Map<String, Double> clientConnectionCounts = new HashMap<>();
      int total = 0;
      for (ActiveMQConnection c : connections) {
         String key = c.getTransportChannel().getRemoteAddress();
         if (key != null) {
            total++;
            if (clientConnectionCounts.containsKey(key)) {
               double count = clientConnectionCounts.get(key);
               count += 1.0;
               clientConnectionCounts.put(key, count);
            } else {
               clientConnectionCounts.put(key, 1.0);
            }
         }
      }
      Set<String> keys = clientConnectionCounts.keySet();
      for (String key : keys) {
         double count = clientConnectionCounts.get(key);
         double percentage = count / total;
         System.out.println(count + " of " + total + " connections for " + key + " = " + percentage);
         Assert.assertTrue("Connections distribution expected to be >= than " + minimumPercentage + ".  Actual distribution was " + percentage + " for connection " + key, percentage >= minimumPercentage);
      }
   }

   protected void assertAllConnectedTo(String url) throws Exception {
      for (ActiveMQConnection c : connections) {
         Assert.assertEquals(url, c.getTransportChannel().getRemoteAddress());
      }
   }

}
