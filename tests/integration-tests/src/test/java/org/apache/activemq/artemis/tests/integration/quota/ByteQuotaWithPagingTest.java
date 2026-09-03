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
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaService;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that byte quota is enforced consistently regardless of whether
 * addresses are paging or not. The quota provides a hard bound on broker
 * resource usage for message storage.
 */
public class ByteQuotaWithPagingTest extends ActiveMQTestBase {

   private static final int MESSAGE_SIZE = 1024; // 1KB message body
   // Actual persistent size includes headers, properties, and encoding overhead (~34 bytes)
   private static final int MESSAGE_SIZE_ON_BROKER = 1084 + 72; // Actual size tracked in quota

   private static final long QUOTA_LIMIT = 15 * MESSAGE_SIZE_ON_BROKER; // 10KB quota

   @Test
   public void testByteQuotaEnforcedWhenAddressIsPaging() throws Exception {
      Configuration config = createDefaultConfig(true); // persistence enabled for paging

      // Create quota with 10KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(QUOTA_LIMIT);
      config.addResourceQuota("test-quota", quotaConfig);

      // Configure address to page after 5KB (half the quota)
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(5 * 1024L); // Page after 5KB
      settings.setPageSizeBytes(2 * 1024); // 2KB pages
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("paging.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("paging.test");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("paging.queue").setAddress(address));

         ClientProducer producer = session.createProducer(address);

         // Send messages until quota exceeded
         // Should be enforced at ~10KB regardless of paging
         boolean quotaExceeded = false;
         int messagesSent = 0;

         for (int i = 0; i < 20; i++) {
            try {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[MESSAGE_SIZE]);
               producer.send(message);
               messagesSent++;
            } catch (ActiveMQException e) {
               if (e instanceof ActiveMQResourceQuotaExceededException && e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
                  quotaExceeded = true;
                  break;
               }
            }
         }

         assertTrue(quotaExceeded,
            "Byte quota should be enforced even when address is paging. Sent " + messagesSent + " messages without quota exception!");

         // Allow some overhead for message headers
         assertTrue(messagesSent >= 8 && messagesSent <= 19,
            "Expected ~10 messages before quota (10KB / 1KB), but sent " + messagesSent);

         // Verify the address actually started paging
         Queue queue = server.locateQueue(SimpleString.of("paging.queue"));
         assertNotNull(queue);
         assertTrue(queue.getPageSubscription().getPagingStore().isPaging(),
            "Address should have started paging at 5KB (before quota limit at 10KB)");

         // Verify quota tracking
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");
         assertNotNull(quota);

         long currentBytes = quota.getCurrentMessageBytes();
         assertTrue(currentBytes >= QUOTA_LIMIT * 0.9 && currentBytes <= QUOTA_LIMIT * 1.1,
            "Quota should track ~10KB, but tracking " + currentBytes + " bytes");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testByteQuotaConsistentAcrossPagingAndNonPaging() throws Exception {
      Configuration config = createDefaultConfig(true);

      // Create quota with 20KB byte limit shared across two addresses
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("shared-quota");
      quotaConfig.setMaxMessageBytes(20 * 1024L); // 20KB total
      config.addResourceQuota("shared-quota", quotaConfig);

      // Configure one address to page aggressively (paging.*)
      AddressSettings pagingSettings = new AddressSettings();
      pagingSettings.setResourceQuota("shared-quota");
      pagingSettings.setMaxSizeBytes(2 * 1024L); // Page after just 2KB
      pagingSettings.setPageSizeBytes(1024);
      pagingSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("paging.#", pagingSettings);

      // Configure another address to never page (memory.*)
      AddressSettings memorySettings = new AddressSettings();
      memorySettings.setResourceQuota("shared-quota");
      memorySettings.setMaxSizeBytes(-1L); // Never page locally
      memorySettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("memory.#", memorySettings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString pagingAddr = SimpleString.of("paging.address");
         SimpleString memoryAddr = SimpleString.of("memory.address");

         session.createAddress(pagingAddr, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("paging.queue").setAddress(pagingAddr));

         session.createAddress(memoryAddr, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("memory.queue").setAddress(memoryAddr));

         ClientProducer pagingProducer = session.createProducer(pagingAddr);
         ClientProducer memoryProducer = session.createProducer(memoryAddr);

         // Send 10KB to paging address (will page)
         for (int i = 0; i < 10; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[1024]);
            pagingProducer.send(message);
         }

         // Verify paging started
         Queue pagingQueue = server.locateQueue(SimpleString.of("paging.queue"));
         assertTrue(pagingQueue.getPageSubscription().getPagingStore().isPaging(),
            "Paging address should be paging");

         // Try to send 10KB more to memory address
         // Should hit quota at ~20KB total across both addresses
         boolean quotaExceeded = false;
         int memoryMessagesSent = 0;

         for (int i = 0; i < 15; i++) {
            try {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[1024]);
               memoryProducer.send(message);
               memoryMessagesSent++;
            } catch (ActiveMQResourceQuotaExceededException e) {
               quotaExceeded = true;
               break;
            } catch (ActiveMQException e) {
               if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
                  quotaExceeded = true;
                  break;
               }
               throw e;
            }
         }

