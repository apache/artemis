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

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.security.User;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;

public class SecurityConfiguration extends Configuration {

   protected final Map<String, User> users = new HashMap<>();

   protected String defaultUser = null;

   protected final Map<String, List<String>> roles = new HashMap<>();

   public SecurityConfiguration() {
   }

   public SecurityConfiguration(Map<String, String> users, Map<String, List<String>> roles) {
      for (Map.Entry<String, String> entry : users.entrySet()) {
         addUser(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, List<String>> entry : roles.entrySet()) {
         for (String role : entry.getValue()) {
            addRole(entry.getKey(), role);
         }
      }
   }

   public void addUser(final String user, final String password) {
      if (user == null) {
         throw ActiveMQMessageBundle.BUNDLE.nullUser();
      }
      if (password == null) {
         throw ActiveMQMessageBundle.BUNDLE.nullPassword();
      }
      users.put(user, new User(user, password));
   }

   public void removeUser(final String user) {
      users.remove(user);
      roles.remove(user);
   }

   public void addRole(final String user, final String role) {
      if (roles.get(user) == null) {
         roles.put(user, new ArrayList<>());
      }
      roles.get(user).add(role);
   }

   public void removeRole(final String user, final String role) {
      if (roles.get(user) == null) {
         return;
      }
      roles.get(user).remove(role);
   }

   /**
    * Set the default user for null users.
    */
   public void setDefaultUser(final String username) {
      defaultUser = username;
   }

   public String getDefaultUser() {
      return defaultUser;
   }

   public org.apache.activemq.artemis.core.security.User getUser(String username) {
      return users.get(username);
   }

   public List<String> getRole(String username) {
      return roles.get(username);
   }

   @Override
   public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
      Map<String, SecurityConfiguration> map = new HashMap<>();
      map.put(InVMLoginModule.CONFIG_PROP_NAME, this);
      AppConfigurationEntry appConfigurationEntry = new AppConfigurationEntry(name, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, map);

      return new AppConfigurationEntry[]{appConfigurationEntry};
   }
}
