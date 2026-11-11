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
package org.apache.activemq.artemis.spi.core.security.jaas;

import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LDAPLoginSSLSocketFactoryTest {

   @Test
   public void testConstructorsWithNullEnvironment() {
      try {
         LDAPLoginModule.removeEnvironment();
         new LDAPLoginSSLSocketFactory();
         fail("Should have thrown NullPointerException");
      } catch (Exception e) {
         assertEquals(NullPointerException.class, e.getClass());
      }

      try {
         new LDAPLoginSSLSocketFactory(null);
         fail("Should have thrown NullPointerException");
      } catch (Exception e) {
         assertEquals(NullPointerException.class, e.getClass());
      }
   }

   @Test
   public void testConstructorsWithEmptyEnvironment() {
      LDAPLoginModule.setEnvironment(Collections.emptyMap());
      try {
         new LDAPLoginSSLSocketFactory();
      } finally {
         LDAPLoginModule.removeEnvironment();
      }

      new LDAPLoginSSLSocketFactory(Collections.emptyMap());
   }

   @Test
   public void testGetDefaultFactoryWithNullEnvironment() {
      try {
         LDAPLoginModule.removeEnvironment();
         LDAPLoginSSLSocketFactory.getDefault();
         fail("Should have thrown NullPointerException");
      } catch (Exception e) {
         assertEquals(NullPointerException.class, e.getClass());
      }
   }

   @Test
   public void testGetDefaultFactoryWithEmptyEnvironment() {
      LDAPLoginModule.setEnvironment(Collections.emptyMap());
      try {
         LDAPLoginSSLSocketFactory.getDefault();
      } finally {
         LDAPLoginModule.removeEnvironment();
      }
   }

   @Test
   public void testGetSSLContextConfigWithAllOptions() {
      Map<String, String> environment = new HashMap<>();
      environment.put("keystoreProvider", "SunJSSE");
      environment.put("keystoreType", "PKCS12");
      environment.put("keystorePath", "server-keystore.p12");
      environment.put("keystorePassword", "securepass");
      environment.put("keystoreAlias", "server");
      environment.put("truststoreProvider", "SUN");
      environment.put("truststoreType", "JKS");
      environment.put("truststorePath", "server-ca-truststore.jks");
      environment.put("truststorePassword", "securepass");
      environment.put("crlPath", "/path/to/crl");
      environment.put("trustAll", "false");
      environment.put("trustManagerFactoryPlugin", "trust.manager.factory.Plugin");
      environment.put("crcOptions", "ONLY_END_ENTITY,SOFT_FAIL");
      environment.put("ocspResponderURL", "http://localhost:8080");

      LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(environment);
      SSLContextConfig config = factory.getSSLContextConfig();

      assertNotNull(config);
      assertEquals("SunJSSE", config.getKeystoreProvider());
      assertEquals("PKCS12", config.getKeystoreType());
      assertEquals("server-keystore.p12", config.getKeystorePath());
      assertEquals("securepass", config.getKeystorePassword());
      assertEquals("server", config.getKeystoreAlias());
      assertEquals("SUN", config.getTruststoreProvider());
      assertEquals("JKS", config.getTruststoreType());
      assertEquals("server-ca-truststore.jks", config.getTruststorePath());
      assertEquals("securepass", config.getTruststorePassword());
      assertEquals("/path/to/crl", config.getCrlPath());
      assertFalse(config.isTrustAll());
      assertEquals("trust.manager.factory.Plugin", config.getTrustManagerFactoryPlugin());
      assertEquals("ONLY_END_ENTITY,SOFT_FAIL", config.getCrcOptions());
      assertEquals("http://localhost:8080", config.getOcspResponderURL());
   }

   @Test
   public void testPlainPasswordsWithDefaultPasswordCodec() throws Exception {
      Map<String, String> environment = new HashMap<>();
      environment.put("keystorePassword", "abc");
      environment.put("truststorePassword", "xyz");

      testPasswords(environment);
   }

   @Test
   public void testEncodedPasswordsWithDefaultPasswordCodec() throws Exception {
      Map<String, String> environment = new HashMap<>();
      environment.put("keystorePassword", PasswordMaskingUtil.wrap(
         PasswordMaskingUtil.getDefaultCodec().encode("abc")));
      environment.put("truststorePassword", PasswordMaskingUtil.wrap(
         PasswordMaskingUtil.getDefaultCodec().encode("xyz")));

      testPasswords(environment);
   }

   @Test
   public void testPlainPasswordsWithCustomPasswordCodec() {
      Map<String, String> environment = new HashMap<>();
      environment.put("passwordCodec", LDAPLoginModuleTest.TestSensitiveDataCodec.class.getName());
      environment.put("keystorePassword", "abc");
      environment.put("truststorePassword", "xyz");

      testPasswords(environment);
   }

   @Test
   public void testEncodedPasswordsWithCustomPasswordCodec() {
      Map<String, String> environment = new HashMap<>();
      environment.put("passwordCodec", LDAPLoginModuleTest.TestSensitiveDataCodec.class.getName());
      environment.put("keystorePassword", "ENC(cba)");
      environment.put("truststorePassword", "ENC(zyx)");

      testPasswords(environment);
   }

   public void testPasswords(Map<String, String> environment) {
      LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(environment);
      SSLContextConfig config = factory.getSSLContextConfig();
      assertEquals("abc", config.getKeystorePassword());
      assertEquals("xyz", config.getTruststorePassword());
   }

   @Test
   public void testEncodedPasswordsWithInvalidCodec() {
      Map<String, String> environment = new HashMap<>();
      environment.put("passwordCodec", "com.example.NonExistentCodec");
      environment.put("keystorePassword", "ENC(cba)");
      environment.put("truststorePassword", "ENC(zyx)");

      LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(environment);
      try {
         factory.getSSLContextConfig();
         fail("Should have thrown IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         assertTrue(e.getMessage().contains("Failed to parse sensitive text"));
      }
   }


   @Test
   public void testCreateSocketUnconnected() throws Exception {
      final LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(Collections.emptyMap());

      try (Socket socket = factory.createSocket()) {
         assertFalse(socket.isConnected());
         assertFalse(socket.isClosed());
      }
   }

   @Test
   public void testCreateSocketWithHostAndPort() throws IOException {
      final LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(Collections.emptyMap());

      testSocket((TestSocketFactory) port -> factory.createSocket("localhost", port));
   }

   @Test
   public void testCreateSocketWithAllParameters() throws IOException {
      final LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(Collections.emptyMap());

      testSocket((TestSocketFactory) port -> factory.createSocket("localhost", port, InetAddress.getLoopbackAddress(), 0));
   }

   @Test
   public void testCreateSocketWithInetAddress() throws IOException {
      final LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(Collections.emptyMap());

      testSocket((TestSocketFactory) port -> factory.createSocket(InetAddress.getLoopbackAddress(), port));
   }

   @Test
   public void testCreateSocketWithInetAddressAndLocalAddress() throws IOException {
      final LDAPLoginSSLSocketFactory factory = new LDAPLoginSSLSocketFactory(Collections.emptyMap());

      testSocket((TestSocketFactory) port -> factory.createSocket(InetAddress.getLoopbackAddress(), port, InetAddress.getLoopbackAddress(), 0));
   }

   @FunctionalInterface
   private interface TestSocketFactory {
      Socket createSocket(int port) throws IOException;
   }

   private void testSocket(TestSocketFactory socketFactory) throws IOException {
      try (ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
         try (Socket socket = socketFactory.createSocket(serverSocket.getLocalPort())) {
            assertTrue(socket.getRemoteSocketAddress() instanceof InetSocketAddress);
            assertEquals(InetAddress.getLoopbackAddress(), ((InetSocketAddress)socket.getRemoteSocketAddress()).getAddress());
            assertEquals(serverSocket.getLocalPort(), ((InetSocketAddress)socket.getRemoteSocketAddress()).getPort());
         }
      }
   }
}

