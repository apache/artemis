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
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.SHARED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.SHARED_SUBS;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyCapabilities;
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
import org.apache.activemq.artemis.core.server.AddressQueryResult;
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
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receiver implementation for Bridged Addresses that receives from a remote
 * AMQP peer and forwards those messages onto the internal broker Address for
 * consumption by an attached consumers.
 */
public class AMQPBridgeFromAddressReceiver extends AMQPBridgeReceiver {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public AMQPBridgeFromAddressReceiver(AMQPBridgeFromPolicyManager policyManager, AMQPBridgeReceiverConfiguration configuration,
                                        AMQPSessionContext session, AMQPBridgeReceiverInfo receiverInfo,
                                        AMQPBridgeAddressPolicy policy, ReceiverMetrics metrics) {
      super(policyManager, configuration, session, receiverInfo, policy, metrics);
   }

   @Override
   public final AMQPBridgeAddressPolicy getPolicy() {
      return (AMQPBridgeAddressPolicy) policy;
   }

   @Override
   public int getReceiverIdleTimeout() {
      return configuration.getAddressReceiverIdleTimeout();
   }

   private boolean isUseSharedDurableSubscriptions() {
      return getPolicy().isUseDurableSubscriptions() &&
             configuration.isPreferSharedDurableSubscriptions() &&
             verifyCapabilities(session.getSession().getConnection().getRemoteOfferedCapabilities(), SHARED_SUBS);
   }

