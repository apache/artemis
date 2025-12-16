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
package org.apache.activemq.artemis.api.core;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.JsonLoader;

/**
 * This class holds all the properties required to configure a queue. The only strictly required property is
 * {@code name}. Some defaults will be enforced for properties which are not explicitly set:
 * <ul>
 * <li>{@code address} : the value set for {@code name}
 * <li>{@code transient} : {@code false}
 * <li>{@code temporary} : {@code false}
 * <li>{@code durable} : {@code true}
 * <li>{@code autoCreated} : {@code false}
 * <li>{@code internal} : {@code false}
 * <li>{@code configurationManaged} : {@code false}
 * </ul>
 */
public class QueueConfiguration implements Serializable {

   // The prefix for Mirror SNF Queues
   public static final String MIRROR_ADDRESS = "$ACTIVEMQ_ARTEMIS_MIRROR";

   private static final long serialVersionUID = 2601016432150225938L;

   public static final String ID = "id";
   public static final String NAME = "name";
   public static final String ADDRESS = "address";
   public static final String ROUTING_TYPE = "routing-type";
   public static final String FILTER_STRING = "filter-string";
   public static final String DURABLE = "durable";
   public static final String USER = "user";
   public static final String MAX_CONSUMERS = "max-consumers";
   public static final String EXCLUSIVE = "exclusive";
   public static final String GROUP_REBALANCE = "group-rebalance";
   public static final String GROUP_REBALANCE_PAUSE_DISPATCH = "group-rebalance-pause-dispatch";
   public static final String GROUP_BUCKETS = "group-buckets";
   public static final String GROUP_FIRST_KEY = "group-first-key";
   public static final String LAST_VALUE = "last-value";
   public static final String LAST_VALUE_KEY = "last-value-key";
   public static final String NON_DESTRUCTIVE = "non-destructive";
   public static final String PURGE_ON_NO_CONSUMERS = "purge-on-no-consumers";
   public static final String ENABLED = "enabled";
   public static final String CONSUMERS_BEFORE_DISPATCH = "consumers-before-dispatch";
   public static final String DELAY_BEFORE_DISPATCH = "delay-before-dispatch";
   public static final String CONSUMER_PRIORITY = "consumer-priority";
   public static final String AUTO_DELETE = "auto-delete";
   public static final String AUTO_DELETE_DELAY = "auto-delete-delay";
   public static final String AUTO_DELETE_MESSAGE_COUNT = "auto-delete-message-count";
   public static final String RING_SIZE = "ring-size";
   public static final String CONFIGURATION_MANAGED = "configuration-managed";
   public static final String TEMPORARY = "temporary";
   public static final String AUTO_CREATE_ADDRESS = "auto-create-address";
   public static final String INTERNAL = "internal";
   public static final String TRANSIENT = "transient";
   public static final String AUTO_CREATED = "auto-created";
   public static final String FQQN = "fqqn";

   private Long id; // internal use
   private SimpleString name;
   private SimpleString address;
   private RoutingType routingType;
   private SimpleString filterString;
   private Boolean durable;
   private SimpleString user;
   private Integer maxConsumers;
   private Boolean exclusive;
   private Boolean groupRebalance;
   private Boolean groupRebalancePauseDispatch;
   private Integer groupBuckets;
   private SimpleString groupFirstKey;
   private Boolean lastValue;
   private SimpleString lastValueKey;
   private Boolean nonDestructive;
   private Boolean purgeOnNoConsumers;
   private Boolean enabled;
   private Integer consumersBeforeDispatch;
   private Long delayBeforeDispatch;
   private Integer consumerPriority;
   private Boolean autoDelete;
   private Long autoDeleteDelay;
   private Long autoDeleteMessageCount;
   private Long ringSize;
   private Boolean configurationManaged;
   private Boolean temporary;
   private Boolean autoCreateAddress;
   private Boolean internal;
   private Boolean _transient;
   private Boolean autoCreated;
   private Boolean fqqn;

   /**
    * Instance factory which invokes {@link #setName(SimpleString)}
    *
    * @param name the name to use for the queue
    * @see #setName(SimpleString)
    */
   public static QueueConfiguration of(final String name) {
      return new QueueConfiguration(name);
   }

