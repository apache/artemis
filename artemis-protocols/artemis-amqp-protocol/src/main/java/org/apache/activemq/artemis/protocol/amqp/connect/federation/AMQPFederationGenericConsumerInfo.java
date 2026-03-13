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

package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import java.util.Objects;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.utils.CompositeAddress;

/**
 * Information and identification class for Federation consumers created to federate queues and addresses. Instances of
 * this class should be usable in Collections classes where equality and hashing support is needed.
 */
public class AMQPFederationGenericConsumerInfo implements FederationConsumerInfo {

   public static final String FEDERATED_QUEUE_PREFIX = "federated";
   public static final String QUEUE_NAME_FORMAT_STRING = "${address}::${routeType}";

   private final Role role;
   private final String sourceAddress;
   private final String sourceFqqn;
   private final String targetAddress;
   private final String targetFqqn;
   private final String queueName;
   private final RoutingType routingType;
   private final String filterString;
   private final int priority;
   private final String id;

   public AMQPFederationGenericConsumerInfo(Role role, String sourceAddress, String targetAddress, String queueName,
                                            RoutingType routingType, String filterString, int priority) {
      this.role = role;
      this.sourceAddress = sourceAddress;
      this.targetAddress = targetAddress;
      this.queueName = queueName;
      this.routingType = routingType;
      this.filterString = filterString;
      this.sourceFqqn = CompositeAddress.toFullyQualified(sourceAddress, queueName);
      this.targetFqqn = CompositeAddress.toFullyQualified(targetAddress, queueName);
      this.priority = priority;
      this.id = UUID.randomUUID().toString();
   }

   @Override
   public String getId() {
      return id;
   }

   @Override
   public Role getRole() {
      return role;
   }

   @Override
   public String getQueueName() {
      return queueName;
   }

   @Override
   public String getSourceAddress() {
      return sourceAddress;
   }

   @Override
   public String getTargetAddress() {
      return targetAddress;
   }

   @Override
   public String getSourceFqqn() {
      return sourceFqqn;
   }

   @Override
   public String getTargetFqqn() {
      return targetFqqn;
   }

   @Override
   public RoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public String getFilterString() {
      return filterString;
   }

   @Override
   public int getPriority() {
      return priority;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPFederationGenericConsumerInfo other)) {
         return false;
      }

      return role == other.role &&
             priority == other.priority &&
             Objects.equals(sourceAddress, other.sourceAddress) &&
             Objects.equals(targetAddress, other.targetAddress) &&
             Objects.equals(queueName, other.queueName) &&
             routingType == other.routingType &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(sourceFqqn, other.sourceFqqn) &&
             Objects.equals(targetFqqn, other.targetFqqn);
   }

   @Override
   public int hashCode() {
      return Objects.hash(role, sourceAddress, targetAddress, queueName, routingType, filterString, sourceFqqn, targetFqqn, priority);
   }

   @Override
   public String toString() {
      return "FederationConsumerInfo: { " + getRole() + ", " + getSourceFqqn() + "}";
   }
}
