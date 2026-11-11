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

public class PacketsConfirmedMessage extends PacketImpl {

   private int commandID;

   public PacketsConfirmedMessage(final int commandID) {
      super(PACKETS_CONFIRMED);

      this.commandID = commandID;
   }

   public PacketsConfirmedMessage() {
      super(PACKETS_CONFIRMED);
   }

   public int getCommandID() {
      return commandID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(commandID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      commandID = buffer.readInt();
   }

   @Override
   public final boolean isRequiresConfirmations() {
      return false;
   }

   @Override
   protected String getPacketString() {
      return super.getPacketString() + ", commandID=" + commandID;
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(commandID);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof PacketsConfirmedMessage other)) {
         return false;
      }

      return commandID == other.commandID;
   }
}
