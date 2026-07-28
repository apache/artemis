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

import java.io.IOException;
import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.UTF8Util;

public class ChannelBufferWrapper implements ActiveMQBuffer {

   protected final ByteBuf buffer;
   private final boolean releasable;
   private final boolean isPooled;
   public static ByteBuf unwrap(ByteBuf buffer) {
      ByteBuf parent;
      while ((parent = buffer.unwrap()) != null && parent != buffer) { // this last part is just in case the semantic
         // ever changes where unwrap is returning itself
         buffer = parent;
      }

      return buffer;
   }

   public ChannelBufferWrapper(final ByteBuf buffer) {
      this(buffer, false);
   }
   public ChannelBufferWrapper(final ByteBuf buffer, boolean releasable) {
      this(buffer, releasable, false);
   }
   public ChannelBufferWrapper(final ByteBuf buffer, boolean releasable, boolean pooled) {
      if (!releasable) {
         this.buffer = Unpooled.unreleasableBuffer(buffer);
      } else {
         this.buffer = buffer;
      }
      this.releasable = releasable;
      this.isPooled = pooled;

   }

   @Override
   public boolean readBoolean() {
      return readByte() != 0;
   }

   @Override
   public Boolean readNullableBoolean() {
      int b = readByte();
      if (b == DataConstants.NULL) {
         return null;
      } else {
         return readBoolean();
      }
   }

   @Override
   public SimpleString readNullableSimpleString() {
      return SimpleString.readNullableSimpleString(buffer);
   }

   @Override
   public String readNullableString() {
      int b = buffer.readByte();
      if (b == DataConstants.NULL) {
         return null;
      }
      return readStringInternal();
   }

   @Override
   public SimpleString readSimpleString() {
      return SimpleString.readSimpleString(buffer);
   }

   @Override
   public String readString() {
      return readStringInternal();
   }

   private String readStringInternal() {
      int len = buffer.readInt();

      if (len < 9) {
         char[] chars = new char[len];
         for (int i = 0; i < len; i++) {
            chars[i] = (char) buffer.readShort();
         }
         return new String(chars);
      } else if (len < 0xfff) {
         return readUTF();
      } else {
         return SimpleString.readSimpleString(buffer).toString();

      }
   }

   @Override
   public void writeNullableString(String val) {
      UTF8Util.writeNullableString(buffer, val);
   }

   @Override
   public void writeUTF(String utf) {
      UTF8Util.saveUTF(buffer, utf);
   }

   @Override
   public String readUTF() {
      return UTF8Util.readUTF(this);
   }

   @Override
   public void writeBoolean(final boolean val) {
      buffer.writeByte((byte) (val ? -1 : 0));
   }

   @Override
   public void writeNullableBoolean(Boolean val) {
      if (val == null) {
         buffer.writeByte(DataConstants.NULL);
      } else {
         buffer.writeByte(DataConstants.NOT_NULL);
         writeBoolean(val);
      }
   }

   @Override
   public void writeNullableSimpleString(final SimpleString val) {
      SimpleString.writeNullableSimpleString(buffer, val);
   }

   @Override
   public void writeSimpleString(final SimpleString val) {
      SimpleString.writeSimpleString(buffer, val);
   }

   @Override
   public void writeString(final String val) {
      UTF8Util.writeString(buffer, val);
   }

   @Override
   public int capacity() {
      return buffer.capacity();
   }

   @Override
   public ByteBuf byteBuf() {
      return buffer;
   }

   @Override
   public void clear() {
      buffer.clear();
   }

   @Override
   public ActiveMQBuffer copy() {
      return new ChannelBufferWrapper(buffer.copy(), releasable);
   }

   @Override
   public ActiveMQBuffer copy(final int index, final int length) {
      return new ChannelBufferWrapper(buffer.copy(index, length), releasable);
   }

   @Override
   public void discardReadBytes() {
      buffer.discardReadBytes();
   }

   @Override
   public ActiveMQBuffer duplicate() {
      return new ChannelBufferWrapper(buffer.duplicate(), releasable);
   }

   @Override
   public byte getByte(final int index) {
      return buffer.getByte(index);
   }

   @Override
   public void getBytes(final int index, final byte[] dst, final int dstIndex, final int length) {
      buffer.getBytes(index, dst, dstIndex, length);
   }

   @Override
   public void getBytes(final int index, final byte[] dst) {
      buffer.getBytes(index, dst);
   }

   @Override
   public void getBytes(final int index, final ByteBuffer dst) {
      buffer.getBytes(index, dst);
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst, final int dstIndex, final int length) {
      buffer.getBytes(index, dst.byteBuf(), dstIndex, length);
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst, final int length) {
      buffer.getBytes(index, dst.byteBuf(), length);
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst) {
      buffer.getBytes(index, dst.byteBuf());
   }

