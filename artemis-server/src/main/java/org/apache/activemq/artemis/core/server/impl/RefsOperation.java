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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQShutdownException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class RefsOperation extends TransactionOperationAbstract {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AckReason reason;

   private final StorageManager storageManager;
   private Queue queue;
   List<MessageReference> refsToAck = new ArrayList<>();

   List<MessageReference> pagedMessagesToPostACK = null;

   /**
    * It will ignore redelivery check, which is used during consumer.close to not perform reschedule redelivery check
    */
   protected boolean ignoreRedeliveryCheck = false;

   private boolean delivering = true;

   private String lingerSessionId = null;

   public RefsOperation(Queue queue, AckReason reason, StorageManager storageManager) {
      this.queue = queue;
      this.reason = reason;
      this.storageManager = storageManager;
   }


   public RefsOperation setDelivering(boolean delivering) {
      this.delivering = delivering;
      return this;
   }

   public boolean isDelivering() {
      return delivering;
   }

   // once turned on, we shouldn't turn it off, that's why no parameters
   public void setIgnoreRedeliveryCheck() {
      ignoreRedeliveryCheck = true;
   }

   synchronized void addOnlyRefAck(final MessageReference ref) {
      refsToAck.add(ref);
   }

   synchronized void addAck(final MessageReference ref) {
      refsToAck.add(ref);
      if (ref.isPaged()) {
         if (pagedMessagesToPostACK == null) {
            pagedMessagesToPostACK = new ArrayList<>();
         }
         pagedMessagesToPostACK.add(ref);
         //here we do something to prevent page file
         //from being deleted until the operation is done.
         ((PagedReference)ref).addPendingFlag();
      }
   }

   @Override
   public void afterRollback(final Transaction tx) {
      Map<QueueImpl, LinkedList<MessageReference>> queueMap = new HashMap<>();

      long timeBase = System.currentTimeMillis();

      //add any already acked refs, this means that they have been transferred via a producer.send() and have no
      // previous state persisted.
      List<MessageReference> ackedRefs = new ArrayList<>();

      for (MessageReference ref : refsToAck) {
         clearLingerRef(ref);

         ref.emptyConsumerID();

         logger.trace("rolling back {}", ref);

         try {
            if (ref.isAlreadyAcked()) {
               ackedRefs.add(ref);
            }
            rollbackRedelivery(tx, ref, timeBase, queueMap);
         } catch (ActiveMQShutdownException e) {
            ActiveMQServerLogger.LOGGER.unableToProcessRedeliveryDuringRollback(ref.toString(), tx.toString(), e.getMessage());
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorCheckingDLQ(e);
         }
      }

      for (Map.Entry<QueueImpl, LinkedList<MessageReference>> entry : queueMap.entrySet()) {
         LinkedList<MessageReference> refs = entry.getValue();

         QueueImpl queue = entry.getKey();

         queue.postRollback(refs);
      }

      if (!ackedRefs.isEmpty()) {
         //since pre acked refs have no previous state we need to actually create this by storing the message and the
         //message references
         try {
            Transaction ackedTX = new TransactionImpl(storageManager);
            for (MessageReference ref : ackedRefs) {
               Message message = ref.getMessage();
               if (message.isDurable()) {
                  int durableRefCount = ref.getQueue().durableUp(ref.getMessage());

                  if (durableRefCount == 1) {
                     storageManager.storeMessageTransactional(ackedTX.getID(), message);
                  }

                  storageManager.storeReferenceTransactional(ackedTX.getID(), queue.getID(), message.getMessageID());

                  ackedTX.setContainsPersistent();
               }

               ref.getQueue().refUp(ref);
            }
            ackedTX.commit(true);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToProcessMessageReferenceAfterRollback(e);
         }
      }

      if (pagedMessagesToPostACK != null) {
         for (MessageReference refmsg : pagedMessagesToPostACK) {
            ((PagedReference)refmsg).removePendingFlag();
         }
      }
   }

   protected void rollbackRedelivery(Transaction tx, MessageReference ref, long timeBase, Map<QueueImpl, LinkedList<MessageReference>> queueMap) throws Exception {
      if (RefCountMessage.isRefTraceEnabled()) {
         RefCountMessage.deferredDebug(ref.getMessage(), "RollbackDelivery");
      }
      // if ignore redelivery check, we just perform redelivery straight
      if (ref.getQueue().checkRedelivery(ref, timeBase, ignoreRedeliveryCheck).getA()) {
         LinkedList<MessageReference> toCancel = queueMap.get(ref.getQueue());

         if (toCancel == null) {
            toCancel = new LinkedList<>();

            queueMap.put((QueueImpl) ref.getQueue(), toCancel);
         }

         toCancel.addFirst(ref);
      }
   }

   @Override
   public void afterCommit(final Transaction tx) {
      for (MessageReference ref : refsToAck) {
         clearLingerRef(ref);

         synchronized (ref.getQueue()) {
            ref.getQueue().postAcknowledge(ref, reason, delivering);
         }
      }

      if (pagedMessagesToPostACK != null) {
         for (MessageReference refmsg : pagedMessagesToPostACK) {
            ((PagedReference)refmsg).removePendingFlag();
            if (((PagedReference) refmsg).isLargeMessage()) {
               refmsg.getQueue().refDown(refmsg);
            }
         }
      }
   }

   private void clearLingerRef(MessageReference ref) {
      if (!ref.hasConsumerId() && lingerSessionId != null) {
         ref.getQueue().removeLingerSession(lingerSessionId);
      }
   }

   @Override
   public synchronized List<MessageReference> getRelatedMessageReferences() {
      List<MessageReference> listRet = new LinkedList<>();

      if (refsToAck != null && !refsToAck.isEmpty()) {
         listRet.addAll(refsToAck);
      }

      return listRet;
   }

   @Override
   public synchronized List<MessageReference> getListOnConsumer(long consumerID) {
      List<MessageReference> list = new LinkedList<>();
      for (MessageReference ref : refsToAck) {
         if (ref.hasConsumerId() && ref.getConsumerId() == consumerID) {
            list.add(ref);
         }
      }

      return list;
   }

   public List<MessageReference> getReferencesToAcknowledge() {
      return refsToAck;
   }

   public synchronized List<MessageReference> getLingerMessages() {
      List<MessageReference> list = new LinkedList<>();
      for (MessageReference ref : refsToAck) {
         if (!ref.hasConsumerId() && lingerSessionId != null) {
            list.add(ref);
         }
      }

      return list;
   }

   public void setLingerSession(String lingerSessionId) {
      this.lingerSessionId = lingerSessionId;
   }
}
