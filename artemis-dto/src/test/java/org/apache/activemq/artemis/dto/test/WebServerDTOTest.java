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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.dto.BindingDTO;
import org.apache.activemq.artemis.dto.WebServerDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class WebServerDTOTest {

   @Test
   public void testDefault() throws Exception {
      WebServerDTO webServer = new WebServerDTO();

      assertNotNull(webServer.getAllBindings());
      assertEquals(1, webServer.getAllBindings().size());
      assertNotNull(webServer.getDefaultBinding());

      BindingDTO defaultBinding = webServer.getDefaultBinding();
      assertNull(defaultBinding.uri);
      assertNull(defaultBinding.apps);
      assertNull(defaultBinding.clientAuth);
      assertNull(defaultBinding.passwordCodec);
      assertNull(defaultBinding.keyStorePath);
      assertNull(defaultBinding.trustStorePath);
      assertNull(defaultBinding.getIncludedTLSProtocols());
      assertNull(defaultBinding.getExcludedTLSProtocols());
      assertNull(defaultBinding.getIncludedCipherSuites());
      assertNull(defaultBinding.getExcludedCipherSuites());
      assertNull(defaultBinding.getKeyStorePassword());
      assertNull(defaultBinding.getTrustStorePassword());
      assertNull(defaultBinding.getSniHostCheck());
      assertNull(defaultBinding.getSniRequired());
      assertNull(defaultBinding.getSslAutoReload());
   }

   @Test
   public void testWebServerConfig() {
      WebServerDTO webServer = new WebServerDTO();
      webServer.bind = "http://localhost:0";

      assertNotNull(webServer.getAllBindings());
      assertEquals(1, webServer.getAllBindings().size());
      assertNotNull(webServer.getDefaultBinding());
      assertEquals("http://localhost:0", webServer.getDefaultBinding().uri);
   }

   @Test
   public void testWebServerWithBinding() {
      BindingDTO binding = new BindingDTO();
      binding.uri = "http://localhost:0";

      WebServerDTO webServer = new WebServerDTO();
      webServer.setBindings(Collections.singletonList(binding));

      assertNotNull(webServer.getAllBindings());
      assertEquals(1, webServer.getAllBindings().size());
      assertNotNull(webServer.getDefaultBinding());
      assertEquals("http://localhost:0", webServer.getDefaultBinding().uri);
   }

   @Test
   public void testWebServerWithMultipleBindings() {
      BindingDTO binding1 = new BindingDTO();
      binding1.uri = "http://localhost:0";
      BindingDTO binding2 = new BindingDTO();
      binding2.uri = "http://localhost:1";

      WebServerDTO webServer = new WebServerDTO();
      webServer.setBindings(List.of(binding1, binding2));

      assertNotNull(webServer.getAllBindings());
      assertEquals(2, webServer.getAllBindings().size());
      assertNotNull(webServer.getDefaultBinding());
      assertEquals("http://localhost:0", webServer.getDefaultBinding().uri);
      assertEquals("http://localhost:0", webServer.getAllBindings().get(0).uri);
      assertEquals("http://localhost:1", webServer.getAllBindings().get(1).uri);
   }

   @Test
   public void testWebServerConfigAndBinding() {
      BindingDTO binding = new BindingDTO();
      binding.uri = "http://localhost:0";

      WebServerDTO webServer = new WebServerDTO();
      webServer.bind = "http://localhost:1";
      webServer.setBindings(Collections.singletonList(binding));

      assertNotNull(webServer.getAllBindings());
      assertEquals(1, webServer.getAllBindings().size());
      assertNotNull(webServer.getDefaultBinding());
      assertEquals("http://localhost:0", webServer.getDefaultBinding().uri);
   }

}
