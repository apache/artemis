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
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;

public class MemorySequentialFileFactory implements SequentialFileFactory {

   @Override
   public SequentialFile createSequentialFile(String fileName) {
      return new MemorySequentialFile(fileName);
   }

   @Override
   public int getMaxIO() {
      return 1;
   }

   @Override
   public List<String> listFiles(String extension) throws Exception {
      return Collections.emptyList();
   }

   @Override
   public boolean isSupportsCallbacks() {
      return false;
   }

   @Override
   public ByteBuffer allocateDirectBuffer(int size) {
      return ByteBuffer.allocateDirect(size);
   }

   @Override
   public void releaseDirectBuffer(ByteBuffer buffer) {
   }

   @Override
   public ByteBuffer newBuffer(int size) {
      return ByteBuffer.allocate(size);
   }

   @Override
   public void releaseBuffer(ByteBuffer buffer) {
   }

   @Override
   public void activateBuffer(SequentialFile file) {
   }

   @Override
   public void deactivateBuffer() {
   }

   @Override
   public ByteBuffer wrapBuffer(byte[] bytes) {
      return ByteBuffer.wrap(bytes);
   }

   @Override
   public int getAlignment() {
      return 1;
   }

   @Override
   public SequentialFileFactory setAlignment(int alignment) {
      return this;
   }

   @Override
   public int calculateBlockSize(int bytes) {
      return bytes;
   }

   @Override
   public File getDirectory() {
      return null;
   }

   @Override
   public void clearBuffer(ByteBuffer buffer) {
      final int limit = buffer.limit();
      buffer.rewind();
      for (int i = 0; i < limit; i++) {
         buffer.put((byte) 0);
      }
      buffer.rewind();
   }

   @Override
   public void start() {
   }

   @Override
   public void stop() {
   }

   @Override
   public void createDirs() throws Exception {
   }

   @Override
   public void flush() {
   }

   @Override
   public boolean isDatasync() {
      return false;
   }

   @Override
   public SequentialFileFactory setDatasync(boolean enabled) {
      return this;
   }

   @Override
   public long getBufferSize() {
      return 0;
   }

   @Override
   public void onIOError(Throwable exception, String message, String file) {
   }
}
