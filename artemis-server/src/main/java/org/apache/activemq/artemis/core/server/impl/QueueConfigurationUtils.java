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
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

public class QueueConfigurationUtils {

   /**
    * This method inspects the {@code QueueConfiguration} and applies default values to it based on the
    * {@code AddressSettings} as well as {@code static} defaults. The {@code static} values are applied only after the
    * values from the {@code AddressSettings} are applied. Values are only changed to defaults if they are
    * {@code null}.
    *
    * @param config the {@code QueueConfiguration} to modify with default values
    * @param as     the {@code AddressSettings} to use when applying dynamic default values
    */
   public static void applyDefaults(final QueueConfiguration config, AddressSettings as) {
      applyDynamicDefaults(config, as);
      applyStaticDefaults(config);
   }

   /**
    * This method inspects the {@code QueueConfiguration} and applies default values to it based on the
    * {@code AddressSettings}. Values are only changed to defaults if they are {@code null}.
    *
    * @param config the {@code QueueConfiguration} to modify with default values
    * @param as     the {@code AddressSettings} to use when applying dynamic default values
    */
   public static void applyDynamicDefaults(final QueueConfiguration config, AddressSettings as) {
      if (config.getMaxConsumers() == null) {
         config.setMaxConsumers(as.getDefaultMaxConsumers());
      }
      if (config.isExclusive() == null) {
         config.setExclusive(as.isDefaultExclusiveQueue());
      }
      if (config.isGroupRebalance() == null) {
         config.setGroupRebalance(as.isDefaultGroupRebalance());
      }
      if (config.isGroupRebalancePauseDispatch() == null) {
         config.setGroupRebalancePauseDispatch(as.isDefaultGroupRebalancePauseDispatch());
      }
      if (config.getGroupBuckets() == null) {
         config.setGroupBuckets(as.getDefaultGroupBuckets());
      }
      if (config.getGroupFirstKey() == null) {
         config.setGroupFirstKey(as.getDefaultGroupFirstKey());
      }
      if (config.isLastValue() == null) {
         config.setLastValue(as.isDefaultLastValueQueue());
      }
      if (config.getLastValueKey() == null) {
         config.setLastValueKey(as.getDefaultLastValueKey());
      }
      if (config.isNonDestructive() == null) {
         config.setNonDestructive(as.isDefaultNonDestructive());
      }
      if (config.getConsumersBeforeDispatch() == null) {
         config.setConsumersBeforeDispatch(as.getDefaultConsumersBeforeDispatch());
      }
      if (config.getDelayBeforeDispatch() == null) {
         config.setDelayBeforeDispatch(as.getDefaultDelayBeforeDispatch());
      }
      if (config.getRingSize() == null) {
         config.setRingSize(as.getDefaultRingSize());
      }
      if (config.getRoutingType() == null) {
         config.setRoutingType(as.getDefaultQueueRoutingType());
      }
      if (config.isPurgeOnNoConsumers() == null) {
         config.setPurgeOnNoConsumers(as.isDefaultPurgeOnNoConsumers());
      }
      if (config.isAutoCreateAddress() == null) {
         config.setAutoCreateAddress(as.isAutoCreateAddresses());
      }
      if (config.isAutoDelete() == null) {
         config.setAutoDelete(!config.isConfigurationManaged() && ((config.isAutoCreated() && as.isAutoDeleteQueues()) || (!config.isAutoCreated() && as.isAutoDeleteCreatedQueues())));
      }
      if (config.getAutoDeleteDelay() == null) {
         config.setAutoDeleteDelay(as.getAutoDeleteQueuesDelay());
      }
      if (config.getAutoDeleteMessageCount() == null) {
         config.setAutoDeleteMessageCount(as.getAutoDeleteQueuesMessageCount());
      }
   }

   /**
    * This method inspects the {@code QueueConfiguration} and applies default values to it based on the {@code static}
    * defaults. Values are only changed to defaults if they are {@code null}.
    * <p>
    * Static defaults are not applied directly in {@code QueueConfiguration} because {@code null} values allow us to
    * determine whether the fields have actually been set. This allows us, for example, to omit unset fields from JSON
    * payloads during queue-related management operations.
    *
    * @param config the {@code QueueConfiguration} to modify with default values
    */
   public static void applyStaticDefaults(final QueueConfiguration config) {
      if (config.getMaxConsumers() == null) {
         config.setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers());
      }
      if (config.getRoutingType() == null) {
         config.setRoutingType(ActiveMQDefaultConfiguration.getDefaultRoutingType());
      }
      if (config.isExclusive() == null) {
         config.setExclusive(ActiveMQDefaultConfiguration.getDefaultExclusive());
      }
      if (config.isNonDestructive() == null) {
         config.setNonDestructive(ActiveMQDefaultConfiguration.getDefaultNonDestructive());
      }
      if (config.isPurgeOnNoConsumers() == null) {
         config.setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers());
      }
      if (config.isEnabled() == null) {
         config.setEnabled(ActiveMQDefaultConfiguration.getDefaultEnabled());
      }
      if (config.getConsumersBeforeDispatch() == null) {
         config.setConsumersBeforeDispatch(ActiveMQDefaultConfiguration.getDefaultConsumersBeforeDispatch());
      }
      if (config.getDelayBeforeDispatch() == null) {
         config.setDelayBeforeDispatch(ActiveMQDefaultConfiguration.getDefaultDelayBeforeDispatch());
      }
      if (config.isGroupRebalance() == null) {
         config.setGroupRebalance(ActiveMQDefaultConfiguration.getDefaultGroupRebalance());
      }
      if (config.isGroupRebalancePauseDispatch() == null) {
         config.setGroupRebalancePauseDispatch(ActiveMQDefaultConfiguration.getDefaultGroupRebalancePauseDispatch());
      }
      if (config.getGroupBuckets() == null) {
         config.setGroupBuckets(ActiveMQDefaultConfiguration.getDefaultGroupBuckets());
      }
      if (config.getGroupFirstKey() == null) {
         config.setGroupFirstKey(ActiveMQDefaultConfiguration.getDefaultGroupFirstKey());
      }
      if (config.isAutoDelete() == null) {
         config.setAutoDelete(ActiveMQDefaultConfiguration.getDefaultQueueAutoDelete(config.isAutoCreated()));
      }
      if (config.getAutoDeleteDelay() == null) {
         config.setAutoDeleteDelay(ActiveMQDefaultConfiguration.getDefaultQueueAutoDeleteDelay());
      }
      if (config.getAutoDeleteMessageCount() == null) {
         config.setAutoDeleteMessageCount(ActiveMQDefaultConfiguration.getDefaultQueueAutoDeleteMessageCount());
      }
      if (config.getRingSize() == null) {
         config.setRingSize(ActiveMQDefaultConfiguration.getDefaultRingSize());
      }
      if (config.isLastValue() == null) {
         config.setLastValue(ActiveMQDefaultConfiguration.getDefaultLastValue());
      }
      if (config.getLastValueKey() == null) {
         config.setLastValueKey(ActiveMQDefaultConfiguration.getDefaultLastValueKey());
      }
      if (config.isInternal() == null) {
         config.setInternal(ActiveMQDefaultConfiguration.getDefaultInternal());
      }
   }
}