   @Override
   protected void doCreateReceiver() {
      try {
         final Receiver protonReceiver = session.getSession().receiver(generateLinkName(getPolicy()));
         final Target target = new Target();
         final Source source = new Source();
         final String filterString = receiverInfo.getFilterString();

         source.setAddress(receiverInfo.getRemoteAddress());
         source.setOutcomes(Arrays.copyOf(OUTCOMES, OUTCOMES.length));
         source.setDefaultOutcome(DEFAULT_OUTCOME);
         source.setCapabilities(getRemoteTerminusCapabilities());

         if (getPolicy().isUseDurableSubscriptions()) {
            source.setDurable(TerminusDurability.UNSETTLED_STATE);
            source.setExpiryPolicy(TerminusExpiryPolicy.NEVER);
            source.setDistributionMode(AmqpSupport.COPY);

            if (isUseSharedDurableSubscriptions()) {
               source.setCapabilities(SHARED);
            }
         } else {
            source.setDurable(TerminusDurability.NONE);
            source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
         }

         if (filterString != null && !filterString.isBlank()) {
            final AmqpJmsSelectorFilter jmsFilter = new AmqpJmsSelectorFilter(filterString);
            final Map<Symbol, Object> filtersMap = new HashMap<>();
            filtersMap.put(AmqpSupport.JMS_SELECTOR_KEY, jmsFilter);

            source.setFilter(filtersMap);
         }

         target.setAddress(receiverInfo.getLocalAddress());

         final Map<Symbol, Object> receiverProperties;
         if (receiverInfo.getPriority() != null) {
            receiverProperties = new HashMap<>();
            receiverProperties.put(RECEIVER_PRIORITY, receiverInfo.getPriority().intValue());
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

               receiver = new AMQPBridgeAddressDeliveryReceiver(session, receiverInfo, protonReceiver);

               if (linkOpened) {
                  logger.debug("AMQP Bridge {} address receiver {} completed open", bridgeManager.getName(), receiverInfo);
               } else {
                  logger.debug("AMQP Bridge {} address receiver {} rejected by remote", bridgeManager.getName(), receiverInfo);
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

   private String generateLinkName(AMQPBridgeAddressPolicy policy) {
      if (isUseSharedDurableSubscriptions()) {
         // Append the sequence ID with a '|' to create a shared durable subscription for reconnects
         // but a unique name to prevent stealing on rapid demand cycles.
         return "amqp-bridge-" + bridgeManager.getName() +
                "-policy-" + policy.getPolicyName() +
                "-address-receiver-" + receiverInfo.getRemoteAddress() +
                "-" + bridgeManager.getServer().getNodeID() +
                "|" + LINK_SEQUENCE_ID.incrementAndGet();
      } else if (policy.isUseDurableSubscriptions()) {
         // Omit the sequence ID to create a stable durable subscription name for reconnects.
         return "amqp-bridge-" + bridgeManager.getName() +
                "-policy-" + policy.getPolicyName() +
                "-address-receiver-" + receiverInfo.getRemoteAddress() +
                "-" + bridgeManager.getServer().getNodeID();
      } else {
         return "amqp-bridge-" + bridgeManager.getName() +
                "-policy-" + policy.getPolicyName() +
                "-address-receiver-" + receiverInfo.getRemoteAddress() +
                "-" + bridgeManager.getServer().getNodeID() +
                "-" + LINK_SEQUENCE_ID.incrementAndGet();
      }
   }

   /**
    * Wrapper around the standard receiver context that provides bridge specific entry
    * points and customizes inbound delivery handling for this Address receiver.
    */
   private class AMQPBridgeAddressDeliveryReceiver extends ProtonServerReceiverContext {

      private final SimpleString cachedAddress;
      private boolean closed;

      /**
       * Creates the AMQP bridge receiver instance.
       *
       * @param session
       *    The server session context bound to the receiver instance.
       * @param receiver
       *    The proton receiver that will be wrapped in this server context instance.
       */
      AMQPBridgeAddressDeliveryReceiver(AMQPSessionContext session, AMQPBridgeReceiverInfo receiverInfo, Receiver receiver) {
         super(session.getSessionSPI(), session.getAMQPConnectionContext(), session, receiver);

         this.cachedAddress = SimpleString.of(receiverInfo.getLocalAddress());
      }

      @Override
      public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
         super.close(remoteLinkClose);

         if (!closed) {
            closed = true;

            try {
               AMQPBridgeManagementSupport.unregisterBridgeReceiver(AMQPBridgeFromAddressReceiver.this);
            } catch (Exception e) {
               logger.debug("Error caught when trying to remove bridge address consumer from management", e);
            }

            if (remoteLinkClose && remoteCloseHandler != null) {
               try {
                  remoteCloseHandler.accept(AMQPBridgeFromAddressReceiver.this);
               } catch (Exception e) {
                  logger.debug("User remote closed handler threw error: ", e);
               } finally {
                  remoteCloseHandler = null;
               }
            }
         }
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
      protected Runnable createCreditRunnable(AMQPConnectionContext connection) {
         // We defer to the configuration instance as opposed to the base class version that reads
         // from the connection this allows us to defer to configured policy properties that specify
         // credit over those in the bridge configuration or on the connector URI.
         return createCreditRunnable(configuration.getReceiverCredits(), configuration.getReceiverCreditsLow(), receiver, connection, this);
      }

      @Override
      protected int getConfiguredMinLargeMessageSize(AMQPConnectionContext connection) {
         // Looks at policy properties first before looking at receiver configuration and finally
         // going to the base connection context to read the URI configuration.
         return configuration.getLargeMessageThreshold();
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

         if (!target.getAddress().equals(receiverInfo.getLocalAddress())) {
            throw new ActiveMQAMQPInternalErrorException("Remote should have sent a matching Target address but we got: " + target.getAddress());
         }

         address = SimpleString.of(receiverInfo.getLocalAddress());
         explicitRoutingType = getExplicitRoutingType(target.getCapabilities(), address);
         implicitRoutingType = getImplicitRoutingType(address);

         final RoutingType selectedRoutingType = explicitRoutingType != null ? explicitRoutingType : implicitRoutingType;

         try {
            final AddressQueryResult result = sessionSPI.addressQuery(address, selectedRoutingType, false);

            // We initiated this link so the settings should refer to an address that definitely exists
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
            AMQPBridgeManagementSupport.registerBridgeReceiver(AMQPBridgeFromAddressReceiver.this);
         } catch (Exception e) {
            logger.debug("Error caught when trying to add bridge address consumer to management", e);
         }

         topUpCreditIfNeeded();
      }

      @Override
      protected void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx) {
         try {
            if (logger.isTraceEnabled()) {
               logger.trace("AMQP Bridge {} address receiver {} dispatching incoming message: {}", bridgeManager.getName(), receiverInfo, message);
            }

            final Message theMessage = transformer.transform(message);

            if (theMessage != message && logger.isTraceEnabled()) {
               logger.trace("The transformer {} replaced the original message {} with a new instance {}",
                            transformer, message, theMessage);
            }

            sessionSPI.serverSend(this, tx, receiver, delivery, cachedAddress, getPreferredRoutingType(), routingContext, theMessage);
         } catch (Exception e) {
            logger.warn("Inbound delivery for {} encountered an error: {}", receiverInfo, e.getMessage(), e);
            deliveryFailed(delivery, receiver, e);
         } finally {
            recordMessageReceived(message);
         }
      }
   }
}
