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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.netty.util.collection.IntObjectHashMap;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.ConsumedPage;
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
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.server.impl.QueueImpl.DELIVERY_TIMEOUT;

public final class PageSubscriptionImpl implements PageSubscription {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final Object DUMMY = new Object();

   private static final PagedReference RETRY_MARK = new PagedReferenceImpl(null, null);

   private boolean empty = true;

   /**
    * for tests
    */
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


   PageSubscriptionImpl(final PageCursorProvider cursorProvider,
                        final PagingStore pageStore,
                        final StorageManager store,
                        final Filter filter,
                        final long cursorId,
                        final boolean persistent,
                        final PageSubscriptionCounter counter) {
      assert counter != null;
      this.pageStore = pageStore;
      this.store = store;
      this.cursorProvider = cursorProvider;
      this.cursorId = cursorId;
      this.filter = filter;
      this.persistent = persistent;
      this.counter = counter;
      this.counter.setSubscription(this);
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
   public boolean isStorePaging() {
      return pageStore.isStorePaging();
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
         return counter.getValue();
      }
   }

   @Override
   public boolean isCounterPending() {
      return counter.isRebuilding();
   }

   @Override
   public long getPersistentSize() {
      if (empty) {
         return 0;
      } else {
         //A negative value could happen if an old journal was loaded that didn't have
         //size metrics for old records
         long messageSize = counter.getPersistentSize();
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
    * Usually paging is a stream of messages but in certain scenarios (such as a pending prepared TX) we may have big
    * holes on the page streaming, and we will need to ignore such pages on the cursor/subscription.
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
      info.clear();
      synchronized (consumedPages) {
         consumedPages.put(position.getPageNr(), info);
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
         pageStore.execute(this::performCleanup);
      }
   }

   private void performCleanup() {
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

   @Override
   public void onPageModeCleared(Transaction tx) throws Exception {
      // this could be null on testcases
      counter.delete(tx);
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
      logger.trace(">>>>>>> cleanupEntries");
      try {
         Transaction tx = new TransactionImpl(store);

         boolean persist = false;

         final ArrayList<PageCursorInfo> completedPages = new ArrayList<>();
         final ArrayList<PageCursorInfo> nonExistingPages = new ArrayList<>();

         // First get the completed pages using a lock
         synchronized (consumedPages) {
            // lastAckedPosition = null means no acks were done yet, so we are not ready to cleanup
            if (lastAckedPosition == null) {
               return;
            }

            for (Entry<Long, PageCursorInfo> entry : consumedPages.entrySet()) {
               PageCursorInfo info = entry.getValue();

               if (info.isDone() && !info.isPendingDelete()) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("Complete page {} on queue {} / {}", info, queue.getName(), queue.getID());
                  }
                  Page currentPage = pageStore.getCurrentPage();

                  if (currentPage != null && entry.getKey() == pageStore.getCurrentPage().getPageId()) {
                     logger.trace("We can't clear page {} 's the current page", entry.getKey());
                  } else if (info.getNumberOfMessages() <= 0 && !pageStore.checkPageFileExists(info.getPageId())) {
                     logger.debug("marking page {} as non existent", info.getPageId());
                     nonExistingPages.add(info);
                  } else {
                     if (logger.isTraceEnabled()) {
                        logger.trace("cleanup marking page {} as complete", info.pageId);
                     }
                     info.setPendingDelete();
                     completedPages.add(entry.getValue());
                  }
               }
            }
         }

         for (PageCursorInfo deletedPage : nonExistingPages) {
            consumedPages.remove(deletedPage.pageId);
         }

         for (PageCursorInfo infoPG : completedPages) {
            // There are a few cases where a pending transaction might set a big hole on the page system where we need
            // to ignore these pages in case of a restart. For that reason when we delete complete ACKs we store a
            // single record per page file that will be removed once the page file is deleted notice also that this
            // record is added as part of the same transaction where the information is deleted. In case of a TX Failure
            // (a crash on the server) this will be recovered on the next cleanup once the server is restarted. First
            // will mark the page as complete
            if (isPersistent()) {
               PagePosition completePage = new PagePositionImpl(infoPG.getPageId(), infoPG.getNumberOfMessages());
               infoPG.setCompleteInfo(completePage);
               store.storePageCompleteTransactional(tx.getID(), this.getId(), completePage);
               if (!persist) {
                  persist = true;
                  tx.setContainsPersistent();
               }
            }

            if (infoPG.acks != null) {
               // it will delete the page ack records
               for (PagePosition pos : infoPG.acks.values()) {
                  if (pos.getRecordID() >= 0) {
                     store.deleteCursorAcknowledgeTransactional(tx.getID(), pos.getRecordID());
                     if (!persist) {
                        // only need to set it once
                        tx.setContainsPersistent();
                        persist = true;
                     }
                  }
               }
               infoPG.clear();
            }
         }

         tx.addOperation(new TransactionOperationAbstract() {

            @Override
            public void afterCommit(final Transaction tx1) {
               pageStore.execute(() -> {
                  if (!completeDelete) {
                     cursorProvider.scheduleCleanup();
                  }
               });
            }
         });

         tx.commit();
      } finally {
         logger.trace("<<<<<< cleanupEntries");
      }

   }

