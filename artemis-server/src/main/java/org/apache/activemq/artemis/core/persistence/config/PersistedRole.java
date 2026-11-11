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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;

public class PersistedRole extends PersistedConfiguration {

   private String username;

   private List<String> roles;

   public PersistedRole() {
   }

   public PersistedRole(String username, List<String> roles) {
      this.username = Objects.requireNonNull(username);
      this.roles = Objects.requireNonNull(roles);
   }

   public String getUsername() {
      return username;
   }

   public List<String> getRoles() {
      return roles;
   }

   @Override
   public int getEncodeSize() {
      int size = 0;
      size += BufferHelper.sizeOfString(username);
      size += DataConstants.SIZE_INT;
      for (String role : roles) {
         size += BufferHelper.sizeOfString(role);
      }
      return size;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeString(username);
      buffer.writeInt(roles.size());
      for (String user : roles) {
         buffer.writeString(user);
      }
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      username = buffer.readString();
      roles = new ArrayList<>();
      int size = buffer.readInt();
      for (int i = 0; i < size; i++) {
         roles.add(buffer.readString());
      }
   }

   @Override
   public String toString() {
      StringBuilder result = new StringBuilder();
      result.append("PersistedRole [storeId=").append(storeId);
      result.append(", username=").append(username);
      result.append(", roles [");
      if (roles != null) {
         for (int i = 0; i < roles.size(); i++) {
            result.append(roles.get(i));
            if (i < roles.size() - 1) {
               result.append(", ");
            }
         }
      }
      result.append("]]");

      return result.toString();
   }

   @Override
   public byte getRecordType() {
      return JournalRecordIds.ROLE_RECORD;
   }

   @Override
   public String getName() {
      return username;
   }
}
