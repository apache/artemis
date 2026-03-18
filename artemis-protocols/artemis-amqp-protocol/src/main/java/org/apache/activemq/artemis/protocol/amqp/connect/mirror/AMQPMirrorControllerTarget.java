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
package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import java.util.Collection;
import java.util.HashMap;

import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.OperationConsistencyLevel;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageBrokerAccessor;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonAbstractReceiver;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.pools.MpscPool;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.ADD_ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.BROKER_ID;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.CREATE_QUEUE;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.DELETE_ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.DELETE_QUEUE;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.EVENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.INTERNAL_BROKER_ID_EXTRA_PROPERTY;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.INTERNAL_DESTINATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.INTERNAL_ID;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.POST_ACK;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.QUEUE;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.INTERNAL_ID_EXTRA_PROPERTY;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.TARGET_QUEUES;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.verifyDesiredCapability;

public class AMQPMirrorControllerTarget extends ProtonAbstractReceiver implements MirrorController {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final ThreadLocal<MirrorController> CONTROLLER_THREAD_LOCAL = new ThreadLocal<>();

   public static void setControllerInUse(MirrorController controller) {
      CONTROLLER_THREAD_LOCAL.set(controller);
   }

   public static MirrorController getControllerInUse() {
      return CONTROLLER_THREAD_LOCAL.get();
   }


   @Override
   public boolean isBusy() {
      return getAckManager().size() > getMirrorMaxPendingAcks();
   }

   public int getMirrorMaxPendingAcks() {
      try {
         return connection.getProtocolManager().getMirrorMaxPendingAcks();
      } catch (Throwable e) {
         // It shouldn't happen, but if it did we just log it
         logger.warn(e.getMessage(), e);
         return 0;
      }
   }

   public void verifyCredits() {
      connection.runNow(creditTopUpRunner);
   }


   /**
    * Objects of this class can be used by either transaction or by OperationContext. It is important that when you're
    * using the transactions you clear any references to the operation context. Don't use transaction and
    * OperationContext at the same time as that would generate duplicates on the objects cache.
    */
   class ACKMessageOperation implements IOCallback, Runnable {

      Delivery delivery;

      /**
       * notice that when you use the Transaction, you need to make sure you don't use the IO
       */
      public TransactionOperationAbstract tx = new TransactionOperationAbstract() {
         @Override
         public void afterCommit(Transaction tx) {
            connectionRun();
         }
      };

      void reset() {
         this.delivery = null;
      }

      ACKMessageOperation setDelivery(Delivery delivery) {
         this.delivery = delivery;
         return this;
      }

      @Override
      public void run() {
         if (!connection.isHandler()) {
            logger.info("Moving execution to proton handler");
            connectionRun();
            return;
         }
         logger.trace("Delivery settling for {}, context={}", delivery, delivery.getContext());
         delivery.disposition(Accepted.getInstance());
         settle(delivery);
         connection.flush();
         AMQPMirrorControllerTarget.this.ackMessageMpscPool.release(ACKMessageOperation.this);
      }

      @Override
      public void done() {
         connectionRun();
      }

      public void connectionRun() {
         connection.runNow(ACKMessageOperation.this);
      }

      @Override
      public void onError(int errorCode, String errorMessage) {
         logger.warn("{}-{}", errorCode, errorMessage);
      }
   }

   // in a regular case we should not have more than amqpCredits on the pool, that's the max we would need
   private final MpscPool<ACKMessageOperation> ackMessageMpscPool = new MpscPool<>(connection.getAmqpCredits(), ACKMessageOperation::reset, ACKMessageOperation::new);

   final RoutingContextImpl routingContext = new RoutingContextImpl(null);

   final BasicMirrorController<Receiver> basicController;

   final ActiveMQServer server;

   final Configuration configuration;

   DuplicateIDCache lruduplicateIDCache;
   String lruDuplicateIDKey;

   private final ReferenceIDSupplier referenceNodeStore;

   OperationContext mirrorContext;

   private AckManager ackManager;

