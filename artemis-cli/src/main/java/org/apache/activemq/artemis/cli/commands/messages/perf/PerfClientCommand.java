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
import java.util.Collections;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionProtocol;
import org.apache.activemq.artemis.cli.factory.ConnectionFactoryClosable;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "client", description = "Produce messages to and consume messages from a broker instance.")
public class PerfClientCommand extends PerfCommand {

   @Option(names = "--tx", description = "Perform Message::acknowledge per each message received. Default: disabled.")
   protected boolean transaction;

   @Option(names = "--shared", description = "Create a shared subscription. Default: 0.")
   protected int sharedSubscription = 0;

   @Option(names = "--durable", description = "Enabled durable subscription. Default: disabled.")
   protected boolean durableSubscription = false;

   @Option(names = "--consumer-connections", description = "Number of consumer connections to be used. Default: same as the total number of consumers")
   protected int consumerConnections = 0;

   @Option(names = "--consumers", description = "Number of consumer to use for each generated destination. Default: 1.")
   protected int consumersPerDestination = 1;

   @Option(names = "--persistent", description = "Send messages persistently. Default: non persistent")
   protected boolean persistent = false;

   @Option(names = "--message-size", description = "Size of each bytesMessage. Default: is 1024.")
   protected int messageSize = 1024;

   @Option(names = "--rate", description = "Expected total message rate. Default: unbounded.")
   protected Long rate = null;

   @Option(names = "--ttl", description = "TTL for each message.")
   protected long ttl = 0L;

   @Option(names = "--group", description = "Message Group to be used.")
   protected String msgGroupID = null;

   @Option(names = "--shared-connections", description = "Create --threads shared connections among producers. Default: not shared.")
   protected boolean sharedConnections = false;

   @Option(names = "--producers", description = "Number of producers to use for each generated destination. Default: 1")
   protected int producersPerDestination = 1;

   @Option(names = "--threads", description = "Number of worker threads to schedule producer load tasks. Default: 1.")
   protected int threads = 1;

   @Option(names = "--max-pending", description = "How many not yet completed messages can exists. Default: 1.")
   protected long maxPending = 1;

   @Option(names = "--consumer-url", description = "The url used for MessageListener(s) connections. Default: same as --url.")
   protected String consumerUrl = null;

   @Option(names = "--consumer-protocol", description = "The protocol used for MessageListener(s) connections. Default: same as --protocol. Valid values are ${COMPLETION-CANDIDATES}", converter = ConnectionProtocol.ProtocolConverter.class)
   protected ConnectionProtocol consumerProtocol = null;

   @Option(names = "--enable-msg-id", description = "Set JMS messageID per-message. Default: disabled.")
   protected boolean enableMessageID;

   @Option(names = "--enable-timestamp", description = "Set JMS timestamp per-message. Default: disabled.")
   protected boolean enableTimestamp;

   private volatile BenchmarkService producerBenchmark;

