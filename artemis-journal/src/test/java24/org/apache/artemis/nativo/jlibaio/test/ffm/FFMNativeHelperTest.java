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
package org.apache.artemis.nativo.jlibaio.test.ffm;

import org.apache.artemis.nativo.jlibaio.SubmitInfo;
import org.apache.artemis.nativo.jlibaio.ffm.FFMNativeHelper;
import org.apache.artemis.nativo.jlibaio.ffm.IOControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.LIBAIO;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FFMNativeHelperTest {

   private static final Logger logger = LoggerFactory.getLogger(FFMNativeHelperTest.class);

   @Test
   @EnabledOnOs(OS.LINUX)
   public void libLoadInittest() {
      logger.trace("@Test:: libLoadInittest");
      String libName = System.getProperty("libaio.path", "libaio.so.1");
      SymbolLookup libaio = SymbolLookup.libraryLookup(libName, Arena.global());
      assertTrue(libaio.find("io_setup").isPresent());
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void libLoadtest() {
      logger.trace("@Test:: libLoadtest");
      assertTrue(LIBAIO.find("io_setup").isPresent());
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void testOpenCloseLifecycle() throws IOException, InterruptedException {
      logger.trace("@Test:: testOpenCloseLifecycle");
      Path testFile = Path.of("libaio-test.bin");
      logger.info("Testing file: {}", testFile.toAbsolutePath());
      try {
         int fd = FFMNativeHelper.open(testFile.toString(), false);
         long allocate = 16 * 1024 * 1024L;
         FFMNativeHelper.fallocate(fd, allocate);
         long size = FFMNativeHelper.getSize(fd);
         assertEquals(allocate, size, "file size mismatch");

         fd = FFMNativeHelper.open(testFile.toString(), true);
         assertTrue(fd >= 0, "Failed to open with O_DIRECT");
         logger.info("Opened fd={} WITH O_DIRECT", fd);

         FFMNativeHelper.close(fd);
      } finally {
         // Cleanup
         Files.deleteIfExists(testFile);
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void getBlockSizeFDTest() throws IOException {
      logger.trace("@Test:: getBlockSizeFDTest");
      Path testFile = Path.of("libaio-test.bin");
      logger.info("Testing file: {}", testFile.toAbsolutePath());
      try {
         int fd = FFMNativeHelper.open(testFile.toString(), false);
         long blockSize = FFMNativeHelper.getBlockSizeFD(fd);
         assertTrue(blockSize > 512 && blockSize < 65536, "Invalid blockSize = " + blockSize);
         FFMNativeHelper.close(fd);
      } finally {
         // Cleanup
         Files.deleteIfExists(testFile);
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void getBlockSizeTest() throws IOException {
      logger.trace("@Test:: getBlockSizeTest");
      Path testFile = Path.of("libaio-test.bin");
      Files.write(testFile, new byte[4096]);
      logger.info("Testing file: {}", testFile.toAbsolutePath());
      try {
         int fd = FFMNativeHelper.open(testFile.toString(), false);
         int fdBlockSize = FFMNativeHelper.getBlockSizeFD(fd);
         FFMNativeHelper.close(fd);

         int pathBlockSize = FFMNativeHelper.getBlockSize(testFile.toString());
         assertEquals(fdBlockSize, pathBlockSize, "FD vs Path block size mismatch");
      } finally {
         // Cleanup
         Files.deleteIfExists(testFile);
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void memsetBufferTest() throws IOException {
      logger.trace("@Test:: memsetBufferTest");
      int blockSize = 4096;
      ByteBuffer buffer = ByteBuffer.allocateDirect(blockSize * 2);
      byte[] garbage = new byte[blockSize];
      new Random().nextBytes(garbage);
      buffer.put(garbage);

      FFMNativeHelper.memsetBuffer(buffer, blockSize);
      for (int i = 0; i < blockSize; i++) {
         assertEquals(0, buffer.get(i), "Byte " + i + " is not Zeroed");
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void newAlignedBufferTest() throws IOException {
      logger.trace("@Test:: newAlignedBufferTest");
      int alignment = 4096;
      int size = alignment * 4;

      ByteBuffer buffer = FFMNativeHelper.newAlignedBuffer(size, alignment).asByteBuffer();
      assertTrue(buffer.isDirect());
      assertEquals(size, buffer.capacity());

      MemorySegment addr = MemorySegment.ofBuffer(buffer);
      long address = addr.address();

      assertEquals(0, address % alignment, "Buffer not aligned to " + alignment);
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void testNewContextDeleteContextLifecycle() throws IOException {
      logger.trace("@Test:: testNewContextDeleteContextLifecycle");
      FFMNativeHelper<TestSubmitInfo> helper = new FFMNativeHelper<>(null);
      IOControl context = null;
      context = helper.newContext(10);
      assertNotNull(context, "Context should not be null");
      logger.info("Created context = {}", context);

      helper.deleteContext(context);
      logger.info("Context deleted successfully");
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void testSubmitWriteReadFullCycle() throws IOException, InterruptedException {
      logger.trace("@Test:: testSubmitWriteReadFullCycle");
      Path testFile = Path.of("aio-cycle-test.bin");
      FFMNativeHelper<TestSubmitInfo> helper = new FFMNativeHelper<>(null);
      IOControl context = null;
      int fd = -1;
      ByteBuffer writeBuffer = null, readBuffer = null;
      try {
         Files.deleteIfExists(testFile);
         fd = FFMNativeHelper.open(testFile.toString(), true);
         FFMNativeHelper.fallocate(fd, 4096);

         context = helper.newContext(4);

         byte[] testData = new byte[4096];
         new Random(12345).nextBytes(testData);

         writeBuffer = FFMNativeHelper.newAlignedBuffer(4096, 4096).asByteBuffer();
         writeBuffer.put(testData).flip();

         readBuffer = FFMNativeHelper.newAlignedBuffer(4096, 4096).asByteBuffer();

         //Write
         TestSubmitInfo writeCb = new TestSubmitInfo();
         helper.submitWrite(fd, context, 0, 4096, writeBuffer, writeCb);

         int events = helper.poll(context, new TestSubmitInfo[1], 1, 1);
         assertEquals(1, events);
         assertTrue(writeCb.isDone());
         assertFalse(writeCb.hasError());

         //Read
         TestSubmitInfo readCb = new TestSubmitInfo();
         helper.submitRead(fd, context, 0, 4096, readBuffer, readCb);

         events = helper.poll(context, new TestSubmitInfo[1], 1, 1);
         assertEquals(1, events);
         assertTrue(readCb.isDone());
         assertFalse(readCb.hasError());

         //verify data
         readBuffer.position(0);
         byte[] readData = new byte[4096];
         readBuffer.get(readData);
         assertArrayEquals(testData, readData);
      } finally {
         if (context != null) {
            helper.deleteContext(context);
         }
         if (fd >= 0) {
            FFMNativeHelper.close(fd);
         }
         Files.deleteIfExists(testFile);
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void blockedPollTest() throws IOException, InterruptedException {
      logger.trace("@Test:: blockedPollTest");
      Path testFile = Path.of("blocked-poll-test.bin");
      FFMNativeHelper<TestSubmitInfo> helper = new FFMNativeHelper<>(null);
      IOControl context = null;
      int fd = -1;
      MemorySegment nativeBuffer = null;
      ByteBuffer buffer = null;

      try {
         Files.deleteIfExists(testFile);
         fd = FFMNativeHelper.open(testFile.toString(), true);
         FFMNativeHelper.fallocate(fd, 4096);

         context = helper.newContext(2);

         TestSubmitInfo callBack = new TestSubmitInfo();
         nativeBuffer = FFMNativeHelper.newAlignedBuffer(4096, 4096);
         buffer = nativeBuffer.asByteBuffer();
         buffer.put((byte) 42).flip();

         helper.submitWrite(fd, context, 0, 4096, buffer, callBack);
         final IOControl contextRef = context;
         Thread pollThread = new Thread(() -> {
            try {
               helper.blockedPoll(contextRef, false);
            } catch (Throwable e) {
               logger.error("BlockedPoll failed", e);
            }
         });

         pollThread.start();
         Thread.sleep(100);
         pollThread.join(1000);

         assertTrue(callBack.isDone());
      } finally {
         if (context != null) {
            helper.deleteContext(context);
         }
         if (fd >= 0) {
            FFMNativeHelper.close(fd);
         }
         Files.deleteIfExists(testFile);
         if (buffer != null) {
            FFMNativeHelper.freeBuffer(nativeBuffer);
         }
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void fillMethodTest() throws IOException {
      logger.trace("@Test:: fillMethodTest");
      Path testFile = Path.of("fill-test.bin");
      int fd = -1;

      try {
         Files.deleteIfExists(testFile);
         fd = FFMNativeHelper.open(testFile.toString(), false);
         long size = 3 * 1024 * 1024L;

         FFMNativeHelper.fill(fd, 4096, size);
         long actualSize = FFMNativeHelper.getSize(fd);
         assertEquals(size, actualSize);
      } finally {
         if (fd >= 0) {
            FFMNativeHelper.close(fd);
         }
         Files.deleteIfExists(testFile);
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void lockUnlockTest() throws IOException {
      logger.trace("@Test:: lockUnlockTest");
      Path testFile = Path.of("lock-test.bin");
      int fd = -1;

      try {
         Files.deleteIfExists(testFile);
         fd = FFMNativeHelper.open(testFile.toString(), false);

         assertTrue(FFMNativeHelper.lock(fd));
         int fd2 = FFMNativeHelper.open(testFile.toString(), false);
         try {
            assertFalse(FFMNativeHelper.lock(fd2));
         } finally {
            FFMNativeHelper.close(fd2);
         }
      } finally {
         if (fd >= 0) {
            FFMNativeHelper.close(fd);
            Files.deleteIfExists(testFile);
         }
      }
   }

   @Test
   @EnabledOnOs(OS.LINUX)
   public void iocbPoolExhaustionTest() throws IOException {
      logger.trace("@Test:: iocbPoolExhaustionTest");
      FFMNativeHelper<TestSubmitInfo> helper = new FFMNativeHelper<>(null);
      IOControl context = helper.newContext(1);
      int fd = FFMNativeHelper.open("pool-test.bin", false);
      MemorySegment nativeBuffer = FFMNativeHelper.newAlignedBuffer(4096, 4096);
      ByteBuffer buffer = nativeBuffer.asByteBuffer();
      try {
         TestSubmitInfo cb1 = new TestSubmitInfo();
         helper.submitWrite(fd, context, 0, 4096, buffer, cb1);

         TestSubmitInfo cb2 = new TestSubmitInfo();
         assertThrows(IOException.class, () -> helper.submitWrite(fd, context, 4096, 4096, buffer, cb2));

         helper.poll(context, new TestSubmitInfo[1], 1, 1);

         TestSubmitInfo cb3 = new TestSubmitInfo();
         helper.submitWrite(fd, context, 8192, 4096, buffer, cb3);
      } finally {
         FFMNativeHelper.freeBuffer(nativeBuffer);
         helper.deleteContext(context);
         FFMNativeHelper.close(fd);
         Files.deleteIfExists(Path.of("pool-test.bin"));
      }
   }

   private static class TestSubmitInfo implements SubmitInfo {

      private final AtomicBoolean done = new AtomicBoolean(false);
      private final AtomicBoolean error = new AtomicBoolean(false);
      private final AtomicReference<Integer> errorCode = new AtomicReference<>(0);
      private final AtomicReference<String> errorMessage = new AtomicReference<>("");

      @Override
      public void onError(int errno, String message) {
         error.set(true);
         this.errorCode.set(errno);
         this.errorMessage.set(message);
      }

      @Override
      public void done() {
         done.set(true);
      }

      public boolean isDone() {
         return done.get();
      }

      public boolean hasError() {
         return error.get();
      }
   }
}
