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
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNativeIOError;
import org.apache.activemq.artemis.core.io.AbstractSequentialFile;
import org.apache.activemq.artemis.core.io.DummyCallback;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.journal.impl.SimpleWaitIOCallback;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is implementing Runnable to reuse a callback to close it.
 */
public class AIOSequentialFile extends AbstractSequentialFile {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean opened = false;

   private LibaioFile aioFile;

   private final AIOSequentialFileFactory aioFactory;

   /**
    * Used to determine the next writing sequence
    */
   private final AtomicLong nextWritingSequence = new AtomicLong(0);

   /**
    * AIO can't guarantee ordering over callbacks.
    * <p>
    * We use this {@link PriorityQueue} to hold values until they are in order
    */
   final PriorityQueue<AIOSequentialFileFactory.AIOSequentialCallback> pendingCallbackList = new PriorityQueue<>();

   /**
    * Used to determine the next writing sequence. This is accessed from a single thread (the Poller Thread)
    */
   private long nextReadSequence = 0;

   public AIOSequentialFile(final AIOSequentialFileFactory factory,
                            final int bufferSize,
                            final long bufferTimeoutMilliseconds,
                            final File directory,
                            final String fileName) {
      super(directory, fileName, factory);
      this.aioFactory = factory;
   }

   @Override
   public ByteBuffer map(int position, long size) throws IOException {
      return null;
   }

   @Override
   public boolean isOpen() {
      return opened;
   }

   @Override
   public int calculateBlockStart(final int position) {
      return factory.calculateBlockSize(position);
   }

   @Override
   public SequentialFile cloneFile() {
      return new AIOSequentialFile(aioFactory, -1, -1, getFile().getParentFile(), getFile().getName());
   }


   @Override
   public void close() throws IOException, InterruptedException, ActiveMQException {
      close(true, true);
   }

   @Override
   public synchronized void close(boolean waitSync, boolean blockOnWait) throws IOException, InterruptedException, ActiveMQException {
      // a double call on close, should result on it waitingNotPending before another close is called
      if (!opened) {
         return;
      }

      aioFactory.beforeClose();

      super.close();
      opened = false;
      this.timedBuffer = null;

      try {
         aioFile.close();
      } catch (Throwable e) {
         // an exception here would means a double
         logger.debug("Exeption while closing file", e);
      } finally {
         aioFile = null;
         aioFactory.afterClose();
      }
   }

   @Override
   public synchronized void fill(final int size) throws Exception {
      logger.trace("Filling file: {}", getFileName());

      checkOpened();
      aioFile.fill(aioFactory.getAlignment(), size);

      fileSize = aioFile.getSize();
   }

   @Override
   public void open() throws Exception {
      open(aioFactory.getMaxIO(), true);
   }

   @Override
   public synchronized void open(final int maxIO, final boolean useExecutor) throws ActiveMQException {
      // in case we are opening a file that was just closed, we need to wait previous executions to be done
      if (opened) {
         return;
      }
      opened = true;

      logger.trace("Opening file: {}", getFileName());

      try {
         aioFile = aioFactory.libaioContext.openFile(getFile(), factory.isDatasync());
      } catch (IOException e) {
         logger.error("Error opening file: {}", getFileName());
         factory.onIOError(e, e.getMessage(), this);
         throw new ActiveMQNativeIOError(e.getMessage(), e);
      }

      position.set(0);

      fileSize = aioFile.getSize();
   }

   @Override
   public int read(final ByteBuffer bytes, final IOCallback callback) throws ActiveMQException {
      checkOpened();
      int bytesToRead = bytes.limit();

      long positionToRead = position.getAndAdd(bytesToRead);

      bytes.rewind();

      try {
         // We don't send the buffer to the callback on read,
         // because we want the buffer available.
         // Sending it through the callback would make it released
         aioFile.read(positionToRead, bytesToRead, bytes, getCallback(callback, null));
      } catch (IOException e) {
         logger.error("IOError reading file: {}", getFileName(), e);
         factory.onIOError(e, e.getMessage(), this);
         throw new ActiveMQNativeIOError(e.getMessage(), e);
      }

      return bytesToRead;
   }

