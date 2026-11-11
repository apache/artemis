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
package org.apache.activemq.artemis.core.server.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.routing.CacheConfiguration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.config.routing.NamedPropertyConfiguration;
import org.apache.activemq.artemis.core.config.routing.PoolConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashModuloPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashPolicy;
import org.apache.activemq.artemis.core.server.routing.pools.Pool;
import org.jgroups.util.UUID;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ConnectionRouterManagerTest {

   ActiveMQServer mockServer;
   ConnectionRouterManager underTest;

   @BeforeEach
   public void setUp() throws Exception {

      mockServer = mock(ActiveMQServer.class);
      Mockito.when(mockServer.getNodeID()).thenReturn(SimpleString.of(UUID.randomUUID().toString()));

      underTest = new ConnectionRouterManager(mockServer, null);
      underTest.start();
   }

   @AfterEach
   public void tearDown() throws Exception {
      if (underTest != null) {
         underTest.stop();
      }
   }

   @Test
   public void deployLocalOnlyPoolInvalid() throws Exception {
      assertThrows(IllegalStateException.class, () -> {

         ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
         connectionRouterConfiguration.setName("partition-local-pool");
         NamedPropertyConfiguration policyConfig = new NamedPropertyConfiguration();
         policyConfig.setName(ConsistentHashPolicy.NAME);
         connectionRouterConfiguration.setPolicyConfiguration(policyConfig);

         PoolConfiguration poolConfiguration = new PoolConfiguration();
         poolConfiguration.setLocalTargetEnabled(true);
         connectionRouterConfiguration.setPoolConfiguration(poolConfiguration);

         underTest.deployConnectionRouter(connectionRouterConfiguration);
      });
   }

   @Test
   public void deployLocalOnly() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("partition-local-pool");

      final ConnectionRouter router = underTest.deployConnectionRouter(connectionRouterConfiguration);

      assertNotNull(router);
      assertTrue(router.isStarted());

      underTest.stop();

      assertFalse(router.isStarted());
   }

   @Test
   public void deployLocalOnlyToUnstartedManager() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("partition-local-pool");

      underTest.stop();

      final ConnectionRouter router = underTest.deployConnectionRouter(connectionRouterConfiguration);

      assertNotNull(router);
      assertFalse(router.isStarted());

      underTest.start();

      assertTrue(router.isStarted());
   }

   @Test
   public void deployLocalOnlyWithPolicy() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("partition-local-consistent-hash").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfig = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(2)));
      connectionRouterConfiguration.setPolicyConfiguration(policyConfig);

      underTest.deployConnectionRouter(connectionRouterConfiguration);
   }

   @Test
   public void deploy2LocalOnlyWithSamePolicy() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("partition-local-consistent-hash").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfig = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(2)));
      connectionRouterConfiguration.setPolicyConfiguration(policyConfig);
      final ConnectionRouter router1 = underTest.deployConnectionRouter(connectionRouterConfiguration);

      connectionRouterConfiguration.setName("partition-local-consistent-hash-bis");
      final ConnectionRouter router2 = underTest.deployConnectionRouter(connectionRouterConfiguration);

      assertEquals("partition-local-consistent-hash", router1.getName());
      assertEquals("partition-local-consistent-hash-bis", router2.getName());

      assertTrue(router1.isStarted());
      assertTrue(router2.isStarted());
   }

   @Test
   public void deployPolicyAndRemoveWithUpdate() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      List<ConnectionRouterConfiguration> configurations = new ArrayList<>();

      Configuration mockConfiguration = Mockito.mock(Configuration.class);
      Mockito.when(mockConfiguration.getConnectionRouters()).thenReturn(configurations);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("partition-local-consistent-hash-1").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfig1 = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(2)));
      connectionRouterConfiguration.setPolicyConfiguration(policyConfig1);

      configurations.add(connectionRouterConfiguration);

      underTest.deploy(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);

      configurations.clear();

      underTest.update(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration.getName());
      Mockito.verifyNoMoreInteractions(mockManagementService);

      underTest.stop();

      Mockito.verifyNoMoreInteractions(mockManagementService);
   }

   @Test
   public void deployPolicyAndAddNewPolicyWithUpdate() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      List<ConnectionRouterConfiguration> configurations = new ArrayList<>();

      Configuration mockConfiguration = Mockito.mock(Configuration.class);
      Mockito.when(mockConfiguration.getConnectionRouters()).thenReturn(configurations);

      ConnectionRouterConfiguration connectionRouterConfiguration1 = new ConnectionRouterConfiguration();
      connectionRouterConfiguration1.setName("partition-local-consistent-hash-1").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfig1 = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(2)));
      connectionRouterConfiguration1.setPolicyConfiguration(policyConfig1);

      configurations.add(connectionRouterConfiguration1);

      underTest.deploy(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);
      Mockito.clearInvocations(mockManagementService);

      ConnectionRouterConfiguration connectionRouterConfiguration2 = new ConnectionRouterConfiguration();
      connectionRouterConfiguration2.setName("partition-local-consistent-hash-2").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfig2 = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(2)));
      connectionRouterConfiguration2.setPolicyConfiguration(policyConfig2);

      configurations.add(connectionRouterConfiguration2);

      underTest.update(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);

      assertNotNull(underTest.getRouter("partition-local-consistent-hash-1"));
      assertNotNull(underTest.getRouter("partition-local-consistent-hash-2"));

      final ConnectionRouter router1 = underTest.getRouter("partition-local-consistent-hash-1");
      final ConnectionRouter router2 = underTest.getRouter("partition-local-consistent-hash-2");

      assertTrue(router1.isStarted());
      assertTrue(router2.isStarted());

      underTest.start();

      assertTrue(router1.isStarted());
      assertTrue(router2.isStarted());
      assertNotSame(router1, router2);

      underTest.stop();

      assertFalse(router1.isStarted());
      assertFalse(router2.isStarted());

      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration1.getName());
      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration2.getName());
   }

   @Test
   public void deployPolicyAndUpdateIt() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      List<ConnectionRouterConfiguration> configurations = new ArrayList<>();

      Configuration mockConfiguration = Mockito.mock(Configuration.class);
      Mockito.when(mockConfiguration.getConnectionRouters()).thenReturn(configurations);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("partition-local-consistent-hash-1").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfig = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(2)));
      connectionRouterConfiguration.setPolicyConfiguration(policyConfig);

      configurations.add(connectionRouterConfiguration);

      underTest.deploy(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);
      Mockito.clearInvocations(mockManagementService);

      final ConnectionRouter router = underTest.getRouter("partition-local-consistent-hash-1");

      assertEquals("2", router.getPolicy().getProperties().get(ConsistentHashModuloPolicy.MODULO));

      // Modify some configuration but use the same router name, this should remove the previous one
      // and add back a new instance
      ConnectionRouterConfiguration connectionRouterConfigurationUpdate = new ConnectionRouterConfiguration();
      connectionRouterConfigurationUpdate.setName("partition-local-consistent-hash-1").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfigUpdated = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(3)));
      connectionRouterConfigurationUpdate.setPolicyConfiguration(policyConfigUpdated);

      configurations.clear();
      configurations.add(connectionRouterConfigurationUpdate);

      underTest.update(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration.getName());
      Mockito.clearInvocations(mockManagementService);

      final ConnectionRouter updated = underTest.getRouter("partition-local-consistent-hash-1");

      assertEquals("3", updated.getPolicy().getProperties().get(ConsistentHashModuloPolicy.MODULO));
      assertNull(updated.getCache());
      assertNotSame(updated, router);

      // Modify additional configuration
      ConnectionRouterConfiguration connectionRouterConfigurationUpdate2 = new ConnectionRouterConfiguration();
      connectionRouterConfigurationUpdate2.setName("partition-local-consistent-hash-1").setKeyType(KeyType.CLIENT_ID).setLocalTargetFilter(String.valueOf(2));
      NamedPropertyConfiguration policyConfigUpdated2 = new NamedPropertyConfiguration()
         .setName(ConsistentHashModuloPolicy.NAME)
         .setProperties(Collections.singletonMap(ConsistentHashModuloPolicy.MODULO, String.valueOf(3)));
      connectionRouterConfigurationUpdate2.setPolicyConfiguration(policyConfigUpdated2);
      CacheConfiguration cacheConfig2 = new CacheConfiguration()
         .setPersisted(false)
         .setTimeout(5_000);
      connectionRouterConfigurationUpdate2.setCacheConfiguration(cacheConfig2);

      configurations.clear();
      configurations.add(connectionRouterConfigurationUpdate2);

      underTest.update(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration.getName());

      final ConnectionRouter updated2 = underTest.getRouter("partition-local-consistent-hash-1");

      assertEquals("3", updated2.getPolicy().getProperties().get(ConsistentHashModuloPolicy.MODULO));
      assertNotNull(updated2.getCache());

      assertNotSame(updated2, router);
      assertNotSame(updated2, updated);

      underTest.stop();

      Mockito.verify(mockManagementService, Mockito.times(2)).unregisterConnectionRouter(connectionRouterConfiguration.getName());
      Mockito.verifyNoMoreInteractions(mockManagementService);
   }

   @Test
   public void deployedPolicyNotUpdatedOnUpdate() throws Exception {

      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      List<ConnectionRouterConfiguration> configurations = new ArrayList<>();

      Configuration mockConfiguration = Mockito.mock(Configuration.class);
      Mockito.when(mockConfiguration.getConnectionRouters()).thenReturn(configurations);

      CacheConfiguration cacheConfig1 = new CacheConfiguration();
      cacheConfig1.setPersisted(false);
      cacheConfig1.setTimeout(1000);
      ConnectionRouterConfiguration connectionRouterConfiguration1 = new ConnectionRouterConfiguration();
      connectionRouterConfiguration1.setName("partition-local-pool");
      connectionRouterConfiguration1.setCacheConfiguration(cacheConfig1);

      configurations.add(connectionRouterConfiguration1);

      underTest.deploy(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);
      Mockito.clearInvocations(mockManagementService);

      CacheConfiguration cacheConfig2 = new CacheConfiguration();
      cacheConfig2.setPersisted(false);
      cacheConfig2.setTimeout(1000);
      ConnectionRouterConfiguration connectionRouterConfiguration2 = new ConnectionRouterConfiguration();
      connectionRouterConfiguration2.setName("partition-local-pool");
      connectionRouterConfiguration2.setCacheConfiguration(cacheConfig2);

      configurations.clear();
      configurations.add(connectionRouterConfiguration2);

      underTest.update(mockConfiguration); // Different object but no change in configuration

      Mockito.verifyNoInteractions(mockManagementService);

      CacheConfiguration cacheConfig3 = new CacheConfiguration();
      cacheConfig3.setPersisted(false);
      cacheConfig3.setTimeout(2000);
      ConnectionRouterConfiguration connectionRouterConfiguration3 = new ConnectionRouterConfiguration();
      connectionRouterConfiguration3.setName("partition-local-pool");
      connectionRouterConfiguration3.setCacheConfiguration(cacheConfig3);

      configurations.clear();
      configurations.add(connectionRouterConfiguration3);

      underTest.update(mockConfiguration); // Small change requires remove and replace

      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration3.getName());
      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);
      Mockito.clearInvocations(mockManagementService);
   }

   @Test
   public void deployWithPoolConfigurationAndUpdateIt() throws Exception {
      ManagementService mockManagementService = Mockito.mock(ManagementService.class);
      Mockito.when(mockServer.getManagementService()).thenReturn(mockManagementService);

      List<ConnectionRouterConfiguration> configurations = new ArrayList<>();

      Configuration mockConfiguration = Mockito.mock(Configuration.class);
      Mockito.when(mockConfiguration.getConnectionRouters()).thenReturn(configurations);
      Mockito.when(mockServer.getConfiguration()).thenReturn(mockConfiguration);

      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();
      connectionRouterConfiguration.setName("test-local-pool");
      NamedPropertyConfiguration policyConfig = new NamedPropertyConfiguration();
      policyConfig.setName(ConsistentHashPolicy.NAME);
      connectionRouterConfiguration.setPolicyConfiguration(policyConfig);

      PoolConfiguration poolConfiguration = new PoolConfiguration();
      poolConfiguration.setLocalTargetEnabled(true);
      poolConfiguration.setStaticConnectors(List.of("connector1"));
      poolConfiguration.setCheckPeriod(10);
      poolConfiguration.setQuorumSize(1);
      connectionRouterConfiguration.setPoolConfiguration(poolConfiguration);

      configurations.add(connectionRouterConfiguration);

      underTest.deploy(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verifyNoMoreInteractions(mockManagementService);
      Mockito.clearInvocations(mockManagementService);

      final ConnectionRouter router1 = underTest.getRouter("test-local-pool");
      final Pool pool1 = router1.getPool();

      assertEquals(10, pool1.getCheckPeriod());
      assertEquals(1, pool1.getQuorumSize());

      ConnectionRouterConfiguration connectionRouterConfigurationUpdate = new ConnectionRouterConfiguration();
      connectionRouterConfigurationUpdate.setName("test-local-pool");
      NamedPropertyConfiguration policyConfigUpdate = new NamedPropertyConfiguration();
      policyConfigUpdate.setName(ConsistentHashPolicy.NAME);
      connectionRouterConfigurationUpdate.setPolicyConfiguration(policyConfigUpdate);

      PoolConfiguration poolConfigurationUpdate = new PoolConfiguration();
      poolConfigurationUpdate.setLocalTargetEnabled(true);
      poolConfigurationUpdate.setStaticConnectors(List.of("connector1"));
      poolConfigurationUpdate.setCheckPeriod(100);
      poolConfigurationUpdate.setQuorumSize(20);
      connectionRouterConfigurationUpdate.setPoolConfiguration(poolConfigurationUpdate);

      configurations.clear();
      configurations.add(connectionRouterConfigurationUpdate);

      underTest.update(mockConfiguration);

      Mockito.verify(mockManagementService, Mockito.times(1)).registerConnectionRouter(Mockito.any());
      Mockito.verify(mockManagementService, Mockito.times(1)).unregisterConnectionRouter(connectionRouterConfiguration.getName());
      Mockito.verifyNoMoreInteractions(mockManagementService);
      Mockito.clearInvocations(mockManagementService);

      final ConnectionRouter router2 = underTest.getRouter("test-local-pool");
      final Pool pool2 = router2.getPool();

      assertNotSame(pool1, pool2);
      assertEquals(100, pool2.getCheckPeriod());
      assertEquals(20, pool2.getQuorumSize());

      underTest.stop();
   }
}
