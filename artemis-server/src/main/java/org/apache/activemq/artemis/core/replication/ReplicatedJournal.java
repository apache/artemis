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
package org.apache.activemq.artemis.core.replication;

import java.util.List;
import java.util.Map;

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
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.replication.ReplicationManager.ADD_OPERATION_TYPE;
import org.apache.activemq.artemis.utils.collections.SparseArrayLinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Used by the {@link org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager} to replicate
 * journal calls.
 * <p>
 * This class wraps a {@link ReplicationManager} and the local {@link Journal}. Every call will be relayed to both
 * instances.
 *
 * @see org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager
 */
public class ReplicatedJournal implements Journal {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ReplicationManager replicationManager;

   private final Journal localJournal;

   private final byte journalID;

   @Override
   public boolean isHistory() {
      return localJournal.isHistory();
   }

   @Override
   public void setRemoveExtraFilesOnLoad(boolean removeExtraFilesOnLoad) {
      this.localJournal.setRemoveExtraFilesOnLoad(removeExtraFilesOnLoad);
   }

   @Override
   public boolean isRemoveExtraFilesOnLoad() {
      return localJournal.isRemoveExtraFilesOnLoad();
   }

   public ReplicatedJournal(final byte journalID,
                            final Journal localJournal,
                            final ReplicationManager replicationManager) {
      super();
      this.journalID = journalID;
      this.localJournal = localJournal;
      this.replicationManager = replicationManager;
   }

   @Override
   public void flush() throws Exception {

   }

   public Journal getLocalJournal() {
      return localJournal;
   }

   @Override
   public void appendAddRecord(final long id,
                               final byte recordType,
                               final byte[] record,
                               final boolean sync) throws Exception {
      this.appendAddRecord(id, recordType, new ByteArrayEncoding(record), sync);
   }

