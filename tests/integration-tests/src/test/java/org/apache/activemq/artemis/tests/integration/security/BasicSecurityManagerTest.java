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
package org.apache.activemq.artemis.tests.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class BasicSecurityManagerTest extends ActiveMQTestBase {

   private ServerLocator locator;
   private boolean bootstrapProperties;

   public BasicSecurityManagerTest(boolean bootstrapProperties) {
      this.bootstrapProperties = bootstrapProperties;
   }

   @Parameters(name = "bootstrapProperties={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{false}, {true}};
      return Arrays.asList(params);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = createInVMNonHALocator();
   }

   public ActiveMQServer initializeServer() throws Exception {
      return initializeServer(true);
   }

   public ActiveMQServer initializeServer(boolean persistenceEnabled) throws Exception {
      Map<String, String> initProperties = new HashMap<>();
      if (bootstrapProperties) {
         initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_USER_FILE, "users.properties");
         initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_ROLE_FILE, "roles.properties");
      } else {
         initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_USER, "first");
         initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_PASSWORD, "secret");
         initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_ROLE, "programmers");
      }
      ActiveMQBasicSecurityManager securityManager = new ActiveMQBasicSecurityManager().init(initProperties);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, persistenceEnabled));
      return server;
   }

   @TestTemplate
   public void testPersistenceDisabled() throws Exception {
      ActiveMQServer server = initializeServer(false);
      ActiveMQIllegalStateException e = assertThrows(ActiveMQIllegalStateException.class, server::start);
      assertTrue(e.getMessage().startsWith("AMQ229264"), e.getMessage());
      assertFalse(server.isStarted());
   }

   @TestTemplate
   public void testAuthenticationForBootstrapUser() throws Exception {
      ActiveMQServer server = initializeServer();
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession("first", "secret", false, true, true, false, 0);
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   @TestTemplate
   public void testAuthenticationForAddedUserHashed() throws Exception {
      internalTestAuthenticationForAddedUser(false);
   }

   @TestTemplate
   public void testAuthenticationForAddedUserPlainText() throws Exception {
      internalTestAuthenticationForAddedUser(true);
   }

   private void internalTestAuthenticationForAddedUser(boolean plaintext) throws Exception {
      ActiveMQServer server = initializeServer();
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      server.getActiveMQServerControl().addUser("foo", "bar", "baz", plaintext);

      try {
         ClientSession session = cf.createSession("foo", "bar", false, true, true, false, 0);
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   @TestTemplate
   public void testWithValidatedUser() throws Exception {
      ActiveMQServer server = initializeServer();
      server.getConfiguration().setPopulateValidatedUser(true);
      server.start();
      Role role = new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch("#", roles);
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession("first", "secret", false, true, true, false, 0);
         server.createQueue(QueueConfiguration.of("queue").setAddress("address").setRoutingType(RoutingType.ANYCAST));
         ClientProducer producer = session.createProducer("address");
         producer.send(session.createMessage(true));
         session.commit();
         producer.close();
         ClientConsumer consumer = session.createConsumer("queue");
         session.start();
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         assertEquals("first", message.getValidatedUserID());
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   @TestTemplate
   public void testAuthenticationBadPassword() throws Exception {
      ActiveMQServer server = initializeServer();
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         cf.createSession("first", "badpassword", false, true, true, false, 0);
         fail("should throw exception here");
      } catch (Exception e) {
         // ignore
      }
   }

   @TestTemplate
   public void testAuthorizationNegative() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQServer server = initializeServer();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, false, false, false, false, false, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();
      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("first", "secret", false, true, true, false, 0));

      // CREATE_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CREATE_DURABLE_QUEUE' for queue durableQueue on address address"), "Unexpected exception message: " + e.getMessage());
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='DELETE_DURABLE_QUEUE' for queue durableQueue on address address"), "Unexpected exception message: " + e.getMessage());
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CREATE_NON_DURABLE_QUEUE' for queue nonDurableQueue on address address"), "Unexpected exception message: " + e.getMessage());
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='DELETE_NON_DURABLE_QUEUE' for queue nonDurableQueue on address address"), "Unexpected exception message: " + e.getMessage());
      }

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='SEND' on address address"), "Unexpected exception message: " + e.getMessage());
      }

      // CONSUME
      try {
         ClientConsumer consumer = session.createConsumer(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CONSUME' for queue durableQueue on address address"), "Unexpected exception message: " + e.getMessage());
      }

      // MANAGE
      try {
         ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='MANAGE' on address activemq.management"), "Unexpected exception message: " + e.getMessage());
      }

      // BROWSE
      try {
         ClientConsumer browser = session.createConsumer(DURABLE_QUEUE, true);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='BROWSE' for queue durableQueue on address address"), "Unexpected exception message: " + e.getMessage());
      }
   }

   @TestTemplate
   public void testAuthorizationPositive() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQServer server = initializeServer();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("first", "secret", false, true, true, false, 0));

      // CREATE_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      // CONSUME
      try {
         session.createConsumer(DURABLE_QUEUE);
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      // MANAGE
      try {
         ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
         producer.send(session.createMessage(true));
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }

      // BROWSE
      try {
         session.createConsumer(DURABLE_QUEUE, true);
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }
   }
}
