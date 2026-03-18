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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.cursor.PagedReferenceImpl;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.SimpleFutureImpl;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LongHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageCursorProviderImpl implements PageCursorProvider {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * As an optimization, avoid subsequent schedules as they are unnecessary
    */
   protected final AtomicInteger scheduledCleanup = new AtomicInteger(0);

   protected volatile boolean cleanupEnabled = true;

   // We can't call cleanup before counters were rebuilt
   // as they will determine if a subscription is empty or not
   protected volatile boolean rebuildDone = true;

   protected final PagingStore pagingStore;

   protected final StorageManager storageManager;

   private final ConcurrentLongHashMap<PageSubscription> activeCursors = new ConcurrentLongHashMap<>();

   public PageCursorProviderImpl(final PagingStore pagingStore,
                                 final StorageManager storageManager) {
      this.pagingStore = pagingStore;
      this.storageManager = storageManager;
   }

   @Override
   public synchronized PageSubscription createSubscription(long cursorID, Filter filter, boolean persistent) {
      if (logger.isTraceEnabled()) {
         logger.trace("{} creating subscription {} {} with filter {}", this, this.pagingStore.getAddress(), cursorID, filter);
      }

      if (activeCursors.containsKey(cursorID)) {
         throw new IllegalStateException("Cursor " + cursorID + " had already been created");
      }

      PageSubscriptionCounter subscriptionCounter = createPageCounter(cursorID, persistent);
      PageSubscription activeCursor = new PageSubscriptionImpl(this, pagingStore, storageManager, filter, cursorID, persistent, subscriptionCounter);

      activeCursors.put(cursorID, activeCursor);
      return activeCursor;
   }

   private PageSubscriptionCounter createPageCounter(long cursorID, boolean persistent) {
      return new PageSubscriptionCounterImpl(storageManager, cursorID);
   }

   @Override
   public synchronized PageSubscription getSubscription(long cursorID) {
      return activeCursors.get(cursorID);
   }

   @Override
   public void forEachSubscription(Consumer<PageSubscription> consumer) {
      activeCursors.forEach((k, v) -> consumer.accept(v));
   }

   @Override
   public PagedReference newReference(final PagedMessage msg,
                                      final PageSubscription subscription) {
      return new PagedReferenceImpl(msg, subscription);
   }

   @Override
   public void processReload() throws Exception {
      Collection<PageSubscription> cursorList = this.activeCursors.values();
      for (PageSubscription cursor : cursorList) {
         cursor.processReload();
      }

      if (!cursorList.isEmpty()) {
         // If you ack out of order, the min page could be beyond the first page. We have to reload any previously acked
         // message
         long cursorsMinPage = checkMinPage(cursorList);

         // checkMinPage will return MaxValue if there aren't any pages or any cursors
         if (cursorsMinPage != Long.MAX_VALUE) {
            for (long startPage = pagingStore.getFirstPage(); startPage < cursorsMinPage; startPage++) {
               for (PageSubscription cursor : cursorList) {
                  cursor.reloadPageInfo(startPage);
               }
            }
         }
      }

      cleanup();

   }

   @Override
   public void stop() {
      for (PageSubscription cursor : activeCursors.values()) {
         cursor.stop();
      }
      final int pendingCleanupTasks = scheduledCleanup.get();
      if (pendingCleanupTasks > 0) {
         logger.trace("Stopping with {} cleanup tasks to be completed yet", pendingCleanupTasks);
      }
   }

   @Override
   public void counterSnapshot() {
      for (PageSubscription cursor : activeCursors.values()) {
         cursor.counterSnapshot();
      }
   }

   @Override
   public void flushExecutors() {
      pagingStore.flushExecutors();
   }

   @Override
   public void close(PageSubscription cursor) {
      activeCursors.remove(cursor.getId());

      scheduleCleanup();
   }

   @Override
   public Future<Boolean> scheduleCleanup() {
      final SimpleFutureImpl<Boolean> future = new SimpleFutureImpl<>();
      if (!cleanupEnabled || scheduledCleanup.intValue() > 2) {
         // Scheduled cleanup was already scheduled before.
         // On that case just flush the executor returning the future.set(true)
         // after any previous scheduled cleanup is finished.
         pagingStore.execute(() -> future.set(true));
         return future;
      }

      scheduledCleanup.incrementAndGet();

      pagingStore.execute(() -> {
         storageManager.setContext(storageManager.newSingleThreadContext());
         try {
            if (cleanupEnabled) {
               cleanup();
            }
         } finally {
            storageManager.clearContext();
            scheduledCleanup.decrementAndGet();
            future.set(true);
         }
      });

      return future;
   }

   /**
    * Delete everything associated with any queue on this address. This is to be called when the address is about to be
    * released from paging. Hence the PagingStore will be holding a write lock, meaning no messages are going to be
    * paged at this time. So, we shouldn't lock anything after this method, to avoid dead locks between the writeLock
    * and any synchronization with the CursorProvider.
    */
   @Override
   public void onPageModeCleared() {
      List<PageSubscription> subscriptions = cloneSubscriptions();

      Transaction tx = new TransactionImpl(storageManager);
      for (PageSubscription sub : subscriptions) {
         try {
            sub.onPageModeCleared(tx);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorCleaningPagingOnQueue(sub.getQueue().getName().toString(), e);
         }
      }

      try {
         tx.commit();
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorCleaningPagingDuringCommit(e);
      }
   }

   @Override
   public void disableCleanup() {
      this.cleanupEnabled = false;
   }

   @Override
   public void resumeCleanup() {
      this.cleanupEnabled = true;
      scheduleCleanup();
   }

   protected long getNumberOfMessagesOnSubscriptions() {
      AtomicLong largerCounter = new AtomicLong();
      activeCursors.forEach((id, sub) -> {
         long value = sub.getCounter().getValue();
         if (value > largerCounter.get()) {
            largerCounter.set(value);
         }
      });

      return largerCounter.get();
   }

   @Override
   public void checkClearPageLimit() {
      pagingStore.checkPageLimit(getNumberOfMessagesOnSubscriptions());
   }

   protected void cleanup() {

      if (!rebuildDone) {
         logger.debug("Counters were not rebuilt yet, cleanup has to be ignored on address {}", pagingStore != null ? pagingStore.getAddress() : "NULL");
         return;
      }

      List<Page> depagedPages = new ArrayList<>();
      LongHashSet depagedPagesSet = new LongHashSet();

      // This read lock is required
      // because in case of a replicated configuration
      // The replication manager will first get a writeLock on the StorageManager
      // for a short period when it is getting a list of IDs to send to the replica
      // Not getting this lock now could eventually result in a dead lock for a different order
      //
      // I tried to simplify the locks but each PageStore has its own lock, so this was the best option
      // I found in order to fix https://issues.apache.org/jira/browse/ARTEMIS-3054
      try (ArtemisCloseable readLock = storageManager.closeableReadLock()) {
         while (true) {
            if (pagingStore.writeLock(1_000)) {
               break;
            }
            if (!pagingStore.isStarted()) {
               return;
            }
         }

         logger.trace(">>>> Cleanup {}", this.pagingStore.getAddress());

         try {
            if (!pagingStore.isStarted()) {
               logger.trace("Paging store is not started");
               return;
            }

            if (!pagingStore.isStorePaging()) {
               logger.trace("Paging Store was not paging, so no reason to retry the cleanup");
               return;
            }

            List<PageSubscription> cursorList = cloneSubscriptions();

            long minPage = checkMinPage(cursorList);
            final long firstPage = pagingStore.getFirstPage();
            deliverIfNecessary(cursorList, minPage);

            if (logger.isTraceEnabled()) {
               logger.trace("firstPage={}, minPage={}, currentWritingPage={}", firstPage, minPage, pagingStore.getCurrentWritingPage());
            }

            // First we cleanup regular streaming, at the beginning of set of files
            cleanupRegularStream(depagedPages, depagedPagesSet, cursorList, minPage, firstPage);

            // Then we do some check on eventual pages that can be already removed but they are away from the streaming
            cleanupMiddleStream(depagedPages, depagedPagesSet, cursorList, minPage, firstPage);

            if (pagingStore.isPageFull()) {
               checkClearPageLimit();
            }

            assert pagingStore.getNumberOfPages() >= 0;

            if (!pagingStore.hasPendingIO() && (pagingStore.getNumberOfPages() == 0 || pagingStore.getNumberOfPages() == 1 && (pagingStore.getCurrentPage() == null || pagingStore.getCurrentPage().getNumberOfMessages() == 0))) {
               logger.trace("StopPaging being called on {}, pending={}", pagingStore, pagingStore.hasPendingIO());
               pagingStore.stopPaging();
            } else {
               if (logger.isTraceEnabled()) {
                  logger.trace("Couldn't cleanup page on address {} as numberOfPages == {}  and currentPage.numberOfMessages = {}", pagingStore.getAddress(), pagingStore.getNumberOfPages(), pagingStore.getCurrentPage().getNumberOfMessages());
               }
            }
         } catch (Throwable ex) {
            ActiveMQServerLogger.LOGGER.problemCleaningPageAddress(pagingStore.getAddress(), ex);
            logger.warn(ex.getMessage(), ex);
            return;
         } finally {
            logger.trace("<<<< Cleanup end on {}", pagingStore.getAddress());
            pagingStore.writeUnlock();
         }
      }
      finishCleanup(depagedPages);

   }

   /**
    * This cleanup process will calculate the min page for every cursor and then we remove the pages based on that. if
    * we knew ahead all the queues belonging to every page we could remove this process.
    */
   protected void cleanupRegularStream(List<Page> depagedPages,
                          LongHashSet depagedPagesSet,
                          List<PageSubscription> cursorList,
                          long minPage,
                          long firstPage) throws Exception {
      // if the current page is being written...
      // on that case we need to move to verify it in a different way
      Page currentPage = pagingStore.getCurrentPage();
      if (minPage == pagingStore.getCurrentWritingPage() && currentPage != null && currentPage.getNumberOfMessages() > 0) {
         boolean complete = checkPageCompletion(cursorList, minPage);

         // All the pages on the cursor are complete.. so we will cleanup everything and store a bookmark
         if (complete) {
            cleanupComplete(cursorList);
         }
      }

      for (long i = firstPage; i <= minPage; i++) {
         if (!checkPageCompletion(cursorList, i)) {
            break;
         }
         Page page = pagingStore.depage();
         if (page == null) {
            break;
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Depaging page {}", page.getPageId());
         }

         depagedPagesSet.add(page.getPageId());
         depagedPages.add(page);
      }
   }

   /**
    * The regular depaging will take care of removing messages in a regular streaming.
    * <p>
    * if we had a list of all the cursors that belong to each page, this cleanup would be enough on every situation
    * (with some adjustment to currentPages) So, this routing is to optimize removing pages when all the acks are made
    * on every cursor. We still need regular depaging on a streamed manner as it will check the min page for all the
    * existent cursors.
    */
   private void cleanupMiddleStream(List<Page> depagedPages,
                          LongHashSet depagedPagesSet,
                          List<PageSubscription> cursorList,
                          long minPage,
                          long firstPage) {

      final long currentPageId = pagingStore.getCurrentWritingPage();
      LongObjectHashMap<AtomicInteger> counts = new LongObjectHashMap<>();

      int subscriptions = cursorList.size();

      cursorList.forEach(sub -> {
         sub.forEachConsumedPage(consumedPage -> {
            if (consumedPage.isDone()) {
               AtomicInteger count = counts.get(consumedPage.getPageId());
               if (count == null) {
                  count = new AtomicInteger(0);
                  counts.put(consumedPage.getPageId(), count);
               }
               count.incrementAndGet();
            }
         });
      });

      counts.forEach((pageID, counter) -> {
         try {
            // This check is to make sure we are not removing what has been already removed by depaging
            if (pageID > minPage && pageID > firstPage && pageID != currentPageId) {
               if (counter.get() >= subscriptions) {
                  if (!depagedPagesSet.contains(pageID.longValue())) {
                     Page page = pagingStore.removePage(pageID.intValue());
                     if (logger.isDebugEnabled()) {
                        logger.debug("Removing page {}", pageID);
                     }
                     if (page != null) {
                        depagedPages.add(page);
                        depagedPagesSet.add(page.getPageId());
                     }
                  }
               }
            }
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.problemCleaningPagesubscriptionCounter(e);
            logger.debug("Error while Issuing cleanupMiddlePages with {}, counter = {}", pageID, counter, e);
            depagedPages.forEach(p -> logger.debug("page {}", p));
         }
      });
   }

   // Protected as a way to inject testing
   protected void cleanupComplete(List<PageSubscription> cursorList) throws Exception {
      logger.debug("Address {} is leaving page mode as all messages are consumed and acknowledged from the page store", pagingStore.getAddress());

      pagingStore.forceAnotherPage();

      Page currentPage = pagingStore.getCurrentPage();

      storeBookmark(cursorList, currentPage);

      if (!pagingStore.hasPendingIO()) {
         pagingStore.stopPaging();
      }
   }

   // Protected as a way to inject testing
   protected void finishCleanup(List<Page> depagedPages) {
      logger.trace("this({}) finishing cleanup on {}", this, depagedPages);
      try {
         for (Page depagedPage : depagedPages) {
            LinkedList<PagedMessage> pgdMessagesList = null;
            try {
               depagedPage.open(false);
               pgdMessagesList = depagedPage.read(storageManager, true);
            } finally {
               try {
                  depagedPage.close(false, false);
               } catch (Exception e) {
               }
            }

            depagedPage.delete(pgdMessagesList);
            onDeletePage(depagedPage);
         }
      } catch (Exception ex) {
         ActiveMQServerLogger.LOGGER.problemCleaningPageAddress(pagingStore.getAddress(), ex);
      }

   }

   private boolean checkPageCompletion(List<PageSubscription> cursorList, long minPage) throws Exception {

      logger.trace("checkPageCompletion({})", minPage);

      boolean complete = true;

      if (!pagingStore.checkPageFileExists(minPage)) {
         logger.trace("store {} did not have an existing file, considering it a complete file then", pagingStore.getAddress());
         return true;
      }

      for (PageSubscription cursor : cursorList) {
         if (!cursor.isComplete(minPage)) {
            if (logger.isTraceEnabled()) {
               logger.trace("Cursor {} was considered incomplete at pageNr={}", cursor, minPage);
            }

            complete = false;
            break;
         } else {
            if (logger.isTraceEnabled()) {
               logger.trace("Cursor {} was considered **complete** at pageNr={}", cursor, minPage);
            }
         }
      }
      return complete;
   }

   private synchronized List<PageSubscription> cloneSubscriptions() {
      List<PageSubscription> cursorList = new ArrayList<>(activeCursors.values());
      return cursorList;
   }

   protected void onDeletePage(Page deletedPage) throws Exception {
      List<PageSubscription> subscriptions = cloneSubscriptions();
      for (PageSubscription subs : subscriptions) {
         subs.onDeletePage(deletedPage);
      }
   }

   protected void storeBookmark(List<PageSubscription> cursorList, Page currentPage) throws Exception {
      try {
         // First step: Move every cursor to the next bookmarked page (that was just created)
         for (PageSubscription cursor : cursorList) {
            cursor.confirmPosition(new PagePositionImpl(currentPage.getPageId(), -1));
         }
      } finally {
         for (PageSubscription cursor : cursorList) {
            cursor.enableAutoCleanup();
         }
      }
   }

   @Override
   public String toString() {
      return "PageCursorProviderImpl{" +
         "pagingStore=" + pagingStore +
         '}';
   }

   /**
    * This method is synchronized because we want it to be atomic with the cursors being used
    */
   private long checkMinPage(Collection<PageSubscription> cursorList) {
      long minPage = Long.MAX_VALUE;

      if (logger.isTraceEnabled()) {
         logger.trace("Min page cursorList size {} on {}", cursorList.size(), pagingStore.getAddress(), new Exception("trace"));
      }

      for (PageSubscription cursor : cursorList) {
         long firstPage = cursor.getFirstPage();
         if (logger.isTraceEnabled()) {
            logger.trace("{} has a cursor {} with first page={}", pagingStore.getAddress(), cursor, firstPage);
         }

         // the cursor will return -1 if the cursor is empty
         if (firstPage >= 0 && firstPage < minPage) {
            minPage = firstPage;
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("checkMinPage({}) will have minPage={}", pagingStore.getAddress(), minPage);
      }

      return minPage;

   }

   private void deliverIfNecessary(Collection<PageSubscription> cursorList, long minPage) {
      boolean currentWriting = minPage == pagingStore.getCurrentWritingPage() ? true : false;
      for (PageSubscription cursor : cursorList) {
         long firstPage = cursor.getFirstPage();
         if (firstPage == minPage) {
            /*
             * if first page is current writing page and it's not complete, or first page is before the current writing
             * page, we need to trigger deliverAsync to delete messages in the pages.
             */
            if (cursor.getQueue().getMessageCount() == 0 && (!currentWriting || !cursor.isComplete(firstPage))) {
               cursor.getQueue().deliverAsync();
               break;
            }
         }
      }
   }

   @Override
   public void counterRebuildStarted() {
      this.rebuildDone = false;
   }

   @Override
   public void counterRebuildDone() {
      this.rebuildDone = true;
   }

   @Override
   public boolean isRebuildDone() {
      return this.rebuildDone;
   }
}
