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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceQuotaIntegrationTest extends ActiveMQTestBase {

   @Test
   public void testAddressQuotaEnforcement() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with max 2 addresses
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(2);
      config.addResourceQuota("test-quota", quotaConfig);

      // Configure address settings to use this quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create first address - should succeed
         AddressInfo addr1 = new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST);
         server.addAddressInfo(addr1);

         // Create second address - should succeed
         AddressInfo addr2 = new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST);
         server.addAddressInfo(addr2);

         // Create third address - should fail due to quota
         AddressInfo addr3 = new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST);
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(addr3)
         );
         assertTrue(exception.getMessage().contains("Address quota exceeded"));
         assertTrue(exception.getMessage().contains("test-quota"));

         // Remove one address
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);

         // Now creating addr3 should succeed
         server.addAddressInfo(addr3);

      } finally {
         server.stop();
      }
   }

   @Test
   public void testQueueQuotaEnforcement() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with max 3 queues
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxQueues(3);
      config.addResourceQuota("test-quota", quotaConfig);

      // Configure address settings to use this quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create address first
         AddressInfo addr = new AddressInfo(SimpleString.of("test.addr"), RoutingType.ANYCAST);
         server.addAddressInfo(addr);

         // Create three queues - should all succeed
         server.createQueue(QueueConfiguration.of("queue1").setAddress("test.addr"));
         server.createQueue(QueueConfiguration.of("queue2").setAddress("test.addr"));
         server.createQueue(QueueConfiguration.of("queue3").setAddress("test.addr"));

         // Create fourth queue - should fail due to quota
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.createQueue(QueueConfiguration.of("queue4").setAddress("test.addr"))
         );
         assertTrue(exception.getMessage().contains("Queue quota exceeded"));
         assertTrue(exception.getMessage().contains("test-quota"));

         // Destroy one queue
         server.destroyQueue(SimpleString.of("queue1"));

         // Now creating queue4 should succeed
         server.createQueue(QueueConfiguration.of("queue4").setAddress("test.addr"));

      } finally {
         server.stop();
      }
   }

   @Test
   public void testConcurrentAddressCreationWithQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with max 10 addresses
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(10);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         int numThreads = 20;
         int successCount = 0;
         int failureCount = 0;

         Thread[] threads = new Thread[numThreads];
         boolean[] results = new boolean[numThreads];

         for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
               try {
                  AddressInfo addr = new AddressInfo(
                     SimpleString.of("test.concurrent.addr" + index),
                     RoutingType.ANYCAST
                  );
                  server.addAddressInfo(addr);
                  results[index] = true;
               } catch (Exception e) {
                  assertInstanceOf(ActiveMQResourceQuotaExceededException.class, e);
                  results[index] = false;
               }
            });
         }

         // Start all threads
         for (Thread thread : threads) {
            thread.start();
         }

         // Wait for all threads
         for (Thread thread : threads) {
            thread.join();
         }

         // Count successes and failures
         for (boolean result : results) {
            if (result) {
               successCount++;
            } else {
               failureCount++;
            }
         }

         // Exactly 10 should succeed due to quota limit
         assertEquals(10, successCount, "Expected exactly 10 successful address creations");
         assertEquals(10, failureCount, "Expected exactly 10 failed address creations");

      } finally {
         server.stop();
      }
   }

   @Test
   public void testQuotaRollbackOnDuplicate() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with max 5 addresses
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create 3 addresses
         for (int i = 1; i <= 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            server.addAddressInfo(addr);
         }

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota, "Quota 'test-quota' should exist");

         // Verify quota count is 3
         assertEquals(3, quota.getAddressCount());

         // Try to create an address that already exists (duplicate)
         AddressInfo addr1Duplicate = new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST);
         boolean duplicateResult = server.addAddressInfo(addr1Duplicate);
         // Should return false for duplicate
         assertFalse(duplicateResult, "Duplicate address should return false");

         // Quota count should still be 3 (no change for duplicate)
         assertEquals(3, quota.getAddressCount());

         // We should still be able to create 2 more addresses
         AddressInfo addr4 = new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST);
         server.addAddressInfo(addr4);

         AddressInfo addr5 = new AddressInfo(SimpleString.of("test.addr5"), RoutingType.ANYCAST);
         server.addAddressInfo(addr5);

         assertEquals(5, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testHierarchicalQuotaEnforcement() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create parent quota
      ResourceQuotaConfig parentQuota = new ResourceQuotaConfig("parent");
      parentQuota.setMaxAddresses(5);
      config.addResourceQuota("parent", parentQuota);

      // Create child quota with higher limit but part of parent
      ResourceQuotaConfig childQuota = new ResourceQuotaConfig("child");
      childQuota.setMaxAddresses(10);
      childQuota.setPartOf("parent");
      config.addResourceQuota("child", childQuota);

      // Configure address settings to use child quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("child");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Establish parent relationship (normally done by ResourceQuotaManager)
         // childQuota.setParent(parentQuota);

         // Create 5 addresses - should succeed (parent limit)
         for (int i = 1; i <= 5; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            server.addAddressInfo(addr);
         }

         // Sixth address should fail due to parent limit (even though child limit is 10)
         AddressInfo addr6 = new AddressInfo(SimpleString.of("test.addr6"), RoutingType.ANYCAST);
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(addr6)
         );
         assertTrue(exception.getMessage().contains("quota exceeded"));

      } finally {
         server.stop();
      }
   }

   @Test
   public void testNoQuotaConfigured() throws Exception {
      Configuration config = createDefaultConfig(false);

      // No quota configured
      AddressSettings settings = new AddressSettings();
      // resourceQuota not set
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Should be able to create many addresses without quota
         for (int i = 1; i <= 100; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            server.addAddressInfo(addr);
         }

         // All should succeed - no quota enforcement
         // Verify first and last addresses exist
         assertNotNull(server.getAddressInfo(SimpleString.of("test.addr1")));
         assertNotNull(server.getAddressInfo(SimpleString.of("test.addr100")));

      } finally {
         server.stop();
      }
   }
}
