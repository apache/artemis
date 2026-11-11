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

import java.net.URI;
import java.text.ParseException;
import java.util.Collections;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.JWKSecurityContext;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything we need from {@code /.well-known/openid-configuration} endpoint - including the keys from
 * the {@code jwks_uri} endpoint.
 */
public class OIDCMetadata {

   public static final Logger LOG = LoggerFactory.getLogger(OIDCMetadata.class);

   private final long timestamp;
   private boolean recoverable;
   private boolean valid;

   private long lastKeysCheck = 0L;

   /**
    * Issuer field from the metadata - should actual match the issuer URL we got the metadata from
    */
   private String issuer = null;

   /**
    * URI of the JSON Web Key set (RFC 7517) from
    * <a href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenID Provider Metadata</a>
    * {@code jwks_uri} endpoint
    */
   private URI jwksURI;

   /**
    * Exception during metadata fetching
    */
   private Exception exception;

   /**
    * Error message from metadata processing - could be literal or from an exception
    */
   private String errorMessage;

   /**
    * {@link JWKSecurityContext} used for actual JWT validation/processing
    */
   private JWKSecurityContext currentContext = new JWKSecurityContext(Collections.emptyList());

   public OIDCMetadata(String expectedIssuer, JsonObject json) {
      this(expectedIssuer, json, true);
   }

   public OIDCMetadata(String expectedIssuer, JsonObject json, boolean recoverable) {
      this.timestamp = System.currentTimeMillis();
      this.recoverable = recoverable;
      valid = false;
      if (json != null) {
         // we have to get this
         JsonString issuerV = json.getJsonString("issuer");
         issuer = issuerV == null ? null : issuerV.getString();
         // we should get this - otherwise no token signature validation will be possible
         JsonString jwksUriV = json.getJsonString("jwks_uri");
         jwksURI = jwksUriV == null ? null : URI.create(jwksUriV.getString());

         if (issuer == null) {
            errorMessage = "OIDC Metadata issuer is missing";
            this.recoverable = false;
         } else if (!issuer.equals(expectedIssuer)) {
            errorMessage = "OIDC Metadata issuer mismatch";
            this.recoverable = false;
         } else {
            valid = true;
         }
      }
   }

   /**
    * Whether this metadata should be refreshed because of {@code metadataRetryTime} in seconds
    *
    * @param metadataRetryTime delay between this metadata should fetched again (in seconds)
    * @return {@code true} if the metadata should be fetched again
    */
   public boolean shouldFetchAgain(int metadataRetryTime) {
      return !valid && recoverable && System.currentTimeMillis() - timestamp >= metadataRetryTime * 1000L;
   }

   /**
    * Valid OIDC metadata contains {@code jwks_uri} which points to the endpoint from which we can get
    * a "JWK set" = an array of keys in the format specified in RFC 7517 (JSON Web Key (JWK)). While we don't expect
    * metadata to change (as of 2026-03-11), we're ready to refresh the signature keys sing a configured
    * cache time.
    *
    * @param cacheKeysTime delay between fetching the keys from {@code jwks_uri} endpoint
    * @return {@code true} if the keys should be fetched again from the provider's {@code jwks_uri} endpoint
    */
   public boolean shouldRefreshPublicKeys(int cacheKeysTime) {
      return valid && System.currentTimeMillis() - lastKeysCheck > cacheKeysTime * 1000L;
   }

   // ---- OIDC state access

   /**
    * Retrieve current instance of {@link JWKSecurityContext} with a list of {@link com.nimbusds.jose.jwk.JWK keys}
    * for JWT validation.
    *
    * @return {@link JWKSecurityContext} with currently known public keys
    */
   public JWKSecurityContext currentSecurityContext() {
      return currentContext;
   }

   public String getIssuer() {
      return issuer;
   }

   public URI getJwksURI() {
      return jwksURI;
   }

   public void configureKeys(String json) {
      lastKeysCheck = System.currentTimeMillis();
      try {
         JWKSet set = JWKSet.parse(json);
         currentContext = new JWKSecurityContext(set.getKeys());
      } catch (ParseException e) {
         LOG.warn("Failed to parse JWK JSON structure. No keys will be available for JWT signature validation: {}", e.getMessage());
         currentContext = new JWKSecurityContext(Collections.emptyList());
      }
   }

   // ---- Lower level state access

   public boolean isValid() {
      return valid;
   }

   public boolean isRecoverable() {
      return recoverable;
   }

   public OIDCMetadata withException(Exception issue) {
      exception = issue;
      errorMessage = issue.getMessage() == null ? issue.getClass().getName() : issue.getMessage();
      return this;
   }

   public Exception getException() {
      return exception;
   }

   public OIDCMetadata withErrorMessage(String message) {
      errorMessage = message;
      return this;
   }

   public String getErrorMessage() {
      return errorMessage;
   }
}
