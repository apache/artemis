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
package org.apache.activemq.artemis.core.security.jaas;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Map;

import org.apache.activemq.artemis.spi.core.security.jaas.CertificateCallback;
import org.apache.activemq.artemis.spi.core.security.jaas.CertificateLoginModule;
import org.apache.activemq.artemis.spi.core.security.jaas.JaasCallbackHandler;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoader;
import org.apache.activemq.artemis.spi.core.security.jaas.TextFileUpnCertificateLoginModule;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.CertificateUtilTest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class TextFileUpnCertificateLoginModuleTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String CERT_USERS_FILE = "upn-cert-users.properties";

   private static final String CERT_GROUPS_FILE = "upn-cert-roles.properties";

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = TextFileUpnCertificateLoginModuleTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            try {
               path = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8.name());
               System.setProperty("java.security.auth.login.config", path);
            } catch (UnsupportedEncodingException e) {
               logger.error(e.getMessage(), e);
               throw new RuntimeException(e);
            }
         }
      }
   }

   private CertificateLoginModule loginModule;

   @BeforeAll
   static void setupProvider() {
      Security.addProvider(new BouncyCastleProvider());
   }

   @AfterAll
   static void cleanupProvider() {
      Security.removeProvider("BC");
   }

   @BeforeEach
   public void setUp() throws Exception {
      loginModule = new TextFileUpnCertificateLoginModule();
   }

   @AfterEach
   public void tearDown() throws Exception {
      PropertiesLoader.resetUsersAndGroupsCache();
   }

   @Test
   public void loginTest() throws Exception {
      Map<String, String> options = Map.of("org.apache.activemq.jaas.textfileupn.user", CERT_USERS_FILE,
                                           "org.apache.activemq.jaas.textfileupn.role", CERT_GROUPS_FILE,
                                           "reload", "true");

      for (int i = 0; i < 10; i++) {
         final String user = "user@domain" + (i + 1) + ".com";
         Subject subject = doAuthenticate(options, getJaasCertificateCallbackHandler(user));
         assertEquals(1, subject.getPrincipals().size());
         Principal principal = subject.getPrincipals().iterator().next();
         assertInstanceOf(UserPrincipal.class, principal);
         assertEquals(user, principal.getName());
         loginModule.logout();
      }
   }

   private JaasCallbackHandler getJaasCertificateCallbackHandler(String user) throws Exception {
      X509Certificate cert = CertificateUtilTest.generateCertificateWithUPN(user);
      return new JaasCallbackHandler(null, null, null) {
         @Override
         public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
               if (callback instanceof CertificateCallback certCallback) {
                  certCallback.setCertificates(new X509Certificate[]{cert});
               } else {
                  throw new UnsupportedCallbackException(callback);
               }
            }
         }
      };
   }

   private Subject doAuthenticate(Map<String, ?> options,
                                  JaasCallbackHandler callbackHandler) throws LoginException {
      Subject mySubject = new Subject();
      loginModule.initialize(mySubject, callbackHandler, null, options);
      loginModule.login();
      loginModule.commit();
      return mySubject;
   }
}
