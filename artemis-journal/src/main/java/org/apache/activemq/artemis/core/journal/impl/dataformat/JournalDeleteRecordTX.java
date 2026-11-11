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
package org.apache.activemq.artemis.core.journal.impl.dataformat;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;

public class JournalDeleteRecordTX extends JournalInternalRecord {

   private final long txID;

   private final long id;

   private final EncodingSupport record;

   public JournalDeleteRecordTX(final long txID, final long id, final EncodingSupport record) {
      this.id = id;

      this.txID = txID;

      this.record = record;
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeByte(JournalImpl.DELETE_RECORD_TX);

      buffer.writeInt(fileID);

      buffer.writeByte(compactCount);

      buffer.writeLong(txID);

      buffer.writeLong(id);

      buffer.writeInt(record != null ? record.getEncodeSize() : 0);

      if (record != null) {
         record.encode(buffer);
      }

      buffer.writeInt(getEncodeSize());
   }

   @Override
   public int getEncodeSize() {
      return JournalImpl.SIZE_DELETE_RECORD_TX + (record != null ? record.getEncodeSize() : 0) + 1;
   }
}
