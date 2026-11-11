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
package org.apache.activemq.artemis.core.paging;

import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.transaction.Transaction;

public interface PageTransactionInfo extends EncodingSupport {

   boolean isCommit();

   boolean isRollback();

   void setCommitted(boolean committed);

   void reloadPrepared(Transaction transaction);

   /*
    * When we reload a transaction,
    * We may have to add the counters after commit.
    */
   Transaction getPreparedTransaction();

   void commit();

   void rollback();

   long getRecordID();

   void setRecordID(long id);

   long getTransactionID();

   void store(StorageManager storageManager, PagingManager pagingManager, Transaction tx) throws Exception;

   void storeUpdate(StorageManager storageManager, PagingManager pagingManager, Transaction tx) throws Exception;

   void reloadUpdate(StorageManager storageManager,
                     PagingManager pagingManager,
                     Transaction tx,
                     int increment) throws Exception;

   // To be used after the update was stored or reload
   boolean onUpdate(int update, StorageManager storageManager, PagingManager pagingManager);

   boolean checkSize(StorageManager storageManager, PagingManager pagingManager);

   void increment(int durableSize, int nonDurableSize);

   int getNumberOfMessages();

   /**
    * This method will hold the position to be delivered later in case this transaction is pending. If the tx is not
    * pending, it will return false, so the caller can deliver it right away
    *
    * @return true if the message will be delivered later, false if it should be delivered right away
    */
   boolean deliverAfterCommit(PageIterator pageIterator, PageSubscription cursor, PagedReference pagedMessage);

   /**
    * Used on PageRebuildManager to cleanup orphaned Page Transactions
    */
   boolean isOrphaned();

   PageTransactionInfo setOrphaned(boolean orphaned);

}
