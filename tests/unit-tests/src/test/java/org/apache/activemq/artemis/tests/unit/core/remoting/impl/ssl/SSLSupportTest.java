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

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
@RunWith(value = Parameterized.class)
public class SSLSupportTest extends ActiveMQTestBase {

   @Parameterized.Parameters(name = "storeProvider={0}, storeType={1}")
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
   @Before
   public void setUp() throws Exception {
      super.setUp();
      keyStorePassword = "securepass";
      trustStorePassword = keyStorePassword;
   }

   @Test
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
   @Test
   public void testContextWithNullParameters() throws Exception {
      new SSLSupport().createContext();
   }

   @Test
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

   @Test
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

   @Test
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
         Assert.fail();
      } catch (Exception e) {
      }
   }

   @Test
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
         Assert.fail();
      }
   }

   @Test
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

   @Test
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
         Assert.fail();
      } catch (Exception e) {
      }
   }

   @Test
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
         Assert.fail();
      } catch (Exception e) {
         assertFalse(e instanceof NullPointerException);
      }
   }

   @Test
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
         Assert.fail();
      } catch (Exception e) {
      }
   }

   @Test
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
         Assert.fail();
      } catch (Exception e) {
      }
   }

   @Test
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
}
