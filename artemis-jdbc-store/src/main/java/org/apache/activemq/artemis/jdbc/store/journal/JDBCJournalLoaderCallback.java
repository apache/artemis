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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;

class JDBCJournalLoaderCallback implements LoaderCallback {

   private final List<PreparedTransactionInfo> preparedTransactions;

   private final TransactionFailureCallback failureCallback;

   /*
    * We keep track of list entries for each ID. This preserves order and allows multiple record insertions with the
    * same ID.  We use this for deleting records.
    */
   private final Map<Long, List<Integer>> deleteReferences = new HashMap<>();

   private final List<RecordInfo> committedRecords;

   private long maxId = -1;

   JDBCJournalLoaderCallback(final List<RecordInfo> committedRecords,
                             final List<PreparedTransactionInfo> preparedTransactions,
                             final TransactionFailureCallback failureCallback,
                             final boolean fixBadTX) {
      this.committedRecords = committedRecords;
      this.preparedTransactions = preparedTransactions;
      this.failureCallback = failureCallback;
   }

   private synchronized void checkMaxId(long id) {
      if (maxId < id) {
         maxId = id;
      }
   }

   @Override
   public void addPreparedTransaction(final PreparedTransactionInfo preparedTransaction) {
      preparedTransactions.add(preparedTransaction);
   }

   @Override
   public synchronized void addRecord(final RecordInfo info) {
      int index = committedRecords.size();
      committedRecords.add(index, info);

      List<Integer> indexes = new ArrayList<>();
      indexes.add(index);

      deleteReferences.put(info.id, indexes);
      checkMaxId(info.id);
   }

   @Override
   public synchronized void updateRecord(final RecordInfo info) {
      int index = committedRecords.size();
      committedRecords.add(index, info);
   }

   @Override
   public synchronized void deleteRecord(final long id) {
      for (int i : deleteReferences.get(id)) {
         committedRecords.remove(i);
      }
   }

   @Override
   public void failedTransaction(final long transactionID,
                                 final List<RecordInfo> records,
                                 final List<RecordInfo> recordsToDelete) {
      if (failureCallback != null) {
         failureCallback.failedTransaction(transactionID, records, recordsToDelete);
      }
   }

   public long getMaxId() {
      return maxId;
   }
}
