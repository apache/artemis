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
package org.apache.activemq.artemis.tests.smoke.lockmanager;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.activemq.artemis.core.server.lock.LockCoordinator;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesClient;
import org.apache.activemq.artemis.tests.smoke.lockmanager.kubetimeskew.KubeLockManagerTimeSkew;
import org.apache.artemis.lock.kube.KubeLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test needs external dependencies. It follows the same pattern described at {@link DualMirrorSingleAcceptorRunningTest}.
 * please read the documentation from that test for more detail on how to run this test.
 */
public class LockCoordinatorTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int ZK_BASE_PORT = 2181;
   private static final String ZK_ENDPOINTS = "127.0.0.1:2181";
   private static final long KEEP_ALIVE_INTERVAL_MS = 200;
   private static final int LEASE_TIMEOUT = 1;
   private static final int NUM_THREADS = 10;

   private ExecutorService executorService;
   private ScheduledExecutorService scheduledExecutor;
   private AtomicInteger lockHolderCount;
   private AtomicInteger lockChanged;
   private OrderedExecutorFactory executorFactory;

   @BeforeEach
   @Override
   public void setUp() {
      disableCheckThread();
      scheduledExecutor = Executors.newScheduledThreadPool(NUM_THREADS);
      executorService = Executors.newFixedThreadPool(NUM_THREADS * 2);
      executorFactory = new OrderedExecutorFactory(executorService);
      lockHolderCount = new AtomicInteger(0);
      lockChanged = new AtomicInteger(0);
   }

   @AfterEach
   @Override
   public void tearDown() {
      scheduledExecutor.shutdownNow();
      executorService.shutdownNow();
   }

   @Test
   public void testWithFile() throws Throwable {
      internalTest(this::getFileCoordinators);
   }

   private List<LockCoordinatorWithPause> getFileCoordinators(int numberOfCoordinators) {
      File file = new File(getTemporaryDir() + "/lockFolder");
      file.mkdirs();
      HashMap<String, String> parameters = new HashMap<>();
      parameters.put("locks-folder", file.getAbsolutePath());
      return getLockCoordinators(numberOfCoordinators, FileBasedLockManager.class.getName(), parameters);
   }

   // This test will use minikube if available and running.
   // To run this test locally, start minikube with: minikube start
   // It will be ignored (with an assumption) if the configuration options provided by minikube cannot be found.
   // See MinikubeSupport.supported for how this validation occurs.
   // This test is important for development purposes. testWithFakekube is provided for CI validations.
   @Test
   public void testWithMinikube() throws Throwable {
      Assumptions.assumeTrue(MinikubeSupport.supported());

      KubernetesClient.clear(true); // clearing it just in case a previous test forgot to clean it

      String apiURI = MinikubeSupport.getKubeconfigServer();
      KubernetesClient.setParam(KubernetesClient.KUBERNETES_API_URI, apiURI);
      runAfter(() -> {
         KubernetesClient.clear(true);
      });

      // Set up RBAC permissions for leases
      MinikubeSupport.setupRBAC();
      runAfter(MinikubeSupport::cleanupRBAC);

      String token = MinikubeSupport.generateKubectlToken();
      assertNotNull(token);

      File tokenFile = new File(getTestDirfile(), "token.cr");
      File caFile = new File(getTestDirfile(), "ca.crt");

      Files.writeString(tokenFile.toPath(), token);

      String caCert = MinikubeSupport.extractCACertificate();
      Files.writeString(caFile.toPath(), caCert);

      KubernetesClient.setParam(KubernetesClient.KUBERNETES_CA_PATH, caFile.getAbsolutePath());
      KubernetesClient.setParam(KubernetesClient.KUBERNETES_TOKEN_PATH, tokenFile.getAbsolutePath());

      internalTest(this::getMinikubeCoordinators);
      internalTestKubernetes(this::getMinikubeCoordinators);
   }

   private List<LockCoordinatorWithPause> getMinikubeCoordinators(int numberOfCoordinators) {
      try {
         String hostPortion = "host_" + RandomUtil.randomAlphaNumericString(10);
         ArrayList<LockCoordinatorWithPause> locks = new ArrayList<>();
         String lockName = "lock-test-" + RandomUtil.randomUUIDString();
         for (int i = 0; i < numberOfCoordinators; i++) {
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("hostname", hostPortion + "_host_" + i);
            parameters.put("namespace", "default");
            parameters.put("lease-timeout", String.valueOf(LEASE_TIMEOUT));
            parameters.put("time-adjustment", String.valueOf(-i));
            DistributedLockManager lockManager = DistributedLockManager.newInstanceOf(KubeLockManagerTimeSkew.class.getName(), parameters);

            LockCoordinatorWithPause lockCoordinator = new LockCoordinatorWithPause(scheduledExecutor, executorFactory.getExecutor(), KEEP_ALIVE_INTERVAL_MS, lockManager, lockName, lockName);
            lockCoordinator.onLockAcquired(() -> lock(lockCoordinator));
            lockCoordinator.onLockReleased(() -> unlock(lockCoordinator));
            lockCoordinator.onLockReleased(() -> lockChanged.incrementAndGet());
            lockCoordinator.onLockAcquired(() -> lockChanged.incrementAndGet());
            lockCoordinator.setDebugInfo("ID" + i);
            locks.add(lockCoordinator);
         }
         return locks;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Test
   public void testWithFakekube() throws Throwable {
      KubernetesClient.clear(true); // clearing it just in case a previous test forgot to clean it
      try (Fakekube fakekube = new Fakekube()) {
         fakekube.start(getTestDirfile());


         KubernetesClient.setParam(KubernetesClient.KUBERNETES_API_URI, fakekube.getApiUri());
         KubernetesClient.setParam(KubernetesClient.KUBERNETES_TOKEN_PATH,
                                   LockCoordinatorTest.class.getClassLoader().getResource("client_token").getPath());

         URL caPath = LockCoordinatorTest.class.getClassLoader()
            .getResource("client-and-server-ca-certs.pem");

         assertNotNull(caPath);
         KubernetesClient.setParam(KubernetesClient.KUBERNETES_CA_PATH, caPath.getPath());

         runAfter(() -> {
            KubernetesClient.clear(true);
         });

         internalTest(this::getFakekubeCoordinators);
         internalTestKubernetes(this::getFakekubeCoordinators);
      }
   }

   private List<LockCoordinatorWithPause> getFakekubeCoordinators(int numberOfCoordinators) {
      try {
         String hostPortion = "host_" + RandomUtil.randomAlphaNumericString(10);
         ArrayList<LockCoordinatorWithPause> locks = new ArrayList<>();
         String lockName = "lock-test-" + RandomUtil.randomUUIDString();
         for (int i = 0; i < numberOfCoordinators; i++) {
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("hostname", hostPortion + "_host_" + i);
            parameters.put("namespace", "default");
            parameters.put("lease-timeout", String.valueOf(LEASE_TIMEOUT));
            parameters.put("time-adjustment", String.valueOf(-i));
            DistributedLockManager lockManager = DistributedLockManager.newInstanceOf(KubeLockManagerTimeSkew.class.getName(), parameters);

            LockCoordinatorWithPause lockCoordinator = new LockCoordinatorWithPause(scheduledExecutor, executorFactory.getExecutor(), KEEP_ALIVE_INTERVAL_MS, lockManager, lockName, lockName);
            lockCoordinator.onLockAcquired(() -> lock(lockCoordinator));
            lockCoordinator.onLockReleased(() -> unlock(lockCoordinator));
            lockCoordinator.onLockReleased(() -> lockChanged.incrementAndGet());
            lockCoordinator.onLockAcquired(() -> lockChanged.incrementAndGet());
            lockCoordinator.setDebugInfo("ID" + i);
            locks.add(lockCoordinator);
         }
         return locks;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Test
   public void testWithZK() throws Throwable {
      ZookeeperCluster zkCluster = startZK();
      internalTest(i -> getZKCoordinators(i, zkCluster.getConnectString()));
   }

   private List<LockCoordinatorWithPause> getZKCoordinators(int numberOfCoordinators, String connectString) {
      HashMap<String, String> parameters = new HashMap<>();
      parameters.put("connect-string", connectString);
      return getLockCoordinators(numberOfCoordinators, CuratorDistributedLockManager.class.getName(), parameters);
   }

   private ZookeeperCluster startZK() throws Exception {
      ZookeeperCluster zkCluster = new ZookeeperCluster(temporaryFolder, 1, ZK_BASE_PORT, 100);
      zkCluster.start();
      runAfter(zkCluster::stop);
      assertEquals(ZK_ENDPOINTS, zkCluster.getConnectString());
      return zkCluster;
   }

   private void internalTest(Function<Integer, List<LockCoordinatorWithPause>> lockCoordinatorSupplier) throws Throwable {
      try {
         logTestStart("testSimplePair");
         testSimplePair(lockCoordinatorSupplier.apply(2));
         logTestEnd("testSimplePair");

         logTestStart("testOnlyOneLockHolderAtATime");
         testOnlyOneLockHolderAtATime(lockCoordinatorSupplier.apply(NUM_THREADS));
         logTestEnd("testOnlyOneLockHolderAtATime");

         logTestStart("testAddAfterLocked");
         testAddAfterLocked(lockCoordinatorSupplier.apply(1).get(0));
         logTestEnd("testAddAfterLocked");

         logTestStart("testRetryAfterError");
         testRetryAfterError(lockCoordinatorSupplier.apply(1).get(0));
         logTestEnd("testRetryAfterError");

         logTestStart("testRetryAfterErrorWithDelayAdd");
         testRetryAfterErrorWithDelayAdd(lockCoordinatorSupplier.apply(1).get(0));
         logTestEnd("testRetryAfterErrorWithDelayAdd");

         logTestStart("testPriorityOrdering");
         testPriorityOrdering(lockCoordinatorSupplier.apply(1).get(0));
         logTestEnd("testPriorityOrdering");

         {
            List<LockCoordinatorWithPause> list = lockCoordinatorSupplier.apply(2);
            logTestStart("testNoRetryWhileNotAcquired");
            testNoRetryWhileNotAcquired(list.get(0), list.get(1));
            logTestEnd("testNoRetryWhileNotAcquired");
         }
      } catch (Throwable e) {
         // this is just to capture it into logging
         logger.warn(e.getMessage(), e);
         throw e;
      }
   }

   // Tests that are specific to Kubernetes (minikube and fakekube)
   private void internalTestKubernetes(Function<Integer, List<LockCoordinatorWithPause>> lockCoordinatorSupplier) throws Exception {
      logTestStart("testTimeSensitiveTest");
      testTimeSensitiveTest(lockCoordinatorSupplier.apply(2));
      logTestEnd("testTimeSensitiveTest");

      logTestStart("testKubeTransitions");
      testKubeTransitions(lockCoordinatorSupplier.apply(2));
      logTestEnd("testKubeTransitions");

   }

   private void testAddAfterLocked(LockCoordinatorWithPause lockCoordinator) throws Exception {
      lockHolderCount.set(0);
      lockChanged.set(0);

      try {
         lockCoordinator.start();
         Wait.assertEquals(1, () -> lockHolderCount.get(), 15000, 100);

         AtomicInteger afterRunning = new AtomicInteger(0);
         assertTrue(lockCoordinator.isLocked());
         lockCoordinator.onLockAcquired(afterRunning::incrementAndGet);

         Wait.assertEquals(1, afterRunning::get);

         Wait.assertEquals(1, lockHolderCount::get, 5000, 100);
      } finally {
         lockCoordinator.stop();
      }
   }

   private void testRetryAfterError(LockCoordinatorWithPause lockCoordinator) throws Exception {
      lockHolderCount.set(0);
      lockChanged.set(0);

      AtomicBoolean succeeded = new AtomicBoolean(false);
      AtomicInteger numberOfTries = new AtomicInteger(0);
      try {
         lockCoordinator.onLockAcquired(() -> {
            if (numberOfTries.incrementAndGet() < 5) {
               throw new IOException("please retry");
            }
            succeeded.set(true);
         });
         lockCoordinator.start();

         Wait.assertTrue(succeeded::get, 5000, 100);
         Wait.assertEquals(1, lockHolderCount::get);
      } finally {
         lockCoordinator.stop();
      }
   }

   private void testRetryAfterErrorWithDelayAdd(LockCoordinatorWithPause lockCoordinator) throws Exception {
      lockHolderCount.set(0);
      lockChanged.set(0);

      AtomicBoolean succeeded = new AtomicBoolean(false);
      AtomicInteger numberOfTries = new AtomicInteger(0);
      try {
         lockCoordinator.start();
         Wait.assertEquals(1, lockHolderCount::get);

         lockCoordinator.onLockAcquired(() -> {
            if (numberOfTries.incrementAndGet() < 5) {
               throw new RuntimeException("please retry");
            }
            succeeded.set(true);
         });

         Wait.assertTrue(succeeded::get, 5000, 100);
         Wait.assertEquals(1, lockHolderCount::get);
      } finally {
         lockCoordinator.stop();
      }
   }

   private void testPriorityOrdering(LockCoordinatorWithPause lockCoordinator) throws Exception {
      lockHolderCount.set(0);
      lockChanged.set(0);

      try {
         // Add callbacks with different priorities (lower values execute first)
         List<Integer> executionOrder = new ArrayList<>();
         lockCoordinator.onLockAcquired(() -> executionOrder.add(3), 30);
         lockCoordinator.onLockAcquired(() -> executionOrder.add(1), 10);
         lockCoordinator.onLockAcquired(() -> executionOrder.add(2), 20);
         lockCoordinator.onLockAcquired(() -> executionOrder.add(4), 40);

         List<Integer> releaseOrder = new ArrayList<>();
         lockCoordinator.onLockReleased(() -> releaseOrder.add(2), 20);
         lockCoordinator.onLockReleased(() -> releaseOrder.add(4), 40);
         lockCoordinator.onLockReleased(() -> releaseOrder.add(1), 10);
         lockCoordinator.onLockReleased(() -> releaseOrder.add(3), 30);

         lockCoordinator.start();
         Wait.assertEquals(1, () -> lockHolderCount.get(), 15000, 100);

         // Verify acquired callbacks executed in priority order (lowest first)
         Wait.assertEquals(4, executionOrder::size, 5000, 100);
         assertEquals(List.of(1, 2, 3, 4), executionOrder);

         lockCoordinator.stop();

         // Verify released callbacks executed in priority order (lowest first)
         Wait.assertEquals(4, releaseOrder::size, 5000, 100);
         assertEquals(List.of(1, 2, 3, 4), releaseOrder);
      } finally {
         lockCoordinator.stop();
      }
   }

   // validate that no retry would happen since the lock wasn't held in the secondLock
   private void testNoRetryWhileNotAcquired(LockCoordinatorWithPause firstLock, LockCoordinatorWithPause secondLock) throws Exception {
      lockHolderCount.set(0);
      lockChanged.set(0);
      AtomicBoolean throwError = new AtomicBoolean(true);
      AtomicBoolean errorHappened = new AtomicBoolean(false);

      AtomicBoolean succeeded = new AtomicBoolean(false);
      try {
         firstLock.start();
         Wait.assertEquals(1, lockHolderCount::get);
         assertTrue(firstLock.isLocked());
         secondLock.start();
         assertFalse(secondLock.isLocked());

         secondLock.onLockAcquired(() -> {
            if (throwError.get()) {
               errorHappened.set(true);
               throw new RuntimeException("please retry");
            }
            succeeded.set(true);
         });

         assertFalse(succeeded.get());
         assertFalse(errorHappened.get());
         firstLock.stop();
         Wait.assertTrue(errorHappened::get, 5000, 100);
         throwError.set(false);
         Wait.assertTrue(succeeded::get, 5000, 100);
         Wait.assertEquals(1, lockHolderCount::get);
      } finally {
         firstLock.stop();
         secondLock.stop();
      }
   }


   public void testSimplePair(List<LockCoordinatorWithPause> list) throws Exception {
      assertEquals(2, list.size());
      DistributedLockManager lockManager0 = list.get(0).getLockManager();
      DistributedLockManager lockManager1 = list.get(1).getLockManager();

      String lockid = "lock" + RandomUtil.randomUUIDString();

      lockManager0.start();
      lockManager1.start();

      try {
         for (int i = 0; i < 10; i++) {
            DistributedLock lock0 = lockManager0.getDistributedLock(lockid);
            assertTrue(lock0.tryLock());
            assertTrue(lock0.isHeldByCaller());
            DistributedLock lock1 = lockManager1.getDistributedLock(lockid);
            assertFalse(lock1.tryLock());
            assertFalse(lock1.isHeldByCaller());

            lock0.unlock();
            assertTrue(lock1.tryLock());
            assertTrue(lock1.isHeldByCaller());
            lock1.unlock();
         }
      } finally {
         lockManager0.stop();
         lockManager1.stop();
      }
   }

   private void testOnlyOneLockHolderAtATime(List<LockCoordinatorWithPause> lockCoordinators) throws Exception {
      try {

         lockCoordinators.forEach(LockCoordinatorWithPause::start);

         Wait.assertEquals(1, () -> lockHolderCount.get(), 15000, 100);

         long value = RandomUtil.randomPositiveLong();

         String mutableLongId = "mutableLong" + RandomUtil.randomUUIDString();

         boolean first = true;

         for (LockCoordinatorWithPause lockCoordinator : lockCoordinators) {
            MutableLong mutableLong = lockCoordinator.getLockManager().getMutableLong(mutableLongId);
            if (first) {
               mutableLong.set(value);
               first = false;
            } else {
               assertEquals(value, mutableLong.get());
            }
            mutableLong.close();
         }

         logger.info("Stopping ********************************************************************************");

         // We keep stopping lockManager that is holding the lock
         // we do this until we stop every one of the locks
         while (!lockCoordinators.isEmpty()) {
            if (!Wait.waitFor(() -> lockHolderCount.get() == 1, 15000, 100)) {
               for (LockCoordinatorWithPause lock : lockCoordinators) {
                  logger.info("lock {} is holdingLock={}", lock.getDebugInfo(), lock.isLocked());
               }
            }
            Wait.assertEquals(1, () -> lockHolderCount.get(), 15000, 100);
            for (LockCoordinatorWithPause lock : lockCoordinators) {
               if (lock.isLocked()) {
                  long changed = lockChanged.get();
                  lock.stop();
                  lockCoordinators.remove(lock);
                  //Wait.assertTrue(() -> lockChanged.get() != changed, 5000, 100);
                  break;
               }
            }
         }

         // Verify that no locks are held after stopping
         Wait.assertEquals(0, () -> lockHolderCount.get(), 15000, 100);
      } finally {
         try {
            lockCoordinators.forEach(LockCoordinatorWithPause::stop);
         } catch (Throwable ignored) {
         }
      }
   }

   private List<LockCoordinatorWithPause> getLockCoordinators(int numberOfCoordinators, String factoryName, HashMap<String, String> parameters) {
      return getLockCoordinators(numberOfCoordinators, () -> {
         try {
            return DistributedLockManager.newInstanceOf(factoryName, parameters);
         } catch (Exception e) {
            fail(e.getMessage(), e);
            return null;
         }
      });
   }

   private List<LockCoordinatorWithPause> getLockCoordinators(int numberOfCoordinators, Supplier<DistributedLockManager> lockManagerSupplier) {
      List<LockCoordinatorWithPause> locks = new ArrayList<>();
      String lockName = "lock-test-" + RandomUtil.randomUUIDString();
      for (int i = 0; i < numberOfCoordinators; i++) {
         DistributedLockManager lockManager = lockManagerSupplier.get();

         LockCoordinatorWithPause lockCoordinator = new LockCoordinatorWithPause(scheduledExecutor, executorFactory.getExecutor(), KEEP_ALIVE_INTERVAL_MS, lockManager, lockName, lockName);
         lockCoordinator.onLockAcquired(() -> lock(lockCoordinator));
         lockCoordinator.onLockReleased(() -> unlock(lockCoordinator));
         lockCoordinator.onLockReleased(() -> lockChanged.incrementAndGet());
         lockCoordinator.onLockAcquired(() -> lockChanged.incrementAndGet());
         lockCoordinator.setDebugInfo("ID" + i);
         locks.add(lockCoordinator);
      }
      return locks;
   }

   /**
    * Tests that lock coordination handles timing-sensitive scenarios correctly.
    * This test validates proper lease expiration handling and lock acquisition timing,
    * which can be affected by clock skew in distributed systems.
    *
    * Specifically tests:
    * - Lock cannot be stolen while actively held
    * - Lock is properly released and can be acquired by waiting coordinator
    * - Multiple rapid acquire/release cycles work correctly
    */
   private void testTimeSensitiveTest(List<LockCoordinatorWithPause> lockCoordinators) throws Exception {
      assertEquals(2, lockCoordinators.size());
      lockHolderCount.set(0);
      lockChanged.set(0);

      LockCoordinatorWithPause coordinator0 = lockCoordinators.get(0);
      LockCoordinatorWithPause coordinator1 = lockCoordinators.get(1); // lock 1 is skewed by 1 minute behind

      try {
         coordinator1.start();
         Wait.assertEquals(1, () -> lockHolderCount.get(), 5000, 100);
         assertTrue(coordinator1.isLocked());
         coordinator0.start();
         Thread.sleep(500);
         assertFalse(coordinator0.isLocked(), "Lock was stolen");
         assertEquals(1, lockHolderCount.get());
         coordinator1.pause(); // pretend coordinator1 crashed. No more updates coming to the lease
         Wait.assertTrue(coordinator0::isLocked, 5000, 100);
         DistributedLock internalLock = coordinator1.getDistributedLock();
         if (internalLock instanceof KubeLock) {
            System.out.println("Transitions::" + ((KubeLock)internalLock).getLeaseTransitions());
         }
         coordinator0.stop();
         assertEquals(1, lockHolderCount.get());

      } finally {
         coordinator0.stop();
         coordinator1.stop();
      }
   }

   private void testKubeTransitions(List<LockCoordinatorWithPause> lockCoordinators) throws Exception {
      assertEquals(2, lockCoordinators.size());
      lockHolderCount.set(0);
      lockChanged.set(0);

      LockCoordinatorWithPause coordinator0 = lockCoordinators.get(0);
      LockCoordinatorWithPause coordinator1 = lockCoordinators.get(1); // lock 1 is skewed by 1 minute behind
      coordinator0.start();
      Wait.assertTrue(coordinator0::isLocked);
      coordinator1.start();
      Wait.assertTrue(() -> coordinator1.getDistributedLock() != null);
      assertEquals(0, getKubeTransitions(coordinator0));
      assertEquals(0, getKubeTransitions(coordinator1));

      for (int i = 0; i < 5; i++) {
         if (i % 2 == 0) {
            Wait.assertTrue(coordinator0::isLocked);
            coordinator0.pause();
            Wait.assertTrue(coordinator1::isLocked);
            coordinator0.resume();
         } else {
            Wait.assertTrue(coordinator1::isLocked);
            coordinator1.pause();
            Wait.assertTrue(coordinator0::isLocked);
            coordinator1.resume();
         }
         // After each transition, leaseTransitions should be i+1
         // Initial state: 0, after 1st transition: 1, after 2nd: 2, etc.
         assertEquals(i + 1, getKubeTransitions(coordinator0));
         assertEquals(i + 1, getKubeTransitions(coordinator1));
      }
   }

   private int getKubeTransitions(LockCoordinator coordinator) throws Exception {
      KubeLock lock = (KubeLock) coordinator.getDistributedLock();
      return lock.getLeaseTransitions();
   }

   // This is used on callback
   private void lock(LockCoordinatorWithPause lockCoordinator) {
      logger.info("++Lock {} lock", lockCoordinator.getDebugInfo());
      lockHolderCount.incrementAndGet();
   }

   // This is used on callback
   private void unlock(LockCoordinatorWithPause lockCoordinator) {
      logger.info("--Lock {} unlocking", lockCoordinator.getDebugInfo());
      lockHolderCount.decrementAndGet();
   }

   private void logTestStart(String testName) {
      logger.info("\n********************************************************************************\n {} starting\n", testName);
   }

   private void logTestEnd(String testName) {
      logger.info("\n\n{} finished.\n################################################################################\n\n", testName);
   }


   protected static class LockCoordinatorWithPause extends LockCoordinator {

      public LockCoordinatorWithPause(ScheduledExecutorService scheduledExecutor,
                                      ArtemisExecutor executor,
                                      long checkPeriod,
                                      DistributedLockManager lockManager,
                                      String lockID,
                                      String name) {
         super(scheduledExecutor, executor, checkPeriod, lockManager, lockID, name);
      }

      public void pause() {
         if (future != null) {
            future.cancel(false);
         }
      }

      public void resume() {
         pause();
         final AtomicBoolean booked = this.bookedForRunning;
         future = scheduledExecutorService.scheduleWithFixedDelay(() -> runForScheduler(booked), 0, period, timeUnit);
      }

      @Override
      public LockCoordinatorWithPause setDebugInfo(String debugInfo) {
         super.setDebugInfo(debugInfo);
         return this;
      }
   }

}
