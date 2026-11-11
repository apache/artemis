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
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.utils.DataConstants;

public class SessionSendContinuationMessage_V2 extends SessionSendContinuationMessage {

   private long correlationID;



   public SessionSendContinuationMessage_V2() {
      super();
   }

   public SessionSendContinuationMessage_V2(final Message message,
                                            final byte[] body,
                                            final boolean continues,
                                            final boolean requiresResponse,
                                            final long messageBodySize,
                                            SendAcknowledgementHandler handler) {
      super(message, body, continues, requiresResponse, messageBodySize, handler);
   }

   @Override
   public int expectedEncodeSize() {
      return super.expectedEncodeSize() + DataConstants.SIZE_LONG;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeLong(correlationID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      if (buffer.readableBytes() >= DataConstants.SIZE_LONG) {
         correlationID = buffer.readLong();
      } else {
         correlationID = -1;
      }
   }

   @Override
   public long getCorrelationID() {
      return this.correlationID;
   }

   @Override
   public void setCorrelationID(long correlationID) {
      this.correlationID = correlationID;
   }

   @Override
   public boolean isResponseAsync() {
      return true;
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(correlationID);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", continues=" + continues);
      sb.append(", message=" + message);
      sb.append(", messageBodySize=" + messageBodySize);
      sb.append(", requiresResponse=" + requiresResponse);
      sb.append(", correlationID=" + correlationID);
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
      if (!(obj instanceof SessionSendContinuationMessage_V2 other)) {
         return false;
      }

      return correlationID == other.correlationID;
   }
}
