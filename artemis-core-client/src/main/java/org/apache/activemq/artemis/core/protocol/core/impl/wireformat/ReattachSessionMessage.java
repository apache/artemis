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

public class ReattachSessionMessage extends PacketImpl {

   private String name;

   private int lastConfirmedCommandID;

   public ReattachSessionMessage(final String name, final int lastConfirmedCommandID) {
      super(REATTACH_SESSION);

      this.name = name;

      this.lastConfirmedCommandID = lastConfirmedCommandID;
   }

   public ReattachSessionMessage() {
      super(REATTACH_SESSION);
   }

   public String getName() {
      return name;
   }

   public int getLastConfirmedCommandID() {
      return lastConfirmedCommandID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeString(name);
      buffer.writeInt(lastConfirmedCommandID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      name = buffer.readString();
      lastConfirmedCommandID = buffer.readInt();
   }

   @Override
   public final boolean isRequiresConfirmations() {
      return false;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), lastConfirmedCommandID, name);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", lastConfirmedCommandID=" + lastConfirmedCommandID);
      sb.append(", name=" + name);
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
      if (!(obj instanceof ReattachSessionMessage other)) {
         return false;
      }

      return lastConfirmedCommandID == other.lastConfirmedCommandID &&
             Objects.equals(name, other.name);
   }
}
