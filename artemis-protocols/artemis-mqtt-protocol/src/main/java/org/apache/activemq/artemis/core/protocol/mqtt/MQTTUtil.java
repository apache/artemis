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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.commons.text.CaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.CONTENT_TYPE;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.CORRELATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.RESPONSE_TOPIC;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.USER_PROPERTY;

/**
 * A Utility Class for creating Server Side objects and converting MQTT concepts to/from Artemis.
 */
public class MQTTUtil {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final boolean DURABLE_MESSAGES = true;

   public static final boolean SESSION_AUTO_COMMIT_SENDS = false;

   public static final boolean SESSION_AUTO_COMMIT_ACKS = false;

   public static final boolean SESSION_PREACKNOWLEDGE = false;

   public static final boolean SESSION_XA = false;

   public static final boolean SESSION_AUTO_CREATE_QUEUE = false;

   public static final char DOLLAR = '$';

   public static final char HASH = '#';

   public static final char PLUS = '+';

   public static final char SLASH = '/';

   public static final String MQTT_SESSION_STORE = DOLLAR + "sys.mqtt.sessions";

   public static final String MQTT_RETAIN_ADDRESS_PREFIX = DOLLAR + "sys.mqtt.retain.";

   public static final SimpleString MQTT_QOS_LEVEL_KEY = SimpleString.of("mqtt.qos.level");

   public static final SimpleString MQTT_MESSAGE_RETAIN_KEY = SimpleString.of("mqtt.message.retain");

   public static final SimpleString MQTT_MESSAGE_RETAIN_INITIAL_DISTRIBUTION_KEY = SimpleString.of("mqtt.message.retain.initial.distribution");

   public static final SimpleString MQTT_PAYLOAD_FORMAT_INDICATOR_KEY = SimpleString.of("mqtt.payload.format.indicator");

   public static final SimpleString MQTT_RESPONSE_TOPIC_KEY = SimpleString.of("mqtt.response.topic");

   public static final SimpleString MQTT_CORRELATION_DATA_KEY = SimpleString.of("mqtt.correlation.data");

   public static final String MQTT_USER_PROPERTY_EXISTS_KEY = "mqtt.user.property.exists";

   public static final String MQTT_USER_PROPERTY_KEY_PREFIX = "mqtt.ordered.user.property.";

   public static final SimpleString MQTT_USER_PROPERTY_KEY_PREFIX_SIMPLE = SimpleString.of(MQTT_USER_PROPERTY_KEY_PREFIX);

   public static final SimpleString MQTT_CONTENT_TYPE_KEY = SimpleString.of("mqtt.content.type");

   public static final String SHARED_SUBSCRIPTION_PREFIX = DOLLAR + "share/";

   public static final long FOUR_BYTE_INT_MAX = Long.decode("0xFFFFFFFF"); // 4_294_967_295

   public static final int TWO_BYTE_INT_MAX = Integer.decode("0xFFFF"); // 65_535

   // https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901011
   public static final int VARIABLE_BYTE_INT_MAX = 268_435_455;

   public static final int MAX_PACKET_SIZE = VARIABLE_BYTE_INT_MAX;

   public static final long KEEP_ALIVE_ADJUSTMENT = 1500L;

   public static final int DEFAULT_SERVER_KEEP_ALIVE = 60;

   public static final int DEFAULT_TOPIC_ALIAS_MAX = TWO_BYTE_INT_MAX;

   public static final int DEFAULT_RECEIVE_MAXIMUM = TWO_BYTE_INT_MAX;

   public static final int DEFAULT_MAXIMUM_PACKET_SIZE = MAX_PACKET_SIZE;

   public static final int DEFAULT_MAXIMUM_IN_FLIGHT_PUBLISH_MESSAGES = TWO_BYTE_INT_MAX;

   public static final WildcardConfiguration MQTT_WILDCARD = new WildcardConfiguration().setDelimiter(SLASH).setAnyWords(HASH).setSingleWord(PLUS);

