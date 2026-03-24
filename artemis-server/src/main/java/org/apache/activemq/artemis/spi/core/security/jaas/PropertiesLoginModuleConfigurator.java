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
package org.apache.activemq.artemis.spi.core.security.jaas;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.spi.core.security.UserManagement;
import org.apache.activemq.artemis.utils.StringUtil;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;

import static org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule.ROLE_FILE_PROP_NAME;
import static org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule.USER_FILE_PROP_NAME;

public class PropertiesLoginModuleConfigurator implements UserManagement {

   private static final String LICENSE_HEADER =
           "## ---------------------------------------------------------------------------\n" +
           "## Licensed to the Apache Software Foundation (ASF) under one or more\n" +
           "## contributor license agreements.  See the NOTICE file distributed with\n" +
           "## this work for additional information regarding copyright ownership.\n" +
           "## The ASF licenses this file to You under the Apache License, Version 2.0\n" +
           "## (the \"License\"); you may not use this file except in compliance with\n" +
           "## the License.  You may obtain a copy of the License at\n" +
           "##\n" +
           "## http://www.apache.org/licenses/LICENSE-2.0\n" +
           "##\n" +
           "## Unless required by applicable law or agreed to in writing, software\n" +
           "## distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
           "## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
           "## See the License for the specific language governing permissions and\n" +
           "## limitations under the License.\n" +
           "## ---------------------------------------------------------------------------\n";
   private FileBasedConfigurationBuilder<PropertiesConfiguration> userBuilder;
   private FileBasedConfigurationBuilder<PropertiesConfiguration> roleBuilder;
   private PropertiesConfiguration userConfig;
   private PropertiesConfiguration roleConfig;

   public PropertiesLoginModuleConfigurator(String entryName, String brokerEtc) throws Exception {
      if (entryName == null || entryName.length() == 0) {
         entryName = "activemq";
      }

      Configuration securityConfig = Configuration.getConfiguration();
      AppConfigurationEntry[] entries = securityConfig.getAppConfigurationEntry(entryName);

      if (entries == null || entries.length == 0) {
         throw ActiveMQMessageBundle.BUNDLE.failedToLoadSecurityConfig();
      }

      int entriesInspected = 0;
      for (AppConfigurationEntry entry : entries) {
         entriesInspected++;
         if (entry.getLoginModuleName().equals(PropertiesLoginModule.class.getName())) {
            String userFileName = (String) entry.getOptions().get(USER_FILE_PROP_NAME);
            String roleFileName = (String) entry.getOptions().get(ROLE_FILE_PROP_NAME);

            File etcDir = new File(brokerEtc);
            File userFile = new File(etcDir, userFileName);
            File roleFile = new File(etcDir, roleFileName);

            if (!userFile.exists()) {
               throw ActiveMQMessageBundle.BUNDLE.failedToLoadUserFile(brokerEtc + userFileName);
            }

            if (!roleFile.exists()) {
               throw ActiveMQMessageBundle.BUNDLE.failedToLoadRoleFile(brokerEtc + roleFileName);
            }

            Configurations configs = new Configurations();
            userBuilder = configs.propertiesBuilder(userFile);
            roleBuilder = configs.propertiesBuilder(roleFile);
            userConfig = userBuilder.getConfiguration();
            roleConfig = roleBuilder.getConfiguration();

            String roleHeader = roleConfig.getLayout().getHeaderComment();
            String userHeader = userConfig.getLayout().getHeaderComment();

            if (userHeader == null) {
               if (userConfig.isEmpty()) {
                  //clean and reset header
                  userConfig.clear();
                  userConfig.setHeader(LICENSE_HEADER);
               }
            }

            if (roleHeader == null) {
               if (roleConfig.isEmpty()) {
                  //clean and reset header
                  roleConfig.clear();
                  roleConfig.setHeader(LICENSE_HEADER);
               }
            }
            return;
         }
      }

      if (entriesInspected == entries.length) {
         throw ActiveMQMessageBundle.BUNDLE.failedToFindLoginModuleEntry(entryName);
      }
   }