   /**
    * Instance factory which invokes {@link #setName(SimpleString)}
    *
    * @param name the name to use for the queue
    * @see #setName(SimpleString)
    */
   public static QueueConfiguration of(final SimpleString name) {
      return new QueueConfiguration(name);
   }

   /**
    * {@return the {@code QueueConfiguration} instance which is a copy of the input parameter}
    */
   public static QueueConfiguration of(final QueueConfiguration queueConfiguration) {
      return new QueueConfiguration(queueConfiguration);
   }

   /**
    * @deprecated
    * Use {@link #of(String)} instead.
    */
   @Deprecated(forRemoval = true)
   public QueueConfiguration() {
   }

   /**
    * @param o create a copy of this
    * @deprecated Use {@link #of(QueueConfiguration)} instead.
    */
   @Deprecated(forRemoval = true)
   public QueueConfiguration(final QueueConfiguration o) {
      id = o.id;
      name = o.name;
      address = o.address;
      routingType = o.routingType;
      filterString = o.filterString;
      durable = o.durable;
      user = o.user;
      maxConsumers = o.maxConsumers;
      exclusive = o.exclusive;
      groupRebalance = o.groupRebalance;
      groupRebalancePauseDispatch = o.groupRebalancePauseDispatch;
      groupBuckets = o.groupBuckets;
      groupFirstKey = o.groupFirstKey;
      lastValue = o.lastValue;
      lastValueKey = o.lastValueKey;
      nonDestructive = o.nonDestructive;
      purgeOnNoConsumers = o.purgeOnNoConsumers;
      enabled = o.enabled;
      consumersBeforeDispatch = o.consumersBeforeDispatch;
      delayBeforeDispatch = o.delayBeforeDispatch;
      consumerPriority = o.consumerPriority;
      autoDelete = o.autoDelete;
      autoDeleteDelay = o.autoDeleteDelay;
      autoDeleteMessageCount = o.autoDeleteMessageCount;
      ringSize = o.ringSize;
      configurationManaged = o.configurationManaged;
      temporary = o.temporary;
      autoCreateAddress = o.autoCreateAddress;
      internal = o.internal;
      _transient = o._transient;
      autoCreated = o.autoCreated;
      fqqn = o.fqqn;
   }

   /**
    * Instantiate this object and invoke {@link #setName(SimpleString)}
    *
    * @param name the name to use for the queue
    * @see #setName(SimpleString)
    * @deprecated Use {@link #of(SimpleString)} instead.
    */
   @Deprecated(forRemoval = true)
   public QueueConfiguration(SimpleString name) {
      setName(name);
   }

   /**
    * Instantiate this object and invoke {@link #setName(SimpleString)}
    *
    * @param name the name to use for the queue
    * @see #setName(SimpleString)
    * @deprecated Use {@link #of(String)} instead.
    */
   @Deprecated(forRemoval = true)
   public QueueConfiguration(String name) {
      this(SimpleString.of(name));
   }

