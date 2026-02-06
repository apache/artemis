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
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;

public class ArtemisMBeanServerGuard implements GuardInvocationHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private JMXAccessControlList jmxAccessControlList = JMXAccessControlList.createDefaultList();

   public void init() {
      ArtemisMBeanServerBuilder.setGuard(this);
   }

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getParameterTypes().length == 0)
         return null;

      if (!ObjectName.class.isAssignableFrom(method.getParameterTypes()[0]))
         return null;

      ObjectName objectName = (ObjectName) args[0];
      if ("getAttribute".equals(method.getName())) {
         handleGetAttribute((MBeanServer) proxy, objectName, (String) args[1]);
      } else if ("getAttributes".equals(method.getName())) {
         handleGetAttributes((MBeanServer) proxy, objectName, (String[]) args[1]);
      } else if ("setAttribute".equals(method.getName())) {
         handleSetAttribute((MBeanServer) proxy, objectName, (Attribute) args[1]);
      } else if ("setAttributes".equals(method.getName())) {
         handleSetAttributes((MBeanServer) proxy, objectName, (AttributeList) args[1]);
      } else if ("invoke".equals(method.getName())) {
         handleInvoke(objectName, (String) args[1]);
      }

      return null;
   }

   private void handleGetAttribute(MBeanServer proxy, ObjectName objectName, String attributeName) throws JMException, IOException {
      MBeanInfo info = proxy.getMBeanInfo(objectName);
      String prefix = null;
      for (MBeanAttributeInfo attr : info.getAttributes()) {
         if (attr.getName().equals(attributeName)) {
            prefix = attr.isIs() ? "is" : "get";
         }
      }

      if (prefix != null) {
         try {
            handleInvoke(objectName, prefix + attributeName);
         } catch (SecurityException e) {
            // The security exception message is shown in the attributes tab of the console.
            throw new SecurityException("User not authorized to access attribute: " + attributeName, e);
         }
      }
   }

   private void handleGetAttributes(MBeanServer proxy, ObjectName objectName, String[] attributeNames) throws JMException, IOException {
      for (String attr : attributeNames) {
         handleGetAttribute(proxy, objectName, attr);
      }
   }

   private void handleSetAttribute(MBeanServer proxy, ObjectName objectName, Attribute attribute) throws JMException, IOException {
      String dataType = null;
      MBeanInfo info = proxy.getMBeanInfo(objectName);
      for (MBeanAttributeInfo attr : info.getAttributes()) {
         if (attr.getName().equals(attribute.getName())) {
            dataType = attr.getType();
            break;
         }
      }

      if (dataType == null)
         throw new IllegalStateException("Attribute data type can not be found");

      handleInvoke(objectName, "set" + attribute.getName());
   }

   private void handleSetAttributes(MBeanServer proxy, ObjectName objectName, AttributeList attributes) throws JMException, IOException {
      for (Attribute attr : attributes.asList()) {
         handleSetAttribute(proxy, objectName, attr);
      }
   }

   private boolean canBypassRBAC(ObjectName objectName) {
      return jmxAccessControlList.isInAllowList(objectName);
   }

   @Override
   public boolean canInvoke(String object, String operationName) {
      ObjectName objectName = null;
      try {
         objectName = ObjectName.getInstance(object);
      } catch (MalformedObjectNameException e) {
         logger.debug("can't check invoke rights as object name invalid: {}", object, e);
         return false;
      }
      /*
       * HawtIO calls this with a null operationName as a coarse grained way of authenticating against all the
       * operations on an mbean. Until this addition this was throwing a null pointer on operationName later in this
       * call which was swallowed by HawtIO. Since fine grained checks are carried out against every operation this was
       * never an issue however the new console based on HawtIO 4 passes this exception back to the console which breaks
       * it. Since it is just an optimisation it is fine to always return true. Note that the alternative
       * ArtemisRbacInvocationHandler does allow the ability to restrict a whole mbean.
       */
      if (operationName == null || canBypassRBAC(objectName)) {
         return true;
      }

      // Strip the parameter list from operationName.
      int paramListIndex = operationName.indexOf('(');
      if (paramListIndex > 0) {
         operationName = operationName.substring(0, paramListIndex);
      }

      List<String> requiredRoles = getRequiredRoles(objectName, operationName);
      for (String role : requiredRoles) {
         if (currentUserHasRole(role)) {
            return true;
         }
      }
      logger.debug("{} {} false", object, operationName);
      return false;
   }

   void handleInvoke(ObjectName objectName, String operationName) throws IOException {
      if (canBypassRBAC(objectName)) {
         return;
      }
      List<String> requiredRoles = getRequiredRoles(objectName, operationName);
      for (String role : requiredRoles) {
         if (currentUserHasRole(role))
            return;
      }
      if (AuditLogger.isResourceLoggingEnabled()) {
         AuditLogger.objectInvokedFailure(objectName, operationName);
      }
      throw new SecurityException("User not authorized to access operation: " + operationName);
   }

   List<String> getRequiredRoles(ObjectName objectName, String methodName) {
      return jmxAccessControlList.getRolesForObject(objectName, methodName);
   }

   public void setJMXAccessControlList(JMXAccessControlList JMXAccessControlList) {
      this.jmxAccessControlList = JMXAccessControlList;
   }

   public static boolean currentUserHasRole(String requestedRole) {

      String clazz;
      String role;
      int index = requestedRole.indexOf(':');
      if (index > 0) {
         clazz = requestedRole.substring(0, index);
         role = requestedRole.substring(index + 1);
      } else {
         clazz = "org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal";
         role = requestedRole;
      }

      Subject subject = SecurityManagerShim.currentSubject();
      if (subject == null) {
         return false;
      }
      for (Principal p : subject.getPrincipals()) {
         if (clazz.equals(p.getClass().getName()) && role.equals(p.getName())) {
            return true;
         }
      }
      return false;
   }
}
