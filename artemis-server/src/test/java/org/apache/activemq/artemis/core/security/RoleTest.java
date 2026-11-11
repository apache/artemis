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
package org.apache.activemq.artemis.core.security;

import static org.apache.activemq.artemis.core.security.CheckType.BROWSE;
import static org.apache.activemq.artemis.core.security.CheckType.CONSUME;
import static org.apache.activemq.artemis.core.security.CheckType.CREATE_ADDRESS;
import static org.apache.activemq.artemis.core.security.CheckType.CREATE_DURABLE_QUEUE;
import static org.apache.activemq.artemis.core.security.CheckType.CREATE_NON_DURABLE_QUEUE;
import static org.apache.activemq.artemis.core.security.CheckType.DELETE_DURABLE_QUEUE;
import static org.apache.activemq.artemis.core.security.CheckType.DELETE_NON_DURABLE_QUEUE;
import static org.apache.activemq.artemis.core.security.CheckType.MANAGE;
import static org.apache.activemq.artemis.core.security.CheckType.SEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RoleTest {



   @Test
   public void testWriteRole() throws Exception {
      Role role = new Role("testWriteRole", true, false, false, false, false, false, false, false, false, false, false, false);
      assertTrue(SEND.hasRole(role));
      assertFalse(CONSUME.hasRole(role));
      assertFalse(CREATE_DURABLE_QUEUE.hasRole(role));
      assertFalse(CREATE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(MANAGE.hasRole(role));
      assertFalse(BROWSE.hasRole(role));
      assertFalse(CREATE_ADDRESS.hasRole(role));
   }

   @Test
   public void testReadRole() throws Exception {
      Role role = new Role("testReadRole", false, true, false, false, false, false, false, true, false, false, false, false);
      assertFalse(SEND.hasRole(role));
      assertTrue(CONSUME.hasRole(role));
      assertFalse(CREATE_DURABLE_QUEUE.hasRole(role));
      assertFalse(CREATE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(MANAGE.hasRole(role));
      assertTrue(BROWSE.hasRole(role));
      assertFalse(CREATE_ADDRESS.hasRole(role));
   }

   @Test
   public void testCreateRole() throws Exception {
      Role role = new Role("testCreateRole", false, false, true, false, false, false, false, false, false, false, false, false);
      assertFalse(SEND.hasRole(role));
      assertFalse(CONSUME.hasRole(role));
      assertTrue(CREATE_DURABLE_QUEUE.hasRole(role));
      assertFalse(CREATE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(MANAGE.hasRole(role));
      assertFalse(BROWSE.hasRole(role));
      assertFalse(CREATE_ADDRESS.hasRole(role));
   }

   @Test
   public void testManageRole() throws Exception {
      Role role = new Role("testManageRole", false, false, false, false, false, false, true, false, false, false, false, false);
      assertFalse(SEND.hasRole(role));
      assertFalse(CONSUME.hasRole(role));
      assertFalse(CREATE_DURABLE_QUEUE.hasRole(role));
      assertFalse(CREATE_NON_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_DURABLE_QUEUE.hasRole(role));
      assertFalse(DELETE_NON_DURABLE_QUEUE.hasRole(role));
      assertTrue(MANAGE.hasRole(role));
      assertFalse(BROWSE.hasRole(role));
      assertFalse(CREATE_ADDRESS.hasRole(role));
   }

   @Test
   public void testEqualsAndHashcode() throws Exception {
      Role role = new Role("testEquals", true, true, true, false, false, false, false, false, false, false, false, false);
      Role sameRole = new Role("testEquals", true, true, true, false, false, false, false, false, false, false, false, false);
      Role roleWithDifferentName = new Role("notEquals", true, true, true, false, false, false, false, false, false, false, false, false);
      Role roleWithDifferentRead = new Role("testEquals", false, true, true, false, false, false, false, false, false, false, false, false);
      Role roleWithDifferentWrite = new Role("testEquals", true, false, true, false, false, false, false, false, false, false, false, false);
      Role roleWithDifferentCreate = new Role("testEquals", true, true, false, false, false, false, false, false, false, false, false, false);

      Role roleWithDifferentView = new Role("testEquals", true, true, true, false, false, false, false, false, false, false, true, false);
      Role roleWithDifferentUpdate = new Role("testEquals", true, true, true, false, false, false, false, false, false, false, false, true);

      assertTrue(role.equals(role));

      assertTrue(role.equals(sameRole));
      assertEquals(role.hashCode(), sameRole.hashCode());

      assertFalse(role.equals(roleWithDifferentName));
      assertNotEquals(role.hashCode(), roleWithDifferentName.hashCode());

      assertFalse(role.equals(roleWithDifferentRead));
      assertNotEquals(role.hashCode(), roleWithDifferentRead.hashCode());

      assertFalse(role.equals(roleWithDifferentWrite));
      assertNotEquals(role.hashCode(), roleWithDifferentWrite.hashCode());

      assertFalse(role.equals(roleWithDifferentCreate));
      assertNotEquals(role.hashCode(), roleWithDifferentCreate.hashCode());

      assertFalse(role.equals(roleWithDifferentView));
      assertNotEquals(role.hashCode(), roleWithDifferentView.hashCode());

      assertFalse(role.equals(roleWithDifferentUpdate));
      assertNotEquals(role.hashCode(), roleWithDifferentUpdate.hashCode());

      assertFalse(role.equals(null));
   }


}
