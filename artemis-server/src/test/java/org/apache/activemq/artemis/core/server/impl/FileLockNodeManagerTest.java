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
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileLockNodeManagerTest {

   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   @Test
   @Timeout(3)
   public void testChannelClosed() throws Exception {
      FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false);

      // calling this method sets up the internal FileChannel as it would be in normal usage
      manager.pausePrimaryServer();

      // now close the channel so we can ensure it throws
      manager.getChannel().close();

      assertThrows(ClosedChannelException.class, () -> manager.lock(0));
   }

   @Test
   @Timeout(10)
   public void testLockMonitorWithRetries() throws Exception {
      ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(1);
      try {
         // Create a FileLockNodeManager with custom lock monitor settings:
         // - 200ms check period
         // - 2 retries (so it should tolerate up to 2 consecutive failures)
         Configuration config = TestConfigurationBuilder.forLockTesting(-1, 200, 2);
         FileLockNodeManager manager = new FileLockNodeManager(temporaryFolder, false, config, scheduledPool);
         manager.start();

         AtomicInteger lostLockCallCount = new AtomicInteger(0);
         CountDownLatch lostLockLatch = new CountDownLatch(1);

         // Register a listener to track when notifyLostLock is called
         manager.registerLockListener(() -> {
            lostLockCallCount.incrementAndGet();
            lostLockLatch.countDown();
         });

         // Start as primary to begin lock monitoring
         manager.startPrimaryNode().activationComplete();

         // Verify the manager is in primary mode
         assertTrue(manager.isPrimaryLocked());

         // Close the channel to simulate a lock failure
         // With 2 retries, it should fail 3 times total before calling notifyLostLock
         manager.getChannel().close();

         // Wait for the lock to be declared lost (should take ~600-800ms: 3 check periods)
         boolean lostLockNotified = lostLockLatch.await(5, TimeUnit.SECONDS);
         assertTrue(lostLockNotified, "Lost lock should have been notified after max retries exceeded");
         assertEquals(1, lostLockCallCount.get(), "Lost lock listener should be called exactly once");

         manager.stop();
      } finally {
         scheduledPool.shutdownNow();
      }
   }
}
