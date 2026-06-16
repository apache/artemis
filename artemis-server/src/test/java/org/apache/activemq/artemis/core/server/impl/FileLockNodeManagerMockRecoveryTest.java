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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock-based test demonstrating the transient failure recovery scenario.
 * This simulates the CephFS MDS failover use case where the filesystem
 * temporarily becomes unavailable but recovers within the retry window.
 */
public class FileLockNodeManagerMockRecoveryTest {

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
    * Testable FileLockNodeManager that allows us to inject controlled lock check failures.
    * This simulates the CephFS MDS failover scenario:
    * - Check 1: Fail (MDS is failing over)
    * - Check 2: Fail (MDS still failing over)
    * - Check 3: Success (MDS recovered)
    * - Result: No lock lost notification because recovery happened within retry window
    */
   static class MockableFileLockNodeManager extends FileLockNodeManager {
      private final Queue<Boolean> lockHealthResults = new ConcurrentLinkedQueue<>();
      private final AtomicInteger checkCount = new AtomicInteger(0);
      private volatile Boolean lastResult = null;

      MockableFileLockNodeManager(File directory,
                                         boolean replicatedBackup,
                                         long lockAcquisitionTimeout,
                                         long journalLockMonitorTimeout,
                                         int journalLockMonitorMaxRetries,
                                         ScheduledExecutorService scheduledPool) {
         super(directory, replicatedBackup,
               TestConfigurationBuilder.forLockTesting(lockAcquisitionTimeout, journalLockMonitorTimeout, journalLockMonitorMaxRetries),
               scheduledPool);
      }

      /**
       * Queue a sequence of lock health check results.
       * true = healthy, false = failed
       */
      public void queueLockHealthResults(Boolean... results) {
         for (Boolean result : results) {
            lockHealthResults.offer(result);
         }
      }

      /**
       * Override the primary lock lost check to return our controlled results.
       * This simulates transient storage failures.
       * After queue is exhausted, continues returning the last queued result.
       */
      @Override
      protected boolean isPrimaryLockLost() {
         checkCount.incrementAndGet();
         Boolean nextResult = lockHealthResults.poll();
         if (nextResult != null) {
            lastResult = nextResult;
         }
         // If we have a result (from queue or last), use it
         if (lastResult != null) {
            // Return inverted because isPrimaryLockLost() returns true when lock IS lost
            return !lastResult;
         }
         // If no results ever queued, use real check
         return super.isPrimaryLockLost();
      }

      public int getCheckCount() {
         return checkCount.get();
      }
   }

   /**
    * Test successful recovery scenario:
    * - System configured with 2 retries (tolerates 3 consecutive failures)
    * - First 2 checks fail (simulating MDS failover in progress)
    * - Third check succeeds (MDS recovered)
    * - Result: No lock lost notification, system continues running
    */
   @Test
   @Timeout(10)
   public void testTransientFailureThenRecovery() throws Exception {
      MockableFileLockNodeManager manager = new MockableFileLockNodeManager(
         temporaryFolder, false, -1, 200, 2, scheduledPool);

      // Simulate CephFS MDS failover scenario:
      // Fail, Fail, Success (recovery within retry window)
      manager.queueLockHealthResults(false, false, true);

      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      ActivateCallback callback = manager.startPrimaryNode();
      callback.activationComplete();

      // Wait enough time for 3 checks to occur
      Thread.sleep(800);

      // Verify lock was NOT lost because it recovered within the retry window
      assertEquals(0, lostLockCallCount.get(),
         "Lock should not be declared lost - it recovered within retry window");

      assertTrue(manager.getCheckCount() >= 3,
         "Should have performed at least 3 health checks");

      manager.stop();
   }

   /**
    * Test permanent failure scenario:
    * - System configured with 2 retries
    * - All checks fail (storage is truly gone)
    * - Result: Lock lost notification after retries exhausted
    */
   @Test
   @Timeout(10)
   public void testPermanentFailure() throws Exception {
      MockableFileLockNodeManager manager = new MockableFileLockNodeManager(
         temporaryFolder, false, -1, 200, 2, scheduledPool);

      // Simulate permanent failure: all checks fail
      manager.queueLockHealthResults(false, false, false, false);

      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      ActivateCallback callback = manager.startPrimaryNode();
      callback.activationComplete();

      // Wait for lock to be declared lost
      boolean notified = lostLockLatch.await(2, TimeUnit.SECONDS);

      assertTrue(notified, "Lock should be declared lost after retries exhausted");
      assertEquals(1, lostLockCallCount.get(),
         "Lost lock listener should be called exactly once");

      manager.stop();
   }

   /**
    * Test intermittent failures with eventual recovery:
    * - Fail, Success, Fail, Success pattern
    * - Counter should reset on each success
    * - Should never accumulate enough consecutive failures to trigger notification
    */
   @Test
   @Timeout(10)
   public void testIntermittentFailuresWithRecovery() throws Exception {
      MockableFileLockNodeManager manager = new MockableFileLockNodeManager(
         temporaryFolder, false, -1, 200, 1, scheduledPool);

      // Intermittent failures: Fail, Success, Fail, Success, Fail, Success
      // With 1 retry (max 2 consecutive), should never trigger lost lock
      manager.queueLockHealthResults(false, true, false, true, false, true);

      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
      });

      ActivateCallback callback = manager.startPrimaryNode();
      callback.activationComplete();

      // Wait for all checks to complete
      Thread.sleep(1500);

      // Should never lose lock because consecutive failures never exceed maxRetries
      assertEquals(0, lostLockCallCount.get(),
         "Lock should not be lost with intermittent failures and recovery");

      manager.stop();
   }

   /**
    * Test edge case: exactly maxRetries+1 consecutive failures
    * Should trigger notification on the last failure
    */
   @Test
   @Timeout(10)
   public void testExactThresholdFailures() throws Exception {
      MockableFileLockNodeManager manager = new MockableFileLockNodeManager(
         temporaryFolder, false, -1, 200, 2, scheduledPool);

      // Exactly 3 failures (maxRetries=2, so 2+1=3 triggers notification)
      manager.queueLockHealthResults(false, false, false);

      manager.start();

      AtomicInteger lostLockCallCount = new AtomicInteger(0);
      CountDownLatch lostLockLatch = new CountDownLatch(1);

      manager.registerLockListener(() -> {
         lostLockCallCount.incrementAndGet();
         lostLockLatch.countDown();
      });

      ActivateCallback callback = manager.startPrimaryNode();
      callback.activationComplete();

      // Wait for notification
      boolean notified = lostLockLatch.await(2, TimeUnit.SECONDS);

      assertTrue(notified, "Should notify exactly when threshold is reached");
      assertEquals(1, lostLockCallCount.get());

      manager.stop();
   }
}
