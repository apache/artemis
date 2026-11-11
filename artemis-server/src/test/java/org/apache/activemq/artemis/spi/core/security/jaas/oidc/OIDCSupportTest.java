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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import org.apache.activemq.artemis.core.security.jaas.StubX509Certificate;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for everything just one layer below full
 * {@link org.apache.activemq.artemis.spi.core.security.jaas.OIDCLoginModule}.
 */
public class OIDCSupportTest {

   private static String rsaPublicJson;
   private static String ecKeyJson1;
   private static String ecKeyJson2;

   private HttpClient client;
   private HttpResponse<InputStream> notFoundResponse;
   private HttpResponse<InputStream> oidcMetadataResponse;
   private HttpResponse<String> keysResponse;
   private OIDCMetadataAccess oidcMetadataAccess;
   private HttpClientAccess httpClientAccess;

   @BeforeAll
   public static void setUp() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
      KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
      rsaKpg.initialize(2048);
      KeyPair rsaKeyPair = rsaKpg.generateKeyPair();
      RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
      RSAPublicKey rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();
      RSAKey rsaPublicJWK = new RSAKey.Builder(rsaPublicKey).keyID("rsa-key").keyUse(KeyUse.SIGNATURE).build();
      rsaPublicJson = rsaPublicJWK.toJSONString();

      KeyPairGenerator ecKpg1 = KeyPairGenerator.getInstance("EC");
      ecKpg1.initialize(new ECGenParameterSpec("secp521r1"));
      KeyPair ecKeyPair1 = ecKpg1.generateKeyPair();
      ECPrivateKey ecPrivateKey1 = (ECPrivateKey) ecKeyPair1.getPrivate();
      ECPublicKey ecPublicKey1 = (ECPublicKey) ecKeyPair1.getPublic();
      ECKey ecPublicJWK1 = new ECKey.Builder(Curve.P_521, ecPublicKey1).keyID("ec-key521").keyUse(KeyUse.SIGNATURE).build();
      ecKeyJson1 = ecPublicJWK1.toJSONString();

