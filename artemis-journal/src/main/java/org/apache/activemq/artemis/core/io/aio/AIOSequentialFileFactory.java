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
package org.apache.activemq.artemis.core.io.aio;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.core.io.AbstractSequentialFileFactory;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioFile;
import org.apache.activemq.artemis.nativo.jlibaio.SubmitInfo;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.PowerOf2Util;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

public final class AIOSequentialFileFactory extends AbstractSequentialFileFactory {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // This is useful in cases where you want to disable loading the native library. (e.g. testsuite)
   private static final boolean DISABLED = System.getProperty(AIOSequentialFileFactory.class.getName() + ".DISABLED") != null;

   static {
      // This is usually only used on testsuite.
      // In case it's used, I would rather have it on the loggers so we know what's happening
      if (DISABLED) {

         // This is only used in tests, hence I'm not creating a Logger for this
         logger.info("{}.DISABLED = true", AIOSequentialFileFactory.class.getName());
      }
   }

   private final ReuseBuffersController buffersControl = new ReuseBuffersController();

   private volatile boolean reuseBuffers = true;

   private Thread pollerThread;

   volatile LibaioContext<AIOSequentialCallback> libaioContext;

   private final Queue<AIOSequentialCallback> callbackPool;

   private final AtomicBoolean running = new AtomicBoolean(false);

   private static final String AIO_TEST_FILE = ".aio-test";

   @Override
   public boolean isSyncSupported() {
      return false;
   }

   public void beforeClose() {
   }

   public void afterClose() {
   }

   public AIOSequentialFileFactory(final File journalDir, int maxIO) {
      this(journalDir, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO, maxIO, false, null, null);
   }

   public AIOSequentialFileFactory(final File journalDir, final IOCriticalErrorListener listener, int maxIO) {
      this(journalDir, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO, maxIO, false, listener, null);
   }

   public AIOSequentialFileFactory(final File journalDir,
                                   final int bufferSize,
                                   final int bufferTimeout,
                                   final int maxIO,
                                   final boolean logRates) {
      this(journalDir, bufferSize, bufferTimeout, maxIO, logRates, null, null);
   }

   public AIOSequentialFileFactory(final File journalDir,
                                   final int bufferSize,
                                   final int bufferTimeout,
                                   final int maxIO,
                                   final boolean logRates,
                                   final IOCriticalErrorListener listener,
                                   final CriticalAnalyzer analyzer) {
      super(journalDir, true, bufferSize, bufferTimeout, maxIO, logRates, listener, analyzer);
      if (maxIO == 1) {
         logger.warn("Using journal-max-io 1 isn't a proper use of ASYNCIO journal: consider rise this value or use NIO.");
      }
      final int adjustedMaxIO = Math.max(2, maxIO);
      callbackPool = PlatformDependent.hasUnsafe() ? new MpmcArrayQueue<>(adjustedMaxIO) : new MpmcAtomicArrayQueue<>(adjustedMaxIO);
      logger.trace("New AIO File Created");
   }

   public AIOSequentialCallback getCallback() {
      AIOSequentialCallback callback = callbackPool.poll();
      if (callback == null) {
         callback = new AIOSequentialCallback();
      }

      return callback;
   }

   public void enableBufferReuse() {
      this.reuseBuffers = true;
   }

   public void disableBufferReuse() {
      this.reuseBuffers = false;
   }

   @Override
   public SequentialFile createSequentialFile(final String fileName) {
      return new AIOSequentialFile(this, bufferSize, bufferTimeout, journalDir, fileName);
   }

   @Override
   public boolean isSupportsCallbacks() {
      return true;
   }

   public static boolean isSupported() {
      return !DISABLED && LibaioContext.isLoaded();
   }

   public static boolean isSupported(File journalPath) {
      if (!isSupported()) {
         return false;
      }

      File aioTestFile = new File(journalPath, AIO_TEST_FILE);
      try {
         int fd = LibaioContext.open(aioTestFile.getAbsolutePath(), true);
         LibaioContext.close(fd);
         aioTestFile.delete();
      } catch (Exception e) {
         // try to handle the file using plain Java
         // return false if and only if we can create/remove the file using
         // plain Java but not using AIO
         try {
            if (!aioTestFile.exists()) {
               if (!aioTestFile.createNewFile()) {
                  return true;
               }
            }
            if (!aioTestFile.delete()) {
               return true;
            }
         } catch (Exception ie) {
            // we can not even create the test file using plain java
            return true;
         }
         return false;
      }
      return true;
   }

   @Override
   public ByteBuffer allocateDirectBuffer(final int size) {

      final int alignedSize = calculateBlockSize(size);

      // The buffer on AIO has to be a multiple of getAlignment()
      ByteBuffer buffer = LibaioContext.newAlignedBuffer(alignedSize, getAlignment());

      buffer.limit(size);

      return buffer;
   }

   @Override
   public void releaseDirectBuffer(final ByteBuffer buffer) {
      LibaioContext.freeBuffer(buffer);
   }

