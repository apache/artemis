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

import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.security.auth.Subject;
import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;

public class ArtemisMBeanServerGuardTest extends ServerTestBase {
   @Test
   public void testInvokeNoMethod() throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create("testdomain", "myBroker");
      ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();
      assertTrue(guard.canInvoke(activeMQServerObjectName.getCanonicalName(), null));
   }

   @Test
   public void testCantInvokeMethod() throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create("testdomain", "myBroker");
      ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();
      assertFalse(guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "getSomething"));
   }


   @Test
   public void testCanInvokeMethodWhiteList() throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create("testdomain", "myBroker");
      ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();
      controlList.addToAllowList("testdomain", "broker=myBroker");
      assertTrue(guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "getSomething"));
   }


   @Test
   public void testCanInvokeMethodHasRole() throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create("testdomain", "myBroker");
      ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "getSomething", "admin");
      Subject subject = new Subject();
      subject.getPrincipals().add(new RolePrincipal("admin"));
      Object result = SecurityManagerShim.callAs(subject, (Callable<Object>) () -> {
         try {
            return guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "getSomething");
         } catch (Exception e1) {
            return e1;
         }
      });
      assertTrue((Boolean) result);
   }


   @Test
   public void testCanInvokeMethodDoeNotHasRole() throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create("testdomain", "myBroker");
      ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "getSomething", "admin");
      Subject subject = new Subject();
      subject.getPrincipals().add(new RolePrincipal("view"));
      Object result = SecurityManagerShim.callAs(subject, (Callable<Object>) () -> {
         try {
            return guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "getSomething");
         } catch (Exception e1) {
            return e1;
         }
      });
      assertFalse((Boolean) result);
   }

   @Test
   public void testCanInvokeStripsParameterList() throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create("testdomain", "myBroker");
      ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();

      // Configure permission for operation without parameter list
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "deleteAddress", "admin");

      Subject subject = new Subject();
      subject.getPrincipals().add(new RolePrincipal("admin"));

      // Test with operation name including parameter list
      Object result = SecurityManagerShim.callAs(subject, (Callable<Object>) () -> {
         try {
            return guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "deleteAddress(java.lang.String)");
         } catch (Exception e1) {
            return e1;
         }
      });
      assertTrue((Boolean) result, "Should be able to invoke deleteAddress(java.lang.String) - parameter list should be stripped");

      // Test with empty parameter list
      result = SecurityManagerShim.callAs(subject, (Callable<Object>) () -> {
         try {
            return guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "deleteAddress()");
         } catch (Exception e1) {
            return e1;
         }
      });
      assertTrue((Boolean) result, "Should be able to invoke deleteAddress() - parameter list should be stripped");

      // Test without parameter list (should also work)
      result = SecurityManagerShim.callAs(subject, (Callable<Object>) () -> {
         try {
            return guard.canInvoke(activeMQServerObjectName.getCanonicalName(), "deleteAddress");
         } catch (Exception e1) {
            return e1;
         }
      });
      assertTrue((Boolean) result, "Should be able to invoke deleteAddress without parameters");
   }

   @Test
   public void testInvokeDeniedOperationThrowsSecurityExceptionNamingTheOperation() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      guard.setJMXAccessControlList(new JMXAccessControlList());
      ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
      Method invokeMethod = MBeanServer.class.getMethod("invoke", ObjectName.class, String.class, Object[].class, String[].class);

      Exception thrown = assertThrows(SecurityException.class, () ->
         guard.invoke(mbeanServerMock, invokeMethod, new Object[]{objectName, "doSomething", new Object[0], new String[0]}));
      assertTrue(thrown.getMessage().contains("doSomething"));
   }

   @Test
   public void testInvokeDeniedOperationLogsResourceAuditFailureWhenEnabled() throws Exception {
      String resourceLoggerName = AuditLogger.RESOURCE_LOGGER.getLogger().getName();
      Configurator.setLevel(LogManager.getLogger(resourceLoggerName), Level.INFO);
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
         guard.setJMXAccessControlList(new JMXAccessControlList());
         ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
         MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
         Method invokeMethod = MBeanServer.class.getMethod("invoke", ObjectName.class, String.class, Object[].class, String[].class);

         assertThrows(SecurityException.class, () ->
            guard.invoke(mbeanServerMock, invokeMethod, new Object[]{objectName, "doSomething", new Object[0], new String[0]}));

         // AMQ601718 is the objectInvokedFailure resource-audit log message id
         assertTrue(loggerHandler.findText("AMQ601718"));
      } finally {
         Configurator.setLevel(LogManager.getLogger(resourceLoggerName), Level.OFF);
      }
   }

   @Test
   public void testInvokeGetAttributeUsesIsPrefixForBooleanAttribute() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);

      ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
      // role is granted against "isActive", not "Active" or "getActive", because the attribute is boolean
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "isActive", "admin");

      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
      Mockito.when(mbeanServerMock.getMBeanInfo(objectName)).thenReturn(
         new MBeanInfo(Object.class.getName(), "d",
            new MBeanAttributeInfo[]{new MBeanAttributeInfo("Active", "boolean", "d", true, false, true)}, null, null, null));

      Method getAttributeMethod = MBeanServer.class.getMethod("getAttribute", ObjectName.class, String.class);

      Subject admin = new Subject();
      admin.getPrincipals().add(new RolePrincipal("admin"));
      Object allowedResult = SecurityManagerShim.callAs(admin, (Callable<Object>) () -> {
         try {
            guard.invoke(mbeanServerMock, getAttributeMethod, new Object[]{objectName, "Active"});
            return "ok";
         } catch (Throwable e1) {
            return e1;
         }
      });
      assertEquals("ok", allowedResult);

      Subject nobody = new Subject();
      Object deniedResult = SecurityManagerShim.callAs(nobody, (Callable<Object>) () -> {
         try {
            guard.invoke(mbeanServerMock, getAttributeMethod, new Object[]{objectName, "Active"});
            return "ok";
         } catch (Throwable e1) {
            return e1;
         }
      });
      assertInstanceOf(SecurityException.class, deniedResult);
      assertTrue(((Exception) deniedResult).getMessage().contains("Active"));
   }

   @Test
   public void testInvokeGetAttributesStopsAtFirstDeniedAttribute() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);

      ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "getA", "admin");

      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
      Mockito.when(mbeanServerMock.getMBeanInfo(objectName)).thenReturn(
         new MBeanInfo(Object.class.getName(), "d",
            new MBeanAttributeInfo[]{
               new MBeanAttributeInfo("A", "java.lang.String", "d", true, false, false),
               new MBeanAttributeInfo("B", "java.lang.String", "d", true, false, false)
            }, null, null, null));

      Method getAttributesMethod = MBeanServer.class.getMethod("getAttributes", ObjectName.class, String[].class);

      Subject admin = new Subject();
      admin.getPrincipals().add(new RolePrincipal("admin"));
      Object result = SecurityManagerShim.callAs(admin, (Callable<Object>) () -> {
         try {
            guard.invoke(mbeanServerMock, getAttributesMethod, new Object[]{objectName, new String[]{"A", "B"}});
            return "ok";
         } catch (Throwable e1) {
            return e1;
         }
      });
      assertInstanceOf(SecurityException.class, result, "admin only has getA, so fetching B in the same call should still fail");
      assertTrue(((Exception) result).getMessage().contains("B"));
   }

   @Test
   public void testInvokeSetAttributeRequiresRoleForSetterOperation() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      guard.setJMXAccessControlList(controlList);

      ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "setName", "admin");

      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
      Mockito.when(mbeanServerMock.getMBeanInfo(objectName)).thenReturn(
         new MBeanInfo(Object.class.getName(), "d",
            new MBeanAttributeInfo[]{new MBeanAttributeInfo("Name", "java.lang.String", "d", true, true, false)}, null, null, null));

      Method setAttributeMethod = MBeanServer.class.getMethod("setAttribute", ObjectName.class, Attribute.class);
      Attribute attribute = new Attribute("Name", "newValue");

      Subject nobody = new Subject();
      Object deniedResult = SecurityManagerShim.callAs(nobody, (Callable<Object>) () -> {
         try {
            guard.invoke(mbeanServerMock, setAttributeMethod, new Object[]{objectName, attribute});
            return "ok";
         } catch (Throwable e1) {
            return e1;
         }
      });
      assertInstanceOf(SecurityException.class, deniedResult);

      Subject admin = new Subject();
      admin.getPrincipals().add(new RolePrincipal("admin"));
      Object allowedResult = SecurityManagerShim.callAs(admin, (Callable<Object>) () -> {
         try {
            guard.invoke(mbeanServerMock, setAttributeMethod, new Object[]{objectName, attribute});
            return "ok";
         } catch (Throwable e1) {
            return e1;
         }
      });
      assertEquals("ok", allowedResult);
   }

   @Test
   public void testInvokeSetAttributeThrowsIllegalStateWhenAttributeNotDeclared() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      guard.setJMXAccessControlList(JMXAccessControlList.createDefaultList());

      ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
      Mockito.when(mbeanServerMock.getMBeanInfo(objectName)).thenReturn(
         new MBeanInfo(Object.class.getName(), "d", new MBeanAttributeInfo[0], null, null, null));

      Method setAttributeMethod = MBeanServer.class.getMethod("setAttribute", ObjectName.class, Attribute.class);

      // unlike ArtemisRbacInvocationHandler, this guard does not wrap securityCheck failures in SecurityException,
      // so a missing attribute surfaces as the raw IllegalStateException thrown by handleSetAttribute
      assertThrows(IllegalStateException.class, () ->
         guard.invoke(mbeanServerMock, setAttributeMethod, new Object[]{objectName, new Attribute("ThereIsNoSuchAttribute", "x")}));
   }

   @Test
   public void testHandleQueryIsNoOpForQueryMBeansAndQueryNames() throws Exception {
      // management.xml has no way to express per-query restrictions, so query* calls are deliberately unrestricted
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);

      Method queryNames = MBeanServer.class.getMethod("queryNames", ObjectName.class, QueryExp.class);
      Method queryMBeans = MBeanServer.class.getMethod("queryMBeans", ObjectName.class, QueryExp.class);

      assertDoesNotThrow(() -> guard.invoke(mbeanServerMock, queryNames, new Object[]{null, null}));
      assertDoesNotThrow(() -> guard.invoke(mbeanServerMock, queryMBeans, new Object[]{null, null}));
   }

   @Test
   public void testInvokeBypassesSecurityForAllowListedObjectName() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToAllowList("testdomain", "broker=myBroker");
      guard.setJMXAccessControlList(controlList);

      ObjectName objectName = new ObjectName("testdomain:broker=myBroker");
      MBeanServer mbeanServerMock = Mockito.mock(MBeanServer.class);
      Method invokeMethod = MBeanServer.class.getMethod("invoke", ObjectName.class, String.class, Object[].class, String[].class);

      // no role is configured for "doSomething" at all, and there is no logged in subject; only the allow-list
      // can be letting this through
      assertDoesNotThrow(() -> guard.invoke(mbeanServerMock, invokeMethod, new Object[]{objectName, "doSomething", new Object[0], new String[0]}));
   }

   @Test
   public void testDefaultAccessControlListAllowsViewRoleForGettersButNotSetters() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      ObjectName objectName = new ObjectName("some.other.domain:type=Thing");

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new RolePrincipal("view"));

      assertTrue(SecurityManagerShim.callAs(viewSubject, (Callable<Boolean>) () ->
         guard.canInvoke(objectName.getCanonicalName(), "getSomething")));
      assertFalse(SecurityManagerShim.callAs(viewSubject, (Callable<Boolean>) () ->
         guard.canInvoke(objectName.getCanonicalName(), "setSomething")));
   }

   @Test
   public void testDefaultAccessControlListBypassesHawtioDomain() throws Exception {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      ObjectName hawtioName = new ObjectName("hawtio:type=Something");
      assertTrue(guard.canInvoke(hawtioName.getCanonicalName(), "anyMethod"));
   }

   @Test
   public void testCurrentUserHasRoleMatchesClassQualifiedPrincipal() throws Exception {
      Subject subject = new Subject();
      subject.getPrincipals().add(new CustomPrincipal("myrole"));
      assertTrue(SecurityManagerShim.callAs(subject, (Callable<Boolean>) () ->
         ArtemisMBeanServerGuard.currentUserHasRole(CustomPrincipal.class.getName() + ":myrole")));
   }

   @Test
   public void testCurrentUserHasRoleRejectsWrongPrincipalClass() throws Exception {
      Subject subject = new Subject();
      subject.getPrincipals().add(new RolePrincipal("myrole"));
      assertFalse(SecurityManagerShim.callAs(subject, (Callable<Boolean>) () ->
         ArtemisMBeanServerGuard.currentUserHasRole(CustomPrincipal.class.getName() + ":myrole")));
   }

   @Test
   public void testCurrentUserHasRoleFalseWhenNoSubject() {
      assertFalse(ArtemisMBeanServerGuard.currentUserHasRole("admin"));
   }

   private static final class CustomPrincipal implements Principal {
      private final String name;

      CustomPrincipal(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   private boolean canInvokeAs(Subject subject, JMXAccessControlList controlList, String operation) throws Throwable {
      ArtemisMBeanServerGuard guard = new ArtemisMBeanServerGuard();
      guard.setJMXAccessControlList(controlList);
      String objectName = ObjectNameBuilder.create("testdomain", "myBroker").getActiveMQServerObjectName().getCanonicalName();
      Object result = SecurityManagerShim.callAs(subject, (Callable<Object>) () -> {
         try {
            return guard.canInvoke(objectName, operation);
         } catch (Exception e1) {
            return e1;
         }
      });
      return (Boolean) result;
   }

   @Test
   public void testUserPrincipalNameIsNotARole() throws Throwable {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "deleteAddress", "admin");
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "getSomething", "view");

      Subject subject = new Subject();
      // the username happens to equal a configured role name
      subject.getPrincipals().add(new UserPrincipal("admin"));
      subject.getPrincipals().add(new RolePrincipal("view"));

      assertFalse(canInvokeAs(subject, controlList, "deleteAddress"));
      assertTrue(canInvokeAs(subject, controlList, "getSomething"));
   }

   @Test
   public void testFullyQualifiedRoleMatchesPrincipalClass() throws Throwable {
      JMXAccessControlList controlList = new JMXAccessControlList();
      controlList.addToRoleAccess("testdomain", "broker=myBroker", "getSomething", UserPrincipal.class.getName() + ":admin");

      Subject matching = new Subject();
      matching.getPrincipals().add(new UserPrincipal("admin"));
      assertTrue(canInvokeAs(matching, controlList, "getSomething"));

      // same name, different class
      Subject wrongClass = new Subject();
      wrongClass.getPrincipals().add(new RolePrincipal("admin"));
      assertFalse(canInvokeAs(wrongClass, controlList, "getSomething"));
   }
}
