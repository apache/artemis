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
package org.apache.activemq.artemis.lockmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class DistributedLockTest {

   private final List<AutoCloseable> closeables = new ArrayList<>();

   @BeforeEach
   public void setupEnv() throws Throwable {
   }

   protected abstract void configureManager(Map<String, String> config);

   protected abstract String managerClassName();

   @AfterEach
   public void tearDownEnv() throws Throwable {
      closeables.forEach(closeables -> {
         try {
            closeables.close();
         } catch (Throwable t) {
            // silent here
         }
      });
   }

   protected DistributedLockManager createManagedDistributeManager() {
      return createManagedDistributeManager(stringStringMap -> {
      });
   }

   protected DistributedLockManager createManagedDistributeManager(Consumer<? super Map<String, String>> defaultConfiguration) {
      try {
         final HashMap<String, String> config = new HashMap<>();
         configureManager(config);
         defaultConfiguration.accept(config);
         final DistributedLockManager manager = DistributedLockManager.newInstanceOf(managerClassName(), config);
         closeables.add(manager);
         return manager;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Test
   public void managerReturnsSameLockIfNotClosed() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      assertSame(manager.getDistributedLock("a"), manager.getDistributedLock("a"));
   }

   @Test
   public void managerReturnsDifferentLocksIfClosed() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock closedLock = manager.getDistributedLock("a");
      closedLock.close();
      assertNotSame(closedLock, manager.getDistributedLock("a"));
   }

   @Test
   public void managerReturnsDifferentLocksOnRestart() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock closedLock = manager.getDistributedLock("a");
      manager.stop();
      manager.start();
      assertNotSame(closedLock, manager.getDistributedLock("a"));
   }

   @Test
   public void managerCannotGetLockIfNotStarted() throws Exception {
      assertThrows(IllegalStateException.class, () -> {
         DistributedLockManager manager = createManagedDistributeManager();
         manager.getDistributedLock("a");
      });
   }

   @Test
   public void managerCannotGetLockWithNullLockId() throws Exception {
      assertThrows(NullPointerException.class, () -> {
         DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         manager.getDistributedLock(null);
      });
   }

   @Test
   public void closingLockUnlockIt() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock closedLock = manager.getDistributedLock("a");
      assertTrue(closedLock.tryLock());
      closedLock.close();
      assertTrue(manager.getDistributedLock("a").tryLock());
   }

   @Test
   public void managerStopUnlockLocks() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      assertTrue(manager.getDistributedLock("a").tryLock());
      assertTrue(manager.getDistributedLock("b").tryLock());
      manager.stop();
      manager.start();
      assertFalse(manager.getDistributedLock("a").isHeldByCaller());
      assertFalse(manager.getDistributedLock("b").isHeldByCaller());
   }

   @Test
   public void acquireAndReleaseLock() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock lock = manager.getDistributedLock("a");
      assertFalse(lock.isHeldByCaller());
      assertTrue(lock.tryLock());
      assertTrue(lock.isHeldByCaller());
      lock.unlock();
      assertFalse(lock.isHeldByCaller());
   }

   @Test
   public void cannotAcquireSameLockTwice() throws Exception {
      assertThrows(IllegalStateException.class, () -> {
         DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         DistributedLock lock = manager.getDistributedLock("a");
         assertTrue(lock.tryLock());
         lock.tryLock();
      });
   }

   @Test
   public void heldLockIsVisibleByDifferentManagers() throws Exception {
      DistributedLockManager ownerManager = createManagedDistributeManager();
      DistributedLockManager observerManager = createManagedDistributeManager();
      ownerManager.start();
      observerManager.start();
      assertTrue(ownerManager.getDistributedLock("a").tryLock());
      assertTrue(ownerManager.getDistributedLock("a").isHeldByCaller());
      assertFalse(observerManager.getDistributedLock("a").isHeldByCaller());
   }

   @Test
   public void unlockedLockIsVisibleByDifferentManagers() throws Exception {
      DistributedLockManager ownerManager = createManagedDistributeManager();
      DistributedLockManager observerManager = createManagedDistributeManager();
      ownerManager.start();
      observerManager.start();
      assertTrue(ownerManager.getDistributedLock("a").tryLock());
      ownerManager.getDistributedLock("a").unlock();
      assertFalse(observerManager.getDistributedLock("a").isHeldByCaller());
      assertFalse(ownerManager.getDistributedLock("a").isHeldByCaller());
      assertTrue(observerManager.getDistributedLock("a").tryLock());
   }

   @Test
   public void cannotAcquireSameLockFromDifferentManagers() throws Exception {
      DistributedLockManager ownerManager = createManagedDistributeManager();
      DistributedLockManager notOwnerManager = createManagedDistributeManager();
      ownerManager.start();
      notOwnerManager.start();
      assertTrue(ownerManager.getDistributedLock("a").tryLock());
      assertFalse(notOwnerManager.getDistributedLock("a").tryLock());
   }

   @Test
   public void cannotUnlockFromNotOwnerManager() throws Exception {
      DistributedLockManager ownerManager = createManagedDistributeManager();
      DistributedLockManager notOwnerManager = createManagedDistributeManager();
      ownerManager.start();
      notOwnerManager.start();
      assertTrue(ownerManager.getDistributedLock("a").tryLock());
      notOwnerManager.getDistributedLock("a").unlock();
      assertFalse(notOwnerManager.getDistributedLock("a").isHeldByCaller());
      assertTrue(ownerManager.getDistributedLock("a").isHeldByCaller());
   }

   @Test
   public void timedTryLockSucceedWithShortTimeout() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock backgroundLock = manager.getDistributedLock("a");
      assertTrue(backgroundLock.tryLock(1, TimeUnit.NANOSECONDS));
   }

   @Test
   public void timedTryLockFailAfterTimeout() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLockManager otherManager = createManagedDistributeManager();
      otherManager.start();
      assertTrue(otherManager.getDistributedLock("a").tryLock());
      final long start = System.nanoTime();
      final long timeoutSec = 1;
      assertFalse(manager.getDistributedLock("a").tryLock(timeoutSec, TimeUnit.SECONDS));
      final long elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
      assertTrue(elapsed >= timeoutSec, elapsed + " less than timeout of " + timeoutSec + " seconds");
   }

   @Test
   public void timedTryLockSuccess() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLockManager otherManager = createManagedDistributeManager();
      otherManager.start();
      assertTrue(otherManager.getDistributedLock("a").tryLock());
      DistributedLock backgroundLock = manager.getDistributedLock("a");
      CompletableFuture<Boolean> acquired = new CompletableFuture<>();
      CountDownLatch startedTry = new CountDownLatch(1);
      Thread tryLockThread = new Thread(() -> {
         startedTry.countDown();
         try {
            if (!backgroundLock.tryLock(Long.MAX_VALUE, TimeUnit.DAYS)) {
               acquired.complete(false);
            } else {
               acquired.complete(true);
            }
         } catch (Throwable e) {
            acquired.complete(false);
         }
      });
      tryLockThread.start();
      assertTrue(startedTry.await(10, TimeUnit.SECONDS));
      otherManager.getDistributedLock("a").unlock();
      assertTrue(acquired.get(4, TimeUnit.SECONDS));
   }

   @Test
   public void interruptStopTimedTryLock() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLockManager otherManager = createManagedDistributeManager();
      otherManager.start();
      assertTrue(otherManager.getDistributedLock("a").tryLock());
      DistributedLock backgroundLock = manager.getDistributedLock("a");
      CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
      CountDownLatch startedTry = new CountDownLatch(1);
      Thread tryLockThread = new Thread(() -> {
         startedTry.countDown();
         try {
            backgroundLock.tryLock(Long.MAX_VALUE, TimeUnit.DAYS);
            interrupted.complete(false);
         } catch (UnavailableStateException e) {
            interrupted.complete(false);
         } catch (InterruptedException e) {
            interrupted.complete(true);
         }
      });
      tryLockThread.start();
      assertTrue(startedTry.await(10, TimeUnit.SECONDS));
      // let background lock to perform some tries
      TimeUnit.SECONDS.sleep(1);
      tryLockThread.interrupt();
      assertTrue(interrupted.get(4, TimeUnit.SECONDS));
   }

   @Test
   public void lockAndMutableLongWithSameIdCanExistsTogether() throws Exception {
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      final String id = "a";
      assertTrue(manager.getDistributedLock(id).tryLock());
      assertEquals(0, manager.getMutableLong(id).get());
      manager.getMutableLong(id).set(1);
      assertTrue(manager.getDistributedLock(id).isHeldByCaller());
      assertEquals(1, manager.getMutableLong(id).get());
   }

}

