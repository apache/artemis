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
package org.apache.activemq.artemis.core.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ReferenceCounter;
import org.apache.activemq.artemis.utils.collections.NodeStore;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStoreFactory;
import org.apache.activemq.artemis.utils.critical.CriticalComponent;

public interface Queue extends Bindable, CriticalComponent {

   int MAX_CONSUMERS_UNLIMITED = -1;

   SimpleString getName();

   Long getID();

   Filter getFilter();

   void setFilter(Filter filter);

   PagingStore getPagingStore();

   PageSubscription getPageSubscription();

   RoutingType getRoutingType();

   void setRoutingType(RoutingType routingType);

   /**
    * the current queue and consumer settings will allow use of the Reference Execution and callback.
    *  This is because
    */
   boolean allowsReferenceCallback();

   boolean isDurable();

   int durableUp(Message message);

   int durableDown(Message message);

   void refUp(MessageReference messageReference);

   void refDown(MessageReference messageReference);

   /**
    * Remove item with a supplied non-negative {@literal (>= 0) } ID. If the idSupplier returns {@literal < 0} the ID is
    * considered a non value (null) and it will be ignored.
    *
    * @see org.apache.activemq.artemis.utils.collections.LinkedList#setNodeStore(NodeStore)
    */
   MessageReference removeWithSuppliedID(String serverID, long id, NodeStoreFactory<MessageReference> nodeStore);

   /**
    * The queue definition could be durable, but the messages could eventually be considered non durable. (e.g.
    * purgeOnNoConsumers)
    */
   boolean isDurableMessage();

   boolean isAutoDelete();

   default boolean isSwept() {
      return false;
   }

   default void setSwept(boolean sweep) {
   }

   long getAutoDeleteDelay();

   long getAutoDeleteMessageCount();

   boolean isTemporary();

   boolean isAutoCreated();

   boolean isPurgeOnNoConsumers();

   void setPurgeOnNoConsumers(boolean value);

   boolean isEnabled();

   void setEnabled(boolean value);

   int getConsumersBeforeDispatch();

   void setConsumersBeforeDispatch(int consumersBeforeDispatch);

   long getDelayBeforeDispatch();

   void setDelayBeforeDispatch(long delayBeforeDispatch);

   long getDispatchStartTime();

   boolean isDispatching();

   void setDispatching(boolean dispatching);

   boolean isExclusive();

   void setExclusive(boolean value);

   boolean isLastValue();

   SimpleString getLastValueKey();

   boolean isNonDestructive();

   void setNonDestructive(boolean nonDestructive);

   int getMaxConsumers();

   void setMaxConsumer(int maxConsumers);

   int getGroupBuckets();

   void setGroupBuckets(int groupBuckets);

   boolean isGroupRebalance();

   void setGroupRebalance(boolean groupRebalance);

   boolean isGroupRebalancePauseDispatch();

   void setGroupRebalancePauseDispatch(boolean groupRebalancePauseDisptach);

   SimpleString getGroupFirstKey();

   void setGroupFirstKey(SimpleString groupFirstKey);

   boolean isConfigurationManaged();

   void setConfigurationManaged(boolean configurationManaged);

   void addConsumer(Consumer consumer) throws Exception;

   void addLingerSession(String sessionId);

   void removeLingerSession(String sessionId);

   void removeConsumer(Consumer consumer);

   int getConsumerCount();

   long getConsumerRemovedTimestamp();

   void setRingSize(long ringSize);

   long getRingSize();

   int getInitialQueueBufferSize();

   default boolean isMirrorController() {
      return false;
   }

   default void setMirrorController(boolean mirrorController) {
   }

   /**
    * This will hold a reference counter for every consumer present on the queue. The ReferenceCounter will know what to
    * do when the counter became zeroed. This is used to control what to do with temporary queues, especially on shared
    * subscriptions where the queue needs to be deleted when all the consumers are closed.
    */
   ReferenceCounter getConsumersRefCount();

   /**
    * Called when a message is cancelled back into the queue
    */
   void addSorted(List<MessageReference> refs, boolean scheduling);

   void reload(MessageReference ref);

   void reloadSequence(MessageReference ref);

   default void flushOnIntermediate(Runnable runnable) {
   }

   void addTail(MessageReference ref);

   void addTail(MessageReference ref, boolean direct);

   void addHead(MessageReference ref, boolean scheduling);

