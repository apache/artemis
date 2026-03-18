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
package org.apache.activemq.artemis.core.journal.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQUnsupportedPacketException;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.JournalUpdateCallback;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalAddRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalAddRecordTX;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalCompleteRecordTX;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalCompleteRecordTX.TX_RECORD_TYPE;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalDeleteRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalDeleteRecordTX;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalInternalRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalRollbackRecordTX;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.apache.activemq.artemis.utils.collections.SparseArrayLinkedList;

/**
 * Journal used at a replicating backup server during the synchronization of data with the 'live' server. It just wraps
 * a single {@link JournalFile}.
 * <p>
 * Its main purpose is to store the data as a Journal would, but without verifying records.
 */
public final class FileWrapperJournal extends JournalBase {

   private final ReentrantLock lockAppend = new ReentrantLock();

   private final ConcurrentLongHashMap<AtomicInteger> transactions = new ConcurrentLongHashMap<>();
   private final JournalImpl journal;
   protected volatile JournalFile currentFile;

   @Override
   public void replaceableRecord(byte recordType) {
      journal.replaceableRecord(recordType);
   }

   public FileWrapperJournal(Journal journal) throws Exception {
      super(journal.getFileFactory().isSupportsCallbacks(), journal.getFileSize());
      this.journal = (JournalImpl) journal;
      currentFile = this.journal.setUpCurrentFile(JournalImpl.SIZE_HEADER);
   }

   @Override
   public void start() throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public void stop() throws Exception {
      if (currentFile.getFile().isOpen()) {
         currentFile.getFile().close();
      }
   }

   @Override
   public boolean isStarted() {
      throw new UnsupportedOperationException();
   }

   // ------------------------

   // ------------------------

   @Override
   public void appendAddRecord(long id,
                               byte recordType,
                               Persister persister,
                               Object record,
                               boolean sync,
                               IOCompletion callback) throws Exception {
      JournalInternalRecord addRecord = new JournalAddRecord(true, id, recordType, persister, record);
      writeRecord(addRecord, false, -1, false, callback);
   }

   @Override
   public void appendAddEvent(long id,
                              byte recordType,
                              Persister persister,
                              Object record,
                              boolean sync,
                              IOCompletion callback) throws Exception {

      JournalInternalRecord addRecord = new JournalAddRecord(JournalImpl.EVENT_RECORD, id, recordType, persister, record);
      writeRecord(addRecord, false, -1, false, callback);
   }

   @Override
   public void flush() throws Exception {
   }

   /**
    * The max size record that can be stored in the journal
    */
   @Override
   public long getMaxRecordSize() {
      return journal.getMaxRecordSize();
   }

   @Override
   public long getWarningRecordSize() {
      return journal.getWarningRecordSize();
   }
   /**
    * Write the record to the current file.
    */
   private void writeRecord(JournalInternalRecord encoder,
                            final boolean tx,
                            final long txID,
                            final boolean removeTX,
                            final IOCompletion callback) throws Exception {

      lockAppend.lock();
      try {
         if (callback != null) {
            callback.storeLineUp();
         }
         testSwitchFiles(encoder);
         if (txID >= 0) {
            if (tx) {
               AtomicInteger value;
               if (removeTX) {
                  value = transactions.remove(txID);
               } else {
                  value = transactions.get(txID);
               }
               if (value != null) {
                  encoder.setNumberOfRecords(value.get());
               }
            } else {
               count(txID);
            }
         }
         encoder.setFileID(currentFile.getRecordID());

         if (callback != null) {
            currentFile.getFile().write(encoder, false, callback);
         } else {
            currentFile.getFile().write(encoder, false);
         }
      } finally {
         lockAppend.unlock();
      }
   }

   private void testSwitchFiles(JournalInternalRecord encoder) throws Exception {
      JournalFile oldFile = currentFile;
      currentFile = journal.switchFileIfNecessary(encoder.getEncodeSize());
      if (oldFile != currentFile) {
         for (AtomicInteger value : transactions.values()) {
            value.set(0);
         }
      }
   }

   @Override
   public void appendDeleteRecord(long id, boolean sync, IOCompletion callback) throws Exception {
      JournalInternalRecord deleteRecord = new JournalDeleteRecord(id);
      writeRecord(deleteRecord, false, -1, false, callback);
   }


   @Override
   public void tryAppendDeleteRecord(long id, boolean sync, JournalUpdateCallback updateCallback, IOCompletion callback) throws Exception {
      appendDeleteRecord(id, sync, callback);
   }

   @Override
   public void appendDeleteRecordTransactional(long txID, long id, EncodingSupport record) throws Exception {
      JournalInternalRecord deleteRecordTX = new JournalDeleteRecordTX(txID, id, record);
      writeRecord(deleteRecordTX, false, txID, false, null);
   }

