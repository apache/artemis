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
package org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.SimpleWaitIOCallback;

public class FakeSequentialFileFactory implements SequentialFileFactory {

   private final Map<String, FakeSequentialFile> fileMap = new ConcurrentHashMap<>();

   private volatile int alignment;

   private final boolean supportsCallback;


   private Runnable writeDirectCallback;

   private volatile boolean holdCallbacks;

   private ListenerHoldCallback holdCallbackListener;

   private volatile boolean generateErrors;

   private final List<CallbackRunnable> callbacksInHold;

   public FakeSequentialFileFactory(final int alignment, final boolean supportsCallback) {
      this.alignment = alignment;
      this.supportsCallback = supportsCallback;
      callbacksInHold = new ArrayList<>();
   }

   public FakeSequentialFileFactory() {
      this(1, false);
   }

   @Override
   public SequentialFileFactory setDatasync(boolean enabled) {
      return null;
   }

   @Override
   public boolean isDatasync() {
      return false;
   }

   @Override
   public long getBufferSize() {
      return ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO;
   }

   @Override
   public int getMaxIO() {
      return 1;
   }

   public void setWriteDirectCallback(Runnable writeDirectCallback) {
      this.writeDirectCallback = writeDirectCallback;
   }


   @Override
   public SequentialFile createSequentialFile(final String fileName) {
      FakeSequentialFile sf = fileMap.get(fileName);

      if (sf == null || sf.data == null) {
         sf = newSequentialFile(fileName);

         fileMap.put(fileName, sf);
      } else {
         sf.getData().position(0);

         // log.debug("positioning data to 0");
      }

      return sf;
   }

   @Override
   public void clearBuffer(final ByteBuffer buffer) {
      final int limit = buffer.limit();
      buffer.rewind();

      for (int i = 0; i < limit; i++) {
         buffer.put((byte) 0);
      }

      buffer.rewind();
   }

   @Override
   public List<String> listFiles(final String extension) {
      List<String> files = new ArrayList<>();

      for (String s : fileMap.keySet()) {
         if (s.endsWith("." + extension)) {
            files.add(s);
         }
      }

      return files;
   }

   public Map<String, FakeSequentialFile> getFileMap() {
      return fileMap;
   }

   public void clear() {
      fileMap.clear();
   }

   @Override
   public boolean isSupportsCallbacks() {
      return supportsCallback;
   }

   @Override
   public ByteBuffer newBuffer(int size) {
      if (size % alignment != 0) {
         size = (size / alignment + 1) * alignment;
      }
      return ByteBuffer.allocate(size);
   }

   @Override
   public int calculateBlockSize(final int position) {
      int alignment = getAlignment();

      int pos = (position / alignment + (position % alignment != 0 ? 1 : 0)) * alignment;

      return pos;
   }

   @Override
   public ByteBuffer wrapBuffer(final byte[] bytes) {
      return ByteBuffer.wrap(bytes);
   }

   public synchronized boolean isHoldCallbacks() {
      return holdCallbacks;
   }

   public synchronized void setHoldCallbacks(final boolean holdCallbacks,
                                             final ListenerHoldCallback holdCallbackListener) {
      this.holdCallbacks = holdCallbacks;
      this.holdCallbackListener = holdCallbackListener;
   }

   public boolean isGenerateErrors() {
      return generateErrors;
   }

   public void setGenerateErrors(final boolean generateErrors) {
      this.generateErrors = generateErrors;
   }

   public synchronized void flushAllCallbacks() {
      for (Runnable action : callbacksInHold) {
         action.run();
      }

      callbacksInHold.clear();
   }

   public synchronized void flushCallback(final int position) {
      Runnable run = callbacksInHold.get(position);
      run.run();
      callbacksInHold.remove(run);
   }

   public synchronized void setCallbackAsError(final int position) {
      callbacksInHold.get(position).setSendError(true);
   }

   public synchronized int getNumberOfCallbacks() {
      return callbacksInHold.size();
   }

   @Override
   public int getAlignment() {
      return alignment;
   }

   @Override
   public FakeSequentialFileFactory setAlignment(int alignment) {
      this.alignment = alignment;
      return this;
   }



   protected FakeSequentialFile newSequentialFile(final String fileName) {
      return new FakeSequentialFile(fileName);
   }

   /**
    * This listener will return a message to the test with each callback added
    */
   public interface ListenerHoldCallback {

