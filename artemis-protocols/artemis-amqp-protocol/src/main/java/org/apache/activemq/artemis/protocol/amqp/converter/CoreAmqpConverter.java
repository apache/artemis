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

import static org.apache.activemq.artemis.api.core.FilterConstants.NATIVE_MESSAGE_ID;
import static org.apache.activemq.artemis.api.core.Message.EMBEDDED_TYPE;
import static org.apache.activemq.artemis.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_CONTENT_ENCODING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_CONTENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_DELIVERY_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_DELIVERY_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_FOOTER_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_FIRST_ACQUIRER;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_FOOTER_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_HEADER;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_HEADER_DURABLE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_HEADER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_MESSAGE_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_NATIVE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ORIGINAL_ENCODING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_PROPERTIES;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_REPLYTO_GROUP_ID;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_REPLY_TO_TYPE_MSG_ANNOTATION;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.MESSAGE_DEFAULT_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.toAddress;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.ConversionException;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.spi.core.protocol.EmbedMessageUtil;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.ReadableBuffer.ByteBufferReader;

public class CoreAmqpConverter {

   public static AMQPMessage checkAMQP(Message message, StorageManager storageManager) throws Exception {
      if (message instanceof AMQPMessage amqpMessage) {
         return amqpMessage;
      } else {
         // It will first convert to Core, then to AMQP
         return fromCore(message.toCore(), storageManager);
      }
   }

