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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin;
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
@ApplyLdifFiles("AMQauth2.ldif")
public class LegacyLDAPSecuritySettingPluginTest2 extends AbstractLdapTestUnit {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = LegacyLDAPSecuritySettingPluginTest2.class.getClassLoader().getResource("login.config");
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

   public LegacyLDAPSecuritySettingPluginTest2() {
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

      Map<String, String> init = new HashMap<>();
      init.put("destinationBase", "ou=Destination,ou=ActiveMQ,o=example,ou=system");
      init.put("roleAttribute", "member");

      LegacyLDAPSecuritySettingPlugin legacyLDAPSecuritySettingPlugin = new LegacyLDAPSecuritySettingPlugin()
         .setInitialContextFactory("com.sun.jndi.ldap.LdapCtxFactory")
         .setConnectionURL("ldap://localhost:1024")
         .setConnectionUsername("uid=admin,ou=system")
         .setConnectionPassword("secret")
         .setConnectionProtocol("s")
         .setAuthentication("simple")
         .init(init);

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("LDAPLogin2");
      Configuration configuration = new ConfigurationImpl()
         .setSecurityEnabled(true)
         .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()))
         .setJournalDirectory(ActiveMQTestBase.getJournalDir(testDir, 0, false))
         .setBindingsDirectory(ActiveMQTestBase.getBindingsDir(testDir, 0, false))
         .setPagingDirectory(ActiveMQTestBase.getPageDir(testDir, 0, false))
         .setLargeMessagesDirectory(ActiveMQTestBase.getLargeMessagesDir(testDir, 0, false))
         .setPersistenceEnabled(false)
         .addSecuritySettingPlugin(legacyLDAPSecuritySettingPlugin);

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
   }

   @Test
   public void testBasicPluginAuthorization() throws Exception {
      server.start();
      ClientSessionFactory cf = locator.createSessionFactory();
      String name = "TEST.FOO";

      try {
         ClientSession session = cf.createSession("admin", "secret", false, true, true, false, 0);
         session.createQueue(QueueConfiguration.of(name));
         ClientProducer producer = session.createProducer();
         producer.send(name, session.createMessage(false));
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         Assert.fail("should not throw exception");
      }

      cf.close();
   }
}
