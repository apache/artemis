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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.replication.ReplicationManager.ADD_OPERATION_TYPE;
import org.apache.activemq.artemis.utils.DataConstants;

public class ReplicationAddTXMessage extends PacketImpl {

   private long txId;

   private long id;

   /**
    * 0 - BindingsImpl, 1 - MessagesJournal
    */
   private byte journalID;

   private byte recordType;

   private Persister persister;

   private Object encodingData;

   private byte[] recordData;

   private ADD_OPERATION_TYPE operation;

   public ReplicationAddTXMessage() {
      super(PacketImpl.REPLICATION_APPEND_TX);
   }

   public ReplicationAddTXMessage(final byte journalID,
                                  final ADD_OPERATION_TYPE operation,
                                  final long txId,
                                  final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object encodingData) {
      this();
      this.journalID = journalID;
      this.operation = operation;
      this.txId = txId;
      this.id = id;
      this.recordType = recordType;
      this.encodingData = encodingData;
      this.persister = persister;
   }


   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
            DataConstants.SIZE_BYTE + // buffer.writeByte(journalID);
            DataConstants.SIZE_BOOLEAN + // buffer.writeBoolean(operation.toBoolean());
            DataConstants.SIZE_LONG + // buffer.writeLong(txId);
            DataConstants.SIZE_LONG + // buffer.writeLong(id);
            DataConstants.SIZE_BYTE + // buffer.writeByte(recordType);
            DataConstants.SIZE_INT + // buffer.writeInt(persister.getEncodeSize(encodingData));
            persister.getEncodeSize(encodingData); // persister.encode(buffer, encodingData);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeByte(journalID);
      buffer.writeByte(operation.toRecord());
      buffer.writeLong(txId);
      buffer.writeLong(id);
      buffer.writeByte(recordType);
      buffer.writeInt(persister.getEncodeSize(encodingData));
      persister.encode(buffer, encodingData);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      journalID = buffer.readByte();
      operation = ADD_OPERATION_TYPE.toOperation(buffer.readByte());
      txId = buffer.readLong();
      id = buffer.readLong();
      recordType = buffer.readByte();
      final int recordDataSize = buffer.readInt();
      recordData = new byte[recordDataSize];
      buffer.readBytes(recordData);
   }

   /**
    * @return the id
    */
   public long getId() {
      return id;
   }

   public long getTxId() {
      return txId;
   }

   /**
    * @return the journalID
    */
   public byte getJournalID() {
      return journalID;
   }

   public ADD_OPERATION_TYPE getOperation() {
      return operation;
   }

   /**
    * @return the recordType
    */
   public byte getRecordType() {
      return recordType;
   }

   /**
    * @return the recordData
    */
   public byte[] getRecordData() {
      return recordData;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + ((encodingData == null) ? 0 : encodingData.hashCode());
      result = prime * result + (int) (id ^ (id >>> 32));
      result = prime * result + journalID;
      result = prime * result + ((operation == null) ? 0 : operation.hashCode());
      result = prime * result + Arrays.hashCode(recordData);
      result = prime * result + recordType;
      result = prime * result + (int) (txId ^ (txId >>> 32));
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (!super.equals(obj))
         return false;
      if (!(obj instanceof ReplicationAddTXMessage))
         return false;
      ReplicationAddTXMessage other = (ReplicationAddTXMessage) obj;
      if (encodingData == null) {
         if (other.encodingData != null)
            return false;
      } else if (!encodingData.equals(other.encodingData))
         return false;
      if (id != other.id)
         return false;
      if (journalID != other.journalID)
         return false;
      if (operation != other.operation)
         return false;
      if (!Arrays.equals(recordData, other.recordData))
         return false;
      if (recordType != other.recordType)
         return false;
      if (txId != other.txId)
         return false;
      return true;
   }
}
