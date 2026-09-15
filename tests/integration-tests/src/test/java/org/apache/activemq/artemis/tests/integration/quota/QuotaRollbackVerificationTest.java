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
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaService;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that quota byte counts are properly rolled back
 * when messages are rejected due to quota limits.
 */
public class QuotaRollbackVerificationTest extends ActiveMQTestBase {

   @Test
   public void testQuotaBytesNotIncrementedWhenMessageRejected() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with 1KB limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(1624L); // 1KB
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L); // No address limit
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(true, config); // persistence for paging
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true); // Block to get exceptions
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.rollback");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         // Get quota instance to check state
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.lookupQuota(address);

         // Verify quota starts at 0
         long initialBytes = quota.getCurrentMessageBytes();
         assertEquals(0L, initialBytes, "Quota should start at 0 bytes");

         // Send first message (400 bytes)
         ClientMessage msg1 = session.createMessage(true);
         msg1.getBodyBuffer().writeBytes(new byte[200]);
         producer.send(msg1);

         long afterFirst = quota.getCurrentMessageBytes();
         assertTrue(afterFirst > 0 && afterFirst <= 1024,
            "Quota should include first message: " + afterFirst + " bytes");

         // Send second message (400 bytes) - total ~800 bytes
         ClientMessage msg2 = session.createMessage(true);
         msg2.getBodyBuffer().writeBytes(new byte[200]);
         producer.send(msg2);

         long afterSecond = quota.getCurrentMessageBytes();
         assertTrue(afterSecond > afterFirst && afterSecond <= 1524,
            "Quota should include second message: " + afterSecond + " bytes");

         // Now try to send a message that EXCEEDS the quota
         boolean exceptionThrown = false;
         try {
            ClientMessage msg3 = session.createMessage(true);
            msg3.getBodyBuffer().writeBytes(new byte[200]); // Would exceed 1KB
            producer.send(msg3);
         } catch (ActiveMQException e) {
            if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
               exceptionThrown = true;
            } else {
               throw e; // Unexpected exception type
            }
         }

         assertTrue(exceptionThrown, "Expected quota exceeded exception");

         // CRITICAL: Verify quota was NOT incremented for the rejected message
         long afterReject = quota.getCurrentMessageBytes();
         assertEquals(afterSecond, afterReject,
            "ROLLBACK BUG: Quota should NOT include rejected message size! " +
            "Before reject: " + afterSecond + ", After reject: " + afterReject);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testQuotaBytesDecrementedOnConsumption() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(2248L); // 2KB
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.decrement");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true));

         ClientProducer producer = session.createProducer(address);

         // Get quota to monitor
         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.lookupQuota(address);

         // Send 3 messages
         for (int i = 0; i < 3; i++) {
            ClientMessage msg = session.createMessage(true);
            // this is 690 bytes of quota for the check on the server with core!
            msg.getBodyBuffer().writeBytes(new byte[200]);
            producer.send(msg);
         }

         long afterSend = quota.getCurrentMessageBytes();
         assertTrue(afterSend > 1000, "Quota should include all 3 messages: " + afterSend + " bytes");

         // Consume 2 messages
         session.start();
         var consumer = session.createConsumer("test.queue");
         ClientMessage received1 = consumer.receive(1000);
         received1.acknowledge();
         ClientMessage received2 = consumer.receive(1000);
         received2.acknowledge();
         session.commit();

         // Quota should decrement when messages are consumed
         long afterConsume = quota.getCurrentMessageBytes();
         assertTrue(afterConsume < afterSend,
            "Quota should DECREASE after consuming messages. Before: " + afterSend + ", After: " + afterConsume);

         // Should be roughly 1 message worth left
         assertTrue(afterConsume > 0 && afterConsume < afterSend / 2,
            "Quota should reflect only 1 remaining message, got: " + afterConsume + " bytes");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testMultipleRejectionsDoNotAccumulateInQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Small quota for easy testing
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(900L); // Just enough for 1 message
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.multiple");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.lookupQuota(address);

         // Send one message that fits
         ClientMessage msg1 = session.createMessage(true);
         msg1.getBodyBuffer().writeBytes(new byte[400]);
         producer.send(msg1);

         long afterFirst = quota.getCurrentMessageBytes();

         // Try to send 5 more messages that all get rejected
         for (int i = 0; i < 5; i++) {
            try {
               ClientMessage msg = session.createMessage(true);
               msg.getBodyBuffer().writeBytes(new byte[400]);
               producer.send(msg);
            } catch (ActiveMQException e) {
               // Expected - quota exceeded
            }
         }

         // Quota should still only reflect the ONE accepted message
         long afterRejects = quota.getCurrentMessageBytes();
         assertEquals(afterFirst, afterRejects,
            "ACCUMULATION BUG: Multiple rejections should not accumulate! " +
            "After 1st message: " + afterFirst + ", After 5 rejections: " + afterRejects);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testQuotaBytesNotIncrementedForRejectedNonDurableMessages() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Small quota for testing
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(1624L); // 1KB
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L); // No address limit - stay in memory
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config); // NO persistence - in-memory only
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnNonDurableSend(true); // Must block to get exceptions
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.nondurable.rollback");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.lookupQuota(address);

         // Verify quota starts at 0
         assertEquals(0L, quota.getCurrentMessageBytes(), "Quota should start at 0");

         // Send non-durable messages that fit
         ClientMessage msg1 = session.createMessage(false); // NON-DURABLE
         msg1.getBodyBuffer().writeBytes(new byte[300]);
         producer.send(msg1);

         long afterFirst = quota.getCurrentMessageBytes();
         assertTrue(afterFirst > 0, "Quota should track non-durable message: " + afterFirst);

         ClientMessage msg2 = session.createMessage(false); // NON-DURABLE
         msg2.getBodyBuffer().writeBytes(new byte[300]);
         producer.send(msg2);

         long afterSecond = quota.getCurrentMessageBytes();
         assertTrue(afterSecond > afterFirst, "Quota should track second non-durable message: " + afterSecond);

         // Now try to send non-durable message that exceeds quota
         boolean exceptionThrown = false;
         try {
            ClientMessage msg3 = session.createMessage(false); // NON-DURABLE
            msg3.getBodyBuffer().writeBytes(new byte[300]);
            producer.send(msg3);
         } catch (ActiveMQException e) {
            if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
               exceptionThrown = true;
            } else {
               throw e; // Unexpected exception
            }
         }

         assertTrue(exceptionThrown, "Expected quota exceeded exception for non-durable message");

         // CRITICAL: Verify non-durable message quota was NOT incremented for rejected message
         long afterReject = quota.getCurrentMessageBytes();
         assertEquals(afterSecond, afterReject,
            "Quota should NOT include rejected non-durable message! " +
            "Before: " + afterSecond + " bytes, After: " + afterReject + " bytes");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testNonDurableMessagesDecrementQuotaOnConsumption() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(3000L);
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L); // In-memory
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config); // In-memory
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnNonDurableSend(true); // Required to get exception response for non-persistent sends
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, false, false); // Manual ack

         SimpleString address = SimpleString.of("test.nondurable.decrement");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.nondurable.decrement").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("test-quota");

         // Send 3 non-durable messages
         for (int i = 0; i < 3; i++) {
            ClientMessage msg = session.createMessage(false); // NON-DURABLE
            msg.getBodyBuffer().writeBytes(new byte[300]);
            producer.send(msg);
            session.commit();
         }

         long afterSend = quota.getCurrentMessageBytes();
         assertTrue(afterSend > 600, "Quota should include all 3 non-durable messages: " + afterSend);

         // Consume and acknowledge 2 messages
         session.start();
         var consumer = session.createConsumer(address);

         ClientMessage received1 = consumer.receive(1000);
         received1.acknowledge();

         ClientMessage received2 = consumer.receive(1000);
         received2.acknowledge();

         session.commit();

         // Verify quota decremented for non-durable messages
         long afterConsume = quota.getCurrentMessageBytes();
         assertTrue(afterConsume < afterSend,
            "NON-DURABLE DECREMENT: Quota should decrease after consuming non-durable messages. " +
            "Before: " + afterSend + ", After: " + afterConsume);

         // Should be roughly 1 message left
         assertTrue(afterConsume > 0 && afterConsume < afterSend / 2,
            "Quota should reflect ~1 non-durable message remaining, got: " + afterConsume);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }
}
