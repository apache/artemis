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
package org.apache.activemq.artemis.core.postoffice.impl;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.postoffice.AddressManager;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test to verify that duplicate ID caches (including BRIDGE.* caches) are properly cleaned up
 * when addresses are removed. This test reproduces and verifies the fix for the issue where
 * BRIDGE.* endpoint caches were not being cleaned up properly.
 */
public class DuplicateIDCacheCleanupTest {

   @Mock
   private ActiveMQServer server;

   @Mock
   private AddressManager addressManager;

   @Mock
   private PagingManager pagingManager;

   @Mock
   private QueueFactory queueFactory;

   @Mock
   private ManagementService managementService;

   @Mock
   private HierarchicalRepository<AddressSettings> addressSettingsRepository;

   @Mock
   private ClusterManager clusterManager;

   @Mock
   private ClusterConnection clusterConnection;

   @Mock
   private Bridge bridge;

   @Mock
   private BridgeConfiguration bridgeConfiguration;

   private PostOfficeImpl postOffice;

   @BeforeEach
   public void setUp() {
      MockitoAnnotations.openMocks(this);
      when(addressSettingsRepository.getMatch(anyString())).thenReturn(new AddressSettings());

      // Mock cluster manager and bridge setup to enable duplicate detection
      when(server.getClusterManager()).thenReturn(clusterManager);
      when(clusterManager.getClusterConnections()).thenReturn(Collections.singleton(clusterConnection));
      when(clusterConnection.getBridges()).thenReturn(new Bridge[]{bridge});
      when(bridge.getConfiguration()).thenReturn(bridgeConfiguration);
      when(bridgeConfiguration.isUseDuplicateDetection()).thenReturn(true);

      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();

      postOffice = new PostOfficeImpl(
         server,
         null,  // storageManager
         pagingManager,
         queueFactory,
         managementService,
         1000,  // expiryReaperPeriod
         30000, // addressQueueReaperPeriod
         wildcardConfiguration,
         100,   // idCacheSize
         false, // persistIDCache
         addressSettingsRepository
      );
   }

   /**
    * This test reproduces the issue: BRIDGE.* caches remain in memory after
    * their corresponding addresses are removed.
    */
   @Test
   public void testBridgeCachesOrphanedWhenAddressRemoved() throws Exception {
      SimpleString address = SimpleString.of("bridge.endpoint");
      SimpleString bridgeCache = PostOfficeImpl.BRIDGE_CACHE_STR.concat(address.toString());

      // Initially, the address exists
      Set<SimpleString> addresses = new HashSet<>();
      addresses.add(address);
      when(addressManager.getAddresses()).thenReturn(addresses);

      // Create caches for the address
      ConcurrentMap<SimpleString, DuplicateIDCache> caches = postOffice.getDuplicateIDCaches();
      caches.put(address, mock(DuplicateIDCache.class));
      caches.put(bridgeCache, mock(DuplicateIDCache.class));

      assertEquals(2, caches.size(), "Should have 2 caches (regular + BRIDGE)");

      // Simulate address being removed - now address no longer exists
      addresses.clear();
      when(addressManager.getAddresses()).thenReturn(addresses);

      // BEFORE THE FIX: Caches would remain orphaned in memory
      // WITH THE FIX: cleanupOrphanedDuplicateIDCaches() removes them
      int removed = postOffice.cleanupOrphanedDuplicateIDCaches();

      // Verify both caches are cleaned up
      assertEquals(2, removed, "Should remove both regular and BRIDGE caches");
      assertEquals(0, caches.size(), "Should have 0 caches remaining");
      assertFalse(caches.containsKey(address), "Regular cache should be removed");
      assertFalse(caches.containsKey(bridgeCache), "BRIDGE cache should be removed");
   }


   /**
    * This test verifies the BRIDGE. prefix extraction logic
    */
   @Test
   public void testBridgePrefixHandling() throws Exception {
      SimpleString address = SimpleString.of("my.address");
      SimpleString bridgeAddress = PostOfficeImpl.BRIDGE_CACHE_STR.concat(address.toString());

      // Address doesn't exist
      Set<SimpleString> addresses = new HashSet<>();
      when(addressManager.getAddresses()).thenReturn(addresses);

      // Add BRIDGE cache
      ConcurrentMap<SimpleString, DuplicateIDCache> caches = postOffice.getDuplicateIDCaches();
      caches.put(bridgeAddress, mock(DuplicateIDCache.class));

      // Cleanup should remove it since the base address doesn't exist
      int removed = postOffice.cleanupOrphanedDuplicateIDCaches();

      assertEquals(1, removed, "Should remove BRIDGE cache");
      assertFalse(caches.containsKey(bridgeAddress), "BRIDGE cache should be removed");
   }

   /**
    * This test simulates the real-world scenario: multiple bridge endpoints
    * being created and removed over time
    */
   @Test
   public void testMultipleBridgeEndpointsCleanup() throws Exception {
      // Simulate 5 bridge endpoints
      Set<SimpleString> addresses = new HashSet<>();
      ConcurrentMap<SimpleString, DuplicateIDCache> caches = postOffice.getDuplicateIDCaches();

      for (int i = 0; i < 5; i++) {
         SimpleString address = SimpleString.of("BRIDGE.endpoint." + i);
         SimpleString bridgeCache = PostOfficeImpl.BRIDGE_CACHE_STR.concat(address.toString());

         addresses.add(address);
         caches.put(address, mock(DuplicateIDCache.class));
         caches.put(bridgeCache, mock(DuplicateIDCache.class));
      }

      when(addressManager.getAddresses()).thenReturn(addresses);
      assertEquals(10, caches.size(), "Should have 10 caches (5 regular + 5 BRIDGE)");

      // Now remove all addresses (simulating bridge shutdown)
      addresses.clear();
      when(addressManager.getAddresses()).thenReturn(addresses);

      // Cleanup should remove all orphaned caches
      int removed = postOffice.cleanupOrphanedDuplicateIDCaches();

      assertEquals(10, removed, "Should remove all 10 caches");
      assertEquals(0, caches.size(), "Should have 0 caches remaining");
   }
}
