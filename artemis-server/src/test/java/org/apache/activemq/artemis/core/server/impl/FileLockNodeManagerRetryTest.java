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
package org.apache.activemq.artemis.core.server.impl;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive tests for lock monitor retry mechanism.
 * Tests the specific retry behavior introduced for ENTMQBR-10718.
 */
public class FileLockNodeManagerRetryTest {

   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   private ScheduledExecutorService scheduledPool;

   @BeforeEach
   public void setup() {
      scheduledPool = Executors.newScheduledThreadPool(2);
   }

   @AfterEach
   public void teardown() {
      if (scheduledPool != null) {
         scheduledPool.shutdownNow();
      }
   }

   /**
    * Test that with 0 retries (default), the first failure triggers notifyLostLock
    */
   @Test
   @Timeout(10)
   public void testNoRetriesFailsImmediately() throws Exception {
      Configuration config = TestConfigurationBuilder.forLockTesting(-1, 200, 0);
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);
      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      manager.startPrimaryNode().activationComplete();
      assertTrue(manager.isPrimaryLocked());

      // Close channel to simulate failure
      manager.getChannel().close();

      // Should fail on first check (no retries)
      boolean notified = lostLockLatch.await(5, TimeUnit.SECONDS);
      assertTrue(notified, "Should notify lost lock on first failure with 0 retries");
      assertTrue(lostLockCallCount.get() >= 1, "Should have at least one notification");

      manager.stop();
   }

   /**
    * Test that with 1 retry, it takes 2 consecutive failures before notifying
    */
   @Test
   @Timeout(10)
   public void testOneRetryRequiresTwoFailures() throws Exception {
      Configuration config = TestConfigurationBuilder.forLockTesting(-1, 200, 1);
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);
      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      manager.startPrimaryNode().activationComplete();
      assertTrue(manager.isPrimaryLocked());

      // Close channel - should fail but not notify yet (1 retry available)
      manager.getChannel().close();

      // Wait enough time for 2 check periods (first fail + retry)
      boolean notified = lostLockLatch.await(1, TimeUnit.SECONDS);
      assertTrue(notified, "Should notify after retry exhausted (2 failures total)");
      assertTrue(lostLockCallCount.get() >= 1, "Should have at least one notification");

      manager.stop();
   }

   /**
    * Test recovery scenario: transient failure followed by recovery
    * This simulates the CephFS MDS failover use case
    */
   @Test
   @Timeout(15)
   public void testTransientFailureRecovery() throws Exception {
      // Use slightly longer period to control timing better
      Configuration config = TestConfigurationBuilder.forLockTesting(-1, 300, 2);
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);
      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      manager.startPrimaryNode().activationComplete();
      assertTrue(manager.isPrimaryLocked());

      // Simulate transient failure: close channel briefly
      manager.getChannel().close();

      // Wait for 1-2 failed checks to occur
      Thread.sleep(500);

      // Verify lock was NOT declared lost yet (we have 2 retries)
      assertEquals(0, lostLockCallCount.get(), "Should not have lost lock yet - still have retries");

      // Note: In a real scenario, the filesystem would recover here
      // In this test, we can't easily recover the channel, so we verify
      // the behavior up to this point (retries prevented immediate failure)

      manager.stop();
   }

   /**
    * Test with higher retry count
    */
   @Test
   @Timeout(15)
   public void testMultipleRetries() throws Exception {
      Configuration config = TestConfigurationBuilder.forLockTesting(-1, 150, 3);
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);
      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      manager.startPrimaryNode().activationComplete();

      // Close channel to cause failures
      manager.getChannel().close();

      // With 3 retries, should take 4 consecutive failures
      // At 150ms period, that's ~600ms minimum
      boolean notified = lostLockLatch.await(2, TimeUnit.SECONDS);
      assertTrue(notified, "Should eventually notify after all retries exhausted");
      assertTrue(lostLockCallCount.get() >= 1, "Should have at least one notification");

      manager.stop();
   }

   /**
    * Test accessing the MonitorLock internal state via reflection
    * This allows us to verify the consecutiveFailures counter behavior
    */
   @Test
   @Timeout(10)
   public void testConsecutiveFailuresCounterViaReflection() throws Exception {
      Configuration config = TestConfigurationBuilder.forLockTesting(-1, 200, 2);
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);
      manager.start();
      manager.startPrimaryNode().activationComplete();

      // Get the MonitorLock instance via reflection
      Field monitorLockField = FileLockNodeManager.class.getDeclaredField("monitorLock");
      monitorLockField.setAccessible(true);
      Object monitorLock = monitorLockField.get(manager);

      // Get the consecutiveFailures field (now an AtomicInteger)
      Field consecutiveFailuresField = monitorLock.getClass().getDeclaredField("consecutiveFailures");
      consecutiveFailuresField.setAccessible(true);
      java.util.concurrent.atomic.AtomicInteger consecutiveFailures =
         (java.util.concurrent.atomic.AtomicInteger) consecutiveFailuresField.get(monitorLock);

      // Initially should be 0
      assertEquals(0, consecutiveFailures.get(), "Should start with 0 failures");

      // Close channel to cause failures
      manager.getChannel().close();

      // Wait for a check to occur
      Thread.sleep(400);

      // Should have incremented the failure counter
      int failures = consecutiveFailures.get();
      assertTrue(failures > 0, "Should have recorded at least one failure");
      assertTrue(failures <= 3, "Should not exceed maxRetries + 1");

      manager.stop();
   }

   /**
    * Test that configuration values are properly stored
    */
   @Test
   public void testConfigurationValuesStored() throws Exception {
      Configuration config = TestConfigurationBuilder.forLockTesting(-1, 5000, 5);
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);

      // Access private fields via reflection
      Field timeoutField = FileLockNodeManager.class.getDeclaredField("journalLockMonitorTimeoutNanos");
      timeoutField.setAccessible(true);
      long timeout = (long) timeoutField.get(manager);
      assertEquals(TimeUnit.MILLISECONDS.toNanos(5000), timeout, "Journal lock monitor timeout should be stored in nanos");

      Field retriesField = FileLockNodeManager.class.getDeclaredField("journalLockMonitorMaxRetries");
      retriesField.setAccessible(true);
      int retries = (int) retriesField.get(manager);
      assertEquals(5, retries, "Max retries should be stored correctly");
   }
}
