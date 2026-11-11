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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.junit.jupiter.api.Test;

public class OperationContextUnitTest extends ServerTestBase {

   @Test
   public void testCompleteTaskAfterPaging() throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      try {
         OperationContextImpl impl = new OperationContextImpl(executor);
         final CountDownLatch latch1 = new CountDownLatch(1);
         final CountDownLatch latch2 = new CountDownLatch(1);

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch1.countDown();
            }
         });

         assertTrue(latch1.await(10, TimeUnit.SECONDS));

         for (int i = 0; i < 10; i++) {
            impl.storeLineUp();
         }
         for (int i = 0; i < 3; i++) {
            impl.pageSyncLineUp();
         }

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch2.countDown();
            }
         });

         assertFalse(latch2.await(1, TimeUnit.MILLISECONDS));

         for (int i = 0; i < 9; i++) {
            impl.done();
         }
         for (int i = 0; i < 2; i++) {
            impl.pageSyncDone();
         }

         assertFalse(latch2.await(1, TimeUnit.MILLISECONDS));

         impl.done();
         impl.pageSyncDone();

         assertTrue(latch2.await(10, TimeUnit.SECONDS));

      } finally {
         executor.shutdown();
      }
   }


   @Test
   public void testCompleteTaskStoreOnly() throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      try {
         OperationContextImpl impl = new OperationContextImpl(executor);
         final CountDownLatch latch1 = new CountDownLatch(1);
         final CountDownLatch latch2 = new CountDownLatch(1);
         final CountDownLatch latch3 = new CountDownLatch(1);

         impl.storeLineUp();

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch1.countDown();
            }
         }, OperationConsistencyLevel.STORAGE);

         impl.storeLineUp();

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch3.countDown();
            }
         }, OperationConsistencyLevel.STORAGE);

         impl.done();

         assertTrue(latch1.await(10, TimeUnit.SECONDS));
         assertFalse(latch3.await(1, TimeUnit.MILLISECONDS));

         impl.done();
         assertTrue(latch3.await(10, TimeUnit.SECONDS));

         for (int i = 0; i < 10; i++) {
            impl.storeLineUp();
         }
         for (int i = 0; i < 3; i++) {
            impl.pageSyncLineUp();
         }

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch2.countDown();
            }
         }, OperationConsistencyLevel.STORAGE);

         assertFalse(latch2.await(1, TimeUnit.MILLISECONDS));

         for (int i = 0; i < 9; i++) {
            impl.done();
         }

         assertFalse(latch2.await(1, TimeUnit.MILLISECONDS));

         impl.done();

         assertTrue(latch2.await(10, TimeUnit.SECONDS));

      } finally {
         executor.shutdown();
      }
   }


   @Test
   public void testCompletionLateStoreOnly() throws Exception {
      testCompletionLate(OperationConsistencyLevel.STORAGE);
   }

   @Test
   public void testCompletionLate() throws Exception {
      testCompletionLate(OperationConsistencyLevel.FULL);
   }

   private void testCompletionLate(OperationConsistencyLevel consistencyLevel) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      try {
         OperationContextImpl impl = new OperationContextImpl(executor);
         final CountDownLatch latch1 = new CountDownLatch(1);
         final CountDownLatch latch2 = new CountDownLatch(1);

         if (consistencyLevel == OperationConsistencyLevel.STORAGE) {
            // if storeOnly, then the pageSyncLinup and replication lineup should not bother the results
            impl.pageSyncLineUp();
            impl.replicationLineUp();
         }
         impl.storeLineUp();

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch1.countDown();
            }
         }, consistencyLevel);

         impl.storeLineUpField = 350000;
         impl.stored = impl.storeLineUpField - 1;

         if (impl.tasks != null) {
            impl.tasks.forEach((t) -> t.storeLined = 150000L);
         }

         if (impl.storeOnlyTasks != null) {
            impl.storeOnlyTasks.forEach((t) -> t.storeLined = 150000L);
         }

         impl.executeOnCompletion(new IOCallback() {

            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               latch2.countDown();
            }
         }, consistencyLevel);

         impl.done();

         assertTrue(latch1.await(10, TimeUnit.SECONDS));
         assertTrue(latch2.await(10, TimeUnit.SECONDS));

         if (impl.storeOnlyTasks != null) {
            assertEquals(0, impl.storeOnlyTasks.size());
         }

         if (impl.tasks != null) {
            assertEquals(0, impl.tasks.size());
         }

      } finally {
         executor.shutdown();
      }
   }

   @Test
   public void testSequentialCompletionN() throws Exception {
      ExecutorService executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      Queue<Long> completions = new ConcurrentLinkedQueue();
      final int N = 500;
      try {
         final OperationContextImpl impl = new OperationContextImpl(new OrderedExecutor(executor));

         // pending work to queue completions till done
         impl.storeLineUp();

         for (long l = 0; l < N; l++) {
            long finalL = l;
            impl.executeOnCompletion(new IOCallback() {
               @Override
               public void onError(int errorCode, String errorMessage) {
               }

               @Override
               public void done() {
                  completions.add(finalL);
               }
            });
         }

         impl.done();

         Wait.assertEquals(N, ()-> completions.size());

         for (long i = 0; i < N; i++) {
            assertEquals(i, (long) completions.poll(), "ordered");
         }

      } finally {
         executor.shutdownNow();
      }
   }

   @Test
   public void testIgnoreReplication() throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      runAfter(executor::shutdownNow);
      Queue<Long> ignoreReplicationCompletions = new ConcurrentLinkedQueue();
      Queue<Long> regularCompletion = new ConcurrentLinkedQueue();
      final int N = 500;
      final OperationContextImpl impl = new OperationContextImpl(new OrderedExecutor(executor));

      // pending work to queue completions till done
      impl.storeLineUp();
      impl.replicationLineUp();

      for (long l = 0; l < N; l++) {
         long finalL = l;
         impl.executeOnCompletion(new IOCallback() {
            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               ignoreReplicationCompletions.add(finalL);
            }
         }, OperationConsistencyLevel.IGNORE_REPLICATION);
         impl.executeOnCompletion(new IOCallback() {
            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               regularCompletion.add(finalL);
            }
         }, OperationConsistencyLevel.FULL);
      }

      flushExecutor(executor);
      assertEquals(0, ignoreReplicationCompletions.size());
      assertEquals(0, regularCompletion.size());
      impl.done();

      flushExecutor(executor);
      assertEquals(N, ignoreReplicationCompletions.size());
      assertEquals(0, regularCompletion.size());

      impl.replicationDone();
      flushExecutor(executor);

      assertEquals(N, regularCompletion.size());

      for (long i = 0; i < N; i++) {
         assertEquals(i, (long) ignoreReplicationCompletions.poll(), "ordered");
         assertEquals(i, (long) regularCompletion.poll(), "ordered");
      }
   }

   private void flushExecutor(Executor executor) throws Exception {
      CountDownLatch latch = new CountDownLatch(1);
      executor.execute(latch::countDown);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
   }

   @Test
   public void testWaitOnReplication() throws Exception {
      ExecutorService executor = Executors.newSingleThreadScheduledExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      runAfter(executor::shutdownNow);

      Queue<Long> completions = new ConcurrentLinkedQueue();

      final int N = 500;
      final OperationContextImpl impl = new OperationContextImpl(new OrderedExecutor(executor));

      // pending work to queue completions till done
      impl.storeLineUp();
      impl.replicationLineUp();

      for (long l = 0; l < N; l++) {
         long finalL = l;
         impl.executeOnCompletion(new IOCallback() {
            @Override
            public void onError(int errorCode, String errorMessage) {
            }

            @Override
            public void done() {
               completions.add(finalL);
            }
         });
      }

      impl.done();

      flushExecutor(executor);
      assertEquals(0, completions.size());

      impl.replicationDone();
      flushExecutor(executor);

      Wait.assertEquals(N, ()-> completions.size(), 5000, 100);

      for (long i = 0; i < N; i++) {
         assertEquals(i, (long) completions.poll(), "ordered");
      }

   }

   @Test
   public void testErrorNotLostOnPageSyncError() throws Exception {

      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      ExecutorService pageSyncTimer = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      class PageWriteErrorJob implements Runnable {
         final OperationContextImpl operationContext;
         PageWriteErrorJob(OperationContextImpl impl) {
            impl.pageSyncLineUp();
            operationContext = impl;
         }

         @Override
         public void run() {
            try {
               operationContext.onError(10, "bla");
            } finally {
               operationContext.pageSyncDone();
            }
         }
      }

      try {
         final int numJobs = 1000;
         final CountDownLatch errorsOnLateRegister = new CountDownLatch(numJobs);

         for (int i = 0; i < numJobs; i++) {
            final OperationContextImpl impl = new OperationContextImpl(executor);

            final CountDownLatch done = new CountDownLatch(1);

            pageSyncTimer.execute(new PageWriteErrorJob(impl));
            impl.executeOnCompletion(new IOCallback() {

               @Override
               public void onError(int errorCode, String errorMessage) {
                  errorsOnLateRegister.countDown();
                  done.countDown();
               }

               @Override
               public void done() {
                  done.countDown();
               }
            });

            done.await();
         }
         assertTrue(errorsOnLateRegister.await(10, TimeUnit.SECONDS));

      } finally {
         executor.shutdown();
         pageSyncTimer.shutdown();
      }
   }

   @Test
   public void testCaptureExceptionOnExecutor() throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      executor.shutdown();

      final CountDownLatch latch = new CountDownLatch(1);

      final OperationContextImpl impl = new OperationContextImpl(executor) {
         @Override
         public void complete() {
            super.complete();
            latch.countDown();
         }

      };

      impl.storeLineUp();

      final AtomicInteger numberOfFailures = new AtomicInteger(0);

      Thread t = new Thread(() -> {
         try {
            impl.waitCompletion(5000);
         } catch (Throwable e) {
            e.printStackTrace();
            numberOfFailures.incrementAndGet();
         }
      });

      t.start();

      // Need to wait complete to be called first or the test would be invalid.
      // We use a latch instead of forcing a sleep here
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      impl.done();

      t.join();

      assertEquals(1, numberOfFailures.get());
   }

   @Test
   public void testCaptureExceptionOnFailure() throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      final CountDownLatch latch = new CountDownLatch(1);

      final OperationContextImpl context = new OperationContextImpl(executor) {
         @Override
         public void complete() {
            super.complete();
            latch.countDown();
         }

      };

      context.storeLineUp();

      final AtomicInteger failures = new AtomicInteger(0);

      Thread t = new Thread(() -> {
         try {
            context.waitCompletion(5000);
         } catch (Throwable e) {
            e.printStackTrace();
            failures.incrementAndGet();
         }
      });

      t.start();

      // Need to wait complete to be called first or the test would be invalid.
      // We use a latch instead of forcing a sleep here
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      context.onError(ActiveMQExceptionType.UNSUPPORTED_PACKET.getCode(), "Poop happens!");

      t.join();

      assertEquals(1, failures.get());

      failures.set(0);

      final AtomicInteger operations = new AtomicInteger(0);

      // We should be up to date with lineUps and executions. this should now just finish processing
      context.executeOnCompletion(new IOCallback() {

         @Override
         public void done() {
            operations.incrementAndGet();
         }

         @Override
         public void onError(final int errorCode, final String errorMessage) {
            failures.incrementAndGet();
         }

      });

      assertEquals(1, failures.get());
      assertEquals(0, operations.get());
   }

   @Test
   public void testContextWithoutDebugTrackers() {
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      runAfter(executor::shutdownNow);

      assertEquals(0, OperationContextImpl.getMaxDebugTrackers());
      OperationContextImpl context = new OperationContextImpl(executor);
      assertNull(context.getDebugTrackers());

      context.storeLineUp();
      assertNull(context.getDebugTrackers());

      context.done();
      assertNull(context.getDebugTrackers());
   }

   @Test
   public void testContextWithDebugTrackers() {
      int maxStoreOperationTrackers = OperationContextImpl.getMaxDebugTrackers();
      ExecutorService executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      runAfter(executor::shutdownNow);

      OperationContextImpl.setMaxDebugTrackers(1);
      try {
         assertEquals(1, OperationContextImpl.getMaxDebugTrackers());
         OperationContextImpl context = new OperationContextImpl(executor);
         assertNotNull(context.getDebugTrackers());

         context.storeLineUp();
         assertEquals(1, context.getDebugTrackers().size());
         assertEquals("storeLineUp", ((Exception)context.getDebugTrackers().get()).getStackTrace()[0].getMethodName());
         assertEquals("testContextWithDebugTrackers", ((Exception)context.getDebugTrackers().get()).getStackTrace()[1].getMethodName());

         context.done();
         assertEquals(1, context.getDebugTrackers().size());
         assertEquals("done", ((Exception)context.getDebugTrackers().get()).getStackTrace()[0].getMethodName());
         assertEquals("testContextWithDebugTrackers", ((Exception)context.getDebugTrackers().get()).getStackTrace()[1].getMethodName());
      } finally {
         OperationContextImpl.setMaxDebugTrackers(maxStoreOperationTrackers);
      }
   }
}