      void callbackAdded(ByteBuffer bytes);
   }

   private class CallbackRunnable implements Runnable {

      final FakeSequentialFile file;

      final ByteBuffer bytes;

      final IOCallback callback;

      volatile boolean sendError;

      CallbackRunnable(final FakeSequentialFile file, final ByteBuffer bytes, final IOCallback callback) {
         this.file = file;
         this.bytes = bytes;
         this.callback = callback;
      }

      @Override
      public void run() {

         if (sendError) {
            callback.onError(ActiveMQExceptionType.UNSUPPORTED_PACKET.getCode(), "Fake aio error");
         } else {
            try {
               file.data.put(bytes);
               if (callback != null) {
                  callback.done();
               }
            } catch (Throwable e) {
               e.printStackTrace();
               callback.onError(ActiveMQExceptionType.GENERIC_EXCEPTION.getCode(), e.getMessage());
            }
         }
      }

      public void setSendError(final boolean sendError) {
         this.sendError = sendError;
      }
   }

   public class FakeSequentialFile implements SequentialFile {

      private volatile boolean open;

      private String fileName;

      private ByteBuffer data;

      public ByteBuffer getData() {
         return data;
      }

      @Override
      public boolean isOpen() {
         return open;
      }

      public void flush() {
      }

      public FakeSequentialFile(final String fileName) {
         this.fileName = fileName;
      }

      @Override
      public synchronized void close() {
         open = false;

         if (data != null) {
            data.position(0);
         }

         notifyAll();
      }

      @Override
      public ByteBuffer map(int position, long size) throws IOException {
         return null;
      }

      @Override
      public void delete() {
         fileMap.remove(fileName);
         if (open) {
            close();
         }
      }

      @Override
      public String getFileName() {
         return fileName;
      }

      @Override
      public void open() throws Exception {
         open(1, true);
      }

      @Override
      public synchronized void open(final int currentMaxIO, final boolean useExecutor) throws Exception {
         open = true;
         checkAndResize(0);
      }

      @Override
      public void fill(final int size) throws Exception {
         if (!open) {
            throw new IllegalStateException("Is closed");
         }

         checkAndResize(size);

         // log.debug("size is {} pos is {}", size, pos);

         for (int i = 0; i < size; i++) {
            byte[] array = data.array();
            array[i] = 0;

            // log.debug("Filling {} with char {}", pos, fillCharacter);
         }
      }

      @Override
      public int read(final ByteBuffer bytes) throws Exception {
         return read(bytes, null);
      }

      @Override
      public int read(final ByteBuffer bytes, final IOCallback callback) throws Exception {
         if (!open) {
            throw new IllegalStateException("Is closed");
         }

         byte[] bytesRead = new byte[Math.min(bytes.remaining(), data.remaining())];

         data.get(bytesRead);

         bytes.put(bytesRead);

         bytes.rewind();

         if (callback != null) {
            callback.done();
         }

         return bytesRead.length;
      }

      @Override
      public void position(final long pos) {
         if (!open) {
            throw new IllegalStateException("Is closed");
         }

         checkAlignment(pos);

         data.position((int) pos);
      }

      @Override
      public long position() {
         return data.position();
      }

      @Override
      public synchronized void writeDirect(final ByteBuffer bytes, final boolean sync, final IOCallback callback) {
         if (!open) {
            throw new IllegalStateException("Is closed");
         }

         final int position = data == null ? 0 : data.position();

         // checkAlignment(position);

         // checkAlignment(bytes.limit());

         checkAndResize(bytes.limit() + position);

         CallbackRunnable action = new CallbackRunnable(this, bytes, callback);

         if (generateErrors) {
            action.setSendError(true);
         }

         if (holdCallbacks) {
            addCallback(bytes, action);
         } else {
            action.run();
         }

         if (writeDirectCallback != null) {
            writeDirectCallback.run();
         }

      }

      @Override
      public synchronized void blockingWriteDirect(ByteBuffer bytes,
                                                   boolean sync,
                                                   boolean releaseBuffer) throws Exception {
         SimpleWaitIOCallback callback = new SimpleWaitIOCallback();
         try {
            writeDirect(bytes, sync, callback);
         } finally {
            callback.waitCompletion();
         }
      }

      @Override
      public void sync() throws IOException {
         if (supportsCallback) {
            throw new IllegalStateException("sync is not supported when supportsCallback=true");
         }
      }

