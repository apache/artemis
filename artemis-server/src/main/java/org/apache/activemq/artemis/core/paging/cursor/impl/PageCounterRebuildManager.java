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

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.core.paging.PageTransactionInfo;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.ConsumedPage;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * this class will copy current data from the Subscriptions, count messages while the server is already active
 * performing other activity
 */
public class PageCounterRebuildManager implements Runnable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final PagingStore pgStore;
   private final PagingManager pagingManager;
   private final StorageManager sm;
   private final Map<Long, PageTransactionInfo> transactions;
   private boolean paging;
   private long limitPageId;
   private int limitMessageNr;
   private LongObjectHashMap<CopiedSubscription> copiedSubscriptionMap = new LongObjectHashMap<>();
   private final Set<Long> storedLargeMessages;


   public PageCounterRebuildManager(PagingManager pagingManager, PagingStore store, Map<Long, PageTransactionInfo> transactions, Set<Long> storedLargeMessages, AtomicLong minPageTXIDFound) {
      // we make a copy of the data because we are allowing data to influx. We will consolidate the values at the end
      initialize(store);
      this.pagingManager = pagingManager;
      this.pgStore = store;
      this.sm = store.getStorageManager();
      this.transactions = transactions;
      this.storedLargeMessages = storedLargeMessages;
   }

   /**
    * this method will perform the copy from Acked recorded from the subscription into a separate data structure. So we
    * can count data while we consolidate at the end
    */
   private void initialize(PagingStore store) {
      store.writeLock();
      try {
         try {
            paging = store.isStorePaging();
            if (!paging) {
               logger.trace("Destination {} was not paging, no need to rebuild counters", store.getAddress());
               store.getCursorProvider().forEachSubscription(subscription -> {
                  subscription.getCounter().markRebuilding();
                  subscription.getCounter().finishRebuild();
               });

               store.getCursorProvider().counterRebuildDone();
               return;
            }
            store.getCursorProvider().counterRebuildStarted();
            Page currentPage = store.getCurrentPage();
            limitPageId = store.getCurrentWritingPage();
            limitMessageNr = currentPage.getNumberOfMessages();
            if (logger.isTraceEnabled()) {
               logger.trace("PageCounterRebuild for {}, Current writing page {} and limit will be {} with lastMessage on last page={}", store.getStoreName(), store.getCurrentWritingPage(), limitPageId, limitMessageNr);
            }
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            limitPageId = store.getCurrentWritingPage();
         }
         logger.trace("Copying page store ack information from address {}", store.getAddress());
         store.getCursorProvider().forEachSubscription(subscription -> {
            if (logger.isTraceEnabled()) {
               logger.trace("Copying subscription ID {}", subscription.getId());
            }

            CopiedSubscription copiedSubscription = new CopiedSubscription(subscription);
            copiedSubscription.subscriptionCounter.markRebuilding();
            copiedSubscriptionMap.put(subscription.getId(), copiedSubscription);

            subscription.forEachConsumedPage(consumedPage -> {
               if (logger.isTraceEnabled()) {
                  logger.trace("Copying page {}", consumedPage.getPageId());
               }

               CopiedConsumedPage copiedConsumedPage = new CopiedConsumedPage();
               copiedSubscription.consumedPageMap.put(consumedPage.getPageId(), copiedConsumedPage);
               if (consumedPage.isDone()) {
                  if (logger.isTraceEnabled()) {
                     logger.trace("Marking page {} as done on the copy", consumedPage.getPageId());
                  }
                  copiedConsumedPage.done = true;
               } else {
                  // We only copy the acks if the page is not done
                  // as if the page is done, we just move over
                  consumedPage.forEachAck((messageNR, pagePosition) -> {
                     if (logger.isTraceEnabled()) {
                        logger.trace("Marking messageNR {} as acked on pageID={} copy", messageNR, consumedPage.getPageId());
                     }
                     if (copiedConsumedPage.acks == null) {
                        copiedConsumedPage.acks = new IntObjectHashMap<>();
                     }
                     copiedConsumedPage.acks.put(messageNR, Boolean.TRUE);
                  });
               }
            });
         });
      } finally {
         store.writeUnlock();
      }
   }


   private CopiedSubscription getSubscription(long queueID) {
      return copiedSubscriptionMap.get(queueID);
   }

   private boolean isACK(long queueID, long pageNR, int messageNR) {
      CopiedSubscription subscription = getSubscription(queueID);
      if (subscription == null) {
         return true;
      }

      CopiedConsumedPage consumedPage = subscription.getPage(pageNR);
      if (consumedPage == null) {
         return false;
      } else {
         return consumedPage.isAck(messageNR);
      }
   }

   private void done() {
      copiedSubscriptionMap.forEach((k, copiedSubscription) -> {
         if (!copiedSubscription.empty) {
            copiedSubscription.subscription.notEmpty();
            try {
               copiedSubscription.subscriptionCounter.increment(null, copiedSubscription.addUp, copiedSubscription.sizeUp);
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }
         if (!copiedSubscription.empty) {
            copiedSubscription.subscription.notEmpty();
         }
         if (copiedSubscription.subscriptionCounter != null) {
            copiedSubscription.subscriptionCounter.finishRebuild();
         }
      });
      pgStore.getCursorProvider().counterRebuildDone();
      pgStore.getCursorProvider().scheduleCleanup();
   }

   @Override
   public void run() {
      try {
         rebuild();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

   public void rebuild() throws Exception {
      if (pgStore == null) {
         logger.trace("Page store is null during rebuildCounters");
         return;
      }

      if (!paging) {
         logger.trace("Ignoring call to rebuild pgStore {}", pgStore.getAddress());
      }

      logger.debug("Rebuilding page counter for address {}", pgStore.getAddress());

      for (long pgid = pgStore.getFirstPage(); pgid <= limitPageId; pgid++) {
         if (logger.isTraceEnabled()) {
            logger.trace("Rebuilding counter on messages from page {} on rebuildCounters for address {}", pgid, pgStore.getAddress());
         }
         logger.debug("{} reading paging {} of {}", pgStore.getAddress(), pgid, limitPageId);
         Page page = pgStore.newPageObject(pgid);

         if (!page.getFile().exists()) {
            if (logger.isDebugEnabled()) {
               logger.trace("Skipping page {} on store {}", pgid, pgStore.getAddress());
            }
            continue;
         }
         page.open(false);
         LinkedList<PagedMessage> msgs = page.read(sm);
         page.close(false, false);

         try (LinkedListIterator<PagedMessage> iter = msgs.iterator()) {
            while (iter.hasNext()) {
               PagedMessage msg = iter.next();
               if (storedLargeMessages != null && msg.getMessage().isLargeMessage()) {
                  if (logger.isDebugEnabled()) {
                     logger.trace("removing storedLargeMessage {}", msg.getMessage().getMessageID());
                  }
                  storedLargeMessages.remove(msg.getMessage().getMessageID());
               }
               if (limitPageId == pgid) {
                  if (msg.getMessageNumber() >= limitMessageNr) {
                     if (logger.isDebugEnabled()) {
                        logger.trace("Rebuild counting on {} reached the last message at {}-{}", pgStore.getAddress(), limitPageId, limitMessageNr);
                     }
                     // this is the limit where we should count..
                     // anything beyond this will be new data
                     break;
                  }
               }
               msg.initMessage(sm);
               long[] routedQueues = msg.getQueueIDs();

               if (logger.isTraceEnabled()) {
                  logger.trace("reading message for rebuild cursor on address={}, pg={}, messageNR={}, routedQueues={}, message={}, queueLIst={}", pgStore.getAddress(), msg.getPageNumber(), msg.getMessageNumber(), routedQueues, msg, routedQueues);
               }

               PageTransactionInfo txInfo = null;

               if (msg.getTransactionID() > 0) {
                  txInfo = transactions.get(msg.getTransactionID());
                  if (txInfo != null) {
                     txInfo.setOrphaned(false);
                  }
               }

               Transaction preparedTX = txInfo == null ? null : txInfo.getPreparedTransaction();

               if (logger.isTraceEnabled()) {
                  logger.trace("lookup on {}, tx={}, preparedTX={}", msg.getTransactionID(), txInfo, preparedTX);
               }

               for (long queueID : routedQueues) {
                  boolean ok = !isACK(queueID, msg.getPageNumber(), msg.getMessageNumber());

                  // if the pageTransaction is in prepare state, we have to increment the counter after the commit
                  // notice that there is a check if the commit is done in afterCommit
                  if (preparedTX != null) {
                     PageSubscription subscription = pgStore.getCursorProvider().getSubscription(queueID);
                     preparedTX.addOperation(new TransactionOperationAbstract() {
                        @Override
                        public void afterCommit(Transaction tx) {
                           // We use the pagingManager executor here, in case the commit happened while the rebuild manager is working
                           // in that case the increment will wait any pending tasks on that executor to finish before this executor takes effect
                           pagingManager.execute(() -> {
                              try {
                                 subscription.getCounter().increment(null, 1, msg.getStoredSize());
                              } catch (Exception e) {
                                 logger.warn(e.getMessage(), e);
                              }
                           });
                        }
                     });

                  } else {
                     boolean txIncluded = msg.getTransactionID() <= 0 || transactions == null || txInfo != null;

                     if (!txIncluded) {
                        logger.trace("TX is not included for {}", msg);
                     }

                     if (ok && txIncluded) { // not acked and TX is ok
                        if (logger.isTraceEnabled()) {
                           logger.trace("Message pageNumber={}/{} NOT acked on queue {}", msg.getPageNumber(), msg.getMessageNumber(), queueID);
                        }
                        CopiedSubscription copiedSubscription = copiedSubscriptionMap.get(queueID);
                        if (copiedSubscription != null) {
                           copiedSubscription.empty = false;
                           copiedSubscription.addUp++;
                           copiedSubscription.sizeUp += msg.getPersistentSize();
                        }
                     } else {
                        if (logger.isTraceEnabled()) {
                           logger.trace("Message pageNumber={}/{} IS acked on queue {}", msg.getPageNumber(), msg.getMessageNumber(), queueID);
                        }
                     }
                  }
               }
            }
         }
      }

      logger.debug("Counter rebuilding done for address {}", pgStore.getAddress());

      done();

   }

   private static class CopiedSubscription {
      CopiedSubscription(PageSubscription subscription) {
         this.subscriptionCounter = subscription.getCounter();
         this.subscription = subscription;
      }

      private boolean empty = true;

      LongObjectHashMap<CopiedConsumedPage> consumedPageMap = new LongObjectHashMap<>();

      // this is not a copy! This will be the actual object listed in the PageSubscription
      // any changes to this object will reflect in the system and management;
      PageSubscriptionCounter subscriptionCounter;

      PageSubscription subscription;

      CopiedConsumedPage getPage(long pageNr) {
         return consumedPageMap.get(pageNr);
      }

      int addUp;
      long sizeUp;

   }

   private static class CopiedConsumedPage implements ConsumedPage {
      boolean done;
      IntObjectHashMap<Boolean> acks;

      @Override
      public long getPageId() {
         throw new RuntimeException("method not implemented");
      }

      @Override
      public void forEachAck(BiConsumer<Integer, PagePosition> ackConsumer) {
         throw new RuntimeException("method not implemented");
      }

      @Override
      public boolean isDone() {
         return done;
      }

      @Override
      public boolean isAck(int messageNumber) {
         if (done) {
            return true;
         }
         if (acks != null) {
            return acks.get(messageNumber) != null;
         }
         return false;
      }
   }


}
