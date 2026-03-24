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
package org.apache.activemq.artemis.tests.integration.management;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.security.SecurityTest;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.CONSUMER_CREATED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.SECURITY_AUTHENTICATION_VIOLATION;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class SSLSecurityNotificationTest extends ActiveMQTestBase {

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

   private ActiveMQServer server;

   private ClientSession adminSession;

   private ClientConsumer notifConsumer;

   private SimpleString notifQueue;

   @Test
   public void testSECURITY_AUTHENTICATION_VIOLATION() throws Exception {
      SSLSecurityNotificationTest.flush(notifConsumer);

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "unknown-client-keystore.jks");
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      long start = System.currentTimeMillis();
      try {
         sf.createSession();
         Assert.fail("authentication must fail and a notification of security violation must be sent");
      } catch (Exception e) {
      }

      ClientMessage[] notifications = SSLSecurityNotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(SECURITY_AUTHENTICATION_VIOLATION.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(null, notifications[0].getObjectProperty(ManagementHelper.HDR_USER));
      Assert.assertEquals("CN=ActiveMQ Artemis Unknown Client, OU=Artemis, O=ActiveMQ, L=AMQ, ST=AMQ, C=AMQ", notifications[0].getObjectProperty(ManagementHelper.HDR_CERT_SUBJECT_DN).toString());
      Assert.assertTrue(notifications[0].getObjectProperty(ManagementHelper.HDR_REMOTE_ADDRESS).toString().startsWith("/127.0.0.1"));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));
   }

   @Test
   public void testCONSUMER_CREATED() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      Role role = new Role("notif", true, true, true, true, false, true, true, true, true, true);
      Set<Role> roles = new HashSet<>();
      roles.add(role);

      server.getSecurityRepository().addMatch("#", roles);

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession guestSession = sf.createSession("guest", "guest", false, true, true, false, 1);

      guestSession.createQueue(new QueueConfiguration(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      SSLSecurityNotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      guestSession.createConsumer(queue);

      ClientMessage[] notifications = SecurityNotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(CONSUMER_CREATED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals("guest", notifications[0].getObjectProperty(ManagementHelper.HDR_USER).toString());
      Assert.assertEquals("first", notifications[0].getObjectProperty(ManagementHelper.HDR_VALIDATED_USER).toString());
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertEquals("CN=ActiveMQ Artemis Client, OU=Artemis, O=ActiveMQ, L=AMQ, ST=AMQ, C=AMQ", notifications[0].getObjectProperty(ManagementHelper.HDR_CERT_SUBJECT_DN).toString());
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      guestSession.close();
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("CertLogin");
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      server.start();

      notifQueue = RandomUtil.randomSimpleString();

      Role role = new Role("notif", true, true, true, true, true, true, true, true, true, true);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress().toString(), roles);

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));
      adminSession = sf.createSession(true, true, 1);
      adminSession.start();

      adminSession.createQueue(new QueueConfiguration(notifQueue).setAddress(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress()).setDurable(false).setTemporary(true));

      notifConsumer = adminSession.createConsumer(notifQueue);
   }


   private static void flush(final ClientConsumer notifConsumer) throws ActiveMQException {
      ClientMessage message = null;
      do {
         message = notifConsumer.receive(500);
      }
      while (message != null);
   }

   protected static ClientMessage[] consumeMessages(final int expected,
                                                    final ClientConsumer consumer) throws Exception {
      ClientMessage[] messages = new ClientMessage[expected];

      ClientMessage m = null;
      for (int i = 0; i < expected; i++) {
         m = consumer.receive(500);
         Assert.assertNotNull("expected to received " + expected + " messages, got only " + i, m);
         messages[i] = m;
         m.acknowledge();
      }
      m = consumer.receiveImmediate();
      Assert.assertNull("received one more message than expected (" + expected + ")", m);

      return messages;
   }


}
