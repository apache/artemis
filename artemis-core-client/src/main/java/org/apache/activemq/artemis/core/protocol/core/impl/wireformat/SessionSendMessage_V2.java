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
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.utils.DataConstants;

public class SessionSendMessage_V2 extends SessionSendMessage {

   private long correlationID;

   /**
    * This will be using the CoreMessage because it is meant for the core-protocol
    */
   public SessionSendMessage_V2(final ICoreMessage message,
                                final boolean requiresResponse,
                                final SendAcknowledgementHandler handler) {
      super(SESS_SEND, message, requiresResponse, handler);
   }

   public SessionSendMessage_V2(final CoreMessage message) {
      super(SESS_SEND, message);
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeLong(correlationID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      correlationID = buffer.readLong();
   }

   @Override
   protected int fieldsEncodeSize() {
      return super.fieldsEncodeSize() + DataConstants.SIZE_LONG;
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
      sb.append(", correlationID=" + correlationID);
      sb.append(", requiresResponse=" + super.isRequiresResponse());
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
      if (!(obj instanceof SessionSendMessage_V2 other)) {
         return false;
      }

      return correlationID == other.correlationID;
   }
}
