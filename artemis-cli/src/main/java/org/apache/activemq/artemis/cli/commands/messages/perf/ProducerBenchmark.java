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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import org.HdrHistogram.SingleWriterRecorder;
import org.apache.activemq.artemis.api.core.ObjLongPair;

public final class ProducerBenchmark implements BenchmarkService {

   private final ConnectionFactory factory;
   private final MicrosTimeProvider timeProvider;
   private final EventLoopGroup eventLoopGroup;
   private final int producers;
   private final long messageCount;
   private final String group;
   private final long ttl;
   private final int messageSize;
   private final Destination[] destinations;
   private final boolean persistent;
   private final long maxPending;
   private final long transactionCapacity;
   private final Long messageRate;
   private final boolean sharedConnections;
   private final boolean enableTimestamp;
   private final boolean enableMessageID;
   private Set<Connection> connections;
   private ProducerLoadGenerator[] generators;
   private boolean started;
   private boolean closed;
   private final Map<Destination, List<AsyncJms2ProducerFacade>> producersPerDestination;
   private CompletableFuture<?> allGeneratorClosed;

   public ProducerBenchmark(final ConnectionFactory factory,
                            final MicrosTimeProvider timeProvider,
                            final EventLoopGroup loopGroup,
                            final int producers,
                            final long messageCount,
                            final boolean sharedConnections,
                            final String group,
                            final long ttl,
                            final int messageSize,
                            final Destination[] destinations,
                            final boolean persistent,
                            final long maxPending,
                            final long transactionCapacity,
                            final Long messageRate,
                            final boolean enableMessageID,
                            final boolean enableTimestamp) {
      this.factory = factory;
      this.timeProvider = timeProvider;
      this.eventLoopGroup = loopGroup;
      this.producers = producers;
      this.messageCount = messageCount;
      this.sharedConnections = sharedConnections;
      this.group = group;
      this.ttl = ttl;
      this.messageSize = messageSize;
      this.destinations = destinations;
      this.persistent = persistent;
      this.maxPending = maxPending;
      this.transactionCapacity = transactionCapacity;
      this.messageRate = messageRate;
      this.started = false;
      this.closed = false;
      this.connections = new HashSet<>();
      this.producersPerDestination = new HashMap<>(destinations.length);
      this.enableMessageID = enableMessageID;
      this.enableTimestamp = enableTimestamp;
   }

   private synchronized Stream<ObjLongPair<Destination>> messageSentPerDestination() {
      return producersPerDestination.entrySet().stream()
         .map(producers -> new ObjLongPair(producers.getKey(),
                                           producers.getValue().stream()
                                              .mapToLong(AsyncJms2ProducerFacade::getMessageSent).sum()));
   }

   public synchronized long expectedTotalMessageCountToReceive(final int sharedSubscriptions,
                                                               final int consumersPerDestination) {
      return expectedTotalMessageCountToReceive(messageSentPerDestination(), sharedSubscriptions, consumersPerDestination);
   }

   public static long expectedTotalMessageCountToReceive(final Stream<ObjLongPair<Destination>> messageSentPerDestination,
                                                         final int sharedSubscriptions,
                                                         final int consumersPerDestination) {
      return messageSentPerDestination.mapToLong(messagesPerDestination -> {
         if (messagesPerDestination.getA() instanceof Topic) {
            final int subscribers = sharedSubscriptions > 0 ? sharedSubscriptions : consumersPerDestination;
            return subscribers * messagesPerDestination.getB();
         }
         assert messagesPerDestination.getA() instanceof Queue;
         return messagesPerDestination.getB();
      }).sum();
   }

   public synchronized ProducerLoadGenerator[] getGenerators() {
      return generators;
   }

   @Override
   public synchronized boolean anyError() {
      if (!started || closed) {
         return false;
      }
      for (ProducerLoadGenerator loadGenerator : generators) {
         if (loadGenerator.getFatalException() != null) {
            return true;
         }
      }
      return false;
   }

   @Override
   public synchronized boolean isRunning() {
      if (!started || closed) {
         return false;
      }
      final ProducerLoadGenerator[] generators = this.generators;
      if (generators == null) {
         return false;
      }
      boolean running = false;
      for (ProducerLoadGenerator loadGenerator : generators) {
         if (!loadGenerator.isCompleted()) {
            running = true;
         } else if (loadGenerator.getFatalException() != null) {
            running = false;
            break;
         }
      }
      return running;
   }

