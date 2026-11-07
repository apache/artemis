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

package org.apache.artemis.database.worker;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.artemis.database.data.DBData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataManagerFlowControlTest extends ActiveMQTestBase {

   private DataManager dataManager;
   private ScheduledExecutorService scheduledExecutor;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
      dataManager = new DataManager(
         scheduledExecutor,
         Runnable::run,
         Runnable::run,
         Long.MAX_VALUE,
         null,
         100,
         0,
         0,
         false,
         () -> 1,
         () -> 1000L,
         t -> { });
      dataManager.setMaxCredits(10);
   }

   @Override
   @AfterEach
   public void tearDown() {
      dataManager.close();
      scheduledExecutor.shutdownNow();
   }

   @Test
   public void testBlockCalledWhenOverLimit() {
      DataManagerAccessor.addMockPendingData(dataManager, 11);

      AtomicBoolean blocked = new AtomicBoolean();
      AtomicBoolean released = new AtomicBoolean();
      dataManager.flowControl(() -> blocked.set(true), () -> released.set(true));

      assertTrue(blocked.get());
      assertFalse(released.get());
      assertEquals(1, DataManagerAccessor.getOnReleaseSize(dataManager));
   }

   @Test
   public void testNoBlockWhenUnderLimit() {
      DataManagerAccessor.addMockPendingData(dataManager, 5);

      AtomicBoolean blocked = new AtomicBoolean();
      dataManager.flowControl(() -> blocked.set(true), () -> { });

      assertFalse(blocked.get());
      assertEquals(0, DataManagerAccessor.getOnReleaseSize(dataManager));
   }

   @Test
   public void testReleaseCalledWhenCreditsDrain() {
      DataManagerAccessor.addMockPendingData(dataManager, 11);

      AtomicBoolean released = new AtomicBoolean();
      dataManager.flowControl(() -> { }, () -> released.set(true));
      assertFalse(released.get());

      List<DBData> extracted = DataManagerAccessor.extractTaskList(dataManager);
      assertFalse(extracted.isEmpty());

      assertTrue(released.get());
      assertEquals(0, DataManagerAccessor.getOnReleaseSize(dataManager));
   }

   @Test
   public void testMultipleConnectionsBlockedAndReleased() {
      DataManagerAccessor.addMockPendingData(dataManager, 11);

      AtomicInteger blockCount = new AtomicInteger();
      AtomicInteger releaseCount = new AtomicInteger();

      dataManager.flowControl(blockCount::incrementAndGet, releaseCount::incrementAndGet);
      dataManager.flowControl(blockCount::incrementAndGet, releaseCount::incrementAndGet);
      dataManager.flowControl(blockCount::incrementAndGet, releaseCount::incrementAndGet);

      assertEquals(3, blockCount.get());
      assertEquals(3, DataManagerAccessor.getOnReleaseSize(dataManager));

      DataManagerAccessor.extractTaskList(dataManager);
      assertEquals(3, releaseCount.get());
      assertEquals(0, DataManagerAccessor.getOnReleaseSize(dataManager));
   }

   @Test
   public void testNotBlockedConnectionNotReleased() {
      DataManagerAccessor.addMockPendingData(dataManager, 5);

      AtomicBoolean released = new AtomicBoolean();
      dataManager.flowControl(() -> { }, () -> released.set(true));

      DataManagerAccessor.extractTaskList(dataManager);
      assertFalse(released.get());
   }

   @Test
   public void testBlockAndReleaseMultipleCycles() {
      AtomicInteger blockCount = new AtomicInteger();
      AtomicInteger releaseCount = new AtomicInteger();

      // Cycle 1
      DataManagerAccessor.addMockPendingData(dataManager, 11);
      dataManager.flowControl(blockCount::incrementAndGet, releaseCount::incrementAndGet);
      assertEquals(1, blockCount.get());
      DataManagerAccessor.extractTaskList(dataManager);
      assertEquals(1, releaseCount.get());

      // Cycle 2
      DataManagerAccessor.addMockPendingData(dataManager, 11);
      dataManager.flowControl(blockCount::incrementAndGet, releaseCount::incrementAndGet);
      assertEquals(2, blockCount.get());
      DataManagerAccessor.extractTaskList(dataManager);
      assertEquals(2, releaseCount.get());
   }

   @Test
   public void testExactThresholdBlocks() {
      DataManagerAccessor.addMockPendingData(dataManager, 10);

      AtomicBoolean blocked = new AtomicBoolean();
      dataManager.flowControl(() -> blocked.set(true), () -> { });

      assertTrue(blocked.get());
   }

   @Test
   public void testSetCreditsTriggersRelease() {
      DataManagerAccessor.addMockPendingData(dataManager, 11);

      AtomicBoolean released = new AtomicBoolean();
      dataManager.flowControl(() -> { }, () -> released.set(true));
      assertFalse(released.get());

      DataManagerAccessor.setCredits(dataManager, 5);
      assertEquals(5, DataManagerAccessor.getCredits(dataManager));
      assertTrue(released.get());
      assertEquals(0, DataManagerAccessor.getOnReleaseSize(dataManager));
   }

   @Test
   public void testSetCreditsAboveThresholdDoesNotRelease() {
      DataManagerAccessor.addMockPendingData(dataManager, 15);

      AtomicBoolean released = new AtomicBoolean();
      dataManager.flowControl(() -> { }, () -> released.set(true));

      DataManagerAccessor.setCredits(dataManager, 12);
      assertFalse(released.get());
   }
}
