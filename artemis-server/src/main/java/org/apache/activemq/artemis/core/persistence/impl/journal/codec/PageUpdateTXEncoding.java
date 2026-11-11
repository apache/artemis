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

import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.utils.DataConstants;

public class PageUpdateTXEncoding implements EncodingSupport {

   public long pageTX;

   public int records;

   @Override
   public String toString() {
      return "PageUpdateTXEncoding [pageTX=" + pageTX + ", records=" + records + "]";
   }

   public PageUpdateTXEncoding() {
   }

   public PageUpdateTXEncoding(final long pageTX, final int records) {
      this.pageTX = pageTX;
      this.records = records;
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      this.pageTX = buffer.readLong();
      this.records = buffer.readInt();
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeLong(pageTX);
      buffer.writeInt(records);
   }

   @Override
   public int getEncodeSize() {
      return DataConstants.SIZE_LONG + DataConstants.SIZE_INT;
   }

   public List<MessageReference> getRelatedMessageReferences() {
      return null;
   }
}
