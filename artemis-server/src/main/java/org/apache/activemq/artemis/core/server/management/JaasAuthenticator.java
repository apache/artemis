/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.management;

import org.apache.activemq.artemis.logs.AuditLogger;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class JaasAuthenticator implements JMXAuthenticator {

   private String realm;

   public String getRealm() {
      return realm;
   }

   public void setRealm(String realm) {
      this.realm = realm;
   }

   @Override
   public Subject authenticate(final Object credentials) throws SecurityException {

      Subject subject = new Subject();
      try {

         LoginContext loginContext = new LoginContext(realm, subject, callbacks -> {
            // pull out the jmx credentials if they exist if not, guest login module will handle it
            String[] params = null;
            if (credentials instanceof String[] strings && strings.length == 2) {
               params = strings;
            }
            for (int i = 0; i < callbacks.length; i++) {
               if (callbacks[i] instanceof NameCallback nameCallback) {
                  if (params != null) {
                     nameCallback.setName(params[0]);
                  }
               } else if (callbacks[i] instanceof PasswordCallback passwordCallback) {
                  if (params != null) {
                     passwordCallback.setPassword((params[1].toCharArray()));
                  }
               } else {
                  throw new UnsupportedCallbackException(callbacks[i]);
               }
            }
         });
         loginContext.login();
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.userSuccesfullyAuthenticatedInAudit(subject);
         }
         return subject;
      } catch (LoginException e) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.userFailedAuthenticationInAudit(subject, e.getMessage(), null);
         }
         throw new SecurityException("Authentication failed", e);
      }
   }
}
