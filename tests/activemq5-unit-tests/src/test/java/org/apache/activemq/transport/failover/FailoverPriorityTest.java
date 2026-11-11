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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class FailoverPriorityTest extends OpenwireArtemisBaseTest {

   protected final Logger LOG = LoggerFactory.getLogger(getClass());

   private static final String BROKER_A_CLIENT_TC_ADDRESS = "tcp://127.0.0.1:61616";
   private static final String BROKER_B_CLIENT_TC_ADDRESS = "tcp://127.0.0.1:61617";
   private static final String BROKER_C_CLIENT_TC_ADDRESS = "tcp://127.0.0.1:61618";
   private final Map<Integer, String> urls = new HashMap<>();

   private final List<ActiveMQConnection> connections = new ArrayList<>();
   private final EmbeddedJMS[] servers = new EmbeddedJMS[3];
   private String clientUrl;
   private final Map<String, String> params = new HashMap<>();

   @Before
   public void setUp() throws Exception {
      urls.put(0, BROKER_A_CLIENT_TC_ADDRESS);
      urls.put(1, BROKER_B_CLIENT_TC_ADDRESS);
      params.clear();
      params.put("rebalanceClusterClients", "true");
      params.put("updateClusterClients", "true");
      params.put("updateClusterClientsOnRemove", "true");
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

   @Test
   public void testPriorityBackup() throws Exception {
      Configuration config0 = createConfig("127.0.0.1", 0);
      Configuration config1 = createConfig("127.0.0.1", 1);

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      servers[0] = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      servers[1] = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      servers[0].start();
      servers[1].start();

      Assert.assertTrue(servers[0].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(servers[1].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));

      Thread.sleep(1000);

      setClientUrl("failover:(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")?randomize=false&priorityBackup=true&initialReconnectDelay=1000&useExponentialBackOff=false");
      createClients(5);

      assertAllConnectedTo(urls.get(0));

      restart(false, 0, 1);

      for (int i = 0; i < 3; i++) {
         restart(true, 0, 1);
      }

      Thread.sleep(5000);

      restart(false, 0, 1);

   }

   @Test
   public void testPriorityBackupList() throws Exception {
      Configuration config0 = createConfig("127.0.0.1", 0);
      Configuration config1 = createConfig("127.0.0.1", 1);

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      servers[0] = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      servers[1] = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      servers[0].start();
      servers[1].start();

      Assert.assertTrue(servers[0].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(servers[1].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Thread.sleep(1000);

      setClientUrl("failover:(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")?randomize=false&priorityBackup=true&priorityURIs=tcp://127.0.0.1:61617&initialReconnectDelay=1000&useExponentialBackOff=false");
      createClients(5);

      Thread.sleep(3000);

      assertAllConnectedTo(urls.get(1));

      restart(false, 1, 0);

      for (int i = 0; i < 3; i++) {
         restart(true, 1, 0);
      }

      restart(false, 1, 0);
   }

   @Test
   public void testThreeBrokers() throws Exception {
      setupThreeBrokers();
      Thread.sleep(1000);

      setClientUrl("failover:(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + "," + BROKER_C_CLIENT_TC_ADDRESS + ")?randomize=false&priorityBackup=true&initialReconnectDelay=1000&useExponentialBackOff=false");

      createClients(5);

      assertAllConnectedTo(urls.get(0));

      restart(true, 0, 1, 3);
   }

   @Test
   public void testPriorityBackupAndUpdateClients() throws Exception {
      Configuration config0 = createConfig("127.0.0.1", 0);
      Configuration config1 = createConfig("127.0.0.1", 1);

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      servers[0] = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      servers[1] = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      servers[0].start();
      servers[1].start();

      Assert.assertTrue(servers[0].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(servers[1].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));

      Thread.sleep(1000);

      setClientUrl("failover:(" + BROKER_A_CLIENT_TC_ADDRESS + "," + BROKER_B_CLIENT_TC_ADDRESS + ")?randomize=false&priorityBackup=true&initialReconnectDelay=1000&useExponentialBackOff=false");

      createClients(5);

      // Let's wait a little bit longer just in case it takes a while to realize that the
      // Broker A is the one with higher priority.
      Thread.sleep(5000);

      assertAllConnectedTo(urls.get(0));
   }

   private void restart(boolean primary, int primaryID, int secondaryID) throws Exception {
      restart(primary, primaryID, secondaryID, 2);
   }

   private void restart(boolean primary, int primaryID, int secondaryID, int total) throws Exception {

      Thread.sleep(1000);

      if (primary) {
         LOG.info("Stopping " + primaryID);
         stopBroker(primaryID);
         Assert.assertTrue(servers[secondaryID].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, total - 1));
      } else {
         LOG.info("Stopping " + secondaryID);
         stopBroker(secondaryID);
         Assert.assertTrue(servers[primaryID].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, total - 1));
      }
      Thread.sleep(5000);

      if (primary) {
         assertAllConnectedTo(urls.get(secondaryID));
      } else {
         assertAllConnectedTo(urls.get(primaryID));
      }

      if (primary) {
         Configuration config = createConfig("127.0.0.1", primaryID);

         deployClusterConfiguration(config, secondaryID);

         servers[primaryID] = new EmbeddedJMS().setConfiguration(config).setJmsConfiguration(new JMSConfigurationImpl());
         servers[primaryID].start();

         Assert.assertTrue(servers[primaryID].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, total));
         Assert.assertTrue(servers[secondaryID].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, total));
      } else {
         Configuration config = createConfig("127.0.0.1", secondaryID);

         deployClusterConfiguration(config, primaryID);

         servers[secondaryID] = new EmbeddedJMS().setConfiguration(config).setJmsConfiguration(new JMSConfigurationImpl());
         servers[secondaryID].start();

         Assert.assertTrue(servers[primaryID].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, total));
         Assert.assertTrue(servers[secondaryID].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, total));
      }

      Thread.sleep(5000);

      assertAllConnectedTo(urls.get(primaryID));

   }

   private void stopBroker(int serverID) throws Exception {
      servers[serverID].stop();
   }

   public void setClientUrl(String clientUrl) {
      this.clientUrl = clientUrl;
   }

   protected void createClients(int numOfClients) throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(clientUrl);
      for (int i = 0; i < numOfClients; i++) {
         ActiveMQConnection c = (ActiveMQConnection) factory.createConnection();
         c.start();
         connections.add(c);
      }
   }

   protected void shutdownClients() throws JMSException {
      for (Connection c : connections) {
         c.close();
      }
   }

   protected void assertAllConnectedTo(String url) throws Exception {
      for (ActiveMQConnection c : connections) {
         Assert.assertEquals(url, c.getTransportChannel().getRemoteAddress());
      }
   }

   private void setupThreeBrokers() throws Exception {

      params.put("rebalanceClusterClients", "false");
      params.put("updateClusterClients", "false");
      params.put("updateClusterClientsOnRemove", "false");

      Configuration config0 = createConfig("127.0.0.1", 0, params);
      Configuration config1 = createConfig("127.0.0.1", 1, params);
      Configuration config2 = createConfig("127.0.0.1", 2, params);

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

}
