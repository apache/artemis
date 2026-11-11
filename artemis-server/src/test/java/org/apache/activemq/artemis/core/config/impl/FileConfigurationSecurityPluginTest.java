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
package org.apache.activemq.artemis.core.config.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileConfigurationSecurityPluginTest extends AbstractConfigurationTestBase {

   protected String getConfigurationName() {
      return "ConfigurationTest-security-plugin-config.xml";
   }

   @Override
   protected Configuration createConfiguration() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(getConfigurationName());
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      return fc;
   }

   @Test
   public void testDefaults() {
      List<SecuritySettingPlugin> securitySettingPlugins = conf.getSecuritySettingPlugins();
      assertEquals(1, securitySettingPlugins.size());
      assertEquals("secret", MyPlugin.options.get("setting1"));
      assertEquals("hello", MyPlugin.options.get("setting2"));
   }

   @Test
   public void testSetGetAttributes() throws Exception {
      doSetGetAttributesTestImpl(conf);
   }

   @Test
   public void testGetSetInterceptors() {
      doGetSetInterceptorsTestImpl(conf);
   }

   @Test
   public void testSerialize() throws Exception {
      doSerializeTestImpl(conf);
   }

   @Test
   public void testSetConnectionRoutersPolicyConfiguration() throws Throwable {
      doSetConnectionRoutersPolicyConfigurationTestImpl((ConfigurationImpl) conf);
   }

   // Referenced by the configuration file
   public static class MyPlugin implements SecuritySettingPlugin {

      private static Map<String, String> options;

      public MyPlugin() {
      }

      @Override
      public SecuritySettingPlugin init(Map<String, String> options) {
         MyPlugin.options = options;
         return this;
      }

      @Override
      public SecuritySettingPlugin stop() {
         return null;
      }

      @Override
      public Map<String, Set<Role>> getSecurityRoles() {
         return null;
      }

      @Override
      public void setSecurityRepository(HierarchicalRepository<Set<Role>> securityRepository) {

      }
   }
}
