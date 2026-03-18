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
package org.apache.activemq.artemis.core.remoting.impl.ssl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLContext;

import io.netty.handler.ssl.SslContext;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactory;
import org.apache.activemq.artemis.utils.ConfigurationHelper;

/**
 * {@link SSLContextFactory} providing a cache of {@link SSLContext}. Since {@link SSLContext} should be reused instead
 * of recreated and are thread safe. To activate it you need to allow this Service to be discovered by having a
 * {@code META-INF/services/org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactory} file with
 * {@code org.apache.activemq.artemis.core.remoting.impl.ssl.CachingSSLContextFactory} as value.
 */
public class CachingSSLContextFactory extends DefaultSSLContextFactory {

   private static final ConcurrentMap<Object, SSLContext> sslContextCache = new ConcurrentHashMap<>(2);

   @Override
   public void clearSSLContexts() {
      sslContextCache.clear();
   }

   @Override
   public SSLContext getSSLContext(final SSLContextConfig config, final Map<String, Object> additionalOpts) throws Exception {
      final Object cacheKey = getCacheKey(config, additionalOpts);
      return sslContextCache.computeIfAbsent(cacheKey, key -> {
         try {
            return CachingSSLContextFactory.super.getSSLContext(config, additionalOpts);
         } catch (final Exception ex) {
            throw new RuntimeException("An unexpected exception occured while creating JDK SSLContext with " + config, ex);
         }
      });
   }

   /**
    * Obtains/calculates a cache key for the corresponding {@link SslContext}.
    * <ol>
    * <li>If {@code config} contains an entry with key "sslContext", the associated value is returned
    * <li>Otherwise, the provided {@link SSLContextConfig} is used as cache key.
    * </ol>
    *
    * @return the SSL context name to cache/retrieve the {@link SslContext}
    */
   protected Object getCacheKey(final SSLContextConfig config, final Map<String, Object> additionalOpts) {
      final Object cacheKey = ConfigurationHelper.getStringProperty(TransportConstants.SSL_CONTEXT_PROP_NAME, null, additionalOpts);
      if (cacheKey != null) {
         return cacheKey;
      }

      return config;
   }

   @Override
   public int getPriority() {
      return 10;
   }
}
