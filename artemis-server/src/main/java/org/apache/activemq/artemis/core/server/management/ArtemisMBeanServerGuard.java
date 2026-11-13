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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ArtemisMBeanServerGuard extends AbstractGuardInvocationHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private JMXAccessControlList jmxAccessControlList = JMXAccessControlList.createDefaultList();

   private static final String defaultRoleClass = "org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal";
   private final Cache<ObjectName, Boolean> bypassRBACCache = Caffeine.newBuilder().maximumSize(10000).build();
   private static final Cache<String, ObjectName> objectNameCache = Caffeine.newBuilder().maximumSize(10000).build();
   private static final Cache<Subject, Set<String>> userRolesCache = Caffeine.newBuilder().weakKeys().maximumSize(10000).build();

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      securityCheck((MBeanServer) proxy, method, args);
      return null;
   }

   @Override
   public void handleQuery(String methodName) throws Exception {
      // management.xml has no way to limit the execution of queryNames() or queryMBeans() on the underlying MBeanServer
   }

   private boolean canBypassRBAC(ObjectName objectName) {
      return bypassRBACCache.get(objectName, jmxAccessControlList::isInAllowList);
   }

   @Override
   public boolean canInvoke(String object, String operationName) {

      /*
       * HawtIO calls this with a null operationName as a coarse grained way of authenticating against all the
       * operations on an mbean. Until this addition this was throwing a null pointer on operationName later in this
       * call which was swallowed by HawtIO. Since fine grained checks are carried out against every operation this was
       * never an issue however the new console based on HawtIO 4 passes this exception back to the console which breaks
       * it. Since it is just an optimisation it is fine to always return true. Note that the alternative
       * ArtemisRbacInvocationHandler does allow the ability to restrict a whole mbean.
       */
      if (operationName == null) {
         return true;
      }

      ObjectName objectName = objectNameCache.get(object, key -> {
         try {
            return ObjectName.getInstance(key);
         } catch (MalformedObjectNameException e) {
            logger.debug("can't check invoke rights as object name invalid: {}", key, e);
            return null;
         }
      });

      if (objectName == null) {
         return false;
      }

      if (canBypassRBAC(objectName)) {
         return true;
      }

      Set<String> currentUserRoles = getCurrentUserRoles();
      if (currentUserRoles.isEmpty()) {
         return false;
      }

      operationName = stripParameterList(operationName);

      boolean authorized = authorizeUserForMethod(objectName, operationName, currentUserRoles);

      if (authorized) {
         logger.debug("{} {} true", object, operationName);
         return true;
      } else {
         logger.debug("{} {} false", object, operationName);
         return false;
      }

   }

   @Override
   public void handleInvoke(ObjectName objectName, String operationName) {
      if (canBypassRBAC(objectName)) {
         return;
      }
      Set<String> currentUserRoles = getCurrentUserRoles();
      if (currentUserRoles.isEmpty()) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.objectInvokedFailure(objectName, operationName);
         }
         throw new SecurityException("User not authorized to access operation: " + operationName);
      }
      boolean authorized = authorizeUserForMethod(objectName, operationName, currentUserRoles);
      if (authorized) {
         return;
      }
      if (AuditLogger.isResourceLoggingEnabled()) {
         AuditLogger.objectInvokedFailure(objectName, operationName);
      }
      throw new SecurityException("User not authorized to access operation: " + operationName);
   }

   boolean authorizeUserForMethod(ObjectName objectName, String operationName, Set<String> currentUserRoles) {
      return jmxAccessControlList.authorizeUserForMethod(objectName, operationName, currentUserRoles);
   }

   public void setJMXAccessControlList(JMXAccessControlList JMXAccessControlList) {
      this.jmxAccessControlList = JMXAccessControlList;
   }

   public static Set<String> getCurrentUserRoles() {
      Subject subject = SecurityManagerShim.currentSubject();
      if (subject == null) {
         return Collections.emptySet();
      }

      return userRolesCache.get(subject, s -> {
         Set<String> roles = new HashSet<>();
         for (Principal p : s.getPrincipals()) {
            String clazz = p.getClass().getName();
            roles.add(clazz + ":" + p.getName());
            if (defaultRoleClass.equals(clazz) && p.getName().indexOf(':') <= 0) {
               roles.add(p.getName());
            }
         }
         return Collections.unmodifiableSet(roles);
      });
   }

}
