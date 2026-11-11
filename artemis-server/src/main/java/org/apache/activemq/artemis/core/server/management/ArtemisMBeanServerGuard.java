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

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;

public class ArtemisMBeanServerGuard extends AbstractGuardInvocationHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private JMXAccessControlList jmxAccessControlList = JMXAccessControlList.createDefaultList();

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getParameterTypes().length == 0) {
         return null;
      }

      if (!ObjectName.class.isAssignableFrom(method.getParameterTypes()[0])) {
         return null;
      }

      securityCheck((MBeanServer) proxy, method, args);

      return null;
   }

   @Override
   public void handleQuery(String methodName) throws Exception {
      // management.xml has no way to limit the execution of queryNames() or queryMBeans() on the underlying MBeanServer
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

      operationName = stripParameterList(operationName);

      List<String> requiredRoles = getRequiredRoles(objectName, operationName);
      for (String role : requiredRoles) {
         if (currentUserHasRole(role)) {
            return true;
         }
      }
      logger.debug("{} {} false", object, operationName);
      return false;
   }

   @Override
   public void handleInvoke(ObjectName objectName, String operationName) {
      if (canBypassRBAC(objectName)) {
         return;
      }
      List<String> requiredRoles = getRequiredRoles(objectName, operationName);
      for (String role : requiredRoles) {
         if (currentUserHasRole(role)) {
            return;
         }
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
