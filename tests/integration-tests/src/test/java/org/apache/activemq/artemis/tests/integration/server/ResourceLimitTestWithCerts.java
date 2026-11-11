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

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.ActiveMQSessionCreationException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.security.SecurityTest;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ResourceLimitTestWithCerts extends ActiveMQTestBase {

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

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ResourceLimitSettings limit = new ResourceLimitSettings();
      limit.setMaxConnections(1);
      limit.setMaxQueues(1);
      limit.setMatch(SimpleString.of("first"));

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("CertLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true).addResourceLimitSettings(limit), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      server.start();
   }

   @Test
   public void testSessionLimitForUser() throws Exception {
      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory cf = createSessionFactory(locator);

      ClientSession clientSession = cf.createSession();

      try {
         ClientSessionFactory extraClientSessionFactory = locator.createSessionFactory();
         ClientSession extraClientSession = extraClientSessionFactory.createSession();
         fail("creating a session factory here should fail");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSessionCreationException.class, e);
      }

      clientSession.close();

      clientSession = cf.createSession();

      try {
         ClientSessionFactory extraClientSessionFactory = locator.createSessionFactory();
         ClientSession extraClientSession = extraClientSessionFactory.createSession();
         fail("creating a session factory here should fail");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSessionCreationException.class, e);
      }
      clientSession.close();
      cf.close();
   }

   @Test
   public void testQueueLimitForUser() throws Exception {
      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory cf = createSessionFactory(locator);

      ClientSession clientSession = cf.createSession();
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
