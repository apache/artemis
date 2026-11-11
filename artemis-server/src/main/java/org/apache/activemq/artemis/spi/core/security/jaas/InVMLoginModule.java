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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class InVMLoginModule implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String CONFIG_PROP_NAME = "org.apache.activemq.jaas.invm.config";

   private SecurityConfiguration configuration;
   private Subject subject;
   private String user;
   private final Set<Principal> principals = new LinkedHashSet<>();
   private CallbackHandler callbackHandler;
   private boolean loginSucceeded;

   @Override
   public void initialize(Subject subject,
                          CallbackHandler callbackHandler,
                          Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.callbackHandler = callbackHandler;
      this.configuration = (SecurityConfiguration) options.get(CONFIG_PROP_NAME);
   }

   @Override
   public boolean login() throws LoginException {
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
         if (configuration.getDefaultUser() == null) {
            throw new FailedLoginException("Both username and defaultUser are null");
         } else {
            user = configuration.getDefaultUser();
         }
      } else {
         String password = configuration.getUser(user) == null ? null : configuration.getUser(user).getPassword();

         if (password == null) {
            throw new FailedLoginException("User does not exist");
         }
         if (!password.equals(new String(tmpPassword))) {
            throw new FailedLoginException("Password does not match");
         }
      }
      loginSucceeded = true;

      logger.debug("login {}", user);

      return loginSucceeded;
   }

   @Override
   public boolean commit() throws LoginException {
      boolean result = loginSucceeded;
      if (result) {
         principals.add(new UserPrincipal(user));

         List<String> roles = configuration.getRole(user);

         if (roles != null) {
            for (String role : roles) {
               principals.add(new RolePrincipal(role));
            }
         }

         subject.getPrincipals().addAll(principals);
      }

      // will whack loginSucceeded
      clear();

      logger.debug("commit, result: {}", result);

      return result;
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(user);
      clear();

      logger.debug("abort");

      return true;
   }

   private void clear() {
      user = null;
      loginSucceeded = false;
   }

   @Override
   public boolean logout() throws LoginException {
      subject.getPrincipals().removeAll(principals);
      principals.clear();
      clear();

      logger.debug("logout");

      return true;
   }
}
