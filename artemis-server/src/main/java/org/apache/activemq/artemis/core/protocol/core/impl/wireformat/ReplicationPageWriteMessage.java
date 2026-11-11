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
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public class ReplicationPageWriteMessage extends PacketImpl implements MessagePacketI {

   protected long pageNumber;

   protected PagedMessage pagedMessage;

   protected SimpleString address;

   final boolean useLong;

   final CoreMessageObjectPools coreMessageObjectPools;

   public ReplicationPageWriteMessage(final boolean useLong, CoreMessageObjectPools coreMessageObjectPools) {
      super(PacketImpl.REPLICATION_PAGE_WRITE);
      this.useLong = useLong;
      this.coreMessageObjectPools = coreMessageObjectPools;
   }

   public ReplicationPageWriteMessage(final PagedMessage pagedMessage, final long pageNumber, final boolean useLong, final SimpleString address) {
      this(useLong, null);
      this.pageNumber = pageNumber;
      this.pagedMessage = pagedMessage;
      this.address = address;
   }


   @Override
   public int expectedEncodeSize() {
      if (useLong) {
         return PACKET_HEADERS_SIZE + DataConstants.SIZE_LONG + // buffer.writeLong(pageNumber)
            pagedMessage.getEncodeSize() + //  pagedMessage.encode(buffer)
            SimpleString.sizeofString(address);  // SizeUtil.writeNullableString(address)
      } else {
         return PACKET_HEADERS_SIZE + DataConstants.SIZE_INT + // buffer.writeInt(pageNumber)
            pagedMessage.getEncodeSize(); //  pagedMessage.encode(buffer)
      }
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      if (useLong) {
         buffer.writeLong(pageNumber);
      } else {
         if (pageNumber > Integer.MAX_VALUE) {
            throw new IllegalStateException("pageNumber=" + pageNumber + " is too large to be used with older broker version on replication");
         }
         buffer.writeInt((int) pageNumber);
      }
      pagedMessage.encode(buffer);
      if (useLong) {
         buffer.writeNullableSimpleString(address);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      if (useLong) {
         pageNumber = buffer.readLong();
      } else {
         pageNumber = buffer.readInt();
      }
      pagedMessage = new PagedMessageImpl(0, null);
      pagedMessage.decode(buffer);
      if (buffer.readableBytes() > 0) {
         address = SimpleString.readNullableSimpleString(buffer.byteBuf(), coreMessageObjectPools.getAddressDecoderPool());
      }
   }

   public SimpleString getAddress() {
      if (address != null) {
         return address;
      } else {
         return pagedMessage.getMessage().getAddressSimpleString();
      }
   }

   public long getPageNumber() {
      return pageNumber;
   }

   public PagedMessage getPagedMessage() {
      return pagedMessage;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), pageNumber, pagedMessage);
   }

   @Override
   public Message getMessage() {
      return pagedMessage.getMessage();
   }

   @Override
   public ReplicationPageWriteMessage replaceMessage(Message message) {
      // nothing to be done
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ReplicationPageWriteMessage other)) {
         return false;
      }

      return pageNumber == other.pageNumber &&
             Objects.equals(pagedMessage, other.pagedMessage);
   }

}
