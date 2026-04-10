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
package org.apache.activemq.artemis.protocol.amqp.broker;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.AutoCreateResult;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPResourceLimitExceededException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerReceiverContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.transaction.ProtonTransactionHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.SASLResult;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.SelectorTranslator;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.runnables.RunnableList;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transaction.TransactionalState;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.getReceiverPriority;

public class AMQPSessionCallback implements SessionCallback {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final IDGenerator consumerIDGenerator = new SimpleIDGenerator(0);

   private final AMQPConnectionCallback protonSPI;

   private final ProtonProtocolManager manager;

   private final StorageManager storageManager;

   private final AMQPConnectionContext connection;

   private final Connection transportConnection;

   private ServerSession serverSession;

   private final OperationContext operationContext;

   private AMQPSessionContext protonSession;

   private final Executor sessionExecutor;

   private final boolean directDeliver;

   private final CoreMessageObjectPools coreMessageObjectPools = new CoreMessageObjectPools();

   private ProtonTransactionHandler transactionHandler;

   private final RunnableList blockedRunnables = new RunnableList();

   public AMQPSessionCallback(AMQPConnectionCallback protonSPI,
                              ProtonProtocolManager manager,
                              AMQPConnectionContext connection,
                              Connection transportConnection,
                              Executor executor,
                              OperationContext operationContext) {
      this.protonSPI = protonSPI;
      this.manager = manager;
      this.storageManager = manager.getServer().getStorageManager();
      this.connection = connection;
      this.transportConnection = transportConnection;
      this.sessionExecutor = executor;
      this.operationContext = operationContext;
      this.directDeliver = manager.isDirectDeliver();
   }

   public StorageManager getStorageManager() {
      return storageManager;
   }

   public CoreMessageObjectPools getCoreMessageObjectPools() {
      return coreMessageObjectPools;
   }

   public AMQPSessionContext getAMQPSessionContext() {
      return protonSession;
   }

   public ProtonProtocolManager getProtocolManager() {
      return manager;
   }

   @Override
   public boolean isWritable(ReadyListener callback, Object protocolContext) {
      ProtonServerSenderContext senderContext = (ProtonServerSenderContext) protocolContext;
      return transportConnection.isWritable(callback) && senderContext.getSender().getLocalState() != EndpointState.CLOSED;
   }

