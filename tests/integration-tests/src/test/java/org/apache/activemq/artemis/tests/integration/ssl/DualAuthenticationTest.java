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
package org.apache.activemq.artemis.tests.integration.ssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.tests.integration.security.SecurityTest;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class DualAuthenticationTest extends ActiveMQTestBase {

   public static final SimpleString QUEUE = SimpleString.of("QueueOverSSL");

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = SecurityTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private String SERVER_SIDE_KEYSTORE = "server-keystore.jks";
   private String SERVER_SIDE_TRUSTSTORE = "client-ca-truststore.jks";
   private String CLIENT_SIDE_TRUSTSTORE = "server-ca-truststore.jks";
   private String CLIENT_SIDE_KEYSTORE = "client-keystore.jks";
   private final String PASSWORD = "securepass";

   private ActiveMQServer server;

   private TransportConfiguration tc;

   @Test
   public void testDualAuthentication() throws Exception {
      String text = RandomUtil.randomUUIDString();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);
      tc.getParams().put(TransportConstants.PORT_PROP_NAME, "61617");

      ServerLocator producerLocator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory producerSessionFactory = createSessionFactory(producerLocator);
      ClientSession producerSession = producerSessionFactory.createSession(false, true, true);
      producerSession.createQueue(QueueConfiguration.of(DualAuthenticationTest.QUEUE).setDurable(false));
      ClientProducer producer = producerSession.createProducer(DualAuthenticationTest.QUEUE);

      ClientMessage message = createTextMessage(producerSession, text);
      producer.send(message);

      ServerLocator consumerLocator = addServerLocator(ActiveMQClient.createServerLocator("tcp://localhost:61616"));
      ClientSessionFactory consumerSessionFactory = createSessionFactory(consumerLocator);
      ClientSession consumerSession = consumerSessionFactory.createSession("consumer", "consumerPassword", false, true, true, consumerLocator.isPreAcknowledge(), consumerLocator.getAckBatchSize());
      ClientConsumer consumer = consumerSession.createConsumer(DualAuthenticationTest.QUEUE);
      consumerSession.start();

      Message m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals(text, m.getBodyBuffer().readString());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, SERVER_SIDE_KEYSTORE);
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, SERVER_SIDE_TRUSTSTORE);
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      params.put(TransportConstants.PORT_PROP_NAME, "61617");
      ConfigurationImpl config = createBasicConfig();
      config.addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));
      config.addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY));
      config.setSecurityEnabled(true);

      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager("DualAuthenticationPropertiesLogin", "DualAuthenticationCertLogin");
      server = addServer(ActiveMQServers.newActiveMQServer(config, ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Role sendRole = new Role("producers", true, false, true, false, true, false, false, false, true, false, false, false);
      Role receiveRole = new Role("consumers", false, true, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(sendRole);
      roles.add(receiveRole);
      securityRepository.addMatch(DualAuthenticationTest.QUEUE.toString(), roles);

      server.start();
      waitForServerToStart(server);
      tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
   }
}
