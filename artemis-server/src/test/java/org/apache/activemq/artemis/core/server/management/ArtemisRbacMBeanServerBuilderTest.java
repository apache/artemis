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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.security.auth.Subject;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ArtemisRbacMBeanServerBuilderTest extends ServerTestBase {

   MBeanServer mbeanServer;
   MBeanServerDelegate mBeanServerDelegate;
   ArtemisRbacMBeanServerBuilder underTest;

   @BeforeEach
   public void setUnderTest() throws Exception {
      underTest = new ArtemisRbacMBeanServerBuilder();
      mbeanServer = Mockito.mock(MBeanServer.class);
      mBeanServerDelegate = Mockito.mock(MBeanServerDelegate.class);
   }

   @Test
   public void testRbacAddressFrom() throws Exception {
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ArtemisRbacInvocationHandler handler = (ArtemisRbacInvocationHandler) Proxy.getInvocationHandler(proxy);
      handler.brokerDomain = "a.b";
      handler.rbacPrefix = SimpleString.of("jmx");

      try {
         handler.addressFrom(null);
         fail("expect exception");
      } catch (NullPointerException expected) {
      }

      SimpleString rbacAddress = handler.addressFrom(new ObjectName("java.lang", "type", "Runtime"));
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.java.lang.Runtime")));

      rbacAddress = handler.addressFrom(new ObjectName("a.b", "type", "Runtime"));
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx")));

      rbacAddress = handler.addressFrom(new ObjectName("a.b", "broker", "bb"));
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.broker")));

      Hashtable<String, String> attrs = new Hashtable<>();
      attrs.put("broker", "bb");
      attrs.put("type", "t");
      attrs.put("component", "c");
      attrs.put("name", "n");
      rbacAddress = handler.addressFrom(new ObjectName("a.b", attrs));
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.c.n")));


      rbacAddress = handler.addressFrom(new ObjectName("a.b", attrs), "doIt");
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.c.n.doIt")));

      // non broker domain
      rbacAddress = handler.addressFrom(new ObjectName("j.l", attrs));
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.j.l.t.c.n")));

      // address
      attrs.clear();

      attrs.put("broker", "bb");
      attrs.put("address", "a");
      attrs.put("component", "addresses");
      rbacAddress = handler.addressFrom(new ObjectName("a.b", attrs), "opOnA");
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.address.a.opOnA")));

      // queue
      attrs.clear();

      attrs.put("broker", "bb");
      attrs.put("address", "a");
      attrs.put("queue", "q");
      attrs.put("component", "addresses");
      attrs.put("subcomponent", "queues");

      rbacAddress = handler.addressFrom(new ObjectName("a.b", attrs), "opOnQ");
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.queue.q.opOnQ")));

      // divert
      attrs.clear();

      attrs.put("broker", "bb");
      attrs.put("address", "a");
      attrs.put("queue", "q");
      attrs.put("component", "addresses");
      attrs.put("subcomponent", "diverts");
      attrs.put("divert", "d");

      rbacAddress = handler.addressFrom(new ObjectName("a.b", attrs), "opOnDivert");
      assertNotNull(rbacAddress);
      assertEquals(0, rbacAddress.compareTo(SimpleString.of("jmx.divert.d.opOnDivert")));

   }

   @Test
   public void testRbacAddressFromWithObjectNameBuilder() throws Exception {
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ArtemisRbacInvocationHandler handler =
         (ArtemisRbacInvocationHandler) Proxy.getInvocationHandler(proxy);
      handler.brokerDomain = ActiveMQDefaultConfiguration.getDefaultJmxDomain();
      handler.rbacPrefix = SimpleString.of(ActiveMQDefaultConfiguration.getManagementRbacPrefix());

      for (Method m : ObjectNameBuilder.class.getDeclaredMethods()) {
         if (Modifier.isPublic(m.getModifiers()) && ObjectName.class == m.getReturnType()) {
            Object[] args = new Object[m.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
               Class<?> type = m.getParameterTypes()[i];
               if (type == String.class) {
                  args[i] = RandomUtil.randomUUIDString();
               } else  if (SimpleString.class == type) {
                  args[i] = RandomUtil.randomUUIDSimpleString();
               } else if (RoutingType.class == type) {
                  args[i] = RoutingType.ANYCAST;
               }
            }
            assertNotNull(handler.addressFrom((ObjectName) m.invoke(ObjectNameBuilder.DEFAULT, args), m.getName()));
         }
      }
   }

   @Test
   public void testPermissionsFromDefault() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ArtemisRbacInvocationHandler handler = (ArtemisRbacInvocationHandler) Proxy.getInvocationHandler(proxy);

      handler.viewPermissionMatcher = Pattern.compile(ActiveMQDefaultConfiguration.getViewPermissionMethodMatchPattern());

      assertEquals(CheckType.VIEW, handler.permissionFrom("getClass"));
      assertEquals(CheckType.VIEW, handler.permissionFrom("listDeliveringMessages"));
      assertEquals(CheckType.VIEW, handler.permissionFrom("isEmpty"));
      assertEquals(CheckType.EDIT, handler.permissionFrom("quote"));

      assertEquals(CheckType.VIEW, handler.permissionFrom("getA"));

      assertEquals(CheckType.EDIT, handler.permissionFrom("setA"));
   }

   @Test
   public void testPermissionsFromCustom() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ArtemisRbacInvocationHandler handler = (ArtemisRbacInvocationHandler) Proxy.getInvocationHandler(proxy);

      handler.viewPermissionMatcher = Pattern.compile("^(is(?!SecurityEnabled)|get|list|query).*$");

      assertEquals(CheckType.EDIT, handler.permissionFrom("isSecurityEnabled"));
      assertEquals(CheckType.VIEW, handler.permissionFrom("isEmpty"));

      assertEquals(CheckType.VIEW, handler.permissionFrom("getClass"));
      assertEquals(CheckType.VIEW, handler.permissionFrom("listDeliveringMessages"));

      assertEquals(CheckType.EDIT, handler.permissionFrom("quote"));

      assertEquals(CheckType.VIEW, handler.permissionFrom("getA"));

      assertEquals(CheckType.EDIT, handler.permissionFrom("setA"));
   }

   @Test
   public void testUninitialised() throws Exception {
      assertThrows(IllegalStateException.class, () -> {

         MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

         ObjectName runtimeName = new ObjectName("java.lang", "type", "Runtime");
         RuntimeMXBean runtime = JMX.newMBeanProxy(
            proxy, runtimeName, RuntimeMXBean.class, false);
         runtime.getVmVersion();
      });
   }

   @Test
   public void testUnCheckedDomain() throws Exception {
      assertThrows(UndeclaredThrowableException.class, () -> {

         MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

         ObjectName unchecked = new ObjectName("hawtio", "type", "Runtime");
         RuntimeMXBean runtime = JMX.newMBeanProxy(
            proxy, unchecked, RuntimeMXBean.class, false);
         runtime.getVmVersion();
      });
   }

   @Test
   public void testNotLoggedIn() throws Exception {
      assertThrows(SecurityException.class, () -> {

         MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

         final ActiveMQServer server = createServer(false);
         server.setMBeanServer(proxy);
         server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);
         server.start();

         ObjectName runtimeName = new ObjectName("java.lang", "type", "Runtime");
         RuntimeMXBean runtime = JMX.newMBeanProxy(
            proxy, runtimeName, RuntimeMXBean.class, false);
         runtime.getVmVersion();
      });
   }

   @Test
   public void testNoPermission() throws Exception {
      assertThrows(SecurityException.class, () -> {

         MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

         final ActiveMQServer server = createServer(false);
         server.setMBeanServer(proxy);
         server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);
         server.start();

         ObjectName runtimeName = new ObjectName("java.lang", "type", "Runtime");
         final RuntimeMXBean runtime = JMX.newMBeanProxy(
            proxy, runtimeName, RuntimeMXBean.class, false);

         Subject viewSubject = new Subject();
         viewSubject.getPrincipals().add(new UserPrincipal("v"));
         viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

         throw SecurityManagerShim.callAs(viewSubject, (Callable<Exception>) () -> {
            try {
               runtime.getVmVersion();
               return null;
            } catch (Exception e1) {
               return e1;
            }
         });
      });
   }

   @Test
   public void testPermissionGetAttr() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("mops.broker.getCurrentTimeMillis", roles);

      server.start();

      ObjectName objectName = ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName();
      final ActiveMQServerControl serverControl = JMX.newMBeanProxy(
         proxy, objectName, ActiveMQServerControl.class, false);

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object ret = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return serverControl.getCurrentTimeMillis();
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(Long.class, ret);


      // verify failure case
      Subject noPermSubject = new Subject();
      noPermSubject.getPrincipals().add(new UserPrincipal("dud"));
      noPermSubject.getPrincipals().add(new RolePrincipal("dud"));

      ret = SecurityManagerShim.callAs(noPermSubject, (Callable<Object>) () -> {
         try {
            return serverControl.getCurrentTimeMillis();
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(SecurityException.class, ret);
   }


   @Test
   public void testPermissionIsAttr() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true).setManagementRbacPrefix("jmx");

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.broker.isSecurityEnabled", roles);

      server.start();

      final ActiveMQServerControl serverControl = JMX.newMBeanProxy(
         proxy, ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object ret = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return serverControl.isSecurityEnabled();
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(Boolean.class, ret);


      // verify failure case
      Subject noPermSubject = new Subject();
      noPermSubject.getPrincipals().add(new UserPrincipal("dud"));
      noPermSubject.getPrincipals().add(new RolePrincipal("dud"));

      ret = SecurityManagerShim.callAs(noPermSubject, (Callable<Object>) () -> {
         try {
            return serverControl.isSecurityEnabled();
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(SecurityException.class, ret);
   }

   @Test
   public void testPermissionWithConfiguredJmxPrefix() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true).setManagementRbacPrefix("j.m.x");

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("j.m.x.broker.*", roles);

      server.start();

      final ActiveMQServerControl serverControl = JMX.newMBeanProxy(
         proxy, ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object ret = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return serverControl.isSecurityEnabled();
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(Boolean.class, ret);
   }

   @Test
   public void testConfigViewMethodMatchNoPermission() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);

      // isSecurityEnabled will require Update permission
      server.getConfiguration().setViewPermissionMethodMatchPattern("^(is(?!SecurityEnabled)|get|list|query).*$");

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("mops.broker.#", roles);

      server.start();

      final ActiveMQServerControl serverControl = JMX.newMBeanProxy(
         proxy, ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);


      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object ret =  SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return serverControl.isSecurityEnabled();
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(SecurityException.class, ret);
      assertTrue(((Exception)ret).getMessage().contains("EDIT"));

      // another `is` op is ok with view
      ret =  SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return serverControl.isActive();
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(Boolean.class, ret);
   }

   @Test
   public void testConfigMethodMatchEmptyNeedsUpdate() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true).setManagementRbacPrefix("jmx");

      // all ops will require Update permission
      server.getConfiguration().setViewPermissionMethodMatchPattern("");

      Set<Role> viewRoles = new HashSet<>();
      viewRoles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      Set<Role> editRoles = new HashSet<>();
      editRoles.add(new Role("updaters", false, false, false, false, false, false, false, false, false, false, false, true));

      server.getConfiguration().putSecurityRoles("jmx.broker.#", viewRoles);
      server.getConfiguration().putSecurityRoles("jmx.broker.isSecurityEnabled", editRoles);

      server.start();

      final ActiveMQServerControl serverControl = JMX.newMBeanProxy(
         proxy, ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);


      Subject testSubject = new Subject();
      testSubject.getPrincipals().add(new UserPrincipal("v"));
      testSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object ret =  SecurityManagerShim.callAs(testSubject, (Callable<Object>) () -> {
         try {
            return serverControl.getAddressCount();
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(SecurityException.class, ret);
      assertTrue(((Exception)ret).getMessage().contains("EDIT"));

      // with updaters role we can access a specific method
      testSubject.getPrincipals().add(new RolePrincipal("updaters"));

      ret =  SecurityManagerShim.callAs(testSubject, (Callable<Object>) () -> {
         try {
            return serverControl.isSecurityEnabled();
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNotNull(ret);
      assertInstanceOf(Boolean.class, ret);
   }

   @Test
   public void testQueryWithStar() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("mops.mbeanserver.queryNames", roles);

      server.start();

      Hashtable<String, String> attrs = new Hashtable<>();
      attrs.put("broker", "bb");
      attrs.put("type", "security");
      attrs.put("area", "jmx");
      attrs.put("name", "*");

      final ObjectName queryName = new ObjectName("*", attrs);

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return proxy.queryNames(queryName, null);
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(result);
      assertInstanceOf(Set.class, result);
   }

   @Test
   public void testQueryAllFiltered() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);

      Set<Role> viewerRole = new HashSet<>();
      viewerRole.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));

      Set<Role> condoleRole = new HashSet<>();
      condoleRole.add(new Role("mbeanServer", false, false, false, false, false, false, false, false, false, false, true, false));

      server.getConfiguration().putSecurityRoles("mops.mbeanserver.#", condoleRole);
      server.getConfiguration().putSecurityRoles("mops.address.activemq.notifications", viewerRole);

      server.start();

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("mbeanServer"));

      Object result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return proxy.queryNames(null, null);
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(result);
      assertEquals(1, ((Set<?>) result).size());

      // give view role
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));
      result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return proxy.queryNames(null, null);
         } catch (Exception e1) {
            return e1;
         }
      });

      assertNotNull(result);
      assertEquals(2, ((Set<?>) result).size());

      // and they are there, we just don't see them
      assertEquals(5, proxy.getMBeanCount().intValue());
   }

   @Test
   public void testCanInvoke() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("mops.java.#", roles);

      server.start();


      final HawtioSecurityControl securityControl = JMX.newMBeanProxy(
         proxy, ObjectNameBuilder.DEFAULT.getSecurityObjectName(), HawtioSecurityControl.class, false);

      ObjectName runtimeName = new ObjectName("java.lang", "type", "Runtime");
      final RuntimeMXBean runtime = JMX.newMBeanProxy(
         proxy, runtimeName, RuntimeMXBean.class, false);
      assertNotNull(runtime);

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Object result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(runtimeName.toString());
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertTrue((Boolean) result, "in the absence of an operation to check, view is sufficient");

      result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(runtimeName.toString(), "getVmName");
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertTrue((Boolean) result);

      result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(runtimeName.toString(), "getVmName", new String[]{"args", "are", "ignored"});
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertTrue((Boolean) result);


      Map<String, List<String>> bulkQuery = new HashMap<>();
      bulkQuery.put(runtimeName.toString(), List.of("getVmName()", "getVersion()"));

      result = SecurityManagerShim.callAs(viewSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(bulkQuery);
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertEquals(2, ((TabularData)result).size());

      CompositeData cd = ((TabularData)result).get(new Object[]{runtimeName.toString(), "getVmName()"});
      assertEquals(runtimeName.toString(), cd.get("ObjectName"));
      assertEquals("getVmName()", cd.get("Method"));
      assertEquals(true, cd.get("CanInvoke"));
   }

   @Test
   public void testCanInvokeStripsParameterList() throws Exception {

      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      final ActiveMQServer server = createServer(false);
      server.setMBeanServer(proxy);
      server.getConfiguration().setJMXManagementEnabled(true).setSecurityEnabled(true);

      Set<Role> editRoles = new HashSet<>();
      editRoles.add(new Role("editors", false, false, false, false, false, false, false, false, false, false, false, true));

      // Grant edit permission to deleteAddress operation WITHOUT parameter list
      // This simulates the typical permission setup where operations are configured without parameter signatures
      server.getConfiguration().putSecurityRoles("mops.broker.deleteAddress", editRoles);

      server.start();

      final HawtioSecurityControl securityControl = JMX.newMBeanProxy(
         proxy, ObjectNameBuilder.DEFAULT.getSecurityObjectName(), HawtioSecurityControl.class, false);

      ObjectName serverObjectName = ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName();
      final ActiveMQServerControl serverControl = JMX.newMBeanProxy(
         proxy, serverObjectName, ActiveMQServerControl.class, false);
      assertNotNull(serverControl);

      Subject editSubject = new Subject();
      editSubject.getPrincipals().add(new UserPrincipal("e"));
      editSubject.getPrincipals().add(new RolePrincipal("editors"));

      // Test with operation name including parameter list as sent by console
      Object result = SecurityManagerShim.callAs(editSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(serverObjectName.toString(), "deleteAddress(java.lang.String)");
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertTrue((Boolean) result, "Should be able to invoke deleteAddress(java.lang.String) - parameter list should be stripped to match permission");

      // Test with empty parameter list
      result = SecurityManagerShim.callAs(editSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(serverObjectName.toString(), "deleteAddress()");
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertTrue((Boolean) result, "Should be able to invoke deleteAddress() - parameter list should be stripped");

      // Test without parameter list (should also work)
      result = SecurityManagerShim.callAs(editSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(serverObjectName.toString(), "deleteAddress");
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertTrue((Boolean) result, "Should be able to invoke deleteAddress without parameters");

      // Test bulk query with parameter lists
      Map<String, List<String>> bulkQuery = new HashMap<>();
      bulkQuery.put(serverObjectName.toString(), List.of("deleteAddress(java.lang.String)", "deleteAddress()"));

      result = SecurityManagerShim.callAs(editSubject, (Callable<Object>) () -> {
         try {
            return securityControl.canInvoke(bulkQuery);
         } catch (Exception e1) {
            return e1.getCause();
         }
      });
      assertNotNull(result);
      assertEquals(2, ((TabularData)result).size());

      CompositeData cd = ((TabularData)result).get(new Object[]{serverObjectName.toString(), "deleteAddress(java.lang.String)"});
      assertEquals(serverObjectName.toString(), cd.get("ObjectName"));
      assertEquals("deleteAddress(java.lang.String)", cd.get("Method"));
      assertEquals(true, cd.get("CanInvoke"));

      cd = ((TabularData)result).get(new Object[]{serverObjectName.toString(), "deleteAddress()"});
      assertEquals(serverObjectName.toString(), cd.get("ObjectName"));
      assertEquals("deleteAddress()", cd.get("Method"));
      assertEquals(true, cd.get("CanInvoke"));
   }
}