   @Override
   public String toString() {
      return "PageSubscriptionImpl [cursorId=" + cursorId + ", queue=" + queue + ", filter = " + filter + "]";
   }

   @Override
   public PageIterator iterator() {
      return new CursorIterator();
   }

   @Override
   public PageIterator iterator(boolean browsing) {
      return new CursorIterator(browsing);
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

   @Override
   public void confirmPosition(final Transaction tx, final PagePosition position, boolean fromDelivery) throws Exception {
      // if the cursor is persistent
      if (persistent) {
         store.storeCursorAcknowledgeTransactional(tx.getID(), cursorId, position);
      }
      installTXCallback(tx, position, fromDelivery);

   }

   @Override
   public void ackTx(final Transaction tx, final PagedReference reference, boolean fromDelivery) throws Exception {
      //pre-calculate persistentSize
      final long persistentSize = getPersistentSize(reference);

      confirmPosition(tx, reference.getPagedMessage().newPositionObject(), true);

      counter.increment(tx, -1, -persistentSize);

      PageTransactionInfo txInfo = getPageTransaction(reference);
      if (txInfo != null) {
         txInfo.storeUpdate(store, pageStore.getPagingManager(), tx);
         tx.setContainsPersistent();
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
         return !getPageInfo(ref.getPagedMessage().getPageNumber()).isAck(ref.getPagedMessage().getMessageNumber());
      }
   }

   @Override
   public boolean isAcked(PagedMessage pagedMessage) {
      return getPageInfo(pagedMessage.getPageNumber()).isAck(pagedMessage.getMessageNumber());
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
   public void addPendingDelivery(final PagedMessage pagedMessage) {
      PageCursorInfo info = getPageInfo(pagedMessage.getPageNumber());

      if (info != null) {
         info.incrementPendingTX();
      }
   }

   @Override
   public void removePendingDelivery(final PagedMessage pagedMessage) {
      PageCursorInfo info = getPageInfo(pagedMessage.getPageNumber());

      if (info != null) {
         info.decrementPendingTX();
      }
   }

   @Override
   public void redeliver(final PageIterator iterator, final PagedReference pagedReference) {
      iterator.redeliver(pagedReference);

      synchronized (consumedPages) {
         PageCursorInfo pageInfo = consumedPages.get(pagedReference.getPagedMessage().getPageNumber());
         if (pageInfo != null) {
            pageInfo.decrementPendingTX();
         } else {
            // this shouldn't really happen.
         }
      }
   }

   @Override
   public PagedMessage queryMessage(PagePosition pos) {
      try {
         Page page = pageStore.usePage(pos.getPageNr());

         if (page == null) {
            return null;
         }

         try {
            org.apache.activemq.artemis.utils.collections.LinkedList<PagedMessage> messages = page.getMessages();
            PagedMessage retMessage;
            if (pos.getMessageNr() < messages.size()) {
               retMessage = messages.get(pos.getMessageNr());
            } else {
               retMessage = null;
            }
            return retMessage;
         } finally {
            page.usageDown();
         }
      } catch (Exception e) {
         store.criticalError(e);
         throw new RuntimeException(e.getMessage(), e);
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
      installTXCallback(tx, position, true);

      try {
         counter.increment(tx, -1, -position.getPersistentSize());
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }

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
   public void forEachConsumedPage(Consumer<ConsumedPage> pageConsumer) {
      synchronized (consumedPages) {
         consumedPages.values().forEach(pageConsumer);
      }
   }


   @Override
   public boolean isComplete(long page) {
      logger.trace("{} isComplete {}", this, page);
      synchronized (consumedPages) {
         if (empty && consumedPages.isEmpty()) {
            if (logger.isTraceEnabled()) {
               logger.trace("isComplete({})::Subscription {} has empty={}, consumedPages.isEmpty={}", page, this, empty, consumedPages.isEmpty());
            }
            return true;
         }

         PageCursorInfo info = consumedPages.get(page);

         if (info == null && empty) {
            logger.trace("isComplete({})::::Couldn't find info and it is empty", page);
            return true;
         } else {
            boolean isDone = info != null && info.isDone();
            if (logger.isTraceEnabled()) {
               logger.trace("isComplete({}):: found info={}, isDone={}", (Object) page, info, isDone);
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
               if (cursor.acks != null) {
                  for (PagePosition info : cursor.acks.values()) {
                     if (info.getRecordID() >= 0) {
                        isPersistent = true;
                        store.deleteCursorAcknowledgeTransactional(tx, info.getRecordID());
                     }
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
         if (logger.isDebugEnabled()) {
            logger.debug("processing reload queue name={} with id={}", queue != null ? this.queue.getName() : "N/A", cursorId);
         }

         Collections.sort(recoveredACK);

         for (PagePosition pos : recoveredACK) {
            logger.trace("reloading pos {}", pos);
            lastAckedPosition = pos;
            PageCursorInfo pageInfo = getPageInfo(pos);
            pageInfo.loadACK(pos);
         }

         recoveredACK.clear();
         recoveredACK = null;
      }
   }

   @Override
   public void stop() {
   }

   @Override
   public void counterSnapshot() {
      counter.snapshot();
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
   public void deleteCursorInfo() {
      logger.debug("Delete consumed Paged on {} with consumedPages size = {}", pageStore.getAddress(), consumedPages.size());
      consumedPages.forEach(this::deleteCursorInfo);
      consumedPages.clear();
   }

   private void deleteCursorInfo(Long page, PageCursorInfo info) {
      logger.debug("Deleting cursorInfo for page {} info={}, address={}", page, info, pageStore.getAddress());
      PagePosition completeInfo = info.getCompleteInfo();
      if (completeInfo != null) {
         try {
            store.deletePageComplete(completeInfo.getRecordID());
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorDeletingPageCompleteRecord(e);
         }
         info.setCompleteInfo(null);
      }
      if (info.acks != null) {
         for (PagePosition deleteInfo : info.acks.values()) {
            if (deleteInfo.getRecordID() >= 0) {
               try {
                  store.deleteCursorAcknowledge(deleteInfo.getRecordID());
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.errorDeletingPageCompleteRecord(e);
               }
            }
         }
      }
   }

   @Override
   public void onDeletePage(Page deletedPage) throws Exception {
      logger.debug("removing page {}", deletedPage);
      PageCursorInfo info;
      synchronized (consumedPages) {
         info = consumedPages.remove(deletedPage.getPageId());
      }
      if (info != null) {
         deleteCursorInfo(deletedPage.getPageId(), info);
      }
      deletedPage.usageExhaust();
   }

   @Override
   public void reloadPageInfo(long pageNr) {
      getPageInfo(pageNr);
   }

   PageCursorInfo getPageInfo(final PagePosition pos) {
      return getPageInfo(pos.getPageNr());
   }

   @Override
   public PageCursorInfo locatePageInfo(final long pageNr) {
      synchronized (consumedPages) {
         return consumedPages.get(pageNr);
      }
   }

   public PageCursorInfo getPageInfo(final long pageNr) {
      synchronized (consumedPages) {
         PageCursorInfo pageInfo = consumedPages.get(pageNr);

         if (pageInfo == null) {
            pageInfo = new PageCursorInfo(pageNr);
            consumedPages.put(pageNr, pageInfo);
         }
         return pageInfo;
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
         logger.trace("a new position is being processed as ACK");

         if (lastAckedPosition != null && lastAckedPosition.getPageNr() != pos.getPageNr()) {
            logger.trace("Scheduling cleanup on pageSubscription for address = {} queue = {}", pageStore.getAddress(), getQueue().getName());

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

   private void installTXCallback(final Transaction tx, final PagePosition position, final boolean fromDelivery) {
      if (position.getRecordID() >= 0) {
         // It needs to persist, otherwise the cursor will return to the fist page position
         tx.setContainsPersistent();
      }

      PageCursorInfo info = getPageInfo(position);
      if (info != null) {
         logger.trace("InstallTXCallback looking up pagePosition {}, result={}", position, info);

         info.remove(position.getMessageNr());

         PageCursorTX cursorTX = (PageCursorTX) tx.getProperty(TransactionPropertyIndexes.PAGE_CURSOR_POSITIONS);

         if (cursorTX == null) {
            cursorTX = new PageCursorTX(fromDelivery);
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
    */
   private void onPageDone(final PageCursorInfo info) {
      if (autoCleanup) {
         if (logger.isTraceEnabled()) {
            logger.trace("onPageDone page {}", info.getPageId());
         }
         scheduleCleanupCheck();
      }
   }

   /**
    * This will hold information about the pending ACKs towards a page.
    * <p>
    * This instance will be released as soon as the entire page is consumed, releasing the memory at that point The ref
    * counts are increased also when a message is ignored for any reason.
    */
   public final class PageCursorInfo implements ConsumedPage {

      // Number of messages existent on this page
      private int numberOfMessages;

      private final long pageId;

      private IntObjectHashMap<PagePosition> acks = new IntObjectHashMap<>();

      // This will take DUMMY elements. This is used like a HashSet of Int
      private IntObjectHashMap<Object> removedReferences = new IntObjectHashMap<>();

      // There's a pending TX to add elements on this page
      // also can be used to prevent the page from being deleted too soon.
      private final AtomicInteger pendingTX = new AtomicInteger(0);

      // There's a pending delete on the async IO pipe
      // We're holding this object to avoid delete the pages before the IO is complete,
      // however we can't delete these records again
      private boolean pendingDelete;

      /**
       * This is to be set when all the messages are complete on a given page, and we cleanup the records that are
       * marked on it
       */
      private PagePosition completePage;

      // We need a separate counter as the cursor 3124'gmay be ignoring certain values because of incomplete transactions or
      // expressions
      private final AtomicInteger confirmed = new AtomicInteger(0);

      @Override
      public synchronized boolean isAck(int messageNumber) {
         return completePage != null || acks != null && acks.get(messageNumber) != null;
      }

      @Override
      public void forEachAck(BiConsumer<Integer, PagePosition> ackConsumer) {
         if (acks != null) {
            acks.forEach(ackConsumer);
         }
      }

      public IntObjectHashMap getAcks() {
         return acks;
      }

      public IntObjectHashMap getRemovedReferences() {
         return removedReferences;
      }

      public PagePosition getCompletePageInformation() {
         return completePage;
      }


      @Override
      public String toString() {
         try {
            return "PageCursorInfo::pageNr=" + pageId +
               " numberOfMessage = " +
               numberOfMessages +
               ", confirmed = " +
               confirmed;
         } catch (Exception e) {
            return "PageCursorInfo::pageNr=" + pageId +
               " numberOfMessage = " +
               numberOfMessages +
               ", confirmed = " +
               confirmed + ", exception = " + e;
         }
      }

      PageCursorInfo(final long pageId, final int numberOfMessages) {
         if (numberOfMessages < 0) {
            throw new IllegalStateException("numberOfMessages = " + numberOfMessages + " instead of being >=0");
         }
         this.pageId = pageId;
         this.numberOfMessages = numberOfMessages;
         if (logger.isTraceEnabled()) {
            logger.trace("Created PageCursorInfo for pageNr={}, numberOfMessages={}, not live", pageId, numberOfMessages);
         }
      }

      private PageCursorInfo(long pageId) {
         this.pageId = pageId;
         //given that is live, the exact value must be get directly from cache
         this.numberOfMessages = -1;
      }

      public void clear() {
         this.removedReferences = null;
         this.acks = null;
      }

      public void setCompleteInfo(final PagePosition completePage) {
         if (logger.isTraceEnabled()) {
            logger.trace("Setting up complete page {} on cursor {} on subscription {}", completePage, this, PageSubscriptionImpl.this);
         }

         this.completePage = completePage;
      }

      public PagePosition getCompleteInfo() {
         return completePage;
      }

      @Override
      public boolean isDone() {
         if (logger.isTraceEnabled()) {
            logger.trace("{}::PageCursorInfo({})::isDone checking with completePage!=null->{} getNumberOfMessages={}, confirmed={} and pendingTX={}",
               PageSubscriptionImpl.this, pageId, completePage != null, getNumberOfMessages(), confirmed.get(), pendingTX.get());
         }

         if (!pageStore.isStorePaging()) {
            return true;
         }

         // in cases where the file was damaged it is possible to get more confirmed records than we actually had messages
         // for that case we set confirmed.get() >= getNumberOfMessages instead of ==
         return completePage != null || (confirmed.get() >= getNumberOfMessages() && pendingTX.get() == 0);
      }

      public boolean isPendingDelete() {
         return pendingDelete || completePage != null;
      }

      public void setPendingDelete() {
         pendingDelete = true;
      }

      @Override
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

      public synchronized boolean isRemoved(final int messageNr) {
         // removed references = null means everything is acked and done, so we just return true here
         return completePage != null || removedReferences == null || removedReferences.get(messageNr) != null;
      }

      public synchronized void remove(final int messageNr) {
         if (logger.isTraceEnabled()) {
            logger.trace("PageCursor Removing messageNr {} on page {}", messageNr, pageId);
         }
         if (removedReferences != null) {
            removedReferences.put(messageNr, DUMMY);
         }
      }

      public void addACK(final PagePosition posACK) {

         if (logger.isTraceEnabled()) {
            try {
               logger.trace("numberOfMessages = {} confirmed = {} pendingTX = {}, pageNr = {} posACK = {}",
                            getNumberOfMessages(), (confirmed.get() + 1), pendingTX, pageId, posACK);
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

      synchronized boolean internalAddACK(final PagePosition position) {
         if (logger.isDebugEnabled()) {
            logger.debug("internalAddACK on queue {} (id={}), position {}", queue.getName(), queue.getID(), position);
         }
         if (removedReferences != null) {
            removedReferences.put(position.getMessageNr(), DUMMY);
         }
         if (acks == null) {
            return false;
         } else {
            return acks.put(position.getMessageNr(), position) == null;
         }
      }

      protected void checkDone() {
         pageStore.execute(() -> {
            if (isDone()) {
               onPageDone(this);
            }
         });
      }

      private int getNumberOfMessages() {
         if (!pageStore.isStorePaging()) {
            return 0;
         }
         if (numberOfMessages < 0) {
            try {
               Page page = pageStore.usePage(pageId, true, false);

               if (page == null) {
                  page = pageStore.newPageObject(pageId);
                  numberOfMessages = page.readNumberOfMessages();
               } else {
                  try {
                     if (page.isOpen()) {
                        // if the file is still open (active) we don't cache the number of messages
                        return page.getNumberOfMessages();
                     } else {
                        this.numberOfMessages = page.getNumberOfMessages();
                     }
                  } finally {
                     page.usageDown();
                  }
               }
            } catch (Exception e) {
               store.criticalError(e);
               throw new RuntimeException(e.getMessage(), e);
            }
         }
         return numberOfMessages;
      }

      public int getPendingTx() {
         return pendingTX.get();
      }
   }

   private final class PageCursorTX extends TransactionOperationAbstract {

      private boolean fromDelivery;

      PageCursorTX(boolean fromDelivery) {
         this.fromDelivery = fromDelivery;
      }

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
            }

         }
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return Collections.emptyList();
      }

   }

   private class CursorIterator implements PageIterator {

      private Page currentPage;
      private LinkedListIterator<PagedMessage> currentPageIterator;

      private void initPage(long page) {
         if (logger.isDebugEnabled()) {
            logger.debug("initPage {}", page);
         }
         try {
            if (currentPage != null) {
               if (logger.isTraceEnabled()) {
                  logger.trace("usage down {} on subscription {}", currentPage.getPageId(), cursorId);
               }
               currentPage.usageDown();
            }
            if (currentPageIterator != null) {
               if (logger.isTraceEnabled()) {
                  logger.trace("closing pageIterator on {}", cursorId);
               }
               currentPageIterator.close();
            }
            currentPage = pageStore.usePage(page);
            if (logger.isTraceEnabled()) {
               logger.trace("CursorIterator: getting page {} which will contain {}", page, currentPage.getNumberOfMessages());
            }
            currentPageIterator = currentPage.iterator();
         } catch (Exception e) {
            store.criticalError(e);
            throw new IllegalStateException(e.getMessage(), e);
         }
      }

      private PagedReference currentDelivery = null;

      private volatile PagedReference lastDelivery = null;

      private final boolean browsing;

      // We only store the position for redeliveries. They will be read from the SoftCache again during delivery.
      private final java.util.Queue<PagedReference> redeliveries = new LinkedList<>();

      /**
       * next element taken on hasNext test. it has to be delivered on next next operation
       */
      private volatile PagedReference cachedNext;

      private CursorIterator(boolean browsing) {
         this.browsing = browsing;
      }

      private CursorIterator() {
         this.browsing = false;
      }

      @Override
      public void redeliver(PagedReference reference) {
         synchronized (redeliveries) {
            redeliveries.add(reference);
         }
      }

      @Override
      public void repeat() {
         cachedNext = lastDelivery;
      }

      @Override
      public synchronized PagedReference next() {
         try {
            if (cachedNext != null) {
               currentDelivery = cachedNext;
               cachedNext = null;
               return currentDelivery;
            }

            if (currentPage == null) {
               logger.trace("CursorIterator::next initializing first page as {}", pageStore.getFirstPage());
               initPage(pageStore.getFirstPage());
            }

            currentDelivery = moveNext();
            return currentDelivery;
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            store.criticalError(e);
            throw new RuntimeException(e.getMessage(), e);
         }
      }

      private PagedReference moveNext() {
         synchronized (PageSubscriptionImpl.this) {
            boolean match = false;

            PagedReference message;
            long timeout = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DELIVERY_TIMEOUT);

            do {
               if (System.nanoTime() - timeout > 0) {
                  return RETRY_MARK;
               }

               synchronized (redeliveries) {
                  PagedReference redelivery = redeliveries.poll();

                  if (redelivery != null) {
                     return redelivery;
                  } else {
                     lastDelivery = null;
                  }
               }
               message = internalGetNext();

               if (message == null) {
                  break;
               }

               boolean valid = true;
               boolean ignored = false;

               // Validate the scenarios where the message should be considered not valid even to be considered

               // 1st... is it routed?

               valid = routed(message.getPagedMessage());
               if (!valid) {
                  logger.trace("CursorIterator::message {} was deemed invalid, marking it to ignore", message.getPagedMessage());
                  ignored = true;
               }

               PageCursorInfo info = getPageInfo(message.getPagedMessage().getPageNumber());

               if (!browsing && info != null && (info.isRemoved(message.getPagedMessage().getMessageNumber()) || info.getCompleteInfo() != null)) {
                  if (logger.isTraceEnabled()) {
                     boolean removed = info.isRemoved(message.getPagedMessage().getMessageNumber());
                     logger.trace("CursorIterator::Message from page {} # {} isRemoved={}", message.getPagedMessage().getPageNumber(), message.getPagedMessage().getMessageNumber(), removed);
                  }
                  continue;
               }

               if (info != null && info.isAck(message.getPagedMessage().getMessageNumber())) {
                  logger.trace("CursorIterator::message {} is acked, moving next", message);
                  continue;
               }

               // 2nd ... if TX, is it committed?
               if (valid && message.getPagedMessage().getTransactionID() >= 0) {
                  PageTransactionInfo tx = pageStore.getPagingManager().getTransaction(message.getPagedMessage().getTransactionID());
                  if (tx == null) {
                     if (logger.isDebugEnabled()) {
                        // this message used to be a warning...
                        // when this message was first introduced I was being over carefully about eventually hiding a bug. Over the years I am confident
                        // this could happen between restarts.
                        // also after adding rebuild counters and retry scans over mirroring this could happen more oftenly.
                        // It's time to make this a logger.debug now
                        logger.debug("Could not locate page transaction {}, ignoring message on position {} on address={} queue={}", message.getPagedMessage().getTransactionID(), message.getPagedMessage().newPositionObject(), pageStore.getAddress(), queue.getName());
                     }
                     valid = false;
                     ignored = true;
                  } else {
                     if (tx.deliverAfterCommit(CursorIterator.this, PageSubscriptionImpl.this, message)) {
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

                  if (!browsing && info != null && info.isRemoved(message.getPagedMessage().getMessageNumber())) {
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
                  positionIgnored(message.getPagedMessage().newPositionObject());
               }
            }
            while (!match);

            if (message != null) {
               lastDelivery = message;
            }

            return message;
         }
      }


      private PagedReference internalGetNext() {
         for (;;) {
            assert currentPageIterator != null : "currentPageIterator is null";
            PagedMessage message = currentPageIterator.hasNext() ? currentPageIterator.next() : null;
            logger.trace("CursorIterator::internalGetNext:: new reference {}", message);
            if (message != null) {
               return cursorProvider.newReference(message, PageSubscriptionImpl.this);
            }

            if (logger.isTraceEnabled()) {
               logger.trace("Current page {}", currentPage != null ? currentPage.getPageId() : null);
            }
            long nextPage = getNextPage();
            if (logger.isTraceEnabled()) {
               logger.trace("next page {}", nextPage);
            }
            if (nextPage >= 0) {
               if (logger.isTraceEnabled()) {
                  logger.trace("CursorIterator::internalGetNext:: moving to currentPage {}", nextPage);
               }
               initPage(nextPage);
            } else {
               return null;
            }
         }
      }

      private long getNextPage() {
         long page = currentPage.getPageId() + 1;

         while (page <= pageStore.getCurrentWritingPage()) {
            PageCursorInfo info = locatePageInfo(page);
            // if pendingDelete or complete, we just move to next page
            if (info == null || info.getCompleteInfo() == null && !info.isPendingDelete()) {
               return page;
            }
            if (logger.isDebugEnabled()) {
               logger.debug("Subscription {} named {}  moving faster from page {} to next", cursorId, queue.getName(), page);
            }
            page++;
         }
         return -1;
      }


      @Override
      public synchronized NextResult tryNext() {
         if (cachedNext != null) {
            PageCursorInfo info = locatePageInfo(cachedNext.getPagedMessage().getPageNumber());
            if (info != null && info.isRemoved(cachedNext.getPagedMessage().getMessageNumber())) {
               logger.debug("Reference {} has been removed from another iterator, moving it next", cachedNext);
               cachedNext = null;
            } else {
               return NextResult.hasElements;
            }
         }

         if (!pageStore.isStorePaging()) {
            return NextResult.noElements;
         }

         PagedReference pagedReference = next();
         if (pagedReference == RETRY_MARK) {
            return NextResult.retry;
         } else {
            cachedNext = pagedReference;
            return cachedNext == null ? NextResult.noElements : NextResult.hasElements;
         }
      }

      /**
       * QueueImpl::deliver could be calling hasNext while QueueImpl.depage could be using next and hasNext as well. It
       * would be a rare race condition but I would prefer avoiding that scenario
       */
      @Override
      public synchronized boolean hasNext() {
         NextResult status;
         while ((status = tryNext()) == NextResult.retry) {
         }
         return status == NextResult.hasElements ? true : false;
      }

      @Override
      public void remove() {
         removeLastElement();
      }

      @Override
      public PagedReference removeLastElement() {
         PagedReference delivery = currentDelivery;
         if (delivery != null) {
            PageCursorInfo info = PageSubscriptionImpl.this.getPageInfo(delivery.getPagedMessage().getPageNumber());
            if (info != null) {
               info.remove(delivery.getPagedMessage().getMessageNumber());
            }
         }
         return delivery;
      }

      @Override
      public void close() {
         Page toClose = currentPage;
         if (toClose != null) {
            toClose.usageDown();
         }
         currentPage = null;
      }
   }

   private long getPersistentSize(PagedReference ref) {
      try {
         return ref != null && ref.getPersistentSize() > 0 ? ref.getPersistentSize() : 0;
      } catch (ActiveMQException e) {
         logger.warn("Error computing persistent size of message: {}", ref, e);
         return 0;
      }
   }
}
