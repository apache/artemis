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

public class ClusterConnectionMetrics {

   public static final String MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY = "messagesPendingAcknowledgement";
   public static final String MESSAGES_ACKNOWLEDGED_KEY = "messagesAcknowledged";

   private final long messagesPendingAcknowledgement;
   private final long messagesAcknowledged;

   public ClusterConnectionMetrics(long messagesPendingAcknowledgement, long messagesAcknowledged) {
      super();
      this.messagesPendingAcknowledgement = messagesPendingAcknowledgement;
      this.messagesAcknowledged = messagesAcknowledged;
   }

   public long getMessagesPendingAcknowledgement() {
      return messagesPendingAcknowledgement;
   }

   public long getMessagesAcknowledged() {
      return messagesAcknowledged;
   }

   /**
    * {@return New map containing the Cluster Connection metrics}
    */
   public Map<String, Object> convertToMap() {
      final Map<String, Object> metrics = new HashMap<>();
      metrics.put(MESSAGES_PENDING_ACKNOWLEDGEMENT_KEY, messagesPendingAcknowledgement);
      metrics.put(MESSAGES_ACKNOWLEDGED_KEY, messagesAcknowledged);

      return metrics;
   }
}
