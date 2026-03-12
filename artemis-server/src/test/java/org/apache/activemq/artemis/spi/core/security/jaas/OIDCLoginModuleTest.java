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
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWKSecurityContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.security.jaas.StubX509Certificate;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.OIDCSupport;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.SharedHttpClientAccess;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.SharedOIDCMetadataAccess;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.lang.reflect.Field;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the {@link OIDCLoginModuleTest} which do not require reading {@code login.config} configuration
 * and do not use {@link javax.security.auth.login.LoginContext}.
 */
public class OIDCLoginModuleTest {

   public static final Set<String> NO_CLAIMS = Collections.emptySet();

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
      configuration.getLoggerConfig(OIDCLoginModuleTest.class.getName()).setLevel(Level.DEBUG);
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

   @Test
   public void noCallbackHandler() {
      OIDCLoginModule lm = new OIDCLoginModule();
      lm.initialize(new Subject(), null, null, configMap());
      try {
         lm.login();
         fail();
      } catch (LoginException e) {
         assertTrue(e.getMessage().contains("No callback handler"));
      }
   }

   @Test
   public void noToken() throws LoginException {
      OIDCLoginModule lm = new OIDCLoginModule();
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap());
      assertFalse(lm.login());
      assertFalse(lm.commit());

      assertEquals(0, subject.getPrincipals().size());
      assertEquals(0, subject.getPublicCredentials().size());
      assertEquals(0, subject.getPrivateCredentials().size());
      lm.logout();
   }

   @Test
   public void emptyToken() throws BadJOSEException, JOSEException {
      OIDCLoginModule lm = new OIDCLoginModule();
      Subject subject = new Subject();
      try {
         lm.validateToken(JWTParser.parse(""));
      } catch (ParseException e) {
         assertTrue(e.getMessage().contains("Missing dot delimiter"));
      }
   }

   // ---- Plain tokens tests

   @Test
   public void plainJWTWhenNotAllowed() throws ParseException, JOSEException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap());

      // https://datatracker.ietf.org/doc/html/rfc7519#section-6 - {"alg":"none"}
      String token = new PlainJWT(new JWTClaimsSet.Builder().build()).serialize();
      try {
         lm.validateToken(JWTParser.parse(token));
         fail();
      } catch (BadJOSEException e) {
         assertTrue(e.getMessage().contains("Unsecured (plain) JWTs are rejected"));
      }
   }

   @Test
   public void plainJWTWhenAllowedWithCorrectDates() throws BadJOSEException, ParseException, JOSEException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap(
            OIDCSupport.ConfigKey.ALLOW_PLAIN_JWT.getName(), "true"
      ));

      String token = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() - 5000L))
            .expirationTime(new Date(new Date().getTime() + 5000L))
            .build()).serialize();
      lm.validateToken(JWTParser.parse(token));
   }

   @Test
   public void plainJWTWithAndWithoutDates() throws BadJOSEException, ParseException, JOSEException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap(
            OIDCSupport.ConfigKey.ALLOW_PLAIN_JWT.getName(), "true"
      ));

      String token1 = new PlainJWT(new JWTClaimsSet.Builder()
            .build()).serialize();
      lm.validateToken(JWTParser.parse(token1));

      String token2 = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() - 5000L))
            .build()).serialize();
      lm.validateToken(JWTParser.parse(token2));

      String token3 = new PlainJWT(new JWTClaimsSet.Builder()
            .expirationTime(new Date(new Date().getTime() + 5000L))
            .build()).serialize();
      lm.validateToken(JWTParser.parse(token3));

      String token4 = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() - 5000L))
            .expirationTime(new Date(new Date().getTime() + 5000L))
            .build()).serialize();
      lm.validateToken(JWTParser.parse(token4));
   }

   @Test
   public void plainJWTWithIncorrectDates() throws BadJOSEException, JOSEException, ParseException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap(
            OIDCSupport.ConfigKey.ALLOW_PLAIN_JWT.getName(), "true",
            OIDCSupport.ConfigKey.MAX_CLOCK_SKEW_SECONDS.getName(), "1"
      ));

      String tokenForFuture = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() + 3000L))
            .expirationTime(new Date(new Date().getTime() + 6000L))
            .build()).serialize();
      try {
         lm.validateToken(JWTParser.parse(tokenForFuture));
      } catch (BadJWTException e) {
         assertTrue(e.getMessage().contains("JWT before use time"));
      }

      String tokenForPast = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() - 6000L))
            .expirationTime(new Date(new Date().getTime() - 3000L))
            .build()).serialize();
      try {
         lm.validateToken(JWTParser.parse(tokenForPast));
      } catch (BadJWTException e) {
         assertTrue(e.getMessage().contains("Expired JWT"));
      }
   }

   @Test
   public void plainJWTWithIncorrectDatesButTolerated() throws BadJOSEException, JOSEException, ParseException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap(
            OIDCSupport.ConfigKey.ALLOW_PLAIN_JWT.getName(), "true",
            OIDCSupport.ConfigKey.MAX_CLOCK_SKEW_SECONDS.getName(), "10"
      ));

      String tokenForFuture = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() + 3000L))
            .expirationTime(new Date(new Date().getTime() + 6000L))
            .build()).serialize();
      lm.validateToken(JWTParser.parse(tokenForFuture));

      String tokenForPast = new PlainJWT(new JWTClaimsSet.Builder()
            .notBeforeTime(new Date(new Date().getTime() - 6000L))
            .expirationTime(new Date(new Date().getTime() - 3000L))
            .build()).serialize();
      lm.validateToken(JWTParser.parse(tokenForPast));
   }

   // ---- Signed tokens test - https://datatracker.ietf.org/doc/html/rfc7518#section-3.1
   //      RSA, RSASSA-PSS Java algorithm ids - RS256, RS384, RS512, PS256, PS384, PS512 JWT signatures
   //       - https://datatracker.ietf.org/doc/html/rfc7518#section-3.3
   //       - https://datatracker.ietf.org/doc/html/rfc7518#section-3.5
   //      EC Java algorithm - ES256, ES256K, ES384, ES512 JWT signatures
   //      - https://datatracker.ietf.org/doc/html/rfc7518#section-3.4
   //      Ed25519, Ed448 Java algorithms - EdDSA, Ed25519 JWT signatures
   //      - require com.google.crypto.tink:tink additional dependency

   @Test
   public void rsaSignedTokens() throws JOSEException, NoSuchAlgorithmException, BadJOSEException, ParseException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap());

      // nimbus-jose-jwt supports these RSA/RSA-PSS algorithms
      //  - RS256: RSASSA-PKCS-v1_5 using SHA-256
      //  - RS384: RSASSA-PKCS-v1_5 using SHA-384
      //  - RS512: RSASSA-PKCS-v1_5 using SHA-512
      //  - PS256: RSASSA-PSS using SHA-256
      //  - PS384: RSASSA-PSS using SHA-384
      //  - PS512: RSASSA-PSS using SHA-512

      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();
      // "RSASSA-PSS" is a specific Signature algorithm, this literal exists for KeyPairGenerator
      // for symmetry reasons - the actual key pair is the same (RSA)
      KeyPairGenerator kpgRSA_PSS = KeyPairGenerator.getInstance("RSASSA-PSS");
      KeyPair pairRSA_PSS = kpgRSA_PSS.generateKeyPair();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      JWSAlgorithm[] algorithms = new JWSAlgorithm[]{
         JWSAlgorithm.RS256,
         JWSAlgorithm.RS384,
         JWSAlgorithm.RS512,
         JWSAlgorithm.PS256,
         JWSAlgorithm.PS384,
         JWSAlgorithm.PS512,
      };

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("rs-key").build());
      // from JWK format
      Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      keys.add(new RSAKey.Builder(
            new Base64URL(encoder.encodeToString(((RSAPublicKey) pairRSA_PSS.getPublic()).getModulus().toByteArray())),
            new Base64URL(encoder.encodeToString(((RSAPublicKey) pairRSA_PSS.getPublic()).getPublicExponent().toByteArray()))
      ).keyID("ps-key").build());
      JWKSecurityContext context = new JWKSecurityContext(keys);

      for (JWSAlgorithm algorithm : algorithms) {
         SignedJWT signedJWT;

         if (algorithm.getName().startsWith("RS")) {
            signedJWT = new SignedJWT(new JWSHeader.Builder(algorithm).keyID("rs-key").build(), claims);
            JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
            signedJWT.sign(signer);
            String token = signedJWT.serialize();

            assertFalse(token.endsWith("."), "Should include any signature");
            assertEquals(3, token.split("\\.").length, "Should contain header, payload and signature parts");
            assertTrue(signedJWT.verify(new RSASSAVerifier((RSAPublicKey) pairRSA.getPublic())));
         } else {
            signedJWT = new SignedJWT(new JWSHeader.Builder(algorithm).keyID("ps-key").build(), claims);
            JWSSigner signer = new RSASSASigner(pairRSA_PSS.getPrivate());
            signedJWT.sign(signer);
            String token = signedJWT.serialize();

            assertFalse(token.endsWith("."), "Should include any signature");
            assertEquals(3, token.split("\\.").length, "Should contain header, payload and signature parts");
            assertTrue(signedJWT.verify(new RSASSAVerifier((RSAPublicKey) pairRSA_PSS.getPublic())));
         }

         lm.setOidcSupport(new OIDCSupport(configMap(), false) {
            @Override
            public JWKSecurityContext currentContext() {
               return context;
            }
         });
         JWT jwt = JWTParser.parse(signedJWT.serialize());
         lm.validateToken(jwt);
         assertInstanceOf(SignedJWT.class, jwt);
      }
   }

   @Test
   public void ecSignedTokens() throws JOSEException, NoSuchAlgorithmException, BadJOSEException, ParseException, InvalidAlgorithmParameterException {
      OIDCLoginModule lm = new OIDCLoginModule(NO_CLAIMS);
      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, null, null), null, configMap());

      // nimbus-jose-jwt supports these EC algorithms:
      // NIST curves were standardized for government/enterprise use
      //  - ES256  - secp256r1 - P-256 + SHA-256  - 1.2.840.10045.3.1.7
      //  - ES384  - secp384r1 - P-384 + SHA-384  - 1.3.132.0.34
      //  - ES512  - secp521r1 - P-521 + SHA-512  - 1.3.132.0.35
      // secp256k1 was primarily used in Bitcoin
      //  - ES256K - secp256k1 - P-256K + SHA-256 - 1.3.132.0.10

      KeyPairGenerator kpg1 = KeyPairGenerator.getInstance("EC");
      kpg1.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair pair1 = kpg1.generateKeyPair();
      KeyPairGenerator kpg2 = KeyPairGenerator.getInstance("EC");
      kpg2.initialize(new ECGenParameterSpec("secp384r1"));
      KeyPair pair2 = kpg2.generateKeyPair();
      KeyPairGenerator kpg3 = KeyPairGenerator.getInstance("EC");
      kpg3.initialize(new ECGenParameterSpec("secp521r1"));
      KeyPair pair3 = kpg3.generateKeyPair();
      // java.security.InvalidAlgorithmParameterException: Curve not supported: secp256k1 (1.3.132.0.10)
