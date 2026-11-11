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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.utils.DataConstants;

public class PageCountRecord implements EncodingSupport {

   private long queueID;

   private long value;

   private long persistentSize;

   @Override
   public String toString() {
      return "PageCountRecord [queueID=" + queueID + ", value=" + value + ", persistentSize=" + persistentSize + "]";
   }

   public PageCountRecord() {

   }

   public PageCountRecord(long queueID, long value, long persistentSize) {
      this.queueID = queueID;
      this.value = value;
      this.persistentSize = persistentSize;
   }

   public long getQueueID() {
      return queueID;
   }

   public long getValue() {
      return value;
   }

   public long getPersistentSize() {
      return persistentSize;
   }

   @Override
   public int getEncodeSize() {
      return DataConstants.SIZE_LONG * 3;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeLong(queueID);
      buffer.writeLong(value);
      buffer.writeLong(persistentSize);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      queueID = buffer.readLong();
      value = buffer.readLong();

      if (buffer.readableBytes() > 0) {
         persistentSize = buffer.readLong();
      }
   }

}
