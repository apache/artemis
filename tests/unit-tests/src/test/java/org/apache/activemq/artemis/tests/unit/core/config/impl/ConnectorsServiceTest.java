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
package org.apache.activemq.artemis.tests.unit.core.config.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ConnectorService;
import org.apache.activemq.artemis.core.server.ServiceRegistry;
import org.apache.activemq.artemis.core.server.impl.ConnectorsService;
import org.apache.activemq.artemis.core.server.impl.ServiceRegistryImpl;
import org.apache.activemq.artemis.tests.unit.core.config.impl.fakes.FakeConnectorService;
import org.apache.activemq.artemis.tests.unit.core.config.impl.fakes.FakeConnectorServiceFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConnectorsServiceTest extends ActiveMQTestBase {

   private Configuration configuration;

   private ServiceRegistry serviceRegistry;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      // Setup Configuration
      configuration = new ConfigurationImpl();
      serviceRegistry = new ServiceRegistryImpl();
   }

   /**
    * Test that the connectors added via the service registry are added to the connectorsService,
    */
   @Test
   public void testConnectorsServiceUsesInjectedConnectorServiceFactory() throws Exception {
      ConnectorServiceConfiguration connectorServiceConfiguration = new ConnectorServiceConfiguration().setFactoryClassName(null).setParams(new HashMap<>()).setName("myfact");

      // Creates a fake connector service factory that returns the fake connector service object
      ConnectorService connectorService = new FakeConnectorService();
      FakeConnectorServiceFactory connectorServiceFactory = new FakeConnectorServiceFactory();

      serviceRegistry.addConnectorService(connectorServiceFactory, connectorServiceConfiguration);
      ConnectorsService connectorsService = new ConnectorsService(configuration, null, null, null, serviceRegistry);
      connectorsService.start();

      assertEquals(1, connectorsService.getConnectors().size());
      assertTrue(connectorsService.getConnectors().values().contains(connectorServiceFactory.getConnectorService()));
   }

   /**
    * Test that the connectors added via the config are added to the connectors service.
    */
   @Test
   public void testConnectorsServiceUsesConfiguredConnectorServices() throws Exception {
      ConnectorServiceConfiguration connectorServiceConfiguration = new ConnectorServiceConfiguration().setFactoryClassName(FakeConnectorServiceFactory.class.getCanonicalName()).setParams(new HashMap<>()).setName("myfact");

      List<ConnectorServiceConfiguration> connectorServiceConfigurations = new ArrayList<>();
      connectorServiceConfigurations.add(connectorServiceConfiguration);

      configuration.setConnectorServiceConfigurations(connectorServiceConfigurations);
      ConnectorsService connectorsService = new ConnectorsService(configuration, null, null, null, serviceRegistry);
      connectorsService.start();

      assertEquals(1, connectorsService.getConnectors().size());
   }

   /**
    * Test that connectors can be created and destroyed directly.
    */
   @Test
   public void testConnectorServiceUsedDirectly() throws Exception {
      // Initial setup with existing connector service
      ConnectorServiceConfiguration connectorServiceConfiguration = new ConnectorServiceConfiguration().setFactoryClassName(FakeConnectorServiceFactory.class.getCanonicalName()).setParams(new HashMap<>()).setName("myfact");
      configuration.setConnectorServiceConfigurations(Arrays.asList(connectorServiceConfiguration));

      ConnectorsService connectorsService = new ConnectorsService(configuration, null, null, null, serviceRegistry);
      connectorsService.start();
      assertEquals(1, connectorsService.getConnectors().size());


      // Add with same name
      FakeConnectorServiceFactory connectorServiceFactory = new FakeConnectorServiceFactory();
      try {
         connectorsService.createService(connectorServiceConfiguration, connectorServiceFactory);
         fail("Expected exception when creating service with same name");
      } catch (Exception e) {
      }


      // Add unique with same factory
      ConnectorServiceConfiguration additionalServiceConfiguration = new ConnectorServiceConfiguration().setFactoryClassName(FakeConnectorServiceFactory.class.getCanonicalName()).setParams(new HashMap<>()).setName("myfact2");
      connectorsService.createService(additionalServiceConfiguration, connectorServiceFactory);
      assertEquals(2, connectorsService.getConnectors().size());

      // Destroy existing connector services
      connectorsService.destroyService("myfact");
      assertEquals(1, connectorsService.getConnectors().size());

      connectorsService.destroyService("myfact2");
      assertEquals(0, connectorsService.getConnectors().size());

      // Destroy non-existing connector service
      try {
         connectorsService.destroyService("myfact");
         fail("Expected exception when destroying non-existing service");
      } catch (Exception e) {
      }
   }
}
