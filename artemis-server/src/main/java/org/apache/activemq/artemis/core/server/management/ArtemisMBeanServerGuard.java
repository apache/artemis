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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ArtemisMBeanServerGuard implements GuardInvocationHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private JMXAccessControlList jmxAccessControlList = JMXAccessControlList.createDefaultList();

   private final Cache<String, ObjectName> objectNameCache = Caffeine.newBuilder().maximumSize(10000).build();
   private final Cache<ObjectName, Boolean> bypassRBACCache = Caffeine.newBuilder().maximumSize(10000).build();

   private static final class CachedRolesPrincipal implements Principal {
      final Set<String> roles;

      CachedRolesPrincipal(Set<String> roles) {
         this.roles = Collections.unmodifiableSet(roles);
      }

      @Override public String getName() {
         return "__cached_roles__";
      }
   }

   public void init() {
      ArtemisMBeanServerBuilder.setGuard(this);
   }

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      ObjectName objectName = (ObjectName) args[0];
      switch (method.getName()) {
         case "getAttribute" -> handleGetAttribute((MBeanServer) proxy, objectName, (String) args[1]);
         case "getAttributes" -> handleGetAttributes((MBeanServer) proxy, objectName, (String[]) args[1]);
         case "setAttribute" -> handleSetAttribute((MBeanServer) proxy, objectName, (Attribute) args[1]);
         case "setAttributes" -> handleSetAttributes((MBeanServer) proxy, objectName, (AttributeList) args[1]);
         case "invoke" -> handleInvoke(objectName, (String) args[1]);
         default -> { }
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

      if (dataType == null) {
         throw new IllegalStateException("Attribute data type can not be found");
      }

      handleInvoke(objectName, "set" + attribute.getName());
   }

   private void handleSetAttributes(MBeanServer proxy, ObjectName objectName, AttributeList attributes) throws JMException, IOException {
      for (Attribute attr : attributes.asList()) {
         handleSetAttribute(proxy, objectName, attr);
      }
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

      // Strip the parameter list from operationName.
      int paramListIndex = operationName.indexOf('(');
      if (paramListIndex > 0) {
         operationName = operationName.substring(0, paramListIndex);
      }
      Set<String> currentUserRoles = getCurrentUserRoles();

      if (currentUserRoles.isEmpty()) {
         return false;
      }
      boolean authorized = authorizeUserForMethod(objectName, operationName, currentUserRoles);

      if (authorized) {
         logger.debug("{} {} true", object, operationName);
         return true;
      } else {
         logger.debug("{} {} false", object, operationName);
         return false;
      }

   }

   void handleInvoke(ObjectName objectName, String operationName) throws IOException {
      if (canBypassRBAC(objectName)) {
         return;
      }
      Set<String> requiredRoles = getRequiredRoles(objectName, operationName);
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

   Set<String> getRequiredRoles(ObjectName objectName, String methodName) {
      return jmxAccessControlList.getRolesForObject(objectName, methodName);
   }

   boolean authorizeUserForMethod(ObjectName objectName, String operationName, Set<String> currentUserRoles) {
      return jmxAccessControlList.authorizeUserForMethod(objectName, operationName, currentUserRoles);
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

   public static Set<String> getCurrentUserRoles() {
      Subject subject = SecurityManagerShim.currentSubject();
      if (subject == null) {
         return Collections.emptySet();
      }

      // Check if roles are already cached on the subject
      Set<CachedRolesPrincipal> cached = subject.getPrincipals(CachedRolesPrincipal.class);
      if (!cached.isEmpty()) {
         return cached.iterator().next().roles;
      }

      // First call for this subject — build and cache
      Set<String> roles = new HashSet<>();
      for (Principal p : subject.getPrincipals()) {
         roles.add(p.getName());
      }
      subject.getPrincipals().add(new CachedRolesPrincipal(roles));
      return roles;
   }

}
