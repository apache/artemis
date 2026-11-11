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
package org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_SEQUENCE;
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

public final class CoreStreamMessageWrapper extends CoreMessageWrapper {

   public static final byte TYPE = Message.STREAM_TYPE;

   private int bodyLength = 0;
   /**
    * len here is used to control how many more bytes to read
    */
   private int len = 0;

   public CoreStreamMessageWrapper(ICoreMessage message) {
      super(message);
   }

   @Override
   public Section createAMQPSection(Map<Symbol, Object> maMap, Properties properties) throws ConversionException {
      maMap.put(AMQPMessageSupport.JMS_MSG_TYPE, AMQPMessageSupport.JMS_STREAM_MESSAGE);
      List<Object> list = new ArrayList<>();
      try {
         while (true) {
            list.add(readObject());
         }
      } catch (MessageEOFException e) {
      }

      return switch (getOrignalEncoding()) {
         case AMQP_SEQUENCE -> new AmqpSequence(list);
         default -> new AmqpValue(list);
      };
   }

   public boolean readBoolean() throws MessageEOFException, ConversionException {

      try {
         return streamReadBoolean(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public byte readByte() throws MessageEOFException, ConversionException {
      try {
         return streamReadByte(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public short readShort() throws MessageEOFException, ConversionException {

      try {
         return streamReadShort(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public char readChar() throws MessageEOFException, ConversionException {

      try {
         return streamReadChar(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public int readInt() throws MessageEOFException, ConversionException {

      try {
         return streamReadInteger(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public long readLong() throws ConversionException, MessageEOFException {

      try {
         return streamReadLong(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public float readFloat() throws ConversionException, MessageEOFException {

      try {
         return streamReadFloat(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public double readDouble() throws ConversionException, MessageEOFException {

      try {
         return streamReadDouble(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public String readString() throws ConversionException, MessageEOFException {

      try {
         return streamReadString(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public int readBytes(final byte[] value) throws ConversionException, MessageEOFException {

      try {
         Pair<Integer, Integer> pairRead = streamReadBytes(getReadBodyBuffer(), len, value);

         len = pairRead.getA();
         return pairRead.getB();
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public Object readObject() throws ConversionException, MessageEOFException {

      if (getReadBodyBuffer().readerIndex() >= getReadBodyBuffer().writerIndex()) {
         throw new MessageEOFException();
      }
      try {
         return streamReadObject(getReadBodyBuffer());
      } catch (IllegalStateException e) {
         throw new ConversionException(e.getMessage(), e);
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException();
      }
   }

   public void writeBoolean(final boolean value) {
      getWriteBodyBuffer().writeByte(DataConstants.BOOLEAN);
      getWriteBodyBuffer().writeBoolean(value);
   }

   public void writeByte(final byte value) {

      getWriteBodyBuffer().writeByte(DataConstants.BYTE);
      getWriteBodyBuffer().writeByte(value);
   }

   public void writeShort(final short value) {

      getWriteBodyBuffer().writeByte(DataConstants.SHORT);
      getWriteBodyBuffer().writeShort(value);
   }

   public void writeChar(final char value) {

      getWriteBodyBuffer().writeByte(DataConstants.CHAR);
      getWriteBodyBuffer().writeShort((short) value);
   }

   public void writeInt(final int value) {

      getWriteBodyBuffer().writeByte(DataConstants.INT);
      getWriteBodyBuffer().writeInt(value);
   }

   public void writeLong(final long value) {

      getWriteBodyBuffer().writeByte(DataConstants.LONG);
      getWriteBodyBuffer().writeLong(value);
   }

   public void writeFloat(final float value) {

      getWriteBodyBuffer().writeByte(DataConstants.FLOAT);
      getWriteBodyBuffer().writeInt(Float.floatToIntBits(value));
   }

   public void writeDouble(final double value) {

      getWriteBodyBuffer().writeByte(DataConstants.DOUBLE);
      getWriteBodyBuffer().writeLong(Double.doubleToLongBits(value));
   }

   public void writeString(final String value) {

      getWriteBodyBuffer().writeByte(DataConstants.STRING);
      getWriteBodyBuffer().writeNullableString(value);
   }

   public void writeBytes(final byte[] value) {

      getWriteBodyBuffer().writeByte(DataConstants.BYTES);
      getWriteBodyBuffer().writeInt(value.length);
      getWriteBodyBuffer().writeBytes(value);
   }

   public void writeBytes(final byte[] value, final int offset, final int length) {

      getWriteBodyBuffer().writeByte(DataConstants.BYTES);
      getWriteBodyBuffer().writeInt(length);
      getWriteBodyBuffer().writeBytes(value, offset, length);
   }

   public void writeObject(final Object value) throws ConversionException {
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
         throw new ConversionException("Invalid object type: " + value.getClass());
      }
   }

   public void reset() {
      getWriteBodyBuffer().resetReaderIndex();
   }

   @Override
   public void clearBody() {
      super.clearBody();

      getWriteBodyBuffer().clear();
   }

   @Override
   public void decode() {
      super.decode();
   }

   @Override
   public void encode() {
      super.encode();
      bodyLength = message.getEndOfBodyPosition();
   }

}
