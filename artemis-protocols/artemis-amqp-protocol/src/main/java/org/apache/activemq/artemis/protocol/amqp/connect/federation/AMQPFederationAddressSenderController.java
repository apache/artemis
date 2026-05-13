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

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_ADDRESS_RECEIVER;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationPolicySupport.FEDERATED_ADDRESS_SOURCE_PROPERTIES;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationMetrics.ProducerMetrics;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.SenderController;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SenderController} used when an AMQP federation Address receiver is created and this side of the connection
 * needs to create a matching sender. The address sender controller must check on initialization if the address exists
 * and if not it should create it using the configuration values supplied in the link source properties that control the
 * lifetime of the address once the link is closed.
 */
public final class AMQPFederationAddressSenderController extends AMQPFederationSenderController {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public AMQPFederationAddressSenderController(AMQPFederationRemoteAddressPolicyManager manager, ProducerMetrics metrics, Consumer<AMQPFederationSenderController> closedListener) throws ActiveMQAMQPException {
      super(manager, metrics, closedListener);
   }

   @Override
   public Role getRole() {
      return Role.ADDRESS_PRODUCER;
   }

   @SuppressWarnings("unchecked")
   @Override
   public ServerConsumer createServerConsumer(ProtonServerSenderContext senderContext) throws Exception {
      final Sender sender = senderContext.getSender();
      final Source source = (Source) sender.getRemoteSource();
      final SimpleString sourceAddress = SimpleString.of(source.getAddress());
      final String selector;
      final Connection protonConnection = session.getSession().getConnection();

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      sender.setSenderSettleMode(sender.getRemoteSenderSettleMode());
      // We don't currently support SECOND so enforce that the answer is always FIRST
      sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
      // We need to offer back that we support federation for the remote to complete the attach
      sender.setOfferedCapabilities(new Symbol[] {FEDERATION_ADDRESS_RECEIVER});
      // We indicate desired to meet specification that we cannot use a capability unless we
      // indicated it was desired, however unless offered by the remote we cannot use it.
      sender.setDesiredCapabilities(new Symbol[] {AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT});

      final Map<String, Object> addressSourceProperties;

      if (sender.getRemoteProperties() == null || !sender.getRemoteProperties().containsKey(FEDERATED_ADDRESS_SOURCE_PROPERTIES)) {
         addressSourceProperties = Collections.emptyMap();
      } else {
         addressSourceProperties = (Map<String, Object>) sender.getRemoteProperties().get(FEDERATED_ADDRESS_SOURCE_PROPERTIES);
      }

      final boolean autoDelete = (boolean) addressSourceProperties.getOrDefault(ADDRESS_AUTO_DELETE, false);
      final long autoDeleteDelay = ((Number) addressSourceProperties.getOrDefault(ADDRESS_AUTO_DELETE_DELAY, 0)).longValue();
      final long autoDeleteMsgCount = ((Number) addressSourceProperties.getOrDefault(ADDRESS_AUTO_DELETE_MSG_COUNT, 0)).longValue();

      // An address receiver may opt to filter on things like max message hops or no local message
      // reflection so we must check for a filter here and apply it if it exists.
      final String jmsSelector = getJMSSelectorFromFilters(source);
      final Map.Entry<Symbol, DescribedType> noLocal = AmqpSupport.findFilter(source.getFilter(), AmqpSupport.NO_LOCAL_FILTER_IDS);

      if (noLocal != null) {
         String remoteContainerId = protonConnection.getRemoteContainer();
         String noLocalFilter = MessageUtil.CONNECTION_ID_PROPERTY_NAME_STRING + "<>'" + remoteContainerId + "'";

         if (jmsSelector == null) {
            selector = noLocalFilter;
         } else {
            selector = jmsSelector + " AND " + noLocalFilter;
         }
      } else {
         selector = jmsSelector;
      }

      final SimpleString address;
      final SimpleString queueName;

      // Either we have negotiated subscriptions using FQQN or we default to older behavior based on link names
      if (CompositeAddress.isFullyQualified(sourceAddress)) {
         address = CompositeAddress.extractAddressName(sourceAddress);
         queueName = CompositeAddress.extractQueueName(sourceAddress);
      } else {
         address = sourceAddress;
         queueName = SimpleString.of(sender.getName());
      }

      final AddressQueryResult addressQueryResult;

      try {
         addressQueryResult = sessionSPI.addressQuery(address, RoutingType.MULTICAST, true);
      } catch (ActiveMQSecurityException e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(e.getMessage());
      } catch (ActiveMQAMQPException e) {
         throw e;
      } catch (Exception e) {
         throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
      }

      if (!addressQueryResult.isExists()) {
         federation.registerMissingAddress(address.toString());

         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.sourceAddressDoesntExist();
      }

      final Set<RoutingType> routingTypes = addressQueryResult.getRoutingTypes();

      // Strictly enforce the MULTICAST nature of current address federation support.
      if (!routingTypes.contains(RoutingType.MULTICAST)) {
         throw new ActiveMQAMQPIllegalStateException("Address " + address + " is not configured for MULTICAST support");
      }

      final RoutingType routingType = getRoutingType(source);

      // Recover or create the queue we use to reflect the messages sent to the address to the remote
      QueueQueryResult queueQuery = sessionSPI.queueQuery(queueName, routingType, false);
      if (!queueQuery.isExists()) {
         final QueueConfiguration configuration = QueueConfiguration.of(queueName);

         configuration.setAddress(address);
         configuration.setRoutingType(routingType);
         configuration.setAutoCreateAddress(false);
         configuration.setMaxConsumers(-1);
         configuration.setPurgeOnNoConsumers(false);
         configuration.setFilterString(selector);
         configuration.setDurable(true);
         configuration.setAutoCreated(false);
         configuration.setAutoDelete(autoDelete);
         configuration.setAutoDeleteDelay(autoDeleteDelay);
         configuration.setAutoDeleteMessageCount(autoDeleteMsgCount);

         // Try and create it and then later we will validate fully that it matches our expectations
         // since we could lose a race with some other resource creating its own resources.
         queueQuery = sessionSPI.queueQuery(configuration, true);
      }

      if (!queueQuery.getAddress().equals(address)) {
         throw new ActiveMQAMQPIllegalStateException("Requested queue: " + queueName + " for federation of address: " + address +
                                                     ", but it is already mapped to a different address: " + queueQuery.getAddress());
      }

      // Configure an action to register a watcher for this federated address to be created if it is
      // removed during the lifetime of the federation receiver, if restored an event will be sent
      // to the remote to prompt it to create a new receiver.
      resourceDeletedAction = (e) -> federation.registerMissingAddress(address.toString());

      return sessionSPI.createSender(senderContext, queueName, null, false);
   }

