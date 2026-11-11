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
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link BytesMessage}
 */
public class ActiveMQRABytesMessage extends ActiveMQRAMessage implements BytesMessage {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * Create a new wrapper
    *
    * @param message the message
    * @param session the session
    */
   public ActiveMQRABytesMessage(final BytesMessage message, final ActiveMQRASession session) {
      super(message, session);

      logger.trace("constructor({}, {})", message, session);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getBodyLength() throws JMSException {
      logger.trace("getBodyLength()");

      return ((BytesMessage) message).getBodyLength();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean readBoolean() throws JMSException {
      logger.trace("readBoolean()");

      return ((BytesMessage) message).readBoolean();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public byte readByte() throws JMSException {
      logger.trace("readByte()");

      return ((BytesMessage) message).readByte();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readBytes(final byte[] value, final int length) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("readBytes({}, {})", Arrays.toString(value), length);
      }

      return ((BytesMessage) message).readBytes(value, length);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readBytes(final byte[] value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("readBytes({})", Arrays.toString(value));
      }

      return ((BytesMessage) message).readBytes(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public char readChar() throws JMSException {
      logger.trace("readChar()");

      return ((BytesMessage) message).readChar();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public double readDouble() throws JMSException {
      logger.trace("readDouble()");

      return ((BytesMessage) message).readDouble();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public float readFloat() throws JMSException {
      logger.trace("readFloat()");

      return ((BytesMessage) message).readFloat();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readInt() throws JMSException {
      logger.trace("readInt()");

      return ((BytesMessage) message).readInt();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long readLong() throws JMSException {
      logger.trace("readLong()");

      return ((BytesMessage) message).readLong();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public short readShort() throws JMSException {
      logger.trace("readShort()");

      return ((BytesMessage) message).readShort();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readUnsignedByte() throws JMSException {
      logger.trace("readUnsignedByte()");

      return ((BytesMessage) message).readUnsignedByte();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int readUnsignedShort() throws JMSException {
      logger.trace("readUnsignedShort()");

      return ((BytesMessage) message).readUnsignedShort();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String readUTF() throws JMSException {
      logger.trace("readUTF()");

      return ((BytesMessage) message).readUTF();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void reset() throws JMSException {
      logger.trace("reset()");

      ((BytesMessage) message).reset();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeBoolean(final boolean value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeBoolean({})", value);
      }

      ((BytesMessage) message).writeBoolean(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeByte(final byte value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeByte({})", value);
      }

      ((BytesMessage) message).writeByte(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeBytes({}, {}, {})", Arrays.toString(value), offset, length);
      }

      ((BytesMessage) message).writeBytes(value, offset, length);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeBytes(final byte[] value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeBytes({})", Arrays.toString(value));
      }

      ((BytesMessage) message).writeBytes(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeChar(final char value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeChar({})", value);
      }

      ((BytesMessage) message).writeChar(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeDouble(final double value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeDouble({})", value);
      }

      ((BytesMessage) message).writeDouble(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeFloat(final float value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeFloat({})", value);
      }

      ((BytesMessage) message).writeFloat(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeInt(final int value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeInt({})", value);
      }

      ((BytesMessage) message).writeInt(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeLong(final long value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeLong({})", value);
      }

      ((BytesMessage) message).writeLong(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeObject(final Object value) throws JMSException {
      logger.trace("writeObject({})", value);

      ((BytesMessage) message).writeObject(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeShort(final short value) throws JMSException {
      if (logger.isTraceEnabled()) {
         logger.trace("writeShort({})", value);
      }

      ((BytesMessage) message).writeShort(value);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeUTF(final String value) throws JMSException {
      logger.trace("writeUTF({})", value);

      ((BytesMessage) message).writeUTF(value);
   }
}
