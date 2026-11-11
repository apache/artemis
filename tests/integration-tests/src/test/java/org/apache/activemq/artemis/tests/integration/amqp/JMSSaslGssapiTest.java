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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.client.AMQPClientConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientConnectionManager;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.EventHandler;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.hadoop.minikdc.MiniKdc;
import org.apache.kerby.kerberos.kerb.keytab.Keytab;
import org.apache.kerby.kerberos.kerb.keytab.KeytabEntry;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.sasl.GssapiMechanism;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class JMSSaslGssapiTest extends JMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = JMSSaslGssapiTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private static final String KRB5_TCP_PORT_TEMPLATE = "MINI_KDC_PORT";
   private static final String KRB5_CONFIG_TEMPLATE = "minikdc-krb5-template.conf";
   private static final String KRB5_DEFAULT_KEYTAB = "target/test.krb5.keytab";

   private static MiniKdc kdc;
   private static final boolean debug = false;

   @BeforeAll
   public static void setUpKerberos() throws Exception {
      Properties kdcConf = MiniKdc.createConf();
      kdcConf.setProperty("debug", Boolean.toString(debug));

      // Creates a single server for the tests as there were failures when spawning and killing one per unit test.
      kdc = new MiniKdc(kdcConf, new File("target/"));
      kdc.start();

      // hard coded match, default_keytab_name in minikdc-krb5.conf template
      File userKeyTab = new File(KRB5_DEFAULT_KEYTAB);
      kdc.createPrincipal(userKeyTab, "client", "amqp/localhost");

      // We need to hard code the default keyTab into the Krb5 configuration file which is not possible
      // with this version of MiniKDC so we use a template file and replace the port with the value from
      // the MiniKDC instance we just started.
      rewriteKrbConfFile(kdc);

      if (debug) {
         logger.debug("java.security.krb5.conf='{}'", System.getProperty("java.security.krb5.conf"));
         try (BufferedReader br = new BufferedReader(new FileReader(System.getProperty("java.security.krb5.conf")))) {
            br.lines().forEach(line -> logger.debug(line));
         }

         Keytab kt = Keytab.loadKeytab(userKeyTab);
         for (PrincipalName name : kt.getPrincipals()) {
            for (KeytabEntry entry : kt.getKeytabEntries(name)) {
               logger.info("KeyTab Entry: PrincipalName:{} ; KeyInfo:{}", entry.getPrincipal(), entry.getKey().getKeyType());
            }
         }

         for (java.util.logging.Logger logger : new java.util.logging.Logger[] {java.util.logging.Logger.getLogger("logincontext"),
                                                                                java.util.logging.Logger.getLogger("javax.security.sasl"),
                                                                                java.util.logging.Logger.getLogger("org.apache.qpid.proton")}) {
            logger.setLevel(java.util.logging.Level.FINEST);
            logger.addHandler(new java.util.logging.ConsoleHandler());
            for (java.util.logging.Handler handler : logger.getHandlers()) {
               handler.setLevel(java.util.logging.Level.FINEST);
            }
         }
      }
   }

   @AfterAll
   public static void stopKerberos() throws Exception {
      if (kdc != null) {
         kdc.stop();
      }
   }

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected void configureBrokerSecurity(ActiveMQServer server) {
      server.getConfiguration().setSecurityEnabled(isSecurityEnabled());
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.setConfigurationName("Krb5Plus");
      securityManager.setConfiguration(null);

      final String roleName = "ALLOW_ALL";
      Role role = new Role(roleName, true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(getQueueName(), roles);

      if (debug) {
         // The default produces so much log spam that debugging the exchanges becomes impossible.
         server.getConfiguration().setMessageExpiryScanPeriod(30_000);
      }
   }

   @Override
   protected String getJmsConnectionURIOptions() {
      return "amqp.saslMechanisms=GSSAPI";
   }

   @Override
   protected URI getBrokerQpidJMSConnectionURI() {
      try {
         int port = AMQP_PORT;

         // match the sasl.service <the host name>
         String uri = "amqp://localhost:" + port;

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         }

         return new URI(uri);
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("saslMechanisms", "GSSAPI");
      params.put("saslLoginConfigScope", "amqp-sasl-gssapi");
   }

   @Test
   @Timeout(60)
   public void testConnection() throws Exception {
      Connection connection = createConnection("client", null);
      connection.start();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(getQueueName());
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

   @Test
   @Timeout(60)
   public void testSaslPlainConnectionDenied() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(new URI("amqp://localhost:" + AMQP_PORT + "?amqp.saslMechanisms=PLAIN"));
      try {
         factory.createConnection("plain", "secret");
         fail("Expect sasl failure");
      } catch (JMSSecurityException expected) {
         assertTrue(expected.getMessage().contains("SASL"));
      }
   }

   @Test
   @Timeout(90)
   public void testOutboundWithSlowMech() throws Exception {
      final Map<String, Object> config = new LinkedHashMap<>(); config.put(TransportConstants.HOST_PROP_NAME, "localhost");
      config.put(TransportConstants.PORT_PROP_NAME, String.valueOf(AMQP_PORT));
      final ClientSASLFactory clientSASLFactory = availableMechanims -> {
         GssapiMechanism gssapiMechanism = new GssapiMechanism();
         return new ClientSASL() {
            @Override
            public String getName() {
               return gssapiMechanism.getName();
            }

            @Override
            public byte[] getInitialResponse() {
               gssapiMechanism.setUsername("client");
               gssapiMechanism.setServerName("localhost");
               try {
                  return gssapiMechanism.getInitialResponse();
               } catch (Exception e) {
                  e.printStackTrace();
               }
               return new byte[0];
            }

            @Override
            public byte[] getResponse(byte[] challenge) {
               try {
                  // simulate a slow client
                  TimeUnit.SECONDS.sleep(4);
               } catch (InterruptedException e) {
                  e.printStackTrace();
               }
               try {
                  return gssapiMechanism.getChallengeResponse(challenge);
               } catch (Exception e) {
                  e.printStackTrace();
               }
               return new byte[0];
            }
         };
      };

      final AtomicBoolean connectionOpened = new AtomicBoolean();
      final AtomicBoolean authFailed = new AtomicBoolean();

      EventHandler eventHandler = new EventHandler() {
         @Override
         public void onRemoteOpen(org.apache.qpid.proton.engine.Connection connection) throws Exception {
            connectionOpened.set(true);
         }

         @Override
         public void onAuthFailed(ProtonHandler protonHandler, org.apache.qpid.proton.engine.Connection connection) {
            authFailed.set(true);
         }
      };

      ProtonClientConnectionManager lifeCycleListener = new ProtonClientConnectionManager(new AMQPClientConnectionFactory(server, "myid", Collections.singletonMap(Symbol.getSymbol("myprop"), "propvalue"), 5000), Optional.of(eventHandler), clientSASLFactory);
      ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);
      NettyConnector connector = new NettyConnector(config, lifeCycleListener, lifeCycleListener, server.getExecutorFactory().getExecutor(), server.getExecutorFactory().getExecutor(), server.getScheduledPool(), protocolManager);
      connector.start();
      connector.createConnection();

      try {
         Wait.assertEquals(1, server::getConnectionCount);
         Wait.assertTrue(connectionOpened::get);
         Wait.assertFalse(authFailed::get);

         lifeCycleListener.stop();

         Wait.assertEquals(0, server::getConnectionCount);
      } finally {
         lifeCycleListener.stop();
      }
   }

   private static void rewriteKrbConfFile(MiniKdc server) throws Exception {
      final Path template = Paths.get(JMSSaslGssapiTest.class.getClassLoader().getResource(KRB5_CONFIG_TEMPLATE).toURI());
      final String krb5confTemplate = Files.readString(template);
      final String replacementPort = Integer.toString(server.getPort());

      // Replace the port template with the current actual port of the MiniKDC Server instance.
      final String krb5confUpdated = krb5confTemplate.replaceAll(KRB5_TCP_PORT_TEMPLATE, replacementPort);

      try (OutputStream outputStream = Files.newOutputStream(server.getKrb5conf().toPath());
           WritableByteChannel channel = Channels.newChannel(outputStream)) {

         channel.write(ByteBuffer.wrap(krb5confUpdated.getBytes(StandardCharsets.UTF_8)));
      }
   }
}
