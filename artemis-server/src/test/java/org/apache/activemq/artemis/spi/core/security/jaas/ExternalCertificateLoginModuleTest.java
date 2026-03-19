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

import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalCertificateLoginModuleTest {

   @Test
   void loginFails() throws LoginException {

      ExternalCertificateLoginModule underTest = new ExternalCertificateLoginModule();

      final Subject subject = new Subject();
      underTest.initialize(subject, callbacks -> {
      }, null, null);

      assertFalse(underTest.login());
      assertTrue(subject.getPrincipals().isEmpty());
   }

   @Test
   void loginSuccess() throws Exception {
      ExternalCertificateLoginModule underTest = new ExternalCertificateLoginModule();

      String ksPath = ClassloadingUtil.findResource("san-keystore.p12").getPath();
      KeyStore ks = KeyStore.getInstance("PKCS12");
      try (FileInputStream fis = new FileInputStream(ksPath)) {
         ks.load(fis, "securepass".toCharArray());
      }
      X509Certificate cert = (X509Certificate) ks.getCertificate("san-roles");

      Subject subject = new Subject();
      underTest.initialize(subject, callbacks -> {
         ((CertificateCallback) callbacks[0]).setCertificates(new X509Certificate[]{cert});
      }, null, Map.of(ExternalCertificateLoginModule.SAN_URI_ROLE_PREFIX_PROP, "urn:jaas:role:"));

      assertTrue(underTest.login());
      assertTrue(underTest.commit());
      assertFalse(subject.getPrincipals().isEmpty());
      assertEquals("CN=ok", subject.getPrincipals(UserPrincipal.class).iterator().next().getName());
      assertTrue(subject.getPrincipals(RolePrincipal.class).contains(new RolePrincipal("admin")));
      assertTrue(subject.getPrincipals(RolePrincipal.class).contains(new RolePrincipal("view")));

      // again without the prefix property and same cert
      underTest = new ExternalCertificateLoginModule();
      subject = new Subject();
      underTest.initialize(subject, callbacks -> {
         ((CertificateCallback) callbacks[0]).setCertificates(new X509Certificate[]{cert});
      }, null, null);

      assertTrue(underTest.login());
      assertTrue(underTest.commit());
      assertFalse(subject.getPrincipals().isEmpty());
      assertEquals("CN=ok", subject.getPrincipals(UserPrincipal.class).iterator().next().getName());
      assertTrue(subject.getPrincipals(RolePrincipal.class).isEmpty());
   }
}