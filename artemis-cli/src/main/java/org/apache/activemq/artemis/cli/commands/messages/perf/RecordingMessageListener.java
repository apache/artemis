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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.HdrHistogram.SingleWriterRecorder;

public final class RecordingMessageListener implements MessageListener {

   private final long id;
   private final Destination destination;
   private final long commitInterval;
   private final AtomicLong receivedMessages;
   private final Runnable onMessageReceived;
   private final MicrosTimeProvider timeProvider;
   private final SingleWriterRecorder receiveLatencyRecorder;
   private AtomicBoolean fatalException;
   private Session session;
   private long uncommitted;

   RecordingMessageListener(final long id,
                            final Destination destination,
                            final long commitInterval,
                            final AtomicLong receivedMessages,
                            final Runnable onMessageReceived,
                            final MicrosTimeProvider timeProvider,
                            final SingleWriterRecorder receiveLatencyRecorder,
                            final AtomicBoolean fatalException) {
      this.id = id;
      this.destination = destination;
      this.commitInterval = commitInterval;
      this.receivedMessages = receivedMessages;
      this.onMessageReceived = onMessageReceived;
      this.timeProvider = timeProvider;
      this.receiveLatencyRecorder = receiveLatencyRecorder;
      this.fatalException = fatalException;
   }

   /**
    * @param session the transacted session to commit every {@code commitInterval} messages, or every message if that
    *                is not positive
    */
   void setTransactedSession(final Session session) {
      this.session = session;
   }

   public boolean anyFatalException() {
      return fatalException.get();
   }

   public SingleWriterRecorder getReceiveLatencyRecorder() {
      return receiveLatencyRecorder;
   }

   public long getId() {
      return id;
   }

   public Destination getDestination() {
      return destination;
   }

   public long getReceivedMessages() {
      return receivedMessages.get();
   }

   @Override
   public void onMessage(final Message message) {
      if (onMessageReceived != null) {
         onMessageReceived.run();
      }
      receivedMessages.lazySet(receivedMessages.get() + 1);
      if (receiveLatencyRecorder != null) {
         try {
            final long start = message.getLongProperty("time");
            final long receivedOn = timeProvider.now();
            // the clocks of two hosts can differ, and a wall clock can step back
            final long elapsedUs = Math.max(0, receivedOn - start);
            receiveLatencyRecorder.recordValue(elapsedUs);
         } catch (JMSException fatal) {
            fatalException.compareAndSet(false, true);
         }
      }
      if (session != null) {
         uncommitted++;
         if (uncommitted >= commitInterval) {
            commit();
         }
      }
   }

   /**
    * Commits what has been received since the last commit. A session has one thread of control: call this only when
    * no message can be delivered to this listener, that is, with the connection stopped.
    */
   void commitReceived() {
      if (session != null && uncommitted > 0) {
         commit();
      }
   }

   private void commit() {
      uncommitted = 0;
      try {
         session.commit();
      } catch (JMSException fatal) {
         fatalException.compareAndSet(false, true);
      }
   }
}