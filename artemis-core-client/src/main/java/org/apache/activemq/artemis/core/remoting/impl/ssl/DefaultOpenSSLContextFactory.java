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

import java.lang.invoke.MethodHandles;
import java.util.Map;

import io.netty.handler.ssl.SslContext;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactory;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link OpenSSLContextFactory} for use in {@link NettyConnector} and NettyAcceptor.
 */
public class DefaultOpenSSLContextFactory implements OpenSSLContextFactory {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * @param additionalOpts not used by this implementation
    * @return an {@link SslContext} instance for the given configuration
    */
   @Override
   public SslContext getClientSslContext(final SSLContextConfig config, final Map<String, Object> additionalOpts) throws Exception {
      logger.debug("Creating Client OpenSSL Context with {}", config);
      return new SSLSupport(config)
         .setSslProvider(TransportConstants.OPENSSL_PROVIDER)
         .createNettyClientContext();
   }

   /**
    * @param additionalOpts not used by this implementation
    * @return an {@link SslContext} instance for the given configuration
    */
   @Override
   public SslContext getServerSslContext(final SSLContextConfig config, final Map<String, Object> additionalOpts) throws Exception {
      logger.debug("Creating Server OpenSSL Context with {}", config);
      return new SSLSupport(config)
         .setSslProvider(TransportConstants.OPENSSL_PROVIDER)
         .createNettyContext();
   }

   @Override
   public int getPriority() {
      return 5;
   }
}