         assertTrue(quotaExceeded,
            "Quota should be enforced consistently - sent 10KB to paging address + " + memoryMessagesSent +
            "KB to memory address without hitting 20KB quota!");

         // Verify total is ~20KB (allow overhead)
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("shared-quota");
         long totalBytes = quota.getCurrentMessageBytes();

         assertTrue(totalBytes >= 18 * 1024 && totalBytes <= 22 * 1024,
            "Expected ~20KB total across paging and non-paging addresses, but got " + totalBytes + " bytes");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testByteQuotaWithLargeMessages() throws Exception {
      Configuration config = createDefaultConfig(true);

      // Create quota with 50KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("large-msg-quota");
      quotaConfig.setMaxMessageBytes(50 * 1024L);
      config.addResourceQuota("large-msg-quota", quotaConfig);

      // Configure to page, with large message threshold at 10KB
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("large-msg-quota");
      settings.setMaxSizeBytes(30 * 1024L); // Page after 30KB
      settings.setPageSizeBytes(10 * 1024);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("large.#", settings);

      // Set min large message size to 10KB
      config.setJournalFileSize(1024 * 1024); // 1MB journal files

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setMinLargeMessageSize(10 * 1024); // 10KB threshold for large messages
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("large.messages");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("large.messages").setAddress(address));

         ClientProducer producer = session.createProducer(address);

         // Send large messages (15KB each) until quota exceeded
         boolean quotaExceeded = false;
         int largeMessagesSent = 0;

         for (int i = 0; i < 10; i++) {
            try {
               ClientMessage message = session.createMessage(true);
               // 15KB message - should be treated as large message
               message.getBodyBuffer().writeBytes(new byte[15 * 1024]);
               producer.send(message);
               largeMessagesSent++;
            } catch (ActiveMQException e) {
               if (e.getCause() instanceof ActiveMQResourceQuotaExceededException && e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
                  quotaExceeded = true;
                  break;
               }
               throw e;
            }
         }

         // Get quota for verification
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("large-msg-quota");

         assertTrue(quotaExceeded,
            "Byte quota should enforce limit for large messages. Sent " + largeMessagesSent + " large messages without quota exception!");

         // Should send ~3 large messages (50KB quota / 15KB per message)
         assertTrue(largeMessagesSent >= 2 && largeMessagesSent <= 4,
            "Expected ~3 large messages before quota (50KB / 15KB), but sent " + largeMessagesSent);

         // Verify quota tracking for large messages
         long currentBytes = quota.getCurrentMessageBytes();

         // Each large message is ~15KB body + ~800 bytes headers = ~16KB total
         // With 50KB quota, we can send 3 messages (48KB), but the test stops after 2 messages when the 3rd is rejected
         // So we expect ~32KB (2 * 16KB)
         long expectedBytes = largeMessagesSent * 16 * 1024L;
         assertTrue(currentBytes >= expectedBytes * 0.9 && currentBytes <= expectedBytes * 1.1,
            "Quota should track ~" + (expectedBytes / 1024) + "KB for " + largeMessagesSent + " large messages, but tracking " + currentBytes + " bytes");

         // Verify the address is not paging, memory usage below the quota, quota which includes large message content is reached earlier
         Queue queue = server.locateQueue(address);
         assertNotNull(queue);
         assertFalse(queue.getPageSubscription().getPagingStore().isPaging(), "Address should not have started paging");

         ClientConsumer consumer = session.createConsumer(address);
         session.start();

         // Consume messages to free up quota space
         int messagesToConsume = largeMessagesSent;
         for (int i = 0; i < messagesToConsume; i++) {
            ClientMessage received = consumer.receive(5000);
            assertNotNull(received, "Should receive message " + i);
            received.acknowledge();
            session.commit();
         }

         // Wait for quota to be updated after consumption
         Wait.assertTrue(() -> quota.getCurrentMessageBytes() == 0,
               2000, 100);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testByteQuotaDecrementsWhenConsumingPagedMessages() throws Exception {
      Configuration config = createDefaultConfig(true);

      // Create quota with 15KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("consume-quota");
      quotaConfig.setMaxMessageBytes(15 * 1024L);
      config.addResourceQuota("consume-quota", quotaConfig);

      // Configure to page aggressively
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("consume-quota");
      settings.setMaxSizeBytes(5 * 1024L); // Page after 5KB
      settings.setPageSizeBytes(2 * 1024);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("consume.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true); // Required to get exception response for durable sends
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, false); // Auto-commit sends, manual ack

         SimpleString address = SimpleString.of("consume.test");
         SimpleString queueName = SimpleString.of("consume.queue");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of(queueName).setAddress(address));

