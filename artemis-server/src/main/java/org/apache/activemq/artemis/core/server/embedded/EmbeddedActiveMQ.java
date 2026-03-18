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
package org.apache.activemq.artemis.core.server.embedded;

import javax.management.MBeanServer;
import java.io.File;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.LegacyJMSConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

/**
 * Helper class to simplify bootstrap of Apache Artemis server.  Bootstraps from classpath-based config files.
 */
public class EmbeddedActiveMQ {

   protected ActiveMQSecurityManager securityManager;
   protected String configResourcePath = null;
   protected Configuration configuration;
   protected ActiveMQServer activeMQServer;
   protected MBeanServer mbeanServer;
   protected String propertiesResourcePath = ActiveMQDefaultConfiguration.BROKER_PROPERTIES_SYSTEM_PROPERTY_NAME;

   /**
    * Classpath resource for activemq server config.  Defaults to 'broker.xml'.
    */
   public EmbeddedActiveMQ setConfigResourcePath(String filename) {
      configResourcePath = filename;
      return this;
   }

   /**
    * Classpath resource for broker properties file.  Defaults to 'broker.properties'.
    */
   public EmbeddedActiveMQ setPropertiesResourcePath(String filename) {
      propertiesResourcePath = filename;
      return this;
   }

   /**
    * Set the activemq security manager. This defaults to
    * org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManagerImpl
    */
   public EmbeddedActiveMQ setSecurityManager(ActiveMQSecurityManager securityManager) {
      this.securityManager = securityManager;
      return this;
   }

   /**
    * It will iterate the cluster connections until you have at least the number of expected servers
    *
    * @param timeWait   Time to wait on each iteration
    * @param unit       unit of time to wait
    * @param iterations number of iterations
    * @param servers    number of minimal servers
    */
   public boolean waitClusterForming(long timeWait, TimeUnit unit, int iterations, int servers) throws Exception {
      if (activeMQServer.getClusterManager().getClusterConnections() == null || activeMQServer.getClusterManager().getClusterConnections().isEmpty()) {
         return servers == 0;
      }

      for (int i = 0; i < iterations; i++) {
         for (ClusterConnection connection : activeMQServer.getClusterManager().getClusterConnections()) {
            if (connection.getTopology().getMembers().size() == servers) {
               return true;
            }
            Thread.sleep(unit.toMillis(timeWait));
         }
      }

      return false;
   }

   /**
    * Use this mbean server to register management beans.  If not set, no mbeans will be registered.
    */
   public EmbeddedActiveMQ setMbeanServer(MBeanServer mbeanServer) {
      this.mbeanServer = mbeanServer;
      return this;
   }

   /**
    * Set this object if you are not using file-based configuration.  The default implementation will load
    * configuration from a file.
    */
   public EmbeddedActiveMQ setConfiguration(Configuration configuration) {
      this.configuration = configuration;
      return this;
   }

   public Configuration getConfiguration() {
      return configuration;
   }

   public ActiveMQServer getActiveMQServer() {
      return activeMQServer;
   }

   public EmbeddedActiveMQ start() throws Exception {
      createActiveMQServer();
      activeMQServer.start();
      return this;
   }

   public void createActiveMQServer() throws Exception {
      if (activeMQServer != null) {
         return;
      }
      if (configuration == null) {
         if (configResourcePath == null) {
            configResourcePath = "broker.xml";
         }
         FileDeploymentManager deploymentManager = new FileDeploymentManager(configResourcePath);
         FileConfiguration config = new FileConfiguration();
         LegacyJMSConfiguration legacyJMSConfiguration = new LegacyJMSConfiguration(config);
         deploymentManager.addDeployable(config).addDeployable(legacyJMSConfiguration);
         deploymentManager.readConfiguration();
         configuration = config;
      }
      if (securityManager == null) {
         securityManager = new ActiveMQJAASSecurityManager();
      }
      if (mbeanServer == null) {
         activeMQServer = new ActiveMQServerImpl(configuration, securityManager);
      } else {
         activeMQServer = new ActiveMQServerImpl(configuration, mbeanServer, securityManager);
      }

      if (propertiesResourcePath != null) {
         if (propertiesResourcePath == ActiveMQDefaultConfiguration.BROKER_PROPERTIES_SYSTEM_PROPERTY_NAME) {
            // try and locate on the classpath, broker.properties
            URL brokerPropertiesFromClasspath = this.getClass().getClassLoader().getResource(propertiesResourcePath);
            if (brokerPropertiesFromClasspath != null) {
               activeMQServer.setProperties(new File(brokerPropertiesFromClasspath.toURI()).getAbsolutePath());
            }
         } else {
            // pass through non default configured value
            activeMQServer.setProperties(propertiesResourcePath);
         }
      }
   }

   public EmbeddedActiveMQ stop() throws Exception {
      if (activeMQServer != null) {
         activeMQServer.stop();
      }
      return this;
   }
}