   @Override
   public void appendAddRecordTransactional(long txID,
                                            long id,
                                            byte recordType,
                                            Persister persister,
                                            Object record) throws Exception {
      JournalInternalRecord addRecord = new JournalAddRecordTX(true, txID, id, recordType, persister, record);
      writeRecord(addRecord, false, txID, false, null);
   }

   @Override
   public void appendUpdateRecord(long id,
                                  byte recordType,
                                  Persister persister,
                                  Object record,
                                  boolean sync,
                                  IOCompletion callback) throws Exception {
      JournalInternalRecord updateRecord = new JournalAddRecord(false, id, recordType, persister, record);
      writeRecord(updateRecord, false, -1, false, callback);
   }

   @Override
   public void tryAppendUpdateRecord(long id,
                                     byte recordType,
                                     Persister persister,
                                     Object record,
                                     boolean sync,
                                     boolean replaceableUpdate,
                                     JournalUpdateCallback updateCallback,
                                     IOCompletion callback) throws Exception {
      JournalInternalRecord updateRecord = new JournalAddRecord(false, id, recordType, persister, record);
      writeRecord(updateRecord, false, -1, false, callback);
   }

   @Override
   public void appendUpdateRecordTransactional(long txID,
                                               long id,
                                               byte recordType,
                                               Persister persister,
                                               Object record) throws Exception {
      JournalInternalRecord updateRecordTX = new JournalAddRecordTX(false, txID, id, recordType, persister, record);
      writeRecord(updateRecordTX, false, txID, false, null);
   }

   @Override
   public void appendCommitRecord(long txID,
                                  boolean sync,
                                  IOCompletion callback,
                                  boolean lineUpContext) throws Exception {
      JournalInternalRecord commitRecord = new JournalCompleteRecordTX(TX_RECORD_TYPE.COMMIT, txID, null);

      writeRecord(commitRecord, true, txID, true, callback);
   }

   @Override
   public void appendPrepareRecord(long txID,
                                   EncodingSupport transactionData,
                                   boolean sync,
                                   IOCompletion callback) throws Exception {
      JournalInternalRecord prepareRecord = new JournalCompleteRecordTX(TX_RECORD_TYPE.PREPARE, txID, transactionData);
      writeRecord(prepareRecord, true, txID, false, callback);
   }

   private int count(long txID) throws ActiveMQException {
      AtomicInteger defaultValue = new AtomicInteger(1);
      AtomicInteger count = transactions.putIfAbsent(txID, defaultValue);
      if (count != null) {
         count.incrementAndGet();
      } else {
         count = defaultValue;
      }
      return count.intValue();
   }

   @Override
   public String toString() {
      return FileWrapperJournal.class.getName() + "(currentFile=[" + currentFile + "], hash=" + super.toString() + ")";
   }

   @Override
   public void appendRollbackRecord(long txID, boolean sync, IOCompletion callback) throws Exception {
      JournalInternalRecord rollbackRecord = new JournalRollbackRecordTX(txID);
      writeRecord(rollbackRecord, true, txID, true, callback);
   }

   // UNSUPPORTED STUFF

   @Override
   public JournalLoadInformation load(LoaderCallback reloadManager) throws Exception {
      throw new ActiveMQUnsupportedPacketException();
   }

   @Override
   public JournalLoadInformation loadInternalOnly() throws Exception {
      throw new ActiveMQUnsupportedPacketException();
   }

   @Override
   public void lineUpContext(IOCompletion callback) {
      throw new UnsupportedOperationException();
   }

   @Override
   public JournalLoadInformation load(List<RecordInfo> committedRecords,
                                      List<PreparedTransactionInfo> preparedTransactions,
                                      TransactionFailureCallback transactionFailure,
                                      boolean fixbadtx) throws Exception {
      throw new ActiveMQUnsupportedPacketException();
   }

   @Override
   public JournalLoadInformation load(SparseArrayLinkedList<RecordInfo> committedRecords,
                                      List<PreparedTransactionInfo> preparedTransactions,
                                      TransactionFailureCallback transactionFailure,
                                      boolean fixbadtx) throws Exception {
      throw new ActiveMQUnsupportedPacketException();
   }

   @Override
   public int getAlignment() throws Exception {
      throw new ActiveMQUnsupportedPacketException();
   }

   @Override
   public int getNumberOfRecords() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int getUserVersion() {
      throw new UnsupportedOperationException();
   }

   @Override
   public JournalLoadInformation loadSyncOnly(JournalState state) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public Map<Long, JournalFile> createFilesForBackupSync(long[] fileIds) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public void synchronizationLock() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void synchronizationUnlock() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void forceMoveNextFile() {
      throw new UnsupportedOperationException();
   }

   @Override
   public JournalFile[] getDataFiles() {
      throw new UnsupportedOperationException();
   }

   @Override
   void scheduleReclaim() {
      // no-op
   }

   @Override
   public SequentialFileFactory getFileFactory() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void scheduleCompactAndBlock(int timeout) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public void replicationSyncPreserveOldFiles() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void replicationSyncFinished() {
      throw new UnsupportedOperationException();
   }
}
