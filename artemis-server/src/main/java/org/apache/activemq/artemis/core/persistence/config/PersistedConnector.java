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

public class PersistedConnector extends PersistedConfiguration {

   private String url;

   private String name;

   public PersistedConnector() {
   }

   public PersistedConnector(String name, String url) {
      this.name = name;
      this.url = url;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   public String getUrl() {
      return url;
   }

   public void setName(String name) {
      this.name = name;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.CONNECTOR_RECORD;
   }

   @Override
   public int getEncodeSize() {
      int size = 0;
      size += BufferHelper.sizeOfString(name);
      size += BufferHelper.sizeOfString(url);
      return size;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeString(name);
      buffer.writeString(url);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readString();
      url = buffer.readString();
   }

   @Override
   public String toString() {
      return "PersistedConnector [storeId=" + storeId +
         ", name=" +
         name +
         ", url=" +
         url +
         "]";
   }
}
