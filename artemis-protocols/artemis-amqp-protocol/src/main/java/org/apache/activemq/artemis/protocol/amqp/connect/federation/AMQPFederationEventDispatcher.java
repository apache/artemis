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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederation.FEDERATION_INSTANCE_RECORD;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerAddressPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBindingPlugin;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.SenderController;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Terminus;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sender controller used to fire events from one side of an AMQP Federation connection to the other side.
 */
public class AMQPFederationEventDispatcher implements SenderController, ActiveMQServerBindingPlugin, ActiveMQServerAddressPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Sender sender;
   private final AMQPFederation federation;
   private final AMQPSessionCallback session;
   private final ActiveMQServer server;

   private final Set<String> addressWatches = new HashSet<>();
   private final Set<String> queueWatches = new HashSet<>();

   private String eventsAddress;

   public AMQPFederationEventDispatcher(AMQPFederation federation, AMQPSessionCallback session, Sender sender) {
      this.session = session;
      this.sender = sender;
      this.federation = federation;
      this.server = federation.getServer();
   }

   private String getEventsLinkAddress() {
      return eventsAddress;
   }

   /**
    * Raw event send API that accepts an {@link AMQPMessage} instance and routes it using the server post office
    * instance.
    *
    * @param event The event message to send to the previously created control address.
    * @throws Exception if an error occurs during the message send.
    */
   public void sendEvent(AMQPMessage event) throws Exception {
      Objects.requireNonNull(event, "Null event message is not expected and constitutes an error condition");

      event.setAddress(getEventsLinkAddress());

      server.getPostOffice().route(event, true);
   }

   @Override
   public Consumer init(ProtonServerSenderContext senderContext) throws Exception {
      final Connection protonConnection = senderContext.getSender().getSession().getConnection();
      final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();
      final AMQPFederation federation = attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class);

      if (federation == null) {
         throw new ActiveMQAMQPIllegalStateException("Cannot create a federation link from non-federation connection");
      }

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      sender.setSenderSettleMode(sender.getRemoteSenderSettleMode());

      // We don't currently support SECOND so enforce that the answer is always FIRST
      sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);

      // Create a temporary queue using the unique link name which is where events will be sent to so that they can be
      // held until credit is granted by the remote.
      eventsAddress = federation.prefixEventsLinkQueueName(sender.getName());

      if (sender.getLocalState() != EndpointState.ACTIVE) {
         // Indicate that event link capabilities is supported.
         sender.setOfferedCapabilities(new Symbol[]{FEDERATION_EVENT_LINK});

         // When the federation source creates a events receiver link to receive events from the federation target side
         // we land here on the target as this end should not be active yet, the federation source should request a
         // dynamic source node to be created and we should return the address when opening this end.
         final Terminus remoteTerminus = (Terminus) sender.getRemoteSource();

         if (remoteTerminus == null || !remoteTerminus.getDynamic()) {
            throw new ActiveMQAMQPInternalErrorException("Remote Terminus did not arrive as dynamic node: " + remoteTerminus);
         }

         remoteTerminus.setAddress(getEventsLinkAddress());
      }

      try {
         session.createTemporaryQueue(SimpleString.of(getEventsLinkAddress()), RoutingType.ANYCAST, 1, true);
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingTemporaryQueue(e.getMessage());
      }

      // Attach to the federation instance now that we have a queue to put events onto.
      federation.registerEventSender(this);

      server.registerBrokerPlugin(this); // Start listening for bindings and consumer events.

      return session.createSender(senderContext, SimpleString.of(getEventsLinkAddress()), null, false);
   }

   @Override
   public void close(boolean remoteClose) {
      // Make a best effort to remove the temporary queue used for event messages on close.
      server.unRegisterBrokerPlugin(this);

      try {
         session.removeTemporaryQueue(SimpleString.of(getEventsLinkAddress()));
      } catch (Exception e) {
         // Ignored as the temporary queue should be removed on connection termination.
      }
   }

   @Override
   public void close(ErrorCondition error) {
      // Ensure cleanup on force close using default close API
      close(false);
   }

   /**
    * Add the given address name to the set of addresses that should be watched for and if added to the broker send an
    * event to the remote indicating that it now exists and the remote should attempt to create a new address federation
    * consumer.
    * <p>
    * This method must be called from the connection thread.
    *
    * @param addressName The address name to watch for addition.
    */
   public void addAddressWatch(String addressName) {
      addressWatches.add(addressName);
   }

   /**
    * Add the given queue name to the set of queues that should be watched for and if added to the broker send an event
    * to the remote indicating that it now exists and the remote should attempt to create a new queue federation
    * consumer.
    * <p>
    * This method must be called from the connection thread.
    *
    * @param queueName The queue name to watch for addition.
    */
   public void addQueueWatch(String queueName) {
      queueWatches.add(queueName);
   }

   @Override
   public void afterAddAddress(AddressInfo addressInfo, boolean reload) throws ActiveMQException {
      final String addressName = addressInfo.getName().toString();

      // Run this on the connection thread so that rejection of a federation consumer and addition of the address can't
      // race such that the consumer adds its intent concurrently with the address having been added and we miss the
      // registration.
      federation.getConnectionContext().runLater(() -> {
         if (addressWatches.remove(addressName)) {
            try {
               sendEvent(AMQPFederationEventSupport.encodeAddressAddedEvent(addressName));
            } catch (Exception e) {
               logger.warn("error on send of address added event: {}", e.getMessage());
               federation.signalError(
                  new ActiveMQAMQPInternalErrorException("Error while processing address added: " + e.getMessage()));
            }
         }
      });
   }

   @Override
   public void afterAddBinding(Binding binding) throws ActiveMQException {
      if (binding instanceof QueueBinding queueBinding) {
         final String addressName = queueBinding.getAddress().toString();
         final String queueName = queueBinding.getQueue().getName().toString();

         // Run this on the connection thread so that rejection of a federation consumer and addition of the binding
         // can't race such that the consumer adds its intent concurrently with the binding having been added and we
         // miss the registration.
         federation.getConnectionContext().runLater(() -> {
            if (queueWatches.remove(queueName)) {
               try {
                  sendEvent(AMQPFederationEventSupport.encodeQueueAddedEvent(addressName, queueName));
               } catch (Exception e) {
                  // Likely the connection failed if we get here.
                  logger.warn("Error on send of queue added event: {}", e.getMessage());
                  federation.signalError(
                     new ActiveMQAMQPInternalErrorException("Error while processing queue added: " + e.getMessage()));
               }
            }
         });
      }
   }
}
