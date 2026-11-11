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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class BridgeMetrics {

   public static final String MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY = "messagesPendingAcknowledgement";
   public static final String MESSAGES_ACKNOWLEDGED_KEY = "messagesAcknowledged";

   private static final AtomicLongFieldUpdater<BridgeMetrics> MESSAGES_PENDING_ACKNOWLEDGEMENT_UPDATER =
         AtomicLongFieldUpdater.newUpdater(BridgeMetrics.class, MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY);

   private static final AtomicLongFieldUpdater<BridgeMetrics> MESSAGES_ACKNOWLEDGED_UPDATER =
         AtomicLongFieldUpdater.newUpdater(BridgeMetrics.class, MESSAGES_ACKNOWLEDGED_KEY);

   private volatile long messagesPendingAcknowledgement;
   private volatile long messagesAcknowledged;

   public void incrementMessagesPendingAcknowledgement() {
      MESSAGES_PENDING_ACKNOWLEDGEMENT_UPDATER.incrementAndGet(this);
   }

   public void incrementMessagesAcknowledged() {
      MESSAGES_ACKNOWLEDGED_UPDATER.incrementAndGet(this);
   }

   public long getMessagesPendingAcknowledgement() {
      return messagesPendingAcknowledgement;
   }

   public long getMessagesAcknowledged() {
      return messagesAcknowledged;
   }

   /**
    * {@return <em>new</em> {@code Map} containing the Bridge metrics}
    */
   public Map<String, Object> convertToMap() {
      final Map<String, Object> metrics = new HashMap<>();
      metrics.put(MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY, messagesPendingAcknowledgement);
      metrics.put(MESSAGES_ACKNOWLEDGED_KEY, messagesAcknowledged);

      return metrics;
   }
}
