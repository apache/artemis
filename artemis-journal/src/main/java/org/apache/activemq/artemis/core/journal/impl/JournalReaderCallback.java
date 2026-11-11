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

import org.apache.activemq.artemis.core.journal.RecordInfo;

public interface JournalReaderCallback {

   default void onReadEventRecord(RecordInfo info) throws Exception {
   }

   default void done() {
   }

   default void onReadAddRecord(RecordInfo info) throws Exception {
   }

   default void onReadUpdateRecord(RecordInfo recordInfo) throws Exception {
   }

   default void onReadDeleteRecord(long recordID) throws Exception {
   }


   default void onReadAddRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {
   }

   default void onReadUpdateRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {

   }

   default void onReadDeleteRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {
   }

   default void onReadPrepareRecord(long transactionID, byte[] extraData, int numberOfRecords) throws Exception {
   }

   default void onReadCommitRecord(long transactionID, int numberOfRecords) throws Exception {
   }

   default void onReadRollbackRecord(long transactionID) throws Exception {
   }


   default void markAsDataFile(JournalFile file) {
   }
}
