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
package org.apache.activemq.artemis.core.server;

import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.FilterUtils;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;

@Deprecated
public final class QueueConfig {

   private final long id;
   private final SimpleString address;
   private final SimpleString name;
   private final Filter filter;
   final PagingStore pagingStore;
   private final PageSubscription pageSubscription;
   private final SimpleString user;
   private final boolean durable;
   private final boolean temporary;
   private final boolean autoCreated;
   private final RoutingType routingType;
   private final int maxConsumers;
   private final boolean exclusive;
   private final boolean lastValue;
   private final boolean purgeOnNoConsumers;
   private final int consumersBeforeDispatch;
   private final long delayBeforeDispatch;
   private final boolean groupRebalance;
   private final int groupBuckets;
   private final SimpleString groupFirstKey;
   private final boolean configurationManaged;
   private final SimpleString lastValueKey;
   private final boolean nonDestructive;
   private final boolean autoDelete;
   private final long autoDeleteDelay;
   private final long autoDeleteMessageCount;
   private final long ringSize;

   @Deprecated
   public static final class Builder {

      private final long id;
      private final SimpleString address;
      private final SimpleString name;
      private Filter filter;
      private PagingManager pagingManager;
      private SimpleString user;
      private boolean durable;
      private boolean temporary;
      private boolean autoCreated;
      private RoutingType routingType;
      private int maxConsumers;
      private boolean exclusive;
      private boolean lastValue;
      private SimpleString lastValueKey;
      private boolean nonDestructive;
      private boolean purgeOnNoConsumers;
      private int consumersBeforeDispatch;
      private long delayBeforeDispatch;
      private boolean groupRebalance;
      private int groupBuckets;
      private SimpleString groupFirstKey;
      private boolean autoDelete;
      private long autoDeleteDelay;
      private long autoDeleteMessageCount;
      private long ringSize;
      private boolean configurationManaged;

      private Builder(final long id, final SimpleString name) {
         this(id, name, name);
      }

      private Builder(final long id, final SimpleString name, final SimpleString address) {
         this.id = id;
         this.name = name;
         this.address = address;
         this.filter = null;
         this.pagingManager = null;
         this.user = null;
         this.durable = true;
         this.temporary = false;
         this.autoCreated = true;
         this.routingType = ActiveMQDefaultConfiguration.getDefaultRoutingType();
         this.maxConsumers = ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers();
         this.exclusive = ActiveMQDefaultConfiguration.getDefaultExclusive();
         this.lastValue = ActiveMQDefaultConfiguration.getDefaultLastValue();
         this.lastValueKey = ActiveMQDefaultConfiguration.getDefaultLastValueKey();
         this.nonDestructive = ActiveMQDefaultConfiguration.getDefaultNonDestructive();
         this.purgeOnNoConsumers = ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers();
         this.consumersBeforeDispatch = ActiveMQDefaultConfiguration.getDefaultConsumersBeforeDispatch();
         this.delayBeforeDispatch = ActiveMQDefaultConfiguration.getDefaultDelayBeforeDispatch();
         this.groupRebalance = ActiveMQDefaultConfiguration.getDefaultGroupRebalance();
         this.groupBuckets = ActiveMQDefaultConfiguration.getDefaultGroupBuckets();
         this.groupFirstKey = ActiveMQDefaultConfiguration.getDefaultGroupFirstKey();
         this.autoDelete = ActiveMQDefaultConfiguration.getDefaultQueueAutoDelete(autoCreated);
         this.autoDeleteDelay = ActiveMQDefaultConfiguration.getDefaultQueueAutoDeleteDelay();
         this.autoDeleteMessageCount = ActiveMQDefaultConfiguration.getDefaultQueueAutoDeleteMessageCount();
         this.ringSize = ActiveMQDefaultConfiguration.getDefaultRingSize();
         this.configurationManaged = false;
         validateState();
      }

      private static boolean isEmptyOrNull(SimpleString value) {
         return (value == null || value.isEmpty());
      }

      private void validateState() {
         if (isEmptyOrNull(this.name)) {
            throw new IllegalStateException("name can't be null or empty!");
         }
         if (isEmptyOrNull(this.address)) {
            throw new IllegalStateException("address can't be null or empty!");
         }
      }

      public Builder configurationManaged(final boolean configurationManaged) {
         this.configurationManaged = configurationManaged;
         return this;
      }

      public Builder filter(final Filter filter) {
         this.filter = filter;
         return this;
      }

      public Builder pagingManager(final PagingManager pagingManager) {
         this.pagingManager = pagingManager;
         return this;
      }

