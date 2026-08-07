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
package org.apache.activemq.artemis.tests.unit.core.asyncio2;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.artemis.core.io.aio2.AIO2Helper;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * You need to define {@literal -Djava.library.path=${project-root}/native/src/.libs} when calling the JVM
 * If you are running this test in eclipse you should do:
 * <ol>
 * <li>Run->Open Run Dialog
 * <li>Find the class on the list (you will find it if you already tried running this testcase before)
 * <li>Add {@literal -Djava.library.path=${project-root}/native/src/.libs}
 * </ol>
 */
public class AIO2MultiThreadAsynchronousFileTest extends AIO2TestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   AtomicInteger position = new AtomicInteger(0);

   static final int SIZE = 1024;

   static final int NUMBER_OF_THREADS = 1;

   static final int NUMBER_OF_LINES = 1000;

   ExecutorService executor;

   ExecutorService pollerExecutor;

   @BeforeAll
   public static void hasAIO() {
      assumeTrue(AIO2Helper.isSupported(), "AIO2 is not supported on this platform");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      pollerExecutor = Executors.newCachedThreadPool(new ActiveMQThreadFactory("ActiveMQ-FFM-AIO-poller-pool" + System.identityHashCode(this), false, this.getClass().getClassLoader()));
      executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      executor.shutdown();
      pollerExecutor.shutdown();
      super.tearDown();
   }

   @Test
   public void testMultipleASynchronousWrites() throws Throwable {
      executeTest(false);
   }

   @Test
   public void testMultipleSynchronousWrites() throws Throwable {
      executeTest(true);
   }

   private void executeTest(final boolean sync) throws Throwable {
      logger.debug(sync ? "Sync test:" : "Async test");

      SequentialFileFactory factory = AIO2Helper.getAIO2SequentialFileFactory(getTestDirfile(), 100);
      factory.start();
      factory.disableBufferReuse();

      SequentialFile file = factory.createSequentialFile(fileName);
      file.open();
      try {
         logger.debug("Preallocating file");

         file.fill(AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_THREADS * AIO2MultiThreadAsynchronousFileTest.SIZE * AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_LINES);
         logger.debug("Done Preallocating file");

         CountDownLatch latchStart = new CountDownLatch(AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_THREADS + 1);

         List<ThreadProducer> list = new ArrayList<>(AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_THREADS);
         for (int i = 0; i < AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_THREADS; i++) {
            ThreadProducer producer = new ThreadProducer("Thread " + i, latchStart, factory, file, sync);
            list.add(producer);
            producer.start();
         }

         latchStart.countDown();
         ActiveMQTestBase.waitForLatch(latchStart);

         long startTime = System.currentTimeMillis();

         for (ThreadProducer producer : list) {
            producer.join();
            if (producer.failed != null) {
               throw producer.failed;
            }
         }

         long endTime = System.currentTimeMillis();
         long duration = endTime - startTime;
         long numRecords = NUMBER_OF_THREADS * NUMBER_OF_LINES;
         long rate = numRecords * 1000 / duration;

         logger.info("{} result: Records/Second = {} total time = {} total number of records = {}", (sync ? "Sync" : "Async"), rate, duration, numRecords);
      } finally {
         file.close();
         factory.stop();
      }

   }

   class ThreadProducer extends Thread {

      Throwable failed = null;

      CountDownLatch latchStart;

      boolean sync;

      SequentialFileFactory factory;

      SequentialFile libaio;

      ThreadProducer(final String name,
                     final CountDownLatch latchStart,
                     final SequentialFileFactory factory,
                     final SequentialFile libaio,
                     final boolean sync) {
         super(name);
         this.latchStart = latchStart;
         this.factory = factory;
         this.libaio = libaio;
         this.sync = sync;
      }

      @Override
      public void run() {
         super.run();

         ByteBuffer buffer = null;

         buffer = factory.newNativeBuffer(AIO2MultiThreadAsynchronousFileTest.SIZE, 512);

         try {

            // I'm always reusing the same buffer, as I don't want any noise from
            // malloc on the measurement
            // Encoding buffer
            AIO2MultiThreadAsynchronousFileTest.addString("Thread name=" + Thread.currentThread().getName() + ";" + "\n", buffer);
            for (int local = buffer.position(); local < buffer.capacity() - 1; local++) {
               buffer.put((byte) ' ');
            }
            buffer.put((byte) '\n');

            latchStart.countDown();
            waitForLatch(latchStart);

            CountDownLatch latchFinishThread = null;

            if (!sync) {
               latchFinishThread = new CountDownLatch(AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_LINES);
            }

            LinkedList<CountDownCallback> list = new LinkedList<>();

            for (int i = 0; i < AIO2MultiThreadAsynchronousFileTest.NUMBER_OF_LINES; i++) {

               if (sync) {
                  latchFinishThread = new CountDownLatch(1);
               }
               CountDownCallback callback = new CountDownCallback(latchFinishThread, null, null, 0);
               if (!sync) {
                  list.add(callback);
               }
               addData(libaio, buffer, callback);
               if (sync) {
                  waitForLatch(latchFinishThread);
                  assertEquals(0, callback.errorCalled);
                  assertTrue(callback.doneCalled);
               }
            }
            if (!sync) {
               waitForLatch(latchFinishThread);
            }

            for (CountDownCallback callback : list) {
               assertTrue(callback.doneCalled);
               assertFalse(callback.errorCalled != 0);
            }

            for (CountDownCallback callback : list) {
               assertTrue(callback.doneCalled);
               assertFalse(callback.errorCalled != 0);
            }

         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            failed = e;
         } finally {
            synchronized (AIO2MultiThreadAsynchronousFileTest.class) {
               buffer.clear();
               factory.freeNativeBuffer(buffer);
            }
         }

      }
   }

   private static void addString(final String str, final ByteBuffer buffer) {
      byte[] bytes = str.getBytes();
      buffer.put(bytes);
   }

   private void addData(final SequentialFile aio,
                        final ByteBuffer buffer,
                        final IOCallback callback) throws Exception {
      aio.writeDirect(buffer, true, callback);
   }

}