//        KeyPairGenerator kpg4 = KeyPairGenerator.getInstance("EC");
//        kpg4.initialize(new ECGenParameterSpec("secp256k1"));
//        KeyPair pair4 = kpg4.generateKeyPair();

      // for the record:
//        System.out.println(HexFormat.of().formatHex(pair1.getPublic().getEncoded()));
      // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
      //    0:d=0  hl=2 l=  89 cons: SEQUENCE
      //    2:d=1  hl=2 l=  19 cons:  SEQUENCE
      //    4:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
      //   13:d=2  hl=2 l=   8 prim:   OBJECT            :prime256v1
      //   23:d=1  hl=2 l=  66 prim:  BIT STRING
//        System.out.println(HexFormat.of().formatHex(pair1.getPrivate().getEncoded()));
      // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
      //    0:d=0  hl=2 l=  65 cons: SEQUENCE
      //    2:d=1  hl=2 l=   1 prim:  INTEGER           :00
      //    5:d=1  hl=2 l=  19 cons:  SEQUENCE
      //    7:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
      //   16:d=2  hl=2 l=   8 prim:   OBJECT            :prime256v1
      //   26:d=1  hl=2 l=  39 prim:  OCTET STRING      [HEX DUMP]:30250201010420CC4848D8216329CB08355AD22BC878A4FC8FBD69D8F6CF37FDD05A6A9E36DDF1