   @Override
   public synchronized ProducerBenchmark start() {
      if (started) {
         return this;
      }
      producersPerDestination.clear();
      started = true;
      closed = false;
      // create connections: if shared, one for each event loop, if !shared, one for each producer
      final int totalProducers = destinations.length * producers;
      final IdentityHashMap<EventExecutor, Connection> sharedConnections = this.sharedConnections ? new IdentityHashMap<>() : null;
      final Connection[] exclusiveConnections = !this.sharedConnections ? new Connection[totalProducers] : null;
      if (this.sharedConnections) {
         eventLoopGroup.forEach(eventExecutor -> {
            final Connection connection;
            try {
               connection = factory.createConnection();
               connections.add(connection);
               sharedConnections.put(eventExecutor, connection);
            } catch (JMSException e) {
               throw new RuntimeException(e);
            }
         });
      } else {
         for (int i = 0; i < totalProducers; i++) {
            try {
               final Connection connection = factory.createConnection();
               exclusiveConnections[i] = connection;
               connections.add(connection);
            } catch (JMSException e) {
               throw new RuntimeException(e);
            }
         }
      }
      // start connections
      connections.forEach(connection -> {
         try {
            connection.start();
         } catch (JMSException e) {
            throw new RuntimeException(e);
         }
      });
      final AtomicLong producerId = new AtomicLong(1);
      // create shared content
      final byte[] messageContent = new byte[messageSize];
      Arrays.fill(messageContent, (byte) 1);
      // create producers/sessions/senders/load generators
      this.generators = new ProducerLoadGenerator[totalProducers];
      final ArrayList<AsyncJms2ProducerFacade> allProducers = new ArrayList<>(totalProducers);
      int producerSequence = 0;
      final int messageCountPerProducer = (int) (messageCount / totalProducers);
      long remainingMessageCount = messageCount;
      final Long messageRatePerProducer = messageRate == null ? null : (messageRate / totalProducers);
      Long remainingMessageRate = messageRate;
      for (int d = 0; d < destinations.length; d++) {
         final Destination destination = destinations[d];
         final ArrayList<AsyncJms2ProducerFacade> producers = new ArrayList<>(this.producers);
         producersPerDestination.put(destination, producers);
         for (int i = 0; i < this.producers; i++) {
            final EventLoop eventLoop = eventLoopGroup.next();
            final Connection connection;
            if (this.sharedConnections) {
               connection = sharedConnections.get(eventLoop);
            } else {
               connection = exclusiveConnections[producerSequence];
            }
            final Session session;
            final MessageProducer producer;
            try {
               session = connection.createSession(transactionCapacity > 0 ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
               producer = session.createProducer(destination);
               producer.setDisableMessageID(!enableMessageID);
               producer.setDisableMessageTimestamp(!enableTimestamp);
               producer.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
               producer.setTimeToLive(ttl);
            } catch (JMSException e) {
               throw new RuntimeException(e);
            }
            final AsyncJms2ProducerFacade producerFacade = new AsyncJms2ProducerFacade(producerId.getAndIncrement(),
                                                                                       session, producer, destination,
                                                                                       maxPending, transactionCapacity);
            allProducers.add(producerFacade);
            producers.add(producerFacade);
            final BooleanSupplier keepOnSendingStrategy;
            if (messageCount == 0) {
               keepOnSendingStrategy = () -> true;
            } else {
               final long count = Math.min(messageCountPerProducer, remainingMessageCount);
               remainingMessageCount -= count;
               keepOnSendingStrategy = () -> producerFacade.getMessageSent() < count;
            }
            final SingleWriterRecorder sendLatencyRecorder = new SingleWriterRecorder(2);

            final Long ratePeriodNanos;
            if (messageRatePerProducer != null) {
               final long rate = Math.min(messageRatePerProducer, remainingMessageRate);
               ratePeriodNanos = TimeUnit.SECONDS.toNanos(1) / rate;
               remainingMessageRate -= rate;
            } else {
               ratePeriodNanos = null;
            }

            generators[producerSequence] = ratePeriodNanos != null ?
               new ProducerTargetRateLoadGenerator(producerFacade, eventLoop, timeProvider, keepOnSendingStrategy, ratePeriodNanos, group, messageContent, sendLatencyRecorder, new SingleWriterRecorder(2)) :
               new ProducerMaxLoadGenerator(producerFacade, eventLoop, timeProvider, keepOnSendingStrategy, group, messageContent, sendLatencyRecorder);

            producerSequence++;
         }
      }

      // deploy and start generators
      for (int i = 0; i < totalProducers; i++) {
         generators[i].getExecutor().execute(generators[i]);
      }
      return this;
   }

   /**
    * After this, now new messages are sent, but there still be some to be completed: the return value can be used
    * to await completions to arrive.
    */
   public synchronized CompletionStage<?> asyncClose() {
      if (!started || closed) {
         return CompletableFuture.completedFuture(null);
      }
      if (allGeneratorClosed != null) {
         return allGeneratorClosed;
      }
      final CompletableFuture[] closedGenerators = new CompletableFuture[generators.length];
      for (int i = 0; i < generators.length; i++) {
         final CompletableFuture<?> onClosed = new CompletableFuture();
         closedGenerators[i] = onClosed;
         try {
            generators[i].asyncClose(() -> onClosed.complete(null)).get();
         } catch (final Throwable ignore) {
            closedGenerators[i].completeExceptionally(ignore);
         }
      }
      CompletableFuture<?> allGeneratorClosed = CompletableFuture.allOf(closedGenerators);
      final Connection[] openedConnections = connections.toArray(new Connection[connections.size()]);
      this.allGeneratorClosed = allGeneratorClosed.whenCompleteAsync((res, error) -> {
         synchronized (this) {
            generators = null;
            started = false;
            closed = true;
            this.allGeneratorClosed = null;
         }
         for (Connection connection : openedConnections) {
            // close connection: it should roll-back pending opened sessions and await completion to be called
            try {
               connection.close();
            } catch (JMSException ignore) {

            }
         }
      }, eventLoopGroup);
      connections.clear();
      return allGeneratorClosed;
   }

   @Override
   public void close() {
      asyncClose().toCompletableFuture().join();
   }

}
