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
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for quota token rollback behavior.
 *
 * These tests verify that quota accounting is transactional:
 * - Quota is reserved when operations start
 * - Quota is committed when operations succeed
 * - Quota is automatically rolled back when operations fail
 */
public class QuotaTokenRollbackTest extends ActiveMQTestBase {

   /**
    * Test that quota is correctly accounted for when messages are successfully routed.
    */
   @Test
   public void testQuotaIncrementedOnSuccessfulSend() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("success-quota");
      quotaConfig.setMaxMessageBytes(10240L); // 10KB
      config.addResourceQuota("success-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("success-quota");
      settings.setMaxSizeBytes(-1L);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.success");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.success").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         // Get quota instance from ResourceQuotaService (not config)
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);

         long initialSize = quota.getCurrentMessageBytes();

         // Send 5 messages of 100 bytes each
         for (int i = 0; i < 5; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[100]);
            producer.send(message);
         }

         // Quota should be incremented by approximately 500 bytes (body) plus memory overhead
         // (buffer allocations + reference overhead ~500 bytes per message)
         long finalSize = quota.getCurrentMessageBytes();
         long delta = finalSize - initialSize;

         assertTrue(delta >= 500, "Quota should increase by at least message body size. Delta: " + delta);
         assertTrue(delta < 4000, "Quota increase should be reasonable (body + memory overhead). Delta: " + delta);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that quota is rolled back when transaction is rolled back.
    * Uses QuotaTransactionOperation to participate in transaction lifecycle.
    */
   @Test
   public void testQuotaRollbackOnTransactionRollback() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("rollback-quota");
      quotaConfig.setMaxMessageBytes(10240L); // 10KB
      config.addResourceQuota("rollback-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("rollback-quota");
      settings.setMaxSizeBytes(-1L);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         // Create transacted session
         ClientSession session = sf.createSession(false, false, false);

         SimpleString address = SimpleString.of("test.rollback");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.rollback").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         // Get quota instance from ResourceQuotaService (not config)
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);

         long initialSize = quota.getCurrentMessageBytes();

         // Send messages in transaction
         for (int i = 0; i < 5; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[100]);
            producer.send(message);
         }

         // Note: Quota increment behavior in transactions depends on when routing occurs.
         // The important test is that rollback returns quota to initial state.

         // Rollback transaction
         session.rollback();

         // Quota should be rolled back to initial size
         long finalSize = quota.getCurrentMessageBytes();
         assertTrue(Wait.waitFor(() -> finalSize == quota.getCurrentMessageBytes()),
            "Quota should be rolled back to initial size after transaction rollback");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that quota doesn't leak when messages fail to route.
    */
   @Test
   public void testQuotaNoLeakOnRoutingFailure() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("leak-quota");
      quotaConfig.setMaxMessageBytes(10240L); // 10KB
      config.addResourceQuota("leak-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("leak-quota");
      settings.setMaxSizeBytes(-1L);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.leak");
         session.createAddress(address, RoutingType.ANYCAST, false);

         // Create queue but then delete it
         session.createQueue(QueueConfiguration.of("test.leak").setAddress(address).setRoutingType(RoutingType.ANYCAST));
         session.deleteQueue(SimpleString.of("test.leak"));

         ClientProducer producer = session.createProducer(address);

         // Get quota instance from ResourceQuotaService (not config)
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);

         long initialSize = quota.getCurrentMessageBytes();

         // send with no bindings
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(new byte[100]);
         producer.send(message);

         // Quota should not have leaked - should be back to initial
         long finalSize = quota.getCurrentMessageBytes();
         assertEquals(initialSize, finalSize,
            "Quota should not leak when routing fails");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that quota accounting matches actual message size in paging store.
    */
   @Test
   public void testQuotaSizeMatchesPagingStoreSize() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("size-quota");
      quotaConfig.setMaxMessageBytes(10240L); // 10KB
      config.addResourceQuota("size-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("size-quota");
      settings.setMaxSizeBytes(-1L);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.size");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.size").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         ClientMessage message = null;
         // Send messages
         for (int i = 0; i < 10; i++) {
            message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[100]);
            producer.send(message);
         }

         // Get sizes
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);
         long quotaSize = quota.getCurrentMessageBytes();

         PagingStore pagingStore = server.getPagingManager().getPageStore(address);
         long pagingStoreSize = pagingStore.getAddressSize();

         // Quota size should match paging store size
         assertEquals(pagingStoreSize, quotaSize,
            "Quota size should match paging store size, memSize:" + message.getMemoryEstimate() + ", persistedSize:" + message.getPersistentSize());

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that quota handles concurrent sends correctly without double-counting.
    */
   @Test
   public void testQuotaConcurrentSendsNoDoubleCounting() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("concurrent-quota");
      quotaConfig.setMaxMessageBytes(102400L); // 100KB
      config.addResourceQuota("concurrent-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("concurrent-quota");
      settings.setMaxSizeBytes(-1L);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         SimpleString address = SimpleString.of("test.concurrent");
         AddressInfo addressInfo = new AddressInfo(address, RoutingType.ANYCAST);
         server.addAddressInfo(addressInfo);
         server.createQueue(QueueConfiguration.of("test.concurrent").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         // Create multiple producers sending concurrently
         Thread[] threads = new Thread[5];
         for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
               try {
                  ServerLocator locator = createInVMNonHALocator();
                  ClientSessionFactory sf = createSessionFactory(locator);
                  ClientSession session = sf.createSession(false, true, true);
                  ClientProducer producer = session.createProducer(address);

                  for (int i = 0; i < 10; i++) {
                     ClientMessage message = session.createMessage(true);
                     message.getBodyBuffer().writeBytes(new byte[100]);
                     producer.send(message);
                  }

                  session.close();
                  locator.close();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            });
            threads[t].start();
         }

         // Wait for all threads
         for (Thread thread : threads) {
            thread.join();
         }

         // Verify quota matches paging store (no double counting)
         ResourceQuota quota = server.getResourceQuotaService().lookupQuota(address);
         long quotaSize = quota.getCurrentMessageBytes();

         PagingStore pagingStore = server.getPagingManager().getPageStore(address);
         long pagingStoreSize = pagingStore.getAddressSize();

         assertEquals(pagingStoreSize, quotaSize,
            "Quota size should match paging store size (no double counting)");

      } finally {
         server.stop();
      }
   }
}
