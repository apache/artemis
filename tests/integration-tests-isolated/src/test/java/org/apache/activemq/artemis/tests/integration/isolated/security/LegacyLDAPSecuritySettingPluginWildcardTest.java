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
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
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

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port = 1024)})
@ApplyLdifFiles("AMQauthWildcard.ldif")
public class LegacyLDAPSecuritySettingPluginWildcardTest extends AbstractLdapTestUnit {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = LegacyLDAPSecuritySettingPluginWildcardTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   ActiveMQServer server;
   ActiveMQJAASSecurityManager securityManager;
   Configuration configuration;
   private ServerLocator locator;

   public static final String TARGET_TMP = "./target/tmp";
   private static final String PRINCIPAL = "uid=admin,ou=system";
   private static final String CREDENTIALS = "secret";

   // defined in LDIF
   private static final String AUTHORIZED_USER = "authorizedUser";
   private static final String UNAUTHORIZED_USER = "unauthorizedUser";

   public LegacyLDAPSecuritySettingPluginWildcardTest() {
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

      LegacyLDAPSecuritySettingPlugin legacyLDAPSecuritySettingPlugin = new LegacyLDAPSecuritySettingPlugin()
         .setInitialContextFactory("com.sun.jndi.ldap.LdapCtxFactory")
         .setConnectionURL("ldap://localhost:1024")
         .setConnectionUsername("uid=admin,ou=system")
         .setConnectionPassword("secret")
         .setConnectionProtocol("s")
         .setAuthentication("simple");

      securityManager = new ActiveMQJAASSecurityManager("LDAPLogin");
      configuration = new ConfigurationImpl()
         .setSecurityEnabled(true)
         .setPersistenceEnabled(false)
         .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()))
         .setJournalDirectory(ActiveMQTestBase.getJournalDir(testDir, 0, false))
         .setBindingsDirectory(ActiveMQTestBase.getBindingsDir(testDir, 0, false))
         .setPagingDirectory(ActiveMQTestBase.getPageDir(testDir, 0, false))
         .setLargeMessagesDirectory(ActiveMQTestBase.getLargeMessagesDir(testDir, 0, false))
         .addSecuritySettingPlugin(legacyLDAPSecuritySettingPlugin);
   }

   @After
   public void tearDown() throws Exception {
      locator.close();
      if (server != null) {
         server.stop();
      }
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
   public void testBasicPluginAuthorizationRightAngleBracketWithDefaultAnywords() throws Exception {
      testBasicPluginAuthorization("A.A", DEFAULT_WILDCARD_CONFIGURATION, AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationRightAngleBracketWithDefaultAnywordsNegative() throws Exception {
      testBasicPluginAuthorization("A.A", DEFAULT_WILDCARD_CONFIGURATION, UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationRightAngleBracketWithCustomAnywords() throws Exception {
      testBasicPluginAuthorization("A.A", new WildcardConfiguration().setAnyWords('%'), AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationRightAngleBracketWithCustomAnywordsNegative() throws Exception {
      testBasicPluginAuthorization("A.A", new WildcardConfiguration().setAnyWords('%'), UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationRightAngleBracketWithCustomDelimiter() throws Exception {
      testBasicPluginAuthorization("A_A", new WildcardConfiguration().setDelimiter('_'), AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationRightAngleBracketWithCustomDelimiterNegative() throws Exception {
      testBasicPluginAuthorization("A_A", new WildcardConfiguration().setDelimiter('_'), UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationDollarWithDefaultAnywords() throws Exception {
      testBasicPluginAuthorization("B.B", DEFAULT_WILDCARD_CONFIGURATION, AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationDollarWithDefaultAnywordsNegative() throws Exception {
      testBasicPluginAuthorization("B.B", DEFAULT_WILDCARD_CONFIGURATION, UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationDollarWithCustomAnywords() throws Exception {
      testBasicPluginAuthorization("B.B", new WildcardConfiguration().setAnyWords('%'), AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationDollarWithCustomAnywordsNegative() throws Exception {
      testBasicPluginAuthorization("B.B", new WildcardConfiguration().setAnyWords('%'), UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationDollarWithCustomDelimiter() throws Exception {
      testBasicPluginAuthorization("B_B", new WildcardConfiguration().setDelimiter('_'), AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationDollarWithCustomDelimiterNegative() throws Exception {
      testBasicPluginAuthorization("B_B", new WildcardConfiguration().setDelimiter('_'), UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationWithDefaultSingleWord() throws Exception {
      testBasicPluginAuthorization("C.C.C", DEFAULT_WILDCARD_CONFIGURATION, AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationWithDefaultSingleWordNegative() throws Exception {
      testBasicPluginAuthorization("C.C.C", DEFAULT_WILDCARD_CONFIGURATION, UNAUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationCustomSingleWord() throws Exception {
      testBasicPluginAuthorization("C.C.C", new WildcardConfiguration().setSingleWord('%'), AUTHORIZED_USER);
   }

   @Test
   public void testBasicPluginAuthorizationCustomSingleWordNegative() throws Exception {
      testBasicPluginAuthorization("C.C.C", new WildcardConfiguration().setSingleWord('%'), UNAUTHORIZED_USER);
   }

   private void testBasicPluginAuthorization(String name, WildcardConfiguration wildcardConfiguration, String username) throws Exception {
      if (!wildcardConfiguration.equals(DEFAULT_WILDCARD_CONFIGURATION)) {
         LegacyLDAPSecuritySettingPlugin plugin = (LegacyLDAPSecuritySettingPlugin) configuration.getSecuritySettingPlugins().iterator().next();
         plugin.setAnyWordsWildcardConversion(wildcardConfiguration.getAnyWords());
         plugin.setSingleWordWildcardConversion(wildcardConfiguration.getSingleWord());
         plugin.setDelimiterWildcardConversion(wildcardConfiguration.getDelimiter());
         configuration.setWildCardConfiguration(wildcardConfiguration);
      }
      server = ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, false);
      server.start();

      ClientSessionFactory cf = locator.createSessionFactory();

      try {
         ClientSession session = cf.createSession(username, "secret", false, true, true, false, 0);
         session.createQueue(QueueConfiguration.of(name));
         if (!username.equals(AUTHORIZED_USER)) {
            Assert.fail("should throw exception here");
         }
         ClientProducer producer = session.createProducer();
         producer.send(name, session.createMessage(false));
         session.close();
      } catch (ActiveMQException e) {
         if (username.equals(AUTHORIZED_USER)) {
            Assert.fail("should not throw exception");
         }
      }

      cf.close();
   }

   @Test
   public void testBasicPluginAuthorizationWithBadMatch() throws Exception {
      final String name = "X.X";
      server = ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, false);
      server.start();

      ClientSessionFactory cf = locator.createSessionFactory();

      try {
         ClientSession session = cf.createSession(AUTHORIZED_USER, "secret", false, true, true, false, 0);
         session.createQueue(QueueConfiguration.of(name));
         Assert.fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      cf.close();
   }
}
