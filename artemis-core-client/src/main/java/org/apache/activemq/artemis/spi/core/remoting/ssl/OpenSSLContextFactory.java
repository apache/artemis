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

import java.util.Map;

import io.netty.handler.ssl.SslContext;

/**
 * Service interface to create an {@link SslContext} for a configuration. This is ONLY used with OpenSSL. To create and
 * use your own implementation you need to create a file
 * {@code META-INF/services/org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactory} in your jar and
 * fill it with the full qualified name of your implementation.
 */
public interface OpenSSLContextFactory extends Comparable<OpenSSLContextFactory> {

   /**
    * Release any cached {@link SslContext} instances.
    */
   default void clearSslContexts() {
   }

   @Override
   default int compareTo(final OpenSSLContextFactory other) {
      return this.getPriority() - other.getPriority();
   }

   /**
    * @param additionalOpts implementation specific additional options.
    * @return an {@link SslContext} instance for the given configuration
    */
   SslContext getClientSslContext(SSLContextConfig config, Map<String, Object> additionalOpts) throws Exception;

   /**
    * @param additionalOpts implementation specific additional options.
    * @return an {@link SslContext} instance for the given configuration
    */
   SslContext getServerSslContext(SSLContextConfig config, Map<String, Object> additionalOpts) throws Exception;

   /**
    * The priority for the {@link OpenSSLContextFactory} when resolving the service to get the implementation. This is
    * used when selecting the implementation when several implementations are loaded. The highest priority
    * implementation will be used.
    */
   int getPriority();
}
