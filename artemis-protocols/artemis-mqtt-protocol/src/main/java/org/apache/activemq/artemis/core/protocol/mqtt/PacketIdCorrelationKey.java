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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.collections.AbstractHashMapPersister;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * Composite key that maps a core message delivery to its MQTT packet ID. The MQTT specification requires that packet
 * IDs for in-flight QoS 1 and QoS 2 messages are unique per client session, and that the same packet ID is reused if
 * the message is redelivered (e.g. after a reconnect). This mapping is persisted in the journal so that a reconnecting
 * client receives the same packet ID it was originally assigned. The key includes both the core message ID and the
 * subscription address because overlapping subscriptions (e.g. {@code foo/bar} and {@code foo/#}) can cause the same
 * core message to be delivered to the same client more than once, each through a different subscription address and
 * with its own packet ID.
 */
public class PacketIdCorrelationKey {

   private static Persister persister = new Persister();

   public static Persister getPersister() {
      return persister;
   }

   private long coreMessageId;
   private SimpleString address;

   public static PacketIdCorrelationKey of(long coreMessageId, SimpleString address) {
      return new PacketIdCorrelationKey(coreMessageId, address);
   }

   private PacketIdCorrelationKey(long coreMessageId, SimpleString address) {
      this.coreMessageId = coreMessageId;
      this.address = address;
   }

   public long getCoreMessageId() {
      return coreMessageId;
   }

   public SimpleString getAddress() {
      return address;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof PacketIdCorrelationKey other)) {
         return false;
      }
      return coreMessageId == other.coreMessageId &&
         Objects.equals(address, other.address);
   }

   @Override
   public int hashCode() {
      return Objects.hash(coreMessageId, address);
   }

   @Override
   public String toString() {
      return "PacketIdCorrelation[" + "coreMessageId=" + coreMessageId + ", address=" + address + "]";
   }

   private static class Persister extends AbstractHashMapPersister<String, PacketIdCorrelationKey, Integer> {
      @Override
      protected int getCollectionIdSize(String collectionID) {
         return BufferHelper.sizeOfString(collectionID);
      }

      @Override
      protected void encodeCollectionId(ActiveMQBuffer buffer, String collectionID) {
         buffer.writeString(collectionID);
      }

      @Override
      protected String decodeCollectionId(ActiveMQBuffer buffer) {
         return buffer.readString();
      }

      @Override
      protected int getKeySize(PacketIdCorrelationKey packetIdCorrelationKey) {
         return DataConstants.SIZE_LONG + packetIdCorrelationKey.getAddress().sizeof();
      }

      @Override
      protected void encodeKey(ActiveMQBuffer buffer, PacketIdCorrelationKey packetIdCorrelationKey) {
         buffer.writeLong(packetIdCorrelationKey.getCoreMessageId());
         buffer.writeSimpleString(packetIdCorrelationKey.getAddress());
      }

      @Override
      protected PacketIdCorrelationKey decodeKey(ActiveMQBuffer buffer) {
         return PacketIdCorrelationKey.of(buffer.readLong(), buffer.readSimpleString());
      }

      @Override
      protected int getValueSize(Integer mqttPacketId) {
         return DataConstants.SIZE_INT;
      }

      @Override
      protected void encodeValue(ActiveMQBuffer buffer, Integer mqttPacketId) {
         buffer.writeInt(mqttPacketId);
      }

      @Override
      protected Integer decodeValue(ActiveMQBuffer buffer, PacketIdCorrelationKey coreMessageId) {
         return buffer.readInt();
      }
   }
}
