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
package org.apache.activemq.artemis.core.config;

import java.io.Serializable;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;

@Deprecated
public class CoreQueueConfiguration implements Serializable {

   private static final long serialVersionUID = 650404974977490254L;

   private String address = null;

   private String name = null;

   private String filterString = null;

   private boolean durable = true;

   private String user = null;

   private Boolean exclusive;

   private Boolean groupRebalance;

   private Integer groupBuckets;

   private String groupFirstKey;

   private Boolean lastValue;

   private String lastValueKey;

   private Boolean nonDestructive;

   private Integer maxConsumers;

   private Integer consumersBeforeDispatch;

   private Long delayBeforeDispatch;

   private Boolean enabled;

   private Long ringSize = ActiveMQDefaultConfiguration.getDefaultRingSize();

   private Boolean purgeOnNoConsumers = ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers();

   private RoutingType routingType = ActiveMQDefaultConfiguration.getDefaultRoutingType();

   public CoreQueueConfiguration() {
   }

   public String getAddress() {
      return address;
   }

   public String getName() {
      return name;
   }

   public String getFilterString() {
      return filterString;
   }

   public boolean isDurable() {
      return durable;
   }

   public String getUser() {
      return user;
   }

   public Boolean isExclusive() {
      return exclusive;
   }

   public Boolean isGroupRebalance() {
      return groupRebalance;
   }

   public Integer getGroupBuckets() {
      return groupBuckets;
   }

   public String getGroupFirstKey() {
      return groupFirstKey;
   }

   public Boolean isLastValue() {
      return lastValue;
   }

   public String getLastValueKey() {
      return lastValueKey;
   }

   public Boolean isNonDestructive() {
      return nonDestructive;
   }

   public Integer getConsumersBeforeDispatch() {
      return consumersBeforeDispatch;
   }

   public Long getDelayBeforeDispatch() {
      return delayBeforeDispatch;
   }

   public Long getRingSize() {
      return ringSize;
   }

   public Boolean isEnabled() {
      return enabled;
   }

   public QueueConfiguration toQueueConfiguration() {
      return QueueConfiguration.of(this.getName())
         .setAddress(this.getAddress())
         .setDurable(this.isDurable())
         .setRoutingType(this.getRoutingType())
         .setExclusive(this.isExclusive())
         .setRingSize(this.getRingSize())
         .setGroupRebalance(this.isGroupRebalance())
         .setNonDestructive(this.isNonDestructive())
         .setLastValue(this.isLastValue())
         .setFilterString(this.getFilterString())
         .setMaxConsumers(this.getMaxConsumers())
         .setPurgeOnNoConsumers(this.getPurgeOnNoConsumers())
         .setConsumersBeforeDispatch(this.getConsumersBeforeDispatch())
         .setDelayBeforeDispatch(this.getDelayBeforeDispatch())
         .setGroupBuckets(this.getGroupBuckets())
         .setGroupFirstKey(this.getGroupFirstKey())
         .setUser(this.getUser())
         .setLastValueKey(this.getLastValueKey())
         .setEnabled(this.isEnabled());
   }

   public static CoreQueueConfiguration fromQueueConfiguration(QueueConfiguration queueConfiguration) {
      return new CoreQueueConfiguration()
         .setAddress(Objects.toString(queueConfiguration.getAddress(), null))
         .setName(Objects.toString(queueConfiguration.getName(), null))
         .setFilterString(Objects.toString(queueConfiguration.getFilterString(), null))
         .setDurable(Objects.requireNonNullElse(queueConfiguration.isDurable(), true))
         .setUser(Objects.toString(queueConfiguration.getUser(), null))
         .setExclusive(queueConfiguration.isExclusive())
         .setGroupRebalance(queueConfiguration.isGroupRebalance())
         .setGroupBuckets(queueConfiguration.getGroupBuckets())
         .setGroupFirstKey(Objects.toString(queueConfiguration.getGroupFirstKey(), null))
         .setLastValue(queueConfiguration.isLastValue())
         .setLastValueKey(Objects.toString(queueConfiguration.getLastValueKey(), null))
         .setNonDestructive(queueConfiguration.isNonDestructive())
         .setMaxConsumers(queueConfiguration.getMaxConsumers())
         .setConsumersBeforeDispatch(queueConfiguration.getConsumersBeforeDispatch())
         .setDelayBeforeDispatch(queueConfiguration.getDelayBeforeDispatch())
         .setRingSize(Objects.requireNonNullElse(queueConfiguration.getRingSize(), ActiveMQDefaultConfiguration.getDefaultRingSize()))
         .setEnabled(Objects.requireNonNullElse(queueConfiguration.isEnabled(), ActiveMQDefaultConfiguration.getDefaultEnabled()))
         .setPurgeOnNoConsumers(Objects.requireNonNullElse(queueConfiguration.isPurgeOnNoConsumers(), ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers()))
         .setRoutingType(Objects.requireNonNullElse(queueConfiguration.getRoutingType(), ActiveMQDefaultConfiguration.getDefaultRoutingType()));
   }