   /**
    * This method takes the MQTT-related input and translates it into the proper name for a core subscription queue. The
    * {@code topicFilter} may be either for a shared subscription in the format {@code $share/<shareName>/<topicFilter>}
    * or a normal MQTT topic filter (e.g. {@code a/b/#}, {@code a/+/c}, {@code a/b/c}, etc.).
    *
    * @param topicFilter           the MQTT topic filter
    * @param clientId              the MQTT client ID, used for normal (i.e. non-shared) subscriptions
    * @param wildcardConfiguration the {@code WildcardConfiguration} governing the core broker
    * @return the name of the core subscription queue based on the input
    */
   public static String getCoreQueueFromMqttTopic(String topicFilter, String clientId, WildcardConfiguration wildcardConfiguration) {
      Objects.requireNonNull(topicFilter, "MQTT topic filter must not be null");
      Objects.requireNonNull(wildcardConfiguration, "Broker wildcard configuration must not be null");

      if (isSharedSubscription(topicFilter)) {
         Pair<String, String> decomposed = decomposeSharedSubscriptionTopicFilter(topicFilter);
         return new StringBuilder().append(decomposed.getA()).append(".").append(getCoreAddressFromMqttTopic(decomposed.getB(), wildcardConfiguration)).toString();
      } else {
         Objects.requireNonNull(clientId, "MQTT client ID must not be null");
         return new StringBuilder().append(clientId).append(".").append(getCoreAddressFromMqttTopic(topicFilter, wildcardConfiguration)).toString();
      }
   }

   /**
    * This method takes the MQTT-related input and translates it into the proper name for a core address. The
    * {@code topicFilter} must be normal (i.e. non-shared). It should not be in the format
    * {@code $share/<shareName>/<topicFilter>}.
    *
    * @param topicFilter           the MQTT topic filter
    * @param wildcardConfiguration the {@code WildcardConfiguration} governing the core broker
    * @return the name of the core addres based on the input
    */
   public static String getCoreAddressFromMqttTopic(String topicFilter, WildcardConfiguration wildcardConfiguration) {
      Objects.requireNonNull(topicFilter, "MQTT topic filter must not be null");
      Objects.requireNonNull(wildcardConfiguration, "Broker wildcard configuration must not be null");

      return MQTT_WILDCARD.convert(topicFilter, wildcardConfiguration);
   }

   /**
    * This is exactly the same as {@link #getCoreAddressFromMqttTopic(String, WildcardConfiguration)} except that it
    * also prefixes the return with
    * {@link org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil#MQTT_RETAIN_ADDRESS_PREFIX}
    *
    * @param topicFilter           the MQTT topic filter
    * @param wildcardConfiguration the {@code WildcardConfiguration} governing the core broker
    * @return the name of the core address based on the input, stripping
    * {@link org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil#MQTT_RETAIN_ADDRESS_PREFIX} if it exists
    */
   public static String getCoreRetainAddressFromMqttTopic(String topicFilter, WildcardConfiguration wildcardConfiguration) {
      return MQTT_RETAIN_ADDRESS_PREFIX + getCoreAddressFromMqttTopic(topicFilter, wildcardConfiguration);
   }

   /**
    * Convert a Core address name into a proper MQTT topic name
    *
    * @param address               the core address; can't be {@code null}
    * @param wildcardConfiguration the {@code WildcardConfiguration} governing the core broker; can't be {@code null}
    * @return the name of the MQTT topic based on the input
    */
   public static String getMqttTopicFromCoreAddress(String address, WildcardConfiguration wildcardConfiguration) {
      Objects.requireNonNull(address, "Address must not be null");
      Objects.requireNonNull(wildcardConfiguration, "Broker wildcard configuration must not be null");

      if (address.startsWith(MQTT_RETAIN_ADDRESS_PREFIX)) {
         address = address.substring(MQTT_RETAIN_ADDRESS_PREFIX.length());
      }

      return wildcardConfiguration.convert(address, MQTT_WILDCARD);
   }

