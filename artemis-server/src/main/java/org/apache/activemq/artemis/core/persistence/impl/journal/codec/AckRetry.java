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

package org.apache.activemq.artemis.core.persistence.impl.journal.codec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.collections.AbstractHashMapPersister;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.utils.DataConstants;

public final class AckRetry {
   String nodeID;
   byte[] nodeIDBytes;
   long messageID;
   AckReason reason;
   int pageAttempts;
   int queueAttempts;
   int viewCount;

   public int getViewCount() {
      return viewCount;
   }

   public int incrementViewCount() {
      return ++viewCount;
   }

   private static Persister persister = new Persister();

   public static Persister getPersister() {
      return persister;
   }

   @Override
   public String toString() {
      return "AckRetry{" + "nodeID='" + nodeID + '\'' + ", messageID=" + messageID + ", reason=" + reason + ", pageAttempts=" + pageAttempts + ", queueAttempts=" + queueAttempts + ", viewCount=" + viewCount + '}';
   }

   public AckRetry() {
   }

   public AckRetry(String nodeID, long messageID, AckReason reason) {
      this.nodeID = nodeID;
      this.messageID = messageID;
      this.reason = reason;
   }


   public synchronized byte[] getNodeIDBytes() {
      if (nodeIDBytes == null) {
         nodeIDBytes = nodeID.getBytes(StandardCharsets.US_ASCII);
      }
      return nodeIDBytes;
   }

   public String getNodeID() {
      return nodeID;
   }

   public AckRetry setNodeID(String nodeID) {
      this.nodeID = nodeID;
      return this;
   }

   public long getMessageID() {
      return messageID;
   }

   public AckRetry setMessageID(long messageID) {
      this.messageID = messageID;
      return this;
   }

   public AckReason getReason() {
      return reason;
   }

   public AckRetry setReason(AckReason reason) {
      this.reason = reason;
      return this;
   }

   public int getPageAttempts() {
      return pageAttempts;
   }

   public int getQueueAttempts() {
      return queueAttempts;
   }

   public int attemptedPage() {
      return ++pageAttempts;
   }

   public int attemptedQueue() {
      return ++queueAttempts;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AckRetry other)) {
         return false;
      }

      return messageID == other.messageID &&
             Objects.equals(nodeID, other.nodeID);
   }

   @Override
   public int hashCode() {
      return Objects.hash(nodeID, messageID);
   }

   public static class Persister extends AbstractHashMapPersister<Long, AckRetry, AckRetry> {

      private Persister() {
      }

      @Override
      protected int getCollectionIdSize(Long collectionID) {
         return DataConstants.SIZE_LONG;
      }

      @Override
      protected void encodeCollectionId(ActiveMQBuffer buffer, Long collectionID) {
         buffer.writeLong(collectionID);
      }

      @Override
      protected Long decodeCollectionId(ActiveMQBuffer buffer) {
         return buffer.readLong();
      }

      @Override
      protected int getKeySize(AckRetry key) {
         return DataConstants.SIZE_INT +
            (key.getNodeID() == null ? 0 : key.getNodeIDBytes().length) +
            DataConstants.SIZE_LONG +
            DataConstants.SIZE_BYTE;
      }

      @Override
      protected void encodeKey(ActiveMQBuffer buffer, AckRetry key) {
         if (key.getNodeID() == null) {
            buffer.writeInt(0);
         } else {
            byte[] temporaryNodeBytes = key.getNodeIDBytes();
            buffer.writeInt(temporaryNodeBytes.length);
            buffer.writeBytes(temporaryNodeBytes);
         }
         buffer.writeLong(key.messageID);
         buffer.writeByte(key.reason.getVal());
      }

      @Override
      protected AckRetry decodeKey(ActiveMQBuffer buffer) {
         int sizeBytes = buffer.readInt();
         String nodeID;
         if (sizeBytes == 0) {
            nodeID = null;
         } else {
            byte[] temporaryNodeBytes = new byte[sizeBytes];
            buffer.readBytes(temporaryNodeBytes);
            nodeID = new String(temporaryNodeBytes, StandardCharsets.US_ASCII);
         }
         long messageID = buffer.readLong();
         AckReason reason = AckReason.fromValue(buffer.readByte());
         return new AckRetry(nodeID, messageID, reason);
      }

      @Override
      protected int getValueSize(AckRetry value) {
         return 0;
      }

      @Override
      protected void encodeValue(ActiveMQBuffer buffer, AckRetry value) {
      }

      @Override
      protected AckRetry decodeValue(ActiveMQBuffer buffer, AckRetry key) {
         return key;
      }
   }

}