   @Override
   public void close(boolean remoteLinkClose) throws ActiveMQAMQPException {
      super.close(remoteLinkClose);
      AckManager localAckManager = ackManager;
      if (localAckManager != null) {
         localAckManager.unregisterMirror(this);
      }
   }

   /**
    * This method will wait both replication and storage to finish their current operations.
    */
   public void flush() {
      CountDownLatch latch = new CountDownLatch(1);
      connection.runNow(() -> {
         OperationContext oldContext = OperationContextImpl.getContext();
         try {
            OperationContextImpl.setContext(mirrorContext);
            mirrorContext.executeOnCompletion(new IOCallback() {
               @Override
               public void done() {
                  latch.countDown();
               }

               @Override
               public void onError(int errorCode, String errorMessage) {
                  // we are not doing any IO here, this is extremely unlikely to happen:
                  logger.warn("IO Error code on flushing OperationContext for AMQPMirrorControllerTarget . error code = {} / message = {}", errorCode, errorMessage);
                  latch.countDown();
               }
            });
         } finally {
            OperationContextImpl.setContext(oldContext);
         }
      });

      long timeout;
      try {
         timeout = connection.getProtocolManager().getAckManagerFlushTimeout();
      } catch (Throwable e) {
         // This is redundant code that should not occur
         // Only real possibility for this would be a Mocking test, or some embedded usage
         logger.warn("Could not access the connection and protocol manager, using a default timeout of 10 seconds for AckManagerFlushTimeout", e);
         timeout = 10_000;
      }

      try {
         if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
            ActiveMQAMQPProtocolLogger.LOGGER.timedOutAckManager(timeout);
         }
      } catch (InterruptedException e) {
         ActiveMQAMQPProtocolLogger.LOGGER.interruptedAckManager(e);
         Thread.currentThread().interrupt();
      }
   }

   public AMQPMirrorControllerTarget(AMQPSessionCallback sessionSPI,
                                     AMQPConnectionContext connection,
                                     AMQPSessionContext protonSession,
                                     Receiver receiver,
                                     ActiveMQServer server) {
      super(sessionSPI, connection, protonSession, receiver);
      this.basicController = new BasicMirrorController(server);
      this.basicController.setLink(receiver);
      this.server = server;
      this.configuration = server.getConfiguration();
      this.referenceNodeStore = sessionSPI.getProtocolManager().getReferenceIDSupplier();
      mirrorContext = protonSession.getSessionSPI().getSessionContext();
   }

   @Override
   public String getRemoteMirrorId() {
      return basicController.getRemoteMirrorId();
   }

   @Override
   protected void doCreditTopUpRun() {
      creditRunnable.run();
   }

   @Override
   protected void actualDelivery(Message message, Delivery delivery, DeliveryAnnotations deliveryAnnotations, Receiver receiver, Transaction tx) {
      OperationContext oldContext = recoverContext();
      incrementSettle();

      logger.trace("{}::actualDelivery call for {}", server, message);
      setControllerInUse(this);

      delivery.setContext(message);

      ACKMessageOperation messageAckOperation = this.ackMessageMpscPool.borrow().setDelivery(delivery);

      try {

         if (message instanceof AMQPMessage amqpMessage) {

            /*
             * We use message annotations, because on the same link we will receive control messages
             *  coming from mirror events,
             *  and the actual messages that need to be replicated.
             *  Using anything from the body would force us to parse the body on regular messages.
             *  The body of the message may still be used on control messages, on cases where a JSON string is sent.
             */
            Object eventType = AMQPMessageBrokerAccessor.getMessageAnnotationProperty(amqpMessage, EVENT_TYPE);
            if (eventType != null) {
               if (eventType.equals(ADD_ADDRESS)) {
                  AddressInfo addressInfo = parseAddress(amqpMessage);

                  addAddress(addressInfo);
               } else if (eventType.equals(DELETE_ADDRESS)) {
                  AddressInfo addressInfo = parseAddress(amqpMessage);

                  deleteAddress(addressInfo);
               } else if (eventType.equals(CREATE_QUEUE)) {
                  QueueConfiguration queueConfiguration = parseQueue(amqpMessage);

                  createQueue(queueConfiguration);
               } else if (eventType.equals(DELETE_QUEUE)) {
                  String address = (String) AMQPMessageBrokerAccessor.getMessageAnnotationProperty(amqpMessage, ADDRESS);
                  String queueName = (String) AMQPMessageBrokerAccessor.getMessageAnnotationProperty(amqpMessage, QUEUE);

                  deleteQueue(SimpleString.of(address), SimpleString.of(queueName), null);
               } else if (eventType.equals(POST_ACK)) {
                  String nodeID = (String) AMQPMessageBrokerAccessor.getMessageAnnotationProperty(amqpMessage, BROKER_ID);

                  logger.trace("ACK towards NodeID = {}, while the localNodeID={}", nodeID, server.getNodeID());

                  AckReason ackReason = AMQPMessageBrokerAccessor.getMessageAnnotationAckReason(amqpMessage);

                  if (nodeID == null) {
                     nodeID = getRemoteMirrorId(); // not sending the nodeID means it's data generated on that broker
                     logger.trace("Replacing nodeID by {}", nodeID);
                  }
                  String queueName = (String) AMQPMessageBrokerAccessor.getMessageAnnotationProperty(amqpMessage, QUEUE);
                  AmqpValue value = (AmqpValue) amqpMessage.getBody();
                  Long messageID = (Long) value.getValue();
                  if (postAcknowledge(queueName, nodeID, messageID, messageAckOperation, ackReason)) {
                     messageAckOperation = null;
                  }
               }
            } else {
               if (sendMessage(amqpMessage, deliveryAnnotations, messageAckOperation)) {
                  // since the send was successful, we give up the reference here,
                  // so there won't be any call on afterCompleteOperations
                  messageAckOperation = null;
               }
            }
         } else {
            if (sendMessage(message, deliveryAnnotations, messageAckOperation)) {
               // since the send was successful, we give up the reference here,
               // so there won't be any call on afterCompleteOperations
               messageAckOperation = null;
            }
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      } finally {
         setControllerInUse(null);
         if (messageAckOperation != null) {
            server.getStorageManager().afterCompleteOperations(messageAckOperation, OperationConsistencyLevel.FULL);
         }

         OperationContextImpl.setContext(oldContext);
      }
   }

   @Override
   public void initialize() throws Exception {
      initialized = true;

      // Match the settlement mode of the remote instead of relying on the default of MIXED.
      receiver.setSenderSettleMode(receiver.getRemoteSenderSettleMode());

      // We don't currently support SECOND so enforce that the answer is always FIRST
      receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);

      final HashMap<Symbol, Object> brokerIDProperties = new HashMap<>();
      brokerIDProperties.put(AMQPMirrorControllerSource.BROKER_ID, server.getNodeID().toString());
      receiver.setProperties(brokerIDProperties);

      // We need to check if the remote desires to send us tunneled core messages or not, and if
      // we support that we need to offer that back so it knows it can actually do core tunneling.
      if (verifyDesiredCapability(receiver, AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT)) {
         receiver.setOfferedCapabilities(new Symbol[] {AMQPMirrorControllerSource.MIRROR_CAPABILITY,
                                                       AmqpSupport.CORE_MESSAGE_TUNNELING_SUPPORT});
         enableCoreTunneling(); // Sender requested so enable receipt here.
      } else {
         receiver.setOfferedCapabilities(new Symbol[]{AMQPMirrorControllerSource.MIRROR_CAPABILITY});
      }

      topUpCreditIfNeeded();
   }

   private QueueConfiguration parseQueue(AMQPMessage message) {
      AmqpValue bodyValue = (AmqpValue) message.getBody();
      String body = (String) bodyValue.getValue();
      return QueueConfiguration.fromJSON(body);
   }

   private AddressInfo parseAddress(AMQPMessage message) {
      AmqpValue bodyValue = (AmqpValue) message.getBody();
      String body = (String) bodyValue.getValue();
      return AddressInfo.fromJSON(body);
   }

   @Override
   public void preAcknowledge(Transaction tx, MessageReference ref, AckReason reason) throws Exception {
      // NO-OP
   }

   @Override
   public void addAddress(AddressInfo addressInfo) throws Exception {
      logger.debug("{} adding address {}", server, addressInfo);

      server.addAddressInfo(addressInfo);
   }

   @Override
   public void deleteAddress(AddressInfo addressInfo) throws Exception {
      logger.debug("{} delete address {}", server, addressInfo);

      try {
         server.removeAddressInfo(addressInfo.getName(), null, true);
      } catch (ActiveMQAddressDoesNotExistException expected) {
         // it was removed from somewhere else, which is fine
         logger.debug(expected.getMessage(), expected);
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

   @Override
   public void createQueue(QueueConfiguration queueConfiguration) throws Exception {
      logger.debug("{} adding queue {}", server, queueConfiguration);

      try {
         server.createQueue(queueConfiguration, true);
      } catch (Exception e) {
         logger.debug("Queue could not be created, already existed {}", queueConfiguration, e);
      }
   }

   @Override
   public void deleteQueue(SimpleString addressName, SimpleString queueName, QueueConfiguration configuration) throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("{} destroy queue {} on address = {} server {}", server, queueName, addressName, server.getIdentity());
      }
      try {
         server.destroyQueue(queueName, null, false, true, false, false);
      } catch (ActiveMQNonExistentQueueException expected) {
         if (logger.isDebugEnabled()) {
            logger.debug("{} queue {} was previously removed", server, queueName, expected);
         }
      }
   }

   public boolean postAcknowledge(String queue,
                                  String nodeID,
                                  long messageID,
                                  ACKMessageOperation ackMessage,
                                  AckReason reason) throws Exception {
      final Queue targetQueue = server.locateQueue(queue);

      if (targetQueue == null) {
         logger.warn("Queue {} not found on mirror target, ignoring ack for queue={}, messageID={}, nodeID={}", queue, queue, messageID, nodeID);
         return false;
      }

      if (logger.isDebugEnabled()) {
         // we only do the following check if debug
         if (targetQueue.getConsumerCount() > 0) {
            logger.debug("server {}, queue {} has consumers while delivering ack for {}", server.getIdentity(), targetQueue.getName(), messageID);
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Server {} with queue = {} being acked for {} from {} targetQueue = {} reason = {}",
                      server.getIdentity(), queue, messageID, ackMessage, targetQueue, reason);
      }

      performAck(nodeID, targetQueue, messageID, ackMessage, reason);
      return true;
   }

   private void performAck(String nodeID,
                           Queue targetQueue,
                           long messageID,
                           ACKMessageOperation ackMessageOperation, AckReason reason) {

      if (logger.isTraceEnabled()) {
         logger.trace("performAck (nodeID={}, messageID={}), targetQueue={})", nodeID, messageID, targetQueue.getName());
      }

      getAckManager().ack(nodeID, targetQueue, messageID, reason, true);

      OperationContextImpl.getContext().executeOnCompletion(ackMessageOperation, OperationConsistencyLevel.FULL);
   }

   private AckManager getAckManager() {
      if (ackManager == null) {
         ackManager = AckManagerProvider.getManager(server);
         ackManager.registerMirror(this);
      }
      return ackManager;
   }

   /**
    * this method returning true means the sendMessage was successful, and the IOContext should no longer be used. as
    * the sendMessage was successful the OperationContext of the transaction will take care of the completion. The
    * caller of this method should give up any reference to messageCompletionAck when this method returns {@code true}.
    */
   private boolean sendMessage(Message message, DeliveryAnnotations deliveryAnnotations, ACKMessageOperation messageCompletionAck) throws Exception {
      if (message.getMessageID() <= 0) {
         message.setMessageID(server.getStorageManager().generateID());
      }

      String internalMirrorID = (String) deliveryAnnotations.getValue().get(BROKER_ID);
      if (internalMirrorID == null) {
         internalMirrorID = getRemoteMirrorId(); // not passing the ID means the data was generated on the remote broker
      }
      Long internalIDLong = (Long) deliveryAnnotations.getValue().get(INTERNAL_ID);
      String internalAddress = (String) deliveryAnnotations.getValue().get(INTERNAL_DESTINATION);

      Collection<String> targetQueues = (Collection<String>) deliveryAnnotations.getValue().get(TARGET_QUEUES);

      long internalID = 0;

      if (internalIDLong != null) {
         internalID = internalIDLong;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("sendMessage on server {} for message {} with internalID = {} mirror id {}", server, message, internalIDLong, internalMirrorID);
      }

      routingContext.setDuplicateDetection(false); // we do our own duplicate detection here

      DuplicateIDCache duplicateIDCache;
      if (lruDuplicateIDKey == null || !lruDuplicateIDKey.equals(internalMirrorID)) {
         // we use the number of credits for the duplicate detection, as that means the maximum number of elements you can have pending
         logger.trace("Setting up duplicate detection cache on {}, ServerID={} with {} elements, being the number of credits", ProtonProtocolManager.MIRROR_ADDRESS, internalMirrorID, connection.getAmqpCredits());

         lruDuplicateIDKey = internalMirrorID;
         lruduplicateIDCache = server.getPostOffice().getDuplicateIDCache(SimpleString.of(ProtonProtocolManager.MIRROR_ADDRESS + "_" + internalMirrorID), connection.getAmqpCredits());
      }
      duplicateIDCache = lruduplicateIDCache;

      byte[] duplicateIDBytes = ByteUtil.longToBytes(internalIDLong);

      if (duplicateIDCache.contains(duplicateIDBytes)) {
         message.usageDown(); // large messages would be removed here
         topUpCreditIfNeeded();
         return false;
      }

      message.setBrokerProperty(INTERNAL_ID_EXTRA_PROPERTY, internalID);
      message.setBrokerProperty(INTERNAL_BROKER_ID_EXTRA_PROPERTY, internalMirrorID);

      if (internalAddress != null) {
         message.setAddress(internalAddress);
      }

      // notice that MirrorTransaction is overriding getRequiredConsistency that is being set to ignore Replication.
      // that means in case the target server is using replication, we will not wait for a roundtrip before the message is sent
      // however we will wait the roundtrip before acking the message
      // This is to alleviate a situation where messages would take too long to be delivered and be ready for ack
      final TransactionImpl transaction = new MirrorTransaction(server.getStorageManager()).setAllowPageTransaction(configuration.isMirrorPageTransaction()).setAsync(true);
      routingContext.setTransaction(transaction);
      duplicateIDCache.addToCache(duplicateIDBytes, transaction);

      routingContext.clear().setMirrorSource(this).setLoadBalancingType(MessageLoadBalancingType.LOCAL_ONLY).disableDivert();
      if (targetQueues != null) {
         targetQueuesRouting(message, routingContext, targetQueues);
         server.getPostOffice().processRoute(message, routingContext, false);
      } else {
         server.getPostOffice().route(message, routingContext, false);
      }
      // We use this as part of a transaction because of the duplicate detection cache that needs to be done atomically
      transaction.commit();
      server.getStorageManager().afterCompleteOperations(messageCompletionAck, OperationConsistencyLevel.FULL);
      topUpCreditIfNeeded();

      // return true here will instruct the caller to ignore any references to messageCompletionAck
      return true;
   }

   /**
    * When the source mirror receives messages from a cluster member of his own, it should then fill targetQueues so we
    * could play the same semantic the source applied on its routing
    */
   private void targetQueuesRouting(final Message message,
                                    final RoutingContext context,
                                    final Collection<String> queueNames) throws Exception {
      Bindings bindings = server.getPostOffice().getBindingsForAddress(message.getAddressSimpleString());
      queueNames.forEach(name -> {
         Binding binding = bindings.getBinding(name);
         if (binding != null) {
            try {
               binding.route(message, context);
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }
      });
   }

   @Override
   public void postAcknowledge(MessageReference ref, AckReason reason) {
      // Do nothing
   }

   @Override
   public void sendMessage(Transaction tx, Message message, RoutingContext context) {
      // Do nothing
   }

   @Override
   protected SimpleString getAddressInUse() {
      // We are not using server session level flow control here so this is unused.
      return null;
   }
}