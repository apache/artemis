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

import javax.jms.JMSConsumer;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

public class ActiveMQJMSConsumer implements JMSConsumer {

   private final ActiveMQJMSContext context;
   private final MessageConsumer consumer;

   ActiveMQJMSConsumer(ActiveMQJMSContext context, MessageConsumer consumer) {
      this.context = context;
      this.consumer = consumer;
   }

   @Override
   public String getMessageSelector() {
      try {
         return consumer.getMessageSelector();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public MessageListener getMessageListener() throws JMSRuntimeException {
      try {
         return consumer.getMessageListener();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void setMessageListener(MessageListener listener) throws JMSRuntimeException {
      try {
         consumer.setMessageListener(new MessageListenerWrapper(listener));
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public Message receive() {
      try {
         return context.setLastMessage(consumer.receive());
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public Message receive(long timeout) {
      try {
         return context.setLastMessage(consumer.receive(timeout));
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public Message receiveNoWait() {
      try {
         return context.setLastMessage(consumer.receiveNoWait());
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void close() {
      try {
         consumer.close();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public <T> T receiveBody(Class<T> c) {
      try {
         Message message = consumer.receive();
         context.setLastMessage(message);
         return message == null ? null : message.getBody(c);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public <T> T receiveBody(Class<T> c, long timeout) {
      try {
         Message message = consumer.receive(timeout);
         context.setLastMessage(message);
         return message == null ? null : message.getBody(c);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public <T> T receiveBodyNoWait(Class<T> c) {
      try {
         Message message = consumer.receiveNoWait();
         context.setLastMessage(message);
         return message == null ? null : message.getBody(c);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   final class MessageListenerWrapper implements MessageListener {

      private final MessageListener wrapped;

      MessageListenerWrapper(MessageListener wrapped) {
         this.wrapped = wrapped;
      }

      @Override
      public void onMessage(Message message) {
         context.setLastMessage(message);

         context.getThreadAwareContext().setCurrentThread(false);
         try {
            wrapped.onMessage(message);
         } finally {
            context.getThreadAwareContext().clearCurrentThread(false);
         }
      }
   }
}
