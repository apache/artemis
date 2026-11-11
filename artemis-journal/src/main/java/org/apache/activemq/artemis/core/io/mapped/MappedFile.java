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
package org.apache.activemq.artemis.core.io.mapped;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.utils.PowerOf2Util;
import org.apache.activemq.artemis.utils.Env;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;

final class MappedFile implements AutoCloseable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int OS_PAGE_SIZE = Env.osPageSize();
   private final MappedByteBuffer buffer;
   private final FileChannel channel;
   private final long address;
   private final ByteBuf byteBufWrapper;
   private final ChannelBufferWrapper channelBufferWrapper;
   private int position;
   private int length;

   private MappedFile(FileChannel channel, MappedByteBuffer byteBuffer, int position, int length) throws IOException {
      this.channel = channel;
      this.buffer = byteBuffer;
      this.position = position;
      this.length = length;
      this.byteBufWrapper = Unpooled.wrappedBuffer(buffer);
      this.channelBufferWrapper = new ChannelBufferWrapper(this.byteBufWrapper, false);
      this.address = PlatformDependent.directBufferAddress(buffer);
   }

   public static MappedFile of(File file, int position, int capacity) throws IOException {
      final MappedByteBuffer buffer;
      final int length;
      final FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
      length = (int) channel.size();
      if (length != capacity && length != 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("Adjusting capacity to {} while it was {} on file {}", length, capacity, file);
         }
         capacity = length;
      }
      buffer = channel.map(FileChannel.MapMode.READ_WRITE, position, capacity);
      return new MappedFile(channel, buffer, 0, length);
   }

   public FileChannel channel() {
      return channel;
   }

   public MappedByteBuffer mapped() {
      return buffer;
   }

   public long address() {
      return this.address;
   }

   public void force() {
      this.buffer.force();
   }

   private void checkCapacity(int requiredCapacity) {
      if (requiredCapacity < 0 || requiredCapacity > buffer.capacity()) {
         throw new IllegalStateException("requiredCapacity must be >0 and <= " + buffer.capacity());
      }
   }

   /**
    * It is raw because it doesn't validate capacity through {@link #checkCapacity(int)}.
    */
   private void rawMovePositionAndLength(int position) {
      this.position = position;
      if (position > this.length) {
         this.length = position;
      }
   }

   /**
    * Reads a sequence of bytes from this file into the given buffer.
    * <p>
    * Bytes are read starting at this file's current position, and then the position is updated with the number of bytes
    * actually read.
    */
   public int read(ByteBuffer dst, int dstStart, int dstLength) throws IOException {
      final int remaining = this.length - this.position;
      final int read = Math.min(remaining, dstLength);
      final long srcAddress = this.address + this.position;
      if (dst.isDirect()) {
         final long dstAddress = PlatformDependent.directBufferAddress(dst) + dstStart;
         PlatformDependent.copyMemory(srcAddress, dstAddress, read);
      } else {
         final byte[] dstArray = dst.array();
         PlatformDependent.copyMemory(srcAddress, dstArray, dstStart, read);
      }
      this.position += read;
      return read;
   }

   /**
    * Writes an encoded sequence of bytes to this file from the given buffer.
    * <p>
    * Bytes are written starting at this file's current position,
    */
   public void write(EncodingSupport encodingSupport) throws IOException {
      final int encodedSize = encodingSupport.getEncodeSize();
      final int nextPosition = this.position + encodedSize;
      checkCapacity(nextPosition);
      this.byteBufWrapper.setIndex(this.position, this.position);
      encodingSupport.encode(this.channelBufferWrapper);
      rawMovePositionAndLength(nextPosition);
      assert (byteBufWrapper.writerIndex() == this.position);
   }

   /**
    * Writes a sequence of bytes to this file from the given buffer.
    * <p>
    * Bytes are written starting at this file's current position,
    */
   public void write(ByteBuf src, int srcStart, int srcLength) throws IOException {
      final int nextPosition = this.position + srcLength;
      checkCapacity(nextPosition);
      final long destAddress = this.address + this.position;
      if (src.hasMemoryAddress()) {
         final long srcAddress = src.memoryAddress() + srcStart;
         PlatformDependent.copyMemory(srcAddress, destAddress, srcLength);
      } else if (src.hasArray()) {
         final byte[] srcArray = src.array();
         PlatformDependent.copyMemory(srcArray, srcStart, destAddress, srcLength);
      } else {
         throw new IllegalArgumentException("unsupported byte buffer");
      }
      rawMovePositionAndLength(nextPosition);
   }

   /**
    * Writes a sequence of bytes to this file from the given buffer.
    * <p>
    * Bytes are written starting at this file's current position,
    */
   public void write(ByteBuffer src, int srcStart, int srcLength) throws IOException {
      final int nextPosition = this.position + srcLength;
      checkCapacity(nextPosition);
      final long destAddress = this.address + this.position;
      if (src.isDirect()) {
         final long srcAddress = PlatformDependent.directBufferAddress(src) + srcStart;
         PlatformDependent.copyMemory(srcAddress, destAddress, srcLength);
      } else {
         final byte[] srcArray = src.array();
         PlatformDependent.copyMemory(srcArray, srcStart, destAddress, srcLength);
      }
      rawMovePositionAndLength(nextPosition);
   }

   /**
    * Writes a sequence of bytes to this file from the given buffer.
    * <p>
    * Bytes are written starting at this file's current position,
    */
   public void zeros(int position, final int count) throws IOException {
      checkCapacity(position + count);
      //zeroes memory in reverse direction in OS_PAGE_SIZE batches
      //to gain sympathy by the page cache LRU policy
      final long start = this.address + position;
      final long end = start + count;
      int toZeros = count;
      final long lastGap = (int) (end & (OS_PAGE_SIZE - 1));
      final long lastStartPage = end - lastGap;
      long lastZeroed = end;
      if (start <= lastStartPage) {
         if (lastGap > 0) {
            PlatformDependent.setMemory(lastStartPage, lastGap, (byte) 0);
            lastZeroed = lastStartPage;
            toZeros -= lastGap;
         }
      }
      //any that will enter has lastZeroed OS page aligned
      while (toZeros >= OS_PAGE_SIZE) {
         assert PowerOf2Util.isAligned(lastZeroed, OS_PAGE_SIZE);/**/
         final long startPage = lastZeroed - OS_PAGE_SIZE;
         PlatformDependent.setMemory(startPage, OS_PAGE_SIZE, (byte) 0);
         lastZeroed = startPage;
         toZeros -= OS_PAGE_SIZE;
      }
      //there is anything left in the first OS page?
      if (toZeros > 0) {
         PlatformDependent.setMemory(start, toZeros, (byte) 0);
      }
      //do not move this.position: only this.length can be changed
      position += count;
      if (position > this.length) {
         this.length = position;
      }
   }

   public int position() {
      return this.position;
   }

   public void position(int position) {
      checkCapacity(position);
      this.position = position;
   }

   public long length() {
      return this.length;
   }

   @Override
   public void close() {
      try {
         channel.close();
      } catch (IOException e) {
         throw new IllegalStateException(e);
      } finally {
         //unmap in a deterministic way: do not rely on GC to do it
         PlatformDependent.freeDirectBuffer(this.buffer);
      }
   }
}
