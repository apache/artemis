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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.client.KubernetesClient;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.client.KubernetesClientImpl;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model.TokenReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesLoginModule extends PropertiesLoader implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(KubernetesLoginModule.class);

   public static final String K8S_ROLE_FILE_PROP_NAME = "org.apache.activemq.jaas.kubernetes.role";

   private CallbackHandler handler;
   private Subject subject;
   private TokenReview tokenReview = new TokenReview();
   private boolean ignoreTokenReviewRoles = false;
   private Map<String, Set<String>> roles;
   private final Set<Principal> principals = new LinkedHashSet<>();
   private final KubernetesClient client;

   public KubernetesLoginModule(KubernetesClient client) {
      this.client = client;
   }

   public KubernetesLoginModule() {
      this(new KubernetesClientImpl());
   }

   @Override
   public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
         Map<String, ?> options) {
      this.handler = callbackHandler;
      this.subject = subject;

      debug = booleanOption("debug", options);
      if (debug) {
         logger.debug("Initialized debug");
      }
      // role mapping file is optional
      if (options.containsKey(K8S_ROLE_FILE_PROP_NAME)) {
         roles = load(K8S_ROLE_FILE_PROP_NAME, null, options).invertedPropertiesValuesMap();
         if (debug) {
            logger.debug("loaded roles: {}", roles);
         }
      } else {
         roles = Map.of();
      }

      ignoreTokenReviewRoles = booleanOption("ignoreTokenReviewRoles", options);
   }

   @Override
   public boolean login() throws LoginException {
      Callback[] callbacks = new Callback[1];
      callbacks[0] = new PasswordCallback("Password", false);

      try {
         handler.handle(callbacks);
      } catch (IOException | UnsupportedCallbackException e) {
         throw (LoginException) new LoginException().initCause(e);
      }

      char[] token = ((PasswordCallback) callbacks[0]).getPassword();

      if (token.length == 0) {
         throw new FailedLoginException("Bearer token is empty");
      }

      tokenReview = client.getTokenReview(new String(token));

      if (debug) {
         logger.debug("login {}", tokenReview);
      }
      return tokenReview.isAuthenticated();
   }

   @Override
   public boolean commit() throws LoginException {
      boolean result = false;
      result = tokenReview.isAuthenticated();

      Set<UserPrincipal> authenticatedUsers = subject.getPrincipals(UserPrincipal.class);
      if (result) {
         UserPrincipal userPrincipal = new ServiceAccountPrincipal(tokenReview.getUsername());
         principals.add(userPrincipal);
         authenticatedUsers.add(userPrincipal);
         if (!ignoreTokenReviewRoles) {
            for (String role : tokenReview.getUser().getGroups()) {
               principals.add(new RolePrincipal(role));
            }
         }
         Set<String> matchedRoles = roles.get(userPrincipal.getName());
         if (matchedRoles != null) {
            for (String entry : matchedRoles) {
               principals.add(new RolePrincipal(entry));
            }
         }
      }

      subject.getPrincipals().addAll(principals);

      clear();

      if (debug) {
         logger.debug("commit, result: {}, principals: {}", result, principals);
      }
      return result;
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(tokenReview.getUsername());
      clear();

      if (debug) {
         logger.debug("abort");
      }
      return true;
   }

   @Override
   public void registerFailureForAudit(String name) {
      Subject subject = new Subject();
      subject.getPrincipals().add(new ServiceAccountPrincipal(name));
      AuditLogger.setCurrentCaller(subject);
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
      tokenReview = new TokenReview();
   }

}
