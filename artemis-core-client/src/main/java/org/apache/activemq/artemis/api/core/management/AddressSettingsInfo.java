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
package org.apache.activemq.artemis.api.core.management;

import javax.json.JsonObject;

import org.apache.activemq.artemis.api.core.JsonUtil;

// XXX no javadocs
public final class AddressSettingsInfo {

   private final String addressFullMessagePolicy;

   private final long maxSizeBytes;

   private final int pageSizeBytes;

   private int pageCacheMaxSize;

   private final int maxDeliveryAttempts;

   private final double redeliveryMultiplier;

   private final long maxRedeliveryDelay;

   private final long redeliveryDelay;

   private final String deadLetterAddress;

   private final String expiryAddress;

   private final boolean lastValueQueue;

   private final long redistributionDelay;

   private final boolean sendToDLAOnNoRoute;

   private final long slowConsumerThreshold;

   private final long slowConsumerCheckPeriod;

   private final String slowConsumerPolicy;

   private final boolean autoCreateJmsQueues;

   private final boolean autoDeleteJmsQueues;

   private final boolean autoCreateJmsTopics;

   private final boolean autoDeleteJmsTopics;

   private final boolean autoCreateQueues;

   private final boolean autoDeleteQueues;

   private final boolean autoCreateAddresses;

   private final boolean autoDeleteAddresses;

   private final String configDeleteQueues;

   private final String configDeleteAddresses;

   private final long maxSizeBytesRejectThreshold;

   private final String defaultLastValueKey;

   private final boolean defaultNonDestructive;

   private final boolean defaultExclusiveQueue;

   private final boolean defaultGroupRebalance;

   private final int defaultGroupBuckets;

   private final String defaultGroupFirstKey;

   private final int defaultMaxConsumers;

   private final boolean defaultPurgeOnNoConsumers;

   private final int defaultConsumersBeforeDispatch;

   private final long defaultDelayBeforeDispatch;

   private final String defaultQueueRoutingType;

   private final String defaultAddressRoutingType;

   private final int defaultConsumerWindowSize;

   private final long defaultRingSize;

   private final boolean autoDeleteCreatedQueues;

   private final long autoDeleteQueuesDelay;

   private final long autoDeleteQueuesMessageCount;

   private final long autoDeleteAddressesDelay;

   private final double redeliveryCollisionAvoidanceFactor;

   private final long retroactiveMessageCount;

   private final boolean autoCreateDeadLetterResources;

   private final String deadLetterQueuePrefix;

   private final String deadLetterQueueSuffix;

   private final boolean autoCreateExpiryResources;

   private final String expiryQueuePrefix;

   private final String expiryQueueSuffix;

   private final long expiryDelay;

   private final long minExpiryDelay;

   private final long maxExpiryDelay;

   private final boolean enableMetrics;


