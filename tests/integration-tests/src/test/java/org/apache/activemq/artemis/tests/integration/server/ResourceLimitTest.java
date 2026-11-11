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
package org.apache.activemq.artemis.tests.integration.server;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.ActiveMQSessionCreationException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ResourceLimitTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ResourceLimitSettings resourceLimitSettings = new ResourceLimitSettings();
      resourceLimitSettings.setMatch(SimpleString.of("myUser"));
      resourceLimitSettings.setMaxConnections(1);
      resourceLimitSettings.setMaxQueues(1);

      Configuration configuration = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY)).addResourceLimitSettings(resourceLimitSettings).setSecurityEnabled(true);

      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      server.start();

      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("myUser", "password");
      securityManager.getConfiguration().addRole("myUser", "arole");
      Role role = new Role("arole", false, false, false, false, true, true, false, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch("#", roles);
   }

   @Test
   public void testSessionLimitForUser() throws Exception {
      ServerLocator locator = addServerLocator(createNonHALocator(false));
      ClientSessionFactory clientSessionFactory = locator.createSessionFactory();
      ClientSession clientSession = clientSessionFactory.createSession("myUser", "password", false, true, true, false, 0);

      try {
         ClientSessionFactory extraClientSessionFactory = locator.createSessionFactory();
         ClientSession extraClientSession = extraClientSessionFactory.createSession("myUser", "password", false, true, true, false, 0);
         fail("creating a session factory here should fail");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSessionCreationException.class, e);
      }

      clientSession.close();

      clientSession = clientSessionFactory.createSession("myUser", "password", false, true, true, false, 0);

      try {
         ClientSessionFactory extraClientSessionFactory = locator.createSessionFactory();
         ClientSession extraClientSession = extraClientSessionFactory.createSession("myUser", "password", false, true, true, false, 0);
         fail("creating a session factory here should fail");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSessionCreationException.class, e);
      }
   }

   @Test
   public void testQueueLimitForUser() throws Exception {
      ServerLocator locator = addServerLocator(createNonHALocator(false));
      ClientSessionFactory clientSessionFactory = locator.createSessionFactory();
      ClientSession clientSession = clientSessionFactory.createSession("myUser", "password", false, true, true, false, 0);
      clientSession.createQueue(QueueConfiguration.of("queue").setAddress("address").setRoutingType(RoutingType.ANYCAST).setDurable(false));

      try {
         clientSession.createQueue(QueueConfiguration.of("anotherQueue").setAddress("address").setRoutingType(RoutingType.ANYCAST).setDurable(false));
         fail("Should have thrown an ActiveMQSecurityException");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }

      clientSession.deleteQueue("queue");

      clientSession.createQueue(QueueConfiguration.of("queue").setAddress("address").setRoutingType(RoutingType.ANYCAST).setDurable(false));

      try {
         clientSession.createQueue(QueueConfiguration.of("anotherQueue").setAddress("address").setRoutingType(RoutingType.ANYCAST).setDurable(false));
         fail("Should have thrown an ActiveMQSecurityException");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }

      try {
         clientSession.createSharedQueue(QueueConfiguration.of("anotherQueue").setAddress("address").setDurable(false));
         fail("Should have thrown an ActiveMQSecurityException");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }
   }
}
