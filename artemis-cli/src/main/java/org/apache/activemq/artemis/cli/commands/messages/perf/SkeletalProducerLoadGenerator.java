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

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.JMSException;
import javax.jms.Message;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import io.netty.util.concurrent.OrderedEventExecutor;
import org.HdrHistogram.SingleWriterRecorder;
import org.apache.activemq.artemis.cli.commands.messages.perf.AsyncJms2ProducerFacade.SendAttemptResult;

public abstract class SkeletalProducerLoadGenerator implements CompletionListener, ProducerLoadGenerator {

   protected final AsyncJms2ProducerFacade producer;
   private final OrderedEventExecutor executor;
   protected final BooleanSupplier keepOnSending;
   protected final MicrosTimeProvider timeProvider;
   private final String group;
   private final byte[] messageContent;
   private BytesMessage messageToSend;
   protected boolean closed;
   protected volatile boolean stopLoad;
   private final SingleWriterRecorder waitLatencies;
   private final SingleWriterRecorder sendCompletedLatencies;
   private final AtomicLong unprocessedCompletions;
   private final AtomicBoolean scheduledProcessingCompletions;
   private volatile Exception fatalException;
   private boolean stopHandlingCompletions;

   public SkeletalProducerLoadGenerator(final AsyncJms2ProducerFacade producer,
                                        final OrderedEventExecutor executor,
                                        final MicrosTimeProvider timeProvider,
                                        final BooleanSupplier keepOnSending,
                                        final String group,
                                        final byte[] msgContent,
                                        final SingleWriterRecorder sendCompletedLatencies,
                                        final SingleWriterRecorder waitLatencies) {
      this.sendCompletedLatencies = sendCompletedLatencies;
      this.waitLatencies = waitLatencies;
      this.producer = producer;
      this.executor = executor;
      this.timeProvider = timeProvider;
      this.keepOnSending = keepOnSending;
      this.group = group;
      this.messageContent = msgContent;
      this.messageToSend = null;
      this.closed = false;
      this.stopLoad = false;
      this.unprocessedCompletions = new AtomicLong();
      this.scheduledProcessingCompletions = new AtomicBoolean();
      this.fatalException = null;
      this.stopHandlingCompletions = false;
   }

   @Override
   public Exception getFatalException() {
      return fatalException;
   }

   @Override
   public SingleWriterRecorder getSendCompletedLatencies() {
      return sendCompletedLatencies;
   }

   @Override
   public SingleWriterRecorder getWaitLatencies() {
      return waitLatencies;
   }

   @Override
   public AsyncJms2ProducerFacade getProducer() {
      return producer;
   }

   @Override
   public boolean isCompleted() {
      if (stopLoad && fatalException != null) {
         return true;
      }
      return stopLoad && producer.getMessageCompleted() == producer.getMessageSent();
   }

   @Override
   public OrderedEventExecutor getExecutor() {
      return executor;
   }

   protected final void asyncContinue() {
      asyncContinue(0);
   }

   protected final void asyncContinue(final long usDelay) {
      if (usDelay == 0) {
         executor.execute(this);
      } else {
         executor.schedule(this, usDelay, TimeUnit.MICROSECONDS);
      }
   }

   protected final boolean trySend(final long sendTime) {
      return trySend(sendTime, sendTime);
   }

   protected final boolean trySend(final long expectedSendTime, final long sendTime) {
      assert executor.inEventLoop();
      assert !closed;
      try {
         if (messageToSend == null) {
            messageToSend = producer.createBytesMessage();
            messageToSend.writeBytes(this.messageContent);
         }
         messageToSend.setLongProperty("time", sendTime);
         if (group != null) {
            messageToSend.setStringProperty("JMSXGroupID", group);
         }
         final SendAttemptResult result = producer.trySend(messageToSend, this, this);
         if (result != SendAttemptResult.NotAvailable) {
            messageToSend = null;
            if (result == SendAttemptResult.Success) {
               if (waitLatencies != null) {
                  waitLatencies.recordValue(sendTime - expectedSendTime);
               }
            }
         }
         return result == SendAttemptResult.Success;
      } catch (final JMSException e) {
         onSendErrored(e);
         return false;
      }
   }

   @Override
   public void onCompletion(final Message message) {
      asyncOnSendCompleted(message, null);
   }

   @Override
   public void onException(final Message message, final Exception exception) {
      asyncOnSendCompleted(message, exception);
   }

   private void asyncOnSendCompleted(final Message message, Exception completionError) {
      if (stopHandlingCompletions) {
         return;
      }
      if (completionError == null) {
         try {
            recordSendCompletionLatency(message);
            unprocessedCompletions.incrementAndGet();
            scheduleProcessingCompletions();
         } catch (final JMSException jmsException) {
            completionError = jmsException;
         }
      }
      if (completionError != null) {
         stopHandlingCompletions = true;
         final Exception fatal = completionError;
         executor.execute(() -> onSendErrored(fatal));
      }
   }

   private void onSendErrored(final Exception fatal) {
      assert executor.inEventLoop();
      if (fatalException != null) {
         return;
      }
      producer.onSendErrored();
      fatalException = fatal;
      stopLoad = true;
      closed = true;
   }

   private void scheduleProcessingCompletions() {
      if (unprocessedCompletions.get() > 0 && scheduledProcessingCompletions.compareAndSet(false, true)) {
         executor.execute(this::processCompletions);
      }
   }

   private void processCompletions() {
      assert executor.inEventLoop();
      assert scheduledProcessingCompletions.get();
      if (fatalException != null) {
         return;
      }
      final long completions = unprocessedCompletions.getAndSet(0);
      for (long i = 0; i < completions; i++) {
         final JMSException completionException = producer.onSendCompleted();
         if (completionException != null) {
            fatalException = completionException;
            return;
         }
      }
      scheduledProcessingCompletions.set(false);
      scheduleProcessingCompletions();
   }

   private void recordSendCompletionLatency(final Message message) throws JMSException {
      final long time = message.getLongProperty("time");
      // a wall clock can step back
      final long elapsedMicros = Math.max(0, timeProvider.now() - time);
      sendCompletedLatencies.recordValue(elapsedMicros);
   }

   @Override
   public Future<?> asyncClose(final Runnable onClosed) {
      return executor.submit(() -> onClose(onClosed));
   }

   private void onClose(final Runnable onClosed) {
      assert executor.inEventLoop();
      if (closed) {
         onClosed.run();
         return;
      }
      closed = true;
      // no need for this anymore
      messageToSend = null;
      requestClose(onClosed);
   }

   protected final void requestClose(final Runnable onClosed) {
      final JMSException closeError = producer.requestClose(onClosed);
      if (closeError != null) {
         fatalException = closeError;
      }
   }
}
