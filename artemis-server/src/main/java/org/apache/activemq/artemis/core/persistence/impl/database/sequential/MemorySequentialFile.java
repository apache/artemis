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
package org.apache.activemq.artemis.core.persistence.impl.database.sequential;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;

public class MemorySequentialFile implements SequentialFile {

   private volatile boolean open;
   private String fileName;
   private ActiveMQBuffer data;

   public MemorySequentialFile(String fileName) {
      this.fileName = fileName;
   }

   public MemorySequentialFile(String fileName, ActiveMQBuffer data) {
      this.fileName = fileName;
      this.data = data;
   }

   @Override
   public int getMemoryEstimate() {
      return data.capacity();
   }

   public ActiveMQBuffer getData() {
      return data;
   }

   @Override
   public boolean isOpen() {
      return open;
   }

   @Override
   public synchronized void open() throws Exception {
      open(1, true);
   }

   @Override
   public synchronized void open(int maxIO, boolean useExecutor) throws Exception {
      open = true;
      if (data == null) {
         data = new ChannelBufferWrapper(Unpooled.buffer(0));
      }
      data.readerIndex(0);
   }

   @Override
   public synchronized void close() {
      open = false;
      if (data != null) {
         data.readerIndex(0);
      }
      notifyAll();
   }

   @Override
   public void delete() {
      data = null;
      if (open) {
         close();
      }
   }

   @Override
   public String getFileName() {
      return fileName;
   }

   @Override
   public void renameTo(String newFileName) throws Exception {
      fileName = newFileName;
   }

   @Override
   public void fill(int size) throws Exception {
      if (!open) {
         throw new IllegalStateException("Is closed");
      }
      data.clear();
      for (int i = 0; i < size; i++) {
         data.writeByte((byte) 0);
      }
      data.readerIndex(0);
   }

   @Override
   public int read(ByteBuffer bytes) throws Exception {
      return read(bytes, null);
   }

   @Override
   public int read(ByteBuffer bytes, IOCallback callback) throws Exception {
      if (!open) {
         throw new IllegalStateException("Is closed");
      }

      int readable = Math.min(bytes.remaining(), data.readableBytes());
      int savedLimit = bytes.limit();
      bytes.limit(bytes.position() + readable);
      data.readBytes(bytes);
      bytes.limit(savedLimit);
      bytes.rewind();

      if (callback != null) {
         callback.done();
      }

      return readable;
   }

   @Override
   public void position(long pos) {
      if (!open) {
         throw new IllegalStateException("Is closed");
      }
      data.readerIndex((int) pos);
   }

   @Override
   public long position() {
      return data == null ? 0 : data.readerIndex();
   }

   @Override
   public synchronized void writeDirect(ByteBuffer bytes, boolean sync, IOCallback callback) {
      if (!open) {
         throw new IllegalStateException("Is closed");
      }

      try {
         data.writeBytes(bytes);
         if (callback != null) {
            callback.done();
         }
      } catch (Throwable e) {
         if (callback != null) {
            callback.onError(-1, e.getMessage());
         }
      }
   }

   @Override
   public void writeDirect(ByteBuffer bytes, boolean sync) throws Exception {
      writeDirect(bytes, sync, null);
   }

   @Override
   public synchronized void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) throws Exception {
      writeDirect(bytes, sync);
   }

   @Override
   public void sync() throws IOException {
   }

   @Override
   public long size() throws Exception {
      return data == null ? 0 : data.writerIndex();
   }

   @Override
   public void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback) throws Exception {
      bytes.writerIndex(bytes.capacity());
      bytes.readerIndex(0);
      writeDirect(bytes.toByteBuffer(), sync, callback);
   }

   @Override
   public void write(ActiveMQBuffer bytes, boolean sync) throws Exception {
      bytes.writerIndex(bytes.capacity());
      bytes.readerIndex(0);
      writeDirect(bytes.toByteBuffer(), sync);
   }

   @Override
   public void write(EncodingSupport bytes, boolean sync, IOCallback callback) throws Exception {
      ActiveMQBuffer outbuffer = ActiveMQBuffers.dynamicBuffer(bytes.getEncodeSize());
      bytes.encode(outbuffer);
      write(outbuffer, sync, callback);
   }

   @Override
   public void write(EncodingSupport bytes, boolean sync) throws Exception {
      ActiveMQBuffer outbuffer = ActiveMQBuffers.dynamicBuffer(bytes.getEncodeSize());
      bytes.encode(outbuffer);
      write(outbuffer, sync);
   }

   @Override
   public boolean exists() {
      return data != null && data.capacity() > 0;
   }

   @Override
   public boolean fits(int size) {
      return data != null && data.writerIndex() + size <= data.capacity();
   }

   @Override
   public void setTimedBuffer(TimedBuffer buffer) {
   }

   @Override
   public int calculateBlockStart(int position) throws Exception {
      return position;
   }

   @Override
   public ByteBuffer map(int position, long size) throws IOException {
      return null;
   }

   @Override
   public SequentialFile cloneFile() {
      return this;
   }

   @Override
   public void copyTo(SequentialFile newFileName) {
   }

   @Override
   public File getJavaFile() {
      throw new UnsupportedOperationException();
   }

   @Override
   public String toString() {
      return "MemorySequentialFile:" + fileName;
   }
}