      @Override
      public long size() throws Exception {
         if (data == null) {
            return 0;
         } else {
            return data.limit();
         }
      }

      @Override
      public void writeDirect(final ByteBuffer bytes, final boolean sync) throws Exception {
         writeDirect(bytes, sync, null);
      }

      public void writeInternal(ByteBuffer bytes) throws Exception {
         writeDirect(bytes, true);
      }

      private void checkAndResize(final int size) {
         int oldpos = data == null ? 0 : data.position();

         if (data == null || data.array().length < size) {
            byte[] newBytes = new byte[size];

            if (data != null) {
               System.arraycopy(data.array(), 0, newBytes, 0, data.array().length);
            }

            data = ByteBuffer.wrap(newBytes);

            data.position(oldpos);
         }
      }

      private void addCallback(final ByteBuffer bytes, final CallbackRunnable action) {
         synchronized (FakeSequentialFileFactory.this) {
            callbacksInHold.add(action);
            if (holdCallbackListener != null) {
               holdCallbackListener.callbackAdded(bytes);
            }
         }
      }

      @Override
      public int calculateBlockStart(final int position) throws Exception {
         int pos = (position / alignment + (position % alignment != 0 ? 1 : 0)) * alignment;

         return pos;
      }

      @Override
      public String toString() {
         return "FakeSequentialFile:" + fileName;
      }

      private void checkAlignment(final long position) {
         if (position % alignment != 0) {
            throw new IllegalStateException("Position is not aligned to " + alignment);
         }
      }

      @Override
      public void renameTo(final String newFileName) throws Exception {
         fileMap.remove(fileName);
         fileName = newFileName;
         fileMap.put(newFileName, this);
      }

      @Override
      public boolean fits(final int size) {
         return data.position() + size <= data.limit();
      }

      public void setBuffering(final boolean buffering) {
      }

      public void disableAutoFlush() {
      }

      public void enableAutoFlush() {
      }

      @Override
      public SequentialFile cloneFile() {
         return null; // To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void write(final ActiveMQBuffer bytes, final boolean sync, final IOCallback callback) throws Exception {
         bytes.writerIndex(bytes.capacity());
         bytes.readerIndex(0);
         writeDirect(bytes.toByteBuffer(), sync, callback);

      }

      @Override
      public void write(final ActiveMQBuffer bytes, final boolean sync) throws Exception {
         bytes.writerIndex(bytes.capacity());
         bytes.readerIndex(0);
         writeDirect(bytes.toByteBuffer(), sync);
      }

      @Override
      public void write(final EncodingSupport bytes, final boolean sync, final IOCallback callback) throws Exception {
         ByteBuffer buffer = newBuffer(bytes.getEncodeSize());
         ActiveMQBuffer outbuffer = ActiveMQBuffers.wrappedBuffer(buffer);
         bytes.encode(outbuffer);
         write(outbuffer, sync, callback);
      }

      @Override
      public void write(final EncodingSupport bytes, final boolean sync) throws Exception {
         ByteBuffer buffer = newBuffer(bytes.getEncodeSize());
         ActiveMQBuffer outbuffer = ActiveMQBuffers.wrappedBuffer(buffer);
         bytes.encode(outbuffer);
         write(outbuffer, sync);
      }

      @Override
      public boolean exists() {
         FakeSequentialFile file = fileMap.get(fileName);

         return file != null && file.data != null && file.data.capacity() > 0;
      }

      @Override
      public void setTimedBuffer(final TimedBuffer buffer) {
      }

      @Override
      public void copyTo(SequentialFile newFileName) {
      }

      @Override
      public File getJavaFile() {
         throw new UnsupportedOperationException();
      }
   }

   @Override
   public void createDirs() throws Exception {
      // nothing to be done on the fake Sequential file
   }

   @Override
   public void releaseBuffer(final ByteBuffer buffer) {
   }

   @Override
   public void stop() {
   }

   @Override
   public void activateBuffer(final SequentialFile file) {
   }

   @Override
   public void start() {
   }

   @Override
   public void deactivateBuffer() {
   }

   @Override
   public void flush() {
   }

   @Override
   public void onIOError(Throwable exception, String message, String file) {
   }

   @Override
   public ByteBuffer allocateDirectBuffer(int size) {
      return ByteBuffer.allocateDirect(size);
   }

   @Override
   public void releaseDirectBuffer(ByteBuffer buffer) {
   }

   @Override
   public File getDirectory() {
      return null;
   }

}
