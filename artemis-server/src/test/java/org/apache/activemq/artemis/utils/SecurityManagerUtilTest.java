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

package org.apache.activemq.artemis.utils;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityManagerUtilTest {

   @Test
   public void getUserFromSubject() throws Exception {
      assertNull(SecurityManagerUtil.getUserFromSubject(null, null));
      Subject subject = new Subject();
      assertNull(SecurityManagerUtil.getUserFromSubject(subject, UserPrincipal.class));
      subject.getPrincipals().add(new RolePrincipal("r"));
      assertNull(SecurityManagerUtil.getUserFromSubject(subject, UserPrincipal.class));
      subject.getPrincipals().add(new UserPrincipal("u"));
      assertEquals("u", SecurityManagerUtil.getUserFromSubject(subject, UserPrincipal.class));
   }

   class UserFromOtherDomainPrincipal implements Principal {
      @Override
      public String getName() {
         return "P";
      }
   }

   @Test
   public void getUserFromForeignPrincipalInSubject() throws Exception {
      Subject subject = new Subject();
      subject.getPrincipals().add(new UserFromOtherDomainPrincipal());
      assertNull(SecurityManagerUtil.getUserFromSubject(subject, UserPrincipal.class));
   }

   @Test
   public void testAuthorizeWithNullSubject() {
      Set<Role> roles = Set.of(new Role("user", true, true, true, true, true, true, true, true, true, true, true, true));

      assertFalse(SecurityManagerUtil.authorize(null, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithEmptySubject() {
      Set<Role> roles = Set.of(new Role("user", true, true, true, true, true, true, true, true, true, true, true, true));

      assertFalse(SecurityManagerUtil.authorize(new Subject(), roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithEmptyRoles() {
      Subject subject = getSubject("user");

      assertFalse(SecurityManagerUtil.authorize(subject,  new HashSet<>(), CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithMatchingRole() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", true, false, false, false, false, false, false, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithNonMatchingRole() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("admin", true, false, false, false, false, false, false, false, false, false, false, false));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithRoleWithoutPermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, false, false, false, false, false, false));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithMultipleRolesOneMatching() {
      Subject subject = getSubject("user", "guest");

      Set<Role> roles = Set.of(new Role("admin", true, false, false, false, false, false, false, false, false, false, false, false),
                               new Role("user", true, false, false, false, false, false, false, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithMultipleRolesNoneMatching() {
      Subject subject = getSubject("user", "guest");

      Set<Role> roles = Set.of(new Role("admin", true, false, false, false, false, false, false, false, false, false, false, false),
                               new Role("superuser", true, false, false, false, false, false, false, false, false, false, false, false));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithDifferentCheckTypes() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, true, false, false, false, false, false, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.CONSUME, RolePrincipal.class));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithManagePermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, true, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.MANAGE, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithBrowsePermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, false, true, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.BROWSE, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithCreateAddressPermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, false, false, true, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.CREATE_ADDRESS, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithDeleteAddressPermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, false, false, false, true, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.DELETE_ADDRESS, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithViewPermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, false, false, false, false, true, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.VIEW, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithEditPermission() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, false, false, false, false, false, false, false, false, false, true));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.EDIT, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithMultiplePermissionsOnRole() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", true, true, true, true, false, false, false, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.CONSUME, RolePrincipal.class));
      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.CREATE_DURABLE_QUEUE, RolePrincipal.class));
      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.DELETE_DURABLE_QUEUE, RolePrincipal.class));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.MANAGE, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithSubjectHavingUserAndRolePrincipals() {
      Subject subject = new Subject();
      subject.getPrincipals().add(new UserPrincipal("john"));
      subject.getPrincipals().add(new RolePrincipal("admin"));

      Set<Role> roles = Set.of(new Role("admin", true, false, false, false, false, false, false, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithSubjectHavingOnlyUserPrincipal() {
      Subject subject = new Subject();
      subject.getPrincipals().add(new UserPrincipal("admin"));

      Set<Role> roles = Set.of(new Role("admin", true, false, false, false, false, false, false, false, false, false, false, false));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
   }

   @Test
   public void testAuthorizeWithQueuePermissions() {
      Subject subject = getSubject("user");

      Set<Role> roles = Set.of(new Role("user", false, false, true, true, true, true, false, false, false, false, false, false));

      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.CREATE_DURABLE_QUEUE, RolePrincipal.class));
      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.DELETE_DURABLE_QUEUE, RolePrincipal.class));
      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.CREATE_NON_DURABLE_QUEUE, RolePrincipal.class));
      assertTrue(SecurityManagerUtil.authorize(subject, roles, CheckType.DELETE_NON_DURABLE_QUEUE, RolePrincipal.class));

      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.SEND, RolePrincipal.class));
      assertFalse(SecurityManagerUtil.authorize(subject, roles, CheckType.CONSUME, RolePrincipal.class));
   }

   private Subject getSubject(String... roles) {
      Subject subject = new Subject();
      for (String role : roles) {
         subject.getPrincipals().add(new RolePrincipal(role));
      }
      return subject;
   }

   @Test
   public void testCreateGroupPrincipalWithStringConstructor() throws Exception {
      Object principal = SecurityManagerUtil.createGroupPrincipal("testRole", RolePrincipal.class);

      assertNotNull(principal);
      assertTrue(principal instanceof RolePrincipal);
      assertEquals("testRole", ((RolePrincipal) principal).getName());
   }

   @Test
   public void testCreateGroupPrincipalWithUserPrincipal() throws Exception {
      Object principal = SecurityManagerUtil.createGroupPrincipal("testUser", UserPrincipal.class);

      assertNotNull(principal);
      assertTrue(principal instanceof UserPrincipal);
      assertEquals("testUser", ((UserPrincipal) principal).getName());
   }

   @Test
   public void testCreateGroupPrincipalWithDifferentNames() throws Exception {
      Object principal1 = SecurityManagerUtil.createGroupPrincipal("role1", RolePrincipal.class);
      Object principal2 = SecurityManagerUtil.createGroupPrincipal("role2", RolePrincipal.class);

      assertNotNull(principal1);
      assertNotNull(principal2);
      assertEquals("role1", ((RolePrincipal) principal1).getName());
      assertEquals("role2", ((RolePrincipal) principal2).getName());

      assertFalse(principal1.equals(principal2));
   }

   // Class with String constructor
   public static class PrincipalWithStringConstructor implements Principal {
      private final String name;

      public PrincipalWithStringConstructor(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   // Class with no-arg constructor and setName method
   public static class PrincipalWithNoArgeConstructorAndSetName implements Principal {
      private String name;

      public PrincipalWithNoArgeConstructorAndSetName() {
      }

      public void setName(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   // Class that can't be instantiated (no suitable constructor or setName)
   public static class PrincipalNoValidInstantiation implements Principal {
      private final String name;

      private PrincipalNoValidInstantiation(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   @Test
   public void testCreateGroupPrincipalWithCustomClassStringConstructor() throws Exception {
      Object principal = SecurityManagerUtil.createGroupPrincipal("customRole", PrincipalWithStringConstructor.class);

      assertNotNull(principal);
      assertTrue(principal instanceof PrincipalWithStringConstructor);
      assertEquals("customRole", ((PrincipalWithStringConstructor) principal).getName());
   }

   @Test
   public void testCreateGroupPrincipalWithSetNameMethod() throws Exception {
      Object principal = SecurityManagerUtil.createGroupPrincipal("setNameRole", PrincipalWithNoArgeConstructorAndSetName.class);

      assertNotNull(principal);
      assertTrue(principal instanceof PrincipalWithNoArgeConstructorAndSetName);
      assertEquals("setNameRole", ((PrincipalWithNoArgeConstructorAndSetName) principal).getName());
   }

   @Test
   public void testCreateGroupPrincipalWithNoValidInstantiation() {
      assertThrows(NoSuchMethodException.class, () -> {
         SecurityManagerUtil.createGroupPrincipal("invalidRole", PrincipalNoValidInstantiation.class);
      });
   }

   @Test
   public void testGetPrincipalsInRoleWithEmptyRoles() {
      Set<RolePrincipal> result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, new HashSet<Role>(), RolePrincipal.class);

      assertNotNull(result);
      assertTrue(result.isEmpty());
   }

   @Test
   public void testGetPrincipalsInRoleWithNoMatchingPermission() {
      Set<Role> roles = Set.of(
         new Role("user", false, true, false, false, false, false, false, false, false, false, false, false),
         new Role("admin", false, false, true, false, false, false, false, false, false, false, false, false)
      );

      Set<RolePrincipal> result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class);

      assertNotNull(result);
      assertTrue(result.isEmpty());
   }

   @Test
   public void testGetPrincipalsInRoleWithSingleMatchingRole() {
      Set<Role> roles = Set.of(new Role("sender", true, false, false, false, false, false, false, false, false, false, false, false));

      Set<RolePrincipal> result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class);

      assertNotNull(result);
      assertEquals(1, result.size());
      RolePrincipal principal = result.iterator().next();
      assertEquals("sender", principal.getName());
   }

   @Test
   public void testGetPrincipalsInRoleWithMultipleMatchingRoles() {
      Set<Role> roles = Set.of(
         new Role("user", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("admin", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("guest", false, true, false, false, false, false, false, false, false, false, false, false)
      );

      Set<RolePrincipal> result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class);

      assertNotNull(result);
      assertEquals(2, result.size());

      Set<String> names = new HashSet<>();
      for (RolePrincipal principal : result) {
         names.add(principal.getName());
      }
      assertTrue(names.contains("user"));
      assertTrue(names.contains("admin"));

      assertFalse(names.contains("guest"));
   }

   @Test
   public void testGetPrincipalsInRoleWithAllMatchingRoles() {
      Set<Role> roles = Set.of(
         new Role("role1", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("role2", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("role3", true, false, false, false, false, false, false, false, false, false, false, false)
      );

      Set<RolePrincipal> result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class);

      assertNotNull(result);
      assertEquals(3, result.size());
   }

   @Test
   public void testGetPrincipalsInRoleWithDifferentCheckTypes() {
      Set<Role> roles = Set.of(
         new Role("sender", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("consumer", false, true, false, false, false, false, false, false, false, false, false, false),
         new Role("manager", false, false, false, false, false, false, true, false, false, false, false, false)
      );

      Set<RolePrincipal> sendResult = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class);
      assertEquals(1, sendResult.size());
      assertEquals("sender", sendResult.iterator().next().getName());

      Set<RolePrincipal> consumeResult = SecurityManagerUtil.getPrincipalsInRole(CheckType.CONSUME, roles, RolePrincipal.class);
      assertEquals(1, consumeResult.size());
      assertEquals("consumer", consumeResult.iterator().next().getName());

      Set<RolePrincipal> manageResult = SecurityManagerUtil.getPrincipalsInRole(CheckType.MANAGE, roles, RolePrincipal.class);
      assertEquals(1, manageResult.size());
      assertEquals("manager", manageResult.iterator().next().getName());
   }

   @Test
   public void testGetPrincipalsInRoleWithAllCheckTypes() {
      Set<Role> roles = Set.of(new Role("admin", true, true, true, true, true, true, true, true, true, true, true, true));

      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.CONSUME, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.CREATE_DURABLE_QUEUE, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.DELETE_DURABLE_QUEUE, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.CREATE_NON_DURABLE_QUEUE, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.DELETE_NON_DURABLE_QUEUE, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.MANAGE, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.BROWSE, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.CREATE_ADDRESS, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.DELETE_ADDRESS, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.VIEW, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.EDIT, roles, RolePrincipal.class).size());
   }

   @Test
   public void testGetPrincipalsInRoleWithUserPrincipalClass() {
      Set<Role> roles = Set.of(new Role("user", true, false, false, false, false, false, false, false, false, false, false, false));

      Set result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, UserPrincipal.class);

      assertNotNull(result);
      assertEquals(1, result.size());
      Object principal = result.iterator().next();
      assertTrue(principal instanceof UserPrincipal);
      assertEquals("user", ((UserPrincipal) principal).getName());
   }

   @Test
   public void testGetPrincipalsInRoleWithCustomPrincipalClass() {
      Set<Role> roles = Set.of(new Role("custom", true, false, false, false, false, false, false, false, false, false, false, false));

      Set result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, PrincipalWithStringConstructor.class);

      assertNotNull(result);
      assertEquals(1, result.size());
      Object principal = result.iterator().next();
      assertTrue(principal instanceof PrincipalWithStringConstructor);
      assertEquals("custom", ((PrincipalWithStringConstructor) principal).getName());
   }

   @Test
   public void testGetPrincipalsInRoleWithExceptionHandling() {
      // Using a class that can't be instantiated should cause createGroupPrincipal to throw
      // The method should catch the exception and continue, resulting in an empty set
      Set<Role> roles = Set.of(new Role("invalid", true, false, false, false, false, false, false, false, false, false, false, false));

      Set result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, PrincipalNoValidInstantiation.class);

      // The exception should be caught and logged, returning empty set
      assertNotNull(result);
      assertTrue(result.isEmpty());
   }

   @Test
   public void testGetPrincipalsInRoleWithMultiplePermissionsOnRole() {
      Set<Role> roles = Set.of(new Role("poweruser", true, true, true, false, false, false, false, false, false, false, false, false));

      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.CONSUME, roles, RolePrincipal.class).size());
      assertEquals(1, SecurityManagerUtil.getPrincipalsInRole(CheckType.CREATE_DURABLE_QUEUE, roles, RolePrincipal.class).size());

      assertTrue(SecurityManagerUtil.getPrincipalsInRole(CheckType.MANAGE, roles, RolePrincipal.class).isEmpty());
   }

   @Test
   public void testGetPrincipalsInRolePreservesRoleNames() {
      Set<Role> roles = Set.of(
         new Role("role-with-dashes", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("role_with_underscores", true, false, false, false, false, false, false, false, false, false, false, false),
         new Role("Role.With.Dots", true, false, false, false, false, false, false, false, false, false, false, false)
      );

      Set<RolePrincipal> result = SecurityManagerUtil.getPrincipalsInRole(CheckType.SEND, roles, RolePrincipal.class);

      assertEquals(3, result.size());
      Set<String> names = new HashSet<>();
      for (RolePrincipal principal : result) {
         names.add(principal.getName());
      }
      assertTrue(names.contains("role-with-dashes"));
      assertTrue(names.contains("role_with_underscores"));
      assertTrue(names.contains("Role.With.Dots"));
   }
}