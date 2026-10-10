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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.util.Set;

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
   public void testAuthorize_BasicRole() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "admin");
      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomething", Set.of("view")));
   }

   @Test
   public void testAuthorize_BasicRoleWithKey() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=foo", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("view")));
   }

   @Test
   public void testAuthorize_BasicRoleWithKeyContainingQuotes() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=foo", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=\"foo\""), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=\"foo\""), "listSomething", Set.of("view")));
   }

   @Test
   public void testAuthorize_BasicRoleWithWildcardKey() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=*", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("view")));
   }

   @Test
   public void testAuthorize_BasicRoleWithWildcardInKey() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=foo*", "listSomething", "update");
      controlList.addToRoleAccess("org.myDomain", "type=bar*", "listSomething", "browse");
      controlList.addToRoleAccess("org.myDomain", "type=foo.bar*", "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo.bar.test"), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo.bar.test"), "listSomething", Set.of("view", "update", "browse")));

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=bar.test"), "listSomething", Set.of("browse")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=bar.test"), "listSomething", Set.of("view", "update", "admin")));
   }

   @Test
   public void testAuthorize_MutipleBasicRoles() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "admin", "view", "update");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomething", Set.of("view", "update", "admin")));
   }

   @Test
   public void testAuthorize_BasicRoleWithPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "list*", "admin");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomething", Set.of("admin")));
   }

   @Test
   public void testAuthorize_BasicRoleWithBoth() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomething", Set.of("view")));

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomethingMore", Set.of("view")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:*"), "listSomethingMore", Set.of("admin")));
   }

   @Test
   public void testAuthorize_BasicRoleWithDefaultsPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToDefaultAccess("setSomething", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain.foo:*"), "setSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain.foo:*"), "setSomething", Set.of("view")));
   }

   @Test
   public void testAuthorize_BasicRoleWithDefaultsWildcardPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToDefaultAccess("setSomething", "admin");
      controlList.addToDefaultAccess("set*", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain.foo:*"), "setSomethingMore", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain.foo:*"), "setSomethingMore", Set.of("view")));
   }

   @Test
   public void testAuthorize_BasicRoleWithDefaultscatchAllPrefix() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToDefaultAccess("setSomething", "admin");
      controlList.addToDefaultAccess("*", "admin");
      controlList.addToRoleAccess("org.myDomain", null, "list*", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain.foo:*"), "setSomethingMore", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain.foo:*"), "setSomethingMore", Set.of("view")));
   }

   @Test
   public void testAuthorize_KeylessDomain() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain.foo", null, "list*", "amq", "monitor");
      controlList.addToRoleAccess("org.myDomain.foo", null, "get*", "amq", "monitor");
      controlList.addToRoleAccess("org.myDomain.foo", null, "is*", "amq", "monitor");
      controlList.addToRoleAccess("org.myDomain.foo", null, "set*", "amq");
      controlList.addToRoleAccess("org.myDomain.foo", null, "*", "amq");

      ObjectName withProperty = new ObjectName("org.myDomain.foo:foo=bar");

      assertTrue(controlList.authorizeUserForMethod(withProperty, "listFoo", Set.of("monitor")));
      assertTrue(controlList.authorizeUserForMethod(withProperty, "getFoo", Set.of("monitor")));
      assertTrue(controlList.authorizeUserForMethod(withProperty, "isFoo", Set.of("monitor")));

      assertTrue(controlList.authorizeUserForMethod(withProperty, "setFoo", Set.of("amq")));
      assertFalse(controlList.authorizeUserForMethod(withProperty, "setFoo", Set.of("monitor")));

      assertTrue(controlList.authorizeUserForMethod(withProperty, "createFoo", Set.of("amq")));
      assertFalse(controlList.authorizeUserForMethod(withProperty, "createFoo", Set.of("monitor")));
   }

   @Test
   public void testAuthorize_BareWildcardKey() throws MalformedObjectNameException {
      // no "=" in the pattern
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "*", "listSomething", "admin");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:foo=bar"), "listSomething", Set.of("admin")));
   }

   @Test
   public void testAuthorize_WildcardInKeyName() throws MalformedObjectNameException {
      // wildcard in the part before equal sign
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "*=bar", "listSomething", "admin");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:foo=bar"), "listSomething", Set.of("admin")));
   }

   @Test
   public void testAuthorize_LongerWildcardInKeyNameTakesPriority() throws MalformedObjectNameException {
      // longer wildcard matches should have higher priority than the shorter wildcard matches.
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", "type=*", "listSomething", "roleA");
      controlList.addToRoleAccess("org.myDomain", "*=verylongvalue", "listSomething", "roleB");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=verylongvalue"), "listSomething", Set.of("roleB")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=verylongvalue"), "listSomething", Set.of("roleA")));
   }

   @Test
   public void testAuthorize_KeyAddedAfterLookup() throws MalformedObjectNameException {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("org.myDomain", null, "listSomething", "view");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("view")));

      controlList.addToRoleAccess("org.myDomain", "type=foo", "listSomething", "admin");

      assertTrue(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("admin")));
      assertFalse(controlList.authorizeUserForMethod(new ObjectName("org.myDomain:type=foo"), "listSomething", Set.of("view")));
   }

}
