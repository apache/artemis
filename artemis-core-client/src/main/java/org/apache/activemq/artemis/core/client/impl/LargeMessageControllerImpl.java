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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.ActiveMQLargeMessageInterruptedException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.UTF8Util;

/**
 * This class aggregates several {@link org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveContinuationMessage}
 * as it was being handled
 * by a single buffer. This buffer can be consumed as messages are arriving, and it will hold the
 * packets until they are read using the ChannelBuffer interface, or the setOutputStream or
 * saveStream are called.
 */
public class LargeMessageControllerImpl implements LargeMessageController {

   private static final String READ_ONLY_ERROR_MESSAGE = "This is a read-only buffer, setOperations are not supported";


   private final ClientConsumerInternal consumerInternal;

   private final LinkedBlockingQueue<LargeData> largeMessageData = new LinkedBlockingQueue<>();

   private volatile LargeData currentPacket = null;

   private final long totalSize;

   private final int bufferSize;

   private boolean streamEnded = false;

   private boolean streamClosed = false;

   private final long readTimeout;

   private long readerIndex = 0;

   /**
    * This is to control if packets are arriving for a better timeout control
    */
   private boolean packetAdded = false;

   private long packetPosition = -1;

   private long lastIndex = 0;

   private long packetLastPosition = -1;

   private OutputStream outStream;

   // There's no need to wait a synchronization
   // we just set the exception and let other threads to get it as soon as possible
   private volatile Exception handledException;

   private final FileCache fileCache;

   private boolean local = false;

   public LargeMessageControllerImpl(final ClientConsumerInternal consumerInternal,
                                     final long totalSize,
                                     final long readTimeout) {
      this(consumerInternal, totalSize, readTimeout, null);
   }

   public LargeMessageControllerImpl(final ClientConsumerInternal consumerInternal,
                                     final long totalSize,
                                     final long readTimeout,
                                     final File cachedFile) {
      this(consumerInternal, totalSize, readTimeout, cachedFile, 10 * 1024);
   }

   public LargeMessageControllerImpl(final ClientConsumerInternal consumerInternal,
                                     final long totalSize,
                                     final long readTimeout,
                                     final File cachedFile,
                                     final int bufferSize) {
      this.consumerInternal = consumerInternal;
      this.readTimeout = readTimeout;
      this.totalSize = totalSize;
      if (cachedFile == null) {
         fileCache = null;
      } else {
         fileCache = new FileCache(cachedFile);
      }
      this.bufferSize = bufferSize;
   }

   public void setLocal(boolean local) {
      this.local = local;
   }

   @Override
   public void discardUnusedPackets() {
      if (outStream == null) {
         if (local)
            return;
         try {
            checkForPacket(totalSize - 1);
         } catch (Throwable ignored) {
         }
      }
   }

   /**
    * TODO: move this to ConsumerContext as large message is a protocol specific thing
    * Add a buff to the List, or save it to the OutputStream if set
    */
   @Override
   public void addPacket(byte[] chunk, int flowControlSize, boolean isContinues) {
      int flowControlCredit = 0;

      synchronized (this) {
         packetAdded = true;
         if (outStream != null) {
            try {
               if (!isContinues) {
                  streamEnded = true;
               }

               if (fileCache != null) {
                  fileCache.cachePackage(chunk);
               }

               outStream.write(chunk);

               flowControlCredit = flowControlSize;

               if (streamEnded) {
                  outStream.close();
               }
            } catch (Exception e) {
               ActiveMQClientLogger.LOGGER.errorAddingPacket(e);
               handledException = e;
            } finally {
               notifyAll();
            }
         } else {
            if (fileCache != null) {
               try {
                  fileCache.cachePackage(chunk);
               } catch (Exception e) {
                  ActiveMQClientLogger.LOGGER.errorAddingPacket(e);
                  handledException = e;
               }
            }

            largeMessageData.offer(new LargeData(chunk, flowControlSize, isContinues));
         }
      }

      if (flowControlCredit != 0) {
         try {
            consumerInternal.flowControl(flowControlCredit, !isContinues);
         } catch (Exception e) {
            ActiveMQClientLogger.LOGGER.errorAddingPacket(e);
            handledException = e;
         }
      }
   }

