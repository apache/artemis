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

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.utils.DataConstants;

public class SessionReceiveMessage extends MessagePacket {

   protected long consumerID;

   protected int deliveryCount;

   public SessionReceiveMessage(final long consumerID, final ICoreMessage message, final int deliveryCount) {
      super(SESS_RECEIVE_MSG, message);

      this.consumerID = consumerID;

      this.deliveryCount = deliveryCount;
   }

   public SessionReceiveMessage(final CoreMessage message) {
      super(SESS_RECEIVE_MSG, message);
   }

   public long getConsumerID() {
      return consumerID;
   }

   public int getDeliveryCount() {
      return deliveryCount;
   }


   @Override
   public int expectedEncodeSize() {
      return message.getEncodeSize() + PACKET_HEADERS_SIZE + DataConstants.SIZE_LONG + DataConstants.SIZE_INT;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      message.sendBuffer(buffer.byteBuf(), deliveryCount);
      buffer.writeLong(consumerID);
      buffer.writeInt(deliveryCount);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      // Buffer comes in after having read standard headers and positioned at Beginning of body part

      receiveMessage(copyMessageBuffer(buffer.byteBuf(), DataConstants.SIZE_LONG + DataConstants.SIZE_INT));

      buffer.readerIndex(buffer.capacity() - DataConstants.SIZE_LONG - DataConstants.SIZE_INT);
      this.consumerID = buffer.readLong();
      this.deliveryCount = buffer.readInt();
   }

   protected void receiveMessage(ByteBuf buffer) {
      message.receiveBuffer(buffer);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), consumerID, deliveryCount);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", consumerID=" + consumerID);
      sb.append(", deliveryCount=" + deliveryCount);
      sb.append("]");
      return sb.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionReceiveMessage other)) {
         return false;
      }

      return consumerID == other.consumerID &&
             deliveryCount == other.deliveryCount;
   }
}
