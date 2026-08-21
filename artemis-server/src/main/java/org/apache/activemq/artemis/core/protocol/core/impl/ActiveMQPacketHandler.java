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
package org.apache.activemq.artemis.core.protocol.core.impl;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ServerSessionPacketHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CheckFailoverMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CheckFailoverReplyMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReattachSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReattachSessionResponseMessage;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.utils.actors.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A packet handler for all packets that need to be handled at the server level
 */
public class ActiveMQPacketHandler implements ChannelHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ActiveMQServer server;

   private final Channel channel1;

   private final CoreRemotingConnection connection;

   private final CoreProtocolManager protocolManager;

   private final Actor<Packet> packetActor;

   public ActiveMQPacketHandler(final CoreProtocolManager protocolManager,
                                final ActiveMQServer server,
                                final Channel channel1,
                                final CoreRemotingConnection connection) {
      this.protocolManager = protocolManager;

      this.server = server;

      this.channel1 = channel1;

      this.connection = connection;

      packetActor = new Actor<>(server.getExecutorFactory().getExecutor(), this::internalHandler);
   }

   @Override
   public void handlePacket(final Packet packet) {
      packetActor.act(packet);
   }

   private void internalHandler(final Packet packet) {
      byte type = packet.getType();

      if (AuditLogger.isAnyLoggingEnabled()) {
         AuditLogger.setRemoteAddress(connection.getRemoteAddress());
         AuditLogger.setCurrentCaller(connection.getSubject());
      }

      switch (type) {
         case PacketImpl.CREATESESSION:
         case PacketImpl.CREATESESSION_V2: {
            CreateSessionMessage request = (CreateSessionMessage) packet;

            handleCreateSession(request);

            break;
         }
         case PacketImpl.CHECK_FOR_FAILOVER: {
            CheckFailoverMessage request = (CheckFailoverMessage) packet;

            handleCheckForFailover(request);

            break;
         }
         case PacketImpl.REATTACH_SESSION: {
            ReattachSessionMessage request = (ReattachSessionMessage) packet;

            handleReattachSession(request);

            break;
         }
         case PacketImpl.CREATE_QUEUE: {
            // Create queue can also be fielded here in the case of a replicated store and forward queue creation

            CreateQueueMessage request = (CreateQueueMessage) packet;

            handleCreateQueue(request);

            break;
         }
         default: {
            ActiveMQServerLogger.LOGGER.invalidPacket(packet);
         }
      }
   }

   private void handleCheckForFailover(CheckFailoverMessage failoverMessage) {
      String nodeID = failoverMessage.getNodeID();
      boolean okToFailover = nodeID == null || server.getNodeID().toString().equals(nodeID) || !(server.getHAPolicy().canScaleDown() && !server.hasScaledDown(SimpleString.of(nodeID)));
      channel1.send(new CheckFailoverReplyMessage(okToFailover));
   }

   private void handleCreateSession(final CreateSessionMessage request) {
      boolean incompatibleVersion = false;
      Packet response;
      try {
         Version version = server.getVersion();
         if (!version.isCompatible(request.getVersion())) {
            throw ActiveMQMessageBundle.BUNDLE.incompatibleClientServer();
         }

         if (!server.isStarted()) {
            throw ActiveMQMessageBundle.BUNDLE.serverNotStarted();
         }

         // Taylor commented out this test. Should be verified.
         /*if (!server.checkActivate())
         {
            throw new ActiveMQException(ActiveMQException.SESSION_CREATION_REJECTED,
                                       "Server will not accept create session requests");
         }*/

         if (connection.getChannelVersion() == 0) {
            connection.setChannelVersion(request.getVersion());
         } else if (connection.getChannelVersion() != request.getVersion()) {
            ActiveMQServerLogger.LOGGER.incompatibleVersionAfterConnect(request.getVersion(), connection.getChannelVersion());
         }

         if (request instanceof CreateSessionMessage_V2 v2) {
            connection.setClientID(v2.getClientID());
         }

         Channel channel = connection.getChannel(request.getSessionChannelID(), request.getWindowSize());

         ActiveMQPrincipal activeMQPrincipal = null;

         if (request.getUsername() == null) {
            activeMQPrincipal = connection.getDefaultActiveMQPrincipal();
         }

         final String validatedUser = server.validateUser(activeMQPrincipal == null ? request.getUsername() : activeMQPrincipal.getUserName(), activeMQPrincipal == null ? request.getPassword() : activeMQPrincipal.getPassword(), connection, protocolManager.getSecurityDomain());
         if (connection.getTransportConnection().getRouter() != null) {
            protocolManager.getRoutingHandler().route(connection, validatedUser != null ? validatedUser : request.getUsername());
         }

         OperationContext sessionOperationContext = server.newOperationContext();

         Map<SimpleString, RoutingType> routingTypeMap = protocolManager.getPrefixes();

         CoreSessionCallback sessionCallback = new CoreSessionCallback(request.getName(), protocolManager, channel, connection);
         boolean isLegacyProducer = request.getVersion() < PacketImpl.ARTEMIS_2_28_0_VERSION;
         ServerSession session = server.createSession(request.getName(), activeMQPrincipal == null ? request.getUsername() : activeMQPrincipal.getUserName(), activeMQPrincipal == null ? request.getPassword() : activeMQPrincipal.getPassword(), request.getMinLargeMessageSize(), connection, request.isAutoCommitSends(), request.isAutoCommitAcks(), request.isPreAcknowledge(), request.isXA(), request.getDefaultAddress(), sessionCallback, true, sessionOperationContext, routingTypeMap, protocolManager.getTemporaryPrefixes(), protocolManager.getSecurityDomain(), validatedUser, isLegacyProducer);
         ServerSessionPacketHandler handler = new ServerSessionPacketHandler(server, session, channel);
         channel.setHandler(handler);
         sessionCallback.setSessionHandler(handler);

         // TODO - where is this removed?
         protocolManager.addSessionHandler(request.getName(), handler);

         response = new CreateSessionResponseMessage(server.getVersion().getIncrementingVersion());
      } catch (ActiveMQClusterSecurityException | ActiveMQSecurityException e) {
         response = new ActiveMQExceptionMessage(e);
      } catch (ActiveMQException e) {
         if (e.getType() == ActiveMQExceptionType.INCOMPATIBLE_CLIENT_SERVER_VERSIONS) {
            incompatibleVersion = true;
            logger.debug("Sending ActiveMQException after Incompatible client", e);
         } else if (e.getType() == ActiveMQExceptionType.ROUTING_EXCEPTION) {
            logger.debug("Sending ActiveMQException after routing client", e);
         } else {
            ActiveMQServerLogger.LOGGER.failedToCreateSession(e);
         }

         response = new ActiveMQExceptionMessage(e);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToCreateSession(e);

         response = new ActiveMQExceptionMessage(new ActiveMQInternalErrorException());
      }

      // send the exception to the client and destroy
      // the connection if the client and server versions
      // are not compatible
      if (incompatibleVersion) {
         channel1.sendAndFlush(response);
      } else {
         channel1.send(response);
      }
   }

   private void handleReattachSession(final ReattachSessionMessage request) {
      Packet response = null;

      try {

         if (!server.isStarted()) {
            response = new ReattachSessionResponseMessage(-1, false);
         }

         logger.debug("Reattaching request from {}", connection.getRemoteAddress());

         ServerSessionPacketHandler sessionHandler = protocolManager.getSessionHandler(request.getName());

         if (/*!server.checkActivate() || */ sessionHandler == null) {
            response = new ReattachSessionResponseMessage(-1, false);
         } else {
            if (sessionHandler.getChannel().getConfirmationWindowSize() == -1) {
               // Even though session exists, we can't reattach since confi window size == -1,
               // i.e. we don't have a resend cache for commands, so we just close the old session
               // and let the client recreate

               ActiveMQServerLogger.LOGGER.reattachRequestFailed(connection.getRemoteAddress());

               sessionHandler.closeListeners();
               sessionHandler.close();

               response = new ReattachSessionResponseMessage(-1, false);
            } else {
               // Reconnect the channel to the new connection
               int serverLastConfirmedCommandID = sessionHandler.transferConnection(connection, request.getLastConfirmedCommandID());

               response = new ReattachSessionResponseMessage(serverLastConfirmedCommandID, true);
            }
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToReattachSession(e);

         response = new ActiveMQExceptionMessage(new ActiveMQInternalErrorException());
      }

      channel1.send(response);
   }

   private void handleCreateQueue(final CreateQueueMessage request) {
      try {
         server.createQueue(QueueConfiguration.of(request.getQueueName())
                               .setAddress(request.getAddress())
                               .setFilterString(request.getFilterString())
                               .setDurable(request.isDurable())
                               .setTemporary(request.isTemporary()));
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToHandleCreateQueue(e);
      }
   }
}
