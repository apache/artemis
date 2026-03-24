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
package org.apache.activemq.artemis.jms.client;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;

import static org.apache.activemq.artemis.reader.TextMessageUtil.readBodyText;
import static org.apache.activemq.artemis.reader.TextMessageUtil.writeBodyText;

/**
 * ActiveMQ Artemis implementation of a JMS TextMessage.
 * <br>
 * This class was ported from SpyTextMessage in JBossMQ.
 */
public class ActiveMQTextMessage extends ActiveMQMessage implements TextMessage {

   public static final byte TYPE = Message.TEXT_TYPE;


   // We cache it locally - it's more performant to cache as a SimpleString, the AbstractChannelBuffer write
   // methods are more efficient for a SimpleString
   private SimpleString text;


   public ActiveMQTextMessage(final ClientSession session) {
      super(ActiveMQTextMessage.TYPE, session);
   }

   public ActiveMQTextMessage(final ClientMessage message, final ClientSession session) {
      super(message, session);
   }

   /**
    * A copy constructor for non-ActiveMQ Artemis JMS TextMessages.
    */
   public ActiveMQTextMessage(final TextMessage foreign, final ClientSession session) throws JMSException {
      super(foreign, ActiveMQTextMessage.TYPE, session);

      setText(foreign.getText());
   }


   @Override
   public byte getType() {
      return ActiveMQTextMessage.TYPE;
   }

   // TextMessage implementation ------------------------------------

   @Override
   public void setText(final String text) throws JMSException {
      checkWrite();

      if (text != null) {
         this.text = new SimpleString(text);
      } else {
         this.text = null;
      }

      writeBodyText(message.getBodyBuffer(), this.text);
   }

   @Override
   public String getText() {
      if (text != null) {
         return text.toString();
      } else {
         return null;
      }
   }

   @Override
   public void clearBody() throws JMSException {
      super.clearBody();

      text = null;
   }

   // ActiveMQRAMessage override -----------------------------------------

   @Override
   public void doBeforeReceive() throws ActiveMQException {
      super.doBeforeReceive();

      text = readBodyText(message.getBodyBuffer());
   }

   @Override
   protected <T> T getBodyInternal(Class<T> c) {
      return (T) getText();
   }

   @Override
   @SuppressWarnings("unchecked")
   public boolean isBodyAssignableTo(Class c) {
      if (text == null)
         return true;
      return c.isAssignableFrom(java.lang.String.class);
   }
}