   @Override
   protected void onExecuteBenchmark(final ConnectionFactory producerConnectionFactory, final Destination[] jmsDestinations, final ActionContext context) throws Exception {
      final ConnectionProtocol listenerProtocol = Objects.requireNonNullElse(this.consumerProtocol, protocol);
      final String listenerUrl = Objects.requireNonNullElse(this.consumerUrl, brokerURL);
      if (consumerConnections == 0) {
         if (sharedSubscription > 0) {
            if (getClientID() == null) {
               consumerConnections = sharedSubscription * consumersPerDestination * jmsDestinations.length;
            } else {
               consumerConnections = sharedSubscription * jmsDestinations.length;
            }
         } else {
            consumerConnections = consumersPerDestination * jmsDestinations.length;
         }
      }
      final int totalProducers = producersPerDestination * jmsDestinations.length;

      if (threads >= totalProducers) {
         if (threads > totalProducers) {
            context.err.println("Doesn't make sense to set workers > producers: auto-adjusting it to be the same as the producer count");
            threads = totalProducers;
         }
      }

      boolean warmingUp = warmup != 0;
      final LiveStatistics statistics;
      final StringBuilder skratchBuffer = new StringBuilder();
      try (ConnectionFactoryClosable consumerConnectionFactory = createConnectionFactory(listenerUrl, user, password, null, listenerProtocol);
           MessageListenerBenchmark consumerBenchmark = new MessageListenerBenchmarkBuilder()
            .setClientID(getClientID())
            .setDestinations(consumerProtocol != null ? lookupDestinations(consumerConnectionFactory) : jmsDestinations)
            .setFactory(consumerConnectionFactory)
            .setTransacted(transaction)
            .setConsumers(consumersPerDestination)
            .setConnections(consumerConnections)
            .setTimeProvider(() -> TimeUnit.NANOSECONDS.toMicros(System.nanoTime()))
            .setCanDelayMessageCount(true)
            .setSharedSubscription(sharedSubscription)
            .setDurableSubscription(durableSubscription)
            .createMessageListenerBenchmark()) {

         final DefaultEventLoopGroup eventLoopGroup = new DefaultEventLoopGroup(threads) {
            @Override
            protected EventLoop newChild(final Executor executor, final Object... args) {
               return new DefaultEventLoop(this, executor) {
                  @Override
                  protected Queue<Runnable> newTaskQueue(final int maxPendingTasks) {
                     return new LinkedTransferQueue<>();
                  }
               };
            }
         };
         try (ProducerBenchmark producerBenchmark = new ProducerBenchmarkBuilder()
            .setPersistent(persistent)
            .setDestinations(jmsDestinations)
            .setFactory(producerConnectionFactory)
            .setTtl(ttl)
            .setTransactionCapacity(commitInterval)
            .setGroup(msgGroupID)
            .setProducers(producersPerDestination)
            .setMessageRate(rate)
            .setMessageCount(messageCount)
            .setMessageSize(messageSize)
            .setTimeProvider(() -> TimeUnit.NANOSECONDS.toMicros(System.nanoTime()))
            .setLoopGroup(eventLoopGroup)
            .setMaxPending(maxPending)
            .setSharedConnections(sharedConnections)
            .setEnableMessageID(enableMessageID)
            .setEnableTimestamp(enableTimestamp)
               .createProducerBenchmark()) {
            this.producerBenchmark = producerBenchmark;
            consumerBenchmark.start();
            producerBenchmark.start();
            final long now = System.currentTimeMillis();
            final long endWarmup = warmup > 0 ? now + TimeUnit.SECONDS.toMillis(warmup) : 0;
            final long end = duration > 0 ? now + TimeUnit.SECONDS.toMillis(duration) : 0;
            statistics = new LiveStatistics(reportFileName, hdrFileName, producerBenchmark.getGenerators(), consumerBenchmark.getListeners());
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            warmingUp = collectAndReportStatisticsWhileRunning(warmingUp, statistics, skratchBuffer, endWarmup, end, producerBenchmark);
            final boolean producerFatalError = producerBenchmark.anyError();
            producerBenchmark.asyncClose();
            if (!producerFatalError) {
               consumerBenchmark.setMessageCount(producerBenchmark.expectedTotalMessageCountToReceive(sharedSubscription, consumersPerDestination));
               // we don't care about duration here, but just on the expected messages to receive
               warmingUp = collectAndReportStatisticsWhileRunning(warmingUp, statistics, skratchBuffer, endWarmup, 0, consumerBenchmark);
            }
         }
         // last sample must be collected while the whole benchmark is complete
         statistics.sampleMetrics(warmingUp);
         skratchBuffer.setLength(0);
         statistics.outSummary(skratchBuffer);
         if (!isSilentInput()) {
            context.out.println(skratchBuffer);
         }
         eventLoopGroup.shutdownGracefully();
         statistics.close();
      }
   }

   @Override
   protected void onInterruptBenchmark() {
      final BenchmarkService benchmark = this.producerBenchmark;
      if (benchmark != null) {
         benchmark.close();
      }
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      if (durableSubscription && (destinations == null || destinations.isEmpty())) {
         // An empty destination list would create a single queue://TEST destination but durable subscriptions require
         // topic destinations instead.
         destinations = Collections.singletonList(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + "TEST");
      }

      if (durableSubscription && clientID == null) {
         throw new IllegalArgumentException("The clientID must be set on durable subscriptions");
      }

      return super.execute(context);
   }

