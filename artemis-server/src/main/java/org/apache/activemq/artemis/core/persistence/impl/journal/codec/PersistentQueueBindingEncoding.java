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

import java.util.LinkedList;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.QueueBindingInfo;
import org.apache.activemq.artemis.core.server.impl.QueueConfigurationUtils;
import org.apache.activemq.artemis.utils.DataConstants;

public class PersistentQueueBindingEncoding implements EncodingSupport, QueueBindingInfo {

   private QueueConfiguration config;

   private List<QueueStatusEncoding> queueStatusEncodings;

   public PersistentQueueBindingEncoding() {
   }

   @Override
   public String toString() {
      return "PersistentQueueBindingEncoding [queueConfiguration=" + config + "]";
   }

   public PersistentQueueBindingEncoding(final QueueConfiguration config) {
      this.config = config;
   }

   @Override
   public QueueConfiguration getQueueConfiguration() {
      return config;
   }

   @Override
   public void addQueueStatusEncoding(QueueStatusEncoding status) {
      if (queueStatusEncodings == null) {
         queueStatusEncodings = new LinkedList<>();
      }
      queueStatusEncodings.add(status);
   }

   @Override
   public List<QueueStatusEncoding> getQueueStatusEncodings() {
      return queueStatusEncodings;
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      config = QueueConfiguration.of(buffer.readSimpleString());
      config.setAddress(buffer.readSimpleString());
      config.setFilterString(buffer.readNullableSimpleString());

      String metadata = buffer.readNullableSimpleString().toString();
      if (metadata != null) {
         String[] elements = metadata.split(";");
         for (String element : elements) {
            String[] keyValuePair = element.split("=");
            if (keyValuePair.length == 2) {
               if (keyValuePair[0].equals("user")) {
                  config.setUser(SimpleString.of(keyValuePair[1]));
               }
            }
         }
      }

      config.setAutoCreated(buffer.readBoolean());

      if (buffer.readable()) {
         config.setMaxConsumers(buffer.readInt());
         config.setPurgeOnNoConsumers(buffer.readBoolean());
         config.setRoutingType(RoutingType.getType(buffer.readByte()));
      }

      if (buffer.readable()) {
         config.setExclusive(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setLastValue(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setConfigurationManaged(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setConsumersBeforeDispatch(buffer.readInt());
      }
      if (buffer.readable()) {
         config.setDelayBeforeDispatch(buffer.readLong());
      }
      if (buffer.readable()) {
         config.setLastValueKey(buffer.readNullableSimpleString());
      }
      if (buffer.readable()) {
         config.setNonDestructive(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setGroupRebalance(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setGroupBuckets(buffer.readInt());
      }
      if (buffer.readable()) {
         config.setAutoDelete(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setAutoDeleteDelay(buffer.readLong());
      }
      if (buffer.readable()) {
         config.setAutoDeleteMessageCount(buffer.readLong());
      }
      if (buffer.readable()) {
         config.setGroupFirstKey(buffer.readNullableSimpleString());
      }
      if (buffer.readable()) {
         config.setRingSize(buffer.readLong());
      }
      if (buffer.readable()) {
         config.setEnabled(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setGroupRebalancePauseDispatch(buffer.readBoolean());
      }
      if (buffer.readable()) {
         config.setInternal(buffer.readBoolean());
      }
      QueueConfigurationUtils.applyStaticDefaults(config);
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(config.getName());
      buffer.writeSimpleString(config.getAddress());
      buffer.writeNullableSimpleString(config.getFilterString());
      buffer.writeNullableSimpleString(createMetadata());
      buffer.writeBoolean(config.isAutoCreated());
      buffer.writeInt(config.getMaxConsumers());
      buffer.writeBoolean(config.isPurgeOnNoConsumers());
      buffer.writeByte(config.getRoutingType().getType());
      buffer.writeBoolean(config.isExclusive());
      buffer.writeBoolean(config.isLastValue());
      buffer.writeBoolean(config.isConfigurationManaged());
      buffer.writeInt(config.getConsumersBeforeDispatch());
      buffer.writeLong(config.getDelayBeforeDispatch());
      buffer.writeNullableSimpleString(config.getLastValueKey());
      buffer.writeBoolean(config.isNonDestructive());
      buffer.writeBoolean(config.isGroupRebalance());
      buffer.writeInt(config.getGroupBuckets());
      buffer.writeBoolean(config.isAutoDelete());
      buffer.writeLong(config.getAutoDeleteDelay());
      buffer.writeLong(config.getAutoDeleteMessageCount());
      buffer.writeNullableSimpleString(config.getGroupFirstKey());
      buffer.writeLong(config.getRingSize());
      buffer.writeBoolean(config.isEnabled());
      buffer.writeBoolean(config.isGroupRebalancePauseDispatch());
      buffer.writeBoolean(config.isInternal());
   }

   @Override
   public int getEncodeSize() {
      return SimpleString.sizeofString(config.getName()) + SimpleString.sizeofString(config.getAddress()) +
         SimpleString.sizeofNullableString(config.getFilterString()) + DataConstants.SIZE_BOOLEAN +
         SimpleString.sizeofNullableString(createMetadata()) +
         DataConstants.SIZE_INT +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BYTE +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_INT +
         DataConstants.SIZE_LONG +
         SimpleString.sizeofNullableString(config.getLastValueKey()) +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_INT +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_LONG +
         DataConstants.SIZE_LONG +
         SimpleString.sizeofNullableString(config.getGroupFirstKey()) +
         DataConstants.SIZE_LONG +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BOOLEAN;
   }

   private SimpleString createMetadata() {
      StringBuilder metadata = new StringBuilder();
      if (config.getUser() != null) {
         metadata.append("user=").append(config.getUser()).append(";");
      }
      return SimpleString.of(metadata.toString());
   }
}
