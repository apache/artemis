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
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.HdrHistogram.SingleWriterRecorder;

public final class MessageListenerBenchmark implements BenchmarkService {

   private final ConnectionFactory factory;
   private final MicrosTimeProvider timeProvider;
   private final int consumers;
   private final boolean canDelaySetMessageCount;
   private final int connections;
   private final String clientID;
   private final Destination[] destinations;
   private final int sharedSubscription;
   private final boolean durableSubscription;
   private final long messageCount;
   private final boolean transaction;
   private final long commitInterval;
   private Set<Connection> jmsConnections;
   private boolean started;
   private boolean closed;
   private MessageCountLimiter msgCountLimiter;
   private List<RecordingMessageListener> listeners;
   private AtomicBoolean fatalException;
   private List<Runnable> silentUnsubscribe;

   public static final class MessageCountLimiter {

      private volatile long messageLimit = Long.MAX_VALUE;
      private final LongAdder totalMessagesReceived;

      MessageCountLimiter() {
         totalMessagesReceived = new LongAdder();
      }

      public MessageCountLimiter setMessageLimit(final long messageLimit) {
         this.messageLimit = messageLimit;
         return this;
      }

      public boolean isLimitReached() {
         return totalMessagesReceived.sum() >= messageLimit;
      }

      public void onMessageReceived() {
         totalMessagesReceived.increment();
      }
   }

   public MessageListenerBenchmark(final ConnectionFactory factory,
                                   final MicrosTimeProvider timeProvider,
                                   final int consumers,
                                   final long messageCount,
                                   final int connections,
                                   final String clientID,
                                   final Destination[] destinations,
                                   final boolean transaction,
                                   final long commitInterval,
                                   final int sharedSubscription,
                                   final boolean durableSubscription,
                                   final boolean canDelayMessageCount) {
      this.factory = factory;
      this.timeProvider = timeProvider;
      this.consumers = consumers;
      this.messageCount = messageCount;
      this.connections = connections;
      this.clientID = clientID;
      this.destinations = destinations;
      this.transaction = transaction;
      this.commitInterval = commitInterval;
      this.sharedSubscription = sharedSubscription;
      this.durableSubscription = durableSubscription;
      this.started = false;
      this.closed = false;
      this.jmsConnections = new HashSet<>(connections);
      this.canDelaySetMessageCount = canDelayMessageCount;
      this.listeners = null;
      this.fatalException = null;
      this.silentUnsubscribe = null;
   }

   public synchronized RecordingMessageListener[] getListeners() {
      return listeners == null ? null : listeners.toArray(new RecordingMessageListener[listeners.size()]);
   }

   @Override
   public synchronized boolean anyError() {
      if (fatalException == null) {
         return false;
      }
      return fatalException.get();
   }

   @Override
   public synchronized boolean isRunning() {
      if (!started || closed) {
         return false;
      }
      if (fatalException.get()) {
         return false;
      }
      if (msgCountLimiter == null) {
         return true;
      }
      return !msgCountLimiter.isLimitReached();
   }

   public synchronized void setMessageCount(final long messageCount) {
      if (!started || closed) {
         return;
      }
      msgCountLimiter.setMessageLimit(messageCount);
   }