      public Builder user(final SimpleString user) {
         this.user = user;
         return this;
      }

      public Builder durable(final boolean durable) {
         this.durable = durable;
         return this;
      }

      public Builder temporary(final boolean temporary) {
         this.temporary = temporary;
         return this;
      }

      public Builder autoCreated(final boolean autoCreated) {
         this.autoCreated = autoCreated;
         return this;
      }

      public Builder maxConsumers(final int maxConsumers) {
         this.maxConsumers = maxConsumers;
         return this;
      }

      public Builder exclusive(final boolean exclusive) {
         this.exclusive = exclusive;
         return this;
      }

      public Builder lastValue(final boolean lastValue) {
         this.lastValue = lastValue;
         return this;
      }

      public Builder lastValueKey(SimpleString lastValueKey) {
         this.lastValueKey = lastValueKey;
         return this;
      }

      public Builder nonDestructive(boolean nonDestructive) {
         this.nonDestructive = nonDestructive;
         return this;
      }

      public Builder consumersBeforeDispatch(final int consumersBeforeDispatch) {
         this.consumersBeforeDispatch = consumersBeforeDispatch;
         return this;
      }

      public Builder delayBeforeDispatch(final long delayBeforeDispatch) {
         this.delayBeforeDispatch = delayBeforeDispatch;
         return this;
      }

      public Builder purgeOnNoConsumers(final boolean purgeOnNoConsumers) {
         this.purgeOnNoConsumers = purgeOnNoConsumers;
         return this;
      }

      public Builder autoDelete(final boolean autoDelete) {
         this.autoDelete = autoDelete;
         return this;
      }

      public Builder autoDeleteDelay(final long autoDeleteDelay) {
         this.autoDeleteDelay = autoDeleteDelay;
         return this;
      }

      public Builder autoDeleteMessageCount(final long autoDeleteMessageCount) {
         this.autoDeleteMessageCount = autoDeleteMessageCount;
         return this;
      }

      public Builder ringSize(final long ringSize) {
         this.ringSize = ringSize;
         return this;
      }


      public Builder groupRebalance(final boolean groupRebalance) {
         this.groupRebalance = groupRebalance;
         return this;
      }


      public Builder groupBuckets(final int groupBuckets) {
         this.groupBuckets = groupBuckets;
         return this;
      }

      public Builder groupFirstKey(final SimpleString groupFirstKey) {
         this.groupFirstKey = groupFirstKey;
         return this;
      }


      public Builder routingType(RoutingType routingType) {
         this.routingType = routingType;
         return this;
      }

      /**
       * Returns a new {@link QueueConfig} using the parameters configured on the {@link Builder}.
       * <p>
       * The reference parameters aren't defensively copied from the {@link Builder} to the {@link QueueConfig}.
       * <p>
       * This method creates a new {@link PageSubscription} only if {@link #pagingManager} is not {@code null} and if
       * {@link FilterUtils#isTopicIdentification} returns {@code false} on {@link #filter}.
       *
       * @throws IllegalStateException if the creation of {@link PageSubscription} fails
       */
      public QueueConfig build() {
         final PagingStore pageStore;
         final PageSubscription pageSubscription;
         if (pagingManager != null && !FilterUtils.isTopicIdentification(filter)) {
            try {
               pageStore = this.pagingManager.getPageStore(address);
               if (pageStore != null) {
                  pageSubscription = pageStore.getCursorProvider().createSubscription(id, filter, durable);
               } else {
                  pageSubscription = null;
               }
            } catch (Exception e) {
               throw new IllegalStateException(e);
            }
         } else {
            pageSubscription = null;
            pageStore = null;
         }
         return new QueueConfig(id, address, name, filter, pageStore, pageSubscription, user, durable, temporary, autoCreated, routingType, maxConsumers, exclusive, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, purgeOnNoConsumers, groupRebalance, groupBuckets, groupFirstKey, autoDelete, autoDeleteDelay, autoDeleteMessageCount, ringSize, configurationManaged);
      }

   }

   /**
    * Returns a new {@link Builder} of a durable, not temporary and autoCreated {@link QueueConfig} with the given
    * {@code id} and {@code name}.
    * <p>
    * The {@code address} is defaulted to the {@code name} value. The reference parameters aren't defensively copied.
    *
    * @param id   the id of the queue to be created
    * @param name the name of the queue to be created
    * @throws IllegalStateException if {@code name} is {@code null} or empty
    */
   public static Builder builderWith(final long id, final SimpleString name) {
      return new QueueConfig.Builder(id, name);
   }

