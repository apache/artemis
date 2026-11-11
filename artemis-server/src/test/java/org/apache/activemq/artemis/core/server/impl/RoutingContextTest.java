/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.ReferenceCounter;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStoreFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.activemq.artemis.utils.critical.CriticalCloseable;
import org.junit.jupiter.api.Test;

public class RoutingContextTest {

   private static class FakeQueueForRoutingContextTest implements Queue {

      final String name;
      final boolean isInternal;
      final boolean durable;

      FakeQueueForRoutingContextTest(String name, boolean isInternal, boolean durable) {
         this.name = name;
         this.isInternal = isInternal;
         this.durable = durable;
      }

      @Override
      public CriticalAnalyzer getCriticalAnalyzer() {
         return null;
      }

      @Override
      public CriticalCloseable measureCritical(int path) {
         return null;
      }

      @Override
      public boolean checkExpiration(long timeout, boolean reset) {
         return false;
      }

      @Override
      public void route(Message message, RoutingContext context) throws Exception {

      }

      @Override
      public void routeWithAck(Message message, RoutingContext context) throws Exception {

      }

      @Override
      public SimpleString getName() {
         return SimpleString.of(name);
      }

      @Override
      public Long getID() {
         return null;
      }

      @Override
      public Filter getFilter() {
         return null;
      }

      @Override
      public void setFilter(Filter filter) {

      }

      @Override
      public PagingStore getPagingStore() {
         return null;
      }

      @Override
      public PageSubscription getPageSubscription() {
         return null;
      }

      @Override
      public RoutingType getRoutingType() {
         return null;
      }

      @Override
      public void setRoutingType(RoutingType routingType) {

      }

      @Override
      public boolean allowsReferenceCallback() {
         return false;
      }

      @Override
      public boolean isDurable() {
         return durable;
      }

      @Override
      public int durableUp(Message message) {
         return 0;
      }

      @Override
      public int durableDown(Message message) {
         return 0;
      }

      @Override
      public void refUp(MessageReference messageReference) {

      }

      @Override
      public void refDown(MessageReference messageReference) {

      }

      @Override
      public MessageReference removeWithSuppliedID(String serverID, long id, NodeStoreFactory<MessageReference> nodeStore) {
         return null;
      }

      @Override
      public boolean isDurableMessage() {
         return false;
      }

      @Override
      public boolean isAutoDelete() {
         return false;
      }

      @Override
      public long getAutoDeleteDelay() {
         return 0;
      }

      @Override
      public long getAutoDeleteMessageCount() {
         return 0;
      }

      @Override
      public boolean isTemporary() {
         return false;
      }

      @Override
      public boolean isAutoCreated() {
         return false;
      }

      @Override
      public boolean isPurgeOnNoConsumers() {
         return false;
      }

      @Override
      public void setPurgeOnNoConsumers(boolean value) {

      }

      @Override
      public boolean isEnabled() {
         return false;
      }

      @Override
      public void setEnabled(boolean value) {

      }

      @Override
      public int getConsumersBeforeDispatch() {
         return 0;
      }

      @Override
      public void setConsumersBeforeDispatch(int consumersBeforeDispatch) {

      }

      @Override
      public long getDelayBeforeDispatch() {
         return 0;
      }

      @Override
      public void setDelayBeforeDispatch(long delayBeforeDispatch) {

      }

      @Override
      public long getDispatchStartTime() {
         return 0;
      }

      @Override
      public boolean isDispatching() {
         return false;
      }

      @Override
      public void setDispatching(boolean dispatching) {

      }

      @Override
      public boolean isExclusive() {
         return false;
      }

      @Override
      public void setExclusive(boolean value) {

      }

      @Override
      public boolean isLastValue() {
         return false;
      }

      @Override
      public SimpleString getLastValueKey() {
         return null;
      }

      @Override
      public boolean isNonDestructive() {
         return false;
      }

      @Override
      public void setNonDestructive(boolean nonDestructive) {

      }

      @Override
      public int getMaxConsumers() {
         return 0;
      }

