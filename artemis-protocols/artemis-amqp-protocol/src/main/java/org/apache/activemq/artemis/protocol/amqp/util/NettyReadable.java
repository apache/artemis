/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;

/**
 * {@link ReadableBuffer} implementation that wraps a Netty {@link ByteBuf} to allow use of Netty buffers to be used
 * when decoding AMQP messages.
 */
public class NettyReadable implements ReadableBuffer {

   private final ByteBuf buffer;

   public NettyReadable(ByteBuf buffer) {
      this.buffer = Objects.requireNonNull(buffer);
   }

   public ByteBuf getByteBuf() {
      return this.buffer;
   }

   @Override
   public byte get() {
      return buffer.readByte();
   }

   @Override
   public int getInt() {
      return buffer.readInt();
   }

   @Override
   public long getLong() {
      return buffer.readLong();
   }

   @Override
   public short getShort() {
      return buffer.readShort();
   }

   @Override
   public float getFloat() {
      return buffer.readFloat();
   }

   @Override
   public double getDouble() {
      return buffer.readDouble();
   }

   @Override
   public ReadableBuffer get(byte[] data, int offset, int length) {
      buffer.readBytes(data, offset, length);
      return this;
   }

   @Override
   public ReadableBuffer get(byte[] data) {
      buffer.readBytes(data);
      return this;
   }

   @Override
   public ReadableBuffer position(int position) {
      buffer.readerIndex(position);
      return this;
   }

   @Override
   public ReadableBuffer slice() {
      return new NettyReadable(buffer.slice());
   }

   @Override
   public ReadableBuffer flip() {
      buffer.setIndex(0, buffer.readerIndex());
      return this;
   }

   @Override
   public ReadableBuffer limit(int limit) {
      buffer.writerIndex(limit);
      return this;
   }

   @Override
   public int limit() {
      return buffer.writerIndex();
   }

   @Override
   public int remaining() {
      return buffer.readableBytes();
   }

   @Override
   public int position() {
      return buffer.readerIndex();
   }

   @Override
   public boolean hasRemaining() {
      return buffer.readableBytes() > 0;
   }

   @Override
   public ReadableBuffer duplicate() {
      return new NettyReadable(buffer.duplicate());
   }

   @Override
   public ByteBuffer byteBuffer() {
      return buffer.nioBuffer(0, buffer.writerIndex());
   }

   @Override
   public String readUTF8() {
      return buffer.readCharSequence(buffer.readableBytes(), StandardCharsets.UTF_8).toString();
   }

   @Override
   public byte[] array() {
      return buffer.array();
   }

   @Override
   public int arrayOffset() {
      return buffer.arrayOffset();
   }

   @Override
   public int capacity() {
      return buffer.capacity();
   }

   @Override
   public ReadableBuffer clear() {
      buffer.setIndex(0, buffer.capacity());
      return this;
   }

   @Override
   public ReadableBuffer reclaimRead() {
      return this;
   }

   @Override
   public byte get(int index) {
      return buffer.getByte(index);
   }

   @Override
   public boolean hasArray() {
      return buffer.hasArray();
   }

   @Override
   public ReadableBuffer mark() {
      buffer.markReaderIndex();
      return this;
   }

   @Override
   public String readString(CharsetDecoder decoder) throws CharacterCodingException {
      return buffer.readCharSequence(buffer.readableBytes(), decoder.charset()).toString();
   }

   @Override
   public ReadableBuffer reset() {
      buffer.resetReaderIndex();
      return this;
   }

   @Override
   public ReadableBuffer rewind() {
      buffer.setIndex(0, buffer.writerIndex());
      return this;
   }

   @Override
   public ReadableBuffer get(WritableBuffer target) {
      int start = target.position();

      if (buffer.hasArray()) {
         target.put(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(), buffer.readableBytes());
      } else {
         target.put(buffer.nioBuffer());
      }

      int written = target.position() - start;

      buffer.readerIndex(buffer.readerIndex() + written);

      return this;
   }

   @Override
   public String toString() {
      return buffer.toString();
   }

   @Override
   public int hashCode() {
      return buffer.hashCode();
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      }

      if (!(other instanceof ReadableBuffer readable)) {
         return false;
      }

      if (this.remaining() != readable.remaining()) {
         return false;
      }

      return buffer.nioBuffer().equals(readable.byteBuffer());
   }
}
