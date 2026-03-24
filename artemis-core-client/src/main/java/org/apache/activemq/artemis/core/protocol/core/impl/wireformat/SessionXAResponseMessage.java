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

public class SessionXAResponseMessage extends PacketImpl {

   protected boolean error;

   protected int responseCode;

   protected String message;

   public SessionXAResponseMessage(final boolean isError, final int responseCode, final String message) {
      super(SESS_XA_RESP);

      error = isError;

      this.responseCode = responseCode;

      this.message = message;
   }

   public SessionXAResponseMessage() {
      super(SESS_XA_RESP);
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   public boolean isError() {
      return error;
   }

   public int getResponseCode() {
      return responseCode;
   }

   public String getMessage() {
      return message;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeBoolean(error);
      buffer.writeInt(responseCode);
      buffer.writeNullableString(message);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      error = buffer.readBoolean();
      responseCode = buffer.readInt();
      message = buffer.readNullableString();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (error ? 1231 : 1237);
      result = prime * result + ((message == null) ? 0 : message.hashCode());
      result = prime * result + responseCode;
      return result;
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", error=" + error);
      buff.append(", message=" + message);
      buff.append(", responseCode=" + responseCode);
      buff.append("]");
      return buff.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (!super.equals(obj))
         return false;
      if (!(obj instanceof SessionXAResponseMessage))
         return false;
      SessionXAResponseMessage other = (SessionXAResponseMessage) obj;
      if (error != other.error)
         return false;
      if (message == null) {
         if (other.message != null)
            return false;
      } else if (!message.equals(other.message))
         return false;
      if (responseCode != other.responseCode)
         return false;
      return true;
   }
}
