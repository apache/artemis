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
package org.apache.activemq.artemis.tests.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SimpleStringTest {

   /**
    * Converting back and forth between char and byte requires care as char is unsigned.
    *
    * @see SimpleString#getChars(int, int, char[], int)
    * @see SimpleString#charAt(int)
    * @see SimpleString#split(char)
    * @see SimpleString#concat(char)
    */
   @Test
   public void testGetChar() {
      SimpleString p1 = createSimpleString("foo");
      SimpleString p2 = createSimpleString("bar");
      for (int i = 0; i < 1 << 16; i++) {
         String msg = "expecting " + i;
         char c = (char) i;
         SimpleString s = createSimpleString(String.valueOf(c));

         // test getChars(...)
         char[] c1 = new char[1];
         s.getChars(0, 1, c1, 0);
         assertEquals(c, c1[0], msg);

         // test charAt(int)
         assertEquals(c, s.charAt(0), msg);

         // test concat(char)
         SimpleString s2 = s.concat(c);
         assertEquals(c, s2.charAt(1), msg);

         // test splitting with chars
         SimpleString sSplit = createSimpleString("foo" + String.valueOf(c) + "bar");
         SimpleString[] chunks = sSplit.split(c);
         SimpleString[] split1 = p1.split(c);
         SimpleString[] split2 = p2.split(c);
         assertEquals(split1.length + split2.length, chunks.length);

         int j = 0;
         for (SimpleString iS : split1) {
            assertEquals(iS, chunks[j++], iS.toString());
         }
         for (SimpleString iS : split2) {
            assertEquals(iS, chunks[j++], iS.toString());
         }
      }
   }

   @Test
   public void testString() throws Exception {
      final String str = "hello123ABC__524`16254`6125!%^$!%$!%$!%$!%!$%!$$!\uA324";

      SimpleString s = createSimpleString(str);

      assertEquals(str, s.toString());

      assertEquals(2 * str.length(), s.getData().length);

      byte[] data = s.getData();

      SimpleString s2 = createSimpleString(data);

      assertEquals(str, s2.toString());
   }

   @Test
   public void testStartsWith() throws Exception {
      SimpleString s1 = createSimpleString("abcdefghi");

      assertTrue(s1.startsWith(createSimpleString("abc")));

      assertTrue(s1.startsWith(createSimpleString("abcdef")));

      assertTrue(s1.startsWith(createSimpleString("abcdefghi")));

      assertFalse(s1.startsWith(createSimpleString("abcdefghijklmn")));

      assertFalse(s1.startsWith(createSimpleString("aardvark")));

      assertFalse(s1.startsWith(createSimpleString("z")));
   }

   @Test
   public void testCharSequence() throws Exception {
      String s = "abcdefghijkl";
      SimpleString s1 = createSimpleString(s);

      assertEquals('a', s1.charAt(0));
      assertEquals('b', s1.charAt(1));
      assertEquals('c', s1.charAt(2));
      assertEquals('k', s1.charAt(10));
      assertEquals('l', s1.charAt(11));

      try {
         s1.charAt(-1);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.charAt(-2);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.charAt(s.length());
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.charAt(s.length() + 1);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      assertEquals(s.length(), s1.length());

      CharSequence ss = s1.subSequence(0, s1.length());

      assertEquals(ss, s1);

      ss = s1.subSequence(1, 4);
      assertEquals(ss, createSimpleString("bcd"));

      ss = s1.subSequence(5, 10);
      assertEquals(ss, createSimpleString("fghij"));

      ss = s1.subSequence(5, 12);
      assertEquals(ss, createSimpleString("fghijkl"));

      try {
         s1.subSequence(-1, 2);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.subSequence(-4, -2);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.subSequence(0, s1.length() + 1);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.subSequence(0, s1.length() + 2);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      try {
         s1.subSequence(5, 1);
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }
   }

   @Test
   public void testEquals() throws Exception {
      assertFalse(createSimpleString("abcdef").equals(new Object()));

      assertFalse(createSimpleString("abcef").equals(null));

      assertEquals(createSimpleString("abcdef"), createSimpleString("abcdef"));

      assertFalse(createSimpleString("abcdef").equals(createSimpleString("abggcdef")));
      assertFalse(createSimpleString("abcdef").equals(createSimpleString("ghijkl")));
   }

   @Test
   public void testHashcode() throws Exception {
      SimpleString str = createSimpleString("abcdef");
      SimpleString sameStr = createSimpleString("abcdef");
      SimpleString differentStr = createSimpleString("ghijk");

      assertEquals(str.hashCode(), sameStr.hashCode());
      assertNotEquals(str.hashCode(), differentStr.hashCode());
   }

   @Test
   public void testUnicode() throws Exception {
      String myString = "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5";

      SimpleString s = createSimpleString(myString);
      byte[] data = s.getData();
      s = createSimpleString(data);

      assertEquals(myString, s.toString());
   }

   @Test
   public void testUnicodeWithSurrogates() throws Exception {
      String myString = "abcdef&^*&!^ghijkl\uD900\uDD00";

      SimpleString s = createSimpleString(myString);
      byte[] data = s.getData();
      s = createSimpleString(data);

      assertEquals(myString, s.toString());
   }

   @Test
   public void testSizeofString() throws Exception {
      assertEquals(DataConstants.SIZE_INT, SimpleString.sizeofString(createSimpleString("")));

      SimpleString str = createSimpleString(RandomUtil.randomUUIDString());
      assertEquals(DataConstants.SIZE_INT + str.getData().length, SimpleString.sizeofString(str));
   }

   @Test
   public void testSizeofNullableString() throws Exception {
      assertEquals(1, SimpleString.sizeofNullableString(null));

      assertEquals(1 + DataConstants.SIZE_INT, SimpleString.sizeofNullableString(createSimpleString("")));

      SimpleString str = createSimpleString(RandomUtil.randomUUIDString());
      assertEquals(1 + DataConstants.SIZE_INT + str.getData().length, SimpleString.sizeofNullableString(str));
   }

   @Test
   public void testSplitNoDelimeter() throws Exception {
      SimpleString s = createSimpleString("abcdefghi");
      SimpleString[] strings = s.split('.');
      assertNotNull(strings);
      assertEquals(1, strings.length);
      assertEquals(strings[0], s);
   }

   @Test
   public void testSplit1Delimeter() throws Exception {
      SimpleString s = createSimpleString("abcd.efghi");
      SimpleString[] strings = s.split('.');
      assertNotNull(strings);
      assertEquals(2, strings.length);
      assertEquals(strings[0], createSimpleString("abcd"));
      assertEquals(strings[1], createSimpleString("efghi"));
   }

   @Test
   public void testSplitmanyDelimeters() throws Exception {
      SimpleString s = createSimpleString("abcd.efghi.jklmn.opqrs.tuvw.xyz");
      SimpleString[] strings = s.split('.');
      assertNotNull(strings);
      assertEquals(6, strings.length);
      assertEquals(strings[0], createSimpleString("abcd"));
      assertEquals(strings[1], createSimpleString("efghi"));
      assertEquals(strings[2], createSimpleString("jklmn"));
      assertEquals(strings[3], createSimpleString("opqrs"));
      assertEquals(strings[4], createSimpleString("tuvw"));
      assertEquals(strings[5], createSimpleString("xyz"));
   }

   @Test
   public void testContains() {
      SimpleString simpleString = createSimpleString("abcdefghijklmnopqrst");
      assertFalse(simpleString.contains('.'));
      assertFalse(simpleString.contains('%'));
      assertFalse(simpleString.contains('8'));
      assertFalse(simpleString.contains('.'));
      assertTrue(simpleString.contains('a'));
      assertTrue(simpleString.contains('b'));
      assertTrue(simpleString.contains('c'));
      assertTrue(simpleString.contains('d'));
      assertTrue(simpleString.contains('e'));
      assertTrue(simpleString.contains('f'));
      assertTrue(simpleString.contains('g'));
      assertTrue(simpleString.contains('h'));
      assertTrue(simpleString.contains('i'));
      assertTrue(simpleString.contains('j'));
      assertTrue(simpleString.contains('k'));
      assertTrue(simpleString.contains('l'));
      assertTrue(simpleString.contains('m'));
      assertTrue(simpleString.contains('n'));
      assertTrue(simpleString.contains('o'));
      assertTrue(simpleString.contains('p'));
      assertTrue(simpleString.contains('q'));
      assertTrue(simpleString.contains('r'));
      assertTrue(simpleString.contains('s'));
      assertTrue(simpleString.contains('t'));
   }

   @Test
   public void testConcat() {
      SimpleString start = createSimpleString("abcdefg");
      SimpleString middle = createSimpleString("hijklmnop");
      SimpleString end = createSimpleString("qrstuvwxyz");
      assertEquals(start.concat(middle).concat(end), createSimpleString("abcdefghijklmnopqrstuvwxyz"));
      assertEquals(start.concat('.').concat(end), createSimpleString("abcdefg.qrstuvwxyz"));
      // Testing concat of SimpleString with String
      for (int i = 0; i < 10; i++) {
         assertEquals(createSimpleString("abcdefg-" + i), start.concat("-" + Integer.toString(i)));

      }
   }

   @Test
   public void testMultithreadHashCode() throws Exception {
      for (int repeat = 0; repeat < 10; repeat++) {

         StringBuffer buffer = new StringBuffer();

         for (int i = 0; i < 100; i++) {
            buffer.append("Some Big String " + i);
         }
         String strvalue = buffer.toString();

         final int initialhash = createSimpleString(strvalue).hashCode();

         final SimpleString value = createSimpleString(strvalue);

         int nThreads = 100;
         final CountDownLatch latch = new CountDownLatch(nThreads);
         final CountDownLatch start = new CountDownLatch(1);

         class T extends Thread {

            boolean failed = false;

            @Override
            public void run() {
               try {
                  latch.countDown();
                  start.await();

                  int newhash = value.hashCode();

                  if (newhash != initialhash) {
                     failed = true;
                  }
               } catch (Exception e) {
                  e.printStackTrace();
                  failed = true;
               }
            }
         }

         T[] x = new T[nThreads];
         for (int i = 0; i < nThreads; i++) {
            x[i] = new T();
            x[i].start();
         }

         assertTrue(latch.await(1, TimeUnit.MINUTES), "Latch has got to return within a minute");
         start.countDown();

         for (T t : x) {
            t.join();
         }

         for (T t : x) {
            assertFalse(t.failed);
         }
      }
   }

   @Test
   public void testToSimpleStringPoolStringArgument() throws Exception {
      final String s = "pooled";
      final SimpleString ss = createSimpleString(s);
      final String s1 = ss.toString();
      assertSame(s, s1, "SimpleString::toSimpleString is not pooling the given String");
   }

   @Test
   public void testByteBufSimpleStringPool() {
      final int capacity = 8;
      final int chars = Integer.toString(capacity).length();
      final SimpleString.ByteBufSimpleStringPool pool = new SimpleString.ByteBufSimpleStringPool(capacity, chars);
      final int bytes = createSimpleString(Integer.toString(capacity)).sizeof();
      final ByteBuf bb = Unpooled.buffer(bytes, bytes);
      for (int i = 0; i < capacity; i++) {
         final SimpleString s = createSimpleString(Integer.toString(i));
         bb.resetWriterIndex();
         SimpleString.writeSimpleString(bb, s);
         bb.resetReaderIndex();
         final SimpleString expectedPooled = pool.getOrCreate(bb);
         bb.resetReaderIndex();
         assertSame(expectedPooled, pool.getOrCreate(bb));
         bb.resetReaderIndex();
      }
   }

   @Test
   public void testByteBufSimpleStringPoolTooLong() {
      final SimpleString tooLong = createSimpleString("aa");
      final ByteBuf bb = Unpooled.buffer(tooLong.sizeof(), tooLong.sizeof());
      SimpleString.writeSimpleString(bb, tooLong);
      final SimpleString.ByteBufSimpleStringPool pool = new SimpleString.ByteBufSimpleStringPool(1, tooLong.length() - 1);
      assertNotSame(pool.getOrCreate(bb), pool.getOrCreate(bb.resetReaderIndex()));
   }

   @Test
   public void testStringSimpleStringPool() throws Exception {
      final int capacity = 8;
      final SimpleString.StringSimpleStringPool pool = new SimpleString.StringSimpleStringPool(capacity);
      for (int i = 0; i < capacity; i++) {
         final String s = Integer.toString(i);
         final SimpleString expectedPooled = pool.getOrCreate(s);
         assertSame(expectedPooled, pool.getOrCreate(s));
      }
   }

   protected SimpleString createSimpleString(String string) {
      return SimpleString.of(string);
   }

   protected SimpleString createSimpleString(byte[] bytes) {
      return SimpleString.of(bytes);
   }
}
