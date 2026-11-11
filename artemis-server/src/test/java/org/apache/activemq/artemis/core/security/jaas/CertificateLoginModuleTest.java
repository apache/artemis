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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.activemq.artemis.spi.core.security.jaas.JaasCallbackHandler;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CertificateLoginModuleTest {

   private static final String USER_NAME = "testUser";
   private static final List<String> ROLE_NAMES = new Vector<>();

   private StubCertificateLoginModule loginModule;

   private Subject subject;

   public CertificateLoginModuleTest() {
      ROLE_NAMES.add("testRole1");
      ROLE_NAMES.add("testRole2");
      ROLE_NAMES.add("testRole3");
      ROLE_NAMES.add("testRole4");
   }

   @BeforeEach
   public void setUp() throws Exception {
      subject = new Subject();
   }

   private void loginWithCredentials(String userName, Set<String> rolesNames) throws LoginException {
      loginModule = new StubCertificateLoginModule(userName, new HashSet<>(rolesNames));
      JaasCallbackHandler callbackHandler = new JaasCallbackHandler(null, null, null);

      loginModule.initialize(subject, callbackHandler, null, new HashMap<>());

      loginModule.login();
      loginModule.commit();
   }

   private void checkPrincipalsMatch(Subject subject) {
      boolean nameFound = false;
      boolean[] rolesFound = new boolean[ROLE_NAMES.size()];
      for (int i = 0; i < rolesFound.length; ++i) {
         rolesFound[i] = false;
      }

      for (Principal currentPrincipal : subject.getPrincipals()) {
         if (currentPrincipal instanceof UserPrincipal) {
            if (currentPrincipal.getName().equals(USER_NAME)) {
               if (!nameFound) {
                  nameFound = true;
               } else {
                  fail("UserPrincipal found twice.");
               }

            } else {
               fail("Unknown UserPrincipal found.");
            }

         } else if (currentPrincipal instanceof RolePrincipal rolePrincipal) {
            int principalIdx = ROLE_NAMES.indexOf(rolePrincipal.getName());

            if (principalIdx < 0) {
               fail("Unknown RolePrincipal found.");
            }

            if (!rolesFound[principalIdx]) {
               rolesFound[principalIdx] = true;
            } else {
               fail("RolePrincipal found twice.");
            }
         } else {
            fail("Unknown Principal type found.");
         }
      }
   }

   @Test
   public void testLoginSuccess() throws IOException {
      try {
         loginWithCredentials(USER_NAME, new HashSet<>(ROLE_NAMES));
      } catch (Exception e) {
         fail("Unable to login: " + e.getMessage());
      }

      checkPrincipalsMatch(subject);
   }

   @Test
   public void testLoginFailure() throws IOException {
      boolean loginFailed = false;

      try {
         loginWithCredentials(null, new HashSet<>());
      } catch (LoginException e) {
         loginFailed = true;
      }

      if (!loginFailed) {
         fail("Logged in with unknown certificate.");
      }
   }

   @Test
   public void testLogOut() throws IOException {
      try {
         loginWithCredentials(USER_NAME, new HashSet<>(ROLE_NAMES));
      } catch (Exception e) {
         fail("Unable to login: " + e.getMessage());
      }

      loginModule.logout();

      assertEquals(0, subject.getPrincipals().size(), "logout should have cleared Subject principals.");
   }
}
