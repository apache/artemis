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

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.activemq.artemis.utils.HashProcessor;
import org.apache.activemq.artemis.utils.LazyHashProcessor;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.SecureHashProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class PropertiesLoginModule extends PropertiesLoader implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String USER_FILE_PROP_NAME = "org.apache.activemq.jaas.properties.user";
   public static final String ROLE_FILE_PROP_NAME = "org.apache.activemq.jaas.properties.role";
   public static final String PASSWORD_CODEC_PROP_NAME = "org.apache.activemq.jaas.properties.password.codec";

   private Subject subject;
   private CallbackHandler callbackHandler;

   private Properties users;
   private Map<String, Set<String>> roles;
   private String user;
   private final Set<Principal> principals = new LinkedHashSet<>();
   private boolean loginSucceeded;
   private HashProcessor hashProcessor;

   @Override
   public void initialize(Subject subject,
                          CallbackHandler callbackHandler,
                          Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.callbackHandler = callbackHandler;
      loginSucceeded = false;

      init(options);
      users = load(USER_FILE_PROP_NAME, "user", options).getProps();
      roles = load(ROLE_FILE_PROP_NAME, "role", options).invertedPropertiesValuesMap();

      String passwordCodec = (String)options.get(PASSWORD_CODEC_PROP_NAME);
      if (passwordCodec != null) {
         hashProcessor = new LazyHashProcessor() {
            @Override
            protected HashProcessor createHashProcessor() throws Exception {
               return new SecureHashProcessor(PasswordMaskingUtil.getCodec(passwordCodec));
            }
         };
      }
   }

   @Override
   public boolean login() throws LoginException {
      HashProcessor userHashProcessor;
      Callback[] callbacks = new Callback[2];

      callbacks[0] = new NameCallback("Username: ");
      callbacks[1] = new PasswordCallback("Password: ", false);
      try {
         callbackHandler.handle(callbacks);
      } catch (IOException ioe) {
         throw new LoginException(ioe.getMessage());
      } catch (UnsupportedCallbackException uce) {
         throw new LoginException(uce.getMessage() + " not available to obtain information from user");
      }
      user = ((NameCallback) callbacks[0]).getName();
      char[] tmpPassword = ((PasswordCallback) callbacks[1]).getPassword();
      if (tmpPassword == null) {
         tmpPassword = new char[0];
      }
      if (user == null) {
         throw new FailedLoginException("User is null");
      }
      String password = users.getProperty(user);

      if (password == null) {
         throw new FailedLoginException("User does not exist: " + user);
      }

      try {
         userHashProcessor = PasswordMaskingUtil.getHashProcessor(password, hashProcessor);
      } catch (Exception e) {
         throw new FailedLoginException("Failed to get hash processor");
      }

      if (!userHashProcessor.compare(tmpPassword, password)) {
         throw new FailedLoginException("Password does not match for user: " + user);
      }
      loginSucceeded = true;

      if (debug) {
         logger.debug("login {}", user);
      }
      return loginSucceeded;
   }

   @Override
   public boolean commit() throws LoginException {
      boolean result = loginSucceeded;
      Set<UserPrincipal> authenticatedUsers = subject.getPrincipals(UserPrincipal.class);
      if (result) {
         UserPrincipal userPrincipal = new UserPrincipal(user);
         principals.add(userPrincipal);
         authenticatedUsers.add(userPrincipal);
      }

      // populate roles for UserPrincipal from other login modules too
      for (UserPrincipal userPrincipal : authenticatedUsers) {
         Set<String> matchedRoles = roles.get(userPrincipal.getName());
         if (matchedRoles != null) {
            for (String entry : matchedRoles) {
               principals.add(new RolePrincipal(entry));
            }
         }
      }

      subject.getPrincipals().addAll(principals);

      // will whack loginSucceeded
      clear();

      if (debug) {
         logger.debug("commit, result: {}", result);
      }
      return result;
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(user);
      clear();

      if (debug) {
         logger.debug("abort");
      }
      return true;
   }

   @Override
   public boolean logout() throws LoginException {
      subject.getPrincipals().removeAll(principals);
      principals.clear();
      clear();
      if (debug) {
         logger.debug("logout");
      }
      return true;
   }

   private void clear() {
      user = null;
      loginSucceeded = false;
   }
}
