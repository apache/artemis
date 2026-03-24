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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.LivePageCache;
import org.apache.activemq.artemis.core.paging.cursor.NonExistentPage;
import org.apache.activemq.artemis.core.paging.cursor.PageCache;
import org.apache.activemq.artemis.core.paging.cursor.BulkPageCache;
import org.apache.activemq.artemis.core.paging.cursor.PageCursorProvider;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.cursor.PagedReferenceImpl;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.SoftValueLongObjectHashMap;
import org.apache.activemq.artemis.utils.ThreadDumpUtil;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.jboss.logging.Logger;

/**
 * A PageProviderIMpl
 *
 * TODO: this may be moved entirely into PagingStore as there's an one-to-one relationship here
 * However I want to keep this isolated as much as possible during development
 */
public class PageCursorProviderImpl implements PageCursorProvider {


   private static final Logger logger = Logger.getLogger(PageCursorProviderImpl.class);


   /**
    * As an optimization, avoid subsequent schedules as they are unnecessary
    */
   protected final AtomicInteger scheduledCleanup = new AtomicInteger(0);

   protected volatile boolean cleanupEnabled = true;

   protected final PagingStore pagingStore;

   protected final StorageManager storageManager;

   // This is the same executor used at the PageStoreImpl. One Executor per pageStore
   private final ArtemisExecutor executor;

   private final SoftValueLongObjectHashMap<BulkPageCache> softCache;

   private LongObjectHashMap<Integer> numberOfMessages = null;

   private final LongObjectHashMap<CompletableFuture<BulkPageCache>> inProgressReadPages;

   private final ConcurrentLongHashMap<PageSubscription> activeCursors = new ConcurrentLongHashMap<>();

   private static final long PAGE_READ_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(30);

   //Any concurrent read page request will wait in a loop the original Page::read to complete while
   //printing at intervals a warn message
   private static final long CONCURRENT_PAGE_READ_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(10);

   //storageManager.beforePageRead will be attempted in a loop, printing at intervals a warn message
   private static final long PAGE_READ_PERMISSION_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(10);


   public PageCursorProviderImpl(final PagingStore pagingStore,
                                 final StorageManager storageManager,
                                 final ArtemisExecutor executor,
                                 final int maxCacheSize) {
      this(pagingStore, storageManager, executor, maxCacheSize, false);
   }

   public PageCursorProviderImpl(final PagingStore pagingStore,
                                 final StorageManager storageManager,
                                 final ArtemisExecutor executor,
                                 final int maxCacheSize,
                                 final boolean readWholePage) {
      this.pagingStore = pagingStore;
      this.storageManager = storageManager;
      this.executor = executor;
      this.softCache = new SoftValueLongObjectHashMap<>(maxCacheSize);
      if (!readWholePage) {
         this.numberOfMessages = new LongObjectHashMap<>();
      }
      this.inProgressReadPages = new LongObjectHashMap<>();
   }


   @Override
   public synchronized PageSubscription createSubscription(long cursorID, Filter filter, boolean persistent) {
      if (logger.isTraceEnabled()) {
         logger.trace(this.pagingStore.getAddress() + " creating subscription " + cursorID + " with filter " + filter, new Exception("trace"));
      }

      if (activeCursors.containsKey(cursorID)) {
         throw new IllegalStateException("Cursor " + cursorID + " had already been created");
      }

      PageSubscription activeCursor = new PageSubscriptionImpl(this, pagingStore, storageManager, executor, filter, cursorID, persistent);
      activeCursors.put(cursorID, activeCursor);
      return activeCursor;
   }

   @Override
   public synchronized PageSubscription getSubscription(long cursorID) {
      return activeCursors.get(cursorID);
   }

   @Override
   public PagedMessage getMessage(final PagePosition pos) {
      PageCache cache = getPageCache(pos.getPageNr());

      if (cache == null || pos.getMessageNr() >= cache.getNumberOfMessages()) {
         // sanity check, this should never happen unless there's a bug
         throw new NonExistentPage("Invalid messageNumber passed = " + pos + " on " + cache);
      }

      return cache.getMessage(pos);
   }

   @Override
   public PagedReference newReference(final PagePosition pos,
                                      final PagedMessage msg,
                                      final PageSubscription subscription) {
      return new PagedReferenceImpl(pos, msg, subscription);
   }

