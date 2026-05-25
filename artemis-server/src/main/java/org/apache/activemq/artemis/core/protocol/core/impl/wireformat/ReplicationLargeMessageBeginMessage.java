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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public class ReplicationLargeMessageBeginMessage extends PacketImpl {

   long messageId;

   public ReplicationLargeMessageBeginMessage(final long messageId) {
      this();
      this.messageId = messageId;
   }

   public ReplicationLargeMessageBeginMessage() {
      super(PacketImpl.REPLICATION_LARGE_MESSAGE_BEGIN);
   }


   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_LONG; // buffer.writeLong(messageId);
   }


   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(messageId);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      messageId = buffer.readLong();
   }

   public long getMessageId() {
      return messageId;
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(messageId);
   }

   @Override
   protected String getPacketString() {
      return super.getPacketString() +
         "messageId=" + messageId;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationLargeMessageBeginMessage other)) {
         return false;
      }

      return messageId == other.messageId;
   }
}
