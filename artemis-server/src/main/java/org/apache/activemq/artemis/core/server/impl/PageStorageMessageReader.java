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
package org.apache.activemq.artemis.core.server.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.StorageMessageReader;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class PageStorageMessageReader implements StorageMessageReader {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final QueueImpl queue;

   private final PageIterator pageIterator;

   private final ReentrantLock depageLock = new ReentrantLock();

   private volatile boolean depagePending = false;

   private volatile boolean pageDelivered = false;

   public PageStorageMessageReader(QueueImpl queue) {
      this.queue = queue;
      if (queue.getPageSubscription() != null) {
         pageIterator = queue.getPageSubscription().iterator();
      } else {
         pageIterator = null;
      }
   }

   @Override
   public void lock() {
      depagePending = true;
      depageLock.lock();
   }

   @Override
   public void unlock() {
      depagePending = false;
      depageLock.unlock();
   }

   @Override
   public boolean allowDirectDelivery() {
      return pageIterator != null && !pageIterator.hasNext();
   }

   @Override
   public void checkRead() {
      if (queue.queueDestroyed) {
         return;
      }
      if (pageIterator != null && queue.getPageSubscription().isStorePaging()) {
         if (logger.isDebugEnabled()) {
            logger.debug("CheckDepage on queue name {}, id={}", queue.queueConfiguration.getName(), queue.queueConfiguration.getId());
         }
         pageDelivered = true;

         if (!depagePending && queue.needsDepage() && pageIterator.tryNext() != PageIterator.NextResult.noElements) {
            scheduleRead(false);
         }
      } else {
         pageDelivered = false;
      }
   }

   @Override
   public void scheduleRead(final boolean scheduleExpiry) {
      if (!depagePending) {
         logger.trace("Scheduling depage for queue {}", queue.queueConfiguration.getName());

         depagePending = true;
         queue.pageSubscription.getPagingStore().execute(() -> read(scheduleExpiry));
      }
   }

   public void read(final boolean scheduleExpiry) {
      depagePending = false;

      if (!depageLock.tryLock()) {
         return;
      }

      try {
         synchronized (this) {
            if (queue.isPaused() || pageIterator == null) {
               return;
            }
         }

         long timeout = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(QueueImpl.DELIVERY_TIMEOUT);

         if (logger.isTraceEnabled()) {
            logger.trace("QueueMemorySize before depage on queue={} is {}", queue.queueConfiguration.getName(), queue.queueMemorySize.getSize());
         }

         queue.directDeliver = false;

         int depaged = 0;
         while (timeout - System.nanoTime() > 0 && queue.needsDepage()) {
            PageIterator.NextResult status = pageIterator.tryNext();
            if (status == PageIterator.NextResult.retry) {
               continue;
            } else if (status == PageIterator.NextResult.noElements) {
               break;
            }

            depaged++;
            PagedReference reference = pageIterator.next();
            if (logger.isDebugEnabled()) {
               logger.debug("Depaging reference {} on queue {} depaged::{}", reference, queue.queueConfiguration.getName(), depaged);
            }
            queue.addTail(reference, false);
            pageIterator.remove();
         }

         if (logger.isDebugEnabled()) {
            final int maxSize = queue.pageSubscription.getPagingStore().getPageSizeBytes();

            if (depaged == 0 && queue.queueMemorySize.getSize() >= maxSize) {
               logger.debug("Couldn't depage any message as the maxSize on the queue was achieved. There are too many pending messages to be acked in reference to the page configuration");
            }

            if (logger.isDebugEnabled()) {
               logger.debug("Queue Memory Size after depage on queue={} is {} with maxSize = {}. Depaged {} messages, pendingDelivery={}, intermediateMessageReferences= {}, queueDelivering={}",
                            queue.queueConfiguration.getName(), queue.queueMemorySize.getSize(), maxSize, depaged, queue.messageReferences.size(), queue.intermediateMessageReferences.size(), queue.deliveringMetrics.getMessageCount());
            }
         }

         queue.deliverAsync(true);

         if (depaged > 0 && scheduleExpiry) {
            queue.expireReferences();
         }
      } finally {
         depageLock.unlock();
      }
   }

   @Override
   public int iterateMessages(String operationName, int flushLimit, boolean separatePageIterator, QueueImpl.QueueIterateAction messageAction, int count) throws Exception {
      if (pageIterator == null) {
         return count;
      }

      Transaction tx = new TransactionImpl(queue.storageManager);
      int txCount = 0;
      PageIterator theIterator;
      if (separatePageIterator) {
         theIterator = queue.pageSubscription.iterator();
      } else {
         theIterator = pageIterator;
      }

      try {
         while (theIterator.hasNext() && !messageAction.expectedHitsReached(count)) {
            PagedReference reference = theIterator.next();
            boolean matched = messageAction.match(reference);
            boolean acted = false;

            if (matched) {
               acted = messageAction.actMessage(tx, reference);
            }

            if (logger.isTraceEnabled()) {
               logger.trace("{} matched={} act={} on reference {}, during queue iteration", count, matched, acted, reference);
            }

            if (separatePageIterator) {
               if (acted) {
                  theIterator.remove();
               }
            } else {
               theIterator.remove();

               if (!acted) {
                  queue.addTail(reference, false);
                  if (!queue.needsDepage()) {
                     ActiveMQServerLogger.LOGGER.preventQueueManagementToFloodMemory(operationName, String.valueOf(queue.getName()));
                     break;
                  }
               }
            }

            if (matched) {
               txCount++;
               count++;
            }

            if (txCount > 0 && txCount % flushLimit == 0) {
               tx.commit();
               tx = new TransactionImpl(queue.storageManager);
               txCount = 0;
            }
         }

         if (txCount > 0) {
            tx.commit();
         }

      } finally {
         if (separatePageIterator) {
            theIterator.close();
         }
      }
      return count;
   }
}
