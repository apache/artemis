/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.core.io.DummyCallback;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.io.buffer.TimedBufferObserver;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.SimpleWaitIOCallback;

final class TimedSequentialFile implements SequentialFile {

   private final SequentialFileFactory factory;
   private final SequentialFile sequentialFile;
   private final LocalBufferObserver observer;
   private TimedBuffer timedBuffer;

   TimedSequentialFile(SequentialFileFactory factory, SequentialFile sequentialFile) {
      this.sequentialFile = sequentialFile;
      this.factory = factory;
      this.observer = new LocalBufferObserver();
   }

   @Override
   public boolean isOpen() {
      return this.sequentialFile.isOpen();
   }

   @Override
   public boolean exists() {
      return this.sequentialFile.exists();
   }

   @Override
   public void open() throws Exception {
      this.sequentialFile.open();
   }

   @Override
   public void open(int maxIO, boolean useExecutor) throws Exception {
      this.sequentialFile.open(maxIO, useExecutor);
   }

   @Override
   public boolean fits(int size) {
      if (timedBuffer == null) {
         return this.sequentialFile.fits(size);
      } else {
         return timedBuffer.checkSize(size);
      }
   }

   @Override
   public ByteBuffer map(int position, long size) throws IOException {
      return null;
   }

   @Override
   public int calculateBlockStart(int position) throws Exception {
      return this.sequentialFile.calculateBlockStart(position);
   }

   @Override
   public String getFileName() {
      return this.sequentialFile.getFileName();
   }

   @Override
   public void fill(int size) throws Exception {
      this.sequentialFile.fill(size);
   }

   @Override
   public void delete() throws IOException, InterruptedException, ActiveMQException {
      this.sequentialFile.delete();
   }

