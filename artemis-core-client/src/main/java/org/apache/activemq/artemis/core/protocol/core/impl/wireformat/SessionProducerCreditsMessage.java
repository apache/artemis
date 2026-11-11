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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class SessionProducerCreditsMessage extends PacketImpl {

   private int credits;

   private SimpleString address;

   public SessionProducerCreditsMessage(final int credits, final SimpleString address) {
      super(SESS_PRODUCER_CREDITS);

      this.credits = credits;

      this.address = address;
   }

   public SessionProducerCreditsMessage() {
      super(SESS_PRODUCER_CREDITS);
   }

   public int getCredits() {
      return credits;
   }

   public SimpleString getAddress() {
      return address;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(credits);
      buffer.writeSimpleString(address);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      credits = buffer.readInt();
      address = buffer.readSimpleString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), address, credits);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", address=" + address);
      sb.append(", credits=" + credits);
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
      if (!(obj instanceof SessionProducerCreditsMessage other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             credits == other.credits;
   }
}
