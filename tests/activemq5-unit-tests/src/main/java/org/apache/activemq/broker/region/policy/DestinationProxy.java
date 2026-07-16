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
package org.apache.activemq.broker.region.policy;

import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.DestinationStatistics;
import org.apache.activemq.broker.region.MessageReference;
import org.apache.activemq.broker.region.Subscription;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageDispatchNotification;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.management.CountStatisticImpl;
import org.apache.activemq.store.MessageStore;
import org.apache.activemq.usage.MemoryUsage;
import org.apache.activemq.usage.TempUsage;
import org.apache.activemq.usage.Usage;
import org.apache.activemq.usage.UsageCapacity;
import org.apache.activemq.usage.UsageListener;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

public class DestinationProxy implements Destination {
   private final String name;
   private final Queue view;
   private final ActiveMQServer server;

   public DestinationProxy(Queue view, String name, ActiveMQServer server) {
      this.view = view;
      this.name = name;
      this.server = server;
   }

   // Destination

   @Override
   public int getMaxInflatedDataSize() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addSubscription(ConnectionContext context, Subscription sub) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeSubscription(ConnectionContext context, Subscription sub, long lastDeliveredSequenceId) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void addProducer(ConnectionContext context, ProducerInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void removeProducer(ConnectionContext context, ProducerInfo info) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void send(ProducerBrokerExchange producerExchange, Message messageSend) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void acknowledge(ConnectionContext context, Subscription sub, MessageAck ack, MessageReference node) throws IOException {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public long getInactiveTimeoutBeforeGC() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void markForGC(long timeStamp) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean canGC() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void gc() {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public ActiveMQDestination getActiveMQDestination() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public MemoryUsage getMemoryUsage() {
      return new MemoryUsage() {
         @Override
         public void waitForSpace() throws InterruptedException {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public boolean waitForSpace(long timeout) throws InterruptedException {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public boolean isFull() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void enqueueUsage(long value) throws InterruptedException {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void increaseUsage(long value) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void decreaseUsage(long value) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         protected long retrieveUsage() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public long getUsage() {
            try {
               final PagingStore pageStore = server.getPagingManager().getPageStore(view.getAddress());
               if (pageStore == null) {
                  return 0;
               }
               return pageStore.getAddressSize();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }

         @Override
         public void setUsage(long usage) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setPercentOfJvmHeap(int percentOfJvmHeap) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public boolean waitForSpace(long timeout, int highWaterMark) throws InterruptedException {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public boolean isFull(int highWaterMark) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void addUsageListener(UsageListener listener) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void removeUsageListener(UsageListener listener) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public int getNumUsageListeners() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public long getLimit() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setLimit(long limit) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         protected void onLimitChange() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public float getUsagePortion() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setUsagePortion(float usagePortion) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public int getPercentUsage() {
            final long total;
            try {
               final PagingStore pageStore = server.getPagingManager().getPageStore(view.getAddress());
               if (pageStore == null) {
                  return 0;
               }
               total = pageStore.getMaxSize();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
            return (int) ((float) getUsage() / total * 100.0);
         }

         @Override
         protected void setPercentUsage(int value) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public int getPercentUsageMinDelta() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setPercentUsageMinDelta(int percentUsageMinDelta) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         protected int caclPercentUsage() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public String getName() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setName(String name) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public String toString() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void start() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void stop() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         protected void addChild(MemoryUsage child) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         protected void removeChild(MemoryUsage child) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public boolean notifyCallbackWhenNotFull(Runnable callback) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public UsageCapacity getLimiter() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setLimiter(UsageCapacity limiter) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public int getPollingTime() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setPollingTime(int pollingTime) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public MemoryUsage getParent() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setParent(MemoryUsage parent) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public ThreadPoolExecutor getExecutor() {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public void setExecutor(ThreadPoolExecutor executor) {
            throw new UnsupportedOperationException("Not implemented yet");
         }

         @Override
         public boolean isStarted() {
            throw new UnsupportedOperationException("Not implemented yet");
         }
      };
   }

   @Override
   public void setMemoryUsage(MemoryUsage memoryUsage) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public TempUsage getTempUsage() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void dispose(ConnectionContext context) throws IOException {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isDisposed() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public DestinationStatistics getDestinationStatistics() {
      return new DestinationStatistics() {
         private CountStatisticImpl newFakeCountStatistic(Answer<?> getCountFunction) {
            CountStatisticImpl mock = Mockito.mock(CountStatisticImpl.class);
            Mockito.doAnswer(getCountFunction).when(mock).getCount();
            return mock;
         }

         @Override
         public CountStatisticImpl getEnqueues() {
            return newFakeCountStatistic(invocation -> view.getMessagesAdded());
         }

         @Override
         public CountStatisticImpl getDequeues() {
            return newFakeCountStatistic(invocation -> view.getMessagesAcknowledged());
         }

         @Override
         public CountStatisticImpl getDispatched() {
            return newFakeCountStatistic(invocation -> getDequeues().getCount() + getInflight().getCount());
         }

         @Override
         public CountStatisticImpl getExpired() {
            return newFakeCountStatistic(invocation -> view.getMessagesExpired());
         }

         @Override
         public CountStatisticImpl getMessages() {
            return newFakeCountStatistic(invocation -> view.getMessageCount());
         }

         @Override
         public CountStatisticImpl getInflight() {
            return newFakeCountStatistic(invocation -> (long) view.getDeliveringCount());
         }
      };
   }

   @Override
   public DeadLetterStrategy getDeadLetterStrategy() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public Message[] browse() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public MessageStore getMessageStore() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isProducerFlowControl() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setProducerFlowControl(boolean value) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isAlwaysRetroactive() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setAlwaysRetroactive(boolean value) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public long getBlockedProducerWarningInterval() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setBlockedProducerWarningInterval(long blockedProducerWarningInterval) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public int getMaxProducersToAudit() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setMaxProducersToAudit(int maxProducersToAudit) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public int getMaxAuditDepth() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setMaxAuditDepth(int maxAuditDepth) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isEnableAudit() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setEnableAudit(boolean enableAudit) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isActive() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public int getMaxPageSize() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setMaxPageSize(int maxPageSize) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public int getMaxBrowsePageSize() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setMaxBrowsePageSize(int maxPageSize) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isUseCache() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setUseCache(boolean useCache) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public int getMinimumMessageSize() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setMinimumMessageSize(int minimumMessageSize) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public int getCursorMemoryHighWaterMark() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setCursorMemoryHighWaterMark(int cursorMemoryHighWaterMark) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void wakeup() {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isLazyDispatch() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setLazyDispatch(boolean value) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void messageExpired(ConnectionContext context, Subscription subs, MessageReference node) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void messageConsumed(ConnectionContext context, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void messageDelivered(ConnectionContext context, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void messageDispatched(ConnectionContext context, Subscription sub, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void messageDiscarded(ConnectionContext context, Subscription sub, MessageReference messageReference) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void slowConsumer(ConnectionContext context, Subscription subs) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void fastProducer(ConnectionContext context, ProducerInfo producerInfo) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void isFull(ConnectionContext context, Usage<?> usage) {

   }

   @Override
   public List<Subscription> getConsumers() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void processDispatchNotification(MessageDispatchNotification messageDispatchNotification) throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isPrioritizedMessages() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public SlowConsumerStrategy getSlowConsumerStrategy() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isDoOptimzeMessageStorage() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void setDoOptimzeMessageStorage(boolean doOptimzeMessageStorage) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void clearPendingMessages(int pendingAdditionsCount) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void duplicateFromStore(Message message, Subscription subscription) {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean isSendDuplicateFromStoreToDLQ() {
      return false;
   }

   @Override
   public void setSendDuplicateFromStoreToDLQ(boolean arg) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public void start() throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public void stop() throws Exception {
      throw new UnsupportedOperationException("Not implemented yet");

   }

   @Override
   public boolean iterate() {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isAdvancedNetworkStatisticsEnabled() {
      return false;
   }

   @Override
   public void setAdvancedNetworkStatisticsEnabled(boolean advancedNetworkStatisticsEnabled) {
      throw new UnsupportedOperationException("Not implemented yet");
   }

   @Override
   public boolean isAdvancedMessageStatisticsEnabled() {
      return false;
   }

   @Override
   public void setAdvancedMessageStatisticsEnabled(boolean advancedMessageStatisticsEnabled) {
      throw new UnsupportedOperationException("Not implemented yet");
   }
}
