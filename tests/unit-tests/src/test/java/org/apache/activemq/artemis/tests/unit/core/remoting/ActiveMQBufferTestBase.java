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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public abstract class ActiveMQBufferTestBase extends ActiveMQTestBase {


   private ActiveMQBuffer wrapper;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      wrapper = createBuffer();
   }

   @Override
   @After
   public void tearDown() throws Exception {
      wrapper = null;

      super.tearDown();
   }

   protected abstract ActiveMQBuffer createBuffer();

   @Test
   public void testNullString() throws Exception {
      Assert.assertNull(putAndGetNullableString(null));
   }

   @Test
   public void testEmptyString() throws Exception {
      String result = putAndGetNullableString("");

      Assert.assertNotNull(result);
      Assert.assertEquals("", result);
   }

   @Test
   public void testNonEmptyString() throws Exception {
      String junk = RandomUtil.randomString();

      String result = putAndGetNullableString(junk);

      Assert.assertNotNull(result);
      Assert.assertEquals(junk, result);
   }

   @Test
   public void testNullSimpleString() throws Exception {
      Assert.assertNull(putAndGetNullableSimpleString(null));
   }

   @Test
   public void testEmptySimpleString() throws Exception {
      SimpleString emptySimpleString = new SimpleString("");
      SimpleString result = putAndGetNullableSimpleString(emptySimpleString);

      Assert.assertNotNull(result);
      ActiveMQTestBase.assertEqualsByteArrays(emptySimpleString.getData(), result.getData());
   }

   @Test
   public void testNonEmptySimpleString() throws Exception {
      SimpleString junk = RandomUtil.randomSimpleString();
      SimpleString result = putAndGetNullableSimpleString(junk);

      Assert.assertNotNull(result);
      ActiveMQTestBase.assertEqualsByteArrays(junk.getData(), result.getData());
   }

   @Test
   public void testByte() throws Exception {
      byte b = RandomUtil.randomByte();
      wrapper.writeByte(b);

      Assert.assertEquals(b, wrapper.readByte());
   }

   @Test
   public void testUnsignedByte() throws Exception {
      byte b = (byte) 0xff;
      wrapper.writeByte(b);

      Assert.assertEquals(255, wrapper.readUnsignedByte());

      b = (byte) 0xf;
      wrapper.writeByte(b);

      Assert.assertEquals(b, wrapper.readUnsignedByte());
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

      Assert.assertTrue(wrapper.readBoolean());
   }

   @Test
   public void testPutFalseBoolean() throws Exception {
      wrapper.writeBoolean(false);

      Assert.assertFalse(wrapper.readBoolean());
   }

   @Test
   public void testPutNullableTrueBoolean() throws Exception {
      wrapper.writeNullableBoolean(true);

      Assert.assertTrue(wrapper.readNullableBoolean());
   }

   @Test
   public void testPutNullableFalseBoolean() throws Exception {
      wrapper.writeNullableBoolean(false);

      Assert.assertFalse(wrapper.readNullableBoolean());
   }

   @Test
   public void testNullBoolean() throws Exception {
      wrapper.writeNullableBoolean(null);

      Assert.assertNull(wrapper.readNullableBoolean());
   }

   @Test
   public void testChar() throws Exception {
      wrapper.writeChar('a');

      Assert.assertEquals('a', wrapper.readChar());
   }

   @Test
   public void testInt() throws Exception {
      int i = RandomUtil.randomInt();
      wrapper.writeInt(i);

      Assert.assertEquals(i, wrapper.readInt());
   }

   @Test
   public void testIntAtPosition() throws Exception {
      int firstInt = RandomUtil.randomInt();
      int secondInt = RandomUtil.randomInt();

      wrapper.writeInt(secondInt);
      wrapper.writeInt(secondInt);
      // rewrite firstInt at the beginning
      wrapper.setInt(0, firstInt);

      Assert.assertEquals(firstInt, wrapper.readInt());
      Assert.assertEquals(secondInt, wrapper.readInt());
   }

   @Test
   public void testLong() throws Exception {
      long l = RandomUtil.randomLong();
      wrapper.writeLong(l);

      Assert.assertEquals(l, wrapper.readLong());
   }

   @Test
   public void testNullableLong() throws Exception {
      Long l = RandomUtil.randomLong();
      wrapper.writeNullableLong(l);

      Assert.assertEquals(l, wrapper.readNullableLong());
   }

   @Test
   public void testNullLong() throws Exception {
      wrapper.writeNullableLong(null);

      Assert.assertNull(wrapper.readNullableLong());
   }

   @Test
   public void testUnsignedShort() throws Exception {
      short s1 = Short.MAX_VALUE;

      wrapper.writeShort(s1);

      int s2 = wrapper.readUnsignedShort();

      Assert.assertEquals(s1, s2);

      s1 = Short.MIN_VALUE;

      wrapper.writeShort(s1);

      s2 = wrapper.readUnsignedShort();

      Assert.assertEquals(s1 * -1, s2);

      s1 = -1;

      wrapper.writeShort(s1);

      s2 = wrapper.readUnsignedShort();

      // / The max of an unsigned short
      // (http://en.wikipedia.org/wiki/Unsigned_short)
      Assert.assertEquals(s2, 65535);
   }

   @Test
   public void testShort() throws Exception {
      wrapper.writeShort((short) 1);

      Assert.assertEquals((short) 1, wrapper.readShort());
   }

   @Test
   public void testDouble() throws Exception {
      double d = RandomUtil.randomDouble();
      wrapper.writeDouble(d);

      Assert.assertEquals(d, wrapper.readDouble(), 0.000001);
   }

   @Test
   public void testFloat() throws Exception {
      float f = RandomUtil.randomFloat();
      wrapper.writeFloat(f);

      Assert.assertEquals(f, wrapper.readFloat(), 0.000001);
   }

   @Test
   public void testUTF() throws Exception {
      String str = RandomUtil.randomString();
      wrapper.writeUTF(str);

      Assert.assertEquals(str, wrapper.readUTF());
   }

   @Test
   public void testArray() throws Exception {
      byte[] bytes = RandomUtil.randomBytes(128);
      wrapper.writeBytes(bytes);

      byte[] array = wrapper.toByteBuffer().array();
      Assert.assertEquals(wrapper.capacity(), array.length);
      ActiveMQTestBase.assertEqualsByteArrays(128, bytes, wrapper.toByteBuffer().array());
   }

   @Test
   public void testRewind() throws Exception {
      int i = RandomUtil.randomInt();
      wrapper.writeInt(i);

      Assert.assertEquals(i, wrapper.readInt());

      wrapper.resetReaderIndex();

      Assert.assertEquals(i, wrapper.readInt());
   }

   @Test
   public void testRemaining() throws Exception {
      int capacity = wrapper.capacity();

      // fill 1/3 of the buffer
      int fill = capacity / 3;
      byte[] bytes = RandomUtil.randomBytes(fill);
      wrapper.writeBytes(bytes);

      // check the remaining is 2/3
      Assert.assertEquals(capacity - fill, wrapper.writableBytes());
   }

   @Test
   public void testPosition() throws Exception {
      Assert.assertEquals(0, wrapper.writerIndex());

      byte[] bytes = RandomUtil.randomBytes(128);
      wrapper.writeBytes(bytes);

      Assert.assertEquals(bytes.length, wrapper.writerIndex());

      wrapper.writerIndex(0);
      Assert.assertEquals(0, wrapper.writerIndex());
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
