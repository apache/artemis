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
package org.apache.activemq.artemis.tests.unit.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UTF8Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class UTF8Test extends ActiveMQTestBase {

   @Test
   public void testValidateUTFWithENDChars() {
      testValidateUTFWithChars(1024, (char) 0);
   }

   @Test
   public void testValidateUTFWithLastAsciiChars() {
      testValidateUTFWithChars(1024, (char) Byte.MAX_VALUE);
   }

   private void testValidateUTFWithChars(final int size, final char c) {
      final char[] chars = new char[size];
      Arrays.fill(chars, c);
      final String expectedUtf8String = new String(chars);
      final ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(4 * chars.length);
      UTF8Util.saveUTF(buffer.byteBuf(), expectedUtf8String);
      final byte[] expectedBytes = expectedUtf8String.getBytes(StandardCharsets.UTF_8);
      final int encodedSize = buffer.readUnsignedShort();
      final byte[] realEncodedBytes = new byte[encodedSize];
      buffer.getBytes(buffer.readerIndex(), realEncodedBytes);
      assertArrayEquals(expectedBytes, realEncodedBytes);
   }

   @Test
   public void testValidateUTF() throws Exception {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(60 * 1024);

      byte[] bytes = new byte[20000];

      RandomUtil.getRandom().nextBytes(bytes);

      String str = new String(bytes);

      UTF8Util.saveUTF(buffer.byteBuf(), str);

      String newStr = UTF8Util.readUTF(buffer);

      assertEquals(str, newStr);
   }

   @Test
   public void testValidateUTFOnDataInput() throws Exception {
      for (int i = 0; i < 100; i++) {

         // Random size between 15k and 20K
         byte[] bytes = new byte[15000 + RandomUtil.randomPositiveInt() % 5000];

         RandomUtil.getRandom().nextBytes(bytes);

         String str = new String(bytes);

         // The maximum size the encoded UTF string would reach is str.length * 3 (look at the UTF8 implementation)
         testValidateUTFOnDataInputStream(str, ActiveMQBuffers.wrappedBuffer(ByteBuffer.allocate(str.length() * 3 + DataConstants.SIZE_SHORT)));

         testValidateUTFOnDataInputStream(str, ActiveMQBuffers.dynamicBuffer(100));

         testValidateUTFOnDataInputStream(str, ActiveMQBuffers.fixedBuffer(100 * 1024));
      }
   }

   private void testValidateUTFOnDataInputStream(final String str, final ActiveMQBuffer wrap) throws Exception {
      UTF8Util.saveUTF(wrap.byteBuf(), str);

      DataInputStream data = new DataInputStream(new ByteArrayInputStream(wrap.toByteBuffer().array()));

      String newStr = data.readUTF();

      assertEquals(str, newStr);

      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      DataOutputStream outData = new DataOutputStream(byteOut);

      outData.writeUTF(str);

      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(byteOut.toByteArray());

      newStr = UTF8Util.readUTF(buffer);

      assertEquals(str, newStr);
   }

   @Test
   public void testBigSize() throws Exception {

      char[] chars = new char[0xffff + 1];

      for (int i = 0; i < chars.length; i++) {
         chars[i] = ' ';
      }

      String str = new String(chars);

      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(0xffff + 4);

      try {
         UTF8Util.saveUTF(buffer.byteBuf(), str);
         fail("String is too big, supposed to throw an exception");
      } catch (Exception ignored) {
      }

      assertEquals(0, buffer.writerIndex(), "A buffer was supposed to be untouched since the string was too big");

      chars = new char[25000];

      for (int i = 0; i < chars.length; i++) {
         chars[i] = 0x810;
      }

      str = new String(chars);

      try {
         UTF8Util.saveUTF(buffer.byteBuf(), str);
         fail("Encoded String is too big, supposed to throw an exception");
      } catch (Exception ignored) {
      }

      assertEquals(0, buffer.writerIndex(), "A buffer was supposed to be untouched since the string was too big");

      // Testing a string right on the limit
      chars = new char[0xffff];

      for (int i = 0; i < chars.length; i++) {
         chars[i] = (char) (i % 100 + 1);
      }

      str = new String(chars);

      UTF8Util.saveUTF(buffer.byteBuf(), str);

      assertEquals(0xffff + DataConstants.SIZE_SHORT, buffer.writerIndex());

      String newStr = UTF8Util.readUTF(buffer);

      assertEquals(str, newStr);

   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      UTF8Util.clearBuffer();
      super.tearDown();
   }
}
