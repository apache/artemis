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
package org.apache.activemq.artemis.protocol.amqp.util;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for behavior of NettyWritable
 */
public class NettyWritableTest {

   @Test
   public void testGetBuffer() {
      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertSame(buffer, writable.getByteBuf());
   }

   @Test
   public void testLimit() {
      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(buffer.capacity(), writable.limit());
   }

   @Test
   public void testRemaining() {
      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(buffer.maxCapacity(), writable.remaining());
      writable.put((byte) 0);
      assertEquals(buffer.maxCapacity() - 1, writable.remaining());
   }

   @Test
   public void testHasRemaining() {
      ByteBuf buffer = Unpooled.buffer(100, 100);
      NettyWritable writable = new NettyWritable(buffer);

      assertTrue(writable.hasRemaining());
      writable.put((byte) 0);
      assertTrue(writable.hasRemaining());
      buffer.writerIndex(buffer.maxCapacity());
      assertFalse(writable.hasRemaining());
   }

   @Test
   public void testGetPosition() {
      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(0, writable.position());
      writable.put((byte) 0);
      assertEquals(1, writable.position());
   }

   @Test
   public void testSetPosition() {
      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(0, writable.position());
      writable.position(1);
      assertEquals(1, writable.position());
   }

   @Test
   public void testPutByteBuffer() {
      ByteBuffer input = ByteBuffer.allocate(1024);
      input.put((byte) 1);
      input.flip();

      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(0, writable.position());
      writable.put(input);
      assertEquals(1, writable.position());
   }

   @Test
   public void testPutByteBuf() {
      ByteBuf input = Unpooled.buffer();
      input.writeByte((byte) 1);

      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(0, writable.position());
      writable.put(input);
      assertEquals(1, writable.position());
   }

   @Test
   public void testPutReadableBuffer() {
      doPutReadableBufferTestImpl(true);
      doPutReadableBufferTestImpl(false);
   }

   @Test
   public void testPutReadableBufferWithOffsetAndNonZeroPosition() {
      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      ByteBuffer source = ByteBuffer.allocate(20);
      source.put(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19});

      source.position(5);
      source.limit(10);

      writable.put(source);

      assertEquals(5, writable.position());
      assertEquals(5, buffer.readableBytes());

      byte[] check = new byte[5];
      buffer.readBytes(check);

      assertArrayEquals(new byte[]{5, 6, 7, 8, 9}, check);
   }

   private void doPutReadableBufferTestImpl(boolean readOnly) {
      ByteBuffer buf = ByteBuffer.allocate(1024);
      buf.put((byte) 1);
      buf.flip();
      if (readOnly) {
         buf = buf.asReadOnlyBuffer();
      }

      ReadableBuffer input = new ReadableBuffer.ByteBufferReader(buf);

      if (readOnly) {
         assertFalse(input.hasArray(), "Expected buffer not to hasArray()");
      } else {
         assertTrue(input.hasArray(), "Expected buffer to hasArray()");
      }

      ByteBuf buffer = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(buffer);

      assertEquals(0, writable.position());
      writable.put(input);
      assertEquals(1, writable.position());
   }
}
