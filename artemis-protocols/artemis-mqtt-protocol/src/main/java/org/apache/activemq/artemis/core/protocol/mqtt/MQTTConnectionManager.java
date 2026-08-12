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

import java.util.List;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.utils.UUIDGenerator;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SERVER_KEEP_ALIVE;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.TOPIC_ALIAS_MAXIMUM;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL;

/**
 * MQTTConnectionManager is responsible for handle Connect and Disconnect packets and any resulting behaviour of these
 * events.
 */
public class MQTTConnectionManager {

   private MQTTSession session;

   public MQTTConnectionManager(MQTTSession session) {
      this.session = session;
      MQTTFailureListener failureListener = new MQTTFailureListener(this);
      session.getConnection().addFailureListener(failureListener);
   }

   synchronized void connect(MqttConnectMessage connect, String validatedUser, String username, String password) throws Exception {
      if (session.getVersion() == MQTTVersion.MQTT_5) {
         session.getConnection().setProtocolVersion(Byte.toString(MqttVersion.MQTT_5.protocolLevel()));
         String authenticationMethod = MQTTUtil.getProperty(String.class, connect.variableHeader().properties(), AUTHENTICATION_METHOD);

         if (authenticationMethod != null) {
            session.getProtocolHandler().sendConnack(MQTTReasonCodes.BAD_AUTHENTICATION_METHOD);
            disconnect(true);
            return;
         }
      }

      // the Netty codec uses "CleanSession" for both 3.1.1 "clean session" and 5 "clean start" which have slightly different semantics
      boolean cleanStart = connect.variableHeader().isCleanSession();

      String clientId = session.getConnection().getClientID();
      boolean sessionPresent = session.getStateManager().getSessionStates().containsKey(clientId);
      MQTTSessionState sessionState = session.getStateManager().getSessionState(clientId);
      session.setSessionState(sessionState);
      sessionState.setFailed(false);
      ServerSessionImpl serverSession = createServerSession(username, password, validatedUser);
      serverSession.start();
      session.setServerSession(serverSession);

      if (cleanStart) {
         /*
          * [MQTT-3.1.2-6] If CleanSession is set to 1, the Client and Server MUST discard any previous Session and
          * start a new one. This Session lasts as long as the Network Connection. State data associated with this Session
          * MUST NOT be reused in any subsequent Session
          */
         session.clean(true);
         session.setClean(true);
      }

      if (connect.variableHeader().isWillFlag()) {
         session.getState().setWill(true);
         byte[] willMessage = connect.payload().willMessageInBytes();
         session.getState().setWillMessage(ByteBufAllocator.DEFAULT.buffer(willMessage.length).writeBytes(willMessage));
         session.getState().setWillQoSLevel(connect.variableHeader().willQos());
         session.getState().setWillRetain(connect.variableHeader().isWillRetain());
         session.getState().setWillTopic(connect.payload().willTopic());
         session.getState().setWillStatus(MQTTSessionState.WillStatus.NOT_SENT);

         if (session.getVersion() == MQTTVersion.MQTT_5) {
            MqttProperties willProperties = connect.payload().willProperties();
            if (willProperties != null) {
               MqttProperties.MqttProperty willDelayInterval = willProperties.getProperty(WILL_DELAY_INTERVAL.value());
               if (willDelayInterval != null) {
                  session.getState().setWillDelayInterval((int) willDelayInterval.value());
               }
               List<? extends MqttProperties.MqttProperty> userProperties = willProperties.getProperties(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
               if (userProperties != null) {
                  session.getState().setWillUserProperties(userProperties);
               }
            }
         }
      }

      MqttProperties connackProperties;
      if (session.getVersion() == MQTTVersion.MQTT_5) {
         session.getConnection().setReceiveMaximum(MQTTUtil.getProperty(Integer.class, connect.variableHeader().properties(), RECEIVE_MAXIMUM, -1));

         sessionState.setClientSessionExpiryInterval(MQTTUtil.getProperty(Integer.class, connect.variableHeader().properties(), SESSION_EXPIRY_INTERVAL, 0));
         sessionState.setClientMaxPacketSize(MQTTUtil.getProperty(Integer.class, connect.variableHeader().properties(), MAXIMUM_PACKET_SIZE, 0));
         sessionState.setClientTopicAliasMaximum(MQTTUtil.getProperty(Integer.class, connect.variableHeader().properties(), TOPIC_ALIAS_MAXIMUM));

         connackProperties = getConnackProperties();
      } else {
         sessionState.setClientSessionExpiryInterval(session.getProtocolManager().getDefaultMqttSessionExpiryInterval());
         connackProperties = MqttProperties.NO_PROPERTIES;
      }

      if (sessionState.getClientSessionExpiryInterval() == -1 || sessionState.getClientSessionExpiryInterval() > 0) {
         session.getStateManager().storeDurableState(sessionState);
      }

      session.getConnection().setConnected(true);
      session.getProtocolHandler().sendConnack(MQTTReasonCodes.SUCCESS, sessionPresent && !cleanStart, connackProperties);
      // [MQTT-3.2.0-1] the CONNACK is sent via IO callback so the session should be started the same way to avoid a race
      session.getProtocolHandler().runAfterStorageOperations(() -> {
         try {
            session.start();
         } catch (Exception e) {
            MQTTLogger.LOGGER.errorDisconnectingClient(e);
            disconnect(true);
         }
      });
   }

   private MqttProperties getConnackProperties() {
      MqttProperties connackProperties = new MqttProperties();

      if (this.session.getConnection().isClientIdAssignedByBroker()) {
         connackProperties.add(new MqttProperties.StringProperty(ASSIGNED_CLIENT_IDENTIFIER.value(), this.session.getConnection().getClientID()));
      }

      if (this.session.getProtocolManager().getTopicAliasMaximum() != -1) {
         connackProperties.add(new MqttProperties.IntegerProperty(TOPIC_ALIAS_MAXIMUM.value(), this.session.getProtocolManager().getTopicAliasMaximum()));
      }

      if (this.session.isUsingServerKeepAlive()) {
         connackProperties.add(new MqttProperties.IntegerProperty(SERVER_KEEP_ALIVE.value(), this.session.getProtocolManager().getServerKeepAlive()));
      }

      if (this.session.getProtocolManager().getMaximumPacketSize() != -1) {
         connackProperties.add(new MqttProperties.IntegerProperty(MAXIMUM_PACKET_SIZE.value(), this.session.getProtocolManager().getMaximumPacketSize()));
      }

      return connackProperties;
   }

   ServerSessionImpl createServerSession(String username, String password, String validatedUser) throws Exception {
      String id = UUIDGenerator.getInstance().generateStringUUID();
      ActiveMQServer server = session.getServer();
      ServerSession serverSession = server.createSession(id,
                                                         username,
                                                         password,
                                                         ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                                                         session.getConnection(),
                                                         MQTTUtil.SESSION_AUTO_COMMIT_SENDS,
                                                         MQTTUtil.SESSION_AUTO_COMMIT_ACKS,
                                                         MQTTUtil.SESSION_PREACKNOWLEDGE,
                                                         MQTTUtil.SESSION_XA,
                                                         null,
                                                         session.getSessionCallback(),
                                                         MQTTUtil.SESSION_AUTO_CREATE_QUEUE,
                                                         session.getSessionContext(),
                                                         session.getProtocolManager().getPrefixes(),
                                                         session.getProtocolManager().getSecurityDomain(),
                                                         validatedUser,
                                                         false);
      return (ServerSessionImpl) serverSession;
   }

   synchronized void disconnect(boolean failure) {
      if (session == null || session.getStopped()) {
         return;
      }

      try {
         session.stop(failure);
         session.getConnection().destroy();
      } catch (Exception e) {
         MQTTLogger.LOGGER.errorDisconnectingClient(e);
      } finally {
         if (session.getState() != null) {
            String clientId = session.getState().getClientId();
            /*
             *  ensure that the connection for the client ID matches *this* connection otherwise we could remove the
             *  entry for the client who "stole" this client ID via [MQTT-3.1.4-2]
             */
            if (clientId != null && session.getStateManager().isClientConnected(clientId, session.getConnection())) {
               session.getStateManager().removeConnectedClient(clientId);
            }
         }
      }
   }
}
