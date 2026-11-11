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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.mapped.MappedSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This test spawns a remote VM, as we want to "crash" the VM right after the journal is filled with data
 */
public class ValidateTransactionHealthTest extends SpawnedTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int OK = 10;


   @Test
   public void testAIO() throws Exception {
      internalTest("aio", getTestDir(), 1000, 100, true, 10);
   }

   @Test
   public void testNIO() throws Exception {
      internalTest("nio", getTestDir(), 1000, 100, true, 10);
   }

   @Test
   public void testNIO2() throws Exception {
      internalTest("nio2", getTestDir(), 1000, 100, true, 10);
   }

   @Test
   public void testMMAP() throws Exception {
      internalTest("mmap", getTestDir(), 1000, 100, true, 10);
   }

   private void internalTest(final String type,
                             final String journalDir,
                             final long numberOfRecords,
                             final int transactionSize,
                             final boolean append,
                             final int numberOfThreads) throws Exception {
      try {
         if (type.equals("aio") && !LibaioContext.isLoaded()) {
            logger.warn("AIO not found, test being ignored on this platform");
            return;
         }

         if (append) {
            appendData(type, journalDir, numberOfRecords, transactionSize, numberOfThreads);
         }

         reload(type, journalDir, numberOfRecords, numberOfThreads);
      } finally {
         File file = new File(journalDir);
         deleteDirectory(file);
      }
   }

   private void reload(final String type,
                       final String journalDir,
                       final long numberOfRecords,
                       final int numberOfThreads) throws Exception {
      JournalImpl journal = ValidateTransactionHealthTest.createJournal(type, journalDir);

      journal.start();
      try {
         Loader loadTest = new Loader(numberOfRecords);
         journal.load(loadTest);
         assertEquals(numberOfRecords * numberOfThreads, loadTest.numberOfAdds);
         assertEquals(0, loadTest.numberOfPreparedTransactions);
         assertEquals(0, loadTest.numberOfUpdates);
         assertEquals(0, loadTest.numberOfDeletes);

         if (loadTest.ex != null) {
            throw loadTest.ex;
         }
      } finally {
         journal.stop();
      }

   }


   static class Loader implements LoaderCallback {

      int numberOfPreparedTransactions = 0;

      int numberOfAdds = 0;

      int numberOfDeletes = 0;

      int numberOfUpdates = 0;

      long expectedRecords = 0;

      Exception ex = null;

      long lastID = 0;

      Loader(final long expectedRecords) {
         this.expectedRecords = expectedRecords;
      }

      @Override
      public void addPreparedTransaction(final PreparedTransactionInfo preparedTransaction) {
         numberOfPreparedTransactions++;

      }

      @Override
      public void addRecord(final RecordInfo info) {
         if (info.id == lastID) {
            logger.debug("id = {} last id = {}", info.id, lastID);
         }

         ByteBuffer buffer = ByteBuffer.wrap(info.data);
         long recordValue = buffer.getLong();

         if (recordValue != info.id) {
            ex = new Exception("Content not as expected (" + recordValue + " != " + info.id + ")");

         }

         lastID = info.id;
         numberOfAdds++;

      }

      @Override
      public void deleteRecord(final long id) {
         numberOfDeletes++;

      }

      @Override
      public void updateRecord(final RecordInfo info) {
         numberOfUpdates++;

      }

      @Override
      public void failedTransaction(final long transactionID,
                                    final List<RecordInfo> records,
                                    final List<RecordInfo> recordsToDelete) {
      }

   }

   public void appendData(final String journalType,
                                        final String journalDir,
                                        final long numberOfElements,
                                        final int transactionSize,
                                        final int numberOfThreads) throws Exception {
      final JournalImpl journal = ValidateTransactionHealthTest.createJournal(journalType, journalDir);

      journal.start();
      journal.load(new LoaderCallback() {

         @Override
         public void addPreparedTransaction(final PreparedTransactionInfo preparedTransaction) {
         }

         @Override
         public void addRecord(final RecordInfo info) {
         }

         @Override
         public void deleteRecord(final long id) {
         }

         @Override
         public void updateRecord(final RecordInfo info) {
         }

         @Override
         public void failedTransaction(final long transactionID,
                                       final List<RecordInfo> records,
                                       final List<RecordInfo> recordsToDelete) {
         }
      });

      LocalThread[] threads = new LocalThread[numberOfThreads];
      final AtomicLong sequenceTransaction = new AtomicLong();

      for (int i = 0; i < numberOfThreads; i++) {
         threads[i] = new LocalThread(journal, numberOfElements, transactionSize, sequenceTransaction);
         threads[i].start();
      }

      Exception e = null;
      for (LocalThread t : threads) {
         t.join(10_000);

         assertFalse(t.isAlive());

         if (t.e != null) {
            e = t.e;
         }
      }

      if (e != null) {
         throw e;
      }

      journal.flush();

      journal.stop();
   }

   public static JournalImpl createJournal(final String journalType, final String journalDir) {
      JournalImpl journal = new JournalImpl(10485760, 2, 2, 0, 0, ValidateTransactionHealthTest.getFactory(journalType, journalDir, 10485760), "journaltst", "tst", 500);
      return journal;
   }

   public static SequentialFileFactory getFactory(final String factoryType, final String directory, int fileSize) {
      if (factoryType.equals("aio")) {
         return new AIOSequentialFileFactory(new File(directory), ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO, 10, false);
      } else if (factoryType.equals("nio2")) {
         return new NIOSequentialFileFactory(new File(directory), true, 1);
      } else if (factoryType.equals("mmap")) {
         return new MappedSequentialFileFactory(new File(directory), fileSize, false, 0, 0, null);
      } else {
         return new NIOSequentialFileFactory(new File(directory), false, 1);
      }
   }

   static class LocalThread extends Thread {

      final JournalImpl journal;

      final long numberOfElements;

      final int transactionSize;

      final AtomicLong nextID;

      Exception e;

      LocalThread(final JournalImpl journal,
                  final long numberOfElements,
                  final int transactionSize,
                  final AtomicLong nextID) {
         super();
         this.journal = journal;
         this.numberOfElements = numberOfElements;
         this.transactionSize = transactionSize;
         this.nextID = nextID;
      }

      @Override
      public void run() {
         try {
            int transactionCounter = 0;

            long transactionId = nextID.incrementAndGet();

            for (long i = 0; i < numberOfElements; i++) {

               long id = nextID.incrementAndGet();

               ByteBuffer buffer = ByteBuffer.allocate(512 * 3);
               buffer.putLong(id);

               if (transactionSize != 0) {
                  journal.appendAddRecordTransactional(transactionId, id, (byte) 99, buffer.array());

                  if (++transactionCounter == transactionSize) {
                     logger.debug("Commit transaction {}", transactionId);
                     journal.appendCommitRecord(transactionId, true);
                     transactionCounter = 0;
                     transactionId = nextID.incrementAndGet();
                  }
               } else {
                  journal.appendAddRecord(id, (byte) 99, buffer.array(), false);
               }
            }

            if (transactionCounter != 0) {
               journal.appendCommitRecord(transactionId, true);
            }

            if (transactionSize == 0) {
               journal.debugWait();
            }
         } catch (Exception e) {
            this.e = e;
         }

      }
   }

}
