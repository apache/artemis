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
package org.apache.activemq.artemis.lockmanager.zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.DistributedLockTest;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingZooKeeperServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CuratorDistributedLockTest extends DistributedLockTest {

   // fast-tests runs dont need to run 3 ZK nodes
   private static final int ZK_NODES = Boolean.getBoolean("fast-tests") ? 1 : 3;

   private static final int BASE_SERVER_PORT = 6666;
   private static final int CONNECTION_MS = 500;
   // Beware: the server tick must be small enough that to let the session to be correctly expired
   private static final int SESSION_MS = 1000;
   private static final int SERVER_TICK_MS = 100;
   private static final int RETRIES_MS = 100;
   private static final int RETRIES = 1;

   public int zkNodes;
   private static TestingCluster testingServer;
   private static InstanceSpec[] clusterSpecs;
   private static String connectString;
   private boolean clusterDirty;

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public static File tmpFolder;

   @BeforeAll
   static void startCluster() throws Exception {
      createCluster();
   }

   @AfterAll
   static void tearDownCluster() throws Exception {
      destroyCluster();
   }

   private static void createCluster() throws Exception {
      clusterSpecs = new InstanceSpec[ZK_NODES];
      for (int i = 0; i < ZK_NODES; i++) {
         clusterSpecs[i] = new InstanceSpec(newFolder(tmpFolder, "node" + i), BASE_SERVER_PORT + i, -1, -1, true, i, SERVER_TICK_MS, -1);
      }
      testingServer = new TestingCluster(clusterSpecs);
      testingServer.start();
      connectString = testingServer.getConnectString();
   }

   private static void destroyCluster() {
      if (testingServer != null) {
         try {
            testingServer.stop();
         } catch (Exception e) {
         }
         try {
            testingServer.close();
         } catch (Exception e) {
         }
      }
   }

   @BeforeEach
   @Override
   public void setupEnv() throws Throwable {
      zkNodes = ZK_NODES;
      clusterDirty = false;
      super.setupEnv();
   }

   @AfterEach
   @Override
   public void tearDownEnv() throws Throwable {
      super.tearDownEnv();
      if (clusterDirty) {
         destroyCluster();
         createCluster();
      }
   }

   @Override
   protected void configureManager(Map<String, String> config) {
      config.put("connect-string", connectString);
      config.put("session-ms", Integer.toString(SESSION_MS));
      config.put("connection-ms", Integer.toString(CONNECTION_MS));
      config.put("retries", Integer.toString(RETRIES));
      config.put("retries-ms", Integer.toString(RETRIES_MS));
   }

   @Override
   protected String managerClassName() {
      return CuratorDistributedLockManager.class.getName();
   }

   @Test
   public void cannotCreateManagerWithNotValidParameterNames() {
      assertThrows(RuntimeException.class, () -> {
         final DistributedLockManager manager = createManagedDistributeManager(config -> config.put("_", "_"));
      });
   }

   @Test
   public void canAcquireLocksFromDifferentNamespace() throws Exception {
      final DistributedLockManager manager1 = createManagedDistributeManager(config -> config.put("namespace", "1"));
      manager1.start();
      final DistributedLockManager manager2 = createManagedDistributeManager(config -> config.put("namespace", "2"));
      manager2.start();
      assertTrue(manager1.getDistributedLock("a").tryLock());
      assertTrue(manager2.getDistributedLock("a").tryLock());
   }

   @Test
   public void cannotStartManagerWithDisconnectedServer() throws Exception {
      final DistributedLockManager manager = createManagedDistributeManager();
      closeCluster();
      assertFalse(manager.start(1, TimeUnit.SECONDS));
   }

   @Test
   public void cannotAcquireLockWithDisconnectedServer() throws Exception {
      assertThrows(UnavailableStateException.class, () -> {
         final DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         final DistributedLock lock = manager.getDistributedLock("a");
         final CountDownLatch notAvailable = new CountDownLatch(1);
         final DistributedLock.UnavailableLockListener listener = notAvailable::countDown;
         lock.addListener(listener);
         closeCluster();
         assertTrue(notAvailable.await(30, TimeUnit.SECONDS));
         lock.tryLock();
      });
   }

   @Test
   public void cannotTryLockWithDisconnectedServer() throws Exception {
      assertThrows(UnavailableStateException.class, () -> {
         final DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         final DistributedLock lock = manager.getDistributedLock("a");
         closeCluster();
         lock.tryLock();
      });
   }

   @Test
   public void cannotCheckLockStatusWithDisconnectedServer() throws Exception {
      assertThrows(UnavailableStateException.class, () -> {
         final DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         final DistributedLock lock = manager.getDistributedLock("a");
         assertFalse(lock.isHeldByCaller());
         assertTrue(lock.tryLock());
         closeCluster();
         lock.isHeldByCaller();
      });
   }

   public void looseLockAfterServerStop() throws ExecutionException, InterruptedException, TimeoutException, UnavailableStateException, IOException {
      assertThrows(UnavailableStateException.class, () -> {
         final DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         final DistributedLock lock = manager.getDistributedLock("a");
         assertTrue(lock.tryLock());
         assertTrue(lock.isHeldByCaller());
         final CountDownLatch notAvailable = new CountDownLatch(1);
         final DistributedLock.UnavailableLockListener listener = notAvailable::countDown;
         lock.addListener(listener);
         assertEquals(1, notAvailable.getCount());
         closeCluster();
         assertTrue(notAvailable.await(30, TimeUnit.SECONDS));
         lock.isHeldByCaller();
      });
   }

   @Test
   public void canAcquireLockOnMajorityRestart() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      final DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      final DistributedLock lock = manager.getDistributedLock("a");
      assertTrue(lock.tryLock());
      assertTrue(lock.isHeldByCaller());
      final CountDownLatch notAvailable = new CountDownLatch(1);
      final DistributedLock.UnavailableLockListener listener = notAvailable::countDown;
      lock.addListener(listener);
      assertEquals(1, notAvailable.getCount());
      stopCluster();
      notAvailable.await();
      manager.stop();
      restartMajorityNodes(true);
      final DistributedLockManager otherManager = createManagedDistributeManager();
      otherManager.start();
      // await more then the expected value, that depends by how curator session expiration is configured
      TimeUnit.MILLISECONDS.sleep(SESSION_MS + SERVER_TICK_MS);
      assertTrue(otherManager.getDistributedLock("a").tryLock());
   }

   @Test
   public void cannotStartManagerWithoutQuorum() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      stopMajority(true);
      assertFalse(manager.start(2, TimeUnit.SECONDS));
      assertFalse(manager.isStarted());
   }

   @Test
   public void cannotAcquireLockWithoutQuorum() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      assertThrows(UnavailableStateException.class, () -> {
         DistributedLockManager manager = createManagedDistributeManager();
         manager.start();
         stopMajority(true);
         DistributedLock lock = manager.getDistributedLock("a");
         lock.tryLock();
      });
   }

   @Test
   public void cannotCheckLockWithoutQuorum() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      stopMajority(true);
      DistributedLock lock = manager.getDistributedLock("a");
      final boolean held;
      try {
         held = lock.isHeldByCaller();
      } catch (UnavailableStateException expected) {
         return;
      }
      assertFalse(held);
   }

   @Test
   public void canGetLockWithoutQuorum() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      stopMajority(true);
      DistributedLock lock = manager.getDistributedLock("a");
      assertNotNull(lock);
   }

   @Test
   public void notifiedAsUnavailableWhileLoosingQuorum() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock lock = manager.getDistributedLock("a");
      CountDownLatch unavailable = new CountDownLatch(1);
      lock.addListener(unavailable::countDown);
      stopMajority(true);
      assertTrue(unavailable.await(SESSION_MS + SERVER_TICK_MS, TimeUnit.MILLISECONDS));
   }

   @Test
   public void beNotifiedOnce() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock lock = manager.getDistributedLock("a");
      final AtomicInteger unavailableManager = new AtomicInteger(0);
      final AtomicInteger unavailableLock = new AtomicInteger(0);
      manager.addUnavailableManagerListener(unavailableManager::incrementAndGet);
      lock.addListener(unavailableLock::incrementAndGet);
      stopMajority(true);
      TimeUnit.MILLISECONDS.sleep(SESSION_MS + SERVER_TICK_MS + CONNECTION_MS);
      assertEquals(1, unavailableLock.get());
      assertEquals(1, unavailableManager.get());
   }

   @Test
   public void beNotifiedOfUnavailabilityWhileBlockedOnTimedLock() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      DistributedLock lock = manager.getDistributedLock("a");
      final AtomicInteger unavailableManager = new AtomicInteger(0);
      final AtomicInteger unavailableLock = new AtomicInteger(0);
      manager.addUnavailableManagerListener(unavailableManager::incrementAndGet);
      lock.addListener(unavailableLock::incrementAndGet);
      final DistributedLockManager otherManager = createManagedDistributeManager();
      otherManager.start();
      assertTrue(otherManager.getDistributedLock("a").tryLock());
      final CountDownLatch startedTimedLock = new CountDownLatch(1);
      final AtomicReference<Boolean> unavailableTimedLock = new AtomicReference<>(null);
      Thread timedLock = new Thread(() -> {
         startedTimedLock.countDown();
         try {
            lock.tryLock(Long.MAX_VALUE, TimeUnit.DAYS);
            unavailableTimedLock.set(false);
         } catch (UnavailableStateException e) {
            unavailableTimedLock.set(true);
         } catch (InterruptedException e) {
            unavailableTimedLock.set(false);
         }
      });
      timedLock.start();
      assertTrue(startedTimedLock.await(10, TimeUnit.SECONDS));
      TimeUnit.SECONDS.sleep(1);
      stopMajority(true);
      Wait.waitFor(() -> unavailableLock.get() > 0, SESSION_MS + CONNECTION_MS + SERVER_TICK_MS);
      Wait.waitFor(() -> unavailableManager.get() > 0, SESSION_MS + CONNECTION_MS);
      assertEquals(1, unavailableManager.get());
      Boolean result = unavailableTimedLock.get();
      assertNotNull(result);
      assertTrue(result);
   }

   @Test
   public void beNotifiedOfAlreadyUnavailableManagerAfterAddingListener() throws Exception {
      assumeTrue(zkNodes > 1, zkNodes + " <= 1");
      DistributedLockManager manager = createManagedDistributeManager();
      manager.start();
      final AtomicBoolean unavailable = new AtomicBoolean(false);
      DistributedLockManager.UnavailableManagerListener managerListener = () -> {
         unavailable.set(true);
      };
      manager.addUnavailableManagerListener(managerListener);
      assertFalse(unavailable.get());
      stopMajority(true);
      Wait.waitFor(unavailable::get);
      manager.removeUnavailableManagerListener(managerListener);
      final AtomicInteger unavailableOnRegister = new AtomicInteger();
      manager.addUnavailableManagerListener(unavailableOnRegister::incrementAndGet);
      assertEquals(1, unavailableOnRegister.get());
      unavailableOnRegister.set(0);
      try (DistributedLock lock = manager.getDistributedLock("a")) {
         lock.addListener(unavailableOnRegister::incrementAndGet);
         assertEquals(1, unavailableOnRegister.get());
      }
   }

   private void stopCluster() throws Exception {
      clusterDirty = true;
      testingServer.stop();
   }

   private void closeCluster() throws IOException {
      clusterDirty = true;
      testingServer.close();
   }

   private void stopMajority(boolean fromLast) throws Exception {
      clusterDirty = true;
      List<TestingZooKeeperServer> followers = testingServer.getServers();
      final int quorum = (zkNodes / 2) + 1;
      for (int i = 0; i < quorum; i++) {
         final int nodeIndex = fromLast ? (followers.size() - 1) - i : i;
         followers.get(nodeIndex).stop();
      }
   }

   private void restartMajorityNodes(boolean startFromLast) throws Exception {
      final int quorum = (zkNodes / 2) + 1;
      for (int i = 0; i < quorum; i++) {
         final int nodeIndex = startFromLast ? (zkNodes - 1) - i : i;
         if (!testingServer.restartServer(clusterSpecs[nodeIndex])) {
            throw new IllegalStateException("errored while restarting " + clusterSpecs[nodeIndex]);
         }
      }
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs() && !result.isDirectory()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
