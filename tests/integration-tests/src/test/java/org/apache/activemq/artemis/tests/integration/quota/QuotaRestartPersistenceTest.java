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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQResourceQuotaExceededException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that quota state is correctly rebuilt after server restart.
 * Verifies that quotas survive restart and continue enforcing limits.
 *
 */
public class QuotaRestartPersistenceTest extends ActiveMQTestBase {

   @Test
   public void testAddressQuotaRebuildAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);  // persistence enabled

      // Create quota with max 3 addresses
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(3);
      config.addResourceQuota("test-quota", quotaConfig);

      // Configure address settings to use this quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      try {
         // Create 2 addresses before restart
         AddressInfo addr1 = new AddressInfo(SimpleString.of("test.addr1"), RoutingType.ANYCAST);
         server.addAddressInfo(addr1);

         AddressInfo addr2 = new AddressInfo(SimpleString.of("test.addr2"), RoutingType.ANYCAST);
         server.addAddressInfo(addr2);

         // Verify addresses exist
         assertNotNull(server.getAddressInfo(SimpleString.of("test.addr1")));
         assertNotNull(server.getAddressInfo(SimpleString.of("test.addr2")));

         // Get the RUNTIME quota instance by name (both addresses share same quota)
         ResourceQuota runtimeQuota = server.getResourceQuotaService()
            .getQuotaByName("test-quota");

         assertNotNull(runtimeQuota, "Runtime quota should exist");

         // Verify runtime quota count is 2 before restart
         assertEquals(2, runtimeQuota.getAddressCount(), "Before restart, runtime quota count should be 2");
      } finally {
         server.stop();
      }

      // Restart the server with same configuration
      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         // Verify addresses were restored from journal
         assertNotNull(server2.getAddressInfo(SimpleString.of("test.addr1")),
                      "addr1 should be restored after restart");
         assertNotNull(server2.getAddressInfo(SimpleString.of("test.addr2")),
                      "addr2 should be restored after restart");

         // Get the RUNTIME quota instance by name (both addresses share same quota)
         ResourceQuota runtimeQuota = server2.getResourceQuotaService()
            .getQuotaByName("test-quota");

         assertNotNull(runtimeQuota, "Runtime quota should exist");

         // CRITICAL TEST: After restart, quota counts should be rebuilt by reloading addresses
         // The count should be 2 (matching the restored addresses)
         assertEquals(2, runtimeQuota.getAddressCount(),
                     "After restart, quota count should be rebuilt to 2 - THIS IS THE BUG TEST");

         // Should be able to create one more address (limit is 3)
         AddressInfo addr3 = new AddressInfo(SimpleString.of("test.addr3"), RoutingType.ANYCAST);
         server2.addAddressInfo(addr3);
         assertEquals(3, runtimeQuota.getAddressCount(), "After adding addr3, count should be 3");

         // Fourth address should fail (quota limit reached)
         AddressInfo addr4 = new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST);
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.addAddressInfo(addr4),
            "Fourth address should exceed quota limit"
         );
         assertTrue(exception.getMessage().contains("Address quota exceeded"));
         assertTrue(exception.getMessage().contains("test-quota"));

         // Verify final count is still 3 (addr4 was not created)
         assertEquals(3, runtimeQuota.getAddressCount());

      } finally {
         server2.stop();
      }
   }

   @Test
   public void testAddressQuotaRebuildAfterDeleteAndRestart() throws Exception {
      Configuration config = createDefaultConfig(true);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxAddresses(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      try {
         for (int i = 1; i <= 4; i++) {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST));
         }

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
         assertEquals(4, quota.getAddressCount());

         // Delete two addresses before restart
         server.removeAddressInfo(SimpleString.of("test.addr2"), null);
         server.removeAddressInfo(SimpleString.of("test.addr4"), null);
         assertEquals(2, quota.getAddressCount(), "Count should be 2 after deleting two addresses");
      } finally {
         server.stop();
      }

      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         assertNotNull(server2.getAddressInfo(SimpleString.of("test.addr1")));
         assertNotNull(server2.getAddressInfo(SimpleString.of("test.addr3")));

         ResourceQuota quota = server2.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(2, quota.getAddressCount(),
            "After restart, count should reflect only surviving addresses (not deleted ones)");

         // Can create 3 more up to limit of 5
         server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr5"), RoutingType.ANYCAST));
         server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr6"), RoutingType.ANYCAST));
         server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr7"), RoutingType.ANYCAST));
         assertEquals(5, quota.getAddressCount());

         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr8"), RoutingType.ANYCAST)),
            "Sixth address should exceed quota limit"
         );
      } finally {
         server2.stop();
      }
   }

   @Test
   public void testQueueQuotaRebuildAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);  // persistence enabled

      // Create quota with max 4 queues
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxQueues(4);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      try {
         // Create address
         AddressInfo addr = new AddressInfo(SimpleString.of("test.addr"), RoutingType.ANYCAST);
         server.addAddressInfo(addr);

         // Create 2 queues before restart
         server.createQueue(QueueConfiguration.of("queue1").setAddress("test.addr").setDurable(true));
         server.createQueue(QueueConfiguration.of("queue2").setAddress("test.addr").setDurable(true));

         // Get the RUNTIME quota instance (not the config template!)
         ResourceQuota runtimeQuota = server.getResourceQuotaService()
            .lookupQuota(SimpleString.of("test.addr"));

         assertNotNull(runtimeQuota, "Runtime quota should exist");

         // Verify runtime quota count before restart
         assertEquals(2, runtimeQuota.getQueueCount(), "Before restart, runtime queue count should be 2");

      } finally {
         server.stop();
      }

      // Restart the server
      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         // Verify queues were restored
         assertNotNull(server2.locateQueue(SimpleString.of("queue1")), "queue1 should be restored");
         assertNotNull(server2.locateQueue(SimpleString.of("queue2")), "queue2 should be restored");

         // Get the RUNTIME quota instance by name
         ResourceQuota runtimeQuota = server2.getResourceQuotaService()
            .getQuotaByName("test-quota");

         assertNotNull(runtimeQuota, "Runtime quota should exist");

         // CRITICAL TEST: Quota count should be rebuilt to 2
         assertEquals(2, runtimeQuota.getQueueCount(),
                     "After restart, queue count should be rebuilt to 2");

         // Should be able to create 2 more queues (limit is 4)
         server2.createQueue(QueueConfiguration.of("queue3").setAddress("test.addr").setDurable(true));
         assertEquals(3, runtimeQuota.getQueueCount());

         server2.createQueue(QueueConfiguration.of("queue4").setAddress("test.addr").setDurable(true));
         assertEquals(4, runtimeQuota.getQueueCount());

         // Fifth queue should fail
         ActiveMQResourceQuotaExceededException exception = assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.createQueue(QueueConfiguration.of("queue5").setAddress("test.addr").setDurable(true)),
            "Fifth queue should exceed quota limit"
         );
         assertTrue(exception.getMessage().contains("Queue quota exceeded"));

      } finally {
         server2.stop();
      }
   }

   @Test
   public void testWildcardQuotaRebuildAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);  // persistence enabled

      // Create wildcard template quota "region.*" with max 2 addresses per region
      ResourceQuotaConfig regionTemplate = new ResourceQuotaConfig("region.*");
      regionTemplate.setMaxAddresses(2);
      config.addResourceQuota("region.*", regionTemplate);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      try {
         // Create 2 addresses in region.us before restart
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.orders"), RoutingType.ANYCAST));
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.us.payments"), RoutingType.ANYCAST));

         // Create 1 address in region.eu before restart
         server.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.orders"), RoutingType.ANYCAST));

      } finally {
         server.stop();
      }

      // Restart the server
      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         // Verify addresses were restored
         assertNotNull(server2.getAddressInfo(SimpleString.of("region.us.orders")));
         assertNotNull(server2.getAddressInfo(SimpleString.of("region.us.payments")));
         assertNotNull(server2.getAddressInfo(SimpleString.of("region.eu.orders")));

         // Get the RUNTIME quota instances for each region (wildcard creates separate instances)
         ResourceQuota usQuota = server2.getResourceQuotaService()
            .lookupQuota(SimpleString.of("region.us.orders"));
         ResourceQuota euQuota = server2.getResourceQuotaService()
            .lookupQuota(SimpleString.of("region.eu.orders"));

         assertNotNull(usQuota, "US quota instance should exist");
         assertNotNull(euQuota, "EU quota instance should exist");

         // CRITICAL TEST: Each region's quota should be rebuilt
         assertEquals(2, usQuota.getAddressCount(),
                     "region.us count should be 2 after restart");
         assertEquals(1, euQuota.getAddressCount(),
                     "region.eu count should be 1 after restart");

         // region.us should be at limit (2 addresses)
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.addAddressInfo(new AddressInfo(SimpleString.of("region.us.shipping"), RoutingType.ANYCAST)),
            "region.us should be at limit after restart"
         );

         // region.eu should have capacity for 1 more
         server2.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.payments"), RoutingType.ANYCAST));
         assertEquals(2, euQuota.getAddressCount());

         // Now region.eu should also be at limit
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.addAddressInfo(new AddressInfo(SimpleString.of("region.eu.shipping"), RoutingType.ANYCAST)),
            "region.eu should be at limit after adding second address"
         );

      } finally {
         server2.stop();
      }
   }

   @Test
   public void testHierarchicalQuotaRebuildAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);  // persistence enabled

      // Create parent quota with total limit of 5 addresses
      ResourceQuotaConfig parentQuota = new ResourceQuotaConfig("parent");
      parentQuota.setMaxAddresses(5);
      config.addResourceQuota("parent", parentQuota);

      // Create child quota with higher limit but part of parent
      ResourceQuotaConfig childQuota = new ResourceQuotaConfig("child");
      childQuota.setMaxAddresses(10);
      childQuota.setPartOf("parent");
      config.addResourceQuota("child", childQuota);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("child");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      try {
         // Create 3 addresses before restart
         for (int i = 1; i <= 3; i++) {
            server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr" + i), RoutingType.ANYCAST));
         }

         // Get the RUNTIME quota instances (not the config templates!)
         ResourceQuota runtimeChild = server.getResourceQuotaService()
            .getQuotaByName("child");

         assertNotNull(runtimeChild, "Runtime child quota should exist");
         ResourceQuota runtimeParent = runtimeChild.getParent();
         assertNotNull(runtimeParent, "Runtime parent quota should be linked");

         // Verify runtime counts before restart
         assertEquals(3, runtimeChild.getAddressCount(), "Runtime child count before restart");
         assertEquals(3, runtimeParent.getAddressCount(), "Runtime parent count before restart");
      } finally {
         server.stop();
      }

      // Restart the server
      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         // Verify addresses were restored
         for (int i = 1; i <= 3; i++) {
            assertNotNull(server2.getAddressInfo(SimpleString.of("test.addr" + i)),
                         "addr" + i + " should be restored");
         }

         // Get the RUNTIME quota instances (not the config templates!)
         ResourceQuota runtimeChild = server2.getResourceQuotaService()
            .getQuotaByName("child");

         assertNotNull(runtimeChild, "Runtime child quota should exist");

         // Get parent from child (they should be linked at runtime)
         ResourceQuota runtimeParent = runtimeChild.getParent();
         assertNotNull(runtimeParent, "Parent quota should be linked");

         // CRITICAL TEST: Quota counts should be rebuilt
         assertEquals(3, runtimeChild.getAddressCount(),
                     "Child count after restart should be 3 - THIS IS THE BUG TEST");
         assertEquals(3, runtimeParent.getAddressCount(),
                     "Parent count after restart should be 3 - THIS IS THE BUG TEST");

         // Should be able to create 2 more (parent limit is 5)
         server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr4"), RoutingType.ANYCAST));
         server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr5"), RoutingType.ANYCAST));

         assertEquals(5, runtimeChild.getAddressCount());
         assertEquals(5, runtimeParent.getAddressCount());

         // Sixth address should fail due to parent limit
         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.addAddressInfo(new AddressInfo(SimpleString.of("test.addr6"), RoutingType.ANYCAST)),
            "Should fail due to parent quota limit"
         );

      } finally {
         server2.stop();
      }
   }

   @Test
   public void testWildcardByteQuotaRebuildAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);

      ResourceQuotaConfig globalConfig = new ResourceQuotaConfig("global");
      globalConfig.setMaxMessageBytes(100 * 1600L);
      config.addResourceQuota("global", globalConfig);

      ResourceQuotaConfig regionConfig = new ResourceQuotaConfig("region.*");
      regionConfig.setMaxMessageBytes(50 * 1600L);
      regionConfig.setPartOf("global");
      config.addResourceQuota("region.*", regionConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("region.*");
      config.addAddressSetting("region.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      long euBytesBeforeRestart;
      long usBytesBeforeRestart;
      long globalBytesBeforeRestart;
      ServerLocator locator = null;

      try {
         locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString euAddress = SimpleString.of("region.eu.orders");
         SimpleString usAddress = SimpleString.of("region.us.orders");

         session.createAddress(euAddress, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("eu.queue").setAddress(euAddress).setDurable(true));
         session.createAddress(usAddress, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("us.queue").setAddress(usAddress).setDurable(true));

         ClientProducer euProducer = session.createProducer(euAddress);
         for (int i = 0; i < 5; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1024]);
            euProducer.send(msg);
         }

         ClientProducer usProducer = session.createProducer(usAddress);
         for (int i = 0; i < 3; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1024]);
            usProducer.send(msg);
         }

         ResourceQuota euQuota = server.getResourceQuotaService().lookupQuota(euAddress);
         ResourceQuota usQuota = server.getResourceQuotaService().lookupQuota(usAddress);
         ResourceQuota global = server.getResourceQuotaService().getQuotaByName("global");

         assertNotNull(euQuota);
         assertNotNull(usQuota);
         assertNotNull(global);

         euBytesBeforeRestart = euQuota.getCurrentMessageBytes();
         usBytesBeforeRestart = usQuota.getCurrentMessageBytes();
         globalBytesBeforeRestart = global.getCurrentMessageBytes();

         assertTrue(euBytesBeforeRestart > 0, "EU should have bytes tracked");
         assertTrue(usBytesBeforeRestart > 0, "US should have bytes tracked");
         assertEquals(euBytesBeforeRestart + usBytesBeforeRestart, globalBytesBeforeRestart,
            "Global should be sum of children before restart");

         session.close();
         sf.close();
      } finally {
         if (locator != null) {
            locator.close();
         }
         server.stop();
      }

      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         assertNotNull(server2.locateQueue(SimpleString.of("eu.queue")));
         assertNotNull(server2.locateQueue(SimpleString.of("us.queue")));

         ResourceQuota euQuota = server2.getResourceQuotaService()
            .lookupQuota(SimpleString.of("region.eu.orders"));
         ResourceQuota usQuota = server2.getResourceQuotaService()
            .lookupQuota(SimpleString.of("region.us.orders"));
         ResourceQuota global = server2.getResourceQuotaService().getQuotaByName("global");

         assertNotNull(euQuota, "EU wildcard instance should be recreated");
         assertNotNull(usQuota, "US wildcard instance should be recreated");
         assertNotNull(global);

         assertEquals(euBytesBeforeRestart, euQuota.getCurrentMessageBytes(),
            "EU byte counter should match pre-restart value");
         assertEquals(usBytesBeforeRestart, usQuota.getCurrentMessageBytes(),
            "US byte counter should match pre-restart value");
         assertEquals(globalBytesBeforeRestart, global.getCurrentMessageBytes(),
            "Global byte counter should be sum of children after restart");
      } finally {
         server2.stop();
      }
   }

   @Test
   public void testByteQuotaRebuildAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);  // persistence enabled

      // Create quota with max 10KB message bytes
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(10 * 1600L);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      long bytesBeforeRestart;
      ServerLocator locator = null;
      try {
         locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true); // Required to get exception response for durable sends
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         // Create address and queue
         SimpleString address = SimpleString.of("test.addr");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setDurable(true));

         ClientProducer producer = session.createProducer(address);

         for (int i = 0; i < 5; i++) {
            ClientMessage message = session.createMessage(true); // durable
            message.getBodyBuffer().writeBytes(new byte[1024]); // 1KB payload
            producer.send(message);
         }

         // Get the RUNTIME quota instance
         ResourceQuota runtimeQuota = server.getResourceQuotaService()
            .getQuotaByName("test-quota");

         assertNotNull(runtimeQuota, "Runtime quota should exist");

         // Verify runtime quota byte count before restart
         bytesBeforeRestart = runtimeQuota.getCurrentMessageBytes();
         assertTrue(bytesBeforeRestart >= 4 * 1024 && bytesBeforeRestart <= 8 * 1024,
                   "Before restart, runtime quota should track ~5KB, but was " + bytesBeforeRestart);

         session.close();
         sf.close();
      } finally {
         if (locator != null) {
            locator.close();
         }
         server.stop();
      }

      // Restart the server with same configuration
      ActiveMQServer server2 = createServer(config);
      server2.start();

      ServerLocator locator2 = null;
      try {
         // Verify queue was restored from journal
         assertNotNull(server2.locateQueue(SimpleString.of("test.queue")),
                      "Queue should be restored after restart");

         // Get the RUNTIME quota instance
         ResourceQuota runtimeQuota = server2.getResourceQuotaService()
            .getQuotaByName("test-quota");

         assertNotNull(runtimeQuota, "Runtime quota should exist");

         // After restart, quota byte count should be rebuilt by reloading messages
         // The count should be ~5KB (matching the restored messages)
         long bytesAfterRestart = runtimeQuota.getCurrentMessageBytes();
         assertTrue(bytesAfterRestart >= 4 * 1024 && bytesAfterRestart <= 8 * 1024,
                   "After restart, quota bytes should be rebuilt to ~5KB - THIS IS THE BUG TEST. Was: " + bytesAfterRestart);

         assertEquals(bytesBeforeRestart, bytesAfterRestart, "rebuild should match original");

         // Should be able to send ~5KB more (limit is 10KB)
         locator2 = createInVMNonHALocator();
         locator2.setBlockOnDurableSend(true); // Required to get exception response for durable sends
         ClientSessionFactory sf2 = createSessionFactory(locator2);
         ClientSession session2 = sf2.createSession(false, true, true);
         ClientProducer producer2 = session2.createProducer(SimpleString.of("test.addr"));

         int additionalMessagesSent = 0;
         for (int i = 0; i < 5; i++) {
            ClientMessage message = session2.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[1024]); // 1KB payload
            producer2.send(message);
            additionalMessagesSent++;
         }
         assertEquals(5, additionalMessagesSent, "Should send 5 more messages before quota");

         // Verify we're now at the ~10KB limit
         long bytesAtLimit = runtimeQuota.getCurrentMessageBytes();
         assertTrue(bytesAtLimit >= 9 * 1024 && bytesAtLimit <= 16 * 1024,
                   "After sending to limit, should be at ~10KB, but was " + bytesAtLimit);

         // Next message should fail (quota limit reached)
         boolean quotaExceeded = false;
         try {
            ClientMessage message = session2.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[1024]);
            producer2.send(message);
         } catch (ActiveMQResourceQuotaExceededException e) {
            quotaExceeded = true;
            assertTrue(e.getMessage().contains("byte quota exceeded") ||
                      e.getMessage().contains("Resource quota exceeded"));
            assertTrue(e.getMessage().contains("test-quota"));
         } catch (ActiveMQException e) {
            if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
               quotaExceeded = true;
            } else {
               throw e;
            }
         }

         assertTrue(quotaExceeded, "Sending beyond 10KB limit should exceed quota");

         session2.close();
         sf2.close();
      } finally {
         if (locator2 != null) {
            locator2.close();
         }
         server2.stop();
      }
   }

   @Test
   public void testNonDurableQueueQuotaNotCountedAfterRestart() throws Exception {
      Configuration config = createDefaultConfig(true);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxQueues(5);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(config);
      server.start();

      try {
         server.addAddressInfo(new AddressInfo(SimpleString.of("test.addr"), RoutingType.ANYCAST));

         server.createQueue(QueueConfiguration.of("durable1").setAddress("test.addr").setDurable(true));
         server.createQueue(QueueConfiguration.of("durable2").setAddress("test.addr").setDurable(true));
         server.createQueue(QueueConfiguration.of("temp1").setAddress("test.addr").setDurable(false));
         server.createQueue(QueueConfiguration.of("temp2").setAddress("test.addr").setDurable(false));

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);
         assertEquals(4, quota.getQueueCount(), "All 4 queues should be counted before restart");
      } finally {
         server.stop();
      }

      ActiveMQServer server2 = createServer(config);
      server2.start();

      try {
         assertNotNull(server2.locateQueue(SimpleString.of("durable1")));
         assertNotNull(server2.locateQueue(SimpleString.of("durable2")));

         ResourceQuota quota = server2.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);

         assertEquals(2, quota.getQueueCount(),
            "After restart, only durable queues should be counted (non-durable queues don't survive restart)");

         // Can create 3 more up to limit of 5
         server2.createQueue(QueueConfiguration.of("durable3").setAddress("test.addr").setDurable(true));
         server2.createQueue(QueueConfiguration.of("durable4").setAddress("test.addr").setDurable(true));
         server2.createQueue(QueueConfiguration.of("durable5").setAddress("test.addr").setDurable(true));
         assertEquals(5, quota.getQueueCount());

         assertThrows(
            ActiveMQResourceQuotaExceededException.class,
            () -> server2.createQueue(QueueConfiguration.of("durable6").setAddress("test.addr").setDurable(true)),
            "Sixth queue should exceed quota limit"
         );
      } finally {
         server2.stop();
      }
   }

   @Test
   public void testPagedByteQuotaAccurateAfterRestartEventually() throws Exception {
      Configuration config = createDefaultConfig(true);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(500 * 1024L);
      config.addResourceQuota("test-quota", quotaConfig);

      // Force paging early: max-size-bytes=1024 means only ~1 message fits in memory,
      // the rest go to page files. Page files are NOT rebuilt synchronously on restart.
      // pageSizeBytes is larger than individual messages so numberOfPages * pageSizeBytes
      // produces a conservative overestimate.
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(1024L);
      settings.setPageSizeBytes(10 * 1024);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("paging.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      long bytesBeforeRestart;
      ServerLocator locator = null;

      try {
         locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("paging.test");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("paging.queue").setAddress(address).setDurable(true));

         ClientProducer producer = session.createProducer(address);

         // Send 20 messages — with max-size-bytes=1024, most will be paged
         for (int i = 0; i < 20; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1024]);
            producer.send(msg);
         }

         // Confirm paging is active
         Queue queue = server.locateQueue(SimpleString.of("paging.queue"));
         assertTrue(queue.getPageSubscription().getPagingStore().isPaging(),
            "Address must be paging with max-size-bytes=1024");

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);
         bytesBeforeRestart = quota.getCurrentMessageBytes();
         assertTrue(bytesBeforeRestart > 10_000,
            "Should have significant bytes tracked before restart, was " + bytesBeforeRestart);

         session.close();
         sf.close();
      } finally {
         if (locator != null) {
            locator.close();
         }
         server.stop();
      }

      // Restart — rebuildQuotaCounters runs asynchronously on the paging executor.
      ActiveMQServer server2 = createServer(true, config);
      server2.start();

      try {
         ResourceQuota quota = server2.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);

         long bytesAfterRestart = quota.getCurrentMessageBytes();

         // The byte counter must reflect paged data immediately after restart
         // via the preliminary estimate (numberOfPages * pageSizeBytes).
         assertTrue(bytesAfterRestart > bytesBeforeRestart / 2,
            "Byte counter must include paged data immediately after restart. " +
            "Before restart: " + bytesBeforeRestart + ", after restart: " + bytesAfterRestart +
            ". The gap indicates paged bytes were not yet rebuilt (Race 1).");

         // Enforcement: the quota must know it's non-empty so it won't allow
         // another full quota's worth of data.
         assertFalse(quota.canAddBytes(quota.getMaxMessageBytes()),
            "canAddBytes should deny adding the full quota limit again, " +
            "but the counter is only " + bytesAfterRestart + " — quota doesn't see paged bytes yet");

         // eventually it is correct
         assertTrue(Wait.waitFor((Wait.Condition) () -> bytesBeforeRestart == quota.getCurrentMessageBytes()));

      } finally {
         server2.stop();
      }
   }

   @Test
   public void testPagedByteQuotaAccurateAfterRestartMulticastTwoSubs() throws Exception {
      Configuration config = createDefaultConfig(true);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(500 * 1024L);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(1024L);
      settings.setPageSizeBytes(10 * 1024);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("paging.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      long bytesBeforeRestart;
      ServerLocator locator = null;

      try {
         locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("paging.multicast");
         session.createAddress(address, RoutingType.MULTICAST, false);
         session.createQueue(QueueConfiguration.of("sub1").setAddress(address).setDurable(true));
         session.createQueue(QueueConfiguration.of("sub2").setAddress(address).setDurable(true));

         ClientProducer producer = session.createProducer(address);

         for (int i = 0; i < 20; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[1024]);
            producer.send(msg);
         }

         Queue queue = server.locateQueue(SimpleString.of("sub1"));
         assertTrue(queue.getPageSubscription().getPagingStore().isPaging(),
            "Address must be paging with max-size-bytes=1024");

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);
         bytesBeforeRestart = quota.getCurrentMessageBytes();
         assertTrue(bytesBeforeRestart > 10_000,
            "Should have significant bytes tracked before restart, was " + bytesBeforeRestart);

         session.close();
         sf.close();
      } finally {
         if (locator != null) {
            locator.close();
         }
         server.stop();
      }

      ActiveMQServer server2 = createServer(true, config);
      server2.start();

      try {
         ResourceQuota quota = server2.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);

         assertTrue(Wait.waitFor((Wait.Condition) () -> bytesBeforeRestart == quota.getCurrentMessageBytes()), "Byte counter must match after restart with two multicast subscriptions. " +
            "Before restart: " + bytesBeforeRestart + ", after restart: " + quota.getCurrentMessageBytes() +
            "; should match if page counters match quota.");

      } finally {
         server2.stop();
      }
   }

   @Test
   public void testPagedLargeMessageByteQuotaAccurateAfterRestart() throws Exception {
      final int largeMessageThreshold = 10 * 1024;
      final int largeMessageBodySize = 20 * 1024;

      Configuration config = createDefaultConfig(true);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(2 * 1024 * 1024L);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(1024L);
      settings.setPageSizeBytes(10 * 1024);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("paging.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      long bytesBeforeRestart;
      ServerLocator locator = null;

      try {
         locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         locator.setMinLargeMessageSize(largeMessageThreshold);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("paging.large");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("paging.large.queue").setAddress(address).setDurable(true));

         ClientProducer producer = session.createProducer(address);

         for (int i = 0; i < 10; i++) {
            ClientMessage msg = session.createMessage(true);
            msg.getBodyBuffer().writeBytes(new byte[largeMessageBodySize]);
            producer.send(msg);
         }

         Queue queue = server.locateQueue(SimpleString.of("paging.large.queue"));
         assertTrue(queue.getPageSubscription().getPagingStore().isPaging(),
            "Address must be paging");

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);
         bytesBeforeRestart = quota.getCurrentMessageBytes();
         assertTrue(bytesBeforeRestart > 10 * largeMessageBodySize,
            "Quota should include large message body sizes, was " + bytesBeforeRestart);

         session.close();
         sf.close();
      } finally {
         if (locator != null) {
            locator.close();
         }
         server.stop();
      }

      ActiveMQServer server2 = createServer(true, config);
      server2.start();

      try {
         ResourceQuota quota = server2.getResourceQuotaService().getQuotaByName("test-quota");
         assertNotNull(quota);

         assertTrue(Wait.waitFor((Wait.Condition) () -> bytesBeforeRestart == quota.getCurrentMessageBytes()),
            "Byte counter must match after restart with large messages. " +
            "Before restart: " + bytesBeforeRestart + ", after restart: " + quota.getCurrentMessageBytes());

      } finally {
         server2.stop();
      }
   }
}