//        System.out.println(HexFormat.of().formatHex(pair2.getPublic().getEncoded()));
      // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
      //    0:d=0  hl=2 l= 118 cons: SEQUENCE
      //    2:d=1  hl=2 l=  16 cons:  SEQUENCE
      //    4:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
      //   13:d=2  hl=2 l=   5 prim:   OBJECT            :secp384r1
      //   20:d=1  hl=2 l=  98 prim:  BIT STRING
//        System.out.println(HexFormat.of().formatHex(pair2.getPrivate().getEncoded()));
      // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
      //    0:d=0  hl=2 l=  78 cons: SEQUENCE
      //    2:d=1  hl=2 l=   1 prim:  INTEGER           :00
      //    5:d=1  hl=2 l=  16 cons:  SEQUENCE
      //    7:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
      //   16:d=2  hl=2 l=   5 prim:   OBJECT            :secp384r1
      //   23:d=1  hl=2 l=  55 prim:  OCTET STRING      [HEX DUMP]:30350201010430424D0072CA80BAC3627FF1D55E9F2ECB7AE19F6C3BD40347CFB064A06D39D0C0D1FB789C312E1FF6B9B3A52320A63A7B
//        System.out.println(HexFormat.of().formatHex(pair3.getPublic().getEncoded()));
      // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
      //    0:d=0  hl=3 l= 155 cons: SEQUENCE
      //    3:d=1  hl=2 l=  16 cons:  SEQUENCE
      //    5:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
      //   14:d=2  hl=2 l=   5 prim:   OBJECT            :secp521r1
      //   21:d=1  hl=3 l= 134 prim:  BIT STRING