   public static AddressSettingsInfo from(final String jsonString) {
      JsonObject object = JsonUtil.readJsonObject(jsonString);
      return new AddressSettingsInfo(object.getString("addressFullMessagePolicy"),
                                     object.getJsonNumber("maxSizeBytes").longValue(),
                                     object.getInt("pageSizeBytes"),
                                     object.getInt("pageCacheMaxSize"),
                                     object.getInt("maxDeliveryAttempts"),
                                     object.getJsonNumber("redeliveryDelay").longValue(),
                                     object.getJsonNumber("redeliveryMultiplier").doubleValue(),
                                     object.getJsonNumber("maxRedeliveryDelay").longValue(),
                                     object.getString("DLA"),
                                     object.getString("expiryAddress"),
                                     object.getBoolean("lastValueQueue"),
                                     object.getJsonNumber("redistributionDelay").longValue(),
                                     object.getBoolean("sendToDLAOnNoRoute"),
                                     object.getJsonNumber("slowConsumerThreshold").longValue(),
                                     object.getJsonNumber("slowConsumerCheckPeriod").longValue(),
                                     object.getString("slowConsumerPolicy"),
                                     object.getBoolean("autoCreateJmsQueues"),
                                     object.getBoolean("autoCreateJmsTopics"),
                                     object.getBoolean("autoDeleteJmsQueues"),
                                     object.getBoolean("autoDeleteJmsTopics"),
                                     object.getBoolean("autoCreateQueues"),
                                     object.getBoolean("autoDeleteQueues"),
                                     object.getBoolean("autoCreateAddresses"),
                                     object.getBoolean("autoDeleteAddresses"),
                                     object.getString("configDeleteQueues"),
                                     object.getString("configDeleteAddresses"),
                                     object.getJsonNumber("maxSizeBytesRejectThreshold").longValue(),
                                     object.getString("defaultLastValueKey"),
                                     object.getBoolean("defaultNonDestructive"),
                                     object.getBoolean("defaultExclusiveQueue"),
                                     object.getBoolean("defaultGroupRebalance"),
                                     object.getInt("defaultGroupBuckets"),
                                     object.getString("defaultGroupFirstKey"),
                                     object.getInt("defaultMaxConsumers"),
                                     object.getBoolean("defaultPurgeOnNoConsumers"),
                                     object.getInt("defaultConsumersBeforeDispatch"),
                                     object.getJsonNumber("defaultDelayBeforeDispatch").longValue(),
                                     object.getString("defaultQueueRoutingType"),
                                     object.getString("defaultAddressRoutingType"),
                                     object.getInt("defaultConsumerWindowSize"),
                                     object.getJsonNumber("defaultRingSize").longValue(),
                                     object.getBoolean("autoDeleteCreatedQueues"),
                                     object.getJsonNumber("autoDeleteQueuesDelay").longValue(),
                                     object.getJsonNumber("autoDeleteQueuesMessageCount").longValue(),
                                     object.getJsonNumber("autoDeleteAddressesDelay").longValue(),
                                     object.getJsonNumber("redeliveryCollisionAvoidanceFactor").doubleValue(),
                                     object.getJsonNumber("retroactiveMessageCount").longValue(),
                                     object.getBoolean("autoCreateDeadLetterResources"),
                                     object.getString("deadLetterQueuePrefix"),
                                     object.getString("deadLetterQueueSuffix"),
                                     object.getBoolean("autoCreateExpiryResources"),
                                     object.getString("expiryQueuePrefix"),
                                     object.getString("expiryQueueSuffix"),
                                     object.getJsonNumber("expiryDelay").longValue(),
                                     object.getJsonNumber("minExpiryDelay").longValue(),
                                     object.getJsonNumber("maxExpiryDelay").longValue(),
                                     object.getBoolean("enableMetrics"));
   }


