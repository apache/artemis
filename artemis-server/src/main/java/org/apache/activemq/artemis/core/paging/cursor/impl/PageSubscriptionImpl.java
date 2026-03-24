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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageCache;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.cursor.PagedReferenceImpl;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.jboss.logging.Logger;

import static org.apache.activemq.artemis.core.server.impl.QueueImpl.DELIVERY_TIMEOUT;

public final class PageSubscriptionImpl implements PageSubscription {

   private static final Logger logger = Logger.getLogger(PageSubscriptionImpl.class);

   private static final PagedReference dummyPagedRef = new PagedReferenceImpl(null, null, null);

   private boolean empty = true;

   /** for tests */
   public AtomicInteger getScheduledCleanupCount() {
      return scheduledCleanupCount;
   }

   // Number of scheduled cleanups, to avoid too many schedules
   private final AtomicInteger scheduledCleanupCount = new AtomicInteger(0);

   private volatile boolean autoCleanup = true;

   private final StorageManager store;

   private final long cursorId;

   private Queue queue;

   private final boolean persistent;

   private final Filter filter;

   private final PagingStore pageStore;

   private final PageCursorProvider cursorProvider;

   private volatile PagePosition lastAckedPosition;

   private List<PagePosition> recoveredACK;

   private final SortedMap<Long, PageCursorInfo> consumedPages = new TreeMap<>();

   private final PageSubscriptionCounter counter;

   private final ArtemisExecutor executor;

   private final AtomicLong deliveredCount = new AtomicLong(0);

   private final AtomicLong deliveredSize = new AtomicLong(0);

   // Each CursorIterator will record their current PageReader in this map
   private final ConcurrentLongHashMap<PageReader> pageReaders = new ConcurrentLongHashMap<>();

   /** this variable governs if we need to schedule another runner to look after the scanList. */
   private boolean pageScanNeeded = true;
   private final LinkedList<PageScan> scanList = new LinkedList();

   private static class PageScan {
      final BooleanSupplier retryBeforeScan;
      final ToIntFunction<PagedReference> scanFunction;
      final Runnable found;
      final Runnable notFound;

      public ToIntFunction<PagedReference> getScanFunction() {
         return scanFunction;
      }

      public Runnable getFound() {
         return found;
      }

      public Runnable getNotfound() {
         return notFound;
      }

      PageScan(BooleanSupplier retryBeforeScan, ToIntFunction<PagedReference> scanFunction, Runnable found, Runnable notFound) {
         this.retryBeforeScan = retryBeforeScan;
         this.scanFunction = scanFunction;
         this.found = found;
         this.notFound = notFound;
      }
   }

   @Override
   public void scanAck(BooleanSupplier retryBeforeScan, ToIntFunction<PagedReference> scanFunction, Runnable found, Runnable notFound) {
      PageScan scan = new PageScan(retryBeforeScan, scanFunction, found, notFound);
      boolean pageScanNeededLocal;
      synchronized (scanList) {
         scanList.add(scan);
         pageScanNeededLocal = this.pageScanNeeded;
         this.pageScanNeeded = false;
      }

      if (pageScanNeededLocal) {
         executor.execute(this::performScanAck);
      }
   }

