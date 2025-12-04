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
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.util.UUID;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.protocol.mqtt.exceptions.DisconnectException;
import org.apache.activemq.artemis.core.protocol.mqtt.exceptions.InvalidClientIdException;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.utils.actors.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.AUTHENTICATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER;

/**
 * This class is responsible for receiving and sending MQTT packets, delegating behaviour to one of the
 * MQTTConnectionManager, MQTTPublishManager, MQTTSubscriptionManager classes.
 */
public class MQTTProtocolHandler extends ChannelInboundHandlerAdapter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ConnectionEntry connectionEntry;

   private MQTTConnection connection;

   private MQTTSession session;

   private ActiveMQServer server;

   private MQTTProtocolManager protocolManager;

   // This Channel Handler is not sharable, therefore it can only ever be associated with a single ctx.
   private ChannelHandlerContext ctx;

   private boolean stopped = false;

   private final Actor<MqttMessage> mqttMessageActor;

   public MQTTProtocolHandler(ActiveMQServer server, MQTTProtocolManager protocolManager) {
      this.server = server;
      this.protocolManager = protocolManager;
      this.mqttMessageActor = new Actor<>(server.getThreadPool(), this::act);
   }

   void setConnection(MQTTConnection connection, ConnectionEntry entry) throws Exception {
      this.connectionEntry = entry;
      this.connection = connection;
      this.session = new MQTTSession(this, connection, protocolManager, server.getConfiguration().getWildcardConfiguration(), server.newOperationContext());
      server.getStorageManager().setContext(session.getSessionContext());
   }

   void stop() {
      stopped = true;
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) {
      MqttMessage message = (MqttMessage) msg;

      if (stopped) {
         if (session.getVersion() == MQTTVersion.MQTT_5) {
            sendDisconnect(MQTTReasonCodes.IMPLEMENTATION_SPECIFIC_ERROR);
         }
         disconnect(true);
         return;
      }

      // Disconnect if Netty codec failed to decode the stream.
      if (message.decoderResult().isFailure()) {
         logger.debug("Disconnecting client due to message decoding failure.", message.decoderResult().cause());
         if (session.getVersion() == MQTTVersion.MQTT_5) {
            sendDisconnect(MQTTReasonCodes.MALFORMED_PACKET);
         }
         disconnect(true);
         return;
      }

      String interceptResult = this.protocolManager.invokeIncoming(message, this.connection);
      if (interceptResult != null) {
         logger.debug("Interceptor {} rejected MQTT control packet: {}", interceptResult, message);
         disconnect(true);
         return;
      }

      connection.dataReceived();

      if (AuditLogger.isAnyLoggingEnabled()) {
         AuditLogger.setRemoteAddress(connection.getRemoteAddress());
      }

      MQTTUtil.logMessage(session.getState(), message, true, session.getVersion());

      if (this.ctx == null) {
         this.ctx = ctx;
      }

      // let Netty handle client pings (i.e. connection keep-alive)
      if (MqttMessageType.PINGREQ == message.fixedHeader().messageType()) {
         handlePingreq();
      } else {
         mqttMessageActor.act(message);
      }
   }

   public void act(MqttMessage message) {
      try {
         switch (message.fixedHeader().messageType()) {
            case AUTH:
               handleAuth(message);
               break;
            case CONNECT:
               handleConnect((MqttConnectMessage) message);
               break;
            case PUBLISH:
               handlePublish((MqttPublishMessage) message);
               break;
            case PUBACK:
               handlePuback((MqttPubAckMessage) message);
               break;
            case PUBREC:
               handlePubrec(message);
               break;
            case PUBREL:
               handlePubrel(message);
               break;
            case PUBCOMP:
               handlePubcomp(message);
               break;
            case SUBSCRIBE:
               handleSubscribe((MqttSubscribeMessage) message);
               break;
            case UNSUBSCRIBE:
               handleUnsubscribe((MqttUnsubscribeMessage) message);
               break;
            case DISCONNECT:
               disconnect(false, message);
               break;
            case UNSUBACK:
            case SUBACK:
            case PINGREQ: // These are actually handled by the Netty thread directly so this packet should never make it here
            case PINGRESP:
            case CONNACK: // The server does not instantiate connections therefore any CONNACK received over a connection is an invalid control message.
            default:
               disconnect(true);
         }
      } catch (Exception e) {
         MQTTLogger.LOGGER.errorProcessingControlPacket(message.toString(), e);
         if (session.getVersion() == MQTTVersion.MQTT_5) {
            sendDisconnect(MQTTReasonCodes.IMPLEMENTATION_SPECIFIC_ERROR);
         }
         disconnect(true);
      } finally {
         ReferenceCountUtil.release(message);
      }
   }

   /*
    * Scaffolding for "enhanced authentication" implementation.
    *
    * See:
    *   https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901217
    *   https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901256
    *
    * Tests for this are in:
    *   org.apache.activemq.artemis.tests.integration.mqtt5.spec.controlpackets.AuthTests
    *   org.apache.activemq.artemis.tests.integration.mqtt5.spec.EnhancedAuthenticationTests
    *
    * This should integrate somehow with our existing SASL implementation for challenge/response conversations.
    */
   void handleAuth(MqttMessage auth) throws Exception {
      byte[] authenticationData = MQTTUtil.getProperty(byte[].class, ((MqttReasonCodeAndPropertiesVariableHeader)auth.variableHeader()).properties(), AUTHENTICATION_DATA);
      String authenticationMethod = MQTTUtil.getProperty(String.class, ((MqttReasonCodeAndPropertiesVariableHeader)auth.variableHeader()).properties(), AUTHENTICATION_METHOD);

      MqttReasonCodeAndPropertiesVariableHeader header = (MqttReasonCodeAndPropertiesVariableHeader) auth.variableHeader();
      if (header.reasonCode() == MQTTReasonCodes.RE_AUTHENTICATE) {

      } else if (header.reasonCode() == MQTTReasonCodes.CONTINUE_AUTHENTICATION) {

      } else if (header.reasonCode() == MQTTReasonCodes.SUCCESS) {

      }
   }

   void handleConnect(MqttConnectMessage connect) throws Exception {
      session.setVersion(MQTTVersion.getVersion(connect.variableHeader().version()));
      if (!checkClientVersion()) {
         return;
      }

      session.getConnection().setClientID(connect.payload().clientIdentifier());
      if (!validateClientID(connect.variableHeader().isCleanSession())) {
         return;
      }

      // Perform authentication *before* attempting redirection because redirection may be based on the user's role.
      String password = connect.payload().passwordInBytes() == null ? null : new String(connect.payload().passwordInBytes(), CharsetUtil.UTF_8);
      String username = connect.payload().userName();
      Pair<Boolean, String> validationData = null;
      try {
         validationData = validateUser(username, password);
         if (!validationData.getA()) {
            return;
         }
      } catch (InvalidClientIdException e) {
         handleInvalidClientId();
         return;
      }

      if (handleLinkStealing() == LinkStealingResult.NEW_LINK_DENIED) {
         return;
      } else {
         protocolManager.getStateManager().addConnectedClient(session.getConnection().getClientID(), session.getConnection());
      }

      if (connection.getTransportConnection().getRouter() == null || !protocolManager.getRoutingHandler().route(connection, session, validationData.getB() != null ? validationData.getB() : username)) {
         calculateKeepAlive(connect);

         session.getConnectionManager().connect(connect, validationData.getB(), username, password);
      }
   }

   void disconnect(boolean error) {
      disconnect(error, null);
   }

   void disconnect(boolean error, MqttMessage disconnect) {
      if (disconnect != null && disconnect.variableHeader() instanceof MqttReasonCodeAndPropertiesVariableHeader) {
         Integer sessionExpiryInterval = MQTTUtil.getProperty(Integer.class, ((MqttReasonCodeAndPropertiesVariableHeader)disconnect.variableHeader()).properties(), SESSION_EXPIRY_INTERVAL, null);
         if (sessionExpiryInterval != null) {
            try {
               session.getState().setClientSessionExpiryInterval(sessionExpiryInterval);
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }
      }
      session.getConnectionManager().disconnect(error);
   }

   void sendConnack(byte returnCode) {
      sendConnack(returnCode, MqttProperties.NO_PROPERTIES);
   }

   void sendConnack(byte returnCode, MqttProperties properties) {
      sendConnack(returnCode, true, properties);
   }

   void sendConnack(byte returnCode, boolean sessionPresent, MqttProperties properties) {
      // 3.1.1 - [MQTT-3.2.2-4] If a server sends a CONNACK packet containing a non-zero return code it MUST set Session Present to 0.
      // 5     - [MQTT-3.2.2-6] If a Server sends a CONNACK packet containing a non-zero Reason Code it MUST set Session Present to 0.
      if (returnCode != MQTTReasonCodes.SUCCESS) {
         sessionPresent = false;
      }
      sendToClient(MqttMessageBuilders
                      .connAck()
                      .returnCode(MqttConnectReturnCode.valueOf(returnCode))
                      .properties(properties)
                      .sessionPresent(sessionPresent)
                      .build());
   }

   void sendDisconnect(byte reasonCode) {
      sendToClient(MqttMessageBuilders
                      .disconnect()
                      .reasonCode(reasonCode)
                      .build());
   }

   void handlePublish(MqttPublishMessage message) throws Exception {
      if (session.getVersion() == MQTTVersion.MQTT_5 && session.getProtocolManager().getMaximumPacketSize() != -1 && MQTTUtil.calculateMessageSize(message) > session.getProtocolManager().getMaximumPacketSize()) {
         sendDisconnect(MQTTReasonCodes.PACKET_TOO_LARGE);
         disconnect(true);
         return;
      }

      try {
         session.getMqttPublishManager().sendToQueue(message, false);
      } catch (DisconnectException e) {
         if (session.getVersion() == MQTTVersion.MQTT_5) {
            sendDisconnect(e.getCode());
         }
         disconnect(true);
      }
   }

   void sendPubAck(int messageId, byte reasonCode) {
      sendPublishProtocolControlMessage(messageId, MqttMessageType.PUBACK, reasonCode);
   }

   void sendPubRel(int messageId) {
      sendPublishProtocolControlMessage(messageId, MqttMessageType.PUBREL);
   }

   void sendPubRec(int messageId, byte reasonCode) {
      sendPublishProtocolControlMessage(messageId, MqttMessageType.PUBREC, reasonCode);
   }

   void sendPubComp(int messageId) {
      sendPublishProtocolControlMessage(messageId, MqttMessageType.PUBCOMP);
   }

   void sendPublishProtocolControlMessage(int messageId, MqttMessageType messageType) {
      sendPublishProtocolControlMessage(messageId, messageType, MQTTReasonCodes.SUCCESS);
   }

   void sendPublishProtocolControlMessage(int messageId, MqttMessageType messageType, byte reasonCode) {
      // [MQTT-3.6.1-1] Spec requires 01 in header for rel
      MqttFixedHeader fixedHeader = new MqttFixedHeader(messageType, false, (messageType == MqttMessageType.PUBREL) ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE, false, 0);

      MqttMessageIdVariableHeader variableHeader;
      if (session.getVersion() == MQTTVersion.MQTT_5) {
         variableHeader = new MqttPubReplyMessageVariableHeader(messageId, reasonCode, MqttProperties.NO_PROPERTIES);
      } else {
         variableHeader = MqttMessageIdVariableHeader.from(messageId);
      }
      MqttPubAckMessage pubAck = new MqttPubAckMessage(fixedHeader, variableHeader);
      sendToClient(pubAck);
   }

   void handlePuback(MqttPubAckMessage message) throws Exception {
      // ((MqttPubReplyMessageVariableHeader)message.variableHeader()).reasonCode();
      session.getMqttPublishManager().handlePubAck(getMessageId(message));
   }

   void handlePubrec(MqttMessage message) throws Exception {
      session.getMqttPublishManager().handlePubRec(getMessageId(message));
   }

   void handlePubrel(MqttMessage message) {
      session.getMqttPublishManager().handlePubRel(getMessageId(message));
   }

   void handlePubcomp(MqttMessage message) throws Exception {
      session.getMqttPublishManager().handlePubComp(getMessageId(message));
   }

   void handleSubscribe(MqttSubscribeMessage message) throws Exception {
      Integer subscriptionIdentifier = MQTTUtil.getProperty(Integer.class, message.idAndPropertiesVariableHeader().properties(), SUBSCRIPTION_IDENTIFIER, null);
      int[] qos = session.getSubscriptionManager().addSubscriptions(message.payload().topicSubscriptions(), subscriptionIdentifier);
      MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
      MqttMessageIdAndPropertiesVariableHeader variableHeader = new MqttMessageIdAndPropertiesVariableHeader(message.variableHeader().messageId(), MqttProperties.NO_PROPERTIES);
      MqttSubAckMessage subAck = new MqttSubAckMessage(header, variableHeader, new MqttSubAckPayload(qos));
      sendToClient(subAck);
   }

   void handleUnsubscribe(MqttUnsubscribeMessage message) throws Exception {
      short[] reasonCodes = session.getSubscriptionManager().removeSubscriptions(message.payload().topics(), true);
      MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
      MqttUnsubAckMessage unsubAck;
      if (session.getVersion() == MQTTVersion.MQTT_5) {
         unsubAck = new MqttUnsubAckMessage(header, message.variableHeader(), new MqttUnsubAckPayload(reasonCodes));
      } else {
         unsubAck = new MqttUnsubAckMessage(header, message.variableHeader());
      }
      sendToClient(unsubAck);
   }

   void handlePingreq() {
      MqttMessage pingResp = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0));
      sendToClient(pingResp);
   }

   protected void sendToClient(MqttMessage message) {
      if (this.protocolManager.invokeOutgoing(message, connection) != null) {
         return;
      }
      MQTTUtil.logMessage(session.getState(), message, false, session.getVersion());
      server.getStorageManager().afterCompleteOperations(new IOCallback() {
         @Override
         public void done() {
            ctx.writeAndFlush(message, ctx.voidPromise());
         }

         @Override
         public void onError(int errorCode, String errorMessage) {
         }
      });
   }

   private int getMessageId(MqttMessage message) {
      return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
   }

   ActiveMQServer getServer() {
      return server;
   }

   /*
    * If the server's keep-alive has been disabled (-1) or if the client is using a lower value than the server
    * then we use the client's keep-alive.
    *
    * We must adjust the keep-alive because MQTT communicates keep-alive values in *seconds*, but the broker uses
    * *milliseconds*. Also, the connection keep-alive is effectively "one and a half times" the configured
    * keep-alive value. See [MQTT-3.1.2-22].
    */
   private void calculateKeepAlive(MqttConnectMessage connect) {
      int serverKeepAlive = session.getProtocolManager().getServerKeepAlive();
      int clientKeepAlive = connect.variableHeader().keepAliveTimeSeconds();
      if (serverKeepAlive == -1 || (clientKeepAlive <= serverKeepAlive && clientKeepAlive != 0)) {
         connectionEntry.ttl = clientKeepAlive * MQTTUtil.KEEP_ALIVE_ADJUSTMENT;
      } else {
         session.setUsingServerKeepAlive(true);
      }
   }

   // [MQTT-3.1.2-2] Reject unsupported clients.
   private boolean checkClientVersion() {
      if (session.getVersion() != MQTTVersion.MQTT_3_1 &&
         session.getVersion() != MQTTVersion.MQTT_3_1_1 &&
         session.getVersion() != MQTTVersion.MQTT_5) {

         if (session.getVersion().getVersion() <= MQTTVersion.MQTT_3_1_1.getVersion()) {
            // See MQTT-3.1.2-2 at http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc398718030
            sendConnack(MQTTReasonCodes.UNACCEPTABLE_PROTOCOL_VERSION_3);
         } else {
            // See MQTT-3.1.2-2 at https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901037
            sendConnack(MQTTReasonCodes.UNSUPPORTED_PROTOCOL_VERSION);
         }

         disconnect(true);
         return false;
      }
      return true;
   }

   /*
    * The MQTT specification states:
    *
    *     [MQTT-3.1.4-2] If the client ID represents a client already connected to the server then the server MUST
    *     disconnect the existing client
    *
    * However, this behavior is configurable via the "allowLinkStealing" acceptor URL property.
    */
   private LinkStealingResult handleLinkStealing() throws Exception {
      final String clientID = session.getConnection().getClientID();
      LinkStealingResult result;

      MQTTConnection existingConnection = protocolManager.getStateManager().getConnectedClient(clientID);
      if (existingConnection != null) {
         if (protocolManager.isAllowLinkStealing()) {
            MQTTSession existingSession = protocolManager.getStateManager().getSessionState(clientID).getSession();
            if (existingSession != null) {
               if (existingSession.getVersion() == MQTTVersion.MQTT_5) {
                  existingSession.getProtocolHandler().sendDisconnect(MQTTReasonCodes.SESSION_TAKEN_OVER);
               }
               existingSession.getConnectionManager().disconnect(false);
            } else {
               existingConnection.disconnect(false);
            }
            logger.debug("Existing MQTT session from {} closed due to incoming session from {} with the same client ID: {}", existingConnection.getRemoteAddress(), connection.getRemoteAddress(), session.getConnection().getClientID());
            result = LinkStealingResult.EXISTING_LINK_STOLEN;
         } else {
            if (session.getVersion() == MQTTVersion.MQTT_5) {
               sendDisconnect(MQTTReasonCodes.UNSPECIFIED_ERROR);
            }
            logger.debug("Incoming MQTT session from {} closed due to existing session from {} with the same client ID: {}", connection.getRemoteAddress(), existingConnection.getRemoteAddress(), session.getConnection().getClientID());
            /*
             * Stopping the session here prevents the connection failure listener from inadvertently removing the
             * existing session once the connection is disconnected.
             */
            session.setStopped(true);
            connection.disconnect(false);
            result = LinkStealingResult.NEW_LINK_DENIED;
         }
      } else {
         result = LinkStealingResult.NO_ACTION;
      }

      return result;
   }

   private Pair<Boolean, String> validateUser(String username, String password) throws Exception {
      String validatedUser = null;
      Boolean result;

      try {
         validatedUser = server.validateUser(username, password, session.getConnection(), session.getProtocolManager().getSecurityDomain());
         result = Boolean.TRUE;
      } catch (ActiveMQSecurityException e) {
         if (session.getVersion() == MQTTVersion.MQTT_5) {
            session.getProtocolHandler().sendConnack(MQTTReasonCodes.BAD_USER_NAME_OR_PASSWORD);
         } else {
            session.getProtocolHandler().sendConnack(MQTTReasonCodes.NOT_AUTHORIZED_3);
         }
         // avoid a race with sending the CONNACK packet and disconnecting the client
         server.getStorageManager().afterCompleteOperations(new IOCallback() {
            @Override
            public void done() {
               disconnect(true);
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
            }
         });
         result = Boolean.FALSE;
      }

      return new Pair<>(result, validatedUser);
   }

   private boolean validateClientID(boolean isCleanSession) {
      if (session.getConnection().getClientID() == null || session.getConnection().getClientID().isEmpty()) {
         // [MQTT-3.1.3-7] [MQTT-3.1.3-6] If client does not specify a client ID and clean session is set to 1 create it.
         if (isCleanSession) {
            session.getConnection().setClientID(UUID.randomUUID().toString());
            session.getConnection().setClientIdAssignedByBroker(true);
         } else {
            // [MQTT-3.1.3-8] Return ID rejected and disconnect if clean session = false and client id is null
            return handleInvalidClientId();
         }
      }
      return true;
   }

   private boolean handleInvalidClientId() {
      if (session.getVersion() == MQTTVersion.MQTT_5) {
         session.getProtocolHandler().sendConnack(MQTTReasonCodes.CLIENT_IDENTIFIER_NOT_VALID);
      } else {
         session.getProtocolHandler().sendConnack(MQTTReasonCodes.IDENTIFIER_REJECTED_3);
      }
      disconnect(true);
      return false;
   }

   private enum LinkStealingResult {
      EXISTING_LINK_STOLEN, NEW_LINK_DENIED, NO_ACTION;
   }
}
