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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.ssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

/**
 * Tests for SSLContextConfig to ensure proper handling of crcOptions
 */
public class SSLContextConfigTest extends ActiveMQTestBase {

   @Test
   public void testCrcOptionsInBuilder() {
      final String crcOptions = "SOFT_FAIL,PREFER_CRLS";

      SSLContextConfig config = SSLContextConfig.builder()
         .crcOptions(crcOptions)
         .build();

      assertEquals(crcOptions, config.getCrcOptions());
   }

   @Test
   public void testDefaultCrcOptions() {
      SSLContextConfig config = SSLContextConfig.builder()
         .build();

      assertNull(config.getCrcOptions());
   }

   @Test
   public void testEqualsWithCrcOptions() {
      SSLContextConfig config1 = SSLContextConfig.builder()
         .crcOptions("SOFT_FAIL")
         .build();

      SSLContextConfig config2 = SSLContextConfig.builder()
         .crcOptions("SOFT_FAIL")
         .build();

      SSLContextConfig config3 = SSLContextConfig.builder()
         .crcOptions("PREFER_CRLS")
         .build();

      assertEquals(config1, config2);
      assertNotEquals(config1, config3);
   }

   @Test
   public void testHashCodeWithCrcOptions() {
      SSLContextConfig config1 = SSLContextConfig.builder()
         .crcOptions("SOFT_FAIL")
         .build();

      SSLContextConfig config2 = SSLContextConfig.builder()
         .crcOptions("SOFT_FAIL")
         .build();

      assertEquals(config1.hashCode(), config2.hashCode());
   }

   @Test
   public void testToStringWithCrcOptions() {
      final String crcOptions = "SOFT_FAIL,PREFER_CRLS";

      SSLContextConfig config = SSLContextConfig.builder()
         .crcOptions(crcOptions)
         .build();

      String toString = config.toString();
      assert toString.contains(crcOptions);
   }

   @Test
   public void testBuilderFromExistingConfigWithCrcOptions() {
      final String crcOptions = "SOFT_FAIL,PREFER_CRLS";

      SSLContextConfig originalConfig = SSLContextConfig.builder()
         .crcOptions(crcOptions)
         .build();

      SSLContextConfig copiedConfig = SSLContextConfig.builder()
         .from(originalConfig)
         .build();

      assertEquals(originalConfig.getCrcOptions(), copiedConfig.getCrcOptions());
      assertEquals(crcOptions, copiedConfig.getCrcOptions());
   }

   @Test
   public void testOcspResponderURLInBuilder() {
      final String ocspURL = "http://ocsp.example.com:8080";

      SSLContextConfig config = SSLContextConfig.builder()
         .ocspResponderURL(ocspURL)
         .build();

      assertEquals(ocspURL, config.getOcspResponderURL());
   }

   @Test
   public void testDefaultOcspResponderURL() {
      SSLContextConfig config = SSLContextConfig.builder()
         .build();

      assertNull(config.getOcspResponderURL());
   }

   @Test
   public void testEqualsWithOcspResponderURL() {
      SSLContextConfig config1 = SSLContextConfig.builder()
         .ocspResponderURL("http://ocsp1.example.com")
         .build();

      SSLContextConfig config2 = SSLContextConfig.builder()
         .ocspResponderURL("http://ocsp1.example.com")
         .build();

      SSLContextConfig config3 = SSLContextConfig.builder()
         .ocspResponderURL("http://ocsp2.example.com")
         .build();

      assertEquals(config1, config2);
      assertNotEquals(config1, config3);
   }

   @Test
   public void testHashCodeWithOcspResponderURL() {
      SSLContextConfig config1 = SSLContextConfig.builder()
         .ocspResponderURL("http://ocsp.example.com")
         .build();

      SSLContextConfig config2 = SSLContextConfig.builder()
         .ocspResponderURL("http://ocsp.example.com")
         .build();

      assertEquals(config1.hashCode(), config2.hashCode());
   }

   @Test
   public void testToStringWithOcspResponderURL() {
      final String ocspURL = "http://ocsp.example.com:8080";

      SSLContextConfig config = SSLContextConfig.builder()
         .ocspResponderURL(ocspURL)
         .build();

      String toString = config.toString();
      assert toString.contains(ocspURL);
   }

   @Test
   public void testBuilderFromExistingConfigWithOcspResponderURL() {
      final String ocspURL = "http://ocsp.example.com:8080";

      SSLContextConfig originalConfig = SSLContextConfig.builder()
         .ocspResponderURL(ocspURL)
         .build();

      SSLContextConfig copiedConfig = SSLContextConfig.builder()
         .from(originalConfig)
         .build();

      assertEquals(originalConfig.getOcspResponderURL(), copiedConfig.getOcspResponderURL());
      assertEquals(ocspURL, copiedConfig.getOcspResponderURL());
   }

   @Test
   public void testKeyPasswordInBuilder() {
      final String keyPassword = "keypass123";

      SSLContextConfig config = SSLContextConfig.builder()
         .keyPassword(keyPassword)
         .build();

      assertEquals(keyPassword, config.getKeyPassword());
   }

   @Test
   public void testDefaultKeyPassword() {
      SSLContextConfig config = SSLContextConfig.builder()
         .build();

      assertNull(config.getKeyPassword());
   }

   @Test
   public void testEqualsExcludesKeyPassword() {
      SSLContextConfig config1 = SSLContextConfig.builder()
         .keyPassword("password1")
         .build();

      SSLContextConfig config2 = SSLContextConfig.builder()
         .keyPassword("password2")
         .build();

      SSLContextConfig config3 = SSLContextConfig.builder()
         .build();

      // Passwords are intentionally excluded from equals/hashCode
      assertEquals(config1, config2);
      assertEquals(config1, config3);
   }

   @Test
   public void testToStringMasksKeyPassword() {
      SSLContextConfig config = SSLContextConfig.builder()
         .keyPassword("secretKeyPass")
         .build();

      String toString = config.toString();
      assertTrue(toString.contains("keyPassword=******"));
      assertFalse(toString.contains("secretKeyPass"));
   }

   @Test
   public void testToStringKeyPasswordNull() {
      SSLContextConfig config = SSLContextConfig.builder()
         .build();

      String toString = config.toString();
      assertTrue(toString.contains("keyPassword=null"));
   }

   @Test
   public void testBuilderFromExistingConfigWithKeyPassword() {
      final String keyPassword = "keypass123";

      SSLContextConfig originalConfig = SSLContextConfig.builder()
         .keyPassword(keyPassword)
         .build();

      SSLContextConfig copiedConfig = SSLContextConfig.builder()
         .from(originalConfig)
         .build();

      assertEquals(originalConfig.getKeyPassword(), copiedConfig.getKeyPassword());
      assertEquals(keyPassword, copiedConfig.getKeyPassword());
   }
}
