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

import java.util.HashMap;
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
import org.junit.Test;

public class FailoverRandomTest extends OpenwireArtemisBaseTest {

   private EmbeddedJMS server0, server1;

   @Before
   public void setUp() throws Exception {
      Map<String, String> params = new HashMap<>();

      params.put("rebalanceClusterClients", "true");
      params.put("updateClusterClients", "true");
      params.put("updateClusterClientsOnRemove", "true");
      params.put("brokerName", "A");

      Configuration config0 = createConfig("127.0.0.1", 0, params);

      params.put("brokerName", "B");
      Configuration config1 = createConfig("127.0.0.2", 1, params);

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      server0 = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      server1 = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());

      server0.start();
      server1.start();

      Assert.assertTrue(server0.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(server1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
   }

   @After
   public void tearDown() throws Exception {
      server0.stop();
      server1.stop();
   }

   @Test
   public void testRandomConnections() throws Exception {
      String failoverUrl = "failover:(" + newURI(0) + "," + newURI(1) + ")";
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(failoverUrl);

      ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();
      final String brokerName1 = connection.getBrokerName();
      Assert.assertNotNull(brokerName1);
      connection.close();

      String brokerName2 = brokerName1;
      int attempts = 40;
      while (brokerName1.equals(brokerName2) && attempts-- > 0) {
         connection = (ActiveMQConnection) cf.createConnection();
         connection.start();
         brokerName2 = connection.getBrokerName();
         Assert.assertNotNull(brokerName2);
         connection.close();
      }
      Assert.assertTrue(brokerName1 + "!=" + brokerName2, !brokerName1.equals(brokerName2));
   }
}