   public boolean isTransaction() {
      return transaction;
   }

   public PerfClientCommand setTransaction(boolean transaction) {
      this.transaction = transaction;
      return this;
   }

   public int getSharedSubscription() {
      return sharedSubscription;
   }

   public PerfClientCommand setSharedSubscription(int sharedSubscription) {
      this.sharedSubscription = sharedSubscription;
      return this;
   }

   public boolean isDurableSubscription() {
      return durableSubscription;
   }

   public PerfClientCommand setDurableSubscription(boolean durableSubscription) {
      this.durableSubscription = durableSubscription;
      return this;
   }

   public int getConsumerConnections() {
      return consumerConnections;
   }

   public PerfClientCommand setConsumerConnections(int consumerConnections) {
      this.consumerConnections = consumerConnections;
      return this;
   }

   public int getConsumersPerDestination() {
      return consumersPerDestination;
   }

   public PerfClientCommand setConsumersPerDestination(int consumersPerDestination) {
      this.consumersPerDestination = consumersPerDestination;
      return this;
   }

   public boolean isPersistent() {
      return persistent;
   }

   public PerfClientCommand setPersistent(boolean persistent) {
      this.persistent = persistent;
      return this;
   }

   public int getMessageSize() {
      return messageSize;
   }

   public PerfClientCommand setMessageSize(int messageSize) {
      this.messageSize = messageSize;
      return this;
   }

   public Long getRate() {
      return rate;
   }

   public PerfClientCommand setRate(Long rate) {
      this.rate = rate;
      return this;
   }

   public long getTtl() {
      return ttl;
   }

   public PerfClientCommand setTtl(long ttl) {
      this.ttl = ttl;
      return this;
   }

   public String getMsgGroupID() {
      return msgGroupID;
   }

   public PerfClientCommand setMsgGroupID(String msgGroupID) {
      this.msgGroupID = msgGroupID;
      return this;
   }

   public boolean isSharedConnections() {
      return sharedConnections;
   }

   public PerfClientCommand setSharedConnections(boolean sharedConnections) {
      this.sharedConnections = sharedConnections;
      return this;
   }

   public int getProducersPerDestination() {
      return producersPerDestination;
   }

   public PerfClientCommand setProducersPerDestination(int producersPerDestination) {
      this.producersPerDestination = producersPerDestination;
      return this;
   }

   public int getThreads() {
      return threads;
   }

   public PerfClientCommand setThreads(int threads) {
      this.threads = threads;
      return this;
   }

   public long getMaxPending() {
      return maxPending;
   }

   public PerfClientCommand setMaxPending(long maxPending) {
      this.maxPending = maxPending;
      return this;
   }

   public String getConsumerUrl() {
      return consumerUrl;
   }

   public PerfClientCommand setConsumerUrl(String consumerUrl) {
      this.consumerUrl = consumerUrl;
      return this;
   }

   public String getConsumerProtocol() {
      return consumerProtocol.toString();
   }

   public PerfClientCommand setConsumerProtocol(String consumerProtocol) {
      this.consumerProtocol = ConnectionProtocol.fromString(consumerProtocol);
      return this;
   }

   public boolean isEnableMessageID() {
      return enableMessageID;
   }

   public PerfClientCommand setEnableMessageID(boolean enableMessageID) {
      this.enableMessageID = enableMessageID;
      return this;
   }

   public boolean isEnableTimestamp() {
      return enableTimestamp;
   }

   public PerfClientCommand setEnableTimestamp(boolean enableTimestamp) {
      this.enableTimestamp = enableTimestamp;
      return this;
   }

   public BenchmarkService getProducerBenchmark() {
      return producerBenchmark;
   }

   public PerfClientCommand setProducerBenchmark(BenchmarkService producerBenchmark) {
      this.producerBenchmark = producerBenchmark;
      return this;
   }
}
