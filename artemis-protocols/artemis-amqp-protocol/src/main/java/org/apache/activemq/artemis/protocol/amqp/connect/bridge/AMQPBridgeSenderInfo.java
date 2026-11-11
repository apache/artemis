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
import org.apache.activemq.artemis.utils.CompositeAddress;

/**
 * Information and identification interface for AMQP bridge senders that will be
 * created on the remote peer as demand on the local broker is detected. The behavior
 * and meaning of some APIs in this interface may vary slightly depending on the role
 * of the sender (Address or Queue).
 */
public class AMQPBridgeSenderInfo {

   enum Role {
      /**
       * Sender created from a match on a configured bridge to address policy.
       */
      ADDRESS_SENDER,

      /**
       * Sender created from a match on a configured bridge to queue policy.
       */
      QUEUE_SENDER
   }

   private final Role role;
   private final String localAddress;
   private final String localQueue;
   private final String localFqqn;
   private final String remoteAddress;
   private final RoutingType routingType;
   private final String id;

   public AMQPBridgeSenderInfo(Role role, String localAddress, String localQueue, RoutingType routingType, String remoteAddress) {
      this.role = role;
      this.localAddress = localAddress;
      this.localQueue = localQueue;
      if (role == Role.QUEUE_SENDER) {
         localFqqn = CompositeAddress.toFullyQualified(localAddress, localQueue).toString();
      } else {
         localFqqn = null;
      }
      this.routingType = routingType;
      this.remoteAddress = remoteAddress;
      this.id = UUID.randomUUID().toString();
   }

   /**
    * {@return a unique Id for the sender being represented}
    */
   public String getId() {
      return id;
   }

   /**
    * {@return the role assigned to this sender instance}
    */
   public Role getRole() {
      return role;
   }

   /**
    * {@return the remote address used to populate the address in the Target address of the AMQP sender}
    */
   public String getRemoteAddress() {
      return remoteAddress;
   }

   /**
    * {@return local address that the remote sender was created for}
    */
   public String getLocalAddress() {
      return localAddress;
   }

   /**
    * {@return local queue that the remote sender was created for or a local queue used to generate demand}
    */
   public String getLocalQueue() {
      return localQueue;
   }

   /**
    * {@return the local FQQN if describing a Queue sender or null for an address sender}
    */
   public String getLocalFqqn() {
      return localFqqn;
   }

   /**
    * {@return the routing type of the local resource this sender bridges to}
    */
   public RoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public int hashCode() {
      return Objects.hash(localAddress, localFqqn, localQueue, remoteAddress, role, routingType);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AMQPBridgeSenderInfo other)) {
         return false;
      }

      return Objects.equals(localAddress, other.localAddress) &&
             Objects.equals(localFqqn, other.localFqqn) &&
             Objects.equals(localQueue, other.localQueue) &&
             Objects.equals(remoteAddress, other.remoteAddress) &&
             role == other.role &&
             routingType == other.routingType;
   }

   @Override
   public String toString() {
      return "AMQPBridgeSenderInfo: { " + getRole() + ", " + getRemoteAddress() + " }";
   }
}
