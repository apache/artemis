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
package org.apache.activemq.artemis.tests.performance.jmh;

import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioFile;
import org.apache.activemq.artemis.nativo.jlibaio.SubmitInfo;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 2)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class NativeLibaioBenchmarkTest {

   private static final int FILE_SIZE = 10000 * 4096;
   private static final int BLOCK_SIZE = 4096;

   @Param({"2048"})
   private static int LIBAIO_QUEUE_SIZE;

   @Param({"10000"})
   private int recordCount;

   private File file;
   private LibaioContext<SubmitInfo> control;
   private LibaioFile<SubmitInfo> libaioFile;

   private ByteBuffer headerBuffer;
   private ByteBuffer recordBuffer;

   private final AtomicReference<CountDownLatch> currentLatch = new AtomicReference<>();

   private Thread pollThread;
   private volatile boolean polling = true;

   private final SubmitInfo callback = new SubmitInfo() {
      @Override
      public void onError(int errno, String message) {
         // ignore for benchmark
      }

      @Override
      public void done() {
         CountDownLatch latch = currentLatch.get();
         if (latch != null) {
            latch.countDown();
         }
      }
   };

   private long fileId = 1L;

   @Setup(Level.Trial)
   public void setup() throws Exception {
      file = File.createTempFile("aio-bench-jni-", ".dat");

      control = new LibaioContext<>(LIBAIO_QUEUE_SIZE, true, true);
      libaioFile = control.openFile(file, true);

      libaioFile.fallocate(FILE_SIZE);

      headerBuffer = LibaioContext.newAlignedBuffer(BLOCK_SIZE, BLOCK_SIZE);
      recordBuffer = LibaioContext.newAlignedBuffer(BLOCK_SIZE, BLOCK_SIZE);

      initRecord(headerBuffer);   // filling the record clock with 1
      initRecord(recordBuffer);   // filling the record clock with 1

      fillHeader(fileId);
      updateRecord(recordBuffer, fileId, 0L);

      polling = true;
      pollThread = new Thread(() -> {
         try {
            while (polling && !Thread.currentThread().isInterrupted()) {
               control.poll();
            }
         } catch (Throwable t) {
         }
      }, "aio-jmh-jni-poll-thread");
      pollThread.setDaemon(true);
      pollThread.start();

      generateGarbage();
   }

   // Garbage generator fields
   private Thread garbageThread;
   private volatile boolean garbage = true;

   private void generateGarbage() {
      garbage = true;
      garbageThread = new Thread(() -> {
         List<byte[]> list = new ArrayList<>();

         while (garbage && !Thread.currentThread().isInterrupted()) {
            byte[] garbageArray = new byte[256];

            for (int i = 0; i < 1000; i++) {
               list.add(garbageArray);
            }

            if (list.size() > 1000000) {
               list.subList(0, list.size() / 2).clear();
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
         }
      }, "aio-jmh-garbage-thread");
      garbageThread.setDaemon(true);
      garbageThread.start();
   }

   @TearDown(Level.Trial)
   public void tearDown() throws Exception {
      stopGarbageGenerator();

      polling = false;

      if (pollThread != null) {
         pollThread.interrupt();
         pollThread.join(TimeUnit.SECONDS.toMillis(10));
      }

      if (libaioFile != null) {
         libaioFile.close();
      }
      if (control != null) {
         control.close();
      }
      if (headerBuffer != null) {
         LibaioContext.freeBuffer(headerBuffer);
      }
      if (recordBuffer != null) {
         LibaioContext.freeBuffer(recordBuffer);
      }
      if (file != null) {
         file.delete();
      }
   }

   private void stopGarbageGenerator() throws InterruptedException {
      garbage = false;
      if (garbageThread != null) {
         garbageThread.interrupt();
         garbageThread.join(TimeUnit.SECONDS.toMillis(10));
      }
   }

   @Benchmark
   public void writeHeaderAndRecords() throws Exception {
      CountDownLatch latch = new CountDownLatch(recordCount * 100);
      currentLatch.set(latch);

      try {
         fillHeader(fileId);
         //            libaioFile.write(0L, BLOCK_SIZE, headerBuffer, callback);

         for (int j = 0; j < 100; j++) {
            for (int i = 0; i < recordCount; i++) {
               updateRecord(recordBuffer, fileId, i);
               long offset = BLOCK_SIZE + ((long) i * BLOCK_SIZE);
               libaioFile.write(offset, BLOCK_SIZE, recordBuffer, callback);
            }
         }

         latch.await();
      } finally {
         currentLatch.compareAndSet(latch, null);
      }
   }

   private void fillHeader(long fileId) {
      headerBuffer.putLong(0, fileId);
   }

   private void updateRecord(ByteBuffer buffer, long fileId, long recordId) {
      buffer.putLong(0, fileId);
      buffer.putLong(8, recordId);
   }

   private void initRecord(ByteBuffer record) {
      while (record.position() < BLOCK_SIZE) {
         record.put((byte) 1);
      }
   }
}
