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
package org.apache.activemq.artemis.core.remoting.impl.ssl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.handler.ssl.SslContext;
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactory;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;

/**
 * {@link OpenSSLContextFactory} providing a cache of {@link SslContext}.
 * Since {@link SslContext} should be reused instead of recreated and are thread safe.
 * To activate it you need to allow this Service to be discovered by having a
 * <code>META-INF/services/org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactory</code>
 * file with <code>org.apache.activemq.artemis.core.remoting.impl.ssl.CachingOpenSSLContextFactory</code>
 * as value.
 */
public class CachingOpenSSLContextFactory extends DefaultOpenSSLContextFactory {

   private final ConcurrentMap<SSLContextConfig, SslContext> clientSslContextCache = new ConcurrentHashMap<>(2);
   private final ConcurrentMap<SSLContextConfig, SslContext> serversSslContextCache = new ConcurrentHashMap<>(2);

   @Override
   public void clearSslContexts() {
      clientSslContextCache.clear();
      serversSslContextCache.clear();
   }

   @Override
   public SslContext getClientSslContext(final SSLContextConfig config, final Map<String, Object> additionalOpts) throws Exception {
      return clientSslContextCache.computeIfAbsent(config, this::getClientSslContext);
   }

   private SslContext getClientSslContext(final SSLContextConfig config) {
      try {
         return super.getClientSslContext(config, null);
      } catch (final Exception ex) {
         throw new RuntimeException("An unexpected exception occured while creating Client OpenSSL Context with " + config, ex);
      }
   }

   @Override
   public SslContext getServerSslContext(final SSLContextConfig config, final Map<String, Object> additionalOpts) throws Exception {
      return clientSslContextCache.computeIfAbsent(config, this::getServerSslContext);
   }

   private SslContext getServerSslContext(final SSLContextConfig config) {
      try {
         return super.getServerSslContext(config, null);
      } catch (final Exception ex) {
         throw new RuntimeException("An unexpected exception occured while creating Server OpenSSL Context " + config, ex);
      }
   }

   @Override
   public int getPriority() {
      return 10;
   }
}
