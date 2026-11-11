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
package org.apache.activemq.artemis.spi.core.remoting.ssl;

import java.util.Map;
import javax.net.ssl.SSLContext;

/**
 * Service interface to create a SSLContext for a configuration. This is NOT used by OpenSSL. To create and use your own
 * implementation you need to create a file
 * {@code META-INF/services/org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactory} in your jar and fill it
 * with the full qualified name of your implementation.
 */
public interface SSLContextFactory extends Comparable<SSLContextFactory> {

   /**
    * @return an {@link SSLContext} for the given configuration
    *
    * @deprecated use {@link #getSSLContext(SSLContextConfig, Map)} instead
    */
   @SuppressWarnings("unused")
   @Deprecated
   default SSLContext getSSLContext(Map<String, Object> configuration,
           String keystoreProvider, String keystorePath, String keystoreType, String keystorePassword,
           String truststoreProvider, String truststorePath, String truststoreType, String truststorePassword,
           String crlPath, String trustManagerFactoryPlugin, boolean trustAll) throws Exception {

      final SSLContextConfig sslContextConfig = SSLContextConfig.builder()
         .keystoreProvider(keystoreProvider)
         .keystorePath(keystorePath)
         .keystoreType(keystoreType)
         .keystorePassword(keystorePassword)
         .truststoreProvider(truststoreProvider)
         .truststorePath(truststorePath)
         .truststorePassword(truststorePassword)
         .trustManagerFactoryPlugin(trustManagerFactoryPlugin)
         .crlPath(crlPath)
         .build();

      return getSSLContext(sslContextConfig, configuration);
   }

   /**
    * @param additionalOpts implementation specific additional options.
    *
    * @return an {@link SSLContext} for the given configuration
    */
   default SSLContext getSSLContext(SSLContextConfig config, Map<String, Object> additionalOpts) throws Exception {
      return getSSLContext(additionalOpts,
         config.getKeystoreProvider(), config.getKeystorePath(), config.getKeystoreType(), config.getKeystorePassword(),
         config.getTruststoreProvider(), config.getTruststorePath(), config.getTruststoreType(), config.getTruststorePassword(),
         config.getCrlPath(), config.getTrustManagerFactoryPlugin(), config.isTrustAll()
      );
   }

   default void clearSSLContexts() {
   }

   /**
    * The priority for the SSLContextFactory when resolving the service to get the implementation. This is used when
    * selecting the implementation when several implementations are loaded. The highest priority implementation will be
    * used.
    *
    * @return the priority
    */
   int getPriority();

   @Override
   default int compareTo(SSLContextFactory other) {
      return this.getPriority() - other.getPriority();
   }
}
