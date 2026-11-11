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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.utils.BufferHelper;

public class CreateSharedQueueMessage_V2 extends CreateSharedQueueMessage {

   private RoutingType routingType;
   private Integer maxConsumers;
   private Boolean purgeOnNoConsumers;
   private Boolean exclusive;
   private Boolean groupRebalance;
   private Boolean groupRebalancePauseDispatch;
   private Integer groupBuckets;
   private SimpleString groupFirstKey;
   private Boolean lastValue;
   private SimpleString lastValueKey;
   private Boolean nonDestructive;
   private Integer consumersBeforeDispatch;
   private Long delayBeforeDispatch;
   private Boolean autoDelete;
   private Long autoDeleteDelay;
   private Long autoDeleteMessageCount;
   private Long ringSize;
   private Boolean enabled;

   public CreateSharedQueueMessage_V2(final QueueConfiguration queueConfiguration, boolean requiresResponse) {
      this(
         queueConfiguration.getAddress(),
         queueConfiguration.getName(),
         queueConfiguration.getRoutingType(),
         queueConfiguration.getFilterString(),
         queueConfiguration.isDurable(),
         queueConfiguration.getMaxConsumers(),
         queueConfiguration.isPurgeOnNoConsumers(),
         queueConfiguration.isExclusive(),
         queueConfiguration.isGroupRebalance(),
         queueConfiguration.isGroupRebalancePauseDispatch(),
         queueConfiguration.getGroupBuckets(),
         queueConfiguration.getGroupFirstKey(),
         queueConfiguration.isLastValue(),
         queueConfiguration.getLastValueKey(),
         queueConfiguration.isNonDestructive(),
         queueConfiguration.getConsumersBeforeDispatch(),
         queueConfiguration.getDelayBeforeDispatch(),
         queueConfiguration.isAutoDelete(),
         queueConfiguration.getAutoDeleteDelay(),
         queueConfiguration.getAutoDeleteMessageCount(),
         queueConfiguration.getRingSize(),
         queueConfiguration.isEnabled(),
         requiresResponse
      );
   }

   public CreateSharedQueueMessage_V2(final SimpleString address,
                                      final SimpleString queueName,
                                      final RoutingType routingType,
                                      final SimpleString filterString,
                                      final boolean durable,
                                      final Integer maxConsumers,
                                      final Boolean purgeOnNoConsumers,
                                      final Boolean exclusive,
                                      final Boolean groupRebalance,
                                      final Boolean groupRebalancePauseDispatch,
                                      final Integer groupBuckets,
                                      final SimpleString groupFirstKey,
                                      final Boolean lastValue,
                                      final SimpleString lastValueKey,
                                      final Boolean nonDestructive,
                                      final Integer consumersBeforeDispatch,
                                      final Long delayBeforeDispatch,
                                      final Boolean autoDelete,
                                      final Long autoDeleteDelay,
                                      final Long autoDeleteMessageCount,
                                      final Long ringSize,
                                      final Boolean enabled,
                                      final boolean requiresResponse) {
      this();

      this.address = address;
      this.queueName = queueName;
      this.filterString = filterString;
      this.durable = durable;
      this.routingType = routingType;
      this.maxConsumers = maxConsumers;
      this.purgeOnNoConsumers = purgeOnNoConsumers;
      this.exclusive = exclusive;
      this.groupRebalance = groupRebalance;
      this.groupRebalancePauseDispatch = groupRebalancePauseDispatch;
      this.groupBuckets = groupBuckets;
      this.groupFirstKey = groupFirstKey;
      this.lastValue = lastValue;
      this.lastValueKey = lastValueKey;
      this.nonDestructive = nonDestructive;
      this.consumersBeforeDispatch = consumersBeforeDispatch;
      this.delayBeforeDispatch = delayBeforeDispatch;
      this.autoDelete = autoDelete;
      this.autoDeleteDelay = autoDeleteDelay;
      this.autoDeleteMessageCount = autoDeleteMessageCount;
      this.ringSize = ringSize;
      this.enabled = enabled;
      this.requiresResponse = requiresResponse;
   }

   public CreateSharedQueueMessage_V2() {
      super(CREATE_SHARED_QUEUE_V2);
   }

   public RoutingType getRoutingType() {
      return routingType;
   }

   public void setRoutingType(RoutingType routingType) {
      this.routingType = routingType;
   }

   public Integer getMaxConsumers() {
      return maxConsumers;
   }

   public void setMaxConsumers(Integer maxConsumers) {
      this.maxConsumers = maxConsumers;
   }

   public Boolean isPurgeOnNoConsumers() {
      return purgeOnNoConsumers;
   }

   public void setPurgeOnNoConsumers(Boolean purgeOnNoConsumers) {
      this.purgeOnNoConsumers = purgeOnNoConsumers;
   }

   public Boolean isExclusive() {
      return exclusive;
   }

   public void setExclusive(Boolean exclusive) {
      this.exclusive = exclusive;
   }

   public Boolean isLastValue() {
      return lastValue;
   }

   public void setLastValue(Boolean lastValue) {
      this.lastValue = lastValue;
   }

