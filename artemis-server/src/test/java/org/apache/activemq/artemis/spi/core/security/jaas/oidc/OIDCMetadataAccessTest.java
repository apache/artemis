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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for getting OpenID Connect metadata under various scenarios and HTTP/connection issues.
 */
public class OIDCMetadataAccessTest {

   private HttpClient client;
   private HttpResponse<InputStream> notFoundResponse;

   private SharedOIDCMetadataAccess access;

   @SuppressWarnings("unchecked")
   private static HttpResponse.BodyHandler<InputStream> anyHandler() {
      return any(HttpResponse.BodyHandler.class);
   }

   // ---- Warming up

   @BeforeEach
   @SuppressWarnings("unchecked")
   public void setup() {
      client = mock(HttpClient.class);

      notFoundResponse = mock(HttpResponse.class);
      when(notFoundResponse.body()).thenReturn(new ByteArrayInputStream("Not Found".getBytes()));
      when(notFoundResponse.statusCode()).thenReturn(404);

      SharedOIDCMetadataAccess.cache.clear();

      access = new SharedOIDCMetadataAccess(baseURI -> client, Map.of(
            OIDCSupport.ConfigKey.METADATA_RETRY_TIME_SECONDS.getName(), "10"
      ), true);
   }

   // ---- Transport failure scenarios

