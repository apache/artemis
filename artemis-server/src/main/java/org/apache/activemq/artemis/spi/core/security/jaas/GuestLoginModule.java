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
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Always login the user with a default 'guest' identity.
 * <p>
 * Useful for unauthenticated communication channels being used in the same broker as authenticated ones.
 */
public class GuestLoginModule implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String GUEST_USER = "org.apache.activemq.jaas.guest.user";
   public static final String GUEST_ROLE = "org.apache.activemq.jaas.guest.role";

   private String userName = "guest";
   private String roleName = "guests";
   private Subject subject;
   private boolean debug;
   private boolean credentialsInvalidate;
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
      debug = "true".equalsIgnoreCase((String) options.get("debug"));
      credentialsInvalidate = "true".equalsIgnoreCase((String) options.get("credentialsInvalidate"));
      if (options.get(GUEST_USER) != null) {
         userName = (String) options.get(GUEST_USER);
      }
      if (options.get(GUEST_ROLE) != null) {
         roleName = (String) options.get(GUEST_ROLE);
      }
      principals.add(new UserPrincipal(userName));
      principals.add(new RolePrincipal(roleName));

      if (debug) {
         logger.debug("Initialized debug={} guestUser={} guestGroup={}", debug, userName, roleName);
      }

   }

   @Override
   public boolean login() throws LoginException {
      loginSucceeded = true;
      if (credentialsInvalidate) {
         PasswordCallback passwordCallback = new PasswordCallback("Password: ", false);
         try {
            callbackHandler.handle(new Callback[]{passwordCallback});
            if (passwordCallback.getPassword() != null) {
               if (debug) {
                  logger.debug("Guest login failing (credentialsInvalidate=true) on presence of a password");
               }
               loginSucceeded = false;
               passwordCallback.clearPassword();
            }
         } catch (IOException | UnsupportedCallbackException e) {
         }
      }
      if (debug) {
         logger.debug("Guest login {}", loginSucceeded);
      }
      return loginSucceeded;
   }

   @Override
   public boolean commit() throws LoginException {
      if (loginSucceeded) {
         subject.getPrincipals().addAll(principals);
      }

      if (debug) {
         logger.debug("commit: {}", loginSucceeded);
      }
      return loginSucceeded;
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(GUEST_USER);
      if (debug) {
         logger.debug("abort");
      }
      return true;
   }

   @Override
   public boolean logout() throws LoginException {
      subject.getPrincipals().removeAll(principals);

      if (debug) {
         logger.debug("logout");
      }
      return true;
   }
}
