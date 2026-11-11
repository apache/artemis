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
package org.apache.activemq.artemis.spi.core.security.jaas.oidc;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access class for {@link HttpClient} instances which keeps already created clients in a static
 * cache, but creates new clients using instance options passed from just-initialized
 * {@link javax.security.auth.spi.LoginModule}.
 */
public class SharedHttpClientAccess implements HttpClientAccess {

   public static final Logger LOG = LoggerFactory.getLogger(SharedHttpClientAccess.class);

   private static final Map<URI, HttpClient> cache = new ConcurrentHashMap<>();

   // ---- Config options from just-initialized JAAS LoginModule

   private final int httpTimeout;
   private final String tlsVersion;
   private final String caCertificate;
   private final boolean debug;

   public SharedHttpClientAccess(Map<String, ?> options, boolean debug) {
      this.httpTimeout = OIDCSupport.intOption(OIDCSupport.ConfigKey.HTTP_TIMEOUT_MILLISECONDS, options);
      this.tlsVersion = OIDCSupport.stringOption(OIDCSupport.ConfigKey.TLS_VERSION, options);
      this.caCertificate = OIDCSupport.stringOption(OIDCSupport.ConfigKey.CA_CERTIFICATE, options);
      this.debug = debug;
   }

   @Override
   public HttpClient getClient(URI baseURI) {
      HttpClient client = cache.get(baseURI);
      if (client == null) {
         synchronized (SharedHttpClientAccess.class) {
            client = cache.get(baseURI);
            if (client == null) {
               client = createDefaultHttpClient();
               if (debug) {
                  LOG.debug("Created new HTTP Client for accessing OIDC provider at {}", baseURI);
               }
               cache.put(baseURI, client);
            }
         }
      }

      return client;
   }

   /**
    * Create a slightly customized {@link HttpClient}
    *
    * @return newly created {@link HttpClient}
    */
   public HttpClient createDefaultHttpClient() {
      HttpClient.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(httpTimeout));

      boolean sslContextSet = false;
      if (tlsVersion != null && caCertificate != null) {
         try {
            File caCertificateFile = new File(caCertificate);
            if (!caCertificateFile.isFile()) {
               LOG.warn("The certificate file {} does not exist", caCertificate);
            } else {
               SSLContext sslContext = SSLContext.getInstance(tlsVersion);
               KeyStore trustStore = SSLSupport.loadKeystore(null, "PEMCA", caCertificate, null);
               TrustManagerFactory tmFactory = TrustManagerFactory
                     .getInstance(TrustManagerFactory.getDefaultAlgorithm());
               tmFactory.init(trustStore);
               sslContext.init(null, tmFactory.getTrustManagers(), new SecureRandom());
               builder.sslContext(sslContext);
               sslContextSet = true;
            }
         } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
         } catch (Exception e) {
            throw new RuntimeException("Can't configure SSL Context for HTTP Client", e);
         }
      }

      if (!sslContextSet) {
         try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, null, new SecureRandom());
            builder.sslContext(sslContext);
         } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Can't configure default SSL Context for HTTP Client", e);
         }
      }

      return builder.build();
   }

}
