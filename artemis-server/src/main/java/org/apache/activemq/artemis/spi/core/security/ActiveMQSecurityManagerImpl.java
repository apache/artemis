/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.spi.core.security;

import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A basic implementation of the ActiveMQSecurityManager. This can be used within an appserver and be deployed by
 * BasicUserCredentialsDeployer or used standalone or embedded.
 */
@Deprecated
public class ActiveMQSecurityManagerImpl implements ActiveMQSecurityManager {

   private final SecurityConfiguration configuration;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ActiveMQSecurityManagerImpl() {
      configuration = new SecurityConfiguration();
   }

   public ActiveMQSecurityManagerImpl(SecurityConfiguration configuration) {
      this.configuration = configuration;
   }

   @Override
   public boolean validateUser(final String username, final String password) {
      if (username != null) {
         User user = configuration.getUser(username);
         return user != null && user.isValid(username, password);
      } else if (password == null) {
         return configuration.getDefaultUser() != null;
      } else { // the only possible case here is user == null, password != null
         logger.debug("Validating default user against a provided password.  This happens when username=null, password!=null");
         String defaultUsername = configuration.getDefaultUser();
         User defaultUser = configuration.getUser(defaultUsername);
         return defaultUser != null && defaultUser.isValid(defaultUsername, password);
      }
   }

   @Override
   public boolean validateUserAndRole(final String user,
                                      final String password,
                                      final Set<Role> roles,
                                      final CheckType checkType) {
      if (validateUser(user, password)) {
         String defaultUser = configuration.getDefaultUser();
         List<String> availableRoles = configuration.getRole(user == null ? defaultUser : user);

         if (availableRoles == null) {
            return false;
         }

         for (String availableRole : availableRoles) {
            if (roles != null) {
               for (Role role : roles) {
                  if (role.getName().equals(availableRole) && checkType.hasRole(role)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public SecurityConfiguration getConfiguration() {
      return configuration;
   }
}
