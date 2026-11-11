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
package org.apache.activemq.artemis.utils;

import org.apache.activemq.artemis.api.core.SimpleString;

public class DestinationUtil {

   public static final String QUEUE_QUALIFIED_PREFIX = "queue://";
   public static final String TOPIC_QUALIFIED_PREFIX = "topic://";
   public static final String TEMP_QUEUE_QUALIFED_PREFIX = "temp-queue://";
   public static final String TEMP_TOPIC_QUALIFED_PREFIX = "temp-topic://";

   public static final char SEPARATOR = '.';

   private static String escape(final String input) {
      if (input == null) {
         return "";
      }
      return input.replace("\\", "\\\\").replace(".", "\\.");
   }

   public static String createQueueNameForSharedSubscription(final boolean isDurable,
                                                             final String clientID,
                                                             final String subscriptionName) {
      if (clientID != null) {
         return (isDurable ? "Durable" : "nonDurable") + SEPARATOR +
            escape(clientID) + SEPARATOR +
            escape(subscriptionName);
      } else {
         return (isDurable ? "Durable" : "nonDurable") + SEPARATOR +
            escape(subscriptionName);
      }
   }

   public static SimpleString createQueueNameForSubscription(final boolean isDurable,
                                                             final String clientID,
                                                             final String subscriptionName) {
      final String queueName;
      if (clientID != null) {
         if (isDurable) {
            queueName = escape(clientID) + SEPARATOR +
               escape(subscriptionName);
         } else {
            queueName = "nonDurable" + SEPARATOR +
               escape(clientID) + SEPARATOR +
               escape(subscriptionName);
         }
      } else {
         if (isDurable) {
            queueName = escape(subscriptionName);
         } else {
            queueName = "nonDurable" + SEPARATOR +
               escape(subscriptionName);
         }
      }
      return SimpleString.of(queueName);
   }

}