   @Override
   public ByteBuffer newBuffer(int size) {
      return newBuffer(size, true);
   }

   @Override
   public ByteBuffer newBuffer(int size, boolean zeroed) {
      final int alignedSize = calculateBlockSize(size);
      return buffersControl.newBuffer(alignedSize, zeroed);
   }

   @Override
   public void clearBuffer(final ByteBuffer directByteBuffer) {
      directByteBuffer.position(0);
      if (PlatformDependent.hasUnsafe()) {
         // that's the same semantic of libaioContext.memsetBuffer: it hasn't any JNI cost
         ByteUtil.zeros(directByteBuffer, 0, directByteBuffer.limit());
      } else {
         // JNI cost
         libaioContext.memsetBuffer(directByteBuffer);
      }
   }

   @Override
   public int getAlignment() {
      if (alignment < 0) {
         alignment = calculateAlignment(journalDir);
      }
      return alignment;
   }

   private static int calculateAlignment(File journalDir) {
      File checkFile = null;
      int alignment;
      try {
         journalDir.mkdirs();
         checkFile = File.createTempFile("journalCheck", ".tmp", journalDir);
         checkFile.mkdirs();
         checkFile.createNewFile();
         alignment = LibaioContext.getBlockSize(checkFile);
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         alignment = 512;
      } finally {
         if (checkFile != null) {
            checkFile.delete();
         }
      }
      return alignment;
   }

   // For tests only
   @Override
   public ByteBuffer wrapBuffer(final byte[] bytes) {
      ByteBuffer newbuffer = newBuffer(bytes.length);
      newbuffer.put(bytes);
      return newbuffer;
   }

   @Override
   public int calculateBlockSize(final int position) {
      final int alignment = getAlignment();
      if (!PowerOf2Util.isPowOf2(alignment)) {
         return align(position, alignment);
      } else {
         return PowerOf2Util.align(position, alignment);
      }
   }

   /**
    * It can be used to align {@code size} if alignment is not a power of 2: otherwise better to use
    * {@link PowerOf2Util#align(int, int)} instead.
    */
   private static int align(int size, int alignment) {
      return (size / alignment + (size % alignment != 0 ? 1 : 0)) * alignment;
   }

   @Override
   public synchronized void releaseBuffer(final ByteBuffer buffer) {
      LibaioContext.freeBuffer(buffer);
   }

   @Override
   public void start() {
      if (running.compareAndSet(false, true)) {
         super.start();

         this.libaioContext = new LibaioContext(maxIO, true, dataSync);

         this.running.set(true);

         pollerThread = new PollerThread();
         pollerThread.start();
      }

   }

   @Override
   public void stop() {
      if (this.running.compareAndSet(true, false)) {
         buffersControl.stop();

         libaioContext.close();
         libaioContext = null;

         if (pollerThread != null) {
            try {
               pollerThread.join(AbstractSequentialFileFactory.EXECUTOR_TIMEOUT * 1000);

               if (pollerThread.isAlive()) {
                  ActiveMQJournalLogger.LOGGER.timeoutOnPollerShutdown(new Exception("trace"));
               }
            } catch (InterruptedException e) {
               throw new ActiveMQInterruptedException(e);
            }
         }

         super.stop();
      }
   }

   /**
    * The same callback is used for Runnable executor. This way we can save some memory over the pool.
    */
   public class AIOSequentialCallback implements SubmitInfo, Runnable, Comparable<AIOSequentialCallback> {

      IOCallback callback;
      boolean error = false;
      AIOSequentialFile sequentialFile;
      ByteBuffer buffer;
      LibaioFile libaioFile;
      String errorMessage;
      int errorCode = -1;
      long writeSequence;
      boolean releaseBuffer;
      long position;
      int bytes;

      @Override
      public String toString() {
         return "AIOSequentialCallback{" +
            "error=" + error +
            ", errorMessage='" + errorMessage + '\'' +
            ", errorCode=" + errorCode +
            ", writeSequence=" + writeSequence +
            ", releaseBuffer=" + releaseBuffer +
            ", position=" + position +
            '}';
      }

      public AIOSequentialCallback initWrite(long positionToWrite, int bytesToWrite) {
         this.position = positionToWrite;
         this.bytes = bytesToWrite;
         return this;
      }

