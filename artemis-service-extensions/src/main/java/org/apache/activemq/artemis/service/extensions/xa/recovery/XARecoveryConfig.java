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
package org.apache.activemq.artemis.service.extensions.xa.recovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManagerFactory;

/**
 * This represents the configuration of a single connection factory.
 *
 * A wrapper around info needed for the xa recovery resource
 */
public class XARecoveryConfig {

   public static final String JNDI_NAME_PROPERTY_KEY = "JNDI_NAME";

   private final boolean ha;
   private final TransportConfiguration[] transportConfiguration;
   private final DiscoveryGroupConfiguration discoveryConfiguration;
   private final String username;
   private final String password;
   private final Map<String, String> properties;
   private final ClientProtocolManagerFactory clientProtocolManager;
   private ServerLocatorConfig locatorConfig;

   public static XARecoveryConfig newConfig(ActiveMQConnectionFactory factory,
                                            String userName,
                                            String password,
                                            Map<String, String> properties) {
      return new XARecoveryConfig(factory.getServerLocator(), userName, password, properties);
   }

   public XARecoveryConfig(final boolean ha,
                           final TransportConfiguration[] transportConfiguration,
                           final String username,
                           final String password,
                           final Map<String, String> properties,
                           final ClientProtocolManagerFactory clientProtocolManager) {
      this(ha, transportConfiguration, username, password, properties, clientProtocolManager, null);
   }

   public XARecoveryConfig(final boolean ha,
                           final TransportConfiguration[] transportConfiguration,
                           final String username,
                           final String password,
                           final Map<String, String> properties,
                           final ClientProtocolManagerFactory clientProtocolManager,
                           ServerLocatorConfig locatorConfig) {
      TransportConfiguration[] newTransportConfiguration = new TransportConfiguration[transportConfiguration.length];
      for (int i = 0; i < transportConfiguration.length; i++) {
         if (clientProtocolManager != null) {
            newTransportConfiguration[i] = clientProtocolManager.adaptTransportConfiguration(transportConfiguration[i].newTransportConfig(""));
         } else {
            newTransportConfiguration[i] = transportConfiguration[i].newTransportConfig("");
         }
      }

      this.transportConfiguration = newTransportConfiguration;
      this.discoveryConfiguration = null;
      this.username = username;
      this.password = password;
      this.ha = ha;
      this.properties = Objects.requireNonNullElseGet(properties, () -> new HashMap<>());
      this.clientProtocolManager = clientProtocolManager;
      this.locatorConfig = locatorConfig;
   }

   public XARecoveryConfig(final boolean ha,
                           final TransportConfiguration[] transportConfiguration,
                           final String username,
                           final String password,
                           final Map<String, String> properties) {
      this(ha, transportConfiguration, username, password, properties, null);
   }

   public XARecoveryConfig(final boolean ha,
                           final DiscoveryGroupConfiguration discoveryConfiguration,
                           final String username,
                           final String password,
                           final Map<String, String> properties,
                           final ClientProtocolManagerFactory clientProtocolManager) {
      this.discoveryConfiguration = discoveryConfiguration;
      this.transportConfiguration = null;
      this.username = username;
      this.password = password;
      this.ha = ha;
      this.clientProtocolManager = clientProtocolManager;
      this.properties = properties == null ? Collections.unmodifiableMap(new HashMap<>()) : Collections.unmodifiableMap(properties);
   }

   public XARecoveryConfig(final boolean ha,
                           final DiscoveryGroupConfiguration discoveryConfiguration,
                           final String username,
                           final String password,
                           final Map<String, String> properties) {
      this(ha, discoveryConfiguration, username, password, properties, null);
   }

   private XARecoveryConfig(ServerLocator serverLocator,
                            String username,
                            String password,
                            Map<String, String> properties) {
      ClientProtocolManagerFactory clientProtocolManager = serverLocator.getProtocolManagerFactory();
      if (serverLocator.getDiscoveryGroupConfiguration() != null) {
         this.discoveryConfiguration = serverLocator.getDiscoveryGroupConfiguration();
         this.transportConfiguration = null;
      } else {
         TransportConfiguration[] transportConfiguration = serverLocator.getStaticTransportConfigurations();
         TransportConfiguration[] newTransportConfiguration = new TransportConfiguration[transportConfiguration.length];
         for (int i = 0; i < transportConfiguration.length; i++) {
            if (clientProtocolManager != null) {
               newTransportConfiguration[i] = clientProtocolManager.adaptTransportConfiguration(transportConfiguration[i].newTransportConfig(""));
            } else {
               newTransportConfiguration[i] = transportConfiguration[i].newTransportConfig("");
            }
         }

         this.transportConfiguration = newTransportConfiguration;
         this.discoveryConfiguration = null;
      }
      this.username = username;
      this.password = password;
      this.ha = serverLocator.isHA();
      this.properties = properties == null ? Collections.unmodifiableMap(new HashMap<>()) : Collections.unmodifiableMap(properties);
      this.clientProtocolManager = clientProtocolManager;
      this.locatorConfig = serverLocator.getLocatorConfig();
   }

   public boolean isHA() {
      return ha;
   }

   public DiscoveryGroupConfiguration getDiscoveryConfiguration() {
      return discoveryConfiguration;
   }

   public TransportConfiguration[] getTransportConfig() {
      return transportConfiguration;
   }

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }

   public Map<String, String> getProperties() {
      return properties;
   }

   public ClientProtocolManagerFactory getClientProtocolManager() {
      return clientProtocolManager;
   }

   /**
    * Create a serverLocator using the configuration
    *
    * @return locator
    */
   public ServerLocator createServerLocator() {
      ServerLocator serverLocator;
      if (getDiscoveryConfiguration() != null) {
         serverLocator = ActiveMQClient.createServerLocator(isHA(), getDiscoveryConfiguration()).setProtocolManagerFactory(clientProtocolManager);
      } else {
         serverLocator = ActiveMQClient.createServerLocator(isHA(), getTransportConfig()).setProtocolManagerFactory(clientProtocolManager);
      }

      if (this.locatorConfig != null) {
         serverLocator.setLocatorConfig(new ServerLocatorConfig(this.locatorConfig));
      }

      return serverLocator;
   }

   public ServerLocatorConfig getLocatorConfig() {
      return locatorConfig;
   }

   @Override
   public int hashCode() {
      return Objects.hash(discoveryConfiguration, Arrays.hashCode(transportConfiguration));
   }

   /*
    * We don't use username and password on purpose.
    * Just having the connector is enough, as we don't want to duplicate resources just because of usernames
    */
   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof XARecoveryConfig other)) {
         return false;
      }

      return Objects.equals(discoveryConfiguration, other.discoveryConfiguration) &&
             Arrays.equals(transportConfiguration, other.transportConfiguration);
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder();

      builder.append("XARecoveryConfig [transportConfiguration=" + Arrays.toString(transportConfiguration));
      builder.append(", discoveryConfiguration=" + discoveryConfiguration);
      builder.append(", username=" + username);
      builder.append(", password=****");

      for (Map.Entry<String, String> entry : properties.entrySet()) {
         builder.append(", " + entry.getKey() + "=" + entry.getValue());
      }
      builder.append("]");

      return builder.toString();
   }
}
