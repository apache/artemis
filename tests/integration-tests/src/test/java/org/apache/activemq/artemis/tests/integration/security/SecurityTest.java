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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.impl.SecurityStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager2;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager3;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager4;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.NoCacheLoginException;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class SecurityTest extends ActiveMQTestBase {

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

   // create session tests
   private static final String addressA = "addressA";

   private static final String queueA = "queueA";

   private ServerLocator locator;

   private Configuration configuration;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = createInVMNonHALocator();
   }

   @Test
   public void testJAASSecurityManagerAuthentication() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
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

   @Test
   public void testNoCacheException() throws Exception {
      ActiveMQSecurityManager5 securityManager = new ActiveMQSecurityManager5() {
         boolean flipper = false;

         @Override
         public Subject authenticate(String user,
                                     String password,
                                     RemotingConnection remotingConnection,
                                     String securityDomain) throws NoCacheLoginException {
            flipper = !flipper;
            if (flipper) {
               Subject validatedSubject = new Subject();
               validatedSubject.getPrincipals().add(new UserPrincipal(user));
               return validatedSubject;
            } else {
               throw new NoCacheLoginException();
            }
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false;
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      cf.createSession("first", "secret", false, true, true, false, 0).close();
      assertEquals(1, ((SecurityStoreImpl)server.getSecurityStore()).getAuthenticationCacheSize());
      try {
         cf.createSession("first", "secret", false, true, true, false, 0);
      } catch (ActiveMQException e) {
         // expected
      }
      assertEquals(1, ((SecurityStoreImpl)server.getSecurityStore()).getAuthenticationCacheSize());
   }

   @Test
   public void testNoCacheNamingException() throws Exception {
      internalTestNoCacheException("BrokenLDAPLoginNamingException");
   }

   @Test
   public void testNoCacheNamingExceptionRegex() throws Exception {
      internalTestNoCacheException("BrokenLDAPLoginNamingExceptionRegex");
   }

   @Test
   public void testNoCacheConnectException() throws Exception {
      internalTestNoCacheException("BrokenLDAPLoginConnectException");
   }

   @Test
   public void testNoCacheConnectExceptionRegex() throws Exception {
      internalTestNoCacheException("BrokenLDAPLoginConnectExceptionRegex");
   }

   private void internalTestNoCacheException(String ldapConfigName) throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(ldapConfigName);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         cf.createSession("first", "secret", false, true, true, false, 0);
      } catch (ActiveMQException e) {
         // expected
      }
      assertEquals(0, ((SecurityStoreImpl)server.getSecurityStore()).getAuthenticationCacheSize());
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithPasswordCodec() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLoginWithPasswordCodec");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession("test", "password", false, true, true, false, 0);
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithValidateUser() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.getConfiguration().setPopulateValidatedUser(true);
      server.start();
      Role role = new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch("#", roles);
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession("first", "secret", false, true, true, false, 0);
         server.createQueue(QueueConfiguration.of("queue").setAddress("address").setRoutingType(RoutingType.ANYCAST));
         ClientProducer producer = session.createProducer("address");
         producer.send(session.createMessage(true));
         session.commit();
         producer.close();
         ClientConsumer consumer = session.createConsumer("queue");
         session.start();
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         assertEquals("first", message.getValidatedUserID());
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithCerts() throws Exception {
      testJAASSecurityManagerAuthenticationWithCerts("CertLogin", TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithCertsWantClientAuth() throws Exception {
      testJAASSecurityManagerAuthenticationWithCerts("CertLogin", TransportConstants.WANT_CLIENT_AUTH_PROP_NAME, "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithRegexps() throws Exception {
      testJAASSecurityManagerAuthenticationWithCerts("CertLoginWithRegexp", TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithRegexpsWantClientAuth() throws Exception {
      testJAASSecurityManagerAuthenticationWithCerts("CertLoginWithRegexp", TransportConstants.WANT_CLIENT_AUTH_PROP_NAME, "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithUpnCerts() throws Exception {
      testJAASSecurityManagerAuthenticationWithCerts("UpnCertLogin", TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "upn-client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithUpnCertsWantClientAuth() throws Exception {
      testJAASSecurityManagerAuthenticationWithCerts("UpnCertLogin", TransportConstants.WANT_CLIENT_AUTH_PROP_NAME, "upn-client-keystore.jks");
   }

   protected void testJAASSecurityManagerAuthenticationWithCerts(String secManager, String clientAuthPropName, String keystore) throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(secManager);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(clientAuthPropName, true);

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      server.start();

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keystore);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession();
         session.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   @Test
   public void testJAASSecurityManagerAuthenticationWithCertsAndOpenWire() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("CertLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      server.getConfiguration().setResolveProtocols(true).addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      // ensure advisory permission is still set for openwire to allow connection to succeed, alternative is url param jms.watchTopicAdvisories=false on the client connection factory
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, true, false, false, true, true, false, false, true, false, false, false));
      server.getConfiguration().putSecurityRoles("ActiveMQ.Advisory.#", roles);

      server.start();

      ActiveMQSslConnectionFactory factory = new ActiveMQSslConnectionFactory("ssl://localhost:61616?verifyHostName=false");
      factory.setTrustStore("server-ca-truststore.jks");
      factory.setTrustStorePassword("securepass");
      factory.setKeyStore("client-keystore.jks");
      factory.setKeyStorePassword("securepass");

      try (ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         session.close();
      } catch (Throwable e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   /**
    * Verify role permissions are applied properly when using OpenWire
    */
   @Test
   public void testJAASSecurityManagerOpenWireNegative() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("CertLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, false, false, false, false, false, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      server.getConfiguration().setResolveProtocols(true).addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));
      server.start();

      ActiveMQSslConnectionFactory factory = new ActiveMQSslConnectionFactory("ssl://localhost:61616?verifyHostName=false");
     // factory.setUserName("test-user");
      factory.setTrustStore("server-ca-truststore.jks");
      factory.setTrustStorePassword("securepass");
      factory.setKeyStore("client-keystore.jks");
      factory.setKeyStorePassword("securepass");
      factory.setWatchTopicAdvisories(false);

      try (ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         //Add the corresponding addresses to the server
         SimpleString address = SimpleString.of("test.queue");
         server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
         SimpleString address2 = SimpleString.of("test.topic");
         server.addAddressInfo(new AddressInfo(address2, RoutingType.MULTICAST));

         //Test queue creation permission
         try {
            session.createConsumer(session.createQueue("test.queue"));
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='CREATE_DURABLE_QUEUE' for queue test.queue on address test.queue"));
         }

         //Test non durable create permission
         try {
            session.createConsumer(session.createTopic("test.topic"));
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='CREATE_NON_DURABLE_QUEUE'"));
         }

         //Add a test queue to the server
         server.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));

         //Test queue produce permission
         try {
            MessageProducer producer = session.createProducer(session.createQueue("test.queue"));
            producer.send(session.createMessage());
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='SEND'"));
         }

         //Test queue consume permission
         try {
            session.createConsumer(session.createQueue("test.queue"));
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='CONSUME' for queue test.queue on address test.queue"));
         }

         //Test queue browse permission
         try {
            QueueBrowser browser = session.createBrowser(session.createQueue("test.queue"));
            browser.getEnumeration();
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='BROWSE' for queue test.queue on address test.queue"));
         }

         //Test queue deletion permission
         try {
            connection.destroyDestination(new ActiveMQQueue("test.queue"));
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='DELETE_DURABLE_QUEUE' for queue test.queue on address test.queue"));
         }

         //Test temp queue
         try {
            session.createTemporaryQueue();
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='CREATE_ADDRESS'"));
         }

         //Test temp topic
         try {
            session.createTemporaryTopic();
            fail("should throw exception here");
         } catch (Exception e) {
            assertTrue(e.getMessage().contains("User: first"));
            assertTrue(e.getMessage().contains("does not have permission='CREATE_ADDRESS'"));
         }

         session.close();
      }
   }

   @Test
   public void testJAASSecurityManagerAuthenticationBadPassword() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         cf.createSession("first", "badpassword", false, true, true, false, 0);
         fail("should throw exception here");
      } catch (Exception e) {
         // ignore
      }
   }

   /**
    * This test requires a client-side certificate that will be trusted by the server but whose DN will be rejected
    * by the {@code TextFileCertificateLoginModule} login module.
    */
   @Test
   public void testJAASSecurityManagerAuthenticationWithBadDnClientCert() throws Exception {
      testJAASSecurityManagerAuthenticationWithBadClientCert("CertLogin", "unknown-client-keystore.jks");
   }


   /**
    * This test requires a client-side certificate that will be trusted by the server but whose UPN will be rejected
    * by the {@code TextFileUpnCertificateLoginModule} login module.
    */
   @Test
   public void testJAASSecurityManagerAuthenticationWithBadUpnClientCert() throws Exception {
      testJAASSecurityManagerAuthenticationWithBadClientCert("UpnCertLogin", "unknown-upn-client-keystore.jks");

   }

   private void testJAASSecurityManagerAuthenticationWithBadClientCert(String configName, String keystore) throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(configName);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      server.start();

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keystore);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         cf.createSession();
         fail("Creating session here should fail due to authentication error.");
      } catch (ActiveMQException e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }
   }

   @Test
   public void testJAASSecurityManagerAuthenticationGuest() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("GuestLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
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

   @Test
   public void testJAASSecurityManagerAuthorizationNegative() throws Exception {
      internalTestJAASSecurityManagerAuthorizationNegative(false);
   }

   @Test
   public void testJAASSecurityManagerAuthorizationNegativeWithUuidNamespace() throws Exception {
      internalTestJAASSecurityManagerAuthorizationNegative(true);
   }

   private void internalTestJAASSecurityManagerAuthorizationNegative(boolean useUuidNamespace) throws Exception {
      final SimpleString ADDRESS = useUuidNamespace ? SimpleString.of(RandomUtil.randomUUIDString()) : SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, false, false, false, false, false, false, false, false));
      String match = "#";
      if (useUuidNamespace) {
         final SimpleString UUID_NAMESPACE = RandomUtil.randomUUIDSimpleString();
         server.getConfiguration().setUuidNamespace(UUID_NAMESPACE.toString());
         match = UUID_NAMESPACE + ".#";
      }
      server.getConfiguration().putSecurityRoles(match, roles);
      server.start();
      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("first", "secret", false, true, true, false, 0));

      // CREATE_DURABLE_QUEUE
      if (!useUuidNamespace) {
         try {
            session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("User: first does not have permission='CREATE_DURABLE_QUEUE' for queue durableQueue on address " + ADDRESS));
         }
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='DELETE_DURABLE_QUEUE' for queue durableQueue on address " + ADDRESS));
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CREATE_NON_DURABLE_QUEUE' for queue nonDurableQueue on address " + ADDRESS));
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='DELETE_NON_DURABLE_QUEUE' for queue nonDurableQueue on address " + ADDRESS));
      }

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='SEND' on address " + ADDRESS));
      }

      // CONSUME
      try {
         ClientConsumer consumer = session.createConsumer(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first does not have permission='CONSUME' for queue durableQueue on address " + ADDRESS));
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
         assertTrue(e.getMessage().contains("User: first does not have permission='BROWSE' for queue durableQueue on address " + ADDRESS));
      }
   }

   @Test
   public void testJAASSecurityManagerCreateQueueWithDifferentRoutingTypeAsAddressNegative() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");
      final SimpleString JMS = SimpleString.of("jms");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, true, false, true, false, false, false, false, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();
      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(JMS, RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("first", "secret", false, true, true, false, 0));

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");

      // Explicit attempt to modify address routing type with a durable queue
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.MULTICAST));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first"));
         assertTrue(e.getMessage().contains("does not have permission='CREATE_ADDRESS' for queue durableQueue on address address"));
      }

      // Implicit attempt to modify address routing type with a durable queue using auto-create via JMS
      try (Connection c = connectionFactory.createConnection("first", "secret")) {
         c.setClientID("myClientID");
         Session s = c.createSession();
         s.createDurableSubscriber(s.createTopic(JMS.toString()), "foo");
         fail("should throw exception here");
      } catch (JMSException e) {
         assertTrue(e.getMessage().contains("User: first"));
         assertTrue(e.getMessage().contains("does not have permission='CREATE_ADDRESS' for queue myClientID.foo on address jms"));
      }

      // Explicit attempt to modify address routing type with a non-durable queue
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false).setRoutingType(RoutingType.MULTICAST));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("User: first"));
         assertTrue(e.getMessage().contains("does not have permission='CREATE_ADDRESS' for queue nonDurableQueue on address address"));
      }

      // Implicit attempt to modify address routing type with a non-durable queue using auto-create via JMS
      try (Connection c = connectionFactory.createConnection("first", "secret")) {
         Session s = c.createSession();
         s.createConsumer(s.createTopic(JMS.toString()));
         fail("should throw exception here");
      } catch (JMSException e) {
         assertTrue(e.getMessage().contains("User: first"));
         assertTrue(e.getMessage().contains("does not have permission='CREATE_ADDRESS'"));
      }
   }

   @Test
   public void testJAASSecurityManagerCreateQueueWithDifferentRoutingTypeAsAddress() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");
      final SimpleString JMS = SimpleString.of("jms");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, true, true, false, true, false, false, false, true, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();
      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("first", "secret", false, true, true, false, 0));

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");

      // Explicit attempt to modify address routing type with a durable queue
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.MULTICAST));
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // Implicit attempt to modify address routing type with a durable queue using auto-create via JMS
      try (Connection c = connectionFactory.createConnection("first", "secret")) {
         c.setClientID("myClientID");
         Session s = c.createSession();
         s.createDurableSubscriber(s.createTopic(JMS.toString()), "foo");
      } catch (JMSException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // Explicit attempt to modify address routing type with a non-durable queue
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false).setRoutingType(RoutingType.MULTICAST));
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // Implicit attempt to modify address routing type with a non-durable queue using auto-create via JMS
      try (Connection c = connectionFactory.createConnection("first", "secret")) {
         Session s = c.createSession();
         s.createConsumer(s.createTopic(JMS.toString()));
      } catch (JMSException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }
   }

   @Test
   // this is for backwards compatibility with the pre-FQQN syntax from ARTEMIS-592
   public void testJAASSecurityManagerAuthorizationSameAddressDifferentQueuesDotSyntax() throws Exception {
      internalJAASSecurityManagerAuthorizationSameAddressDifferentQueues(false);
   }

   @Test
   public void testJAASSecurityManagerAuthorizationSameAddressDifferentQueuesFqqnSyntax() throws Exception {
      internalJAASSecurityManagerAuthorizationSameAddressDifferentQueues(true);
   }

   private void internalJAASSecurityManagerAuthorizationSameAddressDifferentQueues(boolean fqqnSyntax) throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString QUEUE_A = SimpleString.of("a");
      final SimpleString QUEUE_B = SimpleString.of("b");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      Set<Role> aRoles = new HashSet<>();
      aRoles.add(new Role(QUEUE_A.toString(), false, true, false, false, false, false, false, false, false, false, false, false));
      if (fqqnSyntax) {
         server.getConfiguration().putSecurityRoles(CompositeAddress.toFullyQualified(ADDRESS, QUEUE_A).toString(), aRoles);
      } else {
         server.getConfiguration().putSecurityRoles(ADDRESS.concat(".").concat(QUEUE_A).toString(), aRoles);
      }
      Set<Role> bRoles = new HashSet<>();
      bRoles.add(new Role(QUEUE_B.toString(), false, true, false, false, false, false, false, false, false, false, false, false));
      if (fqqnSyntax) {
         server.getConfiguration().putSecurityRoles(CompositeAddress.toFullyQualified(ADDRESS, QUEUE_B).toString(), bRoles);
      } else {
         server.getConfiguration().putSecurityRoles(ADDRESS.concat(".").concat(QUEUE_B).toString(), bRoles);
      }
      server.start();
      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(QUEUE_A).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(QUEUE_B).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession aSession = addClientSession(cf.createSession("a", "a", false, true, true, false, 0));
      ClientSession bSession = addClientSession(cf.createSession("b", "b", false, true, true, false, 0));

      // client A CONSUME from queue A
      try {
         ClientConsumer consumer = aSession.createConsumer(QUEUE_A);
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // client B CONSUME from queue A
      try {
         ClientConsumer consumer = bSession.createConsumer(QUEUE_A);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }

      // client B CONSUME from queue B
      try {
         ClientConsumer consumer = bSession.createConsumer(QUEUE_B);
      } catch (ActiveMQException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // client A CONSUME from queue B
      try {
         ClientConsumer consumer = aSession.createConsumer(QUEUE_B);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }
   }

   @Test
   public void testFallbackConsumerAuthorization() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("a.c.b");
      final SimpleString QUEUE = SimpleString.of("a.c.b");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Set<Role> aRoles = new HashSet<>();
      aRoles.add(new Role("xyz", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("a.*.b", aRoles);

      Set<Role> bRoles = new HashSet<>();
      bRoles.add(new Role("amq", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", bRoles);

      server.start();
      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("x", "x", false, true, true, false, 0));

      try {
         session.createConsumer(QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         assertInstanceOf(ActiveMQSecurityException.class, e);
      }
   }

   @Test
   public void testJAASSecurityManagerFQQNAuthorizationWithJMS() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString QUEUE_A = SimpleString.of("a");
      final SimpleString QUEUE_B = SimpleString.of("b");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Set<Role> aRoles = new HashSet<>();
      aRoles.add(new Role(QUEUE_A.toString(), false, true, true, false, false, false, false, false, true, false, false, false));
      server.getConfiguration().putSecurityRoles(CompositeAddress.toFullyQualified(ADDRESS, QUEUE_A).toString(), aRoles);

      Set<Role> bRoles = new HashSet<>();
      bRoles.add(new Role(QUEUE_B.toString(), false, true, true, false, false, false, false, false, true, false, false, false));
      server.getConfiguration().putSecurityRoles(CompositeAddress.toFullyQualified(ADDRESS, QUEUE_B).toString(), bRoles);

      server.start();

      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      Connection aConnection = cf.createConnection("a", "a");
      Session aSession = aConnection.createSession();
      Connection bConnection = cf.createConnection("b", "b");
      Session bSession = bConnection.createSession();

      javax.jms.Queue queueA = aSession.createQueue(CompositeAddress.toFullyQualified(ADDRESS, QUEUE_A).toString());
      javax.jms.Queue queueB = bSession.createQueue(CompositeAddress.toFullyQualified(ADDRESS, QUEUE_B).toString());

      // client A CONSUME from queue A
      try {
         aSession.createConsumer(queueA);
      } catch (JMSException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // client B CONSUME from queue A
      try {
         bSession.createConsumer(queueA);
         fail("should throw exception here");
      } catch (JMSException e) {
         assertInstanceOf(JMSSecurityException.class, e);
      }

      // client B CONSUME from queue B
      try {
         bSession.createConsumer(queueB);
      } catch (JMSException e) {
         e.printStackTrace();
         fail("should not throw exception here");
      }

      // client A CONSUME from queue B
      try {
         aSession.createConsumer(queueB);
         fail("should throw exception here");
      } catch (JMSException e) {
         assertInstanceOf(JMSSecurityException.class, e);
      }

      aConnection.close();
      bConnection.close();
   }

   @Test
   public void testJAASSecurityManagerAuthorizationNegativeWithDnCerts() throws Exception {
      testJAASSecurityManagerAuthorizationNegativeWithCerts("CertLogin", "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthorizationNegativeWithUpnCerts() throws Exception {
      testJAASSecurityManagerAuthorizationNegativeWithCerts("UpnCertLogin", "upn-client-keystore.jks");
   }

   private void testJAASSecurityManagerAuthorizationNegativeWithCerts(String configName, String keystore) throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(configName);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, false, false, false, false, false, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      server.start();

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keystore);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory cf = createSessionFactory(locator);

      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      ClientSession session = addClientSession(cf.createSession());

      // CREATE_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // DELETE_DURABLE_QUEUE
      try {
         session.deleteQueue(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // CREATE_NON_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // DELETE_NON_DURABLE_QUEUE
      try {
         session.deleteQueue(NON_DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // PRODUCE
      try {
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // CONSUME
      try {
         ClientConsumer consumer = session.createConsumer(DURABLE_QUEUE);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // MANAGE
      try {
         ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
         producer.send(session.createMessage(true));
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }

      // BROWSE
      try {
         ClientConsumer browser = session.createConsumer(DURABLE_QUEUE, true);
         fail("should throw exception here");
      } catch (ActiveMQException e) {
         // ignore
      }
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositive() throws Exception {
      internalTestJAASSecurityManagerAuthorizationPositive(false);
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositiveWithUuidNamespace() throws Exception {
      internalTestJAASSecurityManagerAuthorizationPositive(true);
   }

   private void internalTestJAASSecurityManagerAuthorizationPositive(boolean useUuidNamespace) throws Exception {
      final SimpleString ADDRESS = useUuidNamespace ? SimpleString.of(RandomUtil.randomUUIDString()) : SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false));
      String match = "#";
      if (useUuidNamespace) {
         final SimpleString UUID_NAMESPACE = RandomUtil.randomUUIDSimpleString();
         server.getConfiguration().setUuidNamespace(UUID_NAMESPACE.toString());
         match = UUID_NAMESPACE + ".#";
      }
      server.getConfiguration().putSecurityRoles(match, roles);
      server.start();

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("first", "secret", false, true, true, false, 0));

      // CREATE_DURABLE_QUEUE
      if (!useUuidNamespace) {
         try {
            session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
         } catch (ActiveMQException e) {
            fail("should not throw exception here");
         }
      }

      // DELETE_DURABLE_QUEUE
      if (!useUuidNamespace) {
         try {
            session.deleteQueue(DURABLE_QUEUE);
         } catch (ActiveMQException e) {
            fail("should not throw exception here");
         }
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
      if (!useUuidNamespace) {
         try {
            ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
            producer.send(session.createMessage(true));
         } catch (ActiveMQException e) {
            fail("should not throw exception here");
         }
      }

      // BROWSE
      try {
         session.createConsumer(DURABLE_QUEUE, true);
      } catch (ActiveMQException e) {
         fail("should not throw exception here");
      }
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositiveWithDnCerts() throws Exception {
      testJAASSecurityManagerAuthorizationPositiveWithCerts(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "CertLogin", "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositiveWithDnCertsWantClientAuth() throws Exception {
      testJAASSecurityManagerAuthorizationPositiveWithCerts(TransportConstants.WANT_CLIENT_AUTH_PROP_NAME, "CertLogin", "client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositiveWithUpnCerts() throws Exception {
      testJAASSecurityManagerAuthorizationPositiveWithCerts(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "UpnCertLogin", "upn-client-keystore.jks");
   }

   @Test
   public void testJAASSecurityManagerAuthorizationPositiveWithUpnCertsWantClientAuth() throws Exception {
      testJAASSecurityManagerAuthorizationPositiveWithCerts(TransportConstants.WANT_CLIENT_AUTH_PROP_NAME, "UpnCertLogin", "upn-client-keystore.jks");
   }

   protected void testJAASSecurityManagerAuthorizationPositiveWithCerts(String clientAuthPropName, String configName, String keystore) throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(configName);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(clientAuthPropName, true);

      server.getConfiguration().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();

      TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keystore);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession());

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

   @Test
   public void testJAASSecurityManagerAuthorizationPositiveGuest() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("GuestLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("bar", true, true, true, true, true, true, true, false, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);
      server.start();

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession("junk", "junk", false, true, true, false, 0));

      // CREATE_DURABLE_QUEUE
      try {
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
      } catch (ActiveMQException e) {
         e.printStackTrace();
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
   }

   @Test
   public void testCreateSessionWithNullUserPass() throws Exception {
      ActiveMQServer server = createServer();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession(false, true, true);

         session.close();
      } catch (ActiveMQException e) {
         fail("should not throw exception");
      }
   }

   private ActiveMQServer createServer() throws Exception {
      configuration = createDefaultInVMConfig().setSecurityEnabled(true);
      ActiveMQServer server = createServer(false, configuration);
      return server;
   }

   @Test
   public void testCreateSessionWithNullUserPassNoGuest() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);
      try {
         cf.createSession(false, true, true);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testCreateSessionWithCorrectUserWrongPass() throws Exception {
      ActiveMQServer server = createServer();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("newuser", "apass");
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         cf.createSession("newuser", "awrongpass", false, true, true, false, -1);
         fail("should not throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testCreateSessionWithCorrectUserCorrectPass() throws Exception {
      ActiveMQServer server = createServer();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("newuser", "apass");
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);

      try {
         ClientSession session = cf.createSession("newuser", "apass", false, true, true, false, -1);

         session.close();
      } catch (ActiveMQException e) {
         fail("should not throw exception");
      }
   }

   @Test
   public void testCreateDurableQueueWithRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      session.close();
   }

   @Test
   public void testCreateDurableQueueWithoutRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      try {
         session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testDeleteDurableQueueWithRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, true, false, false, false, false, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      session.deleteQueue(SecurityTest.queueA);
      session.close();
   }

   @Test
   public void testDeleteDurableQueueWithoutRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      try {
         session.deleteQueue(SecurityTest.queueA);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testCreateUuidWithRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, false, false, true, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA).setDurable(false));
      session.close();
   }

   @Test
   public void testCreateUuidWithoutRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      try {
         session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA).setDurable(false));
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testDeleteUuidWithRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, false, false, true, true, false, false, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA).setDurable(false));
      session.deleteQueue(SecurityTest.queueA);
      session.close();
   }

   @Test
   public void testDeleteUuidWithoutRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, false, false, true, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA).setDurable(false));
      try {
         session.deleteQueue(SecurityTest.queueA);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testSendWithRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();

      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();

      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      securityManager.getConfiguration().addUser("auser", "pass");

      Role role = new Role("arole", true, true, true, false, false, false, false, false, true, false, false, false);

      Set<Role> roles = new HashSet<>();

      roles.add(role);

      securityRepository.addMatch(SecurityTest.addressA, roles);

      securityManager.getConfiguration().addRole("auser", "arole");

      locator.setBlockOnNonDurableSend(true);

      ClientSessionFactory cf = createSessionFactory(locator);

      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);

      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));

      ClientProducer cp = session.createProducer(SecurityTest.addressA);

      cp.send(session.createMessage(false));

      session.start();

      ClientConsumer cons = session.createConsumer(queueA);

      ClientMessage receivedMessage = cons.receive(5000);

      assertNotNull(receivedMessage);

      receivedMessage.acknowledge();

      role = new Role("arole", false, false, true, false, false, false, false, false, false, false, false, false);

      roles = new HashSet<>();

      roles.add(role);

      securityRepository.addMatch(SecurityTest.addressA, roles);
      boolean failed = false;
      try {
         cp.send(session.createMessage(true));
      } catch (ActiveMQException e) {
         failed = true;
      }

      assertTrue(failed, "Failure expected on send after removing the match");
   }

   @Test
   public void testSendWithoutRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      locator.setBlockOnNonDurableSend(true);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = session.createProducer(SecurityTest.addressA);
      try {
         cp.send(session.createMessage(false));
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testNonBlockSendWithoutRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = session.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));
      session.close();

      Queue binding = (Queue) server.getPostOffice().getBinding(SimpleString.of(SecurityTest.queueA)).getBindable();
      assertEquals(0, getMessageCount(binding));
   }

   @Test
   public void testCreateConsumerWithRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      Role role = new Role("arole", false, true, false, false, false, false, false, false, false, false, false, false);
      Role sendRole = new Role("guest", true, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(sendRole);
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession senSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      senSession.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = senSession.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));
      session.createConsumer(SecurityTest.queueA);
      session.close();
      senSession.close();
   }

   @Test
   public void testCreateConsumerWithoutRole() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      Role role = new Role("arole", false, false, false, false, false, false, false, false, false, false, false, false);
      Role sendRole = new Role("guest", true, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(sendRole);
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession senSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      senSession.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = senSession.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));
      try {
         session.createConsumer(SecurityTest.queueA);
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
      senSession.close();
   }

   @Test
   public void testSendMessageUpdateRoleCached() throws Exception {
      Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true).setSecurityInvalidationInterval(10000);
      ActiveMQServer server = createServer(false, configuration);
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      Role role = new Role("arole", false, false, false, false, false, false, false, false, false, false, false, false);
      Role sendRole = new Role("guest", true, false, true, false, false, false, false, false, true, false, false, false);
      Role receiveRole = new Role("receiver", false, true, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(sendRole);
      roles.add(role);
      roles.add(receiveRole);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession senSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      senSession.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = senSession.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));
      try {
         session.createConsumer(SecurityTest.queueA);
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      securityManager.getConfiguration().addRole("auser", "receiver");

      // invalidate the authentication cache so the new role will be picked up
      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthenticationCache();

      session.createConsumer(SecurityTest.queueA);

      // Removing the Role... the check should be cached, so the next createConsumer shouldn't fail
      securityManager.getConfiguration().removeRole("auser", "receiver");

      session.createConsumer(SecurityTest.queueA);

      session.close();

      senSession.close();
   }

   @Test
   public void testSendMessageUpdateRoleCached2() throws Exception {
      Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true).setSecurityInvalidationInterval(0);
      ActiveMQServer server = createServer(false, configuration);

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      Role role = new Role("arole", false, false, false, false, false, false, false, false, false, false, false, false);
      Role sendRole = new Role("guest", true, false, true, false, false, false, false, false, true, false, false, false);
      Role receiveRole = new Role("receiver", false, true, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(sendRole);
      roles.add(role);
      roles.add(receiveRole);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession senSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      senSession.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = senSession.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));
      try {
         session.createConsumer(SecurityTest.queueA);
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      securityManager.getConfiguration().addRole("auser", "receiver");

      session.createConsumer(SecurityTest.queueA);

      // Removing the Role... the check should be cached... but we used
      // setSecurityInvalidationInterval(0), so the
      // next createConsumer should fail
      securityManager.getConfiguration().removeRole("auser", "receiver");

      try {
         session.createConsumer(SecurityTest.queueA);
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      session.close();

      senSession.close();
   }

   @Test
   public void testSendMessageUpdateSender() throws Exception {
      Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true).setSecurityInvalidationInterval(1000);
      ActiveMQServer server = createServer(false, configuration);
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      Role role = new Role("arole", false, false, false, false, false, false, false, false, false, false, false, false);
      Role sendRole = new Role("guest", true, false, true, false, false, false, false, false, true, false, false, false);
      Role receiveRole = new Role("receiver", false, true, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(sendRole);
      roles.add(role);
      roles.add(receiveRole);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);

      ClientSession senSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      senSession.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));
      ClientProducer cp = senSession.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));
      try {
         session.createConsumer(SecurityTest.queueA);
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      securityManager.getConfiguration().addRole("auser", "receiver");

      Wait.assertTrue(() -> {
         try {
            session.createConsumer(SecurityTest.queueA);
            return true;
         } catch (Exception e) {
            return false;
         }
      }, 2000, 100);

      // Removing the Role... the check should be cached... but we used
      // setSecurityInvalidationInterval(0), so the
      // next createConsumer should fail
      securityManager.getConfiguration().removeRole("auser", "guest");

      ClientSession sendingSession = cf.createSession("auser", "pass", false, false, false, false, 0);
      ClientProducer prod = sendingSession.createProducer(SecurityTest.addressA);
      prod.send(createClientMessage(sendingSession, "Test", true));
      prod.send(createClientMessage(sendingSession, "Test", true));
      try {
         sendingSession.commit();
         fail("Expected exception");
      } catch (ActiveMQException e) {
         // I would expect the commit to fail, since there were failures registered
      }

      sendingSession.close();

      Xid xid = newXID();

      sendingSession = cf.createSession("auser", "pass", true, false, false, false, 0);
      sendingSession.start(xid, XAResource.TMNOFLAGS);

      prod = sendingSession.createProducer(SecurityTest.addressA);
      prod.send(createClientMessage(sendingSession, "Test", true));
      prod.send(createClientMessage(sendingSession, "Test", true));
      sendingSession.end(xid, XAResource.TMSUCCESS);

      try {
         sendingSession.prepare(xid);
         fail("Exception was expected");
      } catch (Exception e) {
         e.printStackTrace();
      }

      // A prepare shouldn't mark any recoverable resources
      Xid[] xids = sendingSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(0, xids.length);

      session.close();

      senSession.close();

      sendingSession.close();
   }

   private static ClientMessage createClientMessage(final ClientSession session, final String s, final boolean durable) {
      ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, durable, 0, System.currentTimeMillis(), (byte) 1);
      message.getBodyBuffer().writeString(s);
      return message;
   }

   @Test
   public void testSendManagementWithRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, false, false, false, false, true, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(configuration.getManagementAddress().toString(), roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      locator.setBlockOnNonDurableSend(true);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      ClientProducer cp = session.createProducer(configuration.getManagementAddress());
      cp.send(session.createMessage(false));
      session.close();
   }

   @Test
   public void testSendManagementWithoutRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(configuration.getManagementAddress().toString(), roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(configuration.getManagementAddress().toString()));
      ClientProducer cp = session.createProducer(configuration.getManagementAddress());
      cp.send(session.createMessage(false));
      try {
         cp.send(session.createMessage(false));
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();

   }

   @Test
   public void testNonBlockSendManagementWithoutRole() throws Exception {
      ActiveMQServer server = createServer();

      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", false, false, true, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(configuration.getManagementAddress().toString(), roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      session.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(configuration.getManagementAddress().toString()));
      ClientProducer cp = session.createProducer(configuration.getManagementAddress());
      cp.send(session.createMessage(false));
      session.close();

      Queue binding = (Queue) server.getPostOffice().getBinding(SimpleString.of(SecurityTest.queueA)).getBindable();
      assertEquals(0, getMessageCount(binding));

   }

   @Test
   public void testComplexRoles() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("all", "all");
      securityManager.getConfiguration().addUser("bill", "activemq");
      securityManager.getConfiguration().addUser("andrew", "activemq1");
      securityManager.getConfiguration().addUser("frank", "activemq2");
      securityManager.getConfiguration().addUser("sam", "activemq3");
      securityManager.getConfiguration().addRole("all", "all");
      securityManager.getConfiguration().addRole("bill", "user");
      securityManager.getConfiguration().addRole("andrew", "europe-user");
      securityManager.getConfiguration().addRole("andrew", "user");
      securityManager.getConfiguration().addRole("frank", "us-user");
      securityManager.getConfiguration().addRole("frank", "news-user");
      securityManager.getConfiguration().addRole("frank", "user");
      securityManager.getConfiguration().addRole("sam", "news-user");
      securityManager.getConfiguration().addRole("sam", "user");
      Role all = new Role("all", true, true, true, true, true, true, true, true, true, true, false, false);
      HierarchicalRepository<Set<Role>> repository = server.getSecurityRepository();
      Set<Role> add = new HashSet<>();
      add.add(new Role("user", true, true, true, true, true, true, false, true, true, true, false, false));
      add.add(all);
      repository.addMatch("#", add);
      Set<Role> add1 = new HashSet<>();
      add1.add(all);
      add1.add(new Role("user", false, false, true, true, true, true, false, true, true, true, false, false));
      add1.add(new Role("europe-user", true, false, false, false, false, false, false, true, true, true, false, false));
      add1.add(new Role("news-user", false, true, false, false, false, false, false, true, true, true, false, false));
      repository.addMatch("news.europe.#", add1);
      Set<Role> add2 = new HashSet<>();
      add2.add(all);
      add2.add(new Role("user", false, false, true, true, true, true, false, true, true, true, false, false));
      add2.add(new Role("us-user", true, false, false, false, false, false, false, true, true, true, false, false));
      add2.add(new Role("news-user", false, true, false, false, false, false, false, true, true, true, false, false));
      repository.addMatch("news.us.#", add2);
      ClientSession billConnection = null;
      ClientSession andrewConnection = null;
      ClientSession frankConnection = null;
      ClientSession samConnection = null;
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      ClientSessionFactory factory = createSessionFactory(locator);

      ClientSession adminSession = factory.createSession("all", "all", false, true, true, false, -1);
      String genericQueueName = "genericQueue";
      adminSession.createQueue(QueueConfiguration.of(genericQueueName).setDurable(false));
      String eurQueueName = "news.europe.europeQueue";
      adminSession.createQueue(QueueConfiguration.of(eurQueueName).setDurable(false));
      String usQueueName = "news.us.usQueue";
      adminSession.createQueue(QueueConfiguration.of(usQueueName).setDurable(false));
      // Step 4. Try to create a JMS Connection without user/password. It will fail.
      try {
         factory.createSession(false, true, true);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Step 5. bill tries to make a connection using wrong password
      try {
         billConnection = factory.createSession("bill", "activemq1", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Step 6. bill makes a good connection.
      billConnection = factory.createSession("bill", "activemq", false, true, true, false, -1);

      // Step 7. andrew makes a good connection.
      andrewConnection = factory.createSession("andrew", "activemq1", false, true, true, false, -1);

      // Step 8. frank makes a good connection.
      frankConnection = factory.createSession("frank", "activemq2", false, true, true, false, -1);

      // Step 9. sam makes a good connection.
      samConnection = factory.createSession("sam", "activemq3", false, true, true, false, -1);

      checkUserSendAndReceive(genericQueueName, billConnection);
      checkUserSendAndReceive(genericQueueName, andrewConnection);
      checkUserSendAndReceive(genericQueueName, frankConnection);
      checkUserSendAndReceive(genericQueueName, samConnection);

      // Step 11. Check permissions on news.europe.europeTopic for bill: can't send and can't
      // receive
      checkUserNoSendNoReceive(eurQueueName, billConnection, adminSession);

      // Step 12. Check permissions on news.europe.europeTopic for andrew: can send but can't
      // receive
      checkUserSendNoReceive(eurQueueName, andrewConnection);

      // Step 13. Check permissions on news.europe.europeTopic for frank: can't send but can
      // receive
      checkUserReceiveNoSend(eurQueueName, frankConnection, adminSession);

      // Step 14. Check permissions on news.europe.europeTopic for sam: can't send but can
      // receive
      checkUserReceiveNoSend(eurQueueName, samConnection, adminSession);

      // Step 15. Check permissions on news.us.usTopic for bill: can't send and can't receive
      checkUserNoSendNoReceive(usQueueName, billConnection, adminSession);

      // Step 16. Check permissions on news.us.usTopic for andrew: can't send and can't receive
      checkUserNoSendNoReceive(usQueueName, andrewConnection, adminSession);

      // Step 17. Check permissions on news.us.usTopic for frank: can both send and receive
      checkUserSendAndReceive(usQueueName, frankConnection);

      // Step 18. Check permissions on news.us.usTopic for same: can't send but can receive
      checkUserReceiveNoSend(usQueueName, samConnection, adminSession);

      billConnection.close();

      andrewConnection.close();

      frankConnection.close();

      samConnection.close();

      adminSession.close();

   }

   @Test
   @Disabled
   public void testComplexRoles2() throws Exception {
      ActiveMQServer server = createServer();
      server.start();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("all", "all");
      securityManager.getConfiguration().addUser("bill", "activemq");
      securityManager.getConfiguration().addUser("andrew", "activemq1");
      securityManager.getConfiguration().addUser("frank", "activemq2");
      securityManager.getConfiguration().addUser("sam", "activemq3");
      securityManager.getConfiguration().addRole("all", "all");
      securityManager.getConfiguration().addRole("bill", "user");
      securityManager.getConfiguration().addRole("andrew", "europe-user");
      securityManager.getConfiguration().addRole("andrew", "user");
      securityManager.getConfiguration().addRole("frank", "us-user");
      securityManager.getConfiguration().addRole("frank", "news-user");
      securityManager.getConfiguration().addRole("frank", "user");
      securityManager.getConfiguration().addRole("sam", "news-user");
      securityManager.getConfiguration().addRole("sam", "user");
      Role all = new Role("all", true, true, true, true, true, true, true, true, true, true, false, false);
      HierarchicalRepository<Set<Role>> repository = server.getSecurityRepository();
      Set<Role> add = new HashSet<>();
      add.add(new Role("user", true, true, true, true, true, true, false, true, true, true, false, false));
      add.add(all);
      repository.addMatch("#", add);
      Set<Role> add1 = new HashSet<>();
      add1.add(all);
      add1.add(new Role("user", false, false, true, true, true, true, false, true, true, true, false, false));
      add1.add(new Role("europe-user", true, false, false, false, false, false, false, true, true, true, false, false));
      add1.add(new Role("news-user", false, true, false, false, false, false, false, true, true, true, false, false));
      repository.addMatch("news.europe.#", add1);
      Set<Role> add2 = new HashSet<>();
      add2.add(all);
      add2.add(new Role("user", false, false, true, true, true, true, false, true, true, true, false, false));
      add2.add(new Role("us-user", true, false, false, false, false, false, false, true, true, true, false, false));
      add2.add(new Role("news-user", false, true, false, false, false, false, false, true, true, true, false, false));
      repository.addMatch("news.us.#", add2);
      ClientSession billConnection = null;
      ClientSession andrewConnection = null;
      ClientSession frankConnection = null;
      ClientSession samConnection = null;
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      ClientSessionFactory factory = createSessionFactory(locator);

      ClientSession adminSession = factory.createSession("all", "all", false, true, true, false, -1);
      String genericQueueName = "genericQueue";
      adminSession.createQueue(QueueConfiguration.of(genericQueueName).setDurable(false));
      String eurQueueName = "news.europe.europeQueue";
      adminSession.createQueue(QueueConfiguration.of(eurQueueName).setDurable(false));
      String usQueueName = "news.us.usQueue";
      adminSession.createQueue(QueueConfiguration.of(usQueueName).setDurable(false));
      // Step 4. Try to create a JMS Connection without user/password. It will fail.
      try {
         factory.createSession(false, true, true);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Step 5. bill tries to make a connection using wrong password
      try {
         billConnection = factory.createSession("bill", "activemq1", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Step 6. bill makes a good connection.
      billConnection = factory.createSession("bill", "activemq", false, true, true, false, -1);

      // Step 7. andrew makes a good connection.
      andrewConnection = factory.createSession("andrew", "activemq1", false, true, true, false, -1);

      // Step 8. frank makes a good connection.
      frankConnection = factory.createSession("frank", "activemq2", false, true, true, false, -1);

      // Step 9. sam makes a good connection.
      samConnection = factory.createSession("sam", "activemq3", false, true, true, false, -1);

      checkUserSendAndReceive(genericQueueName, billConnection);
      checkUserSendAndReceive(genericQueueName, andrewConnection);
      checkUserSendAndReceive(genericQueueName, frankConnection);
      checkUserSendAndReceive(genericQueueName, samConnection);

      // Step 11. Check permissions on news.europe.europeTopic for bill: can't send and can't
      // receive
      checkUserNoSendNoReceive(eurQueueName, billConnection, adminSession);

      // Step 12. Check permissions on news.europe.europeTopic for andrew: can send but can't
      // receive
      checkUserSendNoReceive(eurQueueName, andrewConnection);

      // Step 13. Check permissions on news.europe.europeTopic for frank: can't send but can
      // receive
      checkUserReceiveNoSend(eurQueueName, frankConnection, adminSession);

      // Step 14. Check permissions on news.europe.europeTopic for sam: can't send but can
      // receive
      checkUserReceiveNoSend(eurQueueName, samConnection, adminSession);

      // Step 15. Check permissions on news.us.usTopic for bill: can't send and can't receive
      checkUserNoSendNoReceive(usQueueName, billConnection, adminSession);

      // Step 16. Check permissions on news.us.usTopic for andrew: can't send and can't receive
      checkUserNoSendNoReceive(usQueueName, andrewConnection, adminSession);

      // Step 17. Check permissions on news.us.usTopic for frank: can both send and receive
      checkUserSendAndReceive(usQueueName, frankConnection);

      // Step 18. Check permissions on news.us.usTopic for same: can't send but can receive
      checkUserReceiveNoSend(usQueueName, samConnection, adminSession);

   }

   @Test
   public void testCustomSecurityManager() throws Exception {
      final Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true);
      final ActiveMQSecurityManager customSecurityManager = new ActiveMQSecurityManager() {
         @Override
         public boolean validateUser(final String username, final String password) {
            return (username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate");
         }

         @Override
         public boolean validateUserAndRole(final String username,
                                            final String password,
                                            final Set<Role> requiredRoles,
                                            final CheckType checkType) {

            if ((username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate")) {

               if (username.equals("all")) {
                  return true;
               } else if (username.equals("foo")) {
                  return checkType == CheckType.CONSUME || checkType == CheckType.CREATE_NON_DURABLE_QUEUE;
               } else if (username.equals("bar")) {
                  return checkType == CheckType.SEND || checkType == CheckType.CREATE_NON_DURABLE_QUEUE;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      };
      final ActiveMQServer server = addServer(new ActiveMQServerImpl(configuration, customSecurityManager));
      server.start();

      final ServerLocator locator = createInVMNonHALocator();
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      final ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession adminSession = factory.createSession("all", "frobnicate", false, true, true, false, -1);

      final String queueName = "test.queue";
      adminSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      // Wrong user name
      try {
         factory.createSession("baz", "frobnicate", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Wrong password
      try {
         factory.createSession("foo", "xxx", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, allowed to send but not receive
      {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(queueName, session, adminSession);
      }

      // Correct user and password, allowed to receive but not send
      {
         final ClientSession session = factory.createSession("bar", "frobnicate", false, true, true, false, -1);
         checkUserSendNoReceive(queueName, session);
      }

   }

   @Test
   public void testCustomSecurityManager2() throws Exception {
      final Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true);
      final ActiveMQSecurityManager customSecurityManager = new ActiveMQSecurityManager2() {
         @Override
         public boolean validateUser(final String username, final String password) {
            fail("Unexpected call to overridden method");
            return false;
         }

         @Override
         public boolean validateUser(final String username,
                                     final String password,
                                     final X509Certificate[] certificates) {
            return (username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate");
         }

         @Override
         public boolean validateUserAndRole(final String username,
                                            final String password,
                                            final Set<Role> requiredRoles,
                                            final CheckType checkType) {

            fail("Unexpected call to overridden method");
            return false;
         }

         @Override
         public boolean validateUserAndRole(final String username,
                                            final String password,
                                            final Set<Role> requiredRoles,
                                            final CheckType checkType,
                                            final String address,
                                            final RemotingConnection connection) {

            if (!(connection.getTransportConnection() instanceof InVMConnection)) {
               return false;
            }

            if ((username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate")) {

               if (username.equals("all")) {
                  return true;
               } else if (username.equals("foo")) {
                  return address.equals("test.queue") && checkType == CheckType.CONSUME;
               } else if (username.equals("bar")) {
                  return address.equals("test.queue") && checkType == CheckType.SEND;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      };
      final ActiveMQServer server = addServer(new ActiveMQServerImpl(configuration, customSecurityManager));
      server.start();

      final ServerLocator locator = createInVMNonHALocator();
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      final ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession adminSession = factory.createSession("all", "frobnicate", false, true, true, false, -1);

      final String queueName = "test.queue";
      adminSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      final String otherQueueName = "other.queue";
      adminSession.createQueue(QueueConfiguration.of(otherQueueName).setDurable(false));

      // Wrong user name
      try {
         factory.createSession("baz", "frobnicate", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Wrong password
      try {
         factory.createSession("foo", "xxx", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, wrong queue for sending
      try {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(otherQueueName, session, adminSession);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, wrong queue for receiving
      try {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(otherQueueName, session, adminSession);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, allowed to send but not receive
      {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(queueName, session, adminSession);
      }

      // Correct user and password, allowed to receive but not send
      {
         final ClientSession session = factory.createSession("bar", "frobnicate", false, true, true, false, -1);
         checkUserSendNoReceive(queueName, session);
      }
   }

   @Test
   public void testCustomSecurityManager3() throws Exception {
      final Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true);
      final ActiveMQSecurityManager customSecurityManager = new ActiveMQSecurityManager3() {
         @Override
         public boolean validateUser(final String username, final String password) {
            fail("Unexpected call to overridden method");
            return false;
         }

         @Override
         public String validateUser(final String username,
                                    final String password,
                                    final RemotingConnection remotingConnection) {
            if ((username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate")) {
               return username;
            } else {
               return null;
            }
         }

         @Override
         public boolean validateUserAndRole(final String username,
                                            final String password,
                                            final Set<Role> requiredRoles,
                                            final CheckType checkType) {

            fail("Unexpected call to overridden method");
            return false;
         }

         @Override
         public String validateUserAndRole(final String username,
                                           final String password,
                                           final Set<Role> requiredRoles,
                                           final CheckType checkType,
                                           final String address,
                                           final RemotingConnection connection) {

            if (!(connection.getTransportConnection() instanceof InVMConnection)) {
               return null;
            }

            if ((username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate")) {

               if (username.equals("all")) {
                  return username;
               } else if (username.equals("foo")) {
                  if (address.equals("test.queue") && checkType == CheckType.CONSUME) {
                     return username;
                  } else {
                     return null;
                  }
               } else if (username.equals("bar")) {
                  if (address.equals("test.queue") && checkType == CheckType.SEND) {
                     return username;
                  } else {
                     return null;
                  }
               } else {
                  return null;
               }
            } else {
               return null;
            }
         }
      };
      final ActiveMQServer server = addServer(new ActiveMQServerImpl(configuration, customSecurityManager));
      server.start();

      final ServerLocator locator = createInVMNonHALocator();
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      final ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession adminSession = factory.createSession("all", "frobnicate", false, true, true, false, -1);

      final String queueName = "test.queue";
      adminSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      final String otherQueueName = "other.queue";
      adminSession.createQueue(QueueConfiguration.of(otherQueueName).setDurable(false));

      // Wrong user name
      try {
         factory.createSession("baz", "frobnicate", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Wrong password
      try {
         factory.createSession("foo", "xxx", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, wrong queue for sending
      try {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(otherQueueName, session, adminSession);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, wrong queue for receiving
      try {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(otherQueueName, session, adminSession);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, allowed to send but not receive
      {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(queueName, session, adminSession);
      }

      // Correct user and password, allowed to receive but not send
      {
         final ClientSession session = factory.createSession("bar", "frobnicate", false, true, true, false, -1);
         checkUserSendNoReceive(queueName, session);
      }
   }

   @Test
   public void testCustomSecurityManager4() throws Exception {
      final Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true);
      final ActiveMQSecurityManager customSecurityManager = new ActiveMQSecurityManager4() {
         @Override
         public boolean validateUser(final String username, final String password) {
            fail("Unexpected call to overridden method");
            return false;
         }

         @Override
         public String validateUser(final String username,
                                    final String password,
                                    final RemotingConnection remotingConnection,
                                    final String securityDomain) {
            if ((username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate")) {
               return username;
            } else {
               return null;
            }
         }

         @Override
         public boolean validateUserAndRole(final String username,
                                            final String password,
                                            final Set<Role> requiredRoles,
                                            final CheckType checkType) {

            fail("Unexpected call to overridden method");
            return false;
         }

         @Override
         public String validateUserAndRole(final String username,
                                           final String password,
                                           final Set<Role> requiredRoles,
                                           final CheckType checkType,
                                           final String address,
                                           final RemotingConnection connection,
                                           final String securityDomain) {

            if (!(connection.getTransportConnection() instanceof InVMConnection)) {
               return null;
            }

            if ((username.equals("foo") || username.equals("bar") || username.equals("all")) && password.equals("frobnicate")) {

               if (username.equals("all")) {
                  return username;
               } else if (username.equals("foo")) {
                  if (address.equals("test.queue") && checkType == CheckType.CONSUME) {
                     return username;
                  } else {
                     return null;
                  }
               } else if (username.equals("bar")) {
                  if (address.equals("test.queue") && checkType == CheckType.SEND) {
                     return username;
                  } else {
                     return null;
                  }
               } else {
                  return null;
               }
            } else {
               return null;
            }
         }
      };
      final ActiveMQServer server = addServer(new ActiveMQServerImpl(configuration, customSecurityManager));
      server.start();

      final ServerLocator locator = createInVMNonHALocator();
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      final ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession adminSession = factory.createSession("all", "frobnicate", false, true, true, false, -1);

      final String queueName = "test.queue";
      adminSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      final String otherQueueName = "other.queue";
      adminSession.createQueue(QueueConfiguration.of(otherQueueName).setDurable(false));

      // Wrong user name
      try {
         factory.createSession("baz", "frobnicate", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Wrong password
      try {
         factory.createSession("foo", "xxx", false, true, true, false, -1);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, wrong queue for sending
      try {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(otherQueueName, session, adminSession);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, wrong queue for receiving
      try {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(otherQueueName, session, adminSession);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      // Correct user and password, allowed to send but not receive
      {
         final ClientSession session = factory.createSession("foo", "frobnicate", false, true, true, false, -1);
         checkUserReceiveNoSend(queueName, session, adminSession);
      }

      // Correct user and password, allowed to receive but not send
      {
         final ClientSession session = factory.createSession("bar", "frobnicate", false, true, true, false, -1);
         checkUserSendNoReceive(queueName, session);
      }
   }

   @Test
   public void testReauthenticationIsCached() throws Exception {
      ActiveMQServer server = createServer();
      server.start();

      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("auser", "pass");
      Role role = new Role("arole", true, false, false, false, false, false, false, false, true, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch(SecurityTest.addressA, roles);
      securityManager.getConfiguration().addRole("auser", "arole");
      server.createQueue(QueueConfiguration.of(SecurityTest.queueA).setAddress(SecurityTest.addressA));

      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthenticationCache();
      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthorizationCache();

      locator.setBlockOnNonDurableSend(true);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession("auser", "pass", false, true, true, false, -1);
      ClientProducer cp = session.createProducer(SecurityTest.addressA);
      cp.send(session.createMessage(false));

      assertEquals(1, ((SecurityStoreImpl)server.getSecurityStore()).getAuthenticationCacheSize());
      assertEquals(1, ((SecurityStoreImpl)server.getSecurityStore()).getAuthorizationCacheSize());

      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthenticationCache();
      ((SecurityStoreImpl)server.getSecurityStore()).invalidateAuthorizationCache();

      cp.send(session.createMessage(false));

      assertEquals(1, ((SecurityStoreImpl)server.getSecurityStore()).getAuthenticationCacheSize());
      assertEquals(1, ((SecurityStoreImpl)server.getSecurityStore()).getAuthorizationCacheSize());
   }

   // Check the user connection has both send and receive permissions on the queue
   private void checkUserSendAndReceive(final String genericQueueName,
                                        final ClientSession connection) throws Exception {
      connection.start();
      try {
         ClientProducer prod = connection.createProducer(genericQueueName);
         ClientConsumer con = connection.createConsumer(genericQueueName);
         ClientMessage m = connection.createMessage(false);
         prod.send(m);
         ClientMessage rec = con.receive(1000);
         assertNotNull(rec);
         rec.acknowledge();
      } finally {
         connection.stop();
      }
   }

   // Check the user can receive message but cannot send message.
   private void checkUserReceiveNoSend(final String queue,
                                       final ClientSession connection,
                                       final ClientSession sendingConn) throws Exception {
      connection.start();
      try {
         ClientProducer prod = connection.createProducer(queue);
         ClientMessage m = connection.createMessage(false);
         try {
            prod.send(m);
            fail("should throw exception");
         } catch (ActiveMQException e) {
            // pass
         }

         prod = sendingConn.createProducer(queue);
         prod.send(m);
         ClientConsumer con = connection.createConsumer(queue);
         ClientMessage rec = con.receive(1000);
         assertNotNull(rec);
         rec.acknowledge();
      } finally {
         connection.stop();
      }
   }

   private void checkUserNoSendNoReceive(final String queue,
                                         final ClientSession connection,
                                         final ClientSession sendingConn) throws Exception {
      connection.start();
      try {
         ClientProducer prod = connection.createProducer(queue);
         ClientMessage m = connection.createMessage(false);
         try {
            prod.send(m);
            fail("should throw exception");
         } catch (ActiveMQException e) {
            // pass
         }

         prod = sendingConn.createProducer(queue);
         prod.send(m);

         try {
            connection.createConsumer(queue);
            fail("should throw exception");
         } catch (ActiveMQException e) {
            // pass
         }
      } finally {
         connection.stop();
      }
   }

   // Check the user can send message but cannot receive message
   private void checkUserSendNoReceive(final String queue, final ClientSession connection) throws Exception {
      ClientProducer prod = connection.createProducer(queue);
      ClientMessage m = connection.createMessage(false);
      prod.send(m);

      try {
         connection.createConsumer(queue);
         fail("should throw exception");
      } catch (ActiveMQSecurityException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   public static class DummySensitiveDataCodec implements SensitiveDataCodec<String> {
      @Override
      public String decode(Object encodedValue) throws Exception {
         throw new IllegalStateException("Decoding not supported");
      }

      @Override
      public String encode(Object value) throws Exception {
         return new StringBuilder((String)value).reverse().toString();
      }

      @Override
      public boolean verify(char[] value, String encodedValue) {
         return encodedValue.equals(new StringBuilder(String.valueOf(value)).reverse().toString());
      }
   }
}