   /**
    * Returns a new {@link Builder} of a durable, not temporary and autoCreated {@link QueueConfig} with the given
    * {@code id}, {@code name} and {@code address}.
    * <p>
    * The reference parameters aren't defensively copied.
    *
    * @param id      the id of the queue to be created
    * @param name    the name of the queue to be created
    * @param address the address of the queue to be created
    * @throws IllegalStateException if {@code name} or {@code address} are {@code null} or empty
    */
   public static Builder builderWith(final long id, final SimpleString name, final SimpleString address) {
      return new QueueConfig.Builder(id, name, address);
   }

   private QueueConfig(final long id,
                       final SimpleString address,
                       final SimpleString name,
                       final Filter filter,
                       final PagingStore pagingStore,
                       final PageSubscription pageSubscription,
                       final SimpleString user,
                       final boolean durable,
                       final boolean temporary,
                       final boolean autoCreated,
                       final RoutingType routingType,
                       final int maxConsumers,
                       final boolean exclusive,
                       final boolean lastValue,
                       final SimpleString lastValueKey,
                       final boolean nonDestructive,
                       final int consumersBeforeDispatch,
                       final long delayBeforeDispatch,
                       final boolean purgeOnNoConsumers,
                       final boolean groupRebalance,
                       final int groupBuckets,
                       final SimpleString groupFirstKey,
                       final boolean autoDelete,
                       final long autoDeleteDelay,
                       final long autoDeleteMessageCount,
                       final long ringSize,
                       final boolean configurationManaged) {
      this.id = id;
      this.address = address;
      this.name = name;
      this.filter = filter;
      this.pagingStore = pagingStore;
      this.pageSubscription = pageSubscription;
      this.user = user;
      this.durable = durable;
      this.temporary = temporary;
      this.autoCreated = autoCreated;
      this.routingType = routingType;
      this.purgeOnNoConsumers = purgeOnNoConsumers;
      this.exclusive = exclusive;
      this.lastValue = lastValue;
      this.lastValueKey = lastValueKey;
      this.nonDestructive = nonDestructive;
      this.maxConsumers = maxConsumers;
      this.consumersBeforeDispatch = consumersBeforeDispatch;
      this.delayBeforeDispatch = delayBeforeDispatch;
      this.groupRebalance = groupRebalance;
      this.groupBuckets = groupBuckets;
      this.groupFirstKey = groupFirstKey;
      this.autoDelete = autoDelete;
      this.autoDeleteDelay = autoDeleteDelay;
      this.autoDeleteMessageCount = autoDeleteMessageCount;
      this.ringSize = ringSize;
      this.configurationManaged = configurationManaged;
   }

   public static QueueConfig fromQueueConfiguration(QueueConfiguration queueConfiguration) throws ActiveMQException {
      return new QueueConfig(Objects.requireNonNullElse(queueConfiguration.getId(), 0L),
                             queueConfiguration.getAddress(),
                             queueConfiguration.getName(),
                             queueConfiguration.getFilterString() == null ? null : FilterImpl.createFilter(queueConfiguration.getFilterString()),
                             null,
                             null,
                             queueConfiguration.getUser(),
                             queueConfiguration.isDurable(),
                             queueConfiguration.isTemporary(),
                             queueConfiguration.isAutoCreated(),
                             queueConfiguration.getRoutingType(),
                             queueConfiguration.getMaxConsumers(),
                             queueConfiguration.isExclusive(),
                             queueConfiguration.isLastValue(),
                             queueConfiguration.getLastValueKey(),
                             queueConfiguration.isNonDestructive(),
                             queueConfiguration.getConsumersBeforeDispatch(),
                             queueConfiguration.getDelayBeforeDispatch(),
                             queueConfiguration.isPurgeOnNoConsumers(),
                             queueConfiguration.isGroupRebalance(),
                             queueConfiguration.getGroupBuckets(),
                             queueConfiguration.getGroupFirstKey(),
                             queueConfiguration.isAutoDelete(),
                             queueConfiguration.getAutoDeleteDelay(),
                             queueConfiguration.getAutoDeleteMessageCount(),
                             queueConfiguration.getRingSize(),
                             queueConfiguration.isConfigurationManaged());
   }

   public long id() {
      return id;
   }

   public SimpleString address() {
      return address;
   }

   public SimpleString name() {
      return name;
   }

   public Filter filter() {
      return filter;
   }

   public PageSubscription pageSubscription() {
      return pageSubscription;
   }

   public SimpleString user() {
      return user;
   }

   public boolean isDurable() {
      return durable;
   }