   /**
    * Set the value of a parameter based on its "key" {@code String}. Valid key names and corresponding {@code static}
    * {@code final} are:
    * <ul>
    * <li>id: {@link #ID}
    * <li>name: {@link #NAME}
    * <li>address: {@link #ADDRESS}
    * <li>routing-type: {@link #ROUTING_TYPE}
    * <li>filter-string: {@link #FILTER_STRING}
    * <li>durable: {@link #DURABLE}
    * <li>user: {@link #USER}
    * <li>max-consumers: {@link #MAX_CONSUMERS}
    * <li>exclusive: {@link #EXCLUSIVE}
    * <li>group-rebalance: {@link #GROUP_REBALANCE}
    * <li>group-rebalance-pause-dispatch: {@link #GROUP_REBALANCE_PAUSE_DISPATCH}
    * <li>group-buckets: {@link #GROUP_BUCKETS}
    * <li>group-first-key: {@link #GROUP_FIRST_KEY}
    * <li>last-value: {@link #LAST_VALUE}
    * <li>last-value-key: {@link #LAST_VALUE_KEY}
    * <li>non-destructive: {@link #NON_DESTRUCTIVE}
    * <li>purge-on-no-consumers: {@link #PURGE_ON_NO_CONSUMERS}
    * <li>consumers-before-dispatch: {@link #CONSUMERS_BEFORE_DISPATCH}
    * <li>delay-before-dispatch: {@link #DELAY_BEFORE_DISPATCH}
    * <li>consumer-priority: {@link #CONSUMER_PRIORITY}
    * <li>auto-delete: {@link #AUTO_DELETE}
    * <li>auto-delete-delay: {@link #AUTO_DELETE_DELAY}
    * <li>auto-delete-message-count: {@link #AUTO_DELETE_MESSAGE_COUNT}
    * <li>ring-size: {@link #RING_SIZE}
    * <li>configuration-managed: {@link #ID}
    * <li>temporary: {@link #TEMPORARY}
    * <li>auto-create-address: {@link #AUTO_CREATE_ADDRESS}
    * <li>internal: {@link #INTERNAL}
    * <li>transient: {@link #TRANSIENT}
    * <li>auto-created: {@link #AUTO_CREATED}
    * </ul>
    * The {@code String}-based values will be converted to the proper value types based on the underlying property. For
    * example, if you pass the value "TRUE" for the key "auto-created" the {@code String} "TRUE" will be converted to
    * the {@code Boolean} {@code true}.
    *
    * @param key   the key to set to the value
    * @param value the value to set for the key
    * @return this {@code QueueConfiguration}
    */
   public QueueConfiguration set(String key, String value) {
      if (key != null && value != null) {
         if (key.equals(NAME)) {
            setName(value);
         } else if (key.equals(ADDRESS)) {
            setAddress(value);
         } else if (key.equals(ROUTING_TYPE)) {
            setRoutingType(RoutingType.valueOf(value));
         } else if (key.equals(FILTER_STRING)) {
            setFilterString(value);
         } else if (key.equals(DURABLE)) {
            setDurable(Boolean.valueOf(value));
         } else if (key.equals(USER)) {
            setUser(SimpleString.of(value));
         } else if (key.equals(MAX_CONSUMERS)) {
            setMaxConsumers(Integer.valueOf(value));
         } else if (key.equals(EXCLUSIVE)) {
            setExclusive(Boolean.valueOf(value));
         } else if (key.equals(GROUP_REBALANCE)) {
            setGroupRebalance(Boolean.valueOf(value));
         } else if (key.equals(GROUP_REBALANCE_PAUSE_DISPATCH)) {
            setGroupRebalancePauseDispatch(Boolean.valueOf(value));
         } else if (key.equals(GROUP_BUCKETS)) {
            setGroupBuckets(Integer.valueOf(value));
         } else if (key.equals(GROUP_FIRST_KEY)) {
            setGroupFirstKey(value);
         } else if (key.equals(LAST_VALUE)) {
            setLastValue(Boolean.valueOf(value));
         } else if (key.equals(LAST_VALUE_KEY)) {
            setLastValueKey(value);
         } else if (key.equals(NON_DESTRUCTIVE)) {
            setNonDestructive(Boolean.valueOf(value));
         } else if (key.equals(PURGE_ON_NO_CONSUMERS)) {
            setPurgeOnNoConsumers(Boolean.valueOf(value));
         } else if (key.equals(ENABLED)) {
            setEnabled(Boolean.valueOf(value));
         } else if (key.equals(CONSUMERS_BEFORE_DISPATCH)) {
            setConsumersBeforeDispatch(Integer.valueOf(value));
         } else if (key.equals(DELAY_BEFORE_DISPATCH)) {
            setDelayBeforeDispatch(Long.valueOf(value));
         } else if (key.equals(CONSUMER_PRIORITY)) {
            setConsumerPriority(Integer.valueOf(value));
         } else if (key.equals(AUTO_DELETE)) {
            setAutoDelete(Boolean.valueOf(value));
         } else if (key.equals(AUTO_DELETE_DELAY)) {
            setAutoDeleteDelay(Long.valueOf(value));
         } else if (key.equals(AUTO_DELETE_MESSAGE_COUNT)) {
            setAutoDeleteMessageCount(Long.valueOf(value));
         } else if (key.equals(RING_SIZE)) {
            setRingSize(Long.valueOf(value));
         } else if (key.equals(CONFIGURATION_MANAGED)) {
            setConfigurationManaged(Boolean.valueOf(value));
         } else if (key.equals(TEMPORARY)) {
            setTemporary(Boolean.valueOf(value));
         } else if (key.equals(AUTO_CREATE_ADDRESS)) {
            setAutoCreateAddress(Boolean.valueOf(value));
         } else if (key.equals(INTERNAL)) {
            setInternal(Boolean.valueOf(value));
         } else if (key.equals(TRANSIENT)) {
            setTransient(Boolean.valueOf(value));
         } else if (key.equals(AUTO_CREATED)) {
            setAutoCreated(Boolean.valueOf(value));
         }
      }
      return this;
   }

