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

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.COPY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.GLOBAL;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.QUEUE_CAPABILITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.SHARED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.TOPIC_CAPABILITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.createQueueName;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.getReceiverPriority;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyDesiredCapability;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyOfferedCapabilities;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifySourceCapability;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.ParameterisedAddress;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link SenderController} instance used by and sender context that is not assigned a custom controller.
 * This controller is extensible so that specialized sender controllers can be created from it.
 * <p>
 * The default controller works best with incoming AMQP clients and JMS over AMQP clients. For intra-broker connections
 * it is likely that a custom sender controller would be a more appropriate option than using the default controller.
 */
public class DefaultSenderController implements SenderController {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AMQPConnectionContext connection;
   private final AMQPSessionCallback sessionSPI;
   private final Sender protonSender;
   private final String clientId;

   // A cached AMQP message writers married to the server sender instance on initialization
   private AMQPMessageWriter standardMessageWriter;
   private AMQPLargeMessageWriter largeMessageWriter;

   // A cached Core message writers married to the server sender instance on initialization
   private AMQPTunneledCoreMessageWriter coreMessageWriter;
   private AMQPTunneledCoreLargeMessageWriter coreLargeMessageWriter;
   private boolean coreTunnelingEnabled;

   private boolean shared;
   private boolean global;
   private boolean multicast;
   private SimpleString queue;
   private SimpleString tempQueueName;
   private String selector;
   private RoutingType routingTypeToUse = RoutingType.ANYCAST;
   private boolean isVolatile;

   public DefaultSenderController(AMQPSessionContext session, Sender protonSender, String clientId) {
      this.connection = session.getAMQPConnectionContext();
      this.sessionSPI = session.getSessionSPI();
      this.protonSender = protonSender;
      this.clientId = clientId;
   }

