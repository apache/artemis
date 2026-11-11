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

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;

import org.slf4j.Logger;

/**
 * Abstract login module that uses an external authenticated principal
 */
public abstract class AbstractPrincipalLoginModule implements AuditLoginModule {
   private final Logger logger;

   private Subject subject;
   private final List<Principal> authenticatedPrincipals = new LinkedList<>();
   private CallbackHandler callbackHandler;
   private boolean loginSucceeded;
   private Principal[] principals;

   public AbstractPrincipalLoginModule(Logger logger) {
      this.logger = logger;
   }

   @Override
   public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.callbackHandler = callbackHandler;
   }

   @Override
   public boolean login() throws LoginException {
      Callback[] callbacks = new Callback[1];

      callbacks[0] = new PrincipalsCallback();
      try {
         callbackHandler.handle(callbacks);
         principals = ((PrincipalsCallback) callbacks[0]).getPeerPrincipals();
         if (principals != null) {
            authenticatedPrincipals.addAll(Arrays.asList(principals));
         }
      } catch (IOException ioe) {
         throw new LoginException(ioe.getMessage());
      } catch (UnsupportedCallbackException uce) {
         throw new LoginException(uce.getMessage() + " not available to obtain information from user");
      }
      if (!authenticatedPrincipals.isEmpty()) {
         loginSucceeded = true;
      }
      logger.debug("login {}", authenticatedPrincipals);
      return loginSucceeded;
   }

   @Override
   public boolean commit() throws LoginException {
      boolean result = loginSucceeded;
      if (result) {
         authenticatedPrincipals.add(new UserPrincipal(authenticatedPrincipals.get(0).getName()));
         subject.getPrincipals().addAll(authenticatedPrincipals);
      }

      clear();

      logger.debug("commit, result: {}", result);

      return result;
   }

   @Override
   public boolean abort() throws LoginException {
      if (principals != null) {
         for (Principal principal : authenticatedPrincipals) {
            registerFailureForAudit(principal.getName());
         }
      }
      clear();

      logger.debug("abort");

      return true;
   }

   private void clear() {
      principals = null;
      loginSucceeded = false;
   }

   @Override
   public boolean logout() throws LoginException {
      subject.getPrincipals().removeAll(authenticatedPrincipals);
      authenticatedPrincipals.clear();
      clear();

      logger.debug("logout");

      return true;
   }
}