   @Override
   public synchronized MessageListenerBenchmark start() {
      if (started) {
         return this;
      }
      started = true;
      closed = false;
      final AtomicLong consumerId = new AtomicLong(1);
      // setup connection failure listeners
      final AtomicBoolean signalBrokenConnection = new AtomicBoolean(false);
      fatalException = signalBrokenConnection;
      // create connections upfront and register failure listener
      final Connection[] jmsConnections = new Connection[connections];
      for (int i = 0; i < connections; i++) {
         final Connection connection;
         try {
            connection = factory.createConnection();
            if (clientID != null) {
               if (connections > 1) {
                  connection.setClientID(clientID + i);
               } else {
                  connection.setClientID(clientID);
               }
            }
            connection.setExceptionListener(ignore -> {
               signalBrokenConnection.set(true);
            });
            jmsConnections[i] = connection;
         } catch (JMSException e) {
            throw new RuntimeException(e);
         }
      }
      this.jmsConnections.addAll(Arrays.asList(jmsConnections));
      // start connections
      this.jmsConnections.forEach(connection -> {
         try {
            connection.start();
         } catch (JMSException e) {
            throw new RuntimeException(e);
         }
      });
      int connectionSequence = 0;
      final int totalListeners = consumers * destinations.length * Math.max(sharedSubscription, 1);
      this.listeners = new ArrayList<>(totalListeners);
      if (messageCount > 0) {
         msgCountLimiter = new MessageCountLimiter().setMessageLimit(messageCount);
      } else if (canDelaySetMessageCount) {
         msgCountLimiter = new MessageCountLimiter();
      }
      // create consumers per destination
      if (durableSubscription) {
         silentUnsubscribe = new ArrayList<>();
      }
      for (int i = 0; i < destinations.length; i++) {
         final Destination destination = destinations[i];
         if (sharedSubscription == 0) {
            final Queue<RecordingMessageListener> destinationListeners = new ArrayDeque<>(consumers);
            createListeners(destinationListeners, consumerId, destination, consumers);
            listeners.addAll(destinationListeners);
            try {
               for (int consumerIndex = 0; consumerIndex < consumers; consumerIndex++) {
                  final Connection connection = jmsConnections[connectionSequence % connections];
                  connectionSequence++;
                  final Session session = connection.createSession(transaction ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
                  final MessageConsumer consumer;
                  if (durableSubscription) {
                     final Topic topic = (Topic) destination;
                     consumer = session.createDurableConsumer((Topic) destination, topic.getTopicName() + consumerIndex);
                  } else {
                     consumer = session.createConsumer(destination);
                  }
                  consumer.setMessageListener(listenerOf(session, destinationListeners.remove()));
               }
            } catch (JMSException e) {
               throw new RuntimeException(e);
            }
         } else {
            final int listenersPerDestination = sharedSubscription * consumers;
            final Queue<RecordingMessageListener> destinationListeners = new ArrayDeque<>(listenersPerDestination);
            createListeners(destinationListeners, consumerId, destination, listenersPerDestination);
            listeners.addAll(destinationListeners);
            try {
               final String topicName = ((Topic) destination).getTopicName();
               for (int subscriptionIndex = 0; subscriptionIndex < sharedSubscription; subscriptionIndex++) {
                  Connection connection = null;
                  if (clientID != null) {
                     connection = jmsConnections[connectionSequence % connections];
                     assert connection.getClientID() != null;
                     connectionSequence++;
                  }
                  for (int consumerIndex = 0; consumerIndex < consumers; consumerIndex++) {
                     if (clientID == null) {
                        assert connection == null;
                        connection = jmsConnections[connectionSequence % connections];
                        connectionSequence++;
                     }
                     final Session session = connection.createSession(transaction ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
                     final MessageConsumer consumer;
                     if (durableSubscription) {
                        final String subscriptionName = topicName + subscriptionIndex;
                        consumer = session.createSharedDurableConsumer((Topic) destination, subscriptionName);
                        silentUnsubscribe.add(() -> {
                           try {
                              session.unsubscribe(subscriptionName);
                           } catch (JMSException e) {
                              throw new RuntimeException(e);
                           }
                        });
                     } else {
                        consumer = session.createSharedConsumer((Topic) destination, topicName + subscriptionIndex);
                     }
                     consumer.setMessageListener(listenerOf(session, destinationListeners.remove()));
                  }
               }
            } catch (JMSException fatal) {
               throw new RuntimeException(fatal);
            }
         }
      }
      return this;
   }

   private RecordingMessageListener listenerOf(final Session session, final RecordingMessageListener listener) {
      if (transaction) {
         listener.setTransactedSession(session);
      }
      return listener;
   }

   private void createListeners(final Collection<? super RecordingMessageListener> listeners,
                                final AtomicLong consumerId,
                                final Destination destination,
                                final int count) {
      for (int c = 0; c < count; c++) {
         listeners.add(new RecordingMessageListener(consumerId.getAndIncrement(), destination, commitInterval,
                                                    new AtomicLong(0),
                                                    msgCountLimiter == null ? null : msgCountLimiter::onMessageReceived,
                                                    timeProvider, new SingleWriterRecorder(2),
                                                    fatalException));
      }
   }

   @Override
   public synchronized void close() {
      if (!started || closed) {
         return;
      }
      if (transaction) {
         // stopping a connection waits for its listeners, leaving their sessions free to commit from here
         jmsConnections.forEach(connection -> {
            try {
               connection.stop();
            } catch (JMSException ignore) {

            }
         });
         listeners.forEach(RecordingMessageListener::commitReceived);
      }
      listeners = null;
      started = false;
      closed = true;
      msgCountLimiter = null;
      fatalException = null;
      if (silentUnsubscribe != null) {
         silentUnsubscribe.forEach(Runnable::run);
         silentUnsubscribe = null;
      }
      jmsConnections.forEach(connection -> {
         try {
            connection.close();
         } catch (JMSException ignore) {

         }
      });
      jmsConnections.clear();
   }
}