   @Override
   public void cancel() {
      this.handledException = ActiveMQClientMessageBundle.BUNDLE.largeMessageInterrupted();

      synchronized (this) {
         int totalSize = 0;
         LargeData polledPacket = null;
         while ((polledPacket = largeMessageData.poll()) != null) {
            totalSize += polledPacket.getFlowControlSize();
         }

         try {
            consumerInternal.flowControl(totalSize, false);
         } catch (Exception ignored) {
            // what else can we do here?
            ActiveMQClientLogger.LOGGER.errorCallingCancel(ignored);
         }

         largeMessageData.offer(new LargeData());
         streamEnded = true;
         streamClosed = true;

         notifyAll();
      }
   }

   @Override
   public synchronized void close() {
      if (fileCache != null) {
         fileCache.close();
      }
   }

   @Override
   public void setOutputStream(final OutputStream output) throws ActiveMQException {

      int totalFlowControl = 0;
      boolean continues = false;

      synchronized (this) {
         if (currentPacket != null) {
            sendPacketToOutput(output, currentPacket);
            currentPacket = null;
         }
         while (handledException == null) {
            LargeData packet = largeMessageData.poll();
            if (packet == null) {
               break;
            }
            totalFlowControl += packet.getFlowControlSize();

            continues = packet.isContinues();
            sendPacketToOutput(output, packet);
         }

         checkException();
         outStream = output;
      }

      if (totalFlowControl > 0) {
         consumerInternal.flowControl(totalFlowControl, !continues);
      }
   }

   @Override
   public synchronized void saveBuffer(final OutputStream output) throws ActiveMQException {
      if (streamClosed) {
         throw ActiveMQClientMessageBundle.BUNDLE.largeMessageLostSession();
      }
      setOutputStream(output);
      waitCompletion(0);
   }

   /**
    * @param timeWait Milliseconds to Wait. 0 means forever
    * @throws ActiveMQException
    */
   @Override
   public synchronized boolean waitCompletion(final long timeWait) throws ActiveMQException {
      if (outStream == null) {
         // There is no stream.. it will never achieve the end of streaming
         return false;
      }

      long timeOut;

      // If timeWait = 0, we will use the readTimeout
      // And we will check if no packets have arrived within readTimeout milliseconds
      if (timeWait != 0) {
         timeOut = System.currentTimeMillis() + timeWait;
      } else {
         timeOut = System.currentTimeMillis() + readTimeout;
      }

      while (!streamEnded && handledException == null) {
         try {
            this.wait(timeWait == 0 ? readTimeout : timeWait);
         } catch (InterruptedException e) {
            throw new ActiveMQInterruptedException(e);
         }

         if (!streamEnded && handledException == null) {
            if (timeWait != 0 && System.currentTimeMillis() > timeOut) {
               throw ActiveMQClientMessageBundle.BUNDLE.timeoutOnLargeMessage();
            } else if (System.currentTimeMillis() > timeOut && !packetAdded) {
               throw ActiveMQClientMessageBundle.BUNDLE.timeoutOnLargeMessage();
            }
         }
      }

      checkException();

      return streamEnded;

   }

   @Override
   public LargeData take() throws InterruptedException {
      return largeMessageData.take();
   }

   /**
    * @throws ActiveMQException
    */
   private void checkException() throws ActiveMQException {
      // it's not needed to copy it as we never set it back to null
      // once the exception is set, the controller is pretty much useless
      if (handledException != null) {
         if (handledException instanceof ActiveMQException) {
            ActiveMQException nestedException;

            // This is just to be user friendly and give the user a proper exception trace,
            // instead to just where it was canceled.
            if (handledException instanceof ActiveMQLargeMessageInterruptedException) {
               nestedException = new ActiveMQLargeMessageInterruptedException(handledException.getMessage());
            } else {
               nestedException = new ActiveMQException(((ActiveMQException) handledException).getType(), handledException.getMessage());
            }
            nestedException.initCause(handledException);

            throw nestedException;
         } else {
            throw new ActiveMQException(ActiveMQExceptionType.LARGE_MESSAGE_ERROR_BODY, "Error on saving LargeMessageBufferImpl", handledException);
         }
      }
   }

