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

public final class ReplicationAddMessage extends PacketImpl {

   private long id;

   /**
    * 0 - BindingsImpl, 1 - MessagesJournal
    */
   private byte journalID;

   private ADD_OPERATION_TYPE operation;

   private byte journalRecordType;

   private Persister persister;

   private Object encodingData;

   private byte[] recordData;

   // this is for version compatibility
   private final boolean beforeTwoEighteen;

   public ReplicationAddMessage(final boolean beforeTwoEighteen) {
      super(PacketImpl.REPLICATION_APPEND);
      this.beforeTwoEighteen = beforeTwoEighteen;
   }

   public ReplicationAddMessage(final boolean beforeTwoEighteen,
                                final byte journalID,
                                final ADD_OPERATION_TYPE operation,
                                final long id,
                                final byte journalRecordType,
                                final Persister persister,
                                final Object encodingData) {
      this(beforeTwoEighteen);
      this.journalID = journalID;
      this.operation = operation;
      this.id = id;
      this.journalRecordType = journalRecordType;
      this.persister = persister;
      this.encodingData = encodingData;
   }


   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_BYTE + // buffer.writeByte(journalID);
         DataConstants.SIZE_BOOLEAN + // buffer.writeBoolean(operation.toBoolean());
         DataConstants.SIZE_LONG + // buffer.writeLong(id);
         DataConstants.SIZE_BYTE + // buffer.writeByte(journalRecordType);
         DataConstants.SIZE_INT + // buffer.writeInt(persister.getEncodeSize(encodingData));
         persister.getEncodeSize(encodingData);// persister.encode(buffer, encodingData);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeByte(journalID);
      if (beforeTwoEighteen) {
         buffer.writeBoolean(operation == ADD_OPERATION_TYPE.UPDATE);
      } else {
         buffer.writeByte(operation.toRecord());
      }
      buffer.writeLong(id);
      buffer.writeByte(journalRecordType);
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
      id = buffer.readLong();
      journalRecordType = buffer.readByte();
      final int recordDataSize = buffer.readInt();
      recordData = new byte[recordDataSize];
      buffer.readBytes(recordData);
   }

   public long getId() {
      return id;
   }

   public byte getJournalID() {
      return journalID;
   }

   public ADD_OPERATION_TYPE getRecord() {
      return operation;
   }

   public byte getJournalRecordType() {
      return journalRecordType;
   }

   public byte[] getRecordData() {
      return recordData;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), encodingData, id, journalID, journalRecordType, operation,
                          Arrays.hashCode(recordData));
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationAddMessage other)) {
         return false;
      }

      return Objects.equals(encodingData, other.encodingData) &&
             id == other.id &&
             journalID == other.journalID &&
             journalRecordType == other.journalRecordType &&
             operation == other.operation &&
             Arrays.equals(recordData, other.recordData);
   }
}
