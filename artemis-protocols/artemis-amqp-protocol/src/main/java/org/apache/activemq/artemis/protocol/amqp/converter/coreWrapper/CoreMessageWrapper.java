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
package org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper;


import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.api.core.FilterConstants.NATIVE_MESSAGE_ID;
import static org.apache.activemq.artemis.api.core.Message.BYTES_TYPE;
import static org.apache.activemq.artemis.api.core.Message.MAP_TYPE;
import static org.apache.activemq.artemis.api.core.Message.OBJECT_TYPE;
import static org.apache.activemq.artemis.api.core.Message.STREAM_TYPE;
import static org.apache.activemq.artemis.api.core.Message.TEXT_TYPE;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_UNKNOWN;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ORIGINAL_ENCODING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.NON_PERSISTENT;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.PERSISTENT;

public class CoreMessageWrapper {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final ICoreMessage message;
   private ActiveMQBuffer readBodyBuffer;

   public CoreMessageWrapper(ICoreMessage message) {
      this.message = message;
   }

   public static CoreMessageWrapper wrap(ICoreMessage wrapped) {
      return switch (wrapped.getType()) {
         case STREAM_TYPE -> new CoreStreamMessageWrapper(wrapped);
         case BYTES_TYPE -> new CoreBytesMessageWrapper(wrapped);
         case MAP_TYPE -> new CoreMapMessageWrapper(wrapped);
         case TEXT_TYPE -> new CoreTextMessageWrapper(wrapped);
         case OBJECT_TYPE -> new CoreObjectMessageWrapper(wrapped);
         default -> new CoreMessageWrapper(wrapped);
      };
   }

   protected short getOrignalEncoding() {
      short orignalEncoding = AMQP_UNKNOWN;

      try {
         orignalEncoding = message.getShortProperty(JMS_AMQP_ORIGINAL_ENCODING);
      } catch (Exception ex) {
         logger.debug(ex.getMessage(), ex);
      // Ignore and stick with UNKNOWN
      }
      return orignalEncoding;
   }


   // returns the AMQP Section for the message type
   public Section createAMQPSection(Map<Symbol, Object> maMap, Properties properties) throws ConversionException {
      maMap.put(AMQPMessageSupport.JMS_MSG_TYPE, AMQPMessageSupport.JMS_MESSAGE);
      // If this is not an AMQP message that was converted then the original encoding
      // will be unknown so we check for special cases of messages with special data
      // encoded into the server message body.
      ICoreMessage internalMessage = this.getInnerMessage();

      // this will represent a readOnly buffer for the message
      ActiveMQBuffer buffer = internalMessage.getDataBuffer();
      try {
         // the buffer may be completely empty (e.g. if the original AMQP message had a null body)
         if (buffer.readableBytes() > 0) {
            Object s = buffer.readNullableSimpleString();
            if (s != null) {
               return new AmqpValue(s.toString());
            }
         }

         return null;
      } catch (Throwable e) {
         logger.debug("Exception ignored during conversion {}", e.getMessage(), e);
         return new AmqpValue("Conversion to AMQP error: " + e.getMessage());
      }

   }

   public ICoreMessage getInnerMessage() {
      return message;
   }

   /**
    * When reading we use a protected copy so multi-threads can work fine
    */
   protected ActiveMQBuffer getReadBodyBuffer() {
      if (readBodyBuffer == null) {
         // to avoid clashes between multiple threads
         readBodyBuffer = message.getDataBuffer();
      }
      return readBodyBuffer;
   }

   /**
    * When writing on the conversion we use the buffer directly
    */
   protected ActiveMQBuffer getWriteBodyBuffer() {
      readBodyBuffer = null; // it invalidates this buffer if anything is written
      return message.getBodyBuffer();
   }

   public final String getJMSMessageID() {
      if (message.containsProperty(NATIVE_MESSAGE_ID)) {
         return getStringProperty(NATIVE_MESSAGE_ID);
      }
      return null;
   }

   public final void setJMSMessageID(String id) {
      if (id != null) {
         message.putStringProperty(NATIVE_MESSAGE_ID, id);
      }
   }

   public final long getJMSTimestamp() {
      return message.getTimestamp();
   }

   public final void setJMSTimestamp(long timestamp) {
      message.setTimestamp(timestamp);
   }

   public final byte[] getJMSCorrelationIDAsBytes() {
      return MessageUtil.getJMSCorrelationIDAsBytes(message);
   }

   public final void setJMSCorrelationIDAsBytes(byte[] correlationID) {
      message.setCorrelationID(correlationID);
   }

