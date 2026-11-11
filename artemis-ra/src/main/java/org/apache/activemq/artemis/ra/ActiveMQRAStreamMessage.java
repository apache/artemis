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
import javax.jms.StreamMessage;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link StreamMessage}.
 */
public class ActiveMQRAStreamMessage extends ActiveMQRAMessage implements StreamMessage {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ActiveMQRAStreamMessage(final StreamMessage message, final ActiveMQRASession session) {
      super(message, session);

      logger.trace("constructor({}, {})", message, session);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean readBoolean() throws JMSException {
      logger.trace("readBoolean()");

      return ((StreamMessage) message).readBoolean();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public byte readByte() throws JMSException {
      logger.trace("readByte()");

      return ((StreamMessage) message).readByte();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readBytes(final byte[] value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("readBytes({})", Arrays.toString(value));
      }

      return ((StreamMessage) message).readBytes(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public char readChar() throws JMSException {
      logger.trace("readChar()");

      return ((StreamMessage) message).readChar();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public double readDouble() throws JMSException {
      logger.trace("readDouble()");

      return ((StreamMessage) message).readDouble();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public float readFloat() throws JMSException {
      logger.trace("readFloat()");

      return ((StreamMessage) message).readFloat();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readInt() throws JMSException {
      logger.trace("readInt()");

      return ((StreamMessage) message).readInt();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long readLong() throws JMSException {
      logger.trace("readLong()");

      return ((StreamMessage) message).readLong();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object readObject() throws JMSException {
      logger.trace("readObject()");

      return ((StreamMessage) message).readObject();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public short readShort() throws JMSException {
      logger.trace("readShort()");

      return ((StreamMessage) message).readShort();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String readString() throws JMSException {
      logger.trace("readString()");

      return ((StreamMessage) message).readString();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void reset() throws JMSException {
      logger.trace("reset()");

      ((StreamMessage) message).reset();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeBoolean(final boolean value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeBoolean({})", value);
      }

      ((StreamMessage) message).writeBoolean(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeByte(final byte value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeByte({})", value);
      }

      ((StreamMessage) message).writeByte(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeBytes({}, {}, {})", Arrays.toString(value), offset, length);
      }

      ((StreamMessage) message).writeBytes(value, offset, length);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeBytes(final byte[] value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeBytes({})", Arrays.toString(value));
      }

      ((StreamMessage) message).writeBytes(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeChar(final char value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeChar({})", value);
      }

      ((StreamMessage) message).writeChar(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeDouble(final double value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeDouble({})", value);
      }

      ((StreamMessage) message).writeDouble(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeFloat(final float value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeFloat({})", value);
      }

      ((StreamMessage) message).writeFloat(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeInt(final int value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeInt({})", value);
      }

      ((StreamMessage) message).writeInt(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeLong(final long value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeLong({})", value);
      }

      ((StreamMessage) message).writeLong(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeObject(final Object value) throws JMSException {
      logger.trace("writeObject({})", value);

      ((StreamMessage) message).writeObject(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeShort(final short value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeShort({})", value);
      }

      ((StreamMessage) message).writeShort(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeString(final String value) throws JMSException {
      logger.trace("writeString({})", value);

      ((StreamMessage) message).writeString(value);
   }
}
