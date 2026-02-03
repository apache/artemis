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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueMaxConsumerLimitReached;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.ConversionException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPResourceLimitExceededException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.transaction.ProtonTransactionImpl;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Outcome;
import org.apache.qpid.proton.amqp.transaction.TransactionalState;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.DeliveryState.DeliveryStateType;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * This is the Equivalent for the ServerConsumer
 */
public class ProtonServerSenderContext extends ProtonInitializable implements ProtonDeliveryHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final byte[] EMPTY_DELIVERY_TAG = new byte[0];

   private final ConnectionFlushIOCallback connectionFlusher = new ConnectionFlushIOCallback();

   protected final AMQPSessionContext protonSession;
   protected final Sender sender;
   protected final AMQPConnectionContext connection;
   protected final AMQPSessionCallback sessionSPI;

   /**
    * The model proton uses requires us to hold a lock in certain times to sync the credits we have versus the credits
    * that are being held in proton
    */
   private final Object creditsLock = new Object();
   private final boolean amqpTreatRejectAsUnmodifiedDeliveryFailed;
   private final AtomicBoolean draining = new AtomicBoolean(false);

   private SenderController controller;
   private ServerConsumer brokerConsumer;
   private ReadyListener onflowControlReady;
   private boolean closed = false;

   private boolean preSettle;
   private int credits = 0;
   private AtomicInteger pending = new AtomicInteger(0);
   private java.util.function.Consumer<? super MessageReference> beforeDelivery;

   protected volatile Runnable afterDelivery;
   protected volatile MessageWriter messageWriter = SenderController.REJECTING_MESSAGE_WRITER;

   public ProtonServerSenderContext(AMQPConnectionContext connection,
                                    Sender sender,
                                    AMQPSessionContext protonSession,
                                    AMQPSessionCallback server) {
      this(connection, sender, protonSession, server, null);
   }

   public ProtonServerSenderContext(AMQPConnectionContext connection,
                                    Sender sender,
                                    AMQPSessionContext protonSession,
                                    AMQPSessionCallback server,
                                    SenderController senderController) {
      super();
      this.controller = senderController;
      this.connection = connection;
      this.sender = sender;
      this.protonSession = protonSession;
      this.sessionSPI = server;
      amqpTreatRejectAsUnmodifiedDeliveryFailed = this.connection.getProtocolManager()
                                                                 .isAmqpTreatRejectAsUnmodifiedDeliveryFailed();
   }

   public ProtonServerSenderContext setBeforeDelivery(java.util.function.Consumer<? super MessageReference> beforeDelivery) {
      this.beforeDelivery = beforeDelivery;
      return this;
   }

   public ServerConsumer getBrokerConsumer() {
      return brokerConsumer;
   }

   protected String getClientId() {
      return connection.getRemoteContainer();
   }

   public AMQPSessionContext getSessionContext() {
      return protonSession;
   }

   public Sender getSender() {
      return sender;
   }

   @Override
   public void onFlow(int currentCredits, boolean drain) {
      if (logger.isDebugEnabled()) {
         logger.debug("flow {}, draing={}", currentCredits, drain);
      }

      connection.requireInHandler();

      setupCredit();

      final ServerConsumerImpl serverConsumer = (ServerConsumerImpl) brokerConsumer;

      if (drain) {
         // If the draining is already running, then don't do anything
         if (draining.compareAndSet(false, true)) {
            flushDrain(serverConsumer);
         }
      } else {
         serverConsumer.receiveCredits(-1);
      }
   }

   private void flushDrain(ServerConsumerImpl serverConsumer) {
      serverConsumer.forceDelivery(1, () -> {
         try {
            connection.runNow(() -> {
               if (messageWriter.isWriting()) {
                  // retry the flush after the large message is done
                  afterDelivery = () -> flushDrain(serverConsumer);
               } else {
                  drained();
               }
            });
         } finally {
            draining.set(false);
         }
      });
   }

   private void drained() {
      connection.requireInHandler();
      sender.drained();
      connection.instantFlush();
      setupCredit();
   }

   public boolean hasCredits() {
      if (messageWriter.isWriting() || !connection.flowControl(onflowControlReady)) {
         return false;
      }

      synchronized (creditsLock) {
         return credits > 0 && sender.getLocalState() != EndpointState.CLOSED;
      }
   }

   private void setupCredit() {
      synchronized (creditsLock) {
         this.credits = sender.getCredit() - pending.get();
         if (credits < 0) {
            credits = 0;
         }
      }
   }

   public void start() throws ActiveMQAMQPException {
      sessionSPI.start();

      try {
         // to do whatever you need to make the broker start sending messages to the consumer
         // this could be null if a link reattach has happened
         if (brokerConsumer != null) {
            sessionSPI.startSender(brokerConsumer);
         }
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorStartingConsumer(e.getMessage());
      }
   }

   /**
    * Create the actual underlying Apache Artemis Server Consumer
    */
   @Override
   public void initialize() throws Exception {
      initialized = true;

      if (controller == null) {
         controller = new DefaultSenderController(protonSession, sender, getClientId());
      }

      try {
         brokerConsumer = (ServerConsumer) controller.init(this);
         preSettle = sender.getSenderSettleMode() == SenderSettleMode.SETTLED;
         onflowControlReady = brokerConsumer::promptDelivery;
      } catch (ActiveMQAMQPResourceLimitExceededException e1) {
         throw e1;
      } catch (ActiveMQSecurityException e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(e.getMessage());
      } catch (ActiveMQQueueMaxConsumerLimitReached e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingConsumer(e.getMessage());
      } catch (ActiveMQException e) {
         throw e;
      } catch (Exception e) {
         ActiveMQAMQPInternalErrorException internalErrorException = ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingConsumer(e.getMessage());
         internalErrorException.initCause(e);
         throw internalErrorException;
      }
   }

   @Override
   public void close(ErrorCondition condition) throws ActiveMQAMQPException {
      if (!closed) {
         closed = true;

         if (condition != null) {
            sender.setCondition(condition);
         }
         protonSession.removeSender(sender);

         connection.runNow(() -> {
            sender.close();
            controller.close(condition);
            try {
               if (brokerConsumer != null) {
                  sessionSPI.closeSender(brokerConsumer);
               }
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            } finally {
               messageWriter.close();
            }
            connection.flush();
         });
      }
   }

   @Override
   public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
      if (!closed) {
         // we need to mark closed first to make sure no more adds are accepted
         closed = true;

         // MessageReferences are sent to the Connection executor (Netty Loop)
         // as a result the returning references have to be done later after they
         // had their chance to finish and clear the runnable
         connection.runNow(() -> {
            try {
               protonSession.removeSender(sender);
               if (brokerConsumer != null) {
                  sessionSPI.closeSender(brokerConsumer);
               }
               // if this is a link close rather than a connection close or detach, we need to delete
               // any durable resources for say pub subs
               controller.close(remoteLinkClose);
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            } finally {
               messageWriter.close();
            }
         });
      }
   }

   @Override
   public void onMessage(Delivery delivery) throws ActiveMQAMQPException {
      if (closed) {
         return;
      }

      final OperationContext oldContext = sessionSPI.recoverContext();

      try {
         final MessageReference reference = (MessageReference) delivery.getContext();
         final Message message = reference != null ? reference.getMessage() : null;
         final DeliveryState remoteState = delivery.getRemoteState();

         if (remoteState != null && remoteState.getType() == DeliveryStateType.Accepted) {
            // this can happen in the two phase settlement mode, that is the receiver accepts and settles
            // separately acknowledging again would show an exception but would have no negative effect
            // but best to handle anyway.
            if (!delivery.isSettled()) {
               doAck(message);

               delivery.settle();
            }
         } else {
            handleExtendedDeliveryOutcomes(message, delivery, remoteState);
         }

         if (!preSettle) {
            protonSession.replaceTag(delivery.getTag());
         }
      } finally {
         sessionSPI.afterIO(connectionFlusher);
         sessionSPI.resetContext(oldContext);
      }
   }

   protected void doAck(Message message) throws ActiveMQAMQPIllegalStateException {
      // An AMQP peer can send back dispositions for any of the dispatched messages in
      // any order so the broker must use individual acknowledgement instead of batched
      try {
         sessionSPI.ack(null, brokerConsumer, message);
      } catch (Exception e) {
         logger.warn(e.toString(), e);
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorAcknowledgingMessage(message.toString(), e.getMessage());
      }
   }

   private boolean handleExtendedDeliveryOutcomes(Message message, Delivery delivery, DeliveryState remoteState) throws ActiveMQAMQPException {
      boolean settleImmediate = true;
      boolean handled = true;

      if (remoteState == null) {
         logger.debug("Received null disposition for delivery update: {}", remoteState);
         return true;
      }

      switch (remoteState.getType()) {
         case Transactional:
            // When the message arrives with a TransactionState disposition the ack should
            // enlist the message into the transaction associated with the given txn ID.
            final TransactionalState txState = (TransactionalState) remoteState;
            final ProtonTransactionImpl tx = (ProtonTransactionImpl) this.sessionSPI.getTransaction(txState.getTxnId(), false);

            if (txState.getOutcome() != null) {
               settleImmediate = false;
               final Outcome outcome = txState.getOutcome();

               if (outcome instanceof Accepted) {
                  if (!delivery.remotelySettled()) {
                     TransactionalState txAccepted = new TransactionalState();
                     txAccepted.setOutcome(Accepted.getInstance());
                     txAccepted.setTxnId(txState.getTxnId());
                     delivery.disposition(txAccepted);
                  }
                  // An AMQP peer can send back dispositions for any of the dispatched messages in
                  // any order so the broker must use individual acknowledgement instead of batched
                  try {
                     if (RefCountMessage.isRefTraceEnabled()) {
                        RefCountMessage.deferredDebug(message, "Adding ACK message to TX {}", (tx == null ? "no-tx" : tx.getID()));
                     }
                     sessionSPI.ack(tx, brokerConsumer, message);
                     tx.addDelivery(delivery, this);
                  } catch (Exception e) {
                     throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorAcknowledgingMessage(message.toString(), e.getMessage());
                  }
               }
            }
            break;
         case Released:
            try {
               sessionSPI.cancel(brokerConsumer, message, false);
            } catch (Exception e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCancellingMessage(message.toString(), e.getMessage());
            }
            break;
         case Rejected:
            try {
               if (amqpTreatRejectAsUnmodifiedDeliveryFailed) {
                  // We could be more discriminating - for instance check for AmqpError#RESOURCE_LIMIT_EXCEEDED
                  sessionSPI.cancel(brokerConsumer, message, true);
               } else {
                  sessionSPI.reject(brokerConsumer, message);
               }
            } catch (Exception e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCancellingMessage(message.toString(), e.getMessage());
            }
            break;
         case Modified:
            try {
               final Modified modification = (Modified) remoteState;

               if (Boolean.TRUE.equals(modification.getUndeliverableHere())) {
                  message.rejectConsumer(brokerConsumer.sequentialID());
               }

               if (Boolean.TRUE.equals(modification.getDeliveryFailed())) {
                  sessionSPI.cancel(brokerConsumer, message, true);
               } else {
                  sessionSPI.cancel(brokerConsumer, message, false);
               }
            } catch (Exception e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCancellingMessage(message.toString(), e.getMessage());
            }
            break;
         default:
            logger.debug("Received null or unknown disposition for delivery update: {}", remoteState);
            handled = false;
      }

      if (settleImmediate) {
         delivery.settle();
      }

      return handled;
   }

   private final class ConnectionFlushIOCallback implements IOCallback {

      @Override
      public void done() {
         connection.flush();
      }

      @Override
      public void onError(int errorCode, String errorMessage) {
         connection.flush();
      }
   }

   public void settle(Delivery delivery) {
      connection.requireInHandler();
      delivery.settle();
   }

   public synchronized void checkState() {
      sessionSPI.resumeDelivery(brokerConsumer);
   }

   /**
    * handle an out going message from Apache Artemis, send via the Proton Sender
    */
   public int deliverMessage(final MessageReference messageReference, final ServerConsumer consumer) throws Exception {
      if (closed) {
         return 0;
      }

      if (beforeDelivery != null) {
         beforeDelivery.accept(messageReference);
      }

      synchronized (creditsLock) {
         if (sender.getLocalState() == EndpointState.CLOSED) {
            return 0;
         }
         pending.incrementAndGet();
         credits--;
      }

      final MessageWriter messageWriter = controller.selectOutgoingMessageWriter(this, messageReference).open(messageReference);

      // Preserve for hasCredits to check for busy state and possible abort on close
      this.messageWriter = messageWriter;

      if (messageReference instanceof Runnable runnable && consumer.allowReferenceCallback()) {
         messageReference.onDelivery(messageWriter);
         connection.runNow(runnable);
      } else {
         connection.runNow(() -> messageWriter.accept(messageReference));
      }

      // This is because on AMQP we only send messages based in credits, not bytes
      return 1;
   }

   Delivery createDelivery(MessageReference messageReference, int messageFormat) {
      // we only need a tag if we are going to settle later
      final Delivery delivery = sender.delivery(preSettle ? EMPTY_DELIVERY_TAG : protonSession.getTag());

      delivery.setContext(messageReference);
      delivery.setMessageFormat(messageFormat);

      return delivery;
   }

   void reportDeliveryError(MessageWriter deliveryWriter, MessageReference messageReference, Exception e) {
      if (e instanceof ConversionException && brokerConsumer.getBinding() instanceof LocalQueueBinding localQueueBinding) {
         ActiveMQAMQPProtocolLogger.LOGGER.messageConversionFailed(e);
         try {
            localQueueBinding.getQueue().sendToDeadLetterAddress(null, messageReference);
         } catch (Exception e1) {
            ActiveMQAMQPProtocolLogger.LOGGER.unableToSendMessageToDLA(messageReference, e1);
         }
         return;
      }
      logger.warn(e.getMessage(), e);
      brokerConsumer.errorProcessing(e, messageReference);
   }

   void reportDeliveryComplete(MessageWriter deliveryWriter, MessageReference messageReference, Delivery delivery, boolean promptDelivery) {
      final Runnable localRunnable = afterDelivery;

      afterDelivery = null;

      synchronized (creditsLock) {
         pending.decrementAndGet();
      }

      if (preSettle) {
         // Presettled means the client implicitly accepts any delivery we send it.
         try {
            sessionSPI.ack(null, brokerConsumer, messageReference.getMessage());
         } catch (Exception e) {
            logger.debug(e.getMessage(), e);
         }
         delivery.settle();
      } else {
         sender.advance();
      }

      deliveryWriter.close();

      if (!closed) {
         if (localRunnable != null) {
            localRunnable.run();
         }

         if (promptDelivery) {
            brokerConsumer.promptDelivery();
            connection.instantFlush();
         } else {
            connection.flush();
         }
      }
   }
}