   public CoreQueueConfiguration setAddress(final String address) {
      this.address = address;
      return this;
   }

   public CoreQueueConfiguration setName(final String name) {
      this.name = name;
      return this;
   }

   public CoreQueueConfiguration setFilterString(final String filterString) {
      this.filterString = filterString;
      return this;
   }

   public CoreQueueConfiguration setDurable(final boolean durable) {
      this.durable = durable;
      return this;
   }

   public CoreQueueConfiguration setMaxConsumers(Integer maxConsumers) {
      this.maxConsumers = maxConsumers;
      return this;
   }

   public CoreQueueConfiguration setConsumersBeforeDispatch(Integer consumersBeforeDispatch) {
      this.consumersBeforeDispatch = consumersBeforeDispatch;
      return this;
   }

   public CoreQueueConfiguration setDelayBeforeDispatch(Long delayBeforeDispatch) {
      this.delayBeforeDispatch = delayBeforeDispatch;
      return this;
   }

   public CoreQueueConfiguration setRingSize(Long ringSize) {
      this.ringSize = ringSize;
      return this;
   }

   public CoreQueueConfiguration setEnabled(Boolean enabled) {
      this.enabled = enabled;
      return this;
   }

   public CoreQueueConfiguration setPurgeOnNoConsumers(Boolean purgeOnNoConsumers) {
      this.purgeOnNoConsumers = purgeOnNoConsumers;
      return this;
   }

   public CoreQueueConfiguration setUser(String user) {
      this.user = user;
      return this;
   }

   public CoreQueueConfiguration setExclusive(Boolean exclusive) {
      this.exclusive = exclusive;
      return this;
   }

   public CoreQueueConfiguration setGroupRebalance(Boolean groupRebalance) {
      this.groupRebalance = groupRebalance;
      return this;
   }

   public CoreQueueConfiguration setGroupBuckets(Integer groupBuckets) {
      this.groupBuckets = groupBuckets;
      return this;
   }

   public CoreQueueConfiguration setGroupFirstKey(String groupFirstKey) {
      this.groupFirstKey = groupFirstKey;
      return this;
   }

   public CoreQueueConfiguration setLastValue(Boolean lastValue) {
      this.lastValue = lastValue;
      return this;
   }

   public CoreQueueConfiguration setLastValueKey(String lastValueKey) {
      this.lastValueKey = lastValueKey;
      return this;
   }

   public CoreQueueConfiguration setNonDestructive(Boolean nonDestructive) {
      this.nonDestructive = nonDestructive;
      return this;
   }

   public boolean getPurgeOnNoConsumers() {
      return purgeOnNoConsumers;
   }

   public Integer getMaxConsumers() {
      return maxConsumers;
   }

   public RoutingType getRoutingType() {
      return routingType;
   }

   public CoreQueueConfiguration setRoutingType(RoutingType routingType) {
      this.routingType = routingType;
      return this;
   }

   @Override
   public int hashCode() {
      return Objects.hash(address, durable, filterString, name, maxConsumers, purgeOnNoConsumers, exclusive,
                          groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive,
                          consumersBeforeDispatch, delayBeforeDispatch, routingType, ringSize, enabled);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof CoreQueueConfiguration other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             durable == other.durable &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(name, other.name) &&
             Objects.equals(maxConsumers, other.maxConsumers) &&
             Objects.equals(purgeOnNoConsumers, other.purgeOnNoConsumers) &&
             Objects.equals(ringSize, other.ringSize) &&
             Objects.equals(enabled, other.enabled) &&
             Objects.equals(exclusive, other.exclusive) &&
             Objects.equals(groupRebalance, other.groupRebalance) &&
             Objects.equals(groupBuckets, other.groupBuckets) &&
             Objects.equals(groupFirstKey, other.groupFirstKey) &&
             Objects.equals(lastValue, other.lastValue) &&
             Objects.equals(lastValueKey, other.lastValueKey) &&
             Objects.equals(nonDestructive, other.nonDestructive) &&
             Objects.equals(consumersBeforeDispatch, other.consumersBeforeDispatch) &&
             Objects.equals(delayBeforeDispatch, other.delayBeforeDispatch) &&
             Objects.equals(routingType, other.routingType);
   }

   @Override
   public String toString() {
      return "CoreQueueConfiguration[" +
         "name=" + name +
         ", address=" + address +
         ", routingType=" + routingType +
         ", durable=" + durable +
         ", filterString=" + filterString +
         ", maxConsumers=" + maxConsumers +
         ", purgeOnNoConsumers=" + purgeOnNoConsumers +
         ", exclusive=" + exclusive +
         ", groupRebalance=" + groupRebalance +
         ", groupBuckets=" + groupBuckets +
         ", groupFirstKey=" + groupFirstKey +
         ", lastValue=" + lastValue +
         ", lastValueKey=" + lastValueKey +
         ", nonDestructive=" + nonDestructive +
         ", consumersBeforeDispatch=" + consumersBeforeDispatch +
         ", delayBeforeDispatch=" + delayBeforeDispatch +
         ", ringSize=" + ringSize +
         ", enabled=" + enabled +
         "]";
   }
}
