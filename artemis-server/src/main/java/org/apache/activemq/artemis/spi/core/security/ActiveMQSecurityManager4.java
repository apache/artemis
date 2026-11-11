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

import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * This is an evolution of {@link ActiveMQSecurityManager3} that adds the ability to specify the JAAS domain per call.
 */
public interface ActiveMQSecurityManager4 extends ActiveMQSecurityManager {

   /**
    * is this a valid user.
    * <p>
    * This method is called instead of {@link ActiveMQSecurityManager#validateUser(String, String)}.
    *
    * @param user           the user
    * @param password       the users password
    * @param securityDomain the name of the JAAS security domain to use (can be null)
    * @return the name of the validated user or null if the user isn't validated
    */
   String validateUser(String user, String password, RemotingConnection remotingConnection, String securityDomain);

   /**
    * Determine whether the given user is valid and whether they have the correct role for the given destination
    * address.
    * <p>
    * This method is called instead of
    * {@link ActiveMQSecurityManager#validateUserAndRole(String, String, Set, CheckType)}.
    *
    * @param user               the user
    * @param password           the user's password
    * @param roles              the user's roles
    * @param checkType          which permission to validate
    * @param address            the address for which to perform authorization
    * @param remotingConnection the user's connection
    * @param securityDomain     the name of the JAAS security domain to use (can be null)
    * @return the name of the validated user or null if the user isn't validated
    */
   String validateUserAndRole(String user,
                              String password,
                              Set<Role> roles,
                              CheckType checkType,
                              String address,
                              RemotingConnection remotingConnection,
                              String securityDomain);
}
