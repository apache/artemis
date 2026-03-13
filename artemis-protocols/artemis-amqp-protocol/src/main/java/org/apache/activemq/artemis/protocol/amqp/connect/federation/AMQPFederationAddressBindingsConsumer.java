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

package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationMetrics.ConsumerMetrics;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer implementation for Federated Addresses that receives from a remote AMQP peer and forwards those messages
 * onto the internal bindings on a given address that matched criteria of the given {@link FederationConsumerInfo}.
 * The consumer is subscribed to the remote address likely using a filter that matches the filter of one or more
 * local bindings on an address which defines the local demand. Once a message is sent from the remote this consumer
 * routes that message to each of the local consumers that triggered the federation link.
 * <p>
 * The goal of this federation consumer implementation is to allow consumption of a more limited set of messages
 * from a remote address by applying filters for local address consumers and routing the resulting messages directly
 * to them. The possible down side here is that if the filters being used aren't fine grained enough that consumers
 * with differing filters might still have overlaps in the messages they receive which would then produce more message
 * traffic across the federation than would be generated if using the standard conduit consumer pattern (this can also
 * happen if filtered and non-filtered consumers are mixed on the local address).
 */
public final class AMQPFederationAddressBindingsConsumer extends AMQPFederationAddressConsumer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Set<Binding> bindings = new ConcurrentHashSet<>();
   private final PostOffice postOffice;
   private final StorageManager storageManager;

   public AMQPFederationAddressBindingsConsumer(AMQPFederationAddressPolicyManager manager,
                                                AMQPFederationConsumerConfiguration configuration,
                                                AMQPSessionContext session, FederationConsumerInfo consumerInfo,
                                                ConsumerMetrics metrics) {
      super(manager, configuration, session, consumerInfo, metrics);

      this.postOffice = manager.getFederation().getServer().getPostOffice();
      this.storageManager = federation.getServer().getStorageManager();
   }

   @Override
   protected AMQPFederatedAddressDeliveryHandler createDeliveryHandler(Receiver receiver) {
      return new AMQPFederatedAddressBindingsDeliveryReceiver(session, consumerInfo, receiver);
   }

   /**
    * Adds a collection of {@link Binding} instances that should have any received messages routed
    * to them as they arrive from the remote.
    *
    * @param currentBindings
    *    The bindings that should receive any messages sent from the remote
    */
   public void addBindings(Collection<Binding> currentBindings) {
      bindings.addAll(currentBindings);
   }

   /**
    * Adds a {@link Binding} to the set of bindings that should receive messages from the remote
    * to the collection of tracked bindings.
    *
    * @param binding
    *    The {@link Binding} to add to the tracked collection.
    */
   public void addBinding(Binding binding) {
      bindings.add(binding);
   }

   /**
    * Removes a {@link Binding} from the set of bindings that should receive messages from the remote
    * to the collection of tracked bindings.
    *
    * @param binding
    *    The {@link Binding} to remove from the tracked collection.
    */
   public void removeBinding(Binding binding) {
      bindings.remove(binding);
   }

   /**
    * Wrapper around the standard receiver context that provides federation specific entry points and customizes inbound
    * delivery handling for this Address receiver.
    */
   private class AMQPFederatedAddressBindingsDeliveryReceiver extends AMQPFederatedAddressDeliveryHandler {

      /**
       * Creates the federation receiver instance.
       *
       * @param session
       *    The server session context bound to the receiver instance.
       * @param consumerInfo
       *    The {@link FederationConsumerInfo} that defines the remote consumer link.
       * @param receiver
       *    The proton receiver that will be wrapped in this server context instance.
       */
      AMQPFederatedAddressBindingsDeliveryReceiver(AMQPSessionContext session, FederationConsumerInfo consumerInfo, Receiver receiver) {
         super(session, consumerInfo, receiver);
      }

      @Override
      protected void routeFederatedMessage(Message message, Delivery delivery, Receiver receiver, Transaction tx) {
         incrementSettle();

         final Collection<Binding> targets = new ArrayList<>(bindings);

         // In case bindings are going away and we receive a message before the consumer is closed
         // due to lack of demand we don't want to try and route the message.
         if (targets.isEmpty()) {
            acceptUnroutableMessage(message, delivery);
            return;
         }

         delivery.setContext(message);

         message.setAddress(consumerInfo.getTargetAddress());
         message.setConnectionID(receiver.getSession().getConnection().getRemoteContainer());
         if (message.getMessageID() <= 0) {
            message.setMessageID(storageManager.generateID());
         }

         final OperationContext oldContext = recoverContext();

         try {
            routingContext.clear();

            logger.trace("Address federation consumer routing incoming message to {} bindings on address: {}", bindings.size(), cachedAddress);

            for (Binding binding : targets) {
               binding.route(message, routingContext);
            }

            // Process route will throw in some cases such as the address being full which
            // triggers a disposition of Rejected being sent back to the federation sender.
            postOffice.processRoute(message, routingContext, false);

            storageManager.afterCompleteOperations(new IOCallback() {

               @Override
               public void done() {
                  connection.runNow(() -> {
                     delivery.disposition(Accepted.getInstance());
                     settle(delivery);
                     connection.flush();
                  });
               }

               @Override
               public void onError(int errorCode, String errorMessage) {
                  logger.warn("Address federation bindings consumer error after IO completion{}-{}", errorCode, errorMessage);
               }
            });
         } catch (Exception e) {
            logger.warn("Address federation failed routing incoming message: {}", e.getMessage(), e);
            deliveryFailed(delivery, receiver, e);
            connection.flush();
         } finally {
            OperationContextImpl.setContext(oldContext);
         }
      }

      private void acceptUnroutableMessage(Message message, Delivery delivery) {
         delivery.disposition(Accepted.getInstance());
         settle(delivery);
         connection.flush();
         if (message.isLargeMessage()) {
            try {
               ((LargeServerMessage) message).deleteFile();
            } catch (Exception e) {
               logger.debug("Error during delete of large message file when no bindings remain on federation consumer:", e);
            }
         }
         return;
      }
   }
}
