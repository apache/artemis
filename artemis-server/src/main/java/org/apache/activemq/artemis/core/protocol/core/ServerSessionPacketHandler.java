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
package org.apache.activemq.artemis.core.protocol.core;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.List;
import java.util.Objects;

import io.netty.util.collection.IntObjectHashMap;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueMaxConsumerLimitReached;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.exception.ActiveMQXAException;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateAddressMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateProducerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSharedQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSharedQueueMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.NullResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.NullResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.RemoveProducerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.RollbackMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAcknowledgeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAddMetaDataMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAddMetaDataMessageV2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V4;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V5;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionConsumerCloseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionConsumerFlowCreditMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCreateConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionDeleteQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionExpireMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionForceConsumerDelivery;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionIndividualAcknowledgeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionRequestProducerCreditsMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionUniqueAddMetaDataMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAAfterFailedMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXACommitMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAEndMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAForgetMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAGetInDoubtXidsResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAGetTimeoutResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAJoinMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAPrepareMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResumeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXARollbackMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXASetTimeoutMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXASetTimeoutResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAStartMessage;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.EmbedMessageUtil;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.pools.MpscPool;
import org.apache.activemq.artemis.utils.pools.Pool;
import org.apache.activemq.artemis.utils.actors.Actor;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_ADDRESS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_PRODUCER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_QUEUE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_QUEUE_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_SHARED_QUEUE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_SHARED_QUEUE_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DELETE_QUEUE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.REMOVE_PRODUCER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_ACKNOWLEDGE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_CLOSE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_COMMIT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_CONSUMER_CLOSE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_CREATECONSUMER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_EXPIRED;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_FLOWTOKEN;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_FORCE_CONSUMER_DELIVERY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_INDIVIDUAL_ACKNOWLEDGE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_PRODUCER_REQUEST_CREDITS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_ROLLBACK;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_SEND;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_SEND_CONTINUATION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_SEND_LARGE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_START;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_STOP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_COMMIT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_END;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_FAILED;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_FORGET;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_GET_TIMEOUT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_INDOUBT_XIDS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_JOIN;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_PREPARE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_RESUME;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_ROLLBACK;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_SET_TIMEOUT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_START;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_SUSPEND;

public class ServerSessionPacketHandler implements ChannelHandler {

   private static final int MAX_CACHED_NULL_RESPONSES = 32;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String PRODUCER_ID_PREFIX = "artemis:sender:ID:";

   private final ActiveMQServer server;

   private final ServerSession session;

   private final StorageManager storageManager;

   private final Channel channel;

   private volatile CoreRemotingConnection remotingConnection;

   private final Actor<Packet> packetActor;

   private final ArtemisExecutor callExecutor;

   // The current currentLargeMessage being processed
   private volatile LargeServerMessage currentLargeMessage;

   private final boolean direct;

   private final Object largeMessageLock = new Object();

   protected final Pool<NullResponseMessage> poolNullResponse;

   private final IntObjectHashMap<String> producers = new IntObjectHashMap<>();

   public ServerSessionPacketHandler(final ActiveMQServer server,
                                     final ServerSession session,
                                     final Channel channel) {
      this.session = session;

      this.server = server;

      session.addCloseable((boolean failed) -> clearLargeMessage());

      this.storageManager = server.getStorageManager();

      this.channel = channel;

      this.remotingConnection = channel.getConnection();

      Connection conn = remotingConnection.getTransportConnection();

      this.callExecutor = server.getExecutorFactory().getExecutor();

      // In an optimized way packetActor should use the threadPool as the parent executor
      // directly from server.getThreadPool();
      // However due to how transferConnection is handled we need to
      // use the same executor
      this.packetActor = new Actor<>(callExecutor, this::onMessagePacket);

      this.direct = conn.isDirectDeliver();

      // no confirmation window size means no resend cache hence NullResponsePackets
      // won't get cached on it because need confirmation
      if (remotingConnection.isVersionBeforeAsyncResponseChange()) {
         poolNullResponse = new MpscPool<>(this.channel.getConfirmationWindowSize() == -1 ? MAX_CACHED_NULL_RESPONSES : 0, NullResponseMessage::reset, NullResponseMessage::new);
      } else {
         poolNullResponse = new MpscPool<>(this.channel.getConfirmationWindowSize() == -1 ? MAX_CACHED_NULL_RESPONSES : 0, NullResponseMessage::reset, NullResponseMessage_V2::new);
      }
   }

