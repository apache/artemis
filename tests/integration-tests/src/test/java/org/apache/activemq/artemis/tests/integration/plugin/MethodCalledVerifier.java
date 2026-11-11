/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueConfig;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.federation.FederatedConsumerKey;
import org.apache.activemq.artemis.core.server.federation.FederatedQueueConsumer;
import org.apache.activemq.artemis.core.server.federation.FederationStream;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.tests.util.Wait;

public class MethodCalledVerifier implements ActiveMQServerPlugin {

   private final Map<String, AtomicInteger> methodCalls;

   public static final String AFTER_CREATE_CONNECTION = "afterCreateConnection";
   public static final String AFTER_DESTROY_CONNECTION = "afterDestroyConnection";
   public static final String BEFORE_CREATE_SESSION = "beforeCreateSession";
   public static final String AFTER_CREATE_SESSION = "afterCreateSession";
   public static final String BEFORE_CLOSE_SESSION = "beforeCloseSession";
   public static final String AFTER_CLOSE_SESSION = "afterCloseSession";
   public static final String BEFORE_SESSION_METADATA_ADDED = "beforeSessionMetadataAdded";
   public static final String AFTER_SESSION_METADATA_ADDED = "afterSessionMetadataAdded";
   public static final String BEFORE_CREATE_CONSUMER = "beforeCreateConsumer";
   public static final String AFTER_CREATE_CONSUMER = "afterCreateConsumer";
   public static final String BEFORE_CLOSE_CONSUMER = "beforeCloseConsumer";
   public static final String AFTER_CLOSE_CONSUMER = "afterCloseConsumer";
   public static final String BEFORE_ADD_ADDRESS = "beforeAddAddress";
   public static final String AFTER_ADD_ADDRESS = "afterAddAddress";
   public static final String BEFORE_UPDATE_ADDRESS = "beforeUpdateAddress";
   public static final String AFTER_UPDATE_ADDRESS = "afterUpdateAddress";
   public static final String BEFORE_REMOVE_ADDRESS = "beforeRemoveAddress";
   public static final String AFTER_REMOVE_ADDRESS = "afterRemoveAddress";
   public static final String BEFORE_CREATE_QUEUE = "beforeCreateQueue";
   public static final String AFTER_CREATE_QUEUE = "afterCreateQueue";
   public static final String BEFORE_DESTROY_QUEUE = "beforeDestroyQueue";
   public static final String AFTER_DESTROY_QUEUE = "afterDestroyQueue";
   public static final String BEFORE_ADD_BINDING = "beforeAddBinding";
   public static final String AFTER_ADD_BINDING = "afterAddBinding";
   public static final String BEFORE_REMOVE_BINDING = "beforeRemoveBinding";
   public static final String AFTER_REMOVE_BINDING = "afterRemoveBinding";
   public static final String MESSAGE_EXPIRED = "messageExpired";
   public static final String MESSAGE_ACKED = "messageAcknowledged";
   public static final String MESSAGE_MOVED = "messageMoved";
   public static final String BEFORE_SEND = "beforeSend";
   public static final String AFTER_SEND = "afterSend";
   public static final String ON_SEND_EXCEPTION = "onSendException";
   public static final String BEFORE_MESSAGE_ROUTE = "beforeMessageRoute";
   public static final String AFTER_MESSAGE_ROUTE = "afterMessageRoute";
   public static final String ON_MESSAGE_ROUTE_EXCEPTION = "onMessageRouteException";
   public static final String BEFORE_DELIVER = "beforeDeliver";
   public static final String AFTER_DELIVER = "afterDeliver";
   public static final String BEFORE_DEPLOY_BRIDGE = "beforeDeployBridge";
   public static final String AFTER_DEPLOY_BRIDGE = "afterDeployBridge";
   public static final String BEFORE_DELIVER_BRIDGE = "beforeDeliverBridge";
   public static final String AFTER_DELIVER_BRIDGE = "afterDeliverBridge";
   public static final String AFTER_ACKNOWLEDGE_BRIDGE = "afterAcknowledgeBridge";
   public static final String FEDERATION_STREAM_STARTED = "federationStreamStarted";
   public static final String FEDERATION_STREAM_STOPPED = "federationStreamStopped";
   public static final String BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER = "beforeCreateFederatedQueueConsumer";
   public static final String AFTER_CREATE_FEDERATED_QUEUE_CONSUMER = "afterCreateFederatedQueueConsumer";
   public static final String BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER = "beforeCloseFederatedQueueConsumer";
   public static final String AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER = "afterCloseFederatedQueueConsumer";
   public static final String FEDERATED_ADDRESS_CONDITIONAL_CREATE_CONSUMER = "federatedAddressConditionalCreateConsumer";
   public static final String FEDERATED_QUEUE_CONDITIONAL_CREATE_CONSUMER = "federatedQueueConditionalCreateConsumer";
   public static final String BEFORE_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED = "beforeFederatedQueueConsumerMessageHandled";
   public static final String AFTER_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED = "afterFederatedQueueConsumerMessageHandled";