   public Long getId() {
      return id;
   }

   public QueueConfiguration setId(Long id) {
      this.id = id;
      return this;
   }

   /**
    * {@return the name of the address; if the address is {@code null} then return the value of {@link #getName()}}
    */
   public SimpleString getAddress() {
      return Objects.requireNonNullElse(address, getName());
   }

   public boolean isAddressNull() {
      return address == null;
   }

   /**
    * Set the address. If the fully-qualified queue name is used then it will be parsed and the corresponding values for
    * {@code address} and {@code name} will be set automatically. For example if "myAddress::myQueue" is passed then the
    * resulting value for {@code address} will be "myAddress" and the value for {@code name} will be "myQueue".
    *
    * @param address the address to use
    * @return this {@code QueueConfiguration}
    */
   public QueueConfiguration setAddress(SimpleString address) {
      if (CompositeAddress.isFullyQualified(address)) {
         this.name = CompositeAddress.extractQueueName(address);
         this.address = CompositeAddress.extractAddressName(address);
         this.fqqn = Boolean.TRUE;
      } else {
         this.address = address;
      }
      return this;
   }

   /**
    * Converts the input {@code String} and invokes {@link #setAddress(SimpleString)}
    *
    * @see QueueConfiguration#setAddress(SimpleString)
    */
   public QueueConfiguration setAddress(String address) {
      return setAddress(SimpleString.of(address));
   }

   public SimpleString getName() {
      return name;
   }

   /**
    * Set the name. If the fully-qualified queue name is used then it will be parsed and the corresponding values for
    * {@code address} and {@code name} will be set automatically. For example if "myAddress::myQueue" is passed then the
    * resulting value for {@code address} will be "myAddress" and the value for {@code name} will be "myQueue".
    *
    * @param name the name to use
    * @return this {@code QueueConfiguration}
    */
   public QueueConfiguration setName(SimpleString name) {
      if (CompositeAddress.isFullyQualified(name)) {
         this.name = CompositeAddress.extractQueueName(name);
         this.address = CompositeAddress.extractAddressName(name);
         this.fqqn = Boolean.TRUE;
      } else {
         this.name = name;
      }
      return this;
   }

   /**
    * Converts the input {@code String} and invokes {@link #setName(SimpleString)}
    *
    * @see QueueConfiguration#setName(SimpleString)
    */
   public QueueConfiguration setName(String name) {
      return setName(SimpleString.of(name));
   }

   public RoutingType getRoutingType() {
      return routingType;
   }

   public QueueConfiguration setRoutingType(RoutingType routingType) {
      this.routingType = routingType;
      return this;
   }

   public SimpleString getFilterString() {
      return filterString;
   }

   /**
    * This sets the {@code SimpleString} value that will be used to create a {@code Filter} for the {@code Queue}
    * implementation on the broker. The filter's syntax is not validated here.
    * @param filterString the filter to use; an empty value or a value filled with whitespace is equivalent to passing
    *                     {@code null}
    * @return this {@code QueueConfiguration}
    */
   public QueueConfiguration setFilterString(SimpleString filterString) {
      if (filterString == null || filterString.isEmpty() || filterString.isBlank()) {
         this.filterString = null;
      } else {
         this.filterString = filterString;
      }
      return this;
   }

   /**
    * Converts the {@code String} parameter to {@code SimpleString} and invokes
    * {@link #setFilterString(SimpleString)}
    * @see #setFilterString(SimpleString)
    */
   public QueueConfiguration setFilterString(String filterString) {
      return setFilterString(SimpleString.of(filterString));
   }