   @Override
   public void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) throws Exception {
      this.sequentialFile.blockingWriteDirect(bytes, sync, releaseBuffer);
   }

   @Override
   public void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback) throws Exception {
      if (this.timedBuffer != null) {
         this.timedBuffer.addBytes(bytes, sync, callback);
      } else {
         this.sequentialFile.write(bytes, sync, callback);
      }
   }

   @Override
   public void write(ActiveMQBuffer bytes, boolean sync) throws Exception {
      if (sync) {
         if (this.timedBuffer != null) {
            //the only way to avoid allocations is by using a lock-free pooled callback -> CyclicBarrier allocates on each new Generation!!!
            final SimpleWaitIOCallback callback = new SimpleWaitIOCallback();
            this.timedBuffer.addBytes(bytes, true, callback);
            callback.waitCompletion();
         } else {
            this.sequentialFile.write(bytes, true);
         }
      } else {
         if (this.timedBuffer != null) {
            this.timedBuffer.addBytes(bytes, false, DummyCallback.getInstance());
         } else {
            this.sequentialFile.write(bytes, false);
         }
      }
   }

   @Override
   public void write(EncodingSupport bytes, boolean sync, IOCallback callback) throws Exception {
      if (this.timedBuffer != null) {
         this.timedBuffer.addBytes(bytes, sync, callback);
      } else {
         this.sequentialFile.write(bytes, sync, callback);
      }
   }

   @Override
   public void write(EncodingSupport bytes, boolean sync) throws Exception {
      if (sync) {
         if (this.timedBuffer != null) {
            //the only way to avoid allocations is by using a lock-free pooled callback -> CyclicBarrier allocates on each new Generation!!!
            final SimpleWaitIOCallback callback = new SimpleWaitIOCallback();
            this.timedBuffer.addBytes(bytes, true, callback);
            callback.waitCompletion();
         } else {
            this.sequentialFile.write(bytes, true);
         }
      } else {
         if (this.timedBuffer != null) {
            this.timedBuffer.addBytes(bytes, false, DummyCallback.getInstance());
         } else {
            this.sequentialFile.write(bytes, false);
         }
      }
   }

   @Override
   public void writeDirect(ByteBuffer bytes, boolean sync, IOCallback callback) {
      this.sequentialFile.writeDirect(bytes, sync, callback);
   }

   @Override
   public void writeDirect(ByteBuffer bytes, boolean sync) throws Exception {
      this.sequentialFile.writeDirect(bytes, sync);
   }

   @Override
   public int read(ByteBuffer bytes, IOCallback callback) throws Exception {
      return this.sequentialFile.read(bytes, callback);
   }

   @Override
   public int read(ByteBuffer bytes) throws Exception {
      return this.sequentialFile.read(bytes);
   }

   @Override
   public void position(long pos) throws IOException {
      this.sequentialFile.position(pos);
   }

   @Override
   public long position() {
      return this.sequentialFile.position();
   }

   @Override
   public void close() throws Exception {
      try {
         this.sequentialFile.close();
      } finally {
         this.timedBuffer = null;
      }
   }

   @Override
   public void sync() throws IOException {
      this.sequentialFile.sync();
   }

   @Override
   public long size() throws Exception {
      return this.sequentialFile.size();
   }

   @Override
   public void renameTo(String newFileName) throws Exception {
      this.sequentialFile.renameTo(newFileName);
   }

   @Override
   public SequentialFile cloneFile() {
      return new TimedSequentialFile(factory, this.sequentialFile.cloneFile());
   }

   @Override
   public void copyTo(SequentialFile newFileName) throws Exception {
      this.sequentialFile.copyTo(newFileName);
   }

   @Override
   public void setTimedBuffer(TimedBuffer buffer) {
      if (this.timedBuffer != null) {
         this.timedBuffer.setObserver(null);
      }
      this.timedBuffer = buffer;
      if (buffer != null) {
         buffer.setObserver(this.observer);
      }
   }

   @Override
   public File getJavaFile() {
      return this.sequentialFile.getJavaFile();
   }

   private final class LocalBufferObserver implements TimedBufferObserver {

      @Override
      public boolean supportSync() {
         return false;
      }

      @Override
      public void flushBuffer(final ByteBuf byteBuf, final boolean requestedSync, final List<IOCallback> callbacks) {
         final int bytes = byteBuf.readableBytes();
         if (bytes > 0) {
            final boolean releaseBuffer;
            final ByteBuffer buffer;
            if (byteBuf.nioBufferCount() == 1) {
               //any ByteBuffer is fine with the MAPPED journal
               releaseBuffer = false;
               buffer = byteBuf.internalNioBuffer(byteBuf.readerIndex(), bytes);
            } else {
               //perform the copy on buffer
               releaseBuffer = true;
               buffer = factory.newBuffer(byteBuf.capacity(), false);
               buffer.limit(bytes);
               byteBuf.getBytes(byteBuf.readerIndex(), buffer);
               buffer.flip();
            }
            try {
               blockingWriteDirect(buffer, requestedSync, releaseBuffer);
               IOCallback.done(callbacks);
            } catch (Throwable t) {
               final int code;
               if (t instanceof IOException) {
                  code = ActiveMQExceptionType.IO_ERROR.getCode();
                  factory.onIOError(new ActiveMQIOErrorException(t.getMessage(), t), t.getMessage(), TimedSequentialFile.this.sequentialFile);
               } else {
                  code = ActiveMQExceptionType.GENERIC_EXCEPTION.getCode();
               }
               IOCallback.onError(callbacks, code, t.getMessage());
            }
         } else {
            IOCallback.done(callbacks);
         }
      }

      @Override
      public void checkSync(boolean syncRequested, List<IOCallback> callbacks) {
         throw new UnsupportedOperationException("This method is not supported on mapped");
      }

      @Override
      public int getRemainingBytes() {
         try {
            final long position = sequentialFile.position();
            final long size = sequentialFile.size();
            final long remaining = size - position;
            if (remaining > Integer.MAX_VALUE) {
               return Integer.MAX_VALUE;
            } else {
               return (int) remaining;
            }
         } catch (Exception e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public String toString() {
         return "TimedBufferObserver on file (" + sequentialFile.getFileName() + ")";
      }

   }
}
