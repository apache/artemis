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
package org.apache.activemq.artemis.core.paging.cursor.impl;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This class will encapsulate the persistent counters for the PagingSubscription
 */
public class PageSubscriptionCounterImpl extends BasePagingCounter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final long subscriptionID;

   // the journal record id that is holding the current value
   private long recordID = -1;

   /**
    * while we rebuild the counters, we will use the recordedValues
    */
   private volatile long recordedValue = -1;
   private static final AtomicLongFieldUpdater<PageSubscriptionCounterImpl> recordedValueUpdater = AtomicLongFieldUpdater.newUpdater(PageSubscriptionCounterImpl.class, "recordedValue");

   /**
    * while we rebuild the counters, we will use the recordedValues
    */
   private volatile long recordedSize = -1;
   private static final AtomicLongFieldUpdater<PageSubscriptionCounterImpl> recordedSizeUpdater = AtomicLongFieldUpdater.newUpdater(PageSubscriptionCounterImpl.class, "recordedSize");

   private PageSubscription subscription;

   private PagingStore pagingStore;

   private final StorageManager storage;

   private volatile long value;
   private static final AtomicLongFieldUpdater<PageSubscriptionCounterImpl> valueUpdater = AtomicLongFieldUpdater.newUpdater(PageSubscriptionCounterImpl.class, "value");

   private volatile long persistentSize;
   private static final AtomicLongFieldUpdater<PageSubscriptionCounterImpl> persistentSizeUpdater = AtomicLongFieldUpdater.newUpdater(PageSubscriptionCounterImpl.class, "persistentSize");

   private volatile long added;
   private static final AtomicLongFieldUpdater<PageSubscriptionCounterImpl> addedUpdater = AtomicLongFieldUpdater.newUpdater(PageSubscriptionCounterImpl.class, "added");

   private volatile long addedPersistentSize;
   private static final AtomicLongFieldUpdater<PageSubscriptionCounterImpl> addedPersistentSizeUpdater = AtomicLongFieldUpdater.newUpdater(PageSubscriptionCounterImpl.class, "addedPersistentSize");

   private LinkedList<PendingCounter> loadList;

   public PageSubscriptionCounterImpl(final StorageManager storage,
                                      final long subscriptionID) {
      this.subscriptionID = subscriptionID;
      this.storage = storage;
   }

   @Override
   public void markRebuilding() {
      if (logger.isDebugEnabled()) {
         logger.debug("Subscription {} marked for rebuilding", subscriptionID);
      }
      super.markRebuilding();
      recordedSizeUpdater.set(this, persistentSizeUpdater.get(this));
      recordedValueUpdater.set(this, recordedValueUpdater.get(this));
      try {
         reset();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

   @Override
   public void finishRebuild() {
      super.finishRebuild();
      if (logger.isDebugEnabled()) {
         logger.debug("Subscription {} finished rebuilding", subscriptionID);
      }
      snapshot();
      addedUpdater.set(this, valueUpdater.get(this));
      addedPersistentSizeUpdater.set(this, persistentSizeUpdater.get(this));
   }

   @Override
   public long getValueAdded() {
      return addedUpdater.get(this);
   }

   @Override
   public long getValue() {
      if (isRebuilding()) {
         if (logger.isTraceEnabled()) {
            logger.trace("returning getValue from isPending on subscription {}, recordedValue={}, addedUpdater={}", recordedValueUpdater.get(this), addedUpdater.get(this));
         }
         return recordedValueUpdater.get(this);
      }
      if (logger.isTraceEnabled()) {
         logger.trace("returning regular getValue subscription {}, value={}", subscriptionID, valueUpdater.get(this));
      }
      return valueUpdater.get(this);
   }

   @Override
   public long getPersistentSizeAdded() {
      return addedPersistentSizeUpdater.get(this);
   }

   @Override
   public long getPersistentSize() {
      if (isRebuilding()) {
         if (logger.isTraceEnabled()) {
            logger.trace("returning getPersistentSize from isPending on subscription {}, recordedSize={}. addedSize={}", subscriptionID, recordedSizeUpdater.get(this), addedPersistentSizeUpdater.get(this));
         }
         return recordedSizeUpdater.get(this);
      }
      if (logger.isTraceEnabled()) {
         logger.trace("returning regular getPersistentSize subscription {}, value={}", subscriptionID, persistentSizeUpdater.get(this));
      }
      return persistentSizeUpdater.get(this);
   }

   @Override
   public void increment(Transaction tx, int add, long size) throws Exception {
      if (tx == null) {
         process(add, size);
      } else {
         applyIncrementOnTX(tx, add, size);
      }
   }

   /**
    * This method will install the TXs
    */
   @Override
   public void applyIncrementOnTX(Transaction tx, int add, long size) {
      CounterOperations oper = tx.getOrCreateOperation(TransactionPropertyIndexes.PAGE_COUNT_INC, CounterOperations::new);
      oper.operations.add(new ItemOper(this, add, size));
   }

   @Override
   public synchronized void loadValue(final long recordID, final long value, long size) {
      if (logger.isDebugEnabled()) {
         logger.debug("Counter for subscription {} reloading recordID={}, value={}, size={}", this.subscriptionID, recordID, value, size);
      }
      this.recordID = recordID;
      recordedValueUpdater.set(this, value);
      recordedSizeUpdater.set(this, size);
      valueUpdater.set(this, value);
      persistentSizeUpdater.set(this, size);
      addedUpdater.set(this, value);
   }

   private void process(final int add, final long size) {
      if (logger.isTraceEnabled()) {
         logger.trace("process subscription={} add={}, size={}", subscriptionID, add, size);
      }
      long value = valueUpdater.addAndGet(this, add);
      persistentSizeUpdater.addAndGet(this, size);
      if (add > 0) {
         addedUpdater.addAndGet(this, add);
         addedPersistentSizeUpdater.addAndGet(this, size);

         /// we could have pagingStore null on tests, so we need to validate if pagingStore != null before anything...
         if (pagingStore != null && pagingStore.getPageFullMessagePolicy() != null && !pagingStore.isPageFull()) {
            checkAdd(value);
         }
      }

      if (isRebuilding()) {
         recordedValueUpdater.addAndGet(this, add);
         recordedSizeUpdater.addAndGet(this, size);
      }
   }

   private void checkAdd(long numberOfMessages) {
      Long pageLimitMessages = pagingStore.getPageLimitMessages();
      if (pageLimitMessages != null) {
         if (numberOfMessages >= pageLimitMessages.longValue()) {
            pagingStore.pageFull(this.subscription);
         }
      }
   }

   @Override
   public void delete() throws Exception {
      Transaction tx = new TransactionImpl(storage);

      delete(tx);

      tx.commit();
   }

   void reset() throws Exception {
      Transaction tx = new TransactionImpl(storage);

      delete(tx, true);

      tx.commit();
   }

   @Override
   public void delete(Transaction tx) throws Exception {
      delete(tx, false);
   }

   private void delete(Transaction tx, boolean keepZero) throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("Subscription {} delete, keepZero={}", subscriptionID, keepZero);
      }
      // always lock the StorageManager first.
      try (ArtemisCloseable lock = storage.closeableReadLock()) {
         synchronized (this) {
            if (recordID >= 0) {
               if (logger.isTraceEnabled()) {
                  logger.trace("Deleting page counter with recordID={}, using TX={}", this.recordID, tx.getID());
               }
               storage.deletePageCounter(tx.getID(), this.recordID);
               tx.setContainsPersistent();
            }

            if (keepZero) {
               tx.setContainsPersistent();
               recordID = storage.storePageCounter(tx.getID(), subscriptionID, 0L, 0L);
            } else {
               recordID = -1;
            }

            valueUpdater.set(this, 0);
            persistentSizeUpdater.set(this, 0);
         }
      }
   }

   @Override
   public void loadInc(long id, int add, long size) {
      if (loadList == null) {
         loadList = new LinkedList<>();
      }

      loadList.add(new PendingCounter(id, add, size));
   }

   @Override
   public void processReload() {
      if (loadList != null) {
         try {
            long tx = -1L;
            logger.debug("Removing increment records on cursor {}", subscriptionID);
            for (PendingCounter incElement : loadList) {
               if (tx < 0) {
                  tx = storage.generateID();
               }
               storage.deletePageCounter(tx, incElement.id);
            }
            if (tx >= 0) {
               storage.commit(tx);
            }
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
         loadList.clear();
         loadList = null;
      }
   }

   /**
    * This method should always be called from a single threaded executor
    */
   @Override
   public synchronized void snapshot() {
      if (isRebuilding()) {
         if (logger.isDebugEnabled()) {
            logger.debug("snapshot call ignored as cursor is being rebuilt for {}", subscriptionID);
         }
         return;
      }

      if (!storage.isStarted()) {
         logger.debug("Storage is not active, ignoring snapshot call on {}", subscriptionID);
         return;
      }

      long valueReplace = valueUpdater.get(this);
      long sizeReplace = persistentSizeUpdater.get(this);

      long newRecordID = -1;

      long txCleanup = -1;

      try {
         if (recordID >= 0) {
            if (txCleanup < 0) {
               txCleanup = storage.generateID();
            }
            storage.deletePageCounter(txCleanup, recordID);
            recordID = -1;
         }

         if (valueReplace > 0) {
            if (txCleanup < 0) {
               txCleanup = storage.generateID();
            }
            newRecordID = storage.storePageCounter(txCleanup, subscriptionID, valueReplace, sizeReplace);
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Replacing page-counter record = {} by record = {} on subscriptionID = {} for queue = {}, value = {}, size = {}",
                         recordID, newRecordID, subscriptionID, subscription.getQueue().getName(), valueReplace, sizeReplace);
         }

         if (txCleanup >= 0) {
            storage.commit(txCleanup);
         }
      } catch (Exception e) {
         newRecordID = recordID;

         ActiveMQServerLogger.LOGGER.problemCleaningPagesubscriptionCounter(e);
         if (txCleanup >= 0) {
            try {
               storage.rollback(txCleanup);
            } catch (Exception ignored) {
            }
         }
      } finally {
         recordID = newRecordID;
         recordedValueUpdater.set(this, valueReplace);
         recordedSizeUpdater.set(this, sizeReplace);
      }
   }

   private static class ItemOper {

      private ItemOper(PageSubscriptionCounterImpl counter, int add, long persistentSize) {
         this.counter = counter;
         this.amount = add;
         this.persistentSize = persistentSize;
      }

      PageSubscriptionCounterImpl counter;

      int amount;

      long persistentSize;
   }

   private static class CounterOperations extends TransactionOperationAbstract implements TransactionOperation {

      LinkedList<ItemOper> operations = new LinkedList<>();

      @Override
      public void afterCommit(Transaction tx) {
         for (ItemOper oper : operations) {
            oper.counter.process(oper.amount, oper.persistentSize);
         }
      }
   }

   private static class PendingCounter {
      private static final AtomicIntegerFieldUpdater<PendingCounter> COUNT_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(PendingCounter.class, "count");

      private static final AtomicLongFieldUpdater<PendingCounter> SIZE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(PendingCounter.class, "persistentSize");

      private final long id;
      private volatile int count;
      private volatile long persistentSize;

      PendingCounter(long id, int count, long persistentSize) {
         super();
         this.id = id;
         this.count = count;
         this.persistentSize = persistentSize;
      }

      public long getId() {
         return id;
      }

      public int getCount() {
         return count;
      }

      public long getPersistentSize() {
         return persistentSize;
      }

      public void addAndGet(int count, long persistentSize) {
         COUNT_UPDATER.addAndGet(this, count);
         SIZE_UPDATER.addAndGet(this, persistentSize);
      }
   }

   @Override
   public PageSubscriptionCounter setSubscription(PageSubscription subscription) {
      this.subscription = subscription;
      this.pagingStore = subscription.getPagingStore();
      return this;
   }
}