      KeyPairGenerator ecKpg2 = KeyPairGenerator.getInstance("EC");
      ecKpg2.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair ecKeyPair2 = ecKpg2.generateKeyPair();
      ECPrivateKey ecPrivateKey2 = (ECPrivateKey) ecKeyPair2.getPrivate();
      ECPublicKey ecPublicKey2 = (ECPublicKey) ecKeyPair2.getPublic();
      ECKey ecPublicJWK2 = new ECKey.Builder(Curve.P_256, ecPublicKey2).keyID("ec-key256").keyUse(KeyUse.SIGNATURE).build();
      ecKeyJson2 = ecPublicJWK2.toJSONString();
   }

   @BeforeAll
   public static void setUpLogging() {
      Configuration configuration = ((LoggerContext) LogManager.getContext(false)).getConfiguration();
      configuration.getLoggerConfig(OIDCSupportTest.class.getName()).setLevel(Level.DEBUG);
   }

   @BeforeEach
   @SuppressWarnings("unchecked")
   public void setup() throws IOException, InterruptedException {
      client = mock(HttpClient.class);

      notFoundResponse = mock(HttpResponse.class);
      when(notFoundResponse.statusCode()).thenReturn(404);
      when(notFoundResponse.body()).thenReturn(new ByteArrayInputStream("Not Found".getBytes()));

      oidcMetadataResponse = Mockito.mock(HttpResponse.class);
      when(oidcMetadataResponse.statusCode()).thenReturn(200);
      when(oidcMetadataResponse.body()).thenReturn(new ByteArrayInputStream("""
                {
                  "issuer":"http://localhost",
                  "jwks_uri":"http://localhost/keys"
                }
            """.getBytes()));

      keysResponse = Mockito.mock(HttpResponse.class);
      when(keysResponse.statusCode()).thenReturn(200);
      String json = String.format("{\"keys\":[%s,%s,%s]}", rsaPublicJson, ecKeyJson1, ecKeyJson2);
      when(keysResponse.body()).thenReturn(json);

      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<String> res = any(HttpResponse.BodyHandler.class);
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest r = inv.getArgument(0, HttpRequest.class);
         return switch (r.uri().getPath()) {
            case "/.well-known/openid-configuration" -> oidcMetadataResponse;
            case "/keys" -> keysResponse;
            default -> notFoundResponse;
         };
      });

      SharedOIDCMetadataAccess.cache.clear();

      httpClientAccess = baseURI -> client;
      oidcMetadataAccess = new SharedOIDCMetadataAccess(httpClientAccess, Map.of(
            OIDCSupport.ConfigKey.PROVIDER_URL.getName(), "http://localhost",
            OIDCSupport.ConfigKey.METADATA_RETRY_TIME_SECONDS.getName(), "10"
      ), true);
   }

   @Test
   public void threeKeysAvailable() {
      Map<String, String> config = Map.of(OIDCSupport.ConfigKey.PROVIDER_URL.getName(), "http://localhost");
      OIDCSupport support = new OIDCSupport(config, true);
      support.setHttpClientAccess(httpClientAccess);
      support.setOidcMetadataAccess(oidcMetadataAccess);
      support.initialize();

      assertEquals(3, support.currentContext().getKeys().size());
   }

   @Test
   public void defaultConfigs() {
      assertArrayEquals(new String[] {"sub"}, OIDCSupport.stringArrayOption(OIDCSupport.ConfigKey.IDENTITY_PATHS, Collections.emptyMap()));
      assertNull(OIDCSupport.stringArrayOption(OIDCSupport.ConfigKey.ROLES_PATHS, Collections.emptyMap()));
   }

   @Test
   public void changedConfigs() {
      assertArrayEquals(new String[] {"sub", "preferred_username"}, OIDCSupport.stringArrayOption(OIDCSupport.ConfigKey.IDENTITY_PATHS, Map.of("identityPaths", "sub, preferred_username")));
      assertArrayEquals(new String[] {"realm_access.roles"}, OIDCSupport.stringArrayOption(OIDCSupport.ConfigKey.ROLES_PATHS, Map.of("rolesPaths", "realm_access.roles")));
   }

   @Test
   public void jsonPathsIntoClaims() {
      JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
      builder.issuer("Keycloak NG");
      // string value
      builder.claim("sub", "some-uuid-value");
      // explicit null value
      builder.claim("azp", null);
      // array of strings
      builder.claim("roles", List.of("admin", "viewer"));
      // nested whitespace separated values in a string
      builder.claim("realm_access", Map.of("roles", "admin viewer\tobserver"));
      // scope which could be configured like this in Keycloak with "Include in token scope" option in Client Scope config
      builder.claim("scope", "openid profile email artemis-2.47.2");
      // deeply nested as in Keycloak
      builder.claim("resource_access", Map.of("account", Map.of("roles", List.of("   admin", "viewer", "some other role   "))));
      JWTClaimsSet set = builder.build();

      OIDCSupport.JWTStringArray v;

      v = OIDCSupport.stringArrayForPath(set, "sub");
      assertArrayEquals(new String[] {"some-uuid-value"}, v.value());
      assertTrue(v.valid());

      v = OIDCSupport.stringArrayForPath(set, "azp");
      assertNull(v.value());
      assertFalse(v.valid());

      v = OIDCSupport.stringArrayForPath(set, "roles");
      assertArrayEquals(new String[] {"admin", "viewer"}, v.value());
      assertTrue(v.valid());

      v = OIDCSupport.stringArrayForPath(set, "realm_access.roles");
      assertArrayEquals(new String[] {"admin", "viewer", "observer"}, v.value());
      assertTrue(v.valid());

      v = OIDCSupport.stringArrayForPath(set, "scope");
      assertArrayEquals(new String[] {"openid", "profile", "email", "artemis-2.47.2"}, v.value());
      assertTrue(v.valid());

      v = OIDCSupport.stringArrayForPath(set, "resource_access.account.roles");
      assertArrayEquals(new String[] {"admin", "viewer", "some other role"}, v.value());
      assertTrue(v.valid());

      v = OIDCSupport.stringArrayForPath(set, "resource_access.account");
      assertNull(v.value());
      assertFalse(v.valid());
   }

   @Test
   public void oauth2MTLSWithMissingClientCertificate() {
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience("broker")
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      assertFalse(OIDCSupport.tlsCertificateMatching(null, claims, true));
      assertFalse(OIDCSupport.tlsCertificateMatching(new X509Certificate[0], claims, true));
   }

   @Test
   public void oauth2MTLSWithProperClientCertificateAndCnfClaim() throws NoSuchAlgorithmException {
      StubX509Certificate cert = new StubX509Certificate(new UserPrincipal("Alice")) {
         @Override
         public byte[] getEncoded() {
            // see for example org.keycloak.crypto.elytron.ElytronPEMUtilsProvider#encode()
            return new byte[] {0x42, 0x2a};
         }
      };

      byte[] digest = MessageDigest.getInstance("SHA256").digest(cert.getEncoded());
      String x5t = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience("broker")
            .claim("azp", "artemis-oidc-client")
            .claim("cnf", Map.of("x5t#256", x5t))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      assertTrue(OIDCSupport.tlsCertificateMatching(new X509Certificate[] {cert}, claims, true));
   }

   @Test
   public void oauth2MTLSWithProperClientCertificateAndNonMatchingCnfClaim() throws NoSuchAlgorithmException {
      StubX509Certificate cert = new StubX509Certificate(new UserPrincipal("Alice")) {
         @Override
         public byte[] getEncoded() {
            // see for example org.keycloak.crypto.elytron.ElytronPEMUtilsProvider#encode()
            return new byte[] {0x42, 0x2a};
         }
      };

      byte[] digest = MessageDigest.getInstance("SHA256").digest(new byte[] {0x2a, 0x42});
      String x5t = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience("broker")
            .claim("azp", "artemis-oidc-client")
            .claim("cnf", Map.of("x5t#256", x5t))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      assertFalse(OIDCSupport.tlsCertificateMatching(new X509Certificate[] {cert}, claims, true));
   }

}
