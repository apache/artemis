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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaManager;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaService;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for runtime resource quota configuration reload.
 */
public class ResourceQuotaReloadTest extends ActiveMQTestBase {

   private static final String BROKER_XML_BOILERPLATE = """
      <?xml version='1.0'?>
      <configuration xmlns="urn:activemq"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="urn:activemq /schema/artemis-configuration.xsd">
         <core xmlns="urn:activemq:core">
            <name>0.0.0.0</name>
            <configuration-file-refresh-period>100</configuration-file-refresh-period>
            <persistence-enabled>false</persistence-enabled>
            <security-enabled>false</security-enabled>
            <journal-type>NIO</journal-type>
            <paging-directory>./target/data/paging</paging-directory>
            <bindings-directory>./target/data/bindings</bindings-directory>
            <journal-directory>./target/data/journal</journal-directory>
            <large-messages-directory>./target/data/large-messages</large-messages-directory>
            <acceptors>
               <acceptor name="in-vm">vm://0</acceptor>
            </acceptors>
      %s
         </core>
      </configuration>
      """;

   private void writeConfig(Path brokerXML, String quotaContent) throws Exception {
      Files.writeString(brokerXML, String.format(BROKER_XML_BOILERPLATE, quotaContent));
   }

   private void reloadConfiguration(EmbeddedActiveMQ server, Path brokerXML,
                                     String quotaContent, ReusableLatch latch) throws Exception {
      latch.setCount(1);
      server.getActiveMQServer().getReloadManager().setTick(latch::countDown);
      writeConfig(brokerXML, quotaContent);
      brokerXML.toFile().setLastModified(System.currentTimeMillis());
      assertTrue(latch.await(10, TimeUnit.SECONDS), "Configuration reload timed out");
   }

   /**
    * Add a new quota at runtime
    * Verifies that a new quota can be added via reload and immediately enforced.
    */
   @Test
   public void testAddQuotaAtRuntime() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="test-quota">
                  <max-addresses>10</max-addresses>
                  <max-queues>20</max-queues>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>test-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         // Verify initial quota exists
         ResourceQuotaService quotaService = embeddedActiveMQ.getActiveMQServer().getResourceQuotaService();
         assertNotNull(quotaService);
         ResourceQuotaManager quotaManager = quotaService.getResourceQuotaManager();
         assertNotNull(quotaManager);

         ResourceQuota initialQuota = quotaManager.getQuota("test-quota");
         assertNotNull(initialQuota, "Initial quota should exist");
         assertEquals(10, initialQuota.getMaxAddresses());

         // Verify new quota does not exist yet
         ResourceQuota newQuota = quotaManager.getQuota("new-quota");
         assertNull(newQuota, "New quota should not exist before reload");

         String addQuotaConfig = """
               <resource-quotas>
                  <resource-quota name="test-quota">
                     <max-addresses>10</max-addresses>
                     <max-queues>20</max-queues>
                  </resource-quota>
                  <resource-quota name="new-quota">
                     <max-addresses>5</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="test.#">
                     <resource-quota>test-quota</resource-quota>
                  </address-setting>
                  <address-setting match="new.#">
                     <resource-quota>new-quota</resource-quota>
                  </address-setting>
               </address-settings>""";

         // Reload configuration with new quota
         reloadConfiguration(embeddedActiveMQ, brokerXML, addQuotaConfig, latch);

         // Verify new quota now exists
         newQuota = quotaManager.getQuota("new-quota");
         assertNotNull(newQuota, "New quota should exist after reload");
         assertEquals(5, newQuota.getMaxAddresses());

         // Verify old quota still exists
         initialQuota = quotaManager.getQuota("test-quota");
         assertNotNull(initialQuota, "Initial quota should still exist");

