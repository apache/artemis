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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.Map;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(10)
public class BrokerPluginReloadTest extends ServerTestBase {

   private static final String BROKER_XML_TEMPLATE = """
      <core xmlns="urn:activemq:core">
         <persistence-enabled>false</persistence-enabled>
         <security-enabled>false</security-enabled>
         <broker-plugins>
            <broker-plugin class-name="%s">
               <property key="key" value="%s"/>
            </broker-plugin>
         </broker-plugins>
      </core>
      """;

   private static final String TWO_PLUGIN_BROKER_XML_TEMPLATE = """
      <core xmlns="urn:activemq:core">
         <persistence-enabled>false</persistence-enabled>
         <security-enabled>false</security-enabled>
         <broker-plugins>
            <broker-plugin class-name="%s">
               <property key="key" value="%s"/>
            </broker-plugin>
            <broker-plugin class-name="%s">
               <property key="key" value="%s"/>
            </broker-plugin>
         </broker-plugins>
      </core>
      """;

   @Test
   public void testPropertiesReloadedGetCorrectProperties() throws Exception {
      File brokerXml = writeBrokerXml(ReloadAwarePlugin.class.getName(), "foo");

      ActiveMQServer server = startServerFromBrokerXml(brokerXml);
      ReloadAwarePlugin plugin = findPlugin(server, ReloadAwarePlugin.class);
      assertEquals("foo", plugin.properties.get("key"));

      writeBrokerXml(brokerXml, ReloadAwarePlugin.class.getName(), "bar");
      server.reloadConfigurationFile();
      assertEquals(1, plugin.reloadCount);
      assertEquals("bar", plugin.properties.get("key"));

      writeBrokerXml(brokerXml, ReloadAwarePlugin.class.getName(), "baz");
      server.reloadConfigurationFile();
      assertEquals(2, plugin.reloadCount);
      assertEquals("baz", plugin.properties.get("key"));
   }

   @Test
   public void testPluginNotInBrokerXmlNotNotified() throws Exception {
      File brokerXml = writeBrokerXml(TestPlugin.class.getName(), "foo");

      ActiveMQServer server = startServerFromBrokerXml(brokerXml);

      // Manually register a plugin that does NOT appear in the broker.xml
      ReloadAwarePlugin plugin = new ReloadAwarePlugin();
      server.registerBrokerPlugin(plugin);

      server.reloadConfigurationFile();

      assertEquals(0, plugin.reloadCount);
   }

   @Test
   public void testExceptionInPluginNotBlockingOtherPlugins() throws Exception {
      // ThrowingPlugin listed first - its exception must not block ReloadAwarePlugin
      File brokerXml = writeBrokerXmlWithTwoPlugins(ThrowingPlugin.class.getName(), "foo1", ReloadAwarePlugin.class.getName(), "foo2");

      ActiveMQServer server = startServerFromBrokerXml(brokerXml);
      ReloadAwarePlugin plugin = findPlugin(server, ReloadAwarePlugin.class);

      writeBrokerXmlWithTwoPlugins(brokerXml, ThrowingPlugin.class.getName(), "bar1", ReloadAwarePlugin.class.getName(), "bar2");
      server.reloadConfigurationFile();

      assertEquals(1, plugin.reloadCount);
      assertEquals("bar2", plugin.properties.get("key"));
   }

   @Test
   public void testPluginNotifiedWhenPropertiesUnchanged() throws Exception {
      File brokerXml = writeBrokerXml(ReloadAwarePlugin.class.getName(), "foo");

      ActiveMQServer server = startServerFromBrokerXml(brokerXml);
      ReloadAwarePlugin plugin = findPlugin(server, ReloadAwarePlugin.class);

      server.reloadConfigurationFile();
      assertEquals(1, plugin.reloadCount);
      assertEquals("foo", plugin.properties.get("key"));
   }

   @Test
   public void testEachConfigEntryNotifiesMatchingPlugins() throws Exception {
      // Two config entries with the same class - plugin should be notified once per entry
      File brokerXml = writeBrokerXmlWithTwoPlugins(ReloadAwarePlugin.class.getName(), "foo1", ReloadAwarePlugin.class.getName(), "foo2");

      ActiveMQServer server = startServerFromBrokerXml(brokerXml);
      ReloadAwarePlugin plugin = findPlugin(server, ReloadAwarePlugin.class);

      writeBrokerXmlWithTwoPlugins(brokerXml, ReloadAwarePlugin.class.getName(), "bar1", ReloadAwarePlugin.class.getName(), "bar2");
      server.reloadConfigurationFile();

      assertEquals(2, plugin.reloadCount);
      // Last notification wins for properties
      assertEquals("bar2", plugin.properties.get("key"));
   }

   private ActiveMQServer startServerFromBrokerXml(File brokerXml) throws Exception {
      Configuration config;
      try (FileInputStream fis = new FileInputStream(brokerXml)) {
         config = new FileConfigurationParser().parseMainConfig(fis);
      }
      config.setConfigurationUrl(brokerXml.toURI().toURL());
      config.setConfigurationFileRefreshPeriod(-1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config));
      server.start();
      return server;
   }

   @SuppressWarnings("unchecked")
   private <T> T findPlugin(ActiveMQServer server, Class<T> type) {
      return (T) server.getBrokerPlugins().stream().filter(type::isInstance).findFirst().orElseThrow();
   }

   private File writeBrokerXml(String className, String value) throws Exception {
      File brokerXml = new File(temporaryFolder, "broker.xml");
      writeBrokerXml(brokerXml, className, value);
      return brokerXml;
   }

   private void writeBrokerXml(File file, String className, String value) throws Exception {
      Files.writeString(file.toPath(), String.format(BROKER_XML_TEMPLATE, className, value));
   }

   private File writeBrokerXmlWithTwoPlugins(String pluginClass1,
                                             String value1,
                                             String pluginClass2,
                                             String value2) throws Exception {
      File brokerXml = new File(temporaryFolder, "broker.xml");
      writeBrokerXmlWithTwoPlugins(brokerXml, pluginClass1, value1, pluginClass2, value2);
      return brokerXml;
   }

   private void writeBrokerXmlWithTwoPlugins(File file,
                                             String pluginClass1,
                                             String value1,
                                             String pluginClass2,
                                             String value2) throws Exception {
      Files.writeString(file.toPath(), String.format(TWO_PLUGIN_BROKER_XML_TEMPLATE, pluginClass1, value1, pluginClass2, value2));
   }

   public static class ReloadAwarePlugin implements ActiveMQServerPlugin {

      Map<String, String> properties;
      int reloadCount;

      @Override
      public void init(Map<String, String> properties) {
         this.properties = properties;
      }

      @Override
      public void propertiesReloaded(Map<String, String> properties) {
         this.properties = properties;
         this.reloadCount++;
      }
   }

   public static class TestPlugin implements ActiveMQServerPlugin {

   }

   public static class ThrowingPlugin implements ActiveMQServerPlugin {

      @Override
      public void propertiesReloaded(Map<String, String> properties) {
         throw new RuntimeException("Simulated plugin failure");
      }
   }
}
