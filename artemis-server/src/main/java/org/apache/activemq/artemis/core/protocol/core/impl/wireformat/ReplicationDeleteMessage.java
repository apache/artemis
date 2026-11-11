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
import org.apache.activemq.artemis.utils.DataConstants;

public final class ReplicationDeleteMessage extends PacketImpl {

   private long id;

   /**
    * 0 - BindingsImpl, 1 - MessagesJournal
    */
   private byte journalID;

   public ReplicationDeleteMessage() {
      super(PacketImpl.REPLICATION_DELETE);
   }

   public ReplicationDeleteMessage(final byte journalID, final long id) {
      this();
      this.journalID = journalID;
      this.id = id;
   }

   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_BYTE + // buffer.writeByte(journalID);
         DataConstants.SIZE_LONG; // buffer.writeLong(id);
   }


   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeByte(journalID);
      buffer.writeLong(id);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      journalID = buffer.readByte();
      id = buffer.readLong();
   }

   public long getId() {
      return id;
   }

   public byte getJournalID() {
      return journalID;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), id, journalID);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationDeleteMessage other)) {
         return false;
      }

      return id == other.id &&
             journalID == other.journalID;
   }
}
