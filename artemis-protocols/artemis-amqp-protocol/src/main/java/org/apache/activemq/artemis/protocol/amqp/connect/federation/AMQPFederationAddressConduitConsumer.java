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

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationMetrics.ConsumerMetrics;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;

/**
 * Consumer implementation for Federated Addresses that receives from a remote AMQP peer and then routes the messages to
 * the target address for dispatch to all attached bindings. The consumer serves as a conduit for message sent to the
 * remote address passing across the federation link and then being sent to the local address to be routed to all the
 * current bindings on that address, any messages that are not consumed by local bindings (due to filters) are discarded.
 */
public final class AMQPFederationAddressConduitConsumer extends AMQPFederationAddressConsumer {

   public AMQPFederationAddressConduitConsumer(AMQPFederationAddressPolicyManager manager,
                                               AMQPFederationConsumerConfiguration configuration,
                                               AMQPSessionContext session, FederationConsumerInfo consumerInfo,
                                               ConsumerMetrics metrics) {
      super(manager, configuration, session, consumerInfo, metrics);
   }

   @Override
   protected AMQPFederatedAddressDeliveryHandler createDeliveryHandler(Receiver receiver) {
      return new AMQPFederatedAddressConduitDeliveryHandler(session, consumerInfo, receiver);
   }

   /**
    * Wrapper around the standard receiver context that provides federation specific entry points and customizes inbound
    * delivery handling for this Address receiver.
    */
   private class AMQPFederatedAddressConduitDeliveryHandler extends AMQPFederatedAddressDeliveryHandler {

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
      AMQPFederatedAddressConduitDeliveryHandler(AMQPSessionContext session, FederationConsumerInfo consumerInfo, Receiver receiver) {
         super(session, consumerInfo, receiver);
      }

      @Override
      protected void routeFederatedMessage(Message message, Delivery delivery, Receiver receiver, Transaction tx) throws Exception {
         sessionSPI.serverSend(this, tx, receiver, delivery, cachedAddress, getPreferredRoutingType(), routingContext, message);
      }
   }
}
