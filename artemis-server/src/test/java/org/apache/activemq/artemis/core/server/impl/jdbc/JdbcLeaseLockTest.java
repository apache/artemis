/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager.LockListener;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCUtils;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class JdbcLeaseLockTest extends ServerTestBase {

   private JdbcSharedStateManager jdbcSharedStateManager;
   private DatabaseStorageConfiguration dbConf;
   private SQLProvider sqlProvider;

   @Parameters(name = "create_tables_prior_test={0}")
   public static List<Object[]> data() {
      return Arrays.asList(new Object[][] {
         {true},
         {false}
      });
   }

   @Parameter(index = 0)
   public boolean withExistingTable;


   private LeaseLock lock() {
      return lock(dbConf.getJdbcLockExpirationMillis());
   }

   private LeaseLock lock(long acquireMillis) {
      try {
         return JdbcSharedStateManager
            .createPrimaryLock(
               UUID.randomUUID().toString(),
               jdbcSharedStateManager.getJdbcConnectionProvider(),
               sqlProvider,
               acquireMillis,
               dbConf.getJdbcAllowedTimeDiff());
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private LeaseLock lock(long acquireMillis, long queryTimeoutMillis) {
      try {
         return JdbcSharedStateManager
            .createPrimaryLock(
               UUID.randomUUID().toString(),
               jdbcSharedStateManager.getJdbcConnectionProvider(),
               sqlProvider,
               acquireMillis,
               queryTimeoutMillis,
               dbConf.getJdbcAllowedTimeDiff());
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @BeforeEach
   public void createLockTable() throws Exception {
      dbConf = createDefaultDatabaseStorageConfiguration();
      sqlProvider = JDBCUtils.getSQLProvider(
         dbConf.getJdbcDriverClassName(),
         dbConf.getNodeManagerStoreTableName(),
         SQLProvider.DatabaseStoreType.NODE_MANAGER);

      if (withExistingTable) {
         TestJDBCDriver testDriver = TestJDBCDriver
            .usingDbConf(
               dbConf,
               sqlProvider);
         testDriver.start();
         testDriver.stop();
      }

      jdbcSharedStateManager = JdbcSharedStateManager
         .usingConnectionProvider(
            UUID.randomUUID().toString(),
            dbConf.getJdbcLockExpirationMillis(),
            dbConf.getJdbcAllowedTimeDiff(),
            dbConf.getConnectionProvider(),
            sqlProvider);
   }

   @AfterEach
   public void dropLockTable() throws Exception {
      jdbcSharedStateManager.destroy();
      jdbcSharedStateManager.close();
   }

   @TestTemplate
   public void shouldAcquireLock() {
      final LeaseLock lock = lock();
      final boolean acquired = lock.tryAcquire();
      assertTrue(acquired, "Must acquire the lock!");
      try {
         assertTrue(lock.isHeldByCaller(), "The lock is been held by the caller!");
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldNotAcquireLockWhenAlreadyHeldByOthers() {
      final LeaseLock lock = lock();
      assertTrue(lock.tryAcquire(), "Must acquire the lock");
      try {
         assertTrue(lock.isHeldByCaller(), "Lock held by the caller");
         final LeaseLock failingLock = lock();
         assertFalse(failingLock.tryAcquire(), "lock already held by other");
         assertFalse(failingLock.isHeldByCaller(), "lock already held by other");
         assertTrue(failingLock.isHeld(), "lock already held by other");
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldNotAcquireLockTwice() {
      final LeaseLock lock = lock();
      assertTrue(lock.tryAcquire(), "Must acquire the lock");
      try {
         assertFalse(lock.tryAcquire(), "lock already acquired");
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldNotCorruptGuardedState() throws InterruptedException {
      final AtomicLong sharedState = new AtomicLong(0);
      final int producers = 2;
      final int writesPerProducer = 10;
      final long idleMillis = 1000;
      final long millisToAcquireLock = writesPerProducer * (producers - 1) * idleMillis;
      final LeaseLock.Pauser pauser = LeaseLock.Pauser.sleep(idleMillis, TimeUnit.MILLISECONDS);
      final CountDownLatch finished = new CountDownLatch(producers);
      final LeaseLock[] locks = new LeaseLock[producers];
      final AtomicInteger lockIndex = new AtomicInteger(0);
      final Runnable producerTask = () -> {
         final LeaseLock lock = locks[lockIndex.getAndIncrement()];
         try {
            for (int i = 0; i < writesPerProducer; i++) {
               final LeaseLock.AcquireResult acquireResult = lock.tryAcquire(millisToAcquireLock, pauser, () -> true);
               if (acquireResult != LeaseLock.AcquireResult.Done) {
                  throw new IllegalStateException(acquireResult + " from " + Thread.currentThread());
               }
               //avoid the atomic getAndIncrement operation on purpose
               sharedState.lazySet(sharedState.get() + 1);
               lock.release();
            }
         } finally {
            finished.countDown();
         }
      };
      final Thread[] producerThreads = new Thread[producers];
      for (int i = 0; i < producers; i++) {
         locks[i] = lock();
         producerThreads[i] = new Thread(producerTask);
      }
      Stream.of(producerThreads).forEach(Thread::start);
      final long maxTestTime = millisToAcquireLock * writesPerProducer * producers;
      assertTrue(finished.await(maxTestTime, TimeUnit.MILLISECONDS), "Each producers must complete the writes");
      assertEquals(writesPerProducer * producers, sharedState.get(), "locks hasn't mutual excluded producers");
   }

   @TestTemplate
   public void shouldAcquireExpiredLock() throws InterruptedException {
      final LeaseLock lock = lock(10);
      assertTrue(lock.tryAcquire(), "lock is not owned by anyone");
      try {
         Thread.sleep(lock.expirationMillis() * 2);
         assertFalse(lock.isHeldByCaller(), "lock is already expired");
         assertFalse(lock.isHeld(), "lock is already expired");
         assertTrue(lock.tryAcquire(), "lock is already expired");
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldOtherAcquireExpiredLock() throws InterruptedException {
      final LeaseLock lock = lock(10);
      assertTrue(lock.tryAcquire(), "lock is not owned by anyone");
      try {
         Thread.sleep(lock.expirationMillis() * 2);
         assertFalse(lock.isHeldByCaller(), "lock is already expired");
         assertFalse(lock.isHeld(), "lock is already expired");
         final LeaseLock otherLock = lock(10);
         try {
            assertTrue(otherLock.tryAcquire(), "lock is already expired");
         } finally {
            otherLock.release();
         }
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldRenewAcquiredLock() throws InterruptedException {
      final LeaseLock lock = lock(TimeUnit.SECONDS.toMillis(10));
      assertTrue(lock.tryAcquire(), "lock is not owned by anyone");
      try {
         assertTrue(lock.renew(), "lock is owned");
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldNotRenewReleasedLock() throws InterruptedException {
      final LeaseLock lock = lock(TimeUnit.SECONDS.toMillis(10));
      assertTrue(lock.tryAcquire(), "lock is not owned by anyone");
      lock.release();
      assertFalse(lock.isHeldByCaller(), "lock is already released");
      assertFalse(lock.isHeld(), "lock is already released");
      assertFalse(lock.renew(), "lock is already released");
   }

   @TestTemplate
   public void shouldRenewExpiredLockNotAcquiredByOthers() throws InterruptedException {
      final LeaseLock lock = lock(500);
      assertTrue(lock.tryAcquire(), "lock is not owned by anyone");
      try {
         Thread.sleep(lock.expirationMillis() * 2);
         assertFalse(lock.isHeldByCaller(), "lock is already expired");
         assertFalse(lock.isHeld(), "lock is already expired");
         assertTrue(lock.renew(), "lock is owned");
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldNotRenewLockAcquiredByOthers() throws InterruptedException {
      final LeaseLock lock = lock(10);
      assertTrue(lock.tryAcquire(), "lock is not owned by anyone");
      try {
         Thread.sleep(lock.expirationMillis() * 2);
         assertFalse(lock.isHeldByCaller(), "lock is already expired");
         assertFalse(lock.isHeld(), "lock is already expired");
         final LeaseLock otherLock = lock(TimeUnit.SECONDS.toMillis(10));
         assertTrue(otherLock.tryAcquire(), "lock is already expired");
         try {
            assertFalse(lock.renew(), "lock is owned by others");
         } finally {
            otherLock.release();
         }
      } finally {
         lock.release();
      }
   }

   @TestTemplate
   public void shouldNotNotifyLostLock() throws Exception {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      final OrderedExecutorFactory factory = new OrderedExecutorFactory(executorService);
      final ArtemisExecutor artemisExecutor = factory.getExecutor();
      final AtomicLong lostLock = new AtomicLong();
      final LockListener lockListener = () -> {
         lostLock.incrementAndGet();
      };
      final ScheduledLeaseLock scheduledLeaseLock = ScheduledLeaseLock
         .of(scheduledExecutorService, artemisExecutor,
             "test", lock(), dbConf.getJdbcLockRenewPeriodMillis(), lockListener);

      assertTrue(scheduledLeaseLock.lock().tryAcquire());
      scheduledLeaseLock.start();
      assertEquals(0, lostLock.get());
      scheduledLeaseLock.stop();
      assertEquals(0, lostLock.get());
      executorService.shutdown();
      scheduledExecutorService.shutdown();
      scheduledLeaseLock.lock().release();
   }


   @TestTemplate
   public void shouldNotifyManyTimesLostLock() throws Exception {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      final OrderedExecutorFactory factory = new OrderedExecutorFactory(executorService);
      final ArtemisExecutor artemisExecutor = factory.getExecutor();
      final AtomicLong lostLock = new AtomicLong();
      final LockListener lockListener = () -> {
         lostLock.incrementAndGet();
      };
      final ScheduledLeaseLock scheduledLeaseLock = ScheduledLeaseLock
         .of(scheduledExecutorService, artemisExecutor,
             "test", lock(TimeUnit.SECONDS.toMillis(1)), 100, lockListener);

      assertTrue(scheduledLeaseLock.lock().tryAcquire());
      scheduledLeaseLock.start();
      // should let the renew to happen at least 1 time, excluding the time to start a scheduled task
      TimeUnit.MILLISECONDS.sleep(2 * scheduledLeaseLock.renewPeriodMillis());
      assertTrue(scheduledLeaseLock.lock().isHeldByCaller());
      assertEquals(0, lostLock.get());
      scheduledLeaseLock.lock().release();
      assertFalse(scheduledLeaseLock.lock().isHeldByCaller());
      TimeUnit.MILLISECONDS.sleep(3 * scheduledLeaseLock.renewPeriodMillis());
      assertTrue(lostLock.get() >= 2L, lostLock.get() + " < 2");
      scheduledLeaseLock.stop();
      executorService.shutdown();
      scheduledExecutorService.shutdown();
   }

   @TestTemplate
   public void shouldJdbcAndSystemTimeToBeAligned() throws InterruptedException {
      final LeaseLock lock = lock(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10));
      assumeTrue(lock instanceof JdbcLeaseLock, lock + " is not an instance of JdbcLeaseLock");
      final JdbcLeaseLock jdbcLock = JdbcLeaseLock.class.cast(lock);
      final long utcSystemTime = System.currentTimeMillis();
      TimeUnit.SECONDS.sleep(1);
      final long utcJdbcTime = jdbcLock.dbCurrentTimeMillis();
      final long millisDiffJdbcSystem = utcJdbcTime - utcSystemTime;
      assertTrue(millisDiffJdbcSystem >= 0L, millisDiffJdbcSystem + " < 0");
      assertTrue(millisDiffJdbcSystem < 10_000, millisDiffJdbcSystem + " >= 10_000");
   }

   @TestTemplate
   public void shouldNotifyOnceLostLockIfStopped() throws Exception {
      final ExecutorService executorService = Executors.newSingleThreadExecutor();
      final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      final OrderedExecutorFactory factory = new OrderedExecutorFactory(executorService);
      final ArtemisExecutor artemisExecutor = factory.getExecutor();
      final AtomicLong lostLock = new AtomicLong();
      final AtomicReference<ScheduledLeaseLock> lock = new AtomicReference<>();
      final AtomicReference<Throwable> stopErrors = new AtomicReference<>();
      final LockListener lockListener = () -> {
         lostLock.incrementAndGet();
         try {
            lock.get().stop();
         } catch (Throwable e) {
            stopErrors.set(e);
         }
      };
      final ScheduledLeaseLock scheduledLeaseLock = ScheduledLeaseLock
         .of(scheduledExecutorService, artemisExecutor, "test", lock(TimeUnit.SECONDS.toMillis(1)),
             100, lockListener);
      lock.set(scheduledLeaseLock);
      assertTrue(scheduledLeaseLock.lock().tryAcquire());
      lostLock.set(0);
      scheduledLeaseLock.start();
      assertTrue(scheduledLeaseLock.lock().isHeldByCaller());
      scheduledLeaseLock.lock().release();
      assertFalse(scheduledLeaseLock.lock().isHeldByCaller());
      Wait.assertTrue(() -> lostLock.get() > 0);
      assertFalse(scheduledLeaseLock.isStarted());
      // wait enough to see if it get triggered again
      TimeUnit.MILLISECONDS.sleep(scheduledLeaseLock.renewPeriodMillis());
      assertEquals(1, lostLock.getAndSet(0));
      assertNull(stopErrors.getAndSet(null));
      scheduledLeaseLock.stop();
      executorService.shutdown();
      scheduledExecutorService.shutdown();
   }

   @TestTemplate
   public void validateTimeDiffsOnLeaseLock() throws Exception {

      AtomicInteger diff = new AtomicInteger(0);

      JdbcLeaseLock hackLock = new JdbcLeaseLock("SomeID", jdbcSharedStateManager.getJdbcConnectionProvider(), sqlProvider.tryAcquirePrimaryLockSQL(),
                                                 sqlProvider.tryReleasePrimaryLockSQL(), sqlProvider.renewPrimaryLockSQL(),
                                                 sqlProvider.isPrimaryLockedSQL(), sqlProvider.currentTimestampSQL(),
                                                 sqlProvider.currentTimestampTimeZoneId(), -1, 1000,
                                                 "PRIMARY", 1000) {
         @Override
         protected long fetchDatabaseTime(Connection connection) throws SQLException {
            return System.currentTimeMillis() + diff.get();
         }
      };

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         diff.set(10_000);
         hackLock.dbCurrentTimeMillis();
         assertTrue(loggerHandler.findText("AMQ224118"));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         diff.set(-10_000);
         hackLock.dbCurrentTimeMillis();
         assertTrue(loggerHandler.findText("AMQ224118"));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         diff.set(0);
         hackLock.dbCurrentTimeMillis();
         assertFalse(loggerHandler.findText("AMQ224118"));
      }
   }
}
