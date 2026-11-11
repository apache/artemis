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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_1_4_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_4_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_10_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_17_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_22_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_28_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_33_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.HORNETQ_2_3_5;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.HORNETQ_2_4_11;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LegacyClientTopologyTest extends ClasspathBase {

   private ActiveMQServer server;
   private ActiveMQServer server2;

   @BeforeEach
   public void setUp() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.setJournalType(JournalType.NIO);
      configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616?anycastPrefix=jms.queue.&multicastPrefix=jms.topic.");
      configuration.setSecurityEnabled(true);
      configuration.setPersistenceEnabled(false);
      configuration.setClusterPassword("guest");
      configuration.addConnectorConfiguration("netty-connector", "tcp://localhost:61616");
      configuration.addConnectorConfiguration("netty-connector-2", "tcp://localhost:61617");
      configuration.addClusterConfiguration(new ClusterConnectionConfiguration().setName("my-cluster").setConnectorName("netty-connector").setStaticConnectors(Arrays.asList("netty-connector-2")));

      server = ActiveMQServers.newActiveMQServer(configuration, false);
      server.start();

      ConfigurationImpl configuration2 = new ConfigurationImpl();
      configuration2.setJournalType(JournalType.NIO);
      configuration2.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61617?anycastPrefix=jms.queue.&multicastPrefix=jms.topic.");
      configuration2.setSecurityEnabled(true);
      configuration2.setPersistenceEnabled(false);
      configuration2.setClusterPassword("guest");
      configuration2.addConnectorConfiguration("netty-connector", "tcp://localhost:61616");
      configuration2.addConnectorConfiguration("netty-connector-2", "tcp://localhost:61617");
      configuration2.addClusterConfiguration(new ClusterConnectionConfiguration().setName("my-cluster").setConnectorName("netty-connector-2").setStaticConnectors(Arrays.asList("netty-connector")));

      server2 = ActiveMQServers.newActiveMQServer(configuration2, false);
      server2.start();

      setupSecurity(server);
      setupSecurity(server2);


      Wait.assertTrue(() -> {
         for (org.apache.activemq.artemis.core.server.cluster.ClusterConnection cc : server.getClusterManager().getClusterConnections()) {
            if (cc.getTopology().getMembers().size() == 2) {
               return true;
            }
         }
         return false;
      }, 10000, 100);

      server.createQueue(QueueConfiguration.of("jms.queue.testQueue").setRoutingType(RoutingType.ANYCAST).setDurable(false));
   }

   private void setupSecurity(ActiveMQServer srv) {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) srv.getSecurityManager();
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "guest");

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("guest", true, true, true, true, true, true, true, true, true, true, false, false));
      srv.getSecurityRepository().addMatch("#", roles);
   }

   @AfterEach
   public void tearDown() throws Exception {
      if (server2 != null) {
         server2.stop();
      }
      if (server != null) {
         server.stop();
      }
   }

   private void runHQTopologyTest(String version) throws Throwable {
      ClassLoader clientClassloader = getClasspath(version, false);
      clearGroovy(clientClassloader);
      Boolean result = (Boolean) evaluate(clientClassloader, "legacyclient/hqTopologyTest.groovy");
      assertTrue(result);
   }

   private void runArtemisTopologyTest(String version) throws Throwable {
      ClassLoader clientClassloader = getClasspath(version, false);
      clearGroovy(clientClassloader);
      Boolean result = (Boolean) evaluate(clientClassloader, "legacyclient/artemisTopologyTest.groovy");
      assertTrue(result);
   }

   @Test
   public void testTopologyFromHQ_2_4_11() throws Throwable {
      runHQTopologyTest(HORNETQ_2_4_11);
   }

   @Test
   public void testTopologyFromHQ_2_3_5() throws Throwable {
      runHQTopologyTest(HORNETQ_2_3_5);
   }


   @Test
   public void testTopologyFromArtemis_1_4_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_1_4_0);
   }

   @Test
   public void testTopologyFromArtemis_2_4_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_4_0);
   }

   @Test
   public void testTopologyFromArtemis_2_10_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_10_0);
   }

   @Test
   public void testTopologyFromArtemis_2_17_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_17_0);
   }

   @Test
   public void testTopologyFromArtemis_2_22_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_22_0);
   }

   @Test
   public void testTopologyFromArtemis_2_28_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_28_0);
   }

   @Test
   public void testTopologyFromArtemis_2_33_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_33_0);
   }

   @Test
   public void testTopologyFromArtemis_2_44_0() throws Throwable {
      runArtemisTopologyTest(ARTEMIS_2_44_0);
   }

   // The purpose for snapshot is to validate itself
   @Test
   public void testTopologyFromArtemis_SNAPSHOT() throws Throwable {
      runArtemisTopologyTest(SNAPSHOT);
   }
}
