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
package org.apache.activemq.artemis.tests.integration.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class XmlConfigPluginTest extends ActiveMQTestBase {

   @Test
   public void testStopStart1() throws Exception {
      ActiveMQServer server = createServerFromConfig("broker-plugins-config.xml");
      try {
         server.start();
         assertEquals(2, server.getBrokerPlugins().size());
         assertInstanceOf(MethodCalledVerifier.class, server.getBrokerPlugins().get(0));
         assertInstanceOf(ConfigurationVerifier.class, server.getBrokerPlugins().get(1));
         ConfigurationVerifier configurationVerifier = (ConfigurationVerifier) server.getBrokerPlugins().get(1);
         assertEquals("val_1", configurationVerifier.value1, "value1");
         assertEquals("val_2", configurationVerifier.value2, "value2");
         assertNull(configurationVerifier.value3, "value3 should not have been set");
      } finally {
         if (server != null) {
            server.stop();
         }
      }
   }

   /**
    * Ensure the configuration is bring picked up correctly by LoggingActiveMQServerPlugin
    */
   @Test
   public void testLoggingActiveMQServerPlugin() throws Exception {
      ActiveMQServer server = createServerFromConfig("broker-logging-plugin.xml");
      try {
         server.start();
         assertEquals(1, server.getBrokerPlugins().size(), "only one plugin should be registered");
         assertInstanceOf(LoggingActiveMQServerPlugin.class, server.getBrokerPlugins().get(0), "ensure LoggingActiveMQServerPlugin is registered");
         LoggingActiveMQServerPlugin loggingActiveMQServerPlugin = (LoggingActiveMQServerPlugin) server.getBrokerPlugins().get(0);
         assertTrue(loggingActiveMQServerPlugin.isLogAll(), "check logAll");
         assertTrue(loggingActiveMQServerPlugin.isLogConnectionEvents(), "check logConnectionEvents");
         assertTrue(loggingActiveMQServerPlugin.isLogSessionEvents(), "check logSessionEvents");
         assertTrue(loggingActiveMQServerPlugin.isLogConsumerEvents(), "check logConsumerEvents");
         assertTrue(loggingActiveMQServerPlugin.isLogDeliveringEvents(), "check logDeliveringEvents");
         assertTrue(loggingActiveMQServerPlugin.isLogSendingEvents(), "check logSendingEvents");
         assertTrue(loggingActiveMQServerPlugin.isLogInternalEvents(), "check logInternalEvents");
      } finally {
         if (server != null) {
            server.stop();
         }
      }
   }

   /**
    *  ensure the LoggingActiveMQServerPlugin uses default values when configured with incorrect values
    */
   @Test
   public void testLoggingActiveMQServerPluginWrongValue() throws Exception {
      ActiveMQServer server = createServerFromConfig("broker-logging-plugin-wrong.xml");
      try {
         server.start();
         assertEquals(1, server.getBrokerPlugins().size(), "only one plugin should be registered");
         assertInstanceOf(LoggingActiveMQServerPlugin.class, server.getBrokerPlugins().get(0), "ensure LoggingActiveMQServerPlugin is registered");
         LoggingActiveMQServerPlugin loggingActiveMQServerPlugin = (LoggingActiveMQServerPlugin) server.getBrokerPlugins().get(0);
         assertFalse(loggingActiveMQServerPlugin.isLogAll(), "check logAll");
         assertFalse(loggingActiveMQServerPlugin.isLogConnectionEvents(), "check logConnectionEvents");
         assertFalse(loggingActiveMQServerPlugin.isLogSessionEvents(), "check logSessionEvents");
         assertFalse(loggingActiveMQServerPlugin.isLogConsumerEvents(), "check logConsumerEvents");
         assertFalse(loggingActiveMQServerPlugin.isLogDeliveringEvents(), "check logDeliveringEvents");
         assertFalse(loggingActiveMQServerPlugin.isLogSendingEvents(), "check logSendingEvents");
         assertFalse(loggingActiveMQServerPlugin.isLogInternalEvents(), "check logInternalEvents");

      } finally {
         if (server != null) {
            server.stop();
         }
      }
   }

   private ActiveMQServer createServerFromConfig(String configFileName) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileJMSConfiguration fileConfiguration = new FileJMSConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(configFileName);
      deploymentManager.addDeployable(fc);
      deploymentManager.addDeployable(fileConfiguration);
      deploymentManager.readConfiguration();

      return addServer(new ActiveMQServerImpl(fc));
   }

}