   @SuppressWarnings("unchecked")
   @Override
   public Consumer init(ProtonServerSenderContext senderContext) throws Exception {
      validateConnectionState();

      this.standardMessageWriter = new AMQPMessageWriter(senderContext);
      this.largeMessageWriter = new AMQPLargeMessageWriter(senderContext);

      Map<String, String> addressParameters = Collections.emptyMap();
      Source source = (Source) protonSender.getRemoteSource();
      final Map<Symbol, Object> supportedFilters = new HashMap<>();

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      protonSender.setSenderSettleMode(protonSender.getRemoteSenderSettleMode());

      // We don't currently support SECOND so enforce that the answer is always FIRST
      protonSender.setReceiverSettleMode(ReceiverSettleMode.FIRST);

      // If the remote receiver says it can accept tunneled core then that's what we will send them
      if (verifyOfferedCapabilities(protonSender, CORE_MESSAGE_TUNNELING_SUPPORT)) {
         protonSender.setDesiredCapabilities(new Symbol[] {CORE_MESSAGE_TUNNELING_SUPPORT});
         coreTunnelingEnabled = true;
         coreMessageWriter = new AMQPTunneledCoreMessageWriter(senderContext);
         coreLargeMessageWriter = new AMQPTunneledCoreLargeMessageWriter(senderContext);
      }

      if (source != null) {
         // We look for message selectors on every receiver, while in other cases we might only
         // consume the filter depending on the subscription type.
         Map.Entry<Symbol, DescribedType> filter = AmqpSupport.findFilter(source.getFilter(), AmqpSupport.JMS_SELECTOR_FILTER_IDS);
         if (filter != null) {
            selector = filter.getValue().getDescribed().toString();
            // Validate the Selector.
            try {
               SelectorParser.parse(selector);
            } catch (FilterException e) {
               throw new ActiveMQAMQPException(AmqpError.INVALID_FIELD, "Invalid filter", ActiveMQExceptionType.INVALID_FILTER_EXPRESSION);
            }

            supportedFilters.put(filter.getKey(), filter.getValue());
         }
      }

      if (source == null) {
         // Attempt to recover a previous subscription happens when a link reattach happens on a
         // subscription queue
         String pubId = protonSender.getName();
         global = verifyDesiredCapability(protonSender, GLOBAL);
         shared = verifyDesiredCapability(protonSender, SHARED);
         queue = createQueueName(connection.isUseCoreSubscriptionNaming(), clientId, pubId, true, global, false);
         QueueQueryResult result = sessionSPI.queueQuery(queue, RoutingType.MULTICAST, false);
         multicast = true;
         routingTypeToUse = RoutingType.MULTICAST;

         // Once confirmed that the address exists we need to return a Source that reflects
         // the lifetime policy and capabilities of the new subscription.
         if (result.isExists()) {
            source = new org.apache.qpid.proton.amqp.messaging.Source();
            source.setAddress(queue.toString());
            source.setDurable(TerminusDurability.UNSETTLED_STATE);
            source.setExpiryPolicy(TerminusExpiryPolicy.NEVER);
            source.setDistributionMode(COPY);
            source.setCapabilities(TOPIC_CAPABILITY);

            SimpleString filterString = result.getFilterString();
            if (filterString != null) {
               selector = filterString.toString();
               boolean noLocal = false;

               String remoteContainerId = protonSender.getSession().getConnection().getRemoteContainer();
               String noLocalFilter = MessageUtil.CONNECTION_ID_PROPERTY_NAME.toString() + "<>'" + remoteContainerId + "'";

               if (selector.endsWith(noLocalFilter)) {
                  if (selector.length() > noLocalFilter.length()) {
                     noLocalFilter = " AND " + noLocalFilter;
                     selector = selector.substring(0, selector.length() - noLocalFilter.length());
                  } else {
                     selector = null;
                  }

                  noLocal = true;
               }

               if (noLocal) {
                  supportedFilters.put(AmqpSupport.NO_LOCAL_NAME, AmqpNoLocalFilter.NO_LOCAL);
               }

               if (selector != null && !selector.trim().isEmpty()) {
                  supportedFilters.put(AmqpSupport.JMS_SELECTOR_NAME, new AmqpJmsSelectorFilter(selector));
               }
            }

            protonSender.setSource(source);
         } else {
            throw new ActiveMQAMQPNotFoundException("Unknown subscription link: " + protonSender.getName());
         }
      } else if (source.getDynamic()) {
         // if dynamic we have to create the node (queue) and set the address on the target, the
         // node is temporary and  will be deleted on closing of the session
         queue = SimpleString.of(java.util.UUID.randomUUID().toString());
         tempQueueName = queue;
         try {
            sessionSPI.createTemporaryQueue(queue, RoutingType.ANYCAST);
            // protonSession.getServerSession().createQueue(queue, queue, null, true, false);
         } catch (Exception e) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingTemporaryQueue(e.getMessage());
         }
         source.setAddress(queue.toString());
      } else {
         final String sourceAddress = ParameterisedAddress.extractAddress(source.getAddress());

         addressParameters = ParameterisedAddress.extractParameters(source.getAddress());

         SimpleString addressToUse;
         SimpleString queueNameToUse = null;
         shared = verifySourceCapability(source, SHARED);
         global = verifySourceCapability(source, GLOBAL);

         final boolean isFQQN;

         //find out if we have an address made up of the address and queue name, if yes then set queue name
         if (CompositeAddress.isFullyQualified(sourceAddress)) {
            isFQQN = true;
            addressToUse = SimpleString.of(CompositeAddress.extractAddressName(sourceAddress));
            queueNameToUse = SimpleString.of(CompositeAddress.extractQueueName(sourceAddress));
         } else {
            isFQQN = false;
            addressToUse = SimpleString.of(sourceAddress);
         }

         //check to see if the client has defined how we act
         boolean clientDefined = verifySourceCapability(source, TOPIC_CAPABILITY) || verifySourceCapability(source, QUEUE_CAPABILITY);
         if (clientDefined) {
            multicast = verifySourceCapability(source, TOPIC_CAPABILITY);
            AddressQueryResult addressQueryResult = null;
            try {
               addressQueryResult = sessionSPI.addressQuery(addressToUse, multicast ? RoutingType.MULTICAST : RoutingType.ANYCAST, true);
            } catch (ActiveMQSecurityException e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(e.getMessage());
            } catch (ActiveMQAMQPException e) {
               throw e;
            } catch (Exception e) {
               throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
            }

            if (!addressQueryResult.isExists()) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.sourceAddressDoesntExist();
            }

            Set<RoutingType> routingTypes = addressQueryResult.getRoutingTypes();

            //if the client defines 1 routing type and the broker another then throw an exception
            if (multicast && !routingTypes.contains(RoutingType.MULTICAST)) {
               throw new ActiveMQAMQPIllegalStateException("Address " + addressToUse + " is not configured for topic support");
            } else if (!multicast && !routingTypes.contains(RoutingType.ANYCAST)) {
               //if client specifies fully qualified name that's allowed, don't throw exception.
               if (queueNameToUse == null) {
                  throw new ActiveMQAMQPIllegalStateException("Address " + addressToUse + " is not configured for queue support");
               }
            }
         } else {
            // if not we look up the address
            AddressQueryResult addressQueryResult = null;

            // Set this to the broker configured default for the address prior to the lookup so that
            // an auto create will actually use the configured defaults.  The actual query result will
            // contain the true answer on what routing type the address actually has though.
            routingTypeToUse = Objects.requireNonNullElse(sessionSPI.getDefaultRoutingType(addressToUse), ActiveMQDefaultConfiguration.getDefaultRoutingType());

            try {
               addressQueryResult = sessionSPI.addressQuery(addressToUse, routingTypeToUse, true);
            } catch (ActiveMQSecurityException e) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(e.getMessage());
            } catch (ActiveMQAMQPException e) {
               throw e;
            } catch (Exception e) {
               throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
            }