         ClientProducer producer = session.createProducer(address);

         // Fill quota to ~15KB
         int messagesSent = 0;
         for (int i = 0; i < 20; i++) {
            try {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[1024]);
               producer.send(message);
               messagesSent++;
            } catch (ActiveMQException e) {
               if (e instanceof ActiveMQResourceQuotaExceededException ||
                   e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
                  break;
               }
               throw e;
            }
         }

         assertTrue(messagesSent >= 12 && messagesSent <= 17,
            "Should have sent ~15 messages before quota, but sent " + messagesSent);

         // Verify quota is at limit
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("consume-quota");
         long bytesBeforeConsume = quota.getCurrentMessageBytes();

         assertTrue(bytesBeforeConsume >= 13 * 1024,
            "Quota should be near limit before consuming");

         // Now consume and ack some messages
         ClientConsumer consumer = session.createConsumer(queueName);
         session.start();

         // Consume some messages to free up quota space
         int messagesToConsume = 6;
         for (int i = 0; i < messagesToConsume; i++) {
            ClientMessage received = consumer.receive(5000);
            assertNotNull(received, "Should receive message " + i);
            received.acknowledge();
            session.commit();
         }

         // Wait for quota to be updated after consumption
         Wait.assertTrue(() -> quota.getCurrentMessageBytes() < bytesBeforeConsume,
            2000, 100);

         long bytesAfterConsume = quota.getCurrentMessageBytes();

         // Bytes should have decreased significantly (now that refDown decrements by persistent size)
         assertTrue(bytesAfterConsume < bytesBeforeConsume * 0.7,
            "Quota should decrease when consuming paged messages. Before: " + bytesBeforeConsume +
            " After: " + bytesAfterConsume);

         // Should now be able to send more messages since quota freed up
         boolean canSendMore = false;
         try {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[1024]);
            producer.send(message);
            canSendMore = true;
         } catch (ActiveMQException e) {
            e.printStackTrace();
         }

         assertTrue(canSendMore,
            "Should be able to send more messages after consuming and freeing quota space");


         messagesToConsume = messagesSent - messagesToConsume + 1;
         for (int i = 0; i < messagesToConsume; i++) {
            ClientMessage received = consumer.receive(5000);
            assertNotNull(received, "Should receive message " + i);
            received.acknowledge();
            session.commit();
         }

         session.close();
         locator.close();

         // Wait for quota to be updated after consumption
         Wait.assertTrue(() -> quota.getCurrentMessageBytes() == 0,
               2000, 100);

      } finally {
         server.stop();
      }
   }

   @Test
   public void testByteQuotaEnforcedBeforePageFileCreated() throws Exception {
      Configuration config = createDefaultConfig(true);

      // Create very tight quota - smaller than typical page file
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("tight-quota");
      quotaConfig.setMaxMessageBytes(3 * 1024L); // Only 3KB
      config.addResourceQuota("tight-quota", quotaConfig);

      // Configure paging with larger page size than quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("tight-quota");
      settings.setMaxSizeBytes(1024L); // Page after 1KB (before quota)
      settings.setPageSizeBytes(10 * 1024); // 10KB page files (larger than quota!)
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("tight.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("tight.quota");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("tight.queue").setAddress(address));

         ClientProducer producer = session.createProducer(address);

         // Send messages - quota should enforce at 3KB even though page files are 10KB
         boolean quotaExceeded = false;
         int messagesSent = 0;

         for (int i = 0; i < 10; i++) {
            try {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[1024]); // 1KB each
               producer.send(message);
               messagesSent++;
            } catch (ActiveMQResourceQuotaExceededException e) {
               quotaExceeded = true;
               break;
            } catch (ActiveMQException e) {
               if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
                  quotaExceeded = true;
                  break;
               }
               throw e;
            }
         }

         assertTrue(quotaExceeded,
            "Quota should enforce 3KB limit regardless of 10KB page file size. Sent " + messagesSent + " without quota exception!");

         assertTrue(messagesSent >= 2 && messagesSent <= 4,
            "Expected ~3 messages before 3KB quota, but sent " + messagesSent);

         // Verify quota is the limiting factor, not page file size
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("tight-quota");
         long currentBytes = quota.getCurrentMessageBytes();

         assertTrue(currentBytes <= 4 * 1024,
            "Quota enforcement should limit storage to ~3KB, not allow filling a 10KB page file. Current: " + currentBytes);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }
}
