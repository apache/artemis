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
import org.apache.artemis.nativo.jlibaio.util.CallbackCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * This test is using a different package from {@link LibaioFile}
 * as I need to validate public methods on the API
 */
public class LibaioStressTest {

   private static final Logger logger = LoggerFactory.getLogger(LibaioStressTest.class);

   private static final int STRESS_TIME = Integer.parseInt(System.getProperty("test.stress.time", "5000"));

   static {
      logger.debug("LibaioStressTest:: -Dtest.stress.time=" + STRESS_TIME);
   }

   /**
    * This is just an arbitrary number for a number of elements you need to pass to the libaio init method
    * Some of the tests are using half of this number, so if anyone decide to change this please use an even number.
    */
   private static final int LIBAIO_QUEUE_SIZE = 4096;

   private int errors = 0;

   private boolean running = true;

   @Rule
   public TemporaryFolder temporaryFolder;

   public LibaioContext<MyClass> control;

   @Before
   public void setUpFactory() {
      control = new LibaioContext<>(LIBAIO_QUEUE_SIZE, true, false);
   }

   @After
   public void deleteFactory() {
      control.close();
      validateLibaio();
   }

   public void validateLibaio() {
      Assert.assertEquals(0, LibaioContext.getTotalMaxIO());
   }

   public LibaioStressTest() {
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

   CallbackCache<MyClass> callbackCache = new CallbackCache<>(LIBAIO_QUEUE_SIZE);

   class MyClass implements SubmitInfo {

      ReusableLatch reusableLatch;

      @Override
      public void onError(int errno, String message) {

      }

      @Override
      public void done() {
         try {
            reusableLatch.countDown();
            reusableLatch = null;
            callbackCache.put(this);
         } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
         }
      }
   }

   @Test
   public void testForceSyscall() {
      Assert.assertFalse(LibaioContext.isForceSyscall());
      LibaioContext.setForceSyscall(true);
      Assert.assertTrue(LibaioContext.isForceSyscall());
      LibaioContext.setForceSyscall(false);
   }

   @Test
   public void testStressWritesNoSleeps() throws Exception {
      testStressWrites(false);
   }

   @Test
   public void testStressWrites() throws Exception {
      testStressWrites(true);
   }

   private void testStressWrites(boolean sleeps) throws Exception {
      Assume.assumeFalse(LibaioContext.isForceSyscall());

      Thread t = new Thread() {
         @Override
         public void run() {
            control.poll();
         }
      };

      t.start();

      Thread t2 = new Thread(() -> {
         while (running) {
            try {
               Thread.sleep(1000);
            } catch (Exception e) {
            }
            // this is just to make things more interesting from the POV of testing
            System.gc();
         }
      });

      t2.start();

      Thread test1 = startThread("test1.bin", sleeps);
      Thread test2 = startThread("test2.bin", sleeps);
      Thread.sleep(STRESS_TIME); // Configured timeout on the test
      running = false;
      test2.join();
      test1.join();
      t2.join();

      Assert.assertFalse(LibaioContext.isForceSyscall());
      return;
   }

   private Thread startThread(String name, boolean sleeps) {
      Thread t_test = new Thread(() -> {
         try {
            doFile(name, sleeps);
         } catch (IOException e) {
            e.printStackTrace();
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      });
      t_test.start();

      return t_test;
   }

   private void doFile(String fileName, boolean sleeps) throws IOException, InterruptedException {
      ReusableLatch latchWrites = new ReusableLatch(0);

      File file = temporaryFolder.newFile(fileName);
      LibaioFile fileDescriptor = control.openFile(file, true);

      // ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
      MemorySegment memorySegment = LibaioContext.newAlignedBuffer(4096, 4096);
      ByteBuffer buffer = memorySegment.asByteBuffer();

      int maxSize = 4096 * LIBAIO_QUEUE_SIZE;
      fileDescriptor.fill(4096, maxSize);
      for (int i = 0; i < 4096; i++) {
         buffer.put((byte) 'a');
      }

      buffer.rewind();

      int pos = 0;

      long count = 0;

      long nextBreak = System.currentTimeMillis() + 3000;

      while (running) {
         count++;

         if (System.currentTimeMillis() > nextBreak) {
            if (!latchWrites.await(10, TimeUnit.SECONDS)) {
               System.err.println("Latch did not complete for some reason");
               errors++;
               return;
            }
            fileDescriptor.close();

            fileDescriptor = control.openFile(file, true);
            pos = 0;
            // we close / open a file every 5 seconds
            nextBreak = System.currentTimeMillis() + 5000;
         }

         if (count % (sleeps ? 1_000 : 100_000) == 0) {
            logger.debug("Writen " + count + " buffers at " + fileName);
         }
         MyClass myClass = callbackCache.get();

         if (myClass == null) {
            myClass = new MyClass();
         }

         myClass.reusableLatch = latchWrites;
         myClass.reusableLatch.countUp();

         if (sleeps) {
            if (count % 100 == 0) {
               Thread.sleep(100);
            }
         }
         fileDescriptor.write(pos, 4096, buffer, myClass);
         pos += 4096;

         if (pos >= maxSize) {
            pos = 0;
         }

      }

      fileDescriptor.close();
   }

}
