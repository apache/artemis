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
package org.apache.activemq.artemis.core.server.federation;

import java.util.Map;
import org.apache.activemq.artemis.api.core.ActiveMQSessionCreationException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.federation.FederationConnectionConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.uri.BeanSupport;

public class FederationConnection {

   private final FederationConnectionConfiguration config;
   private final ServerLocator serverLocator;
   private final long circuitBreakerTimeout;
   private volatile ClientSessionFactory clientSessionFactory;
   private volatile boolean started;
   private volatile boolean sharedConnection;
   private boolean isPull;

   public FederationConnection(Configuration configuration, String name, FederationConnectionConfiguration config) {
      this.config = config;
      this.circuitBreakerTimeout = config.getCircuitBreakerTimeout();
      Map<String, Object> possibleLocatorParameters = null;
      if (config.getDiscoveryGroupName() != null) {
         DiscoveryGroupConfiguration discoveryGroupConfiguration = configuration.getDiscoveryGroupConfigurations().get(config.getDiscoveryGroupName());
         if (discoveryGroupConfiguration == null) {
            ActiveMQServerLogger.LOGGER.bridgeNoDiscoveryGroup(config.getDiscoveryGroupName());
            serverLocator = null;
            return;
         }

         if (config.isHA()) {
            serverLocator = ActiveMQClient.createServerLocatorWithHA(discoveryGroupConfiguration);
         } else {
            serverLocator = ActiveMQClient.createServerLocatorWithoutHA(discoveryGroupConfiguration);
         }

      } else {
         TransportConfiguration[] tcConfigs = configuration.getTransportConfigurations(config.getStaticConnectors());

         if (tcConfigs == null) {
            ActiveMQServerLogger.LOGGER.bridgeCantFindConnectors(name);
            serverLocator = null;
            return;
         }

         if (config.isHA()) {
            serverLocator = ActiveMQClient.createServerLocatorWithHA(tcConfigs);
         } else {
            serverLocator = ActiveMQClient.createServerLocatorWithoutHA(tcConfigs);
         }

         if (tcConfigs.length > 0) {
            possibleLocatorParameters = tcConfigs[0].getExtraParams();
         }
      }

      if (!config.isHA()) {
         serverLocator.setUseTopologyForLoadBalancing(false);
      }

      serverLocator.setConnectionTTL(config.getConnectionTTL());
      serverLocator.setClientFailureCheckPeriod(config.getClientFailureCheckPeriod());
      serverLocator.setReconnectAttempts(config.getReconnectAttempts());
      serverLocator.setInitialConnectAttempts(config.getInitialConnectAttempts());
      serverLocator.setRetryInterval(config.getRetryInterval());
      serverLocator.setRetryIntervalMultiplier(config.getRetryIntervalMultiplier());
      serverLocator.setMaxRetryInterval(config.getMaxRetryInterval());
      serverLocator.setCallTimeout(config.getCallTimeout());
      serverLocator.setCallFailoverTimeout(config.getCallFailoverTimeout());

      // let url parameters override
      if (possibleLocatorParameters != null) {
         try {
            BeanSupport.setData(serverLocator, possibleLocatorParameters);
         } catch (Exception ignoredAsErrorsVisibleViaBeanUtilsLogging) {
         }
         isPull = ("0".equals(possibleLocatorParameters.get("consumerWindowSize")));
      }
   }

   public synchronized void start() {
      started = true;
   }

   public synchronized void stop() {
      started = false;
      ClientSessionFactory clientSessionFactory = this.clientSessionFactory;
      if (clientSessionFactory != null) {
         clientSessionFactory.cleanup();
         clientSessionFactory.close();
         this.clientSessionFactory = null;
      }
   }

   public boolean isStarted() {
      return started;
   }

   public boolean isPull() {
      return isPull;
   }

   public boolean isSharedConnection() {
      return sharedConnection;
   }

   public void setSharedConnection(boolean sharedConnection) {
      this.sharedConnection = sharedConnection;
   }

   public final ClientSessionFactory clientSessionFactory() throws Exception {
      ClientSessionFactory clientSessionFactory = this.clientSessionFactory;
      if (started) {
         if (clientSessionFactory != null && !clientSessionFactory.isClosed()) {
            return clientSessionFactory;
         } else {
            return circuitBreakerCreateClientSessionFactory();
         }
      } else {
         throw new ActiveMQSessionCreationException();
      }
   }

   public FederationConnectionConfiguration getConfig() {
      return config;
   }

   private Exception circuitBreakerException;
   private long lastCreateClientSessionFactoryExceptionTimestamp;

   private synchronized ClientSessionFactory circuitBreakerCreateClientSessionFactory() throws Exception {
      if (circuitBreakerTimeout < 0 || circuitBreakerException == null || lastCreateClientSessionFactoryExceptionTimestamp < System.currentTimeMillis()) {
         try {
            circuitBreakerException = null;
            return createClientSessionFactory();
         } catch (Exception e) {
            circuitBreakerException = e;
            lastCreateClientSessionFactoryExceptionTimestamp = System.currentTimeMillis() + circuitBreakerTimeout;
            throw e;
         }
      } else {
         throw circuitBreakerException;
      }
   }


   private synchronized ClientSessionFactory createClientSessionFactory() throws Exception {
      ClientSessionFactory clientSessionFactory = this.clientSessionFactory;
      if (clientSessionFactory != null && !clientSessionFactory.isClosed()) {
         return clientSessionFactory;
      } else {
         clientSessionFactory = serverLocator.createSessionFactory(config.getUsername(), config.getPassword());
         this.clientSessionFactory = clientSessionFactory;
         return clientSessionFactory;
      }

   }
}