      @Override
      public void setMaxConsumer(int maxConsumers) {

      }

      @Override
      public int getGroupBuckets() {
         return 0;
      }

      @Override
      public void setGroupBuckets(int groupBuckets) {

      }

      @Override
      public boolean isGroupRebalance() {
         return false;
      }

      @Override
      public void setGroupRebalance(boolean groupRebalance) {

      }

      @Override
      public boolean isGroupRebalancePauseDispatch() {
         return false;
      }

      @Override
      public void setGroupRebalancePauseDispatch(boolean groupRebalancePauseDisptach) {

      }

      @Override
      public SimpleString getGroupFirstKey() {
         return null;
      }

      @Override
      public void setGroupFirstKey(SimpleString groupFirstKey) {

      }

      @Override
      public boolean isConfigurationManaged() {
         return false;
      }

      @Override
      public void setConfigurationManaged(boolean configurationManaged) {

      }

      @Override
      public void addConsumer(Consumer consumer) throws Exception {

      }

      @Override
      public void addLingerSession(String sessionId) {

      }

      @Override
      public void removeLingerSession(String sessionId) {

      }

      @Override
      public void removeConsumer(Consumer consumer) {

      }

      @Override
      public int getConsumerCount() {
         return 0;
      }

      @Override
      public long getConsumerRemovedTimestamp() {
         return 0;
      }

      @Override
      public void setRingSize(long ringSize) {

      }

      @Override
      public long getRingSize() {
         return 0;
      }

      @Override
      public int getInitialQueueBufferSize() {
         return 0;
      }

      @Override
      public ReferenceCounter getConsumersRefCount() {
         return null;
      }

      @Override
      public void addSorted(List<MessageReference> refs, boolean scheduling) {

      }

      @Override
      public void reload(MessageReference ref) {

      }

      @Override
      public void reloadSequence(MessageReference ref) {
      }

      @Override
      public void addTail(MessageReference ref) {

      }

      @Override
      public void addTail(MessageReference ref, boolean direct) {

      }

      @Override
      public void addHead(MessageReference ref, boolean scheduling) {

      }

      @Override
      public void addSorted(MessageReference ref, boolean scheduling) {

      }

      @Override
      public void addHead(List<MessageReference> refs, boolean scheduling) {

      }

      @Override
      public void acknowledge(MessageReference ref) throws Exception {

      }

      @Override
      public void acknowledge(MessageReference ref, ServerConsumer consumer) throws Exception {

      }

      @Override
      public void acknowledge(MessageReference ref, AckReason reason, ServerConsumer consumer) throws Exception {

      }

      @Override
      public void acknowledge(Transaction tx, MessageReference ref) throws Exception {

      }

      @Override
      public void acknowledge(Transaction tx,
                              MessageReference ref,
                              AckReason reason,
                              ServerConsumer consumer,
                              boolean delivering) throws Exception {

      }

      @Override
      public void reacknowledge(Transaction tx, MessageReference ref) throws Exception {

      }

      @Override
      public void cancel(Transaction tx, MessageReference ref) {

      }

      @Override
      public void cancel(Transaction tx, MessageReference ref, boolean ignoreRedeliveryCheck) {

      }

      @Override
      public void cancel(MessageReference reference, long timeBase) throws Exception {

      }

      @Override
      public void deliverAsync() {

      }

      @Override
      public void unproposed(SimpleString groupID) {

      }

      @Override
      public void forceDelivery() {

      }

      @Override
      public void deleteQueue() throws Exception {

      }

      @Override
      public void deleteQueue(boolean removeConsumers) throws Exception {

      }

      @Override
      public void removeAddress() throws Exception {

      }

      @Override
      public void destroyPaging() throws Exception {

      }

      @Override
      public long getMessageCount() {
         return 0;
      }

      @Override
      public long getPersistentSize() {
         return 0;
      }

      @Override
      public long getDurableMessageCount() {
         return 0;
      }

      @Override
      public long getDurablePersistentSize() {
         return 0;
      }

