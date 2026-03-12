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

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.json.JsonObject;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.text.ParseException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for using {@code com.nimbusds:nimbus-jose-jwt} library.
 */
public class JWTTest {

   @Test
   public void plainJWT() throws ParseException {
      // https://datatracker.ietf.org/doc/html/rfc7519#section-6.1 - Example Unsecured JWT
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      PlainJWT plainJWT = new PlainJWT(claims);
      String token = plainJWT.serialize();
      assertTrue(token.endsWith("."), "Should not include any signature");

      JWT jwt = JWTParser.parse(token);
      assertInstanceOf(PlainJWT.class, jwt);
   }

   @Test
   public void signedJWTWithRequiredHmacAlgorithm() throws Exception {
      // https://datatracker.ietf.org/doc/html/rfc7518#section-3.1 - HS256 is the only REQUIRED algorithm

      byte[] sharedSecret = new byte[32]; // 256-bit
      new SecureRandom().nextBytes(sharedSecret);

      SecretKey hmacKey = new SecretKeySpec(sharedSecret, "HmacSHA256");

      Mac mac = Mac.getInstance("HmacSHA256");
      KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);

      // there are 4 signers:
      //  - com.nimbusds.jose.crypto.ECDSASigner
      //  - com.nimbusds.jose.crypto.Ed25519Signer
      //  - com.nimbusds.jose.crypto.MACSigner
      //  - com.nimbusds.jose.crypto.RSASSASigner
      JWSSigner signer = new MACSigner(hmacKey.getEncoded());
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      assertFalse(token.endsWith("."), "Should include any signature");
      assertEquals(3, token.split("\\.").length, "Should contain header, payload and signature parts");