   /**
    * defaults to {@code true}
    */
   public Boolean isDurable() {
      return Objects.requireNonNullElse(durable, true);
   }

   public QueueConfiguration setDurable(Boolean durable) {
      this.durable = durable;
      return this;
   }

   public SimpleString getUser() {
      return user;
   }

   public QueueConfiguration setUser(SimpleString user) {
      this.user = user;
      return this;
   }

   public QueueConfiguration setUser(String user) {
      return setUser(SimpleString.of(user));
   }

   public Integer getMaxConsumers() {
      return maxConsumers;
   }

   public QueueConfiguration setMaxConsumers(Integer maxConsumers) {
      this.maxConsumers = maxConsumers;
      return this;
   }

   public Boolean isExclusive() {
      return exclusive;
   }

   public QueueConfiguration setExclusive(Boolean exclusive) {
      this.exclusive = exclusive;
      return this;
   }

   public Boolean isLastValue() {
      return lastValue;
   }

   public QueueConfiguration setLastValue(Boolean lastValue) {
      this.lastValue = lastValue;
      return this;
   }

   public SimpleString getLastValueKey() {
      return lastValueKey;
   }

   public QueueConfiguration setLastValueKey(SimpleString lastValueKey) {
      this.lastValueKey = lastValueKey;
      return this;
   }

   public QueueConfiguration setLastValueKey(String lastValueKey) {
      return setLastValueKey(SimpleString.of(lastValueKey));
   }

   public Boolean isNonDestructive() {
      return nonDestructive;
   }

   public QueueConfiguration setNonDestructive(Boolean nonDestructive) {
      this.nonDestructive = nonDestructive;
      return this;
   }

   public Boolean isPurgeOnNoConsumers() {
      return purgeOnNoConsumers;
   }

   public QueueConfiguration setPurgeOnNoConsumers(Boolean purgeOnNoConsumers) {
      this.purgeOnNoConsumers = purgeOnNoConsumers;
      return this;
   }

   public Boolean isEnabled() {
      return enabled;
   }

   public QueueConfiguration setEnabled(Boolean enabled) {
      this.enabled = enabled;
      return this;
   }


   public Integer getConsumersBeforeDispatch() {
      return consumersBeforeDispatch;
   }

   public QueueConfiguration setConsumersBeforeDispatch(Integer consumersBeforeDispatch) {
      this.consumersBeforeDispatch = consumersBeforeDispatch;
      return this;
   }

   public Long getDelayBeforeDispatch() {
      return delayBeforeDispatch;
   }

   public QueueConfiguration setDelayBeforeDispatch(Long delayBeforeDispatch) {
      this.delayBeforeDispatch = delayBeforeDispatch;
      return this;
   }

   public Integer getConsumerPriority() {
      return consumerPriority;
   }

   public QueueConfiguration setConsumerPriority(Integer consumerPriority) {
      this.consumerPriority = consumerPriority;
      return this;
   }

   public Boolean isGroupRebalance() {
      return groupRebalance;
   }

   public QueueConfiguration setGroupRebalance(Boolean groupRebalance) {
      this.groupRebalance = groupRebalance;
      return this;
   }

   public Boolean isGroupRebalancePauseDispatch() {
      return groupRebalancePauseDispatch;
   }

   public QueueConfiguration setGroupRebalancePauseDispatch(Boolean groupRebalancePauseDispatch) {
      this.groupRebalancePauseDispatch = groupRebalancePauseDispatch;
      return this;
   }

   public Integer getGroupBuckets() {
      return groupBuckets;
   }

   public QueueConfiguration setGroupBuckets(Integer groupBuckets) {
      this.groupBuckets = groupBuckets;
      return this;
   }

   public SimpleString getGroupFirstKey() {
      return groupFirstKey;
   }

   public QueueConfiguration setGroupFirstKey(SimpleString groupFirstKey) {
      this.groupFirstKey = groupFirstKey;
      return this;
   }

   public QueueConfiguration setGroupFirstKey(String groupFirstKey) {
      return setGroupFirstKey(SimpleString.of(groupFirstKey));
   }

