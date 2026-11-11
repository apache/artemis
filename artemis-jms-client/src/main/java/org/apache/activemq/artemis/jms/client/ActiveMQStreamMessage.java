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
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.StreamMessage;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.utils.DataConstants;

import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadBoolean;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadByte;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadBytes;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadChar;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadDouble;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadFloat;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadInteger;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadLong;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadObject;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadShort;
import static org.apache.activemq.artemis.reader.StreamMessageUtil.streamReadString;

/**
 * Apache Artemis implementation of a JMS StreamMessage.
 */
public class ActiveMQStreamMessage extends ActiveMQMessage implements StreamMessage {

   public static final byte TYPE = Message.STREAM_TYPE;

   protected ActiveMQStreamMessage(final ClientSession session) {
      super(ActiveMQStreamMessage.TYPE, session);
   }

   protected ActiveMQStreamMessage(final ClientMessage message, final ClientSession session) {
      super(message, session);
   }

   public ActiveMQStreamMessage(final StreamMessage foreign, final ClientSession session) throws JMSException {
      super(foreign, ActiveMQStreamMessage.TYPE, session);

      foreign.reset();

      try {
         while (true) {
            Object obj = foreign.readObject();
            writeObject(obj);
         }
      } catch (MessageEOFException e) {
         // Ignore
      }
   }

   // For testing only
   public ActiveMQStreamMessage() {
      message = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1500, null);
   }


   @Override
   public byte getType() {
      return ActiveMQStreamMessage.TYPE;
   }

   // StreamMessage implementation ----------------------------------

   @Override
   public boolean readBoolean() throws JMSException {
      checkRead();
      try {
         return streamReadBoolean(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public byte readByte() throws JMSException {
      checkRead();

      try {
         return streamReadByte(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public short readShort() throws JMSException {
      checkRead();
      try {
         return streamReadShort(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public char readChar() throws JMSException {
      checkRead();
      try {
         return streamReadChar(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public int readInt() throws JMSException {
      checkRead();
      try {
         return streamReadInteger(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public long readLong() throws JMSException {
      checkRead();
      try {
         return streamReadLong(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public float readFloat() throws JMSException {
      checkRead();
      try {
         return streamReadFloat(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public double readDouble() throws JMSException {
      checkRead();
      try {
         return streamReadDouble(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public String readString() throws JMSException {
      checkRead();
      try {
         return streamReadString(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   /**
    * len here is used to control how many more bytes to read
    */
   private int len = 0;

   @Override
   public int readBytes(final byte[] value) throws JMSException {
      checkRead();
      try {
         Pair<Integer, Integer> pairRead = streamReadBytes(message.getBodyBuffer(), len, value);

         len = pairRead.getA();
         return pairRead.getB();
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public Object readObject() throws JMSException {
      checkRead();
      try {
         return streamReadObject(message.getBodyBuffer());
      } catch (IllegalStateException e) {
         throw new MessageFormatException(e.getMessage());
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public void writeBoolean(final boolean value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.BOOLEAN);
      getBuffer().writeBoolean(value);
   }

   @Override
   public void writeByte(final byte value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.BYTE);
      getBuffer().writeByte(value);
   }

   @Override
   public void writeShort(final short value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.SHORT);
      getBuffer().writeShort(value);
   }

   @Override
   public void writeChar(final char value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.CHAR);
      getBuffer().writeShort((short) value);
   }

   @Override
   public void writeInt(final int value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.INT);
      getBuffer().writeInt(value);
   }

   @Override
   public void writeLong(final long value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.LONG);
      getBuffer().writeLong(value);
   }

   @Override
   public void writeFloat(final float value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.FLOAT);
      getBuffer().writeInt(Float.floatToIntBits(value));
   }

   @Override
   public void writeDouble(final double value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.DOUBLE);
      getBuffer().writeLong(Double.doubleToLongBits(value));
   }

   @Override
   public void writeString(final String value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.STRING);
      getBuffer().writeNullableString(value);
   }

   @Override
   public void writeBytes(final byte[] value) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.BYTES);
      getBuffer().writeInt(value.length);
      getBuffer().writeBytes(value);
   }

   @Override
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException {
      checkWrite();
      getBuffer().writeByte(DataConstants.BYTES);
      getBuffer().writeInt(length);
      getBuffer().writeBytes(value, offset, length);
   }

   @Override
   public void writeObject(final Object value) throws JMSException {
      if (value instanceof String string) {
         writeString(string);
      } else if (value instanceof Boolean booleanValue) {
         writeBoolean(booleanValue);
      } else if (value instanceof Byte byteValue) {
         writeByte(byteValue);
      } else if (value instanceof Short shortValue) {
         writeShort(shortValue);
      } else if (value instanceof Integer integer) {
         writeInt(integer);
      } else if (value instanceof Long longValue) {
         writeLong(longValue);
      } else if (value instanceof Float floatValue) {
         writeFloat(floatValue);
      } else if (value instanceof Double doubleValue) {
         writeDouble(doubleValue);
      } else if (value instanceof byte[] bytes) {
         writeBytes(bytes);
      } else if (value instanceof Character character) {
         writeChar(character);
      } else if (value == null) {
         writeString(null);
      } else {
         throw new MessageFormatException("Invalid object type: " + value.getClass());
      }
   }

   @Override
   public void reset() throws JMSException {
      if (!readOnly) {
         readOnly = true;
      }
      getBuffer().resetReaderIndex();
   }

   @Override
   public void clearBody() throws JMSException {
      super.clearBody();

      getBuffer().clear();
   }

   @Override
   public void doBeforeSend() throws Exception {
      reset();
   }

   private ActiveMQBuffer getBuffer() {
      return message.getBodyBuffer();
   }

   @Override
   public boolean isBodyAssignableTo(Class c) {
      return false;
   }
}