   @Test
   @SuppressWarnings("unchecked")
   public void gettingFreshMetadata() throws IOException, InterruptedException {
      HttpHeaders headers = HttpHeaders.of(Map.of(
            "Content-Type", Collections.singletonList("application/json")
      ), (n, v) -> true);
      HttpResponse<InputStream> emptyJSON = mock(HttpResponse.class);
      when(emptyJSON.body()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
      when(emptyJSON.headers()).thenReturn(headers);
      when(emptyJSON.statusCode()).thenReturn(200);

      HttpResponse<InputStream> partialMetadataJSON = mock(HttpResponse.class);
      when(partialMetadataJSON.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8083\"}".getBytes()));
      when(partialMetadataJSON.headers()).thenReturn(headers);
      when(partialMetadataJSON.statusCode()).thenReturn(200);

      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest r = inv.getArgument(0, HttpRequest.class);
         if (r.uri().getPath().equals("/.well-known/openid-configuration")) {
            if (r.uri().getPort() == 8082) {
               return emptyJSON;
            }
            if (r.uri().getPort() == 8083) {
               return partialMetadataJSON;
            }
            return emptyJSON;
         }

         return notFoundResponse;
      });

      OIDCMetadata metadata1 = access.getMetadata(URI.create("http://localhost:8082"));
      OIDCMetadata metadata2 = access.getMetadata(URI.create("http://localhost:8082"));
      OIDCMetadata metadata3 = access.getMetadata(URI.create("http://localhost:8083"));

      assertSame(metadata1, metadata2);
      assertNotSame(metadata1, metadata3);

      assertNull(metadata1.getIssuer());
      assertEquals("http://localhost:8083", metadata3.getIssuer());
   }

   @Test
   public void connectionRefused() throws IOException, InterruptedException {
      // simply connecting to a non listening server
      when(client.send(any(HttpRequest.class), anyHandler())).thenThrow(new ConnectException("Connection refused"));

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertEquals("Connection refused", metadata.getErrorMessage());
      assertTrue("Time to fetch again", metadata.shouldFetchAgain(0));
   }

   @Test
   public void connectionTimeout() throws IOException, InterruptedException {
      // connection timeout - hard to simulate, but see https://en.wikipedia.org/wiki/List_of_reserved_IP_addresses
      // there's special 192.0.2.0/24 which _should_ give you connection timeout
      when(client.send(any(HttpRequest.class), anyHandler())).thenThrow(new HttpConnectTimeoutException("Connection timeout"));

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertEquals("Connection timeout", metadata.getErrorMessage());
      assertTrue("Time to fetch again", metadata.shouldFetchAgain(0));
   }

   @Test
   public void readTimeout() throws IOException, InterruptedException {
      // simulating read timeouts:
      // $ socat -v TCP-LISTEN:8080,reuseaddr,fork EXEC:'sleep 30'
      when(client.send(any(HttpRequest.class), anyHandler())).thenThrow(new HttpTimeoutException("Read timeout"));

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertEquals("Read timeout", metadata.getErrorMessage());
      assertTrue("Time to fetch again", metadata.shouldFetchAgain(0));
   }

   @Test
   public void ioError() throws IOException, InterruptedException {
      when(client.send(any(HttpRequest.class), anyHandler())).thenThrow(new IOException("Something's wrong"));

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertEquals("Something's wrong", metadata.getErrorMessage());
      assertTrue("Time to fetch again", metadata.shouldFetchAgain(0));
   }

   // ---- HTTP failure scenarios

   @Test
   public void noHTTP() throws IOException, InterruptedException {
      // simulating non-HTTP server:
      // $ socat -v TCP-LISTEN:1234,reuseaddr,fork EXEC:'echo HELLO'
      when(client.send(any(HttpRequest.class), anyHandler())).thenThrow(new ProtocolException("Invalid status line: \"HELLO\""));

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertEquals("Invalid status line: \"HELLO\"", metadata.getErrorMessage());
      assertFalse("No time to fetch again - because this looks like unrecoverable error", metadata.shouldFetchAgain(0));
      assertFalse(metadata.isRecoverable());
   }

   @Test
   public void http40x() throws IOException, InterruptedException {
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> notFoundResponse);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertTrue(metadata.getErrorMessage().contains("404"));
      assertFalse("404 is not expected to change - let's treat as unrecoverable", metadata.shouldFetchAgain(0));
   }

   // ---- Low level OIDC metadata problems (empty HTTP body when HTTP status=200 or JSON parsing issues)

   @Test
   @SuppressWarnings("unchecked")
   public void http50x() throws IOException, InterruptedException {
      HttpResponse<InputStream> response50x = mock(HttpResponse.class);
      when(response50x.statusCode()).thenReturn(503);
      HttpResponse<InputStream> finalResponse50x1 = response50x;
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> finalResponse50x1);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertTrue(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertTrue(metadata.getErrorMessage().contains("503"));
      assertTrue("50x can hopefully change at some point", metadata.shouldFetchAgain(0));

      response50x = mock(HttpResponse.class);
      when(response50x.statusCode()).thenReturn(500);
      HttpResponse<InputStream> finalResponse50x2 = response50x;
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> finalResponse50x2);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertTrue(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertTrue(metadata.getErrorMessage().contains("500"));
      assertTrue("50x can hopefully change at some point", metadata.shouldFetchAgain(0));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void emptyOrBlankResponse() throws IOException, InterruptedException {
      final HttpResponse<InputStream> r1 = mock(HttpResponse.class);
      when(r1.statusCode()).thenReturn(200);
      when(r1.body()).thenReturn(null);
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r1);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon - we can simply have bad provider URL", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("No OIDC metadata available"));

      final HttpResponse<InputStream> r2 = mock(HttpResponse.class);
      when(r2.statusCode()).thenReturn(200);
      when(r2.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r2);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon - we can simply have bad provider URL", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("No OIDC metadata available"));

      final HttpResponse<InputStream> r3 = mock(HttpResponse.class);
      when(r3.statusCode()).thenReturn(200);
      when(r3.body()).thenReturn(new ByteArrayInputStream("   ".getBytes()));
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r3);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon - we can simply have bad provider URL", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("No OIDC metadata available"));

      final HttpResponse<InputStream> r4 = mock(HttpResponse.class);
      when(r4.statusCode()).thenReturn(200);
      when(r4.body()).thenReturn(new ByteArrayInputStream("\n\t \n\t".getBytes()));
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r4);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon - we can simply have bad provider URL", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("No OIDC metadata available"));
   }

   // ---- Proper JSON, but missing/invalid OIDC metadata

   @Test
   @SuppressWarnings("unchecked")
   public void invalidJSON() throws IOException, InterruptedException {
      final HttpResponse<InputStream> r1 = mock(HttpResponse.class);
      when(r1.statusCode()).thenReturn(200);
      when(r1.body()).thenReturn(new ByteArrayInputStream("{".getBytes()));
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r1);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("OIDC metadata invalid - JSON error"));

      final HttpResponse<InputStream> r2 = mock(HttpResponse.class);
      when(r2.statusCode()).thenReturn(200);
      when(r2.body()).thenReturn(new ByteArrayInputStream("\"\"".getBytes()));
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r2);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("OIDC metadata invalid - JSON error"));

      final HttpResponse<InputStream> r3 = mock(HttpResponse.class);
      when(r3.statusCode()).thenReturn(200);
      when(r3.body()).thenReturn(new ByteArrayInputStream("42".getBytes()));
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r3);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("OIDC metadata invalid - JSON error"));

      final HttpResponse<InputStream> r4 = mock(HttpResponse.class);
      when(r4.statusCode()).thenReturn(200);
      when(r4.body()).thenReturn(new ByteArrayInputStream("[42]".getBytes()));
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r4);

      metadata = access.getMetadata(URI.create("http://localhost:8081"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertNull(metadata.getException());
      assertFalse("JSON errors can't be fixed any time soon", metadata.shouldFetchAgain(0));
      assertTrue(metadata.getErrorMessage().contains("OIDC metadata invalid - JSON error"));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void emptyJSONObject() throws IOException, InterruptedException {
      final HttpResponse<InputStream> r1 = mock(HttpResponse.class);
      when(r1.statusCode()).thenReturn(200);
      when(r1.body()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r1);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertFalse("Missing issuer in the OIDC metadata - not expected to change anytime soon", metadata.shouldFetchAgain(0));
      assertNull(metadata.getException());
      assertTrue(metadata.getErrorMessage().contains("OIDC Metadata issuer is missing"));
   }

   // ---- Proper JSON, checking keys

   @Test
   @SuppressWarnings("unchecked")
   public void differentIssuer() throws IOException, InterruptedException {
      final HttpResponse<InputStream> r1 = mock(HttpResponse.class);
      when(r1.statusCode()).thenReturn(200);
      when(r1.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8081\"}".getBytes()));
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r1);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertFalse(metadata.isValid());
      assertFalse(metadata.isRecoverable());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertFalse("No need to fetch again", metadata.shouldFetchAgain(0));
      assertNull(metadata.getException());
      assertNotNull(metadata.getErrorMessage());
   }

   @Test
   @SuppressWarnings("unchecked")
   public void problematicKeys() throws IOException, InterruptedException {
      access = new SharedOIDCMetadataAccess(baseURI -> client, Map.of(
            OIDCSupport.ConfigKey.CACHE_KEYS_TIME_SECONDS.getName(), "0"
      ), true);

      // 404 fetching the keys

      final HttpResponse<InputStream> r1 = mock(HttpResponse.class);
      when(r1.statusCode()).thenReturn(200);
      when(r1.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<InputStream> keys = mock(HttpResponse.class);
      when(keys.statusCode()).thenReturn(404);
      when(keys.body()).thenReturn(null);
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         if (argument.uri().toString().equals("http://localhost:8080/.well-known/openid-configuration")) {
            return r1;
         }
         return notFoundResponse;
      });

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertFalse("No need to fetch again", metadata.shouldFetchAgain(0));
      assertNull(metadata.getException());
      assertTrue(metadata.currentSecurityContext().getKeys().isEmpty());

      // Bad JSON when fetching the keys

      final HttpResponse<InputStream> r2 = mock(HttpResponse.class);
      when(r2.statusCode()).thenReturn(200);
      when(r2.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<String> keys2 = mock(HttpResponse.class);
      when(keys2.statusCode()).thenReturn(200);
      when(keys2.body()).thenReturn("{");
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         return switch (argument.uri().toString()) {
            case "http://localhost:8080/.well-known/openid-configuration" -> r2;
            case "http://localhost:8080/keys" -> keys2;
            default -> notFoundResponse;
         };
      });

      metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertTrue(metadata.currentSecurityContext().getKeys().isEmpty());

      // Empty JSON when fetching the keys

      final HttpResponse<InputStream> r3 = mock(HttpResponse.class);
      when(r3.statusCode()).thenReturn(200);
      when(r3.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<String> keys3 = mock(HttpResponse.class);
      when(keys3.statusCode()).thenReturn(200);
      when(keys3.body()).thenReturn("{}");
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         return switch (argument.uri().toString()) {
            case "http://localhost:8080/.well-known/openid-configuration" -> r3;
            case "http://localhost:8080/keys" -> keys3;
            default -> notFoundResponse;
         };
      });

      metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertTrue(metadata.currentSecurityContext().getKeys().isEmpty());

      // Empty key array

      final HttpResponse<InputStream> r4 = mock(HttpResponse.class);
      when(r4.statusCode()).thenReturn(200);
      when(r4.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<String> keys4 = mock(HttpResponse.class);
      when(keys4.statusCode()).thenReturn(200);
      when(keys4.body()).thenReturn("{\"keys\":[]}");
      req = any(HttpRequest.class);
      res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         return switch (argument.uri().toString()) {
            case "http://localhost:8080/.well-known/openid-configuration" -> r4;
            case "http://localhost:8080/keys" -> keys4;
            default -> notFoundResponse;
         };
      });

      metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertTrue(metadata.currentSecurityContext().getKeys().isEmpty());
   }

   @Test
   @SuppressWarnings("unchecked")
   public void noKeys() throws IOException, InterruptedException {
      final HttpResponse<InputStream> r1 = mock(HttpResponse.class);
      when(r1.statusCode()).thenReturn(200);
      when(r1.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\"}".getBytes()));
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> r1);

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertFalse("No time to fetch again yet", metadata.shouldFetchAgain(10));
      assertFalse("No need to fetch again", metadata.shouldFetchAgain(0));
      assertNull(metadata.getException());
      assertTrue(metadata.currentSecurityContext().getKeys().isEmpty());
   }

   @Test
   @SuppressWarnings("unchecked")
   public void oneSimpleRSAPublicKeyWithoutID() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, JOSEException {
      KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
      rsaKpg.initialize(2048);
      KeyPair rsaKeyPair = rsaKpg.generateKeyPair();
      RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
      RSAPublicKey rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

      RSAKey rsaPublicJWK = new RSAKey.Builder(rsaPublicKey).keyUse(KeyUse.SIGNATURE).build();
      String rsaPublicJson = rsaPublicJWK.toJSONString();

      final HttpResponse<InputStream> r = mock(HttpResponse.class);
      when(r.statusCode()).thenReturn(200);
      when(r.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<String> keys = mock(HttpResponse.class);
      when(keys.statusCode()).thenReturn(200);
      when(keys.body()).thenReturn("{\"keys\":[" + rsaPublicJson + "]}");
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         return switch (argument.uri().toString()) {
            case "http://localhost:8080/.well-known/openid-configuration" -> r;
            case "http://localhost:8080/keys" -> keys;
            default -> notFoundResponse;
         };
      });

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertEquals(1, metadata.currentSecurityContext().getKeys().size());
      JWK jwk = metadata.currentSecurityContext().getKeys().get(0);
      assertNull(jwk.getKeyID());
      assertEquals(KeyType.RSA, jwk.getKeyType());
      assertEquals(KeyUse.SIGNATURE, jwk.getKeyUse());

      // sign with Java Crypto
      Signature signer = Signature.getInstance("SHA256withRSA");
      byte[] data = new byte[32];
      new SecureRandom().nextBytes(data);
      signer.initSign(rsaPrivateKey);
      signer.update(data);
      // with RSA it's a plain signature:
      // signature = m^d mod n
      byte[] signature = signer.sign();

      // verify with Nimbus and a public key from JSON representation (JWK)
      assertTrue(new RSASSAVerifier((RSAKey) jwk).verify(new JWSHeader(JWSAlgorithm.RS256), data, Base64URL.encode(signature)));
      assertFalse(new RSASSAVerifier((RSAKey) jwk).verify(new JWSHeader(JWSAlgorithm.RS384), data, Base64URL.encode(signature)));
      assertThrows(JOSEException.class, () -> new RSASSAVerifier((RSAKey) jwk).verify(new JWSHeader(JWSAlgorithm.ES256), data, Base64URL.encode(signature)));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void oneSimpleECPublicKeyWithID() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, JOSEException, InvalidAlgorithmParameterException {
      KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC");
      ecKpg.initialize(new ECGenParameterSpec("secp521r1"));
      KeyPair ecKeyPair = ecKpg.generateKeyPair();
      ECPrivateKey ecPrivateKey = (ECPrivateKey) ecKeyPair.getPrivate();
      ECPublicKey ecPublicKey = (ECPublicKey) ecKeyPair.getPublic();

      ECKey ecPublicJWK = new ECKey.Builder(Curve.P_521, ecPublicKey).keyID("k1").keyUse(KeyUse.SIGNATURE).build();
      String ecPublicJson = ecPublicJWK.toJSONString();

      final HttpResponse<InputStream> r = mock(HttpResponse.class);
      when(r.statusCode()).thenReturn(200);
      when(r.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<String> keys = mock(HttpResponse.class);
      when(keys.statusCode()).thenReturn(200);
      when(keys.body()).thenReturn("{\"keys\":[" + ecPublicJson + "]}");
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         return switch (argument.uri().toString()) {
            case "http://localhost:8080/.well-known/openid-configuration" -> r;
            case "http://localhost:8080/keys" -> keys;
            default -> notFoundResponse;
         };
      });

      OIDCMetadata metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertEquals(1, metadata.currentSecurityContext().getKeys().size());
      JWK jwk = metadata.currentSecurityContext().getKeys().get(0);
      assertEquals("k1", jwk.getKeyID());
      assertEquals(KeyType.EC, jwk.getKeyType());
      assertEquals(KeyUse.SIGNATURE, jwk.getKeyUse());

      // sign with Java Crypto
      Signature signer = Signature.getInstance("SHA512withECDSA");
      byte[] data = new byte[32];
      new SecureRandom().nextBytes(data);
      signer.initSign(ecPrivateKey);
      signer.update(data);
      // with ECDSA it's an ASN.1 structure:
      // $ openssl asn1parse -inform der -in /data/tmp/xxx.der
      //    0:d=0  hl=3 l= 136 cons: SEQUENCE
      //    3:d=1  hl=2 l=  66 prim: INTEGER           :011531C391609C77DEBF0CDED0B8E551A1945A13E6D67DCAF3F1E8925BEF9A09BBCAA54AA252B37DB2D4299D064A09EDB8683896A83E1B987B8F40B753F52D74A0F4
      //   71:d=1  hl=2 l=  66 prim: INTEGER           :F1BCE5589620618A700C8738EF95BA3F4E4D9D068672E51755E5E391C3EC6B1F262F74BFDBD7A080D4FB29B4C45BF7B91F2B59E28F26169B53F4803DB963990495
      // see sun.security.util.ECUtil.encodeSignature
      // see sun.security.ec.ECDSASignature.p1363Format
      byte[] signature = signer.sign();
      byte[] jwsSignature = ECDSA.transcodeSignatureToConcat(
            signature,
            ECDSA.getSignatureByteArrayLength(JWSAlgorithm.ES512)
      );

      assertTrue(new ECDSAVerifier((ECKey) jwk).verify(new JWSHeader(JWSAlgorithm.ES512), data, Base64URL.encode(jwsSignature)));
      assertThrows(JOSEException.class, () -> new ECDSAVerifier((ECKey) jwk).verify(new JWSHeader(JWSAlgorithm.ES384), data, Base64URL.encode(jwsSignature)));
      assertThrows(JOSEException.class, () -> new ECDSAVerifier((ECKey) jwk).verify(new JWSHeader(JWSAlgorithm.RS256), data, Base64URL.encode(jwsSignature)));

      // sign with Java Crypto - less obvious
      signer = Signature.getInstance("SHA512withECDSAinP1363Format");
      byte[] data2 = new byte[32];
      new SecureRandom().nextBytes(data2);
      signer.initSign(ecPrivateKey);
      signer.update(data2);
      // raw format this time
      byte[] signature2 = signer.sign();

      assertTrue(new ECDSAVerifier((ECKey) jwk).verify(new JWSHeader(JWSAlgorithm.ES512), data2, Base64URL.encode(signature2)));
      assertThrows(JOSEException.class, () -> new ECDSAVerifier((ECKey) jwk).verify(new JWSHeader(JWSAlgorithm.ES384), data2, Base64URL.encode(signature2)));
      assertThrows(JOSEException.class, () -> new ECDSAVerifier((ECKey) jwk).verify(new JWSHeader(JWSAlgorithm.RS256), data2, Base64URL.encode(signature2)));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void cachingKeys() throws IOException, InterruptedException, NoSuchAlgorithmException {
      KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
      rsaKpg.initialize(2048);
      KeyPair rsaKeyPair = rsaKpg.generateKeyPair();
      RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
      RSAPublicKey rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

      RSAKey rsaPublicJWK = new RSAKey.Builder(rsaPublicKey).keyUse(KeyUse.SIGNATURE).keyID("k1").build();
      String rsaPublicJson = rsaPublicJWK.toJSONString();
      RSAKey rsaPublicJWK2 = new RSAKey.Builder(rsaPublicKey).keyUse(KeyUse.SIGNATURE).keyID("k2").build();
      String rsaPublicJson2 = rsaPublicJWK2.toJSONString();

      final HttpResponse<InputStream> r = mock(HttpResponse.class);
      when(r.statusCode()).thenReturn(200);
      when(r.body()).thenReturn(new ByteArrayInputStream("{\"issuer\":\"http://localhost:8080\",\"jwks_uri\":\"http://localhost:8080/keys\"}".getBytes()));
      final HttpResponse<String> keys = mock(HttpResponse.class);
      when(keys.statusCode()).thenReturn(200);
      final int[] keyNumber = {1};
      when(keys.body()).thenAnswer(inv -> {
         if (keyNumber[0] == 1) {
            return "{\"keys\":[" + rsaPublicJson + "]}";
         } else {
            return "{\"keys\":[" + rsaPublicJson2 + "]}";
         }
      });
      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<InputStream> res = anyHandler();
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest argument = inv.getArgument(0);
         return switch (argument.uri().toString()) {
            case "http://localhost:8080/.well-known/openid-configuration" -> r;
            case "http://localhost:8080/keys" -> keys;
            default -> notFoundResponse;
         };
      });

      access = new SharedOIDCMetadataAccess(baseURI -> client, Map.of(
            OIDCSupport.ConfigKey.CACHE_KEYS_TIME_SECONDS.getName(), "2"
      ), true);

      OIDCMetadata metadata;

      metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertEquals(1, metadata.currentSecurityContext().getKeys().size());
      JWK jwk = metadata.currentSecurityContext().getKeys().get(0);
      assertEquals("k1", jwk.getKeyID());

      keyNumber[0] = 2;

      metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertEquals(1, metadata.currentSecurityContext().getKeys().size());
      jwk = metadata.currentSecurityContext().getKeys().get(0);
      assertEquals("k1", jwk.getKeyID(), "Should get cached key");

      Thread.sleep(2100);

      metadata = access.getMetadata(URI.create("http://localhost:8080"));
      assertTrue(metadata.isValid());
      assertEquals(1, metadata.currentSecurityContext().getKeys().size());
      jwk = metadata.currentSecurityContext().getKeys().get(0);
      assertEquals("k2", jwk.getKeyID(), "Should get refreshed key");
   }

}
