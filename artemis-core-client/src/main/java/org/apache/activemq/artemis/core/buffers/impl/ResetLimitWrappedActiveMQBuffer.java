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
package org.apache.activemq.artemis.core.buffers.impl;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * A ResetLimitWrappedActiveMQBuffer
 */
public final class ResetLimitWrappedActiveMQBuffer extends ChannelBufferWrapper {

   private final int limit;

   private Message message;

   /**
    * We need to turn of notifications of body changes on reset on the server side when dealing with AMQP conversions,
    * for that reason this method will set the message to null here
    */
   public void setMessage(Message message) {
      this.message = message;
   }

   public ResetLimitWrappedActiveMQBuffer(final int limit, final ActiveMQBuffer buffer, final Message message) {
      // a wrapped inside a wrapper will increase the stack size.
      // we fixed this here due to some profiling testing
      this(limit, unwrap(buffer.byteBuf()).duplicate(), message);
   }

   public ResetLimitWrappedActiveMQBuffer(final int limit, final ByteBuf buffer, final Message message) {
      // a wrapped inside a wrapper will increase the stack size.
      // we fixed this here due to some profiling testing
      super(buffer);

      this.limit = limit;

      if (writerIndex() < limit) {
         writerIndex(limit);
      }

      readerIndex(limit);

      this.message = message;
   }

   private void changed() {
      if (message != null) {
         message.messageChanged();
      }
   }

   @Override
   public void clear() {
      changed();

      buffer.clear();

      buffer.setIndex(limit, limit);

   }

   @Override
   public void readerIndex(int readerIndex) {
      changed();

      if (readerIndex < limit) {
         readerIndex = limit;
      }

      buffer.readerIndex(readerIndex);
   }

   @Override
   public void resetReaderIndex() {
      buffer.readerIndex(limit);
   }

   @Override
   public void resetWriterIndex() {
      changed();

      buffer.writerIndex(limit);
   }

   @Override
   public void setIndex(int readerIndex, int writerIndex) {
      changed();

      if (readerIndex < limit) {
         readerIndex = limit;
      }
      if (writerIndex < limit) {
         writerIndex = limit;
      }
      buffer.setIndex(readerIndex, writerIndex);
   }

   @Override
   public void writerIndex(int writerIndex) {
      changed();

      if (writerIndex < limit) {
         writerIndex = limit;
      }

      buffer.writerIndex(writerIndex);
   }

   @Override
   public void setByte(final int index, final byte value) {
      changed();

      super.setByte(index, value);
   }

   @Override
   public void setBytes(final int index, final byte[] src, final int srcIndex, final int length) {
      changed();

      super.setBytes(index, src, srcIndex, length);
   }

   @Override
   public void setBytes(final int index, final byte[] src) {
      changed();

      super.setBytes(index, src);
   }

   @Override
   public void setBytes(final int index, final ByteBuffer src) {
      changed();

      super.setBytes(index, src);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int srcIndex, final int length) {
      changed();

      super.setBytes(index, src, srcIndex, length);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int length) {
      changed();

      super.setBytes(index, src, length);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src) {
      changed();

      super.setBytes(index, src);
   }

   @Override
   public void setChar(final int index, final char value) {
      changed();

      super.setChar(index, value);
   }

   @Override
   public void setDouble(final int index, final double value) {
      changed();

      super.setDouble(index, value);
   }

   @Override
   public void setFloat(final int index, final float value) {
      changed();

      super.setFloat(index, value);
   }

   @Override
   public void setInt(final int index, final int value) {
      changed();

      super.setInt(index, value);
   }

   @Override
   public void setLong(final int index, final long value) {
      changed();

      super.setLong(index, value);
   }

   @Override
   public void setShort(final int index, final short value) {
      changed();

      super.setShort(index, value);
   }

   @Override
   public void writeBoolean(final boolean val) {
      changed();

      super.writeBoolean(val);
   }

   @Override
   public void writeNullableBoolean(final Boolean val) {
      changed();

      super.writeNullableBoolean(val);
   }

   @Override
   public void writeByte(final byte value) {
      changed();

      super.writeByte(value);
   }

   @Override
   public void writeBytes(final byte[] src, final int srcIndex, final int length) {
      changed();

      super.writeBytes(src, srcIndex, length);
   }

   @Override
   public void writeBytes(final byte[] src) {
      changed();

      super.writeBytes(src);
   }

   @Override
   public void writeBytes(final ByteBuffer src) {
      changed();

      super.writeBytes(src);
   }


   @Override
   public void writeBytes(final ByteBuf src, final int srcIndex, final int length) {
      changed();

      super.writeBytes(src, srcIndex, length);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int srcIndex, final int length) {
      changed();

      super.writeBytes(src, srcIndex, length);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int length) {
      changed();

      super.writeBytes(src, length);
   }

   @Override
   public void writeChar(final char chr) {
      changed();

      super.writeChar(chr);
   }

   @Override
   public void writeDouble(final double value) {
      changed();

      super.writeDouble(value);
   }

   @Override
   public void writeFloat(final float value) {
      changed();

      super.writeFloat(value);
   }

   @Override
   public void writeInt(final int value) {
      changed();

      super.writeInt(value);
   }

   @Override
   public void writeNullableInt(final Integer value) {
      changed();

      super.writeNullableInt(value);
   }

   @Override
   public void writeLong(final long value) {
      changed();

      super.writeLong(value);
   }

   @Override
   public void writeNullableLong(final Long value) {
      changed();

      super.writeNullableLong(value);
   }

   @Override
   public void writeNullableSimpleString(final SimpleString val) {
      changed();

      super.writeNullableSimpleString(val);
   }

   @Override
   public void writeNullableString(final String val) {
      changed();

      super.writeNullableString(val);
   }

   @Override
   public void writeShort(final short value) {
      changed();

      super.writeShort(value);
   }

   @Override
   public void writeSimpleString(final SimpleString val) {
      changed();

      super.writeSimpleString(val);
   }

   @Override
   public void writeString(final String val) {
      changed();

      super.writeString(val);
   }

   @Override
   public void writeUTF(final String utf) {
      changed();

      super.writeUTF(utf);
   }
}
