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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * A SessionSendContinuationMessage<br>
 */
public class SessionSendContinuationMessage extends SessionContinuationMessage {

   protected boolean requiresResponse;

   // Used on confirmation handling
   protected Message message;
   /**
    * In case, we are using a different handler than the one set on the {@link org.apache.activemq.artemis.api.core.client.ClientSession}
    * <br>
    * This field is only used at the client side.
    *
    * @see org.apache.activemq.artemis.api.core.client.ClientSession#setSendAcknowledgementHandler(SendAcknowledgementHandler)
    * @see org.apache.activemq.artemis.api.core.client.ClientProducer#send(org.apache.activemq.artemis.api.core.SimpleString, org.apache.activemq.artemis.api.core.Message, SendAcknowledgementHandler)
    */
   private final transient SendAcknowledgementHandler handler;

   /**
    * to be sent on the last package
    */
   protected long messageBodySize = -1;



   public SessionSendContinuationMessage() {
      super(SESS_SEND_CONTINUATION);
      handler = null;
   }

   protected SessionSendContinuationMessage(byte type) {
      super(type);
      handler = null;
   }

   /**
    * @param body
    * @param continues
    * @param requiresResponse
    */
   public SessionSendContinuationMessage(final Message message,
                                         final byte[] body,
                                         final boolean continues,
                                         final boolean requiresResponse,
                                         final long messageBodySize,
                                         SendAcknowledgementHandler handler) {
      super(SESS_SEND_CONTINUATION, body, continues);
      this.requiresResponse = requiresResponse;
      this.message = message;
      this.handler = handler;
      this.messageBodySize = messageBodySize;
   }

   /**
    * @param body
    * @param continues
    * @param requiresResponse
    */
   protected SessionSendContinuationMessage(final byte type,
                                         final Message message,
                                         final byte[] body,
                                         final boolean continues,
                                         final boolean requiresResponse,
                                         final long messageBodySize,
                                         SendAcknowledgementHandler handler) {
      super(type, body, continues);
      this.requiresResponse = requiresResponse;
      this.message = message;
      this.handler = handler;
      this.messageBodySize = messageBodySize;
   }

   /**
    * @return the requiresResponse
    */
   @Override
   public boolean isRequiresResponse() {
      return requiresResponse;
   }

   public long getMessageBodySize() {
      return messageBodySize;
   }

   /**
    * @return the message
    */
   public Message getMessage() {
      return message;
   }

   @Override
   public int expectedEncodeSize() {
      return super.expectedEncodeSize() + (!continues ? DataConstants.SIZE_LONG : 0) + DataConstants.SIZE_BOOLEAN;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      if (!continues) {
         buffer.writeLong(messageBodySize);
      }
      buffer.writeBoolean(requiresResponse);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      if (!continues) {
         messageBodySize = buffer.readLong();
      }
      requiresResponse = buffer.readBoolean();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + ((message == null) ? 0 : message.hashCode());
      result = prime * result + (int) (messageBodySize ^ (messageBodySize >>> 32));
      result = prime * result + (requiresResponse ? 1231 : 1237);
      return result;
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", continues=" + continues);
      buff.append(", message=" + message);
      buff.append(", messageBodySize=" + messageBodySize);
      buff.append(", requiresResponse=" + requiresResponse);
      return buff.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (!super.equals(obj))
         return false;
      if (!(obj instanceof SessionSendContinuationMessage))
         return false;
      SessionSendContinuationMessage other = (SessionSendContinuationMessage) obj;
      if (message == null) {
         if (other.message != null)
            return false;
      } else if (!message.equals(other.message))
         return false;
      if (messageBodySize != other.messageBodySize)
         return false;
      if (requiresResponse != other.requiresResponse)
         return false;
      return true;
   }

   public SendAcknowledgementHandler getHandler() {
      return handler;
   }
}
