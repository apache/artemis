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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.GroupingInfo;

public class GroupingEncoding implements EncodingSupport, GroupingInfo {

   public long id;

   public SimpleString groupId;

   public SimpleString clusterName;

   public GroupingEncoding(final long id, final SimpleString groupId, final SimpleString clusterName) {
      this.id = id;
      this.groupId = groupId;
      this.clusterName = clusterName;
   }

   public GroupingEncoding() {
   }

   @Override
   public int getEncodeSize() {
      return SimpleString.sizeofString(groupId) + SimpleString.sizeofString(clusterName);
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(groupId);
      buffer.writeSimpleString(clusterName);
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      groupId = buffer.readSimpleString();
      clusterName = buffer.readSimpleString();
   }

   @Override
   public long getId() {
      return id;
   }

   public void setId(final long id) {
      this.id = id;
   }

   @Override
   public SimpleString getGroupId() {
      return groupId;
   }

   @Override
   public SimpleString getClusterName() {
      return clusterName;
   }

   @Override
   public String toString() {
      return "GroupingEncoding [id=" + id + ", groupId=" + groupId + ", clusterName=" + clusterName + "]";
   }
}
