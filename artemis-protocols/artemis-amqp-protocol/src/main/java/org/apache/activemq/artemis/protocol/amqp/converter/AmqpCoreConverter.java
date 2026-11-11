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
package org.apache.activemq.artemis.protocol.amqp.converter;

import static org.apache.activemq.artemis.api.core.Message.HDR_INGRESS_TIMESTAMP;
import static org.apache.activemq.artemis.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_DATA;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_NULL;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_SEQUENCE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_BINARY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_LIST;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_MAP;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_NULL;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_STRING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_CONTENT_ENCODING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_CONTENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_FOOTER_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_FIRST_ACQUIRER;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_FOOTER_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_HEADER;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_HEADER_DURABLE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_HEADER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_MESSAGE_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ORIGINAL_ENCODING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_REPLYTO_GROUP_ID;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createBytesMessage;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createMapMessage;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createMessage;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createObjectMessage;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createStreamMessage;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createTextMessage;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.getCharsetForTextualContent;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.isContentType;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.ConversionException;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreStreamMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Decimal128;
import org.apache.qpid.proton.amqp.Decimal32;
import org.apache.qpid.proton.amqp.Decimal64;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.UnsignedShort;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.WritableBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.NON_PERSISTENT;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.PERSISTENT;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.MESSAGE_DEFAULT_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.MESSAGE_DEFAULT_TIME_TO_LIVE;
import static org.apache.activemq.artemis.utils.DestinationUtil.QUEUE_QUALIFIED_PREFIX;
import static org.apache.activemq.artemis.utils.DestinationUtil.TEMP_QUEUE_QUALIFED_PREFIX;
import static org.apache.activemq.artemis.utils.DestinationUtil.TEMP_TOPIC_QUALIFED_PREFIX;
import static org.apache.activemq.artemis.utils.DestinationUtil.TOPIC_QUALIFIED_PREFIX;

/**
 * This class was created just to separate concerns on AMQPConverter. For better organization of the code.
 */
public class AmqpCoreConverter {
   public static ICoreMessage toCore(AMQPMessage message, CoreMessageObjectPools coreMessageObjectPools) throws Exception {
      return message.toCore(coreMessageObjectPools);
   }