   @Override
   public PageCache getPageCache(final long pageId) {
      try {
         if (pageId > pagingStore.getCurrentWritingPage()) {
            return null;
         }
         boolean createPage = false;
         CompletableFuture<BulkPageCache> inProgressReadPage;
         BulkPageCache cache;
         Page page = null;
         synchronized (softCache) {
            cache = softCache.get(pageId);
            if (cache != null) {
               return cache;
            }
            if (!pagingStore.checkPageFileExists((int) pageId)) {
               return null;
            }
            Page currentPage = pagingStore.getCurrentPage();
            // Live page cache might be cleared by gc, we need to retrieve it otherwise partially written page cache is being returned
            if (currentPage != null && currentPage.getPageId() == pageId && (cache = currentPage.getLiveCache()) != null) {
               softCache.put(cache.getPageId(), cache);
               return cache;
            }
            inProgressReadPage = inProgressReadPages.get(pageId);
            if (inProgressReadPage == null) {
               if (numberOfMessages != null && numberOfMessages.containsKey(pageId)) {
                  return new PageReader(pagingStore.createPage((int) pageId), numberOfMessages.get(pageId));
               }
               final CompletableFuture<BulkPageCache> readPage = new CompletableFuture<>();
               page = pagingStore.createPage((int) pageId);
               createPage = true;
               inProgressReadPage = readPage;
               inProgressReadPages.put(pageId, readPage);
            }
         }
         if (createPage) {
            return readPage(pageId, page, inProgressReadPage);
         } else {
            final long startedWait = System.nanoTime();
            while (true) {
               try {
                  return inProgressReadPage.get(CONCURRENT_PAGE_READ_TIMEOUT_NS, TimeUnit.NANOSECONDS);
               } catch (TimeoutException e) {
                  final long elapsed = System.nanoTime() - startedWait;
                  final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsed);
                  logger.warnf("Waiting a concurrent Page::read for pageNr=%d on cursor %s by %d ms",
                               pageId, pagingStore.getAddress(), elapsedMillis);
               }
            }
         }
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   private PageCache readPage(long pageId,
                              Page page,
                              CompletableFuture<BulkPageCache> inProgressReadPage) throws Exception {
      logger.tracef("adding pageCache pageNr=%d into cursor = %s", pageId, this.pagingStore.getAddress());
      boolean acquiredPageReadPermission = false;
      int num = -1;
      final PageCacheImpl cache;
      try {
         final long startedRequest = System.nanoTime();
         while (!acquiredPageReadPermission) {
            acquiredPageReadPermission = storageManager.beforePageRead(PAGE_READ_PERMISSION_TIMEOUT_NS, TimeUnit.NANOSECONDS);
            if (!acquiredPageReadPermission) {
               final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedRequest);
               logger.warnf("Cannot acquire page read permission of pageNr=%d on cursor %s after %d ms: consider increasing page-max-concurrent-io or use a faster disk",
                            pageId, pagingStore.getAddress(), elapsedMillis);
            }
         }
         page.open();
         final long startedReadPage = System.nanoTime();
         List<PagedMessage> pgdMessages = page.read(storageManager);
         final long elapsedReadPage = System.nanoTime() - startedReadPage;
         if (elapsedReadPage > PAGE_READ_TIMEOUT_NS) {
            logger.warnf("Page::read for pageNr=%d on cursor %s tooks %d ms to read %d bytes", pageId,
                         pagingStore.getAddress(), TimeUnit.NANOSECONDS.toMillis(elapsedReadPage), page.getSize());
         }
         num = pgdMessages.size();
         cache = new PageCacheImpl(pageId, pgdMessages.toArray(new PagedMessage[num]));
      } catch (Throwable t) {
         inProgressReadPage.completeExceptionally(t);
         synchronized (softCache) {
            inProgressReadPages.remove(pageId);
         }
         throw t;
      } finally {
         try {
            if (page != null) {
               page.close(false, false);
            }
         } catch (Throwable ignored) {
         }
         if (acquiredPageReadPermission) {
            storageManager.afterPageRead();
         }
      }
      inProgressReadPage.complete(cache);
      synchronized (softCache) {
         inProgressReadPages.remove(pageId);
         softCache.put(pageId, cache);
         if (numberOfMessages != null && num != -1) {
            numberOfMessages.put(pageId, Integer.valueOf(num));
         }
      }
      return cache;
   }

   @Override
   public void addLivePageCache(LivePageCache cache) {
      logger.tracef("Add live page cache %s", cache);
      synchronized (softCache) {
         softCache.put(cache.getPageId(), cache);
      }
   }

   @Override
   public void setCacheMaxSize(final int size) {
      softCache.setMaxElements(size);
   }

   @Override
   public int getCacheSize() {
      synchronized (softCache) {
         return softCache.size();
      }
   }

   @Override
   public void clearCache() {
      synchronized (softCache) {
         softCache.clear();
      }
   }

   @Override
   public void processReload() throws Exception {
      Collection<PageSubscription> cursorList = this.activeCursors.values();
      for (PageSubscription cursor : cursorList) {
         cursor.processReload();
      }

      if (!cursorList.isEmpty()) {
         // https://issues.jboss.org/browse/JBPAPP-10338 if you ack out of order,
         // the min page could be beyond the first page.
         // we have to reload any previously acked message
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
         logger.tracef("Stopping with %d cleanup tasks to be completed yet", pendingCleanupTasks);
      }
   }