   public AddressSettingsInfo(String addressFullMessagePolicy,
                              long maxSizeBytes,
                              int pageSizeBytes,
                              int pageCacheMaxSize,
                              int maxDeliveryAttempts,
                              long redeliveryDelay,
                              double redeliveryMultiplier,
                              long maxRedeliveryDelay,
                              String deadLetterAddress,
                              String expiryAddress,
                              boolean lastValueQueue,
                              long redistributionDelay,
                              boolean sendToDLAOnNoRoute,
                              long slowConsumerThreshold,
                              long slowConsumerCheckPeriod,
                              String slowConsumerPolicy,
                              boolean autoCreateJmsQueues,
                              boolean autoCreateJmsTopics,
                              boolean autoDeleteJmsQueues,
                              boolean autoDeleteJmsTopics,
                              boolean autoCreateQueues,
                              boolean autoDeleteQueues,
                              boolean autoCreateAddresses,
                              boolean autoDeleteAddresses,
                              String configDeleteQueues,
                              String configDeleteAddresses,
                              long maxSizeBytesRejectThreshold,
                              String defaultLastValueKey,
                              boolean defaultNonDestructive,
                              boolean defaultExclusiveQueue,
                              boolean defaultGroupRebalance,
                              int defaultGroupBuckets,
                              String defaultGroupFirstKey,
                              int defaultMaxConsumers,
                              boolean defaultPurgeOnNoConsumers,
                              int defaultConsumersBeforeDispatch,
                              long defaultDelayBeforeDispatch,
                              String defaultQueueRoutingType,
                              String defaultAddressRoutingType,
                              int defaultConsumerWindowSize,
                              long defaultRingSize,
                              boolean autoDeleteCreatedQueues,
                              long autoDeleteQueuesDelay,
                              long autoDeleteQueuesMessageCount,
                              long autoDeleteAddressesDelay,
                              double redeliveryCollisionAvoidanceFactor,
                              long retroactiveMessageCount,
                              boolean autoCreateDeadLetterResources,
                              String deadLetterQueuePrefix,
                              String deadLetterQueueSuffix,
                              boolean autoCreateExpiryResources,
                              String expiryQueuePrefix,
                              String expiryQueueSuffix,
                              long expiryDelay,
                              long minExpiryDelay,
                              long maxExpiryDelay,
                              boolean enableMetrics) {
      this.addressFullMessagePolicy = addressFullMessagePolicy;
      this.maxSizeBytes = maxSizeBytes;
      this.pageSizeBytes = pageSizeBytes;
      this.pageCacheMaxSize = pageCacheMaxSize;
      this.maxDeliveryAttempts = maxDeliveryAttempts;
      this.redeliveryDelay = redeliveryDelay;
      this.redeliveryMultiplier = redeliveryMultiplier;
      this.maxRedeliveryDelay = maxRedeliveryDelay;
      this.deadLetterAddress = deadLetterAddress;
      this.expiryAddress = expiryAddress;
      this.lastValueQueue = lastValueQueue;
      this.redistributionDelay = redistributionDelay;
      this.sendToDLAOnNoRoute = sendToDLAOnNoRoute;
      this.slowConsumerThreshold = slowConsumerThreshold;
      this.slowConsumerCheckPeriod = slowConsumerCheckPeriod;
      this.slowConsumerPolicy = slowConsumerPolicy;
      this.autoCreateJmsQueues = autoCreateJmsQueues;
      this.autoDeleteJmsQueues = autoDeleteJmsQueues;
      this.autoCreateJmsTopics = autoCreateJmsTopics;
      this.autoDeleteJmsTopics = autoDeleteJmsTopics;
      this.autoCreateQueues = autoCreateQueues;
      this.autoDeleteQueues = autoDeleteQueues;
      this.autoCreateAddresses = autoCreateAddresses;
      this.autoDeleteAddresses = autoDeleteAddresses;
      this.configDeleteQueues = configDeleteQueues;
      this.configDeleteAddresses = configDeleteAddresses;
      this.maxSizeBytesRejectThreshold = maxSizeBytesRejectThreshold;
      this.defaultLastValueKey = defaultLastValueKey;
      this.defaultNonDestructive = defaultNonDestructive;
      this.defaultExclusiveQueue = defaultExclusiveQueue;
      this.defaultGroupRebalance = defaultGroupRebalance;
      this.defaultGroupBuckets = defaultGroupBuckets;
      this.defaultGroupFirstKey = defaultGroupFirstKey;
      this.defaultMaxConsumers = defaultMaxConsumers;
      this.defaultPurgeOnNoConsumers = defaultPurgeOnNoConsumers;
      this.defaultConsumersBeforeDispatch = defaultConsumersBeforeDispatch;
      this.defaultDelayBeforeDispatch = defaultDelayBeforeDispatch;
      this.defaultQueueRoutingType = defaultQueueRoutingType;
      this.defaultAddressRoutingType = defaultAddressRoutingType;
      this.defaultConsumerWindowSize = defaultConsumerWindowSize;
      this.defaultRingSize = defaultRingSize;
      this.autoDeleteCreatedQueues = autoDeleteCreatedQueues;
      this.autoDeleteQueuesDelay = autoDeleteQueuesDelay;
      this.autoDeleteQueuesMessageCount = autoDeleteQueuesMessageCount;
      this.autoDeleteAddressesDelay = autoDeleteAddressesDelay;
      this.redeliveryCollisionAvoidanceFactor = redeliveryCollisionAvoidanceFactor;
      this.retroactiveMessageCount = retroactiveMessageCount;
      this.autoCreateDeadLetterResources = autoCreateDeadLetterResources;
      this.deadLetterQueuePrefix = deadLetterQueuePrefix;
      this.deadLetterQueueSuffix = deadLetterQueueSuffix;
      this.autoCreateExpiryResources = autoCreateExpiryResources;
      this.expiryQueuePrefix = expiryQueuePrefix;
      this.expiryQueueSuffix = expiryQueueSuffix;
      this.expiryDelay = expiryDelay;
      this.minExpiryDelay = minExpiryDelay;
      this.maxExpiryDelay = maxExpiryDelay;
      this.enableMetrics = enableMetrics;
   }

   public int getPageCacheMaxSize() {
      return pageCacheMaxSize;
   }

   public void setPageCacheMaxSize(int pageCacheMaxSize) {
      this.pageCacheMaxSize = pageCacheMaxSize;
   }

   public String getAddressFullMessagePolicy() {
      return addressFullMessagePolicy;
   }

   public long getMaxSizeBytes() {
      return maxSizeBytes;
   }

   public int getPageSizeBytes() {
      return pageSizeBytes;
   }

   public int getMaxDeliveryAttempts() {
      return maxDeliveryAttempts;
   }

   public long getRedeliveryDelay() {
      return redeliveryDelay;
   }

   public String getDeadLetterAddress() {
      return deadLetterAddress;
   }

   public String getExpiryAddress() {
      return expiryAddress;
   }

   public boolean isLastValueQueue() {
      return lastValueQueue;
   }