   @Override
   public int read(final ByteBuffer bytes) throws Exception {
      SimpleWaitIOCallback waitCompletion = new SimpleWaitIOCallback();

      int bytesRead = read(bytes, waitCompletion);

      waitCompletion.waitCompletion();

      return bytesRead;
   }

   @Override
   public void writeDirect(final ByteBuffer bytes, final boolean sync) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Write Direct, Sync: {} File: {}", sync, getFileName());
      }

      if (sync) {
         SimpleWaitIOCallback completion = new SimpleWaitIOCallback();

         writeDirect(bytes, true, completion);

         completion.waitCompletion();
      } else {
         writeDirect(bytes, false, DummyCallback.getInstance());
      }
   }

   @Override
   public void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) throws Exception {
      logger.trace("Write Direct, Sync: true File: {}", getFileName());

      final SimpleWaitIOCallback completion = new SimpleWaitIOCallback();

      try {
         checkOpened();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         completion.onError(-1, e.getClass() + " during blocking write direct: " + e.getMessage());
         return;
      }

      final int bytesToWrite = factory.calculateBlockSize(bytes.limit());

      final long positionToWrite = position.getAndAdd(bytesToWrite);

      final AIOSequentialFileFactory.AIOSequentialCallback runnableCallback = getCallback(completion, bytes, releaseBuffer);
      runnableCallback.initWrite(positionToWrite, bytesToWrite);
      runnableCallback.run();

      completion.waitCompletion();
   }

   /**
    * Note: Parameter sync is not used on AIO
    */
   @Override
   public void writeDirect(final ByteBuffer bytes, final boolean sync, final IOCallback callback) {
      try {
         checkOpened();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         callback.onError(-1, e.getClass() + " during write direct: " + e.getMessage());
         return;
      }

      final int bytesToWrite = factory.calculateBlockSize(bytes.limit());

      final long positionToWrite = position.getAndAdd(bytesToWrite);

      AIOSequentialFileFactory.AIOSequentialCallback runnableCallback = getCallback(callback, bytes);
      runnableCallback.initWrite(positionToWrite, bytesToWrite);
      runnableCallback.run();
   }

   AIOSequentialFileFactory.AIOSequentialCallback getCallback(IOCallback originalCallback, ByteBuffer buffer) {
      return getCallback(originalCallback, buffer, true);
   }

   AIOSequentialFileFactory.AIOSequentialCallback getCallback(IOCallback originalCallback,
                                                              ByteBuffer buffer,
                                                              boolean releaseBuffer) {
      AIOSequentialFileFactory.AIOSequentialCallback callback = aioFactory.getCallback();
      callback.init(this.nextWritingSequence.getAndIncrement(), originalCallback, aioFile, this, buffer, releaseBuffer);
      return callback;
   }

   void done(AIOSequentialFileFactory.AIOSequentialCallback callback) {
      if (callback.writeSequence == -1) {
         callback.sequentialDone();
      }

      if (callback.writeSequence == nextReadSequence) {
         nextReadSequence++;
         try {
            callback.sequentialDone();
         } finally {
            flushCallbacks();
         }
      } else {
         pendingCallbackList.add(callback);
      }

   }

   private void flushCallbacks() {
      while (!pendingCallbackList.isEmpty() && pendingCallbackList.peek().writeSequence == nextReadSequence) {
         AIOSequentialFileFactory.AIOSequentialCallback callback = pendingCallbackList.poll();
         try {
            callback.sequentialDone();
         } finally {
            nextReadSequence++;
         }
      }
   }

   @Override
   public void sync() {
      throw new UnsupportedOperationException("This method is not supported on AIO");
   }

   @Override
   public long size() throws Exception {
      if (aioFile == null) {
         return getFile().length();
      } else {
         return aioFile.getSize();
      }
   }

   @Override
   public String toString() {
      return "AIOSequentialFile{" + getFileName() + ", opened=" + opened + '}';
   }

   private void checkOpened() {
      if (aioFile == null || !opened) {
         throw new NullPointerException("File not opened, file=null on fileName = " + getFileName());
      }
   }

}