   public static AMQPMessage fromCore(ICoreMessage coreMessage, StorageManager storageManager) throws ConversionException {
      if (coreMessage == null) {
         return null;
      }

      try {
         if (coreMessage.isServerMessage() && coreMessage.isLargeMessage() && coreMessage.getType() == EMBEDDED_TYPE) {
            //large AMQP messages received across cluster nodes
            final Message message = EmbedMessageUtil.extractEmbedded(coreMessage, storageManager);
            if (message instanceof AMQPMessage amqpMessage) {
               return amqpMessage;
            }
         }
         CoreMessageWrapper message = CoreMessageWrapper.wrap(coreMessage);
         message.decode();

         Header header = null;
         final Properties properties = new Properties();
         Map<Symbol, Object> daMap = null;
         final Map<Symbol, Object> maMap = new HashMap<>();
         Map<String, Object> apMap = null;
         Map<Symbol, Object> footerMap = null;

         Section body = message.createAMQPSection(maMap, properties);

         if (message.getInnerMessage().isDurable()) {
            if (header == null) {
               header = new Header();
            }
            header.setDurable(true);
         }
         byte priority = (byte) message.getJMSPriority();
         if (priority != MESSAGE_DEFAULT_PRIORITY) {
            if (header == null) {
               header = new Header();
            }
            header.setPriority(UnsignedByte.valueOf(priority));
         }
         String type = message.getJMSType();
         if (type != null) {
            properties.setSubject(type);
         }
         String messageId = message.getJMSMessageID();
         if (messageId != null) {
            try {
               properties.setMessageId(AMQPMessageIdHelper.INSTANCE.toIdObject(messageId));
            } catch (ActiveMQAMQPIllegalStateException e) {
               properties.setMessageId(messageId);
            }
         } else {
            if (message.getInnerMessage().getUserID() != null) {
               properties.setMessageId("ID:" + message.getInnerMessage().getUserID().toString());
            }
         }
         SimpleString destination = message.getDestination();
         if (destination != null) {
            properties.setTo(toAddress(destination.toString()));
            maMap.put(JMS_DEST_TYPE_MSG_ANNOTATION, AMQPMessageSupport.destinationType(destination.toString()));
         }
         SimpleString replyTo = message.getJMSReplyTo();
         if (replyTo != null) {
            properties.setReplyTo(toAddress(replyTo.toString()));
            maMap.put(JMS_REPLY_TO_TYPE_MSG_ANNOTATION, AMQPMessageSupport.destinationType(replyTo.toString()));
         }

         long scheduledDelivery = coreMessage.getScheduledDeliveryTime();

         if (scheduledDelivery > 0) {
            maMap.put(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, scheduledDelivery);
         }

         Object correlationID = message.getInnerMessage().getCorrelationID();
         if (correlationID instanceof String || correlationID instanceof SimpleString) {
            String c = correlationID.toString();
            try {
               properties.setCorrelationId(AMQPMessageIdHelper.INSTANCE.toIdObject(c));
            } catch (ActiveMQAMQPIllegalStateException e) {
               properties.setCorrelationId(correlationID);
            }
         } else if (correlationID instanceof byte[] bytes) {
            properties.setCorrelationId(new Binary(bytes));
         } else {
            properties.setCorrelationId(correlationID);
         }

         long expiration = message.getExpiration();
         if (expiration != 0) {
            long ttl = expiration - System.currentTimeMillis();
            if (ttl < 0) {
               ttl = 1;
            }

            if (header == null) {
               header = new Header();
            }
            header.setTtl(new UnsignedInteger((int) ttl));

            properties.setAbsoluteExpiryTime(new Date(expiration));
         }
         long timeStamp = message.getJMSTimestamp();
         if (timeStamp != 0) {
            properties.setCreationTime(new Date(timeStamp));
         }

         final Set<String> keySet = MessageUtil.getPropertyNames(message.getInnerMessage());
         for (String key : keySet) {
            if (key.startsWith("JMSX")) {
               if (key.equals("JMSXUserID")) {
                  String value = message.getStringProperty(key);
                  if (value != null) {
                     properties.setUserId(Binary.create(StandardCharsets.UTF_8.encode(value)));
                  }
                  continue;
               } else if (key.equals("JMSXGroupID")) {
                  String value = message.getStringProperty(key);
                  properties.setGroupId(value);
                  continue;
               } else if (key.equals("JMSXGroupSeq")) {
                  int value = message.getIntProperty(key);
                  properties.setGroupSequence(UnsignedInteger.valueOf(value));
                  continue;
               }
            } else if (key.startsWith(JMS_AMQP_PREFIX)) {
               // AMQP Message Information stored from a conversion to the Core Message
               if (key.equals(JMS_AMQP_NATIVE)) {
                  // skip..internal use only
                  continue;
               } else if (key.equals(JMS_AMQP_FIRST_ACQUIRER)) {
                  if (header == null) {
                     header = new Header();
                  }
                  header.setFirstAcquirer(message.getBooleanProperty(key));
                  continue;
               } else if (key.equals(JMS_AMQP_HEADER)) {
                  if (header == null) {
                     header = new Header();
                  }
                  continue;
               } else if (key.equals(JMS_AMQP_HEADER_DURABLE)) {
                  if (header == null) {
                     header = new Header();
                  }
                  header.setDurable(message.getInnerMessage().isDurable());
                  continue;
               } else if (key.equals(JMS_AMQP_HEADER_PRIORITY)) {
                  if (header == null) {
                     header = new Header();
                  }
                  header.setPriority(UnsignedByte.valueOf(priority));
                  continue;
               } else if (key.startsWith(JMS_AMQP_PROPERTIES)) {
                  continue;
               } else if (key.startsWith(JMS_AMQP_DELIVERY_ANNOTATION_PREFIX)) {
                  if (daMap == null) {
                     daMap = new HashMap<>();
                  }
                  String name = key.substring(JMS_AMQP_DELIVERY_ANNOTATION_PREFIX.length());
                  daMap.put(Symbol.valueOf(name), message.getObjectProperty(key));
                  continue;
               } else if (key.startsWith(JMS_AMQP_ENCODED_DELIVERY_ANNOTATION_PREFIX)) {
                  if (daMap == null) {
                     daMap = new HashMap<>();
                  }
                  String name = key.substring(JMS_AMQP_ENCODED_DELIVERY_ANNOTATION_PREFIX.length());
                  daMap.put(Symbol.valueOf(name), decodeEmbeddedAMQPType(message.getObjectProperty(key)));
                  continue;
               } else if (key.startsWith(JMS_AMQP_MESSAGE_ANNOTATION_PREFIX)) {
                  String name = key.substring(JMS_AMQP_MESSAGE_ANNOTATION_PREFIX.length());
                  maMap.put(Symbol.valueOf(name), message.getObjectProperty(key));
                  continue;
               } else if (key.startsWith(JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX)) {
                  String name = key.substring(JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX.length());
                  maMap.put(Symbol.valueOf(name), decodeEmbeddedAMQPType(message.getObjectProperty(key)));
                  continue;
               } else if (key.equals(JMS_AMQP_CONTENT_TYPE)) {
                  properties.setContentType(Symbol.getSymbol(message.getStringProperty(key)));
                  continue;
               } else if (key.equals(JMS_AMQP_CONTENT_ENCODING)) {
                  properties.setContentEncoding(Symbol.getSymbol(message.getStringProperty(key)));
                  continue;
               } else if (key.equals(JMS_AMQP_REPLYTO_GROUP_ID)) {
                  properties.setReplyToGroupId(message.getStringProperty(key));
                  continue;
               } else if (key.equals(JMS_AMQP_ORIGINAL_ENCODING)) {
                  // skip..remove annotation from previous inbound transformation
                  continue;
               } else if (key.startsWith(JMS_AMQP_ENCODED_FOOTER_PREFIX)) {
                  if (footerMap == null) {
                     footerMap = new HashMap<>();
                  }
                  String name = key.substring(JMS_AMQP_ENCODED_FOOTER_PREFIX.length());
                  footerMap.put(Symbol.valueOf(name), decodeEmbeddedAMQPType(message.getObjectProperty(key)));
                  continue;
               } else if (key.startsWith(JMS_AMQP_FOOTER_PREFIX)) {
                  if (footerMap == null) {
                     footerMap = new HashMap<>();
                  }
                  String name = key.substring(JMS_AMQP_FOOTER_PREFIX.length());
                  footerMap.put(Symbol.valueOf(name), message.getObjectProperty(key));
                  continue;
               }
            } else if (key.equals(Message.HDR_GROUP_ID.toString())) {
               String value = message.getStringProperty(key);
               properties.setGroupId(value);
               continue;
            } else if (key.equals(Message.HDR_GROUP_SEQUENCE.toString())) {
               int value = message.getIntProperty(key);
               properties.setGroupSequence(UnsignedInteger.valueOf(value));
               continue;
            } else if (key.equals(NATIVE_MESSAGE_ID)) {
               // skip..internal use only
               continue;
            } else if (key.endsWith(HDR_SCHEDULED_DELIVERY_TIME.toString())) {
               // skip..remove annotation from previous inbound transformation
               continue;
            } else if (key.equals(Message.HDR_INGRESS_TIMESTAMP.toString())) {
               maMap.put(AMQPMessageSupport.INGRESS_TIME_MSG_ANNOTATION, message.getLongProperty(key));
               continue;
            }

            if (apMap == null) {
               apMap = new HashMap<>();
            }

            Object objectProperty = message.getObjectProperty(key);
            if (objectProperty instanceof byte[] bytes) {
               objectProperty = new Binary(bytes);
            }

            apMap.put(key, objectProperty);
         }

         long messageID = message.getInnerMessage().getMessageID();
         return AMQPStandardMessage.createMessage(messageID, 0, replyTo, header, properties, daMap, maMap, apMap, footerMap, body);
      } catch (ConversionException ce) {
         throw ce;
      } catch (Exception e) {
         throw new ConversionException(e.getMessage(), e);
      }
   }

   private static Object decodeEmbeddedAMQPType(Object payload) {
      final byte[] encodedType = (byte[]) payload;

      final DecoderImpl decoder = TLSEncode.getDecoder();
      Object decodedType = null;

      decoder.setBuffer(ByteBufferReader.wrap(encodedType));

      try {
         decodedType = decoder.readObject();
      } finally {
         decoder.setBuffer(null);
      }

      return decodedType;
   }

}
