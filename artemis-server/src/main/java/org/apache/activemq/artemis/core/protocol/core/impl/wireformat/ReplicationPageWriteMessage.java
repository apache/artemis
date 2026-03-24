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
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

public class ReplicationPageWriteMessage extends PacketImpl implements MessagePacketI {

   private int pageNumber;

   private PagedMessage pagedMessage;

   public ReplicationPageWriteMessage() {
      super(PacketImpl.REPLICATION_PAGE_WRITE);
   }

   public ReplicationPageWriteMessage(final PagedMessage pagedMessage, final int pageNumber) {
      this();
      this.pageNumber = pageNumber;
      this.pagedMessage = pagedMessage;
   }


   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
             DataConstants.SIZE_INT + // buffer.writeInt(pageNumber);
             pagedMessage.getEncodeSize(); //  pagedMessage.encode(buffer);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(pageNumber);
      pagedMessage.encode(buffer);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      pageNumber = buffer.readInt();
      pagedMessage = new PagedMessageImpl(0, null);
      pagedMessage.decode(buffer);
   }

   /**
    * @return the pageNumber
    */
   public int getPageNumber() {
      return pageNumber;
   }

   /**
    * @return the pagedMessage
    */
   public PagedMessage getPagedMessage() {
      return pagedMessage;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + pageNumber;
      result = prime * result + ((pagedMessage == null) ? 0 : pagedMessage.hashCode());
      return result;
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
      if (this == obj)
         return true;
      if (!super.equals(obj))
         return false;
      if (getClass() != obj.getClass())
         return false;
      ReplicationPageWriteMessage other = (ReplicationPageWriteMessage) obj;
      if (pageNumber != other.pageNumber)
         return false;
      if (pagedMessage == null) {
         if (other.pagedMessage != null)
            return false;
      } else if (!pagedMessage.equals(other.pagedMessage))
         return false;
      return true;
   }

}
