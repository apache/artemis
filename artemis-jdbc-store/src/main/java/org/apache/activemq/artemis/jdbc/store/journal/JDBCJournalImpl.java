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

package org.apache.activemq.artemis.jdbc.store.journal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQShutdownException;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncoderPersister;
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
import org.apache.activemq.artemis.core.journal.impl.SimpleWaitIOCallback;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.jdbc.store.drivers.AbstractJDBCDriver;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.utils.collections.SparseArrayLinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class JDBCJournalImpl extends AbstractJDBCDriver implements Journal {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Sync Delay in ms
   //private static final int SYNC_DELAY = 5;

   @Override
   public void setRemoveExtraFilesOnLoad(boolean removeExtraFilesOnLoad) {
      // no op on JDBC
   }

   @Override
   public boolean isRemoveExtraFilesOnLoad() {
      return false;
   }

   private long syncDelay;

   private static int USER_VERSION = 1;

   private final List<JDBCJournalRecord> records;

   private String insertJournalRecords;

   private String selectJournalRecords;

   private String countJournalRecords;

   private String deleteJournalRecords;

   private String deleteJournalTxRecords;

   private boolean started;

   private AtomicBoolean failed = new AtomicBoolean(false);

   private JDBCJournalSync syncTimer;

   private final Executor completeExecutor;

   private final ScheduledExecutorService scheduledExecutorService;

   // Track Tx Records
   private Map<Long, TransactionHolder> transactions = new ConcurrentHashMap<>();

   // Sequence ID for journal records
   private final AtomicLong seq = new AtomicLong(0);

   private final IOCriticalErrorListener criticalIOErrorListener;

   public JDBCJournalImpl(JDBCConnectionProvider connectionProvider,
                          SQLProvider provider,
                          ScheduledExecutorService scheduledExecutorService,
                          Executor completeExecutor,
                          IOCriticalErrorListener criticalIOErrorListener,
                          long syncDelay) {
      super(connectionProvider, provider);
      records = new ArrayList<>();
      this.scheduledExecutorService = scheduledExecutorService;
      this.completeExecutor = completeExecutor;
      this.criticalIOErrorListener = criticalIOErrorListener;
      this.syncDelay = syncDelay;
   }

   @Override
   public void appendAddEvent(long id,
                              byte recordType,
                              Persister persister,
                              Object record,
                              boolean sync,
                              IOCompletion completionCallback) throws Exception {
      // Nothing to be done
   }

   @Override
   public void start() throws SQLException {
      super.start();
      syncTimer = new JDBCJournalSync(scheduledExecutorService, completeExecutor, syncDelay, TimeUnit.MILLISECONDS, this);
      started = true;
   }

   @Override
   public void flush() throws Exception {
   }

   /**
    * The max size record that can be stored in the journal
    */
   @Override
   public long getMaxRecordSize() {
      return sqlProvider.getMaxBlobSize();
   }

   @Override
   public long getWarningRecordSize() {
      return sqlProvider.getMaxBlobSize() - 2048;
   }

   @Override
   protected void createSchema() throws SQLException {
      createTable(sqlProvider.getCreateJournalTableSQL());
   }

   @Override
   protected void prepareStatements() {
      logger.trace("preparing statements");
      insertJournalRecords = sqlProvider.getInsertJournalRecordsSQL();
      selectJournalRecords = sqlProvider.getSelectJournalRecordsSQL();
      countJournalRecords = sqlProvider.getCountJournalRecordsSQL();
      deleteJournalRecords = sqlProvider.getDeleteJournalRecordsSQL();
      deleteJournalTxRecords = sqlProvider.getDeleteJournalTxRecordsSQL();
   }

   @Override
   public void stop() throws SQLException {
      stop(true);
   }

   public synchronized void stop(boolean sync) throws SQLException {
      if (started) {
         if (sync) {
            sync();
         }
         started = false;
         super.stop();
      }
   }

   @Override
   public synchronized void destroy() throws Exception {
      super.destroy();
      stop();
   }

   public synchronized int sync() {

      List<JDBCJournalRecord> recordRef;
      synchronized (records) {
         if (records.isEmpty()) {
            return 0;
         }
         recordRef = new ArrayList<>(records);
         records.clear();
      }

      if (!started || failed.get()) {
         executeCallbacks(recordRef, false);
         return 0;
      }


      // We keep a list of deleted records and committed tx (used for cleaning up old transaction data).
      List<Long> deletedRecords = new ArrayList<>();
      List<Long> committedTransactions = new ArrayList<>();

      TransactionHolder holder;

      try (Connection connection = connectionProvider.getConnection();
           PreparedStatement deleteJournalRecords = connection.prepareStatement(this.deleteJournalRecords);
           PreparedStatement deleteJournalTxRecords = connection.prepareStatement(this.deleteJournalTxRecords);
           PreparedStatement insertJournalRecords = connection.prepareStatement(this.insertJournalRecords)) {

         connection.setAutoCommit(false);

         for (JDBCJournalRecord record : recordRef) {

            logger.trace("sync::preparing JDBC statement for {}", record);

            switch (record.getRecordType()) {
               case JDBCJournalRecord.DELETE_RECORD:
                  // Standard SQL Delete Record, Non transactional delete
                  deletedRecords.add(record.getId());
                  record.writeDeleteRecord(deleteJournalRecords);
                  break;
               case JDBCJournalRecord.ROLLBACK_RECORD:
                  // Roll back we remove all records associated with this TX ID.  This query is always performed last.
                  deleteJournalTxRecords.setLong(1, record.getTxId());
                  deleteJournalTxRecords.addBatch();
                  break;
               case JDBCJournalRecord.COMMIT_RECORD:
                  // We perform all the deletes and add the commit record in the same Database TX
                  holder = transactions.get(record.getTxId());
                  for (RecordInfo info : holder.recordsToDelete) {
                     deletedRecords.add(record.getId());
                     deletedRecords.add(info.id);
                     deleteJournalRecords.setLong(1, info.id);
                     deleteJournalRecords.addBatch();
                  }
                  record.writeRecord(insertJournalRecords);
                  committedTransactions.add(record.getTxId());
                  break;
               default:
                  // Default we add a new record to the DB
                  record.writeRecord(insertJournalRecords);
                  break;
            }
         }

         insertJournalRecords.executeBatch();
         deleteJournalRecords.executeBatch();
         deleteJournalTxRecords.executeBatch();

         connection.commit();
         logger.trace("JDBC commit worked");

         if (cleanupTxRecords(deletedRecords, committedTransactions, deleteJournalTxRecords)) {
            deleteJournalTxRecords.executeBatch();
            connection.commit();
            logger.trace("JDBC commit worked on cleanupTxRecords");
         }
         executeCallbacks(recordRef, true);

         return recordRef.size();
      } catch (Exception e) {
         handleException(recordRef, e);
         return 0;
      }
   }

   /**
    * public for tests only, not through API
    */
   public void handleException(List<JDBCJournalRecord> recordRef, Throwable e) {
      logger.warn(e.getMessage(), e);
      failed.set(true);
      criticalIOErrorListener.onIOException(e, "Critical IO Error.  Failed to process JDBC Record statements", null);

      logger.trace("Rolling back Transaction, just in case");

      if (recordRef != null) {
         executeCallbacks(recordRef, false);
      }
   }

   /*
    * We store Transaction reference in memory (once all records associated with a Transaction are Deleted, we remove
    * the Tx Records (i.e. PREPARE, COMMIT).
    */
   private synchronized boolean cleanupTxRecords(List<Long> deletedRecords, List<Long> committedTx,
                                                 PreparedStatement deleteJournalTxRecords) throws SQLException {
      List<RecordInfo> iterableCopy;
      List<TransactionHolder> iterableCopyTx = new ArrayList<>();
      iterableCopyTx.addAll(transactions.values());

      for (Long txId : committedTx) {
         transactions.get(txId).committed = true;
      }
      boolean hasDeletedJournalTxRecords = false;
      // TODO (mtaylor) perhaps we could store a reverse mapping of IDs to prevent this O(n) loop
      for (TransactionHolder h : iterableCopyTx) {

         iterableCopy = new ArrayList<>();
         iterableCopy.addAll(h.recordInfos);

         for (RecordInfo info : iterableCopy) {
            if (deletedRecords.contains(info.id)) {
               h.recordInfos.remove(info);
            }
         }

         if (h.recordInfos.isEmpty() && h.committed) {
            deleteJournalTxRecords.setLong(1, h.transactionID);
            deleteJournalTxRecords.addBatch();
            hasDeletedJournalTxRecords = true;
            transactions.remove(h.transactionID);
         }
      }
      return hasDeletedJournalTxRecords;
   }

   private void executeCallbacks(final List<JDBCJournalRecord> records, final boolean success) {
      Runnable r = () -> {
         for (JDBCJournalRecord record : records) {
            if (logger.isTraceEnabled()) {
               logger.trace("Calling callback {} with success = {}", record, success);
            }
            record.complete(success);
         }
      };
      completeExecutor.execute(r);
   }


   private void checkStatus() throws Exception {
      checkStatus(null);
   }

   private void checkStatus(IOCompletion callback) throws Exception {
      if (!started) {
         if (callback != null) {
            callback.onError(-1, "JDBC Journal is not loaded");
         }
         throw new ActiveMQShutdownException("JDBCJournal is not loaded");
      }

      if (failed.get()) {
         if (callback != null) {
            callback.onError(-1, "JDBC Journal failed");
         }
         throw new ActiveMQException("JDBCJournal Failed");
      }
   }


   private void appendRecord(JDBCJournalRecord record) throws Exception {

      // extra measure I know, as all the callers are also checking for this..
      // better to be safe ;)
      checkStatus();

      logger.trace("appendRecord {}", record);

      record.storeLineUp();
      if (!started) {
         if (record.getIoCompletion() != null) {
            record.getIoCompletion().onError(ActiveMQExceptionType.IO_ERROR.getCode(), "JDBC Journal not started");
         }
      }

      SimpleWaitIOCallback callback = null;
      if (record.isSync() && record.getIoCompletion() == null) {
         callback = new SimpleWaitIOCallback();
         record.setIoCompletion(callback);
      }

      synchronized (this) {
         if (record.isTransactional() || record.getRecordType() == JDBCJournalRecord.PREPARE_RECORD) {
            addTxRecord(record);
         }

         synchronized (records) {
            records.add(record);
         }
      }

      syncTimer.delay();
      if (callback != null) {
         callback.waitCompletion();
      }
   }

   private synchronized void addTxRecord(JDBCJournalRecord record) throws Exception {

      if (logger.isTraceEnabled()) {
         logger.trace("addTxRecord {}, started={}, failed={}", record, started, failed);
      }

      checkStatus();

      TransactionHolder txHolder = transactions.get(record.getTxId());
      if (txHolder == null) {
         txHolder = new TransactionHolder(record.getTxId());
         transactions.put(record.getTxId(), txHolder);
      }

      // We actually only need the record ID in this instance.
      if (record.isTransactional()) {
         RecordInfo info = new RecordInfo(record.getId(), record.getRecordType(), new byte[0], record.isUpdate(), false, record.getCompactCount());
         if (record.getRecordType() == JDBCJournalRecord.DELETE_RECORD_TX) {
            txHolder.recordsToDelete.add(info);
         } else {
            txHolder.recordInfos.add(info);
         }
      } else {
         txHolder.prepared = true;
      }
   }

   @Override
   public void appendAddRecord(long id, byte recordType, byte[] record, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.ADD_RECORD, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(record);
      r.setSync(sync);

      logger.trace("appendAddRecord bytes[] {}", r);

      appendRecord(r);
   }

   @Override
   public void appendAddRecord(long id, byte recordType, Persister persister, Object record, boolean sync) throws Exception {
      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.ADD_RECORD, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(persister, record);
      r.setSync(sync);

      logger.trace("appendAddRecord (encoding) {} with record = {}", r, record);

      appendRecord(r);
   }

   @Override
   public void appendAddRecord(long id,
                               byte recordType,
                               Persister persister,
                               Object record,
                               boolean sync,
                               IOCompletion completionCallback) throws Exception {
      checkStatus(completionCallback);

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.ADD_RECORD, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(persister, record);
      r.setSync(sync);
      r.setIoCompletion(completionCallback);

      logger.trace("appendAddRecord (completionCallback & encoding) {} with record = {}", r, record);

      appendRecord(r);
   }

   @Override
   public void appendUpdateRecord(long id, byte recordType, byte[] record, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.UPDATE_RECORD, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(record);
      r.setSync(sync);

      logger.trace("appendUpdateRecord (bytes)) {}", r);

      appendRecord(r);
   }

   @Override
   public void tryAppendUpdateRecord(long id, byte recordType, byte[] record, JournalUpdateCallback updateCallback, boolean sync, boolean replaceableRecord) throws Exception {
      appendUpdateRecord(id, recordType, record, sync);
   }

   @Override
   public void appendUpdateRecord(long id, byte recordType, Persister persister, Object record, boolean sync) throws Exception {
      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.UPDATE_RECORD, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(persister, record);
      r.setSync(sync);

      logger.trace("appendUpdateRecord (encoding)) {} with record {}", r, record);

      appendRecord(r);
   }

   @Override
   public void tryAppendUpdateRecord(long id, byte recordType, Persister persister, Object record, JournalUpdateCallback updateCallback, boolean sync, boolean replaceableUpdate) throws Exception {
      appendUpdateRecord(id, recordType, persister, record, sync);
   }

   @Override
   public void appendUpdateRecord(long id,
                                  byte recordType,
                                  Persister persister,
                                  Object record,
                                  boolean sync,
                                  IOCompletion completionCallback) throws Exception {
      checkStatus(completionCallback);

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.ADD_RECORD, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(persister, record);
      r.setSync(sync);
      r.setIoCompletion(completionCallback);

      logger.trace("appendUpdateRecord (encoding & completioncallback)) {} with record {}", r, record);

      appendRecord(r);
   }


   @Override
   public void tryAppendUpdateRecord(long id,
                                  byte recordType,
                                  Persister persister,
                                  Object record,
                                  boolean sync,
                                  boolean replaceableUpdate,
                                  JournalUpdateCallback updateCallback,
                                  IOCompletion completionCallback) throws Exception {
      appendUpdateRecord(id, recordType, persister, record, sync, completionCallback);
   }


   @Override
   public void appendDeleteRecord(long id, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.DELETE_RECORD, seq.incrementAndGet());
      r.setSync(sync);

      if (logger.isTraceEnabled()) {
         logger.trace("appendDeleteRecord id={} sync={}", id, sync);
      }

      appendRecord(r);
   }

   @Override
   public void tryAppendDeleteRecord(long id, JournalUpdateCallback updateCallback, boolean sync) throws Exception {
      appendDeleteRecord(id, sync);
   }

   @Override
   public void appendDeleteRecord(long id, boolean sync, IOCompletion completionCallback) throws Exception {
      checkStatus(completionCallback);

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.DELETE_RECORD, seq.incrementAndGet());
      r.setSync(sync);
      r.setIoCompletion(completionCallback);

      if (logger.isTraceEnabled()) {
         logger.trace("appendDeleteRecord id={} sync={} with completionCallback", id, sync);
      }

      appendRecord(r);
   }

   @Override
   public void tryAppendDeleteRecord(long id, boolean sync, JournalUpdateCallback updateCallback, IOCompletion completionCallback) throws Exception {
      appendDeleteRecord(id, sync, completionCallback);
   }

   @Override
   public void appendAddRecordTransactional(long txID, long id, byte recordType, byte[] record) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.ADD_RECORD_TX, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(record);
      r.setTxId(txID);
      appendRecord(r);

      if (logger.isTraceEnabled()) {
         logger.trace("appendAddRecordTransactional txID={} id={} using bytes[] r={}", txID, id, r);
      }
   }

   @Override
   public void appendAddRecordTransactional(long txID,
                                            long id,
                                            byte recordType,
                                            Persister persister,
                                            Object record) throws Exception {
      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.ADD_RECORD_TX, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(persister, record);
      r.setTxId(txID);

      if (logger.isTraceEnabled()) {
         logger.trace("appendAddRecordTransactional txID={} id={} using encoding={} and r={}", txID, id, record, r);
      }

      appendRecord(r);
   }

   @Override
   public void appendUpdateRecordTransactional(long txID, long id, byte recordType, byte[] record) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.UPDATE_RECORD_TX, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(record);
      r.setTxId(txID);

      if (logger.isTraceEnabled()) {
         logger.trace("appendUpdateRecordTransactional txID={} id={} using bytes and r={}", txID, id, r);
      }

      appendRecord(r);
   }

   @Override
   public void appendUpdateRecordTransactional(long txID,
                                               long id,
                                               byte recordType,
                                               Persister persister,
                                               Object record) throws Exception {
      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.UPDATE_RECORD_TX, seq.incrementAndGet());
      r.setUserRecordType(recordType);
      r.setRecord(persister, record);
      r.setTxId(txID);

      if (logger.isTraceEnabled()) {
         logger.trace("appendUpdateRecordTransactional txID={} id={} using encoding={} and r={}", txID, id, record, r);
      }

      appendRecord(r);
   }

   @Override
   public void appendDeleteRecordTransactional(long txID, long id, byte[] record) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.DELETE_RECORD_TX, seq.incrementAndGet());
      r.setRecord(record);
      r.setTxId(txID);

      if (logger.isTraceEnabled()) {
         logger.trace("appendDeleteRecordTransactional txID={} id={} using bytes and r={}", txID, id, r);
      }

      appendRecord(r);
   }

   @Override
   public void appendDeleteRecordTransactional(long txID, long id, EncodingSupport record) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.DELETE_RECORD_TX, seq.incrementAndGet());
      r.setRecord(EncoderPersister.getInstance(), record);
      r.setTxId(txID);

      if (logger.isTraceEnabled()) {
         logger.trace("appendDeleteRecordTransactional txID={} id={} using encoding={} and r={}", txID, id, record, r);
      }

      appendRecord(r);
   }

   @Override
   public void appendDeleteRecordTransactional(long txID, long id) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(id, JDBCJournalRecord.DELETE_RECORD_TX, seq.incrementAndGet());
      r.setTxId(txID);

      if (logger.isTraceEnabled()) {
         logger.trace("appendDeleteRecordTransactional txID={} id={}", txID, id);
      }

      appendRecord(r);
   }

   @Override
   public void appendCommitRecord(long txID, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(-1, JDBCJournalRecord.COMMIT_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setSync(sync);

      if (logger.isTraceEnabled()) {
         logger.trace("appendCommitRecord txID={} sync={}", txID, sync);
      }

      appendRecord(r);
   }

   @Override
   public void appendCommitRecord(long txID, boolean sync, IOCompletion callback) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(-1, JDBCJournalRecord.COMMIT_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setSync(sync);
      r.setIoCompletion(callback);

      if (logger.isTraceEnabled()) {
         logger.trace("appendCommitRecord txID={} callback={}", txID, callback);
      }

      appendRecord(r);
   }

   @Override
   public void appendCommitRecord(long txID,
                                  boolean sync,
                                  IOCompletion callback,
                                  boolean lineUpContext) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(-1, JDBCJournalRecord.COMMIT_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setStoreLineUp(lineUpContext);
      r.setIoCompletion(callback);
      r.setSync(sync);

      if (logger.isTraceEnabled()) {
         logger.trace("appendCommitRecord txID={} using callback, lineup={}", txID, lineUpContext);
      }

      appendRecord(r);
   }

   @Override
   public void appendPrepareRecord(long txID, EncodingSupport transactionData, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(-1, JDBCJournalRecord.PREPARE_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setTxData(transactionData);
      r.setSync(sync);

      if (logger.isTraceEnabled()) {
         logger.trace("appendPrepareRecord txID={} using sync={}", txID, sync);
      }

      appendRecord(r);
   }

   @Override
   public void appendPrepareRecord(long txID,
                                   EncodingSupport transactionData,
                                   boolean sync,
                                   IOCompletion callback) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(0, JDBCJournalRecord.PREPARE_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setTxData(transactionData);
      r.setTxData(transactionData);
      r.setSync(sync);
      r.setIoCompletion(callback);

      if (logger.isTraceEnabled()) {
         logger.trace("appendPrepareRecord txID={} using callback, sync={}", txID, sync);
      }

      appendRecord(r);
   }

   @Override
   public void appendPrepareRecord(long txID, byte[] transactionData, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(0, JDBCJournalRecord.PREPARE_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setTxData(transactionData);
      r.setSync(sync);

      if (logger.isTraceEnabled()) {
         logger.trace("appendPrepareRecord txID={} transactionData, sync={}", txID, sync);
      }

      appendRecord(r);
   }

   @Override
   public void appendRollbackRecord(long txID, boolean sync) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(0, JDBCJournalRecord.ROLLBACK_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setSync(sync);

      if (logger.isTraceEnabled()) {
         logger.trace("appendRollbackRecord txID={} sync={}", txID, sync);
      }

      appendRecord(r);
   }

   @Override
   public void appendRollbackRecord(long txID, boolean sync, IOCompletion callback) throws Exception {
      checkStatus();

      JDBCJournalRecord r = new JDBCJournalRecord(0, JDBCJournalRecord.ROLLBACK_RECORD, seq.incrementAndGet());
      r.setTxId(txID);
      r.setSync(sync);
      r.setIoCompletion(callback);

      if (logger.isTraceEnabled()) {
         logger.trace("appendRollbackRecord txID={} sync={} using callback", txID, sync);
      }

      appendRecord(r);
   }

   @Override
   public synchronized JournalLoadInformation load(LoaderCallback reloadManager) {
      JournalLoadInformation jli = new JournalLoadInformation();
      JDBCJournalReaderCallback jrc = new JDBCJournalReaderCallback(reloadManager);
      JDBCJournalRecord r;

      try (Connection connection = connectionProvider.getConnection();
           PreparedStatement selectJournalRecords = connection.prepareStatement(this.selectJournalRecords)) {
         try (ResultSet rs = selectJournalRecords.executeQuery()) {
            int noRecords = 0;
            while (rs.next()) {
               r = JDBCJournalRecord.readRecord(rs);
               switch (r.getRecordType()) {
                  case JDBCJournalRecord.ADD_RECORD:
                     jrc.onReadAddRecord(r.toRecordInfo());
                     break;
                  case JDBCJournalRecord.UPDATE_RECORD:
                     jrc.onReadUpdateRecord(r.toRecordInfo());
                     break;
                  case JDBCJournalRecord.DELETE_RECORD:
                     jrc.onReadDeleteRecord(r.getId());
                     break;
                  case JDBCJournalRecord.ADD_RECORD_TX:
                     jrc.onReadAddRecordTX(r.getTxId(), r.toRecordInfo());
                     break;
                  case JDBCJournalRecord.UPDATE_RECORD_TX:
                     jrc.onReadUpdateRecordTX(r.getTxId(), r.toRecordInfo());
                     break;
                  case JDBCJournalRecord.DELETE_RECORD_TX:
                     jrc.onReadDeleteRecordTX(r.getTxId(), r.toRecordInfo());
                     break;
                  case JDBCJournalRecord.PREPARE_RECORD:
                     jrc.onReadPrepareRecord(r.getTxId(), r.getTxDataAsByteArray(), r.getTxCheckNoRecords());
                     break;
                  case JDBCJournalRecord.COMMIT_RECORD:
                     jrc.onReadCommitRecord(r.getTxId(), r.getTxCheckNoRecords());
                     break;
                  case JDBCJournalRecord.ROLLBACK_RECORD:
                     jrc.onReadRollbackRecord(r.getTxId());
                     break;
                  default:
                     throw new Exception("Error Reading Journal, Unknown Record Type: " + r.getRecordType());
               }
               noRecords++;
               if (r.getSeq() > seq.longValue()) {
                  seq.set(r.getSeq());
               }
            }
            jrc.checkPreparedTx();

            jli.setMaxID(((JDBCJournalLoaderCallback) reloadManager).getMaxId());
            jli.setNumberOfRecords(noRecords);
            transactions = jrc.getTransactions();
         }
      } catch (Throwable e) {
         handleException(null, e);
      }
      return jli;
   }

   @Override
   public JournalLoadInformation loadInternalOnly() throws Exception {
      return null;
   }

   @Override
   public JournalLoadInformation loadSyncOnly(JournalState state) throws Exception {
      return null;
   }

   @Override
   public void lineUpContext(IOCompletion callback) {
      callback.storeLineUp();
   }

   @Override
   public synchronized JournalLoadInformation load(final SparseArrayLinkedList<RecordInfo> committedRecords,
                                                   final List<PreparedTransactionInfo> preparedTransactions,
                                                   final TransactionFailureCallback failureCallback,
                                                   final boolean fixBadTX) throws Exception {
      final List<RecordInfo> records = new ArrayList<>();
      final JournalLoadInformation journalLoadInformation = load(records, preparedTransactions, failureCallback, fixBadTX);
      records.forEach(committedRecords::add);
      return journalLoadInformation;
   }

   @Override
   public synchronized JournalLoadInformation load(final List<RecordInfo> committedRecords,
                                                   final List<PreparedTransactionInfo> preparedTransactions,
                                                   final TransactionFailureCallback failureCallback,
                                                   final boolean fixBadTX) throws Exception {
      JDBCJournalLoaderCallback lc = new JDBCJournalLoaderCallback(committedRecords, preparedTransactions, failureCallback, fixBadTX);
      return load(lc);
   }

   @Override
   public int getAlignment() throws Exception {
      return 0;
   }

   @Override
   public int getNumberOfRecords() {
      int count = 0;
      try (Connection connection = connectionProvider.getConnection();
           PreparedStatement countJournalRecords = connection.prepareStatement(this.countJournalRecords)) {
         try (ResultSet rs = countJournalRecords.executeQuery()) {
            rs.next();
            count = rs.getInt(1);
         }
      } catch (SQLException e) {
         logger.warn(e.getMessage(), e);
         return -1;
      }
      return count;
   }

   @Override
   public int getUserVersion() {
      return USER_VERSION;
   }

   @Override
   public Map<Long, JournalFile> createFilesForBackupSync(long[] fileIds) throws Exception {
      return null;
   }

   @Override
   public final void synchronizationLock() {
      logger.error("Replication is not supported with JDBC Store", new Exception("trace"));
   }

   @Override
   public final void synchronizationUnlock() {
      logger.error("Replication is not supported with JDBC Store", new Exception("trace"));
   }

   @Override
   public void forceMoveNextFile() throws Exception {
   }

   @Override
   public JournalFile[] getDataFiles() {
      return new JournalFile[0];
   }

   @Override
   public SequentialFileFactory getFileFactory() {
      return null;
   }

   @Override
   public int getFileSize() {
      return 0;
   }

   @Override
   public void scheduleCompactAndBlock(int timeout) throws Exception {

   }

   @Override
   public void replicationSyncPreserveOldFiles() {

   }

   @Override
   public void replicationSyncFinished() {

   }

   @Override
   public boolean isStarted() {
      return started;
   }

   private static class JDBCJournalSync extends ActiveMQScheduledComponent {

      private final JDBCJournalImpl journal;

      JDBCJournalSync(ScheduledExecutorService scheduledExecutorService,
                      Executor executor,
                      long checkPeriod,
                      TimeUnit timeUnit,
                      JDBCJournalImpl journal) {
         super(scheduledExecutorService, executor, checkPeriod, timeUnit, true);
         this.journal = journal;
      }

      @Override
      public void run() {
         if (journal.isStarted()) {
            journal.sync();
         }
      }
   }
}
