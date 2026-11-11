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
package org.apache.activemq.artemis.dto.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.dto.BindingDTO;
import org.junit.jupiter.api.Test;

public class BindingDTOTest {

   @Test
   public void testDefault() {
      BindingDTO binding = new BindingDTO();

      assertNull(binding.getIncludedTLSProtocols());
      assertNull(binding.getExcludedTLSProtocols());
      assertNull(binding.getIncludedCipherSuites());
      assertNull(binding.getExcludedCipherSuites());
   }

   @Test
   public void testValues() {
      BindingDTO binding = new BindingDTO();

      binding.setIncludedTLSProtocols("TLSv1.2");
      assertArrayEquals(new String[] {"TLSv1.2"}, binding.getIncludedTLSProtocols());

      binding.setExcludedTLSProtocols("TLSv1,TLSv1.1");
      assertArrayEquals(new String[] {"TLSv1", "TLSv1.1"}, binding.getExcludedTLSProtocols());

      binding.setIncludedCipherSuites("^SSL_.*$");
      assertArrayEquals(new String[] {"^SSL_.*$"}, binding.getIncludedCipherSuites());

      binding.setExcludedCipherSuites("^.*_(MD5|SHA|SHA1)$,^TLS_RSA_.*$,^.*_NULL_.*$,^.*_anon_.*$");
      assertArrayEquals(new String[] {"^.*_(MD5|SHA|SHA1)$", "^TLS_RSA_.*$", "^.*_NULL_.*$", "^.*_anon_.*$"}, binding.getExcludedCipherSuites());
   }

   @Test
   public void testEmptyValues() {
      BindingDTO binding = new BindingDTO();

      binding.setIncludedTLSProtocols("");
      assertArrayEquals(new String[] {""}, binding.getIncludedTLSProtocols());

      binding.setExcludedTLSProtocols("");
      assertArrayEquals(new String[] {""}, binding.getExcludedTLSProtocols());

      binding.setIncludedCipherSuites("");
      assertArrayEquals(new String[] {""}, binding.getIncludedCipherSuites());

      binding.setExcludedCipherSuites("");
      assertArrayEquals(new String[] {""}, binding.getExcludedCipherSuites());
   }

   @Test
   public void testNullValues() {
      BindingDTO binding = new BindingDTO();

      binding.setIncludedTLSProtocols(null);
      assertNull(binding.getIncludedTLSProtocols());

      binding.setExcludedTLSProtocols(null);
      assertNull(binding.getExcludedTLSProtocols());

      binding.setIncludedCipherSuites(null);
      assertNull(binding.getIncludedCipherSuites());

      binding.setExcludedCipherSuites(null);
      assertNull(binding.getExcludedCipherSuites());
   }
}
