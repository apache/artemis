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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.management.impl.view.QueueField;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.Queue;

public class QueuePredicateFilterPart extends PredicateFilterPart<QueueControl> {
   private final ActiveMQServer server;
   private QueueField f;

   public QueuePredicateFilterPart(ActiveMQServer server, String field, String operation, String value) {
      super(operation, value);
      this.server = server;
      if (field != null && !field.isEmpty()) {
         f = QueueField.valueOfName(field);

         //for backward compatibility
         if (f == null) {
            f = QueueField.valueOf(field);
         }
      }
   }

   @Override
   public boolean filterPart(QueueControl queue) {
      return switch (f) {
         case ID -> matchesLong(queue.getID());
         case NAME -> matches(queue.getName());
         case CONSUMER_ID -> {
            Queue q = server.locateQueue(SimpleString.of(queue.getName()));
            for (Consumer consumer : q.getConsumers()) {
               if (matchesLong(consumer.sequentialID()))
                  yield true;
            }
            yield false;
         }
         case MAX_CONSUMERS -> matchesInt(queue.getMaxConsumers());
         case ADDRESS -> matches(queue.getAddress());
         case FILTER -> matches(queue.getFilter());
         case MESSAGE_COUNT -> matchesLong(queue.getMessageCount());
         case CONSUMER_COUNT -> matchesInt(queue.getConsumerCount());
         case DELIVERING_COUNT -> matchesInt(queue.getDeliveringCount());
         case MESSAGES_ADDED -> matchesLong(queue.getMessagesAdded());
         case MESSAGES_ACKED -> matchesLong(queue.getMessagesAcknowledged());
         case MESSAGES_EXPIRED -> matchesLong(queue.getMessagesExpired());
         case ROUTING_TYPE -> matches(queue.getRoutingType());
         case AUTO_CREATED -> matches(server.locateQueue(SimpleString.of(queue.getName())).isAutoCreated());
         case DURABLE -> matches(queue.isDurable());
         case PAUSED -> matches(queue.isPaused());
         case TEMPORARY -> matches(queue.isTemporary());
         case PURGE_ON_NO_CONSUMERS -> matches(queue.isPurgeOnNoConsumers());
         case MESSAGES_KILLED -> matchesLong(queue.getMessagesKilled());
         case EXCLUSIVE -> matches(queue.isExclusive());
         case LAST_VALUE -> matches(queue.isLastValue());
         case SCHEDULED_COUNT -> matchesLong(queue.getScheduledCount());
         case USER -> matches(queue.getUser());
         case INTERNAL_QUEUE -> matches(queue.isInternalQueue());
         default -> true;
      };
   }
}
