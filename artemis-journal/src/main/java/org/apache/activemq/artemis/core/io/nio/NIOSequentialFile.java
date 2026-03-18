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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.core.io.AbstractSequentialFile;
import org.apache.activemq.artemis.core.io.DelegateCallback;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.buffer.TimedBufferObserver;
import org.apache.activemq.artemis.journal.ActiveMQJournalBundle;
import org.apache.activemq.artemis.utils.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NIOSequentialFile extends AbstractSequentialFile {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final boolean DEBUG_OPENS = false;

   /*
    * This value has been tuned just to reduce the memory footprint
      of read/write of the whole file size: given that this value
      is > 8192, RandomAccessFile JNI code will use malloc/free instead
      of using a copy on the stack, but it has been proven to NOT be
      a bottleneck.

      Instead of reading the whole content in a single operation, this will read in smaller chunks.
    */
   private static final int CHUNK_SIZE = 2 * 1024 * 1024;

   protected volatile  FileChannel channel;

   protected volatile RandomAccessFile rfile;

   protected final int maxIO;

   public NIOSequentialFile(final SequentialFileFactory factory,
                            final File directory,
                            final String file,
                            final int maxIO) {
      super(directory, file, factory);
      this.maxIO = maxIO;
   }

   @Override
   protected TimedBufferObserver createTimedBufferObserver() {
      return new SyncLocalBufferObserver();
   }

   @Override
   public int calculateBlockStart(final int position) {
      return position;
   }

   @Override
   public synchronized boolean isOpen() {
      return channel != null;
   }

   /**
    * this.maxIO represents the default maxIO. Some operations while initializing files on the journal may require a
    * different maxIO
    */
   @Override
   public synchronized void open() throws IOException {
      open(maxIO, true);
   }

   @Override
   public ByteBuffer map(int position, long size) throws IOException {
      return channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
   }

   public static void clearDebug() {
      counters.clear();
   }

   public static void printDebug() {
      for (Map.Entry<String, AtomicInteger> entry : counters.entrySet()) {
         System.out.println(entry.getValue() + " " + entry.getKey());
      }
   }

   public static AtomicInteger getDebugCounter(Exception location) {
      StringWriter writer = new StringWriter();
      PrintWriter printWriter = new PrintWriter(writer);
      location.printStackTrace(printWriter);

      String strLocation = writer.toString();

      return getDebugCounter(strLocation);
   }

   public static AtomicInteger getDebugCounter(String strLocation) {
      AtomicInteger value = counters.get(strLocation);
      if (value == null) {
         value = new AtomicInteger(0);
         AtomicInteger oldvalue = counters.putIfAbsent(strLocation, value);
         if (oldvalue != null) {
            value = oldvalue;
         }
      }

      return value;
   }

   private static Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
   @Override
   public void open(final int maxIO, final boolean useExecutor) throws IOException {
      try {
         rfile = new RandomAccessFile(getFile(), "rw");

         channel = rfile.getChannel();

         fileSize = channel.size();

         if (DEBUG_OPENS) {
            getDebugCounter(new Exception("open")).incrementAndGet();
            getDebugCounter("open").incrementAndGet();
         }
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
         throw e;
      }
   }

   @Override
   public void fill(final int size) throws IOException {
      try {
         //uses the most common OS page size to match the Page Cache entry size and reduce JVM memory footprint
         final int zeroPageCapacity = Env.osPageSize();
         final ByteBuffer zeroPage = this.factory.newBuffer(zeroPageCapacity);
         try {
            int bytesToWrite = size;
            long writePosition = 0;
            while (bytesToWrite > 0) {
               zeroPage.clear();
               final int zeroPageLimit = Math.min(bytesToWrite, zeroPageCapacity);
               zeroPage.limit(zeroPageLimit);
               //use the cheaper pwrite instead of fseek + fwrite
               final int writtenBytes = channel.write(zeroPage, writePosition);
               bytesToWrite -= writtenBytes;
               writePosition += writtenBytes;
            }
            if (factory.isDatasync()) {
               channel.force(true);
            }
            //set the position to 0 to match the fill contract
            channel.position(0);
            fileSize = channel.size();
         } finally {
            //return it to the factory
            this.factory.releaseBuffer(zeroPage);
         }
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
         throw e;
      }
   }

   @Override
   public synchronized void close() throws IOException, InterruptedException, ActiveMQException {
      close(true, true);
   }

   @Override
   public synchronized void close(boolean waitSync, boolean blockOnPending) throws IOException, InterruptedException, ActiveMQException {
      super.close();

      if (DEBUG_OPENS) {
         getDebugCounter(new Exception("Close")).incrementAndGet();
         getDebugCounter("close").incrementAndGet();
      }

      try {
         try {
            if (channel != null) {
               if (waitSync && factory.isDatasync()) {
                  channel.force(false);
               }
               channel.close();
            }
         } finally {
            if (rfile != null) {
               rfile.close();
            }
         }
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
         throw e;
      } finally {
         channel = null;
         rfile = null;
      }


      notifyAll();
   }

   @Override
   public int read(final ByteBuffer bytes) throws Exception {
      return read(bytes, null);
   }


   private static int readRafInChunks(RandomAccessFile raf, byte[] b, int off, int len) throws IOException {
      int remaining = len;
      int offset = off;
      while (remaining > 0) {
         final int chunkSize = Math.min(CHUNK_SIZE, remaining);
         final int read = raf.read(b, offset, chunkSize);
         assert read != 0;
         if (read == -1) {
            if (len == remaining) {
               return -1;
            }
            break;
         }
         offset += read;
         remaining -= read;
      }
      return len - remaining;
   }

   private static void writeRafInChunks(RandomAccessFile raf, byte[] b, int off, int len) throws IOException {
      int remaining = len;
      int offset = off;
      while (remaining > 0) {
         final int chunkSize = Math.min(CHUNK_SIZE, remaining);
         raf.write(b, offset, chunkSize);
         offset += chunkSize;
         remaining -= chunkSize;
      }
   }

   @Override
   public synchronized int read(final ByteBuffer bytes,
                                final IOCallback callback) throws IOException, ActiveMQIllegalStateException {
      try {
         if (channel == null) {
            throw new ActiveMQIllegalStateException("File " + this.getFileName() + " has a null channel");
         }
         final int bytesRead;
         if (bytes.hasArray()) {
            if (bytes.remaining() > CHUNK_SIZE) {
               bytesRead = readRafInChunks(rfile, bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
            } else {
               bytesRead = rfile.read(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
            }
            if (bytesRead > 0) {
               bytes.position(bytes.position() + bytesRead);
            }
         } else {
            bytesRead = channel.read(bytes);
         }

         if (callback != null) {
            callback.done();
         }

         bytes.flip();

         return bytesRead;
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         if (callback != null) {
            callback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), e.getClass() + " during read: " + e.getLocalizedMessage());
         }

         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);

         throw e;
      }
   }

   @Override
   public void sync() throws IOException {
      FileChannel channel1 = channel;
      if (factory.isDatasync() && channel1 != null && channel1.isOpen()) {
         try {
            syncChannel(channel1);
         } catch (IOException e) {
            if (e instanceof ClosedChannelException) {
               // ClosedChannelException here means the file was closed after TimedBuffer issued a sync
               // we are performing the sync away from locks to ensure scalability and this is an expected cost
               logger.debug("ClosedChannelException for file {}", file, e);
            } else {
               factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
               throw e;
            }
         }
      }
   }

   protected void syncChannel(FileChannel syncChannel) throws IOException {
      syncChannel.force(false);
   }

   @Override
   public long size() throws IOException {
      if (channel == null) {
         return getFile().length();
      }

      try {
         return channel.size();
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
         throw e;
      }
   }

   @Override
   public void position(final long pos) throws IOException {
      try {
         super.position(pos);
         channel.position(pos);
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
         throw e;
      }
   }

   @Override
   public String toString() {
      return "NIOSequentialFile " + getFile();
   }

   @Override
   public SequentialFile cloneFile() {
      return new NIOSequentialFile(factory, directory, getFileName(), maxIO);
   }

   @Override
   public void writeDirect(final ByteBuffer bytes, final boolean sync, final IOCallback callback) {
      try {
         internalWrite(bytes, sync, callback, true);
      } catch (Exception e) {
         callback.onError(ActiveMQExceptionType.GENERIC_EXCEPTION.getCode(), e.getClass() + " during write direct: " + e.getMessage());
      }
   }

   @Override
   public void writeDirect(final ByteBuffer bytes, final boolean sync) throws Exception {
      internalWrite(bytes, sync, null, true);
   }

   @Override
   public void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) throws Exception {
      internalWrite(bytes, sync, null, releaseBuffer);
   }

   private synchronized void internalWrite(final ByteBuffer bytes,
                              final boolean sync,
                              final IOCallback callback,
                              boolean releaseBuffer) throws IOException, ActiveMQIOErrorException, InterruptedException {
      if (!isOpen()) {
         if (callback != null) {
            callback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), "File not opened. Cannot write to " + getFileName());
         } else {
            throw ActiveMQJournalBundle.BUNDLE.fileNotOpened();
         }
         return;
      }

      position.addAndGet(bytes.limit());

      try {
         doInternalWrite(bytes, sync, callback, releaseBuffer);
      } catch (ClosedChannelException e) {
         throw e;
      } catch (IOException e) {
         factory.onIOError(new ActiveMQIOErrorException(e.getMessage(), e), e.getMessage(), this);
      }
   }

   private void doInternalWrite(final ByteBuffer bytes,
                                final boolean sync,
                                final IOCallback callback,
                                boolean releaseBuffer) throws IOException {
      try {
         if (bytes.hasArray()) {
            if (bytes.remaining() > CHUNK_SIZE) {
               writeRafInChunks(rfile, bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
            } else {
               rfile.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
            }
            bytes.position(bytes.limit());
         } else {
            channel.write(bytes);
         }

         if (sync) {
            sync();
         }

         if (callback != null) {
            callback.done();
         }
      } finally {
         if (releaseBuffer) {
            //release it to recycle the write buffer if big enough
            this.factory.releaseBuffer(bytes);
         }
      }
   }

   @Override
   public void copyTo(SequentialFile dstFile) throws IOException {
      logger.debug("Copying {} as {}", this, dstFile);

      if (isOpen()) {
         throw new IllegalStateException("File opened!");
      }
      if (dstFile.isOpen()) {
         throw new IllegalArgumentException("dstFile must be closed too");
      }
      SequentialFile.appendTo(getFile().toPath(), dstFile.getJavaFile().toPath());
   }

   private class SyncLocalBufferObserver extends LocalBufferObserver {

      @Override
      public void flushBuffer(ByteBuf byteBuf, boolean requestedSync, List<IOCallback> callbacks) {
         //maybe no need to perform any copy
         final int bytes = byteBuf.readableBytes();
         if (bytes == 0) {
            IOCallback.done(callbacks);
         } else {
            //enable zero copy case
            if (byteBuf.nioBufferCount() == 1 && byteBuf.isDirect()) {
               final ByteBuffer buffer = byteBuf.internalNioBuffer(byteBuf.readerIndex(), bytes);
               final IOCallback callback = DelegateCallback.wrap(callbacks);
               try {
                  //no need to pool the buffer and don't care if the NIO buffer got modified
                  internalWrite(buffer, requestedSync, callback, false);
               } catch (Exception e) {
                  if (callbacks != null) {
                     callbacks.forEach(c -> c.onError(ActiveMQExceptionType.GENERIC_EXCEPTION.getCode(), e.getClass() + " while flushing buffer: " + e.getMessage()));
                  }
               }
            } else {
               super.flushBuffer(byteBuf, requestedSync, callbacks);
            }
         }
      }
   }
}
