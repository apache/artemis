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

import java.util.function.Consumer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.transaction.Transaction;

/**
 * This MessageReference should only be created the first time a group is assigned to a consumer, it allows us to make a
 * copy of the message to add the property safely as a delivery semantic, without affecting the underlying message.
 * <p>
 * The overhead is low, as noted above only should be created on first message in a group to a consumer.
 */
public class GroupFirstMessageReference implements MessageReference {

   private final MessageReference messageReference;
   private final SimpleString key;
   private volatile Message message;

   public GroupFirstMessageReference(SimpleString key, MessageReference messageReference) {
      this.messageReference = messageReference;
      this.key = key;
   }

   @Override
   public Message getMessage() {
      if (message == null) {
         synchronized (this) {
            if (message == null) {
               message = messageReference.getMessage().copy();
               message.putBooleanProperty(key, true).reencode();
            }
         }
      }
      return message;
   }

   @Override
   public boolean isPaged() {
      return messageReference.isPaged();
   }

   @Override
   public long getMessageID() {
      return messageReference.getMessageID();
   }

   @Override
   public boolean isDurable() {
      return messageReference.isDurable();
   }

   @Override
   public SimpleString getLastValueProperty() {
      return messageReference.getLastValueProperty();
   }

   @Override
   public void onDelivery(Consumer<? super MessageReference> callback) {
      messageReference.onDelivery(callback);
   }

   @Override
   public int getMessageMemoryEstimate() {
      return messageReference.getMessageMemoryEstimate();
   }

   @Override
   public <T> T getProtocolData(Class<T> typeClass) {
      return messageReference.getProtocolData(typeClass);
   }

   @Override
   public <T> void setProtocolData(Class<T> typeClass, T data) {
      messageReference.setProtocolData(typeClass, data);
   }

   @Override
   public MessageReference copy(Queue queue) {
      return messageReference.copy(queue);
   }

   @Override
   public long getScheduledDeliveryTime() {
      return messageReference.getScheduledDeliveryTime();
   }

   @Override
   public void setScheduledDeliveryTime(long scheduledDeliveryTime) {
      messageReference.setScheduledDeliveryTime(scheduledDeliveryTime);
   }

   @Override
   public int getDeliveryCount() {
      return messageReference.getDeliveryCount();
   }

   @Override
   public void setDeliveryCount(int deliveryCount) {
      messageReference.setDeliveryCount(deliveryCount);
   }

   @Override
   public void setPersistedCount(int deliveryCount) {
      messageReference.setPersistedCount(deliveryCount);
   }

   @Override
   public int getPersistedCount() {
      return messageReference.getPersistedCount();
   }

   @Override
   public void incrementDeliveryCount() {
      messageReference.incrementDeliveryCount();
   }

   @Override
   public void decrementDeliveryCount() {
      messageReference.decrementDeliveryCount();
   }

   @Override
   public Queue getQueue() {
      return messageReference.getQueue();
   }

   @Override
   public void acknowledge() throws Exception {
      messageReference.acknowledge();
   }

   @Override
   public void acknowledge(Transaction tx) throws Exception {
      messageReference.acknowledge(tx);
   }

   @Override
   public void acknowledge(Transaction tx, ServerConsumer consumer) throws Exception {
      messageReference.acknowledge(tx, consumer);
   }

   @Override
   public void acknowledge(Transaction tx, AckReason reason, ServerConsumer consumer) throws Exception {
      messageReference.acknowledge(tx, reason, consumer);
   }

   @Override
   public void acknowledge(Transaction tx, AckReason reason, ServerConsumer consumer, boolean delivering) throws Exception {
      messageReference.acknowledge(tx, reason, consumer, delivering);
   }

   @Override
   public void emptyConsumerID() {
      messageReference.emptyConsumerID();
   }

   @Override
   public void setConsumerId(long consumerID) {
      messageReference.setConsumerId(consumerID);
   }

   @Override
   public boolean hasConsumerId() {
      return messageReference.hasConsumerId();
   }

   @Override
   public long getConsumerId() {
      return messageReference.getConsumerId();
   }

   @Override
   public void handled() {
      messageReference.handled();
   }

   @Override
   public void setInDelivery(boolean inDelivery) {
      messageReference.setInDelivery(inDelivery);
   }

   @Override
   public boolean isInDelivery() {
      return messageReference.isInDelivery();
   }

   @Override
   public void setAlreadyAcked() {
      messageReference.setAlreadyAcked();
   }

   @Override
   public boolean isAlreadyAcked() {
      return messageReference.isAlreadyAcked();
   }

   @Override
   public long getPersistentSize() throws ActiveMQException {
      return messageReference.getPersistentSize();
   }

   @Override
   public long getSequence() {
      return messageReference.getSequence();
   }

   @Override
   public void setSequence(long nextSequence) {
      messageReference.setSequence(nextSequence);
   }

}
