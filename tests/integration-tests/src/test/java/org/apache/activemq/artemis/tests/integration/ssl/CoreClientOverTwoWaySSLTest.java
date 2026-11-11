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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.Interceptor;
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
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.socket.PortFactory;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class CoreClientOverTwoWaySSLTest extends ActiveMQTestBase {

   @Parameters(name = "storeProvider={0}, storeType={1}, clientSSLProvider={2}, serverSSLProvider={3}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{
         {TransportConstants.DEFAULT_KEYSTORE_PROVIDER, TransportConstants.DEFAULT_KEYSTORE_TYPE, TransportConstants.OPENSSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {TransportConstants.DEFAULT_KEYSTORE_PROVIDER, TransportConstants.DEFAULT_KEYSTORE_TYPE, TransportConstants.OPENSSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {TransportConstants.DEFAULT_KEYSTORE_PROVIDER, TransportConstants.DEFAULT_KEYSTORE_TYPE, TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {TransportConstants.DEFAULT_KEYSTORE_PROVIDER, TransportConstants.DEFAULT_KEYSTORE_TYPE, TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"SunJCE", "JCEKS", TransportConstants.OPENSSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {"SunJCE", "JCEKS", TransportConstants.OPENSSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"SunJCE", "JCEKS", TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {"SunJCE", "JCEKS", TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"SUN", "JKS", TransportConstants.OPENSSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {"SUN", "JKS", TransportConstants.OPENSSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"SUN", "JKS", TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {"SUN", "JKS", TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"SunJSSE", "PKCS12", TransportConstants.OPENSSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {"SunJSSE", "PKCS12", TransportConstants.OPENSSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"SunJSSE", "PKCS12", TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER},
         {"SunJSSE", "PKCS12", TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {TransportConstants.DEFAULT_KEYSTORE_TYPE, null, TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"JCEKS", null, TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"JKS", null, TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER},
         {"PKCS12", null, TransportConstants.DEFAULT_SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER}
      });
   }

   public CoreClientOverTwoWaySSLTest(String storeProvider, String storeType, String clientSSLProvider, String serverSSLProvider) {
      this.storeProvider = storeProvider;
      this.storeType = storeType;
      this.clientSSLProvider = clientSSLProvider;
      this.serverSSLProvider = serverSSLProvider;

      String suffix = storeType == null || storeType.isEmpty() ? storeProvider.toLowerCase() : storeType.toLowerCase();
      // keytool expects PKCS12 stores to use the extension "p12"
      if (suffix.equalsIgnoreCase("PKCS12")) {
         suffix = "p12";
      }

      SERVER_SIDE_KEYSTORE = "server-keystore." + suffix;
      SERVER_SIDE_TRUSTSTORE = "client-ca-truststore." + suffix;
      CLIENT_SIDE_TRUSTSTORE = "server-ca-truststore." + suffix;
      CLIENT_SIDE_KEYSTORE = "client-keystore." + suffix;
   }

   public static final SimpleString QUEUE = SimpleString.of("QueueOverSSL");

   private String storeType;
   private String storeProvider;
   private String clientSSLProvider;
   private String serverSSLProvider;
   private String SERVER_SIDE_KEYSTORE;
   private String SERVER_SIDE_TRUSTSTORE;
   private String CLIENT_SIDE_TRUSTSTORE;
   private String CLIENT_SIDE_KEYSTORE;
   private final String PASSWORD = "securepass";

   private ActiveMQServer server;

   private TransportConfiguration tc;

   private class MyInterceptor implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_SEND) {
            try {
               if (connection.getTransportConnection() instanceof NettyConnection nettyConnection) {
                  SslHandler sslHandler = (SslHandler) nettyConnection.getChannel().pipeline().get("ssl");
                  assertNotNull(sslHandler);
                  assertNotNull(sslHandler.engine().getSession());
                  assertNotNull(sslHandler.engine().getSession().getPeerCertificates());
               }
            } catch (SSLPeerUnverifiedException e) {
               fail(e.getMessage());
            }
         }
         return true;
      }
   }

   @TestTemplate
   public void testTwoWaySSL() throws Exception {
      String text = RandomUtil.randomUUIDString();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      tc.getParams().put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(CoreClientOverTwoWaySSLTest.QUEUE).setDurable(false));
      ClientProducer producer = session.createProducer(CoreClientOverTwoWaySSLTest.QUEUE);

      ClientMessage message = createTextMessage(session, text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(CoreClientOverTwoWaySSLTest.QUEUE);
      session.start();

      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals(text, m.getBodyBuffer().readString());
   }

   @TestTemplate
   public void testTwoWaySSLChooseAliasPositive() throws Exception {
      String text = RandomUtil.randomUUIDString();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      // the alias is specified when the keystore is created; see tests/security-resources/build.sh
      tc.getParams().put(TransportConstants.KEYSTORE_ALIAS_PROP_NAME, "client");

      tc.getParams().put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(CoreClientOverTwoWaySSLTest.QUEUE).setDurable(false));
      ClientProducer producer = session.createProducer(CoreClientOverTwoWaySSLTest.QUEUE);

      ClientMessage message = createTextMessage(session, text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(CoreClientOverTwoWaySSLTest.QUEUE);
      session.start();

      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals(text, m.getBodyBuffer().readString());
   }

   @TestTemplate
   public void testTwoWaySSLChooseAliasNegative() throws Exception {

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);
      tc.getParams().put(TransportConstants.KEYSTORE_ALIAS_PROP_NAME, RandomUtil.randomUUIDString());

      tc.getParams().put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      locator.setCallTimeout(500);
      try {
         ClientSessionFactory sf = createSessionFactory(locator);
         fail();
      } catch (ActiveMQNotConnectedException | ActiveMQConnectionTimedOutException e) {
         // expected
      }
   }

   @TestTemplate
   public void testTwoWaySSLVerifyClientHost() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.VERIFY_HOST_PROP_NAME, true);
      acceptor.getConfiguration().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, SERVER_SIDE_TRUSTSTORE);
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      String text = RandomUtil.randomUUIDString();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(CoreClientOverTwoWaySSLTest.QUEUE).setDurable(false));
      ClientProducer producer = session.createProducer(CoreClientOverTwoWaySSLTest.QUEUE);

      ClientMessage message = createTextMessage(session, text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(CoreClientOverTwoWaySSLTest.QUEUE);
      session.start();

      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals(text, m.getBodyBuffer().readString());
   }

   @TestTemplate
   public void testTwoWaySSLVerifyClientHostNegative() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.VERIFY_HOST_PROP_NAME, true);
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "unknown" + CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      locator.setCallTimeout(1000);
      try {
         ClientSessionFactory sf = createSessionFactory(locator);
         fail("Creating a session here should fail due to a certificate with a CN that doesn't match the host name.");
      } catch (Exception e) {
         // ignore
      }
   }

   @TestTemplate
   public void testTwoWaySSLVerifyClientTrustAllTrue() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      //Set trust all so this should work even with no trust store set
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);
      tc.getParams().put(TransportConstants.TRUST_ALL_PROP_NAME, true);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = createSessionFactory(locator);
      sf.close();
   }

   @TestTemplate
   public void testTwoWaySSLVerifyClientTrustAllTrueByURI() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      //Set trust all so this should work even with no trust store set
      StringBuilder uri = new StringBuilder("tcp://" + tc.getParams().get(TransportConstants.HOST_PROP_NAME).toString()
            + ":" + tc.getParams().get(TransportConstants.PORT_PROP_NAME).toString());

      uri.append("?").append(TransportConstants.SSL_ENABLED_PROP_NAME).append("=true");
      uri.append("&").append(TransportConstants.SSL_PROVIDER).append("=").append(clientSSLProvider);
      uri.append("&").append(TransportConstants.TRUST_ALL_PROP_NAME).append("=true");
      if (storeProvider != null && !storeProvider.equals(TransportConstants.DEFAULT_KEYSTORE_PROVIDER)) {
         uri.append("&").append(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME).append("=").append(storeProvider);
      }
      if (storeType != null && !storeType.equals(TransportConstants.DEFAULT_KEYSTORE_TYPE)) {
         uri.append("&").append(TransportConstants.KEYSTORE_TYPE_PROP_NAME).append("=").append(storeType);
      }
      uri.append("&").append(TransportConstants.KEYSTORE_PATH_PROP_NAME).append("=").append(CLIENT_SIDE_KEYSTORE);
      uri.append("&").append(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME).append("=").append(PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator(uri.toString()));
      ClientSessionFactory sf = createSessionFactory(locator);
      sf.close();
   }

   @TestTemplate
   public void testTwoWaySSLVerifyClientTrustAllFalse() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      //Trust all defaults to false so this should fail with no trust store set
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      try {
         ClientSessionFactory sf = createSessionFactory(locator);
         fail("Creating a session here should fail due to no trust store being set");
      } catch (Exception e) {
         // ignore
      }
   }

   @TestTemplate
   public void testTwoWaySSLWithoutClientKeyStore() throws Exception {
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      locator.setCallTimeout(1000);
      try {
         createSessionFactory(locator);
         fail();
      } catch (ActiveMQNotConnectedException se) {
         //ok
      } catch (ActiveMQConnectionTimedOutException te) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @TestTemplate
   public void testTwoWaySSLWithNoFallbackRevocationChecker() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      acceptor.getConfiguration().put(TransportConstants.CRC_OPTIONS_PROP_NAME, "NO_FALLBACK");
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      //Trust all defaults to false so this should fail with no trust store set
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      try {
         ClientSessionFactory sf = createSessionFactory(locator);
         fail("Creating a session here should fail due to no fallback revocation checker");
      } catch (Exception e) {
         // ignore
      }
   }

   @TestTemplate
   public void testTwoWaySSLWithSoftFailRevocationChecker() throws Exception {
      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor("nettySSL");
      acceptor.getConfiguration().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      acceptor.getConfiguration().put(TransportConstants.CRC_OPTIONS_PROP_NAME, "SOFT_FAIL");
      acceptor.getConfiguration().put(TransportConstants.OCSP_RESPONDER_URL_PROP_NAME, "http://localhost:" + PortFactory.findFreePort());
      server.getRemotingService().stop(false);
      server.getRemotingService().start();
      server.getRemotingService().startAcceptors();

      //Set trust all so this should work even with no trust store set
      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.SSL_PROVIDER, clientSSLProvider);
      tc.getParams().put(TransportConstants.TRUST_ALL_PROP_NAME, true);

      tc.getParams().put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      tc.getParams().put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);
      tc.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, CLIENT_SIDE_KEYSTORE);
      tc.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);

      server.getRemotingService().addIncomingInterceptor(new MyInterceptor());

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         ClientSessionFactory sf = createSessionFactory(locator);
         sf.close();

         assertTrue(loggerHandler.findText("AMQ212081",
            "[CN=ActiveMQ Artemis Client,OU=Artemis,O=ActiveMQ,L=AMQ,ST=AMQ,C=AMQ]", "Exception"));
      }
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.SSL_PROVIDER, serverSSLProvider);

      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, SERVER_SIDE_KEYSTORE);
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);
      params.put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, storeProvider);
      params.put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, storeType);

      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, SERVER_SIDE_TRUSTSTORE);
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);
      params.put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, storeProvider);
      params.put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, storeType);

      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);

      ConfigurationImpl config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, "nettySSL"));
      server = createServer(false, config);
      server.start();
      waitForServerToStart(server);
      tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
   }
}
