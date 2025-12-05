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
package org.apache.activemq.artemis.cli.factory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.cli.ConfigurationException;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.dto.BrokerDTO;
import org.apache.activemq.artemis.dto.JaasSecurityDTO;
import org.apache.activemq.artemis.dto.PropertyDTO;
import org.apache.activemq.artemis.dto.SecurityManagerDTO;
import org.apache.activemq.artemis.dto.ServerDTO;
import org.apache.activemq.artemis.integration.Broker;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.FactoryFinder;

public class BrokerFactory {

   private static final String SECURITY_JAAS_DOMAIN_PROP_NAME = "domain";
   private static final String SECURITY_JAAS_CERTIFICATE_DOMAIN_PROP_NAME = "certificateDomain";
   private static final String SECURITY_MANAGER_CLASS_NAME_PROP_NAME = "className";
   private static final String SECURITY_MANAGER_PROPERTIES_PREFIX = "properties.";

   private static BrokerDTO createBrokerConfiguration(URI configURI,
                                                      String artemisHome,
                                                      String artemisInstance,
                                                      URI artemisURIInstance) throws Exception {
      if (configURI.getScheme() == null) {
         throw new ConfigurationException("Invalid configuration URI, no scheme specified: " + configURI);
      }

      BrokerFactoryHandler factory = null;
      try {
         FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/activemq/artemis/broker/");
         factory = (BrokerFactoryHandler) finder.newInstance(configURI.getScheme());
      } catch (IOException ioe) {
         throw new ConfigurationException("Invalid configuration URI, can't find configuration scheme: " + configURI.getScheme());
      }
      BrokerDTO broker = factory.createBroker(configURI, artemisHome, artemisInstance, artemisURIInstance);

      populateSecurityWithSystemProperties(broker);

      return broker;
   }

   private static void populateSecurityWithSystemProperties(BrokerDTO broker) {
      Properties systemProperties = System.getProperties();
      String systemSecurityJaasPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemSecurityJaasPropertyPrefix();
      String systemSecurityManagerPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemSecurityManagerPropertyPrefix();

      if (systemProperties.containsKey(systemSecurityJaasPropertyPrefix + SECURITY_JAAS_DOMAIN_PROP_NAME)) {
         broker.security = broker.security instanceof JaasSecurityDTO ?
            (JaasSecurityDTO) broker.security : new JaasSecurityDTO();
      } else if (systemProperties.containsKey(systemSecurityManagerPropertyPrefix + SECURITY_MANAGER_CLASS_NAME_PROP_NAME)) {
         broker.security = broker.security instanceof SecurityManagerDTO ?
            (SecurityManagerDTO) broker.security : new SecurityManagerDTO();
      }

      if (broker.security instanceof JaasSecurityDTO security) {
         security.domain = Optional.ofNullable((String)systemProperties.get(
            systemSecurityJaasPropertyPrefix + SECURITY_JAAS_DOMAIN_PROP_NAME)).orElse(security.domain);
         security.certificateDomain = Optional.ofNullable((String)systemProperties.get(
            systemSecurityJaasPropertyPrefix + SECURITY_JAAS_CERTIFICATE_DOMAIN_PROP_NAME)).orElse(security.certificateDomain);
      } else if (broker.security instanceof SecurityManagerDTO security) {
         security.className = Optional.ofNullable((String)systemProperties.get(
            systemSecurityManagerPropertyPrefix + SECURITY_MANAGER_CLASS_NAME_PROP_NAME)).orElse(security.className);
         security.properties = Objects.requireNonNullElse(security.properties, new ArrayList<>());
         systemProperties.forEach((key, value) -> {
            if (((String)key).startsWith(systemSecurityManagerPropertyPrefix + SECURITY_MANAGER_PROPERTIES_PREFIX)) {
               security.properties.add(new PropertyDTO(((String)key).substring(
                  systemSecurityManagerPropertyPrefix.length() + SECURITY_MANAGER_PROPERTIES_PREFIX.length()), (String)value));
            }
         });
      }
   }

   public static BrokerDTO createBrokerConfiguration(String configuration,
                                                     String artemisHome,
                                                     String artemisInstance,
                                                     URI artemisURIInstance) throws Exception {
      return createBrokerConfiguration(new URI(configuration), artemisHome, artemisInstance, artemisURIInstance);
   }

   public static Broker createServer(ServerDTO brokerDTO, ActiveMQSecurityManager security, ActivateCallback activateCallback) throws Exception {
      if (brokerDTO.configuration != null) {
         BrokerHandler handler;
         URI configURI = brokerDTO.getConfigurationURI();

         try {
            FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/activemq/artemis/broker/server/");
            handler = (BrokerHandler) finder.newInstance(configURI.getScheme());
         } catch (IOException ioe) {
            throw new ConfigurationException("Invalid configuration URI, can't find configuration scheme: " + configURI.getScheme());
         }

         return handler.createServer(brokerDTO, security, activateCallback);
      }
      return null;
   }

}