   private void performScanAck() {
      try {
         PageScan[] localScanList;
         synchronized (scanList) {
            this.pageScanNeeded = true;
            if (scanList.size() == 0) {
               return;
            }
            localScanList = scanList.stream().toArray(i -> new PageScan[i]);
            scanList.clear();
         }

         int retriedFound = 0;
         for (int i = 0; i < localScanList.length; i++) {
            PageScan scanElemen = localScanList[i];
            if (scanElemen.retryBeforeScan != null && scanElemen.retryBeforeScan.getAsBoolean()) {
               localScanList[i] = null;
               retriedFound++;
            }
         }

         if (retriedFound == localScanList.length) {
            return;
         }

         if (!isPaging()) {
            // this would mean that between the submit and now the system left paging mode
            // at this point we will just return everything as notFound
            for (int i = 0; i < localScanList.length; i++) {
               PageScan scanElemen = localScanList[i];
               if (scanElemen != null && scanElemen.notFound != null) {
                  scanElemen.notFound.run();
               }
            }

            return;
         }

         LinkedList<Runnable> afterCommitList = new LinkedList<>();
         TransactionImpl tx = new TransactionImpl(store);
         tx.addOperation(new TransactionOperationAbstract() {
            @Override
            public void afterCommit(Transaction tx) {
               for (Runnable r : afterCommitList) {
                  try {
                     r.run();
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                  }
               }
            }
         });
         PageIterator iterator = this.iterator(true);
         try {
            while (iterator.hasNext()) {
               PagedReference reference = iterator.next();
               boolean keepMoving = false;
               for (int i = 0; i < localScanList.length; i++) {
                  PageScan scanElemen = localScanList[i];
                  if (scanElemen == null) {
                     continue;
                  }

                  int result = scanElemen.scanFunction.applyAsInt(reference);

                  if (result >= 0) {
                     if (result == 0) {
                        try {
                           PageSubscriptionImpl.this.ackTx(tx, reference);
                           if (scanElemen.found != null) {
                              afterCommitList.add(scanElemen.found);
                           }
                        } catch (Throwable e) {
                           logger.warn(e.getMessage(), e);
                        }
                     } else {
                        if (scanElemen.notFound != null) {
                           scanElemen.notFound.run();
                        }
                     }
                     localScanList[i] = null;
                  } else {
                     keepMoving = true;
                  }
               }

               if (!keepMoving) {
                  break;
               }
            }
         } finally {
            iterator.close();
         }

         for (int i = 0; i < localScanList.length; i++) {
            if (localScanList[i] != null && localScanList[i].notFound != null) {
               localScanList[i].notFound.run();
            }
            localScanList[i] = null;
         }

         if (afterCommitList.size() > 0) {
            try {
               tx.commit();
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }
   }

   PageSubscriptionImpl(final PageCursorProvider cursorProvider,
                        final PagingStore pageStore,
                        final StorageManager store,
                        final ArtemisExecutor executor,
                        final Filter filter,
                        final long cursorId,
                        final boolean persistent) {
      this.pageStore = pageStore;
      this.store = store;
      this.cursorProvider = cursorProvider;
      this.cursorId = cursorId;
      this.executor = executor;
      this.filter = filter;
      this.persistent = persistent;
      this.counter = new PageSubscriptionCounterImpl(store, this, executor, persistent, cursorId);
   }


   @Override
   public PagingStore getPagingStore() {
      return pageStore;
   }

   @Override
   public Queue getQueue() {
      return queue;
   }

   @Override
   public boolean isPaging() {
      return pageStore.isPaging();
   }

   @Override
   public void setQueue(Queue queue) {
      this.queue = queue;
   }

   @Override
   public void disableAutoCleanup() {
      autoCleanup = false;
   }

   @Override
   public void enableAutoCleanup() {
      autoCleanup = true;
   }

   public PageCursorProvider getProvider() {
      return cursorProvider;
   }

   @Override
   public void notEmpty() {
      synchronized (consumedPages) {
         this.empty = false;
      }

   }

   @Override
   public void bookmark(PagePosition position) throws Exception {
      PageCursorInfo cursorInfo = getPageInfo(position);

      if (position.getMessageNr() > 0) {
         cursorInfo.confirmed.addAndGet(position.getMessageNr());
      }

      confirmPosition(position);
   }

   @Override
   public long getMessageCount() {
      if (empty) {
         return 0;
      } else {
         return counter.getValue() - deliveredCount.get();
      }
   }

   @Override
   public long getPersistentSize() {
      if (empty) {
         return 0;
      } else {
         //A negative value could happen if an old journal was loaded that didn't have
         //size metrics for old records
         long messageSize = counter.getPersistentSize() - deliveredSize.get();
         return messageSize > 0 ? messageSize : 0;
      }
   }

   @Override
   public PageSubscriptionCounter getCounter() {
      return counter;
   }

   /**
    * A page marked as complete will be ignored until it's cleared.
    * <p>
    * Usually paging is a stream of messages but in certain scenarios (such as a pending prepared
    * TX) we may have big holes on the page streaming, and we will need to ignore such pages on the
    * cursor/subscription.
    */
   @Override
   public boolean reloadPageCompletion(PagePosition position) throws Exception {
      if (!pageStore.checkPageFileExists((int)position.getPageNr())) {
         return false;
      }
      // if the current page is complete, we must move it out of the way
      if (pageStore.getCurrentPage() != null &&
          pageStore.getCurrentPage().getPageId() == position.getPageNr()) {
         pageStore.forceAnotherPage();
      }
      PageCursorInfo info = new PageCursorInfo(position.getPageNr(), position.getMessageNr());
      info.setCompleteInfo(position);
      synchronized (consumedPages) {
         consumedPages.put(Long.valueOf(position.getPageNr()), info);
      }

      return true;
   }

   @Override
   public void scheduleCleanupCheck() {
      if (autoCleanup) {
         if (logger.isTraceEnabled()) {
            logger.trace("Scheduling cleanup", new Exception("trace"));
         }
         if (scheduledCleanupCount.get() > 2) {
            return;
         }

         scheduledCleanupCount.incrementAndGet();
         executor.execute(new Runnable() {

            @Override
            public void run() {
               try {
                  if (autoCleanup) {
                     cleanupEntries(false);
                  }
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.problemCleaningCursorPages(e);
               } finally {
                  scheduledCleanupCount.decrementAndGet();
               }
            }
         });
      }
   }

   @Override
   public void onPageModeCleared(Transaction tx) throws Exception {
      if (counter != null) {
         // this could be null on testcases
         counter.delete(tx);
      }
      this.empty = true;
   }

   /**
    * It will cleanup all the records for completed pages
    */
   @Override
   public void cleanupEntries(final boolean completeDelete) throws Exception {
      if (completeDelete) {
         counter.delete();
      }
      if (logger.isTraceEnabled()) {
         logger.trace("cleanupEntries", new Exception("trace"));
      }
      Transaction tx = new TransactionImpl(store);

      boolean persist = false;

      final ArrayList<PageCursorInfo> completedPages = new ArrayList<>();

      // First get the completed pages using a lock
      synchronized (consumedPages) {
         // lastAckedPosition = null means no acks were done yet, so we are not ready to cleanup
         if (lastAckedPosition == null) {
            return;
         }

         for (Entry<Long, PageCursorInfo> entry : consumedPages.entrySet()) {
            PageCursorInfo info = entry.getValue();

            if (info.isDone() && !info.isPendingDelete()) {
               Page currentPage = pageStore.getCurrentPage();

               if (currentPage != null && entry.getKey() == pageStore.getCurrentPage().getPageId() &&
                  currentPage.isLive()) {
                  logger.trace("We can't clear page " + entry.getKey() +
                                  " now since it's the current page");
               } else {
                  info.setPendingDelete();
                  completedPages.add(entry.getValue());
               }
            }
         }
      }

      for (PageCursorInfo infoPG : completedPages) {
         // HORNETQ-1017: There are a few cases where a pending transaction might set a big hole on the page system
         //               where we need to ignore these pages in case of a restart.
         //               for that reason when we delete complete ACKs we store a single record per page file that will
         //               be removed once the page file is deleted
         //               notice also that this record is added as part of the same transaction where the information is deleted.
         //               In case of a TX Failure (a crash on the server) this will be recovered on the next cleanup once the
         //               server is restarted.
         // first will mark the page as complete
         if (isPersistent()) {
            PagePosition completePage = new PagePositionImpl(infoPG.getPageId(), infoPG.getNumberOfMessages());
            infoPG.setCompleteInfo(completePage);
            store.storePageCompleteTransactional(tx.getID(), this.getId(), completePage);
            if (!persist) {
               persist = true;
               tx.setContainsPersistent();
            }
         }

         // it will delete the page ack records
         for (PagePosition pos : infoPG.acks) {
            if (pos.getRecordID() >= 0) {
               store.deleteCursorAcknowledgeTransactional(tx.getID(), pos.getRecordID());
               if (!persist) {
                  // only need to set it once
                  tx.setContainsPersistent();
                  persist = true;
               }
            }
         }

         infoPG.acks.clear();
         infoPG.acks = Collections.synchronizedSet(new LinkedHashSet<PagePosition>());
         infoPG.removedReferences.clear();
         infoPG.removedReferences = new ConcurrentHashSet<>();
      }

      tx.addOperation(new TransactionOperationAbstract() {

         @Override
         public void afterCommit(final Transaction tx1) {
            executor.execute(new Runnable() {

               @Override
               public void run() {
                  if (!completeDelete) {
                     cursorProvider.scheduleCleanup();
                  }
               }
            });
         }
      });

      tx.commit();

   }

   @Override
   public String toString() {
      return "PageSubscriptionImpl [cursorId=" + cursorId + ", queue=" + queue + ", filter = " + filter + "]";
   }

   private PagedReference getReference(PagePosition pos) {
      PagedMessage pagedMessage;
      PageReader pageReader = pageReaders.get(pos.getPageNr());
      if (pageReader != null) {
         pagedMessage = pageReader.getMessage(pos, true, false);
      } else {
         pagedMessage = cursorProvider.getMessage(pos);
      }

      return cursorProvider.newReference(pos, pagedMessage, this);
   }

   @Override
   public PageIterator iterator() {
      return new CursorIterator();
   }

   @Override
   public PageIterator iterator(boolean browsing) {
      return new CursorIterator(browsing);
   }

   private PagedReference internalGetNext(final PagePositionAndFileOffset pos) {
      PagePosition retPos = pos.nextPagePostion();

      PageCache cache = null;

      while (retPos.getPageNr() <= pageStore.getCurrentWritingPage()) {
         PageReader pageReader = pageReaders.get(retPos.getPageNr());
         if (pageReader == null) {
            cache = cursorProvider.getPageCache(retPos.getPageNr());
         } else {
            cache = pageReader;
         }

         /**
          * In following cases, we should move to the next page
          * case 1: cache == null means file might be deleted unexpectedly.
          * case 2: cache is not live and  contains no messages.
          * case 3: cache is not live and next message is beyond what's available at the current page.
          */
         if (cache == null || (!cache.isLive() && (retPos.getMessageNr() >= cache.getNumberOfMessages() || cache.getNumberOfMessages() == 0))) {
            // Save current empty page every time we move to next page
            saveEmptyPageAsConsumedPage(cache);
            retPos = moveNextPage(retPos);
            cache = null;
         } else {
            // We need to break loop to get message if cache is live or the next message number is in the range of current page
            break;
         }
      }

      if (cache != null) {
         PagedMessage serverMessage;
         if (cache instanceof PageReader) {
            serverMessage = ((PageReader) cache).getMessage(retPos, false, true);
            PageCache previousPageCache = pageReaders.putIfAbsent(retPos.getPageNr(), (PageReader) cache);
            if (previousPageCache != null && previousPageCache != cache) {
               // Maybe other cursor iterators have added page reader, we have to close this one to avoid file leak
               cache.close();
            }
         } else {
            serverMessage = cache.getMessage(retPos);
         }

         if (serverMessage != null) {
            return cursorProvider.newReference(retPos, serverMessage, this);
         }
      }
      return null;
   }

   private PagePosition moveNextPage(final PagePosition pos) {
      PagePosition retPos = pos;
      PageReader pageReader = pageReaders.remove(pos.getPageNr());
      if (pageReader != null) {
         pageReader.close();
      }
      while (true) {
         retPos = retPos.nextPage();
         synchronized (consumedPages) {
            PageCursorInfo pageInfo = consumedPages.get(retPos.getPageNr());
            // any deleted or complete page will be ignored on the moveNextPage, we will just keep going
            if (pageInfo == null || (!pageInfo.isPendingDelete() && pageInfo.getCompleteInfo() == null)) {
               return retPos;
            }
         }
      }
   }

   private boolean routed(PagedMessage message) {
      long id = getId();

      for (long qid : message.getQueueIDs()) {
         if (qid == id) {
            return true;
         }
      }
      return false;
   }

   /**
    *
    */
   private synchronized PagePositionAndFileOffset getStartPosition() {
      return new PagePositionAndFileOffset(-1, new PagePositionImpl(pageStore.getFirstPage(), -1));
   }

   @Override
   public void confirmPosition(final Transaction tx, final PagePosition position) throws Exception {
      // if the cursor is persistent
      if (persistent) {
         store.storeCursorAcknowledgeTransactional(tx.getID(), cursorId, position);
      }
      installTXCallback(tx, position);

   }

   private void confirmPosition(final Transaction tx, final PagePosition position, final long persistentSize) throws Exception {
      // if the cursor is persistent
      if (persistent) {
         store.storeCursorAcknowledgeTransactional(tx.getID(), cursorId, position);
      }
      installTXCallback(tx, position, persistentSize);
   }

   @Override
   public void ackTx(final Transaction tx, final PagedReference reference) throws Exception {
      //pre-calculate persistentSize
      final long persistentSize = getPersistentSize(reference);

      confirmPosition(tx, reference.getPosition(), persistentSize);

      counter.increment(tx, -1, -persistentSize);

      PageTransactionInfo txInfo = getPageTransaction(reference);
      if (txInfo != null) {
         txInfo.storeUpdate(store, pageStore.getPagingManager(), tx);
      }
   }

   @Override
   public void ack(final PagedReference reference) throws Exception {
      // Need to do the ACK and counter atomically (inside a TX) or the counter could get out of sync
      Transaction tx = new TransactionImpl(this.store);
      ackTx(tx, reference);
      tx.commit();
   }

   @Override
   public boolean contains(PagedReference ref) throws Exception {
      // We first verify if the message was routed to this queue
      boolean routed = false;

      for (long idRef : ref.getPagedMessage().getQueueIDs()) {
         if (idRef == this.cursorId) {
            routed = true;
            break;
         }
      }
      if (!routed) {
         return false;
      } else {
         // if it's been routed here, we have to verify if it was acked
         return !getPageInfo(ref.getPosition()).isAck(ref.getPosition());
      }
   }

   @Override
   public void confirmPosition(final PagePosition position) throws Exception {
      // if we are dealing with a persistent cursor
      if (persistent) {
         store.storeCursorAcknowledge(cursorId, position);
      }

      store.afterCompleteOperations(new IOCallback() {
         volatile String error = "";

         @Override
         public void onError(final int errorCode, final String errorMessage) {
            error = " errorCode=" + errorCode + ", msg=" + errorMessage;
            ActiveMQServerLogger.LOGGER.pageSubscriptionError(this, error);
         }

         @Override
         public void done() {
            processACK(position);
         }

         @Override
         public String toString() {
            return IOCallback.class.getSimpleName() + "(" + PageSubscriptionImpl.class.getSimpleName() + ") " + error;
         }
      });
   }

   @Override
   public long getFirstPage() {
      synchronized (consumedPages) {
         if (empty && consumedPages.isEmpty()) {
            return -1;
         }
         long lastPageSeen = 0;
         for (Map.Entry<Long, PageCursorInfo> info : consumedPages.entrySet()) {
            lastPageSeen = info.getKey();
            if (!info.getValue().isDone() && !info.getValue().isPendingDelete()) {
               return info.getKey();
            }
         }
         return lastPageSeen;
      }

   }

   @Override
   public void addPendingDelivery(final PagePosition position) {
      PageCursorInfo info = getPageInfo(position);

      if (info != null) {
         info.incrementPendingTX();
      }
   }

   @Override
   public void removePendingDelivery(final PagePosition position) {
      PageCursorInfo info = getPageInfo(position);

      if (info != null) {
         info.decrementPendingTX();
      }
   }

   @Override
   public void redeliver(final PageIterator iterator, final PagePosition position) {
      iterator.redeliver(position);

      synchronized (consumedPages) {
         PageCursorInfo pageInfo = consumedPages.get(position.getPageNr());
         if (pageInfo != null) {
            pageInfo.decrementPendingTX();
         } else {
            // this shouldn't really happen.
         }
      }
   }

   @Override
   public PagedMessage queryMessage(PagePosition pos) {
      PageReader pageReader = pageReaders.get(pos.getPageNr());
      if (pageReader != null) {
         return pageReader.getMessage(pos, true, false);
      } else {
         return cursorProvider.getMessage(pos);
      }
   }

   /**
    * Theres no need to synchronize this method as it's only called from journal load on startup
    */
   @Override
   public void reloadACK(final PagePosition position) {
      if (recoveredACK == null) {
         recoveredACK = new LinkedList<>();
      }

      recoveredACK.add(position);
   }

   @Override
   public void reloadPreparedACK(final Transaction tx, final PagePosition position) {
      deliveredCount.incrementAndGet();
      installTXCallback(tx, position);
   }

   @Override
   public void positionIgnored(final PagePosition position) {
      processACK(position);
   }

   @Override
   public void lateDeliveryRollback(PagePosition position) {
      PageCursorInfo cursorInfo = processACK(position);
      cursorInfo.decrementPendingTX();
   }

   @Override
   public boolean isComplete(long page) {
      logger.tracef("%s isComplete %d", this, page);
      synchronized (consumedPages) {
         if (empty && consumedPages.isEmpty()) {
            if (logger.isTraceEnabled()) {
               logger.tracef("isComplete(%d)::Subscription %s has empty=%s, consumedPages.isEmpty=%s", page, this, empty, consumedPages.isEmpty());
            }
            return true;
         }

         PageCursorInfo info = consumedPages.get(page);

         if (info == null && empty) {
            logger.tracef("isComplete(%d)::::Couldn't find info and it is empty", page);
            return true;
         } else {
            boolean isDone = info != null && info.isDone();
            if (logger.isTraceEnabled()) {
               logger.tracef("isComplete(%d):: found info=%s, isDone=%s", (Object) page, info, isDone);
            }
            return isDone;
         }
      }
   }

   /**
    * All the data associated with the cursor should go away here
    */
   @Override
   public void destroy() throws Exception {
      final long tx = store.generateID();
      try {

         boolean isPersistent = false;

         synchronized (consumedPages) {
            for (PageCursorInfo cursor : consumedPages.values()) {
               for (PagePosition info : cursor.acks) {
                  if (info.getRecordID() >= 0) {
                     isPersistent = true;
                     store.deleteCursorAcknowledgeTransactional(tx, info.getRecordID());
                  }
               }
               PagePosition completeInfo = cursor.getCompleteInfo();
               if (completeInfo != null && completeInfo.getRecordID() >= 0) {
                  store.deletePageComplete(completeInfo.getRecordID());
                  cursor.setCompleteInfo(null);
               }
            }
         }

         if (isPersistent) {
            store.commit(tx);
         }

         cursorProvider.close(this);
      } catch (Exception e) {
         try {
            store.rollback(tx);
         } catch (Exception ignored) {
            // exception of the exception.. nothing that can be done here
         }
      }
   }

   @Override
   public long getId() {
      return cursorId;
   }

   @Override
   public boolean isPersistent() {
      return persistent;
   }

   @Override
   public void processReload() throws Exception {
      if (recoveredACK != null) {
         if (logger.isTraceEnabled()) {
            logger.trace("********** processing reload!!!!!!!");
         }
         Collections.sort(recoveredACK);

         long txDeleteCursorOnReload = -1;

         for (PagePosition pos : recoveredACK) {
            lastAckedPosition = pos;
            PageCursorInfo pageInfo = getPageInfo(pos);
            PageCache cache = null;

            if (pageInfo != null) {
               cache = pageInfo.getValidCache();
            }

            if (cache == null || pos.getMessageNr() >= 0 && cache.getMessage(pos) == null) {
               try {
                  ActiveMQServerLogger.LOGGER.pageNotFound(pos, this.cursorId, this.queue.getName().toString());
               } catch (Throwable ignored) {
               }
               if (txDeleteCursorOnReload == -1) {
                  txDeleteCursorOnReload = store.generateID();
               }
               store.deleteCursorAcknowledgeTransactional(txDeleteCursorOnReload, pos.getRecordID());
            } else {
               pageInfo.loadACK(pos);
            }
         }

         if (txDeleteCursorOnReload >= 0) {
            store.commit(txDeleteCursorOnReload);
         }

         recoveredACK.clear();
         recoveredACK = null;
      }
   }

   @Override
   public void flushExecutors() {
      if (!executor.flush(10, TimeUnit.SECONDS)) {
         ActiveMQServerLogger.LOGGER.timedOutFlushingExecutorsPagingCursor(this);
      }
   }

   @Override
   public void stop() {
      flushExecutors();
   }

   @Override
   public void printDebug() {
      printDebug(toString());
   }

   public void printDebug(final String msg) {
      System.out.println("Debug information on PageCurorImpl- " + msg);
      for (PageCursorInfo info : consumedPages.values()) {
         System.out.println(info);
      }
   }

   @Override
   public void onDeletePage(Page deletedPage) throws Exception {
      PageCursorInfo info;
      synchronized (consumedPages) {
         info = consumedPages.remove(Long.valueOf(deletedPage.getPageId()));
      }
      if (info != null) {
         PagePosition completeInfo = info.getCompleteInfo();
         if (completeInfo != null) {
            try {
               store.deletePageComplete(completeInfo.getRecordID());
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.errorDeletingPageCompleteRecord(e);
            }
            info.setCompleteInfo(null);
         }
         for (PagePosition deleteInfo : info.acks) {
            if (deleteInfo.getRecordID() >= 0) {
               try {
                  store.deleteCursorAcknowledge(deleteInfo.getRecordID());
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.errorDeletingPageCompleteRecord(e);
               }
            }
         }
         info.acks.clear();
      }
   }

   @Override
   public ArtemisExecutor getExecutor() {
      return executor;
   }

   @Override
   public void reloadPageInfo(long pageNr) {
      getPageInfo(pageNr);
   }

   private PageCursorInfo getPageInfo(final PagePosition pos) {
      return getPageInfo(pos.getPageNr());
   }

   public PageCursorInfo getPageInfo(final long pageNr) {
      synchronized (consumedPages) {
         PageCursorInfo pageInfo = consumedPages.get(pageNr);

         if (pageInfo == null) {
            PageCache cache = cursorProvider.getPageCache(pageNr);
            if (cache == null) {
               return null;
            }
            assert pageNr == cache.getPageId();
            pageInfo = new PageCursorInfo(cache);
            consumedPages.put(pageNr, pageInfo);
         }
         return pageInfo;
      }

   }

   private void saveEmptyPageAsConsumedPage(final PageCache cache) {
      if (cache != null && cache.getNumberOfMessages() == 0) {
         synchronized (consumedPages) {
            PageCursorInfo pageInfo = consumedPages.get(cache.getPageId());
            if (pageInfo == null) {
               consumedPages.put(cache.getPageId(), new PageCursorInfo(cache));
            }
         }
      }
   }



   private boolean match(final Message message) {
      if (filter == null) {
         return true;
      } else {
         return filter.match(message);
      }
   }


   // To be called only after the ACK has been processed and guaranteed to be on storage
   // The only exception is on non storage events such as not matching messages
   private PageCursorInfo processACK(final PagePosition pos) {
      if (lastAckedPosition == null || pos.compareTo(lastAckedPosition) > 0) {
         if (logger.isTraceEnabled()) {
            logger.trace("a new position is being processed as ACK");
         }
         if (lastAckedPosition != null && lastAckedPosition.getPageNr() != pos.getPageNr()) {
            if (logger.isTraceEnabled()) {
               logger.trace("Scheduling cleanup on pageSubscription for address = " + pageStore.getAddress() + " queue = " + this.getQueue().getName());
            }

            // there's a different page being acked, we will do the check right away
            if (autoCleanup) {
               scheduleCleanupCheck();
            }
         }
         lastAckedPosition = pos;
      }
      PageCursorInfo info = getPageInfo(pos);

      // This could be null if the page file was removed
      if (info == null) {
         // This could become null if the page file was deleted, or if the queue was removed maybe?
         // it's better to diagnose it (based on support tickets) instead of NPE
         ActiveMQServerLogger.LOGGER.nullPageCursorInfo(this.getPagingStore().getAddress().toString(), pos.toString(), cursorId);
      } else {
         info.addACK(pos);
      }

      return info;
   }

   private void installTXCallback(final Transaction tx, final PagePosition position) {
      installTXCallback(tx, position, -1);
   }

   /**
    * @param tx
    * @param position
    * @param persistentSize if negative it needs to be calculated on the fly
    */
   private void installTXCallback(final Transaction tx, final PagePosition position, final long persistentSize) {
      if (position.getRecordID() >= 0) {
         // It needs to persist, otherwise the cursor will return to the fist page position
         tx.setContainsPersistent();
      }

      PageCursorInfo info = getPageInfo(position);
      if (info != null) {
         PageCache cache = info.getCache();
         if (cache != null) {
            final long size;
            if (persistentSize < 0) {
               //cache.getMessage is potentially expensive depending
               //on the current cache size and which message is queried
               size = getPersistentSize(cache.getMessage(position));
            } else {
               size = persistentSize;
            }
            position.setPersistentSize(size);
         }

         logger.tracef("InstallTXCallback looking up pagePosition %s, result=%s", position, info);

         info.remove(position);

         PageCursorTX cursorTX = (PageCursorTX) tx.getProperty(TransactionPropertyIndexes.PAGE_CURSOR_POSITIONS);

         if (cursorTX == null) {
            cursorTX = new PageCursorTX();
            tx.putProperty(TransactionPropertyIndexes.PAGE_CURSOR_POSITIONS, cursorTX);
            tx.addOperation(cursorTX);
         }

         cursorTX.addPositionConfirmation(this, position);
      }

   }

   private PageTransactionInfo getPageTransaction(final PagedReference reference) throws ActiveMQException {
      if (reference.getTransactionID() >= 0) {
         return pageStore.getPagingManager().getTransaction(reference.getTransactionID());
      } else {
         return null;
      }
   }

   /**
    * A callback from the PageCursorInfo. It will be called when all the messages on a page have been acked
    *
    * @param info
    */
   private void onPageDone(final PageCursorInfo info) {
      if (autoCleanup) {
         scheduleCleanupCheck();
      }
   }


   /**
    * This will hold information about the pending ACKs towards a page.
    * <p>
    * This instance will be released as soon as the entire page is consumed, releasing the memory at
    * that point The ref counts are increased also when a message is ignored for any reason.
    */
   public final class PageCursorInfo {

      // Number of messages existent on this page
      private int numberOfMessages;

      private final long pageId;

      // Confirmed ACKs on this page
      private Set<PagePosition> acks = Collections.synchronizedSet(new LinkedHashSet<PagePosition>());

      private WeakReference<PageCache> cache;

      private Set<PagePosition> removedReferences = new ConcurrentHashSet<>();

      // The page was live at the time of the creation
      private final boolean wasLive;

      // There's a pending TX to add elements on this page
      // also can be used to prevent the page from being deleted too soon.
      private final AtomicInteger pendingTX = new AtomicInteger(0);

      // There's a pending delete on the async IO pipe
      // We're holding this object to avoid delete the pages before the IO is complete,
      // however we can't delete these records again
      private boolean pendingDelete;

      /**
       * This is to be set when all the messages are complete on a given page, and we cleanup the records that are marked on it
       */
      private PagePosition completePage;

      // We need a separate counter as the cursor may be ignoring certain values because of incomplete transactions or
      // expressions
      private final AtomicInteger confirmed = new AtomicInteger(0);

      public boolean isAck(PagePosition position) {
         return completePage != null || acks.contains(position);
      }

      @Override
      public String toString() {
         try {
            return "PageCursorInfo::pageNr=" + pageId +
               " numberOfMessage = " +
               numberOfMessages +
               ", confirmed = " +
               confirmed +
               ", isDone=" +
               this.isDone() +
               " wasLive = " + wasLive;
         } catch (Exception e) {
            return "PageCursorInfo::pageNr=" + pageId +
               " numberOfMessage = " +
               numberOfMessages +
               ", confirmed = " +
               confirmed +
               ", isDone=" +
               e.toString();
         }
      }

      private PageCursorInfo(final long pageId, final int numberOfMessages) {
         if (numberOfMessages < 0) {
            throw new IllegalStateException("numberOfMessages = " + numberOfMessages + " instead of being >=0");
         }
         this.pageId = pageId;
         wasLive = false;
         this.numberOfMessages = numberOfMessages;
         logger.tracef("Created PageCursorInfo for pageNr=%d, numberOfMessages=%d, not live", pageId, numberOfMessages);
      }

      private PageCursorInfo(final PageCache cache) {
         Objects.requireNonNull(cache);
         this.pageId = cache.getPageId();
         wasLive = cache.isLive();
         this.cache = new WeakReference<>(cache);
         if (!wasLive) {
            final int numberOfMessages = cache.getNumberOfMessages();
            assert numberOfMessages >= 0;
            this.numberOfMessages = numberOfMessages;
            logger.tracef("Created PageCursorInfo for pageNr=%d, numberOfMessages=%d,  cache=%s, not live", pageId, this.numberOfMessages, cache);
         } else {
            //given that is live, the exact value must be get directly from cache
            this.numberOfMessages = -1;
            logger.tracef("Created PageCursorInfo for pageNr=%d, cache=%s, live", pageId, cache);
         }
      }

      /**
       * @param completePage
       */
      public void setCompleteInfo(final PagePosition completePage) {
         logger.tracef("Setting up complete page %s on cursor %s on subscription %s", completePage, this, PageSubscriptionImpl.this);
         this.completePage = completePage;
      }

      public PagePosition getCompleteInfo() {
         return completePage;
      }

      public boolean isDone() {
         if (logger.isTraceEnabled()) {
            logger.trace(PageSubscriptionImpl.this + "::PageCursorInfo(" + pageId + ")::isDone checking with completePage!=null->" + (completePage != null) + " getNumberOfMessages=" + getNumberOfMessages() + ", confirmed=" + confirmed.get() + " and pendingTX=" + pendingTX.get());

         }
         return completePage != null || (getNumberOfMessages() == confirmed.get() && pendingTX.get() == 0);
      }

      public boolean isPendingDelete() {
         return pendingDelete || completePage != null;
      }

      public void setPendingDelete() {
         pendingDelete = true;
      }

      /**
       * @return the pageId
       */
      public long getPageId() {
         return pageId;
      }

      public void incrementPendingTX() {
         pendingTX.incrementAndGet();
      }

      public void decrementPendingTX() {
         pendingTX.decrementAndGet();
         checkDone();
      }

      public boolean isRemoved(final PagePosition pos) {
         return removedReferences.contains(pos);
      }

      public void remove(final PagePosition position) {
         removedReferences.add(position);
      }

      public void addACK(final PagePosition posACK) {

         if (logger.isTraceEnabled()) {
            try {
               logger.trace("numberOfMessages =  " + getNumberOfMessages() +
                               " confirmed =  " +
                               (confirmed.get() + 1) +
                               " pendingTX = " + pendingTX +
                               ", pageNr = " +
                               pageId + " posACK = " + posACK);
            } catch (Throwable ignored) {
               logger.debug(ignored.getMessage(), ignored);
            }
         }

         boolean added = internalAddACK(posACK);

         // Negative could mean a bookmark on the first element for the page (example -1)
         if (added && posACK.getMessageNr() >= 0) {
            confirmed.incrementAndGet();
            checkDone();
         }
      }

      // To be called during reload
      public void loadACK(final PagePosition posACK) {
         if (internalAddACK(posACK) && posACK.getMessageNr() >= 0) {
            confirmed.incrementAndGet();
         }
      }

      private boolean internalAddACK(final PagePosition posACK) {
         removedReferences.add(posACK);
         return acks.add(posACK);
      }

      /**
       *
       */
      protected void checkDone() {
         if (isDone()) {
            onPageDone(this);
         }
      }

      private int getNumberOfMessagesFromPageCache() {
         // if the page was live at any point, we need to
         // get the number of messages from the page-cache
         PageCache localCache = getValidCache();
         if (localCache == null) return 0;
         int numberOfMessage = localCache.getNumberOfMessages();
         if (!localCache.isLive()) {
            //to avoid further "live" queries
            this.numberOfMessages = numberOfMessage;
         }
         return numberOfMessage;
      }

      public PageCache getValidCache() {
         PageCache localCache = this.cache != null ? this.cache.get() : null;
         if (localCache == null) {
            localCache = cursorProvider.getPageCache(pageId);
            // this could happen if the file does not exist any more, after cleanup
            if (localCache == null) {
               return null;
            }
            this.cache = new WeakReference<>(localCache);
         }
         return localCache;
      }

      private int getNumberOfMessages() {
         final int numberOfMessages = this.numberOfMessages;
         if (wasLive) {
            if (numberOfMessages < 0) {
               return getNumberOfMessagesFromPageCache();
            } else {
               return numberOfMessages;
            }
         } else {
            assert numberOfMessages >= 0;
            return numberOfMessages;
         }
      }

      /**
       * @return the cache
       */
      public PageCache getCache() {
         return cache != null ? cache.get() : null;
      }

      public int getPendingTx() {
         return pendingTX.get();
      }
   }

   private final class PageCursorTX extends TransactionOperationAbstract {

      private final Map<PageSubscriptionImpl, List<PagePosition>> pendingPositions = new HashMap<>();

      private void addPositionConfirmation(final PageSubscriptionImpl cursor, final PagePosition position) {
         List<PagePosition> list = pendingPositions.get(cursor);

         if (list == null) {
            list = new LinkedList<>();
            pendingPositions.put(cursor, list);
         }

         list.add(position);
      }

      @Override
      public void afterCommit(final Transaction tx) {
         for (Entry<PageSubscriptionImpl, List<PagePosition>> entry : pendingPositions.entrySet()) {
            PageSubscriptionImpl cursor = entry.getKey();

            List<PagePosition> positions = entry.getValue();

            for (PagePosition confirmed : positions) {
               cursor.processACK(confirmed);
               cursor.deliveredCount.decrementAndGet();
               cursor.deliveredSize.addAndGet(-confirmed.getPersistentSize());
            }

         }
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return Collections.emptyList();
      }

   }

   private class CursorIterator implements PageIterator {

      private PagePositionAndFileOffset position = null;

      private PagePositionAndFileOffset lastOperation = null;

      private volatile boolean isredelivery = false;

      private PagedReference currentDelivery = null;

      private volatile PagedReference lastRedelivery = null;

      private final boolean browsing;

      // We only store the position for redeliveries. They will be read from the SoftCache again during delivery.
      private final java.util.Queue<PagePosition> redeliveries = new LinkedList<>();

      /**
       * next element taken on hasNext test.
       * it has to be delivered on next next operation
       */
      private volatile PagedReference cachedNext;

      private CursorIterator(boolean browsing) {
         this.browsing = browsing;
      }

      private CursorIterator() {
         this.browsing = false;
      }

      @Override
      public void redeliver(PagePosition reference) {
         synchronized (redeliveries) {
            redeliveries.add(reference);
         }
      }

      @Override
      public void repeat() {
         if (isredelivery) {
            synchronized (redeliveries) {
               cachedNext = lastRedelivery;
            }
         } else {
            if (lastOperation == null) {
               position = null;
            } else {
               position = lastOperation;
            }
         }
      }

      @Override
      public synchronized PagedReference next() {

         if (cachedNext != null) {
            currentDelivery = cachedNext;
            cachedNext = null;
            return currentDelivery;
         }

         if (position == null) {
            position = getStartPosition();
         }

         currentDelivery = moveNext();
         return currentDelivery;
      }

      private PagedReference moveNext() {
         synchronized (PageSubscriptionImpl.this) {
            boolean match = false;

            PagedReference message;

            PagePositionAndFileOffset lastPosition = position;
            PagePositionAndFileOffset tmpPosition = position;

            long timeout = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DELIVERY_TIMEOUT);

            do {
               if (System.nanoTime() - timeout > 0) {
                  return dummyPagedRef;
               }

               synchronized (redeliveries) {
                  PagePosition redelivery = redeliveries.poll();

                  if (redelivery != null) {
                     // There's a redelivery pending, we will get it out of that pool instead
                     isredelivery = true;
                     PagedReference redeliveredMsg = getReference(redelivery);
                     lastRedelivery = redeliveredMsg;

                     return redeliveredMsg;
                  } else {
                     lastRedelivery = null;
                     isredelivery = false;
                  }

                  message = internalGetNext(tmpPosition);
               }

               if (message == null) {
                  break;
               }

               int nextFileOffset = message.getPosition().getFileOffset() == -1 ? -1 : message.getPosition().getFileOffset() + message.getPagedMessage().getStoredSize() + Page.SIZE_RECORD;
               tmpPosition = new PagePositionAndFileOffset(nextFileOffset, message.getPosition());

               boolean valid = true;
               boolean ignored = false;

               // Validate the scenarios where the message should be considered not valid even to be considered

               // 1st... is it routed?

               valid = routed(message.getPagedMessage());
               if (!valid) {
                  ignored = true;
               }

               PageCursorInfo info = getPageInfo(message.getPosition().getPageNr());

               position = tmpPosition;

               if (!browsing && info != null && (info.isRemoved(message.getPosition()) || info.getCompleteInfo() != null)) {
                  continue;
               }

               if (info != null && info.isAck(message.getPosition())) {
                  continue;
               }

               // 2nd ... if TX, is it committed?
               if (valid && message.getPagedMessage().getTransactionID() >= 0) {
                  PageTransactionInfo tx = pageStore.getPagingManager().getTransaction(message.getPagedMessage().getTransactionID());
                  if (tx == null) {
                     ActiveMQServerLogger.LOGGER.pageSubscriptionCouldntLoad(message.getPagedMessage().getTransactionID(), message.getPosition(), pageStore.getAddress(), queue.getName());
                     valid = false;
                     ignored = true;
                  } else {
                     if (tx.deliverAfterCommit(CursorIterator.this, PageSubscriptionImpl.this, message.getPosition())) {
                        valid = false;
                        ignored = false;
                     }
                  }
               }

               // 3rd... was it previously removed?
               if (valid) {
                  // We don't create a PageCursorInfo unless we are doing a write operation (ack or removing)
                  // Say you have a Browser that will only read the files... there's no need to control PageCursors is
                  // nothing
                  // is being changed. That's why the false is passed as a parameter here

                  if (!browsing && info != null && info.isRemoved(message.getPosition())) {
                     valid = false;
                  }
               }

               if (valid) {
                  if (browsing) {
                     match = match(message.getMessage());
                  } else {
                     // if not browsing, we will just trust the routing on the queue
                     match = true;
                  }
               } else if (!browsing && ignored) {
                  positionIgnored(message.getPosition());
               }
            }
            while (!match);

            if (message != null) {
               lastOperation = lastPosition;
            }

            return message;
         }
      }

      @Override
      public synchronized int tryNext() {
         // if an unbehaved program called hasNext twice before next, we only cache it once.
         if (cachedNext != null) {
            return 1;
         }

         if (!pageStore.isPaging()) {
            return 0;
         }

         PagedReference pagedReference = next();
         if (pagedReference == dummyPagedRef) {
            return 2;
         } else {
            cachedNext = pagedReference;
            return cachedNext == null ? 0 : 1;
         }
      }

      /**
       * QueueImpl::deliver could be calling hasNext while QueueImpl.depage could be using next and hasNext as well.
       * It would be a rare race condition but I would prefer avoiding that scenario
       */
      @Override
      public synchronized boolean hasNext() {
         int status;
         while ((status = tryNext()) == 2) {
         }
         return status == 0 ? false : true;
      }

      @Override
      public void remove() {
         deliveredCount.incrementAndGet();
         PagedReference delivery = currentDelivery;
         if (delivery != null) {
            PageCursorInfo info = PageSubscriptionImpl.this.getPageInfo(delivery.getPosition());
            if (info != null) {
               info.remove(delivery.getPosition());
            }
         }
      }

      @Override
      public void close() {
         // When the CursorIterator(especially browse one) is closed, we need to close page they opened
         if (position != null) {
            PageReader pageReader = pageReaders.remove(position.pagePosition.getPageNr());
            if (pageReader != null) {
               pageReader.close();
            }
         }
      }
   }

   /**
    * @return the deliveredCount
    */
   @Override
   public long getDeliveredCount() {
      return deliveredCount.get();
   }

   /**
    * @return the deliveredSize
    */
   @Override
   public long getDeliveredSize() {
      return deliveredSize.get();
   }

   @Override
   public void incrementDeliveredSize(long size) {
      deliveredSize.addAndGet(size);
   }

   private long getPersistentSize(PagedMessage msg) {
      try {
         return msg != null && msg.getPersistentSize() > 0 ? msg.getPersistentSize() : 0;
      } catch (ActiveMQException e) {
         logger.warn("Error computing persistent size of message: " + msg, e);
         return 0;
      }
   }

   private long getPersistentSize(PagedReference ref) {
      try {
         return ref != null && ref.getPersistentSize() > 0 ? ref.getPersistentSize() : 0;
      } catch (ActiveMQException e) {
         logger.warn("Error computing persistent size of message: " + ref, e);
         return 0;
      }
   }

   protected static class PagePositionAndFileOffset {

      private final int nextFileOffset;
      private final PagePosition pagePosition;

      PagePositionAndFileOffset(int nextFileOffset, PagePosition pagePosition) {
         this.nextFileOffset = nextFileOffset;
         this.pagePosition = pagePosition;
      }

      PagePosition nextPagePostion() {
         int messageNr = pagePosition.getMessageNr();
         return new PagePositionImpl(pagePosition.getPageNr(), messageNr + 1, messageNr + 1 == 0 ? 0 : nextFileOffset);
      }
   }
}
