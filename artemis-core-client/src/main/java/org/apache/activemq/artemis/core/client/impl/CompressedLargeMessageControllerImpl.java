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
package org.apache.activemq.artemis.core.client.impl;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.InflaterReader;
import org.apache.activemq.artemis.utils.InflaterWriter;
import org.apache.activemq.artemis.utils.UTF8Util;

final class CompressedLargeMessageControllerImpl implements LargeMessageController {

   private static final String OPERATION_NOT_SUPPORTED = "Operation not supported";

   private final LargeMessageController bufferDelegate;

   CompressedLargeMessageControllerImpl(final LargeMessageController bufferDelegate) {
      this.bufferDelegate = bufferDelegate;
   }

   /**
    *
    */
   @Override
   public void discardUnusedPackets() {
      bufferDelegate.discardUnusedPackets();
   }

   /**
    * Add a buff to the List, or save it to the OutputStream if set
    */
   @Override
   public void addPacket(byte[] chunk, int flowControlSize, boolean isContinues) {
      bufferDelegate.addPacket(chunk, flowControlSize, isContinues);
   }

   @Override
   public synchronized void cancel() {
      bufferDelegate.cancel();
   }

   @Override
   public synchronized void close() {
      bufferDelegate.cancel();
   }

   @Override
   public void setOutputStream(final OutputStream output) throws ActiveMQException {
      bufferDelegate.setOutputStream(new InflaterWriter(output));
   }

   @Override
   public synchronized void saveBuffer(final OutputStream output) throws ActiveMQException {
      setOutputStream(output);
      waitCompletion(0);
   }

   /**
    * @param timeWait Milliseconds to Wait. 0 means forever
    */
   @Override
   public synchronized boolean waitCompletion(final long timeWait) throws ActiveMQException {
      return bufferDelegate.waitCompletion(timeWait);
   }

   @Override
   public LargeMessageControllerImpl.LargeData take() throws InterruptedException {
      return bufferDelegate.take();
   }

   @Override
   public int capacity() {
      return -1;
   }

   DataInputStream dataInput = null;