   @SuppressWarnings("unchecked")
   public static ICoreMessage toCore(AMQPMessage message, CoreMessageObjectPools coreMessageObjectPools, Header header, MessageAnnotations annotations, Properties properties, ApplicationProperties applicationProperties, Section body, Footer footer) throws ConversionException {
      final long messageId = message.getMessageID();
      final Symbol contentType = properties != null ? properties.getContentType() : null;
      final String contentTypeString = Objects.toString(contentType, null);

      CoreMessageWrapper result;

      if (body == null) {
         if (isContentType(SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString(), contentType)) {
            result = createObjectMessage(messageId, coreMessageObjectPools);
         } else if (isContentType(OCTET_STREAM_CONTENT_TYPE, contentType) || isContentType(null, contentType)) {
            result = createBytesMessage(messageId, coreMessageObjectPools);
         } else {
            Charset charset = getCharsetForTextualContent(contentTypeString);
            if (charset != null) {
               result = createTextMessage(messageId, coreMessageObjectPools);
            } else {
               result = createMessage(messageId, coreMessageObjectPools);
            }
         }

         result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_NULL);
      } else if (body instanceof Data data) {
         Binary payload = data.getValue();

         if (isContentType(SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString(), contentType)) {
            result = createObjectMessage(messageId, payload.getArray(), payload.getArrayOffset(), payload.getLength(), coreMessageObjectPools);
         } else if (isContentType(OCTET_STREAM_CONTENT_TYPE, contentType)) {
            result = createBytesMessage(messageId, payload.getArray(), payload.getArrayOffset(), payload.getLength(), coreMessageObjectPools);
         } else {
            Charset charset = getCharsetForTextualContent(contentTypeString);
            if (StandardCharsets.UTF_8.equals(charset)) {
               ByteBuffer buf = ByteBuffer.wrap(payload.getArray(), payload.getArrayOffset(), payload.getLength());

               try {
                  CharBuffer chars = charset.newDecoder().decode(buf);
                  result = createTextMessage(messageId, String.valueOf(chars), coreMessageObjectPools);
               } catch (CharacterCodingException e) {
                  result = createBytesMessage(messageId, payload.getArray(), payload.getArrayOffset(), payload.getLength(), coreMessageObjectPools);
               }
            } else {
               result = createBytesMessage(messageId, payload.getArray(), payload.getArrayOffset(), payload.getLength(), coreMessageObjectPools);
            }
         }

         result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_DATA);
      } else if (body instanceof AmqpSequence amqpSequence) {
         CoreStreamMessageWrapper m = createStreamMessage(messageId, coreMessageObjectPools);
         for (Object item : amqpSequence.getValue()) {
            m.writeObject(item);
         }

         result = m;
         result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_SEQUENCE);
      } else if (body instanceof AmqpValue amqpValue) {
         Object value = amqpValue.getValue();
         if (value == null || value instanceof String) {
            result = createTextMessage(messageId, (String) value, coreMessageObjectPools);

            result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, value == null ? AMQP_VALUE_NULL : AMQP_VALUE_STRING);
         } else if (value instanceof Binary binary) {

            if (isContentType(SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString(), contentType)) {
               result = createObjectMessage(messageId, binary, coreMessageObjectPools);
            } else {
               result = createBytesMessage(messageId, binary.getArray(), binary.getArrayOffset(), binary.getLength(), coreMessageObjectPools);
            }

            result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_BINARY);
         } else if (value instanceof List) {
            CoreStreamMessageWrapper m = createStreamMessage(messageId, coreMessageObjectPools);
            for (Object item : (List<Object>) value) {
               m.writeObject(item);
            }
            result = m;
            result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_LIST);
         } else if (value instanceof Map) {
            result = createMapMessage(messageId, (Map<String, Object>) value, coreMessageObjectPools);
            result.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_MAP);
         } else {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);
            try {
               TLSEncode.getEncoder().setByteBuffer(new NettyWritable(buf));
               TLSEncode.getEncoder().writeObject(body);
               result = createBytesMessage(messageId, buf.array(), buf.arrayOffset(), buf.writerIndex(), coreMessageObjectPools);
            } finally {
               buf.release();
               TLSEncode.getEncoder().setByteBuffer((WritableBuffer)null);
            }
         }
      } else {
         throw new RuntimeException("Unexpected body type: " + body.getClass());
      }

      // Initialize the JMS expiration with the value calculated during the scan of the AMQP message
      // to avoid a different value for each conversion based on System.currentTimeMillis() and ttl.
      result.setJMSExpiration(message.getExpiration());

      processHeader(result, header);
      processMessageAnnotations(result, annotations);
      processApplicationProperties(result, applicationProperties);
      processProperties(result, properties, annotations);
      processFooter(result, footer);
      processExtraProperties(result, message.getExtraProperties());

      // If the JMS expiration has not yet been set...
      if (header != null && result.getExpiration() == 0) {
         // Then lets try to set it based on the message TTL.
         long ttl = MESSAGE_DEFAULT_TIME_TO_LIVE;
         if (header.getTtl() != null) {
            ttl = header.getTtl().longValue();
         }

         if (ttl == 0) {
            result.setJMSExpiration(0);
         } else {
            result.setJMSExpiration(System.currentTimeMillis() + ttl);
         }
      }

      result.getInnerMessage().setDurable(message.isDurable());
      result.getInnerMessage().setPriority(message.getPriority());
      result.getInnerMessage().setAddress(message.getAddressSimpleString());
      result.getInnerMessage().setRoutingType(message.getRoutingType());
      result.encode();

      return result.getInnerMessage();
   }

   protected static CoreMessageWrapper processHeader(CoreMessageWrapper jms, Header header) throws ConversionException {
      if (header != null) {
         jms.setBooleanProperty(JMS_AMQP_HEADER, true);

         if (header.getDurable() != null) {
            jms.setBooleanProperty(JMS_AMQP_HEADER_DURABLE, true);
            jms.setDeliveryMode(header.getDurable().booleanValue() ? PERSISTENT : NON_PERSISTENT);
         } else {
            jms.setDeliveryMode(NON_PERSISTENT);
         }

         if (header.getPriority() != null) {
            jms.setBooleanProperty(JMS_AMQP_HEADER_PRIORITY, true);
            jms.setJMSPriority(header.getPriority().intValue());
         } else {
            jms.setJMSPriority(MESSAGE_DEFAULT_PRIORITY);
         }

         if (header.getFirstAcquirer() != null) {
            jms.setBooleanProperty(JMS_AMQP_FIRST_ACQUIRER, header.getFirstAcquirer());
         }

         if (header.getDeliveryCount() != null) {
            // AMQP Delivery Count counts only failed delivers where JMS
            // Delivery Count should include the original delivery in the count.
            jms.setLongProperty("JMSXDeliveryCount", header.getDeliveryCount().longValue() + 1);
         }
      } else {
         jms.setJMSPriority((byte) MESSAGE_DEFAULT_PRIORITY);
         jms.setDeliveryMode(NON_PERSISTENT);
      }

      return jms;
   }

   protected static CoreMessageWrapper processMessageAnnotations(CoreMessageWrapper jms, MessageAnnotations annotations) {
      if (annotations != null && annotations.getValue() != null) {
         for (Map.Entry<?, ?> entry : annotations.getValue().entrySet()) {
            String key = entry.getKey().toString();
            if ("x-opt-delivery-time".equals(key) && entry.getValue() != null) {
               long deliveryTime = ((Number) entry.getValue()).longValue();
               jms.setLongProperty(HDR_SCHEDULED_DELIVERY_TIME.toString(), deliveryTime);
            } else if ("x-opt-delivery-delay".equals(key) && entry.getValue() != null) {
               long delay = ((Number) entry.getValue()).longValue();
               if (delay > 0) {
                  jms.setLongProperty(HDR_SCHEDULED_DELIVERY_TIME.toString(), System.currentTimeMillis() + delay);
               }
            } else if (AMQPMessageSupport.X_OPT_INGRESS_TIME.equals(key) && entry.getValue() != null) {
               jms.setLongProperty(HDR_INGRESS_TIMESTAMP.toString(), ((Number) entry.getValue()).longValue());
            }

            try {
               setProperty(jms, JMS_AMQP_MESSAGE_ANNOTATION_PREFIX + key, entry.getValue());
            } catch (ActiveMQPropertyConversionException e) {
               encodeUnsupportedMessagePropertyType(jms, JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX + key, entry.getValue());
            }
         }
      }

      return jms;
   }

   private static CoreMessageWrapper processApplicationProperties(CoreMessageWrapper jms, ApplicationProperties properties) {
      if (properties != null && properties.getValue() != null) {
         for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) properties.getValue().entrySet()) {
            setProperty(jms, entry.getKey(), entry.getValue());
         }
      }

      return jms;
   }

   private static CoreMessageWrapper processExtraProperties(CoreMessageWrapper jms, TypedProperties properties) {
      if (properties != null) {
         properties.forEach((k, v) -> {
            if (!k.equals(AMQPMessage.ADDRESS_PROPERTY)) {
               jms.getInnerMessage().putObjectProperty(k, v);
            }
         });
      }

      return jms;
   }

   private static CoreMessageWrapper processProperties(CoreMessageWrapper jms, Properties properties, MessageAnnotations annotations) {
      if (properties != null) {
         if (properties.getMessageId() != null) {
            jms.setJMSMessageID(AMQPMessageIdHelper.INSTANCE.toMessageIdString(properties.getMessageId()));
            //core jms clients get JMSMessageID from UserID which is a UUID object
            if (properties.getMessageId() instanceof UUID) {
               //AMQP's message ID can be a UUID, keep it
               jms.getInnerMessage().setUserID(UUIDGenerator.getInstance().fromJavaUUID((UUID) properties.getMessageId()));
            } else {
               jms.getInnerMessage().setUserID(UUIDGenerator.getInstance().generateUUID());
            }
         }

         Binary userId = properties.getUserId();
         if (userId != null) {
            jms.setStringProperty("JMSXUserID", new String(userId.getArray(), userId.getArrayOffset(), userId.getLength(), StandardCharsets.UTF_8));
         }
         if (properties.getTo() != null) {
            byte queueType = parseQueueAnnotation(annotations, AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION);
            jms.setDestination(properties.getTo());
         }
         if (properties.getSubject() != null) {
            jms.setJMSType(properties.getSubject());
         }
         if (properties.getReplyTo() != null) {
            byte value = parseQueueAnnotation(annotations, AMQPMessageSupport.JMS_REPLY_TO_TYPE_MSG_ANNOTATION);

            switch (value) {
               case AMQPMessageSupport.QUEUE_TYPE:
                  org.apache.activemq.artemis.reader.MessageUtil.setJMSReplyTo(jms.getInnerMessage(), QUEUE_QUALIFIED_PREFIX + properties.getReplyTo());
                  break;
               case AMQPMessageSupport.TEMP_QUEUE_TYPE:
                  org.apache.activemq.artemis.reader.MessageUtil.setJMSReplyTo(jms.getInnerMessage(), TEMP_QUEUE_QUALIFED_PREFIX + properties.getReplyTo());
                  break;
               case AMQPMessageSupport.TOPIC_TYPE:
                  org.apache.activemq.artemis.reader.MessageUtil.setJMSReplyTo(jms.getInnerMessage(), TOPIC_QUALIFIED_PREFIX + properties.getReplyTo());
                  break;
               case AMQPMessageSupport.TEMP_TOPIC_TYPE:
                  org.apache.activemq.artemis.reader.MessageUtil.setJMSReplyTo(jms.getInnerMessage(), TEMP_TOPIC_QUALIFED_PREFIX + properties.getReplyTo());
                  break;
               default:
                  org.apache.activemq.artemis.reader.MessageUtil.setJMSReplyTo(jms.getInnerMessage(), QUEUE_QUALIFIED_PREFIX + properties.getReplyTo());
                  break;
            }
         }
         Object correlationID = properties.getCorrelationId();

         if (correlationID != null) {
            try {
               jms.getInnerMessage().setCorrelationID(AMQPMessageIdHelper.INSTANCE.toCorrelationIdStringOrBytes(correlationID));
            } catch (IllegalArgumentException e) {
               jms.getInnerMessage().setCorrelationID(String.valueOf(correlationID));
            }
         }
         if (properties.getContentType() != null) {
            jms.setStringProperty(JMS_AMQP_CONTENT_TYPE, properties.getContentType().toString());
         }
         if (properties.getContentEncoding() != null) {
            jms.setStringProperty(JMS_AMQP_CONTENT_ENCODING, properties.getContentEncoding().toString());
         }
         if (properties.getCreationTime() != null) {
            jms.setJMSTimestamp(properties.getCreationTime().getTime());
         }
         if (properties.getGroupId() != null) {
            jms.setStringProperty("_AMQ_GROUP_ID", properties.getGroupId());
         }
         if (properties.getGroupSequence() != null) {
            jms.setIntProperty("JMSXGroupSeq", properties.getGroupSequence().intValue());
         }
         if (properties.getReplyToGroupId() != null) {
            jms.setStringProperty(JMS_AMQP_REPLYTO_GROUP_ID, properties.getReplyToGroupId());
         }
         if (properties.getAbsoluteExpiryTime() != null) {
            jms.setJMSExpiration(properties.getAbsoluteExpiryTime().getTime());
         }
      }
      return jms;
   }

   private static byte parseQueueAnnotation(MessageAnnotations annotations, Symbol symbol) {
      Object value = (annotations != null && annotations.getValue() != null ? annotations.getValue().get(symbol) : AMQPMessageSupport.QUEUE_TYPE);

      byte queueType;
      if (value == null || !(value instanceof Number)) {
         queueType = AMQPMessageSupport.QUEUE_TYPE;
      } else {
         queueType = ((Number)value).byteValue();
      }
      return queueType;
   }

   @SuppressWarnings("unchecked")
   private static CoreMessageWrapper processFooter(CoreMessageWrapper jms, Footer footer) {
      if (footer != null && footer.getValue() != null) {
         for (Map.Entry<Symbol, Object> entry : (Set<Map.Entry<Symbol, Object>>) footer.getValue().entrySet()) {
            String key = entry.getKey().toString();
            try {
               setProperty(jms, JMS_AMQP_FOOTER_PREFIX + key, entry.getValue());
            } catch (ActiveMQPropertyConversionException e) {
               encodeUnsupportedMessagePropertyType(jms, JMS_AMQP_ENCODED_FOOTER_PREFIX + key, entry.getValue());
            }
         }
      }

      return jms;
   }

   private static void encodeUnsupportedMessagePropertyType(CoreMessageWrapper jms, String key, Object value) {
      final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer();
      final EncoderImpl encoder = TLSEncode.getEncoder();

      try {
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(value);

         final byte[] encodedBytes = new byte[buffer.writerIndex()];

         buffer.readBytes(encodedBytes);

         setProperty(jms, key, encodedBytes);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   private static void setProperty(CoreMessageWrapper msg, String key, Object value) {
      if (value instanceof UnsignedLong unsignedLong) {
         long v = unsignedLong.longValue();
         msg.setLongProperty(key, v);
      } else if (value instanceof UnsignedInteger unsignedInteger) {
         long v = unsignedInteger.longValue();
         if (Integer.MIN_VALUE <= v && v <= Integer.MAX_VALUE) {
            msg.setIntProperty(key, (int) v);
         } else {
            msg.setLongProperty(key, v);
         }
      } else if (value instanceof UnsignedShort unsignedShort) {
         int v = unsignedShort.intValue();
         if (Short.MIN_VALUE <= v && v <= Short.MAX_VALUE) {
            msg.setShortProperty(key, (short) v);
         } else {
            msg.setIntProperty(key, v);
         }
      } else if (value instanceof UnsignedByte unsignedByte) {
         short v = unsignedByte.shortValue();
         if (Byte.MIN_VALUE <= v && v <= Byte.MAX_VALUE) {
            msg.setByteProperty(key, (byte) v);
         } else {
            msg.setShortProperty(key, v);
         }
      } else if (value instanceof Symbol) {
         msg.setStringProperty(key, value.toString());
      } else if (value instanceof Decimal128 decimal128) {
         msg.setDoubleProperty(key, decimal128.doubleValue());
      } else if (value instanceof Decimal64 decimal64) {
         msg.setDoubleProperty(key, decimal64.doubleValue());
      } else if (value instanceof Decimal32 decimal32) {
         msg.setFloatProperty(key, decimal32.floatValue());
      } else if (value instanceof Binary binary) {
         msg.setObjectProperty(key, Arrays.copyOfRange(binary.getArray(), binary.getArrayOffset(), binary.getLength()));
      } else {
         msg.setObjectProperty(key, value);
      }
   }
}
