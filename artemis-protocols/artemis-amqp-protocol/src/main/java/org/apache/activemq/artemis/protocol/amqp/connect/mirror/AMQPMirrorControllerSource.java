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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.RoutingContext.MirrorOption;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.core.transaction.TransactionPropertyIndexes;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageBrokerAccessor;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnection;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerTarget.getControllerInUse;

public class AMQPMirrorControllerSource extends BasicMirrorController<Sender> implements MirrorController, ActiveMQComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final Symbol EVENT_TYPE = Symbol.getSymbol("x-opt-amq-mr-ev-type");
   public static final Symbol ACK_REASON = Symbol.getSymbol("x-opt-amq-mr-ack-reason");
   public static final Symbol ADDRESS = Symbol.getSymbol("x-opt-amq-mr-adr");
   public static final Symbol QUEUE = Symbol.getSymbol("x-opt-amq-mr-qu");
   public static final Symbol BROKER_ID = Symbol.getSymbol("x-opt-amq-bkr-id");
   public static final SimpleString BROKER_ID_SIMPLE_STRING = SimpleString.of(BROKER_ID.toString());

   // Events:
   public static final Symbol ADD_ADDRESS = Symbol.getSymbol("addAddress");
   public static final Symbol DELETE_ADDRESS = Symbol.getSymbol("deleteAddress");
   public static final Symbol CREATE_QUEUE = Symbol.getSymbol("createQueue");
   public static final Symbol DELETE_QUEUE = Symbol.getSymbol("deleteQueue");
   public static final Symbol POST_ACK = Symbol.getSymbol("postAck");

   // Delivery annotation property used on mirror control routing and Ack
   public static final Symbol INTERNAL_ID = Symbol.getSymbol("x-opt-amq-mr-id");
   public static final Symbol INTERNAL_DESTINATION = Symbol.getSymbol("x-opt-amq-mr-dst");

   /*
    * In a Multi-cast address (or JMS Topics) we may in certain cases (clustered-routing for instance) select which
    * particular queues will receive the routing output
    */
   public static final Symbol TARGET_QUEUES = Symbol.getSymbol("x-opt-amq-mr-trg-q");

   // Capabilities
   public static final Symbol MIRROR_CAPABILITY = Symbol.getSymbol("amq.mirror");
   public static final Symbol QPID_DISPATCH_WAYPOINT_CAPABILITY = Symbol.valueOf("qd.waypoint");

   public static final SimpleString INTERNAL_ID_EXTRA_PROPERTY = SimpleString.of(INTERNAL_ID.toString());
   public static final SimpleString INTERNAL_BROKER_ID_EXTRA_PROPERTY = SimpleString.of(BROKER_ID.toString());

   private static final ThreadLocal<RoutingContext> mirrorControlRouting = ThreadLocal.withInitial(() -> new RoutingContextImpl(null));

   final Queue snfQueue;
   final ActiveMQServer server;
   final ReferenceIDSupplier idSupplier;
   final boolean acks;
   final boolean addQueues;
   final boolean deleteQueues;
   final MirrorAddressFilter addressFilter;
   private final AMQPBrokerConnection brokerConnection;
   private final boolean sync;

   private final PagedRouteContext pagedRouteContext;

   final AMQPMirrorBrokerConnectionElement replicaConfig;

   boolean started;

   TransactionOperation deliveryAsyncTX = new TransactionOperation() {
      @Override
      public void beforePrepare(Transaction tx) throws Exception {
      }

      @Override
      public void afterPrepare(Transaction tx) {
      }

      @Override
      public void beforeCommit(Transaction tx) throws Exception {
      }

      @Override
      public void afterCommit(Transaction tx) {
         snfQueue.deliverAsync();
      }

      @Override
      public void beforeRollback(Transaction tx) throws Exception {
      }

      @Override
      public void afterRollback(Transaction tx) {
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences() {
         return null;
      }

      @Override
      public List<MessageReference> getListOnConsumer(long consumerID) {
         return null;
      }
   };



   @Override
   public void start() throws Exception {
   }

   @Override
   public void stop() throws Exception {
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public AMQPMirrorControllerSource(ReferenceIDSupplier referenceIdSupplier, Queue snfQueue, ActiveMQServer server, AMQPMirrorBrokerConnectionElement replicaConfig,
                                     AMQPBrokerConnection brokerConnection) {
      super(server);
      assert snfQueue != null;
      this.replicaConfig = replicaConfig;
      this.snfQueue = snfQueue;
      if (!snfQueue.isInternalQueue()) {
         logger.debug("marking queue {} as internal to avoid redistribution kicking in", snfQueue.getName());
         snfQueue.setInternalQueue(true); // to avoid redistribution kicking in
      }
      this.server = server;
      this.idSupplier = referenceIdSupplier;
      this.addQueues = replicaConfig.isQueueCreation();
      this.deleteQueues = replicaConfig.isQueueRemoval();
      this.addressFilter = new MirrorAddressFilter(replicaConfig.getAddressFilter());
      this.acks = replicaConfig.isMessageAcknowledgements();
      this.brokerConnection = brokerConnection;
      this.sync = replicaConfig.isSync();
      this.pagedRouteContext = new PagedRouteContext(snfQueue);

      if (sync) {
         logger.debug("Mirror is configured to sync, so pageStore={} being enforced to BLOCK, and not page", snfQueue.getName());
         snfQueue.getPagingStore().enforceAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      } else {
         logger.debug("Mirror is configured to not sync, so pageStore={} being enforced to PAGE", snfQueue.getName());
         snfQueue.getPagingStore().enforceAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      }
   }

   public Queue getSnfQueue() {
      return snfQueue;
   }

   public AMQPBrokerConnection getBrokerConnection() {
      return brokerConnection;
   }

   @Override
   public void addAddress(AddressInfo addressInfo) throws Exception {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      logger.trace("{} addAddress {}", server, addressInfo);

      if (getControllerInUse() != null && !addressInfo.isInternal()) {
         return;
      }

      if (addressInfo.isInternal()) {
         return;
      }

      if (addressInfo.isTemporary()) {
         return;
      }

      if (ignoreAddress(addressInfo.getName())) {
         return;
      }

      if (addQueues) {
         Message message = createMessage(addressInfo.getName(), null, ADD_ADDRESS, null, addressInfo.toJSON());
         routeMirrorCommand(server, message);
      }
   }

   @Override
   public void deleteAddress(AddressInfo addressInfo) throws Exception {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      logger.trace("{} deleteAddress {}", server, addressInfo);

      if (invalidTarget(getControllerInUse()) || addressInfo.isInternal()) {
         return;
      }
      if (ignoreAddress(addressInfo.getName())) {
         return;
      }
      if (deleteQueues) {
         Message message = createMessage(addressInfo.getName(), null, DELETE_ADDRESS, null, addressInfo.toJSON());
         routeMirrorCommand(server, message);
      }
   }

   @Override
   public void createQueue(QueueConfiguration queueConfiguration) throws Exception {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      logger.trace("{} createQueue {}", server, queueConfiguration);

      if (invalidTarget(getControllerInUse()) || queueConfiguration.isInternal()) {
         if (logger.isTraceEnabled()) {
            logger.trace("Rejecting ping pong on create {} as isInternal={} and mirror target = {}", queueConfiguration, queueConfiguration.isInternal(), getControllerInUse());
         }

         return;
      }

      if (queueConfiguration.isTemporary()) {
         return;
      }

      if (ignoreAddress(queueConfiguration.getAddress())) {
         if (logger.isTraceEnabled()) {
            logger.trace("Skipping create {}, queue address {} doesn't match filter", queueConfiguration, queueConfiguration.getAddress());
         }
         return;
      }
      if (addQueues) {
         Message message = createMessage(queueConfiguration.getAddress(), queueConfiguration.getName(), CREATE_QUEUE, null, queueConfiguration.toJSON());
         routeMirrorCommand(server, message);
      }
   }

   @Override
   public void deleteQueue(SimpleString address, SimpleString queue) throws Exception {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      if (logger.isTraceEnabled()) {
         logger.trace("{} deleteQueue {}/{}", server, address, queue);
      }

      if (invalidTarget(getControllerInUse())) {
         return;
      }

      if (ignoreAddress(address)) {
         return;
      }

      if (deleteQueues) {
         Message message = createMessage(address, queue, DELETE_QUEUE, null, queue.toString());
         routeMirrorCommand(server, message);
      }
   }

   private boolean invalidTarget(MirrorController controller, Message message) {
      if (controller == null) {
         return false;
      }
      String remoteID = getRemoteMirrorId();
      if (remoteID == null) {
         // This is to avoid a reflection (Miror sendin messages back to itself) from a small period of time one node reconnects but not the opposite direction.
         Object localRemoteID = message.getAnnotation(BROKER_ID_SIMPLE_STRING);
         if (localRemoteID != null) {
            remoteID = String.valueOf(localRemoteID);
            logger.debug("Remote link is not initialized yet, setting remoteID from message as {}", remoteID);
         }
      }
      return sameNode(remoteID, controller.getRemoteMirrorId());
   }

   private boolean invalidTarget(MirrorController controller) {
      return controller != null && sameNode(getRemoteMirrorId(), controller.getRemoteMirrorId());
   }

   private boolean ignoreAddress(SimpleString address) {
      if (address.startsWith(server.getConfiguration().getManagementAddress())) {
         return true;
      }
      return !addressFilter.match(address);
   }

   private boolean sameNode(String remoteID, String sourceID) {
      return (remoteID != null && sourceID != null && remoteID.equals(sourceID));
   }


   Message copyMessageForPaging(Message message) {
      long newID = server.getStorageManager().generateID();
      long originalID = message.getMessageID();
      if (logger.isTraceEnabled()) {
         logger.trace("copying message {} as {}", originalID, newID);
      }
      message = message.copy(newID, false);
      message.setBrokerProperty(INTERNAL_ID_EXTRA_PROPERTY, originalID);
      return message;
   }


   @Override
   public void sendMessage(Transaction tx, Message message, RoutingContext context) {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      SimpleString address = context.getAddress(message);

      if (context.isInternal()) {
         logger.trace("sendMessage::server {} is discarding send to avoid sending to internal queue", server);
         return;
      }

      if (invalidTarget(context.getMirrorSource(), message)) {
         logger.trace("sendMessage::server {} is discarding send to avoid infinite loop (reflection with the mirror)", server);
         return;
      }

      if (ignoreAddress(address)) {
         logger.trace("sendMessage::server {} is discarding send to address {}, address doesn't match filter", server, address);
         return;
      }

      try {
         context.setReusable(false);

         String nodeID = idSupplier.getServerID(message);

         String remoteID = getRemoteMirrorId();

         if (remoteID == null) {
            if (AMQPMirrorControllerTarget.getControllerInUse() != null) {
               // In case source has not yet connected, we need to take the ID from the Target in use to avoid infinite reflections
               remoteID = AMQPMirrorControllerTarget.getControllerInUse().getRemoteMirrorId();
            }
         }

         if (nodeID != null && nodeID.equals(remoteID)) {
            logger.trace("sendMessage::Message {} already belonged to the node, {}, it won't circle send", message, getRemoteMirrorId());
            return;
         }

         int creditsWrite = snfQueue.getPagingStore().page(message, tx, pagedRouteContext, this::copyMessageForPaging, true);

         // This will store the message on paging, and the message will be copied into paging.
         if (creditsWrite >= 0) {
            snfQueue.getPagingStore().writeFlowControl(creditsWrite);
            if (tx == null) {
               snfQueue.deliverAsync();
            } else {
               if (tx.getProperty(TransactionPropertyIndexes.MIRROR_DELIVERY_ASYNC) == null) {
                  tx.putProperty(TransactionPropertyIndexes.MIRROR_DELIVERY_ASYNC, deliveryAsyncTX);
                  tx.addOperation(deliveryAsyncTX);
               }
            }
            return;
         }

         if (message.isPaged()) {
            // if the source was paged, we copy the message
            // this is because the ACK will happen on different queues.
            // We can only use additional references on the queue when not in page mode.
            // otherwise it must be a copy
            // this will also work better with large messages
            message = copyMessageForPaging(message);
         }

         MessageReference ref = MessageReference.Factory.createReference(message, snfQueue);
         setProtocolData(ref, nodeID, idSupplier.getID(ref), context);

         snfQueue.refUp(ref);

         if (tx != null) {
            logger.debug("sendMessage::Mirroring Message {} with TX", message);
            getSendOperation(tx).addRef(ref);
         } // if non transactional the afterStoreOperations will use the ref directly and call processReferences

         if (sync) {
            OperationContext operContext = OperationContextImpl.getContext(server.getExecutorFactory());
            if (tx == null) {
               // notice that if transactional, the context is lined up on beforeCommit as part of the transaction operation
               operContext.replicationLineUp();
            }
            if (logger.isDebugEnabled()) {
               logger.debug("sendMessage::mirror syncUp context={}, ref={}", operContext, ref);
            }
            ref.setProtocolData(OperationContext.class, operContext);
         }

         if (message.isDurable() && snfQueue.isDurable()) {
            PostOfficeImpl.storeDurableReference(server.getStorageManager(), message, context.getTransaction(), snfQueue, true);
         }

         if (tx == null) {
            server.getStorageManager().afterStoreOperations(new IOCallback() {
               @Override
               public void done() {
                  PostOfficeImpl.processReference(ref, false);
               }

               @Override
               public void onError(int errorCode, String errorMessage) {
               }
            });
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }

      snfQueue.deliverAsync();
   }

   private void syncDone(MessageReference reference) {
      OperationContext ctx = reference.getProtocolData(OperationContext.class);
      if (ctx != null) {
         ctx.replicationDone();
         logger.debug("syncDone::replicationDone::ctx={},ref={}", ctx, reference);
      }  else {
         Message message = reference.getMessage();
         if (message != null) {
            ctx = (OperationContext) message.getUserContext(OperationContext.class);
            if (ctx != null) {
               ctx.replicationDone();
               logger.debug("syncDone::replicationDone message={}", message);
            } else {
               logger.trace("syncDone::No operationContext set on message {}", message);
            }
         } else {
            logger.debug("syncDone::no message set on reference {}", reference);
         }
      }
   }

   public static void validateProtocolData(ReferenceIDSupplier referenceIDSupplier, MessageReference ref, SimpleString snfAddress) {
      if (ref.getProtocolData(DeliveryAnnotations.class) == null && !ref.getMessage().getAddressSimpleString().equals(snfAddress)) {
         logger.trace("validating protocol data, adding protocol data for {}", ref);
         setProtocolData(referenceIDSupplier, ref);
      }
   }

   /**
    * This method will return the brokerID used by the message
    */
   private static String setProtocolData(ReferenceIDSupplier referenceIDSupplier, MessageReference ref) {
      String brokerID = referenceIDSupplier.getServerID(ref);
      long id = referenceIDSupplier.getID(ref);

      setProtocolData(ref, brokerID, id, null);

      return brokerID;
   }

   private static void setProtocolData(MessageReference ref, String brokerID, long id, RoutingContext routingContext) {
      Map<Symbol, Object> daMap = new HashMap<>();
      DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(daMap);

      // getListID will return null when the message was generated on this broker.
      // on this case we do not send the brokerID, and the ControllerTarget will get the information from the link.
      // this is just to safe a few bytes and some processing on the wire.
      if (brokerID != null) {
         // not sending the brokerID, will make the other side to get the brokerID from the remote link's property
         daMap.put(BROKER_ID, brokerID);
      }

      daMap.put(INTERNAL_ID, id);
      String address = ref.getMessage().getAddress();
      if (address != null) { // this is the message that was set through routing
         Properties amqpProperties = getProperties(ref.getMessage());
         if (amqpProperties == null || !address.equals(amqpProperties.getTo())) {
            // We set the internal destination property only if we need to
            // otherwise we just use the one already set over Properties
            daMap.put(INTERNAL_DESTINATION, ref.getMessage().getAddress());
         }
      }

      if (routingContext != null && routingContext.isMirrorIndividualRoute()) {
         List<String> queues = new ArrayList<>();
         routingContext.forEachDurable(q -> queues.add(String.valueOf(q.getName())));
         daMap.put(TARGET_QUEUES, queues);
      }

      ref.setProtocolData(DeliveryAnnotations.class, deliveryAnnotations);
   }

   private static Properties getProperties(Message message) {
      if (message instanceof AMQPMessage pMessage) {
         return AMQPMessageBrokerAccessor.getCurrentProperties(pMessage);
      } else {
         return null;
      }
   }

   private void postACKInternalMessage(MessageReference reference) {
      logger.debug("postACKInternalMessage::server={}, ref={}", server, reference);
      if (sync) {
         syncDone(reference);
      }

      if (reference != null && reference.getQueue() != null && reference.isPaged()) {
         reference.getQueue().deliverAsync();
      }
   }

   @Override
   public void postAcknowledge(MessageReference ref, AckReason reason) throws Exception {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      if (!acks || ref.getQueue().isMirrorController()) {
         postACKInternalMessage(ref);
         return;
      }
      snfQueue.deliverAsync();
   }

   @Override
   public void preAcknowledge(final Transaction tx, final MessageReference ref, final AckReason reason) throws Exception {
      if (!brokerConnection.isEnabled()) {
         return;
      }
      if (logger.isTraceEnabled()) {
         logger.trace("preAcknowledge::tx={}, ref={}, reason={}", tx, ref, reason);
      }

      MirrorController controllerInUse = getControllerInUse();

      // Retried ACKs are not forwarded.
      // This is because they were already confirmed and stored for later ACK which would be happening now
      if (controllerInUse != null && controllerInUse.isRetryACK()) {
         return;
      }

      if (!acks || ref.getQueue().isMirrorController()) { // we don't call preAcknowledge on snfqueues, otherwise we would get infinite loop because of this feedback/
         return;
      }

      if (invalidTarget(controllerInUse)) {
         return;
      }

      if ((ref.getQueue() != null && (ref.getQueue().isInternalQueue() || ref.getQueue().isMirrorController()))) {
         if (logger.isDebugEnabled()) {
            logger.debug("preAcknowledge::{} rejecting preAcknowledge queue={}, ref={} to avoid infinite loop with the mirror (reflection)", server, ref.getQueue().getName(), ref);
         }
         return;
      }

      if (ignoreAddress(ref.getQueue().getAddress())) {
         if (logger.isTraceEnabled()) {
            logger.trace("preAcknowledge::{} rejecting preAcknowledge queue={}, ref={}, queue address is excluded", server, ref.getQueue().getName(), ref);
         }
         return;
      }

      logger.trace("preAcknowledge::{} preAcknowledge {}", server, ref);

      String nodeID = idSupplier.getServerID(ref); // notice the brokerID will be null for any message generated on this broker.
      long internalID = idSupplier.getID(ref);
      Message messageCommand = createMessage(ref.getQueue().getAddress(), ref.getQueue().getName(), POST_ACK, nodeID, internalID, reason);
      if (sync) {
         OperationContext operationContext;
         operationContext = OperationContextImpl.getContext(server.getExecutorFactory());
         messageCommand.setUserContext(OperationContext.class, operationContext);
         if (tx == null) {
            // notice that if transactional, the context is lined up on beforeCommit as part of the transaction operation
            operationContext.replicationLineUp();
         }
      }

      if (tx != null) {
         MirrorACKOperation operation = getAckOperation(tx);
         // notice the operationContext.replicationLineUp is done on beforeCommit as part of the TX
         operation.addMessage(messageCommand, ref);
         routeMirrorCommand(server, messageCommand, tx);
      } else {
         server.getStorageManager().afterStoreOperations(new IOCallback() {
            @Override
            public void done() {
               try {
                  logger.debug("preAcknowledge::afterStoreOperation for messageReference {}", ref);
                  routeMirrorCommand(server, messageCommand);
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
            }
         });
      }
   }

   private MirrorACKOperation getAckOperation(Transaction tx) {
      MirrorACKOperation ackOperation = (MirrorACKOperation) tx.getProperty(TransactionPropertyIndexes.MIRROR_ACK_OPERATION);
      if (ackOperation == null) {
         logger.trace("getAckOperation::setting operation on transaction {}", tx);
         ackOperation = new MirrorACKOperation(server);
         tx.putProperty(TransactionPropertyIndexes.MIRROR_ACK_OPERATION, ackOperation);
         tx.afterWired(ackOperation);
      }

      return ackOperation;
   }

   private MirrorSendOperation getSendOperation(Transaction tx) {
      if (tx == null) {
         return null;
      }
      MirrorSendOperation sendOperation = (MirrorSendOperation) tx.getProperty(TransactionPropertyIndexes.MIRROR_SEND_OPERATION);
      if (sendOperation == null) {
         logger.trace("getSendOperation::setting operation on transaction {}", tx);
         sendOperation = new MirrorSendOperation();
         tx.putProperty(TransactionPropertyIndexes.MIRROR_SEND_OPERATION, sendOperation);
         tx.afterStore(sendOperation);
      }

      return sendOperation;
   }

   private static class MirrorACKOperation implements Runnable {

      final ActiveMQServer server;

      // This map contains the Message used to generate the command towards the target, the reference being acked
      final HashMap<Message, MessageReference> acks = new HashMap<>();

      MirrorACKOperation(ActiveMQServer server) {
         this.server = server;
      }

      /**
       * @param message the message with the instruction to ack on the target node. Notice this is not the message owned
       *                by the reference.
       * @param ref     the reference being acked
       */
      public void addMessage(Message message, MessageReference ref) {
         acks.put(message, ref);
      }

      @Override
      public void run() {
         logger.debug("MirrorACKOperation::wired processing {}", acks);
         acks.forEach(this::doWired);
      }

      // callback to be used on forEach
      private void doWired(Message ack, MessageReference ref) {
         OperationContext context = (OperationContext) ack.getUserContext(OperationContext.class);
         if (context != null) {
            context.replicationLineUp();
         }
      }


   }

   private static final class MirrorSendOperation extends TransactionOperationAbstract {
      final List<MessageReference> refs = new ArrayList<>();

      public void addRef(MessageReference ref) {
         refs.add(ref);
      }

      @Override
      public void beforeCommit(Transaction tx) {
         refs.forEach(this::doBeforeCommit);
      }

      // callback to be used on forEach
      private void doBeforeCommit(MessageReference ref) {
         OperationContext context = ref.getProtocolData(OperationContext.class);
         if (context != null) {
            context.replicationLineUp();
         }
      }

      @Override
      public void afterRollback(Transaction tx) {
         logger.debug("MirrorSendOperation::afterRollback, refs:{}", refs);
         refs.forEach(this::doBeforeRollback);
      }

      // forEach callback
      private void doBeforeRollback(MessageReference ref) {
         OperationContext localCTX = ref.getProtocolData(OperationContext.class);
         if (localCTX != null) {
            localCTX.replicationDone();
         }
      }

      @Override
      public void afterCommit(Transaction tx) {
         logger.debug("MirrorSendOperation::afterCommit refs:{}", refs);
         refs.forEach(this::doAfterCommit);
      }

      // forEach callback
      private void doAfterCommit(MessageReference ref) {
         PostOfficeImpl.processReference(ref, false);
      }
   }

   private Message createMessage(SimpleString address, SimpleString queue, Object event, String brokerID, Object body) {
      return AMQPMirrorMessageFactory.createMessage(snfQueue.getAddress().toString(), address, queue, event, brokerID, body, null);
   }

   private Message createMessage(SimpleString address, SimpleString queue, Object event, String brokerID, Object body, AckReason ackReason) {
      return AMQPMirrorMessageFactory.createMessage(snfQueue.getAddress().toString(), address, queue, event, brokerID, body, ackReason);
   }

   public static void routeMirrorCommand(ActiveMQServer server, Message message) throws Exception {
      routeMirrorCommand(server, message, null);
   }

   public static void routeMirrorCommand(ActiveMQServer server, Message message, Transaction tx) throws Exception {
      message.setMessageID(server.getStorageManager().generateID());
      RoutingContext ctx = mirrorControlRouting.get();
      // it is important to use local only at the source to avoid having the message strictly load balancing
      // to other nodes if the SNF queue has the same name as the one on this node.
      ctx.clear().setMirrorOption(MirrorOption.disabled).setLoadBalancingType(MessageLoadBalancingType.LOCAL_ONLY).setTransaction(tx);
      logger.debug("SetTX {}", tx);
      server.getPostOffice().route(message, ctx, false);
   }

   static class PagedRouteContext implements RouteContextList {

      private final List<Queue> durableQueues;
      private final List<Queue> nonDurableQueues;

      PagedRouteContext(Queue snfQueue) {
         List<Queue> queues = new ArrayList<>(1);
         queues.add(snfQueue);

         if (snfQueue.isDurable()) {
            durableQueues = queues;
            nonDurableQueues = Collections.emptyList();
         } else {
            durableQueues = Collections.emptyList();
            nonDurableQueues = queues;
         }
      }

      @Override
      public int getNumberOfNonDurableQueues() {
         return nonDurableQueues.size();
      }

      @Override
      public int getNumberOfDurableQueues() {
         return durableQueues.size();
      }

      @Override
      public List<Queue> getDurableQueues() {
         return durableQueues;
      }

      @Override
      public List<Queue> getNonDurableQueues() {
         return nonDurableQueues;
      }

      @Override
      public void addAckedQueue(Queue queue) {

      }

      @Override
      public boolean isAlreadyAcked(Queue queue) {
         return false;
      }
   }



}
