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
package org.apache.activemq.artemis.core.paging.impl;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test to verify that paging stores are properly cleaned up when addresses are removed.
 * This test reproduces and verifies the fix for the issue where paging stores for
 * removed addresses (including BRIDGE.* addresses) were not being cleaned up properly.
 */
public class PagingStoreCleanupTest {

   @Mock
   private ActiveMQServer server;

   @Mock
   private PagingStore pagingStore1;

   @Mock
   private PagingStore pagingStore2;

   @Mock
   private PagingStore pagingStore3;

   private PagingManagerImpl pagingManager;
   private ConcurrentMap<SimpleString, PagingStore> stores;

   @BeforeEach
   public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this);

      // Create a minimal PagingManagerImpl instance
      pagingManager = mock(PagingManagerImpl.class);

      // Access the stores field via reflection
      Field storesField = PagingManagerImpl.class.getDeclaredField("stores");
      storesField.setAccessible(true);
      stores = new ConcurrentHashMap<>();
      storesField.set(pagingManager, stores);

      // Access the server field via reflection
      Field serverField = PagingManagerImpl.class.getDeclaredField("server");
      serverField.setAccessible(true);
      serverField.set(pagingManager, server);

      // Access the syncLock field via reflection
      Field syncLockField = PagingManagerImpl.class.getDeclaredField("syncLock");
      syncLockField.setAccessible(true);
      syncLockField.set(pagingManager, new ReentrantReadWriteLock());

      // Mock deletePageStore to just remove from the stores map
      doAnswer(invocation -> {
         SimpleString address = invocation.getArgument(0);
         stores.remove(address);
         return null;
      }).when(pagingManager).deletePageStore(any(SimpleString.class));

      // Call the real method for cleanupOrphanedPageStores
      when(pagingManager.cleanupOrphanedPageStores()).thenCallRealMethod();
   }

   @Test
   public void testOrphanedPagingStoresAreCleanedUp() throws Exception {
      // Arrange: Create paging stores for addresses
      SimpleString address1 = SimpleString.of("test.address.1");
      SimpleString address2 = SimpleString.of("test.address.2");
      SimpleString bridgeAddress = SimpleString.of("BRIDGE.test.address.3");

      stores.put(address1, pagingStore1);
      stores.put(address2, pagingStore2);
      stores.put(bridgeAddress, pagingStore3);

      // Mock server.getAddressInfo() to simulate that only address1 still exists
      when(server.getAddressInfo(address1)).thenReturn(mock(org.apache.activemq.artemis.core.server.impl.AddressInfo.class));
      when(server.getAddressInfo(address2)).thenReturn(null); // address2 was removed
      when(server.getAddressInfo(bridgeAddress)).thenReturn(null); // BRIDGE address was removed

      // Act: Call the cleanup method
      int removed = pagingManager.cleanupOrphanedPageStores();

      // Assert: Should remove 2 orphaned stores (address2 and bridgeAddress)
      assertEquals(2, removed, "Should remove 2 orphaned paging stores");

      // Verify the stores map state - address1 should remain, others removed
      assertTrue(stores.containsKey(address1), "address1 store should still exist");
      assertFalse(stores.containsKey(address2), "address2 store should be removed");
      assertFalse(stores.containsKey(bridgeAddress), "BRIDGE address store should be removed");
   }

   @Test
   public void testMultipleBridgeStoresCleanup() throws Exception {
      // Arrange: Create multiple BRIDGE.* paging stores
      SimpleString bridgeAddress1 = SimpleString.of("BRIDGE.cluster.address.1");
      SimpleString bridgeAddress2 = SimpleString.of("BRIDGE.cluster.address.2");
      SimpleString bridgeAddress3 = SimpleString.of("BRIDGE.cluster.address.3");

      stores.put(bridgeAddress1, pagingStore1);
      stores.put(bridgeAddress2, pagingStore2);
      stores.put(bridgeAddress3, pagingStore3);

      // Mock server.getAddressInfo() to simulate all BRIDGE addresses were removed
      when(server.getAddressInfo(any())).thenReturn(null);

      // Act: Call the cleanup method
      int removed = pagingManager.cleanupOrphanedPageStores();

      // Assert: Should remove all 3 BRIDGE stores
      assertEquals(3, removed, "Should remove all 3 orphaned BRIDGE paging stores");

      // Verify all BRIDGE stores are removed
      assertTrue(stores.isEmpty(), "All BRIDGE stores should be removed");
   }
}