      JWT jwt = JWTParser.parse(token);
      assertInstanceOf(SignedJWT.class, jwt);
      assertTrue(((SignedJWT) jwt).verify(new MACVerifier(hmacKey)));
   }

   @Test
   public void signedJWTWithRecommendedRsaAlgorithm() throws Exception {
      // https://datatracker.ietf.org/doc/html/rfc7518#section-3.1 - RS256 is the RECOMMENDED algorithm

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair keyPair = kpg.generateKeyPair();
      PrivateKey privateKey = keyPair.getPrivate();
      PublicKey publicKey = keyPair.getPublic();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);

      JWSSigner signer = new RSASSASigner(privateKey);
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      assertFalse(token.endsWith("."), "Should include any signature");
      assertEquals(3, token.split("\\.").length, "Should contain header, payload and signature parts");

      JWT jwt = JWTParser.parse(token);
      assertInstanceOf(SignedJWT.class, jwt);
      assertTrue(((SignedJWT) jwt).verify(new RSASSAVerifier((RSAPublicKey) publicKey)));
   }

   @Test
   public void signedJWTWithRecommendedEcAlgorithm() throws Exception {
      // https://datatracker.ietf.org/doc/html/rfc7518#section-3.4 - ES256 is the RECOMMENDED+ algorithm

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
      kpg.initialize(new ECGenParameterSpec("secp521r1"));
      KeyPair keyPair = kpg.generateKeyPair();
      PrivateKey privateKey = keyPair.getPrivate();
      PublicKey publicKey = keyPair.getPublic();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.ES512), claims);

      JWSSigner signer = new ECDSASigner((ECPrivateKey) privateKey);
      signedJWT.sign(signer);
      String token = signedJWT.serialize();

      assertFalse(token.endsWith("."), "Should include any signature");
      assertEquals(3, token.split("\\.").length, "Should contain header, payload and signature parts");

      JWT jwt = JWTParser.parse(token);
      assertInstanceOf(SignedJWT.class, jwt);
      assertTrue(((SignedJWT) jwt).verify(new ECDSAVerifier((ECPublicKey) publicKey)));
   }

   @Test
   public void encryptedJWT() throws Exception {
      // https://datatracker.ietf.org/doc/html/rfc7516

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair keyPair = kpg.generateKeyPair();
      RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
      RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://artemis.apache.org")
            .subject("Alice")
            .expirationTime(new Date(new Date().getTime() + 3_600_000))
            .build();

      JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
            // JWS - https://datatracker.ietf.org/doc/html/rfc7515#section-4.1.10 - "cty" is just a MIME
            // JWE - https://datatracker.ietf.org/doc/html/rfc7516#section-4.1.12 - "cty" is just a MIME
            // JWT - https://datatracker.ietf.org/doc/html/rfc7519#section-5.2 - REQUIRES "cty":"JWT"
            .contentType("JWT")
            .build();

      EncryptedJWT encryptedJWT = new EncryptedJWT(header, claims);

      // there are 9 JWE Encrypters:
      //  - com.nimbusds.jose.crypto.AESEncrypter
      //  - com.nimbusds.jose.crypto.DirectEncrypter
      //  - com.nimbusds.jose.crypto.ECDH1PUEncrypter
      //  - com.nimbusds.jose.crypto.ECDH1PUX25519Encrypter
      //  - com.nimbusds.jose.crypto.ECDHEncrypter
      //  - com.nimbusds.jose.crypto.MultiEncrypter
      //  - com.nimbusds.jose.crypto.PasswordBasedEncrypter
      //  - com.nimbusds.jose.crypto.RSAEncrypter
      //  - com.nimbusds.jose.crypto.X25519Encrypter
      JWEEncrypter encrypter = new RSAEncrypter(publicKey);
      encryptedJWT.encrypt(encrypter);
      String token = encryptedJWT.serialize();

      assertEquals(5, token.split("\\.").length, "Should contain header, encrypted key, IV, cipher text and integrity value parts");

      JWT jwt = JWTParser.parse(token);
      assertInstanceOf(EncryptedJWT.class, jwt);
      assertNull(jwt.getJWTClaimsSet());
      ((EncryptedJWT) jwt).decrypt(new RSADecrypter(privateKey));
      assertNotNull(jwt.getJWTClaimsSet());
   }

   @Test
   public void jwkRepresentation() throws Exception {
      KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
      rsaKpg.initialize(2048);
      KeyPair rsaKeyPair = rsaKpg.generateKeyPair();
      RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
      RSAPublicKey rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

      KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC");
      ecKpg.initialize(new ECGenParameterSpec("secp521r1"));
      KeyPair ecKeyPair = ecKpg.generateKeyPair();
      ECPrivateKey ecPrivateKey = (ECPrivateKey) ecKeyPair.getPrivate();
      ECPublicKey ecPublicKey = (ECPublicKey) ecKeyPair.getPublic();

      RSAKey rsaPublicJWK = new RSAKey.Builder(rsaPublicKey).keyUse(KeyUse.SIGNATURE).build();
      String rsaPublicJson = rsaPublicJWK.toJSONString();
      JsonObject rsaPublic = JsonUtil.readJsonObject(rsaPublicJson);
      assertTrue(rsaPublic.containsKey("e"), "should contain RSA modulus");
      assertTrue(rsaPublic.containsKey("n"), "should contain RSA exponent");

      RSAKey rsaPrivateJWK = new RSAKey.Builder(rsaPublicKey).keyUse(KeyUse.SIGNATURE).privateKey(rsaPrivateKey).build();
      String rsaPrivateJson = rsaPrivateJWK.toJSONString();
      JsonObject rsaPrivate = JsonUtil.readJsonObject(rsaPrivateJson);
      assertTrue(rsaPrivate.containsKey("e"), "should contain RSA modulus");
      assertTrue(rsaPrivate.containsKey("n"), "should contain RSA exponent");
      assertTrue(rsaPrivate.containsKey("d"), "should contain RSA private exponent");
      assertTrue(rsaPrivate.containsKey("p"), "should contain RSA first prime factor");
      assertTrue(rsaPrivate.containsKey("q"), "should contain RSA second prime factor");
      assertTrue(rsaPrivate.containsKey("dp"), "should contain RSA first factor CRT exponent");
      assertTrue(rsaPrivate.containsKey("dq"), "should contain RSA second factor CRT exponent");
      assertTrue(rsaPrivate.containsKey("qi"), "should contain RSA first CRT coefficient");

      ECKey ecPublicJWK = new ECKey.Builder(Curve.P_521, ecPublicKey).keyUse(KeyUse.SIGNATURE).build();
      String ecPublicJson = ecPublicJWK.toJSONString();
      JsonObject ecPublic = JsonUtil.readJsonObject(ecPublicJson);
      assertTrue(ecPublic.containsKey("crv"), "should contain EC curve");
      assertTrue(ecPublic.containsKey("x"), "should contain EC X coordinate");
      assertTrue(ecPublic.containsKey("y"), "should contain EC Y coordinate");

      ECKey ecPrivateJWK = new ECKey.Builder(Curve.P_521, ecPublicKey).keyUse(KeyUse.SIGNATURE).privateKey(ecPrivateKey).build();
      String ecPrivateJson = ecPrivateJWK.toJSONString();
      JsonObject ecPrivate = JsonUtil.readJsonObject(ecPrivateJson);
      assertTrue(ecPrivate.containsKey("crv"), "should contain EC curve");
      assertTrue(ecPrivate.containsKey("x"), "should contain EC X coordinate");
      assertTrue(ecPrivate.containsKey("y"), "should contain EC Y coordinate");
      assertTrue(ecPrivate.containsKey("d"), "should contain EC ECC private key");
   }

}