   public Boolean isAutoDelete() {
      return autoDelete;
   }

   public QueueConfiguration setAutoDelete(Boolean autoDelete) {
      this.autoDelete = autoDelete;
      return this;
   }

   public Long getAutoDeleteDelay() {
      return autoDeleteDelay;
   }

   public QueueConfiguration setAutoDeleteDelay(Long autoDeleteDelay) {
      this.autoDeleteDelay = autoDeleteDelay;
      return this;
   }

   public Long getAutoDeleteMessageCount() {
      return autoDeleteMessageCount;
   }

   public QueueConfiguration setAutoDeleteMessageCount(Long autoDeleteMessageCount) {
      this.autoDeleteMessageCount = autoDeleteMessageCount;
      return this;
   }

   public Long getRingSize() {
      return ringSize;
   }

   public QueueConfiguration setRingSize(Long ringSize) {
      this.ringSize = ringSize;
      return this;
   }

   /**
    * defaults to {@code false}
    */
   public Boolean isConfigurationManaged() {
      return Objects.requireNonNullElse(configurationManaged, false);
   }

   public QueueConfiguration setConfigurationManaged(Boolean configurationManaged) {
      this.configurationManaged = configurationManaged;
      return this;
   }

   /**
    * defaults to {@code false}
    */
   public Boolean isTemporary() {
      return Objects.requireNonNullElse(temporary, false);
   }

   public QueueConfiguration setTemporary(Boolean temporary) {
      this.temporary = temporary;
      return this;
   }

   public Boolean isAutoCreateAddress() {
      return autoCreateAddress;
   }

   public QueueConfiguration setAutoCreateAddress(Boolean autoCreateAddress) {
      this.autoCreateAddress = autoCreateAddress;
      return this;
   }

   /**
    * defaults to {@code false}
    */
   public Boolean isInternal() {
      return Objects.requireNonNullElse(internal, false);
   }

   public QueueConfiguration setInternal(Boolean internal) {
      this.internal = internal;
      return this;
   }

   /**
    * defaults to {@code false}
    */
   public Boolean isTransient() {
      return Objects.requireNonNullElse(_transient, false);
   }

   public QueueConfiguration setTransient(Boolean _transient) {
      this._transient = _transient;
      return this;
   }

   /**
    * defaults to {@code false}
    */
   public Boolean isAutoCreated() {
      return Objects.requireNonNullElse(autoCreated, false);
   }

   public QueueConfiguration setAutoCreated(Boolean autoCreated) {
      this.autoCreated = autoCreated;
      return this;
   }

   /**
    * Based on if the name or address uses FQQN when set
    *
    * defaults to {@code false}
    */
   public Boolean isFqqn() {
      return Objects.requireNonNullElse(fqqn, Boolean.FALSE);
   }

