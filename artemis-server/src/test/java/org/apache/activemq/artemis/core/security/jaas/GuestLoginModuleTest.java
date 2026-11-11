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
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuestLoginModuleTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = GuestLoginModuleTest.class.getClassLoader().getResource("login.config");
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

   @Test
   public void testLogin() throws LoginException {
      LoginContext context = new LoginContext("GuestLogin", callbacks -> assertEquals(0, callbacks.length, "Should have no Callbacks"));
      context.login();

      Subject subject = context.getSubject();

      assertEquals(2, subject.getPrincipals().size(), "Should have two principals");
      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size(), "Should have one user principal");
      assertTrue(subject.getPrincipals(UserPrincipal.class).contains(new UserPrincipal("foo")), "User principal is 'foo'");

      assertEquals(1, subject.getPrincipals(RolePrincipal.class).size(), "Should have one group principal");
      assertTrue(subject.getPrincipals(RolePrincipal.class).contains(new RolePrincipal("bar")), "Role principal is 'bar'");

      context.logout();

      assertEquals(0, subject.getPrincipals().size(), "Should have zero principals");
   }

   @Test
   public void testLoginWithDefaults() throws LoginException {
      LoginContext context = new LoginContext("GuestLoginWithDefaults", callbacks -> assertEquals(0, callbacks.length, "Should have no Callbacks"));
      context.login();

      Subject subject = context.getSubject();

      assertEquals(2, subject.getPrincipals().size(), "Should have two principals");
      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size(), "Should have one user principal");
      assertTrue(subject.getPrincipals(UserPrincipal.class).contains(new UserPrincipal("guest")), "User principal is 'guest'");

      assertEquals(1, subject.getPrincipals(RolePrincipal.class).size(), "Should have one group principal");
      assertTrue(subject.getPrincipals(RolePrincipal.class).contains(new RolePrincipal("guests")), "Role principal is 'guests'");

      context.logout();

      assertEquals(0, subject.getPrincipals().size(), "Should have zero principals");
   }
}
