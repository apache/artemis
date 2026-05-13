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
package org.apache.activemq.artemis.tests.soak.journal;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncoderPersister;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimedBufferMovementTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final ConcurrentHashMap<String, String> pendingCallbacks = new ConcurrentHashMap<>();

   @Test
   @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   public void testForceMoveNextFile() throws Exception {
      int REGULAR_THREADS = 5;
      int TX_THREADS = 5;

      ExecutorService executorService = Executors.newFixedThreadPool(REGULAR_THREADS + TX_THREADS + 10);
      runAfter(executorService::shutdownNow);
      OrderedExecutorFactory orderedExecutorFactory = new OrderedExecutorFactory(executorService);

      NIOSequentialFileFactory factory = new NIOSequentialFileFactory(getTestDirfile(), true, 1);
      factory.start();
      runAfter(factory::stop);

      JournalImpl journal = new JournalImpl(orderedExecutorFactory, 1024 * 1024, 10, 10, 3, 0, 50_000, factory, "coll", "data", 1, 0);
      journal.start();
      runAfter(journal::stop);
      journal.load(new LoaderCallback() {
         @Override
         public void addPreparedTransaction(PreparedTransactionInfo preparedTransaction) {

         }

         @Override
         public void addRecord(RecordInfo info) {

         }

         @Override
         public void deleteRecord(long id) {

         }

         @Override
         public void updateRecord(RecordInfo info) {

         }

         @Override
         public void failedTransaction(long transactionID, List<RecordInfo> records, List<RecordInfo> recordsToDelete) {

         }
      });
      AtomicInteger recordsWritten = new AtomicInteger(0);
      AtomicInteger recordsCallback = new AtomicInteger(0);

      int RECORDS = 500_000;
      CountDownLatch done = new CountDownLatch(REGULAR_THREADS + TX_THREADS);

      AtomicInteger errors = new AtomicInteger(0);

      AtomicInteger sequence = new AtomicInteger(1);

      CyclicBarrier startFlag = new CyclicBarrier(REGULAR_THREADS + TX_THREADS);

      for (int t = 0; t < REGULAR_THREADS; t++) {
         executorService.execute(() -> {
            OperationContext context = new OperationContextImpl(orderedExecutorFactory.getExecutor());
            try {
               try {
                  startFlag.await(10, TimeUnit.SECONDS);
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  if (e instanceof InterruptedException) {
                     Thread.interrupted();
                  }
               }
               for (int r = 0; r < RECORDS; r++) {
                  String uuid = "noTX_ " + RandomUtil.randomUUIDString();
                  pendingCallbacks.put(uuid, uuid);
                  try {
                     int add = sequence.incrementAndGet();
                     journal.appendAddRecord(add, (byte) 0, new ByteArrayEncoding(new byte[5]), true, context);
                     recordsWritten.incrementAndGet();
                     context.executeOnCompletion(new IOCallback() {
                        @Override
                        public void done() {
                           pendingCallbacks.remove(uuid);
                           recordsCallback.incrementAndGet();
                        }

                        @Override
                        public void onError(int errorCode, String errorMessage) {
                           logger.warn("Error {}", errorCode);
                           errors.incrementAndGet();
                        }
                     });
                     journal.appendDeleteRecord(add, false);
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                     errors.incrementAndGet();
                  }
               }
            } finally {
               context.executeOnCompletion(new IOCompletion() {
                  @Override
                  public void storeLineUp() {
                  }

                  @Override
                  public void done() {
                     done.countDown();
                  }

                  @Override
                  public void onError(int errorCode, String errorMessage) {
                     logger.warn("error {} / message = {}", errorCode, errorMessage);
                     errors.incrementAndGet();
                     done.countDown();
                  }
               });
            }
         });
      }

      for (int t = 0; t < TX_THREADS; t++) {
         executorService.execute(() -> {
            OperationContext context = new OperationContextImpl(orderedExecutorFactory.getExecutor());
            try {
               try {
                  startFlag.await(10, TimeUnit.SECONDS);
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  if (e instanceof InterruptedException) {
                     Thread.interrupted();
                  }
               }
               for (int r = 0; r < RECORDS; r++) {
                  String uuid = "tx_" + RandomUtil.randomUUIDString();
                  try {
                     long tx = sequence.incrementAndGet();
                     pendingCallbacks.put(uuid, String.valueOf(tx));
                     int add1 = sequence.incrementAndGet();
                     int add2 = sequence.incrementAndGet();
                     journal.appendAddRecordTransactional(tx, add1, (byte) 0, EncoderPersister.getInstance(), new ByteArrayEncoding(new byte[5]));
                     journal.appendAddRecordTransactional(tx, add2, (byte) 0, EncoderPersister.getInstance(), new ByteArrayEncoding(new byte[5]));
                     journal.appendCommitRecord(tx, true, context);
                     recordsWritten.incrementAndGet();
                     context.executeOnCompletion(new IOCallback() {
                        @Override
                        public void done() {
                           pendingCallbacks.remove(uuid);
                           recordsCallback.incrementAndGet();
                        }

                        @Override
                        public void onError(int errorCode, String errorMessage) {
                        }
                     });
                     journal.appendDeleteRecord(add1, false);
                     journal.appendDeleteRecord(add2, false);
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                     errors.incrementAndGet();
                  }
               }
            } finally {
               context.executeOnCompletion(new IOCompletion() {
                  @Override
                  public void storeLineUp() {
                  }

                  @Override
                  public void done() {
                     done.countDown();
                  }

                  @Override
                  public void onError(int errorCode, String errorMessage) {
                     logger.warn("error {} / message = {}", errorCode, errorMessage);
                     errors.incrementAndGet();
                     done.countDown();
                  }
               });
            }
         });
      }

      int countRepeat = 0;
      int missingData = 0;

      while (!done.await(10, TimeUnit.MILLISECONDS)) {
         logger.debug("forcing recordsWritten={}, pendingCallback={}, recordsCallback={}", recordsWritten.get(), pendingCallbacks.size(), recordsCallback.get());
         if (countRepeat++ < 3) { // compact a few times
            journal.scheduleCompactAndBlock(500_000);
         }
         // we will keep forcing this method (which will move to a next file)
         // to introduce possible races
         journal.forceBackup(1, TimeUnit.SECONDS);

         // If the issue was happening, this would print the IDs that are missing
         if (pendingCallbacks.size() < 10 && !pendingCallbacks.isEmpty() && done.getCount() == 0) {
            if (missingData++ > 5) {
               // lets give a chance for the test to finish, otherwise it would never finish
               break;
            }
            pendingCallbacks.forEach((a, b) -> {
               logger.info("ID {} with tx={} still in the list", a, b);
            });
         }
      }

      assertTrue(done.await(1, TimeUnit.MINUTES));

      // the pendingCallbacks list is removed on the IO completion of each thread.
      // No need to use Wait on this place.
      assertEquals(0, pendingCallbacks.size());

      journal.stop();
      factory.stop();

      assertEquals(0, errors.get());

      logger.debug("Done!, callback={}, written={}", recordsCallback.get(), recordsWritten.get());
   }

}