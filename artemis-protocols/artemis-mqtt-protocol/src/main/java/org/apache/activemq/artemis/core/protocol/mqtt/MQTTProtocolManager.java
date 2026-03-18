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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.CleaningActivateCallback;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationListener;
import org.apache.activemq.artemis.spi.core.protocol.AbstractProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTTProtocolManager extends AbstractProtocolManager<MqttMessage, MQTTInterceptor, MQTTConnection, MQTTRoutingHandler> implements NotificationListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final List<String> websocketRegistryNames = Arrays.asList("mqtt", "mqttv3.1");

   private ActiveMQServer server;

   private final List<MQTTInterceptor> incomingInterceptors = new ArrayList<>();
   private final List<MQTTInterceptor> outgoingInterceptors = new ArrayList<>();

   private int defaultMqttSessionExpiryInterval = -1;

   private int topicAliasMaximum = MQTTUtil.DEFAULT_TOPIC_ALIAS_MAX;

   private int receiveMaximum = MQTTUtil.DEFAULT_RECEIVE_MAXIMUM;

   private int serverKeepAlive = MQTTUtil.DEFAULT_SERVER_KEEP_ALIVE;

   private int maximumPacketSize = MQTTUtil.DEFAULT_MAXIMUM_PACKET_SIZE;

   private boolean closeMqttConnectionOnPublishAuthorizationFailure = true;

   private boolean allowLinkStealing = true;

   private int defaultMaximumInFlightPublishMessages = MQTTUtil.DEFAULT_MAXIMUM_IN_FLIGHT_PUBLISH_MESSAGES;

   private final MQTTRoutingHandler routingHandler;

   private MQTTStateManager sessionStateManager;

   MQTTProtocolManager(ActiveMQServer server,
                       List<BaseInterceptor> incomingInterceptors,
                       List<BaseInterceptor> outgoingInterceptors) throws Exception {
      this.server = server;
      this.updateInterceptors(incomingInterceptors, outgoingInterceptors);
      server.getManagementService().addNotificationListener(this);
      routingHandler = new MQTTRoutingHandler(server);
      sessionStateManager = MQTTStateManager.getInstance(server);
      server.registerActivateCallback(new CleaningActivateCallback() {
         @Override
         public void deActivate() {
            MQTTStateManager.removeInstance(server);
            sessionStateManager = null;
         }
      });
   }

   public int getDefaultMqttSessionExpiryInterval() {
      return defaultMqttSessionExpiryInterval;
   }

   public MQTTProtocolManager setDefaultMqttSessionExpiryInterval(int sessionExpiryInterval) {
      this.defaultMqttSessionExpiryInterval = sessionExpiryInterval;
      return this;
   }

   public int getTopicAliasMaximum() {
      return topicAliasMaximum;
   }

   public MQTTProtocolManager setTopicAliasMaximum(int topicAliasMaximum) {
      this.topicAliasMaximum = topicAliasMaximum;
      return this;
   }

   public int getReceiveMaximum() {
      return receiveMaximum;
   }

   public MQTTProtocolManager setReceiveMaximum(int receiveMaximum) {
      this.receiveMaximum = receiveMaximum;
      return this;
   }

   public int getMaximumPacketSize() {
      return maximumPacketSize;
   }

   public MQTTProtocolManager setMaximumPacketSize(int maximumPacketSize) {
      this.maximumPacketSize = maximumPacketSize;
      return this;
   }

   public int getServerKeepAlive() {
      return serverKeepAlive;
   }

   public MQTTProtocolManager setServerKeepAlive(int serverKeepAlive) {
      this.serverKeepAlive = serverKeepAlive;
      return this;
   }

   public boolean isCloseMqttConnectionOnPublishAuthorizationFailure() {
      return closeMqttConnectionOnPublishAuthorizationFailure;
   }

   public void setCloseMqttConnectionOnPublishAuthorizationFailure(boolean closeMqttConnectionOnPublishAuthorizationFailure) {
      this.closeMqttConnectionOnPublishAuthorizationFailure = closeMqttConnectionOnPublishAuthorizationFailure;
   }

   public boolean isAllowLinkStealing() {
      return allowLinkStealing;
   }

   public void setAllowLinkStealing(boolean allowLinkStealing) {
      this.allowLinkStealing = allowLinkStealing;
   }

   public int getDefaultMaximumInFlightPublishMessages() {
      return defaultMaximumInFlightPublishMessages;
   }

   public void setDefaultMaximumInFlightPublishMessages(int defaultMaximumInFlightPublishMessages) {
      this.defaultMaximumInFlightPublishMessages = defaultMaximumInFlightPublishMessages;
   }

   @Override
   public void onNotification(Notification notification) {
      if (!(notification.getType() instanceof CoreNotificationType type)) {
         return;
      }

      if (type != CoreNotificationType.SESSION_CREATED) {
         return;
      }

      TypedProperties props = notification.getProperties();

      SimpleString protocolName = props.getSimpleStringProperty(ManagementHelper.HDR_PROTOCOL_NAME);

      //Only process SESSION_CREATED notifications for the MQTT protocol
      if (protocolName == null || !protocolName.toString().startsWith(MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME)) {
         return;
      }

      int distance = props.getIntProperty(ManagementHelper.HDR_DISTANCE);

      //distance > 0 means only processing notifications which are received from other nodes in the cluster
      if (distance > 0) {
         String clientId = props.getSimpleStringProperty(ManagementHelper.HDR_CLIENT_ID).toString();
         /*
          * If there is a connection in the node with the same clientId as the value of the "_AMQ_Client_ID" attribute
          * in the SESSION_CREATED notification, you need to close this connection.
          * Avoid consumers with the same client ID in the cluster appearing at different nodes at the same time.
          */
         MQTTConnection mqttConnection = sessionStateManager.getConnectedClients().get(clientId);
         if (mqttConnection != null) {
            mqttConnection.destroy();
         }
      }
   }

   @Override
   public ProtocolManagerFactory getFactory() {
      return new MQTTProtocolManagerFactory();
   }

   @Override
   public void updateInterceptors(List incoming, List outgoing) {
      this.incomingInterceptors.clear();
      this.incomingInterceptors.addAll(getFactory().filterInterceptors(incoming));

      this.outgoingInterceptors.clear();
      this.outgoingInterceptors.addAll(getFactory().filterInterceptors(outgoing));
   }

   @Override
   public ConnectionEntry createConnectionEntry(Acceptor acceptorUsed, Connection connection) {
      try {
         MQTTConnection mqttConnection = new MQTTConnection(connection);
         /*
          * We must adjust the keep-alive because MQTT communicates keep-alive values in *seconds*, but the broker uses
          * *milliseconds*. Also, the connection keep-alive is effectively "one and a half times" the configured
          * keep-alive value. See [MQTT-3.1.2-22].
          */
         ConnectionEntry entry = new ConnectionEntry(mqttConnection, null, System.currentTimeMillis(), getServerKeepAlive() == -1 || getServerKeepAlive() == 0 ? -1 : getServerKeepAlive() * MQTTUtil.KEEP_ALIVE_ADJUSTMENT);

         NettyServerConnection nettyConnection = ((NettyServerConnection) connection);
         MQTTProtocolHandler protocolHandler = nettyConnection.getChannel().pipeline().get(MQTTProtocolHandler.class);
         protocolHandler.setConnection(mqttConnection, entry);
         return entry;
      } catch (Exception e) {
         logger.error("Error creating connection entry", e);
         return null;
      }
   }

   @Override
   public boolean acceptsNoHandshake() {
      return false;
   }

   @Override
   public void handleBuffer(RemotingConnection connection, ActiveMQBuffer buffer) {
      connection.bufferReceived(connection.getID(), buffer);
   }

   @Override
   public void addChannelHandlers(ChannelPipeline pipeline) {
      pipeline.addLast(MqttEncoder.INSTANCE);
      /*
       * If we use the value from getMaximumPacketSize() here anytime a client sends a packet that's too large it
       * will receive a DISCONNECT with a reason code of 0x81 instead of 0x95 like it should according to the spec.
       * See https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901086:
       *
       *   If a Server receives a packet whose size exceeds this limit, this is a Protocol Error, the Server uses
       *   DISCONNECT with Reason Code 0x95 (Packet too large)...
       *
       * Therefore we check manually in org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolHandler.handlePublish
       */
      pipeline.addLast(new MqttDecoder(MQTTUtil.MAX_PACKET_SIZE));

      pipeline.addLast(new MQTTProtocolHandler(server, this));
   }

   /**
    * Relevant portions of the specs we support:
    * <ul>
    * <li>MQTT 3.1 - https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#connect
    * <li>MQTT 3.1.1 - http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc398718028
    * <li>MQTT 5 - https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901033
    * </ul>
    */
   @Override
   public boolean isProtocol(byte[] array) {
      ByteBuf buf = Unpooled.wrappedBuffer(array);

      // Parse "fixed header"
      if (!(readByte(buf) == 16 && validateRemainingLength(buf) && readByte(buf) == (byte) 0)) {
         return false;
      }

      /*
       * Parse the protocol name by looking at the length LSB and the next 2 bytes (which should be "MQ").
       * This should be 4 for MQTT 5 & 3.1.1 because they both use "MQTT"
       * This should be or 6 for MQTT 3.1 because it uses "MQIsdp"
       */
      byte b = readByte(buf);
      if ((b == 4 || b == 6) &&
         (readByte(buf) != 77 || // M
         readByte(buf) != 81)) { // Q
         return false;
      }

      return true;
   }

   byte readByte(ByteBuf buf) {
      byte b = buf.readByte();
      if (logger.isTraceEnabled()) {
         logger.trace(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
      }
      return b;
   }

   private boolean validateRemainingLength(ByteBuf buffer) {
      byte msb = (byte) 0b10000000;
      for (byte i = 0; i < 4; i++) {
         if ((readByte(buffer) & msb) != msb) {
            return true;
         }
      }
      return false;
   }

   @Override
   public void handshake(NettyServerConnection connection, ActiveMQBuffer buffer) {
   }

   @Override
   public List<String> websocketSubprotocolIdentifiers() {
      return websocketRegistryNames;
   }

   @Override
   public MQTTRoutingHandler getRoutingHandler() {
      return routingHandler;
   }

   public String invokeIncoming(MqttMessage mqttMessage, MQTTConnection connection) {
      return super.invokeInterceptors(this.incomingInterceptors, mqttMessage, connection);
   }

   public String invokeOutgoing(MqttMessage mqttMessage, MQTTConnection connection) {
      return super.invokeInterceptors(this.outgoingInterceptors, mqttMessage, connection);
   }

   public MQTTStateManager getStateManager() {
      return sessionStateManager;
   }
}
