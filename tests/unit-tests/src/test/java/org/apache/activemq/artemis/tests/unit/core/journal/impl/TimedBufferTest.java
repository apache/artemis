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
package org.apache.activemq.artemis.tests.unit.core.journal.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.DummyCallback;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.io.buffer.TimedBufferObserver;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

public class TimedBufferTest extends ActiveMQTestBase {
   private static final Logger log = Logger.getLogger(TimedBufferTest.class);


   private static final int ONE_SECOND_IN_NANOS = 1000000000; // in nanoseconds



   IOCallback dummyCallback = new IOCallback() {

      @Override
      public void done() {
      }

      @Override
      public void onError(final int errorCode, final String errorMessage) {
      }
   };

   @Test
   public void testFillBuffer() {
      final ArrayList<ByteBuffer> buffers = new ArrayList<>();
      final AtomicInteger flushTimes = new AtomicInteger(0);
      class TestObserver implements TimedBufferObserver {

         @Override
         public void flushBuffer(final ByteBuf byteBuf, final boolean sync, final List<IOCallback> callbacks) {
            final ByteBuffer buffer = ByteBuffer.allocate(byteBuf.readableBytes());
            buffer.limit(byteBuf.readableBytes());
            byteBuf.getBytes(byteBuf.readerIndex(), buffer);
            buffer.flip();
            buffers.add(buffer);
            flushTimes.incrementAndGet();
         }

         @Override
         public int getRemainingBytes() {
            return 1024 * 1024;
         }
      }

      TimedBuffer timedBuffer = new TimedBuffer(null, 100, TimedBufferTest.ONE_SECOND_IN_NANOS, false);

      timedBuffer.start();

      try {

         timedBuffer.setObserver(new TestObserver());

         int x = 0;
         for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[10];
            for (int j = 0; j < 10; j++) {
               bytes[j] = ActiveMQTestBase.getSamplebyte(x++);
            }

            ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(bytes);

            timedBuffer.checkSize(10);
            timedBuffer.addBytes(buff, false, dummyCallback);
         }

         timedBuffer.checkSize(1);

         Assert.assertEquals(1, flushTimes.get());

         ByteBuffer flushedBuffer = buffers.get(0);

         Assert.assertEquals(100, flushedBuffer.limit());

         Assert.assertEquals(100, flushedBuffer.capacity());

         flushedBuffer.rewind();

         for (int i = 0; i < 100; i++) {
            Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), flushedBuffer.get());
         }
      } finally {
         timedBuffer.stop();
      }

   }
   @Test
   public void testTimeOnTimedBuffer() throws Exception {
      final ReusableLatch latchFlushed = new ReusableLatch(0);
      final AtomicInteger flushes = new AtomicInteger(0);
      class TestObserver implements TimedBufferObserver {

         @Override
         public void flushBuffer(final ByteBuf byteBuf, final boolean sync, final List<IOCallback> callbacks) {
            final ByteBuffer buffer = ByteBuffer.allocate(byteBuf.readableBytes());
            buffer.limit(byteBuf.readableBytes());
            byteBuf.getBytes(byteBuf.readerIndex(), buffer);
            for (IOCallback callback : callbacks) {
               callback.done();
            }
         }

         @Override
         public int getRemainingBytes() {
            return 1024 * 1024;
         }
      }

      TimedBuffer timedBuffer = new TimedBuffer(null, 100, TimedBufferTest.ONE_SECOND_IN_NANOS / 2, false);

      timedBuffer.start();

      TestObserver observer = new TestObserver();
      timedBuffer.setObserver(observer);


      int x = 0;

      byte[] bytes = new byte[10];
      for (int j = 0; j < 10; j++) {
         bytes[j] = ActiveMQTestBase.getSamplebyte(x++);
      }

      ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(bytes);

      IOCallback callback = new IOCallback() {
         @Override
         public void done() {
            log.debug("done");
            latchFlushed.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      };


      try {
         latchFlushed.setCount(2);

         // simulating a low load period
         timedBuffer.addBytes(buff, true, callback);
         Thread.sleep(1000);
         timedBuffer.addBytes(buff, true, callback);
         Assert.assertTrue(latchFlushed.await(5, TimeUnit.SECONDS));
         latchFlushed.setCount(5);


         flushes.set(0);

         // Sending like crazy... still some wait (1 millisecond) between each send..
         long time = System.currentTimeMillis();
         for (int i = 0; i < 5; i++) {
            timedBuffer.addBytes(buff, true, callback);
            Thread.sleep(1);
         }
         Assert.assertTrue(latchFlushed.await(5, TimeUnit.SECONDS));

         // The purpose of the timed buffer is to batch writes up to a millisecond.. or up to the size of the buffer.
         Assert.assertTrue("Timed Buffer is not batching accordingly, it was expected to take at least 500 seconds batching multiple writes while it took " + (System.currentTimeMillis() - time) + " milliseconds", System.currentTimeMillis() - time >= 450);

         // ^^ there are some discounts that can happen inside the timed buffer that are still considered valid (like discounting the time it took to perform the operation itself
         // for that reason the test has been failing (before this commit) at 499 or 480 milliseconds. So, I'm using a reasonable number close to 500 milliseconds that would still be valid for the test

         // it should be in fact only writing once..
         // i will set for 3 just in case there's a GC or anything else happening on the test
         Assert.assertTrue("Too many writes were called", flushes.get() <= 3);
      } finally {
         timedBuffer.stop();
      }



   }

   private static void spinSleep(long timeout) {
      if (timeout > 0) {
         final long deadline = System.nanoTime() + timeout;
         while (System.nanoTime() - deadline < 0) {
            //spin wait
         }
      }
   }

   private static final class NonBlockingObserver implements TimedBufferObserver, AutoCloseable {

      private long flushes = 0;
      private final ByteBuffer dummyBuffer;
      private final Thread asyncIOWriter;
      private final AtomicLong flushRequest = new AtomicLong(0);
      private final AtomicLong flushesDone = new AtomicLong(0);

      private NonBlockingObserver(int bufferSize, long deviceTime) {
         this.asyncIOWriter = new Thread(() -> {
            long flushes = 0;
            while (!Thread.interrupted()) {
               if (flushRequest.get() > flushes) {
                  final long flushesToBePerformed = flushRequest.get() - flushes;
                  //during the flush time no new flush request can be taken!
                  for (int i = 0; i < flushesToBePerformed; i++) {
                     spinSleep(deviceTime);
                     flushes++;
                     //make progress to let others being notified
                     flushesDone.lazySet(flushes);
                  }
               }
            }
         });
         dummyBuffer = ByteBuffer.allocate(bufferSize);
         asyncIOWriter.start();
      }

      @Override
      public void flushBuffer(final ByteBuf byteBuf, final boolean sync, final List<IOCallback> callbacks) {
         assert sync;
         dummyBuffer.limit(byteBuf.readableBytes());
         byteBuf.getBytes(byteBuf.readerIndex(), dummyBuffer);
         if (dummyBuffer.position() > 0) {
            dummyBuffer.clear();
            flushes++;
            //ask the device to perform a flush
            flushRequest.lazySet(flushes);
         }
      }

      @Override
      public int getRemainingBytes() {
         return Integer.MAX_VALUE;
      }

      @Override
      public void close() {
         asyncIOWriter.interrupt();
      }

      public void waitUntilFlushIsDone(long flush) {
         //spin wait to be more reactive
         while (flushesDone.get() < flush) {

         }
      }

      public long flushesDone() {
         return flushesDone.get();
      }
   }

   private static final class BlockingObserver implements TimedBufferObserver, AutoCloseable {

      private long flushes = 0;
      private final ByteBuffer dummyBuffer;
      private final long deviceTime;
      private final AtomicLong flushesDone = new AtomicLong(0);

      private BlockingObserver(int bufferSize, long deviceTime) {
         this.dummyBuffer = ByteBuffer.allocate(bufferSize);
         this.deviceTime = deviceTime;
      }

      @Override
      public void flushBuffer(final ByteBuf byteBuf, final boolean sync, final List<IOCallback> callbacks) {
         assert sync;
         dummyBuffer.limit(byteBuf.readableBytes());
         byteBuf.getBytes(byteBuf.readerIndex(), dummyBuffer);
         if (dummyBuffer.position() > 0) {
            dummyBuffer.clear();
            //emulate the flush time of a blocking device with a precise sleep
            spinSleep(deviceTime);
            flushes++;
            //publish the number of flushes happened
            flushesDone.lazySet(flushes);
         }
      }

      @Override
      public int getRemainingBytes() {
         return Integer.MAX_VALUE;
      }

      @Override
      public void close() {
         //no op
      }

      public void waitUntilFlushIsDone(long flush) {
         //spin wait to be more reactive
         while (flushesDone.get() < flush) {

         }
      }

      public long flushesDone() {
         return flushesDone.get();
      }

   }

   private static final EncodingSupport LONG_ENCODER = new EncodingSupport() {
      @Override
      public int getEncodeSize() {
         return Long.BYTES;
      }

      @Override
      public void encode(ActiveMQBuffer buffer) {
         buffer.writeLong(1L);
      }

      @Override
      public void decode(ActiveMQBuffer buffer) {

      }
   };

   /**
    * This test is showing the behaviour of the TimedBuffer with a blocking API (NIO/MAPPED) and
    * how the timeout value is not == 1/IOPS like the ASYNCIO case
    */
   @Test
   public void timeoutShouldMatchFlushIOPSWithNotBlockingFlush() {
      //use a large timeout in order to be reactive
      final long timeout = TimeUnit.MILLISECONDS.toNanos(100);
      assert ((int) timeout) > 0;
      //it is optimistic: the timeout and the blockingDeviceFlushTime are a perfect match
      final long deviceTime = timeout;
      final int bufferSize = Env.osPageSize();
      final TimedBuffer timedBuffer = new TimedBuffer(null, bufferSize, (int) timeout, false);
      timedBuffer.start();
      try (NonBlockingObserver observer = new NonBlockingObserver(bufferSize, deviceTime)) {
         timedBuffer.setObserver(observer);
         //do not call checkSize because we already know that will succeed
         timedBuffer.addBytes(LONG_ENCODER, true, DummyCallback.getInstance());
         //wait the first flush to happen
         observer.waitUntilFlushIsDone(1);
         //for a not-blocking flush I'm expecting the TimedBuffer has near to finished sleeping now
         assert observer.flushesDone() == 1;
         //issue a new write
         timedBuffer.addBytes(LONG_ENCODER, true, DummyCallback.getInstance());
         //the countdown on the TimedBuffer is already started even before this addBytes
         final long endOfWriteRequest = System.nanoTime();
         //wait until it will succeed
         observer.waitUntilFlushIsDone(2);
         final long flushDone = System.nanoTime();
         final long elapsedTime = flushDone - endOfWriteRequest;
         assert observer.flushesDone() == 2;
         //it is much more than what is expected!!if it will fail it means that the timed IOPS = 1/(timeout + blockingDeviceFlushTime)!!!!!!
         //while it has to be IOPS = 1/timeout
         log.debug("elapsed time: " + elapsedTime + " with timeout: " + timeout);
         final long maxExpected = timeout + deviceTime;
         Assert.assertTrue("elapsed = " + elapsedTime + " max expected = " + maxExpected, elapsedTime <= maxExpected);
      } finally {
         timedBuffer.stop();
      }
   }

   /**
    * This test is showing the behaviour of the TimedBuffer with a blocking API (NIO/MAPPED) and
    * how the timeout value is not == 1/IOPS like the ASYNCIO case
    */
   @Test
   public void timeoutShouldMatchFlushIOPSWithBlockingFlush() {
      //use a large timeout in order to be reactive
      final long timeout = TimeUnit.MILLISECONDS.toNanos(100);
      assert ((int) timeout) > 0;
      //it is optimistic: the timeout and the blockingDeviceFlushTime are a perfect match
      final long deviceTime = timeout;
      final int bufferSize = Env.osPageSize();
      final TimedBuffer timedBuffer = new TimedBuffer(null, bufferSize, (int) timeout, false);
      timedBuffer.start();
      try (BlockingObserver observer = new BlockingObserver(bufferSize, deviceTime)) {
         timedBuffer.setObserver(observer);
         //do not call checkSize because we already know that will succeed
         timedBuffer.addBytes(LONG_ENCODER, true, DummyCallback.getInstance());
         //wait the first flush to happen
         observer.waitUntilFlushIsDone(1);
         //for a blocking flush I'm expecting the TimedBuffer has started sleeping now
         assert observer.flushesDone() == 1;
         //issue a new write
         timedBuffer.addBytes(LONG_ENCODER, true, DummyCallback.getInstance());
         //the countdown on the TimedBuffer is already started even before this addBytes
         final long endOfWriteRequest = System.nanoTime();
         //wait until it will succeed
         observer.waitUntilFlushIsDone(2);
         final long flushDone = System.nanoTime();
         final long elapsedTime = flushDone - endOfWriteRequest;
         assert observer.flushesDone() == 2;
         //it is much more than what is expected!!if it will fail it means that the timed IOPS = 1/(timeout + blockingDeviceFlushTime)!!!!!!
         //while it has to be IOPS = 1/timeout
         log.debug("elapsed time: " + elapsedTime + " with timeout: " + timeout);
         final long maxExpected = timeout + deviceTime;
         Assert.assertTrue("elapsed = " + elapsedTime + " max expected = " + maxExpected, elapsedTime <= maxExpected);
      } finally {
         timedBuffer.stop();
      }
   }

   @Test
   public void testTimingAndFlush() throws Exception {
      final ArrayList<ByteBuffer> buffers = new ArrayList<>();
      final AtomicInteger flushTimes = new AtomicInteger(0);
      class TestObserver implements TimedBufferObserver {

         @Override
         public void flushBuffer(final ByteBuf byteBuf, final boolean sync, final List<IOCallback> callbacks) {
            final ByteBuffer buffer = ByteBuffer.allocate(byteBuf.readableBytes());
            buffer.limit(byteBuf.readableBytes());
            byteBuf.getBytes(byteBuf.readerIndex(), buffer);
            buffer.flip();
            buffers.add(buffer);
            flushTimes.incrementAndGet();
         }

         @Override
         public int getRemainingBytes() {
            return 1024 * 1024;
         }
      }

      TimedBuffer timedBuffer = new TimedBuffer(null, 100, TimedBufferTest.ONE_SECOND_IN_NANOS / 10, false);

      timedBuffer.start();

      try {

         timedBuffer.setObserver(new TestObserver());

         int x = 0;

         byte[] bytes = new byte[10];
         for (int j = 0; j < 10; j++) {
            bytes[j] = ActiveMQTestBase.getSamplebyte(x++);
         }

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(bytes);

         timedBuffer.checkSize(10);
         timedBuffer.addBytes(buff, false, dummyCallback);

         Thread.sleep(200);

         Assert.assertEquals(0, flushTimes.get());

         bytes = new byte[10];
         for (int j = 0; j < 10; j++) {
            bytes[j] = ActiveMQTestBase.getSamplebyte(x++);
         }

         buff = ActiveMQBuffers.wrappedBuffer(bytes);

         timedBuffer.checkSize(10);
         timedBuffer.addBytes(buff, true, dummyCallback);

         Thread.sleep(500);

         Assert.assertEquals(1, flushTimes.get());

         ByteBuffer flushedBuffer = buffers.get(0);

         Assert.assertEquals(20, flushedBuffer.limit());

         Assert.assertEquals(20, flushedBuffer.capacity());

         flushedBuffer.rewind();

         for (int i = 0; i < 20; i++) {
            Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), flushedBuffer.get());
         }
      } finally {
         timedBuffer.stop();
      }

   }
}
