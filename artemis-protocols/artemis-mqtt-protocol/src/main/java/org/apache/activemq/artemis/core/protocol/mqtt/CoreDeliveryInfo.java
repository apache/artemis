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
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * Tracks volatile, in-memory state for an in-flight MQTT delivery: the consumer that originated it and the
 * {@link PacketIdCorrelationKey} that identifies the underlying core message and address. This information is held only
 * for the lifetime of the connection and is discarded on disconnect, whereas the {@link PacketIdCorrelationKey} mapping
 * is persisted in the journal so that packet IDs can be correlated across reconnects.
 */
public class CoreDeliveryInfo {
   private long consumerId;
   private PacketIdCorrelationKey packetIdCorrelationKey;

   public static CoreDeliveryInfo of(long consumerId, PacketIdCorrelationKey packetIdCorrelationKey) {
      return new CoreDeliveryInfo(consumerId, packetIdCorrelationKey);
   }

   private CoreDeliveryInfo(long consumerId, PacketIdCorrelationKey packetIdCorrelationKey) {
      this.consumerId = consumerId;
      this.packetIdCorrelationKey = packetIdCorrelationKey;
   }

   public long getConsumerId() {
      return consumerId;
   }

   public PacketIdCorrelationKey getPacketIdCorrelationKey() {
      return packetIdCorrelationKey;
   }

   public long getCoreMessageId() {
      return packetIdCorrelationKey.getCoreMessageId();
   }

   public SimpleString getAddress() {
      return packetIdCorrelationKey.getAddress();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof CoreDeliveryInfo other)) {
         return false;
      }
      return consumerId == other.consumerId &&
         Objects.equals(packetIdCorrelationKey, other.packetIdCorrelationKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(consumerId, packetIdCorrelationKey);
   }

   @Override
   public String toString() {
      return "CoreDeliveryInfo[" + "consumerId=" + consumerId + ", packetIdCorrelationKey=" + packetIdCorrelationKey + "]";
   }
}
