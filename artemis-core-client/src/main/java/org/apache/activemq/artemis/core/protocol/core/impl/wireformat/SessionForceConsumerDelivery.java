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

public class SessionForceConsumerDelivery extends PacketImpl {

   private long consumerID;
   private long sequence;

   public SessionForceConsumerDelivery(final long consumerID, final long sequence) {
      super(SESS_FORCE_CONSUMER_DELIVERY);

      this.consumerID = consumerID;
      this.sequence = sequence;
   }

   public SessionForceConsumerDelivery() {
      super(SESS_FORCE_CONSUMER_DELIVERY);
   }

   public long getConsumerID() {
      return consumerID;
   }

   public long getSequence() {
      return sequence;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(consumerID);
      buffer.writeLong(sequence);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      consumerID = buffer.readLong();
      sequence = buffer.readLong();
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", consumerID=" + consumerID);
      sb.append(", sequence=" + sequence);
      return sb.toString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), consumerID, sequence);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionForceConsumerDelivery other)) {
         return false;
      }

      return consumerID == other.consumerID &&
             sequence == other.sequence;
   }

}
