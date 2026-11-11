/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.unit.core.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientMessageInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQConsumerContext;
import org.apache.activemq.artemis.spi.core.remoting.ConsumerContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LargeMessageBufferTest extends ActiveMQTestBase {


   static int tmpFileCounter = 0;


   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      tmpFileCounter++;

      File tmp = new File(getTestDir());
      tmp.mkdirs();
   }

   // Test Simple getBytes
   @Test
   public void testGetBytes() throws Exception {
      LargeMessageControllerImpl buffer = create15BytesSample();

      for (int i = 1; i <= 15; i++) {
         try {
            assertEquals(i, buffer.readByte());
         } catch (Exception e) {
            throw new Exception("Exception at position " + i, e);
         }
      }

      try {
         buffer.readByte();
         fail("supposed to throw an exception");
      } catch (IndexOutOfBoundsException e) {
      }
   }

   // Test for void getBytes(final int index, final byte[] dst)
   @Test
   public void testGetBytesIByteArray() throws Exception {
      LargeMessageControllerImpl buffer = create15BytesSample();

      byte[] bytes = new byte[15];
      buffer.getBytes(0, bytes);

      validateAgainstSample(bytes);

      try {
         buffer = create15BytesSample();

         bytes = new byte[16];
         buffer.getBytes(0, bytes);
         fail("supposed to throw an exception");
      } catch (java.lang.IndexOutOfBoundsException e) {
      }
   }

   // testing void getBytes(int index, ChannelBuffer dst, int dstIndex, int length)
   @Test
   public void testGetBytesILChannelBufferII() throws Exception {
      LargeMessageControllerImpl buffer = create15BytesSample();

      ActiveMQBuffer dstBuffer = ActiveMQBuffers.fixedBuffer(20);

      dstBuffer.setIndex(0, 5);

      buffer.getBytes(0, dstBuffer);

      byte[] compareBytes = new byte[15];
      dstBuffer.getBytes(5, compareBytes);

      validateAgainstSample(compareBytes);
   }

   // testing void getBytes(int index, ChannelBuffer dst, int dstIndex, int length)
   @Test
   public void testReadIntegers() throws Exception {
      LargeMessageControllerImpl buffer = createBufferWithIntegers(3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

      for (int i = 1; i <= 15; i++) {
         assertEquals(i, buffer.readInt());
      }

      try {
         buffer.readByte();
         fail("supposed to throw an exception");
      } catch (IndexOutOfBoundsException e) {
      }
   }

   @Test
   public void testReadIntegersOverStream() throws Exception {
      LargeMessageControllerImpl buffer = createBufferWithIntegers(3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
      ActiveMQBufferInputStream is = new ActiveMQBufferInputStream(buffer);
      DataInputStream dataInput = new DataInputStream(is);

      for (int i = 1; i <= 15; i++) {
         assertEquals(i, dataInput.readInt());
      }

      assertEquals(-1, dataInput.read());
      dataInput.close();
   }

   // testing void getBytes(int index, ChannelBuffer dst, int dstIndex, int length)
   @Test
   public void testReadLongs() throws Exception {
      LargeMessageControllerImpl buffer = createBufferWithLongs(3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

      for (int i = 1; i <= 15; i++) {
         assertEquals(i, buffer.readLong());
      }

      try {
         buffer.readByte();
         fail("supposed to throw an exception");
      } catch (IndexOutOfBoundsException e) {
      }
   }

   @Test
   public void testReadLongsOverStream() throws Exception {
      LargeMessageControllerImpl buffer = createBufferWithLongs(3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
      ActiveMQBufferInputStream is = new ActiveMQBufferInputStream(buffer);
      DataInputStream dataInput = new DataInputStream(is);

      for (int i = 1; i <= 15; i++) {
         assertEquals(i, dataInput.readLong());
      }

      assertEquals(-1, dataInput.read());
      dataInput.close();
   }

   @Test
   public void testReadData() throws Exception {
      ActiveMQBuffer dynamic = ActiveMQBuffers.dynamicBuffer(1);

      String str1 = RandomUtil.randomUUIDString();
      String str2 = RandomUtil.randomUUIDString();
      double d1 = RandomUtil.randomDouble();
      float f1 = RandomUtil.randomFloat();

      dynamic.writeUTF(str1);
      dynamic.writeString(str2);
      dynamic.writeDouble(d1);
      dynamic.writeFloat(f1);

      LargeMessageControllerImpl readBuffer = splitBuffer(3, dynamic.toByteBuffer().array());

      assertEquals(str1, readBuffer.readUTF());
      assertEquals(str2, readBuffer.readString());
      assertEquals(d1, readBuffer.readDouble(), 0.000001);
      assertEquals(f1, readBuffer.readFloat(), 0.000001);
   }

   private File getTestFile() {
      return new File(getTestDir(), "temp." + tmpFileCounter + ".file");
   }

   @Test
   public void testReadDataOverCached() throws Exception {
      clearDataRecreateServerDirs();

      ActiveMQBuffer dynamic = ActiveMQBuffers.dynamicBuffer(1);

      String str1 = RandomUtil.randomUUIDString();
      String str2 = RandomUtil.randomUUIDString();
      double d1 = RandomUtil.randomDouble();
      float f1 = RandomUtil.randomFloat();

      dynamic.writeUTF(str1);
      dynamic.writeString(str2);
      dynamic.writeDouble(d1);
      dynamic.writeFloat(f1);

      LargeMessageControllerImpl readBuffer = splitBuffer(3, dynamic.toByteBuffer().array(), getTestFile());

      assertEquals(str1, readBuffer.readUTF());
      assertEquals(str2, readBuffer.readString());
      assertEquals(d1, readBuffer.readDouble(), 0.00000001);
      assertEquals(f1, readBuffer.readFloat(), 0.000001);

      readBuffer.readerIndex(0);

      assertEquals(str1, readBuffer.readUTF());
      assertEquals(str2, readBuffer.readString());
      assertEquals(d1, readBuffer.readDouble(), 0.00000001);
      assertEquals(f1, readBuffer.readFloat(), 0.000001);

      readBuffer.close();
   }

   @Test
   public void testReadPartialData() throws Exception {

      final LargeMessageControllerImpl buffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 10, 10);

      buffer.addPacket(new byte[]{0, 1, 2, 3, 4}, 1, true);

      byte[] bytes = new byte[30];
      buffer.readBytes(bytes, 0, 5);

      for (byte i = 0; i < 5; i++) {
         assertEquals(i, bytes[i]);
      }

      final CountDownLatch latchGo = new CountDownLatch(1);

      final AtomicInteger errorCount = new AtomicInteger(0);

      Thread t = new Thread(() -> {

         try {
            latchGo.countDown();
            buffer.readBytes(new byte[5]);
         } catch (IndexOutOfBoundsException ignored) {
         } catch (IllegalAccessError ignored) {

         } catch (Throwable e) {
            e.printStackTrace();
            errorCount.incrementAndGet();
         }
      });

      t.start();

      ActiveMQTestBase.waitForLatch(latchGo);

      buffer.cancel();

      t.join();

      assertEquals(0, errorCount.get());

   }

   @Test
   public void testInterruptData() throws Exception {
      LargeMessageControllerImpl readBuffer = splitBuffer(3, new byte[]{0, 1, 2, 3, 4});

      byte[] bytes = new byte[30];
      readBuffer.readBytes(bytes, 0, 5);

      for (byte i = 0; i < 5; i++) {
         assertEquals(i, bytes[i]);
      }
   }

   @Test
   public void testSplitBufferOnFile() throws Exception {
      LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 1024 * 1024, 1, getTestFile(), 1024);
      try {

         long count = 0;
         for (int i = 0; i < 10; i++) {
            byte[] buffer = new byte[10240];
            for (int j = 0; j < 10240; j++) {
               buffer[j] = getSamplebyte(count++);
            }
            outBuffer.addPacket(buffer, 1, true);
         }

         outBuffer.readerIndex(0);

         for (int i = 0; i < 10240 * 10; i++) {
            assertEquals(getSamplebyte(i), outBuffer.readByte(), "position " + i);
         }

         outBuffer.readerIndex(0);

         for (int i = 0; i < 10240 * 10; i++) {
            assertEquals(getSamplebyte(i), outBuffer.readByte(), "position " + i);
         }
      } finally {
         outBuffer.close();
      }

   }

   @Test
   public void testStreamData() throws Exception {
      final LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 1024 * 11 + 123, 1000);

      final PipedOutputStream output = new PipedOutputStream();
      final PipedInputStream input = new PipedInputStream(output);

      final AtomicInteger errors = new AtomicInteger(0);

      // Done reading 3 elements
      final CountDownLatch done1 = new CountDownLatch(1);
      // Done with the thread
      final CountDownLatch done2 = new CountDownLatch(1);

      final AtomicInteger count = new AtomicInteger(0);
      final AtomicInteger totalBytes = new AtomicInteger(0);

      Thread treader = new Thread("treader") {
         @Override
         public void run() {
            try {

               byte[] line = new byte[1024];
               int dataRead = 0;
               while (dataRead >= 0) {
                  dataRead = input.read(line);
                  if (dataRead > 0) {
                     totalBytes.addAndGet(dataRead);
                     if (count.incrementAndGet() == 3) {
                        done1.countDown();
                     }
                  }
               }
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            } finally {
               done1.countDown();
               done2.countDown();
            }
         }
      };

      treader.setDaemon(true);
      treader.start();

      for (int i = 0; i < 3; i++) {
         outBuffer.addPacket(new byte[1024], 1, true);
      }

      outBuffer.setOutputStream(output);

      final CountDownLatch waiting = new CountDownLatch(1);

      Thread twaiter = new Thread("twaiter") {
         @Override
         public void run() {
            try {
               outBuffer.waitCompletion(0);
               waiting.countDown();
            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
      };

      twaiter.setDaemon(true);
      twaiter.start();

      assertTrue(done1.await(10, TimeUnit.SECONDS));

      assertEquals(3, count.get());
      assertEquals(1024 * 3, totalBytes.get());

      for (int i = 0; i < 8; i++) {
         outBuffer.addPacket(new byte[1024], 1, true);
      }

      assertEquals(1, waiting.getCount());

      outBuffer.addPacket(new byte[123], 1, false);

      assertTrue(done2.await(10, TimeUnit.SECONDS));

      assertTrue(waiting.await(10, TimeUnit.SECONDS));

      assertEquals(12, count.get());
      assertEquals(1024 * 11 + 123, totalBytes.get());

      treader.join();

      twaiter.join();

      assertEquals(0, errors.get());
      input.close();
   }

   @Test
   public void testStreamDataWaitCompletionOnCompleteBuffer() throws Exception {
      final LargeMessageControllerImpl outBuffer = create15BytesSample();

      outBuffer.saveBuffer(new OutputStream() {
         @Override
         public void write(final int b) throws IOException {
            // nohting to be done
         }
      });
   }

   @Test
   public void testStreamDataWaitCompletionOnInCompleteBuffer() throws Exception {
      final LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 5, 1000);

      class FakeOutputStream extends OutputStream {

         @Override
         public void write(int b) throws IOException {
         }
      }

      outBuffer.setOutputStream(new FakeOutputStream());

      long time = System.currentTimeMillis();
      try {
         outBuffer.waitCompletion(0);
         fail("supposed to throw an exception");
      } catch (ActiveMQException e) {
      }

      assertTrue(System.currentTimeMillis() - time > 1000, "It was supposed to wait at least 1 second");
   }

   @Test
   public void testStreamDataWaitCompletionOnException() throws Exception {
      final LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 5, 5000);

      class FakeOutputStream extends OutputStream {

         @Override
         public void write(int b) throws IOException {
            throw new IOException();
         }
      }

      outBuffer.setOutputStream(new FakeOutputStream());

      CountDownLatch latch = new CountDownLatch(1);
      new Thread(() -> {
         try {
            outBuffer.waitCompletion(0);
            fail("supposed to throw an exception");
         } catch (ActiveMQException e) {
            latch.countDown();
         }
      }).start();
      outBuffer.addPacket(RandomUtil.randomBytes(), 1, true);
      assertTrue(latch.await(3, TimeUnit.SECONDS), "The IOException should trigger an immediate failure");
   }

   @Test
   public void testStreamDataWaitCompletionOnSlowComingBuffer() throws Exception {
      final LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 5, 1000);

      class FakeOutputStream extends OutputStream {

         @Override
         public void write(int b) throws IOException {
         }
      }

      outBuffer.setOutputStream(new FakeOutputStream());

      Thread sender = new Thread(() -> {
         try {
            Thread.sleep(100);
            outBuffer.addPacket(new byte[]{0}, 1, true);
            Thread.sleep(100);
            outBuffer.addPacket(new byte[]{0}, 1, true);
            Thread.sleep(200);
            outBuffer.addPacket(new byte[]{0}, 1, false);
         } catch (Exception e) {
         }
      });

      sender.start();
      assertTrue(outBuffer.waitCompletion(5000));
      sender.join();
   }

   @Test
   public void testErrorOnSetStreaming() throws Exception {
      long start = System.currentTimeMillis();
      final LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), 5, 30000);

      outBuffer.addPacket(new byte[]{0, 1, 2, 3, 4}, 1, true);

      final CountDownLatch latchBytesWritten1 = new CountDownLatch(5);
      final CountDownLatch latchBytesWritten2 = new CountDownLatch(10);

      outBuffer.setOutputStream(new OutputStream() {
         @Override
         public void write(final int b) throws IOException {
            latchBytesWritten1.countDown();
            latchBytesWritten2.countDown();
         }
      });

      ActiveMQTestBase.waitForLatch(latchBytesWritten1);

      try {
         outBuffer.readByte();
         fail("supposed to throw an exception");
      } catch (IllegalAccessError ignored) {
      }

      assertTrue(System.currentTimeMillis() - start < 30000, "It waited too much");

   }

   @Test
   public void testReadBytesOnStreaming() throws Exception {
      byte[] byteArray = new byte[1024];
      for (int i = 0; i < byteArray.length; i++) {
         byteArray[i] = getSamplebyte(i);
      }

      ActiveMQBuffer splitbuffer = splitBuffer(3, byteArray);

      ActiveMQBufferInputStream is = new ActiveMQBufferInputStream(splitbuffer);

      for (int i = 0; i < 100; i++) {
         assertEquals(getSamplebyte(i), (byte) is.read());
      }

      for (int i = 100; i < byteArray.length; i += 10) {
         byte[] readBytes = new byte[10];

         int size = is.read(readBytes);

         for (int j = 0; j < size; j++) {
            assertEquals(getSamplebyte(i + j), readBytes[j]);
         }
      }
      is.close();
   }

   private LargeMessageControllerImpl create15BytesSample() throws Exception {
      return splitBuffer(5, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
   }

   private LargeMessageControllerImpl createBufferWithIntegers(final int splitFactor,
                                                               final int... values) throws Exception {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream(values.length * 4);
      DataOutputStream dataOut = new DataOutputStream(byteOut);

      for (int value : values) {
         dataOut.writeInt(value);
      }

      return splitBuffer(splitFactor, byteOut.toByteArray());
   }

   private LargeMessageControllerImpl createBufferWithLongs(final int splitFactor,
                                                            final long... values) throws Exception {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream(values.length * 8);
      DataOutputStream dataOut = new DataOutputStream(byteOut);

      for (long value : values) {
         dataOut.writeLong(value);
      }

      return splitBuffer(splitFactor, byteOut.toByteArray());
   }

   private LargeMessageControllerImpl splitBuffer(final int splitFactor, final byte[] bytes) throws Exception {
      return splitBuffer(splitFactor, bytes, null);
   }

   private LargeMessageControllerImpl splitBuffer(final int splitFactor,
                                                  final byte[] bytes,
                                                  final File file) throws Exception {
      LargeMessageControllerImpl outBuffer = new LargeMessageControllerImpl(new FakeConsumerInternal(), bytes.length, 5000, file);

      ByteArrayInputStream input = new ByteArrayInputStream(bytes);

      while (true) {
         byte[] splitElement = new byte[splitFactor];
         int size = input.read(splitElement);
         if (size <= 0) {
            break;
         }
         if (size < splitFactor) {
            byte[] newSplit = new byte[size];
            System.arraycopy(splitElement, 0, newSplit, 0, size);

            outBuffer.addPacket(newSplit, 1, input.available() > 0);
         } else {
            outBuffer.addPacket(splitElement, 1, input.available() > 0);
         }
      }

      return outBuffer;

   }

   private void validateAgainstSample(final byte[] bytes) {
      for (int i = 1; i <= 15; i++) {
         assertEquals(i, bytes[i - 1]);
      }
   }

   static class FakeConsumerInternal implements ClientConsumerInternal {

      @Override
      public ConsumerContext getConsumerContext() {
         return new ActiveMQConsumerContext(0);
      }

      @Override
      public void close() throws ActiveMQException {
      }

      @Override
      public Exception getLastException() {
         return null;
      }

      @Override
      public MessageHandler getMessageHandler() throws ActiveMQException {
         return null;
      }

      @Override
      public boolean isClosed() {
         return false;
      }

      @Override
      public ClientMessage receive() throws ActiveMQException {
         return null;
      }

      @Override
      public Thread getCurrentThread() {
         return null;
      }

      @Override
      public ClientMessage receive(final long timeout) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientMessage receiveImmediate() throws ActiveMQException {
         return null;
      }

      @Override
      public FakeConsumerInternal setMessageHandler(final MessageHandler handler) throws ActiveMQException {
         return this;
      }

      @Override
      public void acknowledge(final ClientMessage message) throws ActiveMQException {
      }

      @Override
      public void individualAcknowledge(ClientMessage message) throws ActiveMQException {
      }

      @Override
      public void cleanUp() throws ActiveMQException {
      }

      @Override
      public void clear(boolean waitForOnMessage) throws ActiveMQException {
      }

      @Override
      public void clearAtFailover() {
      }

      @Override
      public void flowControl(final int messageBytes, final boolean discountSlowConsumer) throws ActiveMQException {
      }

      @Override
      public void flushAcks() throws ActiveMQException {
      }

      @Override
      public int getBufferSize() {

         return 0;
      }

      @Override
      public int getClientWindowSize() {

         return 0;
      }

      @Override
      public int getInitialWindowSize() {
         return 0;
      }

      @Override
      public SimpleString getFilterString() {

         return null;
      }

      @Override
      public int getPriority() {
         return 0;
      }

      public long getID() {

         return 0;
      }

      @Override
      public SimpleString getQueueName() {

         return null;
      }

      @Override
      public boolean isBrowseOnly() {

         return false;
      }

      @Override
      public void handleMessage(ClientMessageInternal message) throws Exception {
      }

      @Override
      public void handleLargeMessage(ClientLargeMessageInternal clientLargeMessage,
                                     long largeMessageSize) throws Exception {
      }

      @Override
      public void handleLargeMessageContinuation(byte[] chunk,
                                                 int flowControlSize,
                                                 boolean isContinues) throws Exception {
      }

      @Override
      public void start() {

      }

      public void stop() throws ActiveMQException {

      }

      @Override
      public void stop(boolean waitForOnMessage) throws ActiveMQException {
      }

      @Override
      public ClientSession.QueueQuery getQueueInfo() {
         return null;
      }

      @Override
      public long getForceDeliveryCount() {
         return 0;
      }

      @Override
      public ClientConsumer setManualFlowMessageHandler(MessageHandler handler) throws ActiveMQException {
         return null;
      }

      @Override
      public void resetIfSlowConsumer() {
      }

      public ClientSessionInternal getSession() {
         return null;
      }

      @Override
      public Thread prepareForClose(FutureLatch future) throws ActiveMQException {
         return null;
      }
   }
}
