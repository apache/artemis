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
package org.apache.activemq.artemis.ra;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link MessageConsumer}.
 */
public class ActiveMQRAMessageConsumer implements MessageConsumer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected MessageConsumer consumer;

   protected ActiveMQRASession session;

   public ActiveMQRAMessageConsumer(final MessageConsumer consumer, final ActiveMQRASession session) {
      this.consumer = consumer;
      this.session = session;

      if (logger.isTraceEnabled()) {
         logger.trace("new ActiveMQMessageConsumer {} consumer={} session={}", this, consumer, session);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws JMSException {
      logger.trace("close {}", this);

      try {
         closeConsumer();
      } finally {
         session.removeConsumer(this);
      }
   }

   void checkState() throws JMSException {
      logger.trace("checkState()");

      session.checkState();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MessageListener getMessageListener() throws JMSException {
      logger.trace("getMessageListener()");

      checkState();
      session.checkStrict();
      return consumer.getMessageListener();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void setMessageListener(final MessageListener listener) throws JMSException {
      session.lock();
      try {
         checkState();
         session.checkStrict();
         if (listener == null) {
            consumer.setMessageListener(null);
         } else {
            consumer.setMessageListener(wrapMessageListener(listener));
         }
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public String getMessageSelector() throws JMSException {
      logger.trace("getMessageSelector()");

      checkState();
      return consumer.getMessageSelector();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public Message receive() throws JMSException {
      session.lock();
      try {
         logger.trace("receive {}", this);

         checkState();
         Message message = consumer.receive();

         logger.trace("received {} result={}", this, message);

         if (message == null) {
            return null;
         } else {
            return wrapMessage(message);
         }
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public Message receive(final long timeout) throws JMSException {
      session.lock();
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("receive {} timeout={}", this, timeout);
         }

         checkState();
         Message message = consumer.receive(timeout);

         logger.trace("received {} result={}", this, message);

         if (message == null) {
            return null;
         } else {
            return wrapMessage(message);
         }
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public Message receiveNoWait() throws JMSException {
      session.lock();
      try {
         logger.trace("receiveNoWait {}", this);

         checkState();
         Message message = consumer.receiveNoWait();

         logger.trace("received {} result={}", this, message);

         if (message == null) {
            return null;
         } else {
            return wrapMessage(message);
         }
      } finally {
         session.unlock();
      }
   }

   void closeConsumer() throws JMSException {
      logger.trace("closeConsumer()");

      consumer.close();
   }

   Message wrapMessage(final Message message) {
      logger.trace("wrapMessage({})", message);

      if (message instanceof BytesMessage bytesMessage) {
         return new ActiveMQRABytesMessage(bytesMessage, session);
      } else if (message instanceof MapMessage mapMessage) {
         return new ActiveMQRAMapMessage(mapMessage, session);
      } else if (message instanceof ObjectMessage objectMessage) {
         return new ActiveMQRAObjectMessage(objectMessage, session);
      } else if (message instanceof StreamMessage streamMessage) {
         return new ActiveMQRAStreamMessage(streamMessage, session);
      } else if (message instanceof TextMessage textMessage) {
         return new ActiveMQRATextMessage(textMessage, session);
      }
      return new ActiveMQRAMessage(message, session);
   }

   MessageListener wrapMessageListener(final MessageListener listener) {
      logger.trace("getMessageSelector()");

      return new ActiveMQRAMessageListener(listener, this);
   }
}
