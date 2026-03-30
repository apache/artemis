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
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;

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
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

/**
 * Tests for the keyPassword SSL parameter which allows using a key password
 * that differs from the keystore password. This is supported by JKS and JCEKS
 * keystore types but not PKCS12.
 *
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class CoreClientOverOneWaySSLKeyPasswordTest extends ActiveMQTestBase {

   public static final SimpleString QUEUE = SimpleString.of("QueueOverSSLKeyPassword");

   private static final String SERVER_SIDE_KEYSTORE = "server-keystore-keypass.jks";
   private static final String CLIENT_SIDE_TRUSTSTORE = "server-ca-truststore.jks";
   private static final String STORE_PASSWORD = "securepass";
   private static final String KEY_PASSWORD = "keypass123";

   private ActiveMQServer server;
   private TransportConfiguration tc;

   @Test
   public void testOneWaySSLWithKeyPassword() throws Exception {
      createSslServerWithKeyPassword(KEY_PASSWORD);
      String text = RandomUtil.randomUUIDString();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, STORE_PASSWORD);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));
      ClientSession session = addClientSession(sf.createSession(false, true, true));
      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));
      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      ClientMessage message = createTextMessage(session, text);
      producer.send(message);

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals(text, m.getBodyBuffer().readString());
   }

   @Test
   public void testOneWaySSLWithWrongKeyPassword() throws Exception {
      // Wrong key password causes the SSL acceptor to fail initialization; client cannot connect
      createSslServerWithKeyPassword("wrongKeyPassword");

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, STORE_PASSWORD);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      try {
         createSessionFactory(locator);
         fail("Should have failed to connect with wrong key password");
      } catch (Exception e) {
         // Expected
      }
   }

   @Test
   public void testOneWaySSLWithKeyPasswordAndAlias() throws Exception {
      createSslServerWithKeyPasswordAndAlias(KEY_PASSWORD, "server");
      String text = RandomUtil.randomUUIDString();

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, STORE_PASSWORD);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));
      ClientSession session = addClientSession(sf.createSession(false, true, true));
      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));
      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      ClientMessage message = createTextMessage(session, text);
      producer.send(message);

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals(text, m.getBodyBuffer().readString());
   }

   @Test
   public void testOneWaySSLWithoutKeyPasswordFails() throws Exception {
      // Without keyPassword, keystorePassword is used for the key; mismatch causes acceptor failure
      createSslServerWithKeyPassword(null);

      tc.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
      tc.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, STORE_PASSWORD);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(tc));
      try {
         createSessionFactory(locator);
         fail("Should have failed to connect without specifying keyPassword");
      } catch (Exception e) {
         // Expected
      }
   }

   private void createSslServerWithKeyPassword(String keyPassword) throws Exception {
      createSslServerWithKeyPasswordAndAlias(keyPassword, null);
   }

   private void createSslServerWithKeyPasswordAndAlias(String keyPassword, String alias) throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, "JKS");
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, SERVER_SIDE_KEYSTORE);
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, STORE_PASSWORD);
      if (keyPassword != null) {
         params.put(TransportConstants.KEY_PASSWORD_PROP_NAME, keyPassword);
      }
      if (alias != null) {
         params.put(TransportConstants.KEYSTORE_ALIAS_PROP_NAME, alias);
      }
      params.put(TransportConstants.HOST_PROP_NAME, "localhost");

      ConfigurationImpl config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, "nettySSL"));
      server = createServer(false, config);
      server.start();
      waitForServerToStart(server);
      tc = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
   }
}