      @Override
      public int getDeliveringCount() {
         return 0;
      }

      @Override
      public long getDeliveringSize() {
         return 0;
      }

      @Override
      public int getDurableDeliveringCount() {
         return 0;
      }

      @Override
      public long getDurableDeliveringSize() {
         return 0;
      }

      @Override
      public void referenceHandled(MessageReference ref) {

      }

      @Override
      public int getScheduledCount() {
         return 0;
      }

      @Override
      public long getScheduledSize() {
         return 0;
      }

      @Override
      public int getDurableScheduledCount() {
         return 0;
      }

      @Override
      public long getDurableScheduledSize() {
         return 0;
      }

      @Override
      public List<MessageReference> getScheduledMessages() {
         return null;
      }

      @Override
      public Map<String, List<MessageReference>> getDeliveringMessages() {
         return null;
      }

      @Override
      public long getMessagesAdded() {
         return 0;
      }

      @Override
      public long getAcknowledgeAttempts() {
         return 0;
      }

      @Override
      public long getMessagesAcknowledged() {
         return 0;
      }

      @Override
      public long getMessagesExpired() {
         return 0;
      }

      @Override
      public long getMessagesKilled() {
         return 0;
      }

      @Override
      public long getMessagesReplaced() {
         return 0;
      }

      @Override
      public MessageReference removeReferenceWithID(long id) throws Exception {
         return null;
      }

      @Override
      public int deleteAllReferences() throws Exception {
         return 0;
      }

      @Override
      public int deleteAllReferences(int flushLimit) throws Exception {
         return 0;
      }

      @Override
      public boolean deleteReference(long messageID) throws Exception {
         return false;
      }

