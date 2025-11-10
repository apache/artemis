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

import org.apache.activemq.artemis.core.management.impl.view.ConsumerField;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerConsumer;

public class ConsumerPredicateFilterPart extends PredicateFilterPart<ServerConsumer> {

   private final ActiveMQServer server;

   private ConsumerField f;

   public ConsumerPredicateFilterPart(ActiveMQServer server, String field, String operation, String value) {
      super(operation, value);
      this.server = server;
      if (field != null && !field.isEmpty()) {
         this.f = ConsumerField.valueOfName(field);

         //for backward compatibility
         if (this.f == null) {
            this.f = ConsumerField.valueOf(field);
         }
      }
   }

   @Override
   public boolean filterPart(ServerConsumer consumer) throws Exception {
      return switch (f) {
         case ID -> matchesLong(consumer.getSequentialID());
         case SESSION -> matches(consumer.getSessionID());
         case USER -> matches(server.getSessionByID(consumer.getSessionID()).getUsername());
         case VALIDATED_USER -> matches(server.getSessionByID(consumer.getSessionID()).getValidatedUser());
         case ADDRESS -> matches(consumer.getQueue().getAddress());
         case QUEUE -> matches(consumer.getQueue().getName());
         case FILTER -> matches(consumer.getFilterString());
         case PROTOCOL -> matches(server.getSessionByID(consumer.getSessionID()).getRemotingConnection().getProtocolName());
         case CLIENT_ID -> matches(server.getSessionByID(consumer.getSessionID()).getRemotingConnection().getClientID());
         case LOCAL_ADDRESS -> matches(server.getSessionByID(consumer.getSessionID()).getRemotingConnection().getTransportConnection().getLocalAddress());
         case REMOTE_ADDRESS -> matches(server.getSessionByID(consumer.getSessionID()).getRemotingConnection().getTransportConnection().getRemoteAddress());
         case MESSAGES_IN_TRANSIT -> matchesLong(consumer.getMessagesInTransit());
         case MESSAGES_IN_TRANSIT_SIZE -> matchesLong(consumer.getMessagesInTransitSize());
         case MESSAGES_DELIVERED -> matches(consumer.getDeliveringMessages());
         case MESSAGES_DELIVERED_SIZE -> matchesLong(consumer.getMessagesDeliveredSize());
         case MESSAGES_ACKNOWLEDGED -> matchesLong(consumer.getMessagesAcknowledged());
         case MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT -> matchesLong(consumer.getMessagesAcknowledgedAwaitingCommit());
         default -> true;
      };
   }
}
