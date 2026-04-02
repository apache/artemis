/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.utils;

import javax.security.auth.Subject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

public class SecurityManagerUtil {

   private static final String WILDCARD = "*";

   public static Set<RolePrincipal> getPrincipalsInRole(final CheckType checkType, final Set<Role> roles, final String rolePrincipalClass) {
      Set principals = new HashSet<>();
      for (Role role : roles) {
         if (checkType.hasRole(role)) {
            try {
               principals.add(SecurityManagerUtil.createGroupPrincipal(role.getName(), rolePrincipalClass));
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.failedAddRolePrincipal(e);
            }
         }
      }
      return principals;
   }

   public static String getUserFromSubject(Subject subject, Class<? extends Principal> principalClass) {
      if (subject != null) {
         for (Principal candidate : subject.getPrincipals(principalClass)) {
            return candidate.getName();
         }
      }
      return null;
   }

   public static Object createGroupPrincipal(String name, String groupClass) throws Exception {
      if (WILDCARD.equals(name)) {
         // simple match all group principal - match any name and class
         return new Principal() {
            @Override
            public String getName() {
               return WILDCARD;
            }

            @Override
            public boolean equals(Object other) {
               return true;
            }

            @Override
            public int hashCode() {
               return WILDCARD.hashCode();
            }
         };
      }
      Object[] param = new Object[]{name};

      Class<?> cls = Class.forName(groupClass);

      Constructor<?>[] constructors = cls.getConstructors();
      int i;
      Object instance;
      for (i = 0; i < constructors.length; i++) {
         Class<?>[] paramTypes = constructors[i].getParameterTypes();
         if (paramTypes.length != 0 && paramTypes[0].equals(String.class)) {
            break;
         }
      }
      if (i < constructors.length) {
         instance = constructors[i].newInstance(param);
      } else {
         instance = cls.newInstance();
         Method[] methods = cls.getMethods();
         i = 0;
         for (i = 0; i < methods.length; i++) {
            Class<?>[] paramTypes = methods[i].getParameterTypes();
            if (paramTypes.length != 0 && methods[i].getName().equals("setName") && paramTypes[0].equals(String.class)) {
               break;
            }
         }

         if (i < methods.length) {
            methods[i].invoke(instance, param);
         } else {
            throw new NoSuchMethodException();
         }
      }

      return instance;
   }

   /**
    * This method tries to match the RolePrincipals in the Subject with the provided Set of Roles and CheckType
    */
   public static boolean authorize(final Subject subject, final Set<Role> roles, final CheckType checkType, final String rolePrincipalClass) {
      boolean authorized = false;

      if (subject != null) {
         Set<RolePrincipal> rolesWithPermission = getPrincipalsInRole(checkType, roles, rolePrincipalClass);

         // Check the caller's roles
         Set<Principal> rolesForSubject = new HashSet<>();
         try {
            rolesForSubject.addAll(subject.getPrincipals(Class.forName(rolePrincipalClass).asSubclass(Principal.class)));
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToFindRolesForTheSubject(e);
         }
         if (rolesForSubject.size() > 0 && rolesWithPermission.size() > 0) {
            Iterator<Principal> rolesForSubjectIter = rolesForSubject.iterator();
            while (!authorized && rolesForSubjectIter.hasNext()) {
               Iterator<RolePrincipal> rolesWithPermissionIter = rolesWithPermission.iterator();
               Principal subjectRole = rolesForSubjectIter.next();
               while (!authorized && rolesWithPermissionIter.hasNext()) {
                  Principal roleWithPermission = rolesWithPermissionIter.next();
                  authorized = subjectRole.equals(roleWithPermission);
               }
            }
         }
      }

      return authorized;
   }
}
