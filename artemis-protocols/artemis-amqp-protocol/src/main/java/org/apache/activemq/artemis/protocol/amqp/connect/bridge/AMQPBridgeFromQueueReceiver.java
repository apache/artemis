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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.AMQP_LINK_INITIALIZER_KEY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyDesiredCapability;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeMetrics.ReceiverMetrics;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpJmsSelectorFilter;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerReceiverContext;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receiver implementation for Bridged Queues that receives from a remote
 * AMQP peer and forwards those messages onto the internal broker Queue for
 * consumption by an attached consumers.
 */
public class AMQPBridgeFromQueueReceiver extends AMQPBridgeReceiver {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final int DEFAULT_PENDING_MSG_CHECK_BACKOFF_MULTIPLIER = 2;
   public static final int DEFAULT_PENDING_MSG_CHECK_MAX_DELAY = 30;

   public AMQPBridgeFromQueueReceiver(AMQPBridgeFromPolicyManager policyManager,
                                      AMQPBridgeReceiverConfiguration configuration,
                                      AMQPSessionContext session,
                                      AMQPBridgeReceiverInfo receiverInfo,
                                      AMQPBridgeQueuePolicy policy,
                                      ReceiverMetrics metrics) {
      super(policyManager, configuration, session, receiverInfo, policy, metrics);
   }

   @Override
   public int getReceiverIdleTimeout() {
      return configuration.getQueueReceiverIdleTimeout();
   }

