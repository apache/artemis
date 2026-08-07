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

package org.apache.artemis.nativo.jlibaio.test;

import org.apache.artemis.nativo.jlibaio.LibaioContext;
import org.apache.artemis.nativo.jlibaio.LibaioFile;
import org.apache.artemis.nativo.jlibaio.SubmitInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This test is using a different package from {@link LibaioFile}
 * as I need to validate public methods on the API
 */
public class LibaioTest {

   private static final Logger logger = LoggerFactory.getLogger(LibaioTest.class);

   @BeforeClass
   public static void testAIO() {

      File parent = new File("./target");
      File file = new File(parent, "testFile");

      try {
         parent.mkdirs();

         boolean failed = false;
         try (LibaioContext control = new LibaioContext<>(1, true, true); LibaioFile fileDescriptor = control.openFile(file, true)) {
            fileDescriptor.fallocate(4 * 1024);
         } catch (Exception e) {
            e.printStackTrace();
            failed = true;
         }

         Assume.assumeFalse("There is not enough support to libaio", failed);
      } finally {
         file.delete();
      }
   }

   /**
    * This is just an arbitrary number for a number of elements you need to pass to the libaio init method
    * Some of the tests are using half of this number, so if anyone decide to change this please use an even number.
    */
   private static final int LIBAIO_QUEUE_SIZE = 50;

   @Rule
   public TemporaryFolder temporaryFolder;

   public LibaioContext<TestInfo> control;

   @Before
   public void setUpFactory() {
      control = new LibaioContext<>(LIBAIO_QUEUE_SIZE, true, true);
   }

   @After
   public void deleteFactory() {
      control.close();
      validateLibaio();
   }

   public void validateLibaio() {
      Assert.assertEquals(0, LibaioContext.getTotalMaxIO());
   }

   public LibaioTest() {
      /*
       *  I didn't use /tmp for three reasons
       *  - Most systems now will use tmpfs which is not compatible with O_DIRECT
       *  - This would fill up /tmp in case of failures.
       *  - target is cleaned up every time you do a mvn clean, so it's safer
       */
      File parent = new File("./target");
      parent.mkdirs();
      temporaryFolder = new TemporaryFolder(parent);
   }

