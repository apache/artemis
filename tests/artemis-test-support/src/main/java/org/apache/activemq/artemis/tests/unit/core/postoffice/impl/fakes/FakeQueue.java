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
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ReferenceCounter;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStoreFactory;
import org.apache.activemq.artemis.utils.critical.CriticalComponentImpl;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;

public class FakeQueue extends CriticalComponentImpl implements Queue {

   @Override
   public void setPurgeOnNoConsumers(boolean value) {

   }

   @Override
   public void reloadSequence(MessageReference ref) {
   }

   @Override
   public boolean isEnabled() {
      return false;
   }

   @Override
   public void setEnabled(boolean value) {

   }

   @Override
   public void expireReferences(Runnable done) {
   }

   @Override
   public PagingStore getPagingStore() {
      return null;
   }

   @Override
   public int durableUp(Message message) {
      return 1;
   }

   @Override
   public int durableDown(Message message) {
      return 1;
   }

   @Override
   public void refUp(MessageReference messageReference) {

   }

   @Override
   public void refDown(MessageReference messageReference) {

   }

   @Override
   public int getConsumersBeforeDispatch() {
      return 0;
   }

   @Override
   public void setConsumersBeforeDispatch(int consumersBeforeDispatch) {

   }

   @Override
   public void removeAddress() throws Exception {

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
   public boolean allowsReferenceCallback() {
      return false;
   }

   @Override
   public boolean isExclusive() {
      // no-op
      return false;
   }

   @Override
   public MessageReference removeWithSuppliedID(String serverID, long id, NodeStoreFactory<MessageReference> nodeStore) {
      return null;
   }

   @Override
   public void setExclusive(boolean value) {
      // no-op
   }

   @Override
   public boolean isLastValue() {
      // no-op
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
   public boolean isInternalQueue() {
      // no-op
      return false;
   }

   @Override
   public boolean sendToDeadLetterAddress(Transaction tx, MessageReference ref) throws Exception {
      return false;
   }

   @Override
   public void deleteQueue(boolean removeConsumers) throws Exception {
   }

   @Override
   public void unproposed(SimpleString groupID) {

   }

   @Override
   public void reloadPause(long recordID) {

   }

   @Override
   public void recheckRefCount(OperationContext context) {

   }

   @Override
   public boolean isPersistedPause() {
      return false;
   }

   @Override
   public int retryMessages(Filter filter) throws Exception {
      return 0;
   }

   @Override
   public void setInternalQueue(boolean internalQueue) {
      // no-op

   }

   @Override
   public long getAcknowledgeAttempts() {
      return 0;
   }

   @Override
   public void cancel(Transaction tx, MessageReference ref, boolean ignoreRedeliveryCheck) {
      // no-op
   }

   PageSubscription subs;

   @Override
   public boolean isDirectDeliver() {
      // no-op
      return false;
   }

   @Override
   public void close() {
      // no-op

   }

   public void forceCheckQueueSize() {
      // no-op

   }

   @Override
   public void reload(MessageReference ref) {
      // no-op

   }

   @Override
   public void pause(boolean persist) {

   }

   @Override
   public boolean flushExecutor() {
      return true;
   }

   @Override
   public void addHead(MessageReference ref, boolean scheduling) {
      // no-op

   }

   @Override
   public void addSorted(MessageReference ref, boolean scheduling) {

   }

   @Override
   public void addHead(List<MessageReference> ref, boolean scheduling) {
      // no-op

   }

   @Override
   public void addTail(MessageReference ref, boolean direct) {
      // no-op

   }

   @Override
   public void addTail(MessageReference ref) {
      // no-op

   }

   @Override
   public void resetAllIterators() {
      // no-op

   }

   private final SimpleString name;

   private final Long id;

   private boolean durable;

   private long messageCount;

   public FakeQueue(final SimpleString name) {
      this(name, 0);
   }

   public FakeQueue(final SimpleString name, final long id) {
      super(EmptyCriticalAnalyzer.getInstance(), 1);
      this.name = name;
      this.id = id;
   }

   @Override
   public void acknowledge(final MessageReference ref) throws Exception {
      // no-op

   }

   @Override
   public void acknowledge(final MessageReference ref, ServerConsumer consumer) throws Exception {
      // no-op

   }

   @Override
   public void acknowledge(MessageReference ref, AckReason reason, ServerConsumer consumer) throws Exception {
      // no-op

   }

   @Override
   public void acknowledge(final Transaction tx, final MessageReference ref) throws Exception {
      // no-op

   }

   @Override
   public void acknowledge(Transaction tx, MessageReference ref, AckReason reason, ServerConsumer consumer, boolean decDel) throws Exception {
      // no-op

   }

   @Override
   public void addConsumer(final Consumer consumer) throws Exception {
      // no-op

   }

   @Override
   public void addLingerSession(String sessionId) {

   }

   @Override
   public void removeLingerSession(String sessionId) {

   }

   @Override
   public void addRedistributor(final long delay) {
      // no-op

   }

   @Override
   public void cancel(final MessageReference reference, final long timeBase) throws Exception {
      // no-op

   }

   @Override
   public void cancel(final Transaction tx, final MessageReference ref) {
      // no-op

   }

   @Override
   public void cancelRedistributor() {
      // no-op

   }

   @Override
   public boolean changeReferencePriority(final long messageID, final byte newPriority) throws Exception {
      // no-op
      return false;
   }

   @Override
   public int changeReferencesPriority(Filter filter, byte newPriority) throws Exception {
      // no-op
      return 0;
   }

   @Override
   public Pair<Boolean, Boolean> checkRedelivery(final MessageReference ref,
                                  final long timeBase,
                                  final boolean check) throws Exception {
      // no-op
      return new Pair<>(false, false);
   }

   @Override
   public int deleteAllReferences() throws Exception {
      // no-op
      return 0;
   }

   @Override
   public int deleteMatchingReferences(final Filter filter) throws Exception {
      // no-op
      return 0;
   }

   @Override
   public boolean deleteReference(final long messageID) throws Exception {
      // no-op
      return false;
   }

   @Override
   public void deliverAsync() {
      // no-op

   }

   @Override
   public void expire(final MessageReference ref) throws Exception {
      // no-op

   }

   @Override
   public void expire(final MessageReference ref, final ServerConsumer consumer, boolean decDel) throws Exception {
      // no-op

   }

   @Override
   public boolean expireReference(final long messageID) throws Exception {
      // no-op
      return false;
   }

   @Override
   public void expireReferences() {
      // no-op

   }

   @Override
   public int expireReferences(final Filter filter) throws Exception {
      // no-op
      return 0;
   }

   @Override

   public int getConsumerCount() {
      // no-op
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
   public ReferenceCounter getConsumersRefCount() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
   }

   @Override
   public void addSorted(List<MessageReference> refs, boolean scheduling) {

   }

   @Override
   public Set<Consumer> getConsumers() {
      // no-op
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
   public int getDeliveringCount() {
      // no-op
      return 0;
   }

   @Override
   public Filter getFilter() {
      // no-op
      return null;
   }

   @Override
   public void setFilter(Filter filter) {

   }

   @Override
   public long getMessageCount() {
      return messageCount;
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

   public void setMessageCount(long messageCount) {
      this.messageCount = messageCount;
   }

   @Override
   public long getMessagesAdded() {
      // no-op
      return 0;
   }

   @Override
   public long getMessagesAcknowledged() {
      // no-op
      return 0;
   }

   @Override
   public long getMessagesExpired() {
      // no-op
      return 0;
   }

   @Override
   public long getMessagesKilled() {
      // no-op
      return 0;
   }

   @Override
   public long getMessagesReplaced() {
      // no-op
      return 0;
   }

   @Override
   public void resetMessagesAdded() {
      // no-op

   }

   @Override
   public void resetMessagesAcknowledged() {
      // no-op

   }

   @Override
   public void resetMessagesExpired() {
      // no-op

   }

   @Override
   public void resetMessagesKilled() {
      // no-op

   }

   @Override
   public void incrementMesssagesAdded() {

   }

   @Override
   public void deliverScheduledMessages() {

   }

   @Override
   public void deliverScheduledMessages(String filter) throws ActiveMQException {

   }

   @Override
   public void deliverScheduledMessage(long messageId) throws ActiveMQException {

   }

   @Override
   public SimpleString getName() {
      return name;
   }

   @Override
   public SimpleString getAddress() {
      // no-op
      return null;
   }

   @Override
   public Long getID() {
      return id;
   }

   @Override
   public int getScheduledCount() {
      // no-op
      return 0;
   }

   @Override
   public long getScheduledSize() {
      // no-op
      return 0;
   }

   @Override
   public List<MessageReference> getScheduledMessages() {
      // no-op
      return null;
   }

   @Override
   public boolean isDurableMessage() {
      // no-op
      return durable;
   }

   public FakeQueue setDurable(boolean durable) {
      this.durable = durable;
      return this;
   }

   @Override
   public boolean isDurable() {
      // no-op
      return false;
   }

   @Override
   public boolean isPaused() {
      // no-op
      return false;
   }

   @Override
   public boolean isAutoDelete() {
      // no-op
      return false;
   }

   @Override
   public long getAutoDeleteDelay() {
      // no-op
      return -1;
   }

   @Override
   public long getAutoDeleteMessageCount() {
      // no-op
      return -1;
   }

   @Override
   public boolean isTemporary() {
      // no-op
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
   public int getMaxConsumers() {
      return -1;
   }

   @Override
   public LinkedListIterator<MessageReference> iterator() {
      // no-op
      return null;
   }

   @Override
   public int moveReferences(final Filter filter, final SimpleString toAddress, Binding binding) throws Exception {
      // no-op
      return 0;
   }

   @Override
   public void pause() {
      // no-op

   }

   @Override
   public void reacknowledge(final Transaction tx, final MessageReference ref) throws Exception {
      // no-op

   }

   @Override
   public void referenceHandled(MessageReference ref) {
      // no-op

   }

   @Override
   public void removeConsumer(final Consumer consumer) {
   }

   public MessageReference removeFirstReference(final long id1) throws Exception {
      // no-op
      return null;
   }

   @Override
   public MessageReference removeReferenceWithID(final long id1) throws Exception {
      // no-op
      return null;
   }

   @Override
   public void resume() {
      // no-op

   }

   @Override
   public boolean sendMessageToDeadLetterAddress(final long messageID) throws Exception {
      // no-op
      return false;
   }

   @Override
   public int sendMessagesToDeadLetterAddress(Filter filter) throws Exception {
      // no-op
      return 0;
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
   public void route(final Message message, final RoutingContext context) throws Exception {
      // no-op

   }

   @Override
   public void routeWithAck(Message message, RoutingContext context) {

   }

   @Override
   public boolean hasMatchingConsumer(final Message message) {
      // no-op
      return false;
   }

   @Override
   public long getPendingMessageCount() {
      return 0;
   }

   @Override
   public Executor getExecutor() {
      // no-op
      return null;
   }

   public void addLast(MessageReference ref, boolean direct) {
      // no-op

   }

   @Override
   public PageSubscription getPageSubscription() {
      return subs;
   }

   @Override
   public RoutingType getRoutingType() {
      return ActiveMQDefaultConfiguration.getDefaultRoutingType();
   }

   @Override
   public void setRoutingType(RoutingType routingType) {

   }

   public FakeQueue setPageSubscription(PageSubscription sub) {
      this.subs = sub;
      if (subs != null) {
         sub.setQueue(this);
      }
      return this;
   }

   @Override
   public boolean moveReference(long messageID, SimpleString toAddress, Binding binding, boolean rejectDuplicates) throws Exception {
      // no-op
      return false;
   }

   @Override
   public int deleteAllReferences(int flushLimit) throws Exception {
      return 0;
   }

   @Override
   public int deleteMatchingReferences(int flushLImit, Filter filter, AckReason reason) throws Exception {
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
   public int moveReferences(int flushLimit, Filter filter, SimpleString toAddress, boolean rejectDuplicates, int messageCount, Binding binding) throws Exception {
      return 0;
   }

   @Override
   public boolean copyReference(long messageID, SimpleString address, Binding binding) throws Exception {
      return false;
   }

   @Override
   public void forceDelivery() {
      // no-op

   }

   @Override
   public void deleteQueue() throws Exception {
      // no-op
   }

   @Override
   public void destroyPaging() {
   }

   @Override
   public Map<String, List<MessageReference>> getDeliveringMessages() {
      return null;
   }

   @Override
   public LinkedListIterator<MessageReference> browserIterator() {
      return null;
   }

   @Override
   public void postAcknowledge(MessageReference ref, AckReason reason, boolean delivering) {
   }

   @Override
   public void postAcknowledge(MessageReference ref, AckReason reason) {
   }

   @Override
   public SimpleString getUser() {
      return null;
   }

   @Override
   public void setUser(SimpleString user) {
      // no-op
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
   public int getDurableScheduledCount() {
      return 0;
   }

   @Override
   public long getDurableScheduledSize() {
      return 0;
   }

   @Override
   public int getInitialQueueBufferSize() {
      return 0;
   }
}
