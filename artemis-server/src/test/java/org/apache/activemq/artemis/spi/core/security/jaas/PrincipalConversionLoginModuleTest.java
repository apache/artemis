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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

public class PrincipalConversionLoginModuleTest {

   @Test
   public void loginOk() throws Exception {
      PrincipalConversionLoginModule underTest = new PrincipalConversionLoginModule();

      final Subject subject = new Subject();
      underTest.initialize(subject, null, null, new HashMap<>());

      assertTrue(underTest.login());
      assertFalse(underTest.commit());
      assertTrue(subject.getPrincipals().isEmpty());
   }

   @Test
   public void loginOkOnNullSubject() throws Exception {
      PrincipalConversionLoginModule underTest = new PrincipalConversionLoginModule();

      underTest.initialize(null, null, null, new HashMap<>());

      assertTrue(underTest.login());
      assertFalse(underTest.commit());
      assertTrue(underTest.logout());

   }

   @Test
   public void loginConvert() throws Exception {
      PrincipalConversionLoginModule underTest = new PrincipalConversionLoginModule();

      final Subject subject = new Subject();
      final HashMap<String, String> options = new HashMap<>();
      options.put(PrincipalConversionLoginModule.PRINCIPAL_CLASS_LIST, RolePrincipal.class.getCanonicalName());
      subject.getPrincipals().add(new RolePrincipal("BLA"));
      underTest.initialize(subject, null, null, options);

      assertTrue(underTest.login());
      assertTrue(underTest.commit());
      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      assertEquals("BLA", ((Principal)subject.getPrincipals(UserPrincipal.class).iterator().next()).getName());

      underTest.logout();
      assertEquals(0, subject.getPrincipals(UserPrincipal.class).size());
   }


   @Test
   public void loginNoOverwriteExistingUserPrincipal() throws Exception {
      PrincipalConversionLoginModule underTest = new PrincipalConversionLoginModule();

      final Subject subject = new Subject();
      final HashMap<String, String> options = new HashMap<>();
      options.put(PrincipalConversionLoginModule.PRINCIPAL_CLASS_LIST, RolePrincipal.class.getCanonicalName());
      subject.getPrincipals().add(new RolePrincipal("BLA"));
      subject.getPrincipals().add(new UserPrincipal("Important"));

      underTest.initialize(subject, null, null, options);

      assertTrue(underTest.login());
      assertFalse(underTest.commit());
      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      assertEquals("Important", ((Principal)subject.getPrincipals(UserPrincipal.class).iterator().next()).getName());
   }



   static final class TestPrincipal implements Principal {

      @Override
      public boolean equals(Object obj) {
         return this == obj;
      }

      @Override
      public String toString() {
         return null;
      }

      @Override
      public int hashCode() {
         return 0;
      }

      @Override
      public String getName() {
         return "TestPrincipal";
      }
   }

   @Test
   public void loginConvertList() throws Exception {
      PrincipalConversionLoginModule underTest = new PrincipalConversionLoginModule();

      final Subject subject = new Subject();
      final HashMap<String, String> options = new HashMap<>();
      String multiOptionList = TestPrincipal.class.getTypeName() + ", " + RolePrincipal.class.getTypeName();
      options.put(PrincipalConversionLoginModule.PRINCIPAL_CLASS_LIST, multiOptionList);
      subject.getPrincipals().add(new TestPrincipal());
      underTest.initialize(subject, null, null, options);

      assertTrue(underTest.login());
      assertTrue(underTest.commit());
      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      assertEquals("TestPrincipal", ((Principal)subject.getPrincipals(UserPrincipal.class).iterator().next()).getName());
   }

}
