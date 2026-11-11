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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access class for {@link OIDCMetadata} which uses a static cache - necessary because of how
 * the lifecycle of {@link javax.security.auth.login.LoginContext} is designed.
 * Additionally public keys for the metadata may be refreshed using configurable validity time.
 */
public class SharedOIDCMetadataAccess implements OIDCMetadataAccess {

   public static final Logger LOG = LoggerFactory.getLogger(SharedOIDCMetadataAccess.class);

   static final Map<URI, OIDCMetadata> cache = new ConcurrentHashMap<>();

   private final HttpClientAccess httpClientAccess;

   private final boolean debug;
   private final int cacheKeysTime;
   private final int metadataRetryTime;
   private final int httpTimeout;

   public SharedOIDCMetadataAccess(HttpClientAccess httpClientAccess, Map<String, ?> options, boolean debug) {
      this.httpClientAccess = httpClientAccess;
      this.debug = debug;

      this.cacheKeysTime = OIDCSupport.intOption(OIDCSupport.ConfigKey.CACHE_KEYS_TIME_SECONDS, options);
      this.metadataRetryTime = OIDCSupport.intOption(OIDCSupport.ConfigKey.METADATA_RETRY_TIME_SECONDS, options);
      this.httpTimeout = OIDCSupport.intOption(OIDCSupport.ConfigKey.HTTP_TIMEOUT_MILLISECONDS, options);
   }

   @Override
   public OIDCMetadata getMetadata(URI baseURI) {
      OIDCMetadata metadata = cache.get(baseURI);
      if (metadata == null || metadata.shouldFetchAgain(metadataRetryTime)) {
         synchronized (SharedOIDCMetadataAccess.class) {
            metadata = fetchOIDCMetadata(baseURI);
            cache.put(baseURI, metadata);
         }
      }

      if (metadata.shouldRefreshPublicKeys(cacheKeysTime)) {
         // we don't want the metadata to deal with HTTP client
         if (metadata.getJwksURI() != null) {
            metadata.configureKeys(fetchJwkSet(baseURI, metadata.getJwksURI()));
         }
      }

      return metadata;
   }

   /**
    * Actually fetch JSON metadata from {@code /.well-known/openid-configuration} and build {@link OIDCMetadata}
    *
    * @return {@link OIDCMetadata} for given provider's base URI
    */
   private OIDCMetadata fetchOIDCMetadata(URI baseURI) {
      HttpClient client = httpClientAccess.getClient(baseURI);

      String expectedIssuer = baseURI.toString();
      OIDCMetadata result;

      try {
         URI metadataURI = new URI(baseURI + "/.well-known/openid-configuration");

         HttpRequest request = HttpRequest.newBuilder().GET()
               .timeout(Duration.ofMillis(httpTimeout))
               .uri(metadataURI).build();
         if (debug) {
            LOG.debug("Fetching OIDC Metadata from {}", metadataURI);
         }
         HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

         int statusCode = response.statusCode();
         if (statusCode == 200) {
            // separate try..catch because we actually start JSON parsing
            InputStream body = response.body();
            if (body != null) {
               try (body) {
                  JsonObject json = JsonLoader.readObject(new InputStreamReader(body));
                  result = new OIDCMetadata(expectedIssuer, json);
               } catch (IllegalStateException e) {
                  // when the body is empty
                  result = new OIDCMetadata(expectedIssuer, null, false).withErrorMessage("No OIDC metadata available");
               } catch (ClassCastException e) {
                  // we need to catch it because there's no generic "read" method in org.apache.activemq.artemis.utils.JsonLoader
                  // and we don't have access to javax.json.spi.JsonProvider
                  result = new OIDCMetadata(expectedIssuer, null, false).withErrorMessage("OIDC metadata invalid - JSON error");
               } catch (RuntimeException e) {
                  // hmm, we explicitly do not have access to javax.json library (optional in artemis-commons)
                  // so we have to be clever here
                  if (e.getClass().getName().startsWith("javax.json")
                        || e.getClass().getName().startsWith("org.apache.activemq.artemis.commons.shaded.json")) {
                     // we can assume it's a parsing exception
                     result = new OIDCMetadata(expectedIssuer, null, false).withErrorMessage("OIDC metadata invalid - JSON error");
                  } else {
                     // well - generic issue, can't do much
                     result = new OIDCMetadata(expectedIssuer, null, false).withException(e);
                  }
               }
            } else {
               result = new OIDCMetadata(expectedIssuer, null, false).withErrorMessage("No OIDC metadata available");
            }
         } else {
            LOG.warn("OIDC Metadata cannot be retrieved: HTTP status code {}", statusCode);
            boolean recoverable = statusCode < 400 || statusCode >= 500;
            result = new OIDCMetadata(expectedIssuer, null, recoverable).withErrorMessage("HTTP " + statusCode + " error when fetching OIDC metadata");
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         result = new OIDCMetadata(expectedIssuer, null).withException(e);
      } catch (ConnectException | /*HttpConnectTimeoutException | */ HttpTimeoutException connectionException) {
         // ConnectException: server not responding
         // HttpConnectTimeoutException: connection timeout
         // HttpTimeoutException: read timeout
         result = new OIDCMetadata(expectedIssuer, null).withException(connectionException);
      } catch (URISyntaxException | ProtocolException e) {
         // URISyntaxException: can't actually happen, because it would happen earlier, not when using
         // baseURI + /.well-known
         // ProtocolException: non-HTTP server responding
         result = new OIDCMetadata(expectedIssuer, null, false).withException(e);
      } catch (IOException e) {
         // IOException: any other I/O issue
         result = new OIDCMetadata(expectedIssuer, null).withException(e);
      }

      return result;
   }

   /**
    * Retrieve JSON definition of JWK Set (JSON structure defined in RFC 7517) to be parsed later by Nimbus library
    * (that's why we don't return {@link JsonObject}.
    *
    * @param baseURI base URI for the provider
    * @param jwksURI {@code jwks_uri} endpoint from OIDC metadata
    * @return String representation of the JWK (RFC 7517) JSON to be parsed by Nimbus Jose JWT library
    */
   private String fetchJwkSet(URI baseURI, URI jwksURI) {
      HttpClient client = httpClientAccess.getClient(baseURI);

      boolean jsonError = false;
      String errorMessage = null;
      Exception otherException = null;
      try {
         HttpRequest request = HttpRequest.newBuilder().GET()
               .timeout(Duration.ofMillis(httpTimeout))
               .uri(jwksURI).build();
         if (debug) {
            LOG.debug("Fetching JWK set from {}", jwksURI);
         }
         HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

         int statusCode = response.statusCode();
         if (statusCode == 200) {
            String body = response.body();
            if (body != null) {
               // it'll be passed to
               return body;
            } else {
               jsonError = true;
            }
         } else {
            errorMessage = "HTTP " + statusCode + " error when fetching public keys from " + jwksURI;
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         otherException = e;
      } catch (Exception e) {
         otherException = e;
      }

      if (errorMessage == null) {
         errorMessage = otherException != null ? otherException.getMessage() : null;
      }
      if (jsonError) {
         LOG.warn("Error processing JSON definition of keys from {}: {}", jwksURI, errorMessage);
      } else {
         LOG.warn("Error retrieving keys from {}: {}", jwksURI, errorMessage);
      }

      return "{}";
   }

}
