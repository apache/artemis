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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A Metrics class that supports nesting to provide various elements in the bridge
 * space a view of its own Metrics for bridging operations.
 */
public final class AMQPBridgeMetrics {

   private final AMQPBridgeMetrics parent;

   private static final AtomicLongFieldUpdater<AMQPBridgeMetrics> MESSAGES_SENT_UPDATER =
      AtomicLongFieldUpdater.newUpdater(AMQPBridgeMetrics.class, "messagesSent");
   private static final AtomicLongFieldUpdater<AMQPBridgeMetrics> MESSAGES_RECEIVED_UPDATER =
      AtomicLongFieldUpdater.newUpdater(AMQPBridgeMetrics.class, "messagesReceived");

   private volatile long messagesSent;
   private volatile long messagesReceived;

   public AMQPBridgeMetrics() {
      this(null);
   }

   private AMQPBridgeMetrics(AMQPBridgeMetrics parent) {
      this.parent = parent;
   }

   public long getMessagesSent() {
      return messagesSent;
   }

   public long getMessagesReceived() {
      return messagesReceived;
   }

   AMQPBridgeMetrics newPolicyMetrics() {
      return new AMQPBridgeMetrics(this);
   }

   ReceiverMetrics newReceiverMetrics() {
      return new ReceiverMetrics(this);
   }

   SenderMetrics newSenderMetrics() {
      return new SenderMetrics(this);
   }

   /**
    * Metrics for a single receiver instance that will also updates to the parents
    * when the Metrics are updated by the receiver.
    */
   public static class ReceiverMetrics {

      private static final AtomicLongFieldUpdater<ReceiverMetrics> MESSAGES_RECEIVED_UPDATER =
         AtomicLongFieldUpdater.newUpdater(ReceiverMetrics.class, "messagesReceived");

      private final AMQPBridgeMetrics parent;

      private volatile long messagesReceived;

      private ReceiverMetrics(AMQPBridgeMetrics parent) {
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
    * Metrics for a single sender instance that will also updates to the parents
    * when the Metrics are updated by the sender.
    */
   public static class SenderMetrics {

      private static final AtomicLongFieldUpdater<SenderMetrics> MESSAGES_SENT_UPDATER =
         AtomicLongFieldUpdater.newUpdater(SenderMetrics.class, "messagesSent");

      private final AMQPBridgeMetrics parent;

      private volatile long messagesSent;

      private SenderMetrics(AMQPBridgeMetrics parent) {
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
