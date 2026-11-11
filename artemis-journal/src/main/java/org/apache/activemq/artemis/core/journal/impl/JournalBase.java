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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.io.DummyCallback;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalUpdateCallback;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;

abstract class JournalBase implements Journal {

   protected final int fileSize;
   private final boolean supportsCallback;
   protected boolean removeExtraFilesOnLoad = false;

   @Override
   public void setRemoveExtraFilesOnLoad(boolean setting) {
      this.removeExtraFilesOnLoad = setting;
   }

   @Override
   public boolean isRemoveExtraFilesOnLoad() {
      return removeExtraFilesOnLoad;
   }

   JournalBase(boolean supportsCallback, int fileSize) {
      if (fileSize < JournalImpl.MIN_FILE_SIZE) {
         throw new IllegalArgumentException("File size cannot be less than " + JournalImpl.MIN_FILE_SIZE + " bytes");
      }
      this.supportsCallback = supportsCallback;
      this.fileSize = fileSize;
   }

   @Override
   public void appendAddRecord(long id, byte recordType, byte[] record, boolean sync) throws Exception {
      appendAddRecord(id, recordType, new ByteArrayEncoding(record), sync);
   }

   @Override
   public void appendAddRecord(long id, byte recordType, Persister persister, Object record, boolean sync) throws Exception {
      SyncIOCompletion callback = getSyncCallback(sync);

      appendAddRecord(id, recordType, persister, record, sync, callback);

      if (callback != null) {
         callback.waitCompletion();
      }
   }

   @Override
   public void appendCommitRecord(final long txID, final boolean sync) throws Exception {
      SyncIOCompletion syncCompletion = getSyncCallback(sync);

      appendCommitRecord(txID, sync, syncCompletion, true);

      if (syncCompletion != null) {
         syncCompletion.waitCompletion();
      }
   }

   @Override
   public void appendCommitRecord(final long txID, final boolean sync, final IOCompletion callback) throws Exception {
      appendCommitRecord(txID, sync, callback, true);
   }

   @Override
   public void appendUpdateRecord(final long id,
                                     final byte recordType,
                                     final byte[] record,
                                     final boolean sync) throws Exception {
      appendUpdateRecord(id, recordType, new ByteArrayEncoding(record), sync);
   }

   @Override
   public void tryAppendUpdateRecord(final long id,
                                     final byte recordType,
                                     final byte[] record,
                                     JournalUpdateCallback updateCallback,
                                     final boolean sync,
                                     final boolean replaceableRecord) throws Exception {
      tryAppendUpdateRecord(id, recordType, new ByteArrayEncoding(record), updateCallback, sync, replaceableRecord);
   }

   @Override
   public void appendUpdateRecordTransactional(final long txID,
                                               final long id,
                                               final byte recordType,
                                               final byte[] record) throws Exception {
      appendUpdateRecordTransactional(txID, id, recordType, new ByteArrayEncoding(record));
   }

   @Override
   public void appendAddRecordTransactional(final long txID,
                                            final long id,
                                            final byte recordType,
                                            final byte[] record) throws Exception {
      appendAddRecordTransactional(txID, id, recordType, new ByteArrayEncoding(record));
   }

   @Override
   public void appendDeleteRecordTransactional(final long txID, final long id) throws Exception {
      appendDeleteRecordTransactional(txID, id, NullEncoding.instance);
   }

   @Override
   public void appendPrepareRecord(final long txID, final byte[] transactionData, final boolean sync) throws Exception {
      appendPrepareRecord(txID, new ByteArrayEncoding(transactionData), sync);
   }

   @Override
   public void appendPrepareRecord(final long txID,
                                   final EncodingSupport transactionData,
                                   final boolean sync) throws Exception {
      SyncIOCompletion syncCompletion = getSyncCallback(sync);

      appendPrepareRecord(txID, transactionData, sync, syncCompletion);

      if (syncCompletion != null) {
         syncCompletion.waitCompletion();
      }
   }

   @Override
   public void appendDeleteRecordTransactional(final long txID, final long id, final byte[] record) throws Exception {
      appendDeleteRecordTransactional(txID, id, new ByteArrayEncoding(record));
   }

   @Override
   public void appendUpdateRecord(final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object record,
                                  final boolean sync) throws Exception {
      SyncIOCompletion callback = getSyncCallback(sync);

      appendUpdateRecord(id, recordType, persister, record, sync, callback);

      if (callback != null) {
         callback.waitCompletion();
      }
   }

   @Override
   public void tryAppendUpdateRecord(final long id,
                                     final byte recordType,
                                     final Persister persister,
                                     final Object record,
                                     final JournalUpdateCallback updateCallback,
                                     final boolean sync,
                                     final boolean replaceableUpdate) throws Exception {
      SyncIOCompletion callback = getSyncCallback(sync);

      tryAppendUpdateRecord(id, recordType, persister, record, sync, replaceableUpdate, updateCallback, callback);

      if (callback != null) {
         callback.waitCompletion();
      }
   }

   @Override
   public void appendRollbackRecord(final long txID, final boolean sync) throws Exception {
      SyncIOCompletion syncCompletion = getSyncCallback(sync);

      appendRollbackRecord(txID, sync, syncCompletion);

      if (syncCompletion != null) {
         syncCompletion.waitCompletion();
      }

   }

   @Override
   public void appendDeleteRecord(final long id, final boolean sync) throws Exception {
      SyncIOCompletion callback = getSyncCallback(sync);

      appendDeleteRecord(id, sync, callback);

      if (callback != null) {
         callback.waitCompletion();
      }
   }

   @Override
   public void tryAppendDeleteRecord(final long id, JournalUpdateCallback updateCallback, final boolean sync) throws Exception {
      SyncIOCompletion callback = getSyncCallback(sync);

      tryAppendDeleteRecord(id, sync, updateCallback, callback);

      if (callback != null) {
         callback.waitCompletion();
      }
   }
   abstract void scheduleReclaim();

   protected SyncIOCompletion getSyncCallback(final boolean sync) {
      if (sync) {
         return new SimpleWaitIOCallback();
      }
      return DummyCallback.getInstance();
   }

   private static final class NullEncoding implements EncodingSupport {

      private static NullEncoding instance = new NullEncoding();

      @Override
      public void decode(final ActiveMQBuffer buffer) {
         // no-op
      }

      @Override
      public void encode(final ActiveMQBuffer buffer) {
         // no-op
      }

      @Override
      public int getEncodeSize() {
         return 0;
      }
   }

   @Override
   public int getFileSize() {
      return fileSize;
   }
}