   public void withinSessionExecutor(Runnable run) {
      sessionExecutor.execute(() -> {
         try {
            withinContext(run);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      });
   }

   public void withinContext(Runnable run) throws Exception {
      OperationContext context = recoverContext();
      try {
         run.run();
      } finally {
         resetContext(context);
      }
   }

   public void execute(Runnable run) {
      sessionExecutor.execute(run);
   }

   public void afterIO(IOCallback ioCallback) {
      OperationContext context = recoverContext();
      try {
         manager.getServer().getStorageManager().afterCompleteOperations(ioCallback);
      } finally {
         resetContext(context);
      }
   }

   public OperationContext getSessionContext() {
      return serverSession.getSessionContext();
   }

   @Override
   public void browserFinished(ServerConsumer consumer) {

   }

   @Override
   public boolean supportsDirectDelivery() {
      return manager.isDirectDeliver();
   }

   public void init(AMQPSessionContext protonSession, SASLResult saslResult) throws Exception {

      this.protonSession = protonSession;

      String name = UUIDGenerator.getInstance().generateStringUUID();

      if (connection.isBrokerConnection())  {
         serverSession = manager.getServer().createInternalSession(name, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, protonSPI.getProtonConnectionDelegate(), // RemotingConnection remotingConnection,
                                                           false, // boolean autoCommitSends
                                                           false, // boolean autoCommitAcks,
                                                           false, // boolean preAcknowledge,
                                                           true, //boolean xa,
                                                           null, this, true, operationContext, manager.getPrefixes(), manager.getSecurityDomain(), false);
      } else {
         serverSession = manager.getServer().createSession(name, connection.getUser(), connection.getPassword(), ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, protonSPI.getProtonConnectionDelegate(), // RemotingConnection remotingConnection,
                                                           false, // boolean autoCommitSends
                                                           false, // boolean autoCommitAcks,
                                                           false, // boolean preAcknowledge,
                                                           true, //boolean xa,
                                                           null, this, true, operationContext, manager.getPrefixes(), manager.getSecurityDomain(), connection.getValidatedUser(), false);
      }
   }

   @Override
   public void afterDelivery() throws Exception {

   }

   public void start() {

   }

   /**
    * Creates a server consume that reads from the given named queue and forwards the read messages to the AMQP sender
    * to dispatch to the remote peer. The consumer priority value is extracted from the remote link properties that were
    * assigned by the remote receiver.
    *
    * @param protonSender The {@link ProtonServerReceiverContext} that will be attached to the resulting consumer
    * @param queue        The target queue that the consumer reads from.
    * @param filter       The filter assigned to the consumer of the target queue.
    * @param browserOnly  Should the consumer act as a browser on the target queue.
    * @return a new {@link ServerConsumer} attached to the given queue
    * @throws Exception if an error occurs while creating the consumer instance.
    */
   public ServerConsumer createSender(ProtonServerSenderContext protonSender,
                                      SimpleString queue,
                                      String filter,
                                      boolean browserOnly) throws Exception {
      return createSender(protonSender, queue, filter, browserOnly, getReceiverPriority(protonSender.getSender().getRemoteProperties()));
   }

   /**
    * Creates a server consume that reads from the given named queue and forwards the read messages to the AMQP sender
    * to dispatch to the remote peer.
    *
    * @param protonSender The {@link ProtonServerReceiverContext} that will be attached to the resulting consumer
    * @param queue        The target queue that the consumer reads from.
    * @param filter       The filter assigned to the consumer of the target queue.
    * @param browserOnly  Should the consumer act as a browser on the target queue.
    * @param priority     The priority to assign the new consumer (server defaults are used if not set).
    * @return a new {@link ServerConsumer} attached to the given queue
    * @throws Exception if an error occurs while creating the consumer instance.
    */
   public ServerConsumer createSender(ProtonServerSenderContext protonSender,
                                      SimpleString queue,
                                      String filter,
                                      boolean browserOnly,
                                      Number priority) throws Exception {
      final long consumerID = consumerIDGenerator.generateID();
      final SimpleString filterString = SimpleString.of(SelectorTranslator.convertToActiveMQFilterString(filter));
      final int consumerPriority = priority != null ? priority.intValue() : ActiveMQDefaultConfiguration.getDefaultConsumerPriority();
      final ServerConsumer consumer = serverSession.createConsumer(
         consumerID, queue, filterString, consumerPriority, browserOnly, false, null);

      // AMQP handles its own flow control for when it's started
      consumer.setStarted(true);
      consumer.setProtocolContext(protonSender);

      return consumer;
   }

   public void startSender(Object brokerConsumer) throws Exception {
      ServerConsumer serverConsumer = (ServerConsumer) brokerConsumer;
      // flow control is done at proton
      serverConsumer.receiveCredits(-1);
   }

   public void createTemporaryQueue(SimpleString queueName, RoutingType routingType) throws Exception {
      createTemporaryQueue(queueName, queueName, routingType, null, null);
   }

   public void createTemporaryQueue(SimpleString queueName, RoutingType routingType, Integer maxConsumers) throws Exception {
      createTemporaryQueue(queueName, queueName, routingType, null, maxConsumers);
   }

   public void createTemporaryQueue(SimpleString queueName, RoutingType routingType, Integer maxConsumers, Boolean internal) throws Exception {
      createTemporaryQueue(queueName, queueName, routingType, null, maxConsumers, internal);
   }

   public void createTemporaryQueue(SimpleString address,
                                    SimpleString queueName,
                                    RoutingType routingType,
                                    SimpleString filter) throws Exception {
      createTemporaryQueue(address, queueName, routingType, filter, null, null);
   }

   public void createTemporaryQueue(SimpleString address,
                                    SimpleString queueName,
                                    RoutingType routingType,
                                    SimpleString filter,
                                    Integer maxConsumers) throws Exception {
      createTemporaryQueue(address, queueName, routingType, filter, null, null);
   }

   public void createTemporaryQueue(SimpleString address,
                                    SimpleString queueName,
                                    RoutingType routingType,
                                    SimpleString filter,
                                    Integer maxConsumers,
                                    Boolean internal) throws Exception {
      try {
         serverSession.createQueue(QueueConfiguration.of(queueName).setAddress(address)
                                                                   .setRoutingType(routingType)
                                                                   .setFilterString(filter)
                                                                   .setTemporary(true)
                                                                   .setDurable(false)
                                                                   .setMaxConsumers(maxConsumers)
                                                                   .setInternal(internal));
      } catch (ActiveMQSecurityException se) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingTempDestination(se.getMessage());
      }
   }

