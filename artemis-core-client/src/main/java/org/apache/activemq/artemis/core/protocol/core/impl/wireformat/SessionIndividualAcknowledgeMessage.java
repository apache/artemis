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
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class SessionIndividualAcknowledgeMessage extends PacketImpl {


   private long consumerID;

   private long messageID;

   private boolean requiresResponse;


   public SessionIndividualAcknowledgeMessage(final long consumerID,
                                              final long messageID,
                                              final boolean requiresResponse) {
      super(SESS_INDIVIDUAL_ACKNOWLEDGE);

      this.consumerID = consumerID;

      this.messageID = messageID;

      this.requiresResponse = requiresResponse;
   }

   public SessionIndividualAcknowledgeMessage() {
      super(SESS_INDIVIDUAL_ACKNOWLEDGE);
   }

   public long getConsumerID() {
      return consumerID;
   }

   public long getMessageID() {
      return messageID;
   }

   @Override
   public boolean isRequiresResponse() {
      return requiresResponse;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(consumerID);

      buffer.writeLong(messageID);

      buffer.writeBoolean(requiresResponse);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      consumerID = buffer.readLong();

      messageID = buffer.readLong();

      requiresResponse = buffer.readBoolean();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (int) (consumerID ^ (consumerID >>> 32));
      result = prime * result + (int) (messageID ^ (messageID >>> 32));
      result = prime * result + (requiresResponse ? 1231 : 1237);
      return result;
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", consumerID=" + consumerID);
      buff.append(", messageID=" + messageID);
      buff.append(", requiresResponse=" + requiresResponse);
      return buff.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (!super.equals(obj))
         return false;
      if (!(obj instanceof SessionIndividualAcknowledgeMessage))
         return false;
      SessionIndividualAcknowledgeMessage other = (SessionIndividualAcknowledgeMessage) obj;
      if (consumerID != other.consumerID)
         return false;
      if (messageID != other.messageID)
         return false;
      if (requiresResponse != other.requiresResponse)
         return false;
      return true;
   }
}
