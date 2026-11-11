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

import org.junit.jupiter.api.Test;

import java.security.cert.PKIXRevocationChecker;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SSLSupportTest {
   @Test
   public void testLoadRevocationOptionsWithSingleOption() throws Exception {
      SSLSupport sslSupport = new SSLSupport();
      sslSupport.setCrcOptions("SOFT_FAIL");

      Set<PKIXRevocationChecker.Option> options = sslSupport.loadRevocationOptions();

      assertEquals(1, options.size());
      assertTrue(options.contains(PKIXRevocationChecker.Option.SOFT_FAIL));
   }

   @Test
   public void testLoadRevocationOptionsWithMultipleOptions() throws Exception {
      SSLSupport sslSupport = new SSLSupport();
      sslSupport.setCrcOptions("SOFT_FAIL,PREFER_CRLS,NO_FALLBACK");

      Set<PKIXRevocationChecker.Option> options = sslSupport.loadRevocationOptions();

      assertEquals(3, options.size());
      assertTrue(options.contains(PKIXRevocationChecker.Option.SOFT_FAIL));
      assertTrue(options.contains(PKIXRevocationChecker.Option.PREFER_CRLS));
      assertTrue(options.contains(PKIXRevocationChecker.Option.NO_FALLBACK));
   }

   @Test
   public void testLoadRevocationOptionsWithInvalidOption() {
      SSLSupport sslSupport = new SSLSupport();
      sslSupport.setCrcOptions("INVALID_OPTION");

      try {
         sslSupport.loadRevocationOptions();
         fail("Expected IllegalArgumentException for invalid CRC option");
      } catch (IllegalArgumentException e) {
         // Expected exception
      }
   }

   @Test
   public void testLoadRevocationOptionsWithMixedValidInvalid() {
      SSLSupport sslSupport = new SSLSupport();
      sslSupport.setCrcOptions("SOFT_FAIL,INVALID_OPTION");

      try {
         sslSupport.loadRevocationOptions();
         fail("Expected IllegalArgumentException for invalid CRC option");
      } catch (IllegalArgumentException e) {
         // Expected exception
      }
   }

   @Test
   public void testLoadRevocationOptionsWithWhitespace() throws Exception {
      SSLSupport sslSupport = new SSLSupport();
      sslSupport.setCrcOptions(" SOFT_FAIL , PREFER_CRLS ");

      try {
         sslSupport.loadRevocationOptions();
         fail("Expected IllegalArgumentException for CRC options with whitespaces");
      } catch (IllegalArgumentException e) {
         // Expected exception
      }
   }

   @Test
   public void testEmptyCrcOptions() {
      SSLSupport sslSupport = new SSLSupport();
      sslSupport.setCrcOptions("");

      try {
         sslSupport.loadRevocationOptions();
         fail("Expected IllegalArgumentException for empty CRC options");
      } catch (IllegalArgumentException e) {
         // Expected exception
      }
   }
}
