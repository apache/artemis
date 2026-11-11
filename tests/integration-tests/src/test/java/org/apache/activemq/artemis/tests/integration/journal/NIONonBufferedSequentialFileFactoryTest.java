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
package org.apache.activemq.artemis.tests.integration.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.SequentialFileFactoryTestBase;
import org.junit.jupiter.api.Test;

public class NIONonBufferedSequentialFileFactoryTest extends SequentialFileFactoryTestBase {

   @Override
   protected SequentialFileFactory createFactory(String folder) {
      return new NIOSequentialFileFactory(new File(folder), false, 1);
   }

   @Test
   public void writeHeapBufferNotFromBeginningAndReadWithDirectBuffer() throws Exception {
      writeHeapBufferNotFromBeginningAndRead(false);
   }

   @Test
   public void writeHeapBufferNotFromBeginningAndReadWithHeapBuffer() throws Exception {
      writeHeapBufferNotFromBeginningAndRead(true);
   }

   private void writeHeapBufferNotFromBeginningAndRead(boolean useHeapByteBufferToRead) throws Exception {
      final SequentialFile file = factory.createSequentialFile("write.amq");
      file.open();
      assertEquals(0, file.size());
      assertEquals(0, file.position());
      try {
         final String data = "writeDirectArray";
         final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
         file.position(factory.calculateBlockSize(bytes.length));
         file.writeDirect(ByteBuffer.wrap(bytes), false);
         final ByteBuffer readBuffer;
         if (!useHeapByteBufferToRead) {
            readBuffer = factory.newBuffer(bytes.length);
         } else {
            readBuffer = ByteBuffer.allocate(bytes.length);
         }
         try {
            file.position(factory.calculateBlockSize(bytes.length));
            assertEquals(factory.calculateBlockSize(bytes.length), file.read(readBuffer));
            assertEquals(data, StandardCharsets.UTF_8.decode(readBuffer).toString());
         } finally {
            if (!useHeapByteBufferToRead) {
               factory.releaseBuffer(readBuffer);
            }
         }
      } finally {
         file.close();
         file.delete();
      }
   }

   @Test
   public void writeHeapBufferAndReadWithDirectBuffer() throws Exception {
      writeHeapBufferAndRead(false);
   }

   @Test
   public void writeHeapBufferAndReadWithHeapBuffer() throws Exception {
      writeHeapBufferAndRead(true);
   }

   private void writeHeapBufferAndRead(boolean useHeapByteBufferToRead) throws Exception {
      final SequentialFile file = factory.createSequentialFile("write.amq");
      file.open();
      assertEquals(0, file.size());
      assertEquals(0, file.position());
      try {
         final String data = "writeDirectArray";
         final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
         file.writeDirect(ByteBuffer.wrap(bytes), false);
         final ByteBuffer readBuffer;
         if (!useHeapByteBufferToRead) {
            readBuffer = factory.newBuffer(bytes.length);
         } else {
            readBuffer = ByteBuffer.allocate(bytes.length);
         }
         try {
            file.position(0);
            assertEquals(factory.calculateBlockSize(bytes.length), file.read(readBuffer));
            assertEquals(data, StandardCharsets.UTF_8.decode(readBuffer).toString());
         } finally {
            if (!useHeapByteBufferToRead) {
               factory.releaseBuffer(readBuffer);
            }
         }
      } finally {
         file.close();
         file.delete();
      }
   }

   @Test
   public void writeHeapAndDirectBufferAndReadWithDirectBuffer() throws Exception {
      writeHeapAndDirectBufferAndRead(false);
   }

   @Test
   public void writeHeapAndDirectBufferAndReadWithHeapBuffer() throws Exception {
      writeHeapAndDirectBufferAndRead(true);
   }

   private void writeHeapAndDirectBufferAndRead(boolean useHeapByteBufferToRead) throws Exception {
      final SequentialFile file = factory.createSequentialFile("write.amq");
      file.open();
      assertEquals(0, file.size());
      assertEquals(0, file.position());
      try {
         final String data = "writeDirectArray";
         final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
         file.writeDirect(ByteBuffer.wrap(bytes), false);
         final ByteBuffer byteBuffer = factory.newBuffer(bytes.length);
         byteBuffer.put(bytes);
         byteBuffer.flip();
         file.writeDirect(byteBuffer, false);
         final ByteBuffer readBuffer;
         if (!useHeapByteBufferToRead) {
            readBuffer = factory.newBuffer(bytes.length);
         } else {
            readBuffer = ByteBuffer.allocate(bytes.length);
         }
         try {
            file.position(0);
            assertEquals(factory.calculateBlockSize(bytes.length), file.read(readBuffer));
            assertEquals(data, StandardCharsets.UTF_8.decode(readBuffer).toString());
            readBuffer.flip();
            assertEquals(factory.calculateBlockSize(bytes.length), file.read(readBuffer));
            assertEquals(data, StandardCharsets.UTF_8.decode(readBuffer).toString());
         } finally {
            if (!useHeapByteBufferToRead) {
               factory.releaseBuffer(readBuffer);
            }
         }
      } finally {
         file.close();
         file.delete();
      }
   }

}
