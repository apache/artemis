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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalReaderCallback;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;

class JDBCJournalReaderCallback implements JournalReaderCallback {

   private final Map<Long, TransactionHolder> loadTransactions = new LinkedHashMap<>();

   private final LoaderCallback loadManager;

   JDBCJournalReaderCallback(final LoaderCallback loadManager) {
      this.loadManager = loadManager;
   }

   @Override
   public void onReadAddRecord(final RecordInfo info) throws Exception {
      loadManager.addRecord(info);
   }

   @Override
   public void onReadUpdateRecord(final RecordInfo info) throws Exception {
      loadManager.updateRecord(info);
   }

   @Override
   public void onReadDeleteRecord(final long recordID) throws Exception {
      loadManager.deleteRecord(recordID);
   }

   @Override
   public void onReadUpdateRecordTX(final long transactionID, final RecordInfo info) throws Exception {
      TransactionHolder tx = loadTransactions.get(transactionID);
      if (tx == null) {
         tx = new TransactionHolder(transactionID);
         loadTransactions.put(transactionID, tx);
      }
      tx.recordInfos.add(info);
   }

   @Override
   public void onReadAddRecordTX(final long transactionID, final RecordInfo info) throws Exception {
      TransactionHolder tx = loadTransactions.get(transactionID);
      if (tx == null) {
         tx = new TransactionHolder(transactionID);
         loadTransactions.put(transactionID, tx);
      }
      tx.recordInfos.add(info);
   }

   @Override
   public void onReadDeleteRecordTX(final long transactionID, final RecordInfo info) throws Exception {
      TransactionHolder tx = loadTransactions.get(transactionID);
      if (tx == null) {
         tx = new TransactionHolder(transactionID);
         loadTransactions.put(transactionID, tx);
      }
      tx.recordsToDelete.add(info);
   }

   @Override
   public void onReadPrepareRecord(final long transactionID,
                                   final byte[] extraData,
                                   final int numberOfRecords) throws Exception {
      TransactionHolder tx = loadTransactions.get(transactionID);
      if (tx == null) {
         tx = new TransactionHolder(transactionID);
         loadTransactions.put(transactionID, tx);
      }
      tx.prepared = true;
      tx.extraData = extraData;
   }

   @Override
   public void onReadCommitRecord(final long transactionID, final int numberOfRecords) throws Exception {
      // It is possible that the TX could be null, since deletes could have happened in the journal.
      TransactionHolder tx = loadTransactions.get(transactionID);

      // We can remove local Tx without associated records
      if (tx != null) {
         tx.committed = true;
         for (RecordInfo txRecord : tx.recordInfos) {
            if (txRecord.isUpdate) {
               loadManager.updateRecord(txRecord);
            } else {
               loadManager.addRecord(txRecord);
            }
         }
      }
   }

   @Override
   public void onReadRollbackRecord(final long transactionID) throws Exception {
      TransactionHolder tx = loadTransactions.remove(transactionID);
      if (tx == null) {
         throw new IllegalStateException("Cannot rollback, tx not found with ID: " + transactionID);
      }
   }

   @Override
   public void markAsDataFile(JournalFile file) {
      // Not needed for JDBC journal impl
   }

   void checkPreparedTx() {
      for (TransactionHolder transaction : loadTransactions.values()) {
         if ((!transaction.prepared && !transaction.committed) || transaction.invalid) {
            ActiveMQJournalLogger.LOGGER.uncomittedTxFound(transaction.transactionID);
            loadManager.failedTransaction(transaction.transactionID, transaction.recordInfos, transaction.recordsToDelete);
         } else if (!transaction.committed) {
            PreparedTransactionInfo info = new PreparedTransactionInfo(transaction.transactionID, transaction.extraData);
            info.getRecords().addAll(transaction.recordInfos);
            info.getRecordsToDelete().addAll(transaction.recordsToDelete);
            loadManager.addPreparedTransaction(info);
         }
      }
   }

   public Map<Long, TransactionHolder> getTransactions() {
      return loadTransactions;
   }
}
