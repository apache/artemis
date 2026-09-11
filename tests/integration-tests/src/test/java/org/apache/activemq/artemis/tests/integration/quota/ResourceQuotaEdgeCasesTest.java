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
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaService;
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
 * Tests for edge cases and boundary conditions in resource quota enforcement.
 */
public class ResourceQuotaEdgeCasesTest extends ActiveMQTestBase {

   @Test
   public void testZeroLimitEnforcement() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with zero limits - should reject immediately
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("zero-quota");
      quotaConfig.setMaxAddresses(0);
      quotaConfig.setMaxQueues(0);
      config.addResourceQuota("zero-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("zero-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // First address should fail with zero limit
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST))
         );
         assertTrue(exception.getMessage().contains("Address quota exceeded"));
         assertTrue(exception.getMessage().contains("max addresses is 0"));

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("zero-quota");
         assertNotNull(quota);

         assertEquals(0, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testNegativeLimitMeansUnlimited() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Negative limits mean unlimited
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("unlimited-quota");
      quotaConfig.setMaxAddresses(-1);
      quotaConfig.setMaxQueues(-1);
      config.addResourceQuota("unlimited-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("unlimited-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Should be able to create many addresses
         for (int i = 0; i < 100; i++) {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST));
         }

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("unlimited-quota");
         assertNotNull(quota);

         assertEquals(100, quota.getAddressCount());

         // Create first address to attach queues to
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.queues"), RoutingType.ANYCAST));

         // Should be able to create many queues
         for (int i = 0; i < 50; i++) {
            server.createQueue(QueueConfiguration.of("queue" + i).setAddress("test.queues"));
         }

         assertEquals(50, quota.getQueueCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testNullLimitMeansUnlimited() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Null limits (not set) mean unlimited
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("null-quota");
      // Don't set maxAddresses or maxQueues - they default to null
      config.addResourceQuota("null-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("null-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Should be able to create many addresses
         for (int i = 0; i < 50; i++) {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST));
         }

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("null-quota");
         assertNotNull(quota);

         assertEquals(50, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testAddressDeletionDecrementsQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create 5 addresses (at limit)
         for (int i = 1; i <= 5; i++) {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST));
         }

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(5, quota.getAddressCount());

         // Should be at limit
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr6"), RoutingType.ANYCAST))
         );

         // Remove 2 addresses
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);
         assertEquals(4, quota.getAddressCount());

         server.removeAddressInfo(SimpleString.of("test.addr2"), null);
         assertEquals(3, quota.getAddressCount());

         // Verify addr6 doesn't exist yet
         AddressInfo addr6Before = server.getAddressInfo(SimpleString.of("test.addr6"));
         assertTrue(addr6Before == null, "addr6 should not exist before creation");

         // Should be able to create 2 more
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr6"), RoutingType.ANYCAST));

         // Verify addr6 was actually created
         AddressInfo addr6After = server.getAddressInfo(SimpleString.of("test.addr6"));
         assertNotNull(addr6After, "addr6 should exist after creation");

         assertEquals(4, quota.getAddressCount(), "Quota should be 4 after creating addr6");

         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr7"), RoutingType.ANYCAST));
         assertEquals(5, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testQueueDeletionDecrementsQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxQueues(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create address
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr"), RoutingType.ANYCAST));

         // Create 5 queues (at limit)
         for (int i = 1; i <= 5; i++) {
            server.createQueue(QueueConfiguration.of("queue" + i).setAddress("test.addr"));
         }

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(5, quota.getQueueCount());

         // Should be at limit
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.createQueue(QueueConfiguration.of("queue6").setAddress("test.addr"))
         );

         // Destroy 2 queues
         server.destroyQueue(SimpleString.of("queue1"));
         assertEquals(4, quota.getQueueCount());

         server.destroyQueue(SimpleString.of("queue2"));
         assertEquals(3, quota.getQueueCount());

         // Should be able to create 2 more
         server.createQueue(QueueConfiguration.of("queue6").setAddress("test.addr"));
         assertEquals(4, quota.getQueueCount());

         server.createQueue(QueueConfiguration.of("queue7").setAddress("test.addr"));
         assertEquals(5, quota.getQueueCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testDuplicateAddressRollback() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create first address
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(1, quota.getAddressCount());

         // Try to create duplicate - should fail and rollback quota
         try {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));
         } catch (Exception e) {
            // Expected - duplicate address
         }

         // Quota should still be 1 (rollback worked)
         assertEquals(1, quota.getAddressCount());

         // Should still have capacity
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST));
         assertEquals(2, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testHierarchyWithZeroParentLimit() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Parent with zero limit
      ResourceQuotaConfig parentQuota = new ResourceQuotaConfig("parent");
      parentQuota.setMaxAddresses(0);
      config.addResourceQuota("parent", parentQuota);

      // Child with higher limit but constrained by parent
      ResourceQuotaConfig childQuota = new ResourceQuotaConfig("child");
      childQuota.setMaxAddresses(10);
      childQuota.setPartOf("parent");
      config.addResourceQuota("child", childQuota);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("child");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Should fail immediately due to parent limit of 0
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST))
         );

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("child");
         assertNotNull(quota);

         assertEquals(0, quota.getAddressCount());

         quota = quotaService.getQuotaByName("parent");
         assertNotNull(quota);
         assertEquals(0, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testMixedLimitsInHierarchy() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Parent with address limit but no queue limit
      ResourceQuotaConfig parentQuota = new ResourceQuotaConfig("parent");
      parentQuota.setMaxAddresses(5);
      parentQuota.setMaxQueues(-1); // unlimited
      config.addResourceQuota("parent", parentQuota);

      // Child with queue limit but unlimited addresses
      ResourceQuotaConfig childQuota = new ResourceQuotaConfig("child");
      childQuota.setMaxAddresses(-1); // unlimited (but parent constrains)
      childQuota.setMaxQueues(3);
      childQuota.setPartOf("parent");
      config.addResourceQuota("child", childQuota);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("child");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create 5 addresses (parent limit)
         for (int i = 1; i <= 5; i++) {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST));
         }

         // Sixth address should fail (parent limit)
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr6"), RoutingType.ANYCAST))
         );

         // Create 3 queues on first address (child limit)
         for (int i = 1; i <= 3; i++) {
            server.createQueue(QueueConfiguration.of("queue" + i).setAddress("test.addr1"));
         }

         // Fourth queue should fail (child limit)
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.createQueue(QueueConfiguration.of("queue4").setAddress("test.addr1"))
         );

      } finally {
         server.stop();
      }
   }

   @Test
   public void testQuotaCountNeverGoesNegative() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create an address
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(1, quota.getAddressCount());

         // Remove it
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);
         assertEquals(0, quota.getAddressCount());

         // Try to remove non-existent address (should not decrement below 0)
         try {
            server.removeAddressInfo(SimpleString.of("test.nonexistent"), null);
         } catch (Exception e) {
            // Expected - address doesn't exist
         }

         // Count should still be 0, not negative
         assertTrue(quota.getAddressCount() >= 0);

      } finally {
         server.stop();
      }
   }
}
