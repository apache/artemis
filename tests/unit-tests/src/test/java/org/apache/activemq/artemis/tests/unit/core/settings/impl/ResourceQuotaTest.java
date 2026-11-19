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
package org.apache.activemq.artemis.tests.unit.core.settings.impl;

import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceQuotaTest {

   @Test
   public void testBasicQuotaCreation() {
      ResourceQuota quota = new ResourceQuota("test-quota");
      assertEquals("test-quota", quota.getName());
      assertEquals(ResourceQuota.DEFAULT_MAX_MESSAGE_BYTES, quota.getMaxMessageBytes());
      assertEquals(ResourceQuota.DEFAULT_MAX_ADDRESSES, quota.getMaxAddresses());
      assertEquals(ResourceQuota.DEFAULT_MAX_QUEUES, quota.getMaxQueues());
      assertNull(quota.getPartOf());
   }

   @Test
   public void testQuotaConfiguration() {
      ResourceQuota quota = new ResourceQuota("test-quota");
      quota.setMaxMessageBytes(1024L * 1024L);
      quota.setMaxAddresses(100);
      quota.setMaxQueues(50);
      quota.setPartOf("parent-quota");

      assertEquals(1024L * 1024L, quota.getMaxMessageBytes());
      assertEquals(100, quota.getMaxAddresses());
      assertEquals(50, quota.getMaxQueues());
      assertEquals("parent-quota", quota.getPartOf());
   }

   @Test
   public void testByteTracking() {
      ResourceQuota quota = new ResourceQuota("test-quota");
      quota.setMaxMessageBytes(1000L);

      assertEquals(0, quota.getSize());

      quota.addSize(100);
      assertEquals(100, quota.getSize());

      quota.addSize(200);
      assertEquals(300, quota.getSize());

      quota.addSize(-50);
      assertEquals(250, quota.getSize());
   }

   @Test
   public void testByteLimitExceeded() {
      ResourceQuota quota = new ResourceQuota("test-quota");
      quota.setMaxMessageBytes(1000L);

      assertFalse(quota.isByteLimitReached());

      quota.addSize(500);
      assertFalse(quota.isByteLimitReached());

      quota.addSize(600);
      assertTrue(quota.isByteLimitReached());

      // Going back under the lower mark (90% of max = 900)
      quota.addSize(-300);
      assertFalse(quota.isByteLimitReached());
   }

   @Test
   public void testAddressCountTracking() {
      ResourceQuota quota = new ResourceQuota("test-quota");
      quota.setMaxAddresses(5);

      assertEquals(0, quota.getAddressCount());
      assertFalse(quota.isAddressLimitReached());

      quota.incrementAddressCount();
      assertEquals(1, quota.getAddressCount());
      assertFalse(quota.isAddressLimitReached());

      for (int i = 0; i < 4; i++) {
         quota.incrementAddressCount();
      }
      assertEquals(5, quota.getAddressCount());
      assertTrue(quota.isAddressLimitReached());

      quota.decrementAddressCount();
      assertEquals(4, quota.getAddressCount());
      assertFalse(quota.isAddressLimitReached());
   }

   @Test
   public void testQueueCountTracking() {
      ResourceQuota quota = new ResourceQuota("test-quota");
      quota.setMaxQueues(10);

      assertEquals(0, quota.getQueueCount());
      assertFalse(quota.isQueueLimitReached());

      for (int i = 0; i < 10; i++) {
         quota.incrementQueueCount();
      }
      assertEquals(10, quota.getQueueCount());
      assertTrue(quota.isQueueLimitReached());

      quota.decrementQueueCount();
      assertEquals(9, quota.getQueueCount());
      assertFalse(quota.isQueueLimitReached());
   }

   @Test
   public void testParentPropagation() {
      ResourceQuota parent = new ResourceQuota("parent");
      parent.setMaxMessageBytes(10000L);
      parent.setMaxAddresses(100);
      parent.setMaxQueues(100);

      ResourceQuota child = new ResourceQuota("child");
      child.setMaxMessageBytes(5000L);
      child.setMaxAddresses(50);
      child.setMaxQueues(50);
      child.setParent(parent);

      // Test byte propagation
      child.addSize(1000);
      assertEquals(1000, child.getSize());
      assertEquals(1000, parent.getSize());

      // Test address count propagation
      child.incrementAddressCount();
      assertEquals(1, child.getAddressCount());
      assertEquals(1, parent.getAddressCount());

      // Test queue count propagation
      child.incrementQueueCount();
      assertEquals(1, child.getQueueCount());
      assertEquals(1, parent.getQueueCount());

      // Test decrement propagation
      child.decrementAddressCount();
      assertEquals(0, child.getAddressCount());
      assertEquals(0, parent.getAddressCount());

      child.decrementQueueCount();
      assertEquals(0, child.getQueueCount());
      assertEquals(0, parent.getQueueCount());
   }

   @Test
   public void testThreeLevelHierarchy() {
      ResourceQuota global = new ResourceQuota("global");
      global.setMaxMessageBytes(100000L);

      ResourceQuota region = new ResourceQuota("EU");
      region.setMaxMessageBytes(50000L);
      region.setParent(global);

      ResourceQuota country = new ResourceQuota("EU.fr");
      country.setMaxMessageBytes(10000L);
      country.setParent(region);

      // Add bytes to country-level quota
      country.addSize(5000);

      assertEquals(5000, country.getSize());
      assertEquals(5000, region.getSize());
      assertEquals(5000, global.getSize());

      // Add more to exceed country limit but not region
      country.addSize(6000);
      assertEquals(11000, country.getSize());
      assertTrue(country.isByteLimitReached());
      assertFalse(region.isByteLimitReached());
      assertFalse(global.isByteLimitReached());
   }

   @Test
   public void testThreadSafety() throws Exception {
      ResourceQuota quota = new ResourceQuota("test-quota");
      quota.setMaxMessageBytes(1000000L);
      quota.setMaxAddresses(1000);
      quota.setMaxQueues(1000);

      int threadCount = 10;
      int operationsPerThread = 100;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
         new Thread(() -> {
            try {
               startLatch.await();
               for (int j = 0; j < operationsPerThread; j++) {
                  quota.addSize(10);
                  quota.incrementAddressCount();
                  quota.incrementQueueCount();
               }
            } catch (Exception e) {
               e.printStackTrace();
            } finally {
               doneLatch.countDown();
            }
         }).start();
      }

      startLatch.countDown();
      assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

      assertEquals(threadCount * operationsPerThread * 10, quota.getSize());
      assertEquals(threadCount * operationsPerThread, quota.getAddressCount());
      assertEquals(threadCount * operationsPerThread, quota.getQueueCount());
   }

   @Test
   public void testNoNegativeCountProtection() {
      ResourceQuota quota = new ResourceQuota("test");

      // Decrement address count when it's already 0
      quota.decrementAddressCount();
      assertEquals(-1, quota.getAddressCount());

      // Decrement queue count when it's already 0
      quota.decrementQueueCount();
      assertEquals(-1, quota.getQueueCount());
   }

   @Test
   public void testSizeOnlyTracking() {
      ResourceQuota quota = new ResourceQuota("test");

      quota.addSize(100);
      assertEquals(100, quota.getSize());

      quota.addSize(50);
      assertEquals(150, quota.getSize());
   }

   @Test
   public void testAdjustCountersDirect() {
      ResourceQuota parent = new ResourceQuota("parent");
      parent.setMaxMessageBytes(100000L);
      parent.setMaxAddresses(100);
      parent.setMaxQueues(100);

      ResourceQuota child = new ResourceQuota("child");
      child.setMaxMessageBytes(50000L);
      child.setMaxAddresses(50);
      child.setMaxQueues(50);
      child.setParent(parent);

      // Build up counters via propagating methods
      for (int i = 0; i < 5; i++) {
         child.incrementAddressCount();
         child.incrementQueueCount();
      }
      child.addSize(10000);
      assertEquals(5, child.getAddressCount());
      assertEquals(5, child.getQueueCount());
      assertEquals(10000, child.getSize());
      assertEquals(5, parent.getAddressCount());
      assertEquals(5, parent.getQueueCount());
      assertEquals(10000, parent.getSize());

      // adjustCountersDirect should modify counters WITHOUT propagating to parent
      child.adjustCountersDirect(3, 2, 5000);
      assertEquals(8, child.getAddressCount());
      assertEquals(7, child.getQueueCount());
      assertEquals(15000, child.getSize());
      // Parent should NOT have changed
      assertEquals(5, parent.getAddressCount(), "Parent should not be affected by adjustCountersDirect");
      assertEquals(5, parent.getQueueCount(), "Parent should not be affected by adjustCountersDirect");
      assertEquals(10000, parent.getSize(), "Parent should not be affected by adjustCountersDirect");

      // Negative deltas
      child.adjustCountersDirect(-2, -3, -5000);
      assertEquals(6, child.getAddressCount());
      assertEquals(4, child.getQueueCount());
      assertEquals(10000, child.getSize());
      assertEquals(5, parent.getAddressCount(), "Parent should remain unchanged");
   }

}