   // Channel Buffer Implementation ---------------------------------

   @Override
   public int capacity() {
      return -1;
   }

   @Override
   public byte readByte() {
      return getByte(readerIndex++);
   }

   @Override
   public byte getByte(final int index) {
      return getByte((long) index);
   }

   private byte getByte(final long index) {
      checkForPacket(index);

      if (fileCache != null && index < packetPosition) {
         return fileCache.getByteFromCache(index);
      } else {
         return currentPacket.getChunk()[(int) (index - packetPosition)];
      }
   }

   @Override
   public void getBytes(final int index, final ActiveMQBuffer dst, final int dstIndex, final int length) {
      byte[] destBytes = new byte[length];
      getBytes(index, destBytes);
      dst.setBytes(dstIndex, destBytes);
   }

   private void getBytes(final long index, final ActiveMQBuffer dst, final int dstIndex, final int length) {
      byte[] destBytes = new byte[length];
      getBytes(index, destBytes);
      dst.setBytes(dstIndex, destBytes);
   }

   @Override
   public void getBytes(final int index, final byte[] dst, final int dstIndex, final int length) {
      byte[] bytesToGet = new byte[length];

      getBytes(index, bytesToGet);

      System.arraycopy(bytesToGet, 0, dst, dstIndex, length);
   }

   public void getBytes(final long index, final byte[] dst, final int dstIndex, final int length) {
      byte[] bytesToGet = new byte[length];

      getBytes(index, bytesToGet);

      System.arraycopy(bytesToGet, 0, dst, dstIndex, length);
   }

   @Override
   public void getBytes(final int index, final ByteBuffer dst) {
      byte[] bytesToGet = new byte[dst.remaining()];
      getBytes(index, bytesToGet);
      dst.put(bytesToGet);
   }

   public void getBytes(final long index, final ByteBuffer dst) {
      byte[] bytesToGet = new byte[dst.remaining()];
      getBytes(index, bytesToGet);
      dst.put(bytesToGet);
   }

   public void getBytes(final int index, final OutputStream out, final int length) throws IOException {
      byte[] bytesToGet = new byte[length];
      getBytes(index, bytesToGet);
      out.write(bytesToGet);
   }

   public void getBytes(final long index, final OutputStream out, final int length) throws IOException {
      byte[] bytesToGet = new byte[length];
      getBytes(index, bytesToGet);
      out.write(bytesToGet);
   }

   public int getBytes(final int index, final GatheringByteChannel out, final int length) throws IOException {
      byte[] bytesToGet = new byte[length];
      getBytes(index, bytesToGet);
      return out.write(ByteBuffer.wrap(bytesToGet));
   }

   @Override
   public int getInt(final int index) {
      return (getByte(index) & 0xff) << 24 | (getByte(index + 1) & 0xff) << 16 |
         (getByte(index + 2) & 0xff) << 8 |
         (getByte(index + 3) & 0xff) << 0;
   }

   public int getInt(final long index) {
      return (getByte(index) & 0xff) << 24 | (getByte(index + 1) & 0xff) << 16 |
         (getByte(index + 2) & 0xff) << 8 |
         (getByte(index + 3) & 0xff) << 0;
   }

   @Override
   public long getLong(final int index) {
      return ((long) getByte(index) & 0xff) << 56 | ((long) getByte(index + 1) & 0xff) << 48 |
         ((long) getByte(index + 2) & 0xff) << 40 |
         ((long) getByte(index + 3) & 0xff) << 32 |
         ((long) getByte(index + 4) & 0xff) << 24 |
         ((long) getByte(index + 5) & 0xff) << 16 |
         ((long) getByte(index + 6) & 0xff) << 8 |
         ((long) getByte(index + 7) & 0xff) << 0;
   }

