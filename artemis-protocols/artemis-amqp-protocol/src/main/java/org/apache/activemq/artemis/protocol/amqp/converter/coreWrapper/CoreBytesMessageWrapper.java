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

import java.util.Map;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_NULL;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_BINARY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.EMPTY_BINARY;
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

public class CoreBytesMessageWrapper extends CoreMessageWrapper {


   public CoreBytesMessageWrapper(ICoreMessage message) {
      super(message);
   }

   protected static Binary getBinaryFromMessageBody(CoreBytesMessageWrapper message) {
      byte[] data = new byte[(int) message.getBodyLength()];
      message.readBytes(data);
      message.reset(); // Need to reset after readBytes or future readBytes

      return new Binary(data);
   }

   @Override
   public Section createAMQPSection(Map<Symbol, Object> maMap, Properties properties) {
      short orignalEncoding = getOrignalEncoding();
      Binary payload = getBinaryFromMessageBody(this);

      maMap.put(AMQPMessageSupport.JMS_MSG_TYPE, AMQPMessageSupport.JMS_BYTES_MESSAGE);
      if (payload == null) {
         payload = EMPTY_BINARY;
      }

      return switch (orignalEncoding) {
         case AMQP_NULL -> null;
         case AMQP_VALUE_BINARY -> new AmqpValue(payload);
         default -> new Data(payload);
      };
   }

   public long getBodyLength() {
      return message.getBodyBufferSize();
   }

   public boolean readBoolean() {
      return bytesReadBoolean(getReadBodyBuffer());
   }

   public byte readByte() {
      return bytesReadByte(getReadBodyBuffer());
   }

   public int readUnsignedByte() {
      return bytesReadUnsignedByte(getReadBodyBuffer());
   }

   public short readShort() {
      return bytesReadShort(getReadBodyBuffer());
   }

   public int readUnsignedShort() {
      return bytesReadUnsignedShort(getReadBodyBuffer());
   }

   public char readChar() {
      return bytesReadChar(getReadBodyBuffer());
   }

   public int readInt() {
      return bytesReadInt(getReadBodyBuffer());
   }

   public long readLong() {
      return bytesReadLong(getReadBodyBuffer());
   }

   public float readFloat() {
      return bytesReadFloat(getReadBodyBuffer());
   }

   public double readDouble() {
      return bytesReadDouble(getReadBodyBuffer());
   }

   public String readUTF() {
      return bytesReadUTF(getReadBodyBuffer());
   }

   public int readBytes(byte[] value) {
      return bytesReadBytes(getReadBodyBuffer(), value);
   }

   public int readBytes(byte[] value, int length) {
      return bytesReadBytes(getReadBodyBuffer(), value, length);
   }

   public void writeBoolean(boolean value) {
      bytesWriteBoolean(getWriteBodyBuffer(), value);

   }

   public void writeByte(byte value) {
      bytesWriteByte(getWriteBodyBuffer(), value);
   }

   public void writeShort(short value) {
      bytesWriteShort(getWriteBodyBuffer(), value);
   }

   public void writeChar(char value) {
      bytesWriteChar(getWriteBodyBuffer(), value);
   }

   public void writeInt(int value) {
      bytesWriteInt(getWriteBodyBuffer(), value);
   }

   public void writeLong(long value) {
      bytesWriteLong(getWriteBodyBuffer(), value);
   }

   public void writeFloat(float value) {
      bytesWriteFloat(getWriteBodyBuffer(), value);
   }

   public void writeDouble(double value) {
      bytesWriteDouble(getWriteBodyBuffer(), value);
   }

   public void writeUTF(String value) {
      bytesWriteUTF(getWriteBodyBuffer(), value);
   }

   public void writeBytes(byte[] value) {
      bytesWriteBytes(getWriteBodyBuffer(), value);
   }

   public void writeBytes(byte[] value, int offset, int length) {
      bytesWriteBytes(getWriteBodyBuffer(), value, offset, length);
   }

   public void writeObject(Object value) throws ConversionException {
      if (!bytesWriteObject(getWriteBodyBuffer(), value)) {
         throw new ConversionException("Can't make conversion of " + value + " to any known type");
      }
   }

   @Override
   public void encode() {
      super.encode();
      // this is to make sure we encode the body-length before it's persisted
      getBodyLength();
   }

   @Override
   public void decode() {
      super.decode();

   }

   public void reset() {
      if (!message.isLargeMessage()) {
         bytesMessageReset(getReadBodyBuffer());
         bytesMessageReset(getWriteBodyBuffer());
      }
   }

}
