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
package org.apache.activemq.artemis.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.artemis.api.config.ServerLocatorConfigTestAccessor;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

public class BroadcastDiscoveryConfigurationValidationTest extends ServerTestBase {

   @Test
   public void testConstructionSucceedsWhenDisabled() throws Exception {
      Configuration configuration = createDefaultInVMConfig()
         .addConnectorConfiguration("invm", new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(0), "invm"))
         .addBroadcastGroupConfiguration(createBroadcastGroupConfiguration());

      assertDoesNotThrow(() -> ActiveMQServers.newActiveMQServer(configuration, false));
   }

   @Test
   public void testStartFailsWithBroadcastGroupsWhenDisabled() throws Exception {
      Configuration configuration = createDefaultInVMConfig()
         .addConnectorConfiguration("invm", new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(0), "invm"))
         .addBroadcastGroupConfiguration(createBroadcastGroupConfiguration());

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(configuration, false);

      ActiveMQIllegalStateException exception = assertThrows(ActiveMQIllegalStateException.class, server::start);
      assertTrue(exception.getMessage().contains("AMQ229262"));
   }

   @Test
   public void testStartFailsWithDiscoveryGroupsWhenDisabled() throws Exception {
      Configuration configuration = createDefaultInVMConfig()
         .addDiscoveryGroupConfiguration("dg1", createDiscoveryGroupConfiguration());

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(configuration, false);

      ActiveMQIllegalStateException exception = assertThrows(ActiveMQIllegalStateException.class, server::start);
      assertTrue(exception.getMessage().contains("AMQ229263"));
   }

   @Test
   public void testStartSucceedsWhenEnabledViaSystemProperty() throws Exception {
      ServerLocatorConfigTestAccessor.setDiscoveryEnabled(null);
      System.setProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY, "true");
      runAfter(() -> {
         System.clearProperty(ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY);
         ServerLocatorConfigTestAccessor.setDiscoveryEnabled(null);
      });

      testStartSucceeds();
   }

   @Test
   public void testStartSucceedsWhenEnabled() throws Exception {
      enableDiscoveryForTest();

      testStartSucceeds();
   }

   private void testStartSucceeds() throws Exception {
      Configuration configuration = configurationWithBroadcastAndDiscovery();

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(configuration, false);

      server.start();
      server.stop();
   }

   private BroadcastGroupConfiguration createBroadcastGroupConfiguration() {
      List<String> connectorNames = new ArrayList<>();
      connectorNames.add("invm");

      return new BroadcastGroupConfiguration()
         .setName("bg1")
         .setBroadcastPeriod(1000)
         .setConnectorInfos(connectorNames)
         .setEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress("231.7.7.7").setGroupPort(9876));
   }

   private DiscoveryGroupConfiguration createDiscoveryGroupConfiguration() {
      return new DiscoveryGroupConfiguration()
         .setName("dg1")
         .setRefreshTimeout(5000)
         .setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress("231.7.7.7").setGroupPort(9876));
   }

   private Configuration configurationWithBroadcastAndDiscovery() throws Exception {
      TransportConfiguration connector = new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(0), "invm");
      return createDefaultInVMConfig()
         .addConnectorConfiguration("invm", connector)
         .addBroadcastGroupConfiguration(createBroadcastGroupConfiguration())
         .addDiscoveryGroupConfiguration("dg1", createDiscoveryGroupConfiguration());
   }
}
