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
package org.apache.activemq.artemis.cli.commands.messages.perf;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;

public class MessageListenerBenchmarkBuilder {

   private ConnectionFactory factory;
   private MicrosTimeProvider timeProvider;
   private int consumers;
   private long messageCount;
   private int connections;
   private String clientID;
   private Destination[] destinations;
   private boolean transaction;
   private long commitInterval;
   private int sharedSubscription;
   private boolean durableSubscription;
   private boolean canDelayMessageCount;

   public MessageListenerBenchmarkBuilder setFactory(final ConnectionFactory factory) {
      this.factory = factory;
      return this;
   }

   public MessageListenerBenchmarkBuilder setTimeProvider(final MicrosTimeProvider timeProvider) {
      this.timeProvider = timeProvider;
      return this;
   }

   public MessageListenerBenchmarkBuilder setConsumers(final int consumers) {
      this.consumers = consumers;
      return this;
   }

   public MessageListenerBenchmarkBuilder setMessageCount(final long messageCount) {
      this.messageCount = messageCount;
      return this;
   }

   public MessageListenerBenchmarkBuilder setConnections(final int connections) {
      this.connections = connections;
      return this;
   }

   public MessageListenerBenchmarkBuilder setClientID(final String clientID) {
      this.clientID = clientID;
      return this;
   }

   public MessageListenerBenchmarkBuilder setDestinations(final Destination[] destinations) {
      this.destinations = destinations;
      return this;
   }

   public MessageListenerBenchmarkBuilder setTransacted(final boolean transacted) {
      this.transaction = transacted;
      return this;
   }

   public MessageListenerBenchmarkBuilder setCommitInterval(final long commitInterval) {
      this.commitInterval = commitInterval;
      return this;
   }

   public MessageListenerBenchmarkBuilder setSharedSubscription(final int sharedSubscription) {
      this.sharedSubscription = sharedSubscription;
      return this;
   }

   public MessageListenerBenchmarkBuilder setDurableSubscription(final boolean durableSubscription) {
      this.durableSubscription = durableSubscription;
      return this;
   }

   public MessageListenerBenchmarkBuilder setCanDelayMessageCount(final boolean canDelayMessageCount) {
      this.canDelayMessageCount = canDelayMessageCount;
      return this;
   }

   public MessageListenerBenchmark createMessageListenerBenchmark() {
      return new MessageListenerBenchmark(factory, timeProvider, consumers, messageCount, connections, clientID,
                                          destinations, transaction, commitInterval, sharedSubscription, durableSubscription,
                                          canDelayMessageCount);
   }
}