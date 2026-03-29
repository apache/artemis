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
package org.apache.activemq.artemis.cli.factory.jmx;


import org.apache.activemq.artemis.cli.ConfigurationException;
import org.apache.activemq.artemis.core.config.JMXConnectorConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.management.ManagementContext;
import org.apache.activemq.artemis.dto.AccessDTO;
import org.apache.activemq.artemis.dto.AllowListDTO;
import org.apache.activemq.artemis.dto.AuthorisationDTO;
import org.apache.activemq.artemis.dto.EntryDTO;
import org.apache.activemq.artemis.dto.JMXConnectorDTO;
import org.apache.activemq.artemis.dto.ManagementContextDTO;
import org.apache.activemq.artemis.dto.MatchDTO;
import org.apache.activemq.artemis.core.server.management.JMXAccessControlList;
import org.apache.activemq.artemis.dto.WhiteListDTO;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

import java.net.URI;
import java.util.List;
import java.util.ServiceLoader;

public class ManagementFactory {

   private static ManagementContextDTO createJmxAclConfiguration(URI configURI,
                                                                 String artemisHome,
                                                                 String artemisInstance,
                                                                 URI artemisURIInstance) throws Exception {
      if (configURI.getScheme() == null) {
         throw new ConfigurationException("Invalid configuration URI, no scheme specified: " + configURI);
      }

      ServiceLoader<JmxAclHandler> loader = ServiceLoader.load(JmxAclHandler.class, ManagementFactory.class.getClassLoader());
      JmxAclHandler factory = null;
      for (JmxAclHandler handler : loader) {
         if (handler.getName().equals(configURI.getScheme())) {
            factory = handler;
            break;
         }
      }
      if (factory == null) {
         throw new ConfigurationException("Invalid configuration URI, can't find configuration scheme: " + configURI.getScheme());
      }
      return factory.createJmxAcl(configURI, artemisHome, artemisInstance, artemisURIInstance);
   }

   public static ManagementContextDTO createJmxAclConfiguration(String configuration,
                                                                String artemisHome,
                                                                String artemisInstance,
                                                                URI artemisURIInstance) throws Exception {
      return createJmxAclConfiguration(new URI(configuration), artemisHome, artemisInstance, artemisURIInstance);
   }

   public static ManagementContext create(ManagementContextDTO config, ActiveMQSecurityManager securityManager) throws Exception {
      ManagementContext context = new ManagementContext();

      if (config.getAuthorisation() != null) {
         AuthorisationDTO authorisation = config.getAuthorisation();
         JMXAccessControlList accessControlList = new JMXAccessControlList();
         //deprecated but here for backward compatibility
         WhiteListDTO whiteList = authorisation.getWhiteList();
         if (whiteList != null) {
            ActiveMQServerLogger.LOGGER.useAllowList();
            for (EntryDTO entry : whiteList.getEntries()) {
               accessControlList.addToAllowList(entry.domain, entry.key);
            }
         }

         AllowListDTO allowList = authorisation.getAllowList();
         if (allowList != null) {
            if (whiteList != null) {
               ActiveMQServerLogger.LOGGER.useOnlyAllowList();
            }
            for (EntryDTO entry : allowList.getEntries()) {
               accessControlList.addToAllowList(entry.domain, entry.key);
            }
         }

         List<AccessDTO> accessList = authorisation.getDefaultAccess().getAccess();
         for (AccessDTO access : accessList) {
            String[] split = access.roles.split(",");
            accessControlList.addToDefaultAccess(access.method, split);
         }

         List<MatchDTO> matches = authorisation.getRoleAccess().getMatch();
         for (MatchDTO match : matches) {
            List<AccessDTO> accesses = match.getAccess();
            for (AccessDTO access : accesses) {
               String[] split = access.roles.split(",");
               accessControlList.addToRoleAccess(match.getDomain(), match.getKey(), access.method, split);
            }
         }
         context.setAccessControlList(accessControlList);
      }

      if (config.getJmxConnector() != null) {
         JMXConnectorDTO jmxConnector = config.getJmxConnector();
         JMXConnectorConfiguration jmxConnectorConfiguration = new JMXConnectorConfiguration();

         jmxConnectorConfiguration.setConnectorPort(jmxConnector.getConnectorPort());
         if (jmxConnector.getConnectorHost() != null) {
            jmxConnectorConfiguration.setConnectorHost(jmxConnector.getConnectorHost());
         }
         if (jmxConnector.getRmiRegistryPort() != null) {
            jmxConnectorConfiguration.setRmiRegistryPort(jmxConnector.getRmiRegistryPort());
         }
         if (jmxConnector.getJmxRealm() != null) {
            jmxConnectorConfiguration.setJmxRealm(jmxConnector.getJmxRealm());
         }
         if (jmxConnector.getAuthenticatorType() != null) {
            jmxConnectorConfiguration.setAuthenticatorType(jmxConnector.getAuthenticatorType());
         }
         if (jmxConnector.getKeyStorePath() != null) {
            jmxConnectorConfiguration.setKeyStorePath(jmxConnector.getKeyStorePath());
         }
         if (jmxConnector.getKeyStoreProvider() != null) {
            jmxConnectorConfiguration.setKeyStoreProvider(jmxConnector.getKeyStoreProvider());
         }
         if (jmxConnector.getKeyStoreType() != null) {
            jmxConnectorConfiguration.setKeyStoreType(jmxConnector.getKeyStoreType());
         }
         if (jmxConnector.getKeyStorePassword() != null) {
            jmxConnectorConfiguration.setKeyStorePassword(jmxConnector.getKeyStorePassword());
         }
         if (jmxConnector.getTrustStorePath() != null) {
            jmxConnectorConfiguration.setTrustStorePath(jmxConnector.getTrustStorePath());
         }
         if (jmxConnector.getTrustStoreProvider() != null) {
            jmxConnectorConfiguration.setTrustStoreProvider(jmxConnector.getTrustStoreProvider());
         }
         if (jmxConnector.getTrustStoreType() != null) {
            jmxConnectorConfiguration.setTrustStoreType(jmxConnector.getTrustStoreType());
         }
         if (jmxConnector.getTrustStorePassword() != null) {
            jmxConnectorConfiguration.setTrustStorePassword(jmxConnector.getTrustStorePassword());
         }
         if (jmxConnector.getObjectName() != null) {
            jmxConnectorConfiguration.setObjectName(jmxConnector.getObjectName());
         }
         if (jmxConnector.isSecured() != null) {
            jmxConnectorConfiguration.setSecured(jmxConnector.isSecured());
         }
         context.setJmxConnectorConfiguration(jmxConnectorConfiguration);
         context.setSecurityManager(securityManager);
      }

      context.init();

      return context;
   }
}
