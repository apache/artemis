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
package org.apache.activemq.artemis.spi.core.remoting.ssl;

import java.util.Objects;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;

/**
 * This class holds configuration parameters for SSL context initialization.
 * To be used with {@link SSLContextFactory} and {@link OpenSSLContextFactory}.
 * <br>
 * Use {@link SSLContextConfig#builder()} to create new immutable instances.
 */
public final class SSLContextConfig {

   public static final class Builder {
      private String keystorePath = TransportConstants.DEFAULT_KEYSTORE_PATH;
      private String keystoreType = TransportConstants.DEFAULT_KEYSTORE_TYPE;
      private String keystorePassword = TransportConstants.DEFAULT_KEYSTORE_PASSWORD;
      private String keystoreProvider = TransportConstants.DEFAULT_KEYSTORE_PROVIDER;
      private String truststorePath = TransportConstants.DEFAULT_TRUSTSTORE_PATH;
      private String truststoreType = TransportConstants.DEFAULT_TRUSTSTORE_TYPE;
      private String truststorePassword = TransportConstants.DEFAULT_TRUSTSTORE_PASSWORD;
      private String truststoreProvider = TransportConstants.DEFAULT_TRUSTSTORE_PROVIDER;
      private String crlPath = TransportConstants.DEFAULT_CRL_PATH;
      private String trustManagerFactoryPlugin = TransportConstants.DEFAULT_TRUST_MANAGER_FACTORY_PLUGIN;
      private boolean trustAll = TransportConstants.DEFAULT_TRUST_ALL;

      private Builder() {
      }

      public Builder from(final SSLContextConfig config) {
         if (config == null)
            return this;

         keystorePath = config.getKeystorePath();
         keystoreType = config.getKeystoreType();
         keystorePassword = config.getKeystorePassword();
         keystoreProvider = config.getKeystoreProvider();
         truststorePath = config.getTruststorePath();
         truststoreType = config.getTruststoreType();
         truststorePassword = config.getTruststorePassword();
         crlPath = config.getCrlPath();
         truststoreProvider = config.getTruststoreProvider();
         trustAll = config.trustAll;
         return this;
      }

      public SSLContextConfig build() {
         return new SSLContextConfig(
            keystoreProvider, keystorePath, keystoreType, keystorePassword,
            truststoreProvider, truststorePath, truststoreType, truststorePassword,
            crlPath, trustManagerFactoryPlugin, trustAll
         );
      }

      public Builder keystorePath(final String keystorePath) {
         this.keystorePath = keystorePath;
         return this;
      }

      public Builder keystoreType(final String keystoreType) {
         this.keystoreType = keystoreType;
         return this;
      }

      public Builder keystorePassword(final String keystorePassword) {
         this.keystorePassword = keystorePassword;
         return this;
      }

      public Builder keystoreProvider(final String keystoreProvider) {
         this.keystoreProvider = keystoreProvider;
         return this;
      }

      public Builder truststorePath(final String truststorePath) {
         this.truststorePath = truststorePath;
         return this;
      }

      public Builder truststoreType(final String truststoreType) {
         this.truststoreType = truststoreType;
         return this;
      }

      public Builder truststorePassword(final String truststorePassword) {
         this.truststorePassword = truststorePassword;
         return this;
      }

      public Builder truststoreProvider(final String truststoreProvider) {
         this.truststoreProvider = truststoreProvider;
         return this;
      }

      public Builder crlPath(final String crlPath) {
         this.crlPath = crlPath;
         return this;
      }

      public Builder trustAll(final boolean trustAll) {
         this.trustAll = trustAll;
         return this;
      }

      public Builder trustManagerFactoryPlugin(final String trustManagerFactoryPlugin) {
         this.trustManagerFactoryPlugin = trustManagerFactoryPlugin;
         return this;
      }
   }

   public static  Builder builder() {
      return new Builder();
   }

   private final String keystorePath;
   private final String keystoreType;
   private final String keystorePassword;
   private final String keystoreProvider;
   private final String truststorePath;
   private final String truststoreType;
   private final String truststorePassword;
   private final String truststoreProvider;
   private final String trustManagerFactoryPlugin;
   private final String crlPath;
   private final boolean trustAll;
   private final int hashCode;

   private SSLContextConfig(final String keystoreProvider,
                            final String keystorePath,
                            final String keystoreType,
                            final String keystorePassword,
                            final String truststoreProvider,
                            final String truststorePath,
                            final String truststoreType,
                            final String truststorePassword,
                            final String crlPath,
                            final String trustManagerFactoryPlugin,
                            final boolean trustAll) {
      this.keystorePath = keystorePath;
      this.keystoreType = keystoreType;
      this.keystoreProvider = keystoreProvider;
      this.keystorePassword = keystorePassword;
      this.truststorePath = truststorePath;
      this.truststoreType = truststoreType;
      this.truststorePassword = truststorePassword;
      this.truststoreProvider = truststoreProvider;
      this.trustManagerFactoryPlugin = trustManagerFactoryPlugin;
      this.crlPath = crlPath;
      this.trustAll = trustAll;
      hashCode = Objects.hash(
         keystorePath, keystoreType, keystoreProvider,
         truststorePath, truststoreType, truststoreProvider,
         crlPath, trustManagerFactoryPlugin, trustAll
      );
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj)
         return true;
      if (obj == null || getClass() != obj.getClass())
         return false;
      final SSLContextConfig other = (SSLContextConfig) obj;
      return Objects.equals(keystorePath, other.keystorePath)
         && Objects.equals(keystoreType, other.keystoreType)
         && Objects.equals(keystoreProvider, other.keystoreProvider)
         && Objects.equals(truststorePath, other.truststorePath)
         && Objects.equals(truststoreType, other.truststoreType)
         && Objects.equals(truststoreProvider, other.truststoreProvider)
         && Objects.equals(crlPath, other.crlPath)
         && Objects.equals(trustManagerFactoryPlugin, other.trustManagerFactoryPlugin)
         && trustAll == other.trustAll;
   }

   public String getCrlPath() {
      return crlPath;
   }

   public String getKeystorePassword() {
      return keystorePassword;
   }

   public String getKeystorePath() {
      return keystorePath;
   }

   public String getKeystoreType() {
      return keystoreType;
   }

   public String getKeystoreProvider() {
      return keystoreProvider;
   }

   public String getTrustManagerFactoryPlugin() {
      return trustManagerFactoryPlugin;
   }

   public String getTruststorePassword() {
      return truststorePassword;
   }

   public String getTruststorePath() {
      return truststorePath;
   }

   public String getTruststoreType() {
      return truststoreType;
   }

   public String getTruststoreProvider() {
      return truststoreProvider;
   }

   @Override
   public int hashCode() {
      return hashCode;
   }

   public boolean isTrustAll() {
      return trustAll;
   }

   @Override
   public String toString() {
      return "SSLSupport [" +
         "keystoreProvider=" + keystoreProvider +
         ", keystorePath=" + keystorePath +
         ", keystoreType=" + keystoreType +
         ", keystorePassword=" + (keystorePassword == null ? null : "******") +
         ", truststoreProvider=" + truststoreProvider +
         ", truststorePath=" + truststorePath +
         ", truststoreType=" + truststoreType +
         ", truststorePassword=" + (truststorePassword == null ? null : "******") +
         ", crlPath=" + crlPath +
         ", trustAll=" + trustAll +
         ", trustManagerFactoryPlugin=" + trustManagerFactoryPlugin +
         "]";
   }
}
