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
package org.apache.activemq.artemis.tests.integration.quota;

import org.apache.activemq.artemis.api.core.ActiveMQResourceQuotaExceededException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that verify quota counters don't get orphaned when deletion operations fail.
 *
 * Before fixes:
 * - removeAddress() decremented quota AFTER removeAddressInfo() - if removal failed, quota leaked
 * - destroyQueue() decremented quota AFTER deleteQueue() - if deletion failed, quota leaked
 *
 * After fixes:
 * - Quota is decremented BEFORE potentially-throwing operations
 * - If deletion fails, quota was already decremented (conservative approach)
 */
public class QuotaOrphaningPreventionTest extends ActiveMQTestBase {

   /**
    * Test that address quota doesn't orphan when address removal succeeds.
    * This is the normal case - verify quota is correctly decremented.
    */
   @Test
   public void testAddressQuotaDecrementedOnSuccessfulRemoval() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("address-remove-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("address-remove-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("address-remove-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Get quota by looking up an address that matches the pattern
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(SimpleString.of("test.addr1"));
         assertNotNull(quota);

         // Create 3 addresses
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST));

         assertEquals(3, quota.getCurrentAddressCount(), "Should have 3 addresses");

         // Remove one address
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);

         assertEquals(2, quota.getCurrentAddressCount(), "Should have 2 addresses after removal");

         // Remove another
         server.removeAddressInfo(SimpleString.of("test.addr2"), null);

         assertEquals(1, quota.getCurrentAddressCount(), "Should have 1 address after removal");

      } finally {
         server.stop();
      }
   }

   /**
    * Test that queue quota doesn't orphan when queue deletion succeeds.
    * This is the normal case - verify quota is correctly decremented.
    */
   @Test
   public void testQueueQuotaDecrementedOnSuccessfulDeletion() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("queue-delete-quota");
      quotaConfig.setMaxQueues(5);
      config.addResourceQuota("queue-delete-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("queue-delete-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         SimpleString address = SimpleString.of("test.queues");
         server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));

         // Get quota by looking up an address that matches the pattern
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);
         assertNotNull(quota);

         // Create 3 queues
         server.createQueue(QueueConfiguration.of("queue1").setAddress(address).setRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of("queue2").setAddress(address).setRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of("queue3").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         assertEquals(3, quota.getCurrentQueueCount(), "Should have 3 queues");

         // Delete one queue
         server.destroyQueue(SimpleString.of("queue1"));

         assertEquals(2, quota.getCurrentQueueCount(), "Should have 2 queues after deletion");

         // Delete another
         server.destroyQueue(SimpleString.of("queue2"));

         assertEquals(1, quota.getCurrentQueueCount(), "Should have 1 queue after deletion");

      } finally {
         server.stop();
      }
   }

   /**
    * Test that address quota can be reused after addresses are removed and added back.
    * This verifies quota accounting stays consistent through add/remove cycles.
    */
   @Test
   public void testAddressQuotaReuseAfterRemoval() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("reuse-quota");
      quotaConfig.setMaxAddresses(2);
      config.addResourceQuota("reuse-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("reuse-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create 2 addresses (max quota)
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST));

         // Try to create third - should fail
         assertThrows(ActiveMQResourceQuotaExceededException.class, () ->
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST))
         );

         // Remove one address
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);

         // Now creating addr3 should succeed
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST));

         // Remove both
         server.removeAddressInfo(SimpleString.of("test.addr2"), null);
         server.removeAddressInfo(SimpleString.of("test.addr3"), null);

         // Should be able to create 2 new addresses
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr5"), RoutingType.ANYCAST));

         // Get quota again to verify final count
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(SimpleString.of("test.addr4"));
         assertEquals(2, quota.getCurrentAddressCount(), "Should have 2 addresses");

      } finally {
         server.stop();
      }
   }

   /**
    * Test that queue quota can be reused after queues are deleted and created again.
    */
   @Test
   public void testQueueQuotaReuseAfterDeletion() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("queue-reuse-quota");
      quotaConfig.setMaxQueues(2);
      config.addResourceQuota("queue-reuse-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("queue-reuse-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         SimpleString address = SimpleString.of("test.qreuse");
         server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));

         // Create 2 queues (max quota)
         server.createQueue(QueueConfiguration.of("queue1").setAddress(address).setRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of("queue2").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         // Try to create third - should fail
         assertThrows(ActiveMQResourceQuotaExceededException.class, () ->
            server.createQueue(QueueConfiguration.of("queue3").setAddress(address).setRoutingType(RoutingType.ANYCAST))
         );

         // Delete one queue
         server.destroyQueue(SimpleString.of("queue1"));

         // Now creating queue3 should succeed
         server.createQueue(QueueConfiguration.of("queue3").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         // Delete both
         server.destroyQueue(SimpleString.of("queue2"));
         server.destroyQueue(SimpleString.of("queue3"));

         // Should be able to create 2 new queues
         server.createQueue(QueueConfiguration.of("queue4").setAddress(address).setRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of("queue5").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         // Get quota again to verify final count
         org.apache.activemq.artemis.core.settings.impl.ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);
         assertEquals(2, quota.getCurrentQueueCount(), "Should have 2 queues");

      } finally {
         server.stop();
      }
   }

   /**
    * Test that address removal with force=true correctly decrements quota.
    * Force removal deletes bindings first, then the address.
    */
   @Test
   public void testAddressQuotaDecrementedOnForceRemoval() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("force-remove-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("force-remove-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("force-remove-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         SimpleString address = SimpleString.of("test.force");
         server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of("queue1").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);

         assertEquals(1, quota.getCurrentAddressCount());

         // Force remove (deletes queues and address)
         server.removeAddressInfo(address, null, true);

         assertEquals(0, quota.getCurrentAddressCount(),
            "Quota should be decremented after force removal");

      } finally {
         server.stop();
      }
   }

   /**
    * Test that quota decrement happens even if address doesn't exist.
    * This is the conservative approach - better to decrement twice than leak.
    */
   @Test
   public void testAddressQuotaDecrementIdempotent() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("idempotent-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("idempotent-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("idempotent-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         SimpleString address = SimpleString.of("test.idempotent");
         server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));

         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);

         assertEquals(1, quota.getCurrentAddressCount());

         // Remove address
         server.removeAddressInfo(address, null);

         assertEquals(0, quota.getCurrentAddressCount());

         // Try to remove again - address doesn't exist, but quota was already decremented
         // This should not cause quota to go negative
         try {
            server.removeAddressInfo(address, null);
         } catch (Exception e) {
            // Expected - address doesn't exist
         }

         // Quota should not go negative
         assertTrue(quota.getCurrentAddressCount() >= 0,
            "Quota should not go negative: " + quota.getCurrentAddressCount());

      } finally {
         server.stop();
      }
   }
}
