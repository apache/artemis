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

import java.util.Objects;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.SenderController;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Sender;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederation.FEDERATION_INSTANCE_RECORD;

/**
 * A {@link SenderController} implementation used by the AMQP federation control link to encode and send federation
 * policies or other commands to the remote side of the AMQP federation instance.
 */
public class AMQPFederationCommandDispatcher implements SenderController {

   private final Sender sender;
   private final AMQPSessionCallback session;
   private final ActiveMQServer server;

   private String controlAddress;

   AMQPFederationCommandDispatcher(Sender sender, ActiveMQServer server, AMQPSessionCallback session) {
      this.session = session;
      this.sender = sender;
      this.server = server;
   }

   /**
    * Sends the given {@link FederationReceiveFromQueuePolicy} instance using the control link which should instruct the
    * remote to begin federation operations back to this peer for matching remote queues with demand.
    *
    * @param policy The policy to encode and send over the federation control link.
    * @throws Exception if an error occurs during the control and send operation.
    */
   public void sendPolicy(FederationReceiveFromQueuePolicy policy) throws Exception {
      Objects.requireNonNull(policy, "Cannot encode and send a null policy instance.");

      final AMQPMessage command =
         AMQPFederationPolicySupport.encodeQueuePolicyControlMessage(policy);

      sendCommand(command);
   }

   /**
    * Sends the given {@link FederationReceiveFromAddressPolicy} instance using the control link which should instruct
    * the remote to begin federation operations back to this peer for matching remote address.
    *
    * @param policy The policy to encode and send over the federation control link.
    * @throws Exception if an error occurs during the control and send operation.
    */
   public void sendPolicy(FederationReceiveFromAddressPolicy policy) throws Exception {
      Objects.requireNonNull(policy, "Cannot encode and send a null policy instance.");

      final AMQPMessage command =
         AMQPFederationPolicySupport.encodeAddressPolicyControlMessage(policy);

      sendCommand(command);
   }

   /**
    * Raw send command that accepts and {@link AMQPMessage} instance and routes it using the server post office
    * instance.
    *
    * @param command The command message to send to the previously created control address.
    * @throws Exception if an error occurs during the message send.
    */
   public void sendCommand(AMQPMessage command) throws Exception {
      Objects.requireNonNull(command, "Null command message is not expected and constitutes an error condition");

      command.setAddress(getControlLinkAddress());

      server.getPostOffice().route(command, true);
   }

   @Override
   public Consumer init(ProtonServerSenderContext senderContext) throws Exception {
      final Connection protonConnection = senderContext.getSender().getSession().getConnection();
      final org.apache.qpid.proton.engine.Record attachments = protonConnection.attachments();
      final AMQPFederation federation = attachments.get(FEDERATION_INSTANCE_RECORD, AMQPFederation.class);

      if (federation == null) {
         throw new ActiveMQAMQPIllegalStateException("Cannot create a federation link from non-federation connection");
      }

      // Get the dynamically generated name to use for local creation of a matching temporary
      // queue that we will send control message to and the broker will dispatch as remote
      // credit is made available.
      controlAddress = federation.prefixControlLinkQueueName(sender.getRemoteTarget().getAddress());

      try {
         session.createTemporaryQueue(SimpleString.of(getControlLinkAddress()), RoutingType.ANYCAST, 1, true);
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingTemporaryQueue(e.getMessage());
      }

      return session.createSender(senderContext, SimpleString.of(getControlLinkAddress()), null, false);
   }

   @Override
   public void close(boolean remoteClose) throws Exception {
      // Make a best effort to remove the temporary queue used for control commands on close.

      try {
         session.removeTemporaryQueue(SimpleString.of(getControlLinkAddress()));
      } catch (Exception e) {
         // Ignored as the temporary queue should be removed on connection termination.
      }
   }

   private String getControlLinkAddress() {
      return controlAddress;
   }
}