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
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageListenerBenchmarkTest {

   private static final long NOW = 1_000_000;

   private static RecordingMessageListener startListener(final MessageListenerBenchmark benchmark) {
      benchmark.start();
      return benchmark.getListeners()[0];
   }

   private static MessageListenerBenchmark benchmark() throws Exception {
      final ConnectionFactory factory = Mockito.mock(ConnectionFactory.class);
      final Connection connection = Mockito.mock(Connection.class);
      final Session session = Mockito.mock(Session.class);
      Mockito.when(factory.createConnection()).thenReturn(connection);
      Mockito.when(connection.createSession(Mockito.anyInt())).thenReturn(session);
      Mockito.when(session.createConsumer(Mockito.any())).thenReturn(Mockito.mock(MessageConsumer.class));
      return new MessageListenerBenchmarkBuilder()
         .setFactory(factory)
         .setTimeProvider(() -> NOW)
         .setConsumers(1)
         .setConnections(1)
         .setDestinations(new Destination[]{Mockito.mock(Queue.class)})
         .createMessageListenerBenchmark();
   }

   private static Message messageSentAt(final long time) throws Exception {
      final Message message = Mockito.mock(Message.class);
      Mockito.when(message.getLongProperty("time")).thenReturn(time);
      return message;
   }

   /**
    * A message stamped ahead of the receiver's clock used to throw out of onMessage, and so be redelivered for ever.
    */
   @Test
   public void testMessageSentAheadOfTheClockIsRecordedAsNoLatency() throws Exception {
      try (MessageListenerBenchmark benchmark = benchmark()) {
         final RecordingMessageListener listener = startListener(benchmark);

         listener.onMessage(messageSentAt(NOW + 500));

         final Histogram latencies = listener.getReceiveLatencyRecorder().getIntervalHistogram();
         assertEquals(1, latencies.getTotalCount());
         assertEquals(0, latencies.getMaxValue());
         assertEquals(1, listener.getReceivedMessages());
      }
   }

   @Test
   public void testMessageSentEarlierIsRecordedWithItsLatency() throws Exception {
      try (MessageListenerBenchmark benchmark = benchmark()) {
         final RecordingMessageListener listener = startListener(benchmark);

         listener.onMessage(messageSentAt(NOW - 500));

         final Histogram latencies = listener.getReceiveLatencyRecorder().getIntervalHistogram();
         assertEquals(1, latencies.getTotalCount());
         assertEquals(500, latencies.getValueAtPercentile(100), 5);
      }
   }
}