         // Verify new quota is enforced - create addresses under new.# pattern
         AddressInfo addr1 = new AddressInfo(SimpleString.of("new.addr1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         // Verify counter incremented
         newQuota = quotaManager.getQuota("new-quota");
         assertEquals(1, newQuota.getCurrentAddressCount(), "New quota should track created address");

         // reload again with no change does not update the instance
         reloadConfiguration(embeddedActiveMQ, brokerXML, addQuotaConfig, latch);
         assertSame(newQuota, quotaManager.getQuota("new-quota"));
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Remove quota at runtime
    * Verifies that removing a quota disables enforcement gracefully.
    */
   @Test
   public void testRemoveQuotaAtRuntime() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="test-quota">
                  <max-addresses>10</max-addresses>
                  <max-queues>20</max-queues>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>test-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         // Verify quota exists
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();
         ResourceQuota quota = quotaManager.getQuota("test-quota");
         assertNotNull(quota, "Quota should exist initially");

         // Create an address under quota
         AddressInfo addr1 = new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         // Verify quota tracks it
         quota = quotaManager.getQuota("test-quota");
         assertEquals(1, quota.getCurrentAddressCount());

         // Reload configuration without quota
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <address-settings>
                  <address-setting match="test.#">
                  </address-setting>
               </address-settings>""", latch);

         // Verify quota removed
         quota = quotaManager.getQuota("test-quota");
         assertNull(quota, "Quota should be removed after reload");

         // Verify addresses still work - create more addresses (no quota limit now)
         for (int i = 0; i < 15; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Success - no exception thrown, quota enforcement disabled

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Modify quota limits
    * Verifies that changing quota limits updates enforcement and rebuilds counters.
    */
   @Test
   public void testModifyQuotaLimits() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="test-quota">
                  <max-addresses>10</max-addresses>
                  <max-queues>20</max-queues>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>test-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial limits
         ResourceQuota quota = quotaManager.getQuota("test-quota");
         assertNotNull(quota);
         assertEquals(10, quota.getMaxAddresses());
         assertEquals(20, quota.getMaxQueues());

         // Create 5 addresses
         for (int i = 0; i < 5; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify counter
         quota = quotaManager.getQuota("test-quota");
         assertEquals(5, quota.getCurrentAddressCount());

         // Reload with modified limits
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="test-quota">
                     <max-addresses>20</max-addresses>
                     <max-queues>40</max-queues>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="test.#">
                     <resource-quota>test-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify limits changed
         quota = quotaManager.getQuota("test-quota");
         assertNotNull(quota);
         assertEquals(20, quota.getMaxAddresses(), "Max addresses should be updated to 20");
         assertEquals(40, quota.getMaxQueues(), "Max queues should be updated to 40");

         // Verify counter rebuilt correctly
         assertEquals(5, quota.getCurrentAddressCount(), "Counter should be rebuilt to 5");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Wildcard quota template preserved on reload
    * Verifies wildcard template quotas work after reload with modified limits.
    */
   @Test
   public void testWildcardQuotaTemplatePreservedOnReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="region.*">
                  <max-addresses>15</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="region.#">
                  <resource-quota>region.*</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify wildcard template exists
         ResourceQuota template = quotaManager.getQuota("region.*");
         assertNotNull(template, "Wildcard template should exist");
         assertEquals(15, template.getMaxAddresses());

         // Create addresses that match wildcard pattern
         AddressInfo addr1 = new AddressInfo(SimpleString.of("region.us.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         AddressInfo addr2 = new AddressInfo(SimpleString.of("region.eu.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr2);

         // Reload configuration with modified limits
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="region.*">
                     <max-addresses>25</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="region.#">
                     <resource-quota>region.*</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify wildcard template still exists with updated limits
         template = quotaManager.getQuota("region.*");
         assertNotNull(template, "Wildcard template should exist after reload");
         assertEquals(25, template.getMaxAddresses(), "Max addresses should be updated to 25");

         // Verify can create more addresses
         AddressInfo addr3 = new AddressInfo(SimpleString.of("region.ap.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr3);

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Parent hierarchy reload
    * Verifies parent-child quota relationships work after reload with modified limits.
    */
   @Test
   public void testParentHierarchyReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="tenant1">
                  <part-of>global</part-of>
                  <max-addresses>30</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="tenant1.#">
                  <resource-quota>tenant1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify parent and child quotas exist
         ResourceQuota parent = quotaManager.getQuota("global");
         ResourceQuota child = quotaManager.getQuota("tenant1");
         assertNotNull(parent, "Parent quota should exist");
         assertNotNull(child, "Child quota should exist");

         // Verify parent relationship
         assertEquals("global", child.getPartOf(), "Child should reference parent");
         assertNotNull(child.getParent(), "Child should have parent reference");
         assertEquals("global", child.getParent().getName(), "Child parent should be global");

         // Create addresses under child quota
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("tenant1.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify counters and initial limits
         child = quotaManager.getQuota("tenant1");
         parent = quotaManager.getQuota("global");
         assertEquals(3, child.getCurrentAddressCount(), "Child should track 3 addresses");
         assertEquals(3, parent.getCurrentAddressCount(), "Parent should also track 3 addresses");
         assertEquals(30, child.getMaxAddresses(), "Initial child max should be 30");
         assertEquals(100, parent.getMaxAddresses(), "Initial parent max should be 100");

         // Reload configuration with modified limits
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="global">
                     <max-addresses>200</max-addresses>
                  </resource-quota>
                  <resource-quota name="tenant1">
                     <part-of>global</part-of>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="tenant1.#">
                     <resource-quota>tenant1</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify hierarchy still correct after reload
         child = quotaManager.getQuota("tenant1");
         parent = quotaManager.getQuota("global");
         assertNotNull(child.getParent(), "Child should still have parent after reload");
         assertEquals("global", child.getParent().getName());

         // Verify limits changed
         assertEquals(50, child.getMaxAddresses(), "Child max should be updated to 50");
         assertEquals(200, parent.getMaxAddresses(), "Parent max should be updated to 200");

         // Verify counters rebuilt correctly
         assertEquals(3, child.getCurrentAddressCount(), "Child counter should be rebuilt");
         assertEquals(3, parent.getCurrentAddressCount(), "Parent counter should be rebuilt");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Wildcard quota decrement on removal
    * Verifies that wildcard quota instances properly decrement counters
    * when addresses/queues are removed.
    */
   @Test
   public void testWildcardQuotaDecrementOnRemoval() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="region.*">
                  <max-addresses>15</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="region.#">
                  <resource-quota>region.*</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify wildcard template exists
         ResourceQuota template = quotaManager.getQuota("region.*");
         assertNotNull(template, "Wildcard template should exist");
         assertEquals(15, template.getMaxAddresses());

         // Create addresses that match wildcard pattern - this should create instances
         AddressInfo addr1 = new AddressInfo(SimpleString.of("region.us.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         AddressInfo addr2 = new AddressInfo(SimpleString.of("region.us.payments"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr2);

         AddressInfo addr3 = new AddressInfo(SimpleString.of("region.eu.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr3);

         // Check that wildcard instances were created and have correct counts
         // The quota lookup should return the instantiated quota (region.us or region.eu)
         ResourceQuota usQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.us.orders"));
         assertNotNull(usQuota, "US region quota instance should exist");
         assertEquals(2, usQuota.getCurrentAddressCount(), "US region should have 2 addresses");

         ResourceQuota euQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.eu.orders"));
         assertNotNull(euQuota, "EU region quota instance should exist");
         assertEquals(1, euQuota.getCurrentAddressCount(), "EU region should have 1 address");

         // Now remove one US address
         embeddedActiveMQ.getActiveMQServer().removeAddressInfo(SimpleString.of("region.us.orders"), null);

         // Verify US quota decremented
         usQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.us.payments"));
         assertEquals(1, usQuota.getCurrentAddressCount(), "US region should have 1 address after removal");

         // EU quota should be unchanged
         euQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.eu.orders"));
         assertEquals(1, euQuota.getCurrentAddressCount(), "EU region should still have 1 address");

         // Remove remaining US address
         embeddedActiveMQ.getActiveMQServer().removeAddressInfo(SimpleString.of("region.us.payments"), null);

         // Verify US quota is now at 0
         usQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.us.test"));
         assertEquals(0, usQuota.getCurrentAddressCount(), "US region should have 0 addresses after all removed");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Circular reference detection during reload
    * Verifies that circular parent references in config are detected and handled gracefully.
    */
   @Test
   public void testCircularReferenceDetectionOnReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="test-quota">
                  <max-addresses>10</max-addresses>
                  <max-queues>20</max-queues>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>test-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial quota has no parent
         ResourceQuota quota = quotaManager.getQuota("test-quota");
         assertNotNull(quota);
         assertNull(quota.getParent());

         // Reload with circular reference config
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <!-- Circular reference: quota1 -> quota2 -> quota1 -->
                  <resource-quota name="quota1">
                     <part-of>quota2</part-of>
                     <max-addresses>10</max-addresses>
                  </resource-quota>
                  <resource-quota name="quota2">
                     <part-of>quota1</part-of>
                     <max-addresses>20</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="test.#">
                     <resource-quota>quota1</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify both quotas exist but neither has parent (circular reference detected)
         ResourceQuota quota1 = quotaManager.getQuota("quota1");
         ResourceQuota quota2 = quotaManager.getQuota("quota2");

         assertNotNull(quota1, "quota1 should exist");
         assertNotNull(quota2, "quota2 should exist");

         // Circular reference should be detected and broken
         assertNull(quota1.getParent(), "quota1 should have no parent due to circular reference");
         assertNull(quota2.getParent(), "quota2 should have no parent due to circular reference");

         // Verify quotas still function (limits enforced even without parent)
         assertEquals(10, quota1.getMaxAddresses());
         assertEquals(20, quota2.getMaxAddresses());

         // Verify can create addresses under the quota
         AddressInfo addr1 = new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         // Should use quota1 based on address-settings
         ResourceQuota usedQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("test.addr1"));
         assertEquals("quota1", usedQuota.getName());
         assertEquals(1, usedQuota.getCurrentAddressCount());

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Queue quota enforcement after reload
    * Verifies that queue quotas are properly enforced after configuration reload
    * and that queue counters are correctly rebuilt.
    */
   @Test
   public void testQueueQuotaEnforcementAfterReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="queue-quota">
                  <max-addresses>20</max-addresses>
                  <max-queues>10</max-queues>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="queue.#">
                  <resource-quota>queue-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial quota
         ResourceQuota quota = quotaManager.getQuota("queue-quota");
         assertNotNull(quota);
         assertEquals(10, quota.getMaxQueues());

         // Create an address and some queues
         AddressInfo addr1 = new AddressInfo(SimpleString.of("queue.test1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("q1")
               .setAddress("queue.test1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("q2")
               .setAddress("queue.test1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("q3")
               .setAddress("queue.test1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         // Verify counters
         quota = quotaManager.getQuota("queue-quota");
         assertEquals(1, quota.getCurrentAddressCount());
         assertEquals(3, quota.getCurrentQueueCount());

         // Reload with modified queue limit
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="queue-quota">
                     <max-addresses>20</max-addresses>
                     <max-queues>25</max-queues>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="queue.#">
                     <resource-quota>queue-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify queue limit changed and counters rebuilt
         quota = quotaManager.getQuota("queue-quota");
         assertNotNull(quota);
         assertEquals(25, quota.getMaxQueues(), "Max queues should be updated to 25");
         assertEquals(1, quota.getCurrentAddressCount(), "Address counter should be rebuilt to 1");
         assertEquals(3, quota.getCurrentQueueCount(), "Queue counter should be rebuilt to 3");

         // Verify can create more queues (new limit allows it)
         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("q4")
               .setAddress("queue.test1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         quota = quotaManager.getQuota("queue-quota");
         assertEquals(4, quota.getCurrentQueueCount(), "Queue counter should increment to 4");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Wildcard quota instance cleanup on reload
    * Verifies that instantiated wildcard quotas are cleaned up when
    * the template is removed from configuration.
    */
   @Test
   public void testWildcardQuotaInstancesCleanedOnReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="region.*">
                  <max-addresses>15</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="region.#">
                  <resource-quota>region.*</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Create addresses that instantiate the wildcard template
         AddressInfo addr1 = new AddressInfo(SimpleString.of("region.us.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         AddressInfo addr2 = new AddressInfo(SimpleString.of("region.eu.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr2);

         // Verify instances were created
         ResourceQuota usQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.us.orders"));
         assertNotNull(usQuota, "US region quota instance should exist");

         ResourceQuota euQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.eu.orders"));
         assertNotNull(euQuota, "EU region quota instance should exist");

         // Reload config without wildcard quota
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <address-settings>
                  <address-setting match="region.#">
                  </address-setting>
               </address-settings>""", latch);

         // Verify template is removed
         ResourceQuota template = quotaManager.getQuota("region.*");
         assertNull(template, "Wildcard template should be removed");

         // Verify addresses still exist
         assertNotNull(embeddedActiveMQ.getActiveMQServer().getAddressInfo(SimpleString.of("region.us.orders")));
         assertNotNull(embeddedActiveMQ.getActiveMQServer().getAddressInfo(SimpleString.of("region.eu.orders")));

         // Verify no quota is applied to these addresses anymore
         ResourceQuota noQuota = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("region.us.orders"));
         assertNull(noQuota, "No quota should be applied after template removal");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Change parent relationship during reload
    * Verifies that changing a quota's parent hierarchy during reload
    * correctly updates counters throughout the new hierarchy.
    */
   @Test
   public void testChangeParentOnReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="tenant1">
                  <part-of>global</part-of>
                  <max-addresses>30</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="tenant1.#">
                  <resource-quota>tenant1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial hierarchy: global -> tenant1
         ResourceQuota global = quotaManager.getQuota("global");
         ResourceQuota tenant1 = quotaManager.getQuota("tenant1");
         assertNotNull(global);
         assertNotNull(tenant1);
         assertEquals("global", tenant1.getPartOf());
         assertNotNull(tenant1.getParent());
         assertEquals("global", tenant1.getParent().getName());