   public MethodCalledVerifier(Map<String, AtomicInteger> methodCalls) {
      super();
      this.methodCalls = methodCalls;
   }

   public MethodCalledVerifier() {
      this(new ConcurrentHashMap<>());
   }

   @Override
   public void afterCreateConnection(RemotingConnection connection) {
      Objects.requireNonNull(connection);
      methodCalled(AFTER_CREATE_CONNECTION);
   }

   @Override
   public void afterDestroyConnection(RemotingConnection connection) {
      Objects.requireNonNull(connection);
      methodCalled(AFTER_DESTROY_CONNECTION);
   }

   @Override
   public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection,
                                   boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa,
                                   String defaultAddress, SessionCallback callback, boolean autoCreateQueues,
                                   OperationContext context, Map<SimpleString, RoutingType> prefixes) {
      Objects.requireNonNull(connection);
      methodCalled(BEFORE_CREATE_SESSION);
   }

   @Override
   public void afterCreateSession(ServerSession session) {
      Objects.requireNonNull(session);
      methodCalled(AFTER_CREATE_SESSION);
   }

   @Override
   public void beforeCloseSession(ServerSession session, boolean failed) {
      Objects.requireNonNull(session);
      methodCalled(BEFORE_CLOSE_SESSION);
   }

   @Override
   public void afterCloseSession(ServerSession session, boolean failed) {
      Objects.requireNonNull(session);
      methodCalled(AFTER_CLOSE_SESSION);
   }

   @Override
   public void beforeSessionMetadataAdded(ServerSession session, String key, String data) {
      Objects.requireNonNull(key);
      methodCalled(BEFORE_SESSION_METADATA_ADDED);
   }

   @Override
   public void afterSessionMetadataAdded(ServerSession session, String key, String data) {
      Objects.requireNonNull(key);
      methodCalled(AFTER_SESSION_METADATA_ADDED);
   }

   @Override
   public void beforeCreateConsumer(long consumerID, QueueBinding queueBinding, SimpleString filterString,
                                    boolean browseOnly, boolean supportLargeMessage) {
      Objects.requireNonNull(queueBinding);
      methodCalled(BEFORE_CREATE_CONSUMER);
   }

   @Override
   public void afterCreateConsumer(ServerConsumer consumer) {
      Objects.requireNonNull(consumer);
      methodCalled(AFTER_CREATE_CONSUMER);
   }

   @Override
   public void beforeCloseConsumer(ServerConsumer consumer, boolean failed) {
      Objects.requireNonNull(consumer);
      methodCalled(BEFORE_CLOSE_CONSUMER);
   }

   @Override
   public void afterCloseConsumer(ServerConsumer consumer, boolean failed) {
      Objects.requireNonNull(consumer);
      methodCalled(AFTER_CLOSE_CONSUMER);
   }

   @Override
   public void beforeAddAddress(AddressInfo addressInfo, boolean reload) throws ActiveMQException {
      Objects.requireNonNull(addressInfo);
      methodCalled(BEFORE_ADD_ADDRESS);
   }

   @Override
   public void afterAddAddress(AddressInfo addressInfo, boolean reload) throws ActiveMQException {
      Objects.requireNonNull(addressInfo);
      methodCalled(AFTER_ADD_ADDRESS);
   }

   @Override
   public void beforeUpdateAddress(SimpleString address, EnumSet<RoutingType> routingTypes)
         throws ActiveMQException {
      Objects.requireNonNull(address);
      Objects.requireNonNull(routingTypes);
      methodCalled(BEFORE_UPDATE_ADDRESS);
   }

   @Override
   public void afterUpdateAddress(AddressInfo addressInfo) throws ActiveMQException {
      Objects.requireNonNull(addressInfo);
      methodCalled(AFTER_UPDATE_ADDRESS);
   }

   @Override
   public void beforeRemoveAddress(SimpleString address) throws ActiveMQException {
      Objects.requireNonNull(address);
      methodCalled(BEFORE_REMOVE_ADDRESS);
   }

   @Override
   public void afterRemoveAddress(SimpleString address, AddressInfo addressInfo) throws ActiveMQException {
      Objects.requireNonNull(address);
      Objects.requireNonNull(addressInfo);
      methodCalled(AFTER_REMOVE_ADDRESS);
   }

   @Override
   public void beforeCreateQueue(QueueConfig queueConfig) {
      Objects.requireNonNull(queueConfig);
      methodCalled(BEFORE_CREATE_QUEUE);
   }

   @Override
   public void afterCreateQueue(org.apache.activemq.artemis.core.server.Queue queue) {
      Objects.requireNonNull(queue);
      methodCalled(AFTER_CREATE_QUEUE);
   }

   @Override
   public void beforeDestroyQueue(Queue queue, SecurityAuth session, boolean checkConsumerCount,
         boolean removeConsumers, boolean autoDeleteAddress) {
      Objects.requireNonNull(queue);
      methodCalled(BEFORE_DESTROY_QUEUE);
   }

   @Override
   public void afterDestroyQueue(Queue queue, SimpleString address, SecurityAuth session, boolean checkConsumerCount,
         boolean removeConsumers, boolean autoDeleteAddress) {
      Objects.requireNonNull(queue);
      methodCalled(AFTER_DESTROY_QUEUE);
   }

   @Override
   public void beforeAddBinding(Binding binding) throws ActiveMQException {
      Objects.requireNonNull(binding);
      methodCalled(BEFORE_ADD_BINDING);
   }

   @Override
   public void afterAddBinding(Binding binding) throws ActiveMQException {
      Objects.requireNonNull(binding);
      methodCalled(AFTER_ADD_BINDING);
   }

   @Override
   public void beforeRemoveBinding(SimpleString uniqueName, Transaction tx, boolean deleteData)
         throws ActiveMQException {
      Objects.requireNonNull(uniqueName);
      methodCalled(BEFORE_REMOVE_BINDING);
   }

   @Override
   public void afterRemoveBinding(Binding binding, Transaction tx, boolean deleteData) throws ActiveMQException {
      Objects.requireNonNull(binding);
      methodCalled(AFTER_REMOVE_BINDING);
   }

   @Override
   public void messageExpired(MessageReference message, SimpleString messageExpiryAddress, ServerConsumer consumer) {
      Objects.requireNonNull(message);
      methodCalled(MESSAGE_EXPIRED);
   }

   @Override
   public void messageAcknowledged(MessageReference ref, AckReason reason, ServerConsumer consumer) {
      Objects.requireNonNull(ref);
      Objects.requireNonNull(reason);
      methodCalled(MESSAGE_ACKED);
   }

   @Override
   public void messageMoved(final Transaction tx,
                            final MessageReference ref,
                            final AckReason reason,
                            final SimpleString destAddress,
                            final Long destQueueID,
                            final ServerConsumer consumer,
                            final Message newMessage,
                            final RoutingStatus result) {
      Objects.requireNonNull(ref);
      Objects.requireNonNull(reason);
      Objects.requireNonNull(destAddress);
      Objects.requireNonNull(newMessage);
      Objects.requireNonNull(result);
      methodCalled(MESSAGE_MOVED);
   }

   @Override
   public void beforeSend(ServerSession session, Transaction tx, Message message, boolean direct,
         boolean noAutoCreateQueue) {
      Objects.requireNonNull(message);
      methodCalled(BEFORE_SEND);
   }

   @Override
   public void afterSend(ServerSession session, Transaction tx, Message message, boolean direct,
         boolean noAutoCreateQueue,
                         RoutingStatus result) {
      Objects.requireNonNull(message);
      Objects.requireNonNull(result);
      methodCalled(AFTER_SEND);
   }

   @Override
   public void onSendException(ServerSession session, Transaction tx, Message message, boolean direct,
                               boolean noAutoCreateQueue, Exception e) {
      Objects.requireNonNull(message);
      Objects.requireNonNull(e);
      methodCalled(ON_SEND_EXCEPTION);
   }

   @Override
   public void beforeMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates) {
      Objects.requireNonNull(message);
      Objects.requireNonNull(context);
      methodCalled(BEFORE_MESSAGE_ROUTE);
   }

   @Override
   public void afterMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates,
                                 RoutingStatus result) {
      Objects.requireNonNull(message);
      Objects.requireNonNull(context);
      Objects.requireNonNull(result);
      methodCalled(AFTER_MESSAGE_ROUTE);
   }

   @Override
   public void onMessageRouteException(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates,
                                       Exception e) {
      Objects.requireNonNull(message);
      Objects.requireNonNull(context);
      Objects.requireNonNull(e);
      methodCalled(ON_MESSAGE_ROUTE_EXCEPTION);
   }

   @Override
   public void beforeDeliver(ServerConsumer consumer, MessageReference reference) {
      Objects.requireNonNull(reference);
      methodCalled(BEFORE_DELIVER);
   }

   @Override
   public void afterDeliver(ServerConsumer consumer, MessageReference reference) {
      Objects.requireNonNull(reference);
      methodCalled(AFTER_DELIVER);
   }

   @Override
   public void beforeDeployBridge(BridgeConfiguration config) {
      Objects.requireNonNull(config);
      methodCalled(BEFORE_DEPLOY_BRIDGE);
   }

   @Override
   public void afterDeployBridge(Bridge bridge) {
      Objects.requireNonNull(bridge);
      methodCalled(AFTER_DEPLOY_BRIDGE);
   }

   @Override
   public void beforeDeliverBridge(Bridge bridge, MessageReference ref) throws ActiveMQException {
      Objects.requireNonNull(bridge);
      methodCalled(BEFORE_DELIVER_BRIDGE);
   }

   @Override
   public void afterDeliverBridge(Bridge bridge, MessageReference ref, HandleStatus status) throws ActiveMQException {
      Objects.requireNonNull(bridge);
      methodCalled(AFTER_DELIVER_BRIDGE);
   }

   @Override
   public void afterAcknowledgeBridge(Bridge bridge, MessageReference ref) throws ActiveMQException {
      Objects.requireNonNull(bridge);
      methodCalled(AFTER_ACKNOWLEDGE_BRIDGE);
   }

   @Override
   public void federationStreamStarted(FederationStream stream) throws ActiveMQException {
      Objects.requireNonNull(stream);
      methodCalled(FEDERATION_STREAM_STARTED);
   }

   @Override
   public void federationStreamStopped(FederationStream stream) throws ActiveMQException {
      Objects.requireNonNull(stream);
      methodCalled(FEDERATION_STREAM_STOPPED);
   }

   @Override
   public void beforeCreateFederatedQueueConsumer(FederatedConsumerKey key) throws ActiveMQException {
      Objects.requireNonNull(key);
      methodCalled(BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER);
   }

   @Override
   public void afterCreateFederatedQueueConsumer(FederatedQueueConsumer consumer) throws ActiveMQException {
      Objects.requireNonNull(consumer);
      methodCalled(AFTER_CREATE_FEDERATED_QUEUE_CONSUMER);
   }

   @Override
   public void beforeCloseFederatedQueueConsumer(FederatedQueueConsumer consumer) throws ActiveMQException {
      Objects.requireNonNull(consumer);
      methodCalled(BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER);
   }

   @Override
   public void afterCloseFederatedQueueConsumer(FederatedQueueConsumer consumer) throws ActiveMQException {
      Objects.requireNonNull(consumer);
      methodCalled(AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER);
   }

   @Override
   public void beforeFederatedQueueConsumerMessageHandled(FederatedQueueConsumer consumer,
                                                          Message message) throws ActiveMQException {
      Objects.requireNonNull(consumer);
      Objects.requireNonNull(message);
      methodCalled(BEFORE_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED);
   }

   @Override
   public void afterFederatedQueueConsumerMessageHandled(FederatedQueueConsumer consumer,
                                                         Message message) throws ActiveMQException {
      Objects.requireNonNull(consumer);
      Objects.requireNonNull(message);
      methodCalled(AFTER_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED);
   }

   @Override
   public boolean federatedAddressConditionalCreateConsumer(Queue queue) throws ActiveMQException {
      Objects.requireNonNull(queue);
      methodCalled(FEDERATED_ADDRESS_CONDITIONAL_CREATE_CONSUMER);
      return true;
   }

   @Override
   public boolean federatedQueueConditionalCreateConsumer(ServerConsumer consumer) throws ActiveMQException {
      Objects.requireNonNull(consumer);
      methodCalled(FEDERATED_QUEUE_CONDITIONAL_CREATE_CONSUMER);
      return true;
   }

   public void validatePluginMethodsEquals(int count, String... names) {
      validatePluginMethodsEquals(count, Wait.MAX_WAIT_MILLIS, Wait.SLEEP_MILLIS);
   }

   public void validatePluginMethodsEquals(int count, long timeout, long sleepMillis, String... names) {
      Arrays.asList(names).forEach(name -> {
         try {
            Wait.waitFor(() -> count == methodCalls.getOrDefault(name, new AtomicInteger()).get(), timeout, sleepMillis);
         } catch (Throwable ignored) {
         }
         assertEquals(count, methodCalls.getOrDefault(name, new AtomicInteger()).get(), "validating method " + name);
      });
   }


   public void validatePluginMethodsAtLeast(int count, String... names) {
      Arrays.asList(names).forEach(name -> {
         try {
            Wait.waitFor(() -> count <= methodCalls.getOrDefault(name, new AtomicInteger()).get());
         } catch (Exception e) {
            e.printStackTrace();
         }
         assertTrue(count <= methodCalls.getOrDefault(name, new AtomicInteger()).get(), "validating method " + name + " expected less than " + count);
      });
   }

   private void methodCalled(String name) {
      methodCalls.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
   }

}