   public final String getJMSCorrelationID() {

      Object correlationID = message.getCorrelationID();
      if (correlationID instanceof String string) {

         return string;
      } else if (correlationID != null) {
         return String.valueOf(correlationID);
      } else {
         return null;
      }
   }

   public final void setJMSCorrelationID(String correlationID) {
      message.setCorrelationID(correlationID);
   }

   public final SimpleString getJMSReplyTo() {
      return MessageUtil.getJMSReplyTo(message);
   }

   public final void setJMSReplyTo(String replyTo) {
      MessageUtil.setJMSReplyTo(message, SimpleString.of(replyTo));
   }

   public SimpleString getDestination() {
      if (message.getAddress() == null || message.getAddress().isEmpty()) {
         return null;
      }
      return SimpleString.of(AMQPMessageSupport.destination(message.getRoutingType(), message.getAddress()));
   }

   public final void setDestination(String destination) {
      message.setAddress(destination);
   }

   public final int getJMSDeliveryMode() {
      return message.isDurable() ? PERSISTENT : NON_PERSISTENT;
   }

   public final void setDeliveryMode(int deliveryMode) throws ConversionException {
      switch (deliveryMode) {
         case PERSISTENT:
            message.setDurable(true);
            break;
         case NON_PERSISTENT:
            message.setDurable(false);
            break;
         default:
            // There shouldn't be any other values used
            throw new ConversionException("Invalid mode " + deliveryMode);
      }
   }


   public final String getJMSType() {
      return MessageUtil.getJMSType(message);
   }

   public final void setJMSType(String type) {
      MessageUtil.setJMSType(message, type);
   }

   public final long getExpiration() {
      return message.getExpiration();
   }

   public final void setJMSExpiration(long expiration) {
      message.setExpiration(expiration);
   }


   public final int getJMSPriority() {
      return message.getPriority();
   }

   public final void setJMSPriority(int priority) {
      message.setPriority((byte) priority);
   }

   public final void clearProperties() {
      MessageUtil.clearProperties(message);

   }

   public final boolean propertyExists(String name) {
      return MessageUtil.propertyExists(message, name);
   }

   public final boolean getBooleanProperty(String name) {
      return message.getBooleanProperty(name);
   }

   public final byte getByteProperty(String name) {
      return message.getByteProperty(name);
   }

   public final short getShortProperty(String name) {
      return message.getShortProperty(name);
   }

   public final int getIntProperty(String name) {
      return MessageUtil.getIntProperty(message, name);
   }

   public final long getLongProperty(String name) {
      return MessageUtil.getLongProperty(message, name);
   }

   public final float getFloatProperty(String name) {
      return message.getFloatProperty(name);
   }

   public final double getDoubleProperty(String name) {
      return message.getDoubleProperty(name);
   }

   public final String getStringProperty(String name) {
      return MessageUtil.getStringProperty(message, name);
   }

   public final Object getObjectProperty(String name) {
      return MessageUtil.getObjectProperty(message, name);
   }

   public final Enumeration getPropertyNames() {
      return Collections.enumeration(MessageUtil.getPropertyNames(message));
   }

   public final void setBooleanProperty(String name, boolean value) {
      message.putBooleanProperty(name, value);
   }

   public final void setByteProperty(String name, byte value) {
      message.putByteProperty(name, value);
   }

   public final void setShortProperty(String name, short value) {
      message.putShortProperty(name, value);
   }

   public final void setIntProperty(String name, int value) {
      MessageUtil.setIntProperty(message, name, value);
   }

   public final void setLongProperty(String name, long value) {
      MessageUtil.setLongProperty(message, name, value);
   }

   public final void setFloatProperty(String name, float value) {
      message.putFloatProperty(name, value);
   }

   public final void setDoubleProperty(String name, double value) {
      message.putDoubleProperty(name, value);
   }

   public final void setStringProperty(String name, String value) {
      MessageUtil.setStringProperty(message, name, value);
   }

   public final void setObjectProperty(String name, Object value) {
      MessageUtil.setObjectProperty(message, name, value);
   }


   public void clearBody() {
      message.getBodyBuffer().clear();
   }

   /**
    * Encode the body into the internal message
    */
   public void encode() {
      if (!message.isLargeMessage()) {
         message.getBodyBuffer().resetReaderIndex();
      }
   }

   public void decode() {
      if (!message.isLargeMessage()) {
         message.getBodyBuffer().resetReaderIndex();
      }
   }

}
