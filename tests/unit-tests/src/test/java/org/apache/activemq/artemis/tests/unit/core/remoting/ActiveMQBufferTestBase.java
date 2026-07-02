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
package org.apache.activemq.artemis.tests.unit.core.remoting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class ActiveMQBufferTestBase extends ActiveMQTestBase {


   private ActiveMQBuffer wrapper;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      wrapper = createBuffer();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      wrapper = null;

      super.tearDown();
   }

   protected abstract ActiveMQBuffer createBuffer();

   @Test
   public void testNullString() throws Exception {
      assertNull(putAndGetNullableString(null));
   }

   @Test
   public void testEmptyString() throws Exception {
      String result = putAndGetNullableString("");

      assertNotNull(result);
      assertEquals("", result);
   }

   @Test
   public void testNonEmptyString() throws Exception {
      String junk = RandomUtil.randomUUIDString();

      String result = putAndGetNullableString(junk);

      assertNotNull(result);
      assertEquals(junk, result);
   }

   @Test
   public void testNullSimpleString() throws Exception {
      assertNull(putAndGetNullableSimpleString(null));
   }

   @Test
   public void testEmptySimpleString() throws Exception {
      SimpleString emptySimpleString = SimpleString.of("");
      SimpleString result = putAndGetNullableSimpleString(emptySimpleString);

      assertNotNull(result);
      ActiveMQTestBase.assertEqualsByteArrays(emptySimpleString.getData(), result.getData());
   }

   @Test
   public void testNonEmptySimpleString() throws Exception {
      SimpleString junk = RandomUtil.randomUUIDSimpleString();
      SimpleString result = putAndGetNullableSimpleString(junk);

      assertNotNull(result);
      ActiveMQTestBase.assertEqualsByteArrays(junk.getData(), result.getData());
   }

   @Test
   public void testByte() throws Exception {
      byte b = RandomUtil.randomByte();
      wrapper.writeByte(b);

      assertEquals(b, wrapper.readByte());
   }

   @Test
   public void testUnsignedByte() throws Exception {
      byte b = (byte) 0xff;
      wrapper.writeByte(b);

      assertEquals(255, wrapper.readUnsignedByte());

      b = (byte) 0xf;
      wrapper.writeByte(b);

      assertEquals(b, wrapper.readUnsignedByte());
   }

   @Test
   public void testBytes() throws Exception {
      byte[] bytes = RandomUtil.randomBytes();
      wrapper.writeBytes(bytes);

      byte[] b = new byte[bytes.length];
      wrapper.readBytes(b);
      ActiveMQTestBase.assertEqualsByteArrays(bytes, b);
   }

   @Test
   public void testBytesWithLength() throws Exception {
      byte[] bytes = RandomUtil.randomBytes();
      // put only half of the bytes
      wrapper.writeBytes(bytes, 0, bytes.length / 2);

      byte[] b = new byte[bytes.length / 2];
      wrapper.readBytes(b, 0, b.length);
      ActiveMQTestBase.assertEqualsByteArrays(b.length, bytes, b);
   }

   @Test
   public void testPutTrueBoolean() throws Exception {
      wrapper.writeBoolean(true);

      assertTrue(wrapper.readBoolean());
   }

   @Test
   public void testPutFalseBoolean() throws Exception {
      wrapper.writeBoolean(false);

      assertFalse(wrapper.readBoolean());
   }

   @Test
   public void testPutNullableTrueBoolean() throws Exception {
      wrapper.writeNullableBoolean(true);

      assertTrue(wrapper.readNullableBoolean());
   }

   @Test
   public void testPutNullableFalseBoolean() throws Exception {
      wrapper.writeNullableBoolean(false);

      assertFalse(wrapper.readNullableBoolean());
   }

   @Test
   public void testNullBoolean() throws Exception {
      wrapper.writeNullableBoolean(null);

      assertNull(wrapper.readNullableBoolean());
   }

   @Test
   public void testChar() throws Exception {
      wrapper.writeChar('a');

      assertEquals('a', wrapper.readChar());
   }

   @Test
   public void testInt() throws Exception {
      int i = RandomUtil.randomInt();
      wrapper.writeInt(i);

      assertEquals(i, wrapper.readInt());
   }

   @Test
   public void testIntAtPosition() throws Exception {
      int firstInt = RandomUtil.randomInt();
      int secondInt = RandomUtil.randomInt();

      wrapper.writeInt(secondInt);
      wrapper.writeInt(secondInt);
      // rewrite firstInt at the beginning
      wrapper.setInt(0, firstInt);

      assertEquals(firstInt, wrapper.readInt());
      assertEquals(secondInt, wrapper.readInt());
   }

   @Test
   public void testLong() throws Exception {
      long l = RandomUtil.randomLong();
      wrapper.writeLong(l);

      assertEquals(l, wrapper.readLong());
   }

   @Test
   public void testNullableLong() throws Exception {
      Long l = RandomUtil.randomLong();
      wrapper.writeNullableLong(l);

      assertEquals(l, wrapper.readNullableLong());
   }

   @Test
   public void testNullLong() throws Exception {
      wrapper.writeNullableLong(null);

      assertNull(wrapper.readNullableLong());
   }

   @Test
   public void testUnsignedShort() throws Exception {
      short s1 = Short.MAX_VALUE;

      wrapper.writeShort(s1);

      int s2 = wrapper.readUnsignedShort();

      assertEquals(s1, s2);

      s1 = Short.MIN_VALUE;

      wrapper.writeShort(s1);

      s2 = wrapper.readUnsignedShort();

      assertEquals(s1 * -1, s2);

      s1 = -1;

      wrapper.writeShort(s1);

      s2 = wrapper.readUnsignedShort();

      // / The max of an unsigned short
      // (http://en.wikipedia.org/wiki/Unsigned_short)
      assertEquals(65535, s2);
   }

   @Test
   public void testWriteUnsignedShort() throws Exception {
      // Test writing 0
      wrapper.writeUnsignedShort(0);
      assertEquals(0, wrapper.readUnsignedShort());

      // Test writing max unsigned short value (65535)
      wrapper.writeUnsignedShort(65535);
      assertEquals(65535, wrapper.readUnsignedShort());

      // Test writing mid-range value
      wrapper.writeUnsignedShort(32768);
      assertEquals(32768, wrapper.readUnsignedShort());

      // Test writing value that would be negative as signed short
      wrapper.writeUnsignedShort(40000);
      assertEquals(40000, wrapper.readUnsignedShort());
   }

   @Test
   public void testWriteUnsignedShortInvalidValues() throws Exception {
      // Test that negative values throw IllegalArgumentException
      try {
         wrapper.writeUnsignedShort(-1);
         fail("Expected IllegalArgumentException for negative value");
      } catch (IllegalArgumentException e) {
         // expected
      }

      // Test that values > 65535 throw IllegalArgumentException
      try {
         wrapper.writeUnsignedShort(65536);
         fail("Expected IllegalArgumentException for value > 65535");
      } catch (IllegalArgumentException e) {
         // expected
      }

      try {
         wrapper.writeUnsignedShort(100000);
         fail("Expected IllegalArgumentException for value > 65535");
      } catch (IllegalArgumentException e) {
         // expected
      }
   }

   @Test
   public void testShort() throws Exception {
      wrapper.writeShort((short) 1);

      assertEquals((short) 1, wrapper.readShort());
   }

   @Test
   public void testDouble() throws Exception {
      double d = RandomUtil.randomDouble();
      wrapper.writeDouble(d);

      assertEquals(d, wrapper.readDouble(), 0.000001);
   }

   @Test
   public void testFloat() throws Exception {
      float f = RandomUtil.randomFloat();
      wrapper.writeFloat(f);

      assertEquals(f, wrapper.readFloat(), 0.000001);
   }

   @Test
   public void testUTF() throws Exception {
      String str = RandomUtil.randomUUIDString();
      wrapper.writeUTF(str);

      assertEquals(str, wrapper.readUTF());
   }

   @Test
   public void testArray() throws Exception {
      byte[] bytes = RandomUtil.randomBytes(128);
      wrapper.writeBytes(bytes);

      byte[] array = wrapper.toByteBuffer().array();
      assertEquals(wrapper.capacity(), array.length);
      ActiveMQTestBase.assertEqualsByteArrays(128, bytes, wrapper.toByteBuffer().array());
   }

   @Test
   public void testRewind() throws Exception {
      int i = RandomUtil.randomInt();
      wrapper.writeInt(i);

      assertEquals(i, wrapper.readInt());

      wrapper.resetReaderIndex();

      assertEquals(i, wrapper.readInt());
   }

   @Test
   public void testRemaining() throws Exception {
      int capacity = wrapper.capacity();

      // fill 1/3 of the buffer
      int fill = capacity / 3;
      byte[] bytes = RandomUtil.randomBytes(fill);
      wrapper.writeBytes(bytes);

      // check the remaining is 2/3
      assertEquals(capacity - fill, wrapper.writableBytes());
   }

   @Test
   public void testPosition() throws Exception {
      assertEquals(0, wrapper.writerIndex());

      byte[] bytes = RandomUtil.randomBytes(128);
      wrapper.writeBytes(bytes);

      assertEquals(bytes.length, wrapper.writerIndex());

      wrapper.writerIndex(0);
      assertEquals(0, wrapper.writerIndex());
   }




   private String putAndGetNullableString(final String nullableString) throws Exception {
      wrapper.writeNullableString(nullableString);

      return wrapper.readNullableString();
   }

   private SimpleString putAndGetNullableSimpleString(final SimpleString nullableSimpleString) throws Exception {
      wrapper.writeNullableSimpleString(nullableSimpleString);

      return wrapper.readNullableSimpleString();
   }

}
