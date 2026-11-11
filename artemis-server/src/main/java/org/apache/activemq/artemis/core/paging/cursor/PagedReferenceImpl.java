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
package org.apache.activemq.artemis.core.paging.cursor;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.AbstractProtocolReference;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class PagedReferenceImpl extends AbstractProtocolReference implements PagedReference, Runnable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final AtomicIntegerFieldUpdater<PagedReferenceImpl> DELIVERY_COUNT_UPDATER = AtomicIntegerFieldUpdater
      .newUpdater(PagedReferenceImpl.class, "deliveryCount");

   protected PagedMessage message;

   private static final long UNDEFINED_DELIVERY_TIME = Long.MIN_VALUE;
   private long deliveryTime = UNDEFINED_DELIVERY_TIME;

   private int persistedCount;

   private int messageEstimate = -1;

   // this is a cached position returned on getPosition.
   // just to avoid creating on object on each call
   PagePosition cachedPositionObject;

   /**
    * This will create a new PagePosition, or return one previously created. This method is used to avoid repetitions on
    * browsing iteration only.
    */
   @Override
   public PagePosition getPosition() {
      if (cachedPositionObject == null) {
         cachedPositionObject = getPagedMessage().newPositionObject();
      }
      return cachedPositionObject;
   }

   private long consumerID;

   private boolean hasConsumerID = false;

   @SuppressWarnings("unused")
   private volatile int deliveryCount = 0;

   protected final PageSubscription subscription;

   private boolean alreadyAcked;

   //0 is false, 1 is true, 2 not defined
   private static final byte IS_NOT_LARGE_MESSAGE = 0;
   private static final byte IS_LARGE_MESSAGE = 1;
   private static final byte UNDEFINED_IS_LARGE_MESSAGE = 2;
   private byte largeMessage;

   private long transactionID = -2;

   private long messageID = -1;

   private long messageSize = -1;

   private Consumer<? super MessageReference> onDelivery;

   //Durable field : 0 is false, 1 is true, -1 not defined
   private static final byte IS_NOT_DURABLE = 0;
   private static final byte IS_DURABLE = 1;
   private static final byte UNDEFINED_IS_DURABLE = -1;
   private byte durable = UNDEFINED_IS_DURABLE;

   @Override
   public Message getMessage() {
      return getPagedMessage().getMessage();
   }

   @Override
   public void onDelivery(Consumer<? super MessageReference> onDelivery) {
      this.onDelivery = onDelivery;
   }

   /**
    * It will call {@link Consumer#accept(Object)} on {@code this} of the {@link Consumer} registered in
    * {@link #onDelivery(Consumer)}, if any.
    */
   @Override
   public void run() {
      final Consumer<? super MessageReference> onDelivery = this.onDelivery;
      if (onDelivery != null) {
         try {
            onDelivery.accept(this);
         } finally {
            this.onDelivery = null;
         }
      }
   }

   @Override
   public synchronized PagedMessage getPagedMessage() {
      return message;
   }

   public PagedReferenceImpl(final PagedMessage message,
                             final PageSubscription subscription) {
      this.message = message;
      this.subscription = subscription;
      if (message != null) {
         this.largeMessage = message.getMessage().isLargeMessage() ? IS_LARGE_MESSAGE : IS_NOT_LARGE_MESSAGE;
         this.transactionID = message.getTransactionID();
         this.messageID = message.getMessage().getMessageID();
         this.durable = message.getMessage().isDurable() ? IS_DURABLE : IS_NOT_DURABLE;
         this.deliveryTime = message.getMessage().getScheduledDeliveryTime();
         //pre-cache the message size so we don't have to reload the message later if it is GC'd
         getPersistentSize();
      } else {
         this.largeMessage = UNDEFINED_IS_LARGE_MESSAGE;
         this.transactionID = -2;
         this.messageID = -1;
         this.messageSize = -1;
         this.durable = UNDEFINED_IS_DURABLE;
         this.deliveryTime = UNDEFINED_DELIVERY_TIME;
      }
   }

   @Override
   public boolean isPaged() {
      return true;
   }

   @Override
   public void setPersistedCount(int count) {
      this.persistedCount = count;
   }

   @Override
   public int getPersistedCount() {
      return persistedCount;
   }

   @Override
   public int getMessageMemoryEstimate() {
      if (messageEstimate <= 0) {
         try {
            messageEstimate = getMessage().getMemoryEstimate();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorCalculateMessageMemoryEstimate(e);
         }
      }
      return messageEstimate;
   }

   @Override
   public MessageReference copy(final Queue queue) {
      return new PagedReferenceImpl(this.getPagedMessage(), this.subscription);
   }

   @Override
   public long getScheduledDeliveryTime() {
      if (deliveryTime == UNDEFINED_DELIVERY_TIME) {
         try {
            Message msg = getMessage();
            return msg.getScheduledDeliveryTime();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorCalculateScheduledDeliveryTime(e);
            return 0L;
         }
      }
      return deliveryTime;
   }

   @Override
   public void setScheduledDeliveryTime(final long scheduledDeliveryTime) {
      assert scheduledDeliveryTime != UNDEFINED_DELIVERY_TIME : "can't use a reserved value";
      deliveryTime = scheduledDeliveryTime;
   }

   @Override
   public int getDeliveryCount() {
      return DELIVERY_COUNT_UPDATER.get(this);
   }

   @Override
   public void setDeliveryCount(final int deliveryCount) {
      DELIVERY_COUNT_UPDATER.set(this, deliveryCount);
   }

   @Override
   public void incrementDeliveryCount() {
      DELIVERY_COUNT_UPDATER.incrementAndGet(this);
      if (logger.isTraceEnabled()) {
         logger.trace("++deliveryCount = {} for {}", deliveryCount, this, new Exception("trace"));
      }
   }

   @Override
   public void decrementDeliveryCount() {
      DELIVERY_COUNT_UPDATER.decrementAndGet(this);
      if (logger.isTraceEnabled()) {
         logger.trace("--deliveryCount = {} for {}", deliveryCount, this, new Exception("trace"));
      }
   }

   @Override
   public Queue getQueue() {
      return subscription.getQueue();
   }

   @Override
   public void handled() {
      getQueue().referenceHandled(this);
   }

   @Override
   public void setInDelivery(boolean inDelivery) {

   }

   @Override
   public boolean isInDelivery() {
      return false;
   }

   @Override
   public void setAlreadyAcked() {
      alreadyAcked = true;
   }

   @Override
   public boolean isAlreadyAcked() {
      return alreadyAcked;
   }

   @Override
   public void acknowledge() throws Exception {
      subscription.ack(this);
   }

   @Override
   public void acknowledge(Transaction tx) throws Exception {
      acknowledge(tx, null);
   }

   @Override
   public void acknowledge(Transaction tx, ServerConsumer consumer) throws Exception {
      acknowledge(tx, AckReason.NORMAL, consumer);
   }

   @Override
   public void acknowledge(Transaction tx, AckReason reason, ServerConsumer consumer) throws Exception {
      getQueue().acknowledge(tx, this, reason, consumer, true);
   }

   @Override
   public void acknowledge(Transaction tx, AckReason reason, ServerConsumer consumer, boolean delivering) throws Exception {
      getQueue().acknowledge(tx, this, reason, consumer, delivering);
   }

   @Override
   public String toString() {
      String msgToString;
      try {
         msgToString = getPagedMessage().toString();
      } catch (Throwable e) {
         // in case of an exception because of a missing page, we just want toString to return null
         msgToString = "error:" + e.getMessage();
      }
      return "PagedReferenceImpl [" +
         "message=" +
         msgToString +
         ", deliveryTime=" +
         (deliveryTime == UNDEFINED_DELIVERY_TIME ? null : deliveryTime) +
         ", persistedCount=" +
         persistedCount +
         ", deliveryCount=" +
         deliveryCount +
         ", subscription=" +
         subscription +
         "]";
   }

   @Override
   public void emptyConsumerID() {
      this.hasConsumerID = false;
   }

   @Override
   public void setConsumerId(long consumerID) {
      this.hasConsumerID = true;
      this.consumerID = consumerID;
   }

   @Override
   public boolean hasConsumerId() {
      return hasConsumerID;
   }

   @Override
   public long getConsumerId() {
      if (!this.hasConsumerID) {
         throw new IllegalStateException("consumerID isn't specified: please check hasConsumerId first");
      }
      return this.consumerID;
   }

   @Override
   public boolean isLargeMessage() {
      if (largeMessage == UNDEFINED_IS_LARGE_MESSAGE && message != null) {
         initializeIsLargeMessage();
      }
      return largeMessage == IS_LARGE_MESSAGE;
   }

   private void initializeIsLargeMessage() {
      assert largeMessage == UNDEFINED_IS_LARGE_MESSAGE && message != null;
      largeMessage = getMessage().isLargeMessage() ? IS_LARGE_MESSAGE : IS_NOT_LARGE_MESSAGE;
   }

   @Override
   public long getTransactionID() {
      if (transactionID < -1) {
         transactionID = getPagedMessage().getTransactionID();
      }
      return transactionID;
   }

   @Override
   public void addPendingFlag() {
      subscription.addPendingDelivery(this.getPagedMessage());
   }

   @Override
   public void removePendingFlag() {
      subscription.removePendingDelivery(this.getPagedMessage());
   }

   @Override
   public long getMessageID() {
      if (messageID < 0) {
         messageID = getPagedMessage().getMessage().getMessageID();
      }
      return messageID;
   }

   @Override
   public SimpleString getLastValueProperty() {
      SimpleString lastValue = getMessage().getSimpleStringProperty(getQueue().getLastValueKey());
      if (lastValue == null) {
         lastValue = getMessage().getLastValueProperty();
      }
      return lastValue;
   }

   @Override
   public long getPersistentSize() {
      if (messageSize == -1) {
         try {
            messageSize = getPagedMessage().getPersistentSize();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorCalculatePersistentSize(e);
         }
      }
      return messageSize;
   }

   @Override
   public boolean isDurable() {
      if (durable == UNDEFINED_IS_DURABLE) {
         durable = getMessage().isDurable() ? IS_DURABLE : IS_NOT_DURABLE;
      }
      return durable == IS_DURABLE;
   }

}
