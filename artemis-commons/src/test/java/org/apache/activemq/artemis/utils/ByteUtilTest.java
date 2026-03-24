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
package org.apache.activemq.artemis.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.PlatformDependent;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ByteUtilTest {

   private static final Logger log = Logger.getLogger(ByteUtilTest.class);

   @Test
   public void testBytesToString() {
      byte[] byteArray = new byte[]{0, 1, 2, 3};

      testEquals("0001 0203", ByteUtil.bytesToHex(byteArray, 2));
      testEquals("00 01 02 03", ByteUtil.bytesToHex(byteArray, 1));
      testEquals("000102 03", ByteUtil.bytesToHex(byteArray, 3));
   }

   @Test
   public void testNonASCII() {
      assertEquals("aA", ByteUtil.toSimpleString(new byte[]{97, 0, 65, 0}));
      assertEquals(ByteUtil.NON_ASCII_STRING, ByteUtil.toSimpleString(new byte[]{0, 97, 0, 65}));
      log.debug(ByteUtil.toSimpleString(new byte[]{0, 97, 0, 65}));
   }

   @Test
   public void testMaxString() {
      byte[] byteArray = new byte[20 * 1024];
      log.debug(ByteUtil.maxString(ByteUtil.bytesToHex(byteArray, 2), 150));
   }

   void testEquals(String string1, String string2) {
      if (!string1.equals(string2)) {
         Assert.fail("String are not the same:=" + string1 + "!=" + string2);
      }
   }

   @Test
   public void testTextBytesToLongBytes() {
      long[] factor = new long[] {1, 5, 10};
      String[] type = new String[]{"", "b", "k", "m", "g"};
      long[] size = new long[]{1, 1, 1024, 1024 * 1024, 1024 * 1024 * 1024};

      for (int i = 0; i < 3; i++) {
         for (int j = 0; j < type.length; j++) {
            assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + type[j]));
            assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + " " + type[j]));
            assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + type[j].toUpperCase()));
            assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + " " + type[j].toUpperCase()));
            if (j >= 2) {
               assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + type[j] + "b"));
               assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + " " + type[j] + "b"));
               assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + type[j].toUpperCase() + "B"));
               assertEquals(factor[i] * size[j], ByteUtil.convertTextBytes(factor[i] + " " + type[j].toUpperCase() + "B"));
            }
         }
      }
   }

   @Test
   public void testUnsafeUnalignedByteArrayHashCode() {
      Assume.assumeTrue(PlatformDependent.hasUnsafe());
      Assume.assumeTrue(PlatformDependent.isUnaligned());
      Map<byte[], Integer> map = new LinkedHashMap<>();
      map.put(new byte[0], 1);
      map.put(new byte[]{1}, 32);
      map.put(new byte[]{2}, 33);
      map.put(new byte[]{0, 1}, 962);
      map.put(new byte[]{1, 2}, 994);
      if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
         map.put(new byte[]{0, 1, 2, 3, 4, 5}, 63504931);
         map.put(new byte[]{6, 7, 8, 9, 0, 1}, -1603953111);
         map.put(new byte[]{-1, -1, -1, (byte) 0xE1}, 1);
      } else {
         map.put(new byte[]{0, 1, 2, 3, 4, 5}, 1250309600);
         map.put(new byte[]{6, 7, 8, 9, 0, 1}, -417148442);
         map.put(new byte[]{-1, -1, -1, (byte) 0xE1}, -503316450);
      }
      for (Map.Entry<byte[], Integer> e : map.entrySet()) {
         assertEquals("input = " + Arrays.toString(e.getKey()), e.getValue().intValue(), ByteUtil.hashCode(e.getKey()));
      }
   }

   @Test
   public void testNoUnsafeAlignedByteArrayHashCode() {
      Assume.assumeFalse(PlatformDependent.hasUnsafe());
      Assume.assumeFalse(PlatformDependent.isUnaligned());
      ArrayList<byte[]> inputs = new ArrayList<>();
      inputs.add(new byte[0]);
      inputs.add(new byte[]{1});
      inputs.add(new byte[]{2});
      inputs.add(new byte[]{0, 1});
      inputs.add(new byte[]{1, 2});
      inputs.add(new byte[]{0, 1, 2, 3, 4, 5});
      inputs.add(new byte[]{6, 7, 8, 9, 0, 1});
      inputs.add(new byte[]{-1, -1, -1, (byte) 0xE1});
      inputs.forEach(input -> assertEquals("input = " + Arrays.toString(input), Arrays.hashCode(input), ByteUtil.hashCode(input)));
   }

   @Test
   public void testTextBytesToLongBytesNegative() {
      try {
         ByteUtil.convertTextBytes("x");
         fail();
      } catch (Exception e) {
         assertTrue(e instanceof IllegalArgumentException);
      }
   }

   private static byte[] duplicateRemaining(ByteBuffer buffer, int offset, int bytes) {
      final int end = offset + bytes;
      final int expectedRemaining = buffer.capacity() - end;
      //it is handling the case of <0 just to allow from to > capacity
      if (expectedRemaining <= 0) {
         return null;
      }
      final byte[] remaining = new byte[expectedRemaining];
      final ByteBuffer duplicate = buffer.duplicate();
      duplicate.clear().position(end);
      duplicate.get(remaining, 0, expectedRemaining);
      return remaining;
   }

   private static byte[] duplicateBefore(ByteBuffer buffer, int offset) {
      if (offset <= 0) {
         return null;
      }
      final int size = Math.min(buffer.capacity(), offset);
      final byte[] remaining = new byte[size];
      final ByteBuffer duplicate = buffer.duplicate();
      duplicate.clear();
      duplicate.get(remaining, 0, size);
      return remaining;
   }

   private static void shouldZeroesByteBuffer(ByteBuffer buffer, int offset, int bytes) {
      final byte[] originalBefore = duplicateBefore(buffer, offset);
      final byte[] originalRemaining = duplicateRemaining(buffer, offset, bytes);
      final int position = buffer.position();
      final int limit = buffer.limit();
      ByteUtil.zeros(buffer, offset, bytes);
      Assert.assertEquals(position, buffer.position());
      Assert.assertEquals(limit, buffer.limit());
      final byte[] zeros = new byte[bytes];
      final byte[] content = new byte[bytes];
      final ByteBuffer duplicate = buffer.duplicate();
      duplicate.clear().position(offset);
      duplicate.get(content, 0, bytes);
      Assert.assertArrayEquals(zeros, content);
      if (originalRemaining != null) {
         final byte[] remaining = new byte[duplicate.remaining()];
         //duplicate position has been moved of bytes
         duplicate.get(remaining);
         Assert.assertArrayEquals(originalRemaining, remaining);
      }
      if (originalBefore != null) {
         final byte[] before = new byte[offset];
         //duplicate position has been moved of bytes: need to reset it
         duplicate.position(0);
         duplicate.get(before);
         Assert.assertArrayEquals(originalBefore, before);
      }
   }

   private ByteBuffer fill(ByteBuffer buffer, int offset, int length, byte value) {
      for (int i = 0; i < length; i++) {
         buffer.put(offset + i, value);
      }
      return buffer;
   }

   @Test
   public void shouldZeroesDirectByteBuffer() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 32;
      final int offset = 1;
      final ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
      try {
         fill(buffer, 0, capacity, one);
         shouldZeroesByteBuffer(buffer, offset, bytes);
      } finally {
         if (PlatformDependent.hasUnsafe()) {
            PlatformDependent.freeDirectBuffer(buffer);
         }
      }
   }

   @Test
   public void shouldZeroesLimitedDirectByteBuffer() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 32;
      final int offset = 1;
      final ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
      try {
         fill(buffer, 0, capacity, one);
         buffer.limit(0);
         shouldZeroesByteBuffer(buffer, offset, bytes);
      } finally {
         if (PlatformDependent.hasUnsafe()) {
            PlatformDependent.freeDirectBuffer(buffer);
         }
      }
   }

   @Test
   public void shouldZeroesHeapByteBuffer() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 32;
      final int offset = 1;
      final ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      shouldZeroesByteBuffer(buffer, offset, bytes);
   }

   @Test
   public void shouldZeroesLimitedHeapByteBuffer() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 32;
      final int offset = 1;
      final ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      buffer.limit(0);
      shouldZeroesByteBuffer(buffer, offset, bytes);
   }

   @Test(expected = ReadOnlyBufferException.class)
   public void shouldFailWithReadOnlyHeapByteBuffer() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 32;
      final int offset = 1;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      buffer = buffer.asReadOnlyBuffer();
      shouldZeroesByteBuffer(buffer, offset, bytes);
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void shouldFailIfOffsetIsGreaterOrEqualHeapByteBufferCapacity() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 0;
      final int offset = 64;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      try {
         shouldZeroesByteBuffer(buffer, offset, bytes);
      } catch (IndexOutOfBoundsException expectedEx) {
         //verify that the buffer hasn't changed
         final byte[] originalContent = duplicateRemaining(buffer, 0, 0);
         final byte[] expectedContent = new byte[capacity];
         Arrays.fill(expectedContent, one);
         Assert.assertArrayEquals(expectedContent, originalContent);
         throw expectedEx;
      }
   }

   @Test(expected = IllegalArgumentException.class)
   public void shouldFailIfOffsetIsNegative() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 1;
      final int offset = -1;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      try {
         shouldZeroesByteBuffer(buffer, offset, bytes);
      } catch (IndexOutOfBoundsException expectedEx) {
         //verify that the buffer hasn't changed
         final byte[] originalContent = duplicateRemaining(buffer, 0, 0);
         final byte[] expectedContent = new byte[capacity];
         Arrays.fill(expectedContent, one);
         Assert.assertArrayEquals(expectedContent, originalContent);
         throw expectedEx;
      }
   }

   @Test(expected = IllegalArgumentException.class)
   public void shouldFailIfBytesIsNegative() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = -1;
      final int offset = 0;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      try {
         shouldZeroesByteBuffer(buffer, offset, bytes);
      } catch (IndexOutOfBoundsException expectedEx) {
         //verify that the buffer hasn't changed
         final byte[] originalContent = duplicateRemaining(buffer, 0, 0);
         final byte[] expectedContent = new byte[capacity];
         Arrays.fill(expectedContent, one);
         Assert.assertArrayEquals(expectedContent, originalContent);
         throw expectedEx;
      }
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void shouldFailIfExceedingHeapByteBufferCapacity() {
      final byte one = (byte) 1;
      final int capacity = 64;
      final int bytes = 65;
      final int offset = 1;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);
      fill(buffer, 0, capacity, one);
      try {
         shouldZeroesByteBuffer(buffer, offset, bytes);
      } catch (IndexOutOfBoundsException expectedEx) {
         //verify that the buffer hasn't changed
         final byte[] originalContent = duplicateRemaining(buffer, 0, 0);
         final byte[] expectedContent = new byte[capacity];
         Arrays.fill(expectedContent, one);
         Assert.assertArrayEquals(expectedContent, originalContent);
         throw expectedEx;
      }
   }

   @Test
   public void testIntToBytes() {
      internalIntToBytesTest(RandomUtil.randomInt(), null);
      internalIntToBytesTest(0, new byte[]{0, 0, 0, 0});
      internalIntToBytesTest(-1, new byte[] {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
      internalIntToBytesTest(Integer.MIN_VALUE, new byte[] {(byte)0x80, (byte)0x00, (byte)0x00, (byte)0x00});
      internalIntToBytesTest(Integer.MAX_VALUE, new byte[] {(byte)0x7F, (byte)0xFF, (byte)0xFF, (byte)0xFF});
   }

   private void internalIntToBytesTest(int intValue, byte[] manualExpect) {
      byte[] expected = ByteBuffer.allocate(4).putInt(intValue).array();
      byte[] actual = ByteUtil.intToBytes(intValue);
      if (manualExpect != null) {
         Assert.assertEquals(4, manualExpect.length);
         assertArrayEquals(manualExpect, actual);
      }
      assertArrayEquals(expected, actual);

      assertEquals(intValue, ByteUtil.bytesToInt(expected));
      assertEquals(intValue, ByteUtil.bytesToInt(actual));
   }

   @Test
   public void testLongToBytes() {
      internalLongToBytesTest(RandomUtil.randomLong(), null);
      internalLongToBytesTest(0, new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
      internalLongToBytesTest(-1, new byte[] {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
      internalLongToBytesTest(Long.MIN_VALUE, new byte[] {(byte)0x80, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00});
      internalLongToBytesTest(Long.MAX_VALUE, new byte[] {(byte)0x7F, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
   }

   private void internalLongToBytesTest(long longValue, byte[] manualExpected) {
      byte[] expected = ByteBuffer.allocate(8).putLong(longValue).array();
      byte[] actual = ByteUtil.longToBytes(longValue);
      if (manualExpected != null) {
         Assert.assertEquals(8, manualExpected.length);
         assertArrayEquals(manualExpected, actual);
      }
      assertArrayEquals(expected, actual);

      assertEquals(longValue, ByteUtil.bytesToLong(expected));
      assertEquals(longValue, ByteUtil.bytesToLong(actual));
   }

   @Test
   public void testDoubleLongToBytes() {
      long randomLong1 = RandomUtil.randomLong();
      long randomLong2 = RandomUtil.randomLong();
      ByteBuffer buffer = ByteBuffer.allocate(16);
      buffer.putLong(randomLong1);
      buffer.putLong(randomLong2);
      byte[] assertContent = buffer.array();

      byte[] convertedContent = ByteUtil.doubleLongToBytes(randomLong1, randomLong2);

      assertArrayEquals(assertContent, convertedContent);
   }

   @Test(expected = IllegalArgumentException.class)
   public void shouldEnsureExactWritableFailToEnlargeWrappedByteBuf() {
      byte[] wrapped = new byte[32];
      ByteBuf buffer = Unpooled.wrappedBuffer(wrapped);
      buffer.writerIndex(wrapped.length);
      ByteUtil.ensureExactWritable(buffer, 1);
   }

   @Test
   public void shouldEnsureExactWritableNotEnlargeBufferWithEnoughSpace() {
      byte[] wrapped = new byte[32];
      ByteBuf buffer = Unpooled.wrappedBuffer(wrapped);
      buffer.writerIndex(wrapped.length - 1);
      ByteUtil.ensureExactWritable(buffer, 1);
      Assert.assertSame(wrapped, buffer.array());
   }

   @Test
   public void shouldEnsureExactWritableEnlargeBufferWithoutEnoughSpace() {
      ByteBuf buffer = Unpooled.buffer(32);
      buffer.writerIndex(32);
      ByteUtil.ensureExactWritable(buffer, 1);
      Assert.assertEquals(33, buffer.capacity());
   }

}
