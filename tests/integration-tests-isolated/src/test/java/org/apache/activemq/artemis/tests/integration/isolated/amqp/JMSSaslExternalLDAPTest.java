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
package org.apache.activemq.artemis.tests.integration.isolated.amqp;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port = 1024)})
@ApplyLdifFiles("AMQauth.ldif")
public class JMSSaslExternalLDAPTest extends AbstractLdapTestUnit {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = JMSSaslExternalLDAPTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private ActiveMQServer server;
   private final boolean debug = false;

   public static final String TARGET_TMP = "./target/tmp";

   public JMSSaslExternalLDAPTest() {
      super();
      File parent = new File(TARGET_TMP);
      parent.mkdirs();
      temporaryFolder = new TemporaryFolder(parent);
   }

   @Rule
   public TemporaryFolder temporaryFolder;
   private String testDir;

   @Before
   public void setUp() throws Exception {

      testDir = temporaryFolder.getRoot().getAbsolutePath();

      if (debug) {
         for (java.util.logging.Logger logger : new java.util.logging.Logger[] {java.util.logging.Logger.getLogger("javax.security.sasl"), java.util.logging.Logger.getLogger("org.apache.qpid.proton")}) {
            logger.setLevel(java.util.logging.Level.FINEST);
            logger.addHandler(new java.util.logging.ConsoleHandler());
            for (java.util.logging.Handler handler : logger.getHandlers()) {
               handler.setLevel(java.util.logging.Level.FINEST);
            }
         }
      }
   }


   @Before
   public void startServer() throws Exception {

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("SaslExternalPlusLdap");
      Configuration configuration = new ConfigurationImpl().setSecurityEnabled(true).addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName())).setJournalDirectory(ActiveMQTestBase.getJournalDir(testDir, 0, false)).setBindingsDirectory(ActiveMQTestBase.getBindingsDir(testDir, 0, false)).setPagingDirectory(ActiveMQTestBase.getPageDir(testDir, 0, false)).setLargeMessagesDirectory(ActiveMQTestBase.getLargeMessagesDir(testDir, 0, false));
      configuration.setConfigurationFileRefreshPeriod(-1); // such that config reload does not whack programmatic config
      server = ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, false);


      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put("saslMechanisms", "EXTERNAL");

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NettyAcceptorFactory.class.getCanonicalName(), params, "netty", extraParams));

      // role mapping via CertLogin - TextFileCertificateLoginModule
      final String roleName = "widgets";
      Role role = new Role(roleName, true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch("TEST", roles);

      server.start();
   }

   @After
   public void stopServer() throws Exception {
      server.stop();
   }

   @Test(timeout = 600000)
   public void testRoundTrip() throws Exception {

      final String keystore = this.getClass().getClassLoader().getResource("client-keystore.jks").getFile();
      final String truststore = this.getClass().getClassLoader().getResource("server-ca-truststore.jks").getFile();

      String connOptions = "?amqp.saslMechanisms=EXTERNAL" + "&" +
         "transport.trustStoreLocation=" + truststore + "&" +
         "transport.trustStorePassword=securepass" + "&" +
         "transport.keyStoreLocation=" + keystore + "&" +
         "transport.keyStorePassword=securepass" + "&" +
         "transport.verifyHost=false";

      JmsConnectionFactory factory = new JmsConnectionFactory(new URI("amqps://localhost:" + 61616 + connOptions));
      Connection connection = factory.createConnection("client", null);
      connection.start();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue("TEST");
         MessageConsumer consumer = session.createConsumer(queue);
         MessageProducer producer = session.createProducer(queue);

         final String text = RandomUtil.randomUUIDString();
         producer.send(session.createTextMessage(text));

         TextMessage m = (TextMessage) consumer.receive(1000);
         assertNotNull(m);
         assertEquals(text, m.getText());

      } finally {
         connection.close();
      }
   }

}
