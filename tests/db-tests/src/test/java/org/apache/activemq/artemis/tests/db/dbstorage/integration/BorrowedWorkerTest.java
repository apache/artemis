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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.artemis.database.worker.BorrowedWorker;
import org.apache.artemis.database.worker.DataManager;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class BorrowedWorkerTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @TestTemplate
   public void testBorrowAndExplicitReturn() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         CountDownLatch borrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> borrowedRef = new AtomicReference<>();

         Executor targetExecutor = executorFactory.getExecutor();

         dataManager.borrowWorker(targetExecutor, 5000, null, () -> { }, borrowed -> {
            borrowedRef.set(borrowed);
            borrowedLatch.countDown();
         });

         assertTrue(borrowedLatch.await(5, TimeUnit.SECONDS), "Should have received a borrowed worker");

         BorrowedWorker borrowed = borrowedRef.get();
         assertNotNull(borrowed);
         assertNotNull(borrowed.getWorker());
         assertFalse(borrowed.isReturned());

         // Explicitly return the worker
         borrowed.returnWorker();
         assertTrue(borrowed.isReturned());

         // Idempotent: returning again should be a no-op
         borrowed.returnWorker();
         assertTrue(borrowed.isReturned());
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testLeaseRenewal() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         CountDownLatch borrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> borrowedRef = new AtomicReference<>();

         Executor targetExecutor = executorFactory.getExecutor();

         // Short lease: 500ms
         dataManager.borrowWorker(targetExecutor, 500, null, () -> { }, borrowed -> {
            borrowedRef.set(borrowed);
            borrowedLatch.countDown();
         });

         assertTrue(borrowedLatch.await(5, TimeUnit.SECONDS));

         BorrowedWorker borrowed = borrowedRef.get();

         // Renew the lease before it expires (at ~300ms)
         Thread.sleep(300);
         assertFalse(borrowed.isReturned(), "Should not have expired yet");
         borrowed.renewLease();

         // Sleep another 300ms — total 600ms from start, but only 300ms since renewal
         Thread.sleep(300);
         assertFalse(borrowed.isReturned(), "Should not have expired after renewal");

         // Now let the lease expire: wait for longer than 500ms from last renewal
         Wait.assertTrue(borrowed::isReturned, 2000, 50);
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testLeaseExpiry() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         CountDownLatch borrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> borrowedRef = new AtomicReference<>();
         AtomicBoolean cleanupCalled = new AtomicBoolean(false);

         Executor targetExecutor = executorFactory.getExecutor();

         // Short lease: 200ms
         dataManager.borrowWorker(targetExecutor, 200, () -> cleanupCalled.set(true), () -> { }, borrowed -> {
            borrowedRef.set(borrowed);
            borrowedLatch.countDown();
         });

         assertTrue(borrowedLatch.await(5, TimeUnit.SECONDS));

         BorrowedWorker borrowed = borrowedRef.get();
         assertFalse(borrowed.isReturned());

         // Wait for lease expiry and cleanup (cleanup runs asynchronously after returned flag is set)
         Wait.assertTrue(borrowed::isReturned, 5000, 50);
         Wait.assertTrue(cleanupCalled::get, 5000, 50);
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testCleanupCalledOnExplicitReturn() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         CountDownLatch borrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> borrowedRef = new AtomicReference<>();
         AtomicBoolean cleanupCalled = new AtomicBoolean(false);

         Executor targetExecutor = executorFactory.getExecutor();

         dataManager.borrowWorker(targetExecutor, 30000, () -> cleanupCalled.set(true), () -> {
         }, borrowed -> {
            borrowedRef.set(borrowed);
            borrowedLatch.countDown();
         });

         assertTrue(borrowedLatch.await(5, TimeUnit.SECONDS));

         BorrowedWorker borrowed = borrowedRef.get();

         // Return explicitly — cleanup should run
         borrowed.returnWorker();
         assertTrue(borrowed.isReturned());
         assertTrue(cleanupCalled.get(), "Cleanup action should have been called on explicit return");
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testDemandConnectionBack() throws Exception {
      // Use a configuration with only 1 connection so we can easily exhaust the pool
      storageConfiguration.setDatabaseConnections(1);

      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         CountDownLatch borrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> borrowedRef = new AtomicReference<>();

         Executor targetExecutor = executorFactory.getExecutor();

         String description = "MessageDispatch queue = testQueue, address = testAddress";

         // Borrow the only worker with a long lease
         dataManager.borrowWorker(targetExecutor, 30000, null, () -> { }, borrowed -> {
            borrowedRef.set(borrowed);
            borrowedLatch.countDown();
         }, description);

         assertTrue(borrowedLatch.await(5, TimeUnit.SECONDS));

         BorrowedWorker borrowed = borrowedRef.get();
         assertFalse(borrowed.needConnectionBack, "Flag should not be set initially");
         assertEquals(description, borrowed.toString());

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
            // Now try to execute a query — this should exhaust the pool and demand the borrowed one back
            CountDownLatch queryDone = new CountDownLatch(1);
            Executor otherExecutor = executorFactory.getExecutor();
            dataManager.executeQuery(otherExecutor, w -> {
               // just a simple query to trigger pool exhaustion
            }, queryDone::countDown);

            // The demand should have set needConnectionBack
            Wait.assertTrue(() -> borrowed.needConnectionBack, 2000, 50);

            assertTrue(loggerHandler.findText("AMQ232002"));
            assertTrue(loggerHandler.findText("testQueue"));
            assertTrue(loggerHandler.findText("testAddress"));

            // Simulate the borrower cooperating: return the worker
            borrowed.returnWorker();
            assertTrue(borrowed.isReturned());

            // The queued query should eventually complete
            assertTrue(queryDone.await(10, TimeUnit.SECONDS), "Queued query should complete after worker returned");
         }
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testBorrowAfterPoolExhausted() throws Exception {
      // Use 1 connection to test queuing
      storageConfiguration.setDatabaseConnections(1);

      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         // First borrow takes the only worker
         CountDownLatch firstBorrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> firstBorrowed = new AtomicReference<>();

         Executor targetExecutor = executorFactory.getExecutor();

         dataManager.borrowWorker(targetExecutor, 30000, null, () -> { }, borrowed -> {
            firstBorrowed.set(borrowed);
            firstBorrowedLatch.countDown();
         });

         assertTrue(firstBorrowedLatch.await(5, TimeUnit.SECONDS));
         assertNotNull(firstBorrowed.get());

         // Second borrow should be queued
         CountDownLatch secondBorrowedLatch = new CountDownLatch(1);
         AtomicReference<BorrowedWorker> secondBorrowed = new AtomicReference<>();

         dataManager.borrowWorker(targetExecutor, 5000, null, () -> { }, borrowed -> {
            secondBorrowed.set(borrowed);
            secondBorrowedLatch.countDown();
         });

         // Should not have received the second one yet
         assertFalse(secondBorrowedLatch.await(200, TimeUnit.MILLISECONDS), "Second borrow should be queued");

         // Return the first
         firstBorrowed.get().returnWorker();

         // Now the second should be dispatched
         assertTrue(secondBorrowedLatch.await(10, TimeUnit.SECONDS), "Second borrow should complete after first returned");
         assertNotNull(secondBorrowed.get());

         // Clean up
         secondBorrowed.get().returnWorker();
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testDemandBackLogsTable() throws Exception {
      storageConfiguration.setDatabaseConnections(2);

      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration,
                                                                                 criticalAnalyzer,
                                                                                 executorFactory,
                                                                                 executorFactory,
                                                                                 scheduledExecutorService,
                                                                                 executorService,
                                                                                 null);
      databaseStorageManager.start();
      try {
         DataManager dataManager = databaseStorageManager.getDataManager();

         CountDownLatch borrowedLatch = new CountDownLatch(2);
         AtomicReference<BorrowedWorker> borrowed1Ref = new AtomicReference<>();
         AtomicReference<BorrowedWorker> borrowed2Ref = new AtomicReference<>();

         Executor executor1 = executorFactory.getExecutor();
         Executor executor2 = executorFactory.getExecutor();

         String desc1 = "MessageDispatch queue = orders, address = orders";
         String desc2 = "MessageDispatch queue = payments.sub1, address = payments";

         dataManager.borrowWorker(executor1, 30000, null, () -> { }, borrowed -> {
            borrowed1Ref.set(borrowed);
            borrowedLatch.countDown();
         }, desc1);

         dataManager.borrowWorker(executor2, 30000, null, () -> { }, borrowed -> {
            borrowed2Ref.set(borrowed);
            borrowedLatch.countDown();
         }, desc2);

         assertTrue(borrowedLatch.await(5, TimeUnit.SECONDS), "Should have received both borrowed workers");

         BorrowedWorker b1 = borrowed1Ref.get();
         BorrowedWorker b2 = borrowed2Ref.get();
         assertEquals(desc1, b1.toString());
         assertEquals(desc2, b2.toString());

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
            // Trigger demand-back by queuing a query when the pool is exhausted
            CountDownLatch queryDone = new CountDownLatch(1);
            Executor otherExecutor = executorFactory.getExecutor();
            dataManager.executeQuery(otherExecutor, w -> { }, queryDone::countDown);

            Wait.assertTrue(() -> b1.needConnectionBack && b2.needConnectionBack, 2000, 50);

            assertTrue(loggerHandler.findText("AMQ232002"));
            assertTrue(loggerHandler.findText("orders"));
            assertTrue(loggerHandler.findText("payments"));

            b1.returnWorker();
            b2.returnWorker();

            assertTrue(queryDone.await(10, TimeUnit.SECONDS));
         }
      } finally {
         databaseStorageManager.stop();
      }
   }
}
