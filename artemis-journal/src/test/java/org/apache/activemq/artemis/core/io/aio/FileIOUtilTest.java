/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.io.aio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.util.FileIOUtil;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileIOUtilTest {

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   /**
    * Since the introduction of asynchronous close on AsyncIO journal there was a situation that if you open a file
    * while it was pending to close you could have many issues with file not open, NPEs this is to capture and fix that
    * race
    */
   @Test
   public void testOpenClose() throws Exception {
      assumeTrue(LibaioContext.isLoaded());
      AtomicInteger errors = new AtomicInteger(0);

      SequentialFileFactory factory = new AIOSequentialFileFactory(temporaryFolder, (Throwable error, String message, String file) -> errors.incrementAndGet(), 4 * 1024);
      factory.start();

      SequentialFile file = factory.createSequentialFile("fileAIO.bin");
      file.open(1024, true);

      final int WRITES = 10;
      final int RECORD_SIZE = 4 * 1024;
      final int OPEN_TIMES = 250;


      file.fill(WRITES * RECORD_SIZE);


      try {

         file.close(true, false);

         for (int nclose = 0; nclose < OPEN_TIMES; nclose++) {
            file.open(1024, true);
            for (int i = 0; i < WRITES; i++) {
               ByteBuffer buffer = factory.newBuffer(RECORD_SIZE);
               for (int s = 0; s < RECORD_SIZE; s++) {
                  buffer.put((byte) 'j');
               }
               file.position(i * RECORD_SIZE);
               file.writeDirect(buffer, false);
            }
            file.close(true, false);
         }
      } finally {
         factory.stop();
      }

      assertEquals(0, errors.get());

   }

   @Test
   public void testCopy() throws Exception {
      SequentialFileFactory factory = new NIOSequentialFileFactory(temporaryFolder, 100);
      SequentialFile file = factory.createSequentialFile("file1.bin");
      file.open();

      ByteBuffer buffer = ByteBuffer.allocate(204800);
      buffer.put(new byte[204800]);
      buffer.rewind();
      file.writeDirect(buffer, true);

      buffer = ByteBuffer.allocate(409605);
      buffer.put(new byte[409605]);
      buffer.rewind();

      SequentialFile file2 = factory.createSequentialFile("file2.bin");

      file2.open();
      file2.writeDirect(buffer, true);

      // This is allocating a reusable buffer to perform the copy, just like it's used within LargeMessageInSync
      buffer = ByteBuffer.allocate(4 * 1024);

      SequentialFile newFile = factory.createSequentialFile("file1.cop");
      FileIOUtil.copyData(file, newFile, buffer);

      SequentialFile newFile2 = factory.createSequentialFile("file2.cop");
      FileIOUtil.copyData(file2, newFile2, buffer);

      assertEquals(file.size(), newFile.size());
      assertEquals(file2.size(), newFile2.size());

      newFile.close();
      newFile2.close();
      file.close();
      file2.close();

   }

}
