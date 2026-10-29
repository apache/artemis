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

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.database.browserIterator.DatabasePendingMessagesIterator;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.StorageMessageReader;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.SizeAwareMetric;
import org.apache.artemis.database.data.MessageData;
import org.apache.artemis.database.queries.MessageDeliveryUpdater;
import org.apache.artemis.database.queries.QueryUtil;
import org.apache.artemis.database.sql.SQLProvider;
import org.apache.artemis.database.worker.BorrowedWorker;
import org.apache.artemis.database.worker.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseStorageMessageReader implements StorageMessageReader {

   private static final int ID_BATCH_SIZE = 100;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final long readIdleTimeoutMillis;

   final QueueImpl queue;

   Supplier<Integer> prefetchBytes;

   Supplier<Integer> prefetchMessages;

   volatile boolean scheduled;

   final DataManager dataManager;

   final DatabaseStorageManager databaseStorageManager;

   final SizeAwareMetric pagedSize = new SizeAwareMetric();

   @Override
   public long getReaderPaged() {
      return pagedSize.getElements();
   }

   // -- Borrowed worker state (accessed only from the queue's executor thread) --

   /**
    * The currently borrowed worker, or null if none is active.
    */
   private BorrowedWorker borrowedWorker;

   /**
    * The open ResultSet cursor from the borrowed worker, or null.
    * Notice, this should only be updated from within the executor.
    */
   private ResultSet resultSet;

   // to be updated from the executor
   Connection secondaryConnection = null;
   // to be updated from the executor
   MessageDeliveryUpdater deliveryUpdater;


   /**
    * True if the ResultSet was fully consumed (next() returned false).
    */
   private boolean resultSetExhausted;

   public DatabaseStorageMessageReader(QueueImpl queue,
                                       DatabaseStorageManager databaseStorageManager,
                                       PagingStore pagingStore) {
      this(queue, databaseStorageManager, pagingStore::getPrefetchPageMessages, pagingStore::getPrefetchPageBytes);
   }

   public DatabaseStorageMessageReader(QueueImpl queue,
                                       DatabaseStorageManager databaseStorageManager,
                                       Supplier<Integer> prefetchMessages,
                                       Supplier<Integer> prefetchBytes) {
      this.queue = queue;
      this.databaseStorageManager = databaseStorageManager;
      this.dataManager = databaseStorageManager.getDataManager();
      this.readIdleTimeoutMillis = databaseStorageManager.getDatabaseReadIdleTimeout();
      this.prefetchMessages = prefetchMessages;
      this.prefetchBytes = prefetchBytes;
   }

   @Override
   public void scheduleRead(boolean scheduleExpiry) {
      if (borrowedWorker != null && !borrowedWorker.isReturned()) {
         // We already have a borrowed worker with an open cursor — resume on the queue executor
         borrowedWorker.resume();
      } else {
         // Need to borrow a fresh worker — fetchMessages runs once the worker is available
         String description = "MessageDispatch queue = " + queue.getName() + ", address = " + queue.getAddress();
         dataManager.borrowWorker(queue.getExecutor(), readIdleTimeoutMillis, this::cleanupWorker, this::fetchMessages, borrowed -> {
            assert resultSet == null;
            borrowedWorker = borrowed;
            resultSetExhausted = false;
            fetchMessages();
         }, description);
      }
   }

   /**
    * Core prefetch loop: opens a cursor if needed, reads a batch, delivers messages.
    * Called both on the initial borrow callback and on subsequent reads via
    * {@link BorrowedWorker#resume()}. Always runs on the queue's executor thread.
    */
   private void fetchMessages() {
      try {
         if (resultSet == null || resultSetExhausted) {
            // Need to open a new cursor (first call, or previous cursor was exhausted)
            if (!openCursor()) {
               return;
            }
         }

         if (queue.needsDepage()) {
            readBatch();
         }
         if (borrowedWorker != null && !borrowedWorker.isReturned()) {
            borrowedWorker.renewLease();
         }
      } catch (Throwable e) {
         logger.warn("Error during prefetch for queue {}: {}", queue.getName(), e.getMessage(), e);
         reconnectAndRetry(e);
      }
   }

   public long getPagedMessages() {
      return pagedSize.getElements();
   }

   public long getPagedBytes() {
      return pagedSize.getSize();
   }

   /**
    * Opens a new query cursor on the borrowed worker. On failure, the worker is
    * reconnected, returned to the pool, and a fresh read is scheduled.
    *
    * @return true if the cursor was opened successfully, false if the worker was returned
    */
   private boolean openCursor() {
      try {
         long start = System.currentTimeMillis();
         logger.info("Executing query....");
         resultSet = borrowedWorker.getWorker().pendingDeliveryQueryForUpdate.execute(queue.getID());

         long end = System.currentTimeMillis();
         logger.info("took {} milliseconds to start returning results", (end - start));
         resultSetExhausted = false;
         return true;
      } catch (Throwable e) {
         if (e instanceof OutOfMemoryError && dataManager.isPageJoinFetch()) {
            ActiveMQServerLogger.LOGGER.storageReaderPrefetchOOM(queue.getName().toString());
         } else {
            ActiveMQServerLogger.LOGGER.storageReaderPrefetchQueryFailed(queue.getName().toString(), e.getMessage());
         }
         reconnectAndRetry(e);
         return false;
      }
   }

   private Connection getSecondaryConnection() throws Exception {
      if (secondaryConnection == null) {
         secondaryConnection = dataManager.getDatabaseProvider().getConnection();
         secondaryConnection.setAutoCommit(false);
      }
      return secondaryConnection;
   }

   private MessageDeliveryUpdater getDeliveryUpdater() throws Exception {
      if (deliveryUpdater == null) {
         deliveryUpdater = new MessageDeliveryUpdater(dataManager.getDatabaseProvider(), getSecondaryConnection());
         deliveryUpdater.init();
      }
      return deliveryUpdater;
   }

   /**
    * Reads up to one batch of messages from the ResultSet cursor. Commits the
    * pending delivery updates after each batch. If the ResultSet is exhausted
    * or the pool demands the connection back, the borrowed worker is returned.
    */
   private void readBatch() throws Exception {
      boolean reschedule = false;
      try {
         int prefetchBytesValue = prefetchBytes.get();
         int prefetchMessagesValue = prefetchMessages.get();

         if (prefetchMessagesValue <= 0) {
            prefetchMessagesValue = 1000;
         }

         if (prefetchBytesValue <= 0) {
            prefetchBytesValue = 1024 * 1024;
         }

         int messagesRead = 0;
         int bytesRead = 0;
         boolean cursorHasMore = true;

         SQLProvider sqlProvider = dataManager.getDatabaseProvider().getSqlProvider();
         String messagesTable = sqlProvider.getMessages();

         ArrayList<Message> messageList = null;

         while (queue.needsDepage() && cursorHasMore) {
            if (borrowedWorker != null && borrowedWorker.needConnectionBack) {
               logger.debug("Pool demands connection back, aborting prefetch for queue {}", queue.getName());
               break;
            }

            if (resultSet.isClosed()) {
               logger.info("Cursor had to be closed....");
               // DB2 may close the cursor when another statement executes on the same connection.
               // Deliver what we have, return the worker, and force a reschedule.
               cursorHasMore = false;
               reschedule = true;
               break;
            }

            // There are 2 modes we can use on querying for paged deliveries. Single join, which costs more on the database
            // and on the Postgresql it will use more memory on the driver.
            // or a 2 queries approach where we fetch for the IDs, then for the message blobs.
            // the best choice depends on the database and size of the table
            assert (prefetchMessagesValue > 0);
            int batchLimit = Math.min(ID_BATCH_SIZE, prefetchMessagesValue - messagesRead);
            if (dataManager.isPageJoinFetch()) {
               for (int i = 0; i < batchLimit; i++) {
                  if (!resultSet.next()) {
                     cursorHasMore = false;
                     break;
                  }
                  MessageData messageData = QueryUtil.readMessageData(resultSet, 1, 2, 3, 4, 5, 6);
                  Message message = DatabaseStorageManager.decodeMessage(messageData.messageID, messageData.isLarge, messageData.isCore, messageData.messageBufferSupplier, messageData.largeBodySupplier, databaseStorageManager);
                  if (messageList == null) {
                     messageList = new ArrayList<>();
                  }
                  messageList.add(message);
                  messagesRead++;
                  bytesRead += messageData.memoryEstimate;
               }
            } else {
               ArrayList<String> ids = new ArrayList<>(batchLimit);

               for (int i = 0; i < batchLimit; i++) {
                  if (!resultSet.next()) {
                     cursorHasMore = false;
                     break;
                  }
                  String id = resultSet.getString(1);
                  ids.add(id);
               }

               if (ids.isEmpty()) {
                  cursorHasMore = false;
                  break;
               }

               try (Statement statement = getSecondaryConnection().createStatement(); ResultSet blobRecords = statement.executeQuery(sqlProvider.selectMessagesBlob(messagesTable, ids))) {
                  while (blobRecords.next()) {
                     MessageData messageData = QueryUtil.readMessageData(blobRecords, 1, 2, 3, 4, 5, 6);
                     Message message = DatabaseStorageManager.decodeMessage(messageData.messageID, messageData.isLarge, messageData.isCore, messageData.messageBufferSupplier, messageData.largeBodySupplier, databaseStorageManager);
                     if (messageList == null) {
                        messageList = new ArrayList<>();
                     }
                     messageList.add(message);
                     messagesRead++;
                     bytesRead += messageData.memoryEstimate;
                  }
               }
            }

            if (prefetchMessagesValue > 0 && messagesRead >= prefetchMessagesValue || prefetchBytesValue > 0 && bytesRead >= prefetchBytesValue) {
               logger.debug("breaking loop as too much been read");
               break;
            }
         }

         if (messageList != null) {
            for (Message message : messageList) {
               getDeliveryUpdater().updateDelivery(queue.getID(), message.getMessageID());
            }
            getDeliveryUpdater().flush();

            try {
               getSecondaryConnection().commit();
               deliverMessages(messageList);
            } catch (Throwable e) {
               logger.warn("Commit failed during prefetch for queue {}: {}", queue.getName(), e.getMessage(), e);
               dataManager.criticalError(e);
               return;
            }
         }

         if (!cursorHasMore) {
            resultSetExhausted = true;
            returnBorrowedWorkerNow();
         } else if (borrowedWorker != null && borrowedWorker.needConnectionBack) {
            returnBorrowedWorkerNow();
         }

      } finally {
         logger.debug("ReadBatch done");
         scheduled = false;
         if (reschedule) {
            scheduleRead(false);
         }
      }
   }

   /**
    * Cleanup action called when the borrowed worker is returned (on lease expiry,
    * explicit return, or demand-back). Closes the open ResultSet if any.
    * This is called from the queue's executor thread via BorrowedWorker.
    */
   private void cleanupWorker() {
      if (resultSet != null) {
         try {
            resultSet.close();
         } catch (Throwable e) { // catch throwable as JDBC can throw anything when connections are bad
            logger.debug("Error closing borrowed ResultSet for queue {}: {}", queue.getName(), e.getMessage(), e);
         }
         resultSet = null;
      }
      if (secondaryConnection != null) {
         try {
            secondaryConnection.close();
         } catch (Throwable e) { // catch thrwoable as JDBC can throw anything when connections are bad
            logger.debug("Error closing secondaryConnection", e);
         }
         secondaryConnection = null;
      }
      if (deliveryUpdater != null) {
         try {
            deliveryUpdater.close();
         } catch (Throwable e) {
            logger.debug("Error closing deliveryUpdater");
         }
         deliveryUpdater = null;
      }
      resultSetExhausted = false;
      borrowedWorker = null;
      resultSetExhausted = false;
   }

   /**
    * Explicitly return the borrowed worker now (e.g. because the cursor is exhausted
    * or an error occurred). The cleanup action runs as part of returnWorker().
    */
   private void returnBorrowedWorkerNow() {
      if (borrowedWorker != null && !borrowedWorker.isReturned()) {
         borrowedWorker.returnWorker();
      }
      // cleanupBorrowedWorker() is called by returnWorker() via the cleanup action,
      // but clear our references in case it wasn't set
      assert borrowedWorker == null;
      assert resultSet == null;
      assert !resultSetExhausted;
   }

   /**
    * On a connection failure: reconnect the worker (so it returns to the pool healthy),
    * then schedule a fresh read that will borrow a new worker.
    */
   private void reconnectAndRetry(Throwable cause) {
      if (borrowedWorker != null && !borrowedWorker.isReturned()) {
         java.sql.SQLException reconnectError = borrowedWorker.reconnect(cause);
         if (reconnectError != null) {
            logger.warn("Failed to reconnect worker for queue {}: {}", queue.getName(), reconnectError.getMessage(), reconnectError);
            dataManager.criticalError(reconnectError);
         }
         cleanupWorker();
         returnBorrowedWorkerNow();
      }
      scheduleRead(false);
   }

   private void deliverMessages(List<Message> messageList) {
      for (Message m : messageList) {
         MessageReference reference = new MessageReferenceImpl(m, queue);
         if (!addPending(-1, reference.getMessage().getMemoryEstimate() * -1)) {
            logger.warn("****** pagedDeliveries became negative on messageID {}", m.getMessageID(), new Exception("trace"));
            Runtime.getRuntime().halt(-1);
         }
         queue.refUp(reference);
         queue.durableUp(m);
         queue.addTail(reference, false);
      }
      queue.deliverAsync();
      // this call is always performed from a single thread call. no need to synchronized here
      scheduled = false;
   }

   @Override
   public void checkRead() {
      if (queue.isPaused()) {
         return;
      }
      if (pagedSize.getElements() < 0) {
         ActiveMQServerLogger.LOGGER.storageReaderNegativePagedCount(queue.getName().toString(), pagedSize.getElements());
      }
      if (pagedSize.getElements() <= 0) {
         logger.info("nothing to read.. give up on queue {}", queue.getName());
         return;
      }
      boolean needsDepage = pagedSize.getElements() > 0 && queue.needsDepage();
      synchronized (this) {
         if (!scheduled && !queue.isPaused() && needsDepage) {
            scheduled = true;
            scheduleRead(false);
         }
      }
   }

   @Override
   public void lock() {
   }

   @Override
   public void unlock() {
   }

   @Override
   public boolean allowDirectDelivery() {
      return false;
   }

   @Override
   public int iterateMessages(String operationName,
                              int flushLimit,
                              boolean separatePageIterator,
                              QueueImpl.QueueIterateAction messageAction,
                              int count) throws Exception {


      try (DatabasePendingMessagesIterator pendingMessagesIterator = databaseStorageManager.pendingMessagesIterator(queue, flushLimit)) {
         Transaction tx = new TransactionImpl(queue.storageManager);
         tx.setContainsPersistent();

         int txCount = 0;

         pendingMessagesIterator.openResources();

         while (pendingMessagesIterator.hasNext() && !messageAction.expectedHitsReached(count)) {
            MessageReference reference = pendingMessagesIterator.next();

            boolean matched = messageAction.match(reference);
            boolean acted = false;

            if (matched) {
               acted = messageAction.actMessage(tx, reference);
            }

            if (acted) {
               txCount++;
               databaseStorageManager.storeAcknowledgeTransactional(tx, queue.getID(), reference.getMessageID());

               if (txCount > 0 && txCount % flushLimit == 0) {
                  tx.commit();
                  tx = new TransactionImpl(queue.storageManager);
                  tx.setContainsPersistent();
                  txCount = 0;
               }
            }
         }

         if (txCount > 0) {
            tx.commit();
         }
      }

      try (Connection connection = databaseStorageManager.getDatabaseProvider().getConnection()) {
         databaseStorageManager.handleOrphanedMessages(connection);
      }

      return count;
   }

   @Override
   public void addPendingAfterStorage(long elements, long size, Transaction tx) {
      if (databaseStorageManager == null) {
         // it may happen on tests, in that case we are not testing for this interaction, so it's okay to return and ignore it
         return;
      }
      if (tx != null) {
         PendingDeliveryUpdate pendingDeliveryUpdate = tx.getOrCreateOperation(TransactionPropertyIndexes.PENDING_DELIVERY_UPDATE, PendingDeliveryUpdate::new);
         pendingDeliveryUpdate.addSize(queue, elements, size);
      } else {
         databaseStorageManager.afterCompleteOperations(new IOCallback() {
            @Override
            public void done() {
               StorageMessageReader reader = queue.getStorageMessageReader();
               if (reader != null) {
                  reader.addPending(elements, size);
               }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {

            }
         });
      }
   }

   @Override
   public boolean addPending(long elements, long size) {
      pagedSize.simpleAdd(elements, size);
      if (pagedSize.getElements() < 0) {
         return false;
      } else {
         return true;
      }
   }

   protected static class PendingDeliveryUpdate extends TransactionOperationAbstract {

      protected HashMap<Queue, SizeAwareMetric> pendingUpdates = new HashMap<>();

      public void addSize(Queue queue, long elements, long size) {
         SizeAwareMetric updateSizeAware = pendingUpdates.computeIfAbsent(queue, k -> new SizeAwareMetric());
         updateSizeAware.simpleAdd(elements, size);
      }

      @Override
      public void afterCommit(Transaction tx) {
         pendingUpdates.forEach(this::updateQueue);
      }

      private void updateQueue(Queue queue, SizeAwareMetric metric) {
         queue.getStorageMessageReader().addPending(metric.getElements(), metric.getSize());
      }
   }

}
