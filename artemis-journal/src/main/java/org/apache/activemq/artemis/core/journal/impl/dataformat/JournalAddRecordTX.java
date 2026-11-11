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
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;

public class JournalAddRecordTX extends JournalInternalRecord {

   private final long txID;

   private final long id;

   protected final Persister persister;

   protected final Object record;

   private final byte recordType;

   private final boolean add;

   public JournalAddRecordTX(final boolean add,
                             final long txID,
                             final long id,
                             final byte recordType,
                             final Persister persister,
                             Object record) {

      this.txID = txID;

      this.id = id;

      this.persister = persister;

      this.record = record;

      this.recordType = recordType;

      this.add = add;
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      if (add) {
         buffer.writeByte(JournalImpl.ADD_RECORD_TX);
      } else {
         buffer.writeByte(JournalImpl.UPDATE_RECORD_TX);
      }

      buffer.writeInt(fileID);

      buffer.writeByte(compactCount);

      buffer.writeLong(txID);

      buffer.writeLong(id);

      int persisterEncodeSize = persister.getEncodeSize(record);
      int encodeSize = getInternalEncodeSize(persisterEncodeSize);

      buffer.writeInt(persisterEncodeSize);

      buffer.writeByte(recordType);

      persister.encode(buffer, record);

      // AMQP Persister may save encoding between getEcodeSize and .encode(). After encode it may release the encoding
      buffer.writeInt(encodeSize);
   }

   @Override
   public int getEncodeSize() {
      return getInternalEncodeSize(persister.getEncodeSize(record));
   }

   private int getInternalEncodeSize(int persisterEncodeSize) {
      return JournalImpl.SIZE_ADD_RECORD_TX + persisterEncodeSize + 1;
   }
}
