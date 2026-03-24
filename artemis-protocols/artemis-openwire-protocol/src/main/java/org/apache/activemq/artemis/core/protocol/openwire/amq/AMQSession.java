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
package org.apache.activemq.artemis.core.protocol.openwire.amq;

import static org.apache.activemq.artemis.core.protocol.openwire.util.OpenWireUtil.OPENWIRE_WILDCARD;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.InvalidDestinationException;
import javax.jms.ResourceAllocationException;

import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireMessageConverter;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireProtocolManager;
import org.apache.activemq.artemis.core.protocol.openwire.util.OpenWireUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.SlowConsumerDetectionListener;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.IDGenerator;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.ProducerAck;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.openwire.OpenWireFormat;
import org.jboss.logging.Logger;

public class AMQSession implements SessionCallback {
   private final Logger logger = Logger.getLogger(AMQSession.class);

   // ConsumerID is generated inside the session, 0, 1, 2, ... as many consumers as you have on the session
   protected final IDGenerator consumerIDGenerator = new SimpleIDGenerator(0);

   private final ConnectionInfo connInfo;
   private ServerSession coreSession;
   private final SessionInfo sessInfo;
   private final ActiveMQServer server;
   private final OpenWireConnection connection;

   private final AtomicBoolean started = new AtomicBoolean(false);

   private final ScheduledExecutorService scheduledPool;

   // The sessionWireformat used by the session
   // this object is meant to be used per thread / session
   // so we make a new one per AMQSession
   private final OpenWireFormat protocolManagerWireFormat;

   private final OpenWireProtocolManager protocolManager;

   private final CoreMessageObjectPools coreMessageObjectPools;

   private String[] existingQueuesCache;

   private final SimpleString clientId;

   public AMQSession(ConnectionInfo connInfo,
                     SessionInfo sessInfo,
                     ActiveMQServer server,
                     OpenWireConnection connection,
                     OpenWireProtocolManager protocolManager, CoreMessageObjectPools coreMessageObjectPools) {
      this.connInfo = connInfo;
      this.sessInfo = sessInfo;
      this.clientId = SimpleString.toSimpleString(connInfo.getClientId());
      this.server = server;
      this.connection = connection;
      this.protocolManager = protocolManager;
      this.scheduledPool = protocolManager.getScheduledPool();
      this.protocolManagerWireFormat = protocolManager.wireFormat().copy();
      this.existingQueuesCache = null;
      this.coreMessageObjectPools = coreMessageObjectPools;
   }

   public boolean isClosed() {
      return coreSession.isClosed();
   }

   public OpenWireFormat wireFormat() {
      return protocolManagerWireFormat;
   }

   public void initialize() {
      String name = sessInfo.getSessionId().toString();
      String username = connInfo.getUserName();
      String password = connInfo.getPassword();

      int minLargeMessageSize = Integer.MAX_VALUE; // disable
      // minLargeMessageSize for
      // now

      try {
         coreSession = server.createSession(name, username, password, minLargeMessageSize, connection, true, false, false, false, null, this, true, connection.getOperationContext(), protocolManager.getPrefixes(), protocolManager.getSecurityDomain());
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.error("error init session", e);
      }

   }


   @Override
   public boolean supportsDirectDelivery() {
      return false;
   }


   @Override
   public boolean updateDeliveryCountAfterCancel(ServerConsumer consumer, MessageReference ref, boolean failed) {
      if (consumer.getProtocolData() != null) {
         return ((AMQConsumer) consumer.getProtocolData()).updateDeliveryCountAfterCancel(ref);
      } else {
         return false;
      }

   }

   public List<AMQConsumer> createConsumer(ConsumerInfo info,
                                           SlowConsumerDetectionListener slowConsumerDetectionListener) throws Exception {
      //check destination
      ActiveMQDestination dest = info.getDestination();
      ActiveMQDestination[] dests = null;
      if (dest.isComposite()) {
         dests = dest.getCompositeDestinations();
      } else {
         dests = new ActiveMQDestination[]{dest};
      }

      List<AMQConsumer> consumersList = new java.util.LinkedList<>();

      for (ActiveMQDestination openWireDest : dests) {
         boolean isInternalAddress = false;
         if (AdvisorySupport.isAdvisoryTopic(dest)) {
            if (!connection.isSuppportAdvisory()) {
               continue;
            }
            isInternalAddress = connection.isSuppressInternalManagementObjects();
         }
         if (openWireDest.isQueue()) {
            openWireDest = protocolManager.virtualTopicConsumerToFQQN(openWireDest);
            SimpleString queueName = new SimpleString(convertWildcard(openWireDest));

            if (!checkAutoCreateQueue(queueName, openWireDest.isTemporary(), OpenWireUtil.extractFilterStringOrNull(info, openWireDest))) {
               throw new InvalidDestinationException("Destination doesn't exist: " + queueName);
            }
         }
         AMQConsumer consumer = new AMQConsumer(this, openWireDest, info, scheduledPool, isInternalAddress);

         long nativeID = consumerIDGenerator.generateID();
         consumer.init(slowConsumerDetectionListener, nativeID);
         consumersList.add(consumer);
      }

      return consumersList;
   }

