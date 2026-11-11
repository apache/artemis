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

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;

import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;

/**
 * JMXAuthenticator implementation to be used with ActiveMQBasicSecurityManager
 */
public class BasicAuthenticator implements JMXAuthenticator {

   ActiveMQBasicSecurityManager securityManager;

   public BasicAuthenticator(ActiveMQBasicSecurityManager securityManager) {
      this.securityManager = securityManager;
   }

   @Override
   public Subject authenticate(final Object credentials) throws SecurityException {
      Subject result;
      String[] params = null;
      if (credentials instanceof String[] strings && strings.length == 2) {
         params = strings;
      }
      result = securityManager.authenticate(params[0], params[1], null, null);
      if (result != null) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.userSuccesfullyAuthenticatedInAudit(result);
         }
         return result;
      } else {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.userFailedAuthenticationInAudit(result, null, null);
         }
         throw new SecurityException("Authentication failed");
      }
   }
}
