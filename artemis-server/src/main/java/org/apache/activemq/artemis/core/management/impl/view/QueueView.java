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
package org.apache.activemq.artemis.core.management.impl.view;

import org.apache.activemq.artemis.core.management.impl.view.predicate.QueuePredicateFilterPart;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.management.impl.view.predicate.QueueFilterPredicate;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.JsonLoader;

public class QueueView extends ActiveMQAbstractView<QueueControl, QueuePredicateFilterPart> {

   private static final String defaultSortField = QueueField.NAME.getName();

   private ActiveMQServer server;

   public QueueView(ActiveMQServer server) {
      super();
      this.predicate = new QueueFilterPredicate(server);
      this.server = server;
   }

   @Override
   public Class getClassT() {
      return QueueControl.class;
   }

   @Override
   public JsonObjectBuilder toJson(QueueControl queue) {
      Queue q = server.locateQueue(SimpleString.of(queue.getName()));
      JsonObjectBuilder obj = JsonLoader.createObjectBuilder()
         .add(QueueField.ID.getName(), toString(queue.getID()))
         .add(QueueField.NAME.getName(), toString(queue.getName()))
         .add(QueueField.ADDRESS.getName(), toString(queue.getAddress()))
         .add(QueueField.FILTER.getName(), toString(queue.getFilter()))
         .add(QueueField.DURABLE.getName(), toString(queue.isDurable()))
         .add(QueueField.PAUSED.getName(), toString(queue.isPaused()))
         .add(QueueField.PERSISTEDPAUSE.getName(), toString(queue.isPersistedPause()))
         .add(QueueField.TEMPORARY.getName(), toString(queue.isTemporary()))
         .add(QueueField.PURGE_ON_NO_CONSUMERS.getName(), toString(queue.isPurgeOnNoConsumers()))
         .add(QueueField.CONSUMER_COUNT.getName(), toString(queue.getConsumerCount()))
         .add(QueueField.MAX_CONSUMERS.getName(), toString(queue.getMaxConsumers()))
         .add(QueueField.AUTO_CREATED.getName(), toString(q.isAutoCreated()))
         .add(QueueField.USER.getName(), toString(q.getUser()))
         .add(QueueField.ROUTING_TYPE.getName(), toString(queue.getRoutingType()))
         .add(QueueField.MESSAGES_ADDED.getName(), toString(queue.getMessagesAdded()))
         .add(QueueField.MESSAGE_COUNT.getName(), toString(queue.getMessageCount()))
         .add(QueueField.MESSAGES_ACKED.getName(), toString(queue.getMessagesAcknowledged()))
         .add(QueueField.MESSAGES_EXPIRED.getName(), toString(queue.getMessagesExpired()))
         .add(QueueField.DELIVERING_COUNT.getName(), toString(queue.getDeliveringCount()))
         .add(QueueField.MESSAGES_KILLED.getName(), toString(queue.getMessagesKilled()))
         .add(QueueField.DIRECT_DELIVER.getName(), toString(q.isDirectDeliver()))
         .add(QueueField.EXCLUSIVE.getName(), toString(queue.isExclusive()))
         .add(QueueField.LAST_VALUE.getName(), toString(queue.isLastValue()))
         .add(QueueField.LAST_VALUE_KEY.getName(), toString(queue.getLastValueKey()))
         .add(QueueField.SCHEDULED_COUNT.getName(), toString(queue.getScheduledCount()))
         .add(QueueField.GROUP_REBALANCE.getName(), toString(queue.isGroupRebalance()))
         .add(QueueField.GROUP_REBALANCE_PAUSE_DISPATCH.getName(), toString(queue.isGroupRebalancePauseDispatch()))
         .add(QueueField.GROUP_BUCKETS.getName(), toString(queue.getGroupBuckets()))
         .add(QueueField.GROUP_FIRST_KEY.getName(), toString(queue.getGroupFirstKey()))
         .add(QueueField.ENABLED.getName(), toString(queue.isEnabled()))
         .add(QueueField.RING_SIZE.getName(), toString(queue.getRingSize()))
         .add(QueueField.CONSUMERS_BEFORE_DISPATCH.getName(), toString(queue.getConsumersBeforeDispatch()))
         .add(QueueField.DELAY_BEFORE_DISPATCH.getName(), toString(queue.getDelayBeforeDispatch()))
         .add(QueueField.AUTO_DELETE.getName(), toString(q.isAutoDelete()))
         .add(QueueField.INTERNAL_QUEUE.getName(), toString(q.isInternalQueue()));
      return obj;
   }

   @Override
   public Object getField(QueueControl queue, String fieldName) {
      Queue q = server.locateQueue(SimpleString.of(queue.getName()));

      QueueField field = QueueField.valueOfName(fieldName);

      return switch (field) {
         case ID -> queue.getID();
         case NAME -> queue.getName();
         case ADDRESS -> queue.getAddress();
         case FILTER -> queue.getFilter();
         case DURABLE -> queue.isDurable();
         case PAUSED -> queue.isPaused();
         case PERSISTEDPAUSE -> queue.isPersistedPause();
         case TEMPORARY -> queue.isTemporary();
         case PURGE_ON_NO_CONSUMERS -> queue.isPurgeOnNoConsumers();
         case CONSUMER_COUNT -> queue.getConsumerCount();
         case MAX_CONSUMERS -> queue.getMaxConsumers();
         case AUTO_CREATED -> q.isAutoCreated();
         case USER -> q.getUser();
         case ROUTING_TYPE -> queue.getRoutingType();
         case MESSAGES_ADDED -> queue.getMessagesAdded();
         case MESSAGE_COUNT -> queue.getMessageCount();
         case MESSAGES_ACKED -> queue.getMessagesAcknowledged();
         case MESSAGES_EXPIRED -> queue.getMessagesExpired();
         case DELIVERING_COUNT -> queue.getDeliveringCount();
         case MESSAGES_KILLED -> queue.getMessagesKilled();
         case DIRECT_DELIVER -> q.isDirectDeliver();
         case EXCLUSIVE -> q.isExclusive();
         case LAST_VALUE -> q.isLastValue();
         case LAST_VALUE_KEY -> q.getLastValueKey();
         case SCHEDULED_COUNT -> q.getScheduledCount();
         case GROUP_REBALANCE -> queue.isGroupRebalance();
         case GROUP_REBALANCE_PAUSE_DISPATCH -> queue.isGroupRebalancePauseDispatch();
         case GROUP_BUCKETS -> queue.getGroupBuckets();
         case GROUP_FIRST_KEY -> queue.getGroupFirstKey();
         case ENABLED -> q.isEnabled();
         case RING_SIZE -> q.getRingSize();
         case CONSUMERS_BEFORE_DISPATCH -> q.getConsumersBeforeDispatch();
         case DELAY_BEFORE_DISPATCH -> q.getDelayBeforeDispatch();
         case INTERNAL_QUEUE -> q.isInternalQueue();
         default -> throw new IllegalArgumentException("Unsupported field, " + fieldName);
      };
   }

   @Override
   public String getDefaultOrderColumn() {
      return defaultSortField;
   }
}
