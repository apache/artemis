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
package org.apache.activemq.artemis.core.client.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;

public class AddressQueryImpl implements ClientSession.AddressQuery {

   private final boolean exists;

   private final List<SimpleString> queueNames;

   private final boolean autoCreateQueues;

   private final boolean autoCreateAddresses;

   private final boolean defaultPurgeOnNoConsumers;

   private final int defaultMaxConsumers;

   private final Boolean defaultExclusive;

   private final Boolean defaultLastValue;

   private final SimpleString defaultLastValueKey;

   private final Boolean defaultNonDestructive;

   private final Integer defaultConsumersBeforeDispatch;

   private final Long defaultDelayBeforeDispatch;

   private final boolean supportsMulticast;

   private final boolean supportsAnycast;

   public AddressQueryImpl(final boolean exists,
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
                           final Long defaultDelayBeforeDispatch,
                           final boolean supportsMulticast,
                           final boolean supportsAnycast) {
      this.exists = exists;
      this.queueNames = new ArrayList<>(queueNames);
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
      this.supportsMulticast = supportsMulticast;
      this.supportsAnycast = supportsAnycast;
   }

   @Override
   public List<SimpleString> getQueueNames() {
      return queueNames;
   }

   @Override
   public boolean isExists() {
      return exists;
   }

   @Override
   public boolean isAutoCreateQueues() {
      return autoCreateQueues;
   }

   @Override
   public boolean isAutoCreateAddresses() {
      return autoCreateAddresses;
   }

   @Override
   public boolean isDefaultPurgeOnNoConsumers() {
      return defaultPurgeOnNoConsumers;
   }

   @Override
   public int getDefaultMaxConsumers() {
      return defaultMaxConsumers;
   }

   @Override
   public Boolean isDefaultLastValueQueue() {
      return defaultLastValue;
   }

   @Override
   public Boolean isDefaultExclusive() {
      return defaultExclusive;
   }

   @Override
   public SimpleString getDefaultLastValueKey() {
      return defaultLastValueKey;
   }

   @Override
   public Boolean isDefaultNonDestructive() {
      return defaultNonDestructive;
   }

   @Override
   public Integer getDefaultConsumersBeforeDispatch() {
      return defaultConsumersBeforeDispatch;
   }

   @Override
   public Long getDefaultDelayBeforeDispatch() {
      return defaultDelayBeforeDispatch;
   }

   @Override
   public boolean isSupportsMulticast() {
      return supportsMulticast;
   }

   @Override
   public boolean isSupportsAnycast() {
      return supportsAnycast;
   }
}
