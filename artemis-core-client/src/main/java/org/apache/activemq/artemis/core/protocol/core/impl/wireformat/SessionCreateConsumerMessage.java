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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;

public class SessionCreateConsumerMessage extends QueueAbstractPacket {

   private long id;

   private SimpleString filterString;

   private int priority;

   private boolean browseOnly;

   private boolean requiresResponse;

   public SessionCreateConsumerMessage(final long id,
                                       final SimpleString queueName,
                                       final SimpleString filterString,
                                       final int priority,
                                       final boolean browseOnly,
                                       final boolean requiresResponse) {
      super(SESS_CREATECONSUMER);

      this.id = id;
      this.queueName = queueName;
      this.filterString = filterString;
      this.priority = priority;
      this.browseOnly = browseOnly;
      this.requiresResponse = requiresResponse;
   }

   public SessionCreateConsumerMessage() {
      super(SESS_CREATECONSUMER);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", queueName=" + queueName);
      sb.append(", filterString=" + filterString);
      sb.append(", id=" + id);
      sb.append(", browseOnly=" + browseOnly);
      sb.append(", requiresResponse=" + requiresResponse);
      sb.append(", priority=" + priority);
      return sb.toString();
   }

   public long getID() {
      return id;
   }

   public SimpleString getFilterString() {
      return filterString;
   }

   public int getPriority() {
      return priority;
   }

   public boolean isBrowseOnly() {
      return browseOnly;
   }

   @Override
   public boolean isRequiresResponse() {
      return requiresResponse;
   }

   public void setQueueName(SimpleString queueName) {
      this.queueName = queueName;
   }

   public void setFilterString(SimpleString filterString) {
      this.filterString = filterString;
   }

   public void setPriority(byte priority) {
      this.priority = priority;
   }

   public void setBrowseOnly(boolean browseOnly) {
      this.browseOnly = browseOnly;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer, final CoreRemotingConnection coreRemotingConnection) {
      buffer.writeLong(id);
      buffer.writeSimpleString(queueName);
      buffer.writeNullableSimpleString(filterString);
      buffer.writeBoolean(browseOnly);
      buffer.writeBoolean(requiresResponse);
      //Priority Support added in 2.7.0
      if (coreRemotingConnection.isVersionSupportConsumerPriority()) {
         buffer.writeInt(priority);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      id = buffer.readLong();
      queueName = buffer.readSimpleString();
      filterString = buffer.readNullableSimpleString();
      browseOnly = buffer.readBoolean();
      requiresResponse = buffer.readBoolean();
      //Priority Support Added in 2.7.0
      if (buffer.readableBytes() > 0) {
         priority = buffer.readInt();
      } else {
         priority = ActiveMQDefaultConfiguration.getDefaultConsumerPriority();
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), browseOnly, filterString, id, priority, queueName, requiresResponse);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionCreateConsumerMessage other)) {
         return false;
      }

      return browseOnly == other.browseOnly &&
             Objects.equals(filterString, other.filterString) &&
             priority == other.priority &&
             id == other.id &&
             Objects.equals(queueName, other.queueName) &&
             requiresResponse == other.requiresResponse;
   }
}
