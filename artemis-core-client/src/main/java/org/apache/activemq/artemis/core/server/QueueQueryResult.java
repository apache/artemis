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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;

public class QueueQueryResult {

   private QueueConfiguration config;

   private boolean exists;

   private int consumerCount;

   private long messageCount;

   private boolean autoCreateQueues;

   private Integer defaultConsumerWindowSize;

   public QueueQueryResult(final QueueConfiguration config,
                           final int consumerCount,
                           final long messageCount,
                           final boolean autoCreateQueues,
                           final boolean exists,
                           final Integer defaultConsumerWindowSize) {
      this.config = config;

      this.consumerCount = consumerCount;

      this.messageCount = messageCount;

      this.autoCreateQueues = autoCreateQueues;

      this.exists = exists;

      this.defaultConsumerWindowSize = defaultConsumerWindowSize;
   }

   public boolean isExists() {
      return exists;
   }

   public boolean isDurable() {
      return config.isDurable();
   }

   public int getConsumerCount() {
      return consumerCount;
   }

   public long getMessageCount() {
      return messageCount;
   }

   public SimpleString getFilterString() {
      return config.getFilterString();
   }

   public SimpleString getAddress() {
      return config.getAddress();
   }

   public SimpleString getName() {
      return config.getName();
   }

   public boolean isTemporary() {
      return config.isTemporary();
   }

   public boolean isAutoCreateQueues() {
      return autoCreateQueues;
   }

   public boolean isAutoCreated() {
      return config.isAutoCreated();
   }

   public boolean isPurgeOnNoConsumers() {
      return config.isPurgeOnNoConsumers();
   }

   public RoutingType getRoutingType() {
      return config.getRoutingType();
   }

   public int getMaxConsumers() {
      return config.getMaxConsumers();
   }

   public void setAddress(SimpleString address) {
      config.setAddress(address);
   }

   public Boolean isExclusive() {
      return config.isExclusive();
   }

   public Boolean isLastValue() {
      return config.isLastValue();
   }

   public SimpleString getLastValueKey() {
      return config.getLastValueKey();
   }

   public Boolean isNonDestructive() {
      return config.isNonDestructive();
   }

   public Integer getConsumersBeforeDispatch() {
      return config.getConsumersBeforeDispatch();
   }

   public Long getDelayBeforeDispatch() {
      return config.getDelayBeforeDispatch();
   }

   public Integer getDefaultConsumerWindowSize() {
      return defaultConsumerWindowSize;
   }

   public Boolean isGroupRebalance() {
      return config.isGroupRebalance();
   }

   public Boolean isGroupRebalancePauseDispatch() {
      return config.isGroupRebalancePauseDispatch();
   }

   public Integer getGroupBuckets() {
      return config.getGroupBuckets();
   }

   public SimpleString getGroupFirstKey() {
      return config.getGroupFirstKey();
   }

   public Boolean isAutoDelete() {
      return config.isAutoDelete();
   }

   public Long getAutoDeleteDelay() {
      return config.getAutoDeleteDelay();
   }

   public Long getAutoDeleteMessageCount() {
      return config.getAutoDeleteMessageCount();
   }

   public Long getRingSize() {
      return config.getRingSize();
   }

   public Boolean isEnabled() {
      return config.isEnabled();
   }

   public Boolean isConfigurationManaged() {
      return config.isConfigurationManaged();
   }
}
