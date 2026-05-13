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
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeMetrics.SenderMetrics;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sender type that handles sending messages sent to a local address to a remote AMQP peer.
 */
public class AMQPBridgeToAddressSender extends AMQPBridgeSender {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public AMQPBridgeToAddressSender(AMQPBridgeToPolicyManager policyManager,
                                    AMQPBridgeSenderConfiguration configuration,
                                    AMQPSessionContext session,
                                    AMQPBridgeSenderInfo senderInfo,
                                    SenderMetrics metrics) {
      super(policyManager, configuration, session, senderInfo, metrics);
   }

   @Override
   public AMQPBridgeToAddressPolicyManager getPolicyManager() {
      return (AMQPBridgeToAddressPolicyManager) policyManager;
   }

   @Override
   public AMQPBridgeAddressPolicy getPolicy() {
      return (AMQPBridgeAddressPolicy) policy;
   }

   @Override
   protected void doCreateSender() {
      try {
         final Sender protonSender = session.getSession().sender(generateLinkName());
         final Target target = new Target();
         final Source source = new Source();
         final String address = senderInfo.getRemoteAddress();

         source.setAddress(senderInfo.getLocalAddress());

         target.setAddress(address);
         target.setDurable(TerminusDurability.NONE);
         target.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
         target.setCapabilities(getRemoteTerminusCapabilities());

         protonSender.setSenderSettleMode(configuration.isUsingPresettledSenders() ? SenderSettleMode.SETTLED : SenderSettleMode.UNSETTLED);
         protonSender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
         // If enabled indicate that core tunneling is desired which we prefer to AMQP conversions of core as
         // the large ones will be converted to standard AMQP messages in memory. If the remote does not offer
         // the capability in return we cannot use core tunneling which we will check when the remote attach
         // response arrives and we complete the link attach.
         if (configuration.isCoreMessageTunnelingEnabled()) {
            protonSender.setDesiredCapabilities(new Symbol[] {AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT});
         }
         protonSender.setTarget(target);
         protonSender.setSource(source);
         protonSender.open();

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

         this.protonSender = protonSender;

         protonSender.attachments().set(AMQP_LINK_INITIALIZER_KEY, Runnable.class, () -> {
            try {
               if (openTimeoutTask != null) {
                  openTimeoutTask.cancel(false);
               }

               if (openTimedOut.get()) {
                  return;
               }

               final boolean linkOpened = protonSender.getRemoteTarget() != null;

               if (linkOpened) {
                  logger.debug("AMQP Bridge {} address senderContext {} completed open", bridgeManager.getName(), senderInfo);
               } else {
                  logger.debug("AMQP Bridge {} address senderContext {} rejected by remote", bridgeManager.getName(), senderInfo);
               }

               // Intercept remote close and check for valid reasons for remote closure such as
               // the remote peer not having a matching node for this subscription or from an
               // operator manually closing the link etc.
               bridgeManager.addLinkClosedInterceptor(senderInfo.getId(), this::remoteLinkClosedInterceptor);

               final AMQPBridgeToAddressSenderController senderController =
                  new AMQPBridgeToAddressSenderController(senderInfo, configuration, getPolicyManager(), session, metrics);

               senderContext = new AMQPBridgeAddressSenderContext(
                  connection, protonSender, session, session.getSessionSPI(), senderController);

               session.addSender(protonSender, senderContext);

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
             "-address-sender-" + senderInfo.getRemoteAddress() +
             "-" + bridgeManager.getServer().getNodeID() +
             "-" + LINK_SEQUENCE_ID.incrementAndGet();
   }

   private class AMQPBridgeAddressSenderContext extends ProtonServerSenderContext {

      AMQPBridgeAddressSenderContext(AMQPConnectionContext connection, Sender sender,
                                     AMQPSessionContext protonSession, AMQPSessionCallback server,
                                     AMQPBridgeToAddressSenderController senderController) {
         super(connection, sender, protonSession, server, senderController);
      }

      @Override
      public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
         super.close(remoteLinkClose);

         if (remoteLinkClose && remoteCloseHandler != null) {
            try {
               remoteCloseHandler.accept(AMQPBridgeToAddressSender.this);
            } catch (Exception e) {
               logger.debug("User remote closed handler threw error: ", e);
            } finally {
               remoteCloseHandler = null;
            }
         }
      }
   }

   public static class AMQPBridgeToAddressSenderController extends AMQPBridgeToSenderController {

      private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

      public AMQPBridgeToAddressSenderController(AMQPBridgeSenderInfo senderInfo, AMQPBridgeSenderConfiguration configuration, AMQPBridgeToPolicyManager policyManager, AMQPSessionContext session, SenderMetrics metrics) throws ActiveMQAMQPException {
         super(senderInfo, configuration, policyManager, session, metrics);
      }

      @Override
      public SenderRole getRole() {
         return SenderRole.ADDRESS_SENDER;
      }

      public AMQPBridgeAddressPolicy getPolicy() {
         return (AMQPBridgeAddressPolicy) policy;
      }

      @Override
      protected void handleLinkRemotelyClosed() {
         tryDeleteTemporarySubscriptionQueue();
      }

      @Override
      protected void handleLinkLocallyClosed(ErrorCondition error) {
         tryDeleteTemporarySubscriptionQueue();
      }

      private void tryDeleteTemporarySubscriptionQueue() {
         if (getPolicy().isUseDurableSubscriptions()) {
            return;
         }

         final AMQPSessionCallback sessionSPI = session.getSessionSPI();
         final SimpleString queueName = SimpleString.of(senderInfo.getLocalQueue());

         try {
            final QueueQueryResult queueQuery = sessionSPI.queueQuery(queueName, RoutingType.MULTICAST, false);

            if (queueQuery.isExists()) {
               sessionSPI.deleteQueue(queueName);
            }
         } catch (Exception e) {
            // Ignore as temporary destinations will be cleaned up automatically later.
         }
      }

      @Override
      protected ServerConsumer createServerConsumer(ProtonServerSenderContext senderContext) throws Exception {
         final AMQPSessionCallback sessionSPI = session.getSessionSPI();
         final SimpleString address = SimpleString.of(senderInfo.getLocalAddress());
         final SimpleString queue = SimpleString.of(senderInfo.getLocalQueue());
         final RoutingType routingType = senderInfo.getRoutingType();

         try {
            final AddressQueryResult result = sessionSPI.addressQuery(address, routingType, false);

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

         if (getPolicy().isUseDurableSubscriptions()) {
            // Recover or create the queue we use to reflect the messages sent to the address to the remote
            QueueQueryResult queueQuery = sessionSPI.queueQuery(queue, routingType, false);

            if (!queueQuery.isExists()) {
               final QueueConfiguration configuration = QueueConfiguration.of(queue);

               configuration.setAddress(address);
               configuration.setRoutingType(routingType);
               configuration.setAutoCreateAddress(false);
               configuration.setMaxConsumers(1);
               configuration.setPurgeOnNoConsumers(false);
               configuration.setFilterString(policy.getFilter());
               configuration.setDurable(true);
               configuration.setAutoCreated(false);
               configuration.setAutoDelete(configuration.isAutoDelete());
               configuration.setAutoDeleteMessageCount(configuration.getAutoDeleteMessageCount());
               configuration.setAutoDeleteDelay(configuration.getAutoDeleteDelay());

               // Try and create it and then later we will validate fully that it matches our expectations
               // since we could lose a race with some other resource creating its own resources, although
               // we should have created a unique queue name that should prevent any overlaps.
               queueQuery = sessionSPI.queueQuery(configuration, true);
            }

            if (!queueQuery.getAddress().equals(address)) {
               throw new ActiveMQAMQPIllegalStateException(
                  "Requested queue: " + queue + " for bridge to address: " + address +
                  ", but it is already mapped to a different address: " + queueQuery.getAddress());
            }
         } else {
            try {
               sessionSPI.createTemporaryQueue(address, queue, routingType, SimpleString.of(getPolicy().getFilter()), 1, true);
            } catch (Exception e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingTemporaryQueue(e.getMessage());
            }
         }

         return sessionSPI.createSender(senderContext, queue, null, false, policy.getPriority());
      }
   }
}
