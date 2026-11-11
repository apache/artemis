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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPSecurityException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the equivalent for the ServerProducer
 */
public class ProtonServerReceiverContext extends ProtonAbstractReceiver {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected SimpleString address;
   protected SimpleString lastAddress;
   protected AddressFullMessagePolicy lastAddressPolicy;
   protected boolean addressAlreadyClashed = false;
   protected RoutingType explicitRoutingType;
   protected RoutingType implicitRoutingType;

   public ProtonServerReceiverContext(AMQPSessionCallback sessionSPI,
                                      AMQPConnectionContext connection,
                                      AMQPSessionContext protonSession,
                                      Receiver receiver) {
      super(sessionSPI, connection, protonSession, receiver);
   }

   @Override
   public void initialize() throws Exception {
      initialized = true;

      org.apache.qpid.proton.amqp.messaging.Target target = (org.apache.qpid.proton.amqp.messaging.Target) receiver.getRemoteTarget();

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      receiver.setSenderSettleMode(receiver.getRemoteSenderSettleMode());

      // We don't currently support SECOND so enforce that the answer is always FIRST
      receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);

      if (target != null) {
         if (target.getDynamic()) {
            // if dynamic we have to create the node (queue) and set the address on the target, the node is temporary and
            // will be deleted on closing of the session
            address = SimpleString.of(sessionSPI.tempQueueName());

            explicitRoutingType = getExplicitRoutingType(target.getCapabilities(), address);
            implicitRoutingType = getImplicitRoutingType(address);

            try {
               if (getPreferredRoutingType() == RoutingType.ANYCAST) {
                  sessionSPI.createTemporaryQueue(address, RoutingType.ANYCAST);
               } else {
                  sessionSPI.createTemporaryAddress(address);
               }
            } catch (ActiveMQAMQPSecurityException e) {
               throw e;
            } catch (ActiveMQSecurityException e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingTempDestination(e.getMessage());
            } catch (Exception e) {
               throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
            }
            target.setAddress(address.toString());
         } else {
            // the target will have an address unless the remote is requesting an anonymous
            // relay in which case the address in the incoming message's to field will be
            // matched on receive of the message.
            String targetAddress = target.getAddress();
            if (targetAddress != null && !targetAddress.isEmpty()) {
               address = SimpleString.of(targetAddress);
            }

            if (address != null) {
               explicitRoutingType = getExplicitRoutingType(target.getCapabilities(), address);
               implicitRoutingType = getImplicitRoutingType(address);

               final RoutingType selectedRoutingType = explicitRoutingType != null ? explicitRoutingType : implicitRoutingType;

               try {
                  if (!sessionSPI.checkAddressAndAutocreateIfPossible(address, selectedRoutingType)) {
                     throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.addressDoesntExist(address.toString());
                  }
               } catch (ActiveMQAMQPNotFoundException e) {
                  throw e;
               } catch (Exception e) {
                  logger.debug(e.getMessage(), e);
                  throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
               }

               final SimpleString addressPart = CompositeAddress.extractAddressName(address);
               final SimpleString queuePart = CompositeAddress.isFullyQualified(address) ?
                  CompositeAddress.extractQueueName(address) : null;

               try {
                  sessionSPI.check(addressPart, queuePart, CheckType.SEND, connection.getSecurityAuth());
               } catch (ActiveMQSecurityException e) {
                  throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingProducer(e.getMessage());
               }
            } else {
               explicitRoutingType = getExplicitRoutingType(target.getCapabilities(), null);
            }
         }

         final Symbol[] remoteDesiredCapabilities = receiver.getRemoteDesiredCapabilities();

         if (remoteDesiredCapabilities != null) {
            final List<Symbol> offeredCapabilities = new ArrayList<>();

            final Symbol[] desiredCapabilities = remoteDesiredCapabilities;
            for (Symbol desired : desiredCapabilities) {
               if (AmqpSupport.DELAYED_DELIVERY.equals(desired)) {
                  offeredCapabilities.add(AmqpSupport.DELAYED_DELIVERY);
               } else if (AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT.equals(desired)) {
                  offeredCapabilities.add(AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT);
                  enableCoreTunneling();
               }
            }

            if (!offeredCapabilities.isEmpty()) {
               receiver.setOfferedCapabilities(offeredCapabilities.toArray(new Symbol[0]));
            }
         }
      }

