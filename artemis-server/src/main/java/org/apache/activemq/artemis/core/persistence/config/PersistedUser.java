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
package org.apache.activemq.artemis.core.persistence.config;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.utils.BufferHelper;

public class PersistedUser extends PersistedConfiguration {

   private String username;

   private String password;

   public PersistedUser() {
   }

   public PersistedUser(String username, String password) {
      this.username = username;
      this.password = password;
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.USER_RECORD;
   }

   @Override
   public String getName() {
      return username;
   }

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }

   @Override
   public int getEncodeSize() {
      int size = 0;
      size += BufferHelper.sizeOfString(username);
      size += BufferHelper.sizeOfString(password);
      return size;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeString(username);
      buffer.writeString(password);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      username = buffer.readString();
      password = buffer.readString();
   }

   @Override
   public String toString() {
      return "PersistedUser [storeId=" + storeId +
         ", username=" +
         username +
         ", password=****" +
         "]";
   }
}
