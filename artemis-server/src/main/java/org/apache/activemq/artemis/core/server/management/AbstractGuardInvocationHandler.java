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

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.apache.activemq.artemis.logs.AuditLogger;

public abstract class AbstractGuardInvocationHandler implements GuardInvocationHandler {

   static final List<String> mBeanServerCheckedMethods = List.of("invoke", "getAttribute", "getAttributes", "setAttribute", "setAttributes", "queryMBeans", "queryNames");

   abstract void handleQuery(String methodName) throws Exception;

   abstract void handleInvoke(ObjectName objectName, String operationName) throws Exception;

   void securityCheck(MBeanServer delegate, Method method, Object[] args) throws Exception {
      final String methodName = method.getName();
      ObjectName objectName = (ObjectName) args[0];

      switch (methodName) {
         case "getAttribute" -> handleGetAttribute(delegate, objectName, (String) args[1]);
         case "getAttributes" -> handleGetAttributes(delegate, objectName, (String[]) args[1]);
         case "setAttribute" -> handleSetAttribute(delegate, objectName, (Attribute) args[1]);
         case "setAttributes" -> handleSetAttributes(delegate, objectName, (AttributeList) args[1]);
         case "invoke" -> handleInvoke(objectName, (String) args[1]);
         case "queryMBeans", "queryNames" -> handleQuery(methodName);
         default -> { }
      }
   }

   void handleGetAttributes(MBeanServer delegate, ObjectName objectName, String[] attributes) throws Exception {
      for (String attribute : attributes) {
         handleGetAttribute(delegate, objectName, attribute);
      }
   }

   void handleGetAttribute(MBeanServer proxy, ObjectName objectName, String attributeName) throws Exception {
      String operationName = resolveAttributeOperationName(proxy.getMBeanInfo(objectName), attributeName, "get");

      if (operationName != null) {
         try {
            handleInvoke(objectName, operationName);
         } catch (SecurityException e) {
            // The security exception message is shown in the attributes tab of the console.
            throw new SecurityException("User not authorized to access attribute: " + attributeName, e);
         }
      }
   }

   void handleSetAttributes(MBeanServer delegate, ObjectName objectName, AttributeList attributeList) throws Exception {
      for (Attribute attributeName : attributeList.asList()) {
         handleSetAttribute(delegate, objectName, attributeName);
      }
   }

   void handleSetAttribute(MBeanServer proxy, ObjectName objectName, Attribute attribute) throws Exception {
      String dataType = null;
      for (MBeanAttributeInfo attr : proxy.getMBeanInfo(objectName).getAttributes()) {
         if (attr.getName().equals(attribute.getName())) {
            dataType = attr.getType();
            break;
         }
      }

      if (dataType == null) {
         throw new IllegalStateException("Attribute data type can not be found");
      }

      handleInvoke(objectName, "set" + attribute.getName());
   }

   /**
    * If this is invoked via jolokia the remote address will already be set by its filter; otherwise derive it from the
    * RMI connection thread name, or mark it internal.
    */
   static void initAuditLoggerContext() {
      if (AuditLogger.isAnyLoggingEnabled() && AuditLogger.getRemoteAddress() == null) {
         String url = "internal";
         final String name = Thread.currentThread().getName();
         if (name.startsWith("RMI TCP Connection")) {
            url = name.substring(name.indexOf('-') + 1);
         }
         AuditLogger.setRemoteAddress(url);
      }
   }

   /**
    * Strips any {@code (paramType,...)} signature suffix some callers (e.g. the console) append to operation names.
    */
   static String stripParameterList(String operationName) {
      if (operationName != null) {
         int paramListIndex = operationName.indexOf('(');
         if (paramListIndex > 0) {
            return operationName.substring(0, paramListIndex);
         }
      }
      return operationName;
   }

   /**
    * Resolves the {@code is}/{@code get}-prefixed operation name backing an attribute, falling back to
    * {@code defaultPrefix} (which may be {@code null}) if the attribute isn't declared on the MBean.
    */
   static String resolveAttributeOperationName(MBeanInfo info, String attributeName, String defaultPrefix) {
      String prefix = defaultPrefix;
      for (MBeanAttributeInfo attr : info.getAttributes()) {
         if (attr.getName().equals(attributeName)) {
            prefix = attr.isIs() ? "is" : "get";
            break;
         }
      }
      return prefix != null ? prefix + attributeName : null;
   }

   /**
    * Invokes {@code method} on {@code target}, unwrapping the checked {@link InvocationTargetException} that reflection
    * wraps the actual target exception in.
    */
   static Object invokeAndUnwrap(Method method, Object target, Object[] args) throws Throwable {
      try {
         return method.invoke(target, args);
      } catch (InvocationTargetException ite) {
         throw ite.getCause();
      }
   }

   /**
    * {@link Proxy} always routes a proxy's {@code equals} call to its invocation handler, but it never unwraps the
    * argument of that call. If {@code method} is {@code equals(Object)} and {@code args[0]} is itself a proxy backed by
    * a {@link MBeanServerWrapper}, this replaces it with the real delegate so the subsequent equals call compares the two
    * real wrapped objects. A no-op for any other method.
    */
   static void handleEquals(Method method, Object[] args) {
      if ("equals".equals(method.getName()) && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == Object.class) {
         Object target = args[0];
         if (target != null && Proxy.isProxyClass(target.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(target);
            if (handler instanceof MBeanServerWrapper mBeanServerWrapper) {
               args[0] = mBeanServerWrapper.unwrap();
            }
         }
      }
   }
}
