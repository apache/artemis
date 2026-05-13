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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_QUEUE_RECEIVER;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationMetrics.ProducerMetrics;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.SenderController;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Sender;

/**
 * {@link SenderController} used when an AMQP federation Queue receiver is created and this side of the connection needs
 * to create a matching sender. The attach of the sender should only succeed if there is a local matching queue,
 * otherwise the link should be closed with an error indicating that the matching resource is not present on this peer.
 */
public final class AMQPFederationQueueSenderController extends AMQPFederationSenderController {

   public AMQPFederationQueueSenderController(AMQPFederationRemoteQueuePolicyManager manager, ProducerMetrics metrics, Consumer<AMQPFederationSenderController> closedListener) throws ActiveMQAMQPException {
      super(manager, metrics, closedListener);
   }

   @Override
   public Role getRole() {
      return Role.QUEUE_PRODUCER;
   }

   @SuppressWarnings("unchecked")
   @Override
   public ServerConsumer createServerConsumer(ProtonServerSenderContext senderContext) throws Exception {
      final Sender sender = senderContext.getSender();
      final Source source = (Source) sender.getRemoteSource();
      final String selector;

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      sender.setSenderSettleMode(sender.getRemoteSenderSettleMode());
      // We don't currently support SECOND so enforce that the answer is always FIRST
      sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
      // We need to offer back that we support federation for the remote to complete the attach
      sender.setOfferedCapabilities(new Symbol[] {FEDERATION_QUEUE_RECEIVER});
      // We indicate desired to meet specification that we cannot use a capability unless we
      // indicated it was desired, however unless offered by the remote we cannot use it.
      sender.setDesiredCapabilities(new Symbol[] {AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT});

      final RoutingType routingType = getRoutingType(source);
      final SimpleString targetAddress;
      final SimpleString targetQueue;

      if (CompositeAddress.isFullyQualified(source.getAddress())) {
         targetAddress = SimpleString.of(CompositeAddress.extractAddressName(source.getAddress()));
         targetQueue = SimpleString.of(CompositeAddress.extractQueueName(source.getAddress()));
      } else {
         targetAddress = null;
         targetQueue = SimpleString.of(source.getAddress());
      }

      final QueueQueryResult result = sessionSPI.queueQuery(targetQueue, routingType, false, null);
      if (!result.isExists()) {
         federation.registerMissingQueue(targetQueue.toString());
         throw new ActiveMQAMQPNotFoundException("Queue: '" + targetQueue + "' does not exist");
      }

      if (targetAddress != null && !result.getAddress().equals(targetAddress)) {
         federation.registerMissingQueue(targetQueue.toString());
         throw new ActiveMQAMQPNotFoundException("Queue: '" + targetQueue + "' is not mapped to specified address: " + targetAddress);
      }

      // An queue receiver may supply a filter if the queue being federated had a filter attached
      // to it at creation, this ensures that we only bring back message that match the original
      // queue filter and not others that would simply increase traffic for no reason.
      final Map.Entry<Symbol, DescribedType> filter = AmqpSupport.findFilter(source.getFilter(), AmqpSupport.JMS_SELECTOR_FILTER_IDS);

      if (filter != null) {
         final String filterString = filter.getValue().getDescribed().toString();
         try {
            SelectorParser.parse(filterString);
         } catch (FilterException e) {
            throw new ActiveMQAMQPException(AmqpError.INVALID_FIELD, "Invalid filter", ActiveMQExceptionType.INVALID_FILTER_EXPRESSION);
         }

         // No need to apply another filter if the current one on the Queue already matches that.
         if (result.getFilterString() == null || !filterString.equals(result.getFilterString().toString())) {
            selector = filterString;
         } else {
            selector = null;
         }
      } else {
         selector = null;
      }

      // Configure an action to register a watcher for this federated queue to be created if it is
      // removed during the lifetime of the federation receiver, if restored an event will be sent
      // to the remote to prompt it to create a new receiver.
      resourceDeletedAction = (e) -> federation.registerMissingQueue(targetQueue.toString());

      return sessionSPI.createSender(senderContext, targetQueue, selector, false);
   }
}
