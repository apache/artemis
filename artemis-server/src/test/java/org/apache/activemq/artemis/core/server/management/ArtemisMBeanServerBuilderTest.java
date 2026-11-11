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

import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.ObjectName;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArtemisMBeanServerBuilderTest extends ServerTestBase {

   // ArtemisMBeanServerBuilder.newMBeanServer(defaultDomain, outer, delegate) does not wrap "outer" - it wraps
   // whatever super.newMBeanServer(...) constructs internally (a real JmxMBeanServer). These mocks only satisfy
   // the JMX builder signature; the handler never delegates to them, so tests below exercise the real MBeanServer
   // that each newMBeanServer() call creates, the same way ArtemisRbacMBeanServerBuilderTest does.
   ArtemisMBeanServerBuilder underTest;
   MBeanServer mbeanServer;
   MBeanServerDelegate mBeanServerDelegate;

   @BeforeEach
   public void setUnderTest() {
      underTest = new ArtemisMBeanServerBuilder();
      mbeanServer = Mockito.mock(MBeanServer.class);
      mBeanServerDelegate = Mockito.mock(MBeanServerDelegate.class);
      // the guard is a static, JVM-wide reference shared by every proxy; tests must not leak it to each other
      ArtemisMBeanServerBuilder.setGuard(null);
   }

   @AfterEach
   public void clearGuard() {
      ArtemisMBeanServerBuilder.setGuard(null);
   }

   @Test
   public void testGuardedCallThrowsIllegalStateWhenGuardNotSet() throws Exception {
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ObjectName name = new ObjectName("d:type=Thing");

      assertThrows(IllegalStateException.class, () -> proxy.getAttribute(name, "Attr"));
   }

   @Test
   public void testUnguardedCallSucceedsWithoutGuardConfigured() throws Exception {
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      // getDefaultDomain() isn't a guarded method, so this works even with no guard configured at all
      assertEquals("d", proxy.getDefaultDomain());
   }

   @Test
   public void testGuardedCallInvokesGuardThenDelegate() throws Throwable {
      InvocationHandler guard = Mockito.mock(InvocationHandler.class);
      ArtemisMBeanServerBuilder.setGuard(guard);
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ObjectName name = new ObjectName("d:type=Thing");
      proxy.registerMBean(new Thing(), name);

      Object result = proxy.getAttribute(name, "Attr");

      assertEquals("value", result);
      Mockito.verify(guard).invoke(Mockito.eq(proxy), Mockito.any(Method.class), Mockito.any());
   }

   @Test
   public void testDelegateExceptionIsUnwrappedNotWrappedInUndeclaredThrowable() throws Exception {
      ArtemisMBeanServerBuilder.setGuard(Mockito.mock(InvocationHandler.class));
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ObjectName name = new ObjectName("d:type=Thing");
      proxy.registerMBean(new Thing(), name);

      assertThrows(AttributeNotFoundException.class, () -> proxy.getAttribute(name, "ThereIsNoSuchAttribute"));
   }

   @Test
   public void testUnregisteredObjectNameThrowsInstanceNotFoundException() throws Exception {
      ArtemisMBeanServerBuilder.setGuard(Mockito.mock(InvocationHandler.class));
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      ObjectName name = new ObjectName("d:type=NeverRegistered");

      assertThrows(InstanceNotFoundException.class, () -> proxy.getAttribute(name, "Attr"));
   }

   @Test
   public void testEqualsIsReflexiveBecauseItUnwrapsItselfToCompareRealDelegates() throws Exception {
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      // without unwrapping args[0], this would compare the real wrapped MBeanServer against the proxy object
      // itself and always return false, breaking basic equals reflexivity
      assertTrue(proxy.equals(proxy));
   }

   @Test
   public void testEqualsOfTwoDistinctProxiesIsFalse() throws Exception {
      MBeanServer proxyOne = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);
      MBeanServer proxyTwo = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      // each call to newMBeanServer() creates its own fresh underlying MBeanServer, even with identical arguments
      assertFalse(proxyOne.equals(proxyTwo));
   }

   @Test
   public void testEqualsWithNonProxyArgumentDoesNotUnwrap() throws Exception {
      MBeanServer proxy = underTest.newMBeanServer("d", mbeanServer, mBeanServerDelegate);

      assertFalse(proxy.equals("not an mbean server"));
   }

   public static final class Thing implements ThingMBean {
      @Override
      public String getAttr() {
         return "value";
      }
   }

   public interface ThingMBean {
      String getAttr();
   }
}
