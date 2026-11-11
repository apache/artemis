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
package org.apache.activemq.artemis.tests.stress.journal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JournalCleanupCompactStressTest extends ActiveMQTestBase {

   public static SimpleIDGenerator idGen = new SimpleIDGenerator(1);

   private static final int MAX_WRITES = 20000;

   private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

   // We want to maximize the difference between appends and deles, or we could get out of memory
   public Semaphore maxRecords;

   private volatile boolean running;

   private final AtomicInteger errors = new AtomicInteger(0);

   private final AtomicInteger numberOfRecords = new AtomicInteger(0);

   private final AtomicInteger numberOfUpdates = new AtomicInteger(0);

   private final AtomicInteger numberOfDeletes = new AtomicInteger(0);

   private JournalImpl journal;

   ThreadFactory tFactory = new ActiveMQThreadFactory("SoakTest" + System.identityHashCode(this), false, JournalCleanupCompactStressTest.class.getClassLoader());

   private ExecutorService threadPool;

   private OrderedExecutorFactory executorFactory = new OrderedExecutorFactory(threadPool);

   Executor testExecutor;

   protected long getTotalTimeMilliseconds() {
      return TimeUnit.MINUTES.toMillis(2);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      threadPool = Executors.newFixedThreadPool(20, tFactory);
      executorFactory = new OrderedExecutorFactory(threadPool);
      testExecutor = executorFactory.getExecutor();

      maxRecords = new Semaphore(MAX_WRITES);

      errors.set(0);

      File dir = new File(getTemporaryDir());
      dir.mkdirs();

      SequentialFileFactory factory;

      int maxAIO;
      if (LibaioContext.isLoaded()) {
         factory = new AIOSequentialFileFactory(dir, 10);
         maxAIO = ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio();
      } else {
         factory = new NIOSequentialFileFactory(dir, true, 1);
         maxAIO = ActiveMQDefaultConfiguration.getDefaultJournalMaxIoNio();
      }

      journal = new JournalImpl(50 * 1024, 20, 20, 50, ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage(), factory, "activemq-data", "amq", maxAIO) {
         @Override
         protected void onCompactLockingTheJournal() throws Exception {
         }

         @Override
         protected void onCompactStart() throws Exception {
            testExecutor.execute(() -> {
               try {
                  // System.out.println("OnCompactStart enter");
                  if (running) {
                     long id = idGen.generateID();
                     journal.appendAddRecord(id, (byte) 0, new byte[]{1, 2, 3}, false);
                     journal.forceMoveNextFile();
                     journal.appendDeleteRecord(id, id == 20);
                  }
                  // System.out.println("OnCompactStart leave");
               } catch (Exception e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            });

         }

      };

      journal.start();
      journal.loadInternalOnly();

   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         if (journal.isStarted()) {
            journal.stop();
         }
      } catch (Exception e) {
         // don't care :-)
      }

      threadPool.shutdown();
   }

   @Test
   public void testAppend() throws Exception {

      running = true;
      SlowAppenderNoTX t1 = new SlowAppenderNoTX();

      int NTHREADS = 5;

      FastAppenderTx[] appenders = new FastAppenderTx[NTHREADS];
      FastUpdateTx[] updaters = new FastUpdateTx[NTHREADS];

      for (int i = 0; i < NTHREADS; i++) {
         appenders[i] = new FastAppenderTx();
         updaters[i] = new FastUpdateTx(appenders[i].queue);
      }

      t1.start();

      Thread.sleep(1000);

      for (int i = 0; i < NTHREADS; i++) {
         appenders[i].start();
         updaters[i].start();
      }

      long timeToEnd = System.currentTimeMillis() + getTotalTimeMilliseconds();

      while (System.currentTimeMillis() < timeToEnd) {
         System.out.println("Append = " + numberOfRecords +
                               ", Update = " +
                               numberOfUpdates +
                               ", Delete = " +
                               numberOfDeletes +
                               ", liveRecords = " +
                               (numberOfRecords.get() - numberOfDeletes.get()));
         Thread.sleep(TimeUnit.SECONDS.toMillis(10));
         rwLock.writeLock().lock();
         System.out.println("Restarting server");
         journal.stop();
         journal.start();
         reloadJournal();
         rwLock.writeLock().unlock();
      }

      running = false;

      // Release Semaphore after setting running to false or the threads may never finish
      maxRecords.release(MAX_WRITES - maxRecords.availablePermits());

      for (Thread t : appenders) {
         t.join();
      }

      for (Thread t : updaters) {
         t.join();
      }

      t1.join();

      final CountDownLatch latchExecutorDone = new CountDownLatch(1);
      testExecutor.execute(latchExecutorDone::countDown);

      ActiveMQTestBase.waitForLatch(latchExecutorDone);

      journal.stop();

      journal.start();

      reloadJournal();

      System.out.println("Deleting everything!");

      journal.getRecords().forEach((id, record) -> {
         try {
            journal.appendDeleteRecord(id, false);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      });

      journal.forceMoveNextFile();

      journal.checkReclaimStatus();

      Thread.sleep(5000);

      assertEquals(0, journal.getDataFilesCount());

      journal.stop();
   }

   private void reloadJournal() throws Exception {
      assertEquals(0, errors.get());

      List<RecordInfo> committedRecords = new ArrayList<>();
      List<PreparedTransactionInfo> preparedTransactions = new ArrayList<>();
      journal.load(committedRecords, preparedTransactions, (transactionID, records, recordsToDelete) -> { });

      long appends = 0;

      for (RecordInfo record : committedRecords) {
         if (record.isUpdate) {
         } else {
            appends++;
         }
      }

      assertEquals(numberOfRecords.get() - numberOfDeletes.get(), appends);
   }

   private byte[] generateRecord() {
      int size = RandomUtil.randomPositiveInt() % 10000;
      if (size == 0) {
         size = 10000;
      }
      return RandomUtil.randomBytes(size);
   }

   class FastAppenderTx extends Thread {

      LinkedBlockingDeque<Long> queue = new LinkedBlockingDeque<>();

      OperationContextImpl ctx = new OperationContextImpl(executorFactory.getExecutor());

      FastAppenderTx() {
         super("FastAppenderTX");
      }

      @Override
      public void run() {
         rwLock.readLock().lock();

         try {
            while (running) {
               final int txSize = RandomUtil.randomMax(100);

               long txID = JournalCleanupCompactStressTest.idGen.generateID();

               long rollbackTXID = JournalCleanupCompactStressTest.idGen.generateID();

               final long[] ids = new long[txSize];

               for (int i = 0; i < txSize; i++) {
                  ids[i] = JournalCleanupCompactStressTest.idGen.generateID();
               }

               journal.appendAddRecordTransactional(rollbackTXID, ids[0], (byte) 0, generateRecord());
               journal.appendRollbackRecord(rollbackTXID, true);

               for (int i = 0; i < txSize; i++) {
                  long id = ids[i];
                  journal.appendAddRecordTransactional(txID, id, (byte) 0, generateRecord());
                  maxRecords.acquire();
               }
               journal.appendCommitRecord(txID, true, ctx);

               ctx.executeOnCompletion(new IOCallback() {

                  @Override
                  public void onError(final int errorCode, final String errorMessage) {
                  }

                  @Override
                  public void done() {
                     numberOfRecords.addAndGet(txSize);
                     for (Long id : ids) {
                        queue.add(id);
                     }
                  }
               });

               rwLock.readLock().unlock();

               Thread.yield();

               rwLock.readLock().lock();

            }
         } catch (Exception e) {
            e.printStackTrace();
            running = false;
            errors.incrementAndGet();
         } finally {
            rwLock.readLock().unlock();
         }
      }
   }

   class FastUpdateTx extends Thread {

      final LinkedBlockingDeque<Long> queue;

      OperationContextImpl ctx = new OperationContextImpl(executorFactory.getExecutor());

      FastUpdateTx(final LinkedBlockingDeque<Long> queue) {
         super("FastUpdateTX");
         this.queue = queue;
      }

      @Override
      public void run() {

         rwLock.readLock().lock();

         try {
            int txSize = RandomUtil.randomMax(100);
            int txCount = 0;
            long[] ids = new long[txSize];

            long txID = JournalCleanupCompactStressTest.idGen.generateID();

            while (running) {

               Long id = queue.poll(10, TimeUnit.SECONDS);
               if (id != null) {
                  ids[txCount++] = id;
                  journal.appendUpdateRecordTransactional(txID, id, (byte) 0, generateRecord());
               }
               if (txCount == txSize || id == null) {
                  if (txCount > 0) {
                     journal.appendCommitRecord(txID, true, ctx);
                     ctx.executeOnCompletion(new DeleteTask(ids));
                  }

                  rwLock.readLock().unlock();

                  Thread.yield();

                  rwLock.readLock().lock();

                  txCount = 0;
                  txSize = RandomUtil.randomMax(100);
                  txID = JournalCleanupCompactStressTest.idGen.generateID();
                  ids = new long[txSize];
               }
            }

            if (txCount > 0) {
               journal.appendCommitRecord(txID, true);
            }
         } catch (Exception e) {
            e.printStackTrace();
            running = false;
            errors.incrementAndGet();
         } finally {
            rwLock.readLock().unlock();
         }
      }
   }

   class DeleteTask implements IOCallback {

      final long[] ids;

      DeleteTask(final long[] ids) {
         this.ids = ids;
      }

      @Override
      public void done() {
         rwLock.readLock().lock();
         numberOfUpdates.addAndGet(ids.length);
         try {
            for (long id : ids) {
               if (id != 0) {
                  journal.appendDeleteRecord(id, false);
                  maxRecords.release();
                  numberOfDeletes.incrementAndGet();
               }
            }
         } catch (Exception e) {
            System.err.println("Can't delete id");
            e.printStackTrace();
            running = false;
            errors.incrementAndGet();
         } finally {
            rwLock.readLock().unlock();
         }
      }

      @Override
      public void onError(final int errorCode, final String errorMessage) {
      }

   }

   /**
    * Adds stuff to the journal, but it will take a long time to remove them. This will cause cleanup and compacting to
    * happen more often
    */
   class SlowAppenderNoTX extends Thread {

      SlowAppenderNoTX() {
         super("SlowAppender");
      }

      @Override
      public void run() {
         rwLock.readLock().lock();
         try {
            while (running) {
               long[] ids = new long[5];
               // Append
               for (int i = 0; running && i < ids.length; i++) {
                  System.out.println("append slow");
                  ids[i] = JournalCleanupCompactStressTest.idGen.generateID();
                  maxRecords.acquire();
                  journal.appendAddRecord(ids[i], (byte) 1, generateRecord(), true);
                  numberOfRecords.incrementAndGet();

                  rwLock.readLock().unlock();

                  Thread.sleep(TimeUnit.SECONDS.toMillis(50));

                  rwLock.readLock().lock();
               }
               // Delete
               for (int i = 0; running && i < ids.length; i++) {
                  System.out.println("Deleting");
                  maxRecords.release();
                  journal.appendDeleteRecord(ids[i], false);
                  numberOfDeletes.incrementAndGet();
               }
            }
         } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
         } finally {
            rwLock.readLock().unlock();
         }
      }
   }



}
