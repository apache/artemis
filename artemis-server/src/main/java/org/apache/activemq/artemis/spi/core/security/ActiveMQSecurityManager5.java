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
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.jaas.NoCacheLoginException;

/**
 * This is an evolution of {@link ActiveMQSecurityManager4} that integrates with the new Subject caching functionality.
 */
public interface ActiveMQSecurityManager5 extends ActiveMQSecurityManager {

   /**
    * is this a valid user.
    * <p>
    * This method is called instead of {@link ActiveMQSecurityManager#validateUser(String, String)}.
    *
    * @param user               the user
    * @param password           the user's password
    * @param remotingConnection the user's connection which contains any corresponding SSL certs
    * @param securityDomain     the name of the JAAS security domain to use (can be null)
    * @return the Subject of the authenticated user, else null
    */
   Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) throws NoCacheLoginException;

   /**
    * Determine whether the given user has the correct role for the given check type.
    * <p>
    * This method is called instead of
    * {@link ActiveMQSecurityManager#validateUserAndRole(String, String, Set, CheckType)}.
    *
    * @param subject   the Subject to authorize
    * @param roles     the roles configured in the security-settings
    * @param checkType which permission to validate
    * @param address   the address (or FQQN) to grant access to
    * @return {@code true} if the user is authorized, else false
    */
   boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address);

}
