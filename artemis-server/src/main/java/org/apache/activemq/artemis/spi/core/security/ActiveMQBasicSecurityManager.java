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
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.User;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.SecurityManagerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * All user and role state (both in memory and on disk) is maintained by the underlying StorageManager
 */
public class ActiveMQBasicSecurityManager implements ActiveMQSecurityManager5, UserManagement {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String BOOTSTRAP_USER = "bootstrapUser";
   public static final String BOOTSTRAP_PASSWORD = "bootstrapPassword";
   public static final String BOOTSTRAP_ROLE = "bootstrapRole";
   public static final String BOOTSTRAP_USER_FILE = "bootstrapUserFile";
   public static final String BOOTSTRAP_ROLE_FILE = "bootstrapRoleFile";

   private Map<String, String> properties;
   private StorageManager storageManager;

   @Override
   public ActiveMQBasicSecurityManager init(Map<String, String> properties) {
      if ((!properties.containsKey(BOOTSTRAP_USER) || !properties.containsKey(BOOTSTRAP_PASSWORD) || !properties.containsKey(BOOTSTRAP_ROLE)) && (!properties.containsKey(BOOTSTRAP_USER_FILE) || !properties.containsKey(BOOTSTRAP_ROLE_FILE))) {
         ActiveMQServerLogger.LOGGER.noBootstrapCredentialsFound();
      } else {
         this.properties = properties;
      }
      return this;
   }

   @Override
   public boolean validateUser(String user, String password) {
      throw new UnsupportedOperationException("Invoke authenticate(String, String, RemotingConnection, String) instead");
   }

   @Override
   public Subject authenticate(final String userToAuthenticate, final String passwordToAuthenticate, RemotingConnection remotingConnection, final String securityDomain) {
      try {
         if (storageManager.isStarted() && storageManager.getPersistedUsers() != null) {
            PersistedUser persistedUser = storageManager.getPersistedUsers().get(userToAuthenticate);
            if (persistedUser != null) {
               User user = new User(persistedUser.getUsername(), persistedUser.getPassword());
               if (user.isValid(userToAuthenticate, passwordToAuthenticate)) {
                  Subject subject = new Subject();
                  subject.getPrincipals().add(new UserPrincipal(userToAuthenticate));
                  for (String role : getRole(userToAuthenticate).getRoles()) {
                     subject.getPrincipals().add((Principal) SecurityManagerUtil.createGroupPrincipal(role, RolePrincipal.class));
                  }
                  return subject;
               }
            }
         }
      } catch (Exception e) {
         logger.debug("Couldn't validate user", e);
      }

      return null;
   }

   @Override
   public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
      throw new UnsupportedOperationException("Invoke authorize(Subject, Set<Role>, CheckType, String) instead");
   }

   @Override
   public boolean authorize(final Subject subject,
                            final Set<Role> roles,
                            final CheckType checkType,
                            final String address) {
      boolean authorized = SecurityManagerUtil.authorize(subject, roles, checkType, RolePrincipal.class);

      return authorized;
   }

   @Override
   public synchronized void addNewUser(String user, String password, String... roles) throws Exception {
      if (user == null) {
         throw ActiveMQMessageBundle.BUNDLE.nullUser();
      }
      if (password == null) {
         throw ActiveMQMessageBundle.BUNDLE.nullPassword();
      }
      if (userExists(user)) {
         throw ActiveMQMessageBundle.BUNDLE.userAlreadyExists(user);
      }

      storageManager.storeUser(new PersistedUser(user, password));
      storageManager.storeRole(new PersistedRole(user, Arrays.asList(roles)));
   }

   @Override
   public synchronized void removeUser(final String user) throws Exception {
      if (!userExists(user)) {
         throw ActiveMQMessageBundle.BUNDLE.userDoesNotExist(user);
      }

      storageManager.deleteUser(user);
      storageManager.deleteRole(user);
   }

   @Override
   public synchronized Map<String, Set<String>> listUser(String user) {
      // a null or empty user is actually valid here
      if (user != null && !user.isEmpty() && !userExists(user)) {
         throw ActiveMQMessageBundle.BUNDLE.userDoesNotExist(user);
      }

      Map<String, Set<String>> result = new HashMap<>();

      if (user != null && !user.isEmpty()) {
         result.put(user, new HashSet<>(getRole(user).getRoles()));
      } else {
         for (String thisUser : storageManager.getPersistedUsers().keySet()) {
            result.put(thisUser, new HashSet<>(getRole(thisUser).getRoles()));
         }
      }
      return result;
   }

   @Override
   public synchronized void updateUser(String user, String password, String... roles) throws Exception {
      if (!userExists(user)) {
         throw ActiveMQMessageBundle.BUNDLE.userDoesNotExist(user);
      }

      // potentially update the user's password
      if (password != null) {
         storageManager.deleteUser(user);
         storageManager.storeUser(new PersistedUser(user, password));
      }

      // potentially update the user's role(s)
      if (roles != null && roles.length > 0) {
         storageManager.deleteRole(user);
         storageManager.storeRole(new PersistedRole(user, Arrays.asList(roles)));
      }
   }

   public void completeInit(StorageManager storageManager) {
      this.storageManager = storageManager;

      // add/update the bootstrap credentials now that the StorageManager is set
      if (properties != null && properties.containsKey(BOOTSTRAP_USER_FILE) && properties.containsKey(BOOTSTRAP_ROLE_FILE)) {
         Properties users = ClassloadingUtil.loadProperties(properties.get(BOOTSTRAP_USER_FILE));
         Map<String, Set<String>> rolesByUser = invertProperties(ClassloadingUtil.loadProperties(properties.get(BOOTSTRAP_ROLE_FILE)));
         for (String user : users.stringPropertyNames()) {
            addOrUpdateUser(user, users.getProperty(user), rolesByUser.get(user).toArray(new String[0]));
         }
      } else if (properties != null && properties.containsKey(BOOTSTRAP_USER) && properties.containsKey(BOOTSTRAP_PASSWORD) && properties.containsKey(BOOTSTRAP_ROLE)) {
         addOrUpdateUser(properties.get(BOOTSTRAP_USER), properties.get(BOOTSTRAP_PASSWORD), new String[]{properties.get(BOOTSTRAP_ROLE)});
      }
   }

   private void addOrUpdateUser(String user, String password, String... roles) {
      try {
         if (userExists(user)) {
            updateUser(user, password, roles);
         } else {
            addNewUser(user, password, roles);
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToCreateBootstrapCredentials(user, e);
      }
   }

   /*
    * The roles properties file used by the PropertiesLoginModule is in the format role=user1,user2. We need to change
    * that so we can look up a user and get their roles instead.
    */
   private Map<String, Set<String>> invertProperties(Properties props) {
      Map<String, Set<String>> invertedProps = new HashMap<>();

      for (Map.Entry<Object, Object> val : props.entrySet()) {
         for (String user : ((String) val.getValue()).split(",")) {
            Set<String> tempRoles = invertedProps.get(user);
            if (tempRoles == null) {
               tempRoles = new HashSet<>();
               invertedProps.put(user, tempRoles);
            }
            tempRoles.add((String) val.getKey());
         }
      }
      return invertedProps;
   }

   private boolean userExists(String user) {
      return user != null && storageManager.getPersistedUsers() != null && storageManager.getPersistedUsers().containsKey(user);
   }

   private PersistedRole getRole(String user) {
      return storageManager.getPersistedRoles().get(user);
   }
}
