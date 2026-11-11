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
import javax.jms.MapMessage;
import java.util.Arrays;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link MapMessage}.
 */
public class ActiveMQRAMapMessage extends ActiveMQRAMessage implements MapMessage {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ActiveMQRAMapMessage(final MapMessage message, final ActiveMQRASession session) {
      super(message, session);

      logger.trace("constructor({}, {})", message, session);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getBoolean(final String name) throws JMSException {
      logger.trace("getBoolean({})", name);

      return ((MapMessage) message).getBoolean(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public byte getByte(final String name) throws JMSException {
      logger.trace("getByte({})", name);

      return ((MapMessage) message).getByte(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public byte[] getBytes(final String name) throws JMSException {
      logger.trace("getBytes({})", name);

      return ((MapMessage) message).getBytes(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public char getChar(final String name) throws JMSException {
      logger.trace("getChar({})", name);

      return ((MapMessage) message).getChar(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public double getDouble(final String name) throws JMSException {
      logger.trace("getDouble({})", name);

      return ((MapMessage) message).getDouble(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public float getFloat(final String name) throws JMSException {
      logger.trace("getFloat({})", name);

      return ((MapMessage) message).getFloat(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getInt(final String name) throws JMSException {
      logger.trace("getInt({})", name);

      return ((MapMessage) message).getInt(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getLong(final String name) throws JMSException {
      logger.trace("getLong({})", name);

      return ((MapMessage) message).getLong(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Enumeration getMapNames() throws JMSException {
      logger.trace("getMapNames()");

      return ((MapMessage) message).getMapNames();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object getObject(final String name) throws JMSException {
      logger.trace("getObject({})", name);

      return ((MapMessage) message).getObject(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public short getShort(final String name) throws JMSException {
      logger.trace("getShort({})", name);

      return ((MapMessage) message).getShort(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getString(final String name) throws JMSException {
      logger.trace("getString({})", name);

      return ((MapMessage) message).getString(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean itemExists(final String name) throws JMSException {
      logger.trace("itemExists({})", name);

      return ((MapMessage) message).itemExists(name);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setBoolean(final String name, final boolean value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setBoolean({}, {})", name, value);
      }

      ((MapMessage) message).setBoolean(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setByte(final String name, final byte value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setByte({}, {})", name, value);
      }

      ((MapMessage) message).setByte(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setBytes(final String name, final byte[] value, final int offset, final int length) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setBytes({}, {}, {}, {})", name, Arrays.toString(value), offset, length);
      }

      ((MapMessage) message).setBytes(name, value, offset, length);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setBytes(final String name, final byte[] value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setBytes({}, {})", name, Arrays.toString(value));
      }

      ((MapMessage) message).setBytes(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setChar(final String name, final char value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setChar({}, {})", name, value);
      }

      ((MapMessage) message).setChar(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setDouble(final String name, final double value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setDouble({}, {})", name, value);
      }

      ((MapMessage) message).setDouble(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setFloat(final String name, final float value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setFloat({}, {})", name, value);
      }

      ((MapMessage) message).setFloat(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setInt(final String name, final int value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setInt({}, {})", name, value);
      }

      ((MapMessage) message).setInt(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setLong(final String name, final long value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setLong({}, {})", name, value);
      }

      ((MapMessage) message).setLong(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setObject(final String name, final Object value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setObject({}, {})", name, value);
      }

      ((MapMessage) message).setObject(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setShort(final String name, final short value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("setShort({}, {})", name, value);
      }

      ((MapMessage) message).setShort(name, value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setString(final String name, final String value) throws JMSException {
      logger.trace("setString({}, {})", name, value);

      ((MapMessage) message).setString(name, value);
   }
}
