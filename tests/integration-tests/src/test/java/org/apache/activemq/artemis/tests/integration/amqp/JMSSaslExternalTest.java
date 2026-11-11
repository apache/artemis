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

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.client.AMQPClientConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientConnectionManager;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.EventHandler;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.sasl.ExternalMechanism;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class JMSSaslExternalTest extends ActiveMQTestBase {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = JMSSaslExternalTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private ActiveMQServer server;
   private final boolean debug = false;

   @BeforeEach
   public void initialise() throws Exception {
      setUpDebug();

      startServer();
   }

   protected void setUpDebug() throws Exception {
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

   protected void startServer() throws Exception {
      ConfigurationImpl configuration = createBasicConfig(0).setJMXManagementEnabled(false);
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("CertLogin");
      server = addServer(ActiveMQServers.newActiveMQServer(configuration.setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

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

   @AfterEach
   public void stopServer() throws Exception {
      server.stop();
   }

   @Test
   @Timeout(60)
   public void testConnection() throws Exception {

      final String keystore = this.getClass().getClassLoader().getResource("other-client-keystore.jks").getFile();
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

   @Test
   public void testOutbound() throws Exception {

      final Map<String, Object> config = new LinkedHashMap<>(); config.put(TransportConstants.HOST_PROP_NAME, "localhost");
      config.put(TransportConstants.PORT_PROP_NAME, String.valueOf(61616));
      config.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      config.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      config.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      config.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      config.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      config.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);


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

      final ClientSASLFactory clientSASLFactory = availableMechanims -> {
         ExternalMechanism externalMechanism = new ExternalMechanism();
         return new ClientSASL() {
            @Override
            public String getName() {
               return externalMechanism.getName();
            }

            @Override
            public byte[] getInitialResponse() {
               return externalMechanism.getInitialResponse();
            }

            @Override
            public byte[] getResponse(byte[] challenge) {
               return new byte[0];
            }
         };
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

}
