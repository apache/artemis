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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_DATA;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_NULL;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_UNKNOWN;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_STRING;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.EMPTY_BINARY;
import static org.apache.activemq.artemis.reader.TextMessageUtil.readBodyText;
import static org.apache.activemq.artemis.reader.TextMessageUtil.writeBodyText;


public class CoreTextMessageWrapper extends CoreMessageWrapper {

   public static final byte TYPE = Message.TEXT_TYPE;



   // We cache it locally - it's more performant to cache as a SimpleString, the AbstractChannelBuffer write
   // methods are more efficient for a SimpleString
   private SimpleString text;


   /*
    * This constructor is used to construct messages prior to sending
    */
   public CoreTextMessageWrapper(ICoreMessage message) {
      super(message);

   }

   @Override
   public Section createAMQPSection(Map<Symbol, Object> maMap, Properties properties) {
      Section body = null;

      String text = getText();

      switch (getOrignalEncoding()) {
         case AMQP_NULL:
            break;
         case AMQP_DATA:
            if (text == null) {
               body = new Data(EMPTY_BINARY);
            } else {
               body = new Data(new Binary(text.getBytes(StandardCharsets.UTF_8)));
            }
            break;
         case AMQP_VALUE_STRING:
         case AMQP_UNKNOWN:
         default:
            body = new AmqpValue(text);
            break;
      }

      maMap.put(AMQPMessageSupport.JMS_MSG_TYPE, AMQPMessageSupport.JMS_TEXT_MESSAGE);

      return body;
   }

   public void setText(final String text)  {
      if (text != null) {
         this.text = new SimpleString(text);
      } else {
         this.text = null;
      }

      writeBodyText(getWriteBodyBuffer(), this.text);
   }

   public String getText() {
      if (text != null) {
         return text.toString();
      } else {
         return null;
      }
   }

   @Override
   public void clearBody()  {
      super.clearBody();

      text = null;
   }

   @Override
   public void encode()  {
      super.encode();
      writeBodyText(getWriteBodyBuffer(), text);
   }

   @Override
   public void decode()  {
      super.decode();
      text = readBodyText(getReadBodyBuffer());
   }

}
