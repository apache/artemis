/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.persistence.config;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.utils.BufferHelper;

public class PersistedKeyValuePair extends PersistedConfiguration {

   private String mapId;

   private String key;

   private String value;

   public PersistedKeyValuePair() {
   }

   public PersistedKeyValuePair(String mapId, String key, String value) {
      this.mapId = mapId;
      this.key = key;
      this.value = value;
   }

   public String getMapId() {
      return mapId;
   }

   public String getKey() {
      return key;
   }

   public String getValue() {
      return value;
   }

   @Override
   public String getName() {
      return mapId;
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.KEY_VALUE_PAIR_RECORD;
   }

   @Override
   public int getEncodeSize() {
      int size = 0;
      size += BufferHelper.sizeOfString(mapId);
      size += BufferHelper.sizeOfString(key);
      size += BufferHelper.sizeOfString(value);
      return size;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeString(mapId);
      buffer.writeString(key);
      buffer.writeString(value);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      mapId = buffer.readString();
      key = buffer.readString();
      value = buffer.readString();
   }

   @Override
   public String toString() {
      return "PersistedKeyValuePair [storeId=" + storeId +
         ", mapId=" + mapId +
         ", key=" + key +
         ", value=" + value +
         "]";
   }
}
