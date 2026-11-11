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

import org.apache.activemq.artemis.core.config.JMXConnectorConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXAuthenticator;
import java.util.HashMap;
import java.util.Map;


public class ManagementConnector implements ActiveMQComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final JMXConnectorConfiguration configuration;
   private ConnectorServerFactory connectorServerFactory;
   private RmiRegistryFactory rmiRegistryFactory;
   private MBeanServerFactory mbeanServerFactory;
   private ActiveMQSecurityManager securityManager;

   public ManagementConnector(JMXConnectorConfiguration configuration, ActiveMQSecurityManager securityManager) {
      this.configuration = configuration;
      this.securityManager = securityManager;
   }

   @Override
   public boolean isStarted() {
      return rmiRegistryFactory != null;
   }

   @Override
   public void start() throws Exception {
      rmiRegistryFactory = new RmiRegistryFactory();
      rmiRegistryFactory.setPort(configuration.getConnectorPort());
      rmiRegistryFactory.setHost(configuration.getConnectorHost());
      rmiRegistryFactory.init();

      mbeanServerFactory = new MBeanServerFactory();
      mbeanServerFactory.setLocateExistingServerIfPossible(true);
      mbeanServerFactory.init();

      MBeanServer mbeanServer = mbeanServerFactory.getServer();

      JMXAuthenticator authenticator;

      if (securityManager != null && securityManager instanceof ActiveMQBasicSecurityManager manager) {
         authenticator = new BasicAuthenticator(manager);
      } else {
         JaasAuthenticator jaasAuthenticator = new JaasAuthenticator();
         jaasAuthenticator.setRealm(configuration.getJmxRealm());
         authenticator = jaasAuthenticator;
      }

      connectorServerFactory = new ConnectorServerFactory();
      connectorServerFactory.setServer(mbeanServer);
      connectorServerFactory.setServiceUrl(configuration.getServiceUrl());
      connectorServerFactory.setRmiServerHost(configuration.getConnectorHost());
      connectorServerFactory.setObjectName(new ObjectName(configuration.getObjectName()));
      Map<String, Object> environment = new HashMap<>();
      environment.put("jmx.remote.authenticator", authenticator);
      try {
         connectorServerFactory.setEnvironment(environment);
         connectorServerFactory.setAuthenticatorType(configuration.getAuthenticatorType());
         connectorServerFactory.setSecured(configuration.isSecured());
         connectorServerFactory.setKeyStorePath(configuration.getKeyStorePath());
         connectorServerFactory.setkeyStoreProvider(configuration.getKeyStoreProvider());
         connectorServerFactory.setkeyStoreType(configuration.getKeyStoreType());
         connectorServerFactory.setKeyStorePassword(configuration.getKeyStorePassword());
         connectorServerFactory.setTrustStorePath(configuration.getTrustStorePath());
         connectorServerFactory.setTrustStoreProvider(configuration.getTrustStoreProvider());
         connectorServerFactory.setTrustStoreType(configuration.getTrustStoreType());
         connectorServerFactory.setTrustStorePassword(configuration.getTrustStorePassword());
         connectorServerFactory.init();
      } catch (Exception e) {
         logger.error("Can't init JMXConnectorServer:", e);
      }
   }

   @Override
   public void stop() {
      if (connectorServerFactory != null) {
         try {
            connectorServerFactory.destroy();
         } catch (Exception e) {
            logger.warn("Error destroying ConnectorServerFactory", e);
         }
         connectorServerFactory = null;
      }
      if (mbeanServerFactory != null) {
         try {
            mbeanServerFactory.destroy();
         } catch (Exception e) {
            logger.warn("Error destroying MBeanServerFactory", e);
         }
         mbeanServerFactory = null;
      }
      if (rmiRegistryFactory != null) {
         try {
            rmiRegistryFactory.destroy();
         } catch (Exception e) {
            logger.warn("Error destroying RMIRegistryFactory", e);
         }
         rmiRegistryFactory = null;
      }
   }

   public ConnectorServerFactory getConnectorServerFactory() {
      return connectorServerFactory;
   }
}
