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
package org.apache.activemq.artemis.tests.unit.core.security.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManagerImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActiveMQSecurityManagerImplTest extends ActiveMQTestBase {

   private ActiveMQSecurityManagerImpl securityManager;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      securityManager = new ActiveMQSecurityManagerImpl();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      securityManager = null;

      super.tearDown();
   }

   @Test
   public void testDefaultSecurity() {
      securityManager.getConfiguration().addUser("guest", "password");
      securityManager.getConfiguration().addRole("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      assertTrue(securityManager.validateUser(null, null));
      assertTrue(securityManager.validateUser("guest", "password"));
      assertFalse(securityManager.validateUser(null, "wrongpass"));
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("guest", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole(null, null, roles, CheckType.CREATE_DURABLE_QUEUE));
      assertTrue(securityManager.validateUserAndRole(null, null, roles, CheckType.SEND));
      assertTrue(securityManager.validateUserAndRole(null, null, roles, CheckType.CONSUME));
      roles = new HashSet<>();
      roles.add(new Role("guest", true, true, false, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole(null, null, roles, CheckType.CREATE_DURABLE_QUEUE));
      assertTrue(securityManager.validateUserAndRole(null, null, roles, CheckType.SEND));
      assertTrue(securityManager.validateUserAndRole(null, null, roles, CheckType.CONSUME));
      roles = new HashSet<>();
      roles.add(new Role("guest", true, false, false, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole(null, null, roles, CheckType.CREATE_DURABLE_QUEUE));
      assertTrue(securityManager.validateUserAndRole(null, null, roles, CheckType.SEND));
      assertFalse(securityManager.validateUserAndRole(null, null, roles, CheckType.CONSUME));
      roles = new HashSet<>();
      roles.add(new Role("guest", false, false, false, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole(null, null, roles, CheckType.CREATE_DURABLE_QUEUE));
      assertFalse(securityManager.validateUserAndRole(null, null, roles, CheckType.SEND));
      assertFalse(securityManager.validateUserAndRole(null, null, roles, CheckType.CONSUME));
   }

   @Test
   public void testAddingUsers() {
      securityManager.getConfiguration().addUser("newuser1", "newpassword1");
      assertTrue(securityManager.validateUser("newuser1", "newpassword1"));
      assertFalse(securityManager.validateUser("newuser1", "guest"));
      assertFalse(securityManager.validateUser("newuser1", null));
      try {
         securityManager.getConfiguration().addUser("newuser2", null);
         fail("password cannot be null");
      } catch (IllegalArgumentException e) {
         // pass
      }
      try {
         securityManager.getConfiguration().addUser(null, "newpassword2");
         fail("password cannot be null");
      } catch (IllegalArgumentException e) {
         // pass
      }
   }

   @Test
   public void testRemovingUsers() {
      securityManager.getConfiguration().addUser("newuser1", "newpassword1");
      assertTrue(securityManager.validateUser("newuser1", "newpassword1"));
      securityManager.getConfiguration().removeUser("newuser1");
      assertFalse(securityManager.validateUser("newuser1", "newpassword1"));
   }

   @Test
   public void testRemovingInvalidUsers() {
      securityManager.getConfiguration().addUser("newuser1", "newpassword1");
      assertTrue(securityManager.validateUser("newuser1", "newpassword1"));
      securityManager.getConfiguration().removeUser("nonuser");
      assertTrue(securityManager.validateUser("newuser1", "newpassword1"));
   }

   @Test
   public void testAddingRoles() {
      securityManager.getConfiguration().addUser("newuser1", "newpassword1");
      securityManager.getConfiguration().addRole("newuser1", "role1");
      securityManager.getConfiguration().addRole("newuser1", "role2");
      securityManager.getConfiguration().addRole("newuser1", "role3");
      securityManager.getConfiguration().addRole("newuser1", "role4");
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("role1", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role2", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role3", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role4", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role5", true, true, true, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
   }

   @Test
   public void testRemovingRoles() {
      securityManager.getConfiguration().addUser("newuser1", "newpassword1");
      securityManager.getConfiguration().addRole("newuser1", "role1");
      securityManager.getConfiguration().addRole("newuser1", "role2");
      securityManager.getConfiguration().addRole("newuser1", "role3");
      securityManager.getConfiguration().addRole("newuser1", "role4");
      securityManager.getConfiguration().removeRole("newuser1", "role2");
      securityManager.getConfiguration().removeRole("newuser1", "role4");
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("role1", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role2", true, true, true, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role3", true, true, true, true, true, true, true, true, true, true, false, false));
      assertTrue(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role4", true, true, true, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
      roles = new HashSet<>();
      roles.add(new Role("role5", true, true, true, true, true, true, true, true, true, true, false, false));
      assertFalse(securityManager.validateUserAndRole("newuser1", "newpassword1", roles, CheckType.SEND));
   }
}
