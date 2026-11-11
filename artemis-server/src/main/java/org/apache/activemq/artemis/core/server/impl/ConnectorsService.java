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
package org.apache.activemq.artemis.core.server.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ConnectorService;
import org.apache.activemq.artemis.core.server.ConnectorServiceFactory;
import org.apache.activemq.artemis.core.server.ServiceRegistry;
import org.apache.activemq.artemis.utils.ConfigurationHelper;

/**
 * ConnectorsService will pool some resource for updates, e.g. Twitter, then the changes are picked and converted into a
 * ServerMessage for a given destination (queue).
 * <p>
 * It may also listen to a queue, and forward them (e.g. messages arriving at the queue are picked and tweeted to some
 * Twitter account).
 */
public final class ConnectorsService implements ActiveMQComponent {

   private final StorageManager storageManager;

   private final PostOffice postOffice;

   private final ScheduledExecutorService scheduledPool;

   private boolean isStarted = false;

   private final Configuration configuration;

   private final Map<String, ConnectorService> connectors = new HashMap<>();

   private final ServiceRegistry serviceRegistry;

   public ConnectorsService(final Configuration configuration,
                            final StorageManager storageManager,
                            final ScheduledExecutorService scheduledPool,
                            final PostOffice postOffice,
                            final ServiceRegistry serviceRegistry) {
      this.configuration = configuration;
      this.storageManager = storageManager;
      this.scheduledPool = scheduledPool;
      this.postOffice = postOffice;
      this.serviceRegistry = serviceRegistry;
   }

   @Override
   public synchronized void start() throws Exception {
      Collection<Pair<ConnectorServiceFactory, ConnectorServiceConfiguration>> connectorServiceFactories = serviceRegistry.getConnectorServices(configuration.getConnectorServiceConfigurations());

      for (Pair<ConnectorServiceFactory, ConnectorServiceConfiguration> pair : connectorServiceFactories) {
         try {
            createService(pair.getB(), pair.getA());
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorStartingConnectorService(pair.getB().getConnectorName(), e);
         }
      }

      isStarted = true;
   }

   public synchronized void createService(ConnectorServiceConfiguration info, ConnectorServiceFactory factory) throws Exception {
      if (connectors.containsKey(info.getConnectorName())) {
         throw ActiveMQExceptionType.GENERIC_EXCEPTION.createException("Connector service " + info.getConnectorName() + " already created");
      }

      if (info.getParams() != null) {
         Set<String> invalid = ConfigurationHelper.checkKeys(factory.getAllowableProperties(), info.getParams().keySet());
         if (!invalid.isEmpty()) {
            throw ActiveMQExceptionType.GENERIC_EXCEPTION.createException("Invalid connector keys for connector service " + info.getConnectorName() + ": " + ConfigurationHelper.stringSetToCommaListString(invalid));
         }
      }

      Set<String> invalid = ConfigurationHelper.checkKeysExist(factory.getRequiredProperties(), info.getParams().keySet());
      if (!invalid.isEmpty()) {
         throw ActiveMQExceptionType.GENERIC_EXCEPTION.createException("Missing connector keys for connector service " + info.getConnectorName() + ": " + ConfigurationHelper.stringSetToCommaListString(invalid));
      }
      ConnectorService connectorService = factory.createConnectorService(info.getConnectorName(), info.getParams(), storageManager, postOffice, scheduledPool);
      connectorService.start();

      connectors.put(info.getConnectorName(), connectorService);
   }

   public synchronized void destroyService(String name) throws Exception {
      if (!connectors.containsKey(name)) {
         throw ActiveMQExceptionType.GENERIC_EXCEPTION.createException("Connector service " + name + " does not exist");
      }
      ConnectorService connectorService = connectors.get(name);
      connectorService.stop();
      connectors.remove(name);
   }

   @Override
   public synchronized void stop() throws Exception {
      if (!isStarted) {
         return;
      }
      for (Map.Entry<String, ConnectorService> connector : connectors.entrySet()) {
         try {
            connector.getValue().stop();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorStoppingConnectorService(connector.getKey(), e);
         }
      }
      connectors.clear();
      isStarted = false;
   }

   @Override
   public synchronized boolean isStarted() {
      return isStarted;
   }

   public synchronized Map<String, ConnectorService> getConnectors() {
      return Collections.unmodifiableMap(connectors);
   }
}