   @Override
   public char getChar(final int index) {
      return (char) buffer.getShort(index);
   }

   @Override
   public double getDouble(final int index) {
      return Double.longBitsToDouble(buffer.getLong(index));
   }

   @Override
   public float getFloat(final int index) {
      return Float.intBitsToFloat(buffer.getInt(index));
   }

   @Override
   public int getInt(final int index) {
      return buffer.getInt(index);
   }

   @Override
   public long getLong(final int index) {
      return buffer.getLong(index);
   }

   @Override
   public short getShort(final int index) {
      return buffer.getShort(index);
   }

   @Override
   public short getUnsignedByte(final int index) {
      return buffer.getUnsignedByte(index);
   }

   @Override
   public long getUnsignedInt(final int index) {
      return buffer.getUnsignedInt(index);
   }

   @Override
   public int getUnsignedShort(final int index) {
      return buffer.getUnsignedShort(index);
   }

   @Override
   public void markReaderIndex() {
      buffer.markReaderIndex();
   }

   @Override
   public void markWriterIndex() {
      buffer.markWriterIndex();
   }

   @Override
   public boolean readable() {
      return buffer.isReadable();
   }

   @Override
   public int readableBytes() {
      return buffer.readableBytes();
   }

   @Override
   public byte readByte() {
      return buffer.readByte();
   }

   @Override
   public void readBytes(final byte[] dst, final int dstIndex, final int length) {
      buffer.readBytes(dst, dstIndex, length);
   }

   @Override
   public void readBytes(final byte[] dst) {
      buffer.readBytes(dst);
   }

   @Override
   public void readBytes(final ByteBuffer dst) {
      buffer.readBytes(dst);
   }

   @Override
   public void readBytes(final ActiveMQBuffer dst, final int dstIndex, final int length) {
      buffer.readBytes(dst.byteBuf(), dstIndex, length);
   }

   @Override
   public void readBytes(final ActiveMQBuffer dst, final int length) {
      buffer.readBytes(dst.byteBuf(), length);
   }

   @Override
   public void readBytes(final ActiveMQBuffer dst) {
      buffer.readBytes(dst.byteBuf());
   }

   @Override
   public char readChar() {
      return (char) buffer.readShort();
   }

   @Override
   public double readDouble() {
      return Double.longBitsToDouble(buffer.readLong());
   }

   @Override
   public int readerIndex() {
      return buffer.readerIndex();
   }

   @Override
   public void readerIndex(final int readerIndex) {
      buffer.readerIndex(readerIndex);
   }

   @Override
   public float readFloat() {
      return Float.intBitsToFloat(buffer.readInt());
   }

   @Override
   public int readInt() {
      return buffer.readInt();
   }

   @Override
   public Integer readNullableInt() {
      int b = readByte();
      if (b == DataConstants.NULL) {
         return null;
      } else {
         return readInt();
      }
   }

   @Override
   public long readLong() {
      return buffer.readLong();
   }

   @Override
   public Long readNullableLong() {
      int b = readByte();
      if (b == DataConstants.NULL) {
         return null;
      } else {
         return readLong();
      }
   }

   @Override
   public short readShort() {
      return buffer.readShort();
   }

   @Override
   public ActiveMQBuffer readSlice(final int length) {
      if (isPooled) {
         ByteBuf fromBuffer = buffer.readSlice(length);
         ByteBuf newNettyBuffer = Unpooled.buffer(fromBuffer.capacity());
         int read = fromBuffer.readerIndex();
         int writ = fromBuffer.writerIndex();
         fromBuffer.readerIndex(0);
         fromBuffer.readBytes(newNettyBuffer, 0, writ);
         newNettyBuffer.setIndex(read, writ);
         ActiveMQBuffer returnBuffer = new ChannelBufferWrapper(newNettyBuffer, releasable, false);
         returnBuffer.setIndex(read, writ);
         return returnBuffer;
      }
      return new ChannelBufferWrapper(buffer.readSlice(length), releasable, isPooled);
   }

   @Override
   public int readUnsignedByte() {
      return buffer.readUnsignedByte();
   }

   @Override
   public long readUnsignedInt() {
      return buffer.readUnsignedInt();
   }

   @Override
   public int readUnsignedShort() {
      return buffer.readUnsignedShort();
   }

   @Override
   public void resetReaderIndex() {
      buffer.resetReaderIndex();
   }

   @Override
   public void resetWriterIndex() {
      buffer.resetWriterIndex();
   }

   @Override
   public void setByte(final int index, final byte value) {
      buffer.setByte(index, value);
   }

   @Override
   public void setBytes(final int index, final byte[] src, final int srcIndex, final int length) {
      buffer.setBytes(index, src, srcIndex, length);
   }

   @Override
   public void setBytes(final int index, final byte[] src) {
      buffer.setBytes(index, src);
   }

