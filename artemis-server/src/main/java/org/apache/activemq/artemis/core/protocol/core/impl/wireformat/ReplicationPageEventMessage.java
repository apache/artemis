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
import org.apache.activemq.artemis.utils.DataConstants;

public class ReplicationPageEventMessage extends PacketImpl {

   protected long pageNumber;

   private SimpleString storeName;

   /**
    * True = delete page, False = close page
    */
   private boolean isDelete;

   private final boolean useLong;

   public ReplicationPageEventMessage(boolean useLong) {
      super(PacketImpl.REPLICATION_PAGE_EVENT);
      this.useLong = useLong;
   }

   public ReplicationPageEventMessage(final SimpleString storeName, final long pageNumber, final boolean isDelete, final boolean useLong) {
      this(useLong);
      this.pageNumber = pageNumber;
      this.isDelete = isDelete;
      this.storeName = storeName;
   }

   @Override
   public int expectedEncodeSize() {
      if (useLong) {
         return PACKET_HEADERS_SIZE + SimpleString.sizeofString(storeName) + // buffer.writeSimpleString(storeName);
            DataConstants.SIZE_LONG + //  buffer.writeLong(pageNumber);
            DataConstants.SIZE_BOOLEAN; // buffer.writeBoolean(isDelete);
      } else {
         return PACKET_HEADERS_SIZE + SimpleString.sizeofString(storeName) + // buffer.writeSimpleString(storeName);
            DataConstants.SIZE_INT + //  buffer.writeInt(pageNumber);
            DataConstants.SIZE_BOOLEAN; // buffer.writeBoolean(isDelete);
      }
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(storeName);
      if (useLong) {
         buffer.writeLong(pageNumber);
      } else {
         if (pageNumber > Integer.MAX_VALUE) {
            throw new IllegalStateException("pageNumber=" + pageNumber + " is too large to be used with older broker version on replication");
         }
         buffer.writeInt((int) pageNumber);
      }
      buffer.writeBoolean(isDelete);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      storeName = buffer.readSimpleString();
      if (useLong) {
         pageNumber = buffer.readLong();
      } else {
         pageNumber = buffer.readInt();
      }
      isDelete = buffer.readBoolean();
   }

   public long getPageNumber() {
      return pageNumber;
   }

   public SimpleString getStoreName() {
      return storeName;
   }

   public boolean isDelete() {
      return isDelete;
   }

   @Override
   protected String getPacketString() {
      String baseString = super.getPacketString();
      return baseString + ", channel=" + channelID + ", isDelete=" + isDelete +
         ", storeName=" + storeName + ", pageNumber=" + pageNumber;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), isDelete, pageNumber, storeName);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationPageEventMessage other)) {
         return false;
      }

      return isDelete == other.isDelete &&
             pageNumber == other.pageNumber &&
             Objects.equals(storeName, other.storeName);
   }
}
