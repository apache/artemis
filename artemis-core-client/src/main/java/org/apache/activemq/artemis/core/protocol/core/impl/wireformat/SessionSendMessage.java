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
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.utils.DataConstants;

public class SessionSendMessage extends MessagePacket {

   protected boolean requiresResponse;

   /**
    * In case, we are using a different handler than the one set on the
    * {@link org.apache.activemq.artemis.api.core.client.ClientSession}
    * <p>
    * This field is only used at the client side.
    *
    * @see org.apache.activemq.artemis.api.core.client.ClientSession#setSendAcknowledgementHandler(SendAcknowledgementHandler)
    * @see org.apache.activemq.artemis.api.core.client.ClientProducer#send(org.apache.activemq.artemis.api.core.SimpleString,
    * org.apache.activemq.artemis.api.core.Message, SendAcknowledgementHandler)
    */
   private final transient SendAcknowledgementHandler handler;

   /**
    * This will be using the CoreMessage because it is meant for the core-protocol
    */
   protected SessionSendMessage(final byte id,
                             final ICoreMessage message,
                             final boolean requiresResponse,
                             final SendAcknowledgementHandler handler) {
      super(id, message);
      this.handler = handler;
      this.requiresResponse = requiresResponse;
   }

   protected SessionSendMessage(final byte id,
                                final CoreMessage message) {
      super(id, message);
      this.handler = null;
   }

   /**
    * This will be using the CoreMessage because it is meant for the core-protocol
    */
   public SessionSendMessage(final ICoreMessage message,
                             final boolean requiresResponse,
                             final SendAcknowledgementHandler handler) {
      super(SESS_SEND, message);
      this.handler = handler;
      this.requiresResponse = requiresResponse;
   }

   public SessionSendMessage(final CoreMessage message) {
      super(SESS_SEND, message);
      this.handler = null;
   }

   @Override
   public boolean isRequiresResponse() {
      return requiresResponse;
   }

   public SendAcknowledgementHandler getHandler() {
      return handler;
   }

   @Override
   public int expectedEncodeSize() {
      return message.getEncodeSize() + PACKET_HEADERS_SIZE + fieldsEncodeSize();
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      message.sendBuffer(buffer.byteBuf(), 0);
      buffer.writeBoolean(requiresResponse);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      // Buffer comes in after having read standard headers and positioned at Beginning of body part

      ByteBuf messageBuffer = copyMessageBuffer(buffer.byteBuf(), fieldsEncodeSize());
      receiveMessage(messageBuffer);

      buffer.readerIndex(buffer.capacity() - fieldsEncodeSize());

      requiresResponse = buffer.readBoolean();
   }

   protected int fieldsEncodeSize() {
      return DataConstants.SIZE_BOOLEAN;
   }

   protected void receiveMessage(ByteBuf messageBuffer) {
      message.receiveBuffer(messageBuffer);
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(requiresResponse);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", requiresResponse=" + requiresResponse);
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
      if (!(obj instanceof SessionSendMessage other)) {
         return false;
      }

      return requiresResponse == other.requiresResponse;
   }

   public int getSenderID() {
      return -1;
   }
}