   private void clearLargeMessage() {
      if (currentLargeMessage != null) {
         logger.debug("pending large message on session being removed {}", currentLargeMessage);
      }
      synchronized (largeMessageLock) {
         if (currentLargeMessage != null) {
            try {
               currentLargeMessage.deleteFile();
               logger.debug("Remove file {} after a failed session", currentLargeMessage.getAppendFile());
            } catch (Throwable error) {
               ActiveMQServerLogger.LOGGER.errorDeletingLargeMessageFile(error);
            } finally {
               currentLargeMessage = null;
            }
         }
      }
   }

   public ServerSession getSession() {
      return session;
   }

   public long getID() {
      return channel.getID();
   }

   public void connectionFailed(final ActiveMQException exception, boolean failedOver) {
      ActiveMQServerLogger.LOGGER.clientConnectionFailed(session.getName());

      closeExecutors();

      try {
         session.close(true);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorClosingSession(e);
      }

      ActiveMQServerLogger.LOGGER.clearingUpSession(session.getName());
   }

   public void closeExecutors() {
      packetActor.shutdown();
      callExecutor.shutdown();
   }

   public void close() {
      closeExecutors();

      channel.flushConfirmations();

      try {
         session.close(false);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorClosingSession(e);
      }
   }

   public Channel getChannel() {
      return channel;
   }

   @Override
   public void handlePacket(final Packet packet) {

      // This method will call onMessagePacket through an actor
      packetActor.act(packet);
   }

   private void onMessagePacket(final Packet packet) {
      logger.trace("ServerSessionPacketHandler::handlePacket, {}", packet);

      if (AuditLogger.isAnyLoggingEnabled()) {
         AuditLogger.setRemoteAddress(remotingConnection.getRemoteAddress());
         AuditLogger.setCurrentCaller(remotingConnection.getSubject());
      }
      final byte type = packet.getType();
      switch (type) {
         case SESS_SEND: {
            onSessionSend(packet);
            break;
         }
         case SESS_ACKNOWLEDGE: {
            onSessionAcknowledge(packet);
            break;
         }
         case SESS_PRODUCER_REQUEST_CREDITS: {
            onSessionRequestProducerCredits(packet);
            break;
         }
         case SESS_FLOWTOKEN: {
            onSessionConsumerFlowCredit(packet);
            break;
         }
         default:
            // separating a method for everything else as JIT was faster this way
            slowPacketHandler(packet);
            break;
      }
   }

