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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslHandler;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

/**
 * Verifies that enabledCipherSuites and enabledProtocols configured on a Netty acceptor
 * are correctly applied to the underlying SSLEngine. Connection-level enforcement of these
 * settings is covered by CoreClientOverOneWaySSLTest.
 *
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class SSLEngineEnforcementTest extends ActiveMQTestBase {

   private static final String ACCEPTOR_NAME = "nettySSL";
   private static final String PASSWORD = "securepass";
   private static final String SERVER_KEYSTORE = "server-keystore.p12";
   private static final String CLIENT_TRUSTSTORE = "server-ca-truststore.p12";
   private static final String STORE_TYPE = "PKCS12";

   private ActiveMQServer server;

   @Test
   public void testAcceptorCipherSuiteEnforcement() throws Exception {
      String allowedCipher = findCompatibleCipherSuite();

      startServerWithRestrictions(allowedCipher, "TLSv1.2");

      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor(ACCEPTOR_NAME);
      assertNotNull(acceptor);
      SslHandler sslHandler = acceptor.getSslHandler(ByteBufAllocator.DEFAULT, null, 0);
      SSLEngine engine = sslHandler.engine();
      String[] engineCiphers = engine.getEnabledCipherSuites();
      assertEquals(1, engineCiphers.length, "Acceptor SSLEngine should have exactly one enabled cipher suite");
      assertEquals(allowedCipher, engineCiphers[0]);
      engine.closeOutbound();
   }

   @Test
   public void testAcceptorProtocolEnforcement() throws Exception {
      startServerWithRestrictions(null, "TLSv1.2");

      NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor(ACCEPTOR_NAME);
      assertNotNull(acceptor);
      SslHandler sslHandler = acceptor.getSslHandler(ByteBufAllocator.DEFAULT, null, 0);
      SSLEngine engine = sslHandler.engine();
      String[] engineProtocols = engine.getEnabledProtocols();
      assertArrayEquals(new String[]{"TLSv1.2"}, engineProtocols, "Acceptor SSLEngine should have exactly TLSv1.2 enabled");
      engine.closeOutbound();
   }

   private void startServerWithRestrictions(String cipherSuites, String protocols) throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, STORE_TYPE);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, SERVER_KEYSTORE);
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);
      params.put(TransportConstants.HOST_PROP_NAME, "localhost");

      if (cipherSuites != null) {
         params.put(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME, cipherSuites);
      }

      if (protocols != null) {
         params.put(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME, protocols);
      }

      ConfigurationImpl config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, ACCEPTOR_NAME));
      server = createServer(false, config);
      server.start();
      waitForServerToStart(server);
   }

   private String findCompatibleCipherSuite() throws Exception {
      SSLContext context = new SSLSupport()
         .setKeystoreType(STORE_TYPE)
         .setKeystorePath(SERVER_KEYSTORE)
         .setKeystorePassword(PASSWORD)
         .setTruststoreType(STORE_TYPE)
         .setTruststorePath(CLIENT_TRUSTSTORE)
         .setTruststorePassword(PASSWORD)
         .createContext();
      SSLEngine engine = context.createSSLEngine();
      String[] suites = engine.getEnabledCipherSuites();
      engine.closeOutbound();

      for (String suite : suites) {
         if (!suite.contains("ECDSA") && suite.contains("RSA")) {
            return suite;
         }
      }

      fail("No RSA-compatible cipher suite available for this test");
      return null;
   }
}
