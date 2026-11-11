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
package org.apache.activemq.artemis.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.Test;

public class ServerLocatorConfigDiscoveryPropertyTest extends ArtemisTestCase {

   @Test
   public void testDiscoveryEnabledEnvVarName() {
      assertEquals("ARTEMIS_DISCOVERY_ENABLED", ServerLocatorConfig.DISCOVERY_ENABLED_ENV_VAR);
   }

   @Test
   public void testDiscoveryDisabledByDefault() {
      assertFalse(ServerLocatorConfig.isDiscoveryEnabled());
   }

   @Test
   public void testDiscoveryEnabledFromSystemProperty() {
      ServerLocatorConfig.setDiscoveryEnabled(null);
      System.setProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY, "true");
      runAfter(() -> {
         System.clearProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY);
         ServerLocatorConfig.setDiscoveryEnabled(null);
      });

      assertTrue(ServerLocatorConfig.isDiscoveryEnabled());
   }

   @Test
   public void testDiscoveryDisabledFromSystemProperty() {
      ServerLocatorConfig.setDiscoveryEnabled(null);
      System.setProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY, "false");
      runAfter(() -> {
         System.clearProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY);
         ServerLocatorConfig.setDiscoveryEnabled(null);
      });

      assertFalse(ServerLocatorConfig.isDiscoveryEnabled());
   }

   @Test
   public void testSetDiscoveryEnabledOverridesCachedValue() {
      runAfter(() -> {
         ServerLocatorConfig.setDiscoveryEnabled(null);
         System.clearProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY);
      });

      assertFalse(ServerLocatorConfig.isDiscoveryEnabled());
      ServerLocatorConfig.setDiscoveryEnabled(true);
      assertTrue(ServerLocatorConfig.isDiscoveryEnabled());
      ServerLocatorConfig.setDiscoveryEnabled(false);
      assertFalse(ServerLocatorConfig.isDiscoveryEnabled());
      ServerLocatorConfig.setDiscoveryEnabled(null);
      assertFalse(ServerLocatorConfig.isDiscoveryEnabled());
   }

   @Test
   public void testCreateServerLocatorFailsWhenDisabled() {
      DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration()
         .setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress("231.7.7.7").setGroupPort(9876));

      IllegalStateException exception = assertThrows(IllegalStateException.class,
         () -> ActiveMQClient.createServerLocatorWithoutHA(groupConfiguration));
      assertTrue(exception.getMessage().contains("server discovery is disabled by default"));
   }

   @Test
   public void testCreateServerLocatorSucceedsWhenEnabledViaSystemProperty() {
      ServerLocatorConfig.setDiscoveryEnabled(null);
      System.setProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY, "true");
      runAfter(() -> {
         System.clearProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY);
         ServerLocatorConfig.setDiscoveryEnabled(null);
      });

      testCreateServerLocatorSucceeds();
   }

   @Test
   public void testCreateServerLocatorSucceedsWhenEnabled() {
      enableDiscoveryForTest();

      testCreateServerLocatorSucceeds();
   }

   private void enableDiscoveryForTest() {
      ServerLocatorConfig.setDiscoveryEnabled(true);
      runAfter(() -> ServerLocatorConfig.setDiscoveryEnabled(null));
   }

   private void testCreateServerLocatorSucceeds() {
      DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration()
         .setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress("231.7.7.7").setGroupPort(9876));

      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(groupConfiguration);
      assertNotNull(locator);
      locator.close();
   }
}
