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
package org.apache.activemq.transport.netty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Holds the defined SSL options for connections that operate over a secure transport. Options are read from the
 * environment and can be overridden by specifying them on the connection URI.
 */
public class NettyTransportSslOptions extends NettyTransportOptions {

   public static final String DEFAULT_STORE_TYPE = "jks";
   public static final String DEFAULT_CONTEXT_PROTOCOL = "TLS";
   public static final boolean DEFAULT_TRUST_ALL = false;
   public static final boolean DEFAULT_VERIFY_HOST = false;
   public static final List<String> DEFAULT_DISABLED_PROTOCOLS = Collections.unmodifiableList(Arrays.asList(new String[] {"SSLv2Hello", "SSLv3"}));
   public static final int DEFAULT_SSL_PORT = 5671;

   public static final NettyTransportSslOptions INSTANCE = new NettyTransportSslOptions();

   private String keyStoreLocation;
   private String keyStorePassword;
   private String trustStoreLocation;
   private String trustStorePassword;
   private String storeType = DEFAULT_STORE_TYPE;
   private String[] enabledCipherSuites;
   private String[] disabledCipherSuites;
   private String[] enabledProtocols;
   private String[] disabledProtocols = DEFAULT_DISABLED_PROTOCOLS.toArray(new String[0]);
   private String contextProtocol = DEFAULT_CONTEXT_PROTOCOL;

   private boolean trustAll = DEFAULT_TRUST_ALL;
   private boolean verifyHost = DEFAULT_VERIFY_HOST;
   private String keyAlias;
   private int defaultSslPort = DEFAULT_SSL_PORT;

   static {
      INSTANCE.setKeyStoreLocation(System.getProperty("javax.net.ssl.keyStore"));
      INSTANCE.setKeyStorePassword(System.getProperty("javax.net.ssl.keyStorePassword"));
      INSTANCE.setTrustStoreLocation(System.getProperty("javax.net.ssl.trustStore"));
      INSTANCE.setTrustStorePassword(System.getProperty("javax.net.ssl.keyStorePassword"));
   }

   public String getKeyStoreLocation() {
      return keyStoreLocation;
   }

   public void setKeyStoreLocation(String keyStoreLocation) {
      this.keyStoreLocation = keyStoreLocation;
   }

   public String getKeyStorePassword() {
      return keyStorePassword;
   }

   public void setKeyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
   }

   public String getTrustStoreLocation() {
      return trustStoreLocation;
   }

   public void setTrustStoreLocation(String trustStoreLocation) {
      this.trustStoreLocation = trustStoreLocation;
   }

   public String getTrustStorePassword() {
      return trustStorePassword;
   }

   public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
   }

   public String getStoreType() {
      return storeType;
   }

   public void setStoreType(String storeType) {
      this.storeType = storeType;
   }

   public String[] getEnabledCipherSuites() {
      return enabledCipherSuites;
   }

   public void setEnabledCipherSuites(String[] enabledCipherSuites) {
      this.enabledCipherSuites = enabledCipherSuites;
   }

   public String[] getDisabledCipherSuites() {
      return disabledCipherSuites;
   }

   public void setDisabledCipherSuites(String[] disabledCipherSuites) {
      this.disabledCipherSuites = disabledCipherSuites;
   }

   /**
    * {@return the enabledProtocols or null if the defaults should be used}
    */
   public String[] getEnabledProtocols() {
      return enabledProtocols;
   }

   /**
    * The protocols to be set as enabled.
    *
    * @param enabledProtocols the enabled protocols to set, or null if the defaults should be used.
    */
   public void setEnabledProtocols(String[] enabledProtocols) {
      this.enabledProtocols = enabledProtocols;
   }

   /**
    * {@return the protocols to disable or null if none should be}
    */
   public String[] getDisabledProtocols() {
      return disabledProtocols;
   }

   /**
    * The protocols to be disable.
    *
    * @param disabledProtocols the protocols to disable, or null if none should be.
    */
   public void setDisabledProtocols(String[] disabledProtocols) {
      this.disabledProtocols = disabledProtocols;
   }

   public String getContextProtocol() {
      return contextProtocol;
   }

   public void setContextProtocol(String contextProtocol) {
      this.contextProtocol = contextProtocol;
   }

   public boolean isTrustAll() {
      return trustAll;
   }

   public void setTrustAll(boolean trustAll) {
      this.trustAll = trustAll;
   }

   public boolean isVerifyHost() {
      return verifyHost;
   }

   public void setVerifyHost(boolean verifyHost) {
      this.verifyHost = verifyHost;
   }

   public String getKeyAlias() {
      return keyAlias;
   }

   public void setKeyAlias(String keyAlias) {
      this.keyAlias = keyAlias;
   }

   public int getDefaultSslPort() {
      return defaultSslPort;
   }

   public void setDefaultSslPort(int defaultSslPort) {
      this.defaultSslPort = defaultSslPort;
   }

   @Override
   public boolean isSSL() {
      return true;
   }

   @Override
   public NettyTransportSslOptions clone() {
      return copyOptions(new NettyTransportSslOptions());
   }

   protected NettyTransportSslOptions copyOptions(NettyTransportSslOptions copy) {
      super.copyOptions(copy);

      copy.setKeyStoreLocation(getKeyStoreLocation());
      copy.setKeyStorePassword(getKeyStorePassword());
      copy.setTrustStoreLocation(getTrustStoreLocation());
      copy.setTrustStorePassword(getTrustStorePassword());
      copy.setStoreType(getStoreType());
      copy.setEnabledCipherSuites(getEnabledCipherSuites());
      copy.setDisabledCipherSuites(getDisabledCipherSuites());
      copy.setEnabledProtocols(getEnabledProtocols());
      copy.setDisabledProtocols(getDisabledProtocols());
      copy.setTrustAll(isTrustAll());
      copy.setVerifyHost(isVerifyHost());
      copy.setKeyAlias(getKeyAlias());
      copy.setContextProtocol(getContextProtocol());
      return copy;
   }
}