   @Override
   public void setBytes(final int index, final ByteBuffer src) {
      buffer.setBytes(index, src);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int srcIndex, final int length) {
      buffer.setBytes(index, src.byteBuf(), srcIndex, length);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int length) {
      buffer.setBytes(index, src.byteBuf(), length);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src) {
      buffer.setBytes(index, src.byteBuf());
   }

   @Override
   public void setChar(final int index, final char value) {
      buffer.setShort(index, (short) value);
   }

   @Override
   public void setDouble(final int index, final double value) {
      buffer.setLong(index, Double.doubleToLongBits(value));
   }

   @Override
   public void setFloat(final int index, final float value) {
      buffer.setInt(index, Float.floatToIntBits(value));
   }

   @Override
   public void setIndex(final int readerIndex, final int writerIndex) {
      buffer.setIndex(readerIndex, writerIndex);
   }

   @Override
   public void setInt(final int index, final int value) {
      buffer.setInt(index, value);
   }

   @Override
   public void setLong(final int index, final long value) {
      buffer.setLong(index, value);
   }

   @Override
   public void setShort(final int index, final short value) {
      buffer.setShort(index, value);
   }

   @Override
   public int skipBytes(final int length) {
      buffer.skipBytes(length);
      return length;
   }

   @Override
   public ActiveMQBuffer slice() {
      return new ChannelBufferWrapper(buffer.slice(), releasable);
   }

   @Override
   public ActiveMQBuffer slice(final int index, final int length) {
      return new ChannelBufferWrapper(buffer.slice(index, length), releasable);
   }

   @Override
   public ByteBuffer toByteBuffer() {
      return buffer.nioBuffer();
   }

   @Override
   public ByteBuffer toByteBuffer(final int index, final int length) {
      return buffer.nioBuffer(index, length);
   }

   @Override
   public void release() {
      if (this.isPooled) {
         buffer.release();
      }
   }

   @Override
   public boolean writable() {
      return buffer.isWritable();
   }

   @Override
   public int writableBytes() {
      return buffer.writableBytes();
   }

   @Override
   public void writeByte(final byte value) {
      buffer.writeByte(value);
   }

   @Override
   public void writeBytes(final byte[] src, final int srcIndex, final int length) {
      buffer.writeBytes(src, srcIndex, length);
   }

   @Override
   public void writeBytes(final byte[] src) {
      buffer.writeBytes(src);
   }

   @Override
   public void writeBytes(final ByteBuffer src) {
      buffer.writeBytes(src);
   }

   @Override
   public void writeBytes(ByteBuf src, int srcIndex, int length) {
      buffer.writeBytes(src, srcIndex, length);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int srcIndex, final int length) {
      buffer.writeBytes(src.byteBuf(), srcIndex, length);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int length) {
      buffer.writeBytes(src.byteBuf(), length);
   }

   @Override
   public void writeChar(final char chr) {
      buffer.writeShort((short) chr);
   }

   @Override
   public void writeDouble(final double value) {
      buffer.writeLong(Double.doubleToLongBits(value));
   }

   @Override
   public void writeFloat(final float value) {
      buffer.writeInt(Float.floatToIntBits(value));
   }

   @Override
   public void writeInt(final int value) {
      buffer.writeInt(value);
   }

   @Override
   public void writeNullableInt(final Integer value) {
      if (value == null) {
         buffer.writeByte(DataConstants.NULL);
      } else {
         buffer.writeByte(DataConstants.NOT_NULL);
         writeInt(value);
      }
   }

   @Override
   public void writeLong(final long value) {
      buffer.writeLong(value);
   }

   @Override
   public void writeNullableLong(Long value) {
      if (value == null) {
         buffer.writeByte(DataConstants.NULL);
      } else {
         buffer.writeByte(DataConstants.NOT_NULL);
         writeLong(value);
      }
   }

   @Override
   public int writerIndex() {
      return buffer.writerIndex();
   }

   @Override
   public void writerIndex(final int writerIndex) {
      buffer.writerIndex(writerIndex);
   }

   @Override
   public void writeShort(final short value) {
      buffer.writeShort(value);
   }

   @Override
   public void writeUnsignedShort(final int value) {
      if (value < 0 || value > 65535) {
         throw new IllegalArgumentException("Value must be in range 0-65535, got: " + value);
      }
      buffer.writeShort((short) value);
   }


   /**
    * from {@link java.io.DataInput} interface
    */
   @Override
   public void readFully(byte[] b) throws IOException {
      readBytes(b);
   }

   /**
    * from {@link java.io.DataInput} interface
    */
   @Override
   public void readFully(byte[] b, int off, int len) throws IOException {
      readBytes(b, off, len);
   }

   /**
    * from {@link java.io.DataInput} interface
    */
   @Override
   public String readLine() throws IOException {
      return ByteUtil.readLine(this);
   }

}
