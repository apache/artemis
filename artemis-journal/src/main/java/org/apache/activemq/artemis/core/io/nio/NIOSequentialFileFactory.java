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
package org.apache.activemq.artemis.core.io.nio;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.core.io.AbstractSequentialFileFactory;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.util.ByteBufferPool;
import org.apache.activemq.artemis.utils.PowerOf2Util;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;

public class NIOSequentialFileFactory extends AbstractSequentialFileFactory {

   private static final int DEFAULT_CAPACITY_ALIGNMENT = Env.osPageSize();

   private boolean bufferPooling;

   private final ByteBufferPool bytesPool;

   public NIOSequentialFileFactory(final File journalDir, final int maxIO) {
      this(journalDir, null, maxIO);
   }

   public NIOSequentialFileFactory(final File journalDir, final IOCriticalErrorListener listener, final int maxIO) {
      this(journalDir, false, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_NIO, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO, maxIO, false, listener, null);
   }

   public NIOSequentialFileFactory(final File journalDir, final boolean buffered, final int maxIO) {
      this(journalDir, buffered, null, maxIO);
   }

   public NIOSequentialFileFactory(final File journalDir,
                                   final boolean buffered,
                                   final IOCriticalErrorListener listener,
                                   final int maxIO) {
      this(journalDir, buffered, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_NIO, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO, maxIO, false, listener, null);
   }

   public NIOSequentialFileFactory(final File journalDir,
                                   final boolean buffered,
                                   final int bufferSize,
                                   final int bufferTimeout,
                                   final int maxIO,
                                   final boolean logRates) {
      this(journalDir, buffered, bufferSize, bufferTimeout, maxIO, logRates, null, null);
   }

   public NIOSequentialFileFactory(final File journalDir,
                                   final boolean buffered,
                                   final int bufferSize,
                                   final int bufferTimeout,
                                   final int maxIO,
                                   final boolean logRates,
                                   final IOCriticalErrorListener listener,
                                   final CriticalAnalyzer analyzer) {
      super(journalDir, buffered, bufferSize, bufferTimeout, maxIO, logRates, listener, analyzer);
      this.bufferPooling = true;
      this.bytesPool = ByteBufferPool.threadLocal(true);
   }

   public static ByteBuffer allocateDirectByteBuffer(final int size) {
      // Using direct buffer
      ByteBuffer buffer2 = null;
      try {
         buffer2 = ByteBuffer.allocateDirect(size);
      } catch (OutOfMemoryError error) {
         // This is a workaround for the way the JDK will deal with native buffers.
         // the main portion is outside of the VM heap
         // and the JDK will not have any reference about it to take GC into account
         // so we force a GC and try again.
         WeakReference<Object> obj = new WeakReference<>(new Object());
         try {
            long timeout = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() > timeout && obj.get() != null) {
               System.gc();
               Thread.sleep(100);
            }
         } catch (InterruptedException e) {
         }

         buffer2 = ByteBuffer.allocateDirect(size);

      }
      return buffer2;
   }

   public void enableBufferReuse() {
      this.bufferPooling = true;
   }

   public void disableBufferReuse() {
      this.bufferPooling = false;
   }

   @Override
   public SequentialFile createSequentialFile(final String fileName) {
      return new NIOSequentialFile(this, journalDir, fileName, maxIO);
   }

   @Override
   public boolean isSupportsCallbacks() {
      return timedBuffer != null;
   }

   @Override
   public ByteBuffer allocateDirectBuffer(final int size) {
      final int requiredCapacity = PowerOf2Util.align(size, DEFAULT_CAPACITY_ALIGNMENT);
      final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(requiredCapacity);
      byteBuffer.limit(size);
      return byteBuffer;
   }

   @Override
   public void releaseDirectBuffer(ByteBuffer buffer) {
      if (buffer.isDirect()) {
         PlatformDependent.freeDirectBuffer(buffer);
      }
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
      } else {
         releaseDirectBuffer(buffer);
      }
   }

   @Override
   public void clearBuffer(final ByteBuffer buffer) {
      if (buffer.isDirect()) {
         PlatformDependent.setMemory(PlatformDependent.directBufferAddress(buffer), buffer.limit(), (byte) 0);
      } else {
         Arrays.fill(buffer.array(), buffer.arrayOffset(), buffer.limit(), (byte) 0);
      }
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
   public int calculateBlockSize(final int bytes) {
      return bytes;
   }

}
