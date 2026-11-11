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

public enum ConsumerField {
   ID("id", "consumerID"),
   SEQUENTIAL_ID("sequentialId", "sequentialId"),
   SESSION("session", "sessionID"),
   CONNECTION("connection", "connectionID"),
   QUEUE("queue", "queueName"),
   FILTER("filter"),
   ADDRESS("address"),
   USER("user"),
   VALIDATED_USER("validatedUser"),
   PROTOCOL("protocol"),
   CLIENT_ID("clientID"),
   LOCAL_ADDRESS("localAddress"),
   REMOTE_ADDRESS("remoteAddress"),
   QUEUE_TYPE("queueType"),
   BROWSE_ONLY("browseOnly"),
   CREATION_TIME("creationTime"),
   MESSAGES_IN_TRANSIT("messagesInTransit", "deliveringCount"),
   MESSAGES_IN_TRANSIT_SIZE("messagesInTransitSize"),
   MESSAGES_DELIVERED("messagesDelivered"),
   MESSAGES_DELIVERED_SIZE("messagesDeliveredSize"),
   MESSAGES_ACKNOWLEDGED("messagesAcknowledged"),
   MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT("messagesAcknowledgedAwaitingCommit"),
   LAST_DELIVERED_TIME("lastDeliveredTime"),
   LAST_ACKNOWLEDGED_TIME("lastAcknowledgedTime"),
   STATUS("status");


   private static final Map<String, ConsumerField> lookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

   static {
      for (ConsumerField e: values()) {
         lookup.put(e.name, e);
      }
   }

   private final String name;

   private final String alternativeName;

   public String getName() {
      return name;
   }

   /**
    * There is some inconsistency with some json objects returned for consumers because they were hard coded. This is
    * just to track the differences and provide backward compatibility.
    *
    * @return the old alternative name
    */
   public String getAlternativeName() {
      return alternativeName;
   }

   ConsumerField(String name) {
      this(name, "");
   }

   ConsumerField(String name, String alternativeName) {
      this.name = name;
      this.alternativeName = alternativeName;
   }

   public static ConsumerField valueOfName(String name) {
      return lookup.get(name);
   }
}
