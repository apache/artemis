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
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import java.security.Principal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * populate an empty (no UserPrincipal) subject with UserPrincipal seeded from existing principal Useful when a third
 * party login module generated principal needs to be accepted as-is by the broker
 */
public class PrincipalConversionLoginModule implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String PRINCIPAL_CLASS_LIST = "principalClassList";
   private Subject subject;
   private String principalClazzList;
   private Principal principal;

   @Override
   public void initialize(Subject subject,
                          CallbackHandler callbackHandler,
                          Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.principalClazzList = (String) options.get(PRINCIPAL_CLASS_LIST);
   }

   @Override
   public boolean login() throws LoginException {
      logger.debug("login ok!");
      return true;
   }

   @Override
   public boolean commit() throws LoginException {

      if (subject == null || !subject.getPrincipals(UserPrincipal.class).isEmpty()) {
         return false;
      }

      if (principalClazzList != null) {
         String[] principalClasses = principalClazzList.split(",");
         for (String principalClass : principalClasses) {
            String trimmedCandidateClassName = principalClass.trim();
            for (Principal candidatePrincipal : subject.getPrincipals()) {
               logger.debug("Checking principal class name:{}, {}", candidatePrincipal.getClass().getName(), candidatePrincipal);
               if (candidatePrincipal.getClass().getName().equals(trimmedCandidateClassName)) {
                  logger.debug("converting: {}", candidatePrincipal);
                  principal = new UserPrincipal(candidatePrincipal.getName());
                  subject.getPrincipals().add(principal);
                  break;
               }
            }
         }
      }

      if (logger.isDebugEnabled()) {
         logger.debug("commit, result: {}", (principal != null));
      }
      return principal != null;
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(principal != null ? principal.getName() : null);
      clear();
      logger.debug("abort");
      return true;
   }

   private void clear() {
      principal = null;
   }

   @Override
   public boolean logout() throws LoginException {
      if (subject != null) {
         subject.getPrincipals().remove(principal);
      }
      clear();
      logger.debug("logout");
      return true;
   }

}