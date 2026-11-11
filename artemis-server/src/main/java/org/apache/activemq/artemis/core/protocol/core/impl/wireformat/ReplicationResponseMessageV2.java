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
import org.apache.activemq.artemis.utils.DataConstants;

public final class ReplicationResponseMessageV2 extends ReplicationResponseMessage {

   boolean synchronizationIsFinishedAcknowledgement = false;

   public ReplicationResponseMessageV2(final boolean synchronizationIsFinishedAcknowledgement) {
      super(REPLICATION_RESPONSE_V2);

      this.synchronizationIsFinishedAcknowledgement = synchronizationIsFinishedAcknowledgement;
   }

   public ReplicationResponseMessageV2() {
      super(PacketImpl.REPLICATION_RESPONSE_V2);
   }

   public boolean isSynchronizationIsFinishedAcknowledgement() {
      return synchronizationIsFinishedAcknowledgement;
   }

   public ReplicationResponseMessageV2 setSynchronizationIsFinishedAcknowledgement(boolean synchronizationIsFinishedAcknowledgement) {
      this.synchronizationIsFinishedAcknowledgement = synchronizationIsFinishedAcknowledgement;
      return this;
   }

   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_BOOLEAN; // buffer.writeBoolean(synchronizationIsFinishedAcknowledgement);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeBoolean(synchronizationIsFinishedAcknowledgement);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      synchronizationIsFinishedAcknowledgement = buffer.readBoolean();
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", synchronizationIsFinishedAcknowledgement=" + synchronizationIsFinishedAcknowledgement);
      return sb.toString();
   }
}
