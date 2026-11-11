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
package org.apache.activemq.artemis.core.paging.cursor.impl;

import java.util.Objects;

import org.apache.activemq.artemis.core.paging.cursor.PagePosition;

public class PagePositionImpl implements PagePosition {

   private final long pageNr;

   /**
    * The index of the message on the page file.
    * <p>
    * This can be used as -1 in cases where the message is irrelevant, for instance when a cursor is storing the next
    * message to be received or when a page is marked as fully complete (as the ACKs are removed)
    */
   private final int messageNr;

   /**
    * ID used for storage
    */
   private long recordID = -1;

   /**
    * Optional size value that can be set to specify the peristent size of the message for metrics tracking purposes
    */
   private long persistentSize;

   public PagePositionImpl(long pageNr, int messageNr) {
      this.pageNr = pageNr;
      this.messageNr = messageNr;
   }

   @Override
   public long getRecordID() {
      return recordID;
   }

   @Override
   public void setRecordID(long recordID) {
      this.recordID = recordID;
   }

   @Override
   public long getPageNr() {
      return pageNr;
   }

   @Override
   public int getMessageNr() {
      return messageNr;
   }

   @Override
   public long getPersistentSize() {
      return persistentSize;
   }

   @Override
   public void setPersistentSize(long persistentSize) {
      this.persistentSize = persistentSize;
   }

   @Override
   public int compareTo(PagePosition o) {
      if (pageNr > o.getPageNr()) {
         return 1;
      } else if (pageNr < o.getPageNr()) {
         return -1;
      } else {
         return Long.compare(recordID, o.getRecordID());
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(messageNr, pageNr);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof PagePositionImpl other)) {
         return false;
      }

      return messageNr == other.messageNr &&
             pageNr == other.pageNr;
   }

   @Override
   public String toString() {
      return "PagePositionImpl [pageNr=" + pageNr + ", messageNr=" + messageNr + ", recordID=" + recordID + "]";
   }
}