   public long getLong(final long index) {
      return ((long) getByte(index) & 0xff) << 56 | ((long) getByte(index + 1) & 0xff) << 48 |
         ((long) getByte(index + 2) & 0xff) << 40 |
         ((long) getByte(index + 3) & 0xff) << 32 |
         ((long) getByte(index + 4) & 0xff) << 24 |
         ((long) getByte(index + 5) & 0xff) << 16 |
         ((long) getByte(index + 6) & 0xff) << 8 |
         ((long) getByte(index + 7) & 0xff) << 0;
   }

   @Override
   public short getShort(final int index) {
      return (short) (getByte(index) << 8 | getByte(index + 1) & 0xFF);
   }

   public short getShort(final long index) {
      return (short) (getByte(index) << 8 | getByte(index + 1) & 0xFF);
   }

   private int getUnsignedMedium(final int index) {
      return (getByte(index) & 0xff) << 16 | (getByte(index + 1) & 0xff) << 8 | (getByte(index + 2) & 0xff) << 0;
   }

   public int getUnsignedMedium(final long index) {
      return (getByte(index) & 0xff) << 16 | (getByte(index + 1) & 0xff) << 8 | (getByte(index + 2) & 0xff) << 0;
   }

   @Override
   public void setByte(final int index, final byte value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int srcIndex, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setBytes(final int index, final byte[] src, final int srcIndex, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setBytes(final int index, final ByteBuffer src) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setInt(final int index, final int value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setLong(final int index, final long value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setShort(final int index, final short value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public ByteBuffer toByteBuffer(final int index, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void release() {
      //no-op
   }

   @Override
   public int readerIndex() {
      return (int) readerIndex;
   }

   @Override
   public void readerIndex(final int readerIndex) {
      try {
         checkForPacket(readerIndex);
      } catch (Exception e) {
         ActiveMQClientLogger.LOGGER.errorReadingIndex(e);
         throw new RuntimeException(e.getMessage(), e);
      }
      this.readerIndex = readerIndex;
   }

   @Override
   public int writerIndex() {
      return (int) totalSize;
   }

   @Override
   public long getSize() {
      return totalSize;
   }

   @Override
   public void writerIndex(final int writerIndex) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setIndex(final int readerIndex, final int writerIndex) {
      try {
         checkForPacket(readerIndex);
      } catch (Exception e) {
         ActiveMQClientLogger.LOGGER.errorSettingIndex(e);
         throw new RuntimeException(e.getMessage(), e);
      }
      this.readerIndex = readerIndex;
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
      long readableBytes = totalSize - readerIndex;

      if (readableBytes > Integer.MAX_VALUE) {
         return Integer.MAX_VALUE;
      } else {
         return (int) (totalSize - readerIndex);
      }
   }

   @Override
   public int writableBytes() {
      return 0;
   }

   @Override
   public void markReaderIndex() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void resetReaderIndex() {
      try {
         checkForPacket(0);
      } catch (Exception e) {
         ActiveMQClientLogger.LOGGER.errorReSettingIndex(e);
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public void markWriterIndex() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void resetWriterIndex() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void discardReadBytes() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public short getUnsignedByte(final int index) {
      return (short) (getByte(index) & 0xFF);
   }

   @Override
   public int getUnsignedShort(final int index) {
      return getShort(index) & 0xFFFF;
   }

   public int getMedium(final int index) {
      int value = getUnsignedMedium(index);
      if ((value & 0x800000) != 0) {
         value |= 0xff000000;
      }
      return value;
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

   public void getBytes(long index, final byte[] dst) {
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
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setBytes(final int index, final ActiveMQBuffer src, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public void setZero(final int index, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public int readUnsignedByte() {
      return (short) (readByte() & 0xFF);
   }

   @Override
   public short readShort() {
      short v = getShort(readerIndex);
      readerIndex += 2;
      return v;
   }

   @Override
   public int readUnsignedShort() {
      return readShort() & 0xFFFF;
   }

   public int readMedium() {
      int value = readUnsignedMedium();
      if ((value & 0x800000) != 0) {
         value |= 0xff000000;
      }
      return value;
   }

   public int readUnsignedMedium() {
      int v = getUnsignedMedium(readerIndex);
      readerIndex += 3;
      return v;
   }

   @Override
   public int readInt() {
      int v = getInt(readerIndex);
      readerIndex += 4;
      return v;
   }

   public int readInt(final int pos) {
      return getInt(pos);
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
      long v = getLong(readerIndex);
      readerIndex += 8;
      return v;
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
      getBytes(readerIndex, dst, dstIndex, length);
      readerIndex += length;
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
      getBytes(readerIndex, dst, dstIndex, length);
      readerIndex += length;
   }

   @Override
   public void readBytes(final ByteBuffer dst) {
      int length = dst.remaining();
      getBytes(readerIndex, dst);
      readerIndex += length;
   }

   public int readBytes(final GatheringByteChannel out, final int length) throws IOException {
      int readBytes = getBytes((int) readerIndex, out, length);
      readerIndex += readBytes;
      return readBytes;
   }

   public void readBytes(final OutputStream out, final int length) throws IOException {
      getBytes(readerIndex, out, length);
      readerIndex += length;
   }

   @Override
   public int skipBytes(final int length) {

      long newReaderIndex = readerIndex + length;
      checkForPacket(newReaderIndex);
      readerIndex = newReaderIndex;
      return length;
   }

   @Override
   public void writeByte(final byte value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeShort(final short value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public void writeMedium(final int value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeInt(final int value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeNullableInt(final Integer value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeLong(final long value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeNullableLong(final Long value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeBytes(final byte[] src, final int srcIndex, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeBytes(final byte[] src) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public void writeBytes(final ActiveMQBuffer src) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeBytes(final ByteBuffer src) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   /**
    * Transfers the specified source buffer's data to this buffer starting at
    * the current {@code writerIndex} until the source buffer's position
    * reaches its limit, and increases the {@code writerIndex} by the
    * number of the transferred bytes.
    *
    * @param src The source buffer
    * @throws IndexOutOfBoundsException if {@code src.remaining()} is greater than
    *                                   {@code this.writableBytes}
    */
   @Override
   public void writeBytes(ByteBuf src, int srcIndex, int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public int writeBytes(final InputStream in, final int length) throws IOException {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public int writeBytes(final ScatteringByteChannel in, final int length) throws IOException {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public void writeZero(final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public ByteBuffer toByteBuffer() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public ByteBuffer[] toByteBuffers() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public ByteBuffer[] toByteBuffers(final int index, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public String toString(final String charsetName) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
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
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeNullableBoolean(final Boolean val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeChar(final char val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeDouble(final double val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);

   }

   @Override
   public void writeFloat(final float val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);

   }

   @Override
   public void writeNullableSimpleString(final SimpleString val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeNullableString(final String val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeSimpleString(final SimpleString val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeString(final String val) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeUTF(final String utf) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public ActiveMQBuffer copy() {
      throw new UnsupportedOperationException();
   }

   @Override
   public ActiveMQBuffer slice(final int index, final int length) {
      throw new UnsupportedOperationException();
   }

   /**
    * @param output
    * @param packet
    * @throws ActiveMQException
    */
   private void sendPacketToOutput(final OutputStream output, final LargeData packet) throws ActiveMQException {
      try {
         output.write(packet.getChunk());
         if (!packet.isContinues()) {
            streamEnded = true;
            output.close();
         }
      } catch (IOException e) {
         throw ActiveMQClientMessageBundle.BUNDLE.errorWritingLargeMessage(e);
      }
   }

   private void popPacket() {
      try {

         if (streamEnded) {
            // no more packets, we are over the last one already
            throw new IndexOutOfBoundsException();
         }

         int sizeToAdd = currentPacket != null ? currentPacket.chunk.length : 1;
         currentPacket = largeMessageData.poll(readTimeout, TimeUnit.MILLISECONDS);
         if (currentPacket == null) {
            throw new IndexOutOfBoundsException();
         }

         if (currentPacket.chunk == null) { // Empty packet as a signal to interruption
            currentPacket = null;
            streamEnded = true;
            throw new IndexOutOfBoundsException();
         }

         consumerInternal.flowControl(currentPacket.getFlowControlSize(), !currentPacket.isContinues());

         packetPosition += sizeToAdd;

         packetLastPosition = packetPosition + currentPacket.getChunk().length;
      } catch (IndexOutOfBoundsException e) {
         throw e;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void checkForPacket(final long index) {
      if (outStream != null) {
         throw new IllegalAccessError("Can't read the messageBody after setting outputStream");
      }

      if (index >= totalSize) {
         throw new IndexOutOfBoundsException();
      }

      if (streamClosed) {
         throw new IllegalAccessError("The consumer associated with this large message was closed before the body was read");
      }

      if (fileCache == null) {
         if (index < lastIndex) {
            throw new IllegalAccessError("LargeMessage have read-only and one-way buffers");
         }
         lastIndex = index;
      }

      while (index >= packetLastPosition && !streamEnded) {
         popPacket();
      }
   }

   private final class FileCache {

      private FileCache(final File cachedFile) {
         this.cachedFile = cachedFile;
      }

      ByteBuffer readCache;

      long readCachePositionStart = Integer.MAX_VALUE;

      long readCachePositionEnd = -1;

      private final File cachedFile;

      private volatile FileChannel cachedChannel;

      private synchronized void readCache(final long position) {

         try {
            if (position < readCachePositionStart || position > readCachePositionEnd) {

               final FileChannel cachedChannel = checkOpen();

               if (position > cachedChannel.size()) {
                  throw new ArrayIndexOutOfBoundsException("position > " + cachedChannel.size());
               }

               readCachePositionStart = position / bufferSize * bufferSize;

               cachedChannel.position(readCachePositionStart);

               if (readCache == null) {
                  readCache = ByteBuffer.allocate(bufferSize);
               }

               readCache.clear();

               readCachePositionEnd = readCachePositionStart + cachedChannel.read(readCache) - 1;
            }
         } catch (Exception e) {
            ActiveMQClientLogger.LOGGER.errorReadingCache(e);
            throw new RuntimeException(e.getMessage(), e);
         } finally {
            close();
         }
      }

      public synchronized byte getByteFromCache(final long position) {
         readCache(position);

         return readCache.get((int) (position - readCachePositionStart));

      }

      public void cachePackage(final byte[] body) throws Exception {
         final FileChannel cachedChannel = checkOpen();

         cachedChannel.position(cachedChannel.size());
         cachedChannel.write(ByteBuffer.wrap(body));

         close();
      }

      /**
       * @throws FileNotFoundException
       */
      private FileChannel checkOpen() throws IOException {
         FileChannel channel = cachedChannel;
         if (cachedFile != null || !channel.isOpen()) {
            channel = FileChannel.open(cachedFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            cachedChannel = channel;
         }
         return channel;
      }

      public void close() {
         FileChannel cachedChannel = this.cachedChannel;
         if (cachedChannel != null && cachedChannel.isOpen()) {
            this.cachedChannel = null;
            try {
               cachedChannel.close();
            } catch (Exception e) {
               ActiveMQClientLogger.LOGGER.errorClosingCache(e);
            }
         }
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
   public String readLine() throws IOException {
      return ByteUtil.readLine(this);
   }

   @Override
   public ByteBuf byteBuf() {
      return null;
   }

   @Override
   public ActiveMQBuffer copy(final int index, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public ActiveMQBuffer duplicate() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public ActiveMQBuffer readSlice(final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setChar(final int index, final char value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setDouble(final int index, final double value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void setFloat(final int index, final float value) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public ActiveMQBuffer slice() {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   @Override
   public void writeBytes(final ActiveMQBuffer src, final int srcIndex, final int length) {
      throw new IllegalAccessError(LargeMessageControllerImpl.READ_ONLY_ERROR_MESSAGE);
   }

   public static class LargeData {

      final byte[] chunk;
      final int flowControlSize;
      final boolean continues;

      private LargeData() {
         continues = false;
         flowControlSize = 0;
         chunk = null;
      }

      private LargeData(byte[] chunk, int flowControlSize, boolean continues) {
         this.chunk = chunk;
         this.flowControlSize = flowControlSize;
         this.continues = continues;
      }

      public byte[] getChunk() {
         return chunk;
      }

      public int getFlowControlSize() {
         return flowControlSize;
      }

      public boolean isContinues() {
         return continues;
      }
   }
}
