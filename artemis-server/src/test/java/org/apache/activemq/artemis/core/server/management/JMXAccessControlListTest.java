/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.management;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.util.List;

public class JMXAccessControlListTest {

   @Test
   public void testBasicDomain() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToAllowList("org.myDomain", null);
      controlList.addToAllowList("org.myDomain.foo", null);
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:*")));
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain.foo:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.bar:*")));
   }

   @Test
   public void testBasicDomainWithProperty() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToAllowList("org.myDomain", "type=foo");
      controlList.addToAllowList("org.myDomain.foo", "type=bar");
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.foo:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.bar:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain:subType=foo")));

      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:type=foo")));
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:subType=bar,type=foo")));
   }

   @Test
   public void testBasicDomainWithWildCardProperty() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToAllowList("org.myDomain", "type=*");
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.foo:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.bar:*")));
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:type=foo")));
   }

   @Test
   public void testWildcardDomain() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToAllowList("*", null);
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:*")));
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain.foo:*")));
   }

   @Test
   public void testWildcardDomainWithProperty() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToAllowList("*", "type=foo");
      controlList.addToAllowList("org.myDomain.foo", "type=bar");
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.foo:*")));
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain.foo:type=bar")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.foo:type=foo")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain.bar:*")));
      assertFalse(controlList.isInAllowList(new ObjectName("org.myDomain:subType=foo")));

      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:type=foo")));
      assertTrue(controlList.isInAllowList(new ObjectName("org.myDomain:subType=bar,type=foo")));
   }

   @Test
   public void testBasicRole() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "admin");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:*"), "listSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithKey() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=foo", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:type=foo"), "listSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithKeyContainingQuotes() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=foo", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:type=\"foo\""), "listSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithWildcardKey() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=*", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:type=foo"), "listSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithWildcardInKey() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=foo*", "listSomething", "update");
      controlList.addToRoleAccess("org.myDomain", "type=bar*", "listSomething", "browse");
      controlList.addToRoleAccess("org.myDomain", "type=foo.bar*", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");
      assertArrayEquals(new String[]{"admin"}, controlList.getRolesForObject(new ObjectName("org.myDomain:type=foo.bar.test"),
                                                                             "listSomething").toArray());
      assertArrayEquals(new String[]{"browse"}, controlList.getRolesForObject(new ObjectName("org.myDomain:type=bar.test"),
                                                                              "listSomething").toArray());
   }

   @Test
   public void testMutipleBasicRoles() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "admin", "view", "update");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:*"), "listSomething");
      assertArrayEquals(new String[]{"admin", "view", "update"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "list*", "admin");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:*"), "listSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithBoth() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain:*"), "listSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
      roles = controlList.getRolesForObject(new ObjectName("org.myDomain:*"), "listSomethingMore");
      assertArrayEquals(new String[]{"view"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithDefaultsPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToDefaultAccess("setSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:*"), "setSomething");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithDefaultsWildcardPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToDefaultAccess("setSomething", "admin");
      controlList.addToDefaultAccess("set*", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:*"), "setSomethingMore");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testBasicRoleWithDefaultscatchAllPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToDefaultAccess("setSomething", "admin");
      controlList.addToDefaultAccess("*", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");
      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:*"), "setSomethingMore");
      assertArrayEquals(new String[]{"admin"}, roles.toArray());
   }

   @Test
   public void testKeylessDomain() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain.foo", null, "list*", "amq", "monitor");
      controlList.addToRoleAccess("org.myDomain.foo", null, "get*", "amq", "monitor");
      controlList.addToRoleAccess("org.myDomain.foo", null, "is*", "amq", "monitor");
      controlList.addToRoleAccess("org.myDomain.foo", null, "set*", "amq");
      controlList.addToRoleAccess("org.myDomain.foo", null, "*", "amq");

      List<String> roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:foo=bar"), "listFoo");
      assertNotNull(roles);
      assertEquals(2, roles.size());
      assertEquals("amq", roles.get(0));
      assertEquals("monitor", roles.get(1));

      roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:foo=bar"), "getFoo");
      assertNotNull(roles);
      assertEquals(2, roles.size());
      assertEquals("amq", roles.get(0));
      assertEquals("monitor", roles.get(1));

      roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:foo=bar"), "isFoo");
      assertNotNull(roles);
      assertEquals(2, roles.size());
      assertEquals("amq", roles.get(0));
      assertEquals("monitor", roles.get(1));

      roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:foo=bar"), "setFoo");
      assertNotNull(roles);
      assertEquals(1, roles.size());
      assertEquals("amq", roles.get(0));

      roles = controlList.getRolesForObject(new ObjectName("org.myDomain.foo:foo=bar"), "createFoo");
      assertNotNull(roles);
      assertEquals(1, roles.size());
      assertEquals("amq", roles.get(0));

   }
}
