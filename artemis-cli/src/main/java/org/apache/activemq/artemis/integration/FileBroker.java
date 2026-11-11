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
package org.apache.activemq.artemis.integration;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.LegacyJMSConfiguration;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.dto.ServerDTO;
import org.apache.activemq.artemis.integration.bootstrap.ActiveMQBootstrapLogger;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.VersionLoader;

public class FileBroker implements Broker {

   private final String configurationUrl;
   private final ActivateCallback activateCallback;

   private boolean started;

   private final ActiveMQSecurityManager securityManager;

   private Map<String, ActiveMQComponent> components;

   public FileBroker(ServerDTO broker, ActiveMQSecurityManager security, ActivateCallback activateCallback) {
      this.securityManager = security;
      this.configurationUrl = broker.configuration;
      this.activateCallback = activateCallback;
   }

   @Override
   public synchronized void start() throws Exception {
      if (started) {
         return;
      }

      if (components == null) {
         createComponents();
      }

      List<ActiveMQComponent> componentsByStartOrder = getComponentsByStartOrder(components);
      String fullVersion = VersionLoader.getVersion().getFullVersion();
      ActiveMQBootstrapLogger.LOGGER.serverStarting(fullVersion);
      for (ActiveMQComponent component : componentsByStartOrder) {
         component.start();
      }
      started = true;

   }


   private void createDirectories(FileConfiguration fileConfiguration) {
      if (!fileConfiguration.isUsingDatabasePersistence()) {
         fileConfiguration.getPagingLocation().mkdirs();
         fileConfiguration.getJournalLocation().mkdirs();
         fileConfiguration.getBindingsLocation().mkdirs();
         fileConfiguration.getLargeMessagesLocation().mkdirs();
      }
   }

   @Override
   public void stop() throws Exception {
      stop(true);
   }

   @Override
   public void stop(boolean isShutdown) throws Exception {
      if (!started) {
         return;
      }
      ActiveMQComponent[] mqComponents = new ActiveMQComponent[components.size()];
      components.values().toArray(mqComponents);
      for (int i = mqComponents.length - 1; i >= 0; i--) {
         if (mqComponents[i] instanceof ServiceComponent serviceComponent) {
            serviceComponent.stop(isShutdown);
         } else {
            mqComponents[i].stop();
         }
      }
      started = false;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public Map<String, ActiveMQComponent> getComponents() {
      return components;
   }

   @Override
   public void createComponents() throws Exception {
      //todo if we start to pullout more configs from the main config then we should pull out the configuration objects from factories if available
      FileConfiguration configuration = new FileConfiguration();

      LegacyJMSConfiguration legacyJMSConfiguration = new LegacyJMSConfiguration(configuration);

      FileDeploymentManager fileDeploymentManager = new FileDeploymentManager(configurationUrl);
      fileDeploymentManager.addDeployable(configuration).addDeployable(legacyJMSConfiguration);
      fileDeploymentManager.readConfiguration();

      createDirectories(configuration);

      components = fileDeploymentManager.buildService(securityManager, ManagementFactory.getPlatformMBeanServer(), activateCallback);
   }

   /**
    * This makes sure the components are started in the correct order. Its simple at the mo as e only have core and jms
    * but will need impproving if we get more.
    */
   private List<ActiveMQComponent> getComponentsByStartOrder(Map<String, ActiveMQComponent> components) {
      List<ActiveMQComponent> activeMQComponents = new ArrayList<>();
      ActiveMQComponent jmsComponent = components.get("jms");
      if (jmsComponent != null) {
         activeMQComponents.add(jmsComponent);
      }
      activeMQComponents.add(components.get("core"));
      return activeMQComponents;
   }

   @Override
   public ActiveMQServer getServer() {
      return (ActiveMQServer) components.get("core");
   }

}
