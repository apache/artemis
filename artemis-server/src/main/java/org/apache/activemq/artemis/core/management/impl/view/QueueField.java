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
package org.apache.activemq.artemis.core.management.impl.view;

import java.util.Map;
import java.util.TreeMap;

public enum QueueField {
   ID("id"),
   NAME("name"),
   CONSUMER_ID("consumerID"),
   ADDRESS("address"),
   MAX_CONSUMERS("maxConsumers"),
   FILTER("filter"),
   MESSAGE_COUNT("messageCount"),
   CONSUMER_COUNT("consumerCount"),
   DELIVERING_COUNT("deliveringCount"),
   MESSAGES_ADDED("messagesAdded"),
   MESSAGES_ACKED("messagesAcked"),
   MESSAGES_EXPIRED("messagesExpired"),
   ROUTING_TYPE("routingType"),
   USER("user"),
   AUTO_CREATED("autoCreated"),
   DURABLE("durable"),
   PAUSED("paused"),
   PERSISTEDPAUSE("persistedPause"),
   TEMPORARY("temporary"),
   PURGE_ON_NO_CONSUMERS("purgeOnNoConsumers"),
   MESSAGES_KILLED("messagesKilled"),
   DIRECT_DELIVER("directDeliver"),
   LAST_VALUE("lastValue"),
   EXCLUSIVE("exclusive"),
   SCHEDULED_COUNT("scheduledCount"),
   LAST_VALUE_KEY("lastValueKey"),
   GROUP_REBALANCE("groupRebalance"),
   GROUP_REBALANCE_PAUSE_DISPATCH("groupRebalancePauseDispatch"),
   GROUP_BUCKETS("groupBuckets"),
   GROUP_FIRST_KEY("groupFirstKey"),
   ENABLED("enabled"),
   RING_SIZE("ringSize"),
   CONSUMERS_BEFORE_DISPATCH("consumersBeforeDispatch"),
   DELAY_BEFORE_DISPATCH("delayBeforeDispatch"),
   AUTO_DELETE("autoDelete"),
   INTERNAL_QUEUE("internalQueue");

   private static final Map<String, QueueField> lookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

   static {
      for (QueueField e: values()) {
         lookup.put(e.name, e);
      }
   }

   private final String name;

   public String getName() {
      return name;
   }

   QueueField(String name) {
      this.name = name;
   }

   public static QueueField valueOfName(String name) {
      return lookup.get(name);
   }
}
