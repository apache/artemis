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
package org.apache.activemq.artemis.core.paging.impl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.utils.DataConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class PageTransactionInfoImpl implements PageTransactionInfo {


   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final AtomicIntegerFieldUpdater<PageTransactionInfoImpl> numberOfMessagesUpdater =
      AtomicIntegerFieldUpdater.newUpdater(PageTransactionInfoImpl.class, "numberOfMessages");
   private static final AtomicIntegerFieldUpdater<PageTransactionInfoImpl> numberOfPersistentMessagesUpdater =
      AtomicIntegerFieldUpdater.newUpdater(PageTransactionInfoImpl.class, "numberOfPersistentMessages");

   private long transactionID;

   private volatile Transaction preparedTX;

   private volatile long recordID = -1;

   private volatile boolean committed = false;

   private volatile boolean useRedelivery = false;

   private volatile boolean rolledback = false;

   private volatile int numberOfMessages = 0;

   private volatile int numberOfPersistentMessages = 0;

   private List<LateDelivery> lateDeliveries;

   /**
    * To be used during by the RebuildManager. When reading transactions not found transactions are marked as done.
    */
   private boolean orphaned;

   public PageTransactionInfoImpl(final long transactionID) {
      this();
      this.transactionID = transactionID;
   }

   @Override
   public boolean isOrphaned() {
      return orphaned;
   }

   @Override
   public PageTransactionInfoImpl setOrphaned(boolean orphaned) {
      this.orphaned = orphaned;
      return this;
   }

   public PageTransactionInfoImpl() {
   }

   @Override
   public Transaction getPreparedTransaction() {
      return preparedTX;
   }

   @Override
   public long getRecordID() {
      return recordID;
   }

   @Override
   public void setRecordID(final long recordID) {
      this.recordID = recordID;
   }

   @Override
   public long getTransactionID() {
      return transactionID;
   }

   @Override
   public boolean onUpdate(final int update, final StorageManager storageManager, PagingManager pagingManager) {
      int afterUpdate = numberOfMessagesUpdater.addAndGet(this, -update);
      return internalCheckSize(storageManager, pagingManager, afterUpdate);
   }

   @Override
   public boolean checkSize(StorageManager storageManager, PagingManager pagingManager) {
      return internalCheckSize(storageManager, pagingManager, numberOfMessagesUpdater.get(this));
   }

   public boolean internalCheckSize(StorageManager storageManager, PagingManager pagingManager, int size) {
      if (size <= 0) {
         if (storageManager != null) {
            try {
               storageManager.deletePageTransactional(this.recordID);
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.pageTxDeleteError(recordID, e);
            }
         }
         if (pagingManager != null) {
            pagingManager.removeTransaction(this.transactionID);
         }
         return false;
      } else {
         return true;
      }
   }

   @Override
   public void increment(final int durableSize, final int nonDurableSize) {
      numberOfPersistentMessagesUpdater.addAndGet(this, durableSize);
      numberOfMessagesUpdater.addAndGet(this, durableSize + nonDurableSize);
   }

   @Override
   public int getNumberOfMessages() {
      return numberOfMessagesUpdater.get(this);
   }

   // EncodingSupport implementation

   @Override
   public synchronized void decode(final ActiveMQBuffer buffer) {
      transactionID = buffer.readLong();
      numberOfMessagesUpdater.set(this, buffer.readInt());
      numberOfPersistentMessagesUpdater.set(this, numberOfMessagesUpdater.get(this));
      committed = true;
   }

   @Override
   public synchronized void encode(final ActiveMQBuffer buffer) {
      buffer.writeLong(transactionID);
      buffer.writeInt(numberOfPersistentMessagesUpdater.get(this));
   }

   @Override
   public synchronized int getEncodeSize() {
      return DataConstants.SIZE_LONG + DataConstants.SIZE_INT;
   }

   @Override
   public synchronized void commit() {
      if (lateDeliveries != null) {
         // This is to make sure deliveries that were touched before the commit arrived will be delivered
         for (LateDelivery pos : lateDeliveries) {
            pos.getSubscription().redeliver(pos.getIterator(), pos.getPageReference());
         }
         lateDeliveries.clear();
      }
      committed = true;
      lateDeliveries = null;
      preparedTX = null;
   }

   @Override
   public void store(final StorageManager storageManager,
                     PagingManager pagingManager,
                     final Transaction tx) throws Exception {
      storageManager.storePageTransaction(tx.getID(), this);
   }

   /*
    * This is to be used after paging. We will update the PageTransactions until they get all the messages delivered. On
    * that case we will delete the page TX
    */
   @Override
   public void storeUpdate(final StorageManager storageManager,
                           final PagingManager pagingManager,
                           final Transaction tx) throws Exception {
      internalUpdatePageManager(storageManager, pagingManager, tx, 1);
   }

   @Override
   public void reloadUpdate(final StorageManager storageManager,
                            final PagingManager pagingManager,
                            final Transaction tx,
                            final int increment) throws Exception {
      UpdatePageTXOperation updt = internalUpdatePageManager(storageManager, pagingManager, tx, increment);
      updt.setStored();
   }

   protected UpdatePageTXOperation internalUpdatePageManager(final StorageManager storageManager,
                                                             final PagingManager pagingManager,
                                                             final Transaction tx,
                                                             final int increment) {

      UpdatePageTXOperation pgtxUpdate = tx.getOrCreateOperation(TransactionPropertyIndexes.PAGE_TRANSACTION_UPDATE, () -> new UpdatePageTXOperation(storageManager, pagingManager));

      tx.setContainsPersistent();

      pgtxUpdate.addUpdate(this, increment);

      return pgtxUpdate;
   }

   @Override
   public boolean isCommit() {
      return committed;
   }

   @Override
   public void setCommitted(final boolean committed) {
      this.committed = committed;
   }

   @Override
   public void reloadPrepared(final Transaction tx) {
      this.preparedTX = tx;
      this.committed = false;
   }

   @Override
   public boolean isRollback() {
      return rolledback;
   }

   @Override
   public synchronized void rollback() {
      preparedTX = null;
      rolledback = true;
      committed = false;

      if (lateDeliveries != null) {
         for (LateDelivery pos : lateDeliveries) {
            pos.getSubscription().lateDeliveryRollback(pos.getPageReference().getPagedMessage().newPositionObject());
            onUpdate(1, null, pos.getSubscription().getPagingStore().getPagingManager());
         }
         lateDeliveries = null;
      }
   }

   @Override
   public String toString() {
      return "PageTransactionInfoImpl(transactionID=" + transactionID +
         ",id=" +
         recordID +
         ",numberOfMessages=" +
         numberOfMessages +
         ")";
   }

   @Override
   public synchronized boolean deliverAfterCommit(PageIterator iterator,
                                                  PageSubscription cursor,
                                                  PagedReference pagedReference) {
      logger.trace("deliver after commit on {}, pagedReference={}", cursor, pagedReference);

      if (committed && useRedelivery) {
         logger.trace("commit & useRedelivery on {}, pagedReference={}", cursor, pagedReference);

         cursor.addPendingDelivery(pagedReference.getPagedMessage());
         cursor.redeliver(iterator, pagedReference);
         return true;
      } else if (committed) {
         logger.trace("committed on {}, position={}, ignoring position", cursor, pagedReference);

         return false;
      } else if (rolledback) {
         logger.trace("rolled back, position ignored on {}, position={}", cursor, pagedReference);

         cursor.positionIgnored(pagedReference.getPagedMessage().newPositionObject());
         onUpdate(1, null, cursor.getPagingStore().getPagingManager());
         return true;
      } else {
         logger.trace("deliverAftercommit/else, marking useRedelivery on {}, position {}", cursor, pagedReference);

         useRedelivery = true;
         if (lateDeliveries == null) {
            lateDeliveries = new LinkedList<>();
         }
         cursor.addPendingDelivery(pagedReference.getPagedMessage());
         lateDeliveries.add(new LateDelivery(cursor, pagedReference, iterator));
         return true;
      }
   }

   /**
    * a Message shouldn't be delivered until it's committed For that reason the page-reference will be written right
    * away But in certain cases we can only deliver after the commit For that reason we will perform a late delivery
    * through the method redeliver.
    */
   private static class LateDelivery {

      final PageSubscription subscription;
      final PagedReference pagedReference;
      final PageIterator iterator;

      private LateDelivery(PageSubscription subscription, PagedReference pagedReference, PageIterator iterator) {
         this.subscription = subscription;
         this.pagedReference = pagedReference;
         this.iterator = iterator;
      }

      public PageSubscription getSubscription() {
         return subscription;
      }

      public PagedReference getPageReference() {
         return pagedReference;
      }

      public PageIterator getIterator() {
         return iterator;
      }
   }

   private static class UpdatePageTXOperation extends TransactionOperationAbstract {

      private final Map<PageTransactionInfo, AtomicInteger> countsToUpdate = new HashMap<>();

      private boolean stored = false;

      private final StorageManager storageManager;

      private final PagingManager pagingManager;

      private UpdatePageTXOperation(final StorageManager storageManager, final PagingManager pagingManager) {
         this.storageManager = storageManager;
         this.pagingManager = pagingManager;
      }

      public void setStored() {
         stored = true;
      }

      public void addUpdate(final PageTransactionInfo info, final int increment) {
         AtomicInteger counter = countsToUpdate.get(info);

         if (counter == null) {
            counter = new AtomicInteger(0);
            countsToUpdate.put(info, counter);
         }

         counter.addAndGet(increment);
      }

      @Override
      public void beforePrepare(Transaction tx) throws Exception {
         storeUpdates(tx);
      }

      @Override
      public void beforeCommit(Transaction tx) throws Exception {
         storeUpdates(tx);
      }

      @Override
      public void afterCommit(Transaction tx) {
         for (Map.Entry<PageTransactionInfo, AtomicInteger> entry : countsToUpdate.entrySet()) {
            entry.getKey().onUpdate(entry.getValue().intValue(), storageManager, pagingManager);
         }
      }

      private void storeUpdates(Transaction tx) throws Exception {
         if (!stored) {
            stored = true;
            for (Map.Entry<PageTransactionInfo, AtomicInteger> entry : countsToUpdate.entrySet()) {
               storageManager.updatePageTransaction(tx.getID(), entry.getKey(), entry.getValue().get());
            }
         }
      }

   }
}
