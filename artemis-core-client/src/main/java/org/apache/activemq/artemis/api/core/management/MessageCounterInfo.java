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

import org.apache.activemq.artemis.json.JsonObject;

import org.apache.activemq.artemis.api.core.JsonUtil;

/**
 * Helper class to create Java Objects from the JSON serialization returned by
 * {@link QueueControl#listMessageCounter()}.
 */
public final class MessageCounterInfo {

   private final String name;

   private final String subscription;

   private final boolean durable;

   private final long count;

   private final long countDelta;

   private final int depth;

   private final int depthDelta;

   private final String lastAddTimestamp;

   private final String lastAckTimestamp;

   private final String updateTimestamp;

   /**
    * {@return a MessageCounterInfo corresponding to the JSON serialization returned by {@link
    * QueueControl#listMessageCounter()}}
    */
   public static MessageCounterInfo fromJSON(final String jsonString) throws Exception {
      JsonObject data = JsonUtil.readJsonObject(jsonString);
      String name = data.getString("destinationName");
      String subscription = data.getString("destinationSubscription", null);
      boolean durable = data.getBoolean("destinationDurable");
      long count = data.getJsonNumber("count").longValue();
      long countDelta = data.getJsonNumber("countDelta").longValue();
      int depth = data.getInt("messageCount");
      int depthDelta = data.getInt("messageCountDelta");
      String lastAddTimestamp = data.getString("lastAddTimestamp");
      String lastAckTimestamp = data.getString("lastAckTimestamp");
      String updateTimestamp = data.getString("updateTimestamp");

      return new MessageCounterInfo(name, subscription, durable, count, countDelta, depth, depthDelta, lastAddTimestamp, lastAckTimestamp, updateTimestamp);
   }


   public MessageCounterInfo(final String name,
                             final String subscription,
                             final boolean durable,
                             final long count,
                             final long countDelta,
                             final int depth,
                             final int depthDelta,
                             final String lastAddTimestamp,
                             final String lastAckTimestamp,
                             final String updateTimestamp) {
      this.name = name;
      this.subscription = subscription;
      this.durable = durable;
      this.count = count;
      this.countDelta = countDelta;
      this.depth = depth;
      this.depthDelta = depthDelta;
      this.lastAddTimestamp = lastAddTimestamp;
      this.lastAckTimestamp = lastAckTimestamp;
      this.updateTimestamp = updateTimestamp;
   }

   /**
    * {@return the name of the queue}
    */
   public String getName() {
      return name;
   }

   /**
    * {@return the name of the subscription}
    */
   public String getSubscription() {
      return subscription;
   }

   /**
    * {@return whether the queue is durable}
    */
   public boolean isDurable() {
      return durable;
   }

   /**
    * {@return the number of messages added to the queue since it was created}
    */
   public long getCount() {
      return count;
   }

   /**
    * {@return the number of messages added to the queue since the last counter sample}
    */
   public long getCountDelta() {
      return countDelta;
   }

   /**
    * {@return the number of messages currently in the queue}
    */
   public int getDepth() {
      return depth;
   }

   /**
    * {@return the number of messages in the queue since last counter sample}
    */
   public int getDepthDelta() {
      return depthDelta;
   }

   /**
    * {@return the timestamp of the last time a message was added to the queue}
    */
   public String getLastAddTimestamp() {
      return lastAddTimestamp;
   }

   /**
    * {@return the timestamp of the last time a message from the queue was acknolwedged}
    */
   public String getLastAckTimestamp() {
      return lastAckTimestamp;
   }

   /**
    * {@return the timestamp of the last time the queue was updated}
    */
   public String getUpdateTimestamp() {
      return updateTimestamp;
   }

   /**
    * Spelling error in public API. Remove in next major release.
    */
   @Deprecated
   public String getUdpateTimestamp() {
      return updateTimestamp;
   }
}
