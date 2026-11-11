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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.util.Objects;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;

/**
 * Information and identification interface for AMQP bridgeManager receivers that will be
 * created on the remote peer as demand on the local broker is detected. The behavior
 * and meaning of some APIs in this interface may vary slightly depending on the role
 * of the receiver (Address or Queue).
 */
public class AMQPBridgeReceiverInfo {

   enum ReceiverRole {
      /**
       * Receiver created from a match on a configured bridgeManager from address policy.
       */
      ADDRESS_RECEIVER,

      /**
       * Receiver created from a match on a configured bridgeManager from queue policy.
       */
      QUEUE_RECEIVER
   }

   private final ReceiverRole role;
   private final String localAddress;
   private final String localQueue;
   private final String localFqqn;
   private final String remoteAddress;
   private final RoutingType routingType;
   private final String filterString;
   private final String id;
   private final Integer priority;

   public AMQPBridgeReceiverInfo(ReceiverRole role, String localAddress, String localQueue, RoutingType routingType,
                                 String remoteAddress, String filterString, Integer priority) {
      this.role = role;
      this.localAddress = localAddress;
      this.localQueue = localQueue;
      this.routingType = routingType;
      if (role == ReceiverRole.QUEUE_RECEIVER) {
         localFqqn = localAddress + "::" + localQueue;
      } else {
         localFqqn = null;
      }
      this.remoteAddress = remoteAddress;
      this.filterString = filterString;
      this.priority = priority;
      this.id = UUID.randomUUID().toString();
   }

   /**
    * {@return a unique Id for the receiver being represented}
    */
   public String getId() {
      return id;
   }

   /**
    * {@return the role assigned to this receiver instance}
    */
   public ReceiverRole getRole() {
      return role;
   }

   /**
    * {@return the remote address used to populate the address in the Source address of the AMQP receiver}
    */
   public String getRemoteAddress() {
      return remoteAddress;
   }

   /**
    * {@return local address that the remote receiver was created for}
    */
   public String getLocalAddress() {
      return localAddress;
   }

   /**
    * {@return the local queue that the remote receiver was created for (null for address receiver)}
    */
   public String getLocalQueue() {
      return localQueue;
   }

   /**
    * {@return the local FQQN if describing a Queue receiver otherwise null for an Address receiver}
    */
   public String getLocalFqqn() {
      return localFqqn;
   }

   /**
    * {@return the routing type of the local resource this receiver bridges to}
    */
   public RoutingType getRoutingType() {
      return routingType;
   }

   /**
    * {@return the filter string that should be set in the remote receiver configuration}
    */
   public String getFilterString() {
      return filterString;
   }

   /**
    * {@return the priority value to assign to the remote receiver configuration (null means no priority)}
    */
   public Integer getPriority() {
      return priority;
   }

   @Override
   public int hashCode() {
      return Objects.hash(filterString, localAddress, localFqqn, localQueue, remoteAddress, role, routingType);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPBridgeReceiverInfo other)) {
         return false;
      }

      return Objects.equals(filterString, other.filterString) &&
             Objects.equals(localAddress, other.localAddress) &&
             Objects.equals(localFqqn, other.localFqqn) &&
             Objects.equals(localQueue, other.localQueue) &&
             Objects.equals(remoteAddress, other.remoteAddress) &&
             Objects.equals(role, other.role) &&
             Objects.equals(routingType, other.routingType);
   }

   @Override
   public String toString() {
      return "AMQPBridgeReceiverInfo: { " + getLocalAddress() + ", " +
                                            getLocalQueue() + ", " +
                                            getLocalFqqn() + ", " +
                                            getRoutingType() + ", " +
                                            getRole() + ", " +
                                            getRemoteAddress() + ", " +
                                            getPriority() + ", " +
                                            getFilterString() + " }";
   }
}
