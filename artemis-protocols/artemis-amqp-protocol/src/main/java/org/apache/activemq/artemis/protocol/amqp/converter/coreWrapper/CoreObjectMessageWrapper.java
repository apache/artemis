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
package org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_BINARY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.EMPTY_BINARY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_CONTENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE;

public class CoreObjectMessageWrapper extends CoreMessageWrapper {

   public static final byte TYPE = Message.OBJECT_TYPE;

   private Binary payload;


   private static Binary getBinaryFromMessageBody(CoreObjectMessageWrapper message) {
      return message.getSerializedForm();
   }

   @Override
   public Section createAMQPSection(Map<Symbol, Object> maMap, Properties properties) throws ConversionException {
      properties.setContentType(AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE);
      maMap.put(AMQPMessageSupport.JMS_MSG_TYPE, AMQPMessageSupport.JMS_OBJECT_MESSAGE);
      Binary payload = getBinaryFromMessageBody(this);

      if (payload == null) {
         payload = EMPTY_BINARY;
      }

      // For a non-AMQP message we tag the outbound content type as containing
      // a serialized Java object so that an AMQP client has a hint as to what
      // we are sending it.
      if (!this.propertyExists(JMS_AMQP_CONTENT_TYPE)) {
         this.setStringProperty(JMS_AMQP_CONTENT_TYPE, SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString());
      }

      return switch (getOrignalEncoding()) {
         case AMQP_VALUE_BINARY -> new AmqpValue(payload);
         default -> new Data(payload);
      };

   }

   public CoreObjectMessageWrapper(ICoreMessage message) {
      super(message);
   }

   public void setSerializedForm(Binary payload) {
      this.payload = payload;
   }

   public Binary getSerializedForm() {
      return payload;
   }

   @Override
   public void encode() {
      super.encode();
      getInnerMessage().getBodyBuffer().writeInt(payload.getLength());
      getInnerMessage().getBodyBuffer().writeBytes(payload.getArray(), payload.getArrayOffset(), payload.getLength());
   }

   @Override
   public void decode() {
      super.decode();
      ActiveMQBuffer buffer = getInnerMessage().getDataBuffer();
      int size = buffer.readInt();
      byte[] bytes = new byte[size];
      buffer.readBytes(bytes);
      payload = new Binary(bytes);
   }
}
