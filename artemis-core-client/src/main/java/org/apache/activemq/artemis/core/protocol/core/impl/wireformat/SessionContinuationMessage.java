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

import java.util.Arrays;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public abstract class SessionContinuationMessage extends PacketImpl {

   public static final int SESSION_CONTINUATION_BASE_SIZE = PACKET_HEADERS_SIZE + DataConstants.SIZE_INT +
      DataConstants.SIZE_BOOLEAN;

   protected byte[] body;

   protected boolean continues;

   public SessionContinuationMessage(final byte type, final byte[] body, final boolean continues) {
      super(type);
      this.body = body;
      this.continues = continues;
   }

   public SessionContinuationMessage(final byte type) {
      super(type);
   }

   public byte[] getBody() {
      if (size <= 0) {
         return new byte[0];
      } else {
         return body;
      }
   }

   public boolean isContinues() {
      return continues;
   }

   /**
    * Returns the exact expected encoded size of {@code this} packet. It will be used to allocate the proper encoding
    * buffer in {@link #createPacket}, hence any wrong value will result in a thrown exception or a resize of the
    * encoding buffer during the encoding process, depending to the implementation of {@link #createPacket}. Any child
    * of {@code this} class are required to override this method if their encoded size is changed from the base class.
    *
    * @return the size in bytes of the expected encoded packet
    */
   @Override
   public int expectedEncodeSize() {
      return SESSION_CONTINUATION_BASE_SIZE + (body == null ? 0 : body.length);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(body.length);
      buffer.writeBytes(body);
      buffer.writeBoolean(continues);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      int size = buffer.readInt();
      body = new byte[size];
      buffer.readBytes(body);
      continues = buffer.readBoolean();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), Arrays.hashCode(body), continues);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", body=" + Arrays.toString(body));
      sb.append(", continues=" + continues);
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
      if (!(obj instanceof SessionContinuationMessage other)) {
         return false;
      }

      return Arrays.equals(body, other.body) &&
             continues == other.continues;
   }
}
