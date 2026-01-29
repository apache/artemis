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

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncoderPersister;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalAddRecord;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class JournalCompactSplitTest extends ActiveMQTestBase {
   private static final long RECORDS_TO_CREATE = 100;

   private static final int JOURNAL_FILE_SIZE = 100 * 1024;

   private static final int BODY_SIZE = 124;

   @Test
   public void testJournalSplit() throws Exception {

      SequentialFileFactory fileFactory = new NIOSequentialFileFactory(new File(getTemporaryDir()), 1);

      createFileWithRecords(fileFactory);

      createAndCompactJournal(fileFactory);
   }

   private void createAndCompactJournal(SequentialFileFactory fileFactory) throws Exception {
      final int minFiles = 2;
      final int compactMinFiles = 2;
      final int compactPercentage = 30;
      final String filePrefix = "artemis-data";
      final String fileExtension = "amq";
      final int maxAIO = 1024;
      JournalImpl journalImpl = new JournalImpl(JOURNAL_FILE_SIZE, minFiles, 2, compactMinFiles, compactPercentage, fileFactory, filePrefix, fileExtension, maxAIO, 1);

      AtomicInteger recordCount = new AtomicInteger(0);
      journalImpl.start();
      runAfter(journalImpl::stop);
      journalImpl.load(new LoaderCallback() {
         @Override
         public void addPreparedTransaction(PreparedTransactionInfo preparedTransaction) {

         }

         @Override
         public void addRecord(RecordInfo info) {
            recordCount.incrementAndGet();
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

      assertEquals(RECORDS_TO_CREATE, recordCount.get());

      journalImpl.compact();
      assertEquals(2, journalImpl.getDataFilesCount());
   }

   private void createFileWithRecords(SequentialFileFactory fileFactory) throws Exception {
      SequentialFile file = fileFactory.createSequentialFile("artemis-data-1.amq", JOURNAL_FILE_SIZE);
      file.open();
      file.fill(JOURNAL_FILE_SIZE);
      file.position(0);
      ByteBuffer bb = fileFactory.newBuffer(JournalImpl.SIZE_HEADER);
      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bb);
      JournalImpl.writeHeader(buffer, 1, 1);
      file.write(buffer, true);

      byte[] z = new byte[BODY_SIZE];
      for (int i = 0; i < BODY_SIZE; i++) {
         z[i] = 'z';
      }
      short compactCount = 10;

      for (long i = 0; i < RECORDS_TO_CREATE; i++) {
         JournalAddRecord record = new JournalAddRecord(true, i, JournalImpl.ADD_RECORD, EncoderPersister.getInstance(), new ByteArrayEncoding(z));
         record.setFileID(1);
         compactCount--;
         if (compactCount < 0) compactCount = 10;

         // this test is playing with the order of compact count
         record.setCompactCount(compactCount);
         file.write(record, false);
      }
      file.close();
   }


}
