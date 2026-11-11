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

import javax.jms.Connection;
import javax.jms.JMSException;
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
import org.junit.Ignore;
import org.junit.Test;

/**
 * Complex cluster test that will exercise the dynamic failover capabilities of a network of brokers. Using a networking
 * of 3 brokers where the 3rd broker is removed and then added back in it is expected in each test that the number of
 * connections on the client should start with 3, then have two after the 3rd broker is removed and then show 3 after
 * the 3rd broker is reintroduced.
 */
@Ignore
public class FailoverComplexClusterTest extends OpenwireArtemisBaseTest {

   private static final String BROKER_A_CLIENT_TC_ADDRESS = "tcp://127.0.0.1:61616";
   private static final String BROKER_B_CLIENT_TC_ADDRESS = "tcp://127.0.0.1:61617";

   private String clientUrl;
   private EmbeddedJMS[] servers = new EmbeddedJMS[3];

   private static final int NUMBER_OF_CLIENTS = 30;
   private final List<ActiveMQConnection> connections = new ArrayList<>();

   @Before
   public void setUp() throws Exception {
   }

   //default setup for most tests
   private void commonSetup() throws Exception {
      Map<String, String> params = new HashMap<>();

      params.put("rebalanceClusterClients", "true");
      params.put("updateClusterClients", "true");
      params.put("updateClusterClientsOnRemove", "true");

      Configuration config0 = createConfig("localhost", 0, params);
      Configuration config1 = createConfig("localhost", 1, params);
      Configuration config2 = createConfig("localhost", 2, params);

      deployClusterConfiguration(config0, 1, 2);
      deployClusterConfiguration(config1, 0, 2);
      deployClusterConfiguration(config2, 0, 1);

      servers[0] = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      servers[1] = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      servers[2] = new EmbeddedJMS().setConfiguration(config2).setJmsConfiguration(new JMSConfigurationImpl());

      servers[0].start();
      servers[1].start();
      servers[2].start();

      Assert.assertTrue(servers[0].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 3));
      Assert.assertTrue(servers[1].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 3));
      Assert.assertTrue(servers[2].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 3));
   }

   @After
   public void tearDown() throws Exception {
      shutdownClients();
      for (EmbeddedJMS server : servers) {
         if (server != null) {
            server.stop();
         }
      }
   }

   /**
    * Basic dynamic failover 3 broker test
    */
   @Test
   public void testThreeBrokerClusterSingleConnectorBasic() throws Exception {
      commonSetup();
      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")");
      createClients();

      Thread.sleep(3000);
      runTests(false, null, null, null);
   }

   /**
    * Tests a 3 broker configuration to ensure that the backup is random and supported in a cluster.
    * useExponentialBackOff is set to false and maxReconnectAttempts is set to 1 to move through the list quickly for
    * this test.
    */
   @Test
   public void testThreeBrokerClusterSingleConnectorBackupFailoverConfig() throws Exception {
      commonSetup();
      Thread.sleep(2000);

      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")?backup=true&backupPoolSize=2&useExponentialBackOff=false&initialReconnectDelay=500");
      createClients();
      Thread.sleep(2000);

      runTests(false, null, null, null);
   }

   /**
    * Tests a 3 broker cluster that passes in connection params on the transport connector. Prior versions of AMQ passed
    * the TC connection params to the client and this should not happen. The chosen param is not compatible with the
    * client and will throw an error if used.
    */
   @Test
   public void testThreeBrokerClusterSingleConnectorWithParams() throws Exception {
      commonSetup();
      Thread.sleep(2000);
      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")");
      createClients();
      Thread.sleep(2000);

      runTests(false, null, null, null);
   }

   /**
    * Tests a 3 broker cluster using a cluster filter of *
    */
   @Test
   public void testThreeBrokerClusterWithClusterFilter() throws Exception {
      commonSetup();
      Thread.sleep(2000);
      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")");
      createClients();

      runTests(false, null, "*", null);
   }

   /**
    * Test to verify that a broker with multiple transport connections only the one marked to update clients is
    * propagate
    */
   @Test
   public void testThreeBrokerClusterMultipleConnectorBasic() throws Exception {
      commonSetup();
      Thread.sleep(2000);

      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")");
      createClients();
      Thread.sleep(2000);

      runTests(true, null, null, null);
   }

   /**
    * Test to verify the reintroduction of the A Broker
    */
   @Test
   public void testOriginalBrokerRestart() throws Exception {
      commonSetup();
      Thread.sleep(2000);

      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")");
      createClients();
      Thread.sleep(2000);

      assertClientsConnectedToThreeBrokers();

      stopServer(0);

      Thread.sleep(5000);

      assertClientsConnectedToTwoBrokers();

      restartServer(0);
      Thread.sleep(5000);

      assertClientsConnectedToThreeBrokers();
   }

   /**
    * Test to ensure clients are evenly to all available brokers in the
    * network.
    */
   @Test
   public void testThreeBrokerClusterClientDistributions() throws Exception {
      commonSetup();
      Thread.sleep(2000);
      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")?useExponentialBackOff=false&initialReconnectDelay=500");
      createClients(100);
      Thread.sleep(5000);

      runClientDistributionTests(false, null, null, null);
   }

   /**
    * Test to verify that clients are distributed with no less than 20% of the clients on any one broker.
    */
   @Test
   public void testThreeBrokerClusterDestinationFilter() throws Exception {
      commonSetup();
      Thread.sleep(2000);
      setClientUrl("failover://(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")");
      createClients();

      runTests(false, null, null, "Queue.TEST.FOO.>");
   }

   @Test
   public void testFailOverWithUpdateClientsOnRemove() throws Exception {
      // Broker A
      Configuration config0 = createConfig(0, "?rebalanceClusterClients=true&updateClusterClients=true&updateClusterClientsOnRemove=true");
      // Broker B
      Configuration config1 = createConfig(1, "?rebalanceClusterClients=true&updateClusterClients=true&updateClusterClientsOnRemove=true");

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      servers[0] = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      servers[0].start();

      servers[1] = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      servers[1].start();

      servers[0].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2);
      servers[1].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2);

      Thread.sleep(1000);

      // create client connecting only to A. It should receive broker B address whet it connects to A.
      setClientUrl("failover:(" + BROKER_A_CLIENT_TC_ADDRESS + ")?useExponentialBackOff=true");
      createClients(1);
      Thread.sleep(5000);

      // We stop broker A.
      servers[0].stop();
      servers[1].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 1);

      Thread.sleep(5000);

      // Client should failover to B.
      assertAllConnectedTo(BROKER_B_CLIENT_TC_ADDRESS);
   }

   /**
    * Runs a 3 Broker dynamic failover test:
    * <ul>
    * <li>asserts clients are distributed across all 3 brokers</li>
    * <li>asserts clients are distributed across 2 brokers after removing the 3rd</li>
    * <li>asserts clients are distributed across all 3 brokers after reintroducing the 3rd broker</li>
    * </ul>
    */
   private void runTests(boolean multi,
                         String tcParams,
                         String clusterFilter,
                         String destinationFilter) throws Exception, InterruptedException {
      assertClientsConnectedToThreeBrokers();

      stopServer(2);

      Thread.sleep(5000);

      assertClientsConnectedToTwoBrokers();

      restartServer(2);

      Thread.sleep(5000);

      assertClientsConnectedToThreeBrokers();
   }

   public void setClientUrl(String clientUrl) {
      this.clientUrl = clientUrl;
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

   protected void shutdownClients() throws JMSException {
      for (Connection c : connections) {
         c.close();
      }
   }

   protected void assertClientsConnectedToThreeBrokers() {
      Set<String> set = new HashSet<>();
      for (ActiveMQConnection c : connections) {
         if (c.getTransportChannel().getRemoteAddress() != null) {
            set.add(c.getTransportChannel().getRemoteAddress());
         }
      }
      Assert.assertTrue("Only 3 connections should be found: " + set, set.size() == 3);
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

   private void stopServer(int serverID) throws Exception {
      servers[serverID].stop();
      for (int i = 0; i < servers.length; i++) {
         if (i != serverID) {
            Assert.assertTrue(servers[i].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, servers.length - 1));
         }
      }
   }

   private void restartServer(int serverID) throws Exception {
      servers[serverID].start();

      for (int i = 0; i < servers.length; i++) {
         Assert.assertTrue(servers[i].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, servers.length));
      }
   }

   private void runClientDistributionTests(boolean multi,
                                           String tcParams,
                                           String clusterFilter,
                                           String destinationFilter) throws Exception, InterruptedException {
      assertClientsConnectedToThreeBrokers();
      //if 2/3 or more of total connections connect to one node, we consider it wrong
      //if 1/4 or less of total connects to one node, we consider it wrong
      assertClientsConnectionsEvenlyDistributed(.25, .67);

      stopServer(2);

      Thread.sleep(5000);

      assertClientsConnectedToTwoBrokers();
      //now there are only 2 nodes
      //if 2/3 or more of total connections go to either node, we consider it wrong
      //if 1/3 or less of total connections go to either node, we consider it wrong
      assertClientsConnectionsEvenlyDistributed(.34, .67);

      restartServer(2);
      Thread.sleep(5000);

      assertClientsConnectedToThreeBrokers();
      //now back to 3 nodes. We assume at least the new node will
      //have 1/10 of the total connections, and any node's connections
      //won't exceed 50%
      assertClientsConnectionsEvenlyDistributed(.10, .50);
   }

   protected void assertClientsConnectionsEvenlyDistributed(double minimumPercentage, double maximumPercentage) {
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
      List<String> errorMsgs = new ArrayList<>();
      for (String key : keys) {
         double count = clientConnectionCounts.get(key);
         double percentage = count / total;
         if (percentage < minimumPercentage || percentage > maximumPercentage) {
            errorMsgs.add("Connections distribution expected to be within range [ " + minimumPercentage + ", " + maximumPercentage + "].  Actuall distribution was " + percentage + " for connection " + key);
         }
         if (errorMsgs.size() > 0) {
            for (String err : errorMsgs) {
               System.err.println(err);
            }
            Assert.fail("Test failed. Please see the log message for details");
         }
      }
   }

   protected void assertAllConnectedTo(String url) throws Exception {
      for (ActiveMQConnection c : connections) {
         Assert.assertEquals(url, c.getTransportChannel().getRemoteAddress());
      }
   }

}