   /**
    * Called when a message is cancelled back into the queue
    */
   void addSorted(MessageReference ref, boolean scheduling);

   void addHead(List<MessageReference> refs, boolean scheduling);

   void acknowledge(MessageReference ref) throws Exception;

   void acknowledge(MessageReference ref, ServerConsumer consumer) throws Exception;

   void acknowledge(MessageReference ref, AckReason reason, ServerConsumer consumer) throws Exception;

   void acknowledge(Transaction tx, MessageReference ref) throws Exception;

   void acknowledge(Transaction tx, MessageReference ref, AckReason reason, ServerConsumer consumer, boolean delivering) throws Exception;

   void reacknowledge(Transaction tx, MessageReference ref) throws Exception;

   void cancel(Transaction tx, MessageReference ref);

   void cancel(Transaction tx, MessageReference ref, boolean ignoreRedeliveryCheck);

   void cancel(MessageReference reference, long timeBase) throws Exception;

   void deliverAsync();

   void unproposed(SimpleString groupID);

   /**
    * This method will make sure that any pending message (including paged message) will be delivered
    */
   void forceDelivery();

   void deleteQueue() throws Exception;

   void deleteQueue(boolean removeConsumers) throws Exception;

   /**
    * This method will push a removeAddress call into server's remove address
    */
   void removeAddress() throws Exception;

   void destroyPaging() throws Exception;

   long getMessageCount();

   /**
    * This is the size of the messages in the queue when persisted on disk which is used for metrics tracking to give an
    * idea of the amount of data on the queue to be consumed
    * <p>
    * Note that this includes all messages on the queue, even messages that are non-durable which may only be in memory
    */
   long getPersistentSize();

   /**
    * This is the number of the durable messages in the queue
    */
   long getDurableMessageCount();

   /**
    * This is the persistent size of all the durable messages in the queue
    */
   long getDurablePersistentSize();

   int getDeliveringCount();

   long getDeliveringSize();

   int getDurableDeliveringCount();

   long getDurableDeliveringSize();

   void referenceHandled(MessageReference ref);

   int getScheduledCount();

   long getScheduledSize();

   int getDurableScheduledCount();

   long getDurableScheduledSize();

   List<MessageReference> getScheduledMessages();

   /**
    * Return a Map consisting of consumer.toString and its messages. Delivering message is a property of the consumer.
    * This method will aggregate the results per Server's consumer object.
    */
   Map<String, List<MessageReference>> getDeliveringMessages();

   long getMessagesAdded();

   long getAcknowledgeAttempts();

   long getMessagesAcknowledged();

   long getMessagesExpired();

   long getMessagesKilled();

   long getMessagesReplaced();

   MessageReference removeReferenceWithID(long id) throws Exception;

   int deleteAllReferences() throws Exception;

   int deleteAllReferences(int flushLimit) throws Exception;

   boolean deleteReference(long messageID) throws Exception;

   int deleteMatchingReferences(Filter filter) throws Exception;

   default int deleteMatchingReferences(int flushLImit, Filter filter) throws Exception {
      return deleteMatchingReferences(flushLImit, filter, AckReason.KILLED);
   }

   int deleteMatchingReferences(int flushLImit, Filter filter, AckReason ackReason) throws Exception;

   boolean expireReference(long messageID) throws Exception;

   /**
    * Expire all the references in the queue which matches the filter
    */
   int expireReferences(Filter filter) throws Exception;

   default void expireReferences() {
      expireReferences((Runnable)null);
   }

   void expireReferences(Runnable done);

   void expire(MessageReference ref) throws Exception;

   void expire(MessageReference ref, ServerConsumer consumer, boolean delivering) throws Exception;

   boolean sendMessageToDeadLetterAddress(long messageID) throws Exception;

   int sendMessagesToDeadLetterAddress(Filter filter) throws Exception;

   /**
    * {@return whether the message was actually sent to a DLA with bindings}
    */
   boolean sendToDeadLetterAddress(Transaction tx, MessageReference ref) throws Exception;

   boolean changeReferencePriority(long messageID, byte newPriority) throws Exception;

   int changeReferencesPriority(Filter filter, byte newPriority) throws Exception;

   boolean moveReference(long messageID, SimpleString toAddress, Binding binding, boolean rejectDuplicates) throws Exception;

   int moveReferences(Filter filter, SimpleString toAddress, Binding binding) throws Exception;

