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
package org.apache.activemq.artemis.core.server.metrics;

public class QueueMetricNames {

   public static final String MESSAGE_COUNT = "message.count";
   public static final String DURABLE_MESSAGE_COUNT = "durable.message.count";
   public static final String PERSISTENT_SIZE = "persistent.size";
   public static final String DURABLE_PERSISTENT_SIZE = "durable.persistent.size";

   public static final String DELIVERING_MESSAGE_COUNT = "delivering.message.count";
   public static final String DELIVERING_DURABLE_MESSAGE_COUNT = "delivering.durable.message.count";
   public static final String DELIVERING_PERSISTENT_SIZE = "delivering.persistent_size";
   public static final String DELIVERING_DURABLE_PERSISTENT_SIZE = "delivering.durable.persistent.size";

   public static final String SCHEDULED_MESSAGE_COUNT = "scheduled.message.count";
   public static final String SCHEDULED_DURABLE_MESSAGE_COUNT = "scheduled.durable.message.count";
   public static final String SCHEDULED_PERSISTENT_SIZE = "scheduled.persistent.size";
   public static final String SCHEDULED_DURABLE_PERSISTENT_SIZE = "scheduled.durable.persistent.size";

   public static final String MESSAGES_ACKNOWLEDGED = "messages.acknowledged";
   public static final String MESSAGES_ADDED = "messages.added";
   public static final String MESSAGES_KILLED = "messages.killed";
   public static final String MESSAGES_EXPIRED = "messages.expired";
   public static final String CONSUMER_COUNT = "consumer.count";
}