//        System.out.println(HexFormat.of().formatHex(pair3.getPrivate().getEncoded()));
      // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
      //    0:d=0  hl=2 l=  96 cons: SEQUENCE
      //    2:d=1  hl=2 l=   1 prim:  INTEGER           :00
      //    5:d=1  hl=2 l=  16 cons:  SEQUENCE
      //    7:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
      //   16:d=2  hl=2 l=   5 prim:   OBJECT            :secp521r1
      //   23:d=1  hl=2 l=  73 prim:  OCTET STRING      [HEX DUMP]:3047020101044201B44B5ED5943BF08DC42BE2DB95F9D267B449F2D8A1522FD8C45F44B7DD06B7EE6991A4B38B882D232FC4054322C1C2A8B4A86DE03FAACB458B63CC71CBC35D21C7

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      JWSAlgorithm[] algorithms = new JWSAlgorithm[]{
         JWSAlgorithm.ES256,
         JWSAlgorithm.ES384,
         JWSAlgorithm.ES512,
//         JWSAlgorithm.ES256K,
      };
      KeyPair[] pairs = new KeyPair[]{pair1, pair2, pair3/*, pair4*/};

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new ECKey.Builder(Curve.P_256, (ECPublicKey) pair1.getPublic()).keyID("k1").build());
      keys.add(new ECKey.Builder(Curve.P_384, (ECPublicKey) pair2.getPublic()).keyID("k2").build());
      keys.add(new ECKey.Builder(Curve.P_521, (ECPublicKey) pair3.getPublic()).keyID("k3").build());
