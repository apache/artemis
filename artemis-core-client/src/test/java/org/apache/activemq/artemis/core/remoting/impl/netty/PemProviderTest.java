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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import de.dentrassi.crypto.pem.PemKeyStoreProvider;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Order is important here because we don't want to load the PEM provider class before we test that it isn't loaded.
 */
@TestMethodOrder(OrderAnnotation.class)
public class PemProviderTest {

   // use a literal to avoid implicitly loading the actual package/class
   static final String PEM_PROVIDER_PACKAGE = "de.dentrassi.crypto.pem";

   @Test
   @Order(1)
   public void testPemProviderNotLoadedOnSSLSupportStaticUse() {
      // ensure the PEM provider isn't already loaded (e.g. by another test)
      assertNull(ClassLoader.getSystemClassLoader().getDefinedPackage(PEM_PROVIDER_PACKAGE));

      // use a static method from SSLSupport to force the JVM to load it as well as any hard dependencies it has
      SSLSupport.parseCommaSeparatedListIntoArray("");

      assertNull(ClassLoader.getSystemClassLoader().getDefinedPackage(PEM_PROVIDER_PACKAGE));
   }

   @Test
   @Order(2)
   public void testPemProviderNotLoadedOnLoadingNonPemKeystore() throws Exception {
      // ensure the PEM provider isn't already loaded (e.g. by another test)
      assertNull(ClassLoader.getSystemClassLoader().getDefinedPackage(PEM_PROVIDER_PACKAGE));

      SSLSupport.loadKeystore(null, "JKS", "", "");

      assertNull(ClassLoader.getSystemClassLoader().getDefinedPackage(PEM_PROVIDER_PACKAGE));
   }

   @Test
   @Order(3)
   public void testPemProviderLoadedOnLoadingPemKeystore() throws Exception {
      // ensure the PEM provider isn't already loaded (e.g. by another test)
      assertNull(ClassLoader.getSystemClassLoader().getDefinedPackage(PEM_PROVIDER_PACKAGE));

      SSLSupport.loadKeystore(null, "PEM", "", "");

      assertNotNull(ClassLoader.getSystemClassLoader().getDefinedPackage(PEM_PROVIDER_PACKAGE));
   }

   /**
    * This test simply verifies that we're using the right literal for the PEM provider implementation.
    */
   @Test
   @Order(4)
   public void testPemProviderPackageName() {
      assertEquals(PEM_PROVIDER_PACKAGE, PemKeyStoreProvider.class.getPackageName());
   }
}
