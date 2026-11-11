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

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link MessageProducer}.
 */
public class ActiveMQRAMessageProducer implements MessageProducer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected MessageProducer producer;

   protected ActiveMQRASession session;

   public ActiveMQRAMessageProducer(final MessageProducer producer, final ActiveMQRASession session) {
      this.producer = producer;
      this.session = session;

      if (logger.isTraceEnabled()) {
         logger.trace("new ActiveMQMessageProducer {}  producer={} session={}", this, producer, session);
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws JMSException {
      logger.trace("close {}", this);

      try {
         closeProducer();
      } finally {
         session.removeProducer(this);
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void send(final Destination destination,
                    final Message message,
                    final int deliveryMode,
                    final int priority,
                    final long timeToLive) throws JMSException {
      session.lock();
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("send {} destination={} message={} deliveryMode={} priority={} ttl={}",
               this, destination, message, deliveryMode, priority, timeToLive);
         }

         checkState();

         producer.send(destination, message, deliveryMode, priority, timeToLive);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void send(final Destination destination, final Message message) throws JMSException {
      session.lock();
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("send {} destination={} message={}", this, destination, message);
         }

         checkState();

         producer.send(destination, message);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void send(final Message message,
                    final int deliveryMode,
                    final int priority,
                    final long timeToLive) throws JMSException {
      session.lock();
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("send {} message={} deliveryMode={} priority={} ttl={}",
               this, message, deliveryMode, priority, timeToLive);
         }

         checkState();

         producer.send(message, deliveryMode, priority, timeToLive);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void send(final Message message) throws JMSException {
      session.lock();
      try {
         logger.trace("send {} result={}", this, message);

         checkState();

         producer.send(message);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public int getDeliveryMode() throws JMSException {
      logger.trace("getRoutingType()");

      return producer.getDeliveryMode();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public Destination getDestination() throws JMSException {
      logger.trace("getDestination()");

      return producer.getDestination();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getDisableMessageID() throws JMSException {
      logger.trace("getDisableMessageID()");

      return producer.getDisableMessageID();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getDisableMessageTimestamp() throws JMSException {
      logger.trace("getDisableMessageTimestamp()");

      return producer.getDisableMessageTimestamp();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public int getPriority() throws JMSException {
      logger.trace("getPriority()");

      return producer.getPriority();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public long getTimeToLive() throws JMSException {
      logger.trace("getTimeToLive()");

      return producer.getTimeToLive();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void setDeliveryMode(final int deliveryMode) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setRoutingType({})", deliveryMode);
      }

      producer.setDeliveryMode(deliveryMode);
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void setDisableMessageID(final boolean value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setDisableMessageID({})", value);
      }

      producer.setDisableMessageID(value);
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void setDisableMessageTimestamp(final boolean value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setDisableMessageTimestamp({})", value);
      }

      producer.setDisableMessageTimestamp(value);
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void setPriority(final int defaultPriority) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setPriority({})", defaultPriority);
      }

      producer.setPriority(defaultPriority);
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void setTimeToLive(final long timeToLive) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setTimeToLive({})", timeToLive);
      }

      producer.setTimeToLive(timeToLive);
   }

   @Override
   public void setDeliveryDelay(long deliveryDelay) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setDeliveryDelay({})", deliveryDelay);
      }
      producer.setDeliveryDelay(deliveryDelay);
   }

   @Override
   public long getDeliveryDelay() throws JMSException {
      logger.trace("getDeliveryDelay()");

      return producer.getDeliveryDelay();
   }

   @Override
   public void send(Message message, CompletionListener completionListener) throws JMSException {
      logger.trace("send({}, {})", message, completionListener);

      producer.send(message, completionListener);
   }

   @Override
   public void send(Message message,
                    int deliveryMode,
                    int priority,
                    long timeToLive,
                    CompletionListener completionListener) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("send({}, {}, {}, {}, {})", message, deliveryMode, priority, timeToLive, completionListener);
      }
      producer.send(message, deliveryMode, priority, timeToLive, completionListener);
   }

   @Override
   public void send(Destination destination,
                    Message message,
                    CompletionListener completionListener) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("send({}, {}, {})", destination, message, completionListener);
      }
      producer.send(destination, message, completionListener);
   }

   @Override
   public void send(Destination destination,
                    Message message,
                    int deliveryMode,
                    int priority,
                    long timeToLive,
                    CompletionListener completionListener) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("send({}, {}, {}, {}, {}, {})", destination, message, deliveryMode, priority, timeToLive, completionListener);
      }
      producer.send(destination, message, deliveryMode, priority, timeToLive, completionListener);
   }

   void checkState() throws JMSException {
      session.checkState();
   }

   void closeProducer() throws JMSException {
      producer.close();
   }
}