   int moveReferences(int flushLimit,
                      Filter filter,
                      SimpleString toAddress,
                      boolean rejectDuplicates,
                      Binding binding) throws Exception;

   int moveReferences(int flushLimit,
                      Filter filter,
                      SimpleString toAddress,
                      boolean rejectDuplicates,
                      int messageCount,
                      Binding binding) throws Exception;


   boolean copyReference(long messageID, SimpleString queue, Binding binding) throws Exception;

   int retryMessages(Filter filter) throws Exception;

   default int retryMessages(Filter filter, Integer expectedHits) throws Exception {
      return retryMessages(filter);
   }

   void addRedistributor(long delay);

   void cancelRedistributor();

   boolean hasMatchingConsumer(Message message);

   long getPendingMessageCount();

   Collection<Consumer> getConsumers();

   Map<SimpleString, Consumer> getGroups();

   void resetGroup(SimpleString groupID);

   void resetAllGroups();

   int getGroupCount();

   /**
    * {@return a Pair of Booleans: the first indicates whether redelivery happened; the second indicates whether or not
    * the message was actually sent to a DLA with bindings}
    */
   Pair<Boolean, Boolean> checkRedelivery(MessageReference ref, long timeBase, boolean ignoreRedeliveryDelay) throws Exception;

   /**
    * Move any references which are still pending delivery into the main list of references so that they are visible to
    * {@link #iterator()}.
    */
   default void flushIntermediateReferences() {
   }

   /**
    * It will iterate through memory only (not paging)
    */
   LinkedListIterator<MessageReference> iterator();

   default void forEach(java.util.function.Consumer<MessageReference> consumer) {
      synchronized (this) {
         try (LinkedListIterator<MessageReference> iterator = iterator()) {
            while (iterator.hasNext()) {
               consumer.accept(iterator.next());
            }
         }
      }
   }

   default MessageReference peekFirstMessage() {
      return null;
   }

   default MessageReference peekFirstScheduledMessage() {
      return null;
   }

   LinkedListIterator<MessageReference> browserIterator();

   SimpleString getExpiryAddress();

   SimpleString getDeadLetterAddress();

   /**
    * Pauses the queue. It will receive messages but won't give them to the consumers until resumed. If a queue is
    * paused, pausing it again will only throw a warning. To check if a queue is paused, invoke <i>isPaused()</i>
    */
   void pause();

   /**
    * Pauses the queue. It will receive messages but won't give them to the consumers until resumed. If a queue is
    * paused, pausing it again will only throw a warning. To check if a queue is paused, invoke {@link #isPaused()}.
    */
   void pause(boolean persist);

   void reloadPause(long recordID);

   /**
    * Resumes the delivery of message for the queue. If a queue is resumed, resuming it again will only throw a warning.
    * To check if a queue is resumed, invoke {@link #isPaused()}.
    */
   void resume();

   boolean isPaused();

   boolean isPersistedPause();

   Executor getExecutor();

   void resetAllIterators();

   boolean flushExecutor();

   void close() throws Exception;

   boolean isDirectDeliver();

   SimpleString getAddress();

   /**
    * We can't send stuff to DLQ on queues used on clustered-bridge-communication
    */
   boolean isInternalQueue();

   void setInternalQueue(boolean internalQueue);

   void resetMessagesAdded();

   void resetMessagesAcknowledged();

   void resetMessagesExpired();

   void resetMessagesKilled();

   void incrementMesssagesAdded();

   /**
    * cancels scheduled messages and send them to the head of the queue.
    */
   void deliverScheduledMessages() throws ActiveMQException;

   /**
    * cancels scheduled messages which match the filter and send them to the head of the queue.
    */
   void deliverScheduledMessages(String filter) throws ActiveMQException;

   /**
    * cancels scheduled message with the corresponding message ID and sends it to the head of the queue.
    */
   void deliverScheduledMessage(long messageId) throws ActiveMQException;

   void postAcknowledge(MessageReference ref, AckReason reason);

   void postAcknowledge(MessageReference ref, AckReason reason, boolean delivering);

   SimpleString getUser();

   void setUser(SimpleString user);

   /**
    * This is to perform a check on the counter again
    */
   void recheckRefCount(OperationContext context);

   default void errorProcessing(Consumer consumer, Throwable t, MessageReference messageReference) {

   }

   default QueueConfiguration getQueueConfiguration() {
      return null;
   }

   default long getCreatedTimestamp() {
      return -1;
   }
}
