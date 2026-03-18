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
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.persistence.impl.journal.AbstractJournalStorageManager;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This message may signal start or end of the replication synchronization.
 * <p>
 * At start, it sends all fileIDs used in a given journal primary server to the backup, so the backup can reserve those
 * IDs.
 */
public class ReplicationStartSyncMessage extends PacketImpl {

   private long[] ids;
   private SyncDataType dataType;
   private boolean synchronizationIsFinished;
   private String nodeID;
   private boolean allowsAutoFailBack;

   public enum SyncDataType {
      JournalBindings(AbstractJournalStorageManager.JournalContent.BINDINGS.typeByte),
      JournalMessages(AbstractJournalStorageManager.JournalContent.MESSAGES.typeByte),
      LargeMessages((byte) 2),
      ActivationSequence((byte) 3);

      private byte code;

      SyncDataType(byte code) {
         this.code = code;
      }

      public static AbstractJournalStorageManager.JournalContent getJournalContentType(SyncDataType dataType) {
         return AbstractJournalStorageManager.JournalContent.getType(dataType.code);
      }

      public static SyncDataType getDataType(byte code) {
         if (code == JournalBindings.code) {
            return JournalBindings;
         }
         if (code == JournalMessages.code) {
            return JournalMessages;
         }
         if (code == LargeMessages.code) {
            return LargeMessages;
         }
         if (code == ActivationSequence.code) {
            return ActivationSequence;
         }

         throw new InvalidParameterException("invalid byte: " + code);
      }
   }

   public ReplicationStartSyncMessage() {
      super(REPLICATION_START_FINISH_SYNC);
   }

   public ReplicationStartSyncMessage(List<Long> filenames) {
      this();
      ids = new long[filenames.size()];
      for (int i = 0; i < filenames.size(); i++) {
         ids[i] = filenames.get(i);
      }
      dataType = SyncDataType.LargeMessages;
      nodeID = ""; // this value will be ignored
   }


   public ReplicationStartSyncMessage(String nodeID, long nodeDataVersion) {
      this();
      synchronizationIsFinished = true;
      this.nodeID = nodeID;
      ids = new long[1];
      ids[0] = nodeDataVersion;
      dataType = SyncDataType.ActivationSequence;
   }

   public ReplicationStartSyncMessage(JournalFile[] datafiles,
                                      AbstractJournalStorageManager.JournalContent contentType,
                                      String nodeID,
                                      boolean allowsAutoFailBack) {
      this();
      this.nodeID = nodeID;
      this.allowsAutoFailBack = allowsAutoFailBack;
      synchronizationIsFinished = false;
      ids = new long[datafiles.length];
      for (int i = 0; i < datafiles.length; i++) {
         ids[i] = datafiles[i].getFileID();
      }
      switch (contentType) {
         case MESSAGES:
            dataType = SyncDataType.JournalMessages;
            break;
         case BINDINGS:
            dataType = SyncDataType.JournalBindings;
            break;
         default:
            throw new IllegalArgumentException();
      }
   }


   @Override
   public int expectedEncodeSize() {
      int size = PACKET_HEADERS_SIZE +
             DataConstants.SIZE_BOOLEAN + // buffer.writeBoolean(synchronizationIsFinished);
             DataConstants.SIZE_BOOLEAN + // buffer.writeBoolean(allowsAutoFailBack);
             nodeID.length() * 3; //  buffer.writeString(nodeID); -- an estimate

      size += DataConstants.SIZE_BYTE + // buffer.writeByte(dataType.code);
              DataConstants.SIZE_INT +  // buffer.writeInt(ids.length);
              DataConstants.SIZE_LONG * ids.length; // the write loop

      return size;
   }


   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeBoolean(synchronizationIsFinished);
      buffer.writeBoolean(allowsAutoFailBack);
      buffer.writeString(nodeID);
      buffer.writeByte(dataType.code);
      buffer.writeInt(ids.length);
      for (long id : ids) {
         buffer.writeLong(id);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      synchronizationIsFinished = buffer.readBoolean();
      allowsAutoFailBack = buffer.readBoolean();
      nodeID = buffer.readString();
      if (buffer.readableBytes() == 0) {
         // Pre-2.18.0 server wouldn't send anything more than this.
         return;
      }
      dataType = SyncDataType.getDataType(buffer.readByte());
      int length = buffer.readInt();
      ids = new long[length];
      for (int i = 0; i < length; i++) {
         ids[i] = buffer.readLong();
      }
   }

   /**
    * @return whether the server is configured to allow for fail-back
    */
   public boolean isServerToFailBack() {
      return allowsAutoFailBack;
   }

   /**
    * @return {@code true} if the primary has finished synchronizing its data and the backup is therefore up-to-date,
    * {@code false} otherwise.
    */
   public boolean isSynchronizationFinished() {
      return synchronizationIsFinished;
   }

   public SyncDataType getDataType() {
      return dataType;
   }

   public long[] getFileIds() {
      return ids;
   }

   public String getNodeID() {
      return nodeID;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), allowsAutoFailBack, dataType, Arrays.hashCode(ids), nodeID,
                          synchronizationIsFinished);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationStartSyncMessage other)) {
         return false;
      }

      return allowsAutoFailBack == other.allowsAutoFailBack &&
             dataType == other.dataType &&
             Arrays.equals(ids, other.ids) &&
             Objects.equals(nodeID, other.nodeID) &&
             synchronizationIsFinished == other.synchronizationIsFinished;
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", synchronizationIsFinished=" + synchronizationIsFinished);
      sb.append(", dataType=" + dataType);
      sb.append(", nodeID=" + nodeID);
      sb.append(", ids=" + Arrays.toString(ids));
      sb.append(", allowsAutoFailBack=" + allowsAutoFailBack);
      return sb.toString();
   }
}
