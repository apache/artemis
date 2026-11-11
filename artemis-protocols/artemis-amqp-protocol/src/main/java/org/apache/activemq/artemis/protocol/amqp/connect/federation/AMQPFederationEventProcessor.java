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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.EVENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_EVENT_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_ADDED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_QUEUE_ADDED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_QUEUE_NAME;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageBrokerAccessor;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonAbstractReceiver;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Terminus;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A specialized AMQP Receiver that handles events from a remote Federation connection such as addition of addresses or
 * queues where federation was requested but they did not exist at the time and the federation consumer was rejected.
 */
public class AMQPFederationEventProcessor extends ProtonAbstractReceiver {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int PROCESSOR_RECEIVER_CREDITS = 10;
   private static final int PROCESSOR_RECEIVER_CREDITS_LOW = 3;

   private final ActiveMQServer server;
   private final AMQPFederation federation;

   /**
    * Create the new federation event receiver
    *
    * @param federation The AMQP Federation instance that this event consumer resides in.
    * @param session    The associated session for this federation event consumer.
    * @param receiver   The proton {@link Receiver} that this event consumer reads from.
    */
   public AMQPFederationEventProcessor(AMQPFederation federation, AMQPSessionContext session, Receiver receiver) {
      super(session.getSessionSPI(), session.getAMQPConnectionContext(), session, receiver);

      this.server = protonSession.getServer();
      this.federation = federation;
   }

   @Override
   public void initialize() throws Exception {
      initialized = true;

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      receiver.setSenderSettleMode(receiver.getRemoteSenderSettleMode());

      // We don't currently support SECOND so enforce that the answer is always FIRST
      receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);

      if (receiver.getLocalState() != EndpointState.ACTIVE) {
         // Indicate that event link capabilities is supported.
         receiver.setOfferedCapabilities(new Symbol[]{FEDERATION_EVENT_LINK});

         // When the federation source creates a events sender link to send events to the federation target side we land
         // here on the target as this end should not be active yet, the federation source should request a dynamic
         // target node to be created and we should return the address when opening this end.
         final Terminus remoteTerminus = (Terminus) receiver.getRemoteTarget();

         if (remoteTerminus == null || !remoteTerminus.getDynamic()) {
            throw new ActiveMQAMQPInternalErrorException("Remote Terminus did not arrive as dynamic node: " + remoteTerminus);
         }

         remoteTerminus.setAddress(receiver.getName());
      }

      // Inform the federation that there is an event processor in play.
      federation.registerEventReceiver(this);

      topUpCreditIfNeeded();
   }

   @Override
   protected void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx) {
      logger.trace("{}::actualdelivery called for {}", server, message);

      final AMQPMessage eventMessage = (AMQPMessage) message;

      delivery.setContext(message);

      try {
         final Object eventType = AMQPMessageBrokerAccessor.getMessageAnnotationProperty(eventMessage, EVENT_TYPE);

         if (REQUESTED_QUEUE_ADDED.equals(eventType)) {
            final Map<String, Object> eventData = AMQPFederationEventSupport.decodeQueueAddedEvent(eventMessage);

            final String addressName = eventData.get(REQUESTED_ADDRESS_NAME).toString();
            final String queueName = eventData.get(REQUESTED_QUEUE_NAME).toString();

            logger.trace("Remote event indicates Queue added that matched a previous request [{}::{}]", addressName, queueName);

            federation.processRemoteQueueAdded(addressName, queueName);
         } else if (REQUESTED_ADDRESS_ADDED.equals(eventType)) {
            final Map<String, Object> eventData = AMQPFederationEventSupport.decodeAddressAddedEvent(eventMessage);

            final String addressName = eventData.get(REQUESTED_ADDRESS_NAME).toString();

            logger.trace("Remote event indicates Address added that matched a previous request [{}]", addressName);

            federation.processRemoteAddressAdded(addressName);
         } else {
            federation.signalError(new ActiveMQAMQPInternalErrorException("Remote sent unknown event."));
            return;
         }

         delivery.disposition(Accepted.getInstance());
         delivery.settle();

         topUpCreditIfNeeded();

         connection.flush();
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         federation.signalError(
            new ActiveMQAMQPInternalErrorException("Error while processing incoming event message: " + e.getMessage()));
      }
   }

   @Override
   protected Runnable createCreditRunnable(AMQPConnectionContext connection) {
      // The events processor is not bound to the configurable credit on the connection as it could be set to zero if
      // trying to create pull federation consumers so we avoid any chance of that happening as otherwise there would be
      // no credit granted for the remote to send us events.
      return createCreditRunnable(PROCESSOR_RECEIVER_CREDITS, PROCESSOR_RECEIVER_CREDITS_LOW, receiver, connection, this);
   }

   @Override
   protected void doCreditTopUpRun() {
      creditRunnable.run();
   }

   @Override
   protected SimpleString getAddressInUse() {
      return SimpleString.of(receiver.getName());
   }
}