   @Override
   protected void handleLinkRemotelyClosed() {
      // Remote closed indicating there was no demand, so we can cleanup the federation binding
      deleteAddressFederationBindingIfPresent();
   }

   @Override
   protected void handleLinkLocallyClosed(ErrorCondition error) {
      if (error != null) {
         // Local side forcibly removed the federation consumer so we should ensure the binding is removed.
         deleteAddressFederationBindingIfPresent();
      }
   }

   private void deleteAddressFederationBindingIfPresent() {
      if (senderContext == null) {
         return;
      }

      try {
         final Sender sender = senderContext.getSender();
         final Source source = (Source) sender.getRemoteSource();
         final SimpleString sourceAddress = SimpleString.of(source.getAddress());
         final RoutingType routingType = getRoutingType(source);

         final SimpleString queueName;

         // Either we have negotiated subscriptions using FQQN or we default to older behavior based on link names
         if (CompositeAddress.isFullyQualified(sourceAddress)) {
            queueName = CompositeAddress.extractQueueName(sourceAddress);
         } else {
            queueName = SimpleString.of(sender.getName());
         }

         final QueueQueryResult queueQuery = sessionSPI.queueQuery(queueName, routingType, false);
         if (queueQuery.isExists()) {
            sessionSPI.deleteQueue(queueName);
         }
      } catch (Exception e) {
         logger.debug("Federation address sender link closed cleanup caught error: ", e);
      }
   }

   @SuppressWarnings("unchecked")
   private String getJMSSelectorFromFilters(Source source) throws ActiveMQAMQPException {
      final Map.Entry<Symbol, DescribedType> jmsSelector = AmqpSupport.findFilter(source.getFilter(), AmqpSupport.JMS_SELECTOR_FILTER_IDS);

      String selectorString = null;

      // Validate the JMS selector if present.
      if (jmsSelector != null) {
         selectorString = jmsSelector.getValue().getDescribed().toString();

         try {
            SelectorParser.parse(selectorString);
         } catch (FilterException e) {
            throw new ActiveMQAMQPException(AmqpError.INVALID_FIELD, "Invalid filter", ActiveMQExceptionType.INVALID_FILTER_EXPRESSION);
         }
      }

      return selectorString;
   }
}