   public SimpleString getLastValueKey() {
      return lastValueKey;
   }

   public void setLastValueKey(SimpleString lastValueKey) {
      this.lastValueKey = lastValueKey;
   }

   public Boolean isNonDestructive() {
      return nonDestructive;
   }

   public void setNonDestructive(Boolean nonDestructive) {
      this.nonDestructive = nonDestructive;
   }

   public Integer getConsumersBeforeDispatch() {
      return consumersBeforeDispatch;
   }

   public void setConsumersBeforeDispatch(Integer consumersBeforeDispatch) {
      this.consumersBeforeDispatch = consumersBeforeDispatch;
   }

   public Long getDelayBeforeDispatch() {
      return delayBeforeDispatch;
   }

   public void setDelayBeforeDispatch(Long delayBeforeDispatch) {
      this.delayBeforeDispatch = delayBeforeDispatch;
   }

   public Boolean isGroupRebalance() {
      return groupRebalance;
   }

   public void setGroupRebalance(Boolean groupRebalance) {
      this.groupRebalance = groupRebalance;
   }

   public Boolean isGroupRebalancePauseDispatch() {
      return groupRebalancePauseDispatch;
   }

   public void setGroupRebalancePauseDispatch(Boolean groupRebalancePauseDispatch) {
      this.groupRebalancePauseDispatch = groupRebalancePauseDispatch;
   }

   public Integer getGroupBuckets() {
      return groupBuckets;
   }

   public void setGroupBuckets(Integer groupBuckets) {
      this.groupBuckets = groupBuckets;
   }

   public SimpleString getGroupFirstKey() {
      return groupFirstKey;
   }

   public void setGroupBuckets(SimpleString groupFirstKey) {
      this.groupFirstKey = groupFirstKey;
   }

   public Boolean isAutoDelete() {
      return autoDelete;
   }

   public void setAutoDelete(Boolean autoDelete) {
      this.autoDelete = autoDelete;
   }

   public Long getAutoDeleteDelay() {
      return autoDeleteDelay;
   }

   public void setAutoDeleteDelay(Long autoDeleteDelay) {
      this.autoDeleteDelay = autoDeleteDelay;
   }

   public Long getAutoDeleteMessageCount() {
      return autoDeleteMessageCount;
   }

   public void setAutoDeleteMessageCount(Long autoDeleteMessageCount) {
      this.autoDeleteMessageCount = autoDeleteMessageCount;
   }

   public Long getRingSize() {
      return ringSize;
   }

   public void setRingSize(Long ringSize) {
      this.ringSize = ringSize;
   }

   public Boolean isEnabled() {
      return enabled;
   }

