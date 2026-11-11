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
package org.apache.activemq.artemis.protocol.amqp.proton;

import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Outcome;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transaction.TransactionalState;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProtonAbstractReceiver extends ProtonInitializable implements ProtonDeliveryHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected enum ReceiverState {
      STARTED,
      DRAINING,
      STOPPING,
      STOPPED,
      CLOSED
   }

   protected final AMQPConnectionContext connection;
   protected final AMQPSessionContext protonSession;
   protected final Receiver receiver;
   protected final int minLargeMessageSize;
   protected final RoutingContext routingContext;
   protected final AMQPSessionCallback sessionSPI;
   // Cached instances used for this receiver which will be swapped as message of varying types
   // are sent to this receiver from the remote peer.
   protected final MessageReader standardMessageReader = new AMQPMessageReader(this);
   protected final MessageReader largeMessageReader = new AMQPLargeMessageReader(this);
   protected final Runnable creditRunnable;
   protected final boolean useModified;
   protected final boolean drainCreditOnNoSpace;
   protected final long drainTimeout;
   protected final Runnable creditTopUpRunner = this::doCreditTopUpRun;

   protected volatile MessageReader messageReader;
   protected int pendingSettles = 0;
   protected volatile ReceiverState state = ReceiverState.STARTED;
   protected BiConsumer<ProtonAbstractReceiver, Boolean> pendingStop;
   protected ScheduledFuture<?> pendingQuiesceTimeout;

   // Not always used so left unallocated until needed, on attach the capabilities drive if supported
   protected MessageReader coreMessageReader;
   protected MessageReader coreLargeMessageReader;
   protected boolean coreTunnelingEnabled;

   public ProtonAbstractReceiver(AMQPSessionCallback sessionSPI,
                                 AMQPConnectionContext connection,
                                 AMQPSessionContext protonSession,
                                 Receiver receiver) {
      this.sessionSPI = sessionSPI;
      this.connection = connection;
      this.protonSession = protonSession;
      this.receiver = receiver;
      this.minLargeMessageSize = getConfiguredMinLargeMessageSize(connection);
      this.creditRunnable = createCreditRunnable(connection);
      this.useModified = isUseModifiedForTransientDeliveryErrors(connection);
      this.drainCreditOnNoSpace = isDrainOnTransientDeliveryErrors(connection);
      this.drainTimeout = getLinkQuiesceTimeout(connection);
      this.routingContext = new RoutingContextImpl(null).setDuplicateDetection(connection.getProtocolManager().isAmqpDuplicateDetection());
   }

   public AMQPSessionContext getSessionContext() {
      return protonSession;
   }

   /**
    * Starts the receiver if not already started which triggers a flow of credit to the remote to begin the processing
    * of incoming messages.  This must be called on the connection thread and will throw and exception if not.
    *
    * @throws IllegalStateException if not called from the connection thread or is closed or stopping.
    */
   public void start() {
      connection.requireInHandler();

      if (state == ReceiverState.CLOSED) {
         throw new IllegalStateException("Cannot start a closed receiver");
      }

      if (state == ReceiverState.STOPPING) {
         throw new IllegalStateException("Cannot start a receiver that is not yet stopped");
      }

      if (state == ReceiverState.STOPPED) {
         state = ReceiverState.STARTED;
         topUpCreditIfNeeded();
      }
   }

   /**
    * Stop the receiver from granting additional credit and drains any granted credit from the link already. If any
    * pending settles or queued message remain in the work queue then the stop occurs asynchronously and the stop
    * callback is signaled later otherwise it will be triggered on the current thread to avoid state changes from making
    * an asynchronous call invalid. The stop call allows a timeout to be specified which will signal the stopped
    * consumer if the timeout elapses and leaves the receiver in the stopping state which does not allow for a restart.
    *
    * @param stopTimeout
    *    A time in milliseconds to wait for the stop to complete before considering it as having failed.
    * @param onStopped
    *    A consumer that is signaled once the receiver has stopped or the timeout elapsed.
    *
    * @throws IllegalStateException if the receiver is currently in the stopping state.
    */
   public void stop(int stopTimeout, BiConsumer<ProtonAbstractReceiver, Boolean> onStopped) {
      Objects.requireNonNull(onStopped, "The stopped callback must not be null");
      connection.requireInHandler();

      if (state.ordinal() < ReceiverState.STOPPING.ordinal()) {
         state = ReceiverState.STOPPING;
         pendingStop = onStopped;

         if (!tryCompletePendingQuiesce()) {
            // The receiver could already be draining but if not we trigger a drain to handle
            // the run off of pending messages from the remote before reporting stopped.
            if (receiver.getCredit() != 0 && !receiver.draining()) {
               receiver.drain(0);
            }

            if (stopTimeout > 0) {
               // Take over any pending timeout setup from a drain operation with one with the given
               // stop timeout otherwise the pending drain timeout will kick in and will signal the
               // stop callback indicating that stop failed.
               if (pendingQuiesceTimeout != null) {
                  pendingQuiesceTimeout.cancel(false);
                  pendingQuiesceTimeout = null;
               }

               pendingQuiesceTimeout = protonSession.getServer().getScheduledPool().schedule(() -> {
                  connection.runNow(() -> signalStoppedCallback(false));
               }, stopTimeout, TimeUnit.MILLISECONDS);
            }
         }
      } else if (isStopped() || isClosed()) {
         pendingStop = onStopped;
         signalStoppedCallback(true);
      } else {
         throw new IllegalStateException("Receiver is currently in the process of stopping");
      }
   }

   @Override
   public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
      state = ReceiverState.CLOSED;
      protonSession.removeReceiver(receiver);
      closeCurrentReader();
      connection.runNow(() -> {
         signalStoppedCallback(true);
      });
   }

   @Override
   public void close(ErrorCondition condition) throws ActiveMQAMQPException {
      receiver.setCondition(condition);
      close(false);
   }

   public AMQPConnectionContext getConnection() {
      return connection;
   }

   public boolean isStarted() {
      return state == ReceiverState.STARTED;
   }

   public boolean isDraining() {
      return state == ReceiverState.DRAINING;
   }

   public boolean isStopping() {
      return state == ReceiverState.STOPPING;
   }

   public boolean isStopped() {
      return state == ReceiverState.STOPPED;
   }

   public boolean isClosed() {
      return state == ReceiverState.CLOSED;
   }

   public boolean isBusy() {
      return false;
   }

   /**
    * Set the proper operation context in the Thread Local.
    *  Return the old context*/
   protected OperationContext recoverContext() {
      return sessionSPI.recoverContext();
   }

   protected void closeCurrentReader() {
      connection.runNow(() -> {
         if (messageReader != null) {
            messageReader.close();
            messageReader = null;
         }
      });
   }

   /**
    * Should the receiver send an AMQP Modified disposition with delivery failed set to true for
    * address full errors instead of the Rejected disposition it would by default.
    *
    * @param connection
    *    The connection context that this receiver instance is bound to
    *
    * @return <code>true</code> if the receiver send a modified outcome and false for rejected outcomes.
    */
   protected boolean isUseModifiedForTransientDeliveryErrors(AMQPConnectionContext connection) {
      return connection.getProtocolManager().isUseModifiedForTransientDeliveryErrors();
   }

   /**
    * Should the receiver drain link credit when a transient delivery error occurs, this allows subclass
    * implementations to override the defaults set on the connection level.
    *
    * @param connection
    *    The connection context that this receiver instance is bound to
    *
    * @return true if transient delivery errors should be handled by draining link credit from the remote sender.
    */
   protected boolean isDrainOnTransientDeliveryErrors(AMQPConnectionContext connection) {
      return connection.getProtocolManager().isDrainOnTransientDeliveryErrors();
   }

   /**
    * Gets the time in milliseconds that the receiver should wait before it considers a link quiesce attempt to
    * have failed and act to close the link with an error, this allows subclass implementations to override the
    * defaults set on the connection level.
    *
    * @param connection
    *    The connection context that this receiver instance is bound to
    *
    * @return the time in milliseconds to wait for remote sender once a link quiesce is initiated by this peer.
    */
   protected int getLinkQuiesceTimeout(AMQPConnectionContext connection) {
      return connection.getProtocolManager().getLinkQuiesceTimeout();
   }

   /**
    * Subclass can override this to provide a custom credit runnable that performs other checks or applies credit in a
    * manner more fitting that implementation.
    *
    * @param connection The {@link AMQPConnectionContext} that this resource falls under.
    * @return a {@link Runnable} that will perform the actual credit granting operation
    */
   protected Runnable createCreditRunnable(AMQPConnectionContext connection) {
      return createCreditRunnable(connection.getAmqpCredits(), connection.getAmqpLowCredits(), receiver, connection, this);
   }

   /**
    * Subclass can override this to provide the minimum large message size that should be used when creating receiver
    * instances.
    *
    * @param connection The {@link AMQPConnectionContext} that this resource falls under.
    * @return the minimum large message size configuration value for this receiver
    */
   protected int getConfiguredMinLargeMessageSize(AMQPConnectionContext connection) {
      return connection.getProtocolManager().getAmqpMinLargeMessageSize();
   }

   /**
    * This Credit Runnable can be used to manage the credit replenishment of a target AMQP receiver.
    *
    * @param refill     The number of credit to top off the receiver to
    * @param threshold  The low water mark for credit before refill is done
    * @param receiver   The proton receiver that will have its credit refilled
    * @param connection The connection that own the receiver
    * @param context    The context that will be associated with the receiver
    *
    * @return A new Runnable that can be used to keep receiver credit replenished
    */
   public static Runnable createCreditRunnable(int refill,
                                               int threshold,
                                               Receiver receiver,
                                               AMQPConnectionContext connection,
                                               ProtonAbstractReceiver context) {
      return new FlowControlRunner(refill, threshold, receiver, connection, context);
   }

   /**
    * This servers as the default credit runnable which grants credit in batches based on a low water mark and a
    * configured credit size to top the credit up to once the low water mark has been reached.
    */
   protected static class FlowControlRunner implements Runnable {

      // The number of credits sent to the remote when the runnable decides that a top off is needed.
      final int refill;

      // The low water mark before the runnable considers performing a credit top off.
      final int threshold;

      final Receiver receiver;
      final AMQPConnectionContext connection;
      final ProtonAbstractReceiver context;

      FlowControlRunner(int refill, int threshold, Receiver receiver, AMQPConnectionContext connection, ProtonAbstractReceiver context) {
         Objects.requireNonNull(receiver, "Given proton receiver cannot be null");
         Objects.requireNonNull(connection, "Given connection context cannot be null");
         Objects.requireNonNull(context, "Given receiver context cannot be null");

         this.refill = refill;
         this.threshold = threshold;
         this.receiver = receiver;
         this.connection = connection;
         this.context = context;
      }

      @Override
      public void run() {
         if (connection.isHandler()) {
            connection.requireInHandler();

            if (context.isStarted() && !context.isBusy()) {
               final int pending = context.pendingSettles;

               if (isBellowThreshold(receiver.getCredit(), pending, threshold)) {
                  int topUp = calculatedUpdateRefill(refill, receiver.getCredit(), pending);
                  if (topUp > 0) {
                     receiver.flow(topUp);
                     connection.instantFlush();
                  }
               }
            }
         } else {
            // This must run on the connection thread as it interacts with proton
            connection.runLater(this);
         }
      }
   }

   public void incrementSettle() {
      assert pendingSettles >= 0;
      connection.requireInHandler();
      pendingSettles++;
   }

   public void settle(Delivery settlement) {
      connection.requireInHandler();
      pendingSettles--;
      assert pendingSettles >= 0;
      settlement.settle();
      if (isStarted()) {
         topUpCreditIfNeeded();
      } else {
         tryCompletePendingQuiesce();
      }
   }

   private boolean tryCompletePendingQuiesce() {
      if (pendingSettles == 0 && receiver.getQueued() == 0 && receiver.getCredit() == 0) {
         if (isStopping()) {
            state = ReceiverState.STOPPED;
            signalStoppedCallback(true);
            return true;
         } else if (isDraining()) {
            state = ReceiverState.STARTED;

            if (pendingQuiesceTimeout != null) {
               pendingQuiesceTimeout.cancel(false);
               pendingQuiesceTimeout = null;
            }

            topUpCreditIfNeeded();
            return true;
         }
      }

      return false;
   }

   @Override
   public void onFlow(int credits, boolean drain) {
      if (isStarted()) {
         topUpCreditIfNeeded();
      } else {
         tryCompletePendingQuiesce();
      }
   }

   private void handleAbortedDelivery(Delivery delivery) {
      Receiver receiver = ((Receiver) delivery.getLink());

      closeCurrentReader();

      // Aborting implicitly remotely settles, so advance
      // receiver to the next delivery and settle locally.
      receiver.advance();
      delivery.settle();

      // Replenish the credit if not doing a drain and the receiver is still
      // started and has not initiated a stop request or has been closed
      if (!receiver.getDrain() && isStarted()) {
         receiver.flow(1);
      } else {
         tryCompletePendingQuiesce();
      }
   }

   private MessageReader getOrSelectMessageReader(Receiver receiver, Delivery delivery) {
      // The reader will be nulled once a message has been read, otherwise a large message
      // is being read in chunks from the remote.
      if (messageReader != null) {
         return messageReader;
      } else {
         final MessageReader selected = trySelectMessageReader(receiver, delivery);

         if (selected != null) {
            return messageReader = selected.open();
         } else {
            return null;
         }
      }
   }

   protected MessageReader trySelectMessageReader(Receiver receiver, Delivery delivery) {
      if (delivery.getMessageFormat() == AMQP_TUNNELED_CORE_MESSAGE_FORMAT) {
         failIfCoreTunnelNotEnabled();
         return coreMessageReader;
      } else if (delivery.getMessageFormat() == AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT) {
         failIfCoreTunnelNotEnabled();
         return coreLargeMessageReader;
      } else if (sessionSPI.getStorageManager() instanceof NullStorageManager) {
         // if we are dealing with the NullStorageManager we should just make it a regular message anyways
         return standardMessageReader;
      } else if (delivery.isPartial()) {
         if (minLargeMessageSize > 0 && delivery.available() >= minLargeMessageSize) {
            return largeMessageReader;
         } else {
            return null; // Not enough context to decide yet.
         }
      } else if (minLargeMessageSize > 0 && delivery.available() >= minLargeMessageSize) {
         // this is treating the case where the frameSize > minLargeMessage and the message is still large enough
         return largeMessageReader;
      } else {
         // Either minLargeMessageSize < 0 which means disable or the entire message has
         // arrived and is under the threshold so use the standard variant.
         return standardMessageReader;
      }
   }

   /**
    * called when Proton receives a message to be delivered via a Delivery.
    *
    * This may be called more than once per deliver so we have to cache the buffer until we have received it all.
    */
   @Override
   public final void onMessage(Delivery delivery) throws ActiveMQAMQPException {
      connection.requireInHandler();

      if (receiver.current() != delivery) {
         return;
      }

      if (delivery.isAborted()) {
         handleAbortedDelivery(delivery);
         return;
      }

      try {
         final MessageReader messageReader = getOrSelectMessageReader(receiver, delivery);

         if (messageReader == null) {
            return;
         }

         Message completeMessage;
         if ((completeMessage = messageReader.readBytes(delivery)) != null) {
            // notice the AMQP Large Message Reader will always return null
            // and call the onMessageComplete directly
            // since that happens asynchronously
            onMessageComplete(delivery, completeMessage, messageReader.getDeliveryAnnotations());
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
      }
   }

   public void onMessageComplete(Delivery delivery, Message message, DeliveryAnnotations deliveryAnnotations) {
      connection.requireInHandler();

      try {
         receiver.advance();

         Transaction tx = null;
         if (delivery.getRemoteState() instanceof TransactionalState txState) {
            try {
               tx = this.sessionSPI.getTransaction(txState.getTxnId(), false);
            } catch (Exception e) {
               this.onExceptionWhileReading(e);
            }
         }

         actualDelivery(message, delivery, deliveryAnnotations, receiver, tx);
      } finally {
         // reader is complete, we give it up now
         this.messageReader.close();
         this.messageReader = null;
      }
   }

   public void onExceptionWhileReading(Throwable e) {
      logger.warn(e.getMessage(), e);
      connection.runNow(() -> {
         // setting it enabled just in case a large message reader disabled it
         connection.enableAutoRead();
         ErrorCondition ec = new ErrorCondition(AmqpError.INTERNAL_ERROR, e.getMessage());
         connection.close(ec);
         connection.flush();
      });
   }

   public void deliveryFailed(Delivery delivery, Receiver receiver, Exception e) {
      connection.runNow(() -> {
         if (drainCreditOnNoSpace && isStarted() && receiver.getCredit() > 0 && isAddressFull(e)) {
            // Only when started do we want to drain off credit when the address is full since we
            // will have already reacted to a stop or close. Once drained this will return to the
            // started state if not stopped or closed since.
            state = ReceiverState.DRAINING;
            receiver.drain(0);

            if (drainTimeout > 0) {
               pendingQuiesceTimeout = protonSession.getServer().getScheduledPool().schedule(() -> {
                  final ErrorCondition error = new ErrorCondition(LinkError.DETACH_FORCED, "Timed out waiting for remote sender to drain");

                  connection.runLater(() -> {
                     try {
                        // Accounts for possible swap to stopping where the caller did not provide their
                        // own timeout so the stop will leave the drain timeout in place if one was active.
                        if (isStopping()) {
                           pendingStop.accept(this, false);
                        } else {
                           try {
                              close(error);
                           } finally {
                              receiver.close();
                           }
                        }
                     } catch (ActiveMQAMQPException ex) {
                        logger.debug("Error while attempting to close receiver that did not drain or stop in time", ex);
                     } finally {
                        connection.flush();
                     }
                  });
               }, drainTimeout, TimeUnit.MILLISECONDS);
            }
         }
         delivery.disposition(determineDeliveryState(((Source) receiver.getSource()), useModified, e));
         settle(delivery);
         connection.flush();
      });
   }

   /**
    * {@return either the fixed address assigned to this sender, or the last address used by an anonymous relay sender;
    * if this is an anonymous relay and no send has occurred then this method returns {@code null}}
    */
   protected abstract SimpleString getAddressInUse();

   /**
    * Perform the actual message processing for an inbound message. The subclass either consumes and settles the message
    * in place or hands it off to another intermediary who is responsible for eventually settling the newly read
    * message.
    *
    * @param message             The message as provided from the remote or after local transformation by subclass.
    * @param delivery            The proton delivery where the message bytes where read from
    * @param deliveryAnnotations The delivery annotations if present that accompanied the incoming message.
    * @param receiver            The proton receiver that represents the link over which the message was sent.
    * @param tx                  The transaction under which the incoming message was sent.
    */
   protected abstract void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx);

   /**
    * Final credit top up request API that will trigger a credit top up if the receiver is in
    * a state where a grant of additional receiver credit is allowable.
    */
   protected final void topUpCreditIfNeeded() {
      connection.requireInHandler();
      // this will configure a flow control event to happen once after the event loop has completed
      if (isStarted()) {
         connection.afterFlush(creditTopUpRunner);
      }
   }

   private void signalStoppedCallback(boolean stopped) {
      if (pendingQuiesceTimeout != null) {
         pendingQuiesceTimeout.cancel(false);
         pendingQuiesceTimeout = null;
      }

      if (pendingStop != null) {
         try {
            pendingStop.accept(this, stopped);
         } catch (Exception e) {
            logger.trace("Suppressed error from pending stop callback: ", e);
         } finally {
            pendingStop = null;
         }
      }
   }

   /**
    * Performs the actual credit top up logic for the receiver.
    * <p>
    * This can be overridden in the subclass to run its own logic for credit top up instead of using the default logic
    * used in this abstract base.
    */
   protected void doCreditTopUpRun() {
      connection.requireInHandler();
      if (isStarted()) {
         // Use the SessionSPI to allocate producer credits, or default, always allocate credit.
         sessionSPI.flow(getAddressInUse(), creditRunnable);
      }
   }

   protected void enableCoreTunneling() {
      coreTunnelingEnabled = true;

      if (coreLargeMessageReader == null || coreMessageReader == null) {
         coreLargeMessageReader = new AMQPTunneledCoreLargeMessageReader(this);
      }
      if (coreMessageReader == null) {
         coreMessageReader = new AMQPTunneledCoreMessageReader(this);
      }
   }

   protected void failIfCoreTunnelNotEnabled() {
      if (!coreTunnelingEnabled) {
         throw new UnsupportedOperationException("Core tunnel not enabled for this link");
      }
   }

   protected static boolean isAddressFull(final Exception e) {
      return e instanceof ActiveMQException amqe && ActiveMQExceptionType.ADDRESS_FULL.equals(amqe.getType());
   }

   protected static boolean outcomeSupported(final Source source, final Symbol outcome) {
      if (source != null && source.getOutcomes() != null) {
         return Arrays.asList((source).getOutcomes()).contains(outcome);
      }
      return false;
   }

   protected static Outcome getEffectiveDefaultOutcome(final Source source) {
      return (source.getOutcomes() == null || source.getOutcomes().length == 0) ? source.getDefaultOutcome() : null;
   }

   private static DeliveryState determineDeliveryState(final Source source, final boolean useModified, final Exception e) {
      Outcome defaultOutcome = getEffectiveDefaultOutcome(source);

      if (isAddressFull(e) && useModified && (outcomeSupported(source, Modified.DESCRIPTOR_SYMBOL) || defaultOutcome instanceof Modified)) {
         Modified modified = new Modified();
         modified.setDeliveryFailed(true);
         return modified;
      } else {
         if (outcomeSupported(source, Rejected.DESCRIPTOR_SYMBOL) || defaultOutcome instanceof Rejected) {
            return createRejected(e);
         } else if (source.getDefaultOutcome() instanceof DeliveryState) {
            return ((DeliveryState) source.getDefaultOutcome());
         } else {
            // The AMQP specification requires that Accepted is returned for this case. However there exist
            // implementations that set neither outcomes/default-outcome but use/expect for full range of outcomes.
            // To maintain compatibility with these implementations, we maintain previous behaviour.
            return createRejected(e);
         }
      }
   }

   private static Rejected createRejected(final Exception e) {
      ErrorCondition condition = new ErrorCondition();

      if (e instanceof ActiveMQSecurityException) {
         condition.setCondition(AmqpError.UNAUTHORIZED_ACCESS);
      } else if (isAddressFull(e)) {
         condition.setCondition(AmqpError.RESOURCE_LIMIT_EXCEEDED);
      } else {
         condition.setCondition(Symbol.valueOf("failed"));
      }
      condition.setDescription(e.getMessage());

      Rejected rejected = new Rejected();
      rejected.setError(condition);

      return rejected;
   }

   public static boolean isBellowThreshold(int credit, int pending, int threshold) {
      return credit <= threshold - pending;
   }

   public static int calculatedUpdateRefill(int refill, int credits, int pending) {
      return refill - credits - pending;
   }
}