            if (!addressQueryResult.isExists()) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.sourceAddressDoesntExist();
            }

            Set<RoutingType> routingTypes = addressQueryResult.getRoutingTypes();
            if (routingTypes.contains(RoutingType.MULTICAST) && routingTypes.size() == 1) {
               multicast = true;
            } else {
               //todo add some checks if both routing types are supported
               multicast = false;
            }
         }
         routingTypeToUse = multicast ? RoutingType.MULTICAST : RoutingType.ANYCAST;
         // if not dynamic then we use the target's address as the address to forward the
         // messages to, however there has to be a queue bound to it so we need to check this.
         if (multicast) {
            Map.Entry<Symbol, DescribedType> filter = AmqpSupport.findFilter(source.getFilter(), AmqpSupport.NO_LOCAL_FILTER_IDS);
            if (filter != null) {
               String remoteContainerId = protonSender.getSession().getConnection().getRemoteContainer();
               String noLocalFilter = MessageUtil.CONNECTION_ID_PROPERTY_NAME.toString() + "<>'" + remoteContainerId + "'";
               if (selector != null) {
                  selector += " AND " + noLocalFilter;
               } else {
                  selector = noLocalFilter;
               }

               supportedFilters.put(filter.getKey(), filter.getValue());
            }

            SimpleString simpleStringSelector = SimpleString.of(selector);
            queue = getMatchingQueue(queueNameToUse, addressToUse, RoutingType.MULTICAST, simpleStringSelector, isFQQN);

            //if the address specifies a broker configured queue then we always use this, treat it as a queue
            if (queue != null) {
               multicast = false;
            } else if (TerminusDurability.UNSETTLED_STATE.equals(source.getDurable()) || TerminusDurability.CONFIGURATION.equals(source.getDurable())) {

               // if we are a subscription and durable create a durable queue using the container
               // id and link name
               String pubId = protonSender.getName();
               queue = createQueueName(connection.isUseCoreSubscriptionNaming(), clientId, pubId, shared, global, false);
               QueueQueryResult result = sessionSPI.queueQuery(queue, routingTypeToUse, false);
               if (result.isExists()) {
                  /*
                   * If a client attaches to an existing durable subscription with a different filter or address then
                   * we must recreate the queue (JMS semantics). However, if the corresponding queue is managed via the
                   * configuration then we don't want to change it. We must account for optional address prefixes that
                   * are not carried over into the actual created address by stripping any prefix value that matches
                   * those configured on the acceptor.
                   */
                  if (!result.isConfigurationManaged() &&
                      (!Objects.equals(result.getAddress(), sessionSPI.removePrefix(addressToUse)) ||
                       !Objects.equals(result.getFilterString(), simpleStringSelector))) {

                     if (result.getConsumerCount() == 0) {
                        sessionSPI.deleteQueue(queue);
                        if (shared) {
                           sessionSPI.createSharedDurableQueue(addressToUse, RoutingType.MULTICAST, queue, simpleStringSelector);
                        } else {
                           sessionSPI.createUnsharedDurableQueue(addressToUse, RoutingType.MULTICAST, queue, simpleStringSelector);
                        }
                     } else {
                        throw new ActiveMQAMQPIllegalStateException("Unable to recreate subscription, consumers already exist");
                     }
                  }
               } else {
                  if (shared) {
                     sessionSPI.createSharedDurableQueue(addressToUse, RoutingType.MULTICAST, queue, simpleStringSelector);
                  } else {
                     sessionSPI.createUnsharedDurableQueue(addressToUse, RoutingType.MULTICAST, queue, simpleStringSelector);
                  }
               }
            } else {
               // otherwise we are a volatile subscription
               isVolatile = true;
               if (shared && protonSender.getName() != null) {
                  queue = createQueueName(connection.isUseCoreSubscriptionNaming(), clientId, protonSender.getName(), shared, global, isVolatile);
                  QueueQueryResult result = sessionSPI.queueQuery(queue, routingTypeToUse, false);
                  if ((!result.isExists() || !Objects.equals(result.getAddress(), addressToUse) || !Objects.equals(result.getFilterString(), simpleStringSelector)) && !result.isConfigurationManaged()) {
                     sessionSPI.createSharedVolatileQueue(addressToUse, RoutingType.MULTICAST, queue, simpleStringSelector);
                  }
               } else {
                  queue = SimpleString.of(java.util.UUID.randomUUID().toString());
                  tempQueueName = queue;
                  try {
                     sessionSPI.createTemporaryQueue(addressToUse, queue, RoutingType.MULTICAST, simpleStringSelector);
                  } catch (Exception e) {
                     throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.errorCreatingTemporaryQueue(e.getMessage());
                  }
               }
            }
         } else {
            if (queueNameToUse != null) {
               SimpleString matchingAnycastQueue;
               QueueQueryResult result = sessionSPI.queueQuery(CompositeAddress.toFullyQualified(addressToUse, queueNameToUse), null, false, null);
               if (result.isExists()) {
                  // if the queue exists and we're using FQQN then just ignore the routing-type
                  routingTypeToUse = null;
               }
               matchingAnycastQueue = getMatchingQueue(queueNameToUse, addressToUse, routingTypeToUse, null, false);
               if (matchingAnycastQueue != null) {
                  queue = matchingAnycastQueue;
               } else {
                  throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.sourceAddressDoesntExist();
               }
            } else {
               SimpleString matchingAnycastQueue = sessionSPI.getMatchingQueue(addressToUse, RoutingType.ANYCAST);
               if (matchingAnycastQueue != null) {
                  queue = matchingAnycastQueue;
               } else {
                  queue = addressToUse;
               }
            }
         }

         if (queue == null) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.sourceAddressNotSet();
         }

         try {
            if (!sessionSPI.queueQuery(queue, routingTypeToUse, !multicast).isExists()) {
               throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.sourceAddressDoesntExist();
            }
         } catch (ActiveMQAMQPNotFoundException e) {
            throw e;
         } catch (Exception e) {
            throw new ActiveMQAMQPInternalErrorException(e.getMessage(), e);
         }
      }

      // We need to update the source with any filters we support otherwise the client
      // is free to consider the attach as having failed if we don't send back what we
      // do support or if we send something we don't support the client won't know we
      // have not honored what it asked for.
      source.setFilter(supportedFilters.isEmpty() ? null : supportedFilters);

      final boolean browseOnly = !multicast && source.getDistributionMode() != null && source.getDistributionMode().equals(COPY);
      final Number consumerPriority = getReceiverPriority(protonSender.getRemoteProperties(), extractConsumerPriority(addressParameters));

      // Any new parameters used should be extracted from the values parsed from the address to avoid this log message.
      if (!addressParameters.isEmpty()) {
         final String unusedParametersMessage = ""
            + " Not all specified address options were applicable to the created server consumer."
            + " Check the options are spelled correctly."
            + " Unused parameters=[" + addressParameters + "].";

         logger.debug(unusedParametersMessage);
      }

      return sessionSPI.createSender(senderContext, queue, multicast ? null : selector, browseOnly, consumerPriority);
   }

   private static Number extractConsumerPriority(Map<String, String> addressParameters) {
      if (addressParameters != null && !addressParameters.isEmpty()) {
         final String priorityString = addressParameters.remove(QueueConfiguration.CONSUMER_PRIORITY);
         if (priorityString != null) {
            return Integer.valueOf(priorityString);
         }
      }

      return null;
   }

   @Override
   public void close(boolean remoteClose) throws Exception {
      // Only delete temporary resources when the remote has triggered the close with a Detach
      if (remoteClose) {
         final Source source = (Source) protonSender.getSource();
         final String sourceAddress = getSourceAddress(source);

         if (source != null && sourceAddress != null && multicast) {
            SimpleString queueName = SimpleString.of(sourceAddress);
            QueueQueryResult result = sessionSPI.queueQuery(queueName, routingTypeToUse, false);
            if (result.isExists() && source.getDynamic()) {
               sessionSPI.deleteQueue(queueName);
            } else {
               if (source.getDurable() == TerminusDurability.NONE && tempQueueName != null && (source.getExpiryPolicy() == TerminusExpiryPolicy.LINK_DETACH || source.getExpiryPolicy() == TerminusExpiryPolicy.SESSION_END)) {
                  sessionSPI.removeTemporaryQueue(tempQueueName);
               } else {
                  String pubId = protonSender.getName();
                  if (pubId.contains("|")) {
                     pubId = pubId.split("\\|")[0];
                  }
                  SimpleString queue = createQueueName(connection.isUseCoreSubscriptionNaming(), clientId, pubId, shared, global, isVolatile);
                  result = sessionSPI.queueQuery(queue, multicast ? RoutingType.MULTICAST : RoutingType.ANYCAST, false);
                  //only delete if it isn't volatile and has no consumers
                  if (result.isExists() && !isVolatile && result.getConsumerCount() == 0) {
                     sessionSPI.deleteQueue(queue);
                  }
               }
            }
         } else if (source != null && source.getDynamic() && (source.getExpiryPolicy() == TerminusExpiryPolicy.LINK_DETACH || source.getExpiryPolicy() == TerminusExpiryPolicy.SESSION_END)) {
            try {
               sessionSPI.removeTemporaryQueue(SimpleString.of(sourceAddress));
            } catch (Exception e) {
               // Ignore on close, its temporary anyway and will be removed later
            }
         }
      }
   }

   @Override
   public MessageWriter selectOutgoingMessageWriter(ProtonServerSenderContext sender, MessageReference reference) {
      final MessageWriter selected;
      final Message message = reference.getMessage();
      final boolean isLarge = message.isLargeMessage();

      if (message instanceof AMQPMessage) {
         selected = isLarge ? largeMessageWriter : standardMessageWriter;
      } else if (coreTunnelingEnabled) {
         selected = isLarge ? coreLargeMessageWriter : coreMessageWriter;
      } else {
         selected = standardMessageWriter;
      }

      return selected;
   }

   protected SimpleString getMatchingQueue(SimpleString queueName, SimpleString address, RoutingType routingType, SimpleString filter, boolean matchFilter) throws Exception {
      if (queueName != null) {
         QueueQueryResult result = sessionSPI.queueQuery(CompositeAddress.toFullyQualified(address, queueName), routingType, true, filter);
         if (!result.isExists()) {
            throw new ActiveMQAMQPNotFoundException("Queue: '" + queueName + "' does not exist");
         } else {
            if (!result.getAddress().equals(address)) {
               throw new ActiveMQAMQPNotFoundException("Queue: '" + queueName + "' does not exist for address '" + address + "'");
            }
            if (matchFilter && filter != null && result.getFilterString() != null && !filter.equals(result.getFilterString())) {
               throw new ActiveMQIllegalStateException("Queue: " + queueName + " filter mismatch [" + filter + "] is different than existing filter [" + result.getFilterString() + "]");

            }
            return queueName;
         }
      }

      return null;
   }

   private static String getSourceAddress(Source source) {
      if (source != null && source.getAddress() != null) {
         return ParameterisedAddress.extractAddress(source.getAddress());
      } else {
         return null;
      }
   }

   private void validateConnectionState() throws ActiveMQException {
      final ProtonHandler handler = connection == null ? null : connection.getHandler();
      final Connection qpidConnection = handler == null ? null : handler.getConnection();

      if (qpidConnection == null) {
         if (logger.isDebugEnabled()) {
            logger.debug("validateConnectionState:: connection={}, handler={}, qpidConnection={}", connection, handler, qpidConnection);
         }

         ActiveMQAMQPProtocolLogger.LOGGER.invalidAMQPConnectionState("null", "null");
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.invalidAMQPConnectionState("null");
      }

      if (qpidConnection.getRemoteState() == EndpointState.CLOSED) {
         ActiveMQAMQPProtocolLogger.LOGGER.invalidAMQPConnectionState(qpidConnection.getRemoteState(), connection.getRemoteAddress());
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.invalidAMQPConnectionState(qpidConnection.getRemoteState());
      }
   }
}
