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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageTimedWriter extends ActiveMQScheduledComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final PagingStoreImpl pagingStore;

   private final StorageManager storageManager;

   protected final List<PageEvent> pageEvents = new ArrayList<>();

   protected volatile int pendingTasks = 0;

   protected final boolean syncNonTX;

   private final Semaphore writeCredits;

   private final int maxCredits;

   private static final AtomicIntegerFieldUpdater<PageTimedWriter> pendingTasksUpdater = AtomicIntegerFieldUpdater.newUpdater(PageTimedWriter.class, "pendingTasks");
   private final ReusableLatch pendingProcessings = new ReusableLatch(0);

   public boolean hasPendingIO() {
      return pendingTasksUpdater.get(this) > 0;
   }

   public static class PageEvent {

      PageEvent(OperationContext context, PagedMessage message, Transaction tx, RouteContextList listCtx, int credits, boolean replicated, boolean useFlowControl) {
         this.context = context;
         this.message = message;
         this.listCtx = listCtx;
         this.replicated = replicated;
         this.credits = credits;
         this.tx = tx;
         this.useFlowControl = useFlowControl;
      }

      final boolean replicated;
      final PagedMessage message;
      final OperationContext context;
      final RouteContextList listCtx;
      final Transaction tx;
      final int credits;
      final boolean useFlowControl;
   }

   public PageTimedWriter(int writeCredits, StorageManager storageManager, PagingStoreImpl pagingStore, ScheduledExecutorService scheduledExecutor, Executor executor, boolean syncNonTX, long timeSync) {
      super(scheduledExecutor, executor, timeSync, TimeUnit.NANOSECONDS, true);
      this.pagingStore = pagingStore;
      this.storageManager = storageManager;
      this.syncNonTX = syncNonTX;
      this.writeCredits = new Semaphore(writeCredits);
      this.maxCredits = writeCredits;
   }

   public int getMaxCredits() {
      return maxCredits;
   }

   @Override
   public void stop() {
      synchronized (this) {
         super.stop();
      }
      try {
         pendingProcessings.await(30, TimeUnit.SECONDS);
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }
   }

   /**
    * We increment task while holding the readLock. This is because we verify if the system is paging, and we get out of
    * paging when no pending tasks and no pending messages. We allocate a task while holding the read Lock. We cannot
    * call addTask within the lock as if the semaphore gets out of credits we would deadlock in certain cases.
    */
   public void incrementTask() {
      pendingTasksUpdater.incrementAndGet(this);
   }

   public int addTask(OperationContext context,
                                    PagedMessage message,
                                    Transaction tx,
                                    RouteContextList listCtx, boolean useFlowControl) {

      logger.trace("Adding paged message {} to paged writer", message);

      // the module using the page operation should later call flowControl from this class.
      // the only exception to this would be from tests where we don't really care about flow control on the TimedExecutor
      // also: the credits is based on the page size, and we use the encodeSize to flow it.
      int credits = Math.min(message.getEncodeSize() + PageReadWriter.SIZE_RECORD, maxCredits);

      synchronized (this) {
         if (!isStarted()) {
            throw new IllegalStateException("PageWriter Service is stopped");
         }

         if (tx != null) {
            // this will delay the commit record until the portion of this task has been completed
            tx.delay();
         }

         final boolean replicated = storageManager.isReplicated();
         PageEvent event = new PageEvent(context, message, tx, listCtx, credits, replicated, useFlowControl);
         context.pageSyncLineUp();
         if (replicated) {
            context.replicationLineUp();
         }
         this.pageEvents.add(event);
         delay();
      }

      return credits;
   }

   public void flowControl(int credits) {
      if (credits > 0) {
         writeCredits.acquireUninterruptibly(credits);
      }
   }

   private synchronized PageEvent[] extractPendingEvents() {
      if (!isStarted()) {
         return null;
      }
      if (pageEvents.isEmpty()) {
         return null;
      }
      pendingProcessings.countUp();
      PageEvent[] pendingsWrites = new PageEvent[pageEvents.size()];
      pendingsWrites = pageEvents.toArray(pendingsWrites);
      pageEvents.clear();
      return pendingsWrites;
   }

   @Override
   public void run() {
      if (!isStarted()) {
         return;
      }

      ArtemisCloseable closeable = storageManager.closeableReadLock(true);
      if (closeable == null) {
         logger.trace("Delaying PagedTimedWriter as it's currently locked");
         delay();
      } else {
         try {
            processMessages();
         } finally {
            closeable.close();
         }
      }
   }

   protected void processMessages() {
      PageEvent[] pendingEvents;
      boolean wasStarted;
      synchronized (this) {
         pendingEvents = extractPendingEvents();
         if (pendingEvents == null) {
            return;
         }
         wasStarted = isStarted();
      }

      OperationContext beforeContext = OperationContextImpl.getContext();

      try {
         if (wasStarted) {
            boolean requireSync = false;
            for (PageEvent event : pendingEvents) {
               OperationContextImpl.setContext(event.context);
               logger.trace("writing message {}", event.message);
               pagingStore.directWritePage(event.message, false, event.replicated);

               if (event.tx != null || syncNonTX) {
                  requireSync = true;
               }
            }
            if (requireSync) {
               logger.trace("performing sync");
               performSync();
            }
            for (PageEvent event : pendingEvents) {
               if (event.tx != null) {
                  event.tx.delayDone();
               }
            }
            logger.trace("Completing events");
            for (PageEvent event : pendingEvents) {
               event.context.pageSyncDone();
            }
         }
      } catch (Throwable e) {
         logger.warn("Captured Exception {}", e.getMessage(), e);
         ActiveMQException amqException = new ActiveMQIllegalStateException(e.getMessage());
         amqException.initCause(e);

         for (PageEvent event : pendingEvents) {
            if (logger.isTraceEnabled()) {
               logger.trace("Error processing Message {}, tx={} ", event.message, event.tx);
            }
            if (event.tx != null) {
               if (logger.isTraceEnabled()) {
                  logger.trace("tx.markRollbackOnly on TX {}", event.tx.getID());
               }
               event.tx.markAsRollbackOnly(amqException);
            }
         }

         // In case of failure, The context should propagate an exception to the client
         // We send an exception to the client even on the case of a failure
         // to avoid possible locks and the client not getting the exception back
         executor.execute(() -> {
            logger.trace("onError processing for callback", e);
            // The onError has to be called from a separate executor
            // because this PagedWriter will be holding the lock on the storage manager
            // and this might lead to a deadlock
            for (PageEvent event : pendingEvents) {
               if (logger.isTraceEnabled()) {
                  logger.trace("onError {}, error={}", event.message, e.getMessage());
               }
               event.context.onError(ActiveMQExceptionType.IO_ERROR.getCode(), e.getClass() + " during ioSync for paging on " + pagingStore.getStoreName() + ": " + e.getMessage());
            }
         });
      } finally {
         try {
            for (PageEvent event : pendingEvents) {
               pendingTasksUpdater.decrementAndGet(this);
               if (event.useFlowControl) {
                  writeCredits.release(event.credits);
               }
            }
            OperationContextImpl.setContext(beforeContext);
         } catch (Throwable t) {
            logger.debug(t.getMessage(), t);
         }
         pendingProcessings.countDown();
      }
   }

   protected void performSync() throws Exception {
      pagingStore.ioSync();
   }

   public int getAvailablePermits() {
      return writeCredits.availablePermits();
   }
}