   @Override
   public void appendAddRecord(final long id,
                               final byte recordType,
                               Persister persister,
                               final Object record,
                               final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Append record id = {} recordType = {}", id, recordType);
      }
      localJournal.appendAddRecord(id, recordType, persister, record, sync);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.ADD, id, recordType, persister, record);
   }

   @Override
   public void appendAddRecord(final long id,
                               final byte recordType,
                               Persister persister,
                               final Object record,
                               final boolean sync,
                               final IOCompletion completionCallback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Append record id = {} recordType = {}", id, recordType);
      }
      localJournal.appendAddRecord(id, recordType, persister, record, sync, completionCallback);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.ADD, id, recordType, persister, record);
   }

   @Override
   public void appendAddEvent(long id,
                       byte recordType,
                       Persister persister,
                       Object record,
                       boolean sync,
                       IOCompletion completionCallback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Append record id = {} recordType = {}", id, recordType);
      }
      localJournal.appendAddEvent(id, recordType, persister, record, sync, completionCallback);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.EVENT, id, recordType, persister, record);
   }

   @Override
   public void appendAddRecordTransactional(final long txID,
                                            final long id,
                                            final byte recordType,
                                            final byte[] record) throws Exception {
      this.appendAddRecordTransactional(txID, id, recordType, new ByteArrayEncoding(record));
   }

   @Override
   public void appendAddRecordTransactional(final long txID,
                                            final long id,
                                            final byte recordType,
                                            final Persister persister,
                                            final Object record) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Append record txID={} recordType = {}", id, recordType);
      }
      localJournal.appendAddRecordTransactional(txID, id, recordType, persister, record);
      replicationManager.appendAddRecordTransactional(journalID, ADD_OPERATION_TYPE.ADD, txID, id, recordType, persister, record);
   }

   @Override
   public void appendCommitRecord(final long txID, final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendCommit txID={}", txID);
      }
      localJournal.appendCommitRecord(txID, sync);
      replicationManager.appendCommitRecord(journalID, txID, sync, true);
   }

   @Override
   public void appendCommitRecord(final long txID, final boolean sync, final IOCompletion callback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendCommit {}", txID);
      }
      localJournal.appendCommitRecord(txID, sync, callback);
      replicationManager.appendCommitRecord(journalID, txID, sync, true);
   }

   @Override
   public void appendCommitRecord(long txID,
                                  boolean sync,
                                  IOCompletion callback,
                                  boolean lineUpContext) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendCommit {}", txID);
      }
      localJournal.appendCommitRecord(txID, sync, callback, lineUpContext);
      replicationManager.appendCommitRecord(journalID, txID, sync, lineUpContext);
   }

   @Override
   public void appendDeleteRecord(final long id, final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendDelete {}", id);
      }
      localJournal.appendDeleteRecord(id, sync);
      replicationManager.appendDeleteRecord(journalID, id);
   }

   @Override
   public void tryAppendDeleteRecord(final long id, final JournalUpdateCallback updateCallback, final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendDelete {}", id);
      }
      localJournal.tryAppendDeleteRecord(id, updateCallback, sync);
      replicationManager.appendDeleteRecord(journalID, id);
   }

   @Override
   public void appendDeleteRecord(final long id,
                                  final boolean sync,
                                  final IOCompletion completionCallback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendDelete {}", id);
      }
      localJournal.appendDeleteRecord(id, sync, completionCallback);
      replicationManager.appendDeleteRecord(journalID, id);
   }

   @Override
   public void tryAppendDeleteRecord(final long id,
                                  final boolean sync,
                                  final JournalUpdateCallback updateCallback,
                                  final IOCompletion completionCallback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendDelete {}", id);
      }
      localJournal.tryAppendDeleteRecord(id, sync, updateCallback, completionCallback);
      replicationManager.appendDeleteRecord(journalID, id);
   }

   @Override
   public void appendDeleteRecordTransactional(final long txID, final long id, final byte[] record) throws Exception {
      this.appendDeleteRecordTransactional(txID, id, new ByteArrayEncoding(record));
   }

   @Override
   public void appendDeleteRecordTransactional(final long txID,
                                               final long id,
                                               final EncodingSupport record) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendDelete txID={} id={}", txID, id);
      }
      localJournal.appendDeleteRecordTransactional(txID, id, record);
      replicationManager.appendDeleteRecordTransactional(journalID, txID, id, record);
   }

   @Override
   public void appendDeleteRecordTransactional(final long txID, final long id) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendDelete (noencoding) txID={} id={}", txID, id);
      }
      localJournal.appendDeleteRecordTransactional(txID, id);
      replicationManager.appendDeleteRecordTransactional(journalID, txID, id);
   }

   @Override
   public void appendPrepareRecord(final long txID, final byte[] transactionData, final boolean sync) throws Exception {
      this.appendPrepareRecord(txID, new ByteArrayEncoding(transactionData), sync);
   }

   @Override
   public void appendPrepareRecord(final long txID,
                                   final EncodingSupport transactionData,
                                   final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendPrepare txID={}", txID);
      }
      localJournal.appendPrepareRecord(txID, transactionData, sync);
      replicationManager.appendPrepareRecord(journalID, txID, transactionData);
   }

   @Override
   public void appendPrepareRecord(final long txID,
                                   final EncodingSupport transactionData,
                                   final boolean sync,
                                   final IOCompletion callback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendPrepare txID={}", txID);
      }
      localJournal.appendPrepareRecord(txID, transactionData, sync, callback);
      replicationManager.appendPrepareRecord(journalID, txID, transactionData);
   }

   @Override
   public void appendRollbackRecord(final long txID, final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendRollback {}", txID);
      }
      localJournal.appendRollbackRecord(txID, sync);
      replicationManager.appendRollbackRecord(journalID, txID);
   }

   @Override
   public void appendRollbackRecord(final long txID, final boolean sync, final IOCompletion callback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendRollback {}", txID);
      }
      localJournal.appendRollbackRecord(txID, sync, callback);
      replicationManager.appendRollbackRecord(journalID, txID);
   }

   @Override
   public void appendUpdateRecord(final long id,
                                  final byte recordType,
                                  final byte[] record,
                                  final boolean sync) throws Exception {
      this.appendUpdateRecord(id, recordType, new ByteArrayEncoding(record), sync);
   }

   @Override
   public void tryAppendUpdateRecord(final long id,
                                     final byte recordType,
                                     final byte[] record,
                                     final JournalUpdateCallback updateCallback,
                                     final boolean sync,
                                     final boolean replaceableRecord) throws Exception {

      this.tryAppendUpdateRecord(id, recordType, new ByteArrayEncoding(record), updateCallback, sync, replaceableRecord);
   }

   @Override
   public void appendUpdateRecord(final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object record,
                                  final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendUpdateRecord id = {} , recordType = {}", id, recordType);
      }
      localJournal.appendUpdateRecord(id, recordType, persister, record, sync);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.UPDATE, id, recordType, persister, record);
   }

   @Override
   public void tryAppendUpdateRecord(final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object record,
                                  final JournalUpdateCallback updateCallback,
                                  final boolean sync, final boolean replaceable) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendUpdateRecord id = {} , recordType = {}", id, recordType);
      }
      localJournal.tryAppendUpdateRecord(id, recordType, persister, record, updateCallback, sync, replaceable);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.UPDATE, id, recordType, persister, record);
   }

   @Override
   public void appendUpdateRecord(final long id,
                                  final byte journalRecordType,
                                  final Persister persister,
                                  final Object record,
                                  final boolean sync,
                                  final IOCompletion completionCallback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendUpdateRecord id = {} , recordType = {}", id, journalRecordType);
      }
      localJournal.appendUpdateRecord(id, journalRecordType, persister, record, sync, completionCallback);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.UPDATE, id, journalRecordType, persister, record);
   }

   @Override
   public void tryAppendUpdateRecord(final long id,
                                  final byte journalRecordType,
                                  final Persister persister,
                                  final Object record,
                                  final boolean sync,
                                  final boolean replaceableUpdate,
                                  final JournalUpdateCallback updateCallback,
                                  final IOCompletion completionCallback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendUpdateRecord id = {} , recordType = {}", id, journalRecordType);
      }
      localJournal.tryAppendUpdateRecord(id, journalRecordType, persister, record, sync, replaceableUpdate, updateCallback, completionCallback);
      replicationManager.appendUpdateRecord(journalID, ADD_OPERATION_TYPE.UPDATE, id, journalRecordType, persister, record);
   }

   @Override
   public void appendUpdateRecordTransactional(final long txID,
                                               final long id,
                                               final byte recordType,
                                               final byte[] record) throws Exception {
      this.appendUpdateRecordTransactional(txID, id, recordType, new ByteArrayEncoding(record));
   }

   @Override
   public void appendUpdateRecordTransactional(final long txID,
                                               final long id,
                                               final byte recordType,
                                               final Persister persister,
                                               final Object record) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("AppendUpdateRecord txid={} id = {} , recordType = {}", txID, id, recordType);
      }
      localJournal.appendUpdateRecordTransactional(txID, id, recordType, persister, record);
      replicationManager.appendAddRecordTransactional(journalID, ADD_OPERATION_TYPE.UPDATE, txID, id, recordType, persister, record);
   }

   @Override
   public JournalLoadInformation load(final List<RecordInfo> committedRecords,
                                      final List<PreparedTransactionInfo> preparedTransactions,
                                      final TransactionFailureCallback transactionFailure,
                                      final boolean fixbadTX) throws Exception {
      return localJournal.load(committedRecords, preparedTransactions, transactionFailure, fixbadTX);
   }

   @Override
   public JournalLoadInformation load(final SparseArrayLinkedList<RecordInfo> committedRecords,
                                      final List<PreparedTransactionInfo> preparedTransactions,
                                      final TransactionFailureCallback transactionFailure,
                                      final boolean fixbadTX) throws Exception {
      return localJournal.load(committedRecords, preparedTransactions, transactionFailure, fixbadTX);
   }

   @Override
   public JournalLoadInformation load(final LoaderCallback reloadManager) throws Exception {
      return localJournal.load(reloadManager);
   }

   @Override
   public void start() throws Exception {
      localJournal.start();
   }

   @Override
   public void stop() throws Exception {
      localJournal.stop();
   }

   @Override
   public int getAlignment() throws Exception {
      return localJournal.getAlignment();
   }

   @Override
   public boolean isStarted() {
      return localJournal.isStarted();
   }

   @Override
   public JournalLoadInformation loadInternalOnly() throws Exception {
      return localJournal.loadInternalOnly();
   }

   @Override
   public int getNumberOfRecords() {
      return localJournal.getNumberOfRecords();
   }

   @Override
   public int getUserVersion() {
      return localJournal.getUserVersion();
   }

   @Override
   public void lineUpContext(IOCompletion callback) {
      ((OperationContext) callback).replicationLineUp();
      localJournal.lineUpContext(callback);
   }

   @Override
   public JournalLoadInformation loadSyncOnly(JournalState state) throws Exception {
      return localJournal.loadSyncOnly(state);
   }

   @Override
   public Map<Long, JournalFile> createFilesForBackupSync(long[] fileIds) throws Exception {
      throw new UnsupportedOperationException("This method should only be called at a replicating backup");
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
   public void forceMoveNextFile() throws Exception {
      localJournal.forceMoveNextFile();
   }

   @Override
   public JournalFile[] getDataFiles() {
      throw new UnsupportedOperationException();
   }

   @Override
   public SequentialFileFactory getFileFactory() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int getFileSize() {
      return localJournal.getFileSize();
   }

   @Override
   public void scheduleCompactAndBlock(int timeout) throws Exception {
      localJournal.scheduleCompactAndBlock(timeout);
   }

   @Override
   public void replicationSyncPreserveOldFiles() {
      throw new UnsupportedOperationException("should never get called");
   }

   @Override
   public void replicationSyncFinished() {
      throw new UnsupportedOperationException("should never get called");
   }

   @Override
   public long getMaxRecordSize() {
      return localJournal.getMaxRecordSize();
   }

   @Override
   public long getWarningRecordSize() {
      return localJournal.getWarningRecordSize();
   }
}
