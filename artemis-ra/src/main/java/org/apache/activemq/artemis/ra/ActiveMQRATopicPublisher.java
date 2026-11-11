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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link TopicPublisher}.
 */
public class ActiveMQRATopicPublisher extends ActiveMQRAMessageProducer implements TopicPublisher {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ActiveMQRATopicPublisher(final TopicPublisher producer, final ActiveMQRASession session) {
      super(producer, session);

      logger.trace("constructor({}, {})", producer, session);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Topic getTopic() throws JMSException {
      logger.trace("getTopic()");

      return ((TopicPublisher) producer).getTopic();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish(final Message message,
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

         ((TopicPublisher) producer).publish(message, deliveryMode, priority, timeToLive);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish(final Message message) throws JMSException {
      session.lock();
      try {
         logger.trace("send {} result={}", this, message);

         checkState();

         ((TopicPublisher) producer).publish(message);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish(final Topic destination,
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

         ((TopicPublisher) producer).publish(destination, message, deliveryMode, priority, timeToLive);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish(final Topic destination, final Message message) throws JMSException {
      session.lock();
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("send {} destination={} message={}", this, destination, message);
         }

         checkState();

         ((TopicPublisher) producer).publish(destination, message);

         logger.trace("sent {} result={}", this, message);
      } finally {
         session.unlock();
      }
   }
}