   public static ICoreMessage createServerMessage(MQTTSession session, SimpleString address, MqttPublishMessage mqttPublishMessage) {
      long id = session.getServer().getStorageManager().generateID();

      CoreMessage message = new CoreMessage(id, mqttPublishMessage.fixedHeader().remainingLength(), session.getCoreMessageObjectPools());
      message.setAddress(address);
      message.putBooleanProperty(MQTT_MESSAGE_RETAIN_KEY, mqttPublishMessage.fixedHeader().isRetain());
      message.putIntProperty(MQTT_QOS_LEVEL_KEY, mqttPublishMessage.fixedHeader().qosLevel().value());
      message.setType(Message.BYTES_TYPE);
      message.putStringProperty(MessageUtil.CONNECTION_ID_PROPERTY_NAME, session.getState().getClientId());

      MqttProperties properties = mqttPublishMessage.variableHeader() == null ? null : mqttPublishMessage.variableHeader().properties();

      Integer payloadIndicatorFormat = getProperty(Integer.class, properties, PAYLOAD_FORMAT_INDICATOR);
      if (payloadIndicatorFormat != null) {
         message.putIntProperty(MQTT_PAYLOAD_FORMAT_INDICATOR_KEY, payloadIndicatorFormat);
      }

      String responseTopic = getProperty(String.class, properties, RESPONSE_TOPIC);
      if (responseTopic != null) {
         message.putStringProperty(MQTT_RESPONSE_TOPIC_KEY, responseTopic);
      }

      byte[] correlationData = getProperty(byte[].class, properties, CORRELATION_DATA);
      if (correlationData != null) {
         message.putBytesProperty(MQTT_CORRELATION_DATA_KEY, correlationData);
      }

      /*
       * [MQTT-3.3.2-18] The Server MUST maintain the order of User Properties when forwarding the Application Message
       *
       * Maintain the original order of the properties by using a decomposable name that indicates the original order.
       */
      List<MqttProperties.StringPair> userProperties = getProperty(List.class, properties, USER_PROPERTY);
      if (userProperties != null && !userProperties.isEmpty()) {
         message.putIntProperty(MQTT_USER_PROPERTY_EXISTS_KEY, userProperties.size());
         for (int i = 0; i < userProperties.size(); i++) {
            String key = new StringBuilder()
               .append(MQTT_USER_PROPERTY_KEY_PREFIX)
               .append(i)
               .append(".")
               .append(userProperties.get(i).key)
               .toString();
            message.putStringProperty(key, userProperties.get(i).value);
         }
      }

      String contentType = getProperty(String.class, properties, CONTENT_TYPE);
      if (contentType != null) {
         message.putStringProperty(MQTT_CONTENT_TYPE_KEY, contentType);
      }

      long time = System.currentTimeMillis();
      message.setTimestamp(time);
      Integer messageExpiryInterval = getProperty(Integer.class, properties, PUBLICATION_EXPIRY_INTERVAL);
      if (messageExpiryInterval != null) {
         message.setExpiration(time + (messageExpiryInterval * 1000));
      }

      return message;
   }

   public static Message createServerMessageFromByteBuf(MQTTSession session,
                                                              SimpleString address,
                                                              MqttPublishMessage mqttPublishMessage) {
      ICoreMessage message = createServerMessage(session, address, mqttPublishMessage);

      ByteBuf payload = mqttPublishMessage.payload();
      message.getBodyBuffer().writeBytes(payload, 0, payload.readableBytes());
      return message;
   }

   public static void logMessage(MQTTSessionState state, MqttMessage message, boolean inbound, MQTTVersion version) {
      if (logger.isTraceEnabled()) {
         StringBuilder log = new StringBuilder("MQTT(");

         if (state != null) {
            log.append(state.getClientId());
         }

         if (inbound) {
            log.append("): IN << ");
         } else {
            log.append("): OUT >> ");
         }

         String messageForLogging = getMessageForLogging(message, version);

         if (messageForLogging != null) {
            logger.trace(log.append(messageForLogging).toString());
         }
      }
   }

