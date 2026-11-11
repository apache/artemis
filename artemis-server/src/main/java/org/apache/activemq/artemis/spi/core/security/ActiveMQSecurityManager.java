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

import javax.security.auth.Subject;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.SecurityManagerUtil;

/**
 * Use to validate whether a user has is valid to connect to the server and perform certain functions
 */
public interface ActiveMQSecurityManager {

   default String getDomain() {
      return "activemq";
   }

   /**
    * is this a valid user.
    *
    * @param user     the user
    * @param password the users password
    * @return {@code true} if a valid user
    */
   boolean validateUser(String user, String password);

   /**
    * is this a valid user and do they have the correct role
    *
    * @param user      the user
    * @param password  the users password
    * @param roles     the roles the user has
    * @param checkType the type of check to perform
    * @return {@code true} if the user is valid and they have the correct roles
    */
   boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType);

   /**
    * Initialize the manager with the given configuration properties. This method is called by the broker when the
    * file-based configuration is read. If you're creating/configuring the plugin programmatically then the recommended
    * approach is to simply use the manager's getters/setters rather than this method.
    *
    * @param properties name/value pairs used to configure the ActiveMQSecurityManager instance
    * @return {@code this} instance
    */
   default ActiveMQSecurityManager init(Map<String, String> properties) {
      return this;
   }

   default String getUserFromSubject(Subject subject) {
      return SecurityManagerUtil.getUserFromSubject(subject, UserPrincipal.class);
   }
}