   private boolean checkCachedExistingQueues(final SimpleString address,
                                             final String physicalName,
                                             final boolean isTemporary) throws Exception {
      String[] existingQueuesCache = this.existingQueuesCache;
      //lazy allocation of the cache
      if (existingQueuesCache == null) {
         //16 means 64 bytes with 32 bit references or 128 bytes with 64 bit references -> 1 or 2 cache lines with common archs
         existingQueuesCache = new String[16];
         assert (Integer.bitCount(existingQueuesCache.length) == 1) : "existingQueuesCache.length must be power of 2";
         this.existingQueuesCache = existingQueuesCache;
      }
      final int hashCode = physicalName.hashCode();
      //this.existingQueuesCache.length must be power of 2
      final int mask = existingQueuesCache.length - 1;
      final int index = hashCode & mask;
      final String existingQueue = existingQueuesCache[index];
      if (existingQueue != null && existingQueue.equals(physicalName)) {
         //if the information is stale (ie no longer valid) it will fail later
         return true;
      }
      final boolean hasQueue = checkAutoCreateQueue(address, isTemporary);
      if (hasQueue) {
         existingQueuesCache[index] = physicalName;
      }
      return hasQueue;
   }

   private boolean checkAutoCreateQueue(SimpleString queueName, boolean isTemporary) throws Exception {
      return checkAutoCreateQueue(queueName, isTemporary, null);
   }

   private boolean checkAutoCreateQueue(SimpleString queueName, boolean isTemporary, String filter) throws Exception {
      boolean hasQueue = true;
      if (!connection.containsKnownDestination(queueName)) {

         BindingQueryResult bindingQuery = server.bindingQuery(queueName);
         QueueQueryResult queueBinding = server.queueQuery(queueName);

         try {
            if (!queueBinding.isExists()) {
               if (bindingQuery.isAutoCreateQueues()) {
                  SimpleString queueNameToUse = queueName;
                  SimpleString addressToUse = queueName;
                  RoutingType routingTypeToUse = RoutingType.ANYCAST;
                  if (CompositeAddress.isFullyQualified(queueName.toString())) {
                     addressToUse = CompositeAddress.extractAddressName(queueName);
                     queueNameToUse = CompositeAddress.extractQueueName(queueName);
                     if (bindingQuery.getAddressInfo() != null) {
                        routingTypeToUse = bindingQuery.getAddressInfo().getRoutingType();
                     } else {
                        AddressSettings as = server.getAddressSettingsRepository().getMatch(addressToUse.toString());
                        routingTypeToUse = as.getDefaultAddressRoutingType();
                     }
                  }
                  coreSession.createQueue(new QueueConfiguration(queueNameToUse).setAddress(addressToUse).setRoutingType(routingTypeToUse).setTemporary(isTemporary).setAutoCreated(true).setFilterString(filter));
                  connection.addKnownDestination(queueName);
               } else {
                  hasQueue = false;
               }
            }
         } catch (ActiveMQQueueExistsException e) {
            // In case another thread created the queue before us but after we did the binding query
            hasQueue = true;
         }

      }
      return hasQueue;
   }

   public void start() {

      coreSession.start();
      started.set(true);

   }

   // rename actualDest to destination
   @Override
   public void afterDelivery() throws Exception {

   }

   @Override
   public void browserFinished(ServerConsumer consumer) {
      AMQConsumer theConsumer = (AMQConsumer) consumer.getProtocolData();
      if (theConsumer != null) {
         theConsumer.browseFinished();
      }
   }

   @Override
   public boolean isWritable(ReadyListener callback, Object protocolContext) {
      return connection.isWritable(callback);
   }

   @Override
   public void sendProducerCreditsMessage(int credits, SimpleString address) {
      // TODO Auto-generated method stub

   }

   @Override
   public void sendProducerCreditsFailMessage(int credits, SimpleString address) {
      // TODO Auto-generated method stub

   }

   @Override
   public int sendMessage(MessageReference reference,
                          org.apache.activemq.artemis.api.core.Message message,
                          ServerConsumer consumer,
                          int deliveryCount) {
      AMQConsumer theConsumer = (AMQConsumer) consumer.getProtocolData();
      //clear up possible rolledback ids.
      theConsumer.removeRolledback(reference);
      return theConsumer.handleDeliver(reference, message.toCore());
   }

