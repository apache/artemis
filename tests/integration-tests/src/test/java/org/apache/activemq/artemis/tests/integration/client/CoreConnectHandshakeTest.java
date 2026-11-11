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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoreConnectHandshakeTest extends ActiveMQTestBase {

   private static final String CONNECT_USER = "connectUser";

   private static final String CONNECT_PASS = "connectPass";

   private ActiveMQServer server;

   private TransportConfiguration connector;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      connector = getNettyConnectorTransportConfiguration(true);

      final ConfigurationImpl config = (ConfigurationImpl) createDefaultNettyConfig();
      config.setSecurityEnabled(true);
      config.setClusterUser("clusterUser");
      config.setClusterPassword(CLUSTER_PASSWORD);
      config.addConnectorConfiguration(connector.getName(), connector);
      config.addClusterConfiguration(basicClusterConnectionConfig(connector.getName(), connector.getName()));

      final SecurityConfiguration securityConfiguration = new SecurityConfiguration();
      securityConfiguration.addUser(CONNECT_USER, CONNECT_PASS);
      securityConfiguration.addRole(CONNECT_USER, "role");

      final ActiveMQJAASSecurityManager securityManager =
         new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);

      server = addServer(new ActiveMQServerImpl(config, securityManager));

      final Role role = new Role("role", true, true, true, true, true, true, true, true, true, true, false, false);
      final Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch("#", roles);

      server.start();
   }

   @Test
   public void testWrongConnectionCredentialsRejectedAtConnect() throws Exception {
      try (ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(connector))) {
         locator.setConnectionCredentials("wrong", "wrong");

         assertThrows(ActiveMQSecurityException.class, () -> locator.createSessionFactory("wrong", "wrong"));
      }
   }

   @Test
   public void testPreAuthTopologyHiddenUntilSessionAuth() throws Exception {
      try (ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(connector))) {
         try (ClientSessionFactory sessionFactory = locator.createSessionFactory()) {
            assertTrue(locator.getTopology().getMembers().isEmpty(),
               "topology must not expose broker nodes before session authentication");

            try (ClientSession session = sessionFactory.createSession(CONNECT_USER, CONNECT_PASS, false, true, true, false, 1)) {
               Wait.assertTrue(() -> locator.getTopology().getMember(server.getNodeID().toString()) != null,
                  5000, 100);
            }
         }
      }
   }

   @Test
   public void testConnectionCredentialsAuthenticateBeforeTopology() throws Exception {
      try (ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(connector))) {
         locator.setConnectionCredentials(CONNECT_USER, CONNECT_PASS);

         try (ClientSessionFactory sessionFactory = locator.createSessionFactory(CONNECT_USER, CONNECT_PASS)) {
            Wait.assertTrue(() -> locator.getTopology().getMember(server.getNodeID().toString()) != null,
               5000, 100);

            try (ClientSession session = sessionFactory.createSession(CONNECT_USER, CONNECT_PASS, false, true, true, false, 1)) {
               session.createQueue(QueueConfiguration.of("queue-" + getName()));
            }
         }
      }
   }
}
