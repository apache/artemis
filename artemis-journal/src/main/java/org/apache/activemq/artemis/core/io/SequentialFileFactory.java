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
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;

/**
 * A SequentialFileFactory
 */
public interface SequentialFileFactory {

   default IOCriticalErrorListener getCriticalErrorListener() {
      return null;
   }

   default void setCriticalErrorListener(IOCriticalErrorListener listener) {
   }

   default CriticalAnalyzer getCriticalAnalyzer() {
      return null;
   }

   SequentialFile createSequentialFile(String fileName);

   default SequentialFile createSequentialFile(String fileName, int capacity) {
      return createSequentialFile(fileName);
   }

   int getMaxIO();

   default boolean isSyncSupported() {
      return true;
   }

   /**
    * Lists files that end with the given extension.
    * <p>
    * This method inserts a ".' before the extension.
    */
   List<String> listFiles(String extension) throws Exception;

   boolean isSupportsCallbacks();

   /**
    * The SequentialFile will call this method when a disk IO Error happens during the live phase.
    */
   void onIOError(Throwable exception, String message, String file);

   default void onIOError(Throwable exception, String message, SequentialFile file) {
      onIOError(exception, message, file != null ? file.getFileName() : (String) null);
   }

   default void onIOError(Throwable exception, String message) {
      onIOError(exception, message, (String) null);
   }

   /**
    * used for cases where you need direct buffer outside of the journal context. This is because the native layer has a
    * method that can be reused in certain cases like paging
    */
   ByteBuffer allocateDirectBuffer(int size);

   /**
    * used for cases where you need direct buffer outside of the journal context. This is because the native layer has a
    * method that can be reused in certain cases like paging
    */
   void releaseDirectBuffer(ByteBuffer buffer);

   /**
    * Note: You need to release the buffer if is used for reading operations. You don't need to do it if using writing
    * operations (AIO Buffer Lister will take of writing operations)
    *
    * @return the allocated ByteBuffer
    */
   ByteBuffer newBuffer(int size);

   /**
    * Note: You need to release the buffer if is used for reading operations. You don't need to do it if using writing
    * operations (AIO Buffer Lister will take of writing operations)
    *
    * @param zeroed if {@code true} the returned {@link ByteBuffer} must be zeroed, otherwise it tries to save zeroing
    *               it.
    * @return the allocated ByteBuffer
    */
   default ByteBuffer newBuffer(int size, boolean zeroed) {
      return newBuffer(size);
   }

   void releaseBuffer(ByteBuffer buffer);

   void activateBuffer(SequentialFile file);

   void deactivateBuffer();

   // To be used in tests only
   ByteBuffer wrapBuffer(byte[] bytes);

   int getAlignment();

   SequentialFileFactory setAlignment(int alignment);

   int calculateBlockSize(int bytes);

   File getDirectory();

   default String getDirectoryName() {
      return getDirectory().getName();
   }

   void clearBuffer(ByteBuffer buffer);

   void start();

   void stop();

   default boolean deleteFolder() {
      return false;
   }

   /**
    * Creates the directory if it does not exist yet.
    */
   void createDirs() throws Exception;

   void flush();

   SequentialFileFactory setDatasync(boolean enabled);

   boolean isDatasync();

   long getBufferSize();

   /**
    * Only JDBC supports individual context. Meaning for Files we need to use the Sync scheduler. for JDBC we need to
    * use a callback from the JDBC completion thread to complete the IOContexts.
    */
   default boolean supportsIndividualContext() {
      return false;
   }
}
