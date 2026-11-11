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
package org.apache.activemq.artemis.core.io;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;

public interface SequentialFile {

   boolean isOpen();

   boolean exists();

   void open() throws Exception;

   default void afterComplete(Runnable run) {
      run.run();
   }

   /**
    * The maximum number of simultaneous writes accepted
    */
   void open(int maxIO, boolean useExecutor) throws Exception;

   ByteBuffer map(int position, long size) throws IOException;

   boolean fits(int size);

   int calculateBlockStart(int position) throws Exception;

   String getFileName();

   void fill(int size) throws Exception;

   void delete() throws IOException, InterruptedException, ActiveMQException;

   void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback) throws Exception;

   void write(ActiveMQBuffer bytes, boolean sync) throws Exception;

   void write(EncodingSupport bytes, boolean sync, IOCallback callback) throws Exception;

   void write(EncodingSupport bytes, boolean sync) throws Exception;

   /**
    * Write directly to the file without using any buffer
    *
    * @param bytes the ByteBuffer must be compatible with the SequentialFile implementation (AIO or NIO). To be safe,
    *              use a buffer from the corresponding {@link SequentialFileFactory#newBuffer(int)}.
    */
   void writeDirect(ByteBuffer bytes, boolean sync, IOCallback callback);

   /**
    * Write directly to the file without using intermediate any buffer
    *
    * @param bytes the ByteBuffer must be compatible with the SequentialFile implementation (AIO or NIO). To be safe,
    *              use a buffer from the corresponding {@link SequentialFileFactory#newBuffer(int)}.
    */
   void writeDirect(ByteBuffer bytes, boolean sync) throws Exception;

   /**
    * Write directly to the file without using any intermediate buffer and wait completion.
    * <p>
    * If {@code releaseBuffer} is {@code true} the provided {@code bytes} should be released through
    * {@link SequentialFileFactory#releaseBuffer(ByteBuffer)}, if supported.
    *
    * @param bytes         the ByteBuffer must be compatible with the SequentialFile implementation (AIO or NIO). If
    *                      {@code releaseBuffer} is {@code true} use a buffer from
    *                      {@link SequentialFileFactory#newBuffer(int)},
    *                      {@link SequentialFileFactory#allocateDirectBuffer(int)} otherwise.
    * @param sync          if {@code true} will durable flush the written data on the file, {@code false} otherwise
    * @param releaseBuffer if {@code true} will release the buffer, {@code false} otherwise
    */
   void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) throws Exception;

   /**
    * Read the file.
    *
    * @param bytes the ByteBuffer must be compatible with the SequentialFile implementation (AIO or NIO). To be safe,
    *              use a buffer from the corresponding {@link SequentialFileFactory#newBuffer(int)}.
    */
   int read(ByteBuffer bytes, IOCallback callback) throws Exception;

   /**
    * Read the file.
    *
    * @param bytes the ByteBuffer must be compatible with the SequentialFile implementation (AIO or NIO). To be safe,
    *              use a buffer from the corresponding {@link SequentialFileFactory#newBuffer(int)}.
    */
   int read(ByteBuffer bytes) throws Exception;

   void position(long pos) throws IOException;

   long position();

   void close() throws Exception;

   /**
    * When closing a file from a finalize block, you cant wait on syncs or anything like that. otherwise the VM may
    * hung. Especially on the testsuite.
    */
   default void close(boolean waitSync, boolean blockOnWait) throws Exception {
      // by default most implementations are just using the regular close..
      // if the close needs sync, please use this parameter or fianlizations may get stuck
      close();
   }

   void sync() throws IOException;

   long size() throws Exception;

   void renameTo(String newFileName) throws Exception;

   SequentialFile cloneFile();

   void copyTo(SequentialFile newFileName) throws Exception;

   void setTimedBuffer(TimedBuffer buffer);

   /**
    * {@return a native {@code File} of the file underlying this sequential file}
    */
   File getJavaFile();

   static long appendTo(Path src, Path dst) throws IOException {
      try (FileChannel srcChannel = FileChannel.open(src, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE); FileLock srcLock = srcChannel.lock()) {
         final long readableBytes = srcChannel.size();
         if (readableBytes > 0) {
            try (FileChannel dstChannel = FileChannel.open(dst, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE); FileLock dstLock = dstChannel.lock()) {
               final long oldLength = dstChannel.size();
               final long transferred = dstChannel.transferFrom(srcChannel, oldLength, readableBytes);
               if (transferred != readableBytes) {
                  dstChannel.truncate(oldLength);
                  throw new IOException("copied less then expected");
               }
               return transferred;
            }
         }
         return 0;
      }
   }
}