         // Create addresses under tenant1
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("tenant1.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify initial counters
         tenant1 = quotaManager.getQuota("tenant1");
         global = quotaManager.getQuota("global");
         assertEquals(3, tenant1.getCurrentAddressCount());
         assertEquals(3, global.getCurrentAddressCount(), "Parent global should track 3 addresses");

         // Reload with new hierarchy: global -> newParent -> tenant1
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="global">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="newParent">
                     <part-of>global</part-of>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
                  <resource-quota name="tenant1">
                     <part-of>newParent</part-of>
                     <max-addresses>30</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="tenant1.#">
                     <resource-quota>tenant1</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify new hierarchy
         global = quotaManager.getQuota("global");
         ResourceQuota newParent = quotaManager.getQuota("newParent");
         tenant1 = quotaManager.getQuota("tenant1");

         assertNotNull(global);
         assertNotNull(newParent);
         assertNotNull(tenant1);

         assertEquals("newParent", tenant1.getPartOf(), "tenant1 should now be part of newParent");
         assertEquals("newParent", tenant1.getParent().getName());
         assertEquals("global", newParent.getPartOf(), "newParent should be part of global");
         assertEquals("global", newParent.getParent().getName());

         // Verify counters are correct throughout hierarchy
         assertEquals(3, tenant1.getCurrentAddressCount(), "tenant1 should have 3 addresses");
         assertEquals(3, newParent.getCurrentAddressCount(), "newParent should track 3 addresses from tenant1");
         assertEquals(3, global.getCurrentAddressCount(), "global should track 3 addresses from entire hierarchy");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Address settings quota mapping change.
    * Verifies that when address-settings mapping changes to point to a different quota,
    * counters are rebalanced: decremented from the old quota, incremented on the new one.
    */
   @Test
   public void testAddressSettingsQuotaMappingChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="quota1">
                  <max-addresses>10</max-addresses>
               </resource-quota>
               <resource-quota name="quota2">
                  <max-addresses>20</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>quota1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify both quotas exist
         ResourceQuota quota1 = quotaManager.getQuota("quota1");
         ResourceQuota quota2 = quotaManager.getQuota("quota2");
         assertNotNull(quota1);
         assertNotNull(quota2);

         // Create addresses under test.# pattern (mapped to quota1 initially)
         for (int i = 0; i < 5; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify quota1 tracks these addresses
         quota1 = quotaManager.getQuota("quota1");
         quota2 = quotaManager.getQuota("quota2");
         assertEquals(5, quota1.getCurrentAddressCount(), "quota1 should track 5 addresses");
         assertEquals(0, quota2.getCurrentAddressCount(), "quota2 should track 0 addresses");

         // Verify lookup returns quota1
         ResourceQuota lookedUp = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("test.addr0"));
         assertEquals("quota1", lookedUp.getName());

         // Reload with address-settings changed to map test.# to quota2
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="quota1">
                     <max-addresses>10</max-addresses>
                  </resource-quota>
                  <resource-quota name="quota2">
                     <max-addresses>20</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="test.#">
                     <resource-quota>quota2</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify quotas still exist
         quota1 = quotaManager.getQuota("quota1");
         quota2 = quotaManager.getQuota("quota2");
         assertNotNull(quota1);
         assertNotNull(quota2);

         // Verify quota2 now tracks these addresses (counters rebuilt)
         assertEquals(0, quota1.getCurrentAddressCount(), "quota1 should no longer track addresses");
         assertEquals(5, quota2.getCurrentAddressCount(), "quota2 should now track 5 addresses");

         // Verify lookup now returns quota2
         lookedUp = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().lookupQuota(SimpleString.of("test.addr0"));
         assertEquals("quota2", lookedUp.getName());

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Parent quota removed while child exists
    * Verifies that when a parent quota is removed but the child remains,
    * the child functions correctly without a parent.
    */
   @Test
   public void testParentRemovedChildRemains() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="tenant1">
                  <part-of>global</part-of>
                  <max-addresses>30</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="tenant1.#">
                  <resource-quota>tenant1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify parent-child hierarchy exists
         ResourceQuota global = quotaManager.getQuota("global");
         ResourceQuota tenant1 = quotaManager.getQuota("tenant1");
         assertNotNull(global);
         assertNotNull(tenant1);
         assertEquals("global", tenant1.getParent().getName());

         // Create addresses under child
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("tenant1.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify both track the addresses
         tenant1 = quotaManager.getQuota("tenant1");
         global = quotaManager.getQuota("global");
         assertEquals(3, tenant1.getCurrentAddressCount());
         assertEquals(3, global.getCurrentAddressCount());

         // Reload config removing parent
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="tenant1">
                     <part-of>global</part-of>
                     <max-addresses>30</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="tenant1.#">
                     <resource-quota>tenant1</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify parent is gone
         global = quotaManager.getQuota("global");
         assertNull(global, "Parent quota 'global' should be removed");

         // Verify child still exists with correct counters
         tenant1 = quotaManager.getQuota("tenant1");
         assertNotNull(tenant1, "Child quota 'tenant1' should still exist");
         assertEquals(3, tenant1.getCurrentAddressCount(), "Child counter should be rebuilt to 3");

         // Verify child has no parent (even though part-of is still set in config)
         assertNull(tenant1.getParent(), "Child should have no parent since parent was removed");

         // Verify child quota still functions - can create more addresses
         AddressInfo addr = new AddressInfo(SimpleString.of("tenant1.app99"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);

         tenant1 = quotaManager.getQuota("tenant1");
         assertEquals(4, tenant1.getCurrentAddressCount(), "Child should track new address");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testMessageBytesCounterRebuildOnReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="bytes-quota">
                  <max-message-bytes>100000</max-message-bytes>
                  <max-addresses>50</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="bytes.#">
                  <resource-quota>bytes-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial quota
         ResourceQuota quota = quotaManager.getQuota("bytes-quota");
         assertNotNull(quota);
         assertEquals(100000L, quota.getMaxMessageBytes());
         assertEquals(0L, quota.getCurrentMessageBytes(), "Initial bytes should be 0");

         // Create an address and queue
         AddressInfo addr1 = new AddressInfo(SimpleString.of("bytes.test1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("bytesQueue1")
               .setAddress("bytes.test1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         // Send real messages using Core API client to build up bytes
         org.apache.activemq.artemis.api.core.client.ServerLocator locator = createInVMNonHALocator();
         org.apache.activemq.artemis.api.core.client.ClientSessionFactory sf = createSessionFactory(locator);
         org.apache.activemq.artemis.api.core.client.ClientSession session = sf.createSession(false, true, true);
         org.apache.activemq.artemis.api.core.client.ClientProducer producer = session.createProducer("bytes.test1");

         // Send 50 messages of ~1000 bytes each = ~50KB total
         for (int i = 0; i < 50; i++) {
            org.apache.activemq.artemis.api.core.client.ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1000]);
            producer.send(msg);
         }

         session.close();
         sf.close();
         locator.close();

         // Verify quota tracked the bytes
         quota = quotaManager.getQuota("bytes-quota");
         long bytesBeforeReload = quota.getCurrentMessageBytes();
         assertTrue(bytesBeforeReload > 0, "Quota should track message bytes before reload");

         // Reload with modified byte limit
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="bytes-quota">
                     <max-message-bytes>200000</max-message-bytes>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="bytes.#">
                     <resource-quota>bytes-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify byte limit changed
         quota = quotaManager.getQuota("bytes-quota");
         assertNotNull(quota);
         assertEquals(200000L, quota.getMaxMessageBytes(), "Max bytes should be updated to 200000");

         // CRITICAL: Verify bytes counter was rebuilt correctly
         long bytesAfterReload = quota.getCurrentMessageBytes();
         assertEquals(bytesBeforeReload, bytesAfterReload,
            "Message bytes counter should be rebuilt to match actual size in PagingStore. " +
            "This test exposes the bug that rebuildCountersForQuota() doesn't rebuild message bytes!");

      } finally {
         embeddedActiveMQ.stop();
      }
   }


   /**
    * Message bytes hierarchy rebuild on reload
    * Verifies that parent quotas correctly track total bytes from child quotas
    * after reload, with proper counter rebuilding throughout the hierarchy.
    */
   @Test
   public void testMessageBytesHierarchyRebuild() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global-bytes">
                  <max-message-bytes>500000</max-message-bytes>
               </resource-quota>
               <resource-quota name="tenant-bytes">
                  <part-of>global-bytes</part-of>
                  <max-message-bytes>200000</max-message-bytes>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="tenant.#">
                  <resource-quota>tenant-bytes</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify hierarchy exists
         ResourceQuota globalQuota = quotaManager.getQuota("global-bytes");
         ResourceQuota tenantQuota = quotaManager.getQuota("tenant-bytes");
         assertNotNull(globalQuota);
         assertNotNull(tenantQuota);
         assertEquals("global-bytes", tenantQuota.getParent().getName());

         // Create address and queue under child quota
         AddressInfo addr1 = new AddressInfo(SimpleString.of("tenant.app1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("tenantQueue1")
               .setAddress("tenant.app1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         // Send real messages to build up size in the hierarchy
         org.apache.activemq.artemis.api.core.client.ServerLocator locator = createInVMNonHALocator();
         org.apache.activemq.artemis.api.core.client.ClientSessionFactory sf = createSessionFactory(locator);
         org.apache.activemq.artemis.api.core.client.ClientSession session = sf.createSession(false, true, true);
         org.apache.activemq.artemis.api.core.client.ClientProducer producer = session.createProducer("tenant.app1");

         // Send 50 messages of ~1000 bytes each = ~50KB
         for (int i = 0; i < 50; i++) {
            org.apache.activemq.artemis.api.core.client.ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1000]);
            producer.send(msg);
         }

         session.close();
         sf.close();
         locator.close();

         // Verify both quotas track the bytes
         tenantQuota = quotaManager.getQuota("tenant-bytes");
         globalQuota = quotaManager.getQuota("global-bytes");
         long tenantBytesBeforeReload = tenantQuota.getCurrentMessageBytes();
         long globalBytesBeforeReload = globalQuota.getCurrentMessageBytes();

         assertTrue(tenantBytesBeforeReload > 0, "Tenant should track bytes");
         assertEquals(tenantBytesBeforeReload, globalBytesBeforeReload,
            "Parent should track same bytes as child (hierarchy propagation)");

         // Verify initial limits
         assertEquals(200000L, tenantQuota.getMaxMessageBytes(), "Initial tenant max should be 200000");
         assertEquals(500000L, globalQuota.getMaxMessageBytes(), "Initial global max should be 500000");

         // Reload configuration with modified limits
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="global-bytes">
                     <max-message-bytes>1000000</max-message-bytes>
                  </resource-quota>
                  <resource-quota name="tenant-bytes">
                     <part-of>global-bytes</part-of>
                     <max-message-bytes>400000</max-message-bytes>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="tenant.#">
                     <resource-quota>tenant-bytes</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify hierarchy still correct
         tenantQuota = quotaManager.getQuota("tenant-bytes");
         globalQuota = quotaManager.getQuota("global-bytes");
         assertNotNull(tenantQuota.getParent());
         assertEquals("global-bytes", tenantQuota.getParent().getName());

         // Verify limits changed
         assertEquals(400000L, tenantQuota.getMaxMessageBytes(), "Tenant max should be updated to 400000");
         assertEquals(1000000L, globalQuota.getMaxMessageBytes(), "Global max should be updated to 1000000");

         // CRITICAL: Verify bytes rebuilt correctly throughout hierarchy
         long tenantBytesAfterReload = tenantQuota.getCurrentMessageBytes();
         long globalBytesAfterReload = globalQuota.getCurrentMessageBytes();

         assertEquals(tenantBytesBeforeReload, tenantBytesAfterReload,
            "Tenant bytes should be rebuilt correctly");
         assertEquals(globalBytesBeforeReload, globalBytesAfterReload,
            "Parent bytes should be rebuilt correctly (this will fail without fix)");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Test that when multiple sibling children both change parents during reload,
    * the shared original parent's counters are reset only once (not double-reset).
    * This is a regression test for the double-reset bug where each child's rebuild
    * would reset the entire parent chain, causing shared parents to lose counts.
    */
   @Test
   public void testMultipleSiblingsChangeParent() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="child1">
                  <part-of>global</part-of>
                  <max-addresses>30</max-addresses>
               </resource-quota>
               <resource-quota name="child2">
                  <part-of>global</part-of>
                  <max-addresses>30</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="child1.#">
                  <resource-quota>child1</resource-quota>
               </address-setting>
               <address-setting match="child2.#">
                  <resource-quota>child2</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial hierarchy: global <- child1, global <- child2
         ResourceQuota global = quotaManager.getQuota("global");
         ResourceQuota child1 = quotaManager.getQuota("child1");
         ResourceQuota child2 = quotaManager.getQuota("child2");
         assertNotNull(global);
         assertNotNull(child1);
         assertNotNull(child2);
         assertEquals("global", child1.getPartOf());
         assertEquals("global", child2.getPartOf());
         assertEquals("global", child1.getParent().getName());
         assertEquals("global", child2.getParent().getName());

         // Create addresses under both children
         for (int i = 0; i < 3; i++) {
            AddressInfo addr1 = new AddressInfo(SimpleString.of("child1.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);

            AddressInfo addr2 = new AddressInfo(SimpleString.of("child2.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr2);
         }

         // Verify counts before reload
         assertEquals(3, child1.getCurrentAddressCount(), "child1 should have 3 addresses");
         assertEquals(3, child2.getCurrentAddressCount(), "child2 should have 3 addresses");
         assertEquals(6, global.getCurrentAddressCount(), "global should track 6 addresses (3+3)");

         // Reload config where both children change to different new parents
         // child1: global -> newParent1, child2: global -> newParent2
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="global">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="newParent1">
                     <part-of>global</part-of>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
                  <resource-quota name="newParent2">
                     <part-of>global</part-of>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
                  <resource-quota name="child1">
                     <part-of>newParent1</part-of>
                     <max-addresses>30</max-addresses>
                  </resource-quota>
                  <resource-quota name="child2">
                     <part-of>newParent2</part-of>
                     <max-addresses>30</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="child1.#">
                     <resource-quota>child1</resource-quota>
                  </address-setting>
                  <address-setting match="child2.#">
                     <resource-quota>child2</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Get updated quota references
         global = quotaManager.getQuota("global");
         child1 = quotaManager.getQuota("child1");
         child2 = quotaManager.getQuota("child2");
         ResourceQuota newParent1 = quotaManager.getQuota("newParent1");
         ResourceQuota newParent2 = quotaManager.getQuota("newParent2");

         assertNotNull(global);
         assertNotNull(child1);
         assertNotNull(child2);
         assertNotNull(newParent1);
         assertNotNull(newParent2);

         // Verify new hierarchy
         assertEquals("newParent1", child1.getPartOf());
         assertEquals("newParent2", child2.getPartOf());
         assertEquals("newParent1", child1.getParent().getName());
         assertEquals("newParent2", child2.getParent().getName());
         assertEquals("global", newParent1.getParent().getName());
         assertEquals("global", newParent2.getParent().getName());

         // Verify counts after reload
         // This is the critical test: without the alreadyReset fix, global would be
         // reset twice (once for child1's rebuild, once for child2's rebuild) but
         // only rebuilt once (from the last child processed), losing half the counts.
         assertEquals(3, child1.getCurrentAddressCount(), "child1 should still have 3 addresses");
         assertEquals(3, child2.getCurrentAddressCount(), "child2 should still have 3 addresses");
         assertEquals(3, newParent1.getCurrentAddressCount(), "newParent1 should track 3 addresses from child1");
         assertEquals(3, newParent2.getCurrentAddressCount(), "newParent2 should track 3 addresses from child2");
         assertEquals(6, global.getCurrentAddressCount(),
            "global should track 6 addresses (3 from newParent1 + 3 from newParent2) - " +
            "this will fail without the double-reset fix");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Test that quota enforcement is preserved continuously during reload when
    * the hierarchy changes.
    */
   @Test
   public void testReloadPreservesQuotaEnforcementDuringHierarchyChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="test-quota">
                  <max-addresses>5</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="child.#">
                  <resource-quota>test-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial quota exists with no parent
         ResourceQuota quota = quotaManager.getQuota("test-quota");
         assertNotNull(quota);
         assertEquals(5, quota.getMaxAddresses());
         assertNull(quota.getParent(), "Initial quota should have no parent");

         // Create exactly 5 addresses to reach the limit
         for (int i = 0; i < 5; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("child.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify quota is at its limit
         assertEquals(5, quota.getCurrentAddressCount(), "Should have 5 addresses");
         assertFalse(quota.canAddAddress(), "Should be at limit - no more addresses allowed");

         // Verify enforcement works before reload
         assertThrows(
            org.apache.activemq.artemis.api.core.ActiveMQResourceQuotaExceededException.class,
            () -> embeddedActiveMQ.getActiveMQServer().addAddressInfo(
               new AddressInfo(SimpleString.of("child.overflow"), RoutingType.ANYCAST)),
            "Should reject address creation when quota is at limit");

         // Set up concurrent enforcement checker.
         // This thread polls canAddAddress() in a tight loop to detect if
         // counters are ever reset to zero during reload.
         final AtomicBoolean windowDetected = new AtomicBoolean(false);
         final AtomicBoolean checkingActive = new AtomicBoolean(true);
         final CountDownLatch checkerStarted = new CountDownLatch(1);
         final ResourceQuota quotaRef = quota;

         Thread enforcementChecker = new Thread(() -> {
            checkerStarted.countDown();
            while (checkingActive.get()) {
               if (quotaRef.canAddAddress()) {
                  windowDetected.set(true);
               }
            }
         });
         enforcementChecker.setDaemon(true);
         enforcementChecker.start();
         checkerStarted.await();

         // Trigger reload that adds a parent (partOf changes from null to "parent-quota").
         // This hierarchy change causes rebuildCountersForQuota() which calls
         // resetCounters() (zeroing all counters) then scans to rebuild them.
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="parent-quota">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="test-quota">
                     <part-of>parent-quota</part-of>
                     <max-addresses>5</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="child.#">
                     <resource-quota>test-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Stop the checker
         checkingActive.set(false);
         enforcementChecker.join(5000);

         // Verify counters were rebuilt correctly after reload
         quota = quotaManager.getQuota("test-quota");
         assertNotNull(quota);
         assertEquals(5, quota.getCurrentAddressCount(), "Counter should be rebuilt to 5 after reload");
         assertFalse(quota.canAddAddress(), "Should still be at limit after reload");

         // Verify parent relationship was established
         assertNotNull(quota.getParent(), "Should have parent after reload");
         assertEquals("parent-quota", quota.getParent().getName());

         // THE KEY ASSERTION: enforcement should never have been broken during reload.
         // This fails because resetCounters() zeros the counters before the rebuild
         // scan restores them, creating a window where canAddAddress() returns true
         // for a quota that is at its limit.
         assertFalse(windowDetected.get(),
            "Quota enforcement was temporarily broken during reload: canAddAddress() returned true " +
            "for a quota at its limit. The reload resets counters to zero before rebuilding, " +
            "creating a window where quota limits are not enforced.");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Byte counters preserved during hierarchy change.
    * Verifies that adjustParentChain correctly handles byte deltas
    * when a quota's partOf changes.
    */
   @Test
   public void testHierarchyChangePreservesByteCounters() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global-bytes">
                  <max-message-bytes>500000</max-message-bytes>
               </resource-quota>
               <resource-quota name="tenant-bytes">
                  <part-of>global-bytes</part-of>
                  <max-message-bytes>200000</max-message-bytes>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="tenant.#">
                  <resource-quota>tenant-bytes</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial hierarchy: global-bytes -> tenant-bytes
         ResourceQuota globalQuota = quotaManager.getQuota("global-bytes");
         ResourceQuota tenantQuota = quotaManager.getQuota("tenant-bytes");
         assertNotNull(globalQuota);
         assertNotNull(tenantQuota);
         assertEquals("global-bytes", tenantQuota.getParent().getName());

         // Create address, queue, and send messages to build up bytes
         AddressInfo addr1 = new AddressInfo(SimpleString.of("tenant.app1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);
         embeddedActiveMQ.getActiveMQServer().createQueue(
            org.apache.activemq.artemis.api.core.QueueConfiguration.of("tenantQ1")
               .setAddress("tenant.app1")
               .setRoutingType(RoutingType.ANYCAST)
               .setDurable(false));

         org.apache.activemq.artemis.api.core.client.ServerLocator locator = createInVMNonHALocator();
         org.apache.activemq.artemis.api.core.client.ClientSessionFactory sf = createSessionFactory(locator);
         org.apache.activemq.artemis.api.core.client.ClientSession session = sf.createSession(false, true, true);
         org.apache.activemq.artemis.api.core.client.ClientProducer producer = session.createProducer("tenant.app1");

         for (int i = 0; i < 50; i++) {
            org.apache.activemq.artemis.api.core.client.ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1000]);
            producer.send(msg);
         }
         session.close();
         sf.close();
         locator.close();

         // Capture counters before reload
         tenantQuota = quotaManager.getQuota("tenant-bytes");
         globalQuota = quotaManager.getQuota("global-bytes");
         long tenantBytesBefore = tenantQuota.getCurrentMessageBytes();
         int tenantAddrBefore = tenantQuota.getCurrentAddressCount();
         int tenantQueueBefore = tenantQuota.getCurrentQueueCount();
         assertTrue(tenantBytesBefore > 0, "Tenant should have bytes");
         assertEquals(tenantBytesBefore, globalQuota.getCurrentMessageBytes());

         // Reload: tenant-bytes changes partOf from global-bytes to new-parent-bytes
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="global-bytes">
                     <max-message-bytes>500000</max-message-bytes>
                  </resource-quota>
                  <resource-quota name="new-parent-bytes">
                     <max-message-bytes>300000</max-message-bytes>
                  </resource-quota>
                  <resource-quota name="tenant-bytes">
                     <part-of>new-parent-bytes</part-of>
                     <max-message-bytes>200000</max-message-bytes>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="tenant.#">
                     <resource-quota>tenant-bytes</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify tenant counters preserved (never zeroed)
         tenantQuota = quotaManager.getQuota("tenant-bytes");
         assertEquals(tenantBytesBefore, tenantQuota.getCurrentMessageBytes(), "Tenant bytes should be preserved");
         assertEquals(tenantAddrBefore, tenantQuota.getCurrentAddressCount(), "Tenant addresses should be preserved");
         assertEquals(tenantQueueBefore, tenantQuota.getCurrentQueueCount(), "Tenant queues should be preserved");

         // Verify new parent gets tenant's counters
         ResourceQuota newParent = quotaManager.getQuota("new-parent-bytes");
         assertNotNull(newParent, "new-parent-bytes should exist");
         assertEquals("new-parent-bytes", tenantQuota.getParent().getName());
         assertEquals(tenantBytesBefore, newParent.getCurrentMessageBytes(), "New parent should aggregate tenant bytes");

         // Verify old parent lost tenant's counters
         globalQuota = quotaManager.getQuota("global-bytes");
         assertEquals(0, globalQuota.getCurrentMessageBytes(), "Global should have 0 bytes after tenant moved away");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Quota loses its parent (partOf removed from config).
    * Verifies that removing the partOf field from a quota's config
    * correctly detaches it from the parent while preserving its own counters.
    */
   @Test
   public void testRemovePartOfFromQuota() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="global">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="tenant1">
                  <part-of>global</part-of>
                  <max-addresses>30</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="tenant1.#">
                  <resource-quota>tenant1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial hierarchy: global -> tenant1
         ResourceQuota global = quotaManager.getQuota("global");
         ResourceQuota tenant1 = quotaManager.getQuota("tenant1");
         assertNotNull(global);
         assertNotNull(tenant1);
         assertEquals("global", tenant1.getPartOf());
         assertNotNull(tenant1.getParent());

         // Create addresses
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("tenant1.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         assertEquals(3, tenant1.getCurrentAddressCount());
         assertEquals(3, global.getCurrentAddressCount());

         // Reload: tenant1 loses partOf (no <part-of> element), global still exists
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="global">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="tenant1">
                     <max-addresses>30</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="tenant1.#">
                     <resource-quota>tenant1</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify tenant1 counters preserved and parent detached
         tenant1 = quotaManager.getQuota("tenant1");
         global = quotaManager.getQuota("global");
         assertNotNull(tenant1);
         assertNotNull(global);
         assertEquals(3, tenant1.getCurrentAddressCount(), "Tenant counters should be preserved");
         assertNull(tenant1.getParent(), "Tenant should have no parent after partOf removed");
         assertNull(tenant1.getPartOf(), "Tenant partOf config should be null");

         // Global should have been decremented by delta
         assertEquals(0, global.getCurrentAddressCount(), "Global should be decremented to 0");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Add quota when none existed, with pre-existing addresses.
    * Tests both manager creation from null and populateCountersForNewQuota
    * scanning pre-existing addresses.
    */
   @Test
   public void testAddQuotaWithPreExistingAddresses() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <address-settings>
               <address-setting match="quota.#">
                  <max-size-messages>-1</max-size-messages>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         // Verify no quota manager initially
         ResourceQuotaService quotaService = embeddedActiveMQ.getActiveMQServer().getResourceQuotaService();
         assertNotNull(quotaService);
         assertNull(quotaService.getResourceQuotaManager(), "Manager should be null with no quotas");

         // Create addresses and queues BEFORE any quota exists
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("quota.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
            embeddedActiveMQ.getActiveMQServer().createQueue(
               org.apache.activemq.artemis.api.core.QueueConfiguration.of("q" + i)
                  .setAddress("quota.addr" + i)
                  .setRoutingType(RoutingType.ANYCAST)
                  .setDurable(false));
         }

         // Reload: add quota for quota.# pattern
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="new-quota">
                     <max-addresses>10</max-addresses>
                     <max-queues>20</max-queues>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="quota.#">
                     <resource-quota>new-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify manager was created
         ResourceQuotaManager quotaManager = quotaService.getResourceQuotaManager();
         assertNotNull(quotaManager, "Manager should be created after reload");

         // Verify new quota picked up pre-existing addresses and queues
         ResourceQuota newQuota = quotaManager.getQuota("new-quota");
         assertNotNull(newQuota, "new-quota should exist");
         assertEquals(3, newQuota.getCurrentAddressCount(),
            "New quota should have scanned pre-existing addresses");
         assertEquals(3, newQuota.getCurrentQueueCount(),
            "New quota should have scanned pre-existing queues");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * New child quota added under existing parent that already has counters.
    * Verifies that populateCountersForNewQuota correctly propagates to parent
    * without double-counting.
    */
   @Test
   public void testNewChildQuotaUnderExistingParent() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="parent-quota">
                  <max-addresses>100</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="parent.#">
                  <resource-quota>parent-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify only parent-quota exists
         ResourceQuota parentQuota = quotaManager.getQuota("parent-quota");
         assertNotNull(parentQuota);
         assertNull(quotaManager.getQuota("child-quota"), "child-quota should not exist yet");

         // Create addresses under parent.# (tracked by parent-quota)
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("parent.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Create addresses under child.# (no quota yet)
         for (int i = 0; i < 2; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("child.app" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         assertEquals(3, parentQuota.getCurrentAddressCount(), "Parent should track its own 3 addresses");

         // Reload: add child-quota with partOf=parent-quota for child.#
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="parent-quota">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="child-quota">
                     <part-of>parent-quota</part-of>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="parent.#">
                     <resource-quota>parent-quota</resource-quota>
                  </address-setting>
                  <address-setting match="child.#">
                     <resource-quota>child-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify child-quota picks up pre-existing child.* addresses
         ResourceQuota childQuota = quotaManager.getQuota("child-quota");
         assertNotNull(childQuota, "child-quota should exist after reload");
         assertEquals(2, childQuota.getCurrentAddressCount(),
            "child-quota should have scanned 2 pre-existing child addresses");

         // Verify parent aggregates correctly: 3 (own) + 2 (from child propagation) = 5
         parentQuota = quotaManager.getQuota("parent-quota");
         assertEquals(5, parentQuota.getCurrentAddressCount(),
            "Parent should aggregate own (3) + child (2) = 5 addresses");

         // Verify hierarchy
         assertEquals("parent-quota", childQuota.getParent().getName());

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Nested hierarchy changes in same reload.
    * Both parent and child change partOf simultaneously. Verifies that
    * delta adjustments produce correct results regardless of HashMap iteration order.
    */
   @Test
   public void testNestedHierarchyChangesInSameReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="root">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="parent">
                  <part-of>root</part-of>
                  <max-addresses>50</max-addresses>
               </resource-quota>
               <resource-quota name="child">
                  <part-of>parent</part-of>
                  <max-addresses>10</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="child.#">
                  <resource-quota>child</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify initial hierarchy: root -> parent -> child
         ResourceQuota root = quotaManager.getQuota("root");
         ResourceQuota parent = quotaManager.getQuota("parent");
         ResourceQuota child = quotaManager.getQuota("child");
         assertNotNull(root);
         assertNotNull(parent);
         assertNotNull(child);
         assertEquals("parent", child.getPartOf());
         assertEquals("root", parent.getPartOf());

         // Create addresses under child
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("child.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         assertEquals(3, child.getCurrentAddressCount());
         assertEquals(3, parent.getCurrentAddressCount(), "Parent should aggregate child's 3");
         assertEquals(3, root.getCurrentAddressCount(), "Root should aggregate 3 through parent");

         // Reload: child changes partOf to root, parent changes partOf to newRoot
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="root">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="newRoot">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="parent">
                     <part-of>newRoot</part-of>
                     <max-addresses>50</max-addresses>
                  </resource-quota>
                  <resource-quota name="child">
                     <part-of>root</part-of>
                     <max-addresses>10</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="child.#">
                     <resource-quota>child</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify new hierarchy
         child = quotaManager.getQuota("child");
         parent = quotaManager.getQuota("parent");
         root = quotaManager.getQuota("root");
         ResourceQuota newRoot = quotaManager.getQuota("newRoot");
         assertNotNull(newRoot);

         assertEquals("root", child.getPartOf(), "child should now be partOf root");
         assertEquals("newRoot", parent.getPartOf(), "parent should now be partOf newRoot");

         // Verify counters are correct
         assertEquals(3, child.getCurrentAddressCount(), "child counters should be preserved");
         assertEquals(0, parent.getCurrentAddressCount(), "parent should have 0 (child moved away)");
         assertEquals(3, root.getCurrentAddressCount(), "root should have 3 (from child directly)");
         assertEquals(0, newRoot.getCurrentAddressCount(), "newRoot should have 0 (parent has no addresses)");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Wildcard template partOf change during reload.
    * Verifies that when a wildcard template quota changes its partOf,
    * both the template and its instantiated children are reparented
    * with counters correctly moved between parent chains.
    */
   @Test
   public void testWildcardTemplatePartOfChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="parent-quota">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="region.*">
                  <part-of>parent-quota</part-of>
                  <max-addresses>15</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="region.#">
                  <resource-quota>region.*</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();
         ResourceQuotaService quotaService = embeddedActiveMQ.getActiveMQServer().getResourceQuotaService();

         // Verify initial hierarchy: parent-quota -> region.*
         ResourceQuota parentQuota = quotaManager.getQuota("parent-quota");
         ResourceQuota template = quotaManager.getQuota("region.*");
         assertNotNull(parentQuota);
         assertNotNull(template);
         assertEquals("parent-quota", template.getPartOf());

         // Create addresses to instantiate wildcard instances
         AddressInfo addr1 = new AddressInfo(SimpleString.of("region.us.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr1);
         AddressInfo addr2 = new AddressInfo(SimpleString.of("region.eu.orders"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr2);

         // Verify instances were created and parent tracks addresses
         ResourceQuota usQuota = quotaService.lookupQuota(SimpleString.of("region.us.orders"));
         ResourceQuota euQuota = quotaService.lookupQuota(SimpleString.of("region.eu.orders"));
         assertNotNull(usQuota, "US instance should exist");
         assertNotNull(euQuota, "EU instance should exist");
         assertEquals(1, usQuota.getCurrentAddressCount());
         assertEquals(1, euQuota.getCurrentAddressCount());

         parentQuota = quotaManager.getQuota("parent-quota");
         int parentAddrBefore = parentQuota.getCurrentAddressCount();
         assertTrue(parentAddrBefore > 0, "Parent should have addresses from wildcard instances");

         // Reload: region.* template changes partOf from parent-quota to new-parent
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="parent-quota">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="new-parent">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="region.*">
                     <part-of>new-parent</part-of>
                     <max-addresses>15</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="region.#">
                     <resource-quota>region.*</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify template's partOf updated correctly
         template = quotaManager.getQuota("region.*");
         assertNotNull(template, "Template should still exist");
         assertEquals("new-parent", template.getPartOf());

         // Verify instances were reparented: their partOf and parent should
         // now point to new-parent, not parent-quota
         usQuota = quotaService.lookupQuota(SimpleString.of("region.us.orders"));
         euQuota = quotaService.lookupQuota(SimpleString.of("region.eu.orders"));
         assertEquals("new-parent", usQuota.getPartOf(), "US instance should have new partOf");
         assertEquals("new-parent", euQuota.getPartOf(), "EU instance should have new partOf");
         assertEquals(1, usQuota.getCurrentAddressCount(), "US instance counters should be preserved");
         assertEquals(1, euQuota.getCurrentAddressCount(), "EU instance counters should be preserved");

         // Verify new parent gets the instance counts
         ResourceQuota newParent = quotaManager.getQuota("new-parent");
         assertNotNull(newParent, "new-parent should exist");
         assertEquals(parentAddrBefore, newParent.getCurrentAddressCount(),
            "New parent should aggregate instance counts");

         // Verify old parent lost the instance counts
         parentQuota = quotaManager.getQuota("parent-quota");
         assertEquals(0, parentQuota.getCurrentAddressCount(),
            "Old parent should have 0 addresses after instances reparented");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Queue enforcement preserved during hierarchy change.
    * Concurrent thread polls canAddQueue() while a reload changes the quota's partOf.
    */
   @Test
   public void testReloadPreservesQueueEnforcementDuringHierarchyChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="queue-quota">
                  <max-queues>5</max-queues>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="qenf.#">
                  <resource-quota>queue-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         ResourceQuota quota = quotaManager.getQuota("queue-quota");
         assertNotNull(quota);
         assertEquals(5, quota.getMaxQueues());

         // Create address and 5 queues to reach the limit
         AddressInfo addr = new AddressInfo(SimpleString.of("qenf.test1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         for (int i = 0; i < 5; i++) {
            embeddedActiveMQ.getActiveMQServer().createQueue(
               org.apache.activemq.artemis.api.core.QueueConfiguration.of("qenf.q" + i)
                  .setAddress("qenf.test1")
                  .setRoutingType(RoutingType.ANYCAST)
                  .setDurable(false));
         }

         assertEquals(5, quota.getCurrentQueueCount());
         assertFalse(quota.canAddQueue(), "Should be at queue limit");

         // Set up concurrent queue enforcement checker
         final AtomicBoolean windowDetected = new AtomicBoolean(false);
         final AtomicBoolean checkingActive = new AtomicBoolean(true);
         final CountDownLatch checkerStarted = new CountDownLatch(1);
         final ResourceQuota quotaRef = quota;

         Thread enforcementChecker = new Thread(() -> {
            checkerStarted.countDown();
            while (checkingActive.get()) {
               if (quotaRef.canAddQueue()) {
                  windowDetected.set(true);
               }
            }
         });
         enforcementChecker.setDaemon(true);
         enforcementChecker.start();
         checkerStarted.await();

         // Trigger reload adding partOf
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="parent-quota">
                     <max-queues>100</max-queues>
                  </resource-quota>
                  <resource-quota name="queue-quota">
                     <part-of>parent-quota</part-of>
                     <max-queues>5</max-queues>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="qenf.#">
                     <resource-quota>queue-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         checkingActive.set(false);
         enforcementChecker.join(5000);

         // Verify counters rebuilt and enforcement preserved
         quota = quotaManager.getQuota("queue-quota");
         assertEquals(5, quota.getCurrentQueueCount(), "Queue count should be preserved");
         assertFalse(quota.canAddQueue(), "Should still be at queue limit");
         assertNotNull(quota.getParent(), "Should have parent after reload");

         assertFalse(windowDetected.get(),
            "Queue enforcement was temporarily broken during reload: canAddQueue() returned true " +
            "for a quota at its queue limit.");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Byte enforcement preserved during hierarchy change.
    * Concurrent thread polls canAddBytes() while a reload changes the quota's partOf.
    */
   @Test
   public void testReloadPreservesByteEnforcementDuringHierarchyChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="bytes-quota">
                  <max-message-bytes>50000</max-message-bytes>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="benf.#">
                  <resource-quota>bytes-quota</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         ResourceQuota quota = quotaManager.getQuota("bytes-quota");
         assertNotNull(quota);
         assertEquals(50000L, quota.getMaxMessageBytes());

         // Create address so the quota is active
         AddressInfo addr = new AddressInfo(SimpleString.of("benf.test1"), RoutingType.ANYCAST);
         embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);

         // Add bytes directly to reach exactly the limit
         quota.addSize(50000);
         long bytesBefore = quota.getCurrentMessageBytes();
         assertEquals(50000L, bytesBefore);
         assertFalse(quota.canAddBytes(1), "Should not allow more bytes at limit");

         // Set up concurrent byte enforcement checker
         final AtomicBoolean windowDetected = new AtomicBoolean(false);
         final AtomicBoolean checkingActive = new AtomicBoolean(true);
         final CountDownLatch checkerStarted = new CountDownLatch(1);
         final ResourceQuota quotaRef = quota;

         Thread enforcementChecker = new Thread(() -> {
            checkerStarted.countDown();
            while (checkingActive.get()) {
               if (quotaRef.canAddBytes(1)) {
                  windowDetected.set(true);
               }
            }
         });
         enforcementChecker.setDaemon(true);
         enforcementChecker.start();
         checkerStarted.await();

         // Trigger reload adding partOf
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="parent-quota">
                     <max-message-bytes>500000</max-message-bytes>
                  </resource-quota>
                  <resource-quota name="bytes-quota">
                     <part-of>parent-quota</part-of>
                     <max-message-bytes>50000</max-message-bytes>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="benf.#">
                     <resource-quota>bytes-quota</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         checkingActive.set(false);
         enforcementChecker.join(5000);

         // Verify byte counters preserved and enforcement maintained
         quota = quotaManager.getQuota("bytes-quota");
         assertEquals(bytesBefore, quota.getCurrentMessageBytes(), "Byte count should be preserved");
         assertFalse(quota.canAddBytes(1), "Should still be over byte limit");
         assertNotNull(quota.getParent(), "Should have parent after reload");

         assertFalse(windowDetected.get(),
            "Byte enforcement was temporarily broken during reload: canAddBytes() returned true " +
            "for a quota over its byte limit.");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Address-settings mapping change with queues and bytes.
    * Verifies that when address-settings remaps addresses to a different quota,
    * address counts, queue counts, AND byte counts are all rebalanced.
    */
   @Test
   public void testAddressSettingsQuotaMappingChangeWithQueuesAndBytes() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="quota1">
                  <max-addresses>10</max-addresses>
                  <max-queues>20</max-queues>
                  <max-message-bytes>500000</max-message-bytes>
               </resource-quota>
               <resource-quota name="quota2">
                  <max-addresses>20</max-addresses>
                  <max-queues>40</max-queues>
                  <max-message-bytes>1000000</max-message-bytes>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>quota1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         ResourceQuota quota1 = quotaManager.getQuota("quota1");
         ResourceQuota quota2 = quotaManager.getQuota("quota2");
         assertNotNull(quota1);
         assertNotNull(quota2);

         // Create 3 addresses, each with 2 queues
         for (int i = 0; i < 3; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
            for (int q = 0; q < 2; q++) {
               embeddedActiveMQ.getActiveMQServer().createQueue(
                  org.apache.activemq.artemis.api.core.QueueConfiguration.of("test.addr" + i + ".q" + q)
                     .setAddress("test.addr" + i)
                     .setRoutingType(RoutingType.ANYCAST)
                     .setDurable(false));
            }
         }

         // Send messages to build up bytes on test.addr0
         org.apache.activemq.artemis.api.core.client.ServerLocator locator = createInVMNonHALocator();
         org.apache.activemq.artemis.api.core.client.ClientSessionFactory sf = createSessionFactory(locator);
         org.apache.activemq.artemis.api.core.client.ClientSession session = sf.createSession(false, true, true);
         org.apache.activemq.artemis.api.core.client.ClientProducer producer = session.createProducer("test.addr0");
         for (int i = 0; i < 20; i++) {
            org.apache.activemq.artemis.api.core.client.ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1000]);
            producer.send(msg);
         }
         session.close();
         sf.close();
         locator.close();

         // Verify quota1 tracks everything
         quota1 = quotaManager.getQuota("quota1");
         quota2 = quotaManager.getQuota("quota2");
         assertEquals(3, quota1.getCurrentAddressCount(), "quota1 should track 3 addresses");
         assertEquals(6, quota1.getCurrentQueueCount(), "quota1 should track 6 queues");
         long bytesBefore = quota1.getCurrentMessageBytes();
         assertTrue(bytesBefore > 0, "quota1 should track bytes from messages");

         assertEquals(0, quota2.getCurrentAddressCount(), "quota2 should track 0 addresses");
         assertEquals(0, quota2.getCurrentQueueCount(), "quota2 should track 0 queues");
         assertEquals(0, quota2.getCurrentMessageBytes(), "quota2 should track 0 bytes");

         // Reload with address-settings changed to map test.# to quota2
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="quota1">
                     <max-addresses>10</max-addresses>
                     <max-queues>20</max-queues>
                     <max-message-bytes>500000</max-message-bytes>
                  </resource-quota>
                  <resource-quota name="quota2">
                     <max-addresses>20</max-addresses>
                     <max-queues>40</max-queues>
                     <max-message-bytes>1000000</max-message-bytes>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="test.#">
                     <resource-quota>quota2</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify all counters moved from quota1 to quota2
         quota1 = quotaManager.getQuota("quota1");
         quota2 = quotaManager.getQuota("quota2");
         assertEquals(0, quota1.getCurrentAddressCount(), "quota1 should no longer track addresses");
         assertEquals(0, quota1.getCurrentQueueCount(), "quota1 should no longer track queues");
         assertEquals(0, quota1.getCurrentMessageBytes(), "quota1 should no longer track bytes");

         assertEquals(3, quota2.getCurrentAddressCount(), "quota2 should now track 3 addresses");
         assertEquals(6, quota2.getCurrentQueueCount(), "quota2 should now track 6 queues");
         assertEquals(bytesBefore, quota2.getCurrentMessageBytes(), "quota2 should now track all bytes");

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Address-settings mapping change with parent hierarchy.
    * Verifies that when addresses are remapped from quota1 (partOf=parent1) to
    * quota2 (partOf=parent2), the parent chain counters are also rebalanced.
    */
   @Test
   public void testAddressSettingsQuotaMappingChangeWithHierarchy() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      writeConfig(brokerXML, """
            <resource-quotas>
               <resource-quota name="parent1">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="parent2">
                  <max-addresses>100</max-addresses>
               </resource-quota>
               <resource-quota name="quota1">
                  <part-of>parent1</part-of>
                  <max-addresses>10</max-addresses>
               </resource-quota>
               <resource-quota name="quota2">
                  <part-of>parent2</part-of>
                  <max-addresses>20</max-addresses>
               </resource-quota>
            </resource-quotas>
            <address-settings>
               <address-setting match="test.#">
                  <resource-quota>quota1</resource-quota>
               </address-setting>
            </address-settings>""");

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(0);
      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ResourceQuotaManager quotaManager = embeddedActiveMQ.getActiveMQServer()
            .getResourceQuotaService().getResourceQuotaManager();

         // Verify hierarchy: quota1→parent1, quota2→parent2
         ResourceQuota quota1 = quotaManager.getQuota("quota1");
         ResourceQuota quota2 = quotaManager.getQuota("quota2");
         ResourceQuota parent1 = quotaManager.getQuota("parent1");
         ResourceQuota parent2 = quotaManager.getQuota("parent2");
         assertNotNull(quota1);
         assertNotNull(quota2);
         assertNotNull(parent1);
         assertNotNull(parent2);
         assertNotNull(quota1.getParent(), "quota1 should have parent");
         assertEquals("parent1", quota1.getParent().getName());
         assertNotNull(quota2.getParent(), "quota2 should have parent");
         assertEquals("parent2", quota2.getParent().getName());

         // Create 4 addresses under test.# (mapped to quota1 initially)
         for (int i = 0; i < 4; i++) {
            AddressInfo addr = new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST);
            embeddedActiveMQ.getActiveMQServer().addAddressInfo(addr);
         }

         // Verify quota1 and parent1 track the addresses
         quota1 = quotaManager.getQuota("quota1");
         parent1 = quotaManager.getQuota("parent1");
         assertEquals(4, quota1.getCurrentAddressCount(), "quota1 should track 4 addresses");
         assertEquals(4, parent1.getCurrentAddressCount(), "parent1 should track 4 addresses via propagation");
         assertEquals(0, quota2.getCurrentAddressCount(), "quota2 should track 0");
         assertEquals(0, parent2.getCurrentAddressCount(), "parent2 should track 0");

         // Reload with address-settings changed to map test.# to quota2
         reloadConfiguration(embeddedActiveMQ, brokerXML, """
               <resource-quotas>
                  <resource-quota name="parent1">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="parent2">
                     <max-addresses>100</max-addresses>
                  </resource-quota>
                  <resource-quota name="quota1">
                     <part-of>parent1</part-of>
                     <max-addresses>10</max-addresses>
                  </resource-quota>
                  <resource-quota name="quota2">
                     <part-of>parent2</part-of>
                     <max-addresses>20</max-addresses>
                  </resource-quota>
               </resource-quotas>
               <address-settings>
                  <address-setting match="test.#">
                     <resource-quota>quota2</resource-quota>
                  </address-setting>
               </address-settings>""", latch);

         // Verify counters moved from quota1→parent1 chain to quota2→parent2 chain
         quota1 = quotaManager.getQuota("quota1");
         quota2 = quotaManager.getQuota("quota2");
         parent1 = quotaManager.getQuota("parent1");
         parent2 = quotaManager.getQuota("parent2");

         assertEquals(0, quota1.getCurrentAddressCount(), "quota1 should no longer track addresses");
         assertEquals(0, parent1.getCurrentAddressCount(), "parent1 should no longer track addresses");
         assertEquals(4, quota2.getCurrentAddressCount(), "quota2 should now track 4 addresses");
         assertEquals(4, parent2.getCurrentAddressCount(), "parent2 should track 4 via propagation");

      } finally {
         embeddedActiveMQ.stop();
      }
   }
}