   // This is being separated from onMessagePacket as JIT was more efficient with a small method for the
   // hot executions.
   private void slowPacketHandler(final Packet packet) {
      final byte type = packet.getType();
      storageManager.setContext(session.getSessionContext());

      Packet response = null;
      boolean flush = false;
      boolean closeChannel = false;
      boolean requiresResponse = false;

      try {
         try {
            switch (type) {
               case SESS_SEND_LARGE: {
                  SessionSendLargeMessage message = (SessionSendLargeMessage) packet;
                  sendLarge(message.getLargeMessage());
                  break;
               }
               case SESS_SEND_CONTINUATION: {
                  SessionSendContinuationMessage message = (SessionSendContinuationMessage) packet;
                  requiresResponse = message.isRequiresResponse();
                  int senderID = message.getSenderID();
                  sendContinuations(message.getPacketSize(), message.getMessageBodySize(), message.getBody(), message.isContinues(), senderID);
                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case SESS_CREATECONSUMER: {
                  SessionCreateConsumerMessage request = (SessionCreateConsumerMessage) packet;
                  requiresResponse = request.isRequiresResponse();
                  session.createConsumer(request.getID(), request.getQueueName(), request.getFilterString(), request.getPriority(), request.isBrowseOnly(), true, null);
                  if (requiresResponse) {
                     // We send back queue information on the queue as a response- this allows the queue to
                     // be automatically recreated on failover
                     QueueQueryResult queueQueryResult = session.executeQueueQuery(request.getQueueName());

                     if (channel.supports(PacketImpl.SESS_QUEUEQUERY_RESP_V3)) {
                        response = new SessionQueueQueryResponseMessage_V3(queueQueryResult);
                     } else if (channel.supports(PacketImpl.SESS_QUEUEQUERY_RESP_V2)) {
                        response = new SessionQueueQueryResponseMessage_V2(queueQueryResult);
                     } else {
                        response = new SessionQueueQueryResponseMessage(queueQueryResult);
                     }
                  }

                  break;
               }
               case CREATE_ADDRESS: {
                  CreateAddressMessage request = (CreateAddressMessage) packet;
                  requiresResponse = request.isRequiresResponse();
                  session.createAddress(request.getAddress(), request.getRoutingTypes(), request.isAutoCreated());
                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case CREATE_QUEUE: {
                  CreateQueueMessage request = (CreateQueueMessage) packet;
                  requiresResponse = request.isRequiresResponse();
                  session.createQueue(QueueConfiguration.of(request.getQueueName())
                                         .setAddress(request.getAddress())
                                         .setRoutingType(getRoutingTypeFromAddress(request.getAddress()))
                                         .setFilterString(request.getFilterString())
                                         .setTemporary(request.isTemporary())
                                         .setDurable(request.isDurable()));
                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case CREATE_QUEUE_V2: {
                  CreateQueueMessage_V2 request = (CreateQueueMessage_V2) packet;
                  requiresResponse = request.isRequiresResponse();
                  session.createQueue(request.toQueueConfiguration());

                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case CREATE_SHARED_QUEUE: {
                  CreateSharedQueueMessage request = (CreateSharedQueueMessage) packet;
                  requiresResponse = request.isRequiresResponse();
                  QueueQueryResult result = session.executeQueueQuery(request.getQueueName());
                  if (!(result.isExists() && Objects.equals(result.getAddress(), request.getAddress()) && Objects.equals(result.getFilterString(), request.getFilterString()))) {
                     session.createSharedQueue(QueueConfiguration.of(request.getQueueName())
                                                  .setAddress(request.getAddress())
                                                  .setFilterString(request.getFilterString())
                                                  .setDurable(request.isDurable()));
                  }
                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case CREATE_SHARED_QUEUE_V2: {
                  CreateSharedQueueMessage_V2 request = (CreateSharedQueueMessage_V2) packet;
                  requiresResponse = request.isRequiresResponse();
                  QueueQueryResult result = session.executeQueueQuery(request.getQueueName());
                  if (!(result.isExists() && Objects.equals(result.getAddress(), request.getAddress()) && Objects.equals(result.getFilterString(), request.getFilterString()))) {
                     session.createSharedQueue(request.toQueueConfiguration());
                  }
                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case DELETE_QUEUE: {
                  requiresResponse = true;
                  SessionDeleteQueueMessage request = (SessionDeleteQueueMessage) packet;
                  session.deleteQueue(request.getQueueName());
                  response = createNullResponseMessage(packet);
                  break;
               }
               case SESS_QUEUEQUERY: {
                  requiresResponse = true;
                  SessionQueueQueryMessage request = (SessionQueueQueryMessage) packet;
                  QueueQueryResult result = session.executeQueueQuery(request.getQueueName());

                  if (result.isExists() && remotingConnection.getChannelVersion() < PacketImpl.ADDRESSING_CHANGE_VERSION) {
                     result.setAddress(SessionQueueQueryMessage.getOldPrefixedAddress(result.getAddress(), result.getRoutingType()));
                  }

                  if (channel.supports(PacketImpl.SESS_QUEUEQUERY_RESP_V3)) {
                     response = new SessionQueueQueryResponseMessage_V3(result);
                  } else if (channel.supports(PacketImpl.SESS_QUEUEQUERY_RESP_V2)) {
                     response = new SessionQueueQueryResponseMessage_V2(result);
                  } else {
                     response = new SessionQueueQueryResponseMessage(result);
                  }
                  break;
               }
               case SESS_BINDINGQUERY: {
                  requiresResponse = true;
                  SessionBindingQueryMessage request = (SessionBindingQueryMessage) packet;
                  final int clientVersion = remotingConnection.getChannelVersion();
                  BindingQueryResult result = session.executeBindingQuery(request.getAddress());

                  /*
                   * if the session is JMS and it's from an older client then we need to add the old prefix to the queue
                   * names otherwise the older client won't realize the queue exists and will try to create it and receive
                   * an error
                   */
                  if (result.isExists() && clientVersion < PacketImpl.ADDRESSING_CHANGE_VERSION && session.getMetaData(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY) != null) {
                     final List<SimpleString> queueNames = result.getQueueNames();
                     if (!queueNames.isEmpty()) {
                        final List<SimpleString> convertedQueueNames = request.convertQueueNames(clientVersion, queueNames);
                        if (convertedQueueNames != queueNames) {
                           result = new BindingQueryResult(result.isExists(), result.getAddressInfo(), convertedQueueNames, result.isAutoCreateQueues(), result.isAutoCreateAddresses(), result.isDefaultPurgeOnNoConsumers(), result.getDefaultMaxConsumers(), result.isDefaultExclusive(), result.isDefaultLastValue(), result.getDefaultLastValueKey(), result.isDefaultNonDestructive(), result.getDefaultConsumersBeforeDispatch(), result.getDefaultDelayBeforeDispatch());
                        }
                     }
                  }

                  if (channel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V5)) {
                     response = new SessionBindingQueryResponseMessage_V5(result.isExists(), result.getQueueNames(), result.isAutoCreateQueues(), result.isAutoCreateAddresses(), result.isDefaultPurgeOnNoConsumers(), result.getDefaultMaxConsumers(), result.isDefaultExclusive(), result.isDefaultLastValue(), result.getDefaultLastValueKey(), result.isDefaultNonDestructive(), result.getDefaultConsumersBeforeDispatch(), result.getDefaultDelayBeforeDispatch(), result.getAddressInfo() == null ? false : result.getAddressInfo().getRoutingTypes().contains(RoutingType.MULTICAST), result.getAddressInfo() == null ? false : result.getAddressInfo().getRoutingTypes().contains(RoutingType.ANYCAST));
                  } else if (channel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V4)) {
                     response = new SessionBindingQueryResponseMessage_V4(result.isExists(), result.getQueueNames(), result.isAutoCreateQueues(), result.isAutoCreateAddresses(), result.isDefaultPurgeOnNoConsumers(), result.getDefaultMaxConsumers(), result.isDefaultExclusive(), result.isDefaultLastValue(), result.getDefaultLastValueKey(), result.isDefaultNonDestructive(), result.getDefaultConsumersBeforeDispatch(), result.getDefaultDelayBeforeDispatch());
                  } else if (channel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V3)) {
                     response = new SessionBindingQueryResponseMessage_V3(result.isExists(), result.getQueueNames(), result.isAutoCreateQueues(), result.isAutoCreateAddresses());
                  } else if (channel.supports(PacketImpl.SESS_BINDINGQUERY_RESP_V2)) {
                     response = new SessionBindingQueryResponseMessage_V2(result.isExists(), result.getQueueNames(), result.isAutoCreateQueues());
                  } else {
                     response = new SessionBindingQueryResponseMessage(result.isExists(), result.getQueueNames());
                  }
                  break;
               }
               case SESS_EXPIRED: {
                  SessionExpireMessage message = (SessionExpireMessage) packet;
                  session.expire(message.getConsumerID(), message.getMessageID());
                  break;
               }
               case SESS_COMMIT: {
                  requiresResponse = true;
                  session.commit();
                  response = createNullResponseMessage(packet);
                  break;
               }
               case SESS_ROLLBACK: {
                  requiresResponse = true;
                  session.rollback(((RollbackMessage) packet).isConsiderLastMessageAsDelivered());
                  response = createNullResponseMessage(packet);
                  break;
               }
               case SESS_XA_COMMIT: {
                  requiresResponse = true;
                  SessionXACommitMessage message = (SessionXACommitMessage) packet;
                  session.xaCommit(message.getXid(), message.isOnePhase());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_END: {
                  requiresResponse = true;
                  SessionXAEndMessage message = (SessionXAEndMessage) packet;
                  session.xaEnd(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_FORGET: {
                  requiresResponse = true;
                  SessionXAForgetMessage message = (SessionXAForgetMessage) packet;
                  session.xaForget(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_JOIN: {
                  requiresResponse = true;
                  SessionXAJoinMessage message = (SessionXAJoinMessage) packet;
                  session.xaJoin(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_RESUME: {
                  requiresResponse = true;
                  SessionXAResumeMessage message = (SessionXAResumeMessage) packet;
                  session.xaResume(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_ROLLBACK: {
                  requiresResponse = true;
                  SessionXARollbackMessage message = (SessionXARollbackMessage) packet;
                  session.xaRollback(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_START: {
                  requiresResponse = true;
                  SessionXAStartMessage message = (SessionXAStartMessage) packet;
                  session.xaStart(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_FAILED: {
                  requiresResponse = true;
                  SessionXAAfterFailedMessage message = (SessionXAAfterFailedMessage) packet;
                  session.xaFailed(message.getXid());
                  // no response on this case
                  break;
               }
               case SESS_XA_SUSPEND: {
                  requiresResponse = true;
                  session.xaSuspend();
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_PREPARE: {
                  requiresResponse = true;
                  SessionXAPrepareMessage message = (SessionXAPrepareMessage) packet;
                  session.xaPrepare(message.getXid());
                  response = createSessionXAResponseMessage(packet);
                  break;
               }
               case SESS_XA_INDOUBT_XIDS: {
                  requiresResponse = true;
                  List<Xid> xids = session.xaGetInDoubtXids();
                  response = new SessionXAGetInDoubtXidsResponseMessage(xids);
                  break;
               }
               case SESS_XA_GET_TIMEOUT: {
                  requiresResponse = true;
                  int timeout = session.xaGetTimeout();
                  response = new SessionXAGetTimeoutResponseMessage(timeout);
                  break;
               }
               case SESS_XA_SET_TIMEOUT: {
                  requiresResponse = true;
                  SessionXASetTimeoutMessage message = (SessionXASetTimeoutMessage) packet;
                  session.xaSetTimeout(message.getTimeoutSeconds());
                  response = new SessionXASetTimeoutResponseMessage(true);
                  break;
               }
               case SESS_START: {
                  session.start();
                  break;
               }
               case SESS_STOP: {
                  requiresResponse = true;
                  session.stop();
                  response = createNullResponseMessage(packet);
                  break;
               }
               case SESS_CLOSE: {
                  requiresResponse = true;
                  session.close(false);
                  // removeConnectionListeners();
                  response = createNullResponseMessage(packet);
                  flush = true;
                  closeChannel = true;
                  break;
               }
               case SESS_INDIVIDUAL_ACKNOWLEDGE: {
                  SessionIndividualAcknowledgeMessage message = (SessionIndividualAcknowledgeMessage) packet;
                  requiresResponse = message.isRequiresResponse();
                  session.individualAcknowledge(message.getConsumerID(), message.getMessageID());
                  if (requiresResponse) {
                     response = createNullResponseMessage(packet);
                  }
                  break;
               }
               case SESS_CONSUMER_CLOSE: {
                  requiresResponse = true;
                  SessionConsumerCloseMessage message = (SessionConsumerCloseMessage) packet;
                  session.closeConsumer(message.getConsumerID());
                  response = createNullResponseMessage(packet);
                  break;
               }
               case SESS_FORCE_CONSUMER_DELIVERY: {
                  SessionForceConsumerDelivery message = (SessionForceConsumerDelivery) packet;
                  session.forceConsumerDelivery(message.getConsumerID(), message.getSequence());
                  break;
               }
               case PacketImpl.SESS_ADD_METADATA: {
                  response = createNullResponseMessage(packet);
                  SessionAddMetaDataMessage message = (SessionAddMetaDataMessage) packet;
                  session.addMetaData(message.getKey(), message.getData());
                  break;
               }
               case PacketImpl.SESS_ADD_METADATA2: {
                  requiresResponse = true;
                  SessionAddMetaDataMessageV2 message = (SessionAddMetaDataMessageV2) packet;
                  if (message.isRequiresConfirmations()) {
                     response = createNullResponseMessage(packet);
                  }
                  session.addMetaData(message.getKey(), message.getData());
                  break;
               }
               case PacketImpl.SESS_UNIQUE_ADD_METADATA: {
                  requiresResponse = true;
                  SessionUniqueAddMetaDataMessage message = (SessionUniqueAddMetaDataMessage) packet;
                  if (session.addUniqueMetaData(message.getKey(), message.getData())) {
                     response = createNullResponseMessage(packet);
                  } else {
                     response = new ActiveMQExceptionMessage(ActiveMQMessageBundle.BUNDLE.duplicateMetadata(message.getKey(), message.getData()));
                  }
                  break;
               }
               case CREATE_PRODUCER: {
                  CreateProducerMessage message = (CreateProducerMessage) packet;
                  if (!producers.containsKey(message.getId())) {
                     // this is used to create/destroy the producer so needs to be unique
                     String senderName = PRODUCER_ID_PREFIX + UUIDGenerator.getInstance().generateUUID();
                     producers.put(message.getId(), senderName);
                     session.addProducer(senderName, ActiveMQClient.DEFAULT_CORE_PROTOCOL, Objects.toString(message.getAddress(), null));
                  } else {
                     ActiveMQServerLogger.LOGGER.producerAlreadyExists(message.getId(), session.getName(), remotingConnection.getRemoteAddress());
                  }
                  break;
               }
               case REMOVE_PRODUCER: {
                  RemoveProducerMessage message = (RemoveProducerMessage) packet;
                  String remove = producers.remove(message.getId());
                  if (remove != null) {
                     session.removeProducer(remove);
                  } else {
                     ActiveMQServerLogger.LOGGER.producerDoesNotExist(message.getId(), session.getName(), remotingConnection.getRemoteAddress());
                  }
                  break;
               }
            }
         } catch (ActiveMQIOErrorException e) {
            response = onActiveMQIOErrorExceptionWhileHandlePacket(packet, e, requiresResponse, response, this.session);
         } catch (ActiveMQXAException e) {
            response = onActiveMQXAExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQQueueMaxConsumerLimitReached e) {
            response = onActiveMQQueueMaxConsumerLimitReachedWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQException e) {
            response = onActiveMQExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (Throwable t) {
            response = onCatchThrowableWhileHandlePacket(packet, t, requiresResponse, response, this.session);
         }
         sendResponse(packet, response, flush, closeChannel);
      } finally {
         storageManager.clearContext();
      }
   }

   private RoutingType getRoutingTypeFromAddress(SimpleString address) {
      if (address.startsWith(PacketImpl.OLD_QUEUE_PREFIX) || address.startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX)) {
         return RoutingType.ANYCAST;
      }
      return RoutingType.MULTICAST;
   }

   private Packet createNullResponseMessage(Packet packet) {
      NullResponseMessage response = poolNullResponse.borrow();

      // this should be already set by the channel too, but let's do it just in case
      response.setCorrelationID(packet.getCorrelationID());

      return response;
   }

   private Packet createSessionXAResponseMessage(Packet packet) {
      Packet response;
      if (packet.isResponseAsync()) {
         response = new SessionXAResponseMessage_V2(packet.getCorrelationID(), false, XAResource.XA_OK, null);
      } else {
         response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
      }
      return response;
   }

   private void releaseResponse(Packet packet) {
      if (packet instanceof NullResponseMessage message) {
         poolNullResponse.release(message);
      }
   }

   private void onSessionAcknowledge(Packet packet) {
      this.storageManager.setContext(session.getSessionContext());
      try {
         Packet response = null;
         boolean requiresResponse = false;
         try {
            final SessionAcknowledgeMessage message = (SessionAcknowledgeMessage) packet;
            requiresResponse = message.isRequiresResponse();
            this.session.acknowledge(message.getConsumerID(), message.getMessageID());
            if (requiresResponse) {
               response = createNullResponseMessage(packet);
            }
         } catch (ActiveMQIOErrorException e) {
            response = onActiveMQIOErrorExceptionWhileHandlePacket(packet, e, requiresResponse, response, this.session);
         } catch (ActiveMQXAException e) {
            response = onActiveMQXAExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQQueueMaxConsumerLimitReached e) {
            response = onActiveMQQueueMaxConsumerLimitReachedWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQException e) {
            response = onActiveMQExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (Throwable t) {
            response = onCatchThrowableWhileHandlePacket(packet, t, requiresResponse, response, this.session);
         }
         sendResponse(packet, response, false, false);
      } finally {
         this.storageManager.clearContext();
      }
   }

   private void onSessionSend(Packet packet) {
      this.storageManager.setContext(session.getSessionContext());
      try {
         Packet response = null;
         boolean requiresResponse = false;
         try {
            final SessionSendMessage message = (SessionSendMessage) packet;
            requiresResponse = message.isRequiresResponse();
            this.session.send(EmbedMessageUtil.extractEmbedded(message.getMessage(), storageManager), this.direct, producers.get(message.getSenderID()));
            if (requiresResponse) {
               response = createNullResponseMessage(packet);
            }
         } catch (ActiveMQIOErrorException e) {
            response = onActiveMQIOErrorExceptionWhileHandlePacket(packet, e, requiresResponse, response, this.session);
         } catch (ActiveMQXAException e) {
            response = onActiveMQXAExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQQueueMaxConsumerLimitReached e) {
            response = onActiveMQQueueMaxConsumerLimitReachedWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQException e) {
            response = onActiveMQExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (Throwable t) {
            response = onCatchThrowableWhileHandlePacket(packet, t, requiresResponse, response, this.session);
         }
         sendResponse(packet, response, false, false);
      } finally {
         this.storageManager.clearContext();
      }
   }

   private void onSessionRequestProducerCredits(Packet packet) {
      this.storageManager.setContext(session.getSessionContext());
      try {
         Packet response = null;
         boolean requiresResponse = false;
         try {
            SessionRequestProducerCreditsMessage message = (SessionRequestProducerCreditsMessage) packet;
            session.requestProducerCredits(message.getAddress(), message.getCredits());
         } catch (ActiveMQIOErrorException e) {
            response = onActiveMQIOErrorExceptionWhileHandlePacket(packet, e, requiresResponse, response, this.session);
         } catch (ActiveMQXAException e) {
            response = onActiveMQXAExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQQueueMaxConsumerLimitReached e) {
            response = onActiveMQQueueMaxConsumerLimitReachedWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQException e) {
            response = onActiveMQExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (Throwable t) {
            response = onCatchThrowableWhileHandlePacket(packet, t, requiresResponse, response, this.session);
         }
         sendResponse(packet, response, false, false);
      } finally {
         this.storageManager.clearContext();
      }
   }

   private void onSessionConsumerFlowCredit(Packet packet) {
      this.storageManager.setContext(session.getSessionContext());
      try {
         Packet response = null;
         boolean requiresResponse = false;
         try {
            SessionConsumerFlowCreditMessage message = (SessionConsumerFlowCreditMessage) packet;
            session.receiveConsumerCredits(message.getConsumerID(), message.getCredits());
         } catch (ActiveMQIOErrorException e) {
            response = onActiveMQIOErrorExceptionWhileHandlePacket(packet, e, requiresResponse, response, this.session);
         } catch (ActiveMQXAException e) {
            response = onActiveMQXAExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQQueueMaxConsumerLimitReached e) {
            response = onActiveMQQueueMaxConsumerLimitReachedWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (ActiveMQException e) {
            response = onActiveMQExceptionWhileHandlePacket(packet, e, requiresResponse, response);
         } catch (Throwable t) {
            response = onCatchThrowableWhileHandlePacket(packet, t, requiresResponse, response, this.session);
         }
         sendResponse(packet, response, false, false);
      } finally {
         this.storageManager.clearContext();
      }
   }


   private static Packet onActiveMQIOErrorExceptionWhileHandlePacket(Packet packet,
                                                                     ActiveMQIOErrorException e,
                                                                     boolean requiresResponse,
                                                                     Packet response,
                                                                     ServerSession session) {
      session.markTXFailed(e);
      if (requiresResponse) {
         logger.debug("Sending exception to client", e);
         response = convertToExceptionPacket(packet, e);
      } else {
         ActiveMQServerLogger.LOGGER.caughtException(e);
      }
      return response;
   }

   private static Packet onActiveMQXAExceptionWhileHandlePacket(Packet packet,
                                                                ActiveMQXAException e,
                                                                boolean requiresResponse,
                                                                Packet response) {
      if (requiresResponse) {
         logger.debug("Sending exception to client", e);
         if (packet.isResponseAsync()) {
            response = new SessionXAResponseMessage_V2(packet.getCorrelationID(), true, e.errorCode, e.getMessage());
         } else {
            response = new SessionXAResponseMessage(true, e.errorCode, e.getMessage());
         }
      } else {
         ActiveMQServerLogger.LOGGER.caughtXaException(e);
      }
      return response;
   }

   private static Packet onActiveMQQueueMaxConsumerLimitReachedWhileHandlePacket(Packet packet,
                                                                                 ActiveMQQueueMaxConsumerLimitReached e,
                                                                                 boolean requiresResponse,
                                                                                 Packet response) {
      if (requiresResponse) {
         logger.debug("Sending exception to client", e);
         response = convertToExceptionPacket(packet, e);
      } else {
         ActiveMQServerLogger.LOGGER.caughtException(e);
      }
      return response;
   }

   private static Packet convertToExceptionPacket(Packet packet, ActiveMQException e) {
      Packet response;
      if (packet.isResponseAsync()) {
         response = new ActiveMQExceptionMessage_V2(packet.getCorrelationID(), e);
      } else {
         response = new ActiveMQExceptionMessage(e);
      }
      return response;
   }

   private static Packet onActiveMQExceptionWhileHandlePacket(Packet packet,
                                                              ActiveMQException e,
                                                              boolean requiresResponse,
                                                              Packet response) {
      if (requiresResponse) {
         logger.debug("Sending exception to client", e);
         response = convertToExceptionPacket(packet, e);
      } else {
         if (e.getType() == ActiveMQExceptionType.QUEUE_EXISTS) {
            logger.debug("Caught exception", e);
         } else {
            ActiveMQServerLogger.LOGGER.caughtException(e);
         }
      }
      return response;
   }

   private static Packet onCatchThrowableWhileHandlePacket(Packet packet,
                                                           Throwable t,
                                                           boolean requiresResponse,
                                                           Packet response,
                                                           ServerSession session) {
      session.markTXFailed(t);
      if (requiresResponse) {
         ActiveMQServerLogger.LOGGER.sendingUnexpectedExceptionToClient(t);
         ActiveMQException activeMQInternalErrorException = new ActiveMQInternalErrorException();
         activeMQInternalErrorException.initCause(t);
         response = convertToExceptionPacket(packet, activeMQInternalErrorException);
      } else {
         ActiveMQServerLogger.LOGGER.caughtException(t);
      }
      return response;
   }



   private void sendResponse(final Packet confirmPacket,
                             final Packet response,
                             final boolean flush,
                             final boolean closeChannel) {
      logger.trace("ServerSessionPacketHandler::scheduling response::{}", response);

      storageManager.afterCompleteOperations(new IOCallback() {
         @Override
         public void onError(final int errorCode, final String errorMessage) {
            ActiveMQServerLogger.LOGGER.errorProcessingIOCallback(errorCode, errorMessage);

            Packet exceptionPacket = convertToExceptionPacket(confirmPacket, ActiveMQExceptionType.createException(errorCode, errorMessage));
            doConfirmAndResponse(confirmPacket, exceptionPacket, flush, closeChannel);

            logger.trace("ServerSessionPacketHandler::exception response sent::{}", exceptionPacket);
         }

         @Override
         public void done() {
            logger.trace("ServerSessionPacketHandler::regular response sent::{}", response);

            doConfirmAndResponse(confirmPacket, response, flush, closeChannel);
         }
      });
   }

   private void doConfirmAndResponse(final Packet confirmPacket,
                                     final Packet response,
                                     final boolean flush,
                                     final boolean closeChannel) {
      // don't confirm if the response is an exception
      if (confirmPacket != null && (response == null || (response != null && response.getType() != PacketImpl.EXCEPTION))) {
         channel.confirm(confirmPacket);

         if (flush) {
            channel.flushConfirmations();
         }
      }

      if (response != null) {
         try {
            channel.send(response);
         } finally {
            releaseResponse(response);
         }
      }

      if (closeChannel) {
         channel.close();
      }
   }

   public void closeListeners() {
      List<CloseListener> listeners = remotingConnection.removeCloseListeners();

      for (CloseListener closeListener : listeners) {
         closeListener.connectionClosed();
         if (closeListener instanceof FailureListener listener) {
            remotingConnection.removeFailureListener(listener);
         }
      }
   }

   // Large Message is part of the core protocol, we have these functions here as part of Packet handler
   private void sendLarge(final Message message) throws Exception {
      // need to create the LargeMessage before continue
      long id = storageManager.generateID();

      if (logger.isDebugEnabled()) {
         logger.debug("initializing large message {}", id);
      }
      LargeServerMessage largeMsg = storageManager.createCoreLargeMessage(id, message);

      logger.trace("sendLarge::{}", largeMsg);

      if (currentLargeMessage != null) {
         ActiveMQServerLogger.LOGGER.replacingIncompleteLargeMessage(currentLargeMessage.getMessageID());

         // this shouldn't really happen.
         // Adding this just in case
         final LargeServerMessage replaced = currentLargeMessage;
         callExecutor.execute(() -> {
            try {
               if (replaced != null) {
                  logger.debug("Replaced failed being removed over interrupted send for message {}", replaced);
                  replaced.deleteFile();
               }
            } catch (Exception e) {
               logger.warn("Error removing currentLargeMessage {}", replaced);
            }
         });
      }

      currentLargeMessage = largeMsg;
   }

   private void sendContinuations(final int packetSize,
                                  final long messageBodySize,
                                  final byte[] body,
                                  final boolean continues,
                                  final int senderID) throws Exception {

      synchronized (largeMessageLock) {
         if (currentLargeMessage == null) {
            throw ActiveMQMessageBundle.BUNDLE.largeMessageNotInitialised();
         }

         // Immediately release the credits for the continuations- these don't contribute to the in-memory size
         // of the message

         currentLargeMessage.addBytes(body);

         if (!continues) {
            currentLargeMessage.releaseResources(server.getConfiguration().isLargeMessageSync(), true);

            if (messageBodySize >= 0) {
               currentLargeMessage.toMessage().putLongProperty(Message.HDR_LARGE_BODY_SIZE, messageBodySize);
            }

            LargeServerMessage message = currentLargeMessage;
            currentLargeMessage.setStorageManager(storageManager);
            currentLargeMessage = null;

            try {
               Message m = EmbedMessageUtil.extractEmbedded((ICoreMessage) message.toMessage(), storageManager);
               session.send(session.getCurrentTransaction(), m, false, producers.get(senderID), false);

               /*
                * The message embedded in the large Core message (e.g. an AMQP message) may, in fact, not be large. If
                * this is the case then we need to clean up the large message file that's no longer needed.
                */
               if (!m.isLargeMessage()) {
                  message.deleteFile();
               }
            } catch (Exception e) {
               message.deleteFile();
               throw e;
            } catch (Throwable e) {
               logger.warn("********************************************************************************");
               logger.warn("Throwable on currentLargeMessage {}", message.getMessageID(), e);
               logger.warn("********************************************************************************");

            }
         }
      }
   }
}