   @Override
   public int sendLargeMessage(MessageReference reference,
                               org.apache.activemq.artemis.api.core.Message message,
                               ServerConsumer consumerID,
                               long bodySize,
                               int deliveryCount) {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public int sendLargeMessageContinuation(ServerConsumer consumerID,
                                           byte[] body,
                                           boolean continues,
                                           boolean requiresResponse) {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void closed() {
      // TODO Auto-generated method stub

   }

   @Override
   public boolean hasCredits(ServerConsumer consumer) {

      AMQConsumer amqConsumer = null;

      if (consumer.getProtocolData() != null) {
         amqConsumer = (AMQConsumer) consumer.getProtocolData();
      }

      return amqConsumer != null && amqConsumer.hasCredits();
   }

   @Override
   public void disconnect(ServerConsumer serverConsumer, SimpleString queueName) {
      // for an openwire consumer this is fatal because unlike with activemq5 sending
      // to the address will not auto create the consumer binding and it will be in limbo.
      // forcing disconnect allows it to failover and recreate its binding.
      final IOException forcePossibleFailoverReconnect = new IOException("Destination : " + queueName + " has been deleted.");
      try {
         connection.serviceException(forcePossibleFailoverReconnect);
      } catch (Exception ignored) {
      }
      connection.disconnect(forcePossibleFailoverReconnect.getMessage(), true);
   }

   private static boolean isTemporary(ProducerInfo producerInfo) {
      return producerInfo != null && producerInfo.getDestination() != null && producerInfo.getDestination().isTemporary();
   }

   public void send(final ProducerInfo producerInfo,
                    final Message messageSend,
                    final boolean sendProducerAck) throws Exception {
      messageSend.setBrokerInTime(System.currentTimeMillis());

      final ActiveMQDestination destination = messageSend.getDestination();

      final ActiveMQDestination[] actualDestinations;
      final int actualDestinationsCount;
      if (destination.isComposite()) {
         actualDestinations = destination.getCompositeDestinations();
         messageSend.setOriginalDestination(destination);
         actualDestinationsCount = actualDestinations.length;
      } else {
         actualDestinations = null;
         actualDestinationsCount = 1;
      }

      final org.apache.activemq.artemis.api.core.Message originalCoreMsg = OpenWireMessageConverter.inbound(messageSend, protocolManagerWireFormat, coreMessageObjectPools);

      assert clientId.toString().equals(this.connection.getState().getInfo().getClientId()) : "Session cached clientId must be the same of the connection";
      originalCoreMsg.putStringProperty(MessageUtil.CONNECTION_ID_PROPERTY_NAME, clientId);

      /* ActiveMQ failover transport will attempt to reconnect after connection failure.  Any sent messages that did
      * not receive acks will be resent.  (ActiveMQ broker handles this by returning a last sequence id received to
      * the client).  To handle this in Artemis we use a duplicate ID cache.  To do this we check to see if the
      * message comes from failover connection.  If so we add a DUPLICATE_ID to handle duplicates after a resend. */

      if (connection.getContext().isFaultTolerant() && protocolManager.isOpenwireUseDuplicateDetectionOnFailover() && !messageSend.getProperties().containsKey(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID.toString()) && !isTemporary(producerInfo)) {
         originalCoreMsg.putStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.toSimpleString(messageSend.getMessageId().toString()));
      }

      final boolean shouldBlockProducer = producerInfo.getWindowSize() > 0 || messageSend.isResponseRequired();

      final AtomicInteger count = actualDestinations != null ? new AtomicInteger(actualDestinationsCount) : null;

      if (shouldBlockProducer) {
         connection.getContext().setDontSendReponse(true);
      }

      for (int i = 0; i < actualDestinationsCount; i++) {
         final ActiveMQDestination dest = actualDestinations != null ? actualDestinations[i] : destination;
         final String physicalName = dest.getPhysicalName();
         final SimpleString address = SimpleString.toSimpleString(physicalName, coreMessageObjectPools.getAddressStringSimpleStringPool());
         //the last coreMsg could be directly the original one -> it avoid 1 copy if actualDestinations > 1 and ANY copy if actualDestinations == 1
         final org.apache.activemq.artemis.api.core.Message coreMsg = (i == actualDestinationsCount - 1) ? originalCoreMsg : originalCoreMsg.copy();
         coreMsg.setAddress(address);

         if (dest.isQueue()) {
            checkCachedExistingQueues(address, physicalName, dest.isTemporary());
            coreMsg.setRoutingType(RoutingType.ANYCAST);
         } else {
            coreMsg.setRoutingType(RoutingType.MULTICAST);
         }
         final PagingStore store = server.getPagingManager().getPageStore(address);

         if (shouldBlockProducer) {
            sendShouldBlockProducer(producerInfo, messageSend, sendProducerAck, store, dest, count, coreMsg, address);
         } else {
            if (store != null) {
               if (!store.checkMemory(true, this::restoreAutoRead, this::blockConnection)) {
                  restoreAutoRead();
                  throw new ResourceAllocationException("Queue is full " + address);
               }
            } else {
               restoreAutoRead();
            }

            getCoreSession().send(coreMsg, false, dest.isTemporary());

            if (count == null || count.decrementAndGet() == 0) {
               if (sendProducerAck) {
                  final ProducerAck ack = new ProducerAck(producerInfo.getProducerId(), messageSend.getSize());
                  connection.dispatchAsync(ack);
               }
            }
         }
      }
   }

   private void sendShouldBlockProducer(final ProducerInfo producerInfo,
                                        final Message messageSend,
                                        final boolean sendProducerAck,
                                        final PagingStore store,
                                        final ActiveMQDestination dest,
                                        final AtomicInteger count,
                                        final org.apache.activemq.artemis.api.core.Message coreMsg,
                                        final SimpleString address) throws ResourceAllocationException {
      final Runnable task = () -> {
         Exception exceptionToSend = null;

         try {
            getCoreSession().send(coreMsg, false, dest.isTemporary());
         } catch (Exception e) {
            logger.debug("Sending exception to the client", e);
            exceptionToSend = e;
         }
         connection.enableTtl();
         if (count == null || count.decrementAndGet() == 0) {
            if (exceptionToSend != null) {
               this.connection.getContext().setDontSendReponse(false);
               connection.sendException(exceptionToSend);
            } else {
               server.getStorageManager().afterCompleteOperations(new IOCallback() {
                  @Override
                  public void done() {
                     if (sendProducerAck) {
                        try {
                           ProducerAck ack = new ProducerAck(producerInfo.getProducerId(), messageSend.getSize());
                           connection.dispatchAsync(ack);
                        } catch (Exception e) {
                           connection.getContext().setDontSendReponse(false);
                           ActiveMQServerLogger.LOGGER.warn(e.getMessage(), e);
                           connection.sendException(e);
                        }
                     } else {
                        connection.getContext().setDontSendReponse(false);
                        try {
                           Response response = new Response();
                           response.setCorrelationId(messageSend.getCommandId());
                           connection.dispatchAsync(response);
                        } catch (Exception e) {
                           ActiveMQServerLogger.LOGGER.warn(e.getMessage(), e);
                           connection.sendException(e);
                        }
                     }
                  }

                  @Override
                  public void onError(int errorCode, String errorMessage) {
                     try {
                        final IOException e = new IOException(errorMessage);
                        ActiveMQServerLogger.LOGGER.warn(errorMessage);
                        connection.serviceException(e);
                     } catch (Exception ex) {
                        ActiveMQServerLogger.LOGGER.debug(ex);
                     }
                  }
               });
            }
         }
      };
      if (store != null) {
         if (!store.checkMemory(false, task, null)) {
            this.connection.getContext().setDontSendReponse(false);
            connection.enableTtl();
            throw new ResourceAllocationException("Queue is full " + address);
         }
      } else {
         task.run();
      }
   }

   private void restoreAutoRead() {
      connection.restoreAutoRead();
   }

   private void blockConnection() {
      connection.blockConnection();
   }

   public String convertWildcard(ActiveMQDestination openWireDest) {
      if (openWireDest.isTemporary() || AdvisorySupport.isAdvisoryTopic(openWireDest)) {
         return openWireDest.getPhysicalName();
      } else {
         return OPENWIRE_WILDCARD.convert(openWireDest.getPhysicalName(), server.getConfiguration().getWildcardConfiguration());
      }
   }

   public ServerSession getCoreSession() {
      return this.coreSession;
   }

   public ActiveMQServer getCoreServer() {
      return this.server;
   }

   public ConnectionInfo getConnectionInfo() {
      return this.connInfo;
   }

   public void disableSecurity() {
      this.coreSession.disableSecurity();
   }

   public void deliverMessage(MessageDispatch dispatch) {
      this.connection.deliverMessage(dispatch);
   }

   public void close() throws Exception {
      this.coreSession.close(false);
   }

   public OpenWireConnection getConnection() {
      return connection;
   }

   public boolean isInternal() {
      return sessInfo.getSessionId().getValue() == -1;
   }
}