      @Override
      public void run() {
         try {
            libaioFile.write(position, bytes, buffer, this);
         } catch (IOException e) {
            callback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), e.getClass() + " during write to " + sequentialFile.getFileName() + ": " + e.getMessage());
            onIOError(e, "Failed to write to file", sequentialFile);
         }
      }

      @Override
      public int compareTo(AIOSequentialCallback other) {
         if (this == other || this.writeSequence == other.writeSequence) {
            return 0;
         } else if (other.writeSequence < this.writeSequence) {
            return 1;
         } else {
            return -1;
         }
      }

      public AIOSequentialCallback init(long writeSequence,
                                        IOCallback IOCallback,
                                        LibaioFile libaioFile,
                                        AIOSequentialFile sequentialFile,
                                        ByteBuffer usedBuffer,
                                        boolean releaseBuffer) {
         this.callback = IOCallback;
         this.sequentialFile = sequentialFile;
         this.error = false;
         this.buffer = usedBuffer;
         this.libaioFile = libaioFile;
         this.writeSequence = writeSequence;
         this.errorMessage = null;
         this.releaseBuffer = releaseBuffer;
         return this;
      }

      @Override
      public void onError(int errno, String message) {
         if (logger.isTraceEnabled()) {
            logger.trace("AIO on error issued. Error(code: {} msg: {})", errno, message);
         }

         this.error = true;
         this.errorCode = errno;
         this.errorMessage = message;
      }

      /**
       * this is called by libaio.
       */
      @Override
      public void done() {
         this.sequentialFile.done(this);
      }

      /**
       * This is callbed by the AIOSequentialFile, after determined the callbacks were returned in sequence
       */
      public void sequentialDone() {

         if (error) {
            if (callback != null) {
               callback.onError(errorCode, errorMessage);
            }
            onIOError(new ActiveMQException(errorCode, errorMessage), errorMessage);
            errorMessage = null;
         } else {
            if (callback != null) {
               callback.done();
            }

            if (buffer != null && reuseBuffers && releaseBuffer) {
               buffersControl.bufferDone(buffer);
            }

            callbackPool.offer(AIOSequentialCallback.this);
         }
      }
   }

   private class PollerThread extends Thread {

      private PollerThread() {
         super("activemq-libaio-poller");
      }

      @Override
      public void run() {
         while (running.get()) {
            // To optimize performance, libaioContext.poll should always be invoked from the same thread.
            // This approach leverages kernel-level efficiencies in context switching.
            // Consistent polling from a dedicated thread will yield substantial performance gains.
            try {
               libaioContext.poll();
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               onIOError(new ActiveMQException("Error on libaio poll"), e.getMessage());
            }
         }
      }
   }

   /**
    * Class that will control buffer-reuse
    */
   private class ReuseBuffersController {

      private volatile long bufferReuseLastTime = System.currentTimeMillis();

      private final Queue<ByteBuffer> reuseBuffersQueue = new ConcurrentLinkedQueue<>();

      private boolean stopped = false;

      private int alignedBufferSize = 0;

      private int getAlignedBufferSize() {
         if (alignedBufferSize == 0) {
            alignedBufferSize = calculateBlockSize(bufferSize);
         }

         return alignedBufferSize;
      }

      public ByteBuffer newBuffer(final int size, final boolean zeroed) {
         // if a new buffer wasn't requested in 10 seconds, we clear the queue
         // This is being done this way as we don't need another Timeout Thread
         // just to cleanup this
         if (bufferSize > 0 && System.currentTimeMillis() - bufferReuseLastTime > 10000) {
            if (logger.isTraceEnabled()) {
               logger.trace("Clearing reuse buffers queue with {} elements", reuseBuffersQueue.size());
            }

            bufferReuseLastTime = System.currentTimeMillis();

            clearPoll();
         }

         // if a buffer is bigger than the configured-bufferSize, we just create a new
         // buffer.
         if (size > getAlignedBufferSize()) {
            return LibaioContext.newAlignedBuffer(size, getAlignment());
         } else {
            // We need to allocate buffers following the rules of the storage
            // being used (AIO/NIO)
            final int alignedSize;

            if (size < getAlignedBufferSize()) {
               alignedSize = getAlignedBufferSize();
            } else {
               alignedSize = calculateBlockSize(size);
            }

            // Try getting a buffer from the queue...
            ByteBuffer buffer = reuseBuffersQueue.poll();

            if (buffer == null) {
               // if empty create a new one.
               buffer = LibaioContext.newAlignedBuffer(alignedSize, getAlignment());

               buffer.limit(calculateBlockSize(size));
            } else {
               if (zeroed) {
                  clearBuffer(buffer);
               } else {
                  buffer.position(0);
               }

               // set the limit of the buffer to the bufferSize being required
               buffer.limit(calculateBlockSize(size));
            }

            buffer.rewind();

            return buffer;
         }
      }

      public synchronized void stop() {
         stopped = true;
         clearPoll();
      }

      public synchronized void clearPoll() {
         ByteBuffer reusedBuffer;

         while ((reusedBuffer = reuseBuffersQueue.poll()) != null) {
            releaseBuffer(reusedBuffer);
         }
      }

      public void bufferDone(final ByteBuffer buffer) {
         synchronized (this) {

            if (stopped) {
               releaseBuffer(buffer);
            } else {
               bufferReuseLastTime = System.currentTimeMillis();

               // If a buffer has any other than the configured bufferSize, the buffer
               // will be just sent to GC
               if (buffer.capacity() == getAlignedBufferSize()) {
                  reuseBuffersQueue.offer(buffer);
               } else {
                  releaseBuffer(buffer);
               }
            }
         }
      }
   }

   @Override
   public String toString() {
      return AIOSequentialFileFactory.class.getSimpleName() + "(buffersControl.stopped=" + buffersControl.stopped +
         "):" + super.toString();
   }
}
