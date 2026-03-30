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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class SSLSupportTest extends ActiveMQTestBase {

   @Parameters(name = "storeProvider={0}, storeType={1}")
   public static Collection getParameters() {
      if (System.getProperty("java.vendor").contains("IBM")) {
         return Arrays.asList(new Object[][]{
            {TransportConstants.DEFAULT_KEYSTORE_PROVIDER, TransportConstants.DEFAULT_KEYSTORE_TYPE},
            {"IBMJCE", "JCEKS"}
         });
      } else {
         return Arrays.asList(new Object[][]{
            {TransportConstants.DEFAULT_KEYSTORE_PROVIDER, TransportConstants.DEFAULT_KEYSTORE_TYPE},
            {"SunJCE", "JCEKS"},
            {"SUN", "JKS"},
            {"SunJSSE", "PKCS12"}
         });
      }
   }

   public SSLSupportTest(String storeProvider, String storeType) {
      this.storeProvider = storeProvider;
      this.storeType = storeType;
      String suffix = storeType.toLowerCase();
      if (storeType.equals("PKCS12")) {
         suffix = "p12";
      }
      keyStorePath = "server-keystore." + suffix;
      trustStorePath = "client-ca-truststore." + suffix;
   }

   private String storeProvider;

   private String storeType;

   private String keyStorePath;

   private String keyStorePassword;

   private String trustStorePath;

   private String trustStorePassword;




   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      keyStorePassword = "securepass";
      trustStorePassword = keyStorePassword;
   }

   @TestTemplate
   public void testContextWithRightParameters() throws Exception {
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
   }

   // This is valid as it will create key and trust managers with system defaults
   @TestTemplate
   public void testContextWithNullParameters() throws Exception {
      new SSLSupport().createContext();
   }

   @TestTemplate
   public void testContextWithKeyStorePathAsURL() throws Exception {
      URL url = Thread.currentThread().getContextClassLoader().getResource(keyStorePath);
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(url.toString())
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
   }

   @TestTemplate
   public void testContextWithKeyStorePathAsFile() throws Exception {
      URL url = Thread.currentThread().getContextClassLoader().getResource(keyStorePath);
      File file = new File(url.toURI());
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(file.getAbsolutePath())
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
   }

   @TestTemplate
   public void testContextWithBadKeyStorePath() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath("not a keystore")
            .setKeystorePassword(keyStorePassword)
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword(trustStorePassword)
            .createContext();
         fail();
      } catch (Exception e) {
      }
   }

   @TestTemplate
   public void testContextWithNullKeyStorePath() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath(null)
            .setKeystorePassword(keyStorePassword)
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword(trustStorePassword)
            .createContext();
      } catch (Exception e) {
         fail();
      }
   }

   @TestTemplate
   public void testContextWithKeyStorePathAsRelativePath() throws Exception {
      // this test is dependent on a path relative to the tests directory.
      // it will fail if launch from somewhere else (or from an IDE)
      File currentDir = new File(System.getProperty("user.dir"));
      if (!currentDir.getAbsolutePath().endsWith("tests")) {
         return;
      }

      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath("../security-resources/" + keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
   }

   @TestTemplate
   public void testContextWithBadKeyStorePassword() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath(keyStorePath)
            .setKeystorePassword("bad password")
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword(trustStorePassword)
            .createContext();
         fail();
      } catch (Exception e) {
      }
   }

   @TestTemplate
   public void testContextWithNullKeyStorePassword() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath(keyStorePath)
            .setKeystorePassword(null)
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword(trustStorePassword)
            .createContext();
         fail();
      } catch (Exception e) {
         assertFalse(e instanceof NullPointerException);
      }
   }

   @TestTemplate
   public void testContextWithBadTrustStorePath() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath(keyStorePath)
            .setKeystorePassword(keyStorePassword)
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath("not a trust store")
            .setTruststorePassword(trustStorePassword)
            .createContext();
         fail();
      } catch (Exception e) {
      }
   }

   @TestTemplate
   public void testContextWithBadTrustStorePassword() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath(keyStorePath)
            .setKeystorePassword(keyStorePassword)
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword("bad passord")
            .createContext();
         fail();
      } catch (Exception e) {
      }
   }

   @TestTemplate
   public void testContextWithDifferentKeyPassword() throws Exception {
      // JKS and JCEKS keystores support a private key password different from the store password
      if (!"JKS".equals(storeType) && !"JCEKS".equals(storeType)) {
         return;
      }
      String suffix = storeType.toLowerCase();
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath("server-keystore-keypass." + suffix)
         .setKeystorePassword(keyStorePassword)
         .setKeyPassword("keypass123")
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
   }

   @TestTemplate
   public void testContextWithWrongKeyPassword() throws Exception {
      // Verify that a wrong key password fails
      if (!"JKS".equals(storeType) && !"JCEKS".equals(storeType)) {
         return;
      }
      String suffix = storeType.toLowerCase();
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath("server-keystore-keypass." + suffix)
            .setKeystorePassword(keyStorePassword)
            .setKeyPassword("wrongpassword")
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword(trustStorePassword)
            .createContext();
         fail();
      } catch (Exception e) {
      }
   }

   @TestTemplate
   public void testContextWithKeyPasswordFallback() throws Exception {
      // When keyPassword is not set, keystorePassword is used (existing behavior)
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
   }

   @TestTemplate
   public void testContextWithTrustAll() throws Exception {
      //This is using a bad password but should not fail because the trust store should be ignored with
      //the trustAll flag set to true
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword("bad passord")
         .setTrustAll(true)
         .createContext();
   }

   @TestTemplate
   public void testContextWithCrcOptions() throws Exception {
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .setCrcOptions("SOFT_FAIL")
         .createContext();
   }

   @TestTemplate
   public void testContextWithMultipleCrcOptions() throws Exception {
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .setCrcOptions("SOFT_FAIL,PREFER_CRLS,NO_FALLBACK")
         .createContext();
   }

   @TestTemplate
   public void testContextWithInvalidCrcOptions() throws Exception {
      try {
         new SSLSupport()
            .setKeystoreProvider(storeProvider)
            .setKeystoreType(storeType)
            .setKeystorePath(keyStorePath)
            .setKeystorePassword(keyStorePassword)
            .setTruststoreProvider(storeProvider)
            .setTruststoreType(storeType)
            .setTruststorePath(trustStorePath)
            .setTruststorePassword(trustStorePassword)
            .setCrcOptions("INVALID_OPTION")
            .createContext();
      } catch (IllegalArgumentException e) {
         // Expected exception
      }
   }

   @TestTemplate
   public void testContextWithOcspResponderURL() throws Exception {
      new SSLSupport()
         .setKeystoreProvider(storeProvider)
         .setKeystoreType(storeType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(storeProvider)
         .setTruststoreType(storeType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .setOcspResponderURL("http://localhost:8080")
         .createContext();
   }
}
