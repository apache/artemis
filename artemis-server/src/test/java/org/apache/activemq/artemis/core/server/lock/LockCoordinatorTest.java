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
package org.apache.activemq.artemis.core.server.lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.LockCoordinatorConfiguration;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class LockCoordinatorTest extends ArtemisTestCase {

   private static final int CHECK_PERIOD = 100;

   @Test
   public void testLockCoordinatorConfigurationAutoStartDefaultsToTrue() {
      LockCoordinatorConfiguration configuration = new LockCoordinatorConfiguration();
      assertTrue(configuration.isAutoStart(), "auto-start must default to true");
   }

   @Test
   public void testLockCoordinatorConfigurationAutoStartSetter() {
      LockCoordinatorConfiguration configuration = new LockCoordinatorConfiguration();
      LockCoordinatorConfiguration returned = configuration.setAutoStart(false);
      assertSame(configuration, returned, "setAutoStart must return this for fluent chaining");
      assertFalse(configuration.isAutoStart());
   }

   @Test
   public void testLockCoordinatorAutoStartDefaultsToTrue() {
      LockCoordinator coordinator = newCoordinator();
      assertTrue(coordinator.isAutoStart(), "a LockCoordinator must be auto-starting unless configured otherwise");
   }

   @Test
   public void testLockCoordinatorAutoStartSetter() {
      LockCoordinator coordinator = newCoordinator();
      LockCoordinator returned = coordinator.setAutoStart(false);
      assertSame(coordinator, returned, "setAutoStart must return this for fluent chaining");
      assertFalse(coordinator.isAutoStart());
   }

   /**
    * A lock manager unable to connect must not make LockCoordinator::stop hang: the periodic task and the
    * cleanup share the same ordered executor, hence a start blocking forever would keep the broker from
    * being stopped or restarted.
    */
   @Test
   @Timeout(value = 60, unit = TimeUnit.SECONDS)
   public void testStopWithLockManagerBlockedOnStart() throws Exception {
      final CountDownLatch startCalled = new CountDownLatch(1);
      final CountDownLatch releaseStart = new CountDownLatch(1);
      final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      final ExecutorService executorService = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      try {
         final DistributedLockManager lockManager = new BlockedOnStartLockManager(startCalled, releaseStart);
         final LockCoordinator coordinator = new LockCoordinator(scheduledExecutor, new OrderedExecutor(executorService), CHECK_PERIOD, lockManager, "theLock", "theLock");
         coordinator.start();

         assertTrue(startCalled.await(10, TimeUnit.SECONDS), "the lock coordinator never tried to start the lock manager");

         final CountDownLatch stopped = new CountDownLatch(1);
         final Thread stopper = new Thread(() -> {
            coordinator.stop();
            stopped.countDown();
         }, "lock-coordinator-stopper");
         stopper.start();
         try {
            assertTrue(stopped.await(30, TimeUnit.SECONDS), "LockCoordinator::stop is hanging while the lock manager is blocked on start");
         } finally {
            releaseStart.countDown();
            stopper.join(TimeUnit.SECONDS.toMillis(10));
         }
      } finally {
         releaseStart.countDown();
         executorService.shutdownNow();
         scheduledExecutor.shutdownNow();
      }
   }

   private LockCoordinator newCoordinator() {
      final Executor directExecutor = Runnable::run;
      return new LockCoordinator(null, directExecutor, CHECK_PERIOD, new NoopLockManager(), "theLock", "theLock");
   }

   private static class NoopLockManager implements DistributedLockManager {

      private volatile boolean started;

      @Override
      public void addUnavailableManagerListener(UnavailableManagerListener listener) {
      }

      @Override
      public void removeUnavailableManagerListener(UnavailableManagerListener listener) {
      }

      @Override
      public boolean start(long timeout, TimeUnit unit) {
         started = true;
         return true;
      }

      @Override
      public void start() {
         started = true;
      }

      @Override
      public boolean isStarted() {
         return started;
      }

      @Override
      public void stop() {
         started = false;
      }

      @Override
      public DistributedLock getDistributedLock(String lockId) {
         throw new UnsupportedOperationException("not needed by these tests");
      }

      @Override
      public MutableLong getMutableLong(String mutableLongId) {
         throw new UnsupportedOperationException("not needed by these tests");
      }
   }

   /**
    * A DistributedLockManager blocking forever on start, the same way CuratorDistributedLockManager does
    * when ZooKeeper is not reachable: start() calls start(-1, null), which parks on
    * CuratorFramework::blockUntilConnected with no timeout.
    */
   private static class BlockedOnStartLockManager implements DistributedLockManager {

      private final CountDownLatch startCalled;
      private final CountDownLatch releaseStart;
      private volatile boolean started;

      BlockedOnStartLockManager(CountDownLatch startCalled, CountDownLatch releaseStart) {
         this.startCalled = startCalled;
         this.releaseStart = releaseStart;
      }

      @Override
      public void addUnavailableManagerListener(UnavailableManagerListener listener) {
      }

      @Override
      public void removeUnavailableManagerListener(UnavailableManagerListener listener) {
      }

      @Override
      public boolean start(long timeout, TimeUnit unit) throws InterruptedException {
         startCalled.countDown();
         if (timeout >= 0) {
            return releaseStart.await(timeout, unit) && markStarted();
         }
         releaseStart.await();
         return markStarted();
      }

      @Override
      public void start() throws InterruptedException {
         start(-1, null);
      }

      private boolean markStarted() {
         started = true;
         return true;
      }

      @Override
      public boolean isStarted() {
         return started;
      }

      @Override
      public void stop() {
         started = false;
      }

      @Override
      public DistributedLock getDistributedLock(String lockId) {
         return new NeverAcquiredLock(lockId);
      }

      @Override
      public MutableLong getMutableLong(String mutableLongId) {
         throw new UnsupportedOperationException();
      }
   }

   private static class NeverAcquiredLock implements DistributedLock {

      private final String lockId;

      NeverAcquiredLock(String lockId) {
         this.lockId = lockId;
      }

      @Override
      public String getLockId() {
         return lockId;
      }

      @Override
      public boolean isHeldByCaller() {
         return false;
      }

      @Override
      public boolean tryLock() {
         return false;
      }

      @Override
      public void unlock() {
      }

      @Override
      public void addListener(UnavailableLockListener listener) {
      }

      @Override
      public void removeListener(UnavailableLockListener listener) {
      }

      @Override
      public void close() {
      }
   }
}