   public static String getMessageForLogging(MqttMessage message, MQTTVersion version) {
      String result = null;

      if (message.fixedHeader() != null) {
         StringBuilder log = new StringBuilder();
         log.append(message.fixedHeader().messageType().toString());

         if (message.variableHeader() instanceof MqttMessageIdVariableHeader) {
            log.append("(" + ((MqttMessageIdVariableHeader) message.variableHeader()).messageId() + ")");
         }

         switch (message.fixedHeader().messageType()) {
            case PUBLISH:
               MqttPublishVariableHeader publishHeader = (MqttPublishVariableHeader) message.variableHeader();
               String topicName = publishHeader.topicName();
               if (topicName == null || topicName.isEmpty()) {
                  topicName = "<empty>";
               }
               log.append("(" + publishHeader.packetId() + ")")
                  .append(" topic=" + topicName)
                  .append(", qos=" + message.fixedHeader().qosLevel().value())
                  .append(", retain=" + message.fixedHeader().isRetain())
                  .append(", dup=" + message.fixedHeader().isDup())
                  .append(", remainingLength=" + message.fixedHeader().remainingLength());
               for (MqttProperties.MqttProperty property : ((MqttPublishMessage) message).variableHeader().properties().listAll()) {
                  Object value = property.value();
                  if (value != null) {
                     if (value instanceof byte[] bytes) {
                        value = new String(bytes, StandardCharsets.UTF_8);
                     } else if (value instanceof ArrayList<?> list && !list.isEmpty() && list.get(0) instanceof MqttProperties.StringPair) {
                        StringBuilder userProperties = new StringBuilder();
                        userProperties.append("[");
                        for (MqttProperties.StringPair pair : (ArrayList<MqttProperties.StringPair>) value) {
                           userProperties.append(pair.key).append(": ").append(pair.value).append(", ");
                        }
                        userProperties.delete(userProperties.length() - 2, userProperties.length());
                        userProperties.append("]");
                        value = userProperties.toString();
                     }
                  }
                  log.append(", " + formatCase(MqttPropertyType.valueOf(property.propertyId()).name()) + "=" + value);
               }
               log.append(", payload=" + getPayloadForLogging((MqttPublishMessage) message, 256));
               break;
            case CONNECT:
               // intentionally omit the username & password from the log
               MqttConnectVariableHeader connectHeader = (MqttConnectVariableHeader) message.variableHeader();
               MqttConnectPayload payload = ((MqttConnectMessage) message).payload();
               log.append(" protocol=(").append(connectHeader.name()).append(", ").append(connectHeader.version()).append(")")
                  .append(", hasPassword=").append(connectHeader.hasPassword())
                  .append(", isCleanStart=").append(connectHeader.isCleanSession())
                  .append(", keepAliveTimeSeconds=").append(connectHeader.keepAliveTimeSeconds())
                  .append(", clientIdentifier=").append(payload.clientIdentifier())
                  .append(", hasUserName=").append(connectHeader.hasUserName())
                  .append(", isWillFlag=").append(connectHeader.isWillFlag());
               if (connectHeader.isWillFlag()) {
                  log.append(", willQos=").append(connectHeader.willQos())
                     .append(", isWillRetain=").append(connectHeader.isWillRetain())
                     .append(", willTopic=").append(payload.willTopic());
               }
               for (MqttProperties.MqttProperty property : connectHeader.properties().listAll()) {
                  log.append(", " + formatCase(MqttPropertyType.valueOf(property.propertyId()).name()) + "=" + property.value());
               }
               break;
            case CONNACK:
               MqttConnAckVariableHeader connackHeader = (MqttConnAckVariableHeader) message.variableHeader();
               log.append(" connectReasonCode=").append(formatByte(connackHeader.connectReturnCode().byteValue()))
                  .append(", sessionPresent=").append(connackHeader.isSessionPresent());
               for (MqttProperties.MqttProperty property : connackHeader.properties().listAll()) {
                  log.append(", " + formatCase(MqttPropertyType.valueOf(property.propertyId()).name()) + "=" + property.value());
               }
               break;
            case SUBSCRIBE:
               for (MqttTopicSubscription sub : ((MqttSubscribeMessage) message).payload().topicSubscriptions()) {
                  log.append("\n\ttopic: ").append(sub.topicName())
                     .append(", qos: ").append(sub.qualityOfService())
                     .append(", nolocal: ").append(sub.option().isNoLocal())
                     .append(", retainHandling: ").append(sub.option().retainHandling())
                     .append(", isRetainAsPublished: ").append(sub.option().isRetainAsPublished());
               }
               break;
            case SUBACK:
               for (Integer qos : ((MqttSubAckMessage) message).payload().grantedQoSLevels()) {
                  log.append("\n\t" + qos);
               }
               break;
            case UNSUBSCRIBE:
               for (String topic : ((MqttUnsubscribeMessage) message).payload().topics()) {
                  log.append("\n\t" + topic);
               }
               break;
            case PUBACK:
               break;
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
               if (version == MQTTVersion.MQTT_5) {
                  MqttPubReplyMessageVariableHeader pubReplyVariableHeader = (MqttPubReplyMessageVariableHeader) message.variableHeader();
                  log.append(" reasonCode=").append(formatByte(pubReplyVariableHeader.reasonCode()));
               }
               break;
            case DISCONNECT:
               if (version == MQTTVersion.MQTT_5) {
                  MqttReasonCodeAndPropertiesVariableHeader disconnectVariableHeader = (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
                  log.append(" reasonCode=").append(formatByte(disconnectVariableHeader.reasonCode()));
               }
               break;
         }
         result = log.toString();
      }

      return result;
   }

   private static String formatByte(byte bite) {
      return String.format("0x%02X", bite);
   }

   private static String formatCase(String string) {
      return CaseUtils.toCamelCase(string, false, '_');
   }

   private static String getPayloadForLogging(MqttPublishMessage message, int maxPayloadLogSize) {
      if (message.payload() == null) {
         return "<empty>";
      }
      String publishPayload = message.payload().toString(StandardCharsets.UTF_8);
      if (publishPayload.isEmpty()) {
         return "<empty>";
      }
      return publishPayload.length() > maxPayloadLogSize ? publishPayload.substring(0, maxPayloadLogSize) : publishPayload;
   }

   /*
    * https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Remaining_Length:
    * "The Remaining Length is a Variable Byte Integer that represents the number of bytes remaining within the current
    * Control Packet, including data in the Variable Header and the Payload. The Remaining Length does not include the
    * bytes used to encode the Remaining Length."
    *
    * https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901106
    * "The Variable Header of the PUBLISH Packet contains the following fields in the order:  Topic Name, Packet
    * Identifier, and Properties."
    */
   public static int calculateRemainingLength(String topicName, MqttProperties properties, ByteBuf payload) {
      int size = 0;

      /*
       * https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc358219870
       * "The Variable Header component of many of the MQTT Control Packet types includes a Two Byte Integer Packet
       * Identifier field."
       */
      final int PACKET_ID_SIZE = 2;

      size += PACKET_ID_SIZE;
      size += ByteBufUtil.utf8Bytes(topicName);
      size += calculatePublishPropertiesSize(properties);
      size += payload.resetReaderIndex().readableBytes();

      return size;
   }

   /**
    * https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901027
    */
   private static int calculatePublishPropertiesSize(MqttProperties properties) {
      if (properties == null) {
         return 0;
      }
      int size = 0;
      for (MqttProperties.MqttProperty property : properties.listAll()) {
         MqttPropertyType propertyType = MqttPropertyType.valueOf(property.propertyId());
         size += switch (propertyType) {
            case PAYLOAD_FORMAT_INDICATOR -> {
               yield calculateVariableByteIntegerSize(property.propertyId()) + 1;
            }
            case TOPIC_ALIAS -> {
               yield calculateVariableByteIntegerSize(property.propertyId()) + 2;
            }
            case PUBLICATION_EXPIRY_INTERVAL -> { // AKA "Message Expiry Interval"
               yield calculateVariableByteIntegerSize(property.propertyId()) + 4;
            }
            case SUBSCRIPTION_IDENTIFIER -> {
               yield calculateVariableByteIntegerSize(property.propertyId()) +
                  calculateVariableByteIntegerSize(((MqttProperties.IntegerProperty) property).value());
            }
            case CONTENT_TYPE, RESPONSE_TOPIC -> {
               yield calculateVariableByteIntegerSize(property.propertyId()) +
                  ByteBufUtil.utf8Bytes(((MqttProperties.StringProperty) property).value());
            }
            case USER_PROPERTY -> {
               int userPropertySize = 0;
               for (MqttProperties.StringPair pair : ((MqttProperties.UserProperties) property).value()) {
                  userPropertySize += calculateVariableByteIntegerSize(property.propertyId());
                  userPropertySize += ByteBufUtil.utf8Bytes(pair.key);
                  userPropertySize += ByteBufUtil.utf8Bytes(pair.value);
               }
               yield userPropertySize;
            }
            case CORRELATION_DATA -> {
               yield calculateVariableByteIntegerSize(property.propertyId()) + 2 +
                  ((MqttProperties.BinaryProperty) property).value().length;
            }
            default -> throw new EncoderException("Unknown property type: " + propertyType);
         };

      }
      size += calculateVariableByteIntegerSize(size);

      return size;
   }

   /**
    * https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Remaining_Length:
    * "The packet size is the total number of bytes in an MQTT Control Packet, this is equal to the length of the Fixed
    * Header plus the Remaining Length."
    *
    * The length of the Fixed Header for a PUBLISH packet is 1 byte + the size of the "Remaining Length" Variable Byte
    * Integer.
    */
   public static int calculateMessageSize(MqttPublishMessage message) {
      return 1 + calculateVariableByteIntegerSize(message.fixedHeader().remainingLength()) + message.fixedHeader().remainingLength();
   }

   // https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901011
   private static int calculateVariableByteIntegerSize(int vbi) {
      int count = 0;
      do {
         vbi /= 128;
         count++;
      }
      while (vbi > 0);
      return count;
   }

   public static <T> T getProperty(Class<T> type, MqttProperties properties, MqttPropertyType propertyName) {
      return getProperty(type, properties, propertyName, null);
   }

   public static <T> T getProperty(Class<T> type, MqttProperties properties, MqttPropertyType propertyName, T defaultReturnValue) {
      if (properties != null) {
         MqttProperties.MqttProperty o = properties.getProperty(propertyName.value());
         if (o != null) {
            try {
               return type.cast(o.value());
            } catch (ClassCastException e) {
               MQTTLogger.LOGGER.failedToCastProperty(propertyName.toString());
               throw e;
            }
         }
      }

      return defaultReturnValue == null ? null : defaultReturnValue;
   }

   /**
    * MQTT shared subscriptions are specified with
    * <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901250">this syntax</a>.
    *
    * @param topicFilter String in the format {@code $share/<shareName>/<topicFilter>}
    * @return {@code Pair<String, String>} with {@code shareName} and {@code topicFilter} respectively or {@code null}
    * and {@code topicFilter} if not in the shared-subscription format.
    */
   public static Pair<String, String> decomposeSharedSubscriptionTopicFilter(String topicFilter) {
      if (isSharedSubscription(topicFilter)) {
         int prefix = SHARED_SUBSCRIPTION_PREFIX.length();
         String shareName = topicFilter.substring(prefix, topicFilter.indexOf(SLASH, prefix));
         String parsedTopicName = topicFilter.substring(topicFilter.indexOf(SLASH, prefix) + 1);
         return new Pair<>(shareName, parsedTopicName);
      } else {
         return new Pair<>(null, topicFilter);
      }
   }

   /**
    * Test whether an MQTT topic filter is for a shared subscription.
    *
    * @param topicFilter the MQTT topic filter
    * @return {@code true} if the input starts with {@code $share/}, {@code false} otherwise
    */
   public static boolean isSharedSubscription(String topicFilter) {
      if (topicFilter.startsWith(SHARED_SUBSCRIPTION_PREFIX)) {
         return true;
      } else {
         return false;
      }
   }

   public static void sendMessageDirectlyToQueue(StorageManager storageManager, PostOffice postOffice, Message message, Queue queue, final Transaction incomingTx) throws Exception {
      Transaction tx = incomingTx;
      if (incomingTx == null) {
         tx = new TransactionImpl(storageManager);
         tx.setAsync(true);
      }
      RoutingContext context = new RoutingContextImpl(tx);
      queue.route(message, context);
      postOffice.processRoute(message, context, false);
      if (incomingTx == null) {
         // commit the transaction we created otherwise leave it for the caller to commit
         tx.commit();
      }
   }
}
