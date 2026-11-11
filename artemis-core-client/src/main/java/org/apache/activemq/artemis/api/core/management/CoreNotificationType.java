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
package org.apache.activemq.artemis.api.core.management;

/**
 * This enum defines all core notification types
 */
public enum CoreNotificationType implements NotificationType {
   BINDING_ADDED(0),
   BINDING_REMOVED(1),
   CONSUMER_CREATED(2),
   CONSUMER_CLOSED(3),
   SECURITY_AUTHENTICATION_VIOLATION(6),
   SECURITY_PERMISSION_VIOLATION(7),
   DISCOVERY_GROUP_STARTED(8),
   DISCOVERY_GROUP_STOPPED(9),
   BROADCAST_GROUP_STARTED(10),
   BROADCAST_GROUP_STOPPED(11),
   BRIDGE_STARTED(12),
   BRIDGE_STOPPED(13),
   CLUSTER_CONNECTION_STARTED(14),
   CLUSTER_CONNECTION_STOPPED(15),
   ACCEPTOR_STARTED(16),
   ACCEPTOR_STOPPED(17),
   PROPOSAL(18),
   PROPOSAL_RESPONSE(19),
   UNPROPOSAL(20),
   CONSUMER_SLOW(21),
   ADDRESS_ADDED(22),
   ADDRESS_REMOVED(23),
   CONNECTION_CREATED(24),
   CONNECTION_DESTROYED(25),
   SESSION_CREATED(26),
   SESSION_CLOSED(27),
   MESSAGE_DELIVERED(28),
   MESSAGE_EXPIRED(29),
   BINDING_UPDATED(30);

   private final int value;

   CoreNotificationType(final int value) {
      this.value = value;
   }

   @Override
   public int getType() {
      return value;
   }
}
