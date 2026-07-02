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
package org.apache.artemis.nativo.jlibaio;

import org.apache.artemis.nativo.jlibaio.LibaioContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This is an extension to use libaio.
 */
public final class LibaioFile<Callback extends SubmitInfo> implements AutoCloseable {

   private static final Logger logger = LoggerFactory.getLogger(LibaioFile.class);

   protected boolean open;
   /**
    * This represents a structure allocated on the native
    * this is a io_context_t
    */
   final LibaioContext<Callback> ctx;

   private int fd;

   LibaioFile(int fd, LibaioContext ctx) {
      this.ctx = ctx;
      this.fd = fd;
   }

   public int getBlockSize() throws IOException {
      return LibaioContext.getBlockSizeFD(fd);
   }

   public boolean lock() {
      return LibaioContext.lock(fd);
   }

   @Override
   public void close() throws IOException {
      open = false;
      LibaioContext.close(fd);
   }

   /**
    * @return The size of the file.
    */
   public long getSize() throws IOException {
      return LibaioContext.getSize(fd);
   }

   /**
    * It will submit a write to the queue. The callback sent here will be received on the
    * {@link LibaioContext#poll(SubmitInfo[], int, int)}
    * In case of the libaio queue is full (e.g. returning E_AGAIN) this method will return false.
    * <br>
    * Notice: this won't hold a global reference on buffer, callback should hold a reference towards bufferWrite.
    * And don't free the buffer until the callback was called as this could crash the VM.
    *
    * @param position The position on the file to write. Notice this has to be a multiple of 512.
    * @param size     The size of the buffer to use while writing.
    * @param buffer   if you are using O_DIRECT the buffer here needs to be allocated by {@link #newBuffer(int)}.
    * @param callback A callback to be returned on the poll method.
    * @throws IOException in case of error
    */
   public void write(long position, int size, ByteBuffer buffer, Callback callback) throws IOException {
      Objects.requireNonNull(callback, "Callback cannot be null");
      ctx.submitWrite(fd, position, size, buffer, callback);
   }

   /**
    * It will submit a read to the queue. The callback sent here will be received on the
    * {@link LibaioContext#poll(SubmitInfo[], int, int)}.
    * In case of the libaio queue is full (e.g. returning E_AGAIN) this method will return false.
    * <br>
    * Notice: this won't hold a global reference on buffer, callback should hold a reference towards bufferWrite.
    * And don't free the buffer until the callback was called as this could crash the VM.
    * *
    *
    * @param position The position on the file to read. Notice this has to be a multiple of 512.
    * @param size     The size of the buffer to use while reading.
    * @param buffer   if you are using O_DIRECT the buffer here needs to be allocated by {@link #newBuffer(int)}.
    * @param callback A callback to be returned on the poll method.
    * @throws IOException in case of error
    * @see LibaioContext#poll(SubmitInfo[], int, int)
    */
   public void read(long position, int size, ByteBuffer buffer, Callback callback) throws IOException {
      Objects.requireNonNull(callback, "Callback cannot be null");
      ctx.submitRead(fd, position, size, buffer, callback);
   }

   /**
    * It will allocate a buffer to be used on libaio operations.
    * Buffers here are allocated with posix_memalign.
    * <br>
    * You need to explicitly free the buffer created from here using the
    * {@link LibaioContext#freeBuffer(MemorySegment)}.
    *
    * @param size the size of the buffer.
    * @return the buffer allocated.
    */
   public MemorySegment newBuffer(int size) {
      return LibaioContext.newAlignedBuffer(size, 4 * 1024);
   }

   /**
    * It will preallocate the file with a given size.
    *
    * @param size number of bytes to be filled on the file
    */
   public void fill(int alignment, long size) throws IOException {
      try {
         LibaioContext.fill(fd, alignment, size);
      } catch (OutOfMemoryError e) {
         logger.warn("Did not have enough memory to allocate " + size + " bytes in memory while filling the file, using simple fallocate");
         LibaioContext.fallocate(fd, size);
      }
   }

   /**
    * It will use fallocate to initialize a file.
    *
    * @param size number of bytes to be filled on the file
    */
   public void fallocate(long size) throws IOException {
      LibaioContext.fallocate(fd, size);
   }

}
