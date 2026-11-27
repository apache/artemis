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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResourceQuotaServiceTest extends ActiveMQTestBase {

   @Test
   public void testAddressQuotaCheckAndIncrement() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(3);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         assertNotNull(quotaService);
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         // Check quota before creating address
         quotaService.checkAddressQuota(SimpleString.of("test.addr1"));
         assertEquals(0, quota.getAddressCount());

         // Increment after successful creation
         quotaService.incrementAddressCount(SimpleString.of("test.addr1"));
         assertEquals(1, quota.getAddressCount());

         // Check and increment again
         quotaService.checkAddressQuota(SimpleString.of("test.addr2"));
         quotaService.incrementAddressCount(SimpleString.of("test.addr2"));
         assertEquals(2, quota.getAddressCount());

         // Decrement after removal
         quotaService.decrementAddressCount(SimpleString.of("test.addr1"));
         assertEquals(1, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testQueueQuotaCheckAndIncrement() throws Exception {
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
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         // Check quota before creating queue
         quotaService.checkQueueQuota(SimpleString.of("test.addr"));
         assertEquals(0, quota.getQueueCount());

         // Increment after successful creation
         quotaService.incrementQueueCount(SimpleString.of("test.addr"));
         assertEquals(1, quota.getQueueCount());

         // Check and increment again
         quotaService.checkQueueQuota(SimpleString.of("test.addr"));
         quotaService.incrementQueueCount(SimpleString.of("test.addr"));
         assertEquals(2, quota.getQueueCount());

         // Decrement after removal
         quotaService.decrementQueueCount(SimpleString.of("test.addr"));
         assertEquals(1, quota.getQueueCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testEndToEndAddressCreation() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(3);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         // Create addresses through normal API (uses tokens internally)
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));
         assertEquals(1, quota.getAddressCount());

         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST));
         assertEquals(2, quota.getAddressCount());

         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST));
         assertEquals(3, quota.getAddressCount());

         // Fourth should fail
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST))
         );

         // Remove one
         server.removeAddressInfo(SimpleString.of("test.addr1"), null);
         assertEquals(2, quota.getAddressCount());

         // Should be able to create another
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST));
         assertEquals(3, quota.getAddressCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testEndToEndQueueCreationWithTokens() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxQueues(3);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create address first
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr"), RoutingType.ANYCAST));

         // Create queues through normal API (uses tokens internally)
         server.createQueue(QueueConfiguration.of("queue1").setAddress("test.addr"));

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(1, quota.getQueueCount());

         server.createQueue(QueueConfiguration.of("queue2").setAddress("test.addr"));
         assertEquals(2, quota.getQueueCount());

         server.createQueue(QueueConfiguration.of("queue3").setAddress("test.addr"));
         assertEquals(3, quota.getQueueCount());

         // Fourth should fail
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server.createQueue(QueueConfiguration.of("queue4").setAddress("test.addr"))
         );

         // Destroy one
         server.destroyQueue(SimpleString.of("queue1"));
         assertEquals(2, quota.getQueueCount());

         // Should be able to create another
         server.createQueue(QueueConfiguration.of("queue4").setAddress("test.addr"));
         assertEquals(3, quota.getQueueCount());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testReloadAddingSiblingPreservesParentCounts() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Start with Global parent and two children: EU and US
      ResourceQuotaConfig globalConfig = new ResourceQuotaConfig("global");
      globalConfig.setMaxAddresses(100);
      config.addResourceQuota("global", globalConfig);

      ResourceQuotaConfig euConfig = new ResourceQuotaConfig("eu-quota");
      euConfig.setMaxAddresses(50);
      euConfig.setPartOf("global");
      config.addResourceQuota("eu-quota", euConfig);

      ResourceQuotaConfig usConfig = new ResourceQuotaConfig("us-quota");
      usConfig.setMaxAddresses(50);
      usConfig.setPartOf("global");
      config.addResourceQuota("us-quota", usConfig);

      AddressSettings euSettings = new AddressSettings();
      euSettings.setResourceQuota("eu-quota");
      config.addAddressSetting("eu.#", euSettings);

      AddressSettings usSettings = new AddressSettings();
      usSettings.setResourceQuota("us-quota");
      config.addAddressSetting("us.#", usSettings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota global = quotaService.getQuotaByName("global");
         ResourceQuota eu = quotaService.getQuotaByName("eu-quota");
         ResourceQuota us = quotaService.getQuotaByName("us-quota");
         assertNotNull(global);
         assertNotNull(eu);
         assertNotNull(us);

         // Create addresses under both quotas
         server.addAddressInfo(new AddressInfo(SimpleString.of("eu.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("eu.addr2"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("eu.addr3"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("us.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("us.addr2"), RoutingType.ANYCAST));

         assertEquals(3, eu.getAddressCount());
         assertEquals(2, us.getAddressCount());
         assertEquals(5, global.getAddressCount(), "Global should have combined count");

         // Add a new sibling "ap-quota" under the same parent via reload.
         // This new quota goes into newQuotasForRebuild, which resets its parent chain
         // (Global). Only the new quota is rebuilt — EU and US contributions to Global
         // must not be lost.
         ResourceQuotaConfig apConfig = new ResourceQuotaConfig("ap-quota");
         apConfig.setMaxAddresses(50);
         apConfig.setPartOf("global");
         config.addResourceQuota("ap-quota", apConfig);

         AddressSettings apSettings = new AddressSettings();
         apSettings.setResourceQuota("ap-quota");
         config.addAddressSetting("ap.#", apSettings);

         quotaService.reloadQuotas();

         // Re-fetch since reload may recreate objects
         global = quotaService.getQuotaByName("global");
         eu = quotaService.getQuotaByName("eu-quota");
         us = quotaService.getQuotaByName("us-quota");
         ResourceQuota ap = quotaService.getQuotaByName("ap-quota");

         assertNotNull(ap, "New AP quota should exist after reload");
         assertEquals(0, ap.getAddressCount(), "AP has no addresses yet");

         // Existing quotas must be unchanged
         assertEquals(3, eu.getAddressCount(), "EU address count must be preserved");
         assertEquals(2, us.getAddressCount(), "US address count must be preserved");

         // Global must retain ALL children's contributions
         assertEquals(5, global.getAddressCount(),
            "Global must retain EU+US contributions after adding new sibling AP");

      } finally {
         server.stop();
      }
   }

   @Test
   public void testReloadWithWildcardTemplatePreservesParentCounts() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Global parent quota
      ResourceQuotaConfig globalConfig = new ResourceQuotaConfig("global");
      globalConfig.setMaxAddresses(100);
      config.addResourceQuota("global", globalConfig);

      // Wildcard template child — instantiates "region.eu", "region.us" etc.
      ResourceQuotaConfig regionConfig = new ResourceQuotaConfig("region.*");
      regionConfig.setMaxAddresses(50);
      regionConfig.setPartOf("global");
      config.addResourceQuota("region.*", regionConfig);

      // Address settings route region.# addresses to the wildcard template
      AddressSettings regionSettings = new AddressSettings();
      regionSettings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", regionSettings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota global = quotaService.getQuotaByName("global");
         assertNotNull(global);

         // Create addresses that trigger wildcard instantiation:
         //   "region.eu.addr1" → instantiated quota "region.eu" with parent=global
         //   "region.us.addr1" → instantiated quota "region.us" with parent=global
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr1"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.addr2"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.addr1"), RoutingType.ANYCAST));

         assertEquals(3, global.getAddressCount(),
            "Global should have combined count from instantiated wildcard children");

         // Add a new direct child quota under global via reload.
         // This triggers resetQuotaCounters on global (parent chain reset).
         // The sibling rebuild walks getAllQuotas() which only returns repository
         // quotas — instantiated wildcard children are missed, so their
         // contributions to global are lost.
         ResourceQuotaConfig newChildConfig = new ResourceQuotaConfig("new-child");
         newChildConfig.setMaxAddresses(20);
         newChildConfig.setPartOf("global");
         config.addResourceQuota("new-child", newChildConfig);

         quotaService.reloadQuotas();

         // Re-fetch in case reload recreated objects
         global = quotaService.getQuotaByName("global");

         // Global must still reflect the instantiated wildcard children
         assertEquals(3, global.getAddressCount(),
            "Global must retain instantiated wildcard children's contributions after reload");

      } finally {
         server.stop();
      }
   }

   @Test
   public void testReloadUpdatesQuotaOnPagingStore() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Start with quotaA assigned to "test.#" addresses
      ResourceQuotaConfig quotaAConfig = new ResourceQuotaConfig("quotaA");
      quotaAConfig.setMaxAddresses(10);
      config.addResourceQuota("quotaA", quotaAConfig);

      ResourceQuotaConfig quotaBConfig = new ResourceQuotaConfig("quotaB");
      quotaBConfig.setMaxAddresses(20);
      config.addResourceQuota("quotaB", quotaBConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("quotaA");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         // Create an address to establish its PagingStore
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST));

         ResourceQuota quotaA = server.getResourceQuotaService().getQuotaByName("quotaA");
         ResourceQuota quotaB = server.getResourceQuotaService().getQuotaByName("quotaB");

         // Verify PagingStore initially references quotaA
         org.apache.activemq.artemis.core.paging.PagingStore pagingStore =
            server.getPagingManager().getPageStore(SimpleString.of("test.addr1"));
         assertSame(quotaA, pagingStore.getResourceQuota(),
            "PagingStore should initially reference quotaA");

         // Change the live address-settings repository to reference quotaB instead
         AddressSettings newSettings = new AddressSettings();
         newSettings.setResourceQuota("quotaB");
         server.getAddressSettingsRepository().addMatch("test.#", newSettings);

         // PagingStore must now reference quotaB
         assertSame(quotaB, pagingStore.getResourceQuota(),
            "PagingStore must reference quotaB after reload reassigns the address");

      } finally {
         server.stop();
      }
   }
}
