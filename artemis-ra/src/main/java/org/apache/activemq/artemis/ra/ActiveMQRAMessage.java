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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for a {@link Message}.
 */
public class ActiveMQRAMessage implements Message {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected Message message;

   protected ActiveMQRASession session;

   public ActiveMQRAMessage(final Message message, final ActiveMQRASession session) {
      this.message = Objects.requireNonNull(message);
      this.session = Objects.requireNonNull(session);
      logger.trace("constructor({}, {})", message, session);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void acknowledge() throws JMSException {
      logger.trace("acknowledge()");

      session.getSession(); // Check for closed
      message.acknowledge();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void clearBody() throws JMSException {
      logger.trace("clearBody()");

      message.clearBody();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void clearProperties() throws JMSException {
      logger.trace("clearProperties()");

      message.clearProperties();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getBooleanProperty(final String name) throws JMSException {
      logger.trace("getBooleanProperty({})", name);

      return message.getBooleanProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public byte getByteProperty(final String name) throws JMSException {
      logger.trace("getByteProperty({})", name);

      return message.getByteProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public double getDoubleProperty(final String name) throws JMSException {
      logger.trace("getDoubleProperty({})", name);

      return message.getDoubleProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public float getFloatProperty(final String name) throws JMSException {
      logger.trace("getFloatProperty({})", name);

      return message.getFloatProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getIntProperty(final String name) throws JMSException {
      logger.trace("getIntProperty({})", name);

      return message.getIntProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getJMSCorrelationID() throws JMSException {
      logger.trace("getJMSCorrelationID()");

      return message.getJMSCorrelationID();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
      logger.trace("getJMSCorrelationIDAsBytes()");

      return message.getJMSCorrelationIDAsBytes();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getJMSDeliveryMode() throws JMSException {
      logger.trace("getJMSDeliveryMode()");

      return message.getJMSDeliveryMode();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Destination getJMSDestination() throws JMSException {
      logger.trace("getJMSDestination()");

      return message.getJMSDestination();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getJMSExpiration() throws JMSException {
      logger.trace("getJMSExpiration()");

      return message.getJMSExpiration();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getJMSMessageID() throws JMSException {
      logger.trace("getJMSMessageID()");

      return message.getJMSMessageID();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getJMSPriority() throws JMSException {
      logger.trace("getJMSPriority()");

      return message.getJMSPriority();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getJMSRedelivered() throws JMSException {
      logger.trace("getJMSRedelivered()");

      return message.getJMSRedelivered();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Destination getJMSReplyTo() throws JMSException {
      logger.trace("getJMSReplyTo()");

      return message.getJMSReplyTo();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getJMSTimestamp() throws JMSException {
      logger.trace("getJMSTimestamp()");

      return message.getJMSTimestamp();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getJMSType() throws JMSException {
      logger.trace("getJMSType()");

      return message.getJMSType();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getLongProperty(final String name) throws JMSException {
      logger.trace("getLongProperty({})", name);

      return message.getLongProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object getObjectProperty(final String name) throws JMSException {
      logger.trace("getObjectProperty({})", name);

      return message.getObjectProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Enumeration getPropertyNames() throws JMSException {
      logger.trace("getPropertyNames()");

      return message.getPropertyNames();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public short getShortProperty(final String name) throws JMSException {
      logger.trace("getShortProperty({})", name);

      return message.getShortProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getStringProperty(final String name) throws JMSException {
      logger.trace("getStringProperty({})", name);

      return message.getStringProperty(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean propertyExists(final String name) throws JMSException {
      logger.trace("propertyExists({})", name);

      return message.propertyExists(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setBooleanProperty(final String name, final boolean value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setBooleanProperty({}, {})", name, value);
      }

      message.setBooleanProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setByteProperty(final String name, final byte value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setByteProperty({}, {})", name, value);
      }

      message.setByteProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setDoubleProperty(final String name, final double value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setDoubleProperty({}, {})", name, value);
      }

      message.setDoubleProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setFloatProperty(final String name, final float value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setFloatProperty({}, {})", name, value);
      }

      message.setFloatProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setIntProperty(final String name, final int value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setIntProperty({}, {})", name, value);
      }

      message.setIntProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSCorrelationID(final String correlationID) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSCorrelationID({})", correlationID);
      }

      message.setJMSCorrelationID(correlationID);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSCorrelationIDAsBytes(final byte[] correlationID) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSCorrelationIDAsBytes({})", Arrays.toString(correlationID));
      }

      message.setJMSCorrelationIDAsBytes(correlationID);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSDeliveryMode(final int deliveryMode) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSDeliveryMode({})", deliveryMode);
      }

      message.setJMSDeliveryMode(deliveryMode);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSDestination(final Destination destination) throws JMSException {
      logger.trace("setJMSDestination({})", destination);

      message.setJMSDestination(destination);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSExpiration(final long expiration) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSExpiration({})", expiration);
      }

      message.setJMSExpiration(expiration);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSMessageID(final String id) throws JMSException {
      logger.trace("setJMSMessageID({})", id);

      message.setJMSMessageID(id);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSPriority(final int priority) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSPriority({})", priority);
      }

      message.setJMSPriority(priority);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSRedelivered(final boolean redelivered) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSRedelivered({})", redelivered);
      }

      message.setJMSRedelivered(redelivered);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSReplyTo(final Destination replyTo) throws JMSException {
      logger.trace("setJMSReplyTo({})", replyTo);

      message.setJMSReplyTo(replyTo);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSTimestamp(final long timestamp) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSTimestamp({})", timestamp);
      }

      message.setJMSTimestamp(timestamp);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSType(final String type) throws JMSException {
      logger.trace("setJMSType({})", type);

      message.setJMSType(type);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setLongProperty(final String name, final long value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setLongProperty({}, {})", name, value);
      }

      message.setLongProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setObjectProperty(final String name, final Object value) throws JMSException {
      logger.trace("setObjectProperty({}, {})", name, value);

      message.setObjectProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setShortProperty(final String name, final short value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setShortProperty({}, {})", name, value);
      }

      message.setShortProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setStringProperty(final String name, final String value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setStringProperty({}, {})", name, value);
      }

      message.setStringProperty(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getJMSDeliveryTime() throws JMSException {
      logger.trace("getJMSDeliveryTime()");

      return message.getJMSDeliveryTime();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setJMSDeliveryTime(long deliveryTime) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setJMSDeliveryTime({})", deliveryTime);
      }

      message.setJMSDeliveryTime(deliveryTime);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public <T> T getBody(Class<T> c) throws JMSException {
      logger.trace("getBody({})", c);

      return message.getBody(c);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isBodyAssignableTo(Class c) throws JMSException {
      logger.trace("isBodyAssignableTo({})", c);

      return message.isBodyAssignableTo(c);
   }

   @Override
   public int hashCode() {
      logger.trace("hashCode()");

      return message.hashCode();
   }

   @Override
   public boolean equals(final Object object) {
      logger.trace("equals({})", object);
      if (this == object) {
         return true;
      }
      if (!(object instanceof ActiveMQRAMessage activeMQRAMessage)) {
         return false;
      }

      return Objects.equals(message, activeMQRAMessage.message);
   }

   @Override
   public String toString() {
      logger.trace("toString()");

      return message.toString();
   }
}
