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
      return Objects.hash(super.hashCode(), error, message, responseCode);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", error=" + error);
      sb.append(", message=" + message);
      sb.append(", responseCode=" + responseCode);
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
      if (!(obj instanceof SessionXAResponseMessage other)) {
         return false;
      }

      return error == other.error &&
             Objects.equals(message, other.message) &&
             responseCode == other.responseCode;
   }
}
