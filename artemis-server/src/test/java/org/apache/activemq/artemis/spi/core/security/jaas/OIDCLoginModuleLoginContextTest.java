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
package org.apache.activemq.artemis.spi.core.security.jaas;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.HttpClientAccess;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.OIDCMetadataAccess;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.OIDCSupport;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.SharedHttpClientAccess;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.SharedOIDCMetadataAccess;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OIDCLoginModuleLoginContextTest {

   public static final Set<String> NO_CLAIMS = Collections.emptySet();

   private static final String loginConfigSysPropName = "java.security.auth.login.config";
   private static String oldLoginConfig;
   private static String ecKeyJson;

   private static ECPrivateKey privateKey;

   private HttpResponse<InputStream> notFoundResponse;
   private HttpResponse<InputStream> oidcMetadataResponse;
   private HttpResponse<String> keysResponse;
   private OIDCMetadataAccess oidcMetadataAccess;
   private HttpClientAccess httpClientAccess;

   public static Map<String, String> configMap(String... entries) {
      if (entries.length % 2 != 0) {
         throw new IllegalArgumentException("Should contain even number of entries");
      }
      Map<String, String> map = new HashMap<>();
      map.put(OIDCSupport.ConfigKey.PROVIDER_URL.getName(), "http://localhost");
      for (int i = 0; i < entries.length; i += 2) {
         map.put(entries[i], entries[i + 1]);
      }
      return map;
   }

   @BeforeAll
   public static void setUpLogging() {
      Configuration configuration = ((LoggerContext) LogManager.getContext(false)).getConfiguration();
      configuration.getLoggerConfig(OIDCLoginModuleLoginContextTest.class.getName()).setLevel(Level.DEBUG);
   }

   @BeforeAll
   public static void setUpJaas() {
      oldLoginConfig = System.getProperty(loginConfigSysPropName, null);
      System.setProperty(loginConfigSysPropName, "src/test/resources/login.config");
   }

   @BeforeAll
   public static void setUpKeys() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
      kpg.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair kp = kpg.generateKeyPair();
      privateKey = (ECPrivateKey) kp.getPrivate();
      ECPublicKey publicKey = (ECPublicKey) kp.getPublic();
      ECKey ecPublicKey = new ECKey.Builder(Curve.P_256, publicKey).keyID("ec-key256").keyUse(KeyUse.SIGNATURE).build();
      ecKeyJson = ecPublicKey.toJSONString();
   }

   @AfterAll
   public static void resetJaas() {
      if (oldLoginConfig != null) {
         System.setProperty(loginConfigSysPropName, oldLoginConfig);
      }
   }

   @BeforeEach
   public void setUp() throws NoSuchFieldException, IllegalAccessException {
      Field f1 = SharedHttpClientAccess.class.getDeclaredField("cache");
      f1.setAccessible(true);
      ((Map<?, ?>) f1.get(null)).clear();
      Field f2 = SharedOIDCMetadataAccess.class.getDeclaredField("cache");
      f2.setAccessible(true);
      ((Map<?, ?>) f2.get(null)).clear();
   }

   @BeforeEach
   @SuppressWarnings("unchecked")
   public void setup() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
      HttpClient client = mock(HttpClient.class);

      notFoundResponse = mock(HttpResponse.class);
      when(notFoundResponse.statusCode()).thenReturn(404);
      when(notFoundResponse.body()).thenReturn(new ByteArrayInputStream("Not Found".getBytes()));

      oidcMetadataResponse = Mockito.mock(HttpResponse.class);
      when(oidcMetadataResponse.statusCode()).thenReturn(200);
      when(oidcMetadataResponse.body()).thenReturn(new ByteArrayInputStream("""
                {
                  "issuer":"http://localhost/testopenidprovider",
                  "jwks_uri":"http://localhost/testopenidprovider/keys"
                }
            """.getBytes()));

      keysResponse = Mockito.mock(HttpResponse.class);
      when(keysResponse.statusCode()).thenReturn(200);
      String json = String.format("{\"keys\":[%s]}", ecKeyJson);
      when(keysResponse.body()).thenReturn(json);

      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<String> res = any(HttpResponse.BodyHandler.class);
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest r = inv.getArgument(0, HttpRequest.class);
         return switch (r.uri().getPath()) {
            case "/testopenidprovider/.well-known/openid-configuration" -> oidcMetadataResponse;
            case "/testopenidprovider/keys" -> keysResponse;
            default -> notFoundResponse;
         };
      });

      Field f1 = SharedHttpClientAccess.class.getDeclaredField("cache");
      f1.setAccessible(true);
      ((Map<URI, HttpClient>) f1.get(null)).put(URI.create("http://localhost/testopenidprovider"), client);
   }

   @Test
   public void properSignedToken() throws JOSEException, LoginException {
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost/testopenidprovider")
            .subject("Alice")
            .audience("artemis-broker")
            .claim("azp", "artemis-oidc-client")
            .claim("scope", "role1 role2")
            .claim("realm_access", Map.of("roles", List.of("admin")))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("ec-key256").build(), claims);
      JWSSigner signer = new ECDSASigner(privateKey);
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      Subject subject = new Subject();
      LoginContext lc = new LoginContext("OIDCLogin", subject, new JaasCallbackHandler(null, token, null));

      assertTrue(subject.getPrincipals().isEmpty());
      assertTrue(subject.getPublicCredentials().isEmpty());
      assertTrue(subject.getPrivateCredentials().isEmpty());

      lc.login();

      // only "sub" (default) configured as identity path
      assertEquals(2, subject.getPrincipals().size());
      assertEquals("Alice", subject.getPrincipals(UserPrincipal.class).iterator().next().getName());
      assertEquals("admin", subject.getPrincipals(RolePrincipal.class).iterator().next().getName());
      assertTrue(subject.getPublicCredentials().isEmpty());
      assertFalse(subject.getPrivateCredentials().isEmpty());
   }

}
