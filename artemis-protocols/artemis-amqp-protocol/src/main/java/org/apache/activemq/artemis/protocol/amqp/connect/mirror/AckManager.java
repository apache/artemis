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

package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.collections.JournalHashMap;
import org.apache.activemq.artemis.core.journal.collections.JournalHashMapProvider;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AckRetry;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.server.mirror.MirrorRegistry;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AckManager implements ActiveMQComponent {

   // we first retry on the queue a few times
   private static DisabledAckMirrorController disabledAckMirrorController = new DisabledAckMirrorController();

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final Set<AMQPMirrorControllerTarget> mirrorControllerTargets = new HashSet<>();
   final LongSupplier sequenceGenerator;
   final JournalHashMapProvider<AckRetry, AckRetry, Queue> journalHashMapProvider;
   final ActiveMQServer server;
   final Configuration configuration;
   final ReferenceIDSupplier referenceIDSupplier;
   final IOCriticalErrorListener ioCriticalErrorListener;
   volatile MultiStepProgress progress;
   ActiveMQScheduledComponent scheduledComponent;

   final MirrorRegistry mirrorRegistry;

   public int size() {
      return mirrorRegistry.getMirrorAckSize();
   }

   public AckManager(ActiveMQServer server) {
      assert server != null && server.getConfiguration() != null;
      this.server = server;
      this.configuration = server.getConfiguration();
      this.ioCriticalErrorListener = server.getIoCriticalErrorListener();
      this.sequenceGenerator = server.getStorageManager()::generateID;
      this.mirrorRegistry = server.getMirrorRegistry();
      // The JournalHashMap has to use the storage manager to guarantee we are using the Replicated Journal Wrapper in case this is a replicated journal
      journalHashMapProvider = new JournalHashMapProvider<>(sequenceGenerator, server.getStorageManager(), AckRetry.getPersister(), JournalRecordIds.ACK_RETRY, OperationContextImpl::getContext, server.getPostOffice()::findQueue, server.getIoCriticalErrorListener());
      this.referenceIDSupplier = new ReferenceIDSupplier(server);
   }

   public void reload(RecordInfo recordInfo) {
      journalHashMapProvider.reload(recordInfo);
      mirrorRegistry.incrementMirrorAckSize();
   }

   @Override
   public synchronized void stop() {
      if (scheduledComponent != null) {
         scheduledComponent.stop();
         scheduledComponent = null;
      }
      AckManagerProvider.remove(this.server);
      logger.trace("Stopping ackmanager on server {}", server);
   }

   public synchronized void pause() {
      if (scheduledComponent != null) {
         scheduledComponent.stop();
         scheduledComponent = null;
      }
   }

   @Override
   public synchronized boolean isStarted() {
      return scheduledComponent != null && scheduledComponent.isStarted();
   }

   @Override
   public synchronized void start() {
      if (logger.isTraceEnabled()) {
         logger.trace("Starting ACKManager on {} with period = {}, minQueueAttempts={}, maxPageAttempts={}", server, configuration.getMirrorAckManagerRetryDelay(), configuration.getMirrorAckManagerQueueAttempts(), configuration.getMirrorAckManagerPageAttempts());
      }
      if (!isStarted()) {
         scheduledComponent = new ActiveMQScheduledComponent(server.getScheduledPool(), server.getExecutorFactory().getExecutor(), server.getConfiguration().getMirrorAckManagerRetryDelay(), server.getConfiguration().getMirrorAckManagerRetryDelay(), TimeUnit.MILLISECONDS, true) {
            @Override
            public void run() {
               beginRetry();
            }
         };
         scheduledComponent.start();
         scheduledComponent.delay();
      } else {
         logger.trace("Starting ignored on server {}", server);
      }
   }

   public void beginRetry() {
      logger.trace("being retry server {}", server);
      if (initRetry()) {
         logger.trace("Starting process to retry, server={}", server);
         progress.nextStep();
      } else {
         logger.trace("Retry already happened");
      }
   }

   public void endRetry() {
      logger.trace("Retry done on server {}", server);
      progress = null;

      // schedule a retry
      if (!sortRetries().isEmpty()) {
         ActiveMQScheduledComponent scheduleComponentReference = scheduledComponent;
         if (scheduleComponentReference != null) {
            try {
               scheduleComponentReference.delay();
            } catch (RejectedExecutionException ree) {
               logger.debug("AckManager could not schedule a new retry due to the executor being shutdown {}", ree.getMessage(), ree);
            }
         }
      }
   }

   public boolean initRetry() {
      if (progress != null) {
         logger.trace("Retry already in progress, we will wait next time, server={}", server);
         return false;
      }

      Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> retries = sortRetries();

      flushMirrorTargets();

      if (retries.isEmpty()) {
         logger.trace("Nothing to retry!, server={}", server);
         return false;
      }

      progress = new MultiStepProgress(retries);
      return true;
   }

   public synchronized void registerMirror(AMQPMirrorControllerTarget mirrorTarget) {
      this.mirrorControllerTargets.add(mirrorTarget);
   }

   public synchronized void unregisterMirror(AMQPMirrorControllerTarget mirrorTarget) {
      this.mirrorControllerTargets.remove(mirrorTarget);
   }

   private void flushMirrorTargets() {
      logger.trace("scanning and flushing mirror targets");
      List<AMQPMirrorControllerTarget> targetCopy = copyTargets();
      targetCopy.forEach(AMQPMirrorControllerTarget::flush);
   }

   private void checkFlowControlMirrorTargets() {
      List<AMQPMirrorControllerTarget> targetCopy = copyTargets();
      targetCopy.forEach(AMQPMirrorControllerTarget::verifyCredits);
   }

   private synchronized List<AMQPMirrorControllerTarget> copyTargets() {
      return new ArrayList<>(mirrorControllerTargets);
   }

   // Sort the ACK list by address
   // We have the retries by queue, we need to sort them by address
   // as we will perform all the retries on the same addresses at the same time (in the Multicast case with multiple queues acking)
   public Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> sortRetries() {
      // We will group the retries by address,
      // so we perform all of the queues in the same address at once
      Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> retriesByAddress = new HashMap<>();

      Iterator<JournalHashMap<AckRetry, AckRetry, Queue>> queueRetriesIterator = journalHashMapProvider.getMaps().iterator();

      while (queueRetriesIterator.hasNext()) {
         JournalHashMap<AckRetry, AckRetry, Queue> ackRetries = queueRetriesIterator.next();
         if (!ackRetries.isEmpty()) {
            Queue queue = ackRetries.getContext();
            if (queue != null) {
               SimpleString address = queue.getAddress();
               LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> queueRetriesOnAddress = retriesByAddress.get(address);
               if (queueRetriesOnAddress == null) {
                  queueRetriesOnAddress = new LongObjectHashMap<>();
                  retriesByAddress.put(address, queueRetriesOnAddress);
               }
               queueRetriesOnAddress.put(queue.getID(), ackRetries);
            }
         }
      }

      return retriesByAddress;
   }

   private boolean isSnapshotComplete(LongObjectHashMap<AtomicInteger> pendingSnapshot) {
      AtomicBoolean complete = new AtomicBoolean(true);
      pendingSnapshot.forEach((l, count) -> {
         if (count.get() > 0) {
            complete.set(false);
         }
      });

      return complete.get();
   }

   // to be used with the same executor as the PagingStore executor
   public void retryAddress(SimpleString address, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> acksToRetry) {


      // This is an optimization:
      // we peek at how many records we currently have. When we scan all the records that were initially input we would
      // interrupt the scan and start over
      // to avoid scanning for records that will never match since new ones are probably "in the past" now
      LongObjectHashMap<AtomicInteger> snapshotCount = buildCounterSnapshot(acksToRetry);

      MirrorController previousController = AMQPMirrorControllerTarget.getControllerInUse();
      logger.trace("retrying address {} on server {}", address, server);
      try {
         AMQPMirrorControllerTarget.setControllerInUse(disabledAckMirrorController);

         if (checkRetriesAndPaging(acksToRetry, snapshotCount)) {
            logger.trace("scanning paging for {}", address);

            PagingStore store = server.getPagingManager().getPageStore(address);
            for (long pageId = store.getFirstPage(); pageId <= store.getCurrentWritingPage(); pageId++) {
               if (isSnapshotComplete(snapshotCount)) {
                  logger.debug("AckManager Page Scanning complete (done) on address {}", address);
                  break;
               }
               Page page = openPage(store, pageId);
               if (page == null) {
                  continue;
               }

               if (logger.isDebugEnabled()) {
                  logger.debug("scanning for acks on page {}/{} on address {}", page.getPageId(), store.getCurrentWritingPage(), address);
               }
               try {
                  retryPage(snapshotCount, acksToRetry, address, page);
               } finally {
                  page.usageDown();
               }
            }

            // a note on the following statement:
            // I used to store the result of isSnapshotComplete in a variable
            // however in the case where we got to the end of the list and at the same time the snapshot was emptied,
            // we would still print any logging on the next block, and verify the expired sets
            // You can see the difference between this and the previous commit on ARTEMIS-5564
            if (!isSnapshotComplete(snapshotCount)) {
               // isSnapshotComplete == true, it means that every record meant to be acked was used,
               // so there is no need to check the expired set and we bypass this check
               // we used to check this every page scan, but as an optimization we removed this unecessary step
               validateExpiredSet(address, acksToRetry);

               if (logger.isDebugEnabled()) {
                  logger.debug("Retry page address got to the end of the list without still finding a few records to acknowledge");
                  snapshotCount.forEach((l, c) -> logger.debug("queue {} still have {} ack records after the scan is finished", l, c));
                  acksToRetry.forEach((l, m) -> {
                     logger.debug("Records on queue {}:", l);
                     m.forEach((ack1, ack2) -> {
                        if (ack1.getViewCount() > 0) {
                           logger.debug("Record {}", ack1);
                        }
                     });
                  });
               }
            }
         } else {
            logger.trace("Page Scan not required for address {}", address);
         }

         checkFlowControlMirrorTargets();

      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      } finally {
         AMQPMirrorControllerTarget.setControllerInUse(previousController);
      }
   }

   private static LongObjectHashMap<AtomicInteger> buildCounterSnapshot(LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> acksToRetry) {
      LongObjectHashMap<AtomicInteger> snapshotCount = new LongObjectHashMap<>();
      acksToRetry.forEach((l, map) -> {
         AtomicInteger recordCount = new AtomicInteger(0);
         snapshotCount.put(l, recordCount);
         map.forEach((ack1, ack2) -> {
            ack2.incrementViewCount();
            recordCount.incrementAndGet();
         });
         logger.trace("Building counter snapshot for Queue {} with {} ack elements", l, recordCount);
      });
      return snapshotCount;
   }

   private Page openPage(PagingStore store, long pageID) throws Throwable {
      Page page = store.newPageObject(pageID);
      if (page.getFile().exists()) {
         page.getMessages();
         return page;
      } else {
         return null;
      }

   }

   private void validateExpiredSet(SimpleString address, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> queuesToRetry) {
      queuesToRetry.forEach((q, r) -> this.validateExpireSet(address, q, r));
   }

   private void validateExpireSet(SimpleString address, long queueID, JournalHashMap<AckRetry, AckRetry, Queue> retries) {
      for (AckRetry retry : retries.valuesCopy()) {
         // we only remove or configure to be removed if the retry was initially seen on the start of the process
         // this is to avoid a race where an ACK entered the list after the scan been through where the element was supposed to be
         if (retry.getViewCount() > 0 && retry.getQueueAttempts() >= configuration.getMirrorAckManagerQueueAttempts()) {
            if (retry.attemptedPage() >= configuration.getMirrorAckManagerPageAttempts()) {
               if (configuration.isMirrorAckManagerWarnUnacked()) {
                  ActiveMQAMQPProtocolLogger.LOGGER.ackRetryFailed(retry, address, queueID);
               }
               if (logger.isDebugEnabled()) {
                  logger.debug("Retried {} {} times, giving up on the entry now. Configured Page Attempts={}", retry, retry.getPageAttempts(), configuration.getMirrorAckManagerPageAttempts());
               }
               if (retries.remove(retry) != null) {
                  mirrorRegistry.decrementMirrorAckSize();
               }
            } else {
               if (logger.isTraceEnabled()) {
                  logger.trace("Retry {} attempted {} times on paging, Configuration Page Attempts={}", retry, retry.getPageAttempts(), configuration.getMirrorAckManagerPageAttempts());
               }
            }
         } else {
            logger.trace("Retry {} queue attempted {} times on paging, QueueAttempts {} Configuration Page Attempts={}", retry, retry.getQueueAttempts(), retry.getPageAttempts(), configuration.getMirrorAckManagerPageAttempts());
         }
      }
   }

   private void retryPage(LongObjectHashMap<AtomicInteger> snapshotCount,
                          LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> queuesToRetry,
                          SimpleString address,
                          Page page) throws Exception {

      AckRetry key = new AckRetry();
      TransactionImpl transaction = new TransactionImpl(server.getStorageManager()).setAsync(true);
      // scan each page for acks
      page.getMessages().forEach(pagedMessage -> {
         for (int i = 0; i < pagedMessage.getQueueIDs().length; i++) {
            long queueID = pagedMessage.getQueueIDs()[i];
            JournalHashMap<AckRetry, AckRetry, Queue> retries = queuesToRetry.get(queueID);
            AtomicInteger snapshotOnQueue = snapshotCount.get(queueID);
            if (retries != null) {
               String serverID = referenceIDSupplier.getServerID(pagedMessage.getMessage());
               if (serverID == null) {
                  serverID = referenceIDSupplier.getDefaultNodeID();
               }
               long id = referenceIDSupplier.getID(pagedMessage.getMessage());

               key.setNodeID(serverID).setMessageID(id);

               AckRetry ackRetry = retries.get(key);

               if (ackRetry != null) {
                  Queue queue = retries.getContext();

                  if (queue != null) {
                     PageSubscription subscription = queue.getPageSubscription();
                     if (!subscription.isAcked(pagedMessage)) {
                        PagedReference reference = retries.getContext().getPagingStore().getCursorProvider().newReference(pagedMessage, subscription);
                        try {
                           subscription.ackTx(transaction, reference, false);
                           subscription.getQueue().postAcknowledge(reference, ackRetry.getReason(), false);
                        } catch (Exception e) {
                           logger.warn(e.getMessage(), e);
                           if (ioCriticalErrorListener != null) {
                              ioCriticalErrorListener.onIOException(e, e.getMessage(), null);
                           }
                        }
                     }
                     if (retries.remove(ackRetry, transaction.getID()) != null) {
                        mirrorRegistry.decrementMirrorAckSize();
                        decrementSnapshotCount(ackRetry, snapshotOnQueue);
                     }
                     transaction.setContainsPersistent();
                     logger.trace("retry performed ok, ackRetry={} for message={} on queue", ackRetry, pagedMessage);
                  }
               }
            }
         }
      });

      try {
         transaction.commit();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         if (ioCriticalErrorListener != null) {
            ioCriticalErrorListener.onIOException(e, e.getMessage(), null);
         }
      }
   }

   private void decrementSnapshotCount(AckRetry retry, AtomicInteger queueSnapshotCount) {
      // we check the view count as we only decrement the snapshot if the record was
      // in the initial list when we started the scan
      // otherwise we will of course ack the message since we have the record anyways
      // but we won't discount it from the snapshot counter
      if (retry.getViewCount() > 0) {
         if (queueSnapshotCount != null) {
            queueSnapshotCount.decrementAndGet();
         }
      } else {
         // we count the initial records on the retry list
         // however more records could still be flowing while the scan is being performed
         // on this case the record entered the scan list, we can actually acknowledge the entry, but we should not decrement
         // the snapshot count as it wasn't part of the initial list
         logger.trace("Update on snapshot count ignrored for {}", retry);
      }
   }

   /**
    * {@return {@code true} if there are retries ready to be scanned on paging}
    */
   private boolean checkRetriesAndPaging(LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> queuesToRetry, LongObjectHashMap<AtomicInteger> snapshotCount) {
      boolean needScanOnPaging = false;
      Iterator<Map.Entry<Long, JournalHashMap<AckRetry, AckRetry, Queue>>> iter = queuesToRetry.entrySet().iterator();

      while (iter.hasNext()) {
         Map.Entry<Long, JournalHashMap<AckRetry, AckRetry, Queue>> entry = iter.next();
         JournalHashMap<AckRetry, AckRetry, Queue> queueRetries = entry.getValue();
         Queue queue = queueRetries.getContext();
         AtomicInteger queueSnapshotCount = snapshotCount.get(queue.getID());
         for (AckRetry retry : queueRetries.valuesCopy()) {
            if (ack(retry.getNodeID(), queue, retry.getMessageID(), retry.getReason(), false)) {
               logger.trace("Removing retry {} as the retry went ok", retry);
               queueRetries.remove(retry);
               mirrorRegistry.decrementMirrorAckSize();
               decrementSnapshotCount(retry, queueSnapshotCount);
            } else {
               int retried = retry.attemptedQueue();
               if (logger.isTraceEnabled()) {
                  logger.trace("retry {} attempted {} times on the queue", retry, retried);
               }
               if (retried >= configuration.getMirrorAckManagerQueueAttempts()) {
                  needScanOnPaging = true;
               }
            }
         }
      }

      return needScanOnPaging;
   }

   public synchronized void addRetry(String nodeID, Queue queue, long messageID, AckReason reason) {
      if (nodeID == null) {
         nodeID = referenceIDSupplier.getDefaultNodeID();
      }
      AckRetry retry = new AckRetry(nodeID, messageID, reason);
      journalHashMapProvider.getMap(queue.getID(), queue).put(retry, retry);
      mirrorRegistry.incrementMirrorAckSize();
      if (scheduledComponent != null) {
         // we set the retry delay again in case it was changed.
         scheduledComponent.setPeriod(configuration.getMirrorAckManagerRetryDelay());
         scheduledComponent.delay();
      }
   }

   public boolean ack(String nodeID, Queue targetQueue, long messageID, AckReason reason, boolean allowRetry) {
      if (logger.isTraceEnabled()) {
         logger.trace("performAck (nodeID={}, messageID={}), targetQueue={}, allowRetry={})", nodeID, messageID, targetQueue.getName(), allowRetry);
      }

      MessageReference reference = targetQueue.removeWithSuppliedID(nodeID, messageID, referenceIDSupplier);

      if (reference == null) {
         if (logger.isTraceEnabled()) {
            logger.trace("ACK Manager could not find reference nodeID={} (while localID={}), messageID={} on queue {}, server={}. Adding retry with minQueue={}, maxPage={}, delay={}", nodeID, referenceIDSupplier.getDefaultNodeID(), messageID, targetQueue.getName(), server, configuration.getMirrorAckManagerQueueAttempts(), configuration.getMirrorAckManagerPageAttempts(), configuration.getMirrorAckManagerRetryDelay());
         }

         if (allowRetry) {
            if (configuration != null && configuration.isMirrorAckManagerWarnUnacked() && targetQueue.getConsumerCount() > 0) {
               ActiveMQAMQPProtocolLogger.LOGGER.unackWithConsumer(targetQueue.getConsumerCount(), targetQueue.getName(), nodeID, messageID);
            } else {
               logger.trace("There are {} consumers on queue {}, what made Ack for message with nodeID={}, messageID={} enter a retry list", targetQueue.getConsumerCount(), targetQueue.getName(), nodeID, messageID);
            }
            addRetry(nodeID, targetQueue, messageID, reason);
         }
         return false;
      } else  {
         if (logger.isTraceEnabled()) {
            logger.trace("ack worked well for messageID={} nodeID={} queue={}, reference={}", messageID, nodeID, reference.getQueue().getName(), reference);
            if (reference.isPaged()) {
               logger.trace("position for messageID={} = {}", messageID, ((PagedReference)reference).getPosition());
            }
         }
         doACK(targetQueue, reference, reason);
         return true;
      }
   }

   private void doACK(Queue targetQueue, MessageReference reference, AckReason reason) {
      try {
         switch (reason) {
            case EXPIRED:
               targetQueue.expire(reference, null, false);
               break;
            default:
               TransactionImpl transaction = new TransactionImpl(server.getStorageManager());
               targetQueue.acknowledge(transaction, reference, reason, null, false);
               transaction.commit();
               if (logger.isTraceEnabled()) {
                  logger.trace("Transaction {} committed on acking reference {}", transaction.getID(), reference);
               }
               break;
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      } finally {
         targetQueue.deliverAsync();
      }
   }
   /*
    * The ACKManager will perform the retry on each address's pageStore executor.
    * It will perform each address individually, one by one.
    */
   class MultiStepProgress {
      Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> retryList;

      Iterator<Map.Entry<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>>> retryIterator;


      MultiStepProgress(Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> retryList) {
         this.retryList = retryList;
         retryIterator = retryList.entrySet().iterator();
      }

      public void nextStep() {
         try {
            if (!retryIterator.hasNext()) {
               logger.trace("Iterator is done on retry, server={}", server);
               AckManager.this.endRetry();
            } else {
               Map.Entry<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> entry = retryIterator.next();

               //////////////////////////////////////////////////////////////////////
               // Issue a deliverAsync on each queue before doing the retries
               // to make it more likely to hit the ack retry on each queue
               entry.getValue().values().forEach(this::deliveryAsync);

               PagingStore pagingStore = server.getPagingManager().getPageStore(entry.getKey());
               pagingStore.execute(() -> {
                  AckManager.this.retryAddress(entry.getKey(), entry.getValue());
                  nextStep();
               });
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            // there was an exception, I'm clearing the current progress to allow a new one
            AckManager.this.endRetry();
         }
      }

      private void deliveryAsync(JournalHashMap<AckRetry, AckRetry, Queue> map) {
         Queue queue = map.getContext();
         if (queue != null) {
            queue.deliverAsync();
         }
      }
   }



   private static class DisabledAckMirrorController implements MirrorController {

      @Override
      public boolean isRetryACK() {
         return true;
      }

      @Override
      public void addAddress(AddressInfo addressInfo) throws Exception {

      }

      @Override
      public void deleteAddress(AddressInfo addressInfo) throws Exception {

      }

      @Override
      public void createQueue(QueueConfiguration queueConfiguration) throws Exception {

      }

      @Override
      public void deleteQueue(SimpleString addressName, SimpleString queueName, QueueConfiguration configuration) throws Exception {

      }

      @Override
      public void sendMessage(Transaction tx, Message message, RoutingContext context) {

      }

      @Override
      public void postAcknowledge(MessageReference ref, AckReason reason) throws Exception {

      }

      @Override
      public void preAcknowledge(Transaction tx, MessageReference ref, AckReason reason) throws Exception {

      }

      @Override
      public String getRemoteMirrorId() {
         return null;
      }
   }
}