   private void waitForFuture() {
      if (!executor.flush(10, TimeUnit.SECONDS)) {
         ActiveMQServerLogger.LOGGER.timedOutStoppingPagingCursor(executor);
         ActiveMQServerLogger.LOGGER.threadDump(ThreadDumpUtil.threadDump(""));
      }
   }

   @Override
   public void flushExecutors() {
      for (PageSubscription cursor : activeCursors.values()) {
         cursor.flushExecutors();
      }
      waitForFuture();
   }

   @Override
   public void close(PageSubscription cursor) {
      activeCursors.remove(cursor.getId());

      scheduleCleanup();
   }

   @Override
   public void scheduleCleanup() {

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling cleanup", new Exception("trace"));
      }
      if (!cleanupEnabled || scheduledCleanup.intValue() > 2) {
         // Scheduled cleanup was already scheduled before.. never mind!
         // or we have cleanup disabled
         return;
      }

      scheduledCleanup.incrementAndGet();

      executor.execute(new Runnable() {
         @Override
         public void run() {
            storageManager.setContext(storageManager.newSingleThreadContext());
            try {
               if (cleanupEnabled) {
                  cleanup();
               }
            } finally {
               storageManager.clearContext();
               scheduledCleanup.decrementAndGet();
            }
         }
      });
   }

   /**
    * Delete everything associated with any queue on this address.
    * This is to be called when the address is about to be released from paging.
    * Hence the PagingStore will be holding a write lock, meaning no messages are going to be paged at this time.
    * So, we shouldn't lock anything after this method, to avoid dead locks between the writeLock and any synchronization with the CursorProvider.
    */
   @Override
   public void onPageModeCleared() {
      ArrayList<PageSubscription> subscriptions = cloneSubscriptions();

      Transaction tx = new TransactionImpl(storageManager);
      for (PageSubscription sub : subscriptions) {
         try {
            sub.onPageModeCleared(tx);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorCleaningPagingOnQueue(e, sub.getQueue().getName().toString());
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

   @Override
   public void cleanup() {

      logger.tracef("performing page cleanup %s", this);

      ArrayList<Page> depagedPages = new ArrayList<>();

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
            if (pagingStore.lock(100)) {
               break;
            }
            if (!pagingStore.isStarted())
               return;
         }

         logger.tracef("%s locked", this);

         synchronized (this) {
            try {
               if (!pagingStore.isStarted()) {
                  return;
               }

               if (pagingStore.getNumberOfPages() == 0) {
                  return;
               }

               ArrayList<PageSubscription> cursorList = cloneSubscriptions();

               long minPage = checkMinPage(cursorList);
               deliverIfNecessary(cursorList, minPage);

               logger.debugf("Asserting cleanup for address %s, firstPage=%d", pagingStore.getAddress(), minPage);

               // if the current page is being written...
               // on that case we need to move to verify it in a different way
               if (minPage == pagingStore.getCurrentWritingPage() && pagingStore.getCurrentPage().getNumberOfMessages() > 0) {
                  boolean complete = checkPageCompletion(cursorList, minPage);

                  if (!pagingStore.isStarted()) {
                     return;
                  }

                  // All the pages on the cursor are complete.. so we will cleanup everything and store a bookmark
                  if (complete) {

                     cleanupComplete(cursorList);
                  }
               }

               for (long i = pagingStore.getFirstPage(); i <= minPage; i++) {
                  if (!checkPageCompletion(cursorList, i)) {
                     break;
                  }
                  Page page = pagingStore.depage();
                  if (page == null) {
                     break;
                  }
                  depagedPages.add(page);
               }

               if (pagingStore.getNumberOfPages() == 0 || pagingStore.getNumberOfPages() == 1 && pagingStore.getCurrentPage().getNumberOfMessages() == 0) {
                  pagingStore.stopPaging();
               } else {
                  if (logger.isTraceEnabled()) {
                     logger.trace("Couldn't cleanup page on address " + this.pagingStore.getAddress() + " as numberOfPages == " + pagingStore.getNumberOfPages() + " and currentPage.numberOfMessages = " + pagingStore.getCurrentPage().getNumberOfMessages());
                  }
               }
            } catch (Exception ex) {
               ActiveMQServerLogger.LOGGER.problemCleaningPageAddress(ex, pagingStore.getAddress());
               logger.warn(ex.getMessage(), ex);
               return;
            } finally {
               pagingStore.unlock();
            }
         }
      }
      finishCleanup(depagedPages);

   }

   // Protected as a way to inject testing
   protected void cleanupComplete(ArrayList<PageSubscription> cursorList) throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("Address " + pagingStore.getAddress() +
                         " is leaving page mode as all messages are consumed and acknowledged from the page store");
      }

      pagingStore.forceAnotherPage();

      Page currentPage = pagingStore.getCurrentPage();

      storeBookmark(cursorList, currentPage);

      pagingStore.stopPaging();
   }

   // Protected as a way to inject testing
   protected void finishCleanup(ArrayList<Page> depagedPages) {
      logger.tracef("this(%s) finishing cleanup on %s", this, depagedPages);
      try {
         for (Page depagedPage : depagedPages) {
            BulkPageCache cache;
            PagedMessage[] pgdMessages;
            synchronized (softCache) {
               cache = softCache.get((long) depagedPage.getPageId());
            }

            if (logger.isTraceEnabled()) {
               logger.trace("Removing pageNr=" + depagedPage.getPageId() + " from page-cache");
            }

            if (cache == null) {
               // The page is not on cache any more
               // We need to read the page-file before deleting it
               // to make sure we remove any large-messages pending
               storageManager.beforePageRead();

               List<PagedMessage> pgdMessagesList = null;
               try {
                  depagedPage.open();
                  pgdMessagesList = depagedPage.read(storageManager, true);
               } finally {
                  try {
                     depagedPage.close(false, false);
                  } catch (Exception e) {
                  }

                  storageManager.afterPageRead();
               }
               pgdMessages = pgdMessagesList.isEmpty() ? null :
                  pgdMessagesList.toArray(new PagedMessage[pgdMessagesList.size()]);
            } else {
               pgdMessages = cache.getMessages();
            }

            depagedPage.delete(pgdMessages);
            synchronized (softCache) {
               long pageId = (long) depagedPage.getPageId();
               softCache.remove(pageId);
               if (numberOfMessages != null) {
                  numberOfMessages.remove(pageId);
               }
            }
            onDeletePage(depagedPage);
         }
      } catch (Exception ex) {
         ActiveMQServerLogger.LOGGER.problemCleaningPageAddress(ex, pagingStore.getAddress());
         return;
      }

   }

   private boolean checkPageCompletion(ArrayList<PageSubscription> cursorList, long minPage) {

      logger.tracef("checkPageCompletion(%d)", minPage);

      boolean complete = true;

      for (PageSubscription cursor : cursorList) {
         if (!cursor.isComplete(minPage)) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cursor " + cursor + " was considered incomplete at pageNr=" + minPage);
            }

            complete = false;
            break;
         } else {
            if (logger.isDebugEnabled()) {
               logger.debug("Cursor " + cursor + " was considered **complete** at pageNr=" + minPage);
            }
         }
      }
      return complete;
   }

   /**
    * @return
    */
   private synchronized ArrayList<PageSubscription> cloneSubscriptions() {
      ArrayList<PageSubscription> cursorList = new ArrayList<>(activeCursors.values());
      return cursorList;
   }

   protected void onDeletePage(Page deletedPage) throws Exception {
      List<PageSubscription> subscriptions = cloneSubscriptions();
      for (PageSubscription subs : subscriptions) {
         subs.onDeletePage(deletedPage);
      }
   }

   /**
    * @param cursorList
    * @param currentPage
    * @throws Exception
    */
   protected void storeBookmark(ArrayList<PageSubscription> cursorList, Page currentPage) throws Exception {
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
   public void printDebug() {
      System.out.println("Debug information for PageCursorProviderImpl:");
      for (PageCache cache : softCache.values()) {
         System.out.println("Cache " + cache);
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

      for (PageSubscription cursor : cursorList) {
         long firstPage = cursor.getFirstPage();
         if (logger.isDebugEnabled()) {
            logger.debug(this.pagingStore.getAddress() + " has a cursor " + cursor + " with first page=" + firstPage);
         }

         // the cursor will return -1 if the cursor is empty
         if (firstPage >= 0 && firstPage < minPage) {
            minPage = firstPage;
         }
      }

      if (logger.isDebugEnabled()) {
         logger.debug(this.pagingStore.getAddress() + " has minPage=" + minPage);
      }

      return minPage;

   }

   private void deliverIfNecessary(Collection<PageSubscription> cursorList, long minPage) {
      boolean currentWriting = minPage == pagingStore.getCurrentWritingPage() ? true : false;
      for (PageSubscription cursor : cursorList) {
         long firstPage = cursor.getFirstPage();
         if (firstPage == minPage) {
            /**
             * if first page is current writing page and it's not complete, or
             * first page is before the current writing page, we need to trigger
             * deliverAsync to delete messages in the pages.
             */
            if (cursor.getQueue().getMessageCount() == 0 && (!currentWriting || !cursor.isComplete(firstPage))) {
               cursor.getQueue().deliverAsync();
               break;
            }
         }
      }
   }


}
