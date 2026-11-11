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

import io.netty.channel.EventLoopGroup;

public class ProducerBenchmarkBuilder {

   private ConnectionFactory factory;
   private MicrosTimeProvider timeProvider;
   private EventLoopGroup loopGroup;
   private int producers;
   private long messageCount;
   private String group;
   private long ttl;
   private int messageSize;
   private Destination[] destinations;
   private boolean persistent;
   private long maxPending;
   private long transactionCapacity;
   private Long messageRate;
   private boolean sharedConnections;
   private boolean enableMessageID;
   private boolean enableTimestamp;

   public ProducerBenchmarkBuilder setFactory(final ConnectionFactory factory) {
      this.factory = factory;
      return this;
   }

   public ProducerBenchmarkBuilder setTimeProvider(final MicrosTimeProvider timeProvider) {
      this.timeProvider = timeProvider;
      return this;
   }

   public ProducerBenchmarkBuilder setLoopGroup(final EventLoopGroup loopGroup) {
      this.loopGroup = loopGroup;
      return this;
   }

   public ProducerBenchmarkBuilder setProducers(final int producers) {
      this.producers = producers;
      return this;
   }

   public ProducerBenchmarkBuilder setMessageCount(final long messageCount) {
      this.messageCount = messageCount;
      return this;
   }

   public ProducerBenchmarkBuilder setSharedConnections(final boolean sharedConnections) {
      this.sharedConnections = sharedConnections;
      return this;
   }

   public ProducerBenchmarkBuilder setGroup(final String group) {
      this.group = group;
      return this;
   }

   public ProducerBenchmarkBuilder setTtl(final long ttl) {
      this.ttl = ttl;
      return this;
   }

   public ProducerBenchmarkBuilder setMessageSize(final int messageSize) {
      this.messageSize = messageSize;
      return this;
   }

   public ProducerBenchmarkBuilder setDestinations(final Destination[] destinations) {
      this.destinations = destinations;
      return this;
   }

   public ProducerBenchmarkBuilder setPersistent(final boolean persistent) {
      this.persistent = persistent;
      return this;
   }

   public ProducerBenchmarkBuilder setMaxPending(final long maxPending) {
      this.maxPending = maxPending;
      return this;
   }

   public ProducerBenchmarkBuilder setTransactionCapacity(final long transactionCapacity) {
      this.transactionCapacity = transactionCapacity;
      return this;
   }

   public ProducerBenchmarkBuilder setMessageRate(final Long messageRate) {
      this.messageRate = messageRate;
      return this;
   }

   public ProducerBenchmarkBuilder setEnableTimestamp(final boolean enableTimestamp) {
      this.enableTimestamp = enableTimestamp;
      return this;
   }

   public ProducerBenchmarkBuilder setEnableMessageID(final boolean enableMessageID) {
      this.enableMessageID = enableMessageID;
      return this;
   }

   public ProducerBenchmark createProducerBenchmark() {
      return new ProducerBenchmark(factory, timeProvider, loopGroup, producers, messageCount, sharedConnections,
                                   group, ttl, messageSize, destinations, persistent, maxPending,
                                   transactionCapacity, messageRate, enableMessageID, enableTimestamp);
   }
}