   private DataInputStream getStream() {
      if (dataInput == null) {
         try {
            InputStream input = new ActiveMQBufferInputStream(bufferDelegate);

            dataInput = new DataInputStream(new InflaterReader(input));
         } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
         }

      }
      return dataInput;
   }

   private void positioningNotSupported() {
      throw new IllegalStateException("Position not supported over compressed large messages");
   }

   @Override
   public byte readByte() {
      try {
         return getStream().readByte();
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public byte getByte(final int index) {
      positioningNotSupported();
      return 0;
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst, final int dstIndex, final int length) {
      positioningNotSupported();
   }

   @Override
   public void getBytes(final int index, final byte[] dst, final int dstIndex, final int length) {
      positioningNotSupported();
   }

   @Override
   public void getBytes(final int index, final ByteBuffer dst) {
      positioningNotSupported();
   }

   @Override
   public int getInt(final int index) {
      positioningNotSupported();
      return 0;
   }

   @Override
   public long getLong(final int index) {
      positioningNotSupported();
      return 0;
   }

   @Override
   public short getShort(final int index) {
      positioningNotSupported();
      return 0;
   }

   @Override
   public void setByte(final int index, final byte value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int srcIndex, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setBytes(final int index, final byte[] src, final int srcIndex, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setBytes(final int index, final ByteBuffer src) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setInt(final int index, final int value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setLong(final int index, final long value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setShort(final int index, final short value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public ByteBuffer toByteBuffer(final int index, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void release() {
      //no-op
   }

   @Override
   public int readerIndex() {
      return 0;
   }

   @Override
   public void readerIndex(final int readerIndex) {
      // TODO
   }

   @Override
   public int writerIndex() {
      // TODO
      return 0;
   }

   @Override
   public long getSize() {
      return this.bufferDelegate.getSize();
   }

   @Override
   public void writerIndex(final int writerIndex) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setIndex(final int readerIndex, final int writerIndex) {
      positioningNotSupported();
   }

   @Override
   public void clear() {
   }

   @Override
   public boolean readable() {
      return true;
   }

   @Override
   public boolean writable() {
      return false;
   }

   @Override
   public int readableBytes() {
      return 1;
   }

   @Override
   public int writableBytes() {
      return 0;
   }

   @Override
   public void markReaderIndex() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void resetReaderIndex() {
      // TODO: reset positioning if possible
   }

   @Override
   public void markWriterIndex() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void resetWriterIndex() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void discardReadBytes() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public short getUnsignedByte(final int index) {
      return (short) (getByte(index) & 0xFF);
   }

   @Override
   public int getUnsignedShort(final int index) {
      return getShort(index) & 0xFFFF;
   }

   @Override
   public long getUnsignedInt(final int index) {
      return getInt(index) & 0xFFFFFFFFL;
   }

   @Override
   public void getBytes(int index, final byte[] dst) {
      // TODO: optimize this by using System.arraycopy
      for (int i = 0; i < dst.length; i++) {
         dst[i] = getByte(index++);
      }
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst) {
      getBytes(index, dst, dst.writableBytes());
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst, final int length) {
      if (length > dst.writableBytes()) {
         throw new IndexOutOfBoundsException();
      }
      getBytes(index, dst, dst.writerIndex(), length);
      dst.writerIndex(dst.writerIndex() + length);
   }

   @Override
   public void setBytes(final int index, final byte[] src) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public int readUnsignedByte() {
      try {
         return getStream().readUnsignedByte();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   @Override
   public short readShort() {
      try {
         return getStream().readShort();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   @Override
   public int readUnsignedShort() {
      try {
         return getStream().readUnsignedShort();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   @Override
   public int readInt() {
      try {
         return getStream().readInt();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
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
   public long readUnsignedInt() {
      return readInt() & 0xFFFFFFFFL;
   }

   @Override
   public long readLong() {
      try {
         return getStream().readLong();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
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
   public void readBytes(final byte[] dst, final int dstIndex, final int length) {
      try {
         int nReadBytes = getStream().read(dst, dstIndex, length);
         if (nReadBytes < length) {
            ActiveMQClientLogger.LOGGER.compressedLargeMessageError(length, nReadBytes);
         }
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   @Override
   public void readBytes(final byte[] dst) {
      readBytes(dst, 0, dst.length);
   }

   @Override
   public void readBytes(final ActiveMQBuffer dst) {
      readBytes(dst, dst.writableBytes());
   }

   @Override
   public void readBytes(final ActiveMQBuffer dst, final int length) {
      if (length > dst.writableBytes()) {
         throw new IndexOutOfBoundsException();
      }
      readBytes(dst, dst.writerIndex(), length);
      dst.writerIndex(dst.writerIndex() + length);
   }

   @Override
   public void readBytes(final ActiveMQBuffer dst, final int dstIndex, final int length) {
      byte[] destBytes = new byte[length];
      readBytes(destBytes);
      dst.setBytes(dstIndex, destBytes);
   }

   @Override
   public void readBytes(final ByteBuffer dst) {
      byte[] bytesToGet = new byte[dst.remaining()];
      readBytes(bytesToGet);
      dst.put(bytesToGet);
   }

   @Override
   public int skipBytes(final int length) {

      try {
         for (int i = 0; i < length; i++) {
            getStream().read();
         }
         return length;
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage(), e);
      }
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
   @SuppressWarnings("deprecation")
   public String readLine() throws IOException {
      return getStream().readLine();
   }

   @Override
   public void writeByte(final byte value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeShort(final short value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeInt(final int value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeNullableInt(final Integer value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeLong(final long value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeNullableLong(final Long value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeBytes(final byte[] src, final int srcIndex, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeBytes(final byte[] src) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeBytes(final ByteBuffer src) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeBytes(ByteBuf src, int srcIndex, int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }


   @Override
   public ByteBuffer toByteBuffer() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   public Object getUnderlyingBuffer() {
      return this;
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
   public char readChar() {
      return (char) readShort();
   }

   @Override
   public char getChar(final int index) {
      return (char) getShort(index);
   }

   @Override
   public double getDouble(final int index) {
      return Double.longBitsToDouble(getLong(index));
   }

   @Override
   public float getFloat(final int index) {
      return Float.intBitsToFloat(getInt(index));
   }

   @Override
   public double readDouble() {
      return Double.longBitsToDouble(readLong());
   }

   @Override
   public float readFloat() {
      return Float.intBitsToFloat(readInt());
   }

   @Override
   public SimpleString readNullableSimpleString() {
      int b = readByte();
      if (b == DataConstants.NULL) {
         return null;
      } else {
         return readSimpleString();
      }
   }

   @Override
   public String readNullableString() {
      int b = readByte();
      if (b == DataConstants.NULL) {
         return null;
      } else {
         return readString();
      }
   }

   @Override
   public SimpleString readSimpleString() {
      int len = readInt();
      byte[] data = new byte[len];
      readBytes(data);
      return new SimpleString(data);
   }

   @Override
   public String readString() {
      int len = readInt();

      if (len < 9) {
         char[] chars = new char[len];
         for (int i = 0; i < len; i++) {
            chars[i] = (char) readShort();
         }
         return new String(chars);
      } else if (len < 0xfff) {
         return readUTF();
      } else {
         return readSimpleString().toString();
      }
   }

   @Override
   public String readUTF() {
      return UTF8Util.readUTF(this);
   }

   @Override
   public void writeBoolean(final boolean val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeNullableBoolean(final Boolean val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeChar(final char val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeDouble(final double val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);

   }

   @Override
   public void writeFloat(final float val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);

   }

   @Override
   public void writeNullableSimpleString(final SimpleString val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeNullableString(final String val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeSimpleString(final SimpleString val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeString(final String val) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeUTF(final String utf) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public ActiveMQBuffer copy() {
      throw new UnsupportedOperationException();
   }

   @Override
   public ActiveMQBuffer slice(final int index, final int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ByteBuf byteBuf() {
      return null;
   }

   @Override
   public ActiveMQBuffer copy(final int index, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public ActiveMQBuffer duplicate() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public ActiveMQBuffer readSlice(final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setChar(final int index, final char value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setDouble(final int index, final double value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void setFloat(final int index, final float value) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public ActiveMQBuffer slice() {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int srcIndex, final int length) {
      throw new IllegalAccessError(OPERATION_NOT_SUPPORTED);
   }
}
