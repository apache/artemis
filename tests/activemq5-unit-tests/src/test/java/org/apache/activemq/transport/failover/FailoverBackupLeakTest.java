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
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Ensures connections aren't leaked when when we use backup=true and randomize=false
 */
@Ignore
public class FailoverBackupLeakTest extends OpenwireArtemisBaseTest {

   private EmbeddedJMS s1, s2;

   @Before
   public void setUp() throws Exception {

      Configuration config0 = createConfig("127.0.0.1", 0);
      Configuration config1 = createConfig("127.0.0.1", 1);

      deployClusterConfiguration(config0, 1);
      deployClusterConfiguration(config1, 0);

      s1 = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      s2 = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      s1.start();
      s2.start();

      Assert.assertTrue(s1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(s2.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
   }

   @After
   public void tearDown() throws Exception {
      if (s2 != null) {
         s2.stop();
      }
      if (s1 != null) {
         s1.stop();
      }
   }

   @Test
   public void backupNoRandomize() throws Exception {
      check("backup=true&randomize=false");
   }

   @Test
   public void priorityBackupNoRandomize() throws Exception {
      check("priorityBackup=true&randomize=false");
   }

   private void check(String connectionProperties) throws Exception {
      String s1URL = newURI(0), s2URL = newURI(1);
      String uri = "failover://(" + s1URL + "," + s2URL + ")?" + connectionProperties;
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(uri);
      final int initCount1 = getConnectionCount(s1);
      final int initCount2 = getConnectionCount(s2);

      for (int i = 0; i < 10; i++) {
         buildConnection(factory);
      }

      assertTrue(connectionProperties + " broker1 connection count not zero: was[" + getConnectionCount(s1) + "]", Wait.waitFor(() -> getConnectionCount(s1) == initCount1));

      assertTrue(connectionProperties + " broker2 connection count not zero: was[" + getConnectionCount(s2) + "]", Wait.waitFor(() -> getConnectionCount(s2) == initCount2));
   }

   private int getConnectionCount(EmbeddedJMS server) throws Exception {
      ManagementService managementService = server.getActiveMQServer().getManagementService();
      ActiveMQServerControl jmsControl = (ActiveMQServerControl) managementService.getServerControl();
      String[] ids = jmsControl.listConnectionIDs();
      if (ids != null) {
         return ids.length;
      }
      return 0;
   }

   private void buildConnection(ConnectionFactory local) throws Exception {
      Connection conn = null;
      Session sess = null;
      try {
         conn = local.createConnection();
         sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      } finally {
         try {
            if (sess != null)
               sess.close();
         } catch (JMSException ignore) {
         }
         try {
            if (conn != null)
               conn.close();
         } catch (JMSException ignore) {
         }
      }
   }
}
