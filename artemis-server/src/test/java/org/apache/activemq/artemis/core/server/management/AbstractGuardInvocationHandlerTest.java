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

import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.activemq.artemis.core.server.management.AbstractGuardInvocationHandler.initAuditLoggerContext;
import static org.apache.activemq.artemis.core.server.management.AbstractGuardInvocationHandler.invokeAndUnwrap;
import static org.apache.activemq.artemis.core.server.management.AbstractGuardInvocationHandler.resolveAttributeOperationName;
import static org.apache.activemq.artemis.core.server.management.AbstractGuardInvocationHandler.stripParameterList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractGuardInvocationHandlerTest extends ServerTestBase {

   @Test
   public void testStripParameterListRemovesSignature() {
      assertEquals("deleteAddress", stripParameterList("deleteAddress(java.lang.String)"));
      assertEquals("deleteAddress", stripParameterList("deleteAddress()"));
      assertEquals("deleteAddress", stripParameterList("deleteAddress"));
      assertNull(stripParameterList(null));
   }

   @Test
   public void testResolveAttributeOperationNameUsesIsPrefixForBooleanAttributes() {
      MBeanInfo info = new MBeanInfo(Object.class.getName(), "d",
         new MBeanAttributeInfo[]{new MBeanAttributeInfo("Active", "boolean", "d", true, false, true)}, null, null, null);

      assertEquals("isActive", resolveAttributeOperationName(info, "Active", "get"));
   }

   @Test
   public void testResolveAttributeOperationNameUsesGetPrefixForNonBooleanAttributes() {
      MBeanInfo info = new MBeanInfo(Object.class.getName(), "d",
         new MBeanAttributeInfo[]{new MBeanAttributeInfo("Name", "java.lang.String", "d", true, false, false)}, null, null, null);

      assertEquals("getName", resolveAttributeOperationName(info, "Name", "get"));
   }

   @Test
   public void testResolveAttributeOperationNameFallsBackToDefaultPrefixWhenAttributeUndeclared() {
      MBeanInfo info = new MBeanInfo(Object.class.getName(), "d", new MBeanAttributeInfo[0], null, null, null);

      assertEquals("getMissing", resolveAttributeOperationName(info, "Missing", "get"));
   }

   @Test
   public void testResolveAttributeOperationNameReturnsNullWhenNoDefaultPrefixAndAttributeUndeclared() {
      MBeanInfo info = new MBeanInfo(Object.class.getName(), "d", new MBeanAttributeInfo[0], null, null, null);

      assertNull(resolveAttributeOperationName(info, "Missing", null));
   }

   @Test
   public void testInvokeAndUnwrapReturnsDelegateResultOnSuccess() throws Throwable {
      Method echo = Echo.class.getDeclaredMethod("echo", String.class);

      assertEquals("hi", invokeAndUnwrap(echo, new Echo(), new Object[]{"hi"}));
   }

   @Test
   public void testInvokeAndUnwrapPropagatesUnderlyingCauseNotInvocationTargetException() throws Exception {
      Method explode = Echo.class.getDeclaredMethod("explode");

      IOException thrown = assertThrows(IOException.class, () -> invokeAndUnwrap(explode, new Echo(), null));
      assertEquals("boom", thrown.getMessage());
   }

   @Test
   public void testInitAuditLoggerContextNoopWhenAuditLoggingDisabled() {
      String baseLoggerName = AuditLogger.BASE_LOGGER.getLogger().getName();
      Configurator.setLevel(LogManager.getLogger(baseLoggerName), Level.OFF);
      AuditLogger.remoteAddress.remove();

      initAuditLoggerContext();

      assertNull(AuditLogger.getRemoteAddress());
   }

   @Test
   public void testInitAuditLoggerContextSetsInternalWhenNotRmiThread() {
      String baseLoggerName = AuditLogger.BASE_LOGGER.getLogger().getName();
      Configurator.setLevel(LogManager.getLogger(baseLoggerName), Level.INFO);
      AuditLogger.remoteAddress.remove();
      try {
         initAuditLoggerContext();
         assertEquals("@internal", AuditLogger.getRemoteAddress());
      } finally {
         Configurator.setLevel(LogManager.getLogger(baseLoggerName), Level.OFF);
         AuditLogger.remoteAddress.remove();
      }
   }

   @Test
   public void testInitAuditLoggerContextDerivesAddressFromRmiThreadName() throws Exception {
      String baseLoggerName = AuditLogger.BASE_LOGGER.getLogger().getName();
      Configurator.setLevel(LogManager.getLogger(baseLoggerName), Level.INFO);
      AtomicReference<String> captured = new AtomicReference<>();
      try {
         // real RMI connection threads are named like "RMI TCP Connection(2)-192.168.1.5"; everything after the
         // last '-' is taken as the remote address
         Thread rmiThread = new Thread(() -> {
            initAuditLoggerContext();
            captured.set(AuditLogger.getRemoteAddress());
         }, "RMI TCP Connection(2)-192.168.1.5");
         rmiThread.start();
         rmiThread.join(5000);
      } finally {
         Configurator.setLevel(LogManager.getLogger(baseLoggerName), Level.OFF);
      }

      assertEquals("@192.168.1.5", captured.get());
   }

   private static final class Echo {
      String echo(String s) {
         return s;
      }

      void explode() throws IOException {
         throw new IOException("boom");
      }
   }
}
