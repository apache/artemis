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
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for byte quota enforcement across wildcard addresses with multicast routing.
 *
 * Verifies that:
 * 1. Multiple addresses under a wildcard quota (e.g., a.#) share the same byte quota
 * 2. Publishing to different addresses (a.1, a.2, a.3) accumulates against the shared quota
 * 3. When quota is exceeded, publishes to ANY address matching the wildcard fail
 * 4. Multicast routing correctly accounts for N queue references in quota
 */
public class WildcardMulticastByteQuotaTest extends ActiveMQTestBase {

   /**
    * Test that byte quota is shared across multiple addresses matching a wildcard,
    * and that publishing eventually fails when the shared quota is full.
    *
    * Scenario:
    * - Wildcard quota "a.#" with 5KB byte limit
    * - Create multicast queue on "a.#" (consumes messages from a.1, a.2, a.3, etc.)
    * - Consumer stays offline (messages accumulate)
    * - Publish to a.1, a.2, a.3 until quota fills
    * - Next publish to any a.X should fail
    */
   @Test
   public void testWildcardByteQuotaSharedAcrossAddresses() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create shared quota for wildcard "a.#" with 5KB limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("a-quota");
      quotaConfig.setMaxMessageBytes(5120L); // 5KB
      config.addResourceQuota("a-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("a-quota");
      settings.setMaxSizeBytes(-1L); // Disable paging limit, only quota limit
      settings.setAddressFullMessagePolicy(org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("a.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         // Create addresses a.1, a.2, a.3 with MULTICAST routing
         SimpleString addr1 = SimpleString.of("a.1");
         SimpleString addr2 = SimpleString.of("a.2");
         SimpleString addr3 = SimpleString.of("a.3");

         session.createAddress(addr1, RoutingType.MULTICAST, false);
         session.createAddress(addr2, RoutingType.MULTICAST, false);
         session.createAddress(addr3, RoutingType.MULTICAST, false);

         // Create multicast queue on wildcard "a.#" - receives messages from all a.* addresses
         SimpleString queueName = SimpleString.of("multicast-queue");
         SimpleString wildcardAddress = SimpleString.of("a.#");
         session.createQueue(QueueConfiguration.of(queueName)
            .setAddress(wildcardAddress)
            .setRoutingType(RoutingType.MULTICAST));

         // Get quota instance by name (all a.* addresses share this quota)
         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("a-quota");
         assertNotNull(quota, "Quota 'a-quota' should exist");

         long initialQuota = quota.getCurrentMessageBytes();
         assertEquals(0, initialQuota, "Quota should start at 0");

         // Verify all addresses point to the same quota
         ResourceQuota quota1 = server.getResourceQuotaService().lookupQuota(addr1);
         ResourceQuota quota2 = server.getResourceQuotaService().lookupQuota(addr2);
         ResourceQuota quota3 = server.getResourceQuotaService().lookupQuota(addr3);
         assertEquals(quota, quota1, "a.1 should use a-quota");
         assertEquals(quota, quota2, "a.2 should use a-quota");
         assertEquals(quota, quota3, "a.3 should use a-quota");

         // Create producers for each address
         ClientProducer producer1 = session.createProducer(addr1);
         ClientProducer producer2 = session.createProducer(addr2);
         ClientProducer producer3 = session.createProducer(addr3);

         // Send messages to a.1, a.2, a.3 (consumer stays offline so messages accumulate)
         // Each message ~500 bytes, so ~10 messages should fill 5KB quota
         int messageSize = 500;
         int sentCount = 0;

         try {
            // Send to a.1
            for (int i = 0; i < 4; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[messageSize]);
               producer1.send(message);
               sentCount++;
            }

            // Send to a.2
            for (int i = 0; i < 4; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[messageSize]);
               producer2.send(message);
               sentCount++;
            }

            // Send to a.3 - this should start hitting the quota limit
            for (int i = 0; i < 10; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[messageSize]);
               producer3.send(message);
               sentCount++;
            }

            // Should eventually fail - quota is shared across a.1, a.2, a.3
            fail("Expected quota/address full exception");

         } catch (org.apache.activemq.artemis.api.core.ActiveMQException e) {
            // Expected - quota exceeded (may be ResourceQuotaExceeded or AddressFull depending on policy)
            assertTrue(e.getMessage().contains("quota") || e.getMessage().contains("full"),
               "Exception should indicate quota or address full. Got: " + e.getMessage());
         }

         // Verify quota is at or near limit
         long currentQuota = quota.getCurrentMessageBytes();
         assertTrue(currentQuota >= quota.getMaxMessageBytes() * 0.9,
            "Quota should be near limit. Current: " + currentQuota + ", Max: " + quota.getMaxMessageBytes());

         // Verify we sent some messages before hitting limit
         // Note: With memory overhead, ~500 byte body = ~1000 bytes total
         // So 5KB quota fills after ~5 messages
         assertTrue(sentCount >= 4, "Should have sent at least 4 messages before quota filled. Sent: " + sentCount);

         // Try to send to a.1 again - should still fail (quota is shared)
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(new byte[messageSize]);
         assertThrows(org.apache.activemq.artemis.api.core.ActiveMQException.class,
            () -> producer1.send(message),
            "Publishing to a.1 should fail when shared quota is full");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that multicast routing correctly accounts for multiple queue references
    * when calculating byte quota.
    *
    * Scenario:
    * - Create 3 multicast queues on wildcard "b.#"
    * - Publish to b.1 (creates 3 references, one per queue)
    * - Verify quota accounts for message + 3× reference overhead
    */
   @Test
   public void testMulticastReferenceOverheadInQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("b-quota");
      quotaConfig.setMaxMessageBytes(10240L); // 10KB
      config.addResourceQuota("b-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("b-quota");
      settings.setMaxSizeBytes(-1L);
      config.addAddressSetting("b.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         // Create address b.1 with MULTICAST routing
         SimpleString addr1 = SimpleString.of("b.1");
         session.createAddress(addr1, RoutingType.MULTICAST, false);

         // Create 3 multicast queues on wildcard "b.#" - each will receive a reference
         SimpleString wildcardAddress = SimpleString.of("b.#");
         session.createQueue(QueueConfiguration.of("queue1")
            .setAddress(wildcardAddress)
            .setRoutingType(RoutingType.MULTICAST));
         session.createQueue(QueueConfiguration.of("queue2")
            .setAddress(wildcardAddress)
            .setRoutingType(RoutingType.MULTICAST));
         session.createQueue(QueueConfiguration.of("queue3")
            .setAddress(wildcardAddress)
            .setRoutingType(RoutingType.MULTICAST));

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("b-quota");
         assertNotNull(quota, "Quota 'b-quota' should exist");
         long initialQuota = quota.getCurrentMessageBytes();

         // Send 1 message to b.1 (creates 3 references)
         ClientProducer producer = session.createProducer(addr1);
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(new byte[100]);
         producer.send(message);

         long finalQuota = quota.getCurrentMessageBytes();
         long delta = finalQuota - initialQuota;

         // Quota should include:
         // - Message memory estimate (~100 bytes body + overhead)
         // - 3× reference overhead (3 queues × 72 bytes each = 216 bytes)
         // Total should be significantly more than just message body (100 bytes)
         assertTrue(delta > 300, "Quota delta should account for 3 references. Delta: " + delta);

         // For wildcard quotas, we sum all matching paging stores
         // In this case, just b.1 has messages
         long pagingStoreSize = server.getPagingManager().getPageStore(addr1).getAddressSize();

         // Quota should be close to paging store size (allowing for overhead variations)
         long difference = Math.abs(pagingStoreSize - finalQuota);
         assertTrue(difference < 300,
            "Quota should be close to paging store size. Quota: " + finalQuota +
            ", PagingStore: " + pagingStoreSize + ", Difference: " + difference);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that publishing to different addresses under same wildcard quota
    * all contribute to the shared byte limit.
    */
   @Test
   public void testMultipleAddressesShareWildcardByteQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Small quota for testing
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("c-quota");
      quotaConfig.setMaxMessageBytes(3000L); // 3KB
      config.addResourceQuota("c-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("c-quota");
      settings.setMaxSizeBytes(-1L);
      settings.setAddressFullMessagePolicy(org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("c.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         // Create addresses c.1, c.2, c.3 with ANYCAST routing (simpler for this test)
         SimpleString addr1 = SimpleString.of("c.1");
         SimpleString addr2 = SimpleString.of("c.2");
         SimpleString addr3 = SimpleString.of("c.3");

         session.createAddress(addr1, RoutingType.ANYCAST, false);
         session.createAddress(addr2, RoutingType.ANYCAST, false);
         session.createAddress(addr3, RoutingType.ANYCAST, false);

         session.createQueue(QueueConfiguration.of("c.1").setAddress(addr1).setRoutingType(RoutingType.ANYCAST));
         session.createQueue(QueueConfiguration.of("c.2").setAddress(addr2).setRoutingType(RoutingType.ANYCAST));
         session.createQueue(QueueConfiguration.of("c.3").setAddress(addr3).setRoutingType(RoutingType.ANYCAST));

         ResourceQuota quota = server.getResourceQuotaService().getQuotaByName("c-quota");
         assertNotNull(quota, "Quota 'c-quota' should exist");

         ClientProducer producer1 = session.createProducer(addr1);
         ClientProducer producer2 = session.createProducer(addr2);
         ClientProducer producer3 = session.createProducer(addr3);

         // Send messages until quota fills
         // Each message ~300 bytes body, ~700 bytes total with overhead
         // 3KB quota should fit ~4 messages
         int messagesSent = 0;
         boolean quotaExceeded = false;

         try {
            // Send to c.1
            for (int i = 0; i < 5; i++) {
               ClientMessage msg = session.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[300]);
               producer1.send(msg);
               messagesSent++;
            }

            long afterC1 = quota.getCurrentMessageBytes();
            assertTrue(afterC1 > 0, "Quota should increase after sending to c.1");

            // Send to c.2
            for (int i = 0; i < 5; i++) {
               ClientMessage msg = session.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[300]);
               producer2.send(msg);
               messagesSent++;
            }

            long afterC2 = quota.getCurrentMessageBytes();
            assertTrue(afterC2 > afterC1, "Quota should increase further after sending to c.2");

            // Send to c.3 until quota fills
            for (int i = 0; i < 10; i++) {
               ClientMessage msg = session.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[300]);
               producer3.send(msg);
               messagesSent++;
            }
         } catch (org.apache.activemq.artemis.api.core.ActiveMQException e) {
            quotaExceeded = true;
            // May be quota exceeded or address full depending on policy
            assertTrue(e.getMessage().contains("quota") || e.getMessage().contains("full"),
               "Exception should indicate quota or full. Got: " + e.getMessage());
         }

         assertTrue(quotaExceeded, "Quota should eventually be exceeded");
         assertTrue(messagesSent >= 3, "Should have sent at least 3 messages before quota filled. Sent: " + messagesSent);

         // Verify quota is shared: publishing to c.1 should also fail now
         ClientMessage msg = session.createMessage(true);
         msg.getBodyBuffer().writeBytes(new byte[300]);
         assertThrows(org.apache.activemq.artemis.api.core.ActiveMQException.class,
            () -> producer1.send(msg),
            "Publishing to c.1 should fail when shared wildcard quota is full");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }
}