   @Override
   public void addNewUser(String username, String hash, String... roles) {
      if (userConfig.getString(username) != null) {
         throw ActiveMQMessageBundle.BUNDLE.userAlreadyExists(username);
      }
      userConfig.addProperty(username, hash);
      addRoles(username, roles);
   }

   public void save() throws Exception {
      ReloadableProperties.LOCK.writeLock().lock();
      try {
         userBuilder.save();
         roleBuilder.save();
      } finally {
         ReloadableProperties.LOCK.writeLock().unlock();
      }
   }

   @Override
   public void removeUser(String username) {
      if (userConfig.getProperty(username) == null) {
         throw ActiveMQMessageBundle.BUNDLE.userDoesNotExist(username);
      }
      userConfig.clearProperty(username);
      removeRoles(username);
   }

   @Override
   public Map<String, Set<String>> listUser(String username) {
      Map<String, Set<String>> result = new HashMap<>();

      if (username != null && username.length() > 0) {
         result.put(username, findRoles(username));
      } else {
         Iterator<String> iter = userConfig.getKeys();
         while (iter.hasNext()) {
            String keyUser = iter.next();
            result.put(keyUser, findRoles(keyUser));
         }
      }
      return result;
   }

   @Override
   public void updateUser(String username, String password, String... roles) {
      String oldPassword = (String) userConfig.getProperty(username);
      if (oldPassword == null) {
         throw ActiveMQMessageBundle.BUNDLE.userDoesNotExist(username);
      }

      if (password != null) {
         userConfig.setProperty(username, password);
      }

      if (roles != null && roles.length > 0) {

         removeRoles(username);
         addRoles(username, roles);
      }
   }

   private Set<String> findRoles(String username) {
      Iterator<String> iter = roleConfig.getKeys();
      Set<String> roles = new HashSet();
      while (iter.hasNext()) {
         String role = iter.next();
         for (String roleList : roleConfig.getList(String.class, role)) {
            //each roleList may be a comma separated list
            String[] items = roleList.split(",");
            for (String item : items) {
               if (item.trim().equals(username)) {
                  roles.add(role);
               }
            }
         }
      }

      return roles;
   }

   private void addRoles(String username, String[] roles) {
      for (String role : roles) {
         role = role.trim();
         List<String> users = roleConfig.getList(String.class, role);
         if (users == null) {
            users = new ArrayList<>();
         }
         users.add(username);
         roleConfig.setProperty(role, StringUtil.joinStringList(users, ","));
      }
   }

   private void removeRoles(String username) {

      Iterator<String> iterKeys = roleConfig.getKeys();

      List<Pair<String, List<String>>> updateMap = new ArrayList<>();
      while (iterKeys.hasNext()) {
         String theRole = iterKeys.next();

         List<String> userList = roleConfig.getList(String.class, theRole);
         List<String> newList = new ArrayList<>();

         boolean roleChaned = false;
         for (String value : userList) {
            //each value may be comma separated.
            List<String> update = new ArrayList<>();
            String[] items = value.split(",");
            boolean found = false;
            for (String item : items) {
               if (!item.equals(username)) {
                  update.add(item);
               } else {
                  found = true;
                  roleChaned = true;
               }
            }
            if (found) {
               if (update.size() > 0) {
                  newList.add(StringUtil.joinStringList(update, ","));
               }
            }
         }
         if (roleChaned) {
            updateMap.add(new Pair(theRole, newList));
         }
      }
      //do update
      Iterator<Pair<String, List<String>>> iterUpdate = updateMap.iterator();
      while (iterUpdate.hasNext()) {
         Pair<String, List<String>> entry = iterUpdate.next();
         roleConfig.clearProperty(entry.getA());
         if (entry.getB().size() > 0) {
            roleConfig.addProperty(entry.getA(), entry.getB());
         }
      }
   }
}
