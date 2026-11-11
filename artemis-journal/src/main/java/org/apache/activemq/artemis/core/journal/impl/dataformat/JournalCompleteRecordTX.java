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
package org.apache.activemq.artemis.core.journal.impl.dataformat;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;

/**
 * A transaction record (Commit or Prepare), will hold the number of elements the transaction has in the current file.
 * <p>
 * While loading the {@link org.apache.activemq.artemis.core.journal.impl.JournalFile}, the number of operations found
 * is matched against this number. If for any reason there are missing operations, the transaction will be ignored.
 * <p>
 * We can't just use a global counter as reclaiming could delete files after the transaction was successfully committed.
 * That also means not having a whole file on journal-reload doesn't mean we have to invalidate the transaction
 * <p>
 * The commit operation itself is not included in this total.
 */
public class JournalCompleteRecordTX extends JournalInternalRecord {

   public enum TX_RECORD_TYPE {
      COMMIT, PREPARE;
   }

   private final TX_RECORD_TYPE txRecordType;

   private final long txID;

   private final EncodingSupport transactionData;

   private int numberOfRecords;

   public JournalCompleteRecordTX(final TX_RECORD_TYPE isCommit,
                                  final long txID,
                                  final EncodingSupport transactionData) {
      this.txRecordType = isCommit;

      this.txID = txID;

      this.transactionData = transactionData;
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      if (txRecordType == TX_RECORD_TYPE.COMMIT) {
         buffer.writeByte(JournalImpl.COMMIT_RECORD);
      } else {
         buffer.writeByte(JournalImpl.PREPARE_RECORD);
      }

      buffer.writeInt(fileID);

      buffer.writeByte(compactCount);

      buffer.writeLong(txID);

      buffer.writeInt(numberOfRecords);

      if (transactionData != null) {
         buffer.writeInt(transactionData.getEncodeSize());
      }

      if (transactionData != null) {
         transactionData.encode(buffer);
      }

      buffer.writeInt(getEncodeSize());
   }

   @Override
   public void setNumberOfRecords(final int records) {
      numberOfRecords = records;
   }

   @Override
   public int getNumberOfRecords() {
      return numberOfRecords;
   }

   @Override
   public int getEncodeSize() {
      if (txRecordType == TX_RECORD_TYPE.COMMIT) {
         return JournalImpl.SIZE_COMPLETE_TRANSACTION_RECORD + 1;
      } else {
         return JournalImpl.SIZE_PREPARE_RECORD + (transactionData != null ? transactionData.getEncodeSize() : 0) + 1;
      }
   }
}
