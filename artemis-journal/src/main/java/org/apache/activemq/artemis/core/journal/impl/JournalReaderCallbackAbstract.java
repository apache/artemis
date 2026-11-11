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

public class JournalReaderCallbackAbstract implements JournalReaderCallback {

   @Override
   public void markAsDataFile(final JournalFile file) {
   }

   @Override
   public void onReadAddRecord(final RecordInfo info) throws Exception {
   }

   @Override
   public void onReadAddRecordTX(final long transactionID, final RecordInfo recordInfo) throws Exception {
   }

   @Override
   public void onReadCommitRecord(final long transactionID, final int numberOfRecords) throws Exception {
   }

   @Override
   public void onReadDeleteRecord(final long recordID) throws Exception {
   }

   @Override
   public void onReadDeleteRecordTX(final long transactionID, final RecordInfo recordInfo) throws Exception {
   }

   @Override
   public void onReadPrepareRecord(final long transactionID,
                                   final byte[] extraData,
                                   final int numberOfRecords) throws Exception {
   }

   @Override
   public void onReadRollbackRecord(final long transactionID) throws Exception {
   }

   @Override
   public void onReadUpdateRecord(final RecordInfo recordInfo) throws Exception {
   }

   @Override
   public void onReadUpdateRecordTX(final long transactionID, final RecordInfo recordInfo) throws Exception {
   }

}
