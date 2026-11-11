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
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.Set;

import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.jaas.JaasCallbackHandler;
import org.apache.activemq.artemis.spi.core.security.jaas.NoCacheLoginException;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.SecurityManagerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.utils.CertificateUtil.getCertsFromConnection;

/**
 * This implementation delegates to the JAAS security interfaces.
 * <p>
 * The {@link Subject} returned by the login context is expecting to have a set of {@link RolePrincipal} for each role
 * of the user.
 */
public class ActiveMQJAASSecurityManager implements ActiveMQSecurityManager5 {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private String configurationName;
   private String certificateConfigurationName;
   private SecurityConfiguration configuration;
   private SecurityConfiguration certificateConfiguration;
   private Class<? extends Principal> rolePrincipalClass = RolePrincipal.class;
   private Class<? extends Principal> userPrincipalClass = UserPrincipal.class;

   public ActiveMQJAASSecurityManager() {
   }

   public ActiveMQJAASSecurityManager(String configurationName) {
      this.configurationName = configurationName;
   }

   public ActiveMQJAASSecurityManager(String configurationName, String certificateConfigurationName) {
      this.configurationName = configurationName;
      this.certificateConfigurationName = certificateConfigurationName;
   }

   public ActiveMQJAASSecurityManager(String configurationName, SecurityConfiguration configuration) {
      this.configurationName = configurationName;
      this.configuration = configuration;
   }

   public ActiveMQJAASSecurityManager(String configurationName,
                                      String certificateConfigurationName,
                                      SecurityConfiguration configuration,
                                      SecurityConfiguration certificateConfiguration) {
      this.configurationName = configurationName;
      this.configuration = configuration;
      this.certificateConfigurationName = certificateConfigurationName;
      this.certificateConfiguration = certificateConfiguration;
   }

   @Override
   public String getDomain() {
      return configurationName;
   }

   @Override
   public boolean validateUser(String user, String password) {
      throw new UnsupportedOperationException("Invoke validateUser(String, String, RemotingConnection, String) instead");
   }

   @Override
   public Subject authenticate(final String user, final String password, RemotingConnection remotingConnection, final String securityDomain) throws NoCacheLoginException {
      try {
         return getAuthenticatedSubject(user, password, remotingConnection, securityDomain);
      } catch (LoginException e) {
         logger.debug("Couldn't validate user", e);
         if (e instanceof NoCacheLoginException noCacheLoginException) {
            throw noCacheLoginException;
         }
         return null;
      }
   }

   @Override
   public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
      throw new UnsupportedOperationException("Invoke validateUserAndRole(String, String, Set<Role>, CheckType, String, RemotingConnection, String) instead");
   }

   @Override
   public boolean authorize(final Subject subject,
                            final Set<Role> roles,
                            final CheckType checkType,
                            final String address) {
      boolean authorized = SecurityManagerUtil.authorize(subject, roles, checkType, rolePrincipalClass);

      return authorized;
   }

   @Override
   public String getUserFromSubject(Subject subject) {
      return SecurityManagerUtil.getUserFromSubject(subject, userPrincipalClass);
   }

   private Subject getAuthenticatedSubject(final String user,
                                           final String password,
                                           final RemotingConnection remotingConnection,
                                           final String securityDomain) throws LoginException {
      LoginContext lc;
      ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
      ClassLoader thisLoader = this.getClass().getClassLoader();
      try {
         if (thisLoader != currentLoader) {
            Thread.currentThread().setContextClassLoader(thisLoader);
         }
         if (securityDomain != null) {
            lc = new LoginContext(securityDomain, null, new JaasCallbackHandler(user, password, remotingConnection), null);
         } else if (certificateConfigurationName != null && !certificateConfigurationName.isEmpty() && getCertsFromConnection(remotingConnection) != null) {
            lc = new LoginContext(certificateConfigurationName, null, new JaasCallbackHandler(user, password, remotingConnection), certificateConfiguration);
         } else {
            lc = new LoginContext(configurationName, null, new JaasCallbackHandler(user, password, remotingConnection), configuration);
         }
         lc.login();
         return lc.getSubject();
      } finally {
         if (thisLoader != currentLoader) {
            Thread.currentThread().setContextClassLoader(currentLoader);
         }
      }
   }

   public void setConfigurationName(final String configurationName) {
      this.configurationName = configurationName;
   }

   public void setConfiguration(SecurityConfiguration configuration) {
      this.configuration = configuration;
   }

   public void setCertificateConfigurationName(final String certificateConfigurationName) {
      this.certificateConfigurationName = certificateConfigurationName;
   }

   public void setCertificateConfiguration(SecurityConfiguration certificateConfiguration) {
      this.certificateConfiguration = certificateConfiguration;
   }

   public SecurityConfiguration getConfiguration() {
      if (configuration == null) {
         configuration = new SecurityConfiguration();
      }

      return configuration;
   }

   public SecurityConfiguration getCertificateConfiguration() {
      if (certificateConfiguration == null) {
         certificateConfiguration = new SecurityConfiguration();
      }

      return certificateConfiguration;
   }

   public String getRolePrincipalClass() {
      return rolePrincipalClass.getName();
   }

   @SuppressWarnings("unchecked")
   public void setRolePrincipalClass(String principalClass) throws ClassNotFoundException {
      this.rolePrincipalClass = (Class<? extends Principal>) Class.forName(principalClass);
   }

   public String getUserPrincipalClass() {
      return userPrincipalClass.getName();
   }

   @SuppressWarnings("unchecked")
   public void setUserPrincipalClass(String principalClass) throws ClassNotFoundException {
      this.userPrincipalClass = (Class<? extends Principal>) Class.forName(principalClass);
   }
}
