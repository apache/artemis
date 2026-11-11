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
import java.nio.ByteBuffer;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.core.io.AbstractSequentialFileFactory;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.util.ByteBufferPool;
import org.apache.activemq.artemis.utils.PowerOf2Util;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.Env;

public final class MappedSequentialFileFactory extends AbstractSequentialFileFactory {

   private int capacity;
   private boolean bufferPooling;
   private final ByteBufferPool bytesPool;

   public MappedSequentialFileFactory(File directory,
                                       int capacity,
                                       final boolean buffered,
                                       final int bufferSize,
                                       final int bufferTimeout,
                                       IOCriticalErrorListener criticalErrorListener) {

      // at the moment we only use the critical analyzer on the timed buffer
      // MappedSequentialFile is not using any buffering, hence we just pass in null
      super(directory, buffered, bufferSize, bufferTimeout, 1, false, criticalErrorListener, null);

      this.capacity = capacity;
      this.setDatasync(true);
      this.bufferPooling = true;
      this.bytesPool = ByteBufferPool.threadLocal(true);
   }

   public MappedSequentialFileFactory capacity(int capacity) {
      this.capacity = capacity;
      return this;
   }

   public int capacity() {
      return capacity;
   }

   @Override
   public SequentialFile createSequentialFile(String fileName) {
      return createSequentialFile(fileName, capacity);
   }

   @Override
   public SequentialFile createSequentialFile(String fileName, int capacity) {
      final MappedSequentialFile mappedSequentialFile = new MappedSequentialFile(this, journalDir, new File(journalDir, fileName), capacity, critialErrorListener);
      if (this.timedBuffer == null) {
         return mappedSequentialFile;
      } else {
         return new TimedSequentialFile(this, mappedSequentialFile);
      }
   }

   @Override
   public boolean isSupportsCallbacks() {
      return timedBuffer != null;
   }

   @Override
   public ByteBuffer allocateDirectBuffer(final int size) {
      final int requiredCapacity = PowerOf2Util.align(size, Env.osPageSize());
      final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(requiredCapacity);
      byteBuffer.limit(size);
      return byteBuffer;
   }

   @Override
   public void releaseDirectBuffer(ByteBuffer buffer) {
      PlatformDependent.freeDirectBuffer(buffer);
   }

   public MappedSequentialFileFactory enableBufferReuse() {
      this.bufferPooling = true;
      return this;
   }

   public MappedSequentialFileFactory disableBufferReuse() {
      this.bufferPooling = false;
      return this;
   }

   @Override
   public ByteBuffer newBuffer(int size) {
      return newBuffer(size, true);
   }

   @Override
   public ByteBuffer newBuffer(int size, boolean zeroed) {
      if (!this.bufferPooling) {
         return allocateDirectBuffer(size);
      } else {
         return bytesPool.borrow(size, zeroed);
      }
   }

   @Override
   public void releaseBuffer(ByteBuffer buffer) {
      if (this.bufferPooling) {
         bytesPool.release(buffer);
      }
   }

   @Override
   public MappedSequentialFileFactory setDatasync(boolean enabled) {
      super.setDatasync(enabled);
      return this;
   }


   @Override
   public ByteBuffer wrapBuffer(final byte[] bytes) {
      return ByteBuffer.wrap(bytes);
   }

   @Override
   public int getAlignment() {
      return 1;
   }

   @Override
   @Deprecated
   public MappedSequentialFileFactory setAlignment(int alignment) {
      throw new UnsupportedOperationException("alignment can't be changed!");
   }

   @Override
   public int calculateBlockSize(int bytes) {
      return bytes;
   }

   @Override
   public void clearBuffer(final ByteBuffer buffer) {
      ByteUtil.zeros(buffer);
      buffer.rewind();
   }

   @Override
   public void start() {
      if (timedBuffer != null) {
         timedBuffer.start();
      }
   }

   @Override
   public void stop() {
      if (timedBuffer != null) {
         timedBuffer.stop();
      }
   }

}