//        keys.add(new ECKey.Builder(Curve.SECP256K1, (ECPublicKey) pair4.getPublic()).keyID("k4").build());
      JWKSecurityContext context = new JWKSecurityContext(keys);
      lm.setOidcSupport(new OIDCSupport(configMap(), false) {
         @Override
         public JWKSecurityContext currentContext() {
            return context;
         }
      });

      for (int i = 0; i < algorithms.length; i++) {
         JWSAlgorithm algorithm = algorithms[i];
         SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(String.format("k%d", i + 1)).build(), claims);
         JWSSigner signer = new ECDSASigner((ECPrivateKey) pairs[i].getPrivate());
         signedJWT.sign(signer);
         String token = signedJWT.serialize();

         assertFalse(token.endsWith("."), "Should include any signature");
         assertEquals(3, token.split("\\.").length, "Should contain header, payload and signature parts");
         assertTrue(signedJWT.verify(new ECDSAVerifier((ECPublicKey) pairs[i].getPublic())));

         JWT jwt = JWTParser.parse(signedJWT.serialize());
         lm.validateToken(jwt);
         assertInstanceOf(SignedJWT.class, jwt);
      }
   }

   // ---- Tests for actual login

   @Test
   public void properSignedToken() throws NoSuchAlgorithmException, JOSEException, LoginException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(OIDCSupport.ConfigKey.DEBUG.getName(), "true");

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      Subject subject = new Subject();
      // TODO: the token should be passed in something better than a password field
      lm.initialize(subject, new JaasCallbackHandler(null, token, null), null, config);

      assertTrue(subject.getPrincipals().isEmpty());
      assertTrue(subject.getPublicCredentials().isEmpty());
      assertTrue(subject.getPrivateCredentials().isEmpty());

      // here's where the JAAS magic happens
      assertTrue(lm.login());
      assertTrue(lm.commit());

      // only "sub" (default) configured as identity path
      assertEquals(1, subject.getPrincipals().size());
      assertEquals("Alice", subject.getPrincipals().iterator().next().getName());
      assertTrue(subject.getPublicCredentials().isEmpty());
      assertFalse(subject.getPrivateCredentials().isEmpty());
   }

   @Test
   public void unknownKey() throws NoSuchAlgorithmException, JOSEException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(OIDCSupport.ConfigKey.DEBUG.getName(), "true");

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k2").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, null), null, config);

      assertThrows(LoginException.class, lm::login);
   }

   @Test
   public void badSignature() throws NoSuchAlgorithmException, JOSEException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(OIDCSupport.ConfigKey.DEBUG.getName(), "true");

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .jwtID(UUID.randomUUID().toString())
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      byte[] newSignature = new byte[256];
      new SecureRandom().nextBytes(newSignature);
      String[] split = token.split("\\.");
      Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      token = split[0] +  "." + split[1] + "." + encoder.encodeToString(newSignature);

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, null), null, config);

      assertThrows(LoginException.class, lm::login);
   }

   @Test
   public void noProofOfPossessionButNotCheckedWithMTLS() throws NoSuchAlgorithmException, JOSEException, LoginException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(OIDCSupport.ConfigKey.DEBUG.getName(), "true");

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

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
            .jwtID(UUID.randomUUID().toString())
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      RemotingConnection remotingConnection = mock(RemotingConnection.class);
      NettyServerConnection nettyConnection = mock(NettyServerConnection.class);
      when(remotingConnection.getTransportConnection()).thenReturn(nettyConnection);
      when(nettyConnection.getPeerCertificates()).thenReturn(new X509Certificate[] {cert});

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, remotingConnection), null, config);

      assertTrue(lm.login());
   }

   @Test
   public void badProofOfPossession() throws NoSuchAlgorithmException, JOSEException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.REQUIRE_OAUTH_MTLS.getName(), "true"
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

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
            .jwtID(UUID.randomUUID().toString())
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .claim("cnf", Map.of("x5t#256", x5t))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      RemotingConnection remotingConnection = mock(RemotingConnection.class);
      NettyServerConnection nettyConnection = mock(NettyServerConnection.class);
      when(remotingConnection.getTransportConnection()).thenReturn(nettyConnection);
      when(nettyConnection.getPeerCertificates()).thenReturn(new X509Certificate[] {cert});

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, remotingConnection), null, config);

      assertThrows(LoginException.class, lm::login);
   }

   @Test
   public void correctProofOfPossession() throws NoSuchAlgorithmException, JOSEException, LoginException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.REQUIRE_OAUTH_MTLS.getName(), "true"
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

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
            .jwtID(UUID.randomUUID().toString())
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .claim("cnf", Map.of("x5t#256", x5t))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      RemotingConnection remotingConnection = mock(RemotingConnection.class);
      NettyServerConnection nettyConnection = mock(NettyServerConnection.class);
      when(remotingConnection.getTransportConnection()).thenReturn(nettyConnection);
      when(nettyConnection.getPeerCertificates()).thenReturn(new X509Certificate[] {cert});

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, remotingConnection), null, config);

      assertTrue(lm.login());
   }

   @Test
   public void correctProofOfPossessionButNotConfiguredAndWithoutMTLS() throws NoSuchAlgorithmException, JOSEException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.REQUIRE_OAUTH_MTLS.getName(), "false"
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

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
            .jwtID(UUID.randomUUID().toString())
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("azp", "artemis-oidc-client")
            .claim("cnf", Map.of("x5t#256", x5t))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();
      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      RemotingConnection remotingConnection = mock(RemotingConnection.class);
      NettyServerConnection nettyConnection = mock(NettyServerConnection.class);
      when(remotingConnection.getTransportConnection()).thenReturn(nettyConnection);
      // no mTLS
      when(nettyConnection.getPeerCertificates()).thenReturn(new X509Certificate[0]);

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, remotingConnection), null, config);

      assertThrows(LoginException.class, lm::login);
   }

   @Test
   public void badJWT() {
      Map<String, String> config = configMap(OIDCSupport.ConfigKey.DEBUG.getName(), "true");

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(Collections.emptyList());
         }
      });

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, "some bad JWT token", null), null, config);

      assertThrows(LoginException.class, lm::login);
   }

   @Test
   public void tokenPrincipals() throws NoSuchAlgorithmException, JOSEException, LoginException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.IDENTITY_PATHS.getName(), "sub, azp",
            OIDCSupport.ConfigKey.ROLES_PATHS.getName(), "realm_access.roles, groups, scope"
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

      String uuid = UUID.randomUUID().toString();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("sub", uuid)
            .claim("azp", "artemis-oidc-client")
            .claim("scope", "openid profile")
            .claim("groups", List.of("admin", "viewer"))
            .claim("realm_access", Map.of("roles", List.of("admin", "important observer   \t")))
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, null), null, config);

      assertTrue(subject.getPrincipals().isEmpty());
      assertTrue(subject.getPublicCredentials().isEmpty());
      assertTrue(subject.getPrivateCredentials().isEmpty());

      assertTrue(lm.login());

      // still empty
      assertTrue(subject.getPrincipals().isEmpty());
      assertTrue(subject.getPublicCredentials().isEmpty());
      assertTrue(subject.getPrivateCredentials().isEmpty());

      assertTrue(lm.commit());

      // should get principals for "users" (identities) and "roles"
      Set<Principal> principals = subject.getPrincipals();
      assertEquals(7, principals.size());
      Set<String> identities = new HashSet<>(Set.of("artemis-oidc-client", uuid));
      Set<String> roles = new HashSet<>(Set.of("admin", "viewer", "important observer", "openid", "profile"));
      principals.forEach(principal -> {
         if (principal.getClass() == UserPrincipal.class) {
            identities.remove(principal.getName());
         } else if (principal.getClass() == RolePrincipal.class) {
            roles.remove(principal.getName());
         }
      });
      assertTrue(identities.isEmpty());
      assertTrue(roles.isEmpty());
   }

   @Test
   public void wrongPathsForToken() throws NoSuchAlgorithmException, JOSEException, LoginException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.IDENTITY_PATHS.getName(), "xxx"
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

      String uuid = UUID.randomUUID().toString();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("sub", uuid)
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, null), null, config);

      assertTrue(lm.login());

      assertThrows(LoginException.class, lm::commit);
   }

   @Test
   public void customPrincipalClasses() throws NoSuchAlgorithmException, JOSEException, LoginException {
      KeyPairGenerator kpgRSA = KeyPairGenerator.getInstance("RSA");
      KeyPair pairRSA = kpgRSA.generateKeyPair();

      List<JWK> keys = new ArrayList<>();
      // directly from the public key
      keys.add(new RSAKey.Builder((RSAPublicKey) pairRSA.getPublic()).keyID("k1").build());

      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.IDENTITY_PATHS.getName(), "sub",
            OIDCSupport.ConfigKey.ROLES_PATHS.getName(), "roles",
            OIDCSupport.ConfigKey.USER_CLASS.getName(), MyUserPrincipal.class.getName(),
            OIDCSupport.ConfigKey.ROLE_CLASS.getName(), MyRolePrincipal.class.getName()
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return new JWKSecurityContext(keys);
         }
      });

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("http://localhost")
            .subject("Alice")
            .audience(List.of("me-the-broker", "some-other-api"))
            .claim("sub", "me")
            .claim("roles", "admin")
            .claim("azp", "artemis-oidc-client")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
      JWSSigner signer = new RSASSASigner(pairRSA.getPrivate());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      Subject subject = new Subject();
      lm.initialize(subject, new JaasCallbackHandler(null, token, null), null, config);

      assertTrue(lm.login());
      assertTrue(lm.commit());

      Set<MyUserPrincipal> identities = subject.getPrincipals(MyUserPrincipal.class);
      Set<MyRolePrincipal> roles = subject.getPrincipals(MyRolePrincipal.class);
      assertEquals(1, identities.size());
      assertEquals(1, roles.size());
      assertEquals("me", identities.iterator().next().getName());
      assertEquals("admin", roles.iterator().next().getName());
   }

   @Test
   public void principalClassWithWrongConstructor() {
      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.USER_CLASS.getName(), MyPrivatePrincipal.class.getName()
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return null;
         }
      });

      assertThrows(IllegalArgumentException.class, () -> lm.initialize(new Subject(), new JaasCallbackHandler(null, null, null), null, config));
   }

   @Test
   public void principalClassWithWrongSuperInterface() {
      Map<String, String> config = configMap(
            OIDCSupport.ConfigKey.DEBUG.getName(), "true",
            OIDCSupport.ConfigKey.USER_CLASS.getName(), MyNonPrincipal.class.getName()
      );

      OIDCLoginModule lm = new OIDCLoginModule();
      lm.setOidcSupport(new OIDCSupport(config, true) {
         @Override
         public JWKSecurityContext currentContext() {
            return null;
         }
      });

      assertThrows(IllegalArgumentException.class, () -> lm.initialize(new Subject(), new JaasCallbackHandler(null, null, null), null, config));
   }

   public static class MyUserPrincipal implements Principal {
      private final String name;

      public MyUserPrincipal(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   public static class MyRolePrincipal implements Principal {
      private final String name;

      public MyRolePrincipal(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   public static class MyPrivatePrincipal implements Principal {
      private final String name;

      private MyPrivatePrincipal(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   public static class MyNonPrincipal  {
      private final String name;

      public MyNonPrincipal(String name) {
         this.name = name;
      }

      public String getName() {
         return name;
      }
   }

}
