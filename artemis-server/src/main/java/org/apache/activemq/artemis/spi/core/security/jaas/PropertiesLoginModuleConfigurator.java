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
import java.lang.invoke.MethodHandles;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule.ROLE_FILE_PROP_NAME;
import static org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule.USER_FILE_PROP_NAME;

public class PropertiesLoginModuleConfigurator implements UserManagement {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String LICENSE_HEADER = """
      ## ---------------------------------------------------------------------------
      ## Licensed to the Apache Software Foundation (ASF) under one or more
      ## contributor license agreements.  See the NOTICE file distributed with
      ## this work for additional information regarding copyright ownership.
      ## The ASF licenses this file to You under the Apache License, Version 2.0
      ## (the "License"); you may not use this file except in compliance with
      ## the License.  You may obtain a copy of the License at
      ##
      ## http://www.apache.org/licenses/LICENSE-2.0
      ##
      ## Unless required by applicable law or agreed to in writing, software
      ## distributed under the License is distributed on an "AS IS" BASIS,
      ## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      ## See the License for the specific language governing permissions and
      ## limitations under the License.
      ## ---------------------------------------------------------------------------
      """;
   private FileBasedConfigurationBuilder<PropertiesConfiguration> userBuilder;
   private FileBasedConfigurationBuilder<PropertiesConfiguration> roleBuilder;
   private PropertiesConfiguration userConfig;
   private PropertiesConfiguration roleConfig;

   public PropertiesLoginModuleConfigurator(String entryName, String brokerEtc) throws Exception {
      if (entryName == null || entryName.isEmpty()) {
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

            File baseDir = resolveBaseDir(entry.getOptions(), brokerEtc);
            File userFile = new File(baseDir, userFileName);
            File roleFile = new File(baseDir, roleFileName);

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

   /**
    * Resolve the base directory for property files using the same priority order as
    * {@link PropertiesLoader.FileNameKey}:
    * <ol>
    *   <li>The {@code baseDir} option from the JAAS login-module options</li>
    *   <li>The parent directory of the {@code java.security.auth.login.config} system property</li>
    *   <li>The broker {@code etc} directory as a last resort</li>
    * </ol>
    */
   private File resolveBaseDir(Map<String, ?> options, String brokerEtc) {
      Object baseDirOption = options.get("baseDir");
      if (baseDirOption != null) {
         File dir = new File((String) baseDirOption);
         logger.debug("Using baseDir from JAAS options: {}", dir.getAbsolutePath());
         return dir;
      }

      File loginConfigParent = PropertiesLoader.FileNameKey.parentDirOfLoginConfigSystemProperty();
      if (loginConfigParent != null) {
         logger.debug("Using baseDir from login.config location: {}", loginConfigParent.getAbsolutePath());
         return loginConfigParent;
      }

      File etcDir = new File(brokerEtc);
      logger.debug("Using broker etc dir as baseDir: {}", etcDir.getAbsolutePath());
      return etcDir;
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

      if (username != null && !username.isEmpty()) {
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
               if (!update.isEmpty()) {
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
         if (!entry.getB().isEmpty()) {
            roleConfig.addProperty(entry.getA(), entry.getB());
         }
      }
   }
}
