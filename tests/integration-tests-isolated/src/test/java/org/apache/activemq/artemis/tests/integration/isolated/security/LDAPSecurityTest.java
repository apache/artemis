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
package org.apache.activemq.artemis.tests.integration.isolated.security;

import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port = 1024)})
@ApplyLdifFiles("test.ldif")
public class LDAPSecurityTest extends AbstractLdapTestUnit {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = LDAPSecurityTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private ServerLocator locator;
   ActiveMQServer server;

   public static final String TARGET_TMP = "./target/tmp";
   private static final String PRINCIPAL = "uid=admin,ou=system";
   private static final String CREDENTIALS = "secret";

   public LDAPSecurityTest() {
      File parent = new File(TARGET_TMP);
      parent.mkdirs();
      temporaryFolder = new TemporaryFolder(parent);
   }

   @Rule
   public TemporaryFolder temporaryFolder;
   private String testDir;

   @Before
   public void setUp() throws Exception {
      locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getCanonicalName()));
      testDir = temporaryFolder.getRoot().getAbsolutePath();

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("LDAPLogin");
      Configuration configuration = new ConfigurationImpl().setSecurityEnabled(true).addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName())).setJournalDirectory(ActiveMQTestBase.getJournalDir(testDir, 0, false)).setBindingsDirectory(ActiveMQTestBase.getBindingsDir(testDir, 0, false)).setPagingDirectory(ActiveMQTestBase.getPageDir(testDir, 0, false)).setLargeMessagesDirectory(ActiveMQTestBase.getLargeMessagesDir(testDir, 0, false));
      server = ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, false);
   }

   @After
   public void tearDown() throws Exception {
      locator.close();
      server.stop();
   }

   @Test
   public void testRunning() throws Exception {
      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.PROVIDER_URL, "ldap://localhost:1024");
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, PRINCIPAL);
      env.put(Context.SECURITY_CREDENTIALS, CREDENTIALS);
      DirContext ctx = new InitialDirContext(env);

      Set<String> set = new HashSet<>();

      NamingEnumeration<NameClassPair> list = ctx.list("ou=system");

      while (list.hasMore()) {
         NameClassPair ncp = list.next();
         set.add(ncp.getName());
      }

      Assert.assertTrue(set.contains("uid=admin"));
      Assert.assertTrue(set.contains("ou=users"));
      Assert.assertTrue(set.contains("ou=groups"));
      Assert.assertTrue(set.contains("ou=configuration"));
      Assert.assertTrue(set.contains("prefNodeName=sysPrefRoot"));

      ctx.close();
   }

   @Test
   public void testJAASSecurityManagerAuthentication() throws Exception {
      server.start();
      ClientSessionFactory cf = locator.createSessionFactory();

      try {
         ClientSession session = cf.createSession("first", "secret", false, true, true, false, 0);
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         Assert.fail("should not throw exception");
      }

      cf.close();
   }

   @Test
   public void testJAASSecurityManagerAuthenticationBadPassword() throws Exception {
      server.start();
      ClientSessionFactory cf = locator.createSessionFactory();

      try {
         cf.createSession("first", "badpassword", false, true, true, false, 0);
         Assert.fail("should throw exception here");
      } catch (Exception e) {
         // ignore
      }

      cf.close();
   }

   @Test
   public void testJAASSecurityManagerAuthorizationNegative() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, false, false, false, false, false, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();
      server.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
      server.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));

      ClientSessionFactory cf = locator.createSessionFactory();
      ClientSession session = cf.createSession("first", "secret", false, true, true, false, 0);

      // CREATE_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // CONSUME
      try {
         ClientConsumer consumer = session.createConsumer(DURABLE_QUEUE);
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // MANAGE
      try {
         ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
         producer.send(session.createMessage(true));
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // BROWSE
      try {
         ClientConsumer browser = session.createConsumer(DURABLE_QUEUE, true);
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      session.close();
      cf.close();
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositive() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("admins", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();

      ClientSessionFactory cf = locator.createSessionFactory();
      ClientSession session = cf.createSession("first", "secret", false, true, true, false, 0);

      // CREATE_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
      } catch (ActiveMQException e) {
         e.printStackTrace();
         Assert.fail("should not throw exception here");
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
      } catch (ActiveMQException e) {
         e.printStackTrace();
         Assert.fail("should not throw exception here");
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
      } catch (ActiveMQException e) {
         Assert.fail("should not throw exception here");
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
      } catch (ActiveMQException e) {
         Assert.fail("should not throw exception here");
      }

      session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
      } catch (ActiveMQException e) {
         Assert.fail("should not throw exception here");
      }

      // CONSUME
      try {
         session.createConsumer(DURABLE_QUEUE);
      } catch (ActiveMQException e) {
         Assert.fail("should not throw exception here");
      }

      // MANAGE
      try {
         ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
         producer.send(session.createMessage(true));
      } catch (ActiveMQException e) {
         Assert.fail("should not throw exception here");
      }

      // CONSUME
      try {
         session.createConsumer(DURABLE_QUEUE, true);
      } catch (ActiveMQException e) {
         Assert.fail("should not throw exception here");
      }

      session.close();
      cf.close();
   }
}