   /**
    * This method returns a JSON-formatted {@code String} representation of this {@code QueueConfiguration}. It is a
    * simple collection of key/value pairs. The keys used are referenced in {@link #set(String, String)}. For example:
    * <pre>
    * {
    *   "name": "myQueue",
    *   "address": "myAddress",
    *   "routing-type": "ANYCAST",
    *   "durable": true
    * }
    * </pre>
    *
    * @return a JSON-formatted {@code String} representation of this {@code QueueConfiguration}
    */
   public String toJSON() {
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();

      if (getId() != null) {
         builder.add(ID, getId());
      }
      if (getName() != null) {
         builder.add(NAME, getName().toString());
      }
      if (getAddress() != null) {
         builder.add(ADDRESS, getAddress().toString());
      }
      if (getRoutingType() != null) {
         builder.add(ROUTING_TYPE, getRoutingType().toString().toUpperCase());
      }
      if (getFilterString() != null) {
         builder.add(FILTER_STRING, getFilterString().toString());
      }
      if (isDurable() != null) {
         builder.add(DURABLE, isDurable());
      }
      if (getUser() != null) {
         builder.add(USER, getUser().toString());
      }
      if (getMaxConsumers() != null) {
         builder.add(MAX_CONSUMERS, getMaxConsumers());
      }
      if (isExclusive() != null) {
         builder.add(EXCLUSIVE, isExclusive());
      }
      if (isGroupRebalance() != null) {
         builder.add(GROUP_REBALANCE, isGroupRebalance());
      }
      if (isGroupRebalancePauseDispatch() != null) {
         builder.add(GROUP_REBALANCE_PAUSE_DISPATCH, isGroupRebalancePauseDispatch());
      }
      if (getGroupBuckets() != null) {
         builder.add(GROUP_BUCKETS, getGroupBuckets());
      }
      if (getGroupFirstKey() != null) {
         builder.add(GROUP_FIRST_KEY, getGroupFirstKey().toString());
      }
      if (isLastValue() != null) {
         builder.add(LAST_VALUE, isLastValue());
      }
      if (getLastValueKey() != null) {
         builder.add(LAST_VALUE_KEY, getLastValueKey().toString());
      }
      if (isNonDestructive() != null) {
         builder.add(NON_DESTRUCTIVE, isNonDestructive());
      }
      if (isPurgeOnNoConsumers() != null) {
         builder.add(PURGE_ON_NO_CONSUMERS, isPurgeOnNoConsumers());
      }
      if (isEnabled() != null) {
         builder.add(ENABLED, isEnabled());
      }
      if (getConsumersBeforeDispatch() != null) {
         builder.add(CONSUMERS_BEFORE_DISPATCH, getConsumersBeforeDispatch());
      }
      if (getDelayBeforeDispatch() != null) {
         builder.add(DELAY_BEFORE_DISPATCH, getDelayBeforeDispatch());
      }
      if (getConsumerPriority() != null) {
         builder.add(CONSUMER_PRIORITY, getConsumerPriority());
      }
      if (isAutoDelete() != null) {
         builder.add(AUTO_DELETE, isAutoDelete());
      }
      if (getAutoDeleteDelay() != null) {
         builder.add(AUTO_DELETE_DELAY, getAutoDeleteDelay());
      }
      if (getAutoDeleteMessageCount() != null) {
         builder.add(AUTO_DELETE_MESSAGE_COUNT, getAutoDeleteMessageCount());
      }
      if (getRingSize() != null) {
         builder.add(RING_SIZE, getRingSize());
      }
      if (isConfigurationManaged() != null) {
         builder.add(CONFIGURATION_MANAGED, isConfigurationManaged());
      }
      if (isTemporary() != null) {
         builder.add(TEMPORARY, isTemporary());
      }
      if (isAutoCreateAddress() != null) {
         builder.add(AUTO_CREATE_ADDRESS, isAutoCreateAddress());
      }
      if (isInternal() != null) {
         builder.add(INTERNAL, isInternal());
      }
      if (isTransient() != null) {
         builder.add(TRANSIENT, isTransient());
      }
      if (isAutoCreated() != null) {
         builder.add(AUTO_CREATED, isAutoCreated());
      }
      if (isFqqn() != null) {
         builder.add(FQQN, isFqqn());
      }

      return builder.build().toString();
   }

