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

import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.BufferHelper;

public class SessionBindingQueryResponseMessage_V4 extends SessionBindingQueryResponseMessage_V3 {

   protected boolean defaultPurgeOnNoConsumers;

   protected int defaultMaxConsumers;

   protected Boolean defaultExclusive;

   protected Boolean defaultLastValue;

   protected SimpleString defaultLastValueKey;

   protected Boolean defaultNonDestructive;

   protected Integer defaultConsumersBeforeDispatch;

   protected Long defaultDelayBeforeDispatch;

   public SessionBindingQueryResponseMessage_V4(final boolean exists,
                                                final List<SimpleString> queueNames,
                                                final boolean autoCreateQueues,
                                                final boolean autoCreateAddresses,
                                                final boolean defaultPurgeOnNoConsumers,
                                                final int defaultMaxConsumers,
                                                final Boolean defaultExclusive,
                                                final Boolean defaultLastValue,
                                                final SimpleString defaultLastValueKey,
                                                final Boolean defaultNonDestructive,
                                                final Integer defaultConsumersBeforeDispatch,
                                                final Long defaultDelayBeforeDispatch) {
      super(SESS_BINDINGQUERY_RESP_V4);

      this.exists = exists;

      this.queueNames = queueNames;

      this.autoCreateQueues = autoCreateQueues;

      this.autoCreateAddresses = autoCreateAddresses;

      this.defaultPurgeOnNoConsumers = defaultPurgeOnNoConsumers;

      this.defaultMaxConsumers = defaultMaxConsumers;

      this.defaultExclusive = defaultExclusive;

      this.defaultLastValue = defaultLastValue;

      this.defaultLastValueKey = defaultLastValueKey;

      this.defaultNonDestructive = defaultNonDestructive;

      this.defaultConsumersBeforeDispatch = defaultConsumersBeforeDispatch;

      this.defaultDelayBeforeDispatch = defaultDelayBeforeDispatch;
   }

   public SessionBindingQueryResponseMessage_V4() {
      super(SESS_BINDINGQUERY_RESP_V4);
   }

   public SessionBindingQueryResponseMessage_V4(byte v) {
      super(v);
   }

   public boolean isDefaultPurgeOnNoConsumers() {
      return defaultPurgeOnNoConsumers;
   }

   public int getDefaultMaxConsumers() {
      return defaultMaxConsumers;
   }

   public Boolean isDefaultExclusive() {
      return defaultExclusive;
   }

   public Boolean isDefaultLastValue() {
      return defaultLastValue;
   }

   public SimpleString getDefaultLastValueKey() {
      return defaultLastValueKey;
   }

   public Boolean isDefaultNonDestructive() {
      return defaultNonDestructive;
   }

   public Integer getDefaultConsumersBeforeDispatch() {
      return defaultConsumersBeforeDispatch;
   }

   public Long getDefaultDelayBeforeDispatch() {
      return defaultDelayBeforeDispatch;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeBoolean(defaultPurgeOnNoConsumers);
      buffer.writeInt(defaultMaxConsumers);
      BufferHelper.writeNullableBoolean(buffer, defaultExclusive);
      BufferHelper.writeNullableBoolean(buffer, defaultLastValue);
      buffer.writeNullableSimpleString(defaultLastValueKey);
      BufferHelper.writeNullableBoolean(buffer, defaultNonDestructive);
      BufferHelper.writeNullableInteger(buffer, defaultConsumersBeforeDispatch);
      BufferHelper.writeNullableLong(buffer, defaultDelayBeforeDispatch);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      defaultPurgeOnNoConsumers = buffer.readBoolean();
      defaultMaxConsumers = buffer.readInt();
      if (buffer.readableBytes() > 0) {
         defaultExclusive = BufferHelper.readNullableBoolean(buffer);
         defaultLastValue = BufferHelper.readNullableBoolean(buffer);
      }
      if (buffer.readableBytes() > 0) {
         defaultLastValueKey = buffer.readNullableSimpleString();
         defaultNonDestructive = BufferHelper.readNullableBoolean(buffer);
         defaultConsumersBeforeDispatch = BufferHelper.readNullableInteger(buffer);
         defaultDelayBeforeDispatch = BufferHelper.readNullableLong(buffer);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), defaultPurgeOnNoConsumers, defaultMaxConsumers, defaultExclusive,
                          defaultLastValue, defaultLastValueKey, defaultNonDestructive, defaultConsumersBeforeDispatch,
                          defaultDelayBeforeDispatch);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", defaultPurgeOnNoConsumers=" + defaultPurgeOnNoConsumers);
      sb.append(", defaultMaxConsumers=" + defaultMaxConsumers);
      sb.append(", defaultExclusive=" + defaultExclusive);
      sb.append(", defaultLastValue=" + defaultLastValue);
      sb.append(", defaultLastValueKey=" + defaultLastValueKey);
      sb.append(", defaultNonDestructive=" + defaultNonDestructive);
      sb.append(", defaultConsumersBeforeDispatch=" + defaultConsumersBeforeDispatch);
      sb.append(", defaultDelayBeforeDispatch=" + defaultDelayBeforeDispatch);
      return sb.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionBindingQueryResponseMessage_V4 other)) {
         return false;
      }
      return defaultPurgeOnNoConsumers == other.defaultPurgeOnNoConsumers &&
             defaultMaxConsumers == other.defaultMaxConsumers &&
             Objects.equals(defaultExclusive, other.defaultExclusive) &&
             Objects.equals(defaultLastValue, other.defaultLastValue) &&
             Objects.equals(defaultLastValueKey, other.defaultLastValueKey) &&
             Objects.equals(defaultNonDestructive, other.defaultNonDestructive) &&
             Objects.equals(defaultConsumersBeforeDispatch, other.defaultConsumersBeforeDispatch) &&
             Objects.equals(defaultDelayBeforeDispatch, other.defaultDelayBeforeDispatch);
   }
}