      topUpCreditIfNeeded();
   }

   @Override
   protected SimpleString getAddressInUse() {
      return address != null ? address : lastAddress;
   }

   /**
    * {@return true if the context is an anonymous relay (no target address)}
    */
   public final boolean isAnonymousRelay() {
      return address == null;
   }

   /**
    * Return the explicit routing type if one was provided and if not then return the
    * implicit routing type as a fallback.
    *
    * @return the highest priority routing type this receiver selected.
    */
   public RoutingType getPreferredRoutingType() {
      return explicitRoutingType != null ? explicitRoutingType : implicitRoutingType;
   }

   /**
    * Returns the routing type the client defined for this receiver using capabilities set
    * in the link {@link Target} or an address prefix.
    *
    * @return the explicit routing type defined in the link target capabilities or via address prefix.
    */
   public RoutingType getExplicitRoutingType() {
      return explicitRoutingType;
   }

   /**
    * Returns the routing type of the actual link target (address or queue) if one could
    * be determined based on broker defined defaults.
    *
    * @return the implicit routing type based on the target address.
    */
   public RoutingType getImplicitRoutingType() {
      return implicitRoutingType;
   }

   @Override
   protected void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx) {
      try {
         if (sessionSPI == null) {
            return;
         }

         if (isAnonymousRelay()) {
            validateAddressOnAnonymousLink(message);
         }

         sessionSPI.serverSend(this, tx, receiver, delivery, address, deduceRoutingType(message), routingContext, message);
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         deliveryFailed(delivery, receiver, e);
      }
   }

   protected RoutingType deduceRoutingType(Message message) {
      RoutingType routingType = getExplicitRoutingType();

      if (routingType == null) {
         if (isAnonymousRelay()) {
            final SimpleString address = message.getAddressSimpleString();

            routingType = message.getRoutingType();

            if (routingType == null && address != null) {
               routingType = sessionSPI.getRoutingTypeFromPrefix(address, getImplicitRoutingType(address));
            }
         } else {
            final Object msgRoutingType = message.getAnnotation(SimpleString.of(AMQPMessageSupport.ROUTING_TYPE.toString()));

            // This can lead to message being dropped on a link that matches an address whose
            // routing type is MULTICAST but the messages get sent with the ANYCAST routing type
            // or vice versa but we supported this sort of thing and changing if results in changes
            // in behavior from past releases.
            if (msgRoutingType != null) {
               routingType = RoutingType.getTypeOrDefault(((Number) msgRoutingType).byteValue(), getImplicitRoutingType());
            } else {
               routingType = getImplicitRoutingType();
            }
         }
      }

      return routingType;
   }

   private void validateAddressOnAnonymousLink(Message message) throws Exception {
      SimpleString newAddress = message.getAddressSimpleString();
      if (newAddress != null && !newAddress.equals(lastAddress)) {
         AddressFullMessagePolicy currentPolicy = sessionSPI.getProtocolManager().getServer().getPagingManager().getPageStore(newAddress).getAddressFullMessagePolicy();
         if (lastAddressPolicy != null && lastAddressPolicy != currentPolicy) {
            if (!addressAlreadyClashed) {
               addressAlreadyClashed = true; // print the warning only once
               ActiveMQAMQPProtocolLogger.LOGGER.incompatibleAddressFullMessagePolicy(lastAddress.toString(), String.valueOf(lastAddressPolicy), newAddress.toString(), String.valueOf(currentPolicy));
            }

            logger.debug("AddressFullPolicy clash between {}/{} and {}/{}", lastAddress, lastAddressPolicy, newAddress, lastAddressPolicy);
         }
         lastAddress = message.getAddressSimpleString();
         lastAddressPolicy = currentPolicy;
      }
   }

   @Override
   public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
      super.close(remoteLinkClose);
      sessionSPI.removeProducer(receiver.getName());
      org.apache.qpid.proton.amqp.messaging.Target target = (org.apache.qpid.proton.amqp.messaging.Target) receiver.getRemoteTarget();
      if (target != null && target.getDynamic() && (target.getExpiryPolicy() == TerminusExpiryPolicy.LINK_DETACH || target.getExpiryPolicy() == TerminusExpiryPolicy.SESSION_END)) {
         try {
            sessionSPI.removeTemporaryQueue(SimpleString.of(target.getAddress()));
         } catch (Exception e) {
            // Ignored as the temporary resource will be auto removed later.
         }
         try {
            sessionSPI.removeTemporaryAddress(SimpleString.of(target.getAddress()));
         } catch (Exception e) {
            // Ignored as the temporary resource will be auto removed later.
         }
      }
   }

   protected final RoutingType getImplicitRoutingType(SimpleString address) {
      final AddressInfo addressInfo = sessionSPI.getAddress(address);
      /*
       * If we're dealing with an *existing* address that has just one routing-type simply use that.
       * This allows "bare" AMQP clients (which have no built-in routing semantics) to send messages
       * wherever they want in this case because the routing ambiguity is eliminated.
       */
      if (addressInfo != null) {
         if (addressInfo.getRoutingTypes().size() == 1) {
            return addressInfo.getRoutingType();
         } else {
            return null; // We don't assume which type the client wanted, we just route it.
         }
      } else {
         return Objects.requireNonNullElse(sessionSPI.getDefaultRoutingType(address), ActiveMQDefaultConfiguration.getDefaultRoutingType());
      }
   }

   protected final RoutingType getExplicitRoutingType(Symbol[] symbols, SimpleString address) {
      if (symbols != null) {
         for (Symbol symbol : symbols) {
            if (AmqpSupport.TEMP_TOPIC_CAPABILITY.equals(symbol) || AmqpSupport.TOPIC_CAPABILITY.equals(symbol)) {
               return RoutingType.MULTICAST;
            } else if (AmqpSupport.TEMP_QUEUE_CAPABILITY.equals(symbol) || AmqpSupport.QUEUE_CAPABILITY.equals(symbol)) {
               return RoutingType.ANYCAST;
            }
         }
      }

      return address != null ? sessionSPI.getRoutingTypeFromPrefix(address, null) : null;
   }
}
