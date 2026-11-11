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
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class SessionReceiveLargeMessage extends PacketImpl implements MessagePacketI {

   private Message message;

   /**
    * Since we receive the message before the entire message was received
    */
   private long largeMessageSize;

   private long consumerID;

   private int deliveryCount;

   // To be used on decoding at the client while receiving a large message
   public SessionReceiveLargeMessage(final Message message) {
      super(SESS_RECEIVE_LARGE_MSG);
      this.message = message;
   }

   public SessionReceiveLargeMessage(final long consumerID,
                                     final Message message,
                                     final long largeMessageSize,
                                     final int deliveryCount) {
      super(SESS_RECEIVE_LARGE_MSG);

      this.consumerID = consumerID;

      this.message = message;

      this.deliveryCount = deliveryCount;

      this.largeMessageSize = largeMessageSize;
   }

   @Override
   public MessagePacketI replaceMessage(Message message) {
      this.message = message;
      return this;
   }

   public Message getLargeMessage() {
      return message;
   }

   @Override
   public Message getMessage() {
      return message;
   }

   public long getConsumerID() {
      return consumerID;
   }

   public int getDeliveryCount() {
      return deliveryCount;
   }

   public long getLargeMessageSize() {
      return largeMessageSize;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(consumerID);
      buffer.writeInt(deliveryCount);
      buffer.writeLong(largeMessageSize);
      if (message != null) {
         ((CoreMessage)message).encodeHeadersAndProperties(buffer.byteBuf());
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      consumerID = buffer.readLong();
      deliveryCount = buffer.readInt();
      largeMessageSize = buffer.readLong();
      ((CoreMessage)message).decodeHeadersAndProperties(buffer.byteBuf());
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), consumerID, deliveryCount, largeMessageSize, message);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", consumerID=" + consumerID);
      sb.append(", deliveryCount=" + deliveryCount);
      sb.append(", largeMessageSize=" + largeMessageSize);
      sb.append(", message=" + message);
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
      if (!(obj instanceof SessionReceiveLargeMessage other)) {
         return false;
      }

      return consumerID == other.consumerID &&
             deliveryCount == other.deliveryCount &&
             largeMessageSize == other.largeMessageSize &&
             Objects.equals(message, other.message);
   }
}