      @Override
      public int deleteMatchingReferences(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public int deleteMatchingReferences(int flushLImit, Filter filter, AckReason ackReason) throws Exception {
         return 0;
      }

      @Override
      public boolean expireReference(long messageID) throws Exception {
         return false;
      }

      @Override
      public int expireReferences(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public void expireReferences(Runnable done) {

      }

      @Override
      public void expire(MessageReference ref) throws Exception {

      }

      @Override
      public void expire(MessageReference ref, ServerConsumer consumer, boolean delivering) throws Exception {

      }

      @Override
      public boolean sendMessageToDeadLetterAddress(long messageID) throws Exception {
         return false;
      }

      @Override
      public int sendMessagesToDeadLetterAddress(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public boolean sendToDeadLetterAddress(Transaction tx, MessageReference ref) throws Exception {
         return false;
      }

      @Override
      public boolean changeReferencePriority(long messageID, byte newPriority) throws Exception {
         return false;
      }

      @Override
      public int changeReferencesPriority(Filter filter, byte newPriority) throws Exception {
         return 0;
      }

      @Override
      public boolean moveReference(long messageID,
                                   SimpleString toAddress,
                                   Binding binding,
                                   boolean rejectDuplicates) throws Exception {
         return false;
      }

      @Override
      public int moveReferences(Filter filter, SimpleString toAddress, Binding binding) throws Exception {
         return 0;
      }

      @Override
      public int moveReferences(int flushLimit,
                                Filter filter,
                                SimpleString toAddress,
                                boolean rejectDuplicates,
                                Binding binding) throws Exception {
         return 0;
      }

      @Override
      public int moveReferences(int flushLimit,
                                Filter filter,
                                SimpleString toAddress,
                                boolean rejectDuplicates,
                                int messageCount,
                                Binding binding) throws Exception {
         return 0;
      }

      @Override
      public boolean copyReference(long messageID, SimpleString address, Binding binding) throws Exception {
         return false;
      }

      @Override
      public int retryMessages(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public void addRedistributor(long delay) {

      }

      @Override
      public void cancelRedistributor() {

      }

      @Override
      public boolean hasMatchingConsumer(Message message) {
         return false;
      }

      @Override
      public long getPendingMessageCount() {
         return 0;
      }

      @Override
      public Collection<Consumer> getConsumers() {
         return null;
      }

      @Override
      public Map<SimpleString, Consumer> getGroups() {
         return null;
      }

      @Override
      public void resetGroup(SimpleString groupID) {

      }

      @Override
      public void resetAllGroups() {

      }

      @Override
      public int getGroupCount() {
         return 0;
      }

      @Override
      public Pair<Boolean, Boolean> checkRedelivery(MessageReference ref,
                                                    long timeBase,
                                                    boolean ignoreRedeliveryDelay) throws Exception {
         return null;
      }

      @Override
      public LinkedListIterator<MessageReference> iterator() {
         return null;
      }

      @Override
      public LinkedListIterator<MessageReference> browserIterator() {
         return null;
      }

      @Override
      public SimpleString getExpiryAddress() {
         return null;
      }

      @Override
      public SimpleString getDeadLetterAddress() {
         return null;
      }

      @Override
      public void pause() {

      }

      @Override
      public void pause(boolean persist) {

      }

      @Override
      public void reloadPause(long recordID) {

      }

      @Override
      public void resume() {

      }

      @Override
      public boolean isPaused() {
         return false;
      }

      @Override
      public boolean isPersistedPause() {
         return false;
      }

      @Override
      public Executor getExecutor() {
         return null;
      }

      @Override
      public void resetAllIterators() {

      }

      @Override
      public boolean flushExecutor() {
         return false;
      }

      @Override
      public void close() throws Exception {

      }

      @Override
      public boolean isDirectDeliver() {
         return false;
      }

      @Override
      public SimpleString getAddress() {
         return SimpleString.of(name);
      }

      @Override
      public boolean isInternalQueue() {
         return isInternal;
      }

      @Override
      public void setInternalQueue(boolean internalQueue) {

      }

      @Override
      public void resetMessagesAdded() {

      }

      @Override
      public void resetMessagesAcknowledged() {

      }

      @Override
      public void resetMessagesExpired() {

      }

      @Override
      public void resetMessagesKilled() {

      }

      @Override
      public void incrementMesssagesAdded() {

      }

      @Override
      public void deliverScheduledMessages() throws ActiveMQException {

      }

      @Override
      public void deliverScheduledMessages(String filter) throws ActiveMQException {

      }

      @Override
      public void deliverScheduledMessage(long messageId) throws ActiveMQException {

      }

      @Override
      public void postAcknowledge(MessageReference ref, AckReason reason) {

      }

      @Override
      public void postAcknowledge(MessageReference ref, AckReason reason, boolean delivering) {

      }

      @Override
      public SimpleString getUser() {
         return null;
      }

      @Override
      public void setUser(SimpleString user) {

      }

      @Override
      public void recheckRefCount(OperationContext context) {

      }
   }

   @Test
   public void testValidateInternal() {
      RoutingContext context = new RoutingContextImpl(new TransactionImpl(new NullStorageManager()));
      assertFalse(context.isMirrorIgnore());

      context.addQueue(SimpleString.of("t1"), new FakeQueueForRoutingContextTest("t1", true, true));
      assertTrue(context.isMirrorIgnore());

      context.addQueue(SimpleString.of("t2"), new FakeQueueForRoutingContextTest("t2", false, true));
      assertFalse(context.isMirrorIgnore());

      context.addQueue(SimpleString.of("t3"), new FakeQueueForRoutingContextTest("t3", true, true));
      assertFalse(context.isMirrorIgnore());

      context.clear();
      assertFalse(context.isMirrorIgnore());

      context.addQueue(SimpleString.of("t1"), new FakeQueueForRoutingContextTest("t1", true, true));
      assertTrue(context.isMirrorIgnore());

      context.addQueue(SimpleString.of("t2"), new FakeQueueForRoutingContextTest("t2", true, true));
      assertTrue(context.isMirrorIgnore());

      context.addQueue(SimpleString.of("t3"), new FakeQueueForRoutingContextTest("t3", true, true));
      assertTrue(context.isMirrorIgnore());
   }

}
