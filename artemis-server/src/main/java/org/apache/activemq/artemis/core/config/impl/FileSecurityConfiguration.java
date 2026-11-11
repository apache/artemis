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
package org.apache.activemq.artemis.core.config.impl;

import java.net.URL;
import java.util.Properties;
import java.util.Set;

import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

@Deprecated
public class FileSecurityConfiguration extends SecurityConfiguration {

   private final String usersUrl;

   private final String rolesUrl;

   private Boolean maskPassword;

   private String passwordCodec;

   private boolean started;

   public FileSecurityConfiguration(String usersUrl,
                                    String rolesUrl,
                                    String defaultUser,
                                    Boolean maskPassword,
                                    String passwordCodec) {
      this.usersUrl = usersUrl;
      this.rolesUrl = rolesUrl;
      this.defaultUser = defaultUser;
      this.maskPassword = maskPassword;
      this.passwordCodec = passwordCodec;
   }

   public void stop() throws Exception {
      users.clear();

      roles.clear();

      defaultUser = null;
   }

   public boolean isStarted() {
      return true;
   }

   public synchronized void start() throws Exception {
      if (started) {
         return;
      }

      URL theUsersUrl = getClass().getClassLoader().getResource(usersUrl);

      if (theUsersUrl == null) {
         // The URL is outside of the classloader. Trying a pure url now
         theUsersUrl = new URL(usersUrl);
      }
      Properties userProps = new Properties();
      userProps.load(theUsersUrl.openStream());
      URL theRolesUrl = getClass().getClassLoader().getResource(usersUrl);

      if (theRolesUrl == null) {
         // The URL is outside of the classloader. Trying a pure url now
         theRolesUrl = new URL(rolesUrl);
      }
      Properties roleProps = new Properties();
      roleProps.load(theRolesUrl.openStream());

      Set<String> keys = userProps.stringPropertyNames();

      for (String username : keys) {
         String password = userProps.getProperty(username);
         password = PasswordMaskingUtil.resolveMask(this.maskPassword, password, passwordCodec);
         addUser(username, password);
      }

      for (String username : keys) {
         String roles = roleProps.getProperty(username);
         if (roles == null) {
            ActiveMQServerLogger.LOGGER.cannotFindRoleForUser(username);
         } else {
            String[] split = roles.split(",");
            for (String role : split) {
               addRole(username, role.trim());
            }
         }
      }

      started = true;

   }
}
