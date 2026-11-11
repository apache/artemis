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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A Metrics class that supports nesting to provide various elements in the federation space a view of its own Metrics
 * for federation operations.
 */
public final class AMQPFederationMetrics {

   private final AMQPFederationMetrics parent;

   private static final AtomicLongFieldUpdater<AMQPFederationMetrics> MESSAGES_SENT_UPDATER =
      AtomicLongFieldUpdater.newUpdater(AMQPFederationMetrics.class, "messagesSent");
   private static final AtomicLongFieldUpdater<AMQPFederationMetrics> MESSAGES_RECEIVED_UPDATER =
      AtomicLongFieldUpdater.newUpdater(AMQPFederationMetrics.class, "messagesReceived");

   private volatile long messagesSent;
   private volatile long messagesReceived;

   public AMQPFederationMetrics() {
      this(null);
   }

   private AMQPFederationMetrics(AMQPFederationMetrics parent) {
      this.parent = parent;
   }

   public long getMessagesSent() {
      return messagesSent;
   }

   public long getMessagesReceived() {
      return messagesReceived;
   }

   AMQPFederationMetrics newPolicyMetrics() {
      return new AMQPFederationMetrics(this);
   }

   ConsumerMetrics newConsumerMetrics() {
      return new ConsumerMetrics(this);
   }

   ProducerMetrics newProducerMetrics() {
      return new ProducerMetrics(this);
   }

   /**
    * Metrics for a single consumer instance that will also updates to the parents when the Metrics are updated by the
    * consumer.
    */
   public static class ConsumerMetrics {

      private static final AtomicLongFieldUpdater<ConsumerMetrics> MESSAGES_RECEIVED_UPDATER =
         AtomicLongFieldUpdater.newUpdater(ConsumerMetrics.class, "messagesReceived");

      private final AMQPFederationMetrics parent;

      private volatile long messagesReceived;

      private ConsumerMetrics(AMQPFederationMetrics parent) {
         this.parent = parent;
      }

      public long getMessagesReceived() {
         return messagesReceived;
      }

      public void incrementMessagesReceived() {
         MESSAGES_RECEIVED_UPDATER.incrementAndGet(this);
         parent.incrementMessagesReceived();
      }
   }

   /**
    * Metrics for a single producer instance that will also updates to the parents when the Metrics are updated by the
    * producer.
    */
   public static class ProducerMetrics {

      private static final AtomicLongFieldUpdater<ProducerMetrics> MESSAGES_SENT_UPDATER =
         AtomicLongFieldUpdater.newUpdater(ProducerMetrics.class, "messagesSent");

      private final AMQPFederationMetrics parent;

      private volatile long messagesSent;

      private ProducerMetrics(AMQPFederationMetrics parent) {
         this.parent = parent;
      }

      public long getMessagesSent() {
         return messagesSent;
      }

      public void incrementMessagesSent() {
         MESSAGES_SENT_UPDATER.incrementAndGet(this);
         parent.incrementMessagesSent();
      }
   }

   // Should only have the senders and receivers doing updates of these and then
   // have those results trickle up to the parent.

   private void incrementMessagesSent() {
      MESSAGES_SENT_UPDATER.incrementAndGet(this);
      if (parent != null) {
         parent.incrementMessagesSent();
      }
   }

   private void incrementMessagesReceived() {
      MESSAGES_RECEIVED_UPDATER.incrementAndGet(this);
      if (parent != null) {
         parent.incrementMessagesReceived();
      }
   }
}
