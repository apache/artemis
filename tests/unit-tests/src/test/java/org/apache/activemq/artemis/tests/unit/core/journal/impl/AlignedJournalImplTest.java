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
package org.apache.activemq.artemis.tests.unit.core.journal.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.SimpleEncoding;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AlignedJournalImplTest extends ActiveMQTestBase {
   private static final Logger log = Logger.getLogger(AlignedJournalImplTest.class);


   private static final LoaderCallback dummyLoader = new LoaderCallback() {

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
   };



   private SequentialFileFactory factory;

   JournalImpl journalImpl = null;

   private ArrayList<RecordInfo> records = null;

   private ArrayList<Long> incompleteTransactions = null;

   private ArrayList<PreparedTransactionInfo> transactions = null;



   // This test just validates basic alignment on the FakeSequentialFile itself
   @Test
   public void testBasicAlignment() throws Exception {

      FakeSequentialFileFactory factory = new FakeSequentialFileFactory(200, true);

      SequentialFile file = factory.createSequentialFile("test1");

      file.open();

      try {
         ByteBuffer buffer = ByteBuffer.allocateDirect(200);
         for (int i = 0; i < 200; i++) {
            buffer.put(i, (byte) 1);
         }

         file.writeDirect(buffer, true);

         buffer = ByteBuffer.allocate(400);
         for (int i = 0; i < 400; i++) {
            buffer.put(i, (byte) 2);
         }

         file.writeDirect(buffer, true);

         buffer = ByteBuffer.allocate(600);

         file.position(0);

         file.read(buffer);

         for (int i = 0; i < 200; i++) {
            Assert.assertEquals((byte) 1, buffer.get(i));
         }

         for (int i = 201; i < 600; i++) {
            Assert.assertEquals("Position " + i, (byte) 2, buffer.get(i));
         }

      } catch (Exception ignored) {
      }
   }

   @Test
   public void testInconsistentAlignment() throws Exception {
      factory = new FakeSequentialFileFactory(512, true);

      try {
         journalImpl = new JournalImpl(2000, 2, 2, 0, 0, factory, "tt", "tt", 1000);
         Assert.fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException ignored) {
         // expected
      }

   }

   @Test
   public void testSimpleAdd() throws Exception {
      final int JOURNAL_SIZE = 1060;

      setupAndLoadJournal(JOURNAL_SIZE, 10);

      journalImpl.appendAddRecord(13, (byte) 14, new SimpleEncoding(1, (byte) 15), false);

      journalImpl.forceMoveNextFile();

      journalImpl.checkReclaimStatus();

      setupAndLoadJournal(JOURNAL_SIZE, 10);

      Assert.assertEquals(1, records.size());

      Assert.assertEquals(13, records.get(0).id);

      Assert.assertEquals(14, records.get(0).userRecordType);

      Assert.assertEquals(1, records.get(0).data.length);

      Assert.assertEquals(15, records.get(0).data[0]);

   }

   @Test
   public void testAppendAndUpdateRecords() throws Exception {

      final int JOURNAL_SIZE = 1060;

      setupAndLoadJournal(JOURNAL_SIZE, 10);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 25; i++) {
         byte[] bytes = new byte[5];
         for (int j = 0; j < bytes.length; j++) {
            bytes[j] = (byte) i;
         }
         journalImpl.appendAddRecord(i * 100L, (byte) i, bytes, false);
      }

      for (int i = 25; i < 50; i++) {
         EncodingSupport support = new SimpleEncoding(5, (byte) i);
         journalImpl.appendAddRecord(i * 100L, (byte) i, support, false);
      }

      setupAndLoadJournal(JOURNAL_SIZE, 1024);

      Assert.assertEquals(50, records.size());

      int i = 0;
      for (RecordInfo recordItem : records) {
         Assert.assertEquals(i * 100L, recordItem.id);
         Assert.assertEquals(i, recordItem.getUserRecordType());
         Assert.assertEquals(5, recordItem.data.length);
         for (int j = 0; j < 5; j++) {
            Assert.assertEquals((byte) i, recordItem.data[j]);
         }

         i++;
      }

      for (i = 40; i < 50; i++) {
         byte[] bytes = new byte[10];
         for (int j = 0; j < 10; j++) {
            bytes[j] = (byte) 'x';
         }

         journalImpl.appendUpdateRecord(i * 100L, (byte) i, bytes, false);
      }

      setupAndLoadJournal(JOURNAL_SIZE, 1024);

      i = 0;
      for (RecordInfo recordItem : records) {

         if (i < 50) {
            Assert.assertEquals(i * 100L, recordItem.id);
            Assert.assertEquals(i, recordItem.getUserRecordType());
            Assert.assertEquals(5, recordItem.data.length);
            for (int j = 0; j < 5; j++) {
               Assert.assertEquals((byte) i, recordItem.data[j]);
            }
         } else {
            Assert.assertEquals((i - 10) * 100L, recordItem.id);
            Assert.assertEquals(i - 10, recordItem.getUserRecordType());
            Assert.assertTrue(recordItem.isUpdate);
            Assert.assertEquals(10, recordItem.data.length);
            for (int j = 0; j < 10; j++) {
               Assert.assertEquals((byte) 'x', recordItem.data[j]);
            }
         }

         i++;
      }

      journalImpl.stop();

   }

   @Test
   public void testPartialDelete() throws Exception {
      final int JOURNAL_SIZE = 10000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      journalImpl.setAutoReclaim(false);

      journalImpl.checkReclaimStatus();

      journalImpl.debugWait();

      Assert.assertEquals(2, factory.listFiles("tt").size());

      log.debug("Initial:--> " + journalImpl.debug());

      log.debug("_______________________________");

      for (int i = 0; i < 50; i++) {
         journalImpl.appendAddRecord(i, (byte) 1, new SimpleEncoding(1, (byte) 'x'), false);
      }

      journalImpl.forceMoveNextFile();

      // as the request to a new file is asynchronous, we need to make sure the
      // async requests are done
      journalImpl.debugWait();

      Assert.assertEquals(3, factory.listFiles("tt").size());

      for (int i = 10; i < 50; i++) {
         journalImpl.appendDeleteRecord(i, false);
      }

      journalImpl.debugWait();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(10, records.size());

      Assert.assertEquals(3, factory.listFiles("tt").size());

   }

   @Test
   public void testAddAndDeleteReclaimWithoutTransactions() throws Exception {
      final int JOURNAL_SIZE = 10000;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      journalImpl.setAutoReclaim(false);

      journalImpl.checkReclaimStatus();

      journalImpl.debugWait();

      Assert.assertEquals(2, factory.listFiles("tt").size());

      log.debug("Initial:--> " + journalImpl.debug());

      log.debug("_______________________________");

      for (int i = 0; i < 50; i++) {
         journalImpl.appendAddRecord(i, (byte) 1, new SimpleEncoding(1, (byte) 'x'), false);
      }

      // as the request to a new file is asynchronous, we need to make sure the
      // async requests are done
      journalImpl.debugWait();

      Assert.assertEquals(2, factory.listFiles("tt").size());

      for (int i = 0; i < 50; i++) {
         journalImpl.appendDeleteRecord(i, false);
      }

      journalImpl.forceMoveNextFile();

      journalImpl.appendAddRecord(1000, (byte) 1, new SimpleEncoding(1, (byte) 'x'), false);

      journalImpl.debugWait();

      Assert.assertEquals(3, factory.listFiles("tt").size());

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(1, records.size());

      Assert.assertEquals(1000, records.get(0).id);

      journalImpl.checkReclaimStatus();

      log.debug(journalImpl.debug());

      journalImpl.debugWait();

      log.debug("Final:--> " + journalImpl.debug());

      log.debug("_______________________________");

      log.debug("Files bufferSize:" + factory.listFiles("tt").size());

      Assert.assertEquals(2, factory.listFiles("tt").size());

   }

   @Test
   public void testReloadWithTransaction() throws Exception {
      final int JOURNAL_SIZE = 2000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      journalImpl.appendAddRecordTransactional(1, 1, (byte) 1, new SimpleEncoding(1, (byte) 1));

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      try {
         journalImpl.appendCommitRecord(1L, true);
         // This was supposed to throw an exception, as the transaction was
         // forgotten (interrupted by a reload).
         Assert.fail("Supposed to throw exception");
      } catch (Exception e) {
         log.warn(e);
      }

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

   }

   @Test
   public void testReloadWithInterruptedTransaction() throws Exception {
      final int JOURNAL_SIZE = 1100;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      journalImpl.setAutoReclaim(false);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(77L, 1, (byte) 1, new SimpleEncoding(1, (byte) 1));
         journalImpl.forceMoveNextFile();
      }

      journalImpl.debugWait();

      Assert.assertEquals(12, factory.listFiles("tt").size());

      journalImpl.appendAddRecordTransactional(78L, 1, (byte) 1, new SimpleEncoding(1, (byte) 1));

      Assert.assertEquals(12, factory.listFiles("tt").size());

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());
      Assert.assertEquals(2, incompleteTransactions.size());
      Assert.assertEquals((Long) 77L, incompleteTransactions.get(0));
      Assert.assertEquals((Long) 78L, incompleteTransactions.get(1));

      try {
         journalImpl.appendCommitRecord(77L, true);
         // This was supposed to throw an exception, as the transaction was
         // forgotten (interrupted by a reload).
         Assert.fail("Supposed to throw exception");
      } catch (Exception e) {
         log.debug("Expected exception " + e, e);
      }

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      journalImpl.forceMoveNextFile();
      journalImpl.checkReclaimStatus();

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());
   }

   @Test
   public void testReloadWithCompletedTransaction() throws Exception {
      final int JOURNAL_SIZE = 2000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1, i, (byte) 1, new SimpleEncoding(1, (byte) 1));
         journalImpl.forceMoveNextFile();
      }

      journalImpl.appendCommitRecord(1L, false);

      journalImpl.debugWait();

      Assert.assertEquals(12, factory.listFiles("tt").size());

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(10, records.size());
      Assert.assertEquals(0, transactions.size());

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(10, journalImpl.getDataFilesCount());

      Assert.assertEquals(12, factory.listFiles("tt").size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendDeleteRecordTransactional(2L, i);
         journalImpl.forceMoveNextFile();
      }

      journalImpl.appendCommitRecord(2L, false);

      journalImpl.appendAddRecord(100, (byte) 1, new SimpleEncoding(5, (byte) 1), false);

      journalImpl.forceMoveNextFile();

      journalImpl.appendAddRecord(101, (byte) 1, new SimpleEncoding(5, (byte) 1), false);

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(1, journalImpl.getDataFilesCount());

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(1, journalImpl.getDataFilesCount());

      Assert.assertEquals(3, factory.listFiles("tt").size());
   }

   @Test
   public void testTotalSize() throws Exception {
      final int JOURNAL_SIZE = 2000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      journalImpl.appendAddRecordTransactional(1L, 2L, (byte) 3, new SimpleEncoding(1900 - JournalImpl.SIZE_ADD_RECORD_TX - 1, (byte) 4));

      journalImpl.appendCommitRecord(1L, false);

      journalImpl.debugWait();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(1, records.size());

   }

   @Test
   public void testReloadInvalidCheckSizeOnTransaction() throws Exception {
      final int JOURNAL_SIZE = 2000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(2, factory.listFiles("tt").size());

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 2; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 15));
      }

      journalImpl.appendCommitRecord(1L, false);

      journalImpl.debugWait();

      log.debug("Files = " + factory.listFiles("tt"));

      SequentialFile file = factory.createSequentialFile("tt-1.tt");

      file.open();

      ByteBuffer buffer = ByteBuffer.allocate(100);

      // Messing up with the first record (removing the position)
      file.position(100);

      file.read(buffer);

      // jumping RecordType, FileId, TransactionID, RecordID, VariableSize,
      // RecordType, RecordBody (that we know it is 1 )
      buffer.position(1 + 4 + 8 + 8 + 4 + 1 + 1 + 1);

      int posCheckSize = buffer.position();

      Assert.assertEquals(JournalImpl.SIZE_ADD_RECORD_TX + 2, buffer.getInt());

      buffer.position(posCheckSize);

      buffer.putInt(-1);

      buffer.rewind();

      // Changing the check bufferSize, so reload will ignore this record
      file.position(100);

      file.writeDirect(buffer, true);

      file.close();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(0, journalImpl.getDataFilesCount());

      Assert.assertEquals(2, factory.listFiles("tt").size());

   }

   @Test
   public void testPartiallyBrokenFile() throws Exception {
      final int JOURNAL_SIZE = 20000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(2, factory.listFiles("tt").size());

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 20; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 15));
         journalImpl.appendAddRecordTransactional(2L, i + 20L, (byte) 0, new SimpleEncoding(1, (byte) 15));
      }

      journalImpl.appendCommitRecord(1L, false);

      journalImpl.appendCommitRecord(2L, false);

      journalImpl.debugWait();

      SequentialFile file = factory.createSequentialFile("tt-1.tt");

      file.open();

      ByteBuffer buffer = ByteBuffer.allocate(100);

      // Messing up with the first record (removing the position)
      file.position(100);

      file.read(buffer);

      // jumping RecordType, FileId, TransactionID, RecordID, VariableSize,
      // RecordType, RecordBody (that we know it is 1 )
      buffer.position(1 + 4 + 8 + 8 + 4 + 1 + 1 + 1);

      int posCheckSize = buffer.position();

      Assert.assertEquals(JournalImpl.SIZE_ADD_RECORD_TX + 2, buffer.getInt());

      buffer.position(posCheckSize);

      buffer.putInt(-1);

      buffer.rewind();

      // Changing the check bufferSize, so reload will ignore this record
      file.position(100);

      file.writeDirect(buffer, true);

      file.close();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(20, records.size());

      journalImpl.checkReclaimStatus();

   }

   @Test
   public void testReduceFreeFiles() throws Exception {
      final int JOURNAL_SIZE = 2000;

      setupAndLoadJournal(JOURNAL_SIZE, 100, 10);

      Assert.assertEquals(10, factory.listFiles("tt").size());

      setupAndLoadJournal(JOURNAL_SIZE, 100, 2);

      Assert.assertEquals(10, factory.listFiles("tt").size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecord(i, (byte) 0, new SimpleEncoding(1, (byte) 0), false);
         journalImpl.forceMoveNextFile();
      }

      setupAndLoadJournal(JOURNAL_SIZE, 100, 2);

      Assert.assertEquals(10, records.size());

      Assert.assertEquals(12, factory.listFiles("tt").size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendDeleteRecord(i, false);
      }

      journalImpl.forceMoveNextFile();

      journalImpl.checkReclaimStatus();

      setupAndLoadJournal(JOURNAL_SIZE, 100, 2);

      Assert.assertEquals(0, records.size());

      Assert.assertEquals(2, factory.listFiles("tt").size());
   }

   @Test
   public void testReloadIncompleteTransaction() throws Exception {
      final int JOURNAL_SIZE = 2000;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(2, factory.listFiles("tt").size());

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 15));
      }

      for (int i = 10; i < 20; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 15));
      }

      journalImpl.appendCommitRecord(1L, false);

      journalImpl.debugWait();

      SequentialFile file = factory.createSequentialFile("tt-1.tt");

      file.open();

      ByteBuffer buffer = ByteBuffer.allocate(100);

      // Messing up with the first record (removing the position)
      file.position(100);

      file.read(buffer);

      buffer.position(1);

      buffer.putInt(-1);

      buffer.rewind();

      // Messing up with the first record (changing the fileID, so Journal
      // reload will think the record came from a different journal usage)
      file.position(100);

      buffer.rewind();
      file.writeDirect(buffer, true);

      file.close();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(0, journalImpl.getDataFilesCount());

      Assert.assertEquals(2, factory.listFiles("tt").size());

   }

   @Test
   public void testPrepareAloneOnSeparatedFile() throws Exception {
      final int JOURNAL_SIZE = 20000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 15));
      }

      journalImpl.forceMoveNextFile();
      SimpleEncoding xidEncoding = new SimpleEncoding(10, (byte) 'a');

      journalImpl.appendPrepareRecord(1L, xidEncoding, false);
      journalImpl.appendCommitRecord(1L, false);

      for (int i = 0; i < 10; i++) {
         journalImpl.appendDeleteRecordTransactional(2L, i);
      }

      journalImpl.appendCommitRecord(2L, false);
      journalImpl.appendAddRecord(100L, (byte) 0, new SimpleEncoding(1, (byte) 10), false); // Add
      // anything
      // to
      // keep
      // holding
      // the
      // file
      journalImpl.forceMoveNextFile();
      journalImpl.checkReclaimStatus();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(1, records.size());
   }

   @Test
   public void testCommitWithMultipleFiles() throws Exception {
      final int JOURNAL_SIZE = 20000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 50; i++) {
         if (i == 10) {
            journalImpl.forceMoveNextFile();
         }
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 15));
      }

      journalImpl.appendCommitRecord(1L, false);

      for (int i = 0; i < 10; i++) {
         if (i == 5) {
            journalImpl.forceMoveNextFile();
         }
         journalImpl.appendDeleteRecordTransactional(2L, i);
      }

      journalImpl.appendCommitRecord(2L, false);
      journalImpl.forceMoveNextFile();
      journalImpl.checkReclaimStatus();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(40, records.size());

   }

   @Test
   public void testSimplePrepare() throws Exception {
      final int JOURNAL_SIZE = 3 * 1024;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      SimpleEncoding xid = new SimpleEncoding(10, (byte) 1);

      journalImpl.appendAddRecord(10L, (byte) 0, new SimpleEncoding(10, (byte) 0), false);

      journalImpl.appendDeleteRecordTransactional(1L, 10L, new SimpleEncoding(100, (byte) 'j'));

      journalImpl.appendPrepareRecord(1, xid, false);

      journalImpl.debugWait();

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(1, transactions.size());
      Assert.assertEquals(1, transactions.get(0).getRecordsToDelete().size());
      Assert.assertEquals(1, records.size());

      for (RecordInfo record : transactions.get(0).getRecordsToDelete()) {
         byte[] data = record.data;
         Assert.assertEquals(100, data.length);
         for (byte element : data) {
            Assert.assertEquals((byte) 'j', element);
         }
      }

      Assert.assertEquals(10, transactions.get(0).getExtraData().length);

      for (int i = 0; i < 10; i++) {
         Assert.assertEquals((byte) 1, transactions.get(0).getExtraData()[i]);
      }

      journalImpl.appendCommitRecord(1L, false);

      journalImpl.debugWait();

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(0, transactions.size());
      Assert.assertEquals(0, records.size());

   }

   @Test
   public void testReloadWithPreparedTransaction() throws Exception {
      final int JOURNAL_SIZE = 3 * 1024;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1, i, (byte) 1, new SimpleEncoding(50, (byte) 1));
         journalImpl.forceMoveNextFile();
      }

      journalImpl.debugWait();

      SimpleEncoding xid1 = new SimpleEncoding(10, (byte) 1);

      journalImpl.appendPrepareRecord(1L, xid1, false);

      Assert.assertEquals(12, factory.listFiles("tt").size());

      setupAndLoadJournal(JOURNAL_SIZE, 1024);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(1, transactions.size());

      Assert.assertEquals(10, transactions.get(0).getExtraData().length);
      for (int i = 0; i < 10; i++) {
         Assert.assertEquals((byte) 1, transactions.get(0).getExtraData()[i]);
      }

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(10, journalImpl.getDataFilesCount());

      Assert.assertEquals(12, factory.listFiles("tt").size());

      journalImpl.appendCommitRecord(1L, false);

      setupAndLoadJournal(JOURNAL_SIZE, 1024);

      Assert.assertEquals(10, records.size());

      journalImpl.checkReclaimStatus();

      for (int i = 0; i < 10; i++) {
         journalImpl.appendDeleteRecordTransactional(2L, i);
      }

      SimpleEncoding xid2 = new SimpleEncoding(15, (byte) 2);

      journalImpl.appendPrepareRecord(2L, xid2, false);

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(1, transactions.size());

      Assert.assertEquals(15, transactions.get(0).getExtraData().length);

      for (byte element : transactions.get(0).getExtraData()) {
         Assert.assertEquals(2, element);
      }

      Assert.assertEquals(10, journalImpl.getDataFilesCount());

      Assert.assertEquals(12, factory.listFiles("tt").size());

      journalImpl.appendCommitRecord(2L, false);

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      journalImpl.forceMoveNextFile();

      // Reclaiming should still be able to reclaim a file if a transaction was ignored
      journalImpl.checkReclaimStatus();
      journalImpl.flush();

   }

   @Test
   public void testReloadInvalidPrepared() throws Exception {
      final int JOURNAL_SIZE = 3000;

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1, i, (byte) 1, new SimpleEncoding(50, (byte) 1));
      }

      journalImpl.appendPrepareRecord(1L, new SimpleEncoding(13, (byte) 0), false);

      setupAndLoadJournal(JOURNAL_SIZE, 100);
      Assert.assertEquals(0, records.size());
      Assert.assertEquals(1, transactions.size());

      SequentialFile file = factory.createSequentialFile("tt-1.tt");

      file.open();

      ByteBuffer buffer = ByteBuffer.allocate(100);

      // Messing up with the first record (removing the position)
      file.position(100);

      file.read(buffer);

      buffer.position(1);

      buffer.putInt(-1);

      buffer.rewind();

      // Messing up with the first record (changing the fileID, so Journal
      // reload will think the record came from a different journal usage)
      file.position(100);

      file.writeDirect(buffer, true);

      file.close();

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());
   }

   @Test
   public void testReclaimAfterRollabck() throws Exception {
      final int JOURNAL_SIZE = 2000;
      final int COUNT = 10;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      for (int i = 0; i < COUNT; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 0));
         journalImpl.forceMoveNextFile();
      }

      journalImpl.appendRollbackRecord(1L, false);

      journalImpl.forceMoveNextFile();

      // wait for the previous call to forceMoveNextFile() to complete
      assertTrue(Wait.waitFor(() -> factory.listFiles("tt").size() == COUNT + 3, 2000, 50));

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(0, journalImpl.getDataFilesCount());

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(0, journalImpl.getDataFilesCount());

      Assert.assertEquals(2, factory.listFiles("tt").size());

   }

   // It should be ok to write records on AIO, and later read then on NIO
   @Test
   public void testDecreaseAlignment() throws Exception {
      final int JOURNAL_SIZE = 512 * 4;

      setupAndLoadJournal(JOURNAL_SIZE, 512);

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 0));
      }

      journalImpl.appendCommitRecord(1L, false);

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(10, records.size());

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(10, records.size());
   }

   // It should be ok to write records on NIO, and later read then on AIO
   @Test
   public void testIncreaseAlignment() throws Exception {
      final int JOURNAL_SIZE = 512 * 4;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      for (int i = 0; i < 10; i++) {
         journalImpl.appendAddRecordTransactional(1L, i, (byte) 0, new SimpleEncoding(1, (byte) 0));
      }

      journalImpl.appendCommitRecord(1L, false);

      setupAndLoadJournal(JOURNAL_SIZE, 100);

      Assert.assertEquals(10, records.size());

      setupAndLoadJournal(JOURNAL_SIZE, 512);

      Assert.assertEquals(10, records.size());
   }

   @Test
   public void testEmptyPrepare() throws Exception {
      final int JOURNAL_SIZE = 512 * 4;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      journalImpl.appendPrepareRecord(2L, new SimpleEncoding(10, (byte) 'j'), false);

      journalImpl.forceMoveNextFile();

      journalImpl.appendAddRecord(1L, (byte) 0, new SimpleEncoding(10, (byte) 'k'), false);

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(1, journalImpl.getDataFilesCount());

      Assert.assertEquals(1, transactions.size());

      journalImpl.forceMoveNextFile();

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(1, journalImpl.getDataFilesCount());

      Assert.assertEquals(1, transactions.size());

      journalImpl.appendCommitRecord(2L, false);

      journalImpl.appendDeleteRecord(1L, false);

      journalImpl.forceMoveNextFile();

      setupAndLoadJournal(JOURNAL_SIZE, 0);

      journalImpl.forceMoveNextFile();
      journalImpl.debugWait();
      journalImpl.checkReclaimStatus();

      Assert.assertEquals(0, transactions.size());
      Assert.assertEquals(0, journalImpl.getDataFilesCount());

   }

   @Test
   public void testReclaimingAfterConcurrentAddsAndDeletesTx() throws Exception {
      testReclaimingAfterConcurrentAddsAndDeletes(true);
   }

   @Test
   public void testReclaimingAfterConcurrentAddsAndDeletesNonTx() throws Exception {
      testReclaimingAfterConcurrentAddsAndDeletes(false);
   }

   public void testReclaimingAfterConcurrentAddsAndDeletes(final boolean transactional) throws Exception {
      final int JOURNAL_SIZE = 10 * 1024;

      setupAndLoadJournal(JOURNAL_SIZE, 1);

      Assert.assertEquals(0, records.size());
      Assert.assertEquals(0, transactions.size());

      final CountDownLatch latchReady = new CountDownLatch(2);
      final CountDownLatch latchStart = new CountDownLatch(1);
      final AtomicInteger finishedOK = new AtomicInteger(0);
      final BlockingQueue<Integer> queueDelete = new LinkedBlockingQueue<>();

      final int NUMBER_OF_ELEMENTS = 500;

      Thread t1 = new Thread() {
         @Override
         public void run() {
            try {
               latchReady.countDown();
               ActiveMQTestBase.waitForLatch(latchStart);
               for (int i = 0; i < NUMBER_OF_ELEMENTS; i++) {

                  if (transactional) {
                     journalImpl.appendAddRecordTransactional(i, i, (byte) 1, new SimpleEncoding(50, (byte) 1));
                     journalImpl.appendCommitRecord(i, false);
                  } else {
                     journalImpl.appendAddRecord(i, (byte) 1, new SimpleEncoding(50, (byte) 1), false);
                  }

                  queueDelete.offer(i);
               }
               finishedOK.incrementAndGet();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      Thread t2 = new Thread() {
         @Override
         public void run() {
            try {
               latchReady.countDown();
               ActiveMQTestBase.waitForLatch(latchStart);
               for (int i = 0; i < NUMBER_OF_ELEMENTS; i++) {
                  Integer toDelete = queueDelete.poll(10, TimeUnit.SECONDS);
                  if (toDelete == null) {
                     break;
                  }

                  if (transactional) {
                     journalImpl.appendDeleteRecordTransactional(toDelete, toDelete, new SimpleEncoding(50, (byte) 1));
                     journalImpl.appendCommitRecord(i, false);
                  } else {
                     journalImpl.appendDeleteRecord(toDelete, false);
                  }

               }
               finishedOK.incrementAndGet();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      t1.start();
      t2.start();

      ActiveMQTestBase.waitForLatch(latchReady);
      latchStart.countDown();

      t1.join();
      t2.join();

      Assert.assertEquals(2, finishedOK.intValue());

      journalImpl.debugWait();

      journalImpl.forceMoveNextFile();

      journalImpl.debugWait();

      journalImpl.checkReclaimStatus();

      Assert.assertEquals(0, journalImpl.getDataFilesCount());

      Assert.assertEquals(2, factory.listFiles("tt").size());

   }

   @Test
   public void testAlignmentOverReload() throws Exception {

      factory = new FakeSequentialFileFactory(512, false);
      journalImpl = new JournalImpl(512 + 512 * 3, 20, 20, 0, 0, factory, "amq", "amq", 1000);

      journalImpl.start();

      journalImpl.load(AlignedJournalImplTest.dummyLoader);

      journalImpl.appendAddRecord(1L, (byte) 0, new SimpleEncoding(100, (byte) 'a'), false);
      journalImpl.appendAddRecord(2L, (byte) 0, new SimpleEncoding(100, (byte) 'b'), false);
      journalImpl.appendAddRecord(3L, (byte) 0, new SimpleEncoding(100, (byte) 'b'), false);
      journalImpl.appendAddRecord(4L, (byte) 0, new SimpleEncoding(100, (byte) 'b'), false);

      journalImpl.stop();

      journalImpl = new JournalImpl(512 + 1024 + 512, 20, 20, 0, 0, factory, "amq", "amq", 1000);
      addActiveMQComponent(journalImpl);
      journalImpl.start();
      journalImpl.load(AlignedJournalImplTest.dummyLoader);

      // It looks silly, but this forceMoveNextFile is in place to replicate one
      // specific bug caught during development
      journalImpl.forceMoveNextFile();

      journalImpl.appendDeleteRecord(1L, false);
      journalImpl.appendDeleteRecord(2L, false);
      journalImpl.appendDeleteRecord(3L, false);
      journalImpl.appendDeleteRecord(4L, false);

      journalImpl.stop();

      journalImpl = new JournalImpl(512 + 1024 + 512, 20, 20, 0, 0, factory, "amq", "amq", 1000);
      addActiveMQComponent(journalImpl);
      journalImpl.start();

      ArrayList<RecordInfo> info = new ArrayList<>();
      ArrayList<PreparedTransactionInfo> trans = new ArrayList<>();

      journalImpl.load(info, trans, null);

      Assert.assertEquals(0, info.size());
      Assert.assertEquals(0, trans.size());

   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      records = new ArrayList<>();

      transactions = new ArrayList<>();

      incompleteTransactions = new ArrayList<>();

      factory = null;

      journalImpl = null;

   }

   @Override
   @After
   public void tearDown() throws Exception {
      stopComponent(journalImpl);
      if (factory != null)
         factory.stop();
      records = null;

      transactions = null;

      incompleteTransactions = null;

      factory = null;

      journalImpl = null;

      super.tearDown();
   }


   private void setupAndLoadJournal(final int journalSize, final int alignment) throws Exception {
      setupAndLoadJournal(journalSize, alignment, 2);
   }

   private void setupAndLoadJournal(final int journalSize,
                                    final int alignment,
                                    final int numberOfMinimalFiles) throws Exception {
      if (factory == null) {
         factory = new FakeSequentialFileFactory(alignment, true);
      }

      if (journalImpl != null) {
         journalImpl.stop();
      }

      journalImpl = new JournalImpl(journalSize, numberOfMinimalFiles, numberOfMinimalFiles, 0, 0, factory, "tt", "tt", 1000);
      addActiveMQComponent(journalImpl);
      journalImpl.start();

      records.clear();
      transactions.clear();
      incompleteTransactions.clear();

      journalImpl.load(records, transactions, new TransactionFailureCallback() {
         @Override
         public void failedTransaction(final long transactionID,
                                       final List<RecordInfo> records,
                                       final List<RecordInfo> recordsToDelete) {
            log.debug("records.length = " + records.size());
            incompleteTransactions.add(transactionID);
         }

      });
   }


}
