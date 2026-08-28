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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.EmptyByteBuf;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.impl.journal.ActiveMQIDGeneratorStoppedException;
import org.apache.activemq.artemis.core.protocol.mqtt.exceptions.DisconnectException;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.CONTENT_TYPE;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.CORRELATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.RESPONSE_TOPIC;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.TOPIC_ALIAS;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_CONTENT_TYPE_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_CORRELATION_DATA_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_MESSAGE_RETAIN_INITIAL_DISTRIBUTION_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_MESSAGE_RETAIN_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_PAYLOAD_FORMAT_INDICATOR_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_RESPONSE_TOPIC_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_USER_PROPERTY_EXISTS_KEY;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.MQTT_USER_PROPERTY_KEY_PREFIX_SIMPLE;

/**
 * Handles MQTT Exactly Once (QoS level 2) Protocol.
 */
public class MQTTPublishManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String senderName = UUIDGenerator.getInstance().generateUUID().toString();

   private boolean createProducer = true;

   private final MQTTSession session;

   private final Object lock = new Object();

   private boolean closeMqttConnectionOnPublishAuthorizationFailure;

   public MQTTPublishManager(MQTTSession session, boolean closeMqttConnectionOnPublishAuthorizationFailure) {
      this.session = session;
      this.closeMqttConnectionOnPublishAuthorizationFailure = closeMqttConnectionOnPublishAuthorizationFailure;
   }

   void stop() throws Exception {
      ServerSessionImpl serversession = session.getServerSession();
      if (serversession != null) {
         serversession.removeProducer(serversession.getName());
      }
   }

   /**
    * Delivers a message to the MQTT client at the appropriate QoS level. For QoS 1 and 2, each delivery is tracked by a
    * journal-persisted {@link PacketIdCorrelationKey} that pairs the core message ID and subscription address to an
    * MQTT packet ID. This ensures the same packet ID is reused when a message is redelivered after a broker restart, as
    * required by the MQTT specification. The address component of the key allows overlapping subscriptions to receive
    * the same message with distinct packet IDs.
    * <p>
    * A {@link CoreDeliveryInfo} is also stored in-memory for each in-flight packet ID, mapping it back to the consumer
    * ID and correlation key so the correct consumer can be acknowledged when the client responds with PUBACK or
    * PUBCOMP.
    */
   protected void publishToClient(ICoreMessage message, ServerConsumer consumer) throws Exception {
      MQTTSessionState state = session.getState();
      int qos = decideQoS(message, consumer);
      if (qos == 0) {
         // [MQTT-2.2.1-2] Hard-code the packet ID to 0 as QoS0 PUBLISH packets don't have a packet ID
         // [MQTT-3.3.1-2] The DUP flag MUST be set to 0 for all QoS 0 messages.
         if (publishToClient(0, message, false, qos)) {
            consumer.individualAcknowledge(null, message.getMessageID());
         }
      } else if (qos == 1 || qos == 2) {
         Integer existingPacketId;
         final int packetIdToUse;
         boolean redelivery = false;
         synchronized (this) {
            PacketIdCorrelationKey correlationKey = PacketIdCorrelationKey.of(message.getMessageID(), message.getAddressSimpleString());
            existingPacketId = session.getStateManager().getPacketIdCorrelation(state.getClientId(), correlationKey);
            if (existingPacketId != null && !state.coreDeliveryInfoExists(existingPacketId)) {
               // re-delivery after reconnect or restart; reuse persisted packet ID
               packetIdToUse = existingPacketId;
               redelivery = true;
            } else {
               // first delivery, or same message via a different subscription
               packetIdToUse = state.generatePacketId();
               session.getStateManager().putPacketIdCorrelation(state.getClientId(), correlationKey, packetIdToUse);
            }
            state.putCoreDeliveryInfo(packetIdToUse, CoreDeliveryInfo.of(consumer.getID(), correlationKey));
            state.incrementSendQuota();
         }
         publishToClient(packetIdToUse, message, redelivery, qos);
      } else {
         // Client must have disconnected and it's Subscription QoS cleared
         consumer.individualCancel(message.getMessageID(), false);
      }
   }

   /**
    * Sends a message either on behalf of the client or on behalf of the broker (Will Messages)
    *
    * @param internal if true means on behalf of the broker (skips authorisation) and does not return ack.
    */
   void sendToQueue(MqttPublishMessage message, boolean internal) throws Exception {
      synchronized (lock) {
         if (createProducer) {
            session.getServerSession().addProducer(senderName, MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME, ServerProducer.ANONYMOUS);
            createProducer = false;
         }
         String topic = message.variableHeader().topicName();
         if (session.getVersion() == MQTTVersion.MQTT_5) {
            Integer alias = MQTTUtil.getProperty(Integer.class, message.variableHeader().properties(), TOPIC_ALIAS);
            if (alias != null) {
               Integer topicAliasMax = session.getProtocolManager().getTopicAliasMaximum();
               if (alias == 0) {
                  // [MQTT-3.3.2-8]
                  throw new DisconnectException(MQTTReasonCodes.TOPIC_ALIAS_INVALID);
               } else if (topicAliasMax != null && alias > topicAliasMax) {
                  // [MQTT-3.3.2-9]
                  throw new DisconnectException(MQTTReasonCodes.TOPIC_ALIAS_INVALID);
               }

               String existingTopicMapping = session.getState().getClientTopicAlias(alias);
               if (existingTopicMapping == null) {
                  if (topic == null || topic.isEmpty()) {
                     // using a topic alias with no matching topic in the state; potentially [MQTT-3.3.2-7]
                     throw new DisconnectException(MQTTReasonCodes.TOPIC_ALIAS_INVALID);
                  }
                  logger.debug("Adding new alias {} for topic {}", alias, topic);
                  session.getState().putClientTopicAlias(alias, topic);
               } else if (topic != null && !topic.isEmpty()) {
                  logger.debug("Modifying existing alias {}. New value: {}; old value: {}", alias, topic, existingTopicMapping);
                  session.getState().putClientTopicAlias(alias, topic);
               } else {
                  logger.debug("Applying topic {} for alias {}", existingTopicMapping, alias);
                  topic = existingTopicMapping;
               }
            }
         }
         String coreAddress = MQTTUtil.getCoreAddressFromMqttTopic(topic, session.getWildcardConfiguration());
         SimpleString address = SimpleString.of(coreAddress, session.getCoreMessageObjectPools().getAddressStringSimpleStringPool());
         Message serverMessage = MQTTUtil.createServerMessageFromByteBuf(session, address, message);
         int qos = message.fixedHeader().qosLevel().value();
         if (qos > 0) {
            serverMessage.setDurable(MQTTUtil.DURABLE_MESSAGES);
         }

         // only start a transction if really necessary
         Transaction tx = (qos == 2 && !internal) || message.fixedHeader().isRetain() ? session.getServerSession().newTransaction() : null;

         try {
            AddressInfo addressInfo = session.getServer().getAddressInfo(address);
            if (addressInfo == null && session.getServer().getAddressSettingsRepository().getMatch(coreAddress).isAutoCreateAddresses()) {
               session.getServerSession().createAddress(address, RoutingType.MULTICAST, true);
               serverMessage.setRoutingType(RoutingType.MULTICAST);
            }
            if (addressInfo != null) {
               serverMessage.setRoutingType(addressInfo.getRoutingType());
            }

            session.getServerSession().send(tx, serverMessage, true, senderName, false);

            if (qos == 2 && !internal) {
               session.getState().getPublishCache().add(message.variableHeader().packetId(), tx);
            }

            if (message.fixedHeader().isRetain()) {
               ByteBuf payload = message.payload();
               boolean reset = payload instanceof EmptyByteBuf || payload.capacity() == 0;
               session.getRetainMessageManager().handleRetainedMessage(serverMessage, topic, reset, tx);
            }
            if (tx != null) {
               tx.commit();
            }
         } catch (ActiveMQSecurityException e) {
            if (tx != null) {
               tx.rollback();
            }
            if (internal) {
               throw e;
            }
            if (session.getVersion() == MQTTVersion.MQTT_5) {
               sendMessageAck(internal, qos, message.variableHeader().packetId(), MQTTReasonCodes.NOT_AUTHORIZED);
               return;
            } else if (session.getVersion() == MQTTVersion.MQTT_3_1_1) {
               /*
                * For MQTT 3.1.1 clients:
                *
                * [MQTT-3.3.5-2] If a Server implementation does not authorize a PUBLISH to be performed by a Client;
                * it has no way of informing that Client. It MUST either make a positive acknowledgement, according
                * to the normal QoS rules, or close the Network Connection
                *
                * Throwing an exception here will ultimately close the connection. This is the default behavior.
                */
               if (closeMqttConnectionOnPublishAuthorizationFailure) {
                  throw new DisconnectException();
               } else {
                  logger.debug("MQTT 3.1.1 client not authorized to publish message.");
               }
            } else {
               /*
                * For MQTT 3.1 clients:
                *
                * Note that if a server implementation does not authorize a PUBLISH to be made by a client, it has no
                * way of informing that client. It must therefore make a positive acknowledgement, according to the
                * normal QoS rules, and the client will *not* be informed that it was not authorized to publish the
                * message.
                *
                * Log the failure since we have to just swallow it.
                */
               logger.debug("MQTT 3.1 client not authorized to publish message.");
            }
         } catch (Throwable t) {
            MQTTLogger.LOGGER.failedToPublishMqttMessage(session.getState().getClientId(), message.variableHeader().packetId(), t.getMessage(), t);
            if (tx != null) {
               tx.rollback();
            }
            throw t;
         }

         session.getProtocolHandler().runAfterStorageOperations(() -> sendMessageAck(internal, qos, message.variableHeader().packetId(), MQTTReasonCodes.SUCCESS));
      }
   }

   private void sendMessageAck(boolean internal, int qos, int messageId, byte reasonCode) {
      if (!internal) {
         if (qos == 1) {
            session.getProtocolHandler().sendPubAck(messageId, reasonCode);
         } else if (qos == 2) {
            session.getProtocolHandler().sendPubRec(messageId, reasonCode);
         }
      }
   }

   synchronized void handlePubRecError(int packetId) throws Exception {
      acknowledgeDelivery(packetId, false);
   }

   synchronized void handlePubRec(int packetId) throws Exception {
      MQTTSessionState state = session.getState();
      if (state.getPubRecCache().contains(packetId)) {
         sendAcknowledgementReply(packetId, MQTTReasonCodes.SUCCESS, true);
      } else {
         acknowledgeDelivery(packetId, true);
      }
   }

   /**
    * Once we get an acknowledgement for a QoS 1 or 2 message we allow messages to flow
    */
   private void releaseFlowControl(Long consumerId) {
      ServerConsumer consumer = session.getServerSession().locateConsumer(consumerId);
      if (consumer != null) {
         consumer.promptDelivery();
      }
   }

   synchronized void handlePubComp(int packetId) throws Exception {
      session.getState().getPubRecCache().remove(packetId);
   }

   synchronized void handlePubRel(int packetId) throws Exception {
      boolean deleted = session.getState().getPublishCache().remove(packetId);
      if (!deleted) {
         logger.debug("MQTT client {} sent PUBREL for packet {} but no corresponding PUBLISH was found in the cache", session.getState().getClientId(), packetId);
      }
      session.getProtocolHandler().sendPubComp(packetId);
   }

   synchronized void handlePubAck(int packetId) throws Exception {
      acknowledgeDelivery(packetId, false);
   }

   /**
    * Acknowledges an outbound QoS 1 or QoS 2 delivery when the client confirms receipt.
    * <p>
    * For <b>QoS 1</b> no reply is sent since the flow is complete.
    * <p>
    * For <b>QoS 2</b> the packet ID is recorded in the PUBREC cache and a PUBREL is sent to continue the handshake. It
    * is also called when the client's PUBREC carries an error reason code, in which case the delivery is simply cleaned
    * up with no reply.
    * <p>
    * The Core message acknowledgement, packet ID correlation removal, and (for QoS 2) PUBREC cache insertion are
    * performed atomically in a single transaction.
    *
    * @param packetId    the MQTT packet identifier for the in-flight delivery
    * @param needsPubRel {@code true} to record the PUBREC and reply with PUBREL (QoS 2 normal path); {@code false} to
    *                    acknowledge silently (QoS 1 or QoS 2 error path)
    */
   private void acknowledgeDelivery(int packetId, boolean needsPubRel) throws Exception {
      MQTTSessionState state = session.getState();
      Transaction tx = null;
      try {
         CoreDeliveryInfo delivery = state.getCoreDeliveryInfo(packetId);
         if (delivery != null) {
            ServerConsumer consumer = session.getServerSession().locateConsumer(delivery.getConsumerId());
            if (consumer == null) {
               if (session.getServerSession().isClosed()) {
                  logger.debug("MQTT client {} sent an acknowledgement for packet {}, but internal consumer {} was not found because the session is closed.", state.getClientId(), packetId, delivery.getConsumerId());
               } else {
                  MQTTLogger.LOGGER.failedToAckMessageConsumerNotFound(state.getClientId(), packetId, delivery.getConsumerId());
               }
               sendAcknowledgementReply(packetId, MQTTReasonCodes.PACKET_IDENTIFIER_NOT_FOUND, needsPubRel);
               return;
            }
            tx = session.getServerSession().newTransaction();
            if (needsPubRel) {
               state.getPubRecCache().add(packetId, tx);
            }
            session.getStateManager().removePacketIdCorrelation(state.getClientId(), delivery.getPacketIdCorrelationKey(), tx.getID());
            consumer.individualAcknowledge(tx, delivery.getCoreMessageId());
            tx.commit();
            state.removeCoreDeliveryInfo(packetId);
            state.decrementSendQuota();
            releaseFlowControl(delivery.getConsumerId());
            sendAcknowledgementReply(packetId, MQTTReasonCodes.SUCCESS, needsPubRel);
         } else {
            sendAcknowledgementReply(packetId, MQTTReasonCodes.PACKET_IDENTIFIER_NOT_FOUND, needsPubRel);
         }
      } catch (Exception e) {
         if (tx != null) {
            tx.rollback();
         }
         if (e instanceof ActiveMQIDGeneratorStoppedException ignored) {
            logger.debug("MQTT client {} failed to acknowledge message because the storage manager is stopping", state.getClientId(), ignored);
         } else {
            MQTTLogger.LOGGER.failedToAckMessage(state.getClientId(), e.getMessage());
         }
         sendAcknowledgementReply(packetId, MQTTReasonCodes.PACKET_IDENTIFIER_NOT_FOUND, needsPubRel);
      }
   }

   private void sendAcknowledgementReply(int packetId, byte reasonCode, boolean needsPubRel) throws Exception {
      if (needsPubRel) {
         session.getProtocolHandler().sendPubRel(packetId, reasonCode);
      }
   }

   private boolean publishToClient(int packetId, ICoreMessage coreMessage, boolean redelivery, int qos) throws Exception {
      String topic = MQTTUtil.getMqttTopicFromCoreAddress(Objects.requireNonNullElse(coreMessage.getAddress(), ""), session.getWildcardConfiguration());

      ByteBuf payload;
      switch (coreMessage.getType()) {
         case Message.TEXT_TYPE:
            SimpleString text = coreMessage.getDataBuffer().readNullableSimpleString();
            final int utf8Bytes = ByteBufUtil.utf8Bytes(text);
            payload = ByteBufAllocator.DEFAULT.directBuffer(utf8Bytes);
            // IMPORTANT: this one won't enlarge ByteBuf by ByteBufUtil.maxUtf8Bytes(text), but just utf8Bytes
            ByteBufUtil.reserveAndWriteUtf8(payload, text, utf8Bytes);
            break;
         default:
            ActiveMQBuffer bodyBuffer = coreMessage.getDataBuffer();
            payload = ByteBufAllocator.DEFAULT.directBuffer(bodyBuffer.writerIndex());
            payload.writeBytes(bodyBuffer.byteBuf());
            break;
      }

      boolean isRetain = coreMessage.containsProperty(MQTT_MESSAGE_RETAIN_INITIAL_DISTRIBUTION_KEY);
      MqttProperties mqttProperties = null;

      if (session.getVersion() == MQTTVersion.MQTT_5) {
         mqttProperties = getPublishProperties(coreMessage);
         if (!isRetain && coreMessage.getBooleanProperty(MQTT_MESSAGE_RETAIN_KEY)) {
            MqttTopicSubscription sub = session.getState().getSubscriptionItem(topic).getSubscription();
            if (sub != null && sub.option().isRetainAsPublished()) {
               isRetain = true;
            }
         }

         if (session.getState().getClientTopicAliasMaximum() != null) {
            Integer alias = session.getState().getServerTopicAlias(topic);
            if (alias == null) {
               alias = session.getState().addServerTopicAlias(topic);
               if (alias != null) {
                  mqttProperties.add(new MqttProperties.IntegerProperty(TOPIC_ALIAS.value(), alias));
               }
            } else {
               mqttProperties.add(new MqttProperties.IntegerProperty(TOPIC_ALIAS.value(), alias));
               topic = "";
            }
         }
      }

      int remainingLength = MQTTUtil.calculateRemainingLength(topic, mqttProperties, payload);
      MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PUBLISH, qos == 0 ? false : redelivery, MqttQoS.valueOf(qos), isRetain, remainingLength);
      MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, packetId, mqttProperties);
      MqttPublishMessage publish = new MqttPublishMessage(header, varHeader, payload);

      int maxSize = session.getState().getClientMaxPacketSize();
      if (session.getVersion() == MQTTVersion.MQTT_5 && maxSize != 0) {
         int size = MQTTUtil.calculateMessageSize(publish);
         if (size > maxSize) {
            /*
             * [MQTT-3.1.2-25] Where a Packet is too large to send, the Server MUST discard it without sending it and then
             * behave as if it had completed sending that Application Message
             */
            logger.debug("Not sending message {} to client as its size ({}) exceeds the max ({})", coreMessage, size, maxSize);
            if (qos == 0) {
               return true;
            } else if (qos == 1 || qos == 2) {
               acknowledgeDelivery(packetId, false);
            }
            return false;
         }
      }

      session.getProtocolHandler().sendToClient(publish);
      return true;
   }

   private MqttProperties getPublishProperties(ICoreMessage message) {
      MqttProperties props = new MqttProperties();
      if (message.containsProperty(MQTT_PAYLOAD_FORMAT_INDICATOR_KEY)) {
         props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), message.getIntProperty(MQTT_PAYLOAD_FORMAT_INDICATOR_KEY)));
      }

      if (message.containsProperty(MQTT_RESPONSE_TOPIC_KEY)) {
         props.add(new MqttProperties.StringProperty(RESPONSE_TOPIC.value(), message.getStringProperty(MQTT_RESPONSE_TOPIC_KEY)));
      }

      if (message.containsProperty(MQTT_CORRELATION_DATA_KEY)) {
         props.add(new MqttProperties.BinaryProperty(CORRELATION_DATA.value(), message.getBytesProperty(MQTT_CORRELATION_DATA_KEY)));
      }

      if (message.containsProperty(MQTT_USER_PROPERTY_EXISTS_KEY)) {
         MqttProperties.StringPair[] orderedProperties = new MqttProperties.StringPair[message.getIntProperty(MQTT_USER_PROPERTY_EXISTS_KEY)];
         for (SimpleString propertyName : message.getPropertyNames()) {
            if (propertyName.startsWith(MQTT_USER_PROPERTY_KEY_PREFIX_SIMPLE)) {
               SimpleString[] split = propertyName.split('.');
               int position = Integer.parseInt(split[4].toString());
               String key = propertyName.subSeq(MQTT_USER_PROPERTY_KEY_PREFIX_SIMPLE.length() + split[4].length() + 1, propertyName.length()).toString();
               orderedProperties[position] = new MqttProperties.StringPair(key, message.getStringProperty(propertyName));
            }
         }
         props.add(new MqttProperties.UserProperties(Arrays.asList(orderedProperties)));
      }

      if (message.containsProperty(MQTT_CONTENT_TYPE_KEY)) {
         props.add(new MqttProperties.StringProperty(CONTENT_TYPE.value(), message.getStringProperty(MQTT_CONTENT_TYPE_KEY)));
      }

      List<Integer> subscriptionIdentifiers = session.getState().getMatchingSubscriptionIdentifiers(message.getAddress());
      if (subscriptionIdentifiers != null) {
         for (Integer id : subscriptionIdentifiers) {
            props.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), id));
         }
      }

      if (message.getExpiration() != 0) {
         /*
          * [MQTT-3.3.2-6] The PUBLISH packet sent to a Client by the Server MUST contain a Message Expiry Interval set
          * to the received value minus the time that the Application Message has been waiting in the Server.
          *
          * Therefore, calculate how much time is left until the message expires rounded to the nearest *second*.
          */
         int messageExpiryInterval = (int) Math.round(((message.getExpiration() - System.currentTimeMillis()) / 1_000_000.0000) * 1000);
         props.add(new MqttProperties.IntegerProperty(PUBLICATION_EXPIRY_INTERVAL.value(), messageExpiryInterval));
      }
      return props;
   }

   private int decideQoS(Message message, ServerConsumer consumer) {
      int subscriptionQoS = -1;
      try {
         subscriptionQoS = session.getSubscriptionManager().getConsumerQoSLevels().get(consumer.getID());
      } catch (NullPointerException e) {
         // This can happen if the client disconnected during a server send.
         return subscriptionQoS;
      }

      int qos = 2;
      if (message.containsProperty(MQTTUtil.MQTT_QOS_LEVEL_KEY)) {
         qos = message.getIntProperty(MQTTUtil.MQTT_QOS_LEVEL_KEY);
      }

      /*
       * Subscription QoS is the maximum QoS the client is willing to receive for this subscription.  If the message QoS
       * is less than the subscription QoS then use it, otherwise use the subscription qos).
       */
      return subscriptionQoS < qos ? subscriptionQoS : qos;
   }
}
