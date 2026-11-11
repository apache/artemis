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

import org.apache.activemq.artemis.core.remoting.impl.ssl.CachingSSLContextFactory;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactory;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class LDAPLoginSSLSocketFactory extends SocketFactory {

   private static final String KEYSTORE_PROVIDER = "keystoreProvider";
   private static final String KEYSTORE_TYPE = "keystoreType";
   private static final String KEYSTORE_PATH = "keystorePath";
   private static final String KEYSTORE_PASSWORD = "keystorePassword";
   private static final String KEYSTORE_ALIAS = "keystoreAlias";
   private static final String TRUSTSTORE_PROVIDER = "truststoreProvider";
   private static final String TRUSTSTORE_TYPE = "truststoreType";
   private static final String TRUSTSTORE_PATH = "truststorePath";
   private static final String TRUSTSTORE_PASSWORD = "truststorePassword";
   private static final String CRL_PATH = "crlPath";
   private static final String TRUST_ALL = "trustAll";
   private static final String TRUST_MANAGER_FACTORY_PLUGIN = "trustManagerFactoryPlugin";
   private static final String CRC_OPTIONS = "crcOptions";
   private static final String OCSP_RESPONDER_URL = "ocspResponderURL";

   private static final SSLContextFactory sslContextFactory = new CachingSSLContextFactory();

   protected static SSLContextFactory getSSLContextFactory() {
      return sslContextFactory;
   }

   public static LDAPLoginSSLSocketFactory getDefault() {
      return new LDAPLoginSSLSocketFactory();
   }

   private final String codecClass;
   private final Map<String, String> environment;

   private SSLSocketFactory sslSocketFactory;

   public LDAPLoginSSLSocketFactory() {
      this(LDAPLoginModule.getEnvironment());
   }

   public LDAPLoginSSLSocketFactory(Map<String, String> environment) {
      Objects.requireNonNull(environment, "LDAPLoginModule environment is null");
      this.environment = environment;

      codecClass = environment.get(LDAPLoginModule.ConfigKey.PASSWORD_CODEC.getName());
   }

   protected void loadSSLSocketFactory() {
      final SSLContext sslContext = getSSLContext();

      sslSocketFactory = sslContext.getSocketFactory();
   }

   protected SSLContext getSSLContext() {
      final SSLContextConfig sslContextConfig = getSSLContextConfig();

      try {
         return sslContextFactory.getSSLContext(sslContextConfig,
            Collections.unmodifiableMap(environment));
      } catch (Exception e) {
         throw new IllegalStateException("Error getting the ssl context", e);
      }
   }

   protected SSLContextConfig getSSLContextConfig() {
      SSLContextConfig.Builder sslContextConfigBuilder = SSLContextConfig.builder();

      if (environment.containsKey(KEYSTORE_PROVIDER)) {
         sslContextConfigBuilder.keystoreProvider(environment.get(KEYSTORE_PROVIDER));
      }
      if (environment.containsKey(KEYSTORE_TYPE)) {
         sslContextConfigBuilder.keystoreType(environment.get(KEYSTORE_TYPE));
      }
      if (environment.containsKey(KEYSTORE_PATH)) {
         sslContextConfigBuilder.keystorePath(environment.get(KEYSTORE_PATH));
      }
      if (environment.containsKey(KEYSTORE_PASSWORD)) {
         sslContextConfigBuilder.keystorePassword(getSensitiveText(KEYSTORE_PASSWORD));
      }
      if (environment.containsKey(KEYSTORE_ALIAS)) {
         sslContextConfigBuilder.keystoreAlias(environment.get(KEYSTORE_ALIAS));
      }
      if (environment.containsKey(TRUSTSTORE_PROVIDER)) {
         sslContextConfigBuilder.truststoreProvider(environment.get(TRUSTSTORE_PROVIDER));
      }
      if (environment.containsKey(TRUSTSTORE_TYPE)) {
         sslContextConfigBuilder.truststoreType(environment.get(TRUSTSTORE_TYPE));
      }
      if (environment.containsKey(TRUSTSTORE_PATH)) {
         sslContextConfigBuilder.truststorePath(environment.get(TRUSTSTORE_PATH));
      }
      if (environment.containsKey(TRUSTSTORE_PASSWORD)) {
         sslContextConfigBuilder.truststorePassword(getSensitiveText(TRUSTSTORE_PASSWORD));
      }
      if (environment.containsKey(CRL_PATH)) {
         sslContextConfigBuilder.crlPath(environment.get(CRL_PATH));
      }
      if (environment.containsKey(TRUST_ALL)) {
         sslContextConfigBuilder.trustAll(Boolean.parseBoolean(environment.get(TRUST_ALL)));
      }
      if (environment.containsKey(TRUST_MANAGER_FACTORY_PLUGIN)) {
         sslContextConfigBuilder.trustManagerFactoryPlugin(environment.get(TRUST_MANAGER_FACTORY_PLUGIN));
      }
      if (environment.containsKey(CRC_OPTIONS)) {
         sslContextConfigBuilder.crcOptions(environment.get(CRC_OPTIONS));
      }
      if (environment.containsKey(OCSP_RESPONDER_URL)) {
         sslContextConfigBuilder.ocspResponderURL(environment.get(OCSP_RESPONDER_URL));
      }

      return sslContextConfigBuilder.build();
   }

   protected String getSensitiveText(String key) {
      try {
         String text = environment.get(key);
         return PasswordMaskingUtil.resolveMask(text, codecClass);
      } catch (Exception e) {
         throw new IllegalArgumentException("Failed to parse sensitive text " + key, e);
      }
   }

   private void checkSSLSocketFactory() {
      if (sslSocketFactory == null) {
         loadSSLSocketFactory();
      }
   }

   @Override
   public Socket createSocket() throws IOException {
      checkSSLSocketFactory();

      return sslSocketFactory.createSocket();
   }

   @Override
   public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
      checkSSLSocketFactory();

      return sslSocketFactory.createSocket(host, port);
   }

   @Override
   public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
      checkSSLSocketFactory();

      return sslSocketFactory.createSocket(host, port, localHost, localPort);
   }

   @Override
   public Socket createSocket(InetAddress host, int port) throws IOException {
      checkSSLSocketFactory();

      return sslSocketFactory.createSocket(host, port);
   }

   @Override
   public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      checkSSLSocketFactory();

      return sslSocketFactory.createSocket(address, port, localAddress, localPort);
   }
}
