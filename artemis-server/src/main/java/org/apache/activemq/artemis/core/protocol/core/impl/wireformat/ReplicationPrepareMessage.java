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
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public final class ReplicationPrepareMessage extends PacketImpl {

   private long txId;

   /**
    * 0 - BindingsImpl, 1 - MessagesJournal
    */
   private byte journalID;

   private EncodingSupport encodingData;

   private byte[] recordData;

   public ReplicationPrepareMessage() {
      super(PacketImpl.REPLICATION_PREPARE);
   }

   public ReplicationPrepareMessage(final byte journalID, final long txId, final EncodingSupport encodingData) {
      this();
      this.journalID = journalID;
      this.txId = txId;
      this.encodingData = encodingData;
   }


   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
             DataConstants.SIZE_BYTE + // buffer.writeByte(journalID);
             DataConstants.SIZE_LONG + // buffer.writeLong(txId);
             DataConstants.SIZE_INT + // buffer.writeInt(encodingData.getEncodeSize());
             encodingData.getEncodeSize(); // encodingData.encode(buffer);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeByte(journalID);
      buffer.writeLong(txId);
      buffer.writeInt(encodingData.getEncodeSize());
      encodingData.encode(buffer);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      journalID = buffer.readByte();
      txId = buffer.readLong();
      int size = buffer.readInt();
      recordData = new byte[size];
      buffer.readBytes(recordData);
   }

   public long getTxId() {
      return txId;
   }

   public byte getJournalID() {
      return journalID;
   }

   public byte[] getRecordData() {
      return recordData;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), encodingData, journalID, Arrays.hashCode(recordData), txId);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationPrepareMessage other)) {
         return false;
      }

      return Objects.equals(encodingData, other.encodingData) &&
             journalID == other.journalID &&
             Arrays.equals(recordData, other.recordData) &&
             txId == other.txId;
   }
}
