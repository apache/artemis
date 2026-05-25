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

import java.util.Arrays;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public final class ReplicationLargeMessageWriteMessage extends PacketImpl {

   private long messageId;

   private byte[] body;

   public ReplicationLargeMessageWriteMessage() {
      super(PacketImpl.REPLICATION_LARGE_MESSAGE_WRITE);
   }

   public ReplicationLargeMessageWriteMessage(final long messageId, final byte[] body) {
      this();

      this.messageId = messageId;
      this.body = body;
   }

   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_LONG +  // buffer.writeLong(messageId);
         DataConstants.SIZE_LONG +  // buffer.writeLong(messageId);
         DataConstants.SIZE_INT +   // buffer.writeInt(body.length);
         body.length;               // buffer.writeBytes(body);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(messageId);
      buffer.writeInt(body.length);
      buffer.writeBytes(body);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      messageId = buffer.readLong();
      int size = buffer.readInt();
      body = new byte[size];
      buffer.readBytes(body);
   }

   public long getMessageId() {
      return messageId;
   }

   public byte[] getBody() {
      return body;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), Arrays.hashCode(body), messageId);
   }

   @Override
   protected String getPacketString() {
      return super.getPacketString() +
         "messageId=" + messageId +
         ", body.size=" + body.length;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationLargeMessageWriteMessage other)) {
         return false;
      }

      return Arrays.equals(body, other.body) &&
             messageId == other.messageId;
   }
}