   @Override
   protected void doCreateReceiver() {
      try {
         final Receiver protonReceiver = session.getSession().receiver(generateLinkName());
         final Target target = new Target();
         final Source source = new Source();
         final String address = receiverInfo.getRemoteAddress();
         final String filterString = receiverInfo.getFilterString();
         final Queue localQueue = bridgeManager.getServer().locateQueue(receiverInfo.getLocalQueue());

         source.setOutcomes(Arrays.copyOf(OUTCOMES, OUTCOMES.length));
         source.setDefaultOutcome(DEFAULT_OUTCOME);
         source.setDurable(TerminusDurability.NONE);
         source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
         source.setAddress(address);
         source.setCapabilities(getRemoteTerminusCapabilities());

         if (filterString != null && !filterString.isBlank()) {
            final AmqpJmsSelectorFilter jmsFilter = new AmqpJmsSelectorFilter(filterString);
            final Map<Symbol, Object> filtersMap = new HashMap<>();
            filtersMap.put(AmqpSupport.JMS_SELECTOR_KEY, jmsFilter);

            source.setFilter(filtersMap);
         }

         target.setAddress(receiverInfo.getLocalFqqn());

         final Map<Symbol, Object> receiverProperties;
         if (receiverInfo.getPriority() != null) {
            receiverProperties = new HashMap<>();
            receiverProperties.put(RECEIVER_PRIORITY, receiverInfo.getPriority());
         } else {
            receiverProperties = null;
         }

         protonReceiver.setSenderSettleMode(configuration.isUsingPresettledSenders() ? SenderSettleMode.SETTLED : SenderSettleMode.UNSETTLED);
         protonReceiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
         // If enabled offer core tunneling which we prefer to AMQP conversions of core as
         // the large ones will be converted to standard AMQP messages in memory. When not
         // offered the remote must not use core tunneling and AMQP conversion will be the
         // fallback.
         if (configuration.isCoreMessageTunnelingEnabled()) {
            protonReceiver.setOfferedCapabilities(new Symbol[] {AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT});
         }
         protonReceiver.setProperties(receiverProperties);
         protonReceiver.setTarget(target);
         protonReceiver.setSource(source);
         protonReceiver.open();

         final ScheduledFuture<?> openTimeoutTask;
         final AtomicBoolean openTimedOut = new AtomicBoolean(false);

         if (configuration.getLinkAttachTimeout() > 0) {
            openTimeoutTask = bridgeManager.getServer().getScheduledPool().schedule(() -> {
               openTimedOut.set(true);
               bridgeManager.signalResourceCreateError(ActiveMQAMQPProtocolMessageBundle.BUNDLE.brokerConnectionTimeout());
            }, configuration.getLinkAttachTimeout(), TimeUnit.SECONDS);
         } else {
            openTimeoutTask = null;
         }

         this.protonReceiver = protonReceiver;

         protonReceiver.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
            try {
               if (openTimeoutTask != null) {
                  openTimeoutTask.cancel(false);
               }

               if (openTimedOut.get()) {
                  return;
               }

               final boolean linkOpened = protonReceiver.getRemoteSource() != null;

               // Intercept remote close and check for valid reasons for remote closure such as
               // the remote peer not having a matching node for this subscription or from an
               // operator manually closing the link etc.
               bridgeManager.addLinkClosedInterceptor(receiverInfo.getId(), this::remoteLinkClosedInterceptor);

               receiver = new AMQPBridgeQueueDeliveryReceiver(localQueue, receiverInfo, protonReceiver);

               if (linkOpened) {
                  logger.debug("AMQP Bridge {} queue receiver {} completed open", bridgeManager.getName(), receiverInfo);
               } else {
                  logger.debug("AMQP Bridge {} queue receiver {} rejected by remote", bridgeManager.getName(), receiverInfo);
               }

               session.addReceiver(protonReceiver, (session, protonRcvr) -> {
                  return this.receiver;
               });

               if (linkOpened && remoteOpenHandler != null) {
                  remoteOpenHandler.accept(this);
               }
            } catch (Exception e) {
               bridgeManager.signalError(e);
            }
         });
      } catch (Exception e) {
         bridgeManager.signalError(e);
      }

      connection.flush();
   }

   private String generateLinkName() {
      return "amqp-bridge-" + bridgeManager.getName() +
             "-policy-" + policy.getPolicyName() +
             "-queue-receiver-" + receiverInfo.getRemoteAddress() +
             "-" + bridgeManager.getServer().getNodeID() + ":" +
             LINK_SEQUENCE_ID.getAndIncrement();
   }

   private static int caclulateNextDelay(int lastDelay, int backoffMultiplier, int maxDelay) {
      final int nextDelay;

      if (lastDelay == 0) {
         nextDelay = 1;
      } else {
         nextDelay = Math.min(lastDelay * backoffMultiplier, maxDelay);
      }

      return nextDelay;
   }

   /**
    * Wrapper around the standard receiver context that provides bridge specific entry
    * points and customizes inbound delivery handling for this Queue receiver.
    */
   private class AMQPBridgeQueueDeliveryReceiver extends ProtonServerReceiverContext {

      private final SimpleString cachedFqqn;
      private final Queue localQueue;

      private boolean closed;

      /**
       * Creates the AMQP bridge receiver instance.
       *
       * @param session
       *    The server session context bound to the receiver instance.
       * @param receiver
       *    The proton receiver that will be wrapped in this server context instance.
       */
      AMQPBridgeQueueDeliveryReceiver(Queue localQueue, AMQPBridgeReceiverInfo receiverInfo, Receiver receiver) {
         super(session.getSessionSPI(), session.getAMQPConnectionContext(), session, receiver);

         this.cachedFqqn = SimpleString.of(receiverInfo.getLocalFqqn());
         this.localQueue = localQueue;
      }

      @Override
      protected boolean isUseModifiedForTransientDeliveryErrors(AMQPConnectionContext connection) {
         return configuration.isUseModifiedForTransientDeliveryErrors();
      }

      @Override
      protected boolean isDrainOnTransientDeliveryErrors(AMQPConnectionContext connection) {
         return configuration.isDrainOnTransientDeliveryErrors();
      }

      @Override
      protected int getLinkQuiesceTimeout(AMQPConnectionContext connection) {
         return configuration.getLinkQuiesceTimeout();
      }

      @Override
      public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
         super.close(remoteLinkClose);

         if (!closed) {
            closed = true;

            try {
               AMQPBridgeManagementSupport.unregisterBridgeReceiver(AMQPBridgeFromQueueReceiver.this);
            } catch (Exception e) {
               logger.debug("Error caught when trying to remove bridge queue receiver from management", e);
            }

            if (remoteLinkClose && remoteCloseHandler != null) {
               try {
                  remoteCloseHandler.accept(AMQPBridgeFromQueueReceiver.this);
               } catch (Exception e) {
                  logger.debug("User remote closed handler threw error: ", e);
               } finally {
                  remoteCloseHandler = null;
               }
            }
         }
      }

      @Override
      public void initialize() throws Exception {
         initialized = true;

         final Target target = (Target) receiver.getRemoteTarget();

         // Match the settlement mode of the remote instead of relying on the default of MIXED.
         receiver.setSenderSettleMode(receiver.getRemoteSenderSettleMode());

         // We don't currently support SECOND so enforce that the answer is always FIRST
         receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);

         // the target will have an address and it will naturally have a Target otherwise
         // the remote is misbehaving and we close it.
         if (target == null || target.getAddress() == null || target.getAddress().isEmpty()) {
            throw new ActiveMQAMQPInternalErrorException("Remote should have sent a valid Target but we got: " + target);
         }

         if (!target.getAddress().equals(receiverInfo.getLocalFqqn())) {
            throw new ActiveMQAMQPInternalErrorException("Remote should have sent a matching Target FQQN but we got: " + target.getAddress());
         }

         address = SimpleString.of(receiverInfo.getLocalQueue());
         explicitRoutingType = getExplicitRoutingType(target.getCapabilities(), address);
         implicitRoutingType = getImplicitRoutingType(address);

         final RoutingType selectedRoutingType = explicitRoutingType != null ? explicitRoutingType : implicitRoutingType;

         try {
            final QueueQueryResult result = sessionSPI.queueQuery(address, selectedRoutingType, false);

            // We initiated this link so the settings should refer to an queue that definitely exists
            // however there is a chance the address was removed in the interim.
            if (!result.isExists()) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.addressDoesntExist(address.toString());
            }
         } catch (ActiveMQAMQPNotFoundException e) {
            throw e;
         } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
         }

         // If we offered core tunneling then check if the remote indicated support and enabled the readers
         if (configuration.isCoreMessageTunnelingEnabled() && verifyDesiredCapability(receiver, CORE_MESSAGE_TUNNELING_SUPPORT)) {
            enableCoreTunneling();
         }

         try {
            AMQPBridgeManagementSupport.registerBridgeReceiver(AMQPBridgeFromQueueReceiver.this);
         } catch (Exception e) {
            logger.debug("Error caught when trying to add bridge queue receiver to management", e);
         }

         topUpCreditIfNeeded();
      }

      @Override
      protected void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx) {
         try {
            if (logger.isTraceEnabled()) {
               logger.trace("AMQP Bridge {} queue receiver {} dispatching incoming message: {}", bridgeManager.getName(), receiverInfo, message);
            }

            final Message theMessage = transformer.transform(message);

            if (theMessage != message && logger.isTraceEnabled()) {
               logger.trace("The transformer {} replaced the original message {} with a new instance {}",
                            transformer, message, theMessage);
            }

            sessionSPI.serverSend(this, tx, receiver, delivery, cachedFqqn, getPreferredRoutingType(), routingContext, theMessage);
         } catch (Exception e) {
            logger.warn("Inbound delivery for {} encountered an error: {}", receiverInfo, e.getMessage(), e);
            deliveryFailed(delivery, receiver, e);
         } finally {
            recordMessageReceived(message);
         }
      }

      @Override
      protected Runnable createCreditRunnable(AMQPConnectionContext connection) {
         // We defer to the configuration instance as opposed to the base class version that reads
         // from the connection this allows us to defer to configured policy properties that specify
         // credit.
         if (configuration.getReceiverCredits() > 0) {
            return createCreditRunnable(configuration.getReceiverCredits(), configuration.getReceiverCreditsLow(), receiver, connection, this);
         } else {
            return this::checkIfCreditTopUpNeeded;
         }
      }

      @Override
      protected int getConfiguredMinLargeMessageSize(AMQPConnectionContext connection) {
         // Looks at policy properties first before looking at bridge configuration and finally
         // going to the base connection context to read the URI configuration.
         return configuration.getLargeMessageThreshold();
      }

      // Credit handling here kicks in when the connection is configured for zero link credit and
      // we want to then batch credit to the remote only when there is no local pending messages
      // which implies the local consumers are keeping up and we can pull more across.

      private final AtomicBoolean creditTopUpInProgress = new AtomicBoolean();

      private final Runnable checkForNoBacklogRunnable = this::checkForNoBacklogOnQueue;
      private final Runnable performCreditTopUpRunnable = this::performCreditTopUp;

      private int lastBacklogCheckDelay;

      private void checkIfCreditTopUpNeeded() {
         if (!connection.isHandler()) {
            connection.runLater(creditRunnable);
            return;
         }

         if (receiver.getCredit() + AMQPBridgeQueueDeliveryReceiver.this.pendingSettles <= 0 && !creditTopUpInProgress.get()) {
            // We don't need more scheduled tasks stacking up trying to issue a new
            // batch of credit so lets gate this now so they give up.
            creditTopUpInProgress.set(true);

            // Move to the Queue executor to ensure we get a proper read on the state of pending messages.
            localQueue.getExecutor().execute(checkForNoBacklogRunnable);
         }
      }

      private void checkForNoBacklogOnQueue() {
         // Only when there is no backlog do we grant new credit, otherwise we must wait until
         // the local backlog is zero. The top up must be run from the connection executor.
         if (localQueue.getPendingMessageCount() == 0) {
            lastBacklogCheckDelay = 0;
            connection.runLater(performCreditTopUpRunnable);
         } else {
            lastBacklogCheckDelay = caclulateNextDelay(lastBacklogCheckDelay, DEFAULT_PENDING_MSG_CHECK_BACKOFF_MULTIPLIER, DEFAULT_PENDING_MSG_CHECK_MAX_DELAY);

            bridgeManager.getScheduler().schedule(() -> {
               localQueue.getExecutor().execute(checkForNoBacklogRunnable);
            }, lastBacklogCheckDelay, TimeUnit.SECONDS);
         }
      }

      private void performCreditTopUp() {
         connection.requireInHandler();

         try {
            if (receiver.getLocalState() != EndpointState.ACTIVE) {
               return; // Closed before this was triggered.
            }

            receiver.flow(configuration.getPullReceiverBatchSize());
            connection.instantFlush();
            lastBacklogCheckDelay = 0;
         } finally {
            creditTopUpInProgress.set(false);
         }
      }
   }
}