   public void createTemporaryAddress(SimpleString address) throws Exception {
      try {
         serverSession.createAddress(new AddressInfo(address, RoutingType.MULTICAST).setTemporary(true), false);
      } catch (ActiveMQSecurityException se) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingTempDestination(se.getMessage());
      }
   }

   public void createUnsharedDurableQueue(SimpleString address,
                                          RoutingType routingType,
                                          SimpleString queueName,
                                          SimpleString filter) throws Exception {
      try {
         serverSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(routingType).setFilterString(filter).setMaxConsumers(1));
      } catch (ActiveMQSecurityException se) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(se.getMessage());
      }
   }

   public void createSharedDurableQueue(SimpleString address,
                                        RoutingType routingType,
                                        SimpleString queueName,
                                        SimpleString filter) throws Exception {
      try {
         serverSession.createSharedQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(routingType).setFilterString(filter));
      } catch (ActiveMQQueueExistsException alreadyExists) {
         // nothing to be done.. just ignore it. if you have many consumers all doing the same another one probably already done it
      } catch (ActiveMQSecurityException se) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(se.getMessage());
      }
   }

   public void createSharedVolatileQueue(SimpleString address,
                                         RoutingType routingType,
                                         SimpleString queueName,
                                         SimpleString filter) throws Exception {
      try {
         serverSession.createSharedQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(routingType).setFilterString(filter).setDurable(false));
      } catch (ActiveMQSecurityException se) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.securityErrorCreatingConsumer(se.getMessage());
      } catch (ActiveMQQueueExistsException e) {
         // ignore as may be caused by multiple, concurrent clients
      }
   }

   public QueueQueryResult queueQuery(QueueConfiguration configuration, boolean autoCreate) throws Exception {
      QueueQueryResult queueQueryResult = serverSession.executeQueueQuery(configuration.getName());

      if (!queueQueryResult.isExists() && queueQueryResult.isAutoCreateQueues() && autoCreate) {
         try {
            serverSession.createQueue(configuration.setAutoCreated(true));
         } catch (ActiveMQQueueExistsException e) {
            // The queue may have been created by another thread in the mean time.  Catch and do nothing.
         }
         queueQueryResult = serverSession.executeQueueQuery(configuration.getName());
      }

      // if auto-create we will return whatever type was used before
      if (queueQueryResult.isExists() && !queueQueryResult.isAutoCreated()) {
         final RoutingType desiredRoutingType = configuration.getRoutingType();
         if (desiredRoutingType != null && queueQueryResult.getRoutingType() != desiredRoutingType) {
            throw new IllegalStateException("Incorrect Routing Type for queried queue " + configuration.getName() +
                                            ", expecting: " + desiredRoutingType + " while the actual type was: " +
                                            queueQueryResult.getRoutingType());
         }
      }

      return queueQueryResult;
   }

   public QueueQueryResult queueQuery(SimpleString queueName, RoutingType routingType, boolean autoCreate) throws Exception {
      return queueQuery(queueName, routingType, autoCreate, null);
   }

   public QueueQueryResult queueQuery(SimpleString queueName, RoutingType routingType, boolean autoCreate, SimpleString filter) throws Exception {
      return queueQuery(QueueConfiguration.of(queueName).setRoutingType(routingType).setFilterString(filter), autoCreate);
   }

   public boolean checkAddressAndAutocreateIfPossible(SimpleString address, RoutingType routingType) throws Exception {
      AutoCreateResult autoCreateResult = serverSession.checkAutoCreate(QueueConfiguration.of(address).setRoutingType(routingType));
      return autoCreateResult != AutoCreateResult.NOT_FOUND;
   }

   public AddressQueryResult addressQuery(SimpleString addressName,
                                          RoutingType routingType,
                                          boolean autoCreate) throws Exception {

      AddressQueryResult addressQueryResult = serverSession.executeAddressQuery(addressName);

      if (!addressQueryResult.isExists() && addressQueryResult.isAutoCreateAddresses() && autoCreate) {
         try {
            serverSession.createAddress(addressName, routingType, true);
         } catch (ActiveMQQueueExistsException e) {
            // The queue may have been created by another thread in the mean time.  Catch and do nothing.
         }

         addressQueryResult = serverSession.executeAddressQuery(addressName);
      }

      return addressQueryResult;
   }

   public SimpleString removePrefix(SimpleString address) {
      return serverSession.removePrefix(address);
   }

   public void closeSender(final Object brokerConsumer) throws Exception {
      final ServerConsumer consumer = ((ServerConsumer) brokerConsumer);
      consumer.close(false);
      consumer.getQueue().recheckRefCount(serverSession.getSessionContext());
   }

   public String tempQueueName() {
      return UUIDGenerator.getInstance().generateStringUUID();
   }

   public void close() throws Exception {
      blockedRunnables.cancel();
      //need to check here as this can be called if init fails
      if (serverSession != null) {
         // we cannot hold the nettyExecutor on this rollback here, otherwise other connections will be waiting
         sessionExecutor.execute(() -> {
            OperationContext context = recoverContext();
            try {
               try {
                  serverSession.close(false);
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               }
            } finally {
               resetContext(context);
            }
         });
      }
   }

   @Override
   public void close(boolean failed) {
      if (protonSession != null) {
         protonSession.close();
      }
   }

   public void ack(Transaction transaction, Object brokerConsumer, Message message) throws Exception {
      if (transaction == null) {
         transaction = serverSession.getCurrentTransaction();
      }
      OperationContext oldContext = recoverContext();
      try {
         ((ServerConsumer) brokerConsumer).individualAcknowledge(transaction, message.getMessageID());
      } finally {
         resetContext(oldContext);
      }
   }

   public void cancel(Object brokerConsumer, Message message, boolean updateCounts) throws Exception {
      OperationContext oldContext = recoverContext();
      try {
         ((ServerConsumer) brokerConsumer).individualCancel(message.getMessageID(), updateCounts);
         ((ServerConsumer) brokerConsumer).getQueue().forceDelivery();
      } finally {
         resetContext(oldContext);
      }
   }

   public void reject(Object brokerConsumer, Message message) throws Exception {
      OperationContext oldContext = recoverContext();
      try {
         ((ServerConsumer) brokerConsumer).reject(message.getMessageID());
      } finally {
         resetContext(oldContext);
      }
   }

   public void resumeDelivery(Object consumer) {
      ((ServerConsumer) consumer).receiveCredits(-1);
   }

   public AMQPStandardMessage createStandardMessage(Delivery delivery, ReadableBuffer data) {
      return new AMQPStandardMessage(delivery.getMessageFormat(), data, null, coreMessageObjectPools);
   }

   public void serverSend(ProtonServerReceiverContext context,
                          Transaction transaction,
                          Receiver receiver,
                          Delivery delivery,
                          SimpleString address,
                          RoutingType routingType,
                          RoutingContext routingContext,
                          Message message) throws Exception {

      context.incrementSettle();

      final SimpleString messageAddress = message.getAddressSimpleString();
      final SimpleString selectedAddress;

      if (!context.isAnonymousRelay()) {
         // set Fixed-address producer if the message.properties.to address differs from the producer
         if (!address.equals(messageAddress)) {
            message.setAddress(address);
         }

         selectedAddress = address;
      } else if (messageAddress == null) {
         // Anonymous-relay producer, message must carry a To value
         // Errors are not currently handled as required by AMQP 1.0 anonterm-v1.0
         rejectMessage(context, delivery, Symbol.valueOf("failed"), "Missing 'to' field for message sent to an anonymous producer");
         return;
      } else {
         selectedAddress = messageAddress;
      }

      if (!checkAddressAndAutocreateIfPossible(selectedAddress, routingType)) {
         ActiveMQException e = ActiveMQAMQPProtocolMessageBundle.BUNDLE.addressDoesntExist(selectedAddress.toString());
         if (transaction != null) {
            transaction.markAsRollbackOnly(e);
         }
         throw e;
      }

      OperationContext oldcontext = recoverContext();

      try {
         PagingStore store = manager.getServer().getPagingManager().getPageStore(selectedAddress);
         if (store != null && store.isRejectingMessages()) {
            // We drop pre-settled messages (and abort any associated Tx)
            String amqpAddress = delivery.getLink().getTarget().getAddress();
            ActiveMQException e = new ActiveMQAMQPResourceLimitExceededException("Address is full: " + amqpAddress);
            if (delivery.remotelySettled()) {
               if (transaction != null) {
                  transaction.markAsRollbackOnly(e);
               }
            } else {
               throw e;
            }
         } else {
            message.setConnectionID(receiver.getSession().getConnection().getRemoteContainer());
            // We need to transfer IO execution to a different thread otherwise we may deadlock netty loop
            sessionExecutor.execute(() -> inSessionSend(context, transaction, message, selectedAddress, routingType, delivery, receiver, routingContext));
         }
      } catch (Exception e) {
         onSendFailed(message, transaction, e);
         throw e;
      } finally {
         resetContext(oldcontext);
      }
   }

   private void rejectMessage(final ProtonServerReceiverContext context, Delivery delivery, Symbol errorCondition, String errorMessage) {
      ErrorCondition condition = new ErrorCondition();
      condition.setCondition(errorCondition);
      condition.setDescription(errorMessage);
      Rejected rejected = new Rejected();
      rejected.setError(condition);

      afterIO(new IOCallback() {
         @Override
         public void done() {
            connection.runNow(() -> {
               delivery.disposition(rejected);
               context.settle(delivery);
               connection.flush();
            });
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      });
   }

   private void inSessionSend(final ProtonServerReceiverContext context,
                              final Transaction transaction,
                              final Message message,
                              final SimpleString address,
                              final RoutingType routingType,
                              final Delivery delivery,
                              final Receiver receiver,
                              final RoutingContext routingContext) {
      OperationContext oldContext = recoverContext();
      try {
         if (invokeIncoming(message, (ActiveMQProtonRemotingConnection) transportConnection.getProtocolConnection()) == null) {
            serverSession.send(transaction, address, routingType, message, directDeliver, receiver.getName(), false, routingContext);

            afterIO(new IOCallback() {
               @Override
               public void done() {
                  connection.runNow(() -> {
                     if (delivery.getRemoteState() instanceof TransactionalState) {
                        TransactionalState txAccepted = new TransactionalState();
                        txAccepted.setOutcome(Accepted.getInstance());
                        txAccepted.setTxnId(((TransactionalState) delivery.getRemoteState()).getTxnId());

                        delivery.disposition(txAccepted);
                     } else {
                        delivery.disposition(Accepted.getInstance());
                     }
                     context.settle(delivery);
                     connection.flush();
                  });
               }

               @Override
               public void onError(int errorCode, String errorMessage) {
                  sendError(errorCode, errorMessage, receiver);
               }
            });
         } else {
            rejectMessage(context, delivery, Symbol.valueOf("failed"), "Interceptor rejected message");
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         onSendFailed(message, transaction, e);
         context.deliveryFailed(delivery, receiver, e);
      } finally {
         resetContext(oldContext);
      }
   }

   private void onSendFailed(Message message, Transaction transaction, Exception cause) {
      if (message.isLargeMessage()) {
         try {
            ((LargeServerMessage) message).deleteFile();
         } catch (Exception e1) {
            logger.warn("Error while deleting undelivered large AMQP message: {}", cause.getMessage());
         }
      }

      if (transaction != null) {
         if (cause instanceof ActiveMQException activeMQException) {
            transaction.markAsRollbackOnly(activeMQException);
         } else {
            transaction.markAsRollbackOnly(
               new ActiveMQInternalErrorException("Delivery failure triggered TXN to be marked as rollback only", cause));
         }
      }
   }

   private void sendError(int errorCode, String errorMessage, Receiver receiver) {
      connection.runNow(() -> {
         receiver.setCondition(new ErrorCondition(AmqpError.ILLEGAL_STATE, errorCode + ":" + errorMessage));
         connection.flush();
      });
   }

   /**
    * Will execute a Runnable on an Address when there's space in memory
    */
   public void flow(final SimpleString address,
                    Runnable runnable) {
      try {

         if (address == null) {
            PagingManager pagingManager = manager.getServer().getPagingManager();
            if (manager != null && manager.getServer() != null &&
                manager.getServer().getAddressSettingsRepository() != null &&
                manager.getServer().getAddressSettingsRepository().getMatch("#").getAddressFullMessagePolicy().equals(AddressFullMessagePolicy.PAGE)) {
               // If it's paging, we only check for disk full
               pagingManager.checkStorage(runnable);
            } else {
               pagingManager.checkMemory(runnable);
            }
         } else {
            final PagingStore store = manager.getServer().getPagingManager().getPageStore(address);
            if (store != null) {
               store.checkMemory(runnable, blockedRunnables::add);
            } else {
               runnable.run();
            }
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public void deleteQueue(SimpleString queueName) throws Exception {
      serverSession.deleteQueue(queueName);
   }

   public void resetContext(OperationContext oldContext) {
      storageManager.setContext(oldContext);
   }

   /**
    * Set the proper operation context in the Thread Local.
    *  Return the old context*/
   public OperationContext recoverContext() {
      OperationContext oldContext = storageManager.getContext();
      manager.getServer().getStorageManager().setContext(serverSession.getSessionContext());
      return oldContext;
   }

   @Override
   public void sendProducerCreditsMessage(int credits, SimpleString address) {
   }

   @Override
   public boolean updateDeliveryCountAfterCancel(ServerConsumer consumer, MessageReference ref, boolean failed) {
      return false;
   }

   @Override
   public void sendProducerCreditsFailMessage(int credits, SimpleString address) {
   }

   @Override
   public int sendMessage(MessageReference ref, ServerConsumer consumer, int deliveryCount) {

      ProtonServerSenderContext plugSender = (ProtonServerSenderContext) consumer.getProtocolContext();

      try {
         return plugSender.deliverMessage(ref, consumer);
      } catch (Exception e) {
         connection.runNow(() -> {
            plugSender.getSender().setCondition(new ErrorCondition(AmqpError.INTERNAL_ERROR, e.getMessage()));
            connection.flush();
         });
         throw new IllegalStateException("Can't deliver message " + e, e);
      }

   }

   @Override
   public int sendLargeMessage(MessageReference ref,
                               ServerConsumer consumer,
                               long bodySize,
                               int deliveryCount) {
      return 0;
   }

   @Override
   public int sendLargeMessageContinuation(ServerConsumer consumer,
                                           byte[] body,
                                           boolean continues,
                                           boolean requiresResponse) {
      return 0;
   }

   @Override
   public void closed() {
   }

   @Override
   public void disconnect(ServerConsumer consumer, String errorMessage) {
      ErrorCondition ec = new ErrorCondition(AmqpSupport.RESOURCE_DELETED, errorMessage);
      connection.runNow(() -> {
         try {
            ((ProtonServerSenderContext) consumer.getProtocolContext()).close(ec);
            connection.flush();
         } catch (ActiveMQAMQPException e) {
            logger.error("Error closing link for {}", consumer.getQueue().getAddress());
         }
      });
   }

   @Override
   public boolean hasCredits(ServerConsumer consumer) {
      ProtonServerSenderContext plugSender = (ProtonServerSenderContext) consumer.getProtocolContext();

      if (plugSender != null) {
         return plugSender.hasCredits();
      } else {
         return false;
      }
   }

   @Override
   public Transaction getCurrentTransaction() {
      if (this.transactionHandler != null) {
         return this.transactionHandler.getCurrentTransaction();
      }
      return null;
   }

   /**
    * Adds key / value based metadata into the underlying server session implementation for use by the connection
    * resources.
    *
    * @param key   The key to add into the linked server session.
    * @param value The value to add into the linked server session attached to the given key.
    * @return this {@link AMQPSessionCallback} instance
    * @throws Exception if an error occurs while adding the metadata.
    */
   public AMQPSessionCallback addMetaData(String key, String value) throws Exception {
      serverSession.addMetaData(key, value);
      return this;
   }

   public Transaction getTransaction(Binary txid, boolean remove) throws ActiveMQAMQPException {
      return protonSPI.getTransaction(txid, remove);
   }

   public Binary newTransaction() {
      return protonSPI.newTransaction();
   }

   public SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) throws Exception {
      return serverSession.getMatchingQueue(address, routingType);
   }

   public AddressInfo getAddress(SimpleString address) {
      return serverSession.getAddress(address);
   }

   public void removeTemporaryQueue(SimpleString address) throws Exception {
      serverSession.deleteQueue(address);
   }

   public void removeTemporaryAddress(SimpleString address) throws Exception {
      serverSession.deleteAddress(address);
   }

   public RoutingType getDefaultRoutingType(SimpleString address) {
      return manager.getServer().getAddressSettingsRepository().getMatch(address.toString()).getDefaultAddressRoutingType();
   }

   public RoutingType getRoutingTypeFromPrefix(SimpleString address, RoutingType defaultRoutingType) {
      return serverSession.getRoutingTypeFromPrefix(address, defaultRoutingType);
   }

   public void check(SimpleString address, CheckType checkType, SecurityAuth session) throws Exception {
      manager.getServer().getSecurityStore().check(address, checkType, session);
   }

   public void check(SimpleString address, SimpleString queue, CheckType checkType, SecurityAuth session) throws Exception {
      manager.getServer().getSecurityStore().check(address, queue, checkType, session);
   }

   public String invokeIncoming(Message message, ActiveMQProtonRemotingConnection connection) {
      return protonSPI.invokeIncomingInterceptors(message, connection);
   }

   public String invokeOutgoing(Message message, ActiveMQProtonRemotingConnection connection) {
      return protonSPI.invokeOutgoingInterceptors(message, connection);
   }

   public void addProducer(String name, String address) {
      serverSession.addProducer(name, ProtonProtocolManagerFactory.AMQP_PROTOCOL_NAME, address);
   }

   public void removeProducer(String name) {
      serverSession.removeProducer(name);
   }

   public void setTransactionHandler(ProtonTransactionHandler transactionHandler) {
      this.transactionHandler = transactionHandler;
   }

   public Connection getTransportConnection() {
      return transportConnection;
   }

   public ProtonTransactionHandler getTransactionHandler() {
      return this.transactionHandler;
   }

   class AddressQueryCache<T> {
      SimpleString address;
      T result;

      public synchronized T getResult(SimpleString parameterAddress) {
         if (address != null && address.equals(parameterAddress)) {
            return result;
         } else {
            result = null;
            address = null;
            return null;
         }
      }

      public synchronized void setResult(SimpleString parameterAddress, T result) {
         this.address = parameterAddress;
         this.result = result;
      }

   }
   interface CreditRunnable extends Runnable {
      boolean isRun();
   }
}
