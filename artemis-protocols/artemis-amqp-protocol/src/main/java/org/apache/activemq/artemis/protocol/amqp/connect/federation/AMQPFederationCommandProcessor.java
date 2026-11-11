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
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageBrokerAccessor;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonAbstractReceiver;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.OPERATION_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_QUEUE_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_CONTROL_LINK;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V2;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_VERSION;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_ADDRESS_POLICY;

/**
 * A specialized AMQP Receiver that handles commands from a remote Federation connection such as handling incoming
 * policies that should be applied to local addresses and queues.
 */
public class AMQPFederationCommandProcessor extends ProtonAbstractReceiver {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Capabilities that are offered to the remote sender that indicate this receiver supports the
   // control link functions and any other federation specific feature that are offered to the remote.
   private static final Symbol[] OFFERED_LINK_CAPABILITIES = new Symbol[] {FEDERATION_CONTROL_LINK};

   private static final int PROCESSOR_RECEIVER_CREDITS = 10;
   private static final int PROCESSOR_RECEIVER_CREDITS_LOW = 3;

   private final ActiveMQServer server;
   private final AMQPFederationTarget federation;

   /**
    * Create the new federation command receiver
    *
    * @param federation The AMQP Federation instance that this command consumer resides in.
    * @param session    The associated session for this federation command consumer.
    * @param receiver   The proton {@link Receiver} that this command consumer reads from.
    */
   public AMQPFederationCommandProcessor(AMQPFederationTarget federation, AMQPSessionContext session, Receiver receiver) {
      super(session.getSessionSPI(), session.getAMQPConnectionContext(), session, receiver);

      this.server = protonSession.getServer();
      this.federation = federation;
   }

   @Override
   public void initialize() throws Exception {
      initialized = true;

      // For any incoming control link we should gate keep and allow configuration to
      // prevent any user from creating federation connections.

      final Target target = (Target) receiver.getRemoteTarget();

      if (target == null || !target.getDynamic()) {
         throw new ActiveMQAMQPInternalErrorException("Remote Target did not arrive as dynamic node: " + target);
      }

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      receiver.setSenderSettleMode(receiver.getRemoteSenderSettleMode());

      // We don't currently support SECOND so enforce that the answer is always FIRST
      receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);

      // Send the remote the version of AMQP Federation this target broker implements so it
      // can match features with us.
      final Map<Symbol, Object> receiverProperties = new HashMap<>();
      receiverProperties.put(FEDERATION_VERSION, FEDERATION_V2);

      receiver.setProperties(receiverProperties);

      // The target needs a unique address for the remote to send commands to which will get
      // deleted on connection close so no state is retained between connections, we know our
      // link name is unique and carries the federation name that created it so we reuse that
      // as the address for the dynamic node.
      target.setAddress(receiver.getName());

      // Send back offered capabilities to advertise this is a control link command processor.
      receiver.setOfferedCapabilities(OFFERED_LINK_CAPABILITIES);

      // Once we have configured this end of the control link we must initialize the federation capabilities
      federation.getCapabilities().initialize(receiver);

      topUpCreditIfNeeded();
   }

   @Override
   protected void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx) {
      logger.trace("{}::actualdelivery called for {}", server, message);

      final AMQPMessage controlMessage = (AMQPMessage) message;

      delivery.setContext(message);

      try {
         final Object eventType = AMQPMessageBrokerAccessor.getMessageAnnotationProperty(controlMessage, OPERATION_TYPE);

         if (ADD_QUEUE_POLICY.equals(eventType)) {
            final FederationReceiveFromQueuePolicy policy =
               AMQPFederationPolicySupport.decodeReceiveFromQueuePolicy(controlMessage, federation.getWildcardConfiguration());

            federation.addQueueMatchPolicy(policy);
         } else if (ADD_ADDRESS_POLICY.equals(eventType)) {
            final FederationReceiveFromAddressPolicy policy =
               AMQPFederationPolicySupport.decodeReceiveFromAddressPolicy(controlMessage, federation.getWildcardConfiguration());

            federation.addAddressMatchPolicy(policy);
         } else {
            federation.signalError(new ActiveMQAMQPInternalErrorException("Remote sent unknown command."));
            return;
         }

         delivery.disposition(Accepted.getInstance());
         delivery.settle();

         topUpCreditIfNeeded();

         connection.flush();
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         federation.signalError(
            new ActiveMQAMQPInternalErrorException("Error while processing incoming control message: " + e.getMessage()));
      }
   }

   @Override
   protected Runnable createCreditRunnable(AMQPConnectionContext connection) {
      // The command processor is not bound to the configurable credit on the connection as it could be set
      // to zero if trying to create pull federation consumers so we avoid any chance of that happening as
      // otherwise there would be no credit granted for the remote to send us commands..
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
