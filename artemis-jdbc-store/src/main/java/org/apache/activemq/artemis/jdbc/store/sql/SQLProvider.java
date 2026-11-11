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
package org.apache.activemq.artemis.jdbc.store.sql;

public interface SQLProvider {

   enum DatabaseStoreType {
      PAGE, MESSAGE_JOURNAL, BINDINGS_JOURNAL, LARGE_MESSAGE, NODE_MANAGER
   }

   long getMaxBlobSize();

   String[] getCreateJournalTableSQL();

   String getInsertJournalRecordsSQL();

   String getSelectJournalRecordsSQL();

   String getDeleteJournalRecordsSQL();

   String getDeleteJournalTxRecordsSQL();

   String getTableName();

   String[] getCreateFileTableSQL();

   String getInsertFileSQL();

   String getSelectFileNamesByExtensionSQL();

   String getSelectFileByFileName();

   String getReplaceLargeObjectSQL();

   String getAppendToLargeObjectSQL();

   String getReadLargeObjectSQL();

   String getDeleteFileSQL();

   String getUpdateFileNameByIdSQL();

   String getCopyFileRecordByIdSQL();

   String getDropFileTableSQL();

   String getCloneFileRecordByIdSQL();

   String getCountJournalRecordsSQL();

   boolean closeConnectionOnShutdown();

   String createNodeManagerStoreTableSQL();

   String createStateSQL();

   String createNodeIdSQL();

   String createPrimaryLockSQL();

   String createBackupLockSQL();

   String tryAcquirePrimaryLockSQL();

   String tryAcquireBackupLockSQL();

   String tryReleasePrimaryLockSQL();

   String tryReleaseBackupLockSQL();

   String isPrimaryLockedSQL();

   String isBackupLockedSQL();

   String renewPrimaryLockSQL();

   String renewBackupLockSQL();

   String currentTimestampSQL();

   String currentTimestampTimeZoneId();

   String writeStateSQL();

   String readStateSQL();

   String writeNodeIdSQL();

   String initializeNodeIdSQL();

   String readNodeIdSQL();

   interface Factory {

      SQLProvider create(String tableName, DatabaseStoreType dbStoreType);
   }
}