   public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
   }

   public QueueConfiguration toQueueConfiguration() {
      return QueueConfiguration.of(queueName)
         .setAddress(address)
         .setDurable(durable)
         .setRoutingType(routingType)
         .setExclusive(exclusive)
         .setGroupRebalance(groupRebalance)
         .setGroupRebalancePauseDispatch(groupRebalancePauseDispatch)
         .setNonDestructive(nonDestructive)
         .setLastValue(lastValue)
         .setFilterString(filterString)
         .setMaxConsumers(maxConsumers)
         .setPurgeOnNoConsumers(purgeOnNoConsumers)
         .setConsumersBeforeDispatch(consumersBeforeDispatch)
         .setDelayBeforeDispatch(delayBeforeDispatch)
         .setGroupBuckets(groupBuckets)
         .setGroupFirstKey(groupFirstKey)
         .setLastValueKey(lastValueKey)
         .setAutoDelete(autoDelete)
         .setAutoDeleteDelay(autoDeleteDelay)
         .setAutoDeleteMessageCount(autoDeleteMessageCount)
         .setRingSize(ringSize)
         .setEnabled(enabled);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", address=" + address);
      sb.append(", queueName=" + queueName);
      sb.append(", filterString=" + filterString);
      sb.append(", durable=" + durable);
      sb.append(", routingType=" + routingType);
      sb.append(", maxConsumers=" + maxConsumers);
      sb.append(", purgeOnNoConsumers=" + purgeOnNoConsumers);
      sb.append(", exclusive=" + exclusive);
      sb.append(", groupRebalance=" + groupRebalance);
      sb.append(", groupRebalancePauseDispatch=" + groupRebalancePauseDispatch);
      sb.append(", groupBuckets=" + groupBuckets);
      sb.append(", groupFirstKey=" + groupFirstKey);
      sb.append(", lastValue=" + lastValue);
      sb.append(", lastValueKey=" + lastValueKey);
      sb.append(", nonDestructive=" + nonDestructive);
      sb.append(", consumersBeforeDispatch=" + consumersBeforeDispatch);
      sb.append(", delayBeforeDispatch=" + delayBeforeDispatch);
      sb.append(", autoDelete=" + autoDelete);
      sb.append(", autoDeleteDelay=" + autoDeleteDelay);
      sb.append(", autoDeleteMessageCount=" + autoDeleteMessageCount);
      sb.append(", ringSize=" + ringSize);
      sb.append(", enabled=" + enabled);
      sb.append(", requiresResponse=" + requiresResponse);
      return sb.toString();
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(address);
      buffer.writeSimpleString(queueName);
      buffer.writeNullableSimpleString(filterString);
      buffer.writeBoolean(durable);
      buffer.writeByte(routingType == null ? -1 : routingType.getType());
      buffer.writeBoolean(requiresResponse);
      BufferHelper.writeNullableInteger(buffer, maxConsumers);
      BufferHelper.writeNullableBoolean(buffer, purgeOnNoConsumers);
      BufferHelper.writeNullableBoolean(buffer, exclusive);
      BufferHelper.writeNullableBoolean(buffer, lastValue);
      buffer.writeNullableSimpleString(lastValueKey);
      BufferHelper.writeNullableBoolean(buffer, nonDestructive);
      BufferHelper.writeNullableInteger(buffer, consumersBeforeDispatch);
      BufferHelper.writeNullableLong(buffer, delayBeforeDispatch);
      BufferHelper.writeNullableBoolean(buffer, groupRebalance);
      BufferHelper.writeNullableInteger(buffer, groupBuckets);
      BufferHelper.writeNullableBoolean(buffer, autoDelete);
      BufferHelper.writeNullableLong(buffer, autoDeleteDelay);
      BufferHelper.writeNullableLong(buffer, autoDeleteMessageCount);
      buffer.writeNullableSimpleString(groupFirstKey);
      BufferHelper.writeNullableLong(buffer, ringSize);
      BufferHelper.writeNullableBoolean(buffer, enabled);
      BufferHelper.writeNullableBoolean(buffer, groupRebalancePauseDispatch);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      address = buffer.readSimpleString();
      queueName = buffer.readSimpleString();
      filterString = buffer.readNullableSimpleString();
      durable = buffer.readBoolean();
      routingType = RoutingType.getType(buffer.readByte());
      requiresResponse = buffer.readBoolean();
      if (buffer.readableBytes() > 0) {
         maxConsumers = BufferHelper.readNullableInteger(buffer);
         purgeOnNoConsumers = BufferHelper.readNullableBoolean(buffer);
         exclusive = BufferHelper.readNullableBoolean(buffer);
         lastValue = BufferHelper.readNullableBoolean(buffer);
      }
      if (buffer.readableBytes() > 0) {
         lastValueKey = buffer.readNullableSimpleString();
         nonDestructive = BufferHelper.readNullableBoolean(buffer);
         consumersBeforeDispatch = BufferHelper.readNullableInteger(buffer);
         delayBeforeDispatch = BufferHelper.readNullableLong(buffer);
         groupRebalance = BufferHelper.readNullableBoolean(buffer);
         groupBuckets = BufferHelper.readNullableInteger(buffer);
         autoDelete = BufferHelper.readNullableBoolean(buffer);
         autoDeleteDelay = BufferHelper.readNullableLong(buffer);
         autoDeleteMessageCount = BufferHelper.readNullableLong(buffer);
      }
      if (buffer.readableBytes() > 0) {
         groupFirstKey = buffer.readNullableSimpleString();
      }
      if (buffer.readableBytes() > 0) {
         ringSize = buffer.readNullableLong();
      }
      if (buffer.readableBytes() > 0) {
         enabled = buffer.readNullableBoolean();
      }
      if (buffer.readableBytes() > 0) {
         groupRebalancePauseDispatch = buffer.readNullableBoolean();
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), address, filterString, queueName, durable, routingType.getType(),
                          requiresResponse, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance,
                          groupRebalancePauseDispatch, groupBuckets, groupFirstKey, lastValue, lastValueKey,
                          nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay,
                          autoDeleteMessageCount, ringSize, enabled);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof CreateSharedQueueMessage_V2 other)) {
         return false;
      }

      return durable == other.durable &&
             requiresResponse == other.requiresResponse &&
             routingType == other.routingType &&
             Objects.equals(address, other.address) &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(queueName, other.queueName) &&
             Objects.equals(maxConsumers, other.maxConsumers) &&
             Objects.equals(purgeOnNoConsumers, other.purgeOnNoConsumers) &&
             Objects.equals(exclusive, other.exclusive) &&
             Objects.equals(groupRebalance, other.groupRebalance) &&
             Objects.equals(groupRebalancePauseDispatch, other.groupRebalancePauseDispatch) &&
             Objects.equals(groupBuckets, other.groupBuckets) &&
             Objects.equals(groupFirstKey, other.groupFirstKey) &&
             Objects.equals(lastValue, other.lastValue) &&
             Objects.equals(lastValueKey, other.lastValueKey) &&
             Objects.equals(nonDestructive, other.nonDestructive) &&
             Objects.equals(consumersBeforeDispatch, other.consumersBeforeDispatch) &&
             Objects.equals(delayBeforeDispatch, other.delayBeforeDispatch) &&
             Objects.equals(autoDelete, other.autoDelete) &&
             Objects.equals(autoDeleteDelay, other.autoDeleteDelay) &&
             Objects.equals(autoDeleteMessageCount, other.autoDeleteMessageCount) &&
             Objects.equals(ringSize, other.ringSize) &&
             Objects.equals(enabled, other.enabled);
   }
}