   @Test
   public void testOpen() throws Exception {
      LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile("test.bin"), true);
      fileDescriptor.close();
   }

   @Test
   public void testInitAndFallocate10M() throws Exception {
      testInit(10 * 1024 * 1024);
   }

   @Test
   public void testInitAndFallocate10M100K() throws Exception {
      testInit(10 * 1024 * 1024 + 100 * 1024);
   }

   private void testInit(int size) throws IOException {
      LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile("test.bin"), true);
      fileDescriptor.fallocate(size);

      MemorySegment buffer = fileDescriptor.newBuffer(size);
      fileDescriptor.read(0, size, buffer.asByteBuffer(), new TestInfo());

      TestInfo[] callbacks = new TestInfo[1];
      control.poll(callbacks, 1, 1);

      fileDescriptor.close();

      buffer.asByteBuffer().position(0);

      LibaioFile fileDescriptor2 = control.openFile(temporaryFolder.newFile("test2.bin"), true);
      fileDescriptor2.fill(fileDescriptor.getBlockSize(), size);
      fileDescriptor2.read(0, size, buffer.asByteBuffer(), new TestInfo());

      control.poll(callbacks, 1, 1);
      for (int i = 0; i < size; i++) {
         Assert.assertEquals(0, buffer.asByteBuffer().get());
      }

      LibaioContext.freeBuffer(buffer);
   }

   @Test
   public void testInitAndFallocate10K() throws Exception {
      testInit(10 * 4096);
   }

   @Test
   public void testInitAndFallocate20K() throws Exception {
      testInit(20 * 4096);
   }

   @Test
   public void testSubmitWriteOnTwoFiles() throws Exception {

      File file1 = temporaryFolder.newFile("test.bin");
      File file2 = temporaryFolder.newFile("test2.bin");

      fillupFile(file1, LIBAIO_QUEUE_SIZE / 2);
      fillupFile(file2, LIBAIO_QUEUE_SIZE / 2);

      LibaioFile[] fileDescriptor = new LibaioFile[] {control.openFile(file1, true), control.openFile(file2, true)};

      Assert.assertEquals((LIBAIO_QUEUE_SIZE / 2) * 4096, fileDescriptor[0].getSize());
      Assert.assertEquals((LIBAIO_QUEUE_SIZE / 2) * 4096, fileDescriptor[1].getSize());
      Assert.assertEquals(fileDescriptor[0].getBlockSize(), fileDescriptor[1].getBlockSize());
      Assert.assertEquals(LibaioContext.getBlockSize(temporaryFolder.getRoot()), LibaioContext.getBlockSize(file1));
      Assert.assertEquals(LibaioContext.getBlockSize(file1), LibaioContext.getBlockSize(file2));
      logger.debug("blockSize = " + fileDescriptor[0].getBlockSize());
      logger.debug("blockSize /tmp= " + LibaioContext.getBlockSize("/tmp"));

      MemorySegment buffer = LibaioContext.newAlignedBuffer(4096, 4096);

      try {
         for (int i = 0; i < 4096; i++) {
            buffer.asByteBuffer().put((byte) 'a');
         }

         TestInfo callback = new TestInfo();
         TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];

         for (int i = 0; i < LIBAIO_QUEUE_SIZE / 2; i++) {
            for (LibaioFile file : fileDescriptor) {
               file.write(i * 4096, 4096, buffer.asByteBuffer(), callback);
            }
         }

         Assert.assertEquals(LIBAIO_QUEUE_SIZE, control.poll(callbacks, LIBAIO_QUEUE_SIZE, LIBAIO_QUEUE_SIZE));

         for (Object returnedCallback : callbacks) {
            Assert.assertSame(returnedCallback, callback);
         }

         for (LibaioFile file : fileDescriptor) {
            MemorySegment bigbuffer = LibaioContext.newAlignedBuffer(4096 * 25, 4096);
            file.read(0, 4096 * 25, bigbuffer.asByteBuffer(), callback);
            Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

            for (Object returnedCallback : callbacks) {
               Assert.assertSame(returnedCallback, callback);
            }

            for (int i = 0; i < 4096 * 25; i++) {
               Assert.assertEquals((byte) 'a', bigbuffer.asByteBuffer().get());
            }

            LibaioContext.freeBuffer(bigbuffer);

            file.close();
         }
      } finally {
         LibaioContext.freeBuffer(buffer);
      }
   }

   @Test
   public void testSubmitWriteAndRead() throws Exception {
      TestInfo callback = new TestInfo();

      TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];

      LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile("test.bin"), true);

      // ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
      MemorySegment buffer = LibaioContext.newAlignedBuffer(4096, 4096);

      try {
         for (int i = 0; i < 4096; i++) {
            buffer.asByteBuffer().put((byte) 'a');
         }

         buffer.asByteBuffer().rewind();

         fileDescriptor.write(0, 4096, buffer.asByteBuffer(), callback);

         int retValue = control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE);
         Assert.assertEquals(1, retValue);

         Assert.assertSame(callback, callbacks[0]);

         LibaioContext.freeBuffer(buffer);

         buffer = LibaioContext.newAlignedBuffer(4096, 4096);

         for (int i = 0; i < 4096; i++) {
            buffer.asByteBuffer().put((byte) 'B');
         }

         fileDescriptor.write(0, 4096, buffer.asByteBuffer(), new TestInfo());

         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

         buffer.asByteBuffer().rewind();

         fileDescriptor.read(0, 4096, buffer.asByteBuffer(), new TestInfo());

         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

         for (int i = 0; i < 4096; i++) {
            Assert.assertEquals('B', buffer.asByteBuffer().get());
         }
      } finally {
         LibaioContext.freeBuffer(buffer);
         fileDescriptor.close();
      }
   }

   @Test
   /*
    * This file is making use of libaio without O_DIRECT
    * We won't need special buffers on this case.
    */ public void testSubmitWriteAndReadRegularBuffers() throws Exception {
      TestInfo callback = new TestInfo();

      TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];

      File file = temporaryFolder.newFile("test.bin");

      fillupFile(file, LIBAIO_QUEUE_SIZE);

      LibaioFile fileDescriptor = control.openFile(file, false);

      final int BUFFER_SIZE = 50;

      ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

      try {
         for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer.put((byte) 'a');
         }

         buffer.rewind();

         fileDescriptor.write(0, BUFFER_SIZE, buffer, callback);

         int retValue = control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE);
         logger.debug("Return from poll::" + retValue);
         Assert.assertEquals(1, retValue);

         Assert.assertSame(callback, callbacks[0]);

         buffer.rewind();

         for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer.put((byte) 'B');
         }

         fileDescriptor.write(0, BUFFER_SIZE, buffer, new TestInfo());

         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

         buffer.rewind();

         fileDescriptor.read(0, 50, buffer, new TestInfo());

         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

         for (int i = 0; i < BUFFER_SIZE; i++) {
            Assert.assertEquals('B', buffer.get());
         }
      } finally {
         fileDescriptor.close();
      }
   }

   @Test
   public void testSubmitRead() throws Exception {

      TestInfo callback = new TestInfo();

      TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];

      File file = temporaryFolder.newFile("test.bin");

      fillupFile(file, LIBAIO_QUEUE_SIZE);

      LibaioFile fileDescriptor = control.openFile(file, true);

      MemorySegment buffer = LibaioContext.newAlignedBuffer(4096, 4096);

      final int BUFFER_SIZE = 4096;
      try {
         for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer.asByteBuffer().put((byte) '@');
         }

         fileDescriptor.write(0, BUFFER_SIZE, buffer.asByteBuffer(), callback);
         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));
         Assert.assertSame(callback, callbacks[0]);

         buffer.asByteBuffer().rewind();

         fileDescriptor.read(0, BUFFER_SIZE, buffer.asByteBuffer(), callback);

         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

         Assert.assertSame(callback, callbacks[0]);

         for (int i = 0; i < BUFFER_SIZE; i++) {
            Assert.assertEquals('@', buffer.asByteBuffer().get());
         }
      } finally {
         LibaioContext.freeBuffer(buffer);
         fileDescriptor.close();
      }
   }

   @Test
   @Ignore
   public void testInvalidWrite() throws Exception {

      TestInfo callback = new TestInfo();

      TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];

      File file = temporaryFolder.newFile("test.bin");

      fillupFile(file, LIBAIO_QUEUE_SIZE);

      LibaioFile fileDescriptor = control.openFile(file, true);

      try {
         ByteBuffer buffer = ByteBuffer.allocateDirect(300);
         for (int i = 0; i < 300; i++) {
            buffer.put((byte) 'z');
         }

         fileDescriptor.write(0, 300, buffer, callback);

         Assert.assertEquals(1, control.poll(callbacks, 1, LIBAIO_QUEUE_SIZE));

         Assert.assertTrue(callbacks[0].isError());

         // Error condition
         Assert.assertSame(callbacks[0], callback);

         logger.debug("Error:" + callbacks[0]);

         MemorySegment memorySegment = fileDescriptor.newBuffer(4096);
         buffer = memorySegment.asByteBuffer();
         for (int i = 0; i < 4096; i++) {
            buffer.put((byte) 'z');
         }

         callback = new TestInfo();

         fileDescriptor.write(0, 4096, buffer, callback);

         Assert.assertEquals(1, control.poll(callbacks, 1, 1));

         Assert.assertSame(callback, callbacks[0]);

         fileDescriptor.write(5, 4096, buffer, callback);

         Assert.assertEquals(1, control.poll(callbacks, 1, 1));

         Assert.assertTrue(callbacks[0].isError());

         callbacks = null;
         callback = null;

         TestInfo.checkLeaks();
      } finally {
         fileDescriptor.close();
      }
   }

   @Test
   public void testLeaks() throws Exception {
      File file = temporaryFolder.newFile("test.bin");

      fillupFile(file, LIBAIO_QUEUE_SIZE * 2);

      TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];

      LibaioFile<TestInfo> fileDescriptor = control.openFile(file, true);

      MemorySegment bufferWrite = LibaioContext.newAlignedBuffer(4096, 4096);

      try {
         for (int i = 0; i < 4096; i++) {
            bufferWrite.asByteBuffer().put((byte) 'B');
         }

         for (int j = 0; j < LIBAIO_QUEUE_SIZE * 2; j++) {
            for (int i = 0; i < LIBAIO_QUEUE_SIZE; i++) {
               TestInfo countClass = new TestInfo();
               fileDescriptor.write(i * 4096, 4096, bufferWrite.asByteBuffer(), countClass);
            }

            Assert.assertEquals(LIBAIO_QUEUE_SIZE, control.poll(callbacks, LIBAIO_QUEUE_SIZE, LIBAIO_QUEUE_SIZE));

            for (int i = 0; i < LIBAIO_QUEUE_SIZE; i++) {
               Assert.assertNotNull(callbacks[i]);
               callbacks[i] = null;
            }
         }

         TestInfo.checkLeaks();
      } finally {
         LibaioContext.freeBuffer(bufferWrite);
      }
   }

   @Test
   public void testLock() throws Exception {
      File file = temporaryFolder.newFile("test.bin");

      LibaioFile fileDescriptor = control.openFile(file, true);
      fileDescriptor.lock();

      fileDescriptor.close();
   }

   @Test
   public void testAlloc() throws Exception {
      File file = temporaryFolder.newFile("test.bin");

      LibaioFile fileDescriptor = control.openFile(file, true);
      fileDescriptor.fill(fileDescriptor.getBlockSize(), 10 * 1024 * 1024);

      fileDescriptor.close();
   }

   @Test
   public void testReleaseNullBuffer() throws Exception {
      boolean failed = false;
      try {
         LibaioContext.freeBuffer(null);
      } catch (Exception expected) {
         failed = true;
      }

      Assert.assertTrue("Exception happened!", failed);

   }

   @Test
   public void testMemset() throws Exception {

      MemorySegment memorySegment = LibaioContext.newAlignedBuffer(4096 * 8, 4096);
      ByteBuffer buffer = memorySegment.asByteBuffer();

      for (int i = 0; i < buffer.capacity(); i++) {
         buffer.put((byte) 'z');
      }

      buffer.position(0);

      for (int i = 0; i < buffer.capacity(); i++) {
         Assert.assertEquals((byte) 'z', buffer.get());
      }

      control.memsetBuffer(buffer);

      buffer.position(0);

      for (int i = 0; i < buffer.capacity(); i++) {
         Assert.assertEquals((byte) 0, buffer.get());
      }

      LibaioContext.freeBuffer(memorySegment);

   }

   @Test
   @Ignore
   public void testIOExceptionConditions() throws Exception {
      boolean exceptionThrown = false;

      control.close();
      control = new LibaioContext<>(LIBAIO_QUEUE_SIZE, false, true);
      try {
         // There is no space for a queue this huge, the native layer should throw the exception
         LibaioContext newController = new LibaioContext(Integer.MAX_VALUE, false, true);
      } catch (RuntimeException e) {
         exceptionThrown = true;
      }

      Assert.assertTrue(exceptionThrown);
      exceptionThrown = false;

      try {
         // this should throw an exception, we shouldn't be able to open a directory!
         control.openFile(temporaryFolder.getRoot(), true);
      } catch (IOException expected) {
         exceptionThrown = true;
      }

      Assert.assertTrue(exceptionThrown);

      exceptionThrown = false;

      LibaioFile fileDescriptor = control.openFile(temporaryFolder.newFile(), true);
      fileDescriptor.close();
      try {
         fileDescriptor.close();
      } catch (IOException expected) {
         exceptionThrown = true;
      }

      Assert.assertTrue(exceptionThrown);

      fileDescriptor = control.openFile(temporaryFolder.newFile(), true);

      MemorySegment memorySegment = fileDescriptor.newBuffer(4096);
      ByteBuffer buffer = memorySegment.asByteBuffer();

      try {
         for (int i = 0; i < 4096; i++) {
            buffer.put((byte) 'a');
         }

         for (int i = 0; i < LIBAIO_QUEUE_SIZE; i++) {
            fileDescriptor.write(i * 4096, 4096, buffer, new TestInfo());
         }

         boolean ex = false;
         try {
            fileDescriptor.write(0, 4096, buffer, new TestInfo());
         } catch (Exception e) {
            ex = true;
         }

         Assert.assertTrue(ex);

         TestInfo[] callbacks = new TestInfo[LIBAIO_QUEUE_SIZE];
         Assert.assertEquals(LIBAIO_QUEUE_SIZE, control.poll(callbacks, LIBAIO_QUEUE_SIZE, LIBAIO_QUEUE_SIZE));

         // it should be possible to write now after queue space being released
         fileDescriptor.write(0, 4096, buffer, new TestInfo());
         Assert.assertEquals(1, control.poll(callbacks, 1, 100));

         TestInfo errorCallback = new TestInfo();
         // odd positions will have failures through O_DIRECT
         fileDescriptor.read(3, 4096, buffer, errorCallback);
         Assert.assertEquals(1, control.poll(callbacks, 1, 50));
         Assert.assertTrue(callbacks[0].isError());
         Assert.assertSame(errorCallback, (callbacks[0]));

         // to help GC and the checkLeaks
         callbacks = null;
         errorCallback = null;

         TestInfo.checkLeaks();

         exceptionThrown = false;
         try {
            LibaioContext.newAlignedBuffer(300, 4096);
         } catch (RuntimeException e) {
            exceptionThrown = true;
         }

         Assert.assertTrue(exceptionThrown);

         exceptionThrown = false;
         try {
            LibaioContext.newAlignedBuffer(-4096, 4096);
         } catch (RuntimeException e) {
            exceptionThrown = true;
         }

         Assert.assertTrue(exceptionThrown);
      } finally {
         LibaioContext.freeBuffer(memorySegment);
      }
   }

   @Test
   public void testBlockedCallback() throws Exception {
      final LibaioContext blockedContext = new LibaioContext(LIBAIO_QUEUE_SIZE, true, true);
      Thread t = new Thread() {
         @Override
         public void run() {
            blockedContext.poll();
         }
      };

      t.start();

      int NUMBER_OF_BLOCKS = LIBAIO_QUEUE_SIZE * 10;

      final CountDownLatch latch = new CountDownLatch(NUMBER_OF_BLOCKS);

      File file = temporaryFolder.newFile("sub-file.txt");
      LibaioFile aioFile = blockedContext.openFile(file, true);
      aioFile.fill(aioFile.getBlockSize(), NUMBER_OF_BLOCKS * 4096);

      final AtomicInteger errors = new AtomicInteger(0);

      class MyCallback implements SubmitInfo {

         @Override
         public void onError(int errno, String message) {
            errors.incrementAndGet();
         }

         @Override
         public void done() {
            latch.countDown();
         }
      }

      MyCallback callback = new MyCallback();

      MemorySegment memorySegment = LibaioContext.newAlignedBuffer(4096, 4096);
      ByteBuffer buffer = memorySegment.asByteBuffer();

      for (int i = 0; i < 4096; i++) {
         buffer.put((byte) 'a');
      }

      long start = System.currentTimeMillis();

      for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
         aioFile.write(i * 4096, 4096, buffer, callback);
      }

      long end = System.currentTimeMillis();

      latch.await();

      logger.debug("time = " + (end - start) + " writes/second=" + NUMBER_OF_BLOCKS * 1000L / (end - start));

      blockedContext.close();
      t.join();
   }

   private void fillupFile(File file, int blocks) throws IOException {
      FileOutputStream fileOutputStream = new FileOutputStream(file);
      byte[] bufferWrite = new byte[4096];
      for (int i = 0; i < 4096; i++) {
         bufferWrite[i] = (byte) 0;
      }

      for (int i = 0; i < blocks; i++) {
         fileOutputStream.write(bufferWrite);
      }

      fileOutputStream.close();
   }

   static class TestInfo implements SubmitInfo {

      static final Cleaner cleaner;

      static {
         Cleaner tempCleaner;
         try {
            tempCleaner = Cleaner.create();
         } catch (Throwable e) {
            e.printStackTrace();
            tempCleaner = null;
         }
         cleaner = tempCleaner;
      }

      static AtomicInteger count = new AtomicInteger();

      public static void checkLeaks() throws InterruptedException {
         for (int i = 0; count.get() != 0 && i < 50; i++) {
            WeakReference reference = new WeakReference(new Object());
            while (reference.get() != null) {
               System.gc();
               Thread.sleep(100);
            }
         }
         Assert.assertEquals(0, count.get());
      }

      boolean error = false;
      String errorMessage;
      int errno;

      TestInfo() {
         count.incrementAndGet();
         cleaner.register(this, count::decrementAndGet);

      }

      @Override
      public void onError(int errno, String message) {
         this.errno = errno;
         this.errorMessage = message;
         this.error = true;
      }

      @Override
      public void done() {
      }

      public int getErrno() {
         return errno;
      }

      public void setErrno(int errno) {
         this.errno = errno;
      }

      public boolean isError() {
         return error;
      }

      public void setError(boolean error) {
         this.error = error;
      }

      public String getErrorMessage() {
         return errorMessage;
      }

      public void setErrorMessage(String errorMessage) {
         this.errorMessage = errorMessage;
      }
   }
}
