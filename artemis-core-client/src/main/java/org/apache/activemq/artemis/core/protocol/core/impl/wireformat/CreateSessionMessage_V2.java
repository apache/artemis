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

public class CreateSessionMessage_V2 extends CreateSessionMessage {

   private String clientID = null;

   public CreateSessionMessage_V2(final String name,
                               final long sessionChannelID,
                               final int version,
                               final String username,
                               final String password,
                               final int minLargeMessageSize,
                               final boolean xa,
                               final boolean autoCommitSends,
                               final boolean autoCommitAcks,
                               final boolean preAcknowledge,
                               final int windowSize,
                               final String defaultAddress,
                               final String clientID) {
      super(CREATESESSION_V2, name, sessionChannelID, version, username, password, minLargeMessageSize, xa, autoCommitSends, autoCommitAcks, preAcknowledge, windowSize, defaultAddress);

      this.clientID = clientID;
   }

   public CreateSessionMessage_V2() {
      super(CREATESESSION_V2);
   }


   public String getClientID() {
      return clientID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);

      buffer.writeNullableString(clientID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);

      clientID = buffer.readNullableString();
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", metadata=" + clientID);
      return buff.toString();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + ((clientID == null) ? 0 : clientID.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof CreateSessionMessage_V2)) {
         return false;
      }
      CreateSessionMessage_V2 other = (CreateSessionMessage_V2) obj;
      if (clientID == null) {
         if (other.clientID != null)
            return false;
      } else if (!clientID.equals(other.clientID))
         return false;
      return true;
   }
}
