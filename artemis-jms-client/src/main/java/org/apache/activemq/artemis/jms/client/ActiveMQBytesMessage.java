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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;

import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesMessageReset;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadBoolean;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadByte;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadBytes;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadChar;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadDouble;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadFloat;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadInt;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadLong;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadShort;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadUTF;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadUnsignedByte;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesReadUnsignedShort;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteBoolean;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteByte;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteBytes;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteChar;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteDouble;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteFloat;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteInt;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteLong;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteObject;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteShort;
import static org.apache.activemq.artemis.reader.BytesMessageUtil.bytesWriteUTF;

/**
 * ActiveMQ Artemis implementation of a JMS {@link BytesMessage}.
 */
public class ActiveMQBytesMessage extends ActiveMQMessage implements BytesMessage {

   public static final byte TYPE = Message.BYTES_TYPE;


   private int bodyLength;


   /**
    * This constructor is used to construct messages prior to sending
    */
   protected ActiveMQBytesMessage(final ClientSession session) {
      super(ActiveMQBytesMessage.TYPE, session);
   }

   /**
    * Constructor on receipt at client side
    */
   protected ActiveMQBytesMessage(final ClientMessage message, final ClientSession session) {
      super(message, session);
   }

   /**
    * Foreign message constructor
    */
   public ActiveMQBytesMessage(final BytesMessage foreign, final ClientSession session) throws JMSException {
      super(foreign, ActiveMQBytesMessage.TYPE, session);

      foreign.reset();

      byte[] buffer = new byte[1024];
      int n = foreign.readBytes(buffer);
      while (n != -1) {
         writeBytes(buffer, 0, n);
         n = foreign.readBytes(buffer);
      }
   }

   // BytesMessage implementation -----------------------------------

   @Override
   public boolean readBoolean() throws JMSException {
      checkRead();
      try {
         return bytesReadBoolean(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public byte readByte() throws JMSException {
      checkRead();
      try {
         return bytesReadByte(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public int readUnsignedByte() throws JMSException {
      checkRead();
      try {
         return bytesReadUnsignedByte(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public short readShort() throws JMSException {
      checkRead();
      try {
         return bytesReadShort(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public int readUnsignedShort() throws JMSException {
      checkRead();
      try {
         return bytesReadUnsignedShort(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public char readChar() throws JMSException {
      checkRead();
      try {
         return bytesReadChar(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public int readInt() throws JMSException {
      checkRead();
      try {
         return bytesReadInt(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public long readLong() throws JMSException {
      checkRead();
      try {
         return bytesReadLong(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public float readFloat() throws JMSException {
      checkRead();
      try {
         return bytesReadFloat(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public double readDouble() throws JMSException {
      checkRead();
      try {
         return bytesReadDouble(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public String readUTF() throws JMSException {
      checkRead();
      try {
         return bytesReadUTF(message.getBodyBuffer());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      } catch (Exception e) {
         JMSException je = new JMSException("Failed to get UTF");
         je.setLinkedException(e);
         je.initCause(e);
         throw je;
      }
   }

   @Override
   public int readBytes(final byte[] value) throws JMSException {
      checkRead();
      return bytesReadBytes(message.getBodyBuffer(), value);
   }

   @Override
   public int readBytes(final byte[] value, final int length) throws JMSException {
      checkRead();
      return bytesReadBytes(message.getBodyBuffer(), value, length);

   }

   @Override
   public void writeBoolean(final boolean value) throws JMSException {
      checkWrite();
      bytesWriteBoolean(message.getBodyBuffer(), value);
   }

   @Override
   public void writeByte(final byte value) throws JMSException {
      checkWrite();
      bytesWriteByte(message.getBodyBuffer(), value);
   }

   @Override
   public void writeShort(final short value) throws JMSException {
      checkWrite();
      bytesWriteShort(message.getBodyBuffer(), value);
   }

   @Override
   public void writeChar(final char value) throws JMSException {
      checkWrite();
      bytesWriteChar(message.getBodyBuffer(), value);
   }

   @Override
   public void writeInt(final int value) throws JMSException {
      checkWrite();
      bytesWriteInt(message.getBodyBuffer(), value);
   }

   @Override
   public void writeLong(final long value) throws JMSException {
      checkWrite();
      bytesWriteLong(message.getBodyBuffer(), value);
   }

   @Override
   public void writeFloat(final float value) throws JMSException {
      checkWrite();
      bytesWriteFloat(message.getBodyBuffer(), value);
   }

   @Override
   public void writeDouble(final double value) throws JMSException {
      checkWrite();
      bytesWriteDouble(message.getBodyBuffer(), value);
   }

   @Override
   public void writeUTF(final String value) throws JMSException {
      checkWrite();
      try {
         bytesWriteUTF(message.getBodyBuffer(), value);
      } catch (Exception e) {
         JMSException je = new JMSException("Failed to write UTF");
         je.setLinkedException(e);
         je.initCause(e);
         throw je;
      }

   }

   @Override
   public void writeBytes(final byte[] value) throws JMSException {
      checkWrite();
      bytesWriteBytes(message.getBodyBuffer(), value);
   }

   @Override
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException {
      checkWrite();
      bytesWriteBytes(message.getBodyBuffer(), value, offset, length);
   }

   @Override
   public void writeObject(final Object value) throws JMSException {
      checkWrite();
      if (!bytesWriteObject(message.getBodyBuffer(), value)) {
         throw new MessageFormatException("Invalid object for properties");
      }
   }

   @Override
   public void reset() throws JMSException {
      if (!readOnly) {
         readOnly = true;

         bodyLength = message.getBodySize();
      }

      bytesMessageReset(message.getBodyBuffer());
   }

   @Override
   public void doBeforeReceive() throws ActiveMQException {
      bodyLength = message.getBodySize();
   }

   @Override
   public void clearBody() throws JMSException {
      super.clearBody();

      try {
         getBuffer().clear();
      } catch (RuntimeException e) {
         JMSException e2 = new JMSException(e.getMessage());
         e2.initCause(e);
         throw e2;
      }
   }

   @Override
   public long getBodyLength() throws JMSException {
      checkRead();

      return bodyLength;
   }

   @Override
   public void doBeforeSend() throws Exception {
      reset();
   }


   @Override
   public byte getType() {
      return ActiveMQBytesMessage.TYPE;
   }

   private ActiveMQBuffer getBuffer() {
      return message.getBodyBuffer();
   }

   @Override
   @SuppressWarnings("unchecked")
   public boolean isBodyAssignableTo(Class c) {
      return c.isAssignableFrom(byte[].class);
   }

   @Override
   protected <T> T getBodyInternal(Class<T> c) {
      if (bodyLength == 0)
         return null;
      byte[] dst = new byte[bodyLength];
      message.getBodyBuffer().getBytes(CoreMessage.BODY_OFFSET, dst);
      return (T) dst;
   }
}