   /**
    * This method returns a {@code QueueConfiguration} created from the JSON-formatted input {@code String}. The input
    * should be a simple object of key/value pairs. Valid keys are referenced in {@link #set(String, String)}. For
    * example:
    * <pre>
    * {
    *   "name": "myQueue",
    *   "address": "myAddress",
    *   "routing-type": "ANYCAST",
    *   "durable": true
    * }
    * </pre>
    *
    * @return the {@code QueueConfiguration} created from the JSON-formatted input {@code String}
    */
   public static QueueConfiguration fromJSON(String jsonString) {
      JsonObject json = JsonLoader.readObject(new StringReader(jsonString));

      // name is the only required value
      if (!json.containsKey(NAME)) {
         return null;
      }
      QueueConfiguration result = QueueConfiguration.of(json.getString(NAME));

      for (Map.Entry<String, JsonValue> entry : json.entrySet()) {
         result.set(entry.getKey(), entry.getValue().getValueType() == JsonValue.ValueType.STRING ? ((JsonString)entry.getValue()).getString() : entry.getValue().toString());
      }

      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof QueueConfiguration other)) {
         return false;
      }

      return Objects.equals(id, other.id) &&
             Objects.equals(name, other.name) &&
             Objects.equals(address, other.address) &&
             Objects.equals(routingType, other.routingType) &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(durable, other.durable) &&
             Objects.equals(user, other.user) &&
             Objects.equals(maxConsumers, other.maxConsumers) &&
             Objects.equals(exclusive, other.exclusive) &&
             Objects.equals(groupRebalance, other.groupRebalance) &&
             Objects.equals(groupRebalancePauseDispatch, other.groupRebalancePauseDispatch) &&
             Objects.equals(groupBuckets, other.groupBuckets) &&
             Objects.equals(groupFirstKey, other.groupFirstKey) &&
             Objects.equals(lastValue, other.lastValue) &&
             Objects.equals(lastValueKey, other.lastValueKey) &&
             Objects.equals(nonDestructive, other.nonDestructive) &&
             Objects.equals(purgeOnNoConsumers, other.purgeOnNoConsumers) &&
             Objects.equals(enabled, other.enabled) &&
             Objects.equals(consumersBeforeDispatch, other.consumersBeforeDispatch) &&
             Objects.equals(delayBeforeDispatch, other.delayBeforeDispatch) &&
             Objects.equals(consumerPriority, other.consumerPriority) &&
             Objects.equals(autoDelete, other.autoDelete) &&
             Objects.equals(autoDeleteDelay, other.autoDeleteDelay) &&
             Objects.equals(autoDeleteMessageCount, other.autoDeleteMessageCount) &&
             Objects.equals(ringSize, other.ringSize) &&
             Objects.equals(configurationManaged, other.configurationManaged) &&
             Objects.equals(temporary, other.temporary) &&
             Objects.equals(autoCreateAddress, other.autoCreateAddress) &&
             Objects.equals(internal, other.internal) &&
             Objects.equals(_transient, other._transient) &&
             Objects.equals(autoCreated, other.autoCreated) &&
             Objects.equals(fqqn, other.fqqn);
   }

   public boolean isMirrorQueue() {
      return isInternal() && name != null && name.toString().startsWith(MIRROR_ADDRESS);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, name, address, routingType, filterString, durable, user, maxConsumers, exclusive,
                          groupRebalance, groupRebalancePauseDispatch, groupBuckets, groupFirstKey, lastValue,
                          lastValueKey, nonDestructive, purgeOnNoConsumers, enabled, consumersBeforeDispatch,
                          delayBeforeDispatch, consumerPriority, autoDelete, autoDeleteDelay, autoDeleteMessageCount,
                          ringSize, configurationManaged, temporary, autoCreateAddress, internal, _transient,
                          autoCreated, fqqn);
   }

   @Override
   public String toString() {
      return "QueueConfiguration ["
         + "id=" + id
         + ", name=" + name
         + ", address=" + address
         + ", routingType=" + routingType
         + ", filterString=" + filterString
         + ", durable=" + durable
         + ", user=" + user
         + ", maxConsumers=" + maxConsumers
         + ", exclusive=" + exclusive
         + ", groupRebalance=" + groupRebalance
         + ", groupRebalancePauseDispatch=" + groupRebalancePauseDispatch
         + ", groupBuckets=" + groupBuckets
         + ", groupFirstKey=" + groupFirstKey
         + ", lastValue=" + lastValue
         + ", lastValueKey=" + lastValueKey
         + ", nonDestructive=" + nonDestructive
         + ", purgeOnNoConsumers=" + purgeOnNoConsumers
         + ", enabled=" + enabled
         + ", consumersBeforeDispatch=" + consumersBeforeDispatch
         + ", delayBeforeDispatch=" + delayBeforeDispatch
         + ", consumerPriority=" + consumerPriority
         + ", autoDelete=" + autoDelete
         + ", autoDeleteDelay=" + autoDeleteDelay
         + ", autoDeleteMessageCount=" + autoDeleteMessageCount
         + ", ringSize=" + ringSize
         + ", configurationManaged=" + configurationManaged
         + ", temporary=" + temporary
         + ", autoCreateAddress=" + autoCreateAddress
         + ", internal=" + internal
         + ", transient=" + _transient
         + ", autoCreated=" + autoCreated
         + ", fqqn=" + fqqn + ']';
   }
}
