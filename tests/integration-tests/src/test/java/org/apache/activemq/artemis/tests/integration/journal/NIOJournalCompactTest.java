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
package org.apache.activemq.artemis.tests.integration.journal;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncoderPersister;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.AbstractJournalUpdateTask;
import org.apache.activemq.artemis.core.journal.impl.JournalCompactor;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalFileImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.JournalImplTestBase;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.SimpleEncoding;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class NIOJournalCompactTest extends JournalImplTestBase {

   private static final Logger logger = Logger.getLogger(NIOJournalCompactTest.class);

   private static final int NUMBER_OF_RECORDS = 100;

   IDGenerator idGenerator = new SimpleIDGenerator(100000);

   // General tests
   // =============

   @Test
   public void testControlFile() throws Exception {
      ArrayList<JournalFile> dataFiles = new ArrayList<>();

      for (int i = 0; i < 5; i++) {
         SequentialFile file = fileFactory.createSequentialFile("file-" + i + ".tst");
         dataFiles.add(new JournalFileImpl(file, 0, JournalImpl.FORMAT_VERSION));
      }

      ArrayList<JournalFile> newFiles = new ArrayList<>();

      for (int i = 0; i < 3; i++) {
         SequentialFile file = fileFactory.createSequentialFile("file-" + i + ".tst.new");
         newFiles.add(new JournalFileImpl(file, 0, JournalImpl.FORMAT_VERSION));
      }

      ArrayList<Pair<String, String>> renames = new ArrayList<>();
      renames.add(new Pair<>("a", "b"));
      renames.add(new Pair<>("c", "d"));

      AbstractJournalUpdateTask.writeControlFile(fileFactory, dataFiles, newFiles, renames);

      ArrayList<String> strDataFiles = new ArrayList<>();

      ArrayList<String> strNewFiles = new ArrayList<>();

      ArrayList<Pair<String, String>> renamesRead = new ArrayList<>();

      Assert.assertNotNull(JournalCompactor.readControlFile(fileFactory, strDataFiles, strNewFiles, renamesRead));

      Assert.assertEquals(dataFiles.size(), strDataFiles.size());
      Assert.assertEquals(newFiles.size(), strNewFiles.size());
      Assert.assertEquals(renames.size(), renamesRead.size());

      Iterator<String> iterDataFiles = strDataFiles.iterator();
      for (JournalFile file : dataFiles) {
         Assert.assertEquals(file.getFile().getFileName(), iterDataFiles.next());
      }
      Assert.assertFalse(iterDataFiles.hasNext());

      Iterator<String> iterNewFiles = strNewFiles.iterator();
      for (JournalFile file : newFiles) {
         Assert.assertEquals(file.getFile().getFileName(), iterNewFiles.next());
      }
      Assert.assertFalse(iterNewFiles.hasNext());

      Iterator<Pair<String, String>> iterRename = renames.iterator();
      for (Pair<String, String> rename : renamesRead) {
         Pair<String, String> original = iterRename.next();
         Assert.assertEquals(original.getA(), rename.getA());
         Assert.assertEquals(original.getB(), rename.getB());
      }
      Assert.assertFalse(iterNewFiles.hasNext());

   }

   @Test
   public void testCrashRenamingFiles() throws Exception {
      internalCompactTest(false, false, true, false, false, false, false, false, false, false, true, false, false);
   }

   @Test
   public void testCrashDuringCompacting() throws Exception {
      internalCompactTest(false, false, true, false, false, false, false, false, false, false, false, false, false);
   }

   @Test
   public void testCompactwithPendingXACommit() throws Exception {
      internalCompactTest(true, false, false, false, false, false, false, true, false, false, true, true, true);
   }

   @Test
   public void testCompactwithPendingXAPrepareAndCommit() throws Exception {
      internalCompactTest(false, true, false, false, false, false, false, true, false, false, true, true, true);
   }

   @Test
   public void testCompactwithPendingXAPrepareAndDelayedCommit() throws Exception {
      internalCompactTest(false, true, false, false, false, false, false, true, false, true, true, true, true);
   }

   @Test
   public void testCompactwithPendingCommit() throws Exception {
      internalCompactTest(true, false, false, false, false, false, false, true, false, false, true, true, true);
   }

   @Test
   public void testCompactwithDelayedCommit() throws Exception {
      internalCompactTest(false, true, false, false, false, false, false, true, false, true, true, true, true);
   }

   @Test
   public void testCompactwithPendingCommitFollowedByDelete() throws Exception {
      internalCompactTest(false, false, false, false, false, false, false, true, true, false, true, true, true);
   }

   @Test
   public void testCompactwithConcurrentUpdateAndDeletes() throws Exception {
      internalCompactTest(false, false, true, false, true, true, false, false, false, false, true, true, true);
      tearDown();
      setUp();
      internalCompactTest(false, false, true, false, true, false, true, false, false, false, true, true, true);
   }

   @Test
   public void testCompactwithConcurrentDeletes() throws Exception {
      internalCompactTest(false, false, true, false, false, true, false, false, false, false, true, true, true);
      tearDown();
      setUp();
      internalCompactTest(false, false, true, false, false, false, true, false, false, false, true, true, true);
   }

   @Test
   public void testCompactwithConcurrentUpdates() throws Exception {
      internalCompactTest(false, false, true, false, true, false, false, false, false, false, true, true, true);
   }

   @Test
   public void testCompactWithConcurrentAppend() throws Exception {
      internalCompactTest(false, false, true, true, false, false, false, false, false, false, true, true, true);
   }

   @Test
   public void testCompactFirstFileReclaimed() throws Exception {

      setup(2, 60 * 1024, false);

      final byte recordType = (byte) 0;

      journal = new JournalImpl(fileSize, minFiles, minFiles, 0, 0, fileFactory, filePrefix, fileExtension, maxAIO);

      journal.start();

      journal.loadInternalOnly();

      journal.appendAddRecord(1, recordType, "test".getBytes(), true);

      journal.forceMoveNextFile();

      journal.appendUpdateRecord(1, recordType, "update".getBytes(), true);

      journal.appendDeleteRecord(1, true);

      journal.appendAddRecord(2, recordType, "finalRecord".getBytes(), true);

      for (int i = 10; i < 100; i++) {
         journal.appendAddRecord(i, recordType, ("tst" + i).getBytes(), true);
         journal.forceMoveNextFile();
         journal.appendUpdateRecord(i, recordType, ("uptst" + i).getBytes(), true);
         journal.appendDeleteRecord(i, true);
      }

      journal.testCompact();

      journal.stop();

      List<RecordInfo> records1 = new ArrayList<>();

      List<PreparedTransactionInfo> preparedRecords = new ArrayList<>();

      journal.start();

      journal.load(records1, preparedRecords, null);

      assertEquals(1, records1.size());

   }

   @Test
   public void testInvalidDataCompact() throws Exception {

      setup(2, 60 * 1024, false);

      final byte recordType = (byte) 0;

      journal = new JournalImpl(fileSize, minFiles, minFiles, 0, 0, fileFactory, filePrefix, fileExtension, maxAIO);

      AtomicBoolean failed = new AtomicBoolean(false);
      journal.setCriticalErrorListener((a, b, c) -> {
         new Exception("failed").printStackTrace();
         failed.set(true);
      });

      journal.start();

      journal.loadInternalOnly();

      journal.appendAddRecord(1, recordType, "test".getBytes(), true);

      journal.appendUpdateRecord(1, recordType, "update".getBytes(), true);

      // this is simulating a bug on the journal usage
      // compacting should just ignore the record
      journal.appendUpdateRecordTransactional(3, -1, recordType, "upd".getBytes());
      journal.appendPrepareRecord(3, "data".getBytes(), true);

      journal.appendUpdateRecordTransactional(4, -1, recordType, "upd".getBytes());
      journal.appendCommitRecord(3, true);


      try {
         AssertionLoggerHandler.startCapture();
         journal.testCompact();
         Assert.assertTrue(AssertionLoggerHandler.findText("must be"));
         Assert.assertFalse(failed.get());
         AssertionLoggerHandler.clear();
         journal.testCompact();
         Assert.assertFalse(AssertionLoggerHandler.findText("must be")); // the invalid records should be cleared during a compact
      } finally {
         AssertionLoggerHandler.stopCapture();
      }

   }



   @Test
   public void testCompactPrepareRestart() throws Exception {
      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();

      load();

      startCompact();

      addTx(1, 2);

      prepare(1, new SimpleEncoding(10, (byte) 0));

      finishCompact();

      stopJournal();

      createJournal();

      startJournal();

      loadAndCheck();

      startCompact();

      commit(1);

      finishCompact();

      journal.testCompact();

      stopJournal();

      createJournal();

      startJournal();

      loadAndCheck();
   }

   @Test
   public void testCompactPrepareRestart2() throws Exception {
      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();

      load();

      addTx(1, 2);

      prepare(1, new SimpleEncoding(10, (byte) 0));

      stopJournal();

      createJournal();

      startJournal();

      loadAndCheck();

      startCompact();

      commit(1);

      finishCompact();

      journal.testCompact();

      stopJournal();

      createJournal();

      startJournal();

      loadAndCheck();
   }

   @Test
   public void testCompactPrepareRestart3() throws Exception {
      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();

      load();

      addTx(1, 2, 3);

      prepare(1, new SimpleEncoding(10, (byte) 0));

      startCompact();

      commit(1);

      finishCompact();

      journal.testCompact();

      stopJournal();

      createJournal();

      startJournal();

      loadAndCheck();
   }

   @Test
   public void testOnRollback() throws Exception {

      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();

      journal.setAutoReclaim(false);

      load();

      add(1);

      updateTx(2, 1);

      rollback(2);

      journal.testCompact();

      stopJournal();

      startJournal();

      loadAndCheck();

      stopJournal();

   }

   @Test
   public void testCompactSecondFileReclaimed() throws Exception {

      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();

      load();

      addTx(1, 1, 2, 3, 4);

      journal.forceMoveNextFile();

      addTx(1, 5, 6, 7, 8);

      commit(1);

      journal.forceMoveNextFile();

      journal.testCompact();

      add(10);

      stopJournal();

      startJournal();

      loadAndCheck();

      stopJournal();

   }

   @Test
   public void testIncompleteTXDuringcompact() throws Exception {

      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();

      load();

      add(1);

      updateTx(2, 1);

      journal.testCompact();

      journal.testCompact();

      commit(2);

      stopJournal();

      startJournal();

      loadAndCheck();

      stopJournal();

   }

   private void internalCompactTest(final boolean preXA,
                                    // prepare before compact
                                    final boolean postXA,
                                    // prepare after compact
                                    final boolean regularAdd,
                                    final boolean performAppend,
                                    final boolean performUpdate,
                                    boolean performDelete,
                                    boolean performNonTransactionalDelete,
                                    final boolean pendingTransactions,
                                    final boolean deleteTransactRecords,
                                    final boolean delayCommit,
                                    final boolean createControlFile,
                                    final boolean deleteControlFile,
                                    final boolean renameFilesAfterCompacting) throws Exception {
      if (performNonTransactionalDelete) {
         performDelete = false;
      }
      if (performDelete) {
         performNonTransactionalDelete = false;
      }

      setup(2, 60 * 4096, true);

      ArrayList<Long> liveIDs = new ArrayList<>();

      ArrayList<Pair<Long, Long>> transactedRecords = new ArrayList<>();

      final CountDownLatch latchDone = new CountDownLatch(1);
      final CountDownLatch latchWait = new CountDownLatch(1);
      journal = new JournalImpl(fileSize, minFiles, minFiles, 0, 0, fileFactory, filePrefix, fileExtension, maxAIO) {

         @Override
         protected SequentialFile createControlFile(final List<JournalFile> files,
                                                    final List<JournalFile> newFiles,
                                                    final Pair<String, String> pair) throws Exception {
            if (createControlFile) {
               return super.createControlFile(files, newFiles, pair);
            } else {
               throw new IllegalStateException("Simulating a crash during compact creation");
            }
         }

         @Override
         protected void deleteControlFile(final SequentialFile controlFile) throws Exception {
            if (deleteControlFile) {
               super.deleteControlFile(controlFile);
            }
         }

         @Override
         protected void renameFiles(final List<JournalFile> oldFiles,
                                    final List<JournalFile> newFiles) throws Exception {
            if (renameFilesAfterCompacting) {
               super.renameFiles(oldFiles, newFiles);
            }
         }

         @Override
         public void onCompactDone() {
            latchDone.countDown();
            instanceLog.debug("Waiting on Compact");
            try {
               ActiveMQTestBase.waitForLatch(latchWait);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
            instanceLog.debug("Done");
         }
      };

      AtomicInteger criticalErrors = new AtomicInteger(0);
      journal.setCriticalErrorListener((a, b, c) -> {
         System.out.println("Error");
         criticalErrors.incrementAndGet();
      });

      journal.setAutoReclaim(false);

      startJournal();
      load();

      long transactionID = 0;

      if (regularAdd) {

         for (int i = 0; i < NIOJournalCompactTest.NUMBER_OF_RECORDS / 2; i++) {
            add(i);
            if (i % 10 == 0 && i > 0) {
               journal.forceMoveNextFile();
            }
            update(i);
         }

         for (int i = NIOJournalCompactTest.NUMBER_OF_RECORDS / 2; i < NIOJournalCompactTest.NUMBER_OF_RECORDS; i++) {

            addTx(transactionID, i);
            updateTx(transactionID, i);
            if (i % 10 == 0) {
               journal.forceMoveNextFile();
            }
            commit(transactionID++);
            update(i);
         }
      }

      for (int i = 0; i < 10; i++) {
         journal.appendAddEvent(idGenerator.generateID(), (byte) 0, EncoderPersister.getInstance(), new ByteArrayEncoding(new byte[10]), false, null);
      }

      if (pendingTransactions) {
         for (long i = 0; i < 100; i++) {
            long recordID = idGenerator.generateID();
            addTx(transactionID, recordID);
            updateTx(transactionID, recordID);
            if (preXA) {
               prepare(transactionID, new SimpleEncoding(10, (byte) 0));
            }
            transactedRecords.add(new Pair<>(transactionID++, recordID));
         }
      }

      if (regularAdd) {
         for (int i = 0; i < NIOJournalCompactTest.NUMBER_OF_RECORDS; i++) {
            if (!(i % 10 == 0)) {
               delete(i);
            } else {
               liveIDs.add((long) i);
            }
         }
      }

      journal.forceMoveNextFile();

      Thread t = new Thread() {
         @Override
         public void run() {
            try {
               journal.testCompact();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      t.start();

      ActiveMQTestBase.waitForLatch(latchDone);

      int nextID = NIOJournalCompactTest.NUMBER_OF_RECORDS;

      if (performAppend) {
         for (int i = 0; i < 50; i++) {
            add(nextID++);
            if (i % 10 == 0) {
               journal.forceMoveNextFile();
            }
         }

         for (int i = 0; i < 50; i++) {
            // A Total new transaction (that was created after the compact started) to add new record while compacting
            // is still working
            addTx(transactionID, nextID++);
            commit(transactionID++);
            if (i % 10 == 0) {
               journal.forceMoveNextFile();
            }
         }
      }

      if (performUpdate) {
         int count = 0;
         for (Long liveID : liveIDs) {
            if (count++ % 2 == 0) {
               update(liveID);
            } else {
               // A Total new transaction (that was created after the compact started) to update a record that is being
               // compacted
               updateTx(transactionID, liveID);
               commit(transactionID++);
            }
         }
      }

      if (performDelete) {
         int count = 0;
         for (long liveID : liveIDs) {
            if (count++ % 2 == 0) {
               instanceLog.debug("Deleting no trans " + liveID);
               delete(liveID);
            } else {
               instanceLog.debug("Deleting TX " + liveID);
               // A Total new transaction (that was created after the compact started) to delete a record that is being
               // compacted
               deleteTx(transactionID, liveID);
               commit(transactionID++);
            }

            instanceLog.debug("Deletes are going into " + ((JournalImpl) journal).getCurrentFile());
         }
      }

      if (performNonTransactionalDelete) {
         for (long liveID : liveIDs) {
            delete(liveID);
         }
      }

      if (pendingTransactions && !delayCommit) {
         for (Pair<Long, Long> tx : transactedRecords) {
            if (postXA) {
               prepare(tx.getA(), new SimpleEncoding(10, (byte) 0));
            }
            if (tx.getA() % 2 == 0) {
               commit(tx.getA());

               if (deleteTransactRecords) {
                  delete(tx.getB());
               }
            } else {
               rollback(tx.getA());
            }
         }
      }

      /** Some independent adds and updates */
      for (int i = 0; i < 1000; i++) {
         long id = idGenerator.generateID();
         add(id);
         delete(id);

         if (i % 100 == 0) {
            journal.forceMoveNextFile();
         }
      }
      journal.forceMoveNextFile();

      latchWait.countDown();

      t.join();

      if (pendingTransactions && delayCommit) {
         for (Pair<Long, Long> tx : transactedRecords) {
            if (postXA) {
               prepare(tx.getA(), new SimpleEncoding(10, (byte) 0));
            }
            if (tx.getA() % 2 == 0) {
               commit(tx.getA());

               if (deleteTransactRecords) {
                  delete(tx.getB());
               }
            } else {
               rollback(tx.getA());
            }
         }
      }

      long lastId = idGenerator.generateID();

      add(lastId);

      if (createControlFile && deleteControlFile && renameFilesAfterCompacting) {
         journal.testCompact();
      }

      journal.flush();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

      journal.forceMoveNextFile();
      update(lastId);

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

      if (createControlFile) {
         Assert.assertEquals(0, criticalErrors.get());
      } else {
         Assert.assertEquals(1, criticalErrors.get());
      }

   }

   @Test
   public void testCompactAddAndUpdateFollowedByADelete() throws Exception {
      setup(2, 60 * 1024, false);

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();
      journal.setAutoReclaim(false);

      startJournal();
      load();

      long consumerTX = idGen.generateID();

      long firstID = idGen.generateID();

      long appendTX = idGen.generateID();

      long addedRecord = idGen.generateID();

      addTx(consumerTX, firstID);

      startCompact();

      addTx(appendTX, addedRecord);

      commit(appendTX);

      updateTx(consumerTX, addedRecord);

      commit(consumerTX);

      delete(addedRecord);

      finishCompact();

      journal.forceMoveNextFile();

      long newRecord = idGen.generateID();
      add(newRecord);
      update(newRecord);

      journal.testCompact();

      instanceLog.debug("Debug after compact\n" + journal.debug());

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testStressAppends() throws Exception {
      setup(2, 60 * 1024, true);

      final int NUMBER_OF_RECORDS = 200;

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();
      journal.setAutoReclaim(false);

      startJournal();
      load();

      AtomicBoolean running = new AtomicBoolean(true);
      Thread t = new Thread() {
         @Override
         public void run() {
            while (running.get()) {
               journal.testCompact();
            }
         }
      };
      t.start();


      for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
         long tx = idGen.generateID();
         addTx(tx, idGen.generateID());
         LockSupport.parkNanos(1000);
         commit(tx);
      }


      running.set(false);

      t.join(50000);
      if (t.isAlive()) {
         t.interrupt();
         Assert.fail("supposed to join thread");
      }

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testSimpleCommitCompactInBetween() throws Exception {
      setup(2, 60 * 1024, false);

      final int NUMBER_OF_RECORDS = 1;

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();
      journal.setAutoReclaim(false);

      startJournal();
      load();


      for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
         long tx = idGen.generateID();
         addTx(tx, idGen.generateID());
         journal.testCompact();
         journal.testCompact();
         journal.testCompact();
         journal.testCompact();
         logger.debug("going to commit");
         commit(tx);
      }


      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactAddAndUpdateFollowedByADelete2() throws Exception {

      setup(2, 60 * 1024, false);

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();

      journal.setAutoReclaim(false);

      startJournal();
      load();

      long firstID = idGen.generateID();

      long consumerTX = idGen.generateID();

      long appendTX = idGen.generateID();

      long addedRecord = idGen.generateID();

      addTx(consumerTX, firstID);

      startCompact();

      addTx(appendTX, addedRecord);
      commit(appendTX);
      updateTx(consumerTX, addedRecord);
      commit(consumerTX);

      long deleteTXID = idGen.generateID();

      deleteTx(deleteTXID, addedRecord);

      commit(deleteTXID);

      finishCompact();

      journal.forceMoveNextFile();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }

   @Test
   public void testCompactAddAndUpdateFollowedByADelete3() throws Exception {

      setup(2, 60 * 1024, false);

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();

      journal.setAutoReclaim(false);

      startJournal();
      load();

      long firstID = idGen.generateID();

      long consumerTX = idGen.generateID();

      long addedRecord = idGen.generateID();

      add(firstID);

      updateTx(consumerTX, firstID);

      startCompact();

      addTx(consumerTX, addedRecord);
      commit(consumerTX);
      delete(addedRecord);

      finishCompact();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }

   @Test
   public void testCompactAddAndUpdateFollowedByADelete4() throws Exception {

      setup(2, 60 * 1024, false);

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();

      startJournal();
      load();

      long consumerTX = idGen.generateID();

      long firstID = idGen.generateID();

      long appendTX = idGen.generateID();

      long addedRecord = idGen.generateID();

      startCompact();

      addTx(consumerTX, firstID);

      addTx(appendTX, addedRecord);

      commit(appendTX);

      updateTx(consumerTX, addedRecord);

      commit(consumerTX);

      delete(addedRecord);

      finishCompact();

      journal.forceMoveNextFile();

      long newRecord = idGen.generateID();
      add(newRecord);
      update(newRecord);

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }

   @Test
   public void testCompactAddAndUpdateFollowedByADelete6() throws Exception {

      setup(2, 60 * 1024, false);

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();

      journal.setAutoReclaim(false);

      startJournal();
      load();

      long tx0 = idGen.generateID();

      long tx1 = idGen.generateID();

      long add1 = idGen.generateID();

      long add2 = idGen.generateID();

      startCompact();

      addTx(tx0, add1);

      rollback(tx0);

      addTx(tx1, add1, add2);
      commit(tx1);

      finishCompact();

      long tx2 = idGen.generateID();

      updateTx(tx2, add1, add2);
      commit(tx2);

      delete(add1);

      startCompact();

      delete(add2);

      finishCompact();

      journal.forceMoveNextFile();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }

   @Test
   public void testDeleteWhileCleanup() throws Exception {

      setup(2, 60 * 1024, false);

      createJournal();

      startJournal();
      load();

      for (int i = 0; i < 100; i++) {
         add(i);
      }

      journal.forceMoveNextFile();

      for (int i = 10; i < 90; i++) {
         delete(i);
      }

      startCompact();

      // Delete part of the live records while cleanup still working
      for (int i = 1; i < 5; i++) {
         delete(i);
      }

      finishCompact();

      // Delete part of the live records after cleanup is done
      for (int i = 5; i < 10; i++) {
         delete(i);
      }

      assertEquals(9, journal.getCurrentFile().getNegCount(journal.getDataFiles()[0]));

      journal.forceMoveNextFile();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }

   @Test
   public void testCompactAddAndUpdateFollowedByADelete5() throws Exception {

      setup(2, 60 * 1024, false);

      SimpleIDGenerator idGen = new SimpleIDGenerator(1000);

      createJournal();

      startJournal();
      load();

      long appendTX = idGen.generateID();
      long appendOne = idGen.generateID();
      long appendTwo = idGen.generateID();

      long updateTX = idGen.generateID();

      addTx(appendTX, appendOne);

      startCompact();

      addTx(appendTX, appendTwo);

      commit(appendTX);

      updateTx(updateTX, appendOne);
      updateTx(updateTX, appendTwo);

      commit(updateTX);
      // delete(appendTwo);

      finishCompact();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }

   @Test
   public void testSimpleCompacting() throws Exception {
      setup(2, 60 * 1024, false);

      createJournal();
      startJournal();
      load();

      int NUMBER_OF_RECORDS = 1000;

      // add and remove some data to force reclaiming
      {
         ArrayList<Long> ids = new ArrayList<>();
         for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
            long id = idGenerator.generateID();
            ids.add(id);
            add(id);
            if (i > 0 && i % 100 == 0) {
               journal.forceMoveNextFile();
            }
         }

         for (Long id : ids) {
            delete(id);
         }

         journal.forceMoveNextFile();

         journal.checkReclaimStatus();
      }

      long transactionID = 0;

      for (int i = 0; i < NUMBER_OF_RECORDS / 2; i++) {
         add(i);
         if (i % 10 == 0 && i > 0) {
            journal.forceMoveNextFile();
         }
         update(i);
      }

      for (int i = NUMBER_OF_RECORDS / 2; i < NUMBER_OF_RECORDS; i++) {

         addTx(transactionID, i);
         updateTx(transactionID, i);
         if (i % 10 == 0) {
            journal.forceMoveNextFile();
         }
         commit(transactionID++);
         update(i);
      }

      for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
         if (!(i % 10 == 0)) {
            delete(i);
         }
      }

      for (int i = 0; i < 10; i++) {
         journal.appendAddEvent(idGenerator.generateID(), (byte) 0, EncoderPersister.getInstance(), new ByteArrayEncoding(new byte[10]), false, null);
      }

      journal.forceMoveNextFile();

      instanceLog.debug("Number of Files: " + journal.getDataFilesCount());

      instanceLog.debug("Before compact ****************************");
      instanceLog.debug(journal.debug());
      instanceLog.debug("*****************************************");

      journal.testCompact();

      add(idGenerator.generateID());

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

   }


   @Test
   public void testReconfigureJournalSize() throws Exception {
      setup(2, 30 * 4096, false);

      createJournal();
      startJournal();
      load();

      int NUMBER_OF_RECORDS = 1000;

      // add and remove some data to force reclaiming
      {
         ArrayList<Long> ids = new ArrayList<>();
         for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
            long id = idGenerator.generateID();
            ids.add(id);
            add(id);
            if (i > 0 && i % 100 == 0) {
               journal.forceMoveNextFile();
            }
         }

         journal.forceMoveNextFile();

         journal.checkReclaimStatus();
      }

      journal.testCompact();

      stopJournal();

      // expanding the size
      setup(2, 60 * 4096, false);
      createJournal();
      startJournal();
      loadAndCheck();

      // add and remove some data to force reclaiming
      {
         ArrayList<Long> ids = new ArrayList<>();
         for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
            long id = idGenerator.generateID();
            ids.add(id);
            add(id);
            if (i > 0 && i % 100 == 0) {
               journal.forceMoveNextFile();
            }
         }

         journal.forceMoveNextFile();

         journal.checkReclaimStatus();
      }
      stopJournal();

      // shrinking the size
      setup(2, 15 * 4096, false);
      createJournal();
      startJournal();
      loadAndCheck();

      journal.testCompact();


   }

   @Test
   public void testLiveSize() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      ArrayList<Long> listToDelete = new ArrayList<>();

      ArrayList<Integer> expectedSizes = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
         long id = idGenerator.generateID();
         listToDelete.add(id);

         expectedSizes.add(recordLength + JournalImpl.SIZE_ADD_RECORD + 1);

         add(id);
         journal.forceMoveNextFile();
         update(id);

         expectedSizes.add(recordLength + JournalImpl.SIZE_ADD_RECORD + 1);
         journal.forceMoveNextFile();
      }

      JournalFile[] files = journal.getDataFiles();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

      journal.forceMoveNextFile();

      JournalFile[] files2 = journal.getDataFiles();

      Assert.assertEquals(files.length, files2.length);

      for (int i = 0; i < files.length; i++) {
         Assert.assertEquals(expectedSizes.get(i).intValue(), files[i].getLiveSize());
         Assert.assertEquals(expectedSizes.get(i).intValue(), files2[i].getLiveSize());
      }

      for (long id : listToDelete) {
         delete(id);
      }

      journal.forceMoveNextFile();

      JournalFile[] files3 = journal.getDataFiles();

      for (JournalFile file : files3) {
         Assert.assertEquals(0, file.getLiveSize());
      }

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

      files3 = journal.getDataFiles();

      for (JournalFile file : files3) {
         Assert.assertEquals(0, file.getLiveSize());
      }

   }

   @Test
   public void testCompactFirstFileWithPendingCommits() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long tx = idGenerator.generateID();
      for (int i = 0; i < 10; i++) {
         addTx(tx, idGenerator.generateID());
      }

      journal.forceMoveNextFile();

      ArrayList<Long> listToDelete = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         if (i == 5) {
            commit(tx);
         }
         long id = idGenerator.generateID();
         listToDelete.add(id);
         add(id);
      }

      journal.forceMoveNextFile();

      for (Long id : listToDelete) {
         delete(id);
      }

      journal.forceMoveNextFile();

      // This operation used to be journal.cleanup(journal.getDataFiles()[0]); when cleanup was still in place
      journal.testCompact();

      journal.checkReclaimStatus();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactFirstFileWithPendingCommits3() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long tx = idGenerator.generateID();
      for (int i = 0; i < 10; i++) {
         addTx(tx, idGenerator.generateID());
      }

      journal.forceMoveNextFile();

      ArrayList<Long> listToDelete = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         long id = idGenerator.generateID();
         listToDelete.add(id);
         add(id);
      }

      journal.forceMoveNextFile();

      for (Long id : listToDelete) {
         delete(id);
      }

      journal.forceMoveNextFile();

      rollback(tx);

      journal.forceMoveNextFile();
      journal.checkReclaimStatus();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactFirstFileWithPendingCommits2() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long tx = idGenerator.generateID();
      for (int i = 0; i < 10; i++) {
         addTx(tx, idGenerator.generateID());
      }

      journal.forceMoveNextFile();

      ArrayList<Long> listToDelete = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         long id = idGenerator.generateID();
         listToDelete.add(id);
         add(id);
      }

      journal.forceMoveNextFile();

      for (Long id : listToDelete) {
         delete(id);
      }

      journal.forceMoveNextFile();

      startCompact();
      instanceLog.debug("Committing TX " + tx);
      commit(tx);
      finishCompact();

      journal.checkReclaimStatus();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactFirstFileWithPendingCommits4() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long[] ids = new long[10];

      long tx0 = idGenerator.generateID();
      for (int i = 0; i < 10; i++) {
         ids[i] = idGenerator.generateID();
         addTx(tx0, ids[i]);
      }

      long tx1 = idGenerator.generateID();

      journal.forceMoveNextFile();

      ArrayList<Long> listToDelete = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         long id = idGenerator.generateID();
         listToDelete.add(id);
         add(id);
      }

      journal.forceMoveNextFile();

      for (Long id : listToDelete) {
         delete(id);
      }

      journal.forceMoveNextFile();

      startCompact();
      instanceLog.debug("Committing TX " + tx1);
      rollback(tx0);
      for (int i = 0; i < 10; i++) {
         addTx(tx1, ids[i]);
      }

      journal.forceMoveNextFile();
      commit(tx1);
      finishCompact();

      journal.checkReclaimStatus();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactFirstFileWithPendingCommits5() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long[] ids = new long[10];

      long tx0 = idGenerator.generateID();
      for (int i = 0; i < 10; i++) {
         ids[i] = idGenerator.generateID();
         addTx(tx0, ids[i]);
      }

      long tx1 = idGenerator.generateID();

      journal.forceMoveNextFile();

      ArrayList<Long> listToDelete = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
         long id = idGenerator.generateID();
         listToDelete.add(id);
         add(id);
      }

      journal.forceMoveNextFile();

      for (Long id : listToDelete) {
         delete(id);
      }

      journal.forceMoveNextFile();

      startCompact();
      instanceLog.debug("Committing TX " + tx1);
      rollback(tx0);
      for (int i = 0; i < 10; i++) {
         addTx(tx1, ids[i]);
      }

      journal.forceMoveNextFile();
      commit(tx1);
      finishCompact();

      journal.checkReclaimStatus();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactFirstFileWithPendingCommits6() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long[] ids = new long[10];

      long tx0 = idGenerator.generateID();
      for (int i = 0; i < 10; i++) {
         ids[i] = idGenerator.generateID();
         addTx(tx0, ids[i]);
      }

      commit(tx0);

      startCompact();
      for (int i = 0; i < 10; i++) {
         delete(ids[i]);
      }
      finishCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testCompactFirstFileWithPendingCommits7() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      long tx0 = idGenerator.generateID();
      add(idGenerator.generateID());

      long[] ids = new long[]{idGenerator.generateID(), idGenerator.generateID()};

      addTx(tx0, ids[0]);
      addTx(tx0, ids[1]);

      journal.forceMoveNextFile();

      commit(tx0);

      journal.forceMoveNextFile();

      delete(ids[0]);
      delete(ids[1]);

      journal.forceMoveNextFile();

      journal.testCompact();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();
   }

   @Test
   public void testLiveSizeTransactional() throws Exception {
      setup(2, 60 * 1024, true);

      createJournal();
      startJournal();
      loadAndCheck();

      ArrayList<Long> listToDelete = new ArrayList<>();

      ArrayList<Integer> expectedSizes = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
         long tx = idGenerator.generateID();
         long id = idGenerator.generateID();
         listToDelete.add(id);

         // Append Record Transaction will make the recordSize as exactly recordLength (discounting SIZE_ADD_RECORD_TX)
         addTx(tx, id);

         expectedSizes.add(recordLength);
         journal.forceMoveNextFile();

         updateTx(tx, id);
         // uPDATE Record Transaction will make the recordSize as exactly recordLength (discounting SIZE_ADD_RECORD_TX)
         expectedSizes.add(recordLength);

         journal.forceMoveNextFile();
         expectedSizes.add(0);

         commit(tx);

         journal.forceMoveNextFile();
      }

      JournalFile[] files = journal.getDataFiles();

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

      journal.forceMoveNextFile();

      JournalFile[] files2 = journal.getDataFiles();

      Assert.assertEquals(files.length, files2.length);

      for (int i = 0; i < files.length; i++) {
         Assert.assertEquals(expectedSizes.get(i).intValue(), files[i].getLiveSize());
         Assert.assertEquals(expectedSizes.get(i).intValue(), files2[i].getLiveSize());
      }

      long tx = idGenerator.generateID();
      for (long id : listToDelete) {
         deleteTx(tx, id);
      }
      commit(tx);

      journal.forceMoveNextFile();

      JournalFile[] files3 = journal.getDataFiles();

      for (JournalFile file : files3) {
         Assert.assertEquals(0, file.getLiveSize());
      }

      stopJournal();
      createJournal();
      startJournal();
      loadAndCheck();

      files3 = journal.getDataFiles();

      for (JournalFile file : files3) {
         Assert.assertEquals(0, file.getLiveSize());
      }

   }


   @Test
   public void testStressDeletesNoSync() throws Throwable {
      Configuration config = createBasicConfig().setJournalFileSize(100 * 1024).setJournalSyncNonTransactional(false).setJournalSyncTransactional(false).setJournalCompactMinFiles(0).setJournalCompactPercentage(0);

      final AtomicInteger errors = new AtomicInteger(0);

      final AtomicBoolean running = new AtomicBoolean(true);

      final AtomicLong seqGenerator = new AtomicLong(1);

      final ExecutorService executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory());

      final ExecutorService ioexecutor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory());

      OrderedExecutorFactory factory = new OrderedExecutorFactory(executor);

      OrderedExecutorFactory iofactory = new OrderedExecutorFactory(ioexecutor);

      final ExecutorService deleteExecutor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory());

      final JournalStorageManager storage = new JournalStorageManager(config, EmptyCriticalAnalyzer.getInstance(), factory, iofactory);

      storage.start();

      try {
         storage.loadInternalOnly();

         ((JournalImpl) storage.getMessageJournal()).setAutoReclaim(false);
         final LinkedList<Long> survivingMsgs = new LinkedList<>();

         Runnable producerRunnable = new Runnable() {
            @Override
            public void run() {
               try {
                  while (running.get()) {
                     final long[] values = new long[100];
                     long tx = seqGenerator.incrementAndGet();

                     OperationContextImpl ctx = new OperationContextImpl(executor);
                     storage.setContext(ctx);

                     for (int i = 0; i < 100; i++) {
                        long id = seqGenerator.incrementAndGet();
                        values[i] = id;

                        CoreMessage message = new CoreMessage(id, 100);

                        message.getBodyBuffer().writeBytes(new byte[1024]);

                        storage.storeMessageTransactional(tx, message);
                     }
                     CoreMessage message = new CoreMessage(seqGenerator.incrementAndGet(), 100);

                     survivingMsgs.add(message.getMessageID());

                     logger.debug("Going to store " + message);
                     // This one will stay here forever
                     storage.storeMessage(message);
                     logger.debug("message stored " + message);

                     logger.debug("Going to commit " + tx);
                     storage.commit(tx);
                     logger.debug("Committed " + tx);

                     ctx.executeOnCompletion(new IOCallback() {
                        @Override
                        public void onError(int errorCode, String errorMessage) {
                        }

                        @Override
                        public void done() {
                           deleteExecutor.execute(new Runnable() {
                              @Override
                              public void run() {
                                 try {
                                    for (long messageID : values) {
                                       storage.deleteMessage(messageID);
                                    }
                                 } catch (Throwable e) {
                                    e.printStackTrace();
                                    errors.incrementAndGet();
                                 }

                              }
                           });
                        }
                     });

                  }
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            }
         };

         Runnable compressRunnable = new Runnable() {
            @Override
            public void run() {
               try {
                  while (running.get()) {
                     Thread.sleep(500);
                     instanceLog.debug("Compacting");
                     ((JournalImpl) storage.getMessageJournal()).testCompact();
                     ((JournalImpl) storage.getMessageJournal()).checkReclaimStatus();
                  }
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }

            }
         };

         Thread producerThread = new Thread(producerRunnable);
         producerThread.start();

         Thread compactorThread = new Thread(compressRunnable);
         compactorThread.start();

         Thread.sleep(1000);

         running.set(false);

         producerThread.join();

         compactorThread.join();

         deleteExecutor.shutdown();

         assertTrue("delete executor failted to terminate", deleteExecutor.awaitTermination(30, TimeUnit.SECONDS));

         storage.stop();

         executor.shutdown();

         assertTrue("executor failed to terminate", executor.awaitTermination(30, TimeUnit.SECONDS));

         ioexecutor.shutdown();

         assertTrue("ioexecutor failed to terminate", ioexecutor.awaitTermination(30, TimeUnit.SECONDS));

         Assert.assertEquals(0, errors.get());

      } catch (Throwable e) {
         e.printStackTrace();
         throw e;
      } finally {
         try {
            storage.stop();
         } catch (Exception e) {
            e.printStackTrace();
         }

         executor.shutdownNow();
         deleteExecutor.shutdownNow();
         ioexecutor.shutdownNow();
      }

   }

   @Test
   public void testLargeCompacting() throws Exception {
      setup(2, 4 * 1024, false);

      createJournal();

      startJournal();

      load();


      for (int i = 0; i < 250; i += 2) {
         addTx(i, i + 1);

         commit(i);

         journal.forceMoveNextFile();
      }

      journal.testCompact();
   }


   @Override
   @After
   public void tearDown() throws Exception {
      File testDir = new File(getTestDir());

      File[] files = testDir.listFiles(new FilenameFilter() {

         @Override
         public boolean accept(File dir, String name) {
            return name.startsWith(filePrefix) && name.endsWith(fileExtension);
         }
      });

      for (File file : files) {
         assertEquals("File " + file + " doesn't have the expected number of bytes", fileSize, file.length());
      }

      super.tearDown();
   }

   @Override
   protected SequentialFileFactory getFileFactory() throws Exception {
      return new NIOSequentialFileFactory(getTestDirfile(), 1);
   }

}
