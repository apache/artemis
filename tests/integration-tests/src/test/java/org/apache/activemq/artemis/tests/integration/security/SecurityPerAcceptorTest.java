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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class SecurityPerAcceptorTest extends ActiveMQTestBase {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = SecurityPerAcceptorTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private ServerLocator locator;
   private final boolean invm;
   private final String acceptorUrl;

   @Parameters(name = "invm={0}")
   public static Collection<Object[]> data() {
      List<Object[]> list = Arrays.asList(new Object[][]{{true}, {false}});
      return list;
   }

   public SecurityPerAcceptorTest(boolean invm) {
      super();
      this.invm = invm;
      acceptorUrl = invm ? "vm://1?securityDomain=PropertiesLogin" : "tcp://127.0.0.1:61616?securityDomain=PropertiesLogin";
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = invm ? createInVMLocator(1) : createNettyNonHALocator();
   }

   @TestTemplate
   public void testJAASSecurityManagerAuthentication() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager();
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true).addAcceptorConfiguration("acceptor", acceptorUrl), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
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
   public void testJAASSecurityManagerAuthorizationNegative() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager();
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().addAcceptorConfiguration("acceptor", acceptorUrl).setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
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
         assertTrue(e.getMessage().contains("User: first does not have permission='CREATE_DURABLE_QUEUE' for queue durableQueue on address address"));
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='DELETE_DURABLE_QUEUE' for queue durableQueue on address address"));
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CREATE_NON_DURABLE_QUEUE' for queue nonDurableQueue on address address"));
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='DELETE_NON_DURABLE_QUEUE' for queue nonDurableQueue on address address"));
      }

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='SEND' on address address"));
      }

      // CONSUME
      try {
         ClientConsumer consumer = session.createConsumer(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CONSUME' for queue durableQueue on address address"));
      }

      // MANAGE
      try {
         ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='MANAGE' on address activemq.management"));
      }

      // BROWSE
      try {
         ClientConsumer browser = session.createConsumer(DURABLE_QUEUE, true);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='BROWSE' for queue durableQueue on address address"));
      }
   }

   @TestTemplate
   public void testJAASSecurityManagerAuthorizationPositive() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager();
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true).addAcceptorConfiguration("acceptor", acceptorUrl), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
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