   public boolean isTemporary() {
      return temporary;
   }

   public boolean isAutoCreated() {
      return autoCreated;
   }

   public boolean isPurgeOnNoConsumers() {
      return purgeOnNoConsumers;
   }

   public int maxConsumers() {
      return maxConsumers;
   }

   public boolean isExclusive() {
      return exclusive;
   }

   public boolean isLastValue() {
      return lastValue;
   }

   public SimpleString lastValueKey() {
      return lastValueKey;
   }

   public boolean isNonDestructive() {
      return nonDestructive;
   }

   public RoutingType deliveryMode() {
      return routingType;
   }

   public int consumersBeforeDispatch() {
      return consumersBeforeDispatch;
   }

   public long delayBeforeDispatch() {
      return delayBeforeDispatch;
   }

   public boolean isGroupRebalance() {
      return groupRebalance;
   }

   public int getGroupBuckets() {
      return groupBuckets;
   }

   public SimpleString getGroupFirstKey() {
      return groupFirstKey;
   }

   public boolean isConfigurationManaged() {
      return configurationManaged;
   }

   public boolean isAutoDelete() {
      return autoDelete;
   }

   public long getAutoDeleteDelay() {
      return autoDeleteDelay;
   }

   public long getAutoDeleteMessageCount() {
      return autoDeleteMessageCount;
   }

   public long getRingSize() {
      return ringSize;
   }

   public PagingStore getPagingStore() {
      return pagingStore;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof QueueConfig other)) {
         return false;
      }

      return id == other.id &&
             durable == other.durable &&
             temporary == other.temporary &&
             autoCreated == other.autoCreated &&
             Objects.equals(address, other.address) &&
             Objects.equals(name, other.name) &&
             Objects.equals(filter, other.filter) &&
             Objects.equals(pageSubscription, other.pageSubscription) &&
             routingType == other.routingType &&
             maxConsumers == other.maxConsumers &&
             exclusive == other.exclusive &&
             lastValue == other.lastValue &&
             Objects.equals(lastValueKey, other.lastValueKey) &&
             nonDestructive == other.nonDestructive &&
             purgeOnNoConsumers == other.purgeOnNoConsumers &&
             consumersBeforeDispatch == other.consumersBeforeDispatch &&
             delayBeforeDispatch == other.delayBeforeDispatch &&
             groupRebalance == other.groupRebalance &&
             groupBuckets == other.groupBuckets &&
             Objects.equals(groupFirstKey, other.groupFirstKey) &&
             autoDelete == other.autoDelete &&
             autoDeleteDelay == other.autoDeleteDelay &&
             autoDeleteMessageCount == other.autoDeleteMessageCount &&
             ringSize == other.ringSize &&
             configurationManaged == other.configurationManaged &&
             Objects.equals(user, other.user);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, address, name, filter, pageSubscription, user, durable, temporary, autoCreated,
                          routingType, maxConsumers, exclusive, lastValue, lastValueKey, nonDestructive,
                          consumersBeforeDispatch, delayBeforeDispatch, purgeOnNoConsumers, groupRebalance,
                          groupBuckets, groupFirstKey, autoDelete, autoDeleteDelay, autoDeleteMessageCount, ringSize,
                          configurationManaged);
   }

   @Override
   public String toString() {
      return "QueueConfig{"
         + "id=" + id
         + ", address=" + address
         + ", name=" + name
         + ", filter=" + filter
         + ", pageSubscription=" + pageSubscription
         + ", user=" + user
         + ", durable=" + durable
         + ", temporary=" + temporary
         + ", autoCreated=" + autoCreated
         + ", routingType=" + routingType
         + ", maxConsumers=" + maxConsumers
         + ", exclusive=" + exclusive
         + ", lastValue=" + lastValue
         + ", lastValueKey=" + lastValueKey
         + ", nonDestructive=" + nonDestructive
         + ", consumersBeforeDispatch=" + consumersBeforeDispatch
         + ", delayBeforeDispatch=" + delayBeforeDispatch
         + ", purgeOnNoConsumers=" + purgeOnNoConsumers
         + ", groupRebalance=" + groupRebalance
         + ", groupBuckets=" + groupBuckets
         + ", groupFirstKey=" + groupFirstKey
         + ", autoDelete=" + autoDelete
         + ", autoDeleteDelay=" + autoDeleteDelay
         + ", autoDeleteMessageCount=" + autoDeleteMessageCount
         + ", ringSize=" + ringSize
         + ", configurationManaged=" + configurationManaged + '}';
   }
}
