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
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.util.Objects.requireNonNull;

public final class AsyncJms2ProducerFacade {

   private final long id;
   protected final Session session;
   private final MessageProducer producer;

   /*
    * maxPending limits the number of in-flight sent messages
    * in a way that if the limit is reached and a single completion arrive,
    * a subsequent send attempt will succeed.
    */
   private long pending;
   private final long maxPending;

   /*
    * Tracking sent messages in transaction requires using 2 separate counters
    * ie pendingMsgInTransaction, completedMsgInTransaction
    * because, using just one won't allow tracking completions of previously sent messages in order to commit
    * the transaction while there are no more in-flight ones.
    */
   private final long transactionCapacity;
   private long pendingMsgInTransaction;
   private long completedMsgInTransaction;

   private final List<Runnable> availableObservers;
   private final List<Runnable> closedObservers;

   private static final AtomicLongFieldUpdater<AsyncJms2ProducerFacade> MESSAGE_SENT_UPDATER = AtomicLongFieldUpdater.newUpdater(AsyncJms2ProducerFacade.class, "messageSent");
   private static final AtomicLongFieldUpdater<AsyncJms2ProducerFacade> MESSAGE_COMPLETED_UPDATER = AtomicLongFieldUpdater.newUpdater(AsyncJms2ProducerFacade.class, "messageCompleted");
   private static final AtomicLongFieldUpdater<AsyncJms2ProducerFacade> NOT_AVAILABLE_UPDATER = AtomicLongFieldUpdater.newUpdater(AsyncJms2ProducerFacade.class, "notAvailable");

   private volatile long messageSent;
   private volatile long messageCompleted;
   private volatile long notAvailable;

   private boolean closing;
   private boolean closed;
   private final Destination destination;

   public AsyncJms2ProducerFacade(final long id,
                                  final Session session,
                                  final MessageProducer producer,
                                  final Destination destination,
                                  final long maxPending,
                                  final long transactionCapacity) {
      this.id = id;
      this.session = requireNonNull(session);
      this.producer = requireNonNull(producer);
      this.destination = destination;
      this.pending = 0;
      this.maxPending = transactionCapacity > 0 && maxPending > 0 ? Math.max(maxPending, transactionCapacity) : maxPending;
      this.availableObservers = new ArrayList<>(1);
      this.closedObservers = new ArrayList<>(1);
      this.messageSent = 0;
      this.messageCompleted = 0;
      this.notAvailable = 0;
      try {
         if (transactionCapacity < 0) {
            throw new IllegalStateException("transactionCapacity must be >= 0");
         }
         if (transactionCapacity > 0) {
            if (!session.getTransacted()) {
               throw new IllegalStateException("session must be transacted with transactionCapacity != 0");
            }
         } else {
            if (session.getTransacted()) {
               throw new IllegalStateException("session cannot be transacted with transactionCapacity = 0");
            }
         }
      } catch (final JMSException ex) {
         throw new IllegalStateException(ex);
      }
      this.transactionCapacity = transactionCapacity;
      this.pendingMsgInTransaction = 0;
      this.completedMsgInTransaction = 0;
      this.closing = false;
      this.closed = false;
   }

   public long getId() {
      return id;
   }

   public Destination getDestination() {
      return destination;
   }

   BytesMessage createBytesMessage() throws JMSException {
      return session.createBytesMessage();
   }

   private void addedPendingSend() {
      if (transactionCapacity > 0 && pendingMsgInTransaction == transactionCapacity) {
         throw new IllegalStateException("reached max in-flight transacted sent messages");
      }
      if (maxPending > 0 && pending == maxPending) {
         throw new IllegalStateException("reached max in-flight sent messages");
      }
      pending++;
      pendingMsgInTransaction++;
   }

   /**
    * If {@code true}, a subsequent {@link #trySend} would return {@link SendAttemptResult#Success}. Otherwise, a
    * subsequent {@link #trySend} would return {@link SendAttemptResult#NotAvailable}.
    */
   private boolean isAvailable() {
      if (maxPending > 0 && pending == maxPending) {
         return false;
      }
      return transactionCapacity == 0 || pendingMsgInTransaction != transactionCapacity;
   }

   public enum SendAttemptResult {
      Closing, Closed, NotAvailable, Success
   }

   public SendAttemptResult trySend(final Message message,
                                    final CompletionListener completionListener,
                                    final Runnable availableObserver) throws JMSException {
      if (closing) {
         return SendAttemptResult.Closing;
      }
      if (closed) {
         return SendAttemptResult.Closed;
      }
      if (!isAvailable()) {
         availableObservers.add(availableObserver);
         orderedIncrementNotAvailable();
         return SendAttemptResult.NotAvailable;
      }
      producer.send(message, completionListener);
      orderedIncrementSent();
      addedPendingSend();
      return SendAttemptResult.Success;
   }

   public void onSendErrored() {
      if (closed) {
         return;
      }
      availableObservers.clear();
      runObservers(closedObservers);
      closed = true;
   }

   public JMSException onSendCompleted() {
      if (closed) {
         return null;
      }
      JMSException completionError = null;
      orderedIncrementCompleted();
      if (transactionCapacity > 0 && completedMsgInTransaction == transactionCapacity) {
         throw new IllegalStateException("cannot complete more send");
      }
      if (pending == 0) {
         throw new IllegalStateException("cannot complete more send");
      }
      pending--;
      completedMsgInTransaction++;
      if (transactionCapacity > 0) {
         if (completedMsgInTransaction == transactionCapacity || (closing && pending == 0)) {
            completedMsgInTransaction = 0;
            pendingMsgInTransaction = 0;
            try {
               session.commit();
            } catch (final JMSException fatal) {
               completionError = fatal;
               closing = true;
            }
            if (closing) {
               closing = false;
               closed = true;
               runObservers(closedObservers);
            } else if (isAvailable()) {
               runObservers(availableObservers);
            }
         }
      } else {
         if (closing && pending == 0) {
            closing = false;
            closed = true;
            runObservers(closedObservers);
         } else if (isAvailable()) {
            runObservers(availableObservers);
         }
      }
      return completionError;
   }

   /**
    * Runs and removes the given observers. They are taken off the list first, so an observer that registers again while
    * running (e.g. a send attempt that finds the producer not available) is kept for the next round.
    */
   private static void runObservers(final List<Runnable> observers) {
      if (observers.isEmpty()) {
         return;
      }
      final List<Runnable> toRun = new ArrayList<>(observers);
      observers.clear();
      toRun.forEach(Runnable::run);
   }

   public long getMessageSent() {
      return messageSent;
   }

   private void orderedIncrementSent() {
      MESSAGE_SENT_UPDATER.lazySet(this, messageSent + 1);
   }

   public long getMessageCompleted() {
      return messageCompleted;
   }

   private void orderedIncrementCompleted() {
      MESSAGE_COMPLETED_UPDATER.lazySet(this, messageCompleted + 1);
   }

   public long getNotAvailable() {
      return notAvailable;
   }

   private void orderedIncrementNotAvailable() {
      NOT_AVAILABLE_UPDATER.lazySet(this, notAvailable + 1);
   }

   public void requestClose() {
      requestClose(() -> {
      });
   }

   public void requestClose(final Runnable onClosed) {
      if (closed) {
         onClosed.run();
         return;
      }
      if (closing) {
         closedObservers.add(onClosed);
         return;
      }
      availableObservers.clear();
      if (pending > 0) {
         closing = true;
         closedObservers.add(onClosed);
      } else {
         closed = true;
         onClosed.run();
      }
   }
}
