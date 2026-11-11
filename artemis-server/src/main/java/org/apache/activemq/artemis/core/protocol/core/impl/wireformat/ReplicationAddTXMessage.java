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

   // this is for version compatibility
   private final boolean beforeTwoEighteen;

   public ReplicationAddTXMessage(final boolean beforeTwoEighteen) {
      super(PacketImpl.REPLICATION_APPEND_TX);
      this.beforeTwoEighteen = beforeTwoEighteen;
   }

   public ReplicationAddTXMessage(final boolean beforeTwoEighteen,
                                  final byte journalID,
                                  final ADD_OPERATION_TYPE operation,
                                  final long txId,
                                  final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object encodingData) {
      this(beforeTwoEighteen);
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
      if (beforeTwoEighteen) {
         buffer.writeBoolean(operation == ADD_OPERATION_TYPE.UPDATE);
      } else {
         buffer.writeByte(operation.toRecord());
      }
      buffer.writeLong(txId);
      buffer.writeLong(id);
      buffer.writeByte(recordType);
      buffer.writeInt(persister.getEncodeSize(encodingData));
      persister.encode(buffer, encodingData);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      journalID = buffer.readByte();
      if (beforeTwoEighteen) {
         boolean isUpdate = buffer.readBoolean();
         if (isUpdate) {
            operation = ADD_OPERATION_TYPE.UPDATE;
         } else {
            operation = ADD_OPERATION_TYPE.ADD;
         }
      } else {
         operation = ADD_OPERATION_TYPE.toOperation(buffer.readByte());
      }
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

   public byte getJournalID() {
      return journalID;
   }

   public ADD_OPERATION_TYPE getOperation() {
      return operation;
   }

   public byte getRecordType() {
      return recordType;
   }

   public byte[] getRecordData() {
      return recordData;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), encodingData, id, journalID, operation, Arrays.hashCode(recordData),
                          recordType, txId);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationAddTXMessage other)) {
         return false;
      }

      return Objects.equals(encodingData, other.encodingData) &&
             id == other.id &&
             journalID == other.journalID &&
             operation == other.operation &&
             Arrays.equals(recordData, other.recordData) &&
             recordType == other.recordType &&
             txId == other.txId;
   }
}