   public long getRedistributionDelay() {
      return redistributionDelay;
   }

   public boolean isSendToDLAOnNoRoute() {
      return sendToDLAOnNoRoute;
   }

   public double getRedeliveryMultiplier() {
      return redeliveryMultiplier;
   }

   public long getMaxRedeliveryDelay() {
      return maxRedeliveryDelay;
   }

   public long getSlowConsumerThreshold() {
      return slowConsumerThreshold;
   }

   public long getSlowConsumerCheckPeriod() {
      return slowConsumerCheckPeriod;
   }

   public String getSlowConsumerPolicy() {
      return slowConsumerPolicy;
   }

   @Deprecated
   public boolean isAutoCreateJmsQueues() {
      return autoCreateJmsQueues;
   }

   @Deprecated
   public boolean isAutoDeleteJmsQueues() {
      return autoDeleteJmsQueues;
   }

   @Deprecated
   public boolean isAutoCreateJmsTopics() {
      return autoCreateJmsTopics;
   }

   @Deprecated
   public boolean isAutoDeleteJmsTopics() {
      return autoDeleteJmsTopics;
   }

   public boolean isAutoCreateQueues() {
      return autoCreateQueues;
   }

   public boolean isAutoDeleteQueues() {
      return autoDeleteQueues;
   }

   public boolean isAutoCreateAddresses() {
      return autoCreateAddresses;
   }

   public boolean isAutoDeleteAddresses() {
      return autoDeleteAddresses;
   }

   public String getConfigDeleteQueues() {
      return configDeleteQueues;
   }

   public String getConfigDeleteAddresses() {
      return configDeleteAddresses;
   }

   public long getMaxSizeBytesRejectThreshold() {
      return maxSizeBytesRejectThreshold;
   }

   public String getDefaultLastValueKey() {
      return defaultLastValueKey;
   }

   public boolean isDefaultNonDestructive() {
      return defaultNonDestructive;
   }

   public boolean isDefaultExclusiveQueue() {
      return defaultExclusiveQueue;
   }

   public boolean isDefaultGroupRebalance() {
      return defaultGroupRebalance;
   }

   public int getDefaultGroupBuckets() {
      return defaultGroupBuckets;
   }

   public String getDefaultGroupFirstKey() {
      return defaultGroupFirstKey;
   }

   public int getDefaultMaxConsumers() {
      return defaultMaxConsumers;
   }

   public boolean isDefaultPurgeOnNoConsumers() {
      return defaultPurgeOnNoConsumers;
   }

   public int getDefaultConsumersBeforeDispatch() {
      return defaultConsumersBeforeDispatch;
   }

   public long getDefaultDelayBeforeDispatch() {
      return defaultDelayBeforeDispatch;
   }

   public String getDefaultQueueRoutingType() {
      return defaultQueueRoutingType;
   }

   public String getDefaultAddressRoutingType() {
      return defaultAddressRoutingType;
   }

   public int getDefaultConsumerWindowSize() {
      return defaultConsumerWindowSize;
   }

   public long getDefaultRingSize() {
      return defaultRingSize;
   }

   public boolean isAutoDeleteCreatedQueues() {
      return autoDeleteCreatedQueues;
   }

   public long getAutoDeleteQueuesDelay() {
      return autoDeleteQueuesDelay;
   }

   public long getAutoDeleteQueuesMessageCount() {
      return autoDeleteQueuesMessageCount;
   }

   public long getAutoDeleteAddressesDelay() {
      return autoDeleteAddressesDelay;
   }

   public double getRedeliveryCollisionAvoidanceFactor() {
      return redeliveryCollisionAvoidanceFactor;
   }

   public long getRetroactiveMessageCount() {
      return retroactiveMessageCount;
   }

   public boolean isAutoCreateDeadLetterResources() {
      return autoCreateDeadLetterResources;
   }

   public String getDeadLetterQueuePrefix() {
      return deadLetterQueuePrefix;
   }

   public String getDeadLetterQueueSuffix() {
      return deadLetterQueueSuffix;
   }

   public boolean isAutoCreateExpiryResources() {
      return autoCreateExpiryResources;
   }

   public String getExpiryQueuePrefix() {
      return expiryQueuePrefix;
   }

   public String getExpiryQueueSuffix() {
      return expiryQueueSuffix;
   }

   public long getExpiryDelay() {
      return expiryDelay;
   }

   public long getMinExpiryDelay() {
      return minExpiryDelay;
   }

   public long getMaxExpiryDelay() {
      return maxExpiryDelay;
   }

   public boolean isEnableMetrics() {
      return enableMetrics;
   }
}

