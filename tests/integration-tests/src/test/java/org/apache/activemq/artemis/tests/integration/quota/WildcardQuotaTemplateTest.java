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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for wildcard quota template functionality.
 * Verifies that quota templates like "region.*" create separate instances
 * for each wildcard value (e.g., "region.us", "region.eu").
 */
public class WildcardQuotaTemplateTest extends ActiveMQTestBase {

   @Test
   public void testWildcardQuotaTemplateInstantiation() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create wildcard template quota "region.*" with max 3 addresses
      ResourceQuotaConfig regionTemplate = new ResourceQuotaConfig("region.*");
      regionTemplate.setMaxAddresses(3);
      config.addResourceQuota("region.*", regionTemplate);

      // Configure addresses matching "region.#" to use the wildcard quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create addresses in region.us - should instantiate "region.us" quota
         AddressInfo usAddr1 = new AddressInfo(SimpleString.of("region.us.orders"), RoutingType.ANYCAST);
         server.addAddressInfo(usAddr1);

         AddressInfo usAddr2 = new AddressInfo(SimpleString.of("region.us.payments"), RoutingType.ANYCAST);
         server.addAddressInfo(usAddr2);

         AddressInfo usAddr3 = new AddressInfo(SimpleString.of("region.us.shipping"), RoutingType.ANYCAST);
         server.addAddressInfo(usAddr3);

         // Fourth address in region.us should fail (limit is 3 per region)
         AddressInfo usAddr4 = new AddressInfo(SimpleString.of("region.us.inventory"), RoutingType.ANYCAST);
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(usAddr4)
         );
         assertTrue(exception.getMessage().contains("Address quota exceeded"));

         // Create addresses in region.eu - should instantiate separate "region.eu" quota
         AddressInfo euAddr1 = new AddressInfo(SimpleString.of("region.eu.orders"), RoutingType.ANYCAST);
         server.addAddressInfo(euAddr1);

         AddressInfo euAddr2 = new AddressInfo(SimpleString.of("region.eu.payments"), RoutingType.ANYCAST);
         server.addAddressInfo(euAddr2);

         AddressInfo euAddr3 = new AddressInfo(SimpleString.of("region.eu.shipping"), RoutingType.ANYCAST);
         server.addAddressInfo(euAddr3);

         // Fourth address in region.eu should also fail (separate quota instance)
         AddressInfo euAddr4 = new AddressInfo(SimpleString.of("region.eu.inventory"), RoutingType.ANYCAST);
         exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(euAddr4)
         );
         assertTrue(exception.getMessage().contains("Address quota exceeded"));

         // Verify we can create addresses in another region (region.asia)
         AddressInfo asiaAddr1 = new AddressInfo(SimpleString.of("region.asia.orders"), RoutingType.ANYCAST);
         server.addAddressInfo(asiaAddr1);

      } finally {
         server.stop();
      }
   }

   @Test
   public void testWildcardQuotaIsolation() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig regionTemplate = new ResourceQuotaConfig("region.*");
      regionTemplate.setMaxAddresses(2);
      config.addResourceQuota("region.*", regionTemplate);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Fill region.us quota (2 addresses)
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr2"), RoutingType.ANYCAST));

         // region.us should be full
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr3"), RoutingType.ANYCAST))
         );

         // region.eu should still have capacity (separate quota instance)
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr2"), RoutingType.ANYCAST));

         // Now region.eu should also be full
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr3"), RoutingType.ANYCAST))
         );

      } finally {
         server.stop();
      }
   }

   @Test
   public void testWildcardQuotaWithParentHierarchy() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create parent quota with total limit of 8 addresses
      ResourceQuotaConfig globalQuota = new ResourceQuotaConfig("global");
      globalQuota.setMaxAddresses(8);
      config.addResourceQuota("global", globalQuota);

      // Create wildcard template that's part of global, each region limited to 3
      ResourceQuotaConfig regionTemplate = new ResourceQuotaConfig("region.*");
      regionTemplate.setMaxAddresses(3);
      regionTemplate.setPartOf("global");
      config.addResourceQuota("region.*", regionTemplate);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create 3 addresses in region.us (hits region limit)
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr2"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr3"), RoutingType.ANYCAST));

         // Fourth in region.us should fail (region limit)
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr4"), RoutingType.ANYCAST))
         );

         // Create 3 addresses in region.eu (total now 6, within global limit of 8)
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr2"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr3"), RoutingType.ANYCAST));

         // Create 2 addresses in region.asia (total now 8, hitting global limit)
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.asia.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.asia.addr2"), RoutingType.ANYCAST));

         // Next address should fail due to parent global limit (even though region.asia has capacity)
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("region.asia.addr3"), RoutingType.ANYCAST))
         );

      } finally {
         server.stop();
      }
   }

   @Test
   public void testConcurrentWildcardInstanceCreation() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig regionTemplate = new ResourceQuotaConfig("region.*");
      regionTemplate.setMaxAddresses(10);
      config.addResourceQuota("region.*", regionTemplate);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         int numThreads = 20;
         int regionsPerThread = 3;

         Thread[] threads = new Thread[numThreads];
         Map<String, AtomicInteger> successCounts = new ConcurrentHashMap<>();

         // Each thread creates addresses in multiple regions concurrently
         for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
               for (int region = 0; region < regionsPerThread; region++) {
                  String regionName = "r" + region;
                  successCounts.putIfAbsent(regionName, new AtomicInteger(0));

                  for (int addr = 0; addr < 15; addr++) {
                     try {
                        AddressInfo address = new AddressInfo(
                           SimpleString.of("region." + regionName + ".t" + threadIndex + "a" + addr),
                           RoutingType.ANYCAST
                        );
                        server.addAddressInfo(address);
                        successCounts.get(regionName).incrementAndGet();
                     } catch (ActiveMQResourceQuotaExceededException e) {
                        // Expected when quota exceeded
                     } catch (Exception e) {
                        e.printStackTrace();
                     }
                  }
               }
            });
         }

         // Start all threads
         for (Thread thread : threads) {
            thread.start();
         }

         // Wait for completion
         for (Thread thread : threads) {
            thread.join();
         }

         // Each region should have exactly 10 successful creates (the quota limit)
         for (int region = 0; region < regionsPerThread; region++) {
            String regionName = "r" + region;
            int count = successCounts.get(regionName).get();
            assertEquals(10, count, "Region " + regionName + " should have exactly 10 addresses");
         }

      } finally {
         server.stop();
      }
   }

   @Test
   public void testWildcardQuotaDeletion() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Use a simple quota (not wildcard) for deletion test
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(2);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Fill quota (2 addresses)
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST));

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(2, quota.getAddressCount());

         // Should be at limit
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST))
         );

         // Remove one address
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);
         assertEquals(1, quota.getAddressCount());

         // Should now be able to create another
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST));
         assertEquals(2, quota.getAddressCount());

         // Should be at limit again
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST))
         );

      } finally {
         server.stop();
      }
   }
}
