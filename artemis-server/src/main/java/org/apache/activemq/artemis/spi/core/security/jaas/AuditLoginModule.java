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

import org.apache.activemq.artemis.logs.AuditLogger;

import javax.security.auth.Subject;
import javax.security.auth.spi.LoginModule;

/**
 * This is only to support auditlogging
 */
public interface AuditLoginModule extends LoginModule {

   /*
    * We need this because if authentication fails at the web layer then there is no way to access the unauthenticated
    * subject as it is removed and the session destroyed and never gets as far as the broker
    */
   default void registerFailureForAudit(String name) {
      Subject subject = new Subject();
      subject.getPrincipals().add(new UserPrincipal(name));
      AuditLogger.setCurrentCaller(subject);
   }
}
