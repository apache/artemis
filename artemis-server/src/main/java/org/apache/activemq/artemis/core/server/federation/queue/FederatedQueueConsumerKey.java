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
package org.apache.activemq.artemis.core.server.federation.queue;

import java.util.Objects;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.federation.FederatedConsumerKey;
import org.apache.activemq.artemis.utils.CompositeAddress;

public class FederatedQueueConsumerKey implements FederatedConsumerKey {
   private final SimpleString address;
   private final SimpleString queueName;
   private final RoutingType routingType;
   private final SimpleString queueFilterString;
   private final SimpleString filterString;
   private final SimpleString fqqn;
   private final int priority;

   FederatedQueueConsumerKey(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString queueFilterString, SimpleString filterString, int priority) {
      this.address = address;
      this.routingType = routingType;
      this.queueName = queueName;
      this.fqqn = CompositeAddress.toFullyQualified(address, queueName);
      this.filterString = filterString;
      this.queueFilterString = queueFilterString;
      this.priority = priority;
   }

   @Override
   public SimpleString getQueueName() {
      return queueName;
   }

   @Override
   public SimpleString getQueueFilterString() {
      return queueFilterString;
   }

   @Override
   public int getPriority() {
      return priority;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public SimpleString getFqqn() {
      return fqqn;
   }

   @Override
   public RoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public SimpleString getFilterString() {
      return filterString;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederatedQueueConsumerKey other)) {
         return false;
      }

      return priority == other.priority &&
             Objects.equals(address, other.address) &&
             Objects.equals(queueName, other.queueName) &&
             routingType == other.routingType &&
             Objects.equals(queueFilterString, other.queueFilterString) &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(fqqn, other.fqqn);
   }

   @Override
   public int hashCode() {
      return Objects.hash(address, queueName, routingType, queueFilterString, filterString, fqqn, priority);
   }
